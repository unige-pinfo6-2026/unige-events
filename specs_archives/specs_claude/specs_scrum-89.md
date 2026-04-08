# Specs SCRUM-89 — Entité Favorite + endpoints favoris + endpoint partage (shortlink)

> **Branche :** `feature/s4-favorites-share`
> **Sprint :** 4 — Backend Tâche 2/2 · Epic 4 – Engagement & Interaction
> **Prérequis :** main à jour · SCRUM-88 (Attendance) peut être développé en parallèle — aucun conflit
> **Règle d'or :** Modifier `openapi/openapi.yaml` EN PREMIER, puis coder Entity → DTO → Service → Resource → Tests → Docs

---

## Contexte

### Ce qui existe déjà (ne pas retoucher sauf indication contraire)

| Fichier | État |
|---|---|
| `backend/src/main/java/.../entity/Event.java` | Complet — à modifier uniquement pour ajouter le champ `shareCode` |
| `backend/src/main/java/.../entity/User.java` | Complet — champ `id` (UUID) utilisé comme `userId` dans `Favorite` |
| `backend/src/main/java/.../dto/event/EventDTO.java` | Record complet avec factory `EventDTO.from(Event)` — retourné par `GET /users/me/favorites` |
| `backend/src/main/java/.../resource/EventResource.java` | Complet — CRUD `/events` |
| `backend/src/main/java/.../resource/UserResource.java` | Complet — à étendre uniquement pour `GET /me/favorites` |
| `backend/src/main/java/.../service/EventService.java` | Complet — ne pas toucher |
| `backend/src/test/java/.../service/EventServiceMock.java` | Mock in-memory de référence — s'en inspirer pour `FavoriteServiceMock` |
| `openapi/openapi.yaml` | Paths `/events/{id}/favorite`, `/users/me/favorites` présents en TODO Sprint 4 — à compléter. `/events/{id}/share` et `/s/{shortCode}` absents — à ajouter |

### Ce qui est à créer

| Fichier | Action |
|---|---|
| `backend/src/main/java/.../entity/Favorite.java` | Nouveau |
| `backend/src/main/java/.../dto/favorite/FavoriteDTO.java` | Nouveau record |
| `backend/src/main/java/.../dto/event/ShareResponse.java` | Nouveau record |
| `backend/src/main/java/.../service/FavoriteService.java` | Nouveau |
| `backend/src/main/java/.../service/ShareService.java` | Nouveau |
| `backend/src/main/java/.../resource/FavoriteResource.java` | Nouveau — `@Path("/events")` |
| `backend/src/main/java/.../resource/RedirectResource.java` | Nouveau — `@Path("/s")` |
| `backend/src/test/java/.../service/FavoriteServiceMock.java` | Nouveau mock in-memory |
| `backend/src/test/java/.../service/ShareServiceMock.java` | Nouveau mock in-memory |
| `backend/src/test/java/.../resource/FavoriteResourceTest.java` | Nouveau — 12 cas de test |

### Ce qui est à modifier

| Fichier | Modification |
|---|---|
| `backend/src/main/java/.../entity/Event.java` | Ajout champ `shareCode` (String, nullable, unique) |
| `backend/src/main/java/.../resource/UserResource.java` | Ajout `GET /me/favorites` |
| `openapi/openapi.yaml` | Compléter todos + ajouter share + redirect |
| `backend/src/main/resources/application.properties` | Ajout `app.frontend.url` |

### Architecture des endpoints

| Endpoint | Classe | Auth |
|---|---|---|
| `POST /api/events/{id}/favorite` | `FavoriteResource` | `@Authenticated` |
| `DELETE /api/events/{id}/favorite` | `FavoriteResource` | `@Authenticated` |
| `GET /api/events/{id}/share` | `FavoriteResource` | `@Authenticated` |
| `GET /api/users/me/favorites` | `UserResource` (existant, étendu) | `@Authenticated` |
| `GET /api/s/{shortCode}` | `RedirectResource` | `@PermitAll` |

### Note sur SCRUM-88 (Attendance)

SCRUM-88 crée `Attendance.java`, `AttendanceService.java`, `AttendanceResource.java` — fichiers entièrement nouveaux, aucun conflit avec SCRUM-89. Les deux tâches peuvent être développées en parallèle sur des branches séparées.

### Note sur SCRUM-91 (Frontend)

SCRUM-91 consomme exactement les endpoints définis ici. Le contrat `openapi.yaml` est la source de vérité partagée. S'assurer que les noms de champs JSON (`shareUrl`, `shortCode`, `createdAt`) correspondent exactement à ce que le frontend attend.

---

## Étape 0 — `openapi/openapi.yaml` (OBLIGATOIRE EN PREMIER)

