from __future__ import annotations

import copy
import importlib.util
import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


reporting = load_module(
    "build_data_governance_report",
    ROOT / "tools" / "build_data_governance_report.py",
)


class DataGovernanceReportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = json.loads(
            (ROOT / "ops" / "compliance" / "data-governance-policy.json").read_text(
                encoding="utf-8"
            )
        )
        self.evidence = {
            "schemaVersion": 1,
            "environment": "staging",
            "releaseVersion": "commit-0123456789abcdef",
            "runId": "governance-20260824-01",
            "completedAt": "2026-08-24T12:00:00Z",
            "owners": {name: f"{name}-owner" for name in self.policy["requiredOwners"]},
            "checks": {name: True for name in self.policy["requiredChecks"]},
            "artifacts": ["artifacts/data-inventory.json", "artifacts/erasure-e2e.json"],
        }

    def test_complete_evidence_passes(self) -> None:
        self.assertEqual(
            reporting.build_report(self.evidence, self.policy)["status"], "PASS"
        )

    def test_missing_owner_checks_and_artifacts_fail(self) -> None:
        evidence = copy.deepcopy(self.evidence)
        evidence["owners"]["privacyReviewer"] = ""
        evidence["checks"]["accountErasureVerified"] = False
        evidence["artifacts"] = []
        report = reporting.build_report(evidence, self.policy)
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("owner" in item for item in report["errors"]))
        self.assertTrue(any("accountErasureVerified" in item for item in report["errors"]))
        self.assertTrue(any("artifacts" in item for item in report["errors"]))

    def test_production_or_mutable_release_fails(self) -> None:
        evidence = copy.deepcopy(self.evidence)
        evidence["environment"] = "production"
        evidence["releaseVersion"] = "latest"
        report = reporting.build_report(evidence, self.policy)
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("staging" in item for item in report["errors"]))
        self.assertTrue(any("immutable" in item for item in report["errors"]))


if __name__ == "__main__":
    unittest.main()
