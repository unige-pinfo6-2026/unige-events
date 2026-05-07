# Sprint Context — unige-events-api

Dernière mise à jour : 2026-05-07

---

## Sprint 6 — Entité `Follow` + 7 endpoints follow / unfollow / demandes / listes (SCRUM-138) — 2026-05-07

Livré.

Socle backend du graphe social qui débloque SCRUM-141 / 142 / 143 (front S7 — page profil public, FollowButton, modales listes) et anticipe SCRUM-168 (filtre `followedOnly` du feed S9).

- Migration `V14__create_follows.sql` : table de jointure UUID/UUID `(follower_id, followed_id)` avec contrainte unique, FK vers `users(id)` (sans cascade — pattern défensif identique à `Report.reporter`), CHECK constraint sur `status`, index sur `follower_id` et `followed_id`.
- Entité `Follow` (PanacheEntity, Long PK) avec finders statiques dont **`findAcceptedFollowedIds(UUID)`** livré dès maintenant pour éviter à SCRUM-168 (S9) de re-réfléchir à la requête JPQL plus tard.
- Enum `FollowStatus` à 2 valeurs : `PENDING`, `ACCEPTED`. Un reject = DELETE physique de la row (mirror `EventCoOrganizer.DECLINE`) — re-tentative possible sans 409.
- `FollowService` (@ApplicationScoped, @Transactional sur les mutations seulement) avec règles métier : auto-accept si profil cible public, PENDING sinon, 422 `cannot_follow_self`, 409 `already_following` (check applicatif + filet de sécurité unique constraint), 403 sur accept/reject par non-target, 409 `invalid_transition` sur transition non-PENDING, DELETE idempotent.
- `FollowResource` (`/users`) et `FollowRequestResource` (`/follow-requests`) — split en deux Resources pour qu'aucune ne partage son `@Path` racine avec une autre.
- `UserPublicResponse` enrichi : `followerCount`, `followingCount` (long, toujours présents), `followStatus` (nullable, null pour anonymes/self/no-relation). Trois factories : `from(User)` legacy / `from(User, fc, fwc, fs)` enrichie / `fromAnonymous(User)` (zero-init).
- `UserService.getPublicProfile` retourne désormais un `PublicProfileView` (record agrégé `User + 3 compteurs`). Les anonymes prennent un court-circuit qui économise 2 requêtes DB. La règle anti-oracle 404 ISSUE-93 reste inchangée.
- Rate limit `@PerUserRateLimit(name="follows.follow", max=30)` sur `POST /users/{id}/follow` uniquement.
- Notifications de follow (`NEW_FOLLOWER`, `FOLLOW_REQUEST`, `FOLLOW_ACCEPTED`) explicitement hors scope — déléguées à SCRUM-140 / S7 une fois SCRUM-99 (infra Notification) livré.

Tests : 932 verts. JaCoCo 100% lignes sur `Follow`, `FollowStatus`, `FollowDTO`, `PublicProfileView`, `FollowService`, `FollowResource`, `FollowRequestResource`. Sentinels nommément verts : `findAcceptedFollowedIds_returnsOnlyAcceptedUuids` (SCRUM-168), `rejectRequest_followerCanReFollowAfterReject`, `follow_selfFollow_throwsUnprocessable`, `getFollowers_privateProfileNonOwner_returns404_antiOracle`, `getPublicProfile_self_followStatusIsNull`, `getPublicProfile_authNonOwnerWithPending_followStatusIsPending`.

---

## Sprint 7 — `AttendanceDTO` projette `displayName` / `avatarUrl` (fix UUID stats organisateur) — 2026-05-03

Livré.

Sur `/events/:id/stats`, la liste des participants affichait un UUID brut au lieu du nom pour tout user `profilePublic = false`. Cause : le front faisait du N+1 sur `GET /users/{id}` qui renvoie 404 pour les profils privés (hotfix pentest 4.1, anti-oracle). La route `GET /events/{id}/attendees` étant déjà restreinte au créateur ou co-organisateur ACCEPTED (cf. `AttendanceService.getAttendees`), enrichir le DTO ne fuite rien et permet au front de lire le nom directement.

