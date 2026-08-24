# LearnFlow privacy notice draft

Status: DRAFT - NOT APPROVED FOR PRODUCTION
Updated: 2026-08-24

This document describes the current engineering understanding of LearnFlow data processing. It is not a legal approval and must not be published as the final production notice until the hosting region, subprocessors, contact channel, retention exceptions, and the implemented export/account-erasure flows are verified in staging.

## Data used

LearnFlow may process account identifiers and credentials, learning goals and plans, exercise progress, resource feedback, mastery and adaptive-learning records, AI task metadata, administrative audit events, and reliability telemetry.

Passwords are stored as one-way hashes. Tokens, API keys, full prompts, full model answers, and unnecessary user content must not be written to logs or telemetry.

## Purposes

Data is used to authenticate users, deliver learning plans and exercises, personalize recommendations, operate AI workflows, prevent abuse, diagnose failures, measure reliability, and satisfy security audit needs.

## Retention

Current automated defaults are:

- Agent call summaries: 30 days.
- Administrative audit events: 365 days.
- Failed asynchronous-task payloads: 7 days.
- Adaptive-decision evidence: 90 days.
- Password-reset tokens: expire after 20 minutes and are periodically removed.
- Data-export artifacts: 24 hours by default.
- Privacy request workflow metadata: 365 days by default; direct identity is replaced by an HMAC reference after erasure.

Account-linked learning records are retained while the account exists. An engineering flow now exports them and deletes directly linked learning records plus uploaded source objects after confirmed erasure. Real object storage, backup expiry, telemetry retention, and legal retention exceptions are not yet verified.

## User choices and rights

The personal-settings page provides engineering flows to export data and request deletion. The response target for a verified erasure request is no more than 30 days, subject to documented security or legal retention requirements. These flows are not production-ready until staging verification and privacy approval are complete.

## Sharing and international processing

The cloud platform, hosting regions, model providers, object-storage provider, telemetry provider, and other subprocessors have not been selected or approved. Production release is blocked until they are recorded here and reviewed.

## Contact

Privacy contact: not yet assigned.

Do not use this draft as production consent or policy text. The data-protection owner must approve a final notice and attach the approval to the data-governance release evidence.
