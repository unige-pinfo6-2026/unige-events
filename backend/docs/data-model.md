# Data Model — unige-events-api

> **Mise à jour 2026-05-14 — DB-per-service livré.** Chaque service possède sa propre instance PostgreSQL dédiée (commit `f4b5968e`). Le schéma `public` partagé n'existe plus. Mapping table → DB physique :
>
> | Service propriétaire | DB physique (host:port) | Tables possédées |
> |---|---|---|
> | `event-service` | `postgres-event:5432` / DB `unige_events_events` | `events`, `event_tags`, `event_views`, `favorites`, `event_co_organizers` |
> | `user-service` | `postgres-user:5432` / DB `unige_events_users` | `users`, `user_interests`, `follows` |
> | `engagement-service` | `postgres-engagement:5432` / DB `unige_events_engagement` | `attendances`, `comments` |
> | `moderation-service` | `postgres-moderation:5432` / DB `unige_events_moderation` | `reports`, `event_banned_outbox` (outbox transactionnel ADR-003) |
> | `notification-service` | `postgres-notification:5432` / DB `unige_events_notifications` | `notifications` (SCRUM-99 — entité in-app + 3 Kafka consumers) |
>
> Les migrations Flyway sont **redistribuées par service propriétaire** sous `backend/services/<svc>-service/src/main/resources/db/migration/V*.sql`. La numérotation V est **locale** à chaque service (deux services peuvent avoir une `V1__...sql` chacun). Plus jamais de migration cross-service.

## Entités JPA

### User

Owned by **user-service**. Tables : `users` + `user_interests` (ElementCollection).

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `UUID` | `id` | PK, auto-généré (`@GeneratedValue`) |
| `auth0Id` | `auth0Id` | `String` | `auth0_id` | unique, not updatable |
| `email` | `email` | `String` | `email` | unique, not updatable |
| `username` | `username` | `String` | `username` | `@Column(nullable=false, unique=true, length=30)` — SCRUM-169. Stocké lowercase, lookup case-insensitive. Pattern DB-CHECK `^[a-z0-9._-]{3,30}$`. Généré automatiquement à la création du compte par `UsernameGenerator.generate(...)` (slug `displayName` ASCII-fold → fallback `firstName.lastName` → fallback `user`, suffixe numérique sur collision). Modifiable via `PATCH /users/me/username`. Blocklist : `me`, `admin`, `api`, `login`, `logout`, `signup`, `register`, `settings`. |
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

Helpers statiques : `User.findByAuth0Id(String)`, `User.findByEmail(String)`,
`User.findByUsername(String)` (case-insensitive — SCRUM-169).

#### Stratégie de génération de username (SCRUM-169)

La logique vit dans `UsernameGenerator` (`backend/services/user-service/src/main/java/ch/unige/events/user/service/UsernameGenerator.java`) :

