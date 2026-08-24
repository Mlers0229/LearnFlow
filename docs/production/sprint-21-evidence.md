# Sprint 21 M5 disaster recovery engineering evidence

- Date: 2026-08-24
- Scope: M5-DR-01 engineering and release-gate foundation
- Status: Engineering complete; staging execution pending

## Implemented

- Added a versioned 15-minute RPO and 60-minute RTO recovery policy.
- Defined mandatory backup/PITR, accidental deletion, database unavailable, regional failure, model outage and queue/Worker restart scenarios.
- Required staging-only destructive drills, an isolated database restore target, accountable owners, timestamped observations, evidence artifacts and cleanup.
- Added a fail-closed report builder that rejects mutable releases, wrong environments, missing scenarios/checks/artifacts and RPO/RTO breaches.
- Added `disaster-recovery` to the production release policy and defaulted release candidates to `disasterRecoveryPassed=false`.
- Added CI asset validation, unit tests, ADR, drill orchestration and scenario-specific Runbook guidance.

## Local automated evidence

| Gate | Result |
| --- | --- |
| Recovery asset contract | Passed: six required scenarios, staging-only safety, RPO/RTO, release gate and evidence contracts |
| Recovery/performance tool unit tests | 10 passed |
| Backend | 112 tests, 0 failures/errors; 3 PostgreSQL/Testcontainers tests skipped because Docker is unavailable |
| Agent Platform | 75 passed; 1 existing Starlette deprecation warning |
| Frontend | Vitest 10/10 and production build passed |
| Ruff | CI-equivalent Agent/tools scope passed |
| Mypy | 51 source files, 0 issues |
| Deployment, Flyway and performance contracts | Passed; V1-V17 immutable and no new migrations |
| Frontend ESLint | 0 errors, 24 existing warnings |
| git diff --check | Passed |


## Not closed

- The managed OCI platform, primary/alternate regions and budget are not selected.
- No real managed PostgreSQL backup or PITR restore has run.
- No staging accidental deletion, database outage or regional failure drill has run.
- No controlled model-provider outage or all-Worker restart recovery drill has run as recovery-gate evidence.
- Actual RPO/RTO are unknown, so `disasterRecoveryPassed` remains false and M5-DR-01 remains incomplete.

## Related assets

- Recovery policy: `../../ops/recovery/recovery-policy.json`
- Evidence example: `../../ops/recovery/drill-input.example.json`
- Report builder: `../../tools/build_recovery_report.py`
- Drill Runbook: `../runbooks/disaster-recovery-exercise.md`
- Report template: `disaster-recovery-report-template.md`
