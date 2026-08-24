from __future__ import annotations

import argparse
import json
from datetime import datetime
from pathlib import Path
from typing import Any

PLACEHOLDER_RELEASES = {
    "",
    "development",
    "latest",
    "unversioned",
    "commit-sha-required",
}


def parse_time(value: Any, field: str, errors: list[str]) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        errors.append(f"missing timestamp: {field}")
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        errors.append(f"invalid ISO-8601 timestamp: {field}")
        return None
    if parsed.tzinfo is None:
        errors.append(f"timestamp must include an offset: {field}")
        return None
    return parsed


def number(value: Any, field: str, errors: list[str]) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float)):
        errors.append(f"missing numeric observation: {field}")
        return None
    result = float(value)
    if result < 0:
        errors.append(f"observation cannot be negative: {field}")
        return None
    return result


def build_report(evidence: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
    errors: list[str] = []
    if evidence.get("schemaVersion") != 1:
        errors.append("drill evidence must use schemaVersion 1")
    environment = str(evidence.get("environment", "")).lower()
    if environment != policy.get("qualifyingEnvironment"):
        errors.append("disaster-recovery evidence must be produced in staging")
    release = str(evidence.get("releaseVersion", ""))
    if release.lower() in PLACEHOLDER_RELEASES:
        errors.append(
            "disaster-recovery evidence requires an immutable source revision"
        )
    if not str(evidence.get("runId", "")).strip():
        errors.append("drill evidence requires a runId")

    started = parse_time(evidence.get("startedAt"), "startedAt", errors)
    completed = parse_time(evidence.get("completedAt"), "completedAt", errors)
    if started is not None and completed is not None and completed < started:
        errors.append("completedAt cannot precede startedAt")

    owners = evidence.get("owners", {})
    for owner in policy.get("requiredOwners", []):
        if not isinstance(owners, dict) or not str(owners.get(owner, "")).strip():
            errors.append(f"missing accountable owner: {owner}")

    approvals = evidence.get("approvals", {})
    for approval in ("changeWindowApproved", "stagingDestructiveDrillApproved"):
        if not isinstance(approvals, dict) or approvals.get(approval) is not True:
            errors.append(f"missing drill approval: {approval}")

    scenarios = evidence.get("scenarios", [])
    if not isinstance(scenarios, list):
        scenarios = []
        errors.append("scenarios must be an array")
    by_id: dict[str, dict[str, Any]] = {}
    for scenario in scenarios:
        if not isinstance(scenario, dict):
            errors.append("each scenario must be an object")
            continue
        scenario_id = str(scenario.get("id", ""))
        if not scenario_id:
            errors.append("scenario is missing id")
        elif scenario_id in by_id:
            errors.append(f"duplicate scenario evidence: {scenario_id}")
        else:
            by_id[scenario_id] = scenario

    targets = policy.get("targets", {})
    rpo_max = float(targets.get("rpoMinutesMaximum", 0))
    rto_max = float(targets.get("rtoMinutesMaximum", 0))
    observations: list[dict[str, Any]] = []
    required_ids: set[str] = set()
    for required in policy.get("requiredScenarios", []):
        scenario_id = str(required.get("id", ""))
        required_ids.add(scenario_id)
        scenario = by_id.get(scenario_id)
        if scenario is None:
            errors.append(f"missing required scenario: {scenario_id}")
            continue
        if scenario.get("status") != "PASS":
            errors.append(f"scenario did not pass: {scenario_id}")

        observed_rto = number(
            scenario.get("observedRtoMinutes"),
            f"{scenario_id}.observedRtoMinutes",
            errors,
        )
        if observed_rto is not None and observed_rto > rto_max:
            errors.append(f"scenario exceeds RTO target: {scenario_id}")
        observed_rpo: float | None = None
        if required.get("measuresRpo") is True:
            observed_rpo = number(
                scenario.get("observedRpoMinutes"),
                f"{scenario_id}.observedRpoMinutes",
                errors,
            )
            if observed_rpo is not None and observed_rpo > rpo_max:
                errors.append(f"scenario exceeds RPO target: {scenario_id}")

        scenario_started = parse_time(
            scenario.get("startedAt"), f"{scenario_id}.startedAt", errors
        )
        scenario_recovered = parse_time(
            scenario.get("recoveredAt"), f"{scenario_id}.recoveredAt", errors
        )
        if scenario_started is not None and scenario_recovered is not None:
            elapsed = (scenario_recovered - scenario_started).total_seconds() / 60
            if elapsed < 0:
                errors.append(f"recoveredAt cannot precede startedAt: {scenario_id}")
            elif observed_rto is not None and abs(elapsed - observed_rto) > 1:
                errors.append(f"reported RTO does not match timestamps: {scenario_id}")

        checks = scenario.get("checks", {})
        for check in required.get("requiredChecks", []):
            if not isinstance(checks, dict) or checks.get(check) is not True:
                errors.append(
                    f"scenario check failed or missing: {scenario_id}.{check}"
                )
        artifacts = scenario.get("artifacts", [])
        if (
            not isinstance(artifacts, list)
            or not artifacts
            or any(not isinstance(item, str) or not item.strip() for item in artifacts)
        ):
            errors.append(
                f"scenario requires non-empty evidence artifacts: {scenario_id}"
            )
        observations.append(
            {
                "id": scenario_id,
                "status": scenario.get("status"),
                "observedRpoMinutes": observed_rpo,
                "observedRtoMinutes": observed_rto,
                "artifacts": artifacts if isinstance(artifacts, list) else [],
            }
        )

    unknown_ids = sorted(set(by_id) - required_ids)
    if unknown_ids:
        errors.append("unknown recovery scenarios: " + ", ".join(unknown_ids))

    cleanup = evidence.get("cleanup", {})
    for check in policy.get("requiredCleanupChecks", []):
        if not isinstance(cleanup, dict) or cleanup.get(check) is not True:
            errors.append(f"cleanup check failed or missing: {check}")

    return {
        "schemaVersion": 1,
        "status": "PASS" if not errors else "FAIL",
        "releaseVersion": release,
        "environment": environment,
        "runId": evidence.get("runId"),
        "startedAt": evidence.get("startedAt"),
        "completedAt": evidence.get("completedAt"),
        "targets": {
            "rpoMinutesMaximum": rpo_max,
            "rtoMinutesMaximum": rto_max,
        },
        "scenarioObservations": observations,
        "errors": errors,
        "conclusion": (
            "The staging drill satisfied every required recovery scenario and target."
            if not errors
            else "The tested revision is not qualified for the disaster-recovery release gate."
        ),
    }


def markdown(report: dict[str, Any]) -> str:
    lines = [
        "# LearnFlow Disaster Recovery Drill Report",
        "",
        f"- Status: **{report['status']}**",
        f"- Release: `{report['releaseVersion']}`",
        f"- Environment: `{report['environment']}`",
        f"- Run ID: `{report.get('runId') or 'missing'}`",
        f"- RPO target: `{report['targets']['rpoMinutesMaximum']} minutes`",
        f"- RTO target: `{report['targets']['rtoMinutesMaximum']} minutes`",
        "",
        "## Scenario observations",
        "",
        "| Scenario | Status | RPO minutes | RTO minutes | Evidence artifacts |",
        "| --- | --- | ---: | ---: | ---: |",
    ]
    for item in report["scenarioObservations"]:
        lines.append(
            f"| {item['id']} | {item['status']} | "
            f"{item['observedRpoMinutes'] if item['observedRpoMinutes'] is not None else 'n/a'} | "
            f"{item['observedRtoMinutes'] if item['observedRtoMinutes'] is not None else 'missing'} | "
            f"{len(item['artifacts'])} |"
        )
    if report["errors"]:
        lines.extend(["", "## Blocking findings", ""])
        lines.extend(f"- {error}" for error in report["errors"])
    lines.extend(["", "## Conclusion", "", report["conclusion"], ""])
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build a fail-closed LearnFlow disaster recovery drill report"
    )
    parser.add_argument("--evidence", required=True, type=Path)
    parser.add_argument(
        "--policy",
        type=Path,
        default=Path(__file__).resolve().parents[1]
        / "ops"
        / "recovery"
        / "recovery-policy.json",
    )
    parser.add_argument("--output-json", required=True, type=Path)
    parser.add_argument("--output-markdown", required=True, type=Path)
    args = parser.parse_args()

    evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
    policy = json.loads(args.policy.read_text(encoding="utf-8"))
    report = build_report(evidence, policy)
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_markdown.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(
        json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    args.output_markdown.write_text(markdown(report), encoding="utf-8")
    if report["status"] != "PASS":
        raise SystemExit("Recovery report failed closed; inspect blocking findings")
    print(f"Recovery report written to {args.output_markdown}")


if __name__ == "__main__":
    main()
