# API Contract — unige-events-api

Root path : `/api` (configuré via `quarkus.http.root-path=api` dans `application.properties`)

Tous les endpoints produisent et consomment `application/json`.
Les endpoints authentifiés requièrent `Authorization: Bearer <jwt>` (Auth0/OIDC).

---

## Endpoints implémentés

| Méthode | Path | Auth | Description | Codes HTTP |
|---|---|---|---|---|
| `GET` | `/users/{id}` | `@PermitAll` | Profil public d'un utilisateur | 200, 403, 404 |
| `GET` | `/users/me` | `@Authenticated` | Profil complet de l'utilisateur connecté (provisionne le compte au 1er appel) | 200, 401 |
| `PUT` | `/users/me` | `@Authenticated` | Mise à jour du profil de l'utilisateur connecté | 200, 400, 401, 403, 404, 409 |
| `GET` | `/events` | `@PermitAll` | Liste paginée — filtres : status, category, organizerId, endDateFrom (date-time), faculty, facultyNone (mutex avec faculty) | 200 |
| `POST` | `/events` | `@Authenticated` | Créer un événement | 201 |
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
| `GET` | `/events/{id}/attendees` | `@Authenticated` | Liste paginée des inscriptions (créateur uniquement) | 200, 401, 403, 404 |
| `GET` | `/users/me/attendances` | `@Authenticated` | Mes inscriptions (toutes, avec statut) | 200, 401 |

---

## Détail des endpoints

### `GET /users/{id}`

Retourne le profil public d'un utilisateur si `profilePublic = true`.

**Paramètre :** `id` — UUID de l'utilisateur.

**Réponses :**
- `200 OK` — `UserPublicResponse` (id, displayName, faculty, studyLevel, bio, interests, avatarUrl)
- `403 Forbidden` — profil privé (`profilePublic = false`)
- `404 Not Found` — utilisateur introuvable

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
| `POST` | `/events/{id}/report` | Sprint 6 | Signaler un événement |
| `GET` | `/admin/reports` | Sprint 6 | Liste des signalements (admin) |
| `PUT` | `/admin/reports/{id}` | Sprint 6 | Modérer un signalement (admin) |
| `PUT` | `/admin/events/{id}/feature` | Sprint 6 | Mettre en avant (admin) |
| `POST` | `/events/{id}/duplicate` | Sprint 7 | Dupliquer un événement (créateur) |
| `GET` | `/notifications` | Sprint 7 | Notifications de l'utilisateur |
| `PUT` | `/notifications/{id}/read` | Sprint 7 | Marquer comme lu |
