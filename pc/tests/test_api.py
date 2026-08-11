from __future__ import annotations

import time
from pathlib import Path

from fastapi.testclient import TestClient
from starlette.requests import Request

from helpers import AllowGate, FakeDepth, image_bytes
from vr3d_pc.api import client_address, create_app
from vr3d_pc.config import Settings
from vr3d_pc.jobs import JobOrchestrator
from vr3d_pc.limits import RateLimiter
from vr3d_pc.security import GateHealth, SecurityPipeline
from vr3d_pc.storage import Storage


def make_service(tmp_path: Path, ready: bool = True):
    gate = AllowGate()
    if not ready:
        class Closed(AllowGate):
            def health(self):
                return GateHealth(False, "scanner_unconfigured")
        scanner = Closed()
    else:
        scanner = gate
    security = SecurityPipeline(gate, scanner, gate, 100000, 100000, 64)
    return JobOrchestrator(Storage(tmp_path), security, FakeDepth(), RateLimiter(), max_workers=1)


def test_client_address_prefers_valid_cloudflare_header_and_rejects_spoof_shape():
    forwarded = Request({
        "type": "http", "method": "GET", "path": "/", "headers": [(b"cf-connecting-ip", b"203.0.113.7")],
        "client": ("127.0.0.1", 50000), "server": ("127.0.0.1", 8765), "scheme": "http",
        "query_string": b"", "root_path": "", "http_version": "1.1",
    })
    malformed = Request({**forwarded.scope, "headers": [(b"cf-connecting-ip", b"not-an-ip")]})
    assert client_address(forwarded) == "203.0.113.7"
    assert client_address(malformed) == "127.0.0.1"


def test_api_fails_closed_before_accepting_upload(tmp_path: Path):
    settings = Settings(data_dir=tmp_path, max_upload_bytes=100000, max_pixels=100000, max_edge=64)
    with TestClient(create_app(settings, make_service(tmp_path, ready=False))) as client:
        response = client.post("/api/v1/jobs", files={"file": ("photo.png", image_bytes(), "image/png")})
        assert response.status_code == 503
        assert response.json() == {"detail": {"code": "gates_unavailable"}}


def test_api_rejects_traversal_and_supports_status_download(tmp_path: Path):
    settings = Settings(data_dir=tmp_path, max_upload_bytes=100000, max_pixels=100000, max_edge=64)
    with TestClient(create_app(settings, make_service(tmp_path))) as client:
        bad = client.post("/api/v1/jobs", files={"file": ("../photo.png", image_bytes(), "image/png")})
        assert bad.status_code == 400
        created = client.post(
            "/api/v1/jobs", data={"quality": "vits"},
            files={"file": ("photo.png", image_bytes(size=(24, 18)), "image/png")},
        )
        assert created.status_code == 202
        job_id = created.json()["jobId"]
        for _ in range(100):
            status = client.get(f"/api/v1/jobs/{job_id}").json()
            if status["status"] in ("complete", "failed"):
                break
            time.sleep(0.02)
        assert status["status"] == "complete", status
        result = client.get(status["downloadUrl"])
        assert result.status_code == 200
        assert result.headers["content-type"].startswith("application/vnd.vr3d+zip")
        assert result.content.startswith(b"PK")
