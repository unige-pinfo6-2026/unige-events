# Specs — Suppression du statut `INTERESTED` & Calendrier via Favoris

> **Sprint :** 4 — Correctif backend · Epic 4 – Engagement & Interaction
> **Règle d'or :** Modifier `openapi/openapi.yaml` EN PREMIER, puis coder Entity → DTO → Service → Tests → Docs. Ne pas toucher au frontend.

---

## Contexte

### Motivation fonctionnelle

Le système d'attendance comporte deux statuts : `INTERESTED` et `ATTENDING`. En parallèle, le système de favoris (`Favorite`) remplit déjà le rôle de "je note cet événement sans m'y inscrire". Le statut `INTERESTED` est donc redondant avec les favoris et crée de la confusion côté UX.

**Décision :** supprimer `INTERESTED` de l'attendance. L'attendance ne connaît plus qu'un seul statut : `ATTENDING` (inscrit).

En conséquence, le flux ICS/webcal (`CalendarService.generateIcsFeed`) — qui inclut actuellement les événements `INTERESTED` **et** `ATTENDING` — doit basculer vers :
- les événements auxquels l'utilisateur est **`ATTENDING`** (inscription formelle)
- les événements que l'utilisateur a mis en **favori** (`Favorite`)

Union des deux sources, dédupliquée.

---

## Inventaire complet des changements

### Code de production

| Fichier | Type | Action |
|---|---|---|
| `entity/AttendanceStatus.java` | Enum | Supprimer `INTERESTED` |
| `entity/Favorite.java` | Entité | Ajouter `findAllByUser(UUID)` (non paginé) |
| `service/AttendanceService.java` | Service | Simplifier la garde de capacité |
| `service/EventService.java` | Service | Supprimer `countInterested()` + adapter `EventDTO.from()` |
| `service/EventSearchService.java` | Service | Supprimer le bulk-count INTERESTED + adapter `EventDTO.from()` |
| `service/CalendarService.java` | Service | Remplacer Attendance → Favorites + ATTENDING |
| `dto/event/EventDTO.java` | DTO | Supprimer champ `interestedCount`, adapter `from()` |
| `openapi/openapi.yaml` | Contrat | Supprimer `interestedCount`, `INTERESTED` de l'enum |

### Tests

| Fichier | Action |
|---|---|
| `service/AttendanceServiceCoverageTest.java` | Supprimer 4 tests, adapter 4 autres |
| `resource/AttendanceResourceTest.java` | Supprimer 1 test |
| `service/CalendarServiceCoverageTest.java` | Adapter 4 tests (Attendance → Favorite) |
| `service/ServiceCoverageTestHelper.java` | Ajouter `persistFavorite` |
| `service/EventSearchServiceCoverageTest.java` | Supprimer 1 test, adapter 1 autre |
| `dto/EventDTOTest.java` | Adapter signature `EventDTO.from()` |

### Docs

| Fichier | Action |
|---|---|
| `docs/api-contract.md` | Retirer mentions `INTERESTED` |
| `docs/sprint-context.md` | Mettre à jour |

---

## Étape 0 — `openapi/openapi.yaml` (OBLIGATOIRE EN PREMIER)

### 0.1 — Enum `AttendanceStatus`

Localiser le schéma `AttendanceStatus` et remplacer :

```yaml
# AVANT
AttendanceStatus:
  type: string
  enum: [INTERESTED, ATTENDING]

# APRÈS
AttendanceStatus:
  type: string
  enum: [ATTENDING]
```

### 0.2 — Schéma `EventDTO` : supprimer `interestedCount`

Localiser le schéma `EventDTO` dans `components/schemas` et supprimer le champ `interestedCount` :

```yaml
# SUPPRIMER ces lignes
        interestedCount:
          type: integer
          format: int64
          description: Nombre d'utilisateurs inscrits avec le statut INTERESTED
```

### 0.3 — Descriptions mentionnant `INTERESTED`

