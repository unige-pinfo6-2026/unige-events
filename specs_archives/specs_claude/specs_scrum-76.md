# Specs SCRUM-76 — Endpoint recherche full-text `GET /api/events/search`

> **Branche :** `feature/s3-search-endpoint`
> **Sprint :** 3 — Backend Tâche 1/2
> **Prérequis :** main à jour (SCRUM-83 mergé) — SCRUM-107 et SCRUM-77 ne bloquent pas cette tâche
> **Règle d'or :** Modifier `openapi.yaml` EN PREMIER, puis coder Resource → Service → Test

---

## Contexte

### Ce qui existe déjà (ne pas retoucher)

| Fichier | État |
|---|---|
| `backend/src/main/java/.../resource/EventResource.java` | Complet — CRUD `/events`, `@Path("/events")` |
| `backend/src/main/java/.../service/EventService.java` | Complet — filtres dynamiques status/category/organizerId |
| `backend/src/main/java/.../entity/Event.java` | Champs `title`, `description`, `startDate` disponibles pour la recherche |
| `backend/src/main/java/.../entity/EventCategory.java` | Enum complet : ACADEMIC, SPORTS, CULTURAL, SOCIAL, CONFERENCE, OTHER |
| `backend/src/main/java/.../dto/event/EventDTO.java` | Record avec factory `EventDTO.from(Event)` |
| `/workspace/openapi/openapi.yaml` | Path `/events/search` défini (TODO Sprint 3) — manque `page`/`size` |

### Ce qui est à créer

| Fichier | Action |
|---|---|
| `backend/src/main/java/.../resource/EventSearchResource.java` | Nouveau |
| `backend/src/main/java/.../service/EventSearchService.java` | Nouveau |
| `backend/src/test/java/.../service/EventSearchServiceMock.java` | Nouveau — mock in-memory |
| `backend/src/test/java/.../resource/EventSearchResourceTest.java` | Nouveau — 10 cas de test |

### Note sur SCRUM-77 (faculty)

Le paramètre `?faculty=` est déjà présent dans l'openapi.yaml pour `/events/search`. Il ne doit **pas être implémenté dans SCRUM-76** — l'entité `Event` n'a pas encore ce champ. SCRUM-77 ajoutera `faculty` sur `Event.java` puis étendra `EventSearchService.search()` en conséquence. Ne pas ajouter de param `faculty` dans `EventSearchResource` ni dans `EventSearchService` dans cette tâche.

### Note sur SCRUM-107

SCRUM-107 (`PATCH /events/{id}/publish`, `POST /events/{id}/image`) touche `EventResource.java` et `EventService.java`. SCRUM-76 crée de nouveaux fichiers indépendants. Aucune dépendance, aucun conflit possible. ✅

### Note sur l'architecture monorepo

Le projet est passé d'un double-repo (api + web séparés) à un monorepo (`/workspace/backend/` + `/workspace/frontend/`). Il n'y a donc **qu'un seul `openapi.yaml`** : `/workspace/openapi/openapi.yaml`. Les mentions dans les AGENTS.md frontend/backend de "copie synchronisée" sont obsolètes — mettre à jour lors de l'étape documentation.

---

## Étape 0 — `openapi.yaml` (OBLIGATOIRE EN PREMIER)

**Fichier :** `/workspace/openapi/openapi.yaml`

Localiser le path `/events/search` (actuellement marqué `# TODO: Sprint 3 — à implémenter dans EventResource`) et le remplacer par la version complète suivante :

```yaml
/events/search:
  get:
    summary: Recherche full-text d'événements
    description: |
      Recherche insensible à la casse (ILIKE) sur le titre et la description.
      Filtres optionnels : category, plage de dates (startDate).
      Le paramètre faculty sera activé par SCRUM-77 (champ non encore présent sur Event).
    operationId: searchEvents
    tags: [events]
    security: []
    parameters:
      - name: q
        in: query
        description: Terme de recherche full-text (insensible à la casse, cherche dans title et description)
        schema:
          type: string
      - name: category
        in: query
        schema:
          $ref: '#/components/schemas/EventCategory'
      - name: faculty
        in: query
        description: "TODO: SCRUM-77 — filtre activé quand le champ faculty sera ajouté à l'entité Event"
        schema:
          $ref: '#/components/schemas/Faculty'
      - name: dateFrom
        in: query
        description: Filtre les événements dont startDate >= dateFrom (début de journée, 00:00:00)
        schema:
          type: string
          format: date
      - name: dateTo
        in: query
        description: Filtre les événements dont startDate <= dateTo (fin de journée, 23:59:59)
        schema:
          type: string
          format: date
      - name: page
        in: query
        schema:
          type: integer
          default: 0
          minimum: 0
      - name: size
        in: query
        schema:
          type: integer
          default: 20
          minimum: 1
          maximum: 100
    responses:
      '200':
        description: Résultats de la recherche (tableau vide si aucun résultat — jamais 404)
        content:
          application/json:
            schema:
              type: array
              items:
                $ref: '#/components/schemas/Event'
```

