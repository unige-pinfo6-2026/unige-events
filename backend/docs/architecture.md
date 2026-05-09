# Architecture — unige-events backend

> **Sprint 8 — migration vers microservices LIVRÉE** (commits `b858196`
> → `b570c1b`). 13 microservices Quarkus extraits + legacy-monolith
> supprimé + Kong gateway + Kafka broker provisionné.
> Plan archivé : [`specs_archives/specs_claude/specs_microservices_migration.md`](../../specs_archives/specs_claude/specs_microservices_migration.md).
> Détail des PRs d'extraction : [`microservices-migration-roadmap.md`](microservices-migration-roadmap.md).

## Vue d'ensemble — topologie microservices

UNIGE Events est déployé dans Kubernetes (namespace `unige-events` en prod,
`unige-events-pr-N` en preview). Topologie post-migration :

| Composant | Type | Réplicas (prod / preview) | Rôle |
|---|---|---|---|
| **web** | Deployment | 1 / 1 | React 19 SPA servie par Nginx |
| **kong** | Deployment | 2 / 1 | API Gateway DB-less, route `/api/*` vers le bon service via une table de routes déclarative |
| **kafka** | StatefulSet | 1 / 1 | Broker KRaft single-node, 10 topics provisionnés (producteurs/consommateurs câblés au fil des PRs follow-up) |
| **db** | StatefulSet | 1 / 1 | PostgreSQL 16, schéma `public` partagé entre les 13 services (le découpage en schémas par service est différé, cf. spec décision 8) |
| **minio** | StatefulSet | 1 / 1 | S3 compatible — bucket `unige-events-dev` pour les uploads avatar/banner d'user-service + bannières d'event-service |
| **cloudflared** | Deployment | 1 / 1 | Tunnel preview (mode quick) |
| **13 microservices** | Deployment ×13 | 1 / 1 chacun | Quarkus 3.35, image `ghcr.io/unige-pinfo6-2026/unige-events-<svc>:<sha>`, `quarkus-oidc` pour l'auth Auth0 |

### Microservices — endpoints owned

| Service | @Path racines | Tables possédées | Notes |
|---|---|---|---|
| **share-service** | `/events/{id}/share`, `/s/{shortCode}` | aucune (lit `events.share_code`) | Stub Event read-only |
| **view-service** | `/events/{id}/view` | `event_views` | Idempotent upsert via ON CONFLICT |
| **favorite-service** | `/events/{id}/favorite`, `/users/me/favorites` | `favorites` | EventDTO enrichi avec attendance counts |
| **calendar-service** | `/users/me/calendar-token*`, `/calendar/{token}.ics` | aucune (écrit `users.calendar_token`) | RFC 5545 ICS feed, fav ∪ attendances |
| **follow-service** | `/users/{id}/follow*`, `/follow-requests/*`, `/users/me/follow-requests` | `follows` | Cascade ISSUE-93 inlinée, anti-harvest préservé |
| **comment-service** | `/events/{id}/comments`, `/comments/{id}` | `comments` | Cascade ISSUE-92 + SCRUM-136, replies max 1 niveau |
| **co-organizer-service** | `/events/{id}/co-organizers/*`, `/users/me/co-organizer-invitations` | `event_co_organizers` | BFF getMyInvitations enrichit avec EventDTO complet |
| **attendance-service** | `/events/{id}/attend*`, `/users/me/attendances`, `/users/me/participations` | `attendances` | PESSIMISTIC_WRITE pour capacity gating + auto-promotion WAITLISTED |
| **report-service** | `/events/{id}/report`, `/admin/reports*` | `reports` | + ModerationCleanupJob (cron 03:00 Europe/Zurich, replicas:1 strict) |
| **stats-service** | `/events/{id}/stats` | aucune (read-only counters) | 3 counters via 3 stubs (Attendance/Favorite/EventView) |
| **me-aggregator-service** | `/users/me/events` | aucune (BFF) | Cible : fan-out REST clients à PR 16 |
| **user-service** | `/users/me`, `/users/{id}`, `/users/me/image`, `/users/me/banner` | `users`, `user_interests` | Auto-create depuis claims JWT + S3 upload avatar/banner |
| **event-service** | `/events*`, `/admin/events/{id}/{,un}feature`, `/events/search`, `/events/featured`, `/events/{id}/image` | `events`, `event_tags` | + EventExpirationJob (every 1h, replicas:1 strict). 600 lignes de service métier |

