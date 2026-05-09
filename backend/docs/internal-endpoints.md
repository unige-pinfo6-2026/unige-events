# Internal endpoints — service-to-service

> Catalogue des endpoints REST **internes** consommés par les REST
> clients `@RegisterRestClient` cross-service. Ces endpoints **ne sont
> pas** exposés via Kong (pas de route dans `k8s/chart/templates/kong/configmap-routes.yaml`)
> et **ne sont pas** dans `openapi/openapi.yaml` (dérogation explicite à
> la règle openapi-first — ils ne font pas partie du contrat public ;
> cf. Décision Q de [`../../specs_archives/specs_claude/specs_microservices_migration_completion.md`](../../specs_archives/specs_claude/specs_microservices_migration_completion.md)).
>
> Convention : tous les endpoints internes sont préfixés par leur
> propre `@Path` racine (ex. `/users/by-auth0/{auth0Id}`) et restent
> accessibles uniquement à l'intérieur du cluster K8s
> (service-to-service via `http://<svc>-service:8080`).

## Endpoint catalog

| Path | Service propriétaire | Service(s) consommateur(s) | Payload réponse | Notes |
|---|---|---|---|---|
| `GET /users/by-auth0/{auth0Id}` | user-service | view-service, me-aggregator-service, autres consommateurs futurs | `UserPublicResponse` (`shared-domain-dtos`) | Lookup user par claim Auth0 (`sub`). Anti-oracle ISSUE-93 désactivé pour les appels internes (le caller est un service de confiance). |
| `GET /users/by-calendar-token/{token}` | user-service | calendar-service | `UserPublicResponse` (id + displayName uniquement, plus le `calendarToken` lui-même) | Token UUID régénérable. Service-to-service uniquement (pas exposé Kong). |
| `GET /users/{id}/follow-counts` | follow-service | user-service (pour enrichir la réponse `getPublicProfile`) | `FollowCounts(long followers, long following, FollowStatus followStatus)` | Fallback `(0, 0, null)` si follow-service down. |
| `GET /events/{id}/capacity-summary` | event-service | attendance-service | `CapacitySummary(Integer capacity, long currentAttending, long waitlistedCount)` | Évite à attendance-service de lire la table `events` cross-schéma. |
| `GET /events?ids=...` | event-service | favorite-service, calendar-service | `List<EventDTO>` | Bulk lookup pour fabrication de réponses `/me/favorites` et ICS feed. |
| `GET /events/{id}/favorite-count` | favorite-service | event-service, stats-service | `{ "count": long }` | Count atomique. |
| `GET /events/{id}/view-count` | view-service | event-service, stats-service | `{ "count": long }` | Count atomique. |
| `GET /events/{eventId}/attendance-summary` | attendance-service | event-service, co-organizer-service, stats-service | `AttendanceSummary(long attending, long waitlisted, long interested)` | Count by status. |
| `GET /events/attendance-summary?ids=...` | attendance-service | favorite-service, calendar-service | `Map<Long, AttendanceSummary>` | Bulk pour fabriquer `EventDTO` enrichi sur `/me/favorites`. |
| `GET /events/{eventId}/co-organizers/check?userId={uuid}` | co-organizer-service | event-service, comment-service, attendance-service, report-service, stats-service | `{ "accepted": boolean }` | Cascade SCRUM-136 centralisée — règle unique côté co-organizer-service. Évite à 5 services d'inliner la logique. |
| `GET /events/{eventId}/co-organizers/accepted-user-ids` | co-organizer-service | stats-service | `List<UUID>` | Liste des userIds accepted (pour stats compute). |

## Convention de resilience

Chaque REST client `@RegisterRestClient` consommant un endpoint interne porte la même resilience :

```java
@RegisterRestClient(configKey = "<svc>-service")
@Path(...)
public interface <Svc>ServiceClient {
    @GET @Path(...)
    @Retry(maxRetries = 3, delay = 200)
    @Timeout(2000)
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
* changent de signature au gré des besoins cross-service (ex. ajouter un champ à `CapacitySummary` n'est pas un breaking change public).

Les ajouter à `openapi.yaml` polluerait le contrat public et ferait voir des endpoints invisibles aux consommateurs externes. La dérogation est actée par Décision Q de la spec de complétion.

## Tracking — invariant `git diff openapi/`

L'invariant `git diff --shortstat origin/main HEAD -- openapi/` doit rester ≤ 32 lignes — la SEULE modification autorisée à `openapi.yaml` est la suppression du doublon `POST /events/{id}/view` (ligne ~3482, Décision Q de la spec de complétion). Toute autre modification d'`openapi.yaml` lèverait un blocker.
