## 1. Data model and migration hardening

- [x] 1.1 Add a Flyway migration that introduces user profile optimistic-lock versioning with safe defaults/backfill.
- [x] 1.2 Add entity version field (`@Version`) on user profile and verify compatibility with existing records.
- [x] 1.3 Validate migration rollback/safety assumptions for production environments.

## 2. First-login provisioning concurrency safety

- [x] 2.1 Refactor first-login get-or-create flow to be race-safe under concurrent requests for the same `auth0_id`.
- [x] 2.2 Handle duplicate-key create conflicts by re-reading and returning existing profile instead of surfacing 500.
- [x] 2.3 Keep normal successful retrieval behavior unchanged for non-concurrent requests.

## 3. JWT claim handling hardening

- [x] 3.1 Remove synthetic fallback email generation from self-profile retrieval flow.
- [x] 3.2 Implement explicit error behavior when required provisioning claims are missing.
- [x] 3.3 Ensure no fabricated profile data is persisted when required claims are absent.

## 4. Update payload validation and conflict responses

- [x] 4.1 Add Bean Validation constraints to editable profile update fields.
- [x] 4.2 Enforce boundary validation with `@Valid` on `PUT /api/users/me`.
- [x] 4.3 Ensure invalid payloads return structured `400` validation errors.
- [x] 4.4 Map optimistic locking conflicts to deterministic conflict responses.

## 5. API contract and documentation alignment

- [x] 5.1 Update API schema/docs to reflect strict claim handling behavior and validation constraints.
- [x] 5.2 Document and expose optimistic-lock conflict response in OpenAPI examples/responses.
- [x] 5.3 Confirm endpoint paths and successful response shapes remain unchanged.

## 6. Verification and regression safety

- [x] 6.1 Add targeted tests for concurrent first-login provisioning behavior.
- [x] 6.2 Add targeted tests for missing required claim behavior.
- [x] 6.3 Add targeted tests for validation failures and optimistic-lock conflicts.
- [x] 6.4 Run focused test suite and verify no behavior regressions in currently working successful flows.
