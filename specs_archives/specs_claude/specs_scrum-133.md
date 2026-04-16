# SCRUM-133 — Endpoint `GET /api/users/me/events` + durcissement `organizerId` sur `/events`

**Sprint 5 (anticipé depuis S6) — Backend + Frontend | PR atomique**

> **Branche :** `feature/s6-my-events-endpoint`
> **Base de branche :** `origin/feature/s5-event-extra-fields-capacity` (PAS `main`, PAS `feature/s5-my-events-page`)
> **Cible PR :** `main`
> **Règle d'or :** modifier `openapi/openapi.yaml` EN PREMIER, puis Resource → Service → Tests → Frontend
> **Couverture exigée :** 100 % JaCoCo sur les lignes nouvelles (backend) et 100 % V8 sur les lignes nouvelles (frontend)

---

## Branche Git — décision déjà prise

Quatre branches du Sprint 5 sont en vol ou mergées. Analyse pour le choix de la base :

| Branche | État | Contenu pertinent | Base possible ? |
|---|---|---|---|
| `origin/main` | mergée | N'a ni `allDay`, ni la nouvelle signature `EventDTO.from(Event, long, Long, long)`, ni `useMyEvents` | Non — régression certaine |
| `origin/feature/s5-my-events-page` | ouverte (PR) | Introduit `useMyEvents`, `MyPublicationsPage`, `allDay`, `EventDTO.from(Event, long attendingCount)` | Insuffisant — pas la nouvelle signature DTO |
| `origin/feature/s5-event-extra-fields-capacity` | ouverte (PR, SCRUM-126+129) | Inclut tout ce qui précède **et** la nouvelle signature `EventDTO.from(Event, long, Long, long)`, `computeAvailableSpots`, `countWaitlisted`, le bulk `countGroupedByStatus` pour WAITLISTED | **Oui — base choisie** |
| `origin/feature/draft`, `origin/feature/s5-user-banner-front` | ouvertes | Orthogonales (draft recovery, user banner) | Non — pas de chevauchement |

Repartir de `feature/s5-event-extra-fields-capacity` est la seule option qui :
1. Hérite de `useMyEvents.ts` et `MyPublicationsPage.tsx` (qu'on doit refactorer).
2. Hérite de la signature `EventDTO.from(Event, long, Long, long)` que `getMyEvents()` doit obligatoirement utiliser.
3. Préserve les helpers `computeAvailableSpots` et `countWaitlisted` qu'on réutilise.
4. Évite tout conflit sur `EventService.java`, `UserResource.java`, `eventApi.ts`, `useMyEvents.ts`.

### Commande exacte

```bash
git fetch origin
git checkout -b feature/s6-my-events-endpoint origin/feature/s5-event-extra-fields-capacity
```

### Cible du PR

Le PR ouvre **vers `main`**. GitHub affichera tous les commits cumulés des branches parentes tant qu'elles ne sont pas mergées — c'est attendu. Ordre de merge attendu :

1. `feature/s5-my-events-page` → `main`
2. `feature/s5-event-extra-fields-capacity` → `main` (après rebase sur `main`)
3. `feature/s6-my-events-endpoint` → rebase sur `main` (**uniquement après** que les deux parentes soient mergées), puis `git push --force-with-lease`, puis merge.

**Ne pas rebaser avant cette étape**, sous peine de réintroduire les conflits qu'on cherche à éviter.

---

## Contexte

### Pourquoi anticiper SCRUM-133 au Sprint 5

SCRUM-133 est planifié [S6] dans `backend/docs/backlog_s5_s10.md`. Cette spec le ramène au Sprint 5 pour **une seule raison : une faille d'autorisation active dans la branche `feature/s5-my-events-page`**.

#### La faille

Sur `feature/s5-my-events-page`, la page `MyPublicationsPage` affiche les événements créés par l'utilisateur courant via le hook :

```ts
useMyEvents(user?.id ?? null, status)
```

qui appelle en interne :

```ts
getAll({ organizerId, status, size: 100 })
```

soit `GET /api/events?organizerId=<uuid>&status=DRAFT`.

Le endpoint `GET /events` est `@PermitAll`. Le filtre `organizerId` est un **query param public** : n'importe quel utilisateur authentifié (ou même non authentifié) peut passer l'UUID d'un autre utilisateur et obtenir la liste de ses **brouillons** ou de ses événements **annulés**. Ces statuts ne sont pas publics par construction — un DRAFT est un contenu privé en cours d'édition.

#### Deux issues incompatibles avec le produit

1. **Soit** `GET /events?organizerId=X&status=DRAFT` renvoie les DRAFTs de X sans vérification → fuite de données privées.
2. **Soit** le endpoint filtre déjà DRAFT/CANCELLED → `MyPublicationsPage` est cassée (les onglets Brouillons et Annulés sont vides).

L'état actuel sur la branche parente correspond au cas 1 : la page fonctionne parce que le filtre est permissif, et la faille est active.

#### La correction par design

Un endpoint dédié `GET /api/users/me/events` qui dérive l'identité depuis le JWT ferme la faille **structurellement** : impossible de viser un autre utilisateur, l'identité ne peut pas être spoofée via un query param. Tous les statuts (DRAFT, PUBLISHED, CANCELLED) peuvent alors être retournés sans crainte, parce que l'appelant est garanti être le propriétaire.

Parallèlement, `GET /events?organizerId=…` garde du sens **en public** pour "voir les événements publiés de tel organisateur", à condition de **forcer `status = PUBLISHED`** quand `organizerId` est présent.

Laisser la faille jusqu'au Sprint 6 serait livrer un produit avec une exposition documentée de brouillons privés. Le coût de correction (2 SP backend + ~1 SP frontend) ne justifie pas d'attendre.

### Ce qui existe déjà sur `feature/s5-event-extra-fields-capacity` (à NE PAS retoucher hors scope)

| Fichier | État pertinent |
|---|---|
| `entity/Event.java` | `createdAt` (`@PrePersist`), `status`, `creator`, + les 4 champs SCRUM-126 |
| `dto/event/EventDTO.java` | Factory `from(Event, long attendingCount, Long availableSpots, long waitlistedCount)` — signature à 4 paramètres, à réutiliser telle quelle |
| `service/EventService.java` | Helpers `computeAvailableSpots(Integer, long)`, `countWaitlisted(Long)`, bulk `Attendance.countGroupedByStatus(ids, AttendanceStatus, em)` — à réutiliser dans `getMyEvents()` |
| `service/EventService.java#getAll(...)` | Accepte `UUID organizerId` et ajoute `e.creator.id = :organizerId` — à durcir (`status = PUBLISHED` forcé si `organizerId` présent) |
| `resource/EventResource.java#getAll(...)` | Query param `organizerId` (ligne ~50) — à annoter et durcir |
| `resource/UserResource.java` | Expose `/me`, `/me/image`, `/me/favorites`, `/me/calendar-token`, `/me/attendances`. Pattern `identity.getPrincipal().getName()` pour résoudre l'auth0Id. **Pas** de `/me/events`. |
| `entity/User.java` | Helper `findByAuth0Id(String)` déjà utilisé par `EventService.create()` et `AttendanceService` |
| `frontend/src/hooks/useMyEvents.ts` | Appelle `getAll({ organizerId, status, size: 100 })` et trie `startDate` côté client — à refactorer |
| `frontend/src/services/eventApi.ts` | Expose `getAll(params: EventsParams)` avec `organizerId?: string` — on ajoute une nouvelle fonction dédiée, on laisse `getAll` intact (juste le backend durcit la sémantique) |
| `frontend/src/pages/my-events/MyPublicationsPage.tsx` | Appelle `useMyEvents(user?.id ?? null, status)` — signature à changer |

### Pas de migration SQL

Aucun changement de schéma. Uniquement un nouveau endpoint, un nouveau flux de lecture, et un durcissement de validation. Pas de Flyway.

---

## Décisions techniques (à NE PAS revisiter pendant l'implémentation)

### 1. `GET /users/me/events` — endpoint dédié, auth JWT

```java
@GET
@Path("/me/events")
@Authenticated
public List<EventDTO> getMyEvents(
        @QueryParam("status") EventStatus status,
        @QueryParam("page") @DefaultValue("0") @Min(0) int page,
        @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
    return eventService.getMyEvents(identity.getPrincipal().getName(), status, page, size);
}
```

**Pourquoi pas `@PathParam` avec l'UUID** : l'identité DOIT venir du JWT, pas du chemin. Un `@Path("/{id}/events")` ouvrirait la faille qu'on est en train de fermer.

**Pourquoi sur `UserResource` et pas `EventResource`** : sémantiquement, c'est "les events **de l'utilisateur courant**". Aligné avec `/me/attendances`, `/me/favorites`, `/me/image`. Le regroupement par ressource utilisateur est plus lisible que d'inventer un `/events?mine=true`.

**Pagination identique à `/events`** : `@DefaultValue("0") @Min(0)` pour `page`, `@DefaultValue("20") @Positive @Max(100)` pour `size`. Cohérence d'API.

### 2. `EventService.getMyEvents()` — tri `createdAt DESC`, tous statuts, bulk counts réutilisés

```java
@Transactional
public List<EventDTO> getMyEvents(String auth0Id, EventStatus status, int page, int size) {
    User user = User.findByAuth0Id(auth0Id)
            .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

    StringBuilder jpql = new StringBuilder("SELECT e FROM Event e WHERE e.creator.id = :creatorId");
    Map<String, Object> params = new HashMap<>();
    params.put("creatorId", user.id);
    if (status != null) {
        jpql.append(" AND e.status = :status");
        params.put("status", status);
    }
    jpql.append(" ORDER BY e.createdAt DESC, e.id DESC");

    List<Event> events = Event.<Event>find(jpql.toString(), params)
            .page(page, size)
            .list();

    List<Long> ids = events.stream().map(e -> e.id).toList();
    Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(
            ids, AttendanceStatus.ATTENDING, entityManager);
    Map<Long, Long> waitlistedCounts = Attendance.countGroupedByStatus(
            ids, AttendanceStatus.WAITLISTED, entityManager);

    return events.stream()
            .map(e -> {
                long att = attendingCounts.getOrDefault(e.id, 0L);
                long wait = waitlistedCounts.getOrDefault(e.id, 0L);
                return EventDTO.from(e, att, computeAvailableSpots(e.capacity, att), wait);
            })
            .toList();
}
```

**Pourquoi `createdAt DESC` (pas `startDate`)** : le backlog SCRUM-133 le demande explicitement. Sémantique : "mes events, du plus récemment créé au plus ancien" — l'ordre naturel d'un dashboard d'auteur (un organisateur veut voir ses dernières créations en haut, pas l'ordre chronologique des events eux-mêmes, qui mélange brouillons futurs et events passés).