**Fichier :** `/workspace/openapi/openapi.yaml`

### 0.1 — Ajouter le schéma `ShareResponse` dans `components/schemas`

```yaml
    ShareResponse:
      type: object
      description: Résultat de GET /events/{id}/share — URL de partage et code court
      properties:
        shareUrl:
          type: string
          format: uri
          description: URL complète vers la page de l'événement (frontend)
        shortCode:
          type: string
          description: Code court alphanumérique 8 caractères — stable (idempotent)
      required: [shareUrl, shortCode]
      example:
        shareUrl: "https://10.25.10.136.nip.io/events/42"
        shortCode: "aB3xZ9mQ"
```

### 0.2 — Compléter `/users/me/favorites` (remplacer le TODO existant)

Localiser le path `/users/me/favorites` (marqué `# TODO: Sprint 4`) et le remplacer par :

```yaml
  /users/me/favorites:
    get:
      summary: Favoris de l'utilisateur connecté
      description: Retourne la liste paginée des événements mis en favoris par l'utilisateur authentifié.
      operationId: getMyFavorites
      tags: [users, favorites]
      security:
        - BearerAuth: []
      parameters:
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
          description: Événements favoris (tableau vide si aucun favori — jamais 404)
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/Event'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.3 — Compléter `/events/{id}/favorite` (remplacer le TODO existant)

Localiser le path `/events/{id}/favorite` (marqué `# TODO: Sprint 4`) et le remplacer par :

```yaml
  /events/{id}/favorite:
    post:
      summary: Ajouter un événement aux favoris (idempotent)
      description: |
        Ajoute l'événement aux favoris de l'utilisateur connecté.
        **Idempotent** : retourne 200 même si l'événement est déjà en favori.
      operationId: addFavorite
      tags: [events, favorites]
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
        '200':
          description: Ajouté aux favoris (ou déjà en favori — idempotent)
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

    delete:
      summary: Retirer un événement des favoris
      operationId: removeFavorite
      tags: [events, favorites]
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
          description: Retiré des favoris
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Favori introuvable (l'événement n'était pas en favori)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.4 — Ajouter `/events/{id}/share` (nouveau path)

Ajouter après `/events/{id}/favorite` :

```yaml
  /events/{id}/share:
    get:
      summary: Obtenir le lien de partage d'un événement
      description: |
        Retourne l'URL de partage complète et le code court alphanumérique de l'événement.
        Le shortCode est généré une fois et stocké sur l'entité Event (idempotent — deux appels
        successifs retournent le même shortCode).
      operationId: getEventShareInfo
      tags: [events]
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
        '200':
          description: Informations de partage
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ShareResponse'
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
```

### 0.5 — Ajouter `/s/{shortCode}` (nouveau path)

Ajouter en fin de section `paths` :

```yaml
  /s/{shortCode}:
    get:
      summary: Redirection courte vers la page d'un événement
      description: |
        Redirige (302) vers la page frontend de l'événement correspondant au shortCode.
        Endpoint public — aucun token requis (conçu pour être partagé par lien).
      operationId: redirectByShortCode
      tags: [events]
      security: []
      parameters:
        - name: shortCode
          in: path
          required: true
          schema:
            type: string
      responses:
        '302':
          description: Redirection vers la page de l'événement
          headers:
            Location:
              schema:
                type: string
                format: uri
              description: URL frontend de l'événement (ex. https://10.25.10.136.nip.io/events/42)
        '404':
          description: shortCode inconnu
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

---

## Étape 1 — Entités

### 1.1 — `Favorite.java` (nouvelle entité)

**Fichier :** `backend/src/main/java/ch/unige/events/entity/Favorite.java`

```java
package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(
    name = "favorites",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_favorite_user_event",
        columnNames = {"user_id", "event_id"}
    )
)
public class Favorite extends PanacheEntity {

    @Column(name = "user_id", nullable = false)
    public UUID userId;

    @Column(name = "event_id", nullable = false)
    public Long eventId;

    @Column(updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
    }

    public static Optional<Favorite> findByUserAndEvent(UUID userId, Long eventId) {
        return find("userId = ?1 and eventId = ?2", userId, eventId).firstResultOptional();
    }

    public static java.util.List<Favorite> findByUser(UUID userId, int page, int size) {
        return find("userId = ?1 order by createdAt desc", userId)
                .page(page, size)
                .list();
    }
}
```

