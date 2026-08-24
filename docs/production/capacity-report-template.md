# LearnFlow Capacity Report Template

> Generated reports should be produced by `tools/build_capacity_report.py`. This template lists the additional human-reviewed context required before a capacity result can become release evidence.

## Identity

- Immutable release revision:
- Frontend/Backend/Agent image Digests:
- Environment and region:
- Profile and run ID:
- Start/end time:
- Test owner:
- Observation owner:
- Cleanup owner:
- Model or deterministic stub version:
- Approved cost cap / actual cost:

## Workload

- Dedicated test-user count:
- Ordinary API target/observed RPS:
- Plan/RAG/Tutor concurrency:
- Dataset size: users, plans, plan days, resources, chunks and queue depth:
- Database/Backend/Agent replica and pool configuration:

## Results

Attach the generated JSON and Markdown report. Record P50/P95/P99 where available, failure rate, dropped iterations, task success/latency, database pool utilization, queue age, Worker utilization, model success/degradation and cost.

## Saturation and recovery

- First observed saturation point:
- Bounded degradation behavior:
- Spike recovery time:
- Soak growth in memory, connections and queue:
- Fault experiment outcomes:
- Alerts fired and resolved:
- Scaling threshold and recommended action:

## Decision

- Gate result: PASS / FAIL / INCOMPLETE
- Safe supported capacity:
- Blocking findings:
- Follow-up owner and due date:
- Rollback/cleanup verified by:

Do not mark M5-PERF-01 complete from this template alone. A real staging run, generated report, platform telemetry and cleanup record are required.
