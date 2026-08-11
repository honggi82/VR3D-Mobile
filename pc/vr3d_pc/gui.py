from __future__ import annotations

import io
import json
import os
import queue
import re
import subprocess
import sys
import threading
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import URLError
from urllib.request import urlopen

if sys.stdout is None:
    sys.stdout = io.StringIO()
if sys.stderr is None:
    sys.stderr = io.StringIO()

import tkinter as tk
from tkinter import ttk


@dataclass(frozen=True)
class ProcessEvent:
    source: str
    message: str


class EndpointWriter:
    def __init__(self, path: Path):
        self.path = path

    def write(self, api_base: str | None, online: bool) -> None:
        document = {
            "api_version": "v1",
            "online": online,
            "api_base": api_base if online else None,
            "updated_at": datetime.now(timezone.utc).isoformat(),
            "expires_at": None,
        }
        self.path.parent.mkdir(parents=True, exist_ok=True)
        temporary = self.path.with_suffix(self.path.suffix + ".tmp")
        temporary.write_text(json.dumps(document, indent=2) + "\n", encoding="utf-8")
        os.replace(temporary, self.path)


def collect_local_status(data_dir: Path, runtime_dir: Path) -> dict[str, str]:
    gates = "offline"
    try:
        with urlopen("http://127.0.0.1:8765/api/v1/health", timeout=3) as response:
            health = json.load(response)
        gates = ", ".join(
            f"{name}={'OK' if state.get('ready') else state.get('code', 'blocked')}"
            for name, state in health.get("gates", {}).items()
        ) or "unavailable"
    except (OSError, URLError, ValueError, json.JSONDecodeError):
        pass
    counts = {"queued": 0, "scanning": 0, "processing": 0, "complete": 0, "failed": 0}
    expirations: list[datetime] = []
    for metadata in (data_dir / "jobs").glob("*/job.json"):
        try:
            job = json.loads(metadata.read_text(encoding="utf-8"))
            status = job.get("status")
            if status in counts:
                counts[status] += 1
            expirations.append(datetime.fromisoformat(job["expiresAt"]))
        except (OSError, ValueError, KeyError, json.JSONDecodeError):
            continue
    now = datetime.now(timezone.utc)
    remaining = min((expiry - now for expiry in expirations), default=None)
    retention = "없음 / none" if remaining is None else f"{max(0, int(remaining.total_seconds() // 60))} min"
    weights = runtime_dir / "models" / "vda"
    runtime = "ready" if all((weights / f"video_depth_anything_{name}.pth").is_file() for name in ("vits", "vitl")) else "missing"
    try:
        import torch
        gpu = torch.cuda.get_device_name(0) if torch.cuda.is_available() else "CUDA unavailable"
    except (ImportError, RuntimeError):
        gpu = "unavailable"
    return {
        "gates": gates,
        "jobs": f"Q {counts['queued']} / scan {counts['scanning']} / run {counts['processing']} / done {counts['complete']} / failed {counts['failed']}",
        "retention": retention,
        "gpu": gpu,
        "runtime": runtime,
    }


class ChildProcesses:
    def __init__(self, events: queue.Queue[ProcessEvent]):
        self.events = events
        self.children: dict[str, subprocess.Popen] = {}

    def start(self, name: str, argv: list[str]) -> None:
        if name in self.children and self.children[name].poll() is None:
            return
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" and hasattr(subprocess, "CREATE_NO_WINDOW") else 0
        environment = os.environ.copy()
        package_root = str(Path(__file__).resolve().parents[1])
        environment["PYTHONPATH"] = package_root + os.pathsep + environment.get("PYTHONPATH", "")
        process = subprocess.Popen(
            argv, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
            text=True, encoding="utf-8", errors="replace", creationflags=flags,
            env=environment,
        )
        self.children[name] = process

        def reader() -> None:
            assert process.stdout is not None
            for line in process.stdout:
                self.events.put(ProcessEvent(name, line.rstrip()))
            self.events.put(ProcessEvent(name, f"stopped ({process.wait()})"))

        threading.Thread(target=reader, daemon=True).start()

    def stop(self, name: str) -> None:
        process = self.children.get(name)
        if process is None or process.poll() is not None:
            return
        process.terminate()
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)

    def stop_all(self) -> None:
        for name in tuple(self.children):
            self.stop(name)


