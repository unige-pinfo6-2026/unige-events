# Spec SCRUM-107 — Endpoints avancés : Publication & Upload bannière

> **Branche :** `feature/SCRUM-107-event-publish-image`
> **Prérequis :** SCRUM-83 mergé sur main
> **Règle d'or :** Modifier `openapi/openapi.yaml` EN PREMIER, puis coder Resource → Service → Entity → Test

---

## Modèle de rôles

L'autorisation est gérée par Auth0 via des rôles encodés dans le JWT. Aucun champ `admin` n'est ajouté en base de données.

| Rôle | Droits |
|---|---|
| `ADMIN` | Tout — peut publier et uploader sur n'importe quel événement |
| `ORGANIZER` | Peut créer des événements et agir sur les siens (publish, image) |
| `STUDENT` | Lecture seule — ne peut pas appeler les endpoints de modification |

Côté Quarkus : les rôles sont extraits du JWT via `quarkus.oidc.roles.role-claim-path` et exposés sur `SecurityIdentity`. Les endpoints utilisent `@RolesAllowed({"ORGANIZER","ADMIN"})` plutôt que `@Authenticated`.

---

## Contexte

### Ce qui existe déjà (ne pas retoucher)

| Fichier | État |
|---|---|
| `entity/Event.java` | Complet — `status` (DRAFT/PUBLISHED/CANCELLED), `bannerUrl`, `creator` (@ManyToOne LAZY → User) |
| `entity/EventStatus.java` | Enum complet : DRAFT, PUBLISHED, CANCELLED |
| `resource/EventResource.java` | CRUD complet — constructor injection, `@Authenticated` sur mutations |
| `service/EventService.java` | CRUD complet — `isCreator()` helper privé statique, `@Transactional` sur toutes les mutations |
| `openapi/openapi.yaml` | `POST /events/{id}/image` déjà déclaré (TODO SCRUM-107, réponses 401/404 manquantes) ; `PATCH /events/{id}/publish` absent |

### Ce qui manque / est à créer

| Fichier | Action |
|---|---|
| `openapi/openapi.yaml` | Ajouter `PATCH /events/{id}/publish` + compléter `POST /events/{id}/image` (401/404 manquants) + retirer le commentaire TODO SCRUM-107 |
| `pom.xml` | Ajouter `quarkus-rest-reactive-multipart` (fournit `FileUpload` et `@MultipartForm`) |
| `application.properties` | Ajouter `quarkus.oidc.roles.role-claim-path` + `app.uploads.path` |
| `dto/event/ImageUploadForm.java` | Nouveau — POJO multipart avec `@RestForm("file") FileUpload` |
| `config/UploadsRouteConfig.java` | Nouveau — Vert.x `StaticHandler` pour servir `/uploads/*` depuis le filesystem |
| `service/EventService.java` | Ajouter `publish(Long id, String auth0Id, boolean isAdmin)` et `uploadImage(Long id, String auth0Id, FileUpload file, boolean isAdmin)` |
| `resource/EventResource.java` | Ajouter `PATCH /{id}/publish` et `POST /{id}/image` avec `@RolesAllowed({"ORGANIZER","ADMIN"})` |
| `test/.../service/EventServiceMock.java` | Ajouter `publish()`, `uploadImage()`, `forceConflictOnPublish`, `forceBadMimeOnUpload` |
| `test/.../resource/EventResourceTest.java` | +7 nouveaux tests |
| `docs/sprint-context.md` | Marquer SCRUM-107 terminé |

---

## Étape 0 — `openapi/openapi.yaml` (OBLIGATOIRE EN PREMIER)

### A. Ajouter `PATCH /events/{id}/publish`

Insérer ce bloc **avant** `/events/{id}/image` dans la section `paths` :

