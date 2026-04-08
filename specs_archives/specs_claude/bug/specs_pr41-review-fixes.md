# Specs PR #41 — Fixes post-review lead technique

> **Branche :** `feature/s4-attendance-favorites-calendar`
> **Sprint :** 4 — Correctifs post-review · Epic 4 – Engagement & Interaction
> **Prérequis :** SCRUM-88 + SCRUM-89 + SCRUM-89-bis implémentés (tous sur cette branche)
> **Règle d'or :** Modifier `openapi/openapi.yaml` EN PREMIER sur les fixes qui touchent aux endpoints, puis coder Service → Resource → Tests → Docs
> **Ordre d'implémentation suggéré :** Fix 7 → Fix 4 → Fix 5 → Fix 6 → Fix 1 → Fix 2 → Fix 3

---

## Résumé des 7 correctifs

| # | Problème | Fichiers principaux | Impact |
|---|---|---|---|
| 1 | NPE sur `POST /events/{id}/attend` avec body null | `AttendanceResource`, `openapi.yaml`, tests | Bug runtime → 500 au lieu de 400 |
| 2 | Inscription possible sur event DRAFT | `AttendanceService`, `openapi.yaml`, tests | Incohérence données / calendrier |
| 3 | `DELETE /me/calendar-token` → `POST /me/calendar-token/regenerate` | `UserResource`, `openapi.yaml`, tests | Sémantique REST incorrecte |
| 4 | `frontendUrl` dupliqué dans deux services | `AppConfig` (nouveau), `CalendarService`, `ShareService`, `application.properties` | Maintenabilité / DRY |
| 5 | `buildIcsContent` dans `CalendarService` — doit aller dans `util/` | `IcsBuilder` (nouveau), `CalendarService`, tests | Maintenabilité / SRP |
| 6 | Fuseau horaire incorrect dans le flux ICS | `IcsBuilder` (après Fix 5), tests | Bug d'affichage dans l'app calendrier |
| 7 | API inaccessible depuis l'extérieur du devcontainer | `application.properties` | Dev UX |

---

## Ce qui est à créer

| Fichier | Action |
|---|---|
| `backend/src/main/java/ch/unige/events/config/AppConfig.java` | Nouvelle interface `@ConfigMapping` — config centralisée `frontendUrl` |
| `backend/src/main/java/ch/unige/events/util/IcsBuilder.java` | Nouvelle classe utilitaire statique — `buildIcsContent`, `foldLine`, `escapeIcs` |
| `backend/src/test/java/ch/unige/events/util/IcsBuilderTest.java` | Tests unitaires purs (sans `@QuarkusTest`) pour `IcsBuilder` |

---

## Ce qui est à modifier

| Fichier | Modification |
|---|---|
| `backend/src/main/resources/application.properties` | Fix 7 : `%dev.quarkus.http.host=0.0.0.0` + Fix 4 : `app.frontend.url` défaut `http://localhost:5173` |
| `openapi/openapi.yaml` | Fix 1 : 400 documenté sur `POST /events/{id}/attend` · Fix 2 : 400 event non publié · Fix 3 : remplacer `delete /users/me/calendar-token` par `post /users/me/calendar-token/regenerate` |
| `backend/src/main/java/ch/unige/events/resource/AttendanceResource.java` | Fix 1 : `@NotNull` sur `request` |
| `backend/src/main/java/ch/unige/events/service/AttendanceService.java` | Fix 2 : vérification `EventStatus.PUBLISHED` |
| `backend/src/main/java/ch/unige/events/resource/UserResource.java` | Fix 3 : `@DELETE` → `@POST` + nouveau path |
| `backend/src/main/java/ch/unige/events/service/CalendarService.java` | Fix 4 : injecter `AppConfig` · Fix 5 : déléguer à `IcsBuilder` · supprimer méthodes utilitaires |
| `backend/src/main/java/ch/unige/events/service/ShareService.java` | Fix 4 : injecter `AppConfig` · supprimer `@ConfigProperty` inline |
| `backend/src/test/java/ch/unige/events/resource/AttendanceResourceTest.java` | Fix 1 : test body null → 400 · Fix 2 : test event DRAFT → 400 |
| `backend/src/test/java/ch/unige/events/service/AttendanceServiceCoverageTest.java` | Fix 2 : test `attend_draftEvent_throwsBadRequest` |
| `backend/src/test/java/ch/unige/events/resource/UserResourceTest.java` | Fix 3 : `DELETE` → `POST /me/calendar-token/regenerate` |
| `backend/src/test/java/ch/unige/events/service/AttendanceServiceMock.java` | Fix 2 : flag `forceDraftConflict` (si mock nécessaire) |
| `backend/src/test/java/ch/unige/events/service/CalendarServiceCoverageTest.java` | Fix 5/6 : adapter les appels aux méthodes utilitaires déplacées vers `IcsBuilder` |
| `backend/docs/api-contract.md` | Fix 3 : corriger endpoint régénération token calendrier |
| `backend/docs/sprint-context.md` | Marquer les 7 fixes comme terminés |

