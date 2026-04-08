# Specs SCRUM-83 — CRUD de base `/api/events`

> **Branche :** `feature/s2-event-crud`
> **Prérequis :** SCRUM-82 mergé sur main
> **Règle d'or :** Modifier `docs/openapi/openapi.yaml` EN PREMIER, puis coder Resource → Service → Entity → Test

---

## Contexte

### Ce qui existe déjà (ne pas retoucher)

| Fichier | État |
|---|---|
| `entity/Event.java` | Complet (title, description, location, startDate, endDate, category, bannerUrl, creator, status, capacity, createdAt, updatedAt) |
| `entity/EventCategory.java` | Enum complet : ACADEMIC, SPORTS, CULTURAL, SOCIAL, CONFERENCE, OTHER |
| `entity/EventStatus.java` | Enum complet : DRAFT, PUBLISHED, CANCELLED |
| `dto/EventDTO.java` | Record complet avec factory `EventDTO.from(Event)` |
| `dto/EventRequestBase.java` | Classe abstraite avec validation (@NotBlank title/location, @NotNull @Future startDate, @NotNull endDate/category) |
| `dto/CreateEventRequest.java` | Hérite de EventRequestBase — prêt |
| `dto/UpdateEventRequest.java` | Hérite de EventRequestBase + champ `status` — prêt |

### Ce qui est à corriger / compléter

| Fichier | Problème |
|---|---|
| `resource/EventResource.java` | Field injection au lieu de constructeur ; `POST` manque `@Authenticated` + liaison creator ; endpoints GET/{id}, PUT/{id}, DELETE/{id} absents |
| `service/EventService.java` | `create()` ne lie pas le creator ; méthodes getById, update, delete absentes ; getAll() sans pagination ni filtres |

---

## Étape 0 — openapi.yaml (OBLIGATOIRE EN PREMIER)

Avant tout code, ajouter dans `docs/openapi/openapi.yaml` :

### Nouveaux paths

```yaml
/events/{id}:
  get:
    summary: Get event by ID
    tags: [Events]
    parameters:
      - name: id
        in: path
        required: true
        schema: { type: integer, format: int64 }
    responses:
      "200":
        description: Event found
        content:
          application/json:
            schema: { $ref: '#/components/schemas/EventDTO' }
      "404":
        description: Event not found
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ApiErrorResponse' }

  put:
    summary: Update an event (full update)
    tags: [Events]
    security:
      - bearerAuth: []
    parameters:
      - name: id
        in: path
        required: true
        schema: { type: integer, format: int64 }
    requestBody:
      required: true
      content:
        application/json:
          schema: { $ref: '#/components/schemas/UpdateEventRequest' }
    responses:
      "200":
        description: Event updated
        content:
          application/json:
            schema: { $ref: '#/components/schemas/EventDTO' }
      "400":
        description: Validation error
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ValidationErrorResponse' }
      "401":
        description: Unauthorized
      "403":
        description: Forbidden — not the creator
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ApiErrorResponse' }
      "404":
        description: Event not found
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ApiErrorResponse' }

  delete:
    summary: Cancel an event (soft delete)
    tags: [Events]
    security:
      - bearerAuth: []
    parameters:
      - name: id
        in: path
        required: true
        schema: { type: integer, format: int64 }
    responses:
      "204":
        description: Event cancelled
      "401":
        description: Unauthorized
      "403":
        description: Forbidden — not the creator
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ApiErrorResponse' }
      "404":
        description: Event not found
        content:
          application/json:
            schema: { $ref: '#/components/schemas/ApiErrorResponse' }
```

### Mise à jour du path `/events` (GET)

Ajouter les query parameters optionnels :

```yaml
parameters:
  - name: page
    in: query
    schema: { type: integer, default: 0 }
  - name: size
    in: query
    schema: { type: integer, default: 20 }
  - name: status
    in: query
    schema: { $ref: '#/components/schemas/EventStatus' }
  - name: category
    in: query
    schema: { $ref: '#/components/schemas/EventCategory' }
  - name: organizerId
    in: query
    schema: { type: string, format: uuid }
```

