# Plan de consolidation 14→5 services — finalization PR #158

> *Mentions of the dissolved-services (favorite/view/share/stats/me-aggregator/co-organizer → event-service co-located post-finalization ; follow/calendar → user-service co-located post-finalization ; attendance/comment → engagement-service renamed/co-located post-finalization ; report → moderation-service renamed post-finalization) are intentional historical references — see consolidation-plan.md for the 14→5 mapping.*

> Document de contrat pour la consolidation Étape 2 de
> [`specs_microservices_migration_finalization.md`](../../specs_archives/specs_claude/specs_microservices_migration_finalization.md).
>
> Mis à jour : 2026-05-09. Auteur : Claude Code session finalization.

## TL;DR

13 services métiers actifs → 4 services métiers actifs + 1 placeholder. 9 merges + 2 renames.
~12 commits estimés sur Étape 2 (1 commit par paire de merge ; 100-300 lignes diff par paire ;
build local vert à chaque commit).

## Mapping service source → service cible

| Service source | Service cible | Sous-étape | Justification du regroupement |
|---|---|---|---|
| share-service | event-service | 2.2.1 | 1 endpoint (`/events/{id}/share`) + 1 endpoint anonymous (`/s/{shortCode}`). 0 schéma propre, lit `events.share_code`. La logique « génère un short code → URL frontend » est une **vue** sur l'entité Event. Naturellement co-localisé. |
| view-service | event-service | 2.2.2 | 1 endpoint (`POST /events/{id}/view`) + 1 table `event_views`. Sémantiquement une **statistique** d'un Event ; co-localisation = upsert local idempotent. |
| favorite-service | event-service | 2.2.3 | 2 endpoints + 1 table (`favorites`, relation many-to-many `user × event`). Mettre dans event-service supprime les appels REST circulaires. |
| co-organizer-service | event-service | 2.2.4 | 2 endpoints + 1 table (`event_co_organizers`). **La cascade SCRUM-136 est LA primitive de sécurité d'un Event** — elle est consommée par event/engagement/moderation. Centraliser la règle dans event-service supprime ~5 REST clients. |
| stats-service | event-service | 2.2.5 | 1 endpoint (`GET /events/{id}/stats`). Read-only aggregator qui lit attendances + favorites + views. Post-consolidation, favorites + views vivent localement (0 REST call). |
| me-aggregator-service | event-service | 2.2.6 | 1 endpoint (`GET /users/me/events`). BFF justifié par fan-out multi-domaine — pas de fan-out réel ici, le path est strictement event-domain. me-aggregator est SUPPRIMÉ (cf. Décision H). |
| follow-service | user-service | 2.3.1 | 4 endpoints + 1 table (`follows`, relation many-to-many `user × user`). Sémantiquement, follow est une **propriété de l'identité utilisateur**. Le user-service possède déjà `users` ; absorber `follows` consolide le bounded context « social graph ». |
| calendar-service | user-service | 2.3.2 | 3 endpoints. 0 schéma propre, écrit `users.calendar_token`. La feed ICS est strictement user-centric. Co-localiser dans user-service supprime le besoin d'un endpoint interne `GET /users/by-calendar-token/{token}`. |
| attendance-service | engagement-service | 2.1.1 (rename) | Logique complexe (capacity gating, waitlist, lock pessimiste). Renommé en engagement-service : garde toute son infra, accueille comments. |
| comment-service | engagement-service | 2.4.1 | 4 endpoints + 1 table (`comments`). Engagement (« interactions de participants sur un Event ») est un bounded context cohérent : RSVP + commentaires sont les deux formes de participation active. |
| report-service | moderation-service | 2.1.2 (rename) | 2 endpoints + 1 table (`reports`). Renommé en moderation-service (clarifie le rôle : reports + cleanup automatique = modération). |
| event-service | event-service | (inchangé) | Service principal du domaine Event. |
| user-service | user-service | (inchangé) | Service principal du domaine User (+ Follow + Calendar absorbés). |
| notification-service | notification-service | (inchangé, replicas:0) | Placeholder SCRUM-99, hors scope S8/S9. |

## Mouvements par table

