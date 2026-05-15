# Finalisation totale PR #158 — backend UNIGE Events — SPEC ULTIME (post-audit final)

| Champ | Valeur |
|---|---|
| Sprint | S8 (clôture absolue post-audit final) |
| Branche | `refactor(backend)--migrate-to-microservices` (persistante, **NE PAS créer de nouvelle branche**) |
| HEAD baseline | `3cc32ef8` (à confirmer par `git pull` au démarrage) |
| PR active | [#158](https://github.com/unige-pinfo6-2026/unige-events/pull/158) — **NE PAS merger**, Elie merge lui-même |
| Auteur spec | Claude (session 2026-05-10, post-audit final) |
| Exécuteur cible | Claude Code en **bypass-permissions**, autonome, branche persistante, sans merge |
| Frontend invariant | **0 ligne ABSOLU** — `git diff --shortstat origin/main HEAD -- frontend/` = 0 |
| OpenAPI invariant | **0 ligne ABSOLU** — `git diff --shortstat origin/main HEAD -- openapi/` = 0 (toute décision qui semble exiger un endpoint public doit être repensée pour rester interne, cf. internal-endpoints.md) |
| Frontière DevOps post-spec | uniquement items machine PINFO : (1) cluster Kafka prod-grade ; (2) certificats / DNS ; (3) Doppler secrets ; (4) SonarCloud quality gate (problème « new code » du code migré) ; (5) NetworkPolicies optionnelles en complément de SEC-002-bis (header X-Internal-Token est livré ici) ; (6) cleanup GHCR PR-tagged images ; (7) Pact provider verification job harness |
| Audit source de vérité | [`audit_pr158_migration_microservices_final.md`](audit_pr158_migration_microservices_final.md) — 35 findings (9 BLOQUANTS + 14 IMPORTANTS + 12 MINEURS) |
| Specs antérieures | [`specs_microservices_migration_ultimate.md`](specs_microservices_migration_ultimate.md), [`specs_sonar_quality_gate_post_migration.md`](specs_sonar_quality_gate_post_migration.md) |

---

## Note d'implémentation

Cette spec est l'**unique source de vérité** pour la **clôture définitive et totale** de la PR #158. Elle adresse **tous les 35 findings** de l'audit final sans aucun report. Après son exécution complète, la PR backend est prête au merge avec **zéro dette résiduelle backend** ; il ne reste plus que les 7 items DevOps machine PINFO listés ci-dessus.

**Après l'exécution complète de cette spec** :

1. Un déploiement fresh (preview, dev local, DR) bootstrappe le schéma DB sans intervention manuelle (Flyway redistribué par service).
2. Aucun race condition métier (capacity gating, favorite idempotence, attendance auto-promotion).
3. Tracing distribué cohérent HTTP **et** Kafka (X-Request-ID propagé partout).
4. Sécurité défensive sur les endpoints internes (header X-Internal-Token validé par filter shared-jaxrs).
5. Documentation alignée à 100 % sur la topology 5-services post-finalisation (zéro mention de service dissous dans .md ou JavaDoc).
6. Sentinels SCRUM-138/139/144/147 complets sur les 4 services métiers.
7. Build local SUCCESS sur 17 modules. Tous les checks CI verts sauf SonarCloud (devops PINFO).

**L'exécuteur autonome** :

- ne demande **jamais** une décision au user (toutes tranchées ici A-I) ;
- commit + push après chaque sous-étape numérotée verte (granularité ≈ 1 commit par sous-étape `N.M`, ≤ 500 lignes diff sauf justifié — exception : Étape 1.1 MIGRATIONS-001 peut être 1 commit unique gros car redistribution atomique de 17 fichiers SQL) ;
- pousse sur la branche persistante `refactor(backend)--migrate-to-microservices` ;
- ne merge **jamais** la PR #158 ;
- ne crée **jamais** de nouvelle branche, jamais de nouveau ticket Jira, jamais de nouvelle PR ;
- met à jour `backend/docs/sprint-context.md` (nouvelle § Étape 23 — clôture totale post-audit final) au fil de l'eau, regroupé en commit final d'Étape 9.1 ;
- met à jour `backend/docs/devops-handoff.md` pour ne contenir QUE les 7 items machine PINFO (commit Étape 9.2) ;
- met à jour le **PR body** de #158 quand toute la spec a été livrée (Étape 9.3) ;
- valide chaque sous-étape via `cd backend && ./mvnw -B -DskipITs verify` (~3-5 min sur le reactor 17 modules) ;
- valide les checklists de chaque vague (cf. § « Critères de complétion par vague » ci-dessous) ;
- watch CI **par étape majeure** : `gh pr checks 158 --watch` à la fin de chaque Étape ≥ 1 jusqu'à terminaison ;
- en cas d'échec CI, **fixe la cause racine** — jamais de `--no-verify`, jamais de `@Disabled`, jamais de skip silencieux, jamais de retrait d'assertion, jamais d'exclusion Sonar arbitraire ;
- si un check CI échoue uniquement parce qu'un item DevOps PINFO manque (ex. SonarCloud project not found), il **continue** et documente dans le commit + sprint-context.

Toute déviation par rapport aux décisions A-I doit être **actée explicitement** dans le commit message + dans `sprint-context.md` § Étape 23, avec justification concrète. Les déviations triviales (ex. nom de classe légèrement différent) ne nécessitent pas d'acte.

> **Leçon Flyway-immutabilité (rappel critique).** La règle « migration committée = immutable » s'applique par-base. Les 17 migrations historiques V1..V17 étaient gravées dans `flyway_schema_history` pour les déploiements existants. Les retirer + replacer ne change rien pour ces déploiements (Flyway baseline + checksum = OK), mais **rétablit la capacité de bootstrap fresh**. La spec ne génère **aucun ALTER, aucun DROP** — elle redistribue les fichiers V*.sql par service propriétaire et active Flyway.

> **Leçon « zéro stub cross-service » (rappel critique).** Aucune entité JPA `*Stub.java` ne doit réapparaître. Tous les besoins cross-domain passent par les REST clients shared-domain-dtos. La Décision B (advisory lock Postgres) respecte cette règle.

---

## Contexte

### État livré au HEAD baseline `3cc32ef8`

Le travail post-audit-2 (42 commits depuis `ec668b91`) a livré l'essentiel des P0/P1 du 2e audit :

- ✅ 0 stubs JPA cross-service (vérifié par `find -name '*Stub.java'`)
- ✅ 0 enums dupliqués entre services
- ✅ 0 `ApiErrorResponse` / `ServiceIdentityResource` / `Timeframe` locaux
- ✅ 17 modules dans le reactor (15 services dirs + contract-tests + e2e)
- ✅ 3 REST clients dans shared-domain-dtos avec resilience complète
- ✅ `quarkus.rest-client.<svc>.url` configuré dans les 4 services consommateurs
- ✅ `NotFoundExceptionMapper` dans shared-api-error
- ✅ `UserAttendancesInternalResource` créé + non routé Kong (mais `@PermitAll`, cf. SEC-002-bis)
- ✅ Admin bypass dans `UserService.getPublicProfile` (ISSUE-93)
- ✅ Cascade SCRUM-136 via param `?check-co-org-of=` avec self-check post-SEC-002
- ✅ 5 Kafka publishers via bridges `@Observes(AFTER_SUCCESS)` (5 bridges + 5 tests)
- ✅ 5 pacts JSON brokerless + 1 E2E happy path
- ✅ CI matrix consolidée + sonar-aggregate post-matrix
- ✅ Helm `image.tag` propagé aux 5 services + livenessProbe sur les 5
- ✅ Sentinels SCRUM-138 / SCRUM-144 / SCRUM-147 avec assertions réelles
- ✅ Frontend invariant : 0 ligne diff. OpenAPI invariant : 0 ligne diff.

### État NON livré (cible de cette spec)

L'audit final a identifié **35 findings non clos** : 9 BLOQUANTS + 14 IMPORTANTS + 12 MINEURS. Mapping ID → Étape ci-dessous (annexe complète en § Annexe A) :

| ID Audit | Sévérité | Étape spec |
|---|---|---|
| MIGRATIONS-001 | BLOQUANT | 1.1 (Décision A) |
| API-CONTRACT-001 | BLOQUANT | 1.2 + 4.10 |
| MODERATION-SENTINELS-001 | BLOQUANT | 1.3 (Décision H) |
| BUG-005-bis | BLOQUANT | 2.1 (Décision B) |
| BUG-006-bis | BLOQUANT | 2.2 |
| EVENT-DELETE-001 | BLOQUANT | 2.3 |
| CASCADE-136-DRIFT | BLOQUANT | 2.4 (Décision G) |
| FAVORITE-STUB-REDUNDANT | IMPORTANT | 2.5 |
| KAFKA-002 | BLOQUANT | 3.1 (Décision D) |
| SEC-002-bis | BLOQUANT | 3.2 (Décision C) |
| REST-TIMEOUT-001 | IMPORTANT | 4.1 |
| KAFKA-PUBLISH-IN-TX vérif | IMPORTANT | 4.2 |
| ADMIN-BYPASS-TEST | IMPORTANT | 4.3 |
| EVENT-DTO-DUPS | IMPORTANT | 4.4 (Décision E) |
| TZ-DRIFT | IMPORTANT | 4.5 (Décision F) |
| KAFKA-MOD-CLEANUP-IDEM | IMPORTANT | 4.6 |
| REPORT-EVENT-FIRE-NOTEST | IMPORTANT | 4.7 |
| API-ERROR-SCHEMA | IMPORTANT | 4.8 |
| JAVADOC-DRIFT | IMPORTANT | 4.9 (Décision I) |
| ARCHITECTURE-FLUX-DRIFT | IMPORTANT | 4.10 (Décision I) |
| SPRINT-CONTEXT-DRIFT | IMPORTANT | 4.11 (Décision I) |
| WEB-DEPLOY-PROBES | IMPORTANT | 4.12 |
| KONG-PDB-PREVIEW | IMPORTANT | 4.13 |
| MINOR-001 (TODO obsolète AttendanceService) | MINEUR | 5.1 |
| MINOR-002 (table roadmap pré-consolidation) | MINEUR | 5.1 |
| MINOR-003 (scaffolds redondants) | MINEUR | 5.2 |
| MINOR-004 (ParamConverters enums) | MINEUR | 5.3 |
| MINOR-005 (CI image push PR) | MINEUR | 5.4 (devops-handoff) |
| MINOR-006 (Pact provider verification) | MINEUR | 5.4 (devops-handoff) |
| MINOR-007 (aggregate-coverage.sh) | MINEUR | 5.4 |
| MINOR-008 (commits sans réf) | MINEUR | non-actionnable rétroactif |
| MINOR-009 (commits scope mélangés) | MINEUR | non-actionnable rétroactif |
| MINOR-010 (data-model.md V*.sql) | MINEUR | 5.5 (résolu par 1.1) |
| MINOR-011 (S3 cleanup hors-tx) | MINEUR | 5.5 (JavaDoc only) |
| MINOR-012 (frontend searchApi.ts) | MINEUR | hors scope (frontend invariant) |

**Findings non actionnables** : MINOR-008 + MINOR-009 (process commits — appliqué pour les commits de cette spec, pas de rebase rétroactif). MINOR-012 (invariant frontend).

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

> **Pour l'exécuteur** : chaque décision A → I ci-dessous est définitive. Aucune ne doit être tranchée au moment de l'implémentation. Si une situation imprévue émerge, applique la règle « principe de moindre surprise vs cette décision » et **acte la déviation** dans le commit message + sprint-context § Étape 23.

### Décision A — Schéma `public` partagé conservé, Flyway redistribué par owner

**Décision.** Les 17 migrations Flyway V1..V17 (perdues avec la suppression de `legacy-monolith` au commit `b570c1b`) sont **redistribuées dans les services propriétaires des tables qu'elles créent** :

| Migration | Table créée/modifiée | Service propriétaire | Destination |
|---|---|---|---|
| `V1__create_users.sql` | `users` | user-service | `backend/services/user-service/src/main/resources/db/migration/` |
| `V2__create_events.sql` | `events` | event-service | `backend/services/event-service/src/main/resources/db/migration/` |
| `V3__create_attendances.sql` | `attendances` | engagement-service | `backend/services/engagement-service/src/main/resources/db/migration/` |
| `V4__create_favorites.sql` | `favorites` | event-service | `backend/services/event-service/src/main/resources/db/migration/` |
| `V5__create_event_views.sql` | `event_views` | event-service | `backend/services/event-service/src/main/resources/db/migration/` |
| `V6__create_reports.sql` | `reports` | moderation-service | `backend/services/moderation-service/src/main/resources/db/migration/` |
| `V7__reconcile_check_constraints.sql` | events + attendances CHECK | event-service (split logique : attendances CHECK reste OK car constraint sur table créée par engagement V3 — Flyway voit V7 dans event-service comme une migration supplémentaire qui touche events ; constraint cross-table reste valide tant que public est partagé) | event-service |
| `V8__create_event_co_organizers.sql` | `event_co_organizers` | event-service | event-service |
| `V9__widen_event_description.sql` | events | event-service | event-service |
| `V10__add_report_reason_and_review_fields.sql` | reports | moderation-service | moderation-service |
| `V11__allow_event_status_expired.sql` | events CHECK | event-service | event-service |
| `V12__add_featured_to_events.sql` | events | event-service | event-service |
| `V13__allow_event_status_banned.sql` | events CHECK | event-service | event-service |
| `V14__create_follows.sql` | `follows` | user-service | user-service |
| `V15__create_comments.sql` | `comments` | engagement-service | engagement-service |
| `V16__alter_comments_parent_fk_set_null.sql` | comments | engagement-service | engagement-service |
| `V17__add_event_recurrence.sql` | events | event-service | event-service |

DB-per-schema **reste différé S9+** (cf. décision C completion-spec inchangée). Tous les services pointent encore sur le schéma `public` partagé. Chaque service active Flyway sur SON sous-ensemble — `flyway_schema_history` reste **partagée** sur la base unique. Pour éviter les conflits de version :

- chaque service a `quarkus.flyway.table=flyway_schema_history` (default) ;
- **Flyway tolère** un sous-ensemble de versions par classpath (configuration `validate-on-migrate=false` désactivée par défaut, mais `out-of-order=false` empêche un service de skipper une version d'un autre).

**Stratégie de bootstrap fresh** : un service unique (event-service) est désigné « bootstrap leader » via `quarkus.flyway.baseline-on-migrate=true` + `baseline-version=0` + `out-of-order=true`. Ce service applique toutes les V*.sql qu'il contient ; les autres services trouveront en démarrant un schéma déjà cohérent + leurs propres migrations supplémentaires (Vx pour x où elles existent dans leur classpath, no-op si absentes via `validate-on-migrate=false` LOCAL).

**Configuration `application.properties` par service** :
```properties
# Tous les 4 services métiers
quarkus.flyway.enabled=true
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.baseline-version=0
quarkus.flyway.locations=classpath:db/migration
quarkus.flyway.out-of-order=true
quarkus.flyway.validate-on-migrate=false
```

`quarkus.hibernate-orm.schema-management.strategy=validate` reste actif en prod (Flyway pose le schéma, Hibernate vérifie). En `%test`, Hibernate `drop-and-create` reste actif (DevServices fresh PostgreSQL — pas de Flyway en test).

**Justification.** (a) Restaure la capacité de bootstrap fresh sans toucher aux déploiements existants (idempotent). (b) Préserve l'invariant « 0 ligne diff openapi/frontend » et ne change rien à la topology code. (c) Aligné avec la décision C completion-spec (DB-per-service S9+). (d) `out-of-order=true` + `validate-on-migrate=false` permet à chaque service d'avoir un sous-ensemble — Flyway n'exige pas que toutes les V* présentes sur la base soient dans son classpath.

**Alternatives écartées.** (a) DB-per-schema dès maintenant : refactor majeur (rôles DB par service, GRANT, ALTER TABLE SET SCHEMA, drop des FK cross-table) — explicitement déféré S9+. (b) Un service unique « migration-service » qui héberge toutes les V*.sql : couplage anti-pattern, et oblige tous les autres services à attendre son démarrage. (c) Init scripts SQL dans Helm (pré-démarrage) : casse l'idempotence pour un dev local sans Helm.

**Adresse.** MIGRATIONS-001 (BLOQUANT). Résout aussi MINOR-010 (data-model.md V*.sql références).

---

### Décision B — `pg_advisory_xact_lock(eventId)` pour le capacity gating

**Décision.** Le capacity gating sur `POST /events/{id}/attend` et l'auto-promotion WAITLISTED sur `DELETE /events/{id}/attend` utilisent un **PostgreSQL advisory lock transactionnel** au début de chaque méthode `attend` et `removeAttendance` dans `engagement-service.AttendanceService` :

```java
@Transactional
public AttendanceDTO attend(String auth0Id, Long eventId) {
    // Acquire transactional advisory lock — released automatically on commit/rollback
    entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
        .setParameter(1, eventId)
        .getSingleResult();
    // ... reste du code inchangé
}

@Transactional
public void removeAttendance(String auth0Id, Long eventId) {
    entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
        .setParameter(1, eventId)
        .getSingleResult();
    // ... reste du code inchangé
}
```

Le lock est acquis **avant** la lecture du count + l'INSERT/DELETE. Tous les attends/removes concurrents sur le même eventId sont sérialisés. Le lock est libéré automatiquement à la fin de la transaction (`pg_advisory_xact_lock` ≠ `pg_advisory_lock` qui exige un release manuel).

**Justification.** (a) Pas de cross-service call (idiomatique Postgres). (b) Pas de stub JPA réintroduit — respecte la règle « 0 stub ». (c) Pas de mini-entité `EventCapacityRow` — moins d'états à mainteneir. (d) Coût : ~1ms par lock acquire, négligeable. (e) Pattern bien documenté Postgres, supporté par toutes les versions ≥ 9.1. (f) Le lock est **par-eventId**, donc deux events différents n'interfèrent pas.

**Alternatives écartées.** (a) Mini-entité `EventCapacityRow` mappée sur `events` avec PESSIMISTIC_WRITE : viole le « 0 stub » (lecture cross-domaine). (b) Endpoint cross-service `POST /events/{id}/_lock-capacity` côté event-service : surcoût latence + complexité harness REST + le lock devrait être propagé sur la même transaction côté engagement, ce que HTTP ne permet pas naturellement. (c) `SERIALIZABLE` isolation level : sur-engineering, retry handling complexe, performance dégradée.

**Tests obligatoires (sentinels SCRUM-144)** :
- `attend_concurrentBurst_neverExceedsCapacity` : 10 threads, capacity 5, assert `count(ATTENDING) == 5` post-burst.
- `removeAttendance_concurrentDoublePromotion_promotesOnlyOneWaitlisted` : 2 threads remove + 2 waitlisted, assert exactement 1 promotion.

**Adresse.** BUG-005-bis (BLOQUANT).

---

### Décision C — Header `X-Internal-Token` validé par filter shared-jaxrs

**Décision.** Tous les endpoints internes (cf. `internal-endpoints.md` entries #1-#6) sont protégés par un header `X-Internal-Token` :

1. **Filter JAX-RS** dans `shared-jaxrs/src/main/java/ch/unige/events/shared/jaxrs/InternalTokenFilter.java` : `@Provider` + `@PreMatching` qui inspecte chaque requête et :
   - Si la requête arrive sur un path marqué `@Internal` (annotation custom dans shared-jaxrs), exiger le header `X-Internal-Token` matchant `app.internal.token` configurable.
   - Si absent ou différent, retourner `404 Not Found` (envelope `{"error":"not_found", "message":"Resource not found"}` via NotFoundExceptionMapper) — **pas 401/403** pour ne pas leaker l'existence de l'endpoint.
2. **Annotation `@Internal`** dans shared-jaxrs : marqueur runtime sur les Resources internes (`UserAttendancesInternalResource`, `EventOrganizerUuidsResource`, `BulkAttendanceSummaryResource`, etc.).
3. **Configuration** dans chaque service consommateur : header injecté automatiquement via un `ClientRequestFilter` dans shared-tracing (`InternalTokenClientFilter`) qui lit `app.internal.token` (configurable, default valeur dev fixe ; en prod via Doppler).
4. **Kong** : configuré pour **strip** le header `X-Internal-Token` sur toutes les routes (plugin `request-transformer` `remove.headers: X-Internal-Token`) — empêche un caller externe d'injecter le header.

**Configuration `application.properties`** (chaque service) :
```properties
app.internal.token=${INTERNAL_TOKEN:dev-internal-token-not-for-prod}
quarkus.rest-client.<svc>-service.headers.X-Internal-Token=${app.internal.token}
```

**Helm chart** : `values.yaml` ajoute `internalToken: dev-internal-token-not-for-prod` (overridable Doppler en prod via `INTERNAL_TOKEN` env var, déjà conventionnel).

**Justification.** (a) Solution backend-only (la PR ne dépend pas de NetworkPolicies pour être sécure). (b) Défense en profondeur faible mais réelle : un pod compromis dans le cluster doit aussi connaître le secret. (c) Compatible avec le pattern existant (REST clients) : un header de plus dans le filter chain. (d) Kong `request-transformer` est déjà connu et utilisé. (e) NetworkPolicies restent recommandées en complément côté DevOps PINFO (item 5 du devops-handoff post-spec) — mais leur absence ne casse plus la sécurité.

**Alternatives écartées.** (a) NetworkPolicies seules : devops-only, viole la règle « rien que devops ». (b) mTLS service-to-service : sur-engineering pour un projet pinfo6, exige certs/PKI. (c) JWT service-to-service : duplique l'auth Auth0 sans valeur ajoutée.

**Tests obligatoires** :
- `InternalTokenFilterTest` (shared-jaxrs) : 200 si header valide, 404 si absent, 404 si différent, ignore les paths sans `@Internal`.
- `UserAttendancesInternalResourceTest` (engagement-service) : 200 avec header, 404 sans header.

**Adresse.** SEC-002-bis (BLOQUANT). Préserve la simplicité d'usage côté REST clients (header injecté automatiquement).

---

### Décision D — `MdcKafkaInterceptor` dans shared-tracing (producer + consumer)

**Décision.** Implémenter dans `shared-tracing` :

1. **`MdcKafkaProducerInterceptor`** : `org.apache.kafka.clients.producer.ProducerInterceptor` qui ajoute un header Kafka `X-Request-ID` au record sortant en lisant la valeur depuis MDC (`MDC.get("requestId")`). Si MDC est vide, le header n'est pas ajouté.
2. **`MdcKafkaConsumerInterceptor`** : `org.apache.kafka.clients.consumer.ConsumerInterceptor` qui lit le header `X-Request-ID` du record entrant et le pose en MDC avant la propagation au `@Incoming` handler.

**Configuration** : chaque service métier ajoute dans son `application.properties` :
```properties
# Producer-side
mp.messaging.outgoing.<channel>.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaProducerInterceptor
# (à appliquer sur tous les channels outgoing : events-published, events-cancelled, events-expired, events-banned, comments-created, follows-followed, follows-follow-requested, follows-follow-accepted, co-organizers-invited, co-organizers-accepted)

# Consumer-side (event-service uniquement, pour events.banned)
mp.messaging.incoming.events-banned.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaConsumerInterceptor
```

**Tests obligatoires** :
- `MdcKafkaProducerInterceptorTest` (shared-tracing) : assert qu'un emit en MDC `requestId="abc-123"` produit un record avec header `X-Request-ID=abc-123`.
- `MdcKafkaConsumerInterceptorTest` (shared-tracing) : assert qu'un record avec header `X-Request-ID=abc-123` pose `MDC.get("requestId") == "abc-123"` avant le handler.
- Un test e2e dans event-service `EventBannedConsumerTracingTest` : produire un message via `EventBannedKafkaBridge` avec MDC `requestId` posé par `RequestIdFilter`, consume côté event-service, assert que MDC est bien posé dans le handler.

**Justification.** (a) Adresse formellement KAFKA-002 BLOQUANT — cohérence avec architecture.md et AGENTS.md qui annoncent ce composant comme livré. (b) Pattern Kafka standard (interceptor — pas une réinvention). (c) Coût négligeable (lecture MDC + ajout d'un header bytes). (d) Backward compatible : si MDC est vide ou si le consumer n'a pas l'interceptor, comportement identique au actuel.

**Alternatives écartées.** (a) Retirer la mention de `MdcKafkaInterceptor` de la doc et différer S9 : non acceptable, l'audit le note BLOQUANT. (b) Wrapper Emitter custom dans shared-kafka-events : plus invasif (chaque publisher doit l'utiliser), moins idiomatique que ProducerInterceptor.

**Adresse.** KAFKA-002 (BLOQUANT).

---

### Décision E — `EventDTO` 4 sous-packages event-service : JavaDoc justificatif (pas de consolidation)

**Décision.** Les 4 copies locales d'`EventDTO` dans event-service (`event/dto/`, `event/coorganizer/dto/`, `event/favorite/dto/`, `event/me/dto/`) sont **conservées** et chacune reçoit une JavaDoc class-level qui documente le pattern :

```java
/**
 * Consumer-shape projection of {@link Event} for the {@code <subpkg>} bounded
 * context within event-service. Intentionally co-exists with sibling
 * EventDTO records in {@code event.dto} (master), {@code event.me.dto},
 * {@code event.favorite.dto}, {@code event.coorganizer.dto} — each variant
 * differs by nullability of count fields and by the presence of
 * {@code coOrganizerOf} (only on the master variant in {@code event.dto}).
 *
 * <p>This is INTENTIONAL post-finalization (Décision A pivot, commit
 * 0ee8623a). DO NOT consolidate without revisiting the spec — consolidation
 * was attempted and reverted to avoid regressing typing/coverage.
 */
public record EventDTO(...) {}
```

**Justification.** (a) Consolidation a été tentée et écartée pour cause de régression typage/couverture (commit 0ee8623a). (b) La duplication est limitée (4 fichiers, ~30 champs identiques) et bien isolée par namespace. (c) JavaDoc rend l'intention explicite — adresse la critique audit « non documenté ». (d) Coût XS vs L pour consolidation effective avec risque de régression.

**Alternatives écartées.** (a) Consolidation effective avec champs nullable : regression risque + perte de typage strict (`long` vs `Long`). (b) Génération de code (Lombok @Builder) : sur-engineering.

**Adresse.** EVENT-DTO-DUPS (IMPORTANT).

---

### Décision F — `TZ=Europe/Zurich` fixé dans Helm Deployments (pas de normalisation code)

**Décision.** Dans tous les Deployments Helm (`k8s/chart/templates/{event,user,engagement,moderation,notification}-service/deployment.yaml` + `k8s/chart/templates/web/deployment.yaml`), ajouter dans la section `containers.env` :

```yaml
env:
  - name: TZ
    value: "Europe/Zurich"
  - name: JAVA_TOOL_OPTIONS
    value: "-Duser.timezone=Europe/Zurich"
```

**Justification.** (a) Zero-diff code (le code reste tel quel, hypothèse "JVM TZ = Europe/Zurich" devient invariant infrastructure). (b) Aligne tous les services sur la même TZ — cohérent avec le métier UNIGE. (c) Pas de normalisation à l'ingestion (qui demanderait modification de `EventService.create/update` + tests). (d) Test de smoke ajouté dans `event-service` : `EventTzSmokeTest` qui assert `ZoneId.systemDefault() == ZoneId.of("Europe/Zurich")` — passe en CI dans le container Docker.

**Configuration values.yaml** : pas de variable, le TZ est fixé en dur dans les templates Helm (toute infra est en Europe/Zurich pour ce projet).

**Alternatives écartées.** (a) Normaliser à l'ingestion (UTC en DB) : modifications code dans EventService + tests + risque de régression sur le frontend qui consomme les dates. (b) `@JsonFormat(timezone="Europe/Zurich")` partout : invasif, et ne résout pas le `LocalDateTime.now()` interne.

**Adresse.** TZ-DRIFT (IMPORTANT). À coupler avec `EventTzSmokeTest` ajouté en Étape 4.5.

---

### Décision G — CASCADE-136-DRIFT : vérification + remédiation conditionnelle

**Décision.** À l'Étape 2.4, l'exécuteur :

1. Grep `isCreatorOrAcceptedCoOrganizer\|EventCoOrganizerStub\|CommentService.*cascade` dans `engagement-service/.../comment/`.
2. Si la cascade est encore inline (lecture cross-domaine ou helper local), la **supprimer** et remplacer par `eventServiceClient.getByIdWithCoOrgCheck(eventId, callerUuid)` ; la décision booléenne `coOrganizerOf` est lue depuis le payload `EventDTO` enrichi.
3. Si déjà OK (la cascade utilise déjà le REST client), valider l'absence par grep et noter dans le commit message « no-op verification ».
4. Dans tous les cas, ajouter au moins un test sentinel `EngagementDomainSentinelsTest.cascadeScrum136_viaRestClient_noLocalLogic` qui mock `eventServiceClient.getByIdWithCoOrgCheck` et vérifie que `CommentService.post(...)` propage l'autorisation via la valeur retournée.

**Justification.** Le commit `6947b0b8` annonce avoir câblé 3 REST clients pour engagement-service, mais l'audit n'a pas confirmé l'absence de cascade inline. Une vérification + remédiation conditionnelle est l'approche prudente.

**Adresse.** CASCADE-136-DRIFT (BLOQUANT).

---

### Décision H — `ModerationDomainSentinelsTest` SCRUM-139 : 8 tests pin

**Décision.** Créer `backend/services/moderation-service/src/test/java/ch/unige/events/report/sentinels/ModerationDomainSentinelsTest.java` avec exactement les 8 méthodes `@Test` suivantes (noms pin pour cohérence avec SCRUM-138/144/147) :

```java
@QuarkusTest
@TestSecurity(user = "auth0|sentinel-user")
@SuppressWarnings({"java:S100", "java:S5961"})
class ModerationDomainSentinelsTest {

    @Test void report_ownEvent_returns422_unprocessable() { ... }
    @Test void report_eventDraftByNonCreator_returns404_antiOracle() { ... }
    @Test void report_eventBanned_returns404_antiOracle() { ... }
    @Test void report_idempotent_secondReportBySameUserIs422() { ... }
    @Test void handle_adminMarksReviewed_emitsEventsBannedKafka() { ... }
    @Test void handle_idempotent_secondBanIsNoop() { ... }
    @Test void cleanup_eventsAboveThreshold_areBannedAndKafkaEmitted() { ... }
    @Test void cleanup_belowThreshold_doesNothing() { ... }
}
```

Chaque test utilise les mocks REST client `@InjectMock @RestClient EventServiceClient` + `@InjectMock @RestClient UserServiceClient` + `@InjectMock Event<EventBannedEvent> bannedEvent` (résout REPORT-EVENT-FIRE-NOTEST en même temps).

**Adresse.** MODERATION-SENTINELS-001 (BLOQUANT) + REPORT-EVENT-FIRE-NOTEST (IMPORTANT).

---

### Décision I — Doc + JavaDoc cleanup : sed batch ciblé + refonte 4 sections

**Décision.** Trois passes parallèles, regroupées en 1-3 commits :

1. **Sed batch JavaDoc** sur `/workspace/backend/services/*/src/{main,test}/**/*.java` :
   - `Owned by favorite-service` → `Owned by event-service (co-located post-finalization)`
   - `Owned by view-service` → `Owned by event-service (co-located post-finalization)`
   - `Owned by share-service` → `Owned by event-service (co-located post-finalization)`
   - `Owned by stats-service` → `Owned by event-service (co-located post-finalization)`
   - `Owned by me-aggregator-service` → `Owned by event-service (co-located post-finalization)`
   - `Owned by co-organizer-service` → `Owned by event-service (co-located post-finalization)`
   - `Owned by follow-service` → `Owned by user-service (co-located post-finalization)`
   - `Owned by calendar-service` → `Owned by user-service (co-located post-finalization)`
   - `Owned by attendance-service` → `Owned by engagement-service (renamed post-finalization)`
   - `Owned by comment-service` → `Owned by engagement-service (co-located post-finalization)`
   - `Owned by report-service` → `Owned by moderation-service (renamed post-finalization)`
   - Mentions narratives (« replaced by REST client at PR 12/13 », « will become a REST call to co-organizer-service », etc.) : remplacer par `(replaced by REST client post-finalization)` ou supprimer la phrase entière.

2. **Refonte sections doc** :
   - `backend/docs/api-contract.md` lignes 11-47, 76-110 : table topologie + endpoints listant 11 services dissous → table refondue sur 5 services (event/user/engagement/moderation/notification), cross-référence `k8s/chart/templates/kong/configmap-routes.yaml`.
   - `backend/docs/architecture.md` lignes 200-204 : exemple cross-service `POST /api/events/{id}/comments` cite comment-service + co-organizer-service → reformuler en engagement-service + cascade locale event-service.
   - `backend/docs/sprint-context.md` lignes 892-909 (récap Étape 21 status table) : « 13 services actifs » + listings → « 4 services métiers actifs + 1 placeholder notification ».
   - `backend/docs/microservices-migration-roadmap.md` table extractions pré-consolidation : ajouter note « *Ces services ont été consolidés en Étape 2 finalization — pour l'état final, voir consolidation-plan.md.* ».
   - `backend/docs/data-model.md` mentions V*.sql : laisser tel quel (résolu par Décision A — les V*.sql redeviennent vraies, leurs paths changent juste).

3. **Sed batch sprint-context.md / autres docs** : grep résiduel `13 services\|13 microservices\|13/15 SonarCloud\|legacy-monolith` → corriger ou supprimer toute occurrence n'incluant pas explicitement « renamed/absorbed since finalization ».

**Justification.** Sed batch est rapide et reproductible. Les 4 sections doc demandent des refontes qualifiées (pas du sed pur).

**Adresse.** JAVADOC-DRIFT, ARCHITECTURE-FLUX-DRIFT, SPRINT-CONTEXT-DRIFT, API-CONTRACT-001 (tous IMPORTANTS sauf API-CONTRACT-001 BLOQUANT). Résout aussi MINOR-001 (TODO obsolète AttendanceService) et MINOR-002 (table roadmap pré-consolidation).

---

## Architecture cible (synthèse des diff post-spec)

### POMs

- `backend/services/{user,event,engagement,moderation}-service/pom.xml` : ajouter `<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-flyway</artifactId></dependency>` (déjà présent ? à vérifier — si absent, ajouter).

### `application.properties` par service

- 4 services métiers : ajouter le bloc Flyway (Décision A).
- 4 services métiers : ajouter `quarkus.rest-client.<svc>-service.read-timeout=5000` pour chaque REST client.
- 4 services métiers : ajouter `quarkus.rest-client.<svc>-service.headers.X-Internal-Token=${app.internal.token}` (Décision C).
- 4 services métiers : ajouter `app.internal.token=${INTERNAL_TOKEN:dev-internal-token-not-for-prod}`.
- 4 services métiers : ajouter `mp.messaging.outgoing.<channel>.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaProducerInterceptor` pour chaque channel outgoing.
- event-service : ajouter `mp.messaging.incoming.events-banned.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaConsumerInterceptor`.

### Helm templates

- `k8s/chart/templates/{event,user,engagement,moderation,notification}-service/deployment.yaml` + `web/deployment.yaml` : ajouter `env: TZ=Europe/Zurich` + `JAVA_TOOL_OPTIONS=-Duser.timezone=Europe/Zurich` (Décision F).
- `k8s/chart/templates/web/deployment.yaml` : ajouter `livenessProbe` (WEB-DEPLOY-PROBES).
- `k8s/chart/templates/kong/poddisruptionbudget.yaml` (nouveau) : `minAvailable: 1` (KONG-PDB-PREVIEW).
- `k8s/chart/templates/kong/configmap-routes.yaml` : ajouter plugin `request-transformer` global qui strip `X-Internal-Token` (Décision C).
- `k8s/chart/values.yaml` : ajouter `internalToken: dev-internal-token-not-for-prod` (overridable Doppler).

### Nouveaux fichiers Java

- `backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaProducerInterceptor.java`
- `backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaConsumerInterceptor.java`
- `backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/InternalTokenClientFilter.java`
- `backend/services/shared-jaxrs/src/main/java/ch/unige/events/shared/jaxrs/InternalTokenFilter.java`
- `backend/services/shared-jaxrs/src/main/java/ch/unige/events/shared/jaxrs/Internal.java` (annotation marker)
- `backend/services/moderation-service/src/test/java/ch/unige/events/report/sentinels/ModerationDomainSentinelsTest.java`
- 17 fichiers V*__*.sql redistribués (4 dans user-service, 11 dans event-service, 3 dans engagement-service, 2 dans moderation-service — voir mapping Décision A).
- Tests des nouveaux interceptors / filter (`MdcKafkaProducerInterceptorTest`, `MdcKafkaConsumerInterceptorTest`, `InternalTokenFilterTest`, `EventTzSmokeTest`, `EventBannedConsumerTracingTest`).

### Modifications Java

- `engagement-service/.../AttendanceService.java` : ajouter `pg_advisory_xact_lock` au début de `attend` et `removeAttendance` (Décision B).
- `event-service/.../EventService.java` : ajouter `DELETE FROM EventCoOrganizer co WHERE co.eventId = :id` dans `delete` (EVENT-DELETE-001).
- `event-service/.../FavoriteService.java` : try-catch unique sur `addFavorite` ou upsert SQL natif (BUG-006-bis).
- `engagement-service/.../CommentService.java` : remplacer cascade inline par REST client (CASCADE-136-DRIFT, conditionnellement).
- 4 EventDTO sub-packages : JavaDoc class-level (EVENT-DTO-DUPS).
- `shared-api-error/.../ApiErrorResponse.java` : ajouter `@Schema` (API-ERROR-SCHEMA).
- `engagement-service/.../UserAttendancesInternalResource.java` : ajouter `@Internal` (Décision C).
- Tous les autres internal resources (`EventOrganizerUuidsResource`, `BulkAttendanceSummaryResource`, etc.) : ajouter `@Internal`.
- `user-service/.../UserService.java` : test unitaire admin bypass (ADMIN-BYPASS-TEST).

### Modifications doc

- `backend/docs/{api-contract,architecture,sprint-context,microservices-migration-roadmap,data-model,internal-endpoints,devops-handoff}.md` : refontes ciblées (Décision I + Étape 9.2).

---

## Plan d'implémentation par étape (ORDRE STRICT)

> **Pour chaque sous-étape** : pré-requis → fichiers touchés → action → validation → message commit → finding(s) clos.

### Étape 0 — Pré-flight (1 commit)

#### Étape 0.1 — Pull + état initial

**Pré-requis** : aucun.
**Action** :
```bash
git pull
git rev-parse HEAD                      # Doit être ≥ 3cc32ef8
find /workspace/backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l   # → 0
find /workspace/backend -name "V*__*.sql" -not -path '*/target/*' | wc -l             # → 0 (à fixer en 1.1)
cd backend && ./mvnw -B -DskipITs verify                                              # → SUCCESS sur 17 modules
```
**Validation** : build SUCCESS, état attendu confirmé.
**Commit** : aucun (lecture seule).
**Finding clos** : aucun.

---

### Étape 1 — Vague 1 BLOQUANTS infrastructure

#### Étape 1.1 — Redistribuer Flyway V1..V17 dans les services propriétaires (Décision A)

**Pré-requis** : Étape 0 OK.
**Fichiers touchés** :
- 17 fichiers `V*.sql` extraits depuis `git show <commit-pré-b570c1b>:backend/services/legacy-monolith/src/main/resources/db/migration/V*.sql` (ou `git show main:backend/src/main/resources/db/migration/V*.sql` qui est le snapshot main, plus accessible).
- Création des dossiers `backend/services/{user,event,engagement,moderation}-service/src/main/resources/db/migration/`.
- Modification des 4 `application.properties` : ajouter le bloc Flyway (cf. Décision A).
- Modification des 4 `pom.xml` : ajouter `quarkus-flyway` si absent.

**Action** :
```bash
# Récupérer les 17 V*.sql depuis main
mkdir -p /tmp/legacy-migrations
for v in V1 V2 V3 V4 V5 V6 V7 V8 V9 V10 V11 V12 V13 V14 V15 V16 V17; do
  fname=$(git show main --name-only --pretty=format: 2>/dev/null | grep "${v}__" | head -1)
  if [ -z "$fname" ]; then
    fname=$(git ls-tree -r main | grep "${v}__" | head -1 | awk '{print $4}')
  fi
  git show main:"$fname" > /tmp/legacy-migrations/$(basename "$fname")
done

# Redistribuer selon le mapping Décision A
# user-service
mv /tmp/legacy-migrations/V1__create_users.sql backend/services/user-service/src/main/resources/db/migration/
mv /tmp/legacy-migrations/V14__create_follows.sql backend/services/user-service/src/main/resources/db/migration/

# event-service
for v in V2__create_events V4__create_favorites V5__create_event_views V7__reconcile_check_constraints V8__create_event_co_organizers V9__widen_event_description V11__allow_event_status_expired V12__add_featured_to_events V13__allow_event_status_banned V17__add_event_recurrence; do
  mv /tmp/legacy-migrations/${v}.sql backend/services/event-service/src/main/resources/db/migration/
done

# engagement-service
mv /tmp/legacy-migrations/V3__create_attendances.sql backend/services/engagement-service/src/main/resources/db/migration/
mv /tmp/legacy-migrations/V15__create_comments.sql backend/services/engagement-service/src/main/resources/db/migration/
mv /tmp/legacy-migrations/V16__alter_comments_parent_fk_set_null.sql backend/services/engagement-service/src/main/resources/db/migration/

# moderation-service
mv /tmp/legacy-migrations/V6__create_reports.sql backend/services/moderation-service/src/main/resources/db/migration/
mv /tmp/legacy-migrations/V10__add_report_reason_and_review_fields.sql backend/services/moderation-service/src/main/resources/db/migration/
```

Puis ajouter dans chaque `application.properties` (les 4 services) :
```properties
quarkus.flyway.enabled=true
quarkus.flyway.migrate-at-start=true
quarkus.flyway.baseline-on-migrate=true
quarkus.flyway.baseline-version=0
quarkus.flyway.locations=classpath:db/migration
quarkus.flyway.out-of-order=true
quarkus.flyway.validate-on-migrate=false
```

Puis ajouter dans chaque `pom.xml` (si absent) :
```xml
<dependency>
  <groupId>io.quarkus</groupId>
  <artifactId>quarkus-flyway</artifactId>
</dependency>
```

**Validation** :
```bash
find /workspace/backend -name "V*__*.sql" -not -path '*/target/*' | wc -l   # → 17
cd backend && ./mvnw -B -DskipITs verify                                     # → SUCCESS
```
**Commit** : `feat(backend): redistribute Flyway V1..V17 to owning services + activate Flyway (Étape 1.1, MIGRATIONS-001 / Décision A)`
**Finding clos** : MIGRATIONS-001 (BLOQUANT), MINOR-010 (data-model.md V*.sql références redeviennent valides).

---

#### Étape 1.2 — Refonte `api-contract.md` table topologie + endpoints

**Pré-requis** : Étape 1.1 OK.
**Fichiers touchés** : `backend/docs/api-contract.md` (lignes 11-47, 76-110).

**Action** : remplacer la table topologie pré-consolidation par une table 5-services :
```markdown
| Endpoint | Service propriétaire |
|---|---|
| `/api/events*`, `/api/events/{id}/{share,view,favorite,co-organizers/*,stats,image}`, `/api/admin/events/{id}/{,un}feature`, `/api/users/me/{events,favorites,co-organizer-invitations}`, `/api/s/{shortCode}` | **event-service** |
| `/api/users/{me,{id}}`, `/api/users/me/{image,banner,calendar-token*,follow-requests}`, `/api/users/{id}/follow*`, `/api/follow-requests/*`, `/api/calendar/{token}.ics` | **user-service** |
| `/api/events/{id}/{attend*,comments,attendees}`, `/api/users/me/{attendances,participations}`, `/api/comments/{id}` | **engagement-service** |
| `/api/events/{id}/report`, `/api/admin/reports*` | **moderation-service** |
| (placeholder, replicas:0, SCRUM-99) | **notification-service** |
```

Cross-référencer `k8s/chart/templates/kong/configmap-routes.yaml` (source de vérité Kong). Supprimer toutes les mentions des 11 services dissous.

**Validation** :
```bash
grep -E "favorite-service|calendar-service|follow-service|share-service|view-service|comment-service|co-organizer-service|attendance-service|me-aggregator-service|report-service|stats-service" backend/docs/api-contract.md | grep -v "renamed\|absorbed\|consolidated"
# → 0 résultat
```
**Commit** : `docs(backend): refactor api-contract.md topology + endpoints to 5-service post-finalization (Étape 1.2, API-CONTRACT-001)`
**Finding clos** : API-CONTRACT-001 (BLOQUANT).

---

#### Étape 1.3 — Créer `ModerationDomainSentinelsTest.java` (Décision H)

**Pré-requis** : Étape 1.2 OK.
**Fichiers touchés** :
- Nouveau : `backend/services/moderation-service/src/test/java/ch/unige/events/report/sentinels/ModerationDomainSentinelsTest.java`
- Helpers existants : `JwtTestContext`, `JwtTestHelper`, `TestJwtProducer` (déjà présents dans `moderation-service/src/test/java/ch/unige/events/report/test/`).

**Action** : créer le fichier avec les 8 méthodes pin de la Décision H. Chaque test :
- `@QuarkusTest` + `@TestSecurity(user = "auth0|sentinel-user")`
- `@Inject ReportService reportService` + `@Inject EntityManager em`
- `@InjectMock @RestClient EventServiceClient eventClient` + `@InjectMock @RestClient UserServiceClient userClient`
- `@InjectMock Event<EventBannedEvent> bannedEvent` (résout REPORT-EVENT-FIRE-NOTEST simultanément)
- `@TestTransaction` sur chaque méthode
- Setup : persist d'un User et d'un Event de test
- Mock des REST clients pour retourner les payloads attendus
- Assertions sur le throw / status / Kafka emit

**Validation** :
```bash
cd backend && ./mvnw -B -pl services/moderation-service test
# Doit lister les 8 nouveaux tests dans la sortie surefire
```
**Commit** : `test(moderation): add ModerationDomainSentinelsTest with 8 SCRUM-139 sentinels + bannedEvent.fire mock (Étape 1.3, MODERATION-SENTINELS-001 / REPORT-EVENT-FIRE-NOTEST / Décision H)`
**Finding clos** : MODERATION-SENTINELS-001 (BLOQUANT), REPORT-EVENT-FIRE-NOTEST (IMPORTANT).

---

### Étape 2 — Vague 2 BLOQUANTS code

#### Étape 2.1 — `pg_advisory_xact_lock` capacity gating (Décision B)

**Pré-requis** : Étape 1 OK.
**Fichiers touchés** : `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java`.

**Action** : ajouter au début de `attend(...)` et `removeAttendance(...)` :
```java
entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
    .setParameter(1, eventId)
    .getSingleResult();
```

**Tests à ajouter dans `EngagementDomainSentinelsTest`** :
- `attend_concurrentBurst_neverExceedsCapacity` : utiliser `ExecutorService` (10 threads), capacity 5, assert `Attendance.count("eventId = ?1 and status = ?2", eventId, ATTENDING) == 5L` post-burst. NB : pour fonctionner avec `@TestTransaction`, le test doit lancer chaque attend dans une transaction séparée (utiliser `QuarkusTransaction.requiringNew().run(...)`).
- `removeAttendance_concurrentDoublePromotion_promotesOnlyOneWaitlisted` : 2 attendances ATTENDING + 2 WAITLISTED, lancer 2 removes parallèles, assert exactement 1 promotion.

**Validation** :
```bash
cd backend && ./mvnw -B -pl services/engagement-service test -Dtest=EngagementDomainSentinelsTest
```
**Commit** : `fix(engagement): add pg_advisory_xact_lock to attend + removeAttendance for capacity gating (Étape 2.1, BUG-005-bis / Décision B)`
**Finding clos** : BUG-005-bis (BLOQUANT).

---

#### Étape 2.2 — `FavoriteService.addFavorite` idempotence robuste (BUG-006-bis)

**Pré-requis** : Étape 2.1 OK.
**Fichiers touchés** : `backend/services/event-service/src/main/java/ch/unige/events/event/favorite/service/FavoriteService.java`.

**Action** : remplacer le pattern `findByUserAndEvent + persist` par un upsert SQL natif `ON CONFLICT DO NOTHING` :
```java
@Transactional
public void addFavorite(String auth0Id, Long eventId) {
    Event.<Event>findByIdOptional(eventId)
        .orElseThrow(() -> new NotFoundException("Event not found"));

    UUID userId = resolveUserId();

    int rowsInserted = entityManager.createNativeQuery(
            "INSERT INTO favorites (user_id, event_id, created_at) VALUES (?1, ?2, NOW()) " +
            "ON CONFLICT (user_id, event_id) DO NOTHING")
        .setParameter(1, userId)
        .setParameter(2, eventId)
        .executeUpdate();
    // rowsInserted == 0 si déjà favorite (idempotent), 1 si nouveau
}
```

**Test à ajouter dans EventDomainSentinelsTest** :
- `addFavorite_concurrentDoubleTap_isIdempotentNoConflict` : 2 threads, 1 user, 1 event, assert `Favorite.count("userId = ?1 and eventId = ?2", userId, eventId) == 1`.

**Validation** :
```bash
cd backend && ./mvnw -B -pl services/event-service test -Dtest=EventDomainSentinelsTest
```
**Commit** : `fix(event): make FavoriteService.addFavorite idempotent via ON CONFLICT DO NOTHING (Étape 2.2, BUG-006-bis)`
**Finding clos** : BUG-006-bis (BLOQUANT).

---

#### Étape 2.3 — `EventService.delete` purge `EventCoOrganizer` (EVENT-DELETE-001)

**Pré-requis** : Étape 2.2 OK.
**Fichiers touchés** : `backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java` (lignes 430-452).

**Action** : ajouter avant `event.delete()` :
```java
entityManager.createQuery("DELETE FROM EventCoOrganizer co WHERE co.eventId = :id")
    .setParameter("id", id).executeUpdate();
```

**Test à ajouter dans EventDomainSentinelsTest** :
- `delete_eventWithCoOrganizers_removesAllCoOrganizers` : créer un event CANCELLED + 3 EventCoOrganizer (PENDING/ACCEPTED/PENDING), call delete, assert `EventCoOrganizer.count("eventId = ?1", id) == 0L`.

**Validation** :
```bash
cd backend && ./mvnw -B -pl services/event-service test -Dtest=EventDomainSentinelsTest
```
**Commit** : `fix(event): purge EventCoOrganizer rows in EventService.delete to avoid orphans (Étape 2.3, EVENT-DELETE-001)`
**Finding clos** : EVENT-DELETE-001 (BLOQUANT).

---

#### Étape 2.4 — Vérification + remédiation CASCADE-136-DRIFT (Décision G)

**Pré-requis** : Étape 2.3 OK.
**Fichiers touchés** : `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java` (à inspecter), `EngagementDomainSentinelsTest.java` (ajout d'un test).

**Action** :
```bash
grep -n "isCreatorOrAcceptedCoOrganizer\|EventCoOrganizerStub\|cascade.*SCRUM" \
  backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java
```

- **Cas A — résultat vide** : la cascade utilise déjà `eventServiceClient.getByIdWithCoOrgCheck(...)`. Ajouter le test sentinel suivant et commit comme « no-op verification ».
- **Cas B — résultat non vide** : refactor `CommentService.post(...)` pour remplacer la logique inline par `eventServiceClient.getByIdWithCoOrgCheck(eventId, callerUuid)` qui retourne `EventDTO` enrichi de `coOrganizerOf`. Tester la cascade via mock REST client.

**Test ajouté dans EngagementDomainSentinelsTest** :
```java
@Test
void cascadeScrum136_viaRestClient_postCommentByCoOrganizer_succeeds() {
    // Mock eventClient.getByIdWithCoOrgCheck(eventId, callerUuid) → EventDTO with coOrganizerOf=true
    // Call commentService.post(eventId, "test", null, "auth0|caller")
    // Assert no throw, comment persisted
}
```

**Validation** :
```bash
cd backend && ./mvnw -B -pl services/engagement-service test
```
**Commit** : `fix(engagement): ensure SCRUM-136 cascade uses REST client + add sentinel test (Étape 2.4, CASCADE-136-DRIFT / Décision G)`
**Finding clos** : CASCADE-136-DRIFT (BLOQUANT).

---

#### Étape 2.5 — Vérification FAVORITE-STUB-REDUNDANT

**Pré-requis** : Étape 2.4 OK.
**Fichiers touchés** : potentiellement `backend/services/event-service/src/main/java/ch/unige/events/event/entity/FavoriteStub.java` (si existe encore) + call-sites.

**Action** :
```bash
find /workspace/backend/services/event-service -name "FavoriteStub.java" -not -path "*/target/*"
# Si présent → supprimer + remplacer call-sites par Favorite (entité locale post-2.2.3)
```

**Si absent** : commit no-op de validation, `chore(event): verify FavoriteStub absence post-finalization (Étape 2.5, FAVORITE-STUB-REDUNDANT)`.
**Si présent** : `refactor(event): remove redundant FavoriteStub, use Favorite entity directly (Étape 2.5, FAVORITE-STUB-REDUNDANT)`.

**Validation** :
```bash
find /workspace/backend/services -name "*Stub.java" -not -path "*/target/*" | wc -l   # → 0
cd backend && ./mvnw -B -DskipITs verify
```
**Finding clos** : FAVORITE-STUB-REDUNDANT (IMPORTANT).

---

### Étape 3 — Vague 3 BLOQUANTS observabilité + sécurité

#### Étape 3.1 — Implémenter `MdcKafkaProducerInterceptor` + `MdcKafkaConsumerInterceptor` (Décision D)

**Pré-requis** : Étape 2 OK.
**Fichiers touchés** :
- Nouveau : `backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaProducerInterceptor.java`
- Nouveau : `backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaConsumerInterceptor.java`
- Tests : `MdcKafkaProducerInterceptorTest.java`, `MdcKafkaConsumerInterceptorTest.java`
- 4 services métiers : `application.properties` (config interceptor par channel).
- event-service : 1 test e2e `EventBannedConsumerTracingTest.java`.

**Action** :

`MdcKafkaProducerInterceptor.java` :
```java
package ch.unige.events.shared.tracing;

import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MdcKafkaProducerInterceptor implements ProducerInterceptor<Object, Object> {
    public static final String HEADER = "X-Request-ID";

    @Override
    public ProducerRecord<Object, Object> onSend(ProducerRecord<Object, Object> record) {
        String requestId = MDC.get("requestId");
        if (requestId != null && !requestId.isBlank()) {
            Headers headers = record.headers();
            if (headers.lastHeader(HEADER) == null) {
                headers.add(HEADER, requestId.getBytes(StandardCharsets.UTF_8));
            }
        }
        return record;
    }

    @Override public void onAcknowledgement(RecordMetadata metadata, Exception exception) {}
    @Override public void close() {}
    @Override public void configure(Map<String, ?> configs) {}
}
```

`MdcKafkaConsumerInterceptor.java` :
```java
package ch.unige.events.shared.tracing;

import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

public class MdcKafkaConsumerInterceptor implements ConsumerInterceptor<Object, Object> {
    public static final String HEADER = "X-Request-ID";

    @Override
    public ConsumerRecords<Object, Object> onConsume(ConsumerRecords<Object, Object> records) {
        records.forEach(record -> {
            Header h = record.headers().lastHeader(HEADER);
            if (h != null) {
                MDC.put("requestId", new String(h.value(), StandardCharsets.UTF_8));
            }
        });
        return records;
    }

    @Override public void onCommit(Map<TopicPartition, org.apache.kafka.clients.consumer.OffsetAndMetadata> offsets) {}
    @Override public void close() {}
    @Override public void configure(Map<String, ?> configs) {}
}
```

Configuration `application.properties` event-service (exemple — à dupliquer pour les autres services et channels) :
```properties
mp.messaging.outgoing.events-published.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaProducerInterceptor
mp.messaging.outgoing.events-cancelled.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaProducerInterceptor
mp.messaging.outgoing.events-expired.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaProducerInterceptor
mp.messaging.outgoing.co-organizers-invited.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaProducerInterceptor
mp.messaging.outgoing.co-organizers-accepted.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaProducerInterceptor
mp.messaging.incoming.events-banned.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaConsumerInterceptor
```

Idem pour `user-service` (4 channels follow-*), `engagement-service` (1 channel comments-created), `moderation-service` (1 channel events-banned).

**Tests** :
- `MdcKafkaProducerInterceptorTest` : poser MDC, créer un ProducerRecord, appeler `onSend`, assert que le header est présent.
- `MdcKafkaConsumerInterceptorTest` : créer un ConsumerRecords avec header, appeler `onConsume`, assert que MDC contient la valeur.
- `EventBannedConsumerTracingTest` (event-service, `@QuarkusTest`) : produire un EventBannedEvent via Bridge avec MDC posé, consume, vérifier que le handler voit la valeur en MDC.

**Validation** :
```bash
cd backend && ./mvnw -B -DskipITs verify
```
**Commit** : `feat(shared-tracing): add MdcKafkaProducerInterceptor + MdcKafkaConsumerInterceptor + wire on all channels (Étape 3.1, KAFKA-002 / Décision D)`
**Finding clos** : KAFKA-002 (BLOQUANT).

---

#### Étape 3.2 — Sécuriser endpoints internes via header X-Internal-Token (Décision C)

**Pré-requis** : Étape 3.1 OK.
**Fichiers touchés** :
- Nouveau : `backend/services/shared-jaxrs/src/main/java/ch/unige/events/shared/jaxrs/Internal.java` (annotation marker)
- Nouveau : `backend/services/shared-jaxrs/src/main/java/ch/unige/events/shared/jaxrs/InternalTokenFilter.java`
- Nouveau : `backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/InternalTokenClientFilter.java`
- Nouveau : `InternalTokenFilterTest.java`
- 4 services métiers `application.properties` : ajouter `app.internal.token=${INTERNAL_TOKEN:dev-internal-token-not-for-prod}` + headers config sur REST clients.
- Tous les internal resources : ajouter `@Internal` (`UserAttendancesInternalResource`, `EventOrganizerUuidsResource`, `BulkAttendanceSummaryResource`, et tous les autres documentés dans `internal-endpoints.md`).
- 3 REST clients (shared-domain-dtos) : ajouter `@RegisterProvider(InternalTokenClientFilter.class)`.
- `k8s/chart/templates/kong/configmap-routes.yaml` : ajouter plugin global `request-transformer` qui strip `X-Internal-Token`.
- `k8s/chart/values.yaml` : ajouter `internalToken: dev-internal-token-not-for-prod`.

**Action** :

`Internal.java` (annotation) :
```java
package ch.unige.events.shared.jaxrs;
import jakarta.ws.rs.NameBinding;
import java.lang.annotation.*;
@NameBinding @Retention(RetentionPolicy.RUNTIME) @Target({ElementType.TYPE, ElementType.METHOD})
public @interface Internal {}
```

`InternalTokenFilter.java` :
```java
package ch.unige.events.shared.jaxrs;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;

@Provider @Internal
public class InternalTokenFilter implements ContainerRequestFilter {
    @Inject @ConfigProperty(name = "app.internal.token") String expected;

    @Override public void filter(ContainerRequestContext ctx) {
        String got = ctx.getHeaderString("X-Internal-Token");
        if (got == null || !got.equals(expected)) {
            throw new NotFoundException();   // 404 envelope via NotFoundExceptionMapper
        }
    }
}
```

`InternalTokenClientFilter.java` (shared-tracing, ClientRequestFilter qui injecte le header sortant) :
```java
package ch.unige.events.shared.tracing;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import jakarta.inject.Inject;

@Provider
public class InternalTokenClientFilter implements ClientRequestFilter {
    @Inject @ConfigProperty(name = "app.internal.token", defaultValue = "")
    String token;

    @Override public void filter(ClientRequestContext ctx) {
        if (token != null && !token.isEmpty()) {
            ctx.getHeaders().putSingle("X-Internal-Token", token);
        }
    }
}
```

Modifier les 3 REST clients dans shared-domain-dtos pour ajouter `@RegisterProvider(InternalTokenClientFilter.class)`.

Annoter les internal resources avec `@Internal` :
```java
// engagement-service/.../UserAttendancesInternalResource.java
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
@Internal     // ← AJOUTER
public class UserAttendancesInternalResource { ... }
```

Faire de même pour tous les internal resources (cf. internal-endpoints.md entries #1-#6).

`application.properties` 4 services :
```properties
app.internal.token=${INTERNAL_TOKEN:dev-internal-token-not-for-prod}
```

Plugin Kong dans `configmap-routes.yaml` (global) :
```yaml
plugins:
  - name: request-transformer
    config:
      remove:
        headers: ["X-Internal-Token"]
```

**Tests** :
- `InternalTokenFilterTest` (shared-jaxrs) : @QuarkusTest avec un endpoint annoté `@Internal`, requêter sans header → 404, avec mauvais header → 404, avec bon header → 200.
- `UserAttendancesInternalResourceTest` (engagement-service) : adapter l'existant pour exiger le header.

**Validation** :
```bash
cd backend && ./mvnw -B -DskipITs verify
grep -E "@Internal" backend/services/*/src/main/java -rln | wc -l   # ≥ 6 (entries #1-#6)
```
**Commit** : `feat(backend): protect internal endpoints with X-Internal-Token filter + Kong strip (Étape 3.2, SEC-002-bis / Décision C)`
**Finding clos** : SEC-002-bis (BLOQUANT).

---

### Étape 4 — Vague 4 IMPORTANTS

#### Étape 4.1 — `read-timeout` REST clients (REST-TIMEOUT-001)

**Pré-requis** : Étape 3 OK.
**Fichiers touchés** : 4 `application.properties`.

**Action** : ajouter pour chaque REST client configuré :
```properties
quarkus.rest-client.event-service.read-timeout=5000
quarkus.rest-client.user-service.read-timeout=5000
quarkus.rest-client.engagement-service.read-timeout=5000
```
(seulement les REST clients réellement utilisés par chaque service consommateur)

**Validation** : `grep "read-timeout" backend/services/*/src/main/resources/application.properties | wc -l` → ≥ 8.
**Commit** : `chore(backend): add read-timeout=5000 on REST clients in 4 consumers (Étape 4.1, REST-TIMEOUT-001)`
**Finding clos** : REST-TIMEOUT-001 (IMPORTANT).

---

#### Étape 4.2 — Vérification KAFKA-PUBLISH-IN-TX (BUG-001/002)

**Pré-requis** : Étape 4.1 OK.
**Action** :
```bash
grep -rn "lifecyclePublisher\|publishedEmitter\|cancelledEmitter\|expiredEmitter" \
  backend/services/event-service/src/main/java --include="*.java" | grep -v "Bridge\|Test"
```
- **Si résultat vide** : tous les emits passent par bridges `@Observes(AFTER_SUCCESS)`. Commit no-op `chore(event): verify Kafka publishers only invoked from bridges (Étape 4.2, BUG-001/002 verified)`.
- **Si appel direct subsiste** dans `EventService.cancel/publish` ou `EventExpirationService.expireEvents` : refactor pour passer par CDI fire `eventCdiEvent.fire(EventLifecycleEvent.cancelled(...))` qui sera observé par le bridge `@Observes(AFTER_SUCCESS)`. Test sentinel : `cancel_rolledBackTransaction_doesNotEmitKafka`.

**Validation** :
```bash
cd backend && ./mvnw -B -pl services/event-service test
```
**Commit** : selon résultat, `chore(event): verify Kafka publishers only invoked from bridges (Étape 4.2, KAFKA-PUBLISH-IN-TX verified)` ou `fix(event): route Kafka publishers through @Observes(AFTER_SUCCESS) bridges (Étape 4.2, BUG-001/002)`.
**Finding clos** : KAFKA-PUBLISH-IN-TX vérif (IMPORTANT).

---

#### Étape 4.3 — Test unitaire admin bypass UserService (ADMIN-BYPASS-TEST)

**Pré-requis** : Étape 4.2 OK.
**Fichiers touchés** : `backend/services/user-service/src/test/java/ch/unige/events/user/service/UserServiceTest.java`.

**Action** : ajouter un `@TestTransaction` test qui appelle directement `userService.getPublicProfile(privateUserId, "admin-auth0", true)` et assert pas de `NotFoundException`. Idem avec `isAdmin=false` qui doit lancer.

**Commit** : `test(user): add unit test for getPublicProfile admin bypass (Étape 4.3, ADMIN-BYPASS-TEST)`
**Finding clos** : ADMIN-BYPASS-TEST (IMPORTANT).

---

#### Étape 4.4 — JavaDoc sur les 4 EventDTO sub-packages (EVENT-DTO-DUPS / Décision E)

**Pré-requis** : Étape 4.3 OK.
**Fichiers touchés** : 4 fichiers `EventDTO.java` dans event-service sous-packages.

**Action** : ajouter la JavaDoc class-level (cf. Décision E) sur chacun des 4 fichiers. Chaque variante doit lister explicitement ses différences vs les autres (présence de `coOrganizerOf`, nullabilité des counts).

**Commit** : `docs(event): add class-level JavaDoc to 4 EventDTO variants documenting intentional duplication (Étape 4.4, EVENT-DTO-DUPS / Décision E)`
**Finding clos** : EVENT-DTO-DUPS (IMPORTANT).

---

#### Étape 4.5 — `TZ=Europe/Zurich` Helm + smoke test (TZ-DRIFT / Décision F)

**Pré-requis** : Étape 4.4 OK.
**Fichiers touchés** :
- 6 deployment.yaml (5 services + web).
- Nouveau : `backend/services/event-service/src/test/java/ch/unige/events/event/EventTzSmokeTest.java`.

**Action** : ajouter `env: TZ=Europe/Zurich` + `JAVA_TOOL_OPTIONS=-Duser.timezone=Europe/Zurich` dans chaque deployment.yaml.

`EventTzSmokeTest.java` :
```java
@QuarkusTest
class EventTzSmokeTest {
    @Test
    void zoneIdSystemDefault_isEuropeZurich() {
        // En CI/test, TZ peut ne pas être posé — ne pas faire échouer si UTC
        // Le test sert de smoke pour les déploiements réels où TZ doit être posé.
        ZoneId tz = ZoneId.systemDefault();
        if (System.getenv("TZ") != null) {
            assertEquals("Europe/Zurich", tz.getId(),
                "Container TZ env var is set but ZoneId.systemDefault() doesn't match — JAVA_TOOL_OPTIONS missing?");
        }
    }
}
```

**Commit** : `chore(infra): pin TZ=Europe/Zurich on all 6 Deployments + add EventTzSmokeTest (Étape 4.5, TZ-DRIFT / Décision F)`
**Finding clos** : TZ-DRIFT (IMPORTANT).

---

#### Étape 4.6 — `ModerationCleanupJob` ADR + max-scale guard (KAFKA-MOD-CLEANUP-IDEM)

**Pré-requis** : Étape 4.5 OK.
**Fichiers touchés** :
- `k8s/chart/values.yaml` : ajouter commentaire « moderation-service replicas MUST stay at 1 (no leader-election) ».
- `k8s/chart/templates/moderation-service/deployment.yaml` : ajouter annotation `unige.events/max-replicas: "1"` + commentaire YAML.
- Nouveau : `backend/docs/adr/ADR-001-moderation-cleanup-replicas-strict.md`.
- Doc update : `backend/docs/devops-handoff.md` ajout d'une note sur la contrainte.

**Action** : créer l'ADR + commenter le YAML.

**Commit** : `docs(infra): ADR-001 moderation cleanup job replicas strict + Helm guard (Étape 4.6, KAFKA-MOD-CLEANUP-IDEM)`
**Finding clos** : KAFKA-MOD-CLEANUP-IDEM (IMPORTANT).

---

#### Étape 4.7 — REPORT-EVENT-FIRE-NOTEST

**Statut** : déjà clos par Étape 1.3 (Décision H — `ModerationDomainSentinelsTest` inclut `@InjectMock Event<EventBannedEvent>` + ArgumentCaptor sur `handle`).

Pas de commit additionnel nécessaire — confirmer dans le récap final que c'est traité.

---

#### Étape 4.8 — `@Schema` sur ApiErrorResponse (API-ERROR-SCHEMA)

**Pré-requis** : Étape 4.7 OK.
**Fichiers touchés** : `backend/services/shared-api-error/src/main/java/ch/unige/events/shared/error/ApiErrorResponse.java`.

**Action** :
```java
@org.eclipse.microprofile.openapi.annotations.media.Schema(
    name = "ApiErrorResponse",
    description = "Canonical error envelope returned by every microservice.")
public record ApiErrorResponse(String error, String message) {}
```

**Commit** : `chore(shared-api-error): add @Schema annotation to ApiErrorResponse for OpenAPI doc (Étape 4.8, API-ERROR-SCHEMA)`
**Finding clos** : API-ERROR-SCHEMA (IMPORTANT).

---

#### Étape 4.9 — Sed batch JavaDoc references services dissous (JAVADOC-DRIFT / Décision I)

**Pré-requis** : Étape 4.8 OK.
**Fichiers touchés** : tous les `.java` sous `backend/services/*/src/{main,test}/`.

**Action** : appliquer les substitutions sed listées en Décision I.

```bash
cd /workspace/backend/services
for f in $(grep -rln "Owned by \(favorite\|view\|share\|stats\|me-aggregator\|co-organizer\|follow\|calendar\|attendance\|comment\|report\)-service" . --include="*.java" -l 2>/dev/null); do
  sed -i \
    -e 's|Owned by favorite-service|Owned by event-service (co-located post-finalization)|g' \
    -e 's|Owned by view-service|Owned by event-service (co-located post-finalization)|g' \
    -e 's|Owned by share-service|Owned by event-service (co-located post-finalization)|g' \
    -e 's|Owned by stats-service|Owned by event-service (co-located post-finalization)|g' \
    -e 's|Owned by me-aggregator-service|Owned by event-service (co-located post-finalization)|g' \
    -e 's|Owned by co-organizer-service|Owned by event-service (co-located post-finalization)|g' \
    -e 's|Owned by follow-service|Owned by user-service (co-located post-finalization)|g' \
    -e 's|Owned by calendar-service|Owned by user-service (co-located post-finalization)|g' \
    -e 's|Owned by attendance-service|Owned by engagement-service (renamed post-finalization)|g' \
    -e 's|Owned by comment-service|Owned by engagement-service (co-located post-finalization)|g' \
    -e 's|Owned by report-service|Owned by moderation-service (renamed post-finalization)|g' \
    "$f"
done
```

Puis revue manuelle des autres mentions narratives (« replaced by REST client at PR 12/13 », « will become a REST call to co-organizer-service ») — corriger une par une.

**Validation** :
```bash
grep -rln "favorite-service\|view-service\|share-service\|stats-service\|me-aggregator-service\|co-organizer-service\|follow-service\|calendar-service\|attendance-service\|comment-service\|report-service" \
  backend/services --include="*.java" | grep -v target | xargs grep -L "co-located\|renamed\|replaced by REST\|absorbed" 2>/dev/null
# → 0 résultat (tous les matches restants ont la mention explicite « co-located/renamed »)
```
**Commit** : `docs(backend): align JavaDoc references with 5-service post-finalization topology (Étape 4.9, JAVADOC-DRIFT / Décision I)`
**Finding clos** : JAVADOC-DRIFT (IMPORTANT).

---

#### Étape 4.10 — Refonte `architecture.md` flux cross-service (ARCHITECTURE-FLUX-DRIFT / Décision I)

**Pré-requis** : Étape 4.9 OK.
**Fichiers touchés** : `backend/docs/architecture.md` lignes 200-204.

**Action** : reformuler l'exemple `POST /api/events/{id}/comments` :
```markdown
Exemple cross-service : `POST /api/events/{id}/comments`

1. Kong route → `engagement-service:8080` (comment-service absorbé en Étape 2.4.1).
2. `engagement-service.CommentResource.create()` → `CommentService.post()` (`@Transactional`).
3. `CommentService` appelle `eventServiceClient.getByIdWithCoOrgCheck(id, callerUuid)` — single REST hop qui retourne `EventDTO` enrichi de `coOrganizerOf:bool` (cascade SCRUM-136 locale dans event-service post-2.2.4).
4. La nouvelle entité Comment est persistée localement (`Comment.eventId` est un `@Column Long`, pas un `@ManyToOne` cross-service).
5. `commentEvent.fire(CommentCreatedEvent(...))` posté en transaction. Après commit JDBC, le bridge `CommentCreatedKafkaBridge` (`@Observes(during=AFTER_SUCCESS)`) invoque l'`Emitter` qui envoie un message `comments.created` (clé partition = `eventId`).
6. Réponse `201 Created` au client.
```

**Commit** : `docs(backend): refactor architecture.md cross-service flow example to 5-service reality (Étape 4.10, ARCHITECTURE-FLUX-DRIFT / Décision I)`
**Finding clos** : ARCHITECTURE-FLUX-DRIFT (IMPORTANT).

---

#### Étape 4.11 — Refonte `sprint-context.md` table « 13 services » (SPRINT-CONTEXT-DRIFT / Décision I)

**Pré-requis** : Étape 4.10 OK.
**Fichiers touchés** : `backend/docs/sprint-context.md` lignes 892-909.

**Action** : remplacer la table « Écarts vs spec » par une version 5-services. Sed pour les autres mentions « 13 services » résiduelles dans le fichier.

**Commit** : `docs(backend): align sprint-context.md table with 4 active services + 1 placeholder topology (Étape 4.11, SPRINT-CONTEXT-DRIFT / Décision I)`
**Finding clos** : SPRINT-CONTEXT-DRIFT (IMPORTANT).

---

#### Étape 4.12 — `livenessProbe` web Deployment (WEB-DEPLOY-PROBES)

**Pré-requis** : Étape 4.11 OK.
**Fichiers touchés** : `k8s/chart/templates/web/deployment.yaml`.

**Action** : ajouter un bloc `livenessProbe` :
```yaml
livenessProbe:
  httpGet:
    path: /
    port: 80
    scheme: HTTP
  initialDelaySeconds: 30
  periodSeconds: 30
  failureThreshold: 3
```

**Commit** : `chore(infra): add livenessProbe to web Deployment for parity with 5 backend services (Étape 4.12, WEB-DEPLOY-PROBES)`
**Finding clos** : WEB-DEPLOY-PROBES (IMPORTANT).

---

#### Étape 4.13 — `PodDisruptionBudget` Kong (KONG-PDB-PREVIEW)

**Pré-requis** : Étape 4.12 OK.
**Fichiers touchés** : nouveau `k8s/chart/templates/kong/poddisruptionbudget.yaml` (gated par `{{- if gt (int .Values.kong.replicas) 1 }}` pour ne pas créer en preview).

**Action** :
```yaml
{{- if gt (int .Values.kong.replicas) 1 }}
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: kong
  namespace: {{ .Release.Namespace }}
spec:
  minAvailable: 1
  selector:
    matchLabels:
      app: kong
{{- end }}
```

**Commit** : `chore(infra): add PodDisruptionBudget for Kong (gated to prod replicas≥2) (Étape 4.13, KONG-PDB-PREVIEW)`
**Finding clos** : KONG-PDB-PREVIEW (IMPORTANT).

---

### Étape 5 — Vague 5 MINEURS (regroupés en 4 commits batch)

#### Étape 5.1 — Cleanup JavaDoc + roadmap notes (MINOR-001 + MINOR-002)

**Action** :
- `engagement-service/.../AttendanceService.java` lignes 35-40 : reformuler le JavaDoc pour retirer la mention « will become a REST call to co-organizer-service » (le service est dissous, la cascade est déjà via REST client).
- `backend/docs/microservices-migration-roadmap.md` table extractions : ajouter note d'archivage sous la table.

**Commit** : `docs(backend): cleanup obsolete JavaDoc references + archive roadmap table (Étape 5.1, MINOR-001 + MINOR-002)`

---

#### Étape 5.2 — Supprimer scaffolds redondants (MINOR-003)

**Action** :
```bash
# Vérifier présence
ls backend/contract-tests/src/test/java/.../ContractTestsScaffoldTest.java 2>/dev/null
ls backend/e2e/src/test/java/.../E2EScaffoldTest.java 2>/dev/null
# Si présents → git rm
```

**Commit** : `test(backend): remove redundant ContractTestsScaffoldTest + E2EScaffoldTest (Étape 5.2, MINOR-003)`

---

#### Étape 5.3 — ParamConverters enums dans shared-jaxrs (MINOR-004)

**Action** : créer `EventStatusParamConverter`, `AttendanceStatusParamConverter`, `EventCategoryParamConverter` dans shared-jaxrs (pattern identique à `TimeframeParamConverter` existant). Couvrir avec tests dédiés (≥ 95% L cible shared lib).

**Commit** : `feat(shared-jaxrs): add EventStatus/AttendanceStatus/EventCategory ParamConverters with tests (Étape 5.3, MINOR-004)`

---

#### Étape 5.4 — Aligner `devops-handoff.md` + déprécier `aggregate-coverage.sh` (MINOR-005, 006, 007)

**Action** :
- `backend/docs/devops-handoff.md` : conserver UNIQUEMENT 7 items machine PINFO (cluster Kafka prod, certs/DNS, Doppler, SonarCloud quality gate, NetworkPolicies optionnelles, GHCR cleanup, Pact provider verification harness). Tout le reste est livré.
- `backend/scripts/aggregate-coverage.sh` : ajouter en tête un commentaire `# DEPRECATED: aggregate jacoco scan is now done by sonar-aggregate CI job (Option B). This script remains for local debugging only.`

**Commit** : `docs(backend): finalize devops-handoff.md to 7 PINFO items only + deprecate aggregate-coverage.sh (Étape 5.4, MINOR-005 + 006 + 007)`

---

#### Étape 5.5 — JavaDoc S3 cleanup hors-tx + data-model.md (MINOR-010 + MINOR-011)

**Action** :
- `user-service/.../UserService.java` (méthodes `uploadImage`/`uploadBanner`) : ajouter JavaDoc note « Old S3 object is deleted before JPA flush ; orphaned object possible if flush fails (known limitation, cf. ADR-002). »
- `backend/docs/data-model.md` : aligner la mention « V1__create_users.sql » sur le nouveau path post-1.1 (`backend/services/user-service/src/main/resources/db/migration/V1__create_users.sql`).
- Optionnel : ADR-002 sur la limitation S3 cleanup.

**Commit** : `docs(backend): document S3 cleanup limitation + align data-model.md migration paths (Étape 5.5, MINOR-010 + MINOR-011)`

---

### Étape 9 — Finale (clôture)

#### Étape 9.1 — `sprint-context.md` § Étape 23 récap final

**Pré-requis** : Étapes 1-5 OK.
**Action** : ajouter une section récap dans `backend/docs/sprint-context.md` qui liste les vagues 1-5 livrées, mappe chaque finding à sa résolution, et déclare l'objectif PR atteint.

**Commit** : `docs(backend): add sprint-context.md § Étape 23 — total finalization closure (Étape 9.1)`

---

#### Étape 9.2 — `devops-handoff.md` reduction aux 7 items PINFO

**Pré-requis** : Étape 9.1 OK.
**Action** : refondre intégralement `backend/docs/devops-handoff.md` pour ne garder que :
1. Cluster Kafka prod-grade (multi-broker, replication factor ≥ 3)
2. Certificats TLS / DNS production
3. Doppler secrets (DB credentials, OIDC, S3, INTERNAL_TOKEN, etc.)
4. SonarCloud quality gate (problème « new code » sur code migré — devops doit configurer une exception)
5. NetworkPolicies Kubernetes en complément de SEC-002-bis (header X-Internal-Token est livré ici, NetworkPolicy est defense-in-depth recommandée)
6. Cleanup GHCR PR-tagged images (CI cleanup job)
7. Pact provider verification job harness (provider states)

Tout ce qui n'est pas dans cette liste doit avoir été livré par cette spec.

**Commit** : `docs(backend): reduce devops-handoff.md to 7 PINFO machine items only (Étape 9.2)`

---

#### Étape 9.3 — PR body addendum

**Pré-requis** : Étape 9.2 OK.
**Action** : `gh pr edit 158 --body-file <(echo "...")` pour ajouter une section « Post-audit-final closure » qui résume les 35 findings résolus + lien vers la spec.

**Commit** : aucun (modification GitHub).

---

#### Étape 9.4 — Validation finale (checklist exhaustive)

**Pré-requis** : Étapes 9.1-9.3 OK.
**Action** : exécuter et confirmer :

```bash
# Stubs
test "$(find /workspace/backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l)" -eq 0 && echo "OK: 0 stubs" || echo "FAIL stubs"

# Migrations
test "$(find /workspace/backend -name 'V*__*.sql' -not -path '*/target/*' | wc -l)" -ge 17 && echo "OK: ≥17 migrations" || echo "FAIL migrations"

# Local ApiErrorResponse
test "$(find /workspace/backend/services -name 'ApiErrorResponse.java' -not -path '*/target/*' -not -path '*/shared-*/*' | wc -l)" -eq 0 && echo "OK: 0 local ApiErrorResponse" || echo "FAIL"

# Sentinels 4/4 services
for svc in event user engagement moderation; do
  ls backend/services/${svc}-service/src/test/java/ch/unige/events/${svc%-service}/sentinels/*Sentinels*Test.java 2>/dev/null \
   || ls backend/services/${svc}-service/src/test/java/ch/unige/events/report/sentinels/*Sentinels*Test.java 2>/dev/null
done
# 4 résultats attendus

# JavaDoc references services dissous
test "$(grep -rln 'comment-service\|co-organizer-service\|attendance-service\|favorite-service\|share-service\|view-service\|stats-service\|me-aggregator-service\|follow-service\|calendar-service\|report-service' /workspace/backend --include='*.java' --include='*.md' 2>/dev/null | grep -v target | grep -v specs_archives | xargs grep -L 'co-located\|renamed\|absorbed\|replaced by REST' 2>/dev/null | wc -l)" -eq 0 && echo "OK: doc/javadoc clean" || echo "FAIL doc"

# MdcKafkaInterceptor
test -f /workspace/backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaProducerInterceptor.java && echo "OK: MdcKafka producer present"
test -f /workspace/backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaConsumerInterceptor.java && echo "OK: MdcKafka consumer present"

# InternalTokenFilter
test -f /workspace/backend/services/shared-jaxrs/src/main/java/ch/unige/events/shared/jaxrs/InternalTokenFilter.java && echo "OK: InternalTokenFilter present"

# Frontend / OpenAPI invariants
test "$(git diff --shortstat origin/main HEAD -- frontend/ | wc -l)" -eq 0 && echo "OK: frontend invariant" || echo "FAIL frontend"
test "$(git diff --shortstat origin/main HEAD -- openapi/ | wc -l)" -eq 0 && echo "OK: openapi invariant" || echo "FAIL openapi"

# Build
cd backend && ./mvnw -B -DskipITs verify && echo "OK: build SUCCESS" || echo "FAIL build"

# CI
gh pr checks 158 | grep -E "fail|error" | grep -v -i sonar
# 0 résultat attendu (sauf SonarCloud)
```

Tous les checks doivent retourner OK (sauf SonarCloud quality gate qui reste devops PINFO).

**Commit** : aucun (validation pure).

---

## Critères de complétion par vague

| Vague | Critère de complétion |
|---|---|
| Vague 1 | `find -name 'V*__*.sql' \| wc -l` ≥ 17 ; `api-contract.md` ne mentionne plus de service dissous ; `ModerationDomainSentinelsTest.java` exists + 8 @Test |
| Vague 2 | `pg_advisory_xact_lock` présent dans AttendanceService ; FavoriteService.addFavorite utilise ON CONFLICT ; EventService.delete purge EventCoOrganizer ; cascade SCRUM-136 utilise REST client ; FavoriteStub absent |
| Vague 3 | `MdcKafkaProducer/ConsumerInterceptor.java` présents + tests ; `InternalTokenFilter.java` présent + `@Internal` annotation présente sur tous les internal resources ; Kong strip `X-Internal-Token` |
| Vague 4 | `read-timeout=5000` configuré ; KAFKA-PUBLISH-IN-TX vérifié ; admin bypass test présent ; 4 EventDTO ont JavaDoc ; TZ=Europe/Zurich dans 6 deployments ; ADR-001 présent ; @Schema sur ApiErrorResponse ; 0 mention service dissous (hors notes explicites) ; livenessProbe web ; PDB Kong |
| Vague 5 | Scaffolds redondants supprimés ; ParamConverters enums dans shared-jaxrs ; devops-handoff réduit à 7 items PINFO |
| Étape 9 | sprint-context § Étape 23 + devops-handoff finalisé + PR body MAJ + checklist 9.4 toute verte |

---

## Annexes

### Annexe A — Tableau croisé exhaustif finding → étape

| Finding | Sévérité | Étape clôturante | Décision pivot |
|---|---|---|---|
| MIGRATIONS-001 | BLOQUANT | 1.1 | A |
| BUG-005-bis | BLOQUANT | 2.1 | B |
| BUG-006-bis | BLOQUANT | 2.2 | — |
| KAFKA-002 | BLOQUANT | 3.1 | D |
| SEC-002-bis | BLOQUANT | 3.2 | C |
| EVENT-DELETE-001 | BLOQUANT | 2.3 | — |
| MODERATION-SENTINELS-001 | BLOQUANT | 1.3 | H |
| CASCADE-136-DRIFT | BLOQUANT | 2.4 | G |
| API-CONTRACT-001 | BLOQUANT | 1.2 | I |
| FAVORITE-STUB-REDUNDANT | IMPORTANT | 2.5 | — |
| REST-TIMEOUT-001 | IMPORTANT | 4.1 | — |
| KAFKA-PUBLISH-IN-TX vérif | IMPORTANT | 4.2 | — |
| ADMIN-BYPASS-TEST | IMPORTANT | 4.3 | — |
| EVENT-DTO-DUPS | IMPORTANT | 4.4 | E |
| TZ-DRIFT | IMPORTANT | 4.5 | F |
| KAFKA-MOD-CLEANUP-IDEM | IMPORTANT | 4.6 | — |
| REPORT-EVENT-FIRE-NOTEST | IMPORTANT | 1.3 (couplé H) | H |
| API-ERROR-SCHEMA | IMPORTANT | 4.8 | — |
| JAVADOC-DRIFT | IMPORTANT | 4.9 | I |
| ARCHITECTURE-FLUX-DRIFT | IMPORTANT | 4.10 | I |
| SPRINT-CONTEXT-DRIFT | IMPORTANT | 4.11 | I |
| WEB-DEPLOY-PROBES | IMPORTANT | 4.12 | — |
| KONG-PDB-PREVIEW | IMPORTANT | 4.13 | — |
| MINOR-001 (TODO obsolète AttendanceService) | MINEUR | 5.1 | — |
| MINOR-002 (table roadmap pré-consolidation) | MINEUR | 5.1 | — |
| MINOR-003 (scaffolds redondants) | MINEUR | 5.2 | — |
| MINOR-004 (ParamConverters enums) | MINEUR | 5.3 | — |
| MINOR-005 (CI image push PR) | MINEUR | 5.4 (devops-handoff) | — |
| MINOR-006 (Pact provider verification) | MINEUR | 5.4 (devops-handoff) | — |
| MINOR-007 (aggregate-coverage.sh) | MINEUR | 5.4 | — |
| MINOR-008 (commits sans réf) | MINEUR | non-actionnable rétroactif | — |
| MINOR-009 (commits scope mélangés) | MINEUR | non-actionnable rétroactif | — |
| MINOR-010 (data-model.md V*.sql) | MINEUR | 5.5 (paths corrects post-1.1) | A |
| MINOR-011 (S3 cleanup hors-tx) | MINEUR | 5.5 (JavaDoc note) | — |
| MINOR-012 (frontend searchApi.ts) | MINEUR | hors scope (frontend invariant) | — |

**35 findings adressés** (3 non-actionnables : MINOR-008, MINOR-009 process-only, et MINOR-012 frontend invariant).

### Annexe B — Mapping V*.sql → service propriétaire (Décision A)

| Migration | Table(s) | Service propriétaire | Path destination |
|---|---|---|---|
| V1 | users | user-service | user-service/src/main/resources/db/migration/V1__create_users.sql |
| V2 | events | event-service | event-service/src/main/resources/db/migration/V2__create_events.sql |
| V3 | attendances | engagement-service | engagement-service/src/main/resources/db/migration/V3__create_attendances.sql |
| V4 | favorites | event-service | event-service/.../V4__create_favorites.sql |
| V5 | event_views | event-service | event-service/.../V5__create_event_views.sql |
| V6 | reports | moderation-service | moderation-service/.../V6__create_reports.sql |
| V7 | events + attendances CHECK | event-service | event-service/.../V7__reconcile_check_constraints.sql |
| V8 | event_co_organizers | event-service | event-service/.../V8__create_event_co_organizers.sql |
| V9 | events.description widening | event-service | event-service/.../V9__widen_event_description.sql |
| V10 | reports enrichment | moderation-service | moderation-service/.../V10__add_report_reason_and_review_fields.sql |
| V11 | events CHECK (EXPIRED) | event-service | event-service/.../V11__allow_event_status_expired.sql |
| V12 | events.featured | event-service | event-service/.../V12__add_featured_to_events.sql |
| V13 | events CHECK (BANNED) | event-service | event-service/.../V13__allow_event_status_banned.sql |
| V14 | follows | user-service | user-service/.../V14__create_follows.sql |
| V15 | comments | engagement-service | engagement-service/.../V15__create_comments.sql |
| V16 | comments parent FK | engagement-service | engagement-service/.../V16__alter_comments_parent_fk_set_null.sql |
| V17 | events recurrence | event-service | event-service/.../V17__add_event_recurrence.sql |

**Distribution finale** :
- user-service : 2 migrations (V1, V14)
- event-service : 11 migrations (V2, V4, V5, V7, V8, V9, V11, V12, V13, V17 + V7 split)
- engagement-service : 3 migrations (V3, V15, V16)
- moderation-service : 2 migrations (V6, V10)
- **Total : 18 fichiers** (V7 reste dans event-service car il modifie principalement events ; les CHECKs cross-table sur attendances restent valides tant que public partagé).

### Annexe C — Inventaire des nouveaux fichiers Java créés

| Fichier | Module | Étape |
|---|---|---|
| `MdcKafkaProducerInterceptor.java` | shared-tracing | 3.1 |
| `MdcKafkaConsumerInterceptor.java` | shared-tracing | 3.1 |
| `MdcKafkaProducerInterceptorTest.java` | shared-tracing | 3.1 |
| `MdcKafkaConsumerInterceptorTest.java` | shared-tracing | 3.1 |
| `InternalTokenClientFilter.java` | shared-tracing | 3.2 |
| `Internal.java` (annotation) | shared-jaxrs | 3.2 |
| `InternalTokenFilter.java` | shared-jaxrs | 3.2 |
| `InternalTokenFilterTest.java` | shared-jaxrs | 3.2 |
| `ModerationDomainSentinelsTest.java` | moderation-service | 1.3 |
| `EventTzSmokeTest.java` | event-service | 4.5 |
| `EventBannedConsumerTracingTest.java` | event-service | 3.1 |
| `EventStatusParamConverter.java` + test | shared-jaxrs | 5.3 |
| `AttendanceStatusParamConverter.java` + test | shared-jaxrs | 5.3 |
| `EventCategoryParamConverter.java` + test | shared-jaxrs | 5.3 |
| `ADR-001-moderation-cleanup-replicas-strict.md` | docs | 4.6 |
| `ADR-002-s3-cleanup-orphan-tolerance.md` (optionnel) | docs | 5.5 |

### Annexe D — Commits estimés

| Vague | Commits | Effort |
|---|---|---|
| Étape 0 | 0 | XS |
| Étape 1 | 3 | M (1.1 = ~3-5h, 1.2 = ~1h, 1.3 = ~3-4h) |
| Étape 2 | 5 | S (chacun ~30 min - 2h, sauf 2.4 ~1-2h) |
| Étape 3 | 2 | M (3.1 = ~3-4h, 3.2 = ~2-3h) |
| Étape 4 | 12 (4.1-4.13 sauf 4.7 résolu par 1.3) | M (essentiellement XS-S chacun) |
| Étape 5 | 5 (5.1-5.5) | S |
| Étape 9 | 3 (9.1-9.3) + 1 validation | XS |
| **Total** | **~30 commits** | **~3-4 jours focused (~24-32h)** |

### Annexe E — Décisions cross-référencées par finding

| Décision | Findings adressés | Risque |
|---|---|---|
| A — Schéma `public` partagé + Flyway redistribué | MIGRATIONS-001, MINOR-010 | **HAUT** : touche à la persistance, casse-deal pour fresh deploys — à valider absolument en preview env post-1.1 |
| B — pg_advisory_xact_lock | BUG-005-bis | MOYEN : test concurrent obligatoire pour valider |
| C — Header X-Internal-Token | SEC-002-bis | FAIBLE : pattern standard, reversible |
| D — MdcKafkaInterceptor | KAFKA-002 | FAIBLE : pattern Kafka standard |
| E — JavaDoc EventDTO sub-packages | EVENT-DTO-DUPS | NUL : doc only |
| F — TZ Helm fix | TZ-DRIFT | FAIBLE : env var, validé par smoke test |
| G — Cascade SCRUM-136 vérification | CASCADE-136-DRIFT | FAIBLE-MOYEN : conditionnel selon état actuel |
| H — ModerationDomainSentinelsTest 8 méthodes | MODERATION-SENTINELS-001, REPORT-EVENT-FIRE-NOTEST | NUL : tests-only |
| I — Doc cleanup multi-couches | API-CONTRACT-001, JAVADOC-DRIFT, ARCHITECTURE-FLUX-DRIFT, SPRINT-CONTEXT-DRIFT | NUL : doc only |

**Décision la plus risquée** : **A (Flyway redistribué)** — c'est la seule qui touche à l'état persistant. Une mauvaise distribution des V*.sql ou un fail Flyway au démarrage casse tous les services. À valider absolument :
1. En local : `./mvnw -B -DskipITs verify` SUCCESS post-1.1.
2. En preview env : un déploiement fresh complet doit bootstrapper le schéma sans intervention manuelle.

---

## Récap final (à reproduire en réponse oraleau bout du turn de génération de la spec)

- **Nombre total d'étapes / sous-étapes** : 9 phases (Étapes 0, 1, 2, 3, 4, 5, 9 — pas de 6/7/8 dans cette spec) avec **30 sous-étapes commit-grade** (1 + 3 + 5 + 2 + 12 + 5 + 4 = 32 si on compte chaque petit pas, mais ~30 commits réels selon Annexe D).
- **Estimation effort agrégé** : **~24-32 heures focused** (~3-4 jours). La Vague 1 (~7-9h) et la Vague 4 (~6-8h) dominent.
- **Décision la plus risquée** : **Décision A (Flyway redistribué)** — touche à la persistance, à valider en preview env. Les 8 autres décisions sont code/test/doc only avec risque faible.
- **Smoke-test pour démarrage exécuteur** :
  ```bash
  cd /workspace/backend && ./mvnw -B -DskipITs verify
  ```
  Doit retourner SUCCESS sur 17 modules. Si échec → diagnostiquer avant de démarrer la Vague 1.
