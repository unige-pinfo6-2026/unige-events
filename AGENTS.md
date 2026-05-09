# AGENTS.md — unige-events (root)

Ce dépôt est un monorepo contenant le frontend React et le backend Quarkus de UNIGE Events.

Chaque sous-projet possède son propre `AGENTS.md` avec les conventions, commandes et règles qui lui sont propres. **Lire et suivre le fichier correspondant au périmètre de la tâche en cours.**

## Sous-projets

| Dossier | Stack | AGENTS.md |
|---|---|---|
| `frontend/` | React 19 · TypeScript · Vite · Nginx | [`frontend/AGENTS.md`](frontend/AGENTS.md) |
| `backend/` | Java 21 · Quarkus 3 · Hibernate Panache · PostgreSQL 16 — **multi-module** : 13 microservices Quarkus livrés au Sprint 8 + 1 scaffold `notification-service` (replicas:0, follow-up SCRUM-99) + 10 shared libs (`shared-rate-limit`, `shared-storage`, `shared-api-error`, `shared-domain-enums`, `shared-domain-dtos`, `shared-domain-projections`, `shared-jaxrs`, `shared-tracing`, `shared-kafka-events`, `shared-platform`) — cf. [`specs_archives/specs_claude/specs_microservices_migration_completion.md`](specs_archives/specs_claude/specs_microservices_migration_completion.md) | [`backend/AGENTS.md`](backend/AGENTS.md) |

### Architecture backend post-migration (Sprint 8)

Le backend est découpé en 13 microservices sous `backend/services/<svc>-service/` :

`share-service`, `view-service`, `favorite-service`, `calendar-service`,
`follow-service`, `comment-service`, `co-organizer-service`,
`attendance-service`, `report-service`, `stats-service`,
`me-aggregator-service`, `user-service`, `event-service`.

Trafic `/api/*` routé via **Kong API Gateway** (DB-less, ConfigMap déclarative).
Topologie complète + table endpoints owned par service :
[`backend/docs/architecture.md`](backend/docs/architecture.md). Plan de
migration archivé :
[`specs_archives/specs_claude/specs_microservices_migration.md`](specs_archives/specs_claude/specs_microservices_migration.md).

## Contrat API partagé

`openapi/openapi.yaml` est la **source de vérité unique** pour le contrat API, partagée entre frontend et backend. Ne jamais dupliquer ce fichier.

## Workflow Git global

- Branche : `feature/SCRUM-XX-description`
- 1 PR par tâche, review obligatoire avant merge sur main
- Titre de PR : format `<type>(<scope>): <description>` (validé par CI)
- Types autorisés : `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `perf`
- Pour `feat` / `refactor` / `perf`, le scope est obligatoirement l'identifiant Jira en minuscules, ex. `feat(scrum-133): ...`
