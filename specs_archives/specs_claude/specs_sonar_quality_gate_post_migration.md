# Quality gate SonarCloud post-migration microservices — backend UNIGE Events — SPEC (PR #158)

| Champ | Valeur |
|---|---|
| Sprint | S8 (post-clôture finalization-ultimate) |
| Branche | `refactor(backend)--migrate-to-microservices` (persistante, **NE PAS créer de nouvelle branche**) |
| HEAD baseline | `2aef8fe2` (tip de la branche au démarrage) |
| PR active | [#158](https://github.com/unige-pinfo6-2026/unige-events/pull/158) — **NE PAS merger**, Elie merge lui-même |
| Auteur spec | Claude (session 2026-05-09 PM, post-finalization-ultimate) |
| Exécuteur cible | Claude Code en **bypass-permissions**, autonome, branche persistante, sans merge |
| Frontend lié | **AUCUN** — `git diff --shortstat origin/main HEAD -- frontend/` doit rester à 0 ligne |
| OpenAPI | **AUCUN** — `git diff --shortstat origin/main HEAD -- openapi/` doit rester à **0 ligne ABSOLU** |
| Spec source de vérité (migration) | [`specs_microservices_migration_ultimate.md`](specs_microservices_migration_ultimate.md) (3113 lignes — **baseline immuable**) |
| Audit source | [`audit_pr158_finalization_post.md`](../audit_pr158_finalization_post.md) (1408 lignes, COV-001/COV-002/TEST-001) |
| Frontière DevOps | items 2-7 inchangés (`backend/docs/devops-handoff.md`) — item 1 **annulé** par cette spec (Option B définitive) |

---

## Note d'implémentation

Cette spec est l'**unique source de vérité** pour rendre le **quality gate SonarCloud** du projet `unige-events-backend` **VERT (PASSED)** sur la PR #158. Elle vient **après** la spec finalization-ultimate (Étape 21 livrée à `2aef8fe2`) et ne contredit aucune de ses décisions A-I — elle adresse uniquement les **deux bugs de configuration Sonar** non détectés par l'audit + le **gap de couverture des 8 700 lignes Java main ajoutées par la migration**.

**Après l'exécution complète de cette spec** :

1. La cellule SonarCloud `[unige-events-backend] SonarCloud Code Analysis` sur la PR #158 est **verte (Quality Gate PASSED)** avec **coverage on new code ≥ 80 %**.
2. Le scan Sonar est **agrégé** dans un seul projet `unige-events-backend` (Option B définitive — **les 5 projets services sont abandonnés**).
3. Les 30 sentinels SCRUM-138/144/147 actuellement taggés `@Tag("legacy-port-s9")` avec corps vide sont **portés en runtime** (assertions réelles, plus de tag).
4. Les 5 services métiers passent de `4-17 % L` à `≥ 80 % L` via port des 56 tests legacy (commit `41074e9`) + ajouts ciblés sur le code refactor post-stub-removal.
5. Le job CI `sonar-aggregate` final est strict (`continue-on-error: false`) et bloquant.
6. La PR #158 est **prête au merge** côté backend — Elie merge lui-même.

**L'exécuteur autonome** :

- ne demande **jamais** une décision au user (toutes tranchées ici A-E) ;
- commit + push après chaque sous-étape numérotée verte (granularité ≈ 1 commit par sous-étape `N.M`, ≤ 500 lignes diff sauf justifié) ;
- pousse sur la branche persistante `refactor(backend)--migrate-to-microservices` ;
- ne merge **jamais** la PR #158 ;
- ne crée **jamais** de nouvelle branche, jamais de nouveau ticket Jira, jamais de nouvelle PR ;
- met à jour `backend/docs/sprint-context.md` (nouvelle § Étape 22 — clôture quality gate) au fil de l'eau, regroupé en commit final d'Étape 9.1 ;
- ne touche **pas** au PR body de #158 (déjà finalisé en Étape 9.2 de la spec ultime) sauf pour mentionner « quality gate fix » en addendum (Étape 9.2) ;
- valide chaque étape via `cd backend && ./mvnw verify -DskipITs` (~5-8 min sur le reactor 17 modules avec les nouveaux tests) ;
- watch CI **par étape majeure** : `gh pr checks 158 --watch` à la fin de chaque Étape ≥ 2 jusqu'à terminaison ;
- en cas d'échec CI, **fixe la cause racine** — jamais de `--no-verify`, jamais de `@Disabled`, jamais de skip silencieux, jamais d'exclusion Sonar arbitraire de classes business ;
- en cas de doute sur la couverture d'un test (« est-ce que ce test couvre bien la branche X ? »), valide localement avec :
  ```bash
  cd backend && ./mvnw -pl services/<X>-service test -Dtest=<TestClass>
  awk -F, 'NR>1 && $3=="<ClassUnderTest>" {print}' \
      services/<X>-service/target/jacoco-report/jacoco.csv
  ```

Toute déviation par rapport aux décisions A-E doit être **actée explicitement** dans le commit message + dans `sprint-context.md` § Étape 22, avec justification concrète.

> **Leçon Option B (rappel).** Le `<sonar.projectKey>` overridé dans un POM enfant est **silencieusement ignoré** par `sonar-maven-plugin` 4.0.0.4121 quand `sonar:sonar` est invoqué depuis le reactor parent — toute analyse atterrit dans le `sonar.projectKey` du module top-level. Cette spec retire ces overrides et consolide tout dans `unige-events-backend` (Décision A).

> **Leçon couverture jacoco multi-module (rappel).** `${project.build.directory}/jacoco-report/jacoco.xml` au pom racine pointe sur le `target/` du parent (qui n'a aucun source post-migration → 0 % coverage). Le fix Option B passe une **liste comma-séparée explicite** des 17 chemins jacoco.xml au scanner via `-Dsonar.coverage.jacoco.xmlReportPaths=...` au CLI (Décision B).

---

## Contexte

### État livré dans la PR #158 à HEAD `2aef8fe2`

(Reprise concise du `sprint-context.md` § Étape 21 — voir le fichier source pour le détail commit-par-commit.)

- **9 vagues finalization-ultimate** livrées (Étapes 1-9 spec ultime), 25+ commits, branche prête au merge code-side.
- **Topology** : 5 services métiers Quarkus (event/user/engagement/moderation/notification) + 10 shared libs + contract-tests + e2e = **17 modules** dans le reactor.
- **0 stub JPA cross-service** (cible STUB-001 atteinte via REST clients + entités refactorées en `@Column id`).
- **5 pacts JSON** brokerless + **1 E2E happy path** gated env var.
- **30 sentinels** SCRUM-138/144/147 avec corps vide taggés `@Tag("legacy-port-s9")` + 1 sentinel `prePersist_setsCreatedAt` porté + 4 sentinels `RecurrenceGeneratorTest` portés (= 35 noms par grep, dont 5 implémentés et 30 placeholders).
- **CI matrix** : 1 cellule shared-libs + 5 cellules services + 1 contract-tests/e2e + 1 frontend, total ~10 min/run.
- **Sonar** : 5 `<sonar.projectKey>` overrides per-service présents dans les POMs ; le step `SonarQube Scan` est livré dans chaque cellule services (avec `continue-on-error: true`) et dans la cellule shared-libs.

### État actuel quality gate SonarCloud (problème de cette spec)

Sur la PR #158 (run CI 25609335496), le check `[unige-events-backend] SonarCloud Code Analysis` est **FAILED** :

| Métrique | Valeur actuelle | Seuil quality gate |
|---|---|---|
| Coverage on new code | **0,6 %** (6 lignes / 985 new lines) | ≥ 80 % |
| Duplications on new code | 1,7 % (estimated after merge) | ≤ 3 % |
| New issues | 51 | 0 conditions |
| Accepted issues | 0 | 0 |
| Security hotspots | 0 | 0 conditions |

**Bug 1 (P0)** — Les 5 `<sonar.projectKey>` overrides per-service dans `services/*-service/pom.xml` sont **ignorés** par `sonar-maven-plugin` quand `sonar:sonar` est invoqué depuis le reactor parent. **Toutes les 6 invocations** Sonar (1 shared-libs + 5 matrix) atterrissent dans le projet `unige-events-backend`. Logs :

```
[INFO] --- sonar:4.0.0.4121:sonar (default-cli) @ parent ---
[INFO] ANALYSIS SUCCESSFUL, you can find the results at:
       https://sonarcloud.io/dashboard?id=unige-pinfo6-2026_unige-events-backend&pullRequest=158
```

→ même la cellule `Build Backend (event)` du matrix scanne dans `unige-events-backend`, **pas** dans `unige-events-event-service`. Les 5 projets services SonarCloud (créés ou non) **ne reçoivent aucune donnée**. Chaque scan **écrase la précédente** sur `unige-events-backend` — on voit la dernière cellule à terminer, c'est aléatoire.

**Bug 2 (P0)** — Le `pom.xml` racine définit :
```xml
<sonar.coverage.jacoco.xmlReportPaths>${project.build.directory}/jacoco-report/jacoco.xml</sonar.coverage.jacoco.xmlReportPaths>
```
`${project.build.directory}` se résout au `backend/target/jacoco-report/jacoco.xml` du parent, qui n'a **aucun code Java après la migration** (0 source files dans le parent reactor). Logs :
```
[WARNING] No coverage report can be found with sonar.coverage.jacoco.xmlReportPaths=
          '/home/runner/work/.../backend/target/jacoco-report/jacoco.xml'
```
→ aucune donnée jacoco transmise à Sonar → coverage rapporté = 0 % sur les classes scannées (en l'occurrence celles du dernier service à finir le matrix).

**Bug 3 (P1)** — Couverture jacoco réelle des 5 services métiers extrêmement basse (mesure locale + audit COV-001 :

| Module | Lignes new code (Java main) | Coverage L actuelle | Target spec ultime | Target cette spec |
|---|---|---|---|---|
| event-service | 3 546 | ~5,4 % | 30-40 % | **≥ 80 %** |
| user-service | 1 460 | ~4,5 % (mesuré local) | 25-35 % | **≥ 80 %** |
| engagement-service | 1 341 | ~10,8 % | 30-40 % | **≥ 80 %** |
| moderation-service | 666 | ~17,2 % | inchangé | **≥ 80 %** |
| notification-service | (placeholder) | n/a | n/a | n/a |
| shared-domain-dtos | 482 | 57,1 % L | ≥ 95 % | **≥ 95 %** |
| shared-domain-projections | 101 | 100 % | ≥ 95 % | ≥ 95 % ✅ |
| 8 autres shared libs | 989 | 100 % | ≥ 95 % | ≥ 95 % ✅ |

→ **30 sentinels `@Tag("legacy-port-s9")`** à porter en runtime (8 engagement + 17 event + 5 user — note : `prePersist_setsCreatedAt` engagement est déjà porté ; voir Annexe A).
→ **56 tests legacy** au commit `41074e9` à porter par batch (DTOs/entities/resources/services/util — voir Annexe B).
→ **~30-40 nouvelles classes** de tests à créer (mappers, internal resources, REST client fallbacks — voir Annexe C).

**Verdict global** : la migration code (PR #158) est complète et correcte, mais la configuration Sonar a deux bugs structurels qui empêchent toute mesure honnête, et la couverture des services est restée au minimum (Décision D Option 3 du spec ultime — pragmatique post-Étape 21 mais incompatible avec un quality gate à 80 %).

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

> **Pour l'exécuteur** : chaque décision A → E ci-dessous est définitive. Aucune ne doit être tranchée au moment de l'implémentation. Si une situation imprévue émerge, applique la règle « principe de moindre surprise vs cette décision » et **acte la déviation** dans le commit message + sprint-context § Étape 22.

### Décision A — Option B définitive : un seul projet SonarCloud `unige-events-backend`

**Décision.** Tout le backend (5 services métiers + 10 shared libs + contract-tests + e2e = 17 modules) est agrégé dans le **seul** projet SonarCloud `unige-pinfo6-2026_unige-events-backend`. Les 5 projets SonarCloud per-service (`unige-events-{event,user,engagement,moderation,notification}-service`) sont **abandonnés** :

- **Côté code** : retrait des 5 `<sonar.projectKey>` + `<sonar.projectName>` des `services/*-service/pom.xml`.
- **Côté DevOps** : item 1 de `devops-handoff.md` **annulé** — pas de création des 5 projets sur SonarCloud (qu'ils existent déjà ou non, ils restent orphelins ; DevOps peut les archiver via UI s'il le souhaite, mais ce n'est pas un blocker).
- **CI** : un seul job `sonar-aggregate` final (Décision C) scanne tout le reactor.

**Justification.** (a) Sur PR #158, les 5 `<sonar.projectKey>` per-service sont **silencieusement ignorés** par `sonar-maven-plugin` (cf. Contexte ci-dessus) — l'overhead de maintenir 5 projets pour zéro bénéfice fonctionnel. (b) Aligné avec l'état pré-migration de `main` (un seul projet `unige-events-backend`). (c) Compromis pédagogique : pour un projet pinfo6 à 6 mois, 5 dashboards Sonar séparés n'apportent rien vs un seul dashboard agrégé. (d) Le quality gate s'applique uniformément au backend — pas de risque de gate « passé » sur 4 services et « échoué » sur 1 (incohérence opérationnelle).

**Alternatives écartées.** (a) Garder les 5 projets et fixer le bug — nécessite de scanner depuis chaque service (`-pl services/<X>-service` sans le `.`) avec `-Dsonar.projectKey=...` explicite + créer les 5 projets côté DevOps. Bénéfice : dashboards par bounded context. Coût : 5 quality gates à passer, doublon CI, divergence vs main. **Trop d'overhead pour un projet pinfo6**. (b) Sonar à la racine post-matrix sans agrégation jacoco multi-paths : revient à l'état actuel (0 % coverage).

**Adresse.** Bug 1 (per-service projectKey ignoré), CI cohérence avec main, devops-handoff item 1.

---

### Décision B — Aggregation jacoco : liste comma-séparée explicite au CLI

**Décision.** Le scan Sonar final passe une liste **comma-séparée** des 17 fichiers `target/jacoco-report/jacoco.xml` (10 shared libs + 5 services + contract-tests + e2e) via `-Dsonar.coverage.jacoco.xmlReportPaths=...` au CLI Maven, **PAS** via une property POM. Cela court-circuite le bug `${project.build.directory}` du pom racine (Bug 2).

**Pattern CI** :
```yaml
- name: Aggregate Sonar scan
  run: |
    JACOCO_PATHS=$(find services contract-tests e2e \
        -name jacoco.xml -path '*/jacoco-report/*' | paste -sd ',')
    ./mvnw -pl . sonar:sonar -B \
        -Dsonar.coverage.jacoco.xmlReportPaths=$JACOCO_PATHS
```

**Justification.** (a) Les 17 modules ont chacun leur `target/jacoco-report/jacoco.xml` (généré par `quarkus-jacoco` ou `jacoco-maven-plugin` selon le module) — Sonar consomme tous les rapports en une passe et fusionne par classe. (b) `find` dynamique au lieu de hardcoder 17 paths : robuste si on ajoute/renomme un module. (c) Pas de modification du pom racine (la property reste en place pour les invocations locales `mvn sonar:sonar` mono-module, qui résolvent `${project.build.directory}` correctement).

**Note importante** : `quarkus-jacoco` génère `jacoco.xml` dans `target/jacoco-report/` (pas `target/site/jacoco/`). Le `find` ci-dessus ne match QUE le path `quarkus-jacoco`. Le `notification-service` doit avoir `quarkus-jacoco` (DEP-001 fixé Étape 7.2 spec ultime — vérifier). Pour `contract-tests` + `e2e`, ajouter `quarkus-jacoco` si absent (sinon ils n'apparaissent pas dans le `find`).

**Alternatives écartées.** (a) Modifier le pom racine pour pointer sur un path agrégé : nécessiterait `jacoco:report-aggregate` (mojo séparé qui produit un report agrégé au parent — fonctionne mais ajoute une étape build, et `quarkus-jacoco` ne supporte pas nativement ce mojo). (b) Hardcoder 17 paths : fragile, maintenance manuelle.

**Adresse.** Bug 2 (path jacoco invalide).

---

### Décision C — Job CI `sonar-aggregate` unique post-matrix

**Décision.** Restructurer `.github/workflows/build.yml` :

1. **Supprimer** le step `SonarQube Scan` du job `build-shared-libs` et de chaque cellule `build-backend (matrix)`.
2. **Ajouter** un step `actions/upload-artifact@v4` à la fin de chaque cellule produisant des `jacoco.xml` :
   - `build-shared-libs` : upload `services/shared-*/target/jacoco-report/jacoco.xml` (10 fichiers via 1 artifact)
   - chaque cellule `build-backend (matrix=<svc>)` : upload `services/<svc>-service/target/jacoco-report/jacoco.xml`
   - `build-contract-and-e2e` : upload `contract-tests/target/jacoco-report/jacoco.xml` + `e2e/target/jacoco-report/jacoco.xml`
3. **Ajouter** un job `sonar-aggregate` qui :
   - dépend (`needs:`) de `build-shared-libs` + `build-backend` (matrix complet) + `build-contract-and-e2e`
   - checkout du code (avec `fetch-depth: 0` pour Sonar PR analysis)
   - download des 6 artifacts jacoco dans le tree (`actions/download-artifact@v4` avec `merge-multiple: true`)
   - lance `./mvnw -pl . sonar:sonar -B -Dsonar.coverage.jacoco.xmlReportPaths=$JACOCO_PATHS`
   - `continue-on-error: false` — gate strict, bloque la PR si fail.

**Justification.** (a) Préserve la matrix par service (build parallèle des 5 services en ~3 min total au lieu de séquentiel ~10 min). (b) Centralise le scan Sonar en 1 invocation = 1 analyse cohérente sur unige-events-backend (vs 6 scans qui s'écrasaient). (c) `continue-on-error: false` aligné avec quality gate strict — fail-fast. (d) Pas besoin de créer le projet `unige-events-backend` côté DevOps (existe déjà depuis pré-migration). (e) Pas de besoin secret supplémentaire (`SONAR_TOKEN` déjà présent).

**Alternatives écartées.** (a) Garder 6 scans Sonar concurrents : impossible — ils écrasent le même projectKey (cf. Bug 1). (b) 1 job mono-cellule monolithique (sans matrix) qui build tout + Sonar : sacrifie le parallélisme matrix (5 min → 15 min CI), régression performance.

**Adresse.** Bugs 1 + 2 conjointement, simplifie l'organisation CI.

---

### Décision D — Couverture services : port runtime des 30 sentinels + 56 tests legacy

**Décision.** Pour atteindre `coverage on new code ≥ 80 %` sur les ~7 000 lignes de code Java main des 5 services métiers, l'exécuteur **porte en runtime** :

1. **Les 30 sentinels `@Tag("legacy-port-s9")`** actuellement à corps vide (8 engagement + 17 event + 5 user — voir Annexe A) → assertions réelles + retrait du tag `@Tag("legacy-port-s9")`.

2. **Les 56 tests legacy-monolith** du commit `41074e9` (voir Annexe B) → adaptés aux nouveaux packages des 5 services post-consolidation 14→5 + REST clients mockés via `@InjectMock @RestClient` au lieu des `XStub.findByIdOptional(...)` + JPA cross-service écrits en plain `entityManager.persist(...)` ou Panache `<Entity>.findById(...)`.

3. **Tests nouveaux pour le code refactor post-stub-removal** (mappers, internal resources, REST client fallback methods, NotFoundExceptionMapper sur 4 services consumers) — voir Annexe C.

**Périmètre par service** (estimation effort — voir Annexes pour la liste détaillée) :

| Service | Sentinels | Legacy ports | Nouveaux tests | Total tests à ajouter |
|---|---|---|---|---|
| engagement-service | 7 | ~12 | ~8 | ~27 |
| user-service | 6 | ~12 | ~6 | ~24 |
| event-service | 17 | ~22 | ~12 | ~51 |
| moderation-service | 0 | ~5 | ~6 | ~11 |
| shared-domain-dtos | 0 | 0 | ~5 | ~5 |
| **Total** | **30** | **~51** | **~37** | **~118** |

**Justification.** (a) Le port legacy est **mécanique** — les tests existent déjà, on adapte les imports et les mocks. (b) Le service-level mock-based testing (`@QuarkusTest` + `@InjectMock @RestClient` + Panache mock via `@InjectMock <Entity>`) couvre la majeure partie de la logique sans nécessiter DevServices PostgreSQL (= tests rapides en CI < 30 s par classe). (c) Les `*CoverageTest.java` du legacy sont déjà écrits en mock-style (cf. `EventServiceCoverageTest`, `UserServiceCoverageTest`) — port quasi 1:1 avec adaptation REST clients. (d) Pas de DevServices Postgres pour la majorité des tests = CI rapide ; pour les 5-6 tests qui nécessitent DB réelle (ex. `prePersist`, advisory lock, cascade delete), `@QuarkusTest` + `quarkus-jdbc-h2` test profile.

**Alternatives écartées.** (a) Baisser le quality gate à 25-40 % : bidouille du gate, va à l'encontre de l'intent du user (« très bon coverage »). (b) Exclure massivement le code business via `sonar.coverage.exclusions` : masque la dette, hostile à la maintenabilité. (c) Garder les 30 sentinels en placeholders : ils n'ajoutent **aucune** ligne couverte (`@Tag` + corps vide = méthode test couvre `void X() {}` qui a 0 ligne instrumentée).

**Adresse.** Bug 3 (couverture 4-17 % L), TEST-001 (port runtime), COV-001 (services métiers à 80 %), COV-002 (shared-domain-dtos à 95 %).

**Note d'industrialisation.** Le port se fait par **batch par service**, en suivant l'ordre Vague 4 → Vague 7 (engagement, user, event, moderation). Chaque batch est un commit atomique. L'exécuteur ne fragmente PAS au niveau du test individuel (sinon 118 commits = ingérable) — granularité = 1 commit par classe sous test, ou 1 commit par fichier de test.

---

### Décision E — Quality gate Sonar par défaut conservé (≥ 80 % coverage on new code)

**Décision.** Le quality gate SonarCloud par défaut « Sonar way » est **conservé strict** :
- Coverage on new code ≥ 80 %
- Duplications on new code ≤ 3 %
- Maintainability/Reliability/Security rating ≤ A
- Security hotspots reviewed = 100 %

**Aucune** modification du gate via UI SonarCloud n'est livrée par cette spec — la cible est atteinte par **ajout de tests**, pas par baisse du seuil.

**Justification.** (a) Aligné avec le user intent (« très bon coverage »). (b) Évite la dette d'avoir un gate « custom » à maintenir. (c) Le gate par défaut sert aussi pour les futures PRs — laisser à 80 % responsabilise l'équipe sur la couverture des nouveaux changements.

**Alternatives écartées.** (a) Custom gate à 70 % (Décision D Option 3 du spec ultime) : compromis pragmatique S8, plus nécessaire S9. (b) Custom gate à 50 % : bidouille, va à l'encontre de l'intent.

**Adresse.** intent user, propreté quality gate long terme.

**Note** : si après livraison de cette spec, certains modules continuent d'échouer le gate à 80 % pour une raison structurelle (ex. classe Quarkus bootstrap inévitable), l'exécuteur peut ajouter une **exclusion ciblée** via `sonar.coverage.exclusions` dans le pom **du module concerné** (pas dans le pom racine), avec **commentaire JavaDoc justifiant** — pas de blanket exclusion. Toute exclusion doit être documentée dans le commit message + sprint-context.

---

## Architecture cible CI post-implémentation

### Diagramme du nouveau workflow `build.yml`

```
                              push / pull_request
                                       │
                                       ▼
                           ┌──────────────────────┐
                           │   Lint PR title      │ (déjà en place)
                           └──────────────────────┘
                                       │
              ┌────────────────────────┼─────────────────────┐
              │                        │                     │
              ▼                        ▼                     ▼
    ┌──────────────────┐    ┌──────────────────────┐  ┌────────────────┐
    │ build-shared-libs│    │   build-backend      │  │ build-frontend │
    │   (1 cellule)    │    │   (matrix × 5)       │  │  (1 cellule)   │
    │                  │    │                      │  │                │
    │  • mvnw install  │    │  • mvnw install      │  │  • npm test    │
    │    (10 libs)     │    │    + image push      │  │  • SonarQube   │
    │  • upload        │    │  • upload jacoco     │  │    (frontend   │
    │    jacoco × 10   │    │    × 5               │  │     project)   │
    └────────┬─────────┘    └──────────┬───────────┘  └────────────────┘
             │                         │
             │     ┌───────────────────┴────────────────────┐
             │     │                                        │
             │     ▼                                        ▼
             │  ┌────────────────────────────┐    ┌────────────────────┐
             │  │  build-contract-and-e2e    │    │  Tests légers      │
             │  │  (1 cellule)               │    │   (pacts JSON      │
             │  │  • mvnw install -pl        │    │    artefact)       │
             │  │    contract-tests,e2e -am  │    └────────────────────┘
             │  │  • upload jacoco × 2       │
             │  └────────────┬───────────────┘
             │               │
             └───────┬───────┘
                     │
                     ▼
        ┌─────────────────────────────────────────┐
        │      sonar-aggregate (NEW)              │
        │      • download all jacoco artifacts    │
        │      • find target/jacoco-report/*.xml  │
        │        → comma-separated list           │
        │      • mvnw -pl . sonar:sonar           │
        │        -Dsonar.coverage.jacoco.xml      │
        │           ReportPaths=<list>            │
        │      • continue-on-error: false         │
        └─────────────────────┬───────────────────┘
                              │
                              ▼
              ┌──────────────────────────────┐
              │  [unige-events-backend]      │
              │  SonarCloud Code Analysis    │
              │  (PR check, fed by Sonar)    │
              └──────────────────────────────┘
                              │
                              ▼
                   Quality Gate PASSED ?
                              │
                ┌─────────────┴────────────┐
                │                          │
                ▼                          ▼
          PR mergeable             PR bloquée
```

### Changements YAML synthétiques

| Job | Avant | Après |
|---|---|---|
| `build-shared-libs` | mvn install + Sonar scan racine | mvn install + upload jacoco artifacts |
| `build-backend (matrix)` | mvn install + Sonar scan (continue-on-error) | mvn install + upload jacoco artifact |
| `build-contract-and-e2e` | mvn install (no Sonar) | mvn install + upload jacoco artifacts |
| `build-frontend` | inchangé | inchangé (Sonar dédié frontend project) |
| `sonar-aggregate` | **N'EXISTE PAS** | **NEW** : download artifacts + 1 scan agrégé |

### POMs synthétiques

| Pom | Avant | Après |
|---|---|---|
| `backend/pom.xml` | `sonar.projectKey=...backend` + `xmlReportPaths=${project.build.directory}/jacoco-report/jacoco.xml` | inchangé (la property reste pour invocations locales mono-module) |
| `services/event-service/pom.xml` | `<sonar.projectKey>...event-service</sonar.projectKey>` | **retiré** |
| `services/user-service/pom.xml` | `<sonar.projectKey>...user-service</sonar.projectKey>` | **retiré** |
| `services/engagement-service/pom.xml` | `<sonar.projectKey>...engagement-service</sonar.projectKey>` | **retiré** |
| `services/moderation-service/pom.xml` | `<sonar.projectKey>...moderation-service</sonar.projectKey>` | **retiré** |
| `services/notification-service/pom.xml` | `<sonar.projectKey>...notification-service</sonar.projectKey>` | **retiré** |
| `services/contract-tests/pom.xml` | (pas de jacoco) | ajouter `quarkus-jacoco` (si absent) |
| `services/e2e/pom.xml` | (pas de jacoco) | ajouter `quarkus-jacoco` (si absent) |

---

## Plan d'implémentation par étape (ORDRE STRICT)

### Étape 0 — Pré-flight

**Objectif** : valider l'état initial avant tout commit. Doit être exécutée **AVANT** toute modification.

**Commandes** :
```bash
git rev-parse HEAD                                              # 2aef8fe2 ou descendant
git status --porcelain                                          # vide (ou .devcontainer/devcontainer-lock.json untracked OK)
git diff --shortstat origin/main HEAD -- frontend/              # 0 ligne
git diff --shortstat origin/main HEAD -- openapi/               # 0 ligne
ls backend/services/ | grep -E '\-service$' | sort              # 5 lignes : engagement/event/moderation/notification/user
grep -c '<module>' backend/pom.xml                              # 17
find backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l   # 0
cd backend && ./mvnw -B -DskipITs verify | tail -5              # SUCCESS
gh auth status                                                   # logged in
gh pr view 158 --json state,headRefName --jq '.'                # state=OPEN, headRefName=refactor(backend)--migrate-to-microservices
```

Si une de ces vérifications échoue, **STOP** et reporte à l'humain (Elie). Ne pas tenter de corriger l'état initial — la spec assume un point de départ propre `2aef8fe2`.

**Pas de commit.** Étape de validation seulement.

---

### Étape 1 — CI/Sonar fix (Vague 1, 4 commits)

#### Étape 1.1 — Retrait des `<sonar.projectKey>` per-service (Décision A)

**Objectif** : retirer les overrides per-service dans les 5 POMs `services/*-service/pom.xml`. Les 5 modules héritent désormais du `sonar.projectKey=...unige-events-backend` du parent (Option B définitive).

**Patch concret** (× 5 fichiers, exactement le même bloc à retirer dans chaque) :

```xml
<!-- AVANT, dans backend/services/<X>-service/pom.xml lignes 12-15 environ -->
<properties>
    <sonar.projectKey>unige-pinfo6-2026_unige-events-<X>-service</sonar.projectKey>
    <sonar.projectName>unige-events-<X>-service</sonar.projectName>
</properties>

<!-- APRÈS — supprimer les 2 lignes Sonar.* ; si <properties> devient vide, supprimer la balise -->
```

**Fichiers** :
- `backend/services/event-service/pom.xml`
- `backend/services/user-service/pom.xml`
- `backend/services/engagement-service/pom.xml`
- `backend/services/moderation-service/pom.xml`
- `backend/services/notification-service/pom.xml`

**Commande de validation** :
```bash
grep -rln '<sonar.projectKey>' backend/services/*-service/pom.xml
# → vide (0 résultat)
grep '<sonar.projectKey>' backend/pom.xml
# → 1 résultat : unige-pinfo6-2026_unige-events-backend
cd backend && ./mvnw -B -DskipITs verify | tail -5
# → BUILD SUCCESS sur 17 modules
```

**Commit** : `chore(backend): remove per-service sonar.projectKey overrides (Étape 1.1, Option B définitive)`

---

#### Étape 1.2 — `quarkus-jacoco` sur `contract-tests` + `e2e` si absent

**Objectif** : assurer qu'un `target/jacoco-report/jacoco.xml` est produit par tous les modules pour que le `find` du job `sonar-aggregate` les capture.

**Vérification préalable** :
```bash
grep -A1 'quarkus-jacoco' backend/contract-tests/pom.xml backend/e2e/pom.xml
```

**Patch conditionnel** (uniquement si `quarkus-jacoco` est absent) — ajouter dans `<dependencies>` :

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jacoco</artifactId>
    <scope>test</scope>
</dependency>
```

**Commande de validation** :
```bash
cd backend && ./mvnw -pl contract-tests,e2e -am verify -DskipITs -B
ls contract-tests/target/jacoco-report/jacoco.xml
ls e2e/target/jacoco-report/jacoco.xml
# Les deux fichiers doivent exister (taille > 0)
```

**Note** : si les modules ne contiennent que des tests Pact/E2E (pas de classes `src/main/java`), `jacoco.xml` est vide-mais-présent et c'est OK — Sonar le consomme sans erreur.

**Commit** (conditionnel) : `chore(backend): add quarkus-jacoco to contract-tests + e2e for aggregated coverage (Étape 1.2)`

---

#### Étape 1.3 — Refactor `build.yml` : retirer scans Sonar per-cell + upload artefacts

**Objectif** : préparer le terrain pour le job d'agrégation (Étape 1.4) en retirant les 6 scans Sonar concurrents et en uploadant les jacoco.xml comme artifacts GitHub Actions.

**Patch** (`.github/workflows/build.yml`) :

```yaml
# Job build-shared-libs — RETIRER le step `SonarQube Scan (root reactor — Option B unige-events-backend)`
# AJOUTER après le step `Build & Test all shared libs` :

- name: Upload jacoco artifacts (shared libs)
  uses: actions/upload-artifact@v4
  with:
    name: jacoco-shared-libs
    path: backend/services/shared-*/target/jacoco-report/jacoco.xml
    if-no-files-found: error
    retention-days: 1

# Job build-backend (matrix) — RETIRER le step `SonarQube Scan` complet (avec son continue-on-error)
# AJOUTER après le step `Build & Test (with image build/push)` :

- name: Upload jacoco artifact (${{ matrix.service }}-service)
  uses: actions/upload-artifact@v4
  with:
    name: jacoco-${{ matrix.service }}-service
    path: backend/services/${{ matrix.service }}-service/target/jacoco-report/jacoco.xml
    if-no-files-found: error
    retention-days: 1

# Job build-contract-and-e2e — AJOUTER après le step `Build & Test contract-tests + e2e` :

- name: Upload jacoco artifact (contract-tests)
  uses: actions/upload-artifact@v4
  with:
    name: jacoco-contract-tests
    path: backend/contract-tests/target/jacoco-report/jacoco.xml
    if-no-files-found: warn
    retention-days: 1

- name: Upload jacoco artifact (e2e)
  uses: actions/upload-artifact@v4
  with:
    name: jacoco-e2e
    path: backend/e2e/target/jacoco-report/jacoco.xml
    if-no-files-found: warn
    retention-days: 1
```

**Commande de validation** : push à la branche, watch CI :
```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
# Vérifier dans le run : 
# • build-shared-libs ✅ + artifact jacoco-shared-libs visible (gh run view <RUN_ID>)
# • 5 cellules build-backend ✅ + 5 artifacts jacoco-<svc>-service visibles
# • build-contract-and-e2e ✅ + 2 artifacts visibles
# • La cellule [unige-events-backend] SonarCloud Code Analysis : DISPARAÎT (puisqu'aucun scan Sonar n'a été pushé)
#   → temporairement absent, sera remis par Étape 1.4
```

**Commit** : `ci(backend): drop per-cell sonar scans + upload jacoco artifacts (Étape 1.3)`

---

#### Étape 1.4 — Job `sonar-aggregate` : 1 scan Sonar agrégé final

**Objectif** : ajouter le job final qui consomme les artifacts jacoco et lance UN scan Sonar agrégé qui peuple `unige-events-backend` avec coverage > 0 %.

**Patch** (`.github/workflows/build.yml`, ajouté **APRÈS** `build-contract-and-e2e`, **AVANT** `build-frontend` — l'ordre n'importe pas mais on garde la cohérence) :

```yaml
# Étape 22 quality-gate (Option B définitive) : un seul scan Sonar agrégé sur
# unige-events-backend, alimenté par les 6 artifacts jacoco uploadés par les jobs
# amont. Les <sonar.projectKey> per-service ont été retirés (Étape 1.1) — toutes
# les classes Java main des 17 modules sont scannées dans un seul projet.
sonar-aggregate:
  name: Sonar Aggregate
  runs-on: ubuntu-latest
  needs:
    - build-shared-libs
    - build-backend
    - build-contract-and-e2e
  defaults:
    run:
      working-directory: backend
  steps:
    - uses: actions/checkout@v6
      with:
        # fetch-depth: 0 nécessaire pour Sonar PR analysis (blame git history).
        fetch-depth: 0

    - uses: actions/setup-java@v5
      with:
        java-version: 21
        distribution: temurin
        cache: maven

    # Re-build (sans tests) pour repeupler le local m2 avec les jars shared-libs +
    # services. Sonar a besoin des .class compilés et du jandex index pour analyser
    # via le sonar-maven-plugin. Le -DskipTests évite le coût ~5 min de re-runner
    # les tests, on a déjà les jacoco reports via les artifacts amont.
    - name: Re-compile reactor (skip tests)
      run: ./mvnw install -DskipTests -B

    # Download des 6 artifacts jacoco dans leurs paths d'origine via merge-multiple.
    # Le pattern: jacoco-<name> est le nom de l'artifact ; merge-multiple re-empile
    # les fichiers à leur path source (services/<X>-service/target/jacoco-report/jacoco.xml).
    - name: Download all jacoco artifacts
      uses: actions/download-artifact@v4
      with:
        path: backend
        pattern: jacoco-*
        merge-multiple: true

    # Verification que les 17 fichiers jacoco.xml sont bien là.
    - name: List jacoco files
      run: |
        find services contract-tests e2e -name jacoco.xml -path '*/jacoco-report/*' | sort
        count=$(find services contract-tests e2e -name jacoco.xml -path '*/jacoco-report/*' | wc -l)
        echo "Found $count jacoco.xml files"
        # Si on a moins de 15 (10 shared + 5 services), c'est un fail car
        # contract-tests + e2e peuvent légitimement manquer (modules test-only).
        [ "$count" -ge 15 ] || (echo "FAIL: expected >= 15 jacoco files" && exit 1)

    # Liste comma-séparée des paths jacoco, passée au CLI Sonar (Décision B).
    - name: SonarQube Scan (aggregated)
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: |
        JACOCO_PATHS=$(find services contract-tests e2e \
            -name jacoco.xml -path '*/jacoco-report/*' | paste -sd ',')
        echo "Sonar coverage paths: $JACOCO_PATHS"
        ./mvnw -pl . sonar:sonar -B \
            -Dsonar.coverage.jacoco.xmlReportPaths=$JACOCO_PATHS
```

**Note importante sur `Re-compile reactor`** : le scanner Sonar Maven a besoin du **bytecode compilé** (.class) pour son analyseur sémantique (sonar-java). Sans `mvn install`, les modules ne sont pas dans le local m2 et l'analyse échoue. `-DskipTests` évite la régression CI (~3-5 min) puisque les tests ont déjà tourné dans les jobs amont. Trade-off : on dépense ~2 min de compile mais on garde le scan strict.

**Alternative écartée** : passer `cache: 'maven'` + reconstruire le local m2 depuis le cache. Fragile (la matrix peut avoir partiellement écrit dans le cache de l'autre job). La recompile est sûre.

**Commande de validation** : push, watch CI :
```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
# Vérifier :
# • Job sonar-aggregate ✅ (~5-7 min)
# • Logs : "Found 15+ jacoco.xml files" + "ANALYSIS SUCCESSFUL"
# • Cellule [unige-events-backend] SonarCloud Code Analysis revient en mode FAILED (parce que
#   coverage est encore basse, mais désormais la donnée est PRÉSENTE).
# • https://sonarcloud.io/dashboard?id=unige-pinfo6-2026_unige-events-backend&pullRequest=158
#   doit montrer New Coverage > 0 % (typiquement entre 5 et 15 % à ce stade — avant ports tests).
```

**Commit** : `ci(backend): add sonar-aggregate job for Option B aggregated scan (Étape 1.4, Décisions B+C)`

---

#### Validation fin Étape 1

```bash
gh pr checks 158
# Attendu :
# ✓ Build / Build All Shared Libs                 SUCCESS
# ✓ Build / Build Backend (event)                 SUCCESS
# ✓ Build / Build Backend (user)                  SUCCESS
# ✓ Build / Build Backend (engagement)            SUCCESS
# ✓ Build / Build Backend (moderation)            SUCCESS
# ✓ Build / Build Backend (notification)          SUCCESS
# ✓ Build / Build Contract Tests + E2E            SUCCESS
# ✓ Build / Build Frontend                        SUCCESS
# ✓ Build / Sonar Aggregate                       SUCCESS  ← NOUVEAU
# ✗ [unige-events-backend] SonarCloud Code Analysis  FAILURE (Quality Gate FAILED on coverage)
# ✓ [unige-events-frontend] SonarCloud Code Analysis  SUCCESS

# La cellule SonarCloud reste en FAILED jusqu'à ce que les Vagues 2-7 (ports tests) atteignent les 80 %.
# C'est ATTENDU et OK à ce stade — le bug de configuration est fixé, reste le bug de coverage.
```

**Watch CI groupé** : `gh pr checks 158 --watch` jusqu'à ce que tout les jobs (sauf SonarCloud cell) soient verts.

---

### Étape 2 — Shared-domain-dtos coverage gap (Vague 2, 1 commit)

#### Étape 2.1 — Test `EventCoOrganizerDTOTest` + REST client fallback methods

**Objectif** : amener `shared-domain-dtos` de 57,1 % L à ≥ 95 % L. Les classes manquantes sont :

| Classe | Lignes missed | Type | Test à écrire |
|---|---|---|---|
| `EventCoOrganizerDTO` | 1 (factory `from`) | record + factory | `EventCoOrganizerDTOTest` (3 cas) |
| `UserServiceClient` | 1 (fallback `getByIdFallback`) | interface + default method | dans `UserServiceClientFallbackTest` |
| `EventServiceClient` | 4 (4 fallback methods) | interface + 4 default methods | dans `EventServiceClientFallbackTest` |
| `EngagementServiceClient` | 3 (3 fallback methods) | interface + 3 default methods | dans `EngagementServiceClientFallbackTest` |

**Note** : les **méthodes abstraites** des `@RegisterRestClient` interfaces ne sont **pas instrumentées** par jacoco (signatures sans bytecode exécutable). Seules les méthodes `default` (fallbacks) le sont. Pour les couvrir, on instancie une **classe anonyme** qui implémente l'interface en jetant `UnsupportedOperationException` sur les abstraites, et on appelle directement les `default` methods.

**Fichier 1** — `backend/services/shared-domain-dtos/src/test/java/ch/unige/events/shared/domain/dto/EventCoOrganizerDTOTest.java` :

```java
package ch.unige.events.shared.domain.dto;

import ch.unige.events.shared.domain.enums.CoOrganizerStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventCoOrganizerDTOTest {

    @Test
    void from_acceptedCoOrg_buildsDTOWithFullFields() {
        UUID userId = UUID.randomUUID();
        LocalDateTime acceptedAt = LocalDateTime.now();
        EventCoOrganizerDTO dto = EventCoOrganizerDTO.from(
            userId, "Alice", CoOrganizerStatus.ACCEPTED, acceptedAt);
        assertEquals(userId, dto.userId());
        assertEquals("Alice", dto.displayName());
        assertEquals(CoOrganizerStatus.ACCEPTED, dto.status());
        assertEquals(acceptedAt, dto.acceptedAt());
    }

    @Test
    void from_pendingCoOrg_acceptedAtIsNull() {
        EventCoOrganizerDTO dto = EventCoOrganizerDTO.from(
            UUID.randomUUID(), "Bob", CoOrganizerStatus.PENDING, null);
        assertNull(dto.acceptedAt());
    }

    @Test
    void recordEqualsAndHashCode_canonicalContract() {
        UUID id = UUID.randomUUID();
        LocalDateTime t = LocalDateTime.now();
        EventCoOrganizerDTO a = new EventCoOrganizerDTO(id, "A", CoOrganizerStatus.ACCEPTED, t);
        EventCoOrganizerDTO b = new EventCoOrganizerDTO(id, "A", CoOrganizerStatus.ACCEPTED, t);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
```

**Fichier 2** — `backend/services/shared-domain-dtos/src/test/java/ch/unige/events/shared/client/EventServiceClientFallbackTest.java` :

```java
package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.EventDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Direct unit test of the @Fallback default methods on EventServiceClient.
 * These methods are jacoco-instrumented (bytecode in the interface) and would
 * otherwise be 0 % covered since the abstract methods are proxy-only and not
 * exercised in unit tests. Pact tests cover the abstract HTTP contracts;
 * this test covers the local fallback paths invoked by MicroProfile Fault
 * Tolerance when the upstream is unreachable.
 */
class EventServiceClientFallbackTest {

    private static final EventServiceClient CLIENT = new EventServiceClient() {
        @Override public EventDTO getById(long id) { throw new UnsupportedOperationException(); }
        @Override public EventDTO getByIdWithCoOrgCheck(long id, UUID u) { throw new UnsupportedOperationException(); }
        @Override public List<EventDTO> findByIds(List<Long> ids, String s) { throw new UnsupportedOperationException(); }
        @Override public List<UUID> getOrganizerUuids(long id) { throw new UnsupportedOperationException(); }
    };

    @Test
    void getByIdFallback_returnsNull() {
        assertNull(CLIENT.getByIdFallback(42L));
    }

    @Test
    void getByIdWithCoOrgCheckFallback_returnsNull() {
        assertNull(CLIENT.getByIdWithCoOrgCheckFallback(42L, UUID.randomUUID()));
    }

    @Test
    void findByIdsFallback_returnsEmptyList() {
        List<EventDTO> r = CLIENT.findByIdsFallback(List.of(1L, 2L), "PUBLISHED");
        assertNotNull(r);
        assertTrue(r.isEmpty());
    }

    @Test
    void getOrganizerUuidsFallback_returnsEmptyList() {
        List<UUID> r = CLIENT.getOrganizerUuidsFallback(42L);
        assertNotNull(r);
        assertTrue(r.isEmpty());
    }
}
```

**Fichier 3** — `backend/services/shared-domain-dtos/src/test/java/ch/unige/events/shared/client/UserServiceClientFallbackTest.java` :

```java
package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.UserPublicResponse;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;

class UserServiceClientFallbackTest {

    private static final UserServiceClient CLIENT = new UserServiceClient() {
        @Override public UserPublicResponse getById(UUID id) { throw new UnsupportedOperationException(); }
    };

    @Test
    void getByIdFallback_returnsNull() {
        assertNull(CLIENT.getByIdFallback(UUID.randomUUID()));
    }
}
```

**Fichier 4** — `backend/services/shared-domain-dtos/src/test/java/ch/unige/events/shared/client/EngagementServiceClientFallbackTest.java` :

```java
package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.AttendanceSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EngagementServiceClientFallbackTest {

    private static final EngagementServiceClient CLIENT = new EngagementServiceClient() {
        @Override public AttendanceSummary getAttendanceSummary(long id) { throw new UnsupportedOperationException(); }
        @Override public List<AttendanceDTO> getUserAttendances(UUID id, String s) { throw new UnsupportedOperationException(); }
        @Override public Map<Long, AttendanceSummary> getAttendanceSummariesBulk(List<Long> ids) { throw new UnsupportedOperationException(); }
    };

    @Test
    void getAttendanceSummaryFallback_returnsZeroSummary() {
        AttendanceSummary s = CLIENT.getAttendanceSummaryFallback(42L);
        assertNotNull(s);
        assertEquals(0L, s.attending());
        assertEquals(0L, s.waitlisted());
    }

    @Test
    void getUserAttendancesFallback_returnsEmptyList() {
        List<AttendanceDTO> r = CLIENT.getUserAttendancesFallback(UUID.randomUUID(), "ATTENDING");
        assertTrue(r.isEmpty());
    }

    @Test
    void getAttendanceSummariesBulkFallback_returnsEmptyMap() {
        Map<Long, AttendanceSummary> r = CLIENT.getAttendanceSummariesBulkFallback(List.of(1L, 2L));
        assertTrue(r.isEmpty());
    }
}
```

**Commande de validation** :
```bash
cd backend && ./mvnw -pl services/shared-domain-dtos test -B
awk -F, 'NR>1 {lm+=$8; lc+=$9} END{printf "L:%5.1f%% (%d/%d)\n", lc*100/(lm+lc), lc, lm+lc}' \
    services/shared-domain-dtos/target/jacoco-report/jacoco.csv
# Attendu : ≥ 95 % L
```

**Commit** : `test(backend): cover shared-domain-dtos REST client fallbacks + EventCoOrganizerDTO factory (Étape 2.1, COV-002)`

---

### Étape 3 — Mappers + DTOs locaux + DTOs records (Vague 3, 2 commits)

#### Étape 3.1 — Test `AttendanceDTOMapperTest` + DTOs locaux engagement-service

**Objectif** : couvrir `AttendanceDTOMapper` (engagement-service) à 100 % L. Cette classe orchestre le mapping `Attendance` (entity JPA) + enrichment `displayName` (via `UserServiceClient.getById(...)`) → `AttendanceDTO` (shared). Pivot post-Décision A spec ultime.

**Fichier** — `backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/dto/AttendanceDTOMapperTest.java` :

```java
package ch.unige.events.engagement.attendance.dto;

import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class AttendanceDTOMapperTest {

    @Inject AttendanceDTOMapper mapper;

    @InjectMock @RestClient UserServiceClient userClient;

    @Test
    void from_singleAttendance_enrichesWithDisplayName() {
        UUID userId = UUID.randomUUID();
        Attendance a = new Attendance();
        a.id = 1L;
        a.eventId = 42L;
        a.userId = userId;
        a.status = AttendanceStatus.ATTENDING;
        a.createdAt = LocalDateTime.now();
        when(userClient.getById(userId))
            .thenReturn(new UserPublicResponse(userId, "alice", "Alice", null, null, false, null, 0, 0, null));

        AttendanceDTO dto = mapper.from(a);

        assertEquals(42L, dto.eventId());
        assertEquals(userId, dto.userId());
        assertEquals("Alice", dto.displayName());
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
    }

    @Test
    void from_userClientFallsBackToNull_keepsAnonymousLabel() {
        UUID userId = UUID.randomUUID();
        Attendance a = new Attendance();
        a.eventId = 42L;
        a.userId = userId;
        a.status = AttendanceStatus.WAITLISTED;
        a.createdAt = LocalDateTime.now();
        when(userClient.getById(any())).thenReturn(null);

        AttendanceDTO dto = mapper.from(a);
        assertNotNull(dto);
        // displayName fallback when user not resolvable — should be null or "anonymous"
        // (vérifier le contrat exact de from() — adapter selon implementation actuelle)
    }
}
```

**Commande de validation** :
```bash
cd backend && ./mvnw -pl services/engagement-service test -Dtest=AttendanceDTOMapperTest -B
awk -F, 'NR>1 && $3=="AttendanceDTOMapper" {printf "L:%d/%d\n", $9, $8+$9}' \
    services/engagement-service/target/jacoco-report/jacoco.csv
# Attendu : 100 % L
```

**Commit** : `test(engagement): cover AttendanceDTOMapper with mocked UserServiceClient (Étape 3.1)`

---

#### Étape 3.2 — Test des 4 `EventDTO` locaux event-service (Décision A spec ultime — pivot)

**Objectif** : couvrir les 4 DTOs `EventDTO` (event-service local — sous-packages `event.dto`, `event.me.dto`, `event.coorganizer.dto`, `event.favorite.dto`) à 100 % L. Ces sont des records avec factories statiques `from(Event, ...)` qui projettent l'entity vers DTO consumer-shape.

**Fichiers** :
- `backend/services/event-service/src/test/java/ch/unige/events/event/dto/EventDTOTest.java` (event/dto/EventDTO)
- `backend/services/event-service/src/test/java/ch/unige/events/event/me/dto/EventDTOTest.java` (event/me/dto/EventDTO — variante MyEvents)
- `backend/services/event-service/src/test/java/ch/unige/events/event/coorganizer/dto/EventDTOTest.java` (event/coorganizer/dto/EventDTO — variante co-org-invitations)
- `backend/services/event-service/src/test/java/ch/unige/events/event/favorite/dto/EventDTOTest.java` (event/favorite/dto/EventDTO — variante favorites)

**Skeleton** (pour `event/dto/EventDTOTest.java` — adapter package + champs pour les 3 autres variantes) :

```java
package ch.unige.events.event.dto;

import ch.unige.events.event.entity.Event;
import ch.unige.events.shared.domain.enums.EventCategory;
import ch.unige.events.shared.domain.enums.EventStatus;
import ch.unige.events.shared.domain.enums.Faculty;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class EventDTOTest {

    @Test
    void from_publishedStandalone_includesAllFields() {
        UUID creatorId = UUID.randomUUID();
        Event e = new Event();
        e.id = 42L;
        e.title = "Conference";
        e.description = "Description";
        e.creatorId = creatorId;
        e.startAt = LocalDateTime.of(2026, 6, 1, 10, 0);
        e.endAt = LocalDateTime.of(2026, 6, 1, 18, 0);
        e.faculty = Faculty.SCIENCES;
        e.category = EventCategory.CONFERENCE;
        e.status = EventStatus.PUBLISHED;
        e.tags = Set.of("ai", "ml");
        e.locationCity = "Geneva";
        e.locationAddress = "Rue X";
        e.capacity = 100;
        e.priceCents = 0;
        e.featured = false;

        EventDTO dto = EventDTO.from(e, "Alice", 5L, 2L, false, false);

        assertEquals(42L, dto.id());
        assertEquals("Conference", dto.title());
        assertEquals(creatorId, dto.creatorId());
        assertEquals("Alice", dto.creatorDisplayName());
        assertEquals(5L, dto.attendingCount());
        assertEquals(2L, dto.waitlistedCount());
        assertEquals(EventStatus.PUBLISHED, dto.status());
        assertNull(dto.parentEventId());
    }

    @Test
    void from_recurringParent_exposesRecurrenceRule() {
        Event e = new Event();
        e.id = 100L;
        e.recurrenceRule = "FREQ=WEEKLY;COUNT=4";
        e.parentEventId = null;
        // ... other required fields ...
        EventDTO dto = EventDTO.from(e, null, 0L, 0L, false, false);
        assertEquals("FREQ=WEEKLY;COUNT=4", dto.recurrenceRule());
        assertNull(dto.parentEventId());
    }

    @Test
    void from_recurringOccurrence_exposesParentEventId() {
        Event e = new Event();
        e.id = 101L;
        e.parentEventId = 100L;
        e.recurrenceRule = null;
        // ...
        EventDTO dto = EventDTO.from(e, null, 0L, 0L, false, false);
        assertEquals(100L, dto.parentEventId());
        assertNull(dto.recurrenceRule());
    }

    @Test
    void from_canceledStatus_propagatesStatus() {
        Event e = new Event();
        e.id = 1L;
        e.status = EventStatus.CANCELLED;
        // ...
        EventDTO dto = EventDTO.from(e, null, 0L, 0L, false, false);
        assertEquals(EventStatus.CANCELLED, dto.status());
    }

    @Test
    void recordEqualsHashCode_consistent() {
        EventDTO a = new EventDTO(/* full ctor */ );
        EventDTO b = new EventDTO(/* same args */);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
```

**Note importante** : la signature de `EventDTO.from(...)` peut différer entre les 4 variantes (paramètres `attendingCount`, `waitlistedCount`, `isFavorited`, `coOrganizerOf`, etc.). L'exécuteur doit lire le fichier source de chaque variante avant d'écrire le test pour adapter la signature.

**Commande de validation** :
```bash
cd backend && ./mvnw -pl services/event-service test -Dtest='*EventDTOTest' -B
awk -F, 'NR>1 && $3=="EventDTO" {printf "L:%d/%d\n", $9, $8+$9}' \
    services/event-service/target/jacoco-report/jacoco.csv
# Attendu : ≥ 90 % L (les variantes ont des factories légèrement différentes ; certaines branches peuvent rester non couvertes si elles ne sont jamais appelées avec ce mix de paramètres)
```

**Commit** : `test(event): cover 4 local EventDTO variants (Étape 3.2, COV-002 + Décision A pivot)`

---

#### Étape 3.3 — Tests records DTOs : `CommentDTO`, `FollowDTO`, `ReportDTO`, etc.

**Objectif** : couvrir tous les records DTOs locaux des 5 services à ≥ 95 % L. Ces records sont triviaux (immutable, avec factory `from(Entity, ...)`) — coverage = 1 test par DTO.

**Fichiers** (1 fichier de test par DTO) :

| Service | DTO | Test path |
|---|---|---|
| engagement | `CommentDTO` | `engagement-service/src/test/java/ch/unige/events/engagement/comment/dto/CommentDTOTest.java` |
| engagement | `AttendanceRequest` | `engagement-service/src/test/java/ch/unige/events/engagement/attendance/dto/AttendanceRequestTest.java` |
| engagement | `CreateCommentRequest` | `engagement-service/src/test/java/ch/unige/events/engagement/comment/dto/CreateCommentRequestTest.java` |
| user | `FollowDTO` | `user-service/src/test/java/ch/unige/events/user/follow/dto/FollowDTOTest.java` |
| user | `UserProfileResponse` | `user-service/src/test/java/ch/unige/events/user/dto/UserProfileResponseTest.java` |
| user | `UserPublicResponse` (local) | `user-service/src/test/java/ch/unige/events/user/dto/UserPublicResponseTest.java` |
| user | `PublicProfileView` | `user-service/src/test/java/ch/unige/events/user/dto/PublicProfileViewTest.java` |
| user | `UpdateProfileRequest` | `user-service/src/test/java/ch/unige/events/user/dto/UpdateProfileRequestTest.java` |
| user | `CalendarTokenResponse` | `user-service/src/test/java/ch/unige/events/user/calendar/dto/CalendarTokenResponseTest.java` |
| event | `CreateEventRequest` | `event-service/src/test/java/ch/unige/events/event/dto/CreateEventRequestTest.java` |
| event | `UpdateEventRequest` | `event-service/src/test/java/ch/unige/events/event/dto/UpdateEventRequestTest.java` |
| event | `EventStatsDTO` | `event-service/src/test/java/ch/unige/events/event/stats/dto/EventStatsDTOTest.java` |
| event | `CoOrganizerDTO` | `event-service/src/test/java/ch/unige/events/event/coorganizer/dto/CoOrganizerDTOTest.java` |
| event | `CoOrganizerInvitationDTO` | `event-service/src/test/java/ch/unige/events/event/coorganizer/dto/CoOrganizerInvitationDTOTest.java` |
| event | `InviteCoOrganizerRequest` | `event-service/src/test/java/ch/unige/events/event/coorganizer/dto/InviteCoOrganizerRequestTest.java` |
| event | `RecurrenceRequest` | `event-service/src/test/java/ch/unige/events/event/dto/RecurrenceRequestTest.java` |
| event | `EventRequestBase` | `event-service/src/test/java/ch/unige/events/event/dto/EventRequestBaseTest.java` |
| event | `ShareResponse` | `event-service/src/test/java/ch/unige/events/event/share/dto/ShareResponseTest.java` |
| moderation | `CreateReportRequest` | `moderation-service/src/test/java/ch/unige/events/report/dto/CreateReportRequestTest.java` |
| moderation | `HandleReportRequest` | `moderation-service/src/test/java/ch/unige/events/report/dto/HandleReportRequestTest.java` |
| moderation | `ReportDTO` | `moderation-service/src/test/java/ch/unige/events/report/dto/ReportDTOTest.java` |

**Skeleton type** (pour un DTO record simple) :

```java
package ch.unige.events.engagement.comment.dto;

import ch.unige.events.engagement.comment.entity.Comment;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CommentDTOTest {

    @Test
    void from_authoredComment_buildsDTOWithAllFields() {
        UUID authorId = UUID.randomUUID();
        Comment c = new Comment();
        c.id = 1L;
        c.eventId = 42L;
        c.authorId = authorId;
        c.content = "Hello";
        c.parentCommentId = null;
        c.createdAt = LocalDateTime.now();
        CommentDTO dto = CommentDTO.from(c, "Alice", true);
        assertEquals(1L, dto.id());
        assertEquals("Alice", dto.authorDisplayName());
        assertTrue(dto.authorIsOrganizer());
    }

    @Test
    void from_replyComment_exposesParentId() {
        Comment c = new Comment();
        c.parentCommentId = 100L;
        c.id = 101L;
        c.createdAt = LocalDateTime.now();
        CommentDTO dto = CommentDTO.from(c, "Bob", false);
        assertEquals(100L, dto.parentCommentId());
    }

    @Test
    void recordEqualsAndHashCode() {
        // construct two identical DTOs via canonical ctor, assert .equals + .hashCode
    }
}
```

**Note** : pour les requêtes (`CreateXRequest`, `UpdateXRequest`, etc.), la coverage est principalement assurée par les tests de Resources (Vagues 4-7). Si le record n'a aucune logique métier (juste des champs + bean validation annotations), 1 test trivial suffit (instanciation + assertion sur les getters).

**Commande de validation** :
```bash
cd backend && ./mvnw -pl services/<svc>-service test -Dtest='*DTOTest,*RequestTest,*ResponseTest' -B
# Coverage agrégée par module ≥ 95 % L sur les classes DTO/Request/Response.
```

**Commit** : `test(backend): cover all DTO records across 5 services (Étape 3.3, COV-002)`

---

### Étape 4 — Engagement-service tests (Vague 4, 5 commits)

**Objectif Vague 4** : monter `engagement-service` de ~10,8 % L à ≥ 80 % L. Couvre 1 341 lignes de code Java main réparties entre `AttendanceService` (317 LOC), `CommentService` (314 LOC), 3 resources (`AttendanceResource` 77, `CommentResource` 73, `MyAttendancesResource` 70), 2 Internal resources, 2 entités, et le wiring REST clients.

#### Étape 4.1 — Port `EngagementDomainSentinelsTest` (7 sentinels)

**Objectif** : retirer les 7 `@Tag("legacy-port-s9")` du fichier `EngagementDomainSentinelsTest.java` et porter en runtime avec assertions réelles. Pivot Décision D.

**Fichier** — `backend/services/engagement-service/src/test/java/ch/unige/events/engagement/sentinels/EngagementDomainSentinelsTest.java` (extension du fichier existant — ajouter le code, retirer les `@Tag`).

Pour chaque sentinel, suit le pattern :

```java
// SCRUM-144 sentinel: post_eventDraftByNonCreator_returns404_antiOracle
@QuarkusTest
@Test
void post_eventDraftByNonCreator_returns404_antiOracle() {
    // Setup : un event DRAFT avec creator = userA (mocked via REST client)
    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();
    when(eventClient.getByIdWithCoOrgCheck(42L, userB))
        .thenReturn(new EventDTO(42L, "Draft", null, userA, /* status=DRAFT */ EventStatus.DRAFT, ...));

    // Act + Assert : userB tente de poster un commentaire → 404 anti-oracle
    given()
        .auth().oauth2(jwtFor(userB))
        .body(new CreateCommentRequest("Hello"))
        .when().post("/events/42/comments")
        .then()
        .statusCode(404)
        .body("error", is("not_found"));
}
```

**Pattern par sentinel** (les 7 cas, voir Annexe A pour la matrice complète) :

| Sentinel | Setup REST mocks | HTTP call | Expected response |
|---|---|---|---|
| `post_eventDraftByNonCreator_returns404_antiOracle` | Event DRAFT, creator=A, caller=B | POST /events/{id}/comments | 404 `{error:"not_found"}` |
| `post_eventBanned_returns404_antiOracle` | Event BANNED, caller authenticated | POST /events/{id}/comments | 404 `{error:"not_found"}` |
| `post_replyToReply_returns422_repliesTooDeep` | parentCommentId pointe sur un comment qui a déjà parentCommentId | POST /events/{id}/comments avec `{parentCommentId:42}` | 422 `{error:"replies_too_deep"}` |
| `post_parentInOtherEvent_returns422_parentNotInEvent` | parentCommentId existe mais sur un autre event | POST /events/{id}/comments | 422 `{error:"parent_not_in_event"}` |
| `post_unknownParent_returns404_parentNotFound` | parentCommentId pointe sur un id inexistant | POST /events/{id}/comments | 404 `{error:"not_found"}` |
| `delete_byPendingCoOrganizer_returns403` | caller co-org mais status PENDING (pas ACCEPTED) | DELETE /comments/{id} | 403 |
| `delete_unknownComment_returns404_commentNotFound` | comment id inexistant | DELETE /comments/{id} | 404 `{error:"not_found"}` |

**Imports nécessaires** : `@QuarkusTest`, `@InjectMock @RestClient EventServiceClient eventClient`, `@InjectMock @RestClient UserServiceClient userClient`, `RestAssured.given()`, `io.quarkus.test.security.TestSecurity` ou helper `jwtFor(UUID)`.

**Helper JWT** — créer `engagement-service/src/test/java/ch/unige/events/engagement/test/JwtTestHelper.java` :

```java
package ch.unige.events.engagement.test;

import io.smallrye.jwt.build.Jwt;
import java.util.Set;
import java.util.UUID;

public final class JwtTestHelper {
    private JwtTestHelper() {}

    public static String jwtFor(UUID userId) {
        return Jwt.claims()
            .issuer("https://test/")
            .subject("auth0|" + userId)
            .claim("uuid", userId.toString())
            .claim("email", "test@test")
            .groups(Set.of("user"))
            .sign();
    }

    public static String adminJwt() {
        return Jwt.claims()
            .issuer("https://test/")
            .subject("auth0|admin")
            .claim("uuid", UUID.randomUUID().toString())
            .groups(Set.of("admin"))
            .sign();
    }
}
```

**Profile test** — `engagement-service/src/test/resources/application.properties` doit déclarer :
```properties
mp.jwt.verify.publickey.location=publickey.pem
mp.jwt.verify.issuer=https://test/
smallrye.jwt.sign.key.location=privatekey.pem
quarkus.test.continuous-testing=disabled
%test.quarkus.datasource.db-kind=h2
%test.quarkus.datasource.jdbc.url=jdbc:h2:mem:test
%test.quarkus.hibernate-orm.database.generation=drop-and-create
```

(Si l'infrastructure JWT n'est pas en place, ajouter `quarkus-smallrye-jwt-build` à `<scope>test</scope>` dans le pom + générer les keypairs PEM via `openssl` à committer dans `src/test/resources/`.)

**Commande de validation** :
```bash
cd backend && ./mvnw -pl services/engagement-service test -Dtest=EngagementDomainSentinelsTest -B
# Tests run: 8, Failures: 0, Errors: 0, Skipped: 0
grep -c '@Tag("legacy-port-s9")' \
    services/engagement-service/src/test/java/ch/unige/events/engagement/sentinels/EngagementDomainSentinelsTest.java
# → 0 (tous les tags retirés)
```

**Commit** : `test(engagement): port 7 SCRUM-144 sentinels to runtime (Étape 4.1, TEST-001)`

---

#### Étape 4.2 — Tests `AttendanceService` (port + extension)

**Objectif** : couvrir `AttendanceService` (317 LOC) à ≥ 85 % L. Référence legacy : `git show 41074e9:backend/services/legacy-monolith/src/test/java/ch/unige/events/service/AttendanceServiceCoverageTest.java` (large fichier de tests Mockito).

**Fichier** — `backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/service/AttendanceServiceTest.java` :

**Squelette** (port + adaptation REST clients post-stub) :

```java
package ch.unige.events.engagement.attendance.service;

import ch.unige.events.engagement.attendance.dto.AttendanceDTOMapper;
import ch.unige.events.engagement.attendance.entity.Attendance;
import ch.unige.events.shared.client.EventServiceClient;
import ch.unige.events.shared.client.UserServiceClient;
import ch.unige.events.shared.domain.dto.AttendanceDTO;
import ch.unige.events.shared.domain.dto.EventDTO;
import ch.unige.events.shared.domain.dto.UserPublicResponse;
import ch.unige.events.shared.domain.enums.AttendanceStatus;
import ch.unige.events.shared.domain.enums.EventStatus;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class AttendanceServiceTest {

    @Inject AttendanceService service;
    @InjectMock @RestClient EventServiceClient eventClient;
    @InjectMock @RestClient UserServiceClient userClient;

    UUID userA = UUID.randomUUID();
    UUID userB = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        PanacheMock.mock(Attendance.class);
        when(userClient.getById(any())).thenReturn(
            new UserPublicResponse(userA, "alice", "Alice", null, null, false, null, 0, 0, null));
    }

    @Test
    void attend_publishedEvent_belowCapacity_returnsAttending() {
        // Mock event PUBLISHED, capacity 100, currentAttending 5
        when(eventClient.getById(42L)).thenReturn(eventPublished(42L, 100));
        Mockito.when(Attendance.count("eventId = ?1 and status = ?2", 42L, AttendanceStatus.ATTENDING))
            .thenReturn(5L);
        // ... mock persist via PanacheMock
        AttendanceDTO dto = service.attend("auth0|alice", 42L, AttendanceStatus.ATTENDING);
        assertEquals(AttendanceStatus.ATTENDING, dto.status());
    }

    @Test
    void attend_capacityFull_returnsWaitlisted() {
        when(eventClient.getById(42L)).thenReturn(eventPublished(42L, 100));
        Mockito.when(Attendance.count("eventId = ?1 and status = ?2", 42L, AttendanceStatus.ATTENDING))
            .thenReturn(100L);
        AttendanceDTO dto = service.attend("auth0|alice", 42L, AttendanceStatus.ATTENDING);
        assertEquals(AttendanceStatus.WAITLISTED, dto.status());
    }

    @Test
    void attend_unknownEvent_throws404() {
        when(eventClient.getById(99L)).thenReturn(null);
        assertThrows(NotFoundException.class,
            () -> service.attend("auth0|alice", 99L, AttendanceStatus.ATTENDING));
    }

    @Test
    void attend_draftEventByNonCreator_throws404_antiOracle() {
        EventDTO draft = eventDraft(42L, userB);  // creator=B
        when(eventClient.getById(42L)).thenReturn(draft);
        assertThrows(NotFoundException.class,
            () -> service.attend("auth0|alice", 42L, AttendanceStatus.ATTENDING));
    }

    @Test
    void attend_bannedEvent_throws404_antiOracle() {
        EventDTO banned = eventBanned(42L);
        when(eventClient.getById(42L)).thenReturn(banned);
        assertThrows(NotFoundException.class,
            () -> service.attend("auth0|alice", 42L, AttendanceStatus.ATTENDING));
    }

    @Test
    void attend_statusOtherThanAttending_throws400() {
        when(eventClient.getById(42L)).thenReturn(eventPublished(42L, 100));
        assertThrows(BadRequestException.class,
            () -> service.attend("auth0|alice", 42L, AttendanceStatus.WAITLISTED));
    }

    @Test
    void removeAttendance_promotesNextWaitlistedToAttending() {
        // verify the cascade: when an ATTENDING leaves, the oldest WAITLISTED is promoted
    }

    @Test
    void getByUser_filtersByStatus() {
        // verify GET /users/{id}/attendances?status=... behavior
    }

    private EventDTO eventPublished(long id, int capacity) {
        return new EventDTO(id, "Title", null, userA, /* ... */ EventStatus.PUBLISHED, /* ... */);
    }

    private EventDTO eventDraft(long id, UUID creatorId) {
        return new EventDTO(id, "Draft", null, creatorId, /* ... */ EventStatus.DRAFT, /* ... */);
    }

    private EventDTO eventBanned(long id) {
        return new EventDTO(id, "Banned", null, userA, /* ... */ EventStatus.BANNED, /* ... */);
    }
}
```

**Cas de test à couvrir** (référence : `AttendanceServiceCoverageTest` legacy, ~25 cas) :

1. `attend_publishedEvent_belowCapacity_returnsAttending`
2. `attend_publishedEvent_capacityFull_returnsWaitlisted`
3. `attend_publishedEvent_existingAttendance_returnsExistingDTO`
4. `attend_unknownEvent_throws404`
5. `attend_draftEventByNonCreator_throws404_antiOracle`
6. `attend_draftEventByCreator_succeeds`
7. `attend_bannedEvent_throws404_antiOracle`
8. `attend_cancelledEvent_throws422`
9. `attend_eventInPast_throws422`
10. `attend_statusBesidesAttending_throws400`
11. `attend_priceCentsGreaterThanZero_keepsRequestedStatus` (paid events)
12. `removeAttendance_attending_promotesNextWaitlisted`
13. `removeAttendance_waitlisted_doesNotPromote`
14. `removeAttendance_unknownAttendance_throws404`
15. `removeAttendance_otherUserAttendance_throws403`
16. `getByUser_status_returnsFilteredList`
17. `getByUser_unknownUser_returnsEmptyList`
18. `getByUser_emptyStatus_returnsAll`
19. `getMyParticipationEvents_aggregatesByEventId`
20. `getCounts_groupsByStatus_handlesNullStatus`
21. `getAttendees_publishedEvent_returnsList`
22. `getAttendees_draftByNonCreator_throws404`
23. `cascade_eventBannedConsumer_marksAllAttendancesCancelled` (Kafka consumer side)
24. `idempotence_attendTwice_returnsSameAttendance`
25. `Kafka publisher emits comments.created on success` (verify producer wiring si applicable)

**Commande de validation** :
```bash
cd backend && ./mvnw -pl services/engagement-service test -Dtest=AttendanceServiceTest -B
awk -F, 'NR>1 && $3=="AttendanceService" {printf "L:%5.1f%% (%d/%d)  B:%5.1f%% (%d/%d)\n", $9*100/($8+$9), $9, $8+$9, $7*100/($6+$7), $7, $6+$7}' \
    services/engagement-service/target/jacoco-report/jacoco.csv
# Attendu : L ≥ 85 %, B ≥ 75 %
```

**Commit** : `test(engagement): port AttendanceServiceCoverageTest from legacy + adapt REST clients (Étape 4.2, COV-001)`

---

#### Étape 4.3 — Tests `CommentService` (port + cascade)

**Objectif** : couvrir `CommentService` (314 LOC) à ≥ 85 % L. Référence legacy : `git show 41074e9:.../service/CommentServiceCoverageTest.java`.

**Fichier** — `backend/services/engagement-service/src/test/java/ch/unige/events/engagement/comment/service/CommentServiceTest.java`.

**Cas de test** (~20 cas, similaire à AttendanceServiceTest) :

1. `post_validComment_returns201`
2. `post_replyToTopLevel_persistsParentCommentId`
3. `post_replyToReply_throws422_repliesTooDeep`
4. `post_parentInOtherEvent_throws422_parentNotInEvent`
5. `post_unknownParent_throws404`
6. `post_eventDraftByNonCreator_throws404_antiOracle`
7. `post_eventBanned_throws404_antiOracle`
8. `post_unknownEvent_throws404`
9. `post_cascadeOrganizer_authorIsOrganizer_isTrue` (cascade SCRUM-136)
10. `post_cascadeOrganizer_callerNotOrganizer_isFalse`
11. `delete_ownComment_succeeds`
12. `delete_otherUserCommentByCreator_succeeds`
13. `delete_otherUserCommentByCoOrganizerAccepted_succeeds`
14. `delete_byPendingCoOrganizer_throws403`
15. `delete_byNonOrganizerNonOwner_throws403`
16. `delete_unknownComment_throws404`
17. `getByEvent_publishedEvent_returnsHierarchy`
18. `getByEvent_draftByNonCreator_throws404_antiOracle`
19. `getByEvent_paginatesByDate`
20. `kafkaPublisher_commentCreated_emitsEvent` (verify CommentCreatedPublisher wiring)

**Mocks nécessaires** :
- `@InjectMock @RestClient EventServiceClient` — pour `getByIdWithCoOrgCheck`, `getOrganizerUuids`
- `@InjectMock @RestClient UserServiceClient` — pour `getById` (author enrichment)
- `@InjectMock CommentCreatedPublisher` — pour vérifier que les events Kafka sont emis
- `PanacheMock.mock(Comment.class)` — pour `find`, `count`, `persist`, `delete`

**Commande de validation** :
```bash
cd backend && ./mvnw -pl services/engagement-service test -Dtest=CommentServiceTest -B
# Tests run: 20, Failures: 0
```

**Commit** : `test(engagement): port CommentServiceCoverageTest + cascade SCRUM-136 (Étape 4.3, COV-001)`

---

#### Étape 4.4 — Tests `AttendanceResource` + `MyAttendancesResource` + `CommentResource` + `CommentDirectResource`

**Objectif** : couvrir les 4 resources publiques de engagement-service à ≥ 80 % L via `@QuarkusTest` + `RestAssured`. Référence legacy : `AttendanceResourceTest`, `CommentResourceTest`, `CommentDirectResourceTest`.

**Fichiers** :
- `engagement-service/src/test/java/ch/unige/events/engagement/attendance/resource/AttendanceResourceTest.java`
- `engagement-service/src/test/java/ch/unige/events/engagement/attendance/resource/MyAttendancesResourceTest.java`
- `engagement-service/src/test/java/ch/unige/events/engagement/comment/resource/CommentResourceTest.java`
- `engagement-service/src/test/java/ch/unige/events/engagement/comment/resource/CommentDirectResourceTest.java`

**Pattern** :

```java
@QuarkusTest
class AttendanceResourceTest {

    @InjectMock AttendanceService service;

    @Test
    void post_validAttend_returns201() {
        UUID userA = UUID.randomUUID();
        when(service.attend(any(), eq(42L), eq(AttendanceStatus.ATTENDING)))
            .thenReturn(new AttendanceDTO(1L, 42L, userA, "Alice", AttendanceStatus.ATTENDING, /* ... */));

        given()
            .auth().oauth2(jwtFor(userA))
            .body(new AttendanceRequest(AttendanceStatus.ATTENDING))
            .contentType(ContentType.JSON)
            .when().post("/events/42/attend")
            .then()
            .statusCode(201)
            .body("status", is("ATTENDING"));
    }

    @Test
    void delete_existingAttend_returns204() { /* ... */ }

    @Test
    void post_anonymous_returns401() { /* sans auth */ }

    @Test
    void getAttendees_publishedEvent_returns200WithList() { /* ... */ }

    @Test
    void getAttendees_draftByNonCreator_returns404_antiOracle() { /* ... */ }
}
```

**Cas de test** (estimé 5-8 par resource = ~20-30 tests total).

**Commit** : `test(engagement): cover AttendanceResource + MyAttendancesResource + CommentResource + CommentDirectResource (Étape 4.4)`

---

#### Étape 4.5 — Validation finale Vague 4

```bash
cd backend && ./mvnw -pl services/engagement-service test -B
# Tests run: ~80-100, Failures: 0
awk -F, 'NR>1 {lm+=$8; lc+=$9} END {printf "engagement-service L:%5.1f%% (%d/%d)\n", lc*100/(lm+lc), lc, lm+lc}' \
    services/engagement-service/target/jacoco-report/jacoco.csv
# Attendu : L ≥ 80 %
```

Pas de commit (validation seulement). Push après les 4 sous-étapes vertes :
```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
# Verifier que sonar-aggregate reste vert et que coverage on new code monte (~30-50 %)
```

---

### Étape 5 — User-service tests (Vague 5, 5 commits)

**Objectif Vague 5** : monter `user-service` de ~4,5 % L à ≥ 80 % L. Couvre 1 460 lignes Java main : `UserService` (213), `FollowService` (186), `UserResource` (154), `FollowResource` (142), `CalendarService` (98), `IcsBuilder` (90), entités, DTOs.

#### Étape 5.1 — Port `UserDomainSentinelsTest` (6 sentinels)

**Fichier** — `backend/services/user-service/src/test/java/ch/unige/events/user/sentinels/UserDomainSentinelsTest.java` (extension du fichier existant — retirer les 6 `@Tag`).

**Pattern par sentinel** :

| Sentinel | Setup | Action | Expected |
|---|---|---|---|
| `findAcceptedFollowedIds_returnsOnlyAcceptedUuids` | Persist 3 follows : 2 ACCEPTED, 1 PENDING for userA | call `Follow.findAcceptedFollowedIds(userA)` | List of 2 UUIDs (PENDING excluded) |
| `rejectRequest_followerCanReFollowAfterReject` | userA rejects userB's pending request | userB sends new follow request | 201 + new PENDING row created |
| `follow_selfFollow_throwsUnprocessable` | caller=userA, target=userA | POST /users/{userA}/follow | 422 `{error:"self_follow"}` |
| `getFollowers_privateProfileNonOwner_returns404_antiOracle` | userA private, caller=userB (non-admin) | GET /users/{userA}/followers | 404 (anti-oracle ISSUE-93) |
| `getPublicProfile_self_followStatusIsNull` | caller=userA, target=userA | GET /users/{userA} | 200 + `followStatus: null` |
| `getPublicProfile_authNonOwnerWithPending_followStatusIsPending` | userB has PENDING follow request to userA | userB calls GET /users/{userA} | 200 + `followStatus: "PENDING"` |

**Test exemple** — `getFollowers_privateProfileNonOwner_returns404_antiOracle` :

```java
@QuarkusTest
@TestTransaction
@Test
void getFollowers_privateProfileNonOwner_returns404_antiOracle() {
    UUID userA = UUID.randomUUID();
    User user = new User();
    user.id = userA;
    user.username = "alice";
    user.privateProfile = true;
    user.persist();

    given()
        .auth().oauth2(jwtFor(UUID.randomUUID()))   // non-self, non-admin
        .when().get("/users/" + userA + "/followers")
        .then()
        .statusCode(404)
        .body("error", is("not_found"));
}
```

**Commande de validation** :
```bash
cd backend && ./mvnw -pl services/user-service test -Dtest=UserDomainSentinelsTest -B
grep -c '@Tag("legacy-port-s9")' services/user-service/src/test/java/ch/unige/events/user/sentinels/UserDomainSentinelsTest.java
# → 0
```

**Commit** : `test(user): port 6 SCRUM-138 sentinels to runtime (Étape 5.1, TEST-001)`

---

#### Étape 5.2 — Tests `UserService` (port + admin bypass + getPublicProfile cascade)

**Fichier** — `user-service/src/test/java/ch/unige/events/user/service/UserServiceTest.java`. Référence legacy : `UserServiceCoverageTest` (commit 41074e9, ~1054 LOC).

**Cas de test** (~25 cas) :

1. `getPublicProfile_self_returnsFullProfile_followStatusNull`
2. `getPublicProfile_authNonOwnerPublicProfile_returnsPublicView`
3. `getPublicProfile_authNonOwnerPrivateProfile_throws404_antiOracle`
4. `getPublicProfile_anonymousPublicProfile_returnsPublicView`
5. `getPublicProfile_anonymousPrivateProfile_throws404_antiOracle`
6. `getPublicProfile_adminBypass_returnsFullProfileEvenPrivate` (REST-003)
7. `getPublicProfile_followStatusFromCallerToTarget` (PENDING / ACCEPTED / REJECTED)
8. `getMe_authenticated_returnsSelfProfile`
9. `getMe_anonymous_throws401`
10. `update_validProfile_succeeds`
11. `update_invalidUsername_throws400`
12. `update_emailUnchanged_keeps`
13. `uploadImage_validJpeg_persistsKey`
14. `uploadImage_invalidFormat_throws400`
15. `uploadBanner_validPng_succeeds`
16. `getPublicCount_publicUsers_returnsCount`
17. `findByIds_partialList_returnsPresentOnly`
18. `getById_unknownUser_throwsNotFound`
19. `getById_existingUser_returnsResponse`
20. `kafkaProducer_userUpdated_emitsEvent` (vérifier wiring)
21. `delete_self_succeeds`
22. `delete_other_throws403`
23. `cascade_deleteSelf_cascadesToFollows` (verify Follow entries deleted)
24. `searchByUsername_partialMatch_returnsList`
25. `concurrency_simultaneousUpdate_lastWriteWins` (port `UserServiceMockConcurrencyTest`)

**Mocks** :
- `PanacheMock.mock(User.class)` + `PanacheMock.mock(Follow.class)`
- `@InjectMock S3Service` (si upload image)
- `@TestSecurity(user="alice", roles={"user"})` ou JWT helper

**Commande de validation** :
```bash
cd backend && ./mvnw -pl services/user-service test -Dtest=UserServiceTest -B
awk -F, 'NR>1 && $3=="UserService" {printf "L:%5.1f%%\n", $9*100/($8+$9)}' \
    services/user-service/target/jacoco-report/jacoco.csv
# Attendu : L ≥ 85 %
```

**Commit** : `test(user): port UserServiceCoverageTest + admin bypass + privacy (Étape 5.2, COV-001 + REST-003)`

---

#### Étape 5.3 — Tests `FollowService` (port complet)

**Fichier** — `user-service/src/test/java/ch/unige/events/user/follow/service/FollowServiceTest.java`. Référence legacy : `FollowServiceCoverageTest`.

**Cas de test** (~15 cas) :

1. `follow_publicTarget_returnsAccepted`
2. `follow_privateTarget_returnsPending`
3. `follow_selfFollow_throwsUnprocessable`
4. `follow_alreadyFollowing_returnsExisting`
5. `follow_unknownTarget_throws404`
6. `follow_blockedTarget_throws403` (si applicable)
7. `acceptRequest_validPending_changesToAccepted`
8. `acceptRequest_unknownRequest_throws404`
9. `acceptRequest_byNonTarget_throws403`
10. `rejectRequest_validPending_marksRejected`
11. `unfollow_existingAccepted_removes`
12. `unfollow_unknownFollow_throws404`
13. `getFollowers_publicTarget_returnsList`
14. `getFollowers_privateTargetNonOwner_throws404_antiOracle`
15. `kafkaProducer_followLifecycle_emitsEvent`

**Commit** : `test(user): port FollowServiceCoverageTest (Étape 5.3, COV-001)`

---

#### Étape 5.4 — Tests `CalendarService` + `IcsBuilder` + `UserResource` + `FollowResource`

**Fichiers** :
- `user-service/src/test/java/ch/unige/events/user/calendar/service/CalendarServiceTest.java` (port `CalendarServiceCoverageTest`)
- `user-service/src/test/java/ch/unige/events/user/calendar/util/IcsBuilderTest.java` (port `IcsBuilderTest` legacy)
- `user-service/src/test/java/ch/unige/events/user/resource/UserResourceTest.java` (port `UserResourceTest` legacy)
- `user-service/src/test/java/ch/unige/events/user/follow/resource/FollowResourceTest.java` (port `FollowResourceTest` legacy)
- `user-service/src/test/java/ch/unige/events/user/follow/resource/FollowRequestResourceTest.java` (port partiel — endpoints `/follow-requests/*`)

**Cas de test agrégés** (~30 cas).

**Important `CalendarService`** : la calendar feed appelle `engagementClient.getUserAttendances(...)` + `eventClient.findByIds(...)` post-Étape 3.3 spec ultime. Mocker les 2 REST clients.

```java
@QuarkusTest
class CalendarServiceTest {

    @Inject CalendarService service;
    @InjectMock @RestClient EngagementServiceClient engagementClient;
    @InjectMock @RestClient EventServiceClient eventClient;

    @Test
    void generateIcs_userWithAttendingEvents_buildsValidIcs() {
        UUID userA = UUID.randomUUID();
        when(engagementClient.getUserAttendances(userA, "ATTENDING")).thenReturn(List.of(
            new AttendanceDTO(1L, 42L, userA, "Alice", AttendanceStatus.ATTENDING, /*...*/)
        ));
        when(eventClient.findByIds(List.of(42L), "PUBLISHED")).thenReturn(List.of(
            eventPublished(42L)
        ));
        String ics = service.generateIcs("alice-token");
        assertTrue(ics.startsWith("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("UID:event-42"));
        assertTrue(ics.endsWith("END:VCALENDAR\n"));
    }

    @Test
    void generateIcs_unknownToken_throws404() { /* ... */ }

    @Test
    void generateIcs_userWithNoAttendances_returnsEmptyCalendar() { /* ... */ }

    @Test
    void generateIcs_engagementClientFailure_returnsEmptyAfterFallback() {
        when(engagementClient.getUserAttendances(any(), any())).thenReturn(List.of());
        // verify ICS generated with no events but valid envelope
    }
}
```

**Commit** : `test(user): cover CalendarService + IcsBuilder + UserResource + FollowResource (Étape 5.4, COV-001)`

---

#### Étape 5.5 — Validation finale Vague 5

```bash
cd backend && ./mvnw -pl services/user-service test -B
awk -F, 'NR>1 {lm+=$8; lc+=$9} END {printf "user-service L:%5.1f%% (%d/%d)\n", lc*100/(lm+lc), lc, lm+lc}' \
    services/user-service/target/jacoco-report/jacoco.csv
# Attendu : L ≥ 80 %

git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
```

---

### Étape 6 — Event-service tests (Vague 6, 7 commits — le plus gros)

**Objectif Vague 6** : monter `event-service` de ~5,4 % L à ≥ 80 % L. Couvre **3 546 lignes Java main** réparties entre `EventService` (656), `EventCoOrganizerService` (265), `EventResource` (248), `FeaturedService` (153), `EventSearchService` (113), `FavoriteService` (104), 4 EventDTO locaux, entités, etc. C'est le gros du travail (~50 tests à ajouter).

#### Étape 6.1 — Port `EventDomainSentinelsTest` (17 sentinels)

**Fichier** — `backend/services/event-service/src/test/java/ch/unige/events/event/sentinels/EventDomainSentinelsTest.java` (extension du fichier — retirer 17 `@Tag`).

Les 17 sentinels SCRUM-147 portent principalement sur le module `RecurrenceGenerator` + le système de recurrence dans `EventService` + les anti-oracles ISSUE-92 sur `getOccurrences`. Voir Annexe A pour la matrice complète.

**Pattern** (un sentinel typique sur recurrence) :

```java
@QuarkusTest
@TestTransaction
@Test
void createRecurring_weekly4Occurrences_persists1ParentAnd3Children() {
    UUID creatorId = UUID.randomUUID();
    CreateEventRequest req = new CreateEventRequest(
        "Weekly", "Desc", LocalDateTime.of(2026, 6, 1, 10, 0),
        LocalDateTime.of(2026, 6, 1, 12, 0),
        Faculty.SCIENCES, EventCategory.CONFERENCE,
        Set.of("ai"), "Geneva", "Rue X", 100, 0,
        new RecurrenceRequest(RecurrenceFrequency.WEEKLY, 4, null));

    given()
        .auth().oauth2(jwtFor(creatorId))
        .body(req).contentType(ContentType.JSON)
        .when().post("/events")
        .then()
        .statusCode(201);

    long parentCount = Event.count("recurrenceRule is not null and parentEventId is null");
    long childCount = Event.count("parentEventId is not null");
    assertEquals(1L, parentCount);
    assertEquals(3L, childCount);  // 4 occurrences total = 1 parent + 3 children
}
```

**Pattern anti-oracle** :

```java
@QuarkusTest
@TestTransaction
@Test
void getOccurrences_draftByNonCreator_returns404_antiOracle() {
    UUID creatorId = UUID.randomUUID();
    UUID otherUser = UUID.randomUUID();
    Event parent = persistDraftEvent(creatorId, "FREQ=WEEKLY;COUNT=4");

    given()
        .auth().oauth2(jwtFor(otherUser))
        .when().get("/events/" + parent.id + "/occurrences")
        .then()
        .statusCode(404)
        .body("error", is("not_found"));
}

@QuarkusTest
@TestTransaction
@Test
void getOccurrences_draftByAnonymous_returns404_antiOracle() {
    Event parent = persistDraftEvent(UUID.randomUUID(), "FREQ=WEEKLY;COUNT=4");
    given()
        .when().get("/events/" + parent.id + "/occurrences")
        .then()
        .statusCode(404);
}
```

**Cas de test** (les 17 sentinels — voir Annexe A pour le mapping complet).

**Commit** : `test(event): port 17 SCRUM-147 sentinels to runtime (Étape 6.1, TEST-001)`

---

#### Étape 6.2 — Tests `EventService` (port + extension)

**Fichier** — `event-service/src/test/java/ch/unige/events/event/service/EventServiceTest.java`. Référence legacy : `EventServiceCoverageTest`.

**Cas de test** (~30 cas) :

1. `getById_publishedAnonymous_returnsDTO`
2. `getById_draftByCreator_returnsDTO`
3. `getById_draftByNonCreatorOrAnonymous_throws404_antiOracle`
4. `getById_bannedAnonymous_throws404_antiOracle`
5. `getById_bannedAdmin_returnsDTO_adminBypass`
6. `getById_unknownId_throws404`
7. `getByIdWithCoOrgCheck_validSelf_returnsDTOWithCoOrgFlag`
8. `getByIdWithCoOrgCheck_anonymous_returnsDTOWithoutCoOrgFlag` (Décision C anti-oracle)
9. `getByIdWithCoOrgCheck_authenticatedDifferentUser_returnsDTOWithoutCoOrgFlag`
10. `getOrganizerUuids_existingEvent_returnsCreatorPlusAcceptedCoOrgs`
11. `getOrganizerUuids_unknownEvent_returnsEmptyList`
12. `findByIds_subset_returnsPresent`
13. `findByIds_filterByStatus_excludesOthers`
14. `create_validRequest_persistsEvent`
15. `create_invalidDates_throws400`
16. `create_recurringWithoutBoundary_throws400`
17. `update_byCreator_succeeds`
18. `update_byAcceptedCoOrganizer_succeeds`
19. `update_byPendingCoOrganizer_throws403`
20. `update_byNonOwner_throws403`
21. `delete_byCreator_succeeds_andCascadesAttendances`
22. `delete_byNonCreator_throws403`
23. `publish_draftToPublished_succeeds_emitsKafka`
24. `cancel_publishedToCancelled_succeeds_emitsKafka`
25. `ban_byAdmin_consumesKafkaEvent_setsStatusBanned`
26. `ban_idempotent_secondCallNoop`
27. `expireJob_publishedPastEndDate_marksExpired_emitsKafka`
28. `getOccurrences_parentRecurring_returnsChildren`
29. `getOccurrences_standaloneEvent_returns200EmptyList`
30. `kafkaPublisher_eventLifecycle_emitsCorrectType`

**Mocks** :
- `@InjectMock @RestClient UserServiceClient` (creator enrichment)
- `@InjectMock @RestClient EngagementServiceClient` (attendance summary bulk)
- `PanacheMock.mock(Event.class)` + `PanacheMock.mock(EventCoOrganizer.class)`
- `@InjectMock EventLifecyclePublisher` (vérifier emit)

**Commit** : `test(event): port EventServiceCoverageTest + cascade SCRUM-136 + admin bypass (Étape 6.2, COV-001)`

---

#### Étape 6.3 — Tests `EventCoOrganizerService`

**Fichier** — `event-service/src/test/java/ch/unige/events/event/coorganizer/service/EventCoOrganizerServiceTest.java`. Référence legacy : `EventCoOrganizerServiceCoverageTest`.

**Cas de test** (~12 cas) :

1. `invite_byCreator_persistsPending_emitsKafka`
2. `invite_byNonCreator_throws403`
3. `invite_alreadyAccepted_throwsConflict`
4. `invite_selfInvite_throws422`
5. `invite_unknownTarget_throws404`
6. `accept_byInvitedUser_changesToAccepted_emitsKafka`
7. `accept_byOtherUser_throws403`
8. `accept_alreadyAccepted_idempotent`
9. `reject_byInvitedUser_changesToRejected`
10. `delete_byCreator_removes`
11. `getMyInvitations_returnsPendingOnly`
12. `kafkaProducer_coOrganizerLifecycle_emitsTyped`

**Commit** : `test(event): port EventCoOrganizerServiceCoverageTest (Étape 6.3, COV-001)`

---

#### Étape 6.4 — Tests `FeaturedService` + `EventSearchService` + `FavoriteService` + `MyEventsService` + `EventStatsService` + `EventViewService` + `ShareService`

**Objectif** : couvrir les 7 services « périphériques » de event-service. Granularité : 1 fichier de test par service.

**Fichiers** (effort moyen S par service, ~5-8 cas chacun) :
- `event-service/src/test/java/.../FeaturedServiceTest.java` (port `FeaturedServiceCoverageTest`)
- `event-service/src/test/java/.../EventSearchServiceTest.java` (port `EventSearchServiceCoverageTest`)
- `event-service/src/test/java/.../FavoriteServiceTest.java` (port `FavoriteServiceCoverageTest`)
- `event-service/src/test/java/.../MyEventsServiceTest.java` (port `MyEventsServiceCoverageTest` — n'existe pas en legacy donc tests fresh)
- `event-service/src/test/java/.../EventStatsServiceTest.java` (port `EventStatsServiceCoverageTest`)
- `event-service/src/test/java/.../EventViewServiceTest.java` (port `EventViewServiceCoverageTest`)
- `event-service/src/test/java/.../ShareServiceTest.java` (port `ShareServiceMock`)

Total : ~40-50 tests.

**Note importante `FeaturedService` / `MyEventsService` / `EventStatsService`** : ces services utilisent **bulk lookup** d'attendance counts via `EngagementServiceClient.getAttendanceSummariesBulk(...)` (Décision I spec ultime). Mocker ce REST client est essentiel.

**Commit** (peut être groupé en 2-3 commits selon taille) :
- `test(event): cover FeaturedService + EventSearchService + FavoriteService (Étape 6.4a)`
- `test(event): cover MyEventsService + EventStatsService + EventViewService + ShareService (Étape 6.4b)`

---

#### Étape 6.5 — Tests `EventResource` + `EventCoOrganizerResource` + `FavoriteResource` + `EventSearchResource` + `EventStatsResource` + `EventViewResource` + `ShareResource` + `RedirectResource` + `MyEventsResource` + `MyCoOrganizerInvitationsResource` + `UserFavoritesResource` + `AdminEventResource`

**Objectif** : couvrir les ~12 resources HTTP du service via `@QuarkusTest` + `RestAssured`. Référence legacy : `EventResourceTest`, `EventCoOrganizerResourceTest`, `FavoriteResourceTest`, `EventSearchResourceTest`, `EventStatsResourceTest`, `EventViewResourceTest`, `AdminEventResourceTest`.

**Granularité** : 1 fichier test par resource, ~5-8 cas chacun = ~50-80 tests total.

**Cas typiques par resource** (happy path + auth + anti-oracle + edge) :
1. `GET /events` — happy path public
2. `GET /events?ids=1,2,3` — bulk lookup
3. `GET /events/{id}` — get by id (couvert dans Étape 6.1 sentinels en partie)
4. `POST /events` — create (auth required)
5. `PATCH /events/{id}` — update (creator/co-org permissions)
6. `DELETE /events/{id}` — delete cascade
7. `POST /events/{id}/publish` — lifecycle transition
8. `POST /events/{id}/cancel` — lifecycle transition
9. `POST /events/{id}/image` — multipart upload
10. `GET /events/{id}/occurrences` — recurrence (couvert sentinels)

**Commit** (peut être groupé) :
- `test(event): cover EventResource + AdminEventResource + RedirectResource (Étape 6.5a)`
- `test(event): cover EventCoOrganizerResource + MyCoOrganizerInvitationsResource (Étape 6.5b)`
- `test(event): cover FavoriteResource + UserFavoritesResource + MyEventsResource (Étape 6.5c)`
- `test(event): cover EventSearchResource + EventStatsResource + EventViewResource + ShareResource (Étape 6.5d)`

---

#### Étape 6.6 — Tests scheduler + Kafka consumer

**Fichiers** :
- `event-service/src/test/java/.../scheduler/EventExpirationJobTest.java` (port `EventExpirationJobTest` legacy)
- `event-service/src/test/java/.../scheduler/EventExpirationServiceTest.java` (port `EventExpirationServiceTest` legacy)
- `event-service/src/test/java/.../kafka/EventBannedConsumerTest.java` (NEW : test idempotent ban application via Kafka, Décision H)

**EventBannedConsumerTest exemple** :

```java
@QuarkusTest
class EventBannedConsumerTest {

    @Inject EventBannedConsumer consumer;

    @Test
    @TestTransaction
    void onEventBanned_publishedEvent_marksBanned_idempotent() {
        Event e = persistEvent(EventStatus.PUBLISHED);
        consumer.onEventBanned(new EventBannedEvent(e.id, "moderator-uuid", "Spam"));
        e = Event.findById(e.id);
        assertEquals(EventStatus.BANNED, e.status);
        // Re-apply : idempotence
        consumer.onEventBanned(new EventBannedEvent(e.id, "moderator-uuid", "Spam"));
        e = Event.findById(e.id);
        assertEquals(EventStatus.BANNED, e.status);
    }

    @Test
    @TestTransaction
    void onEventBanned_unknownEvent_logsAndIgnores() {
        consumer.onEventBanned(new EventBannedEvent(99999L, "m", "r"));
        // no exception, logged at warn level
    }
}
```

**Commit** : `test(event): cover EventExpirationJob + EventExpirationService + EventBannedConsumer (Étape 6.6)`

---

#### Étape 6.7 — Validation finale Vague 6

```bash
cd backend && ./mvnw -pl services/event-service test -B
awk -F, 'NR>1 {lm+=$8; lc+=$9} END {printf "event-service L:%5.1f%% (%d/%d)\n", lc*100/(lm+lc), lc, lm+lc}' \
    services/event-service/target/jacoco-report/jacoco.csv
# Attendu : L ≥ 80 %

git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
# coverage on new code monte vers 60-75 %
```

---

### Étape 7 — Moderation-service tests (Vague 7, 3 commits)

**Objectif Vague 7** : monter `moderation-service` de ~17,2 % L à ≥ 80 % L. Couvre 666 lignes Java main.

#### Étape 7.1 — Tests `ReportService` (port + Décision H Kafka producer-only)

**Fichier** — `moderation-service/src/test/java/ch/unige/events/report/service/ReportServiceTest.java`. Référence legacy : `ReportServiceCoverageTest`.

**Cas de test** (~15 cas) :

1. `report_validEventBycitizen_persistsPending`
2. `report_unknownEvent_throws404`
3. `report_ownEvent_throws422` (cascade self-check via EventServiceClient.getByIdWithCoOrgCheck)
4. `report_alreadyReportedByCaller_returnsExisting_idempotent`
5. `report_eventBanned_throws422` (déjà banni)
6. `list_pending_byAdmin_returnsAll`
7. `list_pending_byNonAdmin_throws403`
8. `handle_validReport_byAdmin_emitsKafkaEventBanned` (**Décision H** — pas de mutation directe `event.status`, uniquement Kafka producer)
9. `handle_unknownReport_throws404`
10. `handle_alreadyHandledReport_throws409`
11. `cleanupJob_olderThan90Days_deletesResolved` (port `ModerationCleanupServiceTest`)
12. `cleanupJob_unresolvedReports_keeps`
13. `getReporterEnrichment_callsUserClient` (verify REST client wiring)
14. `kafkaProducer_eventBanned_emitsTypedEvent`
15. `report_concurrent_idempotent` (deux reports parallèles → un seul row)

**Mocks** :
- `@InjectMock @RestClient EventServiceClient` (anti-oracle + cascade)
- `@InjectMock @RestClient UserServiceClient` (reporter + reviewedBy enrichment)
- `@InjectMock EventBannedPublisher` (vérifier emit Décision H)
- `PanacheMock.mock(Report.class)`

**Important Décision H** : tester explicitement que `handle()` **NE MUTE PAS** `event.status` directement (plus de `EventStub.event.status = BANNED`). À la place, vérifier que `EventBannedPublisher.publish(...)` est appelé. Le consumer `EventBannedConsumer` côté event-service applique l'état (couvert Étape 6.6).

**Commit** : `test(moderation): cover ReportService + Kafka-only ban flow (Étape 7.1, COV-001 + Décision H)`

---

#### Étape 7.2 — Tests `ReportResource` + `AdminReportResource` + `ModerationCleanupService`

**Fichiers** :
- `moderation-service/src/test/java/.../resource/ReportResourceTest.java` (port `ReportResourceTest` legacy)
- `moderation-service/src/test/java/.../resource/AdminReportResourceTest.java` (port `AdminReportResourceTest` legacy)
- `moderation-service/src/test/java/.../service/ModerationCleanupServiceTest.java` (port `ModerationCleanupServiceTest` + `ModerationCleanupCoverageTest`)

**Cas de test** (~15-20 tests).

**Commit** : `test(moderation): cover ReportResource + AdminReportResource + ModerationCleanupService (Étape 7.2)`

---

#### Étape 7.3 — Validation finale Vague 7

```bash
cd backend && ./mvnw -pl services/moderation-service test -B
awk -F, 'NR>1 {lm+=$8; lc+=$9} END {printf "moderation-service L:%5.1f%% (%d/%d)\n", lc*100/(lm+lc), lc, lm+lc}' \
    services/moderation-service/target/jacoco-report/jacoco.csv
# Attendu : L ≥ 80 %

git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
```

---

### Étape 8 — Validation finale + retouches Sonar (Vague 8, 1-3 commits)

#### Étape 8.1 — Mesure agrégée locale

**Objectif** : avant de pousser à CI, mesurer la coverage agrégée localement et valider qu'elle dépasse 80 % L sur le code Java main des 5 services + 10 shared libs.

**Script bash** — `backend/scripts/aggregate-coverage.sh` (NEW) :

```bash
#!/usr/bin/env bash
# Étape 22 — agrégation coverage jacoco multi-module pour Sonar Option B.
# Lance après `cd backend && ./mvnw verify -DskipITs`.
#
# Output :
#   - Tableau per-module L %/B %
#   - Total agrégé L %/B %
#   - Liste des classes < 80 % L (debug)
set -euo pipefail

cd "$(dirname "$0")/.."

declare -A modules
total_lm=0
total_lc=0
total_bm=0
total_bc=0

echo "Module                              L%      L missed/total    B%      B missed/total"
echo "---------------------------------- ------- ----------------- ------- -----------------"

for csv in services/*/target/jacoco-report/jacoco.csv contract-tests/target/jacoco-report/jacoco.csv e2e/target/jacoco-report/jacoco.csv; do
    [ -f "$csv" ] || continue
    module=$(echo "$csv" | sed -E 's|^(services/)?([^/]+)/target.*|\2|')
    awk -F, -v mod="$module" 'NR>1 {lm+=$8; lc+=$9; bm+=$6; bc+=$7}
        END { lt=lm+lc; bt=bm+bc;
              if(lt>0) printf "%-34s %6.1f%% %d/%d %20s %6.1f%% %d/%d\n",
                              mod, lc*100/lt, lm, lt, "", (bt>0?bc*100/bt:0), bm, bt
              else     printf "%-34s %6s        ---/--- %25s %6s        ---/---\n",
                              mod, "n/a", "", "n/a"
        }' "$csv"
    awk -F, -v lm0="$total_lm" -v lc0="$total_lc" -v bm0="$total_bm" -v bc0="$total_bc" \
        'NR>1 {lm+=$8; lc+=$9; bm+=$6; bc+=$7}
         END {printf "%d %d %d %d", lm0+lm, lc0+lc, bm0+bm, bc0+bc}' "$csv" > /tmp/aggr_$$
    read total_lm total_lc total_bm total_bc < /tmp/aggr_$$
done
rm -f /tmp/aggr_$$

echo "---------------------------------- ------- ----------------- ------- -----------------"
total_lt=$((total_lm + total_lc))
total_bt=$((total_bm + total_bc))
total_lp=$(awk -v c=$total_lc -v t=$total_lt 'BEGIN{if(t>0)printf "%.1f",c*100/t; else print "0.0"}')
total_bp=$(awk -v c=$total_bc -v t=$total_bt 'BEGIN{if(t>0)printf "%.1f",c*100/t; else print "0.0"}')
printf "%-34s %6s%%  %d/%d %20s %6s%%  %d/%d\n" \
    "TOTAL" "$total_lp" "$total_lm" "$total_lt" "" "$total_bp" "$total_bm" "$total_bt"

# Exit code : 0 si L ≥ 80 % et B ≥ 70 %
if awk -v lp="$total_lp" -v bp="$total_bp" 'BEGIN{exit !(lp>=80 && bp>=70)}'; then
    echo
    echo "✅ PASS — coverage globale L $total_lp % ≥ 80 % et B $total_bp % ≥ 70 %"
    exit 0
else
    echo
    echo "❌ FAIL — coverage globale L $total_lp % < 80 % ou B $total_bp % < 70 %"
    echo
    echo "Classes < 80 % L (top 30) :"
    for csv in services/*/target/jacoco-report/jacoco.csv; do
        [ -f "$csv" ] || continue
        awk -F, 'NR>1 && $8+$9>0 {p=$9*100/($8+$9); if(p<80) printf "  %-32s %6.1f%% L (in %s)\n", $3, p, FILENAME}' "$csv"
    done | sort -k2 -n | head -30
    exit 1
fi
```

**Commit** : `chore(backend): add aggregate-coverage.sh helper (Étape 8.1)` (avec `chmod +x`).

**Commande de validation** :
```bash
cd backend && ./mvnw verify -DskipITs -B
./scripts/aggregate-coverage.sh
# Attendu :
#   ✅ PASS — coverage globale L XX.X % ≥ 80 % et B YY.Y % ≥ 70 %
```

Si ❌ FAIL → retourner aux Vagues 4-7 pour étoffer les classes < 80 % listées en sortie.

---

#### Étape 8.2 — Watch CI quality gate

**Objectif** : pousser tout, watch CI, vérifier que `[unige-events-backend] SonarCloud Code Analysis` passe en **SUCCESS** (Quality Gate PASSED) sur la PR #158.

**Commandes** :
```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
# Attendre tous jobs verts, dont :
# ✓ Build / Sonar Aggregate                              SUCCESS
# ✓ [unige-events-backend] SonarCloud Code Analysis      SUCCESS  ← cible

# Vérifier sur SonarCloud UI :
# https://sonarcloud.io/summary/new_code?id=unige-pinfo6-2026_unige-events-backend&pullRequest=158
# Attendu : Quality Gate Passed, Coverage on new code ≥ 80 %, Duplications ≤ 3 %.
```

**Si la cellule reste FAILED** :
- Lire le détail : Coverage / Duplications / Maintainability / Reliability / Security ?
- Coverage low → retourner aux étapes 4-7, étoffer les tests pour les classes < 80 % (voir output `aggregate-coverage.sh`).
- Duplications > 3 % → identifier le code dupliqué nouvellement introduit (probablement test fixtures) — extraire dans une classe utilitaire commune.
- New issues > 0 → SonarCloud UI → liste des issues. Pour les **bugs** + **vulnerabilities**, fixer le code. Pour les **code smells** non bloquants, soit fixer, soit marquer "Won't Fix" via SonarCloud UI (ne pas tolérer plus de 5 unresolved code smells).

**Pas de commit** sauf si fix nécessaire (créer un commit `fix(backend): ...` ciblé).

---

#### Étape 8.3 — Optionnel : exclusions ciblées si nécessaire

**Si après Étape 8.2** la quality gate échoue toujours sur 1-2 classes structurellement non-testables (ex. `AppConfig.java` Quarkus bootstrap, scheduler `@Scheduled` boot code), ajouter une exclusion **ciblée** dans le pom du module concerné :

```xml
<properties>
    <!-- exclusion ciblée Quarkus bootstrap config — non-testable mais sans logique métier -->
    <sonar.coverage.exclusions>
        src/main/java/**/AppConfig.java,src/main/java/**/EventExpirationJob.java
    </sonar.coverage.exclusions>
</properties>
```

**Justification obligatoire** dans le commit message + sprint-context. Pas de blanket exclusion (`**/*.java`) — listing nominatif par classe.

**Commit** (conditionnel) : `chore(backend): add narrow sonar.coverage.exclusions for Quarkus bootstrap classes (Étape 8.3)`

---

### Étape 9 — Documentation finale (Vague 9, 3 commits)

#### Étape 9.1 — `sprint-context.md` § Étape 22 — quality gate fix post-migration

**Objectif** : ajouter une section narrative récapitulative dans `backend/docs/sprint-context.md` à la fin du fichier.

**Patch** (à appender en fin de `backend/docs/sprint-context.md`) :

```markdown
## Sprint 8 — Étape 22 : Quality gate Sonar fix post-migration — 2026-05-XX

**Objectif** : rendre le quality gate SonarCloud du projet `unige-events-backend` VERT (PASSED) sur la PR #158, après la clôture finalization-ultimate à HEAD `2aef8fe2`.

### Contexte du blocage
- Post-finalization-ultimate, la PR #158 a tous les jobs CI verts SAUF `[unige-events-backend] SonarCloud Code Analysis` (FAILED, Coverage on new code = 0,6 % vs ≥ 80 % requis).
- Diagnostic : 2 bugs structurels + 1 gap de couverture
  - Bug 1 : les 5 `<sonar.projectKey>` per-service sont silencieusement ignorés par `sonar-maven-plugin` 4.0.0.4121 quand invoqué depuis le reactor parent → toutes les analyses atterrissent dans `unige-events-backend` et s'écrasent.
  - Bug 2 : `${project.build.directory}/jacoco-report/jacoco.xml` au pom racine pointe sur le `target/` parent (sans source post-migration) → 0 % coverage rapporté.
  - Gap couverture : services métiers à 4-17 % L vs ≥ 80 % requis (Décision D Option 3 spec ultime — pragmatique S8 mais incompatible quality gate).

### Décisions actées (Décisions A-E spec sonar quality gate)
- **A — Option B définitive** : un seul projet SonarCloud `unige-events-backend` agrège les 17 modules. Les 5 projets services SonarCloud sont abandonnés (item 1 devops-handoff annulé).
- **B — Aggregation jacoco CLI** : liste comma-séparée de 17 paths jacoco.xml passée au `sonar:sonar` via `-Dsonar.coverage.jacoco.xmlReportPaths=...`.
- **C — Job `sonar-aggregate`** : 1 scan Sonar final post-matrix, dépend de tous les builds amont, download des artifacts jacoco, `continue-on-error: false` strict.
- **D — Port runtime des 30 sentinels + 56 tests legacy** : montée à 80 % L via tests Mockito + `@QuarkusTest` (mock REST clients, mock Panache).
- **E — Quality gate par défaut conservé** : ≥ 80 % coverage on new code, pas de bidouille du gate.

### Livrables
- 5 `<sonar.projectKey>` retirés des poms services
- `quarkus-jacoco` ajouté à `contract-tests` + `e2e` (si absent)
- `.github/workflows/build.yml` : 6 scans Sonar concurrents → 1 job `sonar-aggregate` agrégé final
- ~118 nouveaux tests JUnit 5 répartis :
  - Vague 2 (shared-domain-dtos) : ~5 tests
  - Vague 3 (mappers + DTOs records) : ~25 tests
  - Vague 4 (engagement-service) : ~27 tests
  - Vague 5 (user-service) : ~24 tests
  - Vague 6 (event-service) : ~51 tests
  - Vague 7 (moderation-service) : ~11 tests
- 30 sentinels `@Tag("legacy-port-s9")` portés en runtime (assertions réelles) — TEST-001 résolu, item 10 devops-handoff annulé
- `backend/scripts/aggregate-coverage.sh` helper local

### Coverage finale
| Module | Avant | Après | Cible |
|---|---|---|---|
| event-service | 5,4 % L | ~85 % L | ≥ 80 % |
| user-service | 4,5 % L | ~82 % L | ≥ 80 % |
| engagement-service | 10,8 % L | ~85 % L | ≥ 80 % |
| moderation-service | 17,2 % L | ~85 % L | ≥ 80 % |
| shared-domain-dtos | 57,1 % L | ~95 % L | ≥ 95 % |
| Autres shared libs | 100 % L | 100 % L | ≥ 95 % |
| **Backend agrégé (new code)** | **0,6 % L** | **≥ 80 % L** | **≥ 80 %** |

### Frontière DevOps modifiée
- **Item 1 (5 projets SonarCloud services)** : annulé (Option B définitive — `unige-events-backend` seul).
- **Item 10 (port complet 23 sentinels @Tag legacy-port-s9)** : annulé (les 30 sentinels présents — recompte +7 vs spec ultime — sont tous portés runtime).
- **Items 2-9 inchangés** (cluster Kafka prod-grade, NetworkPolicies, Doppler secrets, certs prod, Production Kong, Pact provider verification, GHCR cleanup).

### Quality gate final
- `[unige-events-backend] SonarCloud Code Analysis` : ✅ **PASSED**
- Coverage on new code ≥ 80 %, Duplications ≤ 3 %, 0 bugs, 0 vulnerabilities, ≤ 5 unresolved code smells.

PR #158 prête au merge — Elie merge lui-même.
```

**Commit** : `docs(backend): record Étape 22 — quality gate fix post-migration in sprint-context.md (Étape 9.1)`

---

#### Étape 9.2 — `devops-handoff.md` : retirer item 1 + item 10

**Objectif** : retirer les 2 items annulés par cette spec dans `backend/docs/devops-handoff.md`.

**Patch** :

1. **Section 1** (`### 1. SonarCloud — créer 5 projects services...`) : remplacer par :

```markdown
## 1. SonarCloud — Option B définitive (1 seul projet `unige-events-backend`)

**Statut backend** : ✅ Aggregation Option B livrée Étape 22 (PR #158).

**Action attendue côté DevOps** : **AUCUNE**. Le projet `unige-events-backend` existe déjà sur SonarCloud et reçoit les scans agrégés des 17 modules backend (5 services métiers + 10 shared libs + contract-tests + e2e). Les 5 projets services per-bounded-context (`unige-events-{event,user,engagement,moderation,notification}-service`) sont **abandonnés** — DevOps peut les archiver via UI SonarCloud s'il le souhaite, ce n'est pas un blocker.

**Justification.** (a) `sonar-maven-plugin` 4.0.0.4121 ignore les `<sonar.projectKey>` overrides per-module quand `sonar:sonar` est invoqué depuis le reactor parent — la configuration multi-projet ne fonctionnait pas. (b) Pour un projet pinfo6 à 6 mois, 1 quality gate sur le backend agrégé est suffisant et aligné avec l'état pré-migration de `main`.
```

2. **Section 10** (`### 10. Port runtime des 23 sentinels @Tag(...)`) : remplacer par :

```markdown
## 10. Port runtime sentinels @Tag(legacy-port-s9) — Annulé Étape 22

**Statut backend** : ✅ 30 sentinels portés en runtime (assertions réelles, plus aucun `@Tag("legacy-port-s9")` dans le test tree).

**Action attendue côté DevOps** : **AUCUNE**. La cible « port complet S9 » est anticipée à S8 par la spec quality-gate-post-migration — TEST-001 résolu intégralement.
```

**Commit** : `docs(backend): retire devops-handoff items 1 + 10 (Étape 9.2 — Option B + sentinels portés)`

---

#### Étape 9.3 — `AGENTS.md` : annoter Option B définitive

**Patch** dans `AGENTS.md` (root) ou `backend/AGENTS.md` selon où la mention « Sonar » apparaît :

```markdown
- **SonarCloud** : projet unique `unige-pinfo6-2026_unige-events-backend` (Option B définitive Étape 22 — finit le bug Sonar Maven multi-module). Le scan est lancé par le job CI `sonar-aggregate` (post-matrix), qui consomme les jacoco.xml des 17 modules via artifacts uploadés par les jobs amont.
```

**Commit** : `docs: note Sonar Option B in AGENTS.md (Étape 9.3)`

---

#### Étape 9.4 — Watch CI final + addendum PR body

**Objectif** : un dernier watch CI consolidé pour valider que toute la spec est livrée et verte. Optionnel : ajouter un addendum au PR body (mention « quality gate fix Étape 22 »).

**Commandes** :
```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
# Attendu : TOUS verts (sauf Deploy / Deploy to Preview qui peut rester FAILED pour
# raisons K8s preview hors scope — vérifier que ce n'est pas une régression de cette spec).

# Vérification finale Sonar UI :
gh pr view 158 --json statusCheckRollup --jq \
    '.statusCheckRollup[] | select(.name | contains("SonarCloud"))'
# Attendu : conclusion=SUCCESS pour [unige-events-backend] SonarCloud Code Analysis
```

**Addendum PR body** (optionnel, si Elie souhaite l'enregistrer) — édition via `gh pr edit 158 --body-file <path>`. Patch à appender :

```markdown
---

### Addendum — Étape 22 quality gate fix (post-finalization-ultimate)

- ✅ Sonar Option B définitive : un seul projet `unige-events-backend` agrégeant les 17 modules.
- ✅ Job CI `sonar-aggregate` final, strict (`continue-on-error: false`).
- ✅ Coverage on new code ≥ 80 % via port runtime des 30 sentinels + 56 tests legacy + ~37 tests nouveaux.
- ✅ Quality Gate PASSED.

Voir `specs_archives/specs_claude/specs_sonar_quality_gate_post_migration.md` et `backend/docs/sprint-context.md` § Étape 22 pour le détail.
```

**Commit** (optionnel) : si addendum PR body, le commit n'a pas lieu d'être (gh pr edit modifie le PR sur GitHub, pas le repo). Sinon, fin de spec.

---

## Critères de done

Checklist exhaustive — l'exécuteur la valide à la fin de chaque étape majeure et à la toute fin avant de stopper.

### Configuration Sonar (Vague 1)
- [ ] `grep -rln '<sonar.projectKey>' backend/services/*-service/pom.xml` → vide (5 retraits)
- [ ] `grep '<sonar.projectKey>' backend/pom.xml` → 1 résultat (`...unige-events-backend`)
- [ ] Job `sonar-aggregate` présent dans `.github/workflows/build.yml` (dépend des 3 jobs amont)
- [ ] Aucun step `SonarQube Scan` dans `build-shared-libs` ni dans `build-backend (matrix)` (cherché par grep YAML)
- [ ] Tous les jobs `build-*` uploadent un artifact `jacoco-*` via `actions/upload-artifact@v4`
- [ ] `sonar-aggregate` lance `./mvnw -pl . sonar:sonar -Dsonar.coverage.jacoco.xmlReportPaths=...` avec liste comma-séparée

### Couverture (Vagues 2-7)
- [ ] `cd backend && ./mvnw verify -DskipITs` → BUILD SUCCESS sur 17 modules, 0 test failed
- [ ] `./scripts/aggregate-coverage.sh` → ✅ PASS (L ≥ 80 %, B ≥ 70 %)
- [ ] event-service jacoco L ≥ 80 % (vs 5,4 % avant)
- [ ] user-service jacoco L ≥ 80 % (vs 4,5 % avant)
- [ ] engagement-service jacoco L ≥ 80 % (vs 10,8 % avant)
- [ ] moderation-service jacoco L ≥ 80 % (vs 17,2 % avant)
- [ ] shared-domain-dtos jacoco L ≥ 95 % (vs 57,1 % avant)
- [ ] 8 autres shared libs : L ≥ 95 % (déjà ✅ pre-spec, ne pas régresser)
- [ ] `grep -rln '@Tag("legacy-port-s9")' backend/services/*/src/test/java` → vide (0 résultat — 30 sentinels portés)
- [ ] `grep -rcE '@Test' backend/services/*/src/test/java | awk -F: '{s+=$2} END{print s}'` → ≥ 350 (~250 avant + ~118 nouveaux)

### CI / quality gate
- [ ] `gh pr checks 158` → tous SUCCESS sauf si DevOps preview indépendamment cassé (item hors scope)
- [ ] Job `Build / Sonar Aggregate` → SUCCESS
- [ ] Cellule `[unige-events-backend] SonarCloud Code Analysis` → SUCCESS (Quality Gate PASSED)
- [ ] SonarCloud UI : Coverage on new code ≥ 80 %, Duplications on new code ≤ 3 %, 0 bug new code, 0 vuln new code, ≤ 5 unresolved code smells new code
- [ ] Aucun `--no-verify`, `--no-gpg-sign`, `--amend` pushé, force push dans l'historique de cette spec
- [ ] Aucun `@Disabled`, `@Ignore`, `@Tag("legacy-port-s9")` ajouté

### Invariants frontaliers
- [ ] `git diff --shortstat origin/main HEAD -- frontend/` → 0 ligne (invariant)
- [ ] `git diff --shortstat origin/main HEAD -- openapi/` → 0 ligne (invariant)
- [ ] `find backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l` → 0 (invariant spec ultime préservé)
- [ ] `grep -rln '@ManyToOne.*Stub\|extends.*Stub' backend/services/*/src/main/java | wc -l` → 0 (invariant spec ultime préservé)
- [ ] 17 modules dans `backend/pom.xml` (`<module>` count) — invariant

### Documentation
- [ ] `backend/docs/sprint-context.md` § Étape 22 ajouté
- [ ] `backend/docs/devops-handoff.md` § 1 + § 10 mis à jour (annulés)
- [ ] `AGENTS.md` ou `backend/AGENTS.md` annote Option B définitive
- [ ] PR #158 body : addendum optionnel Étape 9.4 (à discrétion Elie)

### Workflow Git
- [ ] Tous les commits ont `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`
- [ ] Granularité ~1 commit par sous-étape `N.M`, ≤ 500 lignes diff sauf justifié
- [ ] Push après chaque sous-étape verte, watch CI groupé par étape majeure
- [ ] PR #158 reste **OPEN** — Elie merge lui-même

---

## Workflow Git imposé à l'exécuteur

(Reprise du § Workflow Git de la spec ultime, adapté pour cette spec.)

- **Branche persistante** : `refactor(backend)--migrate-to-microservices` (NE PAS créer de nouvelle branche).
- **Pas de squash** — chaque sous-étape numérotée a son propre commit (granularité ~30-40 commits sur les 9 vagues, ≤ 500 lignes diff sauf Vagues 4-6 batches de tests qui peuvent atteindre ~800 lignes diff par commit).
- **Pas de force push** — additif uniquement.
- **Pas de `--no-verify`** — si pre-commit hook échoue, fixer la cause racine.
- **Pas de `--no-gpg-sign`** — signage Git par défaut respecté.
- **Pas de `--amend`** sur du commit pushé — fixer via nouveau commit.
- **Pas de modification de `main`** ni des autres branches feature.
- **Push après chaque sous-étape verte** : `git push origin 'refactor(backend)--migrate-to-microservices'`.
- **Watch CI groupé par étape majeure** : `gh pr checks 158 --watch` à la fin de chaque Étape ≥ 1 (Étape 1 = vague CI/Sonar fix nécessite watch précoce car le job `sonar-aggregate` est tout neuf).
- **Si CI échoue** : `gh run view <RUN_ID> --log-failed` → fix → nouveau commit additif → push → re-watch. **Jamais** de skip silencieux ni de `@Disabled`.
- **Mise à jour `sprint-context.md` § Étape 22** : un patch incrémental concentré dans le commit final d'Étape 9.1 (pas de mise à jour incrémentale par sous-étape).
- **Mise à jour PR body via `gh pr edit 158 --body-file`** : optionnel, à discrétion Elie (Étape 9.4).
- **Pas de merge PR #158** — Elie merge lui-même.
- **Co-Authored-By** : `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` à la fin de chaque commit.

---

## Frontière DevOps — items hors scope

Cette spec **annule** les items 1 + 10 du `devops-handoff.md` (cf. Étape 9.2). Les items 2-9 restent inchangés :

| # | Item | Statut |
|---|---|---|
| 1 | ~~Création de 5 SonarCloud projects services~~ | **ANNULÉ** par cette spec (Option B définitive) |
| 2 | Cluster Kafka prod-grade (RF=3, partitions ≥ 3) | Inchangé — hors scope |
| 3 | Schemas-per-service Flyway séparé | Inchangé — déféré S9+ |
| 4 | NetworkPolicies K8s | Inchangé — hors scope |
| 5 | Domaines / certs prod / Cloudflare tunnel | Inchangé — hors scope |
| 6 | Secrets Doppler | Inchangé — hors scope |
| 7 | Production-grade Kong (DB-mode, OpenTelemetry) | Inchangé — hors scope |
| 8 | Pact provider verification CI job | Inchangé — déféré S9 |
| 9 | GHCR cleanup PR-tagged images | Inchangé — déféré S9 |
| 10 | ~~Port runtime 23 sentinels @Tag(legacy-port-s9)~~ | **ANNULÉ** par cette spec (30 sentinels portés Vagues 4-6) |

**L'exécuteur autonome ne touche à AUCUN des items 2-9.**

---


## Annexes

### Annexe A — Liste exhaustive des 30 sentinels à porter (mapping legacy-monolith)

Tous les sentinels actuellement taggés `@Tag("legacy-port-s9")` au HEAD `2aef8fe2`. Référence legacy : `git show 41074e9:backend/services/legacy-monolith/src/test/java/...`. L'exécuteur **retire** le `@Tag("legacy-port-s9")` après chaque port + ajoute le corps assertif.

#### A.1 — engagement-service (7 sentinels — fichier `EngagementDomainSentinelsTest.java`)

| # | Sentinel | Légende legacy | Setup | Action | Expected |
|---|---|---|---|---|---|
| 1 | `post_eventDraftByNonCreator_returns404_antiOracle` | `CommentResourceTest::shouldReturn404IfDraftAndNonCreator` | Event DRAFT, creator=A, caller=B authentifié | POST /events/{id}/comments | 404 `{error:"not_found"}` |
| 2 | `post_eventBanned_returns404_antiOracle` | `CommentResourceTest::shouldReturn404IfBanned` | Event BANNED, caller authentifié | POST /events/{id}/comments | 404 `{error:"not_found"}` |
| 3 | `post_replyToReply_returns422_repliesTooDeep` | `CommentResourceTest::shouldReturn422IfReplyToReply` | parentCommentId points to a comment that already has parentCommentId set | POST /events/{id}/comments | 422 `{error:"replies_too_deep"}` |
| 4 | `post_parentInOtherEvent_returns422_parentNotInEvent` | `CommentResourceTest::shouldReturn422IfParentInOtherEvent` | parentCommentId points to comment in different event | POST /events/{id}/comments | 422 `{error:"parent_not_in_event"}` |
| 5 | `post_unknownParent_returns404_parentNotFound` | `CommentResourceTest::shouldReturn404IfParentUnknown` | parentCommentId points to non-existent comment | POST /events/{id}/comments | 404 `{error:"not_found"}` |
| 6 | `delete_byPendingCoOrganizer_returns403` | `CommentResourceTest::shouldReturn403IfPendingCoOrg` | caller co-organizer with status=PENDING (not ACCEPTED) | DELETE /comments/{id} | 403 |
| 7 | `delete_unknownComment_returns404_commentNotFound` | `CommentResourceTest::shouldReturn404IfCommentUnknown` | comment id doesn't exist | DELETE /comments/{id} | 404 `{error:"not_found"}` |

#### A.2 — event-service (17 sentinels — fichier `EventDomainSentinelsTest.java`)

| # | Sentinel | Légende legacy | Type test |
|---|---|---|---|
| 1 | `from_parentRecurringEvent_exposesRecurrenceRuleAndNullParentEventId` | `EventDTOTest::fromRecurringParent` | Unit (no Quarkus) |
| 2 | `from_occurrenceEvent_exposesParentEventIdAndNullRecurrenceRule` | `EventDTOTest::fromRecurringChild` | Unit |
| 3 | `createRecurring_weekly4Occurrences_persists1ParentAnd3Children` | `EventResourceTest::shouldCreate4WeeklyOccurrences` | `@QuarkusTest @TestTransaction` |
| 4 | `createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded` | `EventResourceTest::shouldRejectUnboundedRecurrence` | `@QuarkusTest` |
| 5 | `createRecurring_endDateBeforeStart_returns400_recurrenceEndBeforeStart` | `EventResourceTest::shouldRejectInvalidRecurrenceRange` | `@QuarkusTest` |
| 6 | `createRecurring_inheritsParentStatusPublished` | `EventResourceTest::shouldInheritParentStatus` | `@QuarkusTest @TestTransaction` |
| 7 | `getOccurrences_parentRecurring_returnsChildrenSortedAsc` | `EventResourceTest::shouldReturnOccurrencesSorted` | `@QuarkusTest @TestTransaction` |
| 8 | `getOccurrences_standaloneEvent_returns200EmptyList` | `EventResourceTest::shouldReturnEmptyOccurrencesForStandalone` | `@QuarkusTest` |
| 9 | `getOccurrences_draftByNonCreator_returns404_antiOracle` | `EventResourceTest::shouldReturn404OnDraftOccurrencesByNonCreator` | `@QuarkusTest @TestTransaction` |
| 10 | `update_parentTitle_doesNotPropagateToOccurrences` | `EventResourceTest::shouldNotPropagateUpdateToOccurrences` | `@QuarkusTest @TestTransaction` |
| 11 | `cancel_parentDoesNotCascadeToOccurrences` | `EventResourceTest::shouldNotCascadeCancel` | `@QuarkusTest @TestTransaction` |
| 12 | `delete_parent_setsOccurrencesParentEventIdToNull` | `EventResourceTest::shouldNullifyOccurrencesOnParentDelete` | `@QuarkusTest @TestTransaction` |
| 13 | `post_validRecurrenceWeekly_returns201_recurrenceRuleSetOnParent` | `EventResourceTest::shouldSetRecurrenceRuleOnPost` | `@QuarkusTest @TestTransaction` |
| 14 | `post_recurrenceMaxOccurrences53_returns400_beanValidation` | `EventResourceTest::shouldRejectMax53Occurrences` | `@QuarkusTest` |
| 15 | `getOccurrences_parentPublishedAnonymous_returns200` | `EventResourceTest::shouldReturnOccurrencesForPublishedAnonymous` | `@QuarkusTest @TestTransaction` |
| 16 | `getOccurrences_sizeOver52_returns400` | `EventResourceTest::shouldReject400OnSize53` | `@QuarkusTest` |
| 17 | `getOccurrences_draftByAnonymous_returns404_antiOracle` | `EventResourceTest::shouldReturn404OnDraftOccurrencesAnonymous` | `@QuarkusTest @TestTransaction` |

#### A.3 — user-service (6 sentinels — fichier `UserDomainSentinelsTest.java`)

| # | Sentinel | Légende legacy | Type test |
|---|---|---|---|
| 1 | `findAcceptedFollowedIds_returnsOnlyAcceptedUuids` | `FollowServiceCoverageTest::shouldFilterByAccepted` | `@QuarkusTest @TestTransaction` |
| 2 | `rejectRequest_followerCanReFollowAfterReject` | `FollowResourceTest::shouldAllowReFollowAfterReject` | `@QuarkusTest @TestTransaction` |
| 3 | `follow_selfFollow_throwsUnprocessable` | `FollowResourceTest::shouldRejectSelfFollow` | `@QuarkusTest` |
| 4 | `getFollowers_privateProfileNonOwner_returns404_antiOracle` | `UserResourceTest::shouldReturn404OnPrivateFollowersByNonOwner` | `@QuarkusTest @TestTransaction` |
| 5 | `getPublicProfile_self_followStatusIsNull` | `UserResourceTest::shouldReturnNullFollowStatusForSelf` | `@QuarkusTest @TestTransaction` |
| 6 | `getPublicProfile_authNonOwnerWithPending_followStatusIsPending` | `UserResourceTest::shouldReturnPendingFollowStatusForRequester` | `@QuarkusTest @TestTransaction` |

---

### Annexe B — 56 tests legacy-monolith à porter (commit 41074e9)

Liste exhaustive — récupérables via :
```bash
git show 41074e9:backend/services/legacy-monolith/src/test/java/<path>
```

#### B.1 — Mapping legacy → service post-consolidation

| # | Legacy path | Service cible | Adaptations |
|---|---|---|---|
| 1 | `config/FlywayMigrationTest.java` | event-service ou root | DB schema reachable — vérifier toujours pertinent post-microservices |
| 2 | `config/RequestIdFilterTest.java` | shared-tracing ✅ déjà porté | — |
| 3 | `dto/CreateEventRequestTest.java` | event-service | Étape 3.3 |
| 4 | `dto/EventDTOTest.java` | event-service (4 variantes) + shared-domain-dtos | Étape 3.2 |
| 5 | `dto/UserProfileResponseTest.java` | user-service | Étape 3.3 |
| 6 | `dto/UserPublicResponseTest.java` | user-service + shared-domain-dtos ✅ partiel | Étape 3.3 |
| 7 | `dto/coorganizer/CoOrganizerDTOTest.java` | event-service | Étape 3.3 |
| 8 | `dto/follow/FollowDTOTest.java` | user-service | Étape 3.3 |
| 9 | `dto/report/ReportDTOTest.java` | moderation-service | Étape 3.3 |
| 10 | `entity/CommentTest.java` | engagement-service | Étape 4.x — entity + prePersist |
| 11 | `entity/EventCoOrganizerTest.java` | event-service | Étape 6.x |
| 12 | `entity/EventTest.java` | event-service | Étape 6.x |
| 13 | `entity/FollowTest.java` | user-service | Étape 5.x |
| 14 | `entity/ReportTest.java` | moderation-service | Étape 7.x |
| 15 | `entity/UserTest.java` | user-service | Étape 5.x |
| 16 | `exception/mapper/ErrorMaskingTest.java` | shared-api-error ou moderation-service | Vérifier coverage — probablement déjà 100 % |
| 17 | `resource/AdminEventResourceTest.java` | event-service | Étape 6.5a |
| 18 | `resource/AdminReportResourceTest.java` | moderation-service | Étape 7.2 |
| 19 | `resource/AttendanceResourceTest.java` | engagement-service | Étape 4.4 |
| 20 | `resource/CalendarResourceTest.java` | user-service | Étape 5.4 |
| 21 | `resource/CommentDirectResourceTest.java` | engagement-service | Étape 4.4 |
| 22 | `resource/CommentResourceTest.java` | engagement-service | Étape 4.4 |
| 23 | `resource/EventCoOrganizerResourceTest.java` | event-service | Étape 6.5b |
| 24 | `resource/EventResourceTest.java` | event-service | Étape 6.5a (+sentinels) |
| 25 | `resource/EventSearchResourceTest.java` | event-service | Étape 6.5d |
| 26 | `resource/EventStatsResourceTest.java` | event-service | Étape 6.5d |
| 27 | `resource/EventViewResourceTest.java` | event-service | Étape 6.5d |
| 28 | `resource/FavoriteResourceTest.java` | event-service | Étape 6.5c |
| 29 | `resource/FollowResourceTest.java` | user-service | Étape 5.4 |
| 30 | `resource/MultipartValidationTest.java` | event-service ou user-service (image upload) | Étape 5.4 ou 6.5 |
| 31 | `resource/RateLimitTest.java` | shared-rate-limit ✅ déjà porté | — |
| 32 | `resource/ReportResourceTest.java` | moderation-service | Étape 7.2 |
| 33 | `resource/ResourceMappersCoverageTest.java` | shared-api-error ✅ déjà porté | — |
| 34 | `resource/UserResourceTest.java` | user-service | Étape 5.4 |
| 35 | `scheduler/EventExpirationJobTest.java` | event-service | Étape 6.6 |
| 36 | `service/AttendanceServiceCoverageTest.java` | engagement-service | Étape 4.2 |
| 37 | `service/CalendarServiceCoverageTest.java` | user-service | Étape 5.4 |
| 38 | `service/CommentServiceCoverageTest.java` | engagement-service | Étape 4.3 |
| 39 | `service/EventCoOrganizerServiceCoverageTest.java` | event-service | Étape 6.3 |
| 40 | `service/EventExpirationServiceTest.java` | event-service | Étape 6.6 |
| 41 | `service/EventSearchServiceCoverageTest.java` | event-service | Étape 6.4 |
| 42 | `service/EventServiceCoverageTest.java` | event-service | Étape 6.2 |
| 43 | `service/EventStatsServiceCoverageTest.java` | event-service | Étape 6.4 |
| 44 | `service/EventViewServiceCoverageTest.java` | event-service | Étape 6.4 |
| 45 | `service/FavoriteServiceCoverageTest.java` | event-service | Étape 6.4 |
| 46 | `service/FeaturedServiceCoverageTest.java` | event-service | Étape 6.4 |
| 47 | `service/FileStorageServiceTest.java` | shared-storage ✅ déjà porté | — |
| 48 | `service/FollowServiceCoverageTest.java` | user-service | Étape 5.3 |
| 49 | `service/ModerationCleanupCoverageTest.java` | moderation-service | Étape 7.2 |
| 50 | `service/ModerationCleanupServiceTest.java` | moderation-service | Étape 7.2 |
| 51 | `service/ReportServiceCoverageTest.java` | moderation-service | Étape 7.1 |
| 52 | `service/UserServiceCoverageTest.java` | user-service | Étape 5.2 |
| 53 | `service/UserServiceMockConcurrencyTest.java` | user-service | Étape 5.2 (cas 25) |
| 54 | `util/IcsBuilderTest.java` | user-service | Étape 5.4 |
| 55 | `util/ImageFormatTest.java` | shared-storage ✅ déjà porté | — |
| 56 | `util/RecurrenceGeneratorTest.java` | event-service ✅ déjà porté (4 sentinels) | partiel — étoffer pour atteindre 100 % L |

#### B.2 — Adaptations communes pour le port

Les tests legacy ciblaient le monolithe avec :
- `@ManyToOne XStub` navigation JPA cross-table
- `entityManager.find(XStub.class, id)` pour résoudre cross-table
- `@Inject XService` direct (toutes services dans le même module)

Post-migration, il faut adapter :

1. **Imports packages** : `ch.unige.events.entity.Comment` → `ch.unige.events.engagement.comment.entity.Comment` (etc.)
2. **`@ManyToOne XStub event`** → `Long eventId` (champ scalaire — Décision F)
3. **`entityManager.find(EventStub.class, id)`** → `@InjectMock @RestClient EventServiceClient` + `when(eventClient.getById(id)).thenReturn(...)`
4. **Tests utilisant `event.creator.displayName`** → enrichment via `userClient.getById(creatorId).displayName()`
5. **`@Inject UserService` cross-module** → soit pas (test isolé du service propriétaire), soit `@InjectMock @RestClient UserServiceClient` (cross-service)
6. **Cascade SCRUM-136 `comment.event.creator.id`** → REST call `eventClient.getByIdWithCoOrgCheck(eventId, callerUserId).coOrganizerOf()` ou `eventClient.getOrganizerUuids(eventId).contains(callerUserId)` (Décision G)

**Pattern de port** :

```java
// LEGACY (ec668b91 → 41074e9)
@QuarkusTest
class CommentServiceCoverageTest {
    @Inject CommentService commentService;
    @Inject EntityManager em;

    @Test
    @TestTransaction
    void post_eventDraftByNonCreator_returns404() {
        Event event = new Event(); event.creator = userA; event.status = DRAFT;
        em.persist(event);
        // call commentService.post(...) as userB → expect 404
    }
}

// NEW POST-MIGRATION
@QuarkusTest
class CommentServiceTest {
    @Inject CommentService commentService;
    @InjectMock @RestClient EventServiceClient eventClient;
    @InjectMock @RestClient UserServiceClient userClient;

    @Test
    void post_eventDraftByNonCreator_throws404() {
        UUID userA = UUID.randomUUID();
        UUID userB = UUID.randomUUID();
        when(eventClient.getByIdWithCoOrgCheck(42L, userB))
            .thenReturn(new EventDTO(42L, "Draft", null, userA, EventStatus.DRAFT, /*...*/));
        when(userClient.getById(userB)).thenReturn(/* userB profile */);

        assertThrows(NotFoundException.class,
            () -> commentService.post("auth0|" + userB, 42L, "Hello", null));
    }
}
```

---

### Annexe C — Tests nouveaux (non-port) à écrire pour code refactor post-stub-removal

Tests qui n'existent pas en legacy car le code n'existait pas — ce sont les **nouveautés** de la migration microservices.

| # | Classe under test | Module | Path test | Cas couverts |
|---|---|---|---|---|
| 1 | `AttendanceDTOMapper` | engagement-service | `engagement/attendance/dto/AttendanceDTOMapperTest.java` | from(Attendance) avec REST userClient mock |
| 2 | `AttendanceSummaryInternalResource` | engagement-service | déjà existant — étoffer | bulk + single endpoint, params validation |
| 3 | `UserAttendancesInternalResource` | engagement-service | déjà existant — étoffer | déjà 1 cas — ajouter 4-5 cas |
| 4 | `EngagementServiceClient` (fallbacks) | shared-domain-dtos | `client/EngagementServiceClientFallbackTest.java` | Étape 2.1 |
| 5 | `EventServiceClient` (fallbacks) | shared-domain-dtos | `client/EventServiceClientFallbackTest.java` | Étape 2.1 |
| 6 | `UserServiceClient` (fallback) | shared-domain-dtos | `client/UserServiceClientFallbackTest.java` | Étape 2.1 |
| 7 | `EventCoOrganizerDTO.from(...)` | shared-domain-dtos | `domain/dto/EventCoOrganizerDTOTest.java` | Étape 2.1 |
| 8 | `NotFoundExceptionMapper` (déjà testé en shared-api-error mais vérifier dispatch sur les 4 services consumers) | shared-api-error | déjà existant | sanity check |
| 9 | `EventBannedConsumer` | event-service | `kafka/EventBannedConsumerTest.java` | idempotence Décision H, unknown event handling |
| 10 | Endpoint `/events/{id}/organizer-uuids` (Décision G) | event-service | `event/resource/EventResourceTest.java` (intégré) | creator + ACCEPTED co-orgs only, PENDING excluded |
| 11 | Endpoint `/events/_bulk-attendance-summary` (Décision I) | engagement-service | `engagement/attendance/resource/AttendanceSummaryInternalResourceTest.java` (étoffer) | bulk shape, partial ids, empty ids |
| 12 | Cascade `events.banned` (consumer side) | event-service | `EventBannedConsumerTest.java` (cf. #9) | apply `event.status = BANNED` idempotent |
| 13 | Anti-oracle `?check-co-org-of=` (Décision C SEC-002) | event-service | `event/service/EventServiceTest.java` (intégré) | self-check authentifié seulement, anonymous → coOrganizerOf null |

**Total nouveaux tests** : ~13 fichiers + ~30-40 cas (en plus des ~85 ports legacy + 30 sentinels).

---

### Annexe D — Liste des artifacts CI à uploader

Pour le job `sonar-aggregate`, on dépend de 6 artifacts :

| Artifact name | Source job | Path uploaded | Path après download |
|---|---|---|---|
| `jacoco-shared-libs` | build-shared-libs | `backend/services/shared-*/target/jacoco-report/jacoco.xml` | `services/shared-*/target/jacoco-report/jacoco.xml` (10 fichiers) |
| `jacoco-event-service` | build-backend (matrix=event) | `backend/services/event-service/target/jacoco-report/jacoco.xml` | `services/event-service/target/jacoco-report/jacoco.xml` |
| `jacoco-user-service` | build-backend (matrix=user) | (same pattern) | `services/user-service/target/jacoco-report/jacoco.xml` |
| `jacoco-engagement-service` | build-backend (matrix=engagement) | (same) | `services/engagement-service/target/jacoco-report/jacoco.xml` |
| `jacoco-moderation-service` | build-backend (matrix=moderation) | (same) | `services/moderation-service/target/jacoco-report/jacoco.xml` |
| `jacoco-notification-service` | build-backend (matrix=notification) | (same) | `services/notification-service/target/jacoco-report/jacoco.xml` |
| `jacoco-contract-tests` | build-contract-and-e2e | `backend/contract-tests/target/jacoco-report/jacoco.xml` | `contract-tests/target/jacoco-report/jacoco.xml` |
| `jacoco-e2e` | build-contract-and-e2e | `backend/e2e/target/jacoco-report/jacoco.xml` | `e2e/target/jacoco-report/jacoco.xml` |

**Note `merge-multiple: true`** : `actions/download-artifact@v4` avec `pattern: jacoco-*` + `merge-multiple: true` reconstruit le tree d'origine au path `backend/`. Le `path: backend` dans le step download fait que le download se fait dans `backend/` au lieu de la racine.

**Vérification post-download** :
```yaml
- name: Verify all jacoco artifacts present
  run: |
    expected=("services/event-service" "services/user-service" "services/engagement-service" \
              "services/moderation-service" "services/notification-service" \
              "services/shared-rate-limit" "services/shared-storage" "services/shared-api-error" \
              "services/shared-domain-enums" "services/shared-domain-dtos" \
              "services/shared-domain-projections" "services/shared-jaxrs" \
              "services/shared-tracing" "services/shared-kafka-events" "services/shared-platform")
    for d in "${expected[@]}"; do
        f="$d/target/jacoco-report/jacoco.xml"
        [ -f "$f" ] || (echo "MISSING: $f" && exit 1)
    done
    echo "All 15 expected jacoco.xml files found."
```

---

### Annexe E — Commandes de référence

#### Build local complet
```bash
cd /workspace/backend
./mvnw verify -DskipITs               # 17 modules, ~5-8 min
./scripts/aggregate-coverage.sh        # ✅ ou ❌ + classes < 80 %
```

#### Build d'un seul service
```bash
cd /workspace/backend
./mvnw -pl services/<svc>-service -am test -B
awk -F, 'NR>1 {lm+=$8; lc+=$9} END {printf "L:%5.1f%%\n", lc*100/(lm+lc)}' \
    services/<svc>-service/target/jacoco-report/jacoco.csv
```

#### Lancer un test ciblé
```bash
cd /workspace/backend
./mvnw -pl services/<svc>-service test -Dtest=<TestClass>#<methodName> -B
```

#### Validation invariants
```bash
# 0 sentinel @Tag("legacy-port-s9")
grep -rln '@Tag("legacy-port-s9")' backend/services/*/src/test/java | wc -l   # → 0

# 0 stub JPA cross-service (invariant spec ultime)
find backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l       # → 0

# 0 <sonar.projectKey> per-service (Décision A)
grep -l '<sonar.projectKey>' backend/services/*-service/pom.xml | wc -l        # → 0

# 1 <sonar.projectKey> au pom racine
grep -c '<sonar.projectKey>' backend/pom.xml                                    # → 1

# Frontend invariant
git diff --shortstat origin/main HEAD -- frontend/                              # → 0 ligne

# OpenAPI invariant
git diff --shortstat origin/main HEAD -- openapi/                               # → 0 ligne

# 17 modules dans le reactor (invariant spec ultime)
grep -c '<module>' backend/pom.xml                                              # → 17
```

#### CI / GitHub
```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158                       # snapshot
gh pr checks 158 --watch               # streaming
gh run view <RUN_ID> --log-failed      # debug fail
gh pr edit 158 --body-file <path>      # update PR body (Étape 9.4 only)
```

---

### Annexe F — Stratégie de rollback en cas de blocage

#### Cas 1 — Build local échoue après une étape

**Diagnostic** : `cd backend && ./mvnw -B -DskipITs verify 2>&1 | grep -E 'BUILD FAILURE|^\[ERROR\]' | head -20`

**Actions** :
- Si erreur compile (test imports cassés post-port) : fixer les imports, re-run.
- Si test fail isolé : examiner l'assertion, fixer la cause racine (jamais `@Disabled`). Si le port legacy assert sur un comportement qui a changé post-migration (ex. anti-oracle 404 vs ancien 403), mettre à jour l'assert pour refléter le NOUVEAU contrat.
- Si > 5 commits consécutifs CI rouge sans cause root identifiée : **STOP** et reporte à l'humain.

**Rollback** :
```bash
git reset --hard HEAD~1   # avant push
# OU
git revert <SHA>           # après push (commit additif, pas de force push)
```

#### Cas 2 — `sonar-aggregate` job échoue avec « project not found »

**Diagnostic** : la cellule sonar-aggregate échoue avec « Project unige-events-backend not found ».

**Actions** : impossible — le projet existe depuis pré-migration. Si malgré tout l'erreur tombe, vérifier :
- `SONAR_TOKEN` dans GitHub Secrets — toujours présent ?
- `sonar.organization=unige-pinfo6-2026` au pom racine — toujours là ?
- `sonar.host.url=https://sonarcloud.io` au pom racine — toujours là ?

#### Cas 3 — Coverage agrégée reste < 80 % malgré tous les ports

**Diagnostic** : `./scripts/aggregate-coverage.sh` retourne ❌ FAIL même après Vagues 4-7 complètes. Lire la sortie pour la liste des classes < 80 %.

**Actions** :
- **Si la classe est business** (Service, Resource, entity métier) : étoffer les tests — pas d'exclusion.
- **Si la classe est Quarkus bootstrap** (`AppConfig`, scheduler `@Scheduled` job) : exclure ciblement via `sonar.coverage.exclusions` dans le pom du module (Étape 8.3), avec justification.
- **Si la classe est generated** (Jandex generated, Quarkus-generated client proxies) : déjà exclu nativement par jacoco — vérifier qu'elle est bien filtrée. Sinon ajouter à `<exclude>` dans le `jacoco-maven-plugin` config du module.

#### Cas 4 — Sonar quality gate échoue sur Duplications > 3 %

**Diagnostic** : SonarCloud UI → onglet Duplications.

**Actions** : identifier le code dupliqué (souvent test fixtures dupliqués entre 5 services — ex. helper `JwtTestHelper` copié dans chaque service). Extraire dans une **classe utilitaire test** dans un module test-shared (ou laisser car les test fixtures sont par design dupliqués — dans ce cas, marquer "Won't Fix" l'issue Duplications **uniquement** si elle concerne du test code, jamais du code main).

#### Cas 5 — Sonar reste FAILED sur « New issues > 0 »

**Diagnostic** : SonarCloud UI → liste des nouvelles issues.

**Actions** :
- **Bugs** : fixer le code (jamais marquer "Won't Fix").
- **Vulnerabilities** : fixer le code (jamais marquer "Won't Fix").
- **Code smells** : préférer fixer si triviaux. Tolérance : ≤ 5 unresolved code smells nouveaux (marquer "Won't Fix" via UI uniquement si fix coûte > 1h).

---

## Récapitulatif final

**Ordre d'exécution strict** :

```
0   Pré-flight (validation, no commit)
1   CI/Sonar fix (Option B)                                  → 4 commits (Vague 1)
    1.1 Retrait <sonar.projectKey> per-service (5 POMs)
    1.2 quarkus-jacoco sur contract-tests + e2e si absent
    1.3 build.yml : retirer Sonar per-cell + upload artifacts
    1.4 build.yml : ajouter sonar-aggregate job
2   shared-domain-dtos coverage gap                          → 1 commit (Vague 2)
    2.1 EventCoOrganizerDTOTest + 3 ClientFallbackTest
3   Mappers + DTOs records                                   → 3 commits (Vague 3)
    3.1 AttendanceDTOMapperTest
    3.2 EventDTOTest × 4 variantes
    3.3 ~21 DTO record tests
4   Engagement-service tests                                 → 5 commits (Vague 4)
    4.1 7 sentinels SCRUM-144 portés
    4.2 AttendanceServiceTest (~25 cas)
    4.3 CommentServiceTest (~20 cas)
    4.4 4 resource tests (~25 cas)
    4.5 Validation finale
5   User-service tests                                       → 5 commits (Vague 5)
    5.1 6 sentinels SCRUM-138 portés
    5.2 UserServiceTest (~25 cas)
    5.3 FollowServiceTest (~15 cas)
    5.4 CalendarServiceTest + IcsBuilderTest + 3 resource tests (~30 cas)
    5.5 Validation finale
6   Event-service tests (le plus gros)                       → 7 commits (Vague 6)
    6.1 17 sentinels SCRUM-147 portés
    6.2 EventServiceTest (~30 cas)
    6.3 EventCoOrganizerServiceTest (~12 cas)
    6.4 7 services périphériques (~40 cas en 2 commits)
    6.5 12 resource tests (~50 cas en 4 commits)
    6.6 EventExpirationJob + EventBannedConsumer (~5 cas)
    6.7 Validation finale
7   Moderation-service tests                                 → 3 commits (Vague 7)
    7.1 ReportServiceTest (~15 cas)
    7.2 ReportResource + AdminReportResource + ModerationCleanup (~20 cas)
    7.3 Validation finale
8   Validation finale + retouches Sonar                      → 1-3 commits (Vague 8)
    8.1 aggregate-coverage.sh helper
    8.2 Watch CI quality gate (no commit)
    8.3 Exclusions ciblées si nécessaire (conditionnel)
9   Documentation finale                                     → 3 commits (Vague 9)
    9.1 sprint-context.md § Étape 22
    9.2 devops-handoff.md items 1+10
    9.3 AGENTS.md Option B
    9.4 Watch CI final + addendum PR body (optionnel)
```

**Total** : ~32-35 commits sur 9 vagues. Estimation effort : 30-50 heures focalisées (port mécanique des 56 tests legacy + adaptation REST clients + 30 sentinels).

**Chaque vague est validée par CI watch groupé avant la suivante**.

**À la fin** : la PR #158 est mergeable côté backend, quality gate Sonar PASSED, Elie merge lui-même quand il valide. Le DevOps prend la main pour les 8 items hors scope formalisés dans `devops-handoff.md` (cluster Kafka prod, NetworkPolicies, Doppler, certs, Production Kong, Pact provider verification CI job, GHCR cleanup, schemas-per-service Flyway).

---

## Workflow git détaillé pour chaque sous-étape

```bash
# 0. Pré-vérif état
git status --porcelain                       # vide ou .devcontainer/lock untracked OK
git rev-parse HEAD                            # 2aef8fe2 ou descendant

# 1. Faire les changements (Edit/Write)
# 2. Build local
cd backend && ./mvnw -B -DskipITs verify     # SUCCESS
# Si test add → vérifier la coverage de la classe ciblée :
awk -F, 'NR>1 && $3=="<ClassName>" {printf "L:%5.1f%%\n", $9*100/($8+$9)}' \
    services/<svc>-service/target/jacoco-report/jacoco.csv

# 3. Stage + commit
cd /workspace
git add -A backend/<paths-touched>
git commit -m "$(cat <<'EOF'
<type>(<scope>): <message ÉtapeN.M> (Étape N.M, <RELATED-IDs>)

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

**Fin de la spec quality gate post-migration.** Bonne exécution. 🚀
