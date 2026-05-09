# Architecture — unige-events backend

> **Sprint 8 — migration vers microservices LIVRÉE + complétion + finalisation**
> (commits `b858196` → tip de la branche `refactor(backend)--migrate-to-microservices`).
> **5 services métiers** (4 actifs + notification placeholder SCRUM-99) + 10 shared libs après consolidation 14→5 (Étape 2 de la finalization, Décision A).
> Kong gateway DB-less + Kafka broker (10 topics, 9 producteurs + 1 consumer) +
> observabilité (logs JSON + Prometheus + X-Request-ID).
> Plan archivé : [`specs_archives/specs_claude/specs_microservices_migration.md`](../../specs_archives/specs_claude/specs_microservices_migration.md).
> Audit post-PR-158 : [`specs_archives/audit_pr158_microservices_migration.md`](../../specs_archives/audit_pr158_microservices_migration.md).
> Spec de complétion : [`specs_archives/specs_claude/specs_microservices_migration_completion.md`](../../specs_archives/specs_claude/specs_microservices_migration_completion.md).
> Spec de finalisation : [`specs_archives/specs_claude/specs_microservices_migration_finalization.md`](../../specs_archives/specs_claude/specs_microservices_migration_finalization.md).
> Plan de consolidation 14→5 : [`consolidation-plan.md`](consolidation-plan.md).
> Détail des PRs d'extraction : [`microservices-migration-roadmap.md`](microservices-migration-roadmap.md).

## Vue d'ensemble — topologie microservices

UNIGE Events est déployé dans Kubernetes (namespace `unige-events` en prod,
`unige-events-pr-N` en preview). Topologie post-finalisation :

