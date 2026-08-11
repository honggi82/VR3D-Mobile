from __future__ import annotations

import shutil
import time
from pathlib import Path


class Storage:
    def __init__(self, root: Path, retention_seconds: int = 86400, clock=time.time):
        self.root = root
        self.retention_seconds = retention_seconds
        self.clock = clock
        self.quarantine = root / "quarantine"
        self.jobs = root / "jobs"
        self.results = root / "results"
        for folder in (self.quarantine, self.jobs, self.results):
            folder.mkdir(parents=True, exist_ok=True)

    def quarantine_path(self, job_id: str) -> Path:
        return self.quarantine / f"{job_id}.upload"

    def input_path(self, job_id: str) -> Path:
        folder = self.jobs / job_id
        folder.mkdir(parents=True, exist_ok=True)
        return folder / "source.webp"

    def result_path(self, job_id: str) -> Path:
        return self.results / f"{job_id}.vr3d"

    def cleanup_expired(self) -> list[str]:
        cutoff = self.clock() - self.retention_seconds
        removed: list[str] = []
        for folder in (self.quarantine, self.jobs, self.results):
            for path in list(folder.iterdir()):
                try:
                    expired = path.stat().st_mtime <= cutoff
                except FileNotFoundError:
                    continue
                if not expired:
                    continue
                if path.is_dir():
                    shutil.rmtree(path)
                else:
                    path.unlink(missing_ok=True)
                removed.append(path.name)
        return removed
