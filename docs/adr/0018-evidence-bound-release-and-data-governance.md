# ADR-0018: Evidence-bound production release and data-governance gate

- Status: Accepted
- Date: 2026-08-24

## Context

The production preflight previously trusted booleans in a release-candidate JSON file. Those booleans did not prove which immutable release was tested, where a check ran, when it completed, or whether the referenced report changed afterward. The production roadmap also requires retention, deletion, and privacy readiness, while LearnFlow currently has only partial retention jobs and no verified account-erasure or export flow.

## Decision

Each required production gate is represented by one entry in a versioned release-evidence manifest. Every entry must:

- be bound to the candidate source revision;
- use the environment required by the evidence policy;
- be within its freshness window;
- have status PASS;
- include every required artifact kind;
- reference a file inside the release bundle; and
- contain the exact SHA-256 digest of that artifact.

A release candidate uses schema version 2 and references the manifest instead of asserting staging booleans. Missing, duplicate, stale, future-dated, wrong-environment, wrong-release, unsafe-path, missing, or digest-mismatched evidence blocks release.

Data governance is a required staging gate. Its report requires verified retention jobs, account and object-storage erasure, export, backup-limit documentation, privacy approval, subprocessor/region publication, sensitive-logging review, and accountable owners. Current gaps remain explicit and production remains blocked.

## Consequences

- Release evidence can be assembled by CI and staging but cannot be replaced by oral approval.
- A passing report for another revision or environment cannot be reused.
- Evidence bundles must retain their artifact files; copying only the manifest is insufficient.
- Evidence freshness windows require periodic reruns.
- The draft privacy notice and policy inventory are engineering inputs, not legal approval.
- Platform selection, real staging runs, account erasure, export, and privacy approval remain external or future implementation work.

## Rollback

Reverting to schema version 1 would restore unverified booleans and is not an acceptable production rollback. If the checker itself is defective, stop the release, fix it under review, and rerun all affected gates. Existing evidence artifacts remain read-only audit material.
