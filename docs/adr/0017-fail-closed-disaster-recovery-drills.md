# ADR-0017: Fail-closed disaster recovery drills and release gate

- Status: Accepted
- Date: 2026-08-24
- Scope: M5-DR-01

## Context

LearnFlow requires an RPO of at most 15 minutes and an RTO of at most 60 minutes. Existing Runbooks describe database, model and queue incidents, but a written procedure alone does not prove that backups are restorable, tasks survive Worker replacement, fallbacks remain bounded or an alternate region has the required dependencies.

The managed OCI provider, region and budget are not selected. The recovery design must therefore be portable, must not pretend that a local Compose run proves managed-service recovery, and must keep production blocked until real staging evidence exists.

## Decision

1. Keep the versioned recovery target, safety rules, scenario matrix and evidence requirements under `ops/recovery`.
2. Require six scenarios: automatic backup/PITR, accidental deletion, database unavailable, regional failure, model provider outage, and queue backlog with all Workers restarted.
3. Restore database backups to an isolated instance first. Never overwrite the failed database before integrity, ownership and application checks pass.
4. Forbid destructive recovery drills in production. Qualifying drills run only in staging during an approved change window with accountable owners and an independent observer.
5. Measure recovery from timestamped evidence. RPO and RTO values must remain within 15 and 60 minutes and must agree with the scenario timestamps.
6. Require evidence artifacts, scenario-specific checks, service health after recovery and cleanup records. Missing or manually asserted evidence fails closed.
7. Add a `disaster-recovery` production release gate. The template remains false until a report for an immutable staging revision passes every required scenario.
8. Keep provider commands in provider-specific execution records after platform selection; the repository contract remains platform-neutral.

## Consequences

CI can validate the recovery contract and report logic without a cloud account, while actual M5-DR-01 completion still requires managed PostgreSQL, an alternate-region design, controlled model failure and real staging Worker exercises. A successful static check is engineering evidence, not recovery evidence.

## Rollback

The report tools and policy do not alter application data or Schema and can be removed without runtime migration. Removing or weakening the release gate requires a superseding ADR with equivalent measured RPO/RTO and fail-closed evidence.
