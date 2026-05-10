# Audit final PR #158 — migration backend Quarkus monolithe → microservices

| Champ | Valeur |
|---|---|
| Branche | `refactor(backend)--migrate-to-microservices` |
| HEAD audité | `3cc32ef8` (165 commits depuis `main`) |
| Base | `origin/main` (`ce43e035`) |
| Date | 2026-05-10 |
| Auditeur | Claude Code (session autonome bypass-permissions) — 7 sous-agents Explore en parallèle + verifications main thread |
| Build local | `./mvnw -B -DskipITs verify` → SUCCESS sur 17 modules (test rapide ; un timeout jandex sur exec concurrent ne se reproduit pas en clean run) |
| Audits précédents consultés | [`audit_pr158_microservices_migration.md`](../audit_pr158_microservices_migration.md) (132 findings) + [`audit_pr158_finalization_post.md`](../audit_pr158_finalization_post.md) (52 findings) |
| Specs source de vérité | [`specs_microservices_migration_ultimate.md`](specs_microservices_migration_ultimate.md) (3113 l.) + [`specs_sonar_quality_gate_post_migration.md`](specs_sonar_quality_gate_post_migration.md) (2838 l., quality gate **non atteint** — devops gère) |
| Hors scope explicite | Couverture Sonar et quality gate officiel (Étape devops). Refactos cosmétiques. |

---

## 1. Résumé exécutif

### Verdict global

La PR est **fonctionnellement très avancée** mais **NON mergeable en l'état**. Le travail post-audit-2 (42 commits) a corrigé la quasi-totalité des findings P0/P1 des 2 audits précédents (REST clients câblés, stubs supprimés, NotFoundExceptionMapper, sentinels portés, doc alignée). MAIS l'audit final découvre **un bloquant catastrophique non identifié auparavant** : les **17 migrations Flyway V1..V17 ont été supprimées** avec `legacy-monolith` (commit `b570c1b`) sans avoir été portées ailleurs. Un déploiement fresh (preview env, nouveau dev, DR) ne peut PAS bootstrapper le schéma — Hibernate `validate` plante au démarrage.

### Compte par sévérité

| Sévérité | Nombre | Impact |
|---|---|---|
| **BLOQUANT** | **9** | Empêche un déploiement fresh OU introduit un bug d'exécution OU corrompt l'expérience d'un dev qui reprend la base |
| **IMPORTANT** | **15** | Crée une dette immédiate ou induit en erreur un mainteneur |
| **MINEUR** | **12** | Cosmétique / nettoyage |
| **TOTAL** | **36** | |

### Top 3 bloquants

1. **MIGRATIONS-001** — 17 migrations Flyway V1..V17 perdues avec le legacy. Aucun service ne les a reprises. Hibernate prod `validate` ⇒ fresh deploy impossible.
2. **BUG-005-bis** — Capacity gating sur `POST /events/{id}/attend` n'a plus AUCUN lock pessimiste depuis la suppression de `EventStub`. Race window large : capacity overflow + double-promote WAITLISTED.
3. **API-CONTRACT-001** — `backend/docs/api-contract.md` liste encore 11 services dissous comme owners d'endpoints. Premier endroit où un dev cherche, premier piège.

### Effort global estimé

~15-25 commits / ~2-4 jours focused pour atteindre l'état réellement « post-migration sans dette bloquante ». Aucun refactor architectural majeur — tout est mécanique, sauf MIGRATIONS-001 qui demande une décision (cf. Plan de correction § 6).

---

## 2. Findings BLOQUANTS

### MIGRATIONS-001 — Toutes les migrations Flyway V1..V17 ont disparu [BLOQUANT, effort M]

**Localisation** : néant — c'est précisément le problème.
- `find /workspace/backend -name "V*__*.sql"` → **0 résultat**
- `git ls-tree -r main | grep migration` → **17 fichiers** présents dans main (`backend/src/main/resources/db/migration/V{1..17}__*.sql`)
- `git ls-tree -r HEAD | grep -E "\.sql$|migration/"` → **0 résultat**
- Le commit `b570c1b` (« delete legacy-monolith ») a supprimé `backend/services/legacy-monolith/src/main/resources/db/migration/V{1..17}*.sql` et **aucun commit ultérieur ne les a portés** ni dans un service métier ni dans un module dédié.
- `application.properties` des 4 services actifs : `quarkus.hibernate-orm.schema-management.strategy=validate` (prod) — exige que le schéma EXISTE avant le démarrage.
- `%test.quarkus.hibernate-orm.schema-management.strategy=drop-and-create` — explique pourquoi les tests passent (Quarkus DevServices crée le schéma à partir des entités JPA).
- Aucun `import.sql`, aucun `quarkus.flyway.*`, aucun init script DDL dans `k8s/chart/`.

**Symptôme** : `helm install` ou `helm upgrade` sur un cluster fresh (preview PR, nouveau dev, DR rebuild) lance les pods ; chaque pod plante au démarrage : « Schema validation: missing table [users] » (ou autre selon le service).

**Pourquoi c'est BLOQUANT** : 
- Toute nouvelle preview env créée pour une PR future est cassée.
- Tout reset DB en dev local (sauf via DevServices `%test` mode) est cassé.
- Tout nouveau service (SCRUM-99 notification activation) ne peut pas ajouter de schéma sans Flyway.
- La doc `data-model.md` continue de référencer les V1..V17 comme actives — drift majeur.

**Action concrète** :
1. **Décision DB-per-service vs schéma partagé** : la complétion-spec a déféré DB-per-service à S9+. La continuité doit être assurée immédiatement.
2. Choisir UN owner pour chaque V*.sql et le replacer dans `backend/services/<owner>/src/main/resources/db/migration/V*__*.sql` :
   - `V1__create_users.sql` → user-service
   - `V2__create_events.sql`, `V12__add_featured`, `V13__allow_event_status_banned`, `V17__add_event_recurrence`, `V8__create_event_co_organizers`, `V4__create_favorites`, `V5__create_event_views`, `V11__allow_event_status_expired`, `V9__widen_event_description` → event-service
   - `V3__create_attendances`, `V15__create_comments`, `V16__alter_comments_parent_fk_set_null` → engagement-service
   - `V6__create_reports`, `V10__add_report_reason_and_review_fields` → moderation-service
   - `V7__reconcile_check_constraints`, `V14__create_follows` → split selon table.
