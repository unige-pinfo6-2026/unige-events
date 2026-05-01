# API Contract — unige-events-api

Root path : `/api` (configuré via `quarkus.http.root-path=api` dans `application.properties`)

Tous les endpoints produisent et consomment `application/json`.
Les endpoints authentifiés requièrent `Authorization: Bearer <jwt>` (Auth0/OIDC).

---

## Endpoints implémentés

| Méthode | Path | Auth | Description | Codes HTTP |
|---|---|---|---|---|
| `GET` | `/users/{id}` | `@PermitAll` | Profil public d'un utilisateur — **payload réduit pour anon**, 404 si privé ou non autorisé (pas d'oracle d'existence) | 200, 404 |
| `GET` | `/users/me` | `@Authenticated` | Profil complet de l'utilisateur connecté (provisionne le compte au 1er appel) | 200, 401 |
| `PUT` | `/users/me` | `@Authenticated` | Mise à jour du profil de l'utilisateur connecté | 200, 400, 401, 403, 404, 409 |
| `GET` | `/events` | `@PermitAll` | Liste paginée — filtres : status, category, organizerId, endDateFrom (date-time), faculty, facultyNone (mutex avec faculty) | 200 |
| `POST` | `/events` | `@Authenticated` | Créer un événement | 201 |
| `GET` | `/events/{id}` | `@PermitAll` | Détail d'un événement — **DRAFT/CANCELLED cachés** (créateur ou admin uniquement, sinon 404) | 200, 404 |
| `GET` | `/events/search` | `@PermitAll` | Recherche full-text (q, category, faculty, facultyNone, tags [substring match case-insensitive], dateFrom, dateTo, page, size) | 200 |
| `POST` | `/events/{id}/favorite` | `@Authenticated` | Ajouter aux favoris (idempotent — 200 même si déjà favori) | 200, 401, 404 |
| `DELETE` | `/events/{id}/favorite` | `@Authenticated` | Retirer des favoris | 204, 401, 404 |
| `GET` | `/users/me/favorites` | `@Authenticated` | Liste paginée des événements favoris | 200, 401 |
| `GET` | `/events/{id}/share` | `@Authenticated` | Obtenir shareUrl + shortCode (idempotent) | 200, 401, 404 |
| `GET` | `/s/{shortCode}` | `@PermitAll` | Redirection 302 vers la page de l'événement | 302, 404 |
| `GET` | `/users/me/calendar-token` | `@Authenticated` | Token webcal personnel — génère si absent (idempotent) | 200, 401 |
| `POST` | `/users/me/calendar-token/regenerate` | `@Authenticated` | Révoquer et régénérer le token | 200, 401, 404 |
| `GET` | `/calendar/{calendarToken}.ics` | `@PermitAll` | Flux iCalendar : événements en favori + événements ATTENDING (PUBLISHED, dédupliqués) | 200, 404 |
| `POST` | `/events/{id}/attend` | `@Authenticated` | Upsert inscription (ATTENDING) — 400 si event non publié, 409 si capacité pleine | 200, 400, 401, 404, 409 |
| `DELETE` | `/events/{id}/attend` | `@Authenticated` | Se désinscrire | 204, 401, 404 |
| `GET` | `/events/{id}/attendees` | `@Authenticated` | Liste paginée des inscriptions (créateur **ou co-organisateur ACCEPTED**) | 200, 401, 403, 404 |
| `GET` | `/users/me/attendances` | `@Authenticated` | Mes inscriptions (toutes, avec statut) | 200, 401 |
| `POST` | `/events/{id}/co-organizers` | `@Authenticated` | Inviter un co-organisateur (créateur ou ADMIN) | 201, 400, 401, 403, 404, 409 |
| `GET` | `/events/{id}/co-organizers` | `@Authenticated` | Lister les co-organisateurs (PENDING + ACCEPTED) | 200, 401, 404 |
| `DELETE` | `/events/{id}/co-organizers/{userId}` | `@Authenticated` | Retirer un co-organisateur (créateur ou ADMIN) | 204, 401, 403, 404 |
| `PATCH` | `/events/{id}/co-organizers/me/accept` | `@Authenticated` | Accepter sa propre invitation (idempotent) | 200, 401, 422 |
| `PATCH` | `/events/{id}/co-organizers/me/decline` | `@Authenticated` | Décliner sa propre invitation (suppression de la row) | 204, 401, 422 |
| `GET` | `/users/me/co-organizer-invitations` | `@Authenticated` | Mes invitations à co-organiser (default `status=PENDING`) | 200, 401, 404 |

