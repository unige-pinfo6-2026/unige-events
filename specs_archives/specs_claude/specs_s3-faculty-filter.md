# Specs — `feature/s3-faculty-filter` (finalisation + correctifs build)

> **Branche :** `feature/s3-faculty-filter`
> **Objet :** Finaliser le rattachement d'un événement à une faculté (unique, nullable) conformément à SCRUM-77, corriger les erreurs de build backend/frontend introduites par la branche, et compléter l'intégration frontend (formulaire create/edit, affichage sur `EventCard`, filtre `?faculty=`).
> **Règle d'or :** `openapi.yaml` EN PREMIER, puis entité → DTO → service → resource → tests → frontend.

---

## Contexte

### Divergence à corriger par rapport à SCRUM-77

La branche a implémenté **plusieurs facultés par événement** (`List<Faculty> faculties` + table de jointure `event_faculties` via `@ElementCollection`), alors que la tâche JIRA SCRUM-77 — et la spec de référence [`specs_scrum-77.md`](./specs_scrum-77.md) — demandent **une faculté unique nullable**. Cette spec ramène l'implémentation à la forme attendue :

- `Faculty faculty` (champ unique, nullable) sur `Event`
- Pas de table de jointure — colonne `faculty` en enum `STRING` + index `idx_event_faculty`
- Query param **singulier** `?faculty=` sur `GET /api/events` et `GET /api/events/search`
- DTOs `EventDTO`, `CreateEventRequest`, `UpdateEventRequest` : champ unique `faculty`
- OpenAPI : idem (champ unique nullable, param singulier)

### État actuel (ce qui est fait, ce qui est cassé, ce qui manque)

**Backend (cassé)** — 8 erreurs de compilation :

| Fichier | Ligne | Erreur |
|---|---|---|
| `EventService.java` | 72, 97, 104, 129, 163, 175 | `EventDTO::from` / `EventDTO.from(event)` appelé avec 1 arg, signature actuelle `from(Event, long attendingCount)` |
| `EventService.java` | 174 | `fileStorageService.saveImage(fileUpload)` appelé avec 1 arg, signature actuelle `saveImage(FileUpload, String folder)` |
| `EventSearchService.java` | 77 | Même problème `EventDTO::from` |

**Backend (divergent)** — [`Event.java`](../../backend/src/main/java/ch/unige/events/entity/Event.java:32-36), [`EventDTO.java`](../../backend/src/main/java/ch/unige/events/dto/event/EventDTO.java:21), [`EventRequestBase.java`](../../backend/src/main/java/ch/unige/events/dto/event/EventRequestBase.java:44), [`EventService.java`](../../backend/src/main/java/ch/unige/events/service/EventService.java:34-44), [`EventSearchService.java`](../../backend/src/main/java/ch/unige/events/service/EventSearchService.java:32-48), [`EventResource.java`](../../backend/src/main/java/ch/unige/events/resource/EventResource.java:52), [`EventSearchResource.java`](../../backend/src/main/java/ch/unige/events/resource/EventSearchResource.java:35), `openapi.yaml` (schémas Event/CreateEventRequest/UpdateEventRequest + params `?faculties=` multi-valeur). Tout est à ramener en singulier.

**Frontend (cassé)** — 2 erreurs TypeScript :

| Fichier | Ligne | Erreur |
|---|---|---|
| `src/__tests__/components/event/IcsExportButton.test.tsx` | 14 | `mockEvent` n'a pas `faculty` — le type `Event` l'exige |
| `src/__tests__/utils/icsGenerator.test.ts` | 5 | Même problème |

**Frontend (déjà fait, à conserver / ajuster)** :

- [`types/faculty.ts`](../../frontend/src/types/faculty.ts) ✅ **source unique de vérité** — `FACULTIES` const object (`as const`) + `Faculty = keyof typeof FACULTIES`. Clés anglaises : `SCIENCES`, `MEDICINE`, `LETTERS`, `SOCIAL_SCIENCES`, `GSEM`, `LAW`, `THEOLOGY`, `PSYCHOLOGY`, `FTI`. Chaque entrée expose `name`, `abbr`, `logo`, `color` (hex). `Faculty` est importé depuis ce fichier partout — plus rien dans `event.ts`.
- [`types/event.ts`](../../frontend/src/types/event.ts) ✅ — `Faculty` supprimé du fichier, importé depuis `./faculty`. `Event.faculty?: Faculty | null`, `CreateEventRequest.faculty?: Faculty | null`, `UpdateEventRequest.faculty?: Faculty | null`.
- [`components/faculty/FacultyBadge.tsx`](../../frontend/src/components/faculty/FacultyBadge.tsx) ✅ — prop `id: Faculty` (non nullable). Couleur via `style={{ backgroundColor: faculty.color }}` (inline style — pas de classe Tailwind dynamique). Libellé : `faculty.abbr`. `aria-label` : `faculty.name`.
- [`components/event/EventForm.tsx`](../../frontend/src/components/event/EventForm.tsx) ✅ — select faculté via `Object.entries(FACULTIES)`, libellé `faculty.name`. Import depuis `@/types/faculty`.
- [`components/event/EventSearchSidebar.tsx`](../../frontend/src/components/event/EventSearchSidebar.tsx) ✅ — chips faculté via `Object.entries(FACULTIES)`, libellé `faculty.abbr`. Import depuis `@/types/faculty`.
- [`types/search.ts`](../../frontend/src/types/search.ts) ✅ — `Faculty` importé depuis `./faculty`.
- [`hooks/useEventForm.ts`](../../frontend/src/hooks/useEventForm.ts) ✅ — `faculty` géré dans values/defaults/toFormValues/payload.
- [`hooks/useEventSearch.ts`](../../frontend/src/hooks/useEventSearch.ts) ✅ — URL sync + payload `faculty` singulier.

### Stratégie de correction pour `EventDTO.from(Event, long)`

Deux options étaient envisageables :

