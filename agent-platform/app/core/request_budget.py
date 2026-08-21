import asyncio
import time
from contextlib import asynccontextmanager
from contextvars import ContextVar
from typing import AsyncIterator

_deadline: ContextVar[float | None] = ContextVar("learnflow_request_deadline", default=None)


@asynccontextmanager
async def request_budget(timeout_seconds: float) -> AsyncIterator[None]:
    timeout_seconds = max(0.1, timeout_seconds)
    current_deadline = _deadline.get()
    deadline = time.monotonic() + timeout_seconds
    if current_deadline is not None:
        deadline = min(deadline, current_deadline)
    token = _deadline.set(deadline)
    try:
        async with asyncio.timeout(max(0.1, deadline - time.monotonic())):
            yield
    finally:
        _deadline.reset(token)


def remaining_seconds(default: float) -> float:
    deadline = _deadline.get()
    if deadline is None:
        return default
    return max(0.1, min(default, deadline - time.monotonic()))


def downstream_seconds(default: float) -> float:
    available = remaining_seconds(default)
    reserve = min(2.0, max(0.05, available * 0.1))
    return max(0.01, available - reserve)


def timeout_from_header(value: str | None) -> float | None:
    if not value:
        return None
    try:
        timeout_ms = int(value)
    except ValueError:
        return None
    timeout_ms = max(100, min(timeout_ms, 300_000))
    upstream_seconds = timeout_ms / 1000.0
    reserve = min(2.0, max(0.1, upstream_seconds * 0.1))
    return max(0.1, upstream_seconds - reserve)
