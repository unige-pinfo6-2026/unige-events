# Étape 24 — Total fix PR #158 (clôture pré-merge)

| Champ | Valeur |
|---|---|
| Sprint | S8 (clôture absolue post-review consolidée multi-agent) |
| Branche | `refactor(backend)--migrate-to-microservices` (persistante, **NE PAS créer de nouvelle branche**) |
| HEAD baseline | `2aeae9de` (à confirmer par `git rev-parse HEAD` au démarrage) |
| PR active | [#158](https://github.com/unige-pinfo6-2026/unige-events/pull/158) — **NE PAS merger**, Elie merge lui-même |
| Auteur spec | Claude (session 2026-05-10, post-review consolidée) |
| Exécuteur cible | Claude Code en bypass-permissions, autonome, branche persistante, sans merge |
| Frontend invariant | **0 ligne ABSOLU** — `git diff --shortstat origin/main HEAD -- frontend/` = 0 |
| OpenAPI invariant | **0 ligne ABSOLU** — `git diff --shortstat origin/main HEAD -- openapi/` = 0 |
| Source de vérité | review consolidée multi-agent (5 sous-agents `pr-review-toolkit`) — 26 findings + 10 recoupés + 9 faux positifs arbitrés |
| Specs antérieures | [`specs_pr158_finalization_complete.md`](specs_pr158_finalization_complete.md), [`audit_pr158_migration_microservices_final.md`](audit_pr158_migration_microservices_final.md), [`../audit_pr158_finalization_post.md`](../audit_pr158_finalization_post.md) |

---

## Sommaire

- [Note d'implémentation](#note-dimplémentation)
- [0. Contexte & invariants](#0-contexte--invariants)
- [1. Stratégie d'exécution](#1-stratégie-dexécution)
- [2. Étapes (56 sous-étapes)](#2-étapes-56-sous-étapes)
  - [Vague 1 — Sécurité critique (A2, A4, A16)](#vague-1--sécurité-critique)
  - [Vague 2 — Sentinels TDD (C1, C2, C3)](#vague-2--sentinels-tdd)
  - [Vague 3 — Observabilité silent failures (A5, A6, A7, A8, A9, A12, A13, A14)](#vague-3--observabilité-silent-failures)
  - [Vague 4 — Kafka outbox EventBanned (A10)](#vague-4--kafka-outbox-eventbanned)
  - [Vague 5 — Robustness restant (A1, A3, A11, A15)](#vague-5--robustness-restant)
  - [Vague 6 — Types (B1..B6)](#vague-6--types)
  - [Vague 7 — Tests intégration (C4..C9)](#vague-7--tests-intégration)
  - [Vague 8 — Refactors utiles (E1..E4)](#vague-8--refactors-utiles)
  - [Vague 9 — Documentation (D1..D21)](#vague-9--documentation)
  - [Vague 10 — sprint-context.md final](#vague-10--sprint-contextmd-final)
- [3. Validation finale](#3-validation-finale)
- [4. Mise à jour sprint-context.md](#4-mise-à-jour-sprint-contextmd)
- [5. Faux positifs / Décisions à acter](#5-faux-positifs--décisions-à-acter)
- [6. Dépendances et risques](#6-dépendances-et-risques)
- [Annexe A — Traçabilité finding → étape → commit](#annexe-a--traçabilité-finding--étape--commit)

---

## Note d'implémentation

Cette spec est l'**unique source de vérité** pour la clôture définitive de la PR #158 avant merge. Elle adresse **tous les 56 items** issus de la review consolidée multi-agent, sans aucun report S9. Après son exécution complète :

1. Tous les bugs corrigés dans la PR ont leur sentinel test (cf. C1/C2/C3) — régression invisible impossible.
2. Toute la couche de tolérance aux pannes (fallbacks REST, `safeGetUser`) est observable (logs structurés + errorIds).
3. La JavaDoc, les `application.properties`, les commentaires Helm sont alignés à la topologie 5-services.
4. La sécurité défensive est durcie (default `INTERNAL_TOKEN` retiré, anti-oracle ISSUE-92 fermé sur `ShareService`).
5. Les types Java expriment leurs invariants (enums avec comportement, records pour les payloads, faculty typé fort).
6. Build local SUCCESS sur 17 modules. Tous les checks CI verts (sauf `Deploy / Preview` cancellé manuellement, intentionnel).

**L'exécuteur autonome** :

- ne demande **jamais** une décision au user (toutes tranchées ici, y compris Décision J pour `/events/{id}/organizer-uuids` cf. § 5) ;
- commit + push après chaque sous-étape numérotée verte (granularité ≈ 1 commit par sous-étape `24.X.Y`, ≤ 500 lignes diff sauf justifié) ;
- pousse sur la branche persistante `refactor(backend)--migrate-to-microservices` ;
- ne merge **jamais** la PR #158 ;
- ne crée **jamais** de nouvelle branche, jamais de nouveau ticket Jira, jamais de nouvelle PR ;
- met à jour `backend/docs/sprint-context.md` (nouvelle § Étape 24) au fil de l'eau, regroupé en commit final d'Étape 24.10.1 ;
- valide chaque sous-étape via `cd backend && ./mvnw -B -DskipITs verify -T 1` (~2:30 sur le reactor 17 modules) ;
- watch CI **par étape majeure** : `gh pr checks 158 --watch` à la fin de chaque vague ;
- en cas d'échec CI, **fixe la cause racine** — jamais de `--no-verify`, jamais de `@Disabled`, jamais de skip silencieux, jamais de retrait d'assertion, jamais d'exclusion Sonar arbitraire.

Toute déviation par rapport aux décisions ici doit être **actée explicitement** dans le commit message + dans `sprint-context.md` § Étape 24, avec justification concrète.

> **Convention bilingue post-finalization (rappel critique).** Tout nouveau document long (ADR-002, sous-section data-model.md) doit porter un préambule FR/EN en tête (cf. commit `c75bde17`). La JavaDoc reste en anglais (convention historique du repo). Les commentaires en `application.properties` peuvent être bilingues si pertinent. Le code reste en anglais.

> **Leçon « zéro stub cross-service » (rappel).** Aucune entité JPA `*Stub.java`. Tous les besoins cross-domain passent par les REST clients shared-domain-dtos. Vérifié à `find backend/services -name '*Stub.java'` = vide après HEAD `2aeae9de`.

---

## 0. Contexte & invariants

### 0.1 État du dépôt au démarrage

- `git rev-parse HEAD` = `2aeae9de` (HEAD baseline ; à confirmer).
- 188 commits depuis `origin/main`.
- 513 fichiers modifiés (+47591 / −22738 lignes).
- 17 modules Maven dans le reactor : 4 services Quarkus actifs (event/user/engagement/moderation) + 1 placeholder (notification, replicas:0) + 10 shared libs + contract-tests + e2e.
- `find backend/services -name '*Stub.java'` = vide.
- `git diff --shortstat origin/main HEAD -- frontend/` = 0 ligne.
- `git diff --shortstat origin/main HEAD -- openapi/` = 0 ligne.
- Build local : `cd backend && ./mvnw -B -DskipITs verify -T 1` → SUCCESS sur 17 modules en ~2:30.
- CI : tous verts (5 builds backend + shared-libs + contract-tests + frontend + Sonar Aggregate + 2 SonarCloud). Seul `Deploy / Preview` cancellé manuellement (intentionnel).
- Étape 23 (35/35 findings de l'audit final clôturés) : commit `c75bde17` (préambule bilingue) → `2aeae9de` (sprint-context Étape 23).

### 0.2 Invariants stricts (à vérifier à chaque étape)

| Invariant | Commande de vérification | Valeur attendue |
|---|---|---|
| Frontend zéro-touch | `git diff --shortstat origin/main HEAD -- frontend/` | `(empty)` |
| OpenAPI zéro-touch | `git diff --shortstat origin/main HEAD -- openapi/` | `(empty)` |
| Pas de stub JPA cross-service | `find backend/services -name '*Stub.java'` | `(empty)` |
| 17 modules dans le reactor | `grep -c '<module>' backend/pom.xml` | `17` |
| Build SUCCESS | `cd backend && ./mvnw -B -DskipITs verify -T 1` | `BUILD SUCCESS` |
| Pas d'`@Emitter.send` in-tx hors bridges | `grep -rn 'emitter.send' backend/services/*/src/main/java/ \| grep -v 'KafkaBridge\.java' \| grep -v 'Publisher\.java'` | `(empty)` |
| Pas de `@Disabled`/`@Tag("legacy-port-s9")` ajoutés | `grep -rln '@Disabled\|@Tag("legacy-port-s9")' backend/services/*/src/test/java` | `(unchanged)` |

### 0.3 Décisions A-I déjà appliquées (rappel)

| Décision | Sujet | Étape source |
|---|---|---|
| A | Schéma `public` partagé conservé, Flyway redistribué par owner | Étape 23.1.1 |
| B | `pg_advisory_xact_lock(eventId)` pour le capacity gating | Étape 23.2.1 |
| C | Header `X-Internal-Token` validé par filter shared-jaxrs | Étape 23.3.2 |
| D | `MdcKafkaInterceptor` dans shared-tracing (producer + consumer) | Étape 23.3.1 |
| E | `EventDTO` 4 sous-packages event-service : JavaDoc justificatif (pas de consolidation) | Étape 23.4.4 |
| F | `TZ=Europe/Zurich` fixé dans Helm Deployments | Étape 23.4.5 |
| G | CASCADE-136-DRIFT vérifié — pas de remédiation nécessaire | Étape 23.2.4 |
| H | `ModerationDomainSentinelsTest` SCRUM-139 : 8 tests pin | Étape 23.1.3 |
| I | Doc + JavaDoc cleanup : sed batch ciblé + refonte 4 sections | Étapes 23.4.9 / 4.10 / 4.11 / 1.2 |

### 0.4 Nouvelle Décision J — `/events/{id}/organizer-uuids` reste `@PermitAll`

Item A16 de la review consolidée. **Décision J** : status quo accepté, formalisé via ADR-002 (créé en Étape 24.1.3). Justification :

- Le seul consommateur (engagement-service) appelle d'abord `GET /events/{id}?check-co-org-of=` qui applique la cascade ISSUE-92 + SCRUM-136. La sortie de `getOrganizerUuids` n'est exploitable que par un caller qui a déjà passé la garde de visibilité.
- Marquer cet endpoint `@Internal` couplerait la liste des co-organisateurs à la disponibilité du header `X-Internal-Token` côté consommateur, alors que la primitive est stable cross-service depuis post-consolidation 14→5.
- Le leak théorique (anonyme peut énumérer les UUIDs des co-organisateurs d'un Event PUBLISHED) est borné : (a) UUIDs non corrélables à des comptes Auth0 sans accès user-service, (b) `getOrganizerUuids` filtre déjà les Events `BANNED`, (c) la pagination naturelle (1 event = N≤10 co-orgs) limite l'exfiltration de masse.

Conséquence : ADR-002 documente la décision + les 3 mitigations, l'endpoint reste annoté `@PermitAll`, un test sentinel (Étape 24.7.4) pin l'invariant « `getOrganizerUuids` ne retourne **jamais** les UUIDs si l'Event est `BANNED`, indépendamment du caller ».

---

## 1. Stratégie d'exécution

### 1.1 Ordonnancement des vagues

Le DAG suivant respecte les dépendances entre items. Chaque vague se valide indépendamment via `mvnw verify` avant de passer à la suivante.

```
Vague 1 ─ Sécurité critique (A2, A4, A16)
         │
         ▼
Vague 2 ─ Sentinels TDD (C1, C2, C3)         ← rouge → vert sur les bugs déjà fixés
         │
         ▼
Vague 3 ─ Observabilité silent failures (A5, A6, A7, A8, A9, A12, A13, A14)
         │
         ▼
Vague 4 ─ Kafka outbox EventBanned (A10)     ← Flyway V18 + nouveau outbox poller
         │
         ▼
Vague 5 ─ Robustness restant (A1, A3, A11, A15)
         │
         ▼
Vague 6 ─ Types (B5, B6 → B3 → B2 → B1 → B4) ← B3 enums avant call-sites refactorés
         │
         ▼
Vague 7 ─ Tests intégration (C4, C5, C6, C7, C8, C9)  ← après B3 pour C9
         │
         ▼
Vague 8 ─ Refactors utiles (E1, E2, E3, E4)
         │
         ▼
Vague 9 ─ Documentation (D1..D21)            ← après code stable
         │
         ▼
Vague 10 ─ sprint-context.md final + push final + watch CI
```

### 1.2 Pattern TDD pour les sentinels (C1, C2, C3, C4)

Les 4 sentinels suivants pinent des bugs **déjà corrigés en main code dans cette PR** :

- C1 : EVENT-DELETE-001 cascade → corrigé en Étape 23.2.3
- C2 : BUG-006-bis idempotence → corrigé en Étape 23.2.2
- C3 : BUG-005-bis advisory lock → corrigé en Étape 23.2.1
- C4 : anti-oracle ISSUE-92 body equivalence → corrigé depuis migration originale

**Procédure TDD pour chaque sentinel** :

1. Écrire le test sentinel.
2. **Vérifier qu'il est vert** (le bug est déjà fixé — c'est le comportement attendu).
3. Pour confirmer la valeur de pinning : commenter temporairement le fix code et relancer le test → doit passer rouge. **NE PAS commiter cet état rouge.**
4. Restaurer le fix code et commiter le test seul.

Cette procédure garantit que les sentinels ne sont pas vacuously green.

### 1.3 Convention de commit

Format : `<type>(<scope>): <description> (Étape 24.X.Y, <Finding-ID>)`

- `<type>` : `fix`, `feat`, `refactor`, `test`, `chore`, `docs`, `ci`, `style`, `perf`.
- `<scope>` : `backend` (la majorité), `event`, `user`, `engagement`, `moderation`, `shared-tracing`, `shared-jaxrs`, `shared-storage`, `shared-domain-dtos`, `shared-domain-projections`, `shared-api-error`, `shared-domain-enums`, `infra` (k8s/Helm), `ci`, `docs`. Pas de scope SCRUM-XXX (les commits Étape 24 ne ferment pas de ticket SCRUM).
- Trailer : `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.

Exemples :

```
chore(infra): align Kafka env var with consumed property name (Étape 24.5.1, A1)

fix(event): apply ISSUE-92 anti-oracle guard on /events/{id}/share (Étape 24.1.2, A2)

test(event): pin EVENT-DELETE-001 cascade EventCoOrganizer/Favorite/EventView (Étape 24.2.1, C1)

docs(infra): replace dead "PR N of extraction roadmap" refs by Étape 2.X.Y links (Étape 24.9.6, D6)
```

### 1.4 Validation par étape

Pour chaque sous-étape, l'exécuteur :

1. Applique le patch (code + test + doc).
2. Lance la commande de validation locale spécifique de l'étape (cf. champ « Validation » de chaque section).
3. Si la validation locale passe : `git add` + `git commit` + `git push`.
4. Si la validation échoue : fix root cause, jamais de `--no-verify`, jamais de retrait d'assertion.

À la fin de chaque vague :

1. `cd backend && ./mvnw -B -DskipITs verify -T 1` — build complet 17 modules.
2. `gh pr checks 158 --watch` — vérifier que la CI reste verte.

À la fin de la spec (Étape 24.10.1) :

1. Validation finale § 3.
2. Push final + watch CI complète.
3. Annonce à Elie : « Étape 24 close, PR #158 prête au merge ».

---

## 2. Étapes (56 sous-étapes)

### Vague 1 — Sécurité critique

#### Étape 24.1.1 — A4 : Default `INTERNAL_TOKEN` retiré des 4 services + commentaire properties corrigé

- **Finding** : A4 (review consolidée — convergent : code-reviewer IMPORTANT-4 + comment-analyzer I11).
- **Sévérité** : IMPORTANT (sécurité défense-en-profondeur SEC-004 violée).
- **Fichiers touchés** :
  - `backend/services/event-service/src/main/resources/application.properties:145-150`
  - `backend/services/user-service/src/main/resources/application.properties` (bloc analogue)
  - `backend/services/engagement-service/src/main/resources/application.properties` (bloc analogue)
  - `backend/services/moderation-service/src/main/resources/application.properties` (bloc analogue)
- **Patch (avant → après)** :

  ```properties
  # AVANT (event-service:145-150)
  # ─── X-Internal-Token (Étape 3.2, SEC-002-bis / Décision C) ────────────────
  # Defense in depth on top of the K8s perimeter for service-to-service
  # endpoints flagged @Internal. Sourced from Doppler in prod via
  # INTERNAL_TOKEN env var — same value across all services. In %test the
  # default value is used (filter only blocks if the value is non-empty).
  unige.internal-token=${INTERNAL_TOKEN:dev-internal-token-not-for-prod}

  # APRÈS
  # ─── X-Internal-Token (Étape 3.2, SEC-002-bis / Décision C) ────────────────
  # Defense in depth on top of the K8s perimeter for service-to-service
  # endpoints flagged @Internal. Sourced from Doppler in prod via the
  # INTERNAL_TOKEN env var — same value across all services. No prod
  # fallback: when INTERNAL_TOKEN is unset (or empty) the InternalTokenFilter
  # rejects every request to @Internal endpoints with 404 (fail-closed). This
  # keeps the secret out of the repo and surfaces missing config as a startup
  # symptom, not a silent security regression.
  unige.internal-token=${INTERNAL_TOKEN:}
  %test.unige.internal-token=test-internal-token
  ```

- **Test sentinel ajouté** : reporté à Étape 24.7.3 (C6) — tests intégration fail-closed.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service,services/user-service,services/engagement-service,services/moderation-service -am -DskipITs verify -T 1
  grep -rn 'dev-internal-token-not-for-prod' backend/  # doit être vide
  grep -rn '%test.unige.internal-token' backend/services/*/src/main/resources/application.properties | wc -l  # doit être 4
  ```
- **Doc à mettre à jour** : aucune ici (le commentaire est lui-même la doc de cette étape).
- **Risque** : les tests existants qui appellent des routes `@Internal` (ex. `AttendanceSummaryInternalResourceTest`, `UserAttendancesInternalResourceTest`, `InternalTokenFilterTest`) doivent envoyer le header `X-Internal-Token: test-internal-token`. Vérifier après le patch ; si un test casse, ajuster en envoyant le header.
- **Commit attendu** :
  ```
  fix(backend): remove default INTERNAL_TOKEN value, fail-closed in prod (Étape 24.1.1, A4)
  ```

---

#### Étape 24.1.2 — A2 : `ShareService.getShareInfo` anti-oracle ISSUE-92

- **Finding** : A2 (code-reviewer IMPORTANT-2).
- **Sévérité** : IMPORTANT (anti-oracle ISSUE-92 violé + mutation persistante non autorisée).
- **Fichiers touchés** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/share/resource/ShareResource.java` (signature à propager)
  - `backend/services/event-service/src/main/java/ch/unige/events/event/share/service/ShareService.java` (logique métier)
- **Patch (direction)** :

  ```java
  // ShareResource.java — propager auth0Id + isAdmin
  @GET
  @Path("/{id}/share")
  @Authenticated
  public Response getShareInfo(@PathParam("id") Long id,
                               @Context SecurityIdentity identity,
                               @Context JsonWebToken jwt) {
      String auth0Id = jwt.getName();
      boolean isAdmin = identity.hasRole("ADMIN");
      ShareResponse response = shareService.getShareInfo(id, auth0Id, isAdmin);
      return Response.ok(response).build();
  }

  // ShareService.java — appliquer la cascade ISSUE-92 + check creator/co-org
  @Transactional
  public ShareResponse getShareInfo(Long eventId, String auth0Id, boolean isAdmin) {
      Event event = Event.<Event>findByIdOptional(eventId)
              .orElseThrow(() -> new NotFoundException("Event not found"));

      // ISSUE-92 anti-oracle : DRAFT/CANCELLED/EXPIRED/BANNED → 404 sauf admin / creator / accepted co-org.
      if (event.status == EventStatus.BANNED) {
          throw new NotFoundException();
      }
      UUID callerUuid = Auth0IdResolver.resolveUserUuid(jwt());
      boolean isCreator = callerUuid != null && callerUuid.equals(event.creatorId);
      boolean isAcceptedCoOrg = callerUuid != null
              && EventCoOrganizer.isAcceptedFor(eventId, callerUuid);

      if (event.status != EventStatus.PUBLISHED && !isAdmin && !isCreator && !isAcceptedCoOrg) {
          throw new NotFoundException();  // anti-oracle
      }
      if (!isAdmin && !isCreator && !isAcceptedCoOrg) {
          throw new ForbiddenException("Only creator, accepted co-organizers or admins can fetch share info.");
      }

      if (event.shareCode == null) {
          event.shareCode = generateCode();
      }

      String shareUrl = appConfig.frontendUrl() + "/events/" + eventId;
      return new ShareResponse(shareUrl, event.shareCode);
  }
  ```

  Variante : déléguer la garde à `EventService.getById(eventId, auth0Id, isAdmin)` (qui applique déjà la cascade ISSUE-92 + SCRUM-136) si l'API permet de récupérer l'`Event` JPA derrière. À défaut, dupliquer le check tel qu'au-dessus.

- **Test sentinel ajouté** :
  - Classe : `ShareServiceTest` (créer si absent) ou `ShareResourceTest` (`@QuarkusTest`).
  - Méthodes :
    - `getShareInfo_byCreator_returnsCode()` — happy path.
    - `getShareInfo_byAcceptedCoOrg_returnsCode()` — co-org accepté.
    - `getShareInfo_byAdmin_returnsCode()` — admin bypass.
    - `getShareInfo_byOtherUserOnPublished_throwsForbidden()` — Event PUBLISHED, caller ni creator ni co-org ni admin → 403.
    - `getShareInfo_byOtherUserOnDraft_throws404()` — anti-oracle ISSUE-92 sur DRAFT.
    - `getShareInfo_byOtherUserOnBanned_throws404()` — anti-oracle sur BANNED.
    - `getShareInfo_doesNotPersistShareCode_whenForbidden()` — assertion clé : si la garde lève 403/404, `Event.shareCode` reste `null` post-rollback.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -am -DskipITs verify -T 1
  ```
- **Doc à mettre à jour** : aucune (l'invariant ISSUE-92 est déjà documenté dans `backend/AGENTS.md`).
- **Commit attendu** :
  ```
  fix(event): apply ISSUE-92 anti-oracle guard on /events/{id}/share (Étape 24.1.2, A2)
  ```

---

#### Étape 24.1.3 — A16 : ADR-002 formalise Décision J `/events/{id}/organizer-uuids` `@PermitAll`

- **Finding** : A16 (code-reviewer Décision G à confirmer + pr-test-analyzer I-5).
- **Sévérité** : IMPORTANT (acter formellement plutôt que durcir).
- **Décision** : Décision J — status quo accepté. Cf. § 0.4.
- **Fichiers touchés** :
  - `backend/docs/adr/ADR-002-organizer-uuids-permitall.md` (nouveau).
  - `backend/services/event-service/src/main/java/ch/unige/events/event/resource/EventResource.java` (JavaDoc enrichie pour pointer vers ADR-002 — pas de changement code).
- **Patch (nouveau fichier ADR-002)** :

  ```markdown
  # ADR-002 — `GET /events/{id}/organizer-uuids` reste `@PermitAll`

  | Champ | Valeur |
  |---|---|
  | Date | 2026-05-10 |
  | Status | Accepted |
  | Author | Backend / Étape 24.1.3 finalization-pre-merge |
  | Review reference | Item A16 review consolidée multi-agent PR #158 |

  ## Préambule (FR / EN)

  - **FR** — Cet ADR formalise la décision de laisser l'endpoint
    `GET /events/{id}/organizer-uuids` annoté `@PermitAll` au lieu de le marquer
    `@Internal` (filter `X-Internal-Token`). Décision actée pour la clôture de la
    PR #158 (migration backend monolithe → 5 microservices).
  - **EN** — This ADR formalizes the decision to keep the `GET
    /events/{id}/organizer-uuids` endpoint as `@PermitAll` rather than marking it
    `@Internal` (X-Internal-Token filter). Acted at PR #158 closure (backend
    monolith → 5 microservices migration).

  ## Context

  Le seul consommateur de `/events/{id}/organizer-uuids` est `engagement-service`
  (`CommentService.computeOrganizerUserIds`, `ReportService.bulkFetchEvents`). Il
  appelle d'abord `GET /events/{id}?check-co-org-of={callerUuid}` qui applique la
  cascade ISSUE-92 (404 sur Event DRAFT/CANCELLED/EXPIRED non-creator) +
  SCRUM-136 (`coOrganizerOf: bool` dans la réponse).

  La review consolidée (item A16, code-reviewer Décision G) signale que
  l'endpoint, bien que documenté interne dans `internal-endpoints.md`, n'est pas
  protégé par `@Internal`. Un anonyme peut donc invoquer
  `/events/123/organizer-uuids` et énumérer les UUIDs des co-organisateurs.

  ## Decision

  L'endpoint reste **`@PermitAll`**. Aucune annotation `@Internal` n'est ajoutée.
  Aucune route Kong-strip n'est posée.

  ## Why this is necessary

  - **Couplage de protection unique inutile.** Le consommateur (engagement-service)
    a déjà passé la garde ISSUE-92 + SCRUM-136 via `getById?check-co-org-of=`
    avant d'invoquer `getOrganizerUuids`. La sortie de `getOrganizerUuids` n'est
    exploitable métier que par un caller qui a déjà passé la garde de visibilité
    en amont.
  - **Stabilité cross-service.** Marquer cet endpoint `@Internal` couplerait la
    liste des co-organisateurs à la disponibilité du header `X-Internal-Token`
    côté consommateur, alors que la primitive est stable cross-service depuis
    post-consolidation 14→5 (Étape 23.2).
  - **Mitigations en place.**
    1. `getOrganizerUuids` filtre les Events `BANNED` (404).
    2. UUIDs retournés non corrélables à des comptes Auth0 sans accès
       `user-service`.
    3. Pagination naturelle borne l'exfiltration (1 event ≤ 10 co-orgs).
    4. Sentinel test (Étape 24.7.4) pin l'invariant « `getOrganizerUuids` ne
       retourne **jamais** les UUIDs si l'Event est `BANNED`, indépendamment du
       caller ».

  ## Alternatives considered

  | Alternative | Rejet |
  |---|---|
  | Ajouter `@Internal` + Kong strip | Couple deux services par un secret partagé sans bénéfice métier ; le consommateur appelle déjà un endpoint sécurisé en amont. |
  | Restreindre à `@RolesAllowed("ADMIN")` | Casse engagement-service qui n'a pas de claim ADMIN sur ses appels REST cross-service. |
  | Supprimer l'endpoint et inliner la primitive dans `getById` | Coût de refactor élevé sans bénéfice de sécurité (l'info partirait quand même par le wire). |

  ## When to revisit

  - Si engagement-service cesse d'être le seul consommateur (ex. SCRUM-99
    notification-service) ; alors évaluer si le nouveau consommateur passe aussi
    par la garde ISSUE-92 amont.
  - Si une nouvelle classe de pentest émerge où l'énumération des UUIDs des
    co-organisateurs devient un risque démontré (ex. social engineering ciblé
    par UUID).

  ## Consequences

  - **Test sentinel obligatoire.** Étape 24.7.4 pin l'invariant filtre BANNED.
    Toute régression future qui retournerait les UUIDs sur un Event BANNED
    casse le test.
  - **Documentation interne-endpoints.** `backend/docs/internal-endpoints.md`
    référence ADR-002 dans la sous-section « `GET /events/{id}/organizer-uuids` »
    (mise à jour Étape 24.9.16).
  ```

- **Test sentinel ajouté** : reporté à Étape 24.7.4 (C-extra) — sentinel ADR-002.
- **Validation** :
  ```bash
  test -f backend/docs/adr/ADR-002-organizer-uuids-permitall.md
  grep -l 'ADR-002' backend/services/event-service/src/main/java/ch/unige/events/event/resource/EventResource.java
  ```
- **Doc à mettre à jour** : `backend/docs/internal-endpoints.md` (Étape 24.9.16).
- **Commit attendu** :
  ```
  docs(backend): add ADR-002 formalizing /events/{id}/organizer-uuids @PermitAll (Étape 24.1.3, A16)
  ```

---

### Vague 2 — Sentinels TDD

> **Pattern TDD rappel.** Les 3 sentinels ci-dessous pinent des bugs **déjà fixés**. Procédure : (1) écrire le test, (2) vérifier qu'il passe vert, (3) commenter temporairement le fix code pour confirmer rouge, (4) restaurer le code et commiter le test seul.

#### Étape 24.2.1 — C1 : Sentinel EVENT-DELETE-001 cascade EventCoOrganizer/Favorite/EventView

- **Finding** : C1 (pr-test-analyzer B-1).
- **Sévérité** : BLOQUANT (sentinel manquant pour bug fixé en Étape 23.2.3).
- **Fichier ajouté/modifié** :
  - `backend/services/event-service/src/test/java/ch/unige/events/event/sentinels/EventDomainSentinelsTest.java` (ajouter méthode).
- **Patch (test à ajouter)** :

  ```java
  @Test
  @DisplayName("EVENT-DELETE-001: deleting an event purges EventCoOrganizer + Favorite + EventView rows (cascade)")
  @TestTransaction
  void delete_cascadesAllChildRows() {
      // Given: a CANCELLED event with 2 co-orgs (1 PENDING, 1 ACCEPTED), 3 favorites, 5 views.
      Event event = TestFixtures.cancelledEvent();
      event.persist();
      Long eventId = event.id;
      UUID creatorId = event.creatorId;

      EventCoOrganizer pendingCoOrg = new EventCoOrganizer();
      pendingCoOrg.eventId = eventId;
      pendingCoOrg.userId = UUID.randomUUID();
      pendingCoOrg.status = CoOrganizerStatus.PENDING;
      pendingCoOrg.persist();

      EventCoOrganizer acceptedCoOrg = new EventCoOrganizer();
      acceptedCoOrg.eventId = eventId;
      acceptedCoOrg.userId = UUID.randomUUID();
      acceptedCoOrg.status = CoOrganizerStatus.ACCEPTED;
      acceptedCoOrg.persist();

      for (int i = 0; i < 3; i++) {
          Favorite fav = new Favorite();
          fav.eventId = eventId;
          fav.userId = UUID.randomUUID();
          fav.persist();
      }
      for (int i = 0; i < 5; i++) {
          EventView v = new EventView();
          v.eventId = eventId;
          v.userId = UUID.randomUUID();
          v.persist();
      }

      // When: creator deletes the event.
      eventService.delete(eventId, creatorId.toString(), false);

      // Then: event + all children are gone.
      assertThat(Event.findById(eventId)).isNull();
      assertThat(EventCoOrganizer.count("eventId = ?1", eventId)).isZero();
      assertThat(Favorite.count("eventId = ?1", eventId)).isZero();
      assertThat(EventView.count("eventId = ?1", eventId)).isZero();
  }
  ```

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest=EventDomainSentinelsTest#delete_cascadesAllChildRows test
  ```
  Doit passer vert. Pour confirmer la valeur de pinning, commenter localement les 3 lignes `entityManager.createQuery("DELETE FROM ...")` dans `EventService.delete:451-456`, relancer → doit passer rouge. **Restaurer avant commit.**
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  test(event): pin EVENT-DELETE-001 cascade EventCoOrganizer/Favorite/EventView (Étape 24.2.1, C1)
  ```

---

#### Étape 24.2.2 — C2 : Sentinel BUG-006-bis branche `catch (PersistenceException)`

- **Finding** : C2 (pr-test-analyzer B-2).
- **Sévérité** : BLOQUANT (sentinel manquant pour bug fixé en Étape 23.2.2).
- **Fichier ajouté/modifié** :
  - `backend/services/event-service/src/test/java/ch/unige/events/event/favorite/service/FavoriteServiceTest.java` (ajouter méthode).
- **Patch (test à ajouter)** :

  ```java
  @Test
  @DisplayName("BUG-006-bis: concurrent double-tap triggers ConstraintViolationException, swallowed idempotently")
  void addFavorite_concurrentDoubleTap_isIdempotentNoop() throws Exception {
      Long eventId = TestFixtures.publishedEvent().id;
      UUID userId = UUID.randomUUID();

      // Use a CountDownLatch so both threads call addFavorite() concurrently
      // → exactly one thread wins the unique-index, the other catches
      // ConstraintViolationException and returns silently.
      ExecutorService pool = Executors.newFixedThreadPool(2);
      CountDownLatch start = new CountDownLatch(1);
      CompletableFuture<Void> a = CompletableFuture.runAsync(() -> {
          try { start.await(); favoriteService.addFavorite(userId, eventId); }
          catch (Exception e) { throw new RuntimeException(e); }
      }, pool);
      CompletableFuture<Void> b = CompletableFuture.runAsync(() -> {
          try { start.await(); favoriteService.addFavorite(userId, eventId); }
          catch (Exception e) { throw new RuntimeException(e); }
      }, pool);
      start.countDown();
      CompletableFuture.allOf(a, b).get(5, TimeUnit.SECONDS);

      // Both calls returned without throwing → branch coverage of the catch.
      // And exactly one row exists in DB → idempotence respected.
      assertThat(Favorite.count("userId = ?1 AND eventId = ?2", userId, eventId)).isOne();
      pool.shutdown();
  }
  ```

  **Note d'implémentation** : ce test requiert DevServices Postgres (pas H2, qui ne respecte pas le pattern `unique constraint` natif Postgres). Si le test reste flaky (les deux threads passent en série naturellement à cause du pool), une variante mock du `EntityManager.persist` retournant null une fois puis throwing `PersistenceException` peut être utilisée, mais préférer la version concurrente qui exerce vraiment la branche en intégration.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest=FavoriteServiceTest#addFavorite_concurrentDoubleTap_isIdempotentNoop test
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  test(event): pin BUG-006-bis catch ConstraintViolationException branch (Étape 24.2.2, C2)
  ```

---

#### Étape 24.2.3 — C3 : Sentinel BUG-005-bis advisory lock concurrent

- **Finding** : C3 (pr-test-analyzer B-3).
- **Sévérité** : BLOQUANT (sentinel manquant pour bug fixé en Étape 23.2.1).
- **Fichier ajouté/modifié** :
  - `backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/service/AttendanceServiceConcurrencyTest.java` (nouveau, `@QuarkusTest` avec DevServices Postgres).
- **Patch (test complet)** :

  ```java
  package ch.unige.events.engagement.attendance.service;

  import io.quarkus.test.junit.QuarkusTest;
  import jakarta.inject.Inject;
  import org.junit.jupiter.api.DisplayName;
  import org.junit.jupiter.api.Test;

  import java.util.UUID;
  import java.util.concurrent.*;
  import java.util.concurrent.atomic.AtomicInteger;

  import static org.assertj.core.api.Assertions.assertThat;

  /**
   * BUG-005-bis sentinel — advisory lock + capacity gating.
   *
   * <p>FR — Pin l'invariant que sur un burst concurrent de N attend() avec
   * capacity=K, exactement K placements ATTENDING et N-K placements WAITLISTED
   * sont créés. Sans pg_advisory_xact_lock, les checks de capacité racent et
   * peuvent persister K+1 ATTENDING.
   *
   * <p>EN — Pins that on a concurrent burst of N attend() with capacity=K,
   * exactly K ATTENDING and N-K WAITLISTED rows are persisted. Without
   * pg_advisory_xact_lock, the capacity checks race and may persist K+1
   * ATTENDING.
   */
  @QuarkusTest
  class AttendanceServiceConcurrencyTest {

      @Inject AttendanceService attendanceService;
      @Inject TestFixtures fixtures;

      @Test
      @DisplayName("BUG-005-bis: concurrent burst of 10 attend() on capacity=3 yields exactly 3 ATTENDING + 7 WAITLISTED")
      void attend_concurrentBurst_respectsCapacity() throws Exception {
          Long eventId = fixtures.publishedEventWithCapacity(3);

          int N = 10;
          ExecutorService pool = Executors.newFixedThreadPool(N);
          CountDownLatch start = new CountDownLatch(1);
          CountDownLatch done = new CountDownLatch(N);
          AtomicInteger errors = new AtomicInteger();

          for (int i = 0; i < N; i++) {
              UUID userId = UUID.randomUUID();
              String auth0Id = "auth0|" + userId;
              fixtures.ensureUser(userId, auth0Id);
              pool.submit(() -> {
                  try {
                      start.await();
                      attendanceService.attend(auth0Id, eventId);
                  } catch (Exception e) {
                      errors.incrementAndGet();
                  } finally {
                      done.countDown();
                  }
              });
          }
          start.countDown();
          assertThat(done.await(15, TimeUnit.SECONDS)).isTrue();
          assertThat(errors).hasValue(0);
          pool.shutdown();

          // Then: exactly 3 ATTENDING + 7 WAITLISTED, never 4 ATTENDING.
          long attending = Attendance.count(
                  "eventId = ?1 AND status = ?2", eventId, AttendanceStatus.ATTENDING);
          long waitlisted = Attendance.count(
                  "eventId = ?1 AND status = ?2", eventId, AttendanceStatus.WAITLISTED);
          assertThat(attending).isEqualTo(3L);
          assertThat(waitlisted).isEqualTo(7L);
      }
  }
  ```

  **Note** : `TestFixtures.publishedEventWithCapacity(int)` + `ensureUser(UUID, String)` peuvent nécessiter ajout dans `engagement-service/src/test/java/.../TestFixtures.java`. Si la classe n'existe pas encore dans engagement-service, créer une fixture minimale qui crée un Event publié via REST client mock + insère l'attendance Auth0 en DB.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/engagement-service -Dtest=AttendanceServiceConcurrencyTest test
  ```
  Doit passer vert. Pour confirmer pinning, commenter `entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")...` dans `AttendanceService.attend()` → relancer → assertion `attending == 3` doit passer rouge (typiquement ≥ 4 ATTENDING). **Restaurer avant commit.**
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  test(engagement): pin BUG-005-bis advisory lock concurrent burst (Étape 24.2.3, C3)
  ```

---

### Vague 3 — Observabilité silent failures

#### Étape 24.3.1 — A5 : Logging des 8 fallbacks REST clients

- **Finding** : A5 (silent-failure-hunter CRIT-1).
- **Sévérité** : CRITIQUE.
- **Fichiers touchés** :
  - `backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/client/EventServiceClient.java` (4 fallbacks).
  - `backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/client/UserServiceClient.java` (1 fallback).
  - `backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/client/EngagementServiceClient.java` (3 fallbacks).
- **Patch (pattern à appliquer aux 8 méthodes)** :

  ```java
  // EventServiceClient.java — exemple
  default EventDTO getByIdFallback(long id) {
      Log.warnf("[REST_FALLBACK_event-service] getById(%d) — returning null (downstream unavailable, callers must treat as 404)", id);
      return null;
  }

  default List<EventDTO> findByIdsFallback(List<Long> ids, String status) {
      Log.warnf("[REST_FALLBACK_event-service] findByIds(ids=%d, status=%s) — returning empty list (downstream unavailable, enrichment degraded)", ids.size(), status);
      return List.of();
  }

  default UUID[] getOrganizerUuidsFallback(long eventId) {
      Log.warnf("[REST_FALLBACK_event-service] getOrganizerUuids(%d) — returning empty array (downstream unavailable, organizer-only checks degraded)", eventId);
      return new UUID[0];
  }

  default EventDTO getByIdWithCoOrgCheckFallback(long id, UUID checkUuid) {
      Log.warnf("[REST_FALLBACK_event-service] getByIdWithCoOrgCheck(%d, %s) — returning null (downstream unavailable)", id, checkUuid);
      return null;
  }

  // EngagementServiceClient.java
  default AttendanceSummary getAttendanceSummaryFallback(long eventId) {
      Log.warnf("[REST_FALLBACK_engagement-service] getAttendanceSummary(%d) — returning AttendanceSummary.of(0, 0) (counts will display as 0)", eventId);
      return AttendanceSummary.of(0L, 0L);
  }

  default Map<Long, AttendanceSummary> getAttendanceSummariesBulkFallback(List<Long> ids) {
      Log.warnf("[REST_FALLBACK_engagement-service] getAttendanceSummariesBulk(ids=%d) — returning empty map (counts will display as 0)", ids.size());
      return Map.of();
  }

  default List<UUID> getAttendingUserIdsFallback(long eventId) {
      Log.warnf("[REST_FALLBACK_engagement-service] getAttendingUserIds(%d) — returning empty list (downstream unavailable)", eventId);
      return List.of();
  }

  // UserServiceClient.java
  default UserPublicResponse getByIdFallback(UUID id) {
      Log.warnf("[REST_FALLBACK_user-service] getById(%s) — returning null (downstream unavailable, comments/reports will display anonymized author)", id);
      return null;
  }
  ```

  Imports à ajouter en tête de chaque fichier : `import io.quarkus.logging.Log;`.

- **Test sentinel ajouté** : reporté à Étape 24.7.2 (C5) — câblage `@Fallback` réel.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/shared-domain-dtos -DskipITs verify
  grep -rn 'REST_FALLBACK_' backend/services/shared-domain-dtos/src/main/java/ | wc -l  # doit être 8
  ```
- **Doc à mettre à jour** : aucune (les logs sont auto-documentés).
- **Commit attendu** :
  ```
  fix(shared-domain-dtos): add observability logs to all 8 REST client fallbacks (Étape 24.3.1, A5)
  ```

---

#### Étape 24.3.2 — A6 : `safeGetUser` × 4 — catch typé NotFoundException + log warn

- **Finding** : A6 (silent-failure-hunter CRIT-2).
- **Sévérité** : CRITIQUE.
- **Fichiers touchés (4 occurrences quasi-identiques)** :
  - `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java:319-328`
  - `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java:270-281`
  - `backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerService.java:225-234`
  - `backend/services/moderation-service/src/main/java/ch/unige/events/report/service/ReportService.java:217-226`
- **Patch (pattern à appliquer aux 4 sites)** :

  ```java
  // AVANT
  private UserPublicResponse safeGetUser(UUID userId) {
      if (userId == null) {
          return null;
      }
      try {
          return userClient.getById(userId);
      } catch (RuntimeException e) {
          return null;
      }
  }

  // APRÈS
  private UserPublicResponse safeGetUser(UUID userId) {
      if (userId == null) {
          return null;
      }
      try {
          return userClient.getById(userId);
      } catch (jakarta.ws.rs.NotFoundException e) {
          // Semantic absence: user was hard-deleted or never existed. Caller
          // already treats null as "anonymous author" — no log needed.
          return null;
      } catch (RuntimeException e) {
          // Infra failure (timeout, CB open, JSON parse error, etc.). Log so
          // ops can correlate degraded enrichment to a downstream incident.
          Log.warnf(e, "[USER_ENRICHMENT_FAIL] safeGetUser(%s) — returning null (degraded enrichment due to downstream failure)", userId);
          return null;
      }
  }
  ```

  Import à ajouter (si absent) : `import io.quarkus.logging.Log;`.

- **Test sentinel** : couvert indirectement par Étape 24.7.2 (tests `@Fallback` REST clients) + tests existants `safeGetUser_returnsNullOnFailure_*`. Pas de nouveau test dédié.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/engagement-service,services/event-service,services/moderation-service -am -DskipITs verify -T 1
  grep -rn 'USER_ENRICHMENT_FAIL' backend/services/ | wc -l  # doit être 4
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(backend): differentiate semantic 404 vs infra failure in safeGetUser (Étape 24.3.2, A6)
  ```

---

#### Étape 24.3.3 — A7 : `Auth0IdResolver.resolveUserUuid` log sur claim malformée

- **Finding** : A7 (silent-failure-hunter CRIT-3).
- **Sévérité** : CRITIQUE.
- **Fichier touché** :
  - `backend/services/shared-domain-projections/src/main/java/ch/unige/events/shared/domain/projections/Auth0IdResolver.java:54-58`
- **Patch** :

  ```java
  // AVANT
  try {
      return UUID.fromString(raw.toString());
  } catch (IllegalArgumentException e) {
      return null;
  }

  // APRÈS
  try {
      return UUID.fromString(raw.toString());
  } catch (IllegalArgumentException e) {
      Log.warnf(
          "[AUTH0_UUID_MALFORMED] JWT claim 'uuid'=%s does not parse — caller treated as anonymous (sub=%s)",
          raw, jwt.getName()
      );
      return null;
  }
  ```

  Import : `import io.quarkus.logging.Log;`.

- **Test sentinel ajouté** :
  - Classe : `Auth0IdResolverTest` (existante, ajouter méthode).
  - Méthode : `resolveUserUuid_malformedClaim_logsWarnAndReturnsNull()` — utiliser un capteur de logs (Quarkus `LogCapture` ou `@TestLogCaptureExtension`) pour asserter que `[AUTH0_UUID_MALFORMED]` est émis.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/shared-domain-projections -DskipITs verify
  grep -rn 'AUTH0_UUID_MALFORMED' backend/services/shared-domain-projections/src/ | wc -l  # ≥ 2 (impl + test)
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(shared-domain-projections): log Auth0 uuid claim parse failure (Étape 24.3.3, A7)
  ```

---

#### Étape 24.3.4 — A8 : `EventBannedConsumer` → `Log.warnf` avec errorId `MOD_BAN_LOST`

- **Finding** : A8 (silent-failure-hunter CRIT-4).
- **Sévérité** : CRITIQUE.
- **Fichier touché** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/kafka/EventBannedConsumer.java:25-29`
- **Patch** :

  ```java
  // AVANT
  Event event = Event.<Event>findByIdOptional(ev.eventId()).orElse(null);
  if (event == null) {
      Log.infof("events.banned: event %d not found locally — skipping (deleted before consume?)", ev.eventId());
      return;
  }

  // APRÈS
  Event event = Event.<Event>findByIdOptional(ev.eventId()).orElse(null);
  if (event == null) {
      // [MOD_BAN_LOST] elevated to WARN: legitimate when an event was deleted
      // between the moderation ban and the consume, but also fires when the
      // local DB diverges from the source of truth (replica drift, schema
      // mismatch, wrong DB pointed). Operators should investigate volumes.
      Log.warnf("[MOD_BAN_LOST] events.banned: event %d not found locally — skipping (deleted upstream or DB divergence?)", ev.eventId());
      return;
  }
  ```

- **Test sentinel** : `EventBannedConsumerTest.onBanned_unknownEvent_logsWarnWithMOD_BAN_LOST()` — modifier le test existant `_silentlyIgnored` ou ajouter un test parallèle qui asserte la présence du log warn.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest=EventBannedConsumerTest test
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(event): elevate EventBannedConsumer missing event log to WARN with MOD_BAN_LOST id (Étape 24.3.4, A8)
  ```

---

#### Étape 24.3.5 — A9 : `EventService.delete` log d'audit cascade

- **Finding** : A9 (silent-failure-hunter CRIT-5).
- **Sévérité** : CRITIQUE.
- **Fichier touché** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java:451-457`
- **Patch** :

  ```java
  // AVANT
  entityManager.createQuery("DELETE FROM Favorite f WHERE f.eventId = :id")
          .setParameter("id", id).executeUpdate();
  entityManager.createQuery("DELETE FROM EventView v WHERE v.eventId = :id")
          .setParameter("id", id).executeUpdate();
  entityManager.createQuery("DELETE FROM EventCoOrganizer co WHERE co.eventId = :id")
          .setParameter("id", id).executeUpdate();
  event.delete();

  // APRÈS
  int favs = entityManager.createQuery("DELETE FROM Favorite f WHERE f.eventId = :id")
          .setParameter("id", id).executeUpdate();
  int views = entityManager.createQuery("DELETE FROM EventView v WHERE v.eventId = :id")
          .setParameter("id", id).executeUpdate();
  int coOrgs = entityManager.createQuery("DELETE FROM EventCoOrganizer co WHERE co.eventId = :id")
          .setParameter("id", id).executeUpdate();
  event.delete();
  Log.infof(
      "[EVENT_DELETE_CASCADE] event=%d caller=%s favorites=%d views=%d coOrgs=%d",
      id, callerAuth0Id, favs, views, coOrgs
  );
  ```

  Note : ajouter `String callerAuth0Id` à la signature si absent (ou réutiliser un paramètre existant). Si la méthode `delete()` est `(Long id, String auth0Id, boolean isAdmin)`, utiliser `auth0Id` directement.

- **Test sentinel** : étendre `EventDomainSentinelsTest.delete_cascadesAllChildRows` (ajouté en C1) pour asserter la présence du log (capteur).
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest='EventDomainSentinelsTest#delete_cascadesAllChildRows' test
  grep -n 'EVENT_DELETE_CASCADE' backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java | wc -l  # ≥ 1
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(event): emit audit log on EventService.delete cascade (Étape 24.3.5, A9)
  ```

---

#### Étape 24.3.6 — A12 : `AttendanceService.removeAttendance` log skip waitlist promotion

- **Finding** : A12 (silent-failure-hunter IMP-4).
- **Sévérité** : IMPORTANT.
- **Fichier touché** :
  - `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java:155-167`
- **Patch** :

  ```java
  // AVANT
  ch.unige.events.shared.domain.dto.EventDTO event = eventClient.getById(eventId);

  attendance.delete();

  if (removed != AttendanceStatus.ATTENDING
          || event == null
          || event.capacity() == null
          ...) {
      return;
  }

  // APRÈS
  ch.unige.events.shared.domain.dto.EventDTO event = eventClient.getById(eventId);

  attendance.delete();

  if (event == null) {
      Log.warnf(
          "[WAITLIST_PROMOTION_SKIPPED] event-service unreachable for event=%d — promotion deferred until next attend/leave",
          eventId
      );
      return;
  }
  if (removed != AttendanceStatus.ATTENDING
          || event.capacity() == null
          ...) {
      return;
  }
  ```

- **Test sentinel** : `AttendanceServiceTest.removeAttendance_eventClientReturnsNull_logsAndSkipsPromotion()` — mock `eventClient.getById` retournant null, asserter pas de promotion + log.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/engagement-service -Dtest=AttendanceServiceTest test
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(engagement): log waitlist promotion skip when event-service unreachable (Étape 24.3.6, A12)
  ```

---

#### Étape 24.3.7 — A13 : `MdcKafkaProducerInterceptor.onAcknowledgement` + JavaDoc property

- **Finding** : A13 (silent-failure-hunter IMP-5 + comment-analyzer B4).
- **Sévérité** : IMPORTANT.
- **Fichiers touchés** :
  - `backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaProducerInterceptor.java`
  - `backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/MdcKafkaConsumerInterceptor.java` (JavaDoc seulement, aucun code change)
- **Patch (Producer)** :

  ```java
  @Override
  public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
      if (exception == null) return;
      // Best-effort: the producer record is no longer available, but log
      // explicitly so a Kafka publish failure is never silent. The MDC of
      // the calling thread (if any) will be attached automatically.
      Log.warnf(exception,
          "[KAFKA_PRODUCE_FAIL] topic=%s partition=%d — record will not reach consumers",
          metadata != null ? metadata.topic() : "(unknown)",
          metadata != null ? metadata.partition() : -1
      );
  }
  ```

- **Patch (JavaDoc des deux interceptors lignes 18-21)** : remplacer
  ```
  mp.messaging.outgoing.<channel>.kafka.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaProducerInterceptor
  ```
  par
  ```
  mp.messaging.outgoing.<channel>.interceptor.classes=ch.unige.events.shared.tracing.MdcKafkaProducerInterceptor
  ```

  (idem pour consumer : `mp.messaging.incoming.<channel>.interceptor.classes=...`).

- **Test sentinel** : `MdcKafkaProducerInterceptorTest.onAcknowledgement_withException_logsWarn()` — capteur log.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/shared-tracing -DskipITs verify
  grep -n 'kafka.interceptor.classes' backend/services/shared-tracing/src/main/java/ | wc -l  # doit être 0
  ```
- **Doc à mettre à jour** : aucune (la JavaDoc est elle-même la doc).
- **Commit attendu** :
  ```
  fix(shared-tracing): log Kafka produce failure + correct interceptor.classes JavaDoc (Étape 24.3.7, A13)
  ```

---

#### Étape 24.3.8 — A14 : `FileStorageService.init` policy fail → `Log.errorf` + errorId

- **Finding** : A14 (silent-failure-hunter IMP-6).
- **Sévérité** : IMPORTANT.
- **Fichier touché** :
  - `backend/services/shared-storage/src/main/java/ch/unige/events/shared/storage/FileStorageService.java:77-84`
- **Patch** :

  ```java
  // AVANT
  try {
      s3.putBucketPolicy(...);
  } catch (Exception e) {
      Log.warnf(e, "Failed to apply bucket policy to '%s'", config.s3Bucket());
  }

  // APRÈS
  try {
      s3.putBucketPolicy(...);
  } catch (Exception e) {
      Log.errorf(e,
          "[S3_POLICY_APPLY_FAIL] Failed to apply public-read bucket policy to '%s' — uploaded images will be private (403 on CDN). Operators must verify Minio/S3 access policy.",
          config.s3Bucket()
      );
  }
  ```

- **Test sentinel** : `FileStorageServiceTest.init_policyFails_logsErrorWithS3_POLICY_APPLY_FAIL()` — mock S3 client throwing.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/shared-storage -DskipITs verify
  grep -n 'S3_POLICY_APPLY_FAIL' backend/services/shared-storage/src/main/java/ | wc -l  # ≥ 1
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(shared-storage): elevate S3 policy apply failure to ERROR with errorId (Étape 24.3.8, A14)
  ```

---

### Vague 4 — Kafka outbox EventBanned

#### Étape 24.4.1 — A10 : Bridges Kafka — resserrement catch + upgrade log + outbox EventBanned

- **Finding** : A10 (silent-failure-hunter IMP-1 + MIN-6).
- **Sévérité** : IMPORTANT (CRITIQUE pour `EventBannedPublisher`).
- **Fichiers touchés** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/kafka/EventLifecyclePublisher.java`
  - `backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/kafka/CoOrganizerPublisher.java`
  - `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/kafka/CommentCreatedPublisher.java`
  - `backend/services/user-service/src/main/java/ch/unige/events/user/follow/kafka/FollowLifecyclePublisher.java`
  - `backend/services/moderation-service/src/main/java/ch/unige/events/report/kafka/EventBannedPublisher.java` (sécurité-critique)
  - `backend/services/moderation-service/src/main/resources/db/migration/V18__create_event_outbox.sql` (nouveau, optionnel cf. ci-dessous)
  - `backend/services/moderation-service/src/main/java/ch/unige/events/report/outbox/EventBannedOutbox.java` (nouvelle entité, optionnel)
  - `backend/services/moderation-service/src/main/java/ch/unige/events/report/outbox/EventBannedOutboxPoller.java` (nouveau scheduler, optionnel)
  - `backend/docs/adr/ADR-003-event-banned-outbox-vs-best-effort.md` (nouveau, recommandé)

- **Sous-étape A10-1 — Resserrement catch + upgrade log sur 5 publishers** :

  ```java
  // PATTERN à appliquer aux 5 publishers (sauf EventBannedPublisher qui va plus loin en A10-2)
  try {
      emitter.send(payload);
  } catch (RuntimeException | java.util.concurrent.RejectedExecutionException e) {
      Log.errorf(e,
          "[KAFKA_PUBLISH_FAIL_<channel>] Failed to publish %s for event %d — downstream consumers will not see this transition",
          payload.type(), payload.eventId()
      );
      // Note: post-commit, no rollback possible. The event is lost; this log
      // is the only signal an operator gets. Do NOT swallow further.
  }
  ```

  Remplacer `<channel>` par le nom du topic (ex. `events_lifecycle`, `events_co_organizer`, `comments_created`, `follow_lifecycle`).

- **Sous-étape A10-2 — Pattern outbox pour `EventBannedPublisher` (sécurité-critique)** :

  **Décision K** (formalisée dans ADR-003, créé en sous-étape A10-3) : implémenter le pattern outbox transactionnel pour `events.banned` uniquement. Pour les 4 autres publishers, l'`Log.errorf` + métrique implicite suffit (DB = book of record, retry manuel admin possible).

  Rationale outbox `EventBannedPublisher` : un ban modération non publié signifie qu'un Event reste publiquement visible alors que la modération l'a banni — bug de sécurité fonctionnelle. Le pattern outbox garantit at-least-once delivery via une table SQL transactionnelle.

  **Implémentation** :

  1. **V18__create_event_outbox.sql** dans `moderation-service` :

  ```sql
  -- V18: outbox table for events.banned to guarantee at-least-once delivery.
  -- Acted in PR #158 Étape 24.4.1 (Décision K — ADR-003).
  CREATE TABLE event_banned_outbox (
      id            BIGSERIAL PRIMARY KEY,
      event_id      BIGINT NOT NULL,
      banned_by     UUID,
      occurred_at   TIMESTAMP WITH TIME ZONE NOT NULL,
      payload_json  JSONB NOT NULL,
      published_at  TIMESTAMP WITH TIME ZONE,
      attempts      INT NOT NULL DEFAULT 0,
      last_error    TEXT,
      created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
  );
  CREATE INDEX idx_event_banned_outbox_unpublished
      ON event_banned_outbox (created_at) WHERE published_at IS NULL;
  ```

  2. **`EventBannedOutbox` entity** (Panache) :

  ```java
  @Entity
  @Table(name = "event_banned_outbox")
  public class EventBannedOutbox extends PanacheEntity {
      public Long eventId;
      public UUID bannedBy;
      public Instant occurredAt;
      @Column(columnDefinition = "jsonb")
      public String payloadJson;
      public Instant publishedAt;
      public int attempts;
      public String lastError;
      public Instant createdAt;
  }
  ```

  3. **`EventBannedKafkaBridge` modifié** : ne publie plus directement, persiste dans l'outbox dans la même transaction que la mise à jour du Report :

  ```java
  void onBanned(@Observes(during = TransactionPhase.AFTER_SUCCESS) EventBannedEvent ev) {
      EventBannedOutbox row = new EventBannedOutbox();
      row.eventId = ev.eventId();
      row.bannedBy = ev.bannedBy();
      row.occurredAt = ev.occurredAt();
      row.payloadJson = jsonb.toJson(ev);
      row.attempts = 0;
      row.persist();
      // Publication async via EventBannedOutboxPoller — not in this thread.
  }
  ```

  4. **`EventBannedOutboxPoller`** (`@Scheduled(every = "10s")`) :

  ```java
  @ApplicationScoped
  public class EventBannedOutboxPoller {
      @Inject @Channel("events_banned_out") Emitter<EventBannedEvent> emitter;

      @Scheduled(every = "10s")
      @Transactional
      public void publishPending() {
          List<EventBannedOutbox> pending = EventBannedOutbox
              .find("publishedAt IS NULL ORDER BY id ASC", PageRequest.of(0, 100))
              .list();
          for (EventBannedOutbox row : pending) {
              try {
                  EventBannedEvent ev = jsonb.fromJson(row.payloadJson, EventBannedEvent.class);
                  emitter.send(ev).toCompletableFuture().get(5, TimeUnit.SECONDS);
                  row.publishedAt = Instant.now();
              } catch (Exception e) {
                  row.attempts++;
                  row.lastError = e.toString();
                  Log.errorf(e, "[KAFKA_OUTBOX_PUBLISH_FAIL_events.banned] outbox row %d attempts=%d — will retry next tick", row.id, row.attempts);
              }
          }
      }
  }
  ```

  5. **Helm guard** : `moderation-service` reste à `replicas: 1` strict (cf. ADR-001) — le poller ne tolère pas la concurrence (le `@Scheduled` fire sur chaque pod sans leader election). Si un jour `replicas: 2` est nécessaire, ajouter Shedlock (deferred).

- **Sous-étape A10-3 — ADR-003 décision Décision K outbox vs best-effort** :

  Créer `backend/docs/adr/ADR-003-event-banned-outbox-vs-best-effort.md` avec la rationale détaillée :

  ```markdown
  # ADR-003 — `events.banned` via outbox transactionnel ; les 4 autres topics restent best-effort

  | Champ | Valeur |
  |---|---|
  | Date | 2026-05-10 |
  | Status | Accepted |
  | Author | Backend / Étape 24.4.1 finalization-pre-merge |
  | Review reference | Item A10 review consolidée multi-agent PR #158 (silent-failure-hunter IMP-1) |

  ## Préambule (FR / EN)

  - **FR** — Cet ADR documente le compromis entre durabilité Kafka (outbox)
    et coût d'implémentation pour les 5 publishers AFTER_SUCCESS du backend
    UNIGE Events. Décision actée pour la clôture de la PR #158.
  - **EN** — This ADR records the trade-off between Kafka durability
    (outbox pattern) and implementation cost for the 5 AFTER_SUCCESS
    publishers in the UNIGE Events backend. Acted at PR #158 closure.

  ## Context

  Les 5 publishers Kafka sont câblés via CDI `@Observes(during = AFTER_SUCCESS)` +
  bridge → `Emitter.send(...)`. Si Kafka est indisponible au moment du send,
  l'événement est perdu silencieusement (pas de rollback car le commit JDBC
  a déjà eu lieu).

  La review consolidée (item A10) signale ce risque pour les 5 topics, avec
  une criticité différenciée :
  - `events.banned` : impact sécurité (un ban perdu = event reste publiquement
    visible alors que modéré).
  - `events.lifecycle` (PUBLISHED/CANCELLED/EXPIRED), `events.co_organizer`,
    `comments.created`, `follow.lifecycle` : impact UX (notifications retardées,
    listes désynchronisées) — DB reste source de vérité.

  ## Decision

  - **`events.banned`** : pattern outbox transactionnel via table
    `event_banned_outbox` + poller `@Scheduled(every = "10s")`. At-least-once
    delivery garantie tant que la DB tient.
  - **Les 4 autres topics** : best-effort. `Emitter.send(...)` post-commit avec
    `Log.errorf` + errorId dédié (`[KAFKA_PUBLISH_FAIL_<channel>]`). Pas
    d'outbox.

  ## Why this is necessary

  - Outbox sur `events.banned` : prévient un défaut de sécurité (event modéré
    qui reste visible). Le coût d'implémentation est modéré (1 table, 1 poller,
    ~150 LoC + tests).
  - Best-effort sur les 4 autres : le pattern outbox sur 5 topics multiplierait
    la dette de schéma (5 tables outbox) sans bénéfice métier proportionnel.
    DB reste source de vérité ; un opérateur peut rejouer manuellement via
    admin endpoint en cas d'incident Kafka long.

  ## Alternatives considered

  | Alternative | Rejet |
  |---|---|
  | Outbox sur les 5 topics | Coût implémentation × 5 sans bénéfice (cf. ci-dessus). |
  | Pas d'outbox du tout, juste log error | Insuffisant pour `events.banned` (sécurité). |
  | Debezium CDC sur les tables sources | Out of scope S8 — Doppler infra dependency. Deferred S9+. |
  | Transactional Outbox via SmallRye Reactive Messaging built-in | Pas encore stable en 3.35 ; à réévaluer en S9+. |

  ## Consequences

  - **`moderation-service` reste `replicas: 1` strict** (cf. ADR-001). Le
    poller `EventBannedOutboxPoller` n'a pas de leader election ; si scaling
    nécessaire, ajouter Shedlock (deferred).
  - **Latence** : `events.banned` peut être retardé de ≤ 10 s (poll period).
    Acceptable pour une action de modération.
  - **Accumulation** : `event_banned_outbox.attempts` + `last_error` doivent
    être surveillés (Grafana panel ou requête manuelle). `published_at IS NULL`
    + `attempts > 5` = incident à investiguer.

  ## When to revisit

  - Si la latence `events.banned` (10 s poll) devient un blocker UX.
  - Si Kafka est upgradé à un cluster prod-grade avec exactly-once semantics
    (idempotent producer + transactional read-process-write).
  - Si Debezium CDC ou Outbox SmallRye built-in mûrit en 4.x.
  ```

- **Test sentinel** :
  - `EventBannedOutboxPollerTest.publishPending_kafkaDown_incrementsAttemptsAndLogs()` — mock emitter throwing.
  - `EventBannedOutboxPollerTest.publishPending_kafkaUp_marksPublishedAt()` — happy path.
  - `EventBannedKafkaBridgeTest.onBanned_persistsOutboxRow_notDirectEmitter()` — vérifier que la bridge ne fait plus de `Emitter.send` direct.

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/moderation-service -am -DskipITs verify -T 1
  grep -rn 'KAFKA_PUBLISH_FAIL_' backend/services/*/src/main/java/ | wc -l  # ≥ 4 (les 4 publishers non-outbox)
  test -f backend/services/moderation-service/src/main/resources/db/migration/V18__create_event_outbox.sql
  test -f backend/docs/adr/ADR-003-event-banned-outbox-vs-best-effort.md
  ```

- **Doc à mettre à jour** :
  - `backend/docs/data-model.md` : nouvelle entité `event_banned_outbox` (Étape 24.9.9).
  - `backend/docs/architecture.md` : section observabilité — nouveau pattern outbox événementiel.
  - `backend/docs/sprint-context.md` § Étape 24 (Étape 24.10.1).

- **Commits attendus** (3 commits granularité fine) :
  ```
  fix(backend): tighten Kafka publisher catch + log error with errorId on 4 best-effort topics (Étape 24.4.1-a, A10)

  feat(moderation): add transactional outbox for events.banned via V18 + EventBannedOutboxPoller (Étape 24.4.1-b, A10)

  docs(backend): add ADR-003 events.banned outbox vs best-effort decision (Étape 24.4.1-c, A10)
  ```

---

### Vague 5 — Robustness restant

#### Étape 24.5.1 — A1 : `KAFKA_BOOTSTRAP_SERVERS` env var rename × 4 deployments

- **Finding** : A1 (code-reviewer IMPORTANT-1).
- **Sévérité** : IMPORTANT.
- **Fichiers touchés** :
  - `k8s/chart/templates/event-service/deployment.yaml:49`
  - `k8s/chart/templates/engagement-service/deployment.yaml:46`
  - `k8s/chart/templates/user-service/deployment.yaml:47`
  - `k8s/chart/templates/moderation-service/deployment.yaml:51`
- **Patch (chacun des 4 fichiers)** :

  ```yaml
  # AVANT
  - name: KAFKA_BOOTSTRAP
    value: kafka:9092

  # APRÈS
  - name: KAFKA_BOOTSTRAP_SERVERS
    value: kafka:9092
  ```

- **Validation** :
  ```bash
  helm template k8s/chart/ -f k8s/chart/values.yaml | grep -A1 'name: KAFKA_BOOTSTRAP_SERVERS' | grep -c 'kafka:9092'  # doit être 4 (4 services)
  helm template k8s/chart/ -f k8s/chart/values.yaml | grep -c 'name: KAFKA_BOOTSTRAP$'  # doit être 0
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  chore(infra): align Kafka env var with consumed property name KAFKA_BOOTSTRAP_SERVERS (Étape 24.5.1, A1)
  ```

---

#### Étape 24.5.2 — A3 : `EventExpirationJob` Helm guard `replicas: 1`

- **Finding** : A3 (code-reviewer IMPORTANT-3).
- **Sévérité** : IMPORTANT.
- **Fichiers touchés** :
  - `k8s/chart/templates/event-service/deployment.yaml` (dupliquer le pattern de `moderation-service/deployment.yaml`).
  - `backend/services/event-service/src/main/java/ch/unige/events/event/scheduler/EventExpirationJob.java` (commentaire renforcé).
  - `backend/services/event-service/src/main/java/ch/unige/events/event/service/EventExpirationService.java` (optionnel : `pg_advisory_xact_lock` sur la query candidate).
  - `backend/docs/adr/ADR-001-moderation-cleanup-replicas-strict.md` (mettre à jour pour mentionner aussi event-service).
- **Patch deployment.yaml event-service** :

  ```yaml
  # ─── Helm guard: replicas:1 strict ───────────────────────────────────────
  # event-service hosts EventExpirationJob (@Scheduled every 1h). Without
  # leader-election, scaling out would double-fire events.expired Kafka events
  # and double-update Event.status concurrently. Forbidden until Shedlock is
  # wired (S9+). Cf. ADR-001 (originally moderation-cleanup, now also covers
  # EventExpirationJob — see ADR-001 update Étape 24.5.2).
  {{- if gt (int .Values.eventService.replicas | default 1) 1 }}
  {{- fail "event-service must run at replicas:1 strict — EventExpirationJob has no leader election. Cf. ADR-001." }}
  {{- end }}
  replicas: 1
  ```

- **Patch optionnel sur `EventExpirationService.expireEvents`** :

  ```java
  @Transactional
  public int expireEvents() {
      // Defense in depth: take a global advisory lock to prevent races if the
      // operator ever bumps replicas (the Helm guard fails-fast at install,
      // but in-cluster scale-up bypasses helm). Lock key 0 = "global event
      // expiration scheduler" (no eventId scope).
      entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(0)").getSingleResult();
      // ... rest of expireEvents() unchanged
  }
  ```

- **Patch ADR-001** : ajouter en bas de la section "How this is enforced" un point 6 :

  ```markdown
  6. **`event-service` follows the same constraint** (since Étape 24.5.2,
     A3) — `EventExpirationJob` is also a `@Scheduled` cron without leader
     election. The Helm guard duplicated to `event-service/deployment.yaml`
     fails any install attempting `replicas: 2+`.
  ```

- **Validation** :
  ```bash
  helm template k8s/chart/ -f k8s/chart/values.yaml --set eventService.replicas=2 2>&1 | grep -q 'event-service must run at replicas:1 strict'
  helm template k8s/chart/ -f k8s/chart/values.yaml | grep -A1 'event-service' | grep -c 'replicas: 1'  # ≥ 1
  ```
- **Doc à mettre à jour** : ADR-001 (cf. patch ci-dessus).
- **Commit attendu** :
  ```
  chore(infra): add Helm guard replicas:1 strict for event-service (EventExpirationJob) (Étape 24.5.2, A3)
  ```

---

#### Étape 24.5.3 — A11 : `AttendanceService.acquireAdvisoryLock(null)` → `IllegalStateException`

- **Finding** : A11 (silent-failure-hunter IMP-3).
- **Sévérité** : IMPORTANT.
- **Fichier touché** :
  - `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java:330-337`
- **Patch** :

  ```java
  // AVANT
  private void acquireAdvisoryLock(Long eventId) {
      if (eventId == null) {
          return;
      }
      entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
              .setParameter(1, eventId)
              .getSingleResult();
  }

  // APRÈS
  private void acquireAdvisoryLock(Long eventId) {
      if (eventId == null) {
          throw new IllegalStateException(
              "acquireAdvisoryLock called with null eventId — capacity gating bypassed. " +
              "This indicates a programming error upstream (REST path always passes a non-null eventId)."
          );
      }
      entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(?1)")
              .setParameter(1, eventId)
              .getSingleResult();
  }
  ```

- **Test sentinel** : `AttendanceServiceTest.acquireAdvisoryLock_nullEventId_throwsIllegalState()` — utiliser réflexion pour appeler la méthode privée OU exposer en package-private pour le test.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/engagement-service -Dtest=AttendanceServiceTest test
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(engagement): throw on null eventId in AttendanceService.acquireAdvisoryLock (Étape 24.5.3, A11)
  ```

---

#### Étape 24.5.4 — A15 : `FavoriteService.addFavorite` match nommé + log idempotent

- **Finding** : A15 (silent-failure-hunter IMP-2).
- **Sévérité** : IMPORTANT.
- **Fichiers touchés** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/favorite/service/FavoriteService.java:62-73`
- **Patch** :

  ```java
  // AVANT
  } catch (PersistenceException e) {
      if (!isUniqueConstraintViolation(e)) {
          throw e;
      }
      // Concurrent insert won — idempotent noop, the favorite exists.
  }

  // APRÈS
  } catch (PersistenceException e) {
      if (!isUniqueFavoriteConflict(e)) {
          throw e;
      }
      Log.debugf("[FAVORITE_DOUBLE_TAP] idempotent noop user=%s event=%d", userId, eventId);
  }

  // ... ailleurs dans le fichier, méthode helper analogue à FollowService.isUniqueFollowConflict
  private static boolean isUniqueFavoriteConflict(PersistenceException e) {
      Throwable cause = e;
      while (cause != null) {
          if (cause instanceof org.hibernate.exception.ConstraintViolationException c) {
              String name = c.getConstraintName();
              if (name != null && name.equalsIgnoreCase("uq_favorite_user_event")) {
                  return true;
              }
          }
          cause = cause.getCause();
      }
      return false;
  }
  ```

  Note : si l'index unique a un autre nom dans `V4__create_favorites.sql`, ajuster en conséquence (`grep -n 'UNIQUE' backend/services/event-service/src/main/resources/db/migration/V4__create_favorites.sql`).

- **Test sentinel** : déjà couvert par C2 (Étape 24.2.2). Optionnel : ajouter `addFavorite_otherUniqueViolation_isReThrown()` qui force une autre contrainte unique → vérifier que le catch ne l'absorbe pas.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest=FavoriteServiceTest test
  grep -n 'FAVORITE_DOUBLE_TAP' backend/services/event-service/src/main/java/ | wc -l  # ≥ 1
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(event): match favorites unique-violation by name + log idempotent double-tap (Étape 24.5.4, A15)
  ```

---

### Vague 6 — Types

> **Ordre des sous-étapes** : B5 (ApiErrorResponse) → B6 (constante header) → B3 (enums comportement) → B2 (faculty enum) → B1 (EventDTO variants) → B4 (records request). Cet ordre minimise les conflits de merge entre étapes.

#### Étape 24.6.1 — B5 : `ApiErrorResponse` constructeur compact + validation

- **Finding** : B5 (type-design-analyzer).
- **Sévérité** : MINEUR.
- **Fichier touché** :
  - `backend/services/shared-api-error/src/main/java/ch/unige/events/shared/error/ApiErrorResponse.java`
- **Patch** :

  ```java
  // AVANT
  public record ApiErrorResponse(
      @Schema(description = "Error code (machine-readable)") String error,
      @Schema(description = "Human-readable message") String message
  ) {}

  // APRÈS
  public record ApiErrorResponse(
      @Schema(description = "Error code (machine-readable, lowercase snake_case)") String error,
      @Schema(description = "Human-readable message") String message
  ) {
      public ApiErrorResponse {
          Objects.requireNonNull(error, "error code must not be null");
          if (error.isBlank()) {
              throw new IllegalArgumentException("error code must not be blank");
          }
      }
  }
  ```

  Import : `import java.util.Objects;`.

  **Note** : l'enum `ErrorCode` (option enrichissement) est explicitement reportée à S9+ (coût/bénéfice marginal sur cette PR). Si Elie veut la spec complète, ajouter `enum ErrorCode { CONFLICT, NOT_FOUND, FORBIDDEN, VALIDATION_FAILED, UNPROCESSABLE, OPTIMISTIC_LOCK_CONFLICT, ... }` dans shared-api-error puis remplacer `String error` par `ErrorCode error` — out of scope cette spec.

- **Test sentinel** : `ApiErrorResponseTest.constructor_nullError_throws()` + `_blankError_throws()`.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/shared-api-error -DskipITs verify
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  refactor(shared-api-error): require non-null non-blank error code via compact ctor (Étape 24.6.1, B5)
  ```

---

#### Étape 24.6.2 — B6 : Centraliser la constante `X-Internal-Token`

- **Finding** : B6 (type-design-analyzer + comment-analyzer NIT).
- **Sévérité** : NIT.
- **Fichiers touchés** :
  - `backend/services/shared-jaxrs/src/main/java/ch/unige/events/shared/jaxrs/InternalTokenFilter.java` (déjà a `public static final String HEADER`).
  - `backend/services/shared-tracing/src/main/java/ch/unige/events/shared/tracing/InternalTokenClientFilter.java` (à dédupliquer).
- **Patch** : `InternalTokenClientFilter.HEADER` → référence `InternalTokenFilter.HEADER`.

  ```java
  // AVANT (InternalTokenClientFilter.java)
  private static final String HEADER = "X-Internal-Token";

  // APRÈS
  // Re-use the canonical constant from shared-jaxrs to avoid drift.
  private static final String HEADER = ch.unige.events.shared.jaxrs.InternalTokenFilter.HEADER;
  ```

  **Alternative propre** : extraire la constante dans une nouvelle classe `InternalTokenContract` dans shared-jaxrs avec `public static final String HEADER = "X-Internal-Token"; public static final String CONFIG_KEY = "unige.internal-token";`. Mais ça ajoute un nouveau type pour 1 constante. Préférer la référence cross-module simple.

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/shared-jaxrs,services/shared-tracing -am -DskipITs verify
  grep -rn '"X-Internal-Token"' backend/services/*/src/main/java/ | wc -l  # doit être 1 (la définition canonique seulement)
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  refactor(shared-tracing): re-use canonical X-Internal-Token constant from shared-jaxrs (Étape 24.6.2, B6)
  ```

---

#### Étape 24.6.3 — B3 : Enums anémiques → comportement métier

- **Finding** : B3 (type-design-analyzer IMPORTANT).
- **Sévérité** : IMPORTANT.
- **Fichiers touchés** :
  - `backend/services/shared-domain-enums/src/main/java/ch/unige/events/shared/domain/enums/EventStatus.java`
  - `backend/services/shared-domain-enums/src/main/java/ch/unige/events/shared/domain/enums/CoOrganizerStatus.java`
  - `backend/services/shared-domain-enums/src/main/java/ch/unige/events/shared/domain/enums/ReportStatus.java`
  - `backend/services/shared-domain-enums/src/main/java/ch/unige/events/shared/domain/enums/FollowStatus.java`
  - Call-sites dans event/user/engagement/moderation services (refactor mécanique).

- **Patch (enums enrichis)** :

  ```java
  // EventStatus.java
  public enum EventStatus {
      DRAFT, PUBLISHED, CANCELLED, EXPIRED, BANNED;

      /** Terminal states: no further transitions allowed. */
      public boolean isTerminal() {
          return this == CANCELLED || this == EXPIRED || this == BANNED;
      }

      /** Visible publicly (anonymous + non-admin non-creator). */
      public boolean isVisible() {
          return this == PUBLISHED;
      }

      /** Whether the soft-delete is active (i.e. event hidden from public lists). */
      public boolean isSoftDeleted() {
          return this == CANCELLED || this == BANNED;
      }

      /** Validates a state machine transition. Returns true if {@code this -> next} is allowed. */
      public boolean canTransitionTo(EventStatus next) {
          if (this == next) return false;  // no-op transitions disallowed
          return switch (this) {
              case DRAFT      -> next == PUBLISHED || next == CANCELLED;
              case PUBLISHED  -> next == CANCELLED || next == EXPIRED || next == BANNED;
              case CANCELLED, EXPIRED, BANNED -> false;
          };
      }
  }

  // CoOrganizerStatus.java
  public enum CoOrganizerStatus {
      PENDING, ACCEPTED, DECLINED;

      public boolean isFinal()    { return this != PENDING; }
      public boolean isOpen()     { return this == PENDING; }
      public boolean isAccepted() { return this == ACCEPTED; }
  }

  // ReportStatus.java
  public enum ReportStatus {
      PENDING, REVIEWED, DISMISSED;

      public boolean isClosed() { return this == REVIEWED || this == DISMISSED; }
      public boolean isOpen()   { return this == PENDING; }
  }

  // FollowStatus.java
  public enum FollowStatus {
      PENDING, ACCEPTED, REJECTED;

      public boolean isAccepted() { return this == ACCEPTED; }
      public boolean isPending()  { return this == PENDING; }
      public boolean isFinal()    { return this != PENDING; }
  }
  ```

- **Refactor des call-sites** : repérer les `if (status == BANNED || status == EXPIRED || status == CANCELLED)` dispersés et remplacer par `status.isTerminal()` etc. Chemins à inspecter :
  - `event-service.EventService` (~5 sites)
  - `event-service.EventCoOrganizerService` (~3 sites)
  - `moderation-service.ReportService` (~2 sites)
  - `user-service.FollowService` (~2 sites)

  Critère : tout `if` qui matche ≥ 2 valeurs d'un enum est candidat au refactor.

  ```java
  // Exemple AVANT (EventService.java)
  if (event.status == EventStatus.CANCELLED || event.status == EventStatus.EXPIRED || event.status == EventStatus.BANNED) {
      // ...
  }
  // APRÈS
  if (event.status.isTerminal()) {
      // ...
  }
  ```

- **Test sentinel** : reporté à Étape 24.7.6 (C9) — pin tests pour chaque helper.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B verify -T 1
  # Vérifier qu'aucun call-site n'a été oublié :
  grep -rn '== EventStatus.CANCELLED \|\| .* == EventStatus.EXPIRED' backend/services/*/src/main/java/  # idéalement empty
  ```
- **Doc à mettre à jour** : aucune (les helpers sont auto-documentés via JavaDoc).
- **Commit attendu** :
  ```
  refactor(shared-domain-enums): add behavioral helpers to EventStatus/CoOrganizerStatus/ReportStatus/FollowStatus + refactor call sites (Étape 24.6.3, B3)
  ```

---

#### Étape 24.6.4 — B2 : `UserPublicResponse.faculty: String` → `Faculty` enum

- **Finding** : B2 (type-design-analyzer IMPORTANT).
- **Sévérité** : IMPORTANT.
- **Fichiers touchés** :
  - `backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/domain/dto/UserPublicResponse.java`
  - Tous les sites qui construisent un `UserPublicResponse` (mapper user-service principalement).
  - Pact contracts existants (vérifier qu'ils acceptent les valeurs canoniques `SCIENCES`, `LETTRES`, etc.).
- **Patch** :

  ```java
  // AVANT
  public record UserPublicResponse(
      UUID id,
      String displayName,
      String avatarUrl,
      String bannerUrl,
      String faculty,        // <-- String
      String studyLevel,
      List<String> interests,
      ...
  ) {}

  // APRÈS
  public record UserPublicResponse(
      UUID id,
      String displayName,
      String avatarUrl,
      String bannerUrl,
      Faculty faculty,       // <-- enum typé
      String studyLevel,     // (studyLevel reste String — pas d'enum existant ; cf. note)
      List<String> interests,
      ...
  ) {}
  ```

  Imports : `import ch.unige.events.shared.domain.enums.Faculty;`.

  **Note `studyLevel`** : pas d'enum `StudyLevel` existant. Création reportée à S9+ (out of scope ici, cf. type-design-analyzer note). Maintenir `String` mais ajouter une JavaDoc `@param studyLevel must match one of {LICENCE, MASTER, PHD, OTHER} — to be enforced by enum in S9+.`.

  **Note Pact** : vérifier `backend/contract-tests/src/test/.../*Pact*Test.java` pour s'assurer que les tests Pact générés pour `UserPublicResponse` envoient les valeurs `Faculty` canoniques (majuscules). Si un Pact stub utilise `"sciences"` minuscule, le mettre à jour.

- **Test sentinel** : `UserPublicResponseTest.faculty_serialization_usesCanonicalEnumName()` — round-trip JSON.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B verify -T 1
  # Aucun champ String faculty ne doit subsister :
  grep -rn 'String faculty' backend/services/*/src/main/java/  # idéalement empty (sauf si commentaire)
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  refactor(shared-domain-dtos): type UserPublicResponse.faculty as Faculty enum (Étape 24.6.4, B2)
  ```

---

#### Étape 24.6.5 — B1 : EventDTO variants — JavaDoc honnête + `coOrganizerOf` aux 4 variants

- **Finding** : B1 (type-design-analyzer IMPORTANT — drift JavaDoc-vs-types).
- **Sévérité** : IMPORTANT.
- **Décision retenue (option a partielle)** : ajouter `coOrganizerOf` aux 4 variants pour aligner sur le master + reformuler la JavaDoc pour cesser de mentir sur la nullability.
- **Fichiers touchés** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/dto/EventDTO.java` (master — JavaDoc seule)
  - `backend/services/event-service/src/main/java/ch/unige/events/event/me/dto/EventDTO.java`
  - `backend/services/event-service/src/main/java/ch/unige/events/event/favorite/dto/EventDTO.java`
  - `backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/dto/EventDTO.java`
- **Patch (à appliquer aux 3 variants me/favorite/coorganizer)** :
  - Ajouter le champ `Boolean coOrganizerOf` en fin de record (cohérent avec le master).
  - Reformuler la JavaDoc pour remplacer « each variant differs by nullability of count fields » par :

  ```java
  /**
   * EventDTO emitted by event-service for the {favorite|me|coorganizer} sub-domain.
   *
   * <p>FR — Variant intentionnellement dupliqué post-Décision E (cf. Étape 23.4.4)
   * pour découpler les contrats sub-domain. Ce variant porte actuellement les
   * MÊMES types que les autres variants — la séparation est un point de
   * variation contrôlé prêt à diverger sans casser les siblings, pas une
   * variation déjà exprimée. NE PAS consolider sans revisiter la spec.
   *
   * <p>EN — Variant intentionally duplicated per Decision E (cf. Étape 23.4.4)
   * to decouple sub-domain contracts. Currently carries the SAME types as the
   * other variants — the split is a controlled point of variation ready to
   * diverge without breaking siblings, not an already-expressed variation. DO
   * NOT consolidate without revisiting the spec.
   *
   * <p>Note: {@code coOrganizerOf} added in Étape 24.6.5 (B1) for cross-variant
   * uniformity — the SCRUM-136 cascade flag is now available on every variant.
   */
  public record EventDTO(
      ...
      Boolean coOrganizerOf  // ajouté
  ) { ... }
  ```

  Pour le master : la JavaDoc existante reste, mais ajouter une note pour pointer vers l'alignement.

  **Adapter les `from(Event event, ...)` factories** : les 3 variants doivent inclure `coOrganizerOf` dans leur factory (peut être `null` par défaut, à passer optionnellement).

- **Test sentinel** : étendre `EventDTOFactoryTest` (existant) pour vérifier que `from(event, false).coOrganizerOf() == false` sur les 4 variants.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -DskipITs verify
  grep -c 'Boolean coOrganizerOf' backend/services/event-service/src/main/java/ch/unige/events/event/{me,favorite,coorganizer,}/dto/EventDTO.java  # doit être 4 (un par variant)
  ```
- **Doc à mettre à jour** : aucune (les JavaDoc sont elles-mêmes la doc).
- **Commit attendu** :
  ```
  refactor(event): align coOrganizerOf across 4 EventDTO variants + reword JavaDoc to match types (Étape 24.6.5, B1)
  ```

---

#### Étape 24.6.6 — B4 : `EventRequestBase` + `Create/UpdateEventRequest` → records

- **Finding** : B4 (type-design-analyzer IMPORTANT).
- **Sévérité** : IMPORTANT.
- **Fichiers touchés** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/dto/EventRequestBase.java` (à supprimer)
  - `backend/services/event-service/src/main/java/ch/unige/events/event/dto/CreateEventRequest.java` (mutable class → record)
  - `backend/services/event-service/src/main/java/ch/unige/events/event/dto/UpdateEventRequest.java` (mutable class → record)
  - `EventResource` + `EventService` (call-sites — passage par accessor `req.title()` au lieu de `req.title`).
- **Patch (CreateEventRequest)** :

  ```java
  // AVANT
  public class CreateEventRequest extends EventRequestBase {
      @NotBlank public String title;
      @NotNull @Future public LocalDateTime startDate;
      // ... champs publics mutables
      public RecurrenceRequest recurrence;
      private EventStatus status;
      // getters/setters mixés
  }

  // APRÈS
  public record CreateEventRequest(
      @NotBlank String title,
      @NotBlank String description,
      @NotBlank String location,
      @NotNull @Future LocalDateTime startDate,
      @NotNull LocalDateTime endDate,
      @NotNull EventCategory category,
      @NotNull Faculty faculty,
      @Min(1) Integer capacity,
      boolean allDay,
      String websiteUrl,
      String contactEmail,
      LocalDateTime registrationDeadline,
      List<String> tags,
      RecurrenceRequest recurrence,
      EventStatus status
  ) {
      public CreateEventRequest {
          tags = (tags == null) ? List.of() : List.copyOf(tags);
      }
  }
  ```

  Idem `UpdateEventRequest`. `EventRequestBase` supprimée (un record ne peut pas hériter d'une classe ; les champs communs sont copiés à plat — accepter la duplication, c'est 2 records de 15 champs chacun).

- **Refactor des call-sites** : `req.title` → `req.title()`. Tooling Java (record auto-generate accessors) garantit la complétude au compilateur.

- **Test sentinel** : tests unitaires existants `CreateEventRequestTest` doivent rester verts ; ajuster à la signature record si besoin (constructor positional vs setter).
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -DskipITs verify -T 1
  test ! -f backend/services/event-service/src/main/java/ch/unige/events/event/dto/EventRequestBase.java
  grep -c 'public record CreateEventRequest' backend/services/event-service/src/main/java/ch/unige/events/event/dto/CreateEventRequest.java  # doit être 1
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  refactor(event): migrate Create/UpdateEventRequest to immutable records (Étape 24.6.6, B4)
  ```

---

### Vague 7 — Tests intégration

#### Étape 24.7.1 — C4 : Anti-oracle ISSUE-92 body equivalence

- **Finding** : C4 (pr-test-analyzer B-4).
- **Sévérité** : IMPORTANT (test hardening).
- **Fichier ajouté/modifié** :
  - `backend/services/event-service/src/test/java/ch/unige/events/event/sentinels/EventDomainSentinelsTest.java` (ajouter méthode).
- **Patch (test à ajouter)** :

  ```java
  @Test
  @DisplayName("ISSUE-92 anti-oracle: 404 body is identical for unknown event, DRAFT non-creator, and BANNED")
  @TestTransaction
  void getById_anti_oracle_bodyEquivalence_acrossUnknownDraftBanned() {
      // Given
      String otherUserAuth0 = "auth0|other-user";

      Event draft = TestFixtures.draftEvent();
      draft.persist();
      Event banned = TestFixtures.bannedEvent();
      banned.persist();
      long unknownId = 999_999_999L;

      // When: collect 3 responses for the same caller (non-creator, non-admin)
      Response unknown = given().auth().oauth2(jwt(otherUserAuth0))
              .when().get("/api/events/" + unknownId);
      Response draftResp = given().auth().oauth2(jwt(otherUserAuth0))
              .when().get("/api/events/" + draft.id);
      Response bannedResp = given().auth().oauth2(jwt(otherUserAuth0))
              .when().get("/api/events/" + banned.id);

      // Then: status code identical
      assertThat(unknown.getStatusCode()).isEqualTo(404);
      assertThat(draftResp.getStatusCode()).isEqualTo(404);
      assertThat(bannedResp.getStatusCode()).isEqualTo(404);

      // And: body strictly identical (same error code, same message, same content-length)
      String unknownBody = unknown.getBody().asString();
      assertThat(draftResp.getBody().asString()).isEqualTo(unknownBody);
      assertThat(bannedResp.getBody().asString()).isEqualTo(unknownBody);
      assertThat(unknown.getHeader("Content-Length")).isEqualTo(draftResp.getHeader("Content-Length"));
      assertThat(unknown.getHeader("Content-Length")).isEqualTo(bannedResp.getHeader("Content-Length"));
  }
  ```

  Note : si `NotFoundExceptionMapper` ajoute des metadata distinctives (ex. errorId par cas), ajuster pour vérifier que ces metadata ne sont pas exposées.

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest='EventDomainSentinelsTest#getById_anti_oracle_bodyEquivalence_acrossUnknownDraftBanned' test
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  test(event): pin ISSUE-92 anti-oracle body equivalence across unknown/DRAFT/BANNED (Étape 24.7.1, C4)
  ```

---

#### Étape 24.7.2 — C5 : Tests `@Fallback` REST clients (câblage MicroProfile FT)

- **Finding** : C5 (pr-test-analyzer I-2).
- **Sévérité** : IMPORTANT.
- **Fichiers ajoutés** :
  - `backend/services/event-service/src/test/java/ch/unige/events/event/integration/EventServiceClientFallbackWiringIT.java`
  - `backend/services/engagement-service/src/test/java/ch/unige/events/engagement/integration/EngagementServiceClientFallbackWiringIT.java`
  - `backend/services/user-service/src/test/java/ch/unige/events/user/integration/UserServiceClientFallbackWiringIT.java`
- **Patch (pattern, exemple Event)** :

  ```java
  @QuarkusTest
  @TestProfile(EventServiceClientFallbackWiringIT.MockProfile.class)
  class EventServiceClientFallbackWiringIT {

      @Inject @RestClient EventServiceClient client;

      public static class MockProfile implements QuarkusTestProfile {
          @Override
          public Map<String, String> getConfigOverrides() {
              return Map.of(
                  // Point the client to a black-hole URL that always fails
                  "quarkus.rest-client.event-service.url", "http://localhost:1/non-routable",
                  "quarkus.rest-client.event-service.connect-timeout", "100",
                  "quarkus.rest-client.event-service.read-timeout", "100"
              );
          }
      }

      @Test
      @DisplayName("getById fallback wired: connection failure routes to fallback method, not exception")
      void getById_connectionFailure_routesToFallback() {
          // Doit retourner null (fallback method), pas throw.
          EventDTO result = client.getById(42L);
          assertThat(result).isNull();
      }

      @Test
      @DisplayName("findByIds fallback wired: connection failure routes to fallback method, returns empty list")
      void findByIds_connectionFailure_routesToFallback() {
          List<EventDTO> result = client.findByIds(List.of(1L, 2L), "PUBLISHED");
          assertThat(result).isEmpty();
      }

      // Idem pour les autres méthodes du client.
  }
  ```

  Variante : utiliser `org.eclipse.microprofile.faulttolerance.exceptions.CircuitBreakerOpenException` simulée si le client a `@CircuitBreaker`. À défaut, la connexion sur port 1 est suffisamment lente/fail pour exercer le fallback via `@Timeout`.

  **But du test** : si quelqu'un retire `@Fallback(fallbackMethod = "getByIdFallback")` de l'interface client, le test passe rouge (l'exception MicroProfile FT remonte au lieu d'être absorbée par le fallback).

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service,services/engagement-service,services/user-service -am -DskipITs verify -T 1
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  test(backend): pin @Fallback wiring on Event/Engagement/User REST clients (Étape 24.7.2, C5)
  ```

---

#### Étape 24.7.3 — C6 : Tests intégration InternalToken (mismatch + fail-closed + scope)

- **Finding** : C6 (pr-test-analyzer I-3).
- **Sévérité** : IMPORTANT.
- **Fichiers modifiés/ajoutés** :
  - `backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/resource/AttendanceSummaryInternalResourceTest.java` (étendre)
  - `backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/resource/UserAttendancesInternalResourceTest.java` (étendre)
  - `backend/services/shared-jaxrs/src/test/java/ch/unige/events/shared/jaxrs/InternalTokenFilterFailClosedIT.java` (nouveau, `@QuarkusTest` avec `unige.internal-token=` vide)
- **Patch (étendre les tests engagement existants)** :

  ```java
  @Test
  @DisplayName("Token mismatch produces same 404 body as missing token (anti-oracle)")
  void getAttendanceSummary_tokenMismatch_returns404SameBody() {
      Response missing = given()
              .when().get("/api/events/42/attendance-summary");
      Response wrong = given()
              .header("X-Internal-Token", "wrong-token-value")
              .when().get("/api/events/42/attendance-summary");

      assertThat(missing.getStatusCode()).isEqualTo(404);
      assertThat(wrong.getStatusCode()).isEqualTo(404);
      assertThat(wrong.getBody().asString()).isEqualTo(missing.getBody().asString());
  }

  @Test
  @DisplayName("Public endpoints in same service are NOT affected by InternalTokenFilter")
  void publicEndpoint_bypasses_InternalTokenFilter() {
      // /api/events/{id}/attendance is the public counterpart, not @Internal.
      // Should work without X-Internal-Token (auth checked by @Authenticated, not the filter).
      given().auth().oauth2(jwt("auth0|user1"))
              .when().get("/api/events/42/attendance")
              .then().statusCode(anyOf(is(200), is(404), is(401))); // any status that ISN'T a NameBinding leak
  }
  ```

- **Patch (nouveau test fail-closed)** :

  ```java
  // backend/services/shared-jaxrs/src/test/java/ch/unige/events/shared/jaxrs/InternalTokenFilterFailClosedIT.java
  @QuarkusTest
  @TestProfile(InternalTokenFilterFailClosedIT.NoTokenProfile.class)
  class InternalTokenFilterFailClosedIT {

      public static class NoTokenProfile implements QuarkusTestProfile {
          @Override
          public Map<String, String> getConfigOverrides() {
              return Map.of("unige.internal-token", "");  // explicitly empty
          }
      }

      @Inject InternalTokenFilter filter;

      @Test
      @DisplayName("Empty unige.internal-token config rejects ALL @Internal requests with 404 (fail-closed)")
      void emptyConfig_rejectsEvenWithMatchingHeader() {
          ContainerRequestContext ctx = mockCtx("any-value");
          assertThatThrownBy(() -> filter.filter(ctx))
                  .isInstanceOf(NotFoundException.class);
      }
      // ...
  }
  ```

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/engagement-service,services/shared-jaxrs -am -DskipITs verify -T 1
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  test(backend): pin InternalToken mismatch + fail-closed + public endpoint scope (Étape 24.7.3, C6)
  ```

---

#### Étape 24.7.4 — Sentinel ADR-002 `getOrganizerUuids` filtre BANNED

- **Finding** : extension de A16 (Décision J).
- **Sévérité** : IMPORTANT.
- **Fichier ajouté/modifié** :
  - `backend/services/event-service/src/test/java/ch/unige/events/event/sentinels/EventDomainSentinelsTest.java` (ajouter méthode).
- **Patch (test à ajouter)** :

  ```java
  @Test
  @DisplayName("ADR-002: GET /events/{id}/organizer-uuids returns 404 (or empty) for BANNED events, regardless of caller")
  @TestTransaction
  void getOrganizerUuids_bannedEvent_doesNotLeakUuidsToAnyCaller() {
      Event banned = TestFixtures.bannedEvent();
      banned.persist();

      // Anonymous caller (default) — even though @PermitAll, BANNED should NOT leak.
      Response anon = given().when().get("/api/events/" + banned.id + "/organizer-uuids");
      assertThat(anon.getStatusCode()).isEqualTo(404);

      // Authenticated non-creator caller
      Response other = given().auth().oauth2(jwt("auth0|other"))
              .when().get("/api/events/" + banned.id + "/organizer-uuids");
      assertThat(other.getStatusCode()).isEqualTo(404);

      // Even the creator gets 404 on BANNED (consistent with the cascade).
      Response creator = given().auth().oauth2(jwt("auth0|" + banned.creatorId))
              .when().get("/api/events/" + banned.id + "/organizer-uuids");
      assertThat(creator.getStatusCode()).isEqualTo(404);
  }
  ```

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest='EventDomainSentinelsTest#getOrganizerUuids_bannedEvent_doesNotLeakUuidsToAnyCaller' test
  ```
- **Doc à mettre à jour** : aucune (ADR-002 référence ce sentinel).
- **Commit attendu** :
  ```
  test(event): pin ADR-002 BANNED filter on /events/{id}/organizer-uuids (Étape 24.7.4)
  ```

---

#### Étape 24.7.5 — C7 : Test intégration MdcKafka in-memory

- **Finding** : C7 (pr-test-analyzer I-4).
- **Sévérité** : IMPORTANT.
- **Fichier ajouté** :
  - `backend/services/shared-tracing/src/test/java/ch/unige/events/shared/tracing/MdcKafkaInMemoryIT.java` (nouveau `@QuarkusTest` avec connector in-memory).
- **Patch** :

  ```java
  @QuarkusTest
  class MdcKafkaInMemoryIT {

      @Inject @Channel("test-trace-out") Emitter<String> emitter;
      @Inject @Any InMemoryConnector connector;

      @Test
      @DisplayName("X-Request-ID propagates through producer→broker→consumer via MdcKafka interceptors")
      void requestId_propagatesAcrossKafka() throws Exception {
          MDC.put("requestId", "req-abc-123");
          try {
              emitter.send("payload").toCompletableFuture().get(5, TimeUnit.SECONDS);

              InMemorySource<String> source = connector.source("test-trace-in");
              source.runOnMessages(msg -> {
                  // Le header X-Request-ID doit être présent dans les headers Kafka.
                  Optional<String> hdr = msg.getMetadata(IncomingKafkaRecordMetadata.class)
                          .flatMap(m -> Optional.ofNullable(m.getHeaders().lastHeader("X-Request-ID")))
                          .map(h -> new String(h.value(), StandardCharsets.UTF_8));
                  assertThat(hdr).contains("req-abc-123");
              });
          } finally {
              MDC.clear();
          }
      }
  }
  ```

  **Note** : nécessite `quarkus-smallrye-reactive-messaging-in-memory` en `<scope>test</scope>` dans `shared-tracing/pom.xml`. Configurer `application.properties` test pour câbler `test-trace-out` et `test-trace-in` avec le connector in-memory + `interceptor.classes=...`.

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/shared-tracing -DskipITs verify
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  test(shared-tracing): pin X-Request-ID propagation via Kafka in-memory interceptor wiring (Étape 24.7.5, C7)
  ```

---

#### Étape 24.7.6 — C8 : Corriger fake-green `getOccurrences_sizeOver52`

- **Finding** : C8 (pr-test-analyzer I-6).
- **Sévérité** : IMPORTANT.
- **Fichier touché** :
  - `backend/services/event-service/src/test/java/ch/unige/events/event/sentinels/EventDomainSentinelsTest.java:352-362`
- **Patch** :

  ```java
  // AVANT
  @Test
  void getOccurrences_sizeOver52_returns400() {
      ValidatorFactory vf = Validation.buildDefaultValidatorFactory();
      assertNotNull(vf);
  }

  // APRÈS
  @Test
  @DisplayName("RecurrenceGenerator: requesting more than 52 occurrences returns 400 (capped at 1 year weekly)")
  void getOccurrences_sizeOver52_returns400() {
      // Given: a recurring event with WEEKLY frequency
      Event parent = TestFixtures.weeklyRecurringEvent();
      parent.persist();

      // When: ask for size=53 (>52 weeks)
      Response resp = given()
              .auth().oauth2(jwt("auth0|" + parent.creatorId))
              .queryParam("size", 53)
              .when().get("/api/events/" + parent.id + "/occurrences");

      // Then: 400 with explicit error code
      assertThat(resp.getStatusCode()).isEqualTo(400);
      assertThat(resp.getBody().jsonPath().getString("error")).isEqualTo("validation_failed");
      assertThat(resp.getBody().jsonPath().getString("message")).contains("size");
      assertThat(resp.getBody().jsonPath().getString("message")).contains("52");
  }
  ```

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest='EventDomainSentinelsTest#getOccurrences_sizeOver52_returns400' test
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  test(event): replace fake-green getOccurrences_sizeOver52 with real assertion (Étape 24.7.6, C8)
  ```

---

#### Étape 24.7.7 — C9 : Pin tests pour helpers d'enum (B3)

- **Finding** : C9 (couverture des helpers ajoutés en Étape 24.6.3).
- **Sévérité** : MINEUR (mais nécessaire pour Sonar coverage).
- **Fichier ajouté** :
  - `backend/services/shared-domain-enums/src/test/java/ch/unige/events/shared/domain/enums/EventStatusBehaviorTest.java`
  - `backend/services/shared-domain-enums/src/test/java/ch/unige/events/shared/domain/enums/CoOrganizerStatusBehaviorTest.java`
  - `backend/services/shared-domain-enums/src/test/java/ch/unige/events/shared/domain/enums/ReportStatusBehaviorTest.java`
  - `backend/services/shared-domain-enums/src/test/java/ch/unige/events/shared/domain/enums/FollowStatusBehaviorTest.java`
- **Patch (pattern, exemple EventStatus)** :

  ```java
  class EventStatusBehaviorTest {

      @Test
      void isTerminal_coversCancelledExpiredBanned() {
          assertThat(EventStatus.DRAFT.isTerminal()).isFalse();
          assertThat(EventStatus.PUBLISHED.isTerminal()).isFalse();
          assertThat(EventStatus.CANCELLED.isTerminal()).isTrue();
          assertThat(EventStatus.EXPIRED.isTerminal()).isTrue();
          assertThat(EventStatus.BANNED.isTerminal()).isTrue();
      }

      @Test
      void isVisible_onlyPublished() {
          for (EventStatus s : EventStatus.values()) {
              assertThat(s.isVisible()).isEqualTo(s == EventStatus.PUBLISHED);
          }
      }

      @Test
      void canTransitionTo_validStates() {
          assertThat(EventStatus.DRAFT.canTransitionTo(EventStatus.PUBLISHED)).isTrue();
          assertThat(EventStatus.DRAFT.canTransitionTo(EventStatus.CANCELLED)).isTrue();
          assertThat(EventStatus.PUBLISHED.canTransitionTo(EventStatus.CANCELLED)).isTrue();
          assertThat(EventStatus.PUBLISHED.canTransitionTo(EventStatus.EXPIRED)).isTrue();
          assertThat(EventStatus.PUBLISHED.canTransitionTo(EventStatus.BANNED)).isTrue();
      }

      @Test
      void canTransitionTo_invalidStates() {
          assertThat(EventStatus.PUBLISHED.canTransitionTo(EventStatus.DRAFT)).isFalse();
          assertThat(EventStatus.CANCELLED.canTransitionTo(EventStatus.PUBLISHED)).isFalse();
          assertThat(EventStatus.BANNED.canTransitionTo(EventStatus.PUBLISHED)).isFalse();
          assertThat(EventStatus.PUBLISHED.canTransitionTo(EventStatus.PUBLISHED)).isFalse();
      }
  }
  ```

  Idem pour les 3 autres enums (CoOrganizerStatus, ReportStatus, FollowStatus) : un test par helper, exhaustif sur toutes les valeurs.

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/shared-domain-enums -DskipITs verify
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  test(shared-domain-enums): cover behavioral helpers added in B3 (Étape 24.7.7, C9)
  ```

---

### Vague 8 — Refactors utiles

#### Étape 24.8.1 — E1 : `FavoriteService.getFavorites` N+1 → bulk fetch

- **Finding** : E1 (code-reviewer MINEUR-1).
- **Sévérité** : MINEUR.
- **Fichier touché** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/favorite/service/FavoriteService.java:111-122`
- **Patch** :

  ```java
  // AVANT (N+1)
  return favorites.stream()
          .map(f -> Event.<Event>findByIdOptional(f.eventId).orElse(null))
          .filter(Objects::nonNull)
          .map(EventDTO::from)
          .toList();

  // APRÈS (1 + 1)
  List<Long> eventIds = favorites.stream().map(f -> f.eventId).toList();
  Map<Long, Event> byId = Event.<Event>list("id IN ?1", eventIds).stream()
          .collect(Collectors.toMap(e -> e.id, e -> e));
  return favorites.stream()
          .map(f -> byId.get(f.eventId))
          .filter(Objects::nonNull)
          .map(EventDTO::from)
          .toList();
  ```

- **Test sentinel** : `FavoriteServiceTest.getFavorites_doesNotIssueNPlusOneQueries()` — utiliser un statistics interceptor Hibernate ou compter les queries via `@QuarkusTest` + `Hibernate.SessionFactory.getStatistics()`.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest=FavoriteServiceTest test
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  perf(event): replace N+1 query in FavoriteService.getFavorites with bulk fetch (Étape 24.8.1, E1)
  ```

---

#### Étape 24.8.2 — E2 : `EventService.getById` baisser read-timeout REST → 1s

- **Finding** : E2 (code-reviewer MINEUR-6).
- **Sévérité** : MINEUR.
- **Décision retenue (option simple)** : baisser `read-timeout` à 1s sur les chemins read-only où l'enrichissement `AttendanceSummary` est appelé en dans `@Transactional`. Le déplacement post-commit (option ambitieuse) est reporté à S9+ (refactor large).
- **Fichiers touchés** :
  - `backend/services/event-service/src/main/resources/application.properties`
- **Patch** :

  ```properties
  # AVANT (probable, à vérifier)
  quarkus.rest-client.engagement-service.read-timeout=5000

  # APRÈS — 1s pour les chemins de lecture
  quarkus.rest-client.engagement-service.read-timeout=1000
  ```

  Si la config existante est utilisée pour les chemins write (où 1s serait trop court), créer un client distinct pour read et un pour write — mais préférer la solution simple (1s suffit pour un appel local Kong→service).

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -DskipITs verify -T 1
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  perf(event): tighten engagement-service read-timeout to 1s on read paths (Étape 24.8.2, E2)
  ```

---

#### Étape 24.8.3 — E3 : `EnumParamConverterProvider` skip Timeframe via `@Priority`

- **Finding** : E3 (type-design-analyzer IMPORTANT).
- **Sévérité** : MINEUR.
- **Fichiers touchés** :
  - `backend/services/shared-jaxrs/src/main/java/ch/unige/events/shared/jaxrs/EnumParamConverterProvider.java`
  - `backend/services/shared-jaxrs/src/main/java/ch/unige/events/shared/jaxrs/TimeframeParamConverterProvider.java` (vérifier la classe)
- **Patch** :

  ```java
  // EnumParamConverterProvider.java — retirer skip codé en dur, ajouter @Priority bas (s'exécute après TimeframeParamConverterProvider)
  // AVANT
  if (rawType == Timeframe.class) return null;
  // APRÈS — supprimer cette ligne, et déplacer la priorité dans l'annotation classe
  @Provider
  @Priority(jakarta.ws.rs.Priorities.USER + 100)  // run AFTER any specific param converter providers
  public class EnumParamConverterProvider implements ParamConverterProvider { ... }
  ```

  Vérifier que `TimeframeParamConverterProvider` n'a pas de `@Priority` ou en a un plus bas (par défaut `Priorities.USER`).

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/shared-jaxrs -DskipITs verify
  # Le test EnumParamConverterProviderTest doit toujours passer + le test Timeframe param converter aussi.
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  refactor(shared-jaxrs): use @Priority instead of hard-coded Timeframe skip in EnumParamConverterProvider (Étape 24.8.3, E3)
  ```

---

#### Étape 24.8.4 — E4 : Imports inutiles

- **Finding** : E4 (code-reviewer NIT-2).
- **Sévérité** : NIT.
- **Fichiers touchés** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java:9` (`AttendanceStatus` non utilisé)
  - `backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java:24` (`Objects` non utilisé)
- **Patch** : retirer les imports.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B verify -T 1
  # Aucune régression, build vert.
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  style(backend): remove unused imports in EventService + UserService (Étape 24.8.4, E4)
  ```

---

### Vague 9 — Documentation

> **Note bilingue.** Tout nouveau bloc multi-paragraphes doit porter le préambule FR/EN (cf. ADR-002, ADR-003). Les corrections ponctuelles (1 phrase, 1 ligne) restent en anglais (convention historique).

> **Note granularité commits.** Les 21 items D peuvent être regroupés en 6-8 commits thématiques pour éviter la pollution log. Regroupement suggéré ci-dessous.

#### Étape 24.9.1 — D4 : JavaDoc `MdcKafkaProducer/ConsumerInterceptor` corrigée

> Couvert par Étape 24.3.7 (A13). Pas de commit séparé.

---

#### Étape 24.9.2 — D1 + D2 + D3 : Commentaires Helm + resources event/user-service obsolètes

- **Findings** : D1, D2, D3 (comment-analyzer B1, B2, B3).
- **Sévérité** : IMPORTANT (comment-rot trompeur post-finalization).
- **Fichiers touchés** :
  - `k8s/chart/templates/event-service/deployment.yaml:13-15`
  - `k8s/chart/templates/kong/configmap-routes.yaml:42-49`
  - `backend/services/user-service/src/main/java/ch/unige/events/user/calendar/resource/UserCalendarTokenResource.java:21-22`
  - `backend/services/event-service/src/main/java/ch/unige/events/event/favorite/resource/UserFavoritesResource.java:23-25`
  - `backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/resource/MyCoOrganizerInvitationsResource.java:26-27`
  - `backend/services/event-service/src/main/java/ch/unige/events/event/me/resource/MyEventsResource.java:25-31`
- **Patch (formulations cibles exactes)** :

  - **D1** Helm event-service deployment.yaml lignes 13-15 — supprimer le bloc complet :

    ```yaml
    # AVANT (3 lignes)
    # The /events/{id}/image upload stays on legacy-monolith via the catch-all
    # (FileStorageService not ported in S8 ; follow-up PR migrates it).

    # APRÈS — supprimer entièrement (le legacy n'existe plus, cf. configmap-routes.yaml:11)
    ```

    Idem `kong/configmap-routes.yaml:42-49` : supprimer le bloc commentaire référençant catch-all + legacy-monolith pour `/events/{id}/image`.

  - **D2** `UserCalendarTokenResource.java:21-22` — remplacer :

    ```java
    // AVANT (paragraphe sed-batch)
    /**
     * ... in the microservices topology they ship with calendar-service
     * so token rotation stays co-located with the feed it secures.
     */

    // APRÈS
    /**
     * Calendar token rotation — co-located with user-service since
     * Étape 2.3.2 finalization (calendar-service was absorbed into user-service
     * because the token lives on users.calendar_token). Cf. sprint-context.md
     * § Étape 23 for the absorption rationale.
     */
    ```

  - **D3** Resources event-service `UserFavoritesResource`, `MyCoOrganizerInvitationsResource`, `MyEventsResource` : aligner sur la formulation utilisée par `Favorite.java`, `EventCoOrganizer.java` :

    ```java
    // PATTERN APRÈS (à adapter selon la resource)
    /**
     * Owned by event-service (co-located post-finalization Étape 2.X.Y, cf.
     * sprint-context.md § Étape 23). Path = /api/users/me/{...}.
     */
    ```

- **Validation** :
  ```bash
  grep -rn 'legacy-monolith via the catch-all' k8s/chart/  # doit être 0
  grep -rn 'they ship with calendar-service' backend/services/  # doit être 0
  grep -rn 'me-aggregator-service absorbed' backend/services/  # doit être 0
  ```
- **Doc à mettre à jour** : aucune (les commentaires modifiés sont eux-mêmes la doc).
- **Commit attendu** :
  ```
  docs(backend): remove dead refs to legacy-monolith + absorbed services in Helm/resources (Étape 24.9.2, D1+D2+D3)
  ```

---

#### Étape 24.9.3 — D5 : Bloc `kong/configmap-routes.yaml:365-395` supprimé

- **Finding** : D5 (comment-analyzer I3).
- **Sévérité** : IMPORTANT.
- **Fichier touché** :
  - `k8s/chart/templates/kong/configmap-routes.yaml:365-395`
- **Patch** : supprimer les lignes 365-395 (bloc « Target topology — commented out, ready to flip per extraction PR » avec références à api-catchall, view-service/share-service/notification-stub absorbés).
- **Validation** :
  ```bash
  grep -n 'Target topology — commented out' k8s/chart/templates/kong/configmap-routes.yaml  # doit être 0
  grep -n 'narrow the.*paths.*api-catchall' k8s/chart/  # doit être 0
  helm lint k8s/chart/  # toujours OK
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  docs(infra): remove obsolete "Target topology" block in kong configmap-routes (Étape 24.9.3, D5)
  ```

---

#### Étape 24.9.4 — D6 + D7 : Refs « PR N of extraction roadmap » + « 13 service blocks »

- **Findings** : D6, D7 (comment-analyzer I4, I5).
- **Sévérité** : IMPORTANT.
- **Fichiers touchés** :
  - `k8s/chart/templates/event-service/deployment.yaml:10`
  - `k8s/chart/templates/user-service/deployment.yaml:10`
  - `k8s/chart/templates/moderation-service/deployment.yaml:10-11`
  - `k8s/chart/templates/kong/configmap-routes.yaml:17-20`
  - `k8s/chart/templates/kong/configmap-routes.yaml:42, 180, 272` (refs « PR N »)
- **Patch (formulation cible)** :

  - Pour chaque ref « PR N of the extraction roadmap » → remplacer par « depuis la finalisation Étape 2.X.Y (cf. `backend/docs/sprint-context.md` § Étape 23) ».

    Tableau de mapping (PR N → Étape) :

    | Ancienne ref | Nouvelle ref |
    |---|---|
    | « PR 9 » (moderation) | « Étape 2.X moderation extraction » |
    | « PR 12 » (user) | « Étape 2.3 user-service finalization » |
    | « PR 13 » (event) | « Étape 2.2 event-service finalization » |

  - configmap-routes lignes 17-20 — remplacer :

    ```yaml
    # AVANT
    # The 13 service blocks below are listed in the historical extraction order

    # APRÈS
    # The service blocks below are listed in their historical extraction
    # order ; after absorptions in Étape 2.X (cf. sprint-context.md § Étape 23),
    # the live topology has 5 microservices (event-service, user-service,
    # engagement-service, moderation-service, notification-service placeholder).
    ```

- **Validation** :
  ```bash
  grep -rn 'PR 12 of the extraction\|PR 13 of the extraction\|PR 9 of the extraction' k8s/chart/  # doit être 0
  grep -rn '13 service blocks' k8s/chart/  # doit être 0
  helm lint k8s/chart/
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  docs(infra): replace dead "PR N extraction roadmap" refs by sprint-context Étape 2.X links (Étape 24.9.4, D6+D7)
  ```

---

#### Étape 24.9.5 — D8 : ADR-001 vs values.yaml

- **Finding** : D8 (comment-analyzer I6).
- **Sévérité** : MINEUR.
- **Décision retenue (option b)** : corriger l'ADR pour décrire la réalité (hard-coded inline). Plus sûr que d'introduire une valeur surchargeable qui pourrait être bumpée à 2 par mégarde.
- **Fichier touché** :
  - `backend/docs/adr/ADR-001-moderation-cleanup-replicas-strict.md:33` + lignes voisines.
- **Patch (section "How this is enforced", point 1)** :

  ```markdown
  // AVANT
  1. **Helm `values.yaml`** sets `moderationService.replicas: 1` and a comment forbidding scale-out without revisiting this ADR.

  // APRÈS
  1. **`k8s/chart/templates/moderation-service/deployment.yaml`** hard-codes
     `replicas: 1` inline (not exposed in `values.yaml`) so the value cannot
     be overridden per-environment without editing the chart and revisiting
     this ADR. A `{{- fail }}` Helm guard above the spec block also rejects
     any explicit `moderationService.replicas` override > 1 if one is added
     later.
  ```

- **Validation** :
  ```bash
  grep -n 'moderationService.replicas: 1' k8s/chart/values.yaml  # doit être 0 (intentionnel)
  grep -n 'replicas: 1' k8s/chart/templates/moderation-service/deployment.yaml  # ≥ 1
  ```
- **Doc à mettre à jour** : ADR-001 (cf. patch ci-dessus).
- **Commit attendu** :
  ```
  docs(backend): align ADR-001 with hard-coded replicas:1 in moderation deployment (Étape 24.9.5, D8)
  ```

---

#### Étape 24.9.6 — D9 : `data-model.md` ModerationCleanupJob + S3 limit

- **Finding** : D9 (comment-analyzer I7 + I8).
- **Sévérité** : IMPORTANT (référence morte).
- **Fichier touché** :
  - `backend/docs/data-model.md:369-371` (réf morte) + nouvelle sous-section.
- **Patch (réf morte)** :

  ```markdown
  // AVANT
  Le job [`ModerationCleanupService`](../src/main/java/ch/unige/events/service/ModerationCleanupService.java) ...

  // APRÈS
  Le job [`ModerationCleanupJob`](../services/moderation-service/src/main/java/ch/unige/events/report/scheduler/ModerationCleanupJob.java) ...
  ```

- **Patch (nouvelle sous-section S3-orphan-tolerance)** : ajouter en fin de fichier :

  ```markdown
  ## S3 cleanup hors-transaction (MINOR-010 + MINOR-011)

  ### Préambule (FR / EN)

  - **FR** — Sur `UserService.uploadImage` / `uploadBanner`, l'objet S3 est
    écrit AVANT le commit JDBC qui mémorise l'URL. Un crash entre les deux
    laisse un orphelin S3. Idem pour les delete : la ligne JDBC est mise à
    jour, l'objet S3 est supprimé après commit ; un crash entre les deux
    laisse l'objet présent dans le bucket alors que la DB ne le référence
    plus.
  - **EN** — On `UserService.uploadImage` / `uploadBanner`, the S3 object
    is written BEFORE the JDBC commit that records the URL. A crash
    between the two leaves an orphaned S3 object. Same for delete: the JDBC
    row is updated, then the S3 object is deleted after commit; a crash in
    between leaves the object in the bucket while the DB no longer
    references it.

  ### Limitation acceptée

  Pas d'outbox pattern sur S3 (cf. ADR-003 — outbox réservé aux topics
  Kafka critiques). La JavaDoc des méthodes concernées documente la
  tolérance aux orphelins. Un cleanup périodique S3 est reporté à S9+
  (devops-handoff item dédié).

  ### Méthodes concernées

  - `user-service.UserService.uploadImage(...)` (avatar)
  - `user-service.UserService.uploadBanner(...)`
  - `user-service.UserService.deleteAvatar(...)` (delete S3 + reset URL)
  - `user-service.UserService.deleteBanner(...)` (NB: ne supprime PAS
    l'objet S3 par dessein — cf. JavaDoc inline)

  ### Mitigation

  - Bucket public read-only ; risque sécurité limité à un orphelin
    inaccessible (URL perdue).
  - Bucket lifecycle policy S9+ : auto-delete after 30j d'inactivité.
  ```

- **Validation** :
  ```bash
  grep -n 'ModerationCleanupService' backend/docs/data-model.md  # doit être 0 (sauf si commentaire historique)
  grep -n 'S3 cleanup hors-transaction' backend/docs/data-model.md  # ≥ 1
  ```
- **Doc à mettre à jour** : data-model.md lui-même.
- **Commit attendu** :
  ```
  docs(backend): correct ModerationCleanupJob path + add S3-orphan-tolerance section in data-model.md (Étape 24.9.6, D9)
  ```

---

#### Étape 24.9.7 — D10 + D11 + D12 : sed batch artifacts dans `shared-domain-dtos` et `FollowService`

- **Findings** : D10, D11, D12 (comment-analyzer I1).
- **Sévérité** : IMPORTANT (sed batch artifacts).
- **Fichiers touchés** :
  - `backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/domain/dto/UserPublicResponse.java:12`
  - `backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/domain/dto/FollowCounts.java:7`
  - `backend/services/user-service/src/main/java/ch/unige/events/user/follow/service/FollowService.java:144`
- **Patch (formulations cibles)** :

  - **D10** `UserPublicResponse.java:12` — remplacer la mention sed-batch (« engagement-service, user-service, engagement-service — all renamed/co-located post-finalization ») par :

    ```java
    /**
     * Public projection of a user — produced by user-service, consumed by
     * event-service (organizer enrichment) and engagement-service (comment
     * author + attendance display).
     */
    ```

  - **D11** `FollowCounts.java:7` — sortir « (follow-service co-located post-finalization) » de l'URL :

    ```java
    // AVANT
    /**
     * Returned by user-service's {@code GET (follow-service co-located post-finalization) /users/{id}/follow-counts}.
     */
    // APRÈS
    /**
     * Returned by user-service's {@code GET /users/{id}/follow-counts}
     * (follow-service was absorbed into user-service post-finalization,
     * cf. sprint-context.md § Étape 23).
     */
    ```

  - **D12** `FollowService.java:144` — reformuler les 3 affirmations contradictoires :

    ```java
    // AVANT (contradictoire)
    // user-service runs it (follow-service co-located post-finalization)
    // inline so ... without a REST call to user-service (which doesn't exist
    // yet — replaced at PR 12)

    // APRÈS
    // ISSUE-93 anti-oracle check inline: user-service hosts both Follow and
    // User entities post-finalization (Étape 2.3 — cf. sprint-context.md §
    // Étape 23), so this lookup is a local query, not a REST call.
    ```

- **Validation** :
  ```bash
  grep -rn 'engagement-service, user-service, engagement-service' backend/services/  # doit être 0
  grep -rn '(follow-service co-located post-finalization)' backend/services/shared-domain-dtos/  # doit être 0
  grep -rn 'replaced at PR 12' backend/services/  # doit être 0
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  docs(backend): clean sed-batch artifacts in shared-domain-dtos and FollowService (Étape 24.9.7, D10+D11+D12)
  ```

---

#### Étape 24.9.8 — D13 : `application.properties` event-service ligne 64 inversée

- **Finding** : D13 (comment-analyzer I9).
- **Sévérité** : MINEUR.
- **Fichier touché** :
  - `backend/services/event-service/src/main/resources/application.properties:63-65`
- **Patch (formulation cible)** :

  ```properties
  # AVANT
  # Note: consumers (notification-service) are out of scope in S8 (cf. SCRUM-99 follow-up).

  # APRÈS
  # Note: notification-service is shipped post-finalization (placeholder
  # replicas:0). Wiring it as a consumer of events.lifecycle is deferred to
  # SCRUM-99 — its deployment exists but no consumer is bound to these
  # topics yet.
  ```

- **Validation** :
  ```bash
  grep -n 'out of scope in S8' backend/services/event-service/src/main/resources/application.properties  # doit être 0
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  docs(event): correct notification-service status comment in application.properties (Étape 24.9.8, D13)
  ```

---

#### Étape 24.9.9 — D14 : `application.properties` user-service ligne 49-50 commentaire orphelin

- **Finding** : D14 (comment-analyzer I10).
- **Sévérité** : MINEUR.
- **Fichier touché** :
  - `backend/services/user-service/src/main/resources/application.properties:49-50`
- **Patch** : option (a) supprimer le commentaire orphelin si la propriété AWS metric publisher n'est pas requise. Vérifier d'abord si les tests posent un problème CloudWatch.

  ```bash
  cd backend && ./mvnw -B -pl services/user-service -DskipITs verify 2>&1 | grep -i cloudwatch
  ```

  Si aucun problème : supprimer les 2 lignes de commentaire.

  Si problème : ajouter la propriété appropriée :

  ```properties
  %test.quarkus.s3.aws.advanced-options.enable-default-metric-publisher=false
  ```

- **Validation** :
  ```bash
  grep -n 'CloudWatch endpoint that doesn' backend/services/user-service/src/main/resources/application.properties
  # Soit absent, soit suivi d'une propriété cohérente.
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  docs(user): clean orphan AWS metric publisher comment in application.properties (Étape 24.9.9, D14)
  ```

---

#### Étape 24.9.10 — D15 : `ServiceIdentityResource.java:18` « 14 microservices »

- **Finding** : D15 (comment-analyzer + code-reviewer MINOR-3).
- **Sévérité** : MINEUR.
- **Fichier touché** :
  - `backend/services/shared-platform/src/main/java/ch/unige/events/shared/platform/ServiceIdentityResource.java:18`
- **Patch** :

  ```java
  // AVANT
  // ... this single bean serves all 14 microservices without per-service copies (REFACTOR-012)

  // APRÈS
  // ... this single bean serves all 5 active microservices (4 actifs + 1
  // placeholder notification) without per-service copies (REFACTOR-012,
  // post-consolidation 14→5 cf. sprint-context.md § Étape 23).
  ```

- **Validation** :
  ```bash
  grep -n 'all 14 microservices' backend/services/shared-platform/src/main/java/  # doit être 0
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  docs(shared-platform): update ServiceIdentityResource JavaDoc to 5-service topology (Étape 24.9.10, D15)
  ```

---

#### Étape 24.9.11 — D16 + D17 : `shared-domain-dtos` listes consumers + pom.xml commentaire

- **Findings** : D16, D17 (comment-analyzer I2).
- **Sévérité** : MINEUR.
- **Fichiers touchés** :
  - `backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/domain/dto/AttendanceSummary.java:6-7`
  - `backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/domain/dto/CoOrganizerCheck.java:7-8`
  - `backend/services/shared-domain-dtos/pom.xml:13-29` (XML commentaire)
- **Patch (formulations cibles)** :

  - **D16** `AttendanceSummary.java` — remplacer :

    ```java
    // AVANT
    // consumed by event-service (capacity gating), co-organizer-service (display), stats-service (aggregation)

    // APRÈS
    // produced by engagement-service ; consumed by event-service (capacity
    // gating + DTO enrichment) — co-organizer-service / stats-service /
    // comment-service / attendance-service were all absorbed post-Étape 2.X
    // (cf. sprint-context.md § Étape 23).
    ```

  - **D16** `CoOrganizerCheck.java` :

    ```java
    // APRÈS
    // produced by event-service ; consumed by engagement-service
    // (CommentService + ReportService cascade SCRUM-136 check).
    ```

  - **D17** `pom.xml:13-29` — purger les services inexistants (follow-service, attendance-service, co-organizer-service) du XML commentaire. Garder la liste des DTOs avec leur producer/consumer réel post-finalization.

- **Validation** :
  ```bash
  grep -rn 'co-organizer-service\|stats-service\|attendance-service\|comment-service' backend/services/shared-domain-dtos/  # ≤ 1 (le pom commentaire purgé peut encore en mentionner historiquement)
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  docs(shared-domain-dtos): align consumer lists with 5-service topology (Étape 24.9.11, D16+D17)
  ```

---

#### Étape 24.9.12 — D18 : `EventCoOrganizerService.decline` filtrage status

- **Finding** : D18 (code-reviewer MINEUR-5).
- **Sévérité** : MINEUR.
- **Décision retenue** : option (a) filtrer `status = PENDING` (sémantique stricte : decline ne s'applique que si l'invitation est PENDING ; un co-org accepté qui veut sortir passe par DELETE explicite).
- **Fichier touché** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerService.java:117-129`
- **Patch** :

  ```java
  // AVANT
  EventCoOrganizer co = EventCoOrganizer.findByEventAndUser(eventId, userId)
          .orElseThrow(() -> ApiErrors.unprocessable("no_pending_invitation", "..."));
  co.delete();

  // APRÈS
  EventCoOrganizer co = EventCoOrganizer.findByEventAndUser(eventId, userId)
          .filter(c -> c.status == CoOrganizerStatus.PENDING)
          .orElseThrow(() -> ApiErrors.unprocessable("no_pending_invitation",
              "No PENDING invitation found for this user/event combination. " +
              "Already accepted invitations must be removed via DELETE /events/{id}/co-organizers/{userId}."));
  co.delete();
  ```

- **Test sentinel** : `EventCoOrganizerServiceTest.decline_acceptedInvitation_throws422()` — pin la sémantique stricte.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest=EventCoOrganizerServiceTest test
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(event): restrict decline to PENDING invitations only (Étape 24.9.12, D18)
  ```

---

#### Étape 24.9.13 — D19 : `EventCoOrganizerService.invite` 404 trompeur

- **Finding** : D19 (silent-failure-hunter MIN-3 + comment-analyzer).
- **Sévérité** : MINEUR.
- **Fichier touché** :
  - `backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerService.java:75-78`
- **Patch** :

  ```java
  // AVANT
  UserPublicResponse target = safeGetUser(targetUserId);
  if (target == null) {
      throw new NotFoundException("Target user not found");
  }

  // APRÈS
  UserPublicResponse target;
  try {
      target = userClient.getById(targetUserId);
  } catch (NotFoundException e) {
      throw new NotFoundException("Target user does not exist (or was deleted).");
  } catch (RuntimeException e) {
      Log.warnf(e, "[INVITE_USER_LOOKUP_FAIL] eventId=%d targetUserId=%s — user-service unreachable", eventId, targetUserId);
      throw new ServiceUnavailableException("User lookup is temporarily unavailable. Please retry.");
  }
  if (target == null) {
      // userClient.getById returned null (fallback path) — same semantics as 404 from caller's perspective.
      throw new NotFoundException("Target user does not exist (or was deleted).");
  }
  ```

- **Test sentinel** : `EventCoOrganizerServiceTest.invite_userServiceDown_throws503()` — mock CB ouvert.
- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/event-service -Dtest=EventCoOrganizerServiceTest test
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(event): distinguish user-not-found from user-service-down in invite (Étape 24.9.13, D19)
  ```

---

#### Étape 24.9.14 — D20 : `OptimisticLockException` `UserService` préserve la cause

- **Finding** : D20 (code-reviewer MINEUR-4).
- **Sévérité** : MINEUR.
- **Fichier touché** :
  - `backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java:135-143`
- **Patch** :

  ```java
  // AVANT
  } catch (OptimisticLockException exception) {
      throw new OptimisticLockException("Profile was updated by another request. Please retry.");
  }

  // APRÈS — préserver la cause + envelope ApiErrorResponse cohérent
  } catch (OptimisticLockException exception) {
      throw new WebApplicationException(
          Response.status(Response.Status.CONFLICT)
                  .entity(new ApiErrorResponse("optimistic_lock_conflict",
                      "Profile was updated by another request. Please retry."))
                  .type(MediaType.APPLICATION_JSON)
                  .build()
      );
  } catch (PersistenceException exception) {
      // Fallback couvert ailleurs — propager.
      throw exception;
  }
  ```

- **Validation** :
  ```bash
  cd backend && ./mvnw -B -pl services/user-service -DskipITs verify
  ```
- **Doc à mettre à jour** : aucune.
- **Commit attendu** :
  ```
  fix(user): wrap OptimisticLockException in 409 ApiErrorResponse + preserve cause (Étape 24.9.14, D20)
  ```

---

#### Étape 24.9.15 — D21 : AGENTS.md backend ligne 61 alignement soft-delete

- **Finding** : D21 (code-reviewer + observation transverse).
- **Sévérité** : MINEUR (doc).
- **Fichier touché** :
  - `backend/AGENTS.md:61`
- **Patch (formulation cible)** :

  ```markdown
  // AVANT
  - Soft-delete : champ `active` boolean sur Event, jamais de DELETE physique.

  // APRÈS
  - Soft-delete d'un Event : transition vers `EventStatus.CANCELLED` (le champ
    `status` porte la sémantique soft-delete ; il n'y a pas de booléen
    `active` séparé). Cf. `data-model.md`. Le DELETE physique d'un Event
    annulé est autorisé via `EventService.delete()` (cascade documentée).
  ```

- **Validation** :
  ```bash
  grep -n 'champ `active` boolean sur Event' backend/AGENTS.md  # doit être 0
  ```
- **Doc à mettre à jour** : AGENTS.md lui-même.
- **Commit attendu** :
  ```
  docs(backend): align AGENTS.md soft-delete description with EventStatus reality (Étape 24.9.15, D21)
  ```

---

#### Étape 24.9.16 — `internal-endpoints.md` référence ADR-002

- **Finding** : extension de A16 / Décision J.
- **Sévérité** : MINEUR (doc).
- **Fichier touché** :
  - `backend/docs/internal-endpoints.md`
- **Patch** : ajouter dans la sous-section dédiée à `GET /events/{id}/organizer-uuids` :

  ```markdown
  > **Note Décision J / ADR-002.** Cet endpoint reste annoté `@PermitAll`
  > plutôt que `@Internal`. Justification + mitigations : cf.
  > [`adr/ADR-002-organizer-uuids-permitall.md`](adr/ADR-002-organizer-uuids-permitall.md).
  > Sentinel test : `EventDomainSentinelsTest.getOrganizerUuids_bannedEvent_doesNotLeakUuidsToAnyCaller`
  > (Étape 24.7.4).
  ```

- **Validation** :
  ```bash
  grep -n 'ADR-002' backend/docs/internal-endpoints.md  # ≥ 1
  ```
- **Doc à mettre à jour** : internal-endpoints.md lui-même.
- **Commit attendu** :
  ```
  docs(backend): cross-link ADR-002 from internal-endpoints.md (Étape 24.9.16)
  ```

---

### Vague 10 — sprint-context.md final

#### Étape 24.10.1 — sprint-context.md § Étape 24

- **Sévérité** : -- (suivi).
- **Fichier touché** :
  - `backend/docs/sprint-context.md` (insérer en haut, avant § Étape 23).
- **Patch (préambule + structure)** :

  ```markdown
  ## Sprint 8 — Étape 24 : Total fix pré-merge — 2026-05-10

  Spec exécutée :
  [`specs_archives/specs_claude/specs_pr158_etape_24_total_fix.md`](../../specs_archives/specs_claude/specs_pr158_etape_24_total_fix.md)
  (branche persistante `refactor(backend)--migrate-to-microservices`,
  PR #158 — Elie merge lui-même). Source de vérité : review consolidée
  multi-agent (5 sous-agents `pr-review-toolkit`) — 26 findings + 10
  recoupés + 9 faux positifs arbitrés.

  ### Résultat — 56/56 items adressés

  | Vague | Items | Sous-étapes | Commits |
  |---|---|---|---|
  | 1 — Sécurité critique | A2, A4, A16 | 24.1.1 → 24.1.3 | 3 |
  | 2 — Sentinels TDD | C1, C2, C3 | 24.2.1 → 24.2.3 | 3 |
  | 3 — Observabilité silent failures | A5, A6, A7, A8, A9, A12, A13, A14 | 24.3.1 → 24.3.8 | 8 |
  | 4 — Kafka outbox EventBanned | A10 (3 sous-commits) | 24.4.1 | 3 |
  | 5 — Robustness restant | A1, A3, A11, A15 | 24.5.1 → 24.5.4 | 4 |
  | 6 — Types | B1..B6 | 24.6.1 → 24.6.6 | 6 |
  | 7 — Tests intégration | C4..C9 + ADR-002 sentinel | 24.7.1 → 24.7.7 | 7 |
  | 8 — Refactors utiles | E1..E4 | 24.8.1 → 24.8.4 | 4 |
  | 9 — Documentation | D1..D21 | 24.9.2 → 24.9.16 | ~10 (regroupés) |
  | 10 — sprint-context final | -- | 24.10.1 | 1 |
  | **TOTAL** | **56** | **~52** | **~50** |

  ### Décisions techniques actées

  - **Décision J** (item A16) : `GET /events/{id}/organizer-uuids` reste
    `@PermitAll`. Formalisée dans `backend/docs/adr/ADR-002-organizer-uuids-permitall.md`.
    Sentinel test pin l'invariant filtre BANNED.
  - **Décision K** (item A10) : `events.banned` via outbox transactionnel ;
    les 4 autres topics restent best-effort. Formalisée dans
    `backend/docs/adr/ADR-003-event-banned-outbox-vs-best-effort.md`.
    Migration Flyway V18 + nouveau poller dans moderation-service.

  ### Build local

  ```
  cd backend && ./mvnw -B -DskipITs verify -T 1
  ```
  SUCCESS sur 17 modules à chaque sous-étape. Total commits Étape 24 : ~50.

  ### CI

  `gh pr checks 158 --watch` — tous verts (5 builds + Sonar Aggregate +
  2 SonarCloud). `Deploy / Preview` cancellé manuellement (intentionnel).

  ### Conséquence — PR #158 prête au merge

  - 0 BLOQUANT, 0 IMPORTANT non clos.
  - 56/56 items review consolidée adressés.
  - 35/35 findings audit final clos (Étape 23) toujours valides.
  - Invariants : frontend/openapi 0 ligne, 0 stub JPA, 17 modules dans le
    reactor.
  - Décisions A-K appliquées sans déviation.

  ---
  ```

  Mettre à jour aussi le résumé de tête `Dernière mise à jour : 2026-05-10 (Étape 24 — total fix pré-merge)`.

- **Validation** :
  ```bash
  grep -n 'Étape 24 : Total fix pré-merge' backend/docs/sprint-context.md  # ≥ 1
  ```
- **Doc à mettre à jour** : sprint-context.md lui-même.
- **Commit attendu** :
  ```
  docs(backend): record Étape 24 — total fix pré-merge in sprint-context.md (Étape 24.10.1)
  ```

---

## 3. Validation finale

À exécuter dans l'ordre, après le dernier commit de la Vague 10. Aucun item ne peut être skippé. Si l'un échoue : fixer avant d'annoncer la PR mergeable.

```bash
# 1. Build complet 17 modules
cd backend && ./mvnw -B verify -T 1
# Attendu : BUILD SUCCESS, 0 test failure, 0 warning de couverture en dessous des seuils.

# 2. Invariant : pas de stub JPA cross-service
find backend/services -name '*Stub.java'
# Attendu : (vide)

# 3. Invariant : frontend zéro-touch
git diff --shortstat origin/main HEAD -- frontend/
# Attendu : (vide)

# 4. Invariant : openapi zéro-touch
git diff --shortstat origin/main HEAD -- openapi/
# Attendu : (vide)

# 5. Helm chart valide + guards opérationnels
helm lint k8s/chart/
helm template k8s/chart/ -f k8s/chart/values.yaml > /tmp/rendered.yaml
grep -c 'replicas: 1' /tmp/rendered.yaml  # ≥ 2 (event-service + moderation-service guards)
helm template k8s/chart/ -f k8s/chart/values.yaml --set eventService.replicas=2 2>&1 | grep -q 'event-service must run at replicas:1 strict'
helm template k8s/chart/ -f k8s/chart/values.yaml --set moderationService.replicas=2 2>&1 | grep -q 'moderation-service must run at replicas:1 strict'
# Attendu : tous OK.

# 6. Push + watch CI
git push origin refactor\(backend\)--migrate-to-microservices
gh pr checks 158 --watch
# Attendu : tous verts sauf "Deploy / Preview" (cancellé manuellement, intentionnel).

# 7. Sonar quality gate
gh pr view 158 --json statusCheckRollup --jq '.statusCheckRollup[] | select(.name | contains("SonarCloud")) | "\(.name) → \(.conclusion)"'
# Attendu : SUCCESS (≥ 80 % coverage on new code, duplication ≤ 3 %, ratings A).

# 8. Compteur de commits ajoutés sur cette Étape 24
git log origin/main..HEAD --oneline | wc -l
# Attendu : ~ 188 + 50 = 238 (à ajuster selon le regroupement final).

# 9. Smoke test des Décisions A-K
grep -l 'Décision A\|Décision B\|Décision C\|Décision D\|Décision E\|Décision F\|Décision G\|Décision H\|Décision I' backend/docs/  # ≥ 1
test -f backend/docs/adr/ADR-001-moderation-cleanup-replicas-strict.md
test -f backend/docs/adr/ADR-002-organizer-uuids-permitall.md
test -f backend/docs/adr/ADR-003-event-banned-outbox-vs-best-effort.md

# 10. sprint-context.md a la section Étape 24
grep -n 'Étape 24 : Total fix pré-merge' backend/docs/sprint-context.md  # ≥ 1
```

**Critère de succès final** : tous les 10 checks passent → la PR #158 est prête au merge. Annoncer à Elie : « Étape 24 close, PR #158 prête au merge. ».

**Critère d'échec** : si un check échoue, **NE PAS** annoncer mergeable. Fixer le root cause + commit + push + relancer. Documenter la déviation dans sprint-context.md § Étape 24.

---

## 4. Mise à jour sprint-context.md

Cf. Étape 24.10.1 ci-dessus. La section nouvelle § « Étape 24 — Total fix pré-merge » est ajoutée en tête de `backend/docs/sprint-context.md`, **avant** la section § Étape 23, conservée intacte.

Mettre aussi à jour :

- L'en-tête `Dernière mise à jour : 2026-05-10 (Étape 24 — total fix pré-merge)`.
- Le tableau de fin (s'il existe) qui liste les sprints terminés.

---

## 5. Faux positifs / Décisions à acter

Synthèse des 9 faux positifs et arbitrages issus de la review consolidée. Chaque ligne reflète une décision tranchée pour cette spec ; elle n'a pas à être revisitée pendant l'exécution.

| # | Source review | Statut | Arbitrage |
|---|---|---|---|
| FP-1 | Agent 5 « B1/B3 » Helm comments « image upload stays on legacy-monolith » + resources citant services absorbés | **REQUALIFIÉ** | Initialement marqué BLOQUANT par comment-analyzer, c'est en réalité du comment-rot (pas un bug code). Sévérité IMPORTANT, traité Étape 24.9.2 (D1+D2+D3). |
| FP-2 | Agent 5 « B2 » `UserCalendarTokenResource` « calendar-service » | **REQUALIFIÉ** | Idem — comment-rot, traité Étape 24.9.2 (D2). |
| FP-3 | Agent 1 « AGENTS.md soft-delete via active boolean » (ligne 61) vs réalité (status enum) | **À CORRIGER (D21)** | Vrai drift doc, pas un bug code. Traité Étape 24.9.15 (D21) — AGENTS.md aligné avec EventStatus. |
| FP-4 | Agent 2 « FP-1 » `InternalTokenFilter` 404 intentionnel | **CONFIRMÉ FP** | Décision C SEC-002-bis. Anti-oracle vs pentesteurs, validé. Mais le manque de log warn est valide → inclus implicitement dans Étape 24.7.3 (C6) tests intégration. |
| FP-5 | Agent 2 « FP-4 » `EventLifecyclePublisher` swallow Kafka outage | **FP PARTIEL** | OK pour `EventLifecycle` (DB = book of record). PAS OK pour `EventBannedPublisher` (sécurité) → outbox (Étape 24.4.1, A10). |
| FP-6 | Agent 1 Décision G `/events/{id}/organizer-uuids` `@PermitAll` à confirmer | **DÉCISION J ACTÉE** | ADR-002 (Étape 24.1.3) formalise le status quo + sentinel test (Étape 24.7.4). |
| FP-7 | Agent 4 « B-4 » anti-oracle BANNED body comparison | **TEST HARDENING** | Suggestion légitime, traitée comme test sentinel (Étape 24.7.1, C4). |
| FP-8 | Agent 5 « PASS » JavaDoc Décision E pour les 4 EventDTO variants vs Agent 3 « drift » | **AGENT 3 CORRECT** | La JavaDoc est formellement présente (PASS Agent 5) mais ment sur la nullability (drift Agent 3). Traité Étape 24.6.5 (B1). |
| FP-9 | Agent 4 « M-3 » couples consumer/provider Pact incomplets | **HORS SCOPE** | Audit Pact contracts est une vague à part (devops PINFO Pact provider verification). Hors scope cette spec. |

### Décisions A → K (récap exhaustif)

- **A** Schéma `public` partagé conservé, Flyway redistribué par owner — Étape 23.1.1.
- **B** `pg_advisory_xact_lock(eventId)` pour le capacity gating — Étape 23.2.1.
- **C** Header `X-Internal-Token` validé par filter shared-jaxrs — Étape 23.3.2.
- **D** `MdcKafkaInterceptor` dans shared-tracing (producer + consumer) — Étape 23.3.1.
- **E** `EventDTO` 4 sous-packages event-service : JavaDoc justificatif (pas de consolidation) — Étape 23.4.4.
- **F** `TZ=Europe/Zurich` fixé dans Helm Deployments — Étape 23.4.5.
- **G** CASCADE-136-DRIFT vérifié — pas de remédiation nécessaire — Étape 23.2.4.
- **H** `ModerationDomainSentinelsTest` SCRUM-139 : 8 tests pin — Étape 23.1.3.
- **I** Doc + JavaDoc cleanup : sed batch ciblé + refonte 4 sections — Étapes 23.4.9 / 4.10 / 4.11 / 1.2.
- **J** (NOUVEAU) `/events/{id}/organizer-uuids` reste `@PermitAll` — Étape 24.1.3 (ADR-002).
- **K** (NOUVEAU) `events.banned` via outbox transactionnel ; 4 autres topics best-effort — Étape 24.4.1 (ADR-003).

---

## 6. Dépendances et risques

### 6.1 Ordre obligatoire (DAG)

| Sous-étape | Dépend de | Raison |
|---|---|---|
| C9 (Étape 24.7.7) | B3 (Étape 24.6.3) | Les helpers `isTerminal()/isFinal()/isOpen()` doivent exister avant les tests de pinning. |
| C6 (Étape 24.7.3) | A4 (Étape 24.1.1) | Les tests fail-closed dépendent de la nouvelle config sans default. |
| Refactor call-sites enums | B3 (Étape 24.6.3) | Les helpers doivent exister avant le `replace_all`. |
| C5 (Étape 24.7.2) | A5 (Étape 24.3.1) | Les tests `@Fallback` wiring vérifient que les fallbacks loggent. |
| Tests Pact post-B2 | B2 (Étape 24.6.4) | Le typage `Faculty` enum peut casser les Pact stubs `"sciences"` minuscule. |
| A10-2 outbox | A10-1 publishers + V18 migration | L'outbox poller dépend de la table créée par V18. |
| C1/C2/C3 sentinels TDD | Aucune (les fix code sont déjà en place) | Procédure TDD : test vert direct ; commenter localement le fix code pour confirmer rouge ; restaurer avant commit. |

### 6.2 Risques identifiés

| Risque | Mitigation |
|---|---|
| **Tests Pact cassent à B2** (`Faculty` enum) — provider envoie `"SCIENCES"` mais consumer attend `"sciences"` | Vérifier les stubs Pact dans `backend/contract-tests/` AVANT B2. Adapter si besoin. |
| **Test C3 (advisory lock concurrent) flaky** — DevServices Postgres parfois lent à démarrer | Augmenter timeout `done.await(15, TimeUnit.SECONDS)` à 30s si CI flaky. NE PAS retry silencieux. |
| **Migration V18 entre en conflit avec un déploiement existant** — `flyway_schema_history` peut être désynchronisé | `quarkus.flyway.out-of-order=true` + `validate-on-migrate=false` (déjà en place Décision A). V18 = migration nouvelle, pas conflit avec V1..V17. |
| **Outbox poller `@Scheduled` fire en parallèle si replicas > 1** | Helm guard ADR-001 + commentaire + sentinel `failsHelmInstallIfReplicasGT1`. |
| **B4 records cassent l'API REST** — Bean Validation sur record peut différer | Tester explicitement `CreateEventRequestTest` + `EventResourceTest` après migration. Records + Bean Validation OK depuis Quarkus 3.2+. |
| **A10 outbox introduit de la latence** — `events.banned` peut retarder de 10s | Acceptable pour modération (ADR-003 § Consequences). Documenter dans Grafana panel S9+. |
| **D8 ADR-001 vs values.yaml** — un opérateur futur peut être surpris que `replicas` ne soit pas dans values.yaml | Mitigation : commentaire renforcé dans deployment.yaml + ADR-001 explicite. |
| **Le grep-replace des call sites enums (B3) peut manquer un site** | Vérification finale `grep -rn '== EventStatus.CANCELLED \|\| .* == EventStatus.EXPIRED'` doit retourner 0 (sauf justifié par un commentaire `// keep explicit for clarity`). |

### 6.3 Contraintes Flyway

- **Pas de modification rétroactive** des migrations V1..V17 (Décision A).
- V18 (Étape 24.4.1) = nouveau fichier, ajouté à `moderation-service/src/main/resources/db/migration/`.
- Pas de migration cross-service (V18 est dans moderation-service uniquement, conforme à la redistribution Décision A).
- En `%test`, Hibernate `drop-and-create` ignore Flyway — vérifier que les tests utilisent les helpers `@QuarkusTest` + `TestFixtures` qui posent les rows nécessaires.

### 6.4 Contraintes Sonar

- Coverage on new code ≥ 80 %. Les nouveaux tests (C1-C9, sentinels enums, outbox poller) doivent compenser les nouvelles lignes (outbox, helpers enum, fallback logs).
- Duplication ≤ 3 %. Les 4 patterns `safeGetUser` quasi-identiques (A6) sont OK car déjà existants ; le refactor préserve la structure.
- Ratings A obligatoires. Aucune nouvelle complexité critique introduite.

### 6.5 Contraintes commit + CI

- Pas de squash, pas de force-push (188 + ~50 commits préservés).
- Chaque commit doit avoir le trailer `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.
- CI verte avant chaque push de fin de vague (`gh pr checks 158 --watch`).
- En cas d'échec CI : root cause analysis, jamais `--no-verify`.

---

## Annexe A — Traçabilité finding → étape → commit

| Finding | Vague | Étape | Commit attendu | Statut initial |
|---|---|---|---|---|
| A1 KAFKA_BOOTSTRAP_SERVERS rename | 5 | 24.5.1 | `chore(infra): align Kafka env var with consumed property name KAFKA_BOOTSTRAP_SERVERS (Étape 24.5.1, A1)` | TODO |
| A2 ShareService anti-oracle ISSUE-92 | 1 | 24.1.2 | `fix(event): apply ISSUE-92 anti-oracle guard on /events/{id}/share (Étape 24.1.2, A2)` | TODO |
| A3 EventExpirationJob Helm guard | 5 | 24.5.2 | `chore(infra): add Helm guard replicas:1 strict for event-service (EventExpirationJob) (Étape 24.5.2, A3)` | TODO |
| A4 INTERNAL_TOKEN default removed | 1 | 24.1.1 | `fix(backend): remove default INTERNAL_TOKEN value, fail-closed in prod (Étape 24.1.1, A4)` | TODO |
| A5 REST client fallback logs | 3 | 24.3.1 | `fix(shared-domain-dtos): add observability logs to all 8 REST client fallbacks (Étape 24.3.1, A5)` | TODO |
| A6 safeGetUser typed catch | 3 | 24.3.2 | `fix(backend): differentiate semantic 404 vs infra failure in safeGetUser (Étape 24.3.2, A6)` | TODO |
| A7 Auth0IdResolver malformed claim log | 3 | 24.3.3 | `fix(shared-domain-projections): log Auth0 uuid claim parse failure (Étape 24.3.3, A7)` | TODO |
| A8 EventBannedConsumer warn + MOD_BAN_LOST | 3 | 24.3.4 | `fix(event): elevate EventBannedConsumer missing event log to WARN with MOD_BAN_LOST id (Étape 24.3.4, A8)` | TODO |
| A9 EventService.delete cascade audit log | 3 | 24.3.5 | `fix(event): emit audit log on EventService.delete cascade (Étape 24.3.5, A9)` | TODO |
| A10 Kafka publishers tighten + outbox EventBanned | 4 | 24.4.1 | 3 commits (a/b/c) — cf. § Vague 4 | TODO |
| A11 acquireAdvisoryLock null throws | 5 | 24.5.3 | `fix(engagement): throw on null eventId in AttendanceService.acquireAdvisoryLock (Étape 24.5.3, A11)` | TODO |
| A12 removeAttendance log skip | 3 | 24.3.6 | `fix(engagement): log waitlist promotion skip when event-service unreachable (Étape 24.3.6, A12)` | TODO |
| A13 MdcKafkaProducer onAcknowledgement + JavaDoc | 3 | 24.3.7 | `fix(shared-tracing): log Kafka produce failure + correct interceptor.classes JavaDoc (Étape 24.3.7, A13)` | TODO |
| A14 FileStorageService policy fail error | 3 | 24.3.8 | `fix(shared-storage): elevate S3 policy apply failure to ERROR with errorId (Étape 24.3.8, A14)` | TODO |
| A15 Favorite unique-violation by name + log | 5 | 24.5.4 | `fix(event): match favorites unique-violation by name + log idempotent double-tap (Étape 24.5.4, A15)` | TODO |
| A16 ADR-002 organizer-uuids @PermitAll | 1 | 24.1.3 | `docs(backend): add ADR-002 formalizing /events/{id}/organizer-uuids @PermitAll (Étape 24.1.3, A16)` | TODO |
| B1 EventDTO variants align + JavaDoc | 6 | 24.6.5 | `refactor(event): align coOrganizerOf across 4 EventDTO variants + reword JavaDoc to match types (Étape 24.6.5, B1)` | TODO |
| B2 UserPublicResponse.faculty enum | 6 | 24.6.4 | `refactor(shared-domain-dtos): type UserPublicResponse.faculty as Faculty enum (Étape 24.6.4, B2)` | TODO |
| B3 Enums behavioral helpers | 6 | 24.6.3 | `refactor(shared-domain-enums): add behavioral helpers to EventStatus/CoOrganizerStatus/ReportStatus/FollowStatus + refactor call sites (Étape 24.6.3, B3)` | TODO |
| B4 Create/UpdateEventRequest records | 6 | 24.6.6 | `refactor(event): migrate Create/UpdateEventRequest to immutable records (Étape 24.6.6, B4)` | TODO |
| B5 ApiErrorResponse compact ctor | 6 | 24.6.1 | `refactor(shared-api-error): require non-null non-blank error code via compact ctor (Étape 24.6.1, B5)` | TODO |
| B6 X-Internal-Token constant centralized | 6 | 24.6.2 | `refactor(shared-tracing): re-use canonical X-Internal-Token constant from shared-jaxrs (Étape 24.6.2, B6)` | TODO |
| C1 EVENT-DELETE-001 sentinel | 2 | 24.2.1 | `test(event): pin EVENT-DELETE-001 cascade EventCoOrganizer/Favorite/EventView (Étape 24.2.1, C1)` | TODO |
| C2 BUG-006-bis sentinel | 2 | 24.2.2 | `test(event): pin BUG-006-bis catch ConstraintViolationException branch (Étape 24.2.2, C2)` | TODO |
| C3 BUG-005-bis sentinel | 2 | 24.2.3 | `test(engagement): pin BUG-005-bis advisory lock concurrent burst (Étape 24.2.3, C3)` | TODO |
| C4 ISSUE-92 body equivalence | 7 | 24.7.1 | `test(event): pin ISSUE-92 anti-oracle body equivalence across unknown/DRAFT/BANNED (Étape 24.7.1, C4)` | TODO |
| C5 @Fallback wiring tests | 7 | 24.7.2 | `test(backend): pin @Fallback wiring on Event/Engagement/User REST clients (Étape 24.7.2, C5)` | TODO |
| C6 InternalToken integration tests | 7 | 24.7.3 | `test(backend): pin InternalToken mismatch + fail-closed + public endpoint scope (Étape 24.7.3, C6)` | TODO |
| ADR-002 sentinel | 7 | 24.7.4 | `test(event): pin ADR-002 BANNED filter on /events/{id}/organizer-uuids (Étape 24.7.4)` | TODO |
| C7 MdcKafka in-memory | 7 | 24.7.5 | `test(shared-tracing): pin X-Request-ID propagation via Kafka in-memory interceptor wiring (Étape 24.7.5, C7)` | TODO |
| C8 fake-green getOccurrences fix | 7 | 24.7.6 | `test(event): replace fake-green getOccurrences_sizeOver52 with real assertion (Étape 24.7.6, C8)` | TODO |
| C9 enum behavior tests | 7 | 24.7.7 | `test(shared-domain-enums): cover behavioral helpers added in B3 (Étape 24.7.7, C9)` | TODO |
| D1+D2+D3 Helm/resources legacy refs | 9 | 24.9.2 | `docs(backend): remove dead refs to legacy-monolith + absorbed services in Helm/resources (Étape 24.9.2, D1+D2+D3)` | TODO |
| D4 MdcKafka JavaDoc property name | 9 | 24.9.1 (couvert par A13) | (no extra commit) | TODO |
| D5 kong configmap target topology block | 9 | 24.9.3 | `docs(infra): remove obsolete "Target topology" block in kong configmap-routes (Étape 24.9.3, D5)` | TODO |
| D6+D7 PR N + 13 service blocks refs | 9 | 24.9.4 | `docs(infra): replace dead "PR N extraction roadmap" refs by sprint-context Étape 2.X links (Étape 24.9.4, D6+D7)` | TODO |
| D8 ADR-001 vs values.yaml | 9 | 24.9.5 | `docs(backend): align ADR-001 with hard-coded replicas:1 in moderation deployment (Étape 24.9.5, D8)` | TODO |
| D9 data-model.md refs + S3 limit | 9 | 24.9.6 | `docs(backend): correct ModerationCleanupJob path + add S3-orphan-tolerance section in data-model.md (Étape 24.9.6, D9)` | TODO |
| D10+D11+D12 sed batch artifacts | 9 | 24.9.7 | `docs(backend): clean sed-batch artifacts in shared-domain-dtos and FollowService (Étape 24.9.7, D10+D11+D12)` | TODO |
| D13 event-service S8 scope inversé | 9 | 24.9.8 | `docs(event): correct notification-service status comment in application.properties (Étape 24.9.8, D13)` | TODO |
| D14 user-service AWS metric publisher orphelin | 9 | 24.9.9 | `docs(user): clean orphan AWS metric publisher comment in application.properties (Étape 24.9.9, D14)` | TODO |
| D15 ServiceIdentityResource 14 microservices | 9 | 24.9.10 | `docs(shared-platform): update ServiceIdentityResource JavaDoc to 5-service topology (Étape 24.9.10, D15)` | TODO |
| D16+D17 shared-domain-dtos consumers + pom | 9 | 24.9.11 | `docs(shared-domain-dtos): align consumer lists with 5-service topology (Étape 24.9.11, D16+D17)` | TODO |
| D18 EventCoOrganizerService.decline status filter | 9 | 24.9.12 | `fix(event): restrict decline to PENDING invitations only (Étape 24.9.12, D18)` | TODO |
| D19 EventCoOrganizerService.invite distinguish | 9 | 24.9.13 | `fix(event): distinguish user-not-found from user-service-down in invite (Étape 24.9.13, D19)` | TODO |
| D20 OptimisticLockException preserve cause | 9 | 24.9.14 | `fix(user): wrap OptimisticLockException in 409 ApiErrorResponse + preserve cause (Étape 24.9.14, D20)` | TODO |
| D21 AGENTS.md soft-delete align | 9 | 24.9.15 | `docs(backend): align AGENTS.md soft-delete description with EventStatus reality (Étape 24.9.15, D21)` | TODO |
| internal-endpoints.md ADR-002 cross-link | 9 | 24.9.16 | `docs(backend): cross-link ADR-002 from internal-endpoints.md (Étape 24.9.16)` | TODO |
| E1 FavoriteService bulk fetch | 8 | 24.8.1 | `perf(event): replace N+1 query in FavoriteService.getFavorites with bulk fetch (Étape 24.8.1, E1)` | TODO |
| E2 EventService read-timeout 1s | 8 | 24.8.2 | `perf(event): tighten engagement-service read-timeout to 1s on read paths (Étape 24.8.2, E2)` | TODO |
| E3 EnumParamConverterProvider @Priority | 8 | 24.8.3 | `refactor(shared-jaxrs): use @Priority instead of hard-coded Timeframe skip in EnumParamConverterProvider (Étape 24.8.3, E3)` | TODO |
| E4 unused imports | 8 | 24.8.4 | `style(backend): remove unused imports in EventService + UserService (Étape 24.8.4, E4)` | TODO |
| sprint-context.md § Étape 24 | 10 | 24.10.1 | `docs(backend): record Étape 24 — total fix pré-merge in sprint-context.md (Étape 24.10.1)` | TODO |

**Total** : 56 items, ~50 commits (regroupements doc + 3 sub-commits A10).

---

## Annexe B — Mapping items review consolidée → finding-IDs

| Source review consolidée | Finding-ID spec | Étape |
|---|---|---|
| code-reviewer IMPORTANT-1 | A1 | 24.5.1 |
| code-reviewer IMPORTANT-2 | A2 | 24.1.2 |
| code-reviewer IMPORTANT-3 | A3 | 24.5.2 |
| code-reviewer IMPORTANT-4 + comment-analyzer I11 | A4 | 24.1.1 |
| silent-failure-hunter CRIT-1 | A5 | 24.3.1 |
| silent-failure-hunter CRIT-2 | A6 | 24.3.2 |
| silent-failure-hunter CRIT-3 | A7 | 24.3.3 |
| silent-failure-hunter CRIT-4 | A8 | 24.3.4 |
| silent-failure-hunter CRIT-5 | A9 | 24.3.5 |
| silent-failure-hunter IMP-1 + MIN-6 | A10 | 24.4.1 |
| silent-failure-hunter IMP-3 | A11 | 24.5.3 |
| silent-failure-hunter IMP-4 | A12 | 24.3.6 |
| silent-failure-hunter IMP-5 + comment-analyzer B4 | A13 | 24.3.7 |
| silent-failure-hunter IMP-6 | A14 | 24.3.8 |
| silent-failure-hunter IMP-2 | A15 | 24.5.4 |
| code-reviewer Décision G + pr-test-analyzer I-5 | A16 | 24.1.3 |
| type-design-analyzer EventDTO drift | B1 | 24.6.5 |
| type-design-analyzer UserPublicResponse.faculty | B2 | 24.6.4 |
| type-design-analyzer enums anémiques | B3 | 24.6.3 |
| type-design-analyzer EventRequestBase mutable | B4 | 24.6.6 |
| type-design-analyzer ApiErrorResponse no validation | B5 | 24.6.1 |
| type-design-analyzer X-Internal-Token duplicate constant | B6 | 24.6.2 |
| pr-test-analyzer B-1 | C1 | 24.2.1 |
| pr-test-analyzer B-2 | C2 | 24.2.2 |
| pr-test-analyzer B-3 | C3 | 24.2.3 |
| pr-test-analyzer B-4 | C4 | 24.7.1 |
| pr-test-analyzer I-2 | C5 | 24.7.2 |
| pr-test-analyzer I-3 | C6 | 24.7.3 |
| pr-test-analyzer I-4 | C7 | 24.7.5 |
| pr-test-analyzer I-6 | C8 | 24.7.6 |
| dérivation B3 | C9 | 24.7.7 |
| comment-analyzer B1 | D1 | 24.9.2 |
| comment-analyzer B2 | D2 | 24.9.2 |
| comment-analyzer B3 | D3 | 24.9.2 |
| comment-analyzer B4 | D4 | (couvert par A13/24.3.7) |
| comment-analyzer I3 | D5 | 24.9.3 |
| comment-analyzer I4 | D6 | 24.9.4 |
| comment-analyzer I5 | D7 | 24.9.4 |
| comment-analyzer I6 | D8 | 24.9.5 |
| comment-analyzer I7 + I8 | D9 | 24.9.6 |
| comment-analyzer I1 (UserPublicResponse) | D10 | 24.9.7 |
| comment-analyzer I1 (FollowCounts) | D11 | 24.9.7 |
| comment-analyzer I1 (FollowService) | D12 | 24.9.7 |
| comment-analyzer I9 | D13 | 24.9.8 |
| comment-analyzer I10 | D14 | 24.9.9 |
| code-reviewer MINOR-3 + comment-analyzer | D15 | 24.9.10 |
| comment-analyzer I2 (AttendanceSummary/CoOrganizerCheck) | D16 | 24.9.11 |
| comment-analyzer (pom.xml) | D17 | 24.9.11 |
| code-reviewer MINEUR-5 | D18 | 24.9.12 |
| silent-failure-hunter MIN-3 | D19 | 24.9.13 |
| code-reviewer MINEUR-4 | D20 | 24.9.14 |
| dérivation transverse | D21 | 24.9.15 |
| code-reviewer MINEUR-1 | E1 | 24.8.1 |
| code-reviewer MINEUR-6 | E2 | 24.8.2 |
| type-design-analyzer @Priority | E3 | 24.8.3 |
| code-reviewer NIT-2 | E4 | 24.8.4 |

---

**FIN DE LA SPEC.** Cette spec est exécutable telle quelle par un agent autonome (Claude Code en bypass-permissions). Toute déviation doit être actée explicitement dans le commit message + sprint-context.md § Étape 24.

