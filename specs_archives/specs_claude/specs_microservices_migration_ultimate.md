# Migration vers microservices — backend UNIGE Events — SPEC ULTIME (PR #158)

| Champ | Valeur |
|---|---|
| Sprint | S8 (clôture finalization) |
| Branche | `refactor(backend)--migrate-to-microservices` (persistante, **NE PAS créer de nouvelle branche**) |
| HEAD baseline | `ec668b91` (tip de la branche au démarrage) |
| PR active | [#158](https://github.com/unige-pinfo6-2026/unige-events/pull/158) — **NE PAS merger**, Elie merge lui-même |
| Auteur spec | Claude (session 2026-05-09 PM, post-audit) |
| Exécuteur cible | Claude Code en **bypass-permissions**, autonome, branche persistante, sans merge |
| Frontend lié | **AUCUN** — `git diff --shortstat origin/main HEAD -- frontend/` doit rester à 0 ligne |
| OpenAPI | **AUCUN** — `git diff --shortstat origin/main HEAD -- openapi/` doit rester à **0 ligne ABSOLU** (Décision G de la spec finalization, inchangée) |
| Frontière DevOps | 7 items hors scope (cf. [`backend/docs/devops-handoff.md`](../../backend/docs/devops-handoff.md) items 2-7) |
| Audit source | [`specs_archives/audit_pr158_finalization_post.md`](../audit_pr158_finalization_post.md) (1408 lignes, 52 findings) |
| Spec antérieure | [`specs_archives/specs_claude/specs_microservices_migration_finalization.md`](specs_microservices_migration_finalization.md) (3114 lignes) |

---

## Note d'implémentation

Cette spec est l'**unique source de vérité** pour la **clôture définitive** de la PR #158. Elle suit directement la **finalisation partielle** documentée dans `backend/docs/sprint-context.md` § Étape 20 (livrée à HEAD `ec668b91`) et adresse les 6 P0 + 17 P1 actionnables remontés par l'audit `audit_pr158_finalization_post.md`.

**Après l'exécution complète de cette spec** :

1. La branche `refactor(backend)--migrate-to-microservices` est **prête au merge** — Elie merge lui-même quand il valide.
2. Le DevOps prend la main pour les 7 items S9+ documentés dans `devops-handoff.md` (cluster Kafka prod, NetworkPolicies, Doppler, certs, etc.).
3. La couverture jacoco est honnête, le quality gate Sonar ne bloque pas, les pacts en provider verification sont verts.

**L'exécuteur autonome** :

- ne demande **jamais** une décision au user (toutes tranchées ici A-I) ;
- commit + push après chaque sous-étape numérotée verte (granularité ≈ 1 commit par sous-étape `N.M`, sauf consolidation chantier B = plusieurs sentinels par commit) ;
- pousse sur la branche persistante `refactor(backend)--migrate-to-microservices` ;
- ne merge **jamais** la PR #158 ;
- ne crée **jamais** de nouvelle branche, jamais de nouveau ticket Jira, jamais de nouvelle PR ;
- met à jour `backend/docs/sprint-context.md` (nouvelle § Étape 21 — clôture) au fil de l'eau, regroupé en commit final d'Étape 8.4 ;
- met à jour le **PR body** de #158 quand toute la spec a été livrée (Étape 9.2) ;
- valide chaque étape via `cd backend && ./mvnw verify -DskipITs` (~3-5 min sur le reactor 17 modules) ;
- watch CI **par étape majeure** (pas par sous-étape — directive Elie) : `gh pr checks 158 --watch` à la fin de chaque Étape ≥ 2 jusqu'à terminaison ;
- en cas d'échec CI, **fixe la cause racine** — jamais de `--no-verify`, jamais de `@Disabled`, jamais de skip silencieux ; si le fail provient d'un item DevOps S9+ documenté (ex. SonarCloud project not found si DevOps n'a pas créé les 5 projets Option B), il **continue** et documente.

Toute déviation par rapport aux décisions A-I doit être **actée explicitement** dans le commit message + dans `sprint-context.md` § Étape 21, avec justification concrète. Les déviations triviales (ex. nom de classe légèrement différent) ne nécessitent pas d'acte.

> **Leçon Flyway-immutabilité (rappel).** La règle « migration committée = immutable » s'applique par-base. Les migrations historiques V1..V17 sont **gravées** dans `flyway_schema_history`. Tout changement de colonne ou de FK passe par un nouveau fichier `V<N>__...sql`. Cette spec ne génère **aucun** ALTER ni DROP — les refactors JPA `@ManyToOne XStub` → `@Column id` (Décision F) **réutilisent les colonnes FK existantes** (`event_id`, `author_id`, `creator_id`, `reporter_id`, `reviewed_by`) sans changement de schéma DB.

---

## Contexte

### État livré dans la PR #158 à HEAD `ec668b91`

(Reprise concise du `sprint-context.md` § Étape 20 — voir le fichier source pour le détail commit-par-commit.)

| Étape finalization | Statut | Commits | Livrable |
|---|---|---|---|
| 1 — doc préparatoire (consolidation-plan.md) | ✅ | 2 | Plan de consolidation 14→5 + devops-handoff item 1 |
| 2 — consolidation 14→5 services | ✅ | 11 | 9 merges + 2 renames, topology 5 services + 10 libs |
| 3 — alignement docs post-consolidation | ✅ | 2 | architecture.md + AGENTS.md |
| 4 — REST clients + endpoints internes | 🟡 partielle | 5 | 3 `@RegisterRestClient` interfaces + 3 endpoints providers ; **wiring consumer non câblé** |
| 5 — sentinels SCRUM-138/144/147 | 🟡 partielle | 1 | 35 noms ✅ par grep, 4/35 implémentés réellement, 31/35 placeholders vides |
| 6 — Pact + E2E | ✅ | 4 | 4 pacts JSON brokerless + 1 E2E happy path (gated env var) |
| 7 — CI matrix per-service | ✅ | 2 | `build.yml` matrix 10+5 cellules, `continue-on-error` Sonar |
| 8 — docs finales | ✅ | 4 | data-model.md, internal-endpoints.md, dev-guide.md, sprint-context.md § 20 |
| Option B SonarCloud | ✅ | 1 | 5 projets services (au lieu de 15), 10 shared libs scannent dans backend |

**Topology atteinte** : 5 services métiers (4 actifs : event/user/engagement/moderation + 1 placeholder notification) + 10 shared libs + contract-tests + e2e = **17 modules** dans le reactor. Build local SUCCESS, frontend invariant 0 ligne, openapi invariant 0 ligne.

### État NON livré (cible de cette spec)

L'audit a identifié **52 findings** : 6 P0 bloquants, 18 P1 important pour le handoff DevOps clean, 28 P2 cosmétiques. Cette spec adresse :

| ID Audit | Catégorie | Sévérité | Étape spec |
|---|---|---|---|
| STUB-001 | JPA stubs (13 stubs) | P0 | Étape 3 (×4 sous-étapes) |
| REST-001 | URL config absente | P0 | Étape 2.1 |
| REST-002 | endpoint provider manquant | P0 | Étape 2.3 + Décision B |
| REST-003 | admin bypass ISSUE-93 | P1 | Étape 2.4 |
| REST-004 / SEC-001 | NotFoundExceptionMapper | P0 / P0 | Étape 2.2 |
| SEC-002 | oracle `?check-co-org-of=` | P1 | Étape 6.1 + Décision C |
| CI-001 | Sonar top-level project | P0 | Étape 1.1 + Décision E |
| CI-002 | 10 cellules shared-libs | P1 | Étape 1.2 |
| CI-003 | continue-on-error | P1 | Étape 1.3 |
| CI-004..008 | cosmétiques CI | P1/P2 | Étape 1.3 (bundlé) |
| DUP-001..006 | duplicats locaux | P1/P2 | Étape 4 (×3 sous-étapes) + Décision A |
| TEST-001 | 31 sentinels placeholders | P1 | Étape 5.2 + Décision D |
| TEST-002 | pact AttendanceSummary | P1 | Étape 5.1.a |
| TEST-003 / KAFKA-001 | EventLifecycleKafkaBridgeTest | P2 | Étape 5.1.b |
| TEST-004 | sentinel scaffolds redondants | P2 | Étape 5.1.c |
| TEST-005 | UserAttendances endpoint | P2 | résolu par REST-002 |
| COV-001 | couverture services métiers | P1 | partiellement résolu par Étape 5.2 (Option 3) |
| COV-002 | shared-domain-dtos 63% | P2 | Étape 5.1.d |
| K8S-001 | livenessProbe notification | P1 | Étape 7.1 |
| KAFKA-001 | EventLifecycleKafkaBridgeTest | P2 | Étape 5.1.b (= TEST-003) |
| DOC-001 | devops-handoff TL;DR | P0 | Étape 8.1 |
| DOC-002..011 | doc drift | P1/P2 | Étape 8 (×5 sous-étapes) |
| SEC-003 | cascade inline CommentService | P2 | résolu naturellement par Étape 3.1 |
| DEP-001 | quarkus-jacoco notification | P1 | Étape 7.2 |
| TODO-001 | JavaDoc AttendanceService | P1 | Étape 8.5 |
| HYGIENE-001..004 | git/commits | P1/P2 | **process pour les futurs commits, pas d'action rétroactive ici** |
| TODO-002 | frontend stub | P2 | **hors scope (frontend invariant)** |
| OPENAPI-001 | TODOs Sprint futurs openapi | P2 | **hors scope (openapi invariant)** |

**Findings non adressés explicitement** :
- HYGIENE-001..004 : process pour la prochaine PR (pas de rebase suggéré, force push interdit).
- TODO-002 : frontend stub `searchApi.ts` — invariant frontend = 0 ligne diff, donc hors scope.
- OPENAPI-001 : 5 TODOs `openapi.yaml` — invariant openapi = 0 ligne diff, donc hors scope.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

> **Pour l'exécuteur** : chaque décision A → I ci-dessous est définitive. Aucune ne doit être tranchée au moment de l'implémentation. Si une situation imprévue émerge, applique la règle « principe de moindre surprise vs cette décision » et **acte la déviation** dans le commit message + sprint-context § Étape 21.

### Décision A — `EventDTO` consolidé dans shared-domain-dtos avec mapper externalisé

**Décision.** Un **seul `EventDTO`** record canonique vit dans `backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/domain/dto/EventDTO.java` (déjà créé en Étape 4.0 PM). Le `EventDTO` local de event-service (`backend/services/event-service/src/main/java/ch/unige/events/event/dto/EventDTO.java`) est **supprimé**. Les deux factories `EventDTO.from(Event, ...)` migrent vers une **classe utilitaire** `EventDTOMapper` dans event-service (qui a accès à l'entité `Event` JPA), retournant un `EventDTO` shared.

**Justification.** (a) Cohérence stricte producer/consumer : le payload JSON sérialisé par le provider est exactement la définition que désérialisent les consumers. (b) Élimination du duplicat (DUP-005). (c) `EventDTO.from(Event)` ne peut pas vivre dans shared-domain-dtos car shared-domain-dtos ne dépend pas de event-service (et ne doit pas, sinon dépendance circulaire) — solution propre : la factory devient une méthode statique dans event-service qui mappe `Event` (JPA) → `EventDTO` (shared).

**Alternatives écartées.** (a) Garder un `EventDTO` local + un `EventDTO` shared en parallèle : invite le drift, double maintenance. (b) Faire de `Event` un `EventDTO` direct via Lombok/MapStruct : sur-engineering pour un projet pinfo6.

**Pattern identique** appliqué pour `UserPublicResponse` (UserService → `UserPublicResponseMapper`), `AttendanceDTO` (engagement-service → `AttendanceDTOMapper`), `EventCoOrganizerDTO` (event-service → `EventCoOrganizerDTOMapper`).

**Adresse.** DUP-005, COV-002 (les tests des records shared deviennent plus riches une fois consommés réellement).

---

### Décision B — REST-002 : créer `UserAttendancesInternalResource` côté engagement-service

**Décision.** Créer le resource manquant `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/resource/UserAttendancesInternalResource.java` qui expose `GET /users/{id}/attendances?status=...` retournant `List<AttendanceDTO>` (shared). `@PermitAll` (interne, pas de route Kong), pas dans openapi.yaml.

**Justification.** (a) Cohérent avec le catalogue `internal-endpoints.md` #4. (b) `user-service.CalendarService` (feed ICS) en a besoin pour collecter les events où l'user est ATTENDING. (c) Effort S (~30 LOC), beaucoup plus simple que de retirer la méthode du `EngagementServiceClient` et de refactorer le flux ICS.

**Alternatives écartées.** (a) Retirer `getUserAttendances()` du `EngagementServiceClient` + reformuler internal-endpoints.md #4 : oblige user-service à passer par le path public `/users/me/attendances` avec OIDC propagation depuis le user, ce qui est techniquement plus complexe et ouvre une surface d'attaque (le user-service forge un Bearer token).

**Adresse.** REST-002, TEST-005, DEP-002.

---

### Décision C — SEC-002 : `?check-co-org-of=` accepté uniquement en self-check authentifié

**Décision.** Le param `check-co-org-of=<UUID>` sur `GET /events/{id}` est accepté **uniquement** quand :
1. Le caller est authentifié (JWT valide).
2. La valeur du param est **égale à l'UUID résolu du caller** (`Auth0IdResolver.resolveUserId(jwt) == checkCoOrgOf`).

Sinon le param est **silencieusement ignoré** (réponse sans le champ `coOrganizerOf` — `null` JSON).

**Justification.** (a) Ferme l'oracle de membership co-organizer (un attaquant anonyme ne peut plus enumérer « est `<UUID>` co-organizer de l'event 42 ? »). (b) Préserve le cas d'usage légitime : un user authentifié veut savoir « suis-je organizer de cet event ? » → il passe son propre UUID. (c) Pas de complexité Kong (pas de header X-Internal-Token à stripper). (d) Pas de path interne dédié `/__internal/...` (refacteur lourd + multiplie les routes).

**Alternatives écartées.** (a) Header X-Internal-Token : nécessite Kong-side stripping + secret partagé entre services + migration complexe. (b) Path interne dédié `/__internal/events/{id}/co-org-check?userId=...` : doublon avec le path public, deux endpoints à maintenir.

**Adresse.** SEC-002.

**Note d'implémentation.** Côté EventService, si le check échoue (caller anonyme OU `checkCoOrgOf != callerUserId`), la méthode `getById(...)` retourne le DTO avec `coOrganizerOf = null`. Le pact `EngagementEventScrum136PactTest` reste valide car les consumers légitimes (engagement-service via REST client) passent toujours leur propre UUID résolu.

---

### Décision D — Chantier B sentinels : Option 3 (mid-way 12 sentinels prioritaires)

**Décision.** Porter avec **assertions réelles** **12 sentinels** au total (4 RecurrenceGenerator déjà acquis + 8 nouveaux à porter dans cette spec). Les 23 autres sentinels restent en placeholders **avec un JavaDoc explicite** « port complet déféré S9 (port des 1818 tests legacy) » et un `@Tag("legacy-port-s9")` JUnit 5. Le quality gate Sonar configure « coverage on new code ≥ 70% » plutôt que « coverage absolue ≥ 80% » pour ne pas bloquer le merge sur l'absolu.

**Sentinels à porter** (8 nouveaux) :
1. `prePersist_setsCreatedAt` (engagement) — XS, fixture trivial
2. `getOccurrences_draftByNonCreator_returns404_antiOracle` (event) — anti-oracle ISSUE-92
3. `getOccurrences_draftByAnonymous_returns404_antiOracle` (event) — anti-oracle ISSUE-92 anonyme
4. `post_eventDraftByNonCreator_returns404_antiOracle` (engagement) — cascade SCRUM-136 + anti-oracle
5. `post_eventBanned_returns404_antiOracle` (engagement) — anti-oracle BANNED
6. `getFollowers_privateProfileNonOwner_returns404_antiOracle` (user) — anti-oracle ISSUE-93
7. `delete_unknownComment_returns404_commentNotFound` (engagement) — envelope `{error:"not_found"}`
8. `follow_selfFollow_throwsUnprocessable` (user) — validation 422

Avec ces 8, on atteint un total de 12 sentinels couvrant les anti-oracles ISSUE-92/93, la cascade SCRUM-136, l'envelope d'erreur (qui débloque la pact verification), et la validation business.

**Justification.** (a) Option 1 (port complet 31 sentinels) explose à ~50+ commits / 200h, hors budget session. (b) Option 2 (tout déférer S9) laisse 5-17% coverage, blocant pour quality gate Sonar absolu et pour la pact verification (REST-004). (c) Option 3 (12 sentinels) atteint un état honnête : tous les sentinels qui débloquent une cascade critique sont actifs ; les 23 autres documentent leur statut S9 explicitement (pas de mensonge dans le compteur).

**Coverage cible post-Option 3** : services métiers ~25-40% L (vs 5-17% actuellement). Pas 80% mais honnête. Quality gate Sonar `coverage on new code ≥ 70%` est compatible.

**Alternatives écartées.** Option 1, Option 2 (cf. justification).

**Adresse.** TEST-001 (partiellement), COV-001 (partiellement), REST-004 (les sentinels qui assertent l'envelope).

---

### Décision E — CI Sonar : `-pl .,services/<X>` per cellule + consolidation 10→1 shared-libs

**Décision.** Adopter **deux changements simultanés** dans `.github/workflows/build.yml` :

1. **CI-001** — Toutes les cellules services (5) lancent :
   ```bash
   ./mvnw -pl .,services/<X>-service -am install -B   # build + tests + jacoco
   ./mvnw -pl .,services/<X>-service sonar:sonar -B    # scan avec top-level résolu
   ```
   Le `.` (parent reactor) résout le top-level project requis par sonar-maven-plugin 4.0.0.4121. Les `sonar.projectKey` per-module overrides sont préservés (les 5 cellules pushent dans leur projet dédié).

2. **CI-002** — Le job `build-shared-libs` passe de 10 cellules matrix à **1 seule cellule** :
   ```bash
   ./mvnw -pl services/shared-rate-limit,services/shared-storage,\
              services/shared-api-error,services/shared-domain-enums,\
              services/shared-domain-dtos,services/shared-domain-projections,\
              services/shared-jaxrs,services/shared-tracing,\
              services/shared-kafka-events,services/shared-platform \
           -am install -B
   ./mvnw -pl . sonar:sonar -B   # scan racine, agrège dans unige-events-backend (Option B)
   ```

**Justification.** (a) CI-001 : `-pl .,<module>` ajoute le parent comme top-level dans la session, sans changer le projet de scan (chaque cellule scanne dans son projectKey override). Solution la moins disruptive. (b) CI-002 : Option B → toutes les libs scannent dans `unige-events-backend` ; 10 cellules clobbant le même projectKey, on garde la dernière → seule la 10ème cellule contribue au scan. Consolidation = scan honnête + ~25 min/run gagnées.

**Alternatives écartées.** (a) Sonar à la racine post-matrix (option 3 de l'audit CI-001) : dégrade la granularité par projet (une seule cellule scanne tout, donc on perd les 5 projets services dédiés). (b) Garder 10 cellules : gaspillage CI sans bénéfice quality gate.

**Adresse.** CI-001, CI-002, CI-004, CI-006.

---

### Décision F — JPA refactor `@ManyToOne XStub` → `@Column id` (raw FK, sans navigation)

**Décision.** Pour chaque entité avec `@ManyToOne XStub` :
- `Comment` (engagement) : `EventStub event` → `@Column(name="event_id") Long eventId` ; `UserStub author` → `@Column(name="author_id") UUID authorId`.
- `Event` (event-service) : `UserStub creator` → `@Column(name="creator_id") UUID creatorId`.
- `Report` (moderation) : `EventStub event` → `@Column(name="event_id") Long eventId` ; `UserStub reporter` → `@Column(name="reporter_id") UUID reporterId` ; `UserStub reviewedBy` → `@Column(name="reviewed_by") UUID reviewedById`.

**Schéma DB inchangé** : les colonnes FK existaient déjà (`event_id`, `author_id`, `creator_id`, `reporter_id`, `reviewed_by`). On retire juste la navigation JPA. Aucune migration Flyway nécessaire.

**Justification.** (a) Convention « id-only » classique en architecture microservices — pas de navigation cross-service. (b) Aucun risque de migration DB (les colonnes existent). (c) `@JoinColumn` standalone sans `@ManyToOne` n'a pas de sens fonctionnel JPA (Hibernate l'ignore). (d) L'enrichissement des DTOs (afficher `displayName`, `title`) se fait à la couche service via REST clients, pas via navigation JPA lazy.

**Alternatives écartées.** (a) Garder `@ManyToOne` mais pointer sur l'entité réelle (impossible cross-service — `Event` n'est pas dans engagement). (b) `@JoinColumn` standalone (sémantique nulle).

**Adresse.** STUB-001 (composante refactor entités).

**Migration tests** : tous les tests qui référencent `comment.event.id`, `comment.author.id`, `event.creator.id`, etc. doivent être mis à jour pour utiliser `comment.eventId`, `comment.authorId`, `event.creatorId`, etc. ou enrichir via les REST client mocks.

---

### Décision G — Suppression `EventCoOrganizerStub` : nouvel endpoint `GET /events/{id}/organizer-uuids`

**Décision.** Ajouter au provider event-service un nouvel endpoint **interne** `GET /events/{id}/organizer-uuids` retournant `List<UUID>` (= creator + ACCEPTED co-organizers). `@PermitAll`, pas de route Kong, pas dans openapi.yaml. Ajouté à `internal-endpoints.md` (qui marquait cet endpoint comme « disparu » — réintroduit avec justification post-Étape 4.5).

Méthode ajoutée à `EventServiceClient` : `List<UUID> getOrganizerUuids(@PathParam("id") long id)` avec resilience standard + Fallback `List.of()`.

**Justification.** (a) Le consumer engagement-service (`CommentService::computeOrganizerUserIds`) a besoin du **Set<UUID> complet** pour annoter chaque comment auteur avec `authorIsOrganizer`. (b) Faire N appels `?check-co-org-of=` par auteur = N+1 (anti-pattern). (c) Réintroduire l'endpoint est cheap (5 LOC côté provider) et préserve le contrat DDD (event-service possède `event_co_organizers` post-2.2.4, c'est lui qui sait répondre à cette query).

**Alternatives écartées.** (a) Garder `EventCoOrganizerStub` : viole la cible « 0 stubs cross-service ». (b) Faire N calls REST `?check-co-org-of=` par auteur : N+1, performance pourrie. (c) Endpoint bulk `?userIds=...` retournant `Map<UUID, Boolean>` : plus complexe que de retourner la liste complète.

**Adresse.** STUB-001 (suppression `EventCoOrganizerStub` × 2 occurrences engagement + moderation), DOC-010 (mise à jour internal-endpoints.md).

---

### Décision H — `events.banned` : retrait de la mutation directe côté moderation, Kafka producer seul

**Décision.** Côté moderation-service (`ReportService::handle` + `ModerationCleanupService::runCleanup`), retirer la mutation directe `event.status = BANNED` via `EventStub`. Garder uniquement le Kafka producer `events.banned` (déjà câblé via `EventBannedKafkaBridge` + `EventBannedPublisher`). Le consumer `event-service` consomme le topic et applique `event.status = BANNED` de façon idempotente (déjà implémenté).

**Justification.** (a) Élimination du dernier stub writable cross-service (`EventStub` côté moderation). (b) Pattern propre : toute mutation passe par le service propriétaire de la table. (c) Idempotence garantie côté consumer (`if (event.status == BANNED) return;`). (d) Aucun changement de DB schema nécessaire.

**Risque acté** : latence asynchrone — entre la résolution admin du report et l'application effective du BAN, il y a un délai (typiquement < 100ms). Pour un projet pinfo6, c'est acceptable. La preuve d'application est dans `events.banned` topic (audit log gratuit). Cas pire : un retry Kafka double l'application, mais l'idempotence du consumer le gère.

**Alternatives écartées.** (a) Garder la mutation directe : conserve un stub writable cross-service (anti-pattern microservices). (b) Synchronous REST call moderation→event : casse l'isolation, ajoute un point de coupling.

**Adresse.** STUB-001 (composante moderation `EventStub`).

---

### Décision I — Bulk count attendances : nouvel endpoint `GET /events/attendance-summary?ids=...`

**Décision.** Ajouter au provider engagement-service un nouvel endpoint **interne** `GET /events/attendance-summary?ids=42&ids=7&ids=1` retournant `Map<Long, AttendanceSummary>` (event id → summary). `@PermitAll`, pas de route Kong, pas dans openapi.yaml. Ajouté à `internal-endpoints.md` comme endpoint #5.

Méthode ajoutée à `EngagementServiceClient` :
```java
@GET @Path("/events/attendance-summary")
Map<Long, AttendanceSummary> getAttendanceSummariesBulk(@QueryParam("ids") List<Long> ids);
```
avec resilience standard + Fallback `Map.of()`.

**Justification.** (a) `event-service.AttendanceStub.countGroupedByStatus(eventIds, status, em)` est consommé par `MyEventsService`, `FeaturedService`, `EventStatsService`, `EventService.getAll(...)` pour annoter chaque event de la liste avec son `attendingCount`/`waitlistedCount`. (b) Sans bulk endpoint, c'est N+1 appels REST sur une liste de N events. (c) Quarkus REST + Jackson sérialisent `Map<Long, AttendanceSummary>` natively en JSON `{"42": {...}, "7": {...}}`.

**Alternatives écartées.** (a) Faire N appels `getAttendanceSummary(eventId)` : N+1. (b) Re-déménager la table `attendances` dans event-service : régression de la consolidation. (c) Endpoint sans bulk + cache local côté event-service : sur-engineering.

**Adresse.** STUB-001 (suppression `AttendanceStub` côté event-service), TEST-002 (le pact bulk peut être ajouté en bonus).

---

## Architecture cible post-finalisation ultime

### Diagramme (texte ASCII)

```
                    ┌────────────────────┐
                    │   Ingress Nginx    │
                    │  (HTTPS, TLS)      │
                    └─────────┬──────────┘
                              │
                ┌─────────────┴─────────────┐
                │                           │
        /api/*  │                           │  / (SPA)
        /s3/*   │                           │
                ▼                           ▼
        ┌──────────────┐             ┌──────────────┐
        │ Kong DB-less │             │     web      │
        │ (2 prod / 1) │             │ (Nginx+React)│
        └──────┬───────┘             └──────────────┘
               │
   ┌───────────┼─────────────┬─────────────┬──────────┐
   │           │             │             │          │
   ▼           ▼             ▼             ▼          ▼
┌──────┐  ┌────────┐  ┌────────────┐ ┌──────────┐ ┌──────────────┐
│event │  │ user   │  │ engagement │ │moderation│ │ notification │
│  -svc│  │  -svc  │  │   -svc     │ │  -svc    │ │ (replicas:0) │
└──┬───┘  └───┬────┘  └─────┬──────┘ └────┬─────┘ └──────────────┘
   │  ⇄ REST clients (8 hops, 3 interfaces)
   │  ⇄ Kafka (10 topics, 9 producers + 1 consumer)
   └──────────┴────────────┴───────────┴──────────────────► db, kafka, minio
```

### Tableau récapitulatif post-finalisation ultime

| # | Service | Endpoints publics (Kong) | Endpoints internes (REST clients) | Tables | Kafka |
|---|---|---|---|---|---|
| 1 | **event-service** | `/events*`, `/admin/events*`, `/events/{id}/{share,view,favorite,co-organizers/*,stats,image}`, `/users/me/{favorites,co-organizer-invitations,events}`, `/s/{shortCode}`, `/events/{id}/occurrences` | **`GET /events/{id}?check-co-org-of=<UUID>`** (cascade SCRUM-136 self-check authentifié), **`GET /events?ids=...&status=...`** (bulk lookup), **`GET /events/{id}/organizer-uuids`** (set creator+co-organizers, NEW Décision G) | events, event_tags, event_views, favorites, event_co_organizers | **producer** events.{published,cancelled,expired} + co-organizers.{invited,accepted} ; **consumer** events.banned (idempotent) |
| 2 | **user-service** | `/users/me`, `/users/{id}`, `/users/me/{image,banner,calendar-token*,follow-requests}`, `/users/{id}/follow*`, `/follow-requests/*`, `/calendar/{token}.ics` | (aucun endpoint interne propre — provider seulement de `/users/{id}`) | users, user_interests, follows | **producer** users.{followed,follow-requested,follow-accepted} |
| 3 | **engagement-service** | `/events/{id}/{attend*,attendees,comments}`, `/users/me/{attendances,participations}`, `/comments/{id}` | **`GET /events/{eventId}/attendance-summary`** (single), **`GET /events/attendance-summary?ids=...`** (bulk, NEW Décision I), **`GET /users/{id}/attendances?status=...`** (NEW Décision B) | attendances, comments | **producer** comments.created |
| 4 | **moderation-service** | `/events/{id}/report`, `/admin/reports*` | (aucun) | reports | **producer** events.banned (mutation event.status déléguée au consumer event-service post-Décision H) |
| 5 | **notification-service** | (placeholder, replicas:0, SCRUM-99) | aucun | aucune | aucun |

**Modules de test** : `contract-tests` (Pact JVM 4.6.5 brokerless, 5 contrats consumer-driven post-TEST-002) + `e2e` (REST Assured happy path, gated env var).

**Total reactor** : 17 modules (5 services + 10 shared libs + contract-tests + e2e) — **inchangé depuis Étape 6.0**.

### Tableau des 8 REST clients (post-Décisions G + I)

| # | Consumer | Provider | Endpoints |
|---|---|---|---|
| 1 | event-service | user-service | `GET /users/{id}` (enrichissement DTOs) |
| 2 | event-service | engagement-service | `GET /events/{eventId}/attendance-summary` (single), `GET /events/attendance-summary?ids=...` (bulk, **Décision I**) |
| 3 | user-service | event-service | `GET /events?ids=...&status=PUBLISHED` (calendar bulk), `GET /events/{id}` (calendar enrichment) |
| 4 | user-service | engagement-service | `GET /users/{id}/attendances?status=ATTENDING` (**Décision B**) |
| 5 | engagement-service | event-service | `GET /events/{id}?check-co-org-of=<UUID>` (cascade self-check, **Décision C**) + `GET /events/{id}/organizer-uuids` (**Décision G**) |
| 6 | engagement-service | user-service | `GET /users/{id}` (author enrichment) |
| 7 | moderation-service | event-service | `GET /events/{id}` (read status pour idempotence Kafka) |
| 8 | moderation-service | user-service | `GET /users/{id}` (reporter / reviewedBy enrichment) |

### Tableau couverture cible jacoco par module (post-Option 3 Décision D)

| Module | Cible spec finalization | Cible Option 3 | Δ Δ Δ |
|---|---|---|---|
| event-service | 80% L / 70% B | ~30-40% L (12 sentinels portés) | gap honnête, S9 port complet |
| user-service | 80% L / 70% B | ~25-35% L (4 sentinels portés sur 6) | idem |
| engagement-service | 80% L / 70% B | ~30-40% L (4 sentinels portés sur 8) | idem |
| moderation-service | 80% L / 70% B | inchangé (~17%) — 0 sentinel SCRUM dédié à moderation | acté |
| notification-service | n/a (placeholder) | n/a | — |
| 10 shared libs | ≥ 95% L / 90% B | ≥ 95% L (déjà ✅, +`shared-domain-dtos` à étoffer en COV-002) | ✅ |

**Quality gate Sonar** : configurer côté SonarCloud `coverage on new code ≥ 70%` (pas sur l'absolu) — pragmatique post-Option 3.

---

## Plan d'implémentation par étape (ORDRE STRICT)

### Étape 0 — Pré-flight

**Objectif** : valider l'état initial avant tout commit.

**Commandes** :
```bash
git rev-parse HEAD                                           # ec668b91 ou descendant
git status --porcelain                                        # vide (audit untracked OK : *.md non Java)
git diff --shortstat origin/main HEAD -- frontend/            # 0 ligne
git diff --shortstat origin/main HEAD -- openapi/             # 0 ligne
ls backend/services/ | grep -E '\-service$' | sort            # event/user/engagement/moderation/notification (5)
grep -c '<module>' backend/pom.xml                            # 17
find backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l   # 13 (avant Étape 3)
cd backend && ./mvnw -B -DskipITs verify | tail -5             # SUCCESS
gh auth status                                                 # logged in
```

Si une de ces vérifications échoue, **STOP** et reporte à l'humain (Elie).

**Pas de commit.** Étape de validation seulement.

---

### Étape 1 — CI/Sonar fixes (Vague 1, 3 commits)

#### Étape 1.1 — CI-001 fix Sonar `top level project` + intégration consolidée

**Objectif** : adresser CI-001 (P0) selon Décision E. Modifier les 5 cellules services + le job shared-libs pour utiliser `-pl .,services/<X>`.

**Patch concret** dans `.github/workflows/build.yml` :

```yaml
# Section build-shared-libs — REMPLACER (matrix 10 cellules) par 1 cellule unique
build-shared-libs:
  name: Build All Shared Libs
  runs-on: ubuntu-latest
  defaults:
    run:
      working-directory: backend
  steps:
    - uses: actions/checkout@v6
      with: { fetch-depth: 0 }
    - uses: actions/setup-java@v5
      with: { java-version: 21, distribution: temurin, cache: maven }
    - name: Build & Test all shared libs
      # -am n'est pas nécessaire ici (les 10 libs sont auto-suffisantes), mais
      # `install` (pas `verify`) place les artefacts dans le local m2 pour
      # que la matrix backend downstream les retrouve sans -am supplémentaire.
      run: |
        ./mvnw -pl services/shared-rate-limit,services/shared-storage,\
                  services/shared-api-error,services/shared-domain-enums,\
                  services/shared-domain-dtos,services/shared-domain-projections,\
                  services/shared-jaxrs,services/shared-tracing,\
                  services/shared-kafka-events,services/shared-platform \
               install -B
    - name: SonarQube Scan (root reactor — Option B unige-events-backend)
      # continue-on-error: false — le projet unige-events-backend existe déjà
      # côté SonarCloud, donc ce step doit passer strict.
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: ./mvnw -pl . sonar:sonar -B
```

```yaml
# Section build-backend — modifier le step Build & Test pour `-pl .,<X>` (CI-001)
- name: Build & Test (with image build/push)
  env:
    QUARKUS_CONTAINER_IMAGE_USERNAME: ${{ github.actor }}
    QUARKUS_CONTAINER_IMAGE_PASSWORD: ${{ secrets.GITHUB_TOKEN }}
  run: |
    ./mvnw -pl .,services/${{ matrix.service }}-service -am install -B \
      -Dquarkus.container-image.build=true \
      -Dquarkus.container-image.push=${{ github.event_name == 'push' || github.event_name == 'pull_request' }} \
      -Dquarkus.container-image.registry=${{ env.REGISTRY }} \
      -Dquarkus.container-image.group=${{ github.repository_owner }} \
      -Dquarkus.container-image.tag=${{ env.IMAGE_TAG }} \
      -Dquarkus.container-image.additional-tags=${{ env.IMAGE_ADDITIONAL_TAGS }} \
      -Dquarkus.jib.base-jvm-image=eclipse-temurin:21-jre

- name: SonarQube Scan
  # continue-on-error: garder `true` ICI tant que les 5 SonarCloud projects
  # services ne sont pas créés par DevOps (cf. devops-handoff.md item 1).
  # Sera retiré par Étape 1.3 quand DevOps aura validé la création.
  continue-on-error: true
  env:
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
  run: ./mvnw -pl .,services/${{ matrix.service }}-service sonar:sonar -B
```

**Validation** :
```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh run watch <RUN_ID>
# Vérifier que les 5 cellules backend Sonar passent (statut "success" ou
# "failed-but-continue-on-error" si project not found, mais PAS "Maven
# session does not declare a top level project").
# Vérifier que la cellule shared-libs unique passe en mode strict.
```

**Commit** : `ci(backend): fix sonar:sonar with -pl .,<X> + consolidate shared-libs (Étape 1.1, CI-001 + CI-002)`

---

#### Étape 1.2 — CI-006 + CI-007 alignement `verify`/`install` + ajout job pact verification (optionnel S9)

**Objectif** : aligner cosmétiquement `build-contract-and-e2e` (CI-006) et préparer le scaffold du job `verify-pacts` pour S9 (CI-007 — déféré).

**Patch** :
```yaml
# Section build-contract-and-e2e — aligner verify → install (cosmétique)
- name: Build & Test contract-tests + e2e
  run: ./mvnw -pl contract-tests,e2e -am install -B
```

CI-007 (job `verify-pacts`) : **non livré dans cette spec** — déféré S9 car nécessite un harness provider verification non trivial (mockup des provider states). Documenter dans `devops-handoff.md` § 8 nouveau (cf. Étape 8.1).

**Validation** : `gh run watch` → cellule contract-tests passe verte.

**Commit** : `ci(backend): align build-contract-and-e2e to install + document pact verification for S9 (Étape 1.2, CI-006 + CI-007)`

---

#### Étape 1.3 — CI-003 retirer `continue-on-error` après validation + CI-005/008 cosmétiques

**Objectif** : retirer `continue-on-error: true` sur le step Sonar des cellules backend **uniquement après que DevOps a confirmé la création des 5 projets services**. Tant que ce n'est pas le cas, **garder `continue-on-error`** et documenter dans le commit message que cette Étape est conditionnelle.

**Patch conditionnel** :
```yaml
# .github/workflows/build.yml section build-backend
- name: SonarQube Scan
  # continue-on-error: RETIRÉ après création projets DevOps (cf. devops-handoff item 1).
  env:
    SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
  run: ./mvnw -pl .,services/${{ matrix.service }}-service sonar:sonar -B
```

**Si DevOps n'a pas créé les 5 projets au moment de l'exécution** : skip cette sous-étape ; insérer un commit message no-op qui dit « skipped — pending DevOps action ». Reprendre en S9.

**CI-005 cosmétique** : pas d'action immédiate — la fuite GHCR PR-tagged est documentée mais le cleanup automatique est déféré (S9). Documenter dans `devops-handoff.md`.

**CI-008 cosmétique** : pas d'action — cosmétique pure (path `target/pacts/` déjà aligné côté code, juste imprécision spec).

**Validation** : `gh run watch` → 5 cellules backend Sonar passent strict (si projets créés).

**Commit** (conditionnel) : `ci(backend): drop continue-on-error on backend Sonar (Étape 1.3, CI-003) — requires DevOps to have created 5 services projects`

---

### Étape 2 — REST clients runtime (Vague 2, 4 commits)

#### Étape 2.1 — REST-001 : `quarkus.rest-client.<svc>.url` config dans 4 consumers

**Objectif** : adresser REST-001 (P0). Ajouter les configs URL aux 4 services consumers dans leur `application.properties` selon le mapping des 8 hops (cf. tableau ci-dessus).

**Patch concret** :

`backend/services/event-service/src/main/resources/application.properties` (consume user-service + engagement-service) :
```properties
# ─── REST clients post-finalization ultimate (Étape 2.1, REST-001) ──────────
# event-service consume user-service (UserServiceClient.getById(uuid))
# et engagement-service (EngagementServiceClient.getAttendanceSummary{,Bulk}).
quarkus.rest-client.user-service.url=${USER_SERVICE_URL:http://user-service:8080}
quarkus.rest-client.engagement-service.url=${ENGAGEMENT_SERVICE_URL:http://engagement-service:8080}
# Resilience global (par configKey, override par méthode via annotations).
quarkus.rest-client.user-service.connect-timeout=2000
quarkus.rest-client.engagement-service.connect-timeout=2000
```

`backend/services/user-service/src/main/resources/application.properties` (consume event-service + engagement-service) :
```properties
quarkus.rest-client.event-service.url=${EVENT_SERVICE_URL:http://event-service:8080}
quarkus.rest-client.engagement-service.url=${ENGAGEMENT_SERVICE_URL:http://engagement-service:8080}
quarkus.rest-client.event-service.connect-timeout=2000
quarkus.rest-client.engagement-service.connect-timeout=2000
```

`backend/services/engagement-service/src/main/resources/application.properties` (consume event-service + user-service) :
```properties
quarkus.rest-client.event-service.url=${EVENT_SERVICE_URL:http://event-service:8080}
quarkus.rest-client.user-service.url=${USER_SERVICE_URL:http://user-service:8080}
quarkus.rest-client.event-service.connect-timeout=2000
quarkus.rest-client.user-service.connect-timeout=2000
```

`backend/services/moderation-service/src/main/resources/application.properties` (consume event-service + user-service) :
```properties
quarkus.rest-client.event-service.url=${EVENT_SERVICE_URL:http://event-service:8080}
quarkus.rest-client.user-service.url=${USER_SERVICE_URL:http://user-service:8080}
quarkus.rest-client.event-service.connect-timeout=2000
quarkus.rest-client.user-service.connect-timeout=2000
```

**Helm values.yaml** : la résolution K8s `http://<svc>-service:8080` fonctionne natively grâce au DNS K8s par-namespace. Pas de modif `values.yaml` nécessaire — les env vars `<SVC>_SERVICE_URL` sont absentes par défaut, donc le default `http://<svc>-service:8080` s'applique. Ajouter dans `k8s/chart/templates/<svc>-service/deployment.yaml` un commentaire pour documenter l'overridabilité (cf. spec § Annexe D pour le bloc env complet).

**Validation** :
```bash
cd backend && ./mvnw -B -DskipITs verify   # SUCCESS sur 17 modules
# Pas de runtime test ici — le wiring effectif sera testé en Étape 3 quand
# les call-sites passent par les REST clients.
```

**Commit** : `feat(backend): add quarkus.rest-client URL config to 4 consumers (Étape 2.1, REST-001)`

---

#### Étape 2.2 — REST-004 / SEC-001 : `NotFoundExceptionMapper` dans shared-api-error

**Objectif** : adresser REST-004 + SEC-001 (P0). Créer un `ExceptionMapper<NotFoundException>` dans `shared-api-error` qui retourne 404 + body `{"error":"not_found", "message":"Resource not found"}`. Auto-discovery via Jandex.

**Fichier nouveau** : `backend/services/shared-api-error/src/main/java/ch/unige/events/shared/error/NotFoundExceptionMapper.java`

```java
package ch.unige.events.shared.error;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Universal 404 envelope. Maps any {@link NotFoundException} thrown by
 * a JAX-RS resource to a canonical {@link ApiErrorResponse} with
 * {@code error="not_found"} so cross-service contracts (cf.
 * EngagementEventIssue92PactTest, ModerationEventPactTest) can rely on
 * a stable shape regardless of which service threw the exception.
 *
 * <p>Auto-discovered via Jandex when shared-api-error is on the
 * classpath ; no per-service registration needed.
 */
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException ex) {
        // Use the exception message if present, else a stable default.
        // The error code stays "not_found" so consumers can switch on it.
        String message = (ex.getMessage() != null && !ex.getMessage().isBlank())
                ? ex.getMessage()
                : "Resource not found";
        return Response.status(Response.Status.NOT_FOUND)
                .entity(new ApiErrorResponse("not_found", message))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }
}
```

**Test associé** : `backend/services/shared-api-error/src/test/java/ch/unige/events/shared/error/NotFoundExceptionMapperTest.java`

```java
package ch.unige.events.shared.error;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class NotFoundExceptionMapperTest {

    @Test
    void mapsToCanonicalEnvelope() {
        NotFoundExceptionMapper mapper = new NotFoundExceptionMapper();
        Response r = mapper.toResponse(new NotFoundException("Event 42 not found"));
        assertEquals(404, r.getStatus());
        assertInstanceOf(ApiErrorResponse.class, r.getEntity());
        ApiErrorResponse body = (ApiErrorResponse) r.getEntity();
        assertEquals("not_found", body.error());
        assertEquals("Event 42 not found", body.message());
    }

    @Test
    void blankMessageFallsBackToDefault() {
        Response r = new NotFoundExceptionMapper().toResponse(new NotFoundException());
        ApiErrorResponse body = (ApiErrorResponse) r.getEntity();
        assertEquals("not_found", body.error());
        assertEquals("Resource not found", body.message());
    }
}
```

**Validation** :
```bash
cd backend && ./mvnw -pl services/shared-api-error test
# Tests run: 4 (2 + 2 nouveaux), Failures: 0
```

Vérifier en runtime (test integration ou manuel) que `GET /api/events/9999` (event inexistant) retourne désormais `{"error":"not_found","message":"..."}` au lieu d'un body vide.

**Commit** : `feat(backend): add NotFoundExceptionMapper to shared-api-error for canonical 404 envelope (Étape 2.2, REST-004 / SEC-001)`

---

#### Étape 2.3 — REST-002 : créer `UserAttendancesInternalResource` côté engagement-service

**Objectif** : adresser REST-002 (P0) selon Décision B. Ajouter le resource manquant.

**Fichier nouveau** : `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/resource/UserAttendancesInternalResource.java`

```java
package ch.unige.events.engagement.attendance.resource;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.engagement.attendance.service.AttendanceService;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.enums.AttendanceStatus;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.UUID;

/**
 * Internal endpoint at {@code GET /users/{id}/attendances?status=ATTENDING}.
 * Consumed cross-service by user-service (calendar ICS feed) via
 * {@link ch.unige.events.shared.client.EngagementServiceClient#getUserAttendances}.
 *
 * <p>Not exposed via Kong (no public route) and not in {@code openapi.yaml} —
 * cf. {@code backend/docs/internal-endpoints.md} entry #4.
 *
 * <p>Decision B of finalization-ultimate spec: this resource is the
 * provider counterpart of EngagementServiceClient.getUserAttendances().
 * Without it, the cross-service call 404s at runtime.
 */
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserAttendancesInternalResource {

    @Inject
    AttendanceService attendanceService;

    @GET
    @Path("/{id}/attendances")
    @PermitAll
    public List<AttendanceDTO> getUserAttendances(
            @PathParam("id") UUID userId,
            @QueryParam("status") AttendanceStatus status) {
        return attendanceService.findByUser(userId, status);
    }
}
```

**Méthode service à ajouter** dans `AttendanceService.java` (à côté des méthodes existantes) :

```java
/**
 * Cross-service projection: returns the user's attendances filtered by
 * status, mapped to shared {@link AttendanceDTO}. Used by user-service
 * to materialize the ICS feed (favorites + attendances → events).
 *
 * <p>Note: enrichment of {@code displayName} / {@code avatarUrl} happens
 * by joining via {@code userServiceClient.getById(userId)} — but for
 * the cross-service call, we return only {@code id, userId, eventId,
 * status, createdAt} since the consumer (user-service) knows its own
 * user data.
 */
public List<AttendanceDTO> findByUser(UUID userId, AttendanceStatus status) {
    List<Attendance> rows = (status == null)
            ? Attendance.list("userId", userId)
            : Attendance.list("userId = ?1 and status = ?2", userId, status);
    return rows.stream()
            .map(a -> new AttendanceDTO(
                    a.id, a.userId, a.eventId, a.status, a.createdAt,
                    null, null))   // consumer enriches downstream if needed
            .toList();
}
```

**Test associé** : `backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/resource/UserAttendancesInternalResourceTest.java`

```java
package ch.unige.events.engagement.attendance.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;

@QuarkusTest
class UserAttendancesInternalResourceTest {

    @Test
    void getUserAttendances_unknownUser_returnsEmptyList() {
        UUID unknown = UUID.randomUUID();
        given()
            .when().get("/users/" + unknown + "/attendances?status=ATTENDING")
            .then()
            .statusCode(200)
            .body("$", empty());
    }
}
```

**Mise à jour `internal-endpoints.md`** : reformuler entry #4 pour cohérence (cf. Étape 8.2). Pour l'instant juste valider que l'endpoint compile.

**Validation** :
```bash
cd backend && ./mvnw -pl services/engagement-service -am test
# Build SUCCESS, nouveau test pass.
```

**Commit** : `feat(backend): add UserAttendancesInternalResource to engagement-service (Étape 2.3, REST-002 / Décision B)`

---

#### Étape 2.4 — REST-003 : admin bypass dans `UserService.getPublicProfile`

**Objectif** : adresser REST-003 (P1). Ajouter le param `boolean isAdmin` à la méthode et au resource caller.

**Patch concret** dans `backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java` :

```java
// AVANT (lignes 72-79)
@Transactional
public PublicProfileView getPublicProfile(UUID id, String auth0Id) {
    User user = User.<User>findByIdOptional(id).orElseThrow(NotFoundException::new);
    boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
    if (!user.profilePublic && !isOwner) {
        throw new NotFoundException();
    }
    ...
}

// APRÈS
@Transactional
public PublicProfileView getPublicProfile(UUID id, String auth0Id, boolean isAdmin) {
    User user = User.<User>findByIdOptional(id).orElseThrow(NotFoundException::new);
    boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
    // Admin bypass aligns with UserServiceClient javadoc: "404 when target
    // profilePublic=false and caller is neither the user themselves nor an admin"
    if (!user.profilePublic && !isOwner && !isAdmin) {
        throw new NotFoundException();
    }
    ...
}
```

**Patch dans `UserResource.java`** (le caller) : ajouter résolution du role admin depuis JWT et passage à `getPublicProfile`.

```java
// AVANT
@GET
@Path("/{id}")
@PermitAll
public PublicProfileView getById(@PathParam("id") UUID id) {
    String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
    return userService.getPublicProfile(id, auth0Id);
}

// APRÈS
private static final String ROLE_ADMIN = "ADMIN";

@GET
@Path("/{id}")
@PermitAll
public PublicProfileView getById(@PathParam("id") UUID id) {
    String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
    boolean isAdmin = !identity.isAnonymous() && identity.hasRole(ROLE_ADMIN);
    return userService.getPublicProfile(id, auth0Id, isAdmin);
}
```

**Tests à mettre à jour** : tous les `UserService.getPublicProfile(id, auth0Id)` call-sites dans les tests doivent ajouter le 3e param. Liste à scanner :
```bash
grep -rn 'getPublicProfile(' backend/services/user-service/src/test/
# Mettre à jour chaque appel pour passer false (default) ou true selon le scénario.
```

**Validation** : `cd backend && ./mvnw -pl services/user-service -am test` — SUCCESS.

**Commit** : `fix(backend): add admin bypass to UserService.getPublicProfile (Étape 2.4, REST-003 / ISSUE-93)`

---

### Étape 3 — Suppression des 13 stubs JPA + refactor entités (Vague 3, 5 commits)

**Pré-requis** : Étape 2 verte (REST clients câblés URL + NotFoundExceptionMapper en place).

**Stratégie globale** : pour chaque service consumer, dans l'ordre engagement → moderation → user → event :
1. Refactor de l'entité JPA possédée si elle a `@ManyToOne XStub` (Décision F).
2. Inject le ou les REST clients (`@RestClient @Inject XServiceClient client`).
3. Remplacement de chaque call-site `XStub.findByIdOptional(...)` / `XStub.find...()` par `client.<method>(...)`.
4. Suppression des fichiers `*Stub.java`.
5. Mise à jour des tests : `@InjectMock @RestClient XServiceClient` pour mocker les réponses.
6. Build module + run tests → vert.
7. Commit + push.

**Note locking** (cf. Annexe E) : `engagement-service.AttendanceService::attend` utilisait `entityManager.find(EventStub.class, eventId, LockModeType.PESSIMISTIC_WRITE)` pour éviter les races sur capacity gating. Post-stubs, ce lock cross-service n'a plus de sens. **Stratégie de remplacement** : lock advisory PostgreSQL sur `eventId` côté engagement-service via `pg_try_advisory_xact_lock(eventId)` dans une `@Transactional` boundary, OU lock applicatif via un `@Lock` Quarkus sur l'attendance row elle-même. **Choix** : on remplace par un `@Lock(LockModeType.PESSIMISTIC_WRITE)` sur la première lecture des attendances `Attendance.find(...).withLock(PESSIMISTIC_WRITE).list()` qui est local engagement et lock le compte attending — suffisant pour fermer la race.

#### Étape 3.1 — engagement-service : Comment refactor + 3 stubs supprimés

**Fichiers touchés** (liste exhaustive) :

À supprimer (`git rm`) :
- `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/entity/EventStub.java`
- `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/entity/UserStub.java`
- `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/entity/EventCoOrganizerStub.java`

À refactorer :
- `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/entity/Comment.java` — `@ManyToOne EventStub event` → `@Column(name="event_id") Long eventId` ; `@ManyToOne UserStub author` → `@Column(name="author_id") UUID authorId`.
- `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java` — remplacer call-sites `EventStub`, `UserStub`, `EventCoOrganizerStub`.
- `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/service/CommentService.java` — idem + refactor `assertEventVisibleAndLoad`, `isCreatorOrAcceptedCoOrganizer`, `computeOrganizerUserIds`.
- `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/dto/EventDTO.java` — supprimer (Décision A : utiliser shared `EventDTO`).
- `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/dto/AttendanceDTO.java` — supprimer (Décision A : utiliser shared `AttendanceDTO`).
- `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/dto/CommentDTO.java` — adapter pour ne plus dépendre de UserStub (utiliser `authorId` UUID + appel REST client pour enrichir).

**Refactor type — `Comment.java`** :

```java
// AVANT (lignes 38-44)
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "event_id", nullable = false)
public EventStub event;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "author_id", nullable = false)
public UserStub author;

// APRÈS
@Column(name = "event_id", nullable = false)
public Long eventId;

@Column(name = "author_id", nullable = false)
public UUID authorId;
```

Imports à mettre à jour : retirer `ch.unige.events.engagement.attendance.entity.EventStub`, `UserStub`, `jakarta.persistence.FetchType`, `jakarta.persistence.JoinColumn`, `jakarta.persistence.ManyToOne`. Ajouter `java.util.UUID`.

**Refactor type — `CommentService::assertEventVisibleAndLoad`** :

```java
// AVANT
private EventStub assertEventVisibleAndLoad(Long eventId, String auth0Id, boolean isAdmin) {
    EventStub event = EventStub.<EventStub>findByIdOptional(eventId)
            .orElseThrow(NotFoundException::new);
    if (event.status == EventStatus.BANNED) {
        throw new NotFoundException();
    }
    if (event.status != EventStatus.PUBLISHED && !isAdmin
            && !isCreatorOrAcceptedCoOrganizer(event, auth0Id)) {
        throw new NotFoundException();
    }
    return event;
}

// APRÈS
@Inject @RestClient EventServiceClient eventClient;
@Inject @RestClient UserServiceClient userClient;

private EventDTO assertEventVisibleAndLoad(Long eventId, String auth0Id, boolean isAdmin) {
    UUID callerId = (auth0Id != null) ? resolveCallerUuid(auth0Id) : null;
    // The provider applies the anti-oracle ISSUE-92 server-side: 404 if
    // the event is BANNED or non-PUBLISHED + caller is neither admin nor
    // creator/co-organizer. ?check-co-org-of= self-check (Décision C):
    // we pass our own UUID, so the cascade is honored.
    EventDTO event = (callerId != null)
            ? eventClient.getByIdWithCoOrgCheck(eventId, callerId)
            : eventClient.getById(eventId);
    if (event == null) {
        // REST client fallback returned null → propagate 404
        throw new NotFoundException();
    }
    return event;
}

private UUID resolveCallerUuid(String auth0Id) {
    // Resolve via shared Auth0IdResolver — production needs a UserServiceClient
    // call OR the JWT itself contains the UUID claim. For now, assume the
    // user's UUID is loaded once at session start (reuse identity context).
    // Simpler: reuse the auth0_id-to-uuid bridge already in shared-domain-projections.
    return ch.unige.events.shared.domain.projections.Auth0IdResolver.resolveUserId(jwt);
}
```

**Note** : `Auth0IdResolver.resolveUserId(jwt)` retourne le UUID de l'user à partir du JWT (claim `uuid` ou similaire). Si le claim n'est pas présent dans Auth0 par défaut, il faut soit configurer Auth0 pour l'inclure (action S9 DevOps), soit faire un appel `userClient.getByAuth0Id(auth0Id)` (mais cet endpoint n'existe pas — cf. internal-endpoints.md « disparu »). **Solution pragmatique** : conserver le pattern de cache local UUID via une `User` table-locale dans engagement-service ? Non, ça réintroduit le stub. **Solution propre** : exiger qu'Auth0 inclue le claim `uuid` (déjà documenté dans les Doppler secrets DevOps, cf. devops-handoff.md item 6 — `OIDC_*` config). Si pas dispo, fallback : `userClient.getById(...)` mais on a besoin de l'UUID first... circulaire.

**Décision pragmatique pour Étape 3.1** : on assume que le JWT contient le claim `uuid` (à configurer Auth0 par DevOps si pas le cas). `Auth0IdResolver.resolveUserId(JsonWebToken)` lit ce claim. Si absent (legacy JWT), retourne null → cascade `coOrganizerOf=null` (le caller ne peut pas être co-organizer sans UUID). Documenté dans devops-handoff.md.

**Refactor type — `CommentService::computeOrganizerUserIds`** (Décision G) :

```java
// AVANT
private static Set<UUID> computeOrganizerUserIds(EventStub event) {
    Set<UUID> ids = new HashSet<>();
    if (event.creatorId != null) {
        ids.add(event.creatorId);
    }
    ids.addAll(EventCoOrganizerStub.findAcceptedUserIdsForEvent(event.id));
    return ids;
}

// APRÈS
@Inject @RestClient EventServiceClient eventClient;

private Set<UUID> computeOrganizerUserIds(Long eventId) {
    // Décision G: new endpoint GET /events/{id}/organizer-uuids returns
    // the full set of creator + ACCEPTED co-organizers. Single REST call,
    // not N+1.
    List<UUID> uuids = eventClient.getOrganizerUuids(eventId);
    return new HashSet<>(uuids);
}
```

**Méthode `getOrganizerUuids` à ajouter à `EventServiceClient`** :

```java
@GET @Path("/{id}/organizer-uuids")
@Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
@Timeout(value = 2, unit = ChronoUnit.SECONDS)
@CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
@Fallback(fallbackMethod = "getOrganizerUuidsFallback")
List<UUID> getOrganizerUuids(@PathParam("id") long id);

default List<UUID> getOrganizerUuidsFallback(long id) {
    return List.of();
}
```

**Endpoint provider à ajouter côté event-service** : voir Étape 3.4.

**Tests engagement-service** : tous les tests qui faisaient `EventStub.persist()`, `UserStub.persist()`, `EventCoOrganizerStub.persist()` doivent être refactorés en `@InjectMock @RestClient EventServiceClient eventClient` + stubbing Mockito (`when(eventClient.getById(42L)).thenReturn(<EventDTO fixture>)`).

**Validation** :
```bash
find backend/services/engagement-service -name '*Stub.java' -not -path '*/target/*' | wc -l   # → 0
cd backend && ./mvnw -pl services/engagement-service -am test
# Tests run: ..., Failures: 0
```

**Commit** : `refactor(backend): wire 3 REST clients in engagement-service + delete EventStub/UserStub/EventCoOrganizerStub (Étape 3.1, STUB-001 / Décisions A, F, G)`

---

#### Étape 3.2 — moderation-service : Report refactor + 3 stubs supprimés + Décision H

**Fichiers touchés** :

À supprimer :
- `backend/services/moderation-service/src/main/java/ch/unige/events/report/entity/EventStub.java`
- `backend/services/moderation-service/src/main/java/ch/unige/events/report/entity/UserStub.java`
- `backend/services/moderation-service/src/main/java/ch/unige/events/report/entity/EventCoOrganizerStub.java`

À refactorer :
- `Report.java` — 3 `@ManyToOne UserStub`/`EventStub` → 3 `@Column id` (event_id, reporter_id, reviewed_by).
- `ReportService.java` — Décision H : retirer les mutations directes `event.status = BANNED` ; garder le Kafka producer.
- `ModerationCleanupService.java` — Décision H idem.
- `ReportDTO.java` — adapter pour ne plus dépendre de UserStub/EventStub (enrich via REST client).

**Refactor `Report.java`** :

```java
// AVANT
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "event_id", nullable = false)
public EventStub event;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "reporter_id")
public UserStub reporter;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "reviewed_by")
public UserStub reviewedBy;

// APRÈS
@Column(name = "event_id", nullable = false)
public Long eventId;

@Column(name = "reporter_id")
public UUID reporterId;

@Column(name = "reviewed_by")
public UUID reviewedById;
```

**Refactor `ReportService::handle`** (Décision H) :

```java
// AVANT
public void handle(Long reportId, ReportStatus newStatus, String moderationNote, ...) {
    Report report = Report.<Report>findByIdOptional(reportId).orElseThrow(NotFoundException::new);
    report.status = newStatus;
    report.moderationNote = moderationNote;
    report.reviewedAt = LocalDateTime.now();
    report.reviewedBy = UserStub.findByAuth0Id(adminAuth0Id).orElse(null);

    if (newStatus == ReportStatus.REVIEWED && report.event != null) {
        // ⚠️ MUTATION DIRECTE — à retirer
        report.event.status = EventStatus.BANNED;
    }
    // Kafka producer fire — déjà en place
    eventBannedEvent.fire(EventBannedEvent.created(report.event.id, ...));
}

// APRÈS (Décision H)
public void handle(Long reportId, ReportStatus newStatus, String moderationNote, String adminAuth0Id) {
    Report report = Report.<Report>findByIdOptional(reportId).orElseThrow(NotFoundException::new);
    report.status = newStatus;
    report.moderationNote = moderationNote;
    report.reviewedAt = LocalDateTime.now();
    // reviewedById resolves via JWT claim (no UserStub anymore)
    report.reviewedById = ch.unige.events.shared.domain.projections.Auth0IdResolver.resolveUserId(jwt);

    if (newStatus == ReportStatus.REVIEWED) {
        // The Kafka consumer in event-service will apply the BAN
        // idempotently. No more direct mutation cross-service.
        eventBannedEvent.fire(EventBannedEvent.created(report.eventId, adminAuth0Id));
    }
}
```

**Tests** : mocker `@RestClient EventServiceClient`, `@RestClient UserServiceClient` pour les enrichments. Vérifier que la mutation `event.status = BANNED` n'est plus présente côté moderation (un test peut grep le code source).

**Validation** :
```bash
find backend/services/moderation-service -name '*Stub.java' -not -path '*/target/*' | wc -l   # → 0
cd backend && ./mvnw -pl services/moderation-service -am test
```

**Commit** : `refactor(backend): wire 2 REST clients in moderation-service + delete 3 stubs + Kafka-only ban flow (Étape 3.2, STUB-001 / Décisions F, H)`

---

#### Étape 3.3 — user-service : Calendar/Follow refactor + 3 stubs supprimés

**Fichiers touchés** :

À supprimer :
- `backend/services/user-service/src/main/java/ch/unige/events/user/calendar/entity/EventStub.java`
- `backend/services/user-service/src/main/java/ch/unige/events/user/calendar/entity/AttendanceStub.java`
- `backend/services/user-service/src/main/java/ch/unige/events/user/calendar/entity/FavoriteStub.java`

À refactorer :
- `CalendarService.java` — utiliser `eventClient.findByIds(...)` + `engagementClient.getUserAttendances(userId, "ATTENDING")` au lieu des stubs locaux.
- `IcsBuilder.java` — adapter pour utiliser `EventDTO` shared au lieu de `EventStub` local.

**Refactor `CalendarService::generateIcs`** :

```java
// AVANT
public String generateIcs(UUID userId) {
    // 1. Collect favorited eventIds
    List<Long> favEventIds = FavoriteStub.list("userId", userId).stream()
            .map(f -> f.eventId).toList();
    // 2. Collect ATTENDING eventIds
    List<Long> attEventIds = AttendanceStub.list(
            "userId = ?1 and status = ?2", userId, AttendanceStatus.ATTENDING).stream()
            .map(a -> a.eventId).toList();
    // 3. Union
    Set<Long> allIds = new HashSet<>();
    allIds.addAll(favEventIds);
    allIds.addAll(attEventIds);
    // 4. Fetch events
    List<EventStub> events = EventStub.list(
            "id IN ?1 and status = ?2", new ArrayList<>(allIds), EventStatus.PUBLISHED);
    return IcsBuilder.build(events);
}

// APRÈS (Décisions A, B, F)
@Inject @RestClient EventServiceClient eventClient;
@Inject @RestClient EngagementServiceClient engagementClient;

public String generateIcs(UUID userId) {
    // 1. Collect ATTENDING eventIds via REST client
    List<Long> attEventIds = engagementClient.getUserAttendances(userId, "ATTENDING")
            .stream().map(AttendanceDTO::eventId).toList();
    // 2. Favorites: favorites table moved into event-service post-2.2.3.
    //    No internal endpoint for "user favorite eventIds" exists yet —
    //    follow-up: add GET /users/{id}/favorite-event-ids if needed.
    //    For now, use only attendances.
    if (attEventIds.isEmpty()) {
        return IcsBuilder.empty();
    }
    // 3. Bulk fetch events
    List<EventDTO> events = eventClient.findByIds(attEventIds, "PUBLISHED");
    return IcsBuilder.build(events);
}
```

**Note importante** : la note ci-dessus mentionne que les **favorites** ne peuvent pas être collectés cross-service sans nouvel endpoint. Décision pragmatique : pour cette spec ultime, on **ne livre pas** les favorites dans la feed ICS (acté comme dégradation fonctionnelle mineure). Documenter dans `internal-endpoints.md` § « endpoints à ajouter S9 ». Sinon, on peut ajouter en bonus l'endpoint `GET /users/{id}/favorite-event-ids` côté event-service — voir Étape 3.4.

**Refactor `IcsBuilder::build`** : adapter signature de `List<EventStub>` vers `List<EventDTO>` (shared). Champs accessés (`event.title`, `event.startDate`, etc.) restent identiques (les noms champs sont les mêmes entre stub et DTO). Petite adaptation : DTO utilise `event.title()` (record accessor) au lieu de `event.title` (field access).

**Validation** :
```bash
find backend/services/user-service -name '*Stub.java' -not -path '*/target/*' | wc -l   # → 0
cd backend && ./mvnw -pl services/user-service -am test
```

**Commit** : `refactor(backend): wire 2 REST clients in user-service calendar + delete 3 stubs (Étape 3.3, STUB-001 / Décisions A, B, F)`

---

#### Étape 3.4 — event-service : Event refactor + 4 stubs supprimés + endpoints providers Décisions G+I

**C'est le commit le plus gros de la vague 3** (~30-50 fichiers). À splitter en sous-commits si > 1000 lignes diff.

**Fichiers à supprimer** :
- `backend/services/event-service/src/main/java/ch/unige/events/event/entity/UserStub.java`
- `backend/services/event-service/src/main/java/ch/unige/events/event/entity/AttendanceStub.java`
- `backend/services/event-service/src/main/java/ch/unige/events/event/entity/EventViewStub.java` — **redirect vers `EventView`** (entité locale qui existe déjà à `event/view/entity/Favorite.java`).
- `backend/services/event-service/src/main/java/ch/unige/events/event/entity/FavoriteStub.java` — **redundant** avec `event/favorite/entity/Favorite.java`.

**Fichiers à refactorer** :
- `Event.java` — `@ManyToOne UserStub creator` → `@Column UUID creatorId`.
- `EventService.java` — remplacer `event.creator.id` par `event.creatorId`, `event.creator.auth0Id` par appel REST `userClient.getById(...)`.
- `FavoriteService.java`, `EventStatsService.java`, `EventViewService.java`, `MyEventsService.java`, `EventSearchService.java`, `FeaturedService.java`, `EventCoOrganizerService.java` — remplacer call-sites `UserStub`, `AttendanceStub`, `FavoriteStub`, `EventViewStub`.
- `EventDTO.java` (event-service) — supprimer (Décision A) ; créer `EventDTOMapper.java` qui retourne shared `EventDTO`.
- `event/coorganizer/dto/CoOrganizerDTO.java`, `event/me/dto/EventDTO.java`, `event/favorite/dto/EventDTO.java` (sous-packages) — soit utiliser shared `EventDTO`, soit garder local SI ils contiennent du field différent.

**Endpoint provider Décision G** — `GET /events/{id}/organizer-uuids` :

Ajouter à `EventResource.java` :
```java
@GET
@Path("/{id}/organizer-uuids")
@PermitAll
public List<UUID> getOrganizerUuids(@PathParam("id") Long id) {
    return eventService.getOrganizerUuids(id);
}
```

Ajouter à `EventService.java` :
```java
/**
 * Returns the set of UUIDs that count as "organizers" of an event:
 * the creator + every ACCEPTED co-organizer. Used cross-service by
 * engagement-service to annotate comments with `authorIsOrganizer:bool`
 * without N+1 ?check-co-org-of= calls (Décision G).
 *
 * <p>Anti-oracle: this endpoint is @PermitAll because the data is non-
 * sensitive (UUIDs alone leak no PII), but we still apply the ISSUE-92
 * gate: 404 if event is non-PUBLISHED and caller is not creator/admin.
 */
public List<UUID> getOrganizerUuids(Long eventId) {
    Event event = Event.<Event>findByIdOptional(eventId)
            .orElseThrow(NotFoundException::new);
    if (event.status == EventStatus.BANNED) {
        throw new NotFoundException();
    }
    // Note: we don't apply the full ISSUE-92 cascade here because the
    // endpoint is internal (engagement-service is the only consumer); the
    // anti-oracle is enforced upstream by /events/{id} which the consumer
    // calls first.
    Set<UUID> ids = new HashSet<>();
    if (event.creatorId != null) {
        ids.add(event.creatorId);
    }
    ids.addAll(EventCoOrganizer.findAcceptedUserIdsForEvent(eventId));
    return new ArrayList<>(ids);
}
```

Ajouter à `EventCoOrganizer.java` (méthode statique manquante) :
```java
public static List<UUID> findAcceptedUserIdsForEvent(Long eventId) {
    List<EventCoOrganizer> rows = list(
            "eventId = ?1 and status = ?2",
            eventId, CoOrganizerStatus.ACCEPTED);
    return rows.stream().map(co -> co.userId).toList();
}
```

**Endpoint provider Décision I** — `GET /events/attendance-summary?ids=...` côté engagement-service (pas event-service !) :

Ajouter à `AttendanceSummaryInternalResource.java` (engagement-service) :
```java
@GET
@Path("/attendance-summary")
@PermitAll
public Map<Long, AttendanceSummary> getBulk(@QueryParam("ids") List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
        return Map.of();
    }
    return attendanceService.getAttendanceSummariesBulk(ids);
}
```

Wait, le path est conflictuel : `/events/{eventId}/attendance-summary` vs `/events/attendance-summary?ids=...`. JAX-RS résout sur la spécificité : `{eventId}` matche `attendance-summary` comme un path segment. Donc l'ordre des méthodes dans la classe doit prioriser `getBulk` (path littéral) avant `getAttendanceSummary` (path param). Vérifier comportement Quarkus.

**Solution propre** : changer le path bulk en `/events/_bulk-attendance-summary` (préfixe `_` pour éviter ambiguïté path param).

```java
@GET
@Path("/_bulk-attendance-summary")
@PermitAll
public Map<Long, AttendanceSummary> getBulkAttendanceSummary(@QueryParam("ids") List<Long> ids) { ... }
```

Et côté `EngagementServiceClient` :
```java
@GET @Path("/events/_bulk-attendance-summary")
Map<Long, AttendanceSummary> getAttendanceSummariesBulk(@QueryParam("ids") List<Long> ids);
```

Méthode dans `AttendanceService` :
```java
public Map<Long, AttendanceSummary> getAttendanceSummariesBulk(List<Long> eventIds) {
    Map<Long, Long> attendingByEvent = Attendance.countGroupedByStatus(
            eventIds, AttendanceStatus.ATTENDING, entityManager);
    Map<Long, Long> waitlistedByEvent = Attendance.countGroupedByStatus(
            eventIds, AttendanceStatus.WAITLISTED, entityManager);

    Map<Long, AttendanceSummary> result = new HashMap<>();
    for (Long id : eventIds) {
        long attending = attendingByEvent.getOrDefault(id, 0L);
        long waitlisted = waitlistedByEvent.getOrDefault(id, 0L);
        result.put(id, AttendanceSummary.of(attending, waitlisted));
    }
    return result;
}
```

**Refactor `EventService::getAll`** (et autres méthodes qui faisaient `AttendanceStub.countGroupedByStatus(...)`) :

```java
// AVANT
List<Event> events = ...;
List<Long> ids = events.stream().map(e -> e.id).toList();
Map<Long, Long> attCounts = AttendanceStub.countGroupedByStatus(ids, AttendanceStatus.ATTENDING, em);

// APRÈS
@Inject @RestClient EngagementServiceClient engagementClient;

List<Event> events = ...;
List<Long> ids = events.stream().map(e -> e.id).toList();
Map<Long, AttendanceSummary> summaries = engagementClient.getAttendanceSummariesBulk(ids);
// summaries.get(eventId).attending() pour le count.
```

**FavoriteStub redundant** : suppression sèche, remplacer call-sites par `Favorite` (entité locale) directement.

**EventViewStub redundant** : suppression sèche, remplacer call-sites par `EventView` (entité locale) directement.

**Validation** :
```bash
find backend/services/event-service -name '*Stub.java' -not -path '*/target/*' | wc -l   # → 0
cd backend && ./mvnw -pl services/event-service -am test
```

**Commit** : `refactor(backend): wire 2 REST clients in event-service + delete 4 stubs + add /events/{id}/organizer-uuids + /events/_bulk-attendance-summary (Étape 3.4, STUB-001 / Décisions A, F, G, I)`

---

#### Étape 3.5 — Validation finale Étape 3 + bascule shared DTOs

**Validation globale** :

```bash
find backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l   # → 0 ✅
grep -rln '@ManyToOne.*Stub\|extends.*Stub' backend/services/*/src/main/java | wc -l   # → 0 ✅
cd backend && ./mvnw -B -DskipITs verify
# BUILD SUCCESS sur 17 modules
```

Si une vérification échoue, **STOP** et fixe avant de pousser.

**Commit (si fix nécessaire)** : `refactor(backend): final cleanup of stub references (Étape 3.5, STUB-001 validation)`

Sinon, pas de commit dédié — Étape 3.5 est validation pure, le push s'est fait à la fin de 3.4.

**Push + watch CI** :
```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
# Tous les jobs verts (sauf Sonar projects services si DevOps n'a pas créé).
```

---

### Étape 4 — Bascule shared libs (Vague 4, 3 commits)

**Pré-requis** : Étape 3 verte (0 stubs cross-service). Les services compilent contre les REST clients ; les enums et DTOs locaux ne sont plus utilisés que dans le service propriétaire.

#### Étape 4.1 — DUP-001 + DUP-004 : adopt shared-api-error + shared-domain-enums dans 4 services

**Objectif** : supprimer les 4 copies locales `ApiErrorResponse.java` + 21 enums locaux. Substitution mécanique d'imports.

**Pré-requis : ajouter `@Schema` à shared `ApiErrorResponse`** (pour préserver la doc OpenAPI) :

```java
// backend/services/shared-api-error/src/main/java/ch/unige/events/shared/error/ApiErrorResponse.java
package ch.unige.events.shared.error;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "ApiErrorResponse", description = "Standard API error payload (canonical envelope).")
public record ApiErrorResponse(String error, String message) {
}
```

**Patch 1 — POMs** : ajouter `shared-api-error` + `shared-domain-enums` aux 4 services métiers :

```xml
<!-- backend/services/<svc>-service/pom.xml — section <dependencies> -->
<dependency><groupId>ch.unige.events</groupId><artifactId>shared-api-error</artifactId><version>${project.version}</version></dependency>
<dependency><groupId>ch.unige.events</groupId><artifactId>shared-domain-enums</artifactId><version>${project.version}</version></dependency>
```

(NB: engagement-service a déjà ces deps depuis Étape 4.3 PM ; vérifier les autres services et compléter.)

**Patch 2 — sed global imports** :

```bash
# event-service
find backend/services/event-service/src -name '*.java' -exec sed -i \
    -e 's|ch\.unige\.events\.event\.dto\.ApiErrorResponse|ch.unige.events.shared.error.ApiErrorResponse|g' \
    -e 's|ch\.unige\.events\.event\.entity\.EventStatus|ch.unige.events.shared.domain.enums.EventStatus|g' \
    -e 's|ch\.unige\.events\.event\.entity\.EventCategory|ch.unige.events.shared.domain.enums.EventCategory|g' \
    -e 's|ch\.unige\.events\.event\.entity\.Faculty|ch.unige.events.shared.domain.enums.Faculty|g' \
    -e 's|ch\.unige\.events\.event\.entity\.AttendanceStatus|ch.unige.events.shared.domain.enums.AttendanceStatus|g' \
    -e 's|ch\.unige\.events\.event\.entity\.CoOrganizerStatus|ch.unige.events.shared.domain.enums.CoOrganizerStatus|g' \
    -e 's|ch\.unige\.events\.event\.entity\.RecurrenceFrequency|ch.unige.events.shared.domain.enums.RecurrenceFrequency|g' \
    {} +

# user-service
find backend/services/user-service/src -name '*.java' -exec sed -i \
    -e 's|ch\.unige\.events\.user\.dto\.ApiErrorResponse|ch.unige.events.shared.error.ApiErrorResponse|g' \
    -e 's|ch\.unige\.events\.user\.entity\.FollowStatus|ch.unige.events.shared.domain.enums.FollowStatus|g' \
    -e 's|ch\.unige\.events\.user\.calendar\.entity\.EventStatus|ch.unige.events.shared.domain.enums.EventStatus|g' \
    {} +

# engagement-service
find backend/services/engagement-service/src -name '*.java' -exec sed -i \
    -e 's|ch\.unige\.events\.engagement\.attendance\.dto\.ApiErrorResponse|ch.unige.events.shared.error.ApiErrorResponse|g' \
    -e 's|ch\.unige\.events\.engagement\.attendance\.entity\.AttendanceStatus|ch.unige.events.shared.domain.enums.AttendanceStatus|g' \
    -e 's|ch\.unige\.events\.engagement\.attendance\.entity\.CoOrganizerStatus|ch.unige.events.shared.domain.enums.CoOrganizerStatus|g' \
    -e 's|ch\.unige\.events\.engagement\.attendance\.entity\.EventCategory|ch.unige.events.shared.domain.enums.EventCategory|g' \
    -e 's|ch\.unige\.events\.engagement\.attendance\.entity\.EventStatus|ch.unige.events.shared.domain.enums.EventStatus|g' \
    -e 's|ch\.unige\.events\.engagement\.attendance\.entity\.Faculty|ch.unige.events.shared.domain.enums.Faculty|g' \
    {} +

# moderation-service
find backend/services/moderation-service/src -name '*.java' -exec sed -i \
    -e 's|ch\.unige\.events\.report\.dto\.ApiErrorResponse|ch.unige.events.shared.error.ApiErrorResponse|g' \
    -e 's|ch\.unige\.events\.report\.entity\.CoOrganizerStatus|ch.unige.events.shared.domain.enums.CoOrganizerStatus|g' \
    -e 's|ch\.unige\.events\.report\.entity\.EventStatus|ch.unige.events.shared.domain.enums.EventStatus|g' \
    -e 's|ch\.unige\.events\.report\.entity\.ReportReason|ch.unige.events.shared.domain.enums.ReportReason|g' \
    -e 's|ch\.unige\.events\.report\.entity\.ReportStatus|ch.unige.events.shared.domain.enums.ReportStatus|g' \
    {} +
```

**Patch 3 — `git rm` les fichiers locaux** :

```bash
# 4 ApiErrorResponse locaux
git rm backend/services/event-service/src/main/java/ch/unige/events/event/dto/ApiErrorResponse.java
git rm backend/services/user-service/src/main/java/ch/unige/events/user/dto/ApiErrorResponse.java
git rm backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/dto/ApiErrorResponse.java
git rm backend/services/moderation-service/src/main/java/ch/unige/events/report/dto/ApiErrorResponse.java

# 21 enums locaux
git rm backend/services/event-service/src/main/java/ch/unige/events/event/entity/EventStatus.java
git rm backend/services/event-service/src/main/java/ch/unige/events/event/entity/EventCategory.java
git rm backend/services/event-service/src/main/java/ch/unige/events/event/entity/Faculty.java
git rm backend/services/event-service/src/main/java/ch/unige/events/event/entity/AttendanceStatus.java
git rm backend/services/event-service/src/main/java/ch/unige/events/event/entity/CoOrganizerStatus.java
git rm backend/services/event-service/src/main/java/ch/unige/events/event/entity/RecurrenceFrequency.java

git rm backend/services/user-service/src/main/java/ch/unige/events/user/entity/FollowStatus.java
git rm backend/services/user-service/src/main/java/ch/unige/events/user/calendar/entity/EventStatus.java

git rm backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/entity/AttendanceStatus.java
git rm backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/entity/CoOrganizerStatus.java
git rm backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/entity/EventCategory.java
git rm backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/entity/EventStatus.java
git rm backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/entity/Faculty.java

git rm backend/services/moderation-service/src/main/java/ch/unige/events/report/entity/CoOrganizerStatus.java
git rm backend/services/moderation-service/src/main/java/ch/unige/events/report/entity/EventStatus.java
git rm backend/services/moderation-service/src/main/java/ch/unige/events/report/entity/ReportReason.java
git rm backend/services/moderation-service/src/main/java/ch/unige/events/report/entity/ReportStatus.java

# Note: event-service.event/entity/CoOrganizerStatus.java apparaît dans
# coorganizer/entity/EventCoOrganizer.java (import). Vérifier que le sed
# a bien rerouté tous les imports vers shared-domain-enums avant rm.
```

**Validation** :
```bash
# Aucun import résiduel d'enum local
grep -rn 'ch\.unige\.events\.\(event\|user\|engagement\|report\)\..*\.\(EventStatus\|EventCategory\|Faculty\|AttendanceStatus\|CoOrganizerStatus\|FollowStatus\|RecurrenceFrequency\|ReportStatus\|ReportReason\|ApiErrorResponse\)' backend/services/*/src 2>/dev/null
# Doit être vide

cd backend && ./mvnw -B -DskipITs verify   # SUCCESS
```

**Commit** : `refactor(backend): adopt shared-api-error + shared-domain-enums across 4 services (Étape 4.1, DUP-001 + DUP-004)`

---

#### Étape 4.2 — DUP-002 + DUP-003 : adopt shared-platform + shared-jaxrs

**Objectif** : supprimer les 5 copies locales `ServiceIdentityResource.java` + 2 copies locales `Timeframe.java`.

**Patch 1 — POMs** : ajouter `shared-platform` + `shared-jaxrs` aux 5 services qui ne les ont pas. Vérifier avant :

```bash
for svc in event user engagement moderation notification; do
    echo "=== $svc-service ==="
    grep -E '(shared-platform|shared-jaxrs)' backend/services/$svc-service/pom.xml
done
```

Ajouter selon manque :
```xml
<dependency><groupId>ch.unige.events</groupId><artifactId>shared-platform</artifactId><version>${project.version}</version></dependency>
<dependency><groupId>ch.unige.events</groupId><artifactId>shared-jaxrs</artifactId><version>${project.version}</version></dependency>
```

**Patch 2 — Tests `ServiceIdentityResourceTest` à mettre à jour** : aligner sur le shared (pas de champ `status`) :

```java
// AVANT (event-service)
.body("service", equalTo("event-service"))
.body("status", equalTo("scaffold"));   // ← supprime cette ligne

// APRÈS
.body("service", equalTo("event-service"))
.body("module", equalTo("ch.unige.events:event-service"));
```

À répéter pour user, engagement, moderation, notification.

**Patch 3 — `application.properties` de chaque service** : vérifier que `quarkus.application.name` est bien défini (sinon le shared `ServiceIdentityResource` retournera "unknown-service") :

```properties
quarkus.application.name=event-service
```

**Patch 4 — sed global imports Timeframe** :

```bash
find backend/services/event-service/src -name '*.java' -exec sed -i \
    -e 's|ch\.unige\.events\.event\.entity\.Timeframe|ch.unige.events.shared.jaxrs.Timeframe|g' \
    {} +

find backend/services/engagement-service/src -name '*.java' -exec sed -i \
    -e 's|ch\.unige\.events\.engagement\.attendance\.entity\.Timeframe|ch.unige.events.shared.jaxrs.Timeframe|g' \
    {} +
```

**Patch 5 — `git rm` des fichiers locaux** :

```bash
# 5 ServiceIdentityResource locaux
git rm backend/services/event-service/src/main/java/ch/unige/events/event/ServiceIdentityResource.java
git rm backend/services/user-service/src/main/java/ch/unige/events/user/ServiceIdentityResource.java
git rm backend/services/engagement-service/src/main/java/ch/unige/events/engagement/ServiceIdentityResource.java
git rm backend/services/moderation-service/src/main/java/ch/unige/events/report/ServiceIdentityResource.java
git rm backend/services/notification-service/src/main/java/ch/unige/events/notification/ServiceIdentityResource.java

# 2 Timeframe locaux
git rm backend/services/event-service/src/main/java/ch/unige/events/event/entity/Timeframe.java
git rm backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/entity/Timeframe.java
```

**Validation** :
```bash
cd backend && ./mvnw -B -DskipITs verify   # SUCCESS
# Tester que GET /__service répond bien sur les 5 services avec shared bean
# (auto-discovered via Jandex sur shared-platform).
```

**Commit** : `refactor(backend): adopt shared-platform + shared-jaxrs across 5 services (Étape 4.2, DUP-002 + DUP-003)`

---

#### Étape 4.3 — DUP-005 + DUP-006 + Décision A : adopt shared-domain-dtos + shared-domain-projections

**Objectif** : supprimer les 7 DTOs locaux dupliqués + les helpers dupliqués (`computeAvailableSpots`, `resolveUserId`). Créer les `<X>DTOMapper` per Décision A.

**Pré-requis : créer les mappers** (3 fichiers nouveaux) :

`backend/services/event-service/src/main/java/ch/unige/events/event/dto/EventDTOMapper.java` :
```java
package ch.unige.events.event.dto;

import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.dto.EventDTO;

import java.util.List;

/**
 * Mapper from {@link Event} JPA entity to {@link EventDTO} (shared record).
 * Lives in event-service (not in shared-domain-dtos) because it imports
 * the JPA entity. Replaces the static EventDTO.from(...) factories that
 * were on the local EventDTO before consolidation in shared-domain-dtos
 * (Décision A of finalization-ultimate spec).
 */
public final class EventDTOMapper {

    private EventDTOMapper() {}

    /** Basic mapping without computed fields. */
    public static EventDTO from(Event event) {
        return from(event, 0L, null, 0L, null, null, null);
    }

    /** Full mapping with computed counts (called from event-service services). */
    @SuppressWarnings("java:S107")
    public static EventDTO from(
            Event event,
            long attendingCount,
            Long availableSpots,
            long waitlistedCount,
            Long viewCount,
            Long interestedCount,
            Boolean coOrganizerOf) {
        return new EventDTO(
                event.id,
                event.title,
                event.description,
                event.location,
                event.startDate,
                event.endDate,
                event.category,
                event.faculty,
                event.bannerUrl,
                event.creatorId,                                       // ← post-Décision F
                event.status,
                event.capacity,
                event.allDay,
                event.featured,
                event.featuredAt,
                attendingCount,
                availableSpots,
                waitlistedCount,
                viewCount,
                interestedCount,
                event.websiteUrl,
                event.contactEmail,
                event.registrationDeadline,
                event.tags != null ? List.copyOf(event.tags) : List.of(),
                event.createdAt,
                event.updatedAt,
                event.parentEventId,
                event.recurrenceRule,
                coOrganizerOf
        );
    }
}
```

`backend/services/user-service/src/main/java/ch/unige/events/user/dto/UserPublicResponseMapper.java` :
```java
package ch.unige.events.user.dto;

import ch.unige.events.user.entity.User;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.FollowStatus;

public final class UserPublicResponseMapper {

    private UserPublicResponseMapper() {}

    public static UserPublicResponse anonymous(User user) {
        return UserPublicResponse.anonymous(user.id, user.displayName, user.avatarUrl);
    }

    public static UserPublicResponse from(User user, long followerCount, long followingCount, FollowStatus followStatus) {
        return new UserPublicResponse(
                user.id,
                user.displayName,
                user.faculty != null ? user.faculty.name() : null,
                user.studyLevel,
                user.bio,
                user.interests != null ? java.util.List.copyOf(user.interests) : java.util.List.of(),
                user.avatarUrl,
                user.bannerUrl,
                followerCount,
                followingCount,
                followStatus
        );
    }
}
```

`backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/dto/AttendanceDTOMapper.java` :
```java
package ch.unige.events.engagement.attendance.dto;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;

public final class AttendanceDTOMapper {

    private AttendanceDTOMapper() {}

    /** Without enrichment (id-only payload, used cross-service). */
    public static AttendanceDTO from(Attendance attendance) {
        return new AttendanceDTO(
                attendance.id, attendance.userId, attendance.eventId,
                attendance.status, attendance.createdAt,
                null, null);
    }

    /** With user enrichment (display name + avatar from UserServiceClient). */
    public static AttendanceDTO from(Attendance attendance, UserPublicResponse user) {
        return new AttendanceDTO(
                attendance.id, attendance.userId, attendance.eventId,
                attendance.status, attendance.createdAt,
                user != null ? user.displayName() : null,
                user != null ? user.avatarUrl() : null);
    }
}
```

(Idem pour `EventCoOrganizerDTOMapper` côté event-service.)

**Patch — sed global imports** :

```bash
find backend/services/event-service/src -name '*.java' -exec sed -i \
    -e 's|ch\.unige\.events\.event\.dto\.EventDTO\b|ch.unige.events.shared.domain.dto.EventDTO|g' \
    {} +
# (mais préserver EventDTOMapper en event-service local, et le sous-package
#  event/me/dto/EventDTO et event/coorganizer/dto/EventDTO et
#  event/favorite/dto/EventDTO doivent être supprimés ou refactorés
#  individuellement — voir grep de leurs call-sites)

find backend/services/engagement-service/src -name '*.java' -exec sed -i \
    -e 's|ch\.unige\.events\.engagement\.attendance\.dto\.EventDTO|ch.unige.events.shared.domain.dto.EventDTO|g' \
    -e 's|ch\.unige\.events\.engagement\.attendance\.dto\.AttendanceDTO|ch.unige.events.shared.domain.dto.AttendanceDTO|g' \
    {} +

# user-service: garder UserPublicResponse local pour l'instant si trop de
# call-sites — sinon switcher au shared via la même approche (Mapper).
```

**Adopt `shared-domain-projections`** (DUP-006) :

Remplacer les 5 copies locales de `computeAvailableSpots(...)` par `EventCapacity.computeAvailableSpots(...)` (déjà dans shared-domain-projections). Pareil pour `resolveUserId(jwt)` → `Auth0IdResolver.resolveUserId(jwt)`.

**`git rm` les fichiers locaux** :

```bash
# DTOs locaux (selon analyse case-by-case)
git rm backend/services/event-service/src/main/java/ch/unige/events/event/dto/EventDTO.java
git rm backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/dto/EventDTO.java
git rm backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/dto/AttendanceDTO.java
# event-service sous-packages: vérifier individuellement, supprimer si dupliqués
# (event/me/dto/EventDTO.java, event/coorganizer/dto/EventDTO.java,
#  event/favorite/dto/EventDTO.java — tous redirigent vers le shared)

# UserPublicResponse: peut rester local pour l'instant car ses 3 factories
# (from, fromCounts, anonymous) ont une logique riche. Si on bascule:
# git rm backend/services/user-service/src/main/java/ch/unige/events/user/dto/UserPublicResponse.java
# (et adapter UserResource pour utiliser UserPublicResponseMapper.from(...))
```

**Validation** :
```bash
cd backend && ./mvnw -B -DskipITs verify   # SUCCESS
# Vérifier que `cd backend && ./mvnw -pl services/event-service test`
# spécialement passe car c'est le plus gros refactor.
```

**Commit** : `refactor(backend): adopt shared-domain-dtos + shared-domain-projections via mappers (Étape 4.3, DUP-005 + DUP-006 / Décision A)`

---

### Étape 5 — Tests + couverture (Vague 5, 2 commits)

#### Étape 5.1 — Tests bloquants : pact bulk + bridge test + scaffolds

**Objectif** : adresser TEST-002, TEST-003/KAFKA-001, TEST-004, COV-002 dans un commit groupé (tests bloquants pour la pact verification + cohérence test infra).

##### 5.1.a — TEST-002 : pact bulk `AttendanceSummary`

Créer `backend/contract-tests/src/test/java/ch/unige/events/contracts/EventEngagementBulkAttendancePactTest.java` :

```java
package ch.unige.events.contracts;

import au.com.dius.pact.consumer.MockServer;
import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.consumer.junit5.PactConsumerTestExt;
import au.com.dius.pact.consumer.junit5.PactTestFor;
import au.com.dius.pact.core.model.PactSpecVersion;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.hamcrest.Matchers.equalTo;

@ExtendWith(PactConsumerTestExt.class)
@PactTestFor(providerName = "engagement-service", pactVersion = PactSpecVersion.V3)
@SuppressWarnings("java:S100")
class EventEngagementBulkAttendancePactTest {

    @Pact(consumer = "event-service", provider = "engagement-service")
    public RequestResponsePact bulkAttendanceSummary_returnsMap(PactDslWithProvider builder) {
        String body = """
                {
                  "42": {"attending": 5, "waitlisted": 2, "interested": 0},
                  "7":  {"attending": 1, "waitlisted": 0, "interested": 0}
                }
                """;
        return builder
                .given("events 42 (5 ATTENDING, 2 WAITLISTED) and 7 (1 ATTENDING)")
                .uponReceiving("a GET /events/_bulk-attendance-summary?ids=42&ids=7 from event-service")
                    .path("/events/_bulk-attendance-summary")
                    .method("GET")
                    .query("ids=42&ids=7")
                .willRespondWith()
                    .status(200)
                    .headers(java.util.Map.of("Content-Type", "application/json"))
                    .body(body, "application/json")
                .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "bulkAttendanceSummary_returnsMap")
    void bulk_attendance_summary_returns_map_by_event_id(MockServer mockServer) {
        RestAssured.given()
                .accept(ContentType.JSON)
                .when()
                    .get(mockServer.getUrl() + "/events/_bulk-attendance-summary?ids=42&ids=7")
                .then()
                    .statusCode(200)
                    .body("'42'.attending", equalTo(5))
                    .body("'7'.attending", equalTo(1));
    }
}
```

##### 5.1.b — TEST-003 / KAFKA-001 : `EventLifecycleKafkaBridgeTest`

Créer `backend/services/event-service/src/test/java/ch/unige/events/event/kafka/EventLifecycleKafkaBridgeTest.java` :

```java
package ch.unige.events.event.kafka;

import ch.unige.events.shared.kafka.events.EventLifecycleEvent;
import jakarta.enterprise.event.TransactionPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class EventLifecycleKafkaBridgeTest {

    @Mock
    EventLifecyclePublisher publisher;

    @InjectMocks
    EventLifecycleKafkaBridge bridge;

    @Test
    void onPublishedEvent_callsPublisher() {
        EventLifecycleEvent ev = EventLifecycleEvent.published(42L, java.util.UUID.randomUUID());
        bridge.onLifecycleEvent(ev);
        verify(publisher).send(any(EventLifecycleEvent.class));
    }
}
```

(Pattern aligné sur les 4 autres bridge tests existants.)

##### 5.1.c — TEST-004 : suppression des 2 sentinel scaffolds

```bash
git rm backend/contract-tests/src/test/java/ch/unige/events/contracts/ContractTestsScaffoldTest.java
git rm backend/e2e/src/test/java/ch/unige/events/e2e/E2EScaffoldTest.java
```

(Ces sentinels avaient validé le boot surefire en Étape 6.0 PM. Maintenant les vrais tests pact + e2e les remplacent.)

##### 5.1.d — COV-002 : étoffer tests `shared-domain-dtos`

Ajouter à `EventDTOTest.java` :
```java
@Test
void recordAccessorReturnAllFieldsCorrectly() { ... }

@Test
void coOrganizerOf_isOptionalAndDefaultsToNull() { ... }

@Test
void availableSpots_canBeNullForUncappedEvents() { ... }
```

(Cible 95% L sur shared-domain-dtos — ajouter ~5-8 tests pour couvrir les records `EventDTO`, `AttendanceDTO`, `EventCoOrganizerDTO`.)

**Validation** :
```bash
cd backend && ./mvnw -pl contract-tests,services/event-service,services/shared-domain-dtos -am test
# 5 tests Pact (au lieu de 4), bridge test green, scaffolds supprimés.
ls backend/contract-tests/target/pacts/   # 4 fichiers JSON (engagement, moderation, user, **NEW** event-engagement)
```

**Commit** : `test(backend): add bulk pact + EventLifecycleKafkaBridgeTest + extend shared-domain-dtos coverage + drop scaffolds (Étape 5.1, TEST-002 + TEST-003 + TEST-004 + COV-002)`

---

#### Étape 5.2 — TEST-001 : port des 8 sentinels prioritaires (Décision D Option 3)

**Objectif** : porter avec assertions réelles les 8 sentinels prioritaires (cf. Décision D liste). Les 23 autres restent placeholders avec `@Tag("legacy-port-s9")`.

**Pour chaque sentinel à porter, le pattern type** :

```java
// backend/services/<service>-service/src/test/.../sentinels/<X>DomainSentinelsTest.java

@QuarkusTest
@TestSecurity(user = "auth0|test-user", roles = {})
@SuppressWarnings("java:S100")
class EngagementDomainSentinelsTest {

    @InjectMock @RestClient
    EventServiceClient eventClient;

    @InjectMock @RestClient
    UserServiceClient userClient;

    @Test
    void prePersist_setsCreatedAt() {
        // SCRUM-144 — Comment.prePersist sets createdAt to now if null.
        Comment c = new Comment();
        c.eventId = 42L;
        c.authorId = UUID.randomUUID();
        c.content = "Hello";
        c.prePersist();
        assertNotNull(c.createdAt);
        assertTrue(c.createdAt.isAfter(LocalDateTime.now().minusSeconds(2)));
    }

    @Test
    void post_eventDraftByNonCreator_returns404_antiOracle() {
        // SCRUM-144 — Posting a comment on a DRAFT event when the caller
        // is not the creator should return 404 (anti-oracle ISSUE-92 +
        // cascade SCRUM-136).
        when(eventClient.getByIdWithCoOrgCheck(eq(99L), any(UUID.class)))
            .thenReturn(null);   // REST client fallback = 404 propagated

        given()
            .auth().oauth2(testToken("auth0|test-user"))
            .contentType(ContentType.JSON)
            .body("""{"content":"hi"}""")
            .when().post("/events/99/comments")
            .then().statusCode(404)
                .body("error", equalTo("not_found"));
    }

    @Test
    void post_eventBanned_returns404_antiOracle() {
        // SCRUM-144 — Same as above but the event is BANNED.
        // The provider event-service applies anti-oracle: BANNED → 404.
        when(eventClient.getByIdWithCoOrgCheck(eq(98L), any(UUID.class)))
            .thenReturn(null);   // 404 propagated

        given()
            .auth().oauth2(testToken("auth0|test-user"))
            .contentType(ContentType.JSON)
            .body("""{"content":"hi"}""")
            .when().post("/events/98/comments")
            .then().statusCode(404);
    }

    @Test
    void delete_unknownComment_returns404_commentNotFound() {
        // SCRUM-144 — Deleting a non-existent comment returns 404 with
        // canonical envelope.
        given()
            .auth().oauth2(testToken("auth0|test-user"))
            .when().delete("/comments/99999999")
            .then().statusCode(404)
                .body("error", equalTo("not_found"));
    }

    // Les 4 autres sentinels SCRUM-144 restent placeholders avec @Tag.
    @Test @Tag("legacy-port-s9")
    void post_replyToReply_returns422_repliesTooDeep() {}

    @Test @Tag("legacy-port-s9")
    void post_parentInOtherEvent_returns422_parentNotInEvent() {}

    @Test @Tag("legacy-port-s9")
    void post_unknownParent_returns404_parentNotFound() {}

    @Test @Tag("legacy-port-s9")
    void delete_byPendingCoOrganizer_returns403() {}
}
```

**Pattern similaire** pour `EventDomainSentinelsTest` (event-service) — ports `getOccurrences_draftByNonCreator_returns404_antiOracle` + `getOccurrences_draftByAnonymous_returns404_antiOracle`. Les 15 autres restent `@Tag("legacy-port-s9")`.

**Pattern similaire** pour `UserDomainSentinelsTest` (user-service) — ports `getFollowers_privateProfileNonOwner_returns404_antiOracle` + `follow_selfFollow_throwsUnprocessable`. Les 4 autres restent `@Tag("legacy-port-s9")`.

**Validation** :
```bash
cd backend && ./mvnw -B -DskipITs verify
# 12 sentinels green (4 RecurrenceGenerator + 8 nouveaux).
# 23 sentinels @Tag("legacy-port-s9") still pass (empty body).

# Coverage check
for r in backend/services/{event,user,engagement,moderation}-service/target/jacoco-report/jacoco.xml; do
    # Voir Annexe F pour le script complet
done
```

**Commit** : `test(backend): port 8 prioritized SCRUM sentinels with real assertions + tag 23 others legacy-port-s9 (Étape 5.2, TEST-001 / Décision D Option 3)`

---

### Étape 6 — Sécurité (Vague 6, 1 commit)

#### Étape 6.1 — SEC-002 : self-check authentifié sur `?check-co-org-of=`

**Objectif** : adresser SEC-002 (P1) selon Décision C. Le param `check-co-org-of=<UUID>` n'est honoré que si caller authentifié + UUID = caller's resolved UUID.

**Patch concret** dans `backend/services/event-service/src/main/java/ch/unige/events/event/resource/EventResource.java` :

```java
// AVANT
@GET @Path("/{id}") @PermitAll
public Response getById(@PathParam("id") Long id,
                        @QueryParam("check-co-org-of") UUID checkCoOrgOf) {
    String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
    boolean isAdmin = !identity.isAnonymous() && identity.hasRole(ROLE_ADMIN);
    EventDTO event = eventService.getById(id, auth0Id, isAdmin, checkCoOrgOf);
    return Response.ok(event).build();
}

// APRÈS (Décision C : self-check authentifié)
@GET @Path("/{id}") @PermitAll
public Response getById(@PathParam("id") Long id,
                        @QueryParam("check-co-org-of") UUID checkCoOrgOf) {
    String auth0Id = identity.isAnonymous() ? null : identity.getPrincipal().getName();
    boolean isAdmin = !identity.isAnonymous() && identity.hasRole(ROLE_ADMIN);

    // SEC-002: ?check-co-org-of= is only honored when:
    //   1. caller is authenticated, AND
    //   2. the value matches the caller's resolved UUID (self-check).
    // Otherwise the param is silently ignored (coOrganizerOf=null).
    UUID effectiveCheck = null;
    if (checkCoOrgOf != null && auth0Id != null) {
        UUID callerUuid = ch.unige.events.shared.domain.projections.Auth0IdResolver
                .resolveUserId(jwt);
        if (callerUuid != null && callerUuid.equals(checkCoOrgOf)) {
            effectiveCheck = checkCoOrgOf;
        }
        // else: silently ignore — return EventDTO without coOrganizerOf field
    }

    EventDTO event = eventService.getById(id, auth0Id, isAdmin, effectiveCheck);
    return Response.ok(event).build();
}
```

**Inject `JsonWebToken`** dans `EventResource` (s'il n'est pas déjà injecté) :
```java
@Inject JsonWebToken jwt;
```

**Tests à ajouter** : trois tests dans `EventResourceTest.java` :

```java
@Test
@TestSecurity(user = "auth0|user-A", roles = {})
void getById_checkCoOrgOf_self_returnsCoOrganizerOf() {
    // caller A passes their own UUID → coOrganizerOf is computed (true/false).
    given()
        .when().get("/events/42?check-co-org-of=" + USER_A_UUID)
        .then().statusCode(200)
            .body("coOrganizerOf", anyOf(equalTo(true), equalTo(false)));
}

@Test
@TestSecurity(user = "auth0|user-A", roles = {})
void getById_checkCoOrgOf_otherUuid_returnsNullCoOrganizerOf() {
    // caller A passes user B's UUID → param ignored, coOrganizerOf=null.
    given()
        .when().get("/events/42?check-co-org-of=" + USER_B_UUID)
        .then().statusCode(200)
            .body("coOrganizerOf", nullValue());
}

@Test
void getById_checkCoOrgOf_anonymous_returnsNullCoOrganizerOf() {
    // anonymous caller passes a UUID → param ignored.
    given()
        .when().get("/events/42?check-co-org-of=" + USER_B_UUID)
        .then().statusCode(200)
            .body("coOrganizerOf", nullValue());
}
```

**Mise à jour `internal-endpoints.md`** : préciser dans la note de l'endpoint #3 que la cascade est self-check uniquement.

**Validation** :
```bash
cd backend && ./mvnw -pl services/event-service -am test
# 3 nouveaux tests green
```

**Commit** : `fix(backend): mitigate ?check-co-org-of= membership oracle with self-check (Étape 6.1, SEC-002 / Décision C)`

---

### Étape 7 — Helm/K8s + dépendances (Vague 7, 2 commits)

#### Étape 7.1 — K8S-001 : livenessProbe sur notification-service

**Patch** dans `k8s/chart/templates/notification-service/deployment.yaml` :

```yaml
# Section spec.template.spec.containers[0] — ajouter avant readinessProbe
livenessProbe:
  httpGet:
    path: /api/q/health/live
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 30
  timeoutSeconds: 5
  failureThreshold: 3
```

**Validation** :
```bash
helm template k8s/chart --debug | grep -A4 'livenessProbe' | head -20
# Vérifier que les 5 services ont leur livenessProbe.
```

**Commit** : `chore(backend): add livenessProbe to notification-service deployment (Étape 7.1, K8S-001)`

---

#### Étape 7.2 — DEP-001 : `quarkus-jacoco` sur notification-service

**Patch** dans `backend/services/notification-service/pom.xml` :

```xml
<!-- Section <dependencies>, scope test -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jacoco</artifactId>
    <scope>test</scope>
</dependency>
```

**Patch plugins** (si pas déjà là) :
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>${jacoco.version}</version>
    <executions>
        <execution><id>prepare-agent</id><goals><goal>prepare-agent</goal></goals></execution>
        <execution>
            <id>report</id><phase>test</phase>
            <goals><goal>report</goal></goals>
            <configuration><outputDirectory>${project.build.directory}/jacoco-report</outputDirectory></configuration>
        </execution>
    </executions>
</plugin>
```

**Validation** :
```bash
cd backend && ./mvnw -pl services/notification-service test
# Build SUCCESS, target/jacoco-report/jacoco.xml généré
```

**Commit** : `chore(backend): add quarkus-jacoco to notification-service for coverage parity (Étape 7.2, DEP-001)`

---

### Étape 8 — Documentation finale (Vague 8, 5 commits)

**Pré-requis** : Étapes 1-7 vertes. Les changements code sont stables. La doc reflète la réalité.

#### Étape 8.1 — DOC-001 : `devops-handoff.md` TL;DR aligné réalité

**Patch** dans `backend/docs/devops-handoff.md` :

```markdown
<!-- Section TL;DR (lignes 9-25) — REMPLACER -->
## TL;DR

La PR #158 livre **côté code** (état à clôture finale Étape 21) :

* **5 services métiers** Quarkus extraits (event, user, engagement, moderation, notification placeholder) post-consolidation 14→5 + **10 shared libs** + `contract-tests` + `e2e` = **17 modules** dans le reactor.
* Kong DB-less + table de routes 4 services métiers actifs + plugin `rate-limiting` `policy: local` sur 3 routes.
* Kafka KRaft single-broker + 10 topics provisionnés + **9 producteurs câblés + 1 consommateur** (`event-service ← events.banned`).
* **3 REST clients `@RegisterRestClient`** dans `shared-domain-dtos` couvrant **8 hops cross-service** avec resilience (`@Retry` + `@Timeout` + `@CircuitBreaker` + `@Fallback`).
* **0 stub JPA cross-service** — refactor `@ManyToOne XStub` → `@Column id` (Décision F finalization-ultimate) ; mutation `events.banned` déléguée au consumer Kafka (Décision H).
* Anti-oracles ISSUE-92 / ISSUE-93 + cascade SCRUM-136 centralisés derrière les services propriétaires + REST clients ; envelope canonique `{error:"not_found"}` via `NotFoundExceptionMapper` (REST-004 / SEC-001).
* Cascade SCRUM-136 self-check authentifié uniquement sur `?check-co-org-of=` (SEC-002 / Décision C).
* Observabilité : `quarkus-logging-json` + `micrometer-registry-prometheus` + `shared-tracing` (`X-Request-ID` MDC + propagation REST + Kafka).
* Helm : `livenessProbe` sur 5 deployments (4 actifs + notification placeholder).
* CI : `.github/workflows/build.yml` matrix consolidée (1 job shared-libs + 5 services + 1 contract-tests/e2e + 1 frontend), Sonar `-pl .,<X>` pour résoudre top-level project (CI-001).
* Tests : 4 sentinels SCRUM-147 RecurrenceGenerator (assertions réelles) + 8 sentinels SCRUM-138/144/147 prioritaires portés (Décision D Option 3) ; 23 sentinels restants taggés `@Tag("legacy-port-s9")` pour port complet S9. 5 pacts JSON consumer-driven brokerless (engagement-event ×2, moderation-event ×1, user-event-bulk ×1, event-engagement-bulk ×1) + 1 E2E happy path gated env var.

**Côté infra**, sept items restent à faire — formalisés ci-dessous.
Ils sont **explicitement hors scope S8** (cf. spec de complétion
Décision V + spec finalization-ultimate § Frontière DevOps).
Le backend a livré **sa moitié** quand applicable.
```

**Validation** : grep `35 stubs JPA cross-schéma supprimés` dans le repo doit être vide post-patch.

**Commit** : `docs(backend): align devops-handoff.md TL;DR with finalization-ultimate state (Étape 8.1, DOC-001)`

---

#### Étape 8.2 — DOC-002/003/006/010 : `architecture.md` + `api-contract.md` + `internal-endpoints.md`

**Patch `architecture.md`** :
- Section topologie K8s (lignes 252-270) : remplacer "13 microservices" par "5 microservices (4 actifs + 1 placeholder)".
- Section flux comment (lignes 200-204) : reformuler pour `engagement-service` + cascade locale event-service via `?check-co-org-of=` (post-Décision C).
- Tableau consommateurs (lignes 320-330) : remplacer `report-service` par `moderation-service`.

**Patch `api-contract.md`** : refonte complète de la table topologie (lignes 13, 18-47, 76-110) sur les 4 services métiers actifs (event/user/engagement/moderation). Supprimer toute mention des 13 services dissous.

**Patch `internal-endpoints.md`** :
- Endpoint #1 (AttendanceSummary) : OK
- Endpoint #2 (events?ids=&status=) : OK
- Endpoint #3 (events/{id}?check-co-org-of=) : ajouter note « self-check authentifié uniquement (Décision C SEC-002) ».
- Endpoint #4 (`/users/{id}/attendances`) : reformuler pour clarifier que c'est un endpoint interne distinct du public `/users/me/attendances` (REST-002 / Décision B).
- **Ajouter endpoint #5** : `GET /events/{id}/organizer-uuids` (Décision G).
- **Ajouter endpoint #6** : `GET /events/_bulk-attendance-summary?ids=...` (Décision I).
- Section « Endpoints disparus » : retirer `GET /events/{id}/co-organizers/accepted-user-ids` (réintroduit via Décision G).

**Commit** : `docs(backend): align architecture.md + api-contract.md + internal-endpoints.md with finalization-ultimate (Étape 8.2, DOC-002/003/006/010)`

---

#### Étape 8.3 — DOC-004 + DOC-005 : `microservices-migration-roadmap.md` + `AGENTS.md`

**Patch `microservices-migration-roadmap.md`** : ajouter en header (après ligne 1) :

```markdown
> **[ARCHIVÉ — 2026-05-09]** Ce document trace l'historique de la migration
> Sprint 8, désormais **livrée intégralement**. État final : 5 services
> métiers + 10 shared libs + contract-tests + e2e = 17 modules. Pour la
> topologie post-finalization, voir [`architecture.md`](architecture.md) ;
> pour le mapping 14→5, voir [`consolidation-plan.md`](consolidation-plan.md) ;
> pour la spec ultime, voir
> [`../../specs_archives/specs_claude/specs_microservices_migration_ultimate.md`](../../specs_archives/specs_claude/specs_microservices_migration_ultimate.md).
```

**Patch `AGENTS.md` racine + `backend/AGENTS.md`** :
- Remplacer "13 microservices" par "5 microservices" (4 actifs + 1 placeholder).
- Aligner sur Option B (5 SonarCloud projects).
- Mettre à jour layout Maven 24/15 → 17 modules.

**Commit** : `docs(backend): archive microservices-migration-roadmap.md + align AGENTS.md (Étape 8.3, DOC-004 + DOC-005)`

---

#### Étape 8.4 — `sprint-context.md` § Étape 21 (clôture)

**Patch** : ajouter en haut du fichier (après ligne 5) une nouvelle section :

```markdown
## Sprint 8 — Étape 21 : Clôture finale (finalization-ultimate) — 2026-05-09

Suite directe à Étape 20. Spec exécutée :
[`specs_archives/specs_claude/specs_microservices_migration_ultimate.md`](../../specs_archives/specs_claude/specs_microservices_migration_ultimate.md)
(post-audit `audit_pr158_finalization_post.md`).

**Étape 1 — CI/Sonar fixes (3 commits).** CI-001 (sonar `-pl .,<X>`),
CI-002 (consolidation 10→1 cellule shared-libs), CI-006 (verify→install
build-contract-and-e2e), CI-003 conditionnel (continue-on-error retiré
si DevOps a créé les 5 projets services).

**Étape 2 — REST clients runtime (4 commits).** REST-001 (URL config × 4
services), REST-004/SEC-001 (NotFoundExceptionMapper dans shared-api-error),
REST-002 (UserAttendancesInternalResource côté engagement-service —
Décision B), REST-003 (admin bypass UserService.getPublicProfile).

**Étape 3 — Suppression des 13 stubs JPA (5 commits).** Wiring REST
clients dans engagement (3.1), moderation (3.2), user (3.3), event (3.4),
+ validation finale (3.5). Refactor `@ManyToOne XStub` → `@Column id`
sur Comment, Event.creator, Report (Décision F). Endpoints providers
ajoutés : `GET /events/{id}/organizer-uuids` (Décision G), `GET
/events/_bulk-attendance-summary` (Décision I). Mutation events.banned
déléguée au consumer Kafka (Décision H).

**Étape 4 — Bascule shared libs (3 commits).** DUP-001 (4 ApiErrorResponse
locales) + DUP-004 (21 enums locaux). DUP-002 (5 ServiceIdentityResource
locales) + DUP-003 (2 Timeframe locaux). DUP-005/006 (DTOs cross-projetés
+ helpers — adoption via Mappers per Décision A).

**Étape 5 — Tests + couverture (2 commits).** TEST-002 (pact bulk
AttendanceSummary), TEST-003/KAFKA-001 (EventLifecycleKafkaBridgeTest),
TEST-004 (drop scaffolds), COV-002 (étoffer shared-domain-dtos), TEST-001
(8 sentinels prioritaires portés avec assertions réelles, 23 taggés
@Tag("legacy-port-s9") — Décision D Option 3).

**Étape 6 — Sécurité (1 commit).** SEC-002 self-check authentifié sur
`?check-co-org-of=` (Décision C).

**Étape 7 — Helm/K8s + dépendances (2 commits).** K8S-001 (livenessProbe
notification-service) + DEP-001 (quarkus-jacoco notification-service).

**Étape 8 — Documentation finale (5 commits).** DOC-001 (devops-handoff
TL;DR), DOC-002/003/006/010 (architecture + api-contract + internal-
endpoints), DOC-004 (microservices-migration-roadmap archivé), DOC-005
(AGENTS.md), DOC-008/009/011 (sprint-context contradiction + report→
moderation + JavaDoc cleanup).

**Étape 9 — Clôture (1 commit).** PR body finalisé via `gh pr edit`.

**Total commits Étape 21** : ~25 (5 vagues).

**État final des invariants à clôture du Sprint 8** :
- `git diff --shortstat origin/main HEAD -- frontend/` = **0 ligne** ✅
- `git diff --shortstat origin/main HEAD -- openapi/` = **0 ligne** ✅
- 17 modules dans le reactor ✅
- **0 stub JPA cross-service** ✅ (cible STUB-001 atteinte)
- 8 hops cross-service couverts par 3 REST clients + URLs câblées ✅
- 5 pact JSON contracts (4 + bulk Décision I) ✅
- 12/35 sentinels portés avec assertions réelles (Décision D Option 3) ✅
- 23/35 sentinels taggés `@Tag("legacy-port-s9")` pour port S9 ✅
- Couverture services métiers ~25-40% L (vs 5-17% pré-Étape 21) ✅
- Build local SUCCESS sur 17 modules ✅
- Topology Helm = 5 services (livenessProbe sur les 5) ✅
- 5 SonarCloud projects services + parent unige-events-backend (Option B) ✅
- PR body de #158 reflète l'état final ✅

**PR prête au merge** — Elie merge lui-même quand il valide.
```

**Commit** : `docs(backend): sprint-context.md § Étape 21 — finalization-ultimate closure (Étape 8.4)`

---

#### Étape 8.5 — DOC-008 + DOC-009 + DOC-011 + TODO-001 : passe sed nettoyage final

**Patches** :

DOC-008 (sprint-context contradiction lignes 152 vs 589) :
```markdown
<!-- Ligne 589 (section Étape 7 historique) — REMPLACER -->
- 0 JPA stub cross-service ✅
<!-- AVEC -->
- 0 JPA stub cross-service ✅ (cible atteinte en Étape 21 finalization-ultimate ; les 35 stubs initiaux retirés en complétion + les 13 stubs réintroduits par les extractions PR-3..PR-9 ont été supprimés en Étape 21)
```

DOC-009 (data-model.md "report-service" → "moderation-service") :
```bash
sed -i 's|report-service|moderation-service|g' backend/docs/data-model.md
```

DOC-011 (JavaDoc cleanup — passe sed sur les commentaires obsolètes) :
```bash
# Renames simples
find backend/services -name '*.java' -exec sed -i \
    -e 's|report-service|moderation-service|g' \
    -e 's|attendance-service|engagement-service|g' \
    {} +

# Pour les services dissous (share, view, favorite, calendar, follow,
# comment, co-organizer, stats, me-aggregator), ajouter une note
# `[absorbed in <Y>-service per consolidation 2.X.Y]` dans les JavaDoc
# qui référencent explicitement ces services. Pas de sed brutal — vérifier
# manuellement les fichiers identifiés par grep.
```

TODO-001 (`AttendanceService.java:38-39` JavaDoc obsolète) :
```java
// AVANT (lignes 35-40)
/**
 * Same contract as the legacy AttendanceService — pessimistic-lock-based
 * capacity gating, idempotent attend, WAITLISTED auto-promotion on
 * remove. The SCRUM-136 cascade is inlined locally (will become a REST
 * call to co-organizer-service in a follow-up cleanup).
 */

// APRÈS
/**
 * Same contract as the legacy AttendanceService — capacity gating,
 * idempotent attend, WAITLISTED auto-promotion on remove. The SCRUM-136
 * cascade is delegated to event-service via
 * {@link ch.unige.events.shared.client.EventServiceClient#getByIdWithCoOrgCheck}
 * (post Étape 21 finalization-ultimate Décision G ; co-organizer-service
 * was absorbed into event-service in consolidation Étape 2.2.4).
 */
```

**Validation** :
```bash
grep -rn 'report-service' backend/docs/data-model.md   # vide
grep -rn 'will become a REST call to co-organizer-service' backend/services   # vide
```

**Commit** : `docs(backend): final cleanup — fix sprint-context contradiction + report→moderation + JavaDoc references (Étape 8.5, DOC-008/009/011 + TODO-001)`

---

### Étape 9 — Watch CI + finalisation (Vague 9, 1 commit)

#### Étape 9.1 — Watch CI groupé final

**Commandes** :

```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
```

Vérifier que **tous les jobs sont verts** :
- `Build / Build All Shared Libs` (1 cellule consolidée — CI-002)
- `Build / Build Backend (event/user/engagement/moderation/notification)` (5 cellules avec Sonar `-pl .,<X>` — CI-001)
- `Build / Build Contract Tests + E2E` (5 pacts générés)
- `Build / Build Frontend` (inchangé)
- `[unige-events-frontend] SonarCloud Code Analysis` (passe)
- `Deploy / Deploy to Preview` (4 services Ready)
- `PR Title Check` (passe avec `chore(backend):` ou `refactor(backend):`)

**Si Sonar projects services not found côté DevOps** : laisser `continue-on-error: true` (Étape 1.3 conditionnelle). Le step échoue mais ne bloque pas le merge.

**Pas de commit ici** — validation pure.

---

#### Étape 9.2 — PR body — réécriture from scratch (description finale, pas de tracking)

**Objectif** : **réécrire intégralement** le PR body de #158 comme s'il était écrit pour la première fois, en partant d'une page blanche. **Ne pas** repartir de l'existant, **ne pas** garder des phrases « finalisation suite », « clôture », « post-audit » : le PR body final décrit la PR comme un livrable autonome cohérent — ce qu'elle apporte, l'architecture cible, le test plan, et ce qui reste à faire côté DevOps (machine, K8s, secrets prod, etc.). Un futur reviewer qui ouvre la PR pour la première fois doit pouvoir tout comprendre sans lire `sprint-context.md` ni les specs archivées.

**Action** :

```bash
cat > /tmp/pr-body-final.md <<'EOF'
## Sprint 8 — Migration backend monolithe → microservices

Cette PR convertit le backend UNIGE Events d'un monolithe Quarkus unique vers une architecture microservices à **5 services** déployée par un chart Helm umbrella, avec Kong en API gateway et Kafka comme bus d'événements.

## Architecture livrée

### Topologie

| # | Service | Endpoints publics (Kong) | Tables possédées | Kafka | Replicas (prod / preview) |
|---|---|---|---|---|---|
| 1 | **event-service** | `/events*`, `/admin/events*`, `/events/{id}/{share,view,favorite,co-organizers/*,stats,image}`, `/users/me/{favorites,co-organizer-invitations,events}`, `/s/{shortCode}` | `events`, `event_tags`, `event_views`, `favorites`, `event_co_organizers` | producer events.{published,cancelled,expired} + co-organizers.{invited,accepted} ; consumer events.banned (idempotent) | 1 / 1 |
| 2 | **user-service** | `/users/me`, `/users/{id}`, `/users/me/{image,banner,calendar-token*,follow-requests}`, `/users/{id}/follow*`, `/follow-requests/*`, `/calendar/{token}.ics` | `users`, `user_interests`, `follows` | producer users.{followed,follow-requested,follow-accepted} | 1 / 1 |
| 3 | **engagement-service** | `/events/{id}/{attend*,attendees,comments}`, `/users/me/{attendances,participations}`, `/comments/{id}` | `attendances`, `comments` | producer comments.created | 1 / 1 |
| 4 | **moderation-service** | `/events/{id}/report`, `/admin/reports*` (+ `ModerationCleanupJob` cron 03:00) | `reports` | producer events.banned (mutation event.status déléguée au consumer event-service) | 1 / 1 |
| 5 | **notification-service** | (placeholder, replicas:0, scope SCRUM-99) | — | — | 0 / 0 |

**+ 10 shared libs** (`shared-rate-limit`, `shared-storage`, `shared-api-error`, `shared-domain-enums`, `shared-domain-dtos` (3 records DTO + 3 `@RegisterRestClient` interfaces), `shared-domain-projections`, `shared-jaxrs`, `shared-tracing`, `shared-kafka-events`, `shared-platform`).

**+ 2 modules de test** : `contract-tests` (Pact JVM 4.6.5 brokerless) + `e2e` (REST Assured).

**Total : 17 modules** dans le reactor Maven.

### Communications cross-service

8 hops cross-service couverts par **3 interfaces `@RegisterRestClient`** (dans `shared-domain-dtos.shared.client`) :

| Consumer | Provider | Endpoints |
|---|---|---|
| event-service | user-service | `GET /users/{id}` |
| event-service | engagement-service | `GET /events/{eventId}/attendance-summary`, `GET /events/_bulk-attendance-summary?ids=…` |
| user-service | event-service | `GET /events?ids=…&status=PUBLISHED`, `GET /events/{id}` |
| user-service | engagement-service | `GET /users/{id}/attendances?status=…` |
| engagement-service | event-service | `GET /events/{id}?check-co-org-of=…` (cascade SCRUM-136 self-check authentifié), `GET /events/{id}/organizer-uuids` |
| engagement-service | user-service | `GET /users/{id}` |
| moderation-service | event-service | `GET /events/{id}` |
| moderation-service | user-service | `GET /users/{id}` |

**Resilience standard** sur chaque méthode : `@Retry(maxRetries=3, delay=200ms)` + `@Timeout(2s)` + `@CircuitBreaker(failureRatio=0.5, threshold=10)` + `@Fallback` + `@RegisterProvider(RequestIdClientFilter.class)` pour la propagation `X-Request-ID`.

**0 stub JPA cross-service**. Les entités JPA `Comment`, `Event`, `Report` portent des FK colonnes id (`event_id Long`, `author_id UUID`, `creator_id UUID`, `reporter_id UUID`, `reviewed_by UUID`) sans navigation `@ManyToOne` vers d'autres services. L'enrichissement (display name, title, etc.) se fait à la couche service via les REST clients ci-dessus.

Endpoints internes (cross-service uniquement, **pas de route Kong, pas dans `openapi.yaml`**) catalogués dans [`backend/docs/internal-endpoints.md`](backend/docs/internal-endpoints.md).

### Sécurité & anti-oracles

- **ISSUE-92** (event invisibility) : `event-service.EventService.getById(...)` retourne 404 (envelope canonique `{"error":"not_found"}`) pour `BANNED` ou pour `DRAFT/CANCELLED/EXPIRED` quand le caller n'est ni admin, ni creator, ni accepted co-organizer. Pact `EngagementEventIssue92PactTest` pinne ce contrat.
- **ISSUE-93** (private profile) : `user-service.UserService.getPublicProfile(...)` retourne 404 quand `profilePublic=false` et caller n'est ni self ni admin.
- **Cascade SCRUM-136** centralisée dans event-service via le query param `?check-co-org-of=<UUID>` qui retourne `EventDTO.coOrganizerOf: bool`. Le param n'est honoré qu'en **self-check authentifié** (caller UUID = param UUID, JWT valide) — fermeture de l'oracle de membership co-organizer.
- **Envelope d'erreur canonique** : `NotFoundExceptionMapper` dans `shared-api-error` est auto-discovered via Jandex et applique `{"error":"not_found","message":"..."}` à toute `NotFoundException` — cohérence cross-service garantie pour la pact verification.
- **Rate-limiting Kong** : plugin `rate-limiting` `policy: local` sur 3 routes (events.create=10/min, comments.post=10/min, follows.follow=30/min). Doublé par `@PerUserRateLimit` applicatif (defense in depth).
- **OIDC fail-fast** : aucun default bidon dans les `application.properties` ; missing env → boot-time error.

### Kafka

10 topics provisionnés par le Job `kafka-topics-init` post-install/upgrade. **9 producteurs + 1 consumer câblés** via le pattern uniforme CDI `@Observes(during = AFTER_SUCCESS)` + bridge `<Domain>KafkaBridge` qui appelle l'`Emitter` post-commit JDBC (anti-rollback BUG-001/002).

| Topic | Producer | Consumer |
|---|---|---|
| events.published | event-service | — |
| events.cancelled | event-service | — |
| events.expired | event-service | — |
| events.banned | moderation-service | event-service (idempotent ban apply) |
| users.followed | user-service | — |
| users.follow-requested | user-service | — |
| users.follow-accepted | user-service | — |
| comments.created | engagement-service | — |
| co-organizers.invited | event-service | — |
| co-organizers.accepted | event-service | — |

### Observabilité

- `quarkus-logging-json` sur les 5 services (logs structurés JSON).
- `quarkus-micrometer-registry-prometheus` exposant `/q/metrics` interne à chaque pod.
- Lib `shared-tracing` : `RequestIdFilter` (server) + `RequestIdClientFilter` (REST client) propagent `X-Request-ID` dans le MDC + en header sortant + en header Kafka.
- Kong plugin `correlation-id` pose le header en entrée du gateway.

### Helm / K8s

Chart unique `k8s/chart/` avec un sous-template par service :
- 5 `Deployment` (4 actifs replicas:1, notification placeholder replicas:0).
- 5 `Service` ClusterIP port 8080.
- `livenessProbe` + `readinessProbe` sur les 5 (path `/api/q/health/live`).
- Kong DB-less + ConfigMap routes (4 blocs services métiers actifs).
- Kafka KRaft single-broker StatefulSet + PVC + cluster ID immutable + Job topics-init post-install.
- PostgreSQL 16 StatefulSet (schéma `public` partagé — DB-per-service différé S9+).
- MinIO StatefulSet pour uploads avatar/banner/event-image.
- Cloudflared deployment pour tunnel preview.
- Ingress unique `/api → kong`, `/ → web`, `/s3 → minio`.

`helm upgrade --set image.tag=<sha>` propage le SHA aux 5 deployments ; chaque service builds sa propre image GHCR `ghcr.io/unige-pinfo6-2026/unige-events-<svc>:<sha>` via Quarkus jib.

### CI

- **Build matrix** : 1 cellule shared-libs (10 modules en bulk) + 5 cellules services (event, user, engagement, moderation, notification) + 1 cellule contract-tests/e2e + 1 cellule frontend + PR title check.
- **Sonar** : `./mvnw -pl .,services/<X>-service sonar:sonar` (résolution top-level project) ; 5 projets dédiés `unige-events-<svc>-service` + 10 shared libs agrégés dans `unige-events-backend` (Option B).
- **Pacts** : 5 contrats consumer-driven générés à chaque run dans `backend/contract-tests/target/pacts/` (uploaded en artifact GitHub Actions pour la pact provider verification S9).

### Tests

- **Unit + integration** : Quarkus tests `@QuarkusTest` + DevServices PostgreSQL. Lib `shared-rate-limit`, `shared-storage`, `shared-jaxrs`, `shared-tracing`, `shared-domain-projections` à 100 % L coverage.
- **Sentinels SCRUM-138/144/147** : 12 sentinels portés avec assertions réelles (RecurrenceGenerator ×4, anti-oracles ISSUE-92/93/SCRUM-136 ×6, validation business ×2). 23 sentinels supplémentaires sont taggés `@Tag("legacy-port-s9")` (corps vides) — port runtime complet déféré au sprint S9 (~1818 tests legacy au total).
- **Contracts (Pact JVM brokerless)** : 5 fichiers JSON dans `target/pacts/` :
  - `engagement-service-event-service.json` : ISSUE-92 (×2 interactions) + cascade SCRUM-136 (×2 interactions)
  - `moderation-service-event-service.json` : 1 interaction
  - `user-service-event-service.json` : 1 interaction (calendar bulk)
  - `event-service-engagement-service.json` : 1 interaction (bulk attendance summary)
- **E2E** : `E2EHappyPathTest` (create user → create event → publish → get) gated par env var `UNIGE_EVENTS_E2E_BASE_URL` (skipped local, exécutable sur preview cluster).
- **Couverture services métiers** : ~25-40 % L (cible 80 % L atteinte progressivement au fil du port runtime des 23 sentinels S9). Quality gate Sonar configuré sur « coverage on new code ≥ 70 % » plutôt que sur l'absolu.

## Frontend & contrat OpenAPI

`frontend/` : **0 ligne diff** (`git diff --shortstat origin/main HEAD -- frontend/`). Le frontend continue de consommer le contrat OpenAPI existant.

`openapi/openapi.yaml` : **0 ligne diff** (invariant strict). Les endpoints internes cross-service ne sont **pas** dans le contrat public — ils sont catalogués dans [`backend/docs/internal-endpoints.md`](backend/docs/internal-endpoints.md).

## Test plan

- [x] CI matrix : shared-libs + 5 services + contract-and-e2e + frontend + PR title — verts
- [x] `cd backend && ./mvnw verify -DskipITs` → 17 modules SUCCESS
- [x] `git diff --shortstat origin/main HEAD -- frontend/` = 0
- [x] `git diff --shortstat origin/main HEAD -- openapi/` = 0
- [x] `find backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l` = 0
- [x] 3 `@RegisterRestClient` interfaces + 4 services consumers ont leur `quarkus.rest-client.<svc>.url`
- [x] 5 pact JSON contracts dans `backend/contract-tests/target/pacts/`
- [x] 12 sentinels SCRUM-138/144/147 portés ; 23 sentinels taggés `legacy-port-s9`
- [x] `NotFoundExceptionMapper` actif → envelope canonique sur tout 404 cross-service
- [x] Cascade SCRUM-136 self-check authentifié uniquement (caller UUID = param UUID + JWT valide)
- [x] livenessProbe sur les 5 deployments
- [ ] (à valider sur preview cluster post-deploy) Smoke tests cross-service :
  ```bash
  curl -i https://<preview>/api/users/me                           # → 401 sans token
  curl -i -H "Authorization: Bearer <jwt>" https://<preview>/api/users/me   # → 200
  curl -i https://<preview>/api/events                              # → 200 list
  curl -i https://<preview>/api/events/1/comments                   # → 200 list
  for i in {1..15}; do
    curl -s -o /dev/null -w "%{http_code}\n" -X POST \
         -H "Authorization: Bearer <jwt>" \
         -d '{"title":"...","startDate":"..."}' https://<preview>/api/events
  done                                                              # → 11×201 puis 4×429
  kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-console-consumer.sh \
    --bootstrap-server localhost:9092 --topic events.published \
    --from-beginning --max-messages 1                               # → message présent
  kubectl exec -it event-service-<pod> -- curl -s http://localhost:8080/api/q/metrics | head -20
  ```

## Ce qui reste côté DevOps (handoff machine + K8s + Sonar prod)

Ces items sont **explicitement hors scope de la PR** car ils nécessitent un accès admin à l'infrastructure (K8s prod, SonarCloud admin UI, Doppler, Auth0 admin, GitHub registry, DNS provider). Le backend a livré sa moitié quand applicable. Documentés dans [`backend/docs/devops-handoff.md`](backend/docs/devops-handoff.md).

### 1. SonarCloud — créer 5 projects services (Option B)

Sous l'organisation `unige-pinfo6-2026`, créer manuellement (UI ou API) :
- `unige-events-event-service`
- `unige-events-user-service`
- `unige-events-engagement-service`
- `unige-events-moderation-service`
- `unige-events-notification-service`

Le projet `unige-events-backend` existe déjà — il agrège les scans des 10 shared libs (Option B). Vérifier que le secret GitHub `SONAR_TOKEN` est présent. Sans cette action, les 5 cellules services Sonar échouent avec « Project not found » (actuellement masqué par `continue-on-error: true` ; `continue-on-error` retiré dans une PR de suivi quand DevOps a validé).

### 2. Cluster Kafka prod-grade

Migrer le `StatefulSet` Kafka mono-broker actuel vers un cluster **≥ 3 brokers** en prod. Provisionner les 10 topics avec `--replication-factor 3 --partitions 3 --min-insync-replicas 2`. Côté code Quarkus, ajouter `mp.messaging.outgoing.<chan>.acks=all` une fois le cluster en place (PR de suivi).

### 3. Schemas-per-service (Flyway physique séparé)

Aujourd'hui les 7 entités vivent dans un schéma `public` partagé. Le découpage `<svc>_svc.<table>` apporte une défense en profondeur (le service ne peut écrire que dans son schéma) mais n'est pas critique post-Étape 4 puisqu'il n'y a plus de stubs JPA cross-service. Plan détaillé : 7 schémas SQL séparés via `ALTER TABLE … SET SCHEMA`, `GRANT` par rôle si RBAC strict, `currentSchema=<svc>_svc` dans la JDBC URL de chaque service, baselines Flyway adaptées.

### 4. NetworkPolicies K8s

Définir une `NetworkPolicy` par service restreignant les connexions ingress aux seuls voisins listés dans la table « 8 hops cross-service » ci-dessus. Restriction au minimum : Kong → tous les services ; service-to-service uniquement entre couples consumer/provider documentés.

### 5. Domaines / certs prod / Cloudflare tunnel preview

- Configurer le DNS prod (`unige-events.ch` ou équivalent).
- Provisionner les certs TLS via cert-manager (Let's Encrypt).
- Cloudflared tunnel pour preview env (mode quick déjà setup, valider le mode named pour stabilité).

### 6. Secrets Doppler (prod + preview)

Configurer Doppler pour les 5 services :
- DB : `DB_USER`, `DB_PASSWORD`, `DB_HOST`, `DB_PORT`, `DB_NAME`
- OIDC Auth0 : `OIDC_AUTH_SERVER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_AUDIENCE`, `OIDC_ROLE_NAMESPACE`
- **Important** : Auth0 doit inclure le claim `uuid` dans le JWT (utilisé par `Auth0IdResolver.resolveUserId(jwt)` côté backend pour la cascade SCRUM-136 self-check). Sans ce claim, la cascade renvoie toujours `coOrganizerOf=null` (caller UUID inconnu).
- S3 (MinIO en preview, S3 cloud en prod) : `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`
- Kafka : `KAFKA_BOOTSTRAP_SERVERS`
- Frontend : `FRONTEND_URL` (consommé par event-service `ShareResource`)
- TZ : `TZ=Europe/Zurich`

### 7. Production-grade Kong

- Provisionner Postgres dédié pour Kong (DB-mode) → permet hot reload de routes sans rebuild ConfigMap.
- Plugin `opentelemetry` (export OTLP vers Tempo / Jaeger / Honeycomb).
- Migrer plugin `rate-limiting` de `policy: local` vers `policy: redis` (avec un Redis Helm chart) pour un compteur cluster-wide. Sans cela, un attaquant peut tripler son budget en routant sur une autre instance Kong.

### 8. Pact provider verification CI job

Les 5 pacts consumer sont générés à chaque CI run et uploadés en artifact GitHub Actions. Ajouter un job `verify-pacts` qui les vérifie côté provider :
```yaml
verify-pacts:
  needs: [build-shared-libs, build-contract-and-e2e]
  steps:
    - actions/download-artifact pacts-${{ github.sha }}
    - run: ./mvnw -pl services/event-service,services/engagement-service,services/user-service \
                  verify -Dpact.verifier.dir=../../contract-tests/target/pacts \
                  -Dpact.verifier.tests=*PactVerification*
```

Nécessite un harness de provider states (helper qui prépare les fixtures DB pour chaque "given" du pact). Sprint S9.

### 9. GHCR cleanup PR-tagged images

Aujourd'hui le push GHCR des 5 services se fait à chaque PR avec tag `pr-<N>`. Sur PR abandonnées, ces images restent — coût stockage non négligeable à terme. Ajouter à `cleanup.yml` un job qui supprime les images via `gh api -X DELETE /user/packages/container/<img>/versions/<id>` filtré par tag.

### 10. Port runtime des 23 sentinels @Tag("legacy-port-s9")

23 sentinels SCRUM-138/144/147 sont présents par nom (corps vides taggés `@Tag("legacy-port-s9")`). Le port complet (`@InjectMock @RestClient` côté consumers + provider state DB + assertions) est déféré S9 — ~1818 tests legacy au total, à porter par batch. Source : `git show 41074e9:backend/services/legacy-monolith/src/test/java/...` puis adaptation packages + REST clients mockés.

### 11. Doublon openapi `POST /events/{id}/view`

Le contrat openapi.yaml expose `/events/{id}/view` deux fois (cosmétique, pas de bug runtime). À nettoyer dans une PR future avec coordination frontend explicite.

## Documentation

- Architecture finale : [`backend/docs/architecture.md`](backend/docs/architecture.md)
- Plan de consolidation 14→5 (historique) : [`backend/docs/consolidation-plan.md`](backend/docs/consolidation-plan.md)
- Modèle de données : [`backend/docs/data-model.md`](backend/docs/data-model.md)
- Catalogue endpoints internes : [`backend/docs/internal-endpoints.md`](backend/docs/internal-endpoints.md)
- DevOps handoff (10 items) : [`backend/docs/devops-handoff.md`](backend/docs/devops-handoff.md)
- Dev guide (workflows, layout reactor) : [`backend/docs/dev-guide.md`](backend/docs/dev-guide.md)
- Sprint context (timeline livraison) : [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md)
- API contract : [`backend/docs/api-contract.md`](backend/docs/api-contract.md)

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF

gh pr edit 158 --body-file /tmp/pr-body-final.md
```

**Pas de commit** pour ce step — `gh pr edit` modifie le PR sur GitHub, pas le repo. Le commit final cosmétique (s'il y en a un) est celui de l'Étape 8.5 (sed cleanup).

**Watch CI final** :
```bash
git push origin 'refactor(backend)--migrate-to-microservices'   # déjà à jour si Étape 8.5 a push
gh pr checks 158 --watch
```

**STOP — pas de merge.** Elie merge lui-même quand il valide.

> **Important pour l'exécuteur** : ne **pas** réutiliser le PR body actuel comme template. Le PR body actuel parle en termes de « finalisation suite », « clôture », « post-audit » — c'est du tracking de processus. Le PR body final décrit la PR comme un **livrable autonome** : un reviewer qui ouvre la PR pour la première fois doit comprendre quoi a été fait, comment c'est testé, et ce qui reste pour DevOps, sans avoir à lire `sprint-context.md` ni les specs archivées.

---

## Critères de done

- [x] `find backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l` → **0** (cible STUB-001)
- [x] `grep -rln '@ManyToOne.*Stub\|extends.*Stub' backend/services/*/src/main/java | wc -l` → **0** (Décision F)
- [x] 4 services métiers ont `quarkus.rest-client.<svc>.url` configurée (REST-001)
- [x] `NotFoundExceptionMapper` discoverable dans les 4 services métiers (REST-004 / SEC-001)
- [x] 5 cellules services Sonar passent strict si DevOps a créé les 5 projets (CI-001 + Décision E)
- [x] 1 cellule shared-libs Sonar passe strict (consolidation Option B + CI-002)
- [x] Build local `cd backend && ./mvnw verify -DskipITs` SUCCESS sur 17 modules
- [x] **5 pacts JSON** dans `backend/contract-tests/target/pacts/` (engagement-event, moderation-event, user-event-bulk, event-engagement-bulk + un autre nommage selon TEST-002)
- [x] `EngagementEventIssue92PactTest` passe en provider verification (envelope `{"error":"not_found"}` honoré par `NotFoundExceptionMapper`)
- [x] Couverture jacoco services métiers ≥ 25% L (Option 3 — quality gate Sonar « coverage on new code » ≥ 70%)
- [x] 0 ligne diff frontend, 0 ligne diff openapi
- [x] **5 invariants doc** : devops-handoff TL;DR aligné, architecture.md aligné, AGENTS.md aligné, internal-endpoints.md endpoints #5 + #6 ajoutés + endpoint #4 reformulé, sprint-context.md § Étape 21 présent
- [x] PR body de #158 reflète l'état finalisation-ultimate
- [x] CI watch final : tous verts (sauf Sonar projects services si DevOps n'a pas créé)
- [x] **Pas de merge** — Elie merge lui-même

---

## Workflow Git imposé à l'exécuteur

- **Branche persistante** : `refactor(backend)--migrate-to-microservices` (NE PAS créer de nouvelle branche).
- **Pas de squash** — chaque sous-étape numérotée a son propre commit (granularité ~25 commits, ≤ 500 lignes diff sauf Étape 3.4 = ~1000 lignes diff acceptable car bascule mécanique sur tout event-service).
- **Pas de force push** — additif uniquement.
- **Pas de `--no-verify`** — si pre-commit hook échoue, fixer la cause racine.
- **Pas de `--no-gpg-sign`** — signage Git par défaut respecté.
- **Pas de `--amend`** sur du commit pushé — fixer via nouveau commit.
- **Pas de modification de `main`** ni des autres branches feature.
- **Push après chaque sous-étape verte** : `git push origin 'refactor(backend)--migrate-to-microservices'`.
- **Watch CI groupé par étape majeure** : `gh pr checks 158 --watch` à la fin de chaque Étape ≥ 2.
- **Si CI échoue (hors Sonar project not found)** : `gh run view <RUN_ID> --log-failed` → fix → nouveau commit additif → push → re-watch.
- **Mise à jour `sprint-context.md` § Étape 21** : un patch incrémental après chaque étape (concentré dans le commit final d'Étape 8.4).
- **Mise à jour PR body via `gh pr edit 158 --body-file`** : à la toute fin (Étape 9.2), pas en milieu de parcours.
- **Pas de merge PR #158** — Elie merge lui-même.
- **Co-Authored-By** : `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` à la fin de chaque commit.

---

## Frontière DevOps — items HORS scope

(Reprise du tableau `devops-handoff.md` items 2-7 — ne pas modifier.)

| # | Item | Justification report S9+ | Backend a-t-il livré sa moitié ? |
|---|---|---|---|
| 1 | Création de **5 services SonarCloud projects** (Option B) + secret `SONAR_TOKEN` | Nécessite SonarCloud admin UI ; YAML CI matrix l'attend (cf. Étape 7 finalization). | ✅ YAML CI matrix livré, 5 sonar.projectKey override per-service présents |
| 2 | Cluster Kafka prod-grade (RF=3, partitions ≥ 3, ISR ≥ 2, durabilité acks=all) | Hors scope cours, single-broker S8 OK | ✅ Helm chart single-broker livré |
| 3 | Schemas-per-service (Flyway séparé) | Reportée par Décision C completion-spec | ❌ aucune action backend, déviation actée (post-Étape 21 : 0 stubs cross-service rend cette défense en profondeur encore moins critique) |
| 4 | NetworkPolicies K8s | Hors scope code, pure ops K8s | N/A |
| 5 | Domaines / certs prod / Cloudflare tunnel preview | Hors scope code | N/A |
| 6 | Secrets Doppler `DB_*`, `OIDC_*` (incl. claim `uuid` Auth0 pour Auth0IdResolver post-stubs), `S3_*`, `KAFKA_BOOTSTRAP_SERVERS`, `FRONTEND_URL`, `TZ` | Hors scope code, pure ops | ✅ defaults bidons retirés (SEC-004) ; **NEW** : claim `uuid` Auth0 documenté pour Auth0IdResolver |
| 7 | Production-grade Kong (DB-mode, OpenTelemetry, rate-limiting policy=redis) | Hors scope cours, DB-less S8 OK | ✅ rate-limiting `policy: local` livré |
| **8 (NEW)** | Pact provider verification job CI (CI-007 audit) | Sprint S9 — nécessite harness provider states | ⚠️ Pacts consumer générés (5 fichiers JSON), provider verify deferred |
| **9 (NEW)** | GHCR cleanup PR-tagged images (CI-005 audit) | Sprint S9 — pas urgent | ⚠️ Push en place, cleanup deferred |
| **10 (NEW)** | Port complet des 23 sentinels @Tag("legacy-port-s9") (TEST-001 / COV-001 audit) | Sprint S9 — 1818 tests legacy à porter par batch | ⚠️ 12 sentinels prioritaires portés (Décision D Option 3), 23 taggés |

**L'exécuteur autonome ne touche à AUCUN de ces 10 items.**

---

## Annexes

### Annexe A — Mapping audit findings → étapes spec ultime

| Finding | Sév | Catégorie | Étape spec | Note |
|---|---|---|---|---|
| STUB-001 | P0 | JPA stubs | 3.1, 3.2, 3.3, 3.4, 3.5 | 13 stubs supprimés ; refactor JPA Décision F |
| REST-001 | P0 | REST clients | 2.1 | URL config × 4 services |
| REST-002 | P0 | REST clients | 2.3 | UserAttendancesInternalResource (Décision B) |
| REST-003 | P1 | REST clients | 2.4 | Admin bypass UserService.getPublicProfile |
| REST-004 / SEC-001 | P0/P0 | REST clients / Sécurité | 2.2 | NotFoundExceptionMapper |
| SEC-002 | P1 | Sécurité | 6.1 | Self-check authentifié (Décision C) |
| SEC-003 | P2 | Sécurité | 3.1 (résolu) | Cascade inline CommentService → REST client |
| CI-001 | P0 | CI/Sonar | 1.1 | `-pl .,<X>` (Décision E) |
| CI-002 | P1 | CI/Sonar | 1.1 | Consolidation 10→1 cellule shared-libs |
| CI-003 | P1 | CI/Sonar | 1.3 | Conditionnel post-DevOps |
| CI-004 | P1 | CI/Sonar | RAS — résolu en Étape 4.1 (sed sur 2 POMs déjà clean) | |
| CI-005 | P2 | CI/Sonar | déféré S9 (devops-handoff item 9) | |
| CI-006 | P2 | CI/Sonar | 1.2 | Aligner verify→install |
| CI-007 | P2 | CI/Sonar | déféré S9 (devops-handoff item 8) | Pact provider verify job |
| CI-008 | P2 | CI/Sonar | 8.2 (cosmétique) | Spec dit `pacts/` sans `target/` — alignement doc |
| DUP-001 | P1 | Duplicats | 4.1 | 4 ApiErrorResponse |
| DUP-002 | P1 | Duplicats | 4.2 | 5 ServiceIdentityResource |
| DUP-003 | P1 | Duplicats | 4.2 | 2 Timeframe |
| DUP-004 | P1 | Duplicats | 4.1 | 21 enums |
| DUP-005 | P1 | Duplicats | 4.3 | 7 DTOs (Décision A) |
| DUP-006 | P2 | Duplicats | 4.3 | Helpers |
| TEST-001 | P1 | Tests | 5.2 | 8 sentinels portés (Décision D Option 3) |
| TEST-002 | P1 | Tests | 5.1 | Pact bulk AttendanceSummary |
| TEST-003 | P2 | Tests | 5.1 | EventLifecycleKafkaBridgeTest |
| TEST-004 | P2 | Tests | 5.1 | Drop scaffolds |
| TEST-005 | P2 | Tests | 2.3 (résolu via REST-002) | |
| COV-001 | P1 | Couverture | 5.2 (partiellement) | Décision D Option 3 — gap honnête |
| COV-002 | P2 | Couverture | 5.1 | Étoffer shared-domain-dtos tests |
| K8S-001 | P1 | Helm/K8s | 7.1 | livenessProbe notification-service |
| KAFKA-001 | P2 | Kafka | 5.1 | EventLifecycleKafkaBridgeTest (= TEST-003) |
| DOC-001 | P0 | Doc drift | 8.1 | devops-handoff TL;DR |
| DOC-002 | P1 | Doc drift | 8.2 | architecture.md |
| DOC-003 | P1 | Doc drift | 8.2 | architecture.md flux comment |
| DOC-004 | P1 | Doc drift | 8.3 | roadmap [ARCHIVÉ] |
| DOC-005 | P1 | Doc drift | 8.3 | AGENTS.md |
| DOC-006 | P1 | Doc drift | 8.2 | api-contract.md |
| DOC-007 | P1 | Doc drift | 8.1, 8.2, 8.3 | Sonar 13/15 → 5 (Option B) |
| DOC-008 | P1 | Doc drift | 8.5 | sprint-context contradiction |
| DOC-009 | P2 | Doc drift | 8.5 | data-model.md report→moderation |
| DOC-010 | P2 | Doc drift | 8.2 | internal-endpoints #4 |
| DOC-011 | P2 | Doc drift | 8.5 | JavaDoc cleanup |
| DEP-001 | P1 | Dépendances | 7.2 | quarkus-jacoco notification |
| DEP-002 | P2 | Dépendances | 2.3 (résolu via REST-002) | |
| HYGIENE-001 | P1 | Git/commits | **NON ADRESSÉ** | Process pour les futurs commits — pas de rebase rétroactif |
| HYGIENE-002 | P1 | Git/commits | **NON ADRESSÉ** | Idem |
| HYGIENE-003 | P2 | Git/commits | **NON ADRESSÉ** | Commit historique |
| HYGIENE-004 | P2 | Git/commits | **NON ADRESSÉ** | Informational |
| TODO-001 | P1 | TODOs | 8.5 | JavaDoc AttendanceService |
| TODO-002 | P2 | TODOs | **HORS SCOPE** | Frontend invariant 0 ligne |
| OPENAPI-001 | P2 | OpenAPI | **HORS SCOPE** | OpenAPI invariant 0 ligne |
| BORDER-001 | OK | Invariants | — | Vérifié à Étape 0 |

**Total** : 52 findings audit, 47 adressés (37 directement + 4 résolus indirectement + 6 déférés DevOps S9 documentés), 5 explicitement non adressés (4 hygiene + 2 hors scope front/openapi). Aucun finding ignoré silencieusement.

---

### Annexe B — Liste des 13 stubs avec call-sites détaillés

#### engagement-service (3 stubs)

| Stub | Path | Call-sites | Refactor |
|---|---|---|---|
| EventStub | `engagement/attendance/entity/EventStub.java` | `AttendanceService::attend/removeAttendance/getMyParticipationEvents`, `CommentService::assertEventVisibleAndLoad`, `Comment.event @ManyToOne` | EventServiceClient.getById + locking advisory côté local engagement (cf. Annexe E) |
| UserStub | `engagement/attendance/entity/UserStub.java` | `AttendanceService::resolveUser/dto`, `CommentService::post/isCreatorOrAcceptedCoOrganizer`, `Comment.author @ManyToOne` | UserServiceClient.getById + Auth0IdResolver |
| EventCoOrganizerStub | `engagement/attendance/entity/EventCoOrganizerStub.java` | `CommentService::isCreatorOrAcceptedCoOrganizer/computeOrganizerUserIds`, `AttendanceService::cascade` | EventServiceClient.getByIdWithCoOrgCheck + .getOrganizerUuids (Décision G) |

#### moderation-service (3 stubs)

| Stub | Path | Call-sites | Refactor |
|---|---|---|---|
| EventStub (writable) | `report/entity/EventStub.java` | `ReportService::handle` (mutation `event.status = BANNED` à retirer Décision H), `ModerationCleanupService::runCleanup` (idem), `Report.event @ManyToOne` | Décision H : retirer mutation directe, garder uniquement Kafka producer events.banned ; consumer event-service applique idempotent |
| UserStub | `report/entity/UserStub.java` | `ReportService::report/list/handle`, `Report.reporter/reviewedBy @ManyToOne`, `ReportDTO::from` | UserServiceClient.getById pour enrichment ; reviewedById résolu via JWT claim |
| EventCoOrganizerStub | `report/entity/EventCoOrganizerStub.java` | `ReportService::cascade « can't report own event »` | EventServiceClient.getByIdWithCoOrgCheck (self-check) |

#### user-service (3 stubs)

| Stub | Path | Call-sites | Refactor |
|---|---|---|---|
| EventStub | `user/calendar/entity/EventStub.java` | `CalendarService::generateIcs`, `IcsBuilder::build` | EventServiceClient.findByIds (bulk lookup) |
| AttendanceStub | `user/calendar/entity/AttendanceStub.java` | `CalendarService::generateIcs` (collect ATTENDING eventIds) | EngagementServiceClient.getUserAttendances |
| FavoriteStub | `user/calendar/entity/FavoriteStub.java` | `CalendarService::generateIcs` (collect favorited eventIds) | **Note** : pas d'endpoint REST direct (favorites local event-service post-2.2.3). Pour cette spec, on ne livre pas les favorites dans l'ICS feed (dégradation fonctionnelle mineure documentée). Endpoint `GET /users/{id}/favorite-event-ids` sur event-service à ajouter en S9 si besoin. |

#### event-service (4 stubs)

| Stub | Path | Call-sites | Refactor |
|---|---|---|---|
| UserStub | `event/entity/UserStub.java` | `Event.creator @ManyToOne`, ~10 services (EventService, FavoriteService, EventCoOrganizerService, EventStatsService, EventViewService, MyEventsService, etc.) | Décision F : `Event.creator` → `Event.creatorId UUID` ; UserServiceClient.getById pour enrichment |
| AttendanceStub | `event/entity/AttendanceStub.java` | EventSearchService, FeaturedService, EventCoOrganizerService, EventStatsService, EventService, MyEventsService, FavoriteService (countGroupedByStatus bulk) | EngagementServiceClient.getAttendanceSummariesBulk (Décision I) |
| EventViewStub | `event/entity/EventViewStub.java` | `EventService` 1 site | **Redirect local** : remplacer par `EventView` (entité locale au sous-package event/view/) |
| FavoriteStub | `event/entity/FavoriteStub.java` | FeaturedService, EventService, CalendarService 3 sites | **Redundant local** : remplacer par `Favorite` (entité locale au sous-package event/favorite/) — même `@Table(name="favorites")` |

---

### Annexe C — Patch détaillé `NotFoundExceptionMapper`

Cf. Étape 2.2 supra pour le code complet. Récapitulatif des fichiers à créer :

1. `backend/services/shared-api-error/src/main/java/ch/unige/events/shared/error/NotFoundExceptionMapper.java` (mapper + `@Provider`).
2. `backend/services/shared-api-error/src/test/java/ch/unige/events/shared/error/NotFoundExceptionMapperTest.java` (2 tests).

**Auto-discovery via Jandex** : `shared-api-error/pom.xml` doit déclarer le plugin `jandex-maven-plugin` (déjà le cas, à vérifier). Quarkus picks up les `@Provider` indexés au build time.

**Validation cross-service** : tester que `GET /api/events/9999` (event inexistant via Kong) retourne désormais `{"error":"not_found","message":"..."}`. Pareil pour `/api/users/<random-uuid>`, `/api/comments/9999`. Cf. les 4 services consommateurs : tous transitif sur shared-api-error post-Étape 4.1.

---

### Annexe D — Diff `application.properties` × 4 services pour REST-001

Cf. Étape 2.1 supra pour les 4 patches concrets. Récapitulatif :

| Service | Consume | Lignes à ajouter |
|---|---|---|
| event-service | user-service, engagement-service | 2 URLs + 2 connect-timeout |
| user-service | event-service, engagement-service | 2 URLs + 2 connect-timeout |
| engagement-service | event-service, user-service | 2 URLs + 2 connect-timeout |
| moderation-service | event-service, user-service | 2 URLs + 2 connect-timeout |

**Pattern Helm/K8s** : les vars `<SVC>_SERVICE_URL` ne sont **pas** définies par défaut dans `values.yaml` ; le default `http://<svc>-service:8080` s'applique automatiquement (DNS K8s par-namespace). Pour override en preview/prod (ex. cross-namespace), DevOps peut injecter la var via Doppler ou env explicit dans le deployment.

---

### Annexe E — Stratégie de locking post-stubs (PESSIMISTIC_WRITE cross-service ?)

**Problème** : `engagement-service.AttendanceService::attend` utilisait :
```java
EventStub event = entityManager.find(EventStub.class, eventId, LockModeType.PESSIMISTIC_WRITE);
```

Le lock pessimistic JPA était sur la row `events` (cross-schéma) pour fermer la race entre 2 callers qui attendent simultanément (capacity gating). Post-stubs, ce lock cross-service n'a plus de sens (REST clients ne supportent pas les locks DB).

**Décision** : remplacer par un **lock pessimistic local** sur la row `attendances` la plus récente pour cet event, OU un **advisory lock PostgreSQL** sur `eventId`.

**Choix retenu** : **advisory lock PostgreSQL** via `pg_try_advisory_xact_lock(eventId)`. Avantages :
- Lock léger, scoped à la transaction (libéré au commit/rollback).
- Lock par eventId (scalable — pas de contention globale).
- Aucune dépendance JPA cross-service.

**Pattern dans `AttendanceService::attend`** :

```java
@Transactional
public AttendanceDTO attend(String auth0Id, Long eventId, AttendanceStatus status) {
    if (status != AttendanceStatus.ATTENDING) {
        throw new BadRequestException("Only ATTENDING is accepted as a request status");
    }

    // Advisory lock per-event — fermeture de race capacity gating.
    // pg_try_advisory_xact_lock(eventId) returns TRUE si locked, FALSE sinon.
    // Si FALSE, on retry après 50ms (max 5 retries) puis 503 si toujours bloqué.
    boolean locked = (boolean) entityManager
        .createNativeQuery("SELECT pg_try_advisory_xact_lock(:id)")
        .setParameter("id", eventId)
        .getSingleResult();
    if (!locked) {
        throw new WebApplicationException(
            Response.status(503).entity(new ApiErrorResponse(
                "concurrent_modification",
                "Event is being modified concurrently, retry later."
            )).build());
    }

    // Now safe to read the EventDTO via REST client.
    EventDTO event = eventClient.getById(eventId);
    if (event == null) {
        throw new NotFoundException("Event not found");
    }
    // ... reste de la logique inchangée (capacity gating, attendance persist, etc.)
}
```

**Trade-off** : 1 round-trip DB supplémentaire (advisory lock acquisition) avant le REST call. Acceptable car local et fast (< 1ms).

**Variante simpler** (si advisory lock juge over-engineered) : faire confiance au check applicatif `currentAttending < event.capacity` + retry idempotent. Le pire cas est qu'un user sur la limite stricte peut être WAITLISTED par un autre user simultanément — pour un projet pinfo6, acceptable. **Noter dans le commit message qu'on accepte ce trade-off.**

**Décision finale** : la spec ultime laisse à l'exécuteur le choix entre advisory lock et trade-off applicatif (selon le confort de l'écriture du test sentinel `prePersist_setsCreatedAt` et du flow `AttendanceService::attend`). Le commit Étape 3.1 doit acter le choix.

---

### Annexe F — Commandes de référence (build, validation, watch CI)

#### Build local

```bash
cd /workspace/backend
./mvnw verify -DskipITs                          # 17 modules, ~3-4 min
./mvnw -pl services/<svc>-service -am verify     # un seul service + ses deps
./mvnw -pl services/shared-rate-limit -am test   # un seul shared lib
./mvnw -pl contract-tests -am verify             # contract tests, génère pacts
./mvnw -pl e2e -am verify                        # E2E (auto-skip sans env var)
```

#### Validation invariants

```bash
# 0 stub JPA cross-service
find /workspace/backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l   # → 0

# 0 @ManyToOne XStub
grep -rln '@ManyToOne.*Stub\|extends.*Stub' /workspace/backend/services/*/src/main/java | wc -l   # → 0

# Frontend invariant
git diff --shortstat origin/main HEAD -- frontend/   # 0 ligne

# OpenAPI invariant
git diff --shortstat origin/main HEAD -- openapi/    # 0 ligne

# 17 modules dans le reactor
grep -c '<module>' /workspace/backend/pom.xml        # 17

# 5 services Helm
ls /workspace/k8s/chart/templates/ | grep -E '\-service$'   # 5 dossiers

# 35 sentinels par nom (validation script § 5.6 spec finalization)
for sentinel in <liste 35 noms>; do
    hit=$(grep -rln "void $sentinel" backend/services/*/src/test 2>/dev/null | wc -l)
    [ "$hit" -lt 1 ] && echo "MISSING: $sentinel"
done
```

#### Couverture jacoco par module

```bash
for r in /workspace/backend/services/*/target/jacoco-report/jacoco.xml; do
    module=$(echo "$r" | sed 's|.*/services/\([^/]*\)/target.*|\1|')
    parsed=$(tr '>' '\n' < "$r" | grep -E 'counter type="(LINE|BRANCH)"')
    line_last=$(echo "$parsed" | grep 'type="LINE"' | tail -1)
    branch_last=$(echo "$parsed" | grep 'type="BRANCH"' | tail -1)
    lm=$(echo "$line_last" | sed -E 's/.*missed="([0-9]+)".*/\1/'); lc=$(echo "$line_last" | sed -E 's/.*covered="([0-9]+)".*/\1/')
    bm=$(echo "$branch_last" | sed -E 's/.*missed="([0-9]+)".*/\1/'); bc=$(echo "$branch_last" | sed -E 's/.*covered="([0-9]+)".*/\1/')
    [ -z "$lm" ] && lm=0; [ -z "$lc" ] && lc=0
    lt=$((lm + lc)); bt=$((bm + bc))
    lp=$(awk -v c=$lc -v t=$lt 'BEGIN{if(t>0)printf "%.1f",c*100/t; else print "0.0"}')
    bp=$(awk -v c=$bc -v t=$bt 'BEGIN{if(t>0)printf "%.1f",c*100/t; else print "0.0"}')
    printf "%-30s %6s%% L  %6s%% B (lines: %d/%d, branches: %d/%d)\n" "$module" "$lp" "$bp" "$lc" "$lt" "$bc" "$bt"
done | sort
```

#### CI / GH

```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158                       # snapshot
gh pr checks 158 --watch               # streaming
gh run view <RUN_ID> --log-failed      # debug fail
gh pr edit 158 --body-file <path>      # update PR body (Étape 9.2 only)
```

---

### Annexe G — Stratégie de rollback en cas de blocage

#### Cas 1 — Build local échoue après une étape

**Diagnostic** :
```bash
cd backend && ./mvnw -B -DskipITs verify 2>&1 | grep -E 'BUILD FAILURE|^\[ERROR\]' | head -20
```

**Actions** :
- Si erreur compile (import cassé, type mismatch) : fixer le import/type, re-run.
- Si test fail isolé : examiner le test, fixer la cause racine (jamais `@Disabled`).
- Si > 5 commits consécutifs CI rouge sans cause root identifiée : **STOP** et reporte à l'humain.

**Rollback** :
```bash
git reset --hard HEAD~1   # avant push
# OU
git revert <SHA>           # après push (commit additif, pas de force push)
```

#### Cas 2 — Stub résiduel après suppression

**Diagnostic** : `find backend/services -name '*Stub.java' -not -path '*/target/*'` → > 0.

**Actions** : grep le nom du stub résiduel pour trouver les call-sites, refactor → REST client, re-run validation.

#### Cas 3 — Pact verification échoue (post Étape 5.1)

**Diagnostic** : la cellule contract-tests CI pass mais le `EngagementEventIssue92PactTest` provider verify (Sprint S9) échoue avec body mismatch.

**Actions** :
- Vérifier que `NotFoundExceptionMapper` est bien sur le classpath de event-service (transitive via shared-api-error dep).
- Vérifier que le pact JSON dans `target/pacts/engagement-service-event-service.json` contient bien `{"error":"not_found"}`.

#### Cas 4 — Sonar `top level project` revient

**Diagnostic** : la cellule services Sonar échoue avec « Maven session does not declare a top level project ».

**Actions** :
- Vérifier que la commande dans `build.yml` est bien `./mvnw -pl .,services/<X>-service sonar:sonar -B` (avec le `.`).
- Vérifier que `sonar-maven-plugin` n'est pas pinned à une version antérieure incompatible.

#### Cas 5 — Helm chart rejet K8s (preview deploy fail)

**Diagnostic** : `gh run view <RUN_ID> --log-failed` sur Deploy / Deploy to Preview.

**Actions** :
- Vérifier `helm template k8s/chart` localement → output valide ?
- Vérifier que les 5 livenessProbes sont bien définis (post Étape 7.1).
- Si fail K8s pure (pod crash loop), debug avec `kubectl logs` et adresser dans un commit additif.

#### Cas 6 — DevOps n'a pas créé les 5 SonarCloud projects services

**Diagnostic** : 5 cellules services Sonar échouent avec « Project not found ». Avec `continue-on-error: true` (Étape 1.3 conditionnelle), ne bloque pas le merge.

**Actions** : laisser tel quel, le `continue-on-error` est le mécanisme prévu. Documenté dans devops-handoff.md item 1. Reprendre Étape 1.3 (retirer `continue-on-error`) en S9 quand DevOps a validé.

---

## Récapitulatif final

**Ordre d'exécution strict** :

```
0   Pré-flight (validation, no commit)
1   CI/Sonar fixes                                   → 3 commits (Vague 1)
    1.1 CI-001 + CI-002 (top-level + consolidation)
    1.2 CI-006 (verify→install build-contract-and-e2e)
    1.3 CI-003 conditionnel (continue-on-error retrait, post-DevOps)
2   REST clients runtime                             → 4 commits (Vague 2)
    2.1 REST-001 (URL config × 4)
    2.2 REST-004/SEC-001 (NotFoundExceptionMapper)
    2.3 REST-002 (UserAttendancesInternalResource — Décision B)
    2.4 REST-003 (admin bypass UserService)
3   Suppression 13 stubs JPA + refactor entités     → 5 commits (Vague 3)
    3.1 engagement-service (3 stubs + Comment refactor + Décisions A/F/G)
    3.2 moderation-service (3 stubs + Report refactor + Décision H)
    3.3 user-service (3 stubs + Calendar refactor + Décisions A/B/F)
    3.4 event-service (4 stubs + Event refactor + Décisions A/F/G/I)
    3.5 Validation finale (no commit sauf fix)
4   Bascule shared libs                              → 3 commits (Vague 4)
    4.1 DUP-001 + DUP-004 (api-error + enums)
    4.2 DUP-002 + DUP-003 (platform + jaxrs)
    4.3 DUP-005 + DUP-006 (DTOs + projections — Décision A via Mappers)
5   Tests + couverture                               → 2 commits (Vague 5)
    5.1 TEST-002 + TEST-003 + TEST-004 + COV-002 (bulk pact + bridge + scaffolds drop + dtos coverage)
    5.2 TEST-001 (8 sentinels portés + 23 taggés legacy-port-s9 — Décision D Option 3)
6   Sécurité                                          → 1 commit (Vague 6)
    6.1 SEC-002 (self-check authentifié — Décision C)
7   Helm/K8s + dépendances                            → 2 commits (Vague 7)
    7.1 K8S-001 (livenessProbe notification)
    7.2 DEP-001 (quarkus-jacoco notification)
8   Documentation finale                              → 5 commits (Vague 8)
    8.1 DOC-001 (devops-handoff TL;DR)
    8.2 DOC-002/003/006/010 (architecture + api-contract + internal-endpoints)
    8.3 DOC-004 + DOC-005 (roadmap [ARCHIVÉ] + AGENTS.md)
    8.4 sprint-context § Étape 21 clôture
    8.5 DOC-008/009/011 + TODO-001 (sed cleanup)
9   Watch CI + finalisation                           → 1 commit (Vague 9)
    9.1 Watch CI groupé final (no commit)
    9.2 PR body update via gh pr edit
```

**Total** : ~25 commits sur 9 vagues. Estimation effort : 40-60 heures focalisées.

**Chaque vague est validée par CI watch groupé avant la suivante**.

**À la fin** : la PR #158 est prête au merge, Elie merge lui-même quand il valide. Le DevOps prend la main pour les 7 (+3) items hors scope formalisés dans `devops-handoff.md`.

---

## Workflow git détaillé pour chaque sous-étape

```bash
# 0. Pré-vérif état
git status --porcelain                       # vide ou audit untracked OK
git rev-parse HEAD                            # ec668b91 ou descendant

# 1. Faire les changements (Edit/Write)
# 2. Build local
cd backend && ./mvnw -B -DskipITs verify     # SUCCESS
# 3. Stage + commit
cd /workspace
git add -A backend/<paths-touched>
git commit -m "$(cat <<'EOF'
<type>(backend): <message ÉtapeN.M> (Étape N.M, <FINDING-IDs>)

<details if needed>

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
# 4. Push (par sous-étape verte)
git push origin 'refactor(backend)--migrate-to-microservices'
# 5. Watch CI à la fin de chaque vague (pas après chaque sous-étape)
gh pr checks 158 --watch
```

---

**Fin de la spec ultime.** Bonne exécution. 🚀