`AttendanceDTO` reçoit deux nouveaux champs `displayName` (toujours présent pour un user existant — initialisé depuis le claim Auth0 `name` à la création) et `avatarUrl` (nullable). `AttendanceService.getAttendees(...)` charge tous les `User` du batch en une seule requête (`User.list("id in ?1", ids)`) pour éviter le N+1 côté serveur. La page détail publique reste inchangée — elle continue d'afficher "Utilisateur anonyme" pour les profils privés via le flux séparé `getPublicUser`.

Tests : `AttendanceServiceCoverageTest` enrichi de 3 cas (factory DTO avec/sans User, `getAttendees` projette le `displayName` même pour profil privé, ligne orpheline retourne `null` sans planter). `AttendanceServiceMock` adapté à la nouvelle signature `from(Attendance, User)`. OpenAPI : champs `displayName` et `avatarUrl` ajoutés au schéma `Attendance`.

---

## Sprint 6 — `EventDTO` enrichi avec compteurs publics (review #90, SCRUM-92) — 2026-05-03

Livré.

- `EventDTO` reçoit deux champs `Long viewCount` / `Long interestedCount` (nullable). Ils sont **renseignés uniquement** sur `GET /events/{id}` (via les helpers `EventService.countViews` / `countInterested`). Tous les autres call sites de `EventDTO.from(...)` (`create`, `update`, `cancel`, `restore`, `publish`, `uploadImage`, `toEventDTOs` pour les listes paginées, `FavoriteService.getFavorites`, `EventSearchService.search`) passent `null, null` — décision volontaire pour éviter des `count(*)` N+1 sur les listes.
- L'endpoint `GET /events/{id}/stats` reste inchangé et **réservé** au créateur ou co-organisateur ACCEPTED. Frontend : la page `/events/:id/stats` continue d'afficher les visualisations avancées (chart + capacity bar + liste des participants).
- OpenAPI mis à jour (`Event.viewCount`, `Event.interestedCount` avec `nullable: true` et description précisant le scope).
- Tests : `EventDTOTest` (2 cas — `null/null` et valeurs renseignées), `EventServiceCoverageTest` (2 cas — `getById` expose les compteurs depuis `EventView`/`Favorite`, retourne `0` quand vide). Tous les call sites tests (`EventServiceMock`, `EventSearchServiceMock`, `FavoriteServiceMock`, `UserResourceTest`, `CoOrganizerDTOTest`) mis à jour.

---

## Sprint 6 — Hotfix sécurité post-pentest (ISSUE-92) — 2026-04-24

Correction du finding **4.12** (Medium) du rapport de pentest du 2026-04-17 :
`GET /api/events/{id}` renvoyait `200` avec le payload complet d'un event `DRAFT`
ou `CANCELLED` à n'importe quel appelant, y compris anonyme. Combiné au finding
**4.15** (IDs séquentiels), n'importe qui pouvait énumérer tous les brouillons et
events annulés de la plateforme avec `for id in 1..1000; do curl .../events/$id; done`.

Fix :
- `EventService.getById(Long, String, boolean)` — signature étendue avec l'`auth0Id`
  de l'appelant et un flag `isAdmin`. Si `event.status != PUBLISHED` et que
  l'appelant n'est ni le créateur ni un admin → `NotFoundException` (→ `404 not_found`).
- `EventResource.getById` reste `@PermitAll` (PUBLISHED doit rester anon-accessible)
  mais lit `identity.isAnonymous()` + `identity.hasRole("ADMIN")` pour transmettre
  au Service.
- Envelope d'erreur identique à une 404 classique (pas de code d'erreur custom) —
  ferme l'oracle d'existence.
- 12 call-sites internes migrés (tests DB-backed + mock unitaire).

**Pas de changement DB.** Pas d'impact frontend (`useEvent` consomme déjà le 404).

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