| Composant | Type | Réplicas (prod / preview) | Rôle |
|---|---|---|---|
| **web** | Deployment | 1 / 1 | React 19 SPA servie par Nginx |
| **kong** | Deployment | 2 / 1 | API Gateway DB-less, route `/api/*` vers le bon service via une table de routes déclarative |
| **kafka** | StatefulSet | 1 / 1 | Broker KRaft single-node, 10 topics provisionnés. **9 producteurs câblés** (event-service ×5 events.{published,cancelled,expired} + co-organizers.{invited,accepted}, user-service ×3 users.{followed,follow-requested,follow-accepted}, engagement-service ×1 comments.created, moderation-service ×1 events.banned) + **1 consommateur** (event-service ← `events.banned`). Pattern uniforme CDI `@Observes(AFTER_SUCCESS)` + bridge |
| **db** | StatefulSet | 1 / 1 | PostgreSQL 16, schéma `public` partagé (le découpage en schémas par service est **différé S9+** par Décision C de la spec de complétion — l'isolation est matérialisée au niveau code via les REST clients) |
| **minio** | StatefulSet | 1 / 1 | S3 compatible — bucket `unige-events-dev` pour les uploads avatar/banner d'user-service + bannières d'event-service |
| **cloudflared** | Deployment | 1 / 1 | Tunnel preview (mode quick) |
| **5 microservices** | Deployment ×5 | 1 / 1 (notification 0/0) | Quarkus 3.35, image `ghcr.io/unige-pinfo6-2026/unige-events-<svc>:<sha>`, `quarkus-oidc` pour l'auth Auth0. notification-service replicas:0 (placeholder SCRUM-99) |

### Microservices — endpoints owned (post-consolidation)

| Service | @Path racines | Tables possédées | Notes |
|---|---|---|---|
| **event-service** | `/events*`, `/admin/events/{id}/{,un}feature`, `/events/search`, `/events/featured`, `/events/{id}/image`, `/events/{id}/share`, `/s/{shortCode}`, `/events/{id}/view`, `/events/{id}/favorite`, `/users/me/favorites`, `/events/{id}/co-organizers/*`, `/users/me/co-organizer-invitations`, `/events/{id}/stats`, `/users/me/events` | `events`, `event_tags`, `event_views`, `favorites`, `event_co_organizers` | + EventExpirationJob (every 1h, replicas:1 strict). Sous-packages: share, view, favorite, coorganizer, stats, me. Producteur Kafka events.{published,cancelled,expired} + co-organizers.{invited,accepted} ; consumer events.banned (idempotent ban apply) |
| **user-service** | `/users/me`, `/users/{id}`, `/users/me/image`, `/users/me/banner`, `/users/{id}/follow*`, `/follow-requests/*`, `/users/me/follow-requests`, `/users/me/calendar-token*`, `/calendar/{token}.ics` | `users`, `user_interests`, `follows` | Sous-packages: follow, calendar. Auto-create depuis claims JWT + S3 upload avatar/banner. Producteur Kafka users.{followed,follow-requested,follow-accepted} |
| **engagement-service** | `/events/{id}/attend*`, `/users/me/attendances`, `/users/me/participations`, `/events/{id}/comments`, `/comments/{id}` | `attendances`, `comments` | Sous-packages: attendance, comment. PESSIMISTIC_WRITE pour capacity gating + auto-promotion WAITLISTED ; replies comments max 1 niveau ; cascade ISSUE-92 + SCRUM-136. Producteur Kafka comments.created |
| **moderation-service** | `/events/{id}/report`, `/admin/reports*` | `reports` | + ModerationCleanupJob (cron 03:00 Europe/Zurich, replicas:1 strict). Producteur Kafka events.banned |
| **notification-service** | (placeholder, replicas:0, scope SCRUM-99) | aucune | Sentinel `ServiceIdentityResource` only |

### Flux de trafic

```
Utilisateur → HTTPS → Ingress Nginx
                        ├─ /        → Service web (Nginx + SPA React)
                        ├─ /api/*   → Service kong-proxy:8000
                        └─ /s3/*    → Service minio:9000

   Kong DB-less → 4 routes regex anchorées par service métier actif (cf.
                  k8s/chart/templates/kong/configmap-routes.yaml)
                  → Service <svc>-service:8080 (Quarkus pod)
                  → Service db:5432 (PostgreSQL 16)
                  + Service kafka:9092 (producteurs/consommateurs)
                  + Service minio:9000 (uploads via user/event-service)
```

Tout le déploiement passe par un chart Helm umbrella unique
(`k8s/chart/`). Chaque service a son sous-template `templates/<svc>/`
(Deployment + Service ClusterIP) avec `livenessProbe` sur
`/api/q/health/live` (ajoutée en complétion Étape 11 — INFRA-006).
`helm upgrade` reçoit `--set image.tag=<github.sha>` (renommé depuis
`image.api.tag` en complétion Étape 12 — INFRA-007 / SPEC-008) qui
propage le SHA à tous les Deployments — chacun construit son nom
d'image en `unige-events-<svc>:<sha>`.

Auth0 (externe) émet les JWT — chaque service les valide via OIDC
Discovery sans appel inter-service. Kong **ne valide pas** le JWT (cf.
spec décision 7) : il forwarde simplement le header `Authorization:
Bearer <jwt>` vers le service amont qui le revalide localement via
`quarkus-oidc`.

### Notes inter-service (post-completion)

* **REST clients post-finalization** : 8 couples consumer/provider
  (vs 35 stubs JPA pré-finalization). Liste exhaustive : event ↔ user,
  event ↔ engagement (attendance-summary), user ↔ event (bulk
  events?ids=…), user ↔ engagement (user attendances), engagement ↔
  event (avec `?check-co-org-of=` pour cascade SCRUM-136), engagement ↔
  user, moderation ↔ event, moderation ↔ user. Resilience standard sur
  tous les clients : `@Retry(maxRetries=3, delay=200)` + `@Timeout(2000)` +
  `@CircuitBreaker(failureRatio=0.5, requestVolumeThreshold=10)` +
  `@Fallback`. Endpoints **internes** (non Kong) documentés dans
  [`internal-endpoints.md`](internal-endpoints.md) — pas dans
  `openapi.yaml` (Décision G : annulation de la dérogation Q —
  `git diff openapi/` reste à 0).
* **Kafka post-consolidation** : 10 topics provisionnés.
  Producteurs livrés : `event-service` (`events.{published,cancelled,
  expired}` + `co-organizers.{invited,accepted}` post-2.2.4) +
  `moderation-service` (`events.banned`, moderation-service post-2.1.2) +
  `user-service` (`users.{followed,follow-requested,follow-accepted}`,
  ex-follow-service post-2.3.1) + `engagement-service` (`comments.created`,
  ex-comment-service post-2.4.1). Consommateur : `event-service` ←
  `events.banned` (apply `event.status = BANNED`, idempotent).
  Pattern : CDI `@Observes(during = AFTER_SUCCESS)` + bridge
  `<Domain>KafkaBridge` qui appelle l'`Emitter` (Décision A — évite
  BUG-001/002 events fantômes sur rollback).