**Points à respecter :**
- `PanacheEntity` (Long PK) — cohérent avec `Event`
- `userId` (UUID) stocké en colonne `user_id` — **pas de @ManyToOne sur User** pour éviter le chargement inutile
- `eventId` (Long) stocké en colonne `event_id` — **pas de @ManyToOne sur Event** pour la même raison
- Contrainte unique nommée `uq_favorite_user_event` sur les deux colonnes — Hibernate la crée en mode `update`
- `createdAt` en `updatable = false`, initialisé via `@PrePersist`
- Helpers statiques Panache : `findByUserAndEvent` (lookup idempotence) et `findByUser` (liste paginée)
- **Suppression physique autorisée** — `Favorite` n'est pas un Event, pas de soft-delete ici

### 1.2 — Modification de `Event.java` (ajout `shareCode`)

**Fichier :** `backend/src/main/java/ch/unige/events/entity/Event.java`

Ajouter le champ suivant après le champ `capacity` existant :

```java
    @Column(unique = true)
    public String shareCode;
```

Et ajouter un helper statique à la fin de la classe :

```java
    public static Optional<Event> findByShareCode(String shareCode) {
        return find("shareCode", shareCode).firstResultOptional();
    }
```

**Points à respecter :**
- `shareCode` est nullable (null jusqu'au premier appel à `GET /events/{id}/share`)
- `unique = true` — Hibernate crée l'index automatiquement en mode `update`
- Le helper Panache `findByShareCode` est utilisé par `ShareService.resolveByShortCode()`

---

## Étape 2 — DTOs

### 2.1 — `FavoriteDTO.java`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/favorite/FavoriteDTO.java`

```java
package ch.unige.events.dto.favorite;

import ch.unige.events.entity.Favorite;

import java.time.LocalDateTime;
import java.util.UUID;

public record FavoriteDTO(
        Long id,
        UUID userId,
        Long eventId,
        LocalDateTime createdAt
) {
    public static FavoriteDTO from(Favorite favorite) {
        return new FavoriteDTO(
                favorite.id,
                favorite.userId,
                favorite.eventId,
                favorite.createdAt
        );
    }
}
```

**Note :** Les endpoints publics retournent `EventDTO` (pas `FavoriteDTO`) pour exposer les données complètes de l'événement. `FavoriteDTO` est un DTO interne qui peut servir pour de futurs endpoints (ex. date d'ajout aux favoris).

### 2.2 — `ShareResponse.java`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/event/ShareResponse.java`

```java
package ch.unige.events.dto.event;

public record ShareResponse(
        String shareUrl,
        String shortCode
) {}
```

---

## Étape 3 — Services

### 3.1 — `FavoriteService.java`

**Fichier :** `backend/src/main/java/ch/unige/events/service/FavoriteService.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FavoriteService {

    @Transactional
    public void addFavorite(String auth0Id, Long eventId) {
        // Vérifier que l'événement existe
        Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        UUID userId = resolveUserId(auth0Id);

        // Idempotent : ne rien faire si le favori existe déjà
        boolean alreadyExists = Favorite.findByUserAndEvent(userId, eventId).isPresent();
        if (alreadyExists) {
            return;
        }

        Favorite favorite = new Favorite();
        favorite.userId = userId;
        favorite.eventId = eventId;
        favorite.persist();
    }

    @Transactional
    public void removeFavorite(String auth0Id, Long eventId) {
        UUID userId = resolveUserId(auth0Id);

        Favorite favorite = Favorite.findByUserAndEvent(userId, eventId)
                .orElseThrow(NotFoundException::new);

        favorite.delete();
    }

    @Transactional
    public List<EventDTO> getFavorites(String auth0Id, int page, int size) {
        UUID userId = resolveUserId(auth0Id);

        return Favorite.findByUser(userId, page, size).stream()
                .map(f -> Event.<Event>findByIdOptional(f.eventId))
                .filter(opt -> opt.isPresent())
                .map(opt -> EventDTO.from(opt.get()))
                .toList();
    }

    private UUID resolveUserId(String auth0Id) {
        return User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(NotFoundException::new)
                .id;
    }
}
```

**Points à respecter :**
- `@ApplicationScoped` + `@Transactional` sur chaque méthode de mutation
- `addFavorite` est **idempotent** : si le favori existe déjà, la méthode retourne sans erreur ni exception
- `removeFavorite` lève `NotFoundException` si le favori n'existe pas (différent de l'add — DELETE n'est pas idempotent)
- `getFavorites` filtre silencieusement les événements introuvables (cas de cohérence différée)
- `resolveUserId` : lookup du User par `auth0Id` pour obtenir son UUID — lève `NotFoundException` si le profil n'existe pas encore (cas théorique post-provisionnement)
- Aucune logique dans la Resource — toute la logique métier est ici

### 3.2 — `ShareService.java`

