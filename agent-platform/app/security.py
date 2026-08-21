import hmac
from collections.abc import Iterable


def is_valid_internal_authorization(
    authorization: str | None,
    expected_tokens: str | Iterable[str],
) -> bool:
    value = authorization or ""
    received = value[7:] if value.lower().startswith("bearer ") else ""
    if not received:
        return False
    tokens = [expected_tokens] if isinstance(expected_tokens, str) else expected_tokens
    return any(token and hmac.compare_digest(received, token) for token in tokens)
