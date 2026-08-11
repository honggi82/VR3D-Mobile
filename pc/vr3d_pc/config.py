from __future__ import annotations

import json
import os
from dataclasses import dataclass
from pathlib import Path

MIB = 1024 * 1024


@dataclass(frozen=True)
class Settings:
    data_dir: Path = Path(__file__).resolve().parents[1] / "data"
    runtime_dir: Path = Path(r"C:\Users\user\Documents\VR3D_v1_runtime")
    max_upload_bytes: int = 25 * MIB
    max_pixels: int = 50_000_000
    max_edge: int = 1920
    retention_seconds: int = 24 * 60 * 60
    jobs_per_hour: int = 3
    max_active_per_client: int = 1
    scanner_command: tuple[str, ...] = ()
    content_command: tuple[str, ...] = ()
    scanner_timeout_seconds: int = 120
    content_timeout_seconds: int = 120

    @classmethod
    def from_env(cls) -> "Settings":
        def command(name: str) -> tuple[str, ...]:
            value = os.environ.get(name, "")
            if not value:
                return ()
            parsed = json.loads(value)
            if not isinstance(parsed, list) or not parsed or not all(isinstance(x, str) for x in parsed):
                raise ValueError(f"{name} must be a non-empty JSON string array")
            return tuple(parsed)

        return cls(
            data_dir=Path(os.environ.get("VR3D_DATA_DIR", cls.data_dir)),
            runtime_dir=Path(os.environ.get("VR3D_RUNTIME_DIR", cls.runtime_dir)),
            scanner_command=command("VR3D_SCANNER_COMMAND"),
            content_command=command("VR3D_CONTENT_COMMAND"),
        )