---

## Détail des endpoints

### `GET /users/{id}`

Retourne le profil public d'un utilisateur.

**Règle d'autorisation** (hotfix pentest 2026-04-17, findings 4.1 + 4.1b) :
- `profilePublic=true` : accessible en lecture. **Anon** → payload **réduit** (`id`,
  `displayName`, `avatarUrl` ; autres champs `null`). **Authentifié** → payload **complet**.
- `profilePublic=false` : visible uniquement par son propriétaire (`auth0Id` du JWT
  matche `user.auth0Id`). Sinon → `404 not_found`, envelope identique à celle d'un UUID
  inexistant (ferme l'oracle d'existence exploité via `creatorId` leaké par `GET /events`).

**Paramètre :** `id` — UUID de l'utilisateur.

**Réponses :**
- `200 OK` — `UserPublicResponse` (payload complet ou réduit selon l'authentification)
- `404 Not Found` — utilisateur introuvable, OU profil privé demandé par un appelant non autorisé

---

### `GET /events/{id}`

Détail d'un événement.

**Règle d'autorisation** (hotfix pentest 2026-04-17, findings 4.12 + 4.15) :
- Un événement `PUBLISHED` est accessible **anonymement** (pas de JWT requis).
- Un événement `DRAFT` ou `CANCELLED` n'est visible que par son créateur (JWT dont `sub` matche `event.creator.auth0Id`) ou par un admin (rôle `ADMIN`).
- Sinon : `404 not_found` — envelope identique à celle d'un ID inexistant, pour ne pas créer d'oracle d'existence (pas de distinction « n'existe pas » / « existe mais caché »).

**Paramètre :** `id` — ID numérique de l'événement (`Long` séquentiel).

**Réponses :**
- `200 OK` — `EventDTO` complet (mêmes champs que `GET /events`)
- `404 Not Found` — événement introuvable, OU événement non-PUBLISHED demandé par un appelant non autorisé

---

### `GET /users/me`

Retourne le profil complet de l'utilisateur authentifié. **Provisionne automatiquement le compte** en base à la première connexion (idempotent, race-safe).

**Règle comportementale critique :** Retourne `401` si le token JWT est absent ou invalide — **jamais de body partiel ou null**.

**Réponses :**
- `200 OK` — `UserProfileResponse` (profil complet)
- `401 Unauthorized` — token absent, invalide ou claim `email` manquant

---

### `PUT /users/me`

Met à jour le profil de l'utilisateur authentifié. Tous les champs du body sont optionnels — seuls les champs fournis sont modifiés.

**Règle comportementale critique :** Retourne l'objet `User` complet mis à jour en `200` — **pas de `204 No Content`**. Le frontend se met à jour sans refetch supplémentaire.

**Body :** `UpdateProfileRequest` (tous les champs nullable)

```json
{
  "displayName": "Alice Martin",
  "faculty": "Sciences",
  "studyLevel": "Master",
  "bio": "Étudiante en informatique",
  "interests": ["tech", "music"],
  "avatarUrl": "https://example.com/avatar.png",
  "profilePublic": true
}
```

**Réponses :**
- `200 OK` — `UserProfileResponse` complet mis à jour
- `400 Bad Request` — validation échouée (body malformé ou contrainte violée) — `ValidationErrorResponse`
- `401 Unauthorized` — token absent ou invalide
- `403 Forbidden` — tentative de modification du profil d'un autre utilisateur
- `404 Not Found` — utilisateur introuvable en base
- `409 Conflict` — conflit d'écriture concurrent (optimistic lock) — `ApiErrorResponse`

---

### `GET /events`

Retourne la liste de tous les événements.

**Réponses :**
- `200 OK` — `List<EventDTO>`

