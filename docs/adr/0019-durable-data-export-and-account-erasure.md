# ADR-0019: Durable data export and account erasure

- Status: Accepted
- Date: 2026-08-24

## Context

Sprint 22 made data governance a fail-closed release gate, but LearnFlow had no executable account export or erasure flow. A normal `async_task` cannot safely own erasure because it has a required foreign key to the user that the task must eventually delete. Object storage also cannot participate in the PostgreSQL transaction, so a process crash between object deletion and database deletion must be recoverable.

## Decision

Use a dedicated PostgreSQL `privacy_request` state machine for `EXPORT` and `ERASURE` requests.

- Requests use `PENDING/RUNNING/SUCCEEDED/FAILED`, bounded attempts, leases, expired-lease recovery, and deterministic idempotency fingerprints.
- At most one `PENDING` or `RUNNING` erasure may exist per user; a partial unique index and atomic conflict handling collapse concurrent submissions onto that request.
- The table stores a peppered HMAC subject reference. It never stores usernames, email addresses, passwords, tokens, exported content, or raw object keys in logs and telemetry.
- User exports are schema-versioned JSON. Security credentials, internal workflow checkpoints, raw task payloads, and internal object keys are explicitly excluded.
- Export artifacts use the configured protected resource store under a separate `privacy/exports/` prefix, are SHA-256 verified on download, and expire after 24 hours by default.
- Erasure requires the current password and exact `DELETE <username>` confirmation. The account is immediately disabled and all refresh sessions are revoked.
- Administrator self-erasure is blocked and requires a controlled data-protection-owner process.
- The erasure Worker idempotently deletes every uploaded-resource and prior-export object before executing one ordered PostgreSQL erasure transaction. A storage failure leaves the disabled account and durable request available for retry.
- Minimal retained audit evidence contains the privacy request UUID only. Existing audit rows that directly identify the subject are pseudonymized.
- Directly identifying account and learning data is deleted; completed privacy workflow metadata expires after 365 days by default.

## Consequences

- A process restart or transient S3 failure does not silently lose the erasure obligation.
- Existing access tokens for a disabled or deleted account are rejected by an active-account filter.
- User-uploaded resources are deleted with the account rather than silently reassigned to another owner.
- Export size is bounded. Oversized exports fail closed and require an approved operator-assisted process until streaming archives are introduced.
- Local automated tests prove the engineering contract only. Production qualification still requires V18, real S3, browser, retention, backup, and telemetry verification in staging.

## Rollback

Disable new submissions with `LEARNFLOW_PRIVACY_WORKER_ENABLED=false`, preserve all existing request rows and artifacts, and stop production release. Do not roll back V18 while requests exist. A code rollback may continue reading the expanded schema; pending erasures must be completed by the fixed version or an approved manual procedure.
