# ADR-004 — REST resource-first URL convention (no `/api/{service-name}/` prefix)

| Field | Value |
|---|---|
| Date | 2026-05-18 |
| Status | Accepted |
| Author | Backend / SCRUM-80 PR review (devops feedback) |
| Audit reference | n/a — design ADR |

## Context

During the SCRUM-80 PR #176 review, the devops team observed that the
public API paths do not follow a uniform `/api/{service-name}/...`
scheme and proposed renaming endpoints so each service has its own
top-level prefix (e.g. `/api/notifications/me` instead of
`/api/users/me/notifications`). The stated goals were to simplify the
Kong route table and make the service-owner of a given endpoint
obvious at a glance.

An audit of the current contract found that **15 of ~70 implemented
endpoints have a path prefix that does not match their owning
service** — examples:

- `/api/users/me/notifications` → owned by `notification-service`
- `/api/users/me/favorites`, `/api/users/me/events`,
  `/api/users/me/co-organizer-invitations` → owned by `event-service`
- `/api/users/me/attendances`, `/api/users/me/participations` →
  owned by `engagement-service`
- `/api/events/{id}/attend`, `/api/events/{id}/attendees`,
  `/api/events/{id}/comments` → owned by `engagement-service`
- `/api/events/{id}/report`, `/api/comments/{id}/report` → owned
  by `moderation-service`
- `/api/follow-requests/{id}/*` → owned by `user-service`
- `/api/calendar/{token}.ics`, `/api/s/{shortCode}` → not prefixed
  by their owner's collection name at all

These "crossings" fall into three intentional REST patterns:

1. **BFF `/users/me/{collection}`** — a unified per-user view of a
   resource. The client does not have to know that "my notifications"
   live in a different service than "my favorites" or "my profile".
2. **Action on resource (`/events/{id}/{verb}`)** — the verb is owned
   by a different service, but the addressed resource is the event.
3. **Standalone resources** (`/follow-requests/{id}`, `/calendar/*`,
   `/s/{code}`) — naturally short, distinct from their parent
   collection.

## Decision

Keep the REST resource-first convention:
`/api/{collection}/{id}/{sub-resource-or-action}`. The owning service
is a **backend implementation detail** and remains invisible from the
client contract.

The path → service mapping is recorded in two places:

- **`k8s/chart/templates/kong/configmap-routes.yaml`** — runtime
  source of truth. Kong dispatches each path to its upstream service
  via an explicit anchored regex.
- **`openapi/openapi.yaml`** — documentation source of truth. Each
  operation carries an `x-owner-service` vendor extension naming the
  service that implements it (added in the same PR as this ADR — see
  the `docs(api): tag OpenAPI operations` commit). Tooling like
  Redocly surfaces the extension in the generated docs.

## Why this is necessary

- **Industry standard.** Every large public API we audited follows
  the resource-first convention: Stripe (`/v1/customers/{id}`),
  GitHub (`/repos/{owner}/{repo}/issues`), Twilio
  (`/2010-04-01/Accounts/{sid}/Messages`), Linear, Slack. None
  prefix their paths with the owning microservice's name. Diverging
  from this convention would surprise any developer (or future
  third-party integrator) coming from those ecosystems.
- **BFF pattern preserved.** `/api/users/me/notifications` reads as
  *"my notifications"*, a single coherent view of the current user.
  Refactoring to `/api/notifications/me` would lose the
  `/users/me/...` umbrella that already groups every per-user
  collection (favorites, events, attendances, participations,
  follow-requests, calendar-token, image, banner, username, …).
- **Action-on-resource pattern preserved.** `POST
  /api/events/{id}/report` expresses *"report this event"*. The
  noun is the event (owned by `event-service`); the verb happens
  to be implemented by `moderation-service`. Renaming to
  `POST /api/reports/events/{id}` would invert the natural reading.
- **Backend topology stays internal.** Surfacing the service name
  in the path leaks an implementation detail into the public
  contract. Future consolidations or splits (e.g. merging
  `moderation-service` into `engagement-service` someday) would
  become breaking changes for every client.
