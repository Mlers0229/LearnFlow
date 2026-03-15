import json
import logging
import time
from typing import Any, AsyncGenerator, List

import httpx
from fastapi import APIRouter, Body, HTTPException
from starlette.responses import StreamingResponse

from app.config.llm_runtime import (
    get_effective_llm_config,
    load_runtime_config,
    mask_secret,
    save_runtime_config,
)
from app.models.chat import ChatMessage, ChatRequest

router = APIRouter(tags=["chat"])
logger = logging.getLogger(__name__)

_MODEL_CACHE_TTL_SECONDS = 60
_model_cache: dict[str, Any] = {
    "expires_at": 0.0,
    "payload": None,
}


def _reset_model_cache() -> None:
    _model_cache["expires_at"] = 0.0
    _model_cache["payload"] = None


def _normalize_optional_text(value: Any) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def _get_llm_config() -> tuple[str | None, str | None, str | None, bool]:
    config = get_effective_llm_config()
    return (
        config.get("apiBase"),
        config.get("apiKey"),
        config.get("defaultModel"),
        bool(config.get("autoDiscoverModels", True)),
    )


def _fallback_model_payload(
    default_model: str | None,
    message: str,
    configured: bool,
    source: str = "fallback",
):
    model_id = default_model or "gpt-4o-mini"
    return {
        "configured": configured,
        "source": source,
        "defaultModel": model_id,
        "message": message,
        "models": [
            {
                "id": model_id,
                "label": model_id,
                "ownedBy": "configured-default",
            }
        ],
    }


async def _discover_remote_models(force_refresh: bool = False) -> dict[str, Any]:
    now = time.time()
    if (
        not force_refresh
        and _model_cache.get("payload") is not None
        and now < float(_model_cache.get("expires_at") or 0)
    ):
        return _model_cache["payload"]

    api_base, api_key, default_model, auto_discover_models = _get_llm_config()
    configured = bool(api_base and api_key)
    if not configured:
        payload = _fallback_model_payload(
            default_model,
            "LLM_API_BASE or LLM_API_KEY is not configured.",
            configured=False,
        )
        _model_cache["payload"] = payload
        _model_cache["expires_at"] = now + _MODEL_CACHE_TTL_SECONDS
        return payload

    if not auto_discover_models:
        payload = _fallback_model_payload(
            default_model,
            "Auto discovery is disabled by admin configuration.",
            configured=True,
            source="manual",
        )
        _model_cache["payload"] = payload
        _model_cache["expires_at"] = now + _MODEL_CACHE_TTL_SECONDS
        return payload

    url = str(api_base).rstrip("/") + "/v1/models"
    headers = {"Authorization": f"Bearer {api_key}"}

    try:
        timeout = httpx.Timeout(connect=10.0, read=30.0, write=30.0, pool=30.0)
        async with httpx.AsyncClient(timeout=timeout) as client:
            response = await client.get(url, headers=headers)
            response.raise_for_status()
            payload = response.json()
    except Exception as exc:  # noqa: BLE001
        logger.exception("Failed to discover remote chat models", exc_info=exc)
        fallback = _fallback_model_payload(
            default_model,
            f"Remote model discovery failed: {exc}",
            configured=True,
        )
        _model_cache["payload"] = fallback
        _model_cache["expires_at"] = now + 10
        return fallback

    items = payload.get("data") if isinstance(payload, dict) else None
    models: list[dict[str, Any]] = []
    if isinstance(items, list):
        for item in items:
            if not isinstance(item, dict):
                continue
            model_id = item.get("id")
            if not model_id:
                continue
            models.append(
                {
                    "id": str(model_id),
                    "label": str(model_id),
                    "ownedBy": item.get("owned_by") or item.get("ownedBy") or "remote",
                    "created": item.get("created"),
                }
            )

    models.sort(key=lambda item: (item["id"] != default_model, item["id"].lower()))
    if not models:
        models = _fallback_model_payload(
            default_model,
            "Remote model list is empty.",
            configured=True,
        )["models"]

    result = {
        "configured": True,
        "source": "remote",
        "defaultModel": default_model or models[0]["id"],
        "message": "ok",
        "models": models,
    }
    _model_cache["payload"] = result
    _model_cache["expires_at"] = now + _MODEL_CACHE_TTL_SECONDS
    return result


