## ADDED Requirements

### Requirement: Concurrent first-login provisioning MUST be race-safe
The system MUST make `GET /api/users/me` provisioning idempotent under concurrent requests for the same authenticated `sub`.
The system SHALL NOT surface duplicate-key persistence errors to API clients when concurrent first-login requests race.
When a create conflict occurs, the system MUST resolve by returning the existing persisted profile.

#### Scenario: Concurrent first-login requests for same user
- **WHEN** two or more authenticated requests for the same `sub` hit `GET /api/users/me` concurrently and no row exists initially
- **THEN** exactly one persisted user profile exists for that `sub` and all successful responses return the same profile identity

#### Scenario: Existing user read remains unchanged
- **WHEN** a profile already exists for authenticated `sub`
- **THEN** `GET /api/users/me` returns the existing profile without changing endpoint shape or successful response contract

### Requirement: Missing required JWT claims MUST be handled explicitly
The system MUST require required identity claims for profile provisioning and MUST NOT synthesize fallback email values.
If required claims are missing, the system MUST return a deterministic error response and MUST NOT persist fabricated identity data.

#### Scenario: Missing email claim during self-profile retrieval
- **WHEN** an authenticated token is accepted but `email` claim is missing for first-login provisioning
- **THEN** the API returns a consistent error response and no user profile with synthetic email is created

#### Scenario: Required claims present
- **WHEN** authenticated token includes required identity claims (`sub`, `email`)
- **THEN** profile retrieval/provisioning proceeds using claim values directly

### Requirement: Profile update payload MUST be validated at boundary
The system MUST enforce Bean Validation constraints on editable profile fields for `PUT /api/users/me`.
The system MUST reject invalid payload values with structured `400` validation responses.
The system SHALL keep unknown-field rejection behavior unchanged.

#### Scenario: Invalid profile update payload
- **WHEN** `PUT /api/users/me` contains field values violating declared constraints
- **THEN** the API returns `400` with validation details and no partial persistence occurs

#### Scenario: Valid profile update payload
- **WHEN** `PUT /api/users/me` contains valid values for editable fields
- **THEN** the profile is updated successfully and response contract remains unchanged

### Requirement: Concurrent profile updates MUST detect write conflicts
The system MUST use optimistic locking for user profile updates to prevent silent last-write-wins overwrites.
On optimistic lock conflict, the API MUST return a deterministic conflict response.

#### Scenario: Concurrent updates to same profile
- **WHEN** two update operations target the same profile concurrently and one write becomes stale
- **THEN** one update succeeds and the stale update receives a conflict response instead of silently overwriting data

#### Scenario: Non-conflicting single update
- **WHEN** only one update operation is in-flight for a profile
- **THEN** update succeeds as normal with unchanged success response shape

### Requirement: Existing working behavior MUST remain stable
This hardening change MUST preserve existing endpoint paths and normal successful behavior for currently working use cases.
The change MUST NOT introduce new endpoints or broad profile-flow refactors.

#### Scenario: Existing successful retrieval flow remains stable
- **WHEN** clients call `GET /api/users/me` with valid claims in non-concurrent conditions
- **THEN** response semantics and payload contract remain consistent with current behavior

#### Scenario: Existing successful update flow remains stable
- **WHEN** clients call `PUT /api/users/me` with valid payload and no conflict
- **THEN** update behavior remains consistent with current behavior except for newly enforced hardening constraints
