# Data Model — unige-events-api

## Entités JPA

### User

Table : `users` (mapping CamelCase → snake_case par Hibernate NamingStrategy)

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `UUID` | `id` | PK, auto-généré (`@GeneratedValue`) |
| `auth0Id` | `auth0Id` | `String` | `auth0_id` | unique, not updatable |
| `email` | `email` | `String` | `email` | unique, not updatable |
| `displayName` | `displayName` | `String` | `display_name` | nullable |
| `firstName` | `firstName` | `String` | `first_name` | nullable |
| `lastName` | `lastName` | `String` | `last_name` | nullable |
| `faculty` | `faculty` | `String` | `faculty` | nullable (champ libre côté `User`) |
| `studyLevel` | `studyLevel` | `String` | `study_level` | nullable |
| `bio` | `bio` | `String` | `bio` | `@Column(columnDefinition="TEXT")` |
| `interests` | `interests` | `List<String>` | `user_interests` | `@ElementCollection(fetch=EAGER)` |
| `avatarUrl` | `avatarUrl` | `String` | `avatar_url` | nullable |
| `profilePublic` | `profilePublic` | `boolean` | `profile_public` | default `false` |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | immutable, defaults to `now()` |
| `version` | `version` | `Long` | `version` | `@Version` (optimistic locking) |
| `calendarToken` | `calendarToken` | `UUID` | `calendar_token` | nullable, `@Column(unique=true)` — généré à la demande par `CalendarService.getOrCreateToken` |

Helpers statiques : `User.findByAuth0Id(String)`, `User.findByEmail(String)`

#### Règle de visibilité du profil (hotfix pentest 2026-04-17)

Le champ `profilePublic` contrôle deux dimensions simultanément sur `GET /api/users/{id}` :

| `profilePublic` | Appelant | Réponse |
|---|---|---|
| `true` | anon | `200` — payload **réduit** (`id`, `displayName`, `avatarUrl` ; autres `null`) |
| `true` | authentifié | `200` — payload **complet** |
| `false` | anon ou autre user | `404 not_found` (envelope identique à un UUID inexistant) |
| `false` | propriétaire (`auth0Id` matche) | `200` — payload complet (self-case) |

La règle d'autorisation vit dans `UserService.getPublicProfile(UUID, String auth0Id)` ;
le stripping anonyme est appliqué dans `UserResource` via `UserPublicResponse.fromAnonymous`.

---

### Event

Table : `events`

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `title` | `title` | `String` | `title` | `@NotBlank`, `@Size(max=120)` |
| `description` | `description` | `String` | `description` | nullable, `@Size(max=2000)` (DTO), `@Column(columnDefinition="TEXT")` |
| `location` | `location` | `String` | `location` | `@NotBlank` |
| `startDate` | `startDate` | `LocalDateTime` | `start_date` | `@NotNull`, `@Future` |
| `endDate` | `endDate` | `LocalDateTime` | `end_date` | `@NotNull` |
| `category` | `category` | `EventCategory` | `category` | `@NotNull`, `@Enumerated(STRING)` |
| `faculty` | `faculty` | `Faculty` | `faculty` | nullable, `@Enumerated(STRING)` — SCRUM-77 |
| `bannerUrl` | `bannerUrl` | `String` | `banner_url` | nullable |
| `creator` | — | `User` | `creator_id` | `@ManyToOne(LAZY)`, `@JoinColumn` — FK vers `users.id` |
| `status` | `status` | `EventStatus` | `status` | `@NotNull`, `@Enumerated(STRING)`, default `DRAFT` |
| `capacity` | `capacity` | `Integer` | `capacity` | nullable |
| `allDay` | `allDay` | `boolean` | `all_day` | `@Column(nullable=false)`, default `false` — SCRUM-117 |
| `websiteUrl` | `websiteUrl` | `String` | `website_url` | nullable, `@URL` (Hibernate Validator), `@Column(length=500)` — SCRUM-126 |
| `contactEmail` | `contactEmail` | `String` | `contact_email` | nullable, `@Email` (jakarta), `@Column(length=255)` — SCRUM-126 |
| `registrationDeadline` | `registrationDeadline` | `LocalDateTime` | `registration_deadline` | nullable — SCRUM-126. `AttendanceService.attend()` renvoie 409 `registration_closed` si `now().isAfter(registrationDeadline)`. |
| `tags` | `tags` | `List<String>` | table `event_tags` | `@ElementCollection(fetch=EAGER)`, colonne DB `tag VARCHAR(64)` (legacy compat), validation DTO `@Size(max=16)` sur les éléments depuis ISSUE-122, max 20 tags — SCRUM-126. Normalisé côté service (trim + lowercase + dédup ordonnée). |
| `shareCode` | `shareCode` | `String` | `share_code` | nullable, unique — généré à la demande par `ShareService` |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |
| `updatedAt` | `updatedAt` | `LocalDateTime` | `updated_at` | mis à jour via `@PreUpdate` |

