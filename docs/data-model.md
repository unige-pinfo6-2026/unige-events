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

Helpers statiques : `User.findByAuth0Id(String)`, `User.findByEmail(String)`

---

### Event

Table : `events`

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `title` | `title` | `String` | `title` | `@NotBlank` |
| `description` | `description` | `String` | `description` | nullable |

> **Note :** L'entité Event est très partielle (Sprint 2 non encore démarré). Les champs `startDate`, `endDate`, `location`, `category`, `bannerUrl`, `capacity`, `active`, `featured`, `views`, `creatorId` sont planifiés mais pas encore implémentés.

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

> Planifiées pour Sprint 2+ — non encore dans le code.

| Enum Java | Valeurs | Sprint |
|---|---|---|
| `EventCategory` | `ACADEMIC`, `SPORTS`, `CULTURAL`, `SOCIAL`, `CONFERENCE`, `OTHER` | Sprint 2 |
| `AttendanceStatus` | `INTERESTED`, `ATTENDING` | Sprint 4 |
| `ReportStatus` | `PENDING`, `REVIEWED`, `DISMISSED` | Sprint 6 |

Sérialisées en `String` dans le JSON (Jackson default avec Quarkus).

### Valeurs de champs `faculty` et `studyLevel`

Ces champs sont actuellement stockés en `String` dans l'entité `User` — **pas de contrainte enum côté backend**. La validation des valeurs est faite côté frontend uniquement.

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
| `@Version` | `User.version` (optimistic locking) |
| Unique constraint | `User.auth0Id`, `User.email` |
| Unique constraint (planifié) | `Attendance(userId, eventId)` |

---

## Migrations Flyway

**État actuel :** Flyway est configuré dans `pom.xml` mais **désactivé** en dev (`quarkus.flyway.migrate-at-start` commenté dans `application.properties`). Hibernate tourne en mode `update` qui génère/adapte le schéma automatiquement.

**Aucune migration Flyway n'existe** dans `src/main/resources/db/migration/`.

### Convention pour les futures migrations

```
src/main/resources/db/migration/
  V1__init_users.sql
  V2__add_events.sql
  V3__add_attendance_favorite.sql
  ...
```

- Nommer : `V{N}__{description_snake_case}.sql`
- **Ne jamais modifier** un fichier Flyway existant — toujours créer un nouveau
- Même si Hibernate `update` absorbe les changements en dev, **toujours écrire la migration** pour la prod

### Dette de migrations à anticiper

Quand Flyway sera activé pour la prod, les migrations suivantes seront nécessaires :
- `V1__init_users.sql` : table `users` avec tous ses champs actuels
- `V2__init_events.sql` : table `events` minimale (id, title, description)
- Migrations futures pour les entités planifiées (Attendance, Favorite, Notification, Report)
