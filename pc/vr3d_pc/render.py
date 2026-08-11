from __future__ import annotations

import math

import numpy as np
from PIL import Image

ROLL_ANGLES = (-12, -8, -4, 0, 4, 8, 12)
PITCH_ANGLES = (-8, -4, 0, 4, 8)


def _fill_holes(image: np.ndarray, valid: np.ndarray) -> np.ndarray:
    filled = image.copy()
    known = valid.copy()
    height, width = known.shape
    for _ in range(max(height, width)):
        if known.all():
            break
        total = np.zeros_like(filled, dtype=np.float32)
        count = np.zeros((height, width, 1), dtype=np.float32)
        for dy, dx in ((0, -1), (0, 1), (-1, 0), (1, 0)):
            shifted_known = np.roll(known, (dy, dx), axis=(0, 1))
            shifted_image = np.roll(filled, (dy, dx), axis=(0, 1))
            if dy == -1: shifted_known[-1, :] = False
            if dy == 1: shifted_known[0, :] = False
            if dx == -1: shifted_known[:, -1] = False
            if dx == 1: shifted_known[:, 0] = False
            candidates = (~known) & shifted_known
            total[candidates] += shifted_image[candidates]
            count[candidates, 0] += 1
        newly = (~known) & (count[..., 0] > 0)
        if not newly.any():
            break
        filled[newly] = np.rint(total[newly] / count[newly]).astype(np.uint8)
        known[newly] = True
    return filled


def _safe_crop(image: np.ndarray, margin_x: int, margin_y: int) -> np.ndarray:
    height, width = image.shape[:2]
    margin_x = min(max(margin_x, 0), max((width - 2) // 4, 0))
    margin_y = min(max(margin_y, 0), max((height - 2) // 4, 0))
    if margin_x == 0 and margin_y == 0:
        return image
    cropped = image[margin_y:height - margin_y, margin_x:width - margin_x]
    return np.asarray(
        Image.fromarray(cropped).resize((width, height), Image.Resampling.LANCZOS),
        dtype=np.uint8,
    )


def render_view(image: np.ndarray, depth: np.ndarray, roll: float, pitch: float,
                strength: float = 0.8) -> np.ndarray:
    if image.ndim != 3 or image.shape[2] != 3 or depth.shape != image.shape[:2]:
        raise ValueError("shape_mismatch")
    if roll == 0 and pitch == 0:
        return image.copy()
    height, width = depth.shape
    d = np.clip(depth.astype(np.float32), 0.0, 1.0)
    centered = d - 0.5
    shift_x = math.tan(math.radians(roll)) * width * strength * centered
    shift_y = -math.tan(math.radians(pitch)) * height * strength * centered
    yy, xx = np.indices((height, width), dtype=np.float32)
    tx = np.rint(xx + shift_x).astype(np.int32)
    ty = np.rint(yy + shift_y).astype(np.int32)
    inside = (tx >= 0) & (tx < width) & (ty >= 0) & (ty < height)
    target = (ty[inside] * width + tx[inside]).astype(np.int64)
    source = np.flatnonzero(inside)
    near = d.ravel()[source]
    order = np.lexsort((near, target))
    ordered_target = target[order]
    group_end = np.r_[ordered_target[1:] != ordered_target[:-1], True]
    winners = source[order[group_end]]
    destinations = target[order[group_end]]
    output = np.zeros_like(image).reshape(-1, 3)
    output[destinations] = image.reshape(-1, 3)[winners]
    valid = np.zeros(height * width, dtype=bool)
    valid[destinations] = True
    filled = _fill_holes(output.reshape(height, width, 3), valid.reshape(height, width))
    margin_x = int(math.ceil(float(np.max(np.abs(shift_x))))) + (1 if roll else 0)
    margin_y = int(math.ceil(float(np.max(np.abs(shift_y))))) + (1 if pitch else 0)
    return _safe_crop(filled, margin_x, margin_y)


def render_grid(image: np.ndarray, depth: np.ndarray) -> list[tuple[int, int, int, int, np.ndarray]]:
    return [
        (row, column, pitch, roll, render_view(image, depth, roll, pitch))
        for row, pitch in enumerate(PITCH_ANGLES)
        for column, roll in enumerate(ROLL_ANGLES)
    ]
