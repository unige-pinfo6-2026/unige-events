# SCRUM-77 — Champ `faculty` sur `Event` + filtre `?faculty=`

**Sprint 3 — Tâche 2/2 | Backend uniquement**

---

## Contexte

L'API exposait déjà un enum `Faculty` dans le schéma OpenAPI (utilisé sur l'entité `User`).
Un commentaire TODO dans `openapi.yaml` signalait que le filtre `?faculty=` sur `GET /events/search`
serait activé dès que le champ serait présent sur l'entité `Event` (SCRUM-77).

Ce sprint ajoute :
1. L'enum Java `Faculty` (miroir du schéma OpenAPI déjà défini).
2. Le champ `faculty` (nullable) sur l'entité `Event`.
3. Un index DB sur la colonne `faculty`.
4. Le filtre `?faculty=` sur `GET /api/events` et `GET /api/events/search`.
5. La propagation de `faculty` dans `EventDTO`, `CreateEventRequest`, `UpdateEventRequest`.

Aucune migration SQL — Hibernate est en mode `update`, la colonne est ajoutée automatiquement.

---

## Enum `Faculty`

### Fichier : `entity/Faculty.java`

```java
package ch.unige.events.entity;

public enum Faculty {
    SCIENCES, LETTRES, DROIT, MEDECINE, SES, PSYCHOLOGIE, THEOLOGIE, FTI, GSI
}
```

Valeurs identiques à celles de l'enum `Faculty` dans `openapi.yaml` (source de vérité).

---

## Modifications — `Event.java`

### Ajout du champ (nullable)

```java
@Enumerated(EnumType.STRING)
public Faculty faculty;
```

### Ajout de l'index DB

```java
@Table(name = "events", indexes = {
    @Index(name = "idx_event_creator",  columnList = "creator_id"),
    @Index(name = "idx_event_start_date", columnList = "start_date"),
    @Index(name = "idx_event_faculty",  columnList = "faculty")   // SCRUM-77
})
```

---

## Modifications — `EventDTO.java`

Ajout du champ `faculty` dans le record et dans la méthode `from()`.

```java
public record EventDTO(
        Long id,
        String title,
        String description,
        String location,
        LocalDateTime startDate,
        LocalDateTime endDate,
        EventCategory category,
        Faculty faculty,           // SCRUM-77 — nullable
        String bannerUrl,
        UUID creatorId,
        EventStatus status,
        Integer capacity,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EventDTO from(Event event) {
        return new EventDTO(
                event.id,
                event.title,
                event.description,
                event.location,
                event.startDate,
                event.endDate,
                event.category,
                event.faculty,     // SCRUM-77
                event.bannerUrl,
                event.creator != null ? event.creator.id : null,
                event.status,
                event.capacity,
                event.createdAt,
                event.updatedAt
        );
    }
}
```

---

## Modifications — `EventRequestBase.java`

Ajout du champ optionnel `faculty` (pas de contrainte `@NotNull` — le champ est nullable).

```java
public Faculty faculty;   // optionnel — nullable
```

---

## Modifications — `EventService.java`

### `getAll()` — nouveau paramètre `Faculty faculty`

```java
public List<EventDTO> getAll(int page, int size, EventStatus status,
                              EventCategory category, UUID organizerId,
                              LocalDateTime endDateFrom, Faculty faculty)
```

Condition ajoutée :

```java
if (faculty != null) {
    conditions.add("faculty = :faculty");
    params.put("faculty", faculty);
}
```

### `create()` — propagation de `faculty`

```java
event.faculty = request.faculty;
```

### `update()` — propagation de `faculty`

```java
event.faculty = request.faculty;
```

---

## Modifications — `EventResource.java`

Nouveau `@QueryParam` sur `getAll()` :

```java
@QueryParam("faculty") Faculty faculty
```

Appel mis à jour :

```java
return eventService.getAll(page, size, status, category, organizerId, endDateFrom, faculty);
```

---

## Modifications — `EventSearchService.java`

### `search()` — nouveau paramètre `Faculty faculty`

```java
public List<EventDTO> search(String q, EventCategory category, Faculty faculty,
                              LocalDate dateFrom, LocalDate dateTo,
                              int page, int size)
```

Condition ajoutée (après `category`) :

```java
if (faculty != null) {
    conditions.add("faculty = :faculty");
    params.put("faculty", faculty);
}
```

---

## Modifications — `EventSearchResource.java`

Nouveau `@QueryParam` :

```java
@QueryParam("faculty") Faculty faculty
```

Appel mis à jour :

```java
return eventSearchService.search(q, category, faculty, dateFrom, dateTo, page, size);
```

---

## Modifications — `openapi.yaml`

### Schéma `Event` — ajout du champ `faculty`

```yaml
faculty:
  $ref: '#/components/schemas/Faculty'
  nullable: true
```

