#!/usr/bin/env python3
"""Validate provisioned observability assets without requiring Docker."""

from __future__ import annotations

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OBSERVABILITY = ROOT / "ops" / "observability"
REQUIRED_DASHBOARDS = {
    "learnflow-api-slo",
    "learnflow-agent-model",
    "learnflow-database",
    "learnflow-queue",
    "learnflow-cost",
}


def fail(message: str) -> None:
    raise ValueError(message)


def validate_dashboards() -> None:
    found: set[str] = set()
    for path in sorted((OBSERVABILITY / "grafana" / "dashboards").glob("*.json")):
        dashboard = json.loads(path.read_text(encoding="utf-8-sig"))
        uid = dashboard.get("uid")
        if not isinstance(uid, str) or not uid:
            fail(f"{path}: dashboard uid is required")
        if uid in found:
            fail(f"{path}: duplicate dashboard uid {uid}")
        found.add(uid)
        if not dashboard.get("title") or not dashboard.get("panels"):
            fail(f"{path}: dashboard needs a title and at least one panel")
        for panel in dashboard["panels"]:
            if not panel.get("title") or not panel.get("targets"):
                fail(f"{path}: every panel needs a title and query target")
    missing = REQUIRED_DASHBOARDS - found
    if missing:
        fail(f"missing dashboards: {sorted(missing)}")


def validate_alert_metadata() -> None:
    rules_path = OBSERVABILITY / "prometheus" / "rules" / "learnflow-slo.yml"
    content = rules_path.read_text(encoding="utf-8-sig")
    alert_count = content.count("      - alert:")
    if alert_count < 10:
        fail("expected at least ten actionable LearnFlow alerts")
    for field in ("impact:", "dashboard:", "runbook:", "owner:", "severity:"):
        if content.count(field) < alert_count:
            fail(f"every alert must include {field.rstrip(':')}")


def validate_compose_references() -> None:
    compose = (ROOT / "docker-compose.yml").read_text(encoding="utf-8-sig")
    for service in ("otel-collector:", "prometheus:", "alertmanager:", "grafana:"):
        if service not in compose:
            fail(f"docker-compose.yml does not define {service.rstrip(':')}")
    for path in (
        OBSERVABILITY / "prometheus" / "prometheus.yml",
        OBSERVABILITY / "alertmanager" / "alertmanager.yml",
        OBSERVABILITY / "grafana" / "provisioning" / "datasources" / "prometheus.yml",
    ):
        if not path.is_file():
            fail(f"missing provisioned asset: {path}")


def main() -> int:
    try:
        validate_dashboards()
        validate_alert_metadata()
        validate_compose_references()
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"observability asset validation failed: {exc}", file=sys.stderr)
        return 1
    print("observability assets valid: 5 dashboards, alert metadata, and Compose references")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
