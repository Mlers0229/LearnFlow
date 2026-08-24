from __future__ import annotations

import hashlib
import json
import logging
import math
import os
import re
import time
from collections import Counter
from dataclasses import dataclass, field
from typing import Any, List, Mapping, Sequence

from sqlalchemy import text as sql_text
from sqlalchemy.orm import Session

from app.core.embedding import dense_retrieval_enabled, embed_texts
from app.core.reranker import CrossEncoderReranker, reranker_settings
from app.db import ResourceBank, SessionLocal, UserResourceFeedback, save_agent_call
from app.models.resource import (
    ResourceEvidence,
    ResourceIndexStatus,
    ResourceItem,
    ResourceQueryContext,
    ResourceRecommendRequest,
    ResourceRecommendResponse,
    ResourceRecommendResponseV2,
)
from app.observability import (
    record_dense_retrieval,
    record_rag_result,
    record_rerank,
    record_rrf_fusion,
    record_sparse_retrieval,
)

logger = logging.getLogger(__name__)


KEYWORD_HINTS = {
    "java": ["jvm", "面向对象", "集合", "spring"],
    "spring": ["springboot", "ioc", "rest api"],
    "数据库": ["sql", "postgres", "mysql"],
    "后端": ["api", "数据库", "springboot"],
    "项目": ["实战", "project", "demo"],
    "sql": ["mysql", "postgres", "数据库", "查询"],
    "mybatis": ["mapper", "xml", "sql"],
    "rest": ["restful", "api", "http"],
    "接口": ["api", "rest", "controller"],
}

STYLE_TAGS = {
    "视频": {"bilibili", "video"},
    "文档": {"docs", "guide", "official"},
    "文章": {"blog", "csdn", "juejin", "zhihu"},
}

TASK_TYPE_HINTS = {
    "practice": {"project", "demo", "实战", "练习", "example"},
    "project": {"project", "demo", "实战", "案例"},
    "review": {"guide", "docs", "总结", "原理"},
    "debug": {"debug", "排错", "异常", "troubleshooting"},
    "plan_overview": {"guide", "project", "docs", "roadmap"},
    "learn": {"intro", "basic", "guide", "入门"},
}

LEVEL_HINTS = {
    "beginner": {"intro", "basic", "入门", "基础"},
    "intermediate": {"进阶", "实践", "实战", "intermediate"},
    "advanced": {"源码", "原理", "advanced", "架构"},
}

GENERIC_TERMS = {
    "basic",
    "intro",
    "guide",
    "demo",
    "example",
    "project",
    "practice",
    "roadmap",
    "入门",
    "基础",
    "练习",
    "实战",
    "项目",
    "案例",
    "总结",
}

DOMAIN_TERMS = {
    "java",
    "jvm",
    "spring",
    "springboot",
    "mysql",
    "postgres",
    "sql",
    "数据库",
    "面向对象",
    "集合",
    "api",
    "rest",
    "restful",
    "controller",
    "mybatis",
    "mapper",
    "项目",
    "实战",
    "异常",
    "调试",
    "部署",
}

DOMAIN_HINTS = {
    "java": {"java", "spring", "springboot", "jvm", "集合", "面向对象"},
    "python": {"python", "numpy", "pandas", "爬虫"},
    "database": {"sql", "mysql", "postgres", "数据库", "mybatis", "mapper"},
    "english": {"英语", "english", "cet", "cet4", "cet6", "四级", "六级", "词汇", "阅读理解", "写作"},
    "math": {"高数", "数学", "math", "线代", "概率"},
    "frontend": {"vue", "react", "javascript", "css", "html", "前端"},
    "devops": {"linux", "shell", "docker", "运维"},
}

RESOURCE_CACHE_TTL_SECONDS = 60
VECTOR_DIMENSIONS = 64
RETRIEVER_VERSION = "postgres-hybrid-rrf-v1"
INDEX_VERSION = f"deterministic-hash-vector-v1-d{VECTOR_DIMENSIONS}"
RRF_K = 60


def sparse_retrieval_enabled() -> bool:
    return os.getenv("LEARNFLOW_SPARSE_RETRIEVAL_ENABLED", "true").strip().lower() in {
        "1",
        "true",
        "yes",
        "on",
    }


@dataclass(frozen=True)
class FeedbackStats:
    avg_rating: float | None = None
    feedback_count: int = 0
    invalid_count: int = 0


@dataclass(frozen=True)
class RecallHit:
    item: ResourceItem
    score: float
    matched_terms: list[str]
    channels: set[str]


@dataclass(frozen=True)
class DenseHit:
    item: ResourceItem
    similarity: float
    evidence: list[ResourceEvidence] = field(default_factory=list)


@dataclass(frozen=True)
class SparseHit:
    item: ResourceItem
    rank_score: float
    evidence: list[ResourceEvidence] = field(default_factory=list)


@dataclass
class RrfCandidate:
    item: ResourceItem
    rrf_score: float = 0.0
    channels: list[str] = field(default_factory=list)
    evidence: list[ResourceEvidence] = field(default_factory=list)


