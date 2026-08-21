import asyncio
import os

import httpx

_client: httpx.AsyncClient | None = None
_client_lock = asyncio.Lock()


def _positive_float(name: str, default: float) -> float:
    try:
        value = float(os.getenv(name, str(default)))
    except ValueError:
        return default
    return value if value > 0 else default


def _positive_int(name: str, default: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError:
        return default
    return value if value > 0 else default


def build_timeout(read_timeout: float | None = None) -> httpx.Timeout:
    configured_read = _positive_float("LEARNFLOW_LLM_READ_TIMEOUT", 60.0)
    return httpx.Timeout(
        connect=_positive_float("LEARNFLOW_LLM_CONNECT_TIMEOUT", 5.0),
        read=read_timeout if read_timeout is not None else configured_read,
        write=_positive_float("LEARNFLOW_LLM_WRITE_TIMEOUT", 15.0),
        pool=_positive_float("LEARNFLOW_LLM_POOL_TIMEOUT", 5.0),
    )


def create_http_client() -> httpx.AsyncClient:
    max_connections = _positive_int("LEARNFLOW_LLM_MAX_CONNECTIONS", 30)
    max_keepalive = min(
        max_connections,
        _positive_int("LEARNFLOW_LLM_MAX_KEEPALIVE_CONNECTIONS", 10),
    )
    return httpx.AsyncClient(
        timeout=build_timeout(),
        limits=httpx.Limits(
            max_connections=max_connections,
            max_keepalive_connections=max_keepalive,
            keepalive_expiry=30.0,
        ),
    )


async def open_http_client() -> httpx.AsyncClient:
    global _client
    async with _client_lock:
        if _client is None or _client.is_closed:
            _client = create_http_client()
        return _client


async def get_http_client() -> httpx.AsyncClient:
    if _client is not None and not _client.is_closed:
        return _client
    return await open_http_client()


async def close_http_client() -> None:
    global _client
    async with _client_lock:
        client = _client
        _client = None
    if client is not None and not client.is_closed:
        await client.aclose()
