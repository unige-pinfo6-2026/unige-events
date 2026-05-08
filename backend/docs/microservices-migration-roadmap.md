# Microservices Migration — Per-Service Roadmap

Dernière mise à jour : 2026-05-08

Ce document est le **plan opérationnel** de la migration backend monolithe →
microservices documentée dans
[`specs_archives/specs_claude/specs_microservices_migration.md`](../../specs_archives/specs_claude/specs_microservices_migration.md).

PR #158 (`chore(backend): scaffold microservices migration foundations`) a livré :
- les briques infra (Kong DB-less, Kafka KRaft, 10 topics) ;
- le multi-module Maven (`backend/pom.xml` parent + `services/legacy-monolith/`
  qui porte 100 % du code applicatif + 14 placeholders sous `services/<svc>/`) ;
- 14 squelettes Quarkus jar-packagés (un endpoint `/__service`) + 14
  Deployments Helm idle (`replicas: 0`) + 14 Services ClusterIP ;
- la spec et la doc Sprint 8 actuelle.

Ce document trace les **N follow-up PRs restantes** pour livrer la migration
complète. Chaque section est un template prêt à coller dans la description de
la PR correspondante.

---

## Convention commune à toutes les PR d'extraction

### Branche
`refactor/extract-<service>-service` partant de `main` (la branche
`refactor(backend)--migrate-to-microservices` reste un historique persistant).

### Titre PR
**Format imposé par `pr-title-check.yml`** : pour `refactor`, le scope DOIT être
`scrum-XXX`. Deux options :
- créer un ticket Jira `SCRUM-XXX extract <service>-service` et titrer
  `refactor(scrum-XXX): extract <service>-service from monolith` ;
- OU titrer `chore(backend): extract <service>-service from monolith` (scope
  libre pour les `chore`).

### Pattern d'extraction (générique)

Pour chaque service à extraire :

1. **POM `services/<svc>/pom.xml`** :
   - `<packaging>jar</packaging> → <packaging>quarkus</packaging>`
   - **Retirer** l'override surefire `argLine` (le scaffold désactive
     `@{argLine}` faute de jacoco — la PR d'extraction le ré-active).
   - Ajouter les extensions :
     ```xml
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-jdbc-postgresql</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-flyway</artifactId></dependency>
     <dependency><groupId>org.flywaydb</groupId><artifactId>flyway-database-postgresql</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-hibernate-orm-panache</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-hibernate-validator</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-oidc</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-rest-client-reactive</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-rest-client-reactive-jackson</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-smallrye-fault-tolerance</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-micrometer-registry-prometheus</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-container-image-jib</artifactId></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-jacoco</artifactId><scope>test</scope></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-test-security</artifactId><scope>test</scope></dependency>
     <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-devservices-postgresql</artifactId><scope>test</scope></dependency>
     <dependency><groupId>org.mockito</groupId><artifactId>mockito-core</artifactId><scope>test</scope></dependency>
     ```
   - Ajouter `<plugin>quarkus-maven-plugin</plugin>` aux `<build><plugins>`.

2. **`application.properties` du module** : enrichir avec datasource +
   flyway + OIDC complet + S3 (pour les services qui ont besoin) sur le
   modèle de [`services/legacy-monolith/src/main/resources/application.properties`](../services/legacy-monolith/src/main/resources/application.properties).

3. **Migration Flyway `V1__extract_<svc>_schema.sql`** dans
   `services/<svc>/src/main/resources/db/migration/V1__extract_<svc>_schema.sql` :
   ```sql
   -- Crée le schéma (no-op si existe), déplace les tables possédées,
   -- crée le rôle dédié + grants. Insère les baselines V1..V17 dans
   -- flyway_schema_history pour court-circuiter les migrations
   -- historiques (cf. spec décision 9).
   CREATE SCHEMA IF NOT EXISTS <svc>_svc;
   ALTER TABLE <table1> SET SCHEMA <svc>_svc;
   ALTER TABLE <table2> SET SCHEMA <svc>_svc;
   -- (...etc, par table possédée par le service)
   CREATE ROLE <svc>_svc_user WITH LOGIN PASSWORD '<from-doppler>';
   GRANT USAGE ON SCHEMA <svc>_svc TO <svc>_svc_user;
   GRANT ALL ON ALL TABLES IN SCHEMA <svc>_svc TO <svc>_svc_user;
   -- Pseudo-baseline Flyway pour ce schéma
   INSERT INTO <svc>_svc.flyway_schema_history (...) SELECT ... FROM legacy_monolith_flyway_history;
   ```

