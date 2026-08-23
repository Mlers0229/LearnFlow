from __future__ import annotations

import argparse
import json
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Sequence

from app.agents.rag_agent import INDEX_VERSION, RETRIEVER_VERSION, RagAgent
from app.core.reranker import CrossEncoderReranker
from app.evaluation.rag_dataset import (
    SUPPORTED_SPLITS,
    DatasetValidationError,
    RagEvaluationDataset,
    load_rag_evaluation_dataset,
)
from app.evaluation.rag_metrics import evaluate_ranking, percentile


def run_rerank_evaluation(
    dataset: RagEvaluationDataset,
    reranker: CrossEncoderReranker,
    *,
    k: int = 5,
    candidate_k: int = 10,
) -> dict[str, Any]:
    if k <= 0 or k > 10:
        raise ValueError("k must be between 1 and 10")
    if candidate_k < k or candidate_k > 10:
        raise ValueError("candidate_k must be between k and 10")

    agent = RagAgent(
        resources=dataset.resources,
        index_source=f"eval:{dataset.manifest.dataset_id}:{dataset.manifest.dataset_version}",
        enable_call_logging=False,
    )
    query_results: list[dict[str, Any]] = []
    baseline_values: dict[str, list[float]] = {"recall_at_k": [], "mrr": [], "ndcg_at_k": []}
    reranked_values: dict[str, list[float]] = {"recall_at_k": [], "mrr": [], "ndcg_at_k": []}
    latencies_ms: list[float] = []

    for case in dataset.cases:
        request = case.query.model_copy(update={"top_k": candidate_k})
        started = time.perf_counter()
        response = agent.recommend_v2(request)
        documents = [agent._rerank_document(item) for item in response.resources]
        query_text = agent._dense_query_text(request, response.expanded_queries)[:4_000]
        scores = reranker.score_sync(query_text, documents)
        ranked = sorted(
            zip(response.resources, scores, range(len(response.resources)), strict=True),
            key=lambda value: (-value[1], value[2]),
        )
        baseline_ids = [int(item.id) for item in response.resources[:k] if item.id is not None]
        reranked_ids = [int(item.id) for item, _, _ in ranked[:k] if item.id is not None]
        judgments = {value.resource_id: value.relevance for value in case.judgments}
        baseline = evaluate_ranking(baseline_ids, judgments, k)
        reranked = evaluate_ranking(reranked_ids, judgments, k)
        baseline_values["recall_at_k"].append(baseline.recall_at_k)
        baseline_values["mrr"].append(baseline.reciprocal_rank)
        baseline_values["ndcg_at_k"].append(baseline.ndcg_at_k)
        reranked_values["recall_at_k"].append(reranked.recall_at_k)
        reranked_values["mrr"].append(reranked.reciprocal_rank)
        reranked_values["ndcg_at_k"].append(reranked.ndcg_at_k)
        latency_ms = (time.perf_counter() - started) * 1000
        latencies_ms.append(latency_ms)
        query_results.append(
            {
                "query_id": case.query_id,
                "split": case.split,
                "review_status": case.provenance.review_status,
                "baseline_retrieved_ids": baseline_ids,
                "reranked_ids": reranked_ids,
                "rerank_scores": [round(score, 6) for score in scores],
                "latency_ms": round(latency_ms, 3),
            }
        )

    count = len(dataset.cases)
    if count == 0:
        raise DatasetValidationError("No evaluation cases were selected")
    baseline_metrics = _mean_metrics(baseline_values, count)
    reranked_metrics = _mean_metrics(reranked_values, count)
    return {
        "report_schema_version": 1,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "dataset": {
            "id": dataset.manifest.dataset_id,
            "version": dataset.manifest.dataset_version,
            "status": dataset.manifest.status,
            "selected_query_count": count,
            "production_enablement_allowed": dataset.manifest.status != "pilot-unreviewed",
            "file_sha256": dict(sorted(dataset.file_hashes.items())),
        },
        "retrieval": {
            "retriever_version": RETRIEVER_VERSION,
            "index_version": INDEX_VERSION,
            "reranker_model": reranker.model_name,
            "k": k,
            "candidate_k": candidate_k,
        },
        "baseline_metrics": baseline_metrics,
        "reranked_metrics": reranked_metrics,
        "delta": {
            key: round(reranked_metrics[key] - baseline_metrics[key], 6)
            for key in baseline_metrics
        },
        "latency_ms": {
            "p50": round(percentile(latencies_ms, 50), 3),
            "p95": round(percentile(latencies_ms, 95), 3),
            "max": round(max(latencies_ms), 3),
        },
        "queries": query_results,
    }


def _mean_metrics(values: dict[str, list[float]], count: int) -> dict[str, float]:
    return {key: round(sum(metric_values) / count, 6) for key, metric_values in values.items()}


def _build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Compare a Cross Encoder with the frozen RAG baseline")
    parser.add_argument("--dataset-dir", type=Path, required=True)
    parser.add_argument("--model", required=True)
    parser.add_argument("--splits", nargs="+", choices=sorted(SUPPORTED_SPLITS), default=sorted(SUPPORTED_SPLITS))
    parser.add_argument("--k", type=int, default=5)
    parser.add_argument("--candidate-k", type=int, default=10)
    parser.add_argument("--output", type=Path)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _build_parser().parse_args(argv)
    try:
        dataset = load_rag_evaluation_dataset(args.dataset_dir, set(args.splits))
        report = run_rerank_evaluation(
            dataset,
            CrossEncoderReranker(args.model),
            k=args.k,
            candidate_k=args.candidate_k,
        )
        rendered = json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n"
        if args.output:
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(rendered, encoding="utf-8")
        else:
            print(rendered, end="")
    except (DatasetValidationError, RuntimeError, ValueError) as exc:
        print(f"RAG rerank evaluation failed: {exc}", file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