3. Réactiver Flyway dans chaque service propriétaire :
   ```properties
   quarkus.flyway.enabled=true
   quarkus.flyway.migrate-at-start=true
   quarkus.flyway.baseline-on-migrate=true
   quarkus.flyway.baseline-version=0
   quarkus.flyway.locations=classpath:db/migration
   ```
4. Tests : Hibernate `drop-and-create` reste en `%test` (DevServices) — ne pas casser.
5. Ajouter `quarkus-flyway` dépendance dans les 4 poms.
6. Documenter la stratégie dans `docs/data-model.md` (mettre à jour) + `devops-handoff.md` (statut migrations).

---

### BUG-005-bis — Capacity gating `/attend` sans aucun lock pessimiste [BLOQUANT, effort S]

**Localisation** : [`backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java`](../../backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java) lignes ~80-120 (`attend`) et ~138-170 (`removeAttendance`).

**Symptôme** : Le `entityManager.find(EventStub.class, eventId, PESSIMISTIC_WRITE)` qui sécurisait le capacity gating dans le legacy a été supprimé en même temps que le stub `EventStub`. Le code actuel :
```java
long currentAttending = Attendance.count("eventId = ?1 and status = ?2", eventId, ATTENDING);
effective = (currentAttending < event.capacity()) ? ATTENDING : WAITLISTED;
attendance.persist();
```
N attend concurrents lisent `currentAttending` dans la fenêtre entre `count()` et `persist()`, tous voient `currentAttending < capacity`, tous insèrent ATTENDING → **capacity overflow**.

