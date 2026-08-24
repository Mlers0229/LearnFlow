from __future__ import annotations

import argparse
import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

QUALIFYING_PROFILES = {"baseline", "peak", "spike", "soak"}


def metric_value(metrics: dict[str, Any], name: str, key: str) -> float | None:
    payload = metrics.get(name)
    if not isinstance(payload, dict):
        return None
    value = payload.get("values", {}).get(key)
    return float(value) if value is not None else None


def threshold_failures(metrics: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    for name, payload in metrics.items():
        for expression, result in payload.get("thresholds", {}).items():
            if result.get("ok") is not True:
                failures.append(f"{name}: {expression}")
    return failures


def build_report(
    k6_artifact: dict[str, Any],
    telemetry: dict[str, Any],
    workload_model: dict[str, Any],
    release: str,
    environment: str,
) -> dict[str, Any]:
    errors: list[str] = []
    profile = k6_artifact.get("profile")
    if profile not in QUALIFYING_PROFILES:
        errors.append("smoke or unknown profiles cannot qualify as production capacity evidence")
    if environment.lower() != "staging":
        errors.append("capacity evidence must be produced in staging")
    if release in {"", "unversioned", "development", "latest"}:
        errors.append("capacity evidence requires an immutable source revision")
    if profile not in workload_model.get("profiles", {}):
        errors.append("k6 profile is not present in the versioned workload model")
    elif k6_artifact.get("workload") != workload_model["profiles"][profile]:
        errors.append("k6 workload metadata does not match the versioned workload model")

    metrics = k6_artifact.get("summary", {}).get("metrics", {})
    required_metrics = [
        "learnflow_operation_duration_ms{operation:api_read}",
        "learnflow_operation_success{operation:rag}",
        "learnflow_operation_success{operation:tutor}",
        "learnflow_plan_task_success",
        "learnflow_plan_task_end_to_end_ms",
        "dropped_iterations",
    ]
    for name in required_metrics:
        if name not in metrics:
            errors.append(f"k6 summary is missing required metric: {name}")
    errors.extend("k6 threshold failed: " + value for value in threshold_failures(metrics))

    if telemetry.get("passed") is not True:
        errors.append("Prometheus capacity snapshot is incomplete or outside a required boundary")
    telemetry_results = telemetry.get("results", [])
    if not telemetry_results:
        errors.append("Prometheus capacity snapshot contains no query results")
    errors.extend(
        "telemetry failed: " + str(item.get("name"))
        for item in telemetry_results
        if item.get("status") != "PASS"
    )

    observations = {
        "ordinaryApiP95Ms": metric_value(
            metrics, "learnflow_operation_duration_ms{operation:api_read}", "p(95)"
        ),
        "ragP95Ms": metric_value(
            metrics, "learnflow_operation_duration_ms{operation:rag}", "p(95)"
        ),
        "tutorP95Ms": metric_value(
            metrics, "learnflow_operation_duration_ms{operation:tutor}", "p(95)"
        ),
        "planTaskP95Ms": metric_value(metrics, "learnflow_plan_task_end_to_end_ms", "p(95)"),
        "planTaskSuccessRate": metric_value(metrics, "learnflow_plan_task_success", "rate"),
        "droppedIterations": metric_value(metrics, "dropped_iterations", "count"),
    }
    return {
        "schemaVersion": 1,
        "status": "PASS" if not errors else "FAIL",
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "releaseVersion": release,
        "environment": environment,
        "profile": profile,
        "runId": k6_artifact.get("runId"),
        "observations": observations,
        "telemetry": telemetry_results,
        "errors": errors,
        "conclusion": (
            "The tested revision satisfied the versioned load and saturation boundaries."
            if not errors
            else "The tested revision is not qualified for the performance-capacity release gate."
        ),
    }


def markdown(report: dict[str, Any]) -> str:
    lines = [
        "# LearnFlow Capacity Report",
        "",
        f"- Status: **{report['status']}**",
        f"- Release: `{report['releaseVersion']}`",
        f"- Environment: `{report['environment']}`",
        f"- Profile: `{report['profile']}`",
        f"- Run ID: `{report.get('runId') or 'unknown'}`",
        "",
        "## k6 observations",
        "",
        "| Metric | Value |",
        "| --- | ---: |",
    ]
    for name, value in report["observations"].items():
        lines.append(f"| {name} | {value if value is not None else 'missing'} |")
    lines.extend(
        [
            "",
            "## Platform telemetry",
            "",
            "| Metric | Status | Value |",
            "| --- | --- | ---: |",
        ]
    )
    for item in report["telemetry"]:
        lines.append(
            f"| {item.get('name')} | {item.get('status')} | {item.get('value', 'missing')} |"
        )
    if report["errors"]:
        lines.extend(["", "## Blocking findings", ""])
        lines.extend(f"- {error}" for error in report["errors"])
    lines.extend(["", "## Conclusion", "", report["conclusion"], ""])
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(description="Build a fail-closed LearnFlow capacity report")
    parser.add_argument("--k6-summary", required=True, type=Path)
    parser.add_argument("--telemetry", required=True, type=Path)
    parser.add_argument("--release", required=True)
    parser.add_argument("--environment", default="staging")
    parser.add_argument(
        "--workload-model",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "ops" / "performance" / "workload-model.json",
    )
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--output-markdown", required=True, type=Path)
    args = parser.parse_args()

    report = build_report(
        json.loads(args.k6_summary.read_text(encoding="utf-8")),
        json.loads(args.telemetry.read_text(encoding="utf-8")),
        json.loads(args.workload_model.read_text(encoding="utf-8")),
        args.release,
        args.environment,
    )
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_markdown.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    args.output_markdown.write_text(markdown(report), encoding="utf-8")
    if report["status"] != "PASS":
        raise SystemExit("Capacity report failed closed; inspect blocking findings")
    print(f"Capacity report written to {args.output_markdown}")


if __name__ == "__main__":
    main()
