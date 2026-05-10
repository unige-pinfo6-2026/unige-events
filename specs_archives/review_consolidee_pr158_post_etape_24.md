# Review consolidée multi-agent — PR #158 post-Étape 24

> **Date** : 2026-05-10
> **HEAD reviewé** : `e4b3817c` (clôture Étape 24 — total fix pré-merge)
> **Branche** : `refactor(backend)--migrate-to-microservices`
> **PR** : [#158](https://github.com/unige-pinfo6-2026/unige-events/pull/158)
> **Scope review** : 243 commits depuis `origin/main`, 17 modules Maven, ~180 fichiers cumulés modifiés
> **5 sous-agents `pr-review-toolkit`** : `code-reviewer` (CR), `silent-failure-hunter` (SFH), `type-design-analyzer` (TDA), `pr-test-analyzer` (PTA), `comment-analyzer` (CA)
> **Verdict** : ✅ PR #158 mergeable telle quelle — Étape 25 follow-up recommandée mais NON bloquante

---

## Préambule

Cette review consolidée est produite après la clôture de l'Étape 24 (54 commits, 56/56 items review consolidée multi-agent précédente adressés, 35/35 findings audit final Étape 23 toujours valides). Elle vise à confirmer que la PR #158 est mergeable et que la transition vers le DevOps PINFO peut s'opérer (cf. [`backend/docs/devops-handoff.md`](../backend/docs/devops-handoff.md) — 7 items machine PINFO restants).

Les Décisions A-K sont **non-revisitables** dans ce cadre :
- A schéma `public` partagé + Flyway redistribué par owner
- B `pg_advisory_xact_lock(eventId)` capacity gating
- C header `X-Internal-Token` (SEC-002-bis)
- D `MdcKafkaInterceptor` shared-tracing (KAFKA-002)
- E 4 EventDTO sub-package variants intentionnels
- F `TZ=Europe/Zurich` Helm
- G CASCADE-136-DRIFT verified no-op
- H `ModerationDomainSentinelsTest` 8 sentinels
- I doc cleanup
- J `/events/{id}/organizer-uuids` reste `@PermitAll` ([ADR-002](../backend/docs/adr/ADR-002-organizer-uuids-permitall.md))
- K `events.banned` outbox transactionnel + 4 autres best-effort ([ADR-003](../backend/docs/adr/ADR-003-event-banned-outbox-vs-best-effort.md))

---

## 1. Tableau récap par sévérité

### BLOQUANT (0 selon CR/TDA/CA, 3 selon SFH avec sévérité observabilité)

| # | Source | Fichier | Description courte |
|---|---|---|---|
| B1 | SFH | [EventBannedOutboxPoller.java:55-72](../backend/services/moderation-service/src/main/java/ch/unige/events/report/outbox/EventBannedOutboxPoller.java#L55) | Poison pill (JsonProcessingException) non détecté → row rejouée toutes les 10 s à perpétuité, logs `[KAFKA_OUTBOX_PUBLISH_FAIL]` saturent |
| B2 | SFH | [MdcKafkaProducerInterceptor.java:52-64](../backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaProducerInterceptor.java#L52) | `onAcknowledgement` failure log en `WARN` au lieu d'`ERROR` + format errorId non aligné ADR-003 (`[KAFKA_PRODUCE_FAIL]` vs `[KAFKA_PUBLISH_FAIL_<topic>]`) |
| B3 | SFH | [EventBannedPublisher.java:34-50](../backend/services/moderation-service/src/main/java/ch/unige/events/report/kafka/EventBannedPublisher.java#L34) | Rollback admin si serialize JSON échoue mais aucun log structuré avant le re-throw |

> **Note arbitrale** : CR/TDA/CA ne classent rien BLOQUANT. SFH cible des défauts d'**observabilité ADR-003** (logs WARN au lieu d'ERROR/errorId). Sérieux mais pas data-corruption — la fonction métier marche. Le pipeline `events.banned` (sécurité fonctionnelle) reste durable via outbox.

### IMPORTANT (15)

| # | Source | Fichier | Description |
|---|---|---|---|
| I-CR-1 | CR | [EventBannedOutboxPoller.java:46-73](../backend/services/moderation-service/src/main/java/ch/unige/events/report/outbox/EventBannedOutboxPoller.java#L46) | `@Transactional` + `emitter.send().get(5s)` × `BATCH_SIZE=100` = jusqu'à **500 s** TX ouverte → connection pool pinned |
| I-CR-2 | CR + TDA | [User.java:44](../backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java#L44) + [V1__create_users.sql:8](../backend/services/user-service/src/main/resources/db/migration/V1__create_users.sql#L8) | `Faculty` enum `@Enumerated(STRING)` mais colonne `VARCHAR(255)` sans `CHECK` constraint → crash `Enum.valueOf` à l'hydration si valeur ad-hoc en DB |
| I-CR-3 | CR | [EventCoOrganizerService.java:220](../backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerService.java#L220) | `ServiceUnavailableException` lance string nue, pas d'envelope `ApiErrorResponse` (incohérent vs reste du contrat) |
| I-CR-4 + I-PTA-1 | CR + PTA | [UserService.java:236-243](../backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java#L236) + UserServiceTest | **D20 sans test direct** — `optimisticLockConflict()` + `isOptimisticLockConflict()` (commit `940b8318`) ajoutés sans aucun test → régression silencieuse possible |
| I-SFH-1 | SFH | [EventBannedConsumer.java:25-32](../backend/services/event-service/src/main/java/ch/unige/events/event/kafka/EventBannedConsumer.java#L25) | `[MOD_BAN_LOST]` en `WARN` masque potentielle divergence DB / replica drift / mauvais DB pointé |
| I-SFH-2 | SFH | [EventLifecyclePublisher.java:57-68](../backend/services/event-service/src/main/java/ch/unige/events/event/kafka/EventLifecyclePublisher.java#L57) (+ 3 jumeaux Follow/CoOrganizer/CommentCreated) | `try { emitter.send }` couvre seulement la phase synchrone — vrai cas Kafka down dans callback async non logué ici |
| I-SFH-3 | SFH | [EventServiceClient.java:55-115](../backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/client/EventServiceClient.java#L55) (+ 2 autres) | 8 fallback methods loguent `WARN` au lieu d'`ERROR` + errorId. Fallback `getOrganizerUuids→List.of()` peut faire échouer un delete d'organisateur en 403 silencieux |
| I-SFH-4 | SFH | [EventService.java:430-462](../backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java#L430) | `delete()` purge Favorite + EventView + EventCoOrganizer mais **pas l'objet S3** `event.bannerUrl` → orphelins indéfinis |
| I-SFH-5 | SFH | [MdcKafkaConsumerInterceptor.java:31-44](../backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaConsumerInterceptor.java#L31) | Pas de sanity-check sur header MDC (longueur, regex UUID) → `�` peut polluer MDC |
| I-SFH-6 | SFH | [EventCoOrganizerService.java:212-226](../backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerService.java#L212) | Le 503 (D19, user-service down) loggué en `WARN` au lieu d'`ERROR` + errorId |
| I-SFH-7 | SFH | [CommentService.java:224-243](../backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java#L224) | Fallback `null` du REST client → 404 « event introuvable » alors qu'event-service est down |
| I-SFH-8 | SFH | [AttendanceService.java:191-209](../backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java#L191) | `getOrganizerUuids` fallback `List.of()` → créateur légitime reçoit 403 sans signal opérateur |
| I-TDA-1 | TDA | [EventBannedOutbox.java:28-46](../backend/services/moderation-service/src/main/java/ch/unige/events/report/outbox/EventBannedOutbox.java#L28) | Champs `public` mutables sans state-machine (`markPublished`/`recordFailure`) ni `@Column(nullable=false)` côté Java → drift Hibernate `drop-and-create` `%test` vs Flyway prod |
| I-CA-1 | CA | [ingress.yaml:18-21](../k8s/chart/templates/ingress/ingress.yaml#L18) | Doc rot : « 13 owning microservices » faux post-consolidation 14→5 |
| I-CA-2 | CA | [engagement-service/deployment.yaml:10](../k8s/chart/templates/engagement-service/deployment.yaml#L10) | Doc rot : « PR 8 of the extraction roadmap » sémantique pré-consolidation |
| I-CA-3 | CA | [UserService.java:30-35](../backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java#L30) | JavaDoc class : « will become a REST call to user-service » contradictoire (cette classe **EST** user-service) |

### MINEUR (~15)

Quick list (file:line + source) — détails dans rapports agents :

- **CR** : M-CR-5 partial index `event_banned_outbox` non ciblé · M-CR-6 `ReportService.handle` retourne EventDTO pré-BAN · M-CR-7 `EnumParamConverterProvider` priority sans test croisé · M-CR-8 bridge phase `IN_PROGRESS` non explicite · M-CR-9 colonne `faculty VARCHAR(255)` overprovisioned · M-CR-10 outbox sans cap `attempts` ni DLQ · M-CR-11 Helm guard precedence `int|default`
- **SFH** : M-SFH-1..6 (FavoriteService bulk fallback non tracé, `safeGetUser` triplé entre 4 services, S3 cleanup limitations, `tryDeleteObject` blanket catch, `FileStorageService.init` Minio down boot, `Auth0IdResolver` malformed UUID en WARN)
- **CA** : M-CA-4 notification-service deployment « follow-up extraction PR » → SCRUM-99 · M-CA-5 kong configmap-routes:373 lignes périmées · M-CA-6 [data-model.md:763](../backend/docs/data-model.md#L763) réfère à JavaDoc inline absente

### NIT (~10)

Détails dans rapports agents (CR-12..15, SFH-N1..N4, CA-7..8). Cosmétique, sans impact merge.

---

## 2. Findings recoupés (≥ 2 sous-agents → priorité haute)

| Thème | Agents | Items recoupés |
|---|---|---|
| **OptimisticLock 409 (D20) sans test** | CR + PTA | I-CR-4 + I-PTA-1 |
| **`User.faculty` enum sans CHECK + drift doc** | CR + TDA + CA | I-CR-2 + TDA-IMP2 + TDA-IMP3 + M-CA-6 |
| **Pipeline outbox `EventBannedOutbox*` fragile** (transaction longue + poison pill + state-machine + branches non testées) | CR + SFH + TDA + PTA | I-CR-1 + B1 + I-TDA-1 + PTA-gap-modéré |
| **Logs Kafka best-effort en WARN au lieu d'ERROR+errorId ADR-003** | SFH | B2 + I-SFH-2 + I-SFH-3 + I-SFH-6 |
| **REST client fallbacks dégradent silencieusement autorisation/cascade** | SFH | I-SFH-3 + I-SFH-7 + I-SFH-8 |

---

## 3. Top 5 actionables (Étape 25 — pas avant merge)

| # | Action | Effort | Impact | Items couverts |
|---|---|---|---|---|
| **1** | **Ajouter 3 tests D20** : `updateMyProfile_concurrentVersionMismatch_throws409`, `_persistenceExceptionWrappingOptimisticLock_throws409`, `isOptimisticLockConflict_StaleStateInChain_returnsTrue` (réflexion analogue A15) | ~30 min | **élevé** — pin un invariant load-bearing actuellement non couvert | I-CR-4 + I-PTA-1 |
| **2** | **Migration `V19__users_faculty_check.sql`** + alignement [data-model.md:646-679](../backend/docs/data-model.md#L646) sur la nomenclature enum `Faculty` actuelle (LETTERS/LAW/MEDICINE/SOCIAL_SCIENCES…) | ~45 min | **élevé** — prévient crash `Enum.valueOf` à l'hydration sur DB pré-existante | I-CR-2 + TDA-IMP2/3 |
| **3** | **Cleanup 3 doc-rot Helm + UserService class-JavaDoc** : `ingress.yaml:20` « 5 microservices » au lieu de « 13 » ; `engagement-service/deployment.yaml:10` réf Étape 2.X ; suppression phrase morte `UserService.java:30-35` | ~15 min | moyen — cohérence doc post-consolidation 14→5 | I-CA-1/2/3 |
| **4** | **Élever logs Kafka publish best-effort à ERROR + errorId aligné ADR-003** (`[KAFKA_PUBLISH_FAIL_<topic>]`) dans `MdcKafkaProducerInterceptor.onAcknowledgement` + 4 publishers (events/follow/coorg/comments) + 8 fallbacks REST clients | ~1h30 | élevé — tient la promesse ADR-003 d'observabilité opérateur | B2 + I-SFH-2 + I-SFH-3 + I-SFH-6 |
| **5** | **Outbox poller robustness** : (a) détecter `JsonProcessingException` poison-pill (sortir la row du polling), (b) seuil `attempts < N`, (c) ajouter 3 tests (`alreadyPublished_isSkipped`, `retryAfterTransientFailure_succeeds`, état terminal). Optionnel : exposer `markPublished()`/`recordFailure()` méthodes d'instance | ~2h | élevé — robustifie le seul pipeline outbox transactionnel | B1 + I-TDA-1 + PTA-gap-modéré + M-CR-10 |

**Total effort Étape 25** : ~5h. Aucun de ces items ne justifie de retenir la PR — ils peuvent être adressés sur `main` après merge.

---

## 4. Faux positifs arbitrés (rejets explicites)

Tous conformes aux Décisions actées A-K — **NON revisitables**. Les 5 sous-agents ont correctement marqué FP plutôt que de rouvrir.

- `@PermitAll` sur `/events/{id}/organizer-uuids` → **Décision J / [ADR-002](../backend/docs/adr/ADR-002-organizer-uuids-permitall.md)**, sentinel `EventDomainSentinelsTest.getOrganizerUuids_bannedEvent_doesNotLeakUuidsToAnyCaller` pin l'invariant.
- `events.banned` outbox vs 4 autres topics best-effort → **Décision K / [ADR-003](../backend/docs/adr/ADR-003-event-banned-outbox-vs-best-effort.md)**, alternatives "outbox sur les 5" rejetée.
- 4 EventDTO sub-package variants identiques → **Décision E**, JavaDoc justificative bilingue (event/me/dto, event/favorite/dto, event/coorganizer/dto, event/dto/master).
- Header `X-Internal-Token` filter → **Décision C / SEC-002-bis**, fail-closed via `InternalTokenFilterFailClosedTest`.
- `replicas:1 strict` moderation + event → **[ADR-001](../backend/docs/adr/ADR-001-moderation-cleanup-replicas-strict.md)** + Helm guard `{{- fail }}`.
- Outbox poller sans leader-election → **ADR-001 + ADR-003 § Conséquences** (Shedlock deferred S9+).
- `shared-domain-dtos.faculty: Faculty` au lieu de `String` → Étape 24.6.4 explicite.
- `getById` retourne 404 BANNED même au créateur → ISSUE-92 anti-oracle, sentinel C4.

---

## 5. Verdict final

✅ **PR #158 mergeable telle quelle — handoff DevOps OK**

**Justification** :

- **0 BLOQUANT au sens strict** (data-corruption, sécurité fonctionnelle cassée, invariant ADR violé). Les B1/B2/B3 SFH sont des défauts d'observabilité ADR-003 (logs WARN au lieu d'ERROR + errorId) — sérieux mais le pipeline `events.banned` reste **durable** via outbox transactionnel ; la métrique d'audit ne sera juste pas optimale en cas d'incident Kafka.
- **Invariants tenus** : `git diff frontend/ openapi/` = 0 ligne ✓ ; `find -name '*Stub.java'` = vide ✓ ; 17 modules ✓ ; Décisions A-K appliquées sans déviation ✓ ; sentinels métier 4/4 services ✓ ; build local SUCCESS ✓ ; CI verte (sauf Deploy/Preview cancellé manuellement, intentionnel).
- **Couverture Sonar 90,5 %** sur new code, factuellement gonflée de ~8-10 % par DTO tests positionnels (Décision E défendable) — **plancher comportemental réel ~80-82 %**, au-dessus du seuil.
- **Les 7 items [`devops-handoff.md`](../backend/docs/devops-handoff.md)** restent prêts à passer en main DevOps PINFO (Kafka prod-grade, certs TLS, Doppler secrets, NetworkPolicies, GHCR cleanup, Pact provider verif, exception SonarCloud new-code).

**Étape 25 recommandée mais NON bloquante** : Top 5 actionables ci-dessus, idéalement en un sprint court de ~5h post-merge avant de fermer la branche `refactor(backend)--migrate-to-microservices`.

Elie peut **merger** ; le DevOps PINFO peut commencer les 7 items machine en parallèle.

---

## Annexe A — Méthode

**5 sous-agents `pr-review-toolkit` lancés en parallèle** (1 message, 5 Agent calls — gain ~15 min wall-clock vs sériel) :

1. `code-reviewer` — conformité conventions backend (`backend/AGENTS.md`), Quarkus 3.35 / Hibernate Panache best practices, focus changements Étape 24 (a)..(i).
2. `silent-failure-hunter` — swallowed exceptions, fallbacks suspects, observabilité ADR-003.
3. `type-design-analyzer` — design des types nouveaux/modifiés (entités JPA, enums, records DTO, annotations, providers).
4. `pr-test-analyzer` — couverture tests chemins critiques + sentinels + évaluation Sonar 90,5 %.
5. `comment-analyzer` — JavaDoc + docs MD + properties + Helm chart, comment-rot vs comment-paraphrase vs comment précieux.

Chaque agent a chargé en amont (Read en parallèle) : `AGENTS.md` racine, `backend/AGENTS.md`, `backend/docs/architecture.md`, `backend/docs/data-model.md`, `backend/docs/internal-endpoints.md`, ADR-001/002/003, `backend/docs/sprint-context.md` § Étape 23 + § Étape 24, `backend/docs/devops-handoff.md`. Spec source : [`specs_archives/specs_claude/specs_pr158_etape_24_total_fix.md`](specs_claude/specs_pr158_etape_24_total_fix.md).

**Aucune édition** sur le code applicatif pendant la review (review pure, pas de patch).

## Annexe B — Références

- Audit final source (35 findings) : [`audit_pr158_finalization_post.md`](audit_pr158_finalization_post.md)
- Spec finalization-complete (Étape 23) : [`specs_claude/specs_pr158_finalization_complete.md`](specs_claude/specs_pr158_finalization_complete.md)
- Spec total-fix (Étape 24) : [`specs_claude/specs_pr158_etape_24_total_fix.md`](specs_claude/specs_pr158_etape_24_total_fix.md)
- Sprint context (récap commits) : [`backend/docs/sprint-context.md`](../backend/docs/sprint-context.md) § Étape 23 + § Étape 24
- DevOps handoff (7 items machine) : [`backend/docs/devops-handoff.md`](../backend/docs/devops-handoff.md)


Prompt lancé pour cette review :
# Big review parallèle multi-agent — PR #158 unige-events (avant handoff DevOps)

## Contexte projet (à connaître avant de lancer)

- **Repo** : `unige-pinfo6-2026/unige-events` (monorepo).
- **Branche persistante** : `refactor(backend)--migrate-to-microservices` (NE PAS créer de nouvelle branche, NE PAS merger).
- **PR active** : [#158](https://github.com/unige-pinfo6-2026/unige-events/pull/158).
- **HEAD actuel** : `e4b3817c` (clôture Étape 24 — total fix pré-merge ; 56/56 items review consolidée multi-agent adressés + 35/35 findings audit final Étape 23 toujours valides).
- **Scope total** : **243 commits depuis `origin/main`** (dont **54 sur la seule Étape 24**, 22 sur Étape 23, le reste sur la migration monolithe → microservices). **17 modules Maven** (5 services Quarkus actifs + 10 shared libs + contract-tests + e2e), **~180+ fichiers cumulés modifiés**.
- **Stack** : Java 21 / Quarkus 3.35 / Hibernate Panache / PostgreSQL 16 + Flyway / Kafka KRaft / Kong DB-less / Auth0 OIDC / React 19 + Vite (frontend invariant) / Helm umbrella chart.
- **Build local** : `cd backend && ./mvnw -B -DskipITs verify -T 1` → SUCCESS sur 17 modules à HEAD `e4b3817c` (~3:16).
- **CI** : à valider après push final (étape 24.10.1 vient d'être poussée). Tous les jobs précédents (HEAD `a80139a8`) étaient verts (5 builds backend + shared-libs + contract-tests + frontend + Sonar Aggregate + 2 SonarCloud). Seul `Deploy / Preview` est cancellé manuellement (intentionnel).
- **Plugins déjà installés** : `pr-review-toolkit`, `jdtls-lsp`, `typescript-lsp`, `context7`, `frontend-design`, `superpowers`, `code-review`. (`github` MCP en échec OAuth — utiliser `gh` CLI à la place.)

## Documents source à lire AVANT de lancer la review

Lis-les en parallèle (Read tool, plusieurs en un message) pour avoir le contexte avant d'instruire les sous-agents :

1. `AGENTS.md` (racine) — invariants globaux
2. `backend/AGENTS.md` — conventions backend (camelCase, booléens sans `is`, pattern AFTER_SUCCESS Kafka, cascade SCRUM-136, anti-oracles ISSUE-92/93 ; section soft-delete réalignée sur EventStatus enum à l'Étape 24.9.15)
3. `backend/CLAUDE.md` (renvoie à AGENTS.md)
4. `backend/docs/architecture.md` — topologie 5 services post-consolidation
5. `backend/docs/sprint-context.md` § « Étape 24 — Total fix pré-merge » (en haut du fichier, 2026-05-10) — récap des 54 commits de cette dernière vague avec mapping item ↔ vague ↔ Décision A-K
6. `backend/docs/sprint-context.md` § « Étape 23 — Finalisation totale post-audit final » (juste en-dessous) — récap des 22 commits précédents
7. `backend/docs/devops-handoff.md` — les 7 items machine PINFO restants (hors scope de la review)
8. `backend/docs/internal-endpoints.md` — § ADR-002 cross-link (Étape 24.9.16)
9. `backend/docs/data-model.md` — § soft-delete EventStatus + S3 cleanup hors-tx (Étape 24.9.15)
10. `backend/docs/adr/ADR-001-moderation-cleanup-replicas-strict.md` — Décision Helm guard
11. `backend/docs/adr/ADR-002-organizer-uuids-permitall.md` — Décision J (Étape 24.1.3)
12. `backend/docs/adr/ADR-003-event-banned-outbox-vs-best-effort.md` — Décision K (Étape 24.4.1)
13. `specs_archives/specs_claude/specs_pr158_etape_24_total_fix.md` — spec exécutée pour les 54 derniers commits (10 vagues, 56 items)
14. `specs_archives/specs_claude/specs_pr158_finalization_complete.md` — spec Étape 23 (référence historique)
15. `specs_archives/specs_claude/audit_pr158_migration_microservices_final.md` — audit source (35 findings) — référence

## Mission

Lancer **5 sous-agents `pr-review-toolkit` en parallèle** sur **TOUTE la PR** (`git diff origin/main HEAD`, soit 243 commits / ~180 fichiers cumulés). La review doit couvrir l'intégralité du travail de migration ET de finalisation post-Étape 24, pas seulement les derniers commits — donc cible bien le diff complet.

**Lance les 5 `Agent` tool calls dans UN SEUL message** pour parallélisation (gain de ~15 min wall-clock vs sériel).

But final : confirmer que la PR #158 est mergeable telle quelle (Elie merge à la main) et qu'on peut passer la main au DevOps PINFO pour la finalisation infra (cf. `devops-handoff.md`). Si ≥ 1 BLOQUANT non-clos : documenter pour Étape 25.

## Les 5 sous-agents à invoquer (prompts à reprendre tels quels)

### 1. `pr-review-toolkit:code-reviewer`

> Review l'intégralité du diff `git diff origin/main HEAD -- 'backend/**/*.java' 'backend/**/*.xml' 'backend/**/*.properties' 'k8s/chart/**'` pour la PR #158 (migration backend monolithe → 5 microservices Quarkus + finalisation Étape 23 + total fix Étape 24). Cible : conformité aux conventions documentées dans `AGENTS.md` (racine) + `backend/AGENTS.md` (camelCase JPA, booléens sans préfixe `is`, pattern Kafka `@Observes(AFTER_SUCCESS)` + bridge, `@Transactional` sur mutations, anti-oracles ISSUE-92/93, cascade SCRUM-136 via REST client). **Focus particulier sur les changements Étape 24** : (a) le nouveau **outbox EventBanned** (Vague 4, A10) — entité `EventBannedOutbox`, migration Flyway V18, poller dans moderation-service ; (b) `User.faculty` migré de `String` → `Faculty` enum avec `@Enumerated(STRING)` (Vague 6, B6) ; (c) `EventCoOrganizerService.decline` qui filtre maintenant `status == PENDING` (Vague 9, D18) ; (d) `EventCoOrganizerService.lookupTargetUser` helper static distinguant 404/503 (Vague 9, D19) ; (e) `UserService` `OptimisticLockException` enveloppée en `WebApplicationException(409)` avec cause préservée (Vague 9, D20) ; (f) les 3 **FallbackWiring** (event/user/engagement) (Vague 7, C7) ; (g) `InternalTokenFilterFailClosed` (Vague 7, C8) ; (h) `EnumParamConverterProvider` portant `@Priority(USER + 100)` (Vague 8, E3) ; (i) `FavoriteService.getFavorites` bulk fetch + sentinel anti-N+1 (Vague 8, E1). Signale les violations + best practices Quarkus 3.35 / Hibernate Panache. NE PAS éditer le code — produire un rapport classé par fichier + sévérité (BLOQUANT/IMPORTANT/MINEUR) avec `file_path:line_number` cliquable.

### 2. `pr-review-toolkit:silent-failure-hunter`

> Scan toute la PR #158 pour silent failures, swallowed exceptions, fallbacks suspects. Focus particulier post-Étape 24 sur : (a) le **outbox EventBanned** transactionnel vs les 4 autres topics best-effort — vérifier que `events.banned` ne peut PLUS être perdu après commit DB (poller `EventBannedOutboxPoller` dans moderation-service, retry/backoff) ; (b) les 3 nouveaux **FallbackWiring** (event-service, user-service, engagement-service — Vague 7, C7) qui doivent maintenant logger les fallbacks au lieu de les masquer ; (c) `InternalTokenFilterFailClosed` dans shared-jaxrs — vérifier qu'il échoue fermé (rejette si token absent, pas seulement invalide — Vague 7, C8) ; (d) `MdcKafkaInMemory` (Vague 7, C9) — vérifier que les interceptors Kafka ne perdent plus les MDC en in-memory test ; (e) `EventCoOrganizerService.lookupTargetUser` qui distingue 404 (user not found) vs 503 (user-service down) — Vague 9, D19 ; (f) `FavoriteService.getFavorites` bulk fetch (Vague 8, E1) — vérifier qu'aucune erreur partielle de l'API user-service n'est silencieusement masquée par le bulk ; (g) `safeGetUser` dans plusieurs services (catch RuntimeException retournant null) — vérifier qu'il loggue toujours ; (h) tous les `@Fallback` des REST clients dans `shared-domain-dtos.shared.client.*` — vérifier le wiring fait à l'Étape 24.7.5 ; (i) `EventService.delete` (purge EventCoOrganizer + S3 cleanup hors-tx) ; (j) `AttendanceService.attend/removeAttendance` (advisory lock + capacity gating). Liste tout catch qui swallowe sans logger ou re-throw documenté. NE PAS éditer le code.

### 3. `pr-review-toolkit:type-design-analyzer`

> Analyse le design des types introduits par la PR #158, **avec emphase sur les changements Étape 24**. Focus : (a) la nouvelle entité `EventBannedOutbox` (Vague 4, A10) — vérifier l'encapsulation (status, attempts, lastError, etc.), idempotence, expression d'invariants ; (b) `Faculty` enum maintenant **persisté sur `User.faculty`** avec `@Enumerated(STRING)` (Vague 6, B6) — vérifier la cohérence DB ↔ JPA ↔ DTO ; (c) `FollowStatus` (PENDING, ACCEPTED — REJECTED retirée car DB CHECK V14 incompatible) et `CoOrganizerStatus` (PENDING, ACCEPTED, DECLINED) — vérifier les `BehaviorTest` × 4 dans shared-domain-enums (Vague 7) ; (d) `coOrganizerOf` typé **`Boolean` boxed nullable** sur les 4 variants EventDTO (Décision E, Vague 6) — vérifier la JavaDoc ; (e) `shared-api-error.ApiErrorResponse` (record annoté `@Schema`) — utilisé par OptimisticLockException 409 (D20) ; (f) les 4 EventDTO sub-package variants (event/dto, event/me/dto, event/favorite/dto, event/coorganizer/dto — duplication INTENTIONNELLE post-Décision E, vérifier la JavaDoc justificative inchangée) ; (g) annotation `@Internal` (NameBinding) dans shared-jaxrs ; (h) enums `EventStatus`, `AttendanceStatus`, `Faculty`, `FollowStatus`, `CoOrganizerStatus` couverts par `EnumParamConverterProvider` ; (i) records DTOs dans `shared-domain-dtos`. Rate sur encapsulation, expression d'invariants, utilité, enforcement. Signale les types qui mériteraient d'être sealed / records / enums plutôt que classes mutables. NE PAS éditer.

### 4. `pr-review-toolkit:pr-test-analyzer`

> Audit la couverture de tests de la PR #158, **avec emphase sur les nouveaux tests Étape 24**. La PR ajoute / modifie ces tests notables : (a) **Étape 24** : `EventCoOrganizerServiceLookupTargetUserTest` (4 cas dont `invite_userServiceDown_throws503` — D19), sentinel `decline_acceptedInvitation_throws422` (D18), `EventBannedOutboxPollerTest` (A10), 4 × `*BehaviorTest` dans shared-domain-enums (Vague 7), `InternalTokenFilterFailClosedTest` (C8), `MdcKafkaInMemoryTest` (C9), 3 × `*FallbackWiringTest` (C7), `OptimisticLockException409Test` (D20), `FavoriteServiceBulkFetchTest` + sentinel anti-N+1 (E1), sentinel ADR-002 `organizerUuids_filtersBanned` (Étape 24.7.4), `EnumParamConverterProviderPriorityTest` (E3) ; (b) **Étape 23** : `ModerationDomainSentinelsTest` (8 méthodes pin SCRUM-139 + BannedEventCaptor), `MdcKafkaProducer/ConsumerInterceptorTest` (5+4 cas), `InternalTokenFilterTest` (5 cas) + `InternalTokenClientFilterTest` (3 cas), `EnumParamConverterProviderTest` (6 cas), `EventTzSmokeTest` (1 cas), `_missingInternalToken_returns404` × 2 dans engagement-service. Vérifie : (1) la couverture des chemins critiques de chaque sous-étape (BUG-005-bis advisory lock, BUG-006-bis idempotence, EVENT-DELETE-001 cascade, MdcKafka, InternalToken filter, outbox EventBanned, lookupTargetUser, decline filtering, OptimisticLock 409) ; (2) les cas limites manquants (concurrent burst sur capacity, race conditions sur outbox poller, fallback REST clients fail-fast vs fail-slow, anti-oracle leaks sur organizer-uuids ; le test `lookupTargetUser` est un test unitaire pur, pas `@QuarkusTest` — vérifier que la raison invoquée [Fallback global masque RuntimeException dans `@InjectMock`] tient toujours) ; (3) la qualité des sentinels existants 4/4 services métiers + ModerationDomainSentinelsTest. NE PAS éditer.

### 5. `pr-review-toolkit:comment-analyzer`

> Analyse les JavaDoc + commentaires modifiés/ajoutés par la PR #158 (`git diff origin/main HEAD -- '*.java' '*.md' '*.yaml' '*.properties'`). **Focus particulier sur les changements doc Étape 24** : (a) la nouvelle section sprint-context.md § « Étape 24 — Total fix pré-merge » (Vague 10, 24.10.1) — vérifier que le tableau des 56/56 items reflète la réalité (54 commits, 10 vagues, A-K) ; (b) `backend/docs/internal-endpoints.md` — cross-link ADR-002 (Étape 24.9.16) ; (c) `backend/docs/data-model.md` — § S3 cleanup hors-tx + EventStatus soft-delete (Étape 24.9.15) ; (d) `backend/AGENTS.md` § soft-delete realigné sur EventStatus enum (suppression du « champ active boolean sur Event » faux) (Étape 24.9.15, D21) ; (e) ADR-002-organizer-uuids-permitall.md (FR/EN bilingue, Décision J) ; (f) ADR-003-event-banned-outbox-vs-best-effort.md (FR/EN bilingue, Décision K) ; (g) la JavaDoc des nouveaux helpers/services (`EventBannedOutbox`, `EventBannedOutboxPoller`, `EventCoOrganizerService.lookupTargetUser`, `FavoriteService.getFavorites` bulk, `InternalTokenFilterFailClosed`, `MdcKafkaInMemory`) ; (h) le retrait des commentaires obsolètes Helm chart (« legacy-monolith via the catch-all », « PR 9/12/13 of the extraction », « Target topology — commented out », « all 14 microservices ») au profit de la topologie 5-service finale (Vague 9, D1-D5) ; (i) les 4 EventDTO variants — JavaDoc justificative Décision E inchangée (vérifier le PASS) ; (j) la JavaDoc S3-orphan-tolerance sur `UserService.uploadImage/uploadBanner` (Étape 23.5.5 MINOR-011, conservée) ; (k) les blocs commentaires dans `application.properties` des 5 services (Flyway, X-Internal-Token, Kafka interceptors, TZ, engagement-service.read-timeout=1000 — Vague 8 E2). Vérifie l'exactitude des commentaires vs le code décrit, signale les comment-rot potentiels et les commentaires à valeur faible (juste paraphrase du code). NE PAS éditer.

## Format du rapport final consolidé

Une fois les 5 sous-agents revenus, produis une synthèse markdown avec :

1. **Tableau récap par sévérité** (BLOQUANT / IMPORTANT / MINEUR / NIT) — un finding par ligne avec source agent + file:line.
2. **Findings recoupés** : items levés par ≥ 2 sous-agents (signal fort à prioriser).
3. **Top 5 actionables** : 5 patches concrets recommandés avant merge, ordonnés par ROI (ratio impact/effort).
4. **Faux positifs notables** : findings que les sous-agents ont remontés mais qui sont en fait conformes aux décisions actées (cf. Décisions A-K dans sprint-context.md § Étape 24 + ADR-001/002/003). Exemples attendus à classer FP : (a) `@PermitAll` sur `/events/{id}/organizer-uuids` (Décision J / ADR-002, fix pin par sentinel), (b) `events.banned` outbox vs 4 autres topics best-effort (Décision K / ADR-003), (c) duplication intentionnelle des 4 EventDTO sub-package variants (Décision E), (d) header `X-Internal-Token` filter (Décision C / SEC-002-bis), (e) replicas:1 strict sur moderation-service cleanup (ADR-001).
5. **Verdict final** :
   - ✅ **« PR #158 mergeable telle quelle — handoff DevOps OK »** (aucun BLOQUANT non-clos, aucun IMPORTANT non-clos lié au code applicatif) — Elie merge ; les 7 items `devops-handoff.md` passent en main DevOps PINFO.
   - ⚠️ **« Étape 25 nécessaire avant merge »** (≥ 1 BLOQUANT non-clos OU ≥ 1 IMPORTANT bloquant le handoff) — lister les corrections, par ordre.

## Invariants à NE JAMAIS violer pendant la review

- `git diff --shortstat origin/main HEAD -- frontend/` = 0 ligne → si un sous-agent suggère une modif frontend, **rejeter** dans le rapport final.
- `git diff --shortstat origin/main HEAD -- openapi/` = 0 ligne → idem.
- `find backend/services -name '*Stub.java'` = vide → aucune ré-introduction de stub JPA cross-service.
- 17 modules dans le reactor — aucune suggestion de consolidation/split de module.
- Les 11 Décisions actées (A → K) sont **non-revisitables** dans le cadre de cette review : si un sous-agent veut rouvrir une décision, le marquer FP avec référence ADR/spec.
- **Review pure, pas d'édition** : les 5 sous-agents doivent produire des rapports, pas des patches appliqués. Aucun `Edit` / `Write` sur le code applicatif.
- Si un sous-agent veut tracker un point hors-scope (ex: items DevOps PINFO du `devops-handoff.md`), le mentionner mais le marquer « hors scope PR — devops PINFO ».

## Décisions actées (récap pour les sous-agents)

- **A** Schéma `public` partagé conservé, Flyway redistribué par owner — Étape 23.1.1.
- **B** `pg_advisory_xact_lock(eventId)` pour le capacity gating — Étape 23.2.1.
- **C** Header `X-Internal-Token` validé par filter shared-jaxrs — Étape 23.3.2.
- **D** `MdcKafkaInterceptor` dans shared-tracing (producer + consumer) — Étape 23.3.1.
- **E** `EventDTO` 4 sous-packages event-service : JavaDoc justificatif (pas de consolidation) — Étape 23.4.4.
- **F** `TZ=Europe/Zurich` fixé dans Helm Deployments — Étape 23.4.5.
- **G** CASCADE-136-DRIFT vérifié — pas de remédiation nécessaire — Étape 23.2.4.
- **H** `ModerationDomainSentinelsTest` SCRUM-139 : 8 tests pin — Étape 23.1.3.
- **I** Doc + JavaDoc cleanup : sed batch ciblé + refonte 4 sections — Étapes 23.4.9 / 4.10 / 4.11 / 1.2.
- **J** (Étape 24) `/events/{id}/organizer-uuids` reste `@PermitAll` — Étape 24.1.3 (ADR-002).
- **K** (Étape 24) `events.banned` via outbox transactionnel ; 4 autres topics best-effort — Étape 24.4.1 (ADR-003).

## Mémoire utilisateur

Elie Bussod, group 6 PINFO, full-stack UNIGE Events, communique en français. Cf. `~/.claude/projects/-workspace/memory/MEMORY.md` pour le profil complet.

GO.