from __future__ import annotations

import io
from pathlib import Path

import numpy as np
from PIL import Image

from vr3d_pc.security import GateHealth


def image_bytes(fmt: str = "PNG", size: tuple[int, int] = (16, 12)) -> bytes:
    stream = io.BytesIO()
    Image.new("RGB", size, (20, 80, 160)).save(stream, fmt)
    return stream.getvalue()


class AllowGate:
    def health(self) -> GateHealth:
        return GateHealth(True, "ok")

    def scan(self, path: Path) -> bool:
        return True

    def is_safe(self, path: Path) -> bool:
        return True


class DenyGate(AllowGate):
    def scan(self, path: Path) -> bool:
        return False

    def is_safe(self, path: Path) -> bool:
        return False


class FakeDepth:
    def infer(self, image: np.ndarray, variant: str) -> np.ndarray:
        height, width = image.shape[:2]
        return np.tile(np.linspace(0, 1, width, dtype=np.float32), (height, 1))
