## Context

SonarCloud reports code-quality issues in the user domain stack (`User`, `UserResource`, `UserService`) related to mutable public entity fields and field-based dependency injection. The current code is functionally correct and must remain behaviorally identical after remediation.

## Goals / Non-Goals

**Goals:**
- Remove Sonar warnings by encapsulating `User` fields with private visibility and standard accessors.
- Replace field injection with constructor injection in `UserResource` and `UserService`.
- Update direct field access in `UserResource` and `UserService` to use `User` getters/setters.
- Keep endpoints, business logic, persistence mappings, and runtime behavior unchanged.

**Non-Goals:**
- No endpoint additions, removals, or path changes.
- No business-rule changes in profile retrieval/provisioning/update flows.
- No annotation, field-name, field-type, or default-value changes in `User`.
- No changes outside `User.java`, `UserResource.java`, and `UserService.java`.

## Decisions

- Use private fields + JavaBean accessors in `User` for all non-static fields.
  - Rationale: resolves encapsulation warnings and aligns with common persistence/entity code quality rules.
  - Alternative considered: suppress Sonar rule; rejected because it does not improve maintainability.

- Use one `@Inject` constructor in `UserResource` and `UserService`.
  - Rationale: constructor injection improves explicit dependencies and satisfies Sonar guidance.
  - Alternative considered: keep field injection; rejected because it leaves warnings unresolved.

- Replace direct field access with accessor calls only where required by encapsulation change.
  - Rationale: smallest safe change set preserving behavior.
  - Alternative considered: broad refactor and reformat; rejected as unnecessary risk.

- Keep static `User.findByIdOptional(id)` usage explicit in `UserService`.
  - Rationale: aligns with Sonar warning without changing control flow.

## Risks / Trade-offs

- [Risk] Missed field access conversion causes compile failures → Mitigation: run focused compile/test validation after edits.
- [Risk] Constructor injection signature mistakes break CDI wiring → Mitigation: use a single `@Inject` constructor preserving current dependency set.
- [Trade-off] More boilerplate in `User` due to accessors → accepted for improved encapsulation and rule compliance.