---

## Fix 7 — `%dev.quarkus.http.host=0.0.0.0` (à faire EN PREMIER)

**Problème :** En mode `quarkus:dev`, le serveur écoute sur `127.0.0.1` par défaut — l'API est inaccessible depuis l'extérieur du devcontainer (Postman, frontend, autre container).

**Fichier :** `backend/src/main/resources/application.properties`

Ajouter à la **ligne 3** (après `quarkus.http.port=8080`), avant le bloc `# Datasource` :

```properties
%dev.quarkus.http.host=0.0.0.0
```

Résultat attendu du bloc en tête du fichier :

```properties
quarkus.http.root-path=api
quarkus.http.port=8080
%dev.quarkus.http.host=0.0.0.0

# Datasource
...
```

**Aucun test à modifier — changement de config dev uniquement.**

---

## Fix 4 — Centraliser `frontendUrl` dans `AppConfig`

### Étape 4.1 — Vérifier le style existant dans `config/`

Le package `ch.unige.events.config` contient déjà `OpenApiSecurityConfig.java` (annotation uniquement, pas de `@ConfigMapping`). Le projet utilise SmallRye Config (inclus dans Quarkus) — `@ConfigMapping` est disponible sans dépendance supplémentaire.

### Étape 4.2 — Créer `AppConfig.java`

**Fichier :** `backend/src/main/java/ch/unige/events/config/AppConfig.java`

```java
package ch.unige.events.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    @WithDefault("http://localhost:5173")
    String frontendUrl();
}
```

> **Pourquoi `@ConfigMapping` et pas `@ConfigProperty` inline ?**
> `@ConfigMapping` groupe toutes les propriétés `app.*` en un seul bean injectable, élimine la duplication, et est la pratique recommandée SmallRye/Quarkus pour les configs structurées.

### Étape 4.3 — Mettre à jour `application.properties`

Remplacer la ligne existante :
```properties
app.frontend.url=https://10.25.10.136.nip.io
```
par :
```properties
# URL du frontend — défaut local pour dev, à surcharger en prod via variable d'environnement APP_FRONTEND_URL
app.frontend.url=http://localhost:5173
%prod.app.frontend.url=https://10.25.10.136.nip.io
```

> Le profil `%prod` garantit que la prod utilise l'URL NIP. En dev, le défaut `http://localhost:5173` correspond au Vite dev server du frontend.

### Étape 4.4 — Modifier `CalendarService.java`

Supprimer :
```java
@ConfigProperty(name = "app.frontend.url", defaultValue = "https://10.25.10.136.nip.io")
String frontendUrl;
```

Ajouter l'injection (field injection — cohérent avec le style du fichier) :
```java
@Inject
AppConfig appConfig;
```

Remplacer tous les usages de `frontendUrl` par `appConfig.frontendUrl()` :
- Dans `buildTokenResponse` : `String httpsUrl = appConfig.frontendUrl() + "/api/calendar/" + token + ".ics";`
- Dans `buildIcsContent` : `sb.append(foldLine("URL:" + appConfig.frontendUrl() + "/events/" + event.id));`
  *(cette ligne disparaîtra au Fix 5 — `frontendUrl` sera passé en paramètre à `IcsBuilder`)*