---

## Étape 1 — `EventSearchResource.java`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/EventSearchResource.java`

```java
package ch.unige.events.resource;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.service.EventSearchService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import java.time.LocalDate;
import java.util.List;

@Path("/events/search")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EventSearchResource {

    private final EventSearchService eventSearchService;

    @Inject
    public EventSearchResource(EventSearchService eventSearchService) {
        this.eventSearchService = eventSearchService;
    }

    @GET
    @PermitAll
    public List<EventDTO> search(
            @QueryParam("q") String q,
            @QueryParam("category") EventCategory category,
            @QueryParam("dateFrom") LocalDate dateFrom,
            @QueryParam("dateTo") LocalDate dateTo,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        return eventSearchService.search(q, category, dateFrom, dateTo, page, size);
    }
}
```

**Points à respecter :**
- `@Path("/events/search")` est un path absolu — JAX-RS le préfère à `EventResource @Path("/events")` + `@Path("/{id}")` car un segment littéral bat un path param. Pas de conflit de routing.
- Pas d'import `SecurityIdentity` — endpoint public, `@PermitAll`
- Constructor injection : `@Inject` sur le constructeur, jamais sur un champ
- Aucune logique métier — la Resource délègue entièrement au Service
- Validations `@Min(0)`, `@Positive`, `@Max(100)` sur `page`/`size` — cohérent avec `EventResource`
- Pas de param `faculty` — activé par SCRUM-77

---

