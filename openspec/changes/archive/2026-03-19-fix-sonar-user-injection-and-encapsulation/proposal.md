## Why

SonarCloud reports maintainability and code-quality warnings in the user domain layer due to mutable public entity fields and field-based dependency injection. This change addresses those warnings now to keep quality gates clean while preserving all existing API and business behavior.

## What Changes

- Convert all non-static public fields in `User` entity to private fields with standard getters and setters.
- Keep all existing JPA annotations, field names, types, and default values unchanged in `User`.
- Replace direct field access in `UserResource` and `UserService` with accessor calls.
- Replace field injection in `UserResource` with a single constructor annotated with `@Inject`.
- Replace field injection in `UserService` with a single constructor annotated with `@Inject`.
- Use static access for `User.findByIdOptional(id)` in `UserService` where required.
- Restrict implementation scope to `User.java`, `UserResource.java`, and `UserService.java` only.

## Capabilities

### New Capabilities
- `code-quality-user-domain`: Enforces encapsulation and constructor-injection patterns in the user domain/resource/service classes without changing runtime behavior.

### Modified Capabilities
- None.

## Impact

- Affected code: `src/main/java/ch/unige/events/entity/User.java`, `src/main/java/ch/unige/events/resource/UserResource.java`, `src/main/java/ch/unige/events/service/UserService.java`.
- API impact: none (no endpoint, payload, or response changes).
- Dependency impact: none.
- Data model/runtime behavior impact: none (no logic or persistence semantics changed).
