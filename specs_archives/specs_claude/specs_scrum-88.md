# Specs SCRUM-88 — Entité Attendance + endpoints inscription/désinscription + correctif CalendarService

> **Branche :** `feature/s4-favorites-share` (même PR que SCRUM-89 et SCRUM-89-bis)
> **Sprint :** 4 — Backend Tâche 1/2 · Epic 4 – Engagement & Interaction
> **Prérequis :** SCRUM-89 mergé (`Favorite.java`, `FavoriteService.java`, `CalendarService.java`) ✅ déjà dans la branche
> **Règle d'or :** Modifier `openapi/openapi.yaml` EN PREMIER, puis coder Entity → DTO → Service → Resource → Tests → Docs
> **Ordre d'implémentation OBLIGATOIRE :** voir section dédiée en fin de document

---

## Contexte

### Ce que fait SCRUM-88

Implémenter le système de participation aux événements UNIGE :

- Un utilisateur peut s'inscrire à un événement avec le statut `INTERESTED` (je suis intéressé)
  ou `ATTENDING` (je participe activement)
- L'inscription est un **upsert** : un deuxième `POST` avec un statut différent met à jour le statut existant
- La capacité maximale est vérifiée côté backend : si `event.capacity` est atteinte
  et que le statut demandé est `ATTENDING` → `409 Conflict`
- `INTERESTED` n'est jamais bloqué par la capacité

### Correctif CalendarService (inclus dans ces specs)

La première implémentation de `CalendarService.generateIcsFeed()` (SCRUM-89-bis) utilise `Favorite`
comme source de données — **c'est une erreur**. Le flux ICS doit refléter les événements auxquels
l'utilisateur s'est **inscrit explicitement** (`Attendance`), pas ceux qu'il a simplement mis en favoris.

Ce correctif est inclus ici car `Attendance.java` est le prérequis bloquant.

---

## Ce qui existe déjà (ne pas retoucher sauf indication contraire)

| Fichier | État |
|---|---|
| `entity/User.java` | Complet — champ `id` (UUID) utilisé comme `userId` dans `Attendance` |
| `entity/Event.java` | Complet — champs `capacity`, `creator`, `status` lus par `AttendanceService` |
| `entity/Favorite.java` | Complet (SCRUM-89) — **à modifier** : retirer `findAllByUser` |
| `service/FavoriteService.java` | Complet — ne pas toucher |
| `service/CalendarService.java` | Complet (SCRUM-89-bis) — **à modifier** : corriger `generateIcsFeed` |
| `resource/UserResource.java` | Complet — **à étendre** : injection `AttendanceService` + `GET /me/attendances` |
| `test/.../service/FavoriteServiceCoverageProfile.java` | Complet — **à modifier** : exclure `AttendanceServiceMock` |
| `test/.../service/CalendarServiceCoverageProfile.java` | Complet — **à modifier** : exclure `AttendanceServiceMock` |
| `test/.../service/CalendarServiceCoverageTest.java` | Complet — **à modifier** : remplacer `persistFavorite` par `persistAttendance` |
| `test/.../resource/UserResourceTest.java` | Complet — **à étendre** : injection `AttendanceServiceMock` + 2 nouveaux tests |
| `openapi/openapi.yaml` | Complet partiellement — schémas `Attendance`/`AttendanceRequest` en TODO Sprint 4 — à compléter |

---

## Ce qui est à créer

| Fichier | Action |
|---|---|
| `entity/AttendanceStatus.java` | Nouveau enum |
| `entity/Attendance.java` | Nouvelle entité JPA |
| `dto/attendance/AttendanceDTO.java` | Nouveau record |
| `dto/attendance/AttendanceRequest.java` | Nouveau record (body POST) |
| `service/AttendanceService.java` | Nouveau service |
| `resource/AttendanceResource.java` | Nouveau — `@Path("/events")` |
| `test/.../service/AttendanceServiceMock.java` | Nouveau mock in-memory |
| `test/.../service/AttendanceServiceCoverageProfile.java` | Nouveau profil DB |
| `test/.../service/AttendanceServiceCoverageTest.java` | Nouveau — 15 cas de test |
| `test/.../resource/AttendanceResourceTest.java` | Nouveau — 11 cas de test |

---

## Ce qui est à modifier

| Fichier | Modification |
|---|---|
| `entity/Favorite.java` | Retirer `findAllByUser` (ajouté par erreur pour CalendarService) |
| `service/CalendarService.java` | Corriger `generateIcsFeed` : utiliser `Attendance` au lieu de `Favorite` |
| `resource/UserResource.java` | Injection `AttendanceService` + méthode `GET /me/attendances` |
| `test/.../service/FavoriteServiceCoverageProfile.java` | Ajouter `AttendanceServiceMock` dans `exclude-types` |
| `test/.../service/CalendarServiceCoverageProfile.java` | Ajouter `AttendanceServiceMock` dans `exclude-types` |
| `test/.../service/CalendarServiceCoverageTest.java` | Remplacer les 3 tests `persistFavorite` par `persistAttendance` + ajouter cas INTERESTED |
| `test/.../resource/UserResourceTest.java` | Injection `AttendanceServiceMock` + reset + 2 nouveaux tests |
| `openapi/openapi.yaml` | Compléter schémas `Attendance`/`AttendanceRequest` + ajouter `AttendanceDTO` + 4 paths |
| `backend/docs/data-model.md` | Ajouter entité `Attendance` + corriger helper `Favorite` |
| `backend/docs/api-contract.md` | Ajouter 4 nouveaux endpoints + corriger description ICS |
| `backend/docs/sprint-context.md` | Marquer SCRUM-88 comme en cours + corriger SCRUM-89-bis |

---

## Architecture des endpoints