```yaml
  /events/{id}/publish:
    patch:
      summary: Publier un événement (DRAFT → PUBLISHED)
      description: |
        Passe le statut de l'événement de DRAFT à PUBLISHED.
        Réservé au créateur de l'événement (rôle ORGANIZER) ou à un administrateur (rôle ADMIN).
        Retourne 409 si l'événement n'est pas en statut DRAFT (déjà publié ou annulé).
      operationId: publishEvent
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
          description: Événement publié — retourne l'EventDTO complet avec status PUBLISHED
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Event'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '403':
          description: Rôle insuffisant (ni ORGANIZER ni ADMIN) ou pas le créateur de l'événement
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
          description: Conflit — l'événement n'est pas en statut DRAFT
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### B. Compléter `POST /events/{id}/image`

Sur le path existant `/events/{id}/image`, ajouter les réponses manquantes après le `'403'` existant :

```yaml
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

Supprimer le commentaire `# TODO: SCRUM-107 (Antoine Maendly, Sprint 2) — endpoint pas encore implémenté côté backend`.

Mettre à jour la description `'403'` pour indiquer : `"Rôle insuffisant ou pas le créateur de l'événement"`.

La réponse `'200'` retourne `$ref: '#/components/schemas/Event'` (l'événement complet avec le `bannerUrl` mis à jour) — **ne pas changer ce schéma**, il est déjà correct dans l'openapi.yaml existant.

---

## Étape 1 — `pom.xml` — Dépendance multipart

Ajouter dans la section `<dependencies>` :

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-reactive-multipart</artifactId>
</dependency>
```

> Cette dépendance fournit `org.jboss.resteasy.reactive.multipart.FileUpload` et le support `@MultipartForm` pour `quarkus-rest` (RESTEasy Reactive). Sans elle, les uploads multipart retournent une erreur 415.

---

## Étape 2 — `application.properties` — Mapping des rôles Auth0 + chemin d'upload

Ajouter dans `src/main/resources/application.properties` :

```properties
# Rôles Auth0 — le claim JWT contenant les rôles (doit correspondre à l'Action Auth0 configurée)
# Valeurs possibles : ADMIN, ORGANIZER, STUDENT
quarkus.oidc.roles.role-claim-path=https://unige-events/roles

# Upload des bannières événements
app.uploads.path=/tmp/unige-events-uploads
```

> **Note :** Le chemin `https://unige-events/roles` est le nom du claim JWT ajouté par l'Action Auth0. Il doit correspondre exactement à ce que l'Action Auth0 écrit dans le token. À confirmer avec la configuration Auth0 du projet.
>
> Quarkus lit ce claim et peuple `SecurityIdentity.getRoles()` automatiquement. `identity.hasRole("ADMIN")` et `@RolesAllowed({"ADMIN","ORGANIZER"})` fonctionnent alors sans code supplémentaire.
>
> En test, `quarkus.oidc.enabled=false` — les rôles sont simulés directement via `@TestSecurity(roles = {...})`.

---

## Étape 3 — `dto/event/ImageUploadForm.java` (nouveau fichier)

Créer dans `src/main/java/ch/unige/events/dto/event/` :

```java
package ch.unige.events.dto.event;

import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

public class ImageUploadForm {

    @RestForm("file")
    public FileUpload file;
}
```

> `FileUpload` donne accès à :
> - `fileUpload.contentType()` → type MIME déclaré par le client
> - `fileUpload.fileName()` → nom du fichier original (pour l'extension)
> - `fileUpload.uploadedFile()` → `Path` vers le fichier temporaire sur disque (géré par Quarkus)

---

## Étape 4 — `config/UploadsRouteConfig.java` (nouveau fichier)

Créer dans `src/main/java/ch/unige/events/config/` pour servir les images uploadées depuis le filesystem :

```java
package ch.unige.events.config;

import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.FileSystemAccess;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class UploadsRouteConfig {

    private final Router router;
    private final String uploadsPath;

    @Inject
    public UploadsRouteConfig(Router router,
                               @ConfigProperty(name = "app.uploads.path",
                                               defaultValue = "/tmp/unige-events-uploads")
                               String uploadsPath) {
        this.router = router;
        this.uploadsPath = uploadsPath;
    }

    void init(@Observes StartupEvent ev) {
        router.route("/uploads/*")
              .handler(StaticHandler.create(FileSystemAccess.ROOT, uploadsPath));
    }
}
```

> Quarkus expose ainsi toutes les images stockées dans `app.uploads.path` à l'URL `/uploads/{filename}`.
> **Note :** ce handler est enregistré sur le router Vert.x **avant** le préfixe `/api` — les images sont accessibles à `/uploads/…` (pas `/api/uploads/…`). Le `bannerUrl` stocké en base doit donc commencer par `/uploads/`.

---

## Étape 5 — `service/EventService.java` — Deux nouvelles méthodes

### 5.1 Champ de configuration à injecter

Ajouter le chemin d'upload dans EventService. `@ConfigProperty` est une injection MicroProfile Config (pas un bean CDI), l'injection en champ est l'usage idiomatique Quarkus et ne déclenche pas la règle SonarCloud S6813 qui cible `@Inject` :

```java
@ConfigProperty(name = "app.uploads.path", defaultValue = "/tmp/unige-events-uploads")
String uploadsPath;
```

### 5.2 `publish(Long id, String auth0Id, boolean isAdmin)`

La Resource extrait le rôle ADMIN depuis le JWT et le passe au Service. Le Service n'a pas accès au JWT — il reçoit `isAdmin` comme paramètre.

```java
@Transactional
public EventDTO publish(Long id, String auth0Id, boolean isAdmin) {
    Event event = Event.<Event>findByIdOptional(id).orElseThrow(NotFoundException::new);

    if (!isAdmin && !isCreator(event, auth0Id)) {
        throw new ForbiddenException("Only the event creator or an admin can publish this event");
    }

    if (event.status != EventStatus.DRAFT) {
        String message = event.status == EventStatus.PUBLISHED
            ? "Event is already published"
            : "Event cannot be published: current status is " + event.status;
        throw new WebApplicationException(
            Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "conflict", "message", message))
                .build());
    }

    event.status = EventStatus.PUBLISHED;
    return EventDTO.from(event);
}
```

**Logique :**
- 404 si événement inexistant (en premier, avant tout check d'autorisation)
- 403 si ni ADMIN ni créateur de l'événement
- 409 si `event.status != DRAFT` (qu'il soit PUBLISHED ou CANCELLED)
- 200 + EventDTO avec `status = PUBLISHED` en cas de succès

**Imports à ajouter :** `jakarta.ws.rs.WebApplicationException`, `jakarta.ws.rs.core.Response`, `java.util.Map`.

**Note :** la réponse 409 est construite directement via `WebApplicationException(Response)` — il n'existe pas de mapper dédié 409 pour les conflits métier (seul `OptimisticLockException` est mappé en 409). `Map.of(...)` produit le JSON `{"error":"conflict","message":"..."}` conforme à `ApiErrorResponse`.

### 5.3 `uploadImage(Long id, String auth0Id, FileUpload fileUpload, boolean isAdmin)`

```java
@Transactional
public EventDTO uploadImage(Long id, String auth0Id, FileUpload fileUpload, boolean isAdmin) {
    Event event = Event.<Event>findByIdOptional(id).orElseThrow(NotFoundException::new);

    if (!isAdmin && !isCreator(event, auth0Id)) {
        throw new ForbiddenException("Only the event creator or an admin can upload a banner");
    }

    // Validation du type MIME — seuls les types image/* sont acceptés
    String contentType = fileUpload.contentType();
    if (contentType == null || !contentType.startsWith("image/")) {
        throw new BadRequestException(
            "File must be an image (accepted: image/jpeg, image/png, image/webp, image/gif)");
    }

    // Extension dérivée du nom de fichier original
    String originalName = fileUpload.fileName();
    String extension = (originalName != null && originalName.contains("."))
        ? originalName.substring(originalName.lastIndexOf('.'))
        : ".bin";

    // Nom de fichier unique pour éviter les collisions
    String uniqueFileName = UUID.randomUUID() + extension;

    // Copie dans le répertoire d'upload
    Path targetDir = Path.of(uploadsPath);
    Path targetFile = targetDir.resolve(uniqueFileName);
    try {
        Files.createDirectories(targetDir);
        Files.copy(fileUpload.uploadedFile(), targetFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
        throw new InternalServerErrorException("Failed to save banner image: " + e.getMessage());
    }

    // Mise à jour de l'événement — bannerUrl pointe vers le chemin servi par UploadsRouteConfig
    event.bannerUrl = "/uploads/" + uniqueFileName;
    return EventDTO.from(event);
}
```

**Imports à ajouter :** `java.nio.file.*`, `java.io.IOException`, `java.util.UUID`, `jakarta.ws.rs.BadRequestException`, `jakarta.ws.rs.InternalServerErrorException`, `org.jboss.resteasy.reactive.multipart.FileUpload`.

**Note :** `@Transactional` garantit que si la copie fichier réussit mais que le flush Hibernate échoue, la transaction sera rollbackée. Le fichier sur disque restera (pas de compensation automatique) — comportement acceptable pour un projet académique.

---

## Étape 6 — `resource/EventResource.java` — Deux nouveaux endpoints

Pas de changement au constructeur existant — `uploadsPath` est injecté dans le Service. La Resource reste pure : pas de logique métier, pas d'accès au filesystem.

### 6.1 `PATCH /{id}/publish`

```java
@PATCH
@Path("/{id}/publish")
@RolesAllowed({"ORGANIZER", "ADMIN"})
public Response publish(@PathParam("id") Long id) {
    String auth0Id = identity.getPrincipal().getName();
    boolean isAdmin = identity.hasRole("ADMIN");
    EventDTO published = eventService.publish(id, auth0Id, isAdmin);
    return Response.ok(published).build();
}
```

### 6.2 `POST /{id}/image`

```java
@POST
@Path("/{id}/image")
@Consumes(MediaType.MULTIPART_FORM_DATA)
@RolesAllowed({"ORGANIZER", "ADMIN"})
public Response uploadImage(@PathParam("id") Long id, @MultipartForm ImageUploadForm form) {
    String auth0Id = identity.getPrincipal().getName();
    boolean isAdmin = identity.hasRole("ADMIN");
    EventDTO updated = eventService.uploadImage(id, auth0Id, form.file, isAdmin);
    return Response.ok(updated).build();
}
```

**Imports à ajouter :** `jakarta.annotation.security.RolesAllowed`, `jakarta.ws.rs.PATCH`.

**Comportement selon le rôle :**

| Rôle appelant | Résultat au niveau Resource | Résultat au niveau Service |
|---|---|---|
| Non authentifié | 401 (avant d'atteindre le service) | — |
| `STUDENT` | 403 (avant d'atteindre le service) | — |
| `ORGANIZER` non créateur | passe | 403 (ForbiddenException du Service) |
| `ORGANIZER` créateur | passe | 200 si DRAFT, 409 sinon |
| `ADMIN` | passe avec `isAdmin=true` | 200 si DRAFT, 409 sinon (skip creator check) |

> La classe `@Path("/events")` reste `@Produces(MediaType.APPLICATION_JSON)` au niveau classe — le `POST /{id}/image` consomme `MULTIPART_FORM_DATA` mais produit JSON.

---

## Étape 7 — Tests

### 7.1 `EventServiceMock.java` — Ajouts

Ajouter deux flags statiques en tête de classe :

```java
public static volatile boolean forceConflictOnPublish = false;
public static volatile boolean forceBadMimeOnUpload = false;
```

Dans `reset()`, réinitialiser ces flags :

```java
forceConflictOnPublish = false;
forceBadMimeOnUpload = false;
```

Ajouter les méthodes overridées (signatures avec `boolean isAdmin`) :

```java
@Override
public EventDTO publish(Long id, String auth0Id, boolean isAdmin) {
    if (forceConflictOnPublish) {
        throw new WebApplicationException(
            Response.status(Response.Status.CONFLICT)
                .entity(Map.of("error", "conflict", "message", "Event is already published"))
                .build());
    }
    Event e = eventsById.get(id);
    if (e == null) throw new NotFoundException();
    if (forceForbiddenOnUpdate) throw new ForbiddenException("Forbidden");
    e.status = EventStatus.PUBLISHED;
    return EventDTO.from(e);
}

@Override
public EventDTO uploadImage(Long id, String auth0Id, FileUpload fileUpload, boolean isAdmin) {
    if (forceBadMimeOnUpload) {
        throw new BadRequestException("File must be an image");
    }
    Event e = eventsById.get(id);
    if (e == null) throw new NotFoundException();
    if (forceForbiddenOnUpdate) throw new ForbiddenException("Forbidden");
    e.bannerUrl = "/uploads/test-banner.jpg";
    return EventDTO.from(e);
}
```

> Le mock n'accède pas au filesystem — `fileUpload` n'est pas utilisé. C'est intentionnel : les tests resource testent les codes HTTP, pas le stockage disque.

### 7.2 `EventResourceTest.java` — 7 nouveaux tests

`@TestSecurity` utilise désormais `roles` (pas `attributes`) pour les endpoints avec `@RolesAllowed`. Conserver `attributes` uniquement si l'endpoint a besoin du claim `email` (ce n'est pas le cas ici).

#### PATCH /events/{id}/publish — 5 tests

```java
@Test
@TestSecurity(user = "auth0|alice", roles = {"ORGANIZER"})
void publish_asOrganiserOwner_returns200() {
    long eventId = mock.seedEvent("auth0|alice", "Draft Event").id;

    given()
        .when().patch("/events/" + eventId + "/publish")
        .then()
        .statusCode(200)
        .body("status", equalTo("PUBLISHED"));
}

@Test
@TestSecurity(user = "auth0|admin", roles = {"ADMIN"})
void publish_asAdmin_onAnyEvent_returns200() {
    long eventId = mock.seedEvent("auth0|bob", "Bob's Draft Event").id;

    given()
        .when().patch("/events/" + eventId + "/publish")
        .then()
        .statusCode(200)
        .body("status", equalTo("PUBLISHED"));
}

@Test
void publish_unauthenticated_returns401() {
    long eventId = mock.seedEvent("auth0|alice", "Draft Event").id;

    given()
        .when().patch("/events/" + eventId + "/publish")
        .then()
        .statusCode(401);
}

@Test
@TestSecurity(user = "auth0|alice", roles = {"STUDENT"})
void publish_asStudent_returns403() {
    long eventId = mock.seedEvent("auth0|alice", "Draft Event").id;

    given()
        .when().patch("/events/" + eventId + "/publish")
        .then()
        .statusCode(403);
}

@Test
@TestSecurity(user = "auth0|alice", roles = {"ORGANIZER"})
void publish_asOrganiserNotOwner_returns403() {
    long eventId = mock.seedEvent("auth0|bob", "Bob's Event").id;
    EventServiceMock.forceForbiddenOnUpdate = true;

    given()
        .when().patch("/events/" + eventId + "/publish")
        .then()
        .statusCode(403)
        .body("error", equalTo("forbidden"));
}

@Test
@TestSecurity(user = "auth0|alice", roles = {"ORGANIZER"})
void publish_alreadyPublished_returns409() {
    long eventId = mock.seedEvent("auth0|alice", "Published Event").id;
    EventServiceMock.forceConflictOnPublish = true;

    given()
        .when().patch("/events/" + eventId + "/publish")
        .then()
        .statusCode(409)
        .body("error", equalTo("conflict"));
}
```

> `publish_asStudent_returns403` et `publish_unauthenticated_returns401` : le service n'est **jamais appelé** — `@RolesAllowed` bloque la requête avant. Pas besoin de setup du mock.

#### POST /events/{id}/image — 2 tests

```java
@Test
@TestSecurity(user = "auth0|alice", roles = {"ORGANIZER"})
void uploadImage_withValidJpeg_returns200() {
    long eventId = mock.seedEvent("auth0|alice", "Event avec bannière").id;

    given()
        .contentType("multipart/form-data")
        .multiPart("file", "banner.jpg", "fake-jpeg-bytes".getBytes(), "image/jpeg")
        .when().post("/events/" + eventId + "/image")
        .then()
        .statusCode(200)
        .body("bannerUrl", notNullValue());
}

@Test
@TestSecurity(user = "auth0|alice", roles = {"ORGANIZER"})
void uploadImage_withInvalidMime_returns400() {
    long eventId = mock.seedEvent("auth0|alice", "Event").id;
    EventServiceMock.forceBadMimeOnUpload = true;

    given()
        .contentType("multipart/form-data")
        .multiPart("file", "script.sh", "#!/bin/bash".getBytes(), "text/plain")
        .when().post("/events/" + eventId + "/image")
        .then()
        .statusCode(400);
}
```

**Import :** `static org.hamcrest.Matchers.notNullValue` à ajouter si absent.

---

## Étape 8 — Documentation

| Fichier | Action |
|---|---|
| `openapi/openapi.yaml` | Fait à l'étape 0 |
| `docs/sprint-context.md` | Ajouter SCRUM-107 dans les items complétés du Sprint courant |

Dans `sprint-context.md`, ajouter :

```markdown
- [x] `PATCH /events/{id}/publish` : publication d'un événement DRAFT (ORGANIZER créateur ou ADMIN) — 403/404/409
- [x] `POST /events/{id}/image` : upload bannière multipart, stockage local `app.uploads.path`, retourne EventDTO mis à jour — 400 si MIME invalide
- [x] Rôles Auth0 (ADMIN/ORGANIZER/STUDENT) mappés via `quarkus.oidc.roles.role-claim-path`
```

---

## Résumé des fichiers à toucher

| Fichier | Action |
|---|---|
| `openapi/openapi.yaml` | Ajouter `PATCH /events/{id}/publish`, compléter `POST /events/{id}/image`, retirer TODO |
| `pom.xml` | Ajouter `quarkus-rest-reactive-multipart` |
| `src/main/resources/application.properties` | Ajouter `quarkus.oidc.roles.role-claim-path` + `app.uploads.path` |
| `dto/event/ImageUploadForm.java` | **Nouveau** — POJO multipart |
| `config/UploadsRouteConfig.java` | **Nouveau** — Vert.x StaticHandler pour `/uploads/*` |
| `service/EventService.java` | Ajouter `@ConfigProperty uploadsPath`, `publish(…, boolean isAdmin)`, `uploadImage(…, boolean isAdmin)` |
| `resource/EventResource.java` | Ajouter `PATCH /{id}/publish` et `POST /{id}/image` avec `@RolesAllowed` |
| `test/.../service/EventServiceMock.java` | Ajouter `publish()`, `uploadImage()`, 2 flags |
| `test/.../resource/EventResourceTest.java` | +7 tests |
| `docs/sprint-context.md` | Mettre à jour l'avancement |

---

## Règles critiques à respecter

- **Rôles via JWT Auth0 uniquement** — aucun champ `admin` ou `role` en base de données ; la vérification de rôle passe par `identity.hasRole("ADMIN")`
- **`@RolesAllowed({"ORGANIZER","ADMIN"})` sur les nouveaux endpoints** — remplace `@Authenticated`
- **Le Service reçoit `boolean isAdmin`** — il ne consulte pas le JWT ni la base pour déterminer le rôle
- **camelCase partout** — jamais de snake_case dans les champs Java ni en JSON
- **Constructor injection ONLY pour les beans CDI** (`@Inject`) — `@ConfigProperty` en champ est acceptable (règle MicroProfile, pas CDI)
- **Resource ne touche jamais les entités ni le filesystem** — tout passe par le Service
- **`@Transactional`** sur `publish()` et `uploadImage()` dans le Service
- **`openapi.yaml` AVANT le code**
- **`PATCH /events/{id}/publish` retourne `200 + EventDTO`** avec `status = "PUBLISHED"` — jamais `204`
- **`POST /events/{id}/image` retourne `200 + EventDTO`** avec `bannerUrl` mis à jour — conforme à l'openapi.yaml existant
- **409 sur `publish()` si `status != DRAFT`** — que l'événement soit PUBLISHED ou CANCELLED
- **404 avant 403** — chercher l'événement en premier, puis vérifier les droits
- **Type MIME validé côté Service** — seuls les `contentType` commençant par `image/` sont acceptés
