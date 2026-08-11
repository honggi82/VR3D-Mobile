from __future__ import annotations

import hashlib
import json
import zipfile
from pathlib import Path
from uuid import uuid4

import numpy as np
from jsonschema import validate
from PIL import Image

from vr3d_pc.package import build_package
from vr3d_pc.render import render_grid, render_view


def scene():
    image = np.zeros((18, 24, 3), dtype=np.uint8)
    image[..., 0] = np.arange(24, dtype=np.uint8)
    image[..., 1] = np.arange(18, dtype=np.uint8)[:, None]
    depth = np.zeros((18, 24), dtype=np.float32)
    depth[5:13, 8:16] = 1.0
    return image, depth


def test_renderer_center_is_exact_and_both_axes_change_pixels():
    image, depth = scene()
    assert np.array_equal(render_view(image, depth, 0, 0), image)
    horizontal = render_view(image, depth, 12, 0)
    vertical = render_view(image, depth, 0, 8)
    assert not np.array_equal(horizontal, image)
    assert not np.array_equal(vertical, image)
    assert horizontal.shape == image.shape == vertical.shape


def test_grid_and_package_have_35_views_and_verified_hashes(tmp_path: Path):
    image, depth = scene()
    source = tmp_path / "source.webp"
    Image.fromarray(image, "RGB").save(source, "WEBP")
    views = render_grid(image, depth)
    assert len(views) == 35
    destination = tmp_path / f"{uuid4()}.vr3d"
    package_id = destination.stem
    manifest = build_package(package_id, source, depth, views, "vits", destination)
    assert len(manifest["files"]) == 37
    with zipfile.ZipFile(destination) as archive:
        assert all(not name.startswith(("/", "\\")) and ".." not in Path(name).parts for name in archive.namelist())
        decoded = json.loads(archive.read("manifest.json"))
        schema = json.loads((Path(__file__).resolve().parents[2] / "contracts" / "package-v1.schema.json").read_text(encoding="utf-8"))
        validate(decoded, schema)
        assert decoded["viewGrid"]["views"][17]["pitch"] == 0
        assert decoded["viewGrid"]["views"][17]["roll"] == 0
        for name, expected in decoded["files"].items():
            payload = archive.read(name)
            assert hashlib.sha256(payload).hexdigest() == expected["sha256"]
            assert len(payload) == expected["size"]
