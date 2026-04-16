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
| `faculty` | `faculty` | `String` | `faculty` | nullable |
| `studyLevel` | `studyLevel` | `String` | `study_level` | nullable |
| `bio` | `bio` | `String` | `bio` | `@Column(columnDefinition="TEXT")` |
| `interests` | `interests` | `List<String>` | `user_interests` | `@ElementCollection(fetch=EAGER)` |
| `avatarUrl` | `avatarUrl` | `String` | `avatar_url` | nullable |
| `profilePublic` | `profilePublic` | `boolean` | `profile_public` | default `false` |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | immutable, defaults to `now()` |
| `version` | `version` | `Long` | `version` | `@Version` (optimistic locking) |
| `calendarToken` | `calendarToken` | `UUID` | `calendar_token` | nullable, `@Column(unique=true)` — généré à la demande par `CalendarService.getOrCreateToken` |

Helpers statiques : `User.findByAuth0Id(String)`, `User.findByEmail(String)`

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
| `tags` | `tags` | `List<String>` | table `event_tags` | `@ElementCollection(fetch=EAGER)`, tag `length=64`, max 20 — SCRUM-126. Normalisé côté service (trim + lowercase + dédup ordonnée). |
| `shareCode` | `shareCode` | `String` | `share_code` | nullable, unique — généré à la demande par `ShareService` |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |
| `updatedAt` | `updatedAt` | `LocalDateTime` | `updated_at` | mis à jour via `@PreUpdate` |

Index DB : `idx_event_creator` (creator_id), `idx_event_start_date` (start_date), `idx_event_faculty` (faculty)

Table dérivée : `event_tags` — créée automatiquement par Hibernate via `@ElementCollection`. Colonnes `event_id` (FK vers `events.id`, FK nommée `fk_event_tags_event`) et `tag` (varchar(64), not null). Chargée en EAGER avec l'`Event` pour éviter le N+1 dans les endpoints de lecture.

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
| `ReportStatus` | `PENDING`, `REVIEWED`, `DISMISSED` | Sprint 6 | Planifié |

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

## Gestion du schéma

Le schéma est géré par **Hibernate en mode `update`** (`quarkus.hibernate-orm.schema-management.strategy=update`).

- Les entités JPA dans `src/main/java/**/entity/` sont la source de vérité
- Hibernate crée et met à jour les tables automatiquement au démarrage
- Aucun fichier SQL de migration n'est utilisé ni requis
