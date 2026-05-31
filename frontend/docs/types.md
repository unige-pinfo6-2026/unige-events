# docs/types.md — Types TypeScript

Les types vivent dans `src/types/` et sont répartis par domaine.
Source de vérité contrat API : `docs/openapi/openapi.yaml`.

## Types faculté — `src/types/faculty.ts`

### FACULTIES (const object) + Faculty

Source unique pour tout ce qui concerne les facultés. `Faculty` est dérivé via `keyof typeof FACULTIES`.

```ts
export const FACULTIES = { SCIENCES: { … }, MEDICINE: { … }, … } as const
export type Faculty = keyof typeof FACULTIES
// → 'SCIENCES' | 'MEDICINE' | 'LETTERS' | 'SOCIAL_SCIENCES' | 'GSEM' | 'LAW' | 'THEOLOGY' | 'PSYCHOLOGY' | 'FTI'
```

Chaque entrée expose : `name` (libellé complet), `abbr` (abréviation), `logo` (composant SVG), `color` (couleur hex officielle UNIGE).

| Clé             | name                                                        | abbr        | color     |
|-----------------|-------------------------------------------------------------|-------------|-----------|
| SCIENCES        | Faculté des Sciences                                        | Sciences    | `#318063` |
| MEDICINE        | Faculté de Médecine                                         | Médecine    | `#9a0050` |
| LETTERS         | Faculté des Lettres                                         | Lettres     | `#046fcb` |
| SOCIAL_SCIENCES | Faculté des Sciences de la Société                          | SdS         | `#fcb000` |
| GSEM            | Faculté d'Économie et de Management                         | GSEM        | `#425878` |
| LAW             | Faculté de Droit                                            | Droit       | `#ba0c2f` |
| THEOLOGY        | Faculté Autonome de Théologie Protestante                   | Théologie   | `#490674` |
| PSYCHOLOGY      | Faculté de Psychologie et des Sciences de l'Éducation       | Psychologie | `#00b1ae` |
| FTI             | Faculté de Traduction et d'Interprétation                   | FTI         | `#fe5900` |

> `SOCIAL_SCIENCES` et `GSEM` utilisent le logo `Economy` en placeholder — logos dédiés à créer dans `src/assets/faculty/`.

---

## Types événements — `src/types/event.ts`

### Faculty

Re-exporté depuis `src/types/faculty.ts` — voir section ci-dessus. `event.ts` l'importe via `import type { Faculty } from "./faculty"`.

### EventCategory

Dérivé de `EVENT_CATEGORIES` (const object). Valeurs : `ACADEMIC`, `SPORTS`, `CULTURAL`, `SOCIAL`, `CONFERENCE`, `OTHER`.

Chaque entrée expose `name` (libellé français) et `color` (couleur hex) :

| Clé        | Libellé    | Couleur   |
|------------|------------|-----------|
| ACADEMIC   | Académique | `#2563eb` |
| SPORTS     | Sports     | `#16a34a` |
| CULTURAL   | Culturel   | `#9333ea` |
| SOCIAL     | Social     | `#ea580c` |
| CONFERENCE | Conférence | `#0891b2` |
| OTHER      | Autre      | `#6b7280` |

### EventStatus

Dérivé de `EVENT_STATUSES` (const object). Valeurs : `DRAFT`, `PUBLISHED`, `CANCELLED`.

Chaque entrée expose `name` (libellé français). Le frontend n'expose `DRAFT` et `PUBLISHED` dans EventForm ; `CANCELLED` est filtré.

### Event

| Champ       | Type          | Requis |
|-------------|---------------|--------|
| id          | number        | oui    |
| title       | string        | oui    |
| description | string        | non    |
| location    | string        | oui    |
| startDate   | string ISO 8601 | oui  |
| endDate     | string ISO 8601 | oui  |
| category    | EventCategory | oui    |
| faculty     | Faculty \| null | non    |
| bannerUrl   | string        | non    |
| creatorId   | string        | oui    |
| status      | EventStatus   | oui    |
| capacity        | number        | non    |
| availableSpots  | number \| null | non   |
| waitlistedCount | number        | non    |
| viewCount       | number \| null | non   |
| interestedCount | number \| null | non   |
| allDay          | boolean       | non    |
| attendingCount  | number        | non    |
| featured        | boolean       | non    |
| featuredAt      | string ISO 8601 \| null | non |
| websiteUrl              | string \| null | non |
| contactEmail            | string \| null | non |
| registrationDeadline    | string ISO 8601 \| null | non |
| tags                    | string[]       | non |
| createdAt       | string        | oui    |
| updatedAt       | string        | non    |
| parentEventId   | number \| null | non    |
| recurrenceRule  | string \| null | non    |
| attachments     | Attachment[] \| null | non |