* **Rate limiting** : 2 étages.
  (1) Lib `shared-rate-limit` (`@PerUserRateLimit` interceptor +
  state cache) couvre 13 sites annotés sur 6 services consommateurs.
  (2) Plugin Kong `rate-limiting` (`policy: local`) sur 3 routes :
  `events.create=10/min`, `comments.post=10/min`,
  `follows.follow=30/min`. La migration vers `policy: redis`
  cluster-wide est un item DevOps S9+ (cf. [`devops-handoff.md`](devops-handoff.md) item 7).
* **Anti-oracles + cascade** : règle unique côté service propriétaire,
  propagation 404 native via REST clients.
  `event-service.EventService.getById` (ISSUE-92), `user-service.UserService.getPublicProfile`
  (ISSUE-93), `event-service.GET /events/{id}?check-co-org-of={uuid}`
  (cascade SCRUM-136 — endpoint interne unique post-consolidation, plus
  besoin du sub-call dédié co-organizer-service car co-organizer absorbé
  dans event-service en Étape 2.2.4).
* **Observabilité** : 3 extensions Quarkus (`quarkus-logging-json`,
  `quarkus-micrometer-registry-prometheus`, `quarkus-rest-client-reactive`)
  + lib `shared-tracing` (X-Request-ID propagé MDC + REST clients +
  Kafka producers).

## Briques d'infrastructure introduites au Sprint 8

* **Kong** ([`k8s/chart/templates/kong/`](../../k8s/chart/templates/kong/))
  — DB-less, 2 replicas prod / 1 preview, ConfigMap `kong-config` porte
  la table de routes déclarative (4 blocs services métiers actifs
  post-consolidation : event, user, engagement, moderation). Plugins
  globaux : `cors`, `correlation-id` (X-Request-ID propagé),
  `prometheus`. Plugins par-route : `rate-limiting` `policy: local`
  sur 3 routes (events.create=10/min, comments.post=10/min,
  follows.follow=30/min).
* **Kafka** ([`k8s/chart/templates/kafka/`](../../k8s/chart/templates/kafka/))
  — KRaft single-broker, PVC sized via values, `clusterId` immutable.
  Job `kafka-topics-init` post-install/upgrade crée les 10 topics figés
  par la spec § 4.5. **9 producteurs + 1 consommateur câblés en
  complétion** (cf. Notes inter-service ci-dessus).
* **Multi-module Maven** ([`backend/pom.xml`](../pom.xml)) — parent
  agrégateur, **15 modules enfants** post-consolidation : 4 microservices
  métiers actifs (event, user, engagement, moderation) + 1 placeholder
  (notification) + 10 shared libs (shared-rate-limit, shared-storage,
  shared-api-error, shared-domain-enums, shared-domain-dtos,
  shared-domain-projections, shared-jaxrs, shared-tracing,
  shared-kafka-events, shared-platform).

---

## Architecture interne du service API

Le backend suit une **architecture en couches stricte** — jamais de saut de couche.

