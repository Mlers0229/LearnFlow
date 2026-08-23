from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
DIGEST_REFERENCE = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")
PLACEHOLDER_RELEASES = {"", "development", "latest", "commit-sha-required", "previous-commit-sha-required"}


def validate_candidate(candidate: dict[str, Any], runtime_contract: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    platform = runtime_contract["platformSelection"]
    if platform["productionDeploymentsBlocked"] or platform["status"] != "selected":
        errors.append("managed OCI platform selection is not closed")

    release = str(candidate.get("releaseVersion", "")).lower()
    previous = str(candidate.get("previousReleaseVersion", "")).lower()
    if release in PLACEHOLDER_RELEASES:
        errors.append("releaseVersion must be an immutable source revision")
    if previous in PLACEHOLDER_RELEASES or previous == release:
        errors.append("previousReleaseVersion must identify a different stable release")

    images = candidate.get("images", {})
    for service in ("frontend", "backend", "agent"):
        reference = str(images.get(service, ""))
        if DIGEST_REFERENCE.fullmatch(reference) is None:
            errors.append(f"{service} image must use repository@sha256:<64-hex-digest>")

    evidence = candidate.get("stagingEvidence", {})
    for name in (
        "ciPassed",
        "securityScanPassed",
        "migrationPassed",
        "runtimeSmokePassed",
        "rollbackPassed",
        "dashboardsAndAlertsVerified",
    ):
        if evidence.get(name) is not True:
            errors.append(f"staging evidence is incomplete: {name}")

    approvals = candidate.get("approvals", {})
    if approvals.get("platformSelected") is not True:
        errors.append("release approval does not confirm platform selection")
    for owner in ("releaseOwner", "observationOwner", "rollbackOwner"):
        if not str(approvals.get(owner, "")).strip():
            errors.append(f"missing accountable owner: {owner}")
    return errors


def main() -> None:
    parser = argparse.ArgumentParser(description="Fail-closed LearnFlow production release preflight")
    parser.add_argument("candidate", type=Path)
    args = parser.parse_args()

    candidate = json.loads(args.candidate.read_text(encoding="utf-8"))
    contract = json.loads((ROOT / "ops" / "deployment" / "runtime-contract.json").read_text(encoding="utf-8"))
    errors = validate_candidate(candidate, contract)
    if errors:
        for error in errors:
            print(f"- {error}")
        raise SystemExit("Release candidate rejected")
    print(f"Release candidate accepted: {candidate['releaseVersion']}")


if __name__ == "__main__":
    main()
