from __future__ import annotations

import asyncio
import math
import os
from dataclasses import dataclass
from typing import Sequence

import httpx

from app.core.http_client import build_timeout, get_http_client
from app.core.request_budget import downstream_seconds
from app.core.resilience import get_embedding_resilience
from app.observability import mark_model_outcome, model_call_span, record_model_usage
from app.outbound_security import validate_llm_url


class EmbeddingNotConfigured(RuntimeError):
    pass


class EmbeddingProtocolError(RuntimeError):
    pass


@dataclass(frozen=True)
class EmbeddingConfig:
    enabled: bool
    api_base: str
    api_key: str
    provider: str
    model: str
    version: str
    dimensions: int


def embedding_config() -> EmbeddingConfig:
    return EmbeddingConfig(
        enabled=os.getenv("LEARNFLOW_EMBEDDING_ENABLED", "false").lower()
        in {"1", "true", "yes"},
        api_base=os.getenv("LEARNFLOW_EMBEDDING_API_BASE", "").strip(),
        api_key=os.getenv("LEARNFLOW_EMBEDDING_API_KEY", "").strip(),
        provider=os.getenv("LEARNFLOW_EMBEDDING_PROVIDER", "openai-compatible").strip(),
        model=os.getenv("LEARNFLOW_EMBEDDING_MODEL", "text-embedding-3-small").strip(),
        version=os.getenv("LEARNFLOW_EMBEDDING_VERSION", "text-embedding-3-small-v1").strip(),
        dimensions=_positive_int("LEARNFLOW_EMBEDDING_DIMENSIONS", 1536),
    )


def dense_retrieval_enabled() -> bool:
    return embedding_config().enabled


async def embed_texts(
    texts: Sequence[str],
    *,
    model: str | None = None,
    dimensions: int | None = None,
) -> list[list[float]]:
    config = embedding_config()
    if not config.enabled:
        raise EmbeddingNotConfigured("embedding is disabled")
    if not config.api_base or not config.api_key:
        raise EmbeddingNotConfigured("embedding endpoint credentials are not configured")
    use_model = model or config.model
    use_dimensions = dimensions or config.dimensions
    if use_dimensions != 1536:
        raise EmbeddingNotConfigured("this schema version requires 1536-dimensional embeddings")
    normalized = [str(text).strip() for text in texts]
    if not normalized or len(normalized) > 64:
        raise EmbeddingProtocolError("embedding batch size must be between 1 and 64")
    if any(not text or len(text) > 16_000 for text in normalized):
        raise EmbeddingProtocolError("embedding input is empty or exceeds the bounded input size")

    url = config.api_base.rstrip("/") + "/v1/embeddings"
    validate_llm_url(url)
    payload = {"model": use_model, "input": normalized, "dimensions": use_dimensions}
    headers = {"Authorization": f"Bearer {config.api_key}", "Content-Type": "application/json"}
    overall_timeout = downstream_seconds(_positive_float("LEARNFLOW_EMBEDDING_READ_TIMEOUT", 30.0))
    client = await get_http_client()
    resilience = await get_embedding_resilience()

    async def invoke() -> tuple[list[list[float]], object]:
        async with asyncio.timeout(overall_timeout):
            response = await client.post(
                url,
                headers=headers,
                json=payload,
                timeout=build_timeout(read_timeout=overall_timeout),
            )
            response.raise_for_status()
            body = response.json()
            return _parse_vectors(body, len(normalized), use_dimensions), body.get("usage")

    with model_call_span(use_model, "embeddings") as span:
        try:
            vectors, usage = await resilience.execute(invoke, records_failure=_records_provider_failure)
            record_model_usage(span, use_model, usage)
            mark_model_outcome(span, "success")
            return vectors
        except BaseException as failure:
            mark_model_outcome(span, "failure", _failure_reason(failure))
            raise


def _parse_vectors(body: object, expected: int, dimensions: int) -> list[list[float]]:
    if not isinstance(body, dict) or not isinstance(body.get("data"), list):
        raise EmbeddingProtocolError("embedding response has no data array")
    ordered: list[list[float] | None] = [None] * expected
    for position, item in enumerate(body["data"]):
        if not isinstance(item, dict) or not isinstance(item.get("embedding"), list):
            raise EmbeddingProtocolError("embedding response item is invalid")
        index = item.get("index", position)
        if not isinstance(index, int) or index < 0 or index >= expected or ordered[index] is not None:
            raise EmbeddingProtocolError("embedding response index is invalid")
        vector = item["embedding"]
        if len(vector) != dimensions:
            raise EmbeddingProtocolError("embedding vector dimension does not match configuration")
        try:
            numeric = [float(value) for value in vector]
        except (TypeError, ValueError) as exc:
            raise EmbeddingProtocolError("embedding vector contains a non-numeric value") from exc
        if any(not math.isfinite(value) for value in numeric):
            raise EmbeddingProtocolError("embedding vector contains a non-finite value")
        ordered[index] = numeric
    if any(vector is None for vector in ordered):
        raise EmbeddingProtocolError("embedding response is incomplete")
    return [vector for vector in ordered if vector is not None]


def _records_provider_failure(failure: BaseException) -> bool:
    if isinstance(failure, httpx.HTTPStatusError):
        return failure.response.status_code == 429 or failure.response.status_code >= 500
    return isinstance(failure, (TimeoutError, httpx.TimeoutException, httpx.RequestError))


def _failure_reason(failure: BaseException) -> str:
    if isinstance(failure, httpx.HTTPStatusError):
        return f"http_{failure.response.status_code}"
    if isinstance(failure, (TimeoutError, httpx.TimeoutException)):
        return "embedding_timeout"
    if isinstance(failure, EmbeddingProtocolError):
        return "invalid_response"
    return type(failure).__name__.lower()[:48]


def _positive_int(name: str, default: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError:
        return default
    return value if value > 0 else default


def _positive_float(name: str, default: float) -> float:
    try:
        value = float(os.getenv(name, str(default)))
    except ValueError:
        return default
    return value if value > 0 else default