1. **Surcharge `EventDTO.from(Event)` avec `attendingCount = 0`** — simple, mais menteur : `getAll`, `getById`, `search` exposeraient toujours `attendingCount: 0` côté client alors que des attendances peuvent exister. Cela casse des fonctionnalités d'affichage de jauge/compteur sur la page liste et la page détail.
2. **Charger les counts avant mapping** — pattern déjà utilisé dans [`FavoriteService.getFavorites()`](../../backend/src/main/java/ch/unige/events/service/FavoriteService.java:62-67) via `Attendance.countGroupedByStatus(eventIds, AttendanceStatus.ATTENDING, entityManager)`. Respecte l'intention des compteurs.

**Décision : option 2 partout où `attendingCount` est métier**, avec un raccourci pour les méthodes qui opèrent sur un seul event connu :

- `getAll` / `search` → `Attendance.countGroupedByStatus(ids, ATTENDING, em)` en une requête, puis mapping.
- `getById` / `create` / `update` / `publish` / `uploadImage` → compteur de l'event unique via `Attendance.count("event.id = ?1 and status = ?2", id, AttendanceStatus.ATTENDING)`. Pour `create`, le compteur est trivialement `0` (aucun attendance possible sur un event qui vient d'être persisté) — on passe `0L` littéral.

> **Note :** `Attendance.countGroupedByStatus` existe déjà (utilisé par `FavoriteService`). Vérifier sa signature exacte avant de l'importer dans `EventService` / `EventSearchService`.

### Stratégie pour `saveImage(FileUpload, String folder)`

`EventService.uploadImage()` uploadait déjà dans `"events/banners"` avant l'ajout du paramètre `folder` (voir [`FileStorageService.java:65`](../../backend/src/main/java/ch/unige/events/service/FileStorageService.java:65)). Passer la constante `"events/banners"` à [`EventService.java:174`](../../backend/src/main/java/ch/unige/events/service/EventService.java:174).

---

## Étape 0 — `openapi.yaml` (OBLIGATOIRE EN PREMIER)

**Fichier :** `/workspace/openapi/openapi.yaml`

### 0.1 — Schéma `Event` : remplacer le tableau `faculties` par un champ `faculty` unique

Localiser (vers la ligne 174) :

```yaml
        faculties:
          type: array
          items:
            $ref: '#/components/schemas/Faculty'
          description: Facultés organisatrices ou cibles de l'événement (vide = toutes)
```

Remplacer par :

```yaml
        faculty:
          $ref: '#/components/schemas/Faculty'
          nullable: true
          description: Faculté organisatrice ou cible de l'événement (optionnel)
```

### 0.2 — Schéma `CreateEventRequest` : idem (ligne ~228)

Remplacer le bloc `faculties` par :

```yaml
        faculty:
          allOf:
            - $ref: '#/components/schemas/Faculty'
          nullable: true
          description: Faculté organisatrice ou cible de l'événement (optionnel)
```

### 0.3 — Schéma `UpdateEventRequest` : idem (ligne ~274)

Remplacer le bloc `faculties` par :

```yaml
        faculty:
          allOf:
            - $ref: '#/components/schemas/Faculty'
          nullable: true
          description: Faculté organisatrice ou cible de l'événement (optionnel)
```

### 0.4 — `GET /events` : paramètre `faculty` singulier (ligne ~856)

Remplacer :

```yaml
        - name: faculties
          in: query
          description: Filtre les événements par facultés (multi-valeur, ex. ?faculties=SCIENCES&faculties=DROIT)
          explode: true
          schema:
            type: array
            items:
              $ref: '#/components/schemas/Faculty'
```

Par :

```yaml
        - name: faculty
          in: query
          description: Filtre les événements par faculté
          schema:
            $ref: '#/components/schemas/Faculty'
```

### 0.5 — `GET /events/search` : paramètre `faculty` singulier (ligne ~934)

Remplacer le bloc `faculties` par la même version singulière que ci-dessus.
Mettre aussi à jour la `description` du path (ligne ~917-920) :

```yaml
      description: |
        Recherche insensible à la casse (ILIKE) sur le titre et la description.
        Filtres optionnels : category, faculty, plage de dates (startDate).
```

---

## Étape 1 — `Faculty.java` (mis à jour — IDs anglais)

**Fichier :** `backend/src/main/java/ch/unige/events/entity/Faculty.java`

> ⚠️ **Mis à jour post-sprint** : les valeurs ont été renommées en anglais pour aligner backend et frontend.

```java
package ch.unige.events.entity;

public enum Faculty {
    SCIENCES, MEDICINE, LETTERS, SOCIAL_SCIENCES, GSEM, LAW, THEOLOGY, PSYCHOLOGY, FTI
}
```

Correspondance anciens → nouveaux IDs :

| Ancien       | Nouveau         |
|--------------|-----------------|
| MEDECINE     | MEDICINE        |
| LETTRES      | LETTERS         |
| SES          | SOCIAL_SCIENCES |
| GSI          | GSEM            |
| DROIT        | LAW             |
| THEOLOGIE    | THEOLOGY        |
| PSYCHOLOGIE  | PSYCHOLOGY      |

---

## Étape 2 — `Event.java` — champ unique + index

**Fichier :** `backend/src/main/java/ch/unige/events/entity/Event.java`

Remplacer :

```java
    @ElementCollection
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "event_faculties", joinColumns = @JoinColumn(name = "event_id"))
    @Column(name = "faculty")
    public List<Faculty> faculties = new ArrayList<>();
```

Par :

```java
    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "varchar(255)")
    public Faculty faculty;
```

> ⚠️ **Mis à jour post-sprint** : `@Column(columnDefinition = "varchar(255)")` est requis sur tous les champs `@Enumerated(EnumType.STRING)` pour empêcher Hibernate 6+ de générer un CHECK constraint PostgreSQL listant les valeurs de l'enum. Ce constraint n'est jamais mis à jour automatiquement en mode `update`, ce qui provoque une `ConstraintViolationException` lors de tout changement de l'enum. Même annotation appliquée à `category` et `status`.

