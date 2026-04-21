# Sprint Context — unige-events-api

Dernière mise à jour : 2026-04-15

---

## Sprint 1 — TERMINÉ (6–13 mars 2025)

**Objectif :** Authentification complète (Auth0/OIDC) + base du profil utilisateur + architecture full-stack.

### Ce qui est implémenté

- **Intégration Auth0/OIDC** : `quarkus-oidc` configuré en mode `service`, validation JWT automatique, désactivé en `%test`.
- **Entité `User`** : UUID comme PK, champs `auth0Id`, `email`, `displayName`, `firstName`, `lastName`, `faculty`, `studyLevel`, `bio`, `interests`, `avatarUrl`, `profilePublic`, `createdAt`, `version` (optimistic locking).
- **Provisionnement first-login** : `UserService.getOrCreateUser()` — idempotent, race-safe (gestion des conflits `PersistenceException` + retry).
- **Mise à jour OIDC/auth (2026-03-29)** : `GET /users/me` lit désormais les claims profil (`email`, `name`, `given_name`, `family_name`, `picture`) directement depuis le JWT via `JsonWebToken`, au lieu de déclencher un appel Auth0 `/userinfo`.
- **Endpoints profil** :
  - `GET /users/me` — profil complet de l'utilisateur connecté
  - `PUT /users/me` — mise à jour partielle du profil (champs optionnels, `@Valid`, retourne l'objet complet)
  - `GET /users/{id}` — profil public (si `profilePublic = true`, sinon 403)
- **DTOs** : `UserProfileResponse`, `UserPublicResponse`, `UpdateProfileRequest`
- **Exception mappers** : 6 mappers (409, 400, 404, 403, 401, `ConstraintViolationException`)
- **Architecture en couches** : Resource → Service → Entity validée, constructor injection, encapsulation des entités.
- **Configuration OpenAPI** : `OpenApiSecurityConfig` pour le bearer JWT.

### Bugs connus / comportements à surveiller

- `GET /users/me` : si le claim `email` est absent du JWT, une `NotAuthorizedException` est levée → retourne 401. Ce comportement est **correct et intentionnel** selon la spec. À documenter côté frontend.
- `GET /users/me` : l'injection de `UserInfo` n'est plus utilisée. Le flux ne dépend plus implicitement de `user-info-required`, ce qui supprime les appels Auth0 `/userinfo`, évite les rate limits Auth0 et élimine les 401 en cascade observés sur les requêtes authentifiées.
- `PUT /users/me` : retourne `200` avec l'objet `UserProfileResponse` complet — **pas de `204`**. Le frontend doit utiliser cette réponse pour mettre à jour son état sans refetch.
- Hibernate tourne en mode `update` — choix définitif pour ce projet.

---

## Sprint 2 — EN COURS (13–20 mars 2025)

**Objectif :** Création, édition et suppression d'événements (rôle Organisateur). Premières briques du listing public.

### État actuel

- **Entité `Event`** : complète — `id` (Long, PK Panache), `title`, `description`, `location`, `startDate`, `endDate`, `category` (enum), `bannerUrl`, `capacity`, `status` (enum, default `DRAFT`), `createdAt`, `updatedAt`, `creator` (@ManyToOne LAZY → `User`).
- **`EventDTO`** : record avec factory `EventDTO.from(Event)` — expose `creatorId` (UUID) sans relation JPA.
- **`EventResource`** : CRUD complet — `GET /events` (paginé + filtres), `POST /events` (@Authenticated, creator lié), `GET /events/{id}`, `PUT /events/{id}` (créateur uniquement), `DELETE /events/{id}` (soft-delete, créateur uniquement). Constructor injection.
- **`EventService`** : `getAll(page, size, status, category, organizerId)`, `create(auth0Id, request)`, `getById(id)`, `update(id, auth0Id, request)`, `delete(id, auth0Id)` avec `@Transactional`.
- **Tests** : `EventDTOTest` (unit), `EventResourceTest` (16 tests @QuarkusTest avec `EventServiceMock`), `EventTest` (3 tests @QuarkusTest), `CreateEventRequestTest` (6 tests bean validation), `EventServiceMock` (mock in-memory).

### À faire dans ce sprint

- [x] Enrichir `Event` avec tous les champs planifiés
- [x] Créer un `EventDTO` (ne pas exposer l'entité directement)
- [x] Écrire les tests `@QuarkusTest` pour `EventResource`
- [x] `POST /events` : sécuriser avec `@Authenticated`, lier `creator` à l'utilisateur connecté
- [x] `GET /events/{id}` : détail d'un événement
- [x] `PUT /events/{id}` : modification (créateur uniquement → 403 sinon)
- [x] `DELETE /events/{id}` : soft-delete (status → `CANCELLED`)
- [x] `GET /events` : pagination (`?page=`, `?size=`), filtres `?status=`, `?category=`, `?organizerId=`
- [x] `POST /events` : création directement en `PUBLISHED` (champ `status` optionnel dans `CreateEventRequest`, défaut `DRAFT`)
- [x] `PATCH /events/{id}/publish` : publication d'un événement DRAFT (ORGANIZER créateur ou ADMIN) — 403/404/409
- [x] `POST /events/{id}/image` : upload bannière multipart, stockage local `app.uploads.path`, retourne EventDTO mis à jour — 400 si MIME invalide
- [x] Rôles Auth0 (ADMIN/ORGANIZER/STUDENT) mappés via `quarkus.oidc.roles.role-claim-path`

---

## Sprint 3 (planifié : 20–27 mars 2025)

**Objectif :** Découverte avancée — recherche, filtres, vue calendrier.

- [x] `GET /events/search?q=&category=&dateFrom=&dateTo=` — full-text ILIKE sur titre + description, paginé (SCRUM-76) — `EventSearchResource` + `EventSearchService`
- [x] Ajout du champ `faculty` (enum `Faculty`) sur `Event` + filtre `?faculty=` sur `GET /events` et `GET /events/search` (SCRUM-77) — `Faculty.java`, `Event.faculty`, `EventDTO.faculty`, `EventRequestBase.faculty`, `EventService.getAll()`, `EventSearchService.search()`

---

## Sprint 4 (planifié : 27 mars – 3 avril 2025)

**Objectif :** Engagement & Interaction — inscription, favoris, partage.

- [x] Entité `Favorite` (userId, eventId) — SCRUM-89 ✅
- [x] `POST /events/{id}/favorite`, `DELETE /events/{id}/favorite`, `GET /users/me/favorites` — SCRUM-89 ✅
- [x] `GET /events/{id}/share` + `GET /s/{shortCode}` (shortlink redirect) — SCRUM-89 ✅
- [x] `GET /users/me/calendar-token`, `DELETE /users/me/calendar-token`, `GET /calendar/{calendarToken}.ics` — SCRUM-89-bis ✅
- [x] Entité `Attendance` (userId, eventId, status) + endpoints — SCRUM-88 ✅
  - `POST /events/{id}/attend` (upsert ATTENDING)
  - `DELETE /events/{id}/attend` (désinscription)
  - `GET /events/{id}/attendees` (créateur uniquement)
  - `GET /users/me/attendances`
- [x] Suppression du statut `INTERESTED` — correctif backend ✅
  - `AttendanceStatus` réduit à `ATTENDING` (INTERESTED redondant avec les favoris)
  - `CalendarService.generateIcsFeed` : flux ICS = Favoris ∪ ATTENDING (PUBLISHED, dédupliqués)
  - `EventDTO` : champ `interestedCount` supprimé

### Fixes PR #41 (post-review lead technique)
- [x] Fix 1 — NPE body null `POST /events/{id}/attend` → `@NotNull` sur paramètre
- [x] Fix 2 — Inscription sur event DRAFT bloquée → 400 `BadRequestException`
- [x] Fix 3 — `DELETE /me/calendar-token` → `POST /me/calendar-token/regenerate`
- [x] Fix 4 — `frontendUrl` centralisé dans `AppConfig` (défaut `http://localhost:5173`)
- [x] Fix 5 — `buildIcsContent`/`foldLine`/`escapeIcs` extraits dans `util/IcsBuilder`
- [x] Fix 6 — Conversion UTC → Europe/Zurich dans `IcsBuilder.buildIcsContent`
- [x] Fix 7 — `%dev.quarkus.http.host=0.0.0.0` dans `application.properties`

---

## Sprint 5 (planifié : 3–10 avril 2025)

**Objectif :** Statistiques organisateur + liste des participants + enrichissement Event.

- [ ] `GET /events/{id}/stats` (vues, attendingCount — créateur/admin uniquement)
- [x] Tracking de vues via POST /events/{id}/view — entité EventView (eventId, userId, viewedAt), contrainte unique (eventId, userId), service upsert idempotent ✅
- [x] **SCRUM-126** — Champs `websiteUrl`, `contactEmail`, `registrationDeadline`, `tags` sur `Event` ; vérification deadline dans `AttendanceService.attend()` (409 `registration_closed`).
- [x] **SCRUM-129** — Renforcement capacité : `WAITLISTED` ajouté à `AttendanceStatus`, verrou pessimiste sur `Event` pour les mutations liées à la capacité, promotion FIFO (`createdAt ASC`) dans `removeAttendance()`, exposition de `availableSpots` (nullable) et `waitlistedCount` sur `EventDTO`. Plus de 409 pour capacité atteinte — placement automatique en WAITLISTED.
- [x] **SCRUM-133** — Anticipé depuis S6 pour **motif de sécurité**. Nouvel endpoint `GET /users/me/events?status=&page=&size=` (identité dérivée du JWT, tri `createdAt DESC`, tous statuts par défaut) + durcissement de `GET /events?organizerId=…` qui force désormais `status=PUBLISHED` (rejet `400 organizer_filter_requires_published` si un autre statut est demandé). Ferme la faille qui permettait à n'importe quel utilisateur authentifié d'énumérer les brouillons d'un autre via `GET /events?organizerId=<uuid>&status=DRAFT`. Migre `useMyEvents` côté frontend sur le nouvel endpoint.

---

## Sprint 6 (planifié : 10–24 avril 2025)

**Objectif :** Administration & Modération.

- [ ] Champ `admin` (boolean) sur `User` + `@RolesAllowed("admin")` sur endpoints sensibles
- [ ] Entité `Report` (reporterId, eventId, reason, status PENDING|REVIEWED|DISMISSED)
- [ ] `POST /events/{id}/report`
- [ ] `GET /admin/reports`, `PUT /admin/reports/{id}`, `PUT /admin/events/{id}/feature`

---

## Sprint 7 (planifié : 24 avril – 8 mai 2025)

**Objectif :** Notifications, duplication, expiration automatique, polish UI.

- [ ] Entité `Notification` (userId, eventId, type, message, read)
- [ ] `GET /notifications`, `PUT /notifications/{id}/read`
- [ ] `POST /events/{id}/duplicate` (réservé au créateur)
- [ ] Job `@Scheduled` : désactivation auto des events dont `endDate < now()`

---

## Sprint 8 (planifié : 8–22 mai 2025)

**Objectif :** Tests, scalabilité, sécurité, CD, soutenance.

- [ ] Tests d'intégration `@QuarkusTest` couverture >80% sur EventResource, UserResource
- [ ] Audit OWASP Top 10, CORS configuré, secrets en env vars
- [ ] Tests E2E Playwright/Cypress (3–5 scénarios critiques)
- [ ] CD pipeline opérationnel (Kubernetes deploy automatique)
- [ ] Préparation soutenance

---

## Dette technique connue

| Item | Priorité | Sprint cible |
|---|---|---|
| Schéma géré par Hibernate `update` — choix définitif | Info | Sprint 2 ✅ |
| Sécuriser `POST /events` avec `@Authenticated` | Haute | Sprint 2 ✅ |
| Remplacer exposition directe de l'entité `Event` par un DTO | ✅ Fait | Sprint 2 |
| Tests unitaires sur `UserService` | Moyenne | Sprint 2 |