async def _build_admin_config_payload(refresh_catalog: bool = False) -> dict[str, Any]:
    config = get_effective_llm_config()
    catalog = await _discover_remote_models(force_refresh=refresh_catalog)
    return {
        "configured": bool(config.get("apiBase") and config.get("apiKey")),
        "apiBase": config.get("apiBase") or "",
        "defaultModel": config.get("defaultModel") or "",
        "enableLlmPlan": bool(config.get("enableLlmPlan")),
        "autoDiscoverModels": bool(config.get("autoDiscoverModels", True)),
        "hasApiKey": bool(config.get("apiKey")),
        "maskedApiKey": mask_secret(config.get("apiKey")),
        "updatedAt": config.get("updatedAt"),
        "source": config.get("source") or {},
        "catalog": catalog,
    }


async def _stream_llm_chat(
    messages: List[ChatMessage], system_prompt: str | None, model: str | None
) -> AsyncGenerator[str, None]:
    api_base, api_key, default_model, _ = _get_llm_config()
    if not api_base or not api_key:
        yield "\uff08\u672c\u5730\u515c\u5e95\uff0c\u672a\u914d\u7f6e LLM_API_BASE/LLM_API_KEY\uff09"
        last_user = next((m.content for m in reversed(messages) if m.role == "user"), "")
        if last_user:
            yield f"\u4f60\u521a\u624d\u8bf4\uff1a{last_user[:200]}"
        return

    url = str(api_base).rstrip("/") + "/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }

    payload = {
        "model": model or default_model,
        "stream": True,
        "messages": [],
    }
    if system_prompt:
        payload["messages"].append({"role": "system", "content": system_prompt})
    for message in messages:
        payload["messages"].append({"role": message.role, "content": message.content})

    try:
        async with httpx.AsyncClient(timeout=60) as client:
            async with client.stream("POST", url, headers=headers, json=payload) as resp:
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if not line:
                        continue
                    if not line.startswith("data: "):
                        continue
                    data = line[len("data: ") :]
                    if data.strip() == "[DONE]":
                        break
                    try:
                        chunk = json.loads(data)
                        delta = (
                            chunk["choices"][0]["delta"].get("content")
                            if chunk.get("choices")
                            else None
                        )
                        if delta:
                            yield delta
                    except Exception:  # noqa: BLE001
                        continue
    except Exception as exc:  # noqa: BLE001
        logger.exception("Failed to stream chat completion", exc_info=exc)
        yield f"\n[error] \u6d41\u5f0f\u751f\u6210\u5931\u8d25\uff1a{exc}\n"


@router.get("/chat/models")
async def chat_models(refresh: bool = False):
    return await _discover_remote_models(force_refresh=refresh)


@router.get("/chat/admin-config")
async def get_chat_admin_config(refresh: bool = False):
    return await _build_admin_config_payload(refresh_catalog=refresh)


@router.put("/chat/admin-config")
async def update_chat_admin_config(payload: dict[str, Any] = Body(default_factory=dict)):
    current_runtime = load_runtime_config()
    current_effective = get_effective_llm_config()

    next_payload: dict[str, Any] = {
        "apiBase": _normalize_optional_text(payload.get("apiBase")),
        "defaultModel": _normalize_optional_text(payload.get("defaultModel"))
        or current_effective.get("defaultModel")
        or "gpt-4o-mini",
        "enableLlmPlan": bool(
            payload.get("enableLlmPlan", current_effective.get("enableLlmPlan", True))
        ),
        "autoDiscoverModels": bool(
            payload.get(
                "autoDiscoverModels",
                current_effective.get("autoDiscoverModels", True),
            )
        ),
    }

    clear_api_key = bool(payload.get("clearApiKey", False))
    new_api_key = _normalize_optional_text(payload.get("apiKey"))
    if clear_api_key:
        next_payload["apiKey"] = None
    elif new_api_key:
        next_payload["apiKey"] = new_api_key
    elif "apiKey" in current_runtime:
        next_payload["apiKey"] = current_runtime.get("apiKey")

    save_runtime_config(next_payload)
    _reset_model_cache()
    return await _build_admin_config_payload(
        refresh_catalog=bool(next_payload.get("autoDiscoverModels"))
    )


@router.post("/chat/admin-config/refresh-models")
async def refresh_chat_admin_models():
    _reset_model_cache()
    return await _build_admin_config_payload(refresh_catalog=True)


@router.post("/chat/stream")
async def chat_stream(req: ChatRequest):
    if not req.messages:
        raise HTTPException(status_code=400, detail="messages cannot be empty")

    async def generator():
        yield "retry: 1000\n\n"
        async for chunk in _stream_llm_chat(req.messages, req.system_prompt, req.model):
            safe_chunk = chunk.replace("\n", "\\n")
            yield f"data: {safe_chunk}\n\n"

    return StreamingResponse(
        generator(),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "X-Accel-Buffering": "no",
            "Connection": "keep-alive",
        },
    )
