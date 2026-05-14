# AGENTS.md — unige-events backend

## Rôle
Backend REST API de UNIGE Events. **Architecture microservices** post-Sprint 8 + finalisation + fixes infra mai 2026 — **5 services Quarkus 3 tous actifs**, **chacun avec sa propre instance PostgreSQL 16 dédiée** (DB-per-service livré post-PR #158), fronté par Kong DB-less, événementiel via Kafka KRaft. Java 21 · Hibernate Panache · Auth0/OIDC.

Topologie complète + flux requête + table endpoints owned par service : [`docs/architecture.md`](docs/architecture.md). Spec originale : [`../specs_archives/specs_claude/specs_microservices_migration.md`](../specs_archives/specs_claude/specs_microservices_migration.md). Spec de complétion : [`../specs_archives/specs_claude/specs_microservices_migration_completion.md`](../specs_archives/specs_claude/specs_microservices_migration_completion.md). Spec de finalisation : [`../specs_archives/specs_claude/specs_microservices_migration_finalization.md`](../specs_archives/specs_claude/specs_microservices_migration_finalization.md).

## Layout Maven (post-refactor `fab270e0`)

`backend/` est un projet **multi-module**. Le parent POM agrégateur vit à `backend/pom.xml` (`packaging=pom`) et déclare 2 sous-aggregators : `<module>shared</module>` (10 libs leaf) et `<module>services</module>` (5 services leaf). Total : **15 modules leaf** dans le reactor (les modules historiques `contract-tests` et `e2e` ont été retirés du reactor après PR #158 — cf. commit `fab270e0` `refactor(backend): regroup shared libs under backend/shared/`).

| Catégorie | Localisation | Modules | Packaging | Notes |
|---|---|---|---|---|
| **Microservices Quarkus actifs (×5)** | `backend/services/<svc>-service/` | `event-service` (sous-packages share, view, favorite, coorganizer, stats, me), `user-service` (sous-packages follow, calendar), `engagement-service` (sous-packages attendance, comment), `moderation-service`, `notification-service` (actif depuis `f4b5968e`) | `quarkus` | Chacun a sa propre Postgres dédiée (`postgres-<svc>`), owns ses tables Flyway, REST endpoints + Kafka producers/consumers. Cf. `architecture.md` table par-service. |
| **Shared libs (×10)** | `backend/shared/<lib>/` | `rate-limit` (`@PerUserRateLimit` + interceptor + state cache), `storage` (`FileStorageService` S3), `api-error`, `domain-enums`, `domain-dtos`, `domain-projections`, `jaxrs`, `tracing`, `kafka-events`, `platform` | `jar` | `rate-limit` + `storage` à 100 % L. Les 8 autres : ≥ 95 % L / ≥ 90 % B (Décision D spec complétion). Les artefacts Maven gardent leur `artifactId` historique `shared-<lib>` pour compatibilité GAV (cf. `aee13d4e refactor(backend): rename Maven artifactIds and drop <name> tags`). |

## Commandes

```bash
cd backend && ./mvnw verify              # build + tests complets — 15 modules, ~3-4 min
cd backend/services/<svc>-service && ../../mvnw quarkus:dev   # dev local par service
cd backend && ./mvnw -pl services/<svc>-service -am verify    # un service + ses deps
cd backend && ./mvnw -pl shared/<lib>     -am verify          # une shared lib seule
```

`quarkus:dev` ne fonctionne pas depuis le parent multi-module — il s'exécute dans le contexte d'UN module Quarkus. Les tests nécessitent Docker-in-Docker (Quarkus DevServices lance un PostgreSQL éphémère par service, plus Kafka in-memory). En prod / preview, chaque service se connecte à **sa propre Postgres** (`postgres-event`, `postgres-user`, etc.).

## Architecture en couches (par service)

```
Resource (JAX-RS, /api)
    ↓ @Inject
Service (@ApplicationScoped, @Transactional)
    ↓ Panache Active Record       ↓ @Inject @RegisterRestClient
Entity (PanacheEntity + JPA)      Cross-service REST client
    ↓ JDBC                        (avec @Retry/@Timeout/@CircuitBreaker)
PostgreSQL                        HTTP service voisin
```

Jamais de saut de couche. La Resource ne touche pas aux entités directement. La logique métier est dans le Service. **Les calls cross-service passent par REST clients `@RegisterRestClient`** (jamais de JPA cross-schema — les 8 couples consumer/provider post-consolidation 14→5 sont matérialisés en Étape 4 de la spec finalization).

Émissions Kafka post-commit via CDI `@Observes(during = TransactionPhase.AFTER_SUCCESS)` (Décision A). Les bridges (`<Domain>KafkaBridge.java`) déclenchent l'`Emitter.send()` après commit JDBC pour éviter les events fantômes sur rollback.

## Conventions critiques (inchangées de la migration)

### Nommage — camelCase partout
- Champs JPA en **camelCase** : `displayName`, `startDate`, `creatorId`.
- Jackson sérialise en camelCase (convention par défaut).
- **Jamais de snake_case** dans les champs ou les réponses JSON — le frontend consomme `user.displayName`, `event.startDate`, etc.

### Booléens — pas de préfixe `is`
- Utiliser `active` (pas `isActive`), `featured`, `admin`, `read`.
- Lombok génère `isIsActive()` — conflit. Jackson sérialise `isActive` — incohérence.

### Entités, persistance, Auth
- Entités étendent `PanacheEntity` (pas de repository séparé).
- Services `@ApplicationScoped` + `@Transactional` sur toutes les mutations.
- Resources JAX-RS, préfixe `/api` (configuré dans `application.properties`).
- **Hibernate en `validate`** en dev/prod. **Flyway redistribué par service** : chaque service possède ses propres migrations sous `services/<svc>-service/src/main/resources/db/migration/V*.sql`, appliquées sur **sa Postgres dédiée** (`postgres-<svc>`). Plus de schéma `public` partagé — l'isolation DB-per-service a été livrée post-PR #158 (commit `f4b5968e`). En `%test`, Hibernate `drop-and-create` pour les bases éphémères DevServices.
- Soft-delete d'un Event : transition vers `EventStatus.CANCELLED` (le champ
  `status` porte la sémantique soft-delete ; il n'y a pas de booléen
  `active` séparé). Cf. `data-model.md`. Le DELETE physique d'un Event
  annulé est autorisé via `EventService.delete()` (cascade documentée).
- Auth : `quarkus-oidc` mode `service` en prod, `%test.quarkus.oidc.enabled=false`. Pas de defaults bidons (SEC-004) — les vars d'env sont posées par Doppler en preview/prod (cf. [`docs/devops-handoff.md`](docs/devops-handoff.md) item 6).

### Rôle ADMIN — claim Auth0, pas de champ DB
Le rôle ADMIN est porté **exclusivement** par la claim Auth0 (`https://quarkus-security.com/roles`, configurée via `quarkus.oidc.roles.role-claim-path`) et consommé via `@RolesAllowed("ADMIN")` ou `identity.hasRole("ADMIN")`. **Pas de champ `admin: boolean` sur l'entité `User`** — décision SCRUM-94. Une seule source de vérité (Auth0).

### Cascade SCRUM-136 + anti-oracles ISSUE-92 / ISSUE-93

Centralisés derrière les services propriétaires + REST clients (Décision L de la spec de complétion) :
- **ISSUE-92** (Event DRAFT/CANCELLED retourne 404 aux non-créateurs / non-admins) : règle dans `event-service.EventService.getById`. Les consommateurs propagent le 404.
- **ISSUE-93** (User profilePublic=false retourne 404 aux non-self / non-admins) : règle dans `user-service.UserService.getPublicProfile`. Les consommateurs propagent le 404.
- **Cascade SCRUM-136** (`isCreatorOrAcceptedCoOrganizer`) : post-consolidation 14→5 (event-service absorbe co-organizer), la cascade est désormais une primitive locale exposée via le query param `GET /events/{id}?check-co-org-of={uuid}` qui retourne `EventDTO` enrichi du champ `coOrganizerOf: bool`. Plus de hop REST cross-service nécessaire.

Les helpers locaux dupliqués des consommateurs ont été supprimés en complétion.

## Contrat API

[`openapi/openapi.yaml`](../openapi/openapi.yaml) est la **source de vérité** pour le contrat **public** (monorepo — fichier unique partagé entre frontend et backend).

Les **endpoints internes service-to-service** post-finalization (`GET /events/{eventId}/attendance-summary`, `GET /events?ids=…&status=PUBLISHED`, `GET /events/{id}?check-co-org-of={uuid}`, `GET /users/{id}/attendances?status=ATTENDING`) ne sont **pas** dans `openapi.yaml` — ils ne sont pas routés par Kong et ne font pas partie du contrat public. Ils sont documentés dans [`docs/internal-endpoints.md`](docs/internal-endpoints.md). Décision G de la spec finalization annule la dérogation Q — `git diff openapi/` reste à 0 ligne ABSOLU.

Avant d'implémenter un endpoint **public** :
1. L'ajouter dans `openapi/openapi.yaml` (schémas en camelCase, booléens sans préfixe `is`).
2. Ensuite Resource → Service → Entity → Tests (unit + integration).

## Observabilité

Chaque service métier (4 actifs post-consolidation) embarque :
- `quarkus-logging-json` — logs structurés JSON sur stdout.
- `quarkus-micrometer-registry-prometheus` — endpoint `/q/metrics` (interne, scraped par Prometheus K8s ; pas exposé Kong).
- `shared-tracing` — `RequestIdFilter` + `RequestIdClientFilter` qui propagent `X-Request-ID` cross-service via header + MDC.

## Comportement attendu des endpoints
- `GET /users/me` : **401** si token absent ou invalide — jamais de body partiel ou null.
- `PUT /users/me` : retourner l'objet **User complet** dans la réponse (pas `204`).
- Toujours retourner un body JSON cohérent.

## Ce qu'il ne faut jamais faire
- Utiliser du snake_case dans les champs ou JSON.
- Préfixer les booléens avec `is` dans les entités JPA.
- Modifier une migration Flyway déjà committée — créer un `V<N+1>__…`.
- Mettre de la logique métier dans une Resource.
- Retourner `null` ou un body vide là où le frontend attend un objet.
- **Créer un JPA stub cross-service** (`*Stub.java` pour lire la table d'un autre service) — passer par REST client `@RegisterRestClient`. Convention vérifiée par `find backend/services -name '*Stub.java'` qui doit retourner vide.
- Émettre Kafka in-transaction (cf. BUG-001/002) — passer par CDI `@Observes(AFTER_SUCCESS)` + bridge.

## Documentation
- [`docs/architecture.md`](docs/architecture.md) — topologie microservices, flux requête, services + endpoints + tables.
- [`docs/data-model.md`](docs/data-model.md) — entités, ownership par service, conventions, enums.
- [`openapi/openapi.yaml`](../openapi/openapi.yaml) — contrat API public.
- [`docs/internal-endpoints.md`](docs/internal-endpoints.md) — endpoints internes service-to-service (hors openapi).
- [`docs/api-contract.md`](docs/api-contract.md) — annotations rate-limit, service amont par endpoint.
- [`docs/dev-guide.md`](docs/dev-guide.md) — démarrage, workflows, layout 17 modules.
- [`docs/sprint-context.md`](docs/sprint-context.md) — état d'avancement et backlog.
- [`docs/microservices-migration-roadmap.md`](docs/microservices-migration-roadmap.md) — historique des PRs d'extraction.
- [`docs/devops-handoff.md`](docs/devops-handoff.md) — items DevOps S9+ formalisés.

## Maintenance de la documentation

| Fichier modifié | Documentation à mettre à jour |
|---|---|
| Nouvelle entité JPA ou modification de champ | `docs/data-model.md` + schémas dans `openapi.yaml` |
| Nouveau endpoint public ou modification de signature | `openapi/openapi.yaml` EN PREMIER, puis le code |
| Nouvel endpoint **interne** service-to-service | `docs/internal-endpoints.md` (pas `openapi.yaml`) |
| Modification d'un enum partagé | `services/shared-domain-enums/` + `docs/data-model.md` |
| Changement d'architecture ou de convention | `docs/architecture.md` + section dans `AGENTS.md` |
| Fin de sprint / tâche JIRA terminée | `docs/sprint-context.md` |

**Règle d'or : si tu touches au code, tu touches à la doc correspondante dans le même commit.**

## Workflow Git
- Branche : `feature/SCRUM-XX-description`. Branche persistante de migration : `refactor(backend)--migrate-to-microservices` (PR #158, attention au workaround `chore(backend):` cf. sprint-context).
- 1 PR par tâche, review obligatoire avant merge sur `main`.
- Qualité : SonarCloud seuil 80 % couverture (JaCoCo) — projet unique `unige-pinfo6-2026_unige-events-backend` (**Option B définitive Étape 22** — finit le bug Sonar Maven multi-module). Le scan est lancé par le job CI `sonar-aggregate` (post-matrix), qui consomme les jacoco.xml des 17 modules via artifacts uploadés par les jobs amont. Cf. `docs/sprint-context.md` § Étape 22.

### Conventions de PR
- **Titre** : format `<type>(<scope>): <description>`, validé par `.github/workflows/pr-title-check.yml`.
  - Types autorisés : `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `perf`.
  - Pour `feat` / `refactor` / `perf` le scope est **obligatoirement** l'identifiant Jira en minuscules (`scrum-XXX`), ex. `feat(scrum-133): add /users/me/events endpoint`.
  - Pour les autres types le scope est libre, ex. `fix(backend): handle null author`.

### Sonar — exigences nouveau code
- Coverage ≥ 80 %.
- Duplication ≤ 3 %.
- Security Rating : A. Security Review Rating : A. Reliability Rating : A. Maintainability Rating : A.
