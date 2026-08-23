import math

import pytest

from app.agents.rag_agent import DenseHit, RagAgent, SparseHit
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


def test_single_channel_rrf_keeps_legacy_fillers() -> None:
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
    assert fused[0].score == pytest.approx(100 / 61)
    assert fused[0].retrieval_channels == ["dense"]
    assert "RRF" in (fused[0].reason or "")
    assert any(item.id is None for item in fused)


def test_rrf_rewards_cross_channel_hits_and_has_stable_ties() -> None:
    agent = RagAgent.__new__(RagAgent)
    first = ResourceItem(id=10, title="First", url="https://example.com/first")
    cross_channel = ResourceItem(id=20, title="Cross", url="https://example.com/cross")
    tie_from_sparse = ResourceItem(id=5, title="Tie", url="https://example.com/tie")

    fused = agent._rrf_fuse_results(
        [],
        [
            DenseHit(item=first, similarity=0.9),
            DenseHit(item=cross_channel, similarity=0.8),
        ],
        [
            SparseHit(item=cross_channel, rank_score=0.7),
            SparseHit(item=tie_from_sparse, rank_score=0.6),
        ],
    )

    assert [item.id for item in fused] == [20, 10, 5]
    assert fused[0].retrieval_channels == ["dense", "sparse"]
    assert fused[0].score == pytest.approx((1 / 62 + 1 / 61) * 100)


def test_rrf_equal_scores_use_resource_id_as_stable_tiebreaker() -> None:
    agent = RagAgent.__new__(RagAgent)
    larger = ResourceItem(id=9, title="Larger", url="https://example.com/larger")
    smaller = ResourceItem(id=3, title="Smaller", url="https://example.com/smaller")

    fused = agent._rrf_fuse_results(
        [],
        [DenseHit(item=larger, similarity=0.9)],
        [SparseHit(item=smaller, rank_score=0.9)],
    )

    assert [item.id for item in fused] == [3, 9]