class RagAgent:
    """RAG v2：Dense/Sparse 召回、确定性 RRF 与规则/哈希降级。"""

    def __init__(
        self,
        resources: Sequence[ResourceItem] | None = None,
        feedback_stats: Mapping[int, FeedbackStats] | None = None,
        *,
        index_source: str = "snapshot",
        enable_call_logging: bool = True,
        reranker: CrossEncoderReranker | None = None,
    ) -> None:
        self._resources: List[ResourceItem] = list(resources) if resources is not None else SAMPLE_RESOURCES
        self._resource_loaded_at = 0.0
        self._keyword_index: dict[str, set[int]] = {}
        self._resource_vectors: dict[int, list[float]] = {}
        self._resource_terms: dict[int, list[str]] = {}
        self._feedback_stats: dict[int, FeedbackStats] = dict(feedback_stats or {})
        self._index_built_at: float | None = None
        self._index_source = index_source if resources is not None else "sample"
        self._last_index_error: str | None = None
        self._frozen_snapshot = resources is not None
        self._enable_call_logging = enable_call_logging
        self._reranker = reranker
        if self._frozen_snapshot:
            self._resource_loaded_at = time.time()
            self._build_in_memory_index()
        else:
            self.rebuild_index()

    def _load_resources_from_db(self) -> None:
        source = "sample"
        try:
            db: Session
            with SessionLocal() as db:
                rows: List[ResourceBank] = db.query(ResourceBank).filter(ResourceBank.status == "ACTIVE").all()
                if not rows:
                    logger.info("resource_bank 为空，继续使用内置资源。")
                    self._resources = SAMPLE_RESOURCES
                    self._feedback_stats = {}
                    self._index_source = source
                    self._last_index_error = None
                    return

                resources: List[ResourceItem] = []
                for row in rows:
                    if row.id is None or row.title is None:
                        continue
                    tags = [tag.strip() for tag in (row.tags or "").split(",") if tag.strip()]
                    resources.append(
                        ResourceItem(
                            id=row.id,
                            title=row.title,
                            url=row.url or "",
                            level=row.level,
                            domain=row.domain,
                            duration_minutes=row.duration_minutes,
                            tags=tags,
                            source="db",
                        )
                    )
                self._resources = resources
                self._feedback_stats = self._load_feedback_stats(db)
                source = "db"
                self._index_source = source
                self._last_index_error = None
                logger.info("已从数据库加载 %d 条资源用于 RAG 推荐。", len(resources))
        except Exception as exc:  # noqa: BLE001
            self._resources = SAMPLE_RESOURCES
            self._feedback_stats = {}
            self._index_source = "sample"
            self._last_index_error = str(exc)
            logger.exception("加载资源失败，将继续使用内置资源。", exc_info=exc)
        finally:
            self._resource_loaded_at = time.time()

    def _refresh_resources_if_needed(self) -> None:
        if getattr(self, "_frozen_snapshot", False):
            return
        if time.time() - self._resource_loaded_at >= RESOURCE_CACHE_TTL_SECONDS:
            self.rebuild_index()

    def rebuild_index(self) -> ResourceIndexStatus:
        if not getattr(self, "_frozen_snapshot", False):
            self._load_resources_from_db()
        self._build_in_memory_index()
        return self.index_status()

    def _build_in_memory_index(self) -> None:
        self._keyword_index = {}
        self._resource_vectors = {}
        self._resource_terms = {}

        for position, item in enumerate(self._resources):
            terms = self._resource_index_terms(item)
            self._resource_terms[position] = terms
            for term in terms:
                self._keyword_index.setdefault(term, set()).add(position)
            self._resource_vectors[position] = self._embed_terms(terms)

        self._index_built_at = time.time()

    def index_status(self) -> ResourceIndexStatus:
        dense_ready, dense_vector_count, embedding_version = self._dense_index_status()
        sparse_ready, sparse_chunk_count = self._sparse_index_status()
        return ResourceIndexStatus(
            ready=bool(self._resources and self._resource_vectors),
            resource_count=len(self._resources),
            keyword_count=len(self._keyword_index),
            vector_count=len(self._resource_vectors),
            feedback_count=sum(stats.feedback_count for stats in self._feedback_stats.values()),
            source=self._index_source,
            fallback_enabled=not getattr(self, "_frozen_snapshot", False),
            built_at=self._index_built_at,
            last_error=self._last_index_error,
            dense_ready=dense_ready,
            dense_vector_count=dense_vector_count,
            embedding_version=embedding_version,
            sparse_ready=sparse_ready,
            sparse_chunk_count=sparse_chunk_count,
        )

    def _dense_index_status(self) -> tuple[bool, int, str | None]:
        if not dense_retrieval_enabled() or getattr(self, "_frozen_snapshot", False):
            return False, 0, None
        try:
            with SessionLocal() as db:
                row = db.execute(
                    sql_text(
                        """
                        select v.version, count(e.chunk_id) as vector_count
                        from embedding_model_version v
                        left join resource_chunk_embedding e on e.embedding_version = v.version
                        where v.status = 'ACTIVE'
                        group by v.version
                        """
                    )
                ).mappings().first()
                if row is None:
                    return False, 0, None
                count = int(row["vector_count"] or 0)
                return count > 0, count, str(row["version"])
        except Exception:  # noqa: BLE001
            logger.warning("Dense index status is unavailable", exc_info=True)
            return False, 0, None

    def _sparse_index_status(self) -> tuple[bool, int]:
        if not sparse_retrieval_enabled() or getattr(self, "_frozen_snapshot", False):
            return False, 0
        try:
            with SessionLocal() as db:
                count = db.execute(
                    sql_text(
                        """
                        select count(c.search_vector)
                        from resource_chunk c
                        join resource_ingestion_chunk ic on ic.chunk_id = c.id
                        join resource_bank r
                          on r.id = c.resource_id and r.current_ingestion_id = ic.ingestion_id
                        where r.status = 'ACTIVE'
                          and r.ingestion_status = 'SUCCEEDED'
                        """
                    )
                ).scalar_one()
            chunk_count = int(count or 0)
            return chunk_count > 0, chunk_count
        except Exception:  # noqa: BLE001
            logger.warning("Sparse index status is unavailable", exc_info=True)
            return False, 0

    def _load_feedback_stats(self, db: Session) -> dict[int, FeedbackStats]:
        stats: dict[int, FeedbackStats] = {}
        rows: list[UserResourceFeedback] = db.query(UserResourceFeedback).all()
        grouped: dict[int, list[UserResourceFeedback]] = {}
        for row in rows:
            if row.resource_bank_id is None:
                continue
            grouped.setdefault(int(row.resource_bank_id), []).append(row)

        for resource_id, feedbacks in grouped.items():
            ratings = [feedback.rating for feedback in feedbacks if feedback.rating is not None]
            avg_rating = sum(ratings) / len(ratings) if ratings else None
            invalid_count = sum(1 for feedback in feedbacks if feedback.is_reported_invalid)
            stats[resource_id] = FeedbackStats(
                avg_rating=avg_rating,
                feedback_count=len(feedbacks),
                invalid_count=invalid_count,
            )
        return stats

    def recommend(self, req: ResourceRecommendRequest, trace_id: str | None = None) -> ResourceRecommendResponse:
        ctx = ResourceQueryContext(topic=req.topic, level=req.level)
        result = self.recommend_v2(ctx, trace_id=trace_id)
        return ResourceRecommendResponse(resources=result.resources)

    @staticmethod
    def _effective_adaptive_request(req: ResourceQueryContext) -> ResourceQueryContext:
        context = req.adaptive_context
        if context is None or not context.applied or context.target_difficulty is None:
            return req
        return req.model_copy(update={"level": context.target_difficulty})
    def recommend_v2(
        self,
        req: ResourceQueryContext,
        trace_id: str | None = None,
    ) -> ResourceRecommendResponseV2:
        req = self._effective_adaptive_request(req)
        start = time.perf_counter()
        self._refresh_resources_if_needed()
        core_terms = self._extract_core_terms(req)
        expanded_queries = self._expand_query(req, core_terms)
        candidates = self._recall(req, expanded_queries, core_terms)
        reranked = self._rerank(req, candidates)
        resources = reranked[: req.top_k]
        response = ResourceRecommendResponseV2(
            resources=resources,
            expanded_queries=expanded_queries,
            rerank_strategy="metadata-index+keyword-vector-fusion+feedback-rerank",
            query_summary=self._build_query_summary(req, expanded_queries),
        )
        record_rag_result(len(resources))
        self._log_call(req, response, trace_id, start)
        return response

    async def recommend_v2_with_dense(
        self,
        req: ResourceQueryContext,
        trace_id: str | None = None,
    ) -> ResourceRecommendResponseV2:
        """Backward-compatible entry point for callers introduced with Dense Retrieval."""
        return await self.recommend_v2_hybrid(req, trace_id=trace_id)

    async def recommend_v2_hybrid(
        self,
        req: ResourceQueryContext,
        trace_id: str | None = None,
    ) -> ResourceRecommendResponseV2:
        req = self._effective_adaptive_request(req)
        dense_enabled = dense_retrieval_enabled()
        sparse_enabled = sparse_retrieval_enabled()
        if getattr(self, "_frozen_snapshot", False):
            return self.recommend_v2(req, trace_id=trace_id)

        start = time.perf_counter()
        self._refresh_resources_if_needed()
        core_terms = self._extract_core_terms(req)
        expanded_queries = self._expand_query(req, core_terms)
        legacy_hits = self._recall(req, expanded_queries, core_terms)
        legacy_resources = self._rerank(req, legacy_hits)
        dense_hits: list[DenseHit] = []
        if dense_enabled:
            dense_outcome = "success"
            dense_reason = "none"
            dense_started = time.perf_counter()
            try:
                active_version, active_model, active_dimensions = self._active_embedding_definition()
                query_text = self._dense_query_text(req, expanded_queries)
                vectors = await embed_texts(
                    [query_text],
                    model=active_model,
                    dimensions=active_dimensions,
                )
                dense_hits = self._dense_recall(
                    req,
                    vectors[0],
                    max(10, req.top_k * 4),
                    active_version,
                )
                if not dense_hits:
                    dense_outcome = "empty"
            except Exception as failure:  # noqa: BLE001
                dense_outcome = "fallback"
                dense_reason = type(failure).__name__.lower()[:48]
                logger.warning(
                    "Dense retrieval degraded independently errorType=%s",
                    type(failure).__name__,
                )
            record_dense_retrieval(
                dense_outcome,
                dense_reason,
                len(dense_hits),
                time.perf_counter() - dense_started,
            )

        sparse_hits: list[SparseHit] = []
        if sparse_enabled:
            sparse_outcome = "success"
            sparse_reason = "none"
            sparse_started = time.perf_counter()
            try:
                sparse_hits = self._sparse_recall(
                    req,
                    self._sparse_query_text(req, expanded_queries),
                    max(10, req.top_k * 4),
                )
                if not sparse_hits:
                    sparse_outcome = "empty"
            except Exception as failure:  # noqa: BLE001
                sparse_outcome = "fallback"
                sparse_reason = type(failure).__name__.lower()[:48]
                logger.warning(
                    "Sparse retrieval degraded independently errorType=%s",
                    type(failure).__name__,
                )
            record_sparse_retrieval(
                sparse_outcome,
                sparse_reason,
                len(sparse_hits),
                time.perf_counter() - sparse_started,
            )

        fused_resources = self._rrf_fuse_results(legacy_resources, dense_hits, sparse_hits)
        resources, rerank_outcome, degradation_reason = await self._apply_second_stage_rerank(
            req,
            fused_resources,
        )
        resources = resources[: req.top_k]
        active_channels = int(bool(dense_hits)) + int(bool(sparse_hits))
        fusion_outcome = "fallback" if active_channels == 0 else "single" if active_channels == 1 else "hybrid"
        record_rrf_fusion(len(dense_hits), len(sparse_hits), len(fused_resources), fusion_outcome)
        if dense_hits and sparse_hits:
            strategy = "postgres-dense+sparse-rrf+feedback-tiebreak"
        elif dense_hits:
            strategy = "pgvector-dense-rrf+feedback-tiebreak"
        elif sparse_hits:
            strategy = "postgres-fts-sparse-rrf+feedback-tiebreak"
        else:
            strategy = "metadata-index+keyword-vector-fusion+feedback-rerank"
        if rerank_outcome == "success":
            strategy += "+cross-encoder"

        cited_count = sum(1 for item in resources if item.evidence)
        citation_coverage = cited_count / len(resources) if resources else 0.0
        if degradation_reason is None and resources and cited_count == 0:
            degradation_reason = "no_verifiable_evidence"
        elif degradation_reason is None and cited_count < len(resources):
            degradation_reason = "partial_evidence"
        degraded = degradation_reason is not None
        response = ResourceRecommendResponseV2(
            resources=resources,
            expanded_queries=expanded_queries,
            rerank_strategy=strategy,
            query_summary=self._build_query_summary(req, expanded_queries),
            degraded=degraded,
            degradation_reason=degradation_reason,
            citation_coverage=round(citation_coverage, 6),
        )
        record_rag_result(len(resources))
        self._log_call(req, response, trace_id, start)
        return response

    @staticmethod
    def _dense_query_text(req: ResourceQueryContext, expanded_queries: list[str]) -> str:
        parts = [
            req.topic,
            req.goal_text,
            req.phase_title,
            req.week_theme,
            " ".join(req.task_texts or []),
            " ".join(expanded_queries[:12]),
        ]
        return "\n".join(part.strip() for part in parts if part and part.strip())[:16_000]

    def _sparse_query_text(
        self,
        req: ResourceQueryContext,
        expanded_queries: list[str],
    ) -> str:
        candidates = [
            *expanded_queries,
            *self._extract_terms(req.topic),
            *self._extract_terms(req.goal_text or ""),
            *self._extract_terms(" ".join(req.task_texts or [])),
        ]
        terms: list[str] = []
        seen: set[str] = set()
        for candidate in candidates:
            normalized = self._normalize_text(candidate).replace('"', " ").strip()
            if len(normalized) < 2 or normalized in seen:
                continue
            terms.append(normalized)
            seen.add(normalized)
        return " OR ".join(terms[:24])[:2_000]

    def _sparse_recall(
        self,
        req: ResourceQueryContext,
        search_query: str,
        limit: int,
    ) -> list[SparseHit]:
        if not search_query:
            return []
        statement = sql_text(
            """
            with search_terms as (
                select websearch_to_tsquery('simple', :search_query) as query
            ), sparse_chunks as (
                select r.id, r.title, coalesce(r.url, '') as url, r.level, r.domain,
                       r.duration_minutes, r.tags, c.id as chunk_id, c.content, c.content_hash,
                       ts_rank_cd(c.search_vector, q.query, 32) as rank_score
                from search_terms q
                join resource_chunk c on c.search_vector @@ q.query
                join resource_ingestion_chunk ic on ic.chunk_id = c.id
                join resource_bank r
                  on r.id = c.resource_id and r.current_ingestion_id = ic.ingestion_id
                where r.status = 'ACTIVE'
                  and r.ingestion_status = 'SUCCEEDED'
                  and (:domain is null or lower(r.domain) = lower(:domain))
                  and (:level is null or lower(r.level) = lower(:level))
                order by rank_score desc, c.id
                limit :chunk_limit
            ), ranked_chunks as (
                select id, title, url, level, domain, duration_minutes, tags,
                       chunk_id, content, content_hash, rank_score,
                       row_number() over (
                           partition by id
                           order by rank_score desc, chunk_id
                       ) as resource_rank
                from sparse_chunks
            )
            select id, title, url, level, domain, duration_minutes, tags,
                   chunk_id, content, content_hash, rank_score
            from ranked_chunks
            where resource_rank = 1
            order by rank_score desc, id
            limit :candidate_limit
            """
        )
        with SessionLocal() as db:
            rows = db.execute(
                statement,
                {
                    "search_query": search_query,
                    "domain": req.domain,
                    "level": req.level,
                    "candidate_limit": max(1, min(100, limit)),
                    "chunk_limit": max(4, min(400, limit * 4)),
                },
            ).mappings().all()

        hits: list[SparseHit] = []
        for row in rows:
            rank_score = max(0.0, float(row["rank_score"] or 0.0))
            if rank_score <= 0:
                continue
            tags = [tag.strip() for tag in (row["tags"] or "").split(",") if tag.strip()]
            hits.append(
                SparseHit(
                    item=ResourceItem(
                        id=int(row["id"]),
                        title=str(row["title"]),
                        url=str(row["url"]),
                        level=row["level"],
                        domain=row["domain"],
                        duration_minutes=row["duration_minutes"],
                        tags=tags,
                        source="db",
                    ),
                    rank_score=rank_score,
                    evidence=[self._evidence_from_row(row, "sparse")],
                )
            )
        return hits

    def _dense_recall(
        self,
        req: ResourceQueryContext,
        query_vector: list[float],
        limit: int,
        active_version: str,
    ) -> list[DenseHit]:
        if len(query_vector) != 1536:
            raise ValueError("query embedding dimension does not match the active schema")
        vector_literal = "[" + ",".join(str(value) for value in query_vector) + "]"
        statement = sql_text(
            """
            with dense_chunks as (
                select r.id, r.title, coalesce(r.url, '') as url, r.level, r.domain,
                       r.duration_minutes, r.tags, c.id as chunk_id, c.content, c.content_hash,
                       e.embedding <=> cast(:query_vector as vector) as distance
                from resource_chunk_embedding e
                join embedding_model_version v
                  on v.version = e.embedding_version
                join resource_chunk c on c.id = e.chunk_id
                join resource_ingestion_chunk ic on ic.chunk_id = c.id
                join resource_bank r
                  on r.id = c.resource_id and r.current_ingestion_id = ic.ingestion_id
                where r.status = 'ACTIVE'
                  and r.ingestion_status = 'SUCCEEDED'
                  and e.embedding_version = :active_version
                  and (:domain is null or lower(r.domain) = lower(:domain))
                  and (:level is null or lower(r.level) = lower(:level))
                order by e.embedding <=> cast(:query_vector as vector)
                limit :chunk_limit
            ), ranked_chunks as (
                select id, title, url, level, domain, duration_minutes, tags,
                       chunk_id, content, content_hash, distance,
                       row_number() over (
                           partition by r.id
                           order by distance, chunk_id
                       ) as resource_rank
                from dense_chunks r
            )
            select id, title, url, level, domain, duration_minutes, tags,
                   chunk_id, content, content_hash, distance
            from ranked_chunks
            where resource_rank = 1
            order by distance, id
            limit :candidate_limit
            """
        )
        with SessionLocal() as db:
            rows = db.execute(
                statement,
                {
                    "query_vector": vector_literal,
                    "active_version": active_version,
                    "domain": req.domain,
                    "level": req.level,
                    "candidate_limit": max(1, min(100, limit)),
                    "chunk_limit": max(4, min(400, limit * 4)),
                },
            ).mappings().all()

        hits: list[DenseHit] = []
        for row in rows:
            distance = float(row["distance"])
            similarity = max(-1.0, min(1.0, 1.0 - distance))
            if similarity <= 0:
                continue
            tags = [tag.strip() for tag in (row["tags"] or "").split(",") if tag.strip()]
            hits.append(
                DenseHit(
                    item=ResourceItem(
                        id=int(row["id"]),
                        title=str(row["title"]),
                        url=str(row["url"]),
                        level=row["level"],
                        domain=row["domain"],
                        duration_minutes=row["duration_minutes"],
                        tags=tags,
                        source="db",
                    ),
                    similarity=similarity,
                    evidence=[self._evidence_from_row(row, "dense")],
                )
            )
        return hits

    def _active_embedding_definition(self) -> tuple[str, str, int]:
        with SessionLocal() as db:
            row = db.execute(
                sql_text(
                    """
                    select version, model_name, dimensions
                    from embedding_model_version
                    where status = 'ACTIVE'
                    """
                )
            ).mappings().first()
        if row is None:
            raise RuntimeError("no active embedding version is available")
        return str(row["version"]), str(row["model_name"]), int(row["dimensions"])

    def _fuse_dense_results(
        self,
        legacy_resources: list[ResourceItem],
        dense_hits: list[DenseHit],
    ) -> list[ResourceItem]:
        return self._rrf_fuse_results(legacy_resources, dense_hits, [])

    def _rrf_fuse_results(
        self,
        legacy_resources: list[ResourceItem],
        dense_hits: list[DenseHit],
        sparse_hits: list[SparseHit],
    ) -> list[ResourceItem]:
        legacy_by_id = {item.id: item for item in legacy_resources if item.id is not None}
        candidates: dict[int, RrfCandidate] = {}

        def add_channel(hits: Sequence[DenseHit | SparseHit], channel: str) -> None:
            for rank, hit in enumerate(hits, start=1):
                resource_id = hit.item.id
                if resource_id is None:
                    continue
                candidate = candidates.get(resource_id)
                if candidate is None:
                    base = legacy_by_id.get(resource_id, hit.item).model_copy(deep=True)
                    candidate = RrfCandidate(item=base)
                    candidates[resource_id] = candidate
                candidate.rrf_score += 1.0 / (RRF_K + rank)
                if channel not in candidate.channels:
                    candidate.channels.append(channel)
                for evidence in hit.evidence:
                    existing = next(
                        (
                            value
                            for value in candidate.evidence
                            if value.chunk_id == evidence.chunk_id
                        ),
                        None,
                    )
                    if existing is None:
                        candidate.evidence.append(evidence.model_copy(deep=True))
                    elif channel not in existing.retrieval_channels:
                        existing.retrieval_channels.append(channel)

        add_channel(dense_hits, "dense")
        add_channel(sparse_hits, "sparse")

        ranked = sorted(
            candidates.values(),
            key=lambda candidate: (
                -candidate.rrf_score,
                -len(candidate.channels),
                -self._feedback_score(candidate.item.id),
                candidate.item.id or 0,
            ),
        )
        resources: list[ResourceItem] = []
        for candidate in ranked:
            item = candidate.item
            item.score = round(candidate.rrf_score * 100.0, 6)
            item.retrieval_channels = list(candidate.channels)
            item.evidence = candidate.evidence[:3]
            item.evidence_status = "verified" if item.evidence else "unverified"
            channel_label = "+".join(candidate.channels)
            item.reason = f"RRF 融合召回（{channel_label}）"
            resources.append(item)

        modern_ids = set(candidates)
        for legacy in legacy_resources:
            if legacy.id is not None and legacy.id in modern_ids:
                continue
            fallback = legacy.model_copy(deep=True)
            fallback.retrieval_channels = ["fallback"]
            fallback.evidence_status = "unverified"
            resources.append(fallback)
        return resources

    @staticmethod
    def _bounded_excerpt(content: object) -> str:
        text = re.sub(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]", " ", str(content or ""))
        text = re.sub(r"\s+", " ", text).strip()
        return text[:480]

    def _evidence_from_row(self, row: Any, channel: str) -> ResourceEvidence:
        excerpt = self._bounded_excerpt(row["content"])
        if not excerpt:
            raise ValueError("retrieved chunk content is blank")
        return ResourceEvidence(
            chunk_id=row["chunk_id"],
            excerpt=excerpt,
            source_url=str(row["url"]),
            content_hash=str(row["content_hash"]),
            retrieval_channels=[channel],
        )

    async def _apply_second_stage_rerank(
        self,
        req: ResourceQueryContext,
        resources: list[ResourceItem],
    ) -> tuple[list[ResourceItem], str, str | None]:
        settings = reranker_settings()
        candidates = resources[: settings.candidate_limit]
        if not settings.enabled or not candidates:
            record_rerank("disabled", "none", len(candidates), len(candidates), 0.0)
            return candidates, "disabled", None

        query = self._dense_query_text(req, self._extract_core_terms(req))[:4_000]
        documents = [self._rerank_document(item) for item in candidates]
        started = time.perf_counter()
        try:
            reranker = getattr(self, "_reranker", None)
            if reranker is None:
                reranker = CrossEncoderReranker(settings.model)
                self._reranker = reranker
            scores = await reranker.score(query, documents, settings.timeout_seconds)
        except Exception as failure:  # noqa: BLE001
            failure_reason = self._rerank_failure_reason(failure)
            record_rerank("fallback", failure_reason, len(candidates), len(candidates), time.perf_counter() - started)
            logger.warning(
                "Cross Encoder reranking degraded to RRF errorType=%s",
                type(failure).__name__,
            )
            return candidates, "fallback", failure_reason

        for item, confidence in zip(candidates, scores, strict=True):
            item.confidence = round(confidence, 6)
            if not item.evidence:
                item.evidence_status = "insufficient"

        eligible = [
            item
            for item in candidates
            if item.evidence and (item.confidence or 0.0) >= settings.min_score
        ]
        eligible.sort(
            key=lambda item: (
                -(item.confidence or 0.0),
                -(item.score or 0.0),
                item.id if item.id is not None else 2**63 - 1,
            )
        )
        outcome = "success" if eligible else "rejected"
        rejection_reason = None if eligible else "low_confidence"
        record_rerank(outcome, rejection_reason or "none", len(candidates), len(eligible), time.perf_counter() - started)
        return eligible, outcome, rejection_reason

    @staticmethod
    def _rerank_document(item: ResourceItem) -> str:
        evidence = "\n".join(value.excerpt for value in item.evidence[:3])
        metadata = " | ".join(
            value for value in [item.title, item.domain or "", item.level or "", " ".join(item.tags)] if value
        )
        # The Cross Encoder receives an opaque text pair and cannot emit instructions or tools.
        return f"{metadata}\n{evidence}"[:6_000]

    @staticmethod
    def _rerank_failure_reason(failure: BaseException) -> str:
        if isinstance(failure, TimeoutError):
            return "reranker_timeout"
        if isinstance(failure, RuntimeError):
            return "reranker_unavailable"
        if isinstance(failure, ValueError):
            return "invalid_reranker_output"
        return "reranker_failure"

    def _expand_query(self, req: ResourceQueryContext, core_terms: list[str]) -> list[str]:
        expanded: list[str] = list(core_terms)
        context_texts = [req.topic, req.goal_text, *(req.task_texts or [])]
        if not core_terms:
            return []

        topic_lower = " ".join(filter(None, context_texts)).lower()
        for keyword, related in KEYWORD_HINTS.items():
            if keyword in topic_lower or any(term in topic_lower for term in related):
                expanded.append(keyword.lower())
                expanded.extend(term.lower() for term in related)
        if req.task_type:
            expanded.extend(sorted(TASK_TYPE_HINTS.get(req.task_type.lower(), set())))
        if req.level:
            expanded.extend(sorted(LEVEL_HINTS.get(req.level.lower(), set())))
        deduped = []
        seen = set()
        for term in expanded:
            if term and term not in seen:
                deduped.append(term)
                seen.add(term)
        return deduped[:20]

    def _recall(
        self,
        req: ResourceQueryContext,
        expanded_queries: list[str],
        core_terms: list[str],
    ) -> list[RecallHit]:
        hits: dict[int, RecallHit] = {}
        normalized_core_terms = {self._normalize_text(term) for term in core_terms if self._normalize_text(term)}
        if not normalized_core_terms:
            return []

        candidate_positions = self._select_candidate_positions(req)
        keyword_positions = self._keyword_recall(expanded_queries, candidate_positions)
        vector_scores = self._vector_recall(expanded_queries, candidate_positions)
        selected_positions = set(keyword_positions) | set(vector_scores)
        if not selected_positions:
            selected_positions = candidate_positions

        for position in selected_positions:
            item = self._resources[position]
            score = 0.0
            matched_terms: list[str] = []
            title_text = self._normalize_text(item.title)
            tags_text = [self._normalize_text(tag) for tag in item.tags]
            resource_terms = set(self._resource_terms.get(position, []))
            core_matched_terms: set[str] = set()
            channels: set[str] = set()

            for term in expanded_queries:
                normalized_term = self._normalize_text(term)
                if not normalized_term:
                    continue

                if normalized_term in resource_terms:
                    score += 2.2
                    matched_terms.append(term)
                    channels.add("keyword")
                    if normalized_term in normalized_core_terms:
                        core_matched_terms.add(normalized_term)
                    continue
                if normalized_term in title_text:
                    score += 1.6
                    matched_terms.append(term)
                    channels.add("metadata")
                    if normalized_term in normalized_core_terms:
                        core_matched_terms.add(normalized_term)
                    continue
                if any(normalized_term in tag for tag in tags_text):
                    score += 1.9
                    matched_terms.append(term)
                    channels.add("metadata")
                    if normalized_term in normalized_core_terms:
                        core_matched_terms.add(normalized_term)
                    continue
                if any(
                    normalized_term in resource_term or resource_term in normalized_term
                    for resource_term in resource_terms
                    if len(resource_term) >= 2
                ):
                    score += 0.9
                    matched_terms.append(term)
                    channels.add("keyword")
                    if normalized_term in normalized_core_terms:
                        core_matched_terms.add(normalized_term)

            vector_score = vector_scores.get(position, 0.0)
            if vector_score > 0:
                score += vector_score * 2.4
                channels.add("local-vector")

            if req.level and item.level and req.level.lower() == item.level.lower():
                adaptive = req.adaptive_context is not None and req.adaptive_context.applied
                score += 1.4 if adaptive else 0.9
            elif req.adaptive_context is not None and req.adaptive_context.applied and item.level:
                score -= 0.3
            if req.domain and item.domain and req.domain.lower() == item.domain.lower():
                score += 1.2
            if req.task_type and TASK_TYPE_HINTS.get(req.task_type.lower(), set()).intersection(resource_terms):
                score += 0.8
            if req.estimated_minutes and item.duration_minutes:
                diff = abs(req.estimated_minutes - item.duration_minutes)
                score += max(0.0, 0.7 - diff / 240)
            if req.preferred_style:
                preferred = STYLE_TAGS.get(req.preferred_style, set())
                if preferred and preferred.intersection(set(tags_text + [title_text])):
                    score += 0.6
            unique_terms = self._dedupe_terms(matched_terms)
            if len(unique_terms) >= 2:
                score += min(1.2, len(unique_terms) * 0.3)
            if unique_terms and req.task_texts:
                score += 0.2

            if normalized_core_terms:
                if not core_matched_terms:
                    continue
                score += min(1.6, len(core_matched_terms) * 0.5)

            if score > 0:
                hits[position] = RecallHit(item=item, score=score, matched_terms=unique_terms, channels=channels)
        return list(hits.values())

    def _select_candidate_positions(self, req: ResourceQueryContext) -> set[int]:
        inferred_domain = (req.domain or "").strip().lower()
        if not inferred_domain:
            return set(range(len(self._resources)))

        domain_positions = {
            position for position, item in enumerate(self._resources)
            if item.domain and item.domain.lower() == inferred_domain
        }
        if domain_positions:
            return domain_positions

        domain_hints = DOMAIN_HINTS.get(inferred_domain, set())
        if not domain_hints:
            return set()

        hinted_positions = set()
        for position, item in enumerate(self._resources):
            resource_terms = set(self._resource_terms.get(position, self._resource_index_terms(item)))
            if domain_hints.intersection(resource_terms):
                hinted_positions.add(position)
        return hinted_positions

    def _keyword_recall(self, expanded_queries: list[str], candidate_positions: set[int]) -> set[int]:
        positions: set[int] = set()
        for term in expanded_queries:
            normalized_term = self._normalize_text(term)
            if not normalized_term:
                continue
            positions.update(self._keyword_index.get(normalized_term, set()))
        return positions.intersection(candidate_positions)

    def _vector_recall(self, expanded_queries: list[str], candidate_positions: set[int]) -> dict[int, float]:
        query_terms = self._dedupe_terms([
            normalized
            for term in expanded_queries
            if (normalized := self._normalize_text(term))
        ])
        if not query_terms:
            return {}

        query_vector = self._embed_terms(query_terms)
        scores: dict[int, float] = {}
        for position in candidate_positions:
            vector = self._resource_vectors.get(position)
            if not vector:
                continue
            score = self._cosine_similarity(query_vector, vector)
            if score >= 0.12:
                scores[position] = score
        return dict(sorted(scores.items(), key=lambda item: item[1], reverse=True)[:20])

    def _resource_index_terms(self, item: ResourceItem) -> list[str]:
        text = " ".join(
            str(part)
            for part in [
                item.title,
                item.level,
                item.domain,
                *(item.tags or []),
            ]
            if part
        )
        terms = self._extract_terms(text)
        if item.domain:
            terms.append(item.domain.lower())
        if item.level:
            terms.append(item.level.lower())
        return self._dedupe_terms(terms)

    def _embed_terms(self, terms: list[str]) -> list[float]:
        vector = [0.0] * VECTOR_DIMENSIONS
        for term in terms:
            if not term:
                continue
            digest = hashlib.sha256(term.encode("utf-8")).digest()
            first = int.from_bytes(digest[:4], "big")
            second = int.from_bytes(digest[4:8], "big")
            index = first % VECTOR_DIMENSIONS
            sign = 1.0 if second % 2 == 0 else -1.0
            weight = 1.0 + min(0.8, len(term) / 12.0)
            vector[index] += sign * weight

        norm = math.sqrt(sum(value * value for value in vector))
        if norm == 0:
            return vector
        return [value / norm for value in vector]

    @staticmethod
    def _cosine_similarity(left: list[float], right: list[float]) -> float:
        if not left or not right:
            return 0.0
        return sum(a * b for a, b in zip(left, right))

    def _rerank(
        self,
        req: ResourceQueryContext,
        candidates: list[RecallHit],
    ) -> list[ResourceItem]:
        if not candidates:
            return []

        scored = []
        term_counter = Counter(term for hit in candidates for term in hit.matched_terms)
        for hit in candidates:
            clone = hit.item.model_copy(deep=True)
            feedback_score = self._feedback_score(clone.id)
            source_bonus = 0.3 if "keyword" in hit.channels and "local-vector" in hit.channels else 0.0
            score = hit.score + feedback_score + source_bonus
            clone.score = round(score, 2)
            matched_terms = hit.matched_terms
            clone.matched_terms = matched_terms
            clone.reason = self._build_reason(req, matched_terms, hit.channels, clone.id)
            scored.append(clone)

        scored.sort(
            key=lambda item: (
                -(item.score or 0.0),
                -len(item.matched_terms),
                -max((term_counter[term] for term in item.matched_terms), default=0),
                item.id if item.id is not None else 2**63 - 1,
                item.title,
            ),
        )
        return scored

    def _feedback_score(self, resource_id: int | None) -> float:
        if resource_id is None:
            return 0.0
        stats = getattr(self, "_feedback_stats", {}).get(int(resource_id))
        if not stats:
            return 0.0

        score = 0.0
        if stats.avg_rating is not None:
            score += max(-1.0, min(1.0, (stats.avg_rating - 3.0) * 0.35))
        if stats.feedback_count > 0:
            score += min(0.6, math.log(stats.feedback_count + 1) * 0.18)
        if stats.invalid_count > 0:
            score -= min(1.5, stats.invalid_count * 0.45)
            if stats.feedback_count:
                score -= min(1.2, stats.invalid_count / stats.feedback_count * 1.4)
        return score

    def _extract_core_terms(self, req: ResourceQueryContext) -> list[str]:
        terms: list[str] = []
        for text in [req.topic, req.goal_text, *(req.task_texts or [])]:
            terms.extend(self._extract_terms(text))
        if req.domain and req.domain in DOMAIN_HINTS:
            terms.extend(DOMAIN_HINTS[req.domain])
        return [
            term
            for term in self._dedupe_terms(terms)
            if term not in GENERIC_TERMS and len(term) >= 2
        ][:16]

    def _build_reason(
        self,
        req: ResourceQueryContext,
        matched_terms: list[str],
        channels: set[str] | None = None,
        resource_id: int | None = None,
    ) -> str:
        parts: list[str] = []
        top_terms = matched_terms[:2]
        if top_terms:
            parts.append(f"命中{'、'.join(top_terms)}")
        if channels:
            channel_labels = {
                "metadata": "元数据索引",
                "keyword": "关键词召回",
                "local-vector": "本地向量召回",
            }
            labels = [channel_labels[channel] for channel in ["metadata", "keyword", "local-vector"] if channel in channels]
            if labels:
                parts.append("+".join(labels[:2]))

        task_type_labels = {
            "practice": "适合练习",
            "project": "适合实战",
            "review": "适合复习",
            "debug": "适合排错",
            "learn": "适合入门",
            "plan_overview": "适合规划",
        }
        task_label = task_type_labels.get((req.task_type or "").lower())
        if task_label:
            parts.append(task_label)
        elif req.phase_title:
            parts.append(f"适合{req.phase_title}")

        if resource_id is not None:
            stats = self._feedback_stats.get(int(resource_id))
            if stats and stats.feedback_count > 0:
                parts.append(f"反馈{stats.feedback_count}条")

        return "，".join(parts[:3]) if parts else "主题相关"

    @staticmethod
    def _normalize_text(text: str | None) -> str:
        if not text:
            return ""
        normalized = text.lower()
        normalized = re.sub(r"[^0-9a-z\u4e00-\u9fff#+./-]+", " ", normalized)
        return re.sub(r"\s+", " ", normalized).strip()

    def _extract_terms(self, text: str | None) -> list[str]:
        if not text:
            return []

        normalized = self._normalize_text(text)
        if not normalized:
            return []

        terms: list[str] = []
        for part in re.findall(r"[a-z][a-z0-9+#./-]*|[\u4e00-\u9fff]{2,}", normalized):
            cleaned = part.strip(".-/")
            if not cleaned:
                continue
            terms.append(cleaned)
            if re.fullmatch(r"[\u4e00-\u9fff]{5,}", cleaned):
                for phrase in sorted(DOMAIN_TERMS):
                    if phrase in cleaned:
                        terms.append(phrase)

        for phrase in sorted(DOMAIN_TERMS):
            if phrase in normalized:
                terms.append(phrase)

        return self._dedupe_terms(terms)

    @staticmethod
    def _dedupe_terms(terms: list[str]) -> list[str]:
        deduped: list[str] = []
        seen: set[str] = set()
        for term in terms:
            cleaned = term.strip().lower()
            if not cleaned or cleaned in seen:
                continue
            seen.add(cleaned)
            deduped.append(cleaned)
        return deduped

    @staticmethod
    def _build_query_summary(req: ResourceQueryContext, expanded_queries: list[str]) -> str:
        context_bits = [f"主题={req.topic}"]
        if req.phase_title:
            context_bits.append(f"阶段={req.phase_title}")
        if req.task_type:
            context_bits.append(f"任务类型={req.task_type}")
        if req.level:
            context_bits.append(f"水平={req.level}")
        if req.adaptive_context is not None:
            context_bits.append(
                f"适应性={req.adaptive_context.policy_version}/{req.adaptive_context.reason}"
            )
        if req.domain:
            context_bits.append(f"领域={req.domain}")
        if req.task_texts:
            context_bits.append(f"任务数={len(req.task_texts)}")
        return f"查询上下文：{'；'.join(context_bits)}。扩展词：{'、'.join(expanded_queries[:6]) or '无'}。"

    def _log_call(
        self,
        req: ResourceQueryContext,
        response: ResourceRecommendResponseV2,
        trace_id: str | None,
        start: float,
    ) -> None:
        if not getattr(self, "_enable_call_logging", True):
            return
        try:
            save_agent_call(
                agent_name="RagAgent",
                trace_id=trace_id,
                request_payload=json.dumps(req.model_dump(mode="json"), ensure_ascii=False),
                response_payload=json.dumps(response.model_dump(mode="json"), ensure_ascii=False),
                model_name="-",
                duration_ms=int((time.perf_counter() - start) * 1000),
            )
        except Exception:  # noqa: BLE001
            logger.debug("保存 RagAgent 调用日志失败，但不影响主流程。")


