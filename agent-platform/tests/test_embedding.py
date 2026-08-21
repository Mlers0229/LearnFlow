import math

import pytest

from app.agents.rag_agent import DenseHit, RagAgent
from app.core.embedding import EmbeddingProtocolError, _parse_vectors
from app.models.resource import ResourceItem


def test_embedding_response_is_ordered_and_dimension_checked() -> None:
    body = {
        "data": [
            {"index": 1, "embedding": [0.0, 1.0]},
            {"index": 0, "embedding": [1.0, 0.0]},
        ]
    }

    assert _parse_vectors(body, expected=2, dimensions=2) == [[1.0, 0.0], [0.0, 1.0]]


@pytest.mark.parametrize(
    "body",
    [
        {"data": [{"index": 0, "embedding": [1.0]}]},
        {"data": [{"index": 0, "embedding": [math.inf, 0.0]}]},
        {"data": [{"index": 2, "embedding": [1.0, 0.0]}]},
    ],
)
def test_embedding_response_rejects_invalid_vectors(body: object) -> None:
    with pytest.raises(EmbeddingProtocolError):
        _parse_vectors(body, expected=1, dimensions=2)


def test_dense_fusion_rewards_cross_channel_hits_and_keeps_fallbacks() -> None:
    agent = RagAgent.__new__(RagAgent)
    legacy = ResourceItem(
        id=7,
        title="Spring Boot",
        url="https://example.com/spring",
        score=3.0,
        reason="关键词召回",
    )
    unkeyed_fallback = ResourceItem(
        title="Fallback",
        url="https://example.com/fallback",
        score=1.0,
    )
    dense = DenseHit(item=legacy.model_copy(deep=True), similarity=0.8)

    fused = agent._fuse_dense_results([legacy, unkeyed_fallback], [dense])

    assert fused[0].id == 7
    assert fused[0].score == pytest.approx(5.7)
    assert "pgvector" in (fused[0].reason or "")
    assert any(item.id is None for item in fused)