**Fichier :** `backend/src/main/java/ch/unige/events/service/ShareService.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.event.ShareResponse;
import ch.unige.events.entity.Event;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;

@ApplicationScoped
public class ShareService {

    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int CODE_LENGTH = 8;
    private final SecureRandom random = new SecureRandom();

    @ConfigProperty(name = "app.frontend.url", defaultValue = "https://10.25.10.136.nip.io")
    String frontendUrl;

    @Transactional
    public ShareResponse getShareInfo(Long eventId) {
        Event event = Event.<Event>findByIdOptional(eventId)
                .orElseThrow(NotFoundException::new);

        if (event.shareCode == null) {
            event.shareCode = generateCode();
        }

        String shareUrl = frontendUrl + "/events/" + eventId;
        return new ShareResponse(shareUrl, event.shareCode);
    }

    @Transactional
    public Event resolveByShortCode(String shortCode) {
        return Event.findByShareCode(shortCode)
                .orElseThrow(NotFoundException::new);
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
```

**Points à respecter :**
- `@ApplicationScoped` + `@Transactional` sur les deux méthodes (mutation potentielle dans `getShareInfo`)
- `getShareInfo` est **idempotent** : génère le code uniquement si absent, le réutilise sinon
- `SecureRandom` pour la génération du shortCode (pas `Math.random()`) — Security Rating A
- `frontendUrl` injecté via `@ConfigProperty` — configurable sans recompilation
- `generateCode()` : 62 caractères possibles × 8 positions = ~218 milliards de combinaisons
- `resolveByShortCode` lève `NotFoundException` si le code est inconnu → mapper existant retourne 404
- Constructor injection non requise ici car `@ConfigProperty` est injecté par CDI field injection (exception acceptée pour les configs)

**Ajouter dans `application.properties` :**

```properties
# URL de base du frontend (utilisée pour la génération des liens de partage)
app.frontend.url=https://10.25.10.136.nip.io
```

---

## Étape 4 — Resources

### 4.1 — `FavoriteResource.java`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/FavoriteResource.java`

```java
package ch.unige.events.resource;

import ch.unige.events.dto.event.ShareResponse;
import ch.unige.events.service.FavoriteService;
import ch.unige.events.service.ShareService;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FavoriteResource {

    private final FavoriteService favoriteService;
    private final ShareService shareService;
    private final SecurityIdentity identity;

    @Inject
    public FavoriteResource(FavoriteService favoriteService,
                             ShareService shareService,
                             SecurityIdentity identity) {
        this.favoriteService = favoriteService;
        this.shareService = shareService;
        this.identity = identity;
    }

    @POST
    @Path("/{id}/favorite")
    @Authenticated
    public Response addFavorite(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        favoriteService.addFavorite(auth0Id, id);
        return Response.ok().build();
    }

    @DELETE
    @Path("/{id}/favorite")
    @Authenticated
    public Response removeFavorite(@PathParam("id") Long id) {
        String auth0Id = identity.getPrincipal().getName();
        favoriteService.removeFavorite(auth0Id, id);
        return Response.noContent().build();
    }

    @GET
    @Path("/{id}/share")
    @Authenticated
    public Response getShareInfo(@PathParam("id") Long id) {
        ShareResponse shareResponse = shareService.getShareInfo(id);
        return Response.ok(shareResponse).build();
    }
}
```

**Points à respecter :**
- Constructor injection pour les trois dépendances : `FavoriteService`, `ShareService`, `SecurityIdentity`
- `@Path("/events")` — cohabite avec `EventResource` (`@Path("/events")`) sans conflit JAX-RS : les deux classes enregistrent des méthodes sur des sous-paths différents (`/{id}/favorite`, `/{id}/share` vs `/`, `/{id}`, `/{id}/publish`, etc.)
- Aucune logique métier dans la Resource — tout délégué au Service
- `addFavorite` retourne `200 OK` (pas `204`) — idempotent, la réponse vide est cohérente avec l'OpenAPI
- `removeFavorite` retourne `204 No Content`
- `getShareInfo` retourne `200 OK` + `ShareResponse`

### 4.2 — Modification de `UserResource.java` (ajout `GET /me/favorites`)

Ajouter dans `UserResource.java` les imports nécessaires et la méthode suivante, après `uploadImage` :

**Imports à ajouter :**
```java
import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.service.FavoriteService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
```

**Injection à ajouter dans la classe (field injection, cohérent avec le style existant de `UserResource`) :**
```java
    @Inject FavoriteService favoriteService;
```

**Méthode à ajouter :**
```java
    @GET
    @Path("/me/favorites")
    @Authenticated
    public List<EventDTO> getMyFavorites(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        return favoriteService.getFavorites(auth0Id, page, size);
    }
```

**Note :** `UserResource` utilise déjà le field injection (`@Inject SecurityIdentity identity`) — on suit le style existant de ce fichier pour `FavoriteService`. Ne pas réécrire `UserResource` en constructor injection pour cette seule addition (hors scope).

