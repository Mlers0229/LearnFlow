import os
import unittest
from unittest.mock import patch

from opentelemetry import trace
from opentelemetry.trace import NonRecordingSpan, SpanContext, TraceFlags, TraceState

from app.observability import (
    current_trace_id,
    normalize_request_id,
    record_model_usage,
    record_rag_result,
    record_validator_result,
)


class _CapturingSpan:
    def __init__(self) -> None:
        self.attributes: dict[str, object] = {}

    def set_attribute(self, key: str, value: object) -> None:
        self.attributes[key] = value


class ObservabilityTest(unittest.TestCase):
    def test_request_id_accepts_only_bounded_metadata(self) -> None:
        self.assertEqual(normalize_request_id("gateway-request_123"), "gateway-request_123")
        self.assertIsNone(normalize_request_id("short"))
        self.assertIsNone(normalize_request_id("unsafe\nAuthorization: secret"))
        self.assertIsNone(normalize_request_id("x" * 65))

    def test_current_trace_id_uses_active_w3c_context(self) -> None:
        span_context = SpanContext(
            trace_id=int("1234567890abcdef1234567890abcdef", 16),
            span_id=int("1234567890abcdef", 16),
            is_remote=True,
            trace_flags=TraceFlags(TraceFlags.SAMPLED),
            trace_state=TraceState(),
        )
        with trace.use_span(NonRecordingSpan(span_context)):
            self.assertEqual(current_trace_id(), "1234567890abcdef1234567890abcdef")

    def test_model_usage_records_counts_and_only_configured_cost(self) -> None:
        span = _CapturingSpan()
        with patch.dict(
            os.environ,
            {
                "LEARNFLOW_LLM_INPUT_USD_PER_1M_TOKENS": "1.5",
                "LEARNFLOW_LLM_OUTPUT_USD_PER_1M_TOKENS": "2.0",
            },
            clear=False,
        ):
            record_model_usage(
                span,  # type: ignore[arg-type]
                "model-safe-name",
                {"prompt_tokens": 100, "completion_tokens": 50, "total_tokens": 150},
            )

        self.assertEqual(span.attributes["gen_ai.usage.input_tokens"], 100)
        self.assertEqual(span.attributes["gen_ai.usage.output_tokens"], 50)
        self.assertEqual(span.attributes["gen_ai.usage.total_tokens"], 150)
        self.assertEqual(span.attributes["gen_ai.usage.cost_estimate_usd"], 0.00025)
        self.assertFalse(any("prompt" in str(value).lower() for value in span.attributes.values()))

    @patch("app.observability.metrics.get_meter")
    def test_rag_result_uses_only_bounded_outcome_label(self, get_meter) -> None:
        counter = get_meter.return_value.create_counter.return_value

        record_rag_result(0)
        record_rag_result(3)

        self.assertEqual(counter.add.call_args_list[0].args, (1, {"outcome": "empty"}))
        self.assertEqual(counter.add.call_args_list[1].args, (1, {"outcome": "success"}))

    @patch("app.observability.metrics.get_meter")
    def test_validator_result_records_counts_without_issue_content(self, get_meter) -> None:
        meter = get_meter.return_value

        record_validator_result(False, 2, 1)

        meter.create_counter.return_value.add.assert_called_once_with(
            1,
            {"outcome": "invalid"},
        )
        histogram_values = [
            call.args[0]
            for call in meter.create_histogram.return_value.record.call_args_list
        ]
        self.assertEqual(histogram_values, [2, 1])


if __name__ == "__main__":
    unittest.main()
