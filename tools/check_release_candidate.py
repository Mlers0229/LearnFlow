from __future__ import annotations

import argparse
import json
from pathlib import Path

from release_evidence import resolve_bundle_file, validate_candidate

ROOT = Path(__file__).resolve().parents[1]


def main() -> None:
    parser = argparse.ArgumentParser(description="Fail-closed LearnFlow production release preflight")
    parser.add_argument("candidate", type=Path)
    args = parser.parse_args()

    candidate_path = args.candidate.resolve()
    candidate = json.loads(candidate_path.read_text(encoding="utf-8"))
    contract = json.loads(
        (ROOT / "ops" / "deployment" / "runtime-contract.json").read_text(
            encoding="utf-8"
        )
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
    manifest_path = resolve_bundle_file(
        candidate_path.parent, candidate.get("evidenceManifest")
    )
    if manifest_path is None:
        raise SystemExit(
            "Release candidate rejected: evidenceManifest must reference a file "
            "inside the release bundle"
        )
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    errors = validate_candidate(
        candidate,
        contract,
        release_policy,
        evidence_policy,
        manifest,
        candidate_path.parent,
    )
    if errors:
        for error in errors:
            print(f"- {error}")
        raise SystemExit("Release candidate rejected")
    print(f"Release candidate accepted: {candidate['releaseVersion']}")


if __name__ == "__main__":
    main()
