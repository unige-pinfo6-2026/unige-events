## Why

The user profile flow is already functionally correct for expected paths, but it still has production risks around concurrency, claim quality, and write consistency. This change hardens reliability and data integrity now while preserving current behavior that users rely on.

## What Changes

- Make first-login provisioning concurrency-safe so `GET /api/users/me` does not surface duplicate-key failures under concurrent requests.
- Remove synthetic fallback email generation and define explicit, consistent handling when JWT email claim is missing.
- Add request validation constraints for profile updates and enforce validation at the resource boundary.
- Add optimistic locking for profile updates to prevent silent lost updates during concurrent writes.
- Add/adjust tests for concurrent first-login, missing-claim handling, validation failures, and concurrent update conflicts.
- Preserve existing endpoint shapes and currently working successful behavior; this is hardening, not product behavior redesign.

## Capabilities

### New Capabilities
- `user-profile-api`: Production-hardening requirements for existing self-profile retrieval/update endpoints, including safe provisioning, claim handling, payload validation, and concurrent update protection.

### Modified Capabilities
- None.

## Impact

- Affected code: user profile resource/service/entity/DTO validation and related exception mapping.
- Affected APIs: existing `GET /api/users/me` and `PUT /api/users/me` behavior under edge/failure/concurrency scenarios.
- Affected persistence: user profile row versioning and first-login create path behavior under concurrent access.
- Affected tests: add targeted coverage for concurrency and validation/conflict cases.
