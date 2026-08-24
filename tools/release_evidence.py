from __future__ import annotations

import hashlib
import re
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Any

DIGEST_REFERENCE = re.compile(r"^[^\s@]+@sha256:[0-9a-f]{64}$")
SHA256 = re.compile(r"^[0-9a-f]{64}$")
REVISION_REFERENCE = re.compile(r"^commit-[0-9a-f]{7,64}$")
PLACEHOLDER_RELEASES = {
    "",
    "development",
    "latest",
    "commit-sha-required",
    "previous-commit-sha-required",
}


def parse_timestamp(value: Any, field: str, errors: list[str]) -> datetime | None:
    if not isinstance(value, str) or not value.strip():
        errors.append(f"missing timestamp: {field}")
        return None
    try:
        parsed = datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        errors.append(f"invalid timestamp: {field}")
        return None
    if parsed.tzinfo is None or parsed.utcoffset() is None:
        errors.append(f"timestamp must include a UTC offset: {field}")
        return None
    return parsed.astimezone(timezone.utc)


def resolve_bundle_file(bundle_root: Path, relative_path: Any) -> Path | None:
    if not isinstance(relative_path, str) or not relative_path.strip():
        return None
    candidate = Path(relative_path)
    if candidate.is_absolute():
        return None
    root = bundle_root.resolve()
    resolved = (root / candidate).resolve()
    try:
        resolved.relative_to(root)
    except ValueError:
        return None
    if resolved.is_symlink() or not resolved.is_file():
        return None
    return resolved


def validate_evidence_manifest(
    manifest: dict[str, Any],
    candidate: dict[str, Any],
    release_policy: dict[str, Any],
    evidence_policy: dict[str, Any],
    bundle_root: Path,
    now: datetime,
) -> list[str]:
    errors: list[str] = []
    if manifest.get("schemaVersion") != evidence_policy.get("manifestSchemaVersion"):
        errors.append("unsupported release evidence manifest schema")
    release = str(candidate.get("releaseVersion", ""))
    if manifest.get("releaseVersion") != release:
        errors.append("evidence manifest releaseVersion does not match candidate")
    generated_at = parse_timestamp(manifest.get("generatedAt"), "generatedAt", errors)
    clock_skew = timedelta(
        minutes=int(evidence_policy.get("maximumFutureClockSkewMinutes", 5))
    )
    if generated_at is not None and generated_at > now + clock_skew:
        errors.append("evidence manifest generatedAt is in the future")

    configured = {
        str(item.get("id")): item for item in evidence_policy.get("requiredGates", [])
    }
    required = [str(name) for name in release_policy.get("requiredGates", [])]
    if set(configured) != set(required):
        errors.append("evidence policy gates differ from release policy requiredGates")
    entries = manifest.get("gates")
    if not isinstance(entries, list):
        errors.append("evidence manifest gates must be a list")
        return errors
    by_id: dict[str, dict[str, Any]] = {}
    for entry in entries:
        if not isinstance(entry, dict):
            errors.append("evidence manifest contains a non-object gate")
            continue
        gate_id = str(entry.get("id", ""))
        if not gate_id:
            errors.append("evidence gate id is missing")
        elif gate_id in by_id:
            errors.append(f"duplicate evidence gate: {gate_id}")
        else:
            by_id[gate_id] = entry
    for unexpected in sorted(set(by_id) - set(required)):
        errors.append(f"unexpected evidence gate: {unexpected}")

    for gate_id in required:
        gate = by_id.get(gate_id)
        policy = configured.get(gate_id)
        if gate is None:
            errors.append(f"missing required evidence gate: {gate_id}")
            continue
        if policy is None:
            continue
        if gate.get("status") != "PASS":
            errors.append(f"evidence gate did not pass: {gate_id}")
        if gate.get("releaseVersion") != release:
            errors.append(f"evidence gate release mismatch: {gate_id}")
        if gate.get("environment") != policy.get("environment"):
            errors.append(f"evidence gate environment mismatch: {gate_id}")
        completed_at = parse_timestamp(
            gate.get("completedAt"), f"{gate_id}.completedAt", errors
        )
        if completed_at is not None:
            if completed_at > now + clock_skew:
                errors.append(f"evidence gate completedAt is in the future: {gate_id}")
            max_age = timedelta(hours=int(policy.get("maxAgeHours", 0)))
            if max_age <= timedelta(0) or now - completed_at > max_age:
                errors.append(f"evidence gate is stale: {gate_id}")
            if generated_at is not None and completed_at > generated_at + clock_skew:
                errors.append(f"evidence gate is newer than its manifest: {gate_id}")

        artifacts = gate.get("artifacts")
        if not isinstance(artifacts, list):
            errors.append(f"evidence artifacts must be a list: {gate_id}")
            continue
        artifact_kinds: set[str] = set()
        artifact_paths: set[str] = set()
        for artifact in artifacts:
            if not isinstance(artifact, dict):
                errors.append(f"invalid evidence artifact: {gate_id}")
                continue
            kind = str(artifact.get("kind", ""))
            path_value = str(artifact.get("path", ""))
            digest = str(artifact.get("sha256", "")).lower()
            if not kind:
                errors.append(f"evidence artifact kind is missing: {gate_id}")
            artifact_kinds.add(kind)
            if path_value in artifact_paths:
                errors.append(f"duplicate evidence artifact path: {gate_id}:{path_value}")
            artifact_paths.add(path_value)
            artifact_path = resolve_bundle_file(bundle_root, path_value)
            if artifact_path is None:
                errors.append(f"evidence artifact path is unsafe or missing: {gate_id}")
                continue
            if SHA256.fullmatch(digest) is None:
                errors.append(f"invalid evidence artifact sha256: {gate_id}")
                continue
            actual = hashlib.sha256(artifact_path.read_bytes()).hexdigest()
            if actual != digest:
                errors.append(f"evidence artifact digest mismatch: {gate_id}")
        for kind in policy.get("requiredArtifactKinds", []):
            if kind not in artifact_kinds:
                errors.append(f"missing evidence artifact kind: {gate_id}:{kind}")
    return errors


