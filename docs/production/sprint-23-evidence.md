# Sprint 23 data export and account-erasure evidence

- Date: 2026-08-24
- Scope: M5-RELEASE-01 data lifecycle engineering implementation
- Status: Engineering complete; staging qualification blocked

## Implemented

- Added Flyway V18 with a privacy-minimized, durable `EXPORT/ERASURE` request state machine.
- Added bounded attempts, leases, expired-lease recovery, idempotency fingerprints, object artifact metadata, and Backend-only database grants.
- Added versioned JSON exports for account, plans, days, exercises, feedback, uploaded resources, ingestion metadata, learning events, mastery, adaptive decisions, and asynchronous task metadata.
- Excluded password hashes, refresh/reset tokens, internal object keys, raw task payloads, workflow checkpoint state, and content-free operational telemetry.
- Added SHA-256-bound downloads, a 10 MiB default bound, 24-hour artifact expiry, object cleanup, and 365-day privacy-request cleanup.
- Added an erasure/export race guard: pending exports are cancelled and erasure retries until any running export has converged, preventing a post-erasure artifact.
- Added password plus exact-name confirmation, immediate account disablement, refresh-token revocation, admin self-erasure protection, and active-account request rejection.
- Added retry-safe deletion of uploaded source objects and prior export artifacts before ordered transactional database erasure.
- Added privacy-minimized audit pseudonymization and an HMAC subject reference backed by a dedicated production Secret.
- Added personal-settings controls for export progress/download and destructive account confirmation.

## Local automated evidence

| Gate | Result |
| --- | --- |
| Backend | 124 tests, 0 failures/errors; 3 PostgreSQL/Testcontainers tests skipped because Docker is unavailable |
| Sprint 23 Backend tests | Active-account rejection, HTTP identity trust boundary, idempotent submission, admin protection, object-first erasure, export storage, production Secret validation, and V18 contract passed |
| Frontend | Vitest 13/13 and production build passed |
| Frontend ESLint | 0 errors, 24 existing warnings; no new warning introduced |
| Agent | Pytest 75/75 passed; deterministic RAG regression reproduced Recall@5 1.0, MRR 0.916667, NDCG@5 0.876743, empty retrieval 0 |
| Python static analysis | Ruff passed; Mypy passed (51 + 4 source files) |
| Production tools | 17/17 unit tests passed |
| Static contracts | Deployment, Flyway V1-V18, performance, recovery, data-governance, and governance JSON validation passed |

## Not closed

- V18 has not run under remote PostgreSQL/Testcontainers or production-equivalent database roles.
- Export and erasure have not completed against real S3-compatible storage in staging.
- No browser E2E has proved request, download, expiry, account disablement, retry, and final deletion as one evidence bundle.
- Backup expiry/re-erasure and provider-specific log, trace, and metric retention remain unimplemented because the managed platform is not selected.
- Privacy contact, hosting regions, subprocessors, and legal/privacy approval remain empty.
- `accountErasureVerified`, `objectStorageErasureVerified`, `dataExportVerified`, and `retentionJobsVerified` must remain false until staging evidence exists.

## Related assets

- Migration: `backend/src/main/resources/db/migration/V18__add_privacy_request_lifecycle.sql`
- Controller: `backend/src/main/java/com/learnflow/controller/PrivacyController.java`
- Worker: `backend/src/main/java/com/learnflow/service/PrivacyRequestWorker.java`
- Erasure transaction: `backend/src/main/java/com/learnflow/service/PrivacyErasurePersistence.java`
- ADR: `docs/adr/0019-durable-data-export-and-account-erasure.md`
- Runbook: `docs/runbooks/data-retention-export-and-erasure.md`
