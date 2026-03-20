## Context

The service exposes REST endpoints under `/api` and uses Auth0 JWT tokens containing `sub` and `email`. The change introduces a consistent profile contract for authenticated users with first-login provisioning (`GET /api/me`) and partial self-profile updates (`PUT /api/users/me`). The implementation must be minimal, preserve existing structure/signatures where possible, and avoid unrelated refactors.

## Goals / Non-Goals

**Goals:**
- Enforce authentication on `GET /api/me` and `PUT /api/users/me` only.
- Persist user identity from JWT claims (`sub`, `email`) at profile creation time.
- Support partial update of editable fields only: `displayName`, `faculty`, `studyLevel`, `bio`, `interests`, `avatarUrl`, `isProfilePublic`.
- Return full profile payloads using snake_case fields and read-only annotations for non-editable properties.
- Standardize error responses for `400`, `401`, `403`, and `404` and document examples.

**Non-Goals:**
- Admin management endpoints or role-assignment workflows.
- User-driven edits to `id`, `auth0Id`, `email`, `isAdmin`, or `createdAt`.
- Broad refactoring of resources/services/entities outside what is required for this contract.

## Decisions

1. **Identity binding via JWT claims**
   - Use JWT `sub` as canonical `auth0Id` and `email` as initial email source.
   - On first authenticated `GET /api/me`, create profile with `isProfilePublic=false` and `isAdmin=false`.
   - Rationale: deterministic identity mapping and predictable first-login behavior.
   - Alternative considered: explicit registration endpoint; rejected to keep login flow single-step.

2. **Authorization model for self-update**
   - `PUT /api/users/me` always resolves target user from token `sub`; no path/user-id override.
   - Return `403` when request attempts cross-user update semantics (defensive check).
   - Rationale: eliminate IDOR-style update risks.
   - Alternative considered: `/api/users/{id}` with self-check; rejected due to broader attack surface and additional ambiguity.

3. **Partial update contract**
   - Request body fields are all optional; only provided editable fields are changed.
   - Unknown/non-editable fields are rejected as validation failure (`400`).
   - Rationale: explicit, safe patch-like semantics without introducing JSON Patch complexity.
   - Alternative considered: full replace semantics; rejected to avoid client burden and accidental data loss.

4. **Schema and documentation consistency**
   - Use snake_case JSON field names for request/response payloads.
   - Mark non-editable fields as `readOnly: true` in schemas.
   - Attach `bearerAuth` only to endpoints marked as auth-required.
   - Include request/response examples and explicit error schema definitions.
   - Rationale: precise API contract for consumers and generated clients.

5. **Error model**
   - `400`: validation error with structured details.
   - `401`: missing/invalid authentication.
   - `403`: forbidden cross-user update attempt.
   - `404`: profile not found for authenticated user in update path.
   - Rationale: deterministic client handling and testability.

## Risks / Trade-offs

- **[Concurrent first-login requests]** duplicate profile creation risk → Mitigation: enforce uniqueness on `auth0Id` and handle create-race gracefully.
- **[Auth0 email changes over time]** local profile email may become stale → Mitigation: treat stored email as creation snapshot for now; revisit sync policy later.
- **[Strict unknown-field rejection]** older clients sending extra fields may fail → Mitigation: document contract clearly and provide actionable `400` details.
- **[403 edge case]** `/users/me` path naturally implies self-user only → Mitigation: retain explicit authorization check and response for defense-in-depth.

## Migration Plan

1. Add/adjust endpoint behavior and DTO validation with minimal code changes.
2. Ensure entity persistence supports required profile fields and defaults.
3. Update OpenAPI definitions: security scheme, endpoint security, schemas, examples, and error responses.
4. Add/adjust tests for auth required, first-login provisioning, partial update semantics, and error statuses.
5. Rollback strategy: revert endpoint and documentation changes; no destructive data migration required.

## Open Questions

- Should email be refreshed from JWT on each authenticated request or remain immutable after profile creation?
- Should a `403` scenario be preserved if no alternate user-targeting API is exposed yet?