**`e.id DESC` en tie-breaker** : deux events créés dans la même milliseconde (fixture/seed de test) sont départagés de manière déterministe.

**Tous statuts par défaut** : `WHERE e.creator.id = :creatorId` uniquement. DRAFT, PUBLISHED, CANCELLED sont tous retournés quand `status` est null. C'est sûr parce que l'identité est celle du JWT.

**`NotFoundException` sur user absent** : aligné avec `EventService.create()` qui jette la même exception. Le frontend est déjà préparé à ce cas (intercepteur Axios redirige vers `/login` si 404 sur `/me`).

**Bulk counts réutilisés** : `Attendance.countGroupedByStatus` existe déjà sur la branche parente. Pas de N+1 — un seul GROUP BY pour ATTENDING, un autre pour WAITLISTED, peu importe le nombre d'events. Pattern identique à `EventService.getAll()`.

**Pas de cache, pas de projection** : on retourne des `EventDTO` pleins (avec tags, availableSpots, etc.). L'écran consommateur (`MyPublicationsPage`) utilise déjà `attendingCount`, `capacity`, `bannerUrl`, `faculty`, etc. Une projection minimaliste obligerait à réintroduire le DTO complet à l'étape SCRUM-134 (dashboard organisateur). Cohérence > micro-optimisation.

### 3. Durcissement de `GET /events?organizerId=…` — rejet explicite `400`

**Règle** : si `organizerId` est présent **et** `status` est présent **et** `status != PUBLISHED`, retourner `400` avec `error: "organizer_filter_requires_published"`.

```java
// dans EventResource.getAll(...)
if (organizerId != null && status != null && status != EventStatus.PUBLISHED) {
    throw new WebApplicationException(
        Response.status(Response.Status.BAD_REQUEST)
            .entity(new ApiErrorResponse(
                "organizer_filter_requires_published",
                "Filtering by organizerId is only allowed for status=PUBLISHED. Use GET /users/me/events for your own events."))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build());
}
if (organizerId != null && status == null) {
    status = EventStatus.PUBLISHED; // force PUBLISHED implicitly when organizerId is present without explicit status
}
```

**Pourquoi rejet explicite plutôt que coercition silencieuse** :
- La coercition silencieuse (`organizerId + status=DRAFT` → résultat vide) masque l'intention du client et peut cacher un bug côté consommateur.
- Le rejet 400 est explicite et auto-documenté : le message d'erreur oriente vers `/users/me/events`.
- Cohérent avec le style ailleurs dans le projet (`ApiErrorResponse.error` comme discriminant).

**Pourquoi garder la coercition implicite `organizerId sans status → PUBLISHED`** : c'est le cas d'usage public légitime ("voir les events publiés de tel organisateur"). Forcer le client à passer `status=PUBLISHED` explicitement serait une régression pour les consommateurs existants (si le cas existe — à vérifier via grep dans `frontend/src/services/eventApi.ts` — aucun aujourd'hui, mais on garde la porte ouverte).

**Pourquoi pas sur le Service** : la logique de validation métier est dans le Service uniquement quand elle dépend d'état. Ici, c'est une règle de validation d'input pure, qui dépend uniquement des paramètres HTTP — elle reste dans le Resource pour que le 400 soit produit au plus tôt et avec le bon type d'erreur. Le Service garde sa signature actuelle et n'est pas au courant de cette règle (il retournera toujours ce qu'on lui demande).

**Validation dans le Resource et pas en annotation Bean Validation** : pas de contrainte croisée standard `(param1, param2) → validation`. Un `@AssertTrue` custom serait possible mais lourd pour un seul endpoint. Le `if` explicite est plus lisible.

### 4. Frontend refactoré **dans la même PR**

Décision : `useMyEvents.ts` bascule sur `GET /users/me/events` au sein de cette PR. Justification :

