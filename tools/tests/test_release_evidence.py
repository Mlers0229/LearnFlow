from __future__ import annotations

import copy
import hashlib
import importlib.util
import json
import tempfile
import unittest
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


release = load_module("release_evidence", ROOT / "tools" / "release_evidence.py")


class ReleaseEvidenceTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.bundle = Path(self.temp.name)
        self.runtime = json.loads(
            (ROOT / "ops" / "deployment" / "runtime-contract.json").read_text(
                encoding="utf-8"
            )
        )
        self.runtime["platformSelection"] = {
            "status": "selected",
            "productionDeploymentsBlocked": False,
        }
        self.release_policy = json.loads(
            (ROOT / "ops" / "deployment" / "release-policy.json").read_text(
                encoding="utf-8"
            )
        )
        self.evidence_policy = json.loads(
            (ROOT / "ops" / "deployment" / "release-evidence-policy.json").read_text(
                encoding="utf-8"
            )
        )
        self.candidate = {
            "schemaVersion": 2,
            "evidenceManifest": "release-evidence.json",
            "releaseVersion": "commit-0123456789abcdef",
            "previousReleaseVersion": "commit-fedcba9876543210",
            "images": {
                service: f"ghcr.io/owner/learnflow-{service}@sha256:{'0' * 64}"
                for service in ("frontend", "backend", "agent")
            },
            "observationWindowMinutes": 15,
            "approvals": {
                "platformSelected": True,
                "releaseOwner": "release-owner",
                "observationOwner": "observation-owner",
                "rollbackOwner": "rollback-owner",
                "securityOwner": "security-owner",
                "dataProtectionOwner": "data-owner",
            },
        }
        artifacts = self.bundle / "artifacts"
        artifacts.mkdir()
        gates = []
        for item in self.evidence_policy["requiredGates"]:
            path = artifacts / f"{item['id']}.json"
            path.write_text(json.dumps({"status": "PASS", "gate": item["id"]}))
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            gates.append(
                {
                    "id": item["id"],
                    "status": "PASS",
                    "releaseVersion": self.candidate["releaseVersion"],
                    "environment": item["environment"],
                    "completedAt": "2026-08-24T11:00:00Z",
                    "artifacts": [
                        {
                            "kind": item["requiredArtifactKinds"][0],
                            "path": path.relative_to(self.bundle).as_posix(),
                            "sha256": digest,
                        }
                    ],
                }
            )
        self.manifest = {
            "schemaVersion": 1,
            "releaseVersion": self.candidate["releaseVersion"],
            "generatedAt": "2026-08-24T12:00:00Z",
            "gates": gates,
        }
        self.now = datetime(2026, 8, 24, 12, 0, tzinfo=timezone.utc)

    def tearDown(self) -> None:
        self.temp.cleanup()

    def validate(self, manifest: dict | None = None) -> list[str]:
        return release.validate_candidate(
            self.candidate,
            self.runtime,
            self.release_policy,
            self.evidence_policy,
            manifest or self.manifest,
            self.bundle,
            self.now,
        )

    def test_complete_bundle_passes(self) -> None:
        self.assertEqual(self.validate(), [])

    def test_missing_stale_wrong_environment_and_release_fail(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        manifest["gates"].pop()
        manifest["gates"][0]["completedAt"] = "2026-01-01T00:00:00Z"
        manifest["gates"][1]["environment"] = "production"
        manifest["gates"][2]["releaseVersion"] = "commit-other"
        errors = self.validate(manifest)
        self.assertTrue(any("missing required evidence gate" in item for item in errors))
        self.assertTrue(any("stale" in item for item in errors))
        self.assertTrue(any("environment mismatch" in item for item in errors))
        self.assertTrue(any("release mismatch" in item for item in errors))

    def test_tampered_and_unsafe_artifacts_fail(self) -> None:
        manifest = copy.deepcopy(self.manifest)
        first = self.bundle / manifest["gates"][0]["artifacts"][0]["path"]
        first.write_text("tampered")
        manifest["gates"][1]["artifacts"][0]["path"] = "../outside.json"
        errors = self.validate(manifest)
        self.assertTrue(any("digest mismatch" in item for item in errors))
        self.assertTrue(any("unsafe or missing" in item for item in errors))

    def test_boolean_only_candidate_cannot_pass(self) -> None:
        errors = release.validate_candidate(self.candidate, self.runtime)
        self.assertIn("verified release evidence manifest is required", errors)


if __name__ == "__main__":
    unittest.main()
