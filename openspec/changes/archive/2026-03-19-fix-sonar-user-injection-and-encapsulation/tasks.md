## 1. User entity encapsulation

- [ ] 1.1 Convert all non-static public fields in `User` to private fields without changing names, types, defaults, or annotations.
- [ ] 1.2 Add standard getters and setters for each non-static field in `User`.

## 2. Update User field access usage

- [ ] 2.1 Replace direct field reads/writes in `UserService` with corresponding `User` getters/setters.
- [ ] 2.2 Replace direct field reads/writes in `UserResource` with corresponding `User` getters/setters where applicable.

## 3. Constructor injection migration

- [ ] 3.1 Replace `UserResource` field injection with a single `@Inject` constructor and keep endpoint logic/signatures unchanged.
- [ ] 3.2 Replace `UserService` field injection with a single `@Inject` constructor and keep business logic unchanged.

## 4. Static Panache access and verification

- [ ] 4.1 Ensure `UserService` uses static `User.findByIdOptional(id)` access in public profile lookup.
- [ ] 4.2 Run focused compile/tests for touched classes and confirm no behavior, endpoint, or dependency changes.