4. **Code Java** : déplacer les Resources / Services / Entities depuis
   `services/legacy-monolith/src/main/java/ch/unige/events/...` vers
   `services/<svc>/src/main/java/ch/unige/events/<svc>/`. Convertir les
   `@ManyToOne` cross-service en pointeurs Long/UUID bruts + REST clients.
   Conserver les `@PathParam`, `@Authenticated`, `@RolesAllowed`,
   `@PerUserRateLimit` à l'identique (le contrat OpenAPI reste invariant).

5. **REST clients** : pour chaque dépendance vers un autre service amont,
   déclarer une interface `@RegisterRestClient` :
   ```java
   @RegisterRestClient(configKey = "user-service")
   public interface UserServiceClient {
       @GET @Path("/api/users/{id}")
       UserPublicResponse getById(@PathParam("id") UUID id);
   }
   ```
   Configuration dans `application.properties` :
   ```properties
   quarkus.rest-client."user-service".url=http://user-service:8080
   ```
   Annotations resilience : `@Retry`, `@Timeout`, `@CircuitBreaker`,
   `@Fallback` (cf. spec décision 11).

6. **Kafka producteurs / consommateurs** : déclarer `@Outgoing` /
   `@Incoming` selon la table § 4.5 de la spec. Topics déjà créés par le Job
   `kafka-topics-init` du chart (cf. PR #158).

7. **Helm sub-template** [`k8s/chart/templates/<svc>/deployment.yaml`](../../k8s/chart/templates/) :
   - `replicas: 0 → replicas: 1` (et `2` en prod si HA souhaité).
   - L'image `ghcr.io/.../unige-events-<svc>:<tag>` est désormais buildée +
     pushée par CI (POM ajoute `quarkus-container-image-jib`).
   - Les readinessProbe `/api/q/health/ready` continuent de fonctionner.

8. **Kong route** [`k8s/chart/templates/kong/configmap-routes.yaml`](../../k8s/chart/templates/kong/configmap-routes.yaml) :
   - Décommenter le bloc `services: - name: <svc>-service` correspondant.
   - Restreindre la `paths` du `api-catchall` pour qu'elle ne matche plus
     les chemins du service extrait (Kong route au plus spécifique en
     premier, mais explicite est plus sûr).

9. **Suppression dans `legacy-monolith`** : retirer les fichiers Java
   correspondants + leurs tests + adapter les Resources voisines (ex.
   `FavoriteResource` perd la méthode `getShareInfo` quand share-service
   est extrait).

10. **Tests** : unit tests par service (déplacés depuis legacy-monolith
    + adaptés aux REST clients mockés via `@QuarkusTestResource`),
    integration tests `@QuarkusTest` avec DevServices PostgreSQL +
    Testcontainers Kafka, contract tests Pact JSON dans
    `backend/contract-tests/pacts/<consumer>-<provider>.json`.

11. **CI build.yml** — strategy matrix step 17 (cf. plus bas) PAS
    nécessaire pour la première extraction ; le build `cd backend &&
    ./mvnw verify` continue de marcher (legacy + tous les services
    extraits + scaffolds restants). À refondre plus tard pour
    parallélisation.

12. **Doc** : enrichir `backend/docs/data-model.md` (colonne « Service
    propriétaire » par entité), `backend/docs/api-contract.md` (colonne
    « Service amont » par endpoint), `backend/docs/sprint-context.md`
    (bloc « Sprint X — extraction <svc>-service »). `git diff --stat
    frontend/` et `git diff --stat openapi/` doivent rester strictement
    vides.

### Commits atomiques suggérés (par PR)

```
chore(backend): bump <svc>-service POM to quarkus packaging + add extensions
feat(backend): add Flyway V1 extract migration for <svc>-service schema
refactor(backend): move <svc> resources/services/entities into <svc>-service module
feat(backend): wire <svc>-service REST clients to <upstream services>
feat(backend): wire <svc>-service Kafka producers/consumers
chore(infra): bump <svc>-service helm replicas to 1 + flip kong route
refactor(backend): remove <svc>-related code from legacy-monolith
test(backend): add Pact contract tests for <svc>-service consumers
docs(backend): document <svc>-service ownership in data-model + api-contract
```

---

## Ordre des PR d'extraction

Ordre **strict** dicté par la spec décision 20 (le moins couplé d'abord) :

| # | PR | Service | Endpoints | Couplage cross-service |
|---|---|---|---|---|
| 1 | `refactor/extract-share-service` | `share-service` | `/events/{id}/share`, `/s/{shortCode}` | Lit `Event.shareCode` (REST sync vers `event-service`) — qui n'existera qu'après la PR 13 ; **alternative** : laisser share-service lire directement la table `events` du schéma `public` partagé tant que `event-service` n'existe pas |
| 2 | `refactor/extract-view-service` | `view-service` | `/events/{id}/view` | Idempotent write `event_views` ; lookup event PUBLISHED via REST sync |
| 3 | `refactor/extract-favorite-service` | `favorite-service` | `/events/{id}/favorite`, `/users/me/favorites` | CRUD sur `favorites` ; lookup event existence |
| 4 | `refactor/extract-calendar-service` | `calendar-service` | `/users/me/calendar-token*`, `/calendar/{token}.ics` | Lecture pure : token via `user-service`, favoris via `favorite-service`, attendings via `attendance-service`, events via `event-service` |
| 5 | `refactor/extract-follow-service` | `follow-service` | `/users/{id}/follow`, `/followers`, `/following`, `/follow-requests/*` | Lit `User.profilePublic` via `user-service` REST sync |
| 6 | `refactor/extract-comment-service` | `comment-service` | `/events/{id}/comments`, `/comments/{id}` | Cascade lourde : visibilité event (`event-service`), org cascade (`co-organizer-service`), author lookup (`user-service`) |
| 7 | `refactor/extract-co-organizer-service` | `co-organizer-service` | `/events/{id}/co-organizers/*`, `/users/me/co-organizer-invitations` | Cascade d'autorisation centrale ; expose `GET /events/{id}/co-organizers/check?userId=` (interne) consommé par les services voisins |
| 8 | `refactor/extract-attendance-service` | `attendance-service` | `/events/{id}/attend*`, `/users/me/attendances`, `/users/me/participations` | Lookup event PUBLISHED + cascade co-org pour `getAttendees` |
| 9 | `refactor/extract-report-service` | `report-service` | `/events/{id}/report`, `/admin/reports*` + scheduler `ModerationCleanupJob` | Émet `events.banned` Kafka consommé par `event-service` ; cascade co-org pour self-report check |
| 10 | `refactor/extract-stats-service` | `stats-service` | `/events/{id}/stats` | Lecture pure ; agrège counts via REST sync (view, attendance, favorite) |
| 11 | `refactor/extract-me-aggregator-service` | `me-aggregator-service` | `/users/me/events`, `/users/me/attendances`, `/users/me/favorites`, `/users/me/participations` | BFF — proxy multi-domain, dépend de tous les services aval |
| 12 | `refactor/extract-user-service` | `user-service` | `/users/me`, `/users/me/image`, `/users/me/banner`, `/users/{id}` | Cœur : l'auth Auth0 provisionne `User`. Dépendance de presque tout le reste |
| 13 | `refactor/extract-event-service` | `event-service` | `/events*`, `/admin/events*`, `/events/search`, `/events/featured` + scheduler `EventExpirationJob` | Le plus gros : 9 paths principaux + recurrence + featured + admin actions. Émet `events.{published,cancelled,expired}` |

---

## Détails par service

### PR 1 — `share-service`

**Files moved depuis `services/legacy-monolith/`** :
- `src/main/java/ch/unige/events/resource/RedirectResource.java` → `services/share-service/src/main/java/ch/unige/events/share/RedirectResource.java`
- `src/main/java/ch/unige/events/service/ShareService.java` → idem dans `share-service`
- `src/main/java/ch/unige/events/dto/event/ShareResponse.java` → idem
- Méthode `getShareInfo` retirée de `services/legacy-monolith/src/main/java/ch/unige/events/resource/FavoriteResource.java`
- Tests `ShareServiceCoverageTest`, `RedirectResourceTest` (s'ils existent) → `share-service`

**Tables possédées** : aucune (la colonne `Event.shareCode` reste avec
`event-service` à terme — pour S8, share-service lit le schéma `public`
partagé).

**Kong route** :
```yaml
- name: share-service
  url: http://share-service:8080
  routes:
    - { name: event-share, paths: [/api/events/(?:\d+)/share] }
    - { name: shortlink,   paths: [/api/s/[^/]+$] }
```

**REST clients** : `EventServiceClient` pour résoudre `shortCode → event` —
mais `event-service` n'existe pas encore en PR 1. Workaround : pour cette
première extraction, share-service lit directement la table `events` du
schéma `public` partagé (entité `Event` minimale read-only). Quand
event-service sera extrait (PR 13), une PR de cleanup remplace l'accès
direct par REST.

**Kafka** : aucun producteur ni consommateur en S8.

---

### PR 2 — `view-service`

**Files moved** :
- `EventViewResource.java`, `EventViewService.java`, `EventView.java` (entité) → `view-service`
- Tests `EventViewServiceCoverageTest`, `EventViewResourceTest`
- Migration Flyway dédiée `V1__extract_view_schema.sql` (déplace `event_views` table + posera baseline)

**Tables possédées** : `event_views`.

**Kong route** :
```yaml
- name: view-service
  url: http://view-service:8080
  routes:
    - { name: event-view, paths: [/api/events/(?:\d+)/view] }
```

**REST clients** : `EventServiceClient` pour vérifier event PUBLISHED.

**Kafka** : aucun en S8 (potentiellement consommer `events.banned` plus tard pour purger les vues d'events bannis).

---

### PR 3 — `favorite-service`

**Files moved** :
- `FavoriteResource.java`, `FavoriteService.java`, `Favorite.java` → `favorite-service`
- Tests
- `V1__extract_favorite_schema.sql` (table `favorites`)

**Tables possédées** : `favorites`.

**Kong route** :
```yaml
- name: favorite-service
  url: http://favorite-service:8080
  routes:
    - { name: favorite-actions, paths: [/api/events/(?:\d+)/favorite] }
    - { name: my-favorites,     paths: [/api/users/me/favorites] }
```

**REST clients** : `EventServiceClient` (existence event), `UserServiceClient` (resolveUserId).

---

### PR 4 — `calendar-service`

**Files moved** :
- `CalendarResource.java`, `CalendarService.java` (logique ICS), `IcsBuilder.java` (util) → `calendar-service`
- Tests
- Pas de migration Flyway dédiée — `User.calendarToken` reste avec `user-service`.

**Tables possédées** : aucune (lecture pure cross-service).

**Kong route** :
```yaml
- name: calendar-service
  url: http://calendar-service:8080
  routes:
    - { name: calendar-token, paths: [/api/users/me/calendar-token] }
    - { name: calendar-regen, paths: [/api/users/me/calendar-token/regenerate] }
    - { name: ics-feed,       paths: [/api/calendar/[^/]+\.ics] }
```

**REST clients** : `UserServiceClient` (read/regenerate calendarToken),
`FavoriteServiceClient`, `AttendanceServiceClient`, `EventServiceClient`
(events ATTENDING + favoris pour générer le feed).

---

### PR 5 — `follow-service`

**Files moved** :
- `FollowResource.java`, `FollowRequestResource.java`, `FollowService.java`, `Follow.java`, `FollowStatus.java` → `follow-service`
- DTO `FollowDTO`, `FollowRequestDTO`
- Tests `FollowServiceCoverageTest`, `FollowResourceTest`, `FollowRequestResourceTest`
- `V1__extract_follow_schema.sql` (table `follows`, FK vers `users`)

**Tables possédées** : `follows`.

**Kong route** :
```yaml
- name: follow-service
  url: http://follow-service:8080
  routes:
    - { name: follow-actions,  paths: [/api/users/[^/]+/follow] }
    - { name: follow-listings, paths: [/api/users/[^/]+/(?:followers|following)] }
    - { name: follow-requests, paths: [/api/follow-requests/(?:\d+)/(?:accept|reject)] }
    - { name: my-follow-reqs,  paths: [/api/users/me/follow-requests] }
```

**REST clients** : `UserServiceClient` (lit `profilePublic` cible).

**Kafka** : `users.followed`, `users.follow-requested`, `users.follow-accepted` (producteurs).

**À déplacer aussi** : `UserService.getPublicProfile` retourne `PublicProfileView` qui inclut compteurs follow + followStatus. Une fois follow-service extrait, `user-service` (PR 12) appellera `follow-service` REST pour récupérer ces compteurs.

---

### PR 6 — `comment-service`

**Files moved** :
- `CommentResource.java`, `CommentDirectResource.java`, `CommentService.java`, `Comment.java` → `comment-service`
- DTO `CommentDTO`, `CreateCommentRequest`
- Tests
- `V1__extract_comment_schema.sql` (table `comments`, FK auto-référence `parent_comment_id` ON DELETE SET NULL préservée)

**Tables possédées** : `comments`.

**Kong route** :
```yaml
- name: comment-service
  url: http://comment-service:8080
  routes:
    - { name: event-comments, paths: [/api/events/(?:\d+)/comments] }
    - { name: comment-direct, paths: [/api/comments/(?:\d+)] }
```

**REST clients** : `EventServiceClient` (visibilité ISSUE-92), `CoOrganizerServiceClient` (cascade SCRUM-136 via `/events/{id}/co-organizers/check`), `UserServiceClient` (author lookup pour DTO).

**Kafka** : `comments.created` (producteur).

**Rate limit `comments.post` 10/min** préservé via `@PerUserRateLimit`.

---

### PR 7 — `co-organizer-service`

**Files moved** :
- `EventCoOrganizerResource.java`, `EventCoOrganizerService.java`, `EventCoOrganizer.java`, `CoOrganizerStatus.java` → `co-organizer-service`
- DTOs `CoOrganizerDTO`, `CoOrganizerInvitationDTO`, `InviteCoOrganizerRequest`
- Tests
- `V1__extract_co_organizer_schema.sql` (table `event_co_organizers`)

**Tables possédées** : `event_co_organizers`.

**Kong route** :
```yaml
- name: co-organizer-service
  url: http://co-organizer-service:8080
  routes:
    - { name: co-organizers,        paths: [/api/events/(?:\d+)/co-organizers] }
    - { name: co-organizer-userid,  paths: [/api/events/(?:\d+)/co-organizers/[^/]+$] }
    - { name: co-organizer-me,      paths: [/api/events/(?:\d+)/co-organizers/me/(?:accept|decline)] }
    - { name: my-co-org-invites,    paths: [/api/users/me/co-organizer-invitations] }
```

**REST clients** : `EventServiceClient` (resolve creator), `UserServiceClient`.

**Kafka** : `co-organizers.invited`, `co-organizers.accepted` (producteurs).

**Endpoint interne** : exposer `GET /events/{eventId}/co-organizers/check?userId=<uuid>` retournant `{accepted: boolean}` pour la cascade SCRUM-136 utilisée par `comment-service`, `attendance-service`, `stats-service`, `event-service`. Ce path n'est pas dans `openapi.yaml` (API privée backend) — documenté dans `backend/docs/architecture.md`.

---

### PR 8 — `attendance-service`

**Files moved** :
- `AttendanceResource.java`, `AttendanceService.java`, `Attendance.java`, `AttendanceStatus.java` → `attendance-service`
- DTO `AttendanceDTO`
- Tests `AttendanceServiceCoverageTest`, `AttendanceResourceTest`
- `V1__extract_attendance_schema.sql` (table `attendances` + part de V1 historique pour la CHECK constraint)

**Tables possédées** : `attendances`.

**Kong route** :
```yaml
- name: attendance-service
  url: http://attendance-service:8080
  routes:
    - { name: attend-actions,  paths: [/api/events/(?:\d+)/attend, /api/events/(?:\d+)/attendees] }
    - { name: my-attendances,  paths: [/api/users/me/attendances, /api/users/me/participations] }
```

**REST clients** : `EventServiceClient` (PUBLISHED + capacity + registrationDeadline), `UserServiceClient` (displayName + avatarUrl pour AttendanceDTO), `CoOrganizerServiceClient` (cascade getAttendees).

**Kafka** : aucun en S8 (potentiellement `attendances.created` pour stats projection).

---

### PR 9 — `report-service`

**Files moved** :
- `ReportResource.java`, `AdminReportResource.java`, `ReportService.java`, `Report.java`, `ReportReason.java`, `ReportStatus.java` → `report-service`
- `ModerationCleanupJob.java`, `ModerationCleanupService.java` → `report-service`
- DTOs `ReportDTO`, `CreateReportRequest`, `HandleReportRequest`
- Tests
- `V1__extract_report_schema.sql` (table `reports` + V7/V10 historiques)

**Tables possédées** : `reports`.

**Kong route** :
```yaml
- name: report-service
  url: http://report-service:8080
  routes:
    - { name: report-event,    paths: [/api/events/(?:\d+)/report] }
    - { name: admin-reports,   paths: [/api/admin/reports] }
    - { name: admin-report-id, paths: [/api/admin/reports/(?:\d+)] }
```

**REST clients** : `EventServiceClient` (PUBLISHED check + creator pour self-report cascade), `CoOrganizerServiceClient` (cascade SCRUM-136), `UserServiceClient`.

**Kafka** : `events.banned` (producteur — émis quand `ModerationCleanupJob` ou `ReportService.handle()` ban un event ; consommé par `event-service` pour appliquer `status = BANNED`).

**Scheduler** `ModerationCleanupJob` (`cron 0 0 3 * * ? Europe/Zurich`) tourne dans le pod `report-service`. `replicas: 1` strict (pas de leader-election en S8).

---

### PR 10 — `stats-service`

**Files moved** :
- `EventStatsResource.java`, `EventStatsService.java` → `stats-service`
- DTO `EventStatsDTO`
- Tests
- Aucune migration Flyway (lecture pure).

**Tables possédées** : aucune (lecture pure).

**Kong route** :
```yaml
- name: stats-service
  url: http://stats-service:8080
  routes:
    - { name: event-stats, paths: [/api/events/(?:\d+)/stats] }
```

**REST clients** : `EventServiceClient` (creator check + capacity), `CoOrganizerServiceClient` (cascade), `ViewServiceClient` (viewCount), `AttendanceServiceClient` (attendingCount), `FavoriteServiceClient` (interestedCount). Parallel fan-out via Mutiny `Uni.combine().all().unis(...)`.

**Kafka** : pourrait consommer `events.published` pour pré-cache mais pas requis en S8.

---

### PR 11 — `me-aggregator-service`

**Files moved** :
- Endpoint handlers pour `/users/me/events`, `/users/me/attendances`, `/users/me/favorites`, `/users/me/participations` → `me-aggregator-service`
- Pas d'entité, pas de service métier — c'est un BFF qui fait du proxy intelligent.

**Tables possédées** : aucune.

**Kong route** :
```yaml
- name: me-aggregator-service
  url: http://me-aggregator-service:8080
  routes:
    - { name: my-events-bff,        paths: [/api/users/me/events] }
    - { name: my-attendances-bff,   paths: [/api/users/me/attendances] }
    - { name: my-favorites-bff,     paths: [/api/users/me/favorites] }
    - { name: my-participations-bff, paths: [/api/users/me/participations] }
```

**REST clients** : tous les services aval (`event-service`, `attendance-service`, `favorite-service`).

---

### PR 12 — `user-service`

**Files moved** :
- `UserResource.java`, `UserService.java`, `User.java` → `user-service`
- DTOs `UserProfileResponse`, `UserPublicResponse`, `UpdateProfileRequest`, `PublicProfileView`
- Tests
- `V1__extract_user_schema.sql` (tables `users` + `user_interests` + V0 historique pour la création initiale)

**Tables possédées** : `users`, `user_interests` (collection EAGER).

**Kong route** :
```yaml
- name: user-service
  url: http://user-service:8080
  routes:
    - { name: user-me,             paths: [/api/users/me] }
    - { name: user-by-id,          paths: [/api/users/(?:[^/]+)$], strip_path: false }
    - { name: user-image-banner,   paths: [/api/users/me/image, /api/users/me/banner] }
```

**REST clients** : `FollowServiceClient` (compteurs + followStatus pour `getPublicProfile`).

---

### PR 13 — `event-service`

**Files moved** :
- `EventResource.java`, `AdminEventResource.java`, `EventSearchResource.java`, `EventService.java`, `EventSearchService.java`, `FeaturedService.java`, `Event.java`, `EventStatus.java`, `EventCategory.java`, `Faculty.java`, `RecurrenceFrequency.java`, `Timeframe.java` → `event-service`
- `EventExpirationJob.java`, `EventExpirationService.java` → `event-service`
- DTOs `EventDTO`, `CreateEventRequest`, `UpdateEventRequest`, `RecurrenceRequest`
- Helper `RecurrenceGenerator.java` (util)
- `FileStorageService.java` (S3 upload, partagé) → reste avec event-service (gestion bannière)
- Tests massifs
- `V1__extract_event_schema.sql` (tables `events` + `event_tags` + parts de V1/V2/V9/V11/V12/V13/V17 historiques)

**Tables possédées** : `events`, `event_tags`.

**Kong route** :
```yaml
- name: event-service
  url: http://event-service:8080
  routes:
    - { name: events-list,        paths: [/api/events],          strip_path: false }
    - { name: events-by-id,       paths: [/api/events/(?:\d+)], strip_path: false }
    - { name: events-cancel,      paths: [/api/events/(?:\d+)/cancel] }
    - { name: events-restore,     paths: [/api/events/(?:\d+)/restore] }
    - { name: events-publish,     paths: [/api/events/(?:\d+)/publish] }
    - { name: events-image,       paths: [/api/events/(?:\d+)/image] }
    - { name: events-occurrences, paths: [/api/events/(?:\d+)/occurrences] }
    - { name: events-featured,    paths: [/api/events/featured, /api/admin/events/(?:\d+)/(?:un)?feature] }
    - { name: events-search,      paths: [/api/events/search] }
```

**REST clients** : `UserServiceClient` (creator lookup), `CoOrganizerServiceClient` (cascade). Consomme `events.banned` Kafka (depuis `report-service`) pour appliquer `status = BANNED`.

**Kafka** : producteur de `events.published`, `events.cancelled`, `events.expired`. Consommateur de `events.banned` (depuis report-service).

**Scheduler** `EventExpirationJob` (`every 1h`) tourne dans le pod `event-service`. `replicas: 1` strict.

À la fin de cette PR : **legacy-monolith est vide**. Étape 15 ci-dessous le supprime.

---

## PR 14 — Étape 15 : Suppression `legacy-monolith` + `templates/api/`

**Branche** : `refactor/remove-legacy-monolith`.

**Titre PR** : `refactor(scrum-XXX): remove legacy monolith — all services extracted` (ou `chore(backend): remove legacy monolith now that all services are extracted` si pas de Jira).

**Files** :
- `rm -rf backend/services/legacy-monolith/`
- `rm -rf k8s/chart/templates/api/`
- `backend/pom.xml` retire `<module>services/legacy-monolith</module>`
- `k8s/chart/values.yaml` + `values-preview.yaml` retirent la section `api:`
- `.github/workflows/deploy.yml` retire `--set image.api.tag=...`
- `k8s/chart/templates/kong/configmap-routes.yaml` retire le bloc
  `monolith-api` + son catchall (la table de routes Kong est désormais
  uniquement composée des 13 services extraits + me-aggregator)

**Tests** : `./mvnw verify` doit toujours passer (le parent reactor a 14
modules tous Quarkus). `Deploy to Preview` doit toujours déployer (tous les
services extraits avec leurs replicas + Kong route 100% dispatché).

---

## PR 15 — Étape 16 totale : Documentation finale

**Branche** : `refactor/microservices-final-docs`.

**Titre PR** : `docs(backend): finalize microservices architecture documentation`.

**Files** :
- `backend/docs/architecture.md` — réécriture complète : section
  « Vue d'ensemble microservices » liste les 13 services + me-aggregator,
  diagramme texte mis à jour, table des routes Kong, table des topics
  Kafka avec producteurs/consommateurs réels, flux d'authentification,
  flux de lecture / écriture types.
- `backend/docs/data-model.md` — colonne « Service propriétaire » ajoutée
  à chaque table d'entité ; sous-section par service propriétaire.
- `backend/docs/api-contract.md` — colonne « Service amont » à la grande
  table des endpoints.
- `backend/docs/dev-guide.md` — workflow dev local : `docker-compose -f
  docker-compose.dev.yml up` qui lance Kong + Kafka + 13 services + db +
  minio. Section « Lancer un service en isolation » : `cd
  backend/services/<svc> && ../../mvnw quarkus:dev`.
- `AGENTS.md` racine — référence à la nouvelle topologie et aux services
  par bounded context.
- `backend/AGENTS.md` — section « Architecture par service » (chaque
  service a son AGENTS.md plus tard ?).
- `backend/docs/sprint-context.md` — bloc final « Sprint 8 — Migration
  microservices LIVRÉE ».

---

## PR 16 — Étape 17 : CI matrix per-service

**Branche** : `ci/matrix-per-service`.

**Titre PR** : `ci(backend): build and test microservices in parallel matrix`.

**Files** :
- `.github/workflows/build.yml` — refonte du job `build-backend` en
  strategy matrix `service in [user, event, attendance, favorite, view,
  co-organizer, comment, follow, report, stats, share, calendar,
  notification, me-aggregator]`. Chaque cellule de matrice :
  ```yaml
  - name: Build & Test ${{ matrix.service }}
    working-directory: backend/services/${{ matrix.service }}
    run: ./mvnw verify -B \
         -Dquarkus.container-image.build=true \
         -Dquarkus.container-image.push=true \
         -Dquarkus.container-image.name=unige-events-${{ matrix.service }} \
         -Dquarkus.container-image.tag=${{ github.sha }}
  - name: SonarQube Scan ${{ matrix.service }}
    working-directory: backend/services/${{ matrix.service }}
    env: { SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }} }
    run: ./mvnw sonar:sonar -B \
         -Dsonar.projectKey=unige-events-backend-${{ matrix.service }}
  ```
- `.github/workflows/deploy.yml` — `helm upgrade` reçoit
  `--set image.<svc>.tag=$SHA` pour chaque service.
- `pom.xml` racine — properties `<sonar.projectKey>` retiré (chaque
  service le définit dans son POM).
- Chaque `services/<svc>/pom.xml` — ajoute son `<sonar.projectKey>`.

**Pré-requis** : 13 projects créés dans SonarCloud (`unige-events-backend-user`, `unige-events-backend-event`, etc.). Documentation avec captures de
config SonarCloud organization view.

---

## Cleanup post-migration

- **Branche `refactor(backend)--migrate-to-microservices`** : peut être
  supprimée une fois toutes les PRs above mergées. La branche garde l'historique
  des fondations + scaffolds + spec.
- **Spec `specs_archives/specs_claude/specs_microservices_migration.md`** :
  archivée telle quelle (immutable).
- **Bug pr-title-check.yml** : à corriger si l'on veut autoriser
  `refactor(backend): ...` sans scope Jira. Hors scope migration —
  ticket dédié `chore(ci): allow free scope for refactor when no Jira ticket`.

---

## Récap visuel

```
     PR #158 (cette PR — fondations + scaffolds)
        │
        ▼
     ┌────────────────────────────────────┐
     │  Étapes 0 + 1 + 2..14 scaffolding  │
     │  + step-16 partiel                  │
     │  ✅ Kong + Kafka + 14 modules      │
     │  ✅ 14 Helm Deployments idle       │
     │  ❌ Code monolithe pas découpé     │
     └────────────────────────────────────┘
        │
        ▼
     ┌────────────────────────────────────┐
     │  PR 1 — extract share-service      │
     │  PR 2 — extract view-service       │
     │  PR 3 — extract favorite-service   │
     │  PR 4 — extract calendar-service   │
     │  PR 5 — extract follow-service     │
     │  PR 6 — extract comment-service    │
     │  PR 7 — extract co-organizer-svc   │
     │  PR 8 — extract attendance-svc     │
     │  PR 9 — extract report-svc         │
     │  PR 10 — extract stats-svc         │
     │  PR 11 — extract me-aggregator     │
     │  PR 12 — extract user-svc          │
     │  PR 13 — extract event-svc         │
     │     ↓ (legacy-monolith vide)      │
     │  PR 14 — remove legacy monolith    │
     │  PR 15 — final docs                │
     │  PR 16 — CI matrix                 │
     └────────────────────────────────────┘
        │
        ▼
     [Migration COMPLÈTE]
```

---

## TL;DR pour Agon

PR #158 livre les **fondations + scaffolds + docs** du Sprint 8. Les **13
extractions de code par service**, plus la **suppression du monolithe**, plus
la **doc finale**, plus la **CI matrix** sont **16 PRs follow-up** documentées
ici, à exécuter par le DevOps. Chaque PR follow-up a un template
copy-paste-able dans ce doc, un ordre strict, une liste précise des fichiers à
toucher, et le wording du commit.