Rechercher toutes les occurrences de `INTERESTED` dans le fichier et mettre à jour les descriptions :

- Path `POST /events/{id}/attend` : remplacer `"INTERESTED ou ATTENDING"` par `"ATTENDING"`
- Path `GET /users/me/attendances` : retirer la mention de `INTERESTED`
- Path `GET /calendar/{calendarToken}.ics` : la description indique que le flux contient les événements où l'utilisateur s'est inscrit (`INTERESTED` ou `ATTENDING`) — remplacer par : les événements favoris **et** les événements `ATTENDING`

---

## Étape 1 — `AttendanceStatus.java`

**Fichier :** `backend/src/main/java/ch/unige/events/entity/AttendanceStatus.java`

```java
// AVANT
public enum AttendanceStatus {
    INTERESTED,
    ATTENDING
}

// APRÈS
public enum AttendanceStatus {
    ATTENDING
}
```

---

## Étape 2 — `Favorite.java` : ajouter `findAllByUser`

**Fichier :** `backend/src/main/java/ch/unige/events/entity/Favorite.java`

Ajouter après la méthode `findByUser` (paginée) :

```java
public static List<Favorite> findAllByUser(UUID userId) {
    return list("userId = ?1", userId);
}
```

`List` est déjà importé dans le fichier. Ce helper retourne **tous** les favoris sans pagination — nécessaire pour le flux ICS complet.

---

## Étape 3 — `AttendanceService.java` : simplifier la garde de capacité

**Fichier :** `backend/src/main/java/ch/unige/events/service/AttendanceService.java`

Le bloc de vérification de capacité est actuellement conditionné par `status == AttendanceStatus.ATTENDING`. Avec la suppression de `INTERESTED`, cette condition est toujours vraie — elle peut être retirée pour simplifier :

```java
// AVANT
if (status == AttendanceStatus.ATTENDING && event.capacity != null) {
    boolean alreadyAttending = Attendance.<Attendance>find(
            "userId = ?1 and eventId = ?2 and status = ?3",
            userId, eventId, AttendanceStatus.ATTENDING)
            .firstResultOptional()
            .isPresent();
    if (!alreadyAttending) {
        long currentAttending = Attendance.count(
                "eventId = ?1 and status = ?2", eventId, AttendanceStatus.ATTENDING);
        if (currentAttending >= event.capacity) {
            throw new WebApplicationException(...);
        }
    }
}

// APRÈS
if (event.capacity != null) {
    boolean alreadyAttending = Attendance.<Attendance>find(
            "userId = ?1 and eventId = ?2",
            userId, eventId)
            .firstResultOptional()
            .isPresent();
    if (!alreadyAttending) {
        long currentAttending = Attendance.count("eventId = ?1", eventId);
        if (currentAttending >= event.capacity) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(new ApiErrorResponse(
                                    "conflict", "Event has reached maximum capacity"))
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build());
        }
    }
}
```

**Note :** la requête de comptage n'a plus besoin de filtrer sur `status` — toutes les `Attendance` sont désormais `ATTENDING` par définition.

Supprimer également l'import devenu inutile :
```java
// SUPPRIMER si plus utilisé ailleurs dans le fichier
import ch.unige.events.entity.AttendanceStatus;
```

---

## Étape 4 — `EventDTO.java` : supprimer `interestedCount`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/event/EventDTO.java`

```java
// AVANT
public record EventDTO(
        Long id,
        String title,
        // ... autres champs ...
        long attendingCount,
        long interestedCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EventDTO from(Event event, long attendingCount, long interestedCount) {
        return new EventDTO(
                event.id,
                // ... autres champs ...
                attendingCount,
                interestedCount,
                event.createdAt,
                event.updatedAt
        );
    }
}

// APRÈS
public record EventDTO(
        Long id,
        String title,
        // ... autres champs ...
        long attendingCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EventDTO from(Event event, long attendingCount) {
        return new EventDTO(
                event.id,
                // ... autres champs ...
                attendingCount,
                event.createdAt,
                event.updatedAt
        );
    }
}
```

