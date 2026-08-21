import ipaddress
import os
import socket
from urllib.parse import urlsplit


class OutboundUrlBlocked(ValueError):
    pass


def _configured_allowed_hosts() -> set[str]:
    return {
        host.strip().lower().rstrip(".")
        for host in os.getenv("LEARNFLOW_LLM_ALLOWED_HOSTS", "").split(",")
        if host.strip()
    }


def validate_llm_url(url: str) -> str:
    """Validate an outbound model URL before every connection attempt."""
    parsed = urlsplit(url)
    if parsed.scheme not in {"https", "http"}:
        raise OutboundUrlBlocked("only HTTP(S) model endpoints are allowed")
    if parsed.username or parsed.password:
        raise OutboundUrlBlocked("credentials in model URLs are forbidden")
    host = (parsed.hostname or "").lower().rstrip(".")
    if not host:
        raise OutboundUrlBlocked("model endpoint host is missing")
    if parsed.scheme == "http" and os.getenv("LEARNFLOW_ALLOW_INSECURE_LLM_HTTP", "false").lower() != "true":
        raise OutboundUrlBlocked("plain HTTP model endpoints are disabled")

    allowed_hosts = _configured_allowed_hosts()
    if allowed_hosts and host not in allowed_hosts:
        raise OutboundUrlBlocked("model endpoint host is not allow-listed")
    if os.getenv("LEARNFLOW_ENV", "development").lower() == "production" and not allowed_hosts:
        raise OutboundUrlBlocked("production requires LEARNFLOW_LLM_ALLOWED_HOSTS")

    try:
        addresses = {
            item[4][0]
            for item in socket.getaddrinfo(host, parsed.port or (443 if parsed.scheme == "https" else 80))
        }
    except socket.gaierror as exc:
        raise OutboundUrlBlocked("model endpoint DNS resolution failed") from exc
    if not addresses:
        raise OutboundUrlBlocked("model endpoint resolved to no addresses")
    for address in addresses:
        ip = ipaddress.ip_address(address)
        if not ip.is_global:
            raise OutboundUrlBlocked("model endpoint resolved to a non-public address")
    return url
