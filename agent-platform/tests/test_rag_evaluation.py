from __future__ import annotations

import json
import math
import shutil
from pathlib import Path
from unittest.mock import Mock

import pytest

from app.agents.rag_agent import RagAgent
from app.evaluation.rag_dataset import DatasetValidationError, load_rag_evaluation_dataset
from app.evaluation.rag_metrics import evaluate_ranking, percentile
from app.evaluation.rag_runner import run_evaluation
from app.models.resource import ResourceQueryContext

DATASET_DIR = Path(__file__).parents[1] / "evals" / "rag" / "v1"


def test_metric_formulas_match_hand_calculation() -> None:
    metrics = evaluate_ranking([2, 3, 1], {1: 3, 2: 2}, k=2)

    expected_dcg = 3.0
    expected_idcg = 7.0 + (3.0 / math.log2(3))
    assert metrics.recall_at_k == pytest.approx(0.5)
    assert metrics.reciprocal_rank == pytest.approx(1.0)
    assert metrics.ndcg_at_k == pytest.approx(expected_dcg / expected_idcg)
    assert metrics.retrieved_count == 2


def test_percentile_uses_nearest_rank() -> None:
    assert percentile([1.0, 2.0, 3.0, 4.0], 50) == 2.0
    assert percentile([1.0, 2.0, 3.0, 4.0], 95) == 4.0
    assert percentile([], 95) == 0.0


def test_versioned_pilot_dataset_is_valid_and_explicitly_unreviewed() -> None:
    dataset = load_rag_evaluation_dataset(DATASET_DIR)

    assert len(dataset.resources) == 11
    assert len(dataset.cases) == 36
    assert dataset.manifest.status == "pilot-unreviewed"
    assert {case.split for case in dataset.cases} == {"dev", "test", "regression"}
    assert {case.provenance.review_status for case in dataset.cases} == {
        "pending_domain_review"
    }
    assert all(len(digest) == 64 for digest in dataset.file_hashes.values())


def test_frozen_snapshot_never_refreshes_from_database() -> None:
    dataset = load_rag_evaluation_dataset(DATASET_DIR, {"regression"})
    agent = RagAgent(resources=dataset.resources, enable_call_logging=False)
    database_loader = Mock(side_effect=AssertionError("database refresh must stay disabled"))
    agent._load_resources_from_db = database_loader
    agent._resource_loaded_at = 0.0

    response = agent.recommend_v2(
        ResourceQueryContext(
            topic="PostgreSQL SQL 教程",
            level="beginner",
            domain="database",
            top_k=5,
        )
    )

    assert response.resources
    database_loader.assert_not_called()


def test_baseline_rankings_and_quality_metrics_are_reproducible() -> None:
    dataset = load_rag_evaluation_dataset(DATASET_DIR, {"regression"})

    first = run_evaluation(dataset, k=5)
    second = run_evaluation(dataset, k=5)

    assert first["metrics"]["recall_at_k"] == second["metrics"]["recall_at_k"]
    assert first["metrics"]["mrr"] == second["metrics"]["mrr"]
    assert first["metrics"]["ndcg_at_k"] == second["metrics"]["ndcg_at_k"]
    assert [query["retrieved_ids"] for query in first["queries"]] == [
        query["retrieved_ids"] for query in second["queries"]
    ]
    assert first["dataset"]["review_status_counts"] == {"pending_domain_review": 6}
    assert first["retrieval"]["model_version"] == "none"
    assert all(len(digest) == 64 for digest in first["code_sha256"].values())


def test_manifest_count_mismatch_is_rejected(tmp_path: Path) -> None:
    copied_dataset = tmp_path / "dataset"
    shutil.copytree(DATASET_DIR, copied_dataset)
    manifest_path = copied_dataset / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["current_query_count"] = 99
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(DatasetValidationError, match="current_query_count"):
        load_rag_evaluation_dataset(copied_dataset)


def test_query_payload_leakage_between_splits_is_rejected(tmp_path: Path) -> None:
    copied_dataset = tmp_path / "dataset"
    shutil.copytree(DATASET_DIR, copied_dataset)
    dev_path = copied_dataset / "queries.dev.jsonl"
    test_path = copied_dataset / "queries.test.jsonl"
    leaked = json.loads(dev_path.read_text(encoding="utf-8").splitlines()[0])
    leaked["query_id"] = "test-leaked-001"
    leaked["split"] = "test"
    with test_path.open("a", encoding="utf-8") as stream:
        stream.write(json.dumps(leaked, ensure_ascii=False) + "\n")

    manifest_path = copied_dataset / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    manifest["current_query_count"] += 1
    manifest["splits"]["test"]["count"] += 1
    manifest_path.write_text(json.dumps(manifest), encoding="utf-8")

    with pytest.raises(DatasetValidationError, match="leaks across"):
        load_rag_evaluation_dataset(copied_dataset)
