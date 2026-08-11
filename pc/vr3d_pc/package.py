from __future__ import annotations

import hashlib
import json
import os
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from uuid import UUID

import numpy as np
from PIL import Image

from .render import PITCH_ANGLES, ROLL_ANGLES


def _digest(path: Path) -> dict[str, int | str]:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return {"sha256": digest.hexdigest(), "size": path.stat().st_size}


def build_package(package_id: str, source: Path, depth: np.ndarray,
                  views: list[tuple[int, int, int, int, np.ndarray]], variant: str,
                  destination: Path) -> dict:
    UUID(package_id)
    if len(views) != 35:
        raise ValueError("view_count")
    work = destination.parent / f".{package_id}-package"
    if work.exists():
        raise FileExistsError(work)
    (work / "views").mkdir(parents=True)
    try:
        source_copy = work / "source.webp"
        source_copy.write_bytes(source.read_bytes())
        with Image.open(source_copy) as opened:
            width, height = opened.size
        depth_u16 = np.rint(np.clip(depth, 0, 1) * 65535).astype(np.uint16)
        Image.fromarray(depth_u16).save(work / "depth.png", "PNG")
        view_entries = []
        for row, column, pitch, roll, pixels in views:
            relative = f"views/view_r{row}_c{column}.webp"
            Image.fromarray(pixels.astype(np.uint8), "RGB").save(
                work / relative, "WEBP", quality=90, method=6
            )
            view_entries.append({
                "row": row, "column": column, "pitch": pitch,
                "roll": roll, "path": relative,
            })
        payloads = sorted(path for path in work.rglob("*") if path.is_file())
        files = {path.relative_to(work).as_posix(): _digest(path) for path in payloads}
        manifest = {
            "schemaVersion": "1.0",
            "packageId": package_id,
            "createdAt": datetime.now(timezone.utc).isoformat(),
            "source": {"path": "source.webp", "mime": "image/webp", "width": width, "height": height},
            "depth": {"path": "depth.png", "encoding": "png16", "nearValue": 65535},
            "model": {"name": "Video-Depth-Anything", "variant": variant},
            "viewGrid": {
                "rollAngles": list(ROLL_ANGLES), "pitchAngles": list(PITCH_ANGLES),
                "views": view_entries,
            },
            "files": files,
        }
        (work / "manifest.json").write_text(
            json.dumps(manifest, ensure_ascii=False, separators=(",", ":")), encoding="utf-8"
        )
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary = destination.with_suffix(destination.suffix + ".tmp")
        with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=6) as archive:
            for path in sorted(work.rglob("*")):
                if path.is_file():
                    archive.write(path, path.relative_to(work).as_posix())
        os.replace(temporary, destination)
        return manifest
    finally:
        for path in sorted(work.rglob("*"), reverse=True) if work.exists() else []:
            if path.is_file():
                path.unlink()
            elif path.is_dir():
                path.rmdir()
        if work.exists():
            work.rmdir()
