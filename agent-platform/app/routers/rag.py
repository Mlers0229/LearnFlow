from fastapi import APIRouter, HTTPException

from app.agents.rag_agent import RETRIEVER_VERSION, RagAgent
from app.core.embedding import embed_texts, embedding_config
from app.models.resource import (
    InternalEmbeddingRequest,
    InternalEmbeddingResponse,
    InternalEmbeddingResult,
    ResourceIndexRebuildResponse,
    ResourceIndexStatus,
    ResourceQueryContext,
    ResourceRecommendRequest,
    ResourceRecommendResponse,
    ResourceRecommendResponseV2,
)
from app.observability import agent_span, current_trace_id

router = APIRouter(tags=["rag"])

rag_agent = RagAgent()


@router.post("/rag/resources", response_model=ResourceRecommendResponse)
async def recommend_resources(payload: ResourceRecommendRequest) -> ResourceRecommendResponse:
    """兼容旧接口，返回基础资源列表。"""
    with agent_span("RagAgent", RETRIEVER_VERSION, operation="recommend"):
        return rag_agent.recommend(payload, trace_id=current_trace_id())


@router.post("/v2/rag/resources", response_model=ResourceRecommendResponseV2)
async def recommend_resources_v2(payload: ResourceQueryContext) -> ResourceRecommendResponseV2:
    """RAG v2：返回 Dense/Sparse RRF、增强上下文和可降级结果。"""
    with agent_span("RagAgent", RETRIEVER_VERSION, operation="recommend"):
        return await rag_agent.recommend_v2_hybrid(payload, trace_id=current_trace_id())


@router.post("/internal/embeddings", response_model=InternalEmbeddingResponse)
async def create_embeddings(payload: InternalEmbeddingRequest) -> InternalEmbeddingResponse:
    """受内部服务凭证保护的有界批量 Embedding 入口。"""
    config = embedding_config()
    if payload.version != config.version or payload.model != config.model:
        raise HTTPException(status_code=409, detail="embedding version is not configured")
    if payload.dimensions != config.dimensions:
        raise HTTPException(status_code=409, detail="embedding dimensions are not configured")
    vectors = await embed_texts(
        [item.text for item in payload.items],
        model=payload.model,
        dimensions=payload.dimensions,
    )
    return InternalEmbeddingResponse(
        version=config.version,
        model=config.model,
        dimensions=config.dimensions,
        items=[
            InternalEmbeddingResult(chunkId=item.chunk_id, embedding=vector)
            for item, vector in zip(payload.items, vectors, strict=True)
        ],
    )


@router.get("/v2/rag/index/status", response_model=ResourceIndexStatus)
async def rag_index_status() -> ResourceIndexStatus:
    """查看 RAG 资源索引状态。"""
    with agent_span("RagAgent", RETRIEVER_VERSION, operation="index-status"):
        return rag_agent.index_status()


@router.post("/v2/rag/index/rebuild", response_model=ResourceIndexRebuildResponse)
async def rebuild_rag_index() -> ResourceIndexRebuildResponse:
    """重新加载降级索引；PostgreSQL Dense/Sparse 索引由迁移和摄取流水线维护。"""
    with agent_span("RagAgent", RETRIEVER_VERSION, operation="index-rebuild"):
        return ResourceIndexRebuildResponse(rebuilt=True, status=rag_agent.rebuild_index())
