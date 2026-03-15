from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.db import init_db
from app.routers.plan import router as plan_router
from app.routers.rag import router as rag_router
from app.routers.tutor import router as tutor_router
from app.routers.log import router as log_router
from app.routers.chat import router as chat_router


def create_app() -> FastAPI:
    """
    FastAPI 应用工厂。
    后续如果需要挂更多 Router（goal / rag / tutor / supervisor / log），
    可以在这里统一注册，方便管理。
    """
    # 初始化数据库（若表不存在则创建）
    init_db()

    app = FastAPI(
        title="LearnFlow AI Agent Platform",
        description="多 Agent 学习规划与资源推荐平台（FastAPI）",
        version="0.1.0",
    )

    # 允许前端（如 localhost:5173 / 8080）直接访问流式接口
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )

    app.include_router(plan_router, prefix="/api")
    app.include_router(rag_router, prefix="/api")
    app.include_router(tutor_router, prefix="/api")
    app.include_router(log_router, prefix="/api")
    app.include_router(chat_router, prefix="/api")
    return app


app = create_app()