```
┌─────────────────────────────────────────────┐
│  Client HTTP (Bearer JWT)                   │
└─────────────────┬───────────────────────────┘
                  │ REST/JSON
┌─────────────────▼───────────────────────────┐
│  Resource (JAX-RS)                          │
│  @Path, @GET/@POST/@PUT, @Authenticated     │
│  EventResource · UserResource               │
│  → Validation @Valid du body               │
│  → Extraction auth0Id depuis SecurityIdentity│
└─────────────────┬───────────────────────────┘
                  │ @Inject (constructor DI)
┌─────────────────▼───────────────────────────┐
│  Service (@ApplicationScoped)               │
│  @Transactional sur toutes les mutations    │
│  EventService · UserService                 │
│  → Logique métier, gestion des conflits     │
│  → Optimistic locking, race conditions      │
└─────────────────┬───────────────────────────┘
                  │ Panache Active Record
┌─────────────────▼───────────────────────────┐
│  Entity (PanacheEntity + JPA)               │
│  User · Event                               │
│  → @Entity, annotations Bean Validation    │
│  → Helpers statiques : findByAuth0Id, etc. │
└─────────────────┬───────────────────────────┘
                  │ JDBC (Hibernate ORM)
┌─────────────────▼───────────────────────────┐
│  PostgreSQL 16                              │
└─────────────────────────────────────────────┘
```

**Règle absolue :** La Resource ne touche pas aux entités directement. La logique métier est dans le Service.

---

## Flux d'une requête type

Exemple : `PUT /api/users/me` (mono-domaine)

1. Le client envoie `PUT /api/users/me` avec `Authorization: Bearer <jwt>` et un body JSON.
2. **Ingress Nginx** intercepte le trafic public et le proxie vers le service `kong-proxy:8000`.
3. **Kong** (DB-less) résout la route `/api/users/me$` → service amont `user-service:8080`. Plugins globaux appliqués (`correlation-id` pose `X-Request-ID`, `cors`, `prometheus`).
4. **`shared-tracing.RequestIdFilter`** lit le `X-Request-ID` posé par Kong et le pose dans le MDC.
5. **`quarkus-oidc`** valide le JWT via OIDC Discovery Auth0 (signature + expiration + audience). En `%test`, OIDC est désactivé et `@TestSecurity` mocke l'identity.
6. `UserResource.updateMyProfile()` est invoqué — `SecurityIdentity` injecte le `principal.getName()` = `auth0Id`.
7. Le body est désérialisé en `UpdateProfileRequest` et validé par Hibernate Validator (`@Valid`).
8. `UserService.updateMyProfile()` est appelé via `@Inject` — la logique métier s'exécute `@Transactional`.
9. `User.findByAuth0Id()` (Panache Active Record) génère le SQL via Hibernate/JDBC vers PostgreSQL.
10. L'entité est mutée, le flush est explicite, les conflits optimistic lock sont capturés.
11. `UserProfileResponse.from(user)` convertit l'entité en DTO.
12. Jackson sérialise en JSON camelCase → réponse `200 OK`.

Exemple cross-service : `POST /api/events/{id}/comments`

1. Kong route → `comment-service:8080`.
2. `comment-service.CommentResource.create()` → `CommentService.post()` (`@Transactional`).
3. `CommentService` appelle `eventServiceClient.getById(id)` (`@RegisterRestClient`) → `event-service` qui applique l'anti-oracle ISSUE-92 (404 si DRAFT/CANCELLED non-créateur). Le 404 est propagé tel quel par `comment-service`.
4. `CommentService` appelle `coOrganizerServiceClient.check(eventId, userId)` → `co-organizer-service` (cascade SCRUM-136).
5. La nouvelle entité Comment est persistée localement.
6. `cdiEvent.fire(CommentCreatedEvent(...))` posté en transaction. Après commit JDBC, le bridge `CommentCreatedKafkaBridge` (`@Observes(during=AFTER_SUCCESS)`) invoque l'`Emitter` qui envoie un message `comments.created` (clé partition = `eventId`).
7. Réponse `201 Created` au client.

---

## Domain Model

Six entités métier (état actuel + planifié) :

```
User ──────────────────────────────────────────────────────┐
 │ (nom, email, faculté, niveau d'étude, bio, avatar,       │
 │  interests, profilePublic, admin)                        │
 │                                                          │
 │ crée 0..*                                                │
 ▼                                                          │
Event ────────────────────────────────────────────────────  │
 │ (titre, description, dates, lieu, catégorie,             │ notifie
 │  bannière, capacité, active, featured, views)            │
 │                                                          │
 ├── 0..* Attendance ─── N←1 User                          │
 │         (status: INTERESTED | ATTENDING)                 │
 │         contrainte unique(userId, eventId)               │
 │                                                          ▼
 ├── 0..* Favorite ───── N←1 User                    Notification
 │                                                    (type, message,
 ├── 0..* Report ──────── N←1 User                    read, eventId)
 │         (reason, status: PENDING|REVIEWED|DISMISSED)
 │
 └── 0..* Notification ── N←1 User
```