---

## Étape 5 — `EventService.java` : supprimer `countInterested`

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventService.java`

### 5.1 — Méthode `getAll` : supprimer le bulk-count INTERESTED

```java
// AVANT
List<Long> ids = events.stream().map(e -> e.id).toList();
Map<Long, Long> attendingCounts = bulkCountByStatus(ids, AttendanceStatus.ATTENDING);
Map<Long, Long> interestedCounts = bulkCountByStatus(ids, AttendanceStatus.INTERESTED);
return events.stream()
        .map(e -> EventDTO.from(e,
                attendingCounts.getOrDefault(e.id, 0L),
                interestedCounts.getOrDefault(e.id, 0L)))
        .toList();

// APRÈS
List<Long> ids = events.stream().map(e -> e.id).toList();
Map<Long, Long> attendingCounts = bulkCountByStatus(ids, AttendanceStatus.ATTENDING);
return events.stream()
        .map(e -> EventDTO.from(e, attendingCounts.getOrDefault(e.id, 0L)))
        .toList();
```

### 5.2 — Toutes les autres occurrences de `EventDTO.from(event, countAttending(id), countInterested(id))`

Remplacer par `EventDTO.from(event, countAttending(id))` dans les méthodes :
- `getById`
- `update`
- `publish`
- `uploadImage`

Et les occurrences retournant directement `0L` :
- `create` : `EventDTO.from(event, 0L, 0L)` → `EventDTO.from(event, 0L)`

### 5.3 — Supprimer la méthode `countInterested`

```java
// SUPPRIMER entièrement
private long countInterested(Long eventId) {
    return Attendance.count("eventId = ?1 and status = ?2", eventId, AttendanceStatus.INTERESTED);
}
```

### 5.4 — Supprimer l'import `AttendanceStatus` si plus utilisé

Vérifier si `AttendanceStatus` est encore utilisé dans ce fichier (via `bulkCountByStatus(ids, AttendanceStatus.ATTENDING)` et `countAttending`). Si oui, conserver l'import. Sinon, supprimer.

---

## Étape 6 — `EventSearchService.java` : même adaptation

**Fichier :** `backend/src/main/java/ch/unige/events/service/EventSearchService.java`

Localiser le bloc similaire à `EventService.getAll` et appliquer la même modification :

```java
// AVANT
Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(ids, AttendanceStatus.ATTENDING, entityManager);
Map<Long, Long> interestedCounts = Attendance.countGroupedByStatus(ids, AttendanceStatus.INTERESTED, entityManager);
return events.stream()
        .map(e -> EventDTO.from(e,
                attendingCounts.getOrDefault(e.id, 0L),
                interestedCounts.getOrDefault(e.id, 0L)))
        .toList();

// APRÈS
Map<Long, Long> attendingCounts = Attendance.countGroupedByStatus(ids, AttendanceStatus.ATTENDING, entityManager);
return events.stream()
        .map(e -> EventDTO.from(e, attendingCounts.getOrDefault(e.id, 0L)))
        .toList();
