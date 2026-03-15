from fastapi import APIRouter

from app.agents.rag_agent import RagAgent
from app.models.resource import (
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
