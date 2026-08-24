from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from build_recovery_report import build_report

ROOT = Path(__file__).resolve().parents[1]
RECOVERY = ROOT / "ops" / "recovery"


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
        RECOVERY / "recovery-policy.json",
        RECOVERY / "drill-input.example.json",
        ROOT / "docs" / "adr" / "0017-fail-closed-disaster-recovery-drills.md",
        ROOT / "docs" / "production" / "disaster-recovery-report-template.md",
        ROOT / "docs" / "production" / "sprint-21-evidence.md",
        ROOT / "docs" / "runbooks" / "disaster-recovery-exercise.md",
        ROOT / "docs" / "runbooks" / "database-recovery.md",
        ROOT / "docs" / "runbooks" / "async-task-queue.md",
        ROOT / "docs" / "runbooks" / "ai-slo-incident.md",
    ]
    for path in required:
        if not path.is_file():
            errors.append(f"missing required recovery asset: {path.relative_to(ROOT)}")
    if errors:
        return errors

    policy = load_json(RECOVERY / "recovery-policy.json", errors)
    example = load_json(RECOVERY / "drill-input.example.json", errors)
    runtime = load_json(ROOT / "ops" / "deployment" / "runtime-contract.json", errors)
    release_policy = load_json(
        ROOT / "ops" / "deployment" / "release-policy.json", errors
    )
    manifest = load_json(
        ROOT / "ops" / "deployment" / "release-evidence.template.json", errors
    )
    if errors:
        return errors

    if policy.get("schemaVersion") != 1 or policy.get("gate") != "disaster-recovery":
        errors.append(
            "recovery policy must use schemaVersion 1 and disaster-recovery gate"
        )
    if policy.get("qualifyingEnvironment") != "staging":
        errors.append("recovery drills must qualify only from staging")
    targets = policy.get("targets", {})
    if targets.get("rpoMinutesMaximum") != 15 or targets.get("rtoMinutesMaximum") != 60:
        errors.append(
            "recovery targets must preserve the M0 RPO 15 and RTO 60 minute limits"
        )
    safety = policy.get("safety", {})
    if safety.get("productionDestructiveDrillsForbidden") is not True:
        errors.append("destructive recovery drills must be forbidden in production")
    if safety.get("databaseRestoreTarget") != "isolated-instance":
        errors.append("database recovery must first target an isolated instance")

    expected_scenarios = {
        "automatic-backup-pitr",
        "accidental-deletion",
        "database-unavailable",
        "regional-failure",
        "model-provider-outage",
        "queue-backlog-worker-restart",
    }
    scenarios = policy.get("requiredScenarios", [])
    scenario_ids = {item.get("id") for item in scenarios if isinstance(item, dict)}
    if scenario_ids != expected_scenarios:
        errors.append("recovery scenario matrix is incomplete")
    for item in scenarios:
        if not isinstance(item, dict) or not item.get("requiredChecks"):
            errors.append("every recovery scenario must define required checks")

    if build_report(example, policy).get("status") != "FAIL":
        errors.append("example recovery evidence must fail closed")

    recovery = runtime.get("production", {}).get("recovery", {})
    if recovery.get("gate") != "disaster-recovery":
        errors.append("runtime contract must require the disaster-recovery gate")
    if recovery.get("productionDestructiveDrillsForbidden") is not True:
        errors.append("runtime contract must forbid destructive production drills")
    if set(recovery.get("requiredScenarios", [])) != expected_scenarios:
        errors.append("runtime contract recovery scenarios differ from the policy")
    if "disaster-recovery" not in release_policy.get("requiredGates", []):
        errors.append("release policy must require disaster-recovery evidence")
    recovery_gate = next(
        (item for item in manifest.get("gates", []) if item.get("id") == "disaster-recovery"),
        None,
    )
    if recovery_gate is None or recovery_gate.get("status") != "PENDING":
        errors.append("release evidence template must keep disaster-recovery PENDING")

    workflow = (ROOT / ".github" / "workflows" / "ci.yml").read_text(encoding="utf-8")
    if "check_recovery_assets.py" not in workflow:
        errors.append("CI must execute check_recovery_assets.py")
    gitignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
    for ignored in ("ops/recovery/drill-input.json", "ops/recovery/results/"):
        if ignored not in gitignore:
            errors.append(f".gitignore must exclude {ignored}")
    roadmap = (ROOT / "docs" / "production-readiness-roadmap.md").read_text(
        encoding="utf-8"
    )
    if "### Sprint 21" not in roadmap:
        errors.append("production roadmap must include Sprint 21")
    return errors


def main() -> None:
    errors = validate_assets()
    if errors:
        for error in errors:
            print(f"- {error}")
        raise SystemExit("Recovery asset validation failed")
    print(
        "Recovery scenarios, safety controls, release gate, and evidence contracts are valid."
    )


if __name__ == "__main__":
    main()