Index DB : `idx_event_creator` (creator_id), `idx_event_start_date` (start_date), `idx_event_faculty` (faculty)

Table dérivée : `event_tags` — créée automatiquement par Hibernate via `@ElementCollection`. Colonnes `event_id` (FK vers `events.id`, FK nommée `fk_event_tags_event`) et `tag` (varchar(64), not null). Chargée en EAGER avec l'`Event` pour éviter le N+1 dans les endpoints de lecture.

#### Règle de visibilité par statut (hotfix pentest 2026-04-17)

Le statut `Event.status` détermine qui peut lire l'événement via `GET /api/events/{id}` :

| Statut | Visibilité |
|---|---|
| `PUBLISHED` | Public (anon + authentifié) |
| `DRAFT` | Créateur (`event.creator.auth0Id`) ou rôle `ADMIN` uniquement |
| `CANCELLED` | Créateur ou rôle `ADMIN` uniquement |

Un appelant non autorisé reçoit `404 not_found` — même envelope qu'un ID inexistant, pour fermer l'oracle d'existence (cf. findings 4.12 + 4.15 du rapport de pentest). La règle est appliquée dans `EventService.getById(Long, String, boolean)`, avec extraction de l'identité anonyme-safe côté Resource (`identity.isAnonymous()` + `identity.hasRole("ADMIN")`).

Les endpoints de liste (`GET /events`, `GET /events/search`) filtrent déjà les statuts non publics correctement — voir SCRUM-133 pour le contexte.

---

### Favorite

Table : `favorites`

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `userId` | `userId` | `UUID` | `user_id` | not null |
| `eventId` | `eventId` | `Long` | `event_id` | not null |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_favorite_user_event` sur `(user_id, event_id)`.

Suppression physique autorisée (pas de soft-delete).

Helpers statiques : `Favorite.findByUserAndEvent(UUID, Long)`, `Favorite.findByUser(UUID, int, int)`, `Favorite.findAllByUser(UUID)` (non paginé — utilisé par `CalendarService`).

---

### EventView

Table : `event_views`

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `eventId` | `eventId` | `Long` | `event_id` | not null |
| `userId` | `userId` | `UUID` | `user_id` | not null |
| `viewedAt` | `viewedAt` | `LocalDateTime` | `viewed_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_event_view_user_event` sur `(event_id, user_id)` — garantit qu'un utilisateur ne génère qu'une seule vue par événement (idempotence).

Utilisée par `EventStatsService.getStats()` pour calculer `viewCount`.

---

### Attendance

Table : `attendances`

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `userId` | `userId` | `UUID` | `user_id` | not null |
| `eventId` | `eventId` | `Long` | `event_id` | not null |
| `status` | `status` | `AttendanceStatus` | `status` | not null, `@Enumerated(STRING)` |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_attendance_user_event` sur `(user_id, event_id)`.

Depuis SCRUM-129, l'appel `POST /events/{id}/attend` est **idempotent sans upsert** : si l'utilisateur est déjà inscrit (`ATTENDING` ou `WAITLISTED`), l'inscription existante est renvoyée telle quelle, sans modification. La promotion `WAITLISTED → ATTENDING` n'est jamais déclenchée par un appel client — uniquement par la libération d'un slot dans `removeAttendance()`, sous verrou pessimiste.

Helpers statiques : `Attendance.findByEvent(Long, int, int)`, `Attendance.findAllByUser(UUID)`, `Attendance.countGroupedByStatus(List<Long>, AttendanceStatus, EntityManager)` — bulk count utilisé par `EventService.getAll()` pour `attendingCount` et `waitlistedCount`.