```

---

## Étape 7 — `CalendarService.java` : basculer vers Favoris + ATTENDING

**Fichier :** `backend/src/main/java/ch/unige/events/service/CalendarService.java`

### 7.1 — Supprimer les imports devenus inutiles

```java
// SUPPRIMER
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
```

### 7.2 — Ajouter les imports nécessaires

```java
import ch.unige.events.entity.Favorite;
import java.util.HashSet;
import java.util.Set;
```

### 7.3 — Réécrire `generateIcsFeed`

```java
@Transactional
public String generateIcsFeed(UUID calendarToken) {
    User user = User.<User>find("calendarToken", calendarToken)
            .firstResultOptional()
            .orElseThrow(() -> new NotFoundException("Calendar token not found"));

    // Événements favoris (PUBLISHED)
    Set<Long> eventIds = new HashSet<>();
    List<Event> events = new java.util.ArrayList<>();

    Favorite.findAllByUser(user.id).stream()
            .map(f -> Event.<Event>findByIdOptional(f.eventId))
            .flatMap(Optional::stream)
            .filter(e -> e.status == EventStatus.PUBLISHED)
            .forEach(e -> {
                if (eventIds.add(e.id)) events.add(e);
            });

    // Événements auxquels l'utilisateur est ATTENDING (PUBLISHED, dédupliqués)
    Attendance.findAllByUser(user.id).stream()
            .map(a -> Event.<Event>findByIdOptional(a.eventId))
            .flatMap(Optional::stream)
            .filter(e -> e.status == EventStatus.PUBLISHED)
            .forEach(e -> {
                if (eventIds.add(e.id)) events.add(e);
            });

    return IcsBuilder.buildIcsContent(events, appConfig.frontendUrl());
}
```

**Points d'attention :**
- `Set<Long> eventIds` garantit la déduplication : un événement à la fois favori et `ATTENDING` n'apparaît qu'une fois dans l'ICS.
- `Attendance` reste importé car on utilise encore `Attendance.findAllByUser`.
- L'ordre est arbitraire (favoris d'abord, puis ATTENDING exclusifs) — acceptable pour un flux ICS.

---

## Étape 8 — Tests

### 8.1 — `ServiceCoverageTestHelper.java` : ajouter `persistFavorite`

**Fichier :** `backend/src/test/java/ch/unige/events/service/ServiceCoverageTestHelper.java`

Ajouter après `persistAttendance` :

```java
static Favorite persistFavorite(EntityManager em, UUID userId, Long eventId) {
    Favorite f = new Favorite();
    f.userId = userId;
    f.eventId = eventId;
    em.persist(f);
    em.flush();
    return f;
}
```

Ajouter l'import en tête de fichier :
```java
import ch.unige.events.entity.Favorite;
```

---

### 8.2 — `AttendanceServiceCoverageTest.java`

#### Tests à supprimer entièrement

| Méthode | Raison |
|---|---|
| `attend_capacityReached_interestedAllowed` | INTERESTED n'existe plus — le scénario est impossible |
| `interestedCount_incrementsAfterAttend` | `interestedCount` supprimé du DTO |
| `counts_updateCorrectly_whenStatusSwitches` | Le switch INTERESTED → ATTENDING n'existe plus |

#### Tests à adapter

**`attend_firstTime_createsAttendance`** — remplacer `INTERESTED` par `ATTENDING` :
```java
// AVANT
AttendanceDTO dto = attendanceService.attend("auth0|att1", event.id, AttendanceStatus.INTERESTED);
assertEquals(AttendanceStatus.INTERESTED, dto.status());

// APRÈS
AttendanceDTO dto = attendanceService.attend("auth0|att1", event.id, AttendanceStatus.ATTENDING);
assertEquals(AttendanceStatus.ATTENDING, dto.status());
```

**`attend_secondTime_updatesStatus`** — le test testait INTERESTED → ATTENDING. Adapter en ATTENDING → ATTENDING (vérifie l'idempotence de l'upsert) :
```java
// AVANT
attendanceService.attend("auth0|att2", event.id, AttendanceStatus.INTERESTED);
AttendanceDTO second = attendanceService.attend("auth0|att2", event.id, AttendanceStatus.ATTENDING);
assertEquals(AttendanceStatus.ATTENDING, second.status());

