from __future__ import annotations

import math
from dataclasses import dataclass
from typing import Mapping, Sequence


@dataclass(frozen=True)
class RankingMetrics:
    recall_at_k: float
    reciprocal_rank: float
    ndcg_at_k: float
    retrieved_count: int


def evaluate_ranking(
    retrieved_ids: Sequence[int],
    judgments: Mapping[int, int],
    k: int,
) -> RankingMetrics:
    if k <= 0:
        raise ValueError("k must be greater than zero")
    relevant_ids = {resource_id for resource_id, grade in judgments.items() if grade > 0}
    if not relevant_ids:
        raise ValueError("at least one positive relevance judgment is required")

    top_ids = list(retrieved_ids[:k])
    recalled = len(relevant_ids.intersection(top_ids)) / len(relevant_ids)

    reciprocal_rank = 0.0
    for rank, resource_id in enumerate(top_ids, start=1):
        if resource_id in relevant_ids:
            reciprocal_rank = 1.0 / rank
            break

    actual_gains = [judgments.get(resource_id, 0) for resource_id in top_ids]
    ideal_gains = sorted(judgments.values(), reverse=True)[:k]
    actual_dcg = _discounted_cumulative_gain(actual_gains)
    ideal_dcg = _discounted_cumulative_gain(ideal_gains)
    ndcg = actual_dcg / ideal_dcg if ideal_dcg > 0 else 0.0

    return RankingMetrics(
        recall_at_k=recalled,
        reciprocal_rank=reciprocal_rank,
        ndcg_at_k=ndcg,
        retrieved_count=len(top_ids),
    )


def _discounted_cumulative_gain(gains: Sequence[int]) -> float:
    return sum(
        ((2**gain) - 1) / math.log2(rank + 1)
        for rank, gain in enumerate(gains, start=1)
    )


def percentile(values: Sequence[float], percent: int) -> float:
    if not values:
        return 0.0
    if percent < 0 or percent > 100:
        raise ValueError("percent must be between 0 and 100")
    ordered = sorted(values)
    rank = max(1, math.ceil((percent / 100) * len(ordered)))
    return ordered[rank - 1]
