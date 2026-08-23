from pathlib import Path

from app.core.reranker import CrossEncoderReranker
from app.evaluation.rag_dataset import load_rag_evaluation_dataset
from app.evaluation.rag_rerank_runner import run_rerank_evaluation

DATASET_DIR = Path(__file__).parents[1] / "evals" / "rag" / "v1"


class ReverseProvider:
    def predict(self, pairs: list[tuple[str, str]]) -> list[float]:
        return [float(index) for index in range(len(pairs))]


def test_report_compares_baseline_without_enabling_unreviewed_dataset() -> None:
    dataset = load_rag_evaluation_dataset(DATASET_DIR, {"regression"})
    reranker = CrossEncoderReranker("test-cross-encoder", provider=ReverseProvider())

    report = run_rerank_evaluation(dataset, reranker, k=5, candidate_k=10)

    assert report["retrieval"]["reranker_model"] == "test-cross-encoder"
    assert report["retrieval"]["candidate_k"] == 10
    assert report["dataset"]["status"] == "pilot-unreviewed"
    assert report["dataset"]["production_enablement_allowed"] is False
    assert set(report["delta"]) == {"recall_at_k", "mrr", "ndcg_at_k"}