## Étape 2 — `EventSearchService.java`

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventSearchService.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class EventSearchService {

    @Transactional
    public List<EventDTO> search(String q, EventCategory category,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  int page, int size) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new HashMap<>();

        if (q != null && !q.isBlank()) {
            // ILIKE simulé via LOWER() — compatible JPQL + PostgreSQL
            // Les parenthèses sont obligatoires pour isoler le OR face aux AND suivants
            conditions.add("(lower(title) like :q or lower(description) like :q)");
            params.put("q", "%" + q.toLowerCase() + "%");
        }
        if (category != null) {
            conditions.add("category = :category");
            params.put("category", category);
        }
        if (dateFrom != null) {
            conditions.add("startDate >= :dateFrom");
            params.put("dateFrom", dateFrom.atStartOfDay());
        }
        if (dateTo != null) {
            conditions.add("startDate <= :dateTo");
            params.put("dateTo", dateTo.atTime(23, 59, 59));
        }

        PanacheQuery<Event> query;
        if (conditions.isEmpty()) {
            query = Event.find("order by startDate, id");
        } else {
            query = Event.find(String.join(" AND ", conditions) + " order by startDate, id", params);
        }

        return query.page(page, size).list().stream().map(EventDTO::from).toList();
    }
}
```

**Points à respecter :**
- `@ApplicationScoped` + `@Transactional` sur `search()` — même convention que `EventService`
- Pas de dépendances injectées : le service utilise Panache Active Record directement (`Event.find()`). Pas de constructeur `@Inject` nécessaire — la règle "constructor injection" s'applique quand il y a des dépendances à déclarer.
- Filtres **dynamiques** : chaque condition n'est ajoutée que si le paramètre est non-null (non-blank pour `q`)
- Pattern LOWER() : `lower(title) like :q` avec la valeur dans `params` déjà en minuscule + `%` — la DB ne fait que LOWER sur la colonne
- **Parenthèses obligatoires** autour du bloc `OR` : sans elles, `lower(title) like :q OR lower(description) like :q AND category = :category` s'évalue comme `lower(title) like :q OR (lower(description) like :q AND category = :category)` — résultat incorrect
- `dateFrom` → `atStartOfDay()` = `dateFrom 00:00:00` ; `dateTo` → `atTime(23, 59, 59)` = `dateTo 23:59:59`
- Tri stable : `order by startDate, id` — cohérent avec `EventService.getAll()`
- Pas de param `faculty` dans cette méthode — SCRUM-77 l'ajoutera en étendant la signature

---

## Étape 3 — Tests

### Stratégie

Même pattern que `EventResourceTest` / `EventServiceMock` :
- `EventSearchServiceMock` étend `EventSearchService` avec `@Mock @ApplicationScoped` (CDI override en test)
- Stockage in-memory (`ConcurrentHashMap`) — aucun accès DB dans les tests de Resource
- `EventSearchResourceTest` injecte le mock via `@Inject`, appelle `reset()` dans `@BeforeEach`
- RestAssured teste l'endpoint HTTP complet

### 3.1 — `EventSearchServiceMock.java`

**Fichier :** `backend/src/test/java/ch/unige/events/service/EventSearchServiceMock.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class EventSearchServiceMock extends EventSearchService {

    private final Map<Long, Event> eventsById = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public void reset() {
        eventsById.clear();
        idSequence.set(1);
    }

    /**
     * Crée un événement en mémoire.
     * title + description sont utilisés pour tester le filtre ILIKE.
     * category et startDate sont optionnels (valeurs par défaut si null).
     */
    public Event seedEvent(String title, String description,
                           EventCategory category, LocalDateTime startDate) {
        User creator = new User();
        creator.id = UUID.randomUUID();
        creator.auth0Id = "auth0|seed-user";

        Event event = new Event();
        event.id = idSequence.getAndIncrement();
        event.title = title;
        event.description = description;
        event.location = "Uni Mail";
        event.startDate = startDate != null ? startDate : LocalDateTime.now().plusDays(1);
        event.endDate = event.startDate.plusHours(2);
        event.category = category != null ? category : EventCategory.ACADEMIC;
        event.status = EventStatus.PUBLISHED;
        event.creator = creator;
        event.createdAt = LocalDateTime.now();
        event.updatedAt = LocalDateTime.now();

        eventsById.put(event.id, event);
        return event;
    }

    @Override
    public List<EventDTO> search(String q, EventCategory category,
                                  LocalDate dateFrom, LocalDate dateTo,
                                  int page, int size) {
        return eventsById.values().stream()
                .filter(e -> {
                    if (q == null || q.isBlank()) return true;
                    String lower = q.toLowerCase();
                    return (e.title != null && e.title.toLowerCase().contains(lower))
                            || (e.description != null && e.description.toLowerCase().contains(lower));
                })
                .filter(e -> category == null || e.category == category)
                .filter(e -> dateFrom == null || !e.startDate.isBefore(dateFrom.atStartOfDay()))
                .filter(e -> dateTo == null || !e.startDate.isAfter(dateTo.atTime(23, 59, 59)))
                .skip((long) page * size)
                .limit(size)
                .map(EventDTO::from)
                .toList();
    }
}
```

### 3.2 — `EventSearchResourceTest.java`

**Fichier :** `backend/src/test/java/ch/unige/events/resource/EventSearchResourceTest.java`

```java
package ch.unige.events.resource;