`attachments` (SCRUM-149) suit la même asymétrie que `viewCount` / `interestedCount` :
peuplé **uniquement** par `GET /events/{id}`, retourné à `null` par tous les autres
endpoints. Voir `Attachment` ci-dessous.

`parentEventId` et `recurrenceRule` (SCRUM-151) sont remplis par le backend SCRUM-147 :
- `parentEventId != null` → l'événement est une **occurrence** d'un cycle ; pointe vers l'event parent.
- `recurrenceRule != null` → l'événement est le **parent** d'un cycle (chaîne RFC 5545, ex. `FREQ=WEEKLY;UNTIL=20260601`).
- Les deux à `null` → événement standalone.

Le frontend ne **mute jamais** ces champs (lecture seule consommée par `EventCard` pour le badge `Récurrent` et par `EventDetailPage` pour la section repliable des occurrences).

**Constantes de validation frontend** (`src/types/event.ts`) :
- `EVENT_WEBSITE_URL_MAX_LENGTH = 500` — longueur max de `websiteUrl`
- `EVENT_CONTACT_EMAIL_MAX_LENGTH = 255` — longueur max de `contactEmail`
- `EVENT_TAG_MAX_LENGTH = 16` — longueur max d'un tag individuel
- `EVENT_TAGS_MAX_ITEMS = 20` — nombre max de tags par événement
- `RECURRENCE_MAX_OCCURRENCES = 52` — borne haute du nombre d'occurrences (miroir du `@Max(52)` backend SCRUM-147)

**Compteurs publics `viewCount` / `interestedCount`** : renseignés uniquement
sur `GET /events/{id}` (page détail). Les endpoints de liste/recherche
retournent `null` pour ces champs afin d'éviter des requêtes N+1. Le
composant `EventStatsPanel` affiche `—` quand la valeur est `null` /
`undefined`.

### CreateEventRequest

Champs requis : `title`, `location`, `startDate`, `endDate`, `category`.
Champs optionnels : `description`, `faculty`, `bannerUrl`, `capacity`, `status`, `allDay`, `websiteUrl`, `contactEmail`, `registrationDeadline`, `tags`, `recurrence`.

`websiteUrl`, `contactEmail`, `registrationDeadline` acceptent `null` pour effacer la valeur ; `tags` accepte `null` ou `string[]`. Le frontend envoie `null` plutôt qu'une chaîne vide lorsque l'utilisateur laisse le champ vide.

`recurrence` (SCRUM-151) est envoyé **uniquement** quand le switch « Récurrence » de `EventForm` est activé. Cf. `RecurrenceRequest` ci-dessous.

### UpdateEventRequest

Le backend utilise un PUT à sémantique de remplacement complet. Le frontend envoie systématiquement un payload complet avec les mêmes champs que `CreateEventRequest`, **à l'exception** de `recurrence` qui est toujours absent — Décision E SCRUM-151 (la section n'est pas exposée en edit, le PUT ne propage rien aux occurrences côté backend SCRUM-147 D17).

### RecurrenceFrequency, RECURRENCE_FREQUENCIES, RecurrenceRequest

Const map typée (`src/types/event.ts`) miroir du backend SCRUM-147 :

```ts
export const RECURRENCE_FREQUENCIES = {
  WEEKLY:   { name: 'Chaque semaine' },
  BIWEEKLY: { name: 'Toutes les 2 semaines' },
  MONTHLY:  { name: 'Chaque mois' },
} as const

export type RecurrenceFrequency = keyof typeof RECURRENCE_FREQUENCIES
```

`RecurrenceRequest` :

| Champ           | Type              | Notes |
|-----------------|-------------------|-------|
| frequency       | RecurrenceFrequency | toujours présent |
| endDate         | string \| null    | format `YYYY-MM-DD` ; exclusif avec `maxOccurrences` (Décision B) |
| maxOccurrences  | number \| null    | entier ∈ `[1, 52]` ; exclusif avec `endDate` |

Le frontend impose la **mutex** `endDate ↔ maxOccurrences` au niveau du form. Le payload sortant n'a jamais les deux champs renseignés en même temps.

### Attachment (SCRUM-149)

`src/types/attachment.ts` — miroir de `AttachmentDTO` (openapi).

