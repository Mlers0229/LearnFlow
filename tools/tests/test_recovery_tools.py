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
    "build_recovery_report", ROOT / "tools" / "build_recovery_report.py"
)


class RecoveryReportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.policy = json.loads(
            (ROOT / "ops" / "recovery" / "recovery-policy.json").read_text(
                encoding="utf-8"
            )
        )
        scenarios = []
        for item in self.policy["requiredScenarios"]:
            scenario = {
                "id": item["id"],
                "status": "PASS",
                "startedAt": "2026-08-24T00:00:00Z",
                "recoveredAt": "2026-08-24T00:30:00Z",
                "observedRtoMinutes": 30,
                "checks": {name: True for name in item["requiredChecks"]},
                "artifacts": [f"artifacts/{item['id']}.json"],
            }
            if item["measuresRpo"]:
                scenario["observedRpoMinutes"] = 10
            scenarios.append(scenario)
        self.evidence = {
            "schemaVersion": 1,
            "environment": "staging",
            "releaseVersion": "commit-0123456789abcdef",
            "runId": "drill-20260824-01",
            "startedAt": "2026-08-24T00:00:00Z",
            "completedAt": "2026-08-24T01:00:00Z",
            "owners": {
                "incidentCommander": "incident-owner",
                "dataOwner": "data-owner",
                "applicationOwner": "application-owner",
                "observer": "independent-observer",
            },
            "approvals": {
                "changeWindowApproved": True,
                "stagingDestructiveDrillApproved": True,
            },
            "scenarios": scenarios,
            "cleanup": {
                "temporaryResourcesRemoved": True,
                "restoredServicesHealthy": True,
                "evidenceArchived": True,
            },
        }

    def test_qualifying_drill_passes(self) -> None:
        report = reporting.build_report(self.evidence, self.policy)
        self.assertEqual(report["status"], "PASS")
        self.assertEqual(len(report["scenarioObservations"]), 6)

    def test_non_staging_or_mutable_release_fails(self) -> None:
        self.evidence["environment"] = "production"
        self.evidence["releaseVersion"] = "latest"
        report = reporting.build_report(self.evidence, self.policy)
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("staging" in error for error in report["errors"]))
        self.assertTrue(any("immutable" in error for error in report["errors"]))

    def test_missing_scenario_and_check_fail(self) -> None:
        evidence = copy.deepcopy(self.evidence)
        evidence["scenarios"].pop()
        evidence["scenarios"][0]["checks"]["backupScheduleVerified"] = False
        report = reporting.build_report(evidence, self.policy)
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(
            any("missing required scenario" in error for error in report["errors"])
        )
        self.assertTrue(
            any("scenario check failed" in error for error in report["errors"])
        )

    def test_rpo_and_rto_target_breaches_fail(self) -> None:
        evidence = copy.deepcopy(self.evidence)
        evidence["scenarios"][0]["observedRpoMinutes"] = 16
        evidence["scenarios"][1]["observedRtoMinutes"] = 61
        report = reporting.build_report(evidence, self.policy)
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("exceeds RPO" in error for error in report["errors"]))
        self.assertTrue(any("exceeds RTO" in error for error in report["errors"]))

    def test_missing_owners_approval_cleanup_and_artifacts_fail(self) -> None:
        evidence = copy.deepcopy(self.evidence)
        evidence["owners"]["observer"] = ""
        evidence["approvals"]["changeWindowApproved"] = False
        evidence["cleanup"]["evidenceArchived"] = False
        evidence["scenarios"][0]["artifacts"] = []
        report = reporting.build_report(evidence, self.policy)
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("owner" in error for error in report["errors"]))
        self.assertTrue(any("approval" in error for error in report["errors"]))
        self.assertTrue(any("cleanup" in error for error in report["errors"]))
        self.assertTrue(any("artifacts" in error for error in report["errors"]))


if __name__ == "__main__":
    unittest.main()