| Table | Owner avant | Owner après | Notes |
|---|---|---|---|
| events | event-service | event-service (inchangé) | — |
| event_tags | event-service | event-service (inchangé) | — |
| event_views | view-service | event-service | merge 2.2.2 |
| favorites | favorite-service | event-service | merge 2.2.3 |
| event_co_organizers | co-organizer-service | event-service | merge 2.2.4 |
| users | user-service | user-service (inchangé) | — |
| user_interests | user-service | user-service (inchangé) | — |
| follows | follow-service | user-service | merge 2.3.1 |
| (none — calendar_token col on users) | calendar-service | user-service | merge 2.3.2 (lit/écrit users.calendar_token) |
| attendances | attendance-service | engagement-service | rename 2.1.1 |
| comments | comment-service | engagement-service | merge 2.4.1 |
| reports | report-service | moderation-service | rename 2.1.2 |

NB : les tables ne bougent **pas physiquement** (schéma `public` partagé, cf. Décision C de la
completion-spec qui défère DB-per-service S9+) ; seul le service propriétaire **logique** change.
La règle Flyway-immutabilité (V1..V17 gravées dans `flyway_schema_history`) est respectée — aucun
ALTER ni DROP n'est généré par cette consolidation.

## Mouvements par endpoint Kong

| Path | HTTP method | Service upstream | Plugins par-route |
|---|---|---|---|
| `/api/users/me`, `/api/users/me/image`, `/api/users/me/banner`, `/api/users/me/calendar-token*`, `/api/users/me/follow-requests`, `/api/users/{uuid}`, `/api/users/{uuid}/follow*`, `/api/users/{uuid}/(followers\|following)`, `/api/follow-requests/*`, `/api/calendar/{token}.ics` | GET, POST, PUT, PATCH, DELETE | **user-service** | `rate-limiting` `policy: local`, `minute: 30` (sur `/api/users/[^/]+/follow$` POST) |
| `/api/events*`, `/api/admin/events*`, `/api/events/search`, `/api/events/featured`, `/api/events/{id}/image`, `/api/events/{id}/share`, `/api/s/{shortCode}`, `/api/events/{id}/view`, `/api/events/{id}/favorite`, `/api/users/me/favorites`, `/api/events/{id}/co-organizers/*`, `/api/users/me/co-organizer-invitations`, `/api/events/{id}/stats`, `/api/users/me/events` | GET, POST, PUT, PATCH, DELETE | **event-service** | `rate-limiting` `policy: local`, `minute: 10` (sur `/api/events$` POST) |
| `/api/events/{id}/attend*`, `/api/users/me/attendances`, `/api/users/me/participations`, `/api/events/{id}/comments`, `/api/comments/{id}` | GET, POST, PUT, PATCH, DELETE | **engagement-service** | `rate-limiting` `policy: local`, `minute: 10` (sur `/api/events/(?:\d+)/comments$` POST) |
| `/api/events/{id}/report`, `/api/admin/reports*` | GET, POST, PATCH | **moderation-service** | (aucun par-route) |

(notification-service : aucune route Kong active — replicas:0.)

## Mouvements par topic Kafka producer

| Topic | Producer avant | Producer après | Sous-étape |
|---|---|---|---|
| events.published, events.cancelled, events.expired | event-service | event-service (inchangé) | — |
| events.banned | report-service | moderation-service | rename 2.1.2 |
| users.followed, users.follow-requested, users.follow-accepted | follow-service | user-service | merge 2.3.1 |
| comments.created | comment-service | engagement-service | merge 2.4.1 |
| co-organizers.invited, co-organizers.accepted | co-organizer-service | event-service | merge 2.2.4 |

(Le consumer `events.banned` reste dans event-service — inchangé. Pattern CDI
`@Observes(during=AFTER_SUCCESS)` également inchangé.)

## Mouvements par fichier Helm/Kong/POM

