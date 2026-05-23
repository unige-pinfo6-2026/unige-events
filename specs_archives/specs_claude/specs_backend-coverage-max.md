# SPEC — Couverture de tests backend MAXIMALE (sans aucune exclusion)

| Champ | Valeur |
|---|---|
| Périmètre | **Backend uniquement** — pousser la couverture (JaCoCo/Sonar, 15 modules) au **maximum atteignable par TESTS**, **sans AUCUNE exclusion**. On **AJOUTE** des tests (et on convertit des tests plain en `@QuarkusTest` si besoin) ; **aucune** modif de code applicatif. |
| Branche de travail | **`feature/backend-coverage-max`** — **nouvelle** branche depuis `main`. PR #199 est **MERGÉE** (`93cce7a9`) ; on n'y retourne pas. **Nouvelle PR** vers `main`. |
| Base | `origin/main`. Tip à la rédaction : `93cce7a9` (`Merge pull request #199 …`), contenu identique à `8bd2552e`. |
| Sprint | S10 — durcissement couverture, **3ᵉ passe** (PR #199 a livré la 2ᵉ passe : 89 % → **96,7 % overall Sonar** / **98,83 % L + 90,20 % B** sur classes possédées). |
| Objectif chiffré | **Maximiser la couverture overall Sonar** (blend lignes+branches). Cible réaliste **≥ 99,5 %** par tests honnêtes ; **~99,95 %** si les tests « artificiels » optionnels sont ajoutés ; **100 % structurellement impossible** (cf. §6). **ZÉRO exclusion.** |
| Tickets liés | Aucun ticket fonctionnel (dette de test). PR de type `test`. |
| Auteur spec | Elie Bussod (rédaction assistée Claude). |
| Date | 2026-05-23 |
| Règle d'or | **Aucune modif `src/main`** (ni `openapi/`, ni Flyway, ni pom applicatif). **ZÉRO exclusion** : `sonar.coverage.exclusions`, `@Generated`, jacoco `<excludes>` = **INTERDITS** (décision PO). Les lignes/branches **inatteignables par test honnête** sont **documentées comme plafond accepté** (§6), **pas forcées, pas exclues**. **Tests DRY** (gate Sonar : duplication ≤ 3 % + couverture ≥ 80 % sur le nouveau code). Si un test révèle un **bug applicatif**, le **SIGNALER** (ne pas corriger sans accord). |

> **IMPÉRATIF de livrable.** Spec-only. L'implémentation produira **une seule** branche `feature/backend-coverage-max` + **une** PR. Tous les chemins/lignes sont **vérifiés au source réel** sur le **JaCoCo post-merge PR #199** (run CI `26327970082`, `main` @ `93cce7a9`) — pas l'audit pré-merge du run `26327802834`.
>
> **Cette spec succède à [`specs_backend-coverage-95-v2.md`](specs_backend-coverage-95-v2.md)** (objectif ≥ 95 %, ATTEINT et dépassé par PR #199). La présente passe vise le **maximum sans exclusion**, ce qui force à traiter **chaque** ligne/branche restante : soit elle est couvrable (classe **A**/**B**), soit elle est un **plafond structurel documenté** (classe **C**).

---

## 1. Vue d'ensemble

PR #199 a fermé l'essentiel. Il **reste 36 lignes NC + 131 branches manquées** sur les **classes possédées** (package `ch/unige/events/<svc>/…`). Mesure ligne-précise au tip `93cce7a9` (run `26327970082`), classes possédées uniquement (les classes `shared/` et cross-service apparaissent « partielles » dans un rapport donné mais sont **couvertes via l'union Sonar** — ne PAS les recibler) :

| Service | Lignes (cov/total) | Branches (cov/total) | Lignes NC | Branches NC | Classes dominantes (NC restant) |
|---|---|---|---|---|---|
| **user-service** | 641/649 = **98,77 %** | 256/279 = **91,76 %** | 8 | 23 | `UserResource` (uploads) · `UserService` (branches) · `UsernameGenerator` · `FollowResource` · `IcsBuilder` · `FollowService` · `CalendarService` · 2 internal-resources |
| **event-service** | 1226/1243 = **98,63 %** | 476/544 = **87,50 %** | 17 | 68 | `EventService` (8 NC + 29 branches) · `EventCoOrganizerService` (6 NC + 8 br) · `FeaturedService` · `EventResource` · `ShareService` · `EventBannedEventDeserializer` · 4×`EventDTO` · divers |
| **engagement-service** | 634/638 = **99,37 %** | 277/310 = **89,35 %** | 4 | 33 | `CommentService` (1 NC + 15 br) · `AttendanceService` · `CommentLikeService` · 2 entités · 3 resources |
| **moderation-service** | 251/252 = **99,60 %** | 75/80 = **93,75 %** | 1 | 5 | `ReportService` (comment-only / null-reporter / null-name) |
| **notification-service** | 292/298 = **97,99 %** | 122/124 = **98,39 %** | 6 | 2 | `NewCommentConsumer` (2 catch hand-wirés) · `UserFollowedConsumer` · `CommentMentionConsumer` |
| **OWNED TOTAL** | 3044/3080 = **98,83 %** | 1206/1337 = **90,20 %** | **36** | **131** | — |
| **shared** (10 libs) | **100 % L** | — | **0** | — | **RIEN À FAIRE** (déjà fermé) |

**Blend lignes+branches possédé = 96,22 %** ; overall Sonar ≈ 96,7 % (relevé par les libs shared à 100 %). Fermer ces 36 lignes + 131 branches porte le blend possédé vers **≥ 99,5 %** (tests honnêtes) et l'overall Sonar au-delà.

**Décompte par classe de couvrabilité** (un « item » = une ligne/branche ou un groupe partageant le **même** correctif) :

| Classe | Définition | ~Nb items | Action spec |
|---|---|---|---|
| **A** | Atteignable par un test public normal (`@QuarkusTest` RestAssured ou appel service) avec entrées ordinaires | ~30 | À ÉCRIRE (priorité). |
| **B** | Atteignable mais nécessite un **mécanisme** (hand-wiring FT-bypass, `mock(CallerIdentity)→null`, PanacheMock, `@RestClient→null`, multipart+`@InjectMock`, exception nommée, appel direct de bean) | ~25 | À ÉCRIRE (priorité). |
| **B-réflexion (OPTIONNEL)** | Atteignable **uniquement** par réflexion sur méthode privée **ou** en forgeant un état logiquement impossible (PanacheMock) → **test artificiel** | ~12 | **Documenté avec recette, marqué OPTIONNEL — à NE PAS écrire par défaut** (décision PO, cf. D3/§6). |
| **C** | **Structurellement inatteignable** par tout test honnête **et** par réflexion/forge — seul un refactor `src/main` (interdit) la fermerait | 2 | **Plafond accepté documenté** (§6). Ni forcé, ni exclu. |

> **Cap réaliste** : **≥ 99,5 %** blend possédé avec A+B seuls (Tier-2 laissé ouvert) ; **~99,95 %** si l'on ajoute les Tier-2 artificiels ; **100 % impossible** (les 2 items C). Validé par recomptage JaCoCo (§6).

---

## 2. Contexte — mécanismes déterminants (vérifiés empiriquement)

Tous confirmés au source/tests réels lors de la rédaction.

1. **`quarkus-jacoco` ne compte QUE le code exécuté sous `@QuarkusTest`.** Un test Mockito *plain* passe mais contribue **ZÉRO** couverture. → ajouter des cas aux classes `@QuarkusTest`, **convertir** un test plain en `@QuarkusTest`, ou exécuter le code testé depuis une classe `@QuarkusTest`. **Cas réel détecté** : `engagement … comment/entity/CommentTest` est *plain JUnit* — c'est **la seule** raison pour laquelle `Comment.java:66` (`@PrePersist`) reste partielle alors que `CommentTest` teste les deux bras. Idem `CommentLike.java:64` (aucun test d'entité).
2. **Fault Tolerance** : les `@RestClient` (`UserServiceClient`, `EventServiceClient`, `EngagementServiceClient`) sont enveloppés par `@Fallback`. Un `catch` autour d'un appel `@RestClient` **n'est PAS atteint** via `@InjectMock @RestClient` + `thenThrow` (le fallback intercepte → retourne la valeur dégradée, p.ex. `null` ; l'exécution saute sur le `if (x == null)`, **pas** sur le `catch`). On l'atteint par **HAND-WIRING** : `new Service()` + affecter le champ `@Inject` **package-private** à un **mock Mockito simple** (sans FT) qui jette, **OU** construire via le **constructeur public** (cas `NewCommentConsumer`, `CommentLikeService`, `EventViewService`). Le test hand-wiré **doit vivre dans une classe `@QuarkusTest`** (sinon mécanisme 1). Modèles confirmés : `user … service/UserServiceExceptionPathsTest`, `event … favorite/service/FavoriteServiceExceptionPathsTest`, `event … coorganizer/service/EventCoOrganizerServiceSafeGetUserTest`, `moderation … service/ReportServiceUnitTest` (qui **EST** `@QuarkusTest` malgré une Javadoc trompeuse), `engagement … integration/EngagementServiceClientFallbackWiringTest`.
3. **Gardes `if (requireUuid() == null) throw`** : `CallerIdentity.requireUuid()` (shared) ne renvoie **jamais** null — il **lève** `NotFoundException`. Donc via le vrai bean ces gardes paraissent « mortes » (le test « anonyme » couvre le *throw du bean*, **pas** la ligne `== null` + son corps). On les atteint en **hand-wiring** avec `mock(CallerIdentity.class)` dont `requireUuid()` renvoie `null`. **Prouvé** : `moderation … ReportServiceUnitTest` (`mockCaller(null)`). Distinguer de `getUuid()` qui **renvoie** null pour un anonyme (donc les gardes basées sur `getUuid()` sont **A**, atteignables par JWT anonyme — ex. `ShareService`, `getAttendees`/`getByEvent`).
4. **`PanacheMock.mock(Entity.class)`** pour stubber les finders statiques ET injecter des entités **forgées** (champs null, orphelins). **`@TestTransaction`** OBLIGATOIRE si la méthode testée appelle `entity.persist()` (sinon `TransactionRequiredException`).
5. **Branches « summary == null »** (dégradation engagement) : couvertes **SANS** hand-wiring via `@InjectMock @RestClient EngagementServiceClient` + `getAttendanceSummary(...)/getAttendanceSummariesBulk(...)` → `thenReturn(null)` (null **EST** la valeur de fallback). Modèle : `EventServiceTest.findByIds_summariesNullClient_safe`, `FeaturedServiceTest.getFeatured_summariesNullClient_safe`.
6. **Multipart + `@InjectMock FileStorageService`** pour les happy-paths d'upload (`RestAssured.multiPart(...)`). **`UserResourceTest` n'a PAS encore d'`@InjectMock FileStorageService`** — à AJOUTER.
7. **Réflexion sur méthode privée** (pattern DÉJÀ admis : `engagement … AttendanceServiceTest.acquireAdvisoryLock_nullEventId_throwsIllegalState`) pour les gardes défensives qu'aucun chemin public ni hand-wiring ne peut atteindre (ex. `safeGetUser(null)`). **Dernier recours**, **classé explicitement OPTIONNEL** (§3 D3 / §6).
8. **Exception à nom personnalisé** pour les branches `className.contains("OptimisticLock"/"StaleState")` (modèle CONFIRMÉ `user … CalendarServiceTest.VendorOptimisticLockException` ; idem `UserServiceExceptionPathsTest.OptimisticLockMarkerException`). Le bras `OptimisticLock` court-circuite le `||` → le bras `StaleState` exige une **2ᵉ** exception nommée `…StaleState…`.
9. **Pas de Docker en local** → les `@QuarkusTest` ne tournent qu'**en CI**. En local : `./mvnw -o -pl services/<svc>-service test-compile` (offline, après un `./mvnw -q install -DskipTests` initial). Boucle : pousser → CI → `gh run download <id> --repo unige-pinfo6-2026/unige-events --pattern 'jacoco-*-service' --dir <dir>` → parser `jacoco.xml` (XML 1 ligne ; via perl/awk/python-regex ; `<line nr ci="0" mi>0/>` = ligne NC, `mb>0` = branche partielle) en **filtrant le package possédé** `ch/unige/events/<svc>/` → itérer.

### 2.1 No-Docker / vérification = CI
Les 5 services exigent Docker (DevServices Postgres + Kafka in-memory) → `./mvnw verify` ne tourne pas en local. Le `%` se vérifie **en CI** (matrix par service + `sonar-aggregate`). En local : `./mvnw -o … test-compile` doit rester vert. Les artifacts `jacoco-*-service` sont uploadés dès la fin des jobs de test.

---

## 3. Décisions tranchées (NE PAS revisiter)

### D1 — Type, branche, PR
**Nouvelle branche `feature/backend-coverage-max` depuis `main` ; nouvelle PR vers `main` ; titre `test(backend): maximise coverage without exclusions`** (type `test` → scope libre, validé par [`pr-title-check.yml`](/.github/workflows/pr-title-check.yml)). PR #199 est mergée : on n'y retourne pas.

### D2 — ZÉRO exclusion
**Interdits absolus** : `sonar.coverage.exclusions`, `@Generated`, jacoco `<excludes>`, tout autre masquage. La couverture monte **uniquement** par tests. Les items **C** (§6) sont **documentés**, **jamais** exclus ni « forcés » par un hack.

### D3 — Réflexion / forge sur privés = OPTIONNEL, listé, minimisé
La réflexion sur méthode privée (mécanisme 7) et la **forge d'état logiquement impossible** via PanacheMock (ex. une ligne dont l'`id`/`auth0Id` se contredisent) produisent des tests **artificiels**. **Décision PO** : ces ~12 cibles (Tier-2, §6) sont **documentées avec leur recette mais marquées OPTIONNEL — NON écrites par défaut**. La cible **baseline** est donc **A+B uniquement (~99,5 %)**. Le PO peut ré-autoriser le Tier-2 cible par cible (→ ~99,95 %). Aucune n'est exclue.

### D4 — DRY (gate duplication ≤ 3 % nouveau code)
Factoriser **agressivement** les mécanismes répétés : un helper par famille (`handWired(Service)` + setter de champ ; `mockCaller(null)` ; `panacheForge(Entity, …)` ; `clientReturnsNull()` ; `clientThrows()` ; `staleStateException()` ; `multipartUpload(...)`). Réutiliser les `JwtTestHelper`/`JwtTestContext`/`TestCallerIdentity` **du service**. Un copier-coller des blocs `given()…then()` ferait **échouer** le gate.

### D5 — Priorisation (gros lots B d'abord, A dispersés ensuite, C documentés à la fin)
1. **Gros lots B** : `EventService` ~16 ternaries summary-null (mécanisme 5) ; ~10 gardes `requireUuid()==null` (`EventService` 215/216 + `EventCoOrganizerService` ×4 ; mécanisme 3) ; uploads `UserResource` (mécanisme 6) ; `NewCommentConsumer` ×2 catch (mécanisme 2).
2. **A dispersés** : resources (401/anonyme/admin), branches null/blank, IcsBuilder, entités, deserializer, DTO tags-null.
3. **C** documentés (§6). **Tier-2 OPTIONNEL** non écrit (D3).

---

## 4. Analyse par classe (ligne-précise — A / B / B-réflexion(OPT) / C)

> Légende : **[+]** compléter un test existant · **[C]** créer un fichier · **[~]** convertir un test plain en `@QuarkusTest`. Lignes = run `26327970082`. **Confirmer au JaCoCo frais avant chaque module** (les n° peuvent bouger).

### 4.1 — user-service (8 NC + 23 branches)

| Cible | Classe | Approche |
|---|---|---|
| `resource/UserResource` **130-132** (`uploadImage` happy-path) + **154-156** (`uploadBanner`) ; branches **127**/**151** (`file==null` false) | **B** | **[+]** `UserResourceTest` : AJOUTER `@InjectMock FileStorageService`, stub `saveImage(...)`, persister l'user, `multiPart("file",bytes,"image/jpeg").post("/users/me/image")` & `/me/banner` → 200. Couvre NC + le bras `false` des deux gardes en un coup (mécanisme 6). |
| `resource/UserResource` branche **91** (`anonymous \|\| restricted` — 1/4) | **A** | **[+]** test authed **non-self non-admin** sur profil privé via UUID : `GET /users/{id}` → 200 + payload restreint. |
| `resource/UserResource` branche **247** (`!anonymous && hasRole(ADMIN)` dans `getByUsername`) | **A** | **[+]** `@TestSecurity(roles={"ADMIN"})` `GET /users/by-username/{handle}` profil privé → projection complète. |
| `service/UserService` branche **100** (`auth0Id==null`/`target==null` dans `updateMyProfile`) | **A** | **[+]** `UserServiceTest` `@TestTransaction` : `updateMyProfile(null, x, req)` et `(x, null, req)` → `ForbiddenException`. |
| `service/UserService` branches **115**/**118** (`displayName==null` / `faculty!=null`) | **A** | **[+]** un appel `updateMyProfile` avec `displayName==null` MAIS `faculty!=null` (les deux bras manquants en un test). |
| `service/UserService` branche **336** (`message==null` dans `containsMessage`) | **B** | **[+]** `UserServiceExceptionPathsTest` : `flush()` jette une `PersistenceException` avec un maillon **message null** (`new PersistenceException("w", new RuntimeException((String)null))`) → rethrow (pas un conflit). |
| `service/UserService` branche **351** (`StaleState` côté de `\|\|`) | **B** | **[+]** AJOUTER `StaleStateMarkerException extends PersistenceException` (nom contient `StaleState`, pas `OptimisticLock`) jetée par `flush()` → **409** (mécanisme 8). |
| `follow/service/FollowService` branche **90** (`message==null` dans `isUniqueFollowConflict`) | **B** | **[+]** `FollowServiceExceptionPathsTest` : `PersistenceException` message null → rethrow. |
| `calendar/util/IcsBuilder` **39** + branche **38** (`startDate!=null && endDate==null`) | **A** | **[+]** `IcsBuilderTest` : `EventDTO` `startDate` non-null + `endDate==null` → `continue` (skip). |
| `calendar/service/CalendarService` branche **194** (`StaleState` côté `\|\|`) | **B** | **[+]** `CalendarServiceTest` : ajouter `VendorStaleStateException` (jumelle de `VendorOptimisticLockException`) → retry+recovery (mécanisme 8). |
| `resource/FollowResource` branches **136**(self-in-list)/**137** (`!profilePublic && isSelf`) | **A** | **[+]** `FollowResourceTest` : le caller (profil **privé**) figure dans sa **propre** liste `following`/`followers` → `isSelf` true. |
| `resource/UserUsernamesInternalResource` branche **55** + `resource/UserAttendeeProjectionInternalResource` branche **45** (`csv/ids == null`) | **B** | **[+]** appel **direct du bean injecté** sous `@QuarkusTest` : `getByUsernames(null)` / `getAttendeeProjections(null)` → `List.of()` (le `@QueryParam List` JAX-RS n'injecte jamais `null` en HTTP — donc on couvre le bras `==null` par appel méthode direct, **pas** par requête HTTP). |
| `service/UsernameGenerator` **183** + branches **182**/**202** | **C → B-réflexion (OPT)** | `preTranslateLatinExt(null/"")` et `pickFirstNonBlank(second==null)` : `slugify` ne passe **jamais** null/vide (worst-case `"user"` ; `joinNonBlank` rend `""` pas `null`). Inatteignable par chemin public → **plafond** (§6), ou réflexion artificielle si ré-autorisé. |
| `resource/FollowResource` branche **136** sous-cas `callerAuth0Id==null` ; `service/UserService` branches **220**/**311** | **C → B-réflexion/forge (OPT)** | `FollowResource` `@Authenticated` → principal jamais null ; `UserService` 311/220 exigent une ligne dont `auth0Id≠caller` mais `id==caller.id` (état impossible) → forge PanacheMock artificielle. **Plafond** (§6). |

### 4.2 — event-service (17 NC + 68 branches)

| Cible | Classe | Approche |
|---|---|---|
| `service/EventService` ternaries summary-null **319/320** (`getById`), **445/446** (`update`), **539/540** (`cancel`), **563/564** (`restore`), **705/706** (`publish`), **725/726** (`uploadImage`) + **758/759** (`toEventDTOs` bulk null) | **B** | **[+]** `EventServiceTest` (ou companion `@QuarkusTest` hand-wiré) : `@InjectMock @RestClient EngagementServiceClient` `getAttendanceSummary→null` (resp. `…Bulk→null`), un `@TestTransaction` par méthode dans le bon statut → asserter `attending==0 && waitlisted==0` (mécanisme 5). **Paramétrer** pour DRY. |
| `service/EventService` **215**/**216** (garde `requireUuid()==null` dans `persistParent`) | **B** | **[+]** hand-wiring `new EventService()` + `mock(CallerIdentity).requireUuid()→null` + `@TestTransaction`/PanacheMock → `create(...)` → `NotFoundException` (mécanisme 3). |
| `service/EventService` **635**/**636-642** (cap `duplicate` > 100 → 422) | **B** | **[+]** `PanacheMock.mock(Event.class)` : `Event.count(…)` toujours `>0` → boucle dépasse 100 → `WebApplicationException` 422 (mécanisme 4). |
| `service/EventService` branches **163** (recurrence end/start), **210** (occurrence visibility non-admin), **265** (`parent.tags==null`), **396** (`creatorId==null` dans `getOrganizerUuids`), **626** (`source.title==null` dans `duplicate`), **661** (tags clone), **689** (`publish` statut ≠DRAFT/PUBLISHED), **732/735/741/746** (validation publish : `isBlank()` non-null), **797** (`isCreator` legs), **811** (`isCreatorOrAcceptedCoOrganizer` `event==null`/`callerUuid==null` — méthode **publique** → appel direct) | **A** | **[+]** entrées ordinaires qui basculent chaque court-circuit (créateur non-admin sur DRAFT ; tags null ; title null ; publish CANCELLED → 409 ; champs blank non-null ; `service.isCreatorOrAcceptedCoOrganizer(null, uuid)` & `(event, null)`). |
| `coorganizer/service/EventCoOrganizerService` **56/57**, **92/93**, **112/113**, **164/165** (gardes `requireUuid()==null` de `invite`/`accept`/`decline`/`getMyInvitations`) | **B** | **[+]** `EventCoOrganizerServiceSafeGetUserTest`-style : hand-wiring + `mock(CallerIdentity)→null` ; `invite` exige `Event.findByIdOptional` stubbé en amont (mécanisme 3). |
| `coorganizer/service/EventCoOrganizerService` branche **180** + **223**/**226**/**227** (invitation orpheline : `EventCoOrganizer` PENDING dont l'`eventId` n'a pas d'`Event`) | **B** | **[+]** `@TestTransaction` : persister une ligne PENDING danglante → `getMyInvitations` filtre le DTO (ternary `: null`) et `findByIdsAsDTO` retourne `Map.of()` (mécanisme 4). |
| `coorganizer/service/EventCoOrganizerService` branche **264** (`isCreator` legs null) | **A/B** | **[+]** legs créateur/non-créateur (A) ; `requesterId==null` via `mock(CallerIdentity)` dans le test `remove` hand-wiré (B). |
| `kafka/EventBannedEventDeserializer` **8**/**9** (ctor) | **B** | **[C]** `EventBannedEventDeserializerTest` `@QuarkusTest` : `new EventBannedEventDeserializer()` (+ `deserialize` d'un échantillon). |
| `resource/EventResource` **184** (`getOrganizerUuids` 200) | **A** | **[+]** `GET /events/{id}/organizer-uuids` sur event PUBLISHED → 200 + creator UUID. |
| `resource/EventResource` branches **97** (ids vide), **155** (anonyme + `check-co-org-of`), **195** (anonyme `/occurrences`) | **A** | **[+]** `GET /events?ids=` vide ; `?check-co-org-of=` sans JWT ; `/occurrences` anonyme. |
| `resource/EventResource` branche **157** (`callerUuid==null` avec param) | **B** | **[+]** `TestCallerIdentity` `getUuid()→null` (authed mais profil non résolu). |
| `view/resource/EventViewResource` branche **45** (`body==null`) | **A** | **[+]** `POST /events/{id}/view` corps JSON vide → `body==null`. |
| `view/service/EventViewService` branche **60** (`auth0Id!=null` mais `userId==null`) | **B** | **[+]** `new EventViewService(em, mockCaller)` (ctor injection) `getUuid()→null` → fallthrough. |
| `service/FeaturedService` branches **67** (candidates vide), **129** (events vide) | **A** | **[+]** phase-1 remplit la limite (phase-2 vide) ; liste vide. |
| `service/FeaturedService` branches **114**/**115** (single summary null), **124** (bulk null) | **B** | **[+]** `getAttendanceSummary→null` / `…Bulk→null` (mécanisme 5). |
| `attachment/service/EventAttachmentService` branche **219** (`event==null`) | **B** | **[+]** PanacheMock : `EventAttachment` valide mais `Event.findById(eventId)→null` → `download` retourne null. |
| `share/service/ShareService` branches **67**/**68**/**71** (`callerUuid==null` + legs statut) | **A** | **[+]** appel `getShareInfo` **anonyme** (`getUuid()` rend null) sur event PUBLISHED + non-PUBLISHED (mécanisme 3, côté `getUuid`). |
| `coorganizer/entity/EventCoOrganizer` branche **53** (`invitedAt!=null` dans `@PrePersist`) | **A** | **[+]** sous `@QuarkusTest` : pré-fixer `invitedAt` avant persist → non écrasé. |
| `favorite/service/FavoriteService` branche **90** (`name==null` dans `isUniqueFavoriteConflict`) | **B** | **[+]** `FavoriteServiceExceptionPathsTest` : `ConstraintViolationException` `getConstraintName()→null` → rethrow. |
| `dto/EventDTO` **151** · `me/dto/EventDTO` **105** · `coorganizer/dto/EventDTO` **105** · `favorite/dto/EventDTO` **110** (`tags==null ? List.of()`) | **A** | **[+]** `from(...)` avec `Event.tags==null` → DTO tags vide. **Vérifier que les `*EventDTOTest` sont `@QuarkusTest`** ; sinon couvrir le bras null-tags via un appel service `@QuarkusTest` (mécanisme 1). |
| `service/EventService` **134** (`!conditions.isEmpty()`) ; `service/EventService` **828** (overload privé `(Event,String)` `@SuppressWarnings("unused")`) ; `kafka/EventLifecycleKafkaBridge` branche **26** (default switch enum) ; `service/FeaturedService` branche **139** (`countFavorites` vide) ; `coorganizer/…Service` branche **222** (`ids==null`) ; `stats/service/EventStatsService` branche **52** (`event==null`) | **C / B-réflexion(OPT)** | Voir §6. **134** + **bridge 26** = **C pur** (refactor seul, interdit) ; les autres = privés dont le caller garantit l'invariant → réflexion artificielle **OPTIONNELLE**. |

### 4.3 — engagement-service (4 NC + 33 branches)

| Cible | Classe | Approche |
|---|---|---|
| `attendance/service/AttendanceService` **244** + branche **241** (tri-state `coOrgOf==null` quand caller==créateur, ou anonyme) | **A** | **[+]** `AttendanceServiceTest` : `coOrgOf==null` avec `creatorId==userId` (et variante anonyme) → ligne 244 + asserter `getOrganizerUuids` `never()` appelé. |
| `attendance/service/AttendanceService` branche **277** (`p==null \|\| p.id()==null`) | **A** | **[+]** projection liste contenant un `null` et un `AttendeeProjection(null,…)` → anonymisé. |
| `attendance/service/AttendanceService` branche **92** (`registrationDeadline` future) | **A** | **[+]** `attend` avec deadline `now().plusDays(1)` → procède. |
| `attendance/service/AttendanceService` branche **176** (`capacity==null` dans `removeAttendance`) | **A** | **[+]** retrait d'un ATTENDING sur event `capacity==null` → pas de promotion. |
| `comment/service/CommentService` branches **165**/**168**/**195** (eventTitle null/blank, mention `target==null`, username blank) | **A** | **[+]** `CommentServiceTest` : EventDTO `title=null`/`"  "` ; liste mentions avec `null` ; author username `"  "`. |
| `comment/service/CommentService` branche **209** (`authorId==null`), **213** (admin / `eventId==null`), **217**/**348** (admin bypass), **226** (admin `getByEvent`), **252**/**255**/**271**/**276** (rows `authorId==null`) | **A** | **[+]** seeds `authorId==null` ; `@TestSecurity(roles="ADMIN")` sur DRAFT (delete + getByEvent). |
| `comment/service/CommentService` branche **97** (`parent.eventId==null`) | **B** | **[+]** PanacheMock parent `eventId=null` → 422 `parent_comment_not_in_event`. |
| `comment/service/CommentService` branche **209**/**213** sous-cas `callerUuid==null` | **B** | **[+]** `CommentServiceSafeGetUserTest` : `mock(CallerIdentity).requireUuid()→null` (mécanisme 3). |
| `comment/service/CommentLikeService` **128** + branche **127** (`comment.id==null` dans `unlike`) | **B** | **[+]** `CommentLikeServiceTest` : `PanacheMock.mock(Comment.class)` id null + hand-wiring ctor `new CommentLikeService(ci, ec, em)` (mécanismes 2/4). |
| `comment/service/CommentLikeService` branche **138** (`eventId==null` dans `assertEventVisible`) | **B** | **[+]** PanacheMock Comment `eventId=null` → `like` → 404 (le service injecté suffit, garde atteinte avant tout `@RestClient`). |
| `comment/entity/Comment` branche **66** (`createdAt!=null`) | **A** | **[~]** **convertir** `CommentTest` plain → `@QuarkusTest` (il teste déjà les deux bras ; mécanisme 1). |
| `comment/entity/CommentLike` branche **64** (`createdAt!=null`) | **A** | **[C]** `CommentLikeEntityTest` `@QuarkusTest` : bras null + bras pré-fixé. |
| `attendance/resource/UserParticipationsResource` branche **64** (`raw` blank) · `attendance/resource/MyAttendancesResource` branche **61** (`raw==null`) | **A** | **[+]** `?timeframe=` vide (self) ; requête sans param `timeframe`. |
| `attendance/resource/AttendanceSummaryInternalResource` branche **61** (`ids==null`) | **B** | **[+]** appel **bean direct** `new AttendanceSummaryInternalResource().getBulkAttendanceSummary(null)` → `Map.of()` (le `@QueryParam List` JAX-RS n'injecte jamais null). |
| `attendance/service/AttendanceService` **442/443** (`safeGetUser(null)`) · `comment/service/CommentService` **382/383** (`safeGetUser(null)`) · `comment/service/CommentService` branche **358** (`event==null`) | **C / B-réflexion(OPT)** | Gardes de privés qu'aucun chemin public ne passe (callers garantissent non-null). Réflexion artificielle (pattern `acquireAdvisoryLock` admis) → **OPTIONNEL** (§6). |

### 4.4 — moderation-service (1 NC + 5 branches)

| Cible | Classe | Approche |
|---|---|---|
| `service/ReportService` **309** + branche **308** (`bulkFetchEvents` set vide) + branche **236** (`r.eventId==null`) | **A** | **[+]** `ReportServiceTest` (groupe `listByStatus`, harnais PanacheMock déjà présent) : un **report comment-only** (`eventId=null`) → `eventIds` vide → court-circuit. Asserter 1 élément `eventTitle()==null` + `verify(eventClient, never()).findByIds(...)`. **Vérifié** : `ReportDTO.from(report, null, …)` est null-safe (pas de NPE) — la crainte de l'audit ne s'applique pas au code courant. |
| `service/ReportService` branche **237** (`r.reporterId==null`) | **A** | **[+]** un report `reporterId=null` via `listByStatus`. |
| `service/ReportService` branche **244** (`u==null` dans la boucle d'enrichissement) | **B** | **[+]** report `reporterId` non-null mais `userClient.getById→NotFoundException` (donc `safeGetUser→null`) → bras `u==null`. |
| `service/ReportService` branche **213** (`name==null` dans `isUniqueReportCommentConflict`) | **A** | **[+]** `ReportServiceCreateForCommentTest` (appel direct du helper package-private) : `ConstraintViolationException(msg, sqlEx, (String)null)` → `false`. Ctor 3-args **confirmé** dans hibernate-core 7.3.2 (accepte un nom null). |

> **Aucun C ni réflexion** côté moderation : les 6 cibles passent par les harnais `@QuarkusTest` existants (`ReportServiceTest`, `ReportServiceCreateForCommentTest`) avec de petits ajouts.

### 4.5 — notification-service (6 NC + 2 branches)

| Cible | Classe | Approche |
|---|---|---|
| `kafka/NewCommentConsumer` **91/92/93** (catch `eventClient.getById`) | **B** | **[+]** `NewCommentConsumerTest` : AJOUTER `@Inject NotificationService` ; hand-wire `new NewCommentConsumer(notif, userMockSimple, eventMockSimple)` (mocks SIMPLES qui jettent) ; appel `onCommentCreated(ev)` sous `QuarkusTransaction.requiringNew().run(...)` (méthode `@Transactional`). Le test existant `@InjectMock @RestClient`+`thenThrow` **ne** couvre **pas** le catch (FT route vers fallback). Ctor confirmé : `(NotificationService, @RestClient UserServiceClient userClient, @RestClient EventServiceClient eventClient)` (mécanisme 2). |
| `kafka/NewCommentConsumer` **150/151/153** (catch dans `resolveAuthorLabel`) | **B** | **[+]** mêmes mocks ; appel **direct** `hw.resolveAuthorLabel(id)` (package-private, **non** `@Transactional`) → `FALLBACK_AUTHOR_LABEL`. |
| `kafka/UserFollowedConsumer` branche **85** (`displayName()==null` — 2ᵉ opérande de `\|\|`) | **B** | **[+]** `UserFollowedConsumerTest` : `UserPublicResponse.anonymous(id, username, null, null)` (follower non-null, `displayName==null`) → message générique. |
| `kafka/CommentMentionConsumer` branche **52** (`authorId==null`) | **A** | **[+]** `CommentMentionConsumerTest` : event `authorId==null`, `mentionedUserId!=null` → garde non déclenchée, notification créée. |

### 4.6 — shared : RIEN
Les 10 libs sont à **100 % L** au tip `93cce7a9`. Ne pas y toucher.

### 4.7 — Fichiers de test à réutiliser (modèles confirmés)
| Pattern | Référence réelle |
|---|---|
| Hand-wiring FT-bypass (champ package-private) | `user … UserServiceExceptionPathsTest` · `event … FavoriteServiceExceptionPathsTest` · `event … EventCoOrganizerServiceSafeGetUserTest` |
| Hand-wiring via ctor public + `QuarkusTransaction.requiringNew()` | `notification … NewCommentConsumerTest` (à étendre) · `engagement … CommentLikeServiceTest.like_concurrentInsertRace` |
| `mock(CallerIdentity).requireUuid()→null` (`buildService`/`mockCaller`) | `moderation … ReportServiceUnitTest` (**`@QuarkusTest`**) |
| `PanacheMock.mock(Entity)` + finders/forge + `@TestTransaction` | `user … UserServiceExceptionPathsTest` · `moderation … ReportServiceTest` |
| `@InjectMock @RestClient … thenReturn(null)` (summary-null) | `event … EventServiceTest.findByIds_summariesNullClient_safe` · `FeaturedServiceTest.getFeatured_summariesNullClient_safe` |
| Exception nommée OptimisticLock/StaleState | `user … CalendarServiceTest.VendorOptimisticLockException` · `UserServiceExceptionPathsTest.OptimisticLockMarkerException` |
| Réflexion sur privé (pattern admis) | `engagement … AttendanceServiceTest.acquireAdvisoryLock_nullEventId_throwsIllegalState` |
| Util static pur (sous `@QuarkusTest` pour compter) | `user … IcsBuilderTest` · `engagement … MentionParserTest` |

---

## 5. Étapes d'implémentation (ORDONNÉES)

> Pré-requis : créer **`feature/backend-coverage-max`** depuis `origin/main`. Récupérer le JaCoCo frais (§2.1) avant chaque module. Validation **en CI** ; en local `./mvnw -o … test-compile` vert.

0. **Familles DRY (D4)** — helpers réutilisables dans le `test/` de chaque service : `handWired(...)`+setter champ, `mockCaller(null)`, `clientThrows()`, `clientReturnsNull()`, `panacheForge(...)`, `staleStateException()`, `multipartUpload(...)`.
1. **event-service (le plus gros : 17 NC + 68 br)** : ternaries summary-null (B, paramétré) → gardes `requireUuid()` (B) → uploads/deserializer → `EventCoOrganizerService` (B orphelin + gardes) → `FeaturedService`/`ShareService`/`EventViewService` → `EventResource` (A) → DTO tags-null + entité → mop-up branches A → **documenter C** (134, bridge 26, 828, 222, 139, 52).
2. **user-service (8 NC + 23 br)** : uploads `UserResource` (B) → `UserService` branches (A 100/115/118 ; B 336/351) → `FollowService`/`CalendarService` (B) → `FollowResource`/internal-resources (A/B) → IcsBuilder (A) → **documenter C** (UsernameGenerator 182/183/202, UserService 311/220, FollowResource null-caller).
3. **engagement-service (4 NC + 33 br)** : `CommentService` branches A (admin/null-author) + B (parent null/caller null) → `AttendanceService` A → `CommentLikeService` B → entités (`Comment` [~], `CommentLike` [C]) → resources A + internal B → **documenter** (safeGetUser null ×2, CommentService 358 = OPTIONNEL).
4. **moderation-service (1 NC + 5 br)** : `ReportService` comment-only / null-reporter / `u==null` / null-name (tous A/B via harnais existants).
5. **notification-service (6 NC + 2 br)** : `NewCommentConsumer` ×2 catch (B hand-wiring) → `UserFollowedConsumer` (B) → `CommentMentionConsumer` (A).
6. **Intégration & CI** : pousser → CI matrix (5 services) + `sonar-aggregate` → **itérer sur le rapport Sonar de la PR** jusqu'au **maximum** (gate vert : new-code ≥ 80 %, duplication ≤ 3 %, ratings A) + **plafond C documenté** dans la description de PR. Si résiduel, parser les `ci="0"`/`mb>0` restants et fermer (A/B) ou re-classer C.

---

## 6. Plafond accepté (liste exhaustive — ZÉRO exclusion)

Conformément à **D2/D3**. **Aucun** de ces items n'est exclu ; chacun est soit **C pur** (irréductible sans refactor `src/main`, interdit), soit **B-réflexion/forge OPTIONNEL** (recette fournie, **non écrit par défaut**, ré-autorisable cible par cible par le PO).

### Tier 1 — 100 % impossible (C pur — ni test honnête, ni réflexion, ni forge)
| Item | Raison structurelle |
|---|---|
| `event … EventService:134` `if (!conditions.isEmpty())` — bras vide | Le bloc statut (L107-113) **ajoute toujours** une condition (`e.status = :status` **ou**, en `else`, `e.status NOT IN (:hiddenStatuses)`). `conditions` est une **variable locale** : aucune entrée ni aucun mock ne peut la rendre vide. Vérifié au source. |
| `event … EventLifecycleKafkaBridge:26` — branche `default` du `switch (ev.type())` | Switch **exhaustif** sur l'enum à 4 valeurs (PUBLISHED/CANCELLED/EXPIRED/UPDATED), sans `default` écrit. La branche manquée est le **default synthétisé par le compilateur** ; aucun `EventLifecycleEvent.Type` réel ne l'atteint. |

### Tier 2 — atteignable UNIQUEMENT artificiellement (OPTIONNEL — non écrit par défaut, D3)
> Réflexion sur méthode privée (mécanisme 7) **ou** forge d'état logiquement impossible (PanacheMock). Recette indiquée pour ré-autorisation ponctuelle.

| Item | Pourquoi artificiel | Recette si ré-autorisé |
|---|---|---|
| `engagement … AttendanceService:442/443` `safeGetUser(null)` | Privé ; tout caller passe un UUID non-null | Réflexion `getDeclaredMethod("safeGetUser", UUID.class)` → `null` (sous `@QuarkusTest`). |
| `engagement … CommentService:382/383` `safeGetUser(null)` | idem | idem. |
| `engagement … CommentService:358` `event==null` | Privé ; callers gardent `event!=null` (404) avant | Réflexion `(null, uuid)` → `false`. |
| `event … EventStatsService:52` `event==null` | Privé ; `getStats` `orElseThrow` avant | Réflexion `(null, uuid)`. |
| `event … FeaturedService:139` `countFavorites` set vide | Privé ; garde `!candidates.isEmpty()` (L67) en amont | Réflexion `bulk(Set.of())`. |
| `event … EventCoOrganizerService:222` `ids==null/empty` | Privé ; `getMyInvitations` retourne tôt si invitations vides | Réflexion `findByIdsAsDTO(Set.of())`. |
| `event … EventService:828` overload privé `(Event,String)` `@SuppressWarnings("unused")` | **Aucun** call-site (code legacy-compat mort) | Réflexion sur l'overload (+ `mock(CallerIdentity)`). |
| `user … UsernameGenerator:182/183` `preTranslateLatinExt(null/"")` + `:202` `pickFirstNonBlank(second==null)` | Privés ; `slugify` ne passe jamais null/vide (`"user"` worst-case ; `joinNonBlank` rend `""` pas `null`) | Réflexion sur les privés. |
| `user … UserService:311` (`callerId.equals(user.id)` quand caller≠owner) + `:220` (self-match absorbé par le no-op guard) | Exige une ligne `auth0Id≠caller` mais `id==caller.id` (**état impossible**) | Forge PanacheMock d'une ligne incohérente (dishonnête). |
| `user … FollowResource:136` `callerAuth0Id==null` | Endpoint `@Authenticated` → principal jamais null | Réflexion `projectListItem(user, null)`. |

### Estimation du % maximum (recomptée sur le JaCoCo possédé)
- **A + B seuls (Tier-2 ouvert)** : ~32/36 lignes + ~115/131 branches fermées → **≈ 99,5 %** blend possédé (≈ 99,87 % L / ≈ 98,8 % B). **Cible baseline.**
- **+ Tier-2 (réflexion/forge ré-autorisés)** : il ne reste que les **2 branches Tier-1** → **≈ 99,95 %** blend (100 % L / ≈ 99,85 % B).
- **100 %** : **impossible** sans refactor `src/main` (interdit) — les 2 items Tier-1.

L'overall **Sonar** (union avec les 10 libs shared à 100 % L) sera **supérieur** à ces blends possédés.

---

## 7. Checklist finale de validation
- [ ] Branche **`feature/backend-coverage-max`** depuis `main` ; **nouvelle** PR ; titre **`test(backend): …`**.
- [ ] **ZÉRO exclusion** : `git grep -nE 'coverage.exclusions|@Generated|<excludes>'` ne montre aucun **nouvel** ajout ; aucun `@io.quarkus…/lombok…Generated`.
- [ ] **0 fichier sous `*/src/main/`**, **0** ligne `openapi/`, **0** migration Flyway, **0** pom applicatif modifié (`git diff --stat origin/main` ne touche que `*/src/test/` — exception admise : conversion de `CommentTest` plain → `@QuarkusTest`, qui reste un fichier de test).
- [ ] Familles DRY factorisées (helpers hand-wiring/PanacheMock/multipart) — duplication nouveau code **≤ 3 %**.
- [ ] **event** : ternaries summary-null (B) + gardes requireUuid (B) + duplicate-cap (B) + orphelin co-org (B) + Featured/Share/View + EventResource (A) + DTO tags-null + entité + deserializer couverts.
- [ ] **user** : uploads (B) + UserService 100/115/118/336/351 + Follow/Calendar (B) + FollowResource/internal (A/B) + IcsBuilder (A) couverts.
- [ ] **engagement** : CommentService A+B + AttendanceService A + CommentLikeService B + entités (`Comment` converti, `CommentLike` créé) + resources couverts.
- [ ] **moderation** : ReportService comment-only/null-reporter/`u==null`/null-name (A/B).
- [ ] **notification** : NewCommentConsumer ×2 catch (B) + UserFollowedConsumer (B) + CommentMentionConsumer (A).
- [ ] **Tier-2 (artificiel)** : **non écrit** par défaut (D3) ; documenté dans la PR. Tier-1 (C pur) documenté.
- [ ] `./mvnw -o … test-compile` vert en local.
- [ ] **CI verte** : matrix (5 services) + `sonar-aggregate` ; **gate SonarCloud franchi** (new-code ≥ 80 %, duplication ≤ 3 %, ratings A).
- [ ] **Couverture overall Sonar MAXIMISÉE** (≥ 99,5 % blend possédé visé) ; **plafond C documenté** dans la description de PR (Tier-1 + Tier-2-non-faits).
- [ ] Aucun bug applicatif corrigé en douce ; tout bug rencontré est **signalé** dans la PR.
