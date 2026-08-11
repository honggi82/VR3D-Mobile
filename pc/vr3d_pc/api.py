from __future__ import annotations

import argparse
import asyncio
from contextlib import asynccontextmanager
from ipaddress import ip_address
from pathlib import PurePath

import uvicorn
from fastapi import FastAPI, File, Form, HTTPException, Request, UploadFile
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from PIL import Image

from .config import Settings
from .depth import VideoDepthAnythingAdapter
from .images import ImageRejected, inspect_signature
from .jobs import JobOrchestrator
from .limits import LimitExceeded, RateLimiter
from .security import ExplicitCommandGate, SecurityPipeline, V3RealtimeHealth
from .storage import Storage


def error(status: int, code: str) -> HTTPException:
    return HTTPException(status_code=status, detail={"code": code})


def client_address(request: Request) -> str:
    forwarded = request.headers.get("CF-Connecting-IP", "").strip()
    if forwarded:
        try:
            return str(ip_address(forwarded))
        except ValueError:
            pass
    return request.client.host if request.client else "unknown"


async def read_limited(upload: UploadFile, maximum: int) -> bytes:
    data = bytearray()
    while chunk := await upload.read(min(1024 * 1024, maximum + 1 - len(data))):
        data.extend(chunk)
        if len(data) > maximum:
            raise error(413, "file_size")
    return bytes(data)


def create_default_orchestrator(settings: Settings) -> JobOrchestrator:
    probe = settings.data_dir / ".healthcheck.png"
    if not probe.is_file():
        probe.parent.mkdir(parents=True, exist_ok=True)
        Image.new("RGB", (2, 2), (32, 96, 160)).save(probe, "PNG")
    scanner = ExplicitCommandGate(
        settings.scanner_command, settings.scanner_timeout_seconds, "scanner", probe
    )
    content = ExplicitCommandGate(
        settings.content_command, settings.content_timeout_seconds, "content", probe
    )
    security = SecurityPipeline(
        V3RealtimeHealth(), scanner, content,
        settings.max_upload_bytes, settings.max_pixels, settings.max_edge,
    )
    return JobOrchestrator(
        Storage(settings.data_dir, settings.retention_seconds), security,
        VideoDepthAnythingAdapter(settings.runtime_dir),
        RateLimiter(settings.jobs_per_hour, settings.max_active_per_client),
    )


def create_app(settings: Settings | None = None,
               orchestrator: JobOrchestrator | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    service = orchestrator or create_default_orchestrator(settings)

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        service.storage.cleanup_expired()
        stop_cleanup = asyncio.Event()

        async def cleanup_loop() -> None:
            while True:
                try:
                    await asyncio.wait_for(stop_cleanup.wait(), timeout=300)
                    return
                except asyncio.TimeoutError:
                    service.storage.cleanup_expired()

        cleanup_task = asyncio.create_task(cleanup_loop())
        try:
            yield
        finally:
            stop_cleanup.set()
            await cleanup_task
            service.shutdown()

    app = FastAPI(title="VR3D Mobile API", version="1.0", lifespan=lifespan)
    app.state.orchestrator = service
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["https://honggi82.github.io"],
        allow_methods=["GET", "POST"],
        allow_headers=["Content-Type"],
    )

    @app.get("/api/v1/health")
    def health():
        gates = service.security.health()
        return {
            "status": "online",
            "publicReady": all(state.ready for state in gates.values()),
            "gates": {name: {"ready": state.ready, "code": state.code} for name, state in gates.items()},
        }

    @app.post("/api/v1/jobs", status_code=202)
    async def create_job(request: Request, file: UploadFile = File(...), quality: str = Form("vitl")):
        if not service.security.ready():
            raise error(503, "gates_unavailable")
        filename = file.filename or ""
        if not filename or PurePath(filename).name != filename or "/" in filename or "\\" in filename:
            raise error(400, "invalid_filename")
        if quality not in ("vitl", "vits"):
            raise error(400, "invalid_quality")
        payload = await read_limited(file, settings.max_upload_bytes)
        try:
            inspect_signature(payload, settings.max_upload_bytes)
        except ImageRejected as exc:
            status = 413 if exc.code == "file_size" else 415
            raise error(status, "invalid_image" if status == 415 else exc.code) from None
        client_ip = client_address(request)
        try:
            job = service.submit(payload, quality, client_ip)
        except LimitExceeded as exc:
            status = 409 if str(exc) == "active_limit" else 429
            raise error(status, str(exc)) from None
        return {"jobId": job.job_id, "status": job.status, "createdAt": job.created_at.isoformat()}

    @app.get("/api/v1/jobs/{job_id}")
    def job_status(job_id: str):
        job = service.get(job_id)
        if job is None:
            raise error(404, "job_not_found")
        return job.public()

    @app.get("/api/v1/jobs/{job_id}/download")
    def download(job_id: str):
        job = service.get(job_id)
        if job is None:
            raise error(404, "job_not_found")
        if job.status != "complete":
            raise error(409, "job_not_complete")
        result = service.storage.result_path(job_id)
        if not result.is_file():
            raise error(404, "job_not_found")
        return FileResponse(
            result, media_type="application/vnd.vr3d+zip",
            filename=f"{job_id}.vr3d",
        )

    return app


app = create_app()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    uvicorn.run("vr3d_pc.api:app", host=args.host, port=args.port, reload=False)


if __name__ == "__main__":
    main()
