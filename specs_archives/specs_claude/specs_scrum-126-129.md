# SCRUM-126 + SCRUM-129 — Champs additionnels `Event` & renforcement capacité / WAITLISTED

**Sprint 5 — Backend uniquement | Tâches groupées en un seul PR atomique**

> **Branche :** `feature/s5-event-extra-fields-capacity`
> **Base de branche :** `origin/feature/s5-my-events-page` (PAS `main` — voir section dédiée)
> **Cible PR :** `main`
> **Règle d'or :** modifier `openapi/openapi.yaml` EN PREMIER, puis coder Resource → Service → Entity → Test
> **Couverture exigée :** 100 % JaCoCo sur les lignes nouvelles (cible Sonar 80 % minimum, mais on vise 100 % sur le code touché comme `specs_scrum-77.md`)

---

## Branche Git — décision déjà prise

Trois branches sont actuellement ouvertes en parallèle, et `main` est en retard sur la réalité du Sprint 5. Analyse :

| Branche | Backend touché | Empile-t-on dessus ? |
|---|---|---|
| `origin/feature/draft` | 0 fichier backend (DraftResumeCard, frontend pur) | Non — orthogonal |
| `origin/feature/s5-user-banner-front` | 1 ligne `openapi.yaml`, 0 Java | Non — orthogonal |
| `origin/feature/s5-my-events-page` | **TOUT le périmètre cible** : `Event.java`, `EventDTO.java`, `EventRequestBase.java`, `CreateEventRequest.java`, `UpdateEventRequest.java`, `EventService.java`, `EventResource.java`, `openapi.yaml`, `EventServiceCoverageTest.java`, `EventServiceMock.java`, `EventResourceTest.java`, `EventDTOTest.java`, `backend/docs/data-model.md` | **Oui — base de la nouvelle branche** |

`feature/s5-my-events-page` apporte SCRUM-124 (`allDay`), le publish flow, cancel/restore et hard-delete. Elle introduit déjà :
- Champ `allDay` sur `Event`
- Factory `EventDTO.from(Event, long attendingCount)` (signature renommée — l'ancienne `from(Event)` n'existe plus)
- Méthode `Attendance.countGroupedByStatus(List<Long>, AttendanceStatus, EntityManager)` — bulk count à réutiliser
- `EventService.getAll()` avec injection `@Inject EntityManager`
- Validation `@Future`, `@NotBlank`, `@Size`, `@Positive` sur `EventRequestBase`
- Validation `collectPublishValidationErrors` dans `publish()`

Repartir de `main` provoquerait des conflits sur 6 fichiers cœur. Repartir de `feature/s5-my-events-page` :
1. Évite ces conflits.
2. Préserve les acquis de SCRUM-124 et du publish flow (sinon régression).
3. Aligne l'ordre de merge naturel — `feature/s5-my-events-page` sera mergée avant `feature/s5-event-extra-fields-capacity`.
4. Si la branche parente est mergée avant nous, un simple `git rebase main` suffit. L'inverse (résoudre 6 conflits non triviaux) serait douloureux.

### Commande exacte

```bash
git fetch origin
git checkout -b feature/s5-event-extra-fields-capacity origin/feature/s5-my-events-page
```

### Cible du PR

Le PR ouvre **vers `main`** (pas vers `feature/s5-my-events-page`). GitHub affichera tous les commits cumulés tant que la branche parente n'est pas mergée — c'est attendu. Une fois `feature/s5-my-events-page` mergée, faire un `git rebase origin/main` puis `git push --force-with-lease` pour nettoyer la diff. **Ne pas rebaser avant cette étape**, sous peine de réintroduire les conflits qu'on a précisément cherché à éviter.

---

## Contexte

### Pourquoi grouper SCRUM-126 et SCRUM-129 en un seul PR

| Critère | SCRUM-126 | SCRUM-129 |
|---|---|---|
| Entité touchée | `Event` | `Event` (lecture) + `Attendance` |
| DTO touché | `EventDTO`, `EventRequestBase` | `EventDTO`, `AttendanceStatus` |
| Service touché | `EventService.create/update`, `AttendanceService.attend` | `AttendanceService.attend/removeAttendance`, `EventService.getById/getAll` |
| `openapi.yaml` | `Event`, `CreateEventRequest`, `UpdateEventRequest` | `Event`, `AttendanceStatus` |
| Tests | `EventResourceTest`, `EventServiceCoverageTest`, `AttendanceServiceCoverageTest` | mêmes |

Les deux tâches modifient **les mêmes 6 fichiers cœur**, **la même méthode `AttendanceService.attend()`** et **la même factory `EventDTO.from()`**. Les livrer séparément doublerait le travail de merge, casserait l'idempotence du PR et imposerait à la deuxième branche de résoudre des conflits sur la première. Les livrer ensemble :
- Garde `EventDTO.from()` modifié une seule fois (ajout simultané de 4 champs SCRUM-126 + 2 compteurs SCRUM-129).
- Permet de tester l'interaction critique « event avec `registrationDeadline` ET `capacity` » dans une même classe de test.
- Offre un PR plus petit en taille de diff que deux PR successifs avec rebase.

Les deux tâches sont indépendantes côté front (SCRUM-127, SCRUM-130) — le couplage est uniquement backend.

### Ce qui existe déjà sur `feature/s5-my-events-page` (à NE PAS retoucher hors scope)

| Fichier | État pertinent |
|---|---|
| `entity/Event.java` | Champs : `title`, `description`, `location`, `startDate`, `endDate`, `category`, `faculty`, `bannerUrl`, `creator`, `status`, `capacity`, `allDay`, `shareCode`, `createdAt`, `updatedAt`. Indexes : `idx_event_creator`, `idx_event_start_date`, `idx_event_faculty`. `@PrePersist` / `@PreUpdate` gèrent les timestamps. |
| `entity/AttendanceStatus.java` | Enum à **une seule valeur** : `ATTENDING`. Pas de `INTERESTED`, pas de `WAITLISTED`. |
| `entity/Attendance.java` | Contrainte unique `(user_id, event_id)`. Champ `createdAt` géré par `@PrePersist`. Helper statique `countGroupedByStatus(List<Long>, AttendanceStatus, EntityManager)` — bulk count à réutiliser pour `waitlistedCount`. |
| `dto/event/EventDTO.java` | Record avec factory `from(Event, long attendingCount)` — **PAS** `from(Event)`. La signature sera étendue. |
| `dto/event/EventRequestBase.java` | Validation Hibernate Validator déjà active (`@NotBlank`, `@NotNull`, `@Future`, `@Size`, `@Positive`). |
| `service/AttendanceService.java` | `attend()` vérifie déjà : event PUBLISHED → idempotence (déjà inscrit) → recompte capacité → `WebApplicationException(CONFLICT)`. Pas de prise en compte de `registrationDeadline`, pas de promotion sur `removeAttendance()`. |
| `service/EventService.java` | `getAll()` fait déjà un bulk count via `Attendance.countGroupedByStatus` puis appelle `EventDTO.from(e, attendingCounts.getOrDefault(e.id, 0L))`. `getById()` utilise un `countAttending()` privé. `create()` et `update()` propagent `faculty`, `bannerUrl`, `capacity`, `allDay` mais pas les nouveaux champs. |
| `openapi/openapi.yaml` | Schémas `Event`, `CreateEventRequest`, `UpdateEventRequest`, `AttendanceStatus`, endpoint `POST /events/{id}/attend` documenté avec `409` pour capacité atteinte. À étendre. |