### 4.3 — `RedirectResource.java`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/RedirectResource.java`

```java
package ch.unige.events.resource;

import ch.unige.events.entity.Event;
import ch.unige.events.service.ShareService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;

@Path("/s")
@Produces(MediaType.APPLICATION_JSON)
public class RedirectResource {

    private final ShareService shareService;

    @ConfigProperty(name = "app.frontend.url", defaultValue = "https://10.25.10.136.nip.io")
    String frontendUrl;

    @Inject
    public RedirectResource(ShareService shareService) {
        this.shareService = shareService;
    }

    @GET
    @Path("/{shortCode}")
    @PermitAll
    public Response redirect(@PathParam("shortCode") String shortCode) {
        Event event = shareService.resolveByShortCode(shortCode);
        URI location = URI.create(frontendUrl + "/events/" + event.id);
        return Response.status(Response.Status.FOUND).location(location).build();
    }
}
```

**Points à respecter :**
- `@Path("/s")` — path court pour les liens partagés, accessible sans auth (`@PermitAll`)
- `Response.Status.FOUND` = 302 — redirection temporaire (pas 301 permanent)
- `shareService.resolveByShortCode()` lève `NotFoundException` si le code est inconnu → mapper existant retourne 404
- Constructor injection sur `ShareService`
- `frontendUrl` en field injection `@ConfigProperty` — même exception que dans `ShareService`

---

## Étape 5 — Mocks de test

### 5.1 — `FavoriteServiceMock.java`

**Fichier :** `backend/src/test/java/ch/unige/events/service/FavoriteServiceMock.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.event.EventDTO;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class FavoriteServiceMock extends FavoriteService {

    /** Map eventId → Event seeded */
    private final Map<Long, Event> eventsById = new ConcurrentHashMap<>();
    /** Set de (userId_mock, eventId) représentant les favoris en mémoire */
    private final Set<Long> favoritedEventIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong idSequence = new AtomicLong(1);

    public static volatile boolean forceNotFoundOnAdd = false;
    public static volatile boolean forceNotFoundOnRemove = false;

    public void reset() {
        eventsById.clear();
        favoritedEventIds.clear();
        idSequence.set(1);
        forceNotFoundOnAdd = false;
        forceNotFoundOnRemove = false;
    }

    /**
     * Seed un événement en mémoire pour les tests.
     * Retourne l'Event pour permettre à l'appelant d'accéder à son id.
     */
    public Event seedEvent(String title) {
        User creator = new User();
        creator.id = UUID.randomUUID();
        creator.auth0Id = "auth0|seed-creator";

        Event event = new Event();
        event.id = idSequence.getAndIncrement();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = EventStatus.PUBLISHED;
        event.creator = creator;
        event.createdAt = LocalDateTime.now();
        event.updatedAt = LocalDateTime.now();

        eventsById.put(event.id, event);
        return event;
    }

    /** Ajoute un eventId directement dans la liste des favoris (pour les tests de GET favorites) */
    public void seedFavorite(Long eventId) {
        favoritedEventIds.add(eventId);
    }

    @Override
    public void addFavorite(String auth0Id, Long eventId) {
        if (forceNotFoundOnAdd) throw new NotFoundException();
        if (!eventsById.containsKey(eventId)) throw new NotFoundException();
        // Idempotent — pas d'exception si déjà présent
        favoritedEventIds.add(eventId);
    }

    @Override
    public void removeFavorite(String auth0Id, Long eventId) {
        if (forceNotFoundOnRemove) throw new NotFoundException();
        if (!favoritedEventIds.remove(eventId)) throw new NotFoundException();
    }

    @Override
    public List<EventDTO> getFavorites(String auth0Id, int page, int size) {
        return favoritedEventIds.stream()
                .map(eventsById::get)
                .filter(Objects::nonNull)
                .skip((long) page * size)
                .limit(size)
                .map(EventDTO::from)
                .toList();
    }
}
```

### 5.2 — `ShareServiceMock.java`

