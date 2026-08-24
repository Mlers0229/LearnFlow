from __future__ import annotations

import json
from pathlib import Path

from build_data_governance_report import build_report

ROOT = Path(__file__).resolve().parents[1]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise SystemExit(f"Data governance contract failed: {message}")


def main() -> None:
    compliance = ROOT / "ops" / "compliance"
    policy = json.loads(
        (compliance / "data-governance-policy.json").read_text(encoding="utf-8")
    )
    example = json.loads(
        (compliance / "data-governance-input.example.json").read_text(encoding="utf-8")
    )
    release_policy = json.loads(
        (ROOT / "ops" / "deployment" / "release-policy.json").read_text(
            encoding="utf-8"
        )
    )
    evidence_policy = json.loads(
        (ROOT / "ops" / "deployment" / "release-evidence-policy.json").read_text(
            encoding="utf-8"
        )
    )
    require(policy["schemaVersion"] == 1, "unsupported policy schema")
    require(policy["productionReleaseBlocked"], "production must remain blocked")
    require(policy["privacyNotice"]["approved"] is False, "draft notice must not be approved")
    require(policy["privacyNotice"]["status"] == "draft", "privacy notice status must be draft")
    require(policy["erasureSlaDays"] <= 30, "erasure SLA exceeds 30 days")
    required_classes = {
        "authentication",
        "learning-content",
        "adaptive-learning",
        "ai-operations",
        "administrative-audit",
        "resource-sources",
        "telemetry",
    }
    require(
        {item["id"] for item in policy["dataClasses"]} == required_classes,
        "data inventory is incomplete",
    )
    require(
        any(not item["implemented"] for item in policy["dataClasses"]),
        "unverified erasure capability must not be marked complete",
    )
    require(
        "data-governance" in release_policy["requiredGates"],
        "release policy is missing data-governance",
    )
    gate = next(
        item
        for item in evidence_policy["requiredGates"]
        if item["id"] == "data-governance"
    )
    require(gate["environment"] == "staging", "governance evidence must be staging")
    require(
        gate["requiredArtifactKinds"] == ["data-governance-report"],
        "governance artifact contract differs",
    )
    application = (ROOT / "backend" / "src" / "main" / "resources" / "application.yml").read_text(
        encoding="utf-8"
    )
    retention_contract = "\n".join(
        [
            application,
            (ROOT / ".env.example").read_text(encoding="utf-8"),
            (ROOT / "docs" / "security" / "logging-policy.md").read_text(
                encoding="utf-8"
            ),
        ]
    )
    for item in policy["automatedRetention"]:
        require(item["implemented"], f"retention job is not implemented: {item['id']}")
        require(
            item["configuration"] in retention_contract,
            f"retention configuration is not wired: {item['id']}",
        )
    notice_path = ROOT / policy["privacyNotice"]["path"]
    gitignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
    for ignored in (
        "ops/deployment/release-evidence.json",
        "ops/deployment/artifacts/",
        "ops/compliance/data-governance-input.json",
        "ops/compliance/results/",
    ):
        require(ignored in gitignore, f"generated evidence is not ignored: {ignored}")
    require(notice_path.is_file(), "privacy notice draft is missing")
    report = build_report(example, policy)
    require(report["status"] == "FAIL", "unsafe example must fail closed")
    require(
        any("accountErasureVerified" in error for error in report["errors"]),
        "account erasure gap must block release",
    )
    print(
        "Data governance contract OK: inventory, retention, erasure, privacy and "
        "release blocking"
    )


if __name__ == "__main__":
    main()
