# Capacity and load testing

This Runbook governs M5-PERF-01. It is staging-only: never point the suite at production, a shared developer database, or real user accounts.

## Safety contract

- Use an immutable application revision and immutable container image Digests.
- Create dedicated users whose names start with `perf_`; every user must own its supplied plan and day fixture.
- Store the real fixture in ignored `ops/performance/test-data.json` or mount it from a Secret store. Never commit passwords or access tokens.
- Use a controlled model stub or a provider sandbox with an approved hard cost cap. A real paid model comparison is a separate, explicitly approved run.
- Non-local and non-smoke runs require the exact confirmation `LEARNFLOW_PERF_CONFIRM=staging-capacity-test` and an exact hostname allowlist.
- Announce the change window, test owner, observation owner, cost cap, abort authority, and cleanup owner before starting.
- Stop immediately if readiness fails, unexpected 5xx reaches 1%, database pool utilization remains above 85%, queue age exceeds 300 seconds, or the approved cost cap is approached.

## Versioned profiles

| Profile | Purpose | Ordinary API | AI boundary |
| --- | --- | ---: | ---: |
| smoke | Sequential contract validation | 1 VU | sequential |
| baseline | M0 target peak | 100 RPS | 30 concurrent |
| peak | release acceptance at 1.5x | 150 RPS | 30 concurrent |
| spike | burst and recovery | 100 → 200 → 100 RPS | up to 30 concurrent |
| soak | bounded-growth observation | 100 RPS for 2h | 30 concurrent |

The exact model is `ops/performance/workload-model.json`; thresholds are inherited from `docs/production/slo.md`.

## Prepare staging

1. Deploy the immutable revision and verify all three readiness probes.
2. Seed at least 30 dedicated students with owned plan/day fixtures for baseline, peak, spike, and soak.
3. Copy `test-data.example.json` to the ignored `test-data.json`, replace placeholders from the Secret store, and verify no production identifier is present.
4. Reset dashboards, confirm Prometheus freshness, record the starting queue depth and estimated cost, and verify rollback ownership.
5. Select a reviewed k6 image by Digest. Do not use a mutable `latest` tag.

Example environment:

```text
LEARNFLOW_BASE_URL=https://staging.example.test
LEARNFLOW_PERF_ALLOWED_HOSTS=staging.example.test
LEARNFLOW_PERF_ENVIRONMENT=staging
LEARNFLOW_PERF_CONFIRM=staging-capacity-test
LEARNFLOW_PERF_PROFILE=peak
LEARNFLOW_PERF_RUN_ID=20260823-8f119ad-peak
LEARNFLOW_RELEASE_VERSION=8f119ad596d24b8ddc4d8451a0ebe57f140b3c23
```

Mount `ops/performance` read-only, mount only `ops/performance/results` writable, set `LEARNFLOW_PERF_TEST_DATA=/work/test-data.json`, set `LEARNFLOW_PERF_SUMMARY=/results/k6-summary.json`, and run `/work/k6/learnflow.js` with the reviewed k6 image Digest.

## Execution order

1. Run smoke and confirm every API contract.
2. Run baseline; do not continue if any k6 or telemetry threshold fails.
3. Run peak and then spike. Observe recovery for at least five minutes after generated load stops.
4. Run the controlled experiments from `experiments.json` one at a time. Restore healthy dependencies between experiments.
5. Run soak only after cost and cleanup estimates are approved.
6. Do not tune thresholds during a run. Commit a reviewed model change first, then repeat the complete qualifying profile.

## Capture telemetry

Capture the same time window from Prometheus:

```powershell
python tools/capture_capacity_snapshot.py --prometheus-url https://metrics.example.test --start 2026-08-23T12:00:00Z --end 2026-08-23T12:20:00Z --step 15s --output ops/performance/results/telemetry.json
```

If authentication is required, inject `PROMETHEUS_BEARER_TOKEN` through the environment. The tool never writes the token or query URL to the artifact.

Build the fail-closed report:

```powershell
python tools/build_capacity_report.py --k6-summary ops/performance/results/k6-summary.json --telemetry ops/performance/results/telemetry.json --release 8f119ad596d24b8ddc4d8451a0ebe57f140b3c23 --environment staging --output-json ops/performance/results/capacity-report.json --output-markdown ops/performance/results/capacity-report.md
```

Only a `PASS` report from baseline/peak/spike/soak may satisfy `capacityPassed`; smoke output cannot qualify.

## Cleanup and evidence

- Cancel unfinished `PLAN_GENERATION` tasks, remove only the dedicated performance users' generated plans, and verify no lease remains expired.
- Re-enable the healthy model endpoint and restore ordinary pool/worker configuration.
- Confirm readiness, queue age, database connections, AI degradation, cost, and alerts return to baseline.
- Attach k6 summary, telemetry snapshot, capacity report, deployment revision, model/stub version, intervention timeline, dashboard links, and cleanup record.
- Record capacity, first saturation point, scaling threshold, bottleneck, recommended replica/pool/worker settings, actual spend, and every follow-up owner.

A report is evidence for one exact revision and environment only. It must not be copied to a different release candidate.