Et mettre à jour l'annotation `@Table` pour ajouter l'index :

```java
@Entity
@Table(name = "events", indexes = {
        @Index(name = "idx_event_creator", columnList = "creator_id"),
        @Index(name = "idx_event_start_date", columnList = "start_date"),
        @Index(name = "idx_event_faculty", columnList = "faculty")
})
public class Event extends PanacheEntity {
```

**Imports à nettoyer** : supprimer `java.util.ArrayList` et `java.util.List` s'ils ne sont plus utilisés.

> ⚠️ **Attention DB (dev/local)** : Hibernate est en mode `update` — il **ajoute** la colonne `faculty` mais ne supprime pas la table de jointure `event_faculties` laissée par l'implémentation précédente. En local, drop manuel de la table `event_faculties` si nécessaire (la doc `backend/docs/data-model.md` décrit le mode `update`). **Aucune migration SQL à écrire** — l'exercice se fait sans DDL explicite.

---

## Étape 3 — `EventDTO.java`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/event/EventDTO.java`

Remplacement complet :

```java
package ch.unige.events.dto.event;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Faculty;

import java.time.LocalDateTime;
import java.util.UUID;

public record EventDTO(
        Long id,
        String title,
        String description,
        String location,
        LocalDateTime startDate,
        LocalDateTime endDate,
        EventCategory category,
        Faculty faculty,
        String bannerUrl,
        UUID creatorId,
        EventStatus status,
        Integer capacity,
        long attendingCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EventDTO from(Event event, long attendingCount) {
        return new EventDTO(
                event.id,
                event.title,
                event.description,
                event.location,
                event.startDate,
                event.endDate,
                event.category,
                event.faculty,
                event.bannerUrl,
                event.creator != null ? event.creator.id : null,
                event.status,
                event.capacity,
                attendingCount,
                event.createdAt,
                event.updatedAt
        );
    }
}
```

**Points** :
- Signature unique `from(Event, long)` — **pas de surcharge** avec `attendingCount = 0` (voir décision section « Stratégie de correction » en haut).
- Plus de `List<Faculty>` — champ unique `Faculty faculty` nullable.
- Imports `ArrayList` / `List` supprimés.

---

## Étape 4 — `EventRequestBase.java`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/event/EventRequestBase.java`

Remplacer :

```java
    public List<Faculty> faculties = new ArrayList<>();
```

Par :

```java
    public Faculty faculty;   // optionnel — nullable, pas de @NotNull
```

Supprimer les imports `java.util.ArrayList` et `java.util.List` s'ils deviennent inutiles.

---

## Étape 5 — `EventService.java`

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventService.java`

### 5.1 — `getAll()` : filtre `faculty` unique + attending counts réels

```java
@Inject
EntityManager entityManager;   // nouveau — pour Attendance.countGroupedByStatus

@Transactional
public List<EventDTO> getAll(int page, int size, EventStatus status, EventCategory category,
                              UUID organizerId, LocalDateTime endDateFrom, Faculty faculty) {
    StringBuilder jpql = new StringBuilder("SELECT e FROM Event e");
    List<String> conditions = new ArrayList<>();
    Map<String, Object> params = new HashMap<>();

    if (faculty != null) {
        conditions.add("e.faculty = :faculty");
        params.put("faculty", faculty);
    }
    if (status != null) {
        conditions.add("e.status = :status");
        params.put("status", status);
    }
    if (category != null) {
        conditions.add("e.category = :category");
        params.put("category", category);
    }
    if (organizerId != null) {
        conditions.add("e.creator.id = :organizerId");
        params.put("organizerId", organizerId);
    }
    if (endDateFrom != null) {
        conditions.add("e.endDate >= :endDateFrom");
        params.put("endDateFrom", endDateFrom);
    }

    if (!conditions.isEmpty()) {
        jpql.append(" WHERE ").append(String.join(" AND ", conditions));
    }
    jpql.append(" ORDER BY e.startDate, e.id");

    List<Event> events = Event.<Event>find(jpql.toString(), params)
            .page(page, size)
            .list();

    List<Long> ids = events.stream().map(e -> e.id).toList();
    Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(
            ids, AttendanceStatus.ATTENDING, entityManager);

    return events.stream()
            .map(e -> EventDTO.from(e, attendingCounts.getOrDefault(e.id, 0L)))
            .toList();
}
```

**Changements** :
- Suppression de `SELECT DISTINCT`, du `JOIN e.faculties`, du paramètre `List<Faculty>`.
- Ajout d'`EntityManager entityManager` injecté pour `Attendance.countGroupedByStatus`.
- Imports à ajouter : `ch.unige.events.entity.Attendance`, `ch.unige.events.entity.AttendanceStatus`, `jakarta.persistence.EntityManager`.
- **Signature publique changée** : dernier param passe de `List<Faculty>` à `Faculty`. Impacte `EventResource` (étape 7) et les mocks de test (étape 10).

### 5.2 — `create()` : propager faculty unique + attendingCount = 0

Remplacer :

```java
event.faculties = request.faculties != null ? new ArrayList<>(request.faculties) : new ArrayList<>();
```

Par :

```java
event.faculty = request.faculty;
```

Et remplacer `return EventDTO.from(event);` (ligne 97) par :

```java
return EventDTO.from(event, 0L);
```

**Justification** : un event qui vient d'être persisté n'a mathématiquement aucun attendance — `0L` littéral est exact.

### 5.3 — `getById()` : compteur via requête unique

Remplacer `return EventDTO.from(event);` (ligne 104) par :

```java
long attendingCount = Attendance.count(
        "event.id = ?1 and status = ?2", id, AttendanceStatus.ATTENDING);