### Pas de migration SQL

Hibernate est en mode `update` (cf. `backend/AGENTS.md`). Les nouveaux champs et la nouvelle table `event_tags` (issue de `@ElementCollection`) seront créés automatiquement. **Aucun fichier Flyway ne sera produit.**

---

## Décisions techniques (à NE PAS revisiter pendant l'implémentation)

### 1. `tags` — `@ElementCollection(fetch = EAGER)` + `@CollectionTable("event_tags")`

```java
@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(
    name = "event_tags",
    joinColumns = @JoinColumn(name = "event_id"),
    foreignKey = @ForeignKey(name = "fk_event_tags_event")
)
@Column(name = "tag", nullable = false, length = 64)
public List<String> tags = new ArrayList<>();
```

**Pourquoi EAGER** : `tags` est exposé dans `EventDTO` qui est sérialisé pour **tous** les endpoints de lecture (`GET /events`, `GET /events/{id}`, `GET /events/search`). Un fetch LAZY déclencherait soit du N+1 sur `getAll()`, soit une `LazyInitializationException` post-transaction. La cardinalité attendue (~5 tags max par event) rend le surcoût mémoire négligeable. Conforme au choix backlog SCRUM-126.

**Initialisation à `new ArrayList<>()`** : évite les NPE dans `EventDTO.from()` quand un event créé en DB hors flux normal n'a pas de tags.

**Normalisation côté service** : trim + lowercase + dédup, ordre d'insertion préservé (voir section EventService).

### 2. Validation `@URL` (Hibernate Validator) et `@Email` (jakarta)

```java
import org.hibernate.validator.constraints.URL;
import jakarta.validation.constraints.Email;

@URL
@Size(max = 500)
public String websiteUrl;

@Email
@Size(max = 255)
public String contactEmail;
```

**Pourquoi `org.hibernate.validator.constraints.URL`** : `jakarta.validation.constraints` n'expose pas de contrainte URL standard. Quarkus inclut déjà Hibernate Validator transitivement via `quarkus-hibernate-validator` (déjà présent — `@NotBlank` etc. l'utilisent). Aucun ajout de dépendance à `pom.xml`.

**Pourquoi `@Email` jakarta** : c'est la contrainte standard, déjà disponible via `jakarta.validation-api`, déjà utilisée ailleurs dans le projet.

**`@Size(max = …)`** : protège contre des inputs déraisonnables (XSS amplification, payloads abusifs). 500 pour URL (cap raisonnable), 255 pour email (limite RFC 5321).

**Champs nullable** : pas de `@NotBlank` ni `@NotNull` — un event peut ne pas avoir de site ni de contact.

### 3. `registrationDeadline` — vérification dans `AttendanceService.attend()`, pas en `@PrePersist`

```java
public LocalDateTime registrationDeadline;  // nullable
```

**Pourquoi pas en `@PrePersist`** : `@PrePersist` s'exécute à la persistance de l'`Event` (création/édition), or la deadline doit être évaluée **à l'instant de chaque tentative d'inscription**. Le bon endroit logique est donc `AttendanceService.attend()`, juste après la vérification du statut PUBLISHED et avant la branche capacité.

**Pas de validation croisée `registrationDeadline < endDate`** : un organisateur peut légitimement vouloir une deadline postérieure (cas rare mais possible — billetterie last-minute). Aucune contrainte croisée n'est ajoutée. Si le besoin émerge, ce sera dans une tâche dédiée.

### 4. WAITLISTED — promotion FIFO via `createdAt ASC`

```java
Attendance.<Attendance>find(
    "eventId = ?1 and status = ?2 order by createdAt asc, id asc",
    eventId, AttendanceStatus.WAITLISTED)
    .firstResultOptional();
```

**Pourquoi `createdAt`** : c'est l'ordre d'arrivée demandé explicitement par le backlog SCRUM-129. **`id` en tie-breaker** : deux WAITLISTED créés dans la même milliseconde sont départagés par leur ordre d'insertion DB.

**Pas d'index dédié** : la table `attendances` reste petite à l'échelle d'un événement (capacité-bornée). L'index unique `uq_attendance_user_event` couvre déjà les lookups par `(userId, eventId)`. Un `@Index(columnList = "event_id, status, created_at")` serait sur-engineering pour Sprint 5 — à reconsidérer si le profilage le justifie.

### 5. `availableSpots` (Long, nullable) et `waitlistedCount` (Long) — passés en paramètre à `EventDTO.from()`

**Nouvelle signature** :

```java
public static EventDTO from(
    Event event,
    long attendingCount,
    Long availableSpots,    // null si event.capacity == null
    long waitlistedCount
)
```

**Pourquoi étendre la factory plutôt que faire des sous-requêtes dans `EventDTO`** : la factory ne peut pas exécuter de requêtes sans l'`EntityManager`, et même si elle le pouvait, ça créerait du N+1 dans `getAll()`. Les compteurs sont calculés **dans le service** et passés au DTO.

**`availableSpots` nullable** : null = capacité non définie (illimitée). 0 = complet. >0 = places disponibles. Le frontend distingue ainsi « pas de capacité » de « complet ».

**Calcul** : `Math.max(0, event.capacity - attendingCount)` — protège contre le cas edge où `capacity` aurait été réduit alors que `attendingCount > capacity` (cf. edge cases).

**`waitlistedCount` non-nullable** : `0L` si pas de WAITLISTED. Cohérent avec `attendingCount` déjà en place.

### 6. Réutiliser `Attendance.countGroupedByStatus` pour le bulk count WAITLISTED dans `getAll()`

`getAll()` fait déjà un appel `countGroupedByStatus(ids, ATTENDING, em)`. On en fait un deuxième pour `WAITLISTED`. Pas besoin de modifier la méthode statique — son `:status` paramétrable couvre les deux cas.

### 7. Status code 409 pour deadline dépassée

Cohérent avec le `409` déjà utilisé pour `capacity` atteinte (`AttendanceService.attend()` actuel). 422 serait sémantiquement défendable mais romprait la cohérence interne. Le frontend traitera les deux cas via l'`error` dans `ApiErrorResponse` (`registration_closed` vs `conflict`).

### 8. Concurrence sur `count >= capacity` — verrou pessimiste sur `Event`