- La PR backend **ne peut pas** livrer l'endpoint sans migrer le consommateur, sinon la faille reste active — `MyPublicationsPage` continuerait d'appeler `GET /events?organizerId=…&status=DRAFT` jusqu'au merge de SCRUM-134.
- `useMyEvents` est le **seul** consommateur du paramètre `organizerId` avec un status non-PUBLISHED côté frontend (vérifié ci-dessous). Le durcissement backend casserait la page si on ne migre pas dans la même PR.
- Les deux changements sont atomiques par construction : un revert de cette PR doit restaurer l'état antérieur des deux côtés.

**Vérification de l'unique consommateur** : à l'étape d'implémentation, exécuter :

```bash
grep -rn "organizerId" frontend/src --include="*.ts" --include="*.tsx"
```

Le seul résultat attendu est `useMyEvents.ts` et `eventApi.ts` (définition du type `EventsParams`). Si un autre consommateur est trouvé, **arrêter l'implémentation et lever la main** — le périmètre de la PR s'élargit.

### 5. `useMyEvents` — nouvelle signature, suppression du tri client

```ts
export function useMyEvents(status: EventStatus): UseMyEventsResult
```

**Suppression du paramètre `organizerId`** : plus nécessaire, l'identité vient du JWT côté serveur.

**Suppression du tri client** : `const sorted = [...data].sort((a, b) => b.startDate.localeCompare(a.startDate))` est supprimé. Le serveur trie déjà par `createdAt DESC`. Le tri client était un artefact de l'ancien endpoint qui triait par `startDate, id` — ne plus essayer de "corriger" côté client.

**`organizerId === null → set empty` supprimé** : la nouvelle signature n'a plus d'`organizerId`, donc pas de branche `if (!organizerId)`. L'utilisateur non authentifié est géré par `<PrivateRoute>` en amont — la page n'est jamais rendue sans session.

**Nouvelle fonction dans `eventApi.ts`** :

```ts
export async function getMyEvents(params: MyEventsParams = {}): Promise<Event[]> {
  const response = await api.get<Event[]>('/users/me/events', { params })
  return response.data
}

export interface MyEventsParams {
  status?: EventStatus
  page?: number
  size?: number
}
```

**Pas de modification de `getAll`** : l'interface `EventsParams` garde `organizerId?: string` côté types. Un consommateur futur qui passe `organizerId` sans status recevra des events PUBLISHED (coercition backend). Un consommateur qui passe `organizerId + status=DRAFT` recevra un 400 — comportement attendu.

### 6. Pas de Quarkus Cache, pas de TanStack Query

Le backlog SCRUM-134 (frontend) prévoira éventuellement TanStack Query pour le cache. Cette PR **ne** l'introduit **pas** — on garde `useState` + `useEffect` pour rester ISO avec le pattern actuel de `useMyEvents`. Réduire le périmètre de la PR.

### 7. Pas de nouvel index DB

`(creator_id, created_at DESC)` serait l'index optimal pour la nouvelle query, mais :
- `idx_event_creator` existe déjà sur `feature/s5-my-events-page`.
- PostgreSQL peut utiliser cet index pour la partie `WHERE` et faire un sort en mémoire — négligeable pour la volumétrie Sprint 5.
- Ajouter un index composite est du sur-engineering avant profilage.

À reconsidérer si le profilage le justifie au Sprint 7+.

### 8. `@Authenticated` — même annotation que `/me/attendances`