def validate_candidate(
    candidate: dict[str, Any],
    runtime_contract: dict[str, Any],
    release_policy: dict[str, Any] | None = None,
    evidence_policy: dict[str, Any] | None = None,
    evidence_manifest: dict[str, Any] | None = None,
    bundle_root: Path | None = None,
    now: datetime | None = None,
) -> list[str]:
    errors: list[str] = []
    if candidate.get("schemaVersion") != 2:
        errors.append("unsupported release candidate schema")
    if not str(candidate.get("evidenceManifest", "")).strip():
        errors.append("evidenceManifest is required")
    platform = runtime_contract["platformSelection"]
    if platform["productionDeploymentsBlocked"] or platform["status"] != "selected":
        errors.append("managed OCI platform selection is not closed")
    release = str(candidate.get("releaseVersion", "")).lower()
    previous = str(candidate.get("previousReleaseVersion", "")).lower()
    if release in PLACEHOLDER_RELEASES or REVISION_REFERENCE.fullmatch(release) is None:
        errors.append("releaseVersion must be an immutable source revision")
    if previous in PLACEHOLDER_RELEASES or REVISION_REFERENCE.fullmatch(previous) is None or previous == release:
        errors.append("previousReleaseVersion must identify a different stable release")
    images = candidate.get("images", {})
    for service in ("frontend", "backend", "agent"):
        if DIGEST_REFERENCE.fullmatch(str(images.get(service, ""))) is None:
            errors.append(f"{service} image must use repository@sha256:<64-hex-digest>")

    approvals = candidate.get("approvals", {})
    if approvals.get("platformSelected") is not True:
        errors.append("release approval does not confirm platform selection")
    for owner in (
        "releaseOwner",
        "observationOwner",
        "rollbackOwner",
        "securityOwner",
        "dataProtectionOwner",
    ):
        if not str(approvals.get(owner, "")).strip():
            errors.append(f"missing accountable owner: {owner}")
    observation_minutes = candidate.get("observationWindowMinutes")
    required_observation = int(
        runtime_contract.get("production", {})
        .get("release", {})
        .get("minimumObservationWindowMinutes", 0)
    )
    if not isinstance(observation_minutes, int) or observation_minutes < required_observation:
        errors.append("release observation window is shorter than the runtime contract")

    if (
        release_policy is None
        or evidence_policy is None
        or evidence_manifest is None
        or bundle_root is None
    ):
        errors.append("verified release evidence manifest is required")
    else:
        current = now or datetime.now(timezone.utc)
        if current.tzinfo is None or current.utcoffset() is None:
            raise ValueError("now must include a UTC offset")
        errors.extend(
            validate_evidence_manifest(
                evidence_manifest,
                candidate,
                release_policy,
                evidence_policy,
                bundle_root,
                current.astimezone(timezone.utc),
            )
        )
    return errors
