from __future__ import annotations

import asyncio
import importlib
import math
import os
from collections.abc import Sequence
from dataclasses import dataclass
from typing import Any, Protocol

DEFAULT_CANDIDATE_LIMIT = 20
DEFAULT_TIMEOUT_SECONDS = 1.2
DEFAULT_MIN_SCORE = 0.35
DEFAULT_MODEL = "cross-encoder/ms-marco-MiniLM-L-6-v2"


class ScoreProvider(Protocol):
    def predict(self, pairs: Sequence[tuple[str, str]]) -> Any: ...


@dataclass(frozen=True)
class RerankerSettings:
    enabled: bool
    model: str
    candidate_limit: int
    timeout_seconds: float
    min_score: float


def reranker_settings() -> RerankerSettings:
    return RerankerSettings(
        enabled=_env_bool("LEARNFLOW_CROSS_ENCODER_ENABLED", False),
        model=os.getenv("LEARNFLOW_CROSS_ENCODER_MODEL", DEFAULT_MODEL).strip() or DEFAULT_MODEL,
        candidate_limit=_bounded_int(
            "LEARNFLOW_RERANK_CANDIDATE_LIMIT",
            DEFAULT_CANDIDATE_LIMIT,
            minimum=1,
            maximum=50,
        ),
        timeout_seconds=_bounded_float(
            "LEARNFLOW_RERANK_TIMEOUT_SECONDS",
            DEFAULT_TIMEOUT_SECONDS,
            minimum=0.05,
            maximum=10.0,
        ),
        min_score=_bounded_float(
            "LEARNFLOW_RERANK_MIN_SCORE",
            DEFAULT_MIN_SCORE,
            minimum=0.0,
            maximum=1.0,
        ),
    )


class CrossEncoderReranker:
    """Lazy optional Cross Encoder with a numeric-only output boundary."""

    def __init__(self, model_name: str, provider: ScoreProvider | None = None) -> None:
        self.model_name = model_name
        self._provider = provider

    def score_sync(self, query: str, documents: Sequence[str]) -> list[float]:
        if not documents:
            return []
        provider = self._provider or self._load_provider()
        raw_scores = provider.predict([(query, document) for document in documents])
        scores = [_normalize_score(value) for value in list(raw_scores)]
        if len(scores) != len(documents):
            raise ValueError("cross encoder returned an unexpected score count")
        if not all(math.isfinite(score) for score in scores):
            raise ValueError("cross encoder returned a non-finite score")
        return scores

    async def score(
        self,
        query: str,
        documents: Sequence[str],
        timeout_seconds: float,
    ) -> list[float]:
        async with asyncio.timeout(timeout_seconds):
            return await asyncio.to_thread(self.score_sync, query, documents)

    def _load_provider(self) -> ScoreProvider:
        try:
            module = importlib.import_module("sentence_transformers")
        except ImportError as exc:
            raise RuntimeError(
                "Cross Encoder support requires requirements-rerank.txt"
            ) from exc
        cross_encoder = getattr(module, "CrossEncoder")
        self._provider = cross_encoder(self.model_name, trust_remote_code=False)
        return self._provider


def _normalize_score(value: Any) -> float:
    score = float(value)
    if 0.0 <= score <= 1.0:
        return score
    if score >= 0:
        exponent = math.exp(-min(score, 60.0))
        return 1.0 / (1.0 + exponent)
    exponent = math.exp(max(score, -60.0))
    return exponent / (1.0 + exponent)


def _env_bool(name: str, default: bool) -> bool:
    fallback = "true" if default else "false"
    return os.getenv(name, fallback).strip().lower() in {"1", "true", "yes", "on"}


def _bounded_int(name: str, default: int, *, minimum: int, maximum: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError:
        value = default
    return max(minimum, min(maximum, value))


def _bounded_float(name: str, default: float, *, minimum: float, maximum: float) -> float:
    try:
        value = float(os.getenv(name, str(default)))
    except ValueError:
        value = default
    if not math.isfinite(value):
        value = default
    return max(minimum, min(maximum, value))