Pas de nouveau rôle, pas de `@RolesAllowed`. Tout utilisateur authentifié peut lire ses propres events (c'est l'intérêt de dériver l'identité du JWT).

### 9. Log INFO sur le rejet 400 ? Non

Pas de log sur le rejet `organizer_filter_requires_published`. Un utilisateur mal intentionné qui tente d'exploiter la faille historique générerait du bruit dans les logs sans valeur actionnable. Si besoin d'audit sécurité, ajouter un hit counter via metrics au Sprint 7+.

### 10. Idempotence sur `getMyEvents`

Méthode de lecture pure, `@Transactional` (readonly implicite Quarkus). Pas de side-effect, pas de cache — chaque appel relit la DB.

---

## Implémentation backend — fichier par fichier

### Ordre strict

1. `openapi/openapi.yaml`
2. `backend/src/main/java/ch/unige/events/resource/EventResource.java` (durcissement)
3. `backend/src/main/java/ch/unige/events/resource/UserResource.java` (nouvel endpoint)
4. `backend/src/main/java/ch/unige/events/service/EventService.java` (nouvelle méthode `getMyEvents`)
5. `backend/src/test/java/ch/unige/events/resource/UserResourceTest.java`
6. `backend/src/test/java/ch/unige/events/resource/EventResourceTest.java`
7. `backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java`
8. `backend/src/test/java/ch/unige/events/service/EventServiceMock.java` (ajout mock `getMyEvents`)
9. `backend/docs/data-model.md`
10. `backend/docs/sprint-context.md`

### `openapi/openapi.yaml` — nouveau path + durcissement description

**Nouveau path à ajouter** (placer dans la section `paths:` à proximité de `/users/me/attendances`) :

```yaml
  /users/me/events:
    get:
      tags:
        - users
      summary: List events created by the authenticated user
      description: |
        Returns all events where the authenticated user is the creator, ordered by `createdAt DESC`.
        Includes **all statuses** (DRAFT, PUBLISHED, CANCELLED) by default. Use the `status` query parameter
        to filter. Identity is derived from the JWT — there is no way to query another user's events
        through this endpoint, which is why DRAFT and CANCELLED can be safely returned.
      security:
        - bearerAuth: []
      parameters:
        - in: query
          name: status
          required: false
          schema:
            $ref: '#/components/schemas/EventStatus'
          description: Optional filter on a specific status. Omit to retrieve all statuses.
        - in: query
          name: page
          required: false
          schema:
            type: integer
            minimum: 0
            default: 0
        - in: query
          name: size
          required: false
          schema:
            type: integer
            minimum: 1
            maximum: 100
            default: 20
      responses:
        '200':
          description: List of events created by the authenticated user
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Event'
        '401':
          description: Not authenticated
        '404':
          description: User profile not found — call GET /users/me first to provision it
```

**Durcissement de `GET /events`** : mettre à jour la description du query param `organizerId` et la liste des réponses du endpoint `GET /events`.

```yaml
        - in: query
          name: organizerId
          required: false
          schema:
            type: string
            format: uuid
          description: |
            Filter events by creator UUID. **Security constraint**: when `organizerId` is present,
            only `status=PUBLISHED` is allowed. Passing `organizerId` without `status` implicitly
            forces `status=PUBLISHED`. Passing `organizerId` with `status=DRAFT` or `status=CANCELLED`
            returns 400 `organizer_filter_requires_published`. To list your own events in any status,
            use `GET /users/me/events`.
```

Ajouter la réponse `400` explicite au endpoint `GET /events` :

```yaml
        '400':
          description: |
            Invalid combination of query parameters. Error codes in `error`:
            - `organizer_filter_requires_published`: `organizerId` was combined with a non-PUBLISHED status.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### `backend/src/main/java/ch/unige/events/resource/EventResource.java`

Modifier la méthode `getAll(...)` pour appliquer la validation croisée avant de déléguer au service :

```java
import ch.unige.events.dto.ApiErrorResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

@GET
@PermitAll
@SuppressWarnings("java:S107")
public List<EventDTO> getAll(
        @QueryParam("page") @DefaultValue("0") @Min(0) int page,
        @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size,
        @QueryParam("status") EventStatus status,
        @QueryParam("category") EventCategory category,
        @QueryParam("organizerId") UUID organizerId,
        @QueryParam("endDateFrom") LocalDateTime endDateFrom,
        @QueryParam("faculty") Faculty faculty,
        @QueryParam("facultyNone") Boolean facultyNone) {
    if (organizerId != null) {
        if (status == null) {
            status = EventStatus.PUBLISHED;
        } else if (status != EventStatus.PUBLISHED) {
            throw new WebApplicationException(
                Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ApiErrorResponse(
                        "organizer_filter_requires_published",
                        "Filtering by organizerId is only allowed for status=PUBLISHED. Use GET /users/me/events for your own events."))
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .build());
        }
    }
    return eventService.getAll(page, size, status, category, organizerId, endDateFrom, faculty, facultyNone);
}
```

**Note** : la réassignation de `status` avant l'appel service est volontaire — elle matérialise la coercition en un point unique et la rend visible aux logs/tests. Le Service reçoit un `status` non-null cohérent avec la règle.

**`ApiErrorResponse` existe déjà** dans `dto/` (utilisé par `AttendanceService.attend()` pour `registration_closed`). Pas de nouvelle classe.

### `backend/src/main/java/ch/unige/events/resource/UserResource.java`

Ajouter la nouvelle méthode **juste avant** la méthode `getMyAttendances` (pattern `/me/*` regroupé) :

```java
import ch.unige.events.entity.EventStatus;
import ch.unige.events.service.EventService;
// (imports EventDTO, Authenticated, QueryParam, DefaultValue, Min, Positive, Max déjà présents)

@Inject EventService eventService;

@GET
@Path("/me/events")
@Authenticated
public List<EventDTO> getMyEvents(
        @QueryParam("status") EventStatus status,
        @QueryParam("page") @DefaultValue("0") @Min(0) int page,
        @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
    return eventService.getMyEvents(identity.getPrincipal().getName(), status, page, size);
}
```

**`@Inject EventService eventService`** : à ajouter aux champs injectés en haut de la classe (après `@Inject AttendanceService attendanceService;`).

**Pas d'annotations OpenAPI MicroProfile (`@Operation`, `@APIResponse`)** : les autres endpoints `/me/*` en ont, mais le Sprint 5 a choisi `openapi.yaml` comme source de vérité. Cohérence avec `/me/attendances` qui n'en a pas non plus sur la branche parente.

### `backend/src/main/java/ch/unige/events/service/EventService.java`

Ajouter la méthode **à la fin de la classe** (après les helpers privés existants `countAttending`, `countWaitlisted`, `computeAvailableSpots`, `normalizeTags`) :

```java
@Transactional
public List<EventDTO> getMyEvents(String auth0Id, EventStatus status, int page, int size) {
    User user = User.findByAuth0Id(auth0Id)
            .orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"));

    StringBuilder jpql = new StringBuilder("SELECT e FROM Event e WHERE e.creator.id = :creatorId");
    Map<String, Object> params = new HashMap<>();
    params.put("creatorId", user.id);
    if (status != null) {
        jpql.append(" AND e.status = :status");
        params.put("status", status);
    }
    jpql.append(" ORDER BY e.createdAt DESC, e.id DESC");

    List<Event> events = Event.<Event>find(jpql.toString(), params)
            .page(page, size)
            .list();

    List<Long> ids = events.stream().map(e -> e.id).toList();
    Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(
            ids, AttendanceStatus.ATTENDING, entityManager);
    Map<Long, Long> waitlistedCounts = Attendance.countGroupedByStatus(
            ids, AttendanceStatus.WAITLISTED, entityManager);

    return events.stream()
            .map(e -> {
                long att = attendingCounts.getOrDefault(e.id, 0L);
                long wait = waitlistedCounts.getOrDefault(e.id, 0L);
                return EventDTO.from(e, att, computeAvailableSpots(e.capacity, att), wait);
            })
            .toList();
}
```

**Imports nécessaires** (déjà présents en grande partie sur la branche parente) :
- `ch.unige.events.entity.User`
- `jakarta.transaction.Transactional`
- `jakarta.ws.rs.NotFoundException`
- `java.util.*`

**Pas de modification de `getAll(...)`** : la signature existante est conservée. La branche `organizerId != null` reste active, mais le Resource garantit désormais que `status == PUBLISHED` (null devient PUBLISHED avant l'appel). Le comportement au niveau Service est inchangé — on continue de produire la condition `e.creator.id = :organizerId`.

### `backend/src/test/java/ch/unige/events/service/EventServiceMock.java`

Ajouter la méthode mock correspondante :

```java
@Override
public List<EventDTO> getMyEvents(String auth0Id, EventStatus status, int page, int size) {
    if (forceMyEventsNotFound) {
        throw new NotFoundException("User profile not found — call GET /users/me first");
    }
    return myEventsFixture.stream()
            .filter(e -> status == null || e.status() == status)
            .toList();
}

public boolean forceMyEventsNotFound = false;
public List<EventDTO> myEventsFixture = new ArrayList<>();
```

**Pourquoi un fixture** : les `*Mock` du projet remplacent le service en `@QuarkusTest` unitaire. Le mock ne touche pas la DB — il retourne une liste préconfigurée. Cohérent avec le pattern `eventsFixture` déjà en place pour `getAll`.

---

## Implémentation frontend — fichier par fichier

### Ordre strict

1. `frontend/src/services/eventApi.ts` (nouvelle fonction + type)
2. `frontend/src/hooks/useMyEvents.ts` (bascule sur le nouvel endpoint)
3. `frontend/src/pages/my-events/MyPublicationsPage.tsx` (adaptation de l'appel)
4. `frontend/src/__tests__/services/eventApi.test.ts` (tests nouvelle fonction)
5. `frontend/src/__tests__/hooks/useMyEvents.test.ts` (adaptation)
6. `frontend/src/__tests__/pages/my-events/MyPublicationsPage.test.tsx` (vérif du call)
7. `frontend/docs/components.md` (section services)

### `frontend/src/services/eventApi.ts`

Ajouter après la fonction `getAll` (ne pas modifier `getAll` ni `EventsParams`) :

```ts
export interface MyEventsParams {
  status?: EventStatus
  page?: number
  size?: number
}

export async function getMyEvents(params: MyEventsParams = {}): Promise<Event[]> {
  const response = await api.get<Event[]>('/users/me/events', { params })
  return response.data
}
```

**Imports** : `EventStatus` et `Event` sont déjà importés en haut du fichier. Aucun nouvel import.

### `frontend/src/hooks/useMyEvents.ts`

Réécriture complète du hook. Nouvelle version :

```ts
import { useCallback, useEffect, useState } from 'react'
import axios from 'axios'
import {
  cancelEvent,
  deleteEvent,
  getMyEvents,
  publishEvent,
  restoreEvent,
} from '@/services/eventApi'
import type { Event, EventStatus } from '@/types/event'

function extractValidationErrors(e: unknown): string[] {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as { errors?: unknown } | undefined
    if (data && Array.isArray(data.errors)) {
      return data.errors.filter((s): s is string => typeof s === 'string')
    }
  }
  return []
}

export type PublishResult =
  | { ok: true }
  | { ok: false; errors: string[] }

interface UseMyEventsResult {
  events: Event[]
  loading: boolean
  error: string | null
  refresh: () => void
  publish: (id: number) => Promise<PublishResult>
  cancel: (id: number) => Promise<boolean>
  restore: (id: number) => Promise<boolean>
  permanentlyDelete: (id: number) => Promise<boolean>
}

export function useMyEvents(status: EventStatus): UseMyEventsResult {
  const [events, setEvents] = useState<Event[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const fetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const data = await getMyEvents({ status, size: 100 })
      setEvents(data)
    } catch {
      setError('Impossible de charger vos événements.')
    } finally {
      setLoading(false)
    }
  }, [status])

  useEffect(() => {
    fetch()
  }, [fetch])

  const publish = useCallback(async (id: number): Promise<PublishResult> => {
    try {
      await publishEvent(id)
      setEvents(prev => prev.filter(e => e.id !== id))
      return { ok: true }
    } catch (e) {
      const errors = extractValidationErrors(e)
      return { ok: false, errors }
    }
  }, [])

  const cancel = useCallback(async (id: number) => {
    try {
      await cancelEvent(id)
      setEvents(prev => prev.filter(e => e.id !== id))
      return true
    } catch {
      return false
    }
  }, [])

  const restore = useCallback(async (id: number) => {
    try {
      await restoreEvent(id)
      setEvents(prev => prev.filter(e => e.id !== id))
      return true
    } catch {
      return false
    }
  }, [])

  const permanentlyDelete = useCallback(async (id: number) => {
    try {
      await deleteEvent(id)
      setEvents(prev => prev.filter(e => e.id !== id))
      return true
    } catch {
      return false
    }
  }, [])

  return { events, loading, error, refresh: fetch, publish, cancel, restore, permanentlyDelete }
}
```

**Changements précis** :
- Import `getMyEvents` (nouveau) au lieu de `getAll`.
- Signature : `useMyEvents(status: EventStatus)` — un seul paramètre.
- Corps du `fetch` : plus de garde `if (!organizerId)`, plus de tri client. Assignation directe `setEvents(data)`.
- Dépendances du `useCallback` : `[status]` au lieu de `[organizerId, status]`.

**Tout le reste (publish, cancel, restore, permanentlyDelete)** : inchangé — ces callbacks manipulent l'état local après un call mutation, ils n'ont rien à voir avec la source des events.

### `frontend/src/pages/my-events/MyPublicationsPage.tsx`

Un seul changement : la ligne d'appel à `useMyEvents`.

**Avant** :
```tsx
const { events, loading, error, publish, cancel, restore, permanentlyDelete } = useMyEvents(user?.id ?? null, status)
```

**Après** :
```tsx
const { events, loading, error, publish, cancel, restore, permanentlyDelete } = useMyEvents(status)
```

**`const { user } = useAuth()` peut rester** — d'autres parties de la page l'utilisent (vérifier avec grep avant de supprimer). Si `useAuth` n'est plus du tout utilisé, supprimer l'import et le destructuring.

**`<PrivateRoute>` garantit déjà la session** : la page ne peut pas être rendue sans utilisateur authentifié, donc pas besoin de garde côté hook.

---

## Tests

Cible : **100 %** de couverture sur les lignes nouvelles (JaCoCo backend, V8 frontend). Style aligné sur `specs_scrum-126-129.md`.

### `backend/src/test/java/ch/unige/events/resource/UserResourceTest.java` — ajouts

| Test | Scénario | HTTP |
|---|---|---|
| `getMyEvents_empty_returnsEmptyArray` | User authentifié, aucun event créé | 200, `[]` |
| `getMyEvents_threeStatuses_returnsAllThreeWithoutFilter` | Seed 1 DRAFT + 1 PUBLISHED + 1 CANCELLED pour user A, appel sans `status` | 200, taille 3 |
| `getMyEvents_filterDraft_returnsOnlyDraft` | Seed même état, appel `?status=DRAFT` | 200, taille 1, statut DRAFT |
| `getMyEvents_ordersByCreatedAtDesc` | Seed 3 events avec `createdAt` croissants (A < B < C) | 200, ordre C, B, A |
| `getMyEvents_paginationPage0Size1_returnsFirst` | Seed 3 events, appel `?page=0&size=1` | 200, taille 1, event le plus récent |
| `getMyEvents_paginationPage1Size1_returnsSecond` | Même seed, `?page=1&size=1` | 200, taille 1, 2e event |
| `getMyEvents_userNotFound_returns404` | JWT valide mais `User` non provisionné en DB | 404 |
| **`getMyEvents_userBcannotSeeUserAEvents`** | Seed user A avec 1 DRAFT, auth user B, appel `/me/events?status=DRAFT` | 200, taille 0 (sécurité critique) |
| `getMyEvents_unauthenticated_returns401` | Pas de JWT | 401 |
| `getMyEvents_sizeOutOfBounds_returns400` | `?size=0` ou `?size=101` | 400 |
| `getMyEvents_pageNegative_returns400` | `?page=-1` | 400 |

**Fixture clé** : utiliser `@TestTransaction` pour isolation, seed via `Event.persist()` + `User.persist()`. Pattern déjà en place dans `EventResourceTest`.

**Pour le test de sécurité `getMyEvents_userBcannotSeeUserAEvents`** : exploiter le helper `TestJwt` ou l'équivalent sur la branche parente pour émettre deux JWTs différents. Si ce helper n'existe pas, utiliser la stratégie `quarkus.test.security.user = "..."` avec rebuild du profile de test — voir les tests existants d'`AttendanceResourceTest` pour le pattern.

### `backend/src/test/java/ch/unige/events/resource/EventResourceTest.java` — ajouts

| Test | Scénario | HTTP |
|---|---|---|
| `getAll_organizerIdWithoutStatus_implicitlyFiltersPublished` | Seed user A : 1 DRAFT + 1 PUBLISHED + 1 CANCELLED. Appel `GET /events?organizerId=<A>` | 200, taille 1 (uniquement le PUBLISHED) |
| `getAll_organizerIdWithStatusPublished_allowed` | Même seed, `GET /events?organizerId=<A>&status=PUBLISHED` | 200, taille 1 |
| **`getAll_organizerIdWithStatusDraft_returns400`** | `GET /events?organizerId=<A>&status=DRAFT` | 400, body `error: "organizer_filter_requires_published"` |
| **`getAll_organizerIdWithStatusCancelled_returns400`** | `GET /events?organizerId=<A>&status=CANCELLED` | 400, même error |
| `getAll_withoutOrganizerId_unchanged` | `GET /events?status=DRAFT` sans `organizerId` | 200 (comportement existant préservé) |

### `backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java` — ajouts

Tests d'intégration DevServices PostgreSQL (mêmes patterns que les tests SCRUM-129).

| Test | Scénario |
|---|---|
| `getMyEvents_returnsAllStatuses` | Seed 1 DRAFT + 1 PUBLISHED + 1 CANCELLED pour un user, status=null → taille 3 |
| `getMyEvents_filtersOnStatus` | Même seed, status=DRAFT → taille 1 |
| `getMyEvents_ordersByCreatedAtDesc` | Seed 3 events avec persist + `Thread.sleep(2)` entre chaque pour garantir des `createdAt` distincts. Vérifier ordre inverse. |
| `getMyEvents_emptyForUnrelatedUser` | Seed 1 event pour user A, query getMyEvents avec auth0Id de user B → taille 0 |
| `getMyEvents_userNotFound_throwsNotFound` | auth0Id inexistant → `NotFoundException` |
| `getMyEvents_populatesAvailableSpotsAndWaitlistedCount` | Seed event capacity=2, 2 ATTENDING + 1 WAITLISTED → DTO `availableSpots=0`, `waitlistedCount=1` |
| `getMyEvents_nullCapacityReturnsNullAvailableSpots` | Seed event sans capacity → DTO `availableSpots=null` |
| `getMyEvents_pagination` | Seed 5 events, `page=0 size=2` → taille 2, `page=1 size=2` → taille 2, `page=2 size=2` → taille 1 |
| `getMyEvents_tieBreakerById` | Seed 2 events avec `createdAt` forcés identiques (via `entityManager.createQuery("UPDATE Event ...")`) → ordre déterministe par `id DESC` |

### `frontend/src/__tests__/services/eventApi.test.ts` — ajouts

| Test | Scénario |
|---|---|
| `getMyEvents_callsCorrectEndpoint` | Mock `api.get`, appel `getMyEvents({ status: 'DRAFT' })` → vérifier `api.get` appelé avec `'/users/me/events'` et `{ params: { status: 'DRAFT' } }` |
| `getMyEvents_noParams_callsWithEmptyParams` | `getMyEvents()` → `api.get` appelé avec `'/users/me/events'` et `{ params: {} }` |
| `getMyEvents_returnsResponseData` | Mock retourne `[{ id: 1, ... }]` → résultat strictement égal |

### `frontend/src/__tests__/hooks/useMyEvents.test.ts` — adapter

Remplacer les tests existants qui mockent `getAll` par des tests qui mockent `getMyEvents`. Couvrir :

| Test | Scénario |
|---|---|
| `fetchOnMount_callsGetMyEvents_withStatus` | Mount avec `status='DRAFT'` → `getMyEvents` appelé avec `{ status: 'DRAFT', size: 100 }` |
| `refetchOnStatusChange` | Re-render avec `status='PUBLISHED'` → 2e appel `getMyEvents` |
| `noLongerPassesOrganizerId` | Assertion négative : `getMyEvents` n'est **pas** appelé avec une clé `organizerId` dans ses params (guard contre régression) |
| `doesNotSortClientSide` | Mock retourne des events dans un ordre donné → l'état `events` reflète exactement cet ordre (pas de re-sort) |
| `fetchError_setsErrorMessage` | Mock rejette → `error === 'Impossible de charger vos événements.'` |
| `publishOptimisticRemoval` | Appel `publish(id)` OK → `events` ne contient plus l'id |
| `publishFailure_returnsErrors` | Mock `publishEvent` rejette avec `errors: [...]` → retour `{ ok: false, errors }` |
| `cancelSuccess` / `cancelFailure` | Idem |
| `restoreSuccess` / `restoreFailure` | Idem |
| `permanentlyDeleteSuccess` / `permanentlyDeleteFailure` | Idem |

**Supprimer** les tests qui passaient `organizerId=null` — la signature a changé.

### `frontend/src/__tests__/pages/my-events/MyPublicationsPage.test.tsx` — adapter

Vérifier que la page appelle bien `useMyEvents(status)` (un seul argument, pas `user?.id`). Si les tests actuels mockent `useMyEvents`, adapter la signature attendue.

---

## OpenAPI — diff résumé

| Endpoint | Avant | Après |
|---|---|---|
| `GET /events` | `200`, `400` absent | `200`, **nouveau** `400 organizer_filter_requires_published`, description `organizerId` durcie |
| `GET /users/me/events` | **absent** | **nouveau** `200 Event[]`, `401`, `404`, paramètres `status`, `page`, `size` |

**Pas de breaking change** pour les clients existants de `GET /events` — ils n'utilisaient pas `organizerId + status=DRAFT` (vérifié côté frontend). Les nouveaux clients doivent migrer.

---

## Documentation

### `backend/docs/data-model.md`

Ajouter une sous-section "Endpoints liés à l'utilisateur courant" (ou l'équivalent existant) mentionnant `GET /users/me/events` :

```markdown
### Endpoint `GET /users/me/events`

Retourne les events où `creator.id` est l'utilisateur authentifié, tri `createdAt DESC`.
Tous les statuts (DRAFT, PUBLISHED, CANCELLED) sont inclus par défaut. Filtre optionnel
`status=<enum>`. Pagination `page`/`size` (default 0/20).

**Règle d'autorisation** : l'identité est dérivée du JWT. Il n'existe aucun moyen
d'énumérer les events d'un autre utilisateur via cet endpoint. C'est la raison pour
laquelle DRAFT et CANCELLED peuvent être retournés sans vérification supplémentaire.

Le filtre `GET /events?organizerId=<uuid>` reste disponible publiquement pour lister
les events **publiés** d'un organisateur, mais rejette explicitement toute combinaison
avec un statut non-PUBLISHED (`400 organizer_filter_requires_published`).
```

### `backend/docs/sprint-context.md`

Marquer SCRUM-133 comme "Done" dans la section Sprint 5. Ajouter une note expliquant l'anticipation depuis S6 (faille de sécurité).

### `frontend/docs/components.md`

Section "Services" : ajouter `getMyEvents(params)` dans la liste des fonctions exportées par `eventApi.ts`, avec la signature exacte et un renvoi à l'endpoint backend.

### `openapi/openapi.yaml`

Déjà traité en section "Implémentation backend" — c'est le premier fichier à modifier.

---

## Edge cases — comportements explicites

| Cas | Comportement |
|---|---|
| User authentifié sans profil en DB | `404 User profile not found — call GET /users/me first` (aligné sur `EventService.create`) |
| `status` invalide (ex. `?status=FOO`) | `400` — gestion standard Quarkus de l'enum parsing |
| `page=0 size=0` | `400` — `@Positive` rejette `size=0` |
| `size > 100` | `400` — `@Max(100)` |
| Utilisateur avec 0 events | `200 []` |
| `organizerId` sans `status` sur `GET /events` | Coercition silencieuse à `PUBLISHED` |
| `organizerId=<A>` par un utilisateur non authentifié | OK si `status=PUBLISHED` (endpoint `@PermitAll`). La faille sur DRAFT/CANCELLED est fermée par le rejet 400. |
| `organizerId` avec UUID inexistant | `200 []` (pas d'erreur — le filtre retourne vide, comportement existant) |
| Query params combinés (`organizerId + category + faculty + status=PUBLISHED`) | Fonctionne — le filtre croise tout comme avant |
| `useMyEvents` appelé sans session | Impossible — la route est sous `<PrivateRoute>`. Si ça arrivait (dev error), le 401 Axios serait intercepté par l'intercepteur existant (redirect `/login`) |

---

## Ordre d'implémentation

1. **`openapi/openapi.yaml`** — nouveau path `/users/me/events`, durcissement de la description de `organizerId` sur `GET /events`, ajout de la réponse 400. **EN PREMIER.**
2. **`resource/EventResource.java`** — durcissement `getAll(...)` (validation 400).
3. **`resource/UserResource.java`** — ajout `getMyEvents(...)`, injection `EventService`.
4. **`service/EventService.java`** — ajout `getMyEvents(String, EventStatus, int, int)`.
5. **`service/EventServiceMock.java`** (tests) — mock correspondant + fixture.
6. **Tests backend** :
   - `UserResourceTest` — 11 tests (incluant les tests de sécurité critiques).
   - `EventResourceTest` — 5 tests sur le durcissement de `organizerId`.
   - `EventServiceCoverageTest` — 9 tests d'intégration DevServices.
7. **`./mvnw verify`** — vert en local avant de toucher le frontend.
8. **Frontend** :
   - `services/eventApi.ts` — ajout `getMyEvents` et `MyEventsParams`.
   - `hooks/useMyEvents.ts` — réécriture.
   - `pages/my-events/MyPublicationsPage.tsx` — adaptation du call.
   - Tests : `eventApi.test.ts`, `useMyEvents.test.ts`, `MyPublicationsPage.test.tsx`.
9. **`npm run lint && npm run test && npm run build`** — vert en local.
10. **Documentation** :
    - `backend/docs/data-model.md`
    - `backend/docs/sprint-context.md`
    - `frontend/docs/components.md`
11. **Commit messages atomiques** :
    - `feat(scrum-133): add GET /users/me/events endpoint`
    - `feat(scrum-133): harden GET /events organizerId filter`
    - `test(scrum-133): cover new endpoint + security regression tests`
    - `refactor(scrum-133): migrate useMyEvents to /users/me/events`
    - `docs(scrum-133): document new endpoint and authorization rule`

---

## Fichiers touchés (récap)

### Modifiés — backend

- `openapi/openapi.yaml`
- `backend/src/main/java/ch/unige/events/resource/UserResource.java`
- `backend/src/main/java/ch/unige/events/resource/EventResource.java`
- `backend/src/main/java/ch/unige/events/service/EventService.java`
- `backend/src/test/java/ch/unige/events/resource/UserResourceTest.java`
- `backend/src/test/java/ch/unige/events/resource/EventResourceTest.java`
- `backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java`
- `backend/src/test/java/ch/unige/events/service/EventServiceMock.java`
- `backend/docs/data-model.md`
- `backend/docs/sprint-context.md`

### Modifiés — frontend

- `frontend/src/services/eventApi.ts`
- `frontend/src/hooks/useMyEvents.ts`
- `frontend/src/pages/my-events/MyPublicationsPage.tsx`
- `frontend/src/__tests__/services/eventApi.test.ts`
- `frontend/src/__tests__/hooks/useMyEvents.test.ts`
- `frontend/src/__tests__/pages/my-events/MyPublicationsPage.test.tsx`
- `frontend/docs/components.md`

### Créés

Aucun fichier source nouveau. Aucune classe nouvelle. Aucun composant React nouveau.

---

## Interdits stricts

- **PAS** de migration SQL (aucun changement de schéma).
- **PAS** de nouvel index DB.
- **PAS** de modification de `EventService.getAll()` (signature inchangée).
- **PAS** de suppression du query param `organizerId` sur `GET /events` (le cas public "events publiés de X" reste légitime).
- **PAS** de TanStack Query, de cache, de refactor du hook au-delà de la bascule d'endpoint.
- **PAS** de modification des autres consommateurs de `getAll` (`EventsSearchPage`, `EventsListPage`, etc.) — ils n'utilisent pas `organizerId`.
- **PAS** de rebase sur `main` avant que `feature/s5-my-events-page` **et** `feature/s5-event-extra-fields-capacity` soient mergées.
- **PAS** de logique métier dans les Resource — sauf la validation croisée 400 qui est une règle d'input pure et assumée (cf. Décision 3).
- **PAS** de snake_case.
- **PAS** de `any` TypeScript.
- **PAS** de `useAuth` dans `useMyEvents` (l'identité vient du JWT côté serveur, inutile côté client).
- **PAS** de tri client dans `useMyEvents`.
- **PAS** de TODO commenté dans le code.

---

## Checklist Sonar

- [ ] 100 % couverture sur les lignes nouvelles (JaCoCo backend, V8 frontend)
- [ ] Duplication < 3 % sur le code nouveau
- [ ] Security Rating : A
- [ ] Reliability Rating : A
- [ ] Maintainability Rating : A
- [ ] Security Review Rating : A

---

## Checklist finale

### Avant push

- [ ] `./mvnw verify` vert
- [ ] `cd frontend && npm run lint && npm run test && npm run build` vert
- [ ] Rapport JaCoCo `backend/target/site/jacoco/index.html` — lignes nouvelles 100 %
- [ ] `grep -rn "organizerId" frontend/src` — plus aucun usage actif à part le type `EventsParams`
- [ ] `openapi.yaml` validé par le linter pré-commit
- [ ] Les 11 tests `UserResourceTest` nommés passent individuellement (run ciblé)
- [ ] Le test de sécurité `getMyEvents_userBcannotSeeUserAEvents` existe et est vert
- [ ] Le test `getAll_organizerIdWithStatusDraft_returns400` existe et est vert

### Avant PR

- [ ] Branche `feature/s6-my-events-endpoint` basée sur `origin/feature/s5-event-extra-fields-capacity`
- [ ] Commits atomiques nommés selon la convention (`feat(scrum-133)`, `test`, `refactor`, `docs`)
- [ ] Description de PR reprenant : motivation sécurité, décisions tranchées, ordre de merge, dépendances
- [ ] Base du PR : `main` (pas `feature/s5-event-extra-fields-capacity` — on cible main, GitHub affiche le cumul)

### Avant merge

- [ ] `feature/s5-my-events-page` mergée dans `main`
- [ ] `feature/s5-event-extra-fields-capacity` mergée dans `main` (après rebase)
- [ ] `git rebase origin/main` sur `feature/s6-my-events-endpoint`
- [ ] `git push --force-with-lease`
- [ ] CI verte après rebase
- [ ] Review approuvée

---

## Prompt de lancement d'implémentation

```
Tu vas implémenter SCRUM-133 (nouvel endpoint GET /users/me/events + durcissement du query param organizerId sur GET /events + migration du hook frontend useMyEvents) en une seule PR atomique backend + frontend pour le projet UNIGE Events.

## ÉTAPE 0 — Création de la branche

Avant TOUT, crée la branche depuis feature/s5-event-extra-fields-capacity (PAS main, PAS feature/s5-my-events-page) :

    git fetch origin
    git checkout -b feature/s6-my-events-endpoint origin/feature/s5-event-extra-fields-capacity

Pourquoi cette base : on hérite de la signature EventDTO.from(Event, long, Long, long) et des helpers computeAvailableSpots / countWaitlisted introduits par SCRUM-126/129 qu'on doit obligatoirement réutiliser dans getMyEvents. On hérite aussi de useMyEvents, MyPublicationsPage et eventApi.ts qu'on doit refactorer. Repartir de main ou de s5-my-events-page causerait soit des conflits, soit un usage de l'ancienne signature DTO.

Le PR final ciblera main. Ne rebase PAS sur main avant que les deux branches parentes (feature/s5-my-events-page PUIS feature/s5-event-extra-fields-capacity) soient mergées — sinon tu réintroduis les conflits qu'on cherche à éviter.

## Source unique de vérité

specs_archives/specs_claude/specs_scrum-133.md — à lire intégralement avant de coder. Toutes les décisions (endpoint dédié, tri createdAt DESC, durcissement 400, bascule du hook frontend, ordre des checks, signatures exactes) y sont tranchées. Tu n'as RIEN à inventer.

## À lire avant de commencer

1. backend/AGENTS.md — conventions (camelCase, pas de logique dans Resource hors validation d'input, Hibernate update donc pas de migration SQL, openapi.yaml en premier).
2. frontend/AGENTS.md — conventions (alias @/, camelCase, pas de any, types depuis src/types/).
3. specs_archives/specs_claude/specs_scrum-126-129.md — pour comprendre la signature EventDTO.from et les helpers qu'on réutilise.
4. backend/docs/backlog_s5_s10.md — SCRUM-133 pour confirmer l'intention d'origine.
5. Code backend sur la branche (origin/feature/s5-event-extra-fields-capacity) :
   - resource/UserResource.java (pattern /me/attendances à copier)
   - resource/EventResource.java (getAll à durcir)
   - service/EventService.java (helpers déjà présents : computeAvailableSpots, countWaitlisted, bulk countGroupedByStatus)
   - entity/User.java (findByAuth0Id)
6. Code frontend sur la même branche :
   - services/eventApi.ts (pattern getAll)
   - hooks/useMyEvents.ts (à réécrire)
   - pages/my-events/MyPublicationsPage.tsx (un seul call à adapter)

## Ordre d'implémentation strict

1. openapi/openapi.yaml — EN PREMIER. Ajouter le path /users/me/events, durcir la description du param organizerId sur /events, ajouter la réponse 400 organizer_filter_requires_published.
2. resource/EventResource.java — dans getAll, ajouter la validation croisée : organizerId sans status → status=PUBLISHED (coercition) ; organizerId avec status!=PUBLISHED → 400 WebApplicationException(ApiErrorResponse("organizer_filter_requires_published", ...)).
3. resource/UserResource.java — injecter EventService, ajouter GET /me/events avec @Authenticated, QueryParams status/page/size (mêmes defaults que /events : 0, 20, Min 0, Positive, Max 100). Déléguer à eventService.getMyEvents(identity.getPrincipal().getName(), status, page, size).
4. service/EventService.java — ajouter getMyEvents(String auth0Id, EventStatus status, int page, int size) @Transactional. Résoudre user via User.findByAuth0Id().orElseThrow(NotFoundException). JPQL : SELECT e FROM Event e WHERE e.creator.id = :creatorId [AND e.status = :status] ORDER BY e.createdAt DESC, e.id DESC. Pagination via .page(page, size). Deux bulk counts : ATTENDING et WAITLISTED via Attendance.countGroupedByStatus. Factory EventDTO.from(e, att, computeAvailableSpots(e.capacity, att), wait).
5. service/EventServiceMock.java — ajouter le mock getMyEvents qui retourne un fixture filtré par status, avec un flag forceMyEventsNotFound pour tester le 404.
6. Tests backend (viser 100 % couverture sur les lignes nouvelles) :
   - UserResourceTest : 11 tests incluant les 2 tests de sécurité critiques (user B ne voit pas les DRAFTs de user A, 401 sans JWT).
   - EventResourceTest : 5 tests sur le durcissement de organizerId.
   - EventServiceCoverageTest (DevServices PostgreSQL) : 9 tests d'intégration incluant ordre createdAt DESC, pagination, tie-breaker par id, 404 user, user A ne voit pas les events de B.
7. ./mvnw verify — DOIT être vert avant de toucher le frontend.
8. services/eventApi.ts — ajouter interface MyEventsParams { status?: EventStatus; page?: number; size?: number } et la fonction getMyEvents(params) qui appelle api.get<Event[]>('/users/me/events', { params }).
9. hooks/useMyEvents.ts — réécriture : signature useMyEvents(status: EventStatus), import getMyEvents au lieu de getAll, suppression du garde if(!organizerId), suppression du tri client (setEvents(data) direct), dépendance useCallback = [status]. Tout le bloc publish/cancel/restore/permanentlyDelete reste inchangé.
10. pages/my-events/MyPublicationsPage.tsx — une seule ligne à changer : useMyEvents(user?.id ?? null, status) devient useMyEvents(status). Laisser useAuth si d'autres parties de la page l'utilisent (grep avant de supprimer).
11. Tests frontend : eventApi.test.ts (3 tests), useMyEvents.test.ts (adapter tous les tests existants, garder la couverture publish/cancel/restore/delete), MyPublicationsPage.test.tsx (adapter la signature mockée de useMyEvents).
12. npm run lint && npm run test && npm run build — DOIT être vert.
13. Documentation :
    - backend/docs/data-model.md : nouvelle section "Endpoint GET /users/me/events" avec la règle d'autorisation explicite.
    - backend/docs/sprint-context.md : marquer SCRUM-133 Done, noter l'anticipation depuis S6 pour motif sécurité.
    - frontend/docs/components.md : ajouter getMyEvents dans la section Services d'eventApi.ts.

## Interdits stricts

- PAS de migration SQL (aucun changement de schéma).
- PAS de nouvel index DB.
- PAS de modification de la signature de EventService.getAll() ni de la logique interne pour le cas organizerId — le Resource garantit désormais status=PUBLISHED avant l'appel, le Service reste inchangé.
- PAS de suppression du query param organizerId sur GET /events — il reste légitime pour le cas public.
- PAS de TanStack Query, pas de cache, pas de refactor du hook au-delà de la bascule d'endpoint.
- PAS de modification des autres consommateurs de getAll (EventsSearchPage, EventsListPage, CalendarPage, etc.) — vérifie avec grep qu'ils n'utilisent pas organizerId avec un status non-PUBLISHED. Si un consommateur inattendu existe, LÈVE LA MAIN avant d'implémenter.
- PAS de rebase sur main tant que les deux branches parentes ne sont pas mergées.
- PAS de logique métier dans les Resource hors de la validation croisée 400 (cf. Décision 3).
- PAS de snake_case, pas de any, pas de TODO commenté.
- PAS de useAuth dans useMyEvents.
- PAS de tri client dans useMyEvents (le serveur trie déjà).

## Conventions à respecter

- camelCase partout.
- Validation via @Min, @Max, @Positive, @DefaultValue sur les query params — identique à GET /events.
- 100 % couverture JaCoCo (backend) et V8 (frontend) sur les lignes nouvelles.
- Sonar : ratings A partout, duplication < 3 %.
- JPQL via StringBuilder + Map<String, Object> params — pattern identique à EventService.getAll existant.
- Factory EventDTO.from(Event, long, Long, long) — signature exacte, pas de variante.

## Critères de done

- [ ] ./mvnw verify vert localement et en CI.
- [ ] npm run lint && npm run test && npm run build vert localement et en CI.
- [ ] JaCoCo ≥ 100 % sur les lignes nouvelles (rapport target/site/jacoco/index.html).
- [ ] V8 ≥ 100 % sur les lignes nouvelles frontend.
- [ ] Les 2 tests de sécurité critiques sont verts :
  - backend : user B ne voit pas les DRAFTs de user A via /me/events
  - backend : GET /events?organizerId=<A>&status=DRAFT retourne 400
- [ ] Grep "organizerId" dans frontend/src : plus aucun call site actif à part le type EventsParams.
- [ ] openapi.yaml modifié EN PREMIER et cohérent avec le code.
- [ ] backend/docs/data-model.md, backend/docs/sprint-context.md, frontend/docs/components.md mis à jour dans le même PR.
- [ ] PR ouverte avec base main, description reprenant la motivation sécurité et l'ordre de merge.
- [ ] Commits atomiques bien nommés (feat/test/refactor/docs scrum-133).
```