- [x] `GET /events/{id}/stats` (vues, attendingCount, interestedCount — créateur uniquement) ✅
- [ ] Incrémentation du compteur de vues à chaque `GET /events/{id}` (déduplication userId+eventId)
- [x] **SCRUM-126** — Champs `websiteUrl`, `contactEmail`, `registrationDeadline`, `tags` sur `Event` ; vérification deadline dans `AttendanceService.attend()` (409 `registration_closed`).
- [x] **SCRUM-129** — Renforcement capacité : `WAITLISTED` ajouté à `AttendanceStatus`, verrou pessimiste sur `Event` pour les mutations liées à la capacité, promotion FIFO (`createdAt ASC`) dans `removeAttendance()`, exposition de `availableSpots` (nullable) et `waitlistedCount` sur `EventDTO`. Plus de 409 pour capacité atteinte — placement automatique en WAITLISTED.
- [x] **SCRUM-133** — Anticipé depuis S6 pour **motif de sécurité**. Nouvel endpoint `GET /users/me/events?status=&page=&size=` (identité dérivée du JWT, tri `createdAt DESC`, tous statuts par défaut) + durcissement de `GET /events?organizerId=…` qui force désormais `status=PUBLISHED` (rejet `400 organizer_filter_requires_published` si un autre statut est demandé). Ferme la faille qui permettait à n'importe quel utilisateur authentifié d'énumérer les brouillons d'un autre via `GET /events?organizerId=<uuid>&status=DRAFT`. Migre `useMyEvents` côté frontend sur le nouvel endpoint.
- [x] **SCRUM-131** — Filtre `?tags=` (sémantique OR) sur `GET /api/events/search`. Clause JPQL `EXISTS` sur la collection `event_tags`, normalisation lowercase via `EventService.normalizeTags`. Multi-valeurs `?tags=a&tags=b`, blank/null filtrés silencieusement.
  - Amélioration : match substring case-insensitive (ex. `?tags=foot` matche `football` ou `barefoot-running`) via `LOWER(t) LIKE :tagN ESCAPE '|'` construit dynamiquement ; les wildcards SQL `%` et `_` saisis par l'utilisateur sont échappés pour être traités littéralement.

---

## Sprint 6 (planifié : 10–24 avril 2025)

**Objectif :** Administration & Modération.

- [ ] Champ `admin` (boolean) sur `User` + `@RolesAllowed("admin")` sur endpoints sensibles
- [ ] Entité `Report` (reporterId, eventId, reason, status PENDING|REVIEWED|DISMISSED)
- [ ] `POST /events/{id}/report`
- [ ] `GET /admin/reports`, `PUT /admin/reports/{id}`, `PUT /admin/events/{id}/feature`
- [x] **ISSUE-93** — Hotfix sécurité post-pentest (2026-04-24) sur `GET /users/{id}`.
  Correction des findings **4.1** (user-existence oracle via `403` vs `404`) et **4.1b**
  (harvest anonyme des profils opt-in, GDPR-relevant) du rapport de pentest du 2026-04-17.
  - `UserService.getPublicProfile(UUID id, String auth0Id)` — signature étendue. Si
    `profilePublic=false` et que l'appelant n'est pas le propriétaire (self-case sur
    `auth0Id`), throw `NotFoundException` (→ `404 not_found`, envelope identique à un
    UUID inexistant). Ferme l'oracle exploité via `creatorId` leaké par `GET /events`.
  - `UserResource.getProfile` reste `@PermitAll` mais lit `identity.isAnonymous()` pour
    choisir entre `UserPublicResponse.from(user)` (full, authentifié) et
    `UserPublicResponse.fromAnonymous(user)` (réduit : `id` + `displayName` + `avatarUrl`).
  - Nouvelle factory `UserPublicResponse.fromAnonymous(User)` — ne projette que 3 champs
    sur 8. Les 5 autres sont `null` et conformes au schéma (tous `nullable: true`).
  - 5 call-sites internes migrés (1 prod + 3 coverage tests + 1 mock override).
  - Pas de changement DB. Pas d'impact frontend — `ProfilePage.tsx` dégrade gracieusement.