Imports à ajouter :
```java
import ch.unige.events.config.AppConfig;
import jakarta.inject.Inject;
```
*(supprimer `import org.eclipse.microprofile.config.inject.ConfigProperty;`)*

### Étape 4.5 — Modifier `ShareService.java`

Même principe :

Supprimer :
```java
@ConfigProperty(name = "app.frontend.url", defaultValue = "https://10.25.10.136.nip.io")
String frontendUrl;
```

Ajouter :
```java
@Inject
AppConfig appConfig;
```

Remplacer `frontendUrl` par `appConfig.frontendUrl()` dans `getShareInfo` :
```java
String shareUrl = appConfig.frontendUrl() + "/events/" + eventId;
```

Imports à ajouter/modifier :
```java
import ch.unige.events.config.AppConfig;
import jakarta.inject.Inject;
```
*(supprimer `import org.eclipse.microprofile.config.inject.ConfigProperty;`)*

### Tests impactés (Fix 4)

Les mocks `CalendarServiceMock` et `ShareServiceMock` étendent les classes de service — ils n'injectent pas `AppConfig` directement. Aucun test cassé attendu. Si un test de coverage (`CalendarServiceCoverageTest`, `FavoriteServiceCoverageTest`) échoue sur la valeur de `frontendUrl`, ajouter dans le profil de test concerné :
```java
overrides.put("app.frontend.url", "http://localhost:5173");
```

---

## Fix 5 — Extraire les utilitaires ICS dans `IcsBuilder`

### Étape 5.1 — Créer `IcsBuilder.java`

**Fichier :** `backend/src/main/java/ch/unige/events/util/IcsBuilder.java`

```java
package ch.unige.events.util;

import ch.unige.events.entity.Event;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class IcsBuilder {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");
    private static final ZoneId UTC = ZoneId.of("UTC");
    private static final DateTimeFormatter ICS_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    private IcsBuilder() {}

    public static String buildIcsContent(List<Event> events, String frontendUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//UNIGE Events//UNIGE Events API//FR\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:Mes \u00e9v\u00e9nements UNIGE\r\n");
        sb.append("X-WR-TIMEZONE:Europe/Zurich\r\n");

        for (Event event : events) {
            // Conversion UTC → Europe/Zurich (Fix 6)
            ZonedDateTime startZurich = event.startDate.atZone(UTC).withZoneSameInstant(ZURICH);
            ZonedDateTime endZurich   = event.endDate.atZone(UTC).withZoneSameInstant(ZURICH);

            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:").append(event.id).append("@unige-events\r\n");
            sb.append(foldLine("SUMMARY:" + escapeIcs(event.title)));
            sb.append("DTSTART;TZID=Europe/Zurich:").append(startZurich.format(ICS_DT)).append("\r\n");
            sb.append("DTEND;TZID=Europe/Zurich:").append(endZurich.format(ICS_DT)).append("\r\n");
            if (event.location != null) {
                sb.append(foldLine("LOCATION:" + escapeIcs(event.location)));
            }
            if (event.description != null) {
                sb.append(foldLine("DESCRIPTION:" + escapeIcs(event.description)));
            }
            sb.append(foldLine("URL:" + frontendUrl + "/events/" + event.id));
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    /**
     * RFC 5545 §3.1 — replie les lignes de plus de 75 caractères.
     */
    public static String foldLine(String line) {
        if (line.length() <= 75) {
            return line + "\r\n";
        }
        StringBuilder result = new StringBuilder();
        result.append(line, 0, 75).append("\r\n");
        int i = 75;
        while (i < line.length()) {
            int end = Math.min(i + 74, line.length());
            result.append(' ').append(line, i, end).append("\r\n");
            i = end;
        }
        return result.toString();
    }

    /**
     * RFC 5545 §3.3.11 — échappe les caractères spéciaux des valeurs TEXT.
     */
    public static String escapeIcs(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
```

