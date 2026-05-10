import unittest

from app.agents.rag_agent import FeedbackStats, RagAgent
from app.models.resource import ResourceItem, ResourceQueryContext


class RagAgentIndexTest(unittest.TestCase):
    def setUp(self) -> None:
        self.agent = RagAgent.__new__(RagAgent)
        self.agent._resources = [
            ResourceItem(
                id=1,
                title="Spring Boot REST API 实战",
                url="https://example.com/spring",
                level="beginner",
                domain="java",
                duration_minutes=80,
                tags=["springboot", "rest", "api", "project"],
                source="test",
            ),
            ResourceItem(
                id=2,
                title="英语四级阅读理解技巧",
                url="https://example.com/cet4",
                level="beginner",
                domain="english",
                duration_minutes=45,
                tags=["english", "cet4", "reading"],
                source="test",
            ),
            ResourceItem(
                id=3,
                title="Spring Boot 过期资料",
                url="https://example.com/old",
                level="beginner",
                domain="java",
                duration_minutes=70,
                tags=["springboot", "api"],
                source="test",
            ),
        ]
        self.agent._feedback_stats = {
            1: FeedbackStats(avg_rating=4.8, feedback_count=8, invalid_count=0),
            3: FeedbackStats(avg_rating=2.0, feedback_count=5, invalid_count=3),
        }
        self.agent._index_source = "test"
        self.agent._last_index_error = None
        self.agent._resource_loaded_at = 0.0
        self.agent._index_built_at = 123.0
        self.agent._keyword_index = {}
        self.agent._resource_vectors = {}
        self.agent._resource_terms = {}
        for position, item in enumerate(self.agent._resources):
            terms = self.agent._resource_index_terms(item)
            self.agent._resource_terms[position] = terms
            for term in terms:
                self.agent._keyword_index.setdefault(term, set()).add(position)
            self.agent._resource_vectors[position] = self.agent._embed_terms(terms)

    def test_status_exposes_index_counts(self) -> None:
        status = self.agent.index_status()

        self.assertTrue(status.ready)
        self.assertEqual(status.resource_count, 3)
        self.assertGreater(status.keyword_count, 0)
        self.assertEqual(status.vector_count, 3)
        self.assertEqual(status.feedback_count, 13)

    def test_keyword_and_vector_recall_prefers_matching_domain(self) -> None:
        req = ResourceQueryContext(
            topic="Spring REST 接口项目",
            level="beginner",
            domain="java",
            task_type="project",
            top_k=2,
        )

        core_terms = self.agent._extract_core_terms(req)
        expanded = self.agent._expand_query(req, core_terms)
        hits = self.agent._recall(req, expanded, core_terms)
        ranked = self.agent._rerank(req, hits)

        self.assertGreaterEqual(len(ranked), 2)
        self.assertEqual(ranked[0].id, 1)
        self.assertIn("spring", ranked[0].matched_terms)
        self.assertGreater(ranked[0].score, ranked[1].score)
        self.assertTrue(any("召回" in (ranked[0].reason or "") for _ in [ranked[0]]))

    def test_feedback_penalizes_invalid_resource(self) -> None:
        good = self.agent._feedback_score(1)
        bad = self.agent._feedback_score(3)

        self.assertGreater(good, 0)
        self.assertLess(bad, 0)
        self.assertGreater(good, bad)


if __name__ == "__main__":
    unittest.main()
