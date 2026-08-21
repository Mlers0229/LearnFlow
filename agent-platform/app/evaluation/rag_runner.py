from __future__ import annotations

import argparse
import hashlib
import json
import platform
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence

from app.agents.rag_agent import INDEX_VERSION, RETRIEVER_VERSION, RagAgent
from app.evaluation.rag_dataset import (
    SUPPORTED_SPLITS,
    DatasetValidationError,
    RagEvaluationDataset,
    load_rag_evaluation_dataset,
)
from app.evaluation.rag_metrics import evaluate_ranking, percentile


def _source_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def run_evaluation(dataset: RagEvaluationDataset, k: int = 5) -> dict[str, Any]:
    if k <= 0 or k > 10:
        raise ValueError("k must be between 1 and 10")
    agent = RagAgent(
        resources=dataset.resources,
        index_source=f"eval:{dataset.manifest.dataset_id}:{dataset.manifest.dataset_version}",
        enable_call_logging=False,
    )

    query_results: list[dict[str, Any]] = []
    recalls: list[float] = []
    reciprocal_ranks: list[float] = []
    ndcgs: list[float] = []
    latencies_ms: list[float] = []
    empty_count = 0
    review_status_counts: dict[str, int] = {}

    for case in dataset.cases:
        request = case.query.model_copy(update={"top_k": k})
        started_at = time.perf_counter()
        response = agent.recommend_v2(request)
        latency_ms = (time.perf_counter() - started_at) * 1000
        retrieved_ids = [int(item.id) for item in response.resources if item.id is not None]
        judgments = {
            judgment.resource_id: judgment.relevance for judgment in case.judgments
        }
        metrics = evaluate_ranking(retrieved_ids, judgments, k)

        recalls.append(metrics.recall_at_k)
        reciprocal_ranks.append(metrics.reciprocal_rank)
        ndcgs.append(metrics.ndcg_at_k)
        latencies_ms.append(latency_ms)
        if not retrieved_ids:
            empty_count += 1
        review_status = case.provenance.review_status
        review_status_counts[review_status] = review_status_counts.get(review_status, 0) + 1
        query_results.append(
            {
                "query_id": case.query_id,
                "split": case.split,
                "review_status": review_status,
                "retrieved_ids": retrieved_ids,
                "recall_at_k": round(metrics.recall_at_k, 6),
                "reciprocal_rank": round(metrics.reciprocal_rank, 6),
                "ndcg_at_k": round(metrics.ndcg_at_k, 6),
                "latency_ms": round(latency_ms, 3),
            }
        )

    query_count = len(dataset.cases)
    if query_count == 0:
        raise DatasetValidationError("No evaluation cases were selected")

    return {
        "report_schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "dataset": {
            "id": dataset.manifest.dataset_id,
            "version": dataset.manifest.dataset_version,
            "status": dataset.manifest.status,
            "selected_query_count": query_count,
            "review_status_counts": dict(sorted(review_status_counts.items())),
            "file_sha256": dict(sorted(dataset.file_hashes.items())),
        },
        "retrieval": {
            "retriever_version": RETRIEVER_VERSION,
            "index_version": INDEX_VERSION,
            "model_version": "none",
            "resource_count": len(dataset.resources),
            "feedback_snapshot_count": 0,
            "k": k,
        },
        "code_sha256": {
            "rag_agent.py": _source_sha256(Path(__file__).parents[1] / "agents" / "rag_agent.py"),
            "rag_dataset.py": _source_sha256(Path(__file__).with_name("rag_dataset.py")),
            "rag_metrics.py": _source_sha256(Path(__file__).with_name("rag_metrics.py")),
            "rag_runner.py": _source_sha256(Path(__file__)),
        },
        "environment": {
            "python": platform.python_version(),
            "platform": platform.platform(),
        },
        "metrics": {
            "recall_at_k": round(sum(recalls) / query_count, 6),
            "mrr": round(sum(reciprocal_ranks) / query_count, 6),
            "ndcg_at_k": round(sum(ndcgs) / query_count, 6),
            "empty_retrieval_rate": round(empty_count / query_count, 6),
            "latency_ms": {
                "p50": round(percentile(latencies_ms, 50), 3),
                "p95": round(percentile(latencies_ms, 95), 3),
                "max": round(max(latencies_ms), 3),
            },
        },
        "queries": query_results,
    }


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Run the reproducible LearnFlow RAG baseline")
    parser.add_argument("--dataset-dir", type=Path, required=True)
    parser.add_argument(
        "--splits",
        nargs="+",
        choices=sorted(SUPPORTED_SPLITS),
        default=sorted(SUPPORTED_SPLITS),
    )
    parser.add_argument("--k", type=int, default=5)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--min-recall-at-k", type=float)
    parser.add_argument("--min-ndcg-at-k", type=float)
    return parser


def _write_report(report: dict[str, Any], output: Path | None) -> None:
    rendered = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
    if output is None:
        print(rendered, end="")
        return
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(rendered, encoding="utf-8")


def _threshold_failed(report: dict[str, Any], recall: float | None, ndcg: float | None) -> bool:
    metrics = report["metrics"]
    if recall is not None and metrics["recall_at_k"] < recall:
        return True
    if ndcg is not None and metrics["ndcg_at_k"] < ndcg:
        return True
    return False


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    try:
        dataset = load_rag_evaluation_dataset(args.dataset_dir, set(args.splits))
        report = run_evaluation(dataset, args.k)
        _write_report(report, args.output)
    except (DatasetValidationError, ValueError) as exc:
        print(f"RAG evaluation failed: {exc}", file=sys.stderr)
        return 2
    if _threshold_failed(report, args.min_recall_at_k, args.min_ndcg_at_k):
        print("RAG evaluation did not satisfy the configured thresholds", file=sys.stderr)
        return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