1. **Source** : `displayName` (trim) > `firstName.lastName` (skip dot si l'un des deux est vide) > literal `"user"`.
2. **Normalisation** : pré-translation Latin-extended (Đ→D, Ł→L, Ø→O, Æ→AE, Œ→OE, ß→ss, Þ→TH, Ð→D + minuscules) + NFD ASCII-fold + lowercase + whitespace → `.` + drop chars hors `[a-z0-9._-]` + collapse `.` + trim leading/trailing `._-`.
3. **Troncature** : max 30 chars (puis re-trim trailing punct). Si < 3 chars résultat → fallback `"user"`.
4. **Blocklist** : si le résultat appartient à `{me, admin, api, login, logout, signup, register, settings}`, on commence directement au suffixe `2`.
5. **Anti-collision** : boucle `while EXISTS` avec suffixe numérique incrémental (`jean.dupont`, `jean.dupont2`, …). La base est trimmée si `base + suffix > 30 chars`.

La même logique est dupliquée en PL/pgSQL dans `V3__add_user_username.sql` pour le back-fill atomique des comptes pré-existants. Toute modification de l'algorithme Java doit être miroir dans la migration suivante (`V4__...`) — la V3 reste immutable. Les sentinels `UsernameGeneratorTest` (23 cas) pin la sémantique côté Java.

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

**SCRUM-169 — exposition du `username`.** Le champ `username` est **toujours**
projeté dans la réponse, y compris pour les appelants anonymes (contrairement
aux autres champs strippés). Justification : c'est l'identifiant public-facing
du profil (utilisé dans l'URL `/profile/{username}`) — le nullifier casserait
l'usage premier du champ. Le lookup par username (`GET /api/users/by-username/{username}`)
applique la même règle d'autorisation et le même stripping ;
`HEAD /api/users/by-username/{username}` reste léger et `@PermitAll`.

---

### Event

Owned by **event-service**. Tables : `events` + `event_tags`.

**Kafka** : `EventLifecyclePublisher` émet `events.{published, cancelled, expired}` post-commit via CDI `@Observes(AFTER_SUCCESS)` (Décision A/F de la spec de complétion). Consumer `events.banned` dans event-service (apply `event.status = BANNED` localement, idempotent — émis par `moderation-service` lors d'un BAN admin ou d'un auto-ban via `ModerationCleanupJob`).

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
| `parentEventId` | `parentEventId` | `Long` | `parent_event_id` | nullable, FK auto-référente vers `events.id` avec `ON DELETE SET NULL` (FK `fk_events_parent`) — SCRUM-147. `null` sur un parent récurrent ou un standalone, renseigné sur les occurrences enfants. Pas de `@ManyToOne` — pointeur Long brut, cohérent avec Favorite/Attendance/Follow. |
| `recurrenceRule` | `recurrenceRule` | `String` | `recurrence_rule` | nullable, `@Column(length=500)` — SCRUM-147. RFC 5545 RRULE simplifié (`FREQ=WEEKLY;UNTIL=YYYYMMDD;COUNT=N`), porté UNIQUEMENT par le parent récurrent. `null` sur les occurrences enfants et les standalones. |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |
| `updatedAt` | `updatedAt` | `LocalDateTime` | `updated_at` | mis à jour via `@PreUpdate` |

Index DB : `idx_event_creator` (creator_id), `idx_event_start_date` (start_date), `idx_event_faculty` (faculty), `idx_event_featured_status_end` (featured, status, end_date), `idx_event_parent` (parent_event_id) — SCRUM-147.

Table dérivée : `event_tags` — créée automatiquement par Hibernate via `@ElementCollection`. Colonnes `event_id` (FK vers `events.id`, FK nommée `fk_event_tags_event`) et `tag` (varchar(64), not null). Chargée en EAGER avec l'`Event` pour éviter le N+1 dans les endpoints de lecture.

#### Règle de visibilité par statut (hotfix pentest 2026-04-17)

Le statut `Event.status` détermine qui peut lire l'événement via `GET /api/events/{id}` :

| Statut | Visibilité |
|---|---|
| `PUBLISHED` | Public (anon + authentifié) |
| `DRAFT` | Créateur (`event.creator.auth0Id`) ou rôle `ADMIN` uniquement |
| `CANCELLED` | Créateur ou rôle `ADMIN` uniquement |

Un appelant non autorisé reçoit `404 not_found` — même envelope qu'un ID inexistant, pour fermer l'oracle d'existence (cf. findings 4.12 + 4.15 du rapport de pentest). La règle est appliquée dans `EventService.getById(Long, String, boolean)`, avec extraction de l'identité anonyme-safe côté Resource (`identity.isAnonymous()` + `identity.hasRole("ADMIN")`).

#### Récurrence (SCRUM-147)

La récurrence d'un événement est matérialisée par **rows** : chaque occurrence est une row `events` standalone, avec `parent_event_id` pointant vers le **template parent** et `recurrence_rule` portée UNIQUEMENT par le parent. Pas de table de jointure dédiée, pas d'event « virtuel » non-persisté — l'isolation par row préserve toutes les propriétés des services aval (Attendance, Favorite, EventView, EventStats, EventCoOrganizer, Comment, CalendarService) sans branchement spécial.

| Aspect | Valeur |
|---|---|
| Fréquences supportées | `RecurrenceFrequency` ∈ {`WEEKLY`, `BIWEEKLY`, `MONTHLY`}. Pas de `DAILY` ni `YEARLY` en S7. |
| Espacement | WEEKLY = `Period.ofDays(7)`, BIWEEKLY = `Period.ofDays(14)`, MONTHLY = `Period.ofMonths(1)` (rolling 31→28 février naturel via `LocalDateTime.plus(Period)`). |
| Format `recurrence_rule` | RFC 5545 RRULE simplifié — `FREQ=WEEKLY;UNTIL=YYYYMMDD`, `FREQ=BIWEEKLY;COUNT=10`, `FREQ=MONTHLY;UNTIL=YYYYMMDD;COUNT=12`. PAS de support `BYDAY`/`EXDATE`/`INTERVAL` en S7. |
| Cap matérialisation | 52 rows total (parent inclus) — limite hard. `RecurrenceGenerator` retourne au plus 51 ranges. |
| Statut hérité | Les occurrences héritent du `status` du parent à la création (DRAFT par défaut, ou `request.status` si fourni). Symétrie totale parent ↔ enfants. |
| FK `fk_events_parent` | `ON DELETE SET NULL` — un DELETE physique du parent (après cancel) préserve les occurrences orphelines avec `parent_event_id = NULL`. Inscriptions, favoris, vues, comptages survivent. |
| Modification globale (PUT) | **Pas de propagation** au PUT du parent vers les occurrences. Chaque occurrence reste indépendamment éditable (cf. décision spec 17). |
| Cancel cascadé | **Pas de cascade** sur `PATCH /events/{parentId}/cancel`. Les occurrences restent indépendamment cancellables (décision spec 18). |
| Co-organisateurs | **Pas d'héritage automatique**. Le co-org accepté sur le parent n'a aucun privilège sur les occurrences — il faut `POST /co-organizers` par occurrence (décision spec 12). |
| ICS feed | Inchangé — chaque occurrence row génère son propre VEVENT autonome dans `CalendarService.generateIcsFeed`. Pas de RRULE compact dans l'ICS (décision spec 13). |
| Atomicité création | `EventService.createRecurring(...)` est `@Transactional` — parent + occurrences persistés dans la même unité JTA, all-or-nothing. |
| Anti-oracle GET | `GET /events/{id}/occurrences` délègue à `getById(...)` ; un parent invisible (DRAFT non-créateur, BANNED, id inconnu) renvoie 404. Un standalone non-récurrent renvoie 200 + liste vide (pas 404). |

Les endpoints de liste (`GET /events`, `GET /events/search`) filtrent déjà les statuts non publics correctement — voir SCRUM-133 pour le contexte.

---

### Favorite

Owned by **event-service** (sous-package event/favorite, post-consolidation Étape 2.2.3). Table : `favorites`.

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

Owned by **event-service** (sous-package event/view, post-consolidation Étape 2.2.2). Table : `event_views`.

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `eventId` | `eventId` | `Long` | `event_id` | not null |
| `userId` | `userId` | `UUID` | `user_id` | not null |
| `viewedAt` | `viewedAt` | `LocalDateTime` | `viewed_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_event_view_user_event` sur `(event_id, user_id)` — garantit qu'un utilisateur ne génère qu'une seule vue par événement (idempotence).

L'appel `POST /events/{id}/view` est **idempotent** : si l'utilisateur a déjà vu l'événement, la vue existante est conservée et la requête retourne 204 sans erreur ni modification.

Helpers statiques : `EventView.findByEventAndUser(Long eventId, UUID userId)`.

Utilisée par `EventStatsService.getStats()` pour calculer `viewCount`.

---

### Attendance

Owned by **engagement-service** (sous-package engagement/attendance, post-rename Étape 2.1.1). Table : `attendances`.

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

`AttendanceDTO` (record renvoyé par toutes les routes liées aux inscriptions) projette `displayName` et `avatarUrl` depuis le `User` lié à la ligne.

**Filtre de confidentialité sur `GET /events/{id}/attendees` (SCRUM-S7)** : l'endpoint est désormais accessible à tout utilisateur authentifié (plus de 403 pour les non-organisateurs). La confidentialité est appliquée côté DTO :

- **Vue organisateur** (créateur, co-organisateur ACCEPTED, ou administrateur) : `displayName`, `avatarUrl` et `userId` réels pour toutes les lignes, y compris les profils privés.
- **Autre utilisateur authentifié** : identité réelle uniquement pour les profils `profilePublic = true`. Les lignes correspondant à un profil privé sont retournées avec `displayName = null`, `avatarUrl = null`, **et `userId = null`** (l'UUID est volontairement masqué pour empêcher l'appelant de sonder `GET /users/{id}` qui désanonymiserait le participant via le pattern 404 ISSUE-93).
- **Utilisateur supprimé** (ligne orpheline) : anonymisé de la même façon, quel que soit le rôle de l'appelant — aucune identité réelle à exposer.

`AttendanceService.getAttendees(...)` résout en une seule requête cross-service la projection `(id, displayName, avatarUrl, profilePublic)` de tous les users du batch via le nouvel endpoint interne `GET /users/_internal-attendee-projections?ids=...` (entry #7 dans [`internal-endpoints.md`](internal-endpoints.md)). Cet endpoint contourne l'anti-oracle ISSUE-93 (interne uniquement, `@Internal` + `X-Internal-Token`) afin que le consumer puisse décider de la projection par ligne. Sur les autres routes (`/users/me/attendances`, etc.) qui ne renvoient que des inscriptions appartenant au caller, exposer le nom reste sûr y compris pour les profils privés (l'utilisateur regarde ses propres inscriptions).

---

### EventCoOrganizer

Owned by **event-service** (sous-package event/coorganizer, post-consolidation Étape 2.2.4). Table : `event_co_organizers`.

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

### Follow

Owned by **user-service** (sous-package user/follow, post-consolidation Étape 2.3.1). Table : `follows`.

Table : `follows` (créée par la migration `V14__create_follows.sql` en SCRUM-138).

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity`, sequence `follows_seq` |
| `followerId` | `followerId` | `UUID` | `follower_id` | not null — FK `fk_follows_follower` → `users(id)` |
| `followedId` | `followedId` | `UUID` | `followed_id` | not null — FK `fk_follows_followed` → `users(id)` |
| `status` | `status` | `FollowStatus` | `status` | `@Enumerated(STRING)`, not null, `length=16`, CHECK constraint |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_follow_follower_followed` sur `(follower_id, followed_id)` —
empêche le double suivi et sert de filet de sécurité au check applicatif `409 already_following`.

CHECK constraints :
- `follows_status_check` (V14) : `status IN ('PENDING', 'ACCEPTED')`.

Index : `idx_follow_followed` (followed_id), `idx_follow_follower` (follower_id) —
support des compteurs `countFollowers` / `countFollowing` et des listings paginés
(SCRUM-138).

**Pas de cascade FK** — un user supprimé laisse des rows orphelines (pattern défensif
identique à `Report.reporter`, à nettoyer par job ultérieur si nécessaire).

#### Sémantique du `REJECT`

`PATCH /follow-requests/{id}/reject` **supprime physiquement** la row au lieu de la
marquer `REJECTED`. La valeur `REJECTED` n'existe pas dans l'enum `FollowStatus`. Cette
décision permet au follower de re-tenter une demande après refus, sans 409 (la
contrainte unique étant strictement basée sur la présence d'une row, pas sur son
statut). Pattern aligné sur `EventCoOrganizer.DECLINE`.

#### Helpers statiques

- `Follow.findByFollowerAndFollowed(UUID, UUID)` — résolution unitaire pour
  follow/unfollow/cancel.
- `Follow.findFollowersOf(UUID, int, int)` — listing paginé ACCEPTED, tri
  `createdAt DESC, id DESC`.
- `Follow.findFollowingOf(UUID, int, int)` — idem côté following.
- `Follow.findPendingRequestsFor(UUID, int, int)` — inbox des demandes PENDING.
- `Follow.findAcceptedFollowedIds(UUID followerId): List<UUID>` — projection JPQL
  directe. **Anticipation SCRUM-168** (filtre `followedOnly` du feed S9) : consommé
  par `EventService` pour filtrer les events sur les UUIDs suivis. Couvert par un
  test sentinel dédié dans `FollowServiceCoverageTest`.
- `Follow.countFollowersOf(UUID)`, `Follow.countFollowingOf(UUID)` — compteurs
  ACCEPTED uniquement (PENDING ne compte pas).

#### Consommation par `UserService.getPublicProfile`

`UserService.getPublicProfile(UUID id, String auth0Id)` retourne désormais un
`PublicProfileView` (record `(User user, long followerCount, long followingCount,
FollowStatus followStatus)`) :

- Pour un appelant **anonyme** : `PublicProfileView.anonymous(user)` — court-circuit
  (compteurs 0, followStatus null), pas d'appel `FollowService` (économie 2 requêtes
  DB par hit anonyme).
- Pour un appelant **authentifié** : compteurs réels via
  `FollowService.countFollowers/Following`, `followStatus` calculé via
  `FollowService.getStatusBetween(callerId, targetId)`.
- Sur son **propre profil** (auth0Id matche `user.auth0Id`) : `followStatus` reste
  `null` (un user ne peut pas se suivre — cf. SCRUM-138 décision 6, 422 sur self-follow).

La règle anti-oracle 404 ISSUE-93 reste inchangée : un profil privé non-owner jette
`NotFoundException` avant tout calcul de follow.

#### Consommation par `FollowResource` et `FollowRequestResource`

7 endpoints exposés :
- `POST /users/{id}/follow` (201, 401, 404, 409 `already_following`, 422
  `cannot_follow_self`, 429 — `@PerUserRateLimit(name="follows.follow", max=30)`)
- `DELETE /users/{id}/follow` (204 idempotent, 401)
- `GET /users/{id}/followers`, `GET /users/{id}/following` (200, 401, 404
  anti-oracle si profil privé non-owner)
- `GET /users/me/follow-requests` (200, 401, 404)
- `PATCH /follow-requests/{followId}/accept` (200, 401, 403, 404, 409
  `invalid_transition`)
- `PATCH /follow-requests/{followId}/reject` (204, 401, 403, 404, 409)

La règle anti-oracle 404 sur les listings followers/following est portée par un appel
préalable à `userService.getPublicProfile(...)` qui jette si non visible (alignement
ISSUE-93).

---

### Report

Owned by **moderation-service** (post-rename Étape 2.1.2). Table : `reports`.

Table : `reports` (créée par la migration `V6__create_reports.sql` en SCRUM-103,
enrichie par la migration `V10__add_report_reason_and_review_fields.sql` en SCRUM-94).

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `event` | — | `Event` | `event_id` | `@ManyToOne(LAZY)`, `@JoinColumn(nullable=false)` — FK vers `events.id` |
| `reporter` | — | `User` | `reporter_id` | `@ManyToOne(LAZY)`, nullable — FK vers `users.id` |
| `reason` | `reason` | `ReportReason` | `reason` | `@Enumerated(STRING)`, not null, `length=32`, CHECK constraint — SCRUM-94 |
| `description` | `description` | `String` | `description` | nullable, `@Column(columnDefinition="TEXT")` — texte libre saisi en complément du motif catégoriel. Renommé depuis `reason` (TEXT libre) en SCRUM-94. |
| `status` | `status` | `ReportStatus` | `status` | `@Enumerated(STRING)`, not null, défaut `PENDING` |
| `moderationNote` | `moderationNote` | `String` | `moderation_note` | nullable, `@Column(columnDefinition="TEXT")` — note saisie par l'admin au moment du PATCH |
| `reviewedAt` | `reviewedAt` | `LocalDateTime` | `reviewed_at` | nullable — posé par `ReportService.handle()` au moment de la transition |
| `reviewedBy` | — | `User` | `reviewed_by` | `@ManyToOne(LAZY)`, nullable — FK vers `users.id` |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Index DB : `idx_report_event` (event_id), `idx_report_comment` (comment_id, SCRUM-144),
`idx_report_status` (status).

**SCRUM-144 — schéma étendu pour les reports de commentaires** (migration moderation-service
`V4__add_comment_id_to_reports.sql`) :
- `event_id` devient **nullable** (était NOT NULL).
- Nouveau champ `comment_id BIGINT NULL` — renseigné quand la cible est un commentaire,
  mutuellement exclusif avec `event_id`.
- CHECK constraint **`report_target_xor`** : `(event_id IS NULL) <> (comment_id IS NULL)`
  — XOR strict, un report cible exactement un event OU un commentaire (jamais les deux,
  jamais aucun).
- Anciennes contraintes UK : `uk_report_reporter_event` est **droppée** au profit de deux
  **UK partielles** (PG 9.5+, syntaxe `CREATE UNIQUE INDEX ... WHERE`) :
  - `uq_report_event_partial` sur `(reporter_id, event_id) WHERE event_id IS NOT NULL`
    — empêche le double signalement event-side.
  - `uq_report_comment_partial` sur `(reporter_id, comment_id) WHERE comment_id IS NOT NULL`
    — empêche le double signalement comment-side.
  - Un même reporter peut signaler à la fois un event ET un commentaire distinct sans
    collision (les UK sont indépendantes par cible).

Les UK partielles ne sont **pas** déclarables via JPA `@UniqueConstraint` (qui ne
supporte pas la clause `WHERE`) — elles vivent uniquement côté DB via la migration V4.
La détection 409 `already_reported` côté Java se fait par match `getConstraintName`
sur l'exception Hibernate ; cf. helper `ReportService.isUniqueReportCommentConflict`.

CHECK constraints :
- `reports_status_check` (posée par V7) : `status IN ('PENDING', 'REVIEWED', 'DISMISSED')`.
- `reports_reason_check` (posée par V9) : `reason IN ('SPAM', 'INAPPROPRIATE', 'FAKE', 'OTHER')`.
- `report_target_xor` (posée par V4 SCRUM-144) : `(event_id IS NULL) <> (comment_id IS NULL)`.

Le dashboard admin (`AdminReportResource`) distingue les deux types de reports par
le champ id non-null (`event_id` vs `comment_id`).

#### Sémantique des champs

- **`reason`** : motif catégoriel choisi par l'utilisateur dans la modale frontend
  (SCRUM-96). Enum `ReportReason` : `SPAM`, `INAPPROPRIATE`, `FAKE`, `OTHER`. Obligatoire.
- **`description`** : texte libre optionnel (max 2000 chars). Vit **à côté** de `reason`.
- **`reviewedAt`** + **`reviewedBy`** : posés ensemble par `ReportService.handle()` au
  moment où l'admin transitionne le report (`PENDING → REVIEWED|DISMISSED`). L'invariant
  *« reviewedAt non-null ↔ reviewedBy non-null ↔ status != PENDING »* est garanti côté service,
  pas par une CHECK DB.
- **`moderationNote`** : note libre saisie par l'admin au moment du PATCH (max 2000 chars).

#### Consommation par `ModerationCleanupJob`

Le job [`ModerationCleanupJob`](../services/moderation-service/src/main/java/ch/unige/events/report/scheduler/ModerationCleanupJob.java)
(cf. SCRUM-103) compte les rows `Report` avec `status = PENDING` groupées par event. Il
lit uniquement `r.event` et `r.status` — **insensible** aux ajouts de SCRUM-94 (job quotidien 03h00).

**SCRUM-97** — quand le seuil est atteint, l'event passe en `EventStatus.BANNED` (au lieu de
`CANCELLED` historiquement). Cohérent avec le ban manuel via `ReportService.handle()` :
toute modération produit le même état terminal, distinct de l'annulation par le créateur
(`CANCELLED` reste réversible vers `DRAFT` via `PATCH /events/{id}/restore`).

#### Cascade de validation d'un signalement (SCRUM-97)

Quand `ReportService.handle()` reçoit `status=REVIEWED` :
1. Le signalement passe REVIEWED avec `reviewedBy` = admin courant et `moderationNote`
   éventuelle (saisie par l'admin).
2. L'événement lié passe en `EventStatus.BANNED` — état terminal côté créateur, invisible
   sur `GET /events/{id}` (404 pour TOUT LE MONDE, anti-leak — cf. règle d'autorisation
   d'`EventService.getById()`).
3. Tous les autres signalements PENDING sur le même event sont auto-clôturés (REVIEWED,
   `reviewedBy` admin, `moderationNote` null — seul le signalement explicitement traité
   porte la note).

`status=DISMISSED` est neutre — ne touche ni l'event ni les signalements frères.

#### Consommation par `ReportService`

- `ReportService.create(eventId, auth0Id, CreateReportRequest)` — vérifie l'existence
  de l'event, son statut PUBLISHED, l'absence de self-report (cascade SCRUM-136 :
  créateur OU co-organisateur ACCEPTED → 422), l'absence de doublon ; persiste avec status PENDING.
- **`ReportService.createForComment(commentId, auth0Id, CreateReportRequest)`** — SCRUM-144
  (Décision N). Hop interne via `EngagementServiceClient.getCommentVisibility` (anti-oracle
  ISSUE-92 + SCRUM-136 délégué côté engagement), 422 `cannot_report_own_comment` si
  l'auteur est le caller, persist `Report{ commentId, eventId=null }`, catch
  `PersistenceException` sur la UK partielle → 409 `already_reported`. Fallback REST
  surfacé en 503.
- `ReportService.listByStatus(status, page, size)` — listing paginé pour le dashboard
  admin (SCRUM-97), tri `createdAt DESC, id DESC`.
- `ReportService.handle(reportId, adminAuth0Id, HandleReportRequest)` — transition
  `PENDING → REVIEWED|DISMISSED` + audit (`reviewedAt`, `reviewedBy`, `moderationNote`).

---

### CommentLike

Owned by **engagement-service** (sous-package engagement/comment, SCRUM-144 Décision H).
Table : `comment_likes` (créée par la migration `V4__create_comment_likes.sql`).
Back-end pour la sémantique `likedByMe` + `likeCount` côté `Comment`.

| Champ Java | Type Java | Colonne DB | Contraintes |
|---|---|---|---|
| `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `commentId` | `Long` | `comment_id` | not null, FK `comments(id) ON DELETE CASCADE` |
| `userId` | `UUID` | `user_id` | not null (pas de FK cross-DB — DB-per-service strict) |
| `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, `@PrePersist` |

Index DB : `idx_comment_like_user` sur `(user_id)` pour le batch fetch
`findLikedCommentIdsByUser`. UK : `uq_comment_like` sur `(comment_id, user_id)` — empêche
le double-like et sert de filet à la sémantique idempotente service-side.

Cascade FK `ON DELETE CASCADE` : un `DELETE /comments/{id}` purge automatiquement les
rows likes correspondantes (pas de service-side cleanup nécessaire).

Helpers Panache :
- `findByCommentAndUser(Long, UUID) : Optional<CommentLike>` — pré-check happy idempotent.
- `findLikedCommentIdsByUser(Collection<Long>, UUID) : Set<Long>` — JPQL projection batch
  consommée par `CommentService.getByEvent` pour peupler `CommentDTO.likedByMe` en une
  seule requête (anti N+1, Décision K).

Sémantique service-side (`CommentLikeService`, Décision I) : `like()` retourne 201 sur
fresh-like, 200 idempotent sur double-tap (race-safe via catch `PersistenceException`) ;
`unlike()` retourne 204 quel que soit l'état initial. Le champ `Comment.likeCount` est
mis à jour atomiquement dans la même transaction via JPQL `UPDATE Comment c SET ...`.

---

### EventAttachment

Owned by **event-service** (sous-package event/attachment, SCRUM-148 Décision P).
Table : `event_attachments` (créée par la migration `V13__create_event_attachments.sql`).

| Champ Java | Type Java | Colonne DB | Contraintes |
|---|---|---|---|
| `id` | `Long` | `id` | PK, sequence `event_attachments_seq` (start 1, increment 50) |
| `eventId` | `Long` | `event_id` | not null, FK `events(id) ON DELETE CASCADE` |
| `fileName` | `String` | `file_name` | not null, `length=255` |
| `fileUrl` | `String` | `file_url` | not null, `columnDefinition="TEXT"` — URL S3 absolue |
| `fileSize` | `Long` | `file_size` | not null, CHECK `<= 10485760` (10 MiB) |
| `mimeType` | `String` | `mime_type` | not null, `length=128`, CHECK whitelist (6 valeurs depuis SCRUM-149) |
| `uploadedById` | `UUID` | `uploaded_by_id` | not null (pas de FK cross-DB) |
| `uploadedAt` | `LocalDateTime` | `uploaded_at` | not null, `@Column(updatable=false)`, `@PrePersist` |

Index DB : `idx_event_attachment_event` sur `(event_id)` pour les helpers `findByEvent`
+ `countByEvent`. **Pas d'UK** sur `(event_id, file_name)` — duplicates acceptés
(Décision P : un utilisateur peut uploader `presentation.pdf` v1 puis v2 ; le frontend
les distingue via file_size + uploaded_at).

CHECK constraints (last line of defense — le service layer rejette en 422 avant) :
- `event_attachments_size_check` : `file_size <= 10485760` (10 MiB).
- `event_attachments_mime_check` : `mime_type IN (6 MIME : PDF / DOC / DOCX / XLSX / PNG / JPEG)`.
  Élargi en SCRUM-149 par la migration `V14__widen_event_attachments_mime_whitelist.sql`
  (DROP + ADD CHECK pour intégrer `image/png` + `image/jpeg`) — V13 reste immutable
  (piège #12 Flyway).

Cascade :
- FK `ON DELETE CASCADE` auto-purge les rows attachments quand un event est hard-deleted
  via `EventService.delete()` (post-CANCELLED).
- `EventService.delete()` ajoute aussi un cleanup S3 best-effort hors-tx (Décision T) :
  `FileStorageService.deleteObject(url)` est invoqué par fichier ; les échecs sont
  swallowed avec un WARN log (orphan objects rares, mais documentés — devops-handoff).
- `EventService.duplicate()` (SCRUM-99 phase 1) **n'est pas** étendu (Décision AC) — le
  clone DRAFT démarre vide ; l'organisateur ré-upload manuellement.

Helpers Panache :
- `findByEvent(Long eventId) : List<EventAttachment>` — sorted by `uploadedAt ASC, id ASC`,
  consommé par `EventService.getById` (Décision Q) et `EventService.delete` cascade.
- `countByEvent(Long eventId) : long` — utilisé par `EventAttachmentService.upload` pour
  enforcer le cap de **5 attachments par event** (Décision V — 422
  `attachment_limit_exceeded` au-delà).

Permissions (Décision V) :
- `POST /events/{id}/attachments` : creator OR co-org ACCEPTED OR admin.
- `DELETE /events/{id}/attachments/{aid}` : creator OR co-org ACCEPTED OR admin OR
  **uploader d'origine** (un co-org peut supprimer son propre upload même après
  changement de statut).
- Anti-oracle 404 sur path mismatch DELETE (`attachmentId` valide mais autre `eventId`)
  — jamais 403.

Exposition wire-format : `AttachmentDTO` (id, fileName, fileUrl, fileSize, mimeType,
uploadedById, uploadedAt). Embarqué asymétriquement dans `EventDTO.attachments`
**uniquement** par `GET /events/{id}` (Décision Q — null sur tous les autres endpoints).

---

### Comment

Owned by **engagement-service** (sous-package engagement/comment, post-consolidation Étape 2.4.1). Table : `comments`.

Table : `comments` (créée par la migration `V14__create_comments.sql` en SCRUM-139).

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` (sequence `comments_seq`, increment 50) |
| `event` | — | `Event` | `event_id` | `@ManyToOne(LAZY)`, `@JoinColumn(nullable=false)` — FK vers `events.id` |
| `author` | — | `User` | `author_id` | `@ManyToOne(LAZY)`, `@JoinColumn(nullable=false)` — FK vers `users.id` |
| `parentComment` | — | `Comment` | `parent_comment_id` | `@ManyToOne(LAZY)`, nullable — auto-référence vers `comments.id` (1 niveau de profondeur max) |
| `content` | `content` | `String` | `content` | `@Column(columnDefinition="TEXT", nullable=false)`, `@NotBlank`, `@Size(max=500)`, trimmé côté service avant persist |
| `likeCount` | `likeCount` | `int` | `like_count` | `@Column(nullable=false)`, default `0`. **Lecture seule en S6** — la mutation est livrée par SCRUM-144 (S7) |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false, nullable=false)`, initialisé via `@PrePersist` (null-guard) |

Index DB : `idx_comment_event` (event_id), `idx_comment_parent` (parent_comment_id),
`idx_comment_event_created` (event_id, created_at DESC) — composite descendant pour
servir le `ORDER BY createdAt DESC, id DESC` du listing top-level sans scan séquentiel.

Pas de contrainte unique métier sur la table — un user peut poster N commentaires sur
un event.

#### Sémantique du threading (1 niveau de profondeur max)

- `parentComment IS NULL` : commentaire **top-level**, sérialisé avec
  `parentCommentId: null` côté DTO et accompagné de ses replies dans `replies[]` quand
  il sort de `getByEvent`.
- `parentComment NOT NULL` : **reply** à un commentaire top-level. Le service vérifie
  que `parentComment.parentComment IS NULL` au moment du POST — sinon `422 replies_too_deep`.
- Si le commentaire pointé par `parentCommentId` appartient à un autre event que `eventId`,
  le service rejette avec `422 parent_comment_not_in_event`.
- DELETE physique d'un parent : les replies survivent grâce à la clause
  `ON DELETE SET NULL` portée par la FK `fk_comments_parent`. Le `parent_comment_id`
  des replies passe à `NULL` côté DB, et chacune d'elles remonte en **top-level**
  dans `GET /events/{id}/comments` (le DTO expose alors `parentCommentId: null`).
  Pas de cascade `ON DELETE CASCADE` — décision tranchée (SCRUM-139 décision 17)
  pour préserver l'historique conversationnel sans modal mass-delete.

#### Visibilité héritée de `Event`

`CommentService.post(...)` et `CommentService.getByEvent(...)` délèguent **systématiquement**
la garde anti-oracle ISSUE-92 à `EventService.getById(eventId, callerAuth0Id, isAdmin)` —
event invisible (DRAFT/CANCELLED/BANNED non-créateur) → `404 not_found`. Au-delà de cette
garde :

| `event.status` | Caller | Réponse `POST /comments` |
|---|---|---|
| `PUBLISHED` | tout authentifié | `201 Created` |
| `DRAFT` | créateur / co-org ACCEPTED / ADMIN | `400 cannot_comment_draft_event` |
| `DRAFT` | autre user | `404 not_found` (anti-oracle) |
| `CANCELLED` | créateur / co-org ACCEPTED / ADMIN | `400 cannot_comment_cancelled_event` |
| `CANCELLED` | autre user | `404 not_found` (anti-oracle) |
| `EXPIRED` | créateur / co-org ACCEPTED / ADMIN | `400 cannot_comment_expired_event` |
| `EXPIRED` | autre user | `404 not_found` (anti-oracle) |
| `BANNED` | tout monde, admin compris | `404 not_found` (cf. SCRUM-97) |

#### Cascade d'autorisation pour `DELETE`

`CommentService.delete(...)` autorise (cf. décision 16) :

1. l'**auteur** du commentaire (`comment.author.auth0Id == caller.auth0Id`),
2. le **créateur** de l'event (via `EventService.isCreatorOrAcceptedCoOrganizerPublic`),
3. un **co-organisateur ACCEPTED** de l'event (cascade SCRUM-136),
4. un utilisateur **ADMIN** (claim Auth0).

Sinon → `403 forbidden`. `commentId` inexistant → `404 comment_not_found` (envelope
distincte du 404 anti-oracle event, car l'existence d'un commentId n'est pas un secret —
le listing `GET /events/{id}/comments` est `@PermitAll` et liste déjà tout).

#### Calcul de `authorIsOrganizer` (DTO)

`CommentDTO.authorIsOrganizer` est `true` quand l'auteur est créateur de l'event OU
co-organisateur ACCEPTED. Pour `getByEvent`, le calcul est fait en **bulk** via un
`Set<UUID>` mémoïsant `{event.creator.id} ∪ {co-orgs ACCEPTED}` — testé en O(1) pour
chaque commentaire de la page (cf. décision 27, évite le N+1).

#### Consommation par `CommentService`

- `CommentService.post(auth0Id, eventId, CreateCommentRequest)` (`@Transactional`) —
  visibilité event, vérification du parent (existence, appartenance, profondeur),
  trim du content, persist, projection DTO.
- `CommentService.getByEvent(eventId, auth0Id, page, size)` — non-transactional ;
  paginé sur top-level (`createdAt DESC, id DESC`), batch-load des replies en
  **2 requêtes SQL** (top-level page + WHERE parent_comment_id IN (...)).
- `CommentService.delete(auth0Id, commentId)` (`@Transactional`) — DELETE physique
  conditionné par la cascade d'autorisation ci-dessus.

#### Anticipation S7 — likes & report-comment (hors scope SCRUM-139)

- `likeCount` (int, default `0`) et `likedByMe` (boolean, toujours `false` dans le DTO
  S6) sont exposés dès maintenant pour figer le contrat consommé par SCRUM-146 (front S7) ;
  la mutation viendra avec SCRUM-144 (entité `CommentLike`, `POST/DELETE /comments/{id}/like`).
- `POST /comments/{id}/report` (et l'extension `Report.commentId`) sont également SCRUM-144 — hors scope ici.
- Notifications (`NEW_COMMENT` à l'organisateur, `COMMENT_MENTION`) sont SCRUM-145 (S7+),
  dépendantes de l'infra `Notification` SCRUM-99.

---

### Notification

Owned by **notification-service** (SCRUM-99 — service activé depuis placeholder en
phase 1). Table : `notifications`.

Table : `notifications` (créée par la migration `V1__create_notifications.sql` en
SCRUM-99). Aucune FK cross-DB (`postgres-notification` ne voit pas
`postgres-user.users` ni `postgres-event.events`) — la cohérence UUID est garantie
au niveau applicatif (Décision F).

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity`, sequence `notifications_seq` |
| `userId` | `userId` | `UUID` | `user_id` | not null — destinataire (sans FK cross-DB) |
| `type` | `type` | `NotificationType` | `type` | `@Enumerated(STRING)`, not null, `length=32`, CHECK constraint |
| `eventId` | `eventId` | `Long` | `event_id` | nullable — event lié quand applicable (`EVENT_*` et `NEW_ATTENDEE`) |
| `relatedUserId` | `relatedUserId` | `UUID` | `related_user_id` | nullable — acteur secondaire (phase 1 : attendee pour `NEW_ATTENDEE` ; phase 2 : follower pour `NEW_FOLLOWER`/`FOLLOW_REQUEST`/`FOLLOW_ACCEPTED`, mentionner pour `COMMENT_MENTION`) |
| `message` | `message` | `String` | `message` | not null, `@Column(columnDefinition="TEXT")` — message pré-composé par le consumer Kafka |
| `read` | `read` | `boolean` | `read` | not null, default `false` |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | not null, `updatable=false`, initialisé par `@PrePersist` |
| `readAt` | `readAt` | `LocalDateTime` | `read_at` | nullable, posé à `now()` lors de `markRead` (immutable une fois posé — idempotence) |

CHECK constraints :
- `notifications_type_check` (V1) : `type IN ('EVENT_UPDATED', 'EVENT_CANCELLED',
  'EVENT_REMINDER', 'NEW_ATTENDEE')` en phase 1. **Phase 2** : drop+recreate via
  `V2__widen_notification_type_check.sql` pour ajouter `NEW_FOLLOWER`,
  `FOLLOW_REQUEST`, `FOLLOW_ACCEPTED`, `COMMENT_MENTION`, `NEW_COMMENT`.

Index DB :
- `idx_notification_user_read_created` sur `(user_id, read, created_at DESC)` —
  sert le tri unread-first du listing sans table scan.
- `idx_notification_user_created` sur `(user_id, created_at DESC)` — listing
  tout-statut + cleanup futur.

Pas d'UK métier en phase 1 (cf. Décision D — at-least-once Kafka accepté ; deux
livraisons identiques produisent deux rows, c'est la sémantique voulue pour
`EVENT_UPDATED` notamment).

#### Helpers statiques

- `Notification.findByUser(UUID userId, int page, int size)` — paginé avec tri
  `read ASC NULLS FIRST, createdAt DESC, id DESC` (unread first).
- `Notification.findByIdAndUser(Long id, UUID userId): Optional<Notification>` —
  anti-oracle 404 (Optional vide quand la row appartient à un autre user OU
  n'existe pas).
- `Notification.countUnreadByUser(UUID userId): long` — populé dans le header
  `X-Unread-Count` sur le GET listing.
- `Notification.markAllReadByUser(UUID userId): int` — bulk update via
  `update("read = true, readAt = ?1 where userId = ?2 and read = false", ...)`
  retournant le nombre de rows affectées (input de `ReadAllResponse.updated`).

#### Sémantique anti-oracle

`NotificationService.markRead(auth0Id, id)` jette `NotFoundException` quand la
row n'existe pas OU appartient à un autre user — le frontend reçoit un 404
indistinguable d'un id inexistant, jamais un 403 qui leakerait l'existence
de la notif.

#### Pipeline Kafka

Les rows `notifications` sont créées exclusivement par les 3 consumers Kafka
de notification-service (phase 1, SCRUM-99) :
- `EventCancelledConsumer` ← topic `events.cancelled` → un row `EVENT_CANCELLED`
  par attendee `ATTENDING` (créateur skippé s'il est dans la liste).
- `EventUpdatedConsumer` ← topic `events.updated` → idem mais `EVENT_UPDATED`.
- `AttendanceCreatedConsumer` ← topic `attendances.created` → un row `NEW_ATTENDEE`
  vers le créateur (skippé si auto-inscription).

#### Anticipation phase 2

L'enum `NotificationType` et la CHECK contrainte resteront à 4 valeurs jusqu'à
la livraison conjointe de SCRUM-140 (notifications de suivi) + SCRUM-145
(notifications de mentions / commentaires) qui ajouteront `NEW_FOLLOWER`,
`FOLLOW_REQUEST`, `FOLLOW_ACCEPTED`, `COMMENT_MENTION`, `NEW_COMMENT`
**simultanément** avec leurs consumers Kafka respectifs et la migration
`V2__widen_notification_type_check.sql`.

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
Profil public — retourné via `GET /users/{id}`. Format :

```
id, displayName, faculty, studyLevel, bio, interests, avatarUrl, bannerUrl,
followerCount (long), followingCount (long), followStatus (FollowStatus | null)
```

Trois factories (cf. SCRUM-138) :

- `UserPublicResponse.from(User u)` — legacy. Compteurs à 0, followStatus null.
  Utilisée par les items des listes followers / following (les compteurs ne font sens
  que sur le profil cible, pas sur les items de la liste).
- `UserPublicResponse.from(User u, long followerCount, long followingCount, FollowStatus followStatus)`
  — factory enrichie utilisée par `UserResource.getProfile` pour les appelants
  authentifiés.
- `UserPublicResponse.fromAnonymous(User u)` — factory anonyme (ISSUE-93 finding 4.1b).
  Tous les champs sensibles `null`, compteurs à 0, followStatus null. Court-circuit
  servi sans appel à `FollowService`.

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
| `EventStatus` | `DRAFT`, `PUBLISHED`, `CANCELLED`, `EXPIRED`, `BANNED` | Sprint 2 | ✅ Implémenté — `EXPIRED` ajouté SCRUM-98, `BANNED` ajouté SCRUM-97 (modération : état terminal côté créateur, posé par `ReportService.handle()` ou `ModerationCleanupJob`) |
| `Faculty` | `SCIENCES`, `LETTRES`, `DROIT`, `MEDECINE`, `SES`, `PSYCHOLOGIE`, `THEOLOGIE`, `FTI`, `GSI` | Sprint 3 | ✅ Implémenté (SCRUM-77) |
| `AttendanceStatus` | `ATTENDING`, `WAITLISTED` | Sprint 4 / Sprint 5 | ✅ Implémenté (WAITLISTED ajouté en SCRUM-129) |
| `CoOrganizerStatus` | `PENDING`, `ACCEPTED`, `DECLINED` | Sprint 7 | ✅ Implémenté (SCRUM-136 — `DECLINED` est transitoire et n'apparaît jamais en base, cf. section EventCoOrganizer) |
| `ReportStatus` | `PENDING`, `REVIEWED`, `DISMISSED` | Sprint 7 | ✅ Implémenté (US-18) |
| `ReportReason` | `SPAM`, `INAPPROPRIATE`, `FAKE`, `OTHER` | Sprint 7 | ✅ Implémenté (SCRUM-94 — CHECK constraint posée par V9) |
| `FollowStatus` | `PENDING`, `ACCEPTED` | Sprint 6 | ✅ Implémenté (SCRUM-138 — un reject = DELETE physique de la row, `REJECTED` n'est pas un statut stocké) |

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

- Migrations : redistribuées par service propriétaire post-Étape 1.1 finalization-complete (Décision A) sous `backend/services/<svc>-service/src/main/resources/db/migration/V<N>__<snake_case_description>.sql`. Mapping :
  - `user-service` : V1 (`create_users`), V14 (`create_follows`).
  - `event-service` : V2 (`create_events`), V4 (`create_favorites`), V5 (`create_event_views`), V7 (`reconcile_check_constraints`), V8 (`create_event_co_organizers`), V9 (`widen_event_description`), V11 (`allow_event_status_expired`), V12 (`add_featured_to_events`), V13 (`allow_event_status_banned`), V17 (`add_event_recurrence`).
  - `engagement-service` : V3 (`create_attendances`), V15 (`create_comments`), V16 (`alter_comments_parent_fk_set_null`).
  - `moderation-service` : V6 (`create_reports`), V10 (`add_report_reason_and_review_fields`).
- Hibernate est en `validate` en dev/prod : il vérifie que les entités JPA correspondent au schéma migré, sans le modifier. En `%test`, Hibernate est en `drop-and-create` pour bootstrapper la base éphémère DevServices et Flyway est désactivé (`%test.quarkus.flyway.enabled=false`).
- Stratégie d'adoption : `baseline-on-migrate=true` + `baseline-version=0` + `out-of-order=true` + `validate-on-migrate=false`. Chaque service applique son sous-ensemble — la base partagée `public` voit l'union des V*.sql sans qu'aucun service ne réclame la totalité des versions.
- Une migration committée est **immutable** : pour modifier le schéma, ajouter un nouveau fichier `V<N+1>__…` dans le service propriétaire.

### V1 — Réconciliation des contraintes CHECK

`V1__reconcile_check_constraints.sql` est la première migration : elle drop+recrée `events_faculty_check`, `events_category_check`, `events_status_check` et `attendances_status_check` avec les valeurs courantes des enums Java. Elle remplace l'ancien bean `SchemaFixup` qui faisait le même travail au démarrage.

> **À surveiller pour `event_co_organizers_status_check` (SCRUM-136).** La table
> `event_co_organizers` est créée par la migration `V8__create_event_co_organizers.sql`,
> qui pose la CHECK initiale sur `CoOrganizerStatus`. Toute future modification de l'enum
> (ajout d'une valeur, rename) **devra** passer par un nouveau fichier `V<N+1>__…` qui
> drop+recrée la contrainte avec les valeurs courantes — la convention Flyway interdit de
> muter une migration committée.

## S3 cleanup hors-transaction (MINOR-010 + MINOR-011)

### Préambule (FR / EN)

- **FR** — Sur `UserService.uploadImage` / `uploadBanner`, l'objet S3 est
  écrit AVANT le commit JDBC qui mémorise l'URL. Un crash entre les deux
  laisse un orphelin S3. Idem pour les delete : la ligne JDBC est mise à
  jour, l'objet S3 est supprimé après commit ; un crash entre les deux
  laisse l'objet présent dans le bucket alors que la DB ne le référence
  plus.
- **EN** — On `UserService.uploadImage` / `uploadBanner`, the S3 object
  is written BEFORE the JDBC commit that records the URL. A crash
  between the two leaves an orphaned S3 object. Same for delete: the JDBC
  row is updated, then the S3 object is deleted after commit; a crash in
  between leaves the object in the bucket while the DB no longer
  references it.

### Limitation acceptée

Pas d'outbox pattern sur S3 (cf. ADR-003 — outbox réservé aux topics
Kafka critiques). La JavaDoc des méthodes concernées documente la
tolérance aux orphelins. Un cleanup périodique S3 est reporté à S9+
(devops-handoff item dédié).

### Méthodes concernées

- `user-service.UserService.uploadImage(...)` (avatar)
- `user-service.UserService.uploadBanner(...)`
- `user-service.UserService.deleteAvatar(...)` (delete S3 + reset URL)
- `user-service.UserService.deleteBanner(...)` (NB: ne supprime PAS
  l'objet S3 par dessein — cf. JavaDoc inline)

### Mitigation

- Bucket public read-only ; risque sécurité limité à un orphelin
  inaccessible (URL perdue).
- Bucket lifecycle policy S9+ : auto-delete after 30j d'inactivité.