// APRÈS
attendanceService.attend("auth0|att2", event.id, AttendanceStatus.ATTENDING);
AttendanceDTO second = attendanceService.attend("auth0|att2", event.id, AttendanceStatus.ATTENDING);
assertEquals(AttendanceStatus.ATTENDING, second.status());
// La contrainte unique doit toujours n'avoir qu'une seule ligne
long count = Attendance.count("userId = ?1 and eventId = ?2", user.id, event.id);
assertEquals(1, count);
```

**`attend_unknownEvent_throwsNotFound`** — remplacer `INTERESTED` par `ATTENDING` :
```java
// AVANT
assertThrows(NotFoundException.class,
        () -> attendanceService.attend("auth0|att3", 999999L, AttendanceStatus.INTERESTED));

// APRÈS
assertThrows(NotFoundException.class,
        () -> attendanceService.attend("auth0|att3", 999999L, AttendanceStatus.ATTENDING));
```

**`attend_unknownUser_throwsNotFound`** — remplacer `INTERESTED` par `ATTENDING` :
```java
// AVANT
assertThrows(NotFoundException.class,
        () -> attendanceService.attend("auth0|nobody", event.id, AttendanceStatus.INTERESTED));

// APRÈS
assertThrows(NotFoundException.class,
        () -> attendanceService.attend("auth0|nobody", event.id, AttendanceStatus.ATTENDING));
```

**`getMyAttendances_withAttendances_returnsList`** — remplacer `INTERESTED` par `ATTENDING` :
```java
// AVANT
persistAttendance(user.id, event.id, AttendanceStatus.INTERESTED);
// ...
assertEquals(AttendanceStatus.INTERESTED, result.get(0).status());

// APRÈS
persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);
// ...
assertEquals(AttendanceStatus.ATTENDING, result.get(0).status());
```

**`attendingCount_incrementsAfterAttend_andDecrementsAfterUnattend`** — supprimer les assertions sur `interestedCount` :
```java
// AVANT
assertEquals(0, before.attendingCount());
assertEquals(0, before.interestedCount());
// ...
assertEquals(1, afterAttend.attendingCount());
assertEquals(0, afterAttend.interestedCount());
// ...
assertEquals(0, afterUnattend.attendingCount());
assertEquals(0, afterUnattend.interestedCount());

// APRÈS — supprimer toutes les lignes assertEquals(..., ...interestedCount())
assertEquals(0, before.attendingCount());
// ...
assertEquals(1, afterAttend.attendingCount());
// ...
assertEquals(0, afterUnattend.attendingCount());
```

**`counts_multipleUsers_accumulateCorrectly`** — remplacer le troisième utilisateur INTERESTED par ATTENDING :
```java
// AVANT
attendanceService.attend("auth0|mul-u3", event.id, AttendanceStatus.INTERESTED);
// ...
assertEquals(2, dto.attendingCount());
assertEquals(1, dto.interestedCount());

// APRÈS
attendanceService.attend("auth0|mul-u3", event.id, AttendanceStatus.ATTENDING);
// ...
assertEquals(3, dto.attendingCount());
```

---

### 8.3 — `AttendanceResourceTest.java`

#### Test à supprimer entièrement

```java
// SUPPRIMER entièrement
@Test
@TestSecurity(user = "auth0|alice")
void attend_interested_returns200() {
    Event event = attendanceServiceMock.seedEvent("Conférence UNIGE 2");

    given()
            .contentType(ContentType.JSON)
            .body("{\"status\":\"INTERESTED\"}")
            .when().post("/events/{id}/attend", event.id)
            .then()
            .statusCode(200)
            .body("status", equalTo("INTERESTED"));
}
```

**Justification :** `INTERESTED` n'est plus une valeur valide de l'enum. La validation Jakarta Bean Validation sur `@NotNull AttendanceStatus status` dans `AttendanceRequest` retournera désormais un 400 pour un body `{"status":"INTERESTED"}`.

---

### 8.4 — `CalendarServiceCoverageTest.java`

#### Imports à adapter

```java
// SUPPRIMER
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;