### Flux de trafic

```
Utilisateur → HTTPS → Ingress Nginx
                        ├─ /        → Service web (Nginx + SPA React)
                        ├─ /api/*   → Service kong-proxy:8000
                        └─ /s3/*    → Service minio:9000

   Kong DB-less → 13 routes regex anchorées par service (cf.
                  k8s/chart/templates/kong/configmap-routes.yaml)
                  → Service <svc>-service:8080 (Quarkus pod)
                  → Service db:5432 (PostgreSQL 16)
                  + Service kafka:9092 (producteurs/consommateurs)
                  + Service minio:9000 (uploads via user/event-service)
```

Tout le déploiement passe par un chart Helm umbrella unique
(`k8s/chart/`). Chaque service a son sous-template `templates/<svc>/`
(Deployment + Service ClusterIP). `helm upgrade` reçoit
`--set image.api.tag=<github.sha>` qui propage le SHA partagé à tous les
Deployments — chacun construit son nom d'image en
`unige-events-<svc>:<sha>`. Le rename `image.api.tag` → `image.tag`
attend la PR 16 (CI matrix per service).

Auth0 (externe) émet les JWT — chaque service les valide via OIDC
Discovery sans appel inter-service. Kong **ne valide pas** le JWT (cf.
spec décision 7) : il forwarde simplement le header `Authorization:
Bearer <jwt>` vers le service amont qui le revalide localement via
`quarkus-oidc`.

### Notes inter-service (S8)

* **REST clients** : la spec mandate que chaque service appelle ses
  voisins via `@RegisterRestClient` (JAX-RS). En S8 cette plomberie n'est
  pas encore câblée — chaque service utilise des **stubs JPA read-only**
  (`UserStub`, `EventStub`, `AttendanceStub`, ...) qui interrogent le
  même schéma `public` partagé. Le rename en REST clients est une PR de
  cleanup post-migration.
* **Kafka** : 10 topics existent (`events.{published,cancelled,banned,
  expired}`, `users.{followed,follow-requested,follow-accepted}`,
  `comments.created`, `co-organizers.{invited,accepted}`) mais **aucun
  producteur ni consommateur n'est câblé** — les services écrivent
  directement dans le schéma partagé. Le wiring Kafka est une PR de
  follow-up.
* **Rate limiting** : les annotations `@PerUserRateLimit` qui vivaient
  sur `RateLimitInterceptor` du legacy-monolith ont été perdues à
  l'extraction. Restauration via plugin Kong `rate-limiting` ou lib
  partagée à câbler en follow-up.

## Briques d'infrastructure introduites au Sprint 8

* **Kong** ([`k8s/chart/templates/kong/`](../../k8s/chart/templates/kong/))
  — DB-less, 2 replicas prod / 1 preview, ConfigMap `kong-config` porte
  la table de routes déclarative (13 blocs services, un par
  microservice). Plugins globaux : `cors`, `correlation-id` (X-Request-ID
  propagé), `prometheus`.
* **Kafka** ([`k8s/chart/templates/kafka/`](../../k8s/chart/templates/kafka/))
  — KRaft single-broker, PVC sized via values, `clusterId` immutable.
  Job `kafka-topics-init` post-install/upgrade crée les 10 topics figés
  par la spec § 4.5.
* **Multi-module Maven** ([`backend/pom.xml`](../pom.xml)) — parent
  agrégateur, 14 modules enfants (un par microservice après suppression
  de legacy-monolith à step 15).

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

Exemple : `PUT /api/users/me`

