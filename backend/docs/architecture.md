# Architecture — unige-events-api

> **Sprint 8 — migration vers microservices en cours.** Cette doc décrit l'état
> **actuel** (monolithe + briques infra Kong/Kafka). La cible et le plan
> d'extraction par service vivent dans
> [`specs_archives/specs_claude/specs_microservices_migration.md`](../../specs_archives/specs_claude/specs_microservices_migration.md).
> L'état est mis à jour incrémentalement à mesure que les services sont extraits.

## Vue d'ensemble (S8 état courant — strangler-fig)

UNIGE Events est déployé dans Kubernetes (namespace `unige-events` en prod,
`unige-events-pr-N` en preview). Topologie courante :

| Composant | Type | Image | Rôle |
|---|---|---|---|
| **web** | Deployment | `ghcr.io/.../unige-events-web:<sha>` | React 19 SPA, servie par Nginx |
| **kong** | Deployment | `kong:3.7.0` | API Gateway DB-less, route 100 % de `/api/*` vers `api:8080` (catch-all en attendant les flips) |
| **api** | Deployment | `ghcr.io/.../unige-events-api:<sha>` | Quarkus monolith — encore propriétaire de tout le code applicatif |
| **kafka** | StatefulSet | `apache/kafka:3.7.0` | Broker KRaft single-node, 10 topics provisionnés, **pas encore de producteur ni consommateur** |
| **db** | StatefulSet | `postgres:16` | Schéma `public` — partagé tant que les services ne sont pas extraits ; futur découpage en schémas par service (cf. spec décision 8) |
| **minio** | StatefulSet | `minio/minio` | Stockage S3 compatible (uploads bannières / avatars) |
| **cloudflared** | Deployment | `cloudflare/cloudflared` | Tunnel preview (mode quick) |
| **`<svc>`-service × 14** | Deployment (`replicas: 0`) | `ghcr.io/.../unige-events-<svc>:<sha>` (futur) | Squelettes scaffoldés par étape 2..14 — **idle**, ne consomment rien tant que le carve-out n'est pas livré |

Flux de trafic :
```
Utilisateur → HTTPS → Ingress Nginx
                        ├─ /        → Service web (Nginx + SPA React)
                        ├─ /api/*   → Service kong-proxy:8000 ─┐
                        └─ /s3/*    → Service minio:9000        │
                                                                ▼
                                                Kong route catch-all `/api → api:8080`
                                                ▼
                                                Service api:8080 (Quarkus monolith)
                                                ▼
                                                Service db:5432 (PostgreSQL 16)
```

Tous les services sont déployés via le chart Helm umbrella unique (`k8s/chart/`),
versioné via `Chart.yaml`. Les sous-templates `templates/<svc>/` sont
provisionnés à `replicas: 0` — leur bascule à `replicas: 1` est ce que la PR
d'extraction par service livrera.

Auth0 (externe) émet les JWT — le backend les valide via OIDC Discovery sans
appel inter-service. Kong **ne valide pas** le JWT (cf. spec décision 7) : il
forwarde simplement le header `Authorization: Bearer <jwt>` vers le service
amont, qui le revalide localement via `quarkus-oidc`. Cette propriété tient
aujourd'hui (un seul service amont = `api`) et continuera de tenir une fois
les services extraits.

## Briques d'infrastructure ajoutées au S8

* **Kong** ([`k8s/chart/templates/kong/`](../../k8s/chart/templates/kong/)) — DB-less, 2 replicas en prod / 1 en preview, ConfigMap `kong-config` porte la table de routes déclarative (catch-all aujourd'hui ; blocs commentés pour le découpage cible). Plugins globaux : `cors`, `correlation-id` (X-Request-ID propagé), `prometheus`.
* **Kafka** ([`k8s/chart/templates/kafka/`](../../k8s/chart/templates/kafka/)) — KRaft single-broker, PVC sized via values, `clusterId` immutable. Job `kafka-topics-init` post-install/upgrade crée les 10 topics figés par la spec § 4.5 (`events.published`, `events.cancelled`, `events.banned`, `events.expired`, `users.followed`, `users.follow-requested`, `users.follow-accepted`, `comments.created`, `co-organizers.invited`, `co-organizers.accepted`).
* **Multi-module Maven** ([`backend/pom.xml`](../pom.xml)) — parent agrégateur, 15 modules enfants. Cf. [`backend/AGENTS.md`](../AGENTS.md) section « Layout Maven ».

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
