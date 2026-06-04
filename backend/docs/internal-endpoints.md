# Internal endpoints — service-to-service

> *Mentions of the dissolved-services (favorite/view/share/stats/me-aggregator/co-organizer → event-service co-located post-finalization ; follow/calendar → user-service co-located post-finalization ; attendance/comment → engagement-service renamed/co-located post-finalization ; report → moderation-service renamed post-finalization) are intentional historical references — see consolidation-plan.md for the 14→5 mapping.*

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
| 3 | `GET /events/{id}?check-co-org-of={uuid}` | event-service | engagement-service (cascade SCRUM-136 sur post comments + RSVP), moderation-service (cascade pour reports) | `EventDTO` enrichi du champ `coOrganizerOf: bool` | Cascade SCRUM-136 centralisée — règle unique côté event-service (post-merge co-organizer→event en Étape 2.2.4). **Self-check authentifié uniquement** post Étape 6.1 finalization-ultimate (SEC-002 / Décision C) : le param est honoré seulement si caller authentifié + `CallerIdentity.getUuid() == uuid`. Sinon param silencieusement ignoré (`coOrganizerOf=null`). |
| 4 | `GET /users/{id}/attendances?status=ATTENDING` | engagement-service | user-service (calendar ICS feed) | `List<AttendanceDTO>` | Endpoint interne **distinct** du public `/users/me/attendances` (qui agit pour la session courante). Exposé via `UserAttendancesInternalResource` post Étape 2.3 finalization-ultimate (REST-002 / Décision B) — sans cet endpoint, le REST client `EngagementServiceClient.getUserAttendances` 404 au runtime. `@PermitAll` (interne, pas de route Kong, pas dans openapi.yaml). |
| 5 | `GET /events/{id}/organizer-uuids` | event-service | engagement-service (annotation `authorIsOrganizer:bool` sur listing comments), moderation-service (cascade reports) | `List<UUID>` (creator + ACCEPTED co-organizers) | Nouvel endpoint post Étape 3.4 finalization-ultimate (Décision G) — remplace l'ancien `EventCoOrganizerStub.findAcceptedUserIdsForEvent` cross-schéma. Single REST call, pas de N+1 self-check. `@PermitAll`, anti-oracle minimal (404 si BANNED). **MAJ 2026-06-04** : désormais aussi consommé par le **frontend** (`usePublicOrganizers`, équipe organisatrice publique) → **route Kong publique `events-organizer-uuids` ajoutée (docker + helm + k8s) + déclaré dans `openapi.yaml`** (cf. ADR-002 addendum). N'est donc plus purement interne. |
| 6 | `GET /events/_bulk-attendance-summary?ids=42&ids=7` | engagement-service | event-service (bulk capacity gating + listings) | `Map<Long, AttendanceSummary>` | Nouvel endpoint post Étape 3.4 finalization-ultimate (Décision I) — remplace l'ancien `AttendanceStub.countGroupedByStatus(ids, status)` cross-schéma par event-service. Le préfixe `_bulk-` évite l'ambiguïté avec `/events/{eventId}/attendance-summary` (path param). |
| 7 | `GET /users/_internal-attendee-projections?ids=uuid1&ids=uuid2` | user-service | engagement-service (`AttendanceService.getAttendees` filtre confidentialité) | `List<AttendeeProjection>` (`shared-domain-dtos`, `{id, displayName, avatarUrl, profilePublic}`) | Nouveau endpoint SCRUM-S7. Bulk projection bypassant l'anti-oracle ISSUE-93 (interne uniquement, `@Internal`) afin que le consumer puisse anonymiser (`displayName=null`, `avatarUrl=null`, `userId=null` dans `AttendanceDTO`) les lignes pour les non-organisateurs sans faire N+1 vers `GET /users/{id}`. Les ids inconnus (users supprimés) sont silencieusement omis ; le consumer les rend anonymes. `@PermitAll` + `@Internal`, pas de route Kong, pas dans openapi.yaml. |
| 8 | `GET /events/{eventId}/_internal-attendee-ids?status=ATTENDING` | engagement-service | notification-service (`EventCancelledConsumer`, `EventUpdatedConsumer`) | `List<UUID>` | SCRUM-99. JPQL projection sur `attendances.user_id` filtré par status (default ATTENDING — `@DefaultValue` + `EnumParamConverterProvider`). Consommé pour fan-out des notifications `EVENT_CANCELLED` / `EVENT_UPDATED` à tous les inscrits ATTENDING. `@PermitAll` + `@Internal`. Unknown eventId → 200 + `[]` (pas d'oracle d'existence). Token mismatch / missing → 404 same envelope. |
| 9 | `GET /users/_internal-by-auth0-id/{auth0Id}` | user-service | notification-service (`NotificationService.listMine` / `markRead` / `markAllRead`) | `IdProjection` (`shared-domain-dtos`, `{id: UUID}`) | SCRUM-99 Décision E. Résolution `auth0Id → userId` pour les endpoints `/api/users/me/notifications/*` (notification-service stocke `user_id UUID` mais le caller arrive avec un Auth0 sub claim). `@PermitAll` + `@Internal`. Unknown auth0Id → 404 même envelope que token-invalide (anti-oracle). Le REST client porte `abortOn = NotFoundException` + `skipOn = NotFoundException` pour ne pas tripper le CB sur une wave de sign-ins non encore provisionnés. |
| 10 | `GET /comments/{commentId}/_internal-visibility?callerId={uuid}` | engagement-service | moderation-service (`ReportService.createForComment`) | `CommentVisibilityProjection` (`shared-domain-dtos`, `{commentId: Long, eventId: Long, authorId: UUID, callerHasAccess: bool}`) | SCRUM-144 Décision L. Validation cross-service de la visibilité d'un commentaire avant `POST /comments/{id}/report` (moderation-service). Cascade anti-oracle ISSUE-92 + SCRUM-136 ré-utilisée côté engagement via `EventServiceClient.getByIdWithCoOrgCheck` sur l'event parent ; comment inexistant, event invisible OU token mismatch → 404 envelope identique. `@PermitAll` + `@Internal`. Le REST client porte `abortOn = NotFoundException` + `skipOn = NotFoundException` pour ne pas tripper le CB sur une wave de reports légitimes pointant des commentaires invisibles ; fallback throws 503 pour surfacer une erreur explicite au browser. |
| 11 | `GET /users/_internal-followed-ids?followerId={uuid}` | user-service | event-service (`EventResource.getAll` — filtre `followedOnly`), engagement-service (`AttendanceService` — un abonné accepté voit les participations + son identité dans les listes d'inscrits d'un compte privé, MAJ 2026-06-04) | `List<UUID>` | SCRUM-168 Sprint 9. Retourne les UUIDs des utilisateurs suivis par `followerId` avec statut ACCEPTED. `followerId` null → `[]`. Utilisé par event-service pour construire la condition JPQL `e.creatorId IN :followedIds` sur `GET /events?followedOnly=true` ; et par engagement-service pour la garde « abonné accepté » sur `getUserParticipationEvents` + l'exposition d'identité dans `getAttendees` (compte privé). `@PermitAll` + `@Internal`. Fallback du REST client retourne `[]` (dégradation gracieuse : un abonné est alors traité comme non-abonné, jamais de fuite). |
| 12 | `GET /users/_internal-by-usernames?usernames=alice.dosh,bob.smith` | user-service | notification-service (`CommentMentionConsumer`) | `List<IdProjection>` (`shared-domain-dtos`, `{id: UUID, username: String}`) | SCRUM-145. Résolution batchée `@<handle> → UUID` pour le parser de mentions — un seul hop par comment au lieu de N+1 hops `GET /users/by-username/{u}`. Inputs sont trim + lowercase + dédupliqués côté serveur ; cap silencieux à 50 handles par appel (Décision L : un commentaire de 500 chars peut contenir au max ~30 mentions de 16 chars). Les handles non résolus sont silencieusement absents de la réponse (consumer les traite comme "pas de notif"). `@PermitAll` + `@Internal`. Le REST client porte un `@Fallback` qui retourne `List.of()` + WARN log — une mention manquée est acceptable, un consumer crashé ne l'est pas (failure-strategy=ignore côté Kafka channel). |
| 13 | `DELETE /comments/{commentId}/_internal-moderation` | engagement-service | moderation-service (`ReportService.handle` — validation d'un report de commentaire) | `204 No Content` | QA bug batch (bug ③). Hard-delete d'un commentaire signalé quand son report passe `REVIEWED` — l'analogue modération du BAN d'un event. **Bypasse** l'autorisation auteur/organisateur/admin de `CommentService.delete` (le caller est moderation-service sur le canal interne). `@PermitAll` + `@Internal`. Comment inexistant → 404 anti-oracle ; token mismatch / missing → 404 même envelope. Le REST client (`EngagementServiceClient.deleteCommentForModeration`) porte `abortOn = NotFoundException` + `skipOn = NotFoundException` : un 404 = commentaire déjà supprimé = succès idempotent (propagé puis swallowé par `ReportService`) ; `@Fallback` throws 503 sur panne infra. |
| 14 | `GET /comments/_internal-by-ids?ids=1&ids=2` | engagement-service | moderation-service (`ReportService.listByStatus` — enrichissement du dashboard admin) | `List<CommentContentProjection>` (`shared-domain-dtos`, `{id: Long, eventId: Long, content: String}`) | QA bug batch (bug ③). Projection batchée du contenu des commentaires signalés pour que le dashboard admin affiche le corps + deep-link vers l'event. Le segment `_internal-by-ids` est littéral → pas de collision avec les routes path-param `/{commentId}`. Ids inconnus / supprimés silencieusement omis. `@PermitAll` + `@Internal`. Le REST client porte un `@Fallback` qui retourne `List.of()` — enrichissement dégradé (report affiché sans le corps) plutôt qu'une page admin en erreur. |

> **Note Décision J / ADR-002 — sur `GET /events/{id}/organizer-uuids` (entry #5).**
> Cet endpoint reste annoté `@PermitAll` plutôt que `@Internal`.
> Justification + mitigations : cf.
> [`adr/ADR-002-organizer-uuids-permitall.md`](adr/ADR-002-organizer-uuids-permitall.md).
> Sentinel test : `EventDomainSentinelsTest.getOrganizerUuids_bannedEvent_doesNotLeakUuidsToAnyCaller`
> (Étape 24.7.4).
>
> **MAJ 2026-06-04.** Un consommateur **public** (frontend `usePublicOrganizers`,
> équipe organisatrice de la page événement) a été ajouté → une **route Kong
> publique `events-organizer-uuids`** est désormais posée (docker + helm + k8s) et
> l'endpoint est **déclaré dans `openapi.yaml`**. Il n'est donc plus purement
> interne (cf. ADR-002 addendum 2026-06-04).

## Endpoints internes **disparus** post-finalization

Les endpoints internes suivants existaient pré-finalization et **disparaissent** post-consolidation 14→5 (les services concernés ont été absorbés dans event-service ou user-service, donc l'accès devient local) :

| Ancien path | Raison de la suppression |
|---|---|
| `GET /users/by-auth0/{auth0Id}` | Plus nécessaire : event-service / engagement-service / moderation-service résolvent le caller via `GET /users/me`, puis appellent `userServiceClient.getById(uuid)` pour l'enrichissement de profil. |
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