1. Le client envoie `PUT /api/users/me` avec `Authorization: Bearer <jwt>` et un body JSON.
2. **Nginx** (pod web) intercepte `/api/*` et proxie vers le pod `api` sur le port 8080.
3. **quarkus-oidc** (filtre automatique) valide le JWT via OIDC Discovery Auth0 (signature + expiration + audience).
4. `UserResource.updateMyProfile()` est invoqué — `SecurityIdentity` injecte le `principal.getName()` = `auth0Id`.
5. Le body est désérialisé en `UpdateProfileRequest` et validé par Hibernate Validator (`@Valid`).
6. `UserService.updateMyProfile()` est appelé via `@Inject` — la logique métier s'exécute `@Transactional`.
7. `User.findByAuth0Id()` (Panache Active Record) génère le SQL via Hibernate/JDBC vers PostgreSQL.
8. L'entité est mutée, le flush est explicite, les conflits optimistic lock sont capturés.
9. `UserProfileResponse.from(user)` convertit l'entité en DTO.
10. Jackson sérialise en JSON camelCase → réponse `200 OK`.

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

## Composants par couche (état actuel)

### Resources (JAX-RS)
- `UserResource` — `@Path("/users")` : `GET /{id}`, `GET /me`, `PUT /me`
- `EventResource` — `@Path("/events")` : `GET`, `POST`

### Services
- `UserService` : provisionnement (first-login), profil public, update profil avec optimistic locking
- `EventService` : `getAll()`, `create()`

### Entités
- `User` extends `PanacheEntityBase<UUID>` — UUID comme PK
- `Event` extends `PanacheEntity` — Long comme PK

### Exception Mappers
- `ConflictExceptionMapper` → 409
- `BadRequestExceptionMapper` → 400
- `NotFoundExceptionMapper` → 404
- `ForbiddenExceptionMapper` → 403
- `UnauthorizedExceptionMapper` → 401
- `ConstraintViolationExceptionMapper` → 400 avec détails par champ

### Configuration
- `OpenApiSecurityConfig` : définit le `SecurityScheme` bearerAuth pour Swagger UI

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

## Infrastructure Kubernetes

```
Ingress Nginx (path: /)
  ├── Service web (ClusterIP:80)  → Pod web (Nginx + React SPA)
  │     └── reverse proxy /api/* → Service api
  ├── Service api (ClusterIP:8080) → Pod api (Quarkus JVM)
  │     └── JDBC → Service db
  └── Service db (ClusterIP:5432) → Pod db (PostgreSQL 16)
        └── PVC 1Gi
```

Secrets K8s : `ghcr-secret` (accès GHCR), `db-secret` (credentials PostgreSQL).

---

## Tâches planifiées (Scheduled Jobs)

Le backend exécute des jobs de fond via `quarkus-scheduler`.

| Classe | Cron | Rôle |
|---|---|---|
| `ModerationCleanupJob` | `0 0 3 * * ?` (03h00 chaque nuit) | Masque automatiquement les événements dépassant le seuil de signalements en attente |

### ModerationCleanupJob / ModerationCleanupService

Déclenchement quotidien à 03h00. Délègue toute la logique à `ModerationCleanupService.runCleanup()` :

1. Requête JPA : récupère les paires `(Event, nbSignalementsEnAttente)` pour tous les rapports `PENDING`.
2. Filtre Java : retient les événements dont le compte ≥ `app.moderation.auto-hide-threshold` (défaut : 3).
3. Mutation : passe le `status` de chaque événement sélectionné à `CANCELLED`.
4. Log INFO : `ModerationCleanup: {n} event(s) auto-hidden. IDs: [...]`

**Configuration :**
```properties
app.moderation.auto-hide-threshold=3
```

---

## CI/CD (GitHub Actions)

**CI (`ci.yml`) :** Sur chaque PR → main
- API : `./mvnw verify` + build image Docker via `quarkus-container-image-docker` + push GHCR (conditionnel push main)
- Web : `npm ci` + lint + tests + build Vite + Docker build/push GHCR

**CD (`cd.yml`) :** Après CI verte sur main (`workflow_run`)
- `kubectl apply -f k8s/` + rollout restart (timeout 180s)
- Secret `ghcr-secret` créé dynamiquement via `azure/k8s-create-secret`
