import asyncio
from typing import Any

import app.agents.rag_agent as rag_module
from app.agents.rag_agent import RagAgent, SparseHit
from app.models.resource import ResourceItem, ResourceQueryContext


def _disable_metrics(monkeypatch: Any) -> None:
    monkeypatch.setattr(rag_module, "record_dense_retrieval", lambda *args: None)
    monkeypatch.setattr(rag_module, "record_sparse_retrieval", lambda *args: None)
    monkeypatch.setattr(rag_module, "record_rrf_fusion", lambda *args: None)
    monkeypatch.setattr(rag_module, "record_rag_result", lambda *args: None)


def _stub_legacy_pipeline(agent: RagAgent, legacy: list[ResourceItem], monkeypatch: Any) -> None:
    monkeypatch.setattr(agent, "_refresh_resources_if_needed", lambda: None)
    monkeypatch.setattr(agent, "_extract_core_terms", lambda req: ["spring"])
    monkeypatch.setattr(agent, "_expand_query", lambda req, terms: ["spring", "java"])
    monkeypatch.setattr(agent, "_recall", lambda req, expanded, core: [])
    monkeypatch.setattr(agent, "_rerank", lambda req, hits: legacy)
    monkeypatch.setattr(agent, "_log_call", lambda *args: None)


def test_hybrid_keeps_sparse_results_when_dense_fails(monkeypatch: Any) -> None:
    agent = RagAgent.__new__(RagAgent)
    agent._frozen_snapshot = False
    fallback = ResourceItem(id=1, title="Fallback", url="https://example.com/fallback")
    sparse_item = ResourceItem(id=2, title="Sparse", url="https://example.com/sparse")
    _stub_legacy_pipeline(agent, [fallback], monkeypatch)
    _disable_metrics(monkeypatch)
    monkeypatch.setattr(rag_module, "dense_retrieval_enabled", lambda: True)
    monkeypatch.setattr(rag_module, "sparse_retrieval_enabled", lambda: True)
    monkeypatch.setattr(
        agent,
        "_active_embedding_definition",
        lambda: (_ for _ in ()).throw(RuntimeError("dense unavailable")),
    )
    monkeypatch.setattr(agent, "_sparse_query_text", lambda req, expanded: "spring OR java")
    monkeypatch.setattr(
        agent,
        "_sparse_recall",
        lambda req, query, limit: [SparseHit(item=sparse_item, rank_score=0.7)],
    )

    response = asyncio.run(agent.recommend_v2_hybrid(ResourceQueryContext(topic="Spring")))

    assert [item.id for item in response.resources] == [2, 1]
    assert response.resources[0].retrieval_channels == ["sparse"]
    assert response.resources[1].retrieval_channels == ["fallback"]
    assert response.rerank_strategy == "postgres-fts-sparse-rrf+feedback-tiebreak"


def test_hybrid_uses_legacy_only_when_both_channels_fail(monkeypatch: Any) -> None:
    agent = RagAgent.__new__(RagAgent)
    agent._frozen_snapshot = False
    fallback = ResourceItem(id=7, title="Fallback", url="https://example.com/fallback")
    _stub_legacy_pipeline(agent, [fallback], monkeypatch)
    _disable_metrics(monkeypatch)
    monkeypatch.setattr(rag_module, "dense_retrieval_enabled", lambda: True)
    monkeypatch.setattr(rag_module, "sparse_retrieval_enabled", lambda: True)
    monkeypatch.setattr(
        agent,
        "_active_embedding_definition",
        lambda: (_ for _ in ()).throw(RuntimeError("dense unavailable")),
    )
    monkeypatch.setattr(agent, "_sparse_query_text", lambda req, expanded: "spring")
    monkeypatch.setattr(
        agent,
        "_sparse_recall",
        lambda req, query, limit: (_ for _ in ()).throw(RuntimeError("sparse unavailable")),
    )

    response = asyncio.run(agent.recommend_v2_hybrid(ResourceQueryContext(topic="Spring")))

    assert [item.id for item in response.resources] == [7]
    assert response.resources[0].retrieval_channels == ["fallback"]
    assert response.rerank_strategy == "metadata-index+keyword-vector-fusion+feedback-rerank"


def test_hybrid_marks_fallback_when_both_modern_channels_are_disabled(monkeypatch: Any) -> None:
    agent = RagAgent.__new__(RagAgent)
    agent._frozen_snapshot = False
    fallback = ResourceItem(id=8, title="Fallback", url="https://example.com/fallback")
    _stub_legacy_pipeline(agent, [fallback], monkeypatch)
    _disable_metrics(monkeypatch)
    monkeypatch.setattr(rag_module, "dense_retrieval_enabled", lambda: False)
    monkeypatch.setattr(rag_module, "sparse_retrieval_enabled", lambda: False)

    response = asyncio.run(agent.recommend_v2_hybrid(ResourceQueryContext(topic="Spring")))

    assert [item.id for item in response.resources] == [8]
    assert response.resources[0].retrieval_channels == ["fallback"]
    assert response.rerank_strategy == "metadata-index+keyword-vector-fusion+feedback-rerank"


def test_sparse_query_is_bounded_and_uses_safe_or_terms() -> None:
    agent = RagAgent.__new__(RagAgent)
    request = ResourceQueryContext(
        topic='Spring "Boot" API',
        goal_text="构建 Java 后端",
        task_texts=["REST 接口", "数据库查询"],
    )

    query = agent._sparse_query_text(request, ["spring", "rest api"] * 20)

    assert " OR " in query
    assert '"' not in query
    assert len(query) <= 2_000
    assert len(query.split(" OR ")) <= 24


def test_sparse_sql_filters_current_active_resources(monkeypatch: Any) -> None:
    captured: dict[str, Any] = {}

    class Result:
        def mappings(self) -> "Result":
            return self

        def all(self) -> list[dict[str, Any]]:
            return [
                {
                    "id": 11,
                    "title": "Spring",
                    "url": "https://example.com/spring",
                    "level": "beginner",
                    "domain": "java",
                    "duration_minutes": 30,
                    "tags": "spring,java",
                    "rank_score": 0.5,
                    "chunk_id": "123e4567-e89b-12d3-a456-426614174000",
                    "content": "Spring Boot REST API evidence",
                    "content_hash": "a" * 64,
                }
            ]

    class Session:
        def __enter__(self) -> "Session":
            return self

        def __exit__(self, *args: object) -> None:
            return None

        def execute(self, statement: object, params: dict[str, Any]) -> Result:
            captured["sql"] = str(statement).lower()
            captured["params"] = params
            return Result()

    monkeypatch.setattr(rag_module, "SessionLocal", Session)
    agent = RagAgent.__new__(RagAgent)

    hits = agent._sparse_recall(
        ResourceQueryContext(topic="Spring", domain="java", level="beginner"),
        "spring OR java",
        5,
    )

    assert [hit.item.id for hit in hits] == [11]
    assert "r.status = 'active'" in captured["sql"]
    assert "r.ingestion_status = 'succeeded'" in captured["sql"]
    assert "r.current_ingestion_id = ic.ingestion_id" in captured["sql"]
    assert captured["params"]["domain"] == "java"
    assert captured["params"]["candidate_limit"] == 5
    assert hits[0].evidence[0].excerpt == "Spring Boot REST API evidence"
    assert hits[0].evidence[0].source_url == "https://example.com/spring"
    assert hits[0].evidence[0].retrieval_channels == ["sparse"]
    assert "content_hash" in captured["sql"]
