# ADR-0016: Versioned production capacity model and fail-closed gate

- Status: Accepted
- Date: 2026-08-23
- Scope: M5-PERF-01

## Context

M0 assumes 100 ordinary API QPS and 30 concurrent AI requests, but these values are not measured capacity. M5 requires repeatable login, plan, RAG and Tutor tests at 1.5 to 2 times peak, saturation evidence and a report that cannot be replaced by manual claims.

## Decision

1. Keep the workload, SLO thresholds, telemetry queries and controlled experiments versioned under `ops/performance`.
2. Use k6 only as a staging load driver. Runtime dependencies are not added to the application images.
3. Authenticate dedicated `perf_` users and use owned fixtures; production targets and committed credentials are rejected.
4. Qualifying evidence combines k6 output with Prometheus observations of API, pools, queue, Worker, model behavior and cost.
5. Smoke proves contracts only. The release gate requires a baseline, peak, spike or soak run against an immutable staging revision.
6. Any missing metric, failed threshold, mutable revision or non-staging environment fails closed.
7. Keep AI concurrency bounded at 30 until a reviewed capacity result changes the M0 assumption.

## Consequences

The suite is portable across the future managed OCI platform and adds no production framework. Full evidence still depends on a real staging environment, sufficient dedicated fixtures, an approved model stub/provider budget and an observation window.

## Rollback

Remove the `performance-capacity` gate only by superseding this ADR with another versioned and fail-closed capacity mechanism. Test artifacts are disposable; application/database schema is unchanged.
