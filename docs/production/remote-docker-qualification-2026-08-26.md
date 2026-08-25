# Remote Docker and staging qualification evidence

- Date: 2026-08-26
- Branch: `codex/docker-deploy`
- Staging URL: `http://82.156.207.155:8089`
- Qualification status: PASS
- Scope: Docker/Testcontainers, Flyway V1-V18, pgvector/indexes, database ACL, hardened runtime, four-service smoke, and security scanning

## Safety boundary

The Tencent Cloud host has 2 CPUs, 3.3 GiB memory, no swap, and approximately 1.1 GiB available memory during qualification. A second four-service stack was not started because it could cause an out-of-memory event and affect the active staging service. Mutable concurrency checks ran in isolated GitHub Testcontainers. Tencent Cloud checks against the active database were read-only and did not inspect user content.

## GitHub Docker and security evidence

- CI: https://github.com/Mlers0229/LearnFlow/actions/runs/32870258466
- Security: https://github.com/Mlers0229/LearnFlow/actions/runs/32870258419
- Backend Docker job runs the full Maven suite with Docker available, including the PostgreSQL/Testcontainers migration, runtime-role ACL, and expired-lease single-claim tests.
- Runtime job builds Backend, Agent, and Frontend images, starts all four services with readiness waiting, exercises public/internal health endpoints, and removes its isolated volumes.
- Security workflow covers Backend, Agent, Frontend, observability images, CodeQL, dependency/secret/configuration checks, and SBOM generation.

## Tencent Cloud database qualification

A checksum-verified, read-only SQL assertion produced:

| Check | Result |
| --- | --- |
| Flyway | 18 successful migrations, maximum version V18 |
| pgvector | 0.8.6 |
| Critical indexes | 6/6 present, including HNSW and GIN |
| Runtime roles | migrator, Backend, and Agent roles present |
| Backend ACL | required runtime privileges present |
| Agent positive ACL | resource, audit, and workflow privileges present |
| Agent negative ACL | user/task payload/business writes/mastery access denied as designed |

## Hardened runtime and smoke evidence

| Service | Evidence |
| --- | --- |
| Agent | UID/GID 10001, read-only root filesystem, tmpfs, all capabilities dropped, no-new-privileges, healthy |
| Backend | UID/GID 10001, read-only root filesystem, tmpfs, all capabilities dropped, no-new-privileges, healthy |
| Frontend | nginx non-root user, read-only root filesystem, tmpfs, all capabilities dropped, no-new-privileges, healthy |
| PostgreSQL | `pg_isready` accepting connections and container healthy |
| Gateway boundary | unauthenticated protected API returned HTTP 401 |

The public probes return exact responses:

- `/health/live` -> `{"status":"alive"}`
- `/health/ready` -> `{"status":"ready"}`
- Backend liveness/readiness -> `{"status":"UP"}`
- Agent readiness -> `{"status":"ready"}`

## Finding closed during qualification

The Frontend readiness route previously returned the SPA HTML when `index.html` existed. It was status-correct but did not provide an explicit machine-readable readiness contract. Commit `5aaaa5f` changed the endpoint and CI to assert exact JSON. Commits `f038df4` and `b8bec32` made the build-time Nginx syntax check compatible with the runtime-only `backend` DNS name. The deployed Frontend image is `learnflow-staging-frontend:b8bec32`, and its exact readiness assertion passes.

## Remaining blockers outside this qualification

- Real S3 privacy lifecycle browser E2E and object expiry/deletion evidence.
- Managed PostgreSQL/PITR platform, regions, alert receiver, privacy contact, and release owners.
- Baseline/peak/spike/soak capacity evidence.
- Disaster-recovery exercises and final release evidence bundle.