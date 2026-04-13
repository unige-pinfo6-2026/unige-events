# docs/types.md — Types TypeScript

Les types vivent dans `src/types/` et sont répartis par domaine.
Source de vérité contrat API : `docs/openapi/openapi.yaml`.

## Types événements — `src/types/event.ts`

### Faculty

Défini dans `src/types/event.ts` comme **const object `as const` + type union dérivé** — pas un vrai `enum` TypeScript, mais exactement l'ensemble de valeurs de l'enum OpenAPI `Faculty`.

```ts
export const Faculty = { SCIENCES: 'SCIENCES', LETTRES: 'LETTRES', /* … */ } as const
export type Faculty = (typeof Faculty)[keyof typeof Faculty]
```

Valeurs : `SCIENCES`, `LETTRES`, `DROIT`, `MEDECINE`, `SES`, `PSYCHOLOGIE`, `THEOLOGIE`, `FTI`, `GSI`.

La const `FACULTY_LABELS: Record<Faculty, string>` est également dans `src/types/event.ts` (libellés français pour l'UI).

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
| attendingCount  | number        | non    |
| interestedCount | number        | non    |
| createdAt       | string        | oui    |
| updatedAt       | string        | non    |

### CreateEventRequest

Champs requis : `title`, `location`, `startDate`, `endDate`, `category`.
Champs optionnels : `description`, `faculty`, `bannerUrl`, `capacity`, `status`.

### UpdateEventRequest

Le backend utilise un PUT à sémantique de remplacement complet. Le frontend envoie systématiquement un payload complet avec les mêmes champs que `CreateEventRequest`.

---

## Types utilisateur — `src/types/user.ts`

### User

| Champ         | Type       | Requis |
|---------------|------------|--------|
| id            | string     | oui    |
| auth0Id       | string     | oui    |
| email         | string     | oui    |
| displayName   | string     | non    |
| firstName     | string     | non    |
| lastName      | string     | non    |
| faculty       | string     | non    |
| studyLevel    | string     | non    |
| bio           | string     | non    |
| interests     | string[]   | non    |
| avatarUrl     | string     | non    |
| profilePublic | boolean    | oui    |
| createdAt     | string     | oui    |

### StudyLevel

Dérivé de `STUDY_LEVELS` (const object). Valeurs : `BACHELOR`, `MASTER`, `DOCTORAT`, `POST_DOC`, `STAFF`.

---

## Types faculté — `src/types/faculty.ts`

### FACULTIES (const object)

Données d'affichage pour les composants visuels (`FacultyCard`, `FacultyMarquee`).
Clés : `SCIENCES`, `MEDECINE`, `LETTERS`, `ECONOMY`, `LAW`, `THEOLOGY`, `PSYCHOLOGY`, `TRANSLATION`.
Chaque entrée expose `name` (libellé complet) et `logo` (composant SVG).

> Note : le `Faculty` enum utilisé pour les événements et les filtres est dans `src/types/event.ts` — ses valeurs correspondent à l'enum OpenAPI.

---

## Types recherche

### SearchParams

Paramètres envoyés à `GET /api/events/search`. Tous optionnels.
- q : string — terme de recherche full-text
- category : EventCategory — filtre catégorie (valeur unique)
- faculty : Faculty — filtre faculté (valeur unique de l'enum `Faculty` dans `src/types/event.ts`)
- facultyNone : boolean — si `true`, filtre sur les événements dont `faculty` est null (non rattachés à une faculté précise). Mutuellement exclusif avec `faculty` côté UI ; côté serveur, `facultyNone` a priorité si les deux sont fournis.
- dateFrom : string (format date) — startDate >= dateFrom
- dateTo : string (format date) — startDate <= dateTo
- page : number (défaut 0)
- size : number (défaut 20)

### SearchResponse

`Event[]` — tableau d'événements (jamais null, jamais 404 si vide).

### SearchFilters

Définie dans `src/types/search.ts`.
Utilisée comme props par `FilterSidebar`.
Champs : `category?`, `faculty?`, `facultyNone?` (boolean — chip « Toutes facultés »), `dateFrom?`, `dateTo?`, `includePast` (boolean, défaut `false`).
Mutex `faculty` / `facultyNone` : l'UI garantit qu'au plus un des deux est actif. `includePast: false` → l'API reçoit `dateFrom = aujourd'hui` (les événements passés sont masqués par défaut).

## Types présence — `src/types/attendance.ts`

### AttendanceStatus

`'INTERESTED' | 'ATTENDING'`

### Attendance

| Champ     | Type   | Requis |
|-----------|--------|--------|
| id        | number | oui    |
| userId    | string | oui    |
| eventId   | number | oui    |
| status    | AttendanceStatus | oui |
| createdAt | string | oui    |

Correspond au schéma `Attendance` de l'OpenAPI (réponse de `POST /events/{id}/attend`).

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

## Règles générales

- Les types d'entités vivent dans `src/types/` et ne doivent pas être redéfinis ailleurs.
- `EventCategory`, `EventStatus`, `StudyLevel`, `Faculty` sont dérivés via `keyof typeof` — ne pas les déclarer manuellement.
- Les champs restent en camelCase exactement comme dans le backend.
- Le frontend ne doit pas utiliser `any`.