return EventDTO.from(event, attendingCount);
```

### 5.4 — `update()` : faculty + compteur

Remplacer :

```java
event.faculties = request.faculties != null ? new ArrayList<>(request.faculties) : new ArrayList<>();
```

Par :

```java
event.faculty = request.faculty;
```

Et remplacer `return EventDTO.from(event);` (ligne 129) par :

```java
long attendingCount = Attendance.count(
        "event.id = ?1 and status = ?2", id, AttendanceStatus.ATTENDING);
return EventDTO.from(event, attendingCount);
```

### 5.5 — `publish()` : compteur

Remplacer `return EventDTO.from(event);` (ligne 163) par :

```java
long attendingCount = Attendance.count(
        "event.id = ?1 and status = ?2", id, AttendanceStatus.ATTENDING);
return EventDTO.from(event, attendingCount);
```

### 5.6 — `uploadImage()` : `saveImage` + compteur

Remplacer :

```java
event.bannerUrl = fileStorageService.saveImage(fileUpload);
return EventDTO.from(event);
```

Par :

```java
event.bannerUrl = fileStorageService.saveImage(fileUpload, "events/banners");
long attendingCount = Attendance.count(
        "event.id = ?1 and status = ?2", id, AttendanceStatus.ATTENDING);
return EventDTO.from(event, attendingCount);
```

---

## Étape 6 — `EventSearchService.java`

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventSearchService.java`

Remplacement complet de la méthode `search()` :

```java
@Inject
EntityManager entityManager;

@Transactional
public List<EventDTO> search(String q, EventCategory category, Faculty faculty,
                              LocalDate dateFrom, LocalDate dateTo,
                              int page, int size) {
    StringBuilder jpql = new StringBuilder("SELECT e FROM Event e");
    List<String> conditions = new ArrayList<>();
    Map<String, Object> params = new HashMap<>();

    if (q != null && !q.isBlank()) {
        conditions.add("(lower(e.title) like :q or lower(e.description) like :q)");
        params.put("q", "%" + q.toLowerCase(Locale.ROOT) + "%");
    }
    if (category != null) {
        conditions.add("e.category = :category");
        params.put("category", category);
    }
    if (faculty != null) {
        conditions.add("e.faculty = :faculty");
        params.put("faculty", faculty);
    }
    if (dateFrom != null) {
        LocalDateTime dateFromUtc = dateFrom.atStartOfDay(ZURICH)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        conditions.add("e.startDate >= :dateFrom");
        params.put("dateFrom", dateFromUtc);
    }
    if (dateTo != null) {
        LocalDateTime dateToUtc = dateTo.atTime(23, 59, 59).atZone(ZURICH)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
        conditions.add("e.startDate <= :dateTo");
        params.put("dateTo", dateToUtc);
    }

    if (!conditions.isEmpty()) {
        jpql.append(" WHERE ").append(String.join(" AND ", conditions));
    }
    jpql.append(" ORDER BY e.startDate, e.id");

    List<Event> events = Event.<Event>find(jpql.toString(), params)
            .page(page, size)
            .list();

    List<Long> ids = events.stream().map(e -> e.id).toList();
    Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(
            ids, AttendanceStatus.ATTENDING, entityManager);

    return events.stream()
            .map(e -> EventDTO.from(e, attendingCounts.getOrDefault(e.id, 0L)))
            .toList();
}
```

**Changements** :
- Suppression de `SELECT DISTINCT`, du `JOIN e.faculties`, du paramètre `List<Faculty>`.
- Signature publique : `faculty` passe en 3ᵉ position (`String q, EventCategory category, Faculty faculty, LocalDate dateFrom, LocalDate dateTo, int page, int size`).
- Ajout de `EntityManager` injecté pour les compteurs.
- Imports à ajouter : `Attendance`, `AttendanceStatus`, `EntityManager`, `jakarta.inject.Inject`.
- **Conserver** les parenthèses autour du `OR` et la conversion `Europe/Zurich → UTC` (règles critiques SCRUM-76).

---

## Étape 7 — `EventResource.java`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/EventResource.java`

Remplacer le `@QueryParam("faculties") List<Faculty> faculties` par un paramètre singulier et mettre à jour l'appel :

```java
@GET
@PermitAll
public List<EventDTO> getAll(
        @QueryParam("page") @DefaultValue("0") @Min(0) int page,
        @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size,
        @QueryParam("status") EventStatus status,
        @QueryParam("category") EventCategory category,
        @QueryParam("organizerId") UUID organizerId,
        @QueryParam("endDateFrom") LocalDateTime endDateFrom,
        @QueryParam("faculty") Faculty faculty) {
    return eventService.getAll(page, size, status, category, organizerId, endDateFrom, faculty);
}
```

Supprimer l'import `java.util.List` s'il ne sert plus (il reste utilisé pour le type de retour — donc le laisser).

---

## Étape 8 — `EventSearchResource.java`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/EventSearchResource.java`

```java
@GET
@PermitAll
public List<EventDTO> search(
        @QueryParam("q") String q,
        @QueryParam("category") EventCategory category,
        @QueryParam("faculty") Faculty faculty,
        @QueryParam("dateFrom") LocalDate dateFrom,
        @QueryParam("dateTo") LocalDate dateTo,
        @QueryParam("page") @DefaultValue("0") @Min(0) int page,
        @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
    return eventSearchService.search(q, category, faculty, dateFrom, dateTo, page, size);
}
```

---

## Étape 9 — Mocks de test backend

### 9.1 — `EventServiceMock.java`

**Fichier :** `backend/src/test/java/ch/unige/events/service/EventServiceMock.java`

Chercher le mock existant (créé pour SCRUM-83 / 107). Mettre à jour :

- Le champ et le getter/setter `faculty` sur les events seedés (si présent) : remplacer `List<Faculty>` / `faculties` par un champ unique `Faculty faculty` nullable.
- La signature de `getAll()` : dernier paramètre `Faculty faculty` (et non `List<Faculty>`).
- Le filtre in-memory :
  ```java
  .filter(e -> faculty == null || e.faculty == faculty)
  ```
