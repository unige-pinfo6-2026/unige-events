# Specs SCRUM-164 — Restaurer les contraintes CHECK orphelines sur `events` (faculty, category, status)

> **Branche :** `feature/s7-schema-fixup-checks`
> **Base :** `origin/main`
> **Sprint :** S7 (28 avr.–2 mai 2026) — Dette technique / intégrité DB
> **Ticket Jira :** [SCRUM-164](https://pinfo-groupe6.atlassian.net/browse/SCRUM-164) (2 SP, assigné Elie)
> **Story Points :** 2
> **Règle d'or openapi-first :** **N/A** — la tâche n'expose aucun endpoint HTTP. Pas de modification de [openapi/openapi.yaml](openapi/openapi.yaml).

---

## Contexte

### Le problème

Le fichier [`backend/src/main/java/ch/unige/events/config/SchemaFixup.java`](backend/src/main/java/ch/unige/events/config/SchemaFixup.java) (commit `ed65826` du 16 avril 2026, « improve and fix skeleton ») contient depuis sa création **trois `DROP CONSTRAINT IF EXISTS` orphelins** :

```java
stmt.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS events_faculty_check");
stmt.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS events_category_check");
stmt.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS events_status_check");
```

Ces drops ont été ajoutés à l'origine pour nettoyer les contraintes CHECK générées automatiquement par Hibernate `update` mode après le renommage de valeurs d'enum (notamment sur `Faculty` qui est passée d'un jeu francophone — `LETTRES`, `DROIT`, `MEDECINE`, `SES`, `PSYCHOLOGIE`, `THEOLOGIE`, `GSI` — à un jeu anglophone : `LETTERS`, `LAW`, `MEDICINE`, `SOCIAL_SCIENCES`, `PSYCHOLOGY`, `THEOLOGY`, `GSEM`).

**Mais aucune contrainte de remplacement n'a jamais été ajoutée.** Sur tout environnement où `SchemaFixup` s'est exécuté au moins une fois, la base PostgreSQL n'enforce **plus aucune validation** sur les colonnes `events.faculty`, `events.category` et `events.status` : seul le contrat applicatif (Hibernate Validator côté DTO + `@Enumerated(EnumType.STRING)` côté entité) protège la cohérence.

### Le risque

Sans contrainte CHECK au niveau DB, des valeurs invalides peuvent être insérées par tout chemin qui contourne JPA :

- script de seed manuel (`psql`, `pgAdmin`, dump SQL) ;
- migration ad-hoc déployée par mégarde ;
- bug applicatif qui passe au travers de la validation DTO (un override de `Event.faculty` par String litteral, par exemple) ;
- réplication ou import depuis un environnement qui aurait son propre schéma divergent.

Quand Hibernate relit ces lignes via `@Enumerated(EnumType.STRING)`, il lève alors un `IllegalArgumentException` au mapping. Symptôme typique en prod : `500 Internal Server Error` sur des endpoints qui marchaient hier, **sans changement de code**, parce qu'une seule ligne corrompue casse l'hydratation.

C'est exactement le **problème inverse** de celui qui a été fixé pour `attendances_status_check` (cf. décision 14 ci-dessous) : là où la contrainte `attendances_status_check` était trop **restrictive** (bloquait `WAITLISTED` après l'ajout du statut), ici les contraintes `events_*_check` sont **absentes** (laissent passer n'importe quoi).

### Référence — d'où vient le ticket

Le finding a été soulevé en review **Copilot AI** sur la PR liée à [SCRUM-101](https://pinfo-groupe6.atlassian.net/browse/SCRUM-101) (US-07 / branche `feature/s5-attendees-list`). Le reviewer a flaggé la dérive ; après confirmation par `git blame` et `git show ed65826`, l'équipe a constaté que les drops sont pré-existants depuis le squelette et donc **hors scope** pour SCRUM-101 (qui traite de la liste des participants). Le ticket SCRUM-164 a été ouvert pour traiter le sujet en isolation.

### Pourquoi pas Flyway / Liquibase

[`backend/AGENTS.md`](backend/AGENTS.md) tranche explicitement : `quarkus.hibernate-orm.schema-management.strategy=update`, **pas de fichier SQL de migration**, les entités JPA sont la source de vérité. Le pattern `SchemaFixup` est l'échappatoire reconnue (et la seule) pour les manipulations DDL que Hibernate ne sait pas faire seul. Adopter Flyway aujourd'hui sortirait du scope de la tâche et obligerait à reprendre tous les SQL implicites du projet.

### Pourquoi un fix maintenant et pas plus tard

- La PR [`feature/s5-attendees-list`](https://github.com/unige-pinfo6-2026/unige-events) (commit `cf83098 — Reconcile obsolete CHECK constraints`) a déjà traité **un seul des quatre cas** (`attendances_status_check`), en posant le **pattern de référence** : méthode `reconcile()` publique, DDL statique, logger, test `@QuarkusTest`. SCRUM-164 doit étendre ce pattern aux trois `events_*_check` restants.
- La contrainte sur `attendances` n'est pas mergée sur `main` (la PR `feature/s5-attendees-list` est encore ouverte) — voir décision 13 pour la stratégie de divergence.
- Plus on tarde, plus le risque qu'une donnée invalide entre en base augmente.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Option A (recréer) — pas Option B (supprimer les drops)

**Décision.** Implémenter l'**Option A** du ticket : après chaque `DROP CONSTRAINT IF EXISTS`, ajouter un `ADD CONSTRAINT` avec les valeurs d'enum actuelles.

**Justification.** Option B (supprimer les drops devenus inutiles) ferait économiser 3 lignes de DDL mais laisserait les colonnes sans validation DB. Le ticket recommande explicitement Option A « pour préserver la validation, sauf si l'équipe décide d'adopter Flyway ». L'équipe n'a pas pris cette décision Flyway. De plus, Option B obligerait à vérifier sur staging et prod qu'aucune contrainte ne traîne avant suppression — du travail manuel non scriptable. Option A est idempotente et sûre dans tous les cas.

### 2. Source de vérité des valeurs d'enum : le code Java, pas le ticket

**Décision.** Les valeurs `IN (...)` dans les contraintes CHECK doivent **provenir du code Java actuel** ([`Faculty.java`](backend/src/main/java/ch/unige/events/entity/Faculty.java), [`EventCategory.java`](backend/src/main/java/ch/unige/events/entity/EventCategory.java), [`EventStatus.java`](backend/src/main/java/ch/unige/events/entity/EventStatus.java)), **PAS** des valeurs citées dans la description du ticket Jira ni dans le backlog [`backlog_s5_s10.md`](backend/docs/backlog_s5_s10.md).

**Divergence critique sur `Faculty`** :

| Source | Valeurs |
|---|---|
| Ticket Jira / backlog | `SCIENCES`, `LETTRES`, `DROIT`, `MEDECINE`, `SES`, `PSYCHOLOGIE`, `THEOLOGIE`, `FTI`, `GSI` |
| **Code Java actuel** ([Faculty.java:3-13](backend/src/main/java/ch/unige/events/entity/Faculty.java#L3-L13)) | `SCIENCES`, `MEDICINE`, `LETTERS`, `SOCIAL_SCIENCES`, `GSEM`, `LAW`, `THEOLOGY`, `PSYCHOLOGY`, `FTI` |

Le ticket reflète un état historique (avant le rename anglophone qui a justement créé le besoin de drop initial). Suivre les valeurs du ticket recréerait la contrainte avec les **anciennes** valeurs, et bloquerait tous les INSERTs courants (l'inverse strict du bug à fixer). **L'agent d'implémentation doit `cat` les trois fichiers d'enum avant de coder le DDL**, et utiliser leur contenu littéral.

**Pour les deux autres** :
- [`EventCategory.java:3-10`](backend/src/main/java/ch/unige/events/entity/EventCategory.java#L3-L10) : `ACADEMIC`, `SPORTS`, `CULTURAL`, `SOCIAL`, `CONFERENCE`, `OTHER` — concorde avec le backlog.
- [`EventStatus.java:3-7`](backend/src/main/java/ch/unige/events/entity/EventStatus.java#L3-L7) : `DRAFT`, `PUBLISHED`, `CANCELLED` — concorde avec le backlog.

### 3. `faculty` est nullable → `IS NULL OR faculty IN (...)`

**Décision.** La contrainte `events_faculty_check` doit autoriser `NULL` :

```sql
faculty IS NULL OR faculty IN ('SCIENCES','MEDICINE','LETTERS','SOCIAL_SCIENCES','GSEM','LAW','THEOLOGY','PSYCHOLOGY','FTI')
```

**Justification.** [`Event.faculty`](backend/src/main/java/ch/unige/events/entity/Event.java#L37-L39) est `@Enumerated(EnumType.STRING)` avec un simple `@Column(columnDefinition = "varchar(255)")` — **aucune annotation `@NotNull`** sur l'entité, et le champ est explicitement documenté nullable dans [`backend/docs/data-model.md`](backend/docs/data-model.md). Des events publiés en base ont aujourd'hui `faculty = NULL` (filtre `?facultyNone=true` du frontend). Une contrainte stricte `IN (...)` casserait l'application au démarrage du fixup (et serait silencieusement loggée en warning, mais resterait absente — voir décision 8).

### 4. `category` est non-nullable côté app → `category IN (...)` strict

**Décision.** La contrainte `events_category_check` doit être stricte (pas de `IS NULL OR`) :

```sql
category IN ('ACADEMIC','SPORTS','CULTURAL','SOCIAL','CONFERENCE','OTHER')
```

**Justification.** [`Event.category`](backend/src/main/java/ch/unige/events/entity/Event.java#L33-L35) n'a pas non plus de `@NotNull` côté entité — donc strictement parlant, la colonne est nullable au niveau DDL. **Mais le contrat applicatif est non-null** : [`CreateEventRequest.category`](backend/src/main/java/ch/unige/events/dto/CreateEventRequest.java) est `@NotNull` (cf. [`docs/data-model.md` ligne ~248](backend/docs/data-model.md)), et il n'existe **aucun chemin code** qui crée un `Event.category = null`. Toutes les rangées en base ont une valeur. Imposer la stricte `IN (...)` au niveau DB élève le contrat applicatif au rang de garantie DB — exactement l'objectif de SCRUM-164. Si une rangée historique avait `category = NULL`, l'`ADD CONSTRAINT` échouerait silencieusement (catch + log warn) et serait visible au démarrage — c'est la bonne sémantique de surface.

### 5. `status` est non-nullable côté app → `status IN (...)` strict

**Décision.** Identique au précédent :

```sql
status IN ('DRAFT','PUBLISHED','CANCELLED')
```

**Justification.** [`Event.status`](backend/src/main/java/ch/unige/events/entity/Event.java#L47-L49) est déclaré `public EventStatus status = EventStatus.DRAFT;` — initialisé à `DRAFT` à la construction de l'entité, jamais nullable en pratique. Le code appelant n'a aucun chemin pour mettre `status = null`. Symétrique à la décision 4.

### 6. DDL **statique** (string literal) — pas de génération via `Faculty.values()`

**Décision.** Les trois ADD CONSTRAINT sont des **constantes `static final String`** dans `SchemaFixup.java`. **Pas** de boucle qui génère le SQL à partir de `Arrays.stream(Faculty.values()).map(Enum::name)...`.

**Justification.** Trois raisons :
1. **Sécurité.** Les contraintes SQL ici ne passent par aucun pré-processeur Hibernate — un opérateur dynamique sur les noms d'enum n'introduit pas vraiment d'injection (les noms d'enum Java sont déjà des identifiants Java valides), mais SonarCloud est connu pour flagger toute concaténation `String` qui finit dans `Statement.execute(...)`. Le DDL statique est immédiatement reconnaissable comme « pas d'input utilisateur ».
2. **Cohérence avec le pattern existant.** [`SchemaFixup` après `cf83098`](https://github.com/unige-pinfo6-2026/unige-events/commit/cf83098534838df90a2d32469228ac987777b31d) utilise `static final String[] DROP_OBSOLETE_CONSTRAINTS` et `static final String RECREATE_ATTENDANCE_STATUS_CHECK` — DDL statique, signal d'intention. SCRUM-164 reproduit fidèlement ce pattern.
3. **Signal documentaire.** Toute future addition à un enum **forcera explicitement** une mise à jour de `SchemaFixup` (le test passera au rouge si la contrainte ne reflète plus l'enum) — c'est un signal souhaitable, pas une charge. Une génération dynamique cacherait cette dépendance.

### 7. Idempotence : `DROP IF EXISTS` puis `ADD CONSTRAINT` à chaque démarrage

**Décision.** Chaque colonne suit la séquence en deux étapes :

```sql
ALTER TABLE events DROP CONSTRAINT IF EXISTS events_faculty_check;
ALTER TABLE events ADD CONSTRAINT events_faculty_check CHECK (faculty IS NULL OR faculty IN (...));
```

Le `DROP IF EXISTS` rend l'opération idempotente : qu'une ancienne contrainte (avec les valeurs francophones) traîne encore, qu'une contrainte récente conforme soit déjà en place, ou qu'aucune contrainte n'existe — la séquence converge vers l'état attendu.

**Justification.** Pattern strictement identique à `cf83098` pour `attendances_status_check`. Permet un redémarrage répété sans erreur PostgreSQL `constraint already exists`. Le test d'idempotence (décision 12) verrouille ce comportement.

### 8. Best-effort sur l'erreur SQL : log + continuer, pas de fail au boot

**Décision.** L'exécution de `reconcile()` est encapsulée dans un `try / catch (SQLException)` qui logue en `WARN` et **continue**. **Pas** de re-throw, pas d'arrêt du démarrage Quarkus.

**Justification.**
- Pattern hérité de `cf83098` (et de la version originale `ed65826`).
- Si une donnée historique invalide bloque l'`ADD CONSTRAINT`, on veut surfaces le warning sans empêcher l'API de démarrer (sinon : prod down).
- Si la table `events` n'existe pas encore (très tôt dans l'ordre Quarkus DevServices, par exemple), le `ALTER TABLE` lève — non-fatal.
- L'observabilité reste suffisante : le log `WARN` apparaît dans les pods, et le test `@QuarkusTest` (décision 12) garantit qu'au moins en environnement test la contrainte est bien posée.

### 9. Logging : `INFO` au succès, `WARN` à l'échec

**Décision.** Un seul `LOG.info(...)` en fin de `reconcile()` qui résume les contraintes recréées. Un seul `LOG.warnf(e, "...")` dans le `catch`.

**Justification.** Pattern `cf83098` : un INFO récapitulatif (« Schema check constraints reconciled (events.faculty/category/status, attendances.status). »). Pas de log par DDL — bruit pour aucune valeur ajoutée. Le WARN propage `e` au logger pour que la stack trace soit visible si besoin.

### 10. Refactor `onStart` → `reconcile()` public — réutilisation par les tests

**Décision.** L'observer CDI `void onStart(@Observes StartupEvent ev)` délègue à `public void reconcile()`. La méthode `reconcile()` est appelable directement depuis un test `@QuarkusTest`.

**Justification.** Pattern `cf83098`. Permet d'écrire un test qui appelle `schemaFixup.reconcile()` deux fois et vérifie qu'aucune exception n'est levée (test d'idempotence, décision 12). Sans cette refacto, on ne pourrait tester que via `StartupEvent`, ce qui est bien plus lourd.

### 11. Pas de constructor injection — préserver le `@Inject` field du pattern

**Décision.** Conserver `@Inject DataSource dataSource;` en injection par champ. **Ne pas** refactorer en injection par constructeur.

**Justification.** [`backend/AGENTS.md`](backend/AGENTS.md) recommande l'injection par constructeur pour les `Resource` et `Service`. Mais `SchemaFixup` est un bean d'infrastructure (un `@ApplicationScoped` qui observe `StartupEvent`), pas une couche métier. La version `cf83098` garde explicitement l'injection par champ. Refactor opportuniste = bruit dans la PR + écart de scope. À traiter en issue séparée si un jour l'équipe décide d'unifier le style.

### 12. Tests — quatre cas obligatoires

**Décision.** Le fichier `backend/src/test/java/ch/unige/events/config/SchemaFixupTest.java` (existe déjà sur la branche `feature/s5-attendees-list` après `cf83098` — voir décision 13) doit couvrir **quatre cas** :

| Test | Cible | Mécanisme |
|---|---|---|
| `reconcile_isIdempotent_whenInvokedTwice` | Robustesse | Appelle `schemaFixup.reconcile()` 2× après le démarrage, asserte `assertDoesNotThrow` |
| `eventsFacultyCheck_existsAndAllowsAllEnumValues` | Présence + couverture des 9 valeurs `Faculty` | Lecture `pg_get_constraintdef(...)` + INSERT natif d'une ligne par valeur d'enum |
| `eventsFacultyCheck_rejectsInvalidValue` | Rejet d'une valeur hors enum | INSERT natif `('NOT_A_FACULTY')` doit lever `SQLException` (constraint violation) |
| Idem **`eventsCategoryCheck_*`** | 6 valeurs + 1 invalide | Idem |
| Idem **`eventsStatusCheck_*`** | 3 valeurs + 1 invalide | Idem |

**Justification.** Le pattern `cf83098` couvre déjà l'idempotence, l'existence et le persist `WAITLISTED` pour `attendances`. SCRUM-164 reproduit la triade « existe / accepte les valides / rejette les invalides » sur les trois nouvelles contraintes. **L'INSERT natif via `EntityManager.createNativeQuery` est obligatoire** : un `entityManager.persist(event)` JPA passerait par `@Enumerated(EnumType.STRING)` qui ne sait que sérialiser un enum Java — il ne peut pas tester le rejet d'une chaîne invalide. Le test natif simule le scénario réel (script SQL externe) qui justifie la contrainte.

### 13. Stratégie de base : partir de `main`, **pas** attendre `feature/s5-attendees-list`

**Décision.** SCRUM-164 part de `origin/main` et **dupliquerait** au besoin le pattern de `cf83098` (méthode `reconcile()` publique + logger + DDL statique + test). **Ne pas** attendre la merge de `feature/s5-attendees-list`.

**Justification.**
- `feature/s5-attendees-list` (PR ouverte) ajoute le `RECREATE_ATTENDANCE_STATUS_CHECK` et le `SchemaFixupTest`. Sa date de merge n'est pas garantie.
- Bloquer SCRUM-164 derrière elle ajouterait une dépendance forte de timing.
- Le diff `cf83098` est petit (≤ 100 lignes). Le reproduire sur `main` revient à : (a) refactorer `onStart` en `reconcile()` ; (b) ajouter le tableau `DROP_OBSOLETE_CONSTRAINTS` qui inclut **les 4 drops** (3 events + 1 attendances) ; (c) ajouter le tableau `RECREATE_CONSTRAINTS` avec les 4 ADD ; (d) créer `SchemaFixupTest.java`.
- **Conflit de merge attendu** sur `SchemaFixup.java` quand l'une ou l'autre des branches sera mergée en seconde. La résolution est triviale : conserver l'union des 4 ADD CONSTRAINT et l'union des tests. À documenter dans la PR (section « Dépendances / ordre de merge » du template).
- Si `feature/s5-attendees-list` merge **avant** SCRUM-164 : rebase de `feature/s7-schema-fixup-checks` sur `main` mis à jour ; le `RECREATE_ATTENDANCE_STATUS_CHECK` sera déjà en place ; on garde uniquement les 3 ADD `events_*_check` + les 3 nouveaux blocs de tests dans `SchemaFixupTest`.

**Implication pratique** : la PR SCRUM-164 inclut **les 4 ADD CONSTRAINT** (events × 3 + attendances × 1), pour que la PR soit fonctionnellement complète sur `main` même si `cf83098` n'est jamais mergé. Si `cf83098` merge avant, le rebase retire le `RECREATE_ATTENDANCE_STATUS_CHECK` et le test associé devenu redondant.

### 14. Pas d'ajout de `@Transactional`

**Décision.** `reconcile()` ne porte pas `@Transactional`. Les statements DDL sont exécutés directement via `DataSource.getConnection() → Statement.execute(...)`.

**Justification.** PostgreSQL exécute le DDL en auto-commit sur la connexion JDBC obtenue par `dataSource.getConnection()` — chaque `ALTER TABLE` est sa propre transaction. Ajouter `@Transactional` introduirait une transaction Hibernate qui n'a aucune utilité pour du DDL et risquerait des verrous de longue durée si la table est sous charge.

### 15. Pas d'OpenAPI — pas d'API surface

**Décision.** Aucune modification de [`openapi/openapi.yaml`](openapi/openapi.yaml). Aucune modification d'aucun `Resource` JAX-RS. Aucun nouvel endpoint, aucun nouveau DTO, aucun nouveau code d'erreur.

**Justification.** SCRUM-164 est strictement de la dette technique d'infrastructure DB. La règle d'or « openapi-first » de [`backend/AGENTS.md`](backend/AGENTS.md) ne s'applique pas. Le fait de l'expliciter dans la spec évite à un agent zélé d'aller toucher inutilement le contrat API.

### 16. Frontend non impacté

**Décision.** Aucune modification du frontend. `git diff --stat frontend/` doit être vide à la PR.

**Justification.** Pas de surface API, pas de nouveau type, pas de comportement runtime visible côté client. La seule conséquence externe est qu'un script qui aurait inséré une valeur invalide ne pourrait plus le faire — mais le frontend n'a pas ce chemin. Symétrique à la décision 12 de [`specs_scrum-XXX-user-profile-privacy.md`](specs_archives/specs_claude/specs_scrum-XXX-user-profile-privacy.md).

---

## Analyse de l'existant

### Ce qui existe déjà (à réutiliser)

| Élément | Fichier / ligne | Rôle |
|---|---|---|
| Bean `SchemaFixup` (drops uniquement) | [`SchemaFixup.java:17-32`](backend/src/main/java/ch/unige/events/config/SchemaFixup.java#L17-L32) | Fichier à modifier (refactor `onStart` → `reconcile()`, ajout des 3 ADD events + 1 ADD attendances) |
| Pattern `@Inject DataSource` field | [`SchemaFixup.java:19-20`](backend/src/main/java/ch/unige/events/config/SchemaFixup.java#L19-L20) | À conserver tel quel (cf. décision 11) |
| Observer `@Observes StartupEvent` | [`SchemaFixup.java:22`](backend/src/main/java/ch/unige/events/config/SchemaFixup.java#L22) | Conservé, délègue à `reconcile()` |
| Catch `SQLException` non-fatal | [`SchemaFixup.java:28-30`](backend/src/main/java/ch/unige/events/config/SchemaFixup.java#L28-L30) | Conservé, enrichi d'un `LOG.warnf` |
| Pattern `reconcile()` public + logger + DDL statique + test | `cf83098` (branche `feature/s5-attendees-list`, **non mergée**) | Référence à reproduire fidèlement (cf. décision 13) |
| Enum `Faculty` | [`Faculty.java:3-13`](backend/src/main/java/ch/unige/events/entity/Faculty.java#L3-L13) | Source de vérité — 9 valeurs (`SCIENCES, MEDICINE, LETTERS, SOCIAL_SCIENCES, GSEM, LAW, THEOLOGY, PSYCHOLOGY, FTI`) |
| Enum `EventCategory` | [`EventCategory.java:3-10`](backend/src/main/java/ch/unige/events/entity/EventCategory.java#L3-L10) | Source de vérité — 6 valeurs |
| Enum `EventStatus` | [`EventStatus.java:3-7`](backend/src/main/java/ch/unige/events/entity/EventStatus.java#L3-L7) | Source de vérité — 3 valeurs |
| Annotations colonnes events | [`Event.java:33-49`](backend/src/main/java/ch/unige/events/entity/Event.java#L33-L49) | Confirme `faculty` nullable, `category`/`status` non-nullable côté contrat applicatif |
| Section « Gestion du schéma » de la doc | [`backend/docs/data-model.md` ligne ~360](backend/docs/data-model.md) | Section à enrichir d'une sous-section « Réconciliation des contraintes CHECK — `SchemaFixup` » |
| Section « Sprint 7 » de la doc | [`backend/docs/sprint-context.md` ligne 164](backend/docs/sprint-context.md#L164) | À enrichir d'une entrée SCRUM-164 |

### Ce qui est à modifier

| Fichier | Modification |
|---|---|
| [`backend/src/main/java/ch/unige/events/config/SchemaFixup.java`](backend/src/main/java/ch/unige/events/config/SchemaFixup.java) | Refacto `onStart` → `reconcile()` public, ajout `Logger`, ajout 4 ADD CONSTRAINT statiques, ajout du loop, log INFO/WARN |
| [`backend/src/test/java/ch/unige/events/config/SchemaFixupTest.java`](backend/src/test/java/ch/unige/events/config/SchemaFixupTest.java) | **Création** — 9 tests minimum (idempotence + 8 cas constraint × {present, accept-valid, reject-invalid}) |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Ajout sous-section « Réconciliation des contraintes CHECK — `SchemaFixup` » sous la section « Gestion du schéma » |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Ajout entrée Sprint 7 SCRUM-164 |

### Ce qui n'est PAS dans le scope

- ❌ Pas de migration Flyway / Liquibase.
- ❌ Pas de modification d'OpenAPI.
- ❌ Pas de modification d'aucun `Resource`, `Service`, DTO, entité métier.
- ❌ Pas d'ajout de champ à une entité existante.
- ❌ Pas de modification frontend.
- ❌ Pas de refactor de `SchemaFixup` en injection par constructeur.
- ❌ Pas d'ajout de `@Transactional` sur `reconcile()`.
- ❌ Pas de génération dynamique du DDL à partir de `Enum.values()`.
- ❌ Pas de centralisation de la liste des enums dans une classe « `SchemaConstraints` » dédiée — la duplication 1× entre l'enum Java et la string SQL est volontaire (signal documentaire, cf. décision 6).
- ❌ Pas de nouveau code d'erreur ni de ré-écriture du `catch (SQLException)` en `RuntimeException` — le best-effort reste.
- ❌ Pas de log par DDL — un seul INFO récapitulatif et un seul WARN.
- ❌ Pas d'élargissement à d'autres tables/colonnes (uniquement `events.faculty/category/status` + `attendances.status`).
- ❌ Pas de modification de `application.properties` ou `pom.xml`.

---

## Étape 0 — Vérifier les enums avant de coder

**OBLIGATOIRE** avant la moindre ligne de DDL. La spec se base sur l'état observé au moment de la rédaction ; l'agent doit confirmer que rien n'a bougé entre-temps :

```bash
cat backend/src/main/java/ch/unige/events/entity/Faculty.java
cat backend/src/main/java/ch/unige/events/entity/EventCategory.java
cat backend/src/main/java/ch/unige/events/entity/EventStatus.java
```

Attendu (à transcrire littéralement dans le DDL) :

```
Faculty       : SCIENCES, MEDICINE, LETTERS, SOCIAL_SCIENCES, GSEM, LAW, THEOLOGY, PSYCHOLOGY, FTI
EventCategory : ACADEMIC, SPORTS, CULTURAL, SOCIAL, CONFERENCE, OTHER
EventStatus   : DRAFT, PUBLISHED, CANCELLED
```

Si l'un des trois fichiers diverge de cette liste, **arrêter et demander confirmation** avant d'écrire le DDL : un enum a été muté entre la spec et l'implémentation, et SCRUM-164 doit refléter l'état HEAD, pas l'état de la spec.

---

## Étape 1 — `SchemaFixup.java`

**Fichier :** [`backend/src/main/java/ch/unige/events/config/SchemaFixup.java`](backend/src/main/java/ch/unige/events/config/SchemaFixup.java)

### 1.1 — Remplacer le contenu complet

**AVANT** (32 lignes, drops uniquement) :

```java
package ch.unige.events.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import javax.sql.DataSource;
import java.sql.SQLException;

/**
 * Drops stale CHECK constraints left behind by Hibernate's "update" strategy
 * after enum values were renamed. Hibernate adds constraints for
 * {@code @Enumerated(STRING)} columns but never drops them when enum values
 * change, causing inserts to fail against the old value list.
 */
@ApplicationScoped
public class SchemaFixup {

    @Inject
    DataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        try (var conn = dataSource.getConnection();
             var stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS events_faculty_check");
            stmt.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS events_category_check");
            stmt.execute("ALTER TABLE events DROP CONSTRAINT IF EXISTS events_status_check");
        } catch (SQLException e) {
            // Non-fatal: constraint may not exist on fresh databases
        }
    }
}
```

**APRÈS** :

```java
package ch.unige.events.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Reconciles obsolete CHECK constraints left behind by Hibernate's
 * {@code update} schema-management strategy after enum values change.
 *
 * <p>Hibernate adds CHECK constraints on the initial table creation for
 * columns mapped with {@code @Enumerated(STRING)}, but never updates them when
 * enum values are added or renamed afterwards. As a result, an INSERT with a
 * new enum value can fail at flush time with a {@code *_check} violation on
 * databases provisioned before the value was introduced — and conversely, an
 * INSERT with a removed enum value can pass undetected when the obsolete
 * constraint has been dropped without replacement.
 *
 * <p>This class drops obsolete constraints (idempotent) and recreates the
 * canonical {@code events_*_check} and {@code attendances_status_check}
 * constraints with the current enum values to keep DB-level validation in
 * place. Can be removed once the project adopts a real migration tool
 * (Flyway / Liquibase).
 *
 * <p>Adding a new value to {@link ch.unige.events.entity.Faculty},
 * {@link ch.unige.events.entity.EventCategory},
 * {@link ch.unige.events.entity.EventStatus} or
 * {@link ch.unige.events.entity.AttendanceStatus} <strong>requires</strong> a
 * matching update of the constants below — the test suite will fail otherwise.
 */
@ApplicationScoped
public class SchemaFixup {

    private static final Logger LOG = Logger.getLogger(SchemaFixup.class);

    /**
     * Static DDL statements only — never concatenate user input here.
     * Naming follows PostgreSQL's auto-generated convention
     * ({@code <table>_<column>_check}).
     */
    static final String[] DROP_OBSOLETE_CONSTRAINTS = {
            "ALTER TABLE events DROP CONSTRAINT IF EXISTS events_faculty_check",
            "ALTER TABLE events DROP CONSTRAINT IF EXISTS events_category_check",
            "ALTER TABLE events DROP CONSTRAINT IF EXISTS events_status_check",
            "ALTER TABLE attendances DROP CONSTRAINT IF EXISTS attendances_status_check",
    };

    static final String RECREATE_EVENTS_FACULTY_CHECK =
            "ALTER TABLE events ADD CONSTRAINT events_faculty_check "
                    + "CHECK (faculty IS NULL OR faculty IN ("
                    + "'SCIENCES','MEDICINE','LETTERS','SOCIAL_SCIENCES',"
                    + "'GSEM','LAW','THEOLOGY','PSYCHOLOGY','FTI'))";

    static final String RECREATE_EVENTS_CATEGORY_CHECK =
            "ALTER TABLE events ADD CONSTRAINT events_category_check "
                    + "CHECK (category IN ("
                    + "'ACADEMIC','SPORTS','CULTURAL','SOCIAL','CONFERENCE','OTHER'))";

    static final String RECREATE_EVENTS_STATUS_CHECK =
            "ALTER TABLE events ADD CONSTRAINT events_status_check "
                    + "CHECK (status IN ('DRAFT','PUBLISHED','CANCELLED'))";

    static final String RECREATE_ATTENDANCE_STATUS_CHECK =
            "ALTER TABLE attendances ADD CONSTRAINT attendances_status_check "
                    + "CHECK (status IN ('ATTENDING','WAITLISTED'))";

    static final String[] RECREATE_CONSTRAINTS = {
            RECREATE_EVENTS_FACULTY_CHECK,
            RECREATE_EVENTS_CATEGORY_CHECK,
            RECREATE_EVENTS_STATUS_CHECK,
            RECREATE_ATTENDANCE_STATUS_CHECK,
    };

    @Inject
    DataSource dataSource;

    void onStart(@Observes StartupEvent ev) {
        reconcile();
    }

    /**
     * Drops obsolete CHECK constraints and recreates the canonical
     * {@code events_*_check} and {@code attendances_status_check} constraints
     * with the current enum values. Idempotent: safe to call multiple times.
     */
    public void reconcile() {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String ddl : DROP_OBSOLETE_CONSTRAINTS) {
                stmt.execute(ddl);
            }
            for (String ddl : RECREATE_CONSTRAINTS) {
                stmt.execute(ddl);
            }
            LOG.info("Schema check constraints reconciled "
                    + "(events.faculty/category/status, attendances.status).");
        } catch (SQLException e) {
            // Non-fatal: log and continue. Tables may not yet exist on a brand-new DB
            // when this hook runs in some Quarkus startup orderings, or pre-existing
            // invalid rows may block ADD CONSTRAINT — both cases are observable via WARN.
            LOG.warnf(e, "Schema reconciliation skipped: %s", e.getMessage());
        }
    }
}
```

### 1.2 — Points à respecter

- **Ordre des constantes** : un tableau `DROP_OBSOLETE_CONSTRAINTS` (les 4 drops d'abord), quatre `static final String RECREATE_*` (un par contrainte, pour la lisibilité et la réutilisation côté tests), puis un tableau `RECREATE_CONSTRAINTS` qui les regroupe.
- **Visibilité `static final` package-private** (`static final String`, sans `private`) : permet aux tests de la même classe-package (`ch.unige.events.config.SchemaFixupTest`) d'y accéder sans réflexion.
- **`Connection` et `Statement` typés explicitement** (pas de `var`) : aligné sur le style de `cf83098` et plus lisible pour qui n'a pas l'IDE.
- **Pas de `try-with-resources` ouvert pour le `ResultSet`** ici (on n'en utilise pas dans `reconcile()`) — pas de fuite à craindre.
- **Pas de `throws SQLException`** : la méthode encapsule le catch.
- **Le commentaire Javadoc de classe énumère explicitement les 4 enums dépendants** — ce sera la première chose qu'un dev verra quand il ajoutera une valeur d'enum, et le test failera s'il oublie de mettre `SchemaFixup` à jour.
- Conserver `@ApplicationScoped` et `@Inject DataSource dataSource;` — pas de constructor injection (cf. décision 11).

---

## Étape 2 — `SchemaFixupTest.java` (création)

**Fichier :** [`backend/src/test/java/ch/unige/events/config/SchemaFixupTest.java`](backend/src/test/java/ch/unige/events/config/SchemaFixupTest.java) — **à créer**.

### 2.1 — Squelette complet

```java
package ch.unige.events.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class SchemaFixupTest {

    @Inject
    SchemaFixup schemaFixup;

    @Inject
    DataSource dataSource;

    @Inject
    EntityManager entityManager;

    // --- Idempotence ---

    @Test
    void reconcile_isIdempotent_whenInvokedTwice() {
        // Already invoked once at startup; calling reconcile() twice more must not throw.
        assertDoesNotThrow(schemaFixup::reconcile);
        assertDoesNotThrow(schemaFixup::reconcile);
    }

    // --- events_faculty_check ---

    @Test
    void eventsFacultyCheck_existsAfterStartup() throws SQLException {
        assertConstraintDefContains("events", "events_faculty_check",
                "SCIENCES", "MEDICINE", "LETTERS", "SOCIAL_SCIENCES",
                "GSEM", "LAW", "THEOLOGY", "PSYCHOLOGY", "FTI");
    }

    @Test
    void eventsFacultyCheck_acceptsNull() throws SQLException {
        // faculty is nullable — the constraint must not block NULL.
        assertDoesNotThrow(() -> insertNativeEvent("category", "ACADEMIC",
                "status", "DRAFT", "faculty", null));
    }

    @Test
    void eventsFacultyCheck_rejectsInvalidValue() {
        assertCheckViolation(() -> insertNativeEvent(
                "category", "ACADEMIC", "status", "DRAFT", "faculty", "NOT_A_FACULTY"));
    }

    // --- events_category_check ---

    @Test
    void eventsCategoryCheck_existsAfterStartup() throws SQLException {
        assertConstraintDefContains("events", "events_category_check",
                "ACADEMIC", "SPORTS", "CULTURAL", "SOCIAL", "CONFERENCE", "OTHER");
    }

    @Test
    void eventsCategoryCheck_rejectsInvalidValue() {
        assertCheckViolation(() -> insertNativeEvent(
                "category", "NOT_A_CATEGORY", "status", "DRAFT", "faculty", null));
    }

    // --- events_status_check ---

    @Test
    void eventsStatusCheck_existsAfterStartup() throws SQLException {
        assertConstraintDefContains("events", "events_status_check",
                "DRAFT", "PUBLISHED", "CANCELLED");
    }

    @Test
    void eventsStatusCheck_rejectsInvalidValue() {
        assertCheckViolation(() -> insertNativeEvent(
                "category", "ACADEMIC", "status", "ARCHIVED", "faculty", null));
    }

    // --- attendances_status_check (couvert également côté feature/s5-attendees-list ;
    //     dupliqué ici tant que cf83098 n'est pas mergé sur main, à retirer au rebase
    //     si la PR a été mergée avant celle-ci — cf. spec décision 13) ---

    @Test
    void attendancesStatusCheck_existsAfterStartup() throws SQLException {
        assertConstraintDefContains("attendances", "attendances_status_check",
                "ATTENDING", "WAITLISTED");
    }

    // --- Helpers ---

    /**
     * Asserts that the given CHECK constraint exists on the given table and
     * its definition contains every expected enum value as a literal.
     */
    private void assertConstraintDefContains(String table, String constraint, String... expectedValues)
            throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT pg_get_constraintdef(c.oid) "
                             + "FROM pg_constraint c "
                             + "JOIN pg_class t ON c.conrelid = t.oid "
                             + "WHERE t.relname = '" + table + "' "
                             + "AND c.conname = '" + constraint + "'")) {
            assertTrue(rs.next(),
                    constraint + " must exist on table " + table + " after startup");
            String def = rs.getString(1);
            assertNotNull(def);
            for (String value : expectedValues) {
                assertTrue(def.contains(value),
                        "Constraint " + constraint + " must allow " + value + " — got: " + def);
            }
        }
    }

    /**
     * Inserts an Event row via native SQL bypassing JPA validation.
     * The caller passes column/value pairs; missing required columns are
     * filled with sensible defaults.
     */
    private void insertNativeEvent(Object... colVals) throws SQLException {
        String category = "ACADEMIC";
        String status = "DRAFT";
        String faculty = null;
        for (int i = 0; i < colVals.length; i += 2) {
            String col = (String) colVals[i];
            Object val = colVals[i + 1];
            switch (col) {
                case "category" -> category = (String) val;
                case "status" -> status = (String) val;
                case "faculty" -> faculty = (String) val;
                default -> throw new IllegalArgumentException("Unsupported column: " + col);
            }
        }
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            String facultySql = (faculty == null) ? "NULL" : "'" + faculty + "'";
            stmt.execute(
                    "INSERT INTO events (id, title, location, start_date, end_date, "
                            + "category, status, faculty, all_day, created_at, updated_at) "
                            + "VALUES (nextval('events_seq'), 'native-test', 'Uni Mail', "
                            + "now() + interval '1 day', now() + interval '2 day', "
                            + "'" + category + "', '" + status + "', " + facultySql + ", false, now(), now())");
        }
    }

    /**
     * Asserts that the given runnable raises a SQLException whose chain mentions
     * a check constraint violation. PostgreSQL surfaces this as "violates check
     * constraint" with SQLState '23514'.
     */
    private void assertCheckViolation(SqlRunnable runnable) {
        SQLException raised = assertThrows(SQLException.class, runnable::run);
        SQLException cause = raised;
        while (cause != null) {
            if ("23514".equals(cause.getSQLState())
                    || (cause.getMessage() != null && cause.getMessage().contains("check constraint"))) {
                return;
            }
            cause = cause.getNextException();
        }
        throw new AssertionError("Expected check constraint violation, got: " + raised, raised);
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }
}
```

### 2.2 — Points à respecter

- **`@QuarkusTest`** garantit que `SchemaFixup.onStart` a été invoqué avant le premier test (le `StartupEvent` est observé au boot du contexte CDI).
- **Pas de `@TestTransaction` sur les INSERT natifs** : la séquence `events_seq` est partagée — les tests doivent rester compatibles entre eux. PostgreSQL accepte parfaitement les rows orphelines créées par `INSERT` natif sans rollback, et la table de test est éphémère (DevServices).
- **L'INSERT natif fournit toutes les colonnes `NOT NULL`** : `id` (via `nextval('events_seq')`), `title`, `location`, `start_date`, `end_date`, `category`, `status`, `all_day`, `created_at`, `updated_at`. Les autres champs (description, banner_url, capacity, website_url, contact_email, registration_deadline, share_code, creator_id) sont nullable au niveau DB et omis volontairement.
- **`events_seq`** est le nom de séquence par défaut généré par Hibernate pour `PanacheEntity` (PK Long). Si Quarkus utilise `hibernate_sequence` ou une autre convention au moment de l'implémentation, vérifier via `\ds` dans psql — le nom doit être strictement aligné.
- **`SQLState '23514'`** est la valeur PostgreSQL universelle pour `check_violation`. Le helper `assertCheckViolation` traverse `getNextException()` parce que les drivers JDBC chaînent parfois les exceptions.
- **Pas d'utilisation de Bean Validation côté test** : on insère via SQL natif **précisément** pour bypasser tous les filtres applicatifs et tester la contrainte DB en isolation.

### 2.3 — Note sur le test `attendances_status_check`

Si `cf83098` a été mergé avant la PR SCRUM-164 :
- Le test `attendancesStatusCheck_existsAfterStartup` est **redondant** avec ceux déjà introduits par `cf83098` — le retirer pendant le rebase.
- Garder uniquement les 7 tests events × {idempotence + 3 constraints × 2-3 cas chacun}.

Si `cf83098` n'est pas encore mergé (cas attendu au moment de l'ouverture de la PR SCRUM-164) :
- Le test reste — il documente le comportement attendu si SCRUM-164 merge en premier.
- À la merge de la PR `feature/s5-attendees-list` (qui tomberait après SCRUM-164), un conflit léger sur `SchemaFixupTest.java` est attendu : merger les tests des deux côtés.

---

## Étape 3 — Documentation

### 3.1 — [`backend/docs/data-model.md`](backend/docs/data-model.md)

**Localisation :** sous la section « Gestion du schéma » qui termine le fichier (vers la ligne 360+, dernière section avant EOF).

**À ajouter** (sous-section finale de « Gestion du schéma ») :

```markdown
### Réconciliation des contraintes CHECK — `SchemaFixup`

Le mode `update` de Hibernate **n'altère jamais** les contraintes CHECK générées
pour les colonnes mappées en `@Enumerated(STRING)`. Quand une nouvelle valeur
est ajoutée à un enum — ou qu'une valeur existante est renommée — les bases
provisionnées avant le changement conservent l'ancienne contrainte et :

- soit rejettent les INSERT avec la nouvelle valeur (cas `WAITLISTED` sur
  `attendances_status_check`) ;
- soit acceptent silencieusement n'importe quoi si la contrainte a été droppée
  sans remplaçant (cas `events_faculty_check`, `events_category_check`,
  `events_status_check` après le rename anglophone du `Faculty` enum).

`ch.unige.events.config.SchemaFixup` est un bean `@ApplicationScoped` qui
s'exécute sur `StartupEvent` et :

- supprime les contraintes obsolètes avec `DROP CONSTRAINT IF EXISTS` —
  idempotent ;
- recrée les contraintes canoniques avec les valeurs d'enum **actuelles** :

  | Contrainte | Définition (extrait) |
  |---|---|
  | `events_faculty_check` | `faculty IS NULL OR faculty IN ('SCIENCES','MEDICINE','LETTERS','SOCIAL_SCIENCES','GSEM','LAW','THEOLOGY','PSYCHOLOGY','FTI')` |
  | `events_category_check` | `category IN ('ACADEMIC','SPORTS','CULTURAL','SOCIAL','CONFERENCE','OTHER')` |
  | `events_status_check` | `status IN ('DRAFT','PUBLISHED','CANCELLED')` |
  | `attendances_status_check` | `status IN ('ATTENDING','WAITLISTED')` |

Le DDL est **statique** (jamais concaténé avec des entrées utilisateur) — ni
SQL injection ni surprise via réflexion. **Toute future addition à un enum
doit s'accompagner d'une mise à jour des constantes `RECREATE_*` dans
`SchemaFixup`** ; le test `SchemaFixupTest` failera sinon.

L'erreur `SQLException` est journalisée en `WARN` et n'arrête pas le démarrage
— sur une base fraîche où la table n'existe pas encore au moment de l'observer
`StartupEvent`, l'`ALTER TABLE` est non-fatal.

À retirer si le projet adopte un outil de migration (Flyway / Liquibase).

> Voir [`specs_archives/specs_claude/specs_scrum-164.md`](specs_archives/specs_claude/specs_scrum-164.md).
```

### 3.2 — [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md)

**Localisation :** dans la section « Sprint 7 (planifié : 24 avril – 8 mai 2025) » (ligne ~164), ajouter un item à la liste à puces existante (en première position pour refléter la dette technique fixée). Le format suit l'existant (description courte + résumé technique en dessous).

**À ajouter** :

```markdown
- [x] **SCRUM-164** — `SchemaFixup` : restauration des contraintes CHECK orphelines
  sur `events.faculty`, `events.category`, `events.status`. Refactor de la classe
  en `reconcile()` public + DDL statique + logger + test `@QuarkusTest` couvrant
  idempotence, présence de la contrainte (lecture `pg_get_constraintdef`) et
  rejet d'une valeur invalide via INSERT natif. La PR inclut également la
  contrainte `attendances_status_check` (`ATTENDING`, `WAITLISTED`) déjà ajoutée
  côté `feature/s5-attendees-list` (cf83098), pour rendre la PR autonome ; à
  rebase trivial selon l'ordre de merge des deux branches.
```

### 3.3 — Pas de modification de `api-contract.md`

Aucun endpoint ne change. Pas de touche à [`backend/docs/api-contract.md`](backend/docs/api-contract.md).

---

## Edge cases à traiter explicitement

| Cas | Comportement attendu | Couvert par |
|---|---|---|
| Démarrage sur une DB fraîche (tables `events`/`attendances` n'existent pas encore) | `SQLException` capturée → `LOG.warnf` → démarrage continue | `try/catch` dans `reconcile()` (cf. décision 8) |
| Démarrage sur une DB où les contraintes existent déjà | `DROP IF EXISTS` les supprime sans erreur, `ADD CONSTRAINT` les recrée — idempotent | `DROP_OBSOLETE_CONSTRAINTS` ordering |
| Démarrage sur une DB où une rangée a `category = NULL` | `ADD CONSTRAINT events_category_check CHECK (category IN ...)` lève une violation, capturée → `LOG.warnf` → démarrage continue (la contrainte n'est PAS posée, surfacée via le warn) | `try/catch` + log WARN avec stack |
| `reconcile()` appelé manuellement après le démarrage | Idempotent, pas d'exception | Test `reconcile_isIdempotent_whenInvokedTwice` |
| Ajout d'une nouvelle valeur à `Faculty` (ex. `EXCHANGE`) sans mise à jour de `RECREATE_EVENTS_FACULTY_CHECK` | INSERT JPA d'un Event avec `faculty = EXCHANGE` → flush → `SQLException 23514` (rejet par la contrainte) | Surface au runtime ; pas de test dédié (cas dégénéré nominal) |
| Ajout d'une nouvelle valeur à `Faculty` AVEC mise à jour de la constante mais pas du test | Le test `eventsFacultyCheck_existsAfterStartup` fail si l'agent a oublié d'aligner la liste des valeurs attendues — signal correct | Test paramétré sur `expectedValues` |
| INSERT natif d'une valeur inconnue par un script externe | `SQLException` `23514` au moment de l'INSERT (pas au flush — la contrainte est DB-level) | Tests `*_rejectsInvalidValue` |
| `events_seq` introuvable (renommée par Hibernate ou inexistante) | Le helper `insertNativeEvent` échoue avec `SQLException` non liée à la contrainte → test rouge sans ambiguïté | À investiguer manuellement si ça arrive (probablement un changement de version Quarkus) |
| Pré-existant : la PR `feature/s5-attendees-list` est mergée avant SCRUM-164 | Conflit `SchemaFixup.java` au rebase — résolution triviale (garder l'union des constantes/test). Le test `attendancesStatusCheck_existsAfterStartup` est doublé → en garder un seul. | Procédure de rebase documentée dans la PR (cf. décision 13) |
| Pré-existant : SCRUM-164 est mergée avant la PR `feature/s5-attendees-list` | Quand `feature/s5-attendees-list` rebase, conflit symétrique — `cf83098` doit retirer ce qui a été mergé par SCRUM-164. | À traiter par l'auteur de l'autre PR au moment du rebase ; pas de scope SCRUM-164 |

---

## Critères d'acceptation (repris du ticket Jira SCRUM-164)

- [x] **Décision prise et documentée entre options A et B** → Option A (cf. décision 1).
- [ ] **Si option A : les trois contraintes sont recréées avec les valeurs d'enum actuelles, idempotent, testées comme attendances_status_check** → 4 contraintes recréées (`events_faculty/category/status_check` + `attendances_status_check`), idempotence testée par `reconcile_isIdempotent_whenInvokedTwice`.
- [ ] **Test d'intégration : insérer une valeur invalide sur l'une des trois colonnes échoue (option A)** → `eventsFacultyCheck_rejectsInvalidValue`, `eventsCategoryCheck_rejectsInvalidValue`, `eventsStatusCheck_rejectsInvalidValue`.
- [ ] **`backend/docs/data-model.md` section « Réconciliation des contraintes CHECK » mise à jour pour refléter l'état final** → Étape 3.1.

---

## Conventions du projet à respecter

- **Règle d'or `openapi-first`** : **N/A** (pas de surface API).
- **camelCase partout** dans le code Java et la doc Markdown — pas de snake_case dans les identifiants Java (les noms de colonnes SQL `start_date`/`end_date`/`created_at`/`updated_at` sont strictement délégués à Hibernate `CamelCaseToUnderscoresNamingStrategy` et n'apparaissent qu'en SQL natif dans `insertNativeEvent`).
- **Pas de préfixe `is`** sur des booléens d'entité (irrelevant ici — pas d'entité touchée).
- **Pas de migration Flyway** — `SchemaFixup` est l'échappatoire reconnue (cf. [`backend/AGENTS.md`](backend/AGENTS.md)).
- **Pas de logique métier ailleurs que dans un `Service`** — irrelevant ici (`SchemaFixup` est de l'infra, pas du métier).
- **SonarCloud** : ≥ 80 % couverture sur le nouveau code, ≤ 3 % duplication, ratings A. Le DDL statique évite tout flag « SQL injection ».
- **Doc mise à jour dans le même commit** que le code correspondant (règle `backend/AGENTS.md`).
- **Commits atomiques** : `feat(scrum-164): …`, `test(scrum-164): …`, `docs(scrum-164): …` (combinables si le diff est petit — à juger).

---

## Interdits stricts

- ❌ Pas de migration Flyway / Liquibase ni de fichier SQL de migration.
- ❌ Pas de modification d'OpenAPI, d'aucun `Resource`, `Service`, DTO, entité, hook frontend.
- ❌ Pas de `@Transactional` sur `reconcile()`.
- ❌ Pas de constructor injection — préserver `@Inject DataSource dataSource;`.
- ❌ Pas de génération du DDL via `Faculty.values()` — DDL statique uniquement.
- ❌ Pas de centralisation des listes d'enums dans une classe « `SchemaConstraints` » dédiée.
- ❌ Pas de `throws SQLException` sur `reconcile()` — encapsuler le catch.
- ❌ Pas de `RuntimeException` à la place du `LOG.warnf` — le best-effort reste.
- ❌ Pas de log par DDL — un seul INFO récapitulatif et un seul WARN.
- ❌ Pas d'élargissement à d'autres tables/colonnes que `events.faculty`, `events.category`, `events.status`, `attendances.status`.
- ❌ Pas de modification de `application.properties`, `pom.xml`, `Dockerfile`.
- ❌ Pas de retrait de l'observer `onStart` (la production a besoin de la réconciliation au boot).
- ❌ Pas de snake_case, pas de `any`, pas de TODO commenté.
- ❌ Pas de copie/duplication des valeurs d'enum dans une 2e source — les constantes `RECREATE_*` sont la **seule** source SQL ; les enums Java restent la source applicative.
- ❌ Pas de `@TestTransaction` sur les INSERT natifs (reset de séquence non garanti).
- ❌ Pas de mock de `DataSource` ou `EntityManager` dans `SchemaFixupTest` — DevServices PostgreSQL réel obligatoire.

---

## Résumé des fichiers touchés

| Fichier | Action |
|---|---|
| [`backend/src/main/java/ch/unige/events/config/SchemaFixup.java`](backend/src/main/java/ch/unige/events/config/SchemaFixup.java) | Modifier — refacto `onStart` → `reconcile()`, ajout du logger, des constantes DDL et du loop `RECREATE` |
| [`backend/src/test/java/ch/unige/events/config/SchemaFixupTest.java`](backend/src/test/java/ch/unige/events/config/SchemaFixupTest.java) | **Créer** — 9 tests couvrant idempotence, présence et acceptation/rejet sur les 4 contraintes |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Modifier — ajout sous-section « Réconciliation des contraintes CHECK — `SchemaFixup` » sous la section « Gestion du schéma » |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Modifier — ajout entrée Sprint 7 SCRUM-164 |

**Total :** 3 fichiers modifiés, 1 créé, **0 fichier de migration SQL**, **0 fichier OpenAPI**, **0 fichier frontend**.

---

## Branche et PR

### Branche

`feature/s7-schema-fixup-checks`, basée sur `origin/main` :

```bash
git fetch origin
git checkout -b feature/s7-schema-fixup-checks origin/main --no-track
```

⚠️ **`--no-track` est OBLIGATOIRE.** Sans ce flag, la branche traque `origin/main` et `git push` envoie les commits directement sur main (incident documenté sur ISSUE-92, cf. commit de revert `9c2e6d4` sur main, repris par toutes les specs récentes). Le `-u` viendra au premier push pour set-up le bon upstream :

```bash
git push -u origin feature/s7-schema-fixup-checks
```

### PR

- **Base :** `main`.
- **Titre :** `feat(scrum-164): restore CHECK constraints on events.faculty / category / status`
  - `feat` impose un scope `scrum-164` en minuscules — validé par [`/.github/workflows/pr-title-check.yml`](.github/workflows/pr-title-check.yml).
- **Description** (calquée sur [`.github/pull_request_template.md`](.github/pull_request_template.md)) :

  ```markdown
  ## Résumé

  **SCRUM-164** — Restauration des trois contraintes CHECK orphelines sur la table
  `events` (`faculty`, `category`, `status`). Le pattern `SchemaFixup` est étendu
  pour recréer les contraintes après le `DROP IF EXISTS` initial — DDL statique,
  idempotent, couvert par tests. Inclut également la contrainte
  `attendances_status_check` (`ATTENDING`, `WAITLISTED`) pour rendre la PR
  autonome face à `feature/s5-attendees-list` non encore mergée.

  ## Why / Motivation

  Le ticket SCRUM-164 documente que les drops orphelins introduits dans `ed65826`
  laissent les colonnes events sans validation DB-level depuis avril 2026. Une
  insertion via script ou bug applicatif peut faire passer une valeur invalide
  qui casse Hibernate au mapping (`IllegalArgumentException` → 500).

  ## Changements

  ### Backend
  - Refacto `SchemaFixup.onStart` en `public reconcile()` réutilisable par les tests
    (`backend/src/main/java/ch/unige/events/config/SchemaFixup.java`).
  - Ajout de constantes `static final String RECREATE_EVENTS_*` + logger INFO/WARN.
  - Création de `backend/src/test/java/ch/unige/events/config/SchemaFixupTest.java`
    (9 tests : idempotence, présence des 4 contraintes via `pg_get_constraintdef`,
    rejet d'une valeur invalide via INSERT natif).

  ### Documentation
  - `backend/docs/data-model.md` : nouvelle sous-section « Réconciliation des
    contraintes CHECK — `SchemaFixup` » avec table récapitulative.
  - `backend/docs/sprint-context.md` : entrée Sprint 7 SCRUM-164.

  ## Tests

  - `./mvnw verify` vert.
  - `SchemaFixupTest` couvre les 4 contraintes — idempotence, présence
    (lecture `pg_get_constraintdef`), acceptation des valeurs valides,
    rejet d'une valeur invalide (`SQLState '23514'`).

  ## Test plan

  - [ ] `./mvnw verify` localement vert.
  - [ ] Couverture JaCoCo ≥ 80 % sur `SchemaFixup`.
  - [ ] `SchemaFixupTest` rouge si on commente une constante `RECREATE_*`.
  - [ ] `SchemaFixupTest` rouge si on ajoute une valeur à un enum sans mettre à jour
    `RECREATE_*`.

  ## Documentation

  - [x] `backend/docs/data-model.md` mis à jour.
  - [x] `backend/docs/sprint-context.md` mis à jour.
  - [x] `backend/docs/api-contract.md` non modifié — pas de surface API.
  - [x] `openapi/openapi.yaml` non modifié — pas d'endpoint touché.

  ## Dépendances / ordre de merge

  Conflit attendu sur `SchemaFixup.java` avec la PR
  `feature/s5-attendees-list` (commit `cf83098`) qui ajoute également
  `attendances_status_check`. Cette PR inclut volontairement la même contrainte
  pour rester autonome :

  - Si SCRUM-164 merge en **premier** : la PR `feature/s5-attendees-list` rebase
    et retire le `RECREATE_ATTENDANCE_STATUS_CHECK` redondant.
  - Si `feature/s5-attendees-list` merge en **premier** : SCRUM-164 rebase et
    retire le test `attendancesStatusCheck_existsAfterStartup` redondant
    (le `RECREATE_ATTENDANCE_STATUS_CHECK` reste — il est commun aux deux).

  ## Décisions techniques tranchées

  - Option A (recréer) — pas Option B (supprimer les drops). Justification :
    préserver la validation DB-level.
  - Source de vérité : code Java (`Faculty.java`), pas la description du ticket
    qui cite les anciennes valeurs francophones.
  - DDL statique — pas de génération via `Faculty.values()`. Force une mise à
    jour explicite à chaque évolution d'enum (signal documentaire).
  ```

### Commits atomiques suggérés

- `feat(scrum-164): restore CHECK constraints on events.faculty / category / status`
- `test(scrum-164): cover idempotence, presence and rejection on schema check constraints`
- `docs(scrum-164): document SchemaFixup constraint reconciliation`

Combinables si le diff est petit (≤ 200 lignes au total) — à juger au moment de l'implémentation.

---

## Checklist Sonar / qualité

- [ ] Coverage ≥ 80 % sur les lignes nouvelles (JaCoCo). Cible attendue : 100 % (la classe est petite et tous les chemins sont accessibles).
- [ ] Duplication < 3 % sur le code nouveau.
- [ ] **Security Rating : A.** Le DDL est statique (string literal) — aucun input utilisateur n'atteint `Statement.execute()`. Vérifier qu'aucune règle Sonar `java:S2077` (« Formatting SQL queries is security-sensitive ») n'est levée.
- [ ] Reliability Rating : A.
- [ ] Maintainability Rating : A.
- [ ] Security Review Rating : A.

---

## Checklist finale

### Avant push

- [ ] `./mvnw verify` vert localement.
- [ ] Rapport JaCoCo `backend/target/jacoco-report/` — lignes nouvelles ≥ 80 %.
- [ ] Les **4 tests critiques** verts nommément (run ciblé) :
  - `reconcile_isIdempotent_whenInvokedTwice`
  - `eventsFacultyCheck_existsAfterStartup` (vérifie les 9 valeurs)
  - `eventsFacultyCheck_acceptsNull` (faculty est nullable)
  - `eventsCategoryCheck_rejectsInvalidValue` (sentinel anti-régression)
- [ ] `git diff --stat frontend/` vide.
- [ ] `git diff --stat openapi/` vide.
- [ ] Aucune nouvelle dépendance dans `backend/pom.xml`.
- [ ] Pas de `LOG.debug`/`LOG.trace` ajouté hors des deux logs prévus (1 INFO, 1 WARN).
- [ ] `cat backend/src/main/java/ch/unige/events/entity/Faculty.java` aligné avec les valeurs présentes dans `RECREATE_EVENTS_FACULTY_CHECK`.
- [ ] `cat backend/src/main/java/ch/unige/events/entity/EventCategory.java` aligné avec les valeurs présentes dans `RECREATE_EVENTS_CATEGORY_CHECK`.
- [ ] `cat backend/src/main/java/ch/unige/events/entity/EventStatus.java` aligné avec les valeurs présentes dans `RECREATE_EVENTS_STATUS_CHECK`.

### Avant PR

- [ ] Branche `feature/s7-schema-fixup-checks` créée avec `--no-track` depuis `origin/main`.
- [ ] `git branch -vv` confirme que la branche track `origin/feature/s7-schema-fixup-checks` après le premier push (PAS `origin/main`).
- [ ] Commits atomiques nommés selon la convention (`feat(scrum-164): ...`).
- [ ] Description de PR remplie selon le template, sections optionnelles « Why / Motivation », « Dépendances / ordre de merge » et « Décisions techniques tranchées » conservées.
- [ ] Base de la PR : `main`.
- [ ] La check CI `Lint PR title` est verte.

### Avant merge

- [ ] CI verte (`./mvnw verify` côté backend).
- [ ] Review approuvée.
- [ ] SonarCloud quality gate vert.
- [ ] Conflit avec `feature/s5-attendees-list` (si encore ouverte) résolu localement avant push final, sinon documenté pour le mainteneur de l'autre PR.

---

## Prompt de lancement d'implémentation

````
Tu vas implémenter SCRUM-164 — restaurer les contraintes CHECK orphelines sur `events.faculty`, `events.category`, `events.status` (et inclure également `attendances.status` pour rendre la PR autonome face à la PR ouverte `feature/s5-attendees-list`).

## ÉTAPE 0 — Création de la branche (avec --no-track OBLIGATOIRE)

Avant TOUT code :

    git fetch origin
    git checkout -b feature/s7-schema-fixup-checks origin/main --no-track

Le flag `--no-track` est CRITIQUE. Sans lui, la branche traque `origin/main` et `git push` envoie les commits sur main (incident documenté, cf. commit de revert 9c2e6d4 sur main). Le `-u` viendra au premier push pour set-up le bon upstream.

## Source unique de vérité

`specs_archives/specs_claude/specs_scrum-164.md` — à lire INTÉGRALEMENT avant d'écrire une ligne de code. Toutes les décisions (Option A pas B, DDL statique pas dynamique, source de vérité = code Java pas ticket Jira, faculty nullable mais category/status stricts, idempotence via DROP IF EXISTS + ADD, refacto reconcile() public, logger INFO/WARN, pas de @Transactional, pas de constructor injection, stratégie de divergence avec feature/s5-attendees-list) y sont tranchées. Tu n'as RIEN à inventer.

## À lire avant de commencer

1. `backend/AGENTS.md` — conventions (camelCase, pas de Flyway, schema géré par Hibernate update, seuil Sonar 80%, doc mise à jour dans le même commit).
2. `backend/docs/data-model.md` — section « Gestion du schéma » à la fin (ligne ~360+) où une nouvelle sous-section sera ajoutée.
3. `backend/docs/sprint-context.md` — section Sprint 7 (ligne ~164) où une entrée SCRUM-164 sera ajoutée.
4. `backend/src/main/java/ch/unige/events/config/SchemaFixup.java` — état actuel sur main (32 lignes, drops uniquement).
5. Le diff complet du commit cf83098 (`git show cf83098`) sur la branche `feature/s5-attendees-list` — pattern de référence à reproduire fidèlement (méthode reconcile() publique, DDL statique, logger, tableau DROP_OBSOLETE_CONSTRAINTS, test SchemaFixupTest).
6. Les enums actuels :
   - `backend/src/main/java/ch/unige/events/entity/Faculty.java` (9 valeurs : SCIENCES, MEDICINE, LETTERS, SOCIAL_SCIENCES, GSEM, LAW, THEOLOGY, PSYCHOLOGY, FTI)
   - `backend/src/main/java/ch/unige/events/entity/EventCategory.java` (6 valeurs : ACADEMIC, SPORTS, CULTURAL, SOCIAL, CONFERENCE, OTHER)
   - `backend/src/main/java/ch/unige/events/entity/EventStatus.java` (3 valeurs : DRAFT, PUBLISHED, CANCELLED)

⚠️ Vérifier ces valeurs littéralement avant de coder le DDL — le ticket Jira et le backlog citent des valeurs FRANCOPHONES obsolètes (LETTRES, DROIT, MEDECINE, SES, PSYCHOLOGIE, THEOLOGIE, GSI). Ce sont les valeurs ANGLOPHONES du code Java actuel qui font foi.

## Ordre d'implémentation strict

1. **`backend/src/main/java/ch/unige/events/config/SchemaFixup.java`** — remplacer le contenu complet par la version Étape 1 de la spec :
   - Ajouter `org.jboss.logging.Logger`, `java.sql.Connection`, `java.sql.Statement`.
   - Ajouter `private static final Logger LOG = Logger.getLogger(SchemaFixup.class);`.
   - Ajouter `static final String[] DROP_OBSOLETE_CONSTRAINTS = { ... }` avec les 4 drops (3 events + 1 attendances).
   - Ajouter quatre `static final String RECREATE_*` (events_faculty/category/status_check + attendances_status_check) — DDL statique avec les valeurs d'enum littéralement transcrites depuis le code Java.
   - Ajouter `static final String[] RECREATE_CONSTRAINTS` qui les regroupe.
   - Refactorer `onStart` pour qu'il délègue à `reconcile()` public.
   - Implémenter `public void reconcile()` : try-with-resources Connection/Statement, loop DROP, loop RECREATE, LOG.info récapitulatif, catch SQLException → LOG.warnf non-fatal.
   - Conserver `@ApplicationScoped`, `@Inject DataSource dataSource;` (champ, pas constructor).
   - Conserver `void onStart(@Observes StartupEvent ev)`.
   - Pas de `@Transactional`, pas de re-throw.

2. **`backend/src/test/java/ch/unige/events/config/SchemaFixupTest.java`** — créer le fichier complet selon Étape 2 de la spec :
   - `@QuarkusTest`, package-private, injections `SchemaFixup`, `DataSource`, `EntityManager`.
   - 9 tests : `reconcile_isIdempotent_whenInvokedTwice`, `eventsFacultyCheck_existsAfterStartup` (asserts les 9 valeurs), `eventsFacultyCheck_acceptsNull`, `eventsFacultyCheck_rejectsInvalidValue`, `eventsCategoryCheck_existsAfterStartup`, `eventsCategoryCheck_rejectsInvalidValue`, `eventsStatusCheck_existsAfterStartup`, `eventsStatusCheck_rejectsInvalidValue`, `attendancesStatusCheck_existsAfterStartup`.
   - Trois helpers privés : `assertConstraintDefContains(table, constraint, expectedValues...)` (lit `pg_get_constraintdef`), `insertNativeEvent(colVals...)` (insert SQL natif via DataSource), `assertCheckViolation(SqlRunnable)` (asserte SQLState '23514' ou message contenant "check constraint").
   - Pas de `@TestTransaction` sur les tests qui font des INSERT natifs (les rows orphelines en DB éphémère ne posent pas problème).
   - L'INSERT natif fournit toutes les colonnes NOT NULL : id (via nextval('events_seq')), title, location, start_date, end_date, category, status, all_day, created_at, updated_at.

3. **`./mvnw verify`** — DOIT être vert avec couverture ≥ 80 % sur `SchemaFixup`. Corriger avant de passer à la doc. Si `events_seq` n'est pas le bon nom de séquence (Quarkus 3.32 par défaut), inspecter via `psql` les séquences existantes et ajuster — c'est probablement `events_seq` ou `hibernate_sequence`.

4. **Documentation (même commit que le code correspondant ou commit `docs(scrum-164):` séparé)** :
   - `backend/docs/data-model.md` — ajouter sous-section « Réconciliation des contraintes CHECK — `SchemaFixup` » à la fin de la section « Gestion du schéma » (cf. Étape 3.1 de la spec) avec la table récapitulative des 4 contraintes.
   - `backend/docs/sprint-context.md` — ajouter une entrée SCRUM-164 dans la liste à puces « Sprint 7 (planifié : 24 avril – 8 mai 2025) » ligne ~164 (cf. Étape 3.2 de la spec).
   - PAS de modification de `backend/docs/api-contract.md` (pas de surface API).
   - PAS de modification de `openapi/openapi.yaml` (pas d'endpoint).

5. **Vérifications finales avant push** :
   - `git diff --stat frontend/` vide.
   - `git diff --stat openapi/` vide.
   - Pas de nouvelle dépendance Maven.
   - `cat` les trois enums Java et confirmer que les listes dans `RECREATE_*` sont strictement identiques.

## Interdits stricts

- PAS de migration Flyway / Liquibase, PAS de fichier SQL de migration.
- PAS de modification d'OpenAPI, d'aucun Resource, Service, DTO, entité métier, hook frontend.
- PAS de @Transactional sur reconcile().
- PAS de constructor injection — préserver `@Inject DataSource dataSource;` champ.
- PAS de génération du DDL via `Faculty.values()` — DDL statique uniquement.
- PAS de centralisation dans une classe « SchemaConstraints » dédiée.
- PAS de throws SQLException sur reconcile() — encapsuler le catch.
- PAS de RuntimeException à la place du LOG.warnf — best-effort reste.
- PAS de log par DDL — un seul INFO, un seul WARN.
- PAS d'élargissement à d'autres tables/colonnes que faculty, category, status d'events + status d'attendances.
- PAS de modification d'application.properties, pom.xml, Dockerfile.
- PAS de retrait de l'observer onStart.
- PAS de @TestTransaction sur les INSERT natifs (la séquence n'est pas resettée par le rollback).
- PAS de mock de DataSource ou EntityManager — DevServices PostgreSQL réel obligatoire.
- PAS de copie/duplication des valeurs d'enum dans une 2e source — les constantes RECREATE_* sont la SEULE source SQL.
- PAS de snake_case côté Java, pas de TODO commenté.

## Conventions à respecter

- camelCase partout côté Java ; les noms snake_case (start_date, etc.) restent uniquement dans le SQL natif.
- Couverture JaCoCo ≥ 80 % sur les lignes nouvelles ; duplication < 3 % ; Sonar ratings A.
- Doc mise à jour dans le même commit que le code (règle backend/AGENTS.md).
- Commits atomiques nommés `feat(scrum-164): …`, `test(scrum-164): …`, `docs(scrum-164): …`.
- Titre PR : `feat(scrum-164): restore CHECK constraints on events.faculty / category / status`.

## Critères de done

- [ ] `./mvnw verify` vert localement et en CI.
- [ ] JaCoCo ≥ 80 % sur les lignes nouvelles.
- [ ] Les 4 tests critiques verts nommément :
  - `reconcile_isIdempotent_whenInvokedTwice`
  - `eventsFacultyCheck_existsAfterStartup` (avec assertion `containsAll` des 9 valeurs)
  - `eventsFacultyCheck_acceptsNull`
  - `eventsCategoryCheck_rejectsInvalidValue` (capture SQLState '23514')
- [ ] Les 5 autres tests verts (event status × 2, attendances × 1, faculty rejection × 1, category presence × 1).
- [ ] `git diff --stat frontend/` et `git diff --stat openapi/` vides.
- [ ] SonarCloud Quality Gate vert (Security Rating A — DDL statique).
- [ ] `backend/docs/data-model.md` et `backend/docs/sprint-context.md` mis à jour dans la même PR.
- [ ] PR ouverte avec base `main`, titre `feat(scrum-164): restore CHECK constraints on events.faculty / category / status`, description complète selon le template (Résumé, Why, Changements Backend + Documentation, Tests, Test plan, Documentation, Dépendances/ordre de merge mentionnant feature/s5-attendees-list, Décisions techniques tranchées).
- [ ] Commits atomiques bien nommés.
- [ ] `git branch -vv` confirme que la branche track `origin/feature/s7-schema-fixup-checks` (PAS `origin/main`) après le premier push.
- [ ] La check CI `Lint PR title` est verte.
````
