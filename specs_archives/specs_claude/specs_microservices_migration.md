# Migration vers une architecture microservices — UNIGE Events backend

| Champ | Valeur |
|---|---|
| Ticket Jira | Migration `monolithe → microservices` (cours pinfo6, brief Agon) |
| Sprint | S8 (calendrier produit — sprint « scalabilité, CD, soutenance », cf. [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md#L357-L365)) |
| Épic | Hors backlog produit — refonte d'architecture imposée par le cours |
| Story Points | Estimation ≥ 21 (extraction multi-services, infra Kong + Kafka, Helm umbrella, CI/CD par service, tests cross-service) — non livrable en un seul sprint, voir « Stratégie de coexistence et rollback » |
| Branche | `refactor(backend): migrate-to-microservices` (NB : non créée dans cette session — strictement nommée dans la spec, cf. décision 1) |
| Base | `origin/main` (tip à la date de rédaction : `ce43e03 ci(cleanup): add helm uninstall timeout of 3m to avoid long blocking on failure`) |
| Auteur spec | Claude Code (rédaction) — Dany Dosh (mandataire) |
| Date | 2026-05-08 |
| PR de référence (future) | refactor(backend): migrate to microservices architecture with Kong gateway and Kafka broker |
| Frontend lié (consommateur aval) | **Aucun ticket front prévu.** Le contrat OpenAPI reste byte-pour-byte identique — `git diff --stat frontend/` strictement vide à l'ouverture de la PR (cf. décision 24 et section « Changements frontend »). |
| Notifications inter-services | Topic Kafka `notifications.events` consommé par `notification-service` — l'entité `Notification` (back-end-of-record persistant) reste **hors scope** pour cette migration ; elle sera livrée dans le ticket dédié SCRUM-99 (S7+) après la coexistence monolithe ↔ microservices stabilisée (cf. décision 14). |
| Dépendances amont | V17 Flyway mergé sur `main` (cf. [`sprint-context.md`](backend/docs/sprint-context.md#L7-L31)). Aucune autre PR ouverte qui touche `Event`, `User`, `Resource`, ou les schedulers. |
| Règle d'or `openapi-first` | **NON APPLICABLE au sens strict — `openapi/openapi.yaml` ne change PAS.** La migration matérialise le contrat existant en routes Kong amont vers N services. **APPLICABLE en sens inverse :** chaque microservice expose les mêmes paths/schémas qu'aujourd'hui ; toute déviation accidentelle (rename de champ, code HTTP différent, header manquant) est un défaut bloquant la PR. Voir contraintes Agon point 1 + section « Changements frontend ». |

> **Note d'implémentation (2026-05-08).** Cette spec décrit comment **réaliser**
> la migration ; elle ne décrit pas le résultat d'une migration déjà effectuée.
> L'implémentation effective (extraction service par service, intégration Kong,
> intégration Kafka, Helm umbrella, CI/CD par service) est livrée dans la PR
> `refactor(backend): migrate to microservices architecture with Kong gateway
> and Kafka broker` (à ouvrir dans un sprint dédié). Cette spec est l'**unique
> source de vérité** pour cette PR — toute déviation par rapport à ses 30+
> décisions tranchées doit être justifiée auprès du mandataire AVANT exécution.
>
> **Leçon Flyway-immutabilité (rappel — cf. [`specs_scrum-139.md` lignes 32-49](specs_archives/specs_claude/specs_scrum-139.md#L32-L49)).** La règle « migration committée = immutable » s'applique **par-base**. Quand cette migration aboutit à une **base par service** (cf. décision 8), la numérotation Flyway redémarre à V1 dans chaque service — mais les checksums posés par le monolithe restent gravés dans la table `flyway_schema_history` du namespace de chaque preview deploy. La stratégie de coexistence (cf. décision 22) **doit** copier la `flyway_schema_history` du monolithe dans chaque nouvelle base au moment de l'extraction, pour que Flyway accepte de reprendre à partir de V18+.

---

## Contexte

### Le besoin produit (cours pinfo6 — brief Agon)

> *« Le backend de ce projet (Quarkus 3 / Java 21 / PostgreSQL / Kubernetes + Helm) est actuellement un monolithe. Dans le cadre du cours, nous devons migrer vers une architecture microservices. »* — brief Agon, 2026-05-08

Le projet UNIGE Events a été livré en S1..S7 sous forme de **monolithe Quarkus**
(1 service backend `api`, 1 base PostgreSQL partagée, 1 chart Helm umbrella).
Cette architecture a permis un développement rapide — 9 entités, 17 resources,
17 services, 2 schedulers, 17 migrations Flyway, ~960 tests verts, couverture
SonarCloud > 80 % — mais le cours impose en S8 le passage à une architecture
**microservices**, conçue autour des entités/domaines déjà identifiés. La
migration doit préserver toutes les propriétés observables : contrat API
identique côté frontend, auth Auth0 fonctionnelle sur chaque service, ≥ 80 %
couverture SonarCloud, migrations Flyway tracées, conventions AGENTS.md.

### Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| Aucune frontière de service explicite — `EventResource`, `UserResource`, `AttendanceResource`, etc. partagent un même classpath, une même JVM, un même pool JDBC | Couplage fort : toute modification d'`Event` impacte les 14 services applicatifs ; pas de scaling indépendant |
| Une seule base `unige_events` — toutes les entités JPA partagent les mêmes contraintes FK et le même schéma Flyway | Casse la propriété de **bounded context** (Domain-Driven Design) : un service `attendance-service` ne peut pas évoluer son schéma indépendamment d'`event-service` |
| Aucun broker de messages — toute communication inter-couches est in-process via `@Inject` | Impossibilité de découpler les workflows asynchrones (notifications, projections, fan-out modération) ; chaque appel Resource → Service est strict synchrone |
| Aucune API Gateway — l'Ingress Nginx route directement `/api/*` vers `Pod api` (Quarkus monolithe) | Pas de point d'agrégation pour cross-cutting concerns (rate-limit global, plugins OIDC partagés, observabilité, CORS centralisé) |
| Schedulers (`@Scheduled`) couplés au monolithe — `EventExpirationJob` (1h) et `ModerationCleanupJob` (03h00) tournent dans la même JVM que les Resources | Impossible de les isoler / leader-elect en multi-instance ; tout déploiement de `api` redémarre les jobs |
| Aucun chart Helm par service — `k8s/chart/` déploie 1 `Deployment api` + 1 `Service api` + 1 `StatefulSet db` + 1 `StatefulSet minio` | Pas de granularité de déploiement ; tout `helm upgrade` redéploie tout |
| Aucun pipeline build par service dans `.github/workflows/build.yml` — un seul job `build-backend` qui builde le JAR monolithe | Pas de build incrémental ; chaque PR rebuild tout, même si elle touche 1 seul service |

### Ce qui existe déjà à RÉUTILISER tel quel (ne pas recréer)

| Élément | Fichier / ligne | Rôle dans la migration |
|---|---|---|
| Contrat API stable | [`openapi/openapi.yaml`](openapi/openapi.yaml) (4076 lignes, ~50 paths) | **Invariant strict** — chaque microservice doit servir la sous-section qui lui revient, byte-pour-byte |
| Architecture en couches Resource → Service → Entity | [`backend/docs/architecture.md` lignes 25-62](backend/docs/architecture.md#L25-L62) | Chaque microservice **conserve** cette architecture en interne — la migration découpe verticalement (par domaine), pas horizontalement (par couche) |
| 9 entités JPA avec frontières de domaine déjà claires | [`backend/src/main/java/ch/unige/events/entity/`](backend/src/main/java/ch/unige/events/entity/) | Chaque service **possède** un sous-ensemble (cf. décision 5 et tableau « Découpage en services ») |
| 17 services applicatifs `@ApplicationScoped` + `@Transactional` | [`backend/src/main/java/ch/unige/events/service/`](backend/src/main/java/ch/unige/events/service/) | **Préservés tels quels** dans le service propriétaire ; les calls cross-service deviennent REST sync ou Kafka async (cf. décision 11) |
| 17 resources JAX-RS | [`backend/src/main/java/ch/unige/events/resource/`](backend/src/main/java/ch/unige/events/resource/) | **Distribuées** entre les services propriétaires (cf. tableau § 4.2) ; chaque resource garde son `@Path`, ses codes HTTP, ses annotations `@RolesAllowed` |
| `quarkus-oidc` configuré en mode `service` | [`application.properties` lignes 25-39](backend/src/main/resources/application.properties#L25-L39) | **Réutilisé tel quel** sur **chaque** microservice (cf. décision 7) — claim path identique, audience identique, `quarkus.oidc.enabled=false` en `%test` préservé |
| `quarkus-flyway` avec `baseline-on-migrate=true` + `baseline-version=0` | [`application.properties` lignes 17-23](backend/src/main/resources/application.properties#L17-L23) | **Réutilisé tel quel** par service. La numérotation Flyway repart à V1 par service ; les anciennes migrations V1..V17 sont **distribuées** entre services (cf. décision 9 et tableau § 4.3) |
| 2 schedulers (`@Scheduled`) | [`backend/src/main/java/ch/unige/events/scheduler/`](backend/src/main/java/ch/unige/events/scheduler/) (`EventExpirationJob` 1h, `ModerationCleanupJob` cron 03h00) | **Réaffectés** au service propriétaire (cf. décision 13 et tableau § 4.4) ; `quarkus.scheduler.enabled=false` en `%test` préservé |
| `@PerUserRateLimit` (annotation custom + filter) | [`backend/src/main/java/ch/unige/events/config/PerUserRateLimit.java`](backend/src/main/java/ch/unige/events/config/PerUserRateLimit.java) | **Préservé en local** par service pour les endpoints individuels ; le rate-limit **global** par client passe à Kong (plugin `rate-limiting`, cf. décision 6) |
| Pattern test `@QuarkusTest` + `@TestSecurity(user="auth0\|alice")` + RestAssured | tests existants | **Réutilisé tel quel** dans chaque service ; les tests inter-services additionnels sont des **contract tests Pact** (cf. décision 18) |
| Helm chart umbrella avec sous-templates `api/`, `web/`, `db/`, `minio/`, `cloudflared/`, `ingress/` | [`k8s/chart/templates/`](k8s/chart/templates/) | **Étendu** par N nouveaux sous-templates (1 par service) + 1 sous-template Kong + 1 sous-template Kafka (cf. décision 16) |
| CI build single-job `build-backend` | [`.github/workflows/build.yml` lignes 16-63](.github/workflows/build.yml#L16-L63) | **Refondé en matrice** `service in [user, event, attendance, favorite, view, co-organizer, comment, follow, report, share, calendar, search, stats, moderation]` (cf. décision 17) |
| Conventions AGENTS.md (camelCase, booléens sans `is`, openapi-first, doc dans le même commit) | [`AGENTS.md`](AGENTS.md) + [`backend/AGENTS.md`](backend/AGENTS.md) | **Strictement préservées** — la migration n'ouvre aucune dérogation aux conventions du projet |

### Pourquoi maintenant

- **Cours pinfo6 — exigence pédagogique S8.** Le brief Agon (cours « architectures distribuées ») impose la migration sur le sprint S8 (« Tests, scalabilité, sécurité, CD, soutenance », cf. [`sprint-context.md` lignes 357-365](backend/docs/sprint-context.md#L357-L365)). Le rendu de soutenance attend un système microservices fonctionnel.
- **Le code-base est mûr pour l'extraction.** À S7, les 9 entités sont stables, les services applicatifs ont des frontières claires (cf. tableau § 4.1), et la cascade SCRUM-136 (`isCreatorOrAcceptedCoOrganizerPublic`) a déjà institué la règle d'autorisation cross-service in-process — elle se transpose en check REST/JWT sans réécriture.
- **Aucune dépendance amont au sens strict** — pas de PR ouverte qui ajoute une entité ou modifie le contrat OpenAPI. Le V17 (récurrence, SCRUM-147) est mergé ; la PR concurrente attendue (V18) n'est pas encore visible sur `main`. La fenêtre est propre.
- **Débloque** la **scalabilité indépendante** par service (point S8 du backlog), l'**isolation** des incidents (un crash de `notification-service` ne tombe pas `event-service`), et le **build incrémental** (CI plus rapide, pipelines par service).
- **Cohérence avec le cours.** Kong + Kafka sont les choix imposés par le brief Agon (cf. point 2). Aucune marge de manœuvre sur ces deux briques.
- **Pas de réécriture transversale.** L'ossature en couches (Resource → Service → Entity) reste **inchangée** par service ; ce qui change est :
  - le **classpath** (1 module Maven par service, 1 image Docker par service) ;
  - le **datastore** (1 schéma logique par service, cf. décision 8) ;
  - les **appels cross-service** (REST sync via `quarkus-rest-client-reactive` OU async Kafka selon la nature du flux, cf. décision 11) ;
  - le **point d'entrée HTTP** (Kong, cf. décision 6).

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Branche `refactor(backend): migrate-to-microservices`

**Décision.** La branche s'appelle exactement `refactor(backend): migrate-to-microservices` — nom littéral fourni par le brief Agon (cf. point 4). Pas d'alias backlog `feature/s8-microservices` (le cours impose la convention).

**Justification.** Brief Agon explicite. La règle racine [`AGENTS.md` ligne 117](AGENTS.md#L117) autorise le format `feature/SCRUM-XX-description` mais le projet retient l'alias précisé par le mandataire quand il existe. Ici, le mandataire est le cours, et il fixe le nom de branche. Cohérent avec le titre PR final (cf. § « Livrable FINAL attendu »).

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) `refactor(backend): migrate-to-microservices` | Strictement aligné avec le brief Agon ; cohérent avec le titre PR `refactor(<scope>): <description>` validé par `pr-title-check.yml` | Décale du préfixe `feature/s<N>-...` retenu par les specs S6/S7 | ✅ retenu |
| (b) `feature/s8-microservices` | Cohérent avec specs SCRUM-138/139/147 | Inconsistant avec le brief Agon ; perdrait la traçabilité réviseur ↔ cours | ❌ |
| (c) `refactor/SCRUM-XXX-microservices` | Strict respect de la convention AGENTS.md `feature/SCRUM-XX-...` | Inconsistant avec le titre PR validé par CI (le validateur de titre attend exactement le format `<type>(<scope>): <description>`) | ❌ |

### 2. API Gateway → **Kong**

**Décision.** Kong (Open Source, sans Konnect) est l'API Gateway unique. Tout le trafic entrant `/api/*` passe par Kong **avant** d'atteindre l'un des microservices. L'Ingress Nginx pointe désormais sur `Service kong-proxy` (port 8000) au lieu de `Service api`.

**Justification.** Brief Agon explicite (point 2 : *« Utiliser Kong (API Gateway) »*). Aucune marge de manœuvre.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Kong (Open Source)** | Imposé par le brief ; mature, écosystème de plugins (rate-limit, oidc, cors, prometheus) ; déclaratif via DB-less mode (config YAML) | Déploiement Helm supplémentaire ; courbe d'apprentissage Kong | ✅ retenu (imposé) |
| (b) Spring Cloud Gateway | Bien intégré écosystème JVM | **Hors brief** — non retenu | ❌ |
| (c) Traefik | Léger, intégration K8s native | **Hors brief** | ❌ |
| (d) NGINX Ingress + plugins (`auth_request`, `limit_req`) | Pas de nouvelle brique | **Hors brief** ; plugins NGINX limités vs Kong | ❌ |
| (e) Envoy / Istio | Service mesh complet | **Hors brief** ; sur-ingénierie pour un cours | ❌ |

**Mode de déploiement.** Kong en mode **DB-less** (config statique YAML montée en `ConfigMap`) — pas de PostgreSQL Kong dédié. Cohérent avec un cours et le coût infra : pas de StatefulSet Kong, pas de migration interne Kong à gérer.

### 3. Message broker → **Kafka**

**Décision.** Apache Kafka est le broker de messages unique. Producteurs et consommateurs utilisent `quarkus-smallrye-reactive-messaging-kafka` (extension officielle Quarkus). Topics nommés en `domain.event-type` (cf. tableau § 4.5).

**Justification.** Brief Agon explicite (point 2 : *« ainsi que Kafka (message broker) »*). Aucune marge de manœuvre.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Kafka (Apache)** | Imposé par le brief ; durabilité, partitionnement, replay, écosystème mature | Déploiement Helm supplémentaire (StatefulSet + Zookeeper ou KRaft) | ✅ retenu (imposé) |
| (b) RabbitMQ | Léger, AMQP, queues classiques | **Hors brief** | ❌ |
| (c) NATS / NATS JetStream | Très léger, cloud-native | **Hors brief** | ❌ |
| (d) Redis Streams | Réutilise Redis si déjà présent | **Hors brief** ; pas de Redis dans le projet | ❌ |
| (e) PostgreSQL `LISTEN/NOTIFY` | Aucune brique supplémentaire | **Hors brief** ; pas de durabilité, pas de replay, ne scale pas | ❌ |

**Mode de déploiement.** Kafka en **KRaft** (sans Zookeeper) via la chart officielle Bitnami ou une StatefulSet maison à 1 broker (le cours n'exige pas de cluster HA). Cohérent avec le coût infra du cours (1 broker single-node, replication-factor 1, suffisant pour la soutenance).

### 4. Découpage en services — 14 services métiers

**Décision.** Le monolithe est découpé en **14 microservices** (10 services métiers d'événements, 1 service utilisateurs, 3 services transversaux). Chaque service possède son périmètre (entités, resources, schedulers), expose les paths OpenAPI qui lui reviennent, et peut être déployé/scalé indépendamment.

| Service | Entités possédées | Resources publiées | Schedulers | Path OpenAPI principaux |
|---|---|---|---|---|
| `user-service` | `User` | `UserResource` | — | `/users/me`, `/users/me/image`, `/users/me/banner`, `/users/{id}` |
| `event-service` | `Event` (+ enums `EventStatus`, `EventCategory`, `Faculty`, `RecurrenceFrequency`) | `EventResource`, `AdminEventResource`, `EventSearchResource`, `FeaturedService`-resources | `EventExpirationJob` | `/events`, `/events/{id}`, `/events/{id}/cancel`, `/events/{id}/restore`, `/events/{id}/publish`, `/events/{id}/image`, `/events/{id}/occurrences`, `/events/featured`, `/admin/events/{id}/feature`, `/admin/events/{id}/unfeature`, `/events/search` |
| `attendance-service` | `Attendance` (+ enum `AttendanceStatus`) | `AttendanceResource` | — | `/events/{id}/attend`, `/events/{id}/attendees`, `/users/me/attendances`, `/users/me/participations` |
| `favorite-service` | `Favorite` | `FavoriteResource` | — | `/events/{id}/favorite`, `/users/me/favorites` |
| `view-service` | `EventView` | `EventViewResource` | — | `/events/{id}/view` |
| `co-organizer-service` | `EventCoOrganizer` (+ enum `CoOrganizerStatus`) | `EventCoOrganizerResource` | — | `/events/{id}/co-organizers`, `/events/{id}/co-organizers/{userId}`, `/events/{id}/co-organizers/me/accept`, `/events/{id}/co-organizers/me/decline`, `/users/me/co-organizer-invitations` |
| `comment-service` | `Comment` | `CommentResource`, `CommentDirectResource` | — | `/events/{eventId}/comments`, `/comments/{commentId}` |
| `follow-service` | `Follow` (+ enum `FollowStatus`) | `FollowResource`, `FollowRequestResource` | — | `/users/{id}/follow`, `/users/{id}/followers`, `/users/{id}/following`, `/users/me/follow-requests`, `/follow-requests/{followId}/accept`, `/follow-requests/{followId}/reject` |
| `report-service` | `Report` (+ enums `ReportReason`, `ReportStatus`) | `ReportResource`, `AdminReportResource` | `ModerationCleanupJob` | `/events/{id}/report`, `/admin/reports`, `/admin/reports/{id}` |
| `stats-service` | — (lecture seule, projection sur les events de l'organisateur) | `EventStatsResource` | — | `/events/{id}/stats` |
| `share-service` | — (champs `shareCode` portés par `Event` mais résolution centralisée) | `RedirectResource` (`/s/{shortCode}`), endpoint `/events/{id}/share` | — | `/events/{id}/share`, `/s/{shortCode}` |
| `calendar-service` | — (token porté par `User.calendarToken` via `user-service`, génération ICS centralisée) | `CalendarResource` | — | `/users/me/calendar-token`, `/users/me/calendar-token/regenerate`, `/calendar/{calendarToken}.ics` |
| `notification-service` | (futur — `Notification` SCRUM-99 hors scope migration) | `/notifications`, `/notifications/{id}/read` (planifiés) | — | `/notifications`, `/notifications/{id}/read` |
| `me-aggregator-service` | — (BFF qui agrège `/users/me/events`, `/users/me/attendances`, `/users/me/favorites`, `/users/me/participations` en un seul endpoint si nécessaire) | resources d'agrégation `/users/me/events` (filtre `organizerId`), `/users/me/attendances` (proxy), `/users/me/favorites` (proxy) | — | `/users/me/events`, `/users/me/attendances`, `/users/me/favorites`, `/users/me/participations` |

**Justification.**

- Le découpage suit strictement les **frontières de domaine** déjà tracées dans `backend/src/main/java/ch/unige/events/entity/` — chaque entité racine devient (sauf agrégations) une racine de service. Cohérent avec le pattern « Aggregate Root » du DDD.
- **`stats-service`, `share-service`, `calendar-service`** sont des services **lecteurs** (pas d'entité racine propre) qui agrègent/projettent depuis les services voisins via REST sync ou Kafka. Justifié parce qu'ils ont chacun une cohérence opérationnelle propre (cache stats, redirect cache, génération ICS) qui mérite une isolation processus.
- **`me-aggregator-service` (BFF)** est nécessaire parce que les paths `/users/me/events`, `/users/me/attendances`, `/users/me/favorites`, `/users/me/participations` sont **multi-domaines** (toucent à `event-service`, `attendance-service`, `favorite-service` simultanément). Plutôt que de demander au frontend de les composer (le contrat OpenAPI l'interdit — cf. décision 24), un BFF unique les agrège. Pattern « Backend For Frontend ».
- **`notification-service`** est listé pour la complétude architecturale mais reste **hors scope SCRUM-99** ; il sera implémenté quand l'entité `Notification` sera livrée. Sa présence dans le diagramme garantit la place future du consommateur Kafka `notifications.events`.

| Option (granularité) | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **14 services par domaine** | Une entité racine = un service ; bounded contexts clairs ; scaling indépendant | Ops complexité (14 deployments) ; latence cross-service sur `/events/{id}` qui touche 4-5 services | ✅ retenu |
| (b) Macro-services (4 services : `user`, `event-core`, `social`, `admin`) | Moins d'overhead ops | Garde un couplage fort intra-macro ; pas le but pédagogique du cours | ❌ |
| (c) Nano-services (1 service par resource, ~17 services) | Granularité maximale | Sur-ingénierie ; pas de bénéfice pour 17 endpoints liés (`EventResource` + `AdminEventResource` partagent le même domaine) | ❌ |

### 5. Propriété des entités — strict, pas de double propriété

**Décision.** Chaque entité a **un et un seul** service propriétaire (cf. tableau décision 4). Aucune entité n'est dupliquée entre services. Les services consommateurs lisent les données du service propriétaire via REST sync (pour les requêtes utilisateur) ou via projection Kafka (pour les agrégats/statistiques).

**Justification.** Single source of truth = pas de drift de données. Pattern DDD orthodoxe.

**Cas particuliers explicités** :

| Cas | Décision |
|---|---|
| Le champ `Event.creator` (FK vers `User`) | `event-service` stocke uniquement `creatorAuth0Id: String` (et/ou `creatorId: UUID`) — **pas de FK SQL cross-service**. La résolution `creatorId → User` se fait via `user-service` GET `/users/{id}` (sync) au moment de la projection DTO côté `event-service`. Cohérent avec le pattern « pointeur Long brut » déjà adopté pour Favorite/Attendance/Follow (cf. [`specs_scrum-147.md` décision 4](specs_archives/specs_claude/specs_scrum-147.md)). |
| Le champ `Comment.author` (`@ManyToOne User`) | `comment-service` stocke `authorId: UUID` brut (au lieu d'un `@ManyToOne`). La résolution `authorId → displayName, avatarUrl` se fait via REST sync `user-service` au moment de la projection DTO. **Régression contrôlée** (cf. décision 12) : un service `comment-service` ne dispose plus de la navigation `comment.author.displayName` directe ; il appelle `user-service` ou consomme une projection cache locale. |
| `EventCoOrganizer.eventId` + `userId` | `co-organizer-service` stocke les UUIDs/Long bruts (pattern déjà en place côté monolithe). Aucune FK SQL cross-service. La cascade d'autorisation `isCreatorOrAcceptedCoOrganizer` se fait via REST sync `event-service` GET `/events/{id}` (qui retourne `creatorId`) puis `co-organizer-service` lookup local (cf. décision 12). |
| `Report.event` + `reporter` + `reviewedBy` (`@ManyToOne`) | `report-service` stocke `eventId: Long`, `reporterId: UUID`, `reviewedById: UUID` bruts. Les résolutions se font via REST sync au moment de la projection DTO. |
| `Comment.parentComment` (auto-référence intra-service) | **Reste un `@ManyToOne(LAZY) Comment`** — auto-référence intra-`comment-service`, pas cross-service. Pattern OK. |
| `Event.parentEventId` (auto-référence intra-service, SCRUM-147) | **Reste un `Long parentEventId`** brut — auto-référence intra-`event-service`. FK `fk_events_parent ON DELETE SET NULL` préservée. |

### 6. Routage Kong — table de routes 1:1 avec OpenAPI

**Décision.** Kong reçoit tout le trafic `/api/*` et applique une table de routes statique (DB-less, ConfigMap YAML) qui mappe chaque path OpenAPI vers son service amont. Plugins Kong activés globalement : `cors`, `request-id`, `prometheus`, `correlation-id`. Plugin `rate-limiting` activé sélectivement (cf. § « Rate limiting » ci-dessous).

**Table de routes Kong (extrait — la liste complète vit dans `k8s/chart/templates/kong/kong-config.yaml`)** :

| Path OpenAPI | Méthode(s) | Service amont K8s | Plugin OIDC | Rate-limit Kong |
|---|---|---|---|---|
| `/api/users/me` | GET, PUT | `user-service` | ✅ | — |
| `/api/users/me/image`, `/api/users/me/banner` | POST, DELETE | `user-service` | ✅ | — |
| `/api/users/{id}` | GET | `user-service` | optional (anon allowed) | — |
| `/api/users/{id}/follow` | POST, DELETE | `follow-service` | ✅ | 30/min/user |
| `/api/users/{id}/followers`, `/api/users/{id}/following` | GET | `follow-service` | ✅ | — |
| `/api/follow-requests/{id}/accept`, `/reject` | PATCH | `follow-service` | ✅ | — |
| `/api/users/me/follow-requests` | GET | `follow-service` | ✅ | — |
| `/api/users/me/favorites` | GET | `me-aggregator-service` (proxy → favorite-service) | ✅ | — |
| `/api/users/me/calendar-token`, `/regenerate` | GET, POST | `calendar-service` | ✅ | — |
| `/api/calendar/{calendarToken}.ics` | GET | `calendar-service` | optional (token-as-auth) | — |
| `/api/users/me/attendances`, `/api/users/me/participations` | GET | `me-aggregator-service` (proxy → attendance-service) | ✅ | — |
| `/api/users/me/events` | GET | `me-aggregator-service` (proxy → event-service) | ✅ | — |
| `/api/users/me/co-organizer-invitations` | GET | `co-organizer-service` | ✅ | — |
| `/api/events`, `/api/events/{id}` | GET, POST, PUT, DELETE | `event-service` | ✅ (PUT/POST/DELETE) ; optional (GET) | 10/min/user (POST) |
| `/api/events/{id}/cancel`, `/restore`, `/publish`, `/image` | PATCH, POST | `event-service` | ✅ | — |
| `/api/events/{id}/occurrences` | GET | `event-service` | optional | — |
| `/api/events/featured`, `/api/admin/events/{id}/feature`, `/unfeature` | GET, PATCH | `event-service` | ✅ (admin paths) | — |
| `/api/events/search` | GET | `event-service` | optional | — |
| `/api/events/{id}/attend`, `/attendees` | POST, DELETE, GET | `attendance-service` | ✅ | — |
| `/api/events/{id}/co-organizers`, `/co-organizers/{userId}`, `/me/accept`, `/me/decline` | POST, GET, DELETE, PATCH | `co-organizer-service` | ✅ | — |
| `/api/events/{id}/favorite` | POST, DELETE | `favorite-service` | ✅ | — |
| `/api/events/{id}/view` | POST | `view-service` | ✅ | — |
| `/api/events/{id}/share` | GET | `share-service` | ✅ | — |
| `/api/s/{shortCode}` | GET | `share-service` | optional | — |
| `/api/events/{id}/stats` | GET | `stats-service` | ✅ | — |
| `/api/events/{id}/report` | POST | `report-service` | ✅ | — |
| `/api/admin/reports`, `/api/admin/reports/{id}` | GET, PATCH | `report-service` | ✅ (ADMIN role required) | — |
| `/api/events/{eventId}/comments` | POST, GET | `comment-service` | ✅ (POST) ; optional (GET) | 10/min/user (POST) |
| `/api/comments/{commentId}` | DELETE | `comment-service` | ✅ | — |
| `/api/notifications`, `/api/notifications/{id}/read` | GET, PUT | `notification-service` (futur) | ✅ | — |

**Plugins Kong activés (config DB-less)** :

| Plugin | Scope | Config |
|---|---|---|
| `cors` | global | `origins: [https://pinfo6.p-info.net]`, methods, credentials |
| `correlation-id` | global | header `X-Request-ID` (préserve l'existant — cf. `RequestIdFilter` du monolithe) |
| `prometheus` | global | métriques sur `/metrics` |
| `rate-limiting` | par-route | `events.create` 10/min, `comments.post` 10/min, `follows.follow` 30/min — ces buckets **dupliquent** ceux du monolithe (cf. décision 21) |
| `oidc` (lua-resty-openidc) **OU** `jwt` | par-route ou global | validation JWT Auth0 (cf. décision 7) |

**Justification.** Routage déclaratif statique (DB-less) = pas d'admin API exposée, pas de risque de drift de config, versioning git de la table de routes (cf. `k8s/chart/templates/kong/kong-config.yaml`). Conforme au cours.

### 7. Auth Auth0 / OIDC — `quarkus-oidc` activé sur **chaque** service, Kong ne propage que le token

**Décision.** Chaque microservice **conserve** son extension `quarkus-oidc` en mode `service`, exactement comme le monolithe aujourd'hui. Le token JWT Auth0 traverse Kong **inchangé** dans le header `Authorization: Bearer <jwt>`. Chaque service valide le token localement via OIDC Discovery Auth0 (signature + expiration + audience). La claim `https://quarkus-security.com/roles` reste lue par chaque service via `quarkus.oidc.roles.role-claim-path`.

**Justification.**

- **Brief Agon point 1 contrainte 2** : *« L'auth Auth0/OIDC doit fonctionner sur chaque service. »* Validation locale par chaque service = chaque service est self-contained (peut être déployé/testé isolément) ET tolérant aux pannes Kong (un crash Kong ne bloque pas la validation auth — bien que le trafic ne passe plus du tout, donc point limité).
- **`@RolesAllowed("ADMIN")` continue de fonctionner** sur chaque service sans changement (cf. [`AdminEventResource.java:14`](backend/src/main/java/ch/unige/events/resource/AdminEventResource.java#L14), [`AdminReportResource.java:30`](backend/src/main/java/ch/unige/events/resource/AdminReportResource.java#L30)). Aucune réécriture de code Java.
- **`%test` mode `quarkus.oidc.enabled=false`** préservé byte-pour-byte sur chaque service (cf. [`backend/src/test/resources/application.properties`](backend/src/test/resources/application.properties)). `@TestSecurity(user="auth0|alice", roles={"ADMIN"})` continue de fonctionner identiquement.
- **`GET /users/me` retourne 401 si token absent/invalide** — règle critique préservée par `user-service` (la logique anti-401-leak vit déjà dans `UserResource`, cf. [`backend/AGENTS.md` ligne 84](backend/AGENTS.md#L84)).

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **`quarkus-oidc` sur chaque service, Kong forwarde le JWT brut** | Pas de réécriture de code Java ; chaque service self-contained ; compatible avec `@TestSecurity` | Chaque service paie le coût de validation JWT (signature + JWKS) — mitigé par le cache JWKS quarkus-oidc | ✅ retenu |
| (b) Kong valide le JWT (plugin `oidc` lua-resty-openidc) puis forward des claims signés (X-User-Id, X-User-Roles) en headers | Validation centralisée ; services backend allégés | Force de réécrire toute la chaîne `@Authenticated` / `@RolesAllowed` / `SecurityIdentity.getPrincipal()` ; perd `@TestSecurity` ; introduit un secret partagé Kong↔services pour signer les headers ; **non-conforme** au brief (qui exige Auth0 « sur chaque service ») | ❌ |
| (c) Kong valide le JWT + chaque service revalide aussi (double validation) | Defense-in-depth | Sur-ingénierie sans bénéfice (la validation locale est déjà robuste) | ❌ |
| (d) Kong opaque token + introspection → auth aux services par mTLS interne | Sécurité interne forte | Hors scope cours ; nécessite PKI interne | ❌ |

**Note Kong plugin OIDC.** Kong peut activer **optionnellement** un plugin `jwt` (validation rapide pre-routing) qui rejette les tokens malformés AVANT de les forwarder. Cela évite que chaque service paie la validation pour des tokens triviaux invalides. **Cette optimisation est OK** mais pas requise — chaque service revalide derrière. Décision : on commence sans, on ajoute si la latence devient un problème.

### 8. Base de données — **schéma par service** dans une instance PostgreSQL **partagée**

**Décision.** Chaque microservice possède son **propre schéma PostgreSQL** (`user_svc`, `event_svc`, `attendance_svc`, etc.) dans la **même instance** `db` (StatefulSet `postgres:16` existant). Pas de bases physiquement séparées (1 instance, N schémas). Chaque service déclare `quarkus.datasource.jdbc.url=jdbc:postgresql://db:5432/unige_events?currentSchema=event_svc` (par exemple), avec un **rôle DB dédié** par service ayant les privilèges minimaux sur son schéma uniquement.

**Justification.** Deux extrémités du spectre database-per-service à arbitrer :

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **1 instance PostgreSQL, N schémas, N rôles** | Aucune duplication infra ; coût ops minimal pour un cours ; séparation logique stricte (un service ne peut pas lire le schéma d'un autre via SQL — RBAC PostgreSQL) ; pgdump/pgrestore par service | Single point of failure (1 panne DB tombe tous les services) ; pas d'isolation I/O au niveau du moteur | ✅ retenu |
| (b) N instances PostgreSQL (1 StatefulSet par service) | Isolation forte ; failure domain réduit | Coût ops élevé (N PVC, N postgres) ; sur-ingénierie pour un cours | ❌ |
| (c) Une seule base partagée + 1 schéma `public` partagé | Pas de migration de données | **Casse le bounded context** (chaque service voit les tables des autres) ; pas conforme à la définition microservices | ❌ |
| (d) MongoDB / Cassandra par service | NoSQL natif distribué | **Hors brief** ; force la réécriture de toutes les entités JPA | ❌ |

L'option (a) — **schéma-par-service dans une instance partagée** — est le compromis pragmatique : préserve les propriétés de bounded context (chaque service ne peut SQL que son schéma via RBAC), évite le coût ops de N instances, et permet de migrer les données existantes en pur SQL (`CREATE SCHEMA event_svc; ALTER TABLE events SET SCHEMA event_svc; ...` — cf. décision 9 et plan migration § 4.3).

**Rôles DB par service** :

```sql
CREATE ROLE event_svc_user WITH LOGIN PASSWORD '<from-doppler>';
CREATE SCHEMA event_svc AUTHORIZATION event_svc_user;
GRANT USAGE ON SCHEMA event_svc TO event_svc_user;
GRANT ALL ON ALL TABLES IN SCHEMA event_svc TO event_svc_user;
-- Pas de GRANT sur les autres schémas — isolation stricte.
```

(Idem pour `user_svc_user`, `attendance_svc_user`, etc. — cf. § 4.3 plan d'extraction.)

**FK cross-service** : impossibles dans cette configuration (une FK PostgreSQL n'enjambe pas un schéma si le rôle n'a pas de SELECT sur l'autre schéma — et même si elle pouvait, ce serait un anti-pattern microservices). Les FK actuelles `Event.creator → User`, `Comment.event → Event`, etc. **deviennent des pointeurs Long/UUID bruts** sans contrainte SQL (cf. décision 5). La cohérence référentielle est garantie par les services (refus 404 si l'ID n'existe pas côté service propriétaire) et par les jobs de cleanup (orphan-row reaper, futur S9+).

### 9. Migrations Flyway — **par service**, numérotation V1 + import du historique du monolithe

**Décision.** Chaque service possède son propre dossier `src/main/resources/db/migration/V<N>__*.sql` avec numérotation **redémarrée à V1**. Au moment de l'extraction d'un service, sa migration V1 est un script `extract.sql` qui :

1. Crée le schéma `<service>_svc` si absent (idempotent).
2. Déplace les tables possédées vers ce schéma : `ALTER TABLE events SET SCHEMA event_svc;` (cf. tableau ci-dessous).
3. Crée les rôles + grants (cf. décision 8).
4. Insère dans `<service>_svc.flyway_schema_history` les checksums **falsifiés** (`success = TRUE`, `description = 'baseline'`) pour V1..V17 historiques afin que les services qui ont besoin d'invariants V1..V17 (`event-service` qui hérite des contraintes CHECK posées par V1) ne re-exécutent pas ces migrations sur le schéma déplacé.

**Distribution des migrations historiques V1..V17** :

| Migration historique | Tables touchées | Service propriétaire |
|---|---|---|
| `V1__reconcile_check_constraints.sql` | `events`, `attendances` (CHECK constraints) | Scindée : la part `events` reste avec `event-service` ; la part `attendances` migre vers `attendance-service` |
| `V2__create_events.sql` | `events` | `event-service` |
| `V3__create_attendances.sql` | `attendances` | `attendance-service` |
| `V4__create_favorites.sql` | `favorites` | `favorite-service` |
| `V5__create_event_views.sql` | `event_views` | `view-service` |
| `V6__create_reports.sql` | `reports` | `report-service` |
| `V7__reconcile_check_constraints.sql` | `reports` (CHECK status) | `report-service` |
| `V8__create_event_co_organizers.sql` | `event_co_organizers` | `co-organizer-service` |
| `V9__widen_event_description.sql` | `events.description` (TEXT) | `event-service` |
| `V10__add_report_reason_and_review_fields.sql` | `reports` (colonnes SCRUM-94) | `report-service` |
| `V11__allow_event_status_expired.sql` | `events.status` CHECK | `event-service` |
| `V12__add_featured_to_events.sql` | `events.featured` | `event-service` |
| `V13__allow_event_status_banned.sql` | `events.status` CHECK | `event-service` |
| `V14__create_follows.sql` | `follows` | `follow-service` |
| `V15__create_comments.sql` | `comments` | `comment-service` |
| `V16__alter_comments_parent_fk_set_null.sql` | `comments.parent_comment_id` FK | `comment-service` |
| `V17__add_event_recurrence.sql` | `events.parent_event_id`, `recurrence_rule` | `event-service` |
| (V0 — `users` créé par Hibernate `update` historique pré-Flyway) | `users` | `user-service` (création explicite dans son V1) |

**Règle d'or préservée** : une migration committée est **immutable** (cf. [`backend/AGENTS.md` lignes 54-57](backend/AGENTS.md#L54-L57)). Le « pseudo-baseline » introduit ici (faux-checksums dans `flyway_schema_history`) **ne modifie pas** le contenu des fichiers V1..V17 — il marque simplement Flyway comme « j'ai déjà exécuté ces migrations sur ce schéma ». Ce pattern est documenté Flyway sous le nom de [`baseline`](https://flywaydb.org/documentation/concepts/baselinemigrations) et activé par `quarkus.flyway.baseline-on-migrate=true` (déjà présent — cf. [`application.properties` ligne 21](backend/src/main/resources/application.properties#L21)).

**Numérotation V1 par service** :

```
backend/services/user-service/src/main/resources/db/migration/V1__create_users.sql
backend/services/event-service/src/main/resources/db/migration/V1__extract_events_schema.sql
backend/services/event-service/src/main/resources/db/migration/V2__add_event_recurrence.sql   (si on choisit de re-poser V17 monolithe sur le schéma déplacé)
backend/services/attendance-service/src/main/resources/db/migration/V1__extract_attendances_schema.sql
... (idem pour chaque service)
```

**Justification.** Préserve la propriété de bounded context (chaque service est responsable de l'évolution de son schéma) tout en respectant l'invariant Flyway-immutable. Pas de `quarkus.flyway.clean-*` (interdit par AGENTS.md).

### 10. Schedulers — réaffectés, leader-elected via Kafka topic

**Décision.** Les 2 schedulers existants migrent dans leur service propriétaire :

| Scheduler | Service propriétaire | Cron | Idempotence |
|---|---|---|---|
| `EventExpirationJob` (ex `1h`) | `event-service` | `@Scheduled(every = "1h")` | Idempotent par construction (query `WHERE endDate < NOW() AND status != 'EXPIRED'` puis UPDATE — re-run safe) |
| `ModerationCleanupJob` (ex `cron 0 0 3 * * ?`) | `report-service` | `@Scheduled(cron = "0 0 3 * * ?", timeZone = "Europe/Zurich")` | Idempotent (cf. [`backend/docs/architecture.md` lignes 184-204](backend/docs/architecture.md#L184-L204)) ; émet un Kafka event `events.banned` quand un event passe BANNED, consommé par `event-service` pour appliquer le state change cross-service |

**Multi-instance.** Si un service tourne en `replicas: > 1` (futur scaling), le scheduler doit s'exécuter **une seule fois** par firing — pas N fois. Quarkus n'offre pas de leader-election native. Approche retenue :

- **Variante simple (S8 — 1 replica par service par défaut)** : `replicas: 1` sur les Deployments qui hébergent un scheduler (`event-service`, `report-service`). Pas de leader-election. Trade-off accepté (pas de HA pour les jobs de fond).
- **Variante avancée (S9+ si besoin)** : utiliser `quarkus-scheduler-extension-coordinator` ou implémenter un advisory lock PostgreSQL au début du job (`SELECT pg_try_advisory_lock(<jobid>)`). **Hors scope** migration.

**Cross-service via Kafka.** `ModerationCleanupJob` ne peut plus écrire directement dans `events.status = BANNED` (table dans un autre schéma, pas d'accès SQL). Solution : le job émet un event Kafka `event.banned` (topic `events.moderation`, payload `{ eventId, bannedAt, reason }`) consommé par `event-service` qui applique le `UPDATE events SET status='BANNED' WHERE id = ?`. Pattern « Saga / Orchestration externe » (cf. décision 11).

### 11. Communication inter-services — **REST sync** pour les requêtes utilisateur, **Kafka async** pour les fan-outs

**Décision.** Règle simple :

| Type d'appel | Mode | Justification |
|---|---|---|
| Service A appelle service B en réponse à une requête utilisateur HTTP | **REST sync** via `quarkus-rest-client-reactive` | Latence directe ; le service consommateur a besoin de la réponse pour répondre lui-même |
| Service A émet un event consommé par 1+N services en arrière-plan | **Kafka async** | Découplage temporel ; consommateurs peuvent être ralentis sans bloquer A ; replay possible |
| Projection / Materialized view (`stats-service` lit l'historique des views) | **Kafka async** + cache local | Stats n'ont pas besoin de fraîcheur strictement temps-réel (acceptable lag de quelques secondes) |
| Cascade d'autorisation cross-service (ex. `comment-service` doit savoir si l'event est PUBLISHED) | **REST sync** vers `event-service` GET `/events/{id}` | Décision d'autorisation = strict synchrone, ne tolère pas le lag |

**Topics Kafka projetés (table complète § 4.5 plus bas).** Producteurs annotés `@Outgoing("events-published-out")`, consommateurs `@Incoming("events-published-in")` via `quarkus-smallrye-reactive-messaging-kafka`.

**REST clients.** Chaque service consommateur déclare une interface JAX-RS annotée `@RegisterRestClient` pour chaque service amont qu'il appelle. Configuration centrale via `application.properties` :

```properties
ch.unige.events.client.UserServiceClient/mp-rest/url=http://user-service:8080/api
ch.unige.events.client.EventServiceClient/mp-rest/url=http://event-service:8080/api
```

Les clients propagent automatiquement le `Authorization: Bearer <jwt>` du caller via `@ClientHeaderParam` ou via un `ClientRequestFilter` qui lit `JsonWebToken` injecté.

**Resilience.** `quarkus-smallrye-fault-tolerance` activé sur tous les REST clients :
- `@Retry(maxRetries = 2, delay = 100, unit = ChronoUnit.MILLIS)` sur les appels de lecture cross-service.
- `@Timeout(value = 2, unit = ChronoUnit.SECONDS)` sur tous les REST clients.
- `@CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10, delay = 5000)` sur les appels critiques.
- `@Fallback(fallbackMethod = "fallbackUserLookup")` quand un fallback simple est sûr (ex. afficher un displayName fallback "Utilisateur supprimé" si `user-service` 404).

### 12. Cascade d'autorisation cross-service — REST sync, pas Kafka

**Décision.** Les checks d'autorisation qui traversent les services (ex. `comment-service.delete()` autorise « auteur du commentaire OU créateur de l'event OU co-organisateur ACCEPTED OU ADMIN ») se font en **REST sync** :

```
DELETE /api/comments/{id}
  ↓ Kong → comment-service
  ↓ comment-service.delete() lit le comment localement
  ↓ Si caller != author : appelle event-service GET /events/{eventId}
  ↓                       (récupère creatorAuth0Id)
  ↓ Si caller != creator : appelle co-organizer-service GET /events/{eventId}/co-organizers (filtré ACCEPTED)
  ↓ Si caller pas dans la liste : appelle identity.hasRole("ADMIN") localement
  ↓ Sinon → 403
```

**Justification.** L'autorisation = synchrone par nature. Un délai même de 50ms est acceptable ; un lag Kafka de quelques secondes ne l'est pas. L'alternative (cache local des relations co-org dans chaque service consommateur) introduit du drift et un risque d'autorisation incorrecte — inacceptable.

**Optimisation future.** `comment-service` pourrait cacher localement les `(eventId, creatorAuth0Id)` (TTL 60s) pour éviter d'appeler `event-service` à chaque DELETE. **Hors scope migration**, optimisation S9+.

### 13. `EventService.isCreatorOrAcceptedCoOrganizer` — devient un appel REST `co-organizer-service.isAccepted(eventId, userId)`

**Décision.** La méthode helper [`EventService.isCreatorOrAcceptedCoOrganizerPublic`](backend/src/main/java/ch/unige/events/service/EventService.java#L436-L438) ne peut plus accéder à la table `event_co_organizers` (qui vit dans `co_organizer_svc`). Elle est remplacée par :

```java
// dans les services consommateurs (event-service, attendance-service, comment-service, stats-service)
@RegisterRestClient
public interface CoOrganizerServiceClient {
    @GET
    @Path("/api/events/{eventId}/co-organizers/check")
    @ClientHeaderParam(name = "Authorization", value = "{authHeader}")
    CoOrgCheckResponse isAcceptedCoOrganizer(@PathParam("eventId") Long eventId,
                                              @QueryParam("userId") UUID userId);
}
```

`co-organizer-service` expose un nouvel endpoint **interne** `GET /api/events/{eventId}/co-organizers/check?userId=<uuid>` qui renvoie `{ accepted: boolean }`. Cet endpoint est marqué dans une section « API interne » de l'OpenAPI (sous-tag `internal`, **non documenté côté frontend** — il n'est pas dans le contrat actuel et n'a pas à l'être ; les checks cross-service sont une pure préoccupation backend).

**Justification.** Préserve la cascade d'autorisation SCRUM-136 (cf. [`backend/docs/api-contract.md` lignes 283-299](backend/docs/api-contract.md#L283-L299)) sans casser la propriété de bounded context. Pattern « API privée interne » documenté chez Netflix (« back-channels API ») et chez de nombreux frameworks de microservices.

### 14. Notifications — topic Kafka `notifications.events`, consommateur futur

**Décision.** Tous les services qui auraient émis une notification (cf. backlog SCRUM-99/140/145) émettent un event Kafka sur le topic `notifications.events` avec un payload typé :

```json
{ "type": "NEW_FOLLOWER", "userId": "<uuid>", "metadata": { "followerId": "<uuid>" }, "timestamp": "..." }
{ "type": "NEW_COMMENT", "userId": "<uuid>", "metadata": { "eventId": 42, "commentId": 99 }, "timestamp": "..." }
{ "type": "EVENT_BANNED", "userId": "<uuid>", "metadata": { "eventId": 42 }, "timestamp": "..." }
```

`notification-service` (à livrer dans SCRUM-99 — **hors scope migration**) consomme ce topic, persiste les `Notification` rows et expose `GET /notifications`, `PUT /notifications/{id}/read`. **Pour la migration**, on **émet déjà** les events Kafka depuis `follow-service`, `comment-service`, `report-service`, `co-organizer-service` (no-ops aujourd'hui — pas de consumer) pour que SCRUM-99 puisse simplement brancher son consumer plus tard sans toucher aux producteurs.

**Justification.** Découplage temporel parfait. Permet à SCRUM-99 de venir « ajouter un consumer » sans toucher aux services qui produisent. Les events publiés dans le topic avant que SCRUM-99 soit livré sont **rejoués** au moment où le consumer démarre (Kafka conserve les events `retention.ms = 7 days` minimum, configuration recommandée).

### 15. Schémas Kafka — JSON ad hoc, pas Avro

**Décision.** Les payloads Kafka sont des objets JSON typés directement par les services (records Java sérialisés/désérialisés via Jackson). Pas de Schema Registry, pas d'Avro, pas de Protobuf.

**Justification.** Conforme au cours (simplicité). Le coût d'introduire un Schema Registry (Confluent Schema Registry, Apicurio) est démesuré pour 5-10 topics avec des payloads stables et un seul consommateur par topic à terme. Risque accepté : un changement de payload d'un producteur peut casser un consumer — mitigé par les contract tests Pact (cf. décision 18) et par la convention « ajouter des champs OK, retirer/renommer KO ».

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **JSON Jackson sans Schema Registry** | Simplicité ; pas de brique infra supplémentaire ; lisible humainement (kcat) | Pas de validation au build ; risque de breaking change non détecté | ✅ retenu |
| (b) Avro + Schema Registry (Confluent / Apicurio) | Validation forte ; évolution schémas managée | Brique infra supplémentaire ; complexité disproportionnée pour un cours | ❌ |
| (c) Protobuf + gRPC | Schémas typés ; code-gen | Hors brief ; force gRPC en plus de REST | ❌ |

### 16. Helm — **umbrella chart** étendu avec sous-charts par service

**Décision.** Le chart actuel `k8s/chart/` reste l'**umbrella**. Il acquiert des sous-templates par service, plus 2 sous-templates pour Kong et Kafka :

```
k8s/chart/
├── Chart.yaml                      (existant — version bumpée à 0.2.0)
├── values.yaml                     (existant — étendu avec sections par service)
├── values-preview.yaml             (existant — étendu)
└── templates/
    ├── api/                        (LEGACY — supprimé après migration complète)
    ├── web/                        (existant — inchangé)
    ├── db/                         (existant — préservé : 1 instance, schémas multiples)
    ├── minio/                      (existant — inchangé)
    ├── cloudflared/                (existant — inchangé)
    ├── ingress/                    (existant — modifié : `/api/*` route vers `kong-proxy` au lieu de `api`)
    ├── kong/                       (NOUVEAU)
    │   ├── deployment.yaml
    │   ├── service.yaml
    │   ├── configmap-routes.yaml   (table de routes DB-less)
    │   └── plugin-config.yaml
    ├── kafka/                      (NOUVEAU)
    │   ├── statefulset.yaml        (KRaft single-broker)
    │   ├── service.yaml
    │   └── topics-init.yaml        (Job qui crée les topics au premier déploiement)
    ├── user-service/               (NOUVEAU)
    │   ├── deployment.yaml
    │   └── service.yaml
    ├── event-service/              (NOUVEAU — idem)
    ├── attendance-service/         (NOUVEAU — idem)
    ├── favorite-service/           (NOUVEAU — idem)
    ├── view-service/               (NOUVEAU — idem)
    ├── co-organizer-service/       (NOUVEAU — idem)
    ├── comment-service/            (NOUVEAU — idem)
    ├── follow-service/             (NOUVEAU — idem)
    ├── report-service/             (NOUVEAU — idem)
    ├── stats-service/              (NOUVEAU — idem)
    ├── share-service/              (NOUVEAU — idem)
    ├── calendar-service/           (NOUVEAU — idem)
    ├── notification-service/       (PLACEHOLDER — replicas: 0 jusqu'à SCRUM-99)
    └── me-aggregator-service/      (NOUVEAU — idem)
```

**Justification.**

- **Umbrella** plutôt que charts indépendants par service = un seul `helm upgrade` redéploie tout cohérent. Conforme au pattern actuel et au pipeline [`deploy.yml` lignes 56-64](.github/workflows/deploy.yml#L56-L64). Coût pédagogique minimal (les étudiants connaissent déjà le chart).
- **`templates/api/` reste pendant la migration progressive** (cf. décision 22 stratégie de coexistence) puis est supprimé à la fin.
- **Préfixage des Services K8s**: chaque microservice a son `Service ClusterIP <service-name>` sur port 8080, accessible en interne via DNS K8s `<service-name>.<namespace>.svc.cluster.local:8080`.
- **NetworkPolicies** : *hors scope migration* (cours pas exigeant), à ajouter en S9+.
- **Secrets** : `app-secrets` (Doppler-injected, déjà existant) reste partagé entre tous les services pour les credentials Auth0 / DB / S3. Un secret par service serait sur-ingénierie. Le rôle DB est variabilisé par service via env var `DB_USER` / `DB_PASSWORD` injectés depuis Doppler avec naming `<SERVICE>_DB_USER` / `<SERVICE>_DB_PASSWORD`.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Umbrella chart unique avec sous-templates** | Conforme au chart existant ; un seul `helm upgrade` redéploie tout cohérent ; coût pédagogique minimal | Couplage des releases (un service en panne bloque le déploiement) — mitigé par `--wait=false` ou par déploiement par templates ciblés | ✅ retenu |
| (b) N charts indépendants (1 par service) + un meta-chart qui fait `dependencies` | Granularité fine ; deploy / rollback par service possible | Multiplie les artefacts Helm ; complexifie le pipeline ; sur-ingénierie cours | ❌ |
| (c) Charts indépendants sans meta-chart, déployés par CI dans l'ordre | Granularité maximale | Le brief Agon impose un déploiement unifié à la soutenance | ❌ |

### 17. CI/CD — **matrice par service**, image-tag par SHA, build incrémental opt-in

**Décision.** Le job `build-backend` dans [`.github/workflows/build.yml`](.github/workflows/build.yml) est refondé en **stratégie matrix** :

```yaml
build-backend:
  strategy:
    matrix:
      service:
        - user-service
        - event-service
        - attendance-service
        - favorite-service
        - view-service
        - co-organizer-service
        - comment-service
        - follow-service
        - report-service
        - stats-service
        - share-service
        - calendar-service
        - me-aggregator-service
        # notification-service: skipped tant que pas implémenté (futur)
  steps:
    - uses: actions/checkout@v6
      with: { fetch-depth: 0 }
    - uses: actions/setup-java@v5
      with: { java-version: 21, distribution: temurin, cache: maven }
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
      run: ./mvnw sonar:sonar -B -Dsonar.projectKey=unige-events-backend-${{ matrix.service }}
```

**Justification.**

- **Build par service** = parallélisation native GitHub Actions ; chaque service builde indépendamment ; CI 13× plus rapide en wallclock (limité par le service le plus long).
- **Image tag = `${{ github.sha }}`** (déjà la convention existante, cf. [`build.yml` ligne 13](.github/workflows/build.yml#L13)) — tag réutilisé identique sur toutes les images du même commit. Rend le rollback trivial (revert SHA → revert toutes images).
- **SonarCloud par service** : un projectKey distinct par service (`unige-events-backend-user-service`, etc.). Chaque service vise ≥ 80 % couverture **sur son propre code** (pas d'agrégation cross-service nécessaire). Le projectKey monolithe `unige-events-backend` est **archivé** (figé à sa dernière analyse pré-migration).
- **Build incrémental opt-in** : le brief Agon n'exige pas le build incrémental. La matrice complète tourne sur chaque PR (~30 min wallclock estimé pour 14 services en parallèle). Si la latence devient un problème, ajouter `paths-filter` GitHub Action pour ne builder que les services dont le code/pom a changé. **Hors scope migration**, optimisation S9+.

**Pipeline `deploy.yml`.** Reste structuré identique (preview namespace par PR), mais le `helm upgrade` reçoit désormais un set d'`--set image.<service>.tag=<sha>` pour chaque service :

```yaml
helm upgrade --install $NAMESPACE ./k8s/chart \
  --set image.userService.tag=$SHA \
  --set image.eventService.tag=$SHA \
  ...
  --set image.api.tag=$SHA   # LEGACY — durant la coexistence
```

### 18. Tests — unit per-service + integration per-service + **contract tests Pact** inter-services + 1 E2E « happy path »

**Décision.** Stratégie de tests à 4 niveaux :

| Niveau | Outil | Périmètre | Critère SonarCloud |
|---|---|---|---|
| **Unit** (par service) | JUnit 5 (existant) | Logique métier locale, validation DTO, helpers | ≥ 80 % couverture (par service) |
| **Integration** (par service) | `@QuarkusTest` + DevServices PostgreSQL + DevServices Kafka (Testcontainers) | Resource ↔ Service ↔ DB ↔ Kafka producer | inclus dans le 80 % |
| **Contract** (inter-services) | **Pact JVM** | `comment-service` ⇆ `event-service` (le consumer définit le contrat, le provider le vérifie) | Run en CI sur chaque PR du provider |
| **E2E** (happy path) | RestAssured + 1 namespace preview | 1 scénario : user crée un event → invite un co-org → co-org accepte → user inscrit → comment posté → ban admin | 1 test, lance manuellement avant soutenance |

**Justification du choix Pact.**

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Pact JVM (consumer-driven)** | Le consommateur définit le contrat ; le provider doit s'y conformer ; détection de breaking changes en CI provider ; pas besoin de stack E2E lourde | Courbe d'apprentissage Pact ; brokerless = pas de versioning central | ✅ retenu |
| (b) Spring Cloud Contract | Bien intégré écosystème Spring | Hors écosystème Quarkus | ❌ |
| (c) Tests E2E systématiques (RestAssured + N services up) | Confiance maximale | Lent (minutes par run) ; flaky (Kafka, DB shared) ; ne scale pas à 14 services | ❌ |
| (d) Pas de contract tests | Simple | Casse le couplage producteur/consommateur sans détection au build | ❌ |

**Brokerless Pact** : les pacts JSON générés par les consommateurs sont commités dans `backend/contract-tests/pacts/<consumer>-<provider>.json`. Le provider lit directement les fichiers pour les vérifier — pas besoin de Pact Broker (sur-ingénierie pour un cours).

**E2E unique** : 1 test smoke `E2EHappyPathTest.java` dans le repo racine (`backend/e2e/`) qui lance via `kubectl port-forward` ou directement contre le namespace preview de la PR. Pas de framework lourd (Cypress, Playwright) — pur RestAssured.

### 19. Observabilité — logs JSON structurés + traceId via Kong, métriques Prometheus

**Décision.** Trois piliers :

1. **Logs JSON** : chaque service utilise `quarkus-logging-json` (extension officielle). Format `application/json` sur stdout, capté par Kubernetes → fluent-bit (futur S9+). Pour la migration, suffisant d'avoir le JSON sur stdout — `kubectl logs <pod>` reste lisible.
2. **TraceId via header** : Kong génère un `X-Request-ID` (plugin `correlation-id` déjà mentionné décision 6) qui est forwardé à tous les services. Chaque service le lit via `RequestIdFilter` (déjà existant côté monolithe — cf. [`application.properties` ligne 67-69](backend/src/main/resources/application.properties#L67-L69)) et le pose dans MDC pour qu'il apparaisse sur chaque ligne de log. Les REST clients propagent automatiquement le header au prochain service via un `ClientRequestFilter` qui lit MDC. **Pas d'OpenTelemetry** dans cette migration (sur-ingénierie cours) — ajout S9+ si soutenance le demande.
3. **Métriques Prometheus** : `quarkus-micrometer-registry-prometheus` activé sur chaque service ; endpoint `/q/metrics` exposé en interne (pas via Kong public). Plugin Kong `prometheus` agrège les métriques d'edge.

**Justification.** Minimum viable pour un cours. Permet de **démontrer** à la soutenance : un crash dans `comment-service` → on suit le `requestId` dans les logs aggregés (`kubectl logs -l 'app in (comment-service,event-service,user-service)' | grep req=<id>`), on identifie le call chain. Pas de Jaeger / Tempo nécessaire pour un démonstrateur.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Logs JSON + X-Request-ID propagé + Prometheus** | Minimum viable ; pas de brique infra (Jaeger / Tempo / Loki) | Pas de UI native pour explorer les traces | ✅ retenu |
| (b) OpenTelemetry full stack (Tempo / Jaeger) | Traces distribuées propres | Brique infra (StatefulSet collector) ; sur-ingénierie cours | ❌ |
| (c) Logs ad-hoc sans corrélation | Aucune brique | Casse le debug cross-service | ❌ |

### 20. Stratégie de coexistence — **strangler fig pattern**, extraction service par service

**Décision.** La migration s'opère en N étapes (1 par service), chacune mergée individuellement sur `main` derrière la branche unique `refactor(backend): migrate-to-microservices`. À chaque étape :

1. Le **monolithe `api` reste UP** et continue de servir tous les paths qu'il sert aujourd'hui.
2. Un **nouveau microservice X** est livré (chart Helm sub-template, image Docker, code Java).
3. **Kong route les paths de X vers le nouveau service** ; les autres paths continuent de pointer sur le monolithe.
4. Les **données de X** sont déplacées du schéma `public` du monolithe vers `<x>_svc` (cf. décision 9).
5. Le code Java correspondant est **supprimé du monolithe** dans la même PR (sinon drift).

**Ordre d'extraction recommandé** (le moins couplé en premier) :

| Étape | Service extrait | Justification de l'ordre |
|---|---|---|
| 1 | `share-service` (read-only, pas de schedulers, peu de dépendances) | Smoke test des fondations Kong + Helm |
| 2 | `view-service` (write-only `EventView`, fan-out idempotent) | Smoke test du Kafka producer |
| 3 | `favorite-service` (CRUD simple) | Smoke test du REST client → `event-service` |
| 4 | `calendar-service` (read-only ICS) | Smoke test cross-service `user-service` + `event-service` + `favorite-service` + `attendance-service` |
| 5 | `follow-service` (relations user-user, peu de cross-service) | — |
| 6 | `comment-service` | Cascade SCRUM-136 cross-service via REST |
| 7 | `co-organizer-service` | Cascade d'autorisation centrale |
| 8 | `attendance-service` | — |
| 9 | `report-service` (avec `ModerationCleanupJob`) | Premier service avec scheduler ; valide le pattern |
| 10 | `stats-service` (lecture pure) | — |
| 11 | `me-aggregator-service` (BFF) | Une fois tous les services aval prêts |
| 12 | `user-service` | Cœur fonctionnel ; en dernier pour minimiser le risque |
| 13 | `event-service` (avec `EventExpirationJob`) | Le plus gros service, en dernier — tout le reste dépend de lui |

**Plan de rollback par étape** :

À chaque étape, le rollback consiste à :
1. Retirer la route Kong qui pointe sur le nouveau service (revert ConfigMap).
2. Re-router le path vers le monolithe via Kong (qui reste UP — invariant).
3. Restaurer (ou laisser en place) la table dans `public` du monolithe (`ALTER TABLE <x>_svc.<table> SET SCHEMA public;`).
4. Re-cherry-pick le code Java supprimé du monolithe lors de l'étape (commit revert).

**Justification.** Pattern « strangler fig » (Fowler). Risque atomique par étape ; chaque étape est mergeable individuellement avec rollback déterministe ; pas de big-bang qui rendrait la review impossible.

### 21. Rate limiting — **Kong global** + `@PerUserRateLimit` local préservé

**Décision.** Deux niveaux de rate-limiting coexistent :

| Niveau | Outil | Bucket | Justification |
|---|---|---|---|
| **Edge (Kong)** | plugin `rate-limiting` | par-IP, par-route, fenêtre 1 min | Anti-DOS basique au niveau gateway ; fait abstraction des services |
| **Application (`@PerUserRateLimit`)** | annotation Java existante | par-user (auth0Id), par-bucket-named, fenêtre custom | Anti-spam UX (10 events.create/min/user, 30 follows.follow/min/user, 10 comments.post/min/user — cf. [`backend/docs/api-contract.md` lignes 43-47](backend/docs/api-contract.md#L43-L47)) |

**Justification.** Kong protège l'infra, `@PerUserRateLimit` protège l'UX. Dupliquer les buckets `events.create`, `follows.follow`, `comments.post` côté Kong **n'est pas demandé** — l'invariant `[`PerUserRateLimit`](backend/src/main/java/ch/unige/events/config/PerUserRateLimit.java)` (par-user, fenêtre fine) est plus précis que ce que Kong sait faire (Kong fait par-IP, qui est cassé derrière un proxy NAT). On garde la règle par-user dans l'app.

### 22. Stratégie de migration — N PRs (1 par étape) sous une branche unique

**Décision.** Au lieu d'une PR géante de ~50 fichiers nouveaux par service × 14 services = ~700 fichiers, la migration se fait en **13 sous-PRs** (1 par étape, cf. décision 20). Toutes mergent dans la branche locale `refactor(backend): migrate-to-microservices` (une feature branch persistante au-dessus de `main`). Le **brief Agon** parle de « ouvrir une PR » au singulier — interprétation : **la PR finale qui matérialise le résultat fini de la migration** est ouverte une fois toutes les sous-étapes mergées dans la branche, contre `main`.

**Justification.**

- 700 fichiers en review = unreadable. Copilot bot timeout, reviewer humain abandonne. La PR finale resterait grosse (le diff étant cumulé) mais chaque sous-PR sur la branche reste petite et reviewable individuellement par le mainteneur.
- Cohérent avec le pattern « stacked PRs » de Graphite/Sapling — pas natif GitHub mais bien soutenu par `gh` CLI.
- Permet le rollback fin (revert de la sous-PR de l'étape 6 sans toucher aux étapes 1-5).

**Variante simple acceptée si l'équipe préfère.** PR unique massive, reviewée par sections (sections explicites dans la description). Le mandataire (Dany / Elie) tranche au moment de l'ouverture.

### 23. Conventions AGENTS.md — **strictement préservées partout**

**Décision.** Toutes les conventions AGENTS.md (cf. [`AGENTS.md`](AGENTS.md), [`backend/AGENTS.md`](backend/AGENTS.md), [`frontend/AGENTS.md`](frontend/AGENTS.md)) sont préservées par chaque service :

- **camelCase partout** (Java, JSON, OpenAPI). Hibernate convertit en snake_case côté DB.
- **Pas de préfixe `is`** sur les booléens d'entités JPA.
- **Constructor injection** (pattern existant) sur toutes les Resources.
- **`@Transactional`** sur toutes les mutations Service.
- **Doc mise à jour dans le même commit** que le code correspondant — chaque sous-PR de migration met à jour `backend/docs/architecture.md`, `data-model.md`, `api-contract.md` pour refléter la nouvelle topologie.
- **Titre PR** : `<type>(<scope>): <description>` validé par [`pr-title-check.yml`](.github/workflows/pr-title-check.yml). Sous-PRs : `refactor(backend): extract <service-name>`, `chore(infra): add Kong helm template`, etc. PR finale : `refactor(backend): migrate to microservices architecture with Kong gateway and Kafka broker`.

### 24. Frontend — `git diff --stat frontend/` strictement vide

**Décision.** Aucune modification frontend. **Aucun fichier dans `frontend/`** ne change.

**Justification (démonstration que c'est possible)** :

- Le contrat OpenAPI (`openapi/openapi.yaml`) reste **byte-pour-byte identique** — chaque microservice expose la sous-section qui lui revient avec exactement les mêmes paths, méthodes, headers, codes HTTP, schémas de request/response.
- Kong route `/api/<path>` vers le bon service amont, mais `<path>` reste identique. Le frontend (Axios `baseURL: /api`) ne distingue pas.
- La config Vite proxy `/api → http://api:8080` reste **valide en local dev** : le monolithe `api` reste UP en local pendant la coexistence (cf. décision 22). Pour la prod, l'Ingress route `/api` vers `kong-proxy:8000` au lieu de `api:8080` — transparent pour le navigateur.
- Les types TypeScript dans [`frontend/src/types/`](frontend/src/types/) (Event, User, Faculty, etc.) restent valides — ils dérivent du contrat OpenAPI inchangé.
- Tous les services Axios dans [`frontend/src/services/`](frontend/src/services/) (`eventApi.ts`, `favoriteApi.ts`, `userService.ts`, etc.) — inchangés.
- Pas de header custom propagé par le frontend (le `Authorization: Bearer` reste suffisant) ; pas de découpage `baseURL` par feature.
- Skeletons `frontend/skeleton/*.bones.json` — inchangés (rendent un layout, pas un endpoint).

**Test de garantie**. La PR finale doit afficher `git diff --stat frontend/` = 0 lignes ajoutées, 0 lignes supprimées. Si une ligne du frontend bouge, c'est qu'une décision tranchée a été violée — bloquant pour la PR.

### 25. Coverage SonarCloud — ≥ 80 % par service, pas d'agrégation

**Décision.** Chaque service a son propre `sonar.projectKey` (cf. décision 17). Chaque service vise indépendamment :
- **≥ 80 % couverture sur le nouveau code** (cf. [`backend/AGENTS.md` ligne 130](backend/AGENTS.md#L130)).
- **≤ 3 % duplication sur le nouveau code**.
- **Ratings A** (Security, Reliability, Maintainability, Security Review).

L'**agrégation cross-service** n'est **pas** réclamée par SonarCloud (chaque projectKey est analysé séparément) ni par le brief Agon. L'utilisateur est responsable de surveiller les 14 dashboards Sonar individuellement (ou d'utiliser un dashboard SonarCloud Organization view qui agrège).

**Justification.** Conforme au brief Agon (« couverture reste ≥ 80 % SonarCloud »). Conforme à [`backend/AGENTS.md`](backend/AGENTS.md). Pas d'extension nécessaire.

### 26. Topics Kafka — table figée, partition keys explicites

**Décision.** Les topics Kafka sont nommés `<domain>.<event-type>`, partitionnés sur la clé d'agrégat naturelle (ex. `eventId` pour `events.*`, `userId` pour `users.*`). 1 partition unique en S8 (single-broker), `replication-factor=1`, `retention.ms=7 days`.

| Topic | Producteur | Consommateur(s) | Clé partition | Payload | Sémantique |
|---|---|---|---|---|---|
| `events.published` | `event-service` | `notification-service` (futur), `stats-service` | `eventId` | `{eventId, creatorId, publishedAt}` | At-least-once |
| `events.cancelled` | `event-service` | `notification-service` (futur) | `eventId` | `{eventId, cancelledAt}` | At-least-once |
| `events.banned` | `report-service` (job + handle) | `event-service` (applique le state change), `notification-service` (futur) | `eventId` | `{eventId, bannedBy, reason, bannedAt}` | At-least-once |
| `events.expired` | `event-service` (`EventExpirationJob`) | `notification-service` (futur) | `eventId` | `{eventId, expiredAt}` | At-least-once |
| `users.followed` | `follow-service` | `notification-service` (futur) | `followedId` | `{followerId, followedId, status, createdAt}` | At-least-once |
| `users.follow-requested` | `follow-service` | `notification-service` (futur) | `followedId` | idem | At-least-once |
| `users.follow-accepted` | `follow-service` | `notification-service` (futur) | `followerId` | idem | At-least-once |
| `comments.created` | `comment-service` | `notification-service` (futur) | `eventId` | `{commentId, eventId, authorId, parentCommentId, createdAt}` | At-least-once |
| `co-organizers.invited` | `co-organizer-service` | `notification-service` (futur) | `userId` | `{eventId, userId, invitedAt}` | At-least-once |
| `co-organizers.accepted` | `co-organizer-service` | `notification-service` (futur), `event-service` (cache) | `eventId` | `{eventId, userId, acceptedAt}` | At-least-once |
| `notifications.events` | (fan-in alias des topics ci-dessus, optionnel) | `notification-service` (futur) | `userId` (recipient) | `{type, userId, metadata, timestamp}` | At-least-once |

**Idempotence**. Les consommateurs traitent at-least-once → ils doivent être idempotents. Les events portent un `eventId` métier (pas un Kafka offset) qui sert de clé d'idempotence dans les consumers (ex. `notification-service` n'écrit jamais 2 notifications pour le même `eventId+type+userId`).

**DLQ (dead-letter queue).** Pas de DLQ Kafka explicite en S8 (sur-ingénierie). Si un consommateur lève une exception, `quarkus-smallrye-reactive-messaging-kafka` retry par défaut. Au-delà du retry, l'event est **logué** et **skippé** — perte acceptée pour un cours, à durcir en S9+ via topic `<topic>.dlq`.

### 27. Risques et mitigations — explicites

| Risque | Probabilité | Impact | Mitigation |
|---|---|---|---|
| **Latence cumulée cross-service** : `GET /events/{id}` peut maintenant traverser `event-service` → `user-service` (creator) → `attendance-service` (counts) → `view-service` (viewCount) | Moyenne | Moyen (UX dégradée si page détail charge en > 2s) | Pattern « parallel fan-out » via `Uni.combine().all().unis(...)` (Mutiny réactif) ; pour l'optimisation poussée, `event-service` cache localement les counts via Kafka projection (S9+) |
| **Perte d'atomicité transactionnelle** : impossible de commit `attendance` + `view` en une seule transaction JTA | Haute | Faible (les flux ne sont pas tous-ou-rien — ils sont indépendants par design) | Documenter explicitement que chaque service est sa propre frontière transactionnelle ; s'appuyer sur l'idempotence Kafka pour les workflows cross-service |
| **Drift de schéma Kafka** entre producteur et consommateur | Moyenne | Haut (consumer crash en boucle si payload incompatible) | Contract tests Pact (décision 18) + convention « ajouter des champs OK, supprimer/renommer KO » + topic `retention.ms = 7 days` pour permettre replay |
| **Crash Kong** = tout le trafic `/api/*` tombe (single point of failure) | Faible (DB-less stable) | Critique | Kong en `replicas: 2` minimum + readinessProbe stricte ; rollback via Helm trivial |
| **Crash Kafka** = tous les fan-outs se bloquent | Faible | Moyen (les flux sync continuent ; les notifications/projections accumulent un retard) | Kafka en `replicas: 1` en S8 (cours) ; producteurs configurés `acks=1` (pas `all`) ; producteurs catch les exceptions et logguent (pas de blocage du flux principal) |
| **Complexité dev local** : 14 services à lancer en local | Haute | Moyen (DX dégradée) | `docker-compose.dev.yml` qui lance Kong + Kafka + 14 services en `quarkus:dev` mode (mode mounté) ; alternative : `quarkus:dev` sur le monolithe legacy reste fonctionnel pendant la coexistence |
| **Debug ardu** : trouver dans quel service le crash a eu lieu | Moyenne | Moyen | `requestId` propagé via Kong (cf. décision 19) ; logs JSON aggrégés ; smoke E2E pré-soutenance |
| **Migration de données** : déplacer les tables sans downtime | Moyenne | Critique | `ALTER TABLE ... SET SCHEMA` en PostgreSQL = quasi-instant (juste un update de catalogue) ; pas de copie de rows ; downtime sub-seconde ; testé en preview namespace AVANT prod |
| **Tests cross-service flaky en CI** | Haute | Faible | Pact = static (lit fichiers, pas de vrai service up) ; intégration par service = DevServices isolés (Postgres + Kafka Testcontainers, pas de partage) |
| **Pression DB sur l'instance partagée** | Faible | Moyen (1 service spammant peut affecter les autres) | Connection pool par service borné (`quarkus.datasource.jdbc.max-size=10` par défaut) ; PgBouncer S9+ si nécessaire |
| **Incohérence référentielle** : un user supprimé laisse des `Comment.authorId` orphelins | Haute | Faible (déjà accepté côté monolithe, pattern défensif `Report.reporter` nullable) | Job de cleanup `OrphanRowReaper` S9+ ; UX `displayName` fallback `"Utilisateur supprimé"` côté DTO projection |

### 28. Aliasing path Kong — pas de transformation, juste routage

**Décision.** Kong **ne transforme pas** les paths. `/api/events/{id}/comments` arrive à `comment-service` exactement comme `/api/events/{id}/comments` — le service écoute sur ce path, pas sur `/comments` ou `/internal/comments`. L'unique différence entre la requête vue par Kong et celle reçue par le service est l'ajout du header `X-Request-ID` et la validation JWT (qui ne change pas le payload).

**Justification.** Préserve le contrat OpenAPI (cf. décision 24). Tout `quarkus.http.root-path=api` actuel reste en place sur chaque service — chaque service est **indépendant** de Kong : tu peux tester un service en `curl http://comment-service:8080/api/events/42/comments` directement et obtenir la même réponse que via `curl https://pinfo6.p-info.net/api/events/42/comments`.

### 29. Path dupliqué `/events/{id}/view` — préservé, hors scope

**Décision.** L'OpenAPI contient un path `/events/{id}/view` dupliqué (lignes 3482 et 3560) — artefact pré-existant. La migration **ne corrige pas** ce duplicate (cf. [`specs_scrum-147.md` ligne 825](specs_archives/specs_claude/specs_scrum-147.md#L825) : *« c'est un artefact pré-existant **hors scope**, ne pas le toucher »*). Le routage Kong dirige les deux entrées vers `view-service`.

### 30. Dépendances Maven — 4 nouvelles extensions Quarkus, pas de framework non-Quarkus

**Décision.** Chaque service backend gagne 4 nouvelles dépendances dans son `pom.xml` :

```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-rest-client-reactive</artifactId>      <!-- REST sync cross-service -->
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-smallrye-reactive-messaging-kafka</artifactId>  <!-- Kafka producer/consumer -->
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-smallrye-fault-tolerance</artifactId>  <!-- @Retry, @Timeout, @CircuitBreaker, @Fallback -->
</dependency>
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-micrometer-registry-prometheus</artifactId>     <!-- /q/metrics -->
</dependency>
```

**Pas** de dépendances non-Quarkus ajoutées. Pas de Spring, pas de Resilience4j en direct (Quarkus encapsule), pas de Pact JVM en runtime (test scope uniquement).

**Justification.** Reste dans l'écosystème Quarkus officiel ; aucune réécriture de patterns existants ; minimum viable pour faire tourner Kafka + REST clients.

---

## Analyse de l'existant

### 4.1 Entités JPA (à distribuer entre services)

| Entité | Fichier | Service propriétaire futur | Tables | FK cross-service à transformer |
|---|---|---|---|---|
| `User` | [`User.java`](backend/src/main/java/ch/unige/events/entity/User.java) | `user-service` | `users`, `user_interests` (collection) | — (root) |
| `Event` | [`Event.java`](backend/src/main/java/ch/unige/events/entity/Event.java) | `event-service` | `events`, `event_tags` | `creator: @ManyToOne User` → `creatorAuth0Id: String` + `creatorId: UUID` (lookup REST sync vers user-service) |
| `Attendance` | [`Attendance.java`](backend/src/main/java/ch/unige/events/entity/Attendance.java) | `attendance-service` | `attendances` | (déjà `userId: UUID`, `eventId: Long` bruts — rien à changer) |
| `Favorite` | [`Favorite.java`](backend/src/main/java/ch/unige/events/entity/Favorite.java) | `favorite-service` | `favorites` | (déjà bruts) |
| `EventView` | [`EventView.java`](backend/src/main/java/ch/unige/events/entity/EventView.java) | `view-service` | `event_views` | (déjà bruts) |
| `EventCoOrganizer` | [`EventCoOrganizer.java`](backend/src/main/java/ch/unige/events/entity/EventCoOrganizer.java) | `co-organizer-service` | `event_co_organizers` | (déjà bruts) |
| `Comment` | [`Comment.java`](backend/src/main/java/ch/unige/events/entity/Comment.java) | `comment-service` | `comments` | `event: @ManyToOne Event` → `eventId: Long` brut + REST lookup `event-service`. `author: @ManyToOne User` → `authorId: UUID` brut + REST lookup `user-service`. `parentComment: @ManyToOne Comment` → **inchangé** (auto-référence intra-service) |
| `Follow` | [`Follow.java`](backend/src/main/java/ch/unige/events/entity/Follow.java) | `follow-service` | `follows` | (déjà bruts `followerId/followedId: UUID`) |
| `Report` | [`Report.java`](backend/src/main/java/ch/unige/events/entity/Report.java) | `report-service` | `reports` | `event: @ManyToOne Event`, `reporter: @ManyToOne User`, `reviewedBy: @ManyToOne User` → tous transformés en IDs bruts + REST lookup |

### 4.2 Resources JAX-RS (à distribuer)

| Resource | Fichier | Service propriétaire futur | Endpoints | Notes |
|---|---|---|---|---|
| `UserResource` | [`UserResource.java`](backend/src/main/java/ch/unige/events/resource/UserResource.java) | `user-service` | 5+ paths `/users/*` | Garde `quarkus-oidc` validation locale |
| `EventResource` | [`EventResource.java`](backend/src/main/java/ch/unige/events/resource/EventResource.java) | `event-service` | 9+ paths `/events`, `/events/{id}/*` | Inclut `/events/{id}/occurrences` (SCRUM-147) |
| `AdminEventResource` | [`AdminEventResource.java`](backend/src/main/java/ch/unige/events/resource/AdminEventResource.java) | `event-service` | `/admin/events/{id}/feature`, `/unfeature` | `@RolesAllowed("ADMIN")` préservé |
| `EventSearchResource` | [`EventSearchResource.java`](backend/src/main/java/ch/unige/events/resource/EventSearchResource.java) | `event-service` | `/events/search` | Full-text PostgreSQL ILIKE intra-service |
| `AttendanceResource` | [`AttendanceResource.java`](backend/src/main/java/ch/unige/events/resource/AttendanceResource.java) | `attendance-service` | 4 paths attend/attendees | Cross-service: lookup event PUBLISHED via `event-service` REST |
| `FavoriteResource` | [`FavoriteResource.java`](backend/src/main/java/ch/unige/events/resource/FavoriteResource.java) | `favorite-service` | 3 paths | Cross-service: lookup event existence |
| `EventViewResource` | [`EventViewResource.java`](backend/src/main/java/ch/unige/events/resource/EventViewResource.java) | `view-service` | `/events/{id}/view` | Cross-service: lookup event PUBLISHED |
| `EventCoOrganizerResource` | [`EventCoOrganizerResource.java`](backend/src/main/java/ch/unige/events/resource/EventCoOrganizerResource.java) | `co-organizer-service` | 6 paths | Cross-service: cascade vers `event-service` pour resolver `creator` |
| `CommentResource` | [`CommentResource.java`](backend/src/main/java/ch/unige/events/resource/CommentResource.java) | `comment-service` | POST + GET `/events/{id}/comments` | Cascade lourde: `event-service` (visibilité), `co-organizer-service` (org), `user-service` (author) |
| `CommentDirectResource` | [`CommentDirectResource.java`](backend/src/main/java/ch/unige/events/resource/CommentDirectResource.java) | `comment-service` | DELETE `/comments/{id}` | Cascade autorisation cross-service |
| `FollowResource` + `FollowRequestResource` | 2 fichiers | `follow-service` | 7 paths | `user-service` REST lookup pour `profilePublic` cible |
| `ReportResource` + `AdminReportResource` | 2 fichiers | `report-service` | 3 paths | `event-service` REST lookup pour PUBLISHED check + cascade SCRUM-136 |
| `EventStatsResource` | [`EventStatsResource.java`](backend/src/main/java/ch/unige/events/resource/EventStatsResource.java) | `stats-service` | `/events/{id}/stats` | Lecture pure: query stats via `view-service` + `attendance-service` + `favorite-service` REST |
| `CalendarResource` | [`CalendarResource.java`](backend/src/main/java/ch/unige/events/resource/CalendarResource.java) | `calendar-service` | 3 paths token + ICS feed | Cross-service: `user-service` (token) + `favorite-service` (favoris) + `attendance-service` (ATTENDING) + `event-service` (events ID→full) |
| `RedirectResource` | [`RedirectResource.java`](backend/src/main/java/ch/unige/events/resource/RedirectResource.java) | `share-service` | `/s/{shortCode}` | Cross-service: `event-service` REST pour resolver shortCode → event URL |

### 4.3 Migrations Flyway (à scinder)

| Migration | Tables/objets touchés | Distribution |
|---|---|---|
| (V0 implicite) `users` | Hibernate `update` historique | `user-service` V1 doit recréer la table (CREATE TABLE IF NOT EXISTS — idempotent sur le schema déplacé) |
| `V1__reconcile_check_constraints.sql` | CHECK sur `events` + `attendances` | Scinder en 2 : la part `events` reste avec `event-service`, la part `attendances` migre vers `attendance-service` |
| `V2__create_events.sql` | `events` | `event-service` |
| `V3__create_attendances.sql` | `attendances` | `attendance-service` |
| `V4__create_favorites.sql` | `favorites` | `favorite-service` |
| `V5__create_event_views.sql` | `event_views` | `view-service` |
| `V6__create_reports.sql` | `reports` | `report-service` |
| `V7__reconcile_check_constraints.sql` | CHECK `reports.status` | `report-service` |
| `V8__create_event_co_organizers.sql` | `event_co_organizers` | `co-organizer-service` |
| `V9__widen_event_description.sql` | `events.description` TEXT | `event-service` |
| `V10__add_report_reason_and_review_fields.sql` | `reports` colonnes | `report-service` |
| `V11__allow_event_status_expired.sql` | CHECK `events.status` | `event-service` |
| `V12__add_featured_to_events.sql` | `events.featured` + index | `event-service` |
| `V13__allow_event_status_banned.sql` | CHECK `events.status` | `event-service` |
| `V14__create_follows.sql` | `follows` | `follow-service` |
| `V15__create_comments.sql` | `comments` | `comment-service` |
| `V16__alter_comments_parent_fk_set_null.sql` | `comments` FK | `comment-service` |
| `V17__add_event_recurrence.sql` | `events.parent_event_id`, `recurrence_rule` | `event-service` |

**Bootstrap par service** : chaque service possède un `V1__extract_<service>_schema.sql` qui :
- crée le schéma `<service>_svc` ;
- exécute les `ALTER TABLE <table> SET SCHEMA <service>_svc;` pour les tables possédées ;
- `CREATE ROLE` + `GRANT` (cf. décision 8) ;
- `INSERT INTO <service>_svc.flyway_schema_history` les rangées baseline V1..V17 historiques pour court-circuiter Flyway sur le schéma extrait.

### 4.4 Schedulers `@Scheduled`

| Job | Fichier | Cron | Service propriétaire |
|---|---|---|---|
| `EventExpirationJob` | [`EventExpirationJob.java`](backend/src/main/java/ch/unige/events/scheduler/EventExpirationJob.java) | `every = "1h"` | `event-service` |
| `ModerationCleanupJob` | [`ModerationCleanupJob.java`](backend/src/main/java/ch/unige/events/scheduler/ModerationCleanupJob.java) | `cron = "0 0 3 * * ?", timeZone = "Europe/Zurich"` | `report-service` (mais émet event Kafka `events.banned` consommé par `event-service` pour appliquer le state change) |

### 4.5 Topics Kafka projetés (table figée — cf. décision 26)

| Topic | Producteur | Consommateur(s) | Clé | Charge |
|---|---|---|---|---|
| `events.published` | `event-service` | `notification-service` (futur), `stats-service` | `eventId` | `{eventId, creatorId, publishedAt}` |
| `events.cancelled` | `event-service` | `notification-service` (futur) | `eventId` | `{eventId, cancelledAt}` |
| `events.banned` | `report-service` | `event-service`, `notification-service` (futur) | `eventId` | `{eventId, bannedBy, reason, bannedAt}` |
| `events.expired` | `event-service` (`EventExpirationJob`) | `notification-service` (futur) | `eventId` | `{eventId, expiredAt}` |
| `users.followed` | `follow-service` | `notification-service` (futur) | `followedId` | `{followerId, followedId, status, createdAt}` |
| `users.follow-requested` | `follow-service` | `notification-service` (futur) | `followedId` | idem |
| `users.follow-accepted` | `follow-service` | `notification-service` (futur) | `followerId` | idem |
| `comments.created` | `comment-service` | `notification-service` (futur) | `eventId` | `{commentId, eventId, authorId, parentCommentId, createdAt}` |
| `co-organizers.invited` | `co-organizer-service` | `notification-service` (futur) | `userId` | `{eventId, userId, invitedAt}` |
| `co-organizers.accepted` | `co-organizer-service` | `notification-service` (futur), `event-service` | `eventId` | `{eventId, userId, acceptedAt}` |

### 4.6 Helm chart (état actuel)

```
k8s/chart/
├── Chart.yaml                       (v0.1.0, appVersion 1.0.0)
├── values.yaml                      (api / web / db / minio resources, image registry GHCR)
├── values-preview.yaml              (override preview deploys)
└── templates/
    ├── api/                         (Deployment + Service)
    ├── web/                         (Deployment + Service)
    ├── db/                          (StatefulSet postgres:16 + Service + PVC 1Gi)
    ├── minio/                       (StatefulSet + Service)
    ├── cloudflared/                 (Deployment, mode named)
    ├── ingress/                     (2 Ingresses — main + s3 séparés)
    └── doppler.yaml                 (Doppler operator pour secrets)
```

### 4.7 CI/CD pipelines

```
.github/workflows/
├── build.yml          (build-backend single-job + build-frontend)
├── deploy.yml         (deploy-production sur main + deploy-preview sur PR)
├── ci-cd.yml          (orchestrateur : build → deploy)
├── cleanup.yml        (suppression preview namespace après merge/close PR)
└── pr-title-check.yml (validation conventional commits)
```

### 4.8 Tests existants à conserver

| Pattern | Fichier représentatif | Réutilisable par service ? |
|---|---|---|
| `@QuarkusTest` + DevServices PostgreSQL | tous les `*ServiceCoverageTest`, `*ResourceTest` | ✅ — chaque service en hérite |
| `@TestSecurity(user="auth0\|alice")` | tous les `*ResourceTest` | ✅ |
| Mock CDI `@Mock @ApplicationScoped extends Service` | `*ServiceMock.java` | ✅ |
| `ShareServiceCoverageProfile` (exclude-types ARC) | [`ShareServiceCoverageProfile.java`](backend/src/test/java/ch/unige/events/service/ShareServiceCoverageProfile.java) | ✅ — adapté par service |
| RestAssured Hamcrest matchers | tous les `*ResourceTest` | ✅ |

---

## Architecture proposée

### Diagramme d'ensemble (texte)

```
                                ┌──────────────────────────────────┐
                                │         Navigateur SPA           │
                                │  (Axios baseURL = /api,          │
                                │   Authorization: Bearer <jwt>)   │
                                └───────────────┬──────────────────┘
                                                │ HTTPS
                                                ▼
                              ┌─────────────────────────────────┐
                              │   Ingress NGINX (host pinfo6)   │
                              │   - "/" → web (Nginx + SPA)     │
                              │   - "/api/*" → kong-proxy:8000  │
                              │   - "/s3/*"  → minio:9000       │
                              └────────────────┬────────────────┘
                                               │
                                               ▼
       ┌─────────────────────────────────────────────────────────────────────┐
       │                        Kong API Gateway (DB-less)                   │
       │  - Plugins globaux : cors, correlation-id, prometheus               │
       │  - Plugin par-route: rate-limiting (events.create, comments.post,   │
       │    follows.follow)                                                  │
       │  - Forwarde Authorization: Bearer <jwt> sans modification           │
       │  - X-Request-ID forwardé au service amont                            │
       │  - Table de routes statique (cf. décision 6) : path → service amont │
       └──────┬─────────┬──────────┬─────────┬───────┬──────┬──────┬─────────┘
              │         │          │         │       │      │      │ ...
              ▼         ▼          ▼         ▼       ▼      ▼      ▼
       ┌──────────┐  ┌────────┐  ┌────────┐ ┌──────┐ ┌────┐ ┌────┐ ...
       │  user-   │  │ event- │  │attend- │ │ fav- │ │comm│ │follow│
       │ service  │  │service │  │ service│ │service │service│service│
       │  :8080   │  │ :8080  │  │ :8080  │ │:8080 │ │ :  │ │ :  │
       └────┬─────┘  └───┬────┘  └────┬───┘ └──┬───┘ └─┬──┘ └─┬──┘
            │            │            │        │       │      │
            │            │            │        │       │      │
            │   ┌────────┴────────────┴────────┴───────┴──────┴───────┐
            │   │     REST sync cross-service (JWT propagated)        │
            │   └─────────────────────────────────────────────────────┘
            │
            │   ┌────────────────────────────────────────────────────────┐
            └──>│  Kafka (KRaft single-broker)                          │
                │  Topics: events.published, events.cancelled,           │
                │  events.banned, events.expired, users.followed,        │
                │  users.follow-requested, users.follow-accepted,        │
                │  comments.created, co-organizers.invited,              │
                │  co-organizers.accepted, notifications.events          │
                │  (Producteurs: services métiers ; Consommateurs:       │
                │   notification-service futur, event-service, stats)    │
                └────────────────────────────────────────────────────────┘
                                          │
                                          ▼
                ┌─────────────────────────────────────────────────────────┐
                │      PostgreSQL 16 (1 instance partagée)                │
                │      Schémas: user_svc, event_svc, attendance_svc,      │
                │               favorite_svc, view_svc, co_organizer_svc, │
                │               comment_svc, follow_svc, report_svc       │
                │      Rôles dédiés par service (RBAC strict)             │
                └─────────────────────────────────────────────────────────┘
```

### Flux d'authentification

```
1. Frontend → Auth0 /authorize → ... → access_token (JWT)
2. Frontend → GET /api/users/me  Authorization: Bearer <jwt>
3. Ingress NGINX → kong-proxy:8000
4. Kong : applique cors, génère X-Request-ID, ajoute X-Forwarded-* ; (optionnel) plugin jwt rejette si token malformé ;
   route /users/me → user-service:8080
5. user-service (quarkus-oidc en mode service) :
   - valide JWT via OIDC Discovery Auth0 (signature + exp + audience) — JWKS caché
   - injecte SecurityIdentity ; principal.getName() = auth0Id (sub claim)
   - lit la claim https://quarkus-security.com/roles si présente
   - exécute UserResource.me() → 200 + UserProfileResponse
6. Réponse remonte Kong → Ingress → Frontend
```

### Flux d'écriture cross-service (POST /api/events/{id}/comments)

```
1. Frontend POST /api/events/42/comments {"content":"hi","parentCommentId":null} + JWT
2. Kong route → comment-service
3. comment-service.post() :
   a. quarkus-oidc valide JWT, principal = auth0Id
   b. REST sync GET event-service /api/events/42 (visibilité, anti-oracle ISSUE-92)
      → si 404 : remontée 404 telle quelle au caller (envelope identique)
      → si event status ≠ PUBLISHED et caller ≠ creator : 400 cannot_comment_*
   c. (si parentCommentId fourni) lookup local sur comment-service
   d. trim content, persist row dans comment_svc.comments
   e. (cascade SCRUM-136) REST sync co-organizer-service GET /events/42/co-organizers/check?userId=<authorId>
      pour calculer authorIsOrganizer du DTO
   f. émet Kafka event sur comments.created avec key=42
   g. retourne 201 + CommentDTO
4. (asynchrone) notification-service (futur) consomme comments.created → crée Notification rows
   pour le créateur de l'event + co-organisateurs ACCEPTED + utilisateurs mentionnés (@displayName)
```

### Flux de lecture agrégée (GET /api/events/{id})

```
1. Frontend GET /api/events/42 (anonyme ou authentifié)
2. Kong route → event-service
3. event-service.getById() :
   a. SELECT events.* FROM event_svc.events WHERE id = 42 (intra-service)
   b. règle anti-oracle ISSUE-92 (DRAFT/CANCELLED/BANNED non-créateur → 404)
   c. parallel fan-out (Mutiny) :
      - REST sync user-service GET /users/<creatorId> → creator displayName/avatarUrl
      - REST sync attendance-service GET /events/42/counts → attendingCount, waitlistedCount, availableSpots
      - REST sync view-service GET /events/42/views/count → viewCount
      - REST sync favorite-service GET /events/42/favorites/count → interestedCount
   d. compose EventDTO complet et retourne 200
```

---

## Plan d'implémentation par étape (ordre strict — coexistence préservée)

> **Note d'ordre.** Cette migration n'est pas « openapi-first » au sens classique
> (le contrat ne change pas). L'ordre d'implémentation est dicté par la
> **stratégie strangler fig** (cf. décision 20) : chaque étape extrait UN
> service du monolithe pendant que le monolithe reste UP. La règle d'or est :
> **ne jamais casser un path qui marche aujourd'hui** — Kong est responsable
> de basculer une route vers le nouveau service uniquement quand celui-ci a
> été testé en preview namespace.

### Étape 0 — Fondations Kong + Kafka + Helm umbrella (PR #1)

**Objectif** : déployer Kong (DB-less avec table de routes initiale qui forward 100 % du trafic vers le monolithe `api` legacy) + Kafka KRaft single-broker, sans extraction de service.

**Fichiers à créer.**

```
k8s/chart/templates/kong/
  ├── deployment.yaml        (image kong:3.7-alpine, replicas: 2, configmap-mounted /etc/kong/kong.yml)
  ├── service.yaml            (ClusterIP, ports 8000 proxy / 8443 proxy-ssl / 8001 admin-internal)
  ├── configmap-routes.yaml   (table de routes initiale : tout → api:8080)
  └── plugin-config.yaml      (cors, correlation-id, prometheus globaux)

k8s/chart/templates/kafka/
  ├── statefulset.yaml        (image bitnami/kafka:3.x ou apache/kafka:3.x KRaft mode, 1 replica, PVC 5Gi)
  ├── service.yaml            (Headless ClusterIP, port 9092)
  └── topics-init.yaml        (Job qui exécute `kafka-topics.sh --create` pour les 10 topics)

k8s/chart/values.yaml — ajouter sections kong: et kafka:
k8s/chart/templates/ingress/ingress.yaml — basculer /api/* path → kong-proxy:8000
```

**Fichiers à modifier.**

- [`k8s/chart/Chart.yaml`](k8s/chart/Chart.yaml) — bump version `0.1.0 → 0.2.0`.
- [`k8s/chart/values.yaml`](k8s/chart/values.yaml) — ajouter sections `kong:` et `kafka:` avec resources.

**Checks intermédiaires.**
- `helm template ./k8s/chart` valide (pas d'erreur templating).
- `helm upgrade --install` sur namespace preview = success ; tous pods Ready.
- `curl https://pr-N.pinfo6.p-info.net/api/users/me` retourne 401 (token absent) — preuve que Kong route vers le monolithe correctement.

**Commit suggéré.** `chore(infra): add Kong API gateway and Kafka broker to helm chart`

### Étape 1 — Modularisation Maven (PR #2)

**Objectif** : transformer le projet `backend/` mono-module en multi-module Maven avec un parent POM et N sous-modules services. **Pas encore d'extraction logique** — tout le code reste dans `backend/services/legacy-monolith/` pour cette PR.

**Structure cible.**

```
backend/
├── pom.xml                          (parent POM, packaging: pom)
├── services/
│   ├── legacy-monolith/             (existant — déplacé sous services/)
│   │   ├── pom.xml                  (hérite du parent)
│   │   ├── src/main/java/ch/unige/events/...
│   │   └── src/main/resources/...
│   ├── user-service/                (placeholder vide pour les PR ultérieures)
│   ├── event-service/               (placeholder)
│   ├── ...                           (idem)
│   └── shared/                      (futur — DTOs partagés, RestClient interfaces)
└── pom.xml (legacy)                  → remplacé par le parent multi-module
```

**Justification.** Préparation purement structurelle. Le pipeline CI continue de builder uniquement `legacy-monolith` (matrice ne sera activée qu'à la PR #3).

**Checks intermédiaires.**
- `./mvnw verify` à la racine `backend/` build le parent + `legacy-monolith` + tous les modules vides → vert.
- Image Docker monolithe pushée en GHCR identique à pré-PR.
- Preview deploy pleinement fonctionnel.

**Commit suggéré.** `refactor(backend): convert to multi-module maven layout`

### Étape 2 — Extraction `share-service` (PR #3)

**Objectif** : premier service extrait. Choix `share-service` parce que minimalement couplé (pas d'entité racine propre, juste 2 endpoints `/events/{id}/share` + `/s/{shortCode}`).

**Fichiers à créer dans `backend/services/share-service/`.**

- `pom.xml` (extends parent, dépendances `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-oidc`, `quarkus-rest-client-reactive`, `quarkus-smallrye-reactive-messaging-kafka`, `quarkus-smallrye-fault-tolerance`, `quarkus-micrometer-registry-prometheus`).
- `src/main/java/ch/unige/events/share/resource/ShareResource.java` (copier/adapter de `EventResource` la méthode `/events/{id}/share`) + `RedirectResource` complet.
- `src/main/java/ch/unige/events/share/service/ShareService.java` (logique de génération du shortCode — déplacée du monolithe).
- `src/main/java/ch/unige/events/share/client/EventServiceClient.java` (`@RegisterRestClient`, lookup `GET /events/{id}` pour résoudre `shortCode → URL`).
- `src/main/resources/application.properties` (port 8080, rest-client URL, OIDC config héritée d'env).
- `src/main/resources/db/migration/V1__noop_share_has_no_schema.sql` (commentaire explicite : « share-service n'a pas de schéma propre — repo de la dette technique `Event.shareCode` reste avec event-service »).

**Migration / rerouting.**
- `k8s/chart/templates/share-service/` (Deployment + Service ClusterIP).
- `k8s/chart/templates/kong/configmap-routes.yaml` — basculer `/api/events/{id}/share` et `/api/s/{shortCode}` de `api:8080` vers `share-service:8080`.

**Suppression code monolithe.**
- Retirer `RedirectResource.java`, `ShareService.java` du monolithe `legacy-monolith/`.
- Conserver le champ `Event.shareCode` (le lookup REST cross-service vient le résoudre depuis share-service).

**Tests.**
- Unit tests Java migrés tels quels.
- Contract tests Pact : `share-service` consumer définit le contrat de `event-service` GET `/events/{id}` (lecture d'`id` + `shareCode`). Pact JSON commité dans `backend/contract-tests/pacts/share-event.json`.

**Checks intermédiaires.**
- `./mvnw verify` (matrix avec service=share-service) — couverture ≥ 80 %.
- Preview deploy : `curl -L https://pr-N.../api/s/<shortcode>` redirige (302) — fonctionnel via le nouveau service.

**Commit suggéré.** `refactor(backend): extract share-service`

### Étapes 3..13 — Extractions services suivantes (1 PR par étape)

L'ordre suit la décision 20. Chaque PR applique le **même pattern** que l'étape 2 :

1. Créer `backend/services/<service>/` avec POM, `application.properties`, `Dockerfile` (via Jib).
2. **Migration Flyway** dédiée — créer `V1__extract_<service>_schema.sql` (cf. décision 9, baseline + ALTER TABLE SET SCHEMA + GRANT).
3. **Code Java** : copier resources/services/entités du monolithe → adapter les FK `@ManyToOne` cross-service en pointeurs Long/UUID bruts (cf. décision 5).
4. **REST clients** : déclarer interfaces `@RegisterRestClient` pour les services amont nécessaires.
5. **Kafka producteurs** : `@Outgoing("<topic>-out")` sur les méthodes service qui émettent (cf. tableau § 4.5).
6. **Helm sub-template** `k8s/chart/templates/<service>/` (Deployment + Service).
7. **Kong route bascule** : `k8s/chart/templates/kong/configmap-routes.yaml` — déplacer le path correspondant.
8. **Suppression code monolithe** : retirer Resources/Services/Entités migrés ; ne pas oublier de retirer aussi les helpers cross-service inutiles dans le monolithe.
9. **Migration de données** : `V<N>__migrate_<table>.sql` côté monolithe = `ALTER TABLE <table> SET SCHEMA <service>_svc;` exécuté par le pipeline `helm upgrade` au déploiement preview AVANT que Kong ne re-route (ordre strict).
10. **Tests** : unit + integration + contract Pact.
11. **Doc** : `backend/docs/architecture.md` et `data-model.md` mis à jour pour refléter le nouveau service.

**Détail par étape — synthèse** :

| Étape | Service | Fichiers JAX-RS migrés | Tables migrées | Topics Kafka producés |
|---|---|---|---|---|
| 3 | `view-service` | `EventViewResource` | `event_views` | (aucun en S8) |
| 4 | `favorite-service` | `FavoriteResource` | `favorites` | (aucun) |
| 5 | `calendar-service` | `CalendarResource` | (`User.calendarToken` reste avec user-service ; lookup REST) | (aucun) |
| 6 | `follow-service` | `FollowResource`, `FollowRequestResource` | `follows` | `users.followed`, `users.follow-requested`, `users.follow-accepted` |
| 7 | `comment-service` | `CommentResource`, `CommentDirectResource` | `comments` | `comments.created` |
| 8 | `co-organizer-service` | `EventCoOrganizerResource` | `event_co_organizers` | `co-organizers.invited`, `co-organizers.accepted` |
| 9 | `attendance-service` | `AttendanceResource` | `attendances` | (aucun) |
| 10 | `report-service` | `ReportResource`, `AdminReportResource` (+ scheduler `ModerationCleanupJob`) | `reports` | `events.banned` (consommé par event-service) |
| 11 | `stats-service` | `EventStatsResource` | (aucune — lecture pure) | (aucun ; consomme `events.published`) |
| 12 | `me-aggregator-service` | (proxy paths `/users/me/events`, `/users/me/attendances`, `/users/me/favorites`, `/users/me/participations`) | (aucune) | (aucun) |
| 13 | `user-service` | `UserResource` | `users`, `user_interests` | (aucun) |
| 14 | `event-service` (dernier) | `EventResource`, `AdminEventResource`, `EventSearchResource` (+ scheduler `EventExpirationJob`) | `events`, `event_tags` | `events.published`, `events.cancelled`, `events.expired` |

À l'issue de l'étape 14, **`legacy-monolith` est vide** — il ne sert plus de Resources, ne contient plus d'entités. La PR #15 est le **cleanup** :

### Étape 15 — Suppression du monolithe legacy (PR #15)

**Fichiers à supprimer.**
- `backend/services/legacy-monolith/` entièrement.
- `k8s/chart/templates/api/` entièrement.
- Section `api:` dans `values.yaml`.
- Référence `image.api.tag` dans `deploy.yml` (variabilisée par service).

**Validation.**
- `helm template ./k8s/chart` ne rend plus de `Deployment api`.
- Preview deploy fonctionnel via Kong → microservices uniquement.
- E2E happy path test (cf. décision 18) vert sur preview.

**Commit suggéré.** `refactor(backend): remove legacy monolith now that all services are extracted`

### Étape 16 — Documentation finale (PR #16)

**Fichiers à modifier.**
- [`backend/docs/architecture.md`](backend/docs/architecture.md) — réécriture complète : section « Vue d'ensemble microservices » devient une réalité, table des services + responsabilités + topics Kafka producés/consommés, diagramme texte (cf. § « Architecture proposée » de cette spec).
- [`backend/docs/data-model.md`](backend/docs/data-model.md) — ajouter pour chaque entité son service propriétaire + schéma DB.
- [`backend/docs/api-contract.md`](backend/docs/api-contract.md) — ajouter pour chaque path le service amont (colonne supplémentaire dans la grande table).
- [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) — bloc `## Sprint 8 — Migration vers microservices — 2026-MM-DD` (Livré).
- [`backend/docs/dev-guide.md`](backend/docs/dev-guide.md) — workflow dev local mis à jour (lancer `docker-compose -f docker-compose.dev.yml up`).
- [`AGENTS.md`](AGENTS.md) racine — référence à la nouvelle topologie.
- [`backend/AGENTS.md`](backend/AGENTS.md) — section « Architecture en couches » étendue : par service, pas globalement.

**Pas de modification frontend.** `git diff --stat frontend/` strictement vide.

**Commit suggéré.** `docs(backend): document microservices architecture`

---

## Ordre d'implémentation strict

1. **Branchement.** `git fetch origin && git checkout -b refactor(backend):-migrate-to-microservices origin/main --no-track` (le `--no-track` est non négociable). **NB** : le caractère `(` dans le nom de branche est valide (Git autorise `(` `)` dans les refnames). Si le shell pose problème, échapper ou wrapper la branche : `git checkout -b 'refactor(backend): migrate-to-microservices' origin/main --no-track`.
2. **Étape 0 — Kong + Kafka + Helm.** ✅ checkpoint : preview deploy = `curl /api/users/me` retourne 401 (le monolithe reste UP).
3. **Étape 1 — Modularisation Maven.** ✅ checkpoint : `./mvnw verify` à la racine `backend/` vert ; image monolithe identique pré-PR.
4. **Étapes 2-14 — Extractions service par service** (ordre strict cf. décision 20). Pour CHAQUE étape :
   - 4a. Créer service Maven module + POM + classes Java migrées + RestClient + Kafka producer.
   - 4b. Créer migration Flyway dédiée + role/grants DB.
   - 4c. Créer Helm sub-template (Deployment + Service).
   - 4d. **Faire pointer Kong** vers le nouveau service dans le ConfigMap routes (le monolithe reçoit toujours sur ses paths existants tant que la route Kong n'est pas modifiée — sécurité avant tout).
   - 4e. **Migration de données** via `ALTER TABLE ... SET SCHEMA ...` au moment du déploiement preview.
   - 4f. **Supprimer le code Java migré** du monolithe `legacy-monolith/`.
   - 4g. Tests par service + contract Pact pour chaque cross-service consumer.
   - 4h. Doc partielle dans la PR (mise à jour finale en PR #16).
   - ✅ checkpoint : preview deploy + smoke tests + E2E happy path partiel vert.
5. **Étape 15 — Suppression `legacy-monolith` + `templates/api/`.** ✅ checkpoint : helm chart ne contient plus de `Deployment api` ; tous endpoints servis par microservices.
6. **Étape 16 — Documentation finale.** ✅ checkpoint final : `git diff --stat frontend/` strictement vide ; tous tests verts ; tous SonarCloud projectKey verts ; soutenance prête.

---

## Commits atomiques suggérés

Format strictement conforme à [`.github/workflows/pr-title-check.yml`](.github/workflows/pr-title-check.yml) (regex `^([a-z]+)\(([^)]+)\): `, scope obligatoire pour `feat`/`refactor`/`perf`).

PR #1 (Étape 0) :
1. `chore(infra): add Kong API gateway and Kafka broker to helm chart`

PR #2 (Étape 1) :
2. `refactor(backend): convert to multi-module maven layout`

PR #3..14 (Étapes 2..14) — un commit par service, en cumulant éventuellement plusieurs commits par PR si nécessaire pour la review :
3. `refactor(backend): extract share-service`
4. `refactor(backend): extract view-service`
5. `refactor(backend): extract favorite-service`
6. `refactor(backend): extract calendar-service`
7. `refactor(backend): extract follow-service`
8. `refactor(backend): extract comment-service`
9. `refactor(backend): extract co-organizer-service`
10. `refactor(backend): extract attendance-service`
11. `refactor(backend): extract report-service`
12. `refactor(backend): extract stats-service`
13. `refactor(backend): extract me-aggregator-service`
14. `refactor(backend): extract user-service`
15. `refactor(backend): extract event-service`

PR #15 (Étape 15) :
16. `refactor(backend): remove legacy monolith now that all services are extracted`

PR #16 (Étape 16) :
17. `docs(backend): document microservices architecture`

PR finale (consolidation) — titre EXACT (cf. § Livrable FINAL attendu) :
18. (sur la branche `refactor(backend): migrate-to-microservices` consolidée vers `main`) — voir « Livrable FINAL attendu » ci-dessous.

(post-PR, si applicable) `fix(backend): apply Copilot review — <description>`.

Astuce : commiter avec un message conforme dès le **premier** commit de chaque PR → GitHub pré-remplit automatiquement le titre.

---

## Workflow Git / PR / Copilot / CI (obligatoire)

### Pré-requis local

- **Java 21 absent du host** (mémoire `pr_access_workaround.md`). Tout `./mvnw verify` / `mvn` / `gh` / `git` lourd passe par :
  ```bash
  docker exec -w /workspace unige-events-app-1 bash -c "cd /workspace/backend && ./mvnw verify"
  ```
- Validation par étape : `./mvnw verify` doit passer après chaque commit fonctionnel.
- **Outils additionnels** : Helm CLI ≥ 3.12 (pour `helm template` validation) ; `kafka-topics.sh` accessible si on veut inspecter les topics localement (optionnel).

### Avant ouverture de chaque sous-PR

- `git diff --stat frontend/` strictement **vide**.
- `git diff --stat openapi/` strictement **vide** (le contrat OpenAPI ne change pas — invariant).
- Le service extrait par cette PR a son propre `pom.xml`, son propre dossier `db/migration/V1__...sql`, son propre Dockerfile (via Jib config dans `application.properties`).
- `legacy-monolith/pom.xml` — les modules supprimés ne sont plus listés dans les `<dependency>`.
- `k8s/chart/templates/kong/configmap-routes.yaml` — la (les) route(s) du service ont basculé.
- `k8s/chart/templates/api/` reste tant que tous les services ne sont pas extraits (étape 15 le supprime).

### Avant ouverture de la PR finale (consolidation)

- `legacy-monolith` supprimé (`git diff --stat backend/services/legacy-monolith/` montre uniquement des suppressions).
- `k8s/chart/templates/api/` supprimé.
- Tous les SonarCloud projectKey par service au vert (≥ 80 % couverture, ratings A, < 3 % duplication).
- E2E happy path test vert.

### Ouverture PR

1. `gh pr create` exécuté **depuis `/workspace`** dans le devcontainer.
2. Le body PR transite par fichier dédié pour éviter les soucis d'échappement de heredoc :
   ```bash
   cat /tmp/pr-body-microservices.md \
       | docker exec -i unige-events-app-1 bash -c "cat > /tmp/pr-body-microservices.md"
   docker exec -w /workspace unige-events-app-1 bash -c \
       "gh pr create --title 'refactor(backend): migrate to microservices architecture with Kong gateway and Kafka broker' \
                     --body-file /tmp/pr-body-microservices.md \
                     --base main --head 'refactor(backend): migrate-to-microservices'"
   ```
3. **Titre PR final EXACT** (à copier-coller, validé par `pr-title-check.yml`) :
   ```
   refactor(backend): migrate to microservices architecture with Kong gateway and Kafka broker
   ```

### Reviewer Copilot

```bash
gh pr edit <PR_NUM> --add-reviewer copilot-pull-request-reviewer
```
Si l'app n'est pas configurée comme collaborator, fallback :
```bash
gh pr comment <PR_NUM> --body "@copilot review please"
```

### Traitement commentaires Copilot

- Récupérer : `gh api repos/unige-pinfo6-2026/unige-events/pulls/<PR_NUM>/comments --paginate`.
- Pour CHAQUE commentaire :
  - Juger pertinence (alignement avec les conventions projet et les décisions tranchées de cette spec).
  - Si pertinent → corriger dans un commit `fix(backend): …` + push + **répondre au commentaire** avec un lien vers le SHA via `gh api -X POST repos/.../pulls/<PR_NUM>/comments/{id}/replies`.
  - Si non-pertinent → **répondre poliment** en justifiant pourquoi la remarque n'est pas appliquée (cite la décision de la spec qui tranche).
  - **Ne jamais ignorer silencieusement un commentaire.**

### Surveillance CI

```bash
gh pr checks <PR_NUM> --watch
```
Jusqu'à **toutes vertes** ET **SonarCloud Quality Gate vert sur chaque projectKey de service**. Si une check échoue :
- Lire les logs : `gh run view <RUN_ID> --log-failed`.
- **Fix root cause** : pas de `--no-verify`, pas de `@Disabled`, pas de skip de check sous prétexte de fix « ultérieur ».
- Commit + push, surveiller à nouveau.

### Ne PAS merger

L'utilisateur (Dany / Elie) merge lui-même après validation finale et démonstration soutenance.

---

## Critères de done

- [ ] Branche `refactor(backend): migrate-to-microservices` créée depuis `origin/main` avec `--no-track`.
- [ ] **Aucune modification d'`openapi/openapi.yaml`** — `git diff --stat openapi/` strictement **vide**.
- [ ] **Aucune modification frontend** — `git diff --stat frontend/` strictement **vide** ; `frontend/skeleton/`, `frontend/src/`, `frontend/docs/`, `frontend/package.json` — tous inchangés.
- [ ] 14 microservices créés sous `backend/services/<service>/` (sauf `notification-service` placeholder vide jusqu'à SCRUM-99) avec POM, code Java, migration Flyway V1, application.properties, tests.
- [ ] `backend/services/legacy-monolith/` supprimé (étape 15).
- [ ] Helm chart enrichi : 14 sous-templates `<service>/` + `kong/` + `kafka/` ; `templates/api/` supprimé ; `Chart.yaml` version bumpée.
- [ ] Kong DB-less : `k8s/chart/templates/kong/configmap-routes.yaml` mappe chaque path OpenAPI vers le bon service amont (cf. tableau décision 6) ; plugins activés (cors, correlation-id, prometheus, rate-limiting).
- [ ] Kafka KRaft single-broker déployé ; les 10 topics créés au démarrage par Job d'init ; producteurs et consommateurs branchés (cf. tableau § 4.5).
- [ ] **Auth Auth0 / OIDC fonctionnelle sur chaque service** : `quarkus-oidc` configuré identique au monolithe ; claim `https://quarkus-security.com/roles` lue via `quarkus.oidc.roles.role-claim-path` ; `@RolesAllowed("ADMIN")` continue de fonctionner ; `GET /users/me` retourne 401 si token absent/invalide ; `quarkus.oidc.enabled=false` en `%test` préservé.
- [ ] **Migrations Flyway tracées** : V1..V17 monolithe inchangées ; chaque service possède son `V1__extract_<service>_schema.sql` qui ALTER TABLE SET SCHEMA + crée rôle + insère baseline `flyway_schema_history` ; `quarkus.flyway.baseline-on-migrate=true` + `baseline-version=0` préservé par service ; pas de `quarkus.flyway.clean-*`.
- [ ] **Couverture SonarCloud ≥ 80 % par service** sur le nouveau code, duplication ≤ 3 %, ratings A (Security, Reliability, Maintainability, Security Review). Chaque service a son propre `sonar.projectKey` (cf. décision 25).
- [ ] **Cascade SCRUM-136 préservée** via REST sync : `comment-service`, `attendance-service`, `stats-service` appellent `co-organizer-service` GET `/events/{id}/co-organizers/check?userId=<uuid>` pour la cascade `isCreatorOrAcceptedCoOrganizer`.
- [ ] **Cascade ISSUE-92 préservée** : `comment-service`, `attendance-service`, `stats-service`, `report-service` appellent `event-service` GET `/events/{id}` qui applique l'anti-oracle ; les 404 remontent envelope identique au caller.
- [ ] **Schedulers** : `EventExpirationJob` réaffecté à `event-service` (`replicas: 1`) ; `ModerationCleanupJob` réaffecté à `report-service` (`replicas: 1`) ; cron + timezone `Europe/Zurich` préservés.
- [ ] **Tests** : unit per-service ≥ 80 % couverture (JaCoCo) ; integration per-service avec DevServices PostgreSQL + Kafka Testcontainers ; contract tests Pact JSON commités dans `backend/contract-tests/pacts/<consumer>-<provider>.json` ; E2E happy path test (RestAssured) vert.
- [ ] **`pom.xml` racine** : multi-module avec parent + 14 sous-modules services (+ 1 module `shared` optionnel pour DTOs partagés).
- [ ] **`./mvnw verify`** à la racine `backend/` vert (matrix builde tous les services en parallèle).
- [ ] **CI/CD** : pipeline `build.yml` refondé en strategy matrix (cf. décision 17) ; `deploy.yml` adapté avec `--set image.<service>.tag=$SHA` pour chaque service ; pipelines vertes.
- [ ] **Conventions AGENTS.md respectées** : camelCase partout, booléens sans `is`, constructor injection, `@Transactional` sur mutations, doc dans le même commit.
- [ ] **PR ouverte**, **titre EXACT** : `refactor(backend): migrate to microservices architecture with Kong gateway and Kafka broker`.
- [ ] **Body PR** conforme `.github/pull_request_template.md` (sections obligatoires Résumé / Changements / Tests / Test plan / Documentation).
- [ ] **Reviewer Copilot demandé**.
- [ ] **Tous les checks GitHub Actions verts** (`Lint PR title`, build par service ×14, build frontend, deploy preview, Sonar par service ×14) + **SonarCloud Quality Gate vert pour chaque service**.
- [ ] **PR non mergée** par l'agent — l'utilisateur (Dany / Elie) merge lui-même après démonstration soutenance.

---

## Interdits stricts

- ❌ PAS de modification d'`openapi/openapi.yaml` — le contrat reste byte-pour-byte identique (cf. décision 24).
- ❌ PAS de modification frontend — `git diff --stat frontend/` strictement vide.
- ❌ PAS de modification des migrations V1..V17 historiques (immutables — cf. [`backend/AGENTS.md`](backend/AGENTS.md) et la leçon Flyway-immutabilité documentée dans [`specs_scrum-139.md` lignes 32-49](specs_archives/specs_claude/specs_scrum-139.md#L32-L49)).
- ❌ PAS de FK SQL cross-service — toutes les FK `@ManyToOne` cross-service sont transformées en pointeurs Long/UUID bruts (cf. décision 5).
- ❌ PAS de Spring Cloud, NGINX-as-gateway, Traefik, Envoy/Istio en remplacement de Kong (Kong imposé par le brief Agon).
- ❌ PAS de RabbitMQ, NATS, Redis Streams en remplacement de Kafka (Kafka imposé par le brief Agon).
- ❌ PAS de Schema Registry Avro/Protobuf en S8 (cf. décision 15 — JSON Jackson direct).
- ❌ PAS de bases physiquement séparées (1 instance PostgreSQL partagée avec N schémas — cf. décision 8).
- ❌ PAS de plugin Kong-OIDC qui valide le JWT en lieu et place des services (chaque service revalide localement — cf. décision 7).
- ❌ PAS d'OpenTelemetry / Jaeger / Tempo en S8 (cf. décision 19 — minimum viable : logs JSON + X-Request-ID).
- ❌ PAS d'agrégation SonarCloud cross-service (chaque service a son projectKey indépendant — cf. décision 25).
- ❌ PAS de leader-election multi-instance pour les schedulers en S8 (`replicas: 1` strict sur les services qui hébergent un `@Scheduled` — cf. décision 10).
- ❌ PAS de DLQ Kafka explicite en S8 (cf. décision 26 — retry quarkus-smallrye + log + skip ; DLQ S9+).
- ❌ PAS de duplication de logique métier entre services (single source of truth = service propriétaire ; les autres lisent via REST sync ou consomment Kafka).
- ❌ PAS de NetworkPolicies K8s en S8 (cf. décision 16 — sécurité réseau S9+).
- ❌ PAS de TODO commenté dans le code livré.
- ❌ PAS de `--no-verify`, pas de `@Disabled`, pas de skip de check CI sous prétexte de fix « ultérieur ».
- ❌ PAS de force-push sur la branche pendant la review (utiliser des commits additifs).
- ❌ PAS de merge de la PR par l'agent — l'utilisateur (Dany / Elie) s'en charge après démonstration soutenance.

---

## Conventions à respecter

- camelCase partout en Java, JSON, OpenAPI. Hibernate convertit en snake_case côté DB.
- Pas de préfixe `is` sur les booléens d'**entités JPA** (n/a pour cette migration — aucune nouvelle entité créée).
- Constructor injection sur toutes les Resources et Services (pattern projet).
- `@Transactional` sur toutes les **mutations** Service. Lectures non-transactionnelles (sauf cas particuliers historiques type `EventService.getById`).
- Chaque service a son `quarkus.http.root-path=api` — paths JAX-RS relatifs (`/events`, `/users/me`, `/comments`, …).
- Chaque service a son `quarkus-oidc` configuré identique au monolithe (claim path, audience, application-type=service).
- Chaque service a son `quarkus-flyway` avec `baseline-on-migrate=true` et `baseline-version=0`.
- `quarkus.oidc.enabled=false` en `%test` préservé sur **chaque** service.
- `quarkus.scheduler.enabled=false` en `%test` préservé.
- Les codes HTTP existants restent **identiques** (200/201/204/400/401/403/404/409/422/429). Les envelopes `ApiErrorResponse{error, message}` restent **identiques**. Les codes d'erreur slug (`already_following`, `cannot_invite_self`, `recurrence_unbounded`, etc.) restent **identiques**.
- Helm sub-templates suivent le pattern existant (`templates/api/` comme modèle) — cohérence visuelle.
- CI matrix par service avec image-tag `${{ github.sha }}` (cohérent avec convention SCRUM existante).
- Doc mise à jour dans le **même commit** que le code correspondant (ou commit `docs(backend):` dédié dans la dernière PR).
- Commits atomiques `refactor(backend): …`, `chore(infra): …`, `test(backend): …`, `docs(backend): …`, `fix(backend): …`.
- Titre PR final EXACT : `refactor(backend): migrate to microservices architecture with Kong gateway and Kafka broker`.

---

## Changements frontend

**Conclusion attendue : `git diff --stat frontend/` strictement vide à l'ouverture de la PR finale.**

**Démonstration que c'est possible :**

1. **Le contrat `openapi/openapi.yaml` ne change pas (invariant strict, décision 24).** Chaque microservice expose la même sous-section qu'aujourd'hui — paths, méthodes, headers, codes HTTP, schémas request/response, exemples — byte-pour-byte identique. Le frontend voit toujours le même contrat.

2. **Kong route les paths existants** (cf. décision 6, table de routes). Pour le frontend, `POST /api/events/{id}/comments` reste `POST /api/events/{id}/comments` — peu importe que ce soit servi par le monolithe ou par `comment-service`. Pas de versioning d'API (`/v1/`, `/v2/`), pas de header custom de routing.

3. **La config Vite proxy `/api → api:8080`** (cf. [`frontend/AGENTS.md` ligne 144](frontend/AGENTS.md#L144)) reste **valide en local dev** pendant la coexistence (le monolithe `api` reste UP). En prod, l'Ingress route `/api` vers `kong-proxy:8000` en lieu et place de `api:8080` — transparent pour le navigateur (pas de changement DNS, pas de changement de host).

4. **Les types TypeScript dans [`frontend/src/types/`](frontend/src/types/)** — `Event`, `User`, `Faculty`, `EventStatus`, `AttendanceStatus`, `CoOrganizerStatus`, `ReportStatus`, `FollowStatus`, `RecurrenceFrequency`, `Comment`, `CommentDTO`, etc. — dérivent du contrat OpenAPI inchangé. Aucun rename, aucun ajout, aucun retrait.

5. **Les services Axios dans [`frontend/src/services/`](frontend/src/services/)** — `api.ts`, `tokenStore.ts`, `userService.ts`, `eventApi.ts`, `searchApi.ts`, `favoriteApi.ts` — appellent toujours les mêmes URLs, avec le même header `Authorization: Bearer <jwt>`, avec les mêmes attentes de réponse JSON.

6. **L'authentification Auth0 côté frontend** (Auth0 SPA SDK + `useAuth`) est **inchangée**. Le `access_token` JWT reste stocké en localStorage sous la clé `access_token` (cf. [`frontend/AGENTS.md` ligne 139](frontend/AGENTS.md#L139)). Auth0 reste le seul fournisseur d'identité ; ses URLs Authorization/Token/JWKS sont **inchangées**.

7. **Les skeletons `frontend/skeleton/*.bones.json`** — inchangés (rendent un layout, pas un endpoint).

8. **Les pages React, hooks, contexts, components, error boundaries, routes** — tout dans `frontend/src/` reste inchangé.

9. **`frontend/docs/`** — les docs frontend ne mentionnent pas la topologie backend (cf. [`frontend/docs/architecture.md`](frontend/docs/architecture.md)). Aucune mise à jour nécessaire.

**Test de garantie au moment de l'ouverture de la PR finale** :
```bash
git diff --stat origin/main...HEAD -- frontend/
# doit afficher : (rien) ou un ligne d'en-tête sans diff
```

Si une ligne du frontend a bougé, c'est qu'une décision tranchée de cette spec a été violée — bloquant pour la PR.

---

## Livrable FINAL attendu

### Titre PR EXACT

```
refactor(backend): migrate to microservices architecture with Kong gateway and Kafka broker
```

### Description PR (à coller dans le textarea — respecte strictement [`.github/pull_request_template.md`](.github/pull_request_template.md))

```markdown
## Résumé

Migration du backend UNIGE Events d'une architecture monolithique Quarkus vers
14 microservices déployés derrière une API Gateway Kong (DB-less) et reliés
par un broker Kafka (KRaft single-broker) pour les fan-outs asynchrones.
Chaque service possède son propre schéma PostgreSQL (1 instance partagée, N
schémas avec rôles RBAC dédiés), ses migrations Flyway dédiées, sa propre
image Docker et son propre projectKey SonarCloud. Le contrat
`openapi/openapi.yaml` reste **byte-pour-byte identique** ; le frontend
n'est **pas** modifié (`git diff --stat frontend/` strictement vide).
La cascade d'autorisation SCRUM-136
(`isCreatorOrAcceptedCoOrganizerPublic`) et l'anti-oracle ISSUE-92 sont
préservés via REST sync cross-service. Les schedulers `EventExpirationJob`
et `ModerationCleanupJob` sont réaffectés à leurs services propriétaires
(`event-service` et `report-service` respectivement). Les notifications
(SCRUM-99 hors scope) sont déjà émises sur Kafka — un futur
`notification-service` n'aura qu'à brancher son consumer.

## Changements

### Architecture
- 14 microservices : `user-service`, `event-service`, `attendance-service`,
  `favorite-service`, `view-service`, `co-organizer-service`,
  `comment-service`, `follow-service`, `report-service`, `stats-service`,
  `share-service`, `calendar-service`, `me-aggregator-service`, +
  `notification-service` placeholder (vide jusqu'à SCRUM-99).
- Backend Maven multi-module : parent POM + 14 sous-modules dans
  `backend/services/<service>/`.
- `legacy-monolith/` supprimé en fin de migration (étape 15).

### Kong API Gateway
- Mode DB-less, ConfigMap YAML statique pour la table de routes.
- Plugins globaux : `cors`, `correlation-id` (X-Request-ID), `prometheus`.
- Plugin par-route : `rate-limiting` (par-IP, fenêtre 1 min) sur
  `events.create` (10/min), `comments.post` (10/min), `follows.follow` (30/min).
- `replicas: 2` pour HA basique.

### Kafka broker
- KRaft single-broker (apache/kafka:3.x), `replicas: 1`, PVC 5Gi,
  `retention.ms = 7 days`, `replication-factor = 1`.
- 10 topics créés au démarrage par Job d'init :
  `events.published`, `events.cancelled`, `events.banned`, `events.expired`,
  `users.followed`, `users.follow-requested`, `users.follow-accepted`,
  `comments.created`, `co-organizers.invited`, `co-organizers.accepted`.
- `notifications.events` topic logique (fan-in alias).

### Base de données
- 1 instance PostgreSQL 16 (existante), 9 schémas dédiés
  (`user_svc`, `event_svc`, `attendance_svc`, `favorite_svc`, `view_svc`,
  `co_organizer_svc`, `comment_svc`, `follow_svc`, `report_svc`).
- Rôles RBAC dédiés par service (un service ne peut SQL que son schéma).
- Migrations historiques V1..V17 distribuées via `ALTER TABLE ... SET SCHEMA`
  + insert baseline dans `flyway_schema_history` pour court-circuiter
  Flyway sur le schéma déplacé.

### Helm chart
- `Chart.yaml` v0.1.0 → v0.2.0.
- 14 sous-templates `templates/<service>/` (Deployment + Service ClusterIP).
- 2 sous-templates infra `templates/kong/` + `templates/kafka/`.
- `templates/ingress/ingress.yaml` : `/api/*` route maintenant vers `kong-proxy:8000`.
- `templates/api/` supprimé en étape 15.

### CI/CD
- `build.yml` refondé en strategy matrix `service in [...]` — chaque service
  builde indépendamment et push une image Docker tagguée `${{ github.sha }}`.
- `deploy.yml` adapté : `--set image.<service>.tag=$SHA` pour chaque service.
- `pr-title-check.yml` inchangé (validation conventional commits).
- SonarCloud : un projectKey par service
  (`unige-events-backend-<service-name>`).

### Auth Auth0 / OIDC
- `quarkus-oidc` activé sur **chaque** service en mode `service`.
- Claim path `https://quarkus-security.com/roles` lue identiquement.
- `@RolesAllowed("ADMIN")` continue de fonctionner sans changement.
- `quarkus.oidc.enabled=false` en `%test` préservé sur chaque service.
- `GET /users/me` retourne 401 si token absent/invalide (règle critique).

### Tests
- Unit per-service (JUnit 5) — couverture ≥ 80 % JaCoCo.
- Integration per-service (`@QuarkusTest` + DevServices PostgreSQL +
  Kafka Testcontainers).
- Contract tests Pact JVM (brokerless, fichiers JSON commités) :
  consommateurs définissent les contrats des providers.
- E2E happy path test (RestAssured) : 1 scénario user crée event → invite
  co-org → accept → inscription → comment → ban admin.

### Observabilité
- Logs JSON sur stdout (`quarkus-logging-json`).
- `X-Request-ID` propagé par Kong + lu via `RequestIdFilter` (déjà existant
  côté monolithe), poussé en MDC, propagé aux REST clients amont.
- Métriques Prometheus sur `/q/metrics` (interne) + plugin Kong
  `prometheus` agrégé.
- Pas d'OpenTelemetry / Jaeger en S8 (S9+).

### Documentation
- `backend/docs/architecture.md` : nouvelle topologie 14 services + Kong + Kafka,
  diagramme texte, table services / responsabilités / topics.
- `backend/docs/data-model.md` : pour chaque entité, son service propriétaire +
  son schéma DB.
- `backend/docs/api-contract.md` : colonne « Service amont » ajoutée à la grande
  table des endpoints.
- `backend/docs/dev-guide.md` : workflow dev local — `docker-compose -f
  docker-compose.dev.yml up` pour lancer Kong + Kafka + 14 services.
- `backend/docs/sprint-context.md` : bloc `## Sprint 8 — Migration
  microservices`.
- `AGENTS.md` racine : référence à la nouvelle topologie.
- `backend/AGENTS.md` : section architecture par service.

## Tests

- 14 × `./mvnw verify` (matrix CI) verts ; couverture JaCoCo ≥ 80 % par service.
- Pact JSON validés provider-side (chaque service vérifie les contracts
  définis par ses consommateurs).
- E2E happy path test (RestAssured) vert sur preview namespace.
- Smoke manuel sur preview deploy avant soutenance :
  - `curl https://pr-N.../api/users/me` → 401 si pas de token
  - `curl -H "Authorization: Bearer <token>" https://pr-N.../api/users/me` → 200 + profil
  - Création d'event → publication → inscription → comment → ban admin
  - `kubectl logs -l app=kong | grep req=<id>` retrouve la requête

Lancer : `./mvnw verify` à la racine `backend/` (matrix multi-module) via
devcontainer Quarkus DevServices PostgreSQL + Kafka Testcontainers.

## Test plan

- [ ] `./mvnw verify` à la racine `backend/` vert (matrix multi-module).
- [ ] `git diff --stat frontend/` strictement vide.
- [ ] `git diff --stat openapi/` strictement vide.
- [ ] `git diff --stat backend/services/legacy-monolith/` montre uniquement des suppressions (étape 15).
- [ ] 14 SonarCloud Quality Gate verts (un par service).
- [ ] Helm chart : `helm template ./k8s/chart` valide ; `helm lint` vert.
- [ ] Preview deploy : tous pods Ready ; tous endpoints OpenAPI accessibles via Kong.
- [ ] Smoke manuel preview :
  - [ ] `GET /api/users/me` 401 sans token, 200 avec.
  - [ ] `POST /api/events` (avec token) → 201 + event créé.
  - [ ] `POST /api/events/{id}/comments` → 201 + comment créé ; Kafka topic `comments.created` reçoit le message (`kubectl exec kafka -- kafka-console-consumer ...`).
  - [ ] `POST /api/events/{id}/attend` → 200 ; nouveau row dans `attendance_svc.attendances`.
  - [ ] `PATCH /api/admin/reports/{id}` (admin token) → état `REVIEWED` ; topic `events.banned` reçoit ; `event-service` consomme et passe l'event en BANNED ; `GET /api/events/{id}` 404 anti-oracle.
  - [ ] `GET /api/calendar/<token>.ics` → flux ICS contient les events ATTENDING + favoris (cross-service `calendar-service` → `attendance-service` + `favorite-service` + `event-service`).
  - [ ] Schedulers : `EventExpirationJob` tourne dans `event-service` ; `ModerationCleanupJob` tourne dans `report-service` ; `replicas: 1` strict.
- [ ] `gh pr checks` — toutes vertes.
- [ ] E2E happy path test (RestAssured) vert.
- [ ] Review Copilot demandée et **chaque commentaire traité** (apply OU justifié).

## Documentation

- [x] `backend/docs/architecture.md` — réécriture complète : 14 services, Kong, Kafka, diagramme.
- [x] `backend/docs/data-model.md` — colonne « Service propriétaire » + schéma DB par entité.
- [x] `backend/docs/api-contract.md` — colonne « Service amont » par endpoint.
- [x] `backend/docs/dev-guide.md` — `docker-compose -f docker-compose.dev.yml up` ; lancer un service en isolation.
- [x] `backend/docs/sprint-context.md` — bloc `## Sprint 8 — Migration microservices`.
- [x] `AGENTS.md` racine — référence topologie.
- [x] `backend/AGENTS.md` — architecture par service.
- [x] **`openapi/openapi.yaml` strictement inchangé** (invariant — cf. décision 24 spec).

<!-- Optionnel : Why / Motivation -->
## Why / Motivation

Le brief Agon (cours pinfo6) impose en S8 la migration vers une architecture
microservices avec Kong + Kafka. L'objectif pédagogique : démontrer une
décomposition par bounded context, des fan-outs asynchrones, et un découplage
de déploiement par service. La migration préserve **strictement** le contrat
client (frontend inchangé, OpenAPI inchangé, Auth0 fonctionnelle sur chaque
service) — l'extraction est une refonte interne, pas une refonte produit.
Les schedulers, les cascades d'autorisation cross-service, les codes
d'erreur, et les conventions AGENTS.md sont **tous** préservés.

<!-- Optionnel : Dépendances / ordre de merge -->
## Dépendances / ordre de merge

Aucune dépendance amont au sens strict. La migration est livrée en **N
sous-PRs sur la branche persistante** `refactor(backend): migrate-to-microservices`
(pattern strangler fig — cf. décision 20) puis consolidée par cette PR
finale contre `main`. Ordre des sous-PRs (cf. § Ordre d'implémentation) :
fondations Kong+Kafka → multi-module Maven → 13 extractions service par
service (`share` → `view` → `favorite` → `calendar` → `follow` → `comment`
→ `co-organizer` → `attendance` → `report` → `stats` → `me-aggregator` →
`user` → `event`) → suppression `legacy-monolith` → docs finales.

**Hors scope** : `notification-service` (SCRUM-99 — l'entité `Notification`
n'est pas implémentée, mais les events Kafka sont déjà émis pour qu'un
futur consumer puisse simplement brancher).
**Hors scope** : NetworkPolicies K8s (S9+).
**Hors scope** : OpenTelemetry / Jaeger (S9+).
**Hors scope** : Schema Registry Avro/Protobuf (S9+ si besoin).
**Hors scope** : DLQ Kafka explicite (S9+).
**Hors scope** : leader-election multi-instance des schedulers (`replicas: 1`
strict en S8).

<!-- Optionnel : Décisions techniques tranchées -->
## Décisions techniques tranchées

Toutes les décisions sont consignées dans
[`specs_archives/specs_claude/specs_microservices_migration.md`](specs_archives/specs_claude/specs_microservices_migration.md).
Highlights :
- Kong DB-less avec ConfigMap statique de routes (cf. décision 2 + 6).
- Kafka KRaft single-broker, 10 topics JSON Jackson sans Schema Registry
  (cf. décisions 3 + 15 + 26).
- 14 services par bounded context (cf. décision 4) + 1 BFF
  `me-aggregator-service` pour les paths multi-domaines.
- Schéma-par-service dans une instance PostgreSQL partagée (RBAC strict)
  — pas de bases physiquement séparées (cf. décision 8).
- Migrations Flyway distribuées via `ALTER TABLE SET SCHEMA` + baseline
  `flyway_schema_history` (cf. décision 9).
- `quarkus-oidc` activé sur chaque service (validation locale du JWT) —
  Kong forwarde le JWT brut sans transformation (cf. décision 7).
- Cascade d'autorisation cross-service via REST sync (pas Kafka) —
  préserve la sémantique 403/404 (cf. décisions 12 + 13).
- Schedulers réaffectés au service propriétaire (`replicas: 1`,
  pas de leader-election en S8 — cf. décision 10).
- REST sync pour les requêtes utilisateur ; Kafka pour les fan-outs
  asynchrones (cf. décision 11).
- `quarkus-rest-client-reactive` + `quarkus-smallrye-fault-tolerance`
  (`@Retry`/`@Timeout`/`@CircuitBreaker`/`@Fallback`) — cf. décisions
  11 + 30.
- Tests : unit + integration per-service, contract tests Pact JVM
  brokerless, 1 E2E happy path (cf. décision 18).
- Observabilité minimum : logs JSON + `X-Request-ID` Kong → MDC →
  REST clients ; Prometheus ; pas d'OpenTelemetry en S8 (cf. décision 19).
- Stratégie strangler fig : extraction service par service avec
  monolithe UP en parallèle (cf. décision 20).
- Couverture SonarCloud ≥ 80 % par service indépendamment ;
  pas d'agrégation cross-service (cf. décision 25).
- Frontend : `git diff --stat frontend/` strictement vide ;
  contrat OpenAPI strictement inchangé (cf. décisions 24 + section
  « Changements frontend »).

<!-- Optionnel : Notes pour le reviewer -->
## Notes pour le reviewer

- Cette PR est une **consolidation** — le diff cumulé est conséquent (14 services
  créés). La review section par section est recommandée :
  1. Helm chart (`k8s/chart/`) — Kong + Kafka + sous-templates par service.
  2. CI/CD (`.github/workflows/`) — refonte build.yml en matrix.
  3. Backend par service — un dossier `backend/services/<service>/` à la fois.
  4. Documentation (`backend/docs/`) — réécriture architecture.md.
- L'invariant le plus important à vérifier : `git diff --stat openapi/` ET
  `git diff --stat frontend/` doivent **tous deux** être strictement vides.
  C'est la garantie que le frontend / les consommateurs externes / le contrat
  client n'ont pas dérivé.
- La cascade SCRUM-136 (`isCreatorOrAcceptedCoOrganizerPublic`) est
  matérialisée en REST sync cross-service vers le nouvel endpoint **interne**
  `co-organizer-service` GET `/events/{id}/co-organizers/check?userId=<uuid>`.
  Cet endpoint n'est **pas** documenté dans `openapi/openapi.yaml` (c'est
  une API privée backend) ; sa présence est documentée uniquement dans
  `backend/docs/architecture.md`.
- L'anti-oracle ISSUE-92 (DRAFT/CANCELLED/BANNED non-créateur → 404) est
  préservé : `comment-service`, `attendance-service`, `stats-service`,
  `report-service` appellent tous `event-service` GET `/events/{id}` qui
  applique l'anti-oracle ; la 404 remonte avec envelope identique.
- Les schedulers tournent en `replicas: 1` strict — la HA des jobs est S9+.
- Aucune dépendance Maven non-Quarkus n'est introduite (cf. décision 30).
- Le pattern « pseudo-baseline Flyway » (cf. décision 9) n'altère pas les
  fichiers V1..V17 historiques — il insère des rows dans
  `flyway_schema_history` du schéma extrait pour court-circuiter Flyway.
  Conforme à la règle « migration committée = immutable » et à la leçon
  documentée dans `specs_scrum-139.md` lignes 32-49.
```

---

## Prompt de lancement d'implémentation

```
Tu vas implémenter la migration backend du projet UNIGE Events vers une
architecture microservices. La spec d'implémentation complète et figée vit
dans `specs_archives/specs_claude/specs_microservices_migration.md` —
c'est la **source unique de vérité**. Toute déviation par rapport à
cette spec doit être justifiée auprès de l'utilisateur AVANT exécution.

## Working directory et environnement

- Working directory : `/workspace` dans le devcontainer Linux Debian (host : MAC via SSH).
- Java 21 absent du host → tout `./mvnw verify` / `mvn` / `gh` / `git` lourd passe par :
  `docker exec -w /workspace unige-events-app-1 bash -c "cd /workspace/backend && ./mvnw verify"`.
- Helm CLI ≥ 3.12 disponible dans le devcontainer.

## Contexte projet à relire AVANT d'écrire la moindre ligne

1. `AGENTS.md`, `backend/AGENTS.md`, `frontend/AGENTS.md`, `backend/CLAUDE.md`,
   `frontend/CLAUDE.md` — règles d'or projet (camelCase, pas de préfixe `is`
   sur entités, openapi-first, Flyway immutable, conventions PR).
2. `backend/docs/architecture.md`, `backend/docs/data-model.md`,
   `backend/docs/api-contract.md`, `backend/docs/sprint-context.md`,
   `backend/docs/dev-guide.md`.
3. `frontend/docs/architecture.md`, `frontend/docs/components.md`,
   `frontend/docs/types.md`.
4. `openapi/openapi.yaml` — **invariant strict** (4076 lignes, ~50 paths) ;
   il ne change PAS pendant cette migration. Toute modification non-cosmétique
   est un défaut bloquant.
5. `specs_archives/specs_claude/specs_microservices_migration.md` — la spec, intégralement.
6. `specs_archives/specs_claude/specs_scrum-138.md`, `specs_scrum-139.md`,
   `specs_scrum-147.md` — références pattern (split en 2 Resources, FK
   `ON DELETE SET NULL` post-Copilot, helper testable hors Quarkus,
   workflow PR/Copilot/CI).
7. `k8s/chart/` (état actuel) — chart umbrella avec api/web/db/minio/cloudflared/ingress.
8. `.github/workflows/build.yml`, `deploy.yml`, `pr-title-check.yml`,
   `cleanup.yml`, `ci-cd.yml`.
9. `backend/src/main/java/ch/unige/events/` — 9 entités, 17 resources, 17
   services, 2 schedulers, 17 migrations Flyway.

## Branche cible

`refactor(backend): migrate-to-microservices` créée depuis `origin/main` avec `--no-track` (NON NÉGOCIABLE) :

```
git fetch origin && git checkout -b 'refactor(backend): migrate-to-microservices' origin/main --no-track
```

(NB : le caractère `(` dans le nom de branche est valide ; échapper / quoter selon le shell.)

## Ordre d'exécution strict (Étapes 0 → 16)

0. **Fondations Kong + Kafka + Helm umbrella** — créer `k8s/chart/templates/kong/`,
   `k8s/chart/templates/kafka/` ; basculer Ingress `/api/*` vers `kong-proxy:8000` ;
   table de routes Kong initiale = 100 % vers `api:8080` (monolithe). Vérifier
   preview deploy : `curl /api/users/me` → 401 (auth manquante = monolithe atteint).
   Commit : `chore(infra): add Kong API gateway and Kafka broker to helm chart`.
1. **Modularisation Maven** — convertir `backend/` en multi-module avec parent POM
   + `backend/services/legacy-monolith/` (existant déplacé) + 14 sous-modules vides.
   Le pipeline CI continue de builder le monolithe seul. Commit :
   `refactor(backend): convert to multi-module maven layout`.
2-14. **Extractions service par service** — 13 PRs dans l'ordre strict :
   share → view → favorite → calendar → follow → comment → co-organizer →
   attendance → report → stats → me-aggregator → user → event. Pour CHAQUE :
   - 4a. Créer le module Maven (POM, application.properties, classes Java).
   - 4b. Migration Flyway dédiée `V1__extract_<service>_schema.sql`.
   - 4c. Helm sub-template `<service>/` (Deployment + Service ClusterIP).
   - 4d. Basculer la route Kong dans `templates/kong/configmap-routes.yaml`.
   - 4e. Migration de données (`ALTER TABLE ... SET SCHEMA <service>_svc;`).
   - 4f. Supprimer le code Java migré du monolithe.
   - 4g. Tests : unit per-service + integration + contract Pact.
   - 4h. Vérifier preview deploy + smoke tests + E2E partiel.
   Commit : `refactor(backend): extract <service-name>`.
15. **Suppression `legacy-monolith` + `templates/api/`**. Commit :
    `refactor(backend): remove legacy monolith now that all services are extracted`.
16. **Documentation finale** — réécrire `backend/docs/architecture.md`,
    enrichir `data-model.md` et `api-contract.md`, ajouter bloc S8 dans
    `sprint-context.md`, enrichir `dev-guide.md`, mettre à jour AGENTS.md
    racine et `backend/AGENTS.md`. Commit :
    `docs(backend): document microservices architecture`.

À chaque étape, commit + push autorisés (et recommandés). Format commits :
`refactor(backend): …`, `chore(infra): …`, `test(backend): …`,
`docs(backend): …`, `fix(backend): …`.

## Contraintes

- **PAS de modification d'`openapi/openapi.yaml`** (`git diff --stat openapi/`
  strictement vide à la PR finale).
- **PAS de modification frontend** (`git diff --stat frontend/` strictement
  vide à la PR finale).
- **PAS de modification des migrations V1..V17 historiques** (immutables).
- **PAS de FK SQL cross-service** — toutes les FK `@ManyToOne` cross-service
  deviennent des pointeurs Long/UUID bruts (cf. décision 5 spec).
- **Kong DB-less + Kafka KRaft** — imposés par le brief Agon.
- **`quarkus-oidc` activé sur chaque service** — Kong ne valide pas le JWT
  en lieu et place des services (cf. décision 7 spec).
- **1 instance PostgreSQL partagée, N schémas** — pas de bases physiquement
  séparées (cf. décision 8 spec).
- **Schedulers `replicas: 1` strict** en S8 — pas de leader-election
  (cf. décision 10 spec).
- **Couverture SonarCloud ≥ 80 % par service** — un projectKey par service.
- **Conventions AGENTS.md respectées partout** : camelCase, booléens sans
  `is`, constructor injection, `@Transactional` sur mutations, doc dans
  le même commit que le code.
- **Hors scope** : `notification-service` (SCRUM-99 — placeholder vide),
  NetworkPolicies K8s, OpenTelemetry / Jaeger, Schema Registry Avro,
  DLQ Kafka explicite, leader-election multi-instance.

## Workflow PR / Copilot / CI

1. Stratégie : N sous-PRs (1 par étape) sous la branche persistante
   `refactor(backend): migrate-to-microservices` puis 1 PR finale de
   consolidation contre `main`.
2. **Titre PR final EXACT** :
   `refactor(backend): migrate to microservices architecture with Kong gateway and Kafka broker`
   (validé par `.github/workflows/pr-title-check.yml`).
3. Body PR final : copier-coller le bloc fourni dans la spec section
   « Livrable FINAL attendu » — respecte strictement
   `.github/pull_request_template.md`. Le body transite par
   `cat … | docker exec -i unige-events-app-1 bash -c "cat >
   /tmp/pr-body-microservices.md"` puis `gh pr create --body-file
   /tmp/pr-body-microservices.md` depuis le devcontainer.
4. Demander la review à Copilot :
   `gh pr edit <PR_NUM> --add-reviewer copilot-pull-request-reviewer`. Fallback
   si app non collaborator : `gh pr comment <PR_NUM> --body "@copilot review please"`.
5. Pour CHAQUE commentaire de Copilot :
   - Récupérer via `gh api repos/unige-pinfo6-2026/unige-events/pulls/<PR_NUM>/comments --paginate`.
   - Juger pertinence (alignement avec les conventions projet et les décisions
     tranchées de la spec).
   - Si pertinent → corriger dans un commit `fix(backend): …` + push +
     répondre au commentaire avec un lien vers le SHA via
     `gh api -X POST repos/.../pulls/<PR_NUM>/comments/{id}/replies`.
   - Si non-pertinent → répondre poliment en justifiant pourquoi la remarque
     n'est pas appliquée (citer la décision de la spec qui tranche).
   - **Ne jamais ignorer silencieusement un commentaire.**
6. Surveiller la CI : `gh pr checks <PR_NUM> --watch`. Si une check échoue,
   lire les logs (`gh run view <RUN_ID> --log-failed`), corriger la cause
   **racine** (PAS de `--no-verify`, PAS de skip, PAS de `@Disabled`),
   commit + push, surveiller à nouveau jusqu'à ce que **toutes** les checks
   soient vertes ET que les Quality Gate Sonar par service soient verts.
7. **Ne PAS merger** la PR — l'utilisateur (Dany / Elie) s'en charge après
   démonstration soutenance.

## Critères de done (rappel)

- [ ] Branche `refactor(backend): migrate-to-microservices` créée depuis
  `origin/main` avec `--no-track`.
- [ ] `git diff --stat openapi/` strictement vide.
- [ ] `git diff --stat frontend/` strictement vide.
- [ ] 14 microservices créés sous `backend/services/<service>/`,
  `legacy-monolith/` supprimé.
- [ ] Helm chart enrichi (Kong + Kafka + 14 sous-templates) ;
  `templates/api/` supprimé.
- [ ] Kong DB-less avec table de routes complète ; plugins activés.
- [ ] Kafka KRaft + 10 topics ; producteurs/consommateurs branchés.
- [ ] Auth0/OIDC fonctionnelle sur chaque service (`quarkus-oidc`,
  `@RolesAllowed`, `quarkus.oidc.enabled=false` en `%test`).
- [ ] Migrations Flyway tracées : V1..V17 historiques inchangées ;
  `V1__extract_<service>_schema.sql` par service.
- [ ] Schedulers réaffectés (`event-service` / `report-service`,
  `replicas: 1`).
- [ ] Cascade SCRUM-136 + anti-oracle ISSUE-92 préservés via REST sync.
- [ ] CI matrix par service ; Sonar par service ≥ 80 % ;
  duplication ≤ 3 % ; ratings A.
- [ ] Tests unit + integration + Pact + 1 E2E happy path verts.
- [ ] `./mvnw verify` à la racine `backend/` vert.
- [ ] Documentation finale mise à jour.
- [ ] PR ouverte, titre EXACT, body conforme template, Copilot reviewer demandé.
- [ ] Tous les checks GitHub Actions verts + SonarCloud Quality Gate vert
  par service.
- [ ] PR **non mergée** — Dany / Elie merge lui-même après démonstration soutenance.

Procède maintenant. Reporte ton avancement à chaque étape complétée.
```
