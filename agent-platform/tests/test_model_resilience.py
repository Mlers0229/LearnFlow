import asyncio

import pytest

from app.core.resilience import (
    AsyncModelResilience,
    CircuitState,
    ModelBulkheadFull,
    ModelCircuitOpen,
)


def test_bulkhead_rejects_when_model_concurrency_is_saturated() -> None:
    async def scenario() -> None:
        resilience = AsyncModelResilience(
            max_concurrent_calls=1,
            bulkhead_wait_seconds=0.01,
            failure_threshold=3,
            open_seconds=1.0,
            half_open_calls=1,
        )
        entered = asyncio.Event()
        release = asyncio.Event()

        async def blocking_call() -> str:
            entered.set()
            await release.wait()
            return "done"

        first = asyncio.create_task(
            resilience.execute(blocking_call, records_failure=lambda exc: True)
        )
        await entered.wait()
        with pytest.raises(ModelBulkheadFull):
            await resilience.execute(_successful_call, records_failure=lambda exc: True)
        release.set()
        assert await first == "done"

    asyncio.run(scenario())


def test_circuit_opens_and_recovers_with_half_open_probe() -> None:
    async def scenario() -> None:
        resilience = AsyncModelResilience(
            max_concurrent_calls=2,
            bulkhead_wait_seconds=0.01,
            failure_threshold=2,
            open_seconds=0.01,
            half_open_calls=1,
        )

        for _ in range(2):
            with pytest.raises(RuntimeError, match="provider unavailable"):
                await resilience.execute(_failed_call, records_failure=lambda exc: True)

        assert resilience.state is CircuitState.OPEN
        with pytest.raises(ModelCircuitOpen):
            await resilience.execute(_successful_call, records_failure=lambda exc: True)

        await asyncio.sleep(0.02)
        assert (
            await resilience.execute(_successful_call, records_failure=lambda exc: True)
            == "recovered"
        )
        assert resilience.state is CircuitState.CLOSED

    asyncio.run(scenario())


def test_non_transient_failure_does_not_open_circuit() -> None:
    async def scenario() -> None:
        resilience = AsyncModelResilience(
            max_concurrent_calls=1,
            bulkhead_wait_seconds=0.01,
            failure_threshold=1,
            open_seconds=1.0,
            half_open_calls=1,
        )

        with pytest.raises(RuntimeError, match="provider unavailable"):
            await resilience.execute(_failed_call, records_failure=lambda exc: False)
        assert resilience.state is CircuitState.CLOSED

    asyncio.run(scenario())


async def _successful_call() -> str:
    return "recovered"


async def _failed_call() -> str:
    raise RuntimeError("provider unavailable")