import ch.unige.events.entity.EventCategory;
import ch.unige.events.service.EventSearchServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class EventSearchResourceTest {

    @Inject
    EventSearchServiceMock eventSearchServiceMock;

    @BeforeEach
    void setUp() {
        eventSearchServiceMock.reset();
    }

    // --- GET /events/search (sans filtre) ---

    @Test
    void search_noParams_returns200WithAll() {
        eventSearchServiceMock.seedEvent("Conférence Java", "Talk sur Quarkus", EventCategory.CONFERENCE, null);
        eventSearchServiceMock.seedEvent("Match de foot", "Tournoi inter-facs", EventCategory.SPORTS, null);

        given()
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("", hasSize(2));
    }

    // --- Filtre ?q= (ILIKE title) ---

    @Test
    void search_withQ_matchesTitle() {
        eventSearchServiceMock.seedEvent("Conférence Java", "Talk générique", EventCategory.CONFERENCE, null);
        eventSearchServiceMock.seedEvent("Match de foot", "Tournoi inter-facs", EventCategory.SPORTS, null);

        given()
                .queryParam("q", "java")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Conférence Java"));
    }

    @Test
    void search_withQ_matchesDescription() {
        // Vérifie que l'ILIKE cherche aussi dans description
        eventSearchServiceMock.seedEvent("Conférence Tech", "Talk sur Quarkus et Java", EventCategory.CONFERENCE, null);
        eventSearchServiceMock.seedEvent("Match de foot", "Tournoi inter-facs", EventCategory.SPORTS, null);

        given()
                .queryParam("q", "quarkus")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Conférence Tech"));
    }

    @Test
    void search_withQ_isCaseInsensitive() {
        eventSearchServiceMock.seedEvent("Conférence JAVA", null, EventCategory.CONFERENCE, null);

        given()
                .queryParam("q", "java")   // minuscule → doit trouver "JAVA"
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1));
    }

    // --- Filtre ?category= ---

    @Test
    void search_withCategory_returnsFiltered() {
        eventSearchServiceMock.seedEvent("Conférence Java", null, EventCategory.CONFERENCE, null);
        eventSearchServiceMock.seedEvent("Match de foot", null, EventCategory.SPORTS, null);

        given()
                .queryParam("category", "SPORTS")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].category", is("SPORTS"));
    }

    // --- Filtre ?dateFrom= ---

    @Test
    void search_withDateFrom_excludesPastEvents() {
        LocalDateTime past = LocalDateTime.now().minusDays(5);
        LocalDateTime future = LocalDateTime.now().plusDays(5);
        eventSearchServiceMock.seedEvent("Événement passé", null, EventCategory.ACADEMIC, past);
        eventSearchServiceMock.seedEvent("Événement futur", null, EventCategory.ACADEMIC, future);

        given()
                .queryParam("dateFrom", LocalDate.now().toString())
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Événement futur"));
    }

    // --- Filtre ?dateTo= ---

    @Test
    void search_withDateTo_excludesFutureEvents() {
        LocalDateTime past = LocalDateTime.now().minusDays(5);
        LocalDateTime future = LocalDateTime.now().plusDays(5);
        eventSearchServiceMock.seedEvent("Événement passé", null, EventCategory.ACADEMIC, past);
        eventSearchServiceMock.seedEvent("Événement futur", null, EventCategory.ACADEMIC, future);

        given()
                .queryParam("dateTo", LocalDate.now().toString())
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Événement passé"));
    }

    // --- Combinaison de filtres ---

    @Test
    void search_withAllFilters_returnsOnlyMatch() {
        LocalDateTime target = LocalDateTime.now().plusDays(3);
        eventSearchServiceMock.seedEvent("Conférence Java", "Talk Quarkus", EventCategory.CONFERENCE, target);
        eventSearchServiceMock.seedEvent("Conférence Python", "Talk Django", EventCategory.CONFERENCE,
                LocalDateTime.now().plusDays(10));  // hors plage dateTo
        eventSearchServiceMock.seedEvent("Match de foot", "Tournoi", EventCategory.SPORTS, target);  // mauvaise catégorie

        given()
                .queryParam("q", "java")
                .queryParam("category", "CONFERENCE")
                .queryParam("dateFrom", LocalDate.now().toString())
                .queryParam("dateTo", LocalDate.now().plusDays(5).toString())
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].title", is("Conférence Java"));
    }

    // --- Aucun résultat ---

    @Test
    void search_noResults_returns200EmptyList() {
        // 200 avec tableau vide — jamais 404 même si 0 résultats
        eventSearchServiceMock.seedEvent("Conférence Java", null, EventCategory.CONFERENCE, null);

        given()
                .queryParam("q", "xyzresultatimpossible")
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("", hasSize(0));
    }

    // --- q blanc → ignoré ---

    @Test
    void search_blankQ_treatedAsNoFilter() {
        eventSearchServiceMock.seedEvent("Conférence Java", null, EventCategory.CONFERENCE, null);
        eventSearchServiceMock.seedEvent("Match de foot", null, EventCategory.SPORTS, null);

        given()
                .queryParam("q", "   ")   // blancs → doit retourner tout
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(2));
    }

    // --- Pagination ---

    @Test
    void search_withPageSize_returnsPaginatedResults() {
        for (int i = 1; i <= 5; i++) {
            eventSearchServiceMock.seedEvent("Événement " + i, null, EventCategory.ACADEMIC, null);
        }

        given()
                .queryParam("page", 0)
                .queryParam("size", 2)
                .when().get("/events/search")
                .then()
                .statusCode(200)
                .body("", hasSize(2));
    }
}
```

**Récapitulatif des 10 cas de test :**

| Test | Comportement vérifié |
|---|---|
| `search_noParams_returns200WithAll` | 200 + tous les events si aucun filtre |
| `search_withQ_matchesTitle` | ILIKE filtre bien sur le titre |
| `search_withQ_matchesDescription` | ILIKE cherche aussi dans description |
| `search_withQ_isCaseInsensitive` | `"java"` trouve `"JAVA"` — insensible à la casse |
| `search_withCategory_returnsFiltered` | Filtre enum category |
| `search_withDateFrom_excludesPastEvents` | startDate >= dateFrom 00:00:00 |
| `search_withDateTo_excludesFutureEvents` | startDate <= dateTo 23:59:59 |
| `search_withAllFilters_returnsOnlyMatch` | Combinaison q + category + dateFrom + dateTo |
| `search_noResults_returns200EmptyList` | 200 avec `[]` — jamais 404 pour aucun résultat |
| `search_blankQ_treatedAsNoFilter` | `q` blanc → ignoré, retourne tout |
| `search_withPageSize_returnsPaginatedResults` | page=0&size=2 → 2 résultats sur 5 |

> **Note SonarCloud :** 10 tests couvrant les 4 branches de filtres dynamiques du service (`q`, `category`, `dateFrom`, `dateTo`) + l'empty path + la pagination. La couverture de `EventSearchService` sera assurée par les tests d'intégration `@QuarkusTest` en plus des mocks. Viser **>80% coverage** sur les deux nouveaux fichiers.

---

## Étape 4 — Documentation

### `backend/docs/sprint-context.md`

Dans la section **Sprint 3**, remplacer la ligne existante par :

```markdown
- [x] `GET /events/search?q=&category=&dateFrom=&dateTo=` — full-text ILIKE sur titre + description, paginé (SCRUM-76) — `EventSearchResource` + `EventSearchService`
- [ ] Ajout du champ `faculty` (enum) sur `Event` + filtre `?faculty=` sur `GET /events` et `GET /events/search` (SCRUM-77)
```

### `backend/docs/api-contract.md`

Ajouter dans le tableau **Endpoints implémentés** :

```markdown
| `GET` | `/events/search` | `@PermitAll` | Recherche full-text (q, category, dateFrom, dateTo, page, size) | 200 |
```

### Mise à jour de la documentation monorepo

Le projet est passé de deux repos séparés à un monorepo. Mettre à jour :

- **`frontend/AGENTS.md`** — supprimer la ligne `| openapi.yaml mis à jour côté backend | Copier le fichier dans docs/openapi/openapi.yaml |` (plus de copie synchronisée — un seul fichier à `/workspace/openapi/openapi.yaml`)
- **`backend/AGENTS.md`** — s'assurer que la référence à `docs/openapi/openapi.yaml` pointe bien vers `/workspace/openapi/openapi.yaml` (chemin racine du monorepo)
- **`frontend/AGENTS.md`** — mettre à jour `docs/openapi/openapi.yaml` en `../../openapi/openapi.yaml` (chemin relatif) ou préciser le chemin absolu `/workspace/openapi/openapi.yaml`

---

## Résumé des fichiers à créer/modifier

| Fichier | Action |
|---|---|
| `/workspace/openapi/openapi.yaml` | **Modifier** — compléter `/events/search` (page, size, descriptions, remove TODO) |
| `backend/src/main/java/.../resource/EventSearchResource.java` | **Créer** |
| `backend/src/main/java/.../service/EventSearchService.java` | **Créer** |
| `backend/src/test/java/.../service/EventSearchServiceMock.java` | **Créer** |
| `backend/src/test/java/.../resource/EventSearchResourceTest.java` | **Créer** |
| `backend/docs/sprint-context.md` | **Mettre à jour** — SCRUM-76 coché, SCRUM-77 séparé |
| `backend/docs/api-contract.md` | **Mettre à jour** — ajouter `GET /events/search` |
| `frontend/AGENTS.md` | **Mettre à jour** — supprimer référence à la copie synchronisée openapi (monorepo) |
| `backend/AGENTS.md` | **Vérifier** — chemin openapi.yaml pointe vers `/workspace/openapi/openapi.yaml` |

---

## Règles critiques à respecter

| Règle | Détail |
|---|---|
| `openapi.yaml` EN PREMIER | Modifier `/workspace/openapi/openapi.yaml` avant tout code |
| Constructor injection | `@Inject` sur le constructeur de `EventSearchResource` — `EventSearchService` n'a pas de dépendances à injecter (Panache AR) |
| camelCase partout | Champs Java, JSON, paramètres de requête — jamais de snake_case |
| Logique métier dans le Service | `EventSearchResource` ne fait que valider et déléguer |
| `@Transactional` sur le Service | Sur la méthode `search()` uniquement |
| Parenthèses sur le OR | `(lower(title) like :q or lower(description) like :q)` — obligatoire pour l'isolation face aux AND |
| Pas de `faculty` dans SCRUM-76 | Ne pas ajouter ce param dans Resource ni Service — c'est SCRUM-77 |
| 200 + `[]` si aucun résultat | Jamais de 404 quand la recherche retourne 0 résultats |
| SonarCloud | 80% coverage min, 3% duplication max, Security/Reliability/Maintainability Rating A |