### Règles du domaine importantes

- Tout utilisateur authentifié peut créer des événements.
- Un utilisateur a **au plus une participation** par événement (contrainte DB unique).
- Les événements annulés restent visibles avec `active = false` (soft-delete, jamais DELETE physique).
- Un même utilisateur ne peut signaler un même événement qu'**une seule fois**.
- `profilePublic = false` → le profil n'est visible que par l'utilisateur lui-même.
- Le provisionnement du compte (`GET /users/me`) est **idempotent et race-safe** : gestion des conflits de clé unique via `PersistenceException`.
- `GET /users/me` lit les claims d'identité (`sub`, `email`, `name`, `given_name`, `family_name`, `picture`) directement depuis `JsonWebToken` ; aucun appel distant Auth0 `/userinfo` n'est requis pour provisionner ou relire le profil.

---

## Composants par couche (récap post-completion)

Cette section listait l'état Étape-1 (un seul `Service api`). Le code
applicatif est désormais distribué sur/  4 services métiers + notification placeholder /Quarkus — voir la
table « Microservices — endpoints owned » plus haut pour la
ventilation par-service.

### Patterns transversaux (mis en commun via shared libs)

| Pattern | Lib | Consommé par |
|---|---|---|
| `ApiErrorResponse` + factory helpers (`badRequest`/`conflict`/`unprocessable`/`forbidden`/`notFound`) | `shared-api-error` |/  4 services métiers + notification placeholder /|
| Exception Mapper générique pour `WebApplicationException` | `shared-api-error` |/  4 services métiers + notification placeholder /|
| Enums métier (`EventStatus`, `AttendanceStatus`, `EventCategory`, `Faculty`, `CoOrganizerStatus`, `FollowStatus`, `RecurrenceFrequency`, `ReportReason`, `ReportStatus`) | `shared-domain-enums` | 8-12 services chacun |
| DTOs cross-projetés (`UserPublicResponse`, `EventDTO`, `AttendanceDTO`, `EventCoOrganizerDTO`, `CapacitySummary`, `AttendanceSummary`, `FollowCounts`) | `shared-domain-dtos` | 10+ services |
| `ParamConverter`s (Timeframe, AttendanceStatus, EventStatus, …) + `JsonWebTokenLazy` | `shared-jaxrs` |/  4 services métiers + notification placeholder /|
| `ServiceIdentityResource` paramétrisable + health-check helpers | `shared-platform` | 5 services (incl. notification placeholder) |
| `RequestIdFilter` + `RequestIdClientFilter` + `MdcKafkaInterceptor` | `shared-tracing` |/  4 services métiers + notification placeholder /|
| Kafka payload records (`EventLifecycleEvent`, `EventBannedEvent`, `FollowLifecycleEvent`, `CommentCreatedEvent`, `CoOrganizerEvent`) | `shared-kafka-events` | 5 services producteurs + event-service consumer |
| `@PerUserRateLimit` interceptor + state cache | `shared-rate-limit` | 6 services consommateurs (event, user, attendance, comment, favorite, follow) |
| `FileStorageService` S3 | `shared-storage` | 2 services (user, event) |
| `EventCapacity.computeAvailableSpots`, `Auth0IdResolver.resolveUserId` | `shared-domain-projections` | 6 services |

---

## Authentification

Protocole : **OpenID Connect (OIDC)** via Auth0.

```
Frontend → Auth0 /authorize → Page login Auth0
Auth0   → /callback?code=xxx → Frontend
Frontend → Auth0 /oauth/token (code exchange) → access_token + id_token
Frontend → stocker access_token en localStorage
Frontend → GET /api/users/me avec Authorization: Bearer <token>
Quarkus  → quarkus-oidc valide JWT (OIDC Discovery : signature + exp + aud)
Quarkus  → injecte SecurityIdentity → principal.getName() = auth0Id (sub claim)
Quarkus  → injecte JsonWebToken → `UserResource.me()` lit les claims profil localement depuis le JWT
```

