## 1. Profile Contract and Persistence Alignment

- [x] 1.1 Verify/adjust the user profile entity and persistence defaults for `profile_public=false`, and generated timestamps/UUIDs.
- [x] 1.2 Ensure immutable profile fields (`id`, `auth0_id`, `email`, `created_at`) are not writable from update request mapping.
- [x] 1.3 Confirm snake_case JSON serialization/deserialization strategy for profile request/response DTOs.

## 2. Implement GET /api/me Behavior

- [x] 2.1 Enforce Bearer JWT authentication on `GET /api/me` and extract `sub`/`email` claims.
- [x] 2.2 Implement first-login auto-provisioning by `auth0Id` with required defaults when profile does not exist.
- [x] 2.3 Return the full authenticated user profile payload and preserve immutable fields.
- [x] 2.4 Return `401` for missing/invalid authentication.

## 3. Implement PUT /api/users/me Partial Self-Update

- [x] 3.1 Enforce Bearer JWT authentication on `PUT /api/users/me` and resolve target user strictly from JWT `sub`.
- [x] 3.2 Implement partial update logic where all editable fields are optional and only provided editable fields are changed.
- [x] 3.3 Reject invalid/disallowed request fields with structured `400` validation errors.
- [x] 3.4 Return `404` when authenticated user profile is not found.
- [x] 3.5 Return `403` for any cross-user update attempt and `200` with full profile on success.

## 4. OpenAPI Documentation and Security

- [x] 4.1 Define `bearerAuth` as HTTP Bearer JWT in the OpenAPI security schemes.
- [x] 4.2 Apply `bearerAuth` only to `GET /api/me` and `PUT /api/users/me`, leaving non-protected endpoints public.
- [x] 4.3 Add endpoint descriptions explicitly stating auth requirement for each endpoint.
- [x] 4.4 Add request/response examples for `GET /api/me` and `PUT /api/users/me`.
- [x] 4.5 Define and reference reusable error schemas for `400`, `401`, `403`, and `404`.
- [x] 4.6 Mark non-editable profile fields as `readOnly: true` in API schemas.

## 5. Verification

- [x] 5.1 Add/adjust tests for `GET /api/me` first-login creation, existing-profile retrieval, and `401` behavior.
- [x] 5.2 Add/adjust tests for `PUT /api/users/me` partial update success and editable-field enforcement.
- [x] 5.3 Add/adjust tests for `400`, `403`, and `404` error paths on profile updates.
- [x] 5.4 Run targeted test suite and confirm API contract behavior matches the new spec.
