## Why

The API needs a clear, secure contract for authenticated user profile retrieval and self-service profile updates tied to Auth0 identity (oidc with quarkus). Defining this now aligns backend behavior, validation, and documentation with frontend integration and access-control expectations.

## What Changes

- Add authenticated `GET /api/me` behavior that auto-provisions a user profile on first login using JWT claims and returns the full profile.
- Use sub (auth0_id field in DB) as the ONLY identifier to check if user exists in DB
- If no user found with that sub: create a new profile with auth0_id=sub, email=email, profile_public=false, =false
- Add authenticated `PUT /api/users/me` behavior for partial self-profile updates with strict editable-field limits.
- Define validation and error responses for `400`, `401`, `403`, and `404` with explicit response schemas.
- Define a reusable HTTP Bearer JWT security scheme (`bearerAuth`) and apply it only to endpoints marked as requiring authentication.
- Define profile schema constraints including snake_case JSON fields, read-only non-editable fields, and request/response examples.
- Make sure to update the flyway if necessary

## Capabilities

### New Capabilities
- `user-profile-api`: Authenticated user profile retrieval (`/api/me`) and self-update (`/api/users/me`) with Auth0 JWT identity mapping, profile auto-creation, field-level editability rules, and complete error contract.

### Modified Capabilities
- None.

## Impact

- API behavior and contract for profile endpoints under `/api`.
- OpenAPI documentation (security scheme, schemas, examples, and endpoint descriptions).
- Application layers handling profile creation and update authorization/validation.
- DTO mapping and serialization naming to enforce snake_case payloads.
- Tests for success/error paths and auth enforcement on protected endpoints.