#### `AttendanceDTO` — projection du nom du participant

`AttendanceDTO` (record renvoyé par toutes les routes liées aux inscriptions) projette `displayName` et `avatarUrl` depuis le `User` lié à la ligne. Les routes concernées sont déjà restreintes (`GET /events/{id}/attendees` réservée au créateur ou co-organisateur ACCEPTED ; les autres routes ne renvoient que les inscriptions du caller) — exposer le nom y est sûr même pour les profils `profilePublic = false`. C'est ce qui permet à la page stats organisateur d'afficher le vrai nom des participants privés sans passer par `GET /users/{id}` (qui renvoie 404 pour les profils privés, hotfix pentest 4.1).

`AttendanceService.getAttendees(...)` charge les `User` correspondants en une seule requête (`User.list("id in ?1", ids)`) plutôt qu'un lookup par ligne, pour éviter le N+1 côté serveur. `displayName` est `null` uniquement sur les inscriptions orphelines (user supprimé sans cascade FK — pas de `@ManyToOne` aujourd'hui).

---

### EventView

Table : `event_views`

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `eventId` | `eventId` | `Long` | `event_id` | not null |
| `userId` | `userId` | `UUID` | `user_id` | not null |
| `viewedAt` | `viewedAt` | `LocalDateTime` | `viewed_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_event_view_user_event` sur `(event_id, user_id)` — une seule vue enregistrée par utilisateur par événement.

L'appel `POST /events/{id}/view` est **idempotent** : si l'utilisateur a déjà vu l'événement, la vue existante est conservée et la requête retourne 204 sans erreur ni modification.

Helpers statiques : `EventView.findByEventAndUser(Long eventId, UUID userId)`.

---

### EventCoOrganizer

Table : `event_co_organizers` (créée par la migration `V8__create_event_co_organizers.sql` en SCRUM-136).

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `eventId` | — | `Long` | `event_id` | not null |
| `userId` | `userId` | `UUID` | `user_id` | not null |
| `status` | `status` | `CoOrganizerStatus` | `status` | not null, `@Enumerated(STRING)` |
| `invitedAt` | `invitedAt` | `LocalDateTime` | `invited_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_event_co_organizers_event_user` sur `(event_id, user_id)`.
Index : `idx_event_co_organizers_event` (`event_id`), `idx_event_co_organizers_user` (`user_id`).

Suppression physique autorisée (pas de soft-delete) — symétrique à `Favorite`. Le retrait par
le créateur (`DELETE /events/{id}/co-organizers/{userId}`) et le decline par l'invité
(`PATCH /events/{id}/co-organizers/me/decline`) suppriment la row directement.

#### Sémantique du `DECLINE`