- Les méthodes `create` / `update` : `event.faculty = request.faculty;`
- Les mappings vers `EventDTO` : utiliser `EventDTO.from(event, <count>)` en passant `0L` en l'absence d'attendances (cohérent avec le mock SCRUM-83/107 qui n'instancie pas d'attendances).

### 9.2 — `EventSearchServiceMock.java`

**Fichier :** `backend/src/test/java/ch/unige/events/service/EventSearchServiceMock.java`

Mêmes ajustements :

- Signature de `search()` : `String q, EventCategory category, Faculty faculty, LocalDate dateFrom, LocalDate dateTo, int page, int size`
- Filtre :
  ```java
  .filter(e -> faculty == null || e.faculty == faculty)
  ```
- Mapping `EventDTO.from(e, 0L)` (ou compteur si le mock le supporte déjà).

---

## Étape 10 — Tests backend (à ajouter/ajuster)

### 10.1 — `EventDTOTest.java` (existant — ajouter 2 cas)

| Test | Assertion |
|---|---|
| `from_withFaculty_mapsFaculty` | `EventDTO.from(event, 0L).faculty() == Faculty.SCIENCES` quand `event.faculty = SCIENCES` |
| `from_withNullFaculty_returnsNullFaculty` | `EventDTO.from(event, 0L).faculty() == null` quand `event.faculty = null` |

### 10.2 — `EventResourceTest.java` (ajouts)

| Test | Scénario | HTTP |
|---|---|---|
| `getAll_withFacultyFilter_returnsFiltered` | Seed 2 events (SCIENCES, LETTRES) → `?faculty=SCIENCES` retourne 1 | 200 |
| `getAll_withFacultyFilter_noMatch_returnsEmpty` | Seed 1 event (SCIENCES) → `?faculty=DROIT` retourne 0 | 200 |
| `getAll_withNoFacultyFilter_returnsAll` | Seed events mixés → aucun param → tous retournés | 200 |
| `create_withFaculty_returnsFacultyInResponse` | POST avec `faculty=SCIENCES` → réponse contient `faculty: "SCIENCES"` | 201 |
| `create_withoutFaculty_returnsNullFaculty` | POST sans `faculty` → réponse contient `faculty: null` | 201 |
| `update_withFaculty_returnsUpdatedFaculty` | PUT avec `faculty=MEDECINE` → réponse contient `faculty: "MEDECINE"` | 200 |

### 10.3 — `EventSearchResourceTest.java` (ajouts)

| Test | Scénario | HTTP |
|---|---|---|
| `search_withFaculty_returnsFiltered` | Seed 2 events (SCIENCES, LETTRES) → `?faculty=SCIENCES` retourne 1 | 200 |
| `search_withFacultyNoMatch_returnsEmpty` | Seed 1 event (SCIENCES) → `?faculty=DROIT` retourne `[]` | 200 |
| `search_withFacultyAndQ_combined` | Seed 2 events (même titre, facultés différentes) → `?q=java&faculty=SCIENCES` retourne 1 | 200 |
| `search_withFacultyAndCategory_combined` | Seed events → `?category=CONFERENCE&faculty=SCIENCES` retourne intersection | 200 |

### 10.4 — `EventServiceCoverageTest.java` (ajouts — tests d'intégration DB)

| Test | Scénario |
|---|---|
| `getAll_withFacultyFilter_returnsMatchingEvents` | Persiste 2 events (SCIENCES, LETTRES) → filtre SCIENCES retourne 1 |
| `create_withFaculty_persistsFaculty` | Crée event avec `faculty=MEDECINE` → entité en DB a `faculty=MEDECINE` |
| `update_withFaculty_updatesFaculty` | Met à jour faculty de SCIENCES à LETTRES → entité en DB mise à jour |
| `update_withNullFaculty_clearsFaculty` | Event avec faculty → update sans faculty → faculty devient null |
| `getAll_withNullFaculty_returnsAll` | Events avec et sans faculty → sans filtre retourne tous |

### 10.5 — `EventSearchServiceCoverageTest.java` (ajouts)

| Test | Scénario |
|---|---|
| `search_withFacultyFilter_returnsMatchingEvents` | Persiste 2 events (SCIENCES, DROIT) → filtre SCIENCES retourne 1 |
| `search_withFacultyAndCategory_combined` | Faculty + category combinés → intersection correcte |
| `search_withNullFaculty_returnsAll` | Filtre null → tous les events retournés |

### 10.6 — Tests existants à mettre à jour

Parcourir `backend/src/test/java/ch/unige/events/**/*Test*.java` pour remplacer toute référence à :
- `faculties` (champ) → `faculty`
- `List<Faculty>` (type) → `Faculty`
- `event.faculties = List.of(Faculty.X)` → `event.faculty = Faculty.X`
- `getAll(..., List.of(...))` / `search(..., List.of(...))` → `getAll(..., Faculty.X)` / `search(..., Faculty.X)`
- `?faculties=` → `?faculty=`

---

## Étape 11 — Frontend : ajuster le type `Event`

**Fichier :** `frontend/src/types/event.ts`

Passer `faculty` en optionnel :

```ts
export type Event = {
  id: number
  title: string
  description?: string
  location: string
  startDate: string
  endDate: string
  category: EventCategory
  faculty?: Faculty | null
  bannerUrl?: string
  creatorId: string
  status: EventStatus
  capacity?: number
  attendingCount: number
  createdAt: string
  updatedAt?: string
}
```

**Justification** : aligne les mocks de test qui omettent `faculty` et reflète que le champ est optionnel côté API. Les rendus conditionnels `event.faculty != null` restent valides (ils couvrent `null` **et** `undefined`).

---

## Étape 12 — Frontend : corriger les tests cassés

### 12.1 — `src/__tests__/components/event/IcsExportButton.test.tsx`

**Aucune modification du `mockEvent`** : l'étape 11 rend `faculty` optionnel, donc le mock sans la propriété compile.

