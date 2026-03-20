## ADDED Requirements

### Requirement: User entity fields MUST be encapsulated
The `User` entity MUST declare all non-static fields as private and MUST expose standard getters and setters for each field.
All existing JPA and related annotations, field names, field types, and default values MUST remain unchanged.

#### Scenario: Entity encapsulation is applied without mapping changes
- **WHEN** the `User` entity is updated to satisfy code-quality rules
- **THEN** all non-static fields are private with accessors and existing mapping annotations/semantics are preserved

### Requirement: Resource layer dependency injection MUST use constructor injection
`UserResource` MUST use a single constructor annotated with `@Inject` for dependency injection and MUST NOT use field injection.
Endpoint logic, endpoint annotations, and method signatures MUST remain unchanged.

#### Scenario: Resource dependencies are injected via constructor
- **WHEN** `UserResource` is instantiated by CDI
- **THEN** required dependencies are provided through one `@Inject` constructor with unchanged endpoint behavior

### Requirement: Service layer dependency injection MUST use constructor injection
`UserService` MUST use a single constructor annotated with `@Inject` for dependency injection and MUST NOT use field injection.
Business logic and transactional behavior MUST remain unchanged.

#### Scenario: Service dependencies are injected via constructor
- **WHEN** `UserService` is instantiated by CDI
- **THEN** required dependencies are provided through one `@Inject` constructor with unchanged business behavior

### Requirement: Panache static lookup access MUST be explicit
`UserService` MUST use explicit static access for profile ID lookup using `User.findByIdOptional(id)`.
The surrounding control flow and error handling MUST remain unchanged.

#### Scenario: Public profile retrieval keeps existing behavior
- **WHEN** `getPublicProfile` resolves a profile by ID
- **THEN** it uses static `User.findByIdOptional(id)` and preserves existing success/error outcomes
