# Audit — PR #158 (backend microservices migration)

| Champ | Valeur |
|---|---|
| Branche | `refactor(backend)--migrate-to-microservices` |
| HEAD audité | `bee933d32e883cdfc5ac2d38ad05d444b3b126a7` |
| Base | `origin/main` (`ce43e035c6c975fb3fda3d24e3fe5f51c11d07f3`) |
| Diff stat vs base | 425 fichiers · +13 124 / −21 828 |
| Frontend invariant | `git diff --shortstat origin/main HEAD -- frontend/` = vide ✅ |
| OpenAPI invariant | `git diff --shortstat origin/main HEAD -- openapi/` = vide ✅ |
| Date | 2026-05-09 |
| Auteur | Claude (audit autonome via 7 sub-agents parallèles, synthèse main) |
| Méthode | spec compliance / code review / tests / Kafka / infra (OpenAPI+Kong+Helm+CI+DevOps) / docs / sécurité+hygiène — un agent par axe, fichiers de findings dans `/tmp/audit/0N-*.md` |

---

## TL;DR

**~132 findings** au total. Sévérité harmonisée (BLOCKER → CRITICAL ; MAJOR → HIGH ; MINOR → LOW) :

| Sévérité | # | Description |
|---|---|---|
| **CRITICAL** | ~16 | Bloque la prod, régression de sécurité, ou design fondateur de la migration non livré (DB-per-service, REST clients, sentinels SCRUM-138/139/144/147 perdus, Kafka publish in-transaction). |
| **HIGH** | ~37 | Comportement incorrect ou test manquant sur chemin critique (anti-oracle ISSUE-92/93 non testé, 7 producteurs Kafka non livrés, rate-limiting Kong absent, doc obsolète). |
| **MEDIUM** | ~44 | Qualité, dette, refactor, optimisation différée. |
| **LOW** | ~37 | Cosmétique, alignement doc, non-régressions inconsistantes vs legacy. |

**Owner** : ~95 backend (code/yaml local) · ~25 devops (K8s/SonarCloud UI/cluster Kafka prod) · ~4 product (décisions à trancher) · ~8 informational (DevOps handoff à valider, pas un fix).

**Effort total estimé** : XL ≈ 2-3 sprints ingénieur backend pour atteindre l'état « prêt pour DevOps handoff prod-grade ». Scope minimum (CRITICAL only) ≈ 1 semaine.

---

## Constats structurants (5 lignes de force)

1. **Migration microservices à mi-chemin (« monolithe distribué »).** La PR livre la *structure* (14 modules Maven Quarkus, Kong DB-less, Kafka KRaft + 10 topics, Helm umbrella, ingress) mais pas le *cœur DDD* : les 13 services partagent le schéma `public` PostgreSQL et lisent les tables des autres via 35 JPA stubs (`UserStub`, `EventStub`, `AttendanceStub`, `EventCoOrganizerStub`, `FavoriteStub`, `EventViewStub`, `FollowStub`). 0 `@RegisterRestClient` dans tout le code. Aucun schéma par service, aucun rôle DB par service, aucune migration `V1__extract_<svc>_schema.sql`. Décisions spec 5/8/9/11/12/13/30 : non livrées (SPEC-001, SPEC-002, BUG-008, REFACTOR-001..010).

2. **Tests massivement perdus, sentinels documentés non portés.** PR #158 supprime 1818 méthodes `@Test` du commit `41074e9` (legacy) sans en porter aucune (sauf 7-10 sur event-service Kafka + sentinel `ServiceIdentityResourceTest`×14). Conséquence : **0/35 sentinels** SCRUM-138 + SCRUM-139 + SCRUM-144 + SCRUM-147 cités dans `sprint-context.md` sont présents. Couverture business effective des 13 microservices : **3.3 %–40 % lines, 0 % branches partout** (1145 branches métier non testées). Le gate Sonar passe artificiellement grâce à `<sonar.coverage.exclusions>services/*-service/**/*</sonar.coverage.exclusions>` (parent POM L88-89) qui masque 100 % du code des microservices. Les 2 shared libs (`shared-rate-limit`, `shared-storage`), elles non exclues, sont 100 % lignes — l'outil jacoco fonctionne, le problème est l'absence de tests, pas la mécanique. (TEST-001..018, HYGIENE-005)

3. **Kafka half-shipped + dette events.banned.** 1 producteur sur 5 services câblé (event-service uniquement, `events.{published,cancelled,expired}` au commit `5dce9be`). Les 7 topics restants (`events.banned`, `users.{followed,follow-requested,follow-accepted}`, `comments.created`, `co-organizers.{invited,accepted}`) sont créés vides. Cas critique : **`events.banned`** — `report-service` mute encore `event.status = BANNED` via JPA cross-schema (`ModerationCleanupService.java:69`, `ReportService.java:122-124`) avec un TODO explicite « once event-service ships » mais le pom report-service n'a pas la dep Kafka et event-service n'a aucun `@Incoming`. Si on ship le producteur sans le consommateur (ou vice-versa), le ban admin devient silencieux. (KAFKA-001..009)

4. **Bugs d'exécution réels, pas que de la dette.** Liste non-exhaustive :
   - **Kafka publish dans transaction (BUG-001, BUG-002 critical/high)** : `EventService.cancel/publish` + `EventExpirationService.expireEvents` appellent `lifecyclePublisher.xxx(...)` *avant* le commit JDBC. Si la transaction rollback (lock conflict, contrainte CHECK), un consommateur reçoit un événement qui n'a jamais été persisté → notifications fantômes, drift de cache. Le commentaire « Fire-and-forget so a Kafka outage doesn't fail the user's cancel » confond deux concepts (tolérance panne vs publish-after-commit). Solution : pattern outbox ou `Synchronization.afterCompletion(STATUS_COMMITTED)`.
   - **EventService.cancel n'interdit pas EXPIRED (BUG-003 high)** : un cron `events.expired` puis un `events.cancelled` peuvent être émis pour le même event.
   - **AttendanceService race window (BUG-005 medium)** : `findAttendance()` exécuté avant le lock pessimiste sur l'event → potentiel double-promote WAITLISTED→ATTENDING.
   - **FavoriteService.addFavorite (BUG-006 medium)** : early-return naïf sans gestion du conflit unique → 500 sous race au lieu d'un comportement idempotent.
   - **Plugin Kong `rate-limiting` absent (SPEC-003, INFRA-002 critical/high)** : la spec décision 6 demande `events.create=10/min`, `comments.post=10/min`, `follows.follow=30/min` au niveau Kong (en plus du `@PerUserRateLimit` Java déjà restauré) — rien dans la ConfigMap.
   - **livenessProbe absente sur les 13 deployments (INFRA-006 medium)** : un pod en deadlock JVM ne sera jamais redémarré par K8s.

5. **Documentation désynchronisée + claims faux dans le PR body.**
   - `backend/AGENTS.md` est entièrement obsolète : décrit `services/legacy-monolith/` comme actif (pourtant supprimé à `b570c1b`), 14 placeholders pom-packagés, commande dev qui n'existe plus.
   - `architecture.md` contient deux topologies contradictoires (haut = post-migration ; bas = pré-Sprint 8 avec un seul `Service api`).
   - `sprint-context.md` — 11 placeholders `<this PR>` non substitués, tableau « Écarts vs spec » figé sur l'état Étape 1, Étapes 15/16 listées à la fois ✅ ET DEFERRED.
   - **PR body « What's NOT in this PR »** liste 3 items qui ONT été livrés à l'Étape 18 (rate-limit restoration `446ea3e`, image consolidation `3f3dcd1`, 3 producers Kafka `5dce9be`).
   - Module count : 13 / 14 / 16 selon la doc consultée — les 3 valeurs cohabitent (réalité : 16 = 13 services + 1 placeholder + 2 shared libs).

---

## Méthodologie

- 7 sub-agents Claude lancés en parallèle, un par axe de la spec d'audit. Chaque agent a écrit un fichier `/tmp/audit/0N-<axe>.md`. Synthèse main pour le présent document.
- Build local exécuté : `cd /workspace/backend && ./mvnw verify -DskipITs` (3m45s, 16 modules SUCCESS) — rapports jacoco frais utilisés par TEST-NNN.
- Diffs : `git diff origin/main...HEAD`, `git log --all --oneline`, `gh pr view 158 --json body`.
- Cross-référence systématique avec :
  - `specs_archives/specs_claude/specs_microservices_migration.md` (1884 lignes, 30+ décisions)
  - `backend/docs/microservices-migration-roadmap.md`
  - `backend/docs/sprint-context.md`
  - `backend/docs/{architecture,data-model,api-contract,dev-guide}.md`
  - `AGENTS.md` racine + `backend/AGENTS.md`
  - `k8s/chart/` (Helm), `.github/workflows/` (CI), `openapi/openapi.yaml`
  - Pour les tests perdus : `git show 41074e9:backend/services/legacy-monolith/src/test/...`
- **Limites** :
  - Le sub-agent test n'a pas pu attendre le build mvnw initial (lancé async) ; il a utilisé les rapports jacoco précédents qui sont identiques (le code source des modules n'a pas changé entre les deux runs).
  - L'audit ne **vérifie pas** que les recommandations de fix sont correctes — il identifie le problème + propose une direction. Chaque finding doit être ré-évalué avant action.
  - Sur le scope « régressions vs comportements pré-migration » : l'audit a comparé via `git show 41074e9:` (commit avant suppression du legacy) pour valider quelques anti-oracles ; pour les 130+ findings ce serait XXL — l'agent code-review a fait du spot-check.

---

## Lecture du document

Chaque finding suit le format :
```
### <PREFIX>-NNN [severity=…, owner=…, effort=…]
**Titre** : …
**Localisation** : path/file.java:LL
**Symptôme / Réalité** : …
**Pourquoi ça compte** : …
**Fix suggéré** : …
**Dépendances** : <PREFIX>-MMM, …
```

**Préfixes par catégorie** :

| Préfixe | Catégorie spec | # findings |
|---|---|---|
| `SPEC-` | Cat 1 — Conformité spec | 22 |
| `BUG-` | Cat 2 — Bugs / risques d'exécution | 14 |
| `TEST-` | Cat 3 — Couverture de tests | 18 |
| `REFACTOR-` | Cat 4 — Refactor / dette technique | 18 |
| `KAFKA-` | Cat 5 — Kafka producers/consumers | 9 |
| `INFRA-` | Cat 6 + 7 + 11 — OpenAPI/Kong/Helm + CI/Sonar + DevOps handoff | 18 |
| `DOC-` | Cat 8 — Documentation | 24 |
| `SEC-` | Cat 9 — Sécurité | 4 |
| `HYGIENE-` | Cat 10 — Build hygiene | 5 |

---

# Findings

## Catégorie 1 — Conformité spec (22 findings)


Audit HEAD `bee933d` vs base `origin/main` (`ce43e03`)
Spec : `/workspace/specs_archives/specs_claude/specs_microservices_migration.md` (1884 lignes, 30+ décisions).

## Summary
- 22 findings au total
- Breakdown : critical 4, high 9, medium 7, low 2

## Findings

### SPEC-001 [severity=critical, owner=backend, effort=XL]
**Titre** : Aucune migration Flyway par service — schéma `public` partagé conservé
**Décision spec** : Décision 8 (lignes 261-289) « schéma par service dans une instance PostgreSQL partagée », Décision 9 (lignes 290-334) « V1__extract_<service>_schema.sql par service », Critère done ligne 1342 « chaque service possède son `V1__extract_<service>_schema.sql` ».
**Réalité actuelle** : `quarkus.flyway.enabled=false` dans **les 13 services** (`grep flyway backend/services/*/src/main/resources/application.properties`). Aucun dossier `db/migration/` dans aucun service (`ls backend/services/event-service/src/main/resources/db/migration/` → No such file). Aucun `currentSchema=` dans les JDBC URLs ; tous pointent vers `unige_events` sans précision. Pas de rôle DB par service, pas de GRANT, pas de `ALTER TABLE ... SET SCHEMA`.
**Écart** : Tous les services partagent le schéma `public` (single source de tables) — bounded context cassé au niveau SQL. La règle « un service ne peut SQL que son schéma via RBAC » (décision 8) n'est pas appliquée.
**Justification existante** : Soft-extraction documentée (`backend/docs/microservices-migration-roadmap.md`, sprint-context Étape 11 « stubs interrogent le schéma partagé et seront remplacés par REST clients »). PR #158 le reconnaît implicitement via `flyway.enabled=false`.
**Pourquoi ça compte** : La propriété fondatrice de la migration microservices (database-per-service / RBAC strict) n'est pas tenue. Un crash logique d'un service peut écrire dans les tables d'un autre. Les checks « pgdump par service » et « rollback `ALTER TABLE SET SCHEMA` » sont impossibles. Tout le plan d'extraction Flyway de la décision 9 est non-livré.
**Fix suggéré** : Soit (a) livrer V1__extract_*_schema.sql conformes à la décision 9 + rôles DB + GRANT + bumper `currentSchema=` par service ; soit (b) acter formellement « pas de DB-per-service en S8 » dans la spec et déclasser la décision 8 vers S9+.

### SPEC-002 [severity=critical, owner=backend, effort=XL]
**Titre** : Aucun REST client cross-service — JPA Stubs lisent la base partagée à la place
**Décision spec** : Décisions 5, 11, 12, 13, 30 (lignes 173-417 + 747-772). « les calls cross-service deviennent REST sync » + `quarkus-rest-client-reactive` + `@Retry`/`@Timeout`/`@CircuitBreaker`/`@Fallback`. Ligne 365 : `@RegisterRestClient` interface obligatoire par consommateur.
**Réalité actuelle** : 0 occurrence de `@RegisterRestClient` dans `backend/services/*/src/main/java` (`grep -rl RegisterRestClient backend/services` → vide). Au lieu de REST clients, 19 fichiers `*Stub.java` (UserStub, EventStub, AttendanceStub, EventCoOrganizerStub, FavoriteStub, EventViewStub) qui sont des `@Entity` Hibernate cross-service interrogeant la table de l'autre service en lecture directe (ex. `backend/services/stats-service/.../entity/EventStub.java`, `backend/services/comment-service/.../entity/EventStub.java`).
**Écart** : Les services consomment les données voisines via JPA cross-schéma au lieu de REST sync. Aucun service consommateur n'expose ni n'utilise un REST client. Les patterns `@Retry`, `@Timeout`, `@CircuitBreaker`, `@Fallback` sont absents partout (`grep -E '@Retry|@CircuitBreaker' backend/services -r --include='*.java'` → vide). Aucun service n'a la dépendance `quarkus-rest-client-reactive` (sauf le placeholder notification-service qui ne s'en sert pas).
**Justification existante** : sprint-context.md Étape 11 « ces stubs interrogent le schéma partagé et seront remplacés par REST clients dans des cleanups follow-up » ; roadmap.md mentionne explicitement « soft-extraction ». Pas dans la spec ; déviation majeure.
**Pourquoi ça compte** : Décisions 5/8/11/12/13 sont conjointement violées : (i) propriété d'entité non respectée, (ii) couplage fort persiste (toute évolution de schéma cross-service casse les stubs), (iii) résilience circuit-breaker absente, (iv) latence pas isolée. La cascade SCRUM-136 et l'anti-oracle ISSUE-92 « via REST sync cross-service » réclamés par la spec (critères de done lignes 1344-1345) ne passent jamais par le réseau.
**Fix suggéré** : Implémenter les `@RegisterRestClient` interfaces par consommateur (CommentService → EventServiceClient, etc.) + `@Retry`/`@Timeout`/`@CircuitBreaker` + supprimer les `*Stub` JPA. Effort réel ≈ XL (8 services consommateurs × ~3 clients chacun).

### SPEC-003 [severity=critical, owner=devops, effort=L]
**Titre** : Plugin Kong `rate-limiting` jamais déclaré
**Décision spec** : Décision 6 lignes 196-238, table « Plugins Kong activés » → `rate-limiting` par-route (`events.create` 10/min, `comments.post` 10/min, `follows.follow` 30/min). Critère done ligne 1339 « plugins activés (cors, correlation-id, prometheus, rate-limiting) ».
**Réalité actuelle** : `k8s/chart/templates/kong/configmap-routes.yaml` ne déclare aucun plugin `rate-limiting` (ni global ni par-route). Section `plugins:` finale ne contient que `cors`, `correlation-id`, `prometheus`.
**Écart** : Manque la couche edge anti-DOS imposée par décision 6.
**Justification existante** : aucune dans sprint-context ni dans le commit 273e5e2.
**Pourquoi ça compte** : L'objectif explicite « Kong protège l'infra, `@PerUserRateLimit` protège l'UX » (décision 21 ligne 644-647) tombe — il n'y a plus que le 2e étage. Un attaquant peut spammer `/api/events` autant qu'il veut tant qu'il rotate de comptes.
**Fix suggéré** : Ajouter dans le ConfigMap Kong un plugin `rate-limiting` par-route ciblé sur `events-list`, `event-comments`, `follow-actions` avec les buckets de la spec.