class AdminApp:
    def __init__(self, root: tk.Tk):
        self.root = root
        self.root.title("VR3D Mobile PC")
        self.events: queue.Queue[ProcessEvent] = queue.Queue()
        self.processes = ChildProcesses(self.events)
        endpoint_path = Path(os.environ.get(
            "VR3D_ENDPOINT_FILE", Path(__file__).resolve().parents[2] / "web" / "endpoint.json"
        ))
        self.endpoint = EndpointWriter(endpoint_path)
        self.data_dir = Path(os.environ.get("VR3D_DATA_DIR", Path(__file__).resolve().parents[1] / "data"))
        self.runtime_dir = Path(os.environ.get("VR3D_RUNTIME_DIR", r"C:\Users\user\Documents\VR3D_v1_runtime"))
        self.tunnel_url: str | None = None
        self.status = tk.StringVar(value="중지됨 / Stopped")
        ttk.Label(root, textvariable=self.status).pack(anchor="w", padx=12, pady=8)
        controls = ttk.Frame(root)
        controls.pack(fill="x", padx=12)
        ttk.Button(controls, text="서버 시작 / Start server", command=self.start_server).pack(side="left")
        ttk.Button(controls, text="주소 갱신 / Refresh endpoint", command=self.refresh_endpoint).pack(side="left", padx=8)
        ttk.Button(controls, text="중지 / Stop", command=self.stop_all).pack(side="left", padx=8)
        status_frame = ttk.LabelFrame(root, text="운영 상태 / Operations")
        status_frame.pack(fill="x", padx=12, pady=(10, 0))
        self.status_values: dict[str, tk.StringVar] = {}
        labels = {
            "gates": "V3 · scanner · content",
            "jobs": "대기열 / Jobs",
            "retention": "다음 자동삭제 / Next cleanup",
            "gpu": "GPU",
            "runtime": "VDA runtime",
            "endpoint": "Tunnel endpoint",
        }
        for row, (key, label) in enumerate(labels.items()):
            ttk.Label(status_frame, text=label).grid(row=row, column=0, sticky="w", padx=8, pady=2)
            value = tk.StringVar(value="-")
            self.status_values[key] = value
            ttk.Label(status_frame, textvariable=value).grid(row=row, column=1, sticky="w", padx=8, pady=2)
        self.log = tk.Text(root, width=96, height=24, state="disabled")
        self.log.pack(fill="both", expand=True, padx=12, pady=12)
        self.root.protocol("WM_DELETE_WINDOW", self.close)
        self.root.after(100, self.drain_events)
        self.root.after(200, self.refresh_status)

    def start_server(self) -> None:
        self.processes.start("server", [sys.executable, "-m", "vr3d_pc.api", "--host", "127.0.0.1"])
        tunnel = os.environ.get("VR3D_CLOUDFLARED", "")
        if tunnel:
            self.processes.start("tunnel", [tunnel, "tunnel", "--url", "http://127.0.0.1:8765"])
        self.status.set("실행 중 / Running (security gates may still be closed)")

    def stop_all(self) -> None:
        self.processes.stop_all()
        self.endpoint.write(None, False)
        self.tunnel_url = None
        self.status.set("중지됨 / Stopped")

    def refresh_endpoint(self) -> None:
        def check() -> None:
            ready = False
            try:
                with urlopen("http://127.0.0.1:8765/api/v1/health", timeout=5) as response:
                    ready = response.status == 200 and json.load(response).get("publicReady") is True
            except (OSError, URLError, ValueError, json.JSONDecodeError):
                ready = False
            self.endpoint.write(self.tunnel_url, bool(ready and self.tunnel_url))
            state = "공개 주소 준비됨 / Public endpoint ready" if ready and self.tunnel_url else "보안 게이트 닫힘 / Security gates closed"
            self.events.put(ProcessEvent("endpoint", state))

        threading.Thread(target=check, daemon=True).start()

    def refresh_status(self) -> None:
        def collect() -> None:
            snapshot = collect_local_status(self.data_dir, self.runtime_dir)
            self.events.put(ProcessEvent("status", json.dumps(snapshot)))

        threading.Thread(target=collect, daemon=True).start()

    def drain_events(self) -> None:
        while True:
            try:
                event = self.events.get_nowait()
            except queue.Empty:
                break
            if event.source == "tunnel":
                match = re.search(r"https://[a-z0-9-]+\.trycloudflare\.com", event.message, re.IGNORECASE)
                if match:
                    self.tunnel_url = match.group(0).lower()
                    self.status_values["endpoint"].set(self.tunnel_url)
                    self.refresh_endpoint()
            if event.source == "status":
                snapshot = json.loads(event.message)
                for key, value in snapshot.items():
                    self.status_values[key].set(value)
                self.root.after(5000, self.refresh_status)
                continue
            self.log.configure(state="normal")
            self.log.insert("end", f"[{event.source}] {event.message}\n")
            self.log.see("end")
            self.log.configure(state="disabled")
        self.root.after(100, self.drain_events)

    def close(self) -> None:
        self.stop_all()
        self.root.destroy()


def main() -> None:
    root = tk.Tk()
    AdminApp(root)
    root.mainloop()


if __name__ == "__main__":
    main()