### Mise à jour du path `/events` (POST)

Ajouter la sécurité :

```yaml
security:
  - bearerAuth: []
```

Ajouter la réponse 401.

---

## Étape 1 — `EventResource.java`

### Corrections structurelles

1. **Passer en injection par constructeur** (règle SonarCloud — jamais de field injection) :

```java
private final EventService eventService;
private final SecurityIdentity identity;

@Inject
public EventResource(EventService eventService, SecurityIdentity identity) {
    this.eventService = eventService;
    this.identity = identity;
}
```

### GET /api/events (modification)

Ajouter les query params de pagination et filtrage. Rester `@PermitAll`.

```java
@GET
@PermitAll
public List<EventDTO> getAll(
    @QueryParam("page") @DefaultValue("0") int page,
    @QueryParam("size") @DefaultValue("20") int size,
    @QueryParam("status") EventStatus status,
    @QueryParam("category") EventCategory category,
    @QueryParam("organizerId") UUID organizerId
) {
    return eventService.getAll(page, size, status, category, organizerId);
}
```

### POST /api/events (correction)

Passer de `@PermitAll` à `@Authenticated`. Transmettre le `auth0Id` au service pour lier le creator.

```java
@POST
@Authenticated
public Response create(@Valid CreateEventRequest request) {
    String auth0Id = identity.getPrincipal().getName();
    EventDTO created = eventService.create(auth0Id, request);
    return Response.status(Response.Status.CREATED).entity(created).build();
}
```

### GET /api/events/{id} (nouveau)

```java
@GET
@Path("/{id}")
@PermitAll
public Response getById(@PathParam("id") Long id) {
    EventDTO event = eventService.getById(id);
    return Response.ok(event).build();
}
```

- Retourne `200` + `EventDTO` si trouvé
- Le service lève `NotFoundException` → mapper existant renvoie `404`

### PUT /api/events/{id} (nouveau)

```java
@PUT
@Path("/{id}")
@Authenticated
public Response update(@PathParam("id") Long id, @Valid UpdateEventRequest request) {
    String auth0Id = identity.getPrincipal().getName();
    EventDTO updated = eventService.update(id, auth0Id, request);
    return Response.ok(updated).build();
}
```

- `@Valid` : active la validation héritée de `EventRequestBase` (title, location, startDate, endDate, category obligatoires) + le champ `status` optionnel de `UpdateEventRequest`
- Le service lève `NotFoundException` (→ 404) ou `ForbiddenException` (→ 403)
- Retourne `200` + `EventDTO` complet

### DELETE /api/events/{id} (nouveau)

```java
@DELETE
@Path("/{id}")
@Authenticated
public Response delete(@PathParam("id") Long id) {
    String auth0Id = identity.getPrincipal().getName();
    eventService.delete(id, auth0Id);
    return Response.noContent().build();
}
```

- Retourne `204 No Content`
- Le service lève `NotFoundException` (→ 404) ou `ForbiddenException` (→ 403)

---

## Étape 2 — `EventService.java`

### Corrections structurelles

Ajouter l'injection par constructeur. Ajouter `EntityManager` pour les opérations de lookup User.

```java
@ApplicationScoped
public class EventService {

    private final EntityManager em;

    @Inject
    public EventService(EntityManager em) {
        this.em = em;
    }

    // ... méthodes
}
```

### getAll() — avec filtres et pagination

```java
@Transactional
public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category, UUID organizerId) {
    // Construire la requête Panache avec les filtres
    // PanacheQuery<Event> query = Event.findAll();
    // Appliquer les filtres si non-null
    // Appliquer la pagination : query.page(page, size)
    // Retourner query.list().stream().map(EventDTO::from).toList()
}
```

Logique de filtrage :
- `status != null` → `Event.find("status", status)` ou prédicat combiné
- `category != null` → filtre sur `category`
- `organizerId != null` → filtre sur `creator.id = :organizerId`
- Combiner les filtres actifs dans une seule requête Panache (utiliser `find("param1 = ?1 AND param2 = ?2", ...)`)

### create() — avec liaison creator

