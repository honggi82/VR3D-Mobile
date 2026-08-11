from __future__ import annotations

import os
from pathlib import Path

import pytest

from vr3d_pc.limits import LimitExceeded, RateLimiter
from vr3d_pc.storage import Storage


def test_rate_limits_active_and_rolling_hour():
    now = [10000.0]
    limiter = RateLimiter(per_hour=3, max_active=1, clock=lambda: now[0])
    key = limiter.reserve("203.0.113.1")
    with pytest.raises(LimitExceeded, match="active_limit"):
        limiter.reserve("203.0.113.1")
    limiter.release(key)
    limiter.release(limiter.reserve("203.0.113.1"))
    limiter.release(limiter.reserve("203.0.113.1"))
    with pytest.raises(LimitExceeded, match="hourly_limit"):
        limiter.reserve("203.0.113.1")
    now[0] += 3601
    limiter.release(limiter.reserve("203.0.113.1"))


def test_cleanup_removes_only_items_older_than_24_hours(tmp_path: Path):
    now = 200000.0
    storage = Storage(tmp_path, retention_seconds=86400, clock=lambda: now)
    old = storage.results / "old.vr3d"
    fresh = storage.results / "fresh.vr3d"
    old.write_bytes(b"old")
    fresh.write_bytes(b"fresh")
    os.utime(old, (now - 86401, now - 86401))
    os.utime(fresh, (now - 100, now - 100))
    assert storage.cleanup_expired() == ["old.vr3d"]
    assert not old.exists()
    assert fresh.exists()
