# ADR-001 — ModerationCleanupJob runs at `replicas:1` strict

| Field | Value |
|---|---|
| Date | 2026-05-10 |
| Status | Accepted |
| Author | Backend / Étape 4.6 finalization-complete |
| Audit reference | KAFKA-MOD-CLEANUP-IDEM (IMPORTANT) |

## Context

`moderation-service` hosts a `@Scheduled(cron = "0 0 3 * * ?", timeZone = "Europe/Zurich")` job (`ModerationCleanupJob`) that scans `reports.status = PENDING`, groups by `event_id`, and fires a CDI `EventBannedEvent` (later relayed to Kafka `events.banned` via the `EventBannedKafkaBridge` `@Observes(during = AFTER_SUCCESS)`) for every event that crossed the auto-hide threshold.

The scheduling primitive is the SmallRye / Quarkus in-process scheduler. There is **no leader election** — every pod that runs the service runs the cron independently. With `replicas: 1` (default of the Helm chart), this is invariant-safe.

## Decision

`moderation-service` runs **strictly** at `replicas: 1`. Scaling out is forbidden until proper leader-election (Shedlock + DB-backed lock, or a Kubernetes Lease) is wired.

## Why this is necessary

If two replicas of `moderation-service` ran concurrently:

1. The cron fires on both pods simultaneously (clocks drift bounded by NTP).
2. Both pods scan the same `PENDING` reports, both compute the same threshold list, both fire the same `EventBannedEvent`s.
3. `event-service` consuming `events.banned` is **idempotent on `event_id`** — the BAN itself is safe (the second consume is a no-op).
4. **But the Kafka audit trail is duplicated** — every `events.banned` record now exists at least twice in the topic. Downstream metrics (count of bans/day) are doubled. Kafka retention is wasted. Future consumers (notification-service SCRUM-99) would also be duplicated.

The auto-hide trail is the historical record of moderation actions; doubling it pollutes the data.

## How this is enforced

1. **Helm `values.yaml`** sets `moderationService.replicas: 1` and a comment forbidding scale-out without revisiting this ADR.
2. **`k8s/chart/templates/moderation-service/deployment.yaml`** carries an inline comment `# replicas: 1 strict (no leader-election in S8)` next to the `replicas:` declaration.
3. **HorizontalPodAutoscaler** is intentionally **not** declared for `moderation-service`.
4. **`backend/docs/devops-handoff.md`** lists this constraint under the "Operational invariants" section.
5. **CI guard (informational, not blocking)**: a `helm template` linter step in CI that fails if `replicas` for `moderation-service` ≠ 1.
6. **`event-service` follows the same constraint** (since Étape 24.5.2,
   A3) — `EventExpirationJob` is also a `@Scheduled` cron without leader
   election. The Helm guard duplicated to `k8s/chart/templates/event-service/deployment.yaml`
   uses `{{- if gt (int .Values.eventService.replicas | default 1) 1 }}{{- fail … }}{{- end }}`
   to fail any `helm install` / `helm upgrade` attempting `replicas: 2+`.
   `EventExpirationService.expireEvents()` additionally takes a
   `pg_advisory_xact_lock(0)` as defense-in-depth against an in-cluster
   `kubectl scale` that bypasses helm. Same Shedlock migration path
   applies — when leader-election lands, both `moderation-service` and
   `event-service` will be allowed to scale out together.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Shedlock (DB-backed lock) | Adds a runtime dependency + test surface; deferred to S9+ together with proper observability for the cleanup audit trail. |
| Kubernetes Lease (coordination.k8s.io/v1) | Same complexity tier as Shedlock without buying anything until we actually need to scale. |
| Leader election via Kafka consumer group | Out of scope — the cron is a scheduler, not a stream consumer. Would require a wholly different architecture. |

## When to revisit

- When `moderation-service` reaches a sustained throughput where a single pod's cron run exceeds 1 minute (we are currently sub-second on real prod data).
- When SCRUM-99 (notification-service) ships and needs to consume `events.banned` exactly-once across replicas — at that point the audit trail duplication becomes a user-visible bug, not just a metric bloat.

## Consequences

- **Single point of failure**: a `moderation-service` pod crash means no cleanup until Kubernetes restarts it. Recovery time = pod restart latency (~30s with the configured `livenessProbe`). Acceptable: cleanup is best-effort, idempotent across runs, and worst case the next nightly cron picks up the missed events.
- **Rolling updates** must use `maxUnavailable: 0` / `maxSurge: 1` (already configured in the Deployment manifest) so the new pod becomes Ready before the old one is torn down — no double-cron window during deploys.