**Fichier :** `backend/src/test/java/ch/unige/events/service/ShareServiceMock.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.event.ShareResponse;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventCategory;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.User;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Mock
@ApplicationScoped
public class ShareServiceMock extends ShareService {

    private final Map<Long, Event> eventsById = new ConcurrentHashMap<>();
    private final Map<String, Long> codeToEventId = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public void reset() {
        eventsById.clear();
        codeToEventId.clear();
        idSequence.set(1);
    }

    public Event seedEvent(String title) {
        User creator = new User();
        creator.id = UUID.randomUUID();
        creator.auth0Id = "auth0|seed-creator";

        Event event = new Event();
        event.id = idSequence.getAndIncrement();
        event.title = title;
        event.location = "Uni Mail";
        event.startDate = LocalDateTime.now().plusDays(1);
        event.endDate = LocalDateTime.now().plusDays(2);
        event.category = EventCategory.ACADEMIC;
        event.status = EventStatus.PUBLISHED;
        event.creator = creator;
        event.createdAt = LocalDateTime.now();
        event.updatedAt = LocalDateTime.now();

        eventsById.put(event.id, event);
        return event;
    }

    @Override
    public ShareResponse getShareInfo(Long eventId) {
        Event event = eventsById.get(eventId);
        if (event == null) throw new NotFoundException();

        if (event.shareCode == null) {
            event.shareCode = "testCode" + eventId;
            codeToEventId.put(event.shareCode, eventId);
        }

        String shareUrl = "https://10.25.10.136.nip.io/events/" + eventId;
        return new ShareResponse(shareUrl, event.shareCode);
    }

    @Override
    public Event resolveByShortCode(String shortCode) {
        Long eventId = codeToEventId.get(shortCode);
        if (eventId == null) throw new NotFoundException();
        return eventsById.get(eventId);
    }
}
```

---

## Étape 6 — Tests

### Stratégie

Même pattern que `EventResourceTest` / `EventSearchResourceTest` :
- `FavoriteServiceMock` et `ShareServiceMock` étendent leurs services respectifs avec `@Mock @ApplicationScoped` (CDI override en test)
- Stockage in-memory — aucun accès DB
- `FavoriteResourceTest` injecte les deux mocks, appelle `reset()` dans `@BeforeEach`
- RestAssured teste les endpoints HTTP complets (`/events/{id}/favorite`, `/events/{id}/share`, `/s/{shortCode}`)

### `FavoriteResourceTest.java`

**Fichier :** `backend/src/test/java/ch/unige/events/resource/FavoriteResourceTest.java`

```java
package ch.unige.events.resource;

import ch.unige.events.entity.Event;
import ch.unige.events.service.FavoriteServiceMock;
import ch.unige.events.service.ShareServiceMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

@QuarkusTest
class FavoriteResourceTest {

    @Inject
    FavoriteServiceMock favoriteServiceMock;

    @Inject
    ShareServiceMock shareServiceMock;

    @BeforeEach
    void setUp() {
        favoriteServiceMock.reset();
        shareServiceMock.reset();
    }

    // =========================================================
    // POST /events/{id}/favorite
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void addFavorite_existingEvent_returns200() {
        Event event = favoriteServiceMock.seedEvent("Conférence Java");

        given()
                .when().post("/events/{id}/favorite", event.id)
                .then()
                .statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void addFavorite_alreadyFavorited_returns200Idempotent() {
        Event event = favoriteServiceMock.seedEvent("Conférence Java");
        // Premier ajout
        given().when().post("/events/{id}/favorite", event.id).then().statusCode(200);
        // Deuxième ajout — doit aussi retourner 200 sans erreur
        given().when().post("/events/{id}/favorite", event.id).then().statusCode(200);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void addFavorite_unknownEvent_returns404() {
        FavoriteServiceMock.forceNotFoundOnAdd = true;

        given()
                .when().post("/events/{id}/favorite", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    @Test
    void addFavorite_unauthenticated_returns401() {
        given()
                .when().post("/events/{id}/favorite", 1L)
                .then()
                .statusCode(401);
    }

    // =========================================================
    // DELETE /events/{id}/favorite
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void removeFavorite_existingFavorite_returns204() {
        Event event = favoriteServiceMock.seedEvent("Conférence Java");
        favoriteServiceMock.seedFavorite(event.id);

        given()
                .when().delete("/events/{id}/favorite", event.id)
                .then()
                .statusCode(204);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void removeFavorite_notFavorited_returns404() {
        FavoriteServiceMock.forceNotFoundOnRemove = true;

        given()
                .when().delete("/events/{id}/favorite", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    @Test
    void removeFavorite_unauthenticated_returns401() {
        given()
                .when().delete("/events/{id}/favorite", 1L)
                .then()
                .statusCode(401);
    }

    // =========================================================
    // GET /events/{id}/share
    // =========================================================

    @Test
    @TestSecurity(user = "auth0|alice")
    void getShareInfo_existingEvent_returnsShareUrlAndCode() {
        Event event = shareServiceMock.seedEvent("Conférence Java");

        given()
                .when().get("/events/{id}/share", event.id)
                .then()
                .statusCode(200)
                .contentType(ContentType.JSON)
                .body("shareUrl", containsString("/events/" + event.id))
                .body("shortCode", notNullValue())
                .body("shortCode", not(emptyString()));
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getShareInfo_calledTwice_returnsSameShortCode() {
        Event event = shareServiceMock.seedEvent("Conférence Java");

        // Premier appel — génère le code
        String code1 = given()
                .when().get("/events/{id}/share", event.id)
                .then().statusCode(200)
                .extract().path("shortCode");

        // Deuxième appel — doit retourner le même code (idempotent)
        String code2 = given()
                .when().get("/events/{id}/share", event.id)
                .then().statusCode(200)
                .extract().path("shortCode");

        org.junit.jupiter.api.Assertions.assertEquals(code1, code2);
    }

    @Test
    @TestSecurity(user = "auth0|alice")
    void getShareInfo_unknownEvent_returns404() {
        given()
                .when().get("/events/{id}/share", 999L)
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }

    @Test
    void getShareInfo_unauthenticated_returns401() {
        given()
                .when().get("/events/{id}/share", 1L)
                .then()
                .statusCode(401);
    }

    // =========================================================
    // GET /s/{shortCode} (RedirectResource)
    // =========================================================

    @Test
    void redirect_validShortCode_returns302() {
        Event event = shareServiceMock.seedEvent("Conférence Java");
        // Seed le code via getShareInfo
        shareServiceMock.getShareInfo(event.id);

        given()
                .redirects().follow(false)   // Ne pas suivre la redirection — on teste le 302
                .when().get("/s/{code}", event.shareCode)
                .then()
                .statusCode(302)
                .header("Location", containsString("/events/" + event.id));
    }

    @Test
    void redirect_unknownShortCode_returns404() {
        given()
                .when().get("/s/{code}", "inexistant")
                .then()
                .statusCode(404)
                .body("error", is("not_found"));
    }
}
```