| Champ        | Type              | Notes |
|--------------|-------------------|-------|
| id           | number            | PK séquentielle |
| fileName     | string            | nom du fichier original (≤ 255) |
| fileUrl      | string            | path S3 absolu — **usage interne backend uniquement**. Pointe sur `minio:9000` qui n'est pas exposé publiquement → ne JAMAIS l'utiliser comme `href` côté frontend. |
| downloadUrl  | string            | URL same-origin (`/api/events/{eventId}/attachments/{id}/download`) à utiliser pour télécharger. Le backend streame depuis MinIO avec `Content-Disposition: attachment` (SCRUM-149 follow-up). |
| fileSize     | number            | taille en bytes (max 10 MiB) |
| mimeType     | AttachmentMimeType | PDF / DOC / DOCX / XLSX / PNG / JPEG |
| uploadedById | string (UUID)     | uploader (créateur OU co-organisateur ACCEPTED OU admin) |
| uploadedAt   | string ISO 8601   | timestamp serveur, immutable |

Const map associée :

```ts
export const ATTACHMENT_MIME_TYPES = {
  'application/pdf':                                                            { label: 'PDF',  extension: '.pdf'  },
  'application/msword':                                                         { label: 'DOC',  extension: '.doc'  },
  'application/vnd.openxmlformats-officedocument.wordprocessingml.document':    { label: 'DOCX', extension: '.docx' },
  'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet':          { label: 'XLSX', extension: '.xlsx' },
  // SCRUM-149 follow-up — PNG et JPEG ajoutés en plus des documents,
  // backend `DocumentFormat` + V14 migration en miroir.
  'image/png':                                                                  { label: 'PNG',  extension: '.png'  },
  'image/jpeg':                                                                 { label: 'JPEG', extension: '.jpg'  },
} as const
export type AttachmentMimeType = keyof typeof ATTACHMENT_MIME_TYPES
```

Constantes de validation (miroir backend SCRUM-148 + élargissement SCRUM-149) :

- `ATTACHMENT_ACCEPT_ATTR = '.pdf,.doc,.docx,.xlsx,.png,.jpg,.jpeg'`
- `ATTACHMENT_MAX_BYTES = 10 * 1024 * 1024` (10 MiB)
- `ATTACHMENT_MAX_PER_EVENT = 5`

Garde défensive `isAcceptedAttachmentFile(file)` : accepte si **soit** `file.type` est dans la whitelist, **soit** l'extension du nom est dans la map `EXTENSION_TO_MIME` (fallback drag-and-drop / OS qui ne remplit pas `file.type` ; `.jpeg` est mappé en plus de `.jpg`).

---

## Types utilisateur — `src/types/user.ts`

### User

| Champ         | Type       | Requis |
|---------------|------------|--------|
| id            | string     | oui    |
| auth0Id       | string     | oui    |
| email         | string     | oui    |
| username      | string     | oui (SCRUM-169 — pattern `^[a-z0-9._-]{3,30}$`, lowercase, modifiable via `PATCH /users/me/username`) |
| displayName   | string     | non    |
| firstName     | string     | non    |
| lastName      | string     | non    |
| faculty       | string     | non    |
| studyLevel    | string     | non    |
| bio           | string     | non    |
| interests     | string[]   | non    |
| avatarUrl     | string     | non    |
| bannerUrl     | string \| null | non |
| profilePublic | boolean    | oui    |
| createdAt     | string     | oui    |
| roles         | string[]   | non    | Rôles Auth0 du user authentifié, mirrorés par le backend à chaque `/users/me`. Liste vide par défaut. |

Helpers exportés (badge "Staff" sur les profils admins) :
- `STAFF_ROLE = 'ADMIN'` — rôle qui déclenche le badge.
- `isStaff(roles)` — guard `true` si la liste contient `STAFF_ROLE`, tolère `undefined`/`null`.

Constantes exportées (SCRUM-169) :
- `RESERVED_USERNAMES: Set<string>` — miroir de `UsernameGenerator.RESERVED` backend (`me`, `admin`, `api`, `login`, `logout`, `signup`, `register`, `settings`). Permet le live-check `reserved` côté form sans round-trip.
- `USERNAME_PATTERN: RegExp` — `/^[a-z0-9._-]{3,30}$/` (pattern miroir backend).
- `USERNAME_MIN_LENGTH = 3`, `USERNAME_MAX_LENGTH = 30`.

### StudyLevel

Dérivé de `STUDY_LEVELS` (const object). Valeurs : `BACHELOR`, `MASTER`, `DOCTORAT`, `POST_DOC`.

### UserPublicResponse

Profil public retourné par `GET /api/users/{id}` et `GET /api/users/by-username/{username}` quand le profil est public OU que le caller est owner/admin. Si le profil est privé et que le caller n'est ni owner ni admin, le backend retourne **404** (anti-oracle ISSUE-93, indistinguable d'un user inexistant).