| Fichier | Action | Étape |
|---|---|---|
| `k8s/chart/templates/kong/configmap-routes.yaml` | Fusion blocs `<X>-service` dans bloc `<Y>-service` cible (move des routes enfants, change `service:` upstream) | 2.1.1, 2.1.2, 2.2.1-2.2.6, 2.3.1, 2.3.2, 2.4.1 |
| `k8s/chart/templates/<X>-service/` (×9 dossiers à supprimer : share/view/favorite/co-organizer/stats/me-aggregator/follow/calendar/comment) | `git rm -r` du dossier | Pendant chaque merge 2.X |
| `k8s/chart/templates/attendance-service/` → `k8s/chart/templates/engagement-service/` | `git mv` + sed selectors/labels | 2.1.1 |
| `k8s/chart/templates/report-service/` → `k8s/chart/templates/moderation-service/` | `git mv` + sed selectors/labels | 2.1.2 |
| `k8s/chart/values.yaml` | Vérification post-2.5 (pas de clé `image.<svc>.*` orpheline ; depuis Étape 12.4 completion il n'y a plus de clé per-service) | 2.5.1 |
| `backend/pom.xml` | Suppression module `<X>-service` après chaque merge ; rename `attendance-service` → `engagement-service` ; rename `report-service` → `moderation-service` | 2.X (à chaque sous-étape) |
| `.github/workflows/build.yml` | (inchangé en Étape 2 — refonte matrix livrée Étape 7) | — |
| `.github/workflows/deploy.yml` | (inchangé — déjà `--set image.tag=`) | — |

## Risques de régression

| Risque | Probabilité | Impact | Mitigation |
|---|---|---|---|
| **Rename packages incomplet** (Étape 2 sed manqué) | Medium | High | Build local après chaque merge (`./mvnw -pl services/<Y>-service -am test -DskipITs`) ; rollback trivial avant push (`git reset --hard HEAD~1`). |
| **Conflit de classe homonyme** (ex. `ApiErrorResponse` local share + local event après merge 2.2.1) | Medium | Medium | Le sous-package `ch.unige.events.<Y>.<X>.dto.ApiErrorResponse` évite la collision ; la migration vers `shared-api-error` (Étape 4.0) supprime les copies locales. |
| **Stub JPA cross-service oublié post-merge** | Medium | Medium | Garder les stubs cross-service intacts pendant Étape 2 ; les nettoyer en bloc en Étape 4 (REST clients). Les stubs qui pointent vers tables maintenant locales (ex. `EventStub` dans share→event après 2.2.1) deviennent l'entité réelle ou sont supprimés au moment du merge. |
| **Kafka producer déménagé sans channel `application.properties`** | Low | High | Vérifier l'`application.properties` cible après chaque merge avec producer Kafka (2.2.4, 2.3.1, 2.4.1, rename 2.1.2). |
| **Kong route oubliée** (nouveau bloc consumer→provider non actualisé) | Medium | Medium | Vérification manuelle de `k8s/chart/templates/kong/configmap-routes.yaml` après chaque merge (5 blocs services attendus à la fin). |
| **Dépendances Maven manquantes** post-merge (ex. event-service consume now-Kafka deps from co-organizer) | Medium | Medium | Diff `pom.xml` source vs cible avant chaque merge ; ajouter explicitement les `<dependency>` manquantes. |
| **Test legacy référence un stub supprimé** | High | Low (Étape 5 plus tard) | Pas un risque de l'Étape 2 — les tests legacy sont reportés en Étape 5 (port). |

## Ordre d'exécution strict

```
2.1.1  rename attendance-service → engagement-service          (1 commit)
2.1.2  rename report-service     → moderation-service          (1 commit)
2.2.1  merge share-service       → event-service               (1 commit)
2.2.2  merge view-service        → event-service               (1 commit)
2.2.3  merge favorite-service    → event-service               (1 commit)
2.2.4  merge co-organizer-service → event-service              (1 commit)
2.2.5  merge stats-service       → event-service               (1 commit)
2.2.6  merge me-aggregator-service → event-service             (1 commit)
2.3.1  merge follow-service      → user-service                (1 commit)
2.3.2  merge calendar-service    → user-service                (1 commit)
2.4.1  merge comment-service     → engagement-service          (1 commit)
2.5    cleanup + reactor verify  (1 commit si nettoyages requis ; sinon n/a)
```

**Topology cible atteinte à fin Étape 2** :

- 5 services métiers : `event-service`, `user-service`, `engagement-service`, `moderation-service`,
  `notification-service` (placeholder).
- 10 shared libs (inchangées) : `shared-rate-limit`, `shared-storage`, `shared-api-error`,
  `shared-domain-enums`, `shared-domain-dtos`, `shared-domain-projections`, `shared-jaxrs`,
  `shared-tracing`, `shared-kafka-events`, `shared-platform`.
- Total reactor : **15 modules** (vs 24 avant). Étape 6 ajoutera contract-tests + e2e = 17.
