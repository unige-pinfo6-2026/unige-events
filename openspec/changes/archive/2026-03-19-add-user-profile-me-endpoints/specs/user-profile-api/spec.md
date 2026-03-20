## ADDED Requirements

### Requirement: Authenticated user profile retrieval endpoint
The system SHALL expose `GET /api/me` under the `/api` base path and MUST require a valid Bearer JWT.
The endpoint MUST identify the caller from JWT claims (`sub`, `email`).
If no profile exists for the caller's `sub`, the system MUST create one with:
- generated `id`
- `auth0Id` from JWT `sub`
- `email` from JWT `email`
- `profilePublic=false`
- generated `createdAt`
The endpoint MUST return `200` with the full user profile object in snake_case fields.
If authentication is missing or invalid, the endpoint MUST return `401`.

#### Scenario: First login creates profile and returns it
- **WHEN** an authenticated caller invokes `GET /api/me` and no profile exists for the caller's `sub`
- **THEN** the system creates a profile using JWT `sub` and `email`, applies default booleans, and returns `200` with the full profile

#### Scenario: Existing profile is returned
- **WHEN** an authenticated caller invokes `GET /api/me` and a profile already exists for the caller's `sub`
- **THEN** the system returns `200` with the existing full profile and does not change non-editable fields

#### Scenario: Missing authentication on profile retrieval
- **WHEN** `GET /api/me` is called without a valid Bearer token
- **THEN** the system returns `401` with the unauthorized error schema

### Requirement: Authenticated self profile partial update endpoint
The system SHALL expose `PUT /api/users/me` under the `/api` base path and MUST require a valid Bearer JWT.
The endpoint MUST update only the authenticated caller's profile identified by JWT `sub`.
The request body MUST support partial updates where all editable fields are optional.
The only editable fields are:
- `display_name`
- `faculty`
- `study_level`
- `bio`
- `interests`
- `avatar_url`
- `profile_public`
The endpoint MUST reject invalid payloads with `400`.
The endpoint MUST return `404` if no profile exists for the caller.
The endpoint MUST return `403` if a cross-user update attempt is detected.
On success, the endpoint MUST return `200` with the updated full profile.

#### Scenario: Partial update succeeds with editable fields only
- **WHEN** an authenticated caller sends `PUT /api/users/me` with any subset of editable fields
- **THEN** the system updates only provided editable fields and returns `200` with the updated full profile

#### Scenario: Invalid body fails validation
- **WHEN** an authenticated caller sends `PUT /api/users/me` with invalid field types or disallowed fields
- **THEN** the system returns `400` with validation details

#### Scenario: Profile not found for authenticated caller
- **WHEN** an authenticated caller sends `PUT /api/users/me` and no profile exists for the caller's `sub`
- **THEN** the system returns `404` with the not-found error schema

#### Scenario: Forbidden cross-user update attempt
- **WHEN** an authenticated caller triggers an update flow that targets a user identity other than JWT `sub`
- **THEN** the system returns `403` with the forbidden error schema

### Requirement: User profile schema and field mutability contract
The user profile schema MUST include the following fields:
- `id` (UUID, generated)
- `auth0_id` (string, from JWT `sub`)
- `email` (string, from JWT `email`)
- `display_name` (string, nullable)
- `faculty` (string, nullable)
- `study_level` (string, nullable)
- `bio` (string, nullable)
- `interests` (string, nullable)
- `avatar_url` (string, nullable)
- `profile_public` (boolean, default false)
- `created_at` (datetime, generated)
The schema MUST use snake_case JSON property names.
The following fields MUST be marked `readOnly: true` in API schema definitions:
- `id`
- `auth0_id`
- `email`
- `created_at`

#### Scenario: Response payload uses snake_case and read-only metadata
- **WHEN** profile schemas are published in API documentation
- **THEN** all property names are snake_case and non-editable fields are marked as read-only

### Requirement: Security scheme and per-endpoint auth documentation
The API specification MUST define a security scheme named `bearerAuth` of type HTTP Bearer JWT.
The specification MUST apply `bearerAuth` only to endpoints marked as requiring authentication.
Endpoints without explicit auth requirement MUST remain public with no security requirement.
Each endpoint description MUST clearly state whether authentication is required.

#### Scenario: Security is applied only where required
- **WHEN** API security definitions are evaluated
- **THEN** `GET /api/me` and `PUT /api/users/me` require `bearerAuth` and unrelated public endpoints remain unauthenticated

### Requirement: Endpoint examples and standardized error schemas
The API specification MUST include request and response examples for each endpoint:
- `GET /api/me`: `200` profile example and `401` unauthorized example
- `PUT /api/users/me`: request body example, `200` updated profile example, and error examples
The API specification MUST define reusable error response schemas for:
- `400` validation error with details
- `401` unauthorized
- `403` forbidden
- `404` not found
Error examples MUST align with these schemas and status codes.

#### Scenario: Endpoint documentation includes complete examples
- **WHEN** consumers inspect endpoint docs for `GET /api/me` and `PUT /api/users/me`
- **THEN** they can see request/response examples and all required error schema references (`400`, `401`, `403`, `404`)
