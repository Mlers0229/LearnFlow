from __future__ import annotations

from collections import Counter
from typing import List
import json
import logging
import re
import time

from sqlalchemy.orm import Session

from app.db import ResourceBank, SessionLocal, save_agent_call
from app.models.resource import (
    ResourceItem,
    ResourceQueryContext,
    ResourceRecommendRequest,
    ResourceRecommendResponse,
    ResourceRecommendResponseV2,
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


class RagAgent:
    """RAG v2：查询扩展 + 规则召回 + 解释型重排。"""

    def __init__(self) -> None:
        self._resources: List[ResourceItem] = SAMPLE_RESOURCES
        self._resource_loaded_at = 0.0
        self._load_resources_from_db()

    def _load_resources_from_db(self) -> None:
        try:
            db: Session
            with SessionLocal() as db:
                rows: List[ResourceBank] = db.query(ResourceBank).filter(ResourceBank.status == "ACTIVE").all()
                if not rows:
                    logger.info("resource_bank 为空，继续使用内置资源。")
                    return

                resources: List[ResourceItem] = []
                for row in rows:
                    tags = [tag.strip() for tag in (row.tags or "").split(",") if tag.strip()]
                    resources.append(
                        ResourceItem(
                            id=row.id,
                            title=row.title,
                            url=row.url,
                            level=row.level,
                            domain=row.domain,
                            duration_minutes=row.duration_minutes,
                            tags=tags,
                            source="db",
                        )
                )
                self._resources = resources
                logger.info("已从数据库加载 %d 条资源用于 RAG 推荐。", len(resources))
        except Exception as exc:  # noqa: BLE001
            logger.exception("加载资源失败，将继续使用内置资源。", exc_info=exc)
        finally:
            self._resource_loaded_at = time.time()

    def _refresh_resources_if_needed(self) -> None:
        if time.time() - self._resource_loaded_at >= RESOURCE_CACHE_TTL_SECONDS:
            self._load_resources_from_db()

    def recommend(self, req: ResourceRecommendRequest, trace_id: str | None = None) -> ResourceRecommendResponse:
        ctx = ResourceQueryContext(topic=req.topic, level=req.level)
        result = self.recommend_v2(ctx, trace_id=trace_id)
        return ResourceRecommendResponse(resources=result.resources)

    def recommend_v2(
        self,
        req: ResourceQueryContext,
        trace_id: str | None = None,
    ) -> ResourceRecommendResponseV2:
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
            rerank_strategy="rule-based-recall-rerank",
            query_summary=self._build_query_summary(req, expanded_queries),
        )
        self._log_call(req, response, trace_id, start)
        return response

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
    ) -> list[tuple[ResourceItem, float, list[str]]]:
        candidates: list[tuple[ResourceItem, float, list[str]]] = []
        normalized_core_terms = {self._normalize_text(term) for term in core_terms if self._normalize_text(term)}
        if not normalized_core_terms:
            return candidates
        candidate_pool = self._select_candidate_pool(req)
        for item in candidate_pool:
            score = 0.0
            matched_terms: list[str] = []
            title_text = self._normalize_text(item.title)
            tags_text = [self._normalize_text(tag) for tag in item.tags]
            resource_terms = set(self._extract_terms(" ".join([item.title, *item.tags])))
            core_matched_terms: set[str] = set()

            for term in expanded_queries:
                normalized_term = self._normalize_text(term)
                if not normalized_term:
                    continue

                if normalized_term in resource_terms:
                    score += 2.2
                    matched_terms.append(term)
                    if normalized_term in normalized_core_terms:
                        core_matched_terms.add(normalized_term)
                    continue
                if normalized_term in title_text:
                    score += 1.6
                    matched_terms.append(term)
                    if normalized_term in normalized_core_terms:
                        core_matched_terms.add(normalized_term)
                    continue
                if any(normalized_term in tag for tag in tags_text):
                    score += 1.9
                    matched_terms.append(term)
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
                    if normalized_term in normalized_core_terms:
                        core_matched_terms.add(normalized_term)

            if req.level and item.level and req.level.lower() == item.level.lower():
                score += 0.9
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
                candidates.append((item, score, unique_terms))
        return candidates

    def _select_candidate_pool(self, req: ResourceQueryContext) -> list[ResourceItem]:
        inferred_domain = (req.domain or "").strip().lower()
        if not inferred_domain:
            return self._resources

        domain_items = [
            item for item in self._resources
            if item.domain and item.domain.lower() == inferred_domain
        ]
        if domain_items:
            return domain_items

        domain_hints = DOMAIN_HINTS.get(inferred_domain, set())
        if not domain_hints:
            return []

        hinted_items = []
        for item in self._resources:
            resource_terms = set(self._extract_terms(" ".join([item.title, *item.tags])))
            if domain_hints.intersection(resource_terms):
                hinted_items.append(item)
        return hinted_items

    def _rerank(
        self,
        req: ResourceQueryContext,
        candidates: list[tuple[ResourceItem, float, list[str]]],
    ) -> list[ResourceItem]:
        if not candidates:
            return []

        scored = []
        term_counter = Counter(term for _, _, matched_terms in candidates for term in matched_terms)
        for item, score, matched_terms in candidates:
            clone = item.model_copy(deep=True)
            clone.score = round(score, 2)
            clone.matched_terms = matched_terms
            clone.reason = self._build_reason(req, matched_terms)
            scored.append(clone)

        scored.sort(
            key=lambda item: (
                item.score or 0.0,
                len(item.matched_terms),
                term_counter[item.matched_terms[0]] if item.matched_terms else 0,
            ),
            reverse=True,
        )
        return scored

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

    def _build_reason(self, req: ResourceQueryContext, matched_terms: list[str]) -> str:
        parts: list[str] = []
        top_terms = matched_terms[:2]
        if top_terms:
            parts.append(f"命中{'、'.join(top_terms)}")

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

        return "，".join(parts[:2]) if parts else "主题相关"

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
                for phrase in DOMAIN_TERMS:
                    if phrase in cleaned:
                        terms.append(phrase)

        for phrase in DOMAIN_TERMS:
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
        if req.domain:
            context_bits.append(f"领域={req.domain}")
        if req.task_texts:
            context_bits.append(f"任务数={len(req.task_texts)}")
        return f"查询上下文：{'；'.join(context_bits)}。扩展词：{'、'.join(expanded_queries[:6]) or '无'}。"

    @staticmethod
    def _log_call(
        req: ResourceQueryContext,
        response: ResourceRecommendResponseV2,
        trace_id: str | None,
        start: float,
    ) -> None:
        try:
            save_agent_call(
                agent_name="RagAgent",
                trace_id=trace_id,
                request_payload=json.dumps(req.model_dump(), ensure_ascii=False),
                response_payload=json.dumps(response.model_dump(), ensure_ascii=False),
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