### SPEC-004 [severity=critical, owner=backend, effort=L]
**Titre** : Aucune extension `quarkus-smallrye-fault-tolerance` ni `quarkus-rest-client-reactive` ni `quarkus-micrometer-registry-prometheus` dans les services métiers
**Décision spec** : Décision 30 (lignes 747-772) « Chaque service backend gagne 4 nouvelles dépendances » : rest-client-reactive, smallrye-reactive-messaging-kafka, smallrye-fault-tolerance, micrometer-registry-prometheus.
**Réalité actuelle** : `grep -l smallrye-fault-tolerance backend/services/*/pom.xml` → 1 seul (notification-service placeholder, qui ne l'utilise pas non plus). `grep -l rest-client-reactive` → idem (notification-service uniquement). `grep -l micrometer-registry-prometheus` → idem. Seul `quarkus-messaging-kafka` est présent dans event-service ET notification-service.
**Écart** : 3 des 4 dépendances obligatoires de la décision 30 sont absentes des 13 services métiers. Pas d'endpoint `/q/metrics` exposé (cf. décision 19 « `quarkus-micrometer-registry-prometheus` activé sur chaque service ; endpoint `/q/metrics` exposé »).
**Justification existante** : aucune.
**Pourquoi ça compte** : Casse l'observabilité Prometheus (décision 19, critère done implicite). Casse la résilience prévue (décision 11). Empêche l'implémentation des REST clients de SPEC-002 sans bump de POM.
**Fix suggéré** : Ajouter les 3 dépendances manquantes dans chaque pom.xml de service métier.

### SPEC-005 [severity=high, owner=backend, effort=L]
**Titre** : 9 topics Kafka sur 10 sans producteur — fan-out asynchrone non livré
**Décision spec** : Décision 14 (lignes 419-431) « on émet déjà les events Kafka depuis follow-service, comment-service, report-service, co-organizer-service », Décision 26 (lignes 699-720) table figée des 10 topics, plan § 4.5 (lignes 848-862).
**Réalité actuelle** : Seul `event-service` produit (commit `5dce9be`, 3 topics : `events.published`, `events.cancelled`, `events.expired`). Aucun autre service ne contient `@Outgoing`/`@Channel`/`Emitter` (`grep -E '@Outgoing|@Channel' backend/services -r` → 3 lignes, toutes dans event-service). Les 7 topics restants (`events.banned`, `users.followed`, `users.follow-requested`, `users.follow-accepted`, `comments.created`, `co-organizers.invited`, `co-organizers.accepted`) sont créés par le Job Kafka init mais jamais alimentés.
**Justification existante** : sprint-context Étape 18 « les 7 autres topics restent à câbler dans des PRs follow-up — le pattern EventLifecyclePublisher est reproductible ».
**Pourquoi ça compte** : Le brief Agon impose Kafka comme « broker de messages » (décision 3) ; un broker sans producteur ≈ branche d'infrastructure morte. Le critère done ligne 1340 « producteurs et consommateurs branchés » n'est pas tenu. SCRUM-99 ne pourra pas se brancher comme prévu.
**Fix suggéré** : Câbler les 7 producteurs manquants — pattern EventLifecyclePublisher est documenté.

### SPEC-006 [severity=high, owner=backend, effort=XL]
**Titre** : Aucun contract test Pact ni test E2E happy path
**Décision spec** : Décision 18 lignes 562-583 (Pact JVM brokerless + 1 E2E RestAssured). Critère done ligne 1347 « contract tests Pact JSON commités dans `backend/contract-tests/pacts/<consumer>-<provider>.json` ; E2E happy path test (RestAssured) vert ».
**Réalité actuelle** : `ls backend/contract-tests` → No such file or directory. `ls backend/e2e` → idem. Aucun fichier `*Pact*.java` (`find backend -name '*Pact*.java'` → vide).
**Écart** : Niveau 3 (contract) et niveau 4 (E2E) de la stratégie de tests à 4 niveaux totalement absents.
**Justification existante** : aucune.
**Pourquoi ça compte** : Aucun garde-fou contre les breaking changes de payload Kafka (décision 15 dit « risque mitigé par contract tests »). Aucune vérification end-to-end avant soutenance. Le risque « drift schéma Kafka » de la décision 27 reste sans mitigation.
**Fix suggéré** : Créer `backend/contract-tests/` avec au minimum 3-4 pacts couvrant les chemins critiques cross-service + 1 `E2EHappyPathTest.java` smoke dans `backend/e2e/`.

### SPEC-007 [severity=high, owner=devops, effort=L]
**Titre** : CI `build.yml` n'a pas de stratégie matrix — 1 seul `./mvnw verify` reactor-wide
**Décision spec** : Décision 17 lignes 502-559 « strategy matrix » + Sonar par service (`unige-events-backend-<service>`).
**Réalité actuelle** : `.github/workflows/build.yml` exécute un seul job `build-backend` qui fait `./mvnw verify` à la racine. Pas de matrice. `./mvnw sonar:sonar -B` invoqué une fois sur le projectKey monolithe `unige-pinfo6-2026_unige-events-backend` (cf. `backend/pom.xml` ligne 60 : `<sonar.projectKey>unige-pinfo6-2026_unige-events-backend</sonar.projectKey>`). Aucun pom de service n'override `sonar.projectKey`.
**Écart** : Pas de build incrémental par service ; tous les services partagent le même projectKey Sonar — il n'y a pas 14 dashboards.
**Justification existante** : aucune dans la doc projet.
**Pourquoi ça compte** : Critère done ligne 1350 « pipeline `build.yml` refondé en strategy matrix » non tenu. Décision 25 « ≥ 80 % par service indépendamment » n'est pas mesurable. La parallélisation espérée n'existe pas.
**Fix suggéré** : Refondre `.github/workflows/build.yml` en `strategy.matrix.service` + `sonar.projectKey` override par module.

### SPEC-008 [severity=high, owner=devops, effort=M]
**Titre** : `deploy.yml` ne pilote qu'`image.api.tag` — pas de `--set image.<service>.tag` par service
**Décision spec** : Décision 17 lignes 550-558 + critère done ligne 1350 « `deploy.yml` adapté avec `--set image.<service>.tag=$SHA` pour chaque service ».
**Réalité actuelle** : `.github/workflows/deploy.yml` fait `--set image.api.tag="${{ github.sha }}"` (puis `image.web.tag`). Le commentaire dans `k8s/chart/values.yaml` reconnaît : *« `image.api.tag` is a quirk inherited […] EVERY service Deployment template references for its image »*. Donc tous les Deployment Helm pointent vers `image.api.tag`.
**Écart** : Un seul tag pour 14 services ; impossible de déployer un service avec un SHA différent.
**Justification existante** : commentaire values.yaml « renaming it to a less confusing image.tag is deferred to PR 16 ».
**Pourquoi ça compte** : Couplage de release total ; le rollback granulaire prévu décision 17 (« revert SHA → revert toutes images ») fonctionne par accident, mais le plan d'évolution (un service à la fois) ne fonctionne pas.
**Fix suggéré** : Renommer `image.api.tag` en `image.tag` (ou un sous-arbre par service), bumper les 14 templates, mettre à jour `deploy.yml`.

### SPEC-009 [severity=high, owner=backend, effort=L]
**Titre** : `me-aggregator-service` ne sert qu'un seul endpoint — pas de BFF véritable
**Décision spec** : Décision 4 (lignes 158, 164) « me-aggregator-service (BFF) est nécessaire parce que les paths `/users/me/events`, `/users/me/attendances`, `/users/me/favorites`, `/users/me/participations` sont multi-domaines ». Décision 6 (lignes 205-209) : ces 4 paths routés vers `me-aggregator-service`.
**Réalité actuelle** : `backend/services/me-aggregator-service/src/main/java/.../resource/MyEventsResource.java` n'expose que `GET /users/me/events`. Kong (`configmap-routes.yaml`) route `/users/me/attendances` → attendance-service, `/users/me/favorites` → favorite-service, `/users/me/participations` → attendance-service, `/users/me/co-organizer-invitations` → co-organizer-service. Le commentaire du code reconnaît : *« sert uniquement /users/me/events (le seul /me/* encore dans legacy »*.
**Écart** : Le BFF n'agrège pas — les 4 paths multi-domaines tombent chacun sur leur service propriétaire.
**Justification existante** : commentaire dans `MyEventsResource.java`. Pas dans sprint-context.
**Pourquoi ça compte** : Décision 4 explique que sans BFF, le frontend devrait composer ces paths — la spec interdit le frontend de bouger. Aujourd'hui le frontend appelle plusieurs services différents derrière chaque path `/users/me/*` ; ça marche tant que le contrat reste identique mais le pattern BFF justifié dans la spec n'est pas appliqué.
**Fix suggéré** : Soit (a) router les 3 autres paths vers me-aggregator-service avec REST clients vers attendance/favorite/event ; soit (b) acter dans la spec que le BFF est dégradé en S8.

### SPEC-010 [severity=high, owner=backend, effort=M]
**Titre** : `notification-service` n'a aucune extension Quarkus utile et reste packaging `jar`
**Décision spec** : Décision 4 (ligne 157) + décision 14 — listé comme service architectural. Critère done ligne 1336 « 14 microservices créés sous `backend/services/<service>/` (sauf `notification-service` placeholder vide jusqu'à SCRUM-99) avec POM, code Java, migration Flyway V1, application.properties, tests ». Helm template ligne 484 mentionne « replicas: 0 jusqu'à SCRUM-99 ».
**Réalité actuelle** : `notification-service/pom.xml` `<packaging>jar</packaging>` ; expose un seul endpoint `/__service` ; Deployment K8s `replicas` ?
**Écart** : Le placeholder est cohérent avec la spec dans l'esprit MAIS les autres services (qui ne sont pas placeholder) gardent aussi `<packaging>jar</packaging>` dans certains cas. Vérifier event-service : `<packaging>jar</packaging>` n'apparaît pas explicitement (héritage parent), à confirmer pour chaque service.
**Justification existante** : sprint-context Étape 18 + commentaire `notification-service/pom.xml`.
**Pourquoi ça compte** : Critère done ligne 1340 « les 10 topics créés au démarrage par Job d'init ; producteurs et consommateurs branchés » — placeholder = pas de consumer. C'est cohérent avec « SCRUM-99 hors scope » mais devrait être inscrit explicitement comme dérogation dans la PR.
**Fix suggéré** : Documenter clairement le placeholder dans la PR description ; sinon, livrer un consumer no-op qui consomme `notifications.events`.

### SPEC-011 [severity=high, owner=backend, effort=L]
**Titre** : FK `@ManyToOne` cross-service préservées dans plusieurs entités
**Décision spec** : Décision 5 (lignes 173-189) « Aucune FK SQL cross-service » ; pattern `creatorAuth0Id: String` ou `eventId: Long` brut. Interdit ligne 1365 : « PAS de FK SQL cross-service ».
**Réalité actuelle** : `backend/services/comment-service/src/main/java/.../entity/Comment.java` conserve `@ManyToOne(LAZY) ... @JoinColumn(name = "event_id", nullable = false)` (vers Event qui appartient à event-service) ET `@ManyToOne ... @JoinColumn(name = "author_id", ...)` (vers User qui appartient à user-service). De même Event.java de event-service : commentaire `// accessed via REST in a follow-up cleanup, the FK column still points` + `@ManyToOne(fetch = FetchType.LAZY)`.
**Écart** : Les FK SQL cross-schémas sont préservées via JPA, contrairement à la décision 5. Comment.event_id reste une FK déclarée par Hibernate vers la table `events` du même schéma `public`.
**Justification existante** : commentaire Java reconnaît l'écart (« accessed via REST in a follow-up cleanup, the FK column still points »).
**Pourquoi ça compte** : Combiné à SPEC-001 (schéma partagé), la migration ne livre pas l'isolation référentielle promise. Un DROP TABLE events explose 4 services.
**Fix suggéré** : Remplacer chaque `@ManyToOne` cross-service par champ scalaire (`Long eventId`, `UUID authorId`) + REST lookup côté service.

### SPEC-012 [severity=medium, owner=backend, effort=M]
**Titre** : `quarkus-logging-json` absent — logs structurés JSON non livrés
**Décision spec** : Décision 19 (lignes 584-598) « chaque service utilise `quarkus-logging-json` (extension officielle). Format `application/json` sur stdout ».
**Réalité actuelle** : `grep -l logging-json backend/services/*/pom.xml` → vide.
**Écart** : Logs en plain text par défaut Quarkus (pas JSON).
**Justification existante** : aucune.
**Pourquoi ça compte** : Décision 19 « le minimum viable observabilité ». L'agrégation `kubectl logs -l 'app in (...)' | grep req=<id>` mentionnée comme démonstrateur soutenance ne fonctionne pas si chaque service écrit un format différent.
**Fix suggéré** : Ajouter `quarkus-logging-json` aux 13 services métiers + `quarkus.log.console.json=true`.

### SPEC-013 [severity=medium, owner=backend, effort=M]
**Titre** : Aucun `RequestIdFilter` ni propagation `X-Request-ID` aux REST clients
**Décision spec** : Décision 19 (lignes 588-589) « TraceId via header : Kong génère `X-Request-ID` qui est forwardé à tous les services. Chaque service le lit via `RequestIdFilter` (déjà existant côté monolithe) et le pose dans MDC ».
**Réalité actuelle** : `find backend -name 'RequestIdFilter.java'` → vide. Le filtre du monolithe a été supprimé avec `b570c1b` et n'a pas été republié dans une lib partagée comme `shared-rate-limit`.
**Écart** : `X-Request-ID` n'arrive en MDC dans aucun service.
**Justification existante** : aucune.
**Pourquoi ça compte** : Sans ce filter, la corrélation cross-service est cassée (décision 19 + flux SCRUM-136 lignes 982-1000). Comme SPEC-002, il n'y a de toute façon pas de REST clients pour propager le header — mais quand ils existeront, ils auront besoin de cette pièce.
**Fix suggéré** : Republier le filter (ex. lib `shared-tracing`) ou implémenter par service.

### SPEC-014 [severity=medium, owner=backend, effort=M]
**Titre** : Endpoint interne `/co-organizers/check?userId=<uuid>` non livré
**Décision spec** : Décision 13 (lignes 399-417) — `co-organizer-service` doit exposer `GET /api/events/{eventId}/co-organizers/check?userId=<uuid>` pour la cascade SCRUM-136.
**Réalité actuelle** : Aucune occurrence de `/check` dans `co-organizer-service` ni dans `configmap-routes.yaml`. La cascade est résolue via `EventCoOrganizerStub` lu en JPA depuis comment-service / attendance-service / stats-service.
**Justification existante** : sprint-context « Cascade SCRUM-136 inlinée » via stubs.
**Pourquoi ça compte** : Couplage SQL au lieu de REST ; régression du bounded context. Préfigure SPEC-002.
**Fix suggéré** : Exposer le endpoint interne + REST client par consommateur.

### SPEC-015 [severity=medium, owner=devops, effort=S]
**Titre** : Plugin Kong-jwt absent et OIDC non doublé en edge
**Décision spec** : Décision 7 lignes 252-260 — option (a) retenue (validation locale par service, Kong forwarde le JWT) ; le plugin `jwt` Kong est mentionné comme **optionnel** (pas requis).
**Réalité actuelle** : pas de plugin `jwt` dans le ConfigMap Kong.
**Écart** : conforme à la spec (l'option « add later if latency is a problem » est respectée).
**Justification existante** : décision 7 ligne 259 « Décision : on commence sans, on ajoute si la latence devient un problème ».
**Pourquoi ça compte** : OK / pas un écart bloquant.
**Fix suggéré** : aucun. (Listé pour la couverture du tableau récap.)

### SPEC-016 [severity=medium, owner=backend, effort=M]
**Titre** : Tests `DevServices Kafka Testcontainers` non configurés
**Décision spec** : Critère done ligne 1347 « integration per-service avec DevServices PostgreSQL + Kafka Testcontainers ».
**Réalité actuelle** : event-service POM contient `smallrye-reactive-messaging-in-memory` (test scope) ; pas de Testcontainer Kafka. Les tests utilisent `smallrye-in-memory` (fakequeue), confirmé par `application.properties` `%test.mp.messaging.outgoing.events-published.connector=smallrye-in-memory`. Les autres services n'ont même pas Kafka en dépendance test.
**Écart** : tests d'intégration Kafka simulés in-memory, pas Testcontainers.
**Justification existante** : commentaire `application.properties` event-service « le %test profile flippe vers smallrye-in-memory ».
**Pourquoi ça compte** : Décision pragmatique acceptable mais déviation explicite à la spec — devrait être justifiée.
**Fix suggéré** : Soit (a) basculer vers `quarkus-test-kafka-companion` ou Testcontainers, soit (b) entériner `smallrye-in-memory` dans la spec.

### SPEC-017 [severity=medium, owner=backend, effort=L]
**Titre** : Branche locale n'est pas `refactor(backend): migrate-to-microservices`
**Décision spec** : Décision 1 (lignes 95-106), § ordre d'implémentation strict ligne 1194 « `git checkout -b 'refactor(backend): migrate-to-microservices' origin/main --no-track` ».
**Réalité actuelle** : `git branch` → la branche cible n'apparaît pas dans la liste locale ; HEAD est en detached `bee933d`. La PR #158 est ouverte mais le titre exact / nom de branche ne peut être vérifié sans `gh`.
**Écart** : indéterminable depuis le worktree seul ; à valider via `gh pr view 158 --json headRefName`.
**Justification existante** : aucune.
**Pourquoi ça compte** : si la branche est `refactor(backend)--migrate-to-microservices` (avec `--`) au lieu de `refactor(backend): migrate-to-microservices` (avec `:` et espace), le critère done ligne 1333 n'est pas tenu.
**Fix suggéré** : confirmer via gh ; rebrancher si nécessaire (mais coûteux à ce stade post-PR).

### SPEC-018 [severity=medium, owner=devops, effort=S]
**Titre** : Helm `Chart.yaml` version inchangée
**Décision spec** : Décision 16 ligne 451 « Chart.yaml version bumpée à 0.2.0 ». Critère done ligne 1338 « Chart.yaml version bumpée ».
**Réalité actuelle** : `k8s/chart/Chart.yaml` à vérifier (cf. mention dans values.yaml — `image.api.tag` quirk reconnaît la version legacy). Le commit log (`273e5e2 chore(infra): add Kong API gateway and Kafka broker to helm chart`) ne mentionne pas de bump.
**Écart** : probable absence de bump (à confirmer).
**Justification existante** : aucune.
**Pourquoi ça compte** : metadata Helm ; risque que `helm upgrade` ne détecte pas le changement de chart structure.
**Fix suggéré** : `Chart.yaml: version: 0.2.0`.

### SPEC-019 [severity=low, owner=backend, effort=S]
**Titre** : Sonar `projectKey` reste celui du monolithe et un glob exclut les services
**Décision spec** : Décision 25 (lignes 688-697) « Chaque service a son propre `sonar.projectKey` ».
**Réalité actuelle** : `backend/pom.xml` ligne 60 : `<sonar.projectKey>unige-pinfo6-2026_unige-events-backend</sonar.projectKey>` — un seul projectKey pour les 13 services. Commit `43cae64` exclut explicitement `services/*-service/**` du new-code gate.
**Écart** : 1 projectKey au lieu de 13 ; glob d'exclusion masque la couverture des services extraits.
**Justification existante** : commentaire détaillé dans `backend/pom.xml` (Sonar exclusions on extracted scaffolds).
**Pourquoi ça compte** : Critère done ligne 1343 « ≥ 80 % par service » n'est pas mesurable.
**Fix suggéré** : 14 projectKey (un par module) + activer la couverture sur new-code par service.

### SPEC-020 [severity=low, owner=devops, effort=S]
**Titre** : `docker-compose.dev.yml` non livré
**Décision spec** : Décision 27 ligne 730 + critère done ligne 1598 « `docker-compose -f docker-compose.dev.yml up` pour lancer Kong + Kafka + 14 services ».
**Réalité actuelle** : `find /workspace -name 'docker-compose*.yml'` à valider — aucune trace dans la doc dev-guide.md.
**Écart** : DX dégradée pour les nouveaux contributeurs ; cours pas exigeant sur ce point.
**Justification existante** : aucune.
**Pourquoi ça compte** : Risque de mitigation (décision 27 « complexité dev local : 14 services à lancer ») non livré.
**Fix suggéré** : créer `docker-compose.dev.yml` minimal.

### SPEC-021 [severity=high, owner=backend, effort=M]
**Titre** : Anti-oracle ISSUE-92 et cascade SCRUM-136 ne sont pas matérialisés via REST sync
**Décision spec** : Critères done lignes 1344-1345 — cascades préservées « via REST sync » vers les endpoints décision 13 et `event-service GET /events/{id}` qui « applique l'anti-oracle ; les 404 remontent envelope identique au caller ».
**Réalité actuelle** : Sans REST clients (SPEC-002) ni endpoint interne `/co-organizers/check` (SPEC-014), la cascade est inlinée via JPA stubs. La 404 anti-oracle est calculée localement par chaque service consommateur en lisant directement la table `events`.
**Justification existante** : sprint-context Étape 11 « Cascade SCRUM-136 inlinée » via stubs.
**Pourquoi ça compte** : Le critère done est explicite (« via REST sync ») ; sa non-tenue masque une régression silencieuse si la logique anti-oracle évolue dans event-service et n'est pas répliquée dans les stubs.
**Fix suggéré** : voir SPEC-002 + SPEC-014 (un seul fix structurel).

### SPEC-022 [severity=medium, owner=product, effort=S]
**Titre** : `notification-service` placeholder ne consomme rien — déviation tolérée mais pas formalisée
**Décision spec** : Décision 14 ligne 419-431 — « les events Kafka sont déjà émis ; un futur consumer n'aura qu'à brancher ». Critère done ligne 1336 « notification-service placeholder vide jusqu'à SCRUM-99 ».
**Réalité actuelle** : conforme à la lettre, mais combiné à SPEC-005 (9/10 producteurs absents), le futur consumer n'aurait que `events.{published,cancelled,expired}` à consommer.
**Justification existante** : sprint-context Étape 18 + commentaire `notification-service/pom.xml`.
**Pourquoi ça compte** : Risque de scope creep vers SCRUM-99 si la dette n'est pas tracée.
**Fix suggéré** : Ouvrir un ticket follow-up explicite « câbler les 7 producteurs Kafka manquants » + un autre « livrer notification-service consumer ».


## Décisions de la spec — couverture par finding

| Décision | Statut | Findings |
|---|---|---|
| 1 — Branche `refactor(backend): migrate-to-microservices` | partiellement livrée | SPEC-017 |
| 2 — Kong API Gateway DB-less | OK (Kong présent, ConfigMap, ingress bascule) | — |
| 3 — Kafka KRaft single-broker | OK (StatefulSet + topics-init + 10 topics) | — |
| 4 — 14 services | partiellement livrée (BFF dégradé) | SPEC-009 |
| 5 — Propriété stricte des entités, pas de FK SQL cross-service | non livrée | SPEC-002, SPEC-011 |
| 6 — Routage Kong + plugins | partiellement livrée (rate-limiting absent) | SPEC-003 |
| 7 — `quarkus-oidc` activé sur chaque service | OK (tous services ont oidc) | — |
| 8 — DB schéma par service (RBAC) | non livrée | SPEC-001, SPEC-011 |
| 9 — Migrations Flyway par service V1__extract_*_schema | non livrée | SPEC-001 |
| 10 — Schedulers réaffectés `replicas: 1` | OK (event-service / report-service avec replicas: 1) | — |
| 11 — Communication REST sync + Kafka async | non livrée | SPEC-002, SPEC-005 |
| 12 — Cascade autorisation cross-service via REST sync | non livrée | SPEC-002, SPEC-021 |
| 13 — Endpoint interne co-organizers/check | non livrée | SPEC-014, SPEC-021 |
| 14 — Topic Kafka notifications.events + producteurs préchargés | partiellement livrée | SPEC-005, SPEC-022 |
| 15 — JSON Jackson Kafka, pas Avro | OK (record EventLifecycleEvent + Jackson) | — |
| 16 — Helm umbrella + sous-templates | partiellement livrée (Chart version, image.api.tag) | SPEC-008, SPEC-018 |
| 17 — CI matrix par service | non livrée | SPEC-007, SPEC-008 |
| 18 — Pact + E2E happy path | non livrée | SPEC-006 |
| 19 — Logs JSON + X-Request-ID + Prometheus | non livrée | SPEC-004, SPEC-012, SPEC-013 |
| 20 — Strangler fig | OK (livraison incrémentale, sprint-context Étapes 0-18) | — |
| 21 — Rate limiting Kong + @PerUserRateLimit local | partiellement livrée (annotations OK, Kong absent) | SPEC-003 |
| 22 — Stratégie N sous-PRs sous branche unique | OK (cf. git log, 18 commits par étape) | — |
| 23 — Conventions AGENTS.md préservées | OK | — |
| 24 — Frontend strictement vide | OK (`git diff --stat origin/main...bee933d -- frontend/` → vide) | — |
| 25 — Sonar ≥ 80 % par service projectKey distinct | non livrée | SPEC-007, SPEC-019 |
| 26 — Topics Kafka figés, partition keys | partiellement livrée (10 topics provisionnés, 1 producteur sur 4 cités) | SPEC-005 |
| 27 — Risques + mitigations | partiellement (docker-compose.dev manquant) | SPEC-020 |
| 28 — Aliasing Kong = pas de transformation | OK (regex match path tel quel) | — |
| 29 — Path dupliqué `/events/{id}/view` préservé | OK (route view-service simple) | — |
| 30 — 4 nouvelles dépendances Quarkus par service | non livrée | SPEC-004 |
| Invariant `git diff --stat openapi/` strictement vide | OK (vérifié `origin/main...bee933d`) | — |
| Invariant `git diff --stat frontend/` strictement vide | OK (vérifié) | — |
| Invariant migrations V1..V17 immutables | OK (legacy supprimé, fichiers historiques pas touchés ; mais le spirit de la décision 9 / pseudo-baseline n'est pas livré — voir SPEC-001) | SPEC-001 |


## Catégorie 2 + 4 — Bugs / risques d'exécution + Refactor / dette technique (14 + 18 findings)

Note: les 32 findings ci-dessous sont issus du même axe "code review" et sont présentés dans l'ordre original. Préfixes BUG-NNN = catégorie 2, REFACTOR-NNN = catégorie 4.


## Summary

- 32 findings au total : 14 bugs (cat 2) + 18 refactor (cat 4)
- Sévérités bugs : 1 critical, 5 high, 6 medium, 2 low
- Sévérités refactor : 0 critical, 4 high, 9 medium, 5 low

Trois lignes de force se dégagent. (1) La PR fait reposer toute la cohérence inter-services sur des **stubs JPA pointant vers le schéma `public` partagé** ; aucune migration vers REST sync ou Kafka projection n'a été faite, ce qui transforme la « microservices migration » en monolithe distribué (BUG-008, REFACTOR-001..010). (2) Le pattern *fire-and-forget Kafka pendant `@Transactional`* (`EventService.cancel/publish` + `EventExpirationService.expireEvents`) émet des messages **avant le commit** : si la transaction est rollback (lock conflict, contrainte unique, etc.) un consommateur peut recevoir un événement « cancelled » pour un event qui n'a finalement jamais été cancelled (BUG-001, BUG-002 — critical/high). (3) Le code dupliqué entre les 14 services (ApiErrorResponse × 7, enums × 5–6 chacun, cascade `isCreatorOrAcceptedCoOrganizer` × 5, `computeAvailableSpots` × 6) est massif et n'a pas été promu en lib partagée (REFACTOR-001, 011..016).

Concernant les **anti-oracles** (ISSUE-92, ISSUE-93, SCRUM-136), ils sont préservés mais **réimplémentés inline** dans chaque service consommateur (comment-service, attendance-service, stats-service, follow-service…) au lieu d'être centralisés. La régression fonctionnelle est faible mais la dette est élevée : à chaque modification d'une règle de visibilité, il faudra synchroniser N services. La dérive est statistiquement inévitable.

Côté validation des inputs, les Resources sont globalement propres (`@Valid`, `@Min/@Max/@Positive` sur la pagination, `@PathParam` typé `Long`/`UUID` qui produit naturellement un 404 sur input mal formé). Quelques manques notables : `MyAttendancesResource` accepte `timeframe` en `String` plutôt qu'enum (parsing maison), aucun `@Valid` sur les chemins multipart (les annotations de DTO sur `EventRequestBase` ne s'appliquent qu'à JSON).

Concernant la concurrence sur `/attend` : le `PESSIMISTIC_WRITE` est posé sur `EventStub` (table `events`) — bonne pratique pour fermer la course capacity. **Mais** le retrait d'attendance + auto-promotion WAITLISTED→ATTENDING (`AttendanceService.removeAttendance`) lit l'attendance **avant** d'acquérir le lock pessimiste sur l'événement (BUG-005 — race fenêtre étroite mais réelle). De plus, la promotion ne re-vérifie pas `currentAttending < capacity` ; sur un retrait d'un user en WAITLISTED ce n'est pas grave (le code court-circuite via `removed != ATTENDING`), mais l'invariant repose entièrement sur cette guard.

Sur la sécurité des codes HTTP : les ApiErrorResponse renvoient bien le contrat `{error, message}` via `MediaType.APPLICATION_JSON_TYPE`, le mapper rate-limit pose `Retry-After` (RFC 6585), 404 anti-oracle préservé partout. Idempotence des routes : `POST /attend` (early-return si attendance existe) et `POST /favorite` (early-return) sont idempotents ; `POST /events/{id}/view` utilise un upsert SQL natif `ON CONFLICT` correct. ✓.

Sur le code mort : un seul TODO trouvé (`ModerationCleanupService:70` — émission Kafka events.banned) mais commentaire de migration partout (« replaced by REST client at PR 12/13 »). Aucun import inutilisé détecté lors du scan rapide.

## Findings — bugs (cat 2)

### BUG-001 [severity=critical, owner=backend, effort=M]
**Titre** : Kafka publish in-transaction sans listener post-commit → events.cancelled / events.published peuvent fuiter pour des transactions rollback
**Localisation** : `/workspace/backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java:363, 419`
**Symptôme** : `lifecyclePublisher.cancelled(...)` et `.published(...)` sont appelés à l'intérieur d'une méthode `@Transactional` ; l'emitter envoie le message Kafka **avant** le commit JDBC. Si la transaction rollback après l'emit (ex. contrainte CHECK, OptimisticLock, panne DB après le `event.status = CANCELLED`), le consommateur Kafka voit un événement qui n'a jamais été persisté.
**Pourquoi c'est un problème** : violation directe de la cohérence at-least-once de spec. notification-service (SCRUM-99) enverra des emails « votre événement a été annulé » pour des events qui n'ont jamais été annulés. Le commentaire `// Fire-and-forget — fired from inside the transaction (so a Kafka outage doesn't fail the user's cancel)` confond deux problèmes : tolérer une panne Kafka et émettre conditionnellement au commit. Le second exige un **outbox pattern** ou un `TransactionSynchronization` qui flush l'emitter en `afterCommit`.
**Fix suggéré** : implémenter le pattern outbox (table `event_outbox` insérée dans la même transaction, polling job qui publie + supprime), ou utiliser `jakarta.transaction.Synchronization` avec `afterCompletion(STATUS_COMMITTED)` pour différer le `emitter.send(...)`. Cf. SmallRye Reactive Messaging `@Outgoing` + `@Transactional` n'a **pas** de coordination native avec JTA en Quarkus.
**Dépendances** : BUG-002

### BUG-002 [severity=high, owner=backend, effort=M]
**Titre** : `EventExpirationService.expireEvents` émet `events.expired` à l'intérieur de la transaction → mêmes risques de message fuité
**Localisation** : `/workspace/backend/services/event-service/src/main/java/ch/unige/events/event/service/EventExpirationService.java:48`
**Symptôme** : la boucle `for (Event e : candidates) { e.status = EXPIRED; lifecyclePublisher.expired(e.id, creatorId); }` envoie N messages Kafka pendant la transaction. Si un commit final échoue, la totalité de la batch est rollbackée DB mais N messages `events.expired` ont été émis et seront consommés.
**Pourquoi c'est un problème** : pour un cron horaire qui peut traiter 50–100 events, c'est l'impact maximal. Les consommateurs n'ont aucun moyen de distinguer un vrai expire d'un faux positif.
**Fix suggéré** : même outbox pattern que BUG-001. Alternative tactique : émettre les events Kafka dans une boucle séparée **après** que `expireEvents()` retourne — mais alors la cohérence at-least-once n'est plus garantie en cas de crash entre commit et publish.
**Dépendances** : BUG-001

### BUG-003 [severity=high, owner=backend, effort=S]
**Titre** : `EventService.cancel` ne consulte pas `event.status` pour bloquer un cancel sur EXPIRED
**Localisation** : `/workspace/backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java:342-365`
**Symptôme** : Les guards bloquent `CANCELLED` (déjà annulé) et `BANNED` mais **pas** `EXPIRED`. Or annuler un événement déjà passé n'a pas de sens métier ; pire, ça déclenche `lifecyclePublisher.cancelled(...)` qui vient d'être émis pour le même event via `events.expired` une heure plus tôt.
**Pourquoi c'est un problème** : régression vs comportement attendu du legacy (à confirmer en lisant `legacy-monolith` mais le pattern est suspect). Le frontend affiche probablement un bouton « annuler » même sur des events expirés si la guard est côté UI uniquement.
**Fix suggéré** : ajouter `if (event.status == EventStatus.EXPIRED) throw conflict("Expired events cannot be cancelled.")`.
**Dépendances** : aucune

### BUG-004 [severity=high, owner=backend, effort=S]
**Titre** : `EventService.delete` supprime AttendanceStub/FavoriteStub/EventViewStub sans publier d'events Kafka → consommateurs voient l'event disparaître sans notification
**Localisation** : `/workspace/backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java:335-338`
**Symptôme** : DELETE hard sur 3 tables possédées par d'autres services (attendance-service, favorite-service, view-service) via JPQL `DELETE FROM AttendanceStub`. Aucun message Kafka émis ; aucune coordination distribuée. Les consommateurs en cache (notification-service à venir) ne peuvent pas réconcilier.
**Pourquoi c'est un problème** : (a) anti-pattern strict de microservices — un service écrit dans des tables qu'il ne possède pas ; (b) si un consommateur conserve un cache local (frontend SWR par exemple), il verra l'event puis 404, sans transition CANCELLED visible. La spec impose `delete` uniquement sur des events CANCELLED, ce qui mitige le UX, mais l'écriture cross-schema reste un bug architectural.
**Fix suggéré** : émettre `events.deleted` Kafka et laisser chaque service consommer pour purger ses propres tables. À court terme, garder le DELETE direct mais **logger** la fanout pour audit.
**Dépendances** : REFACTOR-001 (stubs → ownership clarifié)

### BUG-005 [severity=medium, owner=backend, effort=S]
**Titre** : `AttendanceService.removeAttendance` lit l'attendance avant le lock pessimiste sur l'event → race-window sur retrait/promotion
**Localisation** : `/workspace/backend/services/attendance-service/src/main/java/ch/unige/events/attendance/service/AttendanceService.java:108-117`
**Symptôme** : `Attendance.find(...).firstResultOptional()` est exécuté **avant** `entityManager.find(EventStub.class, eventId, PESSIMISTIC_WRITE)`. Deux requêtes concurrentes de retrait peuvent toutes deux observer la même attendance, puis acquérir le lock séquentiellement et tenter de la `delete()` deux fois. Hibernate gère le double delete (no-op sur la 2e), mais la promotion WAITLISTED s'exécute aussi 2× → potentiel double-promote si la liste d'attente avait 2+ entries dans la même fenêtre.
**Pourquoi c'est un problème** : sous charge (rush sur un event populaire qui ouvre la registration), peut promouvoir 2 users alors qu'une seule place se libère. Capacity overflow.
**Fix suggéré** : déplacer `entityManager.find(EventStub.class, eventId, PESSIMISTIC_WRITE)` **avant** la lecture de l'attendance. Dans la même veine, le `Attendance.<Attendance>find("eventId = ?1 and status = ?2 order by createdAt asc, id asc, ...").firstResultOptional()` pour la promotion devrait elle aussi être protégée par le lock — c'est le cas (lock pris ligne 115) mais à confirmer que Hibernate ne court-circuite pas le order-by avec un select-for-update.
**Dépendances** : aucune

### BUG-006 [severity=medium, owner=backend, effort=S]
**Titre** : `FavoriteService.addFavorite` early-return sans flush → race condition sur double-tap idempotency
**Localisation** : `/workspace/backend/services/favorite-service/src/main/java/ch/unige/events/favorite/service/FavoriteService.java:38-46`
**Symptôme** : `Favorite.findByUserAndEvent(...).isPresent()` puis `favorite.persist()` sans gestion explicite du conflit unique. Si deux requêtes simultanées passent toutes les deux le check `isPresent() == false`, les deux tentent un INSERT et la seconde lève `PersistenceException` → 500 au lieu d'un 200/204 idempotent. À comparer avec `FollowService.follow` qui a la défense (`isUniqueFollowConflict`) et avec `EventViewService` qui utilise un upsert SQL natif.
**Pourquoi c'est un problème** : POST /favorite spec'd idempotent mais comportement non garanti sous race.
**Fix suggéré** : aligner sur `FollowService.follow` avec un `try { persist; flush; } catch (PersistenceException) { check unique → return }` ou passer en upsert SQL natif comme view-service.
**Dépendances** : REFACTOR-017

### BUG-007 [severity=medium, owner=backend, effort=S]
**Titre** : `EventCoOrganizerService.accept` n'est pas idempotent — si déjà ACCEPTED, retourne 200 silencieusement, mais sans vérifier `status`
**Localisation** : `/workspace/backend/services/co-organizer-service/src/main/java/ch/unige/events/coorganizer/service/EventCoOrganizerService.java:85-89`
**Symptôme** : `if (invitation.status != ACCEPTED) invitation.status = ACCEPTED;`. Ce qui semble idempotent. **Mais** : la guard `findByEventAndUser` ne filtre pas par status (cf. `EventCoOrganizer.findByEventAndUser` à confirmer). Si un user accepte un invite, qu'on le retire (`delete()` côté creator), puis qu'il ré-essaie d'accepter, on ne retombera pas sur l'invite supprimée → 422 "no_pending_invitation". OK. Mais si l'invitation existe en `DECLINED` (pas dans l'enum visible — confirmer), on muterait depuis un état non-PENDING. À confirmer côté entity.
**Pourquoi c'est un problème** : à confirmer — dépend de la palette d'états réelle de `CoOrganizerStatus`.
**Fix suggéré** : ajouter une guard explicite `if (invitation.status != PENDING && invitation.status != ACCEPTED) throw unprocessable(...)`.
**Dépendances** : à confirmer

### BUG-008 [severity=high, owner=backend, effort=L]
**Titre** : Tous les stubs JPA accèdent à des tables possédées par d'autres services → couplage fort, monolithe distribué
**Localisation** : 35 stubs trouvés via `find -name '*Stub.java'` ; tous mappés sur `@Table(name = "events" | "users" | "attendances" | "favorites" | "event_views" | "event_co_organizers" | "follows")`
**Symptôme** : chaque service Quarkus a son propre datasource pointant sur le **même** schéma PostgreSQL. La PR ne migre **pas** vers REST sync ni vers Kafka projection — elle crée 14 modules Maven qui partagent un schéma physique. Toute modification de schéma (ALTER TABLE events ADD COLUMN ...) requiert un déploiement coordonné de N services pour mettre à jour la stub. Toute lecture stale est silencieuse (Hibernate 1st-level cache).
**Pourquoi c'est un problème** : la « migration microservices » est une fiction architecturale tant que la propriété des données n'est pas réelle. Chaque commentaire « replaced by REST client at PR 12/13 » signale que le travail est repoussé. Le scoring SonarQube ne capte pas ça mais la dette technique est massive.
**Fix suggéré** : prioriser les chemins critiques :
- `EventStub` consommé par 9 services → REST sync `GET /events/{id}` event-service exposé en cache
- `UserStub` consommé par 11 services → projection Kafka `users.profile.changed` consumed par chaque service
- `AttendanceStub` consommé par 4 services (calendar, co-organizer, event, favorite, me-aggregator, stats) → REST sync ou projection event-driven
**Dépendances** : REFACTOR-001..010

### BUG-009 [severity=medium, owner=backend, effort=S]
**Titre** : `UserService.getPublicProfile` n'est pas annoté `@Transactional` mais lit `User.findByIdOptional` + `FollowStub.countFollowersOf` + `FollowStub.findByFollowerAndFollowed` — risque LazyInitializationException
**Localisation** : `/workspace/backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java:72-97`
**Symptôme** : pas de `@Transactional` sur `getPublicProfile`. Quarkus active automatiquement Open-Session-In-View (`quarkus.hibernate-orm.implicit-transaction = false` par défaut), mais en cas d'évolution de config, `Follow.findByFollowerAndFollowed(...)` retournée puis `.map(f -> f.status)` peut échouer.
**Pourquoi c'est un problème** : risque hibernate hors-transaction en cas de tweaks futurs ; cohérence aussi (deux SELECT non-isolés dans une même requête HTTP).
**Fix suggéré** : ajouter `@Transactional` (read-only si Hibernate le supporte ici, sinon par défaut).
**Dépendances** : aucune

### BUG-010 [severity=medium, owner=backend, effort=S]
**Titre** : `UserService.updateMyProfile(authenticatedAuth0Id, targetAuth0Id, req)` accepte `Objects.equals(null, null) == true`
**Localisation** : `/workspace/backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java:100-105`
**Symptôme** : si les deux paramètres sont null (cas pathologique mais possible si appelé en interne), `Objects.equals(null, null)` retourne `true` et la mise à jour passe la guard. Le call vers la version (auth0Id, req) tombera ensuite sur `User.findByAuth0Id(null)` qui throw `NotFoundException`. Pas de fuite directe, mais la guard est trompeuse.
**Pourquoi c'est un problème** : faux sentiment de sécurité ; si demain quelqu'un ajoute un fallback dans `findByAuth0Id`, le code devient exploitable.
**Fix suggéré** : `if (authenticatedAuth0Id == null || targetAuth0Id == null || !authenticatedAuth0Id.equals(targetAuth0Id))`.
**Dépendances** : aucune

### BUG-011 [severity=low, owner=backend, effort=S]
**Titre** : `MyAttendancesResource` parse `timeframe` à la main au lieu d'utiliser un enum JAX-RS — incohérence vs `?status=ATTENDING` qui est natif
**Localisation** : `/workspace/backend/services/attendance-service/src/main/java/ch/unige/events/attendance/resource/MyAttendancesResource.java:50, 60-69`
**Symptôme** : `@QueryParam("timeframe") String timeframeParam` puis `parseTimeframe(...)` à la main. Inconsistant avec `@QueryParam("status") AttendanceStatus status` (juste au-dessus) qui se base sur le binding JAX-RS natif.
**Pourquoi c'est un problème** : dette + le commentaire explique « le binding natif retourne 404 plutôt que 400 ». C'est résolu globalement par un `ParamConverterProvider` partagé dans le legacy ; ici on a recopié la solution tactique.
**Fix suggéré** : extraire un `TimeframeParamConverter` dans le shared lib (à créer) ou dans une lib commune `shared-jaxrs` ; idem pour les autres enums.
**Dépendances** : REFACTOR-018

### BUG-012 [severity=low, owner=backend, effort=S]
**Titre** : `RedirectResource` utilise une URL de fallback hardcodée `https://10.25.10.136.nip.io`
**Localisation** : `/workspace/backend/services/share-service/src/main/java/ch/unige/events/share/resource/RedirectResource.java:25`
**Symptôme** : `@ConfigProperty(name = "app.frontend.url", defaultValue = "https://10.25.10.136.nip.io")`. La defaultValue est une IP de preview env qui finira en prod si la config oublie de poser `app.frontend.url`.
**Pourquoi c'est un problème** : un déploiement raté redirige les users vers un environnement preview / privé.
**Fix suggéré** : enlever le defaultValue, faire fail-fast au démarrage si la config n'est pas posée.
**Dépendances** : aucune

### BUG-013 [severity=low, owner=backend, effort=S]
**Titre** : `EventService.delete` ne purge pas les `Comment`, `Report`, `EventCoOrganizer`, `Favorite` (réplique du legacy ?)
**Localisation** : `/workspace/backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java:322-339`
**Symptôme** : DELETE de Attendance/Favorite/EventView mais pas Comment/Report/EventCoOrganizer (à confirmer si la FK cascade le fait au niveau DB).
**Pourquoi c'est un problème** : si les FK n'ont pas `ON DELETE CASCADE`, le DELETE FROM events échoue. Si elles l'ont, la cohérence inter-service est encore plus chaotique. À confirmer en lisant le V1__init.sql.
**Fix suggéré** : audit du DDL + spec explicite des cascades.
**Dépendances** : BUG-004

### BUG-014 [severity=medium, owner=backend, effort=S]
**Titre** : `EventService.publish` valide `startDate.isAfter(LocalDateTime.now())` — but no timezone handling, no `@Future` à la création
**Localisation** : `/workspace/backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java:448`, `EventRequestBase.java:31`
**Symptôme** : `EventRequestBase.startDate` est `@Future` (validateur Bean Validation, JVM default zone) ; à `publish()` re-validation manuelle `isAfter(LocalDateTime.now())`. Les deux comparaisons sont sur l'horloge serveur, sans normalisation timezone alors que `EventSearchService.search` convertit explicitement en `Europe/Zurich → UTC`. L'incohérence rend possible la création d'un event avec startDate dans le futur côté Zurich mais dans le passé côté UTC (ou inversement).
**Pourquoi c'est un problème** : drift de comportement entre create/update (TZ JVM) et search (TZ Zurich). Symptôme caché tant que JVM = Europe/Zurich, casse en prod sur un container UTC.
**Fix suggéré** : normaliser systématiquement en UTC à l'ingestion ; documenter la convention en haut de `EventRequestBase`.
**Dépendances** : aucune

## Findings — refactor (cat 4)

### REFACTOR-001 [severity=high, owner=backend, effort=L]
**Titre** : 35 JPA stubs dupliqués pointant sur le schéma `public` partagé — aucune migration REST/Kafka effective
**Localisation** : voir annexe « JPA stubs → REST clients » ci-dessous
**Symptôme** : chaque service ré-implémente sa propre stub avec subset de colonnes pertinentes. Un même UserStub a 4 variantes différentes (`event-service` 2 colonnes, `stats-service` 2 colonnes, `attendance-service` plus de colonnes…).
**Pourquoi c'est un problème** : (a) toute évolution de schéma `users` requiert un audit transverse ; (b) le 1st-level cache Hibernate de chaque service peut servir des données stale ; (c) la « migration » prétendue par la PR n'a en fait pas eu lieu — c'est de la modularisation Maven.
**Fix suggéré** : voir BUG-008. Court terme : centraliser les stubs dans une lib `shared-data-projections` versionnée, tester la résolution de FK cross-module en intégration.
**Dépendances** : BUG-008

### REFACTOR-002 [severity=high, owner=backend, effort=M]
**Titre** : `ApiErrorResponse` dupliqué dans 7 services (record identique)
**Localisation** : 
- `/workspace/backend/services/co-organizer-service/.../dto/ApiErrorResponse.java`
- `/workspace/backend/services/comment-service/.../dto/ApiErrorResponse.java`
- `/workspace/backend/services/follow-service/.../dto/ApiErrorResponse.java`
- `/workspace/backend/services/user-service/.../dto/ApiErrorResponse.java`
- `/workspace/backend/services/attendance-service/.../dto/ApiErrorResponse.java`
- `/workspace/backend/services/event-service/.../dto/ApiErrorResponse.java`
- `/workspace/backend/services/report-service/.../dto/ApiErrorResponse.java`
**Symptôme** : record identique (`String error, String message`) avec annotation OpenAPI `@Schema`. Le mapper rate-limit le note explicitement (`RateLimitErrorBody`) en commentaire : « Each microservice has its own ApiErrorResponse record in its own package; replicating that record here would couple this lib to the wrong layer ». Justification rationnelle mais conséquence : 7 copies à maintenir.
**Pourquoi c'est un problème** : si le contrat évolue (ex. ajouter `traceId` ou `timestamp`), 7 modifs synchronisées.
**Fix suggéré** : promouvoir en lib `shared-api-error` (ou inclure dans le futur `shared-jaxrs`).
**Dépendances** : aucune

### REFACTOR-003 [severity=high, owner=backend, effort=M]
**Titre** : Enums métier dupliqués (5–6 services × `AttendanceStatus`, `EventStatus`, `EventCategory`, `Faculty`, `CoOrganizerStatus`, `ReportStatus`, `FollowStatus`, `RecurrenceFrequency`)
**Localisation** : 38 fichiers enum trouvés
**Symptôme** : chaque service a sa propre copie de `EventStatus { DRAFT, PUBLISHED, CANCELLED, EXPIRED, BANNED }` etc. Si un nouveau status arrive (ex. `ARCHIVED`), N services à patcher en parallèle.
**Pourquoi c'est un problème** : les enums **doivent** être identiques sinon les `@Enumerated(EnumType.STRING)` lèveront des `IllegalArgumentException` à la lecture. Le risque de drift est non-zero.
**Fix suggéré** : promouvoir les enums en lib `shared-domain-enums`. Un seul artifact à versionner ; chaque service le déclare en dépendance.
**Dépendances** : REFACTOR-001

### REFACTOR-004 [severity=medium, owner=backend, effort=M]
**Titre** : `isCreatorOrAcceptedCoOrganizer(EventStub, UserStub)` dupliqué dans 5 services (cascade SCRUM-136)
**Localisation** :
- `comment-service/CommentService.java:197-205`
- `attendance-service/AttendanceService.java:215-223`
- `report-service/ReportService.java:143-151`
- `stats-service/EventStatsService.java:40-48`
- `event-service/EventService.java:518-528` (variante avec `auth0Id` plutôt que `UserStub`)
**Symptôme** : 5 implémentations identiques, différant uniquement par les noms des stubs locaux.
**Pourquoi c'est un problème** : la règle SCRUM-136 (« creator OR ACCEPTED co-organizer ») est centrale ; toute évolution (ex. ajouter `INVITED` qui aurait des droits read) requiert 5 patches.
**Fix suggéré** : exposer côté co-organizer-service un endpoint REST `GET /events/{eventId}/co-organizers/check?userId=` (déjà mentionné en commentaire dans EventCoOrganizerService.java:165 mais pas cablé). Tous les autres services consomment ce endpoint.
**Dépendances** : BUG-008

### REFACTOR-005 [severity=medium, owner=backend, effort=S]
**Titre** : `computeAvailableSpots(Integer capacity, long attendingCount)` dupliqué dans 6 services
**Localisation** :
- `event-service/EventService.java:490-495`
- `attendance-service/AttendanceService.java:208-213`
- `co-organizer-service/EventCoOrganizerService.java:186-191`
- `favorite-service/FavoriteService.java:83-88`
- `me-aggregator-service/MyEventsService.java:70-75`
- `event-service/FeaturedService.java:111` (référence statique)
**Symptôme** : copie textuelle (3 lignes) dans 6 fichiers.
**Pourquoi c'est un problème** : trivial mais signe d'un manque de domain-lib partagée.
**Fix suggéré** : promouvoir dans `shared-domain-projections` à côté des enums.
**Dépendances** : REFACTOR-003

### REFACTOR-006 [severity=medium, owner=backend, effort=M]
**Titre** : `EventDTO` (record) dupliqué dans 6 services (event/co-organizer/me-aggregator/favorite/attendance/calendar)
**Localisation** : 6 fichiers `EventDTO.java` avec import des enums locaux
**Symptôme** : 25+ champs identiques. Chaque service réimporte ses enums locaux. C'est l'illustration la plus claire que les services partagent un domaine et non pas un contrat REST.
**Pourquoi c'est un problème** : la moindre évolution (ex. ajouter `shareCode` ou `recurrenceRule`) requiert 6 patches synchrones.
**Fix suggéré** : event-service est le owner ; les autres services ne devraient pas re-projeter `EventDTO` mais consommer le DTO via REST + déserialisation. À court terme, factoriser dans `shared-domain-dtos`.
**Dépendances** : BUG-008, REFACTOR-001

### REFACTOR-007 [severity=medium, owner=backend, effort=S]
**Titre** : Méthode `EventService.getAll` 45+ lignes — query JPQL construite dynamiquement à la main
**Localisation** : `event-service/EventService.java:64-110`
**Symptôme** : `StringBuilder` + `List<String> conditions` + `Map<String, Object> params`. Dupliqué dans `EventSearchService.search:38-86`. Pattern Criteria API plus sûr / typé.
**Pourquoi c'est un problème** : risque de SQL-injection si un futur dev ajoute un paramètre non-bindé ; verbeux ; pas de typage.
**Fix suggéré** : passer en JPA Criteria API ou Panache `find` avec un query record fluide.
**Dépendances** : aucune

### REFACTOR-008 [severity=medium, owner=backend, effort=S]
**Titre** : `EventService` 529 lignes — God-object frôlant le seuil de 500 lignes
**Localisation** : `event-service/EventService.java:1-529`
**Symptôme** : porte simultanément CRUD events, recurrence, validation publish, cascade SCRUM-136, normalisation tags, gestion des erreurs.
**Pourquoi c'est un problème** : difficile à lire, mock-friendly réduit en tests.
**Fix suggéré** : extraire `EventLifecycleService` (publish/cancel/restore), `EventRecurrenceService` (createRecurring/getOccurrences), `EventVisibilityService` (cascade + isCreator), garder EventService pour CRUD pur.
**Dépendances** : aucune

### REFACTOR-009 [severity=medium, owner=backend, effort=S]
**Titre** : `assertEventVisibleAndLoad` (anti-oracle ISSUE-92) dupliqué + commentaire « replaces le GET /events/{id} d'event-service »
**Localisation** : `comment-service/CommentService.java:172-183`, équivalent dans event-service/EventService.java:255-265, et stats-service le réimplémente partiellement.
**Symptôme** : la règle « 404 sur DRAFT/CANCELLED non-créateur non-admin » est une règle de visibilité du domaine event-service. Elle est ré-implémentée dans 3 services.
**Pourquoi c'est un problème** : tout drift de la règle = bug d'autorisation. À l'inverse de SCRUM-136 (qui a des positifs ajoutables), un drift sur ISSUE-92 peut révéler l'existence d'un event banni. Sécurité.
**Fix suggéré** : centraliser via un client REST `GET /events/{id}` qui renvoie 404 nativement quand non visible. Les services consommateurs propagent juste le 404.
**Dépendances** : BUG-008, REFACTOR-001

### REFACTOR-010 [severity=medium, owner=backend, effort=S]
**Titre** : `assertProfileVisible` (anti-oracle ISSUE-93) implémenté dans follow-service mais pas exposé en endpoint
**Localisation** : `follow-service/FollowService.java:139-146`
**Symptôme** : helper local qui réplique exactement la logique de `UserService.getPublicProfile:75-78`. Le commentaire JavaDoc reconnaît la duplication (« In the legacy monolith this lived in UserService.getPublicProfile »).
**Pourquoi c'est un problème** : même problème que REFACTOR-009 — drift de sécurité.
**Fix suggéré** : user-service doit exposer un endpoint de vérification (ex. `HEAD /users/{id}` qui retourne 404 si non visible) ou follow-service consomme `GET /users/{id}` direct.
**Dépendances** : BUG-008

### REFACTOR-011 [severity=low, owner=backend, effort=S]
**Titre** : `WebApplicationException` factory helpers (`badRequest`, `conflict`, `unprocessable`, `forbidden`, `notFound`) dupliqués dans 5+ services
**Localisation** :
- `comment-service/CommentService.java:220-250`
- `report-service/ReportService.java:153-175`
- `co-organizer-service/EventCoOrganizerService.java:199-221`
- `follow-service/FollowService.java:154-176`
- `event-service/EventService.java:246-252, 385-390`
**Symptôme** : 5+ classes redéfinissent les mêmes 4–5 méthodes statiques `WebApplicationException badRequest(error, message)`.
**Pourquoi c'est un problème** : duplication mécanique pure.
**Fix suggéré** : ajouter `ApiErrors.badRequest(...)` etc. dans `shared-api-error` (cf. REFACTOR-002).
**Dépendances** : REFACTOR-002

### REFACTOR-012 [severity=low, owner=backend, effort=S]
**Titre** : `ServiceIdentityResource.java` dupliqué dans les 14 services
**Localisation** : 14 occurrences trouvées
**Symptôme** : à confirmer (non lu) mais le pattern « chaque service expose un endpoint d'identité de service » suggère duplication mécanique.
**Pourquoi c'est un problème** : si lecture confirme duplication, factoriser dans `shared-platform`.
**Fix suggéré** : à confirmer.
**Dépendances** : à confirmer

### REFACTOR-013 [severity=low, owner=backend, effort=S]
**Titre** : `resolveUser(auth0Id)` / `resolveUserId(auth0Id)` dupliqués dans 5+ services
**Localisation** : pattern présent dans attendance-service, calendar-service, favorite-service, follow-service, etc.
**Symptôme** : `UserStub.findByAuth0Id(auth0Id).orElseThrow(() -> new NotFoundException("User profile not found — call GET /users/me first"))`. Helper privé dans chaque service.
**Pourquoi c'est un problème** : duplication des messages d'erreur surtout. Tout drift de message → différence de comportement frontend.
**Fix suggéré** : helper centralisé dans `shared-domain-projections`.
**Dépendances** : REFACTOR-001

### REFACTOR-014 [severity=low, owner=backend, effort=S]
**Titre** : Code mort signalé : un seul TODO mais multiples mentions « replaced at PR 12/13 » qui ne s'appliquent plus (legacy supprimé)
**Localisation** : 
- `report-service/ModerationCleanupService.java:70` — TODO Kafka events.banned
- 35+ commentaires « Replaced by REST client at PR 12/13 » dans les stubs et services
**Symptôme** : le legacy-monolith a été supprimé à `b570c1b` mais les commentaires « PR 12/13 » réfèrent à un timeline obsolète.
**Pourquoi c'est un problème** : les nouveaux contributeurs lisent des commentaires qui ne correspondent plus au plan.
**Fix suggéré** : grep + remplacement systématique pour clarifier le plan post-S8.
**Dépendances** : aucune

### REFACTOR-015 [severity=low, owner=backend, effort=S]
**Titre** : `EventCoOrganizer.findByEventAndUser` n'est pas filtrée par status — comportement opaque
**Localisation** : `co-organizer-service/EventCoOrganizerService.java:62, 81, 96, 113`
**Symptôme** : 4 appels au même finder qui retourne soit PENDING soit ACCEPTED soit (si l'enum a d'autres valeurs) tout autre status. Le caller doit deviner.
**Pourquoi c'est un problème** : voir BUG-007. Pour les readers, pas explicite.
**Fix suggéré** : renommer en `findActiveByEventAndUser` et préciser que DECLINED → delete (comme dans `decline`).
**Dépendances** : BUG-007

### REFACTOR-016 [severity=medium, owner=backend, effort=S]
**Titre** : `MyEventsService` (me-aggregator) ne fait pas d'aggregation — c'est juste un wrapper sur EventStub
**Localisation** : `me-aggregator-service/service/MyEventsService.java`
**Symptôme** : me-aggregator-service est censé être le BFF du frontend pour les pages /me/*. Mais `getMyEvents` lit directement `EventStub` (et non event-service via REST). Idem pour les autres /me/* routes qui sont éparpillées sur attendance-service, favorite-service, co-organizer-service. Le commentaire admet : « The other /me/* paths (favorites, attendances, participations) stay on their owning services until event-service ships and the BFF can fan-out via REST clients. »
**Pourquoi c'est un problème** : me-aggregator ne joue pas son rôle ; c'est juste un service de plus qui lit la même DB. Architecture non-cohérente.
**Fix suggéré** : (a) déplacer toutes les routes /me/* dans me-aggregator avec fanout REST ; (b) ou abandonner me-aggregator et garder /me/* dispatché par owning-service.
**Dépendances** : BUG-008

### REFACTOR-017 [severity=low, owner=backend, effort=S]
**Titre** : Pattern d'idempotence inconsistant — early-return / try-catch unique / upsert SQL natif
**Localisation** :
- `FavoriteService` early-return naïf (BUG-006)
- `FollowService` try-catch sur unique constraint
- `EventViewService` upsert SQL natif `ON CONFLICT`
- `AttendanceService` early-return après lock pessimiste
**Symptôme** : 4 patterns différents pour la même intention (« insert idempotent »).
**Pourquoi c'est un problème** : dette + risques différenciés (cf. BUG-006).
**Fix suggéré** : standardiser sur upsert SQL natif (le plus robuste race-wise).
**Dépendances** : BUG-006

### REFACTOR-018 [severity=low, owner=backend, effort=S]
**Titre** : Aucune lib `shared-jaxrs` — chaque service réimplémente ses ParamConverters et ses Mappers
**Localisation** : `MyAttendancesResource.parseTimeframe` ; absence d'un package `shared-jaxrs`
**Symptôme** : seules 2 libs partagées (`shared-rate-limit`, `shared-storage`). Aucun pour les concerns JAX-RS transverses.
**Pourquoi c'est un problème** : duplication latente ; barrière d'entrée pour ajouter de nouveaux concerns transverses.
**Fix suggéré** : créer `shared-jaxrs` (ParamConverters d'enums, ApiErrorResponse, ExceptionMappers, JsonWebToken Instance helper).
**Dépendances** : REFACTOR-002, REFACTOR-011

## Annexe — JPA stubs → REST clients

| Stub | Service consommateur | Service propriétaire | Endpoint REST cible | Pattern (sync/Kafka) |
|---|---|---|---|---|
| `UserStub` | event-service | user-service | `GET /users/{id}`, `GET /users/by-auth0/{auth0Id}` | Kafka projection `users.profile.changed` (faible volume mais haute fréquence de lookup) |
| `UserStub` | attendance-service | user-service | idem | Kafka projection |
| `UserStub` | comment-service | user-service | idem | Kafka projection |
| `UserStub` | co-organizer-service | user-service | idem | Kafka projection |
| `UserStub` | report-service | user-service | idem | Kafka projection |
| `UserStub` | favorite-service | user-service | idem | Kafka projection |
| `UserStub` | calendar-service | user-service | `GET /users/by-calendar-token/{token}` | REST sync (le token bouge à chaque rotate) |
| `UserStub` | view-service | user-service | `GET /users/by-auth0/{auth0Id}` | REST sync (lecture rare, pas worth a projection) |
| `UserStub` | follow-service | user-service | `GET /users/{id}` | REST sync |
| `UserStub` | me-aggregator-service | user-service | `GET /users/by-auth0/{auth0Id}` | REST sync |
| `UserStub` | stats-service | user-service | idem | REST sync |
| `EventStub` | attendance-service | event-service | `GET /events/{id}` (avec PESSIMISTIC_WRITE → API capacity-gating dédiée) | REST sync + endpoint capacité dédié |
| `EventStub` | comment-service | event-service | `GET /events/{id}` | REST sync |
| `EventStub` | co-organizer-service | event-service | `GET /events/{id}` | REST sync |
| `EventStub` | favorite-service | event-service | `GET /events/{id}` (existence check), `GET /events?ids=` (bulk for /me/favorites) | REST sync |
| `EventStub` | view-service | event-service | `GET /events/{id}` (existence check) | REST sync |
| `EventStub` | report-service | event-service | `GET /events/{id}` + `PATCH /events/{id}` (status BANNED) | Kafka producer `events.banned` consumed par event-service |
| `EventStub` | calendar-service | event-service | `GET /events?ids=&status=PUBLISHED` | REST sync |
| `EventStub` | me-aggregator-service | event-service | `GET /events?creatorId={id}` | REST sync |
| `EventStub` | stats-service | event-service | `GET /events/{id}` | REST sync |
| `AttendanceStub` | event-service | attendance-service | `GET /events/{eventId}/attendance-summary` (count by status) | REST sync (read-heavy, cache friendly) |
| `AttendanceStub` | co-organizer-service | attendance-service | idem | REST sync |
| `AttendanceStub` | favorite-service | attendance-service | idem | REST sync (bulk) |
| `AttendanceStub` | calendar-service | attendance-service | `GET /users/{id}/attendances?status=ATTENDING` | REST sync |
| `AttendanceStub` | me-aggregator-service | attendance-service | idem | REST sync |
| `AttendanceStub` | stats-service | attendance-service | `GET /events/{id}/attendance-summary` | REST sync |
| `FavoriteStub` | event-service | favorite-service | `GET /events/{id}/favorite-count` | REST sync (sinon Kafka projection) |
| `FavoriteStub` | calendar-service | favorite-service | `GET /users/{id}/favorites` | REST sync |
| `FavoriteStub` | stats-service | favorite-service | `GET /events/{id}/favorite-count` | REST sync |
| `EventViewStub` | event-service | view-service | `GET /events/{id}/view-count` | REST sync ou Kafka projection |
| `EventViewStub` | stats-service | view-service | idem | REST sync |
| `EventCoOrganizerStub` | event-service | co-organizer-service | `GET /events/{id}/co-organizers/check?userId=`, `GET /events/{id}/co-organizers/accepted-user-ids` | REST sync (cascade SCRUM-136) |
| `EventCoOrganizerStub` | comment-service | co-organizer-service | idem | REST sync |
| `EventCoOrganizerStub` | attendance-service | co-organizer-service | idem | REST sync |
| `EventCoOrganizerStub` | report-service | co-organizer-service | idem | REST sync |
| `EventCoOrganizerStub` | stats-service | co-organizer-service | idem | REST sync |
| `FollowStub` | user-service | follow-service | `GET /users/{id}/follow-counts`, `GET /follows/{follower}/{followed}` | REST sync |

## Annexe — code dupliqué inter-services

| Code | Présent dans | Lib partagée cible |
|---|---|---|
| `ApiErrorResponse` (record `{error, message}`) | 7 services (cf. REFACTOR-002) | `shared-api-error` (à créer) |
| `AttendanceStatus` enum | 6 services | `shared-domain-enums` (à créer) |
| `EventStatus` enum | 8 services | `shared-domain-enums` |
| `EventCategory` enum | 6 services | `shared-domain-enums` |
| `Faculty` enum | 6 services | `shared-domain-enums` |
| `CoOrganizerStatus` enum | 5 services | `shared-domain-enums` |
| `ReportStatus`, `ReportReason` enums | report-service (singleton) | `shared-domain-enums` (préventif si stats/notif les exposent un jour) |
| `FollowStatus` enum | 2 services (user, follow) | `shared-domain-enums` |
| `RecurrenceFrequency` enum | event-service (singleton) | `shared-domain-enums` (préventif) |
| `EventDTO` record | 6 services | `shared-domain-dtos` (à créer) ou consommé exclusivement via REST event-service |
| `isCreatorOrAcceptedCoOrganizer(EventStub, UserStub)` | 5 services | endpoint REST sur co-organizer-service |
| `assertEventVisibleAndLoad` (anti-oracle ISSUE-92) | 3 services | endpoint REST `GET /events/{id}` event-service |
| `assertProfileVisible` (anti-oracle ISSUE-93) | 2 services | endpoint REST `GET /users/{id}` user-service |
| `computeAvailableSpots(Integer, long)` | 6 services | `shared-domain-projections` |
| `WebApplicationException` factory helpers (`badRequest`, `conflict`, `unprocessable`, `forbidden`, `notFound`) | 5+ services | `shared-api-error` |
| `resolveUser(auth0Id)` / `resolveUserId(auth0Id)` helper | 5+ services | `shared-domain-projections` |
| Pattern try-catch unique constraint (`isUniqueXxxConflict(Throwable)`) | 2 services (user, follow) | `shared-data-projections` (utility) |
| `parseTimeframe(String)` | attendance-service (futur : potentiellement dupliqué dès qu'un autre service prend un enum query param) | `shared-jaxrs` ParamConverter |
| `ServiceIdentityResource.java` | 14 services (à confirmer) | `shared-platform` |


## Catégorie 3 — Couverture de tests (18 findings)


PR #158 — `refactor(backend)--migrate-to-microservices` @ `bee933d`

## Summary

- **18 findings**
- Sévérité : 1 BLOCKER (TEST-001 sentinels SCRUM-138/139/147 0/35 présents) · 6 CRITICAL (microservices à 0 % business / classes legacy non portées) · 7 MAJOR · 4 MINOR.
- Les 14 microservices compilent et passent leur unique test (`ServiceIdentityResourceTest`) ; aucun test métier n'a été porté depuis `legacy-monolith`. **1818 méthodes `@Test` du commit `41074e9` ont été supprimées**, **0 ont été ré-implémentées** dans les services extraits.
- Sonar gate "passe" uniquement parce que `services/*-service/**/*` est dans `<sonar.coverage.exclusions>` (parent POM L88-89). Sans cette exclusion, branch coverage du reactor effondre.
- Les deux shared libs (`shared-rate-limit`, `shared-storage`), elles non exclues, sont **100 % lines / 100 % branches** — RAS de ce côté.

## Tableau de couverture par module

Lignes/branches mesurées au top-level du `target/jacoco-report/jacoco.xml`. Les microservices voient leurs jacoco gonflés par les classes `shared/ratelimit` et `shared/storage` packagées dans le uber-jar Quarkus — la quasi-totalité des "missed lines" provient quand même de leur propre code métier (FollowService, EventService, UserService…).

| Module | Lines covered | Branches covered | Classes (cov/total) | Test files | Sentinels listés présents |
|---|---|---|---|---|---|
| attendance-service | 37 / 273 (13.6 %) | 0 / 98 (0 %) | 10 / 24 | 1 (sentinel only) | 0 / N |
| calendar-service | 13 / 106 (12.3 %) | 0 / 16 (0 %) | 6 / 11 | 1 | 0 |
| co-organizer-service | 37 / 194 (19.1 %) | 0 / 54 (0 %) | 10 / 18 | 1 | 0 |
| comment-service | 17 / 233 (7.3 %) | 0 / 110 (0 %) | 7 / 19 | 1 | 0 / 14 (SCRUM-144) |
| event-service | 49 / 764 (6.4 %) | 0 / 290 (0 %) | 13 / 46 | 3 (1 sentinel + 2 kafka DTO) | 0 / 21 (SCRUM-147) |
| favorite-service | 33 / 172 (19.2 %) | 0 / 34 (0 %) | 9 / 19 | 1 | 0 |
| follow-service | 7 / 213 (3.3 %) | 0 / 66 (0 %) | 4 / 16 | 1 | 0 / 6 (SCRUM-138) |
| me-aggregator-service | 32 / 80 (40.0 %) | 0 / 12 (0 %) | 8 / 11 | 1 | n/a |
| notification-service | n/a (no jacoco — service stub) | n/a | 1 / 1 | 1 | n/a |
| report-service | 26 / 165 (15.8 %) | 0 / 56 (0 %) | 9 / 18 | 1 | 0 |
| share-service | 3 / 30 (10.0 %) | 0 / 4 (0 %) | 2 / 6 | 1 | 0 |
| **shared-rate-limit** | **67 / 67 (100 %)** | **24 / 24 (100 %)** | **6 / 6** | 4 | n/a (lib) |
| **shared-storage** | **101 / 101 (100 %)** | **35 / 35 (100 %)** | **7 / 7** | 7 | n/a (lib) |
| stats-service | 15 / 41 (36.6 %) | 0 / 10 (0 %) | 9 / 12 | 1 | 0 |
| user-service | 18 / 324 (5.6 %) | 0 / 123 (0 %) | 5 / 24 | 1 | 0 |
| view-service | 5 / 28 (17.9 %) | 0 / 2 (0 %) | 4 / 6 | 1 | 0 |

Lecture : la colonne « Lines covered » inclut les dépendances shared embarquées ; le **business code** des microservices est essentiellement à **0 %** (l'unique sentinel touche `ServiceIdentityResource` + l'init `PanacheEntity` du PK). La colonne branches est non-nuancée : **0 branche métier exercée sur 14 services**, soit 1145 branches non testées au total dans ce reactor.

Ratio src/test (fichiers Java) :

| Service | src classes | test classes | ratio |
|---|---:|---:|---:|
| attendance-service | 18 | 1 | 0.06 |
| co-organizer-service | 18 | 1 | 0.06 |
| report-service | 19 | 1 | 0.05 |
| comment-service | 13 | 1 | 0.08 |
| calendar-service | 12 | 1 | 0.08 |
| favorite-service | 13 | 1 | 0.08 |
| stats-service | 12 | 1 | 0.08 |
| user-service | 12 | 1 | 0.08 |
| event-service | 32 | 3 | 0.09 |
| me-aggregator-service | 11 | 1 | 0.09 |
| follow-service | 10 | 1 | 0.10 |
| share-service | 7 | 1 | 0.14 |
| view-service | 6 | 1 | 0.17 |
| shared-rate-limit | 5 | 4 | 0.80 |
| shared-storage | 8 | 7 | 0.88 |
| notification-service | 1 | 1 | 1.00 (stub) |

13 services sur 14 ont un ratio < 0.20 (cible normale ≥ 0.7). **Tous prioritaires.**

---

## Findings

### TEST-001 [BLOCKER, owner=backend, effort=XL]
**Titre** : Sentinels documentés SCRUM-138 + SCRUM-139 + SCRUM-144 + SCRUM-147 : **0/35 présents** dans le reactor microservices
**Service / classe / méthode** : N/A (transversal — `git ls-tree -r 41074e9 -- backend/services/legacy-monolith/src/test/`)
**Ce qui manque** : Les 35 sentinels nommément cités dans `backend/docs/sprint-context.md` (recurrence, follow, comment, co-organizer) sont **tous absents** des `src/test` actuels. Recherchés via `grep -rn "void <name>" /workspace/backend/services/*/src/test` — 0 hit pour : `weekly_4Occurrences_returns3DatesSpacedBy7Days`, `monthly_handlesShortFebruaryFromJanuary31`, `bothNull_throwsIllegalArgumentException`, `maxOccurrencesAbove52_cappedTo52`, `from_parentRecurringEvent_exposesRecurrenceRuleAndNullParentEventId`, `from_occurrenceEvent_exposesParentEventIdAndNullRecurrenceRule`, `createRecurring_weekly4Occurrences_persists1ParentAnd3Children`, `createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded`, `createRecurring_endDateBeforeStart_returns400_recurrenceEndBeforeStart`, `createRecurring_inheritsParentStatusPublished`, `getOccurrences_parentRecurring_returnsChildrenSortedAsc`, `getOccurrences_standaloneEvent_returns200EmptyList`, `getOccurrences_draftByNonCreator_returns404_antiOracle`, `update_parentTitle_doesNotPropagateToOccurrences`, `cancel_parentDoesNotCascadeToOccurrences`, `delete_parent_setsOccurrencesParentEventIdToNull`, `post_validRecurrenceWeekly_returns201_recurrenceRuleSetOnParent`, `post_recurrenceMaxOccurrences53_returns400_beanValidation`, `getOccurrences_parentPublishedAnonymous_returns200`, `getOccurrences_sizeOver52_returns400`, `getOccurrences_draftByAnonymous_returns404_antiOracle`, `findAcceptedFollowedIds_returnsOnlyAcceptedUuids`, `rejectRequest_followerCanReFollowAfterReject`, `follow_selfFollow_throwsUnprocessable`, `getFollowers_privateProfileNonOwner_returns404_antiOracle`, `getPublicProfile_self_followStatusIsNull`, `getPublicProfile_authNonOwnerWithPending_followStatusIsPending`, `prePersist_setsCreatedAt`, `post_eventDraftByNonCreator_returns404_antiOracle`, `post_eventBanned_returns404_antiOracle`, `post_replyToReply_returns422_repliesTooDeep`, `post_parentInOtherEvent_returns422_parentNotInEvent`, `post_unknownParent_returns404_parentNotFound`, `delete_byPendingCoOrganizer_returns403`, `delete_unknownComment_returns404_commentNotFound`.
**Coverage actuel** : 0/35 sentinels exécutés (donc 0 anti-oracle 404, 0 vérif rate-limit, 0 contrat de cascade `delete_parent`).
**Coverage cible** : 35/35 (régression non négociable — la doc PR/Sprint cite ces noms comme garants des règles métier).
**Effort estimé** : XL
**Fix suggéré** : porter `git show 41074e9:backend/services/legacy-monolith/src/test/java/...` vers les services correspondants (Recurrence → event-service, Follow → follow-service, Comment → comment-service, CoOrganizer → co-organizer-service).
**Dépendances** : nécessite que les Mock/Stub cross-service soient en place (cf. TEST-018) ; la `@QuarkusTest` Postgres profile doit être branchée par service.

### TEST-002 [CRITICAL, owner=backend, effort=XL]
**Titre** : event-service : 6.4 % lignes, 0 % branches — `EventService` (1500+ lignes) entièrement non couvert
**Service / classe / méthode** : `backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java`, `EventResource.java`, `EventSearchService.java`, `EventSearchResource.java`, `AdminEventResource.java`, `RecurrenceGenerator.java`, `EventExpirationService.java`, `EventExpirationJob.java`, `FeaturedService.java`
**Ce qui manque** : `EventServiceCoverageTest` legacy contenait **296 `@Test`** (2219 LOC) + `RecurrenceGeneratorTest` 13 `@Test` + `EventResourceTest` ~80 `@Test` + `AdminEventResourceTest` + `EventSearchResourceTest` + `EventStatsServiceCoverageTest` + `EventCoOrganizerServiceCoverageTest`. Aucun n'a été porté. Les seuls tests présents sont `EventLifecycleEventTest` + `EventLifecyclePublisherTest` (kafka DTO/publisher, ~7 cas) + sentinel.
**Coverage actuel** : LINE 49/764 (6.4 %) ; BRANCH 0/290 (0 %) ; CLASS 13/46.
**Coverage cible** : ≥ 80 % lines / ≥ 70 % branches (objectif Sonar interne, désactivé ici par exclusion).
**Effort estimé** : XL
**Fix suggéré** : porter en priorité `legacy-monolith/src/test/java/ch/unige/events/util/RecurrenceGeneratorTest.java` (pure JUnit, aucune dépendance) puis `service/EventServiceCoverageTest.java` (DB-backed, profile Postgres déjà en place dans event-service).
**Dépendances** : `EventCoOrganizerStub`, `AttendanceStub`, `UserStub`, `FavoriteStub`, `EventViewStub` doivent être instanciables en test (ils existent déjà dans `src/main`).

### TEST-003 [CRITICAL, owner=backend, effort=L]
**Titre** : event-service : `RecurrenceGenerator` (algorithme métier pur) entièrement non testé
**Service / classe / méthode** : `event-service/src/main/java/ch/unige/events/event/util/RecurrenceGenerator.java`
**Ce qui manque** : `RecurrenceGeneratorTest` legacy (13 `@Test`, 167 LOC, sentinels SCRUM-147 `weekly_4Occurrences_returns3DatesSpacedBy7Days`, `monthly_handlesShortFebruaryFromJanuary31`, `bothNull_throwsIllegalArgumentException`, `maxOccurrencesAbove52_cappedTo52`). Classe ré-incluse dans le service mais pas son test (alors qu'il est sans dépendance Quarkus → portage trivial).
**Coverage actuel** : 0 % lines (la classe n'apparaît dans `<class>` du jacoco que parce qu'elle est instanciée dans `EventService` via init, mais aucun appel productif).
**Coverage cible** : 100 % lines (pure logic, pas de raison de descendre sous 100 %).
**Effort estimé** : L (~2 h, copier-coller-renommer package).
**Fix suggéré** : `git show 41074e9:backend/services/legacy-monolith/src/test/java/ch/unige/events/util/RecurrenceGeneratorTest.java > services/event-service/src/test/java/ch/unige/events/event/util/RecurrenceGeneratorTest.java` puis adapter le package import.
**Dépendances** : aucune.

### TEST-004 [CRITICAL, owner=backend, effort=XL]
**Titre** : user-service : 5.6 % lignes, 0 % branches — `UserService` + `UserResource` (S3 + image) non couverts
**Service / classe / méthode** : `user-service/src/main/java/ch/unige/events/user/service/UserService.java`, `UserResource.java`
**Ce qui manque** : `UserServiceCoverageTest` legacy (avec `getPublicProfile_self_followStatusIsNull`, `getPublicProfile_authNonOwnerWithPending_followStatusIsPending`, anti-oracle 404 ISSUE-93) + `UserResourceTest` + `UserServiceMockConcurrencyTest` (race condition concurrent profile creation) + image upload tests portés depuis `41074e9` (cf. commit message qui ajoute s3 mais pas les tests).
**Coverage actuel** : LINE 18/324 (5.6 %), BRANCH 0/123. La règle anti-oracle 404 (ISSUE-93) n'est plus protégée par aucun test.
**Coverage cible** : ≥ 80 % lines, **100 % sur le branch anti-oracle** (sécurité).
**Effort estimé** : XL
**Fix suggéré** : porter les 4 fichiers de test legacy ; ajouter spécifiquement un test S3 pour le flux `POST /users/me/image` introduit dans `41074e9` (actuellement 0 ligne testée).
**Dépendances** : MinIO test container ou LocalStack + `@QuarkusTestResource`.

### TEST-005 [CRITICAL, owner=backend, effort=L]
**Titre** : follow-service : 3.3 % lignes — pire ratio du reactor, sentinels SCRUM-138 perdus
**Service / classe / méthode** : `follow-service/src/main/java/ch/unige/events/follow/service/FollowService.java`, `FollowResource.java`, `FollowRequestResource.java`
**Ce qui manque** : `FollowServiceCoverageTest` legacy (65 `@Test`, 460 LOC) avec sentinels `findAcceptedFollowedIds_returnsOnlyAcceptedUuids`, `rejectRequest_followerCanReFollowAfterReject`, `follow_selfFollow_throwsUnprocessable`, `getFollowers_privateProfileNonOwner_returns404_antiOracle`. Règles métier non testées : auto-accept profil public vs. PENDING profil privé, 422 `cannot_follow_self`, 409 `already_following`, 403 sur accept/reject par non-target, 409 `invalid_transition`, idempotence DELETE.
**Coverage actuel** : LINE 7/213 (3.3 %), BRANCH 0/66 (0 %). Aucune règle Follow n'est exercée.
**Coverage cible** : ≥ 90 % lines (objectif sprint 6 documenté à 100 % — cf. sprint-context.md ligne 584).
**Effort estimé** : L (DB-backed Postgres, mais le service est isolé)
**Fix suggéré** : porter `FollowServiceCoverageTest.java` legacy + écrire des tests REST pour les deux Resources splittées.
**Dépendances** : `UserStub` (déjà présent dans follow-service `src/main`) ; profile test Postgres.

### TEST-006 [CRITICAL, owner=backend, effort=L]
**Titre** : comment-service : 7.3 % lignes — sentinels SCRUM-144 (anti-oracle, replies depth, parent-not-in-event) absents
**Service / classe / méthode** : `comment-service/src/main/java/ch/unige/events/comment/service/CommentService.java`, `CommentResource.java`, `CommentDirectResource.java`
**Ce qui manque** : `CommentServiceCoverageTest` legacy (93 `@Test`, 633 LOC) + `CommentResourceTest` + `CommentDirectResourceTest`. Sentinels listés sprint-context.md L542-556 : `prePersist_setsCreatedAt`, `post_eventDraftByNonCreator_returns404_antiOracle`, `post_eventBanned_returns404_antiOracle`, `post_replyToReply_returns422_repliesTooDeep` (règle profondeur=2), `post_parentInOtherEvent_returns422_parentNotInEvent`, `post_unknownParent_returns404_parentNotFound`, `delete_byAuthor_removesRow`, `delete_byEventCreator_removesRow`, `delete_byAcceptedCoOrganizer_removesRow`, `delete_byPendingCoOrganizer_returns403`, `delete_byThirdParty_returns403`, `delete_byAdmin_removesRow`, `delete_unknownComment_returns404_commentNotFound`, `get_anonymousOnPublished_returnsList`. Aucun n'est protégé. La règle de profondeur (replies max=2) et l'isolation parent-event ne sont pas vérifiées.
**Coverage actuel** : LINE 17/233 (7.3 %), BRANCH 0/110.
**Coverage cible** : ≥ 90 % lines + **100 % sur les anti-oracle 404** (sécurité).
**Effort estimé** : L
**Fix suggéré** : porter les 3 tests legacy.
**Dépendances** : `EventStub`, `UserStub`, `EventCoOrganizerStub` (déjà présents).

### TEST-007 [CRITICAL, owner=backend, effort=M]
**Titre** : co-organizer-service : 19 % lignes — pas de test pour le state-machine PENDING/ACCEPTED/DECLINED
**Service / classe / méthode** : `co-organizer-service/src/main/java/ch/unige/events/coorganizer/service/EventCoOrganizerService.java` (+ resource)
**Ce qui manque** : `EventCoOrganizerServiceCoverageTest` legacy (cf. `legacy-monolith` listing). Aucune transition d'état testée.
**Coverage actuel** : LINE 37/194 (19 %), BRANCH 0/54 (0 %). Les 54 branches couvrent transitions invalides + 403 par tiers + cascade décline ⇒ DELETE.
**Coverage cible** : ≥ 85 % lines, ≥ 80 % branches.
**Effort estimé** : M
**Fix suggéré** : porter `EventCoOrganizerServiceCoverageTest.java` + `EventCoOrganizerResourceTest.java`.
**Dépendances** : aucune.

### TEST-008 [CRITICAL, owner=backend, effort=M]
**Titre** : attendance-service : 13.6 % lignes — `AttendanceService.getAttendees` (fix UUID Sprint 7) non testé
**Service / classe / méthode** : `attendance-service/src/main/java/ch/unige/events/attendance/service/AttendanceService.java`, `AttendanceDTO.java`
**Ce qui manque** : `AttendanceServiceCoverageTest` legacy + sentinel Sprint 7 (`AttendanceDTO` enrichi `displayName`/`avatarUrl`, `getAttendees` batch sans N+1, factory `from(Attendance, User)` orpheline-safe). Aucun de ces 3 cas n'est porté.
**Coverage actuel** : LINE 37/273 (13.6 %), BRANCH 0/98.
**Coverage cible** : ≥ 80 % lines.
**Effort estimé** : M
**Fix suggéré** : porter `AttendanceServiceCoverageTest.java` + `AttendanceResourceTest.java`.

### TEST-009 [CRITICAL, owner=backend, effort=M]
**Titre** : report-service : 15.8 % lignes — modération + cleanup non couverts
**Service / classe / méthode** : `report-service/src/main/java/ch/unige/events/report/service/ReportService.java`, `ModerationCleanupService.java`
**Ce qui manque** : `ReportServiceCoverageTest` + `ModerationCleanupServiceTest` + `ModerationCleanupCoverageTest` + `AdminReportResourceTest` legacy.
**Coverage actuel** : LINE 26/165 (15.8 %), BRANCH 0/56.
**Coverage cible** : ≥ 80 % lines (les règles de modération doivent être protégées).
**Effort estimé** : M

### TEST-010 [MAJOR, owner=backend, effort=M]
**Titre** : favorite-service : 19 % lignes
**Service / classe / méthode** : `favorite-service/src/main/java/ch/unige/events/favorite/service/FavoriteService.java`, `FavoriteResource.java`
**Ce qui manque** : `FavoriteServiceCoverageTest` + `FavoriteResourceTest` legacy.
**Coverage actuel** : LINE 33/172 (19 %), BRANCH 0/34.
**Coverage cible** : ≥ 80 %.
**Effort estimé** : M.

### TEST-011 [MAJOR, owner=backend, effort=M]
**Titre** : calendar-service : 12.3 % lignes — builder ICS non testé
**Service / classe / méthode** : `calendar-service/src/main/java/ch/unige/events/calendar/util/IcsBuilder.java`, `CalendarService.java`, `CalendarResource.java`
**Ce qui manque** : `IcsBuilderTest` (legacy, sentinels formatage RFC 5545) + `CalendarServiceCoverageTest` + `CalendarResourceTest`. Le builder ICS est de la pure logique format → portage trivial (équivalent RecurrenceGenerator).
**Coverage actuel** : LINE 13/106 (12.3 %), BRANCH 0/16.
**Coverage cible** : 100 % lines pour `IcsBuilder` (logique pure), ≥ 80 % pour le reste.
**Effort estimé** : M.

### TEST-012 [MAJOR, owner=backend, effort=S]
**Titre** : view-service : 17.9 % lignes — comptage de vues non testé
**Service / classe / méthode** : `view-service/src/main/java/ch/unige/events/view/service/EventViewService.java`, `EventViewResource.java`
**Ce qui manque** : `EventViewServiceCoverageTest` + `EventViewResourceTest` legacy.
**Coverage actuel** : LINE 5/28 (17.9 %), BRANCH 0/2.
**Coverage cible** : ≥ 80 %.
**Effort estimé** : S (peu de code).

### TEST-013 [MAJOR, owner=backend, effort=S]
**Titre** : stats-service : 36.6 % lignes (relativement le moins pire mais branches=0)
**Service / classe / méthode** : `stats-service/src/main/java/ch/unige/events/stats/service/EventStatsService.java`, `EventStatsResource.java`
**Ce qui manque** : `EventStatsServiceCoverageTest` + `EventStatsResourceTest` legacy.
**Coverage actuel** : LINE 15/41 (36.6 %), BRANCH 0/10.
**Coverage cible** : ≥ 80 % lines, ≥ 70 % branches.
**Effort estimé** : S.

### TEST-014 [MAJOR, owner=backend, effort=S]
**Titre** : share-service : 10 % lignes — partage email non testé
**Service / classe / méthode** : `share-service/src/main/java/ch/unige/events/share/service/ShareService.java`
**Ce qui manque** : tests pour le flux Mailer Quarkus.
**Coverage actuel** : LINE 3/30 (10 %), BRANCH 0/4.
**Coverage cible** : ≥ 80 %.
**Effort estimé** : S.

### TEST-015 [MAJOR, owner=backend, effort=M]
**Titre** : event-service : `EventLifecyclePublisher` test ne couvre pas la sérialisation Kafka end-to-end
**Service / classe / méthode** : `event-service/src/test/java/ch/unige/events/event/kafka/EventLifecyclePublisherTest.java`
**Ce qui manque** : Le test existe (rare bonne nouvelle) mais branch 0 % sur la classe (cf. jacoco) — sans doute cas de désérialisation et erreur Kafka non testés. Ajouter un cas avec broker indisponible (DLQ ou retry), un cas avec payload invalide, un cas avec partition key.
**Coverage actuel** : test présent, branches business à confirmer dans le rapport HTML.
**Coverage cible** : ≥ 80 % branches.
**Effort estimé** : M.

### TEST-016 [MAJOR, owner=backend, effort=L]
**Titre** : me-aggregator-service : 40 % lignes — fan-out HTTP cross-service non testé en erreur
**Service / classe / méthode** : `me-aggregator-service/src/main/java/...` (les 11 classes)
**Ce qui manque** : Le service agrège plusieurs `*-service` REST clients ; aucun test ne simule un downstream KO/timeout. La résilience au fan-out partiel est non vérifiée (risque : panne d'un seul service KO ⇒ `/me` global KO).
**Coverage actuel** : LINE 32/80 (40 %), BRANCH 0/12.
**Coverage cible** : ≥ 80 % branches (les error paths sont la valeur du test).
**Effort estimé** : L (`@QuarkusTest` + `WireMock` ou `@InjectMock` sur les clients).
**Dépendances** : aucun mock cross-service formalisé (cf. TEST-018).

### TEST-017 [MINOR, owner=backend, effort=S]
**Titre** : notification-service : stub à 1 fichier — incohérence avec le scope SCRUM-99 documenté
**Service / classe / méthode** : `notification-service/src/main/java/ch/unige/events/notification/ServiceIdentityResource.java` (seule classe métier)
**Ce qui manque** : Le service est livré comme scaffold (1 src + 1 test sentinel). Si SCRUM-99 (infra Notification) est hors scope de la PR, documenter le placeholder dans le README du module ; sinon porter le code.
**Coverage actuel** : sentinel seul ; jacoco non généré (pas de classes business).
**Coverage cible** : N/A jusqu'à SCRUM-99.
**Effort estimé** : S (clarification doc).

### TEST-018 [MAJOR, owner=backend, effort=L]
**Titre** : Stubs cross-service (`UserStub`, `EventStub`, `EventCoOrganizerStub`, `FavoriteStub`, `AttendanceStub`, `EventViewStub`, `FollowStub`) non couverts par jacoco — pré-requis pour porter les tests legacy
**Service / classe / méthode** : `*-service/src/main/java/.../entity/*Stub.java` (≈ 7 stubs dupliqués entre services)
**Ce qui manque** : Les `*Stub` sont des mini-entités JPA partagées par convention de nommage (cf. `EventCoOrganizerStub.java`, `UserStub.java` présents dans event-service, follow-service, comment-service, etc.). Aucun service ne teste ces stubs ; aucun `*ServiceMock` formel n'existe pour découpler les tests cross-service. La doc sprint-context cite explicitement `CommentServiceMock`, `AttendanceServiceMock`, `FavoriteServiceMock` dans des `ShareServiceCoverageProfile` exclusion lists — ces classes mock semblent ne plus exister.
**Coverage actuel** : 0 sur les Stub.
**Coverage cible** : portage des tests legacy bloqué tant que la stratégie Stub/Mock cross-service n'est pas re-formalisée.
**Effort estimé** : L (refactor architectural prérequis).
**Fix suggéré** : extraire un module `shared-test-stubs` (ou par-service `<scope>test</scope>`) qui regroupe `*Stub` + `*ServiceMock` ; cf. dépendance pour TEST-001 → TEST-014.

---

## Annexe — sentinels documentés et leur statut

`grep -rnE "void <name>" /workspace/backend/services/*/src/test` → **0 hits** sur tous les noms ci-dessous.

| Sentinel (ticket) | Présent ? | Localisation attendue | Statut |
|---|---|---|---|
| `weekly_4Occurrences_returns3DatesSpacedBy7Days` (SCRUM-147) | NON | event-service `RecurrenceGeneratorTest` | perdu — fichier legacy non porté |
| `monthly_handlesShortFebruaryFromJanuary31` (SCRUM-147) | NON | event-service `RecurrenceGeneratorTest` | perdu |
| `bothNull_throwsIllegalArgumentException` (SCRUM-147) | NON | event-service `RecurrenceGeneratorTest` | perdu |
| `maxOccurrencesAbove52_cappedTo52` (SCRUM-147) | NON | event-service `RecurrenceGeneratorTest` | perdu |
| `from_parentRecurringEvent_exposesRecurrenceRuleAndNullParentEventId` (SCRUM-147) | NON | event-service `EventDTOTest` | perdu |
| `from_occurrenceEvent_exposesParentEventIdAndNullRecurrenceRule` (SCRUM-147) | NON | event-service `EventDTOTest` | perdu |
| `createRecurring_weekly4Occurrences_persists1ParentAnd3Children` (SCRUM-147) | NON | event-service `EventServiceCoverageTest` | perdu |
| `createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded` (SCRUM-147) | NON | event-service `EventResourceTest` | perdu |
| `createRecurring_endDateBeforeStart_returns400_recurrenceEndBeforeStart` (SCRUM-147) | NON | event-service `EventResourceTest` | perdu |
| `createRecurring_inheritsParentStatusPublished` (SCRUM-147) | NON | event-service | perdu |
| `getOccurrences_parentRecurring_returnsChildrenSortedAsc` (SCRUM-147) | NON | event-service `EventResourceTest` | perdu |
| `getOccurrences_standaloneEvent_returns200EmptyList` (SCRUM-147) | NON | event-service | perdu |
| `getOccurrences_draftByNonCreator_returns404_antiOracle` (SCRUM-147) | NON | event-service | perdu — anti-oracle régression sécurité |
| `update_parentTitle_doesNotPropagateToOccurrences` (SCRUM-147) | NON | event-service | perdu |
| `cancel_parentDoesNotCascadeToOccurrences` (SCRUM-147) | NON | event-service | perdu |
| `delete_parent_setsOccurrencesParentEventIdToNull` (SCRUM-147) | NON | event-service | perdu |
| `post_validRecurrenceWeekly_returns201_recurrenceRuleSetOnParent` (SCRUM-147) | NON | event-service | perdu |
| `post_recurrenceMaxOccurrences53_returns400_beanValidation` (SCRUM-147) | NON | event-service | perdu |
| `getOccurrences_parentPublishedAnonymous_returns200` (SCRUM-147) | NON | event-service | perdu |
| `getOccurrences_sizeOver52_returns400` (SCRUM-147) | NON | event-service | perdu |
| `getOccurrences_draftByAnonymous_returns404_antiOracle` (SCRUM-147) | NON | event-service | perdu — anti-oracle |
| `findAcceptedFollowedIds_returnsOnlyAcceptedUuids` (SCRUM-138/168) | NON | follow-service `FollowServiceCoverageTest` | perdu — bloque SCRUM-168 |
| `rejectRequest_followerCanReFollowAfterReject` (SCRUM-138) | NON | follow-service | perdu |
| `follow_selfFollow_throwsUnprocessable` (SCRUM-138) | NON | follow-service | perdu |
| `getFollowers_privateProfileNonOwner_returns404_antiOracle` (SCRUM-138) | NON | follow-service | perdu — anti-oracle |
| `getPublicProfile_self_followStatusIsNull` (SCRUM-138) | NON | follow-service | perdu |
| `getPublicProfile_authNonOwnerWithPending_followStatusIsPending` (SCRUM-138) | NON | follow-service | perdu |
| `prePersist_setsCreatedAt` (SCRUM-144) | NON | comment-service `CommentTest` | perdu |
| `post_eventDraftByNonCreator_returns404_antiOracle` (SCRUM-144) | NON | comment-service | perdu — anti-oracle |
| `post_eventBanned_returns404_antiOracle` (SCRUM-144) | NON | comment-service | perdu |
| `post_replyToReply_returns422_repliesTooDeep` (SCRUM-144) | NON | comment-service | perdu — règle profondeur 2 |
| `post_parentInOtherEvent_returns422_parentNotInEvent` (SCRUM-144) | NON | comment-service | perdu — isolation event |
| `post_unknownParent_returns404_parentNotFound` (SCRUM-144) | NON | comment-service | perdu |
| `delete_byPendingCoOrganizer_returns403` (SCRUM-144) | NON | comment-service | perdu — autorisation |
| `delete_unknownComment_returns404_commentNotFound` (SCRUM-144) | NON | comment-service | perdu |

---

## Conclusion (résumé exécutif)

PR #158 a réussi l'extraction structurelle (14 services + 2 libs Maven) mais **perd 1818 méthodes `@Test` du commit `41074e9` sans en réécrire aucune**. Le seul filet restant est un sentinel `ServiceIdentityResourceTest` par service (1-2 assertions), insuffisant pour protéger les règles métier (anti-oracles 404, rate-limiting, recurrence math, depth=2 replies, follow state machine, état co-organizer). Le gate Sonar passe artificiellement grâce à `<sonar.coverage.exclusions>services/*-service/**/*` (parent POM L88-89). Les shared libs, **non exclues**, sont 100 % — confirme que le pipeline jacoco fonctionne, le problème est l'absence de tests, pas l'outillage.

**Action immédiate requise** : porter au minimum les 4 fichiers cités dans TEST-001 (RecurrenceGeneratorTest, EventServiceCoverageTest, FollowServiceCoverageTest, CommentServiceCoverageTest) pour récupérer les 35 sentinels documentés avant merge. Le reste peut être planifié sur sprint dédié, mais bloquer la PR sur ces 4 portages est défensible.


## Catégorie 5 — Kafka producers / consumers (9 findings)


## Summary
- **9 findings** (1 BLOCKER, 4 HIGH, 3 MEDIUM, 1 LOW)
- **Topics** : 10 attendus / 10 provisionnés / 0 manquants côté chart (mais 7/10 sans aucun producteur câblé, 1/10 sans son consumer câblé)
- **Producteurs livrés** : 1/5 services attendus (event-service uniquement, 3 channels sur 4 attendus pour ce service — il manque le consumer côté event-service pour `events.banned`)
- **Consommateurs livrés** : 0/1 (le seul consumer S8 attendu — event-service sur `events.banned` — n'existe pas)
- **Pattern soft-extraction half-shipped** : le commentaire `// once event-service ships` dans `ModerationCleanupService.java:70` et `ReportService.java:122` confirme que la migration vers Kafka est planifiée mais pas livrée — `EventStub` reste writable et `report.event.status = EventStatus.BANNED` écrit toujours en JPA cross-schema.

## Tableau récap

| Topic | Producteur attendu | Producteur livré ? | Consommateur attendu | Consommateur livré ? | Findings |
|---|---|---|---|---|---|
| `events.published` | event-service | ✅ `EventLifecyclePublisher#published` (commit `5dce9be`) | notification-service (futur), stats-service | ❌ hors scope S8 (notif) — pas de consumer stats-service non plus | — |
| `events.cancelled` | event-service | ✅ `EventLifecyclePublisher#cancelled` | notification-service (futur) | ❌ hors scope S8 | — |
| `events.expired` | event-service (`EventExpirationJob`) | ✅ `EventLifecyclePublisher#expired` | notification-service (futur) | ❌ hors scope S8 | — |
| `events.banned` | **report-service** | ❌ TODO laissé en code | **event-service** (apply state change) + notification (futur) | ❌ pas de `@Incoming` | KAFKA-001, KAFKA-002 |
| `users.followed` | follow-service | ❌ pas même la dep Maven | notification-service (futur) | ❌ hors scope S8 | KAFKA-003 |
| `users.follow-requested` | follow-service | ❌ idem | notification-service (futur) | ❌ hors scope S8 | KAFKA-003 |
| `users.follow-accepted` | follow-service | ❌ idem | notification-service (futur) | ❌ hors scope S8 | KAFKA-003 |
| `comments.created` | comment-service | ❌ pas la dep Maven | notification-service (futur) | ❌ hors scope S8 | KAFKA-004 |
| `co-organizers.invited` | co-organizer-service | ❌ pas la dep Maven | notification-service (futur) | ❌ hors scope S8 | KAFKA-005 |
| `co-organizers.accepted` | co-organizer-service | ❌ idem | notification (futur) + event-service (cache) | ❌ | KAFKA-005 |

## Findings

### KAFKA-001 [BLOCKER, report-service, M]
**Titre** : `events.banned` non produit — soft-extraction laissée à mi-chemin
**Service / topic concerné** : report-service → `events.banned`
**Spec attendue** : § 4.5 — `report-service` producteur, payload `{eventId, bannedBy, reason, bannedAt}`, clé `eventId`
**Réalité actuelle** :
- `services/report-service/src/main/java/ch/unige/events/report/service/ReportService.java:122-124` mute encore `report.event.status = EventStatus.BANNED` via `EventStub` (JPA cross-schema)
- `services/report-service/src/main/java/ch/unige/events/report/service/ModerationCleanupService.java:69-70` idem + commentaire explicite `TODO: emit events.banned Kafka message once event-service ships`
- `services/report-service/pom.xml` ne contient **pas** `quarkus-messaging-kafka`
- `services/report-service/src/main/resources/application.properties` n'a aucune config `mp.messaging.outgoing.events-banned.*`
- `EventStub.java` reste `@Entity` writable en lieu et place de l'event Kafka
**Conséquence** :
1. report-service reste couplé au schéma `event_svc.events` via JPA — viole la frontière transactionnelle de la spec § 4 (§ décision 8 « ROLE par service »)
2. Si event-service sécurise son schéma (REVOKE UPDATE sur `event_svc.events` pour `report_role`) → report-service crash
3. notification-service ne pourra jamais s'abonner à `events.banned` (le topic est créé mais vide)
**Fix suggéré** :
1. Ajouter au `pom.xml` de report-service : `quarkus-messaging-kafka` (compile) + `smallrye-reactive-messaging-in-memory` (test)
2. Créer `services/report-service/src/main/java/ch/unige/events/report/kafka/EventBannedEvent.java` (record `{long eventId, UUID bannedBy, String reason, Instant bannedAt}`)
3. Créer `EventBannedPublisher.java` (Emitter `@Channel("events-banned")`, méthode `banned(eventId, bannedBy, reason)`, log+swallow comme `EventLifecyclePublisher`)
4. Wirer dans `ReportService#handle` (ligne 124) et `ModerationCleanupService#runCleanup` (ligne 69) — remplacer le mutate JPA par `publisher.banned(...)`
5. Supprimer le champ `status` de `EventStub.java` (devient read-only) — ou supprimer entièrement `EventStub` et passer par REST GET event-service
6. Ajouter dans `application.properties` : 4 lignes `mp.messaging.outgoing.events-banned.{connector,topic}` + `%test.mp.messaging.outgoing.events-banned.connector=smallrye-in-memory`
7. Tests : porter `EventLifecyclePublisherTest` + `EventLifecycleEventTest`
**Effort** : M (≈ 4-6h, modèle existe, à recopier)
**Dépendances** : ⚠️ À synchroniser avec KAFKA-002 (livraison conjointe sinon le ban est silencieux)

### KAFKA-002 [BLOCKER, event-service, S]
**Titre** : `events.banned` non consommé — pas de `@Incoming` dans event-service
**Service / topic concerné** : event-service ← `events.banned`
**Spec attendue** : § 4.5 — `event-service` consommateur (« applique le state change »), idempotent at-least-once
**Réalité actuelle** : aucun fichier `*Consumer*`, `*Listener*`, ni `@Incoming` dans `services/event-service/src/main/java`. La config `application.properties` n'a aucune ligne `mp.messaging.incoming.*`. Le pom contient bien `quarkus-messaging-kafka` (déjà là pour les producers) mais rien ne consomme.
**Conséquence** : Tant que KAFKA-001 ne ship pas, c'est latent. Mais si KAFKA-001 ship sans KAFKA-002 → un ban admin n'a **plus aucun effet** sur `event_svc.events.status` (le row reste PUBLISHED). Régression métier majeure : événement banni reste visible publiquement.
**Fix suggéré** :
1. Créer `services/event-service/src/main/java/ch/unige/events/event/kafka/EventBannedConsumer.java` avec `@Incoming("events-banned")` + `@Transactional`
2. Le consumer fait `Event event = Event.findById(payload.eventId()); event.status = EventStatus.BANNED;` + idempotence guard (no-op si déjà BANNED)
3. Récupérer le record `EventBannedEvent` depuis le producer — soit duplication (S8 acceptable), soit lib partagée (cf. KAFKA-007)
4. Ajouter `mp.messaging.incoming.events-banned.{connector,topic}` + `%test.mp.messaging.incoming.events-banned.connector=smallrye-in-memory`
5. Test consumer avec `@InjectConnector InMemoryConnector connector`
**Effort** : S (≈ 2h)
**Dépendances** : KAFKA-001 (sinon rien à consommer) ; KAFKA-007 (payload partagé)

### KAFKA-003 [HIGH, follow-service, M]
**Titre** : 3 producteurs `users.{followed,follow-requested,follow-accepted}` non livrés
**Service / topic concerné** : follow-service → 3 topics
**Spec attendue** : § 4.5 — `follow-service` producteur ; clés `followedId`/`followerId` ; payload `{followerId, followedId, status, createdAt}`
**Réalité actuelle** : `services/follow-service/pom.xml` ne contient pas `quarkus-messaging-kafka`. Aucun fichier kafka, aucune config `mp.messaging.*`, aucun TODO. Le service ne sait littéralement pas que ces topics existent.
**Conséquence** : notification-service (S9) ne pourra pas s'abonner aux follow events. Les 3 topics provisionnés par `topics-init.yaml` resteront vides.
**Fix suggéré** :
1. Ajouter `quarkus-messaging-kafka` + `smallrye-reactive-messaging-in-memory` (test) au pom
2. Créer `services/follow-service/src/main/java/ch/unige/events/follow/kafka/FollowLifecycleEvent.java` (record commun avec discriminator `Type {FOLLOWED, REQUESTED, ACCEPTED}`)
3. Créer `FollowLifecyclePublisher.java` avec 3 Emitters `@Channel("users-followed")`, `@Channel("users-follow-requested")`, `@Channel("users-follow-accepted")`
4. Wirer dans `FollowService` : `follow()` (auto-accept public profile → `users.followed` ; sinon → `users.follow-requested`), `acceptRequest()` (→ `users.follow-accepted`)
5. Ajouter 12 lignes de config `mp.messaging.outgoing.users-{followed,follow-requested,follow-accepted}.*` dans `application.properties`
6. Porter tests `EventLifecyclePublisherTest` (3 channels au lieu de 3)
**Effort** : M (≈ 4-5h)
**Dépendances** : —

### KAFKA-004 [HIGH, comment-service, S]
**Titre** : `comments.created` non produit
**Service / topic concerné** : comment-service → `comments.created`
**Spec attendue** : § 4.5 — payload `{commentId, eventId, authorId, parentCommentId, createdAt}`, clé `eventId`
**Réalité actuelle** : `services/comment-service/pom.xml` sans dep kafka, aucun publisher, aucune config.
**Conséquence** : notification « X a commenté votre event » impossible. Topic vide.
**Fix suggéré** : porter le pattern (1 Emitter, 1 record, wirer dans `CommentService#postComment`). Effort réduit car 1 seul channel.
**Effort** : S (≈ 2h)
**Dépendances** : —

### KAFKA-005 [HIGH, co-organizer-service, S]
**Titre** : `co-organizers.{invited,accepted}` non produits
**Service / topic concerné** : co-organizer-service → 2 topics
**Spec attendue** : § 4.5 — `invited` payload `{eventId, userId, invitedAt}` clé `userId` ; `accepted` payload `{eventId, userId, acceptedAt}` clé `eventId` (consommateurs : notification-service + event-service pour cache)
**Réalité actuelle** : pas de dep, pas de publisher, pas de config.
**Conséquence** : 2 topics vides ; spec mentionne aussi event-service comme consommateur (cache) — donc à terme il y a un consumer event-service à prévoir.
**Fix suggéré** : 1 record discriminé `CoOrganizerEvent {Type {INVITED, ACCEPTED}, eventId, userId, occurredAt}`, 1 Publisher 2 Emitters, wirer dans `CoOrganizerService#invite` (POST) et `#accept` (PATCH).
**Effort** : S (≈ 2-3h)
**Dépendances** : —

### KAFKA-006 [MEDIUM, notification-service, S]
**Titre** : Placeholder notification-service ne déclare pas encore ses futurs consumers
**Service / topic concerné** : notification-service ← 9 topics (tous sauf `events.banned` qui est event-service)
**Spec attendue** : § 4.5 — fan-in de 9 topics ; SCRUM-99 (hors scope S8 explicitement)
**Réalité actuelle** : `services/notification-service/` contient uniquement `ServiceIdentityResource.java` + un test sentinel ; le pom liste les futures deps en commentaire (lignes 22-23) mais ne les inclut pas. Acceptable — la spec marque notification-service comme « futur ».
**Conséquence** : à documenter clairement comme « hors scope S8 » dans la PR description pour qu'un audit futur n'interprète pas ça comme un oubli.
**Fix suggéré** : ajouter dans le `pom.xml` un commentaire `<!-- SCRUM-99: 9 @Incoming consumers for events.{published,cancelled,expired,banned}, users.{followed,follow-requested,follow-accepted}, comments.created, co-organizers.{invited,accepted} -->` + un `package-info.java` listant la dette technique.
**Effort** : XS (15 min)
**Dépendances** : KAFKA-001..005 (les producers doivent shipper avant que les consumers aient quelque chose à consommer)

### KAFKA-007 [MEDIUM, parent / shared lib, M]
**Titre** : Pas de lib partagée pour les payloads Kafka — risque de drift quand les consumers arriveront
**Service / topic concerné** : transverse
**Spec attendue** : § décision 26 — pas de Schema Registry mais convention « ajouter des champs OK, retirer/renommer KO » + contract tests Pact (décision 18). Implicite : il faut un endroit pour partager les records entre producer et consumer.
**Réalité actuelle** : `EventLifecycleEvent` vit dans `services/event-service/src/main/java/ch/unige/events/event/kafka/` — package interne. `services/shared-rate-limit/` et `services/shared-storage/` existent comme libs partagées mais pas de `shared-kafka-payloads/` ou équivalent. Quand event-service consommera `events.banned` (KAFKA-002), il devra soit : (a) dupliquer le record (drift garanti), (b) dépendre de report-service (anti-pattern), (c) ajouter une lib commune.
**Conséquence** : à chaque ajout de consumer (event-service sur banned, notification-service sur tous), copy-paste du record → divergence quand un champ est ajouté. Le risque § 27 « Drift de schéma Kafka » devient effectif.
**Fix suggéré** : créer `services/shared-kafka-events/` (module Maven, packaging jar) qui exporte les 5 records (`EventLifecycleEvent`, `EventBannedEvent`, `FollowLifecycleEvent`, `CommentCreatedEvent`, `CoOrganizerEvent`). Chaque service déclare `<dependency>shared-kafka-events</dependency>`. Décision possible alternative S8 : laisser dupliqué et tracer la dette dans `architecture.md`.
**Effort** : M (≈ 3h pour créer le module + migrer les imports event-service)
**Dépendances** : —

### KAFKA-008 [MEDIUM, event-service, XS]
**Titre** : Sérialiseur Jackson implicite — pas de `value.serializer` explicite
**Service / topic concerné** : event-service (et tout futur producer)
**Spec attendue** : § décision 26 — Jackson sans Schema Registry, convention sur les payloads
**Réalité actuelle** : `services/event-service/src/main/resources/application.properties` configure `connector` et `topic` mais aucune ligne `mp.messaging.outgoing.events-published.value.serializer=...`. Quarkus auto-détecte `ObjectMapperSerializer` quand `quarkus-messaging-kafka` + Jackson sont sur classpath — fonctionne mais n'est pas explicite. Aucun test ne vérifie le format JSON de la sortie binaire (les tests utilisent `InMemoryConnector` qui passe l'objet en mémoire sans sérialiser).
**Conséquence** : une futur upgrade de Quarkus qui change le default → drift silencieux. Un consumer non-Java (notification-service en TypeScript par exemple) n'a pas de garantie de schéma.
**Fix suggéré** : ajouter explicitement `mp.messaging.outgoing.events-{published,cancelled,expired}.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer` (3 lignes, 0 régression). Idem pour les futurs producers KAFKA-001/003/004/005. Optionnel : un test integration qui démarre Kafka via DevServices et asserte le JSON binaire pour 1 topic — sans-doute over-engineered pour S8.
**Effort** : XS (5 min sur les 4 services qui ont des producers à terme)
**Dépendances** : —

### KAFKA-009 [LOW, transverse, XS]
**Titre** : Duplication de `smallrye-reactive-messaging-in-memory` à venir dans 4 services
**Service / topic concerné** : event-service (déjà), report-service, follow-service, comment-service, co-organizer-service, notification-service (futur)
**Spec attendue** : § 4.5 + § décision 30 — pas de framework non-Quarkus mais standardisation des deps
**Réalité actuelle** : event-service déclare `<dependency><groupId>io.smallrye.reactive</groupId><artifactId>smallrye-reactive-messaging-in-memory</artifactId><scope>test</scope></dependency>`. Quand KAFKA-001/003/004/005 shipperont, chaque pom va dupliquer cette ligne.
**Conséquence** : 6 lignes dupliquées dans 6 poms. Mineur mais facile à éviter.
**Fix suggéré** : déplacer dans le `<dependencyManagement>` du parent `backend/pom.xml` — chaque service déclare juste `<dependency>` sans `<version>`. Ou mieux : ajouter dans `<dependencies>` du parent (toutes les services-modules en héritent), test scope.
**Effort** : XS (5 min, 1 commit refactor)
**Dépendances** : —

## Annexe — pattern de réplication EventLifecyclePublisher

Pour qu'un agent ajoute un producteur Kafka à un service `<svc>` (ex. follow-service pour KAFKA-003), suivre **exactement** le pattern de référence event-service (`services/event-service/src/main/java/ch/unige/events/event/kafka/`). Étapes à suivre dans cet ordre :

**1. POM (`services/<svc>/pom.xml`)**
Ajouter dans `<dependencies>` :
```xml
<dependency><groupId>io.quarkus</groupId><artifactId>quarkus-messaging-kafka</artifactId></dependency>
<dependency><groupId>io.smallrye.reactive</groupId><artifactId>smallrye-reactive-messaging-in-memory</artifactId><scope>test</scope></dependency>
```
Vérifier que `quarkus-rest-jackson` est déjà présent (il l'est dans tous les services existants).

**2. Record payload (`<svc>/kafka/<Domain>Event.java`)**
Java `record` immuable. Champs : la clé de partition (`eventId` / `userId`), un discriminator `enum Type` si plusieurs lifecycle states partagent le record, un `Instant occurredAt`. **Pas de DTO mutable, pas de field nullable sauf nécessité métier**. Méthodes factory statiques par Type (cf. `EventLifecycleEvent.published(...)`).

**3. Publisher (`<svc>/kafka/<Domain>Publisher.java`)**
- `@ApplicationScoped`
- 1 `Emitter<TheRecord>` par topic, injecté via constructeur avec `@Channel("<topic-channel-name>")`
- 1 méthode publique par lifecycle state (`published`, `cancelled`, etc.)
- Méthode privée `send()` qui **catch Exception et log warn — ne propage jamais**. Le DB row reste source of truth ; un crash Kafka ne doit pas rollback la transaction métier (cf. § décision 26 risque drift et § décision 27 risque crash Kafka — `acks=1`).
- Javadoc qui pointe explicitement la config `application.properties` et précise le comportement `%test`.

**4. Wiring service (`<svc>/service/<Foo>Service.java`)**
- `@Inject <Domain>Publisher publisher;`
- Appeler `publisher.xxx(...)` **après le commit DB** (pas dans la méthode `@Transactional` si l'ordre importe). Pour event-service, l'appel se fait après le `flush()` — voir `EventService.java` ligne 62 (`@Inject EventLifecyclePublisher`) et ses sites d'appel.

**5. Config (`application.properties`)**
Pour chaque channel `<chan>` :
```
mp.messaging.outgoing.<chan>.connector=smallrye-kafka
mp.messaging.outgoing.<chan>.topic=<topic.name.exact>
mp.messaging.outgoing.<chan>.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer
%test.mp.messaging.outgoing.<chan>.connector=smallrye-in-memory
```
Et au-dessus, la conf bootstrap : `%dev,prod.kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}` + `%test.kafka.bootstrap.servers=localhost:9092` (déjà conventionné dans event-service).

**6. Tests**
- `<Domain>EventTest.java` : asserter les factory methods (record values).
- `<Domain>PublisherTest.java` : `@QuarkusTest` + `@Inject InMemoryConnector connector` ; `connector.sink("<chan>").received()` → asserter taille 1 et payload.
Modèle exact : `services/event-service/src/test/java/ch/unige/events/event/kafka/EventLifecyclePublisherTest.java`.

**7. Topic provisioning** : déjà fait — les 10 topics sont dans `k8s/chart/templates/kafka/topics-init.yaml`. **Aucune modif côté chart**.

**8. Pour un consumer** (cas KAFKA-002) : ajouter `mp.messaging.incoming.<chan>.connector=smallrye-kafka` + `topic` + `value.deserializer=io.quarkus.kafka.client.serialization.ObjectMapperDeserializer` + `value.deserializer.type=ch.unige....EventBannedEvent` + group-id (`group.id=<svc>`) ; classe `@Incoming("<chan>")` `@Transactional` ; idempotence guard sur l'état avant mutation.


## Catégorie 6 + 7 + 11 — OpenAPI / Kong / Helm + CI / Sonar + DevOps handoff (18 findings)


## Summary
- 18 findings total (sev: 2 high, 9 medium, 7 low)
- OpenAPI: spec strictement non modifié (`git diff origin/main HEAD -- openapi/` = 0 ligne) — contrat figé respecté
- Kong: 13/13 services routés, mais `rate-limiting` absent (décision 6 violée), pas de plugin OIDC/JWT
- Helm: tous services à `replicas: 1` sauf `notification-service` (replicas: 0), legacy `api/` supprimé, `Chart.yaml` v0.2.0
- CI: single-job `build-backend` sur reactor entier, single `sonar.projectKey` — matrix per-service déférée à PR 16

## Tableau OpenAPI vs Kong vs services

| Path OpenAPI | Service amont (api-contract.md) | Route Kong (configmap-routes.yaml) | Routé ? |
|---|---|---|---|
| GET/PUT /users/me | user-service | `~/api/users/me/image$`, `~/api/users/me/banner$`, `~/api/users/[^/]+$` | partiel — `/me` matche par fallback `[^/]+` |
| POST/DELETE /users/me/image | user-service | `~/api/users/me/image$` (l.193) | OK |
| POST/DELETE /users/me/banner | user-service | `~/api/users/me/banner$` (l.198) | OK |
| GET /users/{id} | user-service | `~/api/users/[^/]+$` (l.203) | OK |
| POST/DELETE /users/{id}/follow | follow-service | `~/api/users/[^/]+/follow$` (l.358) | OK |
| GET /users/{id}/followers \| /following | follow-service | `~/api/users/[^/]+/(?:followers\|following)$` (l.363) | OK |
| GET /users/me/follow-requests | follow-service | `~/api/users/me/follow-requests$` (l.373) | OK |
| POST /follow-requests/{id}/accept \| reject | follow-service | `~/api/follow-requests/(?:\d+)/(?:accept\|reject)$` (l.368) | OK |
| GET /users/me/favorites | favorite-service | `~/api/users/me/favorites$` (l.75) | OK |
| GET /users/me/calendar-token | calendar-service | `~/api/users/me/calendar-token$` (l.92) | OK |
| POST /users/me/calendar-token/regenerate | calendar-service | `~/api/users/me/calendar-token/regenerate$` (l.97) | OK |
| GET /users/me/attendances \| /participations | attendance-service | `~/api/users/me/(attendances\|participations)$` (l.284-289) | OK |
| GET /users/me/events | me-aggregator-service | `~/api/users/me/events$` (l.220) | OK |
| GET /calendar/{token}.ics | calendar-service | `~/api/calendar/[^/]+\.ics$` (l.102) | OK |
| GET/POST /events | event-service | `~/api/events$` (l.167) | OK |
| GET /events/search | event-service | `~/api/events/search$` (l.122) | OK (devant `/{id}`) |
| GET/PUT/DELETE /events/{id} | event-service | `~/api/events/(?:\d+)$` (l.162) | OK |
| GET /events/{id}/occurrences | event-service | `~/api/events/(?:\d+)/occurrences$` | OK |
| POST /events/{id}/cancel \| restore \| publish | event-service | regex distincts (l.142,147,152) | OK |
| POST/DELETE /events/{id}/image | event-service | `~/api/events/(?:\d+)/image$` (l.157) | OK |
| GET /events/featured | event-service | `~/api/events/featured$` (l.127) | OK |
| POST /admin/events/{id}/feature \| unfeature | event-service | `~/api/admin/events/(?:\d+)/(?:un)?feature$` | OK |
| POST/DELETE /events/{id}/attend | attendance-service | `~/api/events/(?:\d+)/attend$` (l.274) | OK |
| GET /events/{id}/attendees | attendance-service | `~/api/events/(?:\d+)/attendees$` (l.279) | OK |
| GET/POST/DELETE /events/{id}/co-organizers | co-organizer-service | `~/api/events/(?:\d+)/co-organizers$` + variantes | OK |
| POST /events/{id}/co-organizers/me/accept \| decline | co-organizer-service | l.305 | OK |
| DELETE /events/{id}/co-organizers/{userId} | co-organizer-service | l.310 | OK |
| GET /users/me/co-organizer-invitations | co-organizer-service | l.320 | OK |
| POST/DELETE /events/{id}/favorite | favorite-service | `~/api/events/(?:\d+)/favorite$` (l.70) | OK |
| GET /events/{id}/share | share-service | `~/api/events/(?:\d+)/share$` (l.35) | OK |
| POST /events/{id}/view (×2 doublon) | view-service | `~/api/events/(?:\d+)/view$` (l.53) | OK |
| GET /events/{id}/stats | stats-service | `~/api/events/(?:\d+)/stats$` (l.234) | OK |
| POST /events/{id}/report | report-service | `~/api/events/(?:\d+)/report$` (l.249) | OK |
| GET/POST /events/{eventId}/comments | comment-service | `~/api/events/(?:\d+)/comments$` (l.335) | OK |
| DELETE /comments/{commentId} | comment-service | `~/api/comments/(?:\d+)$` (l.340) | OK |
| GET/PUT /admin/reports[/{id}] | report-service | `~/api/admin/reports$`, `~/api/admin/reports/(?:\d+)$` | OK |
| GET /s/{shortCode} | share-service | `~/api/s/[^/]+$` (l.40) | OK |
| **POST /events/{id}/duplicate** | (api-contract.md « Sprint 7 ») | **AUCUNE** | **404** |
| **GET /notifications** | notification-service (vide) | **AUCUNE** (commenté l.402-406) | **404** |
| **PUT /notifications/{id}/read** | notification-service (vide) | **AUCUNE** | **404** |

## Tableau routes Kong

| Path regex | Methods | Service | Plugins | Spec OK ? |
|---|---|---|---|---|
| `~/api/events/(?:\d+)/share$` | toutes | share-service | (globaux uniquement) | rate-limit manquant (low — pas dans buckets spec) |
| `~/api/s/[^/]+$` | toutes | share-service | globaux | OK |
| `~/api/events/(?:\d+)/view$` | toutes | view-service | globaux | OK |
| `~/api/events/(?:\d+)/favorite$` | toutes | favorite-service | globaux | OK |
| `~/api/users/me/favorites$` | toutes | favorite-service | globaux | OK |
| `~/api/users/me/calendar-token$` | toutes | calendar-service | globaux | OK |
| `~/api/users/me/calendar-token/regenerate$` | toutes | calendar-service | globaux | OK |
| `~/api/calendar/[^/]+\.ics$` | toutes | calendar-service (read_timeout 60s) | globaux | OK |
| `~/api/events/search$` | toutes | event-service | globaux | OK |
| `~/api/events/featured$` | toutes | event-service | globaux | OK |
| `~/api/admin/events/(?:\d+)/(?:un)?feature$` | toutes | event-service | globaux | OK |
| `~/api/events/(?:\d+)/(?:occurrences\|cancel\|restore\|publish\|image)$` | toutes | event-service | globaux | **rate-limit `events.create=10/min` MANQUANT pour POST /events** (décision 6) |
| `~/api/events/(?:\d+)$` | toutes | event-service | globaux | OK |
| `~/api/events$` | toutes | event-service | globaux | rate-limit `events.create` manquant |
| `~/api/users/me/image$`, `~/api/users/me/banner$` | toutes | user-service | globaux | OK |
| `~/api/users/[^/]+$` | toutes | user-service | globaux | NB chevauche `/users/me` mais cible identique |
| `~/api/users/me/events$` | toutes | me-aggregator-service | globaux | OK |
| `~/api/events/(?:\d+)/stats$` | toutes | stats-service | globaux | OK |
| `~/api/events/(?:\d+)/report$` | toutes | report-service | globaux | OK |
| `~/api/admin/reports[..]` | toutes | report-service | globaux | OK |
| `~/api/events/(?:\d+)/(?:attend\|attendees)$` | toutes | attendance-service | globaux | OK |
| `~/api/users/me/(?:attendances\|participations)$` | toutes | attendance-service | globaux | OK |
| `~/api/events/(?:\d+)/co-organizers/me/(?:accept\|decline)$` | toutes | co-organizer-service | globaux | OK |
| `~/api/events/(?:\d+)/co-organizers/[^/]+$` | toutes | co-organizer-service | globaux | OK |
| `~/api/events/(?:\d+)/co-organizers$` | toutes | co-organizer-service | globaux | OK |
| `~/api/users/me/co-organizer-invitations$` | toutes | co-organizer-service | globaux | OK |
| `~/api/events/(?:\d+)/comments$` | toutes | comment-service | globaux | **rate-limit `comments.post=10/min` MANQUANT** |
| `~/api/comments/(?:\d+)$` | toutes | comment-service | globaux | OK |
| `~/api/users/[^/]+/follow$` | toutes | follow-service | globaux | **rate-limit `follows.follow=30/min` MANQUANT** |
| `~/api/users/[^/]+/(?:followers\|following)$` | toutes | follow-service | globaux | OK |
| `~/api/follow-requests/(?:\d+)/(?:accept\|reject)$` | toutes | follow-service | globaux | OK |
| `~/api/users/me/follow-requests$` | toutes | follow-service | globaux | OK |

Plugins globaux activés (configmap-routes.yaml l.420-457) : `cors`, `correlation-id`, `prometheus`. Aucun `rate-limiting`. Aucun `oidc`/`jwt`.

Pas de catch-all vers `api:8080` confirmé (l.13-20 + bloc commenté l.380-407). L'ingress (`templates/ingress/ingress.yaml` l.13-24) route encore `/api/*` vers `kong-proxy:8000` mais le commentaire (l.17-21) parle de monolithe — désynchronisé avec la réalité (cf. INFRA-009).

## Findings

### INFRA-001 [high, owner=backend, effort=S]
**Titre** : Path `/events/{id}/view` dupliqué dans openapi.yaml
**Catégorie** : OpenAPI
**Localisation** : `/workspace/openapi/openapi.yaml:3482` et `/workspace/openapi/openapi.yaml:3560`
**Spec attendue** : artefact pré-existant noté ligne 92 du spec (artefact toléré, à nettoyer)
**Réalité actuelle** : deux blocs `POST /events/{id}/view` identiques (l.3482-3513 et l.3560-3585). Les codecs OpenAPI lisent le second et écrasent le premier ; cela passe silencieusement sur Swagger UI mais déclenche un warning sur les générateurs stricts (openapi-generator-cli v7+).
**Conséquence** : la response `401` est silencieusement perdue côté frontend type-gen ; si un day un script génère des clients TS, les types ne couvriront que `204|404`.
**Fix suggéré** : supprimer le bloc l.3482-3513 (le dupliqué a un set d'erreurs plus riche, garder l.3560-3585) — ou inversement, mais lever la duplication.
**Effort** : S
**Owner** : backend

### INFRA-002 [high, owner=backend, effort=M]
**Titre** : Plugin Kong `rate-limiting` absent — décision 6 non honorée
**Catégorie** : Kong
**Localisation** : `/workspace/k8s/chart/templates/kong/configmap-routes.yaml:420-457`
**Spec attendue** : décision 6 (l.236) « `rate-limiting` par-route avec budgets `events.create=10/min`, `comments.post=10/min`, `follows.follow=30/min` — buckets en plus de `@PerUserRateLimit` per-instance ». Re-confirmé décision 21 (l.640-644) « Edge (Kong) plugin rate-limiting par-IP par-route fenêtre 1 min ».
**Réalité actuelle** : seuls `cors`, `correlation-id`, `prometheus` sont déclarés en `plugins:` global. Aucun bloc `plugins:` par route, aucune réf au plugin `rate-limiting`. Le `@PerUserRateLimit` Java existe (ex. `EventResource.java:106`, `CommentResource.java:55`, `FollowResource.java:55`) — deuxième niveau présent, edge level absent.
**Conséquence** : pas de protection anti-DOS au niveau gateway ; un attaquant non-authentifié peut hammer `POST /api/events/{id}/comments` jusqu'à ce que les pods comment-service tombent (rate-limit Java se déclenche après auth/validation, donc charge CPU déjà engagée).
**Fix suggéré** : ajouter par route un bloc `plugins:` :
```yaml
plugins:
  - name: rate-limiting
    config: { minute: 10, policy: local, fault_tolerant: true }
```
sur les routes `events-list` (POST /events), `event-comments` (POST /events/{id}/comments), `follow-actions` (POST /users/{id}/follow). `policy: local` pour démarrer (pas de Redis) ; passer en `policy: redis` après PR 16.
**Effort** : M
**Owner** : backend (juste YAML Helm) — pas DevOps

### INFRA-003 [medium, owner=backend, effort=S]
**Titre** : Aucun plugin Kong OIDC/JWT — la spec laisse ambigu
**Catégorie** : Kong
**Localisation** : `/workspace/k8s/chart/templates/kong/configmap-routes.yaml` (entier)
**Spec attendue** : décision 6 + § « JWT validation par chaque service » — Kong devrait au minimum forwarder le `Authorization: Bearer …` header, sans le valider.
**Réalité actuelle** : pas de plugin `jwt`/`oidc` activé. Kong forwarde transparent (preserve_host: true partout). C'est cohérent avec « validation par service » (chaque Quarkus a `quarkus-oidc`), donc OK fonctionnellement, mais aucun test/check qu'un token absent ne soit pas filtré au gateway.
**Conséquence** : aucune validation early-fail au gateway — chaque appel non-authentifié traverse Kong puis explose côté Quarkus sur `@Authenticated`. Latence + charge inutile, pas de risque de sécurité.
**Fix suggéré** : OK tel quel pour S8. Documenter explicitement dans le commentaire de tête du ConfigMap (l.10-20) que la validation JWT est délibérément déléguée aux services. Considérer plugin `jwt` Kong en S9+ pour edge-validation.
**Effort** : S (commentaire) — non bloquant
**Owner** : backend

### INFRA-004 [medium, owner=backend, effort=S]
**Titre** : Routes manquantes pour `POST /events/{id}/duplicate` + `/notifications*`
**Catégorie** : OpenAPI / Kong
**Localisation** :
- OpenAPI : `openapi.yaml:3850` (`/events/{id}/duplicate`), `:4013` (`/notifications`), `:4030` (`/notifications/{id}/read`)
- Kong : pas de route ; `notifications` commenté `configmap-routes.yaml:402-406`
- api-contract.md:541-543 marque ces 3 endpoints comme « Sprint 7 — Reporté »
**Spec attendue** : implicite — les paths existent dans openapi.yaml donc devraient soit être servis, soit retourner 501. La spec migration-microservices ne mentionne pas explicitement `/duplicate` ; `notification-service` est un scaffold à `replicas: 0`.
**Réalité actuelle** : 404 Kong sur ces 3 endpoints (le scaffold `notification-service` est déployé mais sans pod). Aucune implémentation Java (`grep duplicate` sur event-service = 0 résultat).
**Conséquence** : si un client appelle ces paths (frontend a-t-il des liens « dupliquer un event » ?), il reçoit une 404 Kong opaque sans ApiErrorResponse JSON.
**Fix suggéré** : soit (a) marquer ces paths `deprecated: true` + ajouter `x-deferred: sprint-7` dans openapi.yaml pour clarifier le statut ; soit (b) retirer ces 3 paths de openapi.yaml temporairement. Cohérent avec api-contract.md ligne 541-543.
**Effort** : S
**Owner** : backend

### INFRA-005 [low, owner=backend, effort=XS]
**Titre** : Regex Kong `~/api/users/[^/]+$` matche `/me` ET `/uuid` — chevauchement intentionnel mais fragile
**Catégorie** : Kong
**Localisation** : `configmap-routes.yaml:203` (route `user-by-id`)
**Spec attendue** : décision 6, table de routes
**Réalité actuelle** : le commentaire l.181-184 documente que `/api/users/me` matche cette regex et que c'est intentionnel (les deux endpoints sont sur la même classe `UserResource`). OK — mais l'ordre de précédence Kong pour les regex est par longueur du match, donc `/me/image$` (l.193) gagne, puis `/me/banner$` (l.198) gagne, puis fallback sur `[^/]+$`. Vérifié OK.
**Conséquence** : aucune (test passé) — mais cas-limite : si jamais un endpoint `/api/users/me` lookup-only était split sur un autre service, la route Kong serait à réécrire en `[^/]+(?<!me)$` ou explicit `/me$` route préfixée.
**Fix suggéré** : aucun. Note dans commentaire OK.
**Effort** : XS (rien à faire)
**Owner** : backend

### INFRA-006 [medium, owner=backend, effort=S]
**Titre** : Aucun `livenessProbe` sur les 14 Deployments microservices
**Catégorie** : Helm
**Localisation** : tous les `/workspace/k8s/chart/templates/*-service/deployment.yaml` (vérifié sur event-service:59, share-service, comment-service…)
**Spec attendue** : audit énonce `path /api/q/health/live et /api/q/health/ready` — implique les deux probes
**Réalité actuelle** : seul `readinessProbe` (`path: /api/q/health/ready`, `initialDelaySeconds: 5`, `periodSeconds: 10`) est déclaré. `livenessProbe` (sur `/api/q/health/live`) est absent. Kong et Kafka, eux, ont les deux.
**Conséquence** : un pod en deadlock JVM (memory leak, GC infini) ne sera jamais redémarré par K8s — il restera Ready=false sans crash, drainant les ressources. K8s ne sait pas qu'il faut redémarrer.
**Fix suggéré** : ajouter le bloc :
```yaml
livenessProbe:
  httpGet: { path: /api/q/health/live, port: 8080 }
  initialDelaySeconds: 30
  periodSeconds: 30
  failureThreshold: 3
```
sur les 13 services actifs (le scaffold notification-service vide reste à `replicas: 0`).
**Effort** : S (un copy-paste × 13)
**Owner** : backend

### INFRA-007 [medium, owner=backend, effort=S]
**Titre** : `image.api.tag` legacy — propriété mal nommée référencée par 14 deployments + Kafka
**Catégorie** : Helm
**Localisation** :
- définition `values.yaml:16-17`
- consommée par les 14 deployments microservices (cf. grep sur `image.api.tag` ci-dessus)
- consommée par `templates/kafka/statefulset.yaml:26` (annotation `release-sha`)
- consommée par `.github/workflows/deploy.yml:61,135`
**Spec attendue** : décision 30 + roadmap PR 16 — renommer en `image.tag`
**Réalité actuelle** : 16 fichiers référencent toujours `.Values.image.api.tag`. Le commentaire `values.yaml:9-15` explique que le rename est différé à PR 16 pour ne pas churner 14 templates dans cette PR. `image.api.name` est bien retiré.
**Conséquence** : confusion lecteur (`image.api` suggère un Deployment `api` qui n'existe plus) ; pas de bug fonctionnel.
**Fix suggéré** : laisser tel quel pour S8 (aligné spec). Tracer en sprint-context comme dette PR 16.
**Effort** : S (différé)
**Owner** : backend

### INFRA-008 [low, owner=backend, effort=XS]
**Titre** : `notification-service` Deployment shipped à `replicas: 0` — coût zéro mais bruit
**Catégorie** : Helm
**Localisation** : `/workspace/k8s/chart/templates/notification-service/deployment.yaml:15`
**Spec attendue** : section « Activate this LAST » du spec — scaffold accepté
**Réalité actuelle** : `replicas: 0`, image quand même référencée (`unige-events-notification-service:latest`), `Service` provisionné. Kong route commentée. Pas de Resource Java (juste `ServiceIdentityResource`).
**Conséquence** : aucun pod scheduled, aucune image pull. Helm va quand même tenter de pull au build CI (build.yml émet l'image via Quarkus container-image). Coût négligeable.
**Fix suggéré** : OK tel quel — c'est l'attente.
**Effort** : XS
**Owner** : backend

### INFRA-009 [low, owner=backend, effort=XS]
**Titre** : Commentaire `templates/ingress/ingress.yaml:17-21` mentionne « legacy monolith » — désynchronisé
**Catégorie** : Helm
**Localisation** : `/workspace/k8s/chart/templates/ingress/ingress.yaml:17-21`
**Spec attendue** : monolithe gone (commit `b570c1b`, étape 15)
**Réalité actuelle** : le commentaire dit « currently forwards 100% of the traffic to the legacy monolith (api:8080) ». Faux depuis la PR 13/PR 14.
**Conséquence** : un nouveau dev lit ce commentaire et part dans la mauvaise direction.
**Fix suggéré** : remplacer par « Kong DB-less route 100% du trafic /api/* vers les 13 microservices via configmap-routes.yaml ; pas de fallback monolithe (cf. b570c1b). »
**Effort** : XS
**Owner** : backend

### INFRA-010 [medium, owner=backend, effort=M]
**Titre** : `build-backend` reste single-job — matrix per-service non implémentée
**Catégorie** : CI
**Localisation** : `/workspace/.github/workflows/build.yml:16-67`
**Spec attendue** : décision 17 (spec l.504-540) — `strategy: matrix: service: [share-service, view-service, …]`, un Sonar par service avec `sonar.projectKey=unige-events-backend-${matrix.service}`
**Réalité actuelle** : un seul `build-backend` job qui exécute `./mvnw verify -B` sur tout le reactor depuis `backend/`. Un seul `./mvnw sonar:sonar -B` derrière (sans `-Dsonar.projectKey` override). Sprint-context confirme « matrix différé à PR 16 ».
**Conséquence** : pas de parallélisation (build séquentiel ~2-3min de plus), Sonar agrège tout dans un seul projectKey `unige-pinfo6-2026_unige-events-backend` (cf. parent `pom.xml:60`), pas de quality gate par service.
**Fix suggéré** : différé à PR 16 — OK pour S8. Tracer comme dette explicite.
**Effort** : M (PR 16)
**Owner** : devops + backend (CI YAML + 13 SonarCloud projects à créer)

### INFRA-011 [medium, owner=backend, effort=S]
**Titre** : Sonar `coverage.exclusions=services/*-service/**/*` toujours en place — masque la couverture des microservices
**Catégorie** : CI
**Localisation** : `/workspace/backend/pom.xml:88-89`
**Spec attendue** : commentaire l.83-87 du POM dit « exclusions auto-disappear at PR 14 (legacy removal) » — ce qui est fait (commit `b570c1b`)
**Réalité actuelle** : commit `43cae64` a posé l'exclusion ; `b570c1b` a retiré la legacy ; mais l'exclusion est toujours dans `pom.xml`. Les 14 services représentent 100 % du code Java backend, donc Sonar coverage = 0 % du code analysé en couverture.
**Conséquence** : SonarCloud rapporte « Coverage = N/A » ou faux 0 % sur tout new code → quality gate va échouer ou passer à tort selon config. Sprint-context l.234 confirme « PR 16 (CI matrix per-service avec son propre sonar.projectKey) » liaison directe.
**Fix suggéré** : à PR 16 retirer ces deux lignes en même temps que le passage à matrix per-service (chaque service aura son propre projectKey + son propre rapport Jacoco). Ou retirer maintenant si on accepte que la couverture remonte d'un coup.
**Effort** : S
**Owner** : backend

### INFRA-012 [low, owner=backend, effort=XS]
**Titre** : PR title `chore(backend):` au lieu de `refactor(scrum-XXX):` — workaround documenté
**Catégorie** : CI
**Localisation** : `/workspace/.github/workflows/pr-title-check.yml:67-82`
**Spec attendue** : check exige scope `scrum-XXX` pour `feat|refactor|perf` (l.68)
**Réalité actuelle** : PR #158 titre commençant par `refactor(backend):` rejeté ; sprint-context.md:439 documente le workaround `chore(backend): scaffold microservices migration foundations…`. Le check passe via la branche `chore`.
**Conséquence** : la PR microservices n'a pas de ticket Jira lié dans son titre — affichage Github pas idéal mais pas bloquant. Le titre demandé par la spec (ligne 439) requiert création d'un Jira `SCRUM-XXX migrate-to-microservices` OU patch du check.
**Fix suggéré** : créer un ticket SCRUM dédié migration et passer en `refactor(scrum-NNN): migrate to microservices…` à la PR de consolidation finale ; OU patcher `pr-title-check.yml:68` pour autoriser `refactor(backend)` sans Jira (déconseillé — perte de traçabilité).
**Effort** : XS
**Owner** : backend

### INFRA-013 [low, owner=backend, effort=XS]
**Titre** : `deploy-production` skipping confirmé — clean
**Catégorie** : CI
**Localisation** : `/workspace/.github/workflows/deploy.yml:18`
**Spec attendue** : production protégée
**Réalité actuelle** : `if: github.ref_name == 'main'` — OK, skipping bien depuis une branche feature. PR #158 ne touche pas prod.
**Conséquence** : aucun risque
**Fix suggéré** : RAS
**Effort** : XS
**Owner** : N/A

### INFRA-014 [low, owner=backend, effort=XS]
**Titre** : `deploy-preview` workflow OK + fix Kafka KRaft idempotent
**Catégorie** : CI
**Localisation** : `/workspace/.github/workflows/deploy.yml:67-197`
**Spec attendue** : preview env vert
**Réalité actuelle** : workflow démolit le StatefulSet Kafka + PVC avant chaque upgrade (l.118-126), avec commentaire détaillant la raison (KRaft `meta.properties` stale). Idempotent. La diagnose-on-failure (l.143-165) imprime tous les logs pods.
**Conséquence** : preview robuste, deploy verts récents.
**Fix suggéré** : RAS
**Effort** : XS
**Owner** : N/A

### INFRA-015 [low, owner=backend, effort=XS]
**Titre** : Aucun workflow `.disabled` ni `if: false` — propre
**Catégorie** : CI
**Localisation** : `/workspace/.github/workflows/`
**Spec attendue** : housekeeping
**Réalité actuelle** : `find -name "*.disabled"` = 0 résultat ; `grep "if: false"` = 0. Workflows : `build.yml`, `ci-cd.yml`, `cleanup.yml`, `deploy.yml`, `pr-title-check.yml`. Aucun orphelin.
**Conséquence** : OK
**Fix suggéré** : RAS
**Effort** : XS
**Owner** : N/A

### INFRA-016 [low, owner=backend, effort=XS]
**Titre** : 10 topics Kafka KRaft créés via Job idempotent — conforme spec § 4.5 + décision 26
**Catégorie** : Helm
**Localisation** : `/workspace/k8s/chart/templates/kafka/topics-init.yaml:57-66`
**Spec attendue** : décision 26 (spec l.926, l.848 « Topics Kafka projetés »)
**Réalité actuelle** : 10 topics (`events.published`, `events.cancelled`, `events.banned`, `events.expired`, `users.followed`, `users.follow-requested`, `users.follow-accepted`, `comments.created`, `co-organizers.invited`, `co-organizers.accepted`). Partitions=1, RF=1, retention 7j. `--if-not-exists` → idempotent. Hook `post-install,post-upgrade`.
**Conséquence** : OK pour S8 single-broker. Production-grade : RF=3, partitions ≥ 3 — déféré DevOps.
**Fix suggéré** : RAS pour S8
**Effort** : XS
**Owner** : devops (S9+)

### INFRA-017 [low, owner=backend, effort=XS]
**Titre** : `Chart.yaml` v0.2.0 confirmé
**Catégorie** : Helm
**Localisation** : `/workspace/k8s/chart/Chart.yaml:5`
**Spec attendue** : sprint-context bump 0.1.0 → 0.2.0
**Réalité actuelle** : `version: 0.2.0` ✓
**Conséquence** : OK
**Fix suggéré** : RAS
**Effort** : XS
**Owner** : N/A

### INFRA-018 [low, owner=backend, effort=XS]
**Titre** : Templates `api/` legacy bien supprimés — cohérent avec `b570c1b`
**Catégorie** : Helm
**Localisation** : `ls /workspace/k8s/chart/templates/api` → no such directory
**Spec attendue** : commit `b570c1b refactor(backend): remove legacy-monolith — Sprint 8 step 15 complete`
**Réalité actuelle** : aucun template `api/` ; aucune route `monolith-api` dans Kong ; aucune image `unige-events-api:` produite par build.yml (le commentaire `build.yml:43-48` explique que le hardcoded `unige-events-api` a été retiré).
**Conséquence** : OK — strangler-fig propre
**Fix suggéré** : RAS
**Effort** : XS
**Owner** : N/A

## Annexe — DevOps handoff (cat 11) — items à valider

| Item | Statut spec | Justification déférée | Action attendue côté DevOps |
|---|---|---|---|
| Création des 13 SonarCloud projects (un par service) | spec décision 17 — déféré PR 16 | sprint-context:234 « PR 16 CI matrix per-service avec son propre sonar.projectKey » ; aujourd'hui 1 seul projectKey `unige-pinfo6-2026_unige-events-backend` | À PR 16 : créer 13 projets SonarCloud + 13 secrets `SONAR_TOKEN_*` dans Github (ou réutiliser un même token avec scope multi-projet) |
| Refonte CI matrix per-service | spec décision 17 — déféré PR 16 | sprint-context:228-234 — single job en S8 « pour ne pas churner 14 changements en parallèle » | À PR 16 : implémenter `strategy.matrix.service` dans `build.yml`, supprimer `sonar.coverage.exclusions` du parent POM |
| Kafka broker config production-grade | spec § 4.5 + décision 26 — S8 single-broker accepté | `kafka/statefulset.yaml:80-94` met RF=1, partitions=1, min-ISR=1 ; topics-init.yaml:51-54 idem | À S9+ : passer en cluster KRaft 3 nœuds (RF=3, ISR=2, partitions ≥ 3 par topic), bumper PVC ≥ 50Gi, ajouter durabilité (acks=all côté producers) |
| Schemas-per-service (Flyway séparé) | spec décision 22 (mono-DB partagée S8) | data-model.md confirme schéma unique `unige_events` partagé ; chaque service tire ses migrations Flyway depuis son module mais sur la même DB physique | À S9+ : split en 13 logical schemas + `search_path` par service ; ou 13 DBs physiques (recommandé production) ; plan migration zero-downtime à concevoir |
| Réseaux K8s + secrets Doppler | spec § « Secrets » | `deploy.yml:36-42` injecte `doppler-token` ; chaque deployment fait `envFrom: secretRef: app-secrets` (cf. `event-service/deployment.yaml:48-50`) | RAS S8 — Doppler ops valide les secrets `DB_PASSWORD`, `OIDC_*`, `S3_*`. À S9+ : NetworkPolicies pour isoler chaque service-to-service (e.g. comment-service ne peut pas joindre db sauf si nécessaire) |
| Domaines / certs / Cloudflare tunnel | hors-spec migration | preview via `*.trycloudflare.com` (deploy.yml:170-177) ; prod sur `pinfo6.p-info.net` (values.yaml:23) | RAS S8 — DevOps valide le certif TLS prod renouvelé, tunnel preview stable |
| Production-grade Kong (DB-mode, tracing) | spec décision 6 — DB-less S8 | `kong/deployment.yaml:38-39` `KONG_DATABASE=off`, déclaratif via ConfigMap. 2 replicas en values.yaml:66, 1 en preview | À S9+ si scale : passer en DB-mode (Postgres dédié), activer plugin `opentelemetry`, exposer admin API derrière auth pour ops, plugin `rate-limiting` en `policy: redis` (cluster-wide) |
| Plugin Kong `rate-limiting` (INFRA-002) | spec décision 6 — manquant | aucun bloc `plugins:` par-route dans configmap-routes.yaml | À régler dans cette PR (backend YAML — pas DevOps), policy=local OK pour S8 |

---
End of audit. 18 findings. Spec OpenAPI strictement non modifié (diff vide). Migration microservices alignée avec spec sur structure ; gaps majeurs : (a) plugin Kong rate-limiting absent (INFRA-002), (b) liveness probes absentes (INFRA-006), (c) doublon /events/{id}/view dans openapi.yaml (INFRA-001).


## Catégorie 8 — Documentation (24 findings)


## Summary
- 24 findings across 9 documents + PR body
- Critical contradictions: module count (14 vs 16), service count (13 vs "13+notification scaffold"), 11 `<this PR>` placeholders, multiple stale "What's NOT in this PR" claims, AGENTS.md backend totally out-of-date.
- Étape 18 (4 commits `446ea3e..bee933d`) reflected only in `sprint-context.md`. `architecture.md`, `api-contract.md`, `data-model.md`, PR body, `AGENTS.md` racine and `backend/AGENTS.md` ignorent encore les 4 commits.

## Tableau récap par doc

| Doc | Statut | Findings |
|---|---|---|
| `architecture.md` | ⚠ critique | DOC-001, DOC-002, DOC-003, DOC-004, DOC-005, DOC-006 |
| `data-model.md` | ⚠ partiel | DOC-007, DOC-008 |
| `api-contract.md` | ⚠ partiel | DOC-009, DOC-010 |
| `dev-guide.md` | ⚠ partiel | DOC-011, DOC-012 |
| `sprint-context.md` | ⚠ critique | DOC-013, DOC-014, DOC-015, DOC-016 |
| `microservices-migration-roadmap.md` | ⚠ critique | DOC-017, DOC-018 |
| `README.md` (backend/docs) | ✅ | — |
| `backlog_s5_s10.md` | ✅ | — (hors scope migration) |
| `AGENTS.md` racine | ⚠ partiel | DOC-019 |
| `backend/AGENTS.md` | ⚠ critique | DOC-020, DOC-021 |
| `backend/CLAUDE.md` | ✅ | — (pointeur 2 lignes) |
| PR #158 body | ⚠ critique | DOC-022, DOC-023, DOC-024 |

## Findings

### DOC-001 [high, owner=backend, effort=S]
**Titre** : architecture.md déclare "13 microservices" mais la réalité est 13 services + 1 placeholder + 2 shared libs = 16 modules Maven.
**Document concerné** : `backend/docs/architecture.md`
**Section / ligne** : "Vue d'ensemble" l. 22, "Multi-module Maven" l. 102-104.
**Problème** : "Deployment ×13" et "14 modules enfants (un par microservice après suppression de legacy-monolith à step 15)".
**Réalité** : `backend/pom.xml` déclare 16 modules : `shared-rate-limit`, `shared-storage`, 13 services extraits (`*-service`) + `notification-service` (placeholder packaging=jar avec uniquement `ServiceIdentityResource`). Helm chart a 14 sous-templates de services dont notification-service à `replicas: 0`.
**Fix suggéré** : Mentionner "16 modules Maven (13 services + notification-service scaffold + 2 shared libs)" et préciser que notification-service tourne `replicas: 0` (DNS réservé, pas de code).

### DOC-002 [critical, owner=backend, effort=S]
**Titre** : Section "Composants par couche (état actuel)" est fossile pré-Sprint 8.
**Document concerné** : `backend/docs/architecture.md`
**Section / ligne** : l. 205-228.
**Problème** : N'énumère que `UserResource`, `EventResource`, `UserService`, `EventService` et 6 ExceptionMappers, comme si on avait un seul service.
**Réalité** : 24 Resources réelles réparties dans 13 services (cf. `find services -path '*/resource/*.java'`). Les ExceptionMappers vivent désormais service par service (`shared-storage` héberge `FileTooLargeExceptionMapper` + `InvalidFileTypeExceptionMapper`).
**Fix suggéré** : Soit retirer cette section, soit la pivoter en table par service.

### DOC-003 [critical, owner=backend, effort=S]
**Titre** : Diagramme "Infrastructure Kubernetes" et flux d'une requête référencent encore `Service api`.
**Document concerné** : `backend/docs/architecture.md`
**Section / ligne** : l. 153-156 ("flux PUT /api/users/me") et l. 251-263 (diagramme K8s).
**Problème** : "Nginx (pod web) intercepte /api/* et proxie vers le pod api sur le port 8080" + diagramme avec un seul `Service api`.
**Réalité** : Ingress route `/api/*` vers `kong-proxy:8000` qui dispatche vers 13 services. Cf. la section "Vue d'ensemble" du même doc qui le décrit correctement plus haut — contradiction interne.
**Fix suggéré** : Aligner le bas du doc (legacy) avec le haut, ou supprimer ces sections obsolètes.

### DOC-004 [high, owner=backend, effort=XS]
**Titre** : ModerationCleanupJob documenté comme posant `CANCELLED`, code livre `BANNED`.
**Document concerné** : `backend/docs/architecture.md`
**Section / ligne** : "ModerationCleanupJob" l. 277-282.
**Problème** : "passe le `status` de chaque événement sélectionné à `CANCELLED`".
**Réalité** : `ModerationCleanupService.java:69` fait `event.status = EventStatus.BANNED;` (cf. SCRUM-97 documenté dans data-model.md mais pas répercuté ici).
**Fix suggéré** : Remplacer `CANCELLED` par `BANNED` et noter le commentaire `// TODO: emit events.banned Kafka` (`ModerationCleanupService.java:70`).

### DOC-005 [high, owner=backend, effort=XS]
**Titre** : Section "CI/CD" décrit le pipeline d'avant la migration Helm/multi-module.
**Document concerné** : `backend/docs/architecture.md`
**Section / ligne** : l. 291-299.
**Problème** : "kubectl apply -f k8s/ + rollout restart" et "build image Docker via quarkus-container-image-docker".
**Réalité** : Le déploiement passe par `helm upgrade --set image.api.tag=$SHA` (cf. l. 58-64 du même doc). Build image via `quarkus-container-image-jib` (cf. roadmap PR template).
**Fix suggéré** : Réécrire la section CI/CD en cohérence avec le bloc "Tout le déploiement passe par un chart Helm umbrella unique" (l. 58-64).

### DOC-006 [medium, owner=backend, effort=XS]
**Titre** : Note "Rate limiting" stale après commit `446ea3e`.
**Document concerné** : `backend/docs/architecture.md`
**Section / ligne** : l. 86-89.
**Problème** : "annotations `@PerUserRateLimit` … perdues à l'extraction. Restauration via plugin Kong … à câbler en follow-up".
**Réalité** : Commit `446ea3e` a livré `services/shared-rate-limit/` + 13 annotations restaurées sur 6 Resources (event×6, user×3, attendance/comment/favorite/follow×1). Cf. `grep PerUserRateLimit services/*/src/main/java`.
**Fix suggéré** : Réécrire le bullet pour décrire la lib `services/shared-rate-limit/` + la liste des resources annotées.

### DOC-007 [medium, owner=backend, effort=XS]
**Titre** : Entité `User` n'a pas de ligne "Owned by **<svc>**".
**Document concerné** : `backend/docs/data-model.md`
**Section / ligne** : "### User" l. 5-7.
**Problème** : Toutes les autres entités (Event, Favorite, EventView, Attendance, EventCoOrganizer, Follow, Report, Comment) ont une ligne "Owned by **<svc>**". Manque sur User.
**Réalité** : Code dans `services/user-service/src/main/java/ch/unige/events/user/entity/User.java` — owned by **user-service**.
**Fix suggéré** : Ajouter "Owned by **user-service**. Tables : `users` + `user_interests`." sous le titre `### User`.

### DOC-008 [medium, owner=backend, effort=XS]
**Titre** : Section EventView ne mentionne pas le Kafka producer event-service.
**Document concerné** : `backend/docs/data-model.md`
**Section / ligne** : "### EventView" l. 132-149 et "### Event" l. 45-110.
**Problème** : sprint-context Étape 18 mentionne `EventLifecyclePublisher` côté Event (events.published/cancelled/expired), info absente de data-model.md (qui pousse pourtant ce niveau de détail pour le scheduler).
**Réalité** : `services/event-service/src/main/java/ch/unige/events/event/kafka/EventLifecyclePublisher.java` existe et est wired dans `EventService.publish/cancel` + `EventExpirationService.expireEvents`.
**Fix suggéré** : Ajouter sous "### Event" une note "Kafka : `EventLifecyclePublisher` émet `events.{published,cancelled,expired}` au sein de la même unité JTA (fire-and-forget)."

### DOC-009 [high, owner=backend, effort=S]
**Titre** : Lignes "rate-limit DEFERRED" obsolètes après `446ea3e`.
**Document concerné** : `backend/docs/api-contract.md`
**Section / ligne** : l. 63 (`POST /events`), l. 97 (`POST /events/{id}/comments`), l. 100 (`POST /users/{id}/follow`).
**Problème** : Cellule Auth annotée "(rate-limit DEFERRED)".
**Réalité** : Code porte les annotations : `EventResource.java:106` (events.create), `CommentResource.java:55` (comments.post), `FollowResource.java:55` (follows.follow). Cf. aussi l. 232 ("rate limit `events.create` (max 10/min/utilisateur)") qui se contredit avec "DEFERRED" sur la même page.
**Fix suggéré** : Remplacer "DEFERRED" par les valeurs réelles (`@PerUserRateLimit`) ; ajouter aux 9 autres routes mutating qui portent une annotation (cf. `grep PerUserRateLimit`).

### DOC-010 [high, owner=backend, effort=XS]
**Titre** : Note "Rate limit notice" complètement obsolète.
**Document concerné** : `backend/docs/api-contract.md`
**Section / ligne** : l. 112-116.
**Problème** : "la cellule Auth ne mentionne plus `@PerUserRateLimit` car les annotations vivaient sur `RateLimitInterceptor` du legacy-monolith et n'ont pas été portées vers les microservices. Restauration via plugin Kong … follow-up".
**Réalité** : `services/shared-rate-limit/` livré + 13 annotations actives.
**Fix suggéré** : Remplacer la note par "**Rate limit** : `services/shared-rate-limit/` fournit `@PerUserRateLimit` réutilisé par 6 services. Cf. table ci-dessus pour les budgets par endpoint."

### DOC-011 [medium, owner=backend, effort=XS]
**Titre** : dev-guide.md affirme "14 microservices" et omet les shared libs.
**Document concerné** : `backend/docs/dev-guide.md`
**Section / ligne** : "Layout Maven" l. 14-19.
**Problème** : "**14 microservices Quarkus** sous `backend/services/`".
**Réalité** : 13 services Quarkus extraits + 1 notification-service scaffold + 2 shared libs (jar `shared-rate-limit`, `shared-storage`). Build verify reactor = 16 modules selon Étape 18 (mentionné dans sprint-context.md l. 376-377).
**Fix suggéré** : "13 microservices Quarkus + 1 placeholder (notification-service) + 2 shared libs (`shared-rate-limit`, `shared-storage`) sous `backend/services/`".

### DOC-012 [low, owner=backend, effort=XS]
**Titre** : "Workflow modifier le schéma" est en contradiction avec data-model.md / AGENTS.md.
**Document concerné** : `backend/docs/dev-guide.md`
**Section / ligne** : l. 104-110.
**Problème** : "Le schéma est géré exclusivement par Hibernate en mode `update`. Hibernate applique les changements automatiquement au démarrage."
**Réalité** : data-model.md l. 707-714 et `backend/AGENTS.md` l. 72-83 disent "Hibernate en `validate` / Flyway pilote le schéma". Le mode `update` est seulement pour le `%test`.
**Fix suggéré** : Aligner sur le workflow Flyway-first décrit en data-model.md.

### DOC-013 [critical, owner=backend, effort=M]
**Titre** : 11 placeholders `<this PR>` non remplacés par des SHAs réels.
**Document concerné** : `backend/docs/sprint-context.md`
**Section / ligne** : l. 64, 74, 86, 101, 142, 164, 176, 194, 236, 267, 298 (cf. annexe).
**Problème** : Le placeholder doit être remplacé par le SHA du commit qui livre l'item.
**Réalité** : SHAs réels disponibles via `git log` :
- PR 3 favorite : `5a40df3` (avant `eb5999a`) — à recouper
- PR 4 calendar / PR 5 follow / PR 6 comment / PR 9 report / PR 11 me-aggregator / PR 12 user / PR 13 event : SHAs précis à retrouver via `git log --oneline | grep extract`.
- Image upload migration : `41074e9`
- Step 15 legacy-monolith removal : `b570c1b`
- Étape 16 partielle docs : `912a0e3` + `454cfb3`
**Fix suggéré** : Substituer chaque `<this PR>` par le SHA correspondant. Bloque toute traçabilité git.

### DOC-014 [high, owner=backend, effort=S]
**Titre** : Tableau "Écarts vs spec" toujours figé sur l'état "fondations + scaffolds" pré-extraction.
**Document concerné** : `backend/docs/sprint-context.md`
**Section / ligne** : l. 421-442.
**Problème** : Lignes "Partiel : 2 services réellement extraits", "12 autres à `replicas: 0`", "table de routes : catch-all → api:8080", "Schedulers tournent encore dans legacy-monolith", "CI matrix par service Non livré", "1 seul sonar.projectKey", "PR ouverte titre EXACT… Non". Toutes étaient vraies à l'Étape 1, mais 13 services sont extraits + legacy supprimé + schedulers déplacés depuis `b570c1b`.
**Réalité** : Cf. la section juste au-dessus du même doc qui dit le contraire ("13 services extraits ✅"). Contradiction interne flagrante.
**Fix suggéré** : Récrire la table pour refléter l'état Étape 18 — dans la majorité ✅ ; CI matrix + Pact / E2E / REST clients restent les vrais "non livré".

### DOC-015 [high, owner=backend, effort=XS]
**Titre** : Étape 15 / Étape 16 listées **deux fois** : une fois "✅ livré", une fois "DEFERRED".
**Document concerné** : `backend/docs/sprint-context.md`
**Section / ligne** : l. 267-316 ("Step 15 — Legacy-monolith removal ✅", "Étape 16 partielle ✅") puis l. 404-414 ("Étape 15 — Suppression legacy-monolith DEFERRED", "Étape 16 — Documentation finale (PARTIELLE livrée + reste DEFERRED)").
**Problème** : Le second bloc parle au futur d'une étape déjà décrite comme livrée 130 lignes plus haut.
**Fix suggéré** : Supprimer le second bloc (l. 404-414) ou le reformuler pour dire "voir bloc précédent".

### DOC-016 [medium, owner=backend, effort=XS]
**Titre** : Header doc "étapes 0 + 1 livrées" alors que tout est livré.
**Document concerné** : `backend/docs/sprint-context.md`
**Section / ligne** : l. 7-13.
**Problème** : "Sprint 8 — Migration vers microservices (étapes 0 + 1 livrées) — 2026-05-08 — En cours."
**Réalité** : Étapes 0..15 + 16 partiel + 18 livrées (cf. `git log`). Date doc dit `2026-05-09`, header dit `2026-05-08`.
**Fix suggéré** : "(étapes 0 → 18 livrées)" / "Livré" et propager la date du `Dernière mise à jour`.

### DOC-017 [high, owner=backend, effort=S]
**Titre** : Roadmap sans état "PR mergée" — toutes les PR 1..13 + 14..16 listées au futur.
**Document concerné** : `backend/docs/microservices-migration-roadmap.md`
**Section / ligne** : tout le doc (l. 167-680).
**Problème** : Le tableau "Ordre des PR d'extraction" liste les 13 services au futur (`refactor/extract-<svc>-service partant de main`). Aucune coche ✅ / ❌ / status.
**Réalité** : PR 1..13 mergées sur la branche `refactor(backend)--migrate-to-microservices` (cf. `git log` : commits `b858196`..`f360aff`). PR 14 (legacy removal) = commit `b570c1b`. PR 15 (final docs) partiellement = commits `912a0e3` + `454cfb3`. PR 16 CI matrix toujours non livrée. Étape 18 (post-migration consolidation) absente de la roadmap.
**Fix suggéré** : Ajouter une colonne "Status" et marquer PR 1..14 = ✅ avec SHAs. PR 15 = ⚠ partiel (architecture+dev-guide faits, data-model+api-contract+AGENTS.md à faire). PR 16 = ❌ deferred. Ajouter section "PR 17 — Étape 18 consolidation post-migration ✅" avec les 4 commits.

### DOC-018 [low, owner=backend, effort=XS]
**Titre** : Roadmap décrit `services/legacy-monolith/` comme la source de copie pour les extractions.
**Document concerné** : `backend/docs/microservices-migration-roadmap.md`
**Section / ligne** : l. 9-15, 65-69, 127-131, 163, 193-198.
**Problème** : "Files moved depuis `services/legacy-monolith/`" + "Suppression dans `legacy-monolith` : retirer les fichiers Java correspondants".
**Réalité** : `services/legacy-monolith` n'existe plus (`git rm -r` à `b570c1b`). Le doc reste utile comme historique mais devrait être daté en haut comme "rédigé pre-extraction".
**Fix suggéré** : Banner en tête : "**Note** : ce doc a été rédigé avant les extractions ; les chemins `services/legacy-monolith/...` sont historiques. Pour l'état post-migration cf. architecture.md."

### DOC-019 [medium, owner=backend, effort=XS]
**Titre** : Liste 13 services dans AGENTS.md racine omet notification-service & shared libs.
**Document concerné** : `AGENTS.md` racine
**Section / ligne** : l. 12-21.
**Problème** : "13 microservices Quarkus livrés au Sprint 8" + énumération sans notification-service.
**Réalité** : 13 services extraits + 1 placeholder notification-service + 2 shared libs. PR body dit aussi 13 mais notification-service est explicitement listé comme module Maven dans `backend/pom.xml`.
**Fix suggéré** : "13 microservices Quarkus livrés au Sprint 8 + 1 scaffold notification-service (replicas: 0, follow-up SCRUM-99) + 2 shared libs (`shared-rate-limit`, `shared-storage`)."

### DOC-020 [critical, owner=backend, effort=M]
**Titre** : `backend/AGENTS.md` Layout Maven complètement obsolète.
**Document concerné** : `backend/AGENTS.md`
**Section / ligne** : l. 6-21.
**Problème** : Décrit `services/legacy-monolith/` comme actif (`<packaging>quarkus</packaging>`, "100 % du code applicatif"), 14 placeholders pom-packagés "aucun ne porte de code aujourd'hui". Note "Tant que les extractions ne sont pas livrées, **toutes les conventions ci-dessous s'appliquent à `services/legacy-monolith/`**".
**Réalité** : legacy-monolith supprimé au commit `b570c1b`. 13 services extraits portent du code Quarkus.
**Fix suggéré** : Réécrire en pointant vers architecture.md pour la table des services + retirer toutes les mentions de legacy-monolith.

### DOC-021 [high, owner=backend, effort=XS]
**Titre** : `backend/AGENTS.md` commande `quarkus:dev` pointe vers `services/legacy-monolith/`.
**Document concerné** : `backend/AGENTS.md`
**Section / ligne** : l. 22-38, l. 79.
**Problème** : "dev local — depuis `backend/services/legacy-monolith/` uniquement", "migrations Flyway dans `backend/src/main/resources/db/migration/`".
**Réalité** : `cd backend/services/<svc> && ../../mvnw quarkus:dev` (cf. dev-guide.md l. 39-42). Migrations vivent désormais par service (le path `backend/src/main/resources/db/migration/` n'existe plus).
**Fix suggéré** : Remplacer toutes les occurrences `legacy-monolith` par `<service>`. Aligner sur dev-guide.md.

### DOC-022 [high, owner=backend, effort=S]
**Titre** : PR body "What's NOT in this PR" liste plusieurs items qui ONT été livrés à l'Étape 18.
**Document concerné** : PR #158 body (récupéré via `gh pr view 158`)
**Section / ligne** : section "What's NOT in this PR (follow-up tickets)".
**Problème** : 3 lignes obsolètes :
- "Kafka producteurs/consommateurs — 10 topics provisionnés mais aucun producer/consumer câblé" — **partiellement faux** : `5dce9be` câble 3 producers (events.{published,cancelled,expired}).
- "@PerUserRateLimit restoration — 10 annotations perdues" — **faux** : `446ea3e` restaure 13 annotations via `services/shared-rate-limit/`.
- "Image upload consolidation — option de lib partagée services/shared-storage/" — **faux** : `3f3dcd1` consolide via `services/shared-storage/` (12 fichiers Java dupliqués supprimés).
**Réalité** : ces 3 items sont livrés. Seuls "PR 16 CI matrix", "REST clients", 7 producers Kafka restants, "Docs follow-up data-model+api-contract+AGENTS.md" sont vraiment NOT in this PR.
**Fix suggéré** : Mettre à jour le PR body pour refléter Étape 18 (commits `446ea3e..bee933d`).

### DOC-023 [medium, owner=backend, effort=XS]
**Titre** : PR body Sonar exclusion est désormais inutile mais reste documentée comme nécessaire.
**Document concerné** : PR #158 body
**Section / ligne** : "CI / Sonar — vert" + section docs.
**Problème** : "SonarQube Cloud Backend ✓ — Quality Gate passed après l'exclusion `services/*-service/**/*` (commit `43cae64`) ; les nouveaux services portent uniquement le sentinel `ServiceIdentityResourceTest`".
**Réalité** : Étape 18 a livré `shared-rate-limit` (35 tests, ~95% lignes) et `shared-storage` (75 tests, 100% lignes). Les services portent désormais le code réel + tests réels (cf. event-service `EventLifecyclePublisherTest`). L'exclusion Sonar est devenue largement no-op.
**Fix suggéré** : Préciser que les 2 shared libs sont **hors** du glob d'exclusion (leur couverture compte sur new-code metric).

### DOC-024 [medium, owner=backend, effort=XS]
**Titre** : PR body "Documentation - Différé" mentionne data-model.md / api-contract.md / root AGENTS.md.
**Document concerné** : PR #158 body
**Section / ligne** : section "Documentation".
**Problème** : Marqué `[ ] Différé : data-model.md, api-contract.md, root AGENTS.md`.
**Réalité** : `454cfb3` annote data-model.md (sauf User) + api-contract.md (colonne Service amont). `AGENTS.md` racine annote la nouvelle topologie (cf. l. 14-21). Reste vraiment différé : DOC-007 (User entity), DOC-009/010 (rate-limits), `backend/AGENTS.md` (DOC-020/021).
**Fix suggéré** : Mettre `[~]` partiel sur ces 3 items + lister les sous-items vraiment différés.

## Annexe — placeholders `<this PR>`, TODO, FIXME, XXX

| Type | Localisation | Texte | Fix attendu |
|---|---|---|---|
| `<this PR>` | sprint-context.md:64 | "PR 3 — favorite-service extrait (commit `<this PR>`)" | SHA réel du commit favorite-service |
| `<this PR>` | sprint-context.md:74 | "PR 4 — calendar-service" | SHA `df19461` (mentionné l. 392) |
| `<this PR>` | sprint-context.md:86 | "PR 5 — follow-service" | SHA réel |
| `<this PR>` | sprint-context.md:101 | "PR 6 — comment-service" | SHA = commit `6a44257` (cf. `git log`) |
| `<this PR>` | sprint-context.md:142 | "PR 9 — report-service" | SHA = commit `b064170` |
| `<this PR>` | sprint-context.md:164 | "PR 11 — me-aggregator-service" | SHA = commit `ba3cfa5` |
| `<this PR>` | sprint-context.md:176 | "PR 12 — user-service" | SHA = commit `166b1dd` |
| `<this PR>` | sprint-context.md:194 | "PR 13 — event-service" | SHA = commit `f360aff` |
| `<this PR>` | sprint-context.md:236 | "Image upload migration" | SHA `41074e9` |
| `<this PR>` | sprint-context.md:267 | "Step 15 — Legacy-monolith removal" | SHA `b570c1b` |
| `<this PR>` | sprint-context.md:298 | "Étape 16 partielle — Documentation finale" | SHA `912a0e3` (architecture+dev-guide) + `454cfb3` (data-model+api-contract annotations) |
| `TODO` | report-service/.../ModerationCleanupService.java:70 | "TODO: emit events.banned Kafka message once event-service ships" | event-service Kafka producer livré pour 3 topics ; events.banned reste à câbler côté report-service. Garder TODO mais le préciser : "TODO: emit events.banned via shared-kafka producer (cf. EventLifecyclePublisher pattern)". |
| `SCRUM-XXX` | roadmap.md, sprint-context.md (multiples) | Titres de PR de remplacement | Placeholders volontaires (le ticket Jira correspondant n'a pas été créé) — pas d'action requise mais à transformer en vrais SCRUM-IDs si une PR de cleanup est ouverte. |


## Catégorie 9 + 10 — Sécurité + Build hygiene (4 + 5 findings)


## Summary
- 9 findings (4 sécurité, 5 hygiène). 0 high, 2 medium, 7 low.
- OIDC + `%test.quarkus.oidc.enabled=false` portés sur les 13 services exposés (notification-service stub jar exclu).
- Anti-oracle ISSUE-92 + ISSUE-93 préservés. Anti-harvest 4.1, SVG 4.13, MAX_*_BYTES 4.19, rate-limit 4.14 portés.
- 4 `@RolesAllowed("ADMIN")` (3 event AdminEventResource, 1 report AdminReportResource) — couvre les fichiers legacy à 100 %.
- Build Maven `validate` clean ; warning runtime Quarkus `quarkus.flyway.enabled` partagé par les 13 services (HYGIENE-001).

## Tableau OIDC par service

| Service | quarkus-oidc | application.properties | %test disable | @Authenticated | @RolesAllowed("ADMIN") |
|---|---|---|---|---|---|
| user-service | OK | OK | OK | 6 | 0 |
| event-service | OK | OK | OK | 7 | 3 |
| attendance-service | OK | OK | OK | 5 | 0 |
| favorite-service | OK | OK | OK | 3 | 0 |
| view-service | OK | OK | OK | 1 | 0 |
| co-organizer-service | OK | OK | OK | 6 | 0 |
| comment-service | OK | OK | OK | 2 | 0 |
| follow-service | OK | OK | OK | 7 | 0 |
| report-service | OK | OK | OK | 1 | 1 |
| stats-service | OK | OK | OK | 1 | 0 |
| share-service | OK | OK | OK | 1 | 0 |
| calendar-service | OK | OK | OK | 2 | 0 |
| me-aggregator-service | OK | OK | OK | 1 | 0 |
| notification-service | N/A (stub jar) | minimal | N/A | 0 | 0 |

Total `@RolesAllowed("ADMIN")` = 4. Strict équivalent legacy `AdminEventResource` (3) + `AdminReportResource` (1). Aucune disparition.

## Tableau pentest findings préservés

| Finding | Mitigation | Service | Statut |
|---|---|---|---|
| 4.1 anti-harvest followers privés | `UserPublicResponse.fromAnonymous(...)` | user + follow | OK (deux DTO miroirs, voulu via stubs) |
| 4.12 anti-oracle DRAFT/CANCELLED → 404 | `EventService.getById:263` `status != PUBLISHED && !isAdmin && !isCreator…` ; BANNED aussi | event-service | OK |
| 4.13 SVG rejection | `ImageFormat.matches:38-50` whitelist JPEG/PNG/WebP/GIF | shared-storage | OK |
| 4.14 rate-limiting | 13 usages `@PerUserRateLimit` (= legacy) sur 6 services | event/comment/attendance/favorite/follow/user | OK |
| 4.15 IDs séquentiels | mitigé par 4.12 (DRAFT non publié → 404) | event-service | OK |
| 4.19 taille fichier | `FileTooLargeException` + `MAX_AVATAR_BYTES=2MB` + `MAX_BANNER_BYTES=5MB` | shared-storage | OK |

ISSUE-92 `EventService.getById(Long, String, boolean)` ligne 263 : 404 si non créateur/co-org accepté/admin et status ≠ PUBLISHED. OK.
ISSUE-93 `UserService.getPublicProfile(UUID, String)` ligne 76 : `!user.profilePublic && !isOwner` → 404. Pas de bypass admin. Vérifié contre legacy `b570c1b^:.../service/UserService.java:81` — comportement identique → préservation. Cf. SEC-001.

## Findings

### SEC-001 [low, user-service, S]
**Titre** : `UserService.getPublicProfile` n'accepte pas le bypass admin.
**Localisation** : `user-service/.../service/UserService.java:72-78` ; `UserResource.java:67-79`.
**Symptôme** : un admin appelant `GET /api/users/{uuid}` sur un profil `profile_public=false` reçoit 404 comme un anonyme. Check binaire `!user.profilePublic && !isOwner`.
**Risque** : la spec mentionne "non self **non admin**". Friction modération via API. Pas de fuite.
**Constat** : strictement identique au legacy → **non-régression**, juste désaligné avec `EventService` (qui accepte `isAdmin` sur `getById`/`publish`/`uploadImage`).
**Fix suggéré** : étendre signature `(UUID, String, boolean isAdmin)`. Hors scope PR #158.

### SEC-002 [low, 5 services, M]
**Titre** : `isCreatorOrAcceptedCoOrganizer` dupliquée par inlining dans 5 services.
**Localisation** : `EventService:518` ; `CommentService:186, 197` ; `ReportService:143` ; `AttendanceService:215` ; `EventStatsService:40`.
**Symptôme** : la cascade SCRUM-136 est portée correctement (chaque service implémente via stubs — voulu par DB-per-service). 5 implémentations indépendantes équivalent aujourd'hui à `creator.id == caller.id || EventCoOrganizerStub.find(…ACCEPTED).exists()`.
**Risque** : drift silencieux si la sémantique "accepted co-organizer" évolue. Pas de bug aujourd'hui.
**Fix suggéré** : extraire dans un `shared-authz` lib, ou exposer `GET /events/{id}/authz` côté event-service consommé via REST client. Hors scope.

### SEC-003 [low, event-service, XS]
**Titre** : `cancel`/`restore` n'acceptent pas le bypass admin.
**Localisation** : `EventService.java:342-365` (cancel) ; `367-383` (restore) — signature `(Long, String)`.
**Symptôme** : `PATCH /events/{id}/cancel` et `/restore` rejettent un admin sur l'event d'autrui (403). Identique au legacy (`b570c1b^:.../EventService.java:386, 407`).
**Risque** : non-régression. Inconsistant avec `publish`/`uploadImage` (admin OK) et `delete` (créateur strict).
**Fix suggéré** : décision produit. Hors scope.

### SEC-004 [low, all-13-services, XS]
**Titre** : Defaults OIDC bidons (`your-auth-server.com`, `your-client-id`, `your-client-secret`).
**Localisation** : tous les `application.properties`, ex. `event-service/.../application.properties:24-26`.
**Symptôme** : si `OIDC_*` env-vars manquent en prod, le service démarre quand même. Toutes requêtes `@Authenticated` retourneront 401, donc pas de fuite, mais `/q/health` reste vert.
**Risque** : déploiement silencieusement cassé.
**Fix suggéré** : retirer le default sur `OIDC_CLIENT_SECRET` → fail-fast au boot. Helm chart doit garantir la var.

### HYGIENE-001 [medium, all-13-services, S]
**Titre** : Warning runtime `Unrecognized configuration key "quarkus.flyway.enabled"`.
**Localisation** : 13 `application.properties` (l. ~18-22) déclarent `quarkus.flyway.enabled=false` mais aucun POM n'inclut `quarkus-flyway`. Confirmé : `grep quarkus-flyway services/*/pom.xml` → 0 hit.
**Symptôme** : à chaque démarrage (test ET prod) : `WARN io.quarkus.config Unrecognized configuration key…`. Reproduit dans `/tmp/audit-mvnw.log:26` (me-aggregator).
**Risque** : pollution logs. Signal trompeur (l'opérateur peut croire Flyway désactivé volontairement, alors que la dep est absente).
**Fix suggéré** : retirer la ligne `quarkus.flyway.enabled=false` des 13 fichiers. Le commentaire au-dessus ("X owns no schema") reste documentaire.

### HYGIENE-002 [low, share-service, XS]
**Titre** : `share-service/pom.xml` utilise un format verbeux différent.
**Localisation** : `backend/services/share-service/pom.xml`.
**Symptôme** : POM en multilignes alors que les 12 autres utilisent la forme compacte `<dependency><groupId>…</groupId><artifactId>…</artifactId></dependency>`.
**Risque** : friction sur diffs cross-service.
**Fix suggéré** : normaliser sur compact. Cosmétique.

### HYGIENE-003 [low, notification-service, S]
**Titre** : notification-service est un stub `<packaging>jar</packaging>` sans extraction.
**Localisation** : `notification-service/pom.xml:31` ; Kong route commentée `configmap-routes.yaml:402-406`.
**Symptôme** : POM annonce "scaffolded… The follow-up extraction PR will…". Aucun code Java en `main/`. Pas de trafic.
**Risque** : aucun, mais nom de service alloué sans incarnation runtime — dette.
**Fix suggéré** : statu-quo OK pour PR #158 (stub déclaré). Tracer extraction Sprint 9.

### HYGIENE-004 [low, report-service, XS]
**Titre** : TODO obsolète sur Kafka `events.banned`.
**Localisation** : `report-service/.../ModerationCleanupService.java:70` — `// TODO: emit events.banned Kafka message once event-service ships`.
**Symptôme** : event-service producers Kafka shipped (`5dce9be`), donc condition du TODO levée.
**Risque** : `events.banned` non propagé → consumers downstream ne savent pas qu'un event a été banni par modération.
**Fix suggéré** : implémenter le producer ou retirer le commentaire. Tracer en JIRA.

### HYGIENE-005 [medium, parent-pom, XS]
**Titre** : Sonar exclusions `services/*-service/**/*` censées disparaître à PR 14.
**Localisation** : `backend/pom.xml:88-89`.
**Symptôme** : commentaire (l. 64-86) annonce "auto-disappear at PR 14 (legacy removal)". Legacy supprimé en `b570c1b`, désormais HEAD. Exclusions `sonar.cpd.exclusions` + `sonar.coverage.exclusions` restent actives.
**Risque** : couverture + duplication du code microservices invisibles dans Sonar new-code gate. Dette qualité masquée au moment où il faudrait la mesurer.
**Fix suggéré** : retirer les 2 propriétés du parent POM. Cf. PR 16 mentionnée dans le commentaire — à exécuter maintenant.

## Notes annexes (pas de finding)
- Plugins cohérents : `jandex 3.2.2`, `jacoco 0.8.12`, `mockito-core` BOM, aucune surcharge `quarkus.platform.version`.
- 0 `@Disabled`/`@Ignore` dans `backend/services/*/src/test`.
- `quarkus-devservices-postgresql` test-scope dans les 13 services exposés.
- `%test.quarkus.scheduler.enabled=false` sur event-service + report-service (les 2 porteurs de `@Scheduled`).
- CORS Kong `origins: [https://pinfo6.p-info.net, https://*.pinfo6.p-info.net]` (`configmap-routes.yaml:421-425`) — décision 6 OK.
- Pas de header sécurité custom Quarkus — Kong amont, conforme.
- Pas de secret hardcodé prod (`%test.…secret-access-key=test` est test-only, attendu).
- Maven `validate` (17 modules) : SUCCESS, 0 warning. Warning `dependencies.dependency must be unique` (résolu en `5dce9be`) absent des 17 POMs.

---

# Punch list — ordre d'exécution recommandé

Liste linéaire des findings classés par priorité d'action. Les groupes sont des macro-tranches ; à l'intérieur d'un groupe, l'ordre suggéré privilégie les dépendances et l'effort croissant.

## Groupe A — À régler AVANT de mettre la PR en review finale (CRITICAL)

Ces items rendent la PR « non mergeable » telle quelle pour un projet qui se prétend « migration microservices complète ». Effort total estimé : ~1 semaine ingénieur backend.

| # | Finding | Effort | Notes |
|---|---|---|---|
| 1 | **DOC-013** — substituer 11 placeholders `<this PR>` dans sprint-context.md par les vrais SHAs | M | Bloque toute traçabilité git ; pré-requis pour les autres fix-DOC. |
| 2 | **DOC-022** — corriger le PR body « What's NOT in this PR » qui liste 3 items pourtant livrés (`446ea3e`/`3f3dcd1`/`5dce9be`) | S | Évite que le reviewer croie que le code est moins avancé qu'il ne l'est. |
| 3 | **DOC-020 + DOC-021** — réécrire `backend/AGENTS.md` (fossile pré-migration, parle encore de legacy-monolith) | M | Document central pour les agents IA et les nouveaux contributeurs. |
| 4 | **TEST-003** — porter `RecurrenceGeneratorTest` (logique pure, aucune dépendance Quarkus, portage trivial via `git show 41074e9:...`) | L | Restaure 13 sentinels SCRUM-147, ouvre la voie aux suivants. |
| 5 | **TEST-005** — porter `FollowServiceCoverageTest` (sentinels anti-oracle ISSUE-93 perdus + règle auto-accept) | L | Sécurité : `getFollowers_privateProfileNonOwner_returns404_antiOracle`. |
| 6 | **TEST-006** — porter `CommentServiceCoverageTest` (anti-oracle ISSUE-92 perdu + règle profondeur=2 + cascade SCRUM-136) | L | Sécurité + invariants métier. |
| 7 | **TEST-002** — porter `EventServiceCoverageTest` + `EventResourceTest` (296 + 80 tests legacy) | XL | Le plus gros mais le plus impactant : 6.4 % → ≥ 80 % event-service. |
| 8 | **KAFKA-001 + KAFKA-002** — livrer **conjointement** producteur `events.banned` (report-service) + consommateur (event-service) | M | Sinon ban admin = no-op silencieux. **Ne jamais shipper l'un sans l'autre.** |
| 9 | **BUG-001 + BUG-002** — corriger les 4 sites Kafka publish in-transaction via outbox pattern OU `Synchronization.afterCompletion(STATUS_COMMITTED)` | M | Pattern à standardiser car appliqué aux 7 producteurs futurs (KAFKA-003..005). |
| 10 | **BUG-003** — guard `EventService.cancel` sur `EventStatus.EXPIRED` | S | One-liner, pas de raison de différer. |
| 11 | **SPEC-003 / INFRA-002** — ajouter plugin Kong `rate-limiting` par-route avec les 3 buckets de la spec | M | YAML Helm uniquement, pas DevOps — backend peut le faire seul. |
| 12 | **INFRA-001** — supprimer le doublon `POST /events/{id}/view` dans openapi.yaml | S | Risque silencieux pour codegen TS. |
| 13 | **INFRA-006** — ajouter `livenessProbe` sur les 13 deployments | S | Helm copy-paste × 13. |
| 14 | **HYGIENE-001** — retirer `quarkus.flyway.enabled=false` des 13 `application.properties` (pollution warning runtime) | S | Cosmétique mais signal trompeur (suggère que Flyway est désactivé volontairement alors que la dep est absente). |
| 15 | **HYGIENE-005** — retirer le glob `<sonar.coverage.exclusions>services/*-service/**/*</sonar.coverage.exclusions>` du parent POM (l'exclusion devait disparaître à PR 14, qui est faite) — OU le faire en même temps que groupe B item 8 | S | Une fois fait, le gate Sonar va remonter la couverture business effective ; doit être synchronisé avec le portage des tests groupe A items 4-7. |

## Groupe B — Avant DevOps handoff (HIGH)

Ces items sont des compléments « propres » de la migration. Ne sont pas critiques pour la review interne mais empêchent un handoff DevOps net. Effort total estimé : ~2-3 sprints.

| # | Finding | Effort | Notes |
|---|---|---|---|
| 1 | **KAFKA-003** — câbler 3 producteurs `users.{followed,follow-requested,follow-accepted}` dans follow-service | M | Pattern EventLifecyclePublisher reproductible. |
| 2 | **KAFKA-004** — câbler producteur `comments.created` dans comment-service | S | 1 channel unique. |
| 3 | **KAFKA-005** — câbler 2 producteurs `co-organizers.{invited,accepted}` dans co-organizer-service | S | Idem pattern. |
| 4 | **KAFKA-007** — créer module `services/shared-kafka-events/` + migrer `EventLifecycleEvent` dedans | M | Pré-requis pour KAFKA-002 (event-service consumer doit pouvoir importer le record sans dupliquer). |
| 5 | **TEST-004** — porter `UserServiceCoverageTest` + `UserResourceTest` + tests S3 image | XL | user-service à 5.6 % couverture, anti-oracle ISSUE-93 perdu. |
| 6 | **TEST-007 à TEST-014** — porter les `*ServiceCoverageTest` legacy pour les 8 services restants (attendance, co-organizer, report, favorite, calendar, view, stats, share) | XL | Effort cumulé majeur ; peut être fait par sub-agents en parallèle. |
| 7 | **TEST-018** — formaliser la stratégie cross-service mocks (`*ServiceMock`, `*Stub` test-scope) | L | Pré-requis pour porter les tests qui dépendent de plusieurs services. |
| 8 | **SPEC-007 + SPEC-008 + INFRA-010 + INFRA-011** — refonte CI matrix per-service + 13 SonarCloud projects + rename `image.api.tag → image.tag` (PR 16 du roadmap) | L | Bloc DevOps + backend conjoint. **Owner = devops** (création projets SonarCloud) **+ backend** (YAML CI). |
| 9 | **SPEC-004** — ajouter `quarkus-rest-client-reactive` + `quarkus-smallrye-fault-tolerance` + `quarkus-micrometer-registry-prometheus` aux 13 services métiers | L | Pré-requis pour SPEC-002 (REST clients) et SPEC-012 (logs JSON cross-service). |
| 10 | **SPEC-002 / BUG-008 / REFACTOR-001** — remplacer les 35 JPA stubs par `@RegisterRestClient` interfaces (annexe « JPA stubs → REST clients » du finding REFACTOR-001 fournit la table complète) | XL | Le plus gros chantier après les tests. |
| 11 | **SPEC-014 + REFACTOR-009 + REFACTOR-010** — exposer endpoints internes côté event-service (`GET /events/{id}` avec anti-oracle 404 propagé) + co-organizer-service (`/check?userId=`) + user-service (visibility check) | L | Centralise les anti-oracles ISSUE-92, ISSUE-93 et la cascade SCRUM-136 derrière HTTP 404 plutôt que duplication inline. |
| 12 | **DOC-001 à DOC-024** — pass de cohérence sur `architecture.md` / `data-model.md` / `api-contract.md` / `microservices-migration-roadmap.md` / `AGENTS.md` racine | M-L | Faisable en 1 PR doc focused. |
| 13 | **REFACTOR-002 à REFACTOR-006** — créer `services/shared-domain-enums/`, `services/shared-api-error/`, `services/shared-domain-dtos/` (ou équivalent) et migrer les enums/records dupliqués | L | Sequence : enums d'abord (impact JSON sérialisation), DTOs ensuite. |

## Groupe C — Optimisation & polish (MEDIUM)

| # | Finding | Effort |
|---|---|---|
| 1 | BUG-004 — émettre `events.deleted` Kafka pour notifier les services consommateurs | M |
| 2 | BUG-005 — déplacer le lock pessimiste avant la lecture d'attendance dans `removeAttendance` | S |
| 3 | BUG-006 — pattern d'idempotence harmonisé dans `addFavorite` (try-catch unique constraint ou upsert SQL) | S |
| 4 | BUG-007 — guard explicite sur `accept` co-organizer pour les états DECLINED | S |
| 5 | BUG-009 — `@Transactional` sur `UserService.getPublicProfile` | S |
| 6 | BUG-010 — guard `null` explicite dans `updateMyProfile` | S |
| 7 | BUG-011 — extraire `ParamConverter` Timeframe dans `shared-jaxrs` | S |
| 8 | BUG-014 — normaliser TZ UTC à l'ingestion `EventRequestBase` | S |
| 9 | REFACTOR-007/008 — refactorer `EventService` (529 lignes → split God-object + JPA Criteria API) | M |
| 10 | REFACTOR-011 — `WebApplicationException` factory helpers dans `shared-api-error` | S |
| 11 | REFACTOR-012 — `ServiceIdentityResource` dans `shared-platform` | S |
| 12 | REFACTOR-013 — `resolveUser/resolveUserId` dans `shared-domain-projections` | S |
| 13 | REFACTOR-016 — décision produit sur me-aggregator-service (vrai BFF ou abandon) | L |
| 14 | REFACTOR-017 — standardiser pattern d'idempotence (cf. BUG-006) | S |
| 15 | SPEC-005 — câbler les 7 producteurs Kafka manquants (regroupe KAFKA-001..005 du groupe B mais avec scope spec) | (cf. groupe B) |
| 16 | SPEC-009 — décision produit sur me-aggregator-service | (cf. REFACTOR-016) |
| 17 | SPEC-012 — `quarkus-logging-json` pour logs structurés cross-service | M |
| 18 | SPEC-013 — `RequestIdFilter` dans une lib partagée | M |
| 19 | SPEC-016 — bascule tests Kafka vers Testcontainers (ou entériner `smallrye-in-memory` dans la spec) | M |
| 20 | SPEC-018 — vérifier `Chart.yaml` version | S |
| 21 | SPEC-022 — ouvrir tickets follow-up explicites « câbler producteurs Kafka » + « livrer notification consumer » | XS |
| 22 | INFRA-003 — commenter le ConfigMap Kong sur le choix « pas de plugin JWT » | XS |
| 23 | INFRA-004 — gérer les 3 paths openapi non routés (`/duplicate`, `/notifications*`) — `deprecated: true` ou retrait | S |
| 24 | KAFKA-006 — formaliser scope notification-service dans son pom + package-info | XS |
| 25 | KAFKA-007 — créer `services/shared-kafka-events/` (cf. groupe B item 4) | (cf. groupe B) |
| 26 | KAFKA-008 — expliciter `value.serializer=ObjectMapperSerializer` sur les 3 channels event-service + futurs | XS |
| 27 | KAFKA-009 — `smallrye-reactive-messaging-in-memory` dans le `<dependencyManagement>` parent | XS |
| 28 | TEST-015/016 — durcir les tests Kafka existants (cas erreur, partition key) | M |
| 29 | DOC-006/008/010 — mettre à jour les notes rate-limit + Kafka producer dans architecture.md / data-model.md / api-contract.md | XS |

## Groupe D — Cosmétique / non-régression (LOW)

| # | Finding | Effort |
|---|---|---|
| 1 | BUG-012 — retirer le default URL prod dans `RedirectResource` (fail-fast) | S |
| 2 | BUG-013 — auditer cascades DDL sur `EventService.delete` | S |
| 3 | INFRA-005/008/009 — commentaires alignés sur la réalité (ingress, notification-service replicas:0) | XS |
| 4 | INFRA-012 — décision produit sur la création d'un ticket Jira `SCRUM-XXX migrate-to-microservices` ou patch `pr-title-check.yml` | XS |
| 5 | SEC-001 — décision produit : `getPublicProfile` doit-il accepter le bypass admin ? | S |
| 6 | SEC-003 — décision produit : `cancel`/`restore` doivent-ils accepter le bypass admin ? | XS |
| 7 | SEC-004 — retirer les defaults OIDC bidons (fail-fast) | XS |
| 8 | HYGIENE-002 — normaliser format share-service/pom.xml (compact vs verbose) | XS |
| 9 | HYGIENE-003 — clarifier statut notification-service stub | S |
| 10 | HYGIENE-004 — TODO obsolète sur events.banned (cf. KAFKA-001) | XS |
| 11 | REFACTOR-014/015 — clean-up commentaires `<this PR>` / renommer `findByEventAndUser` | S |
| 12 | REFACTOR-018 — créer `shared-jaxrs` lib (regroupe BUG-011, REFACTOR-002, REFACTOR-011) | S |
| 13 | DOC-019 — corriger compteurs services dans AGENTS.md racine | XS |
| 14 | DOC-018 — bannière historique sur microservices-migration-roadmap.md | XS |
| 15 | DOC-007/011/012/016/023/024 — petites cohérences cross-doc | XS chacun |
| 16 | SPEC-015/017/019/020 — décisions produit ou alignement spec | XS chacun |

## Frontière DevOps — items **non listés** comme findings à fixer (informational)

Ces items sont **explicitement** dans le périmètre DevOps (cf. annexe `INFRA-XXX` Cat 11). Ne pas les traiter côté backend ; juste valider qu'ils sont sur la roadmap DevOps :

1. Création de 13 SonarCloud projects (un par service) à l'occasion de PR 16 CI matrix.
2. Cluster Kafka prod-grade : RF=3, partitions ≥ 3, ISR ≥ 2, durabilité (`acks=all`).
3. Schemas-per-service réels (Flyway séparé) : nécessite plan de migration zero-downtime.
4. NetworkPolicies K8s pour isoler le trafic service-to-service.
5. Production-grade Kong : passage en DB-mode (Postgres dédié) si scale, plugin `opentelemetry`, plugin `rate-limiting` policy=redis cluster-wide.
6. Domaines / certs prod / Cloudflare tunnel preview.
7. Secrets Doppler `DB_PASSWORD`, `OIDC_*`, `S3_*`, `KAFKA_BOOTSTRAP_SERVERS` validés et provisionnés par environnement.

---

# Annexes

## Annexe A — Couverture des décisions spec (récap par décision)

(Cf. cat 1, fichier `01-spec-compliance.md` section finale « Décisions de la spec — couverture par finding »)

| Décision spec | Statut | Findings |
|---|---|---|
| 1 — Branche | partielle | SPEC-017 |
| 2 — Kong API Gateway DB-less | OK | — |
| 3 — Kafka KRaft | OK | — |
| 4 — 14 services | partielle (BFF dégradé) | SPEC-009 |
| 5 — Propriété stricte des entités | non livrée | SPEC-002, SPEC-011 |
| 6 — Routage Kong + plugins | partielle (rate-limiting absent) | SPEC-003 |
| 7 — `quarkus-oidc` activé | OK | — |
| 8 — DB schéma par service (RBAC) | non livrée | SPEC-001, SPEC-011 |
| 9 — Migrations Flyway par service | non livrée | SPEC-001 |
| 10 — Schedulers réaffectés `replicas: 1` | OK | — |
| 11 — REST sync + Kafka async | non livrée | SPEC-002, SPEC-005 |
| 12 — Cascade autorisation cross-service via REST | non livrée | SPEC-002, SPEC-021 |
| 13 — Endpoint co-organizers/check | non livrée | SPEC-014 |
| 14 — Topic notifications.events + producteurs | partielle | SPEC-005, SPEC-022 |
| 15 — JSON Jackson Kafka | OK | — |
| 16 — Helm umbrella | partielle | SPEC-008, SPEC-018 |
| 17 — CI matrix par service | non livrée | SPEC-007, SPEC-008 |
| 18 — Pact + E2E happy path | non livrée | SPEC-006 |
| 19 — Logs JSON + X-Request-ID + Prometheus | non livrée | SPEC-004, SPEC-012, SPEC-013 |
| 20 — Strangler fig | OK | — |
| 21 — Rate limiting Kong + @PerUserRateLimit | partielle (Kong absent) | SPEC-003 |
| 22 — Stratégie N sous-PRs sous branche unique | OK | — |
| 23 — Conventions AGENTS.md | OK | — |
| 24 — Frontend strictement vide | OK | — |
| 25 — Sonar ≥ 80 % par service projectKey distinct | non livrée | SPEC-007, SPEC-019 |
| 26 — Topics Kafka figés | partielle | SPEC-005 |
| 27 — Risques + mitigations (docker-compose.dev) | partielle | SPEC-020 |
| 28 — Aliasing Kong | OK | — |
| 29 — Path dupliqué `/events/{id}/view` | OK | — |
| 30 — 4 nouvelles dépendances Quarkus | non livrée | SPEC-004 |

**Bilan** : 11 décisions OK, 8 partielles, 11 non livrées sur 30+ décisions trackées.

## Annexe B — Couverture jacoco par module (build local 2026-05-09)

(Cf. cat 3, fichier `03-tests-coverage.md` section « Tableau de couverture par module »)

| Module | Lines | Branches | Tests | Statut |
|---|---|---|---|---|
| **shared-rate-limit** | **100 %** | **100 %** | 35 | ✅ exemplaire |
| **shared-storage** | **100 %** | **100 %** | 75 | ✅ exemplaire |
| me-aggregator-service | 40 % | 0 % | 1 | ⚠ |
| stats-service | 36.6 % | 0 % | 1 | ⚠ |
| favorite-service | 19.2 % | 0 % | 1 | ⚠ |
| co-organizer-service | 19.1 % | 0 % | 1 | ⚠ |
| view-service | 17.9 % | 0 % | 1 | ⚠ |
| report-service | 15.8 % | 0 % | 1 | ⚠ |
| attendance-service | 13.6 % | 0 % | 1 | ⚠ |
| calendar-service | 12.3 % | 0 % | 1 | ⚠ |
| share-service | 10 % | 0 % | 1 | ⚠ |
| comment-service | 7.3 % | 0 % | 1 | ❌ |
| event-service | 6.4 % | 0 % | 3 | ❌ |
| user-service | 5.6 % | 0 % | 1 | ❌ |
| follow-service | 3.3 % | 0 % | 1 | ❌ pire ratio |
| notification-service | n/a (stub) | n/a | 1 | OK (placeholder volontaire) |

## Annexe C — Mapping JPA stubs → REST clients (35 stubs)

(Cf. cat 2 + 4, fichier `02-bugs-refactor.md` section « Annexe — JPA stubs → REST clients »)

35 stubs mappés vers leur service propriétaire + endpoint REST cible + pattern de migration (sync vs Kafka projection). Voir le fichier source pour la table complète à 35 lignes.

## Annexe D — Code dupliqué inter-services & libs partagées cibles

(Cf. cat 2 + 4, fichier `02-bugs-refactor.md` section « Annexe — code dupliqué »)

Synthèse des libs partagées à créer pour absorber les duplications :

| Lib cible | Contenu | Priorité |
|---|---|---|
| `shared-api-error` | `ApiErrorResponse` record + `WebApplicationException` factory helpers | HIGH (REFACTOR-002, REFACTOR-011) |
| `shared-domain-enums` | 8 enums dupliqués (`EventStatus`, `AttendanceStatus`, `EventCategory`, `Faculty`, `CoOrganizerStatus`, `FollowStatus`, `RecurrenceFrequency`, `ReportStatus/Reason`) | HIGH (REFACTOR-003) |
| `shared-domain-dtos` | `EventDTO` (et autres DTOs cross-projeté) | MEDIUM (REFACTOR-006) |
| `shared-domain-projections` | `computeAvailableSpots`, `resolveUser/resolveUserId`, helpers stubs partagés | MEDIUM (REFACTOR-005, REFACTOR-013) |
| `shared-jaxrs` | ParamConverters d'enums, base ExceptionMapper, JsonWebToken Instance helper | LOW (REFACTOR-018) |
| `shared-tracing` | `RequestIdFilter` + MDC propagation | MEDIUM (SPEC-013) |
| `shared-kafka-events` | Records de payload Kafka (`EventLifecycleEvent`, `EventBannedEvent`, `FollowLifecycleEvent`, `CommentCreatedEvent`, `CoOrganizerEvent`) | HIGH (KAFKA-007) |
| `shared-platform` | `ServiceIdentityResource`, base `application.properties` patterns | LOW (REFACTOR-012) |
| `shared-authz` (optionnel) | `isCreatorOrAcceptedCoOrganizer` (ou exposer en REST sur co-organizer-service) | MEDIUM (SEC-002, REFACTOR-004) |

## Annexe E — Sentinels documentés et leur statut (35 sentinels SCRUM-138/139/144/147)

(Cf. cat 3, fichier `03-tests-coverage.md` section « Annexe — sentinels documentés »)

**Tous absents.** 0/35 sentinels présents dans le code de test actuel. Doit être restauré en portant les fichiers `*ServiceCoverageTest.java` legacy (cf. groupe A items 4-7 du punch list).

## Annexe F — Diff stats par dossier

```
425 files changed, 13124 insertions(+), 21828 deletions(-)
   383 backend
    38 k8s
     2 .github
     1 specs_archives
     1 AGENTS.md
```

Net delete car legacy-monolith supprimé à `b570c1b`.

---

# Sources

Findings détaillés (non condensés) par axe :
- `/tmp/audit/01-spec-compliance.md` — 22 findings spec (SPEC-001 à SPEC-022)
- `/tmp/audit/02-bugs-refactor.md` — 14 bugs (BUG-001 à BUG-014) + 18 refactor (REFACTOR-001 à REFACTOR-018)
- `/tmp/audit/03-tests-coverage.md` — 18 findings tests (TEST-001 à TEST-018)
- `/tmp/audit/04-kafka.md` — 9 findings Kafka (KAFKA-001 à KAFKA-009)
- `/tmp/audit/05-infra.md` — 18 findings infra (INFRA-001 à INFRA-018)
- `/tmp/audit/06-docs.md` — 24 findings docs (DOC-001 à DOC-024)
- `/tmp/audit/07-security-hygiene.md` — 4 findings sécurité (SEC-001 à SEC-004) + 5 findings hygiène (HYGIENE-001 à HYGIENE-005)

Le contenu intégral de ces 7 fichiers est repris ci-dessus dans les 9 sections « Catégorie N — … ». Les fichiers individuels sont laissés sous `/tmp/audit/` pour traçabilité (ils seront perdus à la prochaine reboot du devcontainer — c'est OK puisque le présent document est self-contained).
