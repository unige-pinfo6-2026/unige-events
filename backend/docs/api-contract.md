# API Contract — unige-events backend

> *Mentions of the dissolved-services (favorite/view/share/stats/me-aggregator/co-organizer → event-service co-located post-finalization ; follow/calendar → user-service co-located post-finalization ; attendance/comment → engagement-service renamed/co-located post-finalization ; report → moderation-service renamed post-finalization) are intentional historical references — see consolidation-plan.md for the 14→5 mapping.*

Root path : `/api` (configuré via `quarkus.http.root-path=api` dans `application.properties` de chaque microservice).

Tous les endpoints produisent et consomment `application/json` (sauf
`/calendar/{token}.ics` qui sert `text/calendar`).
Les endpoints authentifiés requièrent `Authorization: Bearer <jwt>` (Auth0/OIDC).

---

## Topologie — service propriétaire par préfixe

Post-finalisation Sprint 8 (consolidation 14→5), le backend est composé de
**4 services métiers actifs** + 1 placeholder (`notification-service` `replicas:0`,
SCRUM-99 hors scope). Kong DB-less route chaque path vers le service propriétaire
via une regex anchorée — cf. [`k8s/chart/templates/kong/configmap-routes.yaml`](../../k8s/chart/templates/kong/configmap-routes.yaml)
qui est la source de vérité runtime, et [`architecture.md`](architecture.md)
pour la topologie détaillée + flux requête type.

| Endpoint(s) | Service propriétaire |
|---|---|
| `/api/events`, `/api/events/{id}`, `/api/events/{id}/{cancel,restore,publish}`, `/api/events/{id}/occurrences`, `/api/events/{id}/image`, `/api/events/featured`, `/api/events/search`, `/api/admin/events/{id}/{,un}feature`, `/api/events/{id}/{share,view,favorite,co-organizers/*,stats}`, `/api/users/me/{events,favorites,co-organizer-invitations}`, `/api/s/{shortCode}` | **event-service** (absorbe share/view/favorite/co-organizer/stats/me-aggregator post-finalisation) |
| `/api/users/me`, `/api/users/me/{image,banner,calendar-token,calendar-token/regenerate,follow-requests}`, `/api/users/{uuid}`, `/api/users/{uuid}/{follow,followers,following}`, `/api/follow-requests/{id}/{accept,reject}`, `/api/calendar/{token}.ics` | **user-service** (absorbe follow + calendar post-finalisation) |
| `/api/events/{id}/{attend,attendees,comments}`, `/api/users/me/{attendances,participations}`, `/api/comments/{id}` | **engagement-service** (renommé/absorbe attendance + comment post-finalisation) |
| `/api/events/{id}/report`, `/api/admin/reports*` | **moderation-service** (renommé depuis report-service post-finalisation) |
| (placeholder, replicas:0, SCRUM-99) | **notification-service** |

---

## Endpoints implémentés

