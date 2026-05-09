# AGENTS.md — unige-events backend

## Rôle
Backend REST API de UNIGE Events. **Architecture microservices** post-Sprint 8 + finalisation — **5 services Quarkus 3** (4 actifs + 1 placeholder) + 10 shared libs sur PostgreSQL 16 partagé, fronté par Kong DB-less, événementiel via Kafka KRaft. Java 21 · Hibernate Panache · Auth0/OIDC.

Topologie complète + flux requête + table endpoints owned par service : [`docs/architecture.md`](docs/architecture.md). Spec originale : [`../specs_archives/specs_claude/specs_microservices_migration.md`](../specs_archives/specs_claude/specs_microservices_migration.md). Spec de complétion : [`../specs_archives/specs_claude/specs_microservices_migration_completion.md`](../specs_archives/specs_claude/specs_microservices_migration_completion.md). Spec de finalisation (la plus à jour) : [`../specs_archives/specs_claude/specs_microservices_migration_finalization.md`](../specs_archives/specs_claude/specs_microservices_migration_finalization.md).

## Layout Maven (post-finalisation)

`backend/` est un projet **multi-module**. Le parent POM agrégateur vit à `backend/pom.xml` (`packaging=pom`) et déclare **17 modules** sous `backend/services/` (post-consolidation 14→5, Décision A de la spec finalization) :

| Catégorie | Modules | Packaging | Notes |
|---|---|---|---|
| **Microservices Quarkus actifs (×4)** | `event-service` (sous-packages share, view, favorite, coorganizer, stats, me), `user-service` (sous-packages follow, calendar), `engagement-service` (sous-packages attendance, comment), `moderation-service` | `quarkus` | Owned schema(s) + REST endpoints + Kafka producers/consumers. Cf. `architecture.md` table par-service. |
| **Placeholder Notification (×1)** | `notification-service` | `jar` | replicas:0 ; SCRUM-99 hors scope S8 (formalisé dans [`docs/devops-handoff.md`](docs/devops-handoff.md)). |
| **Shared libs Sprint 8 (×2)** | `shared-rate-limit` (`@PerUserRateLimit` + interceptor + state cache), `shared-storage` (`FileStorageService` S3) | `jar` | 100 % couverture tests. Hors glob `<sonar.coverage.exclusions>` du parent. |
| **Shared libs complétion (×8)** | `shared-api-error`, `shared-domain-enums`, `shared-domain-dtos`, `shared-domain-projections`, `shared-jaxrs`, `shared-tracing`, `shared-kafka-events`, `shared-platform` | `jar` | Cible ≥ 95 % L / ≥ 90 % B chacune. Décision D de la spec de complétion. |

## Commandes

```bash
cd backend && ./mvnw verify              # build + tests complets — 17 modules, ~3-4 min
cd backend/services/<svc>-service && ../../mvnw quarkus:dev   # dev local par service
cd backend && ./mvnw -pl services/<svc>-service -am verify    # un service + ses deps
```

`quarkus:dev` ne fonctionne pas depuis le parent multi-module — il s'exécute dans le contexte d'UN module Quarkus. Les tests nécessitent Docker-in-Docker (Quarkus DevServices lance un PostgreSQL éphémère par service, plus Kafka in-memory).

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
- **Hibernate en `validate`** en dev/prod (Flyway pilote le schéma — historique V1..V17 partagé pour l'instant, cf. Décision C de la spec de complétion qui défère DB-per-service S9+). En `%test`, Hibernate `drop-and-create` pour les bases éphémères DevServices.
- Soft-delete : champ `active` boolean sur Event, jamais de DELETE physique.
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