// AJOUTER
import ch.unige.events.entity.Favorite;
```

#### Test à supprimer entièrement

```java
// SUPPRIMER entièrement
@Test
@TestTransaction
void generateIcsFeed_interestedStatusIncluded() { ... }
```

**Justification :** INTERESTED n'existe plus. Ce cas est remplacé par le test sur les favoris ci-dessous.

#### Tests à adapter (Attendance → Favorite)

**`generateIcsFeed_withAttendance_containsVevent`** — renommer et adapter :
```java
// AVANT
@Test
@TestTransaction
void generateIcsFeed_withAttendance_containsVevent() {
    User user = persistUser("auth0|cal5", "cal5@example.com");
    user.calendarToken = UUID.randomUUID();
    entityManager.flush();

    Event event = persistEvent("Conférence UNIGE", user, EventStatus.PUBLISHED);
    persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

    String ics = calendarService.generateIcsFeed(user.calendarToken);

    assertTrue(ics.contains("BEGIN:VEVENT"));
    assertTrue(ics.contains("SUMMARY:Conférence UNIGE"));
}

// APRÈS — tester les deux sources : favori et ATTENDING
@Test
@TestTransaction
void generateIcsFeed_withFavorite_containsVevent() {
    User user = persistUser("auth0|cal5", "cal5@example.com");
    user.calendarToken = UUID.randomUUID();
    entityManager.flush();

    Event event = persistEvent("Conférence Favori", user, EventStatus.PUBLISHED);
    persistFavorite(user.id, event.id);

    String ics = calendarService.generateIcsFeed(user.calendarToken);

    assertTrue(ics.contains("BEGIN:VEVENT"));
    assertTrue(ics.contains("SUMMARY:Conférence Favori"));
}

@Test
@TestTransaction
void generateIcsFeed_withAttending_containsVevent() {
    User user = persistUser("auth0|cal5b", "cal5b@example.com");
    user.calendarToken = UUID.randomUUID();
    entityManager.flush();

    Event event = persistEvent("Conférence Attending", user, EventStatus.PUBLISHED);
    persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

    String ics = calendarService.generateIcsFeed(user.calendarToken);

    assertTrue(ics.contains("BEGIN:VEVENT"));
    assertTrue(ics.contains("SUMMARY:Conférence Attending"));
}
```

**`generateIcsFeed_noAttendance_noVevent`** — renommer :
```java
// Renommer en generateIcsFeed_noFavoriteNoAttending_noVevent
// Le corps reste identique (aucune donnée persistée → pas de VEVENT)
@Test
@TestTransaction
void generateIcsFeed_noFavoriteNoAttending_noVevent() {
    User user = persistUser("auth0|cal6", "cal6@example.com");
    user.calendarToken = UUID.randomUUID();
    entityManager.flush();

    String ics = calendarService.generateIcsFeed(user.calendarToken);

    assertTrue(ics.contains("BEGIN:VCALENDAR"));
    assertFalse(ics.contains("BEGIN:VEVENT"));
}
```

**`generateIcsFeed_draftEventExcluded`** — adapter pour utiliser `persistFavorite` :
```java
// AVANT
Event event = persistEvent("Brouillon", user, EventStatus.DRAFT);
persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