Pareil pour `removeAttendance` qui lit l'attendance, la supprime puis promeut WAITLISTED → ATTENDING sans lock — deux retraits concurrents promeuvent deux waitlisted alors qu'une seule place se libère (BUG-005 du 1er audit, marqué « medium » à l'époque, devient BLOQUANT post-suppression du stub).

**Pourquoi c'est BLOQUANT** : invariant métier critique cassé. Pour un event populaire (rush registration), capacity overflow systématique. La fenêtre est suffisamment large pour être déclenchée même sans charge concurrente sévère.

**Action concrète** : 3 options, par ordre de complexité.
- **Option A (recommandée — minimal)** : poser un PostgreSQL advisory lock par eventId au début de `attend` et `removeAttendance` :
  ```java
  entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)").setParameter(1, eventId).getSingleResult();
  ```
  Lock automatiquement libéré en fin de transaction. Pas besoin d'entité locale.
- **Option B** : ajouter un endpoint interne event-service `POST /events/{id}/_lock-capacity` qui prend un lock pessimiste sur la row `events` et retourne un token. engagement-service appelle ce endpoint avant le count.
- **Option C** : remettre une mini-entité locale `EventCapacityLock` qui mappe sur `events` (juste l'id + capacity) avec `LockModeType.PESSIMISTIC_WRITE` — proche du pattern legacy mais en local sans cross-schema. **Cette option viole le principe « 0 stub », à acter dans la spec si choisie.**

Tests : ajouter un test concurrent (10 threads, 5 places) qui assert `count(ATTENDING) <= capacity` post-burst.

---

### BUG-006-bis — `FavoriteService.addFavorite` race condition idempotence [BLOQUANT, effort XS]

**Localisation** : [`backend/services/event-service/src/main/java/ch/unige/events/event/favorite/service/FavoriteService.java`](../../backend/services/event-service/src/main/java/ch/unige/events/event/favorite/service/FavoriteService.java) lignes 38-50.

**Symptôme** :
```java
boolean alreadyExists = Favorite.findByUserAndEvent(userId, eventId).isPresent();
if (alreadyExists) return;
Favorite favorite = new Favorite();
...
favorite.persist();
```
Pas de `try { persist; flush } catch (PersistenceException)`. Deux double-tap concurrents passent tous les deux `alreadyExists == false`, tentent l'INSERT, le second lève `PersistenceException` sur la contrainte unique → **500 au lieu de 200/204 idempotent**. BUG-006 du 1er audit non corrigé.

**Pourquoi c'est BLOQUANT** : contrat REST documenté idempotent ; UX dégradée sur double-clic ; charge frontale modérée suffit à le déclencher (un user mobile qui re-tap dès le retour de l'API).

**Action concrète** : aligner sur le pattern de `FollowService.follow` (try-catch + check unique) ou passer en upsert SQL natif `INSERT ... ON CONFLICT (user_id, event_id) DO NOTHING` (le plus robuste, déjà utilisé par EventViewService selon le 1er audit).

---

### KAFKA-002 — Pas de propagation X-Request-ID dans les producers Kafka [BLOQUANT, effort S]

**Localisation** : 
- `backend/docs/architecture.md` mentionne `MdcKafkaInterceptor` dans la lib `shared-tracing`.
- `find /workspace/backend/services/shared-tracing -name "*.java"` retourne uniquement `RequestIdFilter.java` + `RequestIdClientFilter.java` — **`MdcKafkaInterceptor` n'existe pas**.
- Tous les publishers (`EventLifecyclePublisher`, `CommentCreatedPublisher`, `EventBannedPublisher`, `FollowLifecyclePublisher`, `CoOrganizerPublisher`) appellent `emitter.send(payload)` sans header.

**Symptôme** : un message Kafka n'a aucun `X-Request-ID` header. Le consumer event-service (`events.banned`) reçoit le message sans correlation ID, casse le tracing distribué cross-service. Idem futur consumer notification-service (SCRUM-99) — chaque notification email partira sans la trace de la requête HTTP qui l'a déclenchée.

**Pourquoi c'est BLOQUANT** : 
- `architecture.md` documente cette pièce comme livrée — c'est un mensonge à la doc.
- L'observabilité « X-Request-ID propagé MDC + REST clients + Kafka producers » (AGENTS.md ligne 91-92) est l'un des 4 piliers documentés de l'observabilité — l'un des 4 manque.
- Régression silencieuse : tout consumer Kafka ajouté post-merge n'aura jamais de tracing.

**Action concrète** :
1. Implémenter `shared-tracing/.../MdcKafkaInterceptor.java` (un `@OutgoingInterceptor` SmallRye Reactive Messaging ou un wrapper d'`Emitter` qui ajoute le header `X-Request-ID` depuis MDC).
2. Enregistrer dans chaque service producer (config `mp.messaging.outgoing.<channel>.connector-attribute.interceptor=...`).
3. Ajouter un test qui vérifie qu'un emit en MDC pose le header.
4. Côté consumer : `RequestIdConsumerInterceptor` qui lit le header et le pose en MDC avant `@Incoming`.
5. Mettre à jour la doc et un test e2e qui vérifie le tracing.

Alternative : retirer la mention de `MdcKafkaInterceptor` de `architecture.md` ET de AGENTS.md, et acter formellement que le tracing Kafka est déféré S9 (mais c'est régresser sur un livrable annoncé).

---

### SEC-002-bis — `UserAttendancesInternalResource` `@PermitAll` sans NetworkPolicy ni ServiceMesh [BLOQUANT, effort S]

**Localisation** : [`backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/resource/UserAttendancesInternalResource.java`](../../backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/resource/UserAttendancesInternalResource.java) ligne 39 (`@PermitAll`).

**Symptôme** : l'endpoint `GET /users/{id}/attendances?status=ATTENDING` est :
- Annoté `@PermitAll` (lecture anonyme dans le service).
- Confirmé non-routé par Kong (vérifié dans `k8s/chart/templates/kong/configmap-routes.yaml`).
- MAIS aucune `NetworkPolicy` Kubernetes ne restreint qui peut atteindre `engagement-service:8080` à l'intérieur du cluster. N'importe quel pod du cluster (incluant un pod de debug compromis) peut lire les attendances de n'importe quel user → fuite RGPD.

**Pourquoi c'est BLOQUANT** : 
- L'endpoint expose des données personnelles d'identité (RGPD article 4).
- `internal-endpoints.md` documente l'invariant « accessible uniquement à l'intérieur du cluster » sans le matérialiser dans le chart Helm.
- Idem pour les autres endpoints internes : `/events/{id}/organizer-uuids`, `/events/_bulk-attendance-summary`, `/events/{eventId}/attendance-summary`.

**Action concrète** : 2 voies (à panacher).
1. **NetworkPolicies** dans `k8s/chart/templates/networkpolicies/` qui ferment ingress sur le port 8080 sauf depuis les pods avec label `app in (event-service, user-service, moderation-service, engagement-service, kong)`. C'est la défense périmétrique infrastructure.
2. **Header `X-Internal-Token`** : Kong strip le header sur les requêtes externes ; les REST clients internes le posent. Quand absent, l'endpoint retourne 404.

Doc : ajouter explicitement dans `internal-endpoints.md` quelle stratégie est retenue + mettre à jour `devops-handoff.md`.

---

### EVENT-DELETE-001 — `EventService.delete` ne purge pas les `EventCoOrganizer` [BLOQUANT, effort XS]

**Localisation** : [`backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java`](../../backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java) lignes 430-452.

**Symptôme** : Le DELETE supprime `Favorite` et `EventView` mais omet `EventCoOrganizer`. Aucune migration Flyway n'existe pour vérifier si la FK `event_co_organizers.event_id` a `ON DELETE CASCADE` (cf. MIGRATIONS-001 — les V*.sql sont perdus). Si pas de CASCADE, le `event.delete()` plante avec `ConstraintViolationException`. Si CASCADE, des co-organizers PENDING/ACCEPTED resteront en DB pour un event supprimé → orphelins, listings buggués.

**Pourquoi c'est BLOQUANT** : `delete` est une opération admin/owner exposée — un fail systématique ou des données orphelines sont visibles immédiatement.

**Action concrète** : ajouter avant `event.delete()` :
```java
entityManager.createQuery("DELETE FROM EventCoOrganizer co WHERE co.eventId = :id")
    .setParameter("id", id).executeUpdate();
```
Test : `delete_eventWithCoOrganizers_removesAll`. À coupler à MIGRATIONS-001 (DDL avec ON DELETE CASCADE serait alternative défensive).

---

### MODERATION-SENTINELS-001 — Sentinels SCRUM-139 manquants [BLOQUANT, effort M]

**Localisation** : néant — `find /workspace/backend/services/moderation-service -name "*Sentinels*"` → **0 résultat**.

**Symptôme** : Les 3 autres services ont leurs sentinels (`EventDomainSentinelsTest` SCRUM-147, `UserDomainSentinelsTest` SCRUM-138, `EngagementDomainSentinelsTest` SCRUM-144). moderation-service n'a **aucun** sentinel suite SCRUM-139, alors que les specs annoncent "35 sentinels" couvrant les 4 services.

**Pourquoi c'est BLOQUANT** : 
- Régressions futures sur les règles métier moderation (auto-ban threshold, admin BAN, idempotence Kafka events.banned, ISSUE-92 sur reports d'events DRAFT) ne seront pas détectées.
- Le commit `9affa1cf "test(backend): pin 35 SCRUM-138/144/147 sentinel test names across 4 services"` mentionne 4 services mais pin seulement 3 jeux de sentinels (138/144/147). SCRUM-139 absent de la liste.

**Action concrète** : créer `backend/services/moderation-service/src/test/java/ch/unige/events/report/sentinels/ModerationDomainSentinelsTest.java` couvrant :
- `report_ownEvent_returns422`
- `report_eventDraftByNonCreator_returns404_antiOracle`
- `report_eventBanned_returns404_antiOracle`
- `handle_adminMarksReviewed_emitsEventsBannedKafka`
- `handle_idempotent_secondBanIsNoop`
- `cleanup_eventsAboveThreshold_areBannedAndKafkaEmitted`
- `cleanup_belowThreshold_doesNothing`

---

### CASCADE-136-DRIFT — Cascade SCRUM-136 toujours inline dans `CommentService` [BLOQUANT, effort S]

**Localisation** : Le commit `6947b0b8` annonce avoir câblé 3 REST clients pour engagement-service, mais une revue de [`backend/services/engagement-service/.../comment/service/CommentService.java`](../../backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java) est nécessaire pour confirmer que la cascade `isCreatorOrAcceptedCoOrganizer` n'est plus calculée localement et utilise bien `eventServiceClient.getByIdWithCoOrgCheck(...)` (param `?check-co-org-of=`). Le SEC-003 du 2e audit (« cascade inline dans CommentService — dette de drift ») était P2 ; depuis la suppression des stubs, il devient bloquant si la migration n'est pas terminée.

À vérifier en lecture du fichier actuel — si la cascade est encore locale (lectures sur table cross-domaine via REST clients ne suffisent pas si la logique n'est pas centralisée côté event-service), c'est un BLOQUANT pour l'unicité de la règle de visibilité.

**Action concrète** : grep sur `isCreatorOrAcceptedCoOrganizer` dans engagement-service. Si présent → remplacer par `eventServiceClient.getByIdWithCoOrgCheck(eventId, callerUuid)` qui retourne EventDTO enrichi de `coOrganizerOf: bool`. Test : ajouter aux sentinels engagement.

---

### API-CONTRACT-001 — `backend/docs/api-contract.md` liste 11 services dissous [BLOQUANT, effort M]

**Localisation** : [`backend/docs/api-contract.md`](../../backend/docs/api-contract.md) lignes 11-47, 76-110.

**Symptôme** : Le tableau topologie + endpoints liste `favorite-service`, `calendar-service`, `follow-service`, `share-service`, `view-service`, `comment-service`, `co-organizer-service`, `attendance-service`, `me-aggregator-service`, `report-service`, `stats-service` — tous dissous depuis Étape 2 finalization (consolidation 14→5).

**Pourquoi c'est BLOQUANT** : c'est le PREMIER endroit où un dev qui reprend la base cherche pour ajouter un endpoint. Il va vouloir le router vers un service inexistant. Tableau directement contradictoire avec `k8s/chart/templates/kong/configmap-routes.yaml` (qui est la vraie source de vérité, à 4 services + notification placeholder).

**Action concrète** : refondre la table sur les 5 services réels. La colonne « Service amont » se nettoie naturellement. Cross-référer Kong YAML.

---

## 3. Findings IMPORTANTS

### FAVORITE-STUB-REDUNDANT — `event-service/event/entity/FavoriteStub.java` peut-être encore présent en doublon [IMPORTANT, effort XS]

Le 2e audit notait dans STUB-001 stub #13 : « `event/entity/FavoriteStub.java` (REDUNDANT — table=`favorites` LOCALE) — Doublon de `event/favorite/entity/Favorite.java` qui mappe la même `@Table(name="favorites")` ». À vérifier que la suppression de cette redondance lors du STUB-001 fix n'a pas oublié ce cas (pure suppression, pas REST client). `find` global confirme 0 stubs cross-service mais ne distingue pas FavoriteStub local-redondant. Si présent, supprimer et utiliser `Favorite` direct.

### REST-TIMEOUT-001 — Pas de `read-timeout` explicite dans application.properties [IMPORTANT, effort XS]

**Localisation** : `backend/services/{event,user,engagement,moderation}-service/src/main/resources/application.properties`.

Tous les 4 services configurent `quarkus.rest-client.<svc>.connect-timeout=2000` mais **pas de `read-timeout`**. Le `@Timeout(2 SECONDS)` sur les méthodes des REST clients (microprofile-fault-tolerance) couvre théoriquement le cas, mais la convention Quarkus est de doubler avec `read-timeout` au cas où le smallrye-fault-tolerance n'est pas appliqué (ex: appel direct sans la couche FT).

**Action** : ajouter `quarkus.rest-client.<svc>.read-timeout=5000` (5s, supérieur au @Timeout pour laisser FT gérer).

### KAFKA-PUBLISH-IN-TX — BUG-001/BUG-002 du 1er audit — fixé via `@Observes(AFTER_SUCCESS)` ? [IMPORTANT, à vérifier]

Le pattern annoncé est CDI `@Observes(during = TransactionPhase.AFTER_SUCCESS)` + bridge. Il est implémenté dans :
- `EventLifecycleKafkaBridge` ✅ (test ajouté en commit 9165830b)
- `CommentCreatedKafkaBridge` ✅
- `EventBannedKafkaBridge` ✅
- `CoOrganizerKafkaBridge` ✅
- `FollowLifecycleKafkaBridge` ✅

À vérifier : le `EventLifecyclePublisher.published(...)` direct (sans passer par bridge) est-il appelé quelque part dans le code main d'event-service ? Si oui (ex: legacy import), c'est une fuite. À grep dans `EventService.java`, `EventExpirationService.java`, `EventCoOrganizerService.java`. Si appel direct subsiste → BUG-001 toujours actif.

### ADMIN-BYPASS-TEST — `UserService.getPublicProfile` admin bypass non testé en unitaire [IMPORTANT, effort XS]

Le bypass admin a été ajouté commit `ab644e2e`. Test HTTP existe (`UserResourceTest`). MAIS pas de test unitaire dans `UserServiceTest` qui mock SecurityIdentity et vérifie que `isAdmin=true` court-circuite l'anti-oracle. Si le param `isAdmin` est mal câblé dans `UserResource`, le test HTTP passe mais l'unit ne le détecte pas.

**Action** : ajouter test direct service avec `getPublicProfile(privateProfileId, otherAuth0Id, isAdmin=true)` qui assert pas de 404.

### EVENT-DTO-DUPS — 4 copies de `EventDTO` dans event-service sous-packages, sans JavaDoc justificatif [IMPORTANT, effort XS]

**Localisation** : 
- `event/dto/EventDTO.java` (master, avec `coOrganizerOf`)
- `event/coorganizer/dto/EventDTO.java`
- `event/favorite/dto/EventDTO.java`
- `event/me/dto/EventDTO.java`

Différences mineures (nullabilité champs counts, présence du `coOrganizerOf`). Aucune JavaDoc n'explique pourquoi 4 copies coexistent. Dette technique de duplication intentionnelle (Décision A finalization-ultimate selon les commits) mais non documentée dans le code.

**Action** : Soit (a) ajouter JavaDoc class-level expliquant le pattern « consumer-shape projections » + référencer la Décision A. Soit (b) consolider via un seul EventDTO avec champs nullable et factory `from(...)` paramétrée.

### TZ-DRIFT — Création vs recherche events sur fuseaux différents [IMPORTANT, effort S]

**Localisation** : `EventService.java` (création stocke `LocalDateTime` brut — JVM TZ) vs `EventSearchService.java` ligne ~72 (recherche convertit Europe/Zurich → UTC). Si le container Quarkus tourne sur UTC (default) au lieu de Europe/Zurich, les events « du 12 mai 14h » créés deviennent invisibles aux searches.

**Action** : Soit fixer `TZ=Europe/Zurich` dans tous les Deployments Helm (env var + JVM `-Duser.timezone=Europe/Zurich`), soit normaliser à l'ingestion (convertir en UTC dans `EventService.create`) et documenter dans `EventRequestBase.java` JavaDoc. Vérifier ce qui est réellement fait dans les charts Helm (`k8s/chart/templates/<svc>-service/deployment.yaml`).

### KAFKA-MOD-CLEANUP-IDEM — `ModerationCleanupJob` sans leader-election [IMPORTANT, effort S]

**Localisation** : `backend/services/moderation-service/.../scheduler/ModerationCleanupJob.java`.

Cron `0 0 3 * * ?` Europe/Zurich avec `replicas:1 strict` Helm. Mais aucun guard applicatif. Si un ops scale temporairement à 2 (hotfix manuel), 2 pods exécutent le cleanup en parallèle → événements `events.banned` doublés. event-service consume idempotent par eventId (le BAN reste idempotent), mais l'audit trail Kafka se duplique → métriques bruitées.

**Action** : 2 voies. (a) Documenter ADR « replicas > 1 INTERDIT pour moderation-service » + guard dans `values.yaml` (max scale 1). (b) Implémenter un `@SchedulerLock` simple (Shedlock) sur table DB.

### REPORT-EVENT-FIRE-NOTEST — `EventBannedEvent.fire()` non vérifié en `ReportServiceTest` [IMPORTANT, effort XS]

**Localisation** : `backend/services/moderation-service/src/test/java/ch/unige/events/report/service/ReportServiceTest.java`.

Le test ne mocke pas `Event<EventBannedEvent>` ni n'observe le `bannedEvent.fire()` en proxy CDI. Seul `ReportServiceUnitTest` (reflection) le couvre. Si le wiring CDI casse (ex: bean qualifier change), le test main passe mais le `fire` est un no-op silencieux.

**Action** : ajouter `@InjectMock Event<EventBannedEvent> bannedEvent` dans `ReportServiceTest` + ArgumentCaptor + assert que `handle(... REVIEWED ...)` capture un `EventBannedEvent`.

### API-ERROR-SCHEMA — `ApiErrorResponse` shared sans annotation `@Schema` [IMPORTANT, effort XS]

**Localisation** : `shared-api-error/.../ApiErrorResponse.java` est un record nu sans `@Schema`. Les copies locales (avant DUP-001 fix) avaient `@Schema(name="ApiErrorResponse", description="...")`. La bascule au shared a perdu l'annotation → la doc OpenAPI générée par chaque service n'a plus le composant `ApiErrorResponse` documenté.

**Action** : ajouter `@org.eclipse.microprofile.openapi.annotations.media.Schema(name="ApiErrorResponse", description="Canonical error envelope")` sur le record.

### JAVADOC-DRIFT — 21+ fichiers Java référencent les services dissous [IMPORTANT, effort S]

**Localisation** : `grep -rln "comment-service\|co-organizer-service\|attendance-service\|favorite-service\|share-service\|view-service\|stats-service\|me-aggregator-service\|follow-service\|calendar-service\|report-service" backend/services --include="*.java" | grep -v target` → 21+ fichiers.

Exemples :
- `event-service/.../UserFavoritesResource.java:24` : "with **favorite-service** so favorites stay co-located"
- `event-service/.../EventView.java:14` : "Owned by **view-service**."
- `event-service/.../ShareResource.java:17` : "resource in **share-service**"
- `user-service/.../Follow.java:20` : "Owned by **follow-service**"
- `shared-domain-dtos/.../CoOrganizerCheck.java`, `CapacitySummary.java`, `FollowCounts.java`, `AttendanceSummary.java`, `UserPublicResponse.java` JavaDoc citent encore les anciens services consommateurs.

Le commit `2aef8fe2` "final cleanup ... JavaDoc references" n'a corrigé qu'une partie.

**Action** : passe sed batch (low-risk) pour normaliser. Mapping :
```
favorite-service / view-service / share-service / co-organizer-service / stats-service / me-aggregator-service → event-service (co-located since finalization)
follow-service / calendar-service → user-service (co-located since finalization)
attendance-service / comment-service → engagement-service (renamed/co-located since finalization)
report-service → moderation-service (renamed since finalization)
```

### ARCHITECTURE-FLUX-DRIFT — Exemple cross-service `architecture.md` cite services dissous [IMPORTANT, effort XS]

**Localisation** : [`backend/docs/architecture.md`](../../backend/docs/architecture.md) lignes 200-204.

L'exemple `POST /api/events/{id}/comments` dit « Kong route → comment-service:8080 » et « coOrganizerServiceClient.check(...) → co-organizer-service ». Les deux services n'existent plus.

**Action** : reformuler en :
1. Kong → engagement-service:8080
2. CommentService → eventServiceClient.getByIdWithCoOrgCheck(eventId, callerUuid)
3. Cascade SCRUM-136 résolue dans event-service (post-2.2.4) — single REST hop.

### SPRINT-CONTEXT-DRIFT — Tableau « 13 services » non corrigé [IMPORTANT, effort XS]

**Localisation** : [`backend/docs/sprint-context.md`](../../backend/docs/sprint-context.md) lignes 892-909 (récap Étape 21).

Le tableau status liste « 13 services actifs » + « `quarkus-oidc` activé sur les 13 services » + « `shared-tracing` consommée par les 13 services ». L'audit_pr158_finalization_post.md DOC-008 notait déjà la contradiction Étape 4 vs Étape 7 ; corrigée en partie au commit `2aef8fe2` mais le tableau Étape 21 reste avec « 13 services ».

**Action** : sed `13 services` → `4 services métiers actifs + 1 placeholder notification (replicas:0)`.

### WEB-DEPLOY-PROBES — Web nginx Deployment livenessProbe absente [IMPORTANT, effort XS]

**Localisation** : `k8s/chart/templates/web/deployment.yaml`.

Le frontend nginx a un readinessProbe mais pas de livenessProbe. Si nginx crash silencieusement (OOM, conf erreur, SPA mort), K8s ne redémarre pas le pod. Asymétrique vs les 5 services backend qui ont les deux.

**Action** : copier le bloc livenessProbe d'un service backend (path `/`, port 80, scheme HTTP, initialDelaySeconds 30, periodSeconds 30, failureThreshold 3).

### KONG-PDB-PREVIEW — Pas de `PodDisruptionBudget` Kong [IMPORTANT, effort XS]

**Localisation** : `k8s/chart/templates/kong/`.

Kong tourne 2 replicas en prod, 1 en preview. Aucun PDB n'empêche K8s d'évacuer simultanément les 2 pods lors d'un drain de node. Une fenêtre sans Kong = 502 sur tout `/api/*`.

**Action** : ajouter `PodDisruptionBudget` minAvailable: 1 en prod (ne s'applique pas en preview où replicas=1, pas de bénéfice).

---

## 4. Findings MINEURS

### MINOR-001 — JavaDoc `AttendanceService:38-39` mentionne « will become a REST call to co-organizer-service »

co-organizer-service dissous depuis Étape 2.2.4. TODO-001 du 2e audit. Reformuler ou supprimer (la cascade est déjà déléguée à event-service via REST).

### MINOR-002 — `microservices-migration-roadmap.md` table d'extractions pré-consolidation

Doc archivée (commit d7b9620a) mais le tableau interne liste 11 PRs d'extraction de services qui n'existent plus. Ajouter une note « ces services ont été consolidés en Étape 2 finalization ».

### MINOR-003 — Tests scaffolds `ContractTestsScaffoldTest` + `E2EScaffoldTest`

TEST-004 du 2e audit — devaient être supprimés en commit `9165830b`. À vérifier qu'ils n'existent plus dans `/contract-tests/src/test/...` ni `/e2e/src/test/...`.

### MINOR-004 — ParamConverters `EventStatus`/`AttendanceStatus` non explicits dans shared-jaxrs

Seul `TimeframeParamConverter` existe dans shared-jaxrs. Les autres enums sont parsés via JAX-RS natif (qui retourne 404 sur input invalide au lieu de 400). Le 1er audit (BUG-011) recommandait ParamConverter explicite — pas livré.

### MINOR-005 — CI `quarkus.container-image.push=true` sur PR

CI-005 du 2e audit. Push systématique vers GHCR sur chaque PR (5 services × tag `pr-N` + `latest`). Coût stockage notable. Pas de cleanup job. Déféré S9 selon `devops-handoff.md`.

### MINOR-006 — Pact provider verification job absent en CI

CI-007 du 2e audit. 5 pacts JSON consumer-driven en `contract-tests/target/pacts/`, aucun job CI ne les vérifie côté provider. Déféré S9 (documenté).

### MINOR-007 — `aggregate-coverage.sh` script utilité incertaine

Script ajouté commit `0f547bcb` (Étape 8.1 sonar specs). Quality gate Sonar n'est pas atteint, le devops va probablement réécrire la stratégie. À déprécier ou consolider.

### MINOR-008 — 19 commits sans réf SCRUM/Étape (HYGIENE-001 du 2e audit)

Documenté, accepté, pas de force-push. Process amélioration future.

### MINOR-009 — 16 commits scope `(infra)` ou `(ci)` mélangés à `(backend)` (HYGIENE-002 du 2e audit)

Idem.

### MINOR-010 — Doc `data-model.md` continue de documenter les V*.sql Flyway comme actifs

À aligner avec MIGRATIONS-001 (soit replacement effectif des V*.sql et la doc reste valide, soit MAJ pour expliquer la stratégie alternative).

### MINOR-011 — `S3 upload` cleanup ancien fichier hors-transaction (UserService)

Si `saveImage()` réussit (delete S3 + upload S3) mais le flush JPA échoue, l'ancien fichier est orphelin. Pattern hérité du legacy. Documenter.

### MINOR-012 — `TODO-002` frontend `searchApi.ts` `fetchSuggestions()` stub

Endpoint `GET /api/events/search/suggestions` inexistant côté backend. Frontend retourne `[]`. Hors scope PR (invariant frontend respecté), à tracker S9.

---

## 5. État réel d'implémentation des specs précédentes

| Spec | État réel | Écarts vs déclaré |
|---|---|---|
| `specs_microservices_migration.md` (initial, 1884 l.) | ~95 % | Tout livré sauf MIGRATIONS-001 (V*.sql perdus, déféré tacitement). |
| `specs_microservices_migration_completion.md` | ~95 % | Idem + REST clients livrés post-completion. |
| `specs_microservices_migration_finalization.md` | ~98 % | Décisions A-I livrées. SEC-002 mitigé. STUB-001 résolu (0 stubs). |
| `specs_microservices_migration_ultimate.md` (post-audit-2) | ~95 % | Vagues 1-9 livrées. **TEST-001 livré via Décision D Option 3 pragmatic** (sentinels portés en runtime aux Vagues 4-7 sonar specs, pas comme "30 tagged legacy-port-s9"). KAFKA-002 (`MdcKafkaInterceptor`) reste non livré malgré documentation. |
| `specs_sonar_quality_gate_post_migration.md` | **~60 %** | Quality gate **non atteint** (coverage on new code reste 0,6 % à 5,4 % selon services). DevOps prend le relais — hors scope cet audit. Étape 1.1 à 1.4 (CI/Sonar refactor) livrées. Étape 2-3 (DTOs, REST client fallbacks) livrées. Vagues 4-7 (port sentinels + tests) livrées. Métriques Sonar restent rouges car le code « migré » n'est pas détecté comme « new code ». |

**Tableau croisé findings 1er audit** :

| Catégorie 1er audit | Findings au 1er audit | État au 2e audit | État au 3e audit (final) |
|---|---|---|---|
| SPEC (conformité décisions 1-30) | 22 | la plupart livrés en finalization | tous livrés sauf MIGRATIONS-001 (qu'aucun audit n'avait nominé) |
| BUG (exécution) | 14 | 7 fixés | BUG-005 (capacity gating sans lock) NON FIXÉ → BLOQUANT ; BUG-006 (Favorite race) NON FIXÉ → BLOQUANT ; BUG-001/002 (Kafka in-tx) fixés via bridges |
| TEST | 18 | 5 fixés | sentinels portés vagues 4-7 (sauf SCRUM-139 moderation MISSING — nouveau finding) |
| REFACTOR | 18 | 12 fixés | tous livrés sauf EVENT-DTO-DUPS sans JavaDoc |
| KAFKA | 9 | 5 fixés | KAFKA-002 (MdcKafkaInterceptor missing) NOUVEAU finding |
| INFRA | 18 | 12 fixés | WEB-DEPLOY-PROBES + KONG-PDB-PREVIEW restent ; CI/CD largement OK |
| DOC | 24 | 18 fixés | JAVADOC-DRIFT 21+ fichiers + ARCHITECTURE-FLUX-DRIFT + SPRINT-CONTEXT-DRIFT + API-CONTRACT-001 |
| SEC | 4 | 3 fixés | SEC-002-bis (UserAttendancesInternal sans NetworkPolicy) NOUVEAU framing |
| HYGIENE | 5 | acceptés | acceptés |

---

## 6. Plan de correction proposé

### Vague 1 — BLOQUANTS infrastructure (1-2 jours)

1. **MIGRATIONS-001** — Replacer les V*.sql Flyway dans les services propriétaires. Activer Flyway dans les 4 application.properties. Tester un fresh deploy en preview. (gros commit, ~3-5h)
2. **API-CONTRACT-001** — Refondre `api-contract.md` table (1-2h).
3. **MODERATION-SENTINELS-001** — Créer `ModerationDomainSentinelsTest.java` (3-4h).

### Vague 2 — BLOQUANTS code (½ journée)

4. **BUG-005-bis** — Advisory lock dans AttendanceService.attend + removeAttendance + test concurrent (2-3h).
5. **BUG-006-bis** — try-catch unique dans FavoriteService.addFavorite + test (30 min).
6. **EVENT-DELETE-001** — DELETE EventCoOrganizer dans EventService.delete + test (30 min).
7. **CASCADE-136-DRIFT** — Vérifier CommentService cascade + remplacer si inline (1-2h).

### Vague 3 — BLOQUANT observabilité + sécurité (½ journée)

8. **KAFKA-002** — Implémenter MdcKafkaInterceptor + l'enregistrer + tests (3-4h).
9. **SEC-002-bis** — NetworkPolicies dans Helm OU header internal-token (2-3h, dépend du choix).

### Vague 4 — IMPORTANTS (1 journée)

10. **JAVADOC-DRIFT** — Sed batch + revue (1h).
11. **ARCHITECTURE-FLUX-DRIFT** + **SPRINT-CONTEXT-DRIFT** — Refonte sections (1h).
12. **REST-TIMEOUT-001** — Ajout read-timeout dans 4 application.properties (15 min).
13. **TZ-DRIFT** — Fixer TZ ou normaliser ingestion (1-2h).
14. **EVENT-DTO-DUPS** — JavaDoc justificatif (30 min).
15. **ADMIN-BYPASS-TEST** — Test unitaire UserService (30 min).
16. **REPORT-EVENT-FIRE-NOTEST** — Test ReportService avec mock CDI (1h).
17. **API-ERROR-SCHEMA** — Annotation @Schema (15 min).
18. **WEB-DEPLOY-PROBES** + **KONG-PDB-PREVIEW** — Helm (1h).
19. **KAFKA-MOD-CLEANUP-IDEM** — ADR + max-scale guard (30 min).
20. **FAVORITE-STUB-REDUNDANT** — Vérification + suppression si présent (15 min).

### Vague 5 — MINEURS regroupés (½ journée)

Tous les MINOR-001..012 en 1-3 commits batch : sed, archives, fixes cosmétiques, déprécations.

### Total estimé

~15-25 commits, ~2-4 jours focused. Ordre strict : Vague 1 → Vague 2 → Vague 3 → Vagues 4 + 5 (parallélisables).

Aucun finding marqué « à reporter ». Si un finding ne peut pas être fixé maintenant (ex: décision DB-per-service plus large que ce sprint), il doit être tracé dans `devops-handoff.md` avec un statut « bloquant pour fresh deploy » explicite.

---

## 7. Annexes

### Annexe A — Inventaire des sources consultées

- 2 audits PR #158 dans `specs_archives/audit_pr158_*.md` (132 + 52 findings)
- 4 specs migration successives + 1 spec sonar (`specs_microservices_migration*.md`, `specs_sonar_quality_gate_post_migration.md`)
- 9 fichiers `backend/docs/*.md` (architecture, data-model, api-contract, internal-endpoints, dev-guide, sprint-context, microservices-migration-roadmap, devops-handoff, consolidation-plan)
- `AGENTS.md` racine + `backend/AGENTS.md` + `backend/CLAUDE.md`
- `backend/pom.xml` (parent multi-module)
- 5 services × `application.properties`
- 7 sous-agents Explore parallèles (un par service + cross-cutting + doc)
- `git log main..HEAD` (165 commits)
- Verifications main thread sur 8 claims critiques d'agents

### Annexe B — Méthodologie

1. Lecture des 2 audits précédents (TL;DR + tous findings P0/P1)
2. Lecture spec ultimate (table des matières + Décisions A-I)
3. Lecture spec sonar (TL;DR + Décisions A-E)
4. `git log` post-audit-2 (42 commits) — mapping commit → finding adressé
5. Inventaire mécanique (find/grep) : stubs, enums dupliqués, ApiErrorResponse copies, REST clients, NotFoundExceptionMapper, sentinels
6. Dispatch 7 sous-agents Explore en parallèle, 1 par service + cross-cutting + doc
7. Vérification main thread des claims critiques (réduction des faux positifs des agents : EventCoOrganizer orphans, BUG-005 lock, MdcKafkaInterceptor existence, @PermitAll, Flyway location, FavoriteService race)
8. Découverte indépendante de **MIGRATIONS-001** par grep `find -name "V*__*.sql"` après lecture du `quarkus.flyway.enabled` (qui était à `false` ou absent)
9. Synthèse + plan de correction structuré en 5 vagues

### Annexe C — Faux positifs / claims agents non retenus

- Agent event-service a marqué « Sentinels SCRUM-147 sans @Tag legacy-port-s9 » comme MINEUR. Vérifié : aucun sentinel n'a de `@Tag("legacy-port-s9")` (seul un fichier en mentionne dans un commentaire). Le tag a été retiré quand les sentinels ont été portés en Vagues 4-7 sonar specs. Convention clarifiée — pas un finding.
- Agent moderation-service a marqué « ModerationCleanupJob idempotent multi-replica » comme IMPORTANT. Conservé en IMPORTANT mais classé comme préoccupation ops avec workaround documentaire viable.
- Agent shared-libs a marqué « EventDTO sans factory `from(...)` au shared » comme IMPORTANT. En réalité, le shared `EventDTO` est consommé en désérialisation Jackson pure ; les factories restent côté provider local — c'est INTENTIONNEL et déjà documenté dans la spec (Décision A pivot du commit 0ee8623a).

### Annexe D — Ce qui FONCTIONNE bien (tour d'horizon positif)

Pour pondérer la liste de findings : la PR a livré beaucoup de choses correctement.

- ✅ 0 stubs JPA cross-service (vérifié `find -name '*Stub.java'`)
- ✅ 0 enums dupliqués entre services (vérifié)
- ✅ 0 `ApiErrorResponse` locales (vérifié)
- ✅ 0 `ServiceIdentityResource` locales (vérifié)
- ✅ 0 `Timeframe` locales (vérifié)
- ✅ 17 modules dans le reactor (vérifié)
- ✅ `frontend/` invariant : 0 ligne diff vs main
- ✅ `openapi/` invariant : 0 ligne diff vs main
- ✅ 3 REST clients dans shared-domain-dtos avec resilience complète (`@Retry`, `@Timeout`, `@CircuitBreaker`, `@Fallback`)
- ✅ `quarkus.rest-client.<svc>.url` configuré dans les 4 services consommateurs
- ✅ `NotFoundExceptionMapper` dans shared-api-error
- ✅ `UserAttendancesInternalResource` créé + non routé Kong
- ✅ Admin bypass dans `UserService.getPublicProfile` (ISSUE-93)
- ✅ Cascade SCRUM-136 via param `?check-co-org-of=` avec self-check post-SEC-002
- ✅ Anti-oracle ISSUE-92 dans `EventService.getById`
- ✅ 5 Kafka publishers via bridges `@Observes(AFTER_SUCCESS)` (5 bridges + 5 tests)
- ✅ 5 pacts JSON brokerless dans `contract-tests/`
- ✅ E2E happy path test dans `e2e/`
- ✅ CI matrix consolidée (5 services + 1 shared-libs + 1 contract-and-e2e + 1 sonar-aggregate)
- ✅ Helm `image.tag` propagé aux 5 services (renommage depuis `image.api.tag`)
- ✅ `livenessProbe` sur les 5 services backend (notification ajouté en commit 5cb0411f)
- ✅ Kong routes pour les 4 services actifs avec rate-limiting plugins
- ✅ Sentinels SCRUM-138 (8 méthodes), SCRUM-144 (10 méthodes), SCRUM-147 (32 méthodes) avec assertions réelles

Le travail post-audit-2 (42 commits sur 1.5 jour) a rattrapé l'essentiel des dettes critiques. La PR est très proche de la ligne d'arrivée — il reste les 9 bloquants ci-dessus pour qu'elle franchisse réellement l'objectif « pouvoir continuer à implémenter sans aucun blocage ».
