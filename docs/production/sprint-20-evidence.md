# Sprint 20 M5 Capacity and performance engineering evidence

- Date: 2026-08-23
- Scope: M5-PERF-01 engineering and release-gate foundation
- Status: Engineering complete; staging execution pending

## Implemented

- Versioned the M0 capacity assumptions and smoke, baseline, peak, spike and two-hour soak profiles.
- Added authenticated k6 journeys for login, plan reads, asynchronous plan generation, RAG and Tutor.
- Enforced staging-only host allowlisting, explicit destructive-load confirmation, dedicated `perf_` users, ignored credentials and immutable run identity.
- Reused the existing API/AI SLOs as executable k6 thresholds, including zero dropped iterations.
- Added Prometheus range capture for API, Backend/Agent database pools, queue, Worker, model success/degradation and estimated cost.
- Added a fail-closed report builder. Smoke, mutable releases, non-staging environments, missing telemetry and any failed threshold cannot satisfy the release gate.
- Added controlled spike, model rate-limit, Worker restart and database-pool pressure experiment contracts.
- Added `performance-capacity` to the release policy and `capacityPassed=false` to the release candidate template.
- Added static asset validation and unit tests to CI.

## Local automated evidence

| Gate | Result |
| --- | --- |
| Performance asset contract | Passed: workload, SLO thresholds, telemetry, experiments, CI and release gates |
| Capacity tool unit tests | 5 passed |
| Ruff / format | 4 Sprint 20 Python files passed and are formatted |
| Mypy | 3 operational source files, 0 issues |
| Deployment contract | Passed, including staging-only capacity evidence and production-load prohibition |
| Flyway contract | V1-V17 immutable; 0 new migrations |
| JSON / CI YAML / k6 JavaScript syntax | Passed |
| git diff --check | Passed; only existing Windows line-ending normalization notices |

## Not closed

- No real staging environment has been selected or provisioned.
- The updated CI performance job has not yet obtained its first remote success evidence.
- Baseline, peak, spike and soak have not run against immutable deployed images.
- No controlled fault experiment or post-load recovery observation has run.
- Database/Agent pool, queue, model quota, memory and cost limits therefore remain unmeasured.
- No generated capacity report exists, so `capacityPassed` remains false and M5-PERF-01 remains incomplete.

## Related assets

- Workload model: `../../ops/performance/workload-model.json`
- Thresholds: `../../ops/performance/thresholds.json`
- k6 suite: `../../ops/performance/k6/learnflow.js`
- Telemetry queries: `../../ops/performance/telemetry-queries.json`
- Controlled experiments: `../../ops/performance/experiments.json`
- Runbook: `../runbooks/capacity-and-load-testing.md`
- Capacity report template: `capacity-report-template.md`
