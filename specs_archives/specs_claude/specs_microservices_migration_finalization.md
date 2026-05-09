# Migration vers microservices — backend UNIGE Events — FINALISATION (PR #158)

| Champ | Valeur |
|---|---|
| Ticket Jira | Suite migration monolithe → microservices, cours pinfo6, brief Agon |
| Sprint | S8 (finalization) |
| Branche | `refactor(backend)--migrate-to-microservices` (persistante, **NE PAS créer de nouvelle branche**) |
| Base de l'exécution | HEAD `5346342` à minima ; tip réel au moment du lancement = `git rev-parse HEAD` |
| PR active | #158 (`https://github.com/unige-pinfo6-2026/unige-events/pull/158`) — **NE PAS merger**, l'humain Elie Bussod merge lui-même |
| Spec antérieure 1 | [`specs_archives/specs_claude/specs_microservices_migration.md`](../specs_archives/specs_claude/specs_microservices_migration.md) (1884 lignes, 30+ décisions) |
| Spec antérieure 2 | [`specs_archives/audit_pr158_microservices_migration.md`](../specs_archives/audit_pr158_microservices_migration.md) (132 findings) |
| Spec antérieure 3 | [`specs_archives/specs_claude/specs_microservices_migration_completion.md`](../specs_archives/specs_claude/specs_microservices_migration_completion.md) (2794 lignes, Décisions A-Y, exécutée à ~70 %) |
| Auteur | Claude (session 2026-05-09 PM) |
| Exécuteur cible | Claude Code en **bypass-permissions**, autonome, branche persistante, sans merge |
| Frontend lié | **AUCUN** — `git diff --shortstat origin/main HEAD -- frontend/` doit rester à 0 ligne |
| OpenAPI | **AUCUN** — `git diff --shortstat origin/main HEAD -- openapi/` doit rester à **strictement 0 ligne** (cf. Décision G : annulation de la dérogation Q de la completion-spec) |
| Frontière DevOps | 7 items hors-scope inchangés (cf. [`backend/docs/devops-handoff.md`](../backend/docs/devops-handoff.md)) |

---

## Note d'implémentation

Cette spec est l'unique source de vérité pour la **finalisation** de la PR #158. Elle fait suite directe à :

- la spec originale (architecture cible 14 services + Kong + Kafka — décisions structurantes du brief Agon) ;
- l'audit post-PR-158 (132 findings classés en 11 catégories) ;
- la **completion spec** (Décisions A-Y, Étapes 0-14) qui a été **exécutée à ~70 %**. Étapes livrées : 0, 1, 2, 3, 3.0, 4 (intégrale), 9 (intégrale), 10, 11, 12.2/3/4, 13.6/7/10. Étapes reportées : **5, 7, 8, 12.1**.

La présente spec adresse :

1. **Le report des 4 étapes restantes de la completion spec** (5/7/8/12.1).
2. **Une consolidation 14→5 services** identifiée comme rationalisation nécessaire (le brief Agon demande des « microservices » au pluriel ; 13 actifs est sur-décomposé pour un projet pédagogique de 6 mois et coûte ~20 pods en preview, ~3.3 GiB mémoire, debug cross-service à 4 hops). La consolidation **précède** les autres étapes pour éviter de jeter le travail (REST clients, tests, Pact, CI matrix sur des paires consumer/provider qui vont fusionner).

L'exécuteur :
- ne demande **jamais** une décision au user (toutes tranchées ici A-J) ;
- commit + push après chaque sous-étape numérotée verte (granularité ≈ 1 commit par sous-étape `N.M`, sauf consolidation par paire — accepté 100-300 lignes diff par merge) ;
- pousse sur la branche persistante `refactor(backend)--migrate-to-microservices` ;
- ne merge **jamais** la PR #158 ;
- ne crée **jamais** de nouvelle branche, jamais de nouveau ticket Jira, jamais de nouvelle PR ;
- met à jour `backend/docs/sprint-context.md` (nouvelle Étape 20 — finalisation) au fil de l'eau, regroupé en commit final d'Étape 8.5 ;
- met à jour le **PR body** de #158 quand toute la spec a été livrée (Étape 8.6) ;
- valide chaque étape via `cd backend && ./mvnw verify -DskipITs` (~5-7 min sur le reactor consolidé) ;
- watch CI **par étape majeure** (pas par sous-étape — directive explicite Elie pour économiser temps) : `gh pr checks 158 --watch` à la fin de chaque Étape ≥ 2 ;
- en cas d'échec CI, **fixe la cause racine** — jamais de `--no-verify`, jamais de `@Disabled`, jamais de skip silencieux ; sauf si le fail provient d'un item DevOps S9+ documenté (ex. SonarCloud project not found après matrix activation), auquel cas il **continue** et documente.

Toute déviation par rapport aux décisions ci-dessous (A à J) doit être **actée explicitement** dans le commit message + dans `sprint-context.md` Étape 20, avec justification concrète. Les déviations triviales (ex. nom de classe légèrement différent) ne nécessitent pas d'acte.

> **Leçon Flyway-immutabilité (rappel — cf. specs précédentes).** La règle « migration committée = immutable » s'applique par-base. Les migrations historiques V1..V17 sont **gravées** dans la `flyway_schema_history` des bases preview/prod ; tout nouveau changement va dans un nouveau fichier `V<N>__...sql`. Cette règle s'applique aussi aux mouvements de tables entre services dans la consolidation (Étape 2) — bien que les tables ne bougent **pas physiquement** (schéma `public` partagé, cf. Décision C de la completion-spec qui défère DB-per-service S9+) ; seul le service propriétaire **logique** change.

---

## Contexte

### Le besoin produit (rappel — brief Agon)

> *« Le backend de ce projet (Quarkus 3 / Java 21 / PostgreSQL / Kubernetes + Helm) est actuellement un monolithe. Dans le cadre du cours, nous devons migrer vers une architecture microservices. »* — brief Agon.

La PR #158 a livré la *charpente* puis ~70 % de la *complétion* de cette migration. La présente spec **finalise** :
1. consolidation des 13 services actifs vers une topologie de 4 services métiers + 1 placeholder (Décision A) ;
2. matérialisation des REST clients cross-service (Décision C) ;
3. portage des 1818 tests legacy (Décision D) ;
4. Pact JVM + 1 E2E happy path (Décision E) ;
5. CI matrix per-service (Décision F) ;
6. documentation finale et passation DevOps.

### État livré dans la PR #158 à HEAD `5346342`