> **Classe `final` avec constructeur privé** — pattern utilitaire statique, cohérent avec les conventions Java.
> `buildIcsContent` reçoit `frontendUrl` en paramètre (pas d'injection CDI dans une classe statique).

### Étape 5.2 — Simplifier `CalendarService.java`

Après Fix 4 et Fix 5, `CalendarService` devient :

```java
// Dans generateIcsFeed — remplacer :
return buildIcsContent(events);
// par :
return IcsBuilder.buildIcsContent(events, appConfig.frontendUrl());
```

Supprimer de `CalendarService` :
- La constante `ICS_DT`
- Les méthodes `buildIcsContent`, `foldLine`, `escapeIcs`

Import à ajouter :
```java
import ch.unige.events.util.IcsBuilder;
```

### Étape 5.3 — Créer `IcsBuilderTest.java`

**Fichier :** `backend/src/test/java/ch/unige/events/util/IcsBuilderTest.java`

Test unitaire pur — **pas de `@QuarkusTest`**, pas de DB, pas de profil. JUnit 5 classique.

| # | Méthode de test | Scénario | Assertion |
|---|---|---|---|
| 1 | `buildIcsContent_emptyList_returnsValidCalendar` | Liste vide | Contient `BEGIN:VCALENDAR` et `END:VCALENDAR`, **pas** de `BEGIN:VEVENT` |
| 2 | `buildIcsContent_singleEvent_containsVevent` | 1 event PUBLISHED | Contient `BEGIN:VEVENT`, `SUMMARY:` + titre, `DTSTART;TZID=Europe/Zurich:` |
| 3 | `buildIcsContent_timezone_utcToZurich` | Event à 7h UTC (hiver, UTC+1) | `DTSTART` contient `T080000` (7h UTC → 8h Zurich en hiver) |
| 4 | `buildIcsContent_timezone_utcToZurichSummer` | Event à 7h UTC (été, UTC+2) | `DTSTART` contient `T090000` (7h UTC → 9h Zurich en été) |
| 5 | `buildIcsContent_urlContainsFrontendUrl` | Event avec frontendUrl custom | `URL:` contient le frontendUrl passé en paramètre |
| 6 | `foldLine_shortLine_noFolding` | Ligne ≤ 75 chars | Retourné tel quel + `\r\n` |
| 7 | `foldLine_longLine_foldedAt75` | Ligne > 75 chars | Première partie 75 chars, continuation avec espace |
| 8 | `escapeIcs_specialChars_areEscaped` | `"a,b;c\nd\\"` | `"a\\,b\\;c\\nd\\\\"` |
| 9 | `escapeIcs_null_returnsEmpty` | `null` | Retourne `""` |

Squelette du fichier de test :

```java
package ch.unige.events.util;

import ch.unige.events.entity.Event;
import ch.unige.events.entity.EventStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IcsBuilderTest {

    private static Event buildEvent(String title, int yearStart, int monthStart, int dayStart,
                                    int hour, int min) {
        Event e = new Event();
        e.id = 1L;
        e.title = title;
        e.status = EventStatus.PUBLISHED;
        e.startDate = LocalDateTime.of(yearStart, monthStart, dayStart, hour, min);
        e.endDate = e.startDate.plusHours(2);
        return e;
    }

    @Test
    void buildIcsContent_emptyList_returnsValidCalendar() {
        String ics = IcsBuilder.buildIcsContent(List.of(), "http://localhost:5173");
        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("END:VCALENDAR"));
        assertFalse(ics.contains("BEGIN:VEVENT"));
    }

    @Test
    void buildIcsContent_singleEvent_containsVevent() {
        Event e = buildEvent("Test Event", 2025, 6, 15, 7, 0); // 7h UTC, été → 9h Zurich
        String ics = IcsBuilder.buildIcsContent(List.of(e), "http://localhost:5173");
        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("SUMMARY:Test Event"));
        assertTrue(ics.contains("DTSTART;TZID=Europe/Zurich:"));
    }

    @Test
    void buildIcsContent_timezone_utcToZurichSummer() {
        // Été (UTC+2) : 7h UTC → 9h Zurich
        Event e = buildEvent("Summer Event", 2025, 6, 15, 7, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "http://localhost:5173");
        assertTrue(ics.contains("DTSTART;TZID=Europe/Zurich:20250615T090000"),
                "Expected 09:00 Zurich (summer UTC+2), got: " + ics);
    }

    @Test
    void buildIcsContent_timezone_utcToZurichWinter() {
        // Hiver (UTC+1) : 7h UTC → 8h Zurich
        Event e = buildEvent("Winter Event", 2025, 1, 15, 7, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "http://localhost:5173");
        assertTrue(ics.contains("DTSTART;TZID=Europe/Zurich:20250115T080000"),
                "Expected 08:00 Zurich (winter UTC+1), got: " + ics);
    }

    @Test
    void buildIcsContent_urlContainsFrontendUrl() {
        Event e = buildEvent("Link Event", 2025, 6, 15, 10, 0);
        String ics = IcsBuilder.buildIcsContent(List.of(e), "https://myapp.example.com");
        assertTrue(ics.contains("URL:https://myapp.example.com/events/1"));
    }

    @Test
    void foldLine_shortLine_noFolding() {
        String result = IcsBuilder.foldLine("SUMMARY:Short");
        assertEquals("SUMMARY:Short\r\n", result);
    }

    @Test
    void foldLine_longLine_foldedAt75() {
        String longLine = "X-CUSTOM:" + "A".repeat(100);
        String result = IcsBuilder.foldLine(longLine);
        assertTrue(result.contains("\r\n "), "Folded line must contain CRLF + space continuation");
        // Première ligne : exactement 75 caractères
        String firstLine = result.substring(0, result.indexOf("\r\n"));
        assertEquals(75, firstLine.length());
    }

    @Test
    void escapeIcs_specialChars_areEscaped() {
        assertEquals("a\\,b\\;c\\nd\\\\", IcsBuilder.escapeIcs("a,b;c\nd\\"));
    }

    @Test
    void escapeIcs_null_returnsEmpty() {
        assertEquals("", IcsBuilder.escapeIcs(null));
    }
}
```

### Étape 5.4 — Mettre à jour `CalendarServiceCoverageTest.java`

Les tests qui appelaient `calendarService.buildIcsContent(...)`, `calendarService.foldLine(...)` ou `calendarService.escapeIcs(...)` directement (méthodes package-private) doivent être mis à jour pour appeler `IcsBuilder.buildIcsContent(...)` etc. (méthodes `public static`).

Ces méthodes ne sont plus testées via le service — elles le sont via `IcsBuilderTest`. Supprimer les tests unitaires directs de ces méthodes dans `CalendarServiceCoverageTest` si redondants avec `IcsBuilderTest`.

---

## Fix 6 — Corriger le fuseau horaire dans le flux ICS

### Analyse du problème

`Event.startDate` est un `LocalDateTime` — **sans information de timezone**. Le frontend et le backend créent les dates sans offset explicite. D'après le comportement observé (un event à 7h UTC s'affiche à 7h dans le calendrier au lieu de 9h), les dates sont stockées **en UTC** par Hibernate (comportement par défaut PostgreSQL TIMESTAMP WITHOUT TIME ZONE avec Java LocalDateTime).

