import socket

import pytest

from app.outbound_security import OutboundUrlBlocked, validate_llm_url


def test_blocks_loopback_and_cloud_metadata(monkeypatch):
    monkeypatch.setenv("LEARNFLOW_ALLOW_INSECURE_LLM_HTTP", "true")
    monkeypatch.setattr(
        socket,
        "getaddrinfo",
        lambda *args, **kwargs: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("127.0.0.1", 80))],
    )

    with pytest.raises(OutboundUrlBlocked, match="non-public"):
        validate_llm_url("http://localhost/v1/models")
    with pytest.raises(OutboundUrlBlocked, match="non-public"):
        validate_llm_url("http://169.254.169.254/latest/meta-data")


def test_requires_allowlist_in_production(monkeypatch):
    monkeypatch.setenv("LEARNFLOW_ENV", "production")
    monkeypatch.delenv("LEARNFLOW_LLM_ALLOWED_HOSTS", raising=False)

    with pytest.raises(OutboundUrlBlocked, match="requires"):
        validate_llm_url("https://api.example.com/v1/models")


def test_accepts_allowlisted_public_endpoint(monkeypatch):
    monkeypatch.setenv("LEARNFLOW_ENV", "production")
    monkeypatch.setenv("LEARNFLOW_LLM_ALLOWED_HOSTS", "api.example.com")
    monkeypatch.setattr(
        socket,
        "getaddrinfo",
        lambda *args, **kwargs: [(socket.AF_INET, socket.SOCK_STREAM, 6, "", ("93.184.216.34", 443))],
    )

    assert validate_llm_url("https://api.example.com/v1/models").startswith("https://")