| Champ          | Type                          | Requis | Notes |
|----------------|-------------------------------|--------|-------|
| id             | string                        | oui    | UUID |
| username       | string                        | oui    | SCRUM-169 — toujours exposé, même aux appelants anonymes (slug public). |
| displayName    | string \| null                | non    | |
| faculty        | string \| null                | non    | |
| studyLevel     | string \| null                | non    | |
| bio            | string \| null                | non    | |
| interests      | string[]                      | non    | |
| avatarUrl      | string \| null                | non    | |
| bannerUrl      | string \| null                | non    | |
| followerCount  | number                        | oui    | Nombre de followers ACCEPTED (toujours présent, `0` pour anonyme). |
| followingCount | number                        | oui    | Nombre d'abonnements ACCEPTED (toujours présent, `0` pour anonyme). |
| followStatus   | FollowStatus \| null          | non    | État de la relation caller → cible. `null` si anonyme, sur son propre profil, ou aucune row `Follow`. |
| roles          | string[]                      | non    | Rôles Auth0 **du profil affiché** (pas du viewer), mirrorés en base. Driver du badge "Staff" — `isStaff(profile.roles)`. Toujours présent côté API (liste vide par défaut). |

### FollowStatus

`'PENDING' | 'ACCEPTED'`

Cf. SCRUM-138. `PENDING` = demande de suivi envoyée par le caller, profil cible privé. `ACCEPTED` = suivi actif (mutuel ou non).

---

## Types suivi — `src/types/follow.ts`

### FollowDTO

Projection d'une row `Follow` renvoyée par les endpoints SCRUM-138 — id-only (pas d'enrichissement `displayName` / `avatarUrl` côté backend ; le frontend résout `getPublicProfile(followerId)` à la demande, cf. SCRUM-110 panneau "Demandes reçues").

| Champ        | Type           | Requis | Notes |
|--------------|----------------|--------|-------|
| id           | number         | oui    | PK séquentielle (`Long`) |
| followerId   | string         | oui    | UUID du `User` qui suit / a demandé à suivre |
| followedId   | string         | oui    | UUID du `User` ciblé |
| status       | FollowStatus   | oui    | `PENDING` ou `ACCEPTED` |
| createdAt    | string         | oui    | ISO date-time |

Sources : `openapi/openapi.yaml#/components/schemas/FollowDTO`. Endpoints producteurs : `POST /users/{id}/follow`, `PATCH /follow-requests/{id}/accept`, `GET /users/me/follow-requests`.

---

## Types recherche

### SearchParams

Paramètres envoyés à `GET /api/events/search`. Tous optionnels.
- q : string — terme de recherche full-text
- category : EventCategory — filtre catégorie (valeur unique)
- faculty : Faculty — filtre faculté (valeur unique de `Faculty` depuis `src/types/faculty.ts`)
- facultyNone : boolean — si `true`, filtre sur les événements dont `faculty` est null (non rattachés à une faculté précise). Mutuellement exclusif avec `faculty` côté UI ; côté serveur, `facultyNone` a priorité si les deux sont fournis.
- tags : string[] — filtre par mots-clés (au moins un tag commun). Sérialisé en `?tags=a&tags=b` sans crochets via `paramsSerializer: { indexes: null }` côté Axios.
- dateFrom : string (format date) — startDate >= dateFrom
- dateTo : string (format date) — startDate <= dateTo
- page : number (défaut 0)
- size : number (défaut 20)

### SearchResponse

`Event[]` — tableau d'événements (jamais null, jamais 404 si vide).

### SearchFilters