Le tag `DTSTART;TZID=Europe/Zurich:` dans l'ICS indique à l'app calendrier que la valeur est en heure Zurich — mais si la valeur est 07:00 en UTC, l'app l'affiche à 07:00 Zurich alors qu'elle devrait afficher 09:00.

### Solution (intégrée dans Fix 5 — `IcsBuilder.buildIcsContent`)

La conversion est déjà incluse dans le code `IcsBuilder.java` fourni ci-dessus (Fix 5) :

```java
ZonedDateTime startZurich = event.startDate.atZone(UTC).withZoneSameInstant(ZURICH);
ZonedDateTime endZurich   = event.endDate.atZone(UTC).withZoneSameInstant(ZURICH);
```

Cette ligne :
1. Interprète `LocalDateTime` comme UTC (`.atZone(UTC)`)
2. Convertit vers Europe/Zurich (`.withZoneSameInstant(ZURICH)`) — gère automatiquement UTC+1 (hiver) et UTC+2 (été)
3. Formate en `yyyyMMdd'T'HHmmss` pour le tag `DTSTART;TZID=Europe/Zurich:`

> **Vérification :** Les tests `buildIcsContent_timezone_utcToZurichSummer` et `buildIcsContent_timezone_utcToZurichWinter` dans `IcsBuilderTest` valident ce comportement.