- **Cost vs. benefit.** A full rename touches the frontend,
  `openapi.yaml`, every Kong route, every backend
  `@Path` annotation, every test, every doc, and any external
  integration. The benefit is purely cosmetic at the URL level;
  the Kong route count is unchanged because it is proportional to
  the number of endpoints, not the prefix shape.

## How this is enforced

- **`openapi/openapi.yaml`** — every operation carries
  `x-owner-service: {event,user,engagement,moderation,notification}-service`.
  This is the documentation-level guarantee that the owner is
  recorded explicitly even when the path prefix does not hint at it.
- **`k8s/chart/templates/kong/configmap-routes.yaml`** — the runtime
  guarantee that each path is dispatched to the declared upstream.
  Routes are anchored regex (`~/api/.../$`) with `strip_path: false`
  and `preserve_host: true`. Consolidated where regexes overlap
  (see `chore(kong): consolidate similar regex routes` commit).
- **`backend/docs/api-contract.md`** — human-readable summary table
  pinning each path to its owning service, kept in sync with the
  two sources above. If the three diverge, `configmap-routes.yaml`
  wins (it is what actually serves traffic).
- **No automated CI check** for path-prefix vs. owner alignment in
  S9 — the convention is *deliberately permissive*. New endpoints
  added in the future may also legitimately "cross"
  (e.g. SCRUM-145 will add `COMMENT_MENTION` / `NEW_COMMENT`
  notification consumers — those notifications will still surface
  via `GET /api/users/me/notifications`).

## Alternatives considered

| Alternative | Why not |
|---|---|
| Rename every endpoint to `/api/{service-name}/...` (devops proposal) | Breaking change for every client (frontend, future integrators). Anti-pattern against the industry standard. Leaks backend topology into the public contract. Breaks the BFF `/users/me/...` umbrella and the "action on resource" pattern. Does not actually shrink the Kong route table — the count of mappings stays equal to the count of endpoints. |
| Introduce a `/api/v2/...` prefix and rename incrementally | Deferred indefinitely. No third-party clients exist; the project is internal to the UNIGE PINFO6 cohort. Introducing v2 would double the maintenance surface (two contracts, two Kong route tables, two OpenAPI sections) for no concrete benefit. Revisit if/when an external integration partner appears. |
| Catch-all Kong route per service with priority overrides (`~/api/events/.*` → event-service, override `/api/events/{id}/(attend\|attendees\|comments\|report)`) | Kong DB-less mode does not support per-route priorities in a robust way (route ordering is alphabetical by route name at the moment, not by user-declared priority). Achievable only by migrating to Kong DB-full, which adds operational weight (Postgres for Kong, migrations, backup). Out of scope for S9. |

## When to revisit

- If the API is opened to third-party integrators (B2B partners,
  mobile-first re-skin, public OAuth clients) — at that point the
  cost-benefit of a versioned `/api/v2/...` rename may flip.
- If a v2 contract rewrite is planned for any other reason
  (large-scale data model overhaul, breaking auth migration) — the
  rename can ride along with that v2 cut at near-zero marginal cost.
- If Kong is migrated to DB-full mode for unrelated reasons (e.g.
  introducing per-route weighted load balancing) — the catch-all
  alternative above becomes feasible and may reduce the route count.

## Consequences

- **Discoverability has two sources.** Reviewers and onboarding
  developers must read either `configmap-routes.yaml` or the
  `x-owner-service` OpenAPI tag to identify the owning service of
  an endpoint — the path prefix alone is not authoritative.
- **No static guarantee** that a new endpoint's path prefix matches
  its implementing service. Code review (and a sanity check that
  the `x-owner-service` tag matches the Kong route's upstream) is
  the safeguard.
- **Refactor cost stays low.** Moving an endpoint from one service
  to another is a Kong route edit + an `x-owner-service` tag edit
  — no client-facing change, no openapi path rename.
- **This ADR is the canonical answer** to future "should we prefix
  paths by service name?" proposals. Link to it from PR review
  threads when the topic resurfaces.