`PATCH /events/{id}/co-organizers/me/decline` **supprime physiquement** la row au lieu de la marquer
`DECLINED`. La valeur `DECLINED` reste définie dans l'enum `CoOrganizerStatus` mais n'apparaît jamais
en base. Cette décision permet au créateur de ré-inviter la même personne après un refus, sans 409
(la contrainte unique étant strictement basée sur la présence d'une row, pas sur son statut).

`PATCH /events/{id}/co-organizers/me/accept` et `PATCH /events/{id}/co-organizers/me/decline`
retournent `422 Unprocessable Entity` avec `error=no_pending_invitation` lorsqu'aucune row
n'existe pour l'utilisateur courant sur cet événement (cf. fix de review SCRUM-136).

#### Helpers statiques

- `EventCoOrganizer.isAcceptedFor(Long eventId, UUID userId)` — réponse boolean en
  une seule requête `count`. Utilisé par `EventService.isCreatorOrAcceptedCoOrganizer`.
- `EventCoOrganizer.findByEventAndUser(Long, UUID)` — résolution unitaire pour accept/decline/remove.
- `EventCoOrganizer.findByEvent(Long eventId)` — listing par event, tri `invitedAt ASC`.
- `EventCoOrganizer.findByUser(UUID userId, CoOrganizerStatus status, int page, int size)` — listing
  par user filtré sur un statut, paginé, tri `invitedAt DESC`.

#### Permissions « créateur ou co-organisateur ACCEPTED » (cascade SCRUM-136)

Le helper privé `EventService.isCreatorOrAcceptedCoOrganizer(Event, String)` (et son wrapper public
`isCreatorOrAcceptedCoOrganizerPublic` réutilisé par les services voisins) unifie la garde
d'autorisation pour les opérations de gestion d'événement déléguables :

- `EventService.update`, `cancel`, `restore`, `publish`, `uploadImage`, `getById`
  (visibilité DRAFT/CANCELLED).
- `AttendanceService.getAttendees`, `EventStatsService.getStats`.

`EventService.delete` (suppression physique d'un event CANCELLED) reste **strict-creator** —
non délégable aux co-organisateurs (action irréversible, hors scope du « partage de gestion »
de US-29). Cette divergence par rapport au libellé du ticket est documentée dans la PR
SCRUM-136.

L'invitation par le créateur OU un admin ; l'accept/decline est self-only (l'identité provient
du JWT — pas de spoofing).

---

### Report

Table : `reports`

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `event` | — | `Event` | `event_id` | `@ManyToOne(LAZY)`, `@JoinColumn(nullable=false)` — FK vers `events.id` |
| `reporter` | — | `User` | `reporter_id` | `@ManyToOne(LAZY)`, nullable — FK vers `users.id` |
| `status` | `status` | `ReportStatus` | `status` | `@Enumerated(STRING)`, not null, défaut `PENDING` |
| `reason` | `reason` | `String` | `reason` | nullable, `@Column(columnDefinition="TEXT")` |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Index DB : `idx_report_event` (event_id), `idx_report_status` (status).

Utilisée par `ModerationCleanupService` pour calculer le nombre de signalements `PENDING` par événement (job quotidien 03h00).

---

## Conventions de nommage

### camelCase obligatoire

Tous les champs des entités JPA utilisent le **camelCase**. Hibernate applique automatiquement la strategy `CamelCaseToUnderscoresNamingStrategy` pour la DB. Jackson sérialise en camelCase dans le JSON — c'est la convention Quarkus par défaut.

| Correct | Incorrect |
|---|---|
| `displayName` | `display_name` |
| `startDate` | `start_date` |
| `profilePublic` | `profile_public` |
| `createdAt` | `created_at` |

**Ne jamais introduire de snake_case** dans les noms de champs Java ou dans les réponses JSON.

### Booléens sans préfixe `is`

Les champs booléens **n'utilisent pas le préfixe `is`** dans les entités JPA.

| Correct | Incorrect |
|---|---|
| `profilePublic` | `isProfilePublic` |
| `active` | `isActive` |
| `featured` | `isFeatured` |
| `admin` | `isAdmin` |
| `read` | `isRead` |

**Raison :** Lombok génèrerait `isIsActive()` → conflit garanti. Jackson sérialise `isActive` → incohérence JSON entre `active` (getter sans `is`) et `isActive` (champ).

---

---

## DTOs

### UserProfileResponse (record)
Profil complet — retourné à l'utilisateur authentifié via `GET /users/me` et `PUT /users/me`.

```
id, auth0Id, email, displayName, faculty, studyLevel, bio, interests, avatarUrl, profilePublic, createdAt
```

Factory : `UserProfileResponse.from(User user)`

### UserPublicResponse (record)
Profil public — retourné via `GET /users/{id}` si `profilePublic = true`.

```
id, displayName, faculty, studyLevel, bio, interests, avatarUrl
```

Factory : `UserPublicResponse.from(User u)`

### EventDTO
Représente un événement retourné par l'API (`GET /events`, `GET /events/{id}`).

```
id, title, description, location, startDate, endDate, category, faculty, bannerUrl,
creatorId (UUID — extrait de creator.id), status, capacity, allDay,
attendingCount, availableSpots, waitlistedCount,
websiteUrl, contactEmail, registrationDeadline, tags,
createdAt, updatedAt
```

Factory : `EventDTO.from(Event event, long attendingCount, Long availableSpots, long waitlistedCount)`

- `attendingCount` et `waitlistedCount` sont chargés via `Attendance.countGroupedByStatus` (bulk) dans `getAll()` et via `Attendance.count` (unitaire) dans `getById()` et les autres mutations.
- `availableSpots` est calculé dans `EventService.computeAvailableSpots(capacity, attendingCount)` : `null` si `capacity` est `null`, sinon `max(0, capacity - attendingCount)`. Jamais négatif, même si `capacity` a été réduit sous le nombre d'ATTENDING déjà inscrits.
- `tags` est exposé sous forme de liste immuable (`List.copyOf`) — jamais `null` (vide si aucun tag).

### CreateEventRequest
Body de création (`POST /events`). Champs requis : `title`, `location`, `startDate`, `endDate`, `category`.

| Champ | Validation |
|---|---|
| `title` | `@NotBlank`, `@Size(max=120)` |
| `location` | `@NotBlank` |
| `startDate` | `@NotNull`, `@Future` |
| `endDate` | `@NotNull` |
| `category` | `@NotNull` |
| `description` | `@Size(max=2000)`, optionnel |
| `bannerUrl`, `capacity` | optionnels |

### UpdateEventRequest
Body de mise à jour partielle (`PUT /events/{id}`). Tous les champs optionnels.

```
title, description, location, startDate, endDate, category, bannerUrl, capacity, status
```

### UpdateProfileRequest (record)
Body de `PUT /users/me`. Tous les champs sont optionnels (nullable).

| Champ | Validation |
|---|---|
| `displayName` | `@Size(max=120)` |
| `faculty` | `@Size(max=120)` |
| `studyLevel` | `@Size(max=120)` |
| `bio` | `@Size(max=2000)` |
| `avatarUrl` | `@Size(max=2048)` + `@Pattern` (http/https uniquement) |
| `interests` | `List<String>`, nullable |
| `profilePublic` | `Boolean`, nullable |

### EventStatsDTO (record)
Statistiques agrégées d'un événement — retourné par `GET /events/{id}/stats` (créateur uniquement).

```
attendingCount, interestedCount, viewCount
```

- `attendingCount` : nombre d'`Attendance` avec `status = ATTENDING`.
- `interestedCount` : nombre de `Favorite` liés à l'événement.
- `viewCount` : nombre de `EventView` liés à l'événement (1 par utilisateur).

### Réponses d'erreur

**ApiErrorResponse** : `{ error: String, message: String }`

**ValidationErrorResponse** : `{ error: String, message: String, details: [ { field: String|null, message: String } ] }`

---

## Énumérations

### Implémentées dans les entités JPA

| Enum Java | Valeurs | Sprint | État |
|---|---|---|---|
| `EventCategory` | `ACADEMIC`, `SPORTS`, `CULTURAL`, `SOCIAL`, `CONFERENCE`, `OTHER` | Sprint 2 | ✅ Implémenté |
| `EventStatus` | `DRAFT`, `PUBLISHED`, `CANCELLED` | Sprint 2 | ✅ Implémenté |
| `Faculty` | `SCIENCES`, `LETTRES`, `DROIT`, `MEDECINE`, `SES`, `PSYCHOLOGIE`, `THEOLOGIE`, `FTI`, `GSI` | Sprint 3 | ✅ Implémenté (SCRUM-77) |
| `AttendanceStatus` | `ATTENDING`, `WAITLISTED` | Sprint 4 / Sprint 5 | ✅ Implémenté (WAITLISTED ajouté en SCRUM-129) |
| `CoOrganizerStatus` | `PENDING`, `ACCEPTED`, `DECLINED` | Sprint 7 | ✅ Implémenté (SCRUM-136 — `DECLINED` est transitoire et n'apparaît jamais en base, cf. section EventCoOrganizer) |
| `ReportStatus` | `PENDING`, `REVIEWED`, `DISMISSED` | Sprint 7 | ✅ Implémenté (US-18) |

Sérialisées en `String` dans le JSON (Jackson default avec Quarkus).

### Valeurs de champs `faculty` et `studyLevel`

Ces champs sont actuellement stockés en `String` dans l'entité `User` — **pas de contrainte enum côté backend**. La validation des valeurs est faite côté frontend uniquement.

> **SCRUM-77 :** Le champ `faculty` sur l'entité `Event` utilise désormais l'enum Java `Faculty` (`@Enumerated(STRING)`). Sur `User`, `faculty` reste un `String` libre.

Valeurs attendues pour `faculty` (cohérentes avec les types TypeScript frontend) :

| Valeur | Libellé |
|---|---|
| `SCIENCES` | Faculté des Sciences |
| `LETTRES` | Faculté des Lettres |
| `DROIT` | Faculté de Droit |
| `MEDECINE` | Faculté de Médecine |
| `SES` | Sciences économiques et sociales |
| `PSYCHOLOGIE` | Psychologie et Sciences de l'éducation |
| `THEOLOGIE` | Théologie |
| `FTI` | Traduction et interprétation |
| `GSI` | Global Studies Institute |

Valeurs attendues pour `studyLevel` :
`BACHELOR`, `MASTER`, `DOCTORAT`, `POST_DOC`, `STAFF`

> **Action Sprint 2 :** Quand `EventCategory` sera implémenté, créer un enum Java et l'utiliser dans l'entité. Pour `faculty`/`studyLevel`, évaluer si une contrainte enum DB est nécessaire ou si la validation frontend suffit.

---

## Règles de validation JPA

| Annotation | Champ(s) concerné(s) |
|---|---|
| `@NotBlank` | `Event.title` |
| `@Size(max=120)` | `EventRequestBase.title` |
| `@Size(max=2000)` | `EventRequestBase.description` |
| `@Version` | `User.version` (optimistic locking) |
| Unique constraint | `User.auth0Id`, `User.email` |
| Unique constraint (planifié) | `Attendance(userId, eventId)` |

---

## Endpoint `GET /users/me/events` (SCRUM-133)

Retourne tous les événements où `creator.id = <utilisateur authentifié>`, triés par `createdAt DESC` (tie-breaker `id DESC`). Inclut **tous les statuts** (`DRAFT`, `PUBLISHED`, `CANCELLED`) par défaut. Paramètres :

- `status` (optionnel, `EventStatus`) : filtre sur un statut précis.
- `page` (défaut `0`, min `0`).
- `size` (défaut `20`, min `1`, max `100`).

**Règle d'autorisation** : l'identité provient du JWT via `SecurityIdentity.getPrincipal().getName()`. Il n'existe aucun moyen d'énumérer les événements d'un autre utilisateur via cet endpoint, c'est pourquoi `DRAFT` et `CANCELLED` peuvent être retournés sans vérification supplémentaire.

**Complémentarité avec `GET /events?organizerId=`** : le filtre public `organizerId` reste disponible pour lister les événements publiés d'un organisateur, mais il force `status = PUBLISHED` (coercition silencieuse si `status` absent, rejet `400 organizer_filter_requires_published` si un autre statut est demandé). Ce verrou ferme la faille qui permettait précédemment d'énumérer les brouillons d'un autre utilisateur via `GET /events?organizerId=<uuid>&status=DRAFT`.

---

## Gestion du schéma — Flyway

Le schéma est piloté par **Flyway**, exécuté au démarrage Quarkus (`quarkus.flyway.migrate-at-start=true`).

- Migrations : `backend/src/main/resources/db/migration/`, nommées `V<N>__<snake_case_description>.sql`.
- Hibernate est en `validate` en dev/prod : il vérifie que les entités JPA correspondent au schéma migré, sans le modifier. En `%test`, Hibernate est en `drop-and-create` pour bootstrapper la base éphémère DevServices ; les migrations Flyway s'y appliquent en no-op (les fichiers V1 sont conditionnés sur l'existence des tables).
- Stratégie d'adoption : `baseline-on-migrate=true` + `baseline-version=0`. Les bases existantes provisionnées historiquement par Hibernate `update` adoptent Flyway à partir de V1 sans dump rétroactif.
- Une migration committée est **immutable** : pour modifier le schéma, ajouter un nouveau fichier `V<N+1>__…`.

### V1 — Réconciliation des contraintes CHECK

`V1__reconcile_check_constraints.sql` est la première migration : elle drop+recrée `events_faculty_check`, `events_category_check`, `events_status_check` et `attendances_status_check` avec les valeurs courantes des enums Java. Elle remplace l'ancien bean `SchemaFixup` qui faisait le même travail au démarrage.

> **À surveiller pour `event_co_organizers_status_check` (SCRUM-136).** La table
> `event_co_organizers` est créée par la migration `V8__create_event_co_organizers.sql`,
> qui pose la CHECK initiale sur `CoOrganizerStatus`. Toute future modification de l'enum
> (ajout d'une valeur, rename) **devra** passer par un nouveau fichier `V<N+1>__…` qui
> drop+recrée la contrainte avec les valeurs courantes — la convention Flyway interdit de
> muter une migration committée.
