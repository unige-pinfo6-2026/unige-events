# Architecture — unige-events-api

## Vue d'ensemble microservices

UNIGE Events est décomposé en **3 services** déployés indépendamment dans Kubernetes (namespace `unige-events`) :

| Service | Repo | Image | Tech |
|---|---|---|---|
| **web** | `unige-events-web` | `ghcr.io/.../web:latest` | React 19 / Nginx |
| **api** | `unige-events-api` | `ghcr.io/.../api:latest` | Java 21 / Quarkus 3.32 |
| **db** | défini dans `api/k8s/db.yml` | `postgres:16` | PostgreSQL 16 |

Flux de trafic :
```
Utilisateur → HTTPS → Ingress Nginx
                        ├─ /        → Pod web (Nginx + SPA React)
                        └─ /api/*   → Pod api (Quarkus JVM, port 8080)
                                          └─ JDBC → Pod db (PostgreSQL 16)
```

Auth0 (externe) émet les JWT — le backend les valide via OIDC Discovery sans appel inter-service.

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

## CI/CD (GitHub Actions)

**CI (`ci.yml`) :** Sur chaque PR → main
- API : `./mvnw verify` + build image Docker via `quarkus-container-image-docker` + push GHCR (conditionnel push main)
- Web : `npm ci` + lint + tests + build Vite + Docker build/push GHCR

**CD (`cd.yml`) :** Après CI verte sur main (`workflow_run`)
- `kubectl apply -f k8s/` + rollout restart (timeout 180s)
- Secret `ghcr-secret` créé dynamiquement via `azure/k8s-create-secret`
