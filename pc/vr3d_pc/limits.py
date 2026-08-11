from __future__ import annotations

import hashlib
import secrets
import threading
import time
from collections import defaultdict, deque


class LimitExceeded(RuntimeError):
    pass


class RateLimiter:
    def __init__(self, per_hour: int = 3, max_active: int = 1, clock=time.time):
        self.per_hour = per_hour
        self.max_active = max_active
        self.clock = clock
        self._salt = secrets.token_bytes(32)
        self._history: dict[str, deque[float]] = defaultdict(deque)
        self._active: dict[str, int] = defaultdict(int)
        self._lock = threading.Lock()

    def key(self, client_ip: str) -> str:
        return hashlib.blake2s(client_ip.encode("utf-8"), key=self._salt, digest_size=12).hexdigest()

    def reserve(self, client_ip: str) -> str:
        key = self.key(client_ip)
        now = self.clock()
        with self._lock:
            history = self._history[key]
            while history and history[0] <= now - 3600:
                history.popleft()
            if self._active[key] >= self.max_active:
                raise LimitExceeded("active_limit")
            if len(history) >= self.per_hour:
                raise LimitExceeded("hourly_limit")
            history.append(now)
            self._active[key] += 1
        return key

    def release(self, key: str) -> None:
        with self._lock:
            self._active[key] = max(0, self._active[key] - 1)
