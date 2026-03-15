"""
???????????

?????
- ?????????? / ???? / ??????????? LLM ???
- ??????????????????????? Agent ?????????
- ??????????? HTTP ?????????????????? JSON ?????

??? OpenAI ?????/v1/chat/completions??????
???? DeepSeek / QwQ ??????????
"""

import logging
from typing import Optional

import httpx

from app.config.llm_runtime import get_effective_llm_config

logger = logging.getLogger(__name__)


def ask_llm(prompt: str, model: Optional[str] = None, system_prompt: Optional[str] = None) -> str:
    """
    ???????????

    - prompt: ?????????????? JSON???????
    - model: ?????????????????????
    - system_prompt: ??????????????

    ????
    - ???????????????
    - ????????????? Agent ???????
    """
    config = get_effective_llm_config()
    api_base = config.get("apiBase")
    api_key = config.get("apiKey")
    default_model = config.get("defaultModel") or "gpt-4o-mini"
    use_model = model or default_model

    if not api_base or not api_key:
        logger.warning("LLM_API_BASE ? LLM_API_KEY ????ask_llm ?????????? Agent ?????")
        return ""

    url = str(api_base).rstrip("/") + "/v1/chat/completions"
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json",
    }
    messages = []
    if system_prompt:
        messages.append({"role": "system", "content": system_prompt})
    else:
        messages.append(
            {
                "role": "system",
                "content": "You are a helpful AI assistant that strictly follows the user's JSON output instructions.",
            }
        )
    messages.append({"role": "user", "content": prompt})

    payload = {
        "model": use_model,
        "messages": messages,
    }

    try:
        timeout = httpx.Timeout(connect=10.0, read=60.0, write=30.0, pool=30.0)
        with httpx.Client(timeout=timeout) as client:
            resp = client.post(url, headers=headers, json=payload)
            resp.raise_for_status()
            data = resp.json()
            content = data["choices"][0]["message"]["content"]
            return content if isinstance(content, str) else str(content)
    except httpx.HTTPStatusError as exc:
        response = exc.response
        preview = _build_response_preview(response.text if response is not None else "")
        logger.error(
            "?? LLM ??? 2xx ???model=%s status=%s url=%s response=%s",
            use_model,
            response.status_code if response is not None else "unknown",
            url,
            preview,
        )
        return ""
    except Exception as exc:  # noqa: BLE001
        logger.exception(
            "?? LLM ???model=%s url=%s error=%s",
            use_model,
            url,
            exc,
        )
        return ""


def _build_response_preview(text: str, limit: int = 400) -> str:
    if not text:
        return "<empty>"
    compact = " ".join(text.split())
    if len(compact) <= limit:
        return compact
    return compact[:limit] + "..."