```java
@Transactional
public EventDTO create(String auth0Id, CreateEventRequest request) {
    User creator = User.find("auth0Id", auth0Id).firstResult();
    // Si creator est null : le profil n'existe pas encore (cas théorique, l'OIDC crée toujours le profil)
    // Ne pas lever d'exception ici — creator reste null si introuvable

    Event event = new Event();
    event.title = request.title;
    event.description = request.description;
    event.location = request.location;
    event.startDate = request.startDate;
    event.endDate = request.endDate;
    event.category = request.category;
    event.bannerUrl = request.bannerUrl;
    event.capacity = request.capacity;
    event.creator = creator;  // Lié à l'utilisateur authentifié
    event.persist();
    return EventDTO.from(event);
}
```

### getById() — nouveau

```java
@Transactional
public EventDTO getById(Long id) {
    Event event = (Event) Event.findByIdOptional(id)
            .orElseThrow(NotFoundException::new);
    return EventDTO.from(event);
}
```

### update() — nouveau

```java
@Transactional
public EventDTO update(Long id, String auth0Id, UpdateEventRequest request) {
    Event event = (Event) Event.findByIdOptional(id)
            .orElseThrow(NotFoundException::new);

    // Vérification : seul le creator peut modifier
    boolean isCreator = event.creator != null
            && event.creator.auth0Id != null
            && event.creator.auth0Id.equals(auth0Id);
    if (!isCreator) {
        throw new ForbiddenException("Only the event creator can update this event");
    }

    event.title = request.title;
    event.description = request.description;
    event.location = request.location;
    event.startDate = request.startDate;
    event.endDate = request.endDate;
    event.category = request.category;
    event.bannerUrl = request.bannerUrl;
    event.capacity = request.capacity;
    if (request.status != null) {
        event.status = request.status;
    }
    // @PreUpdate sur l'entité met à jour updatedAt automatiquement

    return EventDTO.from(event);
}
```

> **Note :** `@Transactional` sur le service + la mutation Panache Active Record — Hibernate détecte les changements et flushes automatiquement au commit. Pas besoin de `event.persist()` pour une mise à jour.

### delete() — nouveau (soft-delete)

```java
@Transactional
public void delete(Long id, String auth0Id) {
    Event event = (Event) Event.findByIdOptional(id)
            .orElseThrow(NotFoundException::new);

    boolean isCreator = event.creator != null
            && event.creator.auth0Id != null
            && event.creator.auth0Id.equals(auth0Id);
    if (!isCreator) {
        throw new ForbiddenException("Only the event creator can cancel this event");
    }

    event.status = EventStatus.CANCELLED;
    // Pas de suppression physique — soft-delete via statut CANCELLED
}
```

---

## Étape 3 — Tests

### Stratégie de test

Créer un `EventServiceMock` sur le modèle de `UserServiceMock` (in-memory, pas de DB) pour les tests de `EventResourceTest`.

**Fichier :** `src/test/java/ch/unige/events/service/EventServiceMock.java`

```java
@Mock
@ApplicationScoped
public class EventServiceMock extends EventService {

    private final Map<Long, Event> eventsById = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);
    public static volatile boolean forceForbiddenOnUpdate = false;
    public static volatile boolean forceForbiddenOnDelete = false;

    public void reset() {
        eventsById.clear();
        idSequence.set(1);
        forceForbiddenOnUpdate = false;
        forceForbiddenOnDelete = false;
    }

    public Event seedEvent(String creatorAuth0Id, String title) {
        // Créer un Event avec un User creator minimal (creator.auth0Id = creatorAuth0Id)
        // Stocker dans eventsById
        // Retourner l'Event
    }

    @Override
    public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category, UUID organizerId) {
        // Retourner la liste filtrée depuis eventsById
    }

    @Override
    public EventDTO create(String auth0Id, CreateEventRequest request) {
        // Créer Event avec creator.auth0Id = auth0Id
    }

    @Override
    public EventDTO getById(Long id) {
        Event e = eventsById.get(id);
        if (e == null) throw new NotFoundException();
        return EventDTO.from(e);
    }

    @Override
    public EventDTO update(Long id, String auth0Id, UpdateEventRequest request) {
        if (forceForbiddenOnUpdate) throw new ForbiddenException("Forbidden");
        Event e = eventsById.get(id);
        if (e == null) throw new NotFoundException();
        // vérif creator + mutation
        return EventDTO.from(e);
    }

    @Override
    public void delete(Long id, String auth0Id) {
        if (forceForbiddenOnDelete) throw new ForbiddenException("Forbidden");
        Event e = eventsById.get(id);
        if (e == null) throw new NotFoundException();
        e.status = EventStatus.CANCELLED;
    }
}
```

