from __future__ import annotations

from pathlib import Path
import sys

import pytest
from PIL import Image

from helpers import AllowGate, DenyGate, image_bytes
from vr3d_pc.images import ImageRejected, inspect_image
from vr3d_pc.security import ExplicitCommandGate, SecurityPipeline


def test_signature_and_full_decode_block_disguised_and_corrupt_files():
    with pytest.raises(ImageRejected, match="signature"):
        inspect_image(b"MZ" + b"not an image", 1000, 1000)
    with pytest.raises(ImageRejected, match="decode"):
        inspect_image(b"\x89PNG\r\n\x1a\ntruncated", 1000, 1000)


def test_pixel_limit_is_enforced_after_header_decode():
    with pytest.raises(ImageRejected, match="pixel_limit"):
        inspect_image(image_bytes(size=(11, 10)), 10000, 100)


def test_sanitize_removes_metadata_and_caps_edge(tmp_path: Path):
    raw = tmp_path / "q.upload"
    raw.write_bytes(image_bytes("JPEG", (40, 20)))
    clean = tmp_path / "source.webp"
    gate = AllowGate()
    result = SecurityPipeline(gate, gate, gate, 10000, 1000, 16).process(raw, clean)
    assert not raw.exists()
    assert result.image.format == "WEBP"
    with Image.open(clean) as opened:
        assert opened.size == (16, 8)
        assert not opened.getexif()


def test_failed_explicit_scanner_deletes_quarantine(tmp_path: Path):
    raw = tmp_path / "q.upload"
    raw.write_bytes(image_bytes())
    clean = tmp_path / "source.webp"
    allow = AllowGate()
    with pytest.raises(ImageRejected, match="scanner_rejected"):
        SecurityPipeline(allow, DenyGate(), allow, 10000, 1000, 16).process(raw, clean)
    assert not raw.exists()
    assert not clean.exists()


def test_explicit_scanner_precedes_full_decode(tmp_path: Path):
    raw = tmp_path / "q.upload"
    raw.write_bytes(b"\x89PNG\r\n\x1a\ncorrupt")
    allow = AllowGate()
    with pytest.raises(ImageRejected, match="scanner_rejected"):
        SecurityPipeline(allow, DenyGate(), allow, 10000, 1000, 16).process(
            raw, tmp_path / "clean.webp"
        )
    assert not raw.exists()


def test_unhealthy_content_gate_fails_closed_and_deletes_payload(tmp_path: Path):
    class Unhealthy(AllowGate):
        def health(self):
            from vr3d_pc.security import GateHealth
            return GateHealth(False, "content_unconfigured")

    raw = tmp_path / "q.upload"
    raw.write_bytes(image_bytes())
    allow = AllowGate()
    with pytest.raises(ImageRejected, match="content_unconfigured"):
        SecurityPipeline(allow, allow, Unhealthy(), 10000, 1000, 16).process(raw, tmp_path / "clean.webp")
    assert not raw.exists()


def test_explicit_gate_health_requires_successful_real_probe(tmp_path: Path):
    probe = tmp_path / "safe.png"
    probe.write_bytes(image_bytes())
    clean = ExplicitCommandGate(
        (sys.executable, "-c", "raise SystemExit(0)", "{path}"), 5, "scanner", probe
    )
    broken = ExplicitCommandGate(
        (sys.executable, "-c", "raise SystemExit(7)", "{path}"), 5, "scanner", probe
    )
    assert clean.health().ready is True
    assert broken.health().ready is False
    assert broken.health().code == "scanner_probe_failed"