| Endpoint | Classe | Auth | Description |
|---|---|---|---|
| `POST /api/events/{id}/attend` | `AttendanceResource` (nouveau) | `@Authenticated` | Upsert participation (INTERESTED / ATTENDING) |
| `DELETE /api/events/{id}/attend` | `AttendanceResource` (nouveau) | `@Authenticated` | Se désinscrire |
| `GET /api/events/{id}/attendees` | `AttendanceResource` (nouveau) | `@Authenticated` | Liste paginée des participants (créateur de l'événement uniquement) |
| `GET /api/users/me/attendances` | `UserResource` (étendu) | `@Authenticated` | Mes inscriptions (toutes, avec statut) |

---

## Étape 0 — `openapi/openapi.yaml` (OBLIGATOIRE EN PREMIER)

**Fichier :** `/workspace/openapi/openapi.yaml`

### 0.1 — Compléter le schéma `Attendance` (remplacer le TODO)

Localiser dans `components/schemas` le bloc `Attendance` (marqué `# TODO: Sprint 4`) et le remplacer par :

```yaml
    Attendance:
      type: object
      description: Inscription d'un utilisateur à un événement (INTERESTED ou ATTENDING)
      properties:
        id:
          type: integer
          format: int64
        userId:
          type: string
          format: uuid
        eventId:
          type: integer
          format: int64
        status:
          $ref: '#/components/schemas/AttendanceStatus'
        createdAt:
          type: string
          format: date-time
      required: [id, userId, eventId, status, createdAt]
```

### 0.2 — Compléter le schéma `AttendanceRequest` (remplacer le TODO)

Localiser `AttendanceRequest` (marqué `# TODO: Sprint 4`) et le remplacer par :

```yaml
    AttendanceRequest:
      type: object
      description: Body pour POST /events/{id}/attend
      required: [status]
      properties:
        status:
          $ref: '#/components/schemas/AttendanceStatus'
          description: Statut souhaité — INTERESTED ou ATTENDING
```

### 0.3 — Ajouter le path `/events/{id}/attend` (après `/events/{id}/favorite`)

```yaml
  /events/{id}/attend:
    post:
      summary: S'inscrire à un événement (upsert)
      description: |
        Crée ou met à jour la participation de l'utilisateur connecté à l'événement.
        **Upsert** : si une inscription existe déjà avec un statut différent, le statut est mis à jour.
        Retourne 409 si l'événement a atteint sa capacité maximale et que le statut demandé est ATTENDING.
        INTERESTED n'est jamais bloqué par la capacité.
      operationId: attendEvent
      tags: [events, attendance]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/AttendanceRequest'
      responses:
        '200':
          description: Inscription créée ou mise à jour
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Attendance'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Événement introuvable
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '409':
          description: Capacité maximale atteinte (uniquement si status=ATTENDING)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

    delete:
      summary: Se désinscrire d'un événement
      operationId: removeAttendance
      tags: [events, attendance]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '204':
          description: Désinscription effectuée
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Inscription introuvable (l'utilisateur n'était pas inscrit)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.4 — Ajouter le path `/events/{id}/attendees` (après `/events/{id}/attend`)

```yaml
  /events/{id}/attendees:
    get:
      summary: Liste des participants à un événement (créateur uniquement)
      description: |
        Retourne la liste paginée des inscriptions à l'événement.
        Réservé au créateur de l'événement — retourne 403 pour tout autre utilisateur authentifié.
      operationId: getEventAttendees
      tags: [events, attendance]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: integer
            format: int64
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
          description: Liste paginée des inscriptions
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Attendance'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '403':
          description: Appelant non créateur de l'événement
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Événement introuvable
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.5 — Ajouter le path `/users/me/attendances` (après `/users/me/calendar-token`)

```yaml
  /users/me/attendances:
    get:
      summary: Mes inscriptions aux événements
      description: |
        Retourne toutes les inscriptions (INTERESTED et ATTENDING) de l'utilisateur connecté.
        Le champ status permet au frontend de distinguer les deux niveaux d'engagement.
      operationId: getMyAttendances
      tags: [users, attendance]
      security:
        - BearerAuth: []
      responses:
        '200':
          description: Liste des inscriptions (tableau vide si aucune — jamais 404)
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Attendance'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.6 — Corriger la description de `GET /calendar/{calendarToken}.ics`

Localiser la description du path `GET /calendar/{calendarToken}.ics` et remplacer :

```yaml
      description: |
        Retourne le flux iCalendar RFC 5545 des événements favoris PUBLISHED de l'utilisateur
        identifié par son token personnel. ...
```

Par :

```yaml
      description: |
        Retourne le flux iCalendar RFC 5545 des événements PUBLISHED auxquels l'utilisateur
        (identifié par son token personnel) s'est inscrit explicitement (statut INTERESTED
        ou ATTENDING). Polled automatiquement par les applications calendrier.
        Content-Type: text/calendar;charset=UTF-8.
        Authentification via token dans l'URL (pas de Bearer JWT requis).
```

---

## Étape 1 — Entités

### 1.1 — `AttendanceStatus.java` (nouveau enum)

**Fichier :** `backend/src/main/java/ch/unige/events/entity/AttendanceStatus.java`

```java
package ch.unige.events.entity;

public enum AttendanceStatus {
    INTERESTED,
    ATTENDING
}
```

### 1.2 — `Attendance.java` (nouvelle entité)

**Fichier :** `backend/src/main/java/ch/unige/events/entity/Attendance.java`

```java
package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "attendances",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_attendance_user_event",
        columnNames = {"user_id", "event_id"}
    )
)
public class Attendance extends PanacheEntity {

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    public AttendanceStatus status;