// APRÈS — tester l'exclusion via Favorite (le cas ATTENDING est identique)
Event event = persistEvent("Brouillon", user, EventStatus.DRAFT);
persistFavorite(user.id, event.id);
```

#### Ajouter un test de déduplication (favori + ATTENDING sur le même événement)

```java
@Test
@TestTransaction
void generateIcsFeed_favoriteAndAttending_sameEvent_appearsOnce() {
    User user = persistUser("auth0|cal9", "cal9@example.com");
    user.calendarToken = UUID.randomUUID();
    entityManager.flush();

    Event event = persistEvent("Double Source", user, EventStatus.PUBLISHED);
    persistFavorite(user.id, event.id);
    persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

    String ics = calendarService.generateIcsFeed(user.calendarToken);

    // L'événement ne doit apparaître qu'une seule fois
    long count = ics.lines().filter(l -> l.equals("BEGIN:VEVENT")).count();
    assertEquals(1, count);
}
```

#### Adapter le helper `persistAttendance`

Le helper local `persistAttendance` dans `CalendarServiceCoverageTest` appelle `ServiceCoverageTestHelper.persistAttendance` — vérifier qu'il est conservé pour les tests qui en ont encore besoin (`generateIcsFeed_withAttending_containsVevent`, `generateIcsFeed_draftEventExcluded` si adapté).

Ajouter un helper `persistFavorite` :
```java
private Favorite persistFavorite(UUID userId, Long eventId) {
    return ServiceCoverageTestHelper.persistFavorite(entityManager, userId, eventId);
}
```

---

### 8.5 — `EventSearchServiceCoverageTest.java`

#### Test à supprimer entièrement

```java
// SUPPRIMER entièrement
@Test
@TestTransaction
void search_withInterestedAttendance_returnsRealInterestedCount() { ... }
```

**Justification :** `interestedCount` n'existe plus dans `EventDTO`.

#### Test à adapter

**`search_withAttendingAttendance_returnsRealAttendingCount`** — supprimer l'assertion sur `interestedCount` :
```java
// AVANT
assertEquals(1L, result.get(0).attendingCount());
assertEquals(0L, result.get(0).interestedCount());

// APRÈS
assertEquals(1L, result.get(0).attendingCount());
```

---

### 8.6 — `EventDTOTest.java`

Toutes les occurrences de `EventDTO.from(event, 0L, 0L)` → `EventDTO.from(event, 0L)` :

```java
// AVANT
EventDTO dto = EventDTO.from(event, 0L, 0L);

// APRÈS
EventDTO dto = EventDTO.from(event, 0L);
```

S'applique aux deux tests du fichier : `from_withCreator_mapsCreatorIdAsUUID` et `from_withNullCreator_returnsNullCreatorId`.

---

## Étape 9 — Documentation

### `docs/api-contract.md`

- Retirer `interestedCount` de la description du schéma `EventDTO`
- Mettre à jour la description de `POST /events/{id}/attend` : `"statut ATTENDING"` (plus `INTERESTED ou ATTENDING`)
- Mettre à jour la description de `GET /calendar/{calendarToken}.ics` : le flux contient désormais les événements **favoris** et **ATTENDING** (pas plus `INTERESTED`)

### `docs/sprint-context.md`

Ajouter dans la section Sprint 4 :

```markdown
- [x] Suppression du statut `INTERESTED` de l'attendance — correctif backend ✅
  - `AttendanceStatus` : enum réduit à `ATTENDING`
  - `CalendarService.generateIcsFeed` : flux ICS basé sur Favoris ∪ ATTENDING
  - `EventDTO` : champ `interestedCount` supprimé
```

---

## Critères de validation

### Fonctionnels

- `POST /api/events/{id}/attend` avec `{"status":"INTERESTED"}` → **400** (enum invalide)
- `POST /api/events/{id}/attend` avec `{"status":"ATTENDING"}` → **200** (comportement inchangé)
- `GET /api/events/{id}` et `GET /api/events` → réponse JSON sans champ `interestedCount`
- `GET /api/calendar/{token}.ics` → contient `BEGIN:VEVENT` pour les événements favoris de l'utilisateur (PUBLISHED)
- `GET /api/calendar/{token}.ics` → contient `BEGIN:VEVENT` pour les événements ATTENDING (PUBLISHED)
- `GET /api/calendar/{token}.ics` → un événement à la fois favori et ATTENDING n'apparaît **qu'une seule fois**
- `GET /api/calendar/{token}.ics` → les événements DRAFT sont exclus, qu'ils soient en favori ou ATTENDING

### Qualité

- Tous les tests backend passent — aucune référence à `INTERESTED` ou `interestedCount` dans le code de production ou les tests
- SonarCloud : Security Rating A, Reliability A, Maintainability A
- Couverture ≥ 80% sur le code modifié
