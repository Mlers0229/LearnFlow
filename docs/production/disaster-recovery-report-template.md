# Disaster recovery report template

Use this template as the human-readable index for the machine-generated report. A completed Markdown document alone does not satisfy the release gate; generate JSON and Markdown with `tools/build_recovery_report.py` from the real staging evidence record.

## Run identity

- Immutable release version:
- Staging environment and region:
- Drill run ID:
- Change window:
- Start and completion timestamps:
- Incident commander:
- Data owner:
- Application owner:
- Independent observer:

## Safety confirmation

- Production was not targeted.
- Destructive staging actions were explicitly approved.
- Database recovery used an isolated target.
- Abort conditions and rollback paths were reviewed.
- Test data and temporary infrastructure cleanup owners were assigned.

## Required scenario results

| Scenario | Status | Observed RPO | Observed RTO | Primary evidence |
| --- | --- | ---: | ---: | --- |
| Automatic backup and PITR | NOT RUN | — | — | — |
| Accidental database deletion | NOT RUN | — | — | — |
| Database unavailable | NOT RUN | n/a | — | — |
| Regional failure | NOT RUN | — | — | — |
| Model provider outage | NOT RUN | n/a | — | — |
| Queue backlog and all Workers restarted | NOT RUN | n/a | — | — |

## Integrity and recovery checks

- Flyway version and checksum validation:
- Critical table counts and constraints:
- Cross-user ownership isolation:
- Backend, Agent and Frontend readiness:
- Login, plan query, asynchronous plan, RAG and Tutor smoke:
- Queue lease recovery and duplicate-write checks:
- Dashboard, alert and trace evidence:

## Cleanup and follow-up

- Temporary instances and fault injection removed:
- Normal readiness and alert state restored:
- Evidence archived in the approved location:
- Remaining data loss or user impact:
- Corrective actions, owners and due dates:

## Generate the release-gate report

```powershell
python tools/build_recovery_report.py `
  --evidence ops/recovery/drill-input.json `
  --output-json ops/recovery/results/recovery-report.json `
  --output-markdown ops/recovery/results/recovery-report.md
```

The example input intentionally fails. Copy it to the ignored `drill-input.json`, replace every placeholder with staging evidence, and never commit credentials, database contents, user payloads or provider access tokens.