SAMPLE_RESOURCES: List[ResourceItem] = [
    ResourceItem(
        title="Java 基础语法快速入门（B 站视频）",
        url="https://www.bilibili.com/video/BV1xx411c7mD",
        level="beginner",
        domain="java",
        duration_minutes=120,
        tags=["java", "basic", "intro", "bilibili", "video"],
        reason="系统讲解 Java 基础语法，适合零基础快速入门。",
        source="sample",
    ),
    ResourceItem(
        title="Java 面向对象与集合框架精讲（CSDN 文章）",
        url="https://blog.csdn.net/xx/article/details/123456789",
        level="intermediate",
        domain="java",
        duration_minutes=60,
        tags=["java", "oop", "collections", "csdn", "blog"],
        reason="适合进一步理解类、对象、继承和集合框架。",
        source="sample",
    ),
    ResourceItem(
        title="Spring Boot 快速入门实战（掘金专栏）",
        url="https://juejin.cn/post/684490xxxxxxxxxxxx",
        level="beginner",
        domain="java",
        duration_minutes=90,
        tags=["spring", "springboot", "backend", "juejin", "blog", "project"],
        reason="通过简单 RESTful 项目帮助快速上手 Spring Boot。",
        source="sample",
    ),
    ResourceItem(
        title="PostgreSQL 数据库基础教程",
        url="https://www.runoob.com/postgresql/postgresql-tutorial.html",
        level="beginner",
        domain="database",
        duration_minutes=75,
        tags=["database", "postgres", "sql", "docs"],
        reason="适合作为后端学习中的数据库基础材料。",
        source="sample",
    ),
    ResourceItem(
        title="RESTful API 设计最佳实践",
        url="https://zhuanlan.zhihu.com/p/xxxxxxx",
        level="intermediate",
        domain="java",
        duration_minutes=45,
        tags=["rest", "api", "backend", "zhihu", "blog"],
        reason="总结接口设计规范与常见问题。",
        source="sample",
    ),
    ResourceItem(
        title="英语四级高频词汇速记",
        url="https://www.bilibili.com/video/BV1vK4y1m7wq",
        level="beginner",
        domain="english",
        duration_minutes=60,
        tags=["english", "cet4", "vocabulary", "bilibili", "video"],
        reason="适合英语四级备考时快速补高频词汇。",
        source="sample",
    ),
    ResourceItem(
        title="英语四级阅读理解技巧精讲",
        url="https://www.bilibili.com/video/BV1bL411v7m3",
        level="beginner",
        domain="english",
        duration_minutes=50,
        tags=["english", "cet4", "reading", "video"],
        reason="适合提升英语四级阅读理解的做题方法。",
        source="sample",
    ),
    ResourceItem(
        title="Python 官方入门教程",
        url="https://docs.python.org/3/tutorial/",
        level="beginner",
        domain="python",
        duration_minutes=110,
        tags=["python", "official", "docs", "intro"],
        reason="适合作为 Python 入门学习的基础材料。",
        source="sample",
    ),
]