Positionné après `category`.

### Schéma `CreateEventRequest` — ajout du champ `faculty`

```yaml
faculty:
  allOf:
    - $ref: '#/components/schemas/Faculty'
  nullable: true
  description: Faculté organisatrice ou cible de l'événement (optionnel)
```

### Schéma `UpdateEventRequest` — ajout du champ `faculty`

```yaml
faculty:
  allOf:
    - $ref: '#/components/schemas/Faculty'
  nullable: true
```

### `GET /events` — nouveau paramètre `?faculty=`

```yaml
- name: faculty
  in: query
  description: Filtre les événements par faculté
  schema:
    $ref: '#/components/schemas/Faculty'
```

### `GET /events/search` — retirer le commentaire TODO SCRUM-77

Le paramètre `faculty` était déjà présent avec une note TODO. Supprimer la note et
activer le paramètre (il est maintenant fonctionnel).

---

## Tests

### Couverture requise : 100 % sur tout le code nouveau

#### `EventDTOTest.java` — ajouts

| Test | Assertion |
|------|-----------|
| `from_withFaculty_mapsFaculty` | `faculty` mappé correctement depuis l'entité |
| `from_withNullFaculty_returnsNullFaculty` | `faculty` null si non défini |

#### `EventResourceTest.java` — ajouts

| Test | Scénario | HTTP |
|------|----------|------|
| `getAll_withFacultyFilter_returnsFiltered` | Seeder 2 events (SCIENCES, LETTRES) → `?faculty=SCIENCES` retourne 1 | 200 |
| `getAll_withFacultyFilter_noMatch_returnsEmpty` | Seeder 1 event (SCIENCES) → `?faculty=DROIT` retourne 0 | 200 |
| `create_withFaculty_returnsFacultyInResponse` | POST avec `faculty=SCIENCES` → réponse contient `faculty: SCIENCES` | 201 |

#### `EventSearchResourceTest.java` — ajouts

| Test | Scénario | HTTP |
|------|----------|------|
| `search_withFaculty_returnsFiltered` | Seeder 2 events (SCIENCES, LETTRES) → `?faculty=SCIENCES` retourne 1 | 200 |
| `search_withFacultyNoMatch_returnsEmpty` | Seeder 1 event (SCIENCES) → `?faculty=DROIT` retourne [] | 200 |
| `search_withFacultyAndQ_combined` | Seeder 2 events (même titre, facultés différentes) → `?q=java&faculty=SCIENCES` retourne 1 | 200 |

#### `EventServiceCoverageTest.java` — ajouts

Tests d'intégration avec base de données réelle (via DevServices PostgreSQL).

| Test | Scénario |
|------|----------|
| `getAll_withFacultyFilter_returnsMatchingEvents` | Persiste 2 events (SCIENCES, LETTRES) → filtre SCIENCES retourne 1 |
| `create_withFaculty_persistsFaculty` | Crée event avec faculty=MEDECINE → entité en DB a faculty=MEDECINE |
| `update_withFaculty_updatesFaculty` | Met à jour faculty de SCIENCES à LETTRES → entité en DB mise à jour |
| `getAll_withFacultyNull_returnsAll` | Events avec et sans faculty → filtre null retourne tous |

#### `EventSearchServiceCoverageTest.java` — ajouts

| Test | Scénario |
|------|----------|
| `search_withFacultyFilter_returnsMatchingEvents` | Persiste 2 events (SCIENCES, DROIT) → filtre SCIENCES retourne 1 |
| `search_withFacultyAndCategory_combined` | Faculty + category combinés → filtre correct |
| `search_withNullFaculty_returnsAll` | Filtre null → tous les events retournés |

---

## Ordre d'implémentation

1. `openapi.yaml` — toujours en premier (source de vérité contrat API)
2. `Faculty.java` — enum
3. `Event.java` — champ + index
4. `EventDTO.java` — champ + `from()`
5. `EventRequestBase.java` — champ optionnel
6. `EventService.java` — `getAll()`, `create()`, `update()`
7. `EventResource.java` — `@QueryParam`
8. `EventSearchService.java` — `search()`
9. `EventSearchResource.java` — `@QueryParam`
10. Mocks de test : `EventServiceMock`, `EventSearchServiceMock`
11. Tests : `EventDTOTest`, `EventResourceTest`, `EventSearchResourceTest`,
    `EventServiceCoverageTest`, `EventSearchServiceCoverageTest`
12. Docs : `data-model.md`, `sprint-context.md`

---

## Checklist Sonar

- [ ] 100 % couverture sur les lignes nouvelles (JaCoCo)
- [ ] Duplication < 3 % sur le code nouveau
- [ ] Security Rating : A
- [ ] Reliability Rating : A
- [ ] Maintainability Rating : A