**Mode test (`%test`) :** `quarkus.oidc.enabled=false` — injection de sécurité mockée via `@TestSecurity`.

---

## Infrastructure Kubernetes (post-migration)

```
Ingress Nginx (path: /, /s3/*)
  ├── Service web (ClusterIP:80)         → Pod web (Nginx + React SPA)
  │      └── reverse proxy /api/*        → Service kong-proxy
  ├── Service kong-proxy (ClusterIP:8000)→ 2 pods Kong DB-less
  │      └── route /api/<rule>           → Service <svc>-service:8080
  │                                         (5 services métiers consolidés post-Étape 2)
  ├── Service kafka (ClusterIP:9092)     → Pod kafka (KRaft, 1 replica)
  │      └── 10 topics provisionnés
  ├── Service db (ClusterIP:5432)        → Pod db (PostgreSQL 16, 1 PVC)
  └── Service minio (ClusterIP:9000)     → Pod minio (S3 compat, 1 PVC)
```

Secrets K8s : `ghcr-secret` (accès GHCR), `app-secrets` (DB / OIDC /
S3 credentials, Doppler-synced), TZ=`Europe/Zurich` injecté par
Deployment env.

---

## Tâches planifiées (Scheduled Jobs)

Le backend exécute des jobs de fond via `quarkus-scheduler`.

| Classe | Service | Cron | Réplicas | Rôle |
|---|---|---|---|---|
| `EventExpirationJob` | `event-service` | `0 0 * * * ?` (toutes les heures) | 1 strict | Marque `EXPIRED` les events publiés dont `endDate` est passé. Émet `events.expired` Kafka post-commit. |
| `ModerationCleanupJob` | `moderation-service` | `0 0 3 * * ?` (03h00 chaque nuit) | 1 strict | Auto-bannit les events dépassant le seuil de signalements `PENDING`. Émet `events.banned` Kafka — event-service consomme et applique `event.status = BANNED` localement. |

### ModerationCleanupJob / ModerationCleanupService (moderation-service)

Déclenchement quotidien à 03h00. Délègue à `ModerationCleanupService.runCleanup()` :

1. Requête JPA : paires `(eventId, nbSignalementsEnAttente)` pour les rapports `PENDING`.
2. Filtre Java : retient les eventIds dont le compte ≥ `app.moderation.auto-hide-threshold` (défaut : 3).
3. Mutation Kafka (post-commit, via `cdiEvent.fire(EventBannedEvent.banned(...))`) — pas de mutation cross-schema sur la table `events` (les anciens stubs JPA ont été supprimés en complétion Étape 5).
4. Log INFO : `ModerationCleanup: {n} event(s) auto-banned. IDs: [...]`.
5. event-service consume `events.banned` (`@Incoming`, `@Transactional`), idempotent : `if (event.status == BANNED) return;` puis `event.status = BANNED`.

**Configuration :**
```properties
app.moderation.auto-hide-threshold=3
```

---

## CI/CD (GitHub Actions)

**CI (`build.yml`) :** Sur chaque PR
- Backend : `strategy.matrix.service: [share, view, favorite, calendar, follow, comment, co-organizer, attendance, report, stats, me-aggregator, user, event]` — chaque cellule fait `./mvnw -pl services/${{ matrix.service }}-service -am verify` + Sonar avec `sonar.projectKey=unige-pinfo6-2026_unige-events-${{ matrix.service }}-service`. Job parallèle pour les 10 shared libs.
- Frontend : `npm ci` + lint + tests + build Vite + image Docker.
- **Pré-requis** : 13 SonarCloud projects créés côté DevOps (item 1 de [`devops-handoff.md`](devops-handoff.md)).

**CD (`deploy.yml`) :** Après CI verte sur la branche
- `helm upgrade --set image.tag="${{ github.sha }}"` (renommé depuis `image.api.tag` en complétion Étape 12).
- Secrets K8s synchronisés via Doppler (item 6 de [`devops-handoff.md`](devops-handoff.md)).