| Méthode | Path | Service amont | Auth | Description | Codes HTTP |
|---|---|---|---|---|---|
| `GET` | `/users/{id}` | user-service | `@PermitAll` | Profil public d'un utilisateur — **payload réduit pour anon**, 404 si privé ou non autorisé (pas d'oracle d'existence) | 200, 404 |
| `GET` | `/users/me` | user-service | `@Authenticated` | Profil complet de l'utilisateur connecté (provisionne le compte au 1er appel) | 200, 401 |
| `PUT` | `/users/me` | user-service | `@Authenticated` | Mise à jour du profil de l'utilisateur connecté | 200, 400, 401, 403, 404, 409 |
| `POST` | `/users/me/image` | user-service | `@Authenticated` (multipart) | Upload avatar — JPEG/PNG/WebP/GIF, max 2 MiB | 200, 400, 401, 413 |
| `DELETE` | `/users/me/image` | user-service | `@Authenticated` | Supprime l'avatar (objet S3 + URL `null`) | 200, 401 |
| `POST` | `/users/me/banner` | user-service | `@Authenticated` (multipart) | Upload bannière de profil — max 5 MiB | 200, 400, 401, 413 |
| `DELETE` | `/users/me/banner` | user-service | `@Authenticated` | Supprime la bannière (URL `null` ; objet S3 conservé, parité legacy) | 200, 401 |
| `PATCH` | `/users/me/username` | user-service | `@Authenticated` + `@PerUserRateLimit(name="users.updateUsername", max=5)` | SCRUM-169 — change le username public-facing. Body `UpdateUsernameRequest`. Codes applicatifs : `username_invalid` (400), `username_reserved` (400), `username_taken` (409) | 200, 400, 401, 404, 409, 429 |
| `GET` | `/users/by-username/{username}` | user-service | `@PermitAll` | SCRUM-169 — lookup case-insensitive par username. Mêmes règles d'autorisation que `GET /users/{id}` (anti-oracle 404 + stripping anonyme avec `username` toujours exposé) | 200, 404 |
| `HEAD` | `/users/by-username/{username}` | user-service | `@PermitAll` | SCRUM-169 — check d'existence léger pour le debounce frontend. **Sémantique inversée** : 200 = username pris, 404 = libre | 200, 404 |
| `GET` | `/events` | event-service | `@PermitAll` | Liste paginée — filtres : status, category, organizerId, endDateFrom (date-time), faculty, facultyNone (mutex avec faculty) | 200 |
| `POST` | `/events` | event-service | `@Authenticated` + `@PerUserRateLimit(name="events.create", max=10, windowSeconds=60)` + Kong `rate-limiting` `policy: local` `minute: 10` | Créer un événement (ponctuel ou récurrent — bloc `recurrence` optionnel SCRUM-147) | 201, 400, 401, 422, 429 |
| `GET` | `/events/{id}` | event-service | `@PermitAll` | Détail d'un événement — **DRAFT/CANCELLED cachés** (créateur ou admin uniquement, sinon 404) | 200, 404 |
| `PUT` | `/events/{id}` | event-service | `@Authenticated` | Mise à jour (créateur ou co-organisateur ACCEPTED) | 200, 400, 401, 403, 404, 409 |
| `DELETE` | `/events/{id}` | event-service | `@Authenticated` | Suppression (créateur uniquement, statut CANCELLED requis) | 204, 401, 403, 404, 409 |
| `PATCH` | `/events/{id}/cancel` | event-service | `@Authenticated` | Annulation | 200, 401, 403, 404, 409 |
| `PATCH` | `/events/{id}/restore` | event-service | `@Authenticated` | Restoration CANCELLED → DRAFT | 200, 401, 403, 404, 409 |
| `PATCH` | `/events/{id}/publish` | event-service | `@Authenticated` | DRAFT → PUBLISHED (créateur, co-org ACCEPTED ou admin) | 200, 401, 403, 404, 409, 422 |
| `POST` | `/events/{id}/image` | event-service | `@Authenticated` (multipart) | Upload bannière event — max 5 MiB | 200, 400, 401, 403, 404, 413 |
| `GET` | `/events/{id}/occurrences` | event-service | `@PermitAll` | Lister les occurrences d'un parent récurrent (SCRUM-147 — tri startDate ASC) | 200, 400, 404 |
| `GET` | `/events/featured` | event-service | `@PermitAll` | Top events (phase 1 = featured + PUBLISHED ; phase 2 = popularity ranking) | 200 |
| `GET` | `/events/search` | event-service | `@PermitAll` | Recherche full-text (q, category, faculty, facultyNone, tags [substring match case-insensitive], dateFrom, dateTo, page, size) | 200 |
| `PATCH` | `/admin/events/{id}/feature` | event-service | `@RolesAllowed("ADMIN")` | Bascule un event en featured | 200, 401, 403, 404 |
| `PATCH` | `/admin/events/{id}/unfeature` | event-service | `@RolesAllowed("ADMIN")` | Inverse | 200, 401, 403, 404 |
| `POST` | `/events/{id}/favorite` | event-service | `@Authenticated` | Ajouter aux favoris (idempotent — 200 même si déjà favori) | 200, 401, 404 |
| `DELETE` | `/events/{id}/favorite` | event-service | `@Authenticated` | Retirer des favoris | 204, 401, 404 |
| `GET` | `/users/me/favorites` | event-service | `@Authenticated` | Liste paginée des événements favoris | 200, 401 |
| `GET` | `/users/me/events` | event-service | `@Authenticated` | Mes events (BFF — fan-out vers event-service à terme) | 200, 401, 404 |
| `GET` | `/events/{id}/share` | event-service | `@Authenticated` | Obtenir shareUrl + shortCode (idempotent) | 200, 401, 404 |
| `GET` | `/s/{shortCode}` | event-service | `@PermitAll` | Redirection 302 vers la page de l'événement | 302, 404 |
| `POST` | `/events/{id}/view` | event-service | `@Authenticated` | Marque l'event vu par l'utilisateur (idempotent — upsert) | 204, 401, 404 |
| `GET` | `/users/me/calendar-token` | user-service | `@Authenticated` | Token webcal personnel — génère si absent (idempotent) | 200, 401 |
| `POST` | `/users/me/calendar-token/regenerate` | user-service | `@Authenticated` | Révoquer et régénérer le token | 200, 401, 404 |
| `GET` | `/calendar/{calendarToken}.ics` | user-service | `@PermitAll` | Flux iCalendar : événements en favori + événements ATTENDING (PUBLISHED, dédupliqués) | 200, 404 |
| `POST` | `/events/{id}/attend` | engagement-service | `@Authenticated` | Upsert inscription (ATTENDING) — 400 si event non publié, 409 si registration deadline dépassée | 200, 400, 401, 404, 409 |
| `DELETE` | `/events/{id}/attend` | engagement-service | `@Authenticated` | Se désinscrire (auto-promotion WAITLISTED → ATTENDING si capacité libérée) | 204, 401, 404 |
| `GET` | `/events/{id}/attendees` | engagement-service | `@Authenticated` | Liste paginée des inscriptions (créateur **ou co-organisateur ACCEPTED**) | 200, 401, 403, 404 |
| `GET` | `/users/me/attendances` | engagement-service | `@Authenticated` | Mes inscriptions (toutes, avec statut) | 200, 401 |
| `GET` | `/users/me/participations` | engagement-service | `@Authenticated` | Mes events ATTENDING/WAITLISTED (avec filtre `status` + `timeframe=upcoming\|past`) | 200, 400, 401 |
| `POST` | `/events/{id}/co-organizers` | event-service | `@Authenticated` | Inviter un co-organisateur (créateur ou ADMIN) | 201, 400, 401, 403, 404, 409 |
| `GET` | `/events/{id}/co-organizers` | event-service | `@Authenticated` | Lister les co-organisateurs (PENDING + ACCEPTED) | 200, 401, 404 |
| `DELETE` | `/events/{id}/co-organizers/{userId}` | event-service | `@Authenticated` | Retirer un co-organisateur (créateur ou ADMIN) | 204, 401, 403, 404 |
| `PATCH` | `/events/{id}/co-organizers/me/accept` | event-service | `@Authenticated` | Accepter sa propre invitation (idempotent) | 200, 401, 422 |
| `PATCH` | `/events/{id}/co-organizers/me/decline` | event-service | `@Authenticated` | Décliner sa propre invitation (suppression de la row) | 204, 401, 422 |
| `GET` | `/users/me/co-organizer-invitations` | event-service | `@Authenticated` | Mes invitations à co-organiser (default `status=PENDING`) | 200, 401, 404 |
| `POST` | `/events/{id}/comments` | engagement-service | `@Authenticated` + `@PerUserRateLimit(name="comments.post", max=10, windowSeconds=60)` + Kong `rate-limiting` `policy: local` `minute: 10` | Poster un commentaire (top-level ou reply 1 niveau max) | 201, 400, 401, 404, 422, 429 |
| `GET` | `/events/{id}/comments` | engagement-service | `@PermitAll` | Lister les commentaires d'un event (top-level paginés DESC, replies imbriquées) | 200, 400, 404 |
| `DELETE` | `/comments/{id}` | engagement-service | `@Authenticated` | Supprimer un commentaire (auteur, créateur, co-organisateur ACCEPTED ou ADMIN) | 204, 401, 403, 404 |
| `POST` | `/users/{id}/follow` | user-service | `@Authenticated` + `@PerUserRateLimit(name="follows.follow", max=30, windowSeconds=60)` + Kong `rate-limiting` `policy: local` `minute: 30` | Suivre un user (auto-accept si `profilePublic=true`, sinon PENDING) | 201, 401, 404, 409, 422, 429 |
| `DELETE` | `/users/{id}/follow` | user-service | `@Authenticated` | Se désabonner / annuler une demande (idempotent) | 204, 401 |
| `GET` | `/users/{id}/followers` | user-service | `@Authenticated` | Liste paginée des followers (404 anti-oracle si privé non-owner) | 200, 401, 404 |
| `GET` | `/users/{id}/following` | user-service | `@Authenticated` | Liste paginée des suivis | 200, 401, 404 |
| `GET` | `/users/me/follow-requests` | user-service | `@Authenticated` | Demandes PENDING reçues | 200, 401, 404 |
| `PATCH` | `/follow-requests/{followId}/accept` | user-service | `@Authenticated` | Accepter (target uniquement) | 200, 401, 403, 404, 409 |
| `PATCH` | `/follow-requests/{followId}/reject` | user-service | `@Authenticated` | Refuser et supprimer la row | 204, 401, 403, 404, 409 |
| `POST` | `/events/{id}/report` | moderation-service | `@Authenticated` | Signaler un event (raison + description) | 201, 400, 401, 404, 409, 422 |
| `GET` | `/admin/reports` | moderation-service | `@RolesAllowed("ADMIN")` | Liste paginée des reports (default `status=PENDING`) | 200, 401, 403 |
| `PATCH` | `/admin/reports/{id}` | moderation-service | `@RolesAllowed("ADMIN")` | Statuer (REVIEWED ban l'event + cascade siblings, DISMISSED neutre) | 200, 400, 401, 403, 404, 409 |
| `GET` | `/events/{id}/stats` | event-service | `@Authenticated` | Counts attending / interested / view (créateur ou co-org ACCEPTED) | 200, 401, 403, 404 |

> **Rate limit notice (post-completion)** : deux étages.
> (1) **Lib `services/shared-rate-limit/`** — `@PerUserRateLimit`
> interceptor + state cache, restaurée au commit `446ea3e` ; 13 sites
> annotés sur 6 services consommateurs (event, user, attendance,
> comment, favorite, follow). 100 % couvert par tests unitaires.
> (2) **Plugin Kong `rate-limiting`** ajouté en complétion (Étape 10
> de la spec de complétion) sur 3 routes : `events.create=10/min`,
> `comments.post=10/min`, `follows.follow=30/min`, avec `policy: local`
> (compteur par instance Kong — la migration vers `policy: redis`
> cluster-wide est un item DevOps S9+ documenté dans
> [`devops-handoff.md`](devops-handoff.md) item 7).
> Les annotations `@PerUserRateLimit` les plus restrictives **et** les
> buckets Kong sont **tous deux** appliqués — Kong protège l'infra,
> Java protège l'UX (cf. spec orig. décision 21).

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
- `200 OK` — `EventDTO` complet (mêmes champs que `GET /events`, **plus** `viewCount` et `interestedCount` renseignés — voir ci-dessous)
- `404 Not Found` — événement introuvable, OU événement non-PUBLISHED demandé par un appelant non autorisé

**Compteurs publics `viewCount` et `interestedCount`** (review #90, SCRUM-92) : `EventDTO`
expose ces deux champs `Long` nullable (vues uniques + favoris) à **tous les utilisateurs**
(pas seulement à l'organisateur). Stratégie volontairement asymétrique pour éviter les
requêtes N+1 sur les listes : seul `EventService.getById` les calcule. Tous les autres
endpoints qui retournent un `EventDTO` (`POST/PUT/PATCH /events`, `GET /events`,
`GET /events/search`, `GET /users/me/favorites`, etc.) renvoient `null` pour ces deux
champs. Le frontend les consomme uniquement sur la page détail (`EventStatsPanel`). La
page dashboard `/events/{id}/stats` reste **réservée à l'organisateur** et expose les
mêmes compteurs au sein de `EventStatsDTO` (anciens noms gardés pour compatibilité).

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

Crée un nouvel événement, ponctuel ou récurrent (SCRUM-147).

**Body :** `CreateEventRequest` — champs requis : `title`, `location`, `startDate` (@Future), `endDate`, `category`. Champ optionnel `recurrence` (cf. `RecurrenceRequest`) : si renseigné, le service crée le parent + jusqu'à 51 occurrences en une transaction atomique.

**Réponses :**
- `201 Created` — `EventDTO` du parent (avec `recurrenceRule` calculée si récurrence)
- `400 Bad Request` :
  - `ValidationErrorResponse` (Bean Validation : title vide, category null, frequency null, maxOccurrences hors [1,52], etc.)
  - `ApiErrorResponse{ error: "recurrence_unbounded" }` — ni `endDate` ni `maxOccurrences` fournis
  - `ApiErrorResponse{ error: "recurrence_end_before_start" }` — `recurrence.endDate < startDate.toLocalDate()`
- `401 Unauthorized` — token absent
- `429 Too Many Requests` — rate limit `events.create` (max 10/min/utilisateur)

> **Note** : si la combinaison `endDate × frequency` produirait plus de 52 occurrences, la récurrence est **silencieusement tronquée** à 52 (cap dur côté générateur). Pas d'erreur 422. La borne client `maxOccurrences > 52` est rejetée par Bean Validation (`@Max(52)`) en `400`.

---

### Event Recurrence (SCRUM-147)

`POST /events` accepte un bloc optionnel `recurrence` qui matérialise un événement récurrent. Le payload de récurrence est un `RecurrenceRequest` : `frequency` (enum `WEEKLY`/`BIWEEKLY`/`MONTHLY`, requis), `endDate` (LocalDate, optionnel), `maxOccurrences` (Integer 1..52, optionnel) — au moins un des deux derniers doit être présent.

**Sémantique** :
- Le **parent** est créé avec `recurrenceRule` calculée (ex. `FREQ=WEEKLY;COUNT=4`) et `parentEventId = null`.
- Les **occurrences** sont des rows `events` standalones avec `parentEventId = parent.id` et `recurrenceRule = null`.
- Statut hérité du parent (DRAFT par défaut, ou `request.status` si fourni).
- Cap hard : 52 rows total (parent + ≤51 enfants).
- Atomicité : si l'INSERT d'une occurrence échoue, parent + occurrences déjà persistées rollback (même transaction JTA).
- **Pas de propagation** PUT et **pas de cascade** PATCH cancel du parent vers les occurrences — chaque occurrence reste indépendamment éditable et cancellable.
- Co-organisateurs **non hérités** automatiquement.
- ICS feed inchangé : chaque occurrence row génère son propre VEVENT (pas de RRULE compact).

**`GET /api/events/{id}/occurrences`** (`@PermitAll`) liste les enfants d'un parent récurrent, triés par `startDate ASC, id ASC`. Pagination `page`/`size` (defaults 0/52, `@Max(52)`). Si l'event ciblé n'a pas d'enfants (standalone, occurrence elle-même, parent vide), retourne `200 OK + []` — pas `404`. Visibilité héritée de `getById` (anti-oracle ISSUE-92 : DRAFT non-créateur, BANNED, id inconnu → `404`).

**Codes d'erreur normalisés** sur `POST /events` côté récurrence :

| HTTP | `error` slug | Cause |
|---|---|---|
| `400` | `recurrence_unbounded` | `recurrence` présent, ni `endDate` ni `maxOccurrences` renseignés |
| `400` | `recurrence_end_before_start` | `recurrence.endDate < startDate.toLocalDate()` |
| `400` | (Bean Validation generic) | `frequency` null, `maxOccurrences` hors [1,52], etc. |

Pas de 422 dédié — au-delà du cap matérialisé de 52 occurrences, le générateur tronque silencieusement (cf. spec décision 9). La seule borne stricte exposée client est `@Max(52)` sur `maxOccurrences` qui renvoie 400 Bean Validation.

**FK `fk_events_parent`** est `ON DELETE SET NULL` côté DB : un `DELETE /events/{parentId}` (après cancel) préserve les occurrences orphelines avec `parent_event_id = NULL` — leurs inscriptions, favoris, vues et comptages restent intacts.

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

### Comments (SCRUM-139)

Trois endpoints. La visibilité de l'event est déléguée à
`EventService.getById(eventId, callerAuth0Id, isAdmin)` — un event invisible
(DRAFT/CANCELLED/BANNED non-créateur, id inconnu) renvoie `404 not_found`
(envelope identique à un id inexistant — anti-oracle ISSUE-92).

#### `POST /events/{eventId}/comments` — `@Authenticated`

**Rate-limité** : `@PerUserRateLimit(name="comments.post", max=10, windowSeconds=60)`.

**Body** : `CreateCommentRequest{ content: string (1..2000), parentCommentId?: int64 }`.
`content` est trimmé côté service avant persistance.

**Réponse** : `201 Created` + `CommentDTO` (replies vide, parentCommentId reflète la
valeur reçue, likedByMe toujours false en S6).

| Code | `error` | Quand |
|---|---|---|
| `400` | `cannot_comment_draft_event` | event DRAFT, caller créateur/co-org/admin |
| `400` | `cannot_comment_cancelled_event` | event CANCELLED |
| `400` | `cannot_comment_expired_event` | event EXPIRED |
| `404` | `not_found` (NotFoundExceptionMapper) | event invisible (DRAFT/CANCELLED/EXPIRED non-créateur, BANNED, id inconnu) |
| `404` | `parent_comment_not_found` | `parentCommentId` inexistant |
| `422` | `replies_too_deep` | `parentCommentId` réfère un commentaire qui a déjà un parent |
| `422` | `parent_comment_not_in_event` | `parentCommentId` réfère un commentaire d'un autre event |
| `429` | `rate_limited` | bucket `comments.post` épuisé sur la fenêtre de 60s |

#### `GET /events/{eventId}/comments?page=&size=` — `@PermitAll`

**Pagination** : `page` (default 0, ≥ 0), `size` (default 20, > 0, ≤ 100). `size > 100` → `400`.

**Tri** : `createdAt DESC, id DESC` sur les top-level. Les replies sont chargées en bulk
(2 requêtes SQL au total — pas de N+1) et imbriquées dans `replies[]` (chronologie ASC
sous chaque parent). Profondeur max 1 niveau.

`authorIsOrganizer` est calculé en bulk via un `Set<UUID>` mémoïsant
`{event.creator.id} ∪ {co-organisateurs ACCEPTED}` — testé en O(1) par commentaire.

**Réponse** : `200 OK` + `List<CommentDTO>` (vide possible).

`404 not_found` si l'event est invisible (visibilité héritée d'`EventService.getById`).

#### `DELETE /comments/{commentId}` — `@Authenticated`

**Cascade d'autorisation** (cf. SCRUM-139 décision 16) :
1. l'**auteur** du commentaire,
2. le **créateur** de l'event,
3. un **co-organisateur ACCEPTED** (cascade SCRUM-136),
4. un utilisateur **ADMIN** (claim Auth0).

DELETE physique — la row part définitivement. Si le commentaire avait des replies, elles
sont conservées et leur `parent_comment_id` passe à `NULL` (`ON DELETE SET NULL` côté DB) ;
au prochain `GET /events/{id}/comments` elles apparaissent en top-level
(`parentCommentId: null`).

| Code | `error` | Quand |
|---|---|---|
| `204` | — | Commentaire supprimé |
| `401` | `unauthorized` | Token absent ou invalide |
| `403` | `forbidden` | Caller authentifié mais ne matche aucune règle de la cascade |
| `404` | `comment_not_found` | `commentId` inexistant |

#### Hors scope SCRUM-139

- **Likes** (`POST/DELETE /comments/{id}/like`) — SCRUM-144 (S7).
- **Signalement de commentaires** (`POST /comments/{id}/report`, extension `Report.commentId`)
  — SCRUM-144 (S7).
- **Notifications** (`NEW_COMMENT` à l'organisateur, `COMMENT_MENTION`) — SCRUM-145 (S7+),
  dépend de l'infra `Notification` SCRUM-99.
- **Édition** d'un commentaire (`PUT /comments/{id}`) — non supportée. UX = supprimer + reposter.

---

### Follow (SCRUM-138)

Sept endpoints exposent la relation de suivi entre utilisateurs. Toutes les opérations
sont sous `@Authenticated`. Pas de privilège `ADMIN` (un admin doit suivre / se
désabonner explicitement). Aucune notification émise (déléguée à SCRUM-140 / S7).

#### `POST /users/{id}/follow`

Crée une row `Follow` entre l'appelant authentifié (`follower`) et l'utilisateur cible
(`followed = {id}`). Auto-accept si `profilePublic=true` côté cible, sinon PENDING.

**Réponses :**
- `201 Created` — `FollowDTO` (status reflétant la cascade auto-accept)
- `401 Unauthorized` — token absent
- `404 Not Found` — UUID cible inexistant ou profil caller non provisionné
- `409 Conflict` — `error=already_following` (le caller suit déjà la cible)
- `422 Unprocessable Entity` — `error=cannot_follow_self`
- `429 Too Many Requests` — `@PerUserRateLimit(name="follows.follow", max=30)` dépassé

#### `DELETE /users/{id}/follow`

Idempotent : supprime la row peu importe son statut, ne lève pas 404 sur l'absence.

**Réponses :**
- `204 No Content`
- `401 Unauthorized`

#### `GET /users/{id}/followers` & `GET /users/{id}/following`

Listes paginées (`page`, `size` ; max 100). Items projetés via
`UserPublicResponse.from(User)` (compteurs et followStatus à 0/null sur les items —
ces champs ne font sens que sur le profil cible). Tri `Follow.createdAt DESC`.

**Règle d'autorisation** (alignée ISSUE-93) :
- Profil cible `profilePublic=true` → 200 + liste paginée.
- Profil cible `profilePublic=false`, caller ≠ owner → `404 not_found` (envelope
  identique à un UUID inexistant — anti-oracle).
- Profil cible `profilePublic=false`, caller = owner → 200.

#### `GET /users/me/follow-requests`

Demandes PENDING reçues par l'utilisateur courant. `List<FollowDTO>` brut (le frontend
résoudra `GET /users/{followerId}` à la demande pour enrichir le rendu).

#### `PATCH /follow-requests/{followId}/accept`

Bascule PENDING → ACCEPTED. Réservé au `followed`.

**Réponses :**
- `200 OK` — `FollowDTO` mis à jour
- `401 Unauthorized`
- `403 Forbidden` — caller ≠ `followed`
- `404 Not Found` — `followId` inexistant
- `409 Conflict` — `error=invalid_transition` (déjà ACCEPTED)

#### `PATCH /follow-requests/{followId}/reject`

Refuse la demande PENDING — **supprime physiquement la row** (cf. data-model.md). Le
follower peut re-tenter ultérieurement sans 409. Réservé au `followed`.

**Réponses :**
- `204 No Content`
- `401 Unauthorized`
- `403 Forbidden`
- `404 Not Found`
- `409 Conflict` — `error=invalid_transition` (row déjà ACCEPTED)

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
