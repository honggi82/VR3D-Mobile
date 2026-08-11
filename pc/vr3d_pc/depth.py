from __future__ import annotations

import sys
import os
import subprocess
import tempfile
import threading
from pathlib import Path

import numpy as np


class DepthUnavailable(RuntimeError):
    pass


class VideoDepthAnythingAdapter:
    CONFIGS = {
        "vits": {"encoder": "vits", "features": 64, "out_channels": [48, 96, 192, 384]},
        "vitl": {"encoder": "vitl", "features": 256, "out_channels": [256, 512, 1024, 1024]},
    }

    def __init__(self, runtime_dir: Path, device: str | None = None,
                 allow_subprocess: bool = True):
        self.runtime_dir = runtime_dir
        self.device = device
        self.allow_subprocess = allow_subprocess
        self._models: dict[str, object] = {}
        self._lock = threading.Lock()

    def _load(self, variant: str):
        if variant not in self.CONFIGS:
            raise ValueError("invalid_quality")
        repo = self.runtime_dir / "models" / "Video-Depth-Anything"
        weight = self.runtime_dir / "models" / "vda" / f"video_depth_anything_{variant}.pth"
        if not repo.is_dir() or not weight.is_file():
            raise DepthUnavailable("depth_runtime_missing")
        try:
            import torch
            if str(repo) not in sys.path:
                sys.path.insert(0, str(repo))
            from video_depth_anything.video_depth_stream import VideoDepthAnything
        except (ImportError, OSError) as exc:
            raise DepthUnavailable("depth_import_failed") from exc
        device = self.device or ("cuda" if torch.cuda.is_available() else "cpu")
        if device != "cuda":
            raise DepthUnavailable("cuda_required")
        model = VideoDepthAnything(**self.CONFIGS[variant])
        checkpoint = torch.load(str(weight), map_location="cpu", weights_only=True)
        model.load_state_dict(checkpoint, strict=True)
        return model.to(device).eval(), device

    def infer(self, image_rgb: np.ndarray, variant: str) -> np.ndarray:
        try:
            with self._lock:
                if variant not in self._models:
                    self._models[variant] = self._load(variant)
                model, device = self._models[variant]
                model.transform = None
                model.frame_id_list = []
                model.frame_cache_list = []
                model.id = -1
                raw = model.infer_video_depth_one(
                    np.ascontiguousarray(image_rgb), input_size=518, device=device, fp32=False
                )
        except DepthUnavailable:
            if not self.allow_subprocess:
                raise
            return self._infer_subprocess(image_rgb, variant)
        return self._normalize(raw)

    def _infer_subprocess(self, image_rgb: np.ndarray, variant: str) -> np.ndarray:
        python = self.runtime_dir / "venv" / "Scripts" / "python.exe"
        worker = Path(__file__).with_name("depth_worker.py")
        if not python.is_file() or not worker.is_file():
            raise DepthUnavailable("depth_python_missing")
        flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" and hasattr(subprocess, "CREATE_NO_WINDOW") else 0
        with tempfile.TemporaryDirectory(prefix="vr3d-depth-") as temporary:
            source = Path(temporary) / "input.npy"
            destination = Path(temporary) / "depth.npy"
            np.save(source, np.ascontiguousarray(image_rgb), allow_pickle=False)
            try:
                result = subprocess.run(
                    [str(python), str(worker), str(self.runtime_dir), variant, str(source), str(destination)],
                    stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                    text=True, encoding="utf-8", errors="replace", timeout=1800,
                    creationflags=flags, check=False,
                )
            except (OSError, subprocess.TimeoutExpired) as exc:
                raise DepthUnavailable("depth_worker_failed") from exc
            if result.returncode != 0 or not destination.is_file():
                raise DepthUnavailable("depth_worker_failed")
            return np.load(destination, allow_pickle=False)

    @staticmethod
    def _normalize(raw: np.ndarray) -> np.ndarray:
        raw = np.asarray(raw, dtype=np.float32)
        finite = np.isfinite(raw)
        if raw.ndim != 2 or not finite.any():
            raise DepthUnavailable("depth_invalid")
        values = raw[finite]
        low, high = np.percentile(values, (2, 98))
        normalized = np.clip((raw - low) / max(float(high - low), 1e-6), 0.0, 1.0)
        normalized[~finite] = 0.0
        return normalized.astype(np.float32)
