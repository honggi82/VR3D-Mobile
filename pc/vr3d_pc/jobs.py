from __future__ import annotations

import json
import threading
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass, replace
from datetime import datetime, timedelta, timezone
from pathlib import Path
from uuid import uuid4

import numpy as np
from PIL import Image

from .depth import DepthUnavailable
from .images import ImageRejected
from .limits import RateLimiter
from .package import build_package
from .render import render_grid
from .security import SecurityPipeline
from .storage import Storage


@dataclass(frozen=True)
class Job:
    job_id: str
    client_key: str
    quality: str
    status: str
    progress: int
    created_at: datetime
    expires_at: datetime
    error_code: str | None = None

    def public(self) -> dict:
        complete = self.status == "complete"
        return {
            "jobId": self.job_id,
            "status": self.status,
            "progress": self.progress,
            "createdAt": self.created_at.isoformat(),
            "expiresAt": self.expires_at.isoformat(),
            "errorCode": self.error_code,
            "downloadUrl": f"/api/v1/jobs/{self.job_id}/download" if complete else None,
        }


class JobOrchestrator:
    def __init__(self, storage: Storage, security: SecurityPipeline, depth,
                 limiter: RateLimiter, max_workers: int = 1):
        self.storage = storage
        self.security = security
        self.depth = depth
        self.limiter = limiter
        self._jobs: dict[str, Job] = {}
        self._lock = threading.Lock()
        self._executor = ThreadPoolExecutor(max_workers=max_workers, thread_name_prefix="vr3d-job")

    def submit(self, payload: bytes, quality: str, client_ip: str) -> Job:
        if quality not in ("vitl", "vits"):
            raise ValueError("invalid_quality")
        client_key = self.limiter.reserve(client_ip)
        job_id = str(uuid4())
        now = datetime.now(timezone.utc)
        job = Job(
            job_id, client_key, quality, "queued", 0, now,
            now + timedelta(seconds=self.storage.retention_seconds),
        )
        quarantine = self.storage.quarantine_path(job_id)
        try:
            quarantine.write_bytes(payload)
            with self._lock:
                self._jobs[job_id] = job
            self._executor.submit(self._run, job_id)
            return job
        except BaseException:
            quarantine.unlink(missing_ok=True)
            self.limiter.release(client_key)
            raise

    def _set(self, job_id: str, **changes) -> Job:
        with self._lock:
            job = replace(self._jobs[job_id], **changes)
            self._jobs[job_id] = job
            metadata = self.storage.jobs / job_id / "job.json"
            metadata.parent.mkdir(parents=True, exist_ok=True)
            metadata.write_text(json.dumps(job.public(), separators=(",", ":")), encoding="utf-8")
            return job

    def _run(self, job_id: str) -> None:
        job = self.get(job_id)
        assert job is not None
        try:
            self._set(job_id, status="scanning", progress=5)
            source = self.storage.input_path(job_id)
            secured = self.security.process(self.storage.quarantine_path(job_id), source)
            self._set(job_id, status="processing", progress=20)
            with Image.open(secured.sanitized_path) as opened:
                image = np.asarray(opened.convert("RGB"), dtype=np.uint8)
            depth = self.depth.infer(image, job.quality)
            self._set(job_id, progress=60)
            views = render_grid(image, depth)
            self._set(job_id, progress=90)
            build_package(job_id, source, depth, views, job.quality, self.storage.result_path(job_id))
            self._set(job_id, status="complete", progress=100)
        except ImageRejected as exc:
            code = exc.code
            self.storage.quarantine_path(job_id).unlink(missing_ok=True)
            self.storage.result_path(job_id).unlink(missing_ok=True)
            self._set(job_id, status="failed", progress=100, error_code=code)
        except DepthUnavailable as exc:
            code = str(exc) or "depth_unavailable"
            self.storage.quarantine_path(job_id).unlink(missing_ok=True)
            self.storage.result_path(job_id).unlink(missing_ok=True)
            self._set(job_id, status="failed", progress=100, error_code=code)
        except ValueError:
            self.storage.quarantine_path(job_id).unlink(missing_ok=True)
            self.storage.result_path(job_id).unlink(missing_ok=True)
            self._set(job_id, status="failed", progress=100, error_code="processing_invalid")
        except OSError:
            self.storage.quarantine_path(job_id).unlink(missing_ok=True)
            self.storage.result_path(job_id).unlink(missing_ok=True)
            self._set(job_id, status="failed", progress=100, error_code="processing_io_error")
        except Exception:
            self.storage.quarantine_path(job_id).unlink(missing_ok=True)
            self.storage.result_path(job_id).unlink(missing_ok=True)
            self._set(job_id, status="failed", progress=100, error_code="internal_error")
        finally:
            self.limiter.release(job.client_key)

    def get(self, job_id: str) -> Job | None:
        with self._lock:
            job = self._jobs.get(job_id)
        if job and datetime.now(timezone.utc) >= job.expires_at:
            return None
        return job

    def shutdown(self) -> None:
        self._executor.shutdown(wait=True, cancel_futures=False)