### 12.2 — `src/__tests__/utils/icsGenerator.test.ts`

Idem, aucun changement requis — le passage en optionnel suffit.

---

## Étape 13 — Frontend : déplacer le `FacultyBadge` sous le titre dans `EventCard`

**Fichier :** `frontend/src/components/event/EventCard.tsx`

Le titre est superposé en bas de la bannière. « Sous le titre » se traduit par : **premier élément du bloc `content`**, avant le groupe date/lieu/capacité.

Remplacer la section `{/* Content */}` (lignes 43-74) :

```tsx
        {/* Content */}
        <div className="p-5 flex flex-col gap-3">
          {event.faculty != null && (
            <FacultyBadge faculty={event.faculty} />
          )}

          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2.5 text-sm text-foreground/55">
              <Calendar className="w-4 h-4 shrink-0" style={{ color: category.color }} />
              <span className="font-medium">{formatEventDateTimeCompact(event.startDate)}</span>
            </div>
            <div className="flex items-center gap-2.5 text-sm text-foreground/55">
              <MapPin className="w-4 h-4 shrink-0" style={{ color: category.color }} />
              <span className="line-clamp-1">{event.location}</span>
            </div>
            {event.capacity != null && (
              <div className="flex items-center gap-2.5 text-sm text-foreground/55">
                <Users className="w-4 h-4 shrink-0" style={{ color: category.color }} />
                <span>{event.capacity} places</span>
              </div>
            )}
          </div>

          {event.description && (
            <>
              <div className="border-t border-border" />
              <p className="text-sm text-foreground/45 line-clamp-2 leading-relaxed">
                {event.description}
              </p>
            </>
          )}
        </div>
```

Conserver `event.faculty != null` (couvre `null` et `undefined`).

---

## Étape 14 — Frontend : vérifier formulaire create/edit

**Fichier :** `frontend/src/components/event/EventForm.tsx`

