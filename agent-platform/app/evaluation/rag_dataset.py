from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

from pydantic import BaseModel, Field, ValidationError

from app.models.resource import ResourceItem, ResourceQueryContext

SUPPORTED_SCHEMA_VERSION = 1
SUPPORTED_SPLITS = frozenset({"dev", "test", "regression"})


class DatasetValidationError(ValueError):
    """Raised when a RAG evaluation dataset cannot be trusted or reproduced."""


class DatasetFile(BaseModel):
    path: str = Field(min_length=1)
    count: int = Field(ge=0)


class RagDatasetManifest(BaseModel):
    schema_version: int
    dataset_id: str = Field(min_length=1)
    dataset_version: str = Field(min_length=1)
    status: str = Field(min_length=1)
    provenance: str = Field(min_length=1)
    target_query_count: str = Field(min_length=1)
    current_query_count: int = Field(ge=0)
    resources: DatasetFile
    splits: dict[str, DatasetFile]


class RelevanceJudgment(BaseModel):
    resource_id: int = Field(gt=0)
    relevance: int = Field(ge=1, le=3)


class QueryProvenance(BaseModel):
    source_type: str = Field(min_length=1)
    review_status: str = Field(min_length=1)
    note: str | None = None


class RagEvaluationCase(BaseModel):
    query_id: str = Field(min_length=1)
    split: str = Field(min_length=1)
    query: ResourceQueryContext
    judgments: list[RelevanceJudgment] = Field(min_length=1)
    provenance: QueryProvenance


@dataclass(frozen=True)
class RagEvaluationDataset:
    root: Path
    manifest: RagDatasetManifest
    resources: tuple[ResourceItem, ...]
    cases: tuple[RagEvaluationCase, ...]
    file_hashes: dict[str, str]


def _read_json(path: Path) -> dict[str, Any]:
    try:
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise DatasetValidationError(f"Cannot read JSON file {path}: {exc}") from exc
    if not isinstance(payload, dict):
        raise DatasetValidationError(f"Expected a JSON object in {path}")
    return payload


def _read_jsonl(path: Path) -> list[dict[str, Any]]:
    rows: list[dict[str, Any]] = []
    try:
        lines = path.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise DatasetValidationError(f"Cannot read JSONL file {path}: {exc}") from exc
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        try:
            payload = json.loads(line)
        except json.JSONDecodeError as exc:
            raise DatasetValidationError(f"Invalid JSON in {path}:{line_number}: {exc}") from exc
        if not isinstance(payload, dict):
            raise DatasetValidationError(f"Expected a JSON object in {path}:{line_number}")
        rows.append(payload)
    return rows


def _resolve_file(root: Path, relative_path: str) -> Path:
    candidate = (root / relative_path).resolve()
    if not candidate.is_relative_to(root):
        raise DatasetValidationError(f"Dataset file escapes its root: {relative_path}")
    if not candidate.is_file():
        raise DatasetValidationError(f"Dataset file does not exist: {relative_path}")
    return candidate


def _sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(65536), b""):
            digest.update(block)
    return digest.hexdigest()


def _validate_manifest(manifest: RagDatasetManifest) -> None:
    if manifest.schema_version != SUPPORTED_SCHEMA_VERSION:
        raise DatasetValidationError(
            f"Unsupported schema version {manifest.schema_version}; "
            f"expected {SUPPORTED_SCHEMA_VERSION}"
        )
    unknown_splits = set(manifest.splits) - SUPPORTED_SPLITS
    missing_splits = SUPPORTED_SPLITS - set(manifest.splits)
    if unknown_splits or missing_splits:
        raise DatasetValidationError(
            f"Manifest splits must be exactly {sorted(SUPPORTED_SPLITS)}; "
            f"unknown={sorted(unknown_splits)}, missing={sorted(missing_splits)}"
        )
    declared_total = sum(item.count for item in manifest.splits.values())
    if declared_total != manifest.current_query_count:
        raise DatasetValidationError(
            "Manifest current_query_count does not equal the sum of split counts"
        )


