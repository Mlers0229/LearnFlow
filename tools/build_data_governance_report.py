from __future__ import annotations

import argparse
import json
import re
from datetime import datetime
from pathlib import Path
from typing import Any

PLACEHOLDER_RELEASES = {"", "development", "latest", "commit-sha-required"}
REVISION_REFERENCE = re.compile(r"^commit-[0-9a-f]{7,64}$")


def build_report(evidence: dict[str, Any], policy: dict[str, Any]) -> dict[str, Any]:
    errors: list[str] = []
    if evidence.get("schemaVersion") != 1:
        errors.append("unsupported data governance evidence schema")
    if evidence.get("environment") != policy.get("qualifyingEnvironment"):
        errors.append("data governance evidence must come from staging")
    release = str(evidence.get("releaseVersion", "")).lower()
    if release in PLACEHOLDER_RELEASES or REVISION_REFERENCE.fullmatch(release) is None:
        errors.append("releaseVersion must identify an immutable release")
    completed_at = evidence.get("completedAt")
    if not isinstance(completed_at, str):
        errors.append("completedAt is required")
    else:
        try:
            parsed = datetime.fromisoformat(completed_at.replace("Z", "+00:00"))
            if parsed.tzinfo is None or parsed.utcoffset() is None:
                errors.append("completedAt must include a UTC offset")
        except ValueError:
            errors.append("completedAt is invalid")

    owners = evidence.get("owners", {})
    for owner in policy.get("requiredOwners", []):
        if not str(owners.get(owner, "")).strip():
            errors.append(f"missing accountable owner: {owner}")
    checks = evidence.get("checks", {})
    for check in policy.get("requiredChecks", []):
        if checks.get(check) is not True:
            errors.append(f"data governance check failed: {check}")
    unexpected = set(checks) - set(policy.get("requiredChecks", []))
    if unexpected:
        errors.append(f"unexpected data governance checks: {','.join(sorted(unexpected))}")
    artifacts = evidence.get("artifacts")
    if not isinstance(artifacts, list) or not artifacts:
        errors.append("data governance evidence artifacts are required")
    elif any(not isinstance(item, str) or not item.strip() for item in artifacts):
        errors.append("data governance artifact references must be non-empty strings")

    return {
        "schemaVersion": 1,
        "gate": "data-governance",
        "status": "PASS" if not errors else "FAIL",
        "environment": evidence.get("environment"),
        "releaseVersion": evidence.get("releaseVersion"),
        "runId": evidence.get("runId"),
        "completedAt": evidence.get("completedAt"),
        "owners": owners,
        "checks": checks,
        "artifacts": artifacts if isinstance(artifacts, list) else [],
        "errors": errors,
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Build a fail-closed LearnFlow data governance report"
    )
    parser.add_argument("evidence", type=Path)
    parser.add_argument("--policy", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    evidence = json.loads(args.evidence.read_text(encoding="utf-8"))
    policy = json.loads(args.policy.read_text(encoding="utf-8"))
    report = build_report(evidence, policy)
    args.output.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    if report["status"] != "PASS":
        for error in report["errors"]:
            print(f"- {error}")
        raise SystemExit("Data governance evidence rejected")
    print(f"Data governance evidence accepted: {report['releaseVersion']}")


if __name__ == "__main__":
    main()