Race condition existante : deux inscriptions simultanées peuvent toutes deux lire `currentAttending = capacity - 1`, puis créer leurs `Attendance` ATTENDING, dépassant la capacité. La même race s'applique à la promotion WAITLISTED → ATTENDING.

**Décision** : lors de chaque mutation où `event.capacity != null`, charger l'`Event` avec `LockModeType.PESSIMISTIC_WRITE` pour sérialiser les inscriptions/désistements concurrents sur le même event.

```java
import jakarta.persistence.LockModeType;

Event event = entityManager.find(Event.class, eventId, LockModeType.PESSIMISTIC_WRITE);
if (event == null) throw new NotFoundException("Event not found");
```

**Pourquoi pessimiste plutôt qu'optimiste (`@Version`)** :
- `@Version` exigerait un retry côté client, et la dernière incrémentation de `Event` pour `tags`/etc. perturberait inutilement la concurrence d'inscription.
- PostgreSQL gère le verrou ligne efficacement. La table `events` n'est pas un hot path en écriture.
- Le verrou est limité au scope `@Transactional` de la méthode service — court par construction.

**Périmètre du verrou** : appliqué uniquement quand `event.capacity != null`. Si pas de capacité, pas de race possible — on garde le `findByIdOptional` actuel.

### 9. Notification de promotion WAITLISTED → ATTENDING — log INFO uniquement

Pas d'infrastructure de notification en Sprint 5. Décision : logger en `INFO` via `org.jboss.logging.Logger` :

```java
private static final Logger LOG = Logger.getLogger(AttendanceService.class);
LOG.infof("[WAITLIST_PROMOTION] event=%d user=%s promoted from WAITLISTED to ATTENDING",
          eventId, promoted.userId);
```

À remplacer par un vrai canal de notification quand l'infra associée arrivera (Sprint 7+). Pas de TODO commenté dans le code (cf. `AGENTS.md`).

### 10. Idempotence de `attend()` post-WAITLISTED

Si un user déjà inscrit (ATTENDING ou WAITLISTED) refait `POST /events/{id}/attend`, on **renvoie son `Attendance` existant sans rien changer**. Le statut WAITLISTED ne « rebascule » jamais en ATTENDING par appel client — la promotion est déclenchée uniquement par un désistement (`removeAttendance`).

Justification : éviter les appels client qui « tentent leur chance ». La promotion a un déclencheur déterministe : un slot se libère.

---

## SCRUM-126 — Détails fichier par fichier

### `entity/Event.java` — ajout des 4 champs

```java
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.FetchType;
import org.hibernate.validator.constraints.URL;
import jakarta.validation.constraints.Email;
import java.util.ArrayList;
import java.util.List;

// ... après le champ `allDay` :

@URL
@Column(length = 500)
public String websiteUrl;

@Email
@Column(length = 255)
public String contactEmail;

public LocalDateTime registrationDeadline;

@ElementCollection(fetch = FetchType.EAGER)
@CollectionTable(
    name = "event_tags",
    joinColumns = @JoinColumn(name = "event_id"),
    foreignKey = @ForeignKey(name = "fk_event_tags_event")
)
@Column(name = "tag", nullable = false, length = 64)
public List<String> tags = new ArrayList<>();
```

**Pas de nouvel index** sur ces champs — pas de filtre prévu sur `websiteUrl`/`contactEmail`/`registrationDeadline` au Sprint 5. La recherche par tag arrive en SCRUM-119 (Sprint 6) avec son propre index si nécessaire.

### `dto/event/EventRequestBase.java` — ajout des 4 champs

```java
import org.hibernate.validator.constraints.URL;
import jakarta.validation.constraints.Email;
import java.util.ArrayList;
import java.util.List;

// ... après `Boolean allDay;` :

@URL
@Size(max = 500)
public String websiteUrl;

@Email
@Size(max = 255)
public String contactEmail;

public LocalDateTime registrationDeadline;

@Size(max = 20)
public List<@NotBlank @Size(max = 64) String> tags = new ArrayList<>();
```

**`@Size(max = 20)` sur `tags`** : borne haute défensive pour éviter qu'un client n'envoie une liste de 10 000 chaînes. 20 est large pour le besoin réel.

**Validation par élément** : `@NotBlank @Size(max = 64)` sur chaque tag — Bean Validation 2.0 supporte les contraintes sur paramètres de type générique (ex. `List<@Valid String>`).

### `dto/event/EventDTO.java` — ajout des 4 champs (et des 2 compteurs SCRUM-129, voir plus bas)

Snippet complet de la nouvelle signature en section « SCRUM-126 + SCRUM-129 — `EventDTO` consolidé » plus bas (les deux tâches étendent le même record en une seule fois).

### `service/EventService.java` — propagation dans `create()` et `update()`

Helper privé pour la normalisation des tags :

```java
private static List<String> normalizeTags(List<String> input) {
    if (input == null) return new ArrayList<>();
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (String raw : input) {
        if (raw == null) continue;
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if (!t.isEmpty()) seen.add(t);
    }
    return new ArrayList<>(seen);
}
```