---

### `POST /events`

Crée un nouvel événement.

**Body :** `CreateEventRequest` — champs requis : `title`, `location`, `startDate` (@Future), `endDate`, `category`

**Réponses :**
- `201 Created` — `EventDTO` créé avec son `id`
- `400 Bad Request` — validation échouée — `ValidationErrorResponse`

---

### Co-organisateurs (SCRUM-136)

Six endpoints exposent le cycle d'invitation à co-organiser un événement. Toutes les opérations
sont sous `@Authenticated`. Le créateur ou un admin peut inviter / retirer ; l'invité peut
accepter / décliner ; tout authentifié peut lister les co-organisateurs d'un event.

#### `POST /events/{id}/co-organizers`

Crée une invitation `PENDING`. Réservé au créateur de l'événement ou à un admin.

**Body :** `InviteCoOrganizerRequest` — `{ "userId": "<uuid>" }` (utilisateur cible déjà provisionné).

**Réponses :**
- `201 Created` — `CoOrganizerDTO` (status PENDING)
- `400 Bad Request` — body absent ou body sans `userId` → `ValidationErrorResponse` ; `error=cannot_invite_self` si le créateur tente de s'inviter
- `401 Unauthorized` — token absent
- `403 Forbidden` — appelant non créateur et non admin
- `404 Not Found` — événement OU utilisateur cible introuvable
- `409 Conflict` — `error=already_invited` (couple `(eventId, userId)` existe déjà, PENDING ou ACCEPTED)

#### `GET /events/{id}/co-organizers`

Liste les co-organisateurs d'un événement (PENDING + ACCEPTED), tri `invitedAt ASC`. Les rows
DECLINED sont supprimées physiquement (cf. `PATCH /me/decline` ci-dessous).

**Réponses :**
- `200 OK` — `List<CoOrganizerDTO>` (tableau vide si aucun ; jamais 404 si l'event existe)
- `401 Unauthorized`
- `404 Not Found` — événement introuvable

#### `DELETE /events/{id}/co-organizers/{userId}`

Retire un co-organisateur (peu importe son statut). Idempotent : si la row n'existe pas,
retourne 204 sans erreur.

**Réponses :**
- `204 No Content`
- `401 Unauthorized`
- `403 Forbidden` — appelant non créateur et non admin
- `404 Not Found` — événement introuvable (404 pour distinguer un mauvais path d'un user déjà retiré)

#### `PATCH /events/{id}/co-organizers/me/accept`

L'invité accepte sa propre invitation. Bascule `PENDING → ACCEPTED`. Idempotent : si déjà
ACCEPTED, retourne 200 sans modification. Self-only — un admin ne peut pas accepter pour autrui.

À partir de l'acceptation, le co-organisateur peut éditer / publier / annuler / restaurer /
charger une bannière / consulter les inscrits / les stats de l'événement (cascade
`isCreatorOrAcceptedCoOrganizer` dans `EventService`, `AttendanceService.getAttendees`,
`EventStatsService.getStats`).

**Réponses :**
- `200 OK` — `CoOrganizerDTO` (status ACCEPTED)
- `401 Unauthorized`
- `422 Unprocessable Entity` — `error=no_pending_invitation` : aucune invitation pour l'utilisateur courant sur cet événement

#### `PATCH /events/{id}/co-organizers/me/decline`

L'invité décline. **Supprime physiquement la row** (cf. décision spec : DECLINE = DELETE row,
permet une ré-invitation ultérieure sans 409). Self-only.

**Réponses :**
- `204 No Content`
- `401 Unauthorized`
- `422 Unprocessable Entity` — `error=no_pending_invitation` : aucune invitation

#### `GET /users/me/co-organizer-invitations`

Liste les invitations adressées à l'utilisateur courant. Par défaut `status=PENDING`. Chaque
entrée enrichit l'`Event` complet (titre, dates, banner) pour éviter les N+1 réseau côté frontend.

**Query params :**
- `status` (optionnel) — `PENDING | ACCEPTED | DECLINED`. Default `PENDING`. `DECLINED` ne
  renvoie jamais rien (les rows DECLINED sont supprimées physiquement).