    @Column(updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public static List<Attendance> findByEvent(Long eventId, int page, int size) {
        return find("eventId = ?1", eventId)
                .page(page, size)
                .list();
    }

    public static List<Attendance> findAllByUser(UUID userId) {
        return list("userId = ?1", userId);
    }
}
```

**Points à respecter :**
- `PanacheEntity` (Long PK) — cohérent avec `Event` et `Favorite`
- `userId` (UUID) et `eventId` (Long) stockés sans `@ManyToOne` — cohérent avec `Favorite`
- Contrainte unique nommée `uq_attendance_user_event` — Hibernate la crée en mode `update`
- `status` avec `@Enumerated(EnumType.STRING)` — valeur lisible en base
- `createdAt` `updatable = false`, initialisé via `@PrePersist`
- `findByEvent` : paginée (pour `GET /events/{id}/attendees`)
- `findAllByUser` : non paginée (pour `GET /users/me/attendances` et `CalendarService`)

### 1.3 — `Favorite.java` : retirer `findAllByUser`

**Fichier :** `backend/src/main/java/ch/unige/events/entity/Favorite.java`

Supprimer la méthode suivante (ajoutée par erreur lors de SCRUM-89-bis) :

```java
// À SUPPRIMER INTÉGRALEMENT
public static List<Favorite> findAllByUser(UUID userId) {
    return list("userId = ?1", userId);
}
```

L'import `java.util.List` reste car il est utilisé par `findByUser`.

---

## Étape 2 — DTOs

### 2.1 — `AttendanceDTO.java` (nouveau record)

**Fichier :** `backend/src/main/java/ch/unige/events/dto/attendance/AttendanceDTO.java`

Créer le répertoire `dto/attendance/` s'il est absent.

```java
package ch.unige.events.dto.attendance;

import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AttendanceDTO(
        Long id,
        UUID userId,
        Long eventId,
        AttendanceStatus status,
        LocalDateTime createdAt
) {
    public static AttendanceDTO from(Attendance attendance) {
        return new AttendanceDTO(
                attendance.id,
                attendance.userId,
                attendance.eventId,
                attendance.status,
                attendance.createdAt
        );
    }
}
```

### 2.2 — `AttendanceRequest.java` (nouveau record)

**Fichier :** `backend/src/main/java/ch/unige/events/dto/attendance/AttendanceRequest.java`

```java
package ch.unige.events.dto.attendance;

import ch.unige.events.entity.AttendanceStatus;
import jakarta.validation.constraints.NotNull;

public record AttendanceRequest(@NotNull AttendanceStatus status) {}
```

---

## Étape 3 — `AttendanceService.java` (nouveau)

**Fichier :** `backend/src/main/java/ch/unige/events/service/AttendanceService.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.dto.attendance.AttendanceRequest;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class AttendanceService {

    @Transactional
    public AttendanceDTO attend(String auth0Id, Long eventId, AttendanceStatus status) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        UUID userId = resolveUserId(auth0Id);

        // Vérification capacité — uniquement pour ATTENDING
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
                    throw new WebApplicationException(
                            Response.status(Response.Status.CONFLICT)
                                    .entity(new ch.unige.events.dto.ApiErrorResponse(
                                            "conflict", "Event has reached maximum capacity"))
                                    .type(MediaType.APPLICATION_JSON_TYPE)
                                    .build());
                }
            }
        }

        // Upsert : mettre à jour si existe, créer sinon
        Attendance attendance = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", userId, eventId)
                .firstResultOptional()
                .orElse(null);

        if (attendance == null) {
            attendance = new Attendance();
            attendance.userId = userId;
            attendance.eventId = eventId;
            attendance.persist();
        }
        attendance.status = status;

        return AttendanceDTO.from(attendance);
    }

    @Transactional
    public void removeAttendance(String auth0Id, Long eventId) {
        UUID userId = resolveUserId(auth0Id);

        Attendance attendance = Attendance.<Attendance>find(
                "userId = ?1 and eventId = ?2", userId, eventId)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Attendance not found"));

        attendance.delete();
    }

    @Transactional
    public List<AttendanceDTO> getAttendees(String auth0Id, Long eventId, int page, int size) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(() -> new NotFoundException("Event not found"));

        if (!event.creator.auth0Id.equals(auth0Id)) {
            throw new ForbiddenException("Only the event creator can view attendees");
        }

        return Attendance.findByEvent(eventId, page, size).stream()
                .map(AttendanceDTO::from)
                .toList();
    }

    @Transactional
    public List<AttendanceDTO> getMyAttendances(String auth0Id) {
        UUID userId = resolveUserId(auth0Id);
        return Attendance.findAllByUser(userId).stream()
                .map(AttendanceDTO::from)
                .toList();
    }

    private UUID resolveUserId(String auth0Id) {
        return User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User profile not found"))
                .id;
    }
}
```

### Points d'attention

| Point | Détail |
|---|---|
| `@Transactional` obligatoire | Sur toutes les méthodes — convention du projet |
| Vérification capacité | Uniquement pour `ATTENDING` — `INTERESTED` n'est jamais bloqué |
| Capacité : cas already ATTENDING | Si l'utilisateur est déjà `ATTENDING` et resoumet `ATTENDING`, ne pas le compter deux fois |
| Upsert | Lookup par `(userId, eventId)`, mise à jour du `status` si trouvé — `createdAt` n'est pas re-setté |
| `WebApplicationException` pour 409 | Construit un `Response` 409 avec `ApiErrorResponse` — pas de mapper dédié nécessaire |
| `event.creator.auth0Id` | Requiert que Hibernate charge `creator` — `@ManyToOne(LAZY)` sur `Event.creator`, accès dans la même transaction `@Transactional` donc OK |
| `ApiErrorResponse` import | `ch.unige.events.dto.ApiErrorResponse` — vérifier que le package est correct à la lecture du fichier |

---

## Étape 4 — Correction de `CalendarService.java`

**Fichier :** `backend/src/main/java/ch/unige/events/service/CalendarService.java`

### 4.1 — Modifier les imports

**Retirer :**
```java
import ch.unige.events.entity.Favorite;
```

**Ajouter :**
```java
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
```

### 4.2 — Remplacer intégralement la méthode `generateIcsFeed`

```java
@Transactional
public String generateIcsFeed(UUID calendarToken) {
    User user = User.<User>find("calendarToken", calendarToken)
            .firstResultOptional()
            .orElseThrow(() -> new NotFoundException("Calendar token not found"));

    List<Event> events = Attendance.findAllByUser(user.id).stream()
            .filter(a -> a.status == AttendanceStatus.INTERESTED
                      || a.status == AttendanceStatus.ATTENDING)
            .map(a -> Event.<Event>findByIdOptional(a.eventId))
            .flatMap(Optional::stream)
            .filter(e -> e.status == EventStatus.PUBLISHED)
            .toList();

    return buildIcsContent(events);
}
```

**Vérifier que `Optional` est déjà importé** (`java.util.Optional`) — il l'est dans la version initiale.

---

## Étape 5 — `AttendanceResource.java` (nouveau)

**Fichier :** `backend/src/main/java/ch/unige/events/resource/AttendanceResource.java`

```java
package ch.unige.events.resource;

