from __future__ import annotations

import json
from pathlib import Path

from vr3d_pc.gui import EndpointWriter, collect_local_status


def test_endpoint_writer_is_fail_closed_and_atomic(tmp_path: Path):
    destination = tmp_path / "endpoint.json"
    writer = EndpointWriter(destination)
    writer.write("https://example.trycloudflare.com", True)
    online = json.loads(destination.read_text(encoding="utf-8"))
    assert online["online"] is True
    assert online["api_base"] == "https://example.trycloudflare.com"
    assert online["api_version"] == "v1"
    writer.write(None, False)
    offline = json.loads(destination.read_text(encoding="utf-8"))
    assert offline["online"] is False
    assert offline["api_base"] is None
    assert not destination.with_suffix(".json.tmp").exists()


def test_status_probe_reports_queue_retention_and_runtime(tmp_path: Path):
    jobs = tmp_path / "data" / "jobs" / "one"
    jobs.mkdir(parents=True)
    (jobs / "job.json").write_text(
        json.dumps({"status": "queued", "expiresAt": "2999-01-01T00:00:00+00:00"}),
        encoding="utf-8",
    )
    runtime = tmp_path / "runtime"
    weights = runtime / "models" / "vda"
    weights.mkdir(parents=True)
    for name in ("vits", "vitl"):
        (weights / f"video_depth_anything_{name}.pth").write_bytes(b"weight")
    status = collect_local_status(tmp_path / "data", runtime)
    assert "Q 1" in status["jobs"]
    assert status["runtime"] == "ready"
    assert status["retention"] != "없음 / none"