---

## Sprint 7 (planifié : 24 avril – 8 mai 2025)

**Objectif :** Notifications, duplication, expiration automatique, polish UI.

- [ ] Entité `Notification` (userId, eventId, type, message, read)
- [ ] `GET /notifications`, `PUT /notifications/{id}/read`
- [ ] `POST /events/{id}/duplicate` (réservé au créateur)
- [ ] Job `@Scheduled` : désactivation auto des events dont `endDate < now()`
- [x] **SCRUM-136** — Co-organisateurs : entité `EventCoOrganizer` (eventId, userId,
      status PENDING/ACCEPTED/DECLINED, invitedAt, contrainte unique `(event_id, user_id)`,
      indexes `idx_event_co_organizers_event` / `idx_event_co_organizers_user`) +
      6 endpoints REST (`POST/GET /events/{id}/co-organizers`,
      `DELETE /events/{id}/co-organizers/{userId}`,
      `PATCH /events/{id}/co-organizers/me/accept|decline`,
      `GET /users/me/co-organizer-invitations`).
      Cascade d'autorisation `isCreatorOrAcceptedCoOrganizer` sur `EventService.update/cancel/
      restore/publish/uploadImage/getById`, `AttendanceService.getAttendees`,
      `EventStatsService.getStats`. `EventService.delete` reste strict-creator (action
      irréversible — divergence assumée par rapport au libellé du ticket Jira).
      DECLINE supprime physiquement la row pour autoriser la ré-invitation sans 409.
      Hors scope : notifications email, transfert d'ownership, invitation par email,
      bulk invite. Frontend SCRUM-137 dépendant.
      *Fix de review post-merge main :* migration `V8__create_event_co_organizers.sql`
      (Flyway désormais source du schéma), `POST /co-organizers` sur body absent → 400
      via `@NotNull`, et `PATCH /me/accept|decline` sans row → 422
      `no_pending_invitation` au lieu de 404.
- [x] **SCRUM-94** — Modération : enrichissement de l'entité `Report` (livrée par
      SCRUM-103) avec l'enum `ReportReason` (SPAM/INAPPROPRIATE/FAKE/OTHER), `description`
      (renommée depuis l'ancienne colonne `reason` libre), `moderationNote`, `reviewedAt`,
      `reviewedBy`. Migration `V10__add_report_reason_and_review_fields.sql` (Hibernate en
      `validate` : Flyway obligatoire — la mention « mode update » du libellé Jira est
      obsolète depuis SCRUM-164). 3 endpoints : `POST /api/events/{id}/report`
      (`@Authenticated`), `GET /api/admin/reports` (paginé, défaut `status=PENDING`,
      tri `createdAt DESC`), `PATCH /api/admin/reports/{id}` (`@RolesAllowed("ADMIN")`,
      transitions `PENDING → REVIEWED|DISMISSED` + audit `reviewedAt`/`reviewedBy`).
      **Pas de champ `admin: boolean` sur `User`** — rôle géré exclusivement via la claim
      Auth0 (`identity.hasRole("ADMIN")` + `@RolesAllowed`). Le TODO `admin` du schéma
      `User` dans openapi.yaml a été retiré, et la section dédiée d'AGENTS.md a été
      remplacée par une note sur la gestion via claim. La cascade SCRUM-136
      (`isCreatorOrAcceptedCoOrganizerPublic`) interdit le self-report d'un event où
      l'on est créateur ou co-organisateur ACCEPTED (422 `cannot_report_own_event`) ;
      un co-organisateur PENDING peut signaler (sentinel cascade). `ModerationCleanupService`
      (SCRUM-103) reste insensible — il ne lit que `r.event` et `r.status`. Hors scope :
      auto-cancel d'event au passage en REVIEWED, bulk-handle, notifications. Frontend
      SCRUM-96 (modale) et SCRUM-97 (dashboard admin) dépendants — attention au rename
      de schéma OpenAPI `ReportRequest → CreateReportRequest`.

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
