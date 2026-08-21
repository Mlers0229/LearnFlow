"""
???????????

?????
- ?????????? / ???? / ??????????? LLM ???
- ??????????????????????? Agent ?????????
- ??????????? HTTP ?????????????????? JSON ?????

??? OpenAI ?????/v1/chat/completions??????
???? DeepSeek / QwQ ??????????
"""

import asyncio
import logging
from typing import Optional

import httpx

from app.config.llm_runtime import get_effective_llm_config
from app.core.http_client import build_timeout, get_http_client
from app.core.request_budget import downstream_seconds
from app.core.resilience import (
    ModelBulkheadFull,
    ModelCircuitOpen,
    get_model_resilience,
)
from app.observability import mark_model_outcome, model_call_span, record_model_usage
from app.outbound_security import OutboundUrlBlocked, validate_llm_url

logger = logging.getLogger(__name__)


async def ask_llm(
    prompt: str,
    model: Optional[str] = None,
    system_prompt: Optional[str] = None,
) -> str:
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
        with model_call_span(use_model, "chat") as span:
            mark_model_outcome(span, "fallback", "model_not_configured")
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

    with model_call_span(use_model, "chat") as span:
        try:
            validate_llm_url(url)
            overall_timeout = downstream_seconds(60.0)
            client = await get_http_client()
            resilience = await get_model_resilience()

            async def invoke_model() -> tuple[str, object]:
                async with asyncio.timeout(overall_timeout):
                    resp = await client.post(
                        url,
                        headers=headers,
                        json=payload,
                        timeout=build_timeout(read_timeout=min(60.0, overall_timeout)),
                    )
                    resp.raise_for_status()
                    data = resp.json()
                    content = data["choices"][0]["message"]["content"]
                    result = content if isinstance(content, str) else str(content)
                    return result, data.get("usage")

            content, usage = await resilience.execute(
                invoke_model,
                records_failure=_records_model_failure,
            )
            record_model_usage(span, use_model, usage)
            mark_model_outcome(span, "success")
            return content
        except OutboundUrlBlocked as exc:
            mark_model_outcome(span, "blocked", "ssrf_policy")
            logger.error("Blocked unsafe LLM endpoint: reason=%s", str(exc))
            return ""
        except httpx.HTTPStatusError as exc:
            response = exc.response
            reason = f"http_{response.status_code}" if response is not None else "http_error"
            mark_model_outcome(span, "failure", reason)
            logger.error(
                "?? LLM ??? 2xx ???model=%s status=%s",
                use_model,
                response.status_code if response is not None else "unknown",
            )
            return ""
        except (TimeoutError, httpx.TimeoutException) as exc:
            mark_model_outcome(span, "timeout", "model_timeout")
            logger.error(
                "LLM request timed out; model=%s timeoutType=%s",
                use_model,
                type(exc).__name__,
            )
            return ""
        except httpx.RequestError as exc:
            mark_model_outcome(span, "failure", "transport_error")
            logger.error(
                "LLM request failed; model=%s errorType=%s",
                use_model,
                type(exc).__name__,
            )
            return ""
        except ModelBulkheadFull:
            mark_model_outcome(span, "degraded", "bulkhead_full")
            logger.error("LLM request degraded; model=%s reason=bulkhead_full", use_model)
            return ""
        except ModelCircuitOpen:
            mark_model_outcome(span, "degraded", "circuit_open")
            logger.error("LLM request degraded; model=%s reason=circuit_open", use_model)
            return ""
        except Exception as exc:  # noqa: BLE001
            mark_model_outcome(span, "failure", type(exc).__name__)
            logger.exception(
                "?? LLM ???model=%s errorType=%s",
                use_model,
                type(exc).__name__,
            )
            return ""


def _records_model_failure(exc: BaseException) -> bool:
    if isinstance(exc, httpx.HTTPStatusError):
        status = exc.response.status_code
        return status == 429 or status >= 500
    return isinstance(exc, (TimeoutError, httpx.TimeoutException, httpx.RequestError))
