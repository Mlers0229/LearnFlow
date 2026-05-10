from fastapi import APIRouter

from app.agents.rag_agent import RagAgent
from app.models.resource import (
    ResourceIndexRebuildResponse,
    ResourceIndexStatus,
    ResourceQueryContext,
    ResourceRecommendRequest,
    ResourceRecommendResponse,
    ResourceRecommendResponseV2,
)

router = APIRouter(tags=["rag"])

rag_agent = RagAgent()


@router.post("/rag/resources", response_model=ResourceRecommendResponse)
async def recommend_resources(payload: ResourceRecommendRequest) -> ResourceRecommendResponse:
    """兼容旧接口，返回基础资源列表。"""
    return rag_agent.recommend(payload)


@router.post("/v2/rag/resources", response_model=ResourceRecommendResponseV2)
async def recommend_resources_v2(payload: ResourceQueryContext) -> ResourceRecommendResponseV2:
    """RAG v2：返回增强上下文、扩展词和重排结果。"""
    return rag_agent.recommend_v2(payload)


@router.get("/v2/rag/index/status", response_model=ResourceIndexStatus)
async def rag_index_status() -> ResourceIndexStatus:
    """查看 RAG 资源索引状态。"""
    return rag_agent.index_status()


@router.post("/v2/rag/index/rebuild", response_model=ResourceIndexRebuildResponse)
async def rebuild_rag_index() -> ResourceIndexRebuildResponse:
    """重新加载资源库元数据、关键词索引、本地向量索引和反馈统计。"""
    return ResourceIndexRebuildResponse(rebuilt=True, status=rag_agent.rebuild_index())
