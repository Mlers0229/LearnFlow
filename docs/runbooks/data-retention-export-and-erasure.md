# Data retention, export, and erasure Runbook

## Current safety state

Production release is blocked. Export and account/object erasure now have an automated engineering path, but V18, real object storage, backup expiry, telemetry retention, final privacy approval, subprocessors, and hosting regions are not verified in staging.

Never change a data-governance check to true based only on code review or this document.


## Engineering flow

- `POST /api/privacy/exports` creates an owned, idempotent export request.
- `GET /api/privacy/requests/{id}` returns privacy-minimized progress for the current owner only.
- `GET /api/privacy/exports/{id}/download` verifies expiry and SHA-256 before returning the JSON attachment.
- `POST /api/privacy/erasure` requires the current password and exact `DELETE <username>` confirmation.
- Erasure disables the account immediately, revokes refresh sessions, deletes object artifacts with retry, then performs ordered transactional database deletion.
- Administrator self-erasure is rejected and must follow an approved operator process.
- Export artifacts expire after `LEARNFLOW_PRIVACY_EXPORT_TTL`; completed request metadata expires after `LEARNFLOW_PRIVACY_REQUEST_RETENTION`.

Do not manually change a `privacy_request` to `SUCCEEDED`. For a failed request, inspect only its bounded `error_code`, repair the dependency, and allow the lease/retry worker to continue. Never copy exported JSON, passwords, object keys, or subject identity into tickets, logs, metrics, traces, or gate evidence.
## Build the gate report

1. Copy ops/compliance/data-governance-input.example.json outside the template path.
2. Bind releaseVersion to the same immutable revision used by the release candidate.
3. Run only in staging with production-equivalent database roles, object storage, telemetry, and retention configuration.
4. Record the data owner, privacy reviewer, and application owner.
5. Attach machine-readable evidence for every required check.
6. Run:

   python tools/build_data_governance_report.py evidence.json --policy ops/compliance/data-governance-policy.json --output artifacts/data-governance-report.json

7. A non-zero exit or FAIL report blocks release. Add the PASS report to the release evidence bundle and record its SHA-256 digest.

## Retention verification

- Seed uniquely tagged records older and newer than each configured cutoff.
- Run the real scheduled cleanup method using staging configuration.
- Prove that expired records are deleted and in-window records remain.
- Confirm logs, traces, metrics, backups, and object-storage lifecycle policies separately.
- Preserve counts and identifiers that do not contain user content.

## Account erasure verification

Use a dedicated staging account. Create a plan, days, exercises, feedback, mastery events, adaptive decisions, tokens, AI tasks, checkpoints, and uploaded resource source. Execute the approved erasure flow, then verify database tables, object storage, search indexes, caches, logs, traces, and scheduled tasks.

Administrative audit records may retain a minimal non-identifying security event only when the approved policy requires it. Never retain password hashes, tokens, prompts, answers, or source content as audit detail.

## Export verification

Request an export for the same staging account. Verify authentication, authorization, completeness, stable schema version, bounded generation time, encrypted transport, expiry, and deletion of the export artifact. A user must never export another user's data.

## Backup limitations

Document the managed PostgreSQL and object-storage backup retention period. Erased records may remain in encrypted backups until expiry, must not be restored into active production, and must be re-erased after an authorized recovery when required by policy.

## Incident and rollback

If erasure deletes another user's data, misses a storage system, or leaks content into evidence, stop the gate, isolate artifacts, notify the security/data owners, and follow the database recovery and security incident Runbooks. Do not restore production broadly to undo a single erasure without incident approval.