**Récapitulatif des 12 cas de test :**

| Test | Endpoint | Comportement vérifié |
|---|---|---|
| `addFavorite_existingEvent_returns200` | `POST /{id}/favorite` | 200 si l'événement existe |
| `addFavorite_alreadyFavorited_returns200Idempotent` | `POST /{id}/favorite` | 200 au deuxième appel — idempotence |
| `addFavorite_unknownEvent_returns404` | `POST /{id}/favorite` | 404 si l'événement n'existe pas |
| `addFavorite_unauthenticated_returns401` | `POST /{id}/favorite` | 401 sans token |
| `removeFavorite_existingFavorite_returns204` | `DELETE /{id}/favorite` | 204 si le favori existe |
| `removeFavorite_notFavorited_returns404` | `DELETE /{id}/favorite` | 404 si non en favori |
| `removeFavorite_unauthenticated_returns401` | `DELETE /{id}/favorite` | 401 sans token |
| `getShareInfo_existingEvent_returnsShareUrlAndCode` | `GET /{id}/share` | 200 + shareUrl + shortCode non vide |
| `getShareInfo_calledTwice_returnsSameShortCode` | `GET /{id}/share` | Idempotence du shortCode |
| `getShareInfo_unknownEvent_returns404` | `GET /{id}/share` | 404 si événement inexistant |
| `getShareInfo_unauthenticated_returns401` | `GET /{id}/share` | 401 sans token |
| `redirect_validShortCode_returns302` | `GET /s/{shortCode}` | 302 + Location header correct |
| `redirect_unknownShortCode_returns404` | `GET /s/{shortCode}` | 404 si code inconnu |

> **Note SonarCloud :** 12 tests couvrant les 3 branches conditionnelles clés (`addFavorite` idempotence, `getShareInfo` génération/réutilisation code, `resolveByShortCode` trouvé/non-trouvé). Viser **>80% coverage** sur `FavoriteService`, `ShareService`, `FavoriteResource`, `RedirectResource`.

---

## Étape 7 — Documentation

### `backend/docs/api-contract.md`

Ajouter dans le tableau **Endpoints implémentés** :

```markdown
| `POST` | `/events/{id}/favorite` | `@Authenticated` | Ajouter aux favoris (idempotent — 200 même si déjà favori) | 200, 401, 404 |
| `DELETE` | `/events/{id}/favorite` | `@Authenticated` | Retirer des favoris | 204, 401, 404 |
| `GET` | `/users/me/favorites` | `@Authenticated` | Liste paginée des événements favoris | 200, 401 |
| `GET` | `/events/{id}/share` | `@Authenticated` | Obtenir shareUrl + shortCode (idempotent) | 200, 401, 404 |
| `GET` | `/s/{shortCode}` | `@PermitAll` | Redirection 302 vers la page de l'événement | 302, 404 |
```

### `backend/docs/data-model.md`

Ajouter la section suivante après la section **Event** :

```markdown
### Favorite

Table : `favorites`

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity` |
| `userId` | `userId` | `UUID` | `user_id` | not null |
| `eventId` | `eventId` | `Long` | `event_id` | not null |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_favorite_user_event` sur `(user_id, event_id)`.

Suppression physique autorisée (pas de soft-delete).

Helpers statiques : `Favorite.findByUserAndEvent(UUID, Long)`, `Favorite.findByUser(UUID, int, int)`.
```

