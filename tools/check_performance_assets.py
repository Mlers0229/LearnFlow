from __future__ import annotations

import json
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
PERF = ROOT / "ops" / "performance"


def load_json(path: Path, errors: list[str]) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        errors.append(f"{path.relative_to(ROOT)} is not valid JSON: {exc}")
        return {}
    if not isinstance(value, dict):
        errors.append(f"{path.relative_to(ROOT)} must contain a JSON object")
        return {}
    return value


def validate_assets() -> list[str]:
    errors: list[str] = []
    required = [
        PERF / "workload-model.json",
        PERF / "thresholds.json",
        PERF / "test-data.example.json",
        PERF / "telemetry-queries.json",
        PERF / "experiments.json",
        PERF / "k6" / "learnflow.js",
        ROOT / "docs" / "runbooks" / "capacity-and-load-testing.md",
        ROOT / "docs" / "production" / "capacity-report-template.md",
        ROOT / "docs" / "production" / "sprint-20-evidence.md",
    ]
    for path in required:
        if not path.is_file():
            errors.append(f"missing required performance asset: {path.relative_to(ROOT)}")
    if errors:
        return errors

    model = load_json(PERF / "workload-model.json", errors)
    limits = load_json(PERF / "thresholds.json", errors)
    fixture = load_json(PERF / "test-data.example.json", errors)
    telemetry = load_json(PERF / "telemetry-queries.json", errors)
    experiments = load_json(PERF / "experiments.json", errors)
    if errors:
        return errors

    if model.get("schemaVersion") != 1 or limits.get("schemaVersion") != 1:
        errors.append("performance JSON assets must use schemaVersion 1")

    assumptions = model.get("capacityAssumptions", {})
    profiles = model.get("profiles", {})
    required_profiles = {"smoke", "baseline", "peak", "spike", "soak"}
    if set(profiles) != required_profiles:
        errors.append("workload profiles must be exactly smoke, baseline, peak, spike, and soak")

    baseline_rps = assumptions.get("ordinaryApiPeakRps")
    if baseline_rps != 100 or profiles.get("baseline", {}).get("ordinaryApiRps") != baseline_rps:
        errors.append("baseline profile must preserve the M0 100 QPS assumption")
    peak = profiles.get("peak", {})
    if peak.get("peakFactor") not in (1.5, 2) or peak.get(
        "ordinaryApiRps"
    ) != baseline_rps * peak.get("peakFactor", 0):
        errors.append("peak profile must exercise 1.5x or 2x the M0 ordinary API peak")
    spike_targets = [
        stage.get("target") for stage in profiles.get("spike", {}).get("ordinaryApiStages", [])
    ]
    if max(spike_targets, default=0) != 200:
        errors.append("spike profile must exercise the 2x 200 QPS boundary")
    if profiles.get("soak", {}).get("duration") != "2h":
        errors.append("soak profile must run for two hours")

    ai_peak = assumptions.get("aiPeakConcurrency")
    for name in ("baseline", "peak", "soak"):
        concurrency = profiles.get(name, {}).get("aiConcurrency", {})
        if sum(concurrency.values()) != ai_peak:
            errors.append(f"{name} AI concurrency must total the M0 boundary of {ai_peak}")
    spike_ai = profiles.get("spike", {}).get("aiStages", {})
    spike_total = sum(
        max((stage["target"] for stage in spike_ai.get(name, [])), default=0)
        for name in ("plan", "rag", "tutor")
    )
    if spike_total != ai_peak:
        errors.append("spike AI concurrency must remain bounded at 30")

    expected_limits = {
        ("ordinaryApi", "p95Milliseconds"): 500,
        ("ordinaryApi", "failureRateMaximum"): 0.005,
        ("planTask", "successRateMinimum"): 0.98,
        ("planTask", "p95EndToEndMilliseconds"): 90000,
        ("rag", "successRateMinimum"): 0.98,
        ("rag", "p95Milliseconds"): 15000,
        ("tutor", "successRateMinimum"): 0.98,
        ("tutor", "p95Milliseconds"): 45000,
    }
    for (section, key), expected in expected_limits.items():
        if limits.get(section, {}).get(key) != expected:
            errors.append(f"{section}.{key} must match the existing SLO value {expected}")

    users = fixture.get("users", [])
    if not users or any(not str(user.get("username", "")).startswith("perf_") for user in users):
        errors.append("example performance users must use the perf_ prefix")
    if any(not str(user.get("password", "")).startswith("replace-") for user in users):
        errors.append("example performance data must contain placeholders, never credentials")
    if str(fixture.get("environment", "")).lower() == "production":
        errors.append("example performance data cannot target production")

    query_names = {query.get("name") for query in telemetry.get("queries", [])}
    required_queries = {
        "api_rps",
        "api_p95_seconds",
        "api_5xx_ratio",
        "backend_database_pool_utilization",
        "agent_database_pool_utilization",
        "queue_oldest_pending_seconds",
        "worker_capacity_utilization",
        "ai_model_success_ratio",
        "ai_degradation_ratio",
        "estimated_cost_usd",
    }
    missing_queries = sorted(required_queries - query_names)
    if missing_queries:
        errors.append("missing capacity telemetry queries: " + ", ".join(missing_queries))

    experiment_ids = {item.get("id") for item in experiments.get("experiments", [])}
    required_experiments = {
        "traffic-spike-recovery",
        "model-rate-limit-degradation",
        "worker-restart-backpressure",
        "database-pool-pressure",
    }
    if experiment_ids != required_experiments:
        errors.append("controlled capacity experiments are incomplete")
    if experiments.get("safety", {}).get("productionForbidden") is not True:
        errors.append("controlled capacity experiments must forbid production")

    script = (PERF / "k6" / "learnflow.js").read_text(encoding="utf-8")
    required_script_tokens = [
        "LEARNFLOW_PERF_CONFIRM",
        "staging-capacity-test",
        "LEARNFLOW_PERF_ALLOWED_HOSTS",
        "LEARNFLOW_PERF_ENVIRONMENT",
        "/api/auth/login",
        "/api/plan/recent",
        "/api/plan/tasks",
        "/api/tasks/",
        "/resources",
        "/exercises",
        "dropped_iterations",
        "handleSummary",
    ]
    for token in required_script_tokens:
        if token not in script:
            errors.append(f"k6 scenario is missing required contract token: {token}")

    gitignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
    for ignored in ("ops/performance/test-data.json", "ops/performance/results/"):
        if ignored not in gitignore:
            errors.append(f".gitignore must exclude {ignored}")

    workflow = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
    if "check_performance_assets.py" not in workflow:
        errors.append("CI must execute check_performance_assets.py")

    policy = load_json(ROOT / "ops" / "deployment" / "release-policy.json", errors)
    if "performance-capacity" not in policy.get("requiredGates", []):
        errors.append("release policy must require the performance-capacity gate")
    manifest = load_json(
        ROOT / "ops" / "deployment" / "release-evidence.template.json", errors
    )
    capacity_gate = next(
        (item for item in manifest.get("gates", []) if item.get("id") == "performance-capacity"),
        None,
    )
    if capacity_gate is None or capacity_gate.get("status") != "PENDING":
        errors.append("release evidence template must keep performance-capacity PENDING")

    roadmap = (ROOT / "docs" / "production-readiness-roadmap.md").read_text(encoding="utf-8")
    if "### Sprint 20" not in roadmap:
        errors.append("production roadmap must include Sprint 20")
    return errors


def main() -> None:
    errors = validate_assets()
    if errors:
        for error in errors:
            print(f"- {error}")
        raise SystemExit("Performance asset validation failed")
    print(
        "Performance workload, thresholds, telemetry, release gates, and evidence contracts are valid."
    )


if __name__ == "__main__":
    main()
