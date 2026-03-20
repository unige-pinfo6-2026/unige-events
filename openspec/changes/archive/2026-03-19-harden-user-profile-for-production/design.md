## Context

The service already exposes working self-profile endpoints at `GET /api/users/me` and `PUT /api/users/me`. Identity is bound to Auth0 JWT claims (`sub`, `email`) and persisted on user records (`auth0_id`, `email`).

Current behavior is acceptable for normal traffic, but production hardening is needed for edge and concurrency conditions:
- concurrent first-login requests can race on create
- missing email claim currently triggers synthetic fallback behavior
- update payload has limited explicit field constraints
- concurrent updates can overwrite each other silently

Constraint: preserve endpoint shape and successful-path behavior that is already working as expected.

## Goals / Non-Goals

**Goals:**
- Prevent duplicate-key/500 failures during concurrent first-login provisioning.
- Define explicit handling for missing required JWT claims (especially `email`) without synthetic fallback values.
- Enforce request validation for profile updates using Bean Validation and resource-boundary validation.
- Detect concurrent write conflicts with optimistic locking instead of silent last-write-wins.
- Keep existing endpoint paths and normal success responses stable.

**Non-Goals:**
- No new endpoints.
- No broad refactor outside user-profile flow.
- No behavior redesign for currently working valid requests.

## Decisions

1. **Concurrency-safe provisioning via DB-constraint-aware create flow**
   - Keep unique constraint on `auth0_id` as source of truth.
   - In `getOrCreateUser`, attempt create when lookup misses; on unique-conflict, re-read by `auth0_id` and return existing row.
   - Rationale: minimal code change, deterministic under race, no API shape change.
   - Alternatives considered:
     - Explicit DB upsert query: stronger atomicity, but more vendor-specific complexity.
     - Pessimistic lock around lookup/create: higher contention and unnecessary complexity for this path.

2. **No synthetic fallback email**
   - Remove fallback `auth0Id + "@example.com"` generation.
   - Treat missing `email` claim as invalid authentication context for profile provisioning and return a consistent error.
   - Rationale: avoids persisting fabricated identity data in production.
   - Alternatives considered:
     - Keep synthetic fallback: rejected due to data integrity risk.
     - Persist null email: rejected because schema and downstream assumptions require stable identity data.

3. **Bean Validation for update payload**
   - Add field constraints (length/basic format where appropriate) in `UpdateProfileRequest`.
   - Enforce via `@Valid` on update endpoint request parameter.
   - Keep unknown-field rejection as-is.
   - Rationale: reject malformed/unsafe values before persistence while preserving existing payload shape.
   - Alternatives considered:
     - Service-layer manual validation only: rejected as less declarative and harder to maintain.

4. **Optimistic locking on user profile updates**
   - Add `@Version` to user entity and DB migration for version column.
   - Map optimistic lock failures to a deterministic conflict response.
   - Rationale: prevents silent overwrite and gives callers explicit retry signal.
   - Alternatives considered:
     - Last-write-wins: rejected for data loss risk under concurrent updates.
     - Pessimistic locking: rejected due to lock contention and reduced throughput.

5. **Compatibility guardrail**
   - Preserve existing endpoint paths and successful output schema.
   - Restrict behavioral changes to hardening/edge scenarios only.
   - Rationale: explicit alignment with stakeholder requirement to keep working behavior unchanged.

## Risks / Trade-offs

- **[Conflict handling can increase 409 responses under heavy contention]** → Mitigation: document retry behavior and add targeted concurrency tests.
- **[Stricter claim requirements may reject previously tolerated malformed tokens]** → Mitigation: use clear error messages and align with auth provider contract.
- **[Validation constraints may reject existing bad-but-stored values on update]** → Mitigation: scope validation to incoming changes; do not rewrite unchanged fields.
- **[Migration adds entity versioning to existing table]** → Mitigation: default initial version values and verify rollback plan.

## Migration Plan

1. Add migration for optimistic lock version column on `users` with safe default/backfill.
2. Add entity `@Version` field and adjust update flow for conflict handling.
3. Harden `getOrCreateUser` create path for race-safe behavior.
4. Remove synthetic email fallback and enforce explicit missing-claim behavior.
5. Add Bean Validation constraints and `@Valid` on update endpoint.
6. Add/update tests: concurrent create, missing claim, validation failures, optimistic-lock conflict.
7. Verify that successful existing request paths remain unchanged.

Rollback strategy:
- Revert hardening code paths and tests.
- If needed, retain version column as backward-compatible schema artifact or remove via rollback migration.

## Open Questions

- Should missing `email` claim map to `401` (auth context invalid) or `400` (request context invalid)?
- Should optimistic-lock conflict return `409` with a dedicated error code (recommended) or reuse generic validation-style payload?
