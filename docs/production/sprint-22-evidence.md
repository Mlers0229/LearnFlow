# Sprint 22 M5 release evidence and data-governance gate

- Date: 2026-08-24
- Scope: M5-RELEASE-01 engineering foundation
- Status: Engineering complete; production qualification blocked

## Implemented

- Replaced unverified release-candidate booleans with a versioned evidence manifest.
- Bound every required gate to an immutable release, required environment, completion time, freshness window, artifact kind, bundle-local path, and SHA-256 digest.
- Rejected missing, duplicate, pending, stale, future-dated, wrong-environment, wrong-release, unsafe-path, missing-file, and digest-mismatched evidence.
- Added security and data-protection owners plus a minimum release observation window.
- Added data-governance as a required staging release gate.
- Added a versioned data inventory, retention inventory, erasure SLA, fail-closed evidence input, report builder, draft privacy notice, ADR, and Runbook.
- Kept account erasure, export, object-storage erasure, backup limits, telemetry retention, privacy approval, regions, and subprocessors explicitly unverified.
- Added CI checks and regression tests for the release evidence and data-governance contracts.

## Local automated evidence

| Gate | Result |
| --- | --- |
| Release/data-governance operational tests | 17 passed, including valid bundle, missing/stale/wrong environment/wrong release, tampering, unsafe path, and fail-closed governance cases |
| Deployment asset contract | Passed |
| Data-governance asset contract | Passed; unsafe example remains blocked |
| Release candidate template | Rejected as designed; all gates remain PENDING |
| Backend | 112 tests, 0 failures/errors; 3 PostgreSQL/Testcontainers tests skipped because Docker is unavailable |
| Agent Platform | 75 passed; 1 existing Starlette deprecation warning |
| Frontend | Vitest 10/10 and production build passed |
| Ruff | Agent, tools, and tests passed |
| Mypy | 56 source files, 0 issues |
| Frontend ESLint | 0 errors, 24 existing warnings |

## Not closed

- The managed OCI platform, regions, budget, and production data-protection owner are not selected.
- No CI/staging evidence bundle has all required gates in PASS state.
- Account erasure, data export, object-storage erasure, backup expiry behavior, and telemetry retention have not been implemented and verified end to end.
- The privacy notice is a draft; contact, hosting regions, subprocessors, and approval are intentionally empty.
- Docker/Testcontainers, container scans, real staging migration/rollback, capacity, disaster recovery, dashboards, alerts, and data-governance checks still need remote or staging evidence.
- Production release remains fail-closed.

## Related assets

- Evidence policy: ../../ops/deployment/release-evidence-policy.json
- Evidence template: ../../ops/deployment/release-evidence.template.json
- Candidate template: ../../ops/deployment/release-candidate.template.json
- Data-governance policy: ../../ops/compliance/data-governance-policy.json
- Candidate validator: ../../tools/check_release_candidate.py
- Data-governance report builder: ../../tools/build_data_governance_report.py
- Runbook: ../runbooks/data-retention-export-and-erasure.md