- `page` (default `0`, min `0`)
- `size` (default `20`, min `1`, max `100`)

**Réponses :**
- `200 OK` — `List<CoOrganizerInvitationDTO>`
- `401 Unauthorized`
- `404 Not Found` — profil utilisateur non provisionné (appeler `GET /users/me` d'abord)

#### Cascade « créateur ou co-organisateur ACCEPTED »

Le helper `EventService.isCreatorOrAcceptedCoOrganizerPublic(Event, String auth0Id)` (et son
équivalent privé) étend la garde d'autorisation à un co-organisateur ACCEPTED pour les
opérations suivantes :

| Endpoint impacté | Cascade appliquée ? |
|---|---|
| `PUT /events/{id}` | ✅ |
| `PATCH /events/{id}/cancel` | ✅ |
| `PATCH /events/{id}/restore` | ✅ |
| `PATCH /events/{id}/publish` | ✅ (avec ADMIN aussi) |
| `POST /events/{id}/image` | ✅ (avec ADMIN aussi) |
| `GET /events/{id}` (DRAFT/CANCELLED) | ✅ |
| `GET /events/{id}/attendees` | ✅ |
| `GET /events/{id}/stats` | ✅ |
| `DELETE /events/{id}` (hard-delete) | ❌ — strict-creator (action irréversible) |

---

## Exception mappers

| Exception Java | Code HTTP | Corps de réponse |
|---|---|---|
| `OptimisticLockException` | `409 Conflict` | `ApiErrorResponse(error="conflict")` |
| `BadRequestException` | `400 Bad Request` | `ValidationErrorResponse` |
| `NotFoundException` | `404 Not Found` | `ApiErrorResponse(error="not_found")` |
| `ForbiddenException` | `403 Forbidden` | `ApiErrorResponse(error="forbidden")` |
| `NotAuthorizedException` | `401 Unauthorized` | `ApiErrorResponse(error="unauthorized")` |
| `ConstraintViolationException` | `400 Bad Request` | `ValidationErrorResponse` avec détails par champ |

---

## Endpoints planifiés (non implémentés)

| Méthode | Path | Sprint | Description |
|---|---|---|---|
| `GET` | `/events/{id}` | Sprint 2 | Détail d'un événement |
| `PUT` | `/events/{id}` | Sprint 2 | Modifier un événement (créateur/admin) |
| `DELETE` | `/events/{id}` | Sprint 2 | Soft-delete (`active = false`) |
| `POST` | `/events/{id}/favorite` | Sprint 4 | Ajouter aux favoris |
| `DELETE` | `/events/{id}/favorite` | Sprint 4 | Retirer des favoris |
| `GET` | `/users/me/favorites` | Sprint 4 | Favoris de l'utilisateur connecté |
| `GET` | `/events/{id}/stats` | Sprint 5 | Stats organisateur (vues, inscriptions) |
| `POST` | `/events/{id}/report` | Sprint 7 (SCRUM-94) | Signaler un événement (`@Authenticated`) — 201 / 400 (`cannot_report_draft`, `cannot_report_cancelled`) / 401 / 404 / 409 (`already_reported`) / 422 (`cannot_report_own_event`) |
| `GET` | `/admin/reports` | Sprint 7 (SCRUM-94) | Liste paginée des signalements (`@RolesAllowed("ADMIN")`, défaut `status=PENDING`) — 200 / 401 / 403 |
| `PATCH` | `/admin/reports/{id}` | Sprint 7 (SCRUM-94) | Traiter un signalement (`PENDING → REVIEWED\|DISMISSED`, `@RolesAllowed("ADMIN")`) — 200 / 400 (`invalid_status`) / 401 / 403 / 404 / 409 (`invalid_transition`) |
| `PUT` | `/admin/events/{id}/feature` | Sprint 6 | Mettre en avant (admin) |
| `POST` | `/events/{id}/duplicate` | Sprint 7 | Dupliquer un événement (créateur) |
| `GET` | `/notifications` | Sprint 7 | Notifications de l'utilisateur |
| `PUT` | `/notifications/{id}/read` | Sprint 7 | Marquer comme lu |
