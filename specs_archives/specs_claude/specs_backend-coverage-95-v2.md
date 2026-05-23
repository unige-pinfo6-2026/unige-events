# SPEC v2 — Couverture de tests backend ≥ 95 % (PR de tests pure — 2ᵉ passe)

| Champ | Valeur |
|---|---|
| Périmètre | **Backend uniquement** — élever la couverture de **lignes agrégée** (JaCoCo/Sonar, 15 modules) de **~90 % à ≥ 95 %**, en **AJOUTANT** des tests. Aucune modif de code applicatif. |
| Branche de travail | **`claude/relaxed-shannon-4SWbr`** (celle de la **PR #199**). On **continue sur cette branche / cette PR** — pas de nouvelle branche, pas de 3ᵉ spec. |
| Base | `origin/main`. Tip de la branche à la rédaction : `533941a8` (`test(backend): extract BANNERS_FOLDER constant`). |
| Sprint | S10 — durcissement de la couverture, **2ᵉ passe** (la PR #199 a livré la 1ʳᵉ passe et plafonne à ~90 %). |
| Objectif chiffré | **Couverture de lignes agrégée ≥ 95 %** (rapport Sonar de la PR). Ne pas régresser la couverture de branches. |
| Tickets liés | Aucun ticket fonctionnel (dette de test). PR de type `test`. |
| Auteur spec | Elie Bussod (rédaction assistée Claude). |
| Date | 2026-05-23 |
| Règle d'or | **Aucune modif de code applicatif** ; on **AJOUTE** des tests. **Tests DRY** (helpers + `@ParameterizedTest`) — le gate SonarCloud impose **duplication ≤ 3 %** et **couverture ≥ 80 %** sur le **nouveau code**. Si un test révèle un **bug applicatif**, le **SIGNALER** (ne pas corriger sans accord). |

> **IMPÉRATIF de livrable.** Cette spec est **spec-only**. L'implémentation produira **un seul** flux (la branche `claude/relaxed-shannon-4SWbr`, la PR #199) ; **aucune autre spec** ne doit être créée. Tous les chemins sont **vérifiés** sur la structure microservices réelle (`backend/services/<svc>-service/` + `backend/shared/<lib>/`).
>
> **Cette v2 supersède [`specs_backend-coverage-95.md`](specs_backend-coverage-95.md).** La v1 supposait des trous « étroits » (poignée de classes 0 % + branches shared) et a **volontairement écarté** les gros gisements de la **couche service** (jugés « déjà couverts au runtime »). Résultat : ~89 % → ~90 % seulement. **Les vrais gisements sont dans les `Service`/`Resource` métier** — quantifiés ci-dessous au numéro de ligne près à partir des **rapports JaCoCo réels** du dernier run CI de la PR.

---

## 1. Vue d'ensemble

La couverture **agrégée** stagne à ~90 %. Les ~230 lignes non couvertes restantes sont **concentrées** dans les couches `Service`/`Resource` des 5 services, et **dominées par un petit nombre de patterns défensifs répétés** (cf. §4.0) : blocs `catch` de **dégradation cross-service** (`safeGetUser`), **fallbacks null** des appels cross-service, **swallow** des publishers Kafka, **rethrow** `PersistenceException` non-conflit, **retry optimistic-lock**, **gardes** des consumers. Couvrir ces patterns avec des helpers + `@ParameterizedTest` ferme **beaucoup de lignes à faible coût/duplication**.

**Mesure réelle (JaCoCo, head `533941a8`, run CI `26313708860`)** — lignes non couvertes par **classe possédée** (hors classes shared/cross-service qui sont couvertes via l'union Sonar), triées par impact :

| Service | Lignes NC (possédées) | Classes dominantes (lignes NC) |
|---|---|---|
| **user-service** | **92** | `UserService` **41** · `FollowService` 12 · `UserResource` 10 · `IcsBuilder` 10 · `User` (entity) 7 · `UsernameGenerator` 6 · `CalendarService` 3 · `FollowLifecyclePublisher` 3 |
| **event-service** | **~64** | `EventCoOrganizerService` **18** · `EventService` 11 · `EventResource` 6 · `EventLifecyclePublisher` 5 · `FavoriteService` 5 · `EventCoOrganizerResource` 4 · `CoOrganizerPublisher` 3 · `FeaturedService` 2 · `EventAttachmentService` 2 · `CoOrganizerDTO` 2 · `EventBannedEventDeserializer` 2 · `RecurrenceGenerator` 1 · `EventLifecycleKafkaBridge` 1 · `EventStatsService` 1 · `MyEventsService` 1 |
| **engagement-service** | **49** | `AttendanceService` **17** · `CommentService` 13 · `CommentLikeService` 5 · `AttendanceCreatedPublisher` 3 · `CommentCreatedPublisher` 3 · `CommentDTO` 2 · `AttendanceResource` 2 · `MentionParser` 1 · `CommentLike` 1 · `AttendanceDTOMapper` 1 · `MyAttendancesResource` 1 |
| **moderation-service** | **15** | `ReportService` **15** (outbox + kafka **déjà fermés par PR #199** — ne pas re-cibler) |
| **notification-service** | **10** | `NewCommentConsumer` **6** · `UserFollowRequestedConsumer` 2 · `UserFollowAcceptedConsumer` 2 |
| **shared** (10 libs) | **0** | **RIEN À FAIRE** — les 10 libs sont à **100 % L** (PR #199 a fermé `domain-dtos`/`jaxrs`/`tracing`/`domain-projections` ; les 6 autres l'étaient déjà). |

**Total ≈ 230 lignes possédées non couvertes.** Les fermer (en priorité user-service + event-service, plus gros dénominateurs) fait converger l'agrégat vers ≥ 95 %. Le chiffre exact se pilote sur le **rapport Sonar de la PR** (§5 étape finale).

> **Pourquoi les chiffres diffèrent des captures Sonar fournies à la rédaction** : les captures montrent la couverture **new-code** (dénominateur réduit) d'une analyse antérieure ; le JaCoCo `533941a8` ci-dessus est l'**overall** au tip courant. Les deux **coïncident quasi exactement** pour les services dont quasiment tout le code est « récent » (migration microservices) — ex. user-service 92 = 92, engagement 49 = 49, calendar 13 = 13. Le JaCoCo `533941a8` est donc la **source de vérité ligne-précise** ; les captures servent de **carte de chaleur** de priorisation. Note : la capture moderation (24) et event (73) incluaient des lignes **déjà fermées par les derniers commits de PR #199** (moderation `outbox`/`kafka`, event `EventBannedPublisher`).

---

## 2. Contexte

### 2.1 Pourquoi 95 %
Objectif de qualité interne S10. Le seuil **dur** SonarCloud reste 80 % sur le **nouveau code** ; l'équipe vise **≥ 95 %** d'agrégat. La dette n'est pas structurelle (ratio test/main 1,6–2,7) : il reste surtout des **chemins d'erreur/dégradation** jamais exercés.

### 2.2 Comment c'est mesuré
- Couverture produite par l'extension **`quarkus-jacoco`** → un `target/jacoco-report/jacoco.xml` par module (`sonar.coverage.jacoco.xmlReportPaths` dans [`backend/pom.xml`](/backend/pom.xml)). **Vérifié** : `quarkus-jacoco` capture **aussi les tests unitaires purs** (ex. `UsernameGenerator`, purement unit-testé, ressort à 92 % L) — pas seulement les `@QuarkusTest`.
- **Aucune exclusion** JaCoCo/Sonar (ni pom, ni `application.properties`) → **tout** compte (DTO, records inclus).
- L'agrégat est calculé par le job CI **`sonar-aggregate`** ([`.github/workflows/build.yml`](/.github/workflows/build.yml)) qui **unit** les 15 jacoco.xml (artifacts amont) dans le projet unique `unige-pinfo6-2026_unige-events-backend`.
- **Nuance d'agrégation (importante)** : un **rapport par service inclut les classes shared/cross-service** chargées par ses tests (ex. `FileStorageService`, `CallerIdentity`, `*ServiceClient`) — montrées **partielles** localement mais **couvertes via l'union** (un autre module / le rapport shared les couvre). C'est pourquoi le total brut par-service paraît bas (~76 %) alors que l'**union réelle ≈ 90 %**. **⇒ Ne raisonner QUE sur les classes possédées** (package `ch/unige/events/<svc>/…`) ; ignorer le « missed » des classes `shared/` et des autres services dans un rapport donné.

### 2.3 CONTRAINTE — Docker
- **Les tests des 5 services exigent Docker** (Quarkus DevServices : Postgres éphémère + Kafka in-memory). **Docker n'est PAS disponible dans l'environnement de dev** → `./mvnw verify` ne tourne pas en local pour les services.
- **Les 10 shared libs sont pur-JUnit** → mesurables sans Docker (mais elles sont **déjà à 100 %**, donc inutile ici).
- **Conséquence** : le **% des services se vérifie EN CI** (matrix par service + `sonar-aggregate`). En local, se limiter à `./mvnw test-compile` (compile-check des tests ajoutés).

### 2.4 Données JaCoCo ligne-précises — comment les (re)produire
Les numéros de ligne du §4 viennent du run CI **`26313708860`** (head `533941a8`). Pour rafraîchir après de nouveaux commits :
```bash
gh run list --branch claude/relaxed-shannon-4SWbr --workflow "CI/CD Pipeline"   # repérer le run le plus récent ayant uploadé les jacoco-*
gh run download <RUN_ID> --pattern 'jacoco-*' --dir /tmp/jacoco
# Pour chaque services/<svc>-service/.../jacoco.xml : ne garder que les <package name="ch/unige/events/<svc>/...">,
# puis lister les <line nr="N" mi=".." ci="0" .../> (ci=0 + mi>0 → ligne NON couverte) et mb>0 → branche partielle.
```
> Les artifacts `jacoco-*` sont uploadés **dès la fin des jobs de test**, même si un step aval (docker push) traîne — un run encore « pending » peut donc déjà fournir les rapports.

---

## 3. Décisions tranchées (NE PAS revisiter)

### D1 — Type et titre de PR
**On garde la PR #199, titre `test(backend): raise coverage toward 95%`** (type `test`, scope libre). `feat`/`refactor`/`perf` exigeraient un scope `scrum-XXX` ([`pr-title-check.yml`](/.github/workflows/pr-title-check.yml)) — non applicable.

### D2 — Couvrir, ne pas exclure
**Aucune exclusion JaCoCo/Sonar.** On augmente la couverture en **écrivant des tests**, jamais en masquant des classes.

### D3 — DRY (gate duplication ≤ 3 %)
Le **pattern défensif est répété à l'identique** (§4.0) → factoriser **agressivement** : un helper par famille de mock (client qui jette `NotFoundException`/`RuntimeException`, client qui renvoie `null`, `Emitter` qui jette), `@BeforeEach` pour le staging JWT, **`@ParameterizedTest`** pour les variantes (par service, par emitter, par type d'exception). Un copier-coller des blocs `given()…then()` ferait **échouer** le gate duplication.

### D4 — Hors-scope (ne PAS toucher)
- **Déjà fermé par PR #199** : shared `BearerTokenClientFilter`, `CallerIdentity`, `EnumParamConverterProvider`, `InternalTokenFilter`, `MdcKafkaProducer/ConsumerInterceptor`, `UserPublicResponse`/`AttendanceDTO`/`CommentVisibilityProjection` ; services `EventService.uploadImage`, moderation `EventBannedPublisher` (JsonProcessingException), `EventBannedOutboxPoller` (InterruptedException), notification `FollowLifecycleEventDeserializer`. **Vérifier au JaCoCo `533941a8` qu'ils sont bien à 0 ligne NC avant d'envisager quoi que ce soit.**
- **Les 10 shared libs** (toutes à 100 % L).
- **Branches réellement inatteignables** : ex. `InternalTokenFilter expected==null` (via `orElse("")`), branche `className.contains("StaleState")` de `isOptimisticLockConflict` (Hibernate ne lève pas de `StaleStateException` nu en test — la branche `OptimisticLock`/`instanceof` suffit). Ne pas s'acharner.
- **`@ConfigMapping AppConfig`** (×3), records DTO triviaux déjà exercés indirectement, `EventBannedEventDeserializer` (ctor `super(...)` trivial — les 2 lignes NC ne valent pas un test dédié).
- **Aucune** modif `openapi/openapi.yaml` (`git diff openapi/` = **0 ligne**), **aucune** migration Flyway, **aucun** fichier sous `*/src/main/`.

### D5 — Priorisation
**user-service (92) + event-service (~64) d'abord** (plus gros dénominateurs et plus gros gisements). Puis engagement (49), moderation (15), notification (10). Au sein d'un service : les classes `Service` d'abord (gros blocs `catch`), puis `Resource`/`Publisher`/`entity`.

### D6 — Stratégie « pattern-first »
Implémenter les **7 patterns du §4.0** comme **familles de tests réutilisables**, puis décliner par classe. C'est ce qui transforme ~230 lignes éparses en une poignée de helpers paramétrés.

---

## 4. Analyse de l'existant

### 4.0 LE LEVIER — 7 patterns défensifs répétés (à implémenter en familles DRY)

| # | Pattern | Localisation (classes possédées) | Comment couvrir |
|---|---|---|---|
| **P1** | **`safeGetUser(UUID)`** — corps **identique** : `try userClient.getById` `catch NotFoundException → null` `catch RuntimeException → Log.warn + null`. Les **2 catch** sont NC partout. | `EventCoOrganizerService` (245-260) · `AttendanceService` (441-456) · `CommentService` (381-396) · `ReportService` (322-337) | `@InjectMock @RestClient UserServiceClient` → `when(getById(id)).thenThrow(new NotFoundException())` puis `.thenThrow(new RuntimeException("CB open"))` ; appeler la méthode publique qui enrichit (`getCoOrganizers`/`getAttendees`/`post`/`handle`) et asserter la **dégradation** (DTO avec champ user null), **sans exception propagée**. |
| **P2** | **Fallback liste/map null** d'un appel cross-service : `if (x == null) return Map.of()/...` (jamais exercé car les mocks renvoient toujours `Map.of()`). | `AttendanceService.fetchAttendeeProjections` (272-273) · `EventCoOrganizerService.findByIdsAsDTO` (230) · `ReportService.bulkFetchEvents` (314) · `EventService.findByIds` (~362) · `FeaturedService` · `FavoriteService` (~120) · `MyEventsService` (59) · `EventSearchService` | Stub du client renvoyant **`null`** ; asserter `attending=0/waitlisted=0` ou map vide (pas de NPE). |
| **P3** | **Swallow `catch (RuntimeException)`** des publishers Kafka (`Log.errorf("[KAFKA_PUBLISH_FAIL_*]")`, pas de rethrow). | `EventLifecyclePublisher` (57,58,67,71,73 — 4 emitters) · `CoOrganizerPublisher` (34,37,39) · `CommentCreatedPublisher` (27,30,32) · `AttendanceCreatedPublisher` (36,38,40) · `FollowLifecyclePublisher` (43,46,48) | Construire le publisher avec des `Emitter` **mockés** (test unitaire pur, comme `CoOrganizerPublisherTest`) ; `doThrow(new RuntimeException()).when(emitter).send(any())` ; `assertDoesNotThrow(...)`. **`@ParameterizedTest`** sur les emitters d'`EventLifecyclePublisher`. |
| **P4** | **Rethrow `PersistenceException` NON-conflit** — branche `if (!isXxxConflict(e)) throw e;` (seul le cas conflit est testé). Couvre transitivement les **helpers de classification** (`containsMessage`, `isUniqueXxxConflict`). | `UserService.getOrCreateUser` (79-83) · `UserService.updateUsername` (227-231) · `ReportService.createForComment` (183-188) · `FollowService.follow` · `FavoriteService` (cause chain 74-90) | Faire jeter `flush()`/`persist()` une `PersistenceException` **sans** le marker attendu (`users_auth0_id_unique`, `uq_users_username`, `uq_report_…`, etc.) → asserter le **rethrow** ; puis **avec** marker (si pas déjà couvert) → asserter le 409 canonique. Mocker `EntityManager`/`PanacheMock`. |
| **P5** | **Optimistic-lock / interruption** | `UserService.updateMyProfile` (127-133 : `catch OptimisticLockException → 409` + `catch PersistenceException` non-OLE → rethrow) · `CalendarService.withOptimisticLockRetry` (162-164 rethrow non-OLE) + `sleepBackoff` (182-184 `catch InterruptedException → Thread.currentThread().interrupt()`) | `flush()` qui jette `OptimisticLockException` → asserter `409 optimistic_lock_conflict`. Pour `sleepBackoff` : provoquer 2 OLE consécutives (retry → `Thread.sleep`) et/ou `Thread.currentThread().interrupt()` avant l'appel pour traverser le `catch InterruptedException`. |
| **P6** | **Gardes des consumers notification** : `catch RuntimeException` du lookup event/user, fallback `displayName().isBlank()` / `username().isBlank()`, ids null. | `NewCommentConsumer` (91-93 eventClient throw ; 150-153 userClient throw ; branches 158/161 blank) · `UserFollowRequestedConsumer` (55-57 ids null ; 70 fallback blank) · `UserFollowAcceptedConsumer` (69-71 ; 87) | Calquer les `*ConsumerTest` existants ; `@InjectMock` clients → `thenThrow`, ou renvoyer un `UserPublicResponse` à `displayName=""` (blank) / `null`. |
| **P7** | **`IcsBuilder`** (méthodes `public static` → **testables sans Quarkus**) | `IcsBuilder.foldLine` (68-76, ligne > 75) · `escapeIcs` (81, `value==null` + `\ ; , \n \r`) · `buildIcsContent` (38-39 start/end null skip ; 49 location null ; 52 description null) | Test JUnit pur : `EventDTO` à `startDate/endDate=null` (skip), `location/description=null`, une `SUMMARY` > 75 chars (folding RFC 5545), `escapeIcs(null)` et avec chaque caractère spécial. |

> **Légende §4.1** : **[C]** créer un fichier de test · **[+]** compléter un test existant. Même package que la classe. Réutiliser le `JwtTestHelper`/`JwtTestContext` **du service**.

### 4.1 — user-service (92 NC — priorité 1)

| Cible (lignes NC `533941a8`) | Action | Cas à couvrir |
|---|---|---|
| [`UserService`](/backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java) **41** (79-83, 127-133, 199, 227-231, 245, 325-356, 367-372 + branches 100/115/118/123/198/220/244/311) | **[+]** [`UserServiceTest`](/backend/services/user-service/src/test/java/ch/unige/events/user/service/UserServiceTest.java) | **P4** `getOrCreateUser` : `flush` jette `PersistenceException` sans marker → rethrow ; avec `users_auth0_id_unique` → refetch (`findByAuth0Id`). **P5** `updateMyProfile` : `flush` jette `OptimisticLockException` → **409** ; `PersistenceException` non-OLE → rethrow. **P4** `updateUsername` : `flush` jette `PersistenceException` sans `uq_users_username` → rethrow. `updateUsername(null)` → `usernameInvalid()` (400, ligne 199). `getByUsername(null)` → 404 (245). Ces tests couvrent transitivement `containsMessage`/`isUniqueXxxConflict`/`isOptimisticLockConflict`/`optimisticLockConflict` (325-372). |
| [`FollowService`](/backend/services/user-service/src/main/java/ch/unige/events/user/follow/service/FollowService.java) **12** (68, 73-76, 87-95 + branche 150) | **[+]** [`FollowServiceTest`](/backend/services/user-service/src/test/java/ch/unige/events/user/follow/service/FollowServiceTest.java) | **P4** `follow` : conflit unique `uq_follow_…` (race — refetch) vs `PersistenceException` non-unique → rethrow (couvre `isUniqueFollowConflict` 87-95). `assertProfileVisible` caller **anonyme** (`callerAuth0Id == null`) sur profil privé → 404 (branche 150). |
| [`UserResource`](/backend/services/user-service/src/main/java/ch/unige/events/user/resource/UserResource.java) **10** (130-132, 154-156, 252-255 + branches 91/127/151/247/249) | **[+]** [`UserResourceTest`](/backend/services/user-service/src/test/java/ch/unige/events/user/resource/UserResourceTest.java) | `getProfile`/`getByUsername` : **admin** sur profil privé (bypass anti-oracle, branche 91/247) ; `searchByUsername` sans match (liste vide) ; chemins de mapping restricted/anonymous (252-255). |
| [`IcsBuilder`](/backend/services/user-service/src/main/java/ch/unige/events/user/calendar/util/IcsBuilder.java) **10** (39, 68-76, 81 + branches 38/49/52/65) | **[C]** `IcsBuilderTest` (JUnit pur — déplacer/compléter ce qui est dans `CalendarResourceTest`) | **P7** : event start/end null → skip ; location/description null → omis ; `foldLine` ligne > 75 ; `escapeIcs(null)` + chaque caractère spécial (`\ ; , \n \r`). |
| [`User`](/backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java) **7** (114, 135, 145, 167, 175-177 + branches 113/134/140/144/166/174) | **[+]** [`UserTest`](/backend/services/user-service/src/test/java/ch/unige/events/user/entity/UserTest.java) | Finders Panache : `findByUsername(null)` → empty (113-114) ; `findByUsernames` liste vide / éléments blanks (134-145) ; `searchByUsernamePrefix` `limit<=0`, prefix null/blank, et **les deux** branches HQL (avec/sans `excludeAuth0Id`) (166-177). Tests d'entité avec `@QuarkusTest`+`@TestTransaction`. |
| [`UsernameGenerator`](/backend/services/user-service/src/main/java/ch/unige/events/user/service/UsernameGenerator.java) **6** (130-131, 135, 183, 190, 215 + 13 branches partielles) | **[+]** [`UsernameGeneratorTest`](/backend/services/user-service/src/test/java/ch/unige/events/user/service/UsernameGeneratorTest.java) | `slugify` : troncature à 30 avec ponctuation finale (retrim 130-131), fallback longueur < MIN (135) ; `buildCandidate` trimming (183) ; `preTranslateLatinExt` (190) ; branches `pickFirstNonBlank`/`joinNonBlank` (199/202/211/214-215). **`@ParameterizedTest`** sur des entrées ciblées. |
| [`CalendarService`](/backend/services/user-service/src/main/java/ch/unige/events/user/calendar/service/CalendarService.java) **3** (182-183, 195 + branche 194) | **[+]** [`CalendarServiceTest`](/backend/services/user-service/src/test/java/ch/unige/events/user/calendar/service/CalendarServiceTest.java) | **P5** `withOptimisticLockRetry` : rethrow non-OLE immédiat (déjà ?) ; `sleepBackoff` `InterruptedException` (interrompre le thread courant) ; `isOptimisticLockConflict` branche `className.contains("OptimisticLock")` (194-195). |
| [`FollowLifecyclePublisher`](/backend/services/user-service/src/main/java/ch/unige/events/user/follow/kafka/FollowLifecyclePublisher.java) **3** (43, 46, 48) | **[+]** [`FollowLifecyclePublisherTest`](/backend/services/user-service/src/test/java/ch/unige/events/user/follow/kafka/FollowLifecyclePublisherTest.java) | **P3** swallow `catch RuntimeException` (emitter qui jette). |

### 4.2 — event-service (~64 NC — priorité 1)

| Cible (lignes NC) | Action | Cas |
|---|---|---|
| [`EventCoOrganizerService`](/backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerService.java) **18** (57, 93, 113, 165, 209-213, 223, 227, 231, 247, 251-259 + branches) | **[+]** [`EventCoOrganizerServiceTest`](/backend/services/event-service/src/test/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerServiceTest.java) + [`…LookupTargetUserTest`](/backend/services/event-service/src/test/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerServiceLookupTargetUserTest.java) | **P1** `safeGetUser` catches (251-259) via `getCoOrganizers` dégradé (enrichissement null, branche 153). `lookupTargetUser` (static) : `RuntimeException` du client → **503** (209-213), `null` → 404 (215). `getMyInvitations` : event supprimé → DTO filtré (branche 180). **P2** `findByIdsAsDTO` summaries null (230-231). Gardes 57/93/113/165 (early-returns). |
| [`EventService`](/backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java) **11** (216, 357, 362, 441, 636-642, 759, 828 + **32 branches partielles**) | **[+]** [`EventServiceTest`](/backend/services/event-service/src/test/java/ch/unige/events/event/service/EventServiceTest.java) | **P2** `findByIds` summaries null (~362). `delete` : cleanup S3 qui jette → warn + swallow (~636-642). `publish` validation `endDate==null`/incohérente → **422** (~732-746 branches). Le reste = gardes/null-checks **éparses** : piloter par le JaCoCo (lignes 216/357/441/759/828 + branches), mopper au cas par cas. **Gros fichier (830 L) → ROI/ligne plus faible, faire après les classes denses.** |
| [`EventResource`](/backend/services/event-service/src/main/java/ch/unige/events/event/resource/EventResource.java) **6** (184, 196, 276-279 + branches 97/117/145/155/157/194/195/273) | **[+]** [`EventResourceTest`](/backend/services/event-service/src/test/java/ch/unige/events/event/resource/EventResourceTest.java) | `followedOnly` + anonyme → **401** (branche 97) ; `organizerId` + statut ≠ PUBLISHED → **400** (117) ; chemins 184/196/276-279 (mapping/validation de query params). RestAssured. |
| [`EventLifecyclePublisher`](/backend/services/event-service/src/main/java/ch/unige/events/event/kafka/EventLifecyclePublisher.java) **5** (57, 58, 67, 71, 73) | **[+]** [`EventLifecyclePublisherTest`](/backend/services/event-service/src/test/java/ch/unige/events/event/kafka/EventLifecyclePublisherTest.java) | **P3** swallow `catch RuntimeException` — **`@ParameterizedTest` sur les 4 emitters** (published/cancelled/expired/updated). |
| [`FavoriteService`](/backend/services/event-service/src/main/java/ch/unige/events/event/favorite/service/FavoriteService.java) **5** (63, 74, 75, 81, 120 + branches 90/119) | **[+]** [`FavoriteServiceTest`](/backend/services/event-service/src/test/java/ch/unige/events/event/favorite/service/FavoriteServiceTest.java) | **P4** `isUniqueFavoriteConflict` : chaîne de cause **null** / sans `ConstraintViolationException` → false (rethrow, 74-90). **P2** `getFavorites` summaries null (119-120). Garde 63. |
| [`CoOrganizerPublisher`](/backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/kafka/CoOrganizerPublisher.java) **3** (34, 37, 39) | **[+]** [`CoOrganizerPublisherTest`](/backend/services/event-service/src/test/java/ch/unige/events/event/coorganizer/kafka/CoOrganizerPublisherTest.java) | **P3** swallow (compléter : l'`assertDoesNotThrow` existant ne traverse pas tous les bras). |
| [`EventCoOrganizerResource`](/backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/resource/EventCoOrganizerResource.java) **4** (74, 75, 84, 85) · [`CoOrganizerDTO`](/backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/dto/CoOrganizerDTO.java) **2** (36-37) · `FeaturedService` **2** (149-150) · `EventAttachmentService` **2** (80,145) · `MyEventsService`/`EventStatsService`/`RecurrenceGenerator`/`EventLifecycleKafkaBridge` **1** ch. | **[+]** tests existants | Branches/gardes ponctuelles : compléter via JaCoCo. `CoOrganizerDTO.from` chemin user null (P1 corollaire). `FeaturedService` summaries null + phase1Ids vide. |

> **Hors-scope event** : `EventBannedEventDeserializer` (2 L, ctor trivial — cf. D4).

### 4.3 — engagement-service (49 NC — priorité 2)

| Cible (lignes NC) | Action | Cas |
|---|---|---|
| [`AttendanceService`](/backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java) **17** (104, 154, 241-244, 273, 282-284, 377, 414, 443, 447-455 + branches) | **[+]** [`AttendanceServiceTest`](/backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/service/AttendanceServiceTest.java) | **P1** `safeGetUser` (447-455). **P2** `fetchAttendeeProjections` : `projections==null` → Map vide (272-273) + `catch RuntimeException` (282-284). Tri-state `coOrgOf` : branche fallback `getOrganizerUuids` (241-244). Gardes 104/154/377/414. |
| [`CommentService`](/backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java) **13** (88, 155-158, 190, 359, 383-395 + branches) | **[+]** [`CommentServiceTest`](/backend/services/engagement-service/src/test/java/ch/unige/events/engagement/comment/service/CommentServiceTest.java) | **P1** `safeGetUser` (383-395). `fanOutMentions` : `userClient.getByUsernames` jette → skip (155-158). `authorLabel(null)` → fallback (190). `isCreatorOrAcceptedCoOrganizer` `event==null||callerUuid==null` (359). Garde 88. |
| [`CommentLikeService`](/backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentLikeService.java) **5** (86, 89-90, 128, 139 + branches 127/138) | **[+]** [`CommentLikeServiceTest`](/backend/services/engagement-service/src/test/java/ch/unige/events/engagement/comment/service/CommentLikeServiceTest.java) | **P4** conflit unique de like (race) : `PersistenceException` conflit vs non-conflit (86-90) ; gardes batch `findLikedCommentIdsByUser` entrée vide (127-139). |
| `AttendanceCreatedPublisher` **3** (36,38,40) · `CommentCreatedPublisher` **3** (27,30,32) | **[+]** tests existants | **P3** swallow (compléter ce qui manque). |
| `CommentDTO` 2 (87-88) · `AttendanceResource` 2 (63-64) · `MentionParser` 1 (52) · `CommentLike` 1 (82) · `AttendanceDTOMapper` 1 (22) · `MyAttendancesResource` 1 (65) | **[+]** | Branches/accessors ponctuels — JaCoCo. |

### 4.4 — moderation-service (15 NC — priorité 2)

| Cible (lignes NC) | Action | Cas |
|---|---|---|
| [`ReportService`](/backend/services/moderation-service/src/main/java/ch/unige/events/report/service/ReportService.java) **15** (72, 152, 183-188, 270, 309, 324-336 + branches 213/…) | **[+]** [`ReportServiceTest`](/backend/services/moderation-service/src/test/java/ch/unige/events/report/service/ReportServiceTest.java) (ou `…UnitTest`) | **P1** `safeGetUser` (324-336). **P4** `createForComment` double-tap : `PersistenceException` conflit `uq_report_comment` vs non-conflit → rethrow (183-188 ; couvre `isUniqueReportCommentConflict` branche 213). **P2** `bulkFetchEvents` ids vide (308-309) / `findByIds` null (314). Gardes `reporterId==null` (152) / `adminId==null` (270) / `createReport` eventId reporter null (72). |

> **Déjà fermé par PR #199** (vérifié absent du JaCoCo `533941a8`) : `report/outbox/EventBannedOutboxPoller`, `report/kafka/EventBannedPublisher`. **Ne pas re-cibler.**

### 4.5 — notification-service (10 NC — priorité 3)

| Cible (lignes NC) | Action | Cas |
|---|---|---|
| [`NewCommentConsumer`](/backend/services/notification-service/src/main/java/ch/unige/events/notification/kafka/NewCommentConsumer.java) **6** (91-93, 150-153 + branches 158/161) | **[+]** [`NewCommentConsumerTest`](/backend/services/notification-service/src/test/java/ch/unige/events/notification/kafka/NewCommentConsumerTest.java) | **P6** `eventClient.getById` jette → skip (91-93) ; `resolveAuthorLabel` `userClient.getById` jette → fallback (150-153) ; `displayName().isBlank()` (158) / `username().isBlank()` (161). |
| [`UserFollowRequestedConsumer`](/backend/services/notification-service/src/main/java/ch/unige/events/notification/kafka/UserFollowRequestedConsumer.java) **2** (56-57 + branches 55/70) · [`UserFollowAcceptedConsumer`](/backend/services/notification-service/src/main/java/ch/unige/events/notification/kafka/UserFollowAcceptedConsumer.java) **2** (70-71 + branches 69/87) | **[+]** tests existants | **P6** ids null (les **deux** : follower **et** followed) ; `resolveMessage` fallback `displayName` null/blank. |

### 4.6 — shared : RIEN
Les 10 libs sont à **100 % L** (`533941a8`). Ne pas y toucher.

### 4.7 PATTERNS DE TEST À RÉUTILISER (fichiers réels)
| Pattern | Référence |
|---|---|
| `@QuarkusTest` + `@TestSecurity` / `JwtTestHelper`+`JwtTestContext` (par service) | `MyAttendancesResourceTest`, helpers `*/test/` de chaque service |
| `@InjectMock @RestClient FooServiceClient` (stub/throw cross-service) | `AttendanceServiceTest`, `EventCoOrganizerServiceTest` |
| `PanacheMock.mock(Entity.class)` + `when(Entity.staticFinder(...))` ; mock `EntityManager` pour faire jeter `flush()` | `AttendanceServiceTest`, `UserServiceTest` |
| Publisher unitaire pur : ctor avec `Emitter` mockés + `doThrow().when(emitter).send(...)` + `assertDoesNotThrow` | `CoOrganizerPublisherTest`, `EventLifecyclePublisherTest` |
| Test JUnit pur (sans Quarkus) pour util static | `MentionParserTest`, futur `IcsBuilderTest` |
| Sentinelle deserializer 3-cas | `EventLifecycleEventDeserializerTest`, `FollowLifecycleEventDeserializerTest` (PR #199) |

---

## 5. Étapes d'implémentation (ORDONNÉES)

> Pré-requis : être **sur `claude/relaxed-shannon-4SWbr`** (PR #199). Récupérer le JaCoCo frais (§2.4) pour confirmer les lignes avant chaque module. Tout se valide **en CI** (Docker indisponible en local) ; en local, `./mvnw test-compile` doit rester vert.

1. **Familles DRY (P1–P7)** — écrire d'abord les helpers réutilisables (mock-client-throws, mock-client-null, mock-emitter-throws, mock-EM-flush-throws) dans le `test/` de chaque service concerné.
2. **user-service (92)** : `UserService` (P4/P5) → `IcsBuilder` (P7) → `FollowService` (P4) → `User` entity finders → `UserResource` (admin bypass) → `UsernameGenerator` → `CalendarService` (P5) → `FollowLifecyclePublisher` (P3).
3. **event-service (~64)** : `EventCoOrganizerService` (P1/P2) → `EventLifecyclePublisher` (P3, paramétré) → `FavoriteService` (P4/P2) → `EventResource` (401/400) → `CoOrganizerPublisher` (P3) → `EventService` (P2 + mop-up branches éparses, en dernier car gros fichier) → resources/DTO ponctuels.
4. **engagement-service (49)** : `AttendanceService` (P1/P2) → `CommentService` (P1 + fanOut) → `CommentLikeService` (P4) → publishers (P3) → DTO/entity ponctuels.
5. **moderation-service (15)** : `ReportService` (P1/P4/P2 + gardes).
6. **notification-service (10)** : `NewCommentConsumer` (P6) → `UserFollow{Requested,Accepted}Consumer` (P6).
7. **Intégration & CI** : pousser sur `claude/relaxed-shannon-4SWbr` → CI matrix (5 services) + `sonar-aggregate` → **itérer sur le rapport Sonar de la PR jusqu'à ≥ 95 % L agrégé** + gate vert (new-code ≥ 80 %, duplication ≤ 3 %, ratings A). Si le pool §4 ne suffit pas, étendre via les lignes `ci="0"` résiduelles du JaCoCo (branches resource, accessors d'entités, DTO mappers).

---

## 6. Ordre de réalisation & dépendances
- **Modules disjoints, parallélisables** (fichiers de test séparés, aucune dépendance inter-module).
- **Gros gains d'abord** : user-service + event-service (≈ 156 des ~230 lignes). Au sein d'un service, **classes `Service` denses avant les fichiers gros/épars** (`EventService` en dernier).
- **Vérification = CI** (pas de Docker local). En local : `./mvnw test-compile` vert + relecture du JaCoCo téléchargé.
- **Bug applicatif** éventuel : **arrêter, signaler** dans la PR (ne pas corriger sans accord).

---

## 7. Checklist finale de validation
- [ ] Familles DRY (P1–P7) factorisées — pas de copier-coller de blocs `given()…then()`.
- [ ] **user-service** : `UserService` catches (P4/P5) + `IcsBuilder` (P7) + `FollowService` (P4) + `User` finders + `UserResource` (admin) + `UsernameGenerator` + `CalendarService` (P5) + `FollowLifecyclePublisher` (P3) couverts.
- [ ] **event-service** : `EventCoOrganizerService` (P1/P2) + `EventLifecyclePublisher` (P3 ×4) + `FavoriteService` (P4/P2) + `EventResource` (401/400) + `EventService` (P2 + branches) + ponctuels.
- [ ] **engagement** : `AttendanceService` + `CommentService` (P1) + `CommentLikeService` (P4) + publishers (P3).
- [ ] **moderation** : `ReportService` (P1/P4/P2). **Pas** de re-test outbox/kafka (déjà PR #199).
- [ ] **notification** : `NewCommentConsumer` + `UserFollow{Requested,Accepted}Consumer` (P6).
- [ ] `git diff origin/main HEAD -- openapi/` = **0 ligne** ; **aucune** migration Flyway ; **aucun** fichier sous `*/src/main/` (`git diff --stat` ne touche que `*/src/test/`).
- [ ] Titre PR = **`test(backend): …`** ; PR = **#199** (même branche).
- [ ] Tests **DRY** — duplication nouveau code ≤ 3 %.
- [ ] `./mvnw test-compile` vert en local.
- [ ] **CI verte** : matrix (5 services) + `sonar-aggregate` ; gate SonarCloud franchi.
- [ ] **Couverture agrégée JaCoCo/Sonar ≥ 95 % L** — vérifiée sur le rapport Sonar de la PR.
- [ ] Aucun bug applicatif corrigé en douce ; tout bug rencontré est **signalé** dans la PR.
