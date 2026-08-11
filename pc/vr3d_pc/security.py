from __future__ import annotations

import os
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Protocol

from .images import ImageInfo, ImageRejected, inspect_image, inspect_signature, sanitize_image


@dataclass(frozen=True)
class GateHealth:
    ready: bool
    code: str


class HealthCheck(Protocol):
    def health(self) -> GateHealth: ...


class FileScanner(HealthCheck, Protocol):
    def scan(self, path: Path) -> bool: ...


class ContentSafety(HealthCheck, Protocol):
    def is_safe(self, path: Path) -> bool: ...


class V3RealtimeHealth:
    """Checks presence of both the V3 UI and its protection service."""

    def health(self) -> GateHealth:
        if os.name != "nt":
            return GateHealth(False, "v3_unsupported_os")
        try:
            import psutil
            names = set()
            for process in psutil.process_iter(["name"]):
                try:
                    names.add((process.info.get("name") or "").lower())
                except psutil.Error:
                    continue
            ready = "v3ui.exe" in names and "asdsvc.exe" in names
            return GateHealth(ready, "ok" if ready else "v3_not_running")
        except ImportError:
            pass
        except psutil.Error:
            pass
        flags = subprocess.CREATE_NO_WINDOW if hasattr(subprocess, "CREATE_NO_WINDOW") else 0
        try:
            result = subprocess.run(
                ["tasklist", "/FO", "CSV", "/NH"], capture_output=True, text=True,
                timeout=10, creationflags=flags, check=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            return GateHealth(False, "v3_unverifiable")
        names = result.stdout.lower()
        ready = result.returncode == 0 and "v3ui.exe" in names and "asdsvc.exe" in names
        return GateHealth(ready, "ok" if ready else "v3_not_running")


class ExplicitCommandGate:
    """Adapter for a locally configured scanner with an explicit clean exit code.

    The command is a JSON argv list supplied by the operator. Exactly one ``{path}``
    placeholder is replaced; shell parsing is never used. Exit code 0 means clean.
    """

    def __init__(self, command: tuple[str, ...], timeout: int, kind: str,
                 probe_path: Path | None = None):
        self.command = command
        self.timeout = timeout
        self.kind = kind
        self.probe_path = probe_path

    def _configured_health(self) -> GateHealth:
        if not self.command:
            return GateHealth(False, f"{self.kind}_unconfigured")
        if sum(part.count("{path}") for part in self.command) != 1:
            return GateHealth(False, f"{self.kind}_invalid_command")
        executable = Path(self.command[0])
        if not executable.is_file():
            return GateHealth(False, f"{self.kind}_missing")
        return GateHealth(True, "ok")

    def _run_command(self, path: Path) -> bool:
        if not self._configured_health().ready:
            return False
        argv = [part.replace("{path}", str(path)) for part in self.command]
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" and hasattr(subprocess, "CREATE_NO_WINDOW") else 0
        try:
            result = subprocess.run(
                argv, stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL, timeout=self.timeout, creationflags=flags,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired):
            return False
        return result.returncode == 0

    def health(self) -> GateHealth:
        configured = self._configured_health()
        if not configured.ready:
            return configured
        if self.probe_path is None or not self.probe_path.is_file():
            return GateHealth(False, f"{self.kind}_probe_unavailable")
        ready = self._run_command(self.probe_path)
        return GateHealth(ready, "ok" if ready else f"{self.kind}_probe_failed")

    def scan(self, path: Path) -> bool:
        return self._run_command(path)

    def is_safe(self, path: Path) -> bool:
        return self._run_command(path)


@dataclass(frozen=True)
class SecurityResult:
    image: ImageInfo
    sanitized_path: Path


class SecurityPipeline:
    def __init__(self, v3: HealthCheck, scanner: FileScanner, content: ContentSafety,
                 max_bytes: int, max_pixels: int, max_edge: int):
        self.v3 = v3
        self.scanner = scanner
        self.content = content
        self.max_bytes = max_bytes
        self.max_pixels = max_pixels
        self.max_edge = max_edge

    def health(self) -> dict[str, GateHealth]:
        return {
            "v3": self.v3.health(),
            "scanner": self.scanner.health(),
            "content": self.content.health(),
        }

    def ready(self) -> bool:
        return all(state.ready for state in self.health().values())

    def process(self, quarantine: Path, sanitized: Path) -> SecurityResult:
        try:
            states = self.health()
            if not all(state.ready for state in states.values()):
                raise ImageRejected(next(state.code for state in states.values() if not state.ready))
            data = quarantine.read_bytes()
            inspect_signature(data, self.max_bytes)
            if not self.scanner.scan(quarantine):
                raise ImageRejected("scanner_rejected")
            inspect_image(data, self.max_bytes, self.max_pixels)
            if not self.content.is_safe(quarantine):
                raise ImageRejected("content_rejected")
            info = sanitize_image(quarantine, sanitized, self.max_edge)
            return SecurityResult(info, sanitized)
        finally:
            quarantine.unlink(missing_ok=True)