### Supprimer l'ancienne `ICS_DT` de `CalendarService`

La constante `private static final DateTimeFormatter ICS_DT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");` dans `CalendarService` est remplacée par celle dans `IcsBuilder`. Supprimer de `CalendarService`.

---

## Fix 1 — NPE sur POST /events/{id}/attend avec body null

### Étape 1.1 — `openapi/openapi.yaml` (EN PREMIER)

Dans le path `POST /events/{id}/attend` (ligne ~1191), le `requestBody` est déjà `required: true`. Ajouter le code `400` dans les réponses :

```yaml
        '400':
          description: Body absent ou invalide (status manquant ou null) / événement non publié
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

Insérer ce bloc **avant** le `'401'` existant.

> **Note :** Ce même `400` couvrira aussi Fix 2 (event DRAFT) — un seul ajout dans le YAML suffit pour les deux fixes.

### Étape 1.2 — `AttendanceResource.java`

Modifier la signature de `attend` — ajouter `@NotNull` :

```java
// Avant :
public Response attend(@PathParam("id") Long id,
                       @Valid AttendanceRequest request) {

// Après :
public Response attend(@PathParam("id") Long id,
                       @NotNull @Valid AttendanceRequest request) {
```

Import à ajouter si absent :
```java
import jakarta.validation.constraints.NotNull;
```

> **Pourquoi `@NotNull` résout le problème ?**
> JAX-RS injecte `null` si le body est absent. `@Valid` seul ne vérifie pas la nullité du paramètre lui-même. `@NotNull` déclenche une `ConstraintViolationException` interceptée par `ConstraintViolationExceptionMapper` → 400.

### Étape 1.3 — Vérifier `ConstraintViolationExceptionMapper`

Le mapper existant (`backend/src/main/java/ch/unige/events/exception/mapper/ConstraintViolationExceptionMapper.java`) gère déjà les `ConstraintViolationException` → 400. Aucune modification requise.

### Étape 1.4 — `AttendanceResourceTest.java`

Ajouter un test après les tests existants du bloc `POST /events/{id}/attend` :

```java
@Test
@TestSecurity(user = "auth0|alice")
void attend_nullBody_returns400() {
    Event event = attendanceServiceMock.seedEvent("Conférence UNIGE NPE");

    given()
            .contentType(ContentType.JSON)
            // Pas de .body() — body absent → null côté JAX-RS
            .when().post("/events/{id}/attend", event.id)
            .then()
            .statusCode(400);
}
```

> **À noter :** Le test `attend_missingStatus_returns400` existant couvre `{}` (body présent, champ null). Le nouveau test couvre l'absence totale de body.

---

## Fix 2 — Inscription sur event DRAFT

### Étape 2.1 — `openapi/openapi.yaml`

Le code `400` a déjà été ajouté à l'étape 1.1. La description inclut `/ événement non publié`. Aucun ajout YAML supplémentaire.

### Étape 2.2 — `AttendanceService.java`

Ajouter la vérification après la récupération de l'event (après la ligne `~26`) :

```java
@Transactional
public AttendanceDTO attend(String auth0Id, Long eventId, AttendanceStatus status) {
    Event event = Event.<Event>findByIdOptional(eventId)
            .orElseThrow(() -> new NotFoundException("Event not found"));

    // Vérification statut — uniquement les events PUBLISHED
    if (event.status != EventStatus.PUBLISHED) {
        throw new BadRequestException("Cannot attend a non-published event");
    }

    // ... reste de la méthode inchangé
```

Imports à ajouter :
```java
import ch.unige.events.entity.EventStatus;
import jakarta.ws.rs.BadRequestException;
```

> **`BadRequestException`** est mappé automatiquement par Quarkus/RESTEasy → 400 avec le message dans le body. Vérifier que `BadRequestExceptionMapper` retourne bien un `ApiErrorResponse` — si oui, le frontend recevra `{"error":"bad_request","message":"Cannot attend a non-published event"}`.

### Étape 2.3 — `AttendanceResourceTest.java`

Ajouter un test (nécessite un flag dans le mock pour simuler l'event DRAFT) :

```java
@Test
@TestSecurity(user = "auth0|alice")
void attend_draftEvent_returns400() {
    Event event = attendanceServiceMock.seedEvent("Draft Event");
    AttendanceServiceMock.forceDraftConflict = true;

    given()
            .contentType(ContentType.JSON)
            .body("{\"status\":\"ATTENDING\"}")
            .when().post("/events/{id}/attend", event.id)
            .then()
            .statusCode(400);
}
```

### Étape 2.4 — `AttendanceServiceMock.java`

Ajouter le flag statique et la levée d'exception dans `attend` :

```java
public static volatile boolean forceDraftConflict = false;

// Dans reset() :
forceDraftConflict = false;

// Dans attend(), après forceCapacityConflict :
if (forceDraftConflict) throw new BadRequestException("Cannot attend a non-published event");
```

### Étape 2.5 — `AttendanceServiceCoverageTest.java`

Ajouter un test DB-backed avec un event DRAFT :

```java
@Test
@TestTransaction
void attend_draftEvent_throwsBadRequest() {
    User user = persistUser("auth0|draft1", "draft1@example.com");
    Event event = persistEvent("Draft Event", user, EventStatus.DRAFT, null);

    assertThrows(BadRequestException.class,
            () -> attendanceService.attend("auth0|draft1", event.id, AttendanceStatus.ATTENDING));
}
```

> Le helper `persistEvent` accepte déjà un `EventStatus` en paramètre — vérifier la signature dans `ServiceCoverageTestHelper` ou `AttendanceServiceCoverageTest` et adapter si nécessaire.

---

## Fix 3 — `DELETE /me/calendar-token` → `POST /me/calendar-token/regenerate`

### Étape 3.1 — `openapi/openapi.yaml` (EN PREMIER)

**Supprimer** le bloc `delete:` sous `/users/me/calendar-token` :

```yaml
# Supprimer ce bloc entier :
    delete:
      summary: Révoquer et régénérer le token de calendrier
      ...
```

**Ajouter** un nouveau path (après `/users/me/calendar-token`) :

```yaml
  /users/me/calendar-token/regenerate:
    post:
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
        '404':
          description: Profil utilisateur introuvable (non provisionné — appeler GET /users/me d'abord)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### Étape 3.2 — `UserResource.java`

Remplacer :
```java
@DELETE
@Path("/me/calendar-token")
@Authenticated
public CalendarTokenResponse regenerateMyCalendarToken() {
    return calendarService.regenerateToken(identity.getPrincipal().getName());
}
```

par :
```java
@POST
@Path("/me/calendar-token/regenerate")
@Authenticated
public CalendarTokenResponse regenerateMyCalendarToken() {
    return calendarService.regenerateToken(identity.getPrincipal().getName());
}
```

> La logique service (`calendarService.regenerateToken`) reste inchangée.

### Étape 3.3 — `UserResourceTest.java`

Remplacer l'appel HTTP du test de régénération :

```java
// Avant :
.when().delete("/users/me/calendar-token")

// Après :
.when().post("/users/me/calendar-token/regenerate")
```

Le test doit rester `@TestSecurity(user = "auth0|alice")` avec assertion `statusCode(200)` et `calendarToken` non null.

---

## Mise à jour de la documentation

### `backend/docs/api-contract.md`

Mettre à jour la ligne de l'endpoint de régénération du token calendrier :

| Avant | `DELETE /users/me/calendar-token` | Révoquer et régénérer le token |
| Après | `POST /users/me/calendar-token/regenerate` | Révoquer et régénérer le token |

Ajouter le code `400` documenté sur `POST /events/{id}/attend` si absent du tableau.

### `backend/docs/sprint-context.md`

Ajouter une section fixes PR #41 :

```markdown
### Fixes PR #41 (post-review lead technique)
- [x] Fix 1 — NPE body null `POST /events/{id}/attend` → `@NotNull` sur paramètre
- [x] Fix 2 — Inscription sur event DRAFT bloquée → 400 `BadRequestException`
- [x] Fix 3 — `DELETE /me/calendar-token` → `POST /me/calendar-token/regenerate`
- [x] Fix 4 — `frontendUrl` centralisé dans `AppConfig` (défaut `http://localhost:5173`)
- [x] Fix 5 — `buildIcsContent`/`foldLine`/`escapeIcs` extraits dans `util/IcsBuilder`
- [x] Fix 6 — Conversion UTC → Europe/Zurich dans `IcsBuilder.buildIcsContent`
- [x] Fix 7 — `%dev.quarkus.http.host=0.0.0.0` dans `application.properties`
```

---

## Critères de validation

### Fonctionnels

| Scenario | Résultat attendu |
|---|---|
| `POST /events/{id}/attend` sans body | 400 + `ApiErrorResponse` |
| `POST /events/{id}/attend` avec body `{}` (status null) | 400 + `ApiErrorResponse` |
| `POST /events/{id}/attend` sur event DRAFT | 400 + `ApiErrorResponse` |
| `POST /events/{id}/attend` sur event PUBLISHED | 200 + `AttendanceDTO` |
| `POST /users/me/calendar-token/regenerate` authentifié | 200 + nouveau `calendarToken` UUID |
| `DELETE /users/me/calendar-token` | 405 Method Not Allowed (endpoint supprimé) |
| `GET /calendar/{token}.ics` — event à 7h UTC, été | `DTSTART;TZID=Europe/Zurich:XXXXXXT090000` |
| `GET /calendar/{token}.ics` — event à 7h UTC, hiver | `DTSTART;TZID=Europe/Zurich:XXXXXXT080000` |
| `GET /events/{id}/share` | URL contenant `http://localhost:5173` en dev |
| API accessible depuis l'extérieur du devcontainer | `curl http://<devcontainer-ip>:8080/api/...` répond |

### Qualité SonarCloud (CI doit passer)

- Coverage ≥ 80% sur le nouveau code (`IcsBuilder`, fixes `AttendanceService`, `AttendanceResource`)
- Duplication ≤ 3%
- Security Rating A
- Reliability Rating A
- Maintainability Rating A
- **CI Build Backend doit passer** : `./mvnw verify` sans erreur

### Tests à vérifier manuellement

- `AttendanceResourceTest` : tous les tests passent + 2 nouveaux (Fix 1, Fix 2)
- `AttendanceServiceCoverageTest` : tous les tests passent + 1 nouveau (Fix 2)
- `UserResourceTest` : test régénération token passe avec `POST` (Fix 3)
- `IcsBuilderTest` : 9 tests passent (Fix 5 + Fix 6)
- `CalendarServiceCoverageTest` : tests ICS passent avec les nouvelles assertions timezone
- `FavoriteServiceCoverageTest`, `ShareServiceMock` : aucune régression sur `frontendUrl`

---

## Rappel conventions AGENTS.md applicables

- **camelCase partout** — `frontendUrl()` dans `AppConfig`, pas `frontend_url`
- **spec-first** — `openapi.yaml` modifié AVANT le code pour Fix 1, Fix 2, Fix 3
- **Pas de logique métier dans les Resources** — la vérification DRAFT est dans `AttendanceService`, pas dans `AttendanceResource`
- **`@Transactional` sur toutes les mutations** — `AttendanceService.attend` reste `@Transactional`
- **Pas de fichier SQL de migration** — aucune migration Hibernate nécessaire pour ces fixes
- **Documentation à jour** — `api-contract.md` et `sprint-context.md` mis à jour en même commit
