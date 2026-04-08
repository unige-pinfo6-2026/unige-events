# Specs SCRUM-89-bis — Synchronisation Calendrier (webcal/ICS personnel)

> **Branche suggérée :** `feature/s4-calendar-sync`
> **Sprint :** 4 — Backend Tâche complémentaire · Epic 4 – Engagement & Interaction
> **Prérequis :** SCRUM-89 mergé (`Favorite.java`, `FavoriteService.java`, `ShareService.java`) · SCRUM-88 peut être parallèle (le feed ICS est extensible pour inclure l'attendance plus tard)
> **Règle d'or :** Modifier `openapi/openapi.yaml` EN PREMIER, puis coder Entity → DTO → Service → Resource → Tests → Docs

---

## Contexte

### Principe fonctionnel

La fonctionnalité "Synchronisation calendrier" permet à un utilisateur authentifié d'abonner son application calendrier (Apple Calendar, Google Calendar, Outlook) à un flux **iCalendar (RFC 5545)** dynamique contenant ses événements favoris UNIGE.

Contrairement à l'export `.ics` ponctuel de SCRUM-100 (généré côté frontend pour **un seul événement**), ce flux est :
- **Persistant** : l'URL est stable, l'utilisateur n'abonne qu'une seule fois
- **Auto-mis-à-jour** : l'app calendrier poll l'URL périodiquement (toutes les semaines pour Apple Calendar) et reçoit les nouveaux favoris automatiquement

### Mécanisme d'authentification

Les applications calendrier ne supportent pas les JWT Bearer. La sécurité repose sur un **token opaque personnel** (`calendarToken`, UUID) stocké sur l'entité `User` et passé dans l'URL :

```
webcal://10.25.10.136.nip.io/api/calendar/{calendarToken}.ics
```

Ce pattern est identique à celui de Google Calendar (liens "Partager ce calendrier"), GitHub (iCal des issues/PR) et Fotmob (flux matchs par équipe). Le token peut être révoqué/régénéré à tout moment.

### Frontières avec SCRUM-100

| SCRUM-100 (export statique) | SCRUM-89-bis (sync dynamique) |
|---|---|
| Génère un `.ics` pour **un seul événement** | Flux de **tous les favoris** de l'utilisateur |
| Généré côté **frontend** (aucun backend) | Généré par le **backend**, endpoint dédié |
| Import unique, pas de mise à jour | Mis à jour automatiquement |
| Aucune auth nécessaire | Token opaque dans l'URL |

---

## Ce qui existe déjà (ne pas retoucher sauf indication contraire)

| Fichier | État |
|---|---|
| `entity/User.java` | Complet — à modifier uniquement pour ajouter `calendarToken` |
| `entity/Event.java` | Complet — lu par `CalendarService` |
| `entity/Favorite.java` | Complet (SCRUM-89) — à modifier uniquement pour ajouter `findAllByUser` |
| `service/FavoriteService.java` | Complet (SCRUM-89) — ne pas toucher |
| `service/ShareService.java` | Complet (SCRUM-89) — ne pas toucher |
| `resource/UserResource.java` | Complet — à étendre pour 2 nouveaux endpoints (field injection existant) |
| `openapi/openapi.yaml` | À compléter — ajouter schéma `CalendarTokenResponse` + 3 paths |
| `test/.../resource/UserResourceTest.java` | Complet — à étendre avec 3 nouveaux tests |
| `test/.../service/FavoriteServiceCoverageProfile.java` | Complet — à modifier pour exclure `CalendarServiceMock` |

---

## Ce qui est à créer

| Fichier | Action |
|---|---|
| `dto/calendar/CalendarTokenResponse.java` | Nouveau record |
| `service/CalendarService.java` | Nouveau — gestion du token + génération ICS |
| `resource/CalendarResource.java` | Nouveau — `@Path("/calendar")`, `@PermitAll` |
| `test/.../service/CalendarServiceMock.java` | Nouveau mock in-memory pour `UserResourceTest` |
| `test/.../service/CalendarServiceCoverageProfile.java` | Nouveau profil DB pour les tests coverage |
| `test/.../service/CalendarServiceCoverageTest.java` | Nouveau — 10 cas de test |

---

## Ce qui est à modifier

| Fichier | Modification |
|---|---|
| `entity/User.java` | Ajout champ `calendarToken` (UUID, unique, nullable) |
| `entity/Favorite.java` | Ajout helper `findAllByUser(UUID userId)` |
| `resource/UserResource.java` | Ajout `@Inject CalendarService` + 2 méthodes |
| `test/.../service/FavoriteServiceCoverageProfile.java` | Ajouter `CalendarServiceMock` dans `exclude-types` |
| `openapi/openapi.yaml` | Schéma `CalendarTokenResponse` + 3 paths |
| `docs/data-model.md` | Ajout `calendarToken` dans la table User |
| `docs/api-contract.md` | Ajout 3 nouveaux endpoints |
| `docs/sprint-context.md` | Marquer SCRUM-89 comme terminé + ajouter SCRUM-89-bis |

---

## Architecture des endpoints

| Endpoint | Classe | Auth | Description |
|---|---|---|---|
| `GET /api/users/me/calendar-token` | `UserResource` (étendu) | `@Authenticated` | Obtenir ou créer le token personnel + URLs (idempotent) |
| `DELETE /api/users/me/calendar-token` | `UserResource` (étendu) | `@Authenticated` | Révoquer et régénérer le token |
| `GET /api/calendar/{calendarToken}.ics` | `CalendarResource` (nouveau) | `@PermitAll` | Flux iCalendar personnel — polled par l'app calendrier |

---

## Étape 0 — `openapi/openapi.yaml` (OBLIGATOIRE EN PREMIER)

**Fichier :** `/workspace/openapi/openapi.yaml`

### 0.1 — Schéma `CalendarTokenResponse` dans `components/schemas`

```yaml
    CalendarTokenResponse:
      type: object
      description: Token de calendrier personnel et URLs d'abonnement (webcal + https)
      properties:
        calendarToken:
          type: string
          format: uuid
          description: Token opaque unique — à inclure dans l'URL d'abonnement
        webcalUrl:
          type: string
          description: >
            URL protocole webcal:// — ouvre directement l'application calendrier
            (Apple Calendar, Outlook). Google Calendar nécessite l'URL https://.
          example: "webcal://10.25.10.136.nip.io/api/calendar/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.ics"
        httpsUrl:
          type: string
          format: uri
          description: URL HTTPS — à copier manuellement dans Google Calendar
          example: "https://10.25.10.136.nip.io/api/calendar/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx.ics"
      required: [calendarToken, webcalUrl, httpsUrl]
      example:
        calendarToken: "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        webcalUrl: "webcal://10.25.10.136.nip.io/api/calendar/a1b2c3d4-e5f6-7890-abcd-ef1234567890.ics"
        httpsUrl: "https://10.25.10.136.nip.io/api/calendar/a1b2c3d4-e5f6-7890-abcd-ef1234567890.ics"
```

### 0.2 — Path `GET /users/me/calendar-token` et `DELETE /users/me/calendar-token`

Ajouter ce path dans la section `paths` (après `/users/me/favorites` par exemple) :

```yaml
  /users/me/calendar-token:
    get:
      summary: Obtenir le token de calendrier personnel (idempotent)
      description: |
        Retourne le token d'abonnement calendrier de l'utilisateur connecté.
        Si aucun token n'existe encore, en génère un nouveau (idempotent).
        Retourner les deux URLs d'abonnement : `webcalUrl` (protocole webcal://)
        et `httpsUrl` (pour Google Calendar qui n'accepte pas webcal://).
      operationId: getMyCalendarToken
      tags: [users, calendar]
      security:
        - BearerAuth: []
      responses:
        '200':
          description: Token et URLs d'abonnement
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CalendarTokenResponse'
        '401':
          description: Token Bearer absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

    delete:
      summary: Révoquer et régénérer le token de calendrier
      description: |
        Invalide le token existant et génère un nouveau UUID.
        Les abonnements existants utilisant l'ancien token cesseront de fonctionner.
        Retourne le nouveau token avec ses URLs d'abonnement.
      operationId: regenerateMyCalendarToken
      tags: [users, calendar]
      security:
        - BearerAuth: []
      responses:
        '200':
          description: Nouveau token et URLs d'abonnement
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/CalendarTokenResponse'
        '401':
          description: Token Bearer absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.3 — Path `GET /calendar/{calendarToken}.ics`

```yaml
  /calendar/{calendarToken}.ics:
    get:
      summary: Flux iCalendar personnel (webcal)
      description: |
        Retourne le flux iCalendar RFC 5545 des événements favoris PUBLISHED de l'utilisateur
        identifié par son token personnel. Polled automatiquement par les applications
        calendrier. Content-Type: text/calendar;charset=UTF-8.
        Authentification via token dans l'URL (pas de Bearer JWT requis).
      operationId: getCalendarFeed
      tags: [calendar]
      security: []
      parameters:
        - name: calendarToken
          in: path
          required: true
          schema:
            type: string
            format: uuid
          description: Token personnel opaque de l'utilisateur
      responses:
        '200':
          description: Flux iCalendar valide (RFC 5545)
          content:
            text/calendar:
              schema:
                type: string
                example: "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n...\r\nEND:VCALENDAR"
        '404':
          description: Token inconnu ou révoqué
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

---

## Étape 1 — Modification de `User.java`

**Fichier :** `backend/src/main/java/ch/unige/events/entity/User.java`

Ajouter **après le champ `version`** (avant la fermeture de la classe) :

```java
@Column(unique = true)
public UUID calendarToken;
```

Import à ajouter si absent :
```java
import java.util.UUID;
```

**Règles :**
- `nullable` implicite (aucune annotation `nullable = false`) — le token est créé à la demande
- `unique = true` garantit qu'aucun token n'appartient à plus d'un utilisateur
- Hibernate mode `update` crée automatiquement la colonne `calendar_token` en DB au démarrage
- **Ne pas initialiser dans `@PrePersist`** — la génération est faite par `CalendarService`

---

## Étape 2 — Helper `Favorite.findAllByUser`

**Fichier :** `backend/src/main/java/ch/unige/events/entity/Favorite.java`

Ajouter après `findByUser` (le helper paginé existant) :

```java
public static List<Favorite> findAllByUser(UUID userId) {
    return list("userId = ?1", userId);
}
```

Ce helper retourne **tous** les favoris de l'utilisateur sans pagination — nécessaire pour le flux ICS complet. Le `List` est déjà importé dans le fichier.

---

## Étape 3 — `CalendarTokenResponse.java` (nouveau DTO)

**Fichier :** `backend/src/main/java/ch/unige/events/dto/calendar/CalendarTokenResponse.java`

```java
package ch.unige.events.dto.calendar;

import java.util.UUID;

public record CalendarTokenResponse(
        UUID calendarToken,
        String webcalUrl,
        String httpsUrl
) {}
```

Créer le répertoire `dto/calendar/` si absent.

---

## Étape 4 — `CalendarService.java` (nouveau)

**Fichier :** `backend/src/main/java/ch/unige/events/service/CalendarService.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.calendar.CalendarTokenResponse;
import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import ch.unige.events.entity.Favorite;
import ch.unige.events.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class CalendarService {

    private static final DateTimeFormatter ICS_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    @ConfigProperty(name = "app.frontend.url", defaultValue = "https://10.25.10.136.nip.io")
    String frontendUrl;

    // ── Token management ──────────────────────────────────────────────────────

    @Transactional
    public CalendarTokenResponse getOrCreateToken(String auth0Id) {
        User user = resolveUser(auth0Id);
        if (user.calendarToken == null) {
            user.calendarToken = UUID.randomUUID();
        }
        return buildTokenResponse(user.calendarToken);
    }

    @Transactional
    public CalendarTokenResponse regenerateToken(String auth0Id) {
        User user = resolveUser(auth0Id);
        user.calendarToken = UUID.randomUUID();
        return buildTokenResponse(user.calendarToken);
    }

    // ── ICS feed ─────────────────────────────────────────────────────────────

    @Transactional
    public String generateIcsFeed(UUID calendarToken) {
        User user = User.<User>find("calendarToken", calendarToken)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Calendar token not found"));

        List<Event> events = Favorite.findAllByUser(user.id).stream()
                .map(f -> Event.<Event>findByIdOptional(f.eventId))
                .flatMap(Optional::stream)
                .filter(e -> e.status == EventStatus.PUBLISHED)
                .toList();

        return buildIcsContent(events);
    }

    // ── ICS builder ───────────────────────────────────────────────────────────

    String buildIcsContent(List<Event> events) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//UNIGE Events//UNIGE Events API//FR\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:Mes \u00e9v\u00e9nements UNIGE\r\n");
        sb.append("X-WR-TIMEZONE:Europe/Zurich\r\n");

        for (Event event : events) {
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(event.id).append("@unige-events\r\n");
            sb.append("SUMMARY:").append(escapeIcs(event.title)).append("\r\n");
            sb.append("DTSTART;TZID=Europe/Zurich:").append(event.startDate.format(ICS_DT)).append("\r\n");
            sb.append("DTEND;TZID=Europe/Zurich:").append(event.endDate.format(ICS_DT)).append("\r\n");
            if (event.location != null) {
                sb.append("LOCATION:").append(escapeIcs(event.location)).append("\r\n");
            }
            if (event.description != null) {
                sb.append("DESCRIPTION:").append(escapeIcs(event.description)).append("\r\n");
            }
            sb.append("URL:").append(frontendUrl).append("/events/").append(event.id).append("\r\n");
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    String escapeIcs(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }

    private CalendarTokenResponse buildTokenResponse(UUID token) {
        String httpsUrl = frontendUrl + "/api/calendar/" + token + ".ics";
        String webcalUrl = httpsUrl.replaceFirst("^https?://", "webcal://");
        return new CalendarTokenResponse(token, webcalUrl, httpsUrl);
    }

    private User resolveUser(String auth0Id) {
        return User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User profile not found"));
    }
}
```

### Points d'attention

| Point | Détail |
|---|---|
| `@Transactional` obligatoire | Sur toutes les méthodes — convention du projet |
| Pas de lib externe | Format ICS en pur Java, cohérent avec SCRUM-100 et AGENTS.md |
| `escapeIcs` | RFC 5545 §3.3.11 — `\`, `;`, `,`, newlines à échapper |
| Filtre `PUBLISHED` | Seuls les événements publiés apparaissent dans le flux |
| `buildIcsContent` et `escapeIcs` **package-private** | Pour être testables dans `CalendarServiceCoverageTest` sans mock |
| N+1 sur `Event.findByIdOptional` | Identique au pattern de `FavoriteService.getFavorites` — acceptable pour Sprint 4 |
| `replaceFirst("^https?://", "webcal://")` | Fonctionne avec `http://` (dev) et `https://` (prod) |

---

## Étape 5 — `CalendarResource.java` (nouveau)

**Fichier :** `backend/src/main/java/ch/unige/events/resource/CalendarResource.java`

```java
package ch.unige.events.resource;

import ch.unige.events.service.CalendarService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/calendar")
public class CalendarResource {

    private final CalendarService calendarService;

    @Inject
    public CalendarResource(CalendarService calendarService) {
        this.calendarService = calendarService;
    }

    @GET
    @Path("/{calendarToken}.ics")
    @PermitAll
    @Produces("text/calendar;charset=UTF-8")
    public Response getCalendarFeed(@PathParam("calendarToken") UUID calendarToken) {
        String icsContent = calendarService.generateIcsFeed(calendarToken);
        return Response.ok(icsContent)
                .header("Content-Disposition", "attachment; filename=\"unige-events.ics\"")
                .build();
    }
}
```

### Points d'attention

- **Pas de `@Produces(APPLICATION_JSON)`** au niveau classe — l'unique endpoint retourne `text/calendar`
- **Constructor injection** (`@Inject` sur constructeur) — convention pour les nouveaux fichiers Resource
- **`@PermitAll`** — l'authentification est assurée par le token dans l'URL
- **`Content-Disposition`** — force le téléchargement avec nom de fichier si appelé depuis un navigateur

---

## Étape 6 — Extension de `UserResource.java`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/UserResource.java`

### 6.1 — Ajouter les imports

```java
import ch.unige.events.dto.calendar.CalendarTokenResponse;
import ch.unige.events.service.CalendarService;
```

### 6.2 — Ajouter l'injection (field injection, cohérent avec le style du fichier)

Dans la zone des `@Inject` existants :

```java
@Inject CalendarService calendarService;
```

### 6.3 — Ajouter les deux méthodes (à la fin de la classe, avant la dernière `}`)

```java
    @GET
    @Path("/me/calendar-token")
    @Authenticated
    public CalendarTokenResponse getMyCalendarToken() {
        return calendarService.getOrCreateToken(identity.getPrincipal().getName());
    }

    @DELETE
    @Path("/me/calendar-token")
    @Authenticated
    public CalendarTokenResponse regenerateMyCalendarToken() {
        return calendarService.regenerateToken(identity.getPrincipal().getName());
    }
```

---

## Étape 7 — Mock `CalendarServiceMock.java`

**Fichier :** `backend/src/test/java/ch/unige/events/service/CalendarServiceMock.java`

```java
package ch.unige.events.service;

import ch.unige.events.dto.calendar.CalendarTokenResponse;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.UUID;

@Mock
@ApplicationScoped
public class CalendarServiceMock extends CalendarService {

    private static final UUID FIXED_TOKEN = UUID.fromString("11111111-2222-3333-4444-555555555555");

    @Override
    public CalendarTokenResponse getOrCreateToken(String auth0Id) {
        return fakeResponse(FIXED_TOKEN);
    }

    @Override
    public CalendarTokenResponse regenerateToken(String auth0Id) {
        return fakeResponse(UUID.randomUUID());
    }

    @Override
    public String generateIcsFeed(UUID calendarToken) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nEND:VCALENDAR\r\n";
    }

    private CalendarTokenResponse fakeResponse(UUID token) {
        String base = "https://10.25.10.136.nip.io/api/calendar/" + token + ".ics";
        return new CalendarTokenResponse(token, base.replace("https://", "webcal://"), base);
    }
}
```

### Mettre à jour `FavoriteServiceCoverageProfile.java`

Ajouter `CalendarServiceMock` dans `quarkus.arc.exclude-types` pour que les tests DB-backed n'utilisent pas le mock :

```java
overrides.put("quarkus.arc.exclude-types",
        "ch.unige.events.service.FavoriteServiceMock," +
        "ch.unige.events.service.ShareServiceMock," +
        "ch.unige.events.service.CalendarServiceMock," +
        "ch.unige.events.resource.*");
```

---

## Étape 8 — Tests coverage `CalendarService`

### 8.1 — `CalendarServiceCoverageProfile.java`

**Fichier :** `backend/src/test/java/ch/unige/events/service/CalendarServiceCoverageProfile.java`

```java
package ch.unige.events.service;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.HashMap;
import java.util.Map;

public class CalendarServiceCoverageProfile implements QuarkusTestProfile {

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
                "ch.unige.events.resource.*");
        return overrides;
    }
}
```

### 8.2 — `CalendarServiceCoverageTest.java` — cas de test

**Fichier :** `backend/src/test/java/ch/unige/events/service/CalendarServiceCoverageTest.java`

Pattern identique à `FavoriteServiceCoverageTest` : `@QuarkusTest`, `@TestProfile(CalendarServiceCoverageProfile.class)`, helpers `persistUser`/`persistEvent`/`persistFavorite`, `@TestTransaction` sur chaque test.

| # | Méthode de test | Scénario | Assertion |
|---|---|---|---|
| 1 | `getOrCreateToken_firstCall_generatesToken` | Utilisateur sans `calendarToken` | `calendarToken` non null après l'appel |
| 2 | `getOrCreateToken_secondCall_returnsSameToken` | Deuxième appel sur même utilisateur | UUID identique aux deux appels (idempotent) |
| 3 | `getOrCreateToken_urlsAreWellFormed` | Appel normal | `webcalUrl` commence par `webcal://`, `httpsUrl` commence par `https://` |
| 4 | `getOrCreateToken_unknownUser_throwsNotFound` | `auth0Id` inconnu | `NotFoundException` levée |
| 5 | `regenerateToken_returnsNewToken` | Appel initial + régénération | Les deux tokens sont **différents** |
| 6 | `regenerateToken_unknownUser_throwsNotFound` | `auth0Id` inconnu | `NotFoundException` levée |
| 7 | `generateIcsFeed_withFavorite_containsVevent` | Favori PUBLISHED | ICS contient `BEGIN:VEVENT` et `SUMMARY:` + titre de l'event |
| 8 | `generateIcsFeed_emptyFavorites_noVevent` | Aucun favori | ICS valide (`BEGIN:VCALENDAR` présent) sans `BEGIN:VEVENT` |
| 9 | `generateIcsFeed_draftEventExcluded` | Favori sur event DRAFT | `BEGIN:VEVENT` absent de l'ICS |
| 10 | `generateIcsFeed_unknownToken_throwsNotFound` | UUID token inconnu | `NotFoundException` levée |

**Note :** `buildIcsContent` et `escapeIcs` sont **package-private** dans `CalendarService` pour permettre des tests unitaires directs dans le même package (`ch.unige.events.service`), sans mock. Ajouter si besoin :

| # | Test unitaire (pas de DB) | Scénario |
|---|---|---|
| 11 | `buildIcsContent_emptyList_returnsValidCalendar` | Liste vide → `BEGIN:VCALENDAR` + `END:VCALENDAR`, pas de VEVENT |
| 12 | `escapeIcs_specialChars_areEscaped` | `"a,b;c\nd"` → `"a\\,b\\;c\\nd"` |

---

## Étape 9 — Extension de `UserResourceTest.java`

Ajouter dans le fichier existant `UserResourceTest.java` :

### 9.1 — Injecter le mock

```java
@Inject
CalendarServiceMock calendarServiceMock;
```

### 9.2 — Nouveaux tests

| # | Test | Endpoint | Assertions |
|---|---|---|---|
| 1 | `getCalendarToken_authenticated_returns200` | `GET /users/me/calendar-token` avec `@TestSecurity` | 200 + champs `calendarToken`, `webcalUrl`, `httpsUrl` présents et non null |
| 2 | `getCalendarToken_unauthenticated_returns401` | `GET /users/me/calendar-token` sans auth | 401 |
| 3 | `regenerateCalendarToken_authenticated_returns200` | `DELETE /users/me/calendar-token` avec `@TestSecurity` | 200 + `calendarToken` non null |

---

## Étape 10 — Mise à jour de la documentation

### `docs/data-model.md` — table User

Ajouter après la ligne `version` :

| `calendarToken` | `calendarToken` | `UUID` | `calendar_token` | nullable, `@Column(unique=true)` — généré à la demande par `CalendarService.getOrCreateToken` |

### `docs/api-contract.md` — table "Endpoints implémentés"

Ajouter (remplacer les entrées correspondantes dans "Planifiés" si elles existent) :

| `GET` | `/users/me/calendar-token` | `@Authenticated` | Token webcal personnel — génère si absent (idempotent) | 200, 401 |
| `DELETE` | `/users/me/calendar-token` | `@Authenticated` | Révoquer et régénérer le token | 200, 401 |
| `GET` | `/calendar/{calendarToken}.ics` | `@PermitAll` | Flux iCalendar des favoris (polled par l'app calendrier) | 200, 404 |

### `docs/sprint-context.md`

Mettre à jour la section Sprint 4 :

```markdown
- [x] Entité `Favorite` (userId, eventId) — SCRUM-89 ✅
- [x] `POST /events/{id}/favorite`, `DELETE /events/{id}/favorite`, `GET /users/me/favorites` — SCRUM-89 ✅
- [x] `GET /events/{id}/share` + `GET /s/{shortCode}` (shortlink redirect) — SCRUM-89 ✅
- [ ] Synchronisation calendrier webcal (SCRUM-89-bis) — en cours
  - `GET /users/me/calendar-token` (idempotent)
  - `DELETE /users/me/calendar-token` (révocation + régénération)
  - `GET /calendar/{calendarToken}.ics` (flux ICS, @PermitAll)
```

Mettre à jour la date "Dernière mise à jour" à la date du commit.

---

## Critères de validation

### Fonctionnels
- `GET /api/users/me/calendar-token` → 200 + `calendarToken` (UUID), `webcalUrl` (commence par `webcal://`), `httpsUrl` (commence par `https://`)
- Deux appels `GET /users/me/calendar-token` → même `calendarToken` (idempotent)
- `DELETE /api/users/me/calendar-token` → 200 + `calendarToken` **différent** de l'ancien
- `GET /api/calendar/{token}.ics` → 200, `Content-Type: text/calendar`, contient `BEGIN:VCALENDAR`
- Token inconnu sur `GET /calendar/{token}.ics` → 404 + `ApiErrorResponse`
- L'ICS liste les événements PUBLISHED favorisés (`SUMMARY:TitreEvent` présent)
- Les événements DRAFT ne sont pas dans l'ICS
- Les endpoints Bearer-auth → 401 sans token

### Qualité SonarCloud
- Couverture ≥ 80% sur le nouveau code (au moins les 10 tests coverage service + 3 tests resource)
- Duplication ≤ 3%
- Security Rating A (pas d'interpolation non-échappée dans l'ICS)
- Reliability Rating A
- Maintainability Rating A

---

## Note — Extension future (hors scope Sprint 4)

Quand SCRUM-88 (Attendance) sera mergé, `CalendarService.generateIcsFeed` pourra être étendu pour inclure les événements `ATTENDING` et `INTERESTED` en plus des favoris :

```java
// À ajouter dans generateIcsFeed après la liste des favoris
List<Event> attendedEvents = Attendance.findByUser(user.id).stream()
        .map(a -> Event.<Event>findByIdOptional(a.eventId))
        .flatMap(Optional::stream)
        .filter(e -> e.status == EventStatus.PUBLISHED)
        .filter(e -> !eventIds.contains(e.id)) // dédupliquer avec les favoris
        .toList();
```

Aucune modification d'endpoint requise — transparent pour le frontend et les apps calendrier.
