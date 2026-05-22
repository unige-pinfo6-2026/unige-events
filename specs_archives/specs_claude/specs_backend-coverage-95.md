# SPEC — Couverture de tests backend ≥ 95 % (PR de tests pure)

| Champ | Valeur |
|---|---|
| Périmètre | **Backend uniquement** — élever la couverture de tests **agrégée** (lignes, JaCoCo/Sonar sur les 15 modules) de **~89 % à ≥ 95 %**, en **AJOUTANT** des tests. Aucune modif de code applicatif. |
| Branche proposée | `feature/backend-coverage-95` (cible PR : `main`). Branche de travail effective : `claude/relaxed-shannon-4SWbr`. |
| Base | `origin/main` — tip à la rédaction : `3d15582` (Merge PR #198 `feature/profile-feed-participations-fixes`). |
| Sprint | S10 — durcissement de la couverture (suite S9/PR #198). |
| Objectif chiffré | **Couverture de lignes agrégée ≥ 95 %** (rapport Sonar de la PR). Cible secondaire : ne pas régresser la couverture de branches. |
| Tickets liés | Aucun ticket fonctionnel (dette de test). PR de type `test`. |
| Auteur spec | Elie Bussod (rédaction assistée Claude). |
| Date | 2026-05-22 |
| Règle d'or | **Aucune modif de code applicatif** ; on **AJOUTE** des tests. **Tests DRY** (helpers + `@ParameterizedTest`) — le gate SonarCloud impose **duplication ≤ 3 %** et **couverture ≥ 80 %** sur le **nouveau code**. Si un test révèle un **bug applicatif**, le **SIGNALER** (ne pas corriger sans accord). |

> **IMPÉRATIF de livrable.** Cette spec est **spec-only**. L'implémentation ultérieure produira **un seul** flux de travail (une branche, une PR) ; **aucune autre spec** ne doit être créée. Tous les chemins ci-dessous sont **vérifiés** sur la structure microservices réelle **post-PR #158** (5 services sous `backend/services/<svc>-service/` + 10 shared libs sous `backend/shared/<lib>/`) — **ne jamais** réutiliser les chemins monolithe `backend/src/main/...` des vieilles specs (obsolètes).

---

## 1. Vue d'ensemble

Le backend est **déjà très bien testé** (ratio test/main de 1,6 à 2,7 selon le module). Le passage de ~89 % à ≥ 95 % ne demande **aucune réécriture** : il se joue sur (a) une poignée de **classes à 0 %** (fines), et (b) des **branches d'erreur / fallback / anti-oracle** non exercées dans les gros services. Le gisement dominant est **event-service** (plus gros dénominateur) et, côté shared, **`domain-dtos`**.

| Module | mainLOC | État (mesuré ou estimé) | Nature des trous |
|---|---|---|---|
| `services/event-service` | 4261 | bien testé, dominant l'agrégat | **3 resources à 0 %** + `EventService.uploadImage` non couvert + qq branches |
| `services/engagement-service` | 2187 | très bon | branches de `AttendanceService` / `CommentService` (fallback, terminal, mentions) |
| `services/user-service` | 2266 | très bon | branches de conflit/optimistic-lock de `UserService` / `CalendarService` |
| `services/moderation-service` | 911 | très bon | 3 bras `catch` distincts (JSON, `InterruptedException`, `NotFoundException`) |
| `services/notification-service` | 1143 | excellent (event-driven) | 1 deserializer sans test dédié + branches-garde de 3 consumers à confirmer |
| `shared/domain-dtos` | — | **MESURÉ 81,0 % L / 33,3 % B** ⚠️ | `BearerTokenClientFilter` à **0 %** + ctors back-compat |
| `shared/domain-projections` | — | **MESURÉ 94,1 % L / 87,5 % B** | 1 branche `CallerIdentity` |
| `shared/jaxrs` | — | **MESURÉ 100 % L / 92,9 % B** | 2 branches isolées |
| `shared/tracing` | — | **MESURÉ 100 % L / 90,0 % B** | 3 branches isolées |
| `shared/api-error` · `domain-enums` · `kafka-events` · `platform` · `rate-limit` · `storage` | — | **MESURÉ 100 % L** | **RIEN À FAIRE** |

**Mesure réelle des 10 shared libs** (exécutées localement, voir §2) : **97,5 % L / 93,2 % B agrégé**, soit **13 lignes** et **14 branches** manquées au total — l'essentiel concentré dans `domain-dtos`.

---

## 2. Contexte

### 2.1 Pourquoi 95 %

L'agrégat backend stagne autour de **89 %** (chiffre équipe/CI). Le seuil dur SonarCloud reste à 80 %, mais l'équipe vise **≥ 95 %** comme objectif de qualité interne. La dette n'est pas structurelle (le code est testable et largement testé) : il reste surtout des **chemins d'erreur** rarement écrits et quelques **classes utilitaires fines** jamais ciblées.

### 2.2 Comment c'est mesuré

- Couverture produite par l'extension **`quarkus-jacoco`** → un rapport par module dans `target/jacoco-report/jacoco.xml` (cf. `sonar.coverage.jacoco.xmlReportPaths` dans [`backend/pom.xml`](/backend/pom.xml)).
- **Aucune exclusion** JaCoCo/Sonar n'existe (ni dans les poms, ni dans les `application.properties`) → **tout** compte (DTO, records, mappers inclus).
- L'agrégat Sonar est calculé par le job CI **`sonar-aggregate`** ([`.github/workflows/build.yml`](/.github/workflows/build.yml)) qui **unit** les jacoco.xml des 15 modules (uploadés en artifacts par les jobs amont) dans le projet unique `unige-pinfo6-2026_unige-events-backend`.
- **Nuance d'agrégation** : Sonar fait l'**union** des rapports. Une classe de `shared/` exercée par un test de service est comptée couverte dans l'agrégat, même si le rapport local de la shared lib la montre partielle. Le **81 % local de `domain-dtos`** est donc un plancher module-local ; seules les classes **jamais exercées nulle part** (ex. `BearerTokenClientFilter`, 0 référence dans tout `*Test.java`) sont réellement à 0 % en agrégat.

### 2.3 CONTRAINTE — Docker

- **Les tests des 5 services exigent Docker** : Quarkus **DevServices** lance un PostgreSQL éphémère par service + un connecteur Kafka **in-memory** (`%test`). Sans Docker, `./mvnw verify` **ne peut pas** tourner localement.
- **Les 10 shared libs ont des tests pur-JUnit** (aucun `@QuarkusTest` avec datasource, aucun Testcontainers) → **testables/mesurables sans Docker**. C'est ainsi qu'ont été obtenus les chiffres réels du §1.
- **Conséquence** : le **% exact des services** se vérifie **EN CI** (matrix par service + `sonar-aggregate`), comme sur la PR #198. En local, on se limite à `./mvnw test-compile` (qui **passe** — 244 classes de test) + l'exécution des shared libs.

---

## 3. Décisions tranchées (NE PAS revisiter pendant l'implémentation)

### D1 — Type et titre de PR

**Titre : `test(backend): raise coverage toward 95%`** (type `test`, **scope libre**).

| Option | Verdict |
|---|---|
| `feat(scrum-XXX)` / `refactor(...)` / `perf(...)` | ❌ ces 3 types **exigent** un scope Jira `scrum-XXX` (validé par [`pr-title-check.yml`](/.github/workflows/pr-title-check.yml)) ; or il n'y a pas de ticket fonctionnel |
| **`test(backend): …`** | ✅ retenu — type `test`, scope libre autorisé ; décrit exactement la nature de la PR |
| `chore(backend): …` | ❌ techniquement valide mais `test` est plus précis et conventionnel pour de l'ajout de tests |

### D2 — Couvrir, ne pas exclure

**Aucune exclusion JaCoCo/Sonar ajoutée.** On augmente la couverture en **écrivant des tests**, pas en masquant des classes.

| Option | Verdict |
|---|---|
| Ajouter `quarkus.jacoco.excludes` / `sonar.coverage.exclusions` sur les DTO triviaux | ❌ change la config de mesure (effet de bord sur l'historique Sonar), masque au lieu de couvrir, et touche du code hors `src/test` |
| **Écrire les tests manquants** (records inclus si JaCoCo flague les accessors) | ✅ retenu — conforme à l'objectif « ajouter des tests », zéro modif de config |

### D3 — DRY (gate duplication ≤ 3 %)

Factoriser **systématiquement** : helpers de construction de DTO (cf. `event(...)` dans [`AttendanceServiceTest.java`](/backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/service/AttendanceServiceTest.java)), `@BeforeEach` pour le staging JWT, et **`@ParameterizedTest`** pour les familles de cas (statuts, timeframes, branches de fallback). Le gate SonarCloud **duplication ≤ 3 % sur le nouveau code** est bloquant — un copier-coller de blocs `given()…then()` le ferait échouer.

### D4 — Ce qu'on NE teste PAS

| Classe / catégorie | Raison |
|---|---|
| `@ConfigMapping` `AppConfig` (event-service, share/config, user-service) | interfaces de config sans corps exécutable — aucune ligne instrumentable utile |
| `EventBannedEventDeserializer` (event-service) | sous-classe à ctor `super(...)` trivial ; couverture incidente seulement |
| `UserMeClient` (domain-dtos) | interface REST client **sans méthode `default`** → aucun corps à couvrir |
| Records DTO sans logique déjà exercés indirectement | déjà couverts par les tests amont ; pas de branche à gagner |
| Code généré Panache / `values()` / `valueOf()` | déjà balayé par les sentinels existants |

> Exception : si JaCoCo flague l'**accessor généré** d'un record réellement à 0 % (ex. `CommentVisibilityProjection`), on ajoute **une** assertion d'accès — coût marginal, ferme la dernière ligne du module.

### D5 — Priorisation

**event-service** (plus gros dénominateur → plus gros impact agrégé par ligne couverte) **et `shared/domain-dtos`** (seul module mesuré sous 95 %) **d'abord**. Le reste = grappillage de branches.

---

## 4. Analyse de l'existant

### 4.1 À TESTER — module par module

> Légende : **[C]** créer un nouveau fichier de test · **[+]** compléter un test existant.

#### `shared/domain-dtos` — MESURÉ 81,0 % L / 33,3 % B (priorité)

| Cible | Action | Cas à couvrir |
|---|---|---|
| [`BearerTokenClientFilter`](/backend/shared/domain-dtos/src/main/java/ch/unige/events/shared/client/BearerTokenClientFilter.java) — **0/9 L, 0/8 B** | **[C]** `BearerTokenClientFilterTest` (JUnit pur) | `jwt.isResolvable()` true/false ; `token == null` ; `rawToken` null/blank ; header `Authorization` positionné. `Instance<JsonWebToken>` mocké (Mockito), `ClientRequestContext` mocké. |
| [`UserPublicResponse`](/backend/shared/domain-dtos/src/main/java/ch/unige/events/shared/domain/dto/UserPublicResponse.java) — 5/2 L | **[+]** [`UserPublicResponseTest`](/backend/services/user-service/src/test/java/ch/unige/events/user/dto/UserPublicResponseTest.java) | ctors back-compat **12-arg** et **11-arg** (lignes de délégation) + overload `anonymous(UUID, displayName, avatarUrl)`. |
| [`AttendanceDTO`](/backend/shared/domain-dtos/src/main/java/ch/unige/events/shared/domain/dto/AttendanceDTO.java) | **[+]** [`AttendanceDTOTest`](/backend/shared/domain-dtos/src/test/java/ch/unige/events/shared/domain/dto/AttendanceDTOTest.java) | ctor **canonique 8-arg** avec `username` non-null (le 7-arg back-compat est déjà couvert). |
| [`CommentVisibilityProjection`](/backend/shared/domain-dtos/src/main/java/ch/unige/events/shared/domain/dto/CommentVisibilityProjection.java) — 0/1 L | **[C]** `CommentVisibilityProjectionTest` (1 assertion d'accessor) | une instanciation + lecture d'un champ (record). Ferme la dernière ligne du module. |

#### `shared/domain-projections` — MESURÉ 94,1 % L / 87,5 % B

| Cible | Action | Cas |
|---|---|---|
| [`CallerIdentity`](/backend/shared/domain-projections/src/main/java/ch/unige/events/shared/domain/projections/CallerIdentity.java) | **[+]** [`CallerIdentityTest`](/backend/shared/domain-projections/src/test/java/ch/unige/events/shared/domain/projections/CallerIdentityTest.java) | branche `me != null && me.id() == null` (fournir un `UserPublicResponse` à `id` null). |

#### `shared/jaxrs` — MESURÉ 100 % L / 92,9 % B

| Cible | Action | Cas |
|---|---|---|
| [`EnumParamConverterProvider`](/backend/shared/jaxrs/src/main/java/ch/unige/events/shared/jaxrs/EnumParamConverterProvider.java) | **[+]** [`EnumParamConverterProviderTest`](/backend/shared/jaxrs/src/test/java/ch/unige/events/shared/jaxrs/EnumParamConverterProviderTest.java) | la branche manquante (1) du `getConverter`. |
| [`InternalTokenFilter`](/backend/shared/jaxrs/src/main/java/ch/unige/events/shared/jaxrs/InternalTokenFilter.java) | **[+]** [`InternalTokenFilterTest`](/backend/shared/jaxrs/src/test/java/ch/unige/events/shared/jaxrs/InternalTokenFilterTest.java) | la branche manquante (1) du contrôle de token. |

#### `shared/tracing` — MESURÉ 100 % L / 90,0 % B

| Cible | Action | Cas |
|---|---|---|
| [`MdcKafkaProducerInterceptor`](/backend/shared/tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaProducerInterceptor.java) | **[+]** [`MdcKafkaProducerInterceptorTest`](/backend/shared/tracing/src/test/java/ch/unige/events/shared/tracing/MdcKafkaProducerInterceptorTest.java) | 2 branches restantes. |
| [`MdcKafkaConsumerInterceptor`](/backend/shared/tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaConsumerInterceptor.java) | **[+]** [`MdcKafkaConsumerInterceptorTest`](/backend/shared/tracing/src/test/java/ch/unige/events/shared/tracing/MdcKafkaConsumerInterceptorTest.java) | header présent mais `h.value() == null`. |

#### `services/event-service` (dominant)

| Cible | Action | Cas à couvrir |
|---|---|---|
| [`RedirectResource`](/backend/services/event-service/src/main/java/ch/unige/events/event/share/resource/RedirectResource.java) — **0 %** | **[C]** `RedirectResourceTest` (`@QuarkusTest` + RestAssured) | `GET /api/s/{shortCode}` → **302** + header `Location` = `{frontendUrl}/events/{id}` (mock `ShareService.resolveByShortCode`) ; **404** pour shortCode inconnu (anti-oracle). `@PermitAll` → pas de JWT. Désactiver le suivi de redirection RestAssured (`redirects().follow(false)`). |
| [`UserFavoritesResource`](/backend/services/event-service/src/main/java/ch/unige/events/event/favorite/resource/UserFavoritesResource.java) — **0 %** | **[C]** `UserFavoritesResourceTest` | `GET /api/users/me/favorites` → **200** (auth, délègue à `FavoriteService.getFavorites`) ; **401** anonyme. Bonus : `size > 100` → 400 (validation `@Max`). |
| [`MyCoOrganizerInvitationsResource`](/backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/resource/MyCoOrganizerInvitationsResource.java) — **0 %** | **[C]** `MyCoOrganizerInvitationsResourceTest` | `GET /api/users/me/co-organizer-invitations` → **200** (auth) ; **401** anonyme. Bonus : `status` enum invalide → 400. |
| [`EventService.uploadImage`](/backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java) | **[+]** [`EventServiceTest`](/backend/services/event-service/src/test/java/ch/unige/events/event/service/EventServiceTest.java) | **404** event inconnu ; **403** non-organisateur ; **force admin** ; **succès** (`FileStorageService.saveImage` + client engagement mockés). Plus gros gain unitaire du module. |
| `EventService.duplicate` | **[+]** `EventServiceTest` | cap de collision **> 100** → **422 `duplicate_title_collision`** (préparer 100 titres en collision via `PanacheMock`). |
| [`EventCoOrganizerService`](/backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerService.java) | **[+]** [`EventCoOrganizerServiceTest`](/backend/services/event-service/src/test/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerServiceTest.java) | `lookupTargetUser` : **503** (`RuntimeException` du client) et **retour null** ; `safeGetUser` : WARN sur échec infra. |
| [`EventViewService`](/backend/services/event-service/src/main/java/ch/unige/events/event/view/service/EventViewService.java) | **[+]** [`EventViewServiceTest`](/backend/services/event-service/src/test/java/ch/unige/events/event/view/service/EventViewServiceTest.java) | branche « authentifié mais `uuid == null` » → fallback anonyme. |

#### `services/engagement-service`

| Cible | Action | Cas |
|---|---|---|
| [`AttendanceService`](/backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java) | **[+]** [`AttendanceServiceTest`](/backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/service/AttendanceServiceTest.java) | `getAttendees` : fallback co-org (`coOrgOf` null/false + `getOrganizerUuids`) ; `removeAttendance` sur statut **terminal EXPIRED** (≠ CANCELLED) ; `getUserParticipationEvents` early-return `eventIds` vide ; `matchesTimeframe` `endDate() == null`. |
| [`CommentService.fanOutMentions`](/backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java) | **[+]** [`CommentServiceTest`](/backend/services/engagement-service/src/test/java/ch/unige/events/engagement/comment/service/CommentServiceTest.java) | `userClient.getByUsernames` jette (`MENTION_RESOLVE_FAIL`) ; résolution **vide** vs **null**. |
| [`CommentLike.findLikedCommentIdsByUser`](/backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/entity/CommentLike.java) | **[+]** (test entité/`CommentLikeServiceTest` existant) | guard d'entrée **vide** (assertion directe). |

#### `services/user-service`

| Cible | Action | Cas |
|---|---|---|
| [`UserService`](/backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java) | **[+]** [`UserServiceTest`](/backend/services/user-service/src/test/java/ch/unige/events/user/service/UserServiceTest.java) | `getOrCreateUser` : catch **conflit unique-auth0** (race) ; `isOptimisticLockConflict` / `isUniqueUsernameConflict` : branches de **matching sur le nom de classe** d'exception ; `searchByUsernamePrefix` : `limit ≤ 0` / **exclusion de soi**. |
| [`CalendarService.withOptimisticLockRetry`](/backend/services/user-service/src/main/java/ch/unige/events/user/calendar/service/CalendarService.java) | **[+]** [`CalendarServiceTest`](/backend/services/user-service/src/test/java/ch/unige/events/user/calendar/service/CalendarServiceTest.java) | **rethrow** d'une exception **non-OLE** (non retryée). |

#### `services/moderation-service` (package `ch.unige.events.report.*`)

| Cible | Action | Cas |
|---|---|---|
| [`EventBannedPublisher`](/backend/services/moderation-service/src/main/java/ch/unige/events/report/kafka/EventBannedPublisher.java) | **[+]** [`EventBannedPublisherTest`](/backend/services/moderation-service/src/test/java/ch/unige/events/report/kafka/EventBannedPublisherTest.java) | catch **`JsonProcessingException`** (~10 L) → `IllegalStateException` (`ObjectMapper` mocké qui jette). Plus gros gain du module. |
| [`EventBannedOutboxPoller`](/backend/services/moderation-service/src/main/java/ch/unige/events/report/outbox/EventBannedOutboxPoller.java) | **[+]** [`EventBannedOutboxPollerTest`](/backend/services/moderation-service/src/test/java/ch/unige/events/report/outbox/EventBannedOutboxPollerTest.java) | bras **`InterruptedException`** (emitter `get()` jette) → `attempts++`, `lastError`, flag d'interruption (distinct du `Exception` générique déjà couvert). |
| [`ReportService.safeGetUser`](/backend/services/moderation-service/src/main/java/ch/unige/events/report/service/ReportService.java) | **[+]** [`ReportServiceTest`](/backend/services/moderation-service/src/test/java/ch/unige/events/report/service/ReportServiceTest.java) | catch **`jakarta.ws.rs.NotFoundException`** explicite (distinct du `RuntimeException` générique déjà couvert). |

#### `services/notification-service` (event-driven)

| Cible | Action | Cas |
|---|---|---|
| [`FollowLifecycleEventDeserializer`](/backend/services/notification-service/src/main/java/ch/unige/events/notification/kafka/FollowLifecycleEventDeserializer.java) — **pas de test dédié** | **[C]** `FollowLifecycleEventDeserializerTest` | sentinel **3-cas** (ctor no-arg / JSON valide / bytes null) — calque des autres `*EventDeserializerTest`. |
| [`EventUpdatedConsumerTest`](/backend/services/notification-service/src/test/java/ch/unige/events/notification/kafka/EventUpdatedConsumerTest.java), [`UserFollowRequestedConsumerTest`](/backend/services/notification-service/src/test/java/ch/unige/events/notification/kafka/UserFollowRequestedConsumerTest.java), [`UserFollowAcceptedConsumerTest`](/backend/services/notification-service/src/test/java/ch/unige/events/notification/kafka/UserFollowAcceptedConsumerTest.java) | **[+]** | **vérifier** qu'ils portent **toutes** les branches-garde (wrong-type / null-id / self-loop / `resolveMessage` fallback) ; compléter celles qui manquent. |

### 4.2 PATTERNS À RÉUTILISER (fichiers réels)

| Pattern | Référence |
|---|---|
| `@QuarkusTest` + `@TestSecurity(user = "auth0|…")` (identité du principal) | [`MyAttendancesResourceTest`](/backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/resource/MyAttendancesResourceTest.java) |
| `PanacheMock.mock(Entity.class)` + `when(Entity.staticFinder(...))` pour les finders statiques Panache | [`AttendanceServiceTest`](/backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/service/AttendanceServiceTest.java), `MyAttendancesResourceTest` |
| `@InjectMock @RestClient FooServiceClient` pour le cross-service | `AttendanceServiceTest` (`EventServiceClient`, `UserServiceClient`) |
| Staging d'identité : `JwtTestContext.set(JwtTestHelper.jwtFor(uuid))` en `@BeforeEach`, `.clear()` en `@AfterEach` | helpers **par service** : event ([`event/test/`](/backend/services/event-service/src/test/java/ch/unige/events/event/test/JwtTestHelper.java)), engagement ([`engagement/test/`](/backend/services/engagement-service/src/test/java/ch/unige/events/engagement/test/JwtTestHelper.java)), user ([`user/test/`](/backend/services/user-service/src/test/java/ch/unige/events/user/test/JwtTestHelper.java)), moderation ([`report/test/`](/backend/services/moderation-service/src/test/java/ch/unige/events/report/test/JwtTestHelper.java)) |
| RestAssured `given().when().get(...).then().statusCode(...)` (resource) | `MyAttendancesResourceTest` |
| Helper de construction de DTO (DRY) + `@ParameterizedTest` | `event(...)` dans `AttendanceServiceTest` |
| Tests **JUnit purs** (sans Quarkus) côté shared (clients + DTO) | `shared/domain-dtos`, `shared/jaxrs`, `shared/tracing`, `shared/domain-projections` tests existants |

> Convention : un fichier de test par classe ciblée, **même package** que la classe testée, nommé `FooTest` / `FooResourceTest`. Réutiliser le `JwtTestContext`/`JwtTestHelper` **du service concerné** (ne pas en recréer).

### 4.3 HORS-SCOPE (explicite — cf. D4)

- ❌ `@ConfigMapping` `AppConfig` (×3) — aucune ligne instrumentable.
- ❌ `EventBannedEventDeserializer` (event-service) — ctor `super(...)` trivial.
- ❌ `UserMeClient` (domain-dtos) — interface sans méthode `default`.
- ❌ Records DTO sans logique déjà exercés indirectement.
- ❌ **Aucune** modif de `openapi/openapi.yaml` (`git diff openapi/` = **0 ligne**).
- ❌ **Aucune** migration Flyway, **aucun** nouvel endpoint, **aucun** fichier sous `src/main/` modifié.
- ❌ Pas de seconde spec, pas de découpage par module en plusieurs PR.
- ❌ Les 6 shared libs à 100 % (`api-error`, `domain-enums`, `kafka-events`, `platform`, `rate-limit`, `storage`).

---

## 5. Étapes d'implémentation (ORDONNÉES par module)

> Pré-requis : créer la branche `feature/backend-coverage-95` (depuis la branche de travail `claude/relaxed-shannon-4SWbr`). Après chaque module shared, lancer **localement** `./mvnw -pl shared/<lib> test` (sans Docker) pour confirmer le rapport jacoco. Pour les services, s'appuyer sur la **CI**.

### Étape 1 — `shared/domain-dtos` (priorité, mesurable en local)
1. **[C]** `BearerTokenClientFilterTest` (JUnit pur) : 4 cas (resolvable/non, token null, raw null/blank, header positionné) — **le plus gros gain shared** (9 L + 8 B).
2. **[+]** `UserPublicResponseTest` : ctors 12-arg / 11-arg + `anonymous(UUID, name, avatar)`.
3. **[+]** `AttendanceDTOTest` : ctor canonique 8-arg.
4. **[C]** `CommentVisibilityProjectionTest` : 1 assertion d'accessor.
5. Vérifier : `./mvnw -pl shared/domain-dtos test` → domain-dtos vise ~100 % L.

### Étape 2 — `shared/{domain-projections, jaxrs, tracing}` (polish branches, local)
1. **[+]** `CallerIdentityTest` (`me.id() == null`).
2. **[+]** `EnumParamConverterProviderTest` + `InternalTokenFilterTest` (1 branche chacun).
3. **[+]** `MdcKafkaProducerInterceptorTest` (2 branches) + `MdcKafkaConsumerInterceptorTest` (`h.value() == null`).
4. Vérifier les 3 modules en local.

### Étape 3 — `services/event-service` (plus gros levier agrégat)
1. **[C]** `RedirectResourceTest` (302 + Location ; 404).
2. **[C]** `UserFavoritesResourceTest` (200 / 401).
3. **[C]** `MyCoOrganizerInvitationsResourceTest` (200 / 401).
4. **[+]** `EventServiceTest` : `uploadImage` (404/403/admin/succès) + `duplicate` cap > 100 (422).
5. **[+]** `EventCoOrganizerServiceTest` (`lookupTargetUser` 503/null, `safeGetUser` infra) ; **[+]** `EventViewServiceTest` (anon null-uuid).

### Étape 4 — `services/engagement-service`
1. **[+]** `AttendanceServiceTest` : fallback co-org, removeAttendance EXPIRED, early-return participations, matchesTimeframe endDate null.
2. **[+]** `CommentServiceTest` : `fanOutMentions` échec / résolution vide vs null.
3. **[+]** guard `CommentLike.findLikedCommentIdsByUser` (entrée vide).

### Étape 5 — `services/user-service`
1. **[+]** `UserServiceTest` : conflit unique-auth0, branches optimistic-lock/unique-username, `searchByUsernamePrefix`.
2. **[+]** `CalendarServiceTest` : `withOptimisticLockRetry` rethrow non-OLE.

### Étape 6 — `services/moderation-service`
1. **[+]** `EventBannedPublisherTest` : catch `JsonProcessingException`.
2. **[+]** `EventBannedOutboxPollerTest` : bras `InterruptedException`.
3. **[+]** `ReportServiceTest` : catch `NotFoundException` explicite.

### Étape 7 — `services/notification-service`
1. **[C]** `FollowLifecycleEventDeserializerTest` (sentinel 3-cas).
2. **[+]** compléter les branches-garde manquantes de `EventUpdatedConsumerTest` / `UserFollowRequestedConsumerTest` / `UserFollowAcceptedConsumerTest`.

### Étape 8 — Intégration & CI
1. `./mvnw -pl shared/<lib> test` pour chaque shared lib modifiée (local, sans Docker).
2. Pousser ; laisser la **CI** exécuter la matrix (5 services) + `sonar-aggregate`.
3. **Boucler sur le rapport Sonar** de la PR jusqu'à **≥ 95 % L agrégé** + gate vert (couverture nouveau code ≥ 80 %, duplication ≤ 3 %).

---

## 6. Ordre de réalisation & dépendances

- **Indépendants, parallélisables** : tous les modules sont disjoints (fichiers de test séparés). Aucune dépendance inter-module.
- **Gros gains d'abord** : **event-service** (3 resources 0 % + `uploadImage` = plusieurs points d'agrégat) et **`shared/domain-dtos`** (`BearerTokenClientFilter`, seul vrai 0 % shared). Les faire **en premier** maximise la pente couverture/effort.
- **Vérification locale possible uniquement sur shared** (sans Docker) → utile pour valider Étapes 1-2 immédiatement ; les Étapes 3-7 (services) se valident **en CI**.
- **Bug applicatif** éventuel découvert en écrivant un test : **arrêter, signaler** dans la PR (ne pas corriger sans accord) — l'objectif est l'ajout de tests, pas la modif applicative.

---

## 7. Checklist finale de validation

- [ ] Toutes les **classes à 0 %** listées ont au moins un test : event-service ×3 (`RedirectResource`, `UserFavoritesResource`, `MyCoOrganizerInvitationsResource`) + `BearerTokenClientFilter` + `CommentVisibilityProjection` + `FollowLifecycleEventDeserializer`.
- [ ] **Branches d'erreur/fallback** ciblées couvertes par module (uploadImage, duplicate cap, co-org fallback, optimistic-lock, JSON/Interrupted/NotFound catches, mentions, branches-garde consumers).
- [ ] `git diff origin/main HEAD -- openapi/` = **0 ligne** ; **aucune** migration Flyway ; **aucun** fichier sous `*/src/main/` modifié (`git diff --stat` ne touche que `*/src/test/`).
- [ ] Titre PR = **`test(backend): …`** (jamais `feat`/`refactor`/`perf`).
- [ ] Tests **DRY** (helpers / `@ParameterizedTest`) — duplication nouveau code ≤ 3 %.
- [ ] `./mvnw -pl shared/<lib> test` vert en local pour chaque shared lib modifiée.
- [ ] **CI verte** : build matrix (5 services) + `sonar-aggregate` ; gate SonarCloud franchi (couverture nouveau code ≥ 80 %, duplication ≤ 3 %, ratings A).
- [ ] **Couverture agrégée JaCoCo/Sonar ≥ 95 % L** — vérifiée sur le rapport Sonar de la PR.
- [ ] Aucun bug applicatif corrigé en douce ; tout bug rencontré est **signalé** dans la PR.
