import json
import os
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

try:
    from app.config import llm_settings
except Exception:  # noqa: BLE001
    llm_settings = None  # type: ignore[assignment]


_RUNTIME_FILE = Path(__file__).with_name("llm_runtime.json")


def _utc_now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _safe_read_json(path: Path) -> dict[str, Any]:
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except Exception:  # noqa: BLE001
        return {}


def load_runtime_config() -> dict[str, Any]:
    data = _safe_read_json(_RUNTIME_FILE)
    if not isinstance(data, dict):
        return {}
    return data


def save_runtime_config(payload: dict[str, Any]) -> dict[str, Any]:
    current = load_runtime_config()
    next_payload = {
        **current,
        **payload,
        "updatedAt": _utc_now_iso(),
    }
    _RUNTIME_FILE.write_text(
        json.dumps(next_payload, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    return next_payload


def get_effective_llm_config() -> dict[str, Any]:
    runtime = load_runtime_config()
    file_api_base = getattr(llm_settings, "LLM_API_BASE", None) if llm_settings is not None else None
    file_api_key = getattr(llm_settings, "LLM_API_KEY", None) if llm_settings is not None else None
    file_default_model = getattr(llm_settings, "LLM_API_MODEL", None) if llm_settings is not None else None
    file_enable_llm_plan = (
        getattr(llm_settings, "ENABLE_LLM_PLAN", None) if llm_settings is not None else None
    )

    api_base = runtime.get("apiBase") or file_api_base or os.getenv("LLM_API_BASE")
    api_key = runtime.get("apiKey") or file_api_key or os.getenv("LLM_API_KEY")
    default_model = (
        runtime.get("defaultModel")
        or file_default_model
        or os.getenv("LLM_API_MODEL")
        or "gpt-4o-mini"
    )

    runtime_enable = runtime.get("enableLlmPlan")
    if runtime_enable is None:
        if file_enable_llm_plan is None:
            enable_llm_plan = os.getenv("ENABLE_LLM_PLAN", "false").lower() == "true"
        else:
            enable_llm_plan = bool(file_enable_llm_plan)
    else:
        enable_llm_plan = bool(runtime_enable)

    auto_discover_models = runtime.get("autoDiscoverModels")
    if auto_discover_models is None:
        auto_discover_models = True

    return {
        "apiBase": api_base,
        "apiKey": api_key,
        "defaultModel": default_model,
        "enableLlmPlan": enable_llm_plan,
        "autoDiscoverModels": bool(auto_discover_models),
        "updatedAt": runtime.get("updatedAt"),
        "source": {
            "apiBase": "runtime" if runtime.get("apiBase") else "llm_settings_or_env",
            "apiKey": "runtime" if runtime.get("apiKey") else "llm_settings_or_env",
            "defaultModel": "runtime" if runtime.get("defaultModel") else "llm_settings_or_env",
            "enableLlmPlan": "runtime" if runtime.get("enableLlmPlan") is not None else "llm_settings_or_env",
            "autoDiscoverModels": "runtime" if runtime.get("autoDiscoverModels") is not None else "default",
        },
    }


def mask_secret(value: str | None) -> str:
    if not value:
        return ""
    if len(value) <= 8:
        return "*" * len(value)
    return f"{value[:4]}{'*' * max(4, len(value) - 8)}{value[-4:]}"