**`LinkedHashSet`** : préserve l'ordre d'insertion (l'organisateur s'attend à voir ses tags dans l'ordre saisi) tout en dédupliquant.

Dans `create()`, juste avant `event.persist()` :

```java
event.websiteUrl = request.websiteUrl;
event.contactEmail = request.contactEmail;
event.registrationDeadline = request.registrationDeadline;
event.tags = normalizeTags(request.tags);
```

Dans `update()`, juste avant le `return` :

```java
event.websiteUrl = request.websiteUrl;
event.contactEmail = request.contactEmail;
event.registrationDeadline = request.registrationDeadline;
event.tags = normalizeTags(request.tags);
```

### `service/AttendanceService.attend()` — vérification `registrationDeadline`

À insérer **après** la vérification `event.status != PUBLISHED` et **avant** la résolution `userId`/idempotence :

```java
if (event.registrationDeadline != null
        && LocalDateTime.now().isAfter(event.registrationDeadline)) {
    throw new WebApplicationException(
        Response.status(Response.Status.CONFLICT)
            .entity(new ApiErrorResponse(
                "registration_closed",
                "La deadline d'inscription est dépassée."))
            .type(MediaType.APPLICATION_JSON_TYPE)
            .build());
}
```

**Code d'erreur `registration_closed`** : distinct de `conflict` (capacité). Permet au front d'afficher des messages spécifiques.

### `openapi/openapi.yaml` — schémas `Event`, `CreateEventRequest`, `UpdateEventRequest`

Ajouter dans **`Event`** (après `attendingCount`) :

```yaml
        websiteUrl:
          type: string
          format: uri
          nullable: true
          description: URL externe de l'événement (optionnel)
        contactEmail:
          type: string
          format: email
          nullable: true
          description: Email de contact de l'organisateur pour cet événement (optionnel)
        registrationDeadline:
          type: string
          format: date-time
          nullable: true
          description: Date limite d'inscription. Au-delà, POST /events/{id}/attend retourne 409 registration_closed.
        tags:
          type: array
          items:
            type: string
            maxLength: 64
          maxItems: 20
          default: []
          description: Mots-clés associés à l'événement (normalisés en lowercase, dédupliqués côté backend).
```

Mêmes blocs à ajouter dans **`CreateEventRequest`** et **`UpdateEventRequest`** (avec `nullable: true` partout — tous optionnels). Pour `tags` dans les requests, garder `default: []` pour rendre le champ omittable.

---

## SCRUM-129 — Détails fichier par fichier

### `entity/AttendanceStatus.java`

```java
public enum AttendanceStatus {
    ATTENDING,
    WAITLISTED
}
```

Une seule valeur ajoutée. Aucun autre statut n'est introduit (pas de `INTERESTED`, hors scope).

### `service/AttendanceService.java` — refonte de `attend()` et `removeAttendance()`

Imports à ajouter :

```java
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.jboss.logging.Logger;
```

Champs et logger :

```java
private static final Logger LOG = Logger.getLogger(AttendanceService.class);

@Inject EntityManager entityManager;
```

#### Nouvelle structure de `attend()`

Pseudo-code (les blocs existants restent largement en place, on ajoute la branche WAITLISTED) :

```
@Transactional
attend(auth0Id, eventId, status):
    // 1. Charger event — verrou pessimiste si capacity != null
    Event event = (peek capacity sans verrou via findByIdOptional)
    if event == null: throw NotFoundException
    boolean withCapacity = (event.capacity != null)
    if withCapacity:
        event = entityManager.find(Event.class, eventId, PESSIMISTIC_WRITE)
        // re-vérifier null (théoriquement impossible mais coverage)

    // 2. Statut PUBLISHED
    if event.status != PUBLISHED:
        throw BadRequestException("Cannot attend a non-published event")

    // 3. Deadline (SCRUM-126)
    if event.registrationDeadline != null && now().isAfter(event.registrationDeadline):
        throw 409 registration_closed

    // 4. Résolution user
    UUID userId = resolveUserId(auth0Id)

    // 5. Idempotence : déjà inscrit ?
    Optional<Attendance> existing = Attendance.find(
        "userId = ?1 and eventId = ?2", userId, eventId).firstResultOptional()
    if existing.isPresent():
        return AttendanceDTO.from(existing.get())   // no-op, garde son statut

    // 6. Décider du statut effectif
    AttendanceStatus effective
    if !withCapacity:
        effective = ATTENDING
    else:
        long currentAttending = Attendance.count(
            "eventId = ?1 and status = ?2", eventId, ATTENDING)
        if currentAttending < event.capacity:
            effective = ATTENDING
        else:
            effective = WAITLISTED

    // 7. Persistance
    Attendance attendance = new Attendance()
    attendance.userId = userId
    attendance.eventId = eventId
    attendance.status = effective
    attendance.persist()
    return AttendanceDTO.from(attendance)
```

**Note importante** : la version actuelle d'`attend()` faisait un upsert (création OU mise à jour). Avec WAITLISTED, l'upsert devient ambigu (un WAITLISTED qui repasse `attend` doit-il « tenter sa chance » ?). Décision idempotente : on **ne touche jamais** une `Attendance` existante via cet endpoint. Le test `attend_alreadyAttending_isIdempotent` actuel reste vert.

**Le param `status` du body est conservé** : seul `ATTENDING` est accepté (`AttendanceRequest.status`). Si `WAITLISTED` est envoyé directement par un client, retourner `400` (jamais le client ne se met en file d'attente lui-même). Ajout d'un check :

```java
if (status != AttendanceStatus.ATTENDING) {
    throw new BadRequestException("Only ATTENDING is accepted as a request status");
}
```

Le statut effectif (`WAITLISTED`) est uniquement assigné par le serveur.

#### Nouvelle structure de `removeAttendance()`

```
@Transactional
removeAttendance(auth0Id, eventId):
    UUID userId = resolveUserId(auth0Id)
    Attendance attendance = Attendance.find(
        "userId = ?1 and eventId = ?2", userId, eventId)
        .firstResultOptional()
        .orElseThrow(NotFoundException)

    AttendanceStatus removed = attendance.status
    Long evtId = attendance.eventId
    attendance.delete()

    // Promotion uniquement si on libère un slot ATTENDING ET event a une capacité
    if removed != ATTENDING:
        return   // pas de promotion sur suppression d'un WAITLISTED
    Event event = entityManager.find(Event.class, evtId, PESSIMISTIC_WRITE)
    if event == null: return
    if event.capacity == null: return    // pas de capacité, pas de file d'attente
    if event.status == CANCELLED: return // ne pas promouvoir sur event annulé

    Attendance promoted = Attendance.find(
        "eventId = ?1 and status = ?2 order by createdAt asc, id asc",
        evtId, WAITLISTED).firstResultOptional().orElse(null)
    if promoted == null: return
    promoted.status = ATTENDING
    LOG.infof("[WAITLIST_PROMOTION] event=%d user=%s promoted to ATTENDING",
              evtId, promoted.userId)
```

**Verrou pessimiste sur `Event` même en suppression** : sérialise les désistements concurrents. Sans lui, deux désistements simultanés pourraient promouvoir le même WAITLISTED deux fois (impossible techniquement car un WAITLISTED ne se duplique pas, mais la promotion exclusive est garantie par la sérialisation).

### `service/EventService.java` — calcul `availableSpots` / `waitlistedCount`

Ajouter un helper :

```java
private static Long computeAvailableSpots(Integer capacity, long attendingCount) {
    if (capacity == null) return null;
    return Math.max(0L, capacity.longValue() - attendingCount);
}
```

Refactoring de `getAll()` — un deuxième bulk count sur WAITLISTED :

```java
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
```

Dans `getById()` :

```java
long att = countAttending(id);
long wait = countWaitlisted(id);
return EventDTO.from(event, att, computeAvailableSpots(event.capacity, att), wait);
```

Avec :

```java
private static long countWaitlisted(Long eventId) {
    return Attendance.count("eventId = ?1 and status = ?2", eventId, AttendanceStatus.WAITLISTED);
}
```

Et pareil dans `create()` (toujours `availableSpots = capacity` ou null, `waitlistedCount = 0`), `update()`, `cancel()`, `restore()`, `publish()`, `uploadImage()` — toutes les méthodes qui appellent actuellement `EventDTO.from(e, attendingCount)` doivent passer aux 4 paramètres. Pas de raccourci : la signature change, le compilateur force la mise à jour de tous les call sites.

### `dto/event/EventDTO.java` — SCRUM-126 + SCRUM-129 consolidés

```java
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
        boolean allDay,
        long attendingCount,
        Long availableSpots,           // SCRUM-129 — null si capacity null
        long waitlistedCount,          // SCRUM-129
        String websiteUrl,             // SCRUM-126
        String contactEmail,           // SCRUM-126
        LocalDateTime registrationDeadline, // SCRUM-126
        List<String> tags,             // SCRUM-126 — jamais null (vide si aucun)
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static EventDTO from(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount
    ) {
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
                event.allDay,
                attendingCount,
                availableSpots,
                waitlistedCount,
                event.websiteUrl,
                event.contactEmail,
                event.registrationDeadline,
                event.tags != null ? List.copyOf(event.tags) : List.of(),
                event.createdAt,
                event.updatedAt
        );
    }
}
```

**`List.copyOf(event.tags)`** : retourne une copie immuable. Évite que le DTO soit muté via la collection JPA proxy.

### `openapi/openapi.yaml` — `AttendanceStatus` et nouveaux champs `Event`

Modifier l'enum :

```yaml
    AttendanceStatus:
      type: string
      enum: [ATTENDING, WAITLISTED]
      description: |
        - ATTENDING : inscription confirmée.
        - WAITLISTED : inscription en file d'attente (assigné automatiquement par le backend
          quand l'événement a atteint sa capacité). Promu en ATTENDING quand un slot se libère.
```

Ajouter dans **`Event`** (après `attendingCount`, avant les champs SCRUM-126) :

```yaml
        availableSpots:
          type: integer
          format: int64
          nullable: true
          minimum: 0
          description: |
            Nombre de places restantes. null si l'événement n'a pas de capacité définie.
            0 = complet (les nouvelles inscriptions passent en WAITLISTED).
        waitlistedCount:
          type: integer
          format: int64
          minimum: 0
          description: Nombre d'utilisateurs en liste d'attente.
```

Mettre à jour la description du `409` sur `POST /events/{id}/attend` :

```yaml
        '409':
          description: |
            Conflit métier. Codes possibles dans `error` :
            - `registration_closed` : la deadline d'inscription est dépassée.
            Note : la capacité atteinte ne renvoie plus 409 — l'utilisateur est automatiquement placé en WAITLISTED (200).
```

**Mise à jour de la description du `200`** sur le même endpoint : ajouter qu'un body `Attendance` peut avoir `status: WAITLISTED`.

`AttendanceRequest.status` reste `ATTENDING` uniquement (le client ne demande jamais WAITLISTED).

---

## Interactions entre les deux tâches

### Ordre exact des checks dans `AttendanceService.attend()`

1. **Authentification** (déjà géré par `@Authenticated` sur la Resource).
2. **Validation du body** : `status == ATTENDING` (sinon 400).
3. **Charger l'event** (verrou pessimiste si `capacity != null`).
4. **Statut PUBLISHED** (sinon 400).
5. **Deadline** (`registrationDeadline` ; sinon 409 `registration_closed`).  ← SCRUM-126
6. **Résolution user**.
7. **Idempotence** : déjà inscrit ? → renvoie l'Attendance existante.
8. **Capacité** : null → ATTENDING ; sinon recompte → ATTENDING ou WAITLISTED.  ← SCRUM-129
9. **Persist** + retour DTO avec le statut effectif.

Cet ordre est testé explicitement (cf. tests `attend_deadlinePassedThenCapacity_returns409Deadline`).

### `EventDTO` retourne désormais 6 nouveaux champs en une seule réponse

Tous les endpoints qui produisent un `EventDTO` exposent simultanément `websiteUrl`, `contactEmail`, `registrationDeadline`, `tags`, `availableSpots`, `waitlistedCount`. Pas de versioning de schéma — c'est une extension additive non-breaking pour les clients existants (Jackson ignore les champs inconnus côté front).

---

## Contrat d'API — diff résumé

| Endpoint | Avant | Après |
|---|---|---|
| `GET /events` | `EventDTO[]` 16 champs | `EventDTO[]` 22 champs (+ 6) |
| `GET /events/{id}` | idem | idem |
| `POST /events` | accepte 13 champs | accepte 17 champs (+ websiteUrl, contactEmail, registrationDeadline, tags) |
| `PUT /events/{id}` | idem | idem |
| `POST /events/{id}/attend` | 200 ATTENDING / 409 capacity / 400 / 401 / 404 | 200 ATTENDING **ou WAITLISTED** / 409 `registration_closed` / 400 / 401 / 404. **Plus de 409 pour capacity.** |
| `DELETE /events/{id}/attend` | 204 | 204 (avec promotion FIFO transparente côté serveur si applicable) |

**Pas de nouveau path.** Pas de nouvel endpoint à enregistrer.

---

## Tests

Cible : 100 % de couverture sur les lignes nouvelles (JaCoCo). Style aligné sur `specs_scrum-77.md`.

### `EventDTOTest.java` — ajouts

| Test | Assertion |
|---|---|
| `from_withAllNewFields_mapsCorrectly` | tous les 6 nouveaux champs (`websiteUrl`, `contactEmail`, `registrationDeadline`, `tags`, `availableSpots`, `waitlistedCount`) sont mappés correctement |
| `from_withNullCapacity_returnsNullAvailableSpots` | `availableSpots == null` quand `event.capacity == null` |
| `from_withCapacityFull_returnsZeroAvailableSpots` | `attendingCount >= capacity` → `availableSpots == 0L` (jamais négatif) |
| `from_withNullTags_returnsEmptyList` | `event.tags = null` → DTO contient `List.of()` (jamais null) |
| `from_returnsImmutableTagsCopy` | modifier `event.tags` après l'appel ne mute pas le DTO |

### `CreateEventRequestTest.java` — ajouts (validation Bean Validation)

| Test | Scénario | Résultat |
|---|---|---|
| `validation_withInvalidWebsiteUrl_violatesURL` | `websiteUrl = "not-a-url"` | violation `URL` |
| `validation_withInvalidContactEmail_violatesEmail` | `contactEmail = "foo@"` | violation `Email` |
| `validation_withTooManyTags_violatesSize` | 21 tags | violation `Size` sur `tags` |
| `validation_withBlankTag_violatesNotBlank` | `tags = ["valid", "  "]` | violation `NotBlank` sur élément |
| `validation_withTooLongTag_violatesSize` | tag de 65 chars | violation `Size` sur élément |
| `validation_withNullableNewFields_passes` | tous les nouveaux champs null/vides | aucune violation |

### `EventResourceTest.java` — ajouts

| Test | Scénario | HTTP |
|---|---|---|
| `create_withAllNewFields_persistsAndReturns` | POST avec `websiteUrl`, `contactEmail`, `registrationDeadline`, `tags=["foo","bar"]` → réponse contient les 4 | 201 |
| `create_withInvalidWebsiteUrl_returns400` | `websiteUrl = "not-a-url"` | 400 ValidationError |
| `create_withInvalidEmail_returns400` | `contactEmail = "foo@"` | 400 |
| `create_withDuplicateTags_returnsNormalized` | `tags=["Foo","FOO","bar"," foo "]` → response `tags=["foo","bar"]` (ordre, lowercase, dédup) | 201 |
| `create_withMixedCaseAndWhitespace_normalized` | `tags=["  Cinéma  ","CINÉMA"]` → `["cinéma"]` | 201 |
| `update_withRegistrationDeadlinePast_persists` | PUT avec deadline passée — autorisé (la validation `@Future` n'est pas appliquée à `registrationDeadline`) | 200 |
| `getById_returnsAvailableSpotsAndWaitlistedCount` | seed event capacity=2, 2 ATTENDING + 1 WAITLISTED → DTO `availableSpots=0`, `waitlistedCount=1` | 200 |
| `getById_withoutCapacity_returnsNullAvailableSpots` | seed event capacity=null → DTO `availableSpots=null` | 200 |
| `getAll_returnsBulkCountedWaitlisted` | 3 events, comptes WAITLISTED différents → bulk count correct (un seul query group, vérifié via taille de la réponse pas via SQL) | 200 |

### `AttendanceResourceTest.java` — ajouts

| Test | Scénario | HTTP |
|---|---|---|
| `attend_afterRegistrationDeadline_returns409RegistrationClosed` | event avec `registrationDeadline = now().minusMinutes(1)` | 409, body `error: "registration_closed"` |
| `attend_beforeRegistrationDeadline_returns200` | deadline future | 200 |
| `attend_capacityReached_returnsWaitlisted` | capacity=1, déjà 1 ATTENDING, second user inscrit | 200, body `status: "WAITLISTED"` |
| `attend_capacityReached_thenCancelOne_promotesFirstWaitlisted` | capacity=1, 1 ATTENDING (A), 2 WAITLISTED (B puis C). DELETE pour A → B passe ATTENDING, C reste WAITLISTED | 204 puis assertion via GET attendees |
| `attend_withWaitlistedRequestStatus_returns400` | client envoie `status: "WAITLISTED"` | 400 |
| `attend_alreadyWaitlisted_isIdempotent` | re-POST → garde WAITLISTED, pas de doublon | 200, status reste WAITLISTED |
| `removeAttendance_removeWaitlisted_doesNotPromote` | capacity=1, 1 ATTENDING (A), 1 WAITLISTED (B). DELETE pour B → A reste ATTENDING, pas de promotion | 204 |
| `removeAttendance_onCancelledEvent_doesNotPromote` | event cancelled (mais on autorise removeAttendance), 1 ATTENDING + 1 WAITLISTED → DELETE A → B reste WAITLISTED | 204 |

### `AttendanceServiceCoverageTest.java` (intégration DevServices) — ajouts

| Test | Scénario |
|---|---|
| `attend_deadlinePassedThenCapacity_returns409Deadline` | deadline ET capacité atteintes → 409 `registration_closed` (ordre des checks vérifié) |
| `attend_capacityReachedExactly_promotesNextOnRemoval` | capacity=2, 2 ATTENDING, 3 WAITLISTED ordonnés par `createdAt`. DELETE 1er ATTENDING → 1er WAITLISTED (le plus ancien) promu |
| `removeAttendance_pessimisticLock_serializesPromotions` | smoke test : deux désistements consécutifs sur capacity=3 ne promeuvent pas le même user deux fois (vérifié via assertion sur les états finaux, pas via threading réel — ce test sert surtout la couverture du chemin verrou) |

### `EventServiceCoverageTest.java` (intégration DevServices) — ajouts

| Test | Scénario |
|---|---|
| `create_withTags_persistsNormalizedList` | persiste `["A","b","A "]` → en DB : `["a","b"]` |
| `create_withRegistrationDeadline_persists` | round-trip OK |
| `update_withTagsCleared_persistsEmpty` | `tags = []` → persisté vide |
| `update_withTagsNull_persistsEmpty` | `tags = null` → normalisé en `[]` |
| `getAll_bulkCountsWaitlisted` | 3 events avec waitlists distinctes → counts corrects |
| `getById_capacityReducedBelowAttending_returnsZeroAvailable` | event avec capacity=5 et 7 ATTENDING (race historique) → `availableSpots=0`, pas de valeur négative |

---

## Edge cases — comportements explicites

| Cas | Comportement |
|---|---|
| `capacity` réduit alors que `attendingCount > capacity` | Pas de rétro-déclassement. `availableSpots = max(0, capacity - attendingCount)`. Les nouveaux passent directement en WAITLISTED. |
| Promotion sur event `CANCELLED` | Skip (early return dans `removeAttendance`). Aucun WAITLISTED promu. Logué en `INFO` si on veut tracer (optionnel). |
| Suppression d'un WAITLISTED | Pas de promotion. Le check `removed != ATTENDING → return` est explicite. |
| `registrationDeadline` postérieure à `endDate` | Autorisé. Pas de validation croisée. |
| `tags` doublons / casse / espaces | Normalisés côté service : trim → lowercase → dédup ordonnée (LinkedHashSet). |
| `tags = null` en input | Stocké comme `[]`, jamais null. Le DTO retourne `List.of()`. |
| Concurrence sur deux désistements simultanés | Verrou pessimiste sur `Event` (PESSIMISTIC_WRITE) sérialise. Le 2e désistement attend que le 1er commit, recompte, promeut le suivant. |
| Concurrence sur deux inscriptions simultanées (capacity=1) | Idem : la 2e attend, recompte ATTENDING=1, passe en WAITLISTED. |
| Event sans `capacity` | Aucun verrou pessimiste (économie d'overhead). Aucun WAITLISTED jamais créé. |
| Utilisateur qui se désinscrit alors qu'il était WAITLISTED | Suppression pure et simple, pas de promotion (ne libère aucun slot ATTENDING). |
| `attend()` reçoit un event introuvable | 404 (comportement existant, conservé). |
| `attend()` sur event PUBLISHED puis cancelled entre-temps | La transaction relit le statut sous verrou → BadRequest (chemin existant). |

---

## Ordre d'implémentation

1. `openapi/openapi.yaml` — schémas `Event`, `CreateEventRequest`, `UpdateEventRequest`, enum `AttendanceStatus`, descriptions des codes 200/409 sur `POST /events/{id}/attend`. **EN PREMIER.**
2. `entity/AttendanceStatus.java` — ajouter `WAITLISTED`.
3. `entity/Event.java` — ajouter `websiteUrl`, `contactEmail`, `registrationDeadline`, `tags`.
4. `dto/event/EventRequestBase.java` — ajouter les 4 champs avec validation.
5. `dto/event/EventDTO.java` — étendre le record (6 champs) + nouvelle signature `from(Event, long, Long, long)`.
6. `service/EventService.java` :
   - helper `normalizeTags`
   - helper `computeAvailableSpots`
   - helper `countWaitlisted`
   - propagation des 4 nouveaux champs dans `create()` et `update()`
   - mise à jour de **tous** les call sites `EventDTO.from(...)` (compilateur force la migration)
   - bulk count WAITLISTED dans `getAll()`
7. `service/AttendanceService.java` :
   - injection `EntityManager` + `Logger`
   - validation `status == ATTENDING` en entrée
   - vérification `registrationDeadline` (409 `registration_closed`)
   - verrou pessimiste sur `Event` quand `capacity != null`
   - logique WAITLISTED dans `attend()`
   - logique de promotion FIFO dans `removeAttendance()`
   - log INFO de promotion
8. Mocks de test : `EventServiceMock`, `AttendanceServiceMock` (mettre à jour les signatures si méthodes mockées).
9. Tests :
   - `EventDTOTest`
   - `CreateEventRequestTest`
   - `EventResourceTest`
   - `AttendanceResourceTest`
   - `EventServiceCoverageTest`
   - `AttendanceServiceCoverageTest`
10. Documentation :
    - `backend/docs/data-model.md` — entité `Event` mise à jour, table `event_tags`, enum `AttendanceStatus`, sémantique de `availableSpots`/`waitlistedCount`.
    - `backend/docs/sprint-context.md` — marquer SCRUM-126 et SCRUM-129 « Done ».

---

## Fichiers touchés (récap)

### Modifiés

- `openapi/openapi.yaml`
- `backend/src/main/java/ch/unige/events/entity/Event.java`
- `backend/src/main/java/ch/unige/events/entity/AttendanceStatus.java`
- `backend/src/main/java/ch/unige/events/dto/event/EventDTO.java`
- `backend/src/main/java/ch/unige/events/dto/event/EventRequestBase.java`
- `backend/src/main/java/ch/unige/events/service/EventService.java`
- `backend/src/main/java/ch/unige/events/service/AttendanceService.java`
- `backend/src/test/java/ch/unige/events/dto/EventDTOTest.java`
- `backend/src/test/java/ch/unige/events/dto/CreateEventRequestTest.java`
- `backend/src/test/java/ch/unige/events/resource/EventResourceTest.java`
- `backend/src/test/java/ch/unige/events/resource/AttendanceResourceTest.java`
- `backend/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java`
- `backend/src/test/java/ch/unige/events/service/AttendanceServiceCoverageTest.java`
- `backend/src/test/java/ch/unige/events/service/EventServiceMock.java` (si signatures à propager)
- `backend/src/test/java/ch/unige/events/service/AttendanceServiceMock.java` (idem)
- `backend/docs/data-model.md`
- `backend/docs/sprint-context.md`

### Créés

Aucun fichier source nouveau. Aucune classe entité nouvelle (la table `event_tags` est créée par Hibernate à partir de `@ElementCollection`).

---

## Checklist Sonar

- [ ] 100 % couverture sur les lignes nouvelles (JaCoCo)
- [ ] Duplication < 3 % sur le code nouveau
- [ ] Security Rating : A
- [ ] Reliability Rating : A
- [ ] Maintainability Rating : A
- [ ] Security Review Rating : A

---

## Prompt de lancement d'implémentation

```
Tu vas implémenter SCRUM-126 (champs additionnels websiteUrl, contactEmail, registrationDeadline, tags sur Event) et SCRUM-129 (renforcement capacité + WAITLISTED + liste d'attente FIFO) en un seul PR atomique pour le projet UNIGE Events backend.

## ÉTAPE 0 — Création de la branche

Avant TOUT, crée la branche depuis `feature/s5-my-events-page` (PAS depuis `main`) :

    git fetch origin
    git checkout -b feature/s5-event-extra-fields-capacity origin/feature/s5-my-events-page

Pourquoi pas `main` : six fichiers backend (Event.java, EventDTO.java, EventRequestBase.java, EventService.java, EventResource.java, openapi.yaml) sont déjà modifiés sur `feature/s5-my-events-page` (qui apporte SCRUM-124 allDay, publish, cancel/restore, hard-delete). Repartir de `main` provoquerait des conflits massifs au merge. Le PR final ciblera `main`, mais ne rebase PAS sur main avant que la branche parente soit mergée — sinon tu réintroduis exactement les conflits qu'on cherche à éviter.

## Source unique de vérité

`specs_archives/specs_claude/specs_scrum-126-129.md` — à lire intégralement avant de coder. Toutes les décisions techniques (verrou pessimiste, normalisation des tags, signature de EventDTO.from, ordre des checks dans attend, etc.) y sont déjà tranchées. Tu n'as RIEN à inventer.

## À lire avant de commencer

1. `backend/AGENTS.md` — conventions impératives (camelCase, pas de snake_case, pas de logique dans Resource, Hibernate update donc PAS de migration SQL, openapi.yaml en premier).
2. `backend/docs/architecture.md`, `backend/docs/data-model.md`, `backend/docs/dev-guide.md`, `backend/docs/sprint-context.md`.
3. `openapi/openapi.yaml` — état actuel sur la branche (déjà à jour avec SCRUM-124 allDay).
4. Le code backend tel qu'il est sur `feature/s5-my-events-page` :
   - `entity/Event.java`, `entity/Attendance.java`, `entity/AttendanceStatus.java`
   - `dto/event/EventDTO.java`, `dto/event/EventRequestBase.java`, `dto/event/CreateEventRequest.java`, `dto/event/UpdateEventRequest.java`
   - `service/EventService.java`, `service/AttendanceService.java`
   - `resource/EventResource.java`, `resource/AttendanceResource.java`
   - Tests existants pour comprendre les patterns en place.

## Ordre d'implémentation strict

1. **`openapi/openapi.yaml`** — TOUJOURS en premier. Ajouter websiteUrl, contactEmail, registrationDeadline, tags dans Event/CreateEventRequest/UpdateEventRequest. Ajouter availableSpots et waitlistedCount dans Event. Ajouter WAITLISTED à AttendanceStatus. Mettre à jour la description du 200 et du 409 sur POST /events/{id}/attend (200 peut désormais retourner WAITLISTED, 409 devient `registration_closed` uniquement, plus de 409 capacity).
2. **`entity/AttendanceStatus.java`** — ajouter WAITLISTED.
3. **`entity/Event.java`** — ajouter websiteUrl (@URL Hibernate Validator + @Column length=500), contactEmail (@Email jakarta + @Column length=255), registrationDeadline (LocalDateTime), tags (@ElementCollection EAGER + @CollectionTable("event_tags") + initialisation `new ArrayList<>()`). Pas de nouvel index.
4. **`dto/event/EventRequestBase.java`** — ajouter les 4 champs avec validation : @URL @Size(max=500) websiteUrl ; @Email @Size(max=255) contactEmail ; LocalDateTime registrationDeadline ; @Size(max=20) List<@NotBlank @Size(max=64) String> tags = new ArrayList<>().
5. **`dto/event/EventDTO.java`** — étendre le record avec les 6 nouveaux champs (websiteUrl, contactEmail, registrationDeadline, tags, availableSpots [Long nullable], waitlistedCount [long]). Nouvelle factory `from(Event, long attendingCount, Long availableSpots, long waitlistedCount)`. Utiliser `List.copyOf(event.tags)` ou `List.of()` si null.
6. **`service/EventService.java`** :
   - helper privé `normalizeTags` (trim → lowercase Locale.ROOT → LinkedHashSet pour dédup ordonnée)
   - helper privé `computeAvailableSpots(Integer capacity, long attendingCount)` (null si capacity null, sinon Math.max(0, capacity - attendingCount))
   - helper privé `countWaitlisted(Long eventId)`
   - dans `create()` et `update()`, propager les 4 nouveaux champs et appeler `normalizeTags(request.tags)`
   - dans `getAll()`, faire un deuxième bulk count via `Attendance.countGroupedByStatus(ids, WAITLISTED, entityManager)` et passer les compteurs à la nouvelle factory
   - mettre à jour TOUS les call sites de `EventDTO.from(...)` (le compilateur t'y forcera) : `getById`, `create`, `update`, `cancel`, `restore`, `publish`, `uploadImage`. Pour `create` initialement, `availableSpots = computeAvailableSpots(capacity, 0L)` et `waitlistedCount = 0L`.
7. **`service/AttendanceService.java`** :
   - `@Inject EntityManager entityManager` et `private static final Logger LOG = Logger.getLogger(...)`
   - Dans `attend()` : valider `status == ATTENDING` en entrée (sinon BadRequest). Charger l'event ; si capacity != null, recharger avec `entityManager.find(Event.class, id, LockModeType.PESSIMISTIC_WRITE)`. Vérifier PUBLISHED. Vérifier registrationDeadline (409 `registration_closed`). Résoudre user. Idempotence : si déjà inscrit (ATTENDING ou WAITLISTED), renvoyer l'existant tel quel sans modification. Sinon, si capacity null → ATTENDING ; sinon recompte ATTENDING : si < capacity → ATTENDING, sinon WAITLISTED. Persist, retour DTO.
   - Dans `removeAttendance()` : supprimer l'attendance. Si `removed != ATTENDING` ou `event.capacity == null` ou `event.status == CANCELLED` → return. Sinon recharger l'event en PESSIMISTIC_WRITE et chercher le premier WAITLISTED `order by createdAt asc, id asc` → le promouvoir (`status = ATTENDING`) et logger en INFO `[WAITLIST_PROMOTION] event=%d user=%s ...`.
8. **Mocks de test** (`EventServiceMock`, `AttendanceServiceMock`) — adapter les signatures si nécessaire.
9. **Tests** (cible 100 % couverture sur lignes nouvelles, voir tableaux dans la spec) :
   - `EventDTOTest` : mapping des nouveaux champs, availableSpots null/zero, tags null → liste vide, immuabilité.
   - `CreateEventRequestTest` : validations URL/Email/Size sur tags.
   - `EventResourceTest` : POST/PUT avec nouveaux champs, normalisation tags (case + dédup + trim), GET retourne availableSpots et waitlistedCount.
   - `AttendanceResourceTest` : 409 registration_closed, 200 WAITLISTED quand capacity atteinte, promotion FIFO sur DELETE, idempotence WAITLISTED, 400 si client demande WAITLISTED, suppression d'un WAITLISTED ne promeut pas, event CANCELLED ne promeut pas.
   - `EventServiceCoverageTest` et `AttendanceServiceCoverageTest` (intégration DevServices PostgreSQL) : tags persistés normalisés, capacity réduit sous attendingCount → availableSpots=0, ordre des checks deadline-puis-capacity, promotion FIFO via createdAt.
10. **Documentation** :
    - `backend/docs/data-model.md` : Event mis à jour, table `event_tags`, enum AttendanceStatus avec WAITLISTED, sémantique availableSpots/waitlistedCount.
    - `backend/docs/sprint-context.md` : marquer SCRUM-126 et SCRUM-129 comme Done.

## Interdits stricts

- **PAS** de migration SQL (Flyway interdit, Hibernate update fait le travail).
- **PAS** de nouvel endpoint REST. Tout passe par les endpoints existants.
- **PAS** de modification de la couche auth (ni Resource ni configuration OIDC).
- **PAS** de rétro-déclassement des ATTENDING quand `capacity` est réduit. `availableSpots = Math.max(0, ...)`.
- **PAS** de système de notification (juste un log INFO sur la promotion).
- **PAS** de rebase sur `main` avant que `feature/s5-my-events-page` soit mergée — tu réintroduirais les conflits qu'on a précisément cherché à éviter.
- **PAS** de logique métier dans les Resource (tout dans les Service).
- **PAS** de snake_case dans les champs Java ou les payloads JSON.
- **PAS** de booléen avec préfixe `is`.
- **PAS** de validation croisée `registrationDeadline < endDate` (autorisé).
- **PAS** de nouvel index DB sur les nouveaux champs (pas de filtre prévu en Sprint 5).
- **PAS** de TODO/commentaires « to be replaced » dans le code (le log INFO de promotion suffit).

## Conventions à respecter

- camelCase partout (champs JPA, JSON).
- Validation via Hibernate Validator (`org.hibernate.validator.constraints.URL`) et jakarta (`jakarta.validation.constraints.Email`, `@Size`, `@NotBlank`). Ne pas ajouter de dépendance — tout est déjà transitivement présent via `quarkus-hibernate-validator`.
- 100 % couverture JaCoCo sur les lignes nouvelles.
- Sonar : ratings A en Security/Reliability/Maintainability/Security Review, duplication < 3 % sur le code nouveau.
- Pas de `null` dans les payloads JSON là où le frontend attend une collection (tags retourne toujours `[]`).
- `EventDTO` est immuable (record + List.copyOf).

## Critères de done

- [ ] `./mvnw verify` vert localement (et en CI).
- [ ] JaCoCo ≥ 100 % sur les lignes nouvelles (vérifier le rapport `target/site/jacoco/index.html`).
- [ ] `openapi/openapi.yaml` validé (linter OpenAPI si pré-commit configuré).
- [ ] Tests scénarios principaux verts :
  - inscription après deadline → 409 `registration_closed`
  - capacité atteinte → 200 `WAITLISTED`
  - désistement d'un ATTENDING → premier WAITLISTED (ordre `createdAt asc`) promu en ATTENDING
  - normalisation des tags (case, trim, dédup, ordre)
  - `availableSpots = null` si pas de capacité, `0` si pleine, jamais négatif
- [ ] `backend/docs/data-model.md` et `backend/docs/sprint-context.md` mis à jour dans le même commit que le code.
- [ ] Doc OpenAPI cohérente avec le code (`openapi.yaml` modifié EN PREMIER, vérifié au merge).
- [ ] PR ouvert avec base `main` (à rebaser sur `main` après merge de `feature/s5-my-events-page`).
- [ ] Commits atomiques et bien nommés (`feat(scrum-126)`, `feat(scrum-129)`, `test(...)`, `docs(...)`).
```
