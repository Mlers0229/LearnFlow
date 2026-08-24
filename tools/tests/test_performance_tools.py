from __future__ import annotations

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


capture = load_module("capture_capacity_snapshot", ROOT / "tools" / "capture_capacity_snapshot.py")
reporting = load_module("build_capacity_report", ROOT / "tools" / "build_capacity_report.py")


class CapacitySnapshotTest(unittest.TestCase):
    def test_reducers_and_boundaries(self) -> None:
        self.assertEqual(capture.reduce_values([1.0, 3.0, 2.0], "max"), 3.0)
        self.assertEqual(capture.reduce_values([1.0, 3.0, 2.0], "min"), 1.0)
        self.assertEqual(capture.reduce_values([1.0, 3.0, 2.0], "last"), 2.0)
        self.assertEqual(capture.reduce_values([1.0, 3.0, 4.5], "delta"), 3.5)
        self.assertEqual(capture.evaluate(0.5, {"maximum": 0.5})[0], True)
        self.assertEqual(capture.evaluate(0.51, {"maximum": 0.5})[0], False)
        self.assertEqual(capture.evaluate(0.98, {"minimum": 0.98})[0], True)

    def test_embedded_prometheus_credentials_are_rejected(self) -> None:
        with self.assertRaises(ValueError):
            capture.safe_source("https://user:secret@metrics.example.test")


class CapacityReportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workload = json.loads(
            (ROOT / "ops" / "performance" / "workload-model.json").read_text(encoding="utf-8")
        )
        self.metrics = {
            "learnflow_operation_duration_ms{operation:api_read}": {
                "values": {"p(95)": 420.0},
                "thresholds": {"p(95)<500": {"ok": True}},
            },
            "learnflow_operation_success{operation:rag}": {
                "values": {"rate": 0.99},
                "thresholds": {"rate>=0.98": {"ok": True}},
            },
            "learnflow_operation_success{operation:tutor}": {
                "values": {"rate": 0.99},
                "thresholds": {"rate>=0.98": {"ok": True}},
            },
            "learnflow_operation_duration_ms{operation:rag}": {
                "values": {"p(95)": 1000.0},
                "thresholds": {"p(95)<15000": {"ok": True}},
            },
            "learnflow_operation_duration_ms{operation:tutor}": {
                "values": {"p(95)": 2000.0},
                "thresholds": {"p(95)<45000": {"ok": True}},
            },
            "learnflow_plan_task_success": {
                "values": {"rate": 0.99},
                "thresholds": {"rate>=0.98": {"ok": True}},
            },
            "learnflow_plan_task_end_to_end_ms": {
                "values": {"p(95)": 5000.0},
                "thresholds": {"p(95)<90000": {"ok": True}},
            },
            "dropped_iterations": {
                "values": {"count": 0.0},
                "thresholds": {"count<=0": {"ok": True}},
            },
        }
        self.telemetry = {
            "passed": True,
            "results": [{"name": "api_p95_seconds", "status": "PASS", "value": 0.42}],
        }

    def artifact(self, profile: str = "peak") -> dict:
        return {
            "profile": profile,
            "runId": "test-run",
            "workload": self.workload["profiles"][profile],
            "summary": {"metrics": self.metrics},
        }

    def test_qualifying_report_passes(self) -> None:
        report = reporting.build_report(
            self.artifact(), self.telemetry, self.workload, "8f119ad", "staging"
        )
        self.assertEqual(report["status"], "PASS")
        self.assertEqual(report["observations"]["ordinaryApiP95Ms"], 420.0)

    def test_threshold_failure_blocks_report(self) -> None:
        self.metrics["dropped_iterations"]["thresholds"]["count<=0"]["ok"] = False
        report = reporting.build_report(
            self.artifact(), self.telemetry, self.workload, "8f119ad", "staging"
        )
        self.assertEqual(report["status"], "FAIL")
        self.assertTrue(any("threshold failed" in error for error in report["errors"]))

    def test_smoke_profile_cannot_qualify(self) -> None:
        report = reporting.build_report(
            self.artifact("smoke"), self.telemetry, self.workload, "8f119ad", "staging"
        )
        self.assertEqual(report["status"], "FAIL")


if __name__ == "__main__":
    unittest.main()