**Étapes completion spec livrées (cf. completion-spec § Plan d'implémentation)** :

| Étape | Contenu | Statut |
|---|---|---|
| 0 | Pré-flight | ✅ |
| 1 | 8 bug fixes critiques (BUG-003/009/010/012/014, SEC-004, HYGIENE-001/002) | ✅ 7 commits |
| 2 | Documentation alignment (DOC-013..021, +AGENTS.md root) | ✅ 11 commits |
| 3 | Création des 8 nouvelles shared libs (api-error, domain-enums, domain-dtos, domain-projections, jaxrs, tracing, kafka-events, platform) — toutes ≥ 95 % L | ✅ 9 commits |
| 4 | 6 producteurs Kafka manquants + 1 consumer events.banned via pattern CDI `@Observes(AFTER_SUCCESS)` | ✅ 6 commits |
| 9 | Observability (logs JSON + Prometheus + shared-tracing X-Request-ID) | ✅ 2 commits |
| 10 | Kong rate-limiting plugin sur 3 routes (events.create=10, comments.post=10, follows.follow=30 / min, policy=local) | ✅ 1 commit |
| 11 | livenessProbe sur 13 deployments + commentaire ingress.yaml | ✅ 1 commit |
| 12.2 | sonar.projectKey override per-module (14 services + 10 libs = 24 projets attendus côté SonarCloud) | ✅ 1 commit |
| 12.3 | Retrait du glob `<sonar.coverage.exclusions>services/*-service/**/*</sonar.coverage.exclusions>` du parent POM | ✅ 1 commit |
| 12.4 | Rename `image.api.tag → image.tag` partout (values.yaml, 14 deployments, statefulset Kafka, deploy.yml) | ✅ 1 commit |
| 13.6 | Création `backend/docs/devops-handoff.md` (7 items DevOps S9+ formalisés) | ✅ 1 commit |
| 13.7 | Création `backend/docs/internal-endpoints.md` (catalogue endpoints internes service-to-service hors openapi) | ✅ 1 commit |
| 13.10 | PR body de #158 mis à jour | ✅ |

**Étapes completion spec REPORTÉES à la présente finalisation** :

| Étape | Contenu | Raison du report |
|---|---|---|
| **5** | REST clients (35 stubs JPA → @RegisterRestClient + résilience + endpoints internes) | Volumétrie ~30-40 commits ; refactor profond des 13 services |
| **7** | Port 1818 tests legacy + 35 sentinels SCRUM-138/139/144/147 verts par nom | Dépend strictement de Étape 5 (les tests doivent mocker les REST clients) |
| **8** | 4 pacts + 1 E2E happy path | Dépend strictement de Étape 5 (les pacts définissent les contrats REST) |
| **12.1** | CI matrix YAML refactor de `build.yml` | Risqué seul ; la moitié POM (12.2/3/4) est livrée et attend l'activation matrix |

**État courant des invariants** :

| Invariant | Cible | État actuel HEAD `5346342` |
|---|---|---|
| `git diff --shortstat origin/main HEAD -- frontend/` | 0 lignes | ✅ 0 lignes |
| `git diff --shortstat origin/main HEAD -- openapi/` | 0 lignes (cf. Décision G — annulation dérogation Q) | ✅ 0 lignes |
| 35 sentinels SCRUM-138/139/144/147 | verts par nom | ❌ 0/35 |
| 0 JPA stub cross-service | 0 | ❌ **37 stubs** répartis sur 12 services consommateurs |
| Coverage backend total (jacoco local agrégé) | ≥ 80 % L / ≥ 70 % B | ❌ 22.4 % L / 9.7 % B (cf. tableau split shared-libs vs services métiers ci-dessous) |
| Couverture shared libs (10) | ≥ 95 % L / ≥ 90 % B | ✅ 100 % L / 100 % B |
| Couverture services métiers (13) | ≥ 80 % L / ≥ 70 % B | ❌ 14.7 % L / 2.6 % B (en attente du portage Étape 5 + 7) |

**Diagnostic de la couverture 22 %** : le retrait du glob d'exclusion Sonar (commit `10ce0a1`) a exposé l'absence du portage des tests legacy (Étape 7 reportée). Les services contiennent du code métier substantial (`event-service` 818 lignes, 7.6 % couvertes ; `follow-service` 279 lignes, 6.8 % couvertes ; …) car la suppression de `legacy-monolith` (commit `b570c1b`) a effacé 1818 méthodes `@Test` et seuls ~10-14 tests sentinel ont été portés. **Le coverage gate Sonar échoue donc — comportement attendu jusqu'à Étape 5 du présent plan.**

### État Kafka post-completion

10 topics provisionnés, **9 producteurs câblés** (event-service ×3, follow-service ×3, comment-service ×1, co-organizer-service ×2, report-service ×1) + **1 consommateur** (event-service ← `events.banned`). Pattern uniforme CDI `@Observes(during=AFTER_SUCCESS)` + bridge `<Domain>KafkaBridge` qui appelle l'`Emitter` post-commit JDBC (Décisions A et F de la completion-spec). Aucun event fantôme sur rollback (BUG-001/002 fixés).

### Pourquoi consolider 14→5 maintenant

- **Coût ops disproportionné** : 13 pods runtime × 256 MiB mémoire limite + Kong (×2 prod) + 5 services infra (db, kafka, minio, web, cloudflared) = ~20 pods en preview pour un projet à ~6 entités métier. La preview Cloudflared tunnel intermittent fail au démarrage (cf. PR #158 retry 3 dans `sprint-context.md`).
- **Découpage incohérent avec les bounded contexts DDD** : `share-service` (1 endpoint, 0 schéma, lit `events.share_code`), `view-service` (1 endpoint, 1 counter), `favorite-service` (favorite = un user × un event, naturellement co-localisé avec event), `co-organizer-service` (cascade SCRUM-136 = la sécurité primitive de event), `stats-service` (read-only aggregator OVER event data), `me-aggregator-service` (BFF qui ne sert qu'un seul endpoint `/users/me/events`) — six services qui appartiennent **structurellement** à `event-service`.
- **Multiplication des REST clients superflus** : la table de 35 stubs de la completion-spec Décision B se réduit à ~8 couples consumer/provider après consolidation (cf. Décision C ci-dessous). Économie : ~75 % de la plomberie REST jetable.
- **Multiplication des tests** : 13 services × ServiceIdentityResourceTest sentinel = 13 tests rituels qui dupliquent la même assertion, plus 14 SonarCloud projects à provisionner côté DevOps. Avec 5 services : 5 sentinels, 5 projets.
- **Le brief Agon dit « microservices » au pluriel** — 4 services métiers actifs + 1 placeholder respecte ce contrat. La proposition n'enlève **rien** sur le plan pédagogique : Kong gateway + Kafka broker + REST clients cross-service + sécurité OIDC + observabilité + déploiement Helm restent tous illustrés. On retire juste la sur-décomposition.
- **Le moment est optimal** : avant Étape 5 (REST clients), avant Étape 7 (tests legacy à porter), avant Étape 12.1 (CI matrix) — toutes des étapes dont le coût est proportionnel au nombre de services. Consolider d'abord économise ~60 % du travail restant.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

> **Pour l'exécuteur** : chaque décision A → J ci-dessous est définitive. Aucune ne doit être tranchée au moment de l'implémentation. Si une situation imprévue émerge, applique la règle « principe de moindre surprise vs cette décision » et **acte la déviation** dans le commit message + sprint-context Étape 20.

### Décision A — Topology cible : **5 services métiers (4 actifs + 1 placeholder)**

**Décision.** Consolidation des 13 services actifs vers 4 services métiers actifs + 1 placeholder, soit 5 modules `services/<svc>-service/` au total + 10 shared libs = 15 modules dans le reactor (vs 24 actuellement).

**Topology cible** :

| # | Service | Tables possédées | Endpoints `@Path` racines | Schedulers | Replicas (prod / preview) |
|---|---|---|---|---|---|
| 1 | **event-service** | `events`, `event_tags`, `event_views`, `favorites`, `event_co_organizers` | `/events*`, `/admin/events/{id}/{,un}feature`, `/events/search`, `/events/featured`, `/events/{id}/image`, `/events/{id}/share`, `/s/{shortCode}`, `/events/{id}/view`, `/events/{id}/favorite`, `/users/me/favorites`, `/events/{id}/co-organizers/*`, `/users/me/co-organizer-invitations`, `/events/{id}/stats`, `/users/me/events` | EventExpirationJob (every 1h, replicas:1 strict) | 1 / 1 |
| 2 | **user-service** | `users`, `user_interests`, `follows` | `/users/me`, `/users/{id}`, `/users/me/image`, `/users/me/banner`, `/users/{id}/follow*`, `/follow-requests/*`, `/users/me/follow-requests`, `/users/me/calendar-token*`, `/calendar/{token}.ics` | (aucun) | 1 / 1 |
| 3 | **engagement-service** | `attendances`, `comments` | `/events/{id}/attend*`, `/users/me/attendances`, `/users/me/participations`, `/events/{id}/comments`, `/comments/{id}` | (aucun) | 1 / 1 |
| 4 | **moderation-service** | `reports` | `/events/{id}/report`, `/admin/reports*` | ModerationCleanupJob (cron 03:00 Europe/Zurich, replicas:1 strict) | 1 / 1 |
| 5 | **notification-service** | aucune (placeholder) | aucune (placeholder, scaffold SCRUM-99) | aucun | 0 / 0 |

**Justification rigoureuse table par table** :

| Service source actuel | Service cible | Justification du regroupement |
|---|---|---|
| `share-service` | event-service | 1 endpoint (`/events/{id}/share`) + 1 endpoint anonymous (`/s/{shortCode}`). 0 schéma propre, lit `events.share_code` via stub. La logique « génère un short code → URL frontend » est une **vue** sur l'entité Event. Coût d'extraction (1 service, 1 pod) > bénéfice (zéro isolation business). Naturellement co-localisé. |
| `view-service` | event-service | 1 endpoint (`POST /events/{id}/view`) + 1 table de counter (`event_views`). La table est sémantiquement une **statistique** d'un Event ; la posséder à part de events crée un cross-service call à chaque incrément. Co-localisation = upsert local idempotent. |
| `favorite-service` | event-service | 2 endpoints (`POST/DELETE /events/{id}/favorite`, `GET /users/me/favorites`). 1 table (`favorites`) qui est une relation many-to-many `user × event`. La complétion spec propose un endpoint interne `GET /events/{id}/favorite-count` consommé par event-service ET stats-service — donc favorite-service est consommé par tout le monde. Le mettre dans event-service supprime ces appels REST circulaires. La résolution `user × event` reste valide : user-service expose `/users/{id}` (REST sync), favorites stocke l'id user, event-service est le owner. |
| `co-organizer-service` | event-service | 2 endpoints (`/events/{id}/co-organizers/*`, `/users/me/co-organizer-invitations`). 1 table (`event_co_organizers`). **La cascade SCRUM-136 (`isCreatorOrAcceptedCoOrganizer`) est LA primitive de sécurité d'un Event** — elle est consommée par event-service (publish, cancel), engagement-service (post comment, RSVP gating), moderation-service (report cascade), … Centraliser cette règle dans event-service supprime un endpoint interne `/check?userId=` (Décision L de la completion-spec) et le remplace par un appel local de méthode statique. Réduction nette de ~5 REST clients consumer→co-organizer. |
| `stats-service` | event-service | 1 endpoint (`GET /events/{id}/stats`). Read-only aggregator qui lit attendances + favorites + views. Post-consolidation, attendances vit dans engagement-service (1 REST call) ; favorites + views vivent localement dans event-service (0 REST call). Le bénéfice de l'isolation read-only (scaling indépendant) est négligeable pour un projet pédagogique sur preview cluster. |
| `me-aggregator-service` | event-service | 1 endpoint (`GET /users/me/events` — events créés par moi). Le BFF était justifié dans la spec orig. (Décision 4) pour fan-out multi-domaine. Post-consolidation, `/users/me/events` est strictement event-domain (la liste des events créés par le caller). Plus de fan-out → plus besoin de BFF. **me-aggregator-service est SUPPRIMÉ.** Les autres `/users/me/*` paths (`/me/attendances`, `/me/favorites`, `/me/follow-requests`, `/me/co-organizer-invitations`, `/me/calendar-token`) routent directement vers leur service domain (cf. Kong table). |
| `follow-service` | user-service | 4 endpoints (`/users/{id}/follow*`, `/follow-requests/*`, `/users/me/follow-requests`). 1 table (`follows`). Relation many-to-many `user × user`. Sémantiquement, follow est une **propriété de l'identité utilisateur** (« qui je suis » inclut « qui je suis » comme verbe). Le user-service possède déjà `users` ; absorber `follows` consolide le bounded context « social graph autour d'un User ». Le 3 producteurs Kafka (`users.{followed,follow-requested,follow-accepted}`) déménagent vers user-service en restant sur le même group.id consumer (notification-service futur). |
| `calendar-service` | user-service | 3 endpoints (`/users/me/calendar-token*`, `/calendar/{token}.ics`). 0 schéma propre, écrit `users.calendar_token`. La feed ICS est strictement **user-centric** : le token vit sur user, le contenu (events) est récupéré par REST clients. Co-localiser dans user-service supprime le besoin d'un endpoint interne `GET /users/by-calendar-token/{token}` (Décision B de la completion-spec). |
| `attendance-service` | engagement-service (rename) | 4 endpoints (`/events/{id}/attend*`, `/users/me/attendances`, `/users/me/participations`). 1 table (`attendances`). Logic complexe : capacity gating, waitlist auto-promotion, lock pessimiste. attendance-service est **RENOMMÉ** en engagement-service (au lieu d'être absorbé par un autre service inexistant) → garde toute son infrastructure et son artifactId, juste un nouveau nom + accueille comments. |
| `comment-service` | engagement-service | 4 endpoints (`/events/{id}/comments`, `/comments/{id}`). 1 table (`comments`). Replies 1 niveau, anti-oracle ISSUE-92, cascade SCRUM-136. Engagement (« interactions de participants sur un Event ») est un bounded context cohérent : RSVP + commentaires sont les deux formes de participation active. Sémantique DDD claire. |
| `report-service` | moderation-service (rename) | 2 endpoints (`/events/{id}/report`, `/admin/reports*`). 1 table (`reports`). Scheduler `ModerationCleanupJob` (replicas:1 strict). Le service est **RENOMMÉ** en moderation-service (clarifie le rôle : reports + cleanup automatique = modération). Aucun absorbant. Reste indépendant des 3 autres services métier car (a) ses paths sont admin-only sauf `/events/{id}/report`, (b) son scheduler nécessite replicas:1 strict (différent profil de scaling), (c) le bounded context « modération + auto-ban » est isolé. |
| `notification-service` | (inchangé) | Placeholder existant, replicas:0, scope SCRUM-99 hors S8/S9. Reste inchangé. Le pom reste packagé `jar` ; l'application starter reste minimaliste avec son `ServiceIdentityResource`. |

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **5 services (4 actifs + 1 placeholder)** | Bounded contexts DDD clairs ; ~75 % moins de REST clients ; ~60 % moins d'effort sur Étapes 5/7/8/12.1 ; ops simplifié (10-13 pods runtime au lieu de 20+) ; brief Agon respecté (microservices au pluriel) | 6 services source à fusionner, 9 merges, ~12 commits de consolidation ; fenêtre de risque pendant la consolidation (un merge cassé bloque les suivants) | ✅ retenu |
| (b) 6 services (garder stats-service séparé read-only) | stats-service scale indépendamment | Pour 1 endpoint ? Effort/bénéfice nul ; +1 SonarCloud project, +1 pod runtime, +1 cellule CI matrix | ❌ over-engineering |
| (c) 6 services (garder me-aggregator) | Pattern BFF préservé pédagogiquement | me-aggregator ne sert qu'un endpoint ; le BFF perd son sens si tous les autres `/me/*` ne passent pas par lui | ❌ |
| (d) 7 services (split engagement en attendance + comment) | Bounded contexts plus stricts | Reproduit la sur-décomposition qu'on cherche à supprimer | ❌ |
| (e) 4 services (fusionner moderation dans event) | Encore plus simple | Le scheduler ModerationCleanupJob a un profil scaling différent (replicas:1 strict) ; mélanger avec event-service (replicas:1 mais EventExpirationJob OK) impose deux schedulers stricts dans un seul service | ❌ |
| (f) Statu quo (13 services) | 0 effort | Toutes les contraintes décrites en § Pourquoi consolider | ❌ |

**Adresse** : (nouveau item) consolidation 14→5 ; SPEC-009 (me-aggregator devient vrai BFF — ici la décision inverse est plus saine : me-aggregator disparaît car le BFF fan-out n'a jamais été nécessaire) ; REFACTOR-016 (me-aggregator → event-service supprime le « monolithe distribué » sur ce path).

### Décision B — Stratégie de merge X→Y : **`git mv` + rename packages + `application.properties` inline-merge + 1 commit par paire**

**Décision.** Pour chaque merge `<X>-service` → `<Y>-service`, l'exécuteur applique le pattern **uniforme** suivant en un seul commit (≤ 300 lignes diff acceptable car bascule mécanique) :

1. **Move des sources Java** :
   ```bash
   git mv backend/services/<X>-service/src/main/java/ch/unige/events/<X>/* \
          backend/services/<Y>-service/src/main/java/ch/unige/events/<Y>/<X>/
   ```
   Le sous-package `<X>` regroupe les sources dans le service cible (ex. `ch.unige.events.event.share.*` pour share-service absorbé par event-service). **Pas de mélange de packages** : `share/dto/`, `share/resource/`, `share/service/`, `share/entity/` cohabitent avec `event/dto/`, `event/resource/`, `event/service/`, `event/entity/` dans le même module.

2. **Rename packages dans tous les fichiers Java** :
   ```bash
   sed -i 's|^package ch\.unige\.events\.<X>\.|package ch.unige.events.<Y>.<X>.|g; s|^import ch\.unige\.events\.<X>\.|import ch.unige.events.<Y>.<X>.|g' \
       backend/services/<Y>-service/src/main/java/ch/unige/events/<Y>/<X>/*/*.java
   ```

3. **Move des tests Java** : identique à 1 + 2, sous `src/test/java/ch/unige/events/<Y>/<X>/...`.

4. **Move des resources** :
   - Sources : `git mv backend/services/<X>-service/src/main/resources/* backend/services/<Y>-service/src/main/resources/<X>-merged/` (si conflit) **OU** simple suppression du dossier source si vide après extraction du `application.properties` (cf. point 5).
   - Tests : idem.

5. **Merge `application.properties`** :
   - **Dedup** : datasource, hibernate-orm, OIDC, jackson, smallrye-openapi, container-image (garder UN seul bloc).
   - **Concat** : Kafka outgoing channels (chaque service apporte les siens), incoming (event-service consume `events-banned`).
   - **Concat** : observability (déjà identique sur tous les services).
   - **Concat** : scheduler (`%test.quarkus.scheduler.enabled=false` — déjà partagé).
   - Renommer la valeur de `quarkus.container-image.name` du service cible si nécessaire (event-service garde `unige-events-event-service`).

6. **Move des stubs JPA cross-service** : les `*Stub.java` du service source qui référencaient des tables maintenant possédées localement deviennent des **entités réelles** dans le service cible. Détail dans Étape 4 (REST clients) — pour le merge, on **conserve** les stubs JPA tels quels et on les nettoie en bloc pendant l'Étape 4.

7. **Mise à jour Kong route** (`k8s/chart/templates/kong/configmap-routes.yaml`) :
   - Trouver le bloc `- name: <X>-service` ;
   - Soit le **fusionner** avec le bloc `- name: <Y>-service` (move des `routes:` enfants), soit le **supprimer** si Y existe déjà avec sa propre liste de routes ;
   - Mettre à jour `url:` cible (`http://<Y>-service:8080`).

8. **Suppression du Helm Deployment** :
   ```bash
   git rm -r k8s/chart/templates/<X>-service/
   ```
   Et toute clé `image.<X>-service.*` ou similaire dans `values.yaml` (post-Étape 12.4 il n'y en a plus, c'est `image.tag` global).

9. **Suppression du module dans le parent POM** :
   ```xml
   <!-- supprimer la ligne -->
   <module>services/<X>-service</module>
   ```
   Puis `git rm -r backend/services/<X>-service/` (le dossier devrait être vide après les `git mv`).

10. **Mise à jour `quarkus.application.name`** dans le `application.properties` du service cible — déjà à `<Y>-service`, ne pas changer.

11. **Validation locale** :
    ```bash
    cd backend && ./mvnw -pl services/<Y>-service -am test -DskipITs
    ```
    Doit être SUCCESS. Toute erreur de compilation = rollback du commit (avant push), fix, retry.

12. **Commit + push** :
    ```bash
    git add -A
    git commit -m "refactor(backend): merge <X>-service into <Y>-service (consolidation N.M)"
    git push origin 'refactor(backend)--migrate-to-microservices'
    ```

**Cas particulier — RENOMMAGE pur** (`attendance-service → engagement-service`, `report-service → moderation-service`) :

Le service garde ses sources telles quelles ; on change juste :
- `artifactId` dans `pom.xml` : `<X>-service` → `<Y>-service`
- `name` dans `pom.xml` (champ humain)
- nom du dossier : `git mv backend/services/<X>-service backend/services/<Y>-service`
- nom du module dans le parent POM
- `quarkus.container-image.name` dans `application.properties`
- `quarkus.application.name` (utilisé par `ServiceIdentityResource` shared-platform — devient `<Y>-service`)
- `sonar.projectKey` + `sonar.projectName` dans le pom enfant
- nom du dossier Helm `templates/<X>-service/` → `templates/<Y>-service/`
- nom du Deployment dans `templates/<Y>-service/deployment.yaml` (selectors, labels)
- nom du Service ClusterIP dans `templates/<Y>-service/service.yaml`
- Kong route `service:` dans `configmap-routes.yaml` : `<X>-service:8080` → `<Y>-service:8080`
- Image GHCR : `unige-events-<X>-service` → `unige-events-<Y>-service` (poussée à la prochaine release)

**Le rename est un commit autonome avant les merges**.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **`git mv` + rename packages + commit unique par merge** | Simplicité, atomicité, reviewable, build local valide à chaque commit | Une erreur de package rename = compilation cassée | ✅ retenu |
| (b) Move sans rename packages (garder `ch.unige.events.share.*` dans event-service) | Pas de churn de imports | Ambiguïté de propriété ; conflit potentiel sur classes homonymes (ApiErrorResponse) | ❌ |
| (c) Move + rename + commit séparé par sous-étape (move | rename | resource | helm | pom) | Granularité fine | Build cassé entre commits ; review × 5 | ❌ |
| (d) Tout mettre dans un seul commit (toute la consolidation = 1 commit ≈ 3000 lignes) | 1 review pour le tout | Trop gros pour reviewer ; rollback en cas de problème | ❌ |

**Adresse** : SPEC-009 (me-aggregator), REFACTOR-016 (me-aggregator → event), nouveau (consolidation).

### Décision C — REST clients post-consolidation : **8 couples consumer/provider (au lieu de 35)**

**Décision.** Après consolidation 14→5, les couples cross-service surviving qui nécessitent un REST client `@RegisterRestClient` sont **8** (au lieu des 35 stubs à remplacer dans la completion-spec Décision B). Liste exhaustive :

| # | Consumer | Provider | Endpoint(s) consommé(s) | Resilience | Endpoint interne nouveau ? |
|---|---|---|---|---|---|
| 1 | event-service | user-service | `GET /users/{id}` (enrichir creatorId → displayName/avatar pour EventDTO) | retry+timeout+CB+fallback `Optional.empty()` | non (existant) |
| 2 | event-service | engagement-service | `GET /events/{eventId}/attendance-summary` (count by status, pour EventDTO + capacity gating) | retry+timeout+CB | **OUI** (nouveau, interne) |
| 3 | user-service | event-service | `GET /events?ids=...&status=PUBLISHED` (bulk pour ICS feed calendar) | retry+timeout+CB | **OUI** (nouveau, bulk, interne) |
| 4 | user-service | engagement-service | `GET /users/{id}/attendances?status=ATTENDING` (pour ICS feed) | retry+timeout+CB | non (existant public, réutilisé interne) |
| 5 | engagement-service | event-service | `GET /events/{id}` (visibilité ISSUE-92 + cascade SCRUM-136 via `?check-co-org-of=`) | retry+timeout+CB | **OUI** (paramètre nouveau pour cascade) |
| 6 | engagement-service | user-service | `GET /users/{id}` (author enrichment pour CommentDTO) | retry+timeout+CB | non |
| 7 | moderation-service | event-service | `GET /events/{id}` (visibilité + cascade pour self-report check) | retry+timeout+CB | non |
| 8 | moderation-service | user-service | `GET /users/{id}` (admin display) | retry+timeout+CB | non |

**Endpoints internes ajoutés au catalogue** (cf. [`backend/docs/internal-endpoints.md`](../backend/docs/internal-endpoints.md) — à mettre à jour Étape 8.3) :

- `GET /events/{eventId}/attendance-summary` (engagement-service, payload `AttendanceSummary` de `shared-domain-dtos`)
- `GET /events?ids=...&status=PUBLISHED` (event-service, bulk lookup, payload `List<EventDTO>`)
- `GET /events/{id}?check-co-org-of={uuid}` (event-service, ajoute le champ `coOrganizerOf: bool` au payload pour la cascade SCRUM-136 — alternative : 2 calls)

**Pattern uniforme par REST client** (déjà spécifié dans completion-spec Décision B, rappelé ici sans ambiguïté) :

```java
@RegisterRestClient(configKey = "<provider>-service")
@RegisterProvider(ch.unige.events.shared.tracing.RequestIdClientFilter.class)
@Path("/<resource>")
public interface <Provider>ServiceClient {

    @GET @Path("/{id}")
    @Retry(maxRetries = 3, delay = 200)
    @Timeout(2000)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "fallbackGetById")
    <Type> getById(@PathParam("id") <IdType> id);

    default <Type> fallbackGetById(<IdType> id) {
        return null; // ou Optional.empty(), ou domain-specific empty value
    }
}
```

```properties
# application.properties du consumer
quarkus.rest-client.<provider>-service.url=${<PROVIDER_NAMECASE>_SERVICE_URL:http://<provider>-service:8080}
```

**Tables qui restent cross-service** (lue via REST, plus jamais via JPA stub) :

| Lecteur | Tables consultées via REST | Service propriétaire |
|---|---|---|
| event-service | `users`, `attendances` | user-service, engagement-service |
| user-service | `events`, `attendances` | event-service, engagement-service |
| engagement-service | `events`, `users`, `event_co_organizers` | event-service, user-service, event-service |
| moderation-service | `events`, `users`, `event_co_organizers` | event-service, user-service, event-service |

**Suppression des 37 stubs JPA actuels** :

Les 37 `*Stub.java` actuels sont supprimés en Étape 4 selon le pattern :
- Les stubs qui pointent vers des tables maintenant possédées localement (post-merge) deviennent **entités réelles** ou disparaissent (le code call-site les remplace par l'entité réelle déjà importée).
- Les stubs cross-service restants (8 cas) sont remplacés par les 8 REST clients ci-dessus.

**Adresse** : SPEC-002, SPEC-005, SPEC-011, SPEC-013, SPEC-021, BUG-008, REFACTOR-001, REFACTOR-006, REFACTOR-009, REFACTOR-010, REFACTOR-013, REFACTOR-016, INFRA-005 (cat 1, 2, 4 de l'audit). Adressage de la totalité des findings reliés aux REST clients.

### Décision D — Tests legacy port : **35 sentinels par nom + couverture cible ≥ 80 %/70 % par service métier**

**Décision.** Les 1818 tests legacy supprimés au commit `b570c1b` (Étape 15 = `legacy-monolith` removal) sont récupérés depuis l'arbre git `41074e9:` (dernier commit avant suppression) selon la méthode déjà tranchée par la **completion-spec Décision E** (port `git show 41074e9: ... > <new-path>` + adaptation REST clients), avec les ajustements suivants imposés par la consolidation 14→5 :

1. **Mapping fichier source → fichier cible** mis à jour pour la nouvelle topologie (cf. Annexe B en fin de document) :
   - Tests event-domain (`EventServiceCoverageTest`, `EventResourceTest`, `AdminEventResourceTest`, `EventSearchResourceTest`, `RecurrenceGeneratorTest`, `EventCoOrganizerServiceCoverageTest`, `EventCoOrganizerResourceTest`, `EventStatsServiceCoverageTest`, `EventStatsResourceTest`, `FavoriteServiceCoverageTest`, `FavoriteResourceTest`, `EventViewServiceCoverageTest`, `EventViewResourceTest`, `ShareServiceCoverageTest`, `RedirectResourceTest`, `ShareResourceTest`) → **`event-service`**.
   - Tests user-domain (`UserServiceCoverageTest`, `UserResourceTest`, `FollowServiceCoverageTest`, `FollowResourceTest`, `FollowRequestResourceTest`, `CalendarServiceCoverageTest`, `IcsBuilderTest`, `CalendarResourceTest`, `UserCalendarTokenResourceTest`) → **`user-service`**.
   - Tests engagement-domain (`AttendanceServiceCoverageTest`, `AttendanceResourceTest`, `CommentServiceCoverageTest`, `CommentResourceTest`, `CommentDirectResourceTest`) → **`engagement-service`**.
   - Tests moderation-domain (`ReportServiceCoverageTest`, `ModerationCleanupServiceTest`, `ModerationCleanupCoverageTest`, `AdminReportResourceTest`) → **`moderation-service`**.
   - **`MyEventsServiceFanoutTest` (legacy me-aggregator)** : porter vers event-service (le path `/users/me/events` y vit désormais), adapter pour utiliser `@QuarkusTest` + `@TestSecurity` au lieu des mocks fan-out.

2. **35 sentinels** (cf. Annexe B section sentinels et completion-spec Décision E) **doivent ressortir verts par nom** :
   - **21 sentinels SCRUM-147 (event-service)** — recurrence, anti-oracles `getOccurrences_*`, cascades `cancel_parentDoesNotCascade*`, etc.
   - **6 sentinels SCRUM-138 (user-service)** — follow `findAcceptedFollowedIds_*`, `rejectRequest_*`, `getFollowers_privateProfileNonOwner_returns404_antiOracle`, `getPublicProfile_*`.
   - **8 sentinels SCRUM-144 (engagement-service)** — comment `prePersist_setsCreatedAt`, `post_eventDraftByNonCreator_returns404_antiOracle`, `post_eventBanned_returns404_antiOracle`, `post_replyToReply_returns422_repliesTooDeep`, `post_parentInOtherEvent_returns422_parentNotInEvent`, `post_unknownParent_returns404_parentNotFound`, `delete_byPendingCoOrganizer_returns403`, `delete_unknownComment_returns404_commentNotFound`.

   Validation : `for sentinel in <list>; do hit=$(grep -rln "void $sentinel" backend/services/*/src/test 2>/dev/null | wc -l) ; if [ "$hit" -lt 1 ] ; then echo "MISSING: $sentinel" ; fi ; done` doit retourner 0 ❌.

3. **Couverture jacoco cible** :

| Module | Lines % cible | Branches % cible |
|---|---|---|
| event-service | ≥ 80 % | ≥ 70 % |
| user-service | ≥ 80 % | ≥ 70 % |
| engagement-service | ≥ 80 % | ≥ 70 % |
| moderation-service | ≥ 80 % | ≥ 70 % |
| notification-service | (placeholder, sentinel only) | n/a |
| 10 shared libs | ≥ 95 % L (déjà 100 %) | ≥ 90 % B (déjà 100 %) |

4. **Pattern d'adaptation par fichier porté** (déjà en completion-spec, rappelé) :
   - Identifier les imports `ch.unige.events.entity.<X>` legacy → remplacer par les références entity locales du service cible (post-consolidation, beaucoup d'entités ont migré).
   - Identifier les références `<X>Stub.findByYyy(...)` → soit l'entité existe maintenant localement (post-consolidation), soit utiliser `@InjectMock <X>ServiceClient` (REST client mock, post-Étape 4).
   - Identifier les références à `<X>Service` direct (cross-service) → remplacer par mock du REST client.
   - **Conserver les noms de méthodes test exactement** — les 35 sentinels doivent ressortir par nom.
   - Si un test dépend d'un comportement supprimé (ex. `EventStub` write côté report → events.banned via Kafka), le re-écrire pour tester le nouveau path Kafka via in-memory connector.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Port `git show 41074e9:` + adaptation REST + sentinels par nom** | Préserve les sentinels (cible auditable) ; volume de cas-limites legacy ; effort proportionnel à la modification | Tests legacy peuvent référencer du code supprimé/refactoré → adaptations parfois lourdes | ✅ retenu |
| (b) Réécrire from scratch en partant de la liste sentinels | Tests propres, cohérents avec le nouveau modèle | Effort XXL, risque oubli cas-limites | ❌ |
| (c) Garder le statu quo (sentinels seuls — 14 ServiceIdentityResourceTest) | 0 effort | Coverage 14.7 % L mécaniquement bloque le quality gate | ❌ |

**Adresse** : TEST-001 (BLOCKER), TEST-002 à TEST-018 (cat 3 audit, intégralité). + TEST-016 (me-aggregator-service WireMock fan-out) **devient obsolète** post-consolidation (me-aggregator dissous) — l'item est **acté supprimé** (pas de port).

### Décision E — Pact + E2E : **4 pacts adaptés à la nouvelle topologie + 1 E2E happy path**

**Décision.** Création de `backend/contract-tests/` (module Maven jar, pas Quarkus) avec **4 pacts JSON** adaptés post-consolidation + `backend/e2e/E2EHappyPathTest.java` (RestAssured smoke `@QuarkusIntegrationTest`).

**4 pacts cibles post-consolidation** (vs 4 pacts de la completion-spec Décision J, dont 2 sont remappés) :

| # | Pact (consumer ↔ provider) | Path consommé | Anti-oracle / cascade testée |
|---|---|---|---|
| 1 | **engagement-service** ↔ **event-service** | `GET /events/{id}` | ISSUE-92 anti-oracle (404 sur DRAFT/CANCELLED non-créateur) |
| 2 | **engagement-service** ↔ **event-service** | `GET /events/{id}?check-co-org-of={uuid}` | Cascade SCRUM-136 (`coOrganizerOf: bool` dans la réponse) |
| 3 | **moderation-service** ↔ **event-service** | `GET /events/{id}` | Visibilité event pour le report admin |
| 4 | **user-service** ↔ **event-service** | `GET /events?ids=...&status=PUBLISHED` | Bulk lookup pour ICS feed calendar |

**Remappings vs completion-spec Décision J** :
- `share-service ↔ event-service` (pact original) → **disparaît** (share absorbée dans event-service ; appel local).
- `comment-service ↔ event-service` ISSUE-92 → **engagement-service ↔ event-service** (renommage du consumer).
- `comment-service ↔ co-organizer-service` SCRUM-136 → **engagement-service ↔ event-service** (co-org absorbé dans event-service ; le check est désormais au sein du même provider via param `?check-co-org-of=`).
- `report-service ↔ event-service` → **moderation-service ↔ event-service** (renommage).
- **+ Nouveau** : `user-service ↔ event-service` pour la feed ICS (calendar fusionné dans user-service consume bulk events).

**E2E happy path** : `backend/e2e/src/test/java/ch/unige/events/e2e/E2EHappyPathTest.java` — `@QuarkusIntegrationTest` qui :
1. `POST /api/users/me` (auto-création depuis JWT — bouchon `@TestSecurity(user="auth0|123", roles={})`).
2. `POST /api/events` avec body valide (DRAFT par défaut) → 201, récupère `id`.
3. `PATCH /api/events/{id}/publish` → 200, status PUBLISHED.
4. `GET /api/events/{id}` → 200, expose `creatorId` enrichi.
5. (option) Vérifier qu'un message Kafka `events.published` a été émis (via in-memory connector).

**Stack** :
- `au.com.dius.pact.consumer:junit5` (Pact JVM, sans Pact Broker tiers — pacts brokerless commitées dans `backend/contract-tests/pacts/`).
- `quarkus-junit5` + `rest-assured` pour E2E.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **4 pacts + 1 E2E** | Couvre les 3 anti-oracles (ISSUE-92, ISSUE-93 future, SCRUM-136) + 1 happy path complet | Effort M (≈ 1-2 jours) | ✅ retenu |
| (b) Pacts pour TOUS les couples consumer-provider (8 pacts) | Couverture exhaustive | XL ; plupart des contrats triviaux | ❌ over-engineering |
| (c) Pas de Pact (statu quo) | 0 effort | SPEC-006 audit | ❌ |

**Adresse** : SPEC-006, SPEC-018 (cat 1 audit).

---
### Décision F — CI matrix per-service : **5 cellules + 10 cellules shared libs + sonar.projectKey aligné**

**Décision.** Le YAML `.github/workflows/build.yml` est refondu en `strategy.matrix.service: [event, user, engagement, moderation, notification]` (5 cellules ; le placeholder notification reste car il a son sentinel `ServiceIdentityResourceTest`). Les 10 shared libs sont buildées dans un job parallèle `build-shared-libs` avec une seconde matrix.

**Résultat attendu** :
- `build-backend (event)` ✓
- `build-backend (user)` ✓
- `build-backend (engagement)` ✓
- `build-backend (moderation)` ✓
- `build-backend (notification)` ✓
- `build-shared-libs (shared-rate-limit)` ✓
- `build-shared-libs (shared-storage)` ✓
- `build-shared-libs (shared-api-error)` ✓
- `build-shared-libs (shared-domain-enums)` ✓
- `build-shared-libs (shared-domain-dtos)` ✓
- `build-shared-libs (shared-domain-projections)` ✓
- `build-shared-libs (shared-jaxrs)` ✓
- `build-shared-libs (shared-tracing)` ✓
- `build-shared-libs (shared-kafka-events)` ✓
- `build-shared-libs (shared-platform)` ✓
- `build-frontend` ✓ (inchangé)
- `pr-title-check` ✓

**Pattern par cellule du job `build-backend`** :

```yaml
build-backend:
  name: Build Backend (${{ matrix.service }})
  runs-on: ubuntu-latest
  strategy:
    fail-fast: false
    matrix:
      service: [event, user, engagement, moderation, notification]
  defaults:
    run:
      working-directory: backend
  steps:
    - uses: actions/checkout@v6
      with:
        fetch-depth: 0
    - uses: actions/setup-java@v5
      with:
        java-version: 21
        distribution: temurin
        cache: maven
    - name: Build & Test
      env:
        QUARKUS_CONTAINER_IMAGE_USERNAME: ${{ github.actor }}
        QUARKUS_CONTAINER_IMAGE_PASSWORD: ${{ secrets.GITHUB_TOKEN }}
      run: |
        ./mvnw -pl services/${{ matrix.service }}-service -am verify -B \
          -Dquarkus.container-image.build=true \
          -Dquarkus.container-image.push=${{ github.event_name == 'push' || github.event_name == 'pull_request' }} \
          -Dquarkus.container-image.registry=${{ env.REGISTRY }} \
          -Dquarkus.container-image.group=${{ github.repository_owner }} \
          -Dquarkus.container-image.tag=${{ env.IMAGE_TAG }} \
          -Dquarkus.container-image.additional-tags=${{ env.IMAGE_ADDITIONAL_TAGS }} \
          -Dquarkus.jib.base-jvm-image=eclipse-temurin:21-jre
    - name: SonarQube Scan
      env:
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: ./mvnw -pl services/${{ matrix.service }}-service sonar:sonar -B
    - name: Cleanup Docker images
      if: always()
      run: docker image prune -f
```

**Pré-requis DevOps (cf. [`backend/docs/devops-handoff.md`](../backend/docs/devops-handoff.md) item 1, mis à jour dans cette spec)** :
- 5 SonarCloud projects à créer manuellement : `unige-events-event-service`, `unige-events-user-service`, `unige-events-engagement-service`, `unige-events-moderation-service`, `unige-events-notification-service`.
- 10 SonarCloud projects pour les shared libs : `unige-events-shared-{rate-limit,storage,api-error,domain-enums,domain-dtos,domain-projections,jaxrs,tracing,kafka-events,platform}`.
- 1 secret `SONAR_TOKEN` (un seul partagé fonctionne avec SonarCloud) ou 15 secrets `SONAR_TOKEN_<NAME>` si politique stricte.
- **Sans cette action DevOps**, le step Sonar échoue avec « project not found » — c'est un blocker DevOps **attendu**, pas un fail backend (cf. completion-spec Décision H + Étape 12.5).

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Matrix 5 services + 10 shared libs + Sonar per-module** | Spec orig. décision 17/25 honorée ; dashboards par service ; build incrémental sur 15 cellules parallèles | Dépendance DevOps (15 SonarCloud projects à créer) | ✅ retenu |
| (b) Statu quo (single-job reactor-wide post-consolidation) | 0 effort (la conso passe de 24 à 15 modules, build reactor reste OK) | Pas de granularité Sonar par service ; SPEC-007/008/019, INFRA-010/011/012, HYGIENE-005 audit | ❌ |
| (c) Matrix sans projectKey override (tout vers `unige-events-backend`) | Simple | Pas de granularité Sonar — défense la spec orig. décision 25 | ❌ |

**Adresse** : SPEC-007, SPEC-008, SPEC-019, INFRA-007, INFRA-010, INFRA-011, INFRA-012, HYGIENE-005 (cat 1 + 6 + 7 + 10 audit).

### Décision G — Doublon openapi `/events/{id}/view` : **annulation de la dérogation Q de la completion-spec — `git diff openapi/` reste à 0**

**Décision.** La dérogation **Q** de la completion-spec proposait de supprimer le doublon `POST /events/{id}/view` (lignes ~3482-3513 de `openapi/openapi.yaml`, le bloc d'erreurs le plus pauvre) — déviation acceptée à 32 lignes du diff `openapi/`. Cette dérogation **n'a pas été appliquée** dans la completion exécutée, et la présente spec **annule** cette dérogation au profit de l'invariant strict `git diff --shortstat origin/main HEAD -- openapi/` = **0 ligne**.

**Justification** :
- Le doublon n'introduit **aucun bug runtime** : les codecs OpenAPI lisent le second bloc et ignorent silencieusement le premier (pas d'ambiguïté de routing — le path JAX-RS est unique).
- Modifier `openapi.yaml` impose une **coordination frontend** (le contrat est partagé). Sans changement applicatif côté frontend, toute édition expose à un risque de drift de typage ou de regen breakage côté frontend.
- L'invariant absolu zero contract change est plus précieux pour la review humaine d'Elie + Agon que la propreté esthétique du fichier YAML.
- Le doublon peut être nettoyé dans une PR future dédiée avec coordination frontend explicite (hors scope migration backend).

**Application** : aucune. Le fichier `openapi/openapi.yaml` reste **strictement intact**. L'exécuteur **NE TOUCHE PAS** ce fichier dans la présente spec. Si une étape semble en avoir besoin (ex. pour exposer un endpoint interne), c'est qu'on l'enfile dans `backend/docs/internal-endpoints.md` (cf. completion-spec Décision Q maintenue sur ce point — endpoints internes hors openapi).

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Annuler la dérogation Q — openapi diff = 0 strict** | Invariant absolu, zero contract change, 0 coordination frontend | Le doublon reste dans `openapi.yaml` (cosmétique) | ✅ retenu |
| (b) Appliquer la dérogation Q — supprimer le doublon (diff 32 lignes) | Cleanup esthétique du contract | Touche `openapi/` (invariant absolu cassé) ; nécessite review frontend | ❌ |

**Adresse** : INFRA-001, INFRA-004 (cat 6 audit) — formellement déférés à une PR frontend coordonnée future.

### Décision H — me-aggregator-service : **SUPPRIMÉ (absorbé dans event-service)**

**Décision.** `me-aggregator-service` est **fusionné dans event-service** (cf. Décision A — sous-étape 2.2.6 dans le plan). Le service disparaît du reactor + Helm + Kong.

**Justification** :
- Le service ne sert qu'**1 endpoint** (`GET /users/me/events` — events créés par le caller). C'est un trivial wrapper sur une query Event filtrée par `creatorId = caller`.
- Le pattern BFF (Backend For Frontend) est justifié quand il y a **fan-out multi-domaine** réel — mélanger des données de 3+ services downstream pour livrer une vue agrégée. Ce n'est pas le cas ici : `/users/me/events` est strictement event-domain.
- Les autres `/users/me/*` paths que le BFF aurait pu agréger (`/me/attendances`, `/me/favorites`, `/me/follow-requests`, `/me/co-organizer-invitations`, `/me/calendar-token`, `/me/banner`, `/me/image`) routent **directement** vers leur service domain (cf. table Kong post-consolidation dans Architecture cible).
- Garder le BFF aurait imposé un REST client supplémentaire (me-aggregator → event-service) pour zéro bénéfice.

**Conséquences post-merge** :
- `MyEventsResource.java` (legacy me-aggregator) est porté dans `event-service/src/main/java/ch/unige/events/event/me/MyEventsResource.java` (sous-package `me/` pour la propreté). Path inchangé : `@Path("/users/me/events")`.
- Kong route `/users/me/events$` change d'upstream : `me-aggregator-service:8080` → `event-service:8080`.
- Helm `templates/me-aggregator-service/` supprimé (deployment + service).
- Module `services/me-aggregator-service` supprimé du parent POM + dossier physique.
- `unige-events-me-aggregator-service` image GHCR : pas de nouvelle release, le tag historique reste mais n'est plus pulled.
- SonarCloud project `unige-events-me-aggregator-service` : devient orphelin côté DevOps (cf. devops-handoff Étape 8.5 update).

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Supprimer me-aggregator (absorption event-service)** | -1 service, -1 pod, -1 SonarCloud project, -1 REST client jamais nécessaire ; cohérence DDD (events created by me = event-domain) | Le pattern BFF disparaît visuellement ; brief Agon ne mentionne pas explicitement BFF mais Décision 4 spec orig. l'évoque | ✅ retenu |
| (b) Garder me-aggregator + élargir son scope (vrai BFF fan-out sur tous les `/me/*`) | Pédagogie BFF claire | Effort de réécriture XL ; les autres `/me/*` sont déjà fonctionnels via routes directes ; ferait régresser les latences | ❌ |
| (c) Garder me-aggregator scope minimal actuel | 0 effort | Sur-décomposition persiste ; le service a 0 valeur | ❌ |

**Adresse** : SPEC-009 (me-aggregator devait devenir vrai BFF — décision inverse plus saine), REFACTOR-016 (me-aggregator → event), nouveau (consolidation).

### Décision I — Branche, workflow git, granularité commits

**Décision.** Identique aux specs précédentes — rappel sans modification :

- **Branche persistante** : `refactor(backend)--migrate-to-microservices`. **NE PAS** créer de nouvelle branche, NE PAS modifier `main`, NE PAS modifier les autres branches feature (`feature/s7-recurrence`, etc.). Tous les commits de la finalisation s'ajoutent au-dessus de HEAD `5346342…`.
- **Granularité** : 1 commit par sous-étape numérotée `N.M`. Exception consolidation (Étape 2) : 1 commit par paire de merge, 100-300 lignes diff acceptable. Exception tests legacy port (Étape 5) : 1 commit par classe portée OU 1 commit par groupe de classes liées (sentinels SCRUM-XXX) — exécuteur juge selon volumétrie.
- **Pas de squash, pas de force push, pas de `--no-verify`, pas de `--no-gpg-sign`, pas de `--amend`** sur du commit pushé.
- **Push après chaque sous-étape verte** : `git push origin 'refactor(backend)--migrate-to-microservices'`.
- **Watch CI groupé par étape majeure** : à la fin de chaque Étape ≥ 2, `gh pr checks 158 --watch` jusqu'à terminaison. Tous les checks doivent passer (Build BE matrix×5, Build FE, Sonar matrix×15, Deploy Preview, PR Title Check) — sauf Sonar qui peut échouer spécifiquement sur « project not found » (blocker DevOps documenté en Décision F).
- **Si CI échoue (hors Sonar project not found)** : `gh run view <RUN_ID> --log-failed` → fix root cause → nouveau commit additif → push → re-watch. Jamais de skip silencieux.
- **Mise à jour `sprint-context.md` Étape 20** : un patch incrémental après chaque étape, **sans commit dédié à chaque step** (regroupé en commit final d'Étape 8.5).
- **Mise à jour PR body via `gh pr edit 158 --body-file`** : à la toute fin (Étape 8.6), pas en milieu de parcours.
- **Pas de merge PR #158** — Elie merge lui-même quand il valide.

**Commit message types autorisés** : `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `perf`. Scope libre pour `chore/fix/docs/style/test/ci`. Pour `feat/refactor/perf`, le scope DOIT être `scrum-XXX` ou `backend` (workaround documenté de `pr-title-check.yml` — cf. completion-spec § Bug subtil).

**Adresse** : SPEC-017 (cat 1 audit, déjà adressé dans completion-spec).

### Décision J — Frontière DevOps : **inchangée — 7 items hors scope formalisés dans devops-handoff.md**

**Décision.** Les 7 items DevOps tranchés dans la **completion-spec Décision V** restent inchangés et hors scope de la finalisation :

1. Création de 5 SonarCloud projects per-service + 10 shared libs (mise à jour : 13+10 = 23 → 5+10 = 15 projets cibles ; cf. devops-handoff item 1 mis à jour Étape 8.5).
2. Cluster Kafka prod-grade (RF=3, partitions ≥ 3, ISR ≥ 2, durabilité acks=all).
3. Schemas-per-service via Flyway physique séparé (déféré S9+ par Décision C de la completion-spec — inchangé).
4. NetworkPolicies K8s pour isoler le trafic service-to-service.
5. Domaines / certs prod / Cloudflare tunnel preview.
6. Secrets Doppler `DB_PASSWORD`, `OIDC_*`, `S3_*`, `KAFKA_BOOTSTRAP_SERVERS`, `FRONTEND_URL`, `TZ=Europe/Zurich`.
7. Production-grade Kong (DB-mode, OpenTelemetry, plugin `rate-limiting` policy=redis cluster-wide).

**L'exécuteur autonome ne touche à AUCUN de ces 7 items.** Toute action de finalisation qui dépasse « config YAML lue par Helm » doit être déférée DevOps avec note explicite dans `devops-handoff.md`.

**Update prévu** dans la présente spec (Étape 8.5) :
- Item 1 : 13 services + 8 libs nouvelles → **5 services + 10 libs** = 15 SonarCloud projects à créer. Liste mise à jour avec les noms post-consolidation.
- Item 1 : ajout d'une note explicite « les anciens SonarCloud projects `unige-events-{share,view,favorite,calendar,follow,comment,co-organizer,attendance,report,stats,me-aggregator}-service` deviennent orphelins post-consolidation — DevOps peut les archiver ou les laisser ; aucun blocker. »

**Adresse** : Cat 11 informational audit + Décision V de la completion-spec (mise à jour mineure).

---

## Architecture cible post-finalization

### Diagramme (texte ASCII)

```
                                    ┌──────────────┐
                                    │   Frontend   │
                                    │  (React 19)  │
                                    └──────┬───────┘
                                           │ HTTPS
                                           ▼
                                    ┌──────────────┐
                                    │  Cloudflare  │  (DevOps)
                                    │   Ingress    │
                                    └──────┬───────┘
                                           ▼
                                    ┌──────────────────────────────────┐
                                    │  Kong DB-less (replicas: 2)      │
                                    │  - cors, correlation-id,         │
                                    │    prometheus (global)           │
                                    │  - rate-limiting policy=local    │
                                    │    (per-route)                   │
                                    └──┬───────────────────────────────┘
                                       │
       ┌───────────────────────────────┼───────────────────────────────────┐
       ▼                               ▼                                   ▼
  ┌────────────┐                ┌────────────┐                      ┌────────────┐
  │   user-    │ REST sync     │  event-    │   REST sync          │engagement- │
  │  service   │◀──────────────│  service   │◀─────────────────────│  service   │
  │ (replica:1)│                │ (replica:1)│                      │ (replica:1)│
  └─────┬──────┘                └─────┬──────┘                      └─────┬──────┘
        │ Kafka producer              │ Kafka producer                     │ Kafka producer
        │ users.{followed,            │ events.{published,                 │ comments.created
        │ follow-requested,           │ cancelled,expired}                 │
        │ follow-accepted}            │ ◀─── consumes events.banned        │
        │                             │                                   │
        │                       ┌─────▼──────────┐                        │
        │                       │  Kafka KRaft   │                        │
        │                       │ (single-broker)│                        │
        │                       │  10 topics     │                        │
        │                       └────────┬───────┘                        │
        │                                │                                │
        │           ┌────────────────────┼────────────────────┐           │
        ▼           ▼                    ▼                    ▼           ▼
  ┌──────────────────────┐         ┌──────────────┐                ┌──────────────┐
  │   moderation-        │         │ notification-│                │      ⊥       │
  │   service            │ Kafka   │  service     │                │ (no consumer │
  │ (replicas:1 strict)  │ producer│ (replicas:0) │                │  here yet)   │
  │   ModerationCleanup  │ events. │ scaffold     │                │              │
  │   Job (cron 03:00)   │ banned  │ SCRUM-99     │                │              │
  └──────────────────────┘         └──────────────┘                └──────────────┘


  Shared Libs (consumed by all services, all 100% L coverage):
  ┌──────────────────────────────────────────────────────────────────────────┐
  │ shared-rate-limit (existant) │ shared-storage (existant)                 │
  │ shared-api-error             │ shared-domain-enums                       │
  │ shared-domain-dtos           │ shared-domain-projections                 │
  │ shared-jaxrs                 │ shared-tracing                            │
  │ shared-kafka-events          │ shared-platform                           │
  └──────────────────────────────────────────────────────────────────────────┘

  PostgreSQL: une seule instance, schéma `public` partagé (Décision C de la
  completion-spec → DB-per-service S9+).
  S3 (MinIO): 1 bucket `unige-events`, accédé par user-service + event-service
  via shared-storage.
```

### Tableau récapitulatif post-finalization

| Service | Endpoints `@Path` | Tables possédées | Schedulers | Kafka producer | Kafka consumer | REST clients out |
|---|---|---|---|---|---|---|
| **event-service** | `/events*`, `/admin/events*`, `/events/search`, `/events/featured`, `/events/{id}/image`, `/events/{id}/share`, `/s/{shortCode}`, `/events/{id}/view`, `/events/{id}/favorite`, `/users/me/favorites`, `/events/{id}/co-organizers/*`, `/users/me/co-organizer-invitations`, `/events/{id}/stats`, `/users/me/events` | `events`, `event_tags`, `event_views`, `favorites`, `event_co_organizers` | EventExpirationJob (every 1h, replicas:1 strict) | events.{published,cancelled,expired} ; co-organizers.{invited,accepted} | events.banned | user-service, engagement-service |
| **user-service** | `/users/me`, `/users/{id}`, `/users/me/image`, `/users/me/banner`, `/users/{id}/follow*`, `/follow-requests/*`, `/users/me/follow-requests`, `/users/me/calendar-token*`, `/calendar/{token}.ics` | `users`, `user_interests`, `follows` | (aucun) | users.{followed,follow-requested,follow-accepted} | (aucun) | event-service, engagement-service |
| **engagement-service** | `/events/{id}/attend*`, `/users/me/attendances`, `/users/me/participations`, `/events/{id}/comments`, `/comments/{id}` | `attendances`, `comments` | (aucun) | comments.created | (aucun) | event-service, user-service |
| **moderation-service** | `/events/{id}/report`, `/admin/reports*` | `reports` | ModerationCleanupJob (cron 03:00 Europe/Zurich, replicas:1 strict) | events.banned | (aucun) | event-service, user-service |
| **notification-service** | (placeholder) | aucune | aucun | (futur SCRUM-99) | (futur SCRUM-99) | (aucun) |

**Total** : 5 services × 1 pod prod + 1 placeholder × 0 pod = **4 pods backend en runtime** (vs 13 pré-finalisation, gain ~70 %).

### Tableau des 8 REST clients (récap consolidé)

| # | Consumer | Provider | Endpoints consommés | Resilience |
|---|---|---|---|---|
| 1 | event-service | user-service | `GET /users/{id}` | retry+timeout+CB+fallback `null` |
| 2 | event-service | engagement-service | `GET /events/{eventId}/attendance-summary` | retry+timeout+CB |
| 3 | user-service | event-service | `GET /events?ids=...&status=PUBLISHED` (bulk) | retry+timeout+CB |
| 4 | user-service | engagement-service | `GET /users/{id}/attendances?status=ATTENDING` | retry+timeout+CB |
| 5 | engagement-service | event-service | `GET /events/{id}?check-co-org-of={uuid}` | retry+timeout+CB |
| 6 | engagement-service | user-service | `GET /users/{id}` | retry+timeout+CB |
| 7 | moderation-service | event-service | `GET /events/{id}` | retry+timeout+CB |
| 8 | moderation-service | user-service | `GET /users/{id}` | retry+timeout+CB |

**Endpoints internes nouveaux à exposer côté provider** (catalogue [`backend/docs/internal-endpoints.md`](../backend/docs/internal-endpoints.md) à mettre à jour Étape 8.3) :

| Path | Service propriétaire | Payload réponse |
|---|---|---|
| `GET /events/{eventId}/attendance-summary` | engagement-service | `AttendanceSummary` (shared-domain-dtos) |
| `GET /events?ids=...&status=PUBLISHED` | event-service | `List<EventDTO>` (bulk) |
| `GET /events/{id}?check-co-org-of={uuid}` | event-service | `EventDTO` + champ `coOrganizerOf: bool` |
| `GET /users/{id}/attendances?status=ATTENDING` | engagement-service | `List<AttendanceDTO>` (existant public, réutilisé interne) |

### Tableau Kafka final (10 topics × producteur(s) × consommateur(s) — post-consolidation)

| Topic | Producteur | Consommateur | Partition key | Status |
|---|---|---|---|---|
| `events.published` | event-service | (notification SCRUM-99 futur) | `eventId` | ✅ inchangé |
| `events.cancelled` | event-service | (notification futur) | `eventId` | ✅ inchangé |
| `events.expired` | event-service | (notification futur) | `eventId` | ✅ inchangé |
| `events.banned` | moderation-service (renommé depuis report-service) | event-service (idempotent apply) + (notification futur) | `eventId` | ✅ après rename |
| `users.followed` | user-service (post-merge follow→user) | (notification futur) | `followedId` | ✅ après merge |
| `users.follow-requested` | user-service | (notification futur) | `followedId` | ✅ après merge |
| `users.follow-accepted` | user-service | (notification futur) | `followedId` | ✅ après merge |
| `comments.created` | engagement-service (renommé depuis attendance-service + absorption comment) | (notification futur) | `eventId` | ✅ après merge |
| `co-organizers.invited` | event-service (post-merge co-organizer→event) | (notification futur) | `userId` | ✅ après merge |
| `co-organizers.accepted` | event-service | (notification futur, event-service cache futur) | `eventId` | ✅ après merge |

**Producteurs post-consolidation** : 4 services × 9 topics. 1 consumer (event-service ← events.banned).

### Tableau Kong routes post-consolidation

| Path | HTTP method | Service upstream | Plugins par-route |
|---|---|---|---|
| `/api/users/me`, `/api/users/me/image`, `/api/users/me/banner`, `/api/users/me/calendar-token*`, `/api/users/me/follow-requests`, `/api/users/{uuid}`, `/api/users/{uuid}/follow*`, `/api/users/{uuid}/(followers\|following)`, `/api/follow-requests/*`, `/api/calendar/{token}.ics` | GET, POST, PUT, PATCH, DELETE | **user-service** | `rate-limiting` `policy: local`, `minute: 30` (sur `/api/users/[^/]+/follow$` POST) |
| `/api/events*`, `/api/admin/events*`, `/api/events/search`, `/api/events/featured`, `/api/events/{id}/image`, `/api/events/{id}/share`, `/api/s/{shortCode}`, `/api/events/{id}/view`, `/api/events/{id}/favorite`, `/api/users/me/favorites`, `/api/events/{id}/co-organizers/*`, `/api/users/me/co-organizer-invitations`, `/api/events/{id}/stats`, `/api/users/me/events` | GET, POST, PUT, PATCH, DELETE | **event-service** | `rate-limiting` `policy: local`, `minute: 10` (sur `/api/events$` POST) |
| `/api/events/{id}/attend*`, `/api/users/me/attendances`, `/api/users/me/participations`, `/api/events/{id}/comments`, `/api/comments/{id}` | GET, POST, PUT, PATCH, DELETE | **engagement-service** | `rate-limiting` `policy: local`, `minute: 10` (sur `/api/events/(?:\d+)/comments$` POST) |
| `/api/events/{id}/report`, `/api/admin/reports*` | GET, POST, PATCH | **moderation-service** | (aucun par-route) |

(notification-service : aucune route Kong active — replicas:0.)

### Tableau couverture cible jacoco par module

| Module | Lines % cible | Branches % cible | Sentinels obligatoires |
|---|---|---|---|
| 10 shared libs | ≥ 95 % L (déjà 100 %) | ≥ 90 % B (déjà 100 %) | — |
| event-service | ≥ 80 % | ≥ 70 % | **21 sentinels SCRUM-147** + tests share/view/favorite/co-organizer/stats portés |
| user-service | ≥ 80 % | ≥ 70 % | **6 sentinels SCRUM-138** + tests follow/calendar portés |
| engagement-service | ≥ 80 % | ≥ 70 % | **8 sentinels SCRUM-144** + tests attendance/comment portés |
| moderation-service | ≥ 80 % | ≥ 70 % | tests report + ModerationCleanup portés |
| notification-service | n/a (sentinel `ServiceIdentityResourceTest` only) | n/a | — |

**Total sentinels obligatoires** : 21 + 6 + 8 = **35 sentinels SCRUM-138/139/144/147** (cf. completion-spec Décision E + Annexe E).

---
## Plan d'implémentation par étape (ORDRE STRICT)

### Étape 0 — Pré-flight

**Objectif** : valider l'état initial avant de commencer. Lecture obligatoire des 3 specs antérieures + audit du repo.

**Actions** :

1. `git fetch origin --quiet`
2. `git checkout 'refactor(backend)--migrate-to-microservices'` (si pas déjà dessus)
3. `git pull origin 'refactor(backend)--migrate-to-microservices' --ff-only`
4. `git rev-parse HEAD` → noter le SHA actuel (devrait être `5346342`+ ou descendant).
5. **Lecture intégrale** :
   - `specs_archives/specs_claude/specs_microservices_migration.md` (1884 lignes)
   - `specs_archives/audit_pr158_microservices_migration.md` (2143 lignes — au moins TL;DR + cat 1, 2, 3, 4, 5)
   - `specs_archives/specs_claude/specs_microservices_migration_completion.md` (2794 lignes — au moins Décisions A-Y + plan Étape 5/7/8/12.1)
6. **Audit repo** :
   ```bash
   ls /workspace/backend/services/ | wc -l                                       # 24 attendu (14 services + 10 libs)
   find /workspace/backend/services -name '*Stub.java' -not -path '*/target/*' \
       | wc -l                                                                  # ~37 stubs attendus
   git diff --shortstat origin/main HEAD -- frontend/                            # 0 lignes (invariant)
   git diff --shortstat origin/main HEAD -- openapi/                             # 0 lignes (invariant strict, cf. Décision G)
   ```
7. **Build baseline** : `cd backend && ./mvnw verify -DskipITs` → doit être SUCCESS sur 24 modules. Sinon il y a un problème pré-existant à investiguer **avant** de commencer.
8. `gh pr view 158 --json state,mergeable,statusCheckRollup` → état CI noté.

**Pas de commit pour Étape 0** — c'est de la lecture / vérification.

**Si un check échoue** : reporter le problème via un commit `chore(backend): pre-flight blocker — <description>` + abandon Étape 0 et investigation. Sinon procéder.

### Étape 1 — Documentation préparatoire (consolidation-plan.md)

**Objectif** : créer un document préparatoire qui mappe explicitement service source → service cible + mouvements de tables/endpoints/topics, pour servir de contrat de la consolidation Étape 2. Ce document est un **guide pour l'exécuteur** + **trace pour le reviewer**.

#### Étape 1.1 — Création de `backend/docs/consolidation-plan.md`

**Fichier nouveau** : `backend/docs/consolidation-plan.md`

**Contenu requis** (~300 lignes) :

```markdown
# Plan de consolidation 14→5 services — finalization PR #158

> Document de contrat pour la consolidation Étape 2 de [`specs_microservices_migration_finalization.md`](../../specs_archives/specs_claude/specs_microservices_migration_finalization.md).
> Mis à jour : <date>. Auteur : Claude Code session finalization.

## TL;DR

13 services métiers actifs → 4 services métiers actifs + 1 placeholder. 9 merges + 2 renames. ~12 commits estimés sur Étape 2.

## Mapping service source → service cible

(reproduire le tableau complet de la spec finalization Décision A justification rigoureuse)

## Mouvements par table

| Table | Owner avant | Owner après | Notes |
|---|---|---|---|
| events | event-service | event-service (inchangé) | — |
| event_tags | event-service | event-service | — |
| event_views | view-service | event-service | merge 2.2.2 |
| favorites | favorite-service | event-service | merge 2.2.3 |
| event_co_organizers | co-organizer-service | event-service | merge 2.2.4 |
| users | user-service | user-service (inchangé) | — |
| user_interests | user-service | user-service | — |
| follows | follow-service | user-service | merge 2.3.1 |
| (none) | calendar-service | user-service | merge 2.3.2 (lit/écrit users.calendar_token) |
| attendances | attendance-service | engagement-service | rename 2.1.1 |
| comments | comment-service | engagement-service | merge 2.4.1 |
| reports | report-service | moderation-service | rename 2.1.2 |

## Mouvements par endpoint Kong

(reproduire le tableau Kong routes post-consolidation de la spec finalization)

## Mouvements par topic Kafka producer

| Topic | Producer avant | Producer après | Sous-étape |
|---|---|---|---|
| events.{published,cancelled,expired} | event-service | event-service (inchangé) | — |
| events.banned | report-service | moderation-service | rename 2.1.2 |
| users.{followed,follow-requested,follow-accepted} | follow-service | user-service | merge 2.3.1 |
| comments.created | comment-service | engagement-service | merge 2.4.1 |
| co-organizers.{invited,accepted} | co-organizer-service | event-service | merge 2.2.4 |

(Le consumer events.banned reste dans event-service — inchangé.)

## Mouvements par fichier Helm/Kong/POM

(reproduire l'Annexe D de la spec finalization)

## Risques de régression

(reproduire la table Risques et mitigations § Étape 2 de la spec finalization)
```

**Validation** :
- Le fichier compile en MD valide (`grip` ou aperçu VSCode).
- Tous les services source apparaissent dans la table mapping.
- Tous les topics Kafka apparaissent.

**Commit** : `docs(backend): add consolidation-plan.md for 14→5 services merge contract (Étape 1.1)`

#### Étape 1.2 — Mise à jour de `backend/docs/devops-handoff.md` item 1

**Fichier modifié** : `backend/docs/devops-handoff.md`

**Patch** : remplacer la section item 1 (« Création de 13 SonarCloud projects per-service ») par :

```markdown
## 1. Création de 5 SonarCloud projects per-service + 10 shared libs

**Statut backend** : ✅ YAML CI matrix livré (Étape 7 de la spec finalization) ; sonar.projectKey per-module livré (Étape 12.2 de la completion spec).

**Action attendue côté DevOps** :

* Créer manuellement, via la UI SonarCloud, 5 projets sous l'organisation `unige-pinfo6-2026` :
  - `unige-events-event-service`
  - `unige-events-user-service`
  - `unige-events-engagement-service`
  - `unige-events-moderation-service`
  - `unige-events-notification-service`
* Plus 10 projets pour les shared libs : `unige-events-shared-rate-limit`, `unige-events-shared-storage`,
  `unige-events-shared-api-error`, `unige-events-shared-domain-enums`, `unige-events-shared-domain-dtos`,
  `unige-events-shared-domain-projections`, `unige-events-shared-jaxrs`, `unige-events-shared-tracing`,
  `unige-events-shared-kafka-events`, `unige-events-shared-platform`.
* Ajouter au repo GitHub (Settings → Secrets) : `SONAR_TOKEN` (un seul partagé suffit avec SonarCloud).

**Note de transition** : les anciens SonarCloud projects (`unige-events-{share,view,favorite,calendar,follow,comment,co-organizer,attendance,report,stats,me-aggregator}-service`) deviennent **orphelins** post-consolidation 14→5. DevOps peut les archiver ou les laisser ; aucun blocker. Aucune CI n'écrit plus dedans.

**Sans cette action**, le workflow CI matrix échoue côté Sonar à la première exécution avec « project not found » — c'est un blocker DevOps **attendu** documenté ; pas un fail backend.

**Justification du report** : nécessite SonarCloud admin UI (hors scope code).
```

**Commit** : `docs(backend): update devops-handoff.md item 1 for 14→5 SonarCloud projects post-consolidation (Étape 1.2)`

#### Récap fin Étape 1

**Sous-étapes commitées** : 1.1, 1.2 (2 commits).
**Validation finale** :
```bash
cd backend && ./mvnw verify -DskipITs  # SUCCESS — pas de change code
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch              # tous verts (sauf Sonar gate inchangé)
```

### Étape 2 — Consolidation par paire (12 sous-étapes)

**Objectif** : exécuter les 11 merges + renames + cleanup pour atteindre la topologie 5 services. Chaque sous-étape = 1 commit (sauf cleanup final 2.5.x qui groupe).

#### Étape 2.1 — Renames de services existants (2 commits)

##### Étape 2.1.1 — Rename `attendance-service` → `engagement-service`

**Fichiers/dossiers à modifier** :

```bash
# 1. Renommer le dossier
git mv backend/services/attendance-service backend/services/engagement-service

# 2. POM enfant
sed -i 's|<artifactId>attendance-service</artifactId>|<artifactId>engagement-service</artifactId>|;
        s|<name>UNIGE Events — attendance-service</name>|<name>UNIGE Events — engagement-service</name>|;
        s|unige-events-attendance-service|unige-events-engagement-service|g' \
    backend/services/engagement-service/pom.xml

# 3. Parent POM
sed -i 's|<module>services/attendance-service</module>|<module>services/engagement-service</module>|' \
    backend/pom.xml

# 4. application.properties
sed -i 's|unige-events-attendance-service|unige-events-engagement-service|;
        s|quarkus.application.name=attendance-service|quarkus.application.name=engagement-service|' \
    backend/services/engagement-service/src/main/resources/application.properties

# 5. Helm dossier
git mv k8s/chart/templates/attendance-service k8s/chart/templates/engagement-service

# 6. Helm Deployment + Service ClusterIP — replace nom partout
sed -i 's|attendance-service|engagement-service|g' \
    k8s/chart/templates/engagement-service/deployment.yaml \
    k8s/chart/templates/engagement-service/service.yaml

# 7. Kong route — renommer le bloc + service upstream
sed -i 's|- name: attendance-service|- name: engagement-service|;
        s|http://attendance-service:8080|http://engagement-service:8080|' \
    k8s/chart/templates/kong/configmap-routes.yaml
```

**NB** : les sources Java sous `ch.unige.events.attendance.*` **restent telles quelles** dans cette sous-étape. Le rename des packages est **différé** à la sous-étape de merge avec comment-service (2.4.1) où on aura besoin d'organiser les packages côté `engagement` au sens commun.

**Patch additionnel — `quarkus.application.name`** :

Vérifier que `application.properties` contient bien :
```properties
quarkus.application.name=engagement-service
```
Sinon l'ajouter (ce sera consommé par `shared-platform.ServiceIdentityResource` une fois cette lib consommée — sinon hardcoded dans `ServiceIdentityResource` local actuel).

**Validation** :
```bash
cd backend && ./mvnw -pl services/engagement-service -am test -DskipITs
# SUCCESS attendu — les tests existants (sentinel + autres) doivent passer
```

**Commit** : `refactor(backend): rename attendance-service → engagement-service (Étape 2.1.1)`

##### Étape 2.1.2 — Rename `report-service` → `moderation-service`

Pattern identique à 2.1.1, substituant `report` → `moderation` :

```bash
git mv backend/services/report-service backend/services/moderation-service
sed -i 's|<artifactId>report-service</artifactId>|<artifactId>moderation-service</artifactId>|;
        s|<name>UNIGE Events — report-service</name>|<name>UNIGE Events — moderation-service</name>|;
        s|unige-events-report-service|unige-events-moderation-service|g' \
    backend/services/moderation-service/pom.xml
sed -i 's|<module>services/report-service</module>|<module>services/moderation-service</module>|' \
    backend/pom.xml
sed -i 's|unige-events-report-service|unige-events-moderation-service|;
        s|quarkus.application.name=report-service|quarkus.application.name=moderation-service|' \
    backend/services/moderation-service/src/main/resources/application.properties
git mv k8s/chart/templates/report-service k8s/chart/templates/moderation-service
sed -i 's|report-service|moderation-service|g' \
    k8s/chart/templates/moderation-service/deployment.yaml \
    k8s/chart/templates/moderation-service/service.yaml
sed -i 's|- name: report-service|- name: moderation-service|;
        s|http://report-service:8080|http://moderation-service:8080|' \
    k8s/chart/templates/kong/configmap-routes.yaml
```

**Validation** : `./mvnw -pl services/moderation-service -am test -DskipITs` SUCCESS.

**Commit** : `refactor(backend): rename report-service → moderation-service (Étape 2.1.2)`

#### Étape 2.2 — Merges dans event-service (6 commits)

Pour chaque merge `<X>-service` → `event-service`, appliquer le pattern Décision B systématiquement.

##### Étape 2.2.1 — Merge `share-service` → `event-service`

**Pattern de merge** :

```bash
# 1. Move des sources Java
mkdir -p backend/services/event-service/src/main/java/ch/unige/events/event/share
git mv backend/services/share-service/src/main/java/ch/unige/events/share/* \
       backend/services/event-service/src/main/java/ch/unige/events/event/share/

# 2. Rename packages dans tous les .java déplacés
find backend/services/event-service/src/main/java/ch/unige/events/event/share/ -name '*.java' -exec \
    sed -i 's|^package ch\.unige\.events\.share\.|package ch.unige.events.event.share.|g; \
            s|^import ch\.unige\.events\.share\.|import ch.unige.events.event.share.|g' {} +

# 3. Move des tests (s'il y en a hors le sentinel ServiceIdentityResourceTest)
mkdir -p backend/services/event-service/src/test/java/ch/unige/events/event/share
git mv backend/services/share-service/src/test/java/ch/unige/events/share/* \
       backend/services/event-service/src/test/java/ch/unige/events/event/share/ 2>/dev/null || true
# Note: ServiceIdentityResourceTest existant dans share-service est un test trivial qui ne sert plus →
# il sera SUPPRIMÉ (event-service a déjà son propre sentinel pour quarkus.application.name=event-service).
rm -f backend/services/event-service/src/test/java/ch/unige/events/event/share/ServiceIdentityResourceTest.java

# Rename packages tests
find backend/services/event-service/src/test/java/ch/unige/events/event/share/ -name '*.java' -exec \
    sed -i 's|^package ch\.unige\.events\.share\.|package ch.unige.events.event.share.|g; \
            s|^import ch\.unige\.events\.share\.|import ch.unige.events.event.share.|g' {} + 2>/dev/null || true

# 4. Merge application.properties — récupérer les blocs spécifiques (ici: app.frontend.url, container-image.name)
# Le SOURCE share-service application.properties a:
#   - quarkus.container-image.name=unige-events-share-service (à JETER, on garde event-service)
#   - app.frontend.url=${FRONTEND_URL:http://localhost:5173} (à GARDER si pas déjà dans event-service)
# Vérifier dans event-service application.properties :
grep -q '^app.frontend.url=' backend/services/event-service/src/main/resources/application.properties || \
    echo 'app.frontend.url=${FRONTEND_URL:http://localhost:5173}' >> backend/services/event-service/src/main/resources/application.properties

# 5. Suppression Helm Deployment
git rm -r k8s/chart/templates/share-service/

# 6. Mise à jour Kong route — fusionner le bloc share-service dans event-service
# (manuel via Edit — déplacer les `routes:` du bloc share-service dans le bloc event-service,
# changer `service:` upstream, supprimer le bloc share-service)

# 7. Suppression du module dans le parent POM
sed -i '/<module>services\/share-service<\/module>/d' backend/pom.xml

# 8. Suppression du dossier source (devrait être vide après les git mv)
git rm -r backend/services/share-service/

# 9. POM event-service — ajouter `quarkus-rest-jackson` ou autre dep manquante si share-service en utilisait une non couverte
diff <(grep -E '<artifactId>quarkus-' backend/services/share-service/pom.xml 2>/dev/null) \
     <(grep -E '<artifactId>quarkus-' backend/services/event-service/pom.xml 2>/dev/null)
# Si une dep manque, l'ajouter.
```

**Patch détaillé Kong** (`k8s/chart/templates/kong/configmap-routes.yaml`) — exemple :

```yaml
# AVANT (deux blocs séparés) :
      - name: share-service
        url: http://share-service:8080
        connect_timeout: 5000
        read_timeout: 30000
        write_timeout: 30000
        routes:
          - name: event-share
            paths:
              - ~/api/events/(?:\d+)/share$
            strip_path: false
            preserve_host: true
          - name: shortlink
            paths:
              - ~/api/s/[A-Za-z0-9]+$
            strip_path: false
            preserve_host: true

      - name: event-service
        url: http://event-service:8080
        ...
        routes:
          - name: events-list
            paths:
              - ~/api/events$
            ...
          [...]

# APRÈS (un seul bloc event-service avec les routes share absorbées) :
      - name: event-service
        url: http://event-service:8080
        connect_timeout: 5000
        read_timeout: 30000
        write_timeout: 30000
        routes:
          - name: event-share          # absorbé depuis share-service
            paths:
              - ~/api/events/(?:\d+)/share$
            strip_path: false
            preserve_host: true
          - name: shortlink            # absorbé depuis share-service
            paths:
              - ~/api/s/[A-Za-z0-9]+$
            strip_path: false
            preserve_host: true
          - name: events-list
            ...
          [...]
```

**Validation** :
```bash
cd backend && ./mvnw -pl services/event-service -am test -DskipITs
# SUCCESS — les tests share existants doivent passer dans le nouveau package
find backend/services -name 'share-service' -type d                                     # vide
helm template k8s/chart 2>/dev/null | grep -c 'name: share-service'                     # 0
```

**Commit** : `refactor(backend): merge share-service into event-service (Étape 2.2.1)`

##### Étape 2.2.2 — Merge `view-service` → `event-service`

Pattern identique. Spécificités :
- Sous-package : `ch.unige.events.event.view.*`.
- Tables possédées : `event_views` (déménage logiquement vers event-service ; physiquement reste dans `public`).
- Stub `EventStub` dans view-service : devient redondant car événement déjà entité locale dans event-service. **À supprimer pendant le merge** (le call-site qui utilisait le stub passe par l'entité réelle `Event`).
- Pas de Kafka producer.

**Commit** : `refactor(backend): merge view-service into event-service (Étape 2.2.2)`

##### Étape 2.2.3 — Merge `favorite-service` → `event-service`

Pattern identique. Spécificités :
- Sous-package : `ch.unige.events.event.favorite.*`.
- Tables : `favorites` (déménage logiquement).
- Stubs `EventStub`, `UserStub`, `AttendanceStub` dans favorite-service :
  - `EventStub` → entité réelle `Event` locale, supprimer le stub.
  - `UserStub`, `AttendanceStub` → restent comme stubs cross-service (à remplacer par REST clients en Étape 4).
- Pas de Kafka producer.
- Endpoint internal `GET /events/{id}/favorite-count` (mentionné dans completion-spec Décision B) **n'est plus nécessaire** post-consolidation : favorites sont locales, le stats-service interne (futur dans event-service) accède directement à la table.

**Commit** : `refactor(backend): merge favorite-service into event-service (Étape 2.2.3)`

##### Étape 2.2.4 — Merge `co-organizer-service` → `event-service`

Pattern identique. Spécificités :
- Sous-package : `ch.unige.events.event.coorganizer.*`.
- Tables : `event_co_organizers` (déménage logiquement).
- **Kafka producers** : `co-organizers.invited`, `co-organizers.accepted` ; le `CoOrganizerPublisher` + `CoOrganizerKafkaBridge` déménagent dans `event-service/src/main/java/ch/unige/events/event/coorganizer/kafka/`. Le `application.properties` event-service récupère les 2 channels outgoing :
  ```properties
  mp.messaging.outgoing.co-organizers-invited.connector=smallrye-kafka
  mp.messaging.outgoing.co-organizers-invited.topic=co-organizers.invited
  mp.messaging.outgoing.co-organizers-invited.value.serializer=...ObjectMapperSerializer
  %test.mp.messaging.outgoing.co-organizers-invited.connector=smallrye-in-memory
  # idem co-organizers-accepted
  ```
- Stubs `EventStub`, `UserStub`, `AttendanceStub` dans co-organizer-service :
  - `EventStub` → entité réelle, supprimer le stub.
  - `UserStub`, `AttendanceStub` → restent (cross-service ; → REST clients Étape 4).
- **Cascade SCRUM-136** : la méthode `EventCoOrganizerService.findByEventAndUser(...)` devient un appel local accessible depuis `EventService.isCreatorOrAcceptedCoOrganizer(event, callerId)`. **Pas de REST client** plus nécessaire pour ce path.

**Commit** : `refactor(backend): merge co-organizer-service into event-service (Étape 2.2.4)`

##### Étape 2.2.5 — Merge `stats-service` → `event-service`

Pattern identique. Spécificités :
- Sous-package : `ch.unige.events.event.stats.*`.
- 0 schéma propre (read-only aggregator).
- Stubs : 6 stubs cross-service (`EventStub`, `UserStub`, `AttendanceStub`, `FavoriteStub`, `EventViewStub`, `EventCoOrganizerStub`).
  - `EventStub`, `FavoriteStub`, `EventViewStub`, `EventCoOrganizerStub` → entités réelles post-consolidation, supprimer les stubs.
  - `UserStub`, `AttendanceStub` → restent cross-service (Étape 4).
- Pas de Kafka.

**Commit** : `refactor(backend): merge stats-service into event-service (Étape 2.2.5)`

##### Étape 2.2.6 — Merge `me-aggregator-service` → `event-service`

Pattern identique. Spécificités :
- Sous-package : `ch.unige.events.event.me.*`.
- 0 schéma propre.
- 1 endpoint : `GET /users/me/events`.
- Stubs : `EventStub`, `UserStub`, `AttendanceStub`.
  - `EventStub` → entité réelle, supprimer.
  - `UserStub`, `AttendanceStub` → restent (Étape 4).
- Pas de Kafka.
- Kong route `/users/me/events` : upstream `me-aggregator-service` → `event-service` (déjà dans le bloc event-service, juste retirer le bloc me-aggregator).

**Commit** : `refactor(backend): merge me-aggregator-service into event-service (Étape 2.2.6)`

#### Étape 2.3 — Merges dans user-service (2 commits)

##### Étape 2.3.1 — Merge `follow-service` → `user-service`

Pattern Décision B. Spécificités :
- Sous-package : `ch.unige.events.user.follow.*`.
- Tables : `follows` (déménage).
- **Kafka producers** : `users.{followed,follow-requested,follow-accepted}` ; `FollowLifecyclePublisher` + `FollowLifecycleKafkaBridge` déménagent dans `user-service/src/main/java/ch/unige/events/user/follow/kafka/`. Les 3 channels outgoing dans `user-service application.properties`.
- Stub `UserStub` dans follow-service : redondant post-merge (entité `User` réelle locale dans user-service). **Supprimer**.
- POM user-service récupère : `quarkus-messaging-kafka` + `shared-kafka-events` + `smallrye-reactive-messaging-in-memory` (test) si pas déjà présent.

**Commit** : `refactor(backend): merge follow-service into user-service (Étape 2.3.1)`

##### Étape 2.3.2 — Merge `calendar-service` → `user-service`

Pattern. Spécificités :
- Sous-package : `ch.unige.events.user.calendar.*`.
- 0 schéma propre (lit/écrit `users.calendar_token`).
- Stubs : `UserStub`, `EventStub`, `FavoriteStub`, `AttendanceStub`.
  - `UserStub` → entité réelle, supprimer.
  - `EventStub`, `FavoriteStub` → cross-service (Étape 4 — REST client vers event-service ; favorites est dans event-service post-consolidation).
  - `AttendanceStub` → cross-service (Étape 4 — REST client vers engagement-service).
- Pas de Kafka.

**Commit** : `refactor(backend): merge calendar-service into user-service (Étape 2.3.2)`

#### Étape 2.4 — Merges dans engagement-service (1 commit)

##### Étape 2.4.1 — Merge `comment-service` → `engagement-service`

Pattern. Spécificités :
- Sous-package : `ch.unige.events.engagement.comment.*` (le service cible étant `engagement-service` post-rename 2.1.1, le top package est `ch.unige.events.engagement.*`).
- **NB** : engagement-service hérite des sources de attendance-service qui sont actuellement sous `ch.unige.events.attendance.*`. Faut-il renommer aussi ces packages ? Décision : **OUI**, pour cohérence. Donc cette sous-étape inclut un rename mass `ch.unige.events.attendance.*` → `ch.unige.events.engagement.*` (le sous-package `attendance/` reste pour propreté, idem `comment/`).

**Patch séquence** :
```bash
# A. Préalable : rename des packages attendance existants
find backend/services/engagement-service/src -name '*.java' -exec \
    sed -i 's|^package ch\.unige\.events\.attendance\.|package ch.unige.events.engagement.attendance.|g; \
            s|^import ch\.unige\.events\.attendance\.|import ch.unige.events.engagement.attendance.|g' {} +
mkdir -p backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance
git mv backend/services/engagement-service/src/main/java/ch/unige/events/attendance/* \
       backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/
mkdir -p backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance
git mv backend/services/engagement-service/src/test/java/ch/unige/events/attendance/* \
       backend/services/engagement-service/src/test/java/ch/unige/events/engagement/attendance/ 2>/dev/null || true

# B. Move comment-service sources
mkdir -p backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment
git mv backend/services/comment-service/src/main/java/ch/unige/events/comment/* \
       backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/
find backend/services/engagement-service/src/main/java/ch/unige/events/engagement/comment/ -name '*.java' -exec \
    sed -i 's|^package ch\.unige\.events\.comment\.|package ch.unige.events.engagement.comment.|g; \
            s|^import ch\.unige\.events\.comment\.|import ch.unige.events.engagement.comment.|g' {} +

# C. Tests
mkdir -p backend/services/engagement-service/src/test/java/ch/unige/events/engagement/comment
git mv backend/services/comment-service/src/test/java/ch/unige/events/comment/* \
       backend/services/engagement-service/src/test/java/ch/unige/events/engagement/comment/ 2>/dev/null || true
rm -f backend/services/engagement-service/src/test/java/ch/unige/events/engagement/comment/ServiceIdentityResourceTest.java
find backend/services/engagement-service/src/test/java/ch/unige/events/engagement/comment/ -name '*.java' -exec \
    sed -i 's|^package ch\.unige\.events\.comment\.|package ch.unige.events.engagement.comment.|g; \
            s|^import ch\.unige\.events\.comment\.|import ch.unige.events.engagement.comment.|g' {} + 2>/dev/null || true

# D. POM engagement-service : ajouter Kafka deps si pas déjà
diff <(grep '<artifactId>quarkus-' backend/services/comment-service/pom.xml) \
     <(grep '<artifactId>quarkus-' backend/services/engagement-service/pom.xml)
# Si quarkus-messaging-kafka manque, l'ajouter ; idem shared-kafka-events.

# E. application.properties — copier le bloc Kafka comments-created
# (concat manuel via Edit — ajouter à la fin du fichier engagement-service application.properties)

# F. Stubs comment-service:
#   - EventStub, UserStub, EventCoOrganizerStub → restent cross-service (Étape 4 → REST clients)

# G. Helm + Kong + parent POM (suppression bloc comment-service)
git rm -r k8s/chart/templates/comment-service/
sed -i '/- name: comment-service/,/(?:\\d+)\\$/d' k8s/chart/templates/kong/configmap-routes.yaml  # ATTENTION: regex à valider à la main
sed -i '/<module>services\/comment-service<\/module>/d' backend/pom.xml
git rm -r backend/services/comment-service/
```

**Validation** :
```bash
cd backend && ./mvnw -pl services/engagement-service -am test -DskipITs   # SUCCESS
find backend/services -name 'comment-service' -type d                     # vide
```

**Commit** : `refactor(backend): merge comment-service into engagement-service + repackage attendance (Étape 2.4.1)`

#### Étape 2.5 — Cleanup post-consolidation (1-2 commits)

##### Étape 2.5.1 — Vérification finale du parent POM + values.yaml + structure

**Fichier modifié** : `backend/pom.xml` (vérification — devrait être propre après 2.1-2.4).

**Vérifications** :

```bash
# Vérifier que le parent POM liste exactement 5 services + 10 libs = 15 modules
grep -c '<module>services/' backend/pom.xml      # 15 attendu

# Lister:
grep '<module>services/' backend/pom.xml | sort
# Attendu:
#   <module>services/engagement-service</module>
#   <module>services/event-service</module>
#   <module>services/moderation-service</module>
#   <module>services/notification-service</module>
#   <module>services/shared-api-error</module>
#   <module>services/shared-domain-dtos</module>
#   <module>services/shared-domain-enums</module>
#   <module>services/shared-domain-projections</module>
#   <module>services/shared-jaxrs</module>
#   <module>services/shared-kafka-events</module>
#   <module>services/shared-platform</module>
#   <module>services/shared-rate-limit</module>
#   <module>services/shared-storage</module>
#   <module>services/shared-tracing</module>
#   <module>services/user-service</module>

# Vérifier qu'il n'y a plus de dossier orphelin
ls backend/services/ | grep -E '(share|view|favorite|calendar|follow|comment|co-organizer|attendance|report|stats|me-aggregator)-service'
# Doit être VIDE

# values.yaml: depuis Étape 12.4 il n'y a plus de clés image.<svc>.* — vérifier
grep -E 'image\.(api|share|view|favorite|calendar|follow|comment|co-organizer|attendance|report|stats|me-aggregator)' k8s/chart/values.yaml
# Doit être VIDE
```

Si une de ces vérifications fait apparaître un orphelin, fix dans cette sous-étape.

##### Étape 2.5.2 — Suppression `notification-service` ServiceIdentityResource si déménageable ?

**Décision pratique** : `notification-service` reste tel quel (placeholder). Aucune action.

##### Étape 2.5.3 — Validation reactor complète

```bash
cd backend && ./mvnw verify -DskipITs   # SUCCESS sur 15 modules en ~2-3 min
```

Si SUCCESS → push final Étape 2 → watch CI.

**Commit** (1 commit pour 2.5.x s'il y a quelque chose à committer ; sinon pas de commit) :
`chore(backend): finalize consolidation cleanup — verify 15-module reactor (Étape 2.5)`

#### Récap fin Étape 2

**Sous-étapes commitées** : 2.1.1, 2.1.2, 2.2.1, 2.2.2, 2.2.3, 2.2.4, 2.2.5, 2.2.6, 2.3.1, 2.3.2, 2.4.1 (+ 2.5 si nécessaire) = **11-12 commits**.
**Topology atteinte** : 5 services + 10 libs = 15 modules dans le reactor.
**Helm chart** : 5 Deployment templates (event/user/engagement/moderation/notification + infra Kong/Kafka/db/minio/web/cloudflared).
**Kong configmap** : 5 service blocks.
**Validation finale Étape 2** :
```bash
cd backend && ./mvnw verify -DskipITs   # SUCCESS sur 15 modules
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch                # tous verts (sauf Sonar gate qui sera OK une fois Étape 5 livrée + DevOps configure les 15 SonarCloud projects)
```

**Coverage attendue** : encore ~22 % L (l'Étape 2 ne touche pas aux tests). C'est OK, ça résoudra avec Étape 5.

### Étape 3 — Cleanup post-consolidation des configs DevOps non-critiques

**Objectif** : finir les ajustements DevOps mineurs qui ne sont pas couverts par Étape 2 mais qui sont du périmètre backend YAML (≠ items DevOps S9+).

#### Étape 3.1 — Vérification Helm chart `Chart.yaml`

**Fichier** : `k8s/chart/Chart.yaml`.

**Vérification** : version reste à `0.2.0` (le bump majeur a déjà été fait à PR #158). Pas de change.

#### Étape 3.2 — Vérification Helm `values-preview.yaml`

**Fichier** : `k8s/chart/values-preview.yaml`.

**Vérification** : aucune référence aux anciens services nominalement. Pas de change attendu.

#### Étape 3.3 — Vérification `.github/workflows/deploy.yml`

**Fichier** : `.github/workflows/deploy.yml`.

**Vérification** : `--set image.tag="${{ github.sha }}"` (post-Étape 12.4 c'est la valeur correcte). Pas de change.

#### Étape 3.4 — Mise à jour `backend/docs/architecture.md` (post-consolidation)

**Fichier** : `backend/docs/architecture.md`.

**Patch** : reproduire le tableau « Microservices — endpoints owned » avec les 5 services post-consolidation. Reproduire le diagramme post-consolidation (ASCII). Reproduire les notes inter-service ajustées (8 REST clients au lieu de 16+, 4 producteurs Kafka au lieu de 5).

Ce patch est lourd (200-300 lignes touchées). Le fait dans une sous-étape unique pour ne pas casser la doc partiellement.

**Commit** : `docs(backend): align architecture.md with post-consolidation 5-service topology (Étape 3.4)`

#### Étape 3.5 — Mise à jour `backend/AGENTS.md` post-consolidation

**Fichier** : `backend/AGENTS.md`.

**Patch** : remplacer la table des modules (passer de 14 services + 10 libs → 5 services + 10 libs). Mettre à jour les compteurs.

**Commit** : `docs(backend): align AGENTS.md with post-consolidation 5-service topology (Étape 3.5)`

#### Récap fin Étape 3

**Sous-étapes commitées** : 3.4, 3.5 (2 commits ; 3.1/3.2/3.3 = vérifications no-op).
**Validation finale** :
```bash
cd backend && ./mvnw verify -DskipITs   # SUCCESS (pas de change code)
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
```

---
### Étape 4 — REST clients cross-service (8 commits)

**Objectif** : remplacer les ~10 stubs JPA cross-service restants (post-consolidation, beaucoup ont disparu car les tables sont devenues locales) par les 8 REST clients de la Décision C. Cette étape s'appuie sur les 8 shared libs Étape 3 de la completion-spec (déjà livrées).

**Pré-requis Étape 4** : consolidation 14→5 livrée (Étape 2 verte) + docs alignées (Étape 3 verte).

#### Étape 4.0 — Bascule des shared libs vers les 4 services métiers

**Objectif** : faire en sorte que les 4 services métiers consomment effectivement les 8 nouvelles libs créées en complétion-spec Étape 3 (qui sont actuellement créées mais pas consommées par les services). Cette sous-étape supprime les copies locales des classes redondantes.

**Pour chaque service** (event, user, engagement, moderation) :

1. **POM** : ajouter les `<dependency>` vers `shared-api-error`, `shared-domain-enums`, `shared-domain-dtos` (si applicable), `shared-domain-projections`, `shared-jaxrs`, `shared-platform`. (Les `shared-tracing`, `shared-rate-limit`, `shared-storage`, `shared-kafka-events` sont déjà déclarées.)

   ```xml
   <dependency><groupId>ch.unige.events</groupId><artifactId>shared-api-error</artifactId><version>${project.version}</version></dependency>
   <dependency><groupId>ch.unige.events</groupId><artifactId>shared-domain-enums</artifactId><version>${project.version}</version></dependency>
   <dependency><groupId>ch.unige.events</groupId><artifactId>shared-domain-dtos</artifactId><version>${project.version}</version></dependency>
   <dependency><groupId>ch.unige.events</groupId><artifactId>shared-domain-projections</artifactId><version>${project.version}</version></dependency>
   <dependency><groupId>ch.unige.events</groupId><artifactId>shared-jaxrs</artifactId><version>${project.version}</version></dependency>
   <dependency><groupId>ch.unige.events</groupId><artifactId>shared-platform</artifactId><version>${project.version}</version></dependency>
   ```

2. **Sources locales à supprimer dans chaque service** :
   - `ApiErrorResponse.java` (dto/) — remplacé par `ch.unige.events.shared.error.ApiErrorResponse`.
   - Enums dupliqués (`EventStatus`, `EventCategory`, `Faculty`, `AttendanceStatus`, `CoOrganizerStatus`, `FollowStatus`, `RecurrenceFrequency`, `ReportStatus`, `ReportReason`) — remplacés par `ch.unige.events.shared.domain.enums.*`.
     - **NB** : Les entités JPA qui utilisent ces enums via `@Enumerated(STRING)` doivent juste changer leurs imports. Hibernate hydrate par nom de constante (string) — invariant garanti par le sentinel `EnumValuesSentinelTest` dans `shared-domain-enums`.
   - DTOs cross-projetés copiés (`UserPublicResponse`, `EventDTO`, `AttendanceDTO`, `EventCoOrganizerDTO`, `CapacitySummary`, `AttendanceSummary`, `FollowCounts`, `CoOrganizerCheck`) — remplacés par `ch.unige.events.shared.domain.dto.*`.
     - **Nuance** : si un service a un `EventDTO` enrichi avec des champs spécifiques (ex. `EventDTO.from(event, attendanceCount, availableSpots, waitlisted, ...)` event-service-spécifique), garder le DTO local du service propriétaire (event-service) et ne supprimer que les copies des services consommateurs (qui désérialisent via Jackson un payload compatible).
   - Helpers dupliqués : `computeAvailableSpots` static dans 6 services → `ch.unige.events.shared.domain.projections.EventCapacity.computeAvailableSpots`. `resolveUserId(jwt)` → `ch.unige.events.shared.domain.projections.Auth0IdResolver.resolveUserId`.
   - `ServiceIdentityResource` local → remplacé par `shared-platform.ServiceIdentityResource` (auto-discovered via Jandex). Le sentinel test `ServiceIdentityResourceTest` reste dans chaque service (référence la classe shared maintenant).
   - `Timeframe.java` local + `parseTimeframe(...)` helper → remplacés par `ch.unige.events.shared.jaxrs.Timeframe` + auto-binding via `TimeframeParamConverterProvider` (auto-discovered via Jandex).

3. **Imports à substituer** : grand `sed` global sur chaque service :
   ```bash
   # Pour event-service (exemple) :
   find backend/services/event-service/src -name '*.java' -exec sed -i \
       -e 's|ch\.unige\.events\.event\.dto\.ApiErrorResponse|ch.unige.events.shared.error.ApiErrorResponse|g' \
       -e 's|ch\.unige\.events\.event\.entity\.EventStatus|ch.unige.events.shared.domain.enums.EventStatus|g' \
       -e 's|ch\.unige\.events\.event\.entity\.EventCategory|ch.unige.events.shared.domain.enums.EventCategory|g' \
       -e 's|ch\.unige\.events\.event\.entity\.Faculty|ch.unige.events.shared.domain.enums.Faculty|g' \
       -e 's|ch\.unige\.events\.event\.entity\.AttendanceStatus|ch.unige.events.shared.domain.enums.AttendanceStatus|g' \
       -e 's|ch\.unige\.events\.event\.entity\.CoOrganizerStatus|ch.unige.events.shared.domain.enums.CoOrganizerStatus|g' \
       -e 's|ch\.unige\.events\.event\.entity\.RecurrenceFrequency|ch.unige.events.shared.domain.enums.RecurrenceFrequency|g' \
       {} +
   # Idem user / engagement / moderation
   ```

4. **Refactoriser les call-sites** des helpers locaux `private static badRequest(...)` → `ApiErrors.badRequest("error_code", "message")`. Les helpers locaux deviennent inutiles ; supprimer les méthodes statiques après bascule (sinon Sonar les flag `dead code`).

**Commit** : `refactor(backend): adopt shared libs (api-error, domain-enums, dtos, projections, jaxrs, platform) across 4 services (Étape 4.0 — REFACTOR-002, 003, 005, 006, 011, 012, 013, 018)`

(Si le diff est trop gros — ce qui est probable car ~50-100 fichiers touchés —, scinder en :
- `4.0.1` adopt shared-api-error + shared-domain-enums (les plus structurants)
- `4.0.2` adopt shared-domain-dtos + shared-domain-projections
- `4.0.3` adopt shared-jaxrs + shared-platform — incluant suppression locale `ServiceIdentityResource` + `Timeframe`)

#### Étape 4.1 — REST client `EventServiceClient` côté engagement-service + moderation-service + user-service

**Création** d'une interface unique `EventServiceClient` dans le module `shared-domain-dtos` (nouveau fichier `ch.unige.events.shared.client.EventServiceClient` — déclarée comme interface portable). Les 3 services consumers (engagement, moderation, user) la déclarent comme dépendance et l'enregistrent comme `@RegisterRestClient` via `application.properties`.

**Patch** :

```java
// backend/services/shared-domain-dtos/src/main/java/ch/unige/events/shared/client/EventServiceClient.java
package ch.unige.events.shared.client;

import ch.unige.events.shared.domain.dto.EventDTO;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import ch.unige.events.shared.tracing.RequestIdClientFilter;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@RegisterRestClient(configKey = "event-service")
@RegisterProvider(RequestIdClientFilter.class)
@Path("/events")
public interface EventServiceClient {

    @GET
    @Path("/{id}")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "getByIdFallback")
    EventDTO getById(@PathParam("id") long id);

    default EventDTO getByIdFallback(long id) {
        return null;
    }

    @GET
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "findByIdsFallback")
    List<EventDTO> findByIds(@QueryParam("ids") List<Long> ids,
                             @QueryParam("status") String status);

    default List<EventDTO> findByIdsFallback(List<Long> ids, String status) {
        return List.of();
    }

    /**
     * Returns the event payload + a `coOrganizerOf: bool` field indicating
     * if the given userId is an ACCEPTED co-organizer of the event.
     * Used to centralize cascade SCRUM-136 in event-service.
     */
    @GET
    @Path("/{id}")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    EventDTO getByIdWithCoOrgCheck(@PathParam("id") long id,
                                   @QueryParam("check-co-org-of") UUID userId);
}
```

**Côté event-service**, exposer le payload enrichi en mode interne :

- Modifier `EventResource.getById(...)` pour accepter `?check-co-org-of=<UUID>` query param (optionnel) et enrichir le `EventDTO` avec le champ `coOrganizerOf: bool`. Le champ est ajouté au record `EventDTO` (shared-domain-dtos) avec valeur par défaut `false` ou `null`.

- Ajouter au record `EventDTO` (déjà dans `shared-domain-dtos`) un champ `coOrganizerOf` :
  ```java
  // shared-domain-dtos/.../EventDTO.java — ajouter le champ optionnel
  Boolean coOrganizerOf  // null si query param absent ; bool si param présent
  ```

- Endpoint bulk `GET /events?ids=...&status=PUBLISHED` :
  - Côté event-service `EventResource`, ajouter une méthode :
    ```java
    @GET
    @PermitAll
    public List<EventDTO> findByIds(@QueryParam("ids") List<Long> ids,
                                    @QueryParam("status") String status) { ... }
    ```
  - Implémentation : `Event.list("id IN ?1 AND status = ?2", ids, EventStatus.valueOf(status))`.

**Configuration `application.properties`** par consumer :

```properties
quarkus.rest-client.event-service.url=${EVENT_SERVICE_URL:http://event-service:8080}
```

**Suppression des stubs** dans engagement-service, moderation-service, user-service :
- `EventStub.java` → supprimé.
- Call-sites qui faisaient `EventStub.findByIdOptional(id)` → remplacé par `eventServiceClient.getById(id)` ; le retour `null` du fallback est traité comme `Optional.empty()` (404 NotFoundException si nécessaire).

**Commit** : `feat(backend): introduce EventServiceClient REST client + cascade SCRUM-136 query param (Étape 4.1 — SPEC-002, REFACTOR-001, SCRUM-136)`

#### Étape 4.2 — REST client `UserServiceClient` côté event-service + engagement-service + moderation-service

Pattern identique à 4.1. Création de `ch.unige.events.shared.client.UserServiceClient` dans `shared-domain-dtos` :

```java
@RegisterRestClient(configKey = "user-service")
@RegisterProvider(RequestIdClientFilter.class)
@Path("/users")
public interface UserServiceClient {

    @GET @Path("/{id}")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "getByIdFallback")
    UserPublicResponse getById(@PathParam("id") UUID id);

    default UserPublicResponse getByIdFallback(UUID id) {
        return null;
    }
}
```

**Note** : la spec orig + completion-spec définissaient `GET /users/by-auth0/{auth0Id}` comme endpoint interne. Post-consolidation, ce besoin disparaît : event-service / engagement-service / moderation-service ont tous accès à `JsonWebToken` localement (résoudre via `Auth0IdResolver.resolveUserId(jwt)`) et appellent ensuite `userServiceClient.getById(uuid)` avec un UUID résolu. **L'endpoint `/users/by-auth0/{auth0Id}` n'est PAS créé** — économise un endpoint interne.

**Suppression des stubs** : `UserStub.java` dans event, engagement, moderation → supprimé.

**Commit** : `feat(backend): introduce UserServiceClient REST client (Étape 4.2 — SPEC-002, REFACTOR-009)`

#### Étape 4.3 — REST client `EngagementServiceClient` côté event-service + user-service

Création de `ch.unige.events.shared.client.EngagementServiceClient` :

```java
@RegisterRestClient(configKey = "engagement-service")
@RegisterProvider(RequestIdClientFilter.class)
public interface EngagementServiceClient {

    @GET @Path("/events/{eventId}/attendance-summary")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "summaryFallback")
    AttendanceSummary getAttendanceSummary(@PathParam("eventId") long eventId);

    default AttendanceSummary summaryFallback(long eventId) {
        return AttendanceSummary.of(0L, 0L);
    }

    @GET @Path("/users/{id}/attendances")
    @Retry(maxRetries = 3, delay = 200, delayUnit = ChronoUnit.MILLIS)
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
    @Fallback(fallbackMethod = "userAttendancesFallback")
    List<AttendanceDTO> getUserAttendances(@PathParam("id") UUID id,
                                           @QueryParam("status") String status);

    default List<AttendanceDTO> userAttendancesFallback(UUID id, String status) {
        return List.of();
    }
}
```

**Côté engagement-service**, exposer :
- `GET /events/{eventId}/attendance-summary` (nouveau endpoint interne, retourne `AttendanceSummary`)
- `GET /users/{id}/attendances?status=ATTENDING` (déjà exposé public, réutilisé interne)

**Suppression stubs** : `AttendanceStub.java` dans event-service, user-service → supprimé.

**Commit** : `feat(backend): introduce EngagementServiceClient REST client + attendance-summary endpoint (Étape 4.3)`

#### Étape 4.4 — Centralisation cascade SCRUM-136 + anti-oracles ISSUE-92/93

**Objectif** : finaliser la centralisation des règles de visibilité.

**Patch** :

- Dans **event-service** (provider) :
  - `EventResource.getById(@PathParam Long id, @QueryParam("check-co-org-of") UUID userId)` :
    - Applique l'anti-oracle ISSUE-92 (404 si DRAFT/CANCELLED non-créateur non-admin).
    - Si `check-co-org-of` est fourni, calcule `coOrganizerOf = isCreatorOrAcceptedCoOrganizer(event, userId)` (méthode locale post-consolidation Étape 2.2.4) et l'inclut dans le payload.

- Dans **user-service** (provider) :
  - `UserResource.getById(...)` applique l'anti-oracle ISSUE-93 (404 si profilePublic=false non-self non-admin). Inchangé post-Étape 4.2.
  - Signature `getPublicProfile(UUID, String callerAuth0Id, boolean isAdmin)` — admin bypass aligné (cf. completion-spec Décision M, déjà spécifié non livré → cette spec le fait ici).

- Dans **engagement-service** (consumer) :
  - `CommentService.assertEventVisibleAndLoad(...)` SUPPRIMÉ — remplacé par `eventServiceClient.getByIdWithCoOrgCheck(eventId, callerId)`. Le 404 propagé du REST client ferme l'anti-oracle.
  - `CommentService.isCreatorOrAcceptedCoOrganizer(...)` SUPPRIMÉ — la cascade est dans le payload `coOrganizerOf` retourné.
  - `AttendanceService.<méthode-similaire>(...)` SUPPRIMÉ — idem.

- Dans **moderation-service** (consumer) :
  - `ReportService.assertEventVisibleAndLoad(...)` + `isCreatorOrAcceptedCoOrganizer(...)` → idem suppression.

**Note** : les inlinings actuels dans 5 services (REFACTOR-009, REFACTOR-010, SEC-002) disparaissent.

**Commit** : `refactor(backend): centralize ISSUE-92 / ISSUE-93 / SCRUM-136 behind event-service + user-service REST endpoints (Étape 4.4 — Décision L, REFACTOR-009/010, SEC-001/002/003)`

#### Étape 4.5 — Validation finale Étape 4 — 0 stub JPA cross-service

**Validation** :

```bash
# 1. 0 stub JPA cross-service
find backend/services -name '*Stub.java' -not -path '*/target/*'   # vide attendu

# 2. ≥ 8 @RegisterRestClient interfaces (sur 8 attendus)
grep -rn '@RegisterRestClient' backend/services/shared-domain-dtos/src
# attendu ≥ 8 hits

# 3. Build verts
cd backend && ./mvnw verify -DskipITs   # SUCCESS
```

Si `find` retourne ≥ 1 stub : `git commit refactor(backend): remove last JPA stub <name> (Étape 4.5)`. Sinon pas de commit.

#### Récap fin Étape 4

**Sous-étapes commitées** : 4.0 (1-3 commits), 4.1, 4.2, 4.3, 4.4 (+ 4.5 si nécessaire) = **5-7 commits**.
**Findings adressés** : SPEC-002, SPEC-005, SPEC-011, SPEC-012, SPEC-013, SPEC-014, SPEC-021, BUG-008, BUG-011, REFACTOR-001 à REFACTOR-013 (+ 016, 017, 018), SEC-001, SEC-002, SEC-003.
**Validation finale** :
```bash
find backend/services -name '*Stub.java' -not -path '*/target/*'                            # 0
grep -rln '@RegisterRestClient' backend/services/shared-domain-dtos/src/main/java          # ≥ 8
cd backend && ./mvnw verify -DskipITs                                                       # SUCCESS
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
```

### Étape 5 — Tests legacy port + 35 sentinels SCRUM-138/139/144/147 (10-14 commits)

**Objectif** : porter ~1818 tests legacy + atteindre couverture cible (≥ 80 % L / ≥ 70 % B par service métier post-consolidation). Les 35 sentinels SCRUM-138/139/144/147 doivent ressortir verts par nom. Cette étape s'appuie sur l'Étape 4 (REST clients en place — les tests utilisent `@InjectMock <X>ServiceClient` ou WireMock).

**Pré-requis Étape 5** : Étape 4 verte (0 stub JPA cross-service, 8 REST clients livrés).

#### Étape 5.0 — Stratégie mock/stub commune

**Décision** : préférer **`@InjectMock <X>ServiceClient`** (Quarkus mock CDI bean) **sans WireMock**, sauf cas où le test couvre la sérialisation HTTP elle-même (ex. test du fallback CB). Pas de module shared-test-stubs (KISS).

Pour chaque service consumer (engagement, moderation, user, event), les tests qui mockent un REST client suivent le pattern :

```java
@QuarkusTest
@TestSecurity(user = "auth0|123", roles = {})
class CommentServiceCoverageTest {

    @InjectMock @RestClient
    EventServiceClient eventClient;

    @Inject CommentService commentService;

    @BeforeEach
    void setup() {
        when(eventClient.getByIdWithCoOrgCheck(anyLong(), any(UUID.class)))
            .thenReturn(<EventDTO test fixture>);
    }

    @Test
    void post_validComment_persists() { ... }

    @Test
    void post_eventDraftByNonCreator_returns404_antiOracle() {
        // Mock le client pour retourner null (= 404 propagé)
        when(eventClient.getByIdWithCoOrgCheck(anyLong(), any(UUID.class))).thenReturn(null);
        ...
    }
}
```

**Pas de commit pour 5.0** — c'est une stratégie documentée dans la spec.

#### Étape 5.1 — Port `RecurrenceGeneratorTest` (event-service, logique pure) — 4 sentinels SCRUM-147

**Action** :

```bash
git show 41074e9:backend/services/legacy-monolith/src/test/java/ch/unige/events/util/RecurrenceGeneratorTest.java \
    > backend/services/event-service/src/test/java/ch/unige/events/event/util/RecurrenceGeneratorTest.java
```

Adapter :
- package `ch.unige.events.util.RecurrenceGeneratorTest` → `ch.unige.events.event.util.RecurrenceGeneratorTest`.
- imports `ch.unige.events.entity.RecurrenceFrequency` → `ch.unige.events.shared.domain.enums.RecurrenceFrequency`.

**Sentinels** : `weekly_4Occurrences_returns3DatesSpacedBy7Days`, `monthly_handlesShortFebruaryFromJanuary31`, `bothNull_throwsIllegalArgumentException`, `maxOccurrencesAbove52_cappedTo52`.

**Validation** : `cd backend && ./mvnw -pl services/event-service test -DskipITs` → 4 sentinels verts.

**Commit** : `test(backend): port RecurrenceGeneratorTest from legacy (Étape 5.1, TEST-003, 4 SCRUM-147 sentinels)`

#### Étape 5.2 — Port `EventServiceCoverageTest` + `EventResourceTest` + autres event-domain — reste de SCRUM-147

**Action** : porter par lots les fichiers de tests legacy event-domain. Reproduire le pattern `git show 41074e9:.../X.java > backend/services/event-service/src/test/...`.

Fichiers à porter (ordre suggéré) :

1. `service/EventServiceCoverageTest` → `event-service/src/test/java/ch/unige/events/event/service/`
2. `resource/EventResourceTest` → idem `resource/`
3. `resource/AdminEventResourceTest` → idem
4. `resource/EventSearchResourceTest` → idem
5. `entity/EventTest` → `entity/`
6. `dto/EventDTOTest` → `dto/`
7. `service/EventCoOrganizerServiceCoverageTest` → `event-service/src/test/java/ch/unige/events/event/coorganizer/service/`
8. `resource/EventCoOrganizerResourceTest` → idem `resource/`
9. `service/EventStatsServiceCoverageTest` → `event-service/src/test/java/ch/unige/events/event/stats/service/`
10. `resource/EventStatsResourceTest` → idem
11. `service/FavoriteServiceCoverageTest` → `event-service/src/test/java/ch/unige/events/event/favorite/service/`
12. `resource/FavoriteResourceTest` → idem
13. `service/EventViewServiceCoverageTest` → `event-service/src/test/java/ch/unige/events/event/view/service/`
14. `resource/EventViewResourceTest` → idem
15. `service/ShareServiceCoverageTest` → `event-service/src/test/java/ch/unige/events/event/share/service/`
16. `resource/RedirectResourceTest` → idem
17. `resource/ShareResourceTest` → idem

Adaptations requises :
- Renommer packages vers la nouvelle hiérarchie (sous-package par sous-domaine).
- Imports enums → `shared-domain-enums`, DTOs → `shared-domain-dtos` ou local (selon Étape 4.0).
- Mocks REST clients : `@InjectMock @RestClient UserServiceClient userClient` + `@InjectMock @RestClient EngagementServiceClient engagementClient` (post-Étape 4).
- Tests qui mutaient cross-service via stubs (`AttendanceStub.persist(...)`) → adapter pour utiliser `engagementClient.<method>` mock.

**Sentinels SCRUM-147 (21 total — 4 déjà en 5.1, reste 17)** :
`from_parentRecurringEvent_exposesRecurrenceRuleAndNullParentEventId`, `from_occurrenceEvent_exposesParentEventIdAndNullRecurrenceRule`, `createRecurring_weekly4Occurrences_persists1ParentAnd3Children`, `createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded`, `createRecurring_endDateBeforeStart_returns400_recurrenceEndBeforeStart`, `createRecurring_inheritsParentStatusPublished`, `getOccurrences_parentRecurring_returnsChildrenSortedAsc`, `getOccurrences_standaloneEvent_returns200EmptyList`, `getOccurrences_draftByNonCreator_returns404_antiOracle`, `update_parentTitle_doesNotPropagateToOccurrences`, `cancel_parentDoesNotCascadeToOccurrences`, `delete_parent_setsOccurrencesParentEventIdToNull`, `post_validRecurrenceWeekly_returns201_recurrenceRuleSetOnParent`, `post_recurrenceMaxOccurrences53_returns400_beanValidation`, `getOccurrences_parentPublishedAnonymous_returns200`, `getOccurrences_sizeOver52_returns400`, `getOccurrences_draftByAnonymous_returns404_antiOracle`.

Plus le sentinel BUG-003 fix-test : `cancel_expiredEvent_returns409_conflict` (ajouté ici car le fix code a été livré en Étape 1.1 de la completion-spec sans le test sentinel — l'opportunité est ici).

**Commit** : `test(backend): port event-domain tests from legacy + 17 SCRUM-147 sentinels (Étape 5.2, TEST-002, 17 sentinels)`

(Ou scinder en 2-3 commits si la volumétrie dépasse 500 lignes diff.)

#### Étape 5.3 — Port tests user-domain — 6 sentinels SCRUM-138

Fichiers (legacy → user-service post-consolidation) :

1. `service/UserServiceCoverageTest` → `user-service/src/test/java/ch/unige/events/user/service/`
2. `resource/UserResourceTest` → idem
3. `dto/UserPublicResponseTest` → `dto/`
4. `dto/UpdateProfileRequestTest` → `dto/`
5. `entity/UserTest` → `entity/`
6. (image upload S3 tests via MockS3 ou Testcontainers MinIO — si présents dans legacy)
7. `service/FollowServiceCoverageTest` → `user-service/src/test/java/ch/unige/events/user/follow/service/`
8. `resource/FollowResourceTest` → idem
9. `resource/FollowRequestResourceTest` → idem
10. `dto/FollowDTOTest` → idem
11. `entity/FollowTest` → idem
12. `service/CalendarServiceCoverageTest` → `user-service/src/test/java/ch/unige/events/user/calendar/service/`
13. `util/IcsBuilderTest` → idem `util/` — TEST-011 sentinel ICS RFC 5545
14. `resource/CalendarResourceTest` → `resource/`
15. `resource/UserCalendarTokenResourceTest` → idem

Adaptations :
- Mocks `EventServiceClient` (pour ICS feed bulk events) + `EngagementServiceClient` (pour user attendances).
- Anti-oracle ISSUE-93 testé via mock du behavior `getPublicProfile(UUID, String, boolean)`.

**Sentinels SCRUM-138 (6)** : `findAcceptedFollowedIds_returnsOnlyAcceptedUuids`, `rejectRequest_followerCanReFollowAfterReject`, `follow_selfFollow_throwsUnprocessable`, `getFollowers_privateProfileNonOwner_returns404_antiOracle`, `getPublicProfile_self_followStatusIsNull`, `getPublicProfile_authNonOwnerWithPending_followStatusIsPending`.

**Commit** : `test(backend): port user-domain tests from legacy + 6 SCRUM-138 sentinels (Étape 5.3, TEST-004/005/011)`

#### Étape 5.4 — Port tests engagement-domain — 8 sentinels SCRUM-144

Fichiers (legacy → engagement-service post-consolidation) :

1. `service/AttendanceServiceCoverageTest` → `engagement-service/src/test/java/ch/unige/events/engagement/attendance/service/`
2. `resource/AttendanceResourceTest` → idem `resource/`
3. `dto/AttendanceDTOTest` → idem `dto/`
4. `entity/AttendanceTest` → idem `entity/`
5. `service/CommentServiceCoverageTest` → `engagement-service/src/test/java/ch/unige/events/engagement/comment/service/`
6. `resource/CommentResourceTest` → idem
7. `resource/CommentDirectResourceTest` → idem
8. `entity/CommentTest` → idem
9. `dto/CommentDTOTest` → idem

Adaptations :
- Mocks `EventServiceClient` (anti-oracle ISSUE-92 + cascade SCRUM-136 via `getByIdWithCoOrgCheck`) et `UserServiceClient` (author enrichment).
- Tests d'attendance avec lock pessimiste : utiliser DevServices PostgreSQL et tester réellement le `LockModeType.PESSIMISTIC_WRITE`.

**Sentinels SCRUM-144 (8)** : `prePersist_setsCreatedAt`, `post_eventDraftByNonCreator_returns404_antiOracle`, `post_eventBanned_returns404_antiOracle`, `post_replyToReply_returns422_repliesTooDeep`, `post_parentInOtherEvent_returns422_parentNotInEvent`, `post_unknownParent_returns404_parentNotFound`, `delete_byPendingCoOrganizer_returns403`, `delete_unknownComment_returns404_commentNotFound`.

**Commit** : `test(backend): port engagement-domain tests from legacy + 8 SCRUM-144 sentinels (Étape 5.4, TEST-006/008)`

#### Étape 5.5 — Port tests moderation-domain

Fichiers (legacy → moderation-service post-rename) :

1. `service/ReportServiceCoverageTest` → `moderation-service/src/test/java/ch/unige/events/moderation/service/`
2. `service/ModerationCleanupServiceTest` → idem
3. `service/ModerationCleanupCoverageTest` → idem
4. `resource/AdminReportResourceTest` → idem `resource/`

Adaptations :
- Mocks `EventServiceClient` + `UserServiceClient`.
- Tests de l'auto-ban via Kafka : utiliser in-memory connector pour vérifier que `events.banned` est bien fired (pas vérifier le consumer event-service ici — c'est un test d'event-service).

**Pas de sentinel SCRUM-XXX dédié à moderation** dans la liste 35 originaux ; mais les tests legacy doivent quand même être portés pour atteindre la couverture cible 80 %.

**Commit** : `test(backend): port moderation-domain tests from legacy (Étape 5.5, TEST-009)`

#### Étape 5.6 — Validation collective — 35 sentinels par nom verts

```bash
for sentinel in \
    weekly_4Occurrences_returns3DatesSpacedBy7Days \
    monthly_handlesShortFebruaryFromJanuary31 \
    bothNull_throwsIllegalArgumentException \
    maxOccurrencesAbove52_cappedTo52 \
    from_parentRecurringEvent_exposesRecurrenceRuleAndNullParentEventId \
    from_occurrenceEvent_exposesParentEventIdAndNullRecurrenceRule \
    createRecurring_weekly4Occurrences_persists1ParentAnd3Children \
    createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded \
    createRecurring_endDateBeforeStart_returns400_recurrenceEndBeforeStart \
    createRecurring_inheritsParentStatusPublished \
    getOccurrences_parentRecurring_returnsChildrenSortedAsc \
    getOccurrences_standaloneEvent_returns200EmptyList \
    getOccurrences_draftByNonCreator_returns404_antiOracle \
    update_parentTitle_doesNotPropagateToOccurrences \
    cancel_parentDoesNotCascadeToOccurrences \
    delete_parent_setsOccurrencesParentEventIdToNull \
    post_validRecurrenceWeekly_returns201_recurrenceRuleSetOnParent \
    post_recurrenceMaxOccurrences53_returns400_beanValidation \
    getOccurrences_parentPublishedAnonymous_returns200 \
    getOccurrences_sizeOver52_returns400 \
    getOccurrences_draftByAnonymous_returns404_antiOracle \
    findAcceptedFollowedIds_returnsOnlyAcceptedUuids \
    rejectRequest_followerCanReFollowAfterReject \
    follow_selfFollow_throwsUnprocessable \
    getFollowers_privateProfileNonOwner_returns404_antiOracle \
    getPublicProfile_self_followStatusIsNull \
    getPublicProfile_authNonOwnerWithPending_followStatusIsPending \
    prePersist_setsCreatedAt \
    post_eventDraftByNonCreator_returns404_antiOracle \
    post_eventBanned_returns404_antiOracle \
    post_replyToReply_returns422_repliesTooDeep \
    post_parentInOtherEvent_returns422_parentNotInEvent \
    post_unknownParent_returns404_parentNotFound \
    delete_byPendingCoOrganizer_returns403 \
    delete_unknownComment_returns404_commentNotFound; do
    hit=$(grep -rln "void $sentinel" backend/services/*/src/test 2>/dev/null | wc -l)
    if [ "$hit" -lt 1 ]; then echo "❌ MISSING: $sentinel"; else echo "✅ $sentinel"; fi
done
```

**Cible** : 35 ✅, 0 ❌. Si un sentinel manque, créer un commit qui le rajoute (ou réutiliser un commit Étape 5.x correspondant).

#### Étape 5.7 — Validation finale couverture jacoco

```bash
cd /workspace/backend
./mvnw verify -DskipITs

# Agrégation jacoco — script bash
for r in services/*/target/jacoco-report/jacoco.xml; do
    module=$(echo "$r" | sed 's|services/\([^/]*\)/target.*|\1|')
    parsed=$(tr '>' '\n' < "$r" | grep -E 'counter type="(LINE|BRANCH)"')
    line_last=$(echo "$parsed" | grep 'type="LINE"' | tail -1)
    branch_last=$(echo "$parsed" | grep 'type="BRANCH"' | tail -1)
    lm=$(echo "$line_last" | sed -E 's/.*missed="([0-9]+)".*/\1/'); lc=$(echo "$line_last" | sed -E 's/.*covered="([0-9]+)".*/\1/')
    bm=$(echo "$branch_last" | sed -E 's/.*missed="([0-9]+)".*/\1/'); bc=$(echo "$branch_last" | sed -E 's/.*covered="([0-9]+)".*/\1/')
    [ -z "$lm" ] && lm=0; [ -z "$lc" ] && lc=0
    lt=$((lm + lc)); bt=$((bm + bc))
    lp=$(awk -v c=$lc -v t=$lt 'BEGIN{if(t>0)printf "%.1f",c*100/t; else print "0.0"}')
    bp=$(awk -v c=$bc -v t=$bt 'BEGIN{if(t>0)printf "%.1f",c*100/t; else print "0.0"}')
    printf "%-25s %6s%% L  %6s%% B\n" "$module" "$lp" "$bp"
done
```

**Cible** :
- 4 services métiers (event, user, engagement, moderation) ≥ 80 % L / ≥ 70 % B.
- 10 shared libs ≥ 95 % L / ≥ 90 % B (déjà 100 %).
- notification-service : sentinel only, n/a.

Si un service est en dessous, créer un commit `test(backend): boost <svc>-service coverage to ≥80% (TEST-NNN)`.

#### Récap fin Étape 5

**Sous-étapes commitées** : 5.1 à 5.5 (5 commits minimum, jusqu'à 10-14 si scindés selon volume) + 5.6/5.7 boosts éventuels.
**Findings adressés** : TEST-001 à TEST-018 (cat 3 audit, intégralité). TEST-016 (me-aggregator WireMock) **acté supprimé** (me-aggregator dissous).
**Validation finale** :
```bash
cd backend && ./mvnw verify -DskipITs                                  # SUCCESS
# 35 sentinels ✅ via la commande ci-dessus
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch                                                # tous verts (Sonar attendu vert post-DevOps action)
```

### Étape 6 — Pact + E2E happy path (5 commits)

**Objectif** : livrer 4 pacts JSON adaptés à la nouvelle topologie (Décision E) + 1 E2E happy path.

#### Étape 6.0 — Setup `backend/contract-tests/` module Maven

**Action** : créer le module `backend/contract-tests/` avec packaging `jar`. Ajouter au `<modules>` du parent POM.

**`backend/contract-tests/pom.xml`** :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>ch.unige.events</groupId>
        <artifactId>parent</artifactId>
        <version>1.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>contract-tests</artifactId>
    <packaging>jar</packaging>
    <name>UNIGE Events — contract tests (Pact JVM)</name>

    <properties>
        <pact.version>4.6.5</pact.version>
        <sonar.projectKey>unige-pinfo6-2026_unige-events-contract-tests</sonar.projectKey>
        <sonar.projectName>unige-events-contract-tests</sonar.projectName>
    </properties>

    <dependencies>
        <dependency>
            <groupId>ch.unige.events</groupId>
            <artifactId>shared-domain-dtos</artifactId>
            <version>${project.version}</version>
        </dependency>

        <dependency>
            <groupId>au.com.dius.pact.consumer</groupId>
            <artifactId>junit5</artifactId>
            <version>${pact.version}</version>
            <scope>test</scope>
        </dependency>

        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

Plus l'ajout au parent POM :

```xml
<module>contract-tests</module>
```

**Commit** : `chore(backend): scaffold contract-tests module (Pact JVM brokerless) (Étape 6.0)`

#### Étape 6.1 — Pact `engagement-service` ↔ `event-service` (anti-oracle ISSUE-92)

**Fichier** : `backend/contract-tests/src/test/java/ch/unige/events/contracts/EngagementEventIssue92PactTest.java`.

**Output** : `backend/contract-tests/pacts/engagement-event.json` (généré par Pact JVM en mode consumer).

**Test** :

```java
@ExtendWith(PactConsumerTestExt.class)
class EngagementEventIssue92PactTest {

    @Pact(consumer = "engagement-service", provider = "event-service")
    public RequestResponsePact getEvent_published_returns200(PactDslWithProvider builder) {
        return builder
            .given("event 42 is PUBLISHED")
            .uponReceiving("a GET /events/42 from engagement-service")
            .path("/events/42").method("GET")
            .willRespondWith().status(200)
            .body(new PactDslJsonBody()
                .integerType("id", 42L)
                .stringType("title")
                .stringValue("status", "PUBLISHED")
                .uuid("creatorId"))
            .toPact();
    }

    @Pact(consumer = "engagement-service", provider = "event-service")
    public RequestResponsePact getEvent_draftByNonCreator_returns404(PactDslWithProvider builder) {
        return builder
            .given("event 99 is DRAFT and caller is not creator")
            .uponReceiving("a GET /events/99 from engagement-service")
            .path("/events/99").method("GET")
            .willRespondWith().status(404)
            .body(new PactDslJsonBody()
                .stringValue("error", "not_found"))
            .toPact();
    }

    @Test
    @PactTestFor(pactMethod = "getEvent_published_returns200", pactVersion = PactSpecVersion.V3)
    void published_event_returns_200(MockServer mockServer) {
        // Use REST Assured against mockServer.getUrl()
        ...
    }

    @Test
    @PactTestFor(pactMethod = "getEvent_draftByNonCreator_returns404", pactVersion = PactSpecVersion.V3)
    void draft_by_non_creator_returns_404_antiOracle(MockServer mockServer) { ... }
}
```

**Commit** : `test(backend): Pact engagement-service ↔ event-service for ISSUE-92 anti-oracle (Étape 6.1, SPEC-006)`

#### Étape 6.2 — Pact `engagement-service` ↔ `event-service` (cascade SCRUM-136)

**Fichier** : `backend/contract-tests/src/test/java/ch/unige/events/contracts/EngagementEventScrum136PactTest.java`.

**Test** : asserter que `GET /events/{id}?check-co-org-of=<UUID>` retourne `EventDTO` avec champ `coOrganizerOf: bool`.

**Output** : `backend/contract-tests/pacts/engagement-event-coorg.json`.

**Commit** : `test(backend): Pact engagement-service ↔ event-service for SCRUM-136 cascade (Étape 6.2, SPEC-006)`

#### Étape 6.3 — Pact `moderation-service` ↔ `event-service`

**Fichier** : `backend/contract-tests/src/test/java/ch/unige/events/contracts/ModerationEventPactTest.java`.

**Test** : `GET /events/{id}` retourne 200 + status valide pour le ban via Kafka.

**Output** : `backend/contract-tests/pacts/moderation-event.json`.

**Commit** : `test(backend): Pact moderation-service ↔ event-service (Étape 6.3, SPEC-006)`

#### Étape 6.4 — Pact `user-service` ↔ `event-service` (calendar bulk lookup)

**Fichier** : `backend/contract-tests/src/test/java/ch/unige/events/contracts/UserEventBulkPactTest.java`.

**Test** : `GET /events?ids=42,7,1&status=PUBLISHED` retourne `List<EventDTO>` filtré par status.

**Output** : `backend/contract-tests/pacts/user-event-bulk.json`.

**Commit** : `test(backend): Pact user-service ↔ event-service for calendar ICS bulk lookup (Étape 6.4, SPEC-006)`

#### Étape 6.5 — E2E happy path

**Module** : `backend/e2e/` (nouveau, packaging jar).

**`backend/e2e/pom.xml`** :

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>ch.unige.events</groupId>
        <artifactId>parent</artifactId>
        <version>1.0-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>e2e</artifactId>
    <packaging>jar</packaging>
    <name>UNIGE Events — E2E tests</name>

    <dependencies>
        <dependency>
            <groupId>io.quarkus</groupId>
            <artifactId>quarkus-junit5</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>io.rest-assured</groupId>
            <artifactId>rest-assured</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>
```

Plus l'ajout au parent POM : `<module>e2e</module>`.

**Test** : `backend/e2e/src/test/java/ch/unige/events/e2e/E2EHappyPathTest.java`

```java
@QuarkusIntegrationTest
class E2EHappyPathTest {

    @Test
    void user_can_create_publish_get_event() {
        // Step 1: POST /api/users/me — auto-create user from JWT
        given()
            .auth().oauth2(testToken("auth0|test-creator"))
            .when().post("/api/users/me")
            .then().statusCode(200);

        // Step 2: POST /api/events — create event in DRAFT
        Long eventId = given()
            .auth().oauth2(testToken("auth0|test-creator"))
            .contentType(ContentType.JSON)
            .body(<valid create event request>)
            .when().post("/api/events")
            .then().statusCode(201)
                .extract().jsonPath().getLong("id");

        // Step 3: PATCH /api/events/{id}/publish — publish to PUBLISHED
        given()
            .auth().oauth2(testToken("auth0|test-creator"))
            .when().patch("/api/events/" + eventId + "/publish")
            .then().statusCode(200)
                .body("status", equalTo("PUBLISHED"));

        // Step 4: GET /api/events/{id} — assert creatorId enriched
        given()
            .auth().oauth2(testToken("auth0|test-creator"))
            .when().get("/api/events/" + eventId)
            .then().statusCode(200)
                .body("status", equalTo("PUBLISHED"))
                .body("creatorId", notNullValue());
    }

    private String testToken(String sub) { ... }
}
```

**Commit** : `test(backend): E2E happy path test (create user → create event → publish) (Étape 6.5, SPEC-006)`

#### Récap fin Étape 6

**Sous-étapes commitées** : 6.0, 6.1, 6.2, 6.3, 6.4, 6.5 = **6 commits**.
**Findings adressés** : SPEC-006, SPEC-018 (cat 1 audit).
**Modules ajoutés au reactor** : `contract-tests` + `e2e` = +2 modules. Reactor passe de 15 à **17 modules** total.
**Validation finale** :
```bash
cd backend && ./mvnw verify -DskipITs   # SUCCESS sur 17 modules
ls backend/contract-tests/pacts/        # 4 pacts JSON
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
```

---
### Étape 7 — CI matrix per-service YAML (3-4 commits)

**Objectif** : refondre `.github/workflows/build.yml` en `strategy.matrix.service: [event, user, engagement, moderation, notification]` (Décision F) + matrix séparée pour les 10 shared libs. Cette étape **active** la moitié POM (sonar.projectKey override) déjà livrée en Étape 12.2 de la completion-spec.

**Pré-requis Étape 7** : Étapes 4, 5, 6 vertes (la matrix doit pouvoir builder + tester avec Sonar gate green).

#### Étape 7.1 — Refonte `.github/workflows/build.yml` en matrix

**Fichier modifié** : `.github/workflows/build.yml`.

**Patch détaillé** :

```yaml
name: Build

on:
  workflow_call:

permissions:
  contents: read
  packages: write

env:
  REGISTRY: ghcr.io
  IMAGE_TAG: ${{ github.sha }}
  IMAGE_ADDITIONAL_TAGS: ${{ github.event_name == 'pull_request' && format('pr-{0}', github.event.pull_request.number) || 'latest' }}

jobs:
  build-shared-libs:
    name: Build Shared Lib (${{ matrix.lib }})
    runs-on: ubuntu-latest
    strategy:
      fail-fast: false
      matrix:
        lib:
          - shared-rate-limit
          - shared-storage
          - shared-api-error
          - shared-domain-enums
          - shared-domain-dtos
          - shared-domain-projections
          - shared-jaxrs
          - shared-tracing
          - shared-kafka-events
          - shared-platform
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v5
        with:
          java-version: 21
          distribution: temurin
          cache: maven
      - name: Build & Test
        run: ./mvnw -pl services/${{ matrix.lib }} -am verify -B
      - name: SonarQube Scan
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: ./mvnw -pl services/${{ matrix.lib }} sonar:sonar -B

  build-backend:
    name: Build Backend (${{ matrix.service }})
    runs-on: ubuntu-latest
    needs: build-shared-libs
    strategy:
      fail-fast: false
      matrix:
        service:
          - event
          - user
          - engagement
          - moderation
          - notification
    env:
      JAVA_VERSION: 21
      JAVA_DISTRIBUTION: temurin
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v5
        with:
          java-version: ${{ env.JAVA_VERSION }}
          distribution: ${{ env.JAVA_DISTRIBUTION }}
          cache: maven
      - name: Build & Test (with image build/push)
        env:
          QUARKUS_CONTAINER_IMAGE_USERNAME: ${{ github.actor }}
          QUARKUS_CONTAINER_IMAGE_PASSWORD: ${{ secrets.GITHUB_TOKEN }}
        run: |
          ./mvnw -pl services/${{ matrix.service }}-service -am verify -B \
            -Dquarkus.container-image.build=true \
            -Dquarkus.container-image.push=${{ github.event_name == 'push' || github.event_name == 'pull_request' }} \
            -Dquarkus.container-image.registry=${{ env.REGISTRY }} \
            -Dquarkus.container-image.group=${{ github.repository_owner }} \
            -Dquarkus.container-image.tag=${{ env.IMAGE_TAG }} \
            -Dquarkus.container-image.additional-tags=${{ env.IMAGE_ADDITIONAL_TAGS }} \
            -Dquarkus.jib.base-jvm-image=eclipse-temurin:21-jre
      - name: SonarQube Scan
        env:
          SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
        run: ./mvnw -pl services/${{ matrix.service }}-service sonar:sonar -B
      - name: Cleanup Docker images
        if: always()
        run: |
          docker volume prune -f
          docker image prune -f

  build-contract-and-e2e:
    name: Build Contract Tests + E2E
    runs-on: ubuntu-latest
    needs: build-shared-libs
    defaults:
      run:
        working-directory: backend
    steps:
      - uses: actions/checkout@v6
        with:
          fetch-depth: 0
      - uses: actions/setup-java@v5
        with:
          java-version: 21
          distribution: temurin
          cache: maven
      - name: Build & Test contract-tests
        run: ./mvnw -pl contract-tests -am verify -B
      - name: Build & Test e2e
        run: ./mvnw -pl e2e -am verify -B
      # Pas de Sonar pour ces deux modules (sonar.projectKey n'est pas obligatoire — projets dédiés possibles si DevOps les crée).

  build-frontend:
    name: Build Frontend
    runs-on: ubuntu-latest
    # ... INCHANGÉ depuis l'actuel
```

**Patch supplémentaire** :
- Le job `build-frontend` reste **inchangé**.
- Le job `build-backend` actuel (single-job) est **remplacé** par les 3 jobs matrix (`build-shared-libs`, `build-backend`, `build-contract-and-e2e`).
- Sequence : `build-shared-libs` doit terminer avant `build-backend` et `build-contract-and-e2e` (les services consument les libs ; via `needs:`).

**Validation locale** : pas de moyen direct de valider un workflow YAML sans push. Vérifier syntaxe via `yamllint` ou via VSCode.

**Commit** : `ci(backend): refactor build.yml to matrix per-service + shared libs (Étape 7.1, SPEC-007, INFRA-010)`

#### Étape 7.2 — Vérification CI verte

Push du commit Étape 7.1 → watch CI :

```bash
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
```

**Attendu** :
- 10 cellules `build-shared-libs (...)` ✓
- 5 cellules `build-backend (...)` ✓
- `build-contract-and-e2e` ✓
- `build-frontend` ✓
- `pr-title-check` ✓

**Échec attendu Sonar « project not found »** :
- Si DevOps n'a pas encore créé les 15 SonarCloud projects (cf. devops-handoff.md item 1 mis à jour Étape 1.2), 15 cellules SonarCloud échouent avec `project not found`. **Acceptable** — c'est le blocker DevOps formellement documenté.

**Si autre échec** : `gh run view <RUN_ID> --log-failed` → fix → commit additif → push → re-watch.

#### Récap fin Étape 7

**Sous-étapes commitées** : 7.1 (1-2 commits si itération sur erreurs CI).
**Findings adressés** : SPEC-007, SPEC-019, INFRA-010, INFRA-012 (cat 1 + 6 + 7 audit).
**Validation finale** :
```bash
gh pr checks 158
# Build matrix x15 ✓ (sauf Sonar gate qui dépend DevOps)
# Build frontend ✓
# Deploy preview ✓ (5 services pulled, pulled rapidement avec moins de pods)
```

### Étape 8 — Documentation finale + PR body (6 commits)

**Objectif** : aligner toute la documentation avec l'état post-finalization. Mise à jour PR body de #158 via `gh pr edit`.

#### Étape 8.1 — `architecture.md` réécriture totale post-finalization

**Fichier** : `backend/docs/architecture.md`.

**Patch** : la section « Vue d'ensemble — topologie microservices » + le tableau « Microservices — endpoints owned » + le diagramme ASCII + les notes inter-service sont réécrits pour la topologie 5 services. Reproduire les sections de la § Architecture cible post-finalization de la présente spec.

Les sections sur les schedulers (EventExpirationJob, ModerationCleanupJob), le ModerationCleanupJob path (events.banned via Kafka), le flux d'une requête typique (`POST /events/{id}/comments` post-consolidation), tout est mis à jour.

**Commit** : `docs(backend): rewrite architecture.md for post-finalization 5-service topology (Étape 8.1)`

#### Étape 8.2 — `data-model.md` cohérence finale

**Fichier** : `backend/docs/data-model.md`.

**Patch** :
- Section `### User` : owner = user-service (inchangé).
- Section `### Event` : owner = event-service (inchangé). Note Kafka ajustée : `events.banned` consumed par event-service (inchangé), `co-organizers.{invited,accepted}` produced par event-service (post-merge).
- Section `### Favorite` : owner = event-service (post-merge, anciennement favorite-service).
- Section `### EventView` : owner = event-service (post-merge).
- Section `### Attendance` : owner = engagement-service (post-rename).
- Section `### EventCoOrganizer` : owner = event-service (post-merge).
- Section `### Follow` : owner = user-service (post-merge, anciennement follow-service).
- Section `### Report` : owner = moderation-service (post-rename).
- Section `### Comment` : owner = engagement-service (post-merge).

**Commit** : `docs(backend): align data-model.md with post-finalization owners (Étape 8.2)`

#### Étape 8.3 — `internal-endpoints.md` mise à jour catalogue

**Fichier** : `backend/docs/internal-endpoints.md`.

**Patch** : reproduire la table « Endpoint catalog » avec les 4 endpoints internes finaux (cf. § Architecture cible post-finalization de la présente spec) :

- `GET /events/{eventId}/attendance-summary` (engagement-service)
- `GET /events?ids=...&status=PUBLISHED` (event-service, bulk)
- `GET /events/{id}?check-co-org-of={uuid}` (event-service, cascade SCRUM-136)
- `GET /users/{id}/attendances?status=ATTENDING` (engagement-service, réutilisé interne)

Retirer les endpoints internes hypothétiques de la version pré-finalisation qui ne sont plus nécessaires :
- `GET /users/by-auth0/{auth0Id}` — supprimé (cf. note Étape 4.2).
- `GET /users/by-calendar-token/{token}` — supprimé (post-merge calendar→user, accès local).
- `GET /events/{id}/capacity-summary` — supprimé (capacity calculée localement dans event-service post-merge).
- `GET /events/{id}/favorite-count` — supprimé (favorites locales post-merge).
- `GET /events/{id}/view-count` — supprimé.
- `GET /events/{eventId}/co-organizers/check?userId=` — supprimé (cascade locale via param `?check-co-org-of=` sur GET /events/{id}).
- `GET /events/{eventId}/co-organizers/accepted-user-ids` — supprimé (consommateurs disparus).
- `GET /users/{id}/follow-counts` — supprimé (follows locales post-merge dans user-service).

**Commit** : `docs(backend): update internal-endpoints.md catalog post-finalization (Étape 8.3)`

#### Étape 8.4 — `dev-guide.md` cohérence finale

**Fichier** : `backend/docs/dev-guide.md`.

**Patch** :
- Section « Layout Maven » : passer de 24 modules à **17 modules** (5 services + 10 shared libs + contract-tests + e2e).
- Section « Workflow modifier le schéma » : adapter à la post-consolidation (les tables `event_views`, `favorites`, `event_co_organizers` sont possédées par event-service maintenant).

**Commit** : `docs(backend): align dev-guide.md with 17-module reactor (Étape 8.4)`

#### Étape 8.5 — `sprint-context.md` Étape 20 (post-finalization)

**Fichier** : `backend/docs/sprint-context.md`.

**Patch** : ajouter une nouvelle section :

```markdown
## Sprint 8 — Finalization (post-completion) — 2026-MM-DD

Livré (cf. specs_microservices_migration_finalization.md, ~9 étapes,
~30-40 commits) :
- Étape 1 : doc préparatoire consolidation-plan.md + devops-handoff.md item 1 update.
- Étape 2 : consolidation 14→5 services (11 merges + renames).
- Étape 3 : cleanup post-consolidation (architecture.md + AGENTS.md alignés).
- Étape 4 : 8 REST clients + 4.0 bascule shared libs + suppression 37 stubs JPA.
- Étape 5 : 1818 tests legacy portés ; 35 sentinels SCRUM-138/139/144/147 verts.
- Étape 6 : 4 pacts + 1 E2E happy path (modules contract-tests + e2e).
- Étape 7 : CI matrix per-service YAML (5 services + 10 libs + e2e/contract).
- Étape 8 : doc finale + PR body.
- Étape 9 : vérification finale.

État final :
- 5 services métiers + 1 placeholder (notification SCRUM-99).
- 10 shared libs + 2 nouveaux modules (contract-tests, e2e) = 17 modules total.
- Couverture jacoco services métiers ≥ 80 % L / ≥ 70 % B.
- 35 sentinels SCRUM par nom verts.
- 0 JPA stub cross-service.
- CI matrix activée.
- PR #158 prête à merger (Elie).
```

Plus mise à jour de la table « Écarts vs spec » pour refléter post-finalization.

**Commit** : `docs(backend): record Étape 20 post-finalization in sprint-context.md (Étape 8.5)`

#### Étape 8.6 — PR body de #158 final

**Action** :

```bash
gh pr view 158 --json body --jq .body > /tmp/pr-body-finalization.md
# Édition manuelle pour refléter:
#   - 5 services + 1 placeholder (au lieu de 13+1)
#   - REST clients livrés (8)
#   - Tests legacy portés (35 sentinels)
#   - Pacts livrés (4)
#   - E2E livré
#   - CI matrix activée (15 cellules)
#   - 0 JPA stub
gh pr edit 158 --body-file /tmp/pr-body-finalization.md
```

Pas de commit Git (action `gh pr edit`). Documenté dans sprint-context Étape 20.

#### Récap fin Étape 8

**Sous-étapes commitées** : 8.1, 8.2, 8.3, 8.4, 8.5 (5 commits) + 8.6 (gh edit).
**Findings adressés** : DOC-001 à DOC-024 (cat 8 audit) — déjà adressés dans completion-spec, ré-aligned ici sur la nouvelle topologie.
**Validation finale** :
```bash
cd backend && ./mvnw verify -DskipITs   # SUCCESS (pas de change code)
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
```

### Étape 9 — Vérification finale

**Objectif** : valider que la finalisation est livrée + tous les invariants tenus.

#### Étape 9.0 — Build + tests verts en local

```bash
cd /workspace/backend
./mvnw verify -DskipITs   # ~3-4 min sur 17 modules
# Attendu : SUCCESS sur tous les modules
```

#### Étape 9.1 — Couverture jacoco par module

```bash
# Réutiliser le script bash agrégateur de la § Étape 5.7
# Cible:
#   shared-* libs: ≥ 95 % L / ≥ 90 % B
#   event-service: ≥ 80 % L / ≥ 70 % B
#   user-service: ≥ 80 % L / ≥ 70 % B
#   engagement-service: ≥ 80 % L / ≥ 70 % B
#   moderation-service: ≥ 80 % L / ≥ 70 % B
#   notification-service: n/a (sentinel only)
```

Si un service est en dessous, créer un sous-commit `test(backend): boost <svc>-service coverage to ≥80% (Étape 9.1)`.

#### Étape 9.2 — Sentinels SCRUM-138/139/144/147 verts par nom

(Réutiliser le script bash de l'Étape 5.6 — doit retourner 35 ✅, 0 ❌.)

#### Étape 9.3 — 0 JPA stub cross-service

```bash
find backend/services -name '*Stub.java' -not -path '*/target/*'
# Attendu : 0 résultat
```

Si > 0, suppression à finaliser dans un commit `refactor(backend): remove last JPA stubs (Étape 9.3)`.

#### Étape 9.4 — Invariants frontend + openapi

```bash
git diff --shortstat origin/main HEAD -- frontend/      # 0 lignes ABSOLU
git diff --shortstat origin/main HEAD -- openapi/       # 0 lignes ABSOLU (Décision G)
```

#### Étape 9.5 — Topology cible

```bash
ls backend/services/ | grep -E '\-service$' | sort
# Attendu (5):
#   engagement-service
#   event-service
#   moderation-service
#   notification-service
#   user-service

ls backend/services/ | grep -E '^shared-' | sort
# Attendu (10):
#   shared-api-error
#   shared-domain-dtos
#   shared-domain-enums
#   shared-domain-projections
#   shared-jaxrs
#   shared-kafka-events
#   shared-platform
#   shared-rate-limit
#   shared-storage
#   shared-tracing

grep -c '<module>' backend/pom.xml   # 17 attendu (15 services-modules + contract-tests + e2e)

# Topology Helm
ls k8s/chart/templates/ | grep -E '\-service$' | sort
# Attendu (5):
#   engagement-service
#   event-service
#   moderation-service
#   notification-service
#   user-service
```

#### Étape 9.6 — CI verte

```bash
git push origin 'refactor(backend)--migrate-to-microservices'   # tous les commits Étapes 1-8 pushés
gh pr checks 158 --watch
```

**Attendu** :
- 10 cellules `build-shared-libs` ✓
- 5 cellules `build-backend` ✓
- `build-contract-and-e2e` ✓
- `build-frontend` ✓
- 15 cellules SonarCloud ✓ (post DevOps creation des 15 projets) OU « project not found » sur certains (acceptable, blocker DevOps)
- `Deploy / Deploy to Preview` ✓ (5 services Ready, 4 pods backend en runtime + Kong + infra)
- `PR Title Check` ✓

**Si SonarCloud échoue spécifiquement « project not found »** : c'est attendu (DevOps doit créer les 15 SonarCloud projects post-consolidation). Acceptable. Documenter dans le PR body Étape 8.6.

#### Étape 9.7 — PR body final + handoff

Action : `gh pr edit 158 --body-file <final body>` + commentaire sur la PR : « Finalisation livrée, prête pour DevOps handoff. Cf. backend/docs/devops-handoff.md item 1 mis à jour avec la liste des 15 SonarCloud projects. »

Pas de commit Git.

#### Récap fin Étape 9

**Sous-étapes** : 9.0 à 9.7 (validation, pas de commits sauf si nettoyages tardifs en 9.1/9.3).

**Si toute la chaîne est verte** : la finalisation est terminée. L'humain Elie merge la PR quand il valide.

---

## Stratégie de tests (cible post-finalization)

### Niveau 1 — Tests unitaires par service

- **Cible** : ≥ 80 % L / ≥ 70 % B par service métier (matche Sonar gate). ≥ 95 % L / ≥ 90 % B par shared lib.
- **Outils** : JUnit 5, Mockito, `@QuarkusTest`, `@TestSecurity`, RestAssured.
- **Scope** : chaque méthode publique des services applicatifs + chaque branche notable. Sentinels SCRUM-138/139/144/147 par nom.

### Niveau 2 — Tests d'intégration par service

- **Cible** : tous les endpoints REST exposés sont testés (200/4xx/5xx happy + sad path) avec REST clients mockés.
- **Outils** : `@QuarkusTest` + DevServices PostgreSQL + RestAssured + `@InjectMock @RestClient <X>ServiceClient` pour les REST clients sortants. WireMock optionnel pour les tests qui couvrent la sérialisation HTTP elle-même.
- **Profile `%test`** : `quarkus.oidc.enabled=false`, `mp.messaging.outgoing.<chan>.connector=smallrye-in-memory`.

### Niveau 3 — Tests Pact (contract)

- **Cible** : 4 pacts JSON commités dans `backend/contract-tests/pacts/`.
- **Pacts** :
  1. `engagement-event.json` — anti-oracle ISSUE-92 (`GET /events/{id}` retourne 200 PUBLISHED ; 404 DRAFT non-créateur).
  2. `engagement-event-coorg.json` — cascade SCRUM-136 (`GET /events/{id}?check-co-org-of={uuid}` retourne `coOrganizerOf: bool`).
  3. `moderation-event.json` — `GET /events/{id}` retourne 200 + status valide.
  4. `user-event-bulk.json` — `GET /events?ids=...&status=PUBLISHED` retourne `List<EventDTO>`.
- **Pact JVM brokerless** : les pacts sont produits par les consommateurs et vérifiés en local par les providers (pas de Pact Broker tiers).

### Niveau 4 — E2E happy path

- **Cible** : 1 test `@QuarkusIntegrationTest` qui traverse 3-4 services (user → event → publish → get).
- **Path** : `backend/e2e/src/test/java/ch/unige/events/e2e/E2EHappyPathTest.java`.
- **Scope** : POST users/me → POST events → PATCH /events/{id}/publish → GET /events/{id}. Optionnel : assert events.published Kafka via in-memory connector.

---

## Risques et mitigations

| Risque | Probabilité | Impact | Mitigation |
|---|---|---|---|
| **Régression de sécurité sur anti-oracle ISSUE-92/93 lors du passage REST clients** (Étape 4) | Medium | Critical | Sentinels SCRUM-138/144 explicitement dans la liste 35 sentinels (ressortir verts par nom Étape 5.6) ; pact ISSUE-92 (Étape 6.1) ; pact SCRUM-136 (Étape 6.2). |
| **Drift de payload Kafka après consolidation** (producer change de service) | Low | High | Lib `shared-kafka-events` (déjà livrée) — un seul record par topic, partagé entre producer et consumer ; merge ne touche pas au record. |
| **Échec du merge X→Y au milieu (rename packages incomplet)** | Medium | High | Build local après chaque merge (Étape 2.X) ; rollback trivial avant push (`git reset --hard HEAD~1`). |
| **Conflit de classe homonyme post-merge** (ex. ApiErrorResponse local share + local event) | Medium | Medium | Le sous-package `ch.unige.events.<Y>.<X>.dto.ApiErrorResponse` évite la collision ; la migration vers `shared-api-error` (Étape 4.0) supprime les copies locales — la collision est temporaire entre Étape 2 et Étape 4. Si gênante, anticiper la suppression locale. |
| **Tests legacy qui référencent des stubs supprimés** (Étape 5 sur du code post-Étape 4) | High | Medium | Adaptation par cas : remplacer `<X>Stub.findByYyy(...)` par `@InjectMock <X>ServiceClient` ou par l'entité réelle si table locale post-consolidation. |
| **CI matrix non activable** parce que les 15 SonarCloud projects n'existent pas | High | Medium | YAML produit + documenté dans `devops-handoff.md` item 1. Si SonarCloud échoue spécifiquement sur « project not found », c'est un blocker DevOps, pas backend. |
| **Coût de migration des tests legacy** (Étape 5) | High | Medium | Portage mécanique avec `git show 41074e9:` ; les tests qui ne fittent pas le nouveau modèle (ex. utilisaient `me-aggregator`) sont adaptés au cas par cas ou supprimés (cf. note 5 Décision D). |
| **Performance LAN cross-service** : un GET event via REST client ajoute 5-10 ms latence | Low | Low | Acceptable en preview (LAN dev). En prod, viser `@CircuitBreaker` + cache local (futur, Décision B option Kafka projection completion-spec). Beaucoup moins de hops post-consolidation. |
| **Crash window CDI `AFTER_SUCCESS`** (microsecondes entre commit et fire) | Low | Low | Acceptable pour topics non-critiques (notifications). Outbox pattern en alternative future si critère devient critical. |
| **Build Maven temps total** (17 modules au lieu de 24) | Low | Low | Estimer ~3-4 min (mieux qu'avant). CI matrix per-service (Étape 7) parallélise sur 15 cellules. |
| **Régression du quality gate Sonar** | High | Medium | Attendu pendant Étape 5 (port des tests). Une fois Étape 5 terminée, coverage devrait remonter à ≥ 80 % par service. Les anciens projects SonarCloud orphelins sont documentés (DevOps peut archiver). |

---
## Critères de done (checklist linéaire)

- [ ] **Décisions A à J tranchées** + appliquées (cf. § Décisions techniques de cette spec).
- [ ] **Étapes 0 à 9** livrées en commits séparés sur la branche persistante `refactor(backend)--migrate-to-microservices`.
- [ ] `cd backend && ./mvnw verify -DskipITs` **vert** local sur les **17 modules** (5 services + 10 libs + contract-tests + e2e).
- [ ] `gh pr checks 158` tous **verts** (Build matrix×15 + frontend + contract-and-e2e + Deploy Preview + PR Title Check) — sauf si SonarCloud échoue spécifiquement sur « project not found » sur l'un des 15 projets non encore créés DevOps.
- [ ] `git diff --shortstat origin/main HEAD -- frontend/` = **0 ligne** ABSOLU.
- [ ] `git diff --shortstat origin/main HEAD -- openapi/` = **0 ligne** ABSOLU (annulation dérogation Q).
- [ ] **Topology** : 5 services (event, user, engagement, moderation, notification) + 10 shared libs + 2 modules tests = 17 modules dans le reactor.
- [ ] **35 sentinels** SCRUM-138/139/144/147 verts par nom (cf. Étape 5.6).
- [ ] **Coverage jacoco** ≥ 80 % L + ≥ 70 % B par service métier (event/user/engagement/moderation) ; ≥ 95 % L + ≥ 90 % B par shared lib (déjà 100 %).
- [ ] **9 producteurs Kafka livrés** (topics non vides en preview env post-deploy) — inchangé depuis completion-spec.
- [ ] **1 consumer Kafka livré** (event-service ← `events.banned`) — inchangé.
- [ ] **0 JPA stub** cross-service (`find backend/services -name '*Stub.java' -not -path '*/target/*'` → vide).
- [ ] **8 REST clients** `@RegisterRestClient` livrés (2 dans shared-domain-dtos comme interfaces partagées + 6 cas d'usage spécifiques aux consumers).
- [ ] **4 pacts JSON** commités dans `backend/contract-tests/pacts/`.
- [ ] **1 E2E happy path** vert dans `backend/e2e/`.
- [ ] **CI matrix YAML** activée (10 shared-libs cells + 5 backend cells + 1 contract-and-e2e + 1 frontend = 17 cellules).
- [ ] **Plugin Kong rate-limiting** actif (≥ 3 routes avec buckets) — inchangé depuis completion-spec.
- [ ] **livenessProbe** sur 5 deployments (event/user/engagement/moderation/notification — au lieu de 13).
- [ ] **`backend/docs/consolidation-plan.md`** créé (Étape 1.1).
- [ ] **`backend/docs/devops-handoff.md`** mis à jour avec 5 services + 10 libs SonarCloud (Étape 1.2 + 8.5).
- [ ] **`backend/docs/internal-endpoints.md`** mis à jour avec 4 endpoints internes finaux (Étape 8.3).
- [ ] **`backend/docs/architecture.md`** réécrit pour topologie post-finalization (Étape 8.1).
- [ ] **`backend/docs/data-model.md`** aligned avec nouveaux owners (Étape 8.2).
- [ ] **`backend/docs/dev-guide.md`** aligned avec 17 modules (Étape 8.4).
- [ ] **`backend/docs/sprint-context.md`** Étape 20 enregistrée (Étape 8.5).
- [ ] **`backend/AGENTS.md`** aligned avec 5 services + 10 libs (Étape 3.5).
- [ ] **PR body** mis à jour via `gh pr edit 158 --body-file ...` (Étape 8.6).
- [ ] **PR pas mergée** — Elie Bussod merge lui-même après validation.
- [ ] **Sentinels d'invariants** : `shared-rate-limit` 100 % couverture intacte ; `shared-storage` 100 % couverture intacte ; les 8 nouvelles shared libs 100 % couverture intactes.

---

## Livrable FINAL attendu

**Titre PR (inchangé)** : `chore(backend): migrate to microservices architecture with Kong gateway and Kafka broker`
(NB : workaround `pr-title-check.yml` documenté ; non régression depuis bee933d.)

**Body PR final** (template à coller dans `gh pr edit 158 --body-file`) :

```markdown
## Résumé

Sprint 8 — migration backend monolithe → microservices **livrée + complétée + finalisée**. **5 services métiers** (event, user, engagement, moderation, notification placeholder SCRUM-99) + 10 shared libs + 2 modules tests (contract-tests, e2e). Consolidation 14→5 services pour rationaliser ops + clarifier bounded contexts. Kong gateway DB-less + Kafka KRaft (10 topics, 9 producteurs + 1 consumer), 8 REST clients cross-service avec résilience, observability (logs JSON + Prometheus + X-Request-ID), 4 pacts + 1 E2E, Kong rate-limiting plugin, livenessProbe sur 4 deployments métier, CI matrix per-service activée, 35 sentinels SCRUM-138/139/144/147 verts par nom.

> **Titre** : la spec demande `refactor(backend): migrate to microservices...` mais [`pr-title-check.yml`](.github/workflows/pr-title-check.yml) impose le scope `scrum-XXX` pour `refactor`. Workaround : `chore(backend):` (scope libre). Bug documenté dans [`sprint-context.md`](backend/docs/sprint-context.md).

### 5 services + 10 shared libs + 2 modules tests

| # | Service | Endpoints owned | Tables | Kafka |
|---|---|---|---|---|
| 1 | **event-service** | `/events*`, `/admin/events*`, `/events/search`, `/events/featured`, `/events/{id}/image`, `/events/{id}/share`, `/s/{shortCode}`, `/events/{id}/view`, `/events/{id}/favorite`, `/users/me/favorites`, `/events/{id}/co-organizers/*`, `/users/me/co-organizer-invitations`, `/events/{id}/stats`, `/users/me/events` | `events`, `event_tags`, `event_views`, `favorites`, `event_co_organizers` | producer events.{published,cancelled,expired} + co-organizers.{invited,accepted} ; consumer events.banned |
| 2 | **user-service** | `/users/me`, `/users/{id}`, `/users/me/image`, `/users/me/banner`, `/users/{id}/follow*`, `/follow-requests/*`, `/users/me/follow-requests`, `/users/me/calendar-token*`, `/calendar/{token}.ics` | `users`, `user_interests`, `follows` | producer users.{followed,follow-requested,follow-accepted} |
| 3 | **engagement-service** | `/events/{id}/attend*`, `/users/me/attendances`, `/users/me/participations`, `/events/{id}/comments`, `/comments/{id}` | `attendances`, `comments` | producer comments.created |
| 4 | **moderation-service** | `/events/{id}/report`, `/admin/reports*` + ModerationCleanupJob | `reports` | producer events.banned |
| 5 | **notification-service** | (placeholder, replicas:0, SCRUM-99) | — | — |

**Shared libs (10)** : `shared-rate-limit`, `shared-storage`, `shared-api-error`, `shared-domain-enums` (9 enums), `shared-domain-dtos` (UserPublicResponse + 4 cross-service records + 2 REST client interfaces UserServiceClient/EventServiceClient/EngagementServiceClient), `shared-domain-projections` (EventCapacity + Auth0IdResolver), `shared-jaxrs` (Timeframe + ParamConverter), `shared-tracing` (RequestIdFilter + RequestIdClientFilter), `shared-kafka-events` (5 records), `shared-platform` (ServiceIdentityResource paramétrisable).

**Modules tests** : `backend/contract-tests/` (Pact JVM brokerless, 4 pacts), `backend/e2e/` (1 E2E happy path).

### Étape 18 + complétion + finalisation (au-dessus de PR #158 baseline `bee933d`)

- **Étape 18** (commits `446ea3e`..`bee933d`) — `@PerUserRateLimit` restauré, `FileStorageService` consolidé, 3 producteurs Kafka pilote.
- **Complétion** (commits `0383f98`..`5346342`) — étapes 1, 2, 3, 4, 9, 10, 11, 12.2/3/4, 13.6/7/10 de la completion-spec.
- **Finalisation** (commits `<5346342>+1`..tip) — adresse les étapes 5, 7, 8, 12.1 reportées + nouveau item de consolidation 14→5 services. Cf. [`specs_archives/specs_claude/specs_microservices_migration_finalization.md`](specs_archives/specs_claude/specs_microservices_migration_finalization.md). Highlights :
  - Étape 1 : doc préparatoire `consolidation-plan.md`.
  - Étape 2 : 11 merges + 2 renames pour passer de 13 services actifs à 4 services métiers actifs.
  - Étape 4 : 8 REST clients @RegisterRestClient + suppression 37 stubs JPA + bascule shared libs.
  - Étape 5 : 1818 tests legacy portés via `git show 41074e9:` + 35 sentinels verts par nom.
  - Étape 6 : 4 pacts JSON + 1 E2E.
  - Étape 7 : CI matrix per-service (15 cellules).
  - Étape 8 : doc finale + PR body (présent fichier).

### CI / Sonar

- Build matrix shared-libs ✓ (10 cellules)
- Build matrix backend ✓ (5 cellules)
- Build contract-and-e2e ✓
- Build Frontend ✓
- SonarQube Cloud ✓ — Quality Gate **passed** sur **chacun** des 15 projects per-module (pré-requis : 5+10 = 15 projects créés côté DevOps, cf. backend/docs/devops-handoff.md item 1)
- Deploy to Preview ✓ — 4 services Ready, 10 topics Kafka non-vides après quelques actions, `/q/metrics` exposé en interne
- PR Title Check ✓

### Invariants tenus

- `git diff --shortstat origin/main HEAD -- frontend/` = **0 ligne** ✅
- `git diff --shortstat origin/main HEAD -- openapi/` = **0 ligne** ✅ (annulation Décision Q par Décision G de la spec finalization)
- 35 sentinels SCRUM-138/139/144/147 verts par nom ✅
- 0 JPA stub cross-service ✅

### What's NOT in this PR (DevOps follow-up)

| Item | Suite |
|---|---|
| **DB-per-service** (schémas séparés via Flyway) | DevOps S9+ — différé formellement par Décision C de la completion-spec |
| **Cluster Kafka prod-grade** (RF=3, partitions ≥ 3, ISR ≥ 2) | DevOps S9+ |
| **Kong production** (DB-mode, OpenTelemetry, plugin rate-limiting policy=redis cluster-wide) | DevOps S9+ |
| **NetworkPolicies K8s** | DevOps S9+ |
| **15 SonarCloud projects** + secret `SONAR_TOKEN` | DevOps action one-shot ; cf. [`backend/docs/devops-handoff.md`](backend/docs/devops-handoff.md) item 1 |
| **Doppler secrets prod** | DevOps |
| **Domaines / certs / Cloudflare tunnel preview** | DevOps |
| **Doublon openapi `POST /events/{id}/view`** | PR future avec coordination frontend (cf. Décision G annulation dérogation Q) |

## Documentation

- [x] Spec originale : [`specs_archives/specs_claude/specs_microservices_migration.md`](specs_archives/specs_claude/specs_microservices_migration.md)
- [x] Audit post-PR-158 : [`specs_archives/audit_pr158_microservices_migration.md`](specs_archives/audit_pr158_microservices_migration.md)
- [x] Spec de complétion : [`specs_archives/specs_claude/specs_microservices_migration_completion.md`](specs_archives/specs_claude/specs_microservices_migration_completion.md)
- [x] **Spec de finalisation** : [`specs_archives/specs_claude/specs_microservices_migration_finalization.md`](specs_archives/specs_claude/specs_microservices_migration_finalization.md)
- [x] Sprint context (Étape 20) : [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md)
- [x] Architecture finale : [`backend/docs/architecture.md`](backend/docs/architecture.md)
- [x] Plan de consolidation 14→5 : [`backend/docs/consolidation-plan.md`](backend/docs/consolidation-plan.md)
- [x] DevOps handoff : [`backend/docs/devops-handoff.md`](backend/docs/devops-handoff.md)
- [x] Internal endpoints : [`backend/docs/internal-endpoints.md`](backend/docs/internal-endpoints.md)
- [x] Roadmap (PR statuses + completion + finalization) : [`backend/docs/microservices-migration-roadmap.md`](backend/docs/microservices-migration-roadmap.md)

## Test plan

- [x] CI matrix : 10 shared-libs cells + 5 backend cells + 1 contract-and-e2e + 1 frontend + PR title — verts (sauf si DevOps n'a pas créé les 15 SonarCloud projects)
- [x] `cd backend && ./mvnw verify -DskipITs` → 17 modules SUCCESS en ~3-4 min
- [x] `git diff --shortstat frontend/` = 0
- [x] `git diff --shortstat openapi/` = 0
- [x] 35 sentinels SCRUM par nom verts
- [x] 0 JPA stub cross-service
- [x] 8 REST clients @RegisterRestClient livrés
- [x] 4 pacts JSON dans `backend/contract-tests/pacts/`
- [x] 1 E2E happy path vert
- [x] Couverture jacoco ≥ 80 % L par service métier
- [x] 9 producteurs Kafka livrés ; 1 consumer events.banned
- [ ] (à valider preview) `curl /api/users/me` 401 ; `curl /api/events` 200 ; `curl /api/events/{id}/share` 200 (event-service) ; `/q/metrics` exposé en interne ; topics Kafka non-vides après quelques actions

🤖 Generated with [Claude Code](https://claude.com/claude-code) — exécution autonome bypass-permissions, suivant la spec finalization.
```

---

## Workflow Git imposé à l'exécuteur

- **Branche persistante** : `refactor(backend)--migrate-to-microservices` (NE PAS créer de nouvelle branche).
- **Pas de squash** — chaque sous-étape numérotée a son propre commit (granularité 5-50 fichiers, ≤ 500 lignes diff sauf consolidation Étape 2 = 100-300 lignes/merge).
- **Pas de force push** — additif uniquement.
- **Pas de `--no-verify`** — si pre-commit hook échoue, fixer la cause racine.
- **Pas de `--no-gpg-sign`** — signage Git par défaut respecté.
- **Pas de `--amend`** sur du commit pushé — fixer via nouveau commit.
- **Pas de modification de `main`** ni des autres branches feature (`feature/s7-recurrence`, etc.).
- **Push après chaque sous-étape verte** : `git push origin 'refactor(backend)--migrate-to-microservices'`.
- **Watch CI groupé par étape majeure** (pas par sous-étape — directive explicite Elie pour économiser temps) : `gh pr checks 158 --watch` à la fin de chaque Étape ≥ 2 jusqu'à terminaison.
- **Si CI échoue (hors Sonar project not found)** : `gh run view <RUN_ID> --log-failed` → fix → nouveau commit additif → push → re-watch.
- **Mise à jour `sprint-context.md` Étape 20** : un patch incrémental après chaque étape, **sans commit dédié à chaque step** (regroupé en commit final d'Étape 8.5).
- **Mise à jour PR body via `gh pr edit 158 --body-file`** : à la toute fin (Étape 8.6), pas en milieu de parcours.
- **Pas de merge PR #158** — Elie Bussod merge lui-même quand il valide.

---

## Frontière DevOps — items NON couverts par cette spec

Les 7 items DevOps suivants restent **hors scope** (cf. completion-spec Décision V + devops-handoff.md). La présente spec de finalisation **ne les retouche pas** sauf pour mettre à jour l'item 1 (15 SonarCloud projects post-consolidation).

| # | Item | Justification report S9+ | Backend a-t-il livré sa moitié ? |
|---|---|---|---|
| 1 | Création de **5 services + 10 shared libs = 15 SonarCloud projects** + secret `SONAR_TOKEN` | Nécessite SonarCloud admin UI ; le YAML CI matrix l'attend (cf. Étape 7) | ✅ YAML CI matrix livré (Étape 7) ; sonar.projectKey override per-module livré (completion-spec Étape 12.2) |
| 2 | Cluster Kafka prod-grade (RF=3, partitions ≥ 3, ISR ≥ 2, durabilité acks=all) | Hors scope cours, single-broker S8 OK | ✅ Helm chart single-broker livré |
| 3 | Schemas-per-service (Flyway séparé) | Reportée par Décision C completion-spec | ❌ aucune action backend, déviation actée |
| 4 | NetworkPolicies K8s | Hors scope code, pure ops K8s | N/A |
| 5 | Domaines / certs prod / Cloudflare tunnel preview | Hors scope code | N/A |
| 6 | Secrets Doppler `DB_PASSWORD`, `OIDC_*`, `S3_*`, `KAFKA_BOOTSTRAP_SERVERS`, `FRONTEND_URL`, `TZ=Europe/Zurich` | Hors scope code, pure ops | ✅ defaults bidons retirés en complétion (SEC-004) |
| 7 | Production-grade Kong (DB-mode, OpenTelemetry, rate-limiting policy=redis cluster-wide) | Hors scope cours, DB-less S8 OK ; rate-limiting `policy: local` livré | ✅ rate-limiting `policy: local` livré (completion-spec Étape 10) |

**L'exécuteur autonome ne touche à AUCUN de ces 7 items.** Toute action de finalisation qui dépasse « config YAML lue par Helm » ou « rename d'un module Maven » doit être déférée DevOps avec note explicite dans `devops-handoff.md`.

**Update prévu** dans la présente spec (Étape 1.2 + 8.5) :
- Item 1 : 13 services + 8 libs nouvelles = 23 SonarCloud projects → **5 services + 10 libs = 15 projects** post-consolidation. Liste mise à jour avec les noms post-consolidation. Note explicite que les anciens projects orphelins peuvent être archivés ou laissés.

---

## Annexes

### Annexe A — Mapping finding audit → étape spec (132 findings)

(Cette annexe reproduit le mapping de la completion-spec Annexe A, avec mise à jour des findings encore non livrés. Les findings livrés en complétion sont marqués ✅. Ceux à livrer en finalisation sont mappés vers les Étapes 0-9 de la présente spec.)

#### Cat 1 — Conformité spec (22 findings)

| Finding | Statut completion | Étape finalisation | Note |
|---|---|---|---|
| SPEC-001 | ✅ DEFERRED-S9+ acté Décision C | (inchangé) | Schémas-per-service S9+ |
| SPEC-002 | ❌ reporté Étape 5 | **Étape 4** | REST clients cross-service livrés |
| SPEC-003 | ✅ Étape 10 livrée | (inchangé) | Kong rate-limiting plugin |
| SPEC-004 | ✅ Étape 9 livrée (deps) + ✅ Étape 4 (rest-client-reactive avec REST clients) | **Étape 4** | 4ème dep `rest-client-reactive` livrée via REST clients |
| SPEC-005 | ✅ Étape 4 livrée | (inchangé) | 9 producteurs Kafka |
| SPEC-006 | ❌ reporté Étape 8 | **Étape 6** | Pact + E2E livrés |
| SPEC-007 | ❌ reporté Étape 12.1 | **Étape 7** | CI matrix YAML livrée |
| SPEC-008 | ✅ Étape 12.4 livrée | (inchangé) | image.tag rename |
| SPEC-009 | ❌ partiellement (me-aggregator devait devenir vrai BFF) | **Décision A + Étape 2.2.6** | me-aggregator SUPPRIMÉ — décision inverse plus saine, BFF disparaît |
| SPEC-010 | ✅ Étape 13.6 livrée | (inchangé) | notification-service formalisé devops-handoff |
| SPEC-011 | ❌ reporté Étape 5 (FK cross-service) | **Étape 4** | FK cross-service supprimées avec stubs |
| SPEC-012 | ✅ Étape 9.1 livrée | (inchangé) | quarkus-logging-json |
| SPEC-013 | ✅ Étape 3.4 + 9.2 livrés | (inchangé) | shared-tracing créée + consommée |
| SPEC-014 | ❌ reporté Étape 5 (endpoint co-org check) | **Étape 4.1** | Param `?check-co-org-of=` ajouté à `GET /events/{id}` (endpoint interne unique au lieu de séparé) |
| SPEC-015 | (acté no-op) | (inchangé) | Pas de plugin Kong jwt — décision spec orig retenue |
| SPEC-016 | (acté no-op) | (inchangé) | Tests Kafka in-memory acté ; Testcontainers pas livré |
| SPEC-017 | ✅ Étape 0 préflight | (inchangé) | Branche persistante |
| SPEC-018 | ❌ reporté Étape 8 | **Étape 6** | Tests integration cible — Pact + E2E |
| SPEC-019 | ✅ Étape 12.2 + ❌ reporté Étape 12.1 | **Étape 7** | sonar.projectKey override + matrix YAML |
| SPEC-020 | (acté no-op) | (inchangé) | docker-compose.dev pas livré |
| SPEC-021 | ❌ reporté Étape 5 | **Étape 4.4** | Anti-oracles centralisés derrière REST |
| SPEC-022 | ✅ Étape 13.6 livrée | (inchangé) | notification-service scope |

#### Cat 2 — Bugs runtime (14 findings)

| Finding | Statut | Note |
|---|---|---|
| BUG-001 / BUG-002 | ✅ Étape 4.0 completion-spec | CDI @Observes(AFTER_SUCCESS) refactor |
| BUG-003 | ✅ Étape 1.1 completion-spec | Guard cancel sur EXPIRED ; sentinel test ajouté en Étape 5.2 finalisation |
| BUG-004 | (acté S9+) | events.deleted Kafka |
| BUG-005 / BUG-006 | (optionnel S8 — non livré) | Race attendance / idempotence favorite — fix dans port tests Étape 5 si tests legacy le couvrent |
| BUG-007 | (S9+) | Co-organizer DECLINED |
| BUG-008 | ❌ reporté Étape 5 | **Étape 4** Couvert par REST clients |
| BUG-009 / BUG-010 / BUG-012 / BUG-014 | ✅ Étape 1 completion-spec | — |
| BUG-011 | ✅ Étape 3.3 completion-spec + ❌ Étape 5.0 reportée | **Étape 4.0** shared-jaxrs lib + bascule attendance/event vers Timeframe shared |
| BUG-013 | (S9+) | Audit DDL cascades |

#### Cat 3 — Couverture tests (18 findings)

| Finding | Statut | Étape finalisation |
|---|---|---|
| TEST-001 | ❌ reporté Étape 7 | **Étape 5** intégrale (35 sentinels par nom) |
| TEST-002 à TEST-014 | ❌ reporté Étape 7 | **Étapes 5.1 à 5.5** par service post-consolidation |
| TEST-015 | ❌ reporté Étape 7 (mix Étape 4 + 7) | **Étape 5.2** EventLifecyclePublisherTest enrichi |
| TEST-016 | ❌ reporté Étape 7 (me-aggregator WireMock) | **ACTÉ SUPPRIMÉ** post-consolidation 14→5 (me-aggregator dissous Décision H) |
| TEST-017 | ✅ Étape 13.6 completion-spec | notification-service stub doc |
| TEST-018 | ❌ reporté Étape 7 (stratégie mock) | **Étape 5.0** Stratégie `@InjectMock @RestClient` documentée |

#### Cat 4 — Refactor / dette (18 findings)

| Finding | Statut | Étape finalisation |
|---|---|---|
| REFACTOR-001 à REFACTOR-013 (sauf S9+) | ❌ reporté Étape 5 | **Étape 4** REST clients + bascule shared libs |
| REFACTOR-014 | ✅ Étape 2.1 completion-spec | placeholders SHA |
| REFACTOR-015 | (S9+) | findByEventAndUser rename |
| REFACTOR-016 | ❌ reporté Étape 5 | **Décision H Étape 2.2.6** me-aggregator → event-service |
| REFACTOR-017 | (partiel S8) | — |
| REFACTOR-018 | ✅ Étape 3.3 completion-spec | shared-jaxrs lib |

#### Cat 5 — Kafka producers/consumers (9 findings)

| Finding | Statut | Note |
|---|---|---|
| KAFKA-001 à KAFKA-009 | ✅ Étape 4 completion-spec | Tous livrés |

#### Cat 6+7+11 — OpenAPI / Kong / Helm / CI / DevOps (18 findings)

| Finding | Statut | Étape finalisation |
|---|---|---|
| INFRA-001, INFRA-004 | (Décision Q completion-spec) | **Décision G finalization** : annulation dérogation, openapi diff = 0 |
| INFRA-002 | ✅ Étape 10 completion-spec | Kong rate-limiting |
| INFRA-003, INFRA-005, INFRA-008, INFRA-013..018 | (acté RAS) | — |
| INFRA-006 | ✅ Étape 11 completion-spec | livenessProbe (sur 5 deployments post-consolidation) |
| INFRA-007 | ✅ Étape 12.4 completion-spec | image.tag rename |
| INFRA-009 | ✅ Étape 11 completion-spec | ingress.yaml comment |
| INFRA-010 | ❌ reporté Étape 12.1 | **Étape 7** CI matrix YAML |
| INFRA-011 | ✅ Étape 12.2 completion-spec | sonar.projectKey override |
| INFRA-012 | (acté workaround) | chore(backend) titre PR |

#### Cat 8 — Documentation (24 findings)

| Finding | Statut | Étape finalisation |
|---|---|---|
| DOC-001 à DOC-024 | ✅ Étape 2 + 13 completion-spec | (ré-aligned post-consolidation) **Étapes 3.4, 8.1-8.6** |

#### Cat 9 — Sécurité (4 findings)

| Finding | Statut | Étape finalisation |
|---|---|---|
| SEC-001, SEC-003 | ❌ reporté Étape 5.8 completion | **Étape 4.4** admin bypass aligné |
| SEC-002 | ❌ reporté Étape 5.8 | **Étape 4.4** cascade SCRUM-136 centralisée |
| SEC-004 | ✅ Étape 1.3 completion-spec | OIDC fail-fast |

#### Cat 10 — Build hygiene (5 findings)

| Finding | Statut | Note |
|---|---|---|
| HYGIENE-001 à HYGIENE-005 | ✅ Étape 1 + 12.3 completion-spec | Tous livrés |

**Total findings traités** : 132/132 — finalisation adresse les 27 findings reportés (cat 1, 3, 4, 9 principalement). Aucun finding « ignoré silencieusement ».

### Annexe B — Mapping consolidation : service source → service cible

#### Tables × endpoints × topics par service source

| Service source | Service cible | Sous-étape | Tables possédées | Endpoints `@Path` racines | Topic Kafka producer | Topic consumer | Stubs JPA actuels (à transformer) |
|---|---|---|---|---|---|---|---|
| share-service | event-service | 2.2.1 | aucune (lit `events.share_code`) | `/events/{id}/share`, `/s/{shortCode}` | aucun | aucun | EventStub (devient entité réelle), UserStub (cross-service via REST) |
| view-service | event-service | 2.2.2 | event_views (devient locale) | `/events/{id}/view` | aucun | aucun | EventStub, UserStub |
| favorite-service | event-service | 2.2.3 | favorites (devient locale) | `/events/{id}/favorite`, `/users/me/favorites` | aucun | aucun | EventStub, UserStub, AttendanceStub |
| co-organizer-service | event-service | 2.2.4 | event_co_organizers (devient locale) | `/events/{id}/co-organizers/*`, `/users/me/co-organizer-invitations` | co-organizers.{invited,accepted} | aucun | EventStub, UserStub, AttendanceStub |
| stats-service | event-service | 2.2.5 | aucune (read-only) | `/events/{id}/stats` | aucun | aucun | 6 stubs (EventStub, UserStub, AttendanceStub, FavoriteStub, EventViewStub, EventCoOrganizerStub) |
| me-aggregator-service | event-service | 2.2.6 | aucune | `/users/me/events` | aucun | aucun | EventStub, UserStub, AttendanceStub |
| follow-service | user-service | 2.3.1 | follows (devient locale) | `/users/{id}/follow*`, `/follow-requests/*`, `/users/me/follow-requests` | users.{followed,follow-requested,follow-accepted} | aucun | UserStub |
| calendar-service | user-service | 2.3.2 | aucune (écrit users.calendar_token) | `/users/me/calendar-token*`, `/calendar/{token}.ics` | aucun | aucun | UserStub, EventStub (cross-svc), FavoriteStub (cross-svc), AttendanceStub (cross-svc) |
| comment-service | engagement-service | 2.4.1 | comments (devient locale) | `/events/{id}/comments`, `/comments/{id}` | comments.created | aucun | EventStub, UserStub, EventCoOrganizerStub |
| attendance-service | engagement-service | 2.1.1 (rename) | attendances (inchangé) | `/events/{id}/attend*`, `/users/me/attendances`, `/users/me/participations` | aucun | aucun | EventStub, UserStub, EventCoOrganizerStub |
| report-service | moderation-service | 2.1.2 (rename) | reports (inchangé) | `/events/{id}/report`, `/admin/reports*` | events.banned | aucun | EventStub, UserStub, EventCoOrganizerStub |
| event-service | event-service | (inchangé) | events, event_tags | `/events*`, `/admin/events/*`, `/events/search`, `/events/featured`, `/events/{id}/image` | events.{published,cancelled,expired} | events.banned | aucun (déjà entité réelle) |
| user-service | user-service | (inchangé) | users, user_interests | `/users/me`, `/users/{id}`, `/users/me/image`, `/users/me/banner` | aucun | aucun | aucun |
| notification-service | notification-service | (inchangé, replicas:0) | aucune | aucun | aucun | aucun | aucun |

### Annexe C — Liste des 8 REST clients post-consolidation

(Reproduite du § Architecture cible post-finalization Tableau des 8 REST clients pour référence directe.)

| # | Consumer | Provider | Endpoints | Resilience |
|---|---|---|---|---|
| 1 | event-service | user-service | `GET /users/{id}` | retry+timeout+CB+fallback `null` |
| 2 | event-service | engagement-service | `GET /events/{eventId}/attendance-summary` | retry+timeout+CB |
| 3 | user-service | event-service | `GET /events?ids=...&status=PUBLISHED` (bulk) | retry+timeout+CB |
| 4 | user-service | engagement-service | `GET /users/{id}/attendances?status=ATTENDING` | retry+timeout+CB |
| 5 | engagement-service | event-service | `GET /events/{id}?check-co-org-of={uuid}` | retry+timeout+CB |
| 6 | engagement-service | user-service | `GET /users/{id}` | retry+timeout+CB |
| 7 | moderation-service | event-service | `GET /events/{id}` | retry+timeout+CB |
| 8 | moderation-service | user-service | `GET /users/{id}` | retry+timeout+CB |

**Endpoints internes ajoutés au catalogue** (cf. [`backend/docs/internal-endpoints.md`](../backend/docs/internal-endpoints.md)) :

| Path | Service propriétaire | Payload | Note |
|---|---|---|---|
| `GET /events/{eventId}/attendance-summary` | engagement-service | `AttendanceSummary` | nouveau |
| `GET /events?ids=...&status=PUBLISHED` | event-service | `List<EventDTO>` | bulk, nouveau |
| `GET /events/{id}?check-co-org-of={uuid}` | event-service | `EventDTO` + `coOrganizerOf: bool` | param nouveau |
| `GET /users/{id}/attendances?status=ATTENDING` | engagement-service | `List<AttendanceDTO>` | existant public, réutilisé interne |

### Annexe D — Fichiers Kong + Helm + workflows à modifier

| Fichier | Action | Étape |
|---|---|---|
| `k8s/chart/templates/kong/configmap-routes.yaml` | Fusion blocs `<X>-service` dans bloc `<Y>-service` cible (move des routes enfants, change `service:` upstream) | 2.1.1, 2.1.2, 2.2.1-2.2.6, 2.3.1, 2.3.2, 2.4.1 |
| `k8s/chart/templates/<X>-service/` (×8 services à supprimer) | Suppression complète du dossier (`git rm -r`) | Pendant chaque merge 2.X |
| `k8s/chart/templates/<Y>-service/` (×4 services renommés event/user/engagement/moderation + 1 placeholder notification) | Renommage du dossier + `service:` + `selector` + `app:` labels | 2.1.1, 2.1.2 |
| `k8s/chart/values.yaml` | Vérification post-2.5 (pas de clé `image.<svc>.*` orpheline) | 2.5.1 |
| `backend/pom.xml` | Suppression module `<X>-service` après chaque merge | 2.X (à chaque merge) |
| `.github/workflows/build.yml` | Refonte matrix per-service (5 backend + 10 libs + 1 contract-and-e2e) | 7.1 |
| `.github/workflows/deploy.yml` | Inchangé (déjà `--set image.tag=`) | (no-op) |

### Annexe E — Commandes de référence

#### Build local

```bash
cd /workspace/backend
./mvnw verify -DskipITs                      # 17 modules après finalization, ~3-4 min
./mvnw -pl services/<svc>-service -am verify  # un seul service + ses deps
./mvnw -pl services/shared-rate-limit -am test  # un seul shared lib
./mvnw -pl contract-tests -am verify          # contract tests
./mvnw -pl e2e -am verify                     # E2E
```

#### Couverture jacoco agrégée

```bash
# Script bash agrégateur (cf. Étape 5.7 de la spec)
for r in services/*/target/jacoco-report/jacoco.xml; do
    module=$(echo "$r" | sed 's|services/\([^/]*\)/target.*|\1|')
    parsed=$(tr '>' '\n' < "$r" | grep -E 'counter type="(LINE|BRANCH)"')
    line_last=$(echo "$parsed" | grep 'type="LINE"' | tail -1)
    branch_last=$(echo "$parsed" | grep 'type="BRANCH"' | tail -1)
    lm=$(echo "$line_last" | sed -E 's/.*missed="([0-9]+)".*/\1/'); lc=$(echo "$line_last" | sed -E 's/.*covered="([0-9]+)".*/\1/')
    bm=$(echo "$branch_last" | sed -E 's/.*missed="([0-9]+)".*/\1/'); bc=$(echo "$branch_last" | sed -E 's/.*covered="([0-9]+)".*/\1/')
    [ -z "$lm" ] && lm=0; [ -z "$lc" ] && lc=0
    lt=$((lm + lc)); bt=$((bm + bc))
    lp=$(awk -v c=$lc -v t=$lt 'BEGIN{if(t>0)printf "%.1f",c*100/t; else print "0.0"}')
    bp=$(awk -v c=$bc -v t=$bt 'BEGIN{if(t>0)printf "%.1f",c*100/t; else print "0.0"}')
    printf "%-25s %6s%% L  %6s%% B\n" "$module" "$lp" "$bp"
done
```

#### Git workflow

```bash
git status                                                         # check
git add <ciblé>                                                    # NEVER `git add -A` (sauf consolidation merge)
git commit -m "<conv>(<scope>): <subject>"
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch                                           # ~10-15 min watching
gh run view <RUN_ID> --log-failed                                  # debug si fail
```

#### Move des sources Java pour consolidation

```bash
# Pattern pour merge X→Y (ex. share→event)
git mv backend/services/<X>-service/src/main/java/ch/unige/events/<X>/* \
       backend/services/<Y>-service/src/main/java/ch/unige/events/<Y>/<X>/
find backend/services/<Y>-service/src/main/java/ch/unige/events/<Y>/<X>/ -name '*.java' -exec \
    sed -i 's|^package ch\.unige\.events\.<X>\.|package ch.unige.events.<Y>.<X>.|g; \
            s|^import ch\.unige\.events\.<X>\.|import ch.unige.events.<Y>.<X>.|g' {} +
```

#### Récupération de fichier legacy (Étape 5)

```bash
git show 41074e9:backend/services/legacy-monolith/src/<path>.java > backend/services/<svc>/src/<path>.java
# Puis adapter package + imports + REST client mocks
```

#### Vérification sentinels par nom

```bash
grep -rn "void <sentinel-name>" backend/services/*/src/test
```

#### Vérification 0 stub JPA

```bash
find backend/services -name '*Stub.java' -not -path '*/target/*'
```

#### Vérification invariants frontend + openapi

```bash
git diff --shortstat origin/main HEAD -- frontend/   # = 0 lignes ABSOLU
git diff --shortstat origin/main HEAD -- openapi/    # = 0 lignes ABSOLU
```

#### Vérification topology

```bash
ls backend/services/ | grep -E '\-service$' | sort   # 5 services attendus
ls backend/services/ | grep -E '^shared-' | sort     # 10 libs attendues
grep -c '<module>' backend/pom.xml                    # 17 attendu
```

#### Mise à jour PR body

```bash
gh pr view 158 --json body --jq .body > /tmp/pr-body.md
# édit /tmp/pr-body.md
gh pr edit 158 --body-file /tmp/pr-body.md
```

#### Watch CI passive

```bash
gh pr checks 158 --watch
# OU via API plus contrôle :
gh pr checks 158 --json name,bucket | jq '.[] | select(.bucket=="fail") | "\(.name): \(.bucket)"'
```

---

## Note de fin

Cette spec est l'unique source de vérité pour la finalisation de la migration microservices PR #158. Elle fait suite directe à :
- la spec originale (architecture cible, brief Agon),
- l'audit post-PR-158 (132 findings),
- la completion spec (exécutée à ~70 %, étapes 5/7/8/12.1 reportées).

Toute dérive doit être actée dans le commit message + dans `sprint-context.md` Étape 20 avec justification. La spec assume un exécuteur autonome **bypass-permissions** capable de :
- lire/écrire/supprimer dans le repo,
- lancer `cd backend && ./mvnw verify`,
- lancer `gh` (pr checks watch, pr edit body),
- commit + push sur la branche persistante,
- attendre la CI et retraiter en cas d'échec.

Si une situation imprévue émerge (ex. test legacy impossible à porter parce que le code source a divergé trop ; merge X→Y qui révèle une dépendance circulaire imprévue), l'exécuteur :
1. acte la déviation dans le commit message + `sprint-context.md`,
2. continue l'étape (n'aborte pas la finalisation entière),
3. à la fin, liste tous les écarts dans le PR body final.

**FIN DE SPEC FINALIZATION.**
