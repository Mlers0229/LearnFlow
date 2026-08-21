import asyncio
import logging
import os
import threading
import time
from collections.abc import Awaitable, Callable
from enum import Enum
from typing import TypeVar

from opentelemetry import metrics

logger = logging.getLogger(__name__)

T = TypeVar("T")


class CircuitState(str, Enum):
    CLOSED = "closed"
    OPEN = "open"
    HALF_OPEN = "half_open"


class ModelBulkheadFull(RuntimeError):
    pass


class ModelCircuitOpen(RuntimeError):
    pass


def _positive_float(name: str, default: float) -> float:
    try:
        value = float(os.getenv(name, str(default)))
    except ValueError:
        return default
    return value if value > 0 else default


def _positive_int(name: str, default: int) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError:
        return default
    return value if value > 0 else default


class AsyncModelResilience:
    def __init__(
        self,
        *,
        max_concurrent_calls: int,
        bulkhead_wait_seconds: float,
        failure_threshold: int,
        open_seconds: float,
        half_open_calls: int,
    ) -> None:
        self._semaphore = asyncio.Semaphore(max(1, max_concurrent_calls))
        self._bulkhead_wait_seconds = max(0.001, bulkhead_wait_seconds)
        self._failure_threshold = max(1, failure_threshold)
        self._open_seconds = max(0.001, open_seconds)
        self._half_open_calls = max(1, half_open_calls)
        self._state = CircuitState.CLOSED
        self._failure_count = 0
        self._opened_at = 0.0
        self._half_open_in_flight = 0
        self._half_open_successes = 0
        self._state_lock = asyncio.Lock()

    @property
    def state(self) -> CircuitState:
        return self._state

    async def execute(
        self,
        action: Callable[[], Awaitable[T]],
        *,
        records_failure: Callable[[BaseException], bool],
    ) -> T:
        acquired = False
        try:
            try:
                await asyncio.wait_for(
                    self._semaphore.acquire(),
                    timeout=self._bulkhead_wait_seconds,
                )
                acquired = True
            except TimeoutError as exc:
                metrics.get_meter("learnflow.model.resilience").create_counter(
                    "learnflow.ai.resilience.rejections"
                ).add(1, {"reason": "bulkhead_full"})
                logger.warning("Model call rejected reason=bulkhead_full")
                raise ModelBulkheadFull("model concurrency limit reached") from exc

            await self._before_call()
            try:
                result = await action()
            except BaseException as exc:
                if records_failure(exc):
                    await self._record_failure()
                else:
                    await self._record_neutral()
                raise
            await self._record_success()
            return result
        finally:
            if acquired:
                self._semaphore.release()

    async def _before_call(self) -> None:
        async with self._state_lock:
            if self._state is CircuitState.OPEN:
                if time.monotonic() - self._opened_at < self._open_seconds:
                    metrics.get_meter("learnflow.model.resilience").create_counter(
                        "learnflow.ai.resilience.rejections"
                    ).add(1, {"reason": "circuit_open"})
                    logger.warning("Model call rejected reason=circuit_open")
                    raise ModelCircuitOpen("model circuit breaker is open")
                self._transition(CircuitState.HALF_OPEN)
                self._half_open_in_flight = 0
                self._half_open_successes = 0
            if self._state is CircuitState.HALF_OPEN:
                if self._half_open_in_flight >= self._half_open_calls:
                    logger.warning("Model call rejected reason=circuit_half_open_limit")
                    raise ModelCircuitOpen("model circuit breaker is probing recovery")
                self._half_open_in_flight += 1

    async def _record_failure(self) -> None:
        async with self._state_lock:
            if self._state is CircuitState.HALF_OPEN:
                self._half_open_in_flight = max(0, self._half_open_in_flight - 1)
                self._open_circuit()
                return
            self._failure_count += 1
            if self._failure_count >= self._failure_threshold:
                self._open_circuit()

    async def _record_success(self) -> None:
        async with self._state_lock:
            if self._state is CircuitState.HALF_OPEN:
                self._half_open_in_flight = max(0, self._half_open_in_flight - 1)
                self._half_open_successes += 1
                if self._half_open_successes >= self._half_open_calls:
                    self._failure_count = 0
                    self._transition(CircuitState.CLOSED)
                return
            self._failure_count = 0

    async def _record_neutral(self) -> None:
        async with self._state_lock:
            if self._state is CircuitState.HALF_OPEN:
                self._half_open_in_flight = max(0, self._half_open_in_flight - 1)

    def _open_circuit(self) -> None:
        self._opened_at = time.monotonic()
        self._half_open_in_flight = 0
        self._half_open_successes = 0
        self._transition(CircuitState.OPEN)

    def _transition(self, new_state: CircuitState) -> None:
        old_state = self._state
        self._state = new_state
        if old_state is not new_state:
            metrics.get_meter("learnflow.model.resilience").create_counter(
                "learnflow.ai.circuit.transitions"
            ).add(1, {"from": old_state.value, "to": new_state.value})
            logger.warning(
                "Model circuit state changed fromState=%s toState=%s",
                old_state.value,
                new_state.value,
            )


_model_resilience: AsyncModelResilience | None = None
_model_resilience_lock = threading.Lock()
_embedding_resilience: AsyncModelResilience | None = None
_embedding_resilience_lock = threading.Lock()


async def get_model_resilience() -> AsyncModelResilience:
    global _model_resilience
    if _model_resilience is not None:
        return _model_resilience
    with _model_resilience_lock:
        if _model_resilience is None:
            _model_resilience = AsyncModelResilience(
                max_concurrent_calls=_positive_int("LEARNFLOW_LLM_MAX_CONCURRENT_CALLS", 12),
                bulkhead_wait_seconds=_positive_float("LEARNFLOW_LLM_BULKHEAD_WAIT", 0.1),
                failure_threshold=_positive_int("LEARNFLOW_LLM_CIRCUIT_FAILURE_THRESHOLD", 5),
                open_seconds=_positive_float("LEARNFLOW_LLM_CIRCUIT_OPEN_SECONDS", 20.0),
                half_open_calls=_positive_int("LEARNFLOW_LLM_CIRCUIT_HALF_OPEN_CALLS", 1),
            )
        return _model_resilience


async def get_embedding_resilience() -> AsyncModelResilience:
    global _embedding_resilience
    if _embedding_resilience is not None:
        return _embedding_resilience
    with _embedding_resilience_lock:
        if _embedding_resilience is None:
            _embedding_resilience = AsyncModelResilience(
                max_concurrent_calls=_positive_int(
                    "LEARNFLOW_EMBEDDING_MAX_CONCURRENT_CALLS", 6
                ),
                bulkhead_wait_seconds=_positive_float(
                    "LEARNFLOW_EMBEDDING_BULKHEAD_WAIT", 0.1
                ),
                failure_threshold=_positive_int(
                    "LEARNFLOW_EMBEDDING_CIRCUIT_FAILURE_THRESHOLD", 5
                ),
                open_seconds=_positive_float(
                    "LEARNFLOW_EMBEDDING_CIRCUIT_OPEN_SECONDS", 20.0
                ),
                half_open_calls=_positive_int(
                    "LEARNFLOW_EMBEDDING_CIRCUIT_HALF_OPEN_CALLS", 1
                ),
            )
        return _embedding_resilience