def load_rag_evaluation_dataset(
    dataset_dir: Path,
    requested_splits: set[str] | None = None,
) -> RagEvaluationDataset:
    root = dataset_dir.resolve()
    manifest_path = _resolve_file(root, "manifest.json")
    try:
        manifest = RagDatasetManifest.model_validate(_read_json(manifest_path))
    except ValidationError as exc:
        raise DatasetValidationError(f"Invalid manifest {manifest_path}: {exc}") from exc
    _validate_manifest(manifest)

    selected_splits = requested_splits or set(SUPPORTED_SPLITS)
    unknown_requested = selected_splits - SUPPORTED_SPLITS
    if unknown_requested:
        raise DatasetValidationError(f"Unknown requested splits: {sorted(unknown_requested)}")

    resources_path = _resolve_file(root, manifest.resources.path)
    resource_rows = _read_jsonl(resources_path)
    if len(resource_rows) != manifest.resources.count:
        raise DatasetValidationError("Resource row count does not match the manifest")
    try:
        resources = tuple(ResourceItem.model_validate(row) for row in resource_rows)
    except ValidationError as exc:
        raise DatasetValidationError(f"Invalid resource snapshot: {exc}") from exc

    resource_ids = [resource.id for resource in resources]
    if any(resource_id is None for resource_id in resource_ids):
        raise DatasetValidationError("Every resource snapshot row must have an id")
    concrete_resource_ids = {int(resource_id) for resource_id in resource_ids if resource_id is not None}
    if len(concrete_resource_ids) != len(resource_ids):
        raise DatasetValidationError("Resource snapshot ids must be unique")

    cases: list[RagEvaluationCase] = []
    file_hashes = {
        "manifest.json": _sha256(manifest_path),
        manifest.resources.path: _sha256(resources_path),
    }
    seen_query_ids: set[str] = set()
    seen_query_payloads: dict[str, str] = {}
    for split in sorted(selected_splits):
        descriptor = manifest.splits[split]
        split_path = _resolve_file(root, descriptor.path)
        rows = _read_jsonl(split_path)
        if len(rows) != descriptor.count:
            raise DatasetValidationError(f"Split {split} row count does not match the manifest")
        file_hashes[descriptor.path] = _sha256(split_path)
        for row in rows:
            try:
                case = RagEvaluationCase.model_validate(row)
            except ValidationError as exc:
                raise DatasetValidationError(f"Invalid case in split {split}: {exc}") from exc
            if case.split != split:
                raise DatasetValidationError(
                    f"Case {case.query_id} declares split {case.split}, expected {split}"
                )
            if case.query_id in seen_query_ids:
                raise DatasetValidationError(f"Duplicate query id: {case.query_id}")
            seen_query_ids.add(case.query_id)

            normalized_query = json.dumps(
                case.query.model_dump(mode="json"),
                ensure_ascii=False,
                sort_keys=True,
                separators=(",", ":"),
            )
            previous_split = seen_query_payloads.get(normalized_query)
            if previous_split is not None and previous_split != split:
                raise DatasetValidationError(
                    f"Identical query payload leaks across {previous_split} and {split}: "
                    f"{case.query_id}"
                )
            seen_query_payloads[normalized_query] = split

            judged_ids = [judgment.resource_id for judgment in case.judgments]
            if len(set(judged_ids)) != len(judged_ids):
                raise DatasetValidationError(f"Duplicate judgments in query {case.query_id}")
            unknown_resources = set(judged_ids) - concrete_resource_ids
            if unknown_resources:
                raise DatasetValidationError(
                    f"Query {case.query_id} judges unknown resources: {sorted(unknown_resources)}"
                )
            cases.append(case)

    return RagEvaluationDataset(
        root=root,
        manifest=manifest,
        resources=resources,
        cases=tuple(cases),
        file_hashes=file_hashes,
    )