Le champ faculty est déjà présent ([EventForm.tsx:231-242](../../frontend/src/components/event/EventForm.tsx#L231-L242)). Vérifier :

1. **Placeholder `"Aucune faculté"`** — actuellement `"Toutes les facultés"`. Le texte actuel est un reliquat du filtre multi-valeur. Sémantiquement, sur un formulaire de création d'event, c'est « pas de faculté rattachée ». **Remplacer** :

   ```tsx
   <option value="">Aucune faculté</option>
   ```

2. **Alignement dimensionnel avec le champ « Catégorie »** : comme le commit `bafc623 align CategorySelect dimensions with Capacité input`, s'assurer que le `Select` faculté utilise le même composant `Select` que « Catégorie ». C'est déjà le cas (les deux passent par `FormField` + `Select` de `@/components/utils/FormField`). Aucun style spécifique à ajouter.

3. **Non-obligatoire** : pas de `required` sur `FormField`, pas de validation dans `useEventForm.validate()` → OK, déjà correct.

**Fichier :** `frontend/src/hooks/useEventForm.ts`

Déjà correct (`faculty` dans values, defaults, `toFormValues`, `payload`). Rien à changer — sauf à vérifier que `payload.faculty = values.faculty` passe bien `null` et non `undefined` quand aucune faculté n'est sélectionnée (Axios sérialise `null` dans le JSON — comportement attendu par l'API).

**Vérifications dans** `EventCreatePage.tsx` et `EventEditPage.tsx` : ces pages ne font qu'orchestrer `useEventForm` + `EventForm` — aucune modification nécessaire (les props passent à travers).

---

## Étape 15 — Frontend : filtre `?faculty=` sur le listing / search

### 15.1 — `EventSearchSidebar.tsx`

Déjà en sélection unique + optionnelle via toggle on/off ([EventSearchSidebar.tsx:61-79](../../frontend/src/components/event/EventSearchSidebar.tsx#L61-L79)). **Rien à changer.**

### 15.2 — `useEventSearch.ts`

Déjà en `filters.faculty` singulier, déjà sync dans l'URL sous `?faculty=` ([useEventSearch.ts:29,57,101](../../frontend/src/hooks/useEventSearch.ts#L29)). **Rien à changer.**

### 15.3 — `searchApi.ts`

Déjà en `SearchParams.faculty?: Faculty`. Axios sérialisera `faculty=SCIENCES` automatiquement. **Rien à changer.**

### 15.4 — Homepage (`useEvents.ts` + `EventsPage.tsx`)

La page d'accueil (`GET /api/events`) ne propose pas de filtre faculty aujourd'hui. La tâche JIRA demande le support **backend** sur cet endpoint, ce qui sera fait en étape 7 ci-dessus. Côté frontend, **ne pas ajouter** de filtre faculty à la homepage — l'expérience de filtrage passe par `EventsSearchPage` via `EventSearchSidebar`. Laisser `useEvents.ts` et `EventsPage.tsx` inchangés.

---

## Étape 16 — Tests frontend (à ajouter)

### 16.1 — `EventCard.test.tsx` (existant ou à créer)

| Test | Scénario |
|---|---|
| `renders_withoutFaculty_doesNotRenderBadge` | `event.faculty` omis → aucun `FacultyBadge` dans le DOM |
| `renders_withFaculty_rendersBadgeBelowTitle` | `event.faculty = 'SCIENCES'` → badge présent, label « Sciences » visible, positionné avant le bloc date/lieu dans le DOM |

### 16.2 — `EventForm.test.tsx` (existant ou à créer)

| Test | Scénario |
|---|---|
| `facultySelect_defaultsToEmpty` | Mode create → select a la valeur `""` (aucune faculté) |
| `facultySelect_onChange_updatesFormValues` | Sélectionner LETTRES → `onFieldChange('faculty', 'LETTRES')` appelé |
| `submit_withoutFaculty_sendsNullFaculty` | Soumettre sans faculté → `createEvent` appelé avec `faculty: null` |
| `submit_withFaculty_sendsSelectedFaculty` | Sélectionner MEDECINE → `createEvent` appelé avec `faculty: 'MEDECINE'` |
| `edit_prefillsFacultyFromEvent` | Mode edit, event avec `faculty='DROIT'` → select initialisé sur `"DROIT"` |

### 16.3 — `FacultyBadge.test.tsx` (existant — ajuster si nécessaire)

Le commit `b976cd9 Update FacultyBadge tests for new color palette` a déjà mis à jour les tests. Vérifier qu'aucun de ces tests ne repose sur un type `Event` avec `faculties: []` — sinon remplacer par `faculty: null`.

---

## Étape 17 — Documentation

### 17.1 — `backend/docs/sprint-context.md`

Remplacer la ligne SCRUM-77 par :

```markdown
- [x] Ajout du champ `faculty` (enum, nullable) sur `Event` + filtre `?faculty=` sur `GET /events` et `GET /events/search` (SCRUM-77)
```

### 17.2 — `backend/docs/api-contract.md`

Mettre à jour la ligne de `GET /events` pour lister `faculty` parmi les query params :

```markdown
| `GET` | `/events` | `@PermitAll` | Liste paginée (page, size, status, category, organizerId, endDateFrom, faculty) | 200 |
```

Idem pour `GET /events/search`.

### 17.3 — `backend/docs/data-model.md`

Ajouter `faculty` (Faculty, nullable, indexée) dans la section « Entity Event » et supprimer toute mention obsolète de `event_faculties` / `List<Faculty> faculties`.

### 17.4 — `frontend/docs/components.md` / `types.md`

Documenter la présence du champ `faculty?: Faculty | null` sur `Event` et l'emplacement du `FacultyBadge` dans `EventCard` (sous le titre, premier élément du bloc content).

---

## Récapitulatif des fichiers à créer/modifier

| Fichier | Action |
|---|---|
| `/workspace/openapi/openapi.yaml` | **Modifier** — schémas Event/CreateEventRequest/UpdateEventRequest + params GET `/events` et `/events/search` → `faculty` singulier |
| `backend/src/main/java/.../entity/Event.java` | **Modifier** — `Faculty faculty` + index `idx_event_faculty`, suppression `List<Faculty>` / `@ElementCollection` |
| `backend/src/main/java/.../dto/event/EventDTO.java` | **Modifier** — champ `Faculty faculty` unique, imports nettoyés |
| `backend/src/main/java/.../dto/event/EventRequestBase.java` | **Modifier** — champ `Faculty faculty` unique |
| `backend/src/main/java/.../service/EventService.java` | **Modifier** — `getAll` signature + filtre `e.faculty`, `create/update/publish/uploadImage/getById` : attendingCount + `EventDTO.from(event, count)`, `saveImage(fileUpload, "events/banners")`, injection `EntityManager` |
| `backend/src/main/java/.../service/EventSearchService.java` | **Modifier** — signature `Faculty faculty`, filtre `e.faculty`, attendingCount, injection `EntityManager` |
| `backend/src/main/java/.../resource/EventResource.java` | **Modifier** — `@QueryParam("faculty") Faculty faculty` |
| `backend/src/main/java/.../resource/EventSearchResource.java` | **Modifier** — `@QueryParam("faculty") Faculty faculty` |
| `backend/src/test/java/.../service/EventServiceMock.java` | **Modifier** — champ `faculty` unique, signature `getAll`, filtre |
| `backend/src/test/java/.../service/EventSearchServiceMock.java` | **Modifier** — champ `faculty` unique, signature `search`, filtre |
| `backend/src/test/java/.../dto/event/EventDTOTest.java` | **Modifier** — tests `from` avec/sans faculty |
| `backend/src/test/java/.../resource/EventResourceTest.java` | **Modifier** — 6 cas faculty |
| `backend/src/test/java/.../resource/EventSearchResourceTest.java` | **Modifier** — 4 cas faculty |
| `backend/src/test/java/.../service/EventServiceCoverageTest.java` | **Modifier** — 5 cas DB faculty |
| `backend/src/test/java/.../service/EventSearchServiceCoverageTest.java` | **Modifier** — 3 cas DB faculty |
| Tous les autres tests backend | **Vérifier/ajuster** — remplacer `faculties` → `faculty`, `List<Faculty>` → `Faculty` |
| `frontend/src/types/event.ts` | **Modifier** — `faculty?: Faculty \| null` optionnel |
| `frontend/src/components/event/EventCard.tsx` | **Modifier** — déplacer `FacultyBadge` en 1er élément du bloc content |
| `frontend/src/components/event/EventForm.tsx` | **Modifier** — placeholder « Aucune faculté » |
| `frontend/src/__tests__/components/event/EventCard.test.tsx` | **Créer ou modifier** — 2 cas faculty |
| `frontend/src/__tests__/components/event/EventForm.test.tsx` | **Créer ou modifier** — 5 cas faculty |
| `frontend/src/__tests__/components/event/IcsExportButton.test.tsx` | **Aucune modif** (rendu optionnel par étape 11) |
| `frontend/src/__tests__/utils/icsGenerator.test.ts` | **Aucune modif** (idem) |
| `backend/docs/sprint-context.md` | **Modifier** — cocher SCRUM-77 |
| `backend/docs/api-contract.md` | **Modifier** — params faculty sur `/events` et `/events/search` |
| `backend/docs/data-model.md` | **Modifier** — champ `faculty` sur Event |
| `frontend/docs/components.md` / `types.md` | **Modifier** — documenter `faculty` et `FacultyBadge` sous le titre |
| `specs_archives/specs_claude/specs_s3-faculty-filter.md` | **Créer** — ce document |

---

## Règles critiques à respecter

| Règle | Détail |
|---|---|
| `openapi.yaml` EN PREMIER | Modifier le contrat avant toute ligne de code Java ou TS |
| `faculty` unique et nullable partout | Jamais de `List<Faculty>` / `faculties` dans aucun fichier |
| Pas de migration SQL | Hibernate mode `update` — la colonne est ajoutée au boot ; la table de jointure obsolète `event_faculties` sera ignorée (nettoyable localement en dev) |
| `@Transactional` sur les mutations du Service | Déjà en place — ne pas retirer |
| Constructor injection | `@Inject` sur le constructeur uniquement pour les Resources ; pour les Services, `@Inject` sur champ comme déjà en place pour `EntityManager` / `FileStorageService` |
| Parenthèses autour du `OR` dans JPQL | Conserver `(lower(e.title) like :q or lower(e.description) like :q)` |
| Europe/Zurich → UTC | Conserver la conversion `dateFrom/dateTo` dans `EventSearchService` |
| `attendingCount` réel | Jamais `0L` pour `getAll`/`search`/`getById`/`update`/`publish`/`uploadImage` — seul `create` a le droit au `0L` littéral |
| camelCase partout | Java, JSON, TS, params de requête — jamais de snake_case |
| Pas de ternaires imbriqués côté React | Const maps et conditionnels simples uniquement |
| Labels FR | Libellés dans `FACULTIES[id].name` / `FACULTIES[id].abbr` (`@/types/faculty`) — `FACULTY_LABELS` supprimé |
| Rendu conditionnel `event.faculty != null` | Couvre `null` **et** `undefined` (optionnel côté TS) — ne jamais utiliser `!== null` strict |
| 200 + `[]` si aucun résultat | Jamais de 404 sur filtres qui ne matchent rien |
| Test mocks cohérents | Les mocks qui simulent des events en mémoire doivent utiliser `faculty` unique |

---

## Checklist Sonar

- [ ] `> 80 %` couverture sur les lignes nouvelles/modifiées (JaCoCo backend, Vitest frontend)
- [ ] Duplication `< 3 %` sur le code nouveau
- [ ] Security Rating : A
- [ ] Reliability Rating : A
- [ ] Maintainability Rating : A
- [ ] Build backend `mvn -q -pl backend verify` passe (0 erreur de compilation)
- [ ] Build frontend `npm -C frontend run build` passe (0 erreur TS)
- [ ] Linter frontend `npm -C frontend run lint` passe
- [ ] Tous les tests backend `EventResourceTest`, `EventSearchResourceTest`, `EventDTOTest`, `EventServiceCoverageTest`, `EventSearchServiceCoverageTest` verts
- [ ] Tous les tests frontend `EventCard.test.tsx`, `EventForm.test.tsx`, `IcsExportButton.test.tsx`, `icsGenerator.test.ts` verts

---

## Ordre d'implémentation

1. `openapi.yaml` — contrat API
2. `Faculty.java` (vérification) — aucune modif
3. `Event.java` — champ `faculty` unique + index, suppression `List<Faculty>`
4. `EventDTO.java` — champ `Faculty faculty`
5. `EventRequestBase.java` — champ `Faculty faculty`
6. `EventService.java` — `getAll` + filtre + attendingCounts, fix `create/update/getById/publish/uploadImage`, `saveImage(..., "events/banners")`, injection `EntityManager`
7. `EventSearchService.java` — `search` + filtre + attendingCounts
8. `EventResource.java` — `@QueryParam("faculty")`
9. `EventSearchResource.java` — `@QueryParam("faculty")`
10. Mocks `EventServiceMock` / `EventSearchServiceMock`
11. Tests backend (DTO, Resource, Service coverage)
12. `frontend/src/types/event.ts` — `faculty?` optionnel
13. `frontend/src/components/event/EventCard.tsx` — déplacer `FacultyBadge`
14. `frontend/src/components/event/EventForm.tsx` — placeholder « Aucune faculté »
15. Tests frontend (EventCard, EventForm)
16. Documentation (`backend/docs/*`, `frontend/docs/*`)
17. Build full backend + frontend + tests — vérifier 0 erreur

---

## Prompt de lancement d'implémentation

Pour lancer l'exécution de cette spec, me renvoyer **exactement** le prompt suivant :

```
Implémente la spec specs_archives/specs_claude/specs_s3-faculty-filter.md de bout en bout
sur la branche feature/s3-faculty-filter. Suis strictement l'ordre d'implémentation défini
dans la section « Ordre d'implémentation », en respectant toutes les « Règles critiques ».

Contraintes :
- openapi.yaml d'abord, avant toute ligne de code Java ou TS.
- faculty unique et nullable partout (jamais de List<Faculty>).
- Aucune migration SQL — Hibernate mode update.
- attendingCount réel via Attendance.countGroupedByStatus / Attendance.count
  sauf dans create() où 0L littéral est correct.
- saveImage(fileUpload, "events/banners") dans EventService.uploadImage.
- faculty?: Faculty | null (optionnel) côté type TypeScript Event.
- FacultyBadge doit apparaître en premier élément du bloc content de EventCard,
  visuellement sous le titre.
- Placeholder « Aucune faculté » dans EventForm.
- Conserver le rendu conditionnel event.faculty != null (couvre null ET undefined).

À la fin :
1. Lancer le build backend (mvn verify sur le module backend) et corriger toute erreur
   jusqu'à ce qu'il passe.
2. Lancer le build + lint + tests frontend (npm run build && npm run lint && npm run test
   dans frontend/) et corriger toute erreur jusqu'à ce qu'ils passent.
3. Vérifier que tous les tests backend et frontend listés dans la section
   « Tests backend » et « Tests frontend (à ajouter) » de la spec existent et passent.
4. Mettre à jour la documentation (backend/docs/sprint-context.md, api-contract.md,
   data-model.md, frontend/docs/components.md, types.md) comme décrit à l'étape 17.
5. Ne rien commiter tant que je n'ai pas donné le feu vert — me faire un récap final
   des fichiers modifiés avec un court résumé des changements par fichier.

Si tu rencontres une ambiguïté non couverte par la spec, relis d'abord le code existant
et cite-le avant de trancher. Ne jamais inventer de nom de méthode ou de signature.
```