import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.dto.attendance.AttendanceRequest;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.service.AttendanceService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AttendanceResource {

    private final AttendanceService attendanceService;
    private final SecurityIdentity identity;

    @Inject
    public AttendanceResource(AttendanceService attendanceService,
                              SecurityIdentity identity) {
        this.attendanceService = attendanceService;
        this.identity = identity;
    }

    @POST
    @Path("/{id}/attend")
    @Authenticated
    public Response attend(@PathParam("id") Long id,
                           @Valid AttendanceRequest request) {
        String auth0Id = identity.getPrincipal().getName();
        AttendanceDTO dto = attendanceService.attend(auth0Id, id, request.status());
        return Response.ok(dto).build();
    }

    @DELETE
    @Path("/{id}/attend")
    @Authenticated
    public Response removeAttendance(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        attendanceService.removeAttendance(auth0Id, id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/attendees")
    @Authenticated
    public List<AttendanceDTO> getAttendees(
            @PathParam("id") Long id,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        return attendanceService.getAttendees(auth0Id, id, page, size);
    }
}
```

### Points d'attention

- **Constructor injection** — convention pour les nouveaux fichiers Resource
- **`@Valid`** sur `AttendanceRequest` — valide `@NotNull AttendanceStatus status`
- **`@Produces` + `@Consumes` au niveau classe** — tous les endpoints JSON sauf le DELETE (pas de body)
- **Pas de `@PermitAll`** — tous les endpoints sont `@Authenticated`

---

## Étape 6 — Extension de `UserResource.java`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/UserResource.java`

### 6.1 — Ajouter les imports

```java
import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.service.AttendanceService;
```

### 6.2 — Ajouter l'injection (field injection, cohérent avec le style du fichier)

Dans la zone des `@Inject` existants :

```java
@Inject AttendanceService attendanceService;
```

### 6.3 — Ajouter la méthode (à la fin de la classe, avant la dernière `}`)

```java
    @GET
    @Path("/me/attendances")
    @Authenticated
    public List<AttendanceDTO> getMyAttendances() {
        return attendanceService.getMyAttendances(identity.getPrincipal().getName());
    }
```

---

## Étape 7 — `AttendanceServiceMock.java` (nouveau mock)

**Fichier :** `backend/src/test/java/ch/unige/events/service/AttendanceServiceMock.java`

```java
package ch.unige.events.service;

import ch.unige.events.MockEventFactory;
import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class AttendanceServiceMock extends AttendanceService {

    private final Map<Long, Event> eventsById = new ConcurrentHashMap<>();
    /** Map eventId → AttendanceStatus (une seule attendance par event pour simplifier les tests) */
    private final Map<Long, AttendanceStatus> attendances = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public static volatile boolean forceCapacityConflict = false;
    public static volatile boolean forceNotFoundOnAttend = false;
    public static volatile boolean forceNotFoundOnRemove = false;
    public static volatile boolean forceForbiddenOnGetAttendees = false;

    public void reset() {
        eventsById.clear();
        attendances.clear();
        idSequence.set(1);
        forceCapacityConflict = false;
        forceNotFoundOnAttend = false;
        forceNotFoundOnRemove = false;
        forceForbiddenOnGetAttendees = false;
    }

    public Event seedEvent(String title) {
        Event event = MockEventFactory.build(title, idSequence);
        eventsById.put(event.id, event);
        return event;
    }

    public void seedAttendance(Long eventId, AttendanceStatus status) {
        attendances.put(eventId, status);
    }

    @Override
    public AttendanceDTO attend(String auth0Id, Long eventId, AttendanceStatus status) {
        if (forceNotFoundOnAttend) throw new NotFoundException();
        if (forceCapacityConflict) {
            throw new WebApplicationException(
                    Response.status(Response.Status.CONFLICT)
                            .entity(Map.of("error", "conflict", "message", "Event has reached maximum capacity"))
                            .type(MediaType.APPLICATION_JSON_TYPE)
                            .build());
        }
        if (!eventsById.containsKey(eventId)) throw new NotFoundException();
        attendances.put(eventId, status);
        Attendance a = buildAttendance(eventId, status);
        return AttendanceDTO.from(a);
    }

    @Override
    public void removeAttendance(String auth0Id, Long eventId) {
        if (forceNotFoundOnRemove) throw new NotFoundException();
        if (!attendances.containsKey(eventId)) throw new NotFoundException();
        attendances.remove(eventId);
    }

    @Override
    public List<AttendanceDTO> getAttendees(String auth0Id, Long eventId, int page, int size) {
        if (forceForbiddenOnGetAttendees) throw new ForbiddenException();
        if (!eventsById.containsKey(eventId)) throw new NotFoundException();
        return attendances.entrySet().stream()
                .filter(e -> e.getKey().equals(eventId))
                .map(e -> AttendanceDTO.from(buildAttendance(e.getKey(), e.getValue())))
                .skip((long) page * size)
                .limit(size)
                .toList();
    }

    @Override
    public List<AttendanceDTO> getMyAttendances(String auth0Id) {
        return attendances.entrySet().stream()
                .map(e -> AttendanceDTO.from(buildAttendance(e.getKey(), e.getValue())))
                .toList();
    }

    private Attendance buildAttendance(Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.id = idSequence.getAndIncrement();
        a.userId = java.util.UUID.randomUUID();
        a.eventId = eventId;
        a.status = status;
        a.createdAt = LocalDateTime.now();
        return a;
    }
}
```

---

## Étape 8 — Profils de test mis à jour

### 8.1 — `AttendanceServiceCoverageProfile.java` (nouveau)

**Fichier :** `backend/src/test/java/ch/unige/events/service/AttendanceServiceCoverageProfile.java`

```java
package ch.unige.events.service;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class AttendanceServiceCoverageProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        Map<String, String> overrides = new HashMap<>();
        overrides.put("quarkus.datasource.active", "true");
        overrides.put("quarkus.hibernate-orm.active", "true");
        overrides.put("quarkus.datasource.devservices.enabled", "true");
        overrides.put("quarkus.arc.exclude-types",
                "ch.unige.events.service.FavoriteServiceMock," +
                "ch.unige.events.service.ShareServiceMock," +
                "ch.unige.events.service.CalendarServiceMock," +
                "ch.unige.events.service.AttendanceServiceMock," +
                "ch.unige.events.resource.*");
        return overrides;
    }
}
```

### 8.2 — Mettre à jour `FavoriteServiceCoverageProfile.java`

Ajouter `AttendanceServiceMock` dans `quarkus.arc.exclude-types` :

```java
overrides.put("quarkus.arc.exclude-types",
        "ch.unige.events.service.FavoriteServiceMock," +
        "ch.unige.events.service.ShareServiceMock," +
        "ch.unige.events.service.CalendarServiceMock," +
        "ch.unige.events.service.AttendanceServiceMock," +
        "ch.unige.events.resource.*");
```

### 8.3 — Mettre à jour `CalendarServiceCoverageProfile.java`

Ajouter `AttendanceServiceMock` dans `quarkus.arc.exclude-types` (même valeur que ci-dessus) :

```java
overrides.put("quarkus.arc.exclude-types",
        "ch.unige.events.service.FavoriteServiceMock," +
        "ch.unige.events.service.ShareServiceMock," +
        "ch.unige.events.service.CalendarServiceMock," +
        "ch.unige.events.service.AttendanceServiceMock," +
        "ch.unige.events.resource.*");
```

---

## Étape 9 — `AttendanceServiceCoverageTest.java` (nouveau)

**Fichier :** `backend/src/test/java/ch/unige/events/service/AttendanceServiceCoverageTest.java`

Pattern identique à `FavoriteServiceCoverageTest` et `CalendarServiceCoverageTest`.

```java
package ch.unige.events.service;

import ch.unige.events.dto.attendance.AttendanceDTO;
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(AttendanceServiceCoverageProfile.class)
class AttendanceServiceCoverageTest {

    @Inject
    AttendanceService attendanceService;

    @Inject
    EntityManager entityManager;

    // =========================================================
    // attend — création / upsert
    // =========================================================

    @Test
    @TestTransaction
    void attend_firstTime_createsAttendance() {
        User user = persistUser("auth0|att1", "att1@example.com");
        Event event = persistEvent("Event A", user, EventStatus.PUBLISHED, null);

        AttendanceDTO dto = attendanceService.attend("auth0|att1", event.id, AttendanceStatus.INTERESTED);

        assertNotNull(dto.id());
        assertEquals(AttendanceStatus.INTERESTED, dto.status());
        assertEquals(event.id, dto.eventId());
    }

    @Test
    @TestTransaction
    void attend_secondTime_updatesStatus() {
        User user = persistUser("auth0|att2", "att2@example.com");
        Event event = persistEvent("Event B", user, EventStatus.PUBLISHED, null);

        attendanceService.attend("auth0|att2", event.id, AttendanceStatus.INTERESTED);
        AttendanceDTO second = attendanceService.attend("auth0|att2", event.id, AttendanceStatus.ATTENDING);

        assertEquals(AttendanceStatus.ATTENDING, second.status());
        // Doit toujours n'y avoir qu'une seule inscription
        long count = Attendance.count("userId = ?1 and eventId = ?2", user.id, event.id);
        assertEquals(1, count);
    }

    @Test
    @TestTransaction
    void attend_unknownEvent_throwsNotFound() {
        persistUser("auth0|att3", "att3@example.com");

        assertThrows(NotFoundException.class,
                () -> attendanceService.attend("auth0|att3", 999999L, AttendanceStatus.INTERESTED));
    }

    @Test
    @TestTransaction
    void attend_unknownUser_throwsNotFound() {
        User user = persistUser("auth0|owner", "owner@example.com");
        Event event = persistEvent("Event C", user, EventStatus.PUBLISHED, null);

        assertThrows(NotFoundException.class,
                () -> attendanceService.attend("auth0|nobody", event.id, AttendanceStatus.INTERESTED));
    }

    @Test
    @TestTransaction
    void attend_capacityReached_throwsConflict() {
        User organizer = persistUser("auth0|org1", "org1@example.com");
        Event event = persistEvent("Full Event", organizer, EventStatus.PUBLISHED, 1);

        // Premier utilisateur prend la seule place
        User user1 = persistUser("auth0|u1", "u1@example.com");
        persistAttendance(user1.id, event.id, AttendanceStatus.ATTENDING);

        // Deuxième utilisateur — 409 attendu
        persistUser("auth0|u2", "u2@example.com");
        WebApplicationException ex = assertThrows(WebApplicationException.class,
                () -> attendanceService.attend("auth0|u2", event.id, AttendanceStatus.ATTENDING));
        assertEquals(409, ex.getResponse().getStatus());
    }

    @Test
    @TestTransaction
    void attend_capacityReached_interestedAllowed() {
        User organizer = persistUser("auth0|org2", "org2@example.com");
        Event event = persistEvent("Full Event 2", organizer, EventStatus.PUBLISHED, 1);

        User user1 = persistUser("auth0|u3", "u3@example.com");
        persistAttendance(user1.id, event.id, AttendanceStatus.ATTENDING);

        // INTERESTED n'est pas bloqué par la capacité
        persistUser("auth0|u4", "u4@example.com");
        assertDoesNotThrow(
                () -> attendanceService.attend("auth0|u4", event.id, AttendanceStatus.INTERESTED));
    }

    @Test
    @TestTransaction
    void attend_alreadyAttending_resubmitAttending_notBlockedByCapacity() {
        User organizer = persistUser("auth0|org3", "org3@example.com");
        Event event = persistEvent("Full Event 3", organizer, EventStatus.PUBLISHED, 1);

        User user = persistUser("auth0|u5", "u5@example.com");
        persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

        // L'utilisateur reconfirme ATTENDING — ne doit pas être bloqué (il n'occupe pas de place supplémentaire)
        assertDoesNotThrow(
                () -> attendanceService.attend("auth0|u5", event.id, AttendanceStatus.ATTENDING));
    }

    // =========================================================
    // removeAttendance
    // =========================================================

    @Test
    @TestTransaction
    void removeAttendance_existingAttendance_deletesIt() {
        User user = persistUser("auth0|rem1", "rem1@example.com");
        Event event = persistEvent("Event D", user, EventStatus.PUBLISHED, null);
        persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

        attendanceService.removeAttendance("auth0|rem1", event.id);

        entityManager.flush();
        long count = Attendance.count("userId = ?1 and eventId = ?2", user.id, event.id);
        assertEquals(0, count);
    }

    @Test
    @TestTransaction
    void removeAttendance_notAttending_throwsNotFound() {
        User user = persistUser("auth0|rem2", "rem2@example.com");
        Event event = persistEvent("Event E", user, EventStatus.PUBLISHED, null);

        assertThrows(NotFoundException.class,
                () -> attendanceService.removeAttendance("auth0|rem2", event.id));
    }

    @Test
    @TestTransaction
    void removeAttendance_unknownUser_throwsNotFound() {
        assertThrows(NotFoundException.class,
                () -> attendanceService.removeAttendance("auth0|nobody", 1L));
    }

    // =========================================================
    // getAttendees
    // =========================================================

    @Test
    @TestTransaction
    void getAttendees_byCreator_returnsList() {
        User creator = persistUser("auth0|cr1", "cr1@example.com");
        Event event = persistEvent("Event F", creator, EventStatus.PUBLISHED, null);
        User attendee = persistUser("auth0|at1", "at1@example.com");
        persistAttendance(attendee.id, event.id, AttendanceStatus.ATTENDING);

        List<AttendanceDTO> result = attendanceService.getAttendees("auth0|cr1", event.id, 0, 20);

        assertEquals(1, result.size());
        assertEquals(AttendanceStatus.ATTENDING, result.get(0).status());
    }

    @Test
    @TestTransaction
    void getAttendees_byNonCreator_throwsForbidden() {
        User creator = persistUser("auth0|cr2", "cr2@example.com");
        Event event = persistEvent("Event G", creator, EventStatus.PUBLISHED, null);
        persistUser("auth0|other", "other@example.com");

        assertThrows(ForbiddenException.class,
                () -> attendanceService.getAttendees("auth0|other", event.id, 0, 20));
    }

    @Test
    @TestTransaction
    void getAttendees_unknownEvent_throwsNotFound() {
        persistUser("auth0|cr3", "cr3@example.com");

        assertThrows(NotFoundException.class,
                () -> attendanceService.getAttendees("auth0|cr3", 999999L, 0, 20));
    }

    // =========================================================
    // getMyAttendances
    // =========================================================

    @Test
    @TestTransaction
    void getMyAttendances_withAttendances_returnsList() {
        User user = persistUser("auth0|my1", "my1@example.com");
        Event event = persistEvent("Event H", user, EventStatus.PUBLISHED, null);
        persistAttendance(user.id, event.id, AttendanceStatus.INTERESTED);

        List<AttendanceDTO> result = attendanceService.getMyAttendances("auth0|my1");

        assertEquals(1, result.size());
        assertEquals(AttendanceStatus.INTERESTED, result.get(0).status());
    }

    @Test
    @TestTransaction
    void getMyAttendances_noAttendances_returnsEmpty() {
        persistUser("auth0|my2", "my2@example.com");

        List<AttendanceDTO> result = attendanceService.getMyAttendances("auth0|my2");

        assertTrue(result.isEmpty());
    }

    // =========================================================
    // AttendanceDTO.from — couverture du factory method
    // =========================================================

    @Test
    void attendanceDTO_from_mapsAllFields() {
        Attendance a = new Attendance();
        a.id = 1L;
        a.userId = UUID.randomUUID();
        a.eventId = 42L;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();

        AttendanceDTO dto = AttendanceDTO.from(a);

        assertEquals(1L, dto.id());
        assertEquals(a.userId, dto.userId());
        assertEquals(42L, dto.eventId());
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
        assertEquals(a.createdAt, dto.createdAt());
    }

    // =========================================================
    // Helpers
    // =========================================================

    private User persistUser(String auth0Id, String email) {
        User user = new User();
        user.auth0Id = auth0Id;
        user.email = email;
        user.profilePublic = false;
        user.createdAt = LocalDateTime.now();
        entityManager.persist(user);
        entityManager.flush();
        return user;
    }

    private Event persistEvent(String title, User creator, EventStatus status, Integer capacity) {
        Event event = new Event();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = status;
        event.creator = creator;
        event.capacity = capacity;
        entityManager.persist(event);
        entityManager.flush();
        return event;
    }

    private Attendance persistAttendance(UUID userId, Long eventId, AttendanceStatus status) {
        Attendance a = new Attendance();
        a.userId = userId;
        a.eventId = eventId;
        a.status = status;
        entityManager.persist(a);
        entityManager.flush();
        return a;
    }
}
```

---

## Étape 10 — `AttendanceResourceTest.java` (nouveau)

**Fichier :** `backend/src/test/java/ch/unige/events/resource/AttendanceResourceTest.java`

```java
package ch.unige.events.resource;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.AttendanceStatus;
import ch.unige.events.service.AttendanceServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class AttendanceResourceTest {

    @Inject
    AttendanceServiceMock attendanceServiceMock;

    @BeforeEach
    void setUp() {
        attendanceServiceMock.reset();
    }

    // =========================================================
    // POST /events/{id}/attend
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void attend_authenticated_returns200() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE");

        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"ATTENDING\"}")
                .when().post("/events/{id}/attend", event.id)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("status", equalTo("ATTENDING"))
                .body("eventId", equalTo(event.id.intValue()));
    }

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

    @Test
    void attend_unauthenticated_returns401() {
        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"ATTENDING\"}")
                .when().post("/events/{id}/attend", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void attend_unknownEvent_returns404() {
        AttendanceServiceMock.forceNotFoundOnAttend = true;

        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"ATTENDING\"}")
                .when().post("/events/{id}/attend", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void attend_capacityReached_returns409() {
        Event event = attendanceServiceMock.seedEvent("Full Event");
        AttendanceServiceMock.forceCapacityConflict = true;

        given()
                .contentType(ContentType.JSON)
                .body("{\"status\":\"ATTENDING\"}")
                .when().post("/events/{id}/attend", event.id)
                .then()
                .statusCode(409);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void attend_missingStatus_returns400() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE 3");

        given()
                .contentType(ContentType.JSON)
                .body("{}")
                .when().post("/events/{id}/attend", event.id)
                .then()
                .statusCode(400);
    }

    // =========================================================
    // DELETE /events/{id}/attend
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void removeAttendance_existingAttendance_returns204() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE");
        attendanceServiceMock.seedAttendance(event.id, AttendanceStatus.ATTENDING);

        given()
                .when().delete("/events/{id}/attend", event.id)
                .then()
                .statusCode(204);
    }

    @Test
    void removeAttendance_unauthenticated_returns401() {
        given()
                .when().delete("/events/{id}/attend", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void removeAttendance_notAttending_returns404() {
        AttendanceServiceMock.forceNotFoundOnRemove = true;

        given()
                .when().delete("/events/{id}/attend", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    // =========================================================
    // GET /events/{id}/attendees
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void getAttendees_byCreator_returns200() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE");
        attendanceServiceMock.seedAttendance(event.id, AttendanceStatus.ATTENDING);

        given()
                .when().get("/events/{id}/attendees", event.id)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON);
    }

    @Test
    void getAttendees_unauthenticated_returns401() {
        given()
                .when().get("/events/{id}/attendees", 1L)
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getAttendees_forbidden_returns403() {
        Event event = attendanceServiceMock.seedEvent("Conférence UNIGE");
        AttendanceServiceMock.forceForbiddenOnGetAttendees = true;

        given()
                .when().get("/events/{id}/attendees", event.id)
                .then()
                .statusCode(403)
                .body("error", is("forbidden"));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getAttendees_unknownEvent_returns404() {
        AttendanceServiceMock.forceNotFoundOnAttend = true;

        given()
                .when().get("/events/{id}/attendees", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }
}
```

**Note sur `getAttendees_unknownEvent_returns404` :** Le mock utilise `forceNotFoundOnAttend` comme
flag générique de "not found" — vérifier que `getAttendees` lève bien `NotFoundException` si le flag
est activé dans le mock. Si ce n'est pas le cas dans l'implémentation du mock ci-dessus, ajouter
un `forceNotFoundOnGetAttendees` séparé ou vérifier `eventsById.containsKey(eventId)` dans `getAttendees`.

---

## Étape 11 — Correction de `CalendarServiceCoverageTest.java`

**Fichier :** `backend/src/test/java/ch/unige/events/service/CalendarServiceCoverageTest.java`

### 11.1 — Modifier les imports

**Retirer :**
```java
import ch.unige.events.entity.Favorite;
```

**Ajouter :**
```java
import ch.unige.events.entity.Attendance;
import ch.unige.events.entity.AttendanceStatus;
```

### 11.2 — Remplacer le helper `persistFavorite` par `persistAttendance`

**Supprimer :**
```java
private Favorite persistFavorite(UUID userId, Long eventId) {
    Favorite favorite = new Favorite();
    favorite.userId = userId;
    favorite.eventId = eventId;
    entityManager.persist(favorite);
    entityManager.flush();
    return favorite;
}
```

**Ajouter :**
```java
private Attendance persistAttendance(UUID userId, Long eventId, AttendanceStatus status) {
    Attendance a = new Attendance();
    a.userId = userId;
    a.eventId = eventId;
    a.status = status;
    entityManager.persist(a);
    entityManager.flush();
    return a;
}
```

### 11.3 — Remplacer les cas de test 7, 8, 9

**Remplacer** `generateIcsFeed_withFavorite_containsVevent` par :

```java
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
```

**Remplacer** `generateIcsFeed_emptyFavorites_noVevent` par :

```java
@Test
@TestTransaction
void generateIcsFeed_noAttendance_noVevent() {
    User user = persistUser("auth0|cal6", "cal6@example.com");
    user.calendarToken = UUID.randomUUID();
    entityManager.flush();

    String ics = calendarService.generateIcsFeed(user.calendarToken);

    assertTrue(ics.contains("BEGIN:VCALENDAR"));
    assertFalse(ics.contains("BEGIN:VEVENT"));
}
```

**Remplacer** `generateIcsFeed_draftEventExcluded` par :

```java
@Test
@TestTransaction
void generateIcsFeed_draftEventExcluded() {
    User user = persistUser("auth0|cal7", "cal7@example.com");
    user.calendarToken = UUID.randomUUID();
    entityManager.flush();

    Event event = persistEvent("Brouillon", user, EventStatus.DRAFT);
    persistAttendance(user.id, event.id, AttendanceStatus.ATTENDING);

    String ics = calendarService.generateIcsFeed(user.calendarToken);

    assertFalse(ics.contains("BEGIN:VEVENT"));
}
```

### 11.4 — Ajouter un 11ème cas de test (INTERESTED inclus dans le flux)

Ajouter après les cas existants, avant les tests unitaires `buildIcsContent`/`escapeIcs` :

```java
@Test
@TestTransaction
void generateIcsFeed_interestedStatusIncluded() {
    User user = persistUser("auth0|cal8", "cal8@example.com");
    user.calendarToken = UUID.randomUUID();
    entityManager.flush();

    Event event = persistEvent("Conférence Interested", user, EventStatus.PUBLISHED);
    persistAttendance(user.id, event.id, AttendanceStatus.INTERESTED);

    String ics = calendarService.generateIcsFeed(user.calendarToken);

    assertTrue(ics.contains("BEGIN:VEVENT"));
    assertTrue(ics.contains("SUMMARY:Conférence Interested"));
}
```

**Note :** La méthode `persistEvent` dans `CalendarServiceCoverageTest` prend `(String title, User creator, EventStatus status)` — 3 paramètres. Vérifier la signature et l'adapter si nécessaire.

---

## Étape 12 — Extension de `UserResourceTest.java`

**Fichier :** `backend/src/test/java/ch/unige/events/resource/UserResourceTest.java`

### 12.1 — Ajouter l'import

```java
import ch.unige.events.service.AttendanceServiceMock;
```

### 12.2 — Ajouter l'injection

```java
@Inject AttendanceServiceMock attendanceServiceMock;
```

### 12.3 — Ajouter le reset dans `setUp()`

```java
@BeforeEach
void setUp() {
    userServiceMock.reset();
    favoriteServiceMock.reset();
    // Ajouter :
    attendanceServiceMock.reset();
}
```

### 12.4 — Ajouter 2 nouveaux tests (à la fin de la classe, avant la dernière `}`)

```java
    // --- GET /users/me/attendances ---

    @Test
    @TestSecurity(user = "auth0|alice")
    void getMyAttendances_authenticated_returns200() {
        given()
            .when().get("/users/me/attendances")
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("$", hasSize(0));
    }

    @Test
    void getMyAttendances_unauthenticated_returns401() {
        given()
            .when().get("/users/me/attendances")
            .then()
            .statusCode(401);
    }
```

---

## Étape 13 — Mise à jour de la documentation

### `backend/docs/data-model.md`

**1. Dans la section Favorite — helpers statiques :**
Retirer `findAllByUser(UUID)` — remettre :
```
Helpers statiques : `Favorite.findByUserAndEvent(UUID, Long)`, `Favorite.findByUser(UUID, int, int)`.
```

**2. Ajouter la section `Attendance` (après Favorite) :**

```markdown
### Attendance

Table : `attendances`

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `userId` | `userId` | `UUID` | `user_id` | not null |
| `eventId` | `eventId` | `Long` | `event_id` | not null |
| `status` | `status` | `AttendanceStatus` | `status` | not null, `@Enumerated(STRING)` |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_attendance_user_event` sur `(user_id, event_id)`.

Upsert : un utilisateur n'a qu'une seule inscription par événement — le statut est mis à jour si l'inscription existe.

Helpers statiques : `Attendance.findByEvent(Long, int, int)`, `Attendance.findAllByUser(UUID)`.
```

### `backend/docs/api-contract.md`

**1. Dans la table "Endpoints implémentés", ajouter :**

```
| `POST` | `/events/{id}/attend` | `@Authenticated` | Upsert inscription (INTERESTED/ATTENDING) — 409 si capacité pleine | 200, 401, 404, 409 |
| `DELETE` | `/events/{id}/attend` | `@Authenticated` | Se désinscrire | 204, 401, 404 |
| `GET` | `/events/{id}/attendees` | `@Authenticated` | Liste paginée des inscriptions (créateur uniquement) | 200, 401, 403, 404 |
| `GET` | `/users/me/attendances` | `@Authenticated` | Mes inscriptions (toutes, avec statut) | 200, 401 |
```

**2. Corriger la description de `GET /calendar/{calendarToken}.ics` :**
```
| `GET` | `/calendar/{calendarToken}.ics` | `@PermitAll` | Flux iCalendar des inscriptions explicites (INTERESTED + ATTENDING) | 200, 404 |
```

### `backend/docs/sprint-context.md`

Mettre à jour la section Sprint 4 :

```markdown
- [x] Entité `Favorite` (userId, eventId) — SCRUM-89 ✅
- [x] `POST /events/{id}/favorite`, `DELETE /events/{id}/favorite`, `GET /users/me/favorites` — SCRUM-89 ✅
- [x] `GET /events/{id}/share` + `GET /s/{shortCode}` (shortlink redirect) — SCRUM-89 ✅
- [x] `GET /users/me/calendar-token`, `DELETE /users/me/calendar-token`, `GET /calendar/{calendarToken}.ics` — SCRUM-89-bis ✅
- [ ] Entité `Attendance` (userId, eventId, status) + endpoints — SCRUM-88 (en cours)
  - `POST /events/{id}/attend` (upsert INTERESTED/ATTENDING)
  - `DELETE /events/{id}/attend` (désinscription)
  - `GET /events/{id}/attendees` (créateur uniquement)
  - `GET /users/me/attendances`
```

Mettre à jour la date "Dernière mise à jour" à la date du commit.

---

## Ordre d'implémentation OBLIGATOIRE

Respecter cet ordre impérativement — chaque étape dépend de la précédente :

```
1.  openapi/openapi.yaml               ← PREMIER, toujours
2.  entity/AttendanceStatus.java       ← enum requis par Attendance
3.  entity/Attendance.java             ← entité principale, requise par tout le reste
4.  entity/Favorite.java               ← MODIFIER : retirer findAllByUser
5.  dto/attendance/AttendanceDTO.java  ← record requis par AttendanceService
6.  dto/attendance/AttendanceRequest.java
7.  service/AttendanceService.java     ← service principal
8.  service/CalendarService.java       ← MODIFIER : corriger generateIcsFeed (Attendance dispo)
9.  resource/AttendanceResource.java   ← resource principale
10. resource/UserResource.java         ← MODIFIER : injection + GET /me/attendances
11. test/.../AttendanceServiceMock.java
12. test/.../AttendanceServiceCoverageProfile.java
13. test/.../AttendanceServiceCoverageTest.java
14. test/.../FavoriteServiceCoverageProfile.java  ← MODIFIER : ajouter AttendanceServiceMock
15. test/.../CalendarServiceCoverageProfile.java  ← MODIFIER : ajouter AttendanceServiceMock
16. test/.../CalendarServiceCoverageTest.java     ← MODIFIER : persistFavorite → persistAttendance + 1 nouveau test
17. test/.../AttendanceResourceTest.java
18. test/.../UserResourceTest.java                ← MODIFIER : AttendanceServiceMock + 2 tests
19. backend/docs/data-model.md
20. backend/docs/api-contract.md
21. backend/docs/sprint-context.md
```

---

## Critères de validation

### Fonctionnels

- `POST /events/{id}/attend` avec `{"status":"ATTENDING"}` → 200 + AttendanceDTO
- Double `POST` avec statut différent → met à jour le statut (1 seule ligne en DB)
- `POST` sur event à capacité pleine avec `ATTENDING` → 409 + `{"error":"conflict"}`
- `POST` sur event à capacité pleine avec `INTERESTED` → 200 (non bloqué)
- Utilisateur déjà `ATTENDING` qui reconfirme `ATTENDING` → 200 (pas de 409)
- `DELETE /events/{id}/attend` → 204
- `DELETE` sans inscription préalable → 404
- `GET /events/{id}/attendees` par le créateur → 200 + liste
- `GET /events/{id}/attendees` par un autre utilisateur → 403
- `GET /users/me/attendances` → 200 + liste avec statut INTERESTED ou ATTENDING
- `GET /calendar/{token}.ics` → ICS des events PUBLISHED avec attendance, sans les favoris seuls
- Tous les endpoints Bearer-auth → 401 sans token

### Qualité SonarCloud

- Couverture ≥ 80% (15 tests service + 11 tests resource + 2 tests UserResource + 11 tests CalendarService corrigés)
- Duplication ≤ 3%
- Security / Reliability / Maintainability Rating A