Définie dans `src/types/search.ts`.
Utilisée comme props par `FilterSidebar`.
Champs : `category?`, `faculty?`, `facultyNone?` (boolean — chip « Toutes facultés »), `tags?` (string[] — multi-tags OR), `dateFrom?`, `dateTo?`, `includePast` (boolean, défaut `false`).
Mutex `faculty` / `facultyNone` : l'UI garantit qu'au plus un des deux est actif. `includePast: false` → l'API reçoit `dateFrom = aujourd'hui` (les événements passés sont masqués par défaut).

## Types présence — `src/types/attendance.ts`

### AttendanceStatus

`'ATTENDING' | 'WAITLISTED'`

Le serveur assigne automatiquement `WAITLISTED` lorsque l'événement est complet (`availableSpots === 0`). Le frontend envoie toujours `ATTENDING` dans le body — c'est le backend qui détermine le statut final retourné.

### Attendance

| Champ       | Type             | Requis | Notes |
|-------------|------------------|--------|-------|
| id          | number           | oui    | |
| userId      | string \| null   | oui    | `null` sur `GET /events/{id}/attendees` quand la ligne est anonymisée pour un appelant non-organisateur (profil privé). Non-nul sur les autres routes. |
| eventId     | number           | oui    | |
| status      | AttendanceStatus | oui    | |
| createdAt   | string           | oui    | |
| displayName | string \| null   | oui    | Projection du nom côté backend ; `null` pour les lignes anonymisées par le filtre de confidentialité (SCRUM-S7) ou pour les inscriptions orphelines (user supprimé). |
| avatarUrl   | string \| null   | oui    | URL d'avatar si défini ; `null` quand anonymisée. |
| username    | string \| null   | oui    | SCRUM-169 — username public-facing du participant. Permet à `AttendeeCard` de construire `/profile/{username}` sans N+1. `null` pour ligne orpheline ou ligne anonymisée par le filtre SCRUM-S7. |

Correspond au schéma `Attendance` de l'OpenAPI (réponse de `POST /events/{id}/attend` et de `GET /events/{id}/attendees`).

**Filtre de confidentialité (SCRUM-S7) sur `GET /events/{id}/attendees`** : appliqué côté backend au niveau du DTO. Les créateurs, co-organisateurs ACCEPTED et admins reçoivent l'identité réelle pour toutes les lignes (y compris les profils privés). Les autres utilisateurs authentifiés reçoivent l'identité réelle pour les profils publics, et `userId=null`/`displayName=null`/`avatarUrl=null` pour les profils privés — l'UUID est volontairement masqué pour empêcher tout sondage de `GET /users/{id}` qui désanonymiserait le participant via le pattern 404. Les autres routes (`/users/me/attendances`, etc.) ne renvoient que des inscriptions appartenant au caller — `userId` y est toujours non-nul.

### AttendanceRequest

| Champ  | Type             | Requis |
|--------|------------------|--------|
| status | AttendanceStatus | oui    |

Body de `POST /events/{id}/attend`.

---

## Types calendrier — `src/types/calendarToken.ts`

### CalendarTokenResponse

Réponse de `GET /api/users/me/calendar-token` et `POST /api/users/me/calendar-token/regenerate`.

| Champ         | Type   | Requis |
|---------------|--------|--------|
| calendarToken | string (uuid) | oui |
| webcalUrl     | string | oui    |
| httpsUrl      | string | oui    |

`webcalUrl` utilise le protocole `webcal://` (Apple Calendar, Outlook).
`httpsUrl` est l'URL `https://` pour Google Calendar et le téléchargement direct.

---

## Types admin — `src/types/admin.ts`

### Report

Ligne du dashboard de modération (`GET /api/admin/reports`). Un signalement cible
**exactement** un event OU un commentaire (XOR).

| Champ | Type | Notes |
|---|---|---|
| `id` | number | |
| `targetType` | `'EVENT' \| 'COMMENT'` | Discriminateur (bug ③) — le `ReportRow` affiche soit l'event, soit le commentaire selon ce champ, plutôt que d'inférer via les id null. |
| `eventId` | number \| null | Renseigné pour un report d'event. |
| `commentId` | number \| null | Renseigné pour un report de commentaire. |
| `eventTitle` | string \| null | Titre projeté (report d'event). |
| `commentContent` | string \| null | Corps du commentaire signalé (report de commentaire). |
| `reporterId` / `reporterDisplayName` | string \| null | Signalant (null si compte supprimé). |
| `reason` | `ReportReason` | Motif catégoriel partagé event+comment. |
| `description` | string \| null | Texte libre. |
| `status` | `ReportStatus` | `PENDING` \| `REVIEWED` \| `DISMISSED`. |
| `moderationNote`, `createdAt`, `reviewedAt`, `reviewedBy` | | Audit. |

**Validation (`REVIEWED`)** : un report d'event → bannit l'event ; un report de
commentaire → **supprime** le commentaire (le `ReportRow` affiche « Bannir
l'événement » vs « Supprimer le commentaire », badge « Banni » vs « Supprimé »).

---

## Règles générales

- Les types d'entités vivent dans `src/types/` et ne doivent pas être redéfinis ailleurs.
- `EventCategory`, `EventStatus`, `StudyLevel`, `Faculty` sont dérivés via `keyof typeof` — ne pas les déclarer manuellement.
- `Faculty` est défini dans `src/types/faculty.ts` et importé partout ailleurs — ne jamais le redéfinir dans `event.ts` ou ailleurs.
- Les champs restent en camelCase exactement comme dans le backend.
- Le frontend ne doit pas utiliser `any`.
