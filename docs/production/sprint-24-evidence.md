# Sprint 24 engineering closeout evidence

- Date: 2026-08-24
- Scope: Sprint 20–23 engineering closeout, production startup regression, privacy concurrency, and local qualification
- Status: Local engineering gates and remote Docker/staging qualification passed; privacy, capacity, recovery, and final release evidence remain blocked

## Findings fixed

- Removed the test-only constructor overload that prevented Spring from instantiating `ProductionConfigurationValidator` in a real application context.
- Added a Spring `ApplicationContextRunner` regression test so constructor-injection breakage fails in the Backend suite.
- Made production startup reject the documented `replace_with_...` privacy HMAC placeholder, in addition to development and test placeholders.
- Added a PostgreSQL partial unique index for one active erasure request per user and changed submission to an atomic `ON CONFLICT DO NOTHING` path.
- Added all four privacy lifecycle endpoints to the required OpenAPI compatibility contract.
- Fixed stale Ruff formatting and suppression issues in the observability and OpenAPI asset validators.

## Local automated evidence

| Gate | Result |
| --- | --- |
| Backend | 126 tests, 0 failures/errors; 3 Testcontainers tests skipped because Docker is unavailable |
| Production configuration | Spring application-context construction, strong Privacy Secret enforcement, and placeholder rejection passed |
| Privacy concurrency | Service regression and V18 static contract passed |
| PostgreSQL V18 runtime | Isolated PostgreSQL 18 schema executed V18; 19 constraints were present; duplicate active erasure was rejected; `ON DELETE SET NULL` passed |
| OpenAPI | Real application context started in OpenAPI-only mode; all 31 required paths were present |
| Agent | Pytest 75/75 passed; Ruff passed; Mypy passed for 55 source files |
| RAG regression | Recall@5 1.000000, MRR 0.916667, NDCG@5 0.876743, empty retrieval rate 0 |
| Frontend | Vitest 13/13, ESLint 0 errors with 24 existing warnings, and production build passed |
| Production tools | 17/17 unit tests passed |
| Static contracts | Deployment, Flyway, performance, recovery, data governance, observability, and CI workflow YAML checks passed |

## Environment-qualified evidence

- Docker is not installed on this machine, so Testcontainers and hardened Compose could not run.
- The local PostgreSQL 18 installation does not provide pgvector. A clean V1–V18 migration correctly failed closed at V10 rather than silently skipping Dense Retrieval.
- V18 was therefore executed separately in an isolated real PostgreSQL schema. This proves V18 syntax and its local constraints, but not the full migration chain, Backend/Agent role ACL, or concurrent lease behavior under the production image.
- OpenAPI-only startup disabled Flyway and Hibernate schema validation to isolate application wiring and API contract evidence. It is not migration evidence.
- No real S3-compatible storage, browser E2E, backup expiry/re-erasure, provider telemetry retention, alert delivery, capacity run, disaster-recovery exercise, or privacy approval was available.

## Remote Docker and staging qualification

On 2026-08-26, GitHub CI and Security completed successfully for commit `b8bec32`. The Backend Docker job ran migrations/JPA and Testcontainers, the runtime job built and started the hardened four-service stack and exercised exact health contracts, and Security completed CodeQL plus application/infrastructure image scanning. Tencent Cloud staging independently passed read-only V1-V18, pgvector 0.8.6, HNSW/GIN, runtime-role ACL, container-hardening, gateway-authentication, and four-service health assertions. See [`remote-docker-qualification-2026-08-26.md`](remote-docker-qualification-2026-08-26.md).

## Remaining release blockers

- Staging must prove export, SHA-256 download, expiry, account disablement, retry, object deletion, database erasure, and browser behavior as one evidence bundle.
- A managed platform, regions, PITR policy, budget, subprocessors, privacy contact, alert receiver, and accountable release/rollback owners remain to be selected.
- `capacityPassed`, `disasterRecoveryPassed`, and the data-governance verification fields must remain false until their evidence bundles pass.
