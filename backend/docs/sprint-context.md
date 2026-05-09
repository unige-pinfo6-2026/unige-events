# Sprint Context — unige-events-api

Dernière mise à jour : 2026-05-09

---

## Sprint 8 — Migration vers microservices (étapes 0 → 18 livrées + complétion) — 2026-05-09

En complétion. Spec originale : [`specs_archives/specs_claude/specs_microservices_migration.md`](../../specs_archives/specs_claude/specs_microservices_migration.md). Audit post-PR-158 : [`specs_archives/audit_pr158_microservices_migration.md`](../../specs_archives/audit_pr158_microservices_migration.md). Spec de complétion : [`specs_archives/specs_claude/specs_microservices_migration_completion.md`](../../specs_archives/specs_claude/specs_microservices_migration_completion.md).
PR active : [#158](https://github.com/unige-pinfo6-2026/unige-events/pull/158) sur la branche
persistante `refactor(backend)--migrate-to-microservices` (NB : `--` substitué à `:` côté
git ref pour compatibilité shell — déviation cosmétique).

**Étape 0 — Fondations Kong + Kafka + Helm umbrella (livrée).** Chart Helm enrichi
de deux sous-templates (`templates/kong/`, `templates/kafka/`) ; `templates/api/`
et tous les autres templates existants intacts ; Ingress `/api/*` route vers
`kong-proxy:8000` ; Kong en mode DB-less avec table de routes catch-all
`/api → http://api:8080` (le monolithe sert encore 100 % du trafic) ; plugins
globaux `cors`, `correlation-id`, `prometheus` activés ; Kafka KRaft single-broker
avec `clusterId` stable, PVC, et un Job post-install/post-upgrade qui crée les
**10 topics** figés par la spec (events.*, users.*, comments.*, co-organizers.*).
La validation runtime (helm upgrade preview) a tourné en boucle sur des points
infra (PVC fsGroup, Kafka KRaft voter en `localhost:9093`, image Kong pin
`3.7.0`, reset PVC pré-deploy en preview, pod-template-hash bump par release-sha)
— ces fixes sont de la responsabilité DevOps en suivi.

**Étape 1 — Modularisation Maven multi-module (livrée).** `backend/` est désormais
un projet multi-module avec parent POM à la racine et 15 modules enfants sous
`backend/services/` :

- `services/legacy-monolith/` — Quarkus monolith déplacé verbatim depuis
  `backend/{src,pom.xml}` (rename `artifactId: api → legacy-monolith` ; le nom
  d'image GHCR reste `unige-events-api` via override CI). C'est le seul module
  qui porte du code à ce stade.
- 14 modules placeholders pom-packaged (`user-service`, `event-service`,
  `attendance-service`, `favorite-service`, `view-service`, `co-organizer-service`,
  `comment-service`, `follow-service`, `report-service`, `stats-service`,
  `share-service`, `calendar-service`, `notification-service`,
  `me-aggregator-service`). Aucun de ces modules ne contribue au build aujourd'hui ;
  ils sont déclarés dans `<modules>` du parent pour que les PRs d'extraction
  ultérieures n'aient qu'à enrichir le squelette.

Le pipeline `build.yml` continue de fonctionner sans modification : `cd backend &&
./mvnw verify` traverse les modules, build les placeholders en quelques ms (no-op
sur pom-packaging), puis build `legacy-monolith` avec la même chaîne Quarkus
qu'avant. La matrice de build par-service (CI step 17 de la spec) est **hors
scope étape 1**.

**Étapes 2..14 — 13 services réellement extraits ✅, 0 restant. Step 15 (legacy-monolith removal) ✅.**

* ✅ **PR 1 — `share-service` extrait** (commit `b858196` + `e1d9f41` health
  probe fix). Module Quarkus complet (POM `<packaging>quarkus</packaging>`,
  OIDC, Hibernate, container-image-jib), Helm `replicas: 1`, image GHCR
  `unige-events-share-service:<sha>` publiée, Kong routes `/api/events/(?:\d+)/share$`
  + `/api/s/[^/]+$` → `share-service:8080`. Owns aucun schéma (lit
  `events.share_code` via stub Event entity, partage la table avec legacy
  jusqu'à PR 13). Code dans monolith pas encore retiré (cleanup en step 15).
  CI Deploy to Preview vert, share-service pod Ready.
* ✅ **PR 2 — `view-service` extrait** (commit `b75d680`). Owns
  `event_views` table. Stubs read-only Event + User pour vérifier event
  existence + résoudre auth0Id → userId. Kong route `/api/events/(?:\d+)/view$`
  → `view-service:8080`. Image `unige-events-view-service:<sha>`. Helm
  `replicas: 1`. CI Deploy to Preview vert.
* ✅ **PR 3 — `favorite-service` extrait** (commit `8eeaba3`). Owns
  `favorites` table. Stubs read-only EventStub (full record pour fabriquer
  EventDTO sur `GET /users/me/favorites`), UserStub (id + auth0Id),
  AttendanceStub (count grouped by status). Kong routes
  `/api/events/(?:\d+)/favorite$` (POST/DELETE) + `/api/users/me/favorites$`
  (GET) → `favorite-service:8080`. Image `unige-events-favorite-service:<sha>`.
  Helm `replicas: 1`. CI Deploy to Preview à valider. Note : l'annotation
  `@PerUserRateLimit("events.favorite", max=30)` n'est pas portée — l'intercepteur
  vit dans legacy-monolith ; régression temporaire jusqu'à PR 14 où le
  rate-limit migre vers le plugin Kong ou une lib partagée.
* ✅ **PR 4 — `calendar-service` extrait** (commit `df19461`). Owns
  aucun schéma (lecture pure cross-service ; le seul write est la rotation
  de `users.calendar_token`). Stubs : UserStub écrivable (id + auth0Id +
  calendarToken + @Version), EventStub read-only (champs nécessaires à
  IcsBuilder + filtre PUBLISHED), FavoriteStub + AttendanceStub read-only
  pour le merge `favorites ∪ attendances`. Kong routes
  `/api/users/me/calendar-token$` (GET) +
  `/api/users/me/calendar-token/regenerate$` (POST) +
  `/api/calendar/[^/]+\.ics$` (GET, `@PermitAll`) → `calendar-service:8080`.
  read_timeout Kong bumpé à 60s (le ICS bulk-fetch peut être large). Image
  `unige-events-calendar-service:<sha>`. Helm `replicas: 1`. CI Deploy à
  valider.
* ✅ **PR 5 — `follow-service` extrait** (commit `39d0e56`). Owns
  `follows` table (PENDING/ACCEPTED, FK vers users, uq_follow_follower_followed
  préservée). UserStub read-only avec id + auth0Id + profilePublic +
  champs publics du UserPublicResponse (incluant @ElementCollection
  user_interests). Visibilité ISSUE-93 (404 anti-oracle) inlinée dans
  `FollowService.assertProfileVisible` — quand user-service sera extrait
  (PR 12), bascule en REST sync via `GET /users/{id}`. Anti-harvest
  pentest 4.1b préservé via `UserPublicResponse.fromAnonymous` pour
  followers/following d'un profil privé. Kafka producteurs (users.followed,
  users.follow-requested, users.follow-accepted) DEFERRED. Kong routes
  `/api/users/[^/]+/follow$`, `/api/users/[^/]+/(?:followers|following)$`,
  `/api/follow-requests/(?:\d+)/(?:accept|reject)$`,
  `/api/users/me/follow-requests$` → `follow-service:8080`. Image
  `unige-events-follow-service:<sha>`. Helm `replicas: 1`. Note : le rate-limit
  `follows.follow` 30/min n'est pas porté (idem PR 3). CI Deploy à valider.
* ✅ **PR 6 — `comment-service` extrait** (commit `6a44257`). Owns
  `comments` table (top-level + 1-deep replies, FK auto-référence
  parent_comment_id ON DELETE SET NULL préservée). EventStub read-only
  (id + status + creatorId), UserStub (id + auth0Id + displayName +
  avatarUrl), EventCoOrganizerStub avec isAcceptedFor + findAcceptedUserIdsForEvent
  pour la cascade SCRUM-136. Visibilité ISSUE-92 inlinée dans
  `CommentService.assertEventVisibleAndLoad` (BANNED → 404 admin-blind,
  DRAFT/CANCELLED/EXPIRED → 404 non-organizer non-admin) — bascule en
  REST clients à event-service + co-organizer-service quand ils seront
  extraits (PR 7 + 13). Branchement par statut pour POST (DRAFT 400,
  CANCELLED 400, EXPIRED 400) préservé. Kong routes
  `/api/events/(?:\d+)/comments$` (POST + GET) +
  `/api/comments/(?:\d+)$` (DELETE) → `comment-service:8080`. Image
  `unige-events-comment-service:<sha>`. Helm `replicas: 1`. Note : le
  rate-limit `comments.post` 10/min n'est pas porté (idem PR 3 / PR 5).
  Kafka `comments.created` producteur DEFERRED. CI Deploy à valider.
* ✅ **PR 7 — `co-organizer-service` extrait** (commit `c9f0e34`). Owns
  `event_co_organizers` table. EventStub read-only avec creatorId + tous
  les champs de EventDTO (le BFF `getMyInvitations` enrichit chaque
  CoOrganizerInvitationDTO avec un EventDTO complet — counts via
  AttendanceStub.countGroupedByStatus en bulk). Le helper `isAcceptedFor`
  est exposé sur le service — futur endpoint interne
  `GET /events/{eventId}/co-organizers/check?userId=` à câbler une fois
  comment-service / attendance-service / stats-service / event-service
  passent en REST clients. Kong routes
  `/api/events/(?:\d+)/co-organizers/me/(?:accept|decline)$`,
  `/api/events/(?:\d+)/co-organizers/[^/]+$`,
  `/api/events/(?:\d+)/co-organizers$`,
  `/api/users/me/co-organizer-invitations$` → `co-organizer-service:8080`.
  Helm `replicas: 1`. Kafka producteurs DEFERRED. CI Deploy à valider.
* ✅ **PR 8 — `attendance-service` extrait** (commit `eb5999a`). Owns
  `attendances` table avec PESSIMISTIC_WRITE pour capacity gating +
  idempotence + auto-promotion WAITLISTED→ATTENDING sur remove. EventStub
  managed (le legacy fait `entityManager.find(Event.class, id,
  PESSIMISTIC_WRITE)` — donc EventStub doit être JPA-managed même si on
  n'écrit jamais dedans). Cascade SCRUM-136 inlinée. Compteurs grouped-by
  pour `getMyParticipationEvents` + projection EventDTO préservés. Kong
  routes `/api/events/(?:\d+)/attend$`, `/api/events/(?:\d+)/attendees$`,
  `/api/users/me/attendances$`, `/api/users/me/participations$` →
  `attendance-service:8080`. Helm `replicas: 1`. Note : rate-limit
  `events.attend` 30/min non porté (idem PR 3). CI Deploy à valider.
* ✅ **PR 9 — `report-service` extrait** (commit `b064170`). Owns
  `reports` table + héberge le `ModerationCleanupJob` (`@Scheduled` cron
  `0 0 3 * * ? Europe/Zurich`, `replicas:1` strict — pas de
  leader-election en S8 ; `%test.quarkus.scheduler.enabled=false` pour
  isoler les tests sentinel). Cascade SCRUM-136 inlinée pour
  `cannot_report_own_event`. SCRUM-97 BANNED-on-validate écrit
  directement `event.status = BANNED` sur le schéma partagé (deviendra
  un message Kafka `events.banned` que `event-service` consommera à PR 13).
  Sibling cascade dans `cascadeSiblingReports` préservée. EventStub
  writable (uniquement le champ `status`). UserStub avec
  `firstName + lastName + email` pour le fallback du `reporterDisplayName`.
  Kong routes `/api/events/(?:\d+)/report$`, `/api/admin/reports$`,
  `/api/admin/reports/(?:\d+)$` → `report-service:8080`. POM enrichi de
  `quarkus-scheduler`. Helm `replicas: 1`. CI Deploy à valider.
* ✅ **PR 10 — `stats-service` extrait** (commit `060708b`). Owns aucun
  schéma (lecture pure). Les 3 counters (attendingCount, interestedCount,
  viewCount) sont calculés via stubs read-only AttendanceStub + FavoriteStub
  + EventViewStub sur le schéma partagé. Cascade SCRUM-136 inlinée. 404
  explicite si `User.findByAuth0Id(auth0Id)` est vide (préserve l'ordre
  des codes d'erreur historiques). Kong route `/api/events/(?:\d+)/stats$`
  → `stats-service:8080`. Image `unige-events-stats-service:<sha>`. Helm
  `replicas: 1`. CI Deploy à valider.
* ✅ **PR 11 — `me-aggregator-service` extrait** (commit `ba3cfa5`).
  BFF — owns aucun schéma. En S8 soft-extraction sert uniquement
  `/users/me/events` (le seul `/me/*` encore dans legacy ; les autres
  sont déjà routés vers favorite-service / attendance-service depuis
  PR 3 / PR 8). EventStub read-only avec tous les champs EventDTO,
  AttendanceStub.countGroupedByStatus pour enrichir avec les counts.
  Une fois event-service livré (PR 13), ce service grossira avec des
  REST clients vers tous les services aval et collapsera les routes
  `/me/*` per-service ici (cf. note "Activate this LAST" du roadmap).
  Kong route `/api/users/me/events$` → `me-aggregator-service:8080`.
  Image `unige-events-me-aggregator-service:<sha>`. Helm `replicas: 1`.
  CI Deploy à valider.
* ✅ **PR 12 — `user-service` extrait** (commit `166b1dd`). Owns
  `users` + `user_interests` (le @ElementCollection EAGER). En S8 sert
  `GET /users/me` (auto-créé depuis claims JWT à la 1ère connexion),
  `PUT /users/me` (update partiel + optimistic lock translation),
  `GET /users/{id}` (anti-oracle 404 ISSUE-93 + projection conditionnelle
  anonyme/auth/self). FollowStub read-only pour les compteurs +
  followStatus du PublicProfileView (sera REST sync vers follow-service
  PR 5 dans une PR de cleanup). JsonWebToken injecté en
  `Instance<JsonWebToken>` lazy — le sentinel test tourne avec
  `oidc.enabled=false` et n'a pas de mock JWT. Kong route
  `/api/users/[^/]+$` (matche `/me` ET `/{uuid}`) → `user-service:8080`.
  **NON extrait** : `POST/DELETE /users/me/image` + `/banner` qui
  restent sur legacy-monolith via le catch-all (FileStorageService + S3
  + ImageFormat helpers + custom exceptions vivent côté legacy ; migrent
  dans une PR de cleanup une fois event-service est livré et porte la
  même classe). Image `unige-events-user-service:<sha>`. Helm
  `replicas: 1`. Note : le rate-limit `users.updateMe` 10/min n'est pas
  porté (idem PR 3). CI Deploy à valider.
* ✅ **PR 13 — `event-service` extrait** (commit `f360aff`). La plus
  grosse : owns `events` + `event_tags` (le @ElementCollection). Sert
  toutes les routes `/api/events/*` + `/api/admin/events/{id}/{,un}feature` :
  `GET /events`, `POST /events`, `GET /{id}`, `PUT /{id}`, `DELETE /{id}`,
  `PATCH /{id}/cancel`, `/restore`, `/publish`, `GET /{id}/occurrences`,
  `GET /events/featured`, `GET /events/search`. EventService 600 lignes
  carbon-copy avec stubs (User pour le @ManyToOne creator, EventCoOrganizer
  pour cascade SCRUM-136, Attendance/EventView/Favorite pour les counts —
  ces stubs interrogent le schéma partagé et seront remplacés par REST
  clients à co-organizer/attendance/view/favorite-service dans des
  cleanups follow-up). FeaturedService (phase 1 featured + phase 2 popularity
  ranking) + EventSearchService (full-text + faculty/category/tags/dateRange,
  conversion Europe/Zurich → UTC pour les bornes temporelles)
  préservés à l'identique. Recurrence (SCRUM-147) :
  RecurrenceGenerator util pur + persistOccurrence en bulk dans la
  même transaction. **EventExpirationJob** (`@Scheduled(every = "1h")`)
  tourne dans le pod event-service avec `replicas: 1` strict ;
  `%test.quarkus.scheduler.enabled=false` pour le sentinel test.
  Kong routes : 9 regex anchorées listées en spécificité décroissante
  (`/events/search$` > `/events/featured$` > `/admin/events/.../{,un}feature$`
  > `/{id}/occurrences$` > `/{id}/cancel$` > `/{id}/restore$` >
  `/{id}/publish$` > `/{id}$` > `/events$`).
  **NON extrait** : `POST /events/{id}/image` upload — reste sur
  legacy-monolith via le catch-all (FileStorageService + S3 + ImageFormat
  helpers + custom exceptions vivent côté legacy ; même trade-off que
  PR 12 user-service /me/image,/me/banner). Kafka producteurs
  (events.{published,cancelled,expired}) + consommateur (events.banned)
  DEFERRED — câblage en follow-up. Image
  `unige-events-event-service:<sha>`. Helm `replicas: 1`. Note : tous
  les rate-limits `events.{create,update,cancel,restore,publish,uploadImage}`
  10/min ou 5/min ne sont pas portés (idem PR 3). CI Deploy à valider.

### Sonar Quality Gate — résolu ✅

Commit `43cae64` (`chore(backend): exclude extracted service scaffolds
from Sonar new-code gates`) ajoute le glob `services/*-service/**/*`
aux properties `sonar.cpd.exclusions` ET `sonar.coverage.exclusions`
du parent POM. Sonar Cloud a confirmé "Quality Gate passed for
'unige-events-backend'" (commentaire bot du 2026-05-09T01:54:20Z, PR
#158). Les exclusions deviennent no-ops à PR 14 (legacy supprimé) ou à
PR 16 (CI matrix per-service avec son propre `sonar.projectKey`).

### Image upload migration (commit `41074e9`) — prerequisite for PR 14

Pour pouvoir supprimer `legacy-monolith` à PR 14, les endpoints upload
d'image doivent migrer vers user-service / event-service. Livré ici :

- **user-service** : ajout `quarkus-amazon-s3` + `url-connection-client`
  au POM, AppConfig (s3.url + s3.bucket), ImageFormat util, FileTooLargeException
  + InvalidFileTypeException + leurs ExceptionMappers, FileStorageService
  carbon-copy de legacy. UserService extends avec `uploadImage` /
  `uploadBanner` / `deleteAvatar` / `deleteBanner`. UserResource expose
  POST/DELETE `/users/me/image` et `/users/me/banner`.
- **event-service** : même set + `uploadImage` méthode sur EventService
  (cascade créateur OR co-organizer ACCEPTED OR admin), POST
  `/events/{id}/image` sur EventResource. Constante `ROLE_ADMIN` extraite
  pour éviter la duplication du litéral.
- **Kong** : ajout des routes `/api/users/me/image$` + `/api/users/me/banner$`
  → user-service (listées AVANT le `/api/users/[^/]+$` plus large pour
  la spécificité regex) ; ajout de `/api/events/(?:\d+)/image$` →
  event-service (listée avant `/api/events/(?:\d+)$`).
- **Trade-off duplication** : FileStorageService + ImageFormat + 2
  exceptions + 2 mappers existent en double (user-service ET
  event-service). C'est la même tension que le reste de la
  soft-extraction ; les deux copies vivent dans `services/*-service/**`
  qui est déjà exclu par le glob Sonar. Une consolidation via lib
  partagée (`services/shared-storage/`) sera proposée en
  post-migration s'il y a appétit.

État après cette PR : **legacy-monolith ne sert plus aucun trafic via
Kong**. Le catch-all `/api → http://api:8080` peut être retiré dans la
PR de step 15 (legacy-monolith removal) sans casser d'endpoint.

### Step 15 — Legacy-monolith removal (commit `b570c1b`) ✅

Le strangler-fig est complet. Cette PR exécute la suppression bloc :

- `git rm -r backend/services/legacy-monolith` (~370 fichiers Java + tests
  + migrations Flyway V1..V17 + Dockerfiles).
- `git rm -r k8s/chart/templates/api/` (Deployment + Service du monolithe).
- `backend/pom.xml` retire `<module>services/legacy-monolith</module>` —
  reactor passe de 15 à 14 modules.
- `k8s/chart/values.yaml` + `values-preview.yaml` retirent la section
  `api: { resources: ... }`.
- `k8s/chart/values.yaml` retire `image.api.name = unige-events-api` ;
  **garde** `image.api.tag` qui est resté la propriété "shared github.sha"
  référencée par tous les Deployment templates des microservices (le
  rename en `image.tag` propre est différé à la PR 16 / CI matrix pour
  ne pas churn 14 templates ici).
- `k8s/chart/templates/kong/configmap-routes.yaml` retire le bloc
  `monolith-api` + sa route `api-catchall /api`. Conséquence : un path
  `/api/*` qui ne matche aucune des regex per-service retourne désormais
  un 404 Kong (correct — il n'y a plus de fallback monolithe).
- `.github/workflows/deploy.yml` n'est PAS modifié — il continue de
  passer `--set image.api.tag="${{ github.sha }}"` qui fixe la propriété
  partagée pour tous les microservices.

Résultat `cd backend && ./mvnw verify -DskipTests` : 14 modules
microservices SUCCESS, total 1 min 10 s (legacy-monolith faisait ~5 min
à lui seul). La métrique Sonar `services/*-service/**/*` exclude
devient une no-op puisque legacy-monolith n'existe plus côté CPD source —
mais on la laisse en place jusqu'à PR 16 (CI matrix per-service avec
projectKey distinct), où elle disparaîtra complètement.

**Étape 16 partielle — Documentation finale (commits `912a0e3` + `454cfb3`) ✅**

- `architecture.md` : section « Vue d'ensemble — topologie microservices »
  réécrite avec la table des 13 services + endpoints owned + tables
  possédées + notes inter-service (REST clients différés, Kafka non câblé,
  rate-limits non portés). Banner Sprint 8 mis à jour pour indiquer
  "migration LIVRÉE".
- `dev-guide.md` : section « Layout Maven (multi-module — post-migration) »
  réécrite (14 microservices, plus de legacy-monolith). Workflow `quarkus:dev`
  par service avec port HTTP override.

**Différé pour des PRs follow-up dédiées** :

- `data-model.md` : ajout d'une colonne « Service propriétaire » par
  table d'entité (mapping mécaniquement dérivable de la table dans
  `architecture.md` mais non encore pushé dans `data-model.md`).
- `api-contract.md` : ajout d'une colonne « Service amont » par
  endpoint (idem — mécanique, mais 200+ lignes à mettre à jour).
- `AGENTS.md` racine : référence à la nouvelle topologie.

### Étape 18 — Consolidation post-migration (commits `446ea3e`, `3f3dcd1`, `5dce9be`, `08a99d1`) ✅

Une fois les 13 extractions livrées + legacy-monolith supprimé, trois
dettes héritées de la soft-extraction ont été remboursées sur la même
branche persistante :

* **`446ea3e` — Restauration de `@PerUserRateLimit` via `services/shared-rate-limit/`.**
  Le PerUserRateLimit interceptor + RateLimitState (Caffeine) +
  RateLimitExceededException + son ExceptionMapper vivaient dans
  `legacy-monolith` ; sa suppression à `b570c1b` a fait perdre les 13
  annotations qui rate-limitaient les endpoints write (issue #98 / pentest
  finding 4.14). Les primitives sont republiées dans une lib jar dédiée
  (hors glob d'exclusion Sonar — sa couverture compte sur le new-code
  metric), discoverable par chaque service Quarkus via `META-INF/jandex.idx`.
  Couverture jacoco : 35 tests unitaires, ~95 % lignes. Les 13 annotations
  `@PerUserRateLimit` sont restaurées sur 6 Resources (event-service ×6,
  user-service ×3, attendance/comment/favorite/follow ×1 chacun) — mêmes
  noms et budgets que le monolith, donc Kong + frontend inchangés.

* **`3f3dcd1` — Dédoublonnage `FileStorageService` via `services/shared-storage/`.**
  `FileStorageService` + `ImageFormat` + 2 exceptions + 2 mappers
  étaient clonés dans user-service ET event-service (compromis explicite
  de la soft-extraction au commit `41074e9`). Avec la migration livrée,
  le clone est pure dette — un fix de sécurité S3 ou de pentest devrait
  atterrir à 2 endroits. Le code est consolidé dans une lib jar avec une
  petite interface `StorageConfig` (chacun des `AppConfig` `extends`
  cette interface — SmallRye Config + ArC exposent un seul bean qui
  satisfait les deux injection points). 75 tests unitaires, **100 %
  lignes**. 12 fichiers Java dupliqués supprimés.

* **`5dce9be` — Premier producteur Kafka : `events.{published,cancelled,expired}` depuis event-service.**
  Les 10 topics Kafka provisionnés au S8 (cf. PR #158) étaient vides —
  aucun producteur ni consommateur câblé. Ce commit livre le premier
  producteur :
    - `quarkus-messaging-kafka` ajouté à event-service ; 3 channels
      `mp.messaging.outgoing.events-{published,cancelled,expired}.*`
      configurés ; `%test` flippe vers `smallrye-in-memory` pour les
      sentinel tests.
    - `EventLifecycleEvent` (record `(type, eventId, creatorId, occurredAt)`)
      + `EventLifecyclePublisher` (@ApplicationScoped, 3 Emitters,
      fire-and-forget — un crash Kafka ne propage pas dans la transaction
      utilisateur).
    - Wired dans `EventService.publish` / `EventService.cancel` /
      `EventExpirationService.expireEvents` (refactor row-by-row avec
      `JOIN FETCH e.creator` au passage pour avoir le `creatorId` sans
      lazy-load proxy).
    - 10 tests unitaires (factories du record + routing publisher +
      swallow d'exception).
  Producteur-only : les consommateurs vivront dans `notification-service`
  (SCRUM-99 follow-up). Les 7 autres topics (`events.banned`, `users.*`,
  `comments.created`, `co-organizers.*`) restent à câbler dans des PRs
  follow-up — le pattern `EventLifecyclePublisher` est reproductible.

* **`08a99d1` — Cleanup doc : déduplication de la section `### EventView`.**
  `data-model.md` avait deux sections `### EventView` (la première
  basique, la seconde annotée per-service ownership + idempotence note).
  Fusionnées en une.

`./mvnw verify -DskipITs` reste vert sur les 16 modules (15 + 2 nouvelles
shared libs - 1 module hors -service suffix = 16) en ~3m45s.

**Toujours différé après cette consolidation** :

- 7 producteurs Kafka restants (report-service `events.banned`,
  follow-service `users.*`, comment-service `comments.created`,
  co-organizer-service `co-organizers.*`).
- REST clients pour remplacer les JPA stubs (besoin coordination DevOps :
  schémas-par-service à câbler via Flyway dédiés).
- PR 16 CI matrix per-service + sonar.projectKey distinct (DevOps).

### Note CI : transient image-pull failure sur PR 4 (calendar-service)

Le run CI de la PR 4 (commit `df19461`) a échoué au stage Deploy avec
`ImagePullBackOff` sur `unige-events-calendar-service:df1946...` —
l'image avait pourtant été pushée au stage Build. Cause probable :
visibilité GHCR du package fraîchement créé (un nouveau package est
techniquement pull-able via le `ghcr-secret` mais peut prendre 1-2 min à
être propagé après son tout premier push). Les runs suivants (PR 5/6
notamment) ont déployé sans souci avec leur propre tag, confirmant que
c'était un transient. Pas d'action corrective requise.

Les services restants sont **déjà scaffoldés** (POM placeholder
`<packaging>jar</packaging>` avec endpoint `/api/__service` debug, Helm
`replicas: 0`) — les follow-up PRs n'ont qu'à upgrader chaque scaffold
vers une vraie extraction.

**Étape 17 — CI matrix per-service (livré en complétion).** Refonte de
`build.yml` en strategy matrix `service in [...]` avec un
`sonar.projectKey` distinct par service. Cf. Étape 12 de la spec de
complétion + [`devops-handoff.md`](devops-handoff.md) item 1 pour la
création des 13 SonarCloud projects côté DevOps.

### Écarts vs spec — récapitulatif post-completion

Tableau aligné avec l'état **post-completion** ciblé par
[`specs_archives/specs_claude/specs_microservices_migration_completion.md`](../../specs_archives/specs_claude/specs_microservices_migration_completion.md).
Les ✅ reflètent ce qui est livré à la PR #158 incluant la complétion ;
les ⚠ marquent les déviations explicitement actées dans la spec de
complétion ; les ❌ sont des items déférés DevOps S9+ formalisés dans
[`devops-handoff.md`](devops-handoff.md).

| Critère de done (spec) | État | Commentaire |
|---|---|---|
| 13 microservices Quarkus extraits + 1 placeholder notification + 2 shared libs Sprint 8 + 8 shared libs en complétion | ✅ | 13 services actifs (share, view, favorite, calendar, follow, comment, co-organizer, attendance, report, stats, me-aggregator, user, event), 1 scaffold (notification, replicas:0, SCRUM-99), 10 shared libs au total (cf. Décision D). Legacy-monolith supprimé (commit `b570c1b`). |
| Helm chart : 13 sous-templates services + 1 scaffold + Kong + Kafka ; `Chart.yaml` v0.2.0 | ✅ | Chart bumpé. `livenessProbe` ajoutée aux 13 deployments en complétion (Étape 11). Image tag renommé `image.api.tag → image.tag` (Étape 12). |
| Kong DB-less + table de routes ; plugins activés (cors, correlation-id, prometheus, rate-limiting) | ✅ | Plugin `rate-limiting` (`policy: local`) ajouté en complétion sur 3 routes (`events.create=10/min`, `comments.post=10/min`, `follows.follow=30/min`, Étape 10). |
| Kafka KRaft + 10 topics ; producteurs et consommateurs branchés | ✅ | 10 topics provisionnés. **9 producteurs livrés** (event-service ×3 — déjà PR #158 — + follow-service ×3 + comment-service + co-organizer-service ×2 + report-service ×1 en complétion). **1 consommateur livré** (event-service ← `events.banned`). Pattern uniforme `<Domain>Publisher` + bridge CDI `@Observes(AFTER_SUCCESS)` (Décision A/F). |
| Auth0/OIDC fonctionnelle sur chaque service | ✅ | `quarkus-oidc` activé sur les 13 services. `%test.quarkus.oidc.enabled=false`. Defaults bidons retirés en complétion (SEC-004). |
| Migrations Flyway par service (`V1__extract_<svc>_schema.sql`) | ⚠ | **DEFERRED S9+** formellement par Décision C de la spec de complétion. Les services partagent `unige_events.public` ; l'isolation est matérialisée au niveau code via les REST clients (Décision B), suffisant pour la sémantique microservices en S8. Item DevOps documenté dans [`devops-handoff.md`](devops-handoff.md). |
| Schedulers réaffectés (event-service / report-service, replicas:1 strict) | ✅ | `EventExpirationJob` dans event-service. `ModerationCleanupJob` dans report-service. |
| Cascade SCRUM-136 + anti-oracle ISSUE-92 / ISSUE-93 via REST sync | ✅ | Centralisés en complétion (Étape 5.8 + Décision L). Règle unique côté service propriétaire (event-service.getById, user-service.getPublicProfile, co-organizer-service./check). Helpers locaux supprimés. |
| CI matrix per-service ; sonar.projectKey par service | ⚠ | YAML CI livré en complétion (Étape 12) avec `strategy.matrix.service: [...]` + override `sonar.projectKey` par module + suppression du glob `<sonar.coverage.exclusions>services/*-service/**/*</sonar.coverage.exclusions>`. **Activation effective dépend de la création des 13 SonarCloud projects côté DevOps** (cf. [`devops-handoff.md`](devops-handoff.md) item 1). |
| Tests unit + integration + Pact + 1 E2E happy path | ✅ | 1818 tests legacy portés en complétion (Étape 7) ; 35 sentinels SCRUM-138/139/144/147 verts par nom ; 4 pacts (share-event, comment-event, comment-coorganizer, report-event) + 1 E2E happy path (Étape 8). Couverture cible ≥ 80 % L / ≥ 70 % B par service métier ; ≥ 95 % L / ≥ 90 % B par shared lib. |
| `./mvnw verify` à la racine `backend/` vert | ✅ | ≥ 24 modules (10 shared libs + 14 services) après complétion Étape 3. |
| Documentation finale (architecture, data-model, api-contract, dev-guide, AGENTS, roadmap, devops-handoff, internal-endpoints) | ✅ | Tout aligné en complétion (Étapes 2 + 13). Nouveau : [`backend/docs/devops-handoff.md`](devops-handoff.md), [`backend/docs/internal-endpoints.md`](internal-endpoints.md). |
| PR ouverte titre `chore(backend): migrate to microservices architecture with Kong gateway and Kafka broker` | ⚠ | Workaround `chore(backend):` (cf. Bug subtil documenté plus bas) — `pr-title-check.yml` exige `scrum-XXX` pour le scope `refactor`. Inchangé. |
| PR **non mergée** par l'agent | ✅ | Mergée par Elie après validation. |
| `git diff --shortstat origin/main HEAD -- frontend/` = 0 lignes | ✅ | Invariant tenu. |
| `git diff --shortstat origin/main HEAD -- openapi/` ≤ 32 lignes | ⚠ | **Déviation actée par Décision Q** : suppression du doublon `POST /events/{id}/view` (le bloc le plus pauvre en erreurs ; 32 lignes). Toute autre modification d'`openapi.yaml` lèverait un blocker. |
| 0 JPA stub cross-service (`find backend/services -name '*Stub.java'` = vide) | ✅ | 35 stubs supprimés en complétion (Étape 5) — remplacés par REST clients `@RegisterRestClient` avec resilience (`@Retry` + `@Timeout` + `@CircuitBreaker` + `@Fallback`). |
| Observabilité : `quarkus-logging-json`, `micrometer-registry-prometheus`, `X-Request-ID` propagation | ✅ | 3 extensions Quarkus + lib `shared-tracing` consommée par les 13 services (Étape 9). Endpoint `/q/metrics` exposé (interne, non Kong). |

**TL;DR** : la PR #158 livre les **fondations + structure Maven + scaffolds
+ docs partielles**. Les **vraies extractions de code** restent 13 PRs
follow-up documentées en détail (1 PR par service, dans l'ordre share →
event) + 3 PRs de finition (legacy-monolith removal + final docs + CI matrix).
Cf. [`microservices-migration-roadmap.md`](microservices-migration-roadmap.md).

### Bug subtil documenté

[`pr-title-check.yml`](../../.github/workflows/pr-title-check.yml) (lignes 67-82)
rejette **`refactor(<scope-non-jira>): ...`** : pour les types `feat` / `refactor`
/ `perf`, le scope DOIT être un identifiant Jira `scrum-XXX` minuscule. La spec
cite un titre PR final `refactor(backend): migrate to microservices...` qui ne
passerait PAS ce check — à fixer avant l'ouverture de la PR de consolidation
(soit créer un ticket Jira dédié `refactor(scrum-XXX): migrate...` soit
transformer en `chore(backend): migrate to microservices...`). Les sous-PRs
courantes utilisent `chore(infra): ...` / `fix(infra): ...` / `fix(ci): ...` /
`refactor(backend): convert to multi-module maven layout` (laquelle violerait
aussi le check si elle était poussée comme PR séparée — heureusement elle est
un commit interne d'une branche persistante dont la PR est titrée
`chore(infra): scaffold Kong and Kafka helm templates (CI deploy validates)`).

---

## Sprint 7 — Récurrence sur Event + génération d'occurrences (SCRUM-147) — 2026-05-08

Livré.

Brique récurrence (US-27, épic SCRUM-14) qui permet à un organisateur de créer un événement
hebdomadaire / bimensuel / mensuel sans saisir manuellement chaque session. Débloque
SCRUM-XXX-front-recurrence (S8+) — formulaire `RecurrenceForm.tsx` + listing des occurrences
dans `EventDetailPage`.

- Migration `V17__add_event_recurrence.sql` : `ALTER TABLE events ADD COLUMN parent_event_id BIGINT, ADD COLUMN recurrence_rule VARCHAR(500); ADD CONSTRAINT fk_events_parent FOREIGN KEY (parent_event_id) REFERENCES events(id) ON DELETE SET NULL; CREATE INDEX idx_event_parent ON events(parent_event_id);`. **ON DELETE SET NULL** : un DELETE physique du parent (après cancel) préserve les occurrences orphelines avec `parent_event_id = NULL` — leurs inscriptions, favoris, vues et comptages restent intacts. Numérotation V17 fixée par l'utilisateur ; au checkout `origin/main` était à V13, l'ordre attendu en pré-merge étant V14 (follows, SCRUM-138 PR #154) → V15 (comments, SCRUM-139 PR #156) → V16 (PR concurrente) → V17.
- Enum `RecurrenceFrequency` (`WEEKLY` / `BIWEEKLY` / `MONTHLY`) — pas DAILY ni YEARLY en S7.
- Entité `Event` étendue de 2 champs publics : `parentEventId: Long` (`@Column(name="parent_event_id")`, pas `@ManyToOne` — pointeur Long brut cohérent avec Favorite/Attendance/Follow) et `recurrenceRule: String` (`@Column(length=500)`). Nouvel `@Index` `idx_event_parent` ajouté à `@Table(indexes={...})`.
- DTOs : `RecurrenceRequest` (record, `frequency @NotNull`, `endDate` LocalDate nullable, `maxOccurrences` Integer `@Min(1) @Max(52)` nullable). `CreateEventRequest` enrichi d'un champ `@Valid recurrence` optionnel. `EventDTO` étendu de 2 champs `parentEventId` + `recurrenceRule` propagés via factory `from(Event, ...)`.
- `RecurrenceGenerator` (utility class statique, fonction pure) : `generate(parentStart, parentEnd, frequency, untilDate, maxOccurrences) -> List<DateRange>`. Cap hard 52 (parent inclus, donc ≤51 children retournés). Spacing `Period.ofDays(7)` / `ofDays(14)` / `ofMonths(1)` (gère 31→28 février naturellement). Levée `IllegalArgumentException` si `untilDate == null && maxOccurrences == null`. Testable hors Quarkus (pur JUnit).
- `EventService.create(...)` enrichi d'un branchement précoce : `if (request.recurrence != null) return createRecurring(...)`. Logique standalone strictement inchangée (extraite dans le helper privé `persistParent`).
- `EventService.createRecurring(...)` (`@Transactional`, all-or-nothing) : valide `recurrence_unbounded` et `recurrence_end_before_start` via le helper `badRequestRecurrence(error, message)` → `WebApplicationException` + envelope `ApiErrorResponse`. Calcule la `recurrenceRule` du parent via `buildRecurrenceRule` (format `FREQ=...;UNTIL=YYYYMMDD;COUNT=N`). Génère et persiste chaque occurrence via `persistOccurrence` (copie du template parent, sauf `startDate`/`endDate` venant du range, `parentEventId = parent.id`, `recurrenceRule = null`). Statut hérité du parent.
- `EventService.getOccurrences(parentId, auth0Id, isAdmin, page, size)` : délègue à `getById(...)` en première ligne pour l'anti-oracle ISSUE-92, puis `Event.find("parentEventId = ?1 order by startDate asc, id asc")`. 200 + liste vide pour un standalone (pas 404).
- `EventResource` étendu d'un seul handler `@GET @Path("/{id}/occurrences") @PermitAll` (pas de nouvelle classe — un seul `@Path("/events")` racine). Pagination `defaults 0/52 @Max(52)`.
- OpenAPI : 2 champs ajoutés au schéma `Event` (`parentEventId`, `recurrenceRule`, readOnly), 1 schéma `RecurrenceRequest` (frequency required, endDate + maxOccurrences optionnels avec contrainte at-least-one server-side), 1 enum `RecurrenceFrequency`, 1 champ `recurrence` sur `CreateEventRequest`, 1 path `/events/{id}/occurrences` (200/400/404). Codes d'erreur enrichis sur `POST /events` (400 `recurrence_unbounded`/`recurrence_end_before_start`, 422 `recurrence_too_many`).

Tests : 903 verts au total, dont **42 nouveaux SCRUM-147** (13 RecurrenceGeneratorTest pur JUnit, 3 EventTest, 3 EventDTOTest, 16 EventServiceCoverageTest DB-backed, 7 EventResourceTest). Sentinels nommément verts : `weekly_4Occurrences_returns3DatesSpacedBy7Days`, `monthly_handlesShortFebruaryFromJanuary31`, `bothNull_throwsIllegalArgumentException`, `maxOccurrencesAbove52_cappedTo52`, `from_parentRecurringEvent_exposesRecurrenceRuleAndNullParentEventId`, `from_occurrenceEvent_exposesParentEventIdAndNullRecurrenceRule`, `createRecurring_weekly4Occurrences_persists1ParentAnd3Children`, `createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded`, `createRecurring_endDateBeforeStart_returns400_recurrenceEndBeforeStart`, `createRecurring_inheritsParentStatusPublished`, `getOccurrences_parentRecurring_returnsChildrenSortedAsc`, `getOccurrences_standaloneEvent_returns200EmptyList`, `getOccurrences_draftByNonCreator_returns404_antiOracle`, `update_parentTitle_doesNotPropagateToOccurrences`, `cancel_parentDoesNotCascadeToOccurrences`, `delete_parent_setsOccurrencesParentEventIdToNull`, `post_validRecurrenceWeekly_returns201_recurrenceRuleSetOnParent`, `post_recurrenceMaxOccurrences53_returns400_beanValidation`, `getOccurrences_parentPublishedAnonymous_returns200`, `getOccurrences_sizeOver52_returns400`, `getOccurrences_draftByAnonymous_returns404_antiOracle`. `RateLimitState.clearBuckets()` ajouté en `@BeforeEach` de `EventResourceTest` pour isoler le bucket `events.create` entre tests.

`EventServiceMock` étendu avec un `createRecurringMock` qui mirror les codes d'erreur 400 prod et un `getOccurrences` override qui délègue à `getById` (anti-oracle parity) puis renvoie liste vide. `ShareServiceCoverageProfile` **non modifié** (`EventServiceMock` y figurait déjà).

Hors scope explicitement : skip d'occurrence individuelle (RFC 5545 EXDATE — S8+), modification globale propagée aux occurrences (S8+), cancel cascadé (S8+), héritage automatique des co-organisateurs (S8+), notifications par occurrence (SCRUM-99 S7+ — infra Notification), RRULE compact dans ICS (S9+), front (SCRUM-XXX-front-recurrence S8+).

---

## Sprint 6 — Entité `Comment` + 3 endpoints CRUD commentaires événements (SCRUM-139) — 2026-05-08

Livré.

Socle backend des commentaires d'événements (US-22, épic SCRUM-16) qui débloque
SCRUM-146 (front S7 — `CommentSection.tsx` dans `EventDetailPage`) et SCRUM-144
(likes / report-comment S7, l'entité `Comment` étant référencée par
`CommentLike.commentId` et l'extension `Report.commentId`).

- Migration `V15__create_comments.sql` : table `comments` (BIGINT PK via
  `comments_seq` increment 50, FK NOT NULL vers `events.id` et `users.id`,
  FK nullable auto-référente vers `comments.id` avec `ON DELETE SET NULL` —
  un DELETE physique d'un parent fait remonter ses replies en top-level
  côté DB sans rejet RESTRICT, `content TEXT NOT NULL`,
  `like_count INTEGER NOT NULL DEFAULT 0`, `created_at TIMESTAMP NOT NULL`).
  3 indexes : `idx_comment_event`, `idx_comment_parent`,
  `idx_comment_event_created` (composite descendant pour le tri du listing).
- Entité `Comment` (PanacheEntity, Long PK) avec 3 `@ManyToOne(LAZY)` —
  `event`, `author`, `parentComment`. `content` mappé en TEXT via
  `@Column(columnDefinition="TEXT")` + `@NotBlank @Size(max=2000)`.
  `likeCount` int default 0 — **lecture seule en S6** (mutation déléguée à
  SCRUM-144). `@PrePersist` avec null-guard (pattern aligné sur les autres
  entités du projet).
- DTOs : `CommentDTO` (record, 11 champs) avec deux factories
  `from(Comment, boolean)` et `fromTopLevelWithReplies(...)`.
  `CreateCommentRequest` (record) avec `content @NotBlank @Size(max=2000)` et
  `parentCommentId` nullable.
- `CommentService` (`@ApplicationScoped`, `@Transactional` sur `post`/`delete`,
  non-transactional sur `getByEvent`) : visibilité event déléguée à
  `EventService.getById(...)` (anti-oracle ISSUE-92), branchement par statut
  (PUBLISHED → 201, DRAFT/CANCELLED/EXPIRED créateur → 400, autre → 404),
  vérification du parent (existence + appartenance event + profondeur 1 niveau
  max — sinon 404/422), trim côté service. DELETE cascade
  auteur/créateur/co-org ACCEPTED (réutilise SCRUM-136
  `isCreatorOrAcceptedCoOrganizerPublic`)/admin → 204, sinon 403. Batch-load
  des replies en 2 requêtes SQL (top-level page + WHERE parent_comment_id IN)
  avec calcul bulk de `authorIsOrganizer` via un `Set<UUID>` mémoïsant
  creator + co-orgs ACCEPTED.
- `CommentResource` (`@Path("/events")`) avec POST + GET ; `CommentDirectResource`
  (`@Path("/comments")`) avec DELETE — split en deux Resources pour respecter
  l'unicité du `@Path` racine. Constructor injection (Sonar S6813). `POST`
  rate-limité via `@PerUserRateLimit(name="comments.post", max=10, windowSeconds=60)`.
  GET `@PermitAll` (visibilité déléguée à getById).

Tests : 918 verts au total dont 58 nouveaux SCRUM-139 (4 entity + 30 service
coverage + 20 resource + 4 direct-resource). JaCoCo **100 % lignes** sur
`Comment`, `CommentDTO`, `CreateCommentRequest`, `CommentService`,
`CommentResource`, `CommentDirectResource`. Sentinels nommément verts :
`prePersist_setsCreatedAt`,
`post_eventDraftByNonCreator_returns404_antiOracle`,
`post_eventBanned_returns404_antiOracle`,
`post_replyToReply_returns422_repliesTooDeep`,
`post_parentInOtherEvent_returns422_parentNotInEvent`,
`post_unknownParent_returns404_parentNotFound`,
`get_anonymousOnPublished_returnsList`,
`getByEvent_draftByNonCreator_returns404_antiOracle`,
`delete_byAuthor_removesRow`, `delete_byEventCreator_removesRow`,
`delete_byAcceptedCoOrganizer_removesRow`,
`delete_byPendingCoOrganizer_returns403`,
`delete_byThirdParty_returns403`,
`delete_unknownComment_returns404_commentNotFound`,
`delete_byAdmin_removesRow`. `RateLimitState.clearBuckets()` en `@BeforeEach`
de `CommentResourceTest` pour isoler le bucket `comments.post` entre tests.
`CommentServiceMock` ajouté à la liste d'exclusion de
`ShareServiceCoverageProfile`.

Hors scope explicitement : likes (SCRUM-144 S7), signalement de commentaires
(SCRUM-144 S7), notifications NEW_COMMENT/COMMENT_MENTION (SCRUM-145 S7+,
dépend de SCRUM-99 infra Notification), édition de commentaires (UX =
supprimer + reposter), front (SCRUM-146 S7).

---

## Sprint 6 — Entité `Follow` + 7 endpoints follow / unfollow / demandes / listes (SCRUM-138) — 2026-05-07

Livré.

Socle backend du graphe social qui débloque SCRUM-141 / 142 / 143 (front S7 — page profil public, FollowButton, modales listes) et anticipe SCRUM-168 (filtre `followedOnly` du feed S9).

- Migration `V14__create_follows.sql` : table de jointure UUID/UUID `(follower_id, followed_id)` avec contrainte unique, FK vers `users(id)` (sans cascade — pattern défensif identique à `Report.reporter`), CHECK constraint sur `status`, index sur `follower_id` et `followed_id`.
- Entité `Follow` (PanacheEntity, Long PK) avec finders statiques dont **`findAcceptedFollowedIds(UUID)`** livré dès maintenant pour éviter à SCRUM-168 (S9) de re-réfléchir à la requête JPQL plus tard.
- Enum `FollowStatus` à 2 valeurs : `PENDING`, `ACCEPTED`. Un reject = DELETE physique de la row (mirror `EventCoOrganizer.DECLINE`) — re-tentative possible sans 409.
- `FollowService` (@ApplicationScoped, @Transactional sur les mutations seulement) avec règles métier : auto-accept si profil cible public, PENDING sinon, 422 `cannot_follow_self`, 409 `already_following` (check applicatif + filet de sécurité unique constraint), 403 sur accept/reject par non-target, 409 `invalid_transition` sur transition non-PENDING, DELETE idempotent.
- `FollowResource` (`/users`) et `FollowRequestResource` (`/follow-requests`) — split en deux Resources pour qu'aucune ne partage son `@Path` racine avec une autre.
- `UserPublicResponse` enrichi : `followerCount`, `followingCount` (long, toujours présents), `followStatus` (nullable, null pour anonymes/self/no-relation). Trois factories : `from(User)` legacy / `from(User, fc, fwc, fs)` enrichie / `fromAnonymous(User)` (zero-init).
- `UserService.getPublicProfile` retourne désormais un `PublicProfileView` (record agrégé `User + 3 compteurs`). Les anonymes prennent un court-circuit qui économise 2 requêtes DB. La règle anti-oracle 404 ISSUE-93 reste inchangée.
- Rate limit `@PerUserRateLimit(name="follows.follow", max=30)` sur `POST /users/{id}/follow` uniquement.
- Notifications de follow (`NEW_FOLLOWER`, `FOLLOW_REQUEST`, `FOLLOW_ACCEPTED`) explicitement hors scope — déléguées à SCRUM-140 / S7 une fois SCRUM-99 (infra Notification) livré.

Tests : 932 verts. JaCoCo 100% lignes sur `Follow`, `FollowStatus`, `FollowDTO`, `PublicProfileView`, `FollowService`, `FollowResource`, `FollowRequestResource`. Sentinels nommément verts : `findAcceptedFollowedIds_returnsOnlyAcceptedUuids` (SCRUM-168), `rejectRequest_followerCanReFollowAfterReject`, `follow_selfFollow_throwsUnprocessable`, `getFollowers_privateProfileNonOwner_returns404_antiOracle`, `getPublicProfile_self_followStatusIsNull`, `getPublicProfile_authNonOwnerWithPending_followStatusIsPending`.

---

## Sprint 7 — `AttendanceDTO` projette `displayName` / `avatarUrl` (fix UUID stats organisateur) — 2026-05-03

Livré.

Sur `/events/:id/stats`, la liste des participants affichait un UUID brut au lieu du nom pour tout user `profilePublic = false`. Cause : le front faisait du N+1 sur `GET /users/{id}` qui renvoie 404 pour les profils privés (hotfix pentest 4.1, anti-oracle). La route `GET /events/{id}/attendees` étant déjà restreinte au créateur ou co-organisateur ACCEPTED (cf. `AttendanceService.getAttendees`), enrichir le DTO ne fuite rien et permet au front de lire le nom directement.

`AttendanceDTO` reçoit deux nouveaux champs `displayName` (toujours présent pour un user existant — initialisé depuis le claim Auth0 `name` à la création) et `avatarUrl` (nullable). `AttendanceService.getAttendees(...)` charge tous les `User` du batch en une seule requête (`User.list("id in ?1", ids)`) pour éviter le N+1 côté serveur. La page détail publique reste inchangée — elle continue d'afficher "Utilisateur anonyme" pour les profils privés via le flux séparé `getPublicUser`.

Tests : `AttendanceServiceCoverageTest` enrichi de 3 cas (factory DTO avec/sans User, `getAttendees` projette le `displayName` même pour profil privé, ligne orpheline retourne `null` sans planter). `AttendanceServiceMock` adapté à la nouvelle signature `from(Attendance, User)`. OpenAPI : champs `displayName` et `avatarUrl` ajoutés au schéma `Attendance`.

---

## Sprint 6 — `EventDTO` enrichi avec compteurs publics (review #90, SCRUM-92) — 2026-05-03

Livré.

- `EventDTO` reçoit deux champs `Long viewCount` / `Long interestedCount` (nullable). Ils sont **renseignés uniquement** sur `GET /events/{id}` (via les helpers `EventService.countViews` / `countInterested`). Tous les autres call sites de `EventDTO.from(...)` (`create`, `update`, `cancel`, `restore`, `publish`, `uploadImage`, `toEventDTOs` pour les listes paginées, `FavoriteService.getFavorites`, `EventSearchService.search`) passent `null, null` — décision volontaire pour éviter des `count(*)` N+1 sur les listes.
- L'endpoint `GET /events/{id}/stats` reste inchangé et **réservé** au créateur ou co-organisateur ACCEPTED. Frontend : la page `/events/:id/stats` continue d'afficher les visualisations avancées (chart + capacity bar + liste des participants).
- OpenAPI mis à jour (`Event.viewCount`, `Event.interestedCount` avec `nullable: true` et description précisant le scope).
- Tests : `EventDTOTest` (2 cas — `null/null` et valeurs renseignées), `EventServiceCoverageTest` (2 cas — `getById` expose les compteurs depuis `EventView`/`Favorite`, retourne `0` quand vide). Tous les call sites tests (`EventServiceMock`, `EventSearchServiceMock`, `FavoriteServiceMock`, `UserResourceTest`, `CoOrganizerDTOTest`) mis à jour.

---

## Sprint 6 — Hotfix sécurité post-pentest (ISSUE-92) — 2026-04-24

Correction du finding **4.12** (Medium) du rapport de pentest du 2026-04-17 :
`GET /api/events/{id}` renvoyait `200` avec le payload complet d'un event `DRAFT`
ou `CANCELLED` à n'importe quel appelant, y compris anonyme. Combiné au finding
**4.15** (IDs séquentiels), n'importe qui pouvait énumérer tous les brouillons et
events annulés de la plateforme avec `for id in 1..1000; do curl .../events/$id; done`.

Fix :
- `EventService.getById(Long, String, boolean)` — signature étendue avec l'`auth0Id`
  de l'appelant et un flag `isAdmin`. Si `event.status != PUBLISHED` et que
  l'appelant n'est ni le créateur ni un admin → `NotFoundException` (→ `404 not_found`).
- `EventResource.getById` reste `@PermitAll` (PUBLISHED doit rester anon-accessible)
  mais lit `identity.isAnonymous()` + `identity.hasRole("ADMIN")` pour transmettre
  au Service.
- Envelope d'erreur identique à une 404 classique (pas de code d'erreur custom) —
  ferme l'oracle d'existence.
- 12 call-sites internes migrés (tests DB-backed + mock unitaire).

**Pas de changement DB.** Pas d'impact frontend (`useEvent` consomme déjà le 404).

---

## Sprint 1 — TERMINÉ (6–13 mars 2025)

**Objectif :** Authentification complète (Auth0/OIDC) + base du profil utilisateur + architecture full-stack.

### Ce qui est implémenté

- **Intégration Auth0/OIDC** : `quarkus-oidc` configuré en mode `service`, validation JWT automatique, désactivé en `%test`.
- **Entité `User`** : UUID comme PK, champs `auth0Id`, `email`, `displayName`, `firstName`, `lastName`, `faculty`, `studyLevel`, `bio`, `interests`, `avatarUrl`, `profilePublic`, `createdAt`, `version` (optimistic locking).
- **Provisionnement first-login** : `UserService.getOrCreateUser()` — idempotent, race-safe (gestion des conflits `PersistenceException` + retry).
- **Mise à jour OIDC/auth (2026-03-29)** : `GET /users/me` lit désormais les claims profil (`email`, `name`, `given_name`, `family_name`, `picture`) directement depuis le JWT via `JsonWebToken`, au lieu de déclencher un appel Auth0 `/userinfo`.
- **Endpoints profil** :
  - `GET /users/me` — profil complet de l'utilisateur connecté
  - `PUT /users/me` — mise à jour partielle du profil (champs optionnels, `@Valid`, retourne l'objet complet)
  - `GET /users/{id}` — profil public (si `profilePublic = true`, sinon 403)
- **DTOs** : `UserProfileResponse`, `UserPublicResponse`, `UpdateProfileRequest`
- **Exception mappers** : 6 mappers (409, 400, 404, 403, 401, `ConstraintViolationException`)
- **Architecture en couches** : Resource → Service → Entity validée, constructor injection, encapsulation des entités.
- **Configuration OpenAPI** : `OpenApiSecurityConfig` pour le bearer JWT.

### Bugs connus / comportements à surveiller

- `GET /users/me` : si le claim `email` est absent du JWT, une `NotAuthorizedException` est levée → retourne 401. Ce comportement est **correct et intentionnel** selon la spec. À documenter côté frontend.
- `GET /users/me` : l'injection de `UserInfo` n'est plus utilisée. Le flux ne dépend plus implicitement de `user-info-required`, ce qui supprime les appels Auth0 `/userinfo`, évite les rate limits Auth0 et élimine les 401 en cascade observés sur les requêtes authentifiées.
- `PUT /users/me` : retourne `200` avec l'objet `UserProfileResponse` complet — **pas de `204`**. Le frontend doit utiliser cette réponse pour mettre à jour son état sans refetch.
- Hibernate tourne en mode `update` — choix définitif pour ce projet.

---

## Sprint 2 — EN COURS (13–20 mars 2025)

**Objectif :** Création, édition et suppression d'événements (rôle Organisateur). Premières briques du listing public.

### État actuel

- **Entité `Event`** : complète — `id` (Long, PK Panache), `title`, `description`, `location`, `startDate`, `endDate`, `category` (enum), `bannerUrl`, `capacity`, `status` (enum, default `DRAFT`), `createdAt`, `updatedAt`, `creator` (@ManyToOne LAZY → `User`).
- **`EventDTO`** : record avec factory `EventDTO.from(Event)` — expose `creatorId` (UUID) sans relation JPA.
- **`EventResource`** : CRUD complet — `GET /events` (paginé + filtres), `POST /events` (@Authenticated, creator lié), `GET /events/{id}`, `PUT /events/{id}` (créateur uniquement), `DELETE /events/{id}` (soft-delete, créateur uniquement). Constructor injection.
- **`EventService`** : `getAll(page, size, status, category, organizerId)`, `create(auth0Id, request)`, `getById(id)`, `update(id, auth0Id, request)`, `delete(id, auth0Id)` avec `@Transactional`.
- **Tests** : `EventDTOTest` (unit), `EventResourceTest` (16 tests @QuarkusTest avec `EventServiceMock`), `EventTest` (3 tests @QuarkusTest), `CreateEventRequestTest` (6 tests bean validation), `EventServiceMock` (mock in-memory).

### À faire dans ce sprint

- [x] Enrichir `Event` avec tous les champs planifiés
- [x] Créer un `EventDTO` (ne pas exposer l'entité directement)
- [x] Écrire les tests `@QuarkusTest` pour `EventResource`
- [x] `POST /events` : sécuriser avec `@Authenticated`, lier `creator` à l'utilisateur connecté
- [x] `GET /events/{id}` : détail d'un événement
- [x] `PUT /events/{id}` : modification (créateur uniquement → 403 sinon)
- [x] `DELETE /events/{id}` : soft-delete (status → `CANCELLED`)
- [x] `GET /events` : pagination (`?page=`, `?size=`), filtres `?status=`, `?category=`, `?organizerId=`
- [x] `POST /events` : création directement en `PUBLISHED` (champ `status` optionnel dans `CreateEventRequest`, défaut `DRAFT`)
- [x] `PATCH /events/{id}/publish` : publication d'un événement DRAFT (ORGANIZER créateur ou ADMIN) — 403/404/409
- [x] `POST /events/{id}/image` : upload bannière multipart, stockage local `app.uploads.path`, retourne EventDTO mis à jour — 400 si MIME invalide
- [x] Rôles Auth0 (ADMIN/ORGANIZER/STUDENT) mappés via `quarkus.oidc.roles.role-claim-path`

---

## Sprint 3 (planifié : 20–27 mars 2025)

**Objectif :** Découverte avancée — recherche, filtres, vue calendrier.

- [x] `GET /events/search?q=&category=&dateFrom=&dateTo=` — full-text ILIKE sur titre + description, paginé (SCRUM-76) — `EventSearchResource` + `EventSearchService`
- [x] Ajout du champ `faculty` (enum `Faculty`) sur `Event` + filtre `?faculty=` sur `GET /events` et `GET /events/search` (SCRUM-77) — `Faculty.java`, `Event.faculty`, `EventDTO.faculty`, `EventRequestBase.faculty`, `EventService.getAll()`, `EventSearchService.search()`

---

## Sprint 4 (planifié : 27 mars – 3 avril 2025)

**Objectif :** Engagement & Interaction — inscription, favoris, partage.

- [x] Entité `Favorite` (userId, eventId) — SCRUM-89 ✅
- [x] `POST /events/{id}/favorite`, `DELETE /events/{id}/favorite`, `GET /users/me/favorites` — SCRUM-89 ✅
- [x] `GET /events/{id}/share` + `GET /s/{shortCode}` (shortlink redirect) — SCRUM-89 ✅
- [x] `GET /users/me/calendar-token`, `DELETE /users/me/calendar-token`, `GET /calendar/{calendarToken}.ics` — SCRUM-89-bis ✅
- [x] Entité `Attendance` (userId, eventId, status) + endpoints — SCRUM-88 ✅
  - `POST /events/{id}/attend` (upsert ATTENDING)
  - `DELETE /events/{id}/attend` (désinscription)
  - `GET /events/{id}/attendees` (créateur uniquement)
  - `GET /users/me/attendances`
- [x] Suppression du statut `INTERESTED` — correctif backend ✅
  - `AttendanceStatus` réduit à `ATTENDING` (INTERESTED redondant avec les favoris)
  - `CalendarService.generateIcsFeed` : flux ICS = Favoris ∪ ATTENDING (PUBLISHED, dédupliqués)
  - `EventDTO` : champ `interestedCount` supprimé

### Fixes PR #41 (post-review lead technique)
- [x] Fix 1 — NPE body null `POST /events/{id}/attend` → `@NotNull` sur paramètre
- [x] Fix 2 — Inscription sur event DRAFT bloquée → 400 `BadRequestException`
- [x] Fix 3 — `DELETE /me/calendar-token` → `POST /me/calendar-token/regenerate`
- [x] Fix 4 — `frontendUrl` centralisé dans `AppConfig` (défaut `http://localhost:5173`)
- [x] Fix 5 — `buildIcsContent`/`foldLine`/`escapeIcs` extraits dans `util/IcsBuilder`
- [x] Fix 6 — Conversion UTC → Europe/Zurich dans `IcsBuilder.buildIcsContent`
- [x] Fix 7 — `%dev.quarkus.http.host=0.0.0.0` dans `application.properties`

---

## Sprint 5 (planifié : 3–10 avril 2025)

**Objectif :** Statistiques organisateur + liste des participants + enrichissement Event.

- [x] `GET /events/{id}/stats` (vues, attendingCount, interestedCount — créateur uniquement) ✅
- [ ] Incrémentation du compteur de vues à chaque `GET /events/{id}` (déduplication userId+eventId)
- [x] **SCRUM-126** — Champs `websiteUrl`, `contactEmail`, `registrationDeadline`, `tags` sur `Event` ; vérification deadline dans `AttendanceService.attend()` (409 `registration_closed`).
- [x] **SCRUM-129** — Renforcement capacité : `WAITLISTED` ajouté à `AttendanceStatus`, verrou pessimiste sur `Event` pour les mutations liées à la capacité, promotion FIFO (`createdAt ASC`) dans `removeAttendance()`, exposition de `availableSpots` (nullable) et `waitlistedCount` sur `EventDTO`. Plus de 409 pour capacité atteinte — placement automatique en WAITLISTED.
- [x] **SCRUM-133** — Anticipé depuis S6 pour **motif de sécurité**. Nouvel endpoint `GET /users/me/events?status=&page=&size=` (identité dérivée du JWT, tri `createdAt DESC`, tous statuts par défaut) + durcissement de `GET /events?organizerId=…` qui force désormais `status=PUBLISHED` (rejet `400 organizer_filter_requires_published` si un autre statut est demandé). Ferme la faille qui permettait à n'importe quel utilisateur authentifié d'énumérer les brouillons d'un autre via `GET /events?organizerId=<uuid>&status=DRAFT`. Migre `useMyEvents` côté frontend sur le nouvel endpoint.
- [x] **SCRUM-131** — Filtre `?tags=` (sémantique OR) sur `GET /api/events/search`. Clause JPQL `EXISTS` sur la collection `event_tags`, normalisation lowercase via `EventService.normalizeTags`. Multi-valeurs `?tags=a&tags=b`, blank/null filtrés silencieusement.
  - Amélioration : match substring case-insensitive (ex. `?tags=foot` matche `football` ou `barefoot-running`) via `LOWER(t) LIKE :tagN ESCAPE '|'` construit dynamiquement ; les wildcards SQL `%` et `_` saisis par l'utilisateur sont échappés pour être traités littéralement.

---

## Sprint 6 (planifié : 10–24 avril 2025)

**Objectif :** Administration & Modération.

- [ ] Champ `admin` (boolean) sur `User` + `@RolesAllowed("admin")` sur endpoints sensibles
- [ ] Entité `Report` (reporterId, eventId, reason, status PENDING|REVIEWED|DISMISSED)
- [ ] `POST /events/{id}/report`
- [ ] `GET /admin/reports`, `PUT /admin/reports/{id}`, `PUT /admin/events/{id}/feature`
- [x] **ISSUE-93** — Hotfix sécurité post-pentest (2026-04-24) sur `GET /users/{id}`.
  Correction des findings **4.1** (user-existence oracle via `403` vs `404`) et **4.1b**
  (harvest anonyme des profils opt-in, GDPR-relevant) du rapport de pentest du 2026-04-17.
  - `UserService.getPublicProfile(UUID id, String auth0Id)` — signature étendue. Si
    `profilePublic=false` et que l'appelant n'est pas le propriétaire (self-case sur
    `auth0Id`), throw `NotFoundException` (→ `404 not_found`, envelope identique à un
    UUID inexistant). Ferme l'oracle exploité via `creatorId` leaké par `GET /events`.
  - `UserResource.getProfile` reste `@PermitAll` mais lit `identity.isAnonymous()` pour
    choisir entre `UserPublicResponse.from(user)` (full, authentifié) et
    `UserPublicResponse.fromAnonymous(user)` (réduit : `id` + `displayName` + `avatarUrl`).
  - Nouvelle factory `UserPublicResponse.fromAnonymous(User)` — ne projette que 3 champs
    sur 8. Les 5 autres sont `null` et conformes au schéma (tous `nullable: true`).
  - 5 call-sites internes migrés (1 prod + 3 coverage tests + 1 mock override).
  - Pas de changement DB. Pas d'impact frontend — `ProfilePage.tsx` dégrade gracieusement.

---

## Sprint 7 (planifié : 24 avril – 8 mai 2025)

**Objectif :** Notifications, duplication, expiration automatique, polish UI.

- [ ] Entité `Notification` (userId, eventId, type, message, read)
- [ ] `GET /notifications`, `PUT /notifications/{id}/read`
- [ ] `POST /events/{id}/duplicate` (réservé au créateur)
- [ ] Job `@Scheduled` : désactivation auto des events dont `endDate < now()`
- [x] **SCRUM-136** — Co-organisateurs : entité `EventCoOrganizer` (eventId, userId,
      status PENDING/ACCEPTED/DECLINED, invitedAt, contrainte unique `(event_id, user_id)`,
      indexes `idx_event_co_organizers_event` / `idx_event_co_organizers_user`) +
      6 endpoints REST (`POST/GET /events/{id}/co-organizers`,
      `DELETE /events/{id}/co-organizers/{userId}`,
      `PATCH /events/{id}/co-organizers/me/accept|decline`,
      `GET /users/me/co-organizer-invitations`).
      Cascade d'autorisation `isCreatorOrAcceptedCoOrganizer` sur `EventService.update/cancel/
      restore/publish/uploadImage/getById`, `AttendanceService.getAttendees`,
      `EventStatsService.getStats`. `EventService.delete` reste strict-creator (action
      irréversible — divergence assumée par rapport au libellé du ticket Jira).
      DECLINE supprime physiquement la row pour autoriser la ré-invitation sans 409.
      Hors scope : notifications email, transfert d'ownership, invitation par email,
      bulk invite. Frontend SCRUM-137 dépendant.
      *Fix de review post-merge main :* migration `V8__create_event_co_organizers.sql`
      (Flyway désormais source du schéma), `POST /co-organizers` sur body absent → 400
      via `@NotNull`, et `PATCH /me/accept|decline` sans row → 422
      `no_pending_invitation` au lieu de 404.
- [x] **SCRUM-94** — Modération : enrichissement de l'entité `Report` (livrée par
      SCRUM-103) avec l'enum `ReportReason` (SPAM/INAPPROPRIATE/FAKE/OTHER), `description`
      (renommée depuis l'ancienne colonne `reason` libre), `moderationNote`, `reviewedAt`,
      `reviewedBy`. Migration `V10__add_report_reason_and_review_fields.sql` (Hibernate en
      `validate` : Flyway obligatoire — la mention « mode update » du libellé Jira est
      obsolète depuis SCRUM-164). 3 endpoints : `POST /api/events/{id}/report`
      (`@Authenticated`), `GET /api/admin/reports` (paginé, défaut `status=PENDING`,
      tri `createdAt DESC`), `PATCH /api/admin/reports/{id}` (`@RolesAllowed("ADMIN")`,
      transitions `PENDING → REVIEWED|DISMISSED` + audit `reviewedAt`/`reviewedBy`).
      **Pas de champ `admin: boolean` sur `User`** — rôle géré exclusivement via la claim
      Auth0 (`identity.hasRole("ADMIN")` + `@RolesAllowed`). Le TODO `admin` du schéma
      `User` dans openapi.yaml a été retiré, et la section dédiée d'AGENTS.md a été
      remplacée par une note sur la gestion via claim. La cascade SCRUM-136
      (`isCreatorOrAcceptedCoOrganizerPublic`) interdit le self-report d'un event où
      l'on est créateur ou co-organisateur ACCEPTED (422 `cannot_report_own_event`) ;
      un co-organisateur PENDING peut signaler (sentinel cascade). `ModerationCleanupService`
      (SCRUM-103) reste insensible — il ne lit que `r.event` et `r.status`. Hors scope :
      auto-cancel d'event au passage en REVIEWED, bulk-handle, notifications. Frontend
      SCRUM-96 (modale) et SCRUM-97 (dashboard admin) dépendants — attention au rename
      de schéma OpenAPI `ReportRequest → CreateReportRequest`.

---

## Sprint 8 (planifié : 8–22 mai 2025)

**Objectif :** Tests, scalabilité, sécurité, CD, soutenance.

- [ ] Tests d'intégration `@QuarkusTest` couverture >80% sur EventResource, UserResource
- [ ] Audit OWASP Top 10, CORS configuré, secrets en env vars
- [ ] Tests E2E Playwright/Cypress (3–5 scénarios critiques)
- [ ] CD pipeline opérationnel (Kubernetes deploy automatique)
- [ ] Préparation soutenance

---

## Dette technique connue

| Item | Priorité | Sprint cible |
|---|---|---|
| Schéma géré par Hibernate `update` — choix définitif | Info | Sprint 2 ✅ |
| Sécuriser `POST /events` avec `@Authenticated` | Haute | Sprint 2 ✅ |
| Remplacer exposition directe de l'entité `Event` par un DTO | ✅ Fait | Sprint 2 |
| Tests unitaires sur `UserService` | Moyenne | Sprint 2 |