### Cas de tests à couvrir dans `EventResourceTest.java`

#### GET /events (existants + pagination)

| Test | Attendu |
|---|---|
| `getAll_returnsOk` *(existant)* | 200, liste |
| `getAll_withStatusFilter_returnsFiltered` | 200, liste filtrée |

#### POST /events

| Test | Attendu |
|---|---|
| `create_withValidRequest_returns201` *(à mettre à jour)* | 201, `creatorId` non-null (car maintenant lié à l'auth) |
| `create_unauthenticated_returns401` | 401 |
| `create_withBlankTitle_returns400` *(existant)* | 400 |
| `create_withNullCategory_returns400` *(existant)* | 400 |

#### GET /events/{id}

| Test | Attendu |
|---|---|
| `getById_existingEvent_returns200` | 200, EventDTO avec bon `id` |
| `getById_unknownEvent_returns404` | 404, `error = "not_found"` |

#### PUT /events/{id}

| Test | Attendu |
|---|---|
| `update_asCreator_returns200` | 200, EventDTO mis à jour |
| `update_unauthenticated_returns401` | 401 |
| `update_notCreator_returns403` | 403, `error = "forbidden"` |
| `update_unknownEvent_returns404` | 404, `error = "not_found"` |
| `update_invalidBody_returns400` | 400 (title blank, etc.) |

#### DELETE /events/{id}

| Test | Attendu |
|---|---|
| `delete_asCreator_returns204` | 204 |
| `delete_unauthenticated_returns401` | 401 |
| `delete_notCreator_returns403` | 403, `error = "forbidden"` |
| `delete_unknownEvent_returns404` | 404, `error = "not_found"` |

### Pattern @TestSecurity à utiliser

```java
@Test
@TestSecurity(user = "auth0|alice", attributes = {
    @SecurityAttribute(key = "email", value = "alice@example.com")
})
void create_authenticated_returns201() {
    // ...
}
```

---

## Étape 4 — Documentation

Après implémentation, mettre à jour obligatoirement (règle AGENTS.md) :

- `docs/sprint-context.md` — marquer SCRUM-83 comme terminé
- `docs/openapi/openapi.yaml` — déjà fait à l'étape 0

---

## Résumé des fichiers à toucher

| Fichier | Action |
|---|---|
| `docs/openapi/openapi.yaml` | Ajouter GET/{id}, PUT/{id}, DELETE/{id} ; update GET filters ; POST auth |
| `resource/EventResource.java` | Constructor injection, @Authenticated POST, 3 nouveaux endpoints, query params |
| `service/EventService.java` | Constructor injection, create avec creator, getById, update, delete, getAll avec filtres |
| `test/.../service/EventServiceMock.java` | Nouveau fichier — mock in-memory |
| `test/.../resource/EventResourceTest.java` | Mise à jour test POST, +10 nouveaux tests |
| `docs/sprint-context.md` | Marquer tâche terminée |

---

## Règles critiques à respecter

- **camelCase partout** — jamais de snake_case dans les champs Java ni en JSON
- **Pas de préfixe `is`** sur les booléens — utiliser `active`, `featured`, etc.
- **Constructor injection ONLY** — jamais `@Inject` sur un champ (SonarCloud)
- **Resource ne touche jamais les entités** — tout passe par le Service
- **@Transactional** sur toutes les mutations du Service
- **Soft-delete** — `DELETE /events/{id}` passe `status = CANCELLED`, jamais de suppression physique
- **PUT retourne l'objet complet** (200 + EventDTO), jamais 204
- **openapi.yaml AVANT le code**