Ajouter dans la section **Event** (champs) la ligne :

```markdown
| `shareCode` | `shareCode` | `String` | `share_code` | nullable, unique — généré à la demande par `ShareService` |
```

Ajouter dans la section **DTOs** :

```markdown
### FavoriteDTO (record)
Représente un favori en base. Non exposé directement dans les endpoints publics (les listes retournent `EventDTO`).
```id, userId, eventId, createdAt```
Factory : `FavoriteDTO.from(Favorite favorite)`

### ShareResponse (record)
Retourné par `GET /events/{id}/share`.
```shareUrl, shortCode```
```

### `backend/docs/sprint-context.md`

Dans la section **Sprint 4**, mettre à jour les cases à cocher :

```markdown
- [x] Entité `Favorite` (userId, eventId, createdAt, contrainte unique) (SCRUM-89)
- [x] `POST /events/{id}/favorite`, `DELETE /events/{id}/favorite`, `GET /users/me/favorites` (SCRUM-89)
- [x] `GET /events/{id}/share`, `GET /s/{shortCode}` — shortlink partageable (SCRUM-89)
```

---

## Résumé des fichiers à créer/modifier

| Fichier | Action |
|---|---|
| `/workspace/openapi/openapi.yaml` | **Modifier** — ajouter `ShareResponse`, compléter `/users/me/favorites`, compléter `/events/{id}/favorite`, ajouter `/events/{id}/share`, ajouter `/s/{shortCode}` |
| `backend/src/main/java/.../entity/Favorite.java` | **Créer** |
| `backend/src/main/java/.../entity/Event.java` | **Modifier** — ajouter champ `shareCode` + helper `findByShareCode` |
| `backend/src/main/java/.../dto/favorite/FavoriteDTO.java` | **Créer** |
| `backend/src/main/java/.../dto/event/ShareResponse.java` | **Créer** |
| `backend/src/main/java/.../service/FavoriteService.java` | **Créer** |
| `backend/src/main/java/.../service/ShareService.java` | **Créer** |
| `backend/src/main/java/.../resource/FavoriteResource.java` | **Créer** |
| `backend/src/main/java/.../resource/RedirectResource.java` | **Créer** |
| `backend/src/main/java/.../resource/UserResource.java` | **Modifier** — ajouter `GET /me/favorites` + injection `FavoriteService` |
| `backend/src/main/resources/application.properties` | **Modifier** — ajouter `app.frontend.url` |
| `backend/src/test/java/.../service/FavoriteServiceMock.java` | **Créer** |
| `backend/src/test/java/.../service/ShareServiceMock.java` | **Créer** |
| `backend/src/test/java/.../resource/FavoriteResourceTest.java` | **Créer** |
| `backend/docs/api-contract.md` | **Mettre à jour** — ajouter 5 endpoints |
| `backend/docs/data-model.md` | **Mettre à jour** — entité Favorite + champ shareCode + DTOs |
| `backend/docs/sprint-context.md` | **Mettre à jour** — SCRUM-89 coché |

---

## Règles critiques à respecter

| Règle | Détail |
|---|---|
| `openapi.yaml` EN PREMIER | Étape 0 obligatoire avant tout code |
| camelCase partout | `userId`, `eventId`, `createdAt`, `shareUrl`, `shortCode` — jamais de snake_case |
| Booléens sans préfixe `is` | N/A dans cette tâche — pas de champ booléen introduit |
| Constructor injection | `FavoriteResource` et `RedirectResource` : `@Inject` sur le constructeur uniquement |
| Logique métier dans le Service | `FavoriteResource` et `RedirectResource` délèguent entièrement — zéro logique dans la Resource |
| `@Transactional` sur toutes les mutations | `addFavorite`, `removeFavorite`, `getShareInfo`, `resolveByShortCode` |
| Idempotence `addFavorite` | Retourne `200 OK` silencieusement si le favori existe déjà — **jamais de 409** |
| Idempotence `getShareInfo` | Retourne le même `shortCode` à chaque appel — généré une seule fois |
| Suppression physique de `Favorite` | `DELETE` sur l'entité `Favorite` autorisé — ce n'est pas un Event |
| `SecureRandom` pour le shortCode | Jamais `Math.random()` — Security Rating A SonarCloud |
| `@PermitAll` sur `GET /s/{shortCode}` | Endpoint de redirection public — pas de token requis |
| SonarCloud | 80% coverage min, 3% duplication max, Security/Reliability/Maintainability Rating A |
| Branche Git | `feature/s4-favorites-share` |
