# Internal endpoints — service-to-service

> Catalogue des endpoints REST **internes** consommés par les REST
> clients `@RegisterRestClient` cross-service post-finalization. Ces
> endpoints **ne sont pas** exposés via Kong (pas de route dans
> `k8s/chart/templates/kong/configmap-routes.yaml`) et **ne sont pas**
> dans `openapi/openapi.yaml`. Cf. Décision G de
> [`../../specs_archives/specs_claude/specs_microservices_migration_finalization.md`](../../specs_archives/specs_claude/specs_microservices_migration_finalization.md)
> qui annule la dérogation Q de la completion-spec : `git diff --shortstat
> origin/main HEAD -- openapi/` doit rester à **0 ligne ABSOLU**.
>
> Convention : tous les endpoints internes restent accessibles uniquement
> à l'intérieur du cluster K8s (service-to-service via
> `http://<svc>-service:8080`).

## Endpoint catalog (post-finalization, après consolidation 14→5)

| # | Path | Service propriétaire | Service(s) consommateur(s) | Payload réponse | Notes |
|---|---|---|---|---|---|
| 1 | `GET /events/{eventId}/attendance-summary` | engagement-service | event-service, moderation-service | `AttendanceSummary` (`shared-domain-dtos`) | Count by status (ATTENDING + WAITLISTED). Nouveau post-finalization (remplace l'ancien `attendance-service.GET /events/{eventId}/attendance-summary`, le service est juste renommé). |
| 2 | `GET /events?ids=...&status=PUBLISHED` | event-service | user-service (calendar ICS feed bulk lookup), engagement-service futur | `List<EventDTO>` | Bulk lookup pour fabriquer la feed ICS de l'utilisateur. |
| 3 | `GET /events/{id}?check-co-org-of={uuid}` | event-service | engagement-service (cascade SCRUM-136 sur post comments + RSVP), moderation-service (cascade pour reports) | `EventDTO` enrichi du champ `coOrganizerOf: bool` | Cascade SCRUM-136 centralisée — règle unique côté event-service (post-merge co-organizer→event en Étape 2.2.4). **Self-check authentifié uniquement** post Étape 6.1 finalization-ultimate (SEC-002 / Décision C) : le param est honoré seulement si caller authentifié + `Auth0IdResolver.resolveUserUuid(jwt) == uuid`. Sinon param silencieusement ignoré (`coOrganizerOf=null`). |
| 4 | `GET /users/{id}/attendances?status=ATTENDING` | engagement-service | user-service (calendar ICS feed) | `List<AttendanceDTO>` | Endpoint interne **distinct** du public `/users/me/attendances` (qui agit pour la session courante). Exposé via `UserAttendancesInternalResource` post Étape 2.3 finalization-ultimate (REST-002 / Décision B) — sans cet endpoint, le REST client `EngagementServiceClient.getUserAttendances` 404 au runtime. `@PermitAll` (interne, pas de route Kong, pas dans openapi.yaml). |
| 5 | `GET /events/{id}/organizer-uuids` | event-service | engagement-service (annotation `authorIsOrganizer:bool` sur listing comments), moderation-service (cascade reports) | `List<UUID>` (creator + ACCEPTED co-organizers) | Nouvel endpoint post Étape 3.4 finalization-ultimate (Décision G) — remplace l'ancien `EventCoOrganizerStub.findAcceptedUserIdsForEvent` cross-schéma. Single REST call, pas de N+1 self-check. `@PermitAll`, anti-oracle minimal (404 si BANNED). |
| 6 | `GET /events/_bulk-attendance-summary?ids=42&ids=7` | engagement-service | event-service (bulk capacity gating + listings) | `Map<Long, AttendanceSummary>` | Nouvel endpoint post Étape 3.4 finalization-ultimate (Décision I) — remplace l'ancien `AttendanceStub.countGroupedByStatus(ids, status)` cross-schéma par event-service. Le préfixe `_bulk-` évite l'ambiguïté avec `/events/{eventId}/attendance-summary` (path param). |

## Endpoints internes **disparus** post-finalization

Les endpoints internes suivants existaient pré-finalization et **disparaissent** post-consolidation 14→5 (les services concernés ont été absorbés dans event-service ou user-service, donc l'accès devient local) :

| Ancien path | Raison de la suppression |
|---|---|
| `GET /users/by-auth0/{auth0Id}` | Plus nécessaire : event-service / engagement-service / moderation-service ont tous accès à `JsonWebToken` localement (résolution via `Auth0IdResolver.resolveUserId(jwt)`) puis appellent `userServiceClient.getById(uuid)` avec UUID résolu. |
| `GET /users/by-calendar-token/{token}` | Plus nécessaire : calendar-service est absorbé dans user-service (Étape 2.3.2), accès local à `users.calendar_token` via `User.findByCalendarToken(...)`. |
| `GET /events/{id}/capacity-summary` | Plus nécessaire : capacity calculée localement dans event-service (qui possède `events.capacity`). |
| `GET /events/{id}/favorite-count` | Plus nécessaire : `favorites` table est locale dans event-service post-2.2.3. |
| `GET /events/{id}/view-count` | Plus nécessaire : `event_views` table est locale dans event-service post-2.2.2. |
| `GET /events/{eventId}/co-organizers/check?userId=` | Remplacé par le param `?check-co-org-of=` sur `GET /events/{id}` (cascade locale post-2.2.4 dans event-service). |
| ~~`GET /events/{eventId}/co-organizers/accepted-user-ids`~~ | **RÉINTRODUIT** post Étape 3.4 finalization-ultimate sous le nom `/events/{id}/organizer-uuids` (Décision G — entry #5 ci-dessus). Nécessaire pour annoter chaque comment auteur avec `authorIsOrganizer:bool` sans N+1 cross-service. |
| `GET /users/{id}/follow-counts` | Plus nécessaire : follow-service est absorbé dans user-service (Étape 2.3.1), `Follow.countFollowersOf(...)` / `Follow.countFollowingOf(...)` sont des appels locaux. |
| `GET /events/attendance-summary?ids=...` (bulk) | Plus nécessaire : favorite-service et calendar-service sont absorbés ; les bulk lookups deviennent des queries locales sur `attendances` cross-service via REST client unique #1. |

## Convention de resilience

Chaque REST client `@RegisterRestClient` consommant un endpoint interne porte la même resilience :

```java
@RegisterRestClient(configKey = "<svc>-service")
@RegisterProvider(ch.unige.events.shared.tracing.RequestIdClientFilter.class)
@Path(...)
public interface <Svc>ServiceClient {
    @GET @Path(...)
    @Retry(maxRetries = 3, delay = 200)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "...")  // ← optionnel selon criticité
    <ResponseType> get(...);

    default <ResponseType> fallback(...) {
        return <ResponseType-vide-ou-degraded>;
    }
}
```

**URL configurée dans `application.properties` du consommateur** :

```properties
quarkus.rest-client.<svc>-service.url=${<SVC>_SERVICE_URL:http://<svc>-service:8080}
```

## Pourquoi les endpoints internes ne sont pas dans openapi.yaml ?

`openapi/openapi.yaml` est le **contrat public** partagé avec le frontend (cf. monorepo openapi-first). Les endpoints internes :

* ne sont jamais appelés par le frontend (pas de route Kong → 404 depuis l'extérieur du cluster) ;
* peuvent évoluer librement sans coordination avec le frontend ;
* changent de signature au gré des besoins cross-service (ex. ajouter un champ à `AttendanceSummary` n'est pas un breaking change public).

Les ajouter à `openapi.yaml` polluerait le contrat public et ferait voir des endpoints invisibles aux consommateurs externes.

## Tracking — invariant `git diff openapi/`

L'invariant `git diff --shortstat origin/main HEAD -- openapi/` doit rester à **0 ligne ABSOLU** post-finalization (Décision G annule la dérogation Q de la completion-spec). Toute modification d'`openapi.yaml` est désormais bloquante. Le doublon `POST /events/{id}/view` (cosmétique) sera nettoyé dans une PR future avec coordination frontend explicite.
