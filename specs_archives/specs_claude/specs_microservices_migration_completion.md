# Migration vers microservices — backend UNIGE Events — COMPLÉTION (PR #158)

| Champ | Valeur |
|---|---|
| Ticket Jira | Suite migration monolithe → microservices, cours pinfo6, brief Agon |
| Sprint | S8 (complétion) |
| Branche | `refactor(backend)--migrate-to-microservices` (persistante, **NE PAS créer de nouvelle branche**) |
| Base de l'exécution | HEAD `bee933d32e883cdfc5ac2d38ad05d444b3b126a7` à minima ; tip réel au moment du lancement = `git rev-parse HEAD` |
| PR active | #158 (`https://github.com/unige-pinfo6-2026/unige-events/pull/158`) — **NE PAS merger**, l'humain Elie Bussod merge lui-même |
| Spec antérieure | [`specs_archives/specs_claude/specs_microservices_migration.md`](../specs_archives/specs_claude/specs_microservices_migration.md) (1884 lignes, 30+ décisions originales) |
| Audit antérieur | [`specs_archives/audit_pr158_microservices_migration.md`](../specs_archives/audit_pr158_microservices_migration.md) (2143 lignes, 132 findings) |
| Auteur de cette spec | Claude (session de génération autonome 2026-05-09) |
| Exécuteur cible | Claude Code en **bypass-permissions**, pas d'humain entre les étapes |
| Frontend lié | **AUCUN** — `git diff --stat origin/main HEAD -- frontend/` doit rester vide |
| OpenAPI | `git diff --stat origin/main HEAD -- openapi/` reste vide SAUF la suppression du doublon `POST /events/{id}/view` (cf. INFRA-001 / Décision Q) — déviation explicitement actée ici |
| Frontière DevOps | 7 items hors-scope explicites (création SonarCloud projects ; cluster Kafka prod-grade ; schemas-per-service via Flyway physique séparé ; NetworkPolicies K8s ; Cloudflare tunnel ; Doppler secrets prod ; Kong DB-mode) |

---

## Note d'implémentation

Cette spec est l'unique source de vérité pour la **complétion** de la PR #158.
Elle fait suite à la spec originale et à l'audit cités dans le header. Elle
traite chaque finding de l'audit (ID `SPEC-NNN`, `BUG-NNN`, `TEST-NNN`,
`REFACTOR-NNN`, `KAFKA-NNN`, `INFRA-NNN`, `DOC-NNN`, `SEC-NNN`,
`HYGIENE-NNN`) soit en le fixant, soit en l'actant formellement comme
DevOps handoff ou non-régression intentionnelle.

Elle est destinée à un **exécuteur autonome** (Claude Code en mode
bypass-permissions). L'exécuteur :
- ne doit **jamais** demander une décision au user (toutes sont tranchées ici) ;
- commit + push après chaque sous-étape numérotée verte (granularité ≈ 1
  commit par sous-étape `N.M`) ;
- pousse sur la branche persistante `refactor(backend)--migrate-to-microservices` ;
- ne merge jamais la PR #158 ;
- ne crée jamais de nouvelle branche, jamais de nouveau ticket Jira, jamais
  de nouvelle PR ;
- met à jour `backend/docs/sprint-context.md` au fur et à mesure (Étape 19
  vivante) ;
- met à jour le **PR body** de #158 quand toute la spec a été livrée ;
- valide chaque étape via `cd backend && ./mvnw verify -DskipITs` (3-4 min) ET
  `gh pr checks 158 --watch` (~10-15 min) ;
- en cas d'échec, **fixe la cause racine** — jamais de `--no-verify`, jamais
  de `@Disabled`, jamais de skip silencieux.

Toute déviation par rapport aux décisions ci-dessous (A à Y) doit être
**actée explicitement** dans le commit message + dans `sprint-context.md`,
avec justification concrète. Les déviations triviales (ex. nom de classe
légèrement différent) ne nécessitent pas d'acte.

> **Leçon Flyway-immutabilité (rappel — cf. spec orig.).** La règle
> « migration committée = immutable » s'applique par-base. Les migrations
> historiques V1..V17 sont **gravées** dans la `flyway_schema_history` des
> bases preview/prod ; tout nouveau changement va dans un nouveau fichier
> `V<N>__...sql`. Cette règle s'applique aussi à toute migration ajoutée
> dans le cadre de la complétion (Étape 6 si livrée).

---

## Contexte

### Le besoin produit (rappel — cours pinfo6, brief Agon)

> *« Le backend de ce projet (Quarkus 3 / Java 21 / PostgreSQL / Kubernetes
> + Helm) est actuellement un monolithe. Dans le cadre du cours, nous
> devons migrer vers une architecture microservices. »* — brief Agon

La PR #158 a livré la *charpente* de cette migration. Le présent travail
consiste à **livrer le cœur** (REST clients, observabilité, tests, Kafka
producteurs/consommateurs manquants) afin que la migration soit
*réellement* microservices et non un monolithe distribué — avant que
le DevOps prenne le relais pour son périmètre infra.

### État livré par PR #158 à HEAD `bee933d`

**Charpente structurelle livrée** :
- 14 modules Maven (`backend/services/*`) : 13 microservices Quarkus extraits
  (`share`, `view`, `favorite`, `calendar`, `follow`, `comment`, `co-organizer`,
  `attendance`, `report`, `stats`, `me-aggregator`, `user`, `event`) + 1
  placeholder vide (`notification-service`, replicas:0, hors scope SCRUM-99) +
  2 shared libs Sprint 8 (`shared-rate-limit` 100 % couvert, `shared-storage`
  100 % couvert).
- `services/legacy-monolith/` supprimé (commit `b570c1b`, ~370 fichiers, ~27K
  lignes).
- Kong DB-less en gateway, Helm umbrella avec sous-templates par service.
- Kafka KRaft single-broker + 10 topics provisionnés via Job Helm
  (`events.{published,cancelled,banned,expired}`,
  `users.{followed,follow-requested,follow-accepted}`, `comments.created`,
  `co-organizers.{invited,accepted}`).
- 1 producteur Kafka pilote livré : event-service émet
  `events.{published,cancelled,expired}` via `EventLifecyclePublisher`
  (commit `5dce9be`).
- 13 annotations `@PerUserRateLimit` restaurées via `shared-rate-limit`
  (commit `446ea3e`) — 6 services consommateurs.
- `FileStorageService` consolidé dans `shared-storage` (commit `3f3dcd1`).
- CI verte : Build BE/FE, Sonar BE/FE Quality Gate **passed**, Deploy
  Preview vert. PR #158 mergeable, en attente review humain.

### Ce qui n'est PAS livré (cf. audit 132 findings)

Synthèse par catégorie d'audit (références exhaustives dans
[`audit_pr158_microservices_migration.md`](../audit_pr158_microservices_migration.md)) :

| # | Catégorie | Findings | Constat |
|---|---|---|---|
| 1 | Conformité spec orig. | 22 (4 critical, 9 high) | 11 décisions sur 30+ non livrées : DB-per-service (8/9), REST clients (5/11/12/13), Kafka complet (14/26), Pact+E2E (18), CI matrix par service (17/25), observabilité logs JSON+Prometheus (19), 3 deps Maven obligatoires (30) |
| 2 | Bugs runtime | 14 (1 critical, 5 high) | Kafka publish in-transaction (BUG-001/002), guard cancel sur EXPIRED manquant (BUG-003), race attendance (BUG-005), idempotence favorite (BUG-006), TZ drift (BUG-014) |
| 3 | Couverture tests | 18 (1 BLOCKER, 6 CRITICAL) | **0/35 sentinels SCRUM-138/139/144/147** présents. 1818 `@Test` legacy supprimés, ~10 portés. Couverture business effective 3.3 %–40 % lignes / **0 % branches** sur les 13 microservices. Sonar gate passe **artificiellement** via `<sonar.coverage.exclusions>services/*-service/**/*</sonar.coverage.exclusions>` |
| 4 | Refactor / dette | 18 (4 high) | 35 JPA stubs cross-schéma (REFACTOR-001 / BUG-008 — c'est un monolithe distribué), 7 copies d'`ApiErrorResponse`, 8 enums dupliqués 5–8× chacun, 6 copies d'`EventDTO`, cascade `isCreatorOrAcceptedCoOrganizer` × 5 |
| 5 | Kafka complétude | 9 (1 BLOCKER, 4 HIGH) | 9 producteurs sur 10 manquants ; 1 consumer sur 1 attendu (event-service ← `events.banned`) absent ; `report-service` mute encore via JPA cross-schema |
| 6 | OpenAPI / Kong / Helm | 13 (cat 6) (2 high, 9 medium, 7 low) | Plugin Kong `rate-limiting` absent (INFRA-002), `livenessProbe` absente sur 13 deployments (INFRA-006), doublon openapi `/events/{id}/view` (INFRA-001), `image.api.tag` legacy partout (INFRA-007) |
| 7 | CI / Sonar | 5 (cat 7) | Build single-job (INFRA-010), un seul `sonar.projectKey` (INFRA-011), exclusion glob présente (HYGIENE-005) |
| 8 | Documentation | 24 (3 critical) | `backend/AGENTS.md` totalement obsolète (DOC-020/021), 11 placeholders `<this PR>` non substitués (DOC-013), tableau « Écarts vs spec » figé sur Étape 1 (DOC-014/015), PR body avec 3 claims faux (DOC-022) |
| 9 | Sécurité | 4 (low) | Inconsistencies admin bypass (SEC-001/003), defaults OIDC bidons (SEC-004) |
| 10 | Build hygiene | 5 (medium/low) | Warning `quarkus.flyway.enabled` runtime sur 13 services (HYGIENE-001), TODO obsolète (HYGIENE-004) |
| 11 | DevOps handoff | 7 informational | Validés comme hors scope, listés en section dédiée |

### Pourquoi compléter maintenant

- **Handoff DevOps imminent.** L'équipe DevOps reprend la PR pour le périmètre
  k8s/cluster prod après cette spec. Si la migration est livrée à 60-70 %,
  DevOps doit jouer le rôle d'archéologue + extension. Si elle est livrée
  à 95 %+, DevOps fait son périmètre propre.
- **Soutenance pinfo6 imminente.** Une démo qui montre 14 services + Kong +
  Kafka MAIS où chaque service lit la table de l'autre via JPA = la note de
  l'évaluateur baisse. Une démo où la cascade `cancel/restore` traverse la
  pile via REST + Kafka events = pédagogie du brief Agon respectée.
- **Le « 80 % livré » est piégeux.** L'audit le formalise : sans REST
  clients, **toute** modification d'un schéma cross-service casse 4-5 modules
  silencieusement (1st-level Hibernate cache stale). Sans tests, **toute**
  modification de la cascade SCRUM-136 ou des anti-oracles ISSUE-92/93 fait
  régresser silencieusement la sécurité. Sans observabilité (logs JSON +
  X-Request-ID + Prometheus), DevOps ne peut pas diagnostiquer un incident
  cross-service. Ces dettes se déclencheront « plus tard » ; on les paie
  maintenant.
- **L'effort est borné.** L'audit chiffre ~2-3 sprints ingénieur backend
  pour passer de 132 findings à 0 findings actionnables. C'est compatible
  avec le calendrier S8 → S9.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

> **Pour l'exécuteur** : chaque décision ci-dessous est définitive. Aucune
> ne doit être tranchée au moment de l'implémentation. Si une situation
> imprévue émerge, applique la règle « principe de moindre surprise vs cette
> décision » et **acte la déviation** dans le commit message + sprint-context.

### Décision A — Pattern de publish Kafka post-commit : **CDI `@Observes(during=AFTER_SUCCESS)`**

**Décision.** Toutes les émissions Kafka qui dépendent d'un commit DB (publish/cancel/expire d'event, ban d'event, follow created, comment posted, co-organizer invited, etc.) passent par un **CDI event** fired depuis la méthode `@Transactional`, et sont émis vers Kafka par un **listener observant en `TransactionPhase.AFTER_SUCCESS`**.

Pattern de référence (à reproduire pour chaque service producteur) :

```java
// Dans le service métier (transactionnel)
@Inject jakarta.enterprise.event.Event<EventLifecycleEvent> cdiEvent;

@Transactional
public EventDTO publish(Long id, ...) {
    // ... mutations DB ...
    cdiEvent.fire(EventLifecycleEvent.published(event.id, creatorId));
    return EventDTO.from(event, ...);
}

// Dans une classe @ApplicationScoped séparée du service métier
@ApplicationScoped
public class EventLifecycleKafkaBridge {
    @Inject EventLifecyclePublisher publisher;

    void onAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) EventLifecycleEvent ev) {
        publisher.send(ev);
    }
}
```

**Justification.** Les topics actuels (`events.published`, `comments.created`, etc.) sont des **notifications** consommées par un futur `notification-service` (SCRUM-99) et par d'autres services downstream (cache, projections lectures). La cohérence de ces consommateurs n'est **pas critique business** : un événement perdu = une notification perdue, pas une transaction monétaire. Le pattern `@Observes(AFTER_SUCCESS)` :
- **0 modification de schéma** (pas de table `*_outbox` à provisionner par service) ;
- **natif CDI**, pas d'API JTA bas niveau à manipuler ;
- fenêtre de fuite ≈ microsecondes (entre le `commit` JDBC et le firing du listener CDI) — acceptable au regard du SLA notification ;
- couplage faible : le listener CDI est dans une classe distincte du service, on peut désactiver Kafka en `%test` sans toucher le service métier.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **CDI `@Observes(during=AFTER_SUCCESS)`** | Simplicité, natif Quarkus/CDI, 0 schéma, fenêtre microsecondes | Fenêtre de fuite non-zéro entre commit et firing (théorique) | ✅ retenu |
| (b) Outbox pattern (`event_outbox` + scheduler de polling + idempotence par message UUID + DELETE après publish OK) | Cohérence at-least-once forte, audit trail, rejouable | XL effort par service (~1j chacun, 5 services), nouvelle table par service, polling overhead, complexité tests intégration, durcit la migration DB-per-service (Décision C) | ❌ over-engineering pour un projet pédagogique sur topics non-critiques |
| (c) Synchronisation JTA brute (`TransactionSynchronizationRegistry.registerInterposedSynchronization(...)`) | Standard JTA | API moins lisible que CDI events, idem fenêtre que (a) | ❌ moins idiomatique Quarkus |
| (d) `Emitter.send()` direct in-transaction (statu quo PR #158) | 0 modif | **BUG-001/002 — fuite events sur rollback** | ❌ régression actée |

**Application.** Sites concernés (à transformer) :
- `event-service/EventService.publish()` ligne ~419 (actuel : `lifecyclePublisher.published(...)` direct → CDI fire).
- `event-service/EventService.cancel()` ligne ~363 (idem).
- `event-service/EventExpirationService.expireEvents()` ligne ~48 (idem, dans la boucle).
- + tous les producteurs futurs livrés en Étape 4 (cf. Décision F).

L'`EventLifecyclePublisher` existant (livré par `5dce9be`) est conservé tel quel — c'est lui qui appelle `Emitter.send()`. Seul le déclenchement migre vers CDI. Une nouvelle classe `EventLifecycleKafkaBridge` (l'observer) est créée par service producteur.

**Adresse** : BUG-001, BUG-002 (cat 2 critical/high)

### Décision B — Stratégie cross-service par stub : **REST sync uniforme + 1 exception Kafka pour la commande de ban**

**Décision.** Tous les 35 JPA stubs identifiés dans l'audit annexe C sont remplacés par des **REST clients synchrones** (`@RegisterRestClient`). **Une seule exception** : la *commande* « ban an event » de report-service vers event-service passe par Kafka topic `events.banned` (déjà provisionné), pas par un PATCH REST — pour respecter l'asymétrie producteur/consommateur de la spec orig. décision 11/14.

Pour chaque stub, le pattern est uniforme :
1. Définir un endpoint REST côté service propriétaire (existant ou nouveau).
2. Définir l'interface `@RegisterRestClient(configKey = "<svc>-service")` côté service consommateur.
3. Configurer l'URL du client dans `application.properties` (`quarkus.rest-client.<svc>-service.url=http://<svc>-service:8080`).
4. Ajouter resilience : `@Retry(maxRetries=3, delay=200)`, `@Timeout(2000)`, `@CircuitBreaker(failureRatio=0.5, requestVolumeThreshold=10)`, `@Fallback(fallbackMethod="...")`.
5. Remplacer les call-sites du stub par des appels au REST client.
6. Supprimer la classe `*Stub.java`.

**Justification.**

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **REST sync uniforme** + Kafka pour les commandes business (1 cas) | KISS ; immédiat et débogable ; pas de cache local à maintenir ; cohérence forte (lecture toujours fraîche) ; align spec orig. décision 11 « REST sync pour les requêtes utilisateur » | Latence cumulée sur les paths multi-domaines (ex. `GET /events/{id}` qui résout creatorId via user-service) ; couplage temporel (panne user-service → 503 sur event-service) | ✅ retenu |
| (b) Kafka projection (chaque service maintient une table cache locale `users_projection`, `events_projection` ; alimentée par events `users.profile.changed` etc.) | Latence locale ; tolérance panne consumer ; lecture sans réseau | XL effort : nouveaux topics (8+), backfill cold-start, idempotence consumer, eventual consistency à expliquer ; transformation profonde du modèle | ❌ over-engineering pour S8 ; pertinent en S9+ pour les hot reads |
| (c) Mix REST/Kafka case-by-case selon fréquence | Optimal théorique | 35 micro-décisions à justifier ; risque de drift d'approche entre services | ❌ trop fragmenté |
| (d) Garder JPA stubs (statu quo PR #158) | 0 effort | **Audit BUG-008 / REFACTOR-001 — monolithe distribué** | ❌ régression actée |

**Annexe consolidée — 35 stubs à remplacer.**

| Stub source | Service consommateur | Service propriétaire | Endpoint REST cible | Resilience |
|---|---|---|---|---|
| `UserStub` | event-service | user-service | `GET /users/{id}` | retry+timeout+CB+fallback `Optional.empty()` |
| `UserStub` | attendance-service | user-service | `GET /users/{id}` | idem |
| `UserStub` | comment-service | user-service | `GET /users/{id}` | idem |
| `UserStub` | co-organizer-service | user-service | `GET /users/{id}` | idem |
| `UserStub` | report-service | user-service | `GET /users/{id}` | idem |
| `UserStub` | favorite-service | user-service | `GET /users/{id}` | idem |
| `UserStub` | calendar-service | user-service | `GET /users/by-calendar-token/{token}` (nouveau, **interne**, **non exposé Kong**) | retry+timeout (sans fallback car requis pour servir le ICS) |
| `UserStub` | view-service | user-service | `GET /users/by-auth0/{auth0Id}` (nouveau, **interne**) | retry+timeout+CB |
| `UserStub` | follow-service | user-service | `GET /users/{id}` | idem |
| `UserStub` | me-aggregator-service | user-service | `GET /users/by-auth0/{auth0Id}` | idem |
| `UserStub` | stats-service | user-service | `GET /users/{id}` | idem |
| `EventStub` | attendance-service | event-service | `GET /events/{id}/capacity-summary` (nouveau, **interne**, retourne capacity + currentAttending + waitlistedCount) | retry+timeout+CB |
| `EventStub` | comment-service | event-service | `GET /events/{id}` | idem |
| `EventStub` | co-organizer-service | event-service | `GET /events/{id}` | idem |
| `EventStub` | favorite-service | event-service | `GET /events/{id}` (existence) + `GET /events?ids=` (bulk pour `/me/favorites`) (nouveau, **interne**) | idem |
| `EventStub` | view-service | event-service | `GET /events/{id}` (existence) | idem |
| `EventStub` | report-service | event-service | `GET /events/{id}` ; **commande de ban via Kafka events.banned** (cf. Décision F) | idem |
| `EventStub` | calendar-service | event-service | `GET /events?ids=&status=PUBLISHED` (nouveau, **interne**, bulk) | idem |
| `EventStub` | me-aggregator-service | event-service | `GET /events?creatorId={id}` (déjà exposé via `?organizerId`) | idem |
| `EventStub` | stats-service | event-service | `GET /events/{id}` | idem |
| `AttendanceStub` | event-service | attendance-service | `GET /events/{eventId}/attendance-summary` (nouveau, **interne**, count by status) | idem |
| `AttendanceStub` | co-organizer-service | attendance-service | `GET /events/{eventId}/attendance-summary` | idem |
| `AttendanceStub` | favorite-service | attendance-service | `GET /events/attendance-summary?ids=` (bulk pour `/me/favorites`) (nouveau, **interne**) | idem |
| `AttendanceStub` | calendar-service | attendance-service | `GET /users/{id}/attendances?status=ATTENDING` (déjà exposé public — réutilisé en interne) | idem |
| `AttendanceStub` | me-aggregator-service | attendance-service | idem | idem |
| `AttendanceStub` | stats-service | attendance-service | `GET /events/{eventId}/attendance-summary` | idem |
| `FavoriteStub` | event-service | favorite-service | `GET /events/{id}/favorite-count` (nouveau, **interne**) | idem |
| `FavoriteStub` | calendar-service | favorite-service | `GET /users/{id}/favorites` (déjà exposé public) | idem |
| `FavoriteStub` | stats-service | favorite-service | `GET /events/{id}/favorite-count` | idem |
| `EventViewStub` | event-service | view-service | `GET /events/{id}/view-count` (nouveau, **interne**) | idem |
| `EventViewStub` | stats-service | view-service | `GET /events/{id}/view-count` | idem |
| `EventCoOrganizerStub` | event-service | co-organizer-service | `GET /events/{eventId}/co-organizers/check?userId=` (nouveau, **interne**, retourne booléen) | idem |
| `EventCoOrganizerStub` | comment-service | co-organizer-service | `GET /events/{eventId}/co-organizers/check?userId=` | idem |
| `EventCoOrganizerStub` | attendance-service | co-organizer-service | `GET /events/{eventId}/co-organizers/check?userId=` | idem |
| `EventCoOrganizerStub` | report-service | co-organizer-service | `GET /events/{eventId}/co-organizers/check?userId=` | idem |
| `EventCoOrganizerStub` | stats-service | co-organizer-service | `GET /events/{eventId}/co-organizers/check?userId=` + `GET /events/{eventId}/co-organizers/accepted-user-ids` | idem |
| `FollowStub` | user-service | follow-service | `GET /users/{id}/follow-counts` (nouveau, **interne**, retourne `{followers, following, followStatus}`) | retry+timeout+CB+fallback `(0, 0, null)` |

**Endpoints internes** (non exposés via Kong, communication service-to-service uniquement) **NE SONT PAS** ajoutés à `openapi/openapi.yaml` (dérogation explicite à la règle openapi-first — ils ne font pas partie du contrat public). Ils sont documentés dans `backend/docs/internal-endpoints.md` (à créer en Étape 5).

**Adresse** : SPEC-002, SPEC-005, SPEC-011, SPEC-013, SPEC-021, BUG-008, REFACTOR-001, REFACTOR-006, REFACTOR-009, REFACTOR-010, REFACTOR-013, REFACTOR-016, INFRA-005 (cat 1, 2, 4)

### Décision C — DB-per-service : **DIFFÉRÉ S9+ formellement**

**Décision.** Les schémas par service (décision 8 de la spec orig.) ne sont **pas** livrés dans la complétion S8. Le schéma physique partagé `unige_events` est **conservé** ; les services y accèdent via le même rôle DB (statu quo). La migration vers schémas par service + RBAC strict est explicitement défférée à S9+ et inscrite dans le futur backlog DevOps.

**Justification.** Le bénéfice fonctionnel de DB-per-service en S8 est **nul** dès lors que la Décision B (REST clients) supprime tous les accès JPA cross-service. Aucun service ne lit physiquement la table d'un autre une fois les stubs disparus. La défense en profondeur (« même si un dev oublie et utilise un stub, la DB rejette le write ») coûte XL en effort vs un bénéfice essentiellement disciplinaire. Pour un projet pédagogique sur table preview où la DB est éphémère, le rapport coût/bénéfice n'est pas tenable.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Différer S9+** | Effort 0 ; concentre l'effort S8 sur Décision B (REST clients) qui matérialise déjà l'isolation au niveau code | Spec orig. décision 8 non honorée formellement (à acter) | ✅ retenu |
| (b) Livrer en S8 — schémas logiques séparés (`<svc>_svc.<table>`) sur DB physique partagée | Aligne formellement la spec orig. ; défense en profondeur RBAC | XL effort : créer schémas + ALTER TABLE SET SCHEMA + GRANT par rôle + Flyway baseline injection + bumper `currentSchema=` partout + casser TOUS les tests temporairement | ❌ trop coûteux pour un bénéfice marginal |
| (c) Livrer en S8 — DBs physiques séparées | Vraie isolation | XXL effort + pas demandé par la spec orig. (qui acte instance partagée) | ❌ hors scope spec orig. |

**Application.** Aucune. La spec acte le report. La décision est documentée dans `backend/docs/devops-handoff.md` (à créer en Étape 13.6) et dans `sprint-context.md` Étape 19. Le finding SPEC-001 reste ouvert mais formellement *différé* dans le tableau « Écarts vs spec » (statut `DEFERRED-S9+`, pas `non-livré`).

**Adresse** : SPEC-001, SPEC-009, SPEC-011 (cat 1 critical/high — actées comme DEFERRED-S9+, pas fixées)

### Décision D — Libs partagées à créer : **8 nouvelles libs sous `backend/services/shared-*`**

**Décision.** Création de **8 nouveaux modules Maven** sous `backend/services/`, **tous packagés `jar`** (pas Quarkus), **tous indexés Jandex** (`io.smallrye:jandex-maven-plugin` 3.2.2), **tous nommés sans suffixe `-service`** (donc hors glob `<sonar.coverage.exclusions>services/*-service/**/*</sonar.coverage.exclusions>` du parent POM — leur couverture compte sur le new-code metric Sonar).

| # | Lib | Contenu | Consommateurs (avant complétion : 0 ; après : N) | Findings adressés |
|---|---|---|---|---|
| 1 | `shared-api-error` | `ApiErrorResponse` (record `{error, message}`) + `ApiErrors` (factory helpers `badRequest/conflict/unprocessable/forbidden/notFound`) + `WebApplicationExceptionMapper` générique | 13 services métiers (tous) | REFACTOR-002, REFACTOR-011 |
| 2 | `shared-domain-enums` | 8 enums : `EventStatus`, `AttendanceStatus`, `EventCategory`, `Faculty`, `CoOrganizerStatus`, `FollowStatus`, `RecurrenceFrequency`, `ReportStatus`, `ReportReason` (9 mais `ReportReason` toujours avec `ReportStatus`, on en met 9) | 8-12 services chacun | REFACTOR-003 |
| 3 | `shared-domain-dtos` | DTOs cross-projetés : `UserPublicResponse`, `EventDTO`, `AttendanceDTO`, `EventCoOrganizerDTO`. **NB** : `UserProfileResponse` (privé) reste dans user-service ; `CommentDTO` reste dans comment-service ; etc. — uniquement les DTOs réellement consommés cross-service | 10+ consommateurs cumulés | REFACTOR-006 |
| 4 | `shared-domain-projections` | `computeAvailableSpots(Integer, long)`, `resolveUserId(...)`, helpers de calcul réutilisables | 6 services | REFACTOR-005, REFACTOR-013 |
| 5 | `shared-jaxrs` | ParamConverters d'enums (`AttendanceStatusParamConverter`, `EventStatusParamConverter`, etc.), `TimeframeParamConverter`, classes utilitaires JsonWebToken `Instance<>` lazy (pour `%test.oidc.enabled=false`) | 13 services | BUG-011, REFACTOR-018 |
| 6 | `shared-tracing` | `RequestIdFilter` (`@Provider` `ContainerRequestFilter` qui lit `X-Request-ID` et le pose en MDC) + `RequestIdClientFilter` (filter REST client qui propage `X-Request-ID` aux REST clients sortants) + `MDCInterceptor` Mutiny pour Kafka producers | 13 services | SPEC-013, SPEC-019 (partiel) |
| 7 | `shared-kafka-events` | Records Kafka payload : `EventLifecycleEvent`, `EventBannedEvent`, `FollowLifecycleEvent`, `CommentCreatedEvent`, `CoOrganizerEvent`. **Migré** depuis event-service (déjà existant) | producteurs + consommateurs (event-service producer + event-service consumer events.banned + futurs notification-service consumers) | KAFKA-007 |
| 8 | `shared-platform` | `ServiceIdentityResource` paramétrisable (chaque service injecte son nom via `@ConfigProperty("quarkus.application.name")`) + extension health-check commune | 13 services + notification-service | REFACTOR-012 |

**Lib NON créée — `shared-authz`.** L'authorization (cascade SCRUM-136 `isCreatorOrAcceptedCoOrganizer`) est centralisée derrière le **REST client co-organizer-service** (cf. Décision B endpoint `GET /events/{eventId}/co-organizers/check?userId=`) plutôt que dupliquée dans une lib. Les 5 inlining actuels (cat 9 SEC-002) sont retirés au passage à REST clients (Étape 5). Une lib séparée serait redondante.

**Lib `shared-rate-limit` (existante)** : laissée intacte. Aucune modification.
**Lib `shared-storage` (existante)** : laissée intacte. Aucune modification.

**Naming et placement.**

```
backend/services/
├── shared-api-error/         <─ jar, jandex
├── shared-domain-dtos/       <─ jar, jandex
├── shared-domain-enums/      <─ jar, jandex
├── shared-domain-projections/<─ jar, jandex
├── shared-jaxrs/             <─ jar, jandex
├── shared-kafka-events/      <─ jar, jandex
├── shared-platform/          <─ jar, jandex
├── shared-rate-limit/        <─ existant
├── shared-storage/           <─ existant
├── shared-tracing/           <─ jar, jandex
└── <13 services>-service/    <─ inchangé en structure
```

Le parent `backend/pom.xml` déclare ces 10 libs **avant** les services dans `<modules>` (les services en dépendent ; Maven résolution build-order).

**Couverture cible.** Chaque shared lib doit atteindre **≥ 95 % lignes / ≥ 90 % branches** (les libs partagées sont small + critiques, exclu pas atteignable = 100 % via tests unitaires comme `shared-rate-limit`/`shared-storage`).

**Adresse** : REFACTOR-002, REFACTOR-003, REFACTOR-005, REFACTOR-006, REFACTOR-011, REFACTOR-012, REFACTOR-013, REFACTOR-018, BUG-011, KAFKA-007, KAFKA-009, SPEC-013, SPEC-019 (partiel) (cat 4 + 5 + 1)

---

(continuer à la suite dans le bloc 2)
### Décision E — Stratégie de portage des tests legacy : **port via `git show 41074e9:` + adaptation REST clients**

**Décision.** Les tests legacy supprimés au commit `b570c1b` sont **récupérés et portés** depuis l'arbre `41074e9` (= commit `41074e9 refactor(backend): migrate image upload from legacy to user/event services` — dernier commit avant la suppression de `legacy-monolith`). Pour chaque service, le pattern est :

1. `git show 41074e9:backend/services/legacy-monolith/src/test/java/<path>.java > backend/services/<svc>/src/test/java/<new-path>.java`
2. **Renommer le package** (`ch.unige.events.<X>` → `ch.unige.events.<svc>.<X>`).
3. **Adapter les imports** vers les nouveaux DTOs/enums (cf. `shared-domain-enums`, `shared-domain-dtos` une fois Étape 3 livrée).
4. **Remplacer les références aux entités cross-service** :
   - Tests qui utilisent `Event.findById(id)` directement → mock le REST client `EventServiceClient` via `@InjectMock` (Quarkus) ou WireMock.
   - Tests qui dépendent d'un autre service applicatif → refactor en mock @ApplicationScoped équivalent (cf. les `*ServiceMock` du legacy : `EventServiceMock`, `UserServiceMock`, `AttendanceServiceMock`, `FavoriteServiceMock`, `CommentServiceMock`).
5. **Conserver les noms exacts des sentinels** (35 sentinels documentés dans `sprint-context.md` SCRUM-138 / SCRUM-139 / SCRUM-144 / SCRUM-147 — cf. annexe E de l'audit). Les noms doivent ressortir verts pour qu'un `grep -rn "void <name>"` retombe dessus.

**Justification.** Réécrire from scratch coûte 5-10× plus cher et risque de manquer des cas-limites que les 1818 tests legacy capturent. Le portage = travail mécanique adapté à un exécuteur autonome.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Port `git show 41074e9:` + adaptation** | Préserve les sentinels (35 garantis verts), gros volume de cas-limites, effort proportionnel à la modification (rename + import + mock REST) | Tests legacy peuvent référencer du code supprimé/refactoré → adaptations parfois lourdes | ✅ retenu |
| (b) Réécrire from scratch en partant de la liste des sentinels | Tests propres, cohérents avec le nouveau modèle | Effort XXL, risque oubli de cas-limites | ❌ |
| (c) Garder le statu quo (sentinels seuls) | 0 effort | **Audit TEST-001 BLOCKER** | ❌ |

**Couverture cible** : ≥ **80 % lignes / ≥ 70 % branches par service** (matche le cible Sonar gate 80 % de la spec orig. décision 25). Pour les modules `shared-*` (hors glob d'exclusion), cible **≥ 95 % lignes / ≥ 90 % branches**.

**Sentinels obligatoires** (35, doivent être présents par nom et verts) :
- **SCRUM-147 (event-service, recurrence)** : `weekly_4Occurrences_returns3DatesSpacedBy7Days`, `monthly_handlesShortFebruaryFromJanuary31`, `bothNull_throwsIllegalArgumentException`, `maxOccurrencesAbove52_cappedTo52`, `from_parentRecurringEvent_exposesRecurrenceRuleAndNullParentEventId`, `from_occurrenceEvent_exposesParentEventIdAndNullRecurrenceRule`, `createRecurring_weekly4Occurrences_persists1ParentAnd3Children`, `createRecurring_withoutEndDateOrMaxOccurrences_returns400_recurrenceUnbounded`, `createRecurring_endDateBeforeStart_returns400_recurrenceEndBeforeStart`, `createRecurring_inheritsParentStatusPublished`, `getOccurrences_parentRecurring_returnsChildrenSortedAsc`, `getOccurrences_standaloneEvent_returns200EmptyList`, `getOccurrences_draftByNonCreator_returns404_antiOracle`, `update_parentTitle_doesNotPropagateToOccurrences`, `cancel_parentDoesNotCascadeToOccurrences`, `delete_parent_setsOccurrencesParentEventIdToNull`, `post_validRecurrenceWeekly_returns201_recurrenceRuleSetOnParent`, `post_recurrenceMaxOccurrences53_returns400_beanValidation`, `getOccurrences_parentPublishedAnonymous_returns200`, `getOccurrences_sizeOver52_returns400`, `getOccurrences_draftByAnonymous_returns404_antiOracle`
- **SCRUM-138 (follow-service)** : `findAcceptedFollowedIds_returnsOnlyAcceptedUuids`, `rejectRequest_followerCanReFollowAfterReject`, `follow_selfFollow_throwsUnprocessable`, `getFollowers_privateProfileNonOwner_returns404_antiOracle`, `getPublicProfile_self_followStatusIsNull`, `getPublicProfile_authNonOwnerWithPending_followStatusIsPending`
- **SCRUM-144 (comment-service)** : `prePersist_setsCreatedAt`, `post_eventDraftByNonCreator_returns404_antiOracle`, `post_eventBanned_returns404_antiOracle`, `post_replyToReply_returns422_repliesTooDeep`, `post_parentInOtherEvent_returns422_parentNotInEvent`, `post_unknownParent_returns404_parentNotFound`, `delete_byPendingCoOrganizer_returns403`, `delete_unknownComment_returns404_commentNotFound`

**Adresse** : TEST-001 (BLOCKER), TEST-002, TEST-003, TEST-004, TEST-005, TEST-006, TEST-007, TEST-008, TEST-009, TEST-010, TEST-011, TEST-012, TEST-013, TEST-014, TEST-015, TEST-016, TEST-017, TEST-018 (cat 3, intégralité)

### Décision F — Producteurs Kafka manquants : **pattern uniforme `<Domain>LifecyclePublisher` + bridge CDI AFTER_SUCCESS**

**Décision.** Tous les producteurs Kafka manquants suivent le **même pattern** que `EventLifecyclePublisher` (livré au commit `5dce9be` — référence vivante dans le code). Pour chaque service producteur :

1. **POM** : ajouter `quarkus-messaging-kafka` (compile) + `smallrye-reactive-messaging-in-memory` (test scope) — voire factoriser dans `<dependencyManagement>` parent (cf. KAFKA-009, fait en Étape 3.0).
2. **Record payload** : déplacé dans `shared-kafka-events` (cf. Décision D). Conventions :
   - Java `record` immuable.
   - Champs : clé de partition (`eventId` / `userId`), discriminator `Type` enum si plusieurs lifecycle states partagent le record, `Instant occurredAt`.
   - **Pas** de champ nullable sauf nécessité métier explicite.
   - Méthodes factory statiques par Type (cf. `EventLifecycleEvent.published(...)`).
3. **Publisher** (`<svc>/kafka/<Domain>Publisher.java`) :
   - `@ApplicationScoped`.
   - 1 `Emitter<TheRecord>` par topic, injecté via constructeur avec `@Channel("<topic-channel-name>")`.
   - 1 méthode publique par lifecycle state.
   - Méthode privée `send()` qui catch `Exception` et log warn — **ne propage jamais**.
4. **Bridge CDI** (`<svc>/kafka/<Domain>KafkaBridge.java`) :
   - `@ApplicationScoped`.
   - Une méthode `void onAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) <Domain>Event ev)` qui appelle `publisher.send(ev)`.
5. **Wiring** dans le service métier : `@Inject jakarta.enterprise.event.Event<<Domain>Event> cdiEvent;` puis `cdiEvent.fire(...)` dans la méthode `@Transactional`.
6. **Config `application.properties`** :
   ```properties
   %dev,prod.kafka.bootstrap.servers=${KAFKA_BOOTSTRAP_SERVERS:kafka:9092}
   %test.kafka.bootstrap.servers=localhost:9092

   mp.messaging.outgoing.<chan>.connector=smallrye-kafka
   mp.messaging.outgoing.<chan>.topic=<topic.name.exact>
   mp.messaging.outgoing.<chan>.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer
   %test.mp.messaging.outgoing.<chan>.connector=smallrye-in-memory
   ```

**Topics cibles à câbler en complétion** (9/10) :

| Topic | Producteur | Partition key | Payload (record) | Trigger métier |
|---|---|---|---|---|
| `events.published` | event-service ✅ déjà livré | `eventId` | `EventLifecycleEvent.published(eventId, creatorId)` | `EventService.publish()` |
| `events.cancelled` | event-service ✅ déjà livré | `eventId` | `EventLifecycleEvent.cancelled(eventId, creatorId)` | `EventService.cancel()` |
| `events.expired` | event-service ✅ déjà livré | `eventId` | `EventLifecycleEvent.expired(eventId, creatorId)` | `EventExpirationService.expireEvents()` boucle |
| `events.banned` | **report-service** | `eventId` | `EventBannedEvent.banned(eventId, bannedBy, reason)` | `ReportService.handle()` quand `decision = BAN` ; `ModerationCleanupService.runCleanup()` quand auto-ban après 7j sans validation |
| `users.followed` | **follow-service** | `followedId` | `FollowLifecycleEvent.followed(followerId, followedId)` | `FollowService.follow()` quand auto-accept (target = profilePublic) |
| `users.follow-requested` | **follow-service** | `followedId` | `FollowLifecycleEvent.followRequested(followerId, followedId)` | `FollowService.follow()` quand PENDING (target = profile privé) |
| `users.follow-accepted` | **follow-service** | `followedId` | `FollowLifecycleEvent.followAccepted(followerId, followedId)` | `FollowService.acceptRequest()` |
| `comments.created` | **comment-service** | `eventId` | `CommentCreatedEvent(commentId, eventId, authorId, parentCommentId, createdAt)` | `CommentService.post()` |
| `co-organizers.invited` | **co-organizer-service** | `userId` | `CoOrganizerEvent.invited(eventId, userId)` | `EventCoOrganizerService.invite()` |
| `co-organizers.accepted` | **co-organizer-service** | `eventId` | `CoOrganizerEvent.accepted(eventId, userId)` | `EventCoOrganizerService.accept()` |

**Consommateur unique en complétion** : event-service consomme `events.banned` pour appliquer `event.status = BANNED` localement. Pattern (à suivre uniformément si d'autres consommateurs émergent) :
```java
@Incoming("events-banned")
@Transactional
public void onBanned(EventBannedEvent ev) {
    Event event = Event.<Event>findByIdOptional(ev.eventId()).orElse(null);
    if (event == null) return;  // idempotence: l'event a déjà été supprimé
    if (event.status == EventStatus.BANNED) return;  // idempotence: déjà banni
    event.status = EventStatus.BANNED;
    // pas de re-fire de Kafka ici — l'event banned est consommé, pas re-publié
}
```

Config consumer :
```properties
mp.messaging.incoming.events-banned.connector=smallrye-kafka
mp.messaging.incoming.events-banned.topic=events.banned
mp.messaging.incoming.events-banned.value.deserializer=io.quarkus.kafka.client.serialization.ObjectMapperDeserializer
mp.messaging.incoming.events-banned.value.deserializer.type=ch.unige.events.shared.kafka.events.EventBannedEvent
mp.messaging.incoming.events-banned.group.id=event-service
%test.mp.messaging.incoming.events-banned.connector=smallrye-in-memory
```

**Livraison conjointe report-service producer + event-service consumer** : les deux **doivent ship dans le MÊME commit** (Étape 4.4) pour éviter le risque half-shipped (BAN admin silencieux si producer ship sans consumer, ou message lost si consumer ship sans producer). Le retrait des mutations cross-schema (`EventStub.status = BANNED` dans `ReportService.java:122-124` + `ModerationCleanupService.java:69`) ship dans **le même commit** que le producer.

**Adresse** : KAFKA-001 (BLOCKER), KAFKA-002 (BLOCKER), KAFKA-003 (HIGH), KAFKA-004 (HIGH), KAFKA-005 (HIGH), KAFKA-006 (informational notification-service), KAFKA-007 (lib partagée — déjà décidé en Décision D), KAFKA-008 (`value.serializer` explicite), KAFKA-009 (in-memory dans dependencyManagement parent), SPEC-005, SPEC-022, HYGIENE-004 (TODO obsolète) (cat 5 + 1 + 10)

### Décision G — Plugin Kong rate-limiting : **`policy: local`, ajout par-route**

**Décision.** Le plugin Kong `rate-limiting` est ajouté à la `ConfigMap` Kong (`k8s/chart/templates/kong/configmap-routes.yaml`) avec **`policy: local`** sur les 3 routes ciblées par la spec orig. décision 6 :

| Route Kong | Bucket | Limite |
|---|---|---|
| `events-list` (POST `/api/events`) | `events.create` | `minute: 10` |
| `event-comments-post` (POST `/api/events/(?:\d+)/comments`) | `comments.post` | `minute: 10` |
| `follow-actions` (POST `/api/users/[^/]+/follow`) | `follows.follow` | `minute: 30` |

YAML pattern (à coller pour chaque route) :
```yaml
plugins:
  - name: rate-limiting
    config:
      minute: 10
      policy: local
      fault_tolerant: true
      hide_client_headers: false
```

**Justification.** `policy: local` ne nécessite pas de Redis cluster-wide (une instance Kong = un compteur isolé). Acceptable en S8 où Kong tourne avec `replicas: 2` (cf. `values.yaml`) — l'attaquant peut tripler son budget en routant sur une autre instance, ce qui rest acceptable au regard de la protection edge complémentaire du `@PerUserRateLimit` Java déjà actif (deuxième étage). La migration vers `policy: redis` cluster-wide est différée DevOps (corrélée à la mise en place d'un Redis Helm). Acte explicite dans la spec.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **`policy: local`** | 0 dépendance externe, immédiat, suffisant pour S8 single-cluster | Compteur par instance Kong, pas global | ✅ retenu |
| (b) `policy: redis` | Cohérence cluster-wide | Prérequis Redis Helm chart, complexité ops, hors scope S8 | ❌ → DevOps |
| (c) Statu quo (pas de plugin Kong) | 0 effort | **Audit SPEC-003 / INFRA-002** | ❌ |

**Adresse** : SPEC-003, INFRA-002 (cat 1 critical, cat 6 high)

### Décision H — CI matrix per-service : **YAML produit en S8, activation par DevOps**

**Décision.** Le YAML `.github/workflows/build.yml` est **refondu en `strategy.matrix.service: [...]`** dans la complétion S8. Chaque module `services/<svc>-service/` publie son propre `sonar.projectKey` via override dans son `pom.xml`. Le glob `<sonar.coverage.exclusions>services/*-service/**/*</sonar.coverage.exclusions>` du parent POM est **supprimé** (legacy-monolith n'existe plus, l'exclusion devait disparaître à PR 14 — c'est fait). La propriété Helm `image.api.tag` est renommée `image.tag` dans `values.yaml` + 14 templates (les services), et `deploy.yml` est ajusté en conséquence (ou alternativement chaque service a son propre `image.<svc>.tag`).

**Préalable DevOps** : 13 SonarCloud projects à créer manuellement (`unige-events-share-service`, `unige-events-view-service`, ..., `unige-events-event-service`) + 13 secrets `SONAR_TOKEN_<SVC>` ajoutés au repo Github (ou un seul `SONAR_TOKEN` multi-projets si SonarCloud le permet). Le YAML CI référence ces tokens. **Sans cette action DevOps, le workflow CI échouera côté Sonar à la première merge.** La spec acte cette dépendance comme un blocker DevOps mais livre quand même le YAML pour que DevOps puisse activer en flippant un seul switch.

**Décision détaillée** :
- `build.yml` matrix sur les 13 microservices (notification-service skip car placeholder vide).
- Les 2 shared libs (`shared-rate-limit`, `shared-storage`) restent buildées dans le job principal (parent reactor).
- Les 8 nouvelles libs partagées (cf. Décision D) idem.
- `sonar.projectKey` par module : surcharge dans `<properties>` du POM enfant.
- `deploy.yml` : remplacer `--set image.api.tag="${{ github.sha }}"` par 13 lignes `--set image.<svc>.tag="${{ github.sha }}"` OU une lecture programmatique. Le rename `image.api.tag → image.tag` ou `image.<svc>.tag` est documenté en commit dédié.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Matrix per-service + projectKey override + 13 SonarCloud projects** | Spec orig. décision 17/25 honorée, dashboards par service, build incrémental | Dépendance DevOps (13 projets à créer), churn YAML | ✅ retenu, livré pour activation DevOps |
| (b) Statu quo (single-job) | 0 effort | **Audit SPEC-007 / INFRA-010 / INFRA-011 / HYGIENE-005** | ❌ |
| (c) Matrix SANS projectKey override (tous projettent vers `unige-events-backend`) | Simple | Pas de granularité Sonar — défense la spec orig. décision 25 | ❌ |

**Adresse** : SPEC-007, SPEC-008, SPEC-019, INFRA-007, INFRA-010, INFRA-011, INFRA-012, HYGIENE-005 (cat 1 + 6 + 7 + 10)

### Décision I — Outbox pattern : **non retenu** (cf. Décision A)

**Décision.** Pas d'outbox pattern. Voir Décision A pour la justification (CDI `@Observes(AFTER_SUCCESS)` couvre les besoins du projet pédagogique).

Cette décision existe pour matérialiser un **rejet explicite** ; elle est référencée par les findings BUG-001/002 mais traitée par Décision A.

### Décision J — Pact contract tests + E2E happy path : **livrer minimum viable**

**Décision.** Création de `backend/contract-tests/` avec **4 pacts JSON** + `backend/e2e/E2EHappyPathTest.java` (RestAssured smoke unique).

**4 pacts cibles** (couvrent les chemins critiques cross-service) :
1. `share-service` (consumer) ↔ `event-service` (provider) : `GET /events/{id}` retourne `{id, shareCode, status, ...}`. Pact JSON commit dans `backend/contract-tests/pacts/share-event.json`.
2. `comment-service` (consumer) ↔ `event-service` (provider) : `GET /events/{id}` retourne `{id, status, creatorId}` (pour cascade ISSUE-92).
3. `comment-service` (consumer) ↔ `co-organizer-service` (provider) : `GET /events/{eventId}/co-organizers/check?userId=` retourne `{accepted: bool}` (pour cascade SCRUM-136).
4. `report-service` (consumer) ↔ `event-service` (provider) : `GET /events/{id}` retourne `{id, status, creatorId}`.

**E2E happy path** : `backend/e2e/E2EHappyPathTest.java` — un seul test `@QuarkusIntegrationTest` qui :
1. crée un User via `POST /users/me` (auto-création first-login),
2. crée un Event via `POST /events` (status DRAFT),
3. publie l'event via `PATCH /events/{id}/publish`,
4. confirme le 201/200 + un GET `/events/{id}` retourne le creatorId enrichi.

**Justification.** Couverture minimale qui valide la chaîne REST cross-service end-to-end + les contrats Kafka payload critique. Le brief Agon mentionne explicitement « tests unit + integration + Pact + 1 E2E ».

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **4 pacts + 1 E2E** | Couvre les 3 anti-oracles (ISSUE-92 via comment-event ; ISSUE-93 future via follow-user ; SCRUM-136 via comment-coorg) + 1 happy path complet | Effort M (≈ 1-2 jours) | ✅ retenu |
| (b) Pacts pour TOUS les couples consumer-provider | Couverture exhaustive | XL effort, plupart des contrats triviaux | ❌ over-engineering S8 |
| (c) Pas de Pact (statu quo) | 0 effort | **Audit SPEC-006** | ❌ |

**Adresse** : SPEC-006 (cat 1 high)

### Décision K — Logs JSON + Prometheus + tracing X-Request-ID

**Décision.** Tous les 13 services métiers (notification-service exclu) gagnent **3 extensions Quarkus** :
- `quarkus-logging-json` (logs structurés JSON sur stdout, agrégeable par K8s + ELK).
- `quarkus-micrometer-registry-prometheus` (endpoint `/q/metrics` exposé).
- consommation de `shared-tracing` (lib créée en Décision D) pour le `RequestIdFilter` + propagation `X-Request-ID` aux REST clients sortants.

**Configuration `application.properties`** par service :
```properties
# Logs JSON
quarkus.log.console.json=true
%dev,prod.quarkus.log.console.json.fields.timestamp.format=yyyy-MM-dd'T'HH:mm:ss.SSSXXX

# Prometheus
quarkus.micrometer.export.prometheus.enabled=true
quarkus.micrometer.export.prometheus.path=/q/metrics
```

**Endpoint `/q/metrics`** : déjà sous `quarkus.http.root-path=api`, donc accessible à `http://<svc>:8080/api/q/metrics`. **Pas exposé via Kong** (pas de route ajoutée — endpoint interne pour scrape Prometheus K8s). DevOps ajoutera un `ServiceMonitor` ou similaire (hors scope spec).

**Justification.** Spec orig. décision 19 explicite. Sans cette pièce, DevOps ne peut pas brancher Prometheus / Grafana ni corréler les logs cross-service.

**Adresse** : SPEC-004 (3 deps obligatoires sur 4 — `rest-client-reactive` traité en Décision B, `messaging-kafka` déjà sur event-service + ajouté aux producteurs), SPEC-012, SPEC-013, SPEC-019 (cat 1 critical/high/medium)

### Décision L — Anti-oracles ISSUE-92, ISSUE-93 + cascade SCRUM-136 : **centralisés derrière REST clients**

**Décision.** Les 3 règles de visibilité critiques de la PR sont **centralisées au niveau du service propriétaire** et propagées via REST :
- **ISSUE-92** (anti-oracle 404 sur Event DRAFT/CANCELLED non-créateur non-admin) : la règle vit **uniquement** dans `event-service.EventService.getById(id, auth0Id, isAdmin)`. Les consommateurs (comment-service, attendance-service, co-organizer-service, report-service, stats-service, calendar-service, favorite-service, view-service) appellent simplement `eventServiceClient.getById(id)` et **propagent le 404** s'il survient. Plus de `assertEventVisibleAndLoad` dupliqué.
- **ISSUE-93** (anti-oracle 404 sur User profilePublic=false non-self non-admin) : la règle vit **uniquement** dans `user-service.UserService.getPublicProfile(uuid, callerAuth0Id, isAdmin)`. Les consommateurs (follow-service notamment) appellent `userServiceClient.getById(uuid)` et propagent le 404.
- **Cascade SCRUM-136** (`isCreatorOrAcceptedCoOrganizer`) : un nouvel endpoint REST côté co-organizer-service `GET /events/{eventId}/co-organizers/check?userId={uuid}` retourne `{accepted: boolean}`. Les consommateurs (comment-service, attendance-service, report-service, stats-service, event-service) l'appellent au lieu d'inliner la logique. La logique « creator OR accepted » côté consumer = `event.creatorId.equals(callerId) || coOrgClient.check(eventId, callerId).accepted()`.

**Justification.** Aujourd'hui, les anti-oracles sont **dupliqués** dans 3-5 services chacun (REFACTOR-009, REFACTOR-010, SEC-002). Toute évolution des règles risque un drift de sécurité silencieux. Centralisation = source unique de vérité. Le coût latence (1 appel REST par check) est acceptable au regard de la sécurité.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Centralisation REST** | Règle unique, drift impossible, latence négligeable (~5ms LAN) | Couplage temporel (panne event-service → 503 sur comment-service) — mitigé par `@CircuitBreaker` + `@Fallback(throw 503)` | ✅ retenu |
| (b) Lib partagée `shared-authz` qui contient la règle (sans REST) | Règle unique, pas de réseau | Reste de la duplication via inlining ; pas vraiment microservices ; suppose accès aux entités cross-service via stubs (pas compatible Décision B) | ❌ |
| (c) Statu quo (inlining) | 0 effort | Drift de sécurité garanti à terme | ❌ |

**Application.**
- En Étape 5 (REST clients), les helpers locaux (`assertEventVisibleAndLoad`, `assertProfileVisible`, `isCreatorOrAcceptedCoOrganizer`) sont **supprimés** des consumers. Les call-sites passent par REST client.
- Côté event-service, `EventResource.getById` ne change pas (déjà l'anti-oracle). On expose juste l'endpoint comme service-to-service via les REST clients internes.
- Côté co-organizer-service, **nouveau endpoint** `GET /events/{eventId}/co-organizers/check?userId={uuid}` créé (cf. Décision B). **Endpoint interne** non exposé via Kong (pas de route Kong ajoutée).

**Adresse** : SPEC-012, SPEC-013 (cascade et anti-oracle), SPEC-014, SPEC-021, REFACTOR-009, REFACTOR-010, SEC-002 (cat 1 + 4 + 9)

### Décision M — Findings « non-régression intentionnelle » : **REJETÉS sauf SEC-001/003**

**Décision.** Les inconsistencies vs legacy listées dans le bloc Sécurité de l'audit sont **partiellement alignées** :
- **SEC-001** (`UserService.getPublicProfile` n'accepte pas le bypass admin) : **CORRIGÉ**. Signature étendue à `getPublicProfile(UUID, String callerAuth0Id, boolean isAdmin)`. Cohérence avec `EventService.getById`.
- **SEC-003** (`cancel` / `restore` n'acceptent pas le bypass admin) : **CORRIGÉ**. Signature étendue à `(Long id, String auth0Id, boolean isAdmin)`. La logique : `isAdmin || isCreatorOrAcceptedCoOrganizer(...)`.
- **SEC-004** (defaults OIDC bidons) : **CORRIGÉ** côté code (retirer les valeurs par défaut, fail-fast au boot). Le Helm chart doit garantir les vars d'env via Doppler — c'est le job DevOps mais le code source ne couvre plus la régression silencieuse.

**Justification.** Aligner sur la convention `EventService` (admin bypass) plutôt qu'admettre une inconsistance pédagogique. Friction modération admin via `getPublicProfile` est réelle (admin doit pouvoir voir tous les profils privés pour la modération). Friction `cancel/restore` est moindre mais reste inconsistante.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) **Aligner admin bypass partout** | Cohérence cross-service, pédagogie | Régression de comportement vs legacy (admin n'avait pas accès à `getPublicProfile`) — mais c'est un **fix**, pas un bug introduit | ✅ retenu |
| (b) Acter formellement l'inconsistance | 0 effort | Continue de propager le pattern du legacy | ❌ |

**Adresse** : SEC-001, SEC-003, SEC-004 (cat 9)

### Décision N — `notification-service` placeholder : **statu quo, formalisé**

**Décision.** Le placeholder `notification-service` reste **`<packaging>jar</packaging>`** avec son `ServiceIdentityResource` unique, **`replicas: 0`** dans le Helm chart, **route Kong commentée** dans la ConfigMap. Une note explicite est ajoutée dans son `pom.xml` (commentaire) + dans `backend/docs/devops-handoff.md` + dans le PR body.

**Justification.** SCRUM-99 (infra Notification + entité Notification persistée + endpoints `/notifications`, `/notifications/{id}/read`) est explicitement hors scope spec orig. (cf. Décision 14 ligne 419). Le scaffold est prêt à recevoir le code à S9+.

**Adresse** : SPEC-010, SPEC-022, KAFKA-006, HYGIENE-003 (cat 1 + 5 + 10)

### Décision O — Idempotence `addFavorite` + race `removeAttendance` + guard `cancel`

**Décision.** 3 bug fixes simples regroupés en Étape 1 :
- **BUG-003** : `EventService.cancel` rejette `EXPIRED` (un cancel sur EXPIRED n'a pas de sens métier + cause double émission Kafka). Code ajouté :
  ```java
  if (event.status == EventStatus.EXPIRED) {
      throw conflict("Expired events cannot be cancelled.");
  }
  ```
- **BUG-005** : `AttendanceService.removeAttendance` réordonne les opérations — le lock pessimiste sur `EventStub` (ou sur `Event` une fois le stub remplacé en Étape 5) est acquis **avant** la lecture de l'attendance. Code refactor.
- **BUG-006** : `FavoriteService.addFavorite` aligné sur le pattern `FollowService.follow` — try/catch `PersistenceException` + check unique constraint, pour idempotence sous race.

**Adresse** : BUG-003, BUG-005, BUG-006 (cat 2)

### Décision P — Defaults config + warning `quarkus.flyway.enabled` + autres fixes hygiène

**Décision.** En Étape 1 :
- **HYGIENE-001** : retirer `quarkus.flyway.enabled=false` des 13 `application.properties`. Sans la dépendance `quarkus-flyway` au POM (ce qui est le cas), la propriété est inconnue → warning runtime à chaque boot. Le commentaire explicatif au-dessus reste documentaire.
- **HYGIENE-002** : normaliser le format de `share-service/pom.xml` (compact, comme les 12 autres).
- **HYGIENE-004** : retirer le TODO obsolète dans `ModerationCleanupService.java:70` (« once event-service ships ») — devient inutile une fois Décision F livrée (Étape 4.4).
- **BUG-009** : ajouter `@Transactional` sur `UserService.getPublicProfile`.
- **BUG-010** : guard null explicite dans `UserService.updateMyProfile`.
- **BUG-011** : extraire `parseTimeframe` dans `shared-jaxrs.TimeframeParamConverter` (cf. Décision D).
- **BUG-012** : retirer le default `https://10.25.10.136.nip.io` dans `RedirectResource` → fail-fast au boot.
- **BUG-014** : normaliser TZ UTC à l'ingestion `EventRequestBase` (commentaire explicite + utilitaire `EventRequestBase.normalizeStartDateUtc`).

**Adresse** : BUG-009, BUG-010, BUG-011, BUG-012, BUG-014, HYGIENE-001, HYGIENE-002, HYGIENE-004 (cat 2 + 10)

### Décision Q — Doublon openapi `/events/{id}/view` : **suppression du premier bloc**

**Décision.** Le doublon `POST /events/{id}/view` aux lignes ~3482 et ~3560 d'`openapi/openapi.yaml` est résolu en **supprimant le premier bloc** (`l.3482-3513`, ensemble d'erreurs plus pauvre). Le second bloc (`l.3560-3585`) est conservé — il a la couverture d'erreurs la plus complète.

**Justification.** Les codecs OpenAPI lisent le second et écrasent silencieusement le premier ; en pratique seul le second est respecté. Mais les générateurs stricts (openapi-generator-cli v7+) émettent un warning. Suppression nettoie la situation.

**Cette modification est la SEULE qui touche `openapi/openapi.yaml`.** Elle est explicitement actée comme déviation de l'invariant « `git diff --stat openapi/` strictement vide ». Toute autre modification d'`openapi/openapi.yaml` doit lever un blocker.

**Justification de la non-modification d'`openapi.yaml` pour les nouveaux endpoints internes (Décision B).** Les endpoints `GET /users/by-auth0/{auth0Id}`, `GET /users/by-calendar-token/{token}`, `GET /events/{id}/capacity-summary`, `GET /events/{id}/view-count`, `GET /events/{id}/favorite-count`, `GET /events/{eventId}/attendance-summary`, `GET /events/{eventId}/co-organizers/check?userId=`, `GET /events/{eventId}/co-organizers/accepted-user-ids`, `GET /users/{id}/follow-counts` sont **internes service-to-service**. Ils ne sont **pas** routés via Kong (pas d'ajout dans `configmap-routes.yaml`) et ne font pas partie du contrat public. Ils sont documentés dans `backend/docs/internal-endpoints.md` (à créer en Étape 5.0). **Dérogation justifiée** à la règle openapi-first.

**Adresse** : INFRA-001, INFRA-004 (cat 6)

### Décision R — Helm hygiene : `livenessProbe` + commentaires alignés

**Décision.** En Étape 11 :
- **INFRA-006** : ajouter `livenessProbe` aux 13 `k8s/chart/templates/<svc>-service/deployment.yaml` (notification-service exclu, `replicas: 0`). Bloc YAML standard :
  ```yaml
  livenessProbe:
    httpGet:
      path: /api/q/health/live
      port: 8080
    initialDelaySeconds: 30
    periodSeconds: 30
    failureThreshold: 3
  ```
- **INFRA-009** : commentaire `templates/ingress/ingress.yaml:17-21` mis à jour (legacy monolith → Kong DB-less + 13 services).

**Adresse** : INFRA-006, INFRA-008 (informational), INFRA-009 (cat 6)

### Décision S — JPA stubs supprimés progressivement

**Décision.** L'ordre de suppression des 35 stubs est lié à l'ordre des REST clients (Étape 5). Pattern : pour chaque service consommateur, **dans le même commit** :
1. ajouter le REST client `@RegisterRestClient`,
2. remplacer les call-sites `Stub.findByXxx(...)` par `client.getXxx(...)`,
3. supprimer la classe `*Stub.java`.

Si plusieurs stubs sont consommés par le même service, on peut grouper en un seul commit (commit 5.<svc>) ou séquencer (5.<svc>.1, 5.<svc>.2, ...).

**Adresse** : (déjà couvert par Décision B)

### Décision T — Branche cible et workflow Git

**Décision.** **Branche persistante `refactor(backend)--migrate-to-microservices`**. **NE PAS** créer de nouvelle branche, NE PAS modifier `main`, NE PAS modifier les autres branches feature (`feature/s7-recurrence`, etc.). Tous les commits de la complétion vont sur cette même branche, additifs au-dessus du HEAD courant.

Push après chaque sous-étape numérotée (`N.M`) verte (build local OK). Pas de squash, pas de force push. Si CI échoue, fix root cause + nouveau commit (jamais `--amend` sur du commit pushé). Les 5 commits de l'Étape 18 livrés en 2026-05-09 (446ea3e..bee933d) restent intacts.

**Adresse** : SPEC-017 (cat 1)

### Décision U — Documentation : pass de cohérence end-to-end

**Décision.** Étape 13 (final docs) couvre les 24 findings DOC-001..024 :
- Réécriture de `backend/AGENTS.md` (DOC-020/021).
- Update de `backend/docs/architecture.md` pour l'état post-completion (DOC-001..006).
- Update de `data-model.md` (DOC-007/008).
- Update de `api-contract.md` (DOC-009/010).
- Update de `dev-guide.md` (DOC-011/012).
- Update de `sprint-context.md` Étape 19 (post-completion) + correction du tableau « Écarts vs spec » (DOC-014/015/016) + substitution des 11 placeholders `<this PR>` (DOC-013).
- Update de `microservices-migration-roadmap.md` avec status réel par PR + ajout PR 17 (consolidation) + PR 18 (completion) (DOC-017/018).
- Update de `AGENTS.md` racine (DOC-019).
- Update du PR body de #158 (DOC-022/023/024) — via `gh pr edit 158 --body-file`.
- Création de `backend/docs/internal-endpoints.md` (cf. Décision Q).
- Création de `backend/docs/devops-handoff.md` (cf. Décision V).

**Adresse** : DOC-001..024 (cat 8, intégralité)

### Décision V — Frontière DevOps : 7 items hors scope, formalisés

**Décision.** Les 7 items DevOps suivants sont **explicitement hors scope** de la complétion S8 et formalisés dans `backend/docs/devops-handoff.md` (à créer en Étape 13.6). Format de chaque item dans le doc : `Titre ; Statut spec ; Justification du report ; Action attendue côté DevOps ; SHA backend qui livre la moitié backend (s'il y en a une)`.

| # | Item | Justification report S9+ | Backend a-t-il livré sa moitié ? |
|---|---|---|---|
| 1 | Création de 13 SonarCloud projects (un par microservice) | Nécessite SonarCloud admin UI ; le YAML CI matrix l'attend (cf. Décision H) | ✅ YAML CI livré en Étape 12 |
| 2 | Cluster Kafka prod-grade (RF=3, partitions ≥ 3, ISR ≥ 2, durabilité acks=all) | Hors scope cours, single-broker S8 OK | ✅ Helm chart single-broker livré |
| 3 | Schemas-per-service (Flyway séparé) | Reportée par Décision C avec justification | ❌ aucune action backend, c'est une déviation actée |
| 4 | NetworkPolicies K8s pour isoler le trafic service-to-service | Hors scope code, pure ops K8s | N/A |
| 5 | Domaines / certs prod / Cloudflare tunnel preview | Hors scope code | N/A |
| 6 | Secrets Doppler `DB_PASSWORD`, `OIDC_*`, `S3_*`, `KAFKA_BOOTSTRAP_SERVERS`, etc. | Hors scope code, pure ops | N/A |
| 7 | Production-grade Kong : DB-mode (Postgres dédié), tracing OpenTelemetry, plugin `rate-limiting` policy=redis cluster-wide | Hors scope cours, DB-less S8 OK ; rate-limiting `policy: local` livré (cf. Décision G) | ✅ rate-limiting `policy: local` livré |

**Adresse** : Cat 11 informational (audit) — formalisée

### Décision W — Stratégie de validation CI à chaque étape

**Décision.** Après chaque sous-étape numérotée (`N.M`) :
1. `cd backend && ./mvnw verify -DskipITs` localement → doit être SUCCESS sur tous les modules touchés (et idéalement le reactor entier — ~3-4 min).
2. `git add` ciblé (pas `git add -A`), `git commit` avec le message-type de la sous-étape, `git push origin 'refactor(backend)--migrate-to-microservices'`.
3. `gh pr checks 158 --watch` jusqu'à terminaison (~10-15 min). Tous les checks doivent passer : Build BE, Build FE, SonarCloud Backend, SonarCloud Frontend, Deploy Preview, PR Title Check.
4. Si un check échoue : lire les logs (`gh run view <RUN_ID> --log-failed`), fixer la cause racine, **commit additif** (jamais `--amend`).
5. **Pas de `--no-verify`, pas de `--no-gpg-sign`, pas de `@Disabled`, pas de skip silencieux.**

**Adresse** : (workflow général)

### Décision X — Ordre d'implémentation entre étapes (rappel)

**Décision.** L'ordre des 14 étapes (Plan d'implémentation ci-dessous) est **strict**. Les dépendances clés :
- Étape 3 (shared libs) **DOIT** précéder Étape 4 (Kafka producteurs ; ils utilisent `shared-kafka-events`).
- Étape 3 **DOIT** précéder Étape 5 (REST clients ; ils utilisent `shared-domain-dtos`, `shared-domain-enums`, `shared-jaxrs`, `shared-tracing`).
- Étape 5 (REST clients) **DOIT** précéder Étape 7 (tests legacy ; ils mockent les REST clients via WireMock — sinon les tests cassent les call-sites JPA stubs supprimés).
- Étape 4 (Kafka) peut être faite EN PARALLÈLE de Étape 5 (REST clients) si gestion fine, mais en pratique l'exécuteur autonome séquence (4 puis 5) pour éviter le débordement de contexte.
- Étape 7 (tests legacy) **DOIT** précéder Étape 8 (Pact + E2E) — Pact teste des contrats que les tests unitaires couvrent en partie.
- Étape 9 (observabilité) peut être faite **après** ou **en parallèle** de Étape 5/7 — les 3 deps Quarkus s'ajoutent indépendamment.
- Étape 10 (Kong rate-limiting) est isolée — peut être faite à n'importe quel moment.
- Étape 11 (Helm hygiene) idem.
- Étape 12 (CI matrix YAML) **DOIT** précéder Étape 14 (vérification finale).
- Étape 13 (docs) **DOIT** être la dernière avant la vérification finale (Étape 14) — elle reflète l'état atteint.

### Décision Y — Granularité des commits

**Décision.** **1 commit par sous-étape numérotée** (`N.M`). Granularité visée :
- 5-50 fichiers touchés par commit ;
- ≤ ~500 lignes diff par commit (sauf création de lib partagée, où ~1000 lignes acceptable) ;
- Build local vert avant push (sinon plus de commit) ;
- Message commit conforme à `pr-title-check.yml` (mais s'applique au PR title, pas aux commits — donc liberté sur les messages de commit hors PR title).

**Adresse** : (workflow général)

---

## Architecture cible (post-completion)

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
                                    │    (per-route: events.create=10, │
                                    │     comments.post=10,            │
                                    │     follows.follow=30 / minute)  │
                                    └──┬───────────────────────────────┘
                                       │
       ┌───────────────────────────────┼───────────────────────────────────┐
       ▼                               ▼                                   ▼
  ┌────────────┐                ┌────────────┐                      ┌────────────┐
  │   user-    │   REST sync   │  event-    │  REST sync           │ attendance │
  │  service   │ ─────────────▶│  service   │◀──────────────       │  service   │
  │ (replica:1)│                │ (replica:1)│                      │ (replica:1)│
  └─────┬──────┘                └─────┬──────┘                      └─────┬──────┘
        │ Kafka                       │ Kafka                              │ Kafka
        │ (no producer)               │ events.{published,                 │ (no producer)
        │                             │ cancelled,expired}                 │
        │                             │ ◀─── consumes events.banned        │
        │                             ▼                                   │
        │                       ┌────────────────┐                        │
        │                       │  Kafka KRaft   │                        │
        │                       │ (single-broker)│                        │
        │                       │  10 topics     │                        │
        │                       └────────┬───────┘                        │
        │                                │                                │
        ▼                                ▼                                ▼
   (similar pattern for follow, comment, co-organizer, report, share, view, calendar, stats, favorite, me-aggregator)


  Shared Libs (consumed by all services):
  ┌──────────────────────────────────────────────────────────────────────────┐
  │ shared-rate-limit (existant) │ shared-storage (existant)                 │
  │ shared-api-error (NEW)       │ shared-domain-enums (NEW)                 │
  │ shared-domain-dtos (NEW)     │ shared-domain-projections (NEW)           │
  │ shared-jaxrs (NEW)           │ shared-tracing (NEW)                      │
  │ shared-kafka-events (NEW)    │ shared-platform (NEW)                     │
  └──────────────────────────────────────────────────────────────────────────┘

  PostgreSQL: une seule instance, schéma `public` partagé (Décision C → S9+).
  S3 (MinIO): 1 bucket `unige-events`, accédé par user-service + event-service via shared-storage.

  notification-service: replicas:0 (placeholder), SCRUM-99 hors scope.
```

### Tableau des shared libs finales (10 total)

| # | Lib | Packaging | Tests cible | Hors glob exclusion Sonar | Consommateurs |
|---|---|---|---|---|---|
| 1 | `shared-rate-limit` (existant) | jar | 100 % L / 100 % B | ✅ oui | 6 services (event, user, attendance, comment, favorite, follow) |
| 2 | `shared-storage` (existant) | jar | 100 % L / 100 % B | ✅ oui | 2 services (user, event) |
| 3 | `shared-api-error` (NEW) | jar | ≥ 95 % L / ≥ 90 % B | ✅ oui | 13 services |
| 4 | `shared-domain-enums` (NEW) | jar | ≥ 95 % L (peu de logique) | ✅ oui | 8-12 services chacun |
| 5 | `shared-domain-dtos` (NEW) | jar | ≥ 95 % L / ≥ 90 % B | ✅ oui | 10+ services cumulés |
| 6 | `shared-domain-projections` (NEW) | jar | ≥ 95 % L / ≥ 90 % B | ✅ oui | 6 services |
| 7 | `shared-jaxrs` (NEW) | jar | ≥ 95 % L / ≥ 90 % B | ✅ oui | 13 services |
| 8 | `shared-tracing` (NEW) | jar | ≥ 95 % L / ≥ 90 % B | ✅ oui | 13 services |
| 9 | `shared-kafka-events` (NEW) | jar | ≥ 95 % L (records + factories triviaux, viser 100 %) | ✅ oui | 5 services producteurs + event-service consumer |
| 10 | `shared-platform` (NEW) | jar | ≥ 95 % L / ≥ 90 % B | ✅ oui | 14 services (incl. notification-service) |

### Tableau des REST clients (récap consolidé — 16 clients)

| Service consommateur | Service amont | Endpoint(s) consommé(s) | Resilience | Endpoints internes nouveaux |
|---|---|---|---|---|
| event-service | user-service | `GET /users/{id}` | retry+timeout+CB+fallback | (aucun nouveau) |
| event-service | favorite-service | `GET /events/{id}/favorite-count` | retry+timeout+CB | nouveau (interne) |
| event-service | view-service | `GET /events/{id}/view-count` | retry+timeout+CB | nouveau (interne) |
| event-service | attendance-service | `GET /events/{id}/attendance-summary` | retry+timeout+CB | nouveau (interne) |
| event-service | co-organizer-service | `GET /events/{eventId}/co-organizers/check?userId=`, `GET /events/{eventId}/co-organizers/accepted-user-ids` | retry+timeout+CB | 2 nouveaux (internes) |
| user-service | follow-service | `GET /users/{id}/follow-counts` | retry+timeout+CB+fallback `(0,0,null)` | nouveau (interne) |
| attendance-service | event-service | `GET /events/{id}/capacity-summary` | retry+timeout+CB | nouveau (interne) |
| attendance-service | user-service | `GET /users/{id}` | retry+timeout+CB | (existant) |
| attendance-service | co-organizer-service | `GET /events/{eventId}/co-organizers/check?userId=` | retry+timeout+CB | (existant Décision B) |
| comment-service | event-service | `GET /events/{id}` | retry+timeout+CB | (existant) |
| comment-service | user-service | `GET /users/{id}` | retry+timeout+CB | (existant) |
| comment-service | co-organizer-service | `GET /events/{eventId}/co-organizers/check?userId=` | retry+timeout+CB | (existant) |
| co-organizer-service | event-service | `GET /events/{id}` | retry+timeout+CB | (existant) |
| co-organizer-service | user-service | `GET /users/{id}` | retry+timeout+CB | (existant) |
| co-organizer-service | attendance-service | `GET /events/{eventId}/attendance-summary` | retry+timeout+CB | (existant) |
| favorite-service | event-service | `GET /events/{id}` (existence) + `GET /events?ids=` (bulk) | retry+timeout+CB | bulk nouveau (interne) |
| favorite-service | user-service | `GET /users/{id}` | retry+timeout+CB | (existant) |
| favorite-service | attendance-service | `GET /events/attendance-summary?ids=` | retry+timeout+CB | bulk nouveau (interne) |
| view-service | event-service | `GET /events/{id}` (existence) | retry+timeout+CB | (existant) |
| view-service | user-service | `GET /users/by-auth0/{auth0Id}` | retry+timeout+CB | nouveau (interne) |
| report-service | event-service | `GET /events/{id}` ; commande BAN via Kafka events.banned | retry+timeout+CB | (existant) |
| report-service | user-service | `GET /users/{id}` | retry+timeout+CB | (existant) |
| report-service | co-organizer-service | `GET /events/{eventId}/co-organizers/check?userId=` | retry+timeout+CB | (existant) |
| stats-service | event-service | `GET /events/{id}` | retry+timeout+CB | (existant) |
| stats-service | user-service | `GET /users/{id}` | retry+timeout+CB | (existant) |
| stats-service | attendance-service | `GET /events/{eventId}/attendance-summary` | retry+timeout+CB | (existant) |
| stats-service | favorite-service | `GET /events/{id}/favorite-count` | retry+timeout+CB | (existant) |
| stats-service | view-service | `GET /events/{id}/view-count` | retry+timeout+CB | (existant) |
| stats-service | co-organizer-service | `GET /events/{eventId}/co-organizers/accepted-user-ids` | retry+timeout+CB | (existant) |
| share-service | event-service | `GET /events/{id}` | retry+timeout+CB | (existant) |
| calendar-service | user-service | `GET /users/by-calendar-token/{token}` | retry+timeout (sans fallback) | nouveau (interne) |
| calendar-service | event-service | `GET /events?ids=&status=PUBLISHED` (bulk) | retry+timeout+CB | bulk nouveau (interne) |
| calendar-service | favorite-service | `GET /users/{id}/favorites` | retry+timeout+CB | (existant) |
| calendar-service | attendance-service | `GET /users/{id}/attendances?status=ATTENDING` | retry+timeout+CB | (existant) |
| follow-service | user-service | `GET /users/{id}` | retry+timeout+CB | (existant) |
| me-aggregator-service | event-service | `GET /events?creatorId=` | retry+timeout+CB | (existant) |
| me-aggregator-service | user-service | `GET /users/by-auth0/{auth0Id}` | retry+timeout+CB | (existant Décision B) |

### Tableau Kafka final (10 topics × producteur(s) × consommateur(s))

| Topic | Producteur(s) | Consommateur(s) | Partition key | Status post-completion |
|---|---|---|---|---|
| `events.published` | event-service | (notification SCRUM-99 futur, stats projections futurs) | `eventId` | ✅ producer livré PR #158 |
| `events.cancelled` | event-service | (notification futur) | `eventId` | ✅ producer livré PR #158 |
| `events.expired` | event-service (cron) | (notification futur) | `eventId` | ✅ producer livré PR #158 |
| `events.banned` | **report-service** | **event-service** (apply state) + (notification futur) | `eventId` | ⚠ à livrer Étape 4.4 |
| `users.followed` | **follow-service** | (notification futur) | `followedId` | ⚠ à livrer Étape 4.1 |
| `users.follow-requested` | **follow-service** | (notification futur) | `followedId` | ⚠ à livrer Étape 4.1 |
| `users.follow-accepted` | **follow-service** | (notification futur) | `followedId` | ⚠ à livrer Étape 4.1 |
| `comments.created` | **comment-service** | (notification futur) | `eventId` | ⚠ à livrer Étape 4.2 |
| `co-organizers.invited` | **co-organizer-service** | (notification futur) | `userId` | ⚠ à livrer Étape 4.3 |
| `co-organizers.accepted` | **co-organizer-service** | (notification futur, event-service cache futur) | `eventId` | ⚠ à livrer Étape 4.3 |

**Producteurs livrés post-completion : 5 services × 9 topics au total (event-service en a déjà 3).**
**Consommateur livré post-completion : 1 (event-service ← events.banned).**
**Consommateurs futurs : tous via notification-service à SCRUM-99.**

### Tableau couverture cible jacoco par module post-completion

| Module | Lines % cible | Branches % cible | Sentinels obligatoires |
|---|---|---|---|
| `shared-rate-limit` | ≥ 100 % (existant) | ≥ 100 % | — (lib pure) |
| `shared-storage` | ≥ 100 % (existant) | ≥ 100 % | — |
| `shared-api-error` | ≥ 95 % | ≥ 90 % | — |
| `shared-domain-enums` | ≥ 95 % (peu de logique) | n/a | — |
| `shared-domain-dtos` | ≥ 95 % | ≥ 90 % | — |
| `shared-domain-projections` | ≥ 95 % | ≥ 90 % | — |
| `shared-jaxrs` | ≥ 95 % | ≥ 90 % | — |
| `shared-tracing` | ≥ 95 % | ≥ 90 % | — |
| `shared-kafka-events` | ≥ 100 % (records purs) | ≥ 100 % | — |
| `shared-platform` | ≥ 95 % | ≥ 90 % | — |
| `event-service` | ≥ 80 % | ≥ 70 % | 21 sentinels SCRUM-147 |
| `user-service` | ≥ 80 % | ≥ 70 % | (port legacy) |
| `attendance-service` | ≥ 80 % | ≥ 70 % | (port legacy) |
| `favorite-service` | ≥ 80 % | ≥ 70 % | (port legacy) |
| `view-service` | ≥ 80 % | ≥ 70 % | (port legacy) |
| `co-organizer-service` | ≥ 80 % | ≥ 70 % | (port legacy) |
| `comment-service` | ≥ 80 % | ≥ 70 % | 8 sentinels SCRUM-144 |
| `follow-service` | ≥ 80 % | ≥ 70 % | 6 sentinels SCRUM-138 |
| `report-service` | ≥ 80 % | ≥ 70 % | (port legacy) |
| `stats-service` | ≥ 80 % | ≥ 70 % | (port legacy) |
| `share-service` | ≥ 80 % | ≥ 70 % | (port legacy) |
| `calendar-service` | ≥ 80 % | ≥ 70 % | (port legacy) |
| `me-aggregator-service` | ≥ 80 % | ≥ 70 % | (port legacy) |
| `notification-service` | n/a (sentinel only) | n/a | — |

**Total sentinels obligatoires** : 21 + 8 + 6 = **35 sentinels SCRUM-138/139/144/147** (cf. Décision E).

---

(continuer à la suite dans le bloc 3 : Plan d'implémentation Étapes 0-7)
## Plan d'implémentation par étape (ORDRE STRICT)

### Étape 0 — Pré-flight

**Objectif** : valider l'état initial avant de commencer.

**Actions** :
1. `git fetch origin --quiet`
2. `git checkout 'refactor(backend)--migrate-to-microservices'` (si pas déjà dessus)
3. `git pull origin 'refactor(backend)--migrate-to-microservices' --ff-only`
4. `git rev-parse HEAD` → noter le SHA actuel (devrait être `bee933d` ou descendant — ex. `3abcab8` si l'audit a été pushé entre temps).
5. Lire en entier :
   - `specs_archives/audit_pr158_microservices_migration.md` (2143 lignes)
   - `specs_archives/specs_claude/specs_microservices_migration.md` (1884 lignes)
6. `cd backend && ./mvnw verify -DskipITs` → doit être SUCCESS (baseline). Sinon il y a un problème pré-existant, à investiguer **avant** de commencer.
7. `gh pr checks 158` → tous verts (baseline).
8. `git diff --stat origin/main HEAD -- frontend/ openapi/` → doit retourner 0 lignes (invariants tenus).

**Pas de commit pour Étape 0** — c'est de la lecture / vérification.

**Si un check échoue** : reporter le problème dans un commit `chore(backend): pre-flight blocker — <description>` ou aborter et revenir vers l'utilisateur (cas exceptionnel — la spec assume que le baseline `bee933d`+ est vert).

### Étape 1 — Bug fixes critiques (Groupe A bugs)

**Objectif** : corriger les bugs runtime + fixes d'hygiène simples qui n'attendent rien d'autre.

#### Étape 1.1 — `EventService.cancel` rejette EXPIRED (BUG-003)

**Fichier** : `backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java` ligne ~342-365.

**Patch** :
```java
// Existant :
if (event.status == EventStatus.CANCELLED) {
    throw conflict("Event is already cancelled");
}
if (event.status == EventStatus.BANNED) {
    throw conflict("Banned events cannot be cancelled by their creator.");
}
// Ajouter :
if (event.status == EventStatus.EXPIRED) {
    throw conflict("Expired events cannot be cancelled.");
}
```

**Test** : ajouter à `EventServiceCoverageTest` (ou créer `EventServiceCancelTest` si la classe coverage n'est pas encore portée — Étape 7) un test sentinel `cancel_expiredEvent_returns409_conflict`.

**Validation** : `cd backend && ./mvnw -pl services/event-service test -DskipITs`
**Commit** : `fix(backend): EventService.cancel rejects EXPIRED status (BUG-003)`

#### Étape 1.2 — `quarkus.flyway.enabled=false` retiré des 13 services (HYGIENE-001)

**Fichiers** : les 13 `backend/services/<svc>-service/src/main/resources/application.properties`.

**Patch** : retirer la ligne `quarkus.flyway.enabled=false` (et le commentaire qui l'introduit s'il existe). Préserver le commentaire « `<svc>-service` n'a pas de schéma propre » qui reste documentaire et vrai (cf. Décision C).

**Validation** :
- `grep -rn 'quarkus.flyway.enabled' backend/services/*/src/main/resources/application.properties` → 0 hit
- `cd backend && ./mvnw verify -DskipITs` → log `Unrecognized configuration key "quarkus.flyway.enabled"` n'apparaît plus

**Commit** : `chore(backend): drop unused quarkus.flyway.enabled key (HYGIENE-001)`

#### Étape 1.3 — Defaults OIDC bidons retirés (SEC-004)

**Fichiers** : les 13 `backend/services/<svc>-service/src/main/resources/application.properties`.

**Patch** :
```properties
# AVANT :
quarkus.oidc.auth-server-url=${OIDC_AUTH_SERVER_URL:https://your-auth-server.com/}
quarkus.oidc.client-id=${OIDC_CLIENT_ID:your-client-id}
quarkus.oidc.credentials.secret=${OIDC_CLIENT_SECRET:your-client-secret}
quarkus.oidc.token.audience=${OIDC_AUDIENCE:https://your-api-audience}
quarkus.oidc.roles.role-claim-path="${OIDC_ROLE_NAMESPACE:https://unige-events/roles}"

# APRÈS :
quarkus.oidc.auth-server-url=${OIDC_AUTH_SERVER_URL}
quarkus.oidc.client-id=${OIDC_CLIENT_ID}
quarkus.oidc.credentials.secret=${OIDC_CLIENT_SECRET}
quarkus.oidc.token.audience=${OIDC_AUDIENCE}
quarkus.oidc.roles.role-claim-path="${OIDC_ROLE_NAMESPACE}"
```

L'absence de `${VAR:default}` cause un fail-fast au boot si la variable d'env n'est pas posée — Helm chart + Doppler sont la garantie que les vars sont posées en preview/prod. En `%test`, l'OIDC est désactivé via `%test.quarkus.oidc.enabled=false`, donc les vars ne sont pas requises.

**Validation** : `./mvnw verify -DskipITs` (les sentinel tests doivent passer car `%test.oidc.enabled=false`).
**Commit** : `fix(backend): drop OIDC default placeholders to fail-fast on missing env vars (SEC-004)`

#### Étape 1.4 — `RedirectResource` defaultValue retiré (BUG-012)

**Fichier** : `backend/services/share-service/src/main/java/ch/unige/events/share/resource/RedirectResource.java` ligne ~25.

**Patch** :
```java
// AVANT :
@ConfigProperty(name = "app.frontend.url", defaultValue = "https://10.25.10.136.nip.io")
String frontendUrl;
// APRÈS :
@ConfigProperty(name = "app.frontend.url")
String frontendUrl;
```

Helm `values.yaml` (et `values-preview.yaml`) doivent avoir `app.frontend.url` posé — vérifier dans le chart (`grep -rn 'frontend.url' k8s/chart/`). Sinon, **ajouter** la valeur (modification YAML mineure du chart, dans le périmètre backend YAML).

**Validation** : `./mvnw -pl services/share-service test`
**Commit** : `fix(backend): RedirectResource fail-fast on missing frontend URL (BUG-012)`

#### Étape 1.5 — `UserService.updateMyProfile` guard null (BUG-010)

**Fichier** : `backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java` ligne ~100-105.

**Patch** :
```java
// AVANT :
public User updateMyProfile(String authenticatedAuth0Id, String targetAuth0Id, UpdateProfileRequest req) {
    if (!Objects.equals(authenticatedAuth0Id, targetAuth0Id)) {
        throw new ForbiddenException("Cannot update another user's profile");
    }
    return updateMyProfile(authenticatedAuth0Id, req);
}
// APRÈS :
public User updateMyProfile(String authenticatedAuth0Id, String targetAuth0Id, UpdateProfileRequest req) {
    if (authenticatedAuth0Id == null || targetAuth0Id == null
            || !authenticatedAuth0Id.equals(targetAuth0Id)) {
        throw new ForbiddenException("Cannot update another user's profile");
    }
    return updateMyProfile(authenticatedAuth0Id, req);
}
```

**Validation** : `./mvnw -pl services/user-service test`
**Commit** : `fix(backend): UserService.updateMyProfile rejects null caller/target (BUG-010)`

#### Étape 1.6 — `UserService.getPublicProfile` `@Transactional` (BUG-009)

**Fichier** : `backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java` ligne ~72.

**Patch** : ajouter `@Transactional` sur la méthode (existe peut-être déjà sur la classe — confirmer).
**Commit** : `fix(backend): UserService.getPublicProfile is @Transactional (BUG-009)`

#### Étape 1.7 — `EventRequestBase.startDate` TZ normalization (BUG-014)

**Fichier** : `backend/services/event-service/src/main/java/ch/unige/events/event/dto/EventRequestBase.java`.

**Patch** : ajouter un commentaire JavaDoc explicite + un helper :
```java
/**
 * The {@code startDate} and {@code endDate} fields use the JVM default timezone
 * for {@code @Future} validation. EventSearchService converts to Europe/Zurich
 * → UTC for time-range filtering. To avoid drift on a UTC container, the JVM
 * timezone is pinned to Europe/Zurich via {@code application.properties}
 * (`quarkus.log.console.format` etc. — already in legacy convention).
 *
 * <p>If you bump the container TZ, also update the search service.
 */
public class EventRequestBase {
```

Si la TZ JVM n'est pas pinned dans `application.properties` (vérifier), l'ajouter :
```properties
%dev,prod.quarkus.native.user-language=fr
%dev,prod.quarkus.native.user-country=CH
quarkus.timezone=Europe/Zurich  # if Quarkus supports this property — sinon JVM arg
```

(En pratique Quarkus utilise le TZ du container ; le pin est plutôt côté Helm `env: TZ=Europe/Zurich`. Acter dans la spec si Helm chart est déjà OK.)

**Commit** : `fix(backend): document and pin TZ for EventRequestBase startDate validation (BUG-014)`

#### Étape 1.8 — `share-service/pom.xml` format normalisé (HYGIENE-002)

**Fichier** : `backend/services/share-service/pom.xml`.

**Patch** : convertir le format multi-lignes en format compact (1 ligne par dependency) — comme les 12 autres POMs.

**Commit** : `chore(backend): normalize share-service pom.xml format (HYGIENE-002)`

#### Étape 1.9 — TODO obsolète retiré (HYGIENE-004) — différé Étape 4.4

Note : cette sous-étape est **différée** à l'Étape 4.4 (livraison conjointe producteur + consommateur Kafka events.banned). Le TODO obsolète sera supprimé **dans le même commit** que l'ajout du producteur.

#### Récap fin Étape 1

**Sous-étapes commitées** : 1.1, 1.2, 1.3, 1.4, 1.5, 1.6, 1.7, 1.8 (8 commits)
**Findings adressés** : BUG-003, BUG-009, BUG-010, BUG-012, BUG-014, HYGIENE-001, HYGIENE-002, SEC-004
**Validation finale** :
```bash
cd backend && ./mvnw verify -DskipITs   # SUCCESS sur 16 modules
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch                 # tous verts
```

### Étape 2 — Documentation alignment (Groupe A docs)

**Objectif** : aligner la documentation avec la réalité du code AVANT de toucher au code structurant. Ainsi le reviewer + les agents IA suivants disposent d'une doc fiable comme baseline. Cette étape n'introduit **aucun changement de code Java**.

#### Étape 2.1 — sprint-context.md : substituer 11 placeholders `<this PR>` (DOC-013)

**Fichier** : `backend/docs/sprint-context.md`.

**Patch** : remplacer les 11 occurrences de `<this PR>` par les vrais SHAs (à retrouver via `git log --oneline | grep "extract"`) :

| Ligne | Remplacer | Par |
|---|---|---|
| ~64 | `PR 3 — favorite-service extrait (commit `<this PR>`)` | `(commit `8eeaba3`)` |
| ~74 | `PR 4 — calendar-service extrait (commit `<this PR>`)` | `(commit `df19461`)` |
| ~86 | `PR 5 — follow-service` | `(commit `39d0e56`)` |
| ~101 | `PR 6 — comment-service` | `(commit `6a44257`)` |
| ~142 | `PR 9 — report-service` | `(commit `b064170`)` |
| ~164 | `PR 11 — me-aggregator-service` | `(commit `ba3cfa5`)` |
| ~176 | `PR 12 — user-service` | `(commit `166b1dd`)` |
| ~194 | `PR 13 — event-service` | `(commit `f360aff`)` |
| ~236 | « Image upload migration » | `(commit `41074e9`)` |
| ~267 | « Step 15 — Legacy-monolith removal » | `(commit `b570c1b`)` |
| ~298 | « Étape 16 partielle — Documentation finale » | `(commits `912a0e3` + `454cfb3`)` |

**Validation** : `grep -c '<this PR>' backend/docs/sprint-context.md` → 0
**Commit** : `docs(backend): substitute 11 <this PR> placeholders with real SHAs in sprint-context.md (DOC-013)`

#### Étape 2.2 — sprint-context.md : tableau « Écarts vs spec » réécrit (DOC-014)

**Fichier** : `backend/docs/sprint-context.md`, lignes ~421-442.

**Patch** : remplacer le tableau figé sur l'état Étape 1 par un tableau qui reflète l'état **post-completion** (à anticiper, ce sera vrai à la fin de la complétion). Les colonnes : `Critère de done (spec) / État / Commentaire`. Les lignes deviennent :
- 14 microservices : ✅ tous extraits (b570c1b legacy supprimé) + 2 shared libs livrées Sprint 8
- Helm chart 14 sous-templates + Kong + Kafka : ✅ + Chart.yaml v0.2.0
- Kong DB-less + table de routes : ✅ + `rate-limiting policy:local` ajouté en complétion (cf. Étape 10)
- 10 topics Kafka : ✅ provisionnés ; producteurs **9/10 livrés en complétion** (cf. Étape 4) ; consommateur events.banned **livré en complétion** (Étape 4.4)
- OIDC sur chaque service : ✅
- Migrations Flyway par service : ❌ → **différé S9+ formellement** (cf. Décision C de la spec de complétion)
- Schedulers replicas:1 : ✅
- Cascade SCRUM-136 + anti-oracle ISSUE-92 via REST : ✅ centralisés en complétion (cf. Étape 5 + Décision L)
- CI matrix par service + Sonar projectKey : ⚠ YAML produit en complétion ; activation côté DevOps
- Tests unit + integration + Pact + 1 E2E : ✅ portés en complétion (cf. Étape 7-8)
- `./mvnw verify` à la racine vert : ✅
- Documentation finale mise à jour : ✅ en complétion (Étape 13)
- PR ouverte titre : ⚠ workaround `chore(backend):` (cf. Bug subtil documenté), inchangé
- PR non mergée : ✅
- `git diff --stat openapi/` strictement vide : ⚠ déviation actée (suppression doublon `/events/{id}/view`, cf. Décision Q)
- `git diff --stat frontend/` strictement vide : ✅

**Commit** : `docs(backend): refresh "Écarts vs spec" table to post-completion state (DOC-014)`

#### Étape 2.3 — sprint-context.md : Étape 15 / 16 deduplication (DOC-015)

**Fichier** : `backend/docs/sprint-context.md`, lignes ~404-414.

**Patch** : supprimer le second bloc « Étape 15 — Suppression legacy-monolith (DEFERRED) / Étape 16 — Documentation finale (PARTIELLE livrée + reste DEFERRED) ». Ces deux étapes sont déjà décrites comme livrées plus haut dans la doc.

**Commit** : `docs(backend): drop duplicate "DEFERRED" block for Étapes 15-16 already shipped (DOC-015)`

#### Étape 2.4 — sprint-context.md : header date + état général (DOC-016)

**Patch** :
- Ligne 7 : « Sprint 8 — Migration vers microservices (étapes 0 + 1 livrées) — 2026-05-08 / En cours. » → « Sprint 8 — Migration vers microservices (étapes 0 → 18 livrées) — 2026-05-09 / En complétion. »
- Ligne 3 : « Dernière mise à jour : 2026-05-09 » (déjà OK, vérifier).

**Commit** : `docs(backend): update sprint-context.md header date + delivery state (DOC-016)`

#### Étape 2.5 — backend/AGENTS.md complètement réécrit (DOC-020, DOC-021)

**Fichier** : `backend/AGENTS.md`.

**Patch** : réécriture intégrale. Sections cibles :
1. **Rôle** : Backend REST API. Java 21, Quarkus 3.35.1, Hibernate Panache, PostgreSQL 16, Auth0/OIDC.
2. **Architecture multi-module microservices** : pointer vers `backend/docs/architecture.md` pour la table des 16 modules. Mentionner explicitement :
   - 14 microservices Quarkus actifs sous `backend/services/<svc>-service/` (tous packagés `quarkus`).
   - 1 placeholder `notification-service` (replicas:0, scaffold SCRUM-99).
   - 2+ shared libs Sprint 8 (`shared-rate-limit`, `shared-storage`).
   - 8 nouvelles shared libs en complétion (cf. spec de complétion Décision D).
3. **Commandes** :
   ```bash
   cd backend && ./mvnw verify              # build + tests complets (CI), 16+ modules
   cd backend/services/<svc>-service && ../../mvnw quarkus:dev   # dev local par service
   ```
   Plus de mention de `legacy-monolith`. Plus de mention de placeholders pom-packagés.
4. **Conventions camelCase / booléens / Flyway / soft-delete** : inchangé.
5. **Architecture en couches Resource → Service → Entity** : inchangé, mais préciser que les calls cross-service passent par REST clients (`@RegisterRestClient`).
6. **Contrat API** : openapi-first inchangé, ajout d'une note : « les endpoints internes service-to-service (cf. spec complétion Décision Q) ne sont pas dans openapi.yaml — ils sont documentés dans `backend/docs/internal-endpoints.md` ».
7. **Comportement attendu des endpoints** : inchangé.
8. **Workflow Git** : inchangé.

**Commit** : `docs(backend): rewrite backend/AGENTS.md for post-migration topology (DOC-020, DOC-021)`

#### Étape 2.6 — architecture.md : sections fossiles supprimées + topologie post-completion (DOC-001..006)

**Fichier** : `backend/docs/architecture.md`.

**Patch** :
- Ligne ~22 et ~102 : aligner « 13 microservices » avec « 16 modules Maven (13 services + notification-service scaffold + 2 shared libs Sprint 8) — futurs +8 shared libs en complétion = 24 modules à terme ».
- Lignes 153-156 + 251-263 : retirer les sections « flux PUT /api/users/me » et « Infrastructure Kubernetes » qui décrivent le monolithe — remplacer par une section « Flux d'une requête typique » qui décrit Kong → service Quarkus → REST clients vers services voisins.
- Lignes 277-282 : `ModerationCleanupJob` documenté comme `BANNED` (pas `CANCELLED`).
- Lignes 291-299 : `helm upgrade --set image.api.tag=$SHA` ; mention `quarkus-container-image-jib`. **NB** : si la rename `image.api.tag → image.tag` est livrée en Étape 12, mettre à jour ici aussi.
- Lignes 86-89 : « Rate limiting » — décrire la **complétion** : `services/shared-rate-limit/` lib + Kong rate-limiting plugin par-route (cf. Étape 10).

**Commit** : `docs(backend): rewrite architecture.md for post-completion topology (DOC-001..006)`

#### Étape 2.7 — data-model.md : ownership + Kafka producer note (DOC-007, DOC-008)

**Fichier** : `backend/docs/data-model.md`.

**Patch** :
- Section `### User` : ajouter ligne « Owned by **user-service**. Tables : `users` + `user_interests`. »
- Section `### Event` : ajouter note « Kafka : `EventLifecyclePublisher` émet `events.{published,cancelled,expired}` ; consumer `events.banned` dans event-service. »

**Commit** : `docs(backend): add User ownership + Event Kafka producer note in data-model.md (DOC-007, DOC-008)`

#### Étape 2.8 — api-contract.md : rate-limit + Service amont (DOC-009, DOC-010)

**Fichier** : `backend/docs/api-contract.md`.

**Patch** :
- Lignes ~63, ~97, ~100 : remplacer « (rate-limit DEFERRED) » par les valeurs réelles `@PerUserRateLimit`. Étendre à toutes les 13 routes mutating qui portent une annotation `@PerUserRateLimit` (cf. `grep -rn '@PerUserRateLimit' backend/services -l`).
- Lignes 112-116 : remplacer la « Rate limit notice » obsolète par une description de l'état post-completion (lib `shared-rate-limit` + plugin Kong `rate-limiting policy:local` ajouté en complétion).

**Commit** : `docs(backend): refresh api-contract.md rate-limit notes (DOC-009, DOC-010)`

#### Étape 2.9 — dev-guide.md : layout 16 modules + Hibernate validate (DOC-011, DOC-012)

**Fichier** : `backend/docs/dev-guide.md`.

**Patch** :
- Section « Layout Maven » : « 13 microservices Quarkus + 1 placeholder + 2 shared libs Sprint 8 + 8 shared libs en complétion = 24 modules à terme. »
- Section « Workflow modifier le schéma » : remplacer « Hibernate en mode `update` » par « Hibernate en `validate` / Flyway pilote le schéma. »

**Commit** : `docs(backend): align dev-guide.md with multi-module + Flyway-first reality (DOC-011, DOC-012)`

#### Étape 2.10 — microservices-migration-roadmap.md : status par PR (DOC-017, DOC-018)

**Fichier** : `backend/docs/microservices-migration-roadmap.md`.

**Patch** :
- Bannière en tête : « **Note** : ce doc a été rédigé avant les extractions ; les chemins `services/legacy-monolith/...` sont historiques. Pour l'état post-migration cf. `architecture.md` ; pour l'état post-completion cf. `specs_archives/specs_claude/specs_microservices_migration_completion.md`. »
- Tableau « Ordre des PR d'extraction » : ajouter colonne « Status » avec ✅ + SHA pour PR 1..14, ⚠ partiel pour PR 15, ❌ deferred pour PR 16.
- Section « PR 17 — Étape 18 consolidation post-migration » : ajouter (commits `446ea3e`..`bee933d`).
- Section « PR 18 — Complétion » : ajouter (commits livrés par cette spec).

**Commit** : `docs(backend): mark migration-roadmap PR statuses + add completion section (DOC-017, DOC-018)`

#### Étape 2.11 — AGENTS.md racine : compteurs services + shared libs (DOC-019)

**Fichier** : `AGENTS.md` (racine).

**Patch** : remplacer ligne 12 « 13 microservices Quarkus livrés au Sprint 8 » par « 13 microservices Quarkus livrés au Sprint 8 + 1 scaffold notification-service (replicas:0, follow-up SCRUM-99) + 2 shared libs (shared-rate-limit, shared-storage) — 8 shared libs supplémentaires en complétion (cf. specs_microservices_migration_completion.md). »

**Commit** : `docs(root): update AGENTS.md service counts (DOC-019)`

#### Étape 2.12 — PR body de #158 mis à jour (DOC-022, DOC-023, DOC-024)

**Action** :
```bash
gh pr view 158 --json body --jq .body > /tmp/pr-body-158.md
# édit /tmp/pr-body-158.md :
# - "What's NOT in this PR" — retirer les 3 items déjà livrés (rate-limit, image consolidation, 3 producers Kafka).
# - Ajouter mention "Étape 18 (post-migration consolidation)" + SHAs.
# - Ajouter mention de la complétion en cours (référencer specs_microservices_migration_completion.md).
# - "Sonar exclusion" : préciser que les 2 shared libs sont hors glob et 100 % couvertes.
gh pr edit 158 --body-file /tmp/pr-body-158.md
```

Ce n'est pas un commit Git mais c'est documenté dans `sprint-context.md` Étape 19.

#### Récap fin Étape 2

**Sous-étapes commitées** : 2.1 à 2.11 (11 commits) + 2.12 = update PR body (gh action, pas commit).
**Findings adressés** : DOC-001..024.
**Validation finale** :
```bash
cd backend && ./mvnw verify -DskipITs   # SUCCESS (aucun changement de code)
git diff --stat origin/main HEAD -- backend/services/   # devrait montrer juste les .properties (Étape 1)
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch
```

### Étape 3 — Création des 8 shared libs

**Objectif** : créer les 8 nouvelles libs partagées (Décision D) qui servent de fondation aux Étapes 4-9. Chaque lib suit le pattern de `shared-rate-limit` / `shared-storage` (jar, jandex, jacoco direct, hors glob exclusion Sonar).

#### Étape 3.0 — Mise en place dependencyManagement parent (KAFKA-009)

**Fichier** : `backend/pom.xml`.

**Patch** : ajouter dans `<dependencyManagement>` :
```xml
<dependency>
    <groupId>io.smallrye.reactive</groupId>
    <artifactId>smallrye-reactive-messaging-in-memory</artifactId>
    <version>${smallrye-reactive-messaging.version}</version>
    <scope>test</scope>
</dependency>
```

(La version est gérée par Quarkus BOM ; vérifier au moment du livrage.)

Retirer la dépendance versionnée du POM event-service (en Étape 4.0) — elle s'en hérite désormais.

**Commit** : `chore(backend): add smallrye-reactive-messaging-in-memory in dependencyManagement parent (KAFKA-009)`

#### Étape 3.1 — Lib `shared-api-error`

**Path** : `backend/services/shared-api-error/`
**Sources** :
- `src/main/java/ch/unige/events/shared/error/ApiErrorResponse.java` — record `{String error, String message}`.
- `src/main/java/ch/unige/events/shared/error/ApiErrors.java` — factory helpers statiques :
  - `WebApplicationException badRequest(String error, String message)`
  - `WebApplicationException conflict(String error, String message)`
  - `WebApplicationException unprocessable(String error, String message)`
  - `WebApplicationException forbidden(String error, String message)`
  - `WebApplicationException notFound(String error, String message)`
  - Chaque méthode construit `Response.status(...).type(MediaType.APPLICATION_JSON).entity(new ApiErrorResponse(error, message)).build()` puis wrap en `WebApplicationException`.
- `src/main/java/ch/unige/events/shared/error/WebApplicationExceptionMapper.java` — mapper générique optionnel (peut rester local par service).

**POM** : modèle copy de `shared-rate-limit/pom.xml` :
- packaging `jar`
- deps : `quarkus-arc` provided, `quarkus-rest` provided, `quarkus-rest-jackson` provided
- jandex-maven-plugin 3.2.2
- jacoco-maven-plugin direct (cf. modèle existant)

**Tests** : `src/test/java/.../ApiErrorsTest.java` (5+ cas, 1 par helper) + `src/test/java/.../ApiErrorResponseTest.java` (record sanity).

**Couverture cible** : ≥ 95 % L / ≥ 90 % B.

**Migration** : ne pas (encore) supprimer les `ApiErrorResponse` / factory helpers locaux des 7 services. Cette suppression sera faite en Étape 5.0 quand chaque service consommateur déclarera la dépendance.

**Validation** : `cd backend && ./mvnw -pl services/shared-api-error -am verify -DskipITs`
**Commit** : `feat(backend): create shared-api-error lib (REFACTOR-002, REFACTOR-011)`

#### Étape 3.2 — Lib `shared-domain-enums`

**Path** : `backend/services/shared-domain-enums/`
**Sources** : 9 enums sous `ch.unige.events.shared.domain.enums.*` :
- `EventStatus` (DRAFT, PUBLISHED, CANCELLED, EXPIRED, BANNED)
- `EventCategory` (cf. legacy)
- `Faculty` (cf. legacy)
- `AttendanceStatus` (ATTENDING, WAITLISTED, INTERESTED, etc. — vérifier)
- `CoOrganizerStatus` (PENDING, ACCEPTED, DECLINED — DECLINED jamais persisté)
- `FollowStatus` (PENDING, ACCEPTED)
- `RecurrenceFrequency` (WEEKLY, BIWEEKLY, MONTHLY)
- `ReportReason` (cf. legacy)
- `ReportStatus` (cf. legacy)

**Tests** : `EnumValuesSentinelTest.java` qui assert `.values().length` pour chaque enum (sentinel anti-régression).
**Couverture cible** : ≥ 95 % L (les enums n'ont pas de branches business).

**Migration** : pas de suppression locale immédiate. En Étape 3.X (par enum), les copies locales sont supprimées des services et remplacées par l'import `shared-domain-enums`. Risque CRITICAL : `@Enumerated(STRING)` doit re-mapper exactement les noms, donc tout enum dupliqué doit être strictement identique en valeurs.

**Validation** : `./mvnw -pl services/shared-domain-enums -am verify -DskipITs`
**Commit** : `feat(backend): create shared-domain-enums lib (REFACTOR-003)`

#### Étape 3.3 — Lib `shared-jaxrs`

**Path** : `backend/services/shared-jaxrs/`
**Sources** :
- `src/main/java/ch/unige/events/shared/jaxrs/EnumParamConverter.java` — base abstraite réutilisable.
- `src/main/java/ch/unige/events/shared/jaxrs/Timeframe.java` — l'enum (PAST, UPCOMING, ALL — cf. legacy `MyAttendancesResource.parseTimeframe`).
- `src/main/java/ch/unige/events/shared/jaxrs/TimeframeParamConverter.java` + `Provider` — ParamConverter Jakarta.
- `src/main/java/ch/unige/events/shared/jaxrs/JsonWebTokenLazy.java` — helper pour injecter `JsonWebToken` lazily (`Instance<JsonWebToken>` qui retourne null si OIDC désactivé en `%test`).

**Tests** : couvrir les ParamConverters + le lazy JWT.
**Migration** : `MyAttendancesResource.parseTimeframe` retiré, remplacé par binding JAX-RS natif via `@QueryParam("timeframe") Timeframe timeframe`. Couvert en Étape 5 quand attendance-service est touché.

**Commit** : `feat(backend): create shared-jaxrs lib with enum ParamConverters (BUG-011, REFACTOR-018)`

#### Étape 3.4 — Lib `shared-tracing`

**Path** : `backend/services/shared-tracing/`
**Sources** :
- `src/main/java/ch/unige/events/shared/tracing/RequestIdFilter.java` — `@Provider` `ContainerRequestFilter` qui lit le header `X-Request-ID` (ou en génère un UUID si absent) et le pose dans MDC. À implémenter avec priority `Priorities.AUTHENTICATION - 1` pour qu'il s'exécute tôt.
- `src/main/java/ch/unige/events/shared/tracing/RequestIdClientFilter.java` — `ClientRequestFilter` pour les REST clients sortants : lit le MDC et propage le `X-Request-ID` au header sortant.
- `src/main/java/ch/unige/events/shared/tracing/MdcKafkaInterceptor.java` (optionnel) — propage le MDC dans les threads des emitters Kafka.

**Tests** : assert que MDC est posé après filter ; assert que header est propagé au client.
**Couverture cible** : ≥ 95 % L / ≥ 90 % B.

**Migration** : la consommation par chaque service est faite en Étape 9 (observabilité).

**Commit** : `feat(backend): create shared-tracing lib with RequestIdFilter (SPEC-013)`

#### Étape 3.5 — Lib `shared-kafka-events`

**Path** : `backend/services/shared-kafka-events/`
**Sources** :
- Migrer `EventLifecycleEvent` depuis `event-service/src/main/java/ch/unige/events/event/kafka/EventLifecycleEvent.java` (déjà existant) vers `services/shared-kafka-events/src/main/java/ch/unige/events/shared/kafka/events/EventLifecycleEvent.java`.
- Créer les 4 nouveaux records :
  - `EventBannedEvent` (record `{long eventId, UUID bannedBy, String reason, Instant bannedAt}`)
  - `FollowLifecycleEvent` (record discriminé, Type {FOLLOWED, REQUESTED, ACCEPTED}, `{Type type, UUID followerId, UUID followedId, Instant occurredAt}`)
  - `CommentCreatedEvent` (record `{long commentId, long eventId, UUID authorId, Long parentCommentId, Instant createdAt}`)
  - `CoOrganizerEvent` (record discriminé, Type {INVITED, ACCEPTED}, `{Type type, long eventId, UUID userId, Instant occurredAt}`)

**Tests** : un par record, factory methods + sentinel `valuesLength` sur les Type enums.
**Couverture cible** : ≥ 100 % L (records purs).

**Migration** : `event-service/src/main/java/ch/unige/events/event/kafka/EventLifecycleEvent.java` est supprimé ; les imports du Publisher event-service basculent sur `ch.unige.events.shared.kafka.events.EventLifecycleEvent`. Le `EventLifecycleEventTest` event-service est aussi supprimé (déplacé dans la lib).

**Commit** : `feat(backend): create shared-kafka-events lib + migrate EventLifecycleEvent (KAFKA-007)`

#### Étape 3.6 — Lib `shared-platform`

**Path** : `backend/services/shared-platform/`
**Sources** :
- `ServiceIdentityResource.java` paramétrisable : injecte `@ConfigProperty(name = "quarkus.application.name") String serviceName;` + expose `GET /__service` retournant `{"service": serviceName}`. Endpoint `@PermitAll`.

**Migration** : retirer les 14 copies de `ServiceIdentityResource.java` dans chaque service ; chacun déclare la dépendance `shared-platform`. Le test sentinel reste dans chaque service (mais référence la classe shared-platform).

**Commit** : `feat(backend): create shared-platform lib + dedup ServiceIdentityResource (REFACTOR-012)`

#### Étape 3.7 — Lib `shared-domain-dtos`

**Path** : `backend/services/shared-domain-dtos/`
**Sources** : DTOs cross-projetés sous `ch.unige.events.shared.domain.dto.*` :
- `UserPublicResponse` (record + factories `from(User, ...)`, `fromAnonymous(...)`).
- `EventDTO` (record, 25+ champs, factories pour les différents call-sites).
- `AttendanceDTO` (record, factories).
- `EventCoOrganizerDTO` ou similar.
- `CapacitySummary` record (pour l'endpoint interne `/events/{id}/capacity-summary`).
- `AttendanceSummary` record (count by status).
- `FollowCounts` record.

**NB** : les DTOs « privés » (`UserProfileResponse`, `CommentDTO`, `FollowDTO`, `RecurrenceRequest`, `CreateEventRequest`, `UpdateEventRequest`, etc.) restent dans le service propriétaire — ils ne sont consommés que via REST côté client (déserialization JSON, le client n'a pas besoin du record exact ; il suffit qu'il en consomme un compatible).

**Tests** : factories + sentinel anti-régression sur le nombre de champs.
**Couverture cible** : ≥ 95 % L / ≥ 90 % B.

**Migration** : les copies locales (cf. REFACTOR-006) sont supprimées en Étape 5 lorsque chaque service consommateur déclare la dépendance.

**Commit** : `feat(backend): create shared-domain-dtos lib (REFACTOR-006)`

#### Étape 3.8 — Lib `shared-domain-projections`

**Path** : `backend/services/shared-domain-projections/`
**Sources** :
- `EventCapacity.computeAvailableSpots(Integer capacity, long attendingCount)` — duplicated in 6 services.
- `Auth0IdResolver.resolveUserId(JsonWebToken jwt) → String` — pattern résolution caller.
- `AttendanceCounts` (record `{long attending, long waitlisted, long interested}`).

**Tests** : 100 % sur les helpers triviaux.
**Migration** : les 6 copies de `computeAvailableSpots` retirées en Étape 5.

**Commit** : `feat(backend): create shared-domain-projections lib (REFACTOR-005, REFACTOR-013)`

#### Étape 3.9 — Cohérence : ajout des 8 modules au parent POM + ordre

**Fichier** : `backend/pom.xml`.

**Patch** : insérer les 8 nouveaux modules **avant** les 14 microservices dans `<modules>` (Maven ordre = dépendance) :
```xml
<modules>
    <!-- Shared libs (jar packaging, hors glob exclusion Sonar) -->
    <module>services/shared-rate-limit</module>
    <module>services/shared-storage</module>
    <module>services/shared-api-error</module>           <!-- NEW -->
    <module>services/shared-domain-enums</module>        <!-- NEW -->
    <module>services/shared-jaxrs</module>               <!-- NEW -->
    <module>services/shared-tracing</module>             <!-- NEW -->
    <module>services/shared-kafka-events</module>        <!-- NEW -->
    <module>services/shared-platform</module>            <!-- NEW -->
    <module>services/shared-domain-dtos</module>         <!-- NEW -->
    <module>services/shared-domain-projections</module>  <!-- NEW -->

    <!-- Microservices Quarkus extraits -->
    <module>services/user-service</module>
    <!-- ... (14 services inchangés) -->
</modules>
```

**Validation** : `cd backend && ./mvnw verify -DskipITs` → SUCCESS sur 24 modules.

**Commit** : (déjà couvert par les 8 commits 3.1-3.8 + commit 3.0). Si Maven build échoue par incohérence d'ordre, fix dans 3.9 avec un commit dédié.

#### Récap fin Étape 3

**Sous-étapes commitées** : 3.0 + 3.1 à 3.8 (9 commits).
**Findings adressés (partiellement)** : REFACTOR-002, REFACTOR-003, REFACTOR-005, REFACTOR-006, REFACTOR-011, REFACTOR-012, REFACTOR-013, REFACTOR-018, BUG-011, KAFKA-007, KAFKA-009, SPEC-013.
- Note : la **consommation** des libs par les services se fait en Étape 5.0 (étape de bascule des consommateurs). En fin d'Étape 3, les libs sont créées mais pas encore importées par les services — le code des services reste fonctionnel comme avant.

**Validation finale Étape 3** : tous les modules compilent + tests verts ; CI verte ; PR body inchangé (les libs ne sont pas encore consommées, donc invisible côté preview env).

### Étape 4 — Kafka producteurs restants (KAFKA-001 à KAFKA-008)

**Objectif** : compléter les 7 topics Kafka restants (5 services à producer-able), livrer le consommateur `events.banned` côté event-service.

#### Étape 4.0 — Refactor `EventLifecyclePublisher` event-service vers le pattern CDI AFTER_SUCCESS (BUG-001, BUG-002)

**Fichier** : `backend/services/event-service/src/main/java/ch/unige/events/event/service/EventService.java` + nouveau fichier `backend/services/event-service/src/main/java/ch/unige/events/event/kafka/EventLifecycleKafkaBridge.java`.

**Patch** :
- Remplacer dans `EventService.publish()` ligne ~419 : `lifecyclePublisher.published(event.id, event.creator != null ? event.creator.id : null);` par `cdiEvent.fire(EventLifecycleEvent.published(event.id, event.creator != null ? event.creator.id : null));`.
- Idem dans `EventService.cancel()` ligne ~363.
- Idem dans `EventExpirationService.expireEvents()` ligne ~48 dans la boucle.
- Ajouter `@Inject jakarta.enterprise.event.Event<EventLifecycleEvent> cdiEvent;` aux deux services.
- Créer `EventLifecycleKafkaBridge.java` :
  ```java
  @ApplicationScoped
  public class EventLifecycleKafkaBridge {
      @Inject EventLifecyclePublisher publisher;
      void onAfterCommit(@Observes(during = TransactionPhase.AFTER_SUCCESS) EventLifecycleEvent ev) {
          switch (ev.type()) {
              case PUBLISHED -> publisher.published(ev.eventId(), ev.creatorId());
              case CANCELLED -> publisher.cancelled(ev.eventId(), ev.creatorId());
              case EXPIRED -> publisher.expired(ev.eventId(), ev.creatorId());
          }
      }
  }
  ```

**Tests** : adapter `EventLifecyclePublisherTest` pour rester valide (le publisher n'a pas changé). Ajouter `EventLifecycleKafkaBridgeTest` qui assert que l'observer route correctement.

**Validation** : `./mvnw -pl services/event-service test -DskipITs` (sentinel + tests Kafka existants doivent rester verts).
**Commit** : `fix(backend): EventService Kafka publish via CDI @Observes(AFTER_SUCCESS) (BUG-001, BUG-002)`

#### Étape 4.1 — follow-service : 3 producteurs `users.{followed,follow-requested,follow-accepted}` (KAFKA-003)

**Fichiers nouveaux** :
- `backend/services/follow-service/src/main/java/ch/unige/events/follow/kafka/FollowLifecyclePublisher.java`
- `backend/services/follow-service/src/main/java/ch/unige/events/follow/kafka/FollowLifecycleKafkaBridge.java`

**POM** : ajouter `quarkus-messaging-kafka` (compile) + dépendance `shared-kafka-events`.

**Wiring** :
- `FollowService.follow()` : si auto-accept (target = profilePublic) → `cdiEvent.fire(FollowLifecycleEvent.followed(...))`. Sinon → `cdiEvent.fire(FollowLifecycleEvent.followRequested(...))`.
- `FollowService.acceptRequest()` : `cdiEvent.fire(FollowLifecycleEvent.followAccepted(...))`.

**Config `application.properties`** : 3 channels outgoing + bootstrap.servers + value.serializer (cf. Décision F).

**Tests** : `FollowLifecyclePublisherTest` (3 méthodes), `FollowLifecycleKafkaBridgeTest`.

**Validation** : `./mvnw -pl services/follow-service test`
**Commit** : `feat(backend): wire follow-service Kafka producers users.{followed,follow-requested,follow-accepted} (KAFKA-003)`

#### Étape 4.2 — comment-service : producteur `comments.created` (KAFKA-004)

**Fichiers nouveaux** : `CommentCreatedPublisher.java` + `CommentCreatedKafkaBridge.java` sous `ch.unige.events.comment.kafka.*`.
**POM** : ajouter `quarkus-messaging-kafka` + `shared-kafka-events`.
**Wiring** : `CommentService.post()` → `cdiEvent.fire(CommentCreatedEvent(...))`.
**Config** : 1 channel.
**Tests** : 2 classes test.

**Commit** : `feat(backend): wire comment-service Kafka producer comments.created (KAFKA-004)`

#### Étape 4.3 — co-organizer-service : 2 producteurs `co-organizers.{invited,accepted}` (KAFKA-005)

**Fichiers nouveaux** : `CoOrganizerPublisher.java` + `CoOrganizerKafkaBridge.java`.
**POM** : ajouter Kafka + `shared-kafka-events`.
**Wiring** :
- `EventCoOrganizerService.invite()` → `cdiEvent.fire(CoOrganizerEvent.invited(...))`.
- `EventCoOrganizerService.accept()` → `cdiEvent.fire(CoOrganizerEvent.accepted(...))`.
**Config** : 2 channels.
**Tests** : 2 classes test.

**Commit** : `feat(backend): wire co-organizer-service Kafka producers co-organizers.{invited,accepted} (KAFKA-005)`

#### Étape 4.4 — report-service producer `events.banned` + event-service consumer (KAFKA-001 + KAFKA-002, livraison conjointe)

**⚠ Livraison CONJOINTE — un seul commit pour producer + consumer (atomicité métier).**

**Fichiers nouveaux côté report-service** :
- `EventBannedPublisher.java` + `EventBannedKafkaBridge.java`.

**Fichier nouveau côté event-service** :
- `EventBannedConsumer.java` (`@Incoming("events-banned")` + `@Transactional` + idempotence guard).

**POM report-service** : ajouter `quarkus-messaging-kafka` + `shared-kafka-events`.
**POM event-service** : ajouter `shared-kafka-events`. Le `quarkus-messaging-kafka` y est déjà.

**Patch metier report-service** :
- `ReportService.handle()` ligne ~122-124 : remplacer `report.event.status = EventStatus.BANNED;` par `cdiEvent.fire(EventBannedEvent.banned(eventId, bannedBy, reason));`. **Retirer la mutation cross-schema.**
- `ModerationCleanupService.runCleanup()` ligne ~69-70 : idem. **Retirer le TODO obsolète (HYGIENE-004).**

**Patch event-service** : nouveau `EventBannedConsumer` qui apply `event.status = BANNED` localement, idempotent.

**Config report-service** : 1 channel outgoing `events-banned` + bootstrap.
**Config event-service** : 1 channel incoming `events-banned` + bootstrap + value.deserializer + group.id.

**Tests** : 4 classes test (publisher + bridge côté report ; consumer + adapter côté event).

**Validation critique** : test integration qui assert que :
1. Report `decision = BAN` fire → consumer event-service lit le message → entity Event passe à BANNED.
2. Si message arrive deux fois (replay), idempotent.

**Commit** : `feat(backend): wire events.banned producer (report-service) + consumer (event-service) (KAFKA-001, KAFKA-002, HYGIENE-004)`

#### Étape 4.5 — Cohérence finale : `value.serializer` explicite (KAFKA-008)

**Fichiers** : tous les `application.properties` qui ont des `mp.messaging.outgoing.<chan>.connector=smallrye-kafka`.

**Patch** : ajouter pour chaque channel `mp.messaging.outgoing.<chan>.value.serializer=io.quarkus.kafka.client.serialization.ObjectMapperSerializer`. Idem pour les channels incoming : `value.deserializer=io.quarkus.kafka.client.serialization.ObjectMapperDeserializer` + `value.deserializer.type=<FQN>`.

**Commit** : `chore(backend): explicit value.serializer / deserializer for all Kafka channels (KAFKA-008)`

#### Récap fin Étape 4

**Sous-étapes commitées** : 4.0 à 4.5 (6 commits).
**Findings adressés** : KAFKA-001..009, BUG-001/002, SPEC-005, SPEC-022, HYGIENE-004.
**Validation finale Étape 4** : `./mvnw verify -DskipITs` SUCCESS ; CI verte ; preview env doit montrer Kafka topics non-vides après quelques actions sur la preview env (smoke test optional).

### Étape 5 — REST clients cross-service (la mega-étape)

**Objectif** : remplacer les 35 JPA stubs par REST clients (Décision B). Cette étape est la plus volumineuse — elle est subdivisée par service consommateur. Elle s'appuie sur les libs Étape 3.

#### Étape 5.0 — Bascule des libs Étape 3 vers les services (REFACTOR-002, 003, 006, 011, 012, 013, BUG-011)

**Objectif** : faire en sorte que les 13 services métiers consomment les 8 nouvelles libs. Cette sous-étape est cruciale et préparée fichier par fichier.

**Pour chaque service** (13 services) :
1. **POM** : ajouter les `<dependency>` vers `shared-api-error`, `shared-domain-enums`, `shared-domain-dtos` (si applicable), `shared-domain-projections` (si applicable), `shared-jaxrs`, `shared-platform`, `shared-tracing`. Ne pas ajouter `shared-kafka-events` ici (déjà ajouté en Étape 4 pour les services producteurs).
2. **Sources** :
   - Supprimer `ApiErrorResponse.java` local du package `dto/`.
   - Supprimer chaque enum dupliqué local.
   - Supprimer les copies de DTOs cross-projetés (`UserPublicResponse`, `EventDTO`, `AttendanceDTO`, `EventCoOrganizerDTO`, `CapacitySummary`, etc.).
   - Supprimer les helpers dupliqués (`computeAvailableSpots`, `resolveUser`, `resolveUserId`, `WebApplicationException badRequest/conflict/...`).
   - Supprimer `ServiceIdentityResource.java` local (remplacé par lib `shared-platform`).
   - Supprimer `parseTimeframe` local + `Timeframe` local.
3. **Imports** : faire un grand find-replace via `sed` ou `Edit replace_all` :
   ```bash
   # exemple pour event-service :
   grep -rn 'ch.unige.events.event.dto.ApiErrorResponse' backend/services/event-service/src
   # remplacer par
   ch.unige.events.shared.error.ApiErrorResponse
   ```
4. **Refactoriser les call-sites** des `ApiErrors.badRequest(...)` vs `private static badRequest(...)` locaux.
5. **Ajouter MdcKafkaInterceptor / RequestIdFilter** wiring (cf. Décision K + lib `shared-tracing`) — actually, cette consommation se fait en Étape 9, pas ici.

**Commit** : `refactor(backend): adopt shared libs (api-error, domain-enums, dtos, projections, jaxrs, platform) across all services (REFACTOR-002, 003, 005, 006, 011, 012, 013, 018, BUG-011)`

(Si trop gros, scinder en N commits par lib ou par service. La règle « ≤500 lignes diff » peut être assouplie ici car c'est une bascule mécanique.)

**Validation** : `./mvnw verify -DskipITs` SUCCESS sur 24 modules.

#### Étape 5.1 — REST client `event-service` côté tous les consommateurs

Cette sous-étape cible toutes les utilisations de `EventStub` à travers les services consommateurs (8 stubs au total selon la table Décision B).

**Pour chaque service consommateur d'`EventStub`** (attendance, comment, co-organizer, favorite, view, report, calendar, me-aggregator, stats — 9 services) :
1. Créer `<svc>/src/main/java/ch/unige/events/<svc>/client/EventServiceClient.java` :
   ```java
   @RegisterRestClient(configKey = "event-service")
   @Path("/events")
   public interface EventServiceClient {
       @GET @Path("/{id}")
       @Retry(maxRetries = 3, delay = 200)
       @Timeout(2000)
       @CircuitBreaker(failureRatio = 0.5, requestVolumeThreshold = 10)
       EventDTO getById(@PathParam("id") long id);
   }
   ```
   Plus d'autres méthodes selon les besoins du service consommateur (getByCreator, getCapacitySummary, etc.).
2. `application.properties` :
   ```properties
   quarkus.rest-client.event-service.url=${EVENT_SERVICE_URL:http://event-service:8080}
   ```
3. Remplacer chaque `EventStub.findByXxx(...)` ou `entityManager.find(EventStub.class, ...)` par `eventServiceClient.getXxx(...)`.
4. **Supprimer la classe `EventStub.java`** du service consommateur.
5. Adapter les tests existants (qui font `Event.persist(...)` puis `EventStub.findByXxx(...)` pour valider) → utiliser WireMock ou `@InjectMock EventServiceClient`.

**Endpoints internes nouveaux côté event-service** (cf. Décision B) :
- `GET /events/{id}/capacity-summary` (consommé par attendance-service)
- `GET /events?ids=...` (bulk, consommé par favorite-service + calendar-service)
- `GET /events/{id}/favorite-count` (proxy vers favorite-service — alternativement, exposer côté favorite-service direct)
- `GET /events/{id}/view-count` (idem, côté view-service)
- `GET /events/{id}/attendance-summary` (idem, côté attendance-service)

**NB** : par cohérence, les endpoints internes vivent côté **service propriétaire** des données — pas côté event-service en proxy. Les corrections au tableau Décision B :
- `GET /events/{id}/favorite-count` → côté favorite-service (interne)
- `GET /events/{id}/view-count` → côté view-service (interne)
- `GET /events/{id}/attendance-summary` → côté attendance-service (interne, non public)

**Commit** : 1 commit par service consommateur (5.1.attendance, 5.1.comment, 5.1.co-organizer, 5.1.favorite, 5.1.view, 5.1.report, 5.1.calendar, 5.1.me-aggregator, 5.1.stats — 9 commits) — OU regroupé en 5.1 single commit (gros mais cohérent).

Recommandation pour l'exécuteur : faire un commit par service consommateur, pour reviewability.

#### Étape 5.2 — REST client `user-service` côté tous les consommateurs (idem pour UserStub, 11 consommateurs)

Pattern identique. Endpoints à exposer côté user-service :
- `GET /users/{id}` (existant, public).
- `GET /users/by-auth0/{auth0Id}` (nouveau, **interne**).
- `GET /users/by-calendar-token/{token}` (nouveau, **interne** — calendar-service uniquement).

**Commit** : 1 par service consommateur (5.2.event, 5.2.attendance, 5.2.comment, 5.2.co-organizer, 5.2.favorite, 5.2.report, 5.2.view, 5.2.follow, 5.2.calendar, 5.2.me-aggregator, 5.2.stats — 11 commits).

#### Étape 5.3 — REST client `attendance-service` (3 consommateurs)

Endpoint nouveau côté attendance-service : `GET /events/{eventId}/attendance-summary` (interne).

**Commit** : 1 par consommateur (event, co-organizer, favorite, calendar, me-aggregator, stats — 6 commits).

#### Étape 5.4 — REST client `favorite-service` (2 consommateurs)

Endpoint nouveau : `GET /events/{id}/favorite-count` (interne).

**Commit** : 2 commits (event-service, stats-service).

#### Étape 5.5 — REST client `view-service` (2 consommateurs)

Endpoint nouveau : `GET /events/{id}/view-count` (interne).

**Commit** : 2 commits (event-service, stats-service).

#### Étape 5.6 — REST client `co-organizer-service` (5 consommateurs)

Endpoints nouveaux : `GET /events/{eventId}/co-organizers/check?userId=` + `GET /events/{eventId}/co-organizers/accepted-user-ids` (internes).

**Commit** : 1 par consommateur (event, comment, attendance, report, stats — 5 commits).

#### Étape 5.7 — REST client `follow-service` (1 consommateur user-service)

Endpoint nouveau : `GET /users/{id}/follow-counts` (interne).

**Commit** : 1 commit.

#### Étape 5.8 — Centralisation anti-oracles ISSUE-92, ISSUE-93, SCRUM-136 (Décision L)

**Action transversale** :
- Dans chaque service qui inlinait un anti-oracle (cf. REFACTOR-009, REFACTOR-010, SEC-002), supprimer le helper local + remplacer par l'appel REST client qui propage le 404 natif d'event-service / user-service ou le booléen co-organizer-service.
- Dans `EventService.getById(...)` event-service : signature inchangée (la règle vit ici).
- Dans `UserService.getPublicProfile(...)` user-service : signature étendue à `(UUID, String callerAuth0Id, boolean isAdmin)` (Décision M / SEC-001) — admin bypass ajouté.
- Dans `EventService.cancel/restore` event-service : signature étendue à `(Long, String, boolean isAdmin)` (Décision M / SEC-003) — admin bypass ajouté.

**Tests** : sentinels d'anti-oracle préservés (`getOccurrences_draftByNonCreator_returns404_antiOracle`, `getFollowers_privateProfileNonOwner_returns404_antiOracle`, `post_eventDraftByNonCreator_returns404_antiOracle`, `post_eventBanned_returns404_antiOracle`, etc.).

**Commit** : `refactor(backend): centralize anti-oracles ISSUE-92, ISSUE-93, SCRUM-136 behind REST clients (Décision L) + admin bypass on getPublicProfile / cancel / restore (SEC-001, SEC-003)`

#### Étape 5.9 — Validation finale — 0 stub JPA

```bash
find backend/services -name '*Stub.java' -not -path '*/target/*'   # → 0
```

Si non vide, lister + finaliser. Commit `refactor(backend): remove last JPA stub <name>`.

#### Récap fin Étape 5

**Sous-étapes commitées** : 5.0 + 5.1.* + 5.2.* + 5.3.* + 5.4.* + 5.5.* + 5.6.* + 5.7 + 5.8 + 5.9 (~30-40 commits selon granularité retenue).
**Findings adressés** : SPEC-002, SPEC-005 (partiel), SPEC-011, SPEC-013, SPEC-014, SPEC-021, BUG-008, BUG-011, REFACTOR-001, REFACTOR-002, REFACTOR-003, REFACTOR-004, REFACTOR-005, REFACTOR-006, REFACTOR-007 (partiel), REFACTOR-008 (partiel), REFACTOR-009, REFACTOR-010, REFACTOR-011, REFACTOR-012, REFACTOR-013, REFACTOR-016, REFACTOR-017, SEC-001, SEC-002, SEC-003.

**Validation finale Étape 5** :
- `find backend/services -name '*Stub.java' -not -path '*/target/*'` → 0 résultat
- `grep -rn '@RegisterRestClient' backend/services` → ≥ 16 interfaces livrées
- `./mvnw verify -DskipITs` SUCCESS
- CI verte
- Smoke test preview env : `curl /api/users/me` 401 ; `curl /api/events` 200 ; les flux cross-service marchent (créer event → publish → consumer voit `events.published`).

### Étape 6 — DB-per-service : **DIFFÉRÉE S9+** (Décision C)

**Action** : aucune modification de code. La spec acte le report formel et documente :
- Dans `backend/docs/devops-handoff.md` : ajouter une section « DB-per-service via schémas séparés + RBAC strict — différé S9+ ».
- Dans `sprint-context.md` : entrée Étape 19 mentionne la déviation.

**Commit** : (rien à committer en Étape 6 — la décision est actée dans la doc Étape 13).

### Étape 7 — Tests legacy portés (TEST-001 à TEST-018)

**Objectif** : porter 1818 tests legacy → atteindre couverture cible (≥ 80 % L / ≥ 70 % B par service métier). Les sentinels SCRUM-138/139/144/147 doivent ressortir verts par nom.

**Pré-requis** : Étape 5 livrée (REST clients en place, tests cross-service utilisent `@InjectMock <Client>` ou WireMock).

#### Étape 7.0 — Stratégie de mock / stub commune (TEST-018)

**Action** : créer un module helper test `backend/services/shared-test-stubs/` (ou par-service à scope `test`) qui regroupe :
- Les `*ServiceMock` réutilisables (`EventServiceMock`, `UserServiceMock`, `AttendanceServiceMock`, `FavoriteServiceMock`, `CommentServiceMock`, `EventCoOrganizerServiceMock`).
- Les helpers WireMock pour les REST clients.
- Les `@QuarkusTestProfile` (`ShareServiceCoverageProfile`, etc.) si nécessaire.

**Décision** : préférer des **`@InjectMock`** Quarkus (pour les beans CDI internes) + **WireMock** (pour les REST clients) dans chaque service, sans module shared-test-stubs (KISS) — le module shared-test-stubs ajoute du bruit pour peu de bénéfice avec REST clients.

**Donc Étape 7.0 = 0 commit. La stratégie est documentée mais pas matérialisée en module.**

#### Étape 7.1 — `RecurrenceGeneratorTest` (event-service, logique pure)

**Action** :
```bash
git show 41074e9:backend/services/legacy-monolith/src/test/java/ch/unige/events/util/RecurrenceGeneratorTest.java \
    > backend/services/event-service/src/test/java/ch/unige/events/event/util/RecurrenceGeneratorTest.java
```

Adapter le package `ch.unige.events.util.RecurrenceGeneratorTest` → `ch.unige.events.event.util.RecurrenceGeneratorTest`. Adapter les imports si la classe `RecurrenceGenerator` event-service est dans un package légèrement différent.

**Validation** : `./mvnw -pl services/event-service test` → 13 tests pass + 4 sentinels SCRUM-147 verts (`weekly_4Occurrences_returns3DatesSpacedBy7Days`, `monthly_handlesShortFebruaryFromJanuary31`, `bothNull_throwsIllegalArgumentException`, `maxOccurrencesAbove52_cappedTo52`).

**Commit** : `test(backend): port RecurrenceGeneratorTest from legacy (TEST-003, 4 SCRUM-147 sentinels)`

#### Étape 7.2 — `EventServiceCoverageTest` + `EventResourceTest` event-service (TEST-002)

**Action** :
- `git show 41074e9:backend/services/legacy-monolith/src/test/java/ch/unige/events/service/EventServiceCoverageTest.java > backend/services/event-service/src/test/java/ch/unige/events/event/service/EventServiceCoverageTest.java`
- Idem pour `EventResourceTest`, `AdminEventResourceTest`, `EventSearchResourceTest`, `EventStatsServiceCoverageTest` (si applicable à event-service vs stats-service), `EventCoOrganizerServiceCoverageTest` (si applicable).

Adapter :
- packages,
- entités cross-service (UserStub → mock REST client UserServiceClient),
- DTOs (cf. Étape 5),
- `@PerUserRateLimit` annotations (déjà restaurées).

**Sentinels SCRUM-147 (21) — vérifier verts par nom** : `weekly_4Occurrences_returns3DatesSpacedBy7Days`, `monthly_handlesShortFebruaryFromJanuary31`, ..., `getOccurrences_draftByAnonymous_returns404_antiOracle`.

**Validation** : `./mvnw -pl services/event-service test` → couverture ≥ 80 % L / ≥ 70 % B.
**Commit** : `test(backend): port EventService + EventResource + AdminEventResource tests from legacy (TEST-002, 21 SCRUM-147 sentinels)`

#### Étape 7.3 — `UserServiceCoverageTest` + `UserResourceTest` user-service (TEST-004)

Pattern identique. Inclure :
- Tests image upload S3 (utilisent `shared-storage` lib).
- Tests anti-oracle ISSUE-93 (`getPublicProfile_*`).
- Tests admin bypass post Décision M.

**Commit** : `test(backend): port UserService + UserResource tests from legacy (TEST-004)`

#### Étape 7.4 — `FollowServiceCoverageTest` + resources tests follow-service (TEST-005)

**Sentinels SCRUM-138 (6) — vérifier** : `findAcceptedFollowedIds_returnsOnlyAcceptedUuids`, `rejectRequest_followerCanReFollowAfterReject`, `follow_selfFollow_throwsUnprocessable`, `getFollowers_privateProfileNonOwner_returns404_antiOracle`, `getPublicProfile_self_followStatusIsNull`, `getPublicProfile_authNonOwnerWithPending_followStatusIsPending`.

**Commit** : `test(backend): port FollowService + resources tests from legacy (TEST-005, 6 SCRUM-138 sentinels)`

#### Étape 7.5 — `CommentServiceCoverageTest` + `CommentResourceTest` + `CommentDirectResourceTest` comment-service (TEST-006)

**Sentinels SCRUM-144 (8) — vérifier** : `prePersist_setsCreatedAt`, `post_eventDraftByNonCreator_returns404_antiOracle`, `post_eventBanned_returns404_antiOracle`, `post_replyToReply_returns422_repliesTooDeep`, `post_parentInOtherEvent_returns422_parentNotInEvent`, `post_unknownParent_returns404_parentNotFound`, `delete_byPendingCoOrganizer_returns403`, `delete_unknownComment_returns404_commentNotFound`.

**Commit** : `test(backend): port CommentService + resources tests from legacy (TEST-006, 8 SCRUM-144 sentinels)`

#### Étape 7.6 à 7.13 — Restant (8 services)

Pattern identique pour : co-organizer-service, attendance-service, report-service, favorite-service, view-service, calendar-service, stats-service, share-service.

**Commit format** : `test(backend): port <Svc>Service + resources tests from legacy (TEST-NNN)`

**Validation collective fin Étape 7** :
- `./mvnw verify -DskipITs` SUCCESS
- Couverture jacoco par service ≥ 80 % L / ≥ 70 % B
- Les 35 sentinels SCRUM-138/139/144/147 sortent verts par nom : `find backend/services -name '*.java' -path '*/test/*' | xargs grep -l "<sentinel-name>"` doit retourner ≥ 1 hit pour chaque sentinel listé en Décision E.

#### Étape 7.14 — `me-aggregator-service` tests avec WireMock (TEST-016)

**Action** : tests qui simulent fan-out partiel KO (downstream service down) → vérifient que `me-aggregator-service` retourne un état dégradé propre (et non un 500 brut).

**Commit** : `test(backend): MyEventsService fan-out tests with WireMock (TEST-016)`

#### Récap fin Étape 7

**Sous-étapes commitées** : 7.1 à 7.14 (14 commits).
**Findings adressés** : TEST-001 à TEST-018.

(continuer à la suite dans le bloc 4 : Étapes 8-14 + tests strategy + risques + done criteria + livrable + git workflow + DevOps boundary + annexes)
### Étape 8 — Pact contract tests + E2E happy path (Décision J)

**Objectif** : livrer le minimum viable de contract tests + 1 E2E pour fermer SPEC-006.

#### Étape 8.0 — Setup `backend/contract-tests/` module Maven

**Action** :
- Créer `backend/contract-tests/pom.xml` avec packaging jar (NOT Quarkus).
- Ajouter à `<modules>` du parent POM.
- Deps : `au.com.dius.pact.consumer:junit5` (Pact JVM), `quarkus-junit5`, `rest-assured`.

**Commit** : `chore(backend): scaffold contract-tests module (Pact JVM)`

#### Étape 8.1 — Pact `share-service` ↔ `event-service`

**Fichier** : `backend/contract-tests/src/test/java/.../ShareEventPactTest.java`.
**Output** : `backend/contract-tests/pacts/share-event.json` (généré par Pact).

**Test** : `share-service.RedirectResource` consomme `event-service.GET /events/{id}` → assert que la réponse contient `id`, `shareCode`, `status`.

**Commit** : `test(backend): Pact share-service consumer ↔ event-service provider (SPEC-006)`

#### Étape 8.2 — Pact `comment-service` ↔ `event-service` (anti-oracle ISSUE-92)

**Test** : asserter que `event-service.GET /events/{id}` retourne 200 pour PUBLISHED + 404 pour DRAFT non-créateur.

**Commit** : `test(backend): Pact comment-event for ISSUE-92 anti-oracle (SPEC-006)`

#### Étape 8.3 — Pact `comment-service` ↔ `co-organizer-service` (cascade SCRUM-136)

**Test** : `co-organizer-service.GET /events/{eventId}/co-organizers/check?userId=` retourne `{accepted: bool}`.

**Commit** : `test(backend): Pact comment-coorganizer for SCRUM-136 cascade (SPEC-006)`

#### Étape 8.4 — Pact `report-service` ↔ `event-service`

**Test** : asserter que `event-service.GET /events/{id}` retourne 200 + status valide pour le ban via Kafka.

**Commit** : `test(backend): Pact report-event provider contract (SPEC-006)`

#### Étape 8.5 — E2E happy path `backend/e2e/E2EHappyPathTest.java`

**Setup** : module Maven `backend/e2e/`, packaging jar, deps `quarkus-junit5`, `rest-assured`.

**Test** : `@QuarkusIntegrationTest` qui :
1. `POST /api/users/me` (auto-création depuis JWT — bouchon `@TestSecurity`).
2. `POST /api/events` avec body valide → 201, récupère `id`.
3. `PATCH /api/events/{id}/publish` → 200, status = PUBLISHED.
4. `GET /api/events/{id}` → 200, expose `creatorId` enrichi.
5. (option) Vérifier qu'un message Kafka `events.published` a été émis (via in-memory connector).

**Commit** : `test(backend): E2E happy path (create user → create event → publish) (SPEC-006)`

#### Récap fin Étape 8

**Sous-étapes commitées** : 8.0 à 8.5 (6 commits).
**Findings adressés** : SPEC-006, SPEC-018 (test integration cible).

### Étape 9 — Observabilité (logs JSON + Prometheus + tracing)

**Objectif** : SPEC-004 + SPEC-012 + SPEC-013 + SPEC-019. Ajouter les 3 extensions Quarkus + consommer `shared-tracing`.

#### Étape 9.1 — `quarkus-logging-json` + `quarkus-micrometer-registry-prometheus` aux 13 services

**Action** : pour chaque service métier (13) :
1. Ajouter dans `pom.xml` :
   ```xml
   <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-logging-json</artifactId></dependency>
   <dependency><groupId>io.quarkus</groupId><artifactId>quarkus-micrometer-registry-prometheus</artifactId></dependency>
   ```
2. Ajouter dans `application.properties` :
   ```properties
   quarkus.log.console.json=true
   quarkus.micrometer.export.prometheus.enabled=true
   quarkus.micrometer.export.prometheus.path=/q/metrics
   ```

**Commit** : `feat(backend): add quarkus-logging-json + micrometer-prometheus to 13 services (SPEC-004, SPEC-012, SPEC-019)`

#### Étape 9.2 — Consommer `shared-tracing` dans les 13 services

**Action** : pour chaque service :
1. Ajouter `<dependency>shared-tracing</dependency>` au POM.
2. Le `RequestIdFilter` est auto-discoverd via Jandex (le filter est `@Provider`).
3. Pour les REST clients : ajouter le `RequestIdClientFilter` via `@RegisterProvider` sur chaque `@RegisterRestClient` interface.
4. Si Kafka producteur : utiliser le `MdcKafkaInterceptor` (configurable via property).

**Commit** : `feat(backend): consume shared-tracing in 13 services (SPEC-013)`

#### Étape 9.3 — `quarkus-rest-client-reactive` confirmation présent

**Action** : vérifier que chaque service consommateur de REST clients a bien `quarkus-rest-client-reactive` au POM. Étape 5 a livré les REST clients donc cette dep DOIT être présente. Sinon ajouter (pas censé arriver, mais filet de sécurité).

**Commit** : (probablement aucun, juste validation).

#### Étape 9.4 — `quarkus-smallrye-fault-tolerance` (Décision B implique resilience annotations)

**Action** : pour chaque service consommateur de REST clients, ajouter au POM `quarkus-smallrye-fault-tolerance` (pour `@Retry`, `@Timeout`, `@CircuitBreaker`, `@Fallback`). Étape 5 a probablement déjà livré ça implicitement — sinon vérifier.

**Commit** : (probablement aucun, juste validation).

#### Récap fin Étape 9

**Sous-étapes commitées** : 9.1 + 9.2 (2 commits gros).
**Findings adressés** : SPEC-004, SPEC-012, SPEC-013, SPEC-019.

### Étape 10 — Plugin Kong rate-limiting (Décision G)

**Objectif** : SPEC-003 + INFRA-002. Ajouter le plugin `rate-limiting` à 3 routes ciblées.

#### Étape 10.1 — Modifier `k8s/chart/templates/kong/configmap-routes.yaml`

**Action** : ajouter `plugins:` par-route sur les 3 routes ciblées (`events-list`, `event-comments-post`, `follow-actions`) avec :
```yaml
plugins:
  - name: rate-limiting
    config:
      minute: <budget>
      policy: local
      fault_tolerant: true
      hide_client_headers: false
```

Budgets : `events.create=10`, `comments.post=10`, `follows.follow=30`.

**Validation** : `helm template ./k8s/chart` rend bien la ConfigMap mise à jour. Pas de test runtime nécessaire (la spec ne le demande pas).

**Commit** : `feat(infra): add Kong rate-limiting plugin per route (SPEC-003, INFRA-002)`

#### Récap fin Étape 10

**Sous-étapes commitées** : 10.1 (1 commit).
**Findings adressés** : SPEC-003, INFRA-002.

### Étape 11 — Helm hygiene

**Objectif** : INFRA-006 + INFRA-009.

#### Étape 11.1 — Ajouter `livenessProbe` aux 13 deployments (INFRA-006)

**Action** : pour chaque `k8s/chart/templates/<svc>-service/deployment.yaml` (13 services actifs), ajouter :
```yaml
livenessProbe:
  httpGet:
    path: /api/q/health/live
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 30
  failureThreshold: 3
```

**Commit** : `chore(infra): add livenessProbe to 13 service Deployments (INFRA-006)`

#### Étape 11.2 — Mettre à jour le commentaire `templates/ingress/ingress.yaml` (INFRA-009)

**Action** : remplacer le commentaire ligne 17-21 (« currently forwards 100% of the traffic to the legacy monolith ») par une description Kong DB-less + 13 services.

**Commit** : `docs(infra): align ingress.yaml comment with post-migration topology (INFRA-009)`

#### Récap fin Étape 11

**Sous-étapes commitées** : 11.1, 11.2 (2 commits).

### Étape 12 — CI matrix per-service (Décision H)

**Objectif** : SPEC-007 + SPEC-008 + SPEC-019 + INFRA-007 + INFRA-010 + INFRA-011 + INFRA-012 + HYGIENE-005. Le YAML est livré ; l'activation effective dépend des 13 SonarCloud projects côté DevOps.

#### Étape 12.1 — Refonte `.github/workflows/build.yml` en matrix per-service

**Action** : remplacer le single-job `build-backend` par un job avec `strategy.matrix.service: [share, view, favorite, calendar, follow, comment, co-organizer, attendance, report, stats, me-aggregator, user, event]` (13 microservices, notification-service exclu). Pour chaque matrix, exécuter `./mvnw -pl services/${{ matrix.service }}-service -am verify`. Sonar invoqué avec `-Dsonar.projectKey=unige-pinfo6-2026_unige-events-${{ matrix.service }}-service`.

Plus un job séparé qui build les 8 shared libs (idéalement ils peuvent être dans le `-am` du premier service qui les consomme, mais pour granularité, un job dédié `shared-libs` matrix sur les 10 libs aussi acceptable).

**Commit** : `ci(backend): refactor build.yml to matrix per-service (SPEC-007, INFRA-010)`

#### Étape 12.2 — Override `sonar.projectKey` par module

**Action** : pour chaque `services/<svc>-service/pom.xml`, ajouter dans `<properties>` :
```xml
<sonar.projectKey>unige-pinfo6-2026_unige-events-<svc>-service</sonar.projectKey>
<sonar.projectName>unige-events-<svc>-service</sonar.projectName>
```

Idem pour les 10 shared libs (chacune son projectKey).

**Commit** : `ci(backend): per-module sonar.projectKey override (SPEC-019, INFRA-011)`

#### Étape 12.3 — Retirer `<sonar.coverage.exclusions>services/*-service/**/*</sonar.coverage.exclusions>` du parent POM (HYGIENE-005)

**Action** : suppression des deux propriétés `sonar.cpd.exclusions` + `sonar.coverage.exclusions`. Le commentaire (lignes 64-86) est aussi retiré (ou réduit à une note historique).

**Commit** : `ci(backend): remove Sonar coverage/CPD exclusion glob (HYGIENE-005)`

#### Étape 12.4 — Rename `image.api.tag` → `image.tag` ou `image.<svc>.tag` (INFRA-007, SPEC-008)

**Décision pratique** : renommer en `image.tag` (mono) et ajouter à `values.yaml` un block par service :
```yaml
image:
  tag: <github.sha>  # shared default
  user-service:
    name: unige-events-user-service
  event-service:
    name: unige-events-event-service
  # ...
```

Chaque deployment template référence `{{ .Values.image.tag }}` avec image name `{{ .Values.image.<svc>.name }}` ou la convention déjà en place.

**Action** :
- `values.yaml` : retirer `image.api.tag` ; ajouter `image.tag`.
- 14 deployment templates : remplacer `{{ .Values.image.api.tag }}` par `{{ .Values.image.tag }}`.
- `kafka/statefulset.yaml` : idem (annotation release-sha).
- `.github/workflows/deploy.yml` : `--set image.tag="${{ github.sha }}"` (au lieu de `image.api.tag`).

**Commit** : `chore(infra): rename image.api.tag to image.tag (INFRA-007, SPEC-008)`

#### Étape 12.5 — Préparation note pour DevOps : 13 SonarCloud projects requis

**Action** : ajouter au `backend/docs/devops-handoff.md` (créé en Étape 13.6) une section explicite « 13 SonarCloud projects à créer » avec les noms exacts (`unige-events-share-service`, etc.) et le format des secrets attendus (`SONAR_TOKEN_<SVC>` ou `SONAR_TOKEN` global).

(Le commit du fichier `devops-handoff.md` se fait en Étape 13.6.)

#### Récap fin Étape 12

**Sous-étapes commitées** : 12.1, 12.2, 12.3, 12.4 (4 commits).
**Findings adressés** : SPEC-007, SPEC-008, SPEC-019, INFRA-007, INFRA-010, INFRA-011, INFRA-012, HYGIENE-005.

### Étape 13 — Documentation finale (Décision U)

**Objectif** : aligner toute la documentation avec l'état post-completion.

(NB : L'Étape 2 a déjà fait un premier pass de cohérence. L'Étape 13 fait un second pass après les changements de code des Étapes 3-12. Certaines sous-étapes 13.* peuvent être triviales si Étape 2 a déjà couvert ; d'autres nécessitent un patch fresh.)

#### Étape 13.1 — `architecture.md` réécriture totale post-completion

**Patch** :
- Diagramme à jour (cf. spec complétion section « Architecture cible (post-completion) »).
- Section « Vue d'ensemble — topologie microservices » : 13 microservices + 10 shared libs + Kafka KRaft + Kong DB-less.
- Section « Composants par couche » : retirer ou réécrire.
- Section « Flux d'une requête typique » : Kong → service Quarkus → REST clients.
- Section « ModerationCleanupJob » : précise `BANNED` + Kafka `events.banned`.
- Section « CI/CD » : reflect Étape 12 (matrix per-service, image.tag).

**Commit** : `docs(backend): rewrite architecture.md for post-completion topology`

#### Étape 13.2 — `data-model.md` cohérence finale

**Patch** : ajout de la note REST clients par entité (« Lue cross-service via REST client `<Service>ServiceClient` »). Ajout de la section sur les 9 enums (cf. `shared-domain-enums`). Inclure note sur l'absence de schéma physique séparé (cf. Décision C report S9+).

**Commit** : `docs(backend): align data-model.md with REST clients + shared-domain-enums`

#### Étape 13.3 — `api-contract.md` cohérence finale

**Patch** : ajout de la table « Endpoints internes service-to-service » (cf. Décision Q + Décision B) + précisions rate-limit.

**Commit** : `docs(backend): align api-contract.md with internal endpoints + rate-limit final state`

#### Étape 13.4 — `dev-guide.md` cohérence finale

**Patch** : section « Workflow modifier le schéma » alignée Flyway-first (cf. Décision C report). Section « 24 modules au build » (16 ↔ 24 selon shared libs livrées).

**Commit** : `docs(backend): align dev-guide.md with 24-module build`

#### Étape 13.5 — `sprint-context.md` Étape 19 (post-completion)

**Patch** : ajouter en haut une section :
```
## Sprint 8 — Complétion post-PR #158 — 2026-MM-DD
Livré (cf. specs_microservices_migration_completion.md, ~14 étapes, ~80+ commits).
- Étape 1 : 8 bug fixes critiques + hygiène.
- Étape 2 : doc alignment (24 findings).
- Étape 3 : 8 nouvelles shared libs créées.
- Étape 4 : 9 producteurs Kafka + 1 consumer livrés (10 topics non vides).
- Étape 5 : 35 JPA stubs remplacés par REST clients ; centralisation anti-oracles ; admin bypass.
- Étape 6 : DB-per-service formellement DEFERRED S9+ (Décision C).
- Étape 7 : 1818 tests legacy portés ; 35 sentinels SCRUM-138/139/144/147 verts.
- Étape 8 : 4 pacts + 1 E2E happy path.
- Étape 9 : observabilité (logs JSON + Prometheus + tracing).
- Étape 10 : Kong rate-limiting plugin.
- Étape 11 : Helm hygiene (livenessProbe).
- Étape 12 : CI matrix per-service YAML (activation DevOps requise).
- Étape 13 : doc finale.
- Étape 14 : vérification finale.
État final : couverture jacoco ≥ 80 %, CI verte, PR pas mergée (Elie).
```

**Commit** : `docs(backend): record Étape 19 post-completion in sprint-context.md`

#### Étape 13.6 — `backend/docs/devops-handoff.md` — création

**Action** : créer le fichier `backend/docs/devops-handoff.md` avec les 7 items DevOps (cf. Décision V) + un préambule qui fait la jonction entre PR #158 backend et le travail DevOps prochain.

**Sections** :
1. **TL;DR** — la PR est prête côté code, voici ce qu'il reste à faire côté infra.
2. **Items différés explicitement S9+** — 7 items avec justification + action attendue.
3. **Items YAML produits par backend, à activer par DevOps** — CI matrix (13 SonarCloud projects), Kong rate-limiting policy=redis migration future, etc.
4. **Smoke tests recommandés post-deploy preview** — `curl /api/users/me` 401, `curl /api/events` 200, vérifier les 10 topics Kafka non vides, vérifier `/q/metrics` exposé.
5. **Liens** — vers spec orig, audit, spec complétion, sprint-context.

**Commit** : `docs(backend): add devops-handoff.md to formalize hors-scope items`

#### Étape 13.7 — `backend/docs/internal-endpoints.md` — création

**Action** : créer le fichier qui liste les ~10 endpoints internes service-to-service (cf. Décision Q). Format : path + service propriétaire + service consommateur + payload schema.

**Commit** : `docs(backend): document internal service-to-service endpoints (Décision Q)`

#### Étape 13.8 — `microservices-migration-roadmap.md` final

**Patch** : ajouter section « PR 18 — Complétion » avec les ~80 commits de la complétion + statut final.

**Commit** : `docs(backend): add PR 18 completion section to migration roadmap`

#### Étape 13.9 — `AGENTS.md` racine + `backend/AGENTS.md` final pass

**Patch** : final consistency pass après tous les changements de code. Vérifier que les compteurs (24 modules, 10 shared libs, 13 services) sont cohérents. (Probablement déjà OK depuis Étape 2.)

**Commit** : `docs(root): final consistency pass on AGENTS.md`

#### Étape 13.10 — PR body de #158 final

**Action** :
```bash
gh pr view 158 --json body --jq .body > /tmp/pr-body-final.md
# édit:
# - Changer titre/résumé : "Sprint 8 — migration backend monolithe → microservices LIVRÉE COMPLÈTEMENT".
# - Mettre à jour le tableau "13 services extraits" pour refléter REST clients + Kafka complet.
# - "What's NOT in this PR" :
#   - Items vraiment déférés DevOps (cf. devops-handoff.md).
#   - Schemas-per-service (Décision C).
# - "CI / Sonar — vert" : mettre à jour selon Étape 14.
# - Mention de specs_microservices_migration_completion.md.
gh pr edit 158 --body-file /tmp/pr-body-final.md
```

(Action `gh`, pas de commit Git.)

#### Récap fin Étape 13

**Sous-étapes commitées** : 13.1 à 13.9 (9 commits) + 13.10 (gh edit).
**Findings adressés** : (rappel) tous les DOC-001..024 finalisés ; nouveaux fichiers créés (devops-handoff.md, internal-endpoints.md).

### Étape 14 — Vérification finale

**Objectif** : valider que la complétion est livrée + tous les invariants tenus.

#### Étape 14.0 — Build + tests verts en local

```bash
cd /workspace/backend
./mvnw verify -DskipITs   # ~3-4 min sur 24 modules
# Attendu : SUCCESS sur tous les modules
```

#### Étape 14.1 — Couverture jacoco par module

```bash
for svc in services/*-service services/shared-*; do
    rep="$svc/target/jacoco-report/jacoco.xml"
    if [ -f "$rep" ]; then
        # parse coverage % via xmllint ou grep
        line_pct=$(grep -oE 'type="LINE" missed="[0-9]+" covered="[0-9]+"' $rep | head -1)
        echo "$svc: $line_pct"
    fi
done
```

**Validation** : chaque service ≥ 80 % L / ≥ 70 % B ; chaque shared lib ≥ 95 % L / ≥ 90 % B.

Si un service est en dessous, créer un sous-commit `test(backend): boost <svc>-service coverage to ≥80% (TEST-NNN)`.

#### Étape 14.2 — Sentinels SCRUM-138/139/144/147 verts par nom

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

**Validation** : 35 ✅, 0 ❌. Si un sentinel manque, créer un commit qui le rajoute (ou réutiliser le commit Étape 7 correspondant).

#### Étape 14.3 — 0 JPA stub cross-service

```bash
find backend/services -name '*Stub.java' -not -path '*/target/*'
# Attendu : 0 résultat
```

Si > 0, suppression à finaliser dans un commit `refactor(backend): remove last JPA stubs`.

#### Étape 14.4 — Invariants frontend + openapi

```bash
git diff --shortstat origin/main HEAD -- frontend/   # 0 lignes
git diff --shortstat origin/main HEAD -- openapi/    # ≤ 32 lignes (la suppression du doublon /events/{id}/view, cf. Décision Q)
```

#### Étape 14.5 — CI verte

```bash
git push origin 'refactor(backend)--migrate-to-microservices'   # tous les commits Étapes 1-13 pushés
gh pr checks 158 --watch
```

**Attendu** :
- Build / Build Backend ✅
- Build / Build Frontend ✅
- SonarCloud Backend ✅ (Quality Gate passed après suppression du glob d'exclusion — la couverture business doit être réelle ≥ 80 % par service)
- SonarCloud Frontend ✅
- Deploy / Deploy to Preview ✅
- PR Title Check ✅

**Si SonarCloud échoue parce que les 13 projects per-service n'existent pas** : c'est attendu (DevOps doit créer les projects). Acceptable si seul SonarCloud échoue ET que le motif est « project not found ». Documenter explicitement cette dépendance dans le PR body (cf. Étape 13.10) et dans `devops-handoff.md`.

#### Étape 14.6 — PR body final + handoff

Action : `gh pr edit 158 --body-file <final body>` + commentaire optionnel sur la PR « Complétion livrée, prête pour DevOps handoff. Cf. backend/docs/devops-handoff.md. »

#### Récap fin Étape 14

**Sous-étapes** : 14.0 à 14.6 (validation, pas de commits sauf si nettoyages tardifs).

**Si toute la chaîne est verte** : la complétion est terminée. L'humain Elie merge la PR quand il valide.

---

## Stratégie de tests (cible post-completion)

### Niveau 1 — Tests unitaires par service

- **Cible** : ≥ 80 % L / ≥ 70 % B par service métier (matche Sonar gate). ≥ 95 % L / ≥ 90 % B par shared lib.
- **Outils** : JUnit 5, Mockito, `@QuarkusTest`, `@TestSecurity`, RestAssured.
- **Scope** : chaque méthode publique des services applicatifs + chaque branche notable.

### Niveau 2 — Tests d'intégration par service

- **Cible** : tous les endpoints REST exposés sont testés (200/4xx/5xx happy + sad path).
- **Outils** : `@QuarkusTest` + DevServices PostgreSQL + RestAssured + WireMock pour les REST clients sortants.
- **Profile `%test`** : `quarkus.oidc.enabled=false`, `mp.messaging.outgoing.<chan>.connector=smallrye-in-memory`.

### Niveau 3 — Tests Pact (contract)

- **Cible** : 4 pacts JSON commités dans `backend/contract-tests/pacts/`.
- **Pacts** : share-event, comment-event (anti-oracle), comment-coorganizer (cascade), report-event.
- **Pact JVM** broker-less : les pacts sont produits par les consommateurs et vérifiés en local par les providers (pas de Pact Broker tiers).

### Niveau 4 — E2E happy path

- **Cible** : 1 test `@QuarkusIntegrationTest` qui traverse 4 services (user → event → publish → get).
- **Path** : `backend/e2e/src/test/java/.../E2EHappyPathTest.java`.

---

## Risques et mitigations

| Risque | Probabilité | Impact | Mitigation |
|---|---|---|---|
| **Régression de sécurité sur anti-oracle ISSUE-92/93 lors de la centralisation REST** (Étape 5) | Medium | Critical | Sentinels Sécurité explicitement dans la liste 35 sentinels ; tests integration end-to-end avec un payload `DRAFT` qui doit retourner 404 ; pact ISSUE-92 (Étape 8.2). |
| **Drift de payload Kafka** entre producteur et consommateur futur | Medium | High | Lib `shared-kafka-events` (Décision D) — un seul record par topic, partagé entre producer et consumer. Pact JSON pour les pairs critiques (KAFKA-001 / KAFKA-002 events.banned). |
| **CI matrix non activable** parce que les 13 SonarCloud projects n'existent pas | High | Medium | YAML produit + documenté dans `devops-handoff.md`. Si SonarCloud échoue spécifiquement sur « project not found », c'est un blocker DevOps, pas backend. |
| **Coût de migration des tests legacy** (Étape 7) | High | Medium | Portage mécanique avec `git show 41074e9:` ; les tests qui ne fittent pas le nouveau modèle (ex. utilisaient `EventStub` au lieu de REST mock) sont adaptés au cas par cas. |
| **Performance LAN cross-service** : un GET event-service.getById qui résout creatorId via user-service ajoute 5-10ms latence | Low | Low | Acceptable en preview (LAN dev). En prod, viser `@CircuitBreaker` + cache local (futur, Décision B option (b) Kafka projection). |
| **Crash window CDI `AFTER_SUCCESS`** (microsecondes entre commit et fire) | Low | Low | Acceptable pour topics non-critiques (notifications). Outbox pattern en alternative future si critère devient critical. |
| **Build Maven temps total** (24 modules au lieu de 16) | Medium | Low | Estimer +1-2 min vs 3m45s actuel. CI matrix per-service (Étape 12) parallélise et compense. |

---

## Critères de done (checklist linéaire)

- [ ] **Décisions A à Y tranchées** + appliquées (cf. § Décisions techniques de cette spec).
- [ ] **Étapes 0 à 14** livrées en commits séparés sur la branche persistante `refactor(backend)--migrate-to-microservices`.
- [ ] `cd backend && ./mvnw verify -DskipITs` **vert** local sur les 24+ modules.
- [ ] `gh pr checks 158` tous **verts** (Build BE, Build FE, Sonar BE, Sonar FE, Deploy Preview, PR Title Check) — sauf si SonarCloud échoue spécifiquement sur « project not found » (blocker DevOps).
- [ ] `git diff --shortstat origin/main HEAD -- frontend/` = **0 ligne**.
- [ ] `git diff --shortstat origin/main HEAD -- openapi/` ≤ **32 lignes** (suppression doublon `/events/{id}/view`, Décision Q).
- [ ] **35 sentinels** SCRUM-138/139/144/147 verts par nom (cf. Étape 14.2).
- [ ] **Coverage jacoco** ≥ 80 % lines + ≥ 70 % branches par service métier ; ≥ 95 % L + ≥ 90 % B par shared lib.
- [ ] **9 producteurs Kafka livrés** (topics non vides en preview env).
- [ ] **1 consumer Kafka livré** (event-service ← `events.banned`).
- [ ] **0 JPA stub** cross-service (`find backend/services -name '*Stub.java' -not -path '*/target/*'` → vide).
- [ ] **N REST clients** `@RegisterRestClient` livrés (cf. tableau Architecture cible — N ≈ 35).
- [ ] **Plugin Kong rate-limiting** actif (≥ 3 routes avec buckets).
- [ ] **livenessProbe** sur 13 deployments microservices.
- [ ] **CI matrix YAML** produit + projectKey override + glob exclusion Sonar retiré.
- [ ] **Toutes les docs** alignées (architecture.md, data-model.md, api-contract.md, dev-guide.md, sprint-context.md Étape 19, microservices-migration-roadmap.md, AGENTS.md racine, backend/AGENTS.md, backend/docs/devops-handoff.md, backend/docs/internal-endpoints.md).
- [ ] **PR body** mis à jour via `gh pr edit 158 --body-file ...`.
- [ ] **PR pas mergée** — Elie Bussod merge lui-même après validation.
- [ ] **Sentinels d'invariants** : `shared-rate-limit` 100 % couverture intacte ; `shared-storage` 100 % couverture intacte.

---

## Livrable FINAL attendu

**Titre PR (inchangé)** : `chore(backend): migrate to microservices architecture with Kong gateway and Kafka broker`
(NB : workaround `pr-title-check.yml` documenté ; non régression depuis bee933d.)

**Body PR final** (template à coller dans `gh pr edit 158 --body-file`) :

```markdown
## Résumé

Sprint 8 — migration backend monolithe → microservices **livrée complètement**. 13 microservices Quarkus extraits + 10 shared libs, Kong gateway DB-less, Kafka KRaft + 10 topics + 9 producteurs + 1 consumer, anti-oracles ISSUE-92/93 + cascade SCRUM-136 centralisés derrière REST clients, observabilité (logs JSON + Prometheus + tracing X-Request-ID), 1818 tests legacy portés (35 sentinels SCRUM-138/139/144/147 verts), Pact + E2E happy path.

> **Titre** : la spec demande `refactor(backend): migrate to microservices...` mais [`pr-title-check.yml`](.github/workflows/pr-title-check.yml) impose le scope `scrum-XXX` pour `refactor`. Workaround : `chore(backend):` (scope libre). Bug documenté dans [`sprint-context.md`](backend/docs/sprint-context.md).

### 13 services + 10 shared libs livrés
[table service × endpoints × tables × topics produits × topics consommés]

### CI / Sonar — vert ✅

- Build Backend ✓ matrix per-service
- Build Frontend ✓
- SonarQube Cloud Backend ✓ — Quality Gate passed sur **chaque** project per-service (pré-requis : 13 projects créés côté DevOps, cf. backend/docs/devops-handoff.md)
- SonarQube Cloud Frontend ✓
- Deploy to Preview ✓ — 13 services Ready, 10 topics Kafka non-vides
- Lint PR title ✓

### Invariants tenus

- `git diff --stat openapi/` = 32 lignes (suppression doublon `/events/{id}/view` — Décision Q de la spec complétion)
- `git diff --stat frontend/` = 0 ligne ✅
- 35 sentinels SCRUM-138/139/144/147 verts par nom
- 0 JPA stub cross-service

### What's NOT in this PR (DevOps follow-up)

| Item | Suite |
|---|---|
| **DB-per-service** (schémas séparés via Flyway physique) | DevOps S9+ — différé formellement (cf. Décision C de la spec complétion) |
| **Cluster Kafka prod-grade** (RF=3, partitions ≥ 3, ISR ≥ 2) | DevOps S9+ |
| **Kong production** (DB-mode, plugin `rate-limiting` policy=redis cluster-wide, OpenTelemetry) | DevOps S9+ |
| **NetworkPolicies K8s** isolation service-to-service | DevOps S9+ |
| **13 SonarCloud projects** + tokens secrets | DevOps action one-shot pour activer la CI matrix |
| **Doppler secrets prod** (DB_PASSWORD, OIDC_*, S3_*, KAFKA_BOOTSTRAP_SERVERS) | DevOps |
| **Domaines / certs / Cloudflare tunnel preview** | DevOps |

## Documentation

- [x] Spec figée : [`specs_archives/specs_claude/specs_microservices_migration.md`](specs_archives/specs_claude/specs_microservices_migration.md)
- [x] Audit : [`specs_archives/audit_pr158_microservices_migration.md`](specs_archives/audit_pr158_microservices_migration.md)
- [x] Spec complétion : [`specs_archives/specs_claude/specs_microservices_migration_completion.md`](specs_archives/specs_claude/specs_microservices_migration_completion.md)
- [x] Sprint context (Étape 19) : [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md)
- [x] Architecture finale : [`backend/docs/architecture.md`](backend/docs/architecture.md)
- [x] Dev guide : [`backend/docs/dev-guide.md`](backend/docs/dev-guide.md)
- [x] DevOps handoff : [`backend/docs/devops-handoff.md`](backend/docs/devops-handoff.md)
- [x] Internal endpoints : [`backend/docs/internal-endpoints.md`](backend/docs/internal-endpoints.md)
- [x] Roadmap : [`backend/docs/microservices-migration-roadmap.md`](backend/docs/microservices-migration-roadmap.md)

## Test plan

- [x] CI : Build Backend matrix per-service, Build Frontend, Sonar BE+FE, Deploy Preview, Lint PR title — verts (sauf Sonar pre-DevOps)
- [x] Sonar Quality Gate per-service passed
- [x] `cd backend && ./mvnw verify -DskipITs` → 24+ modules SUCCESS
- [x] `git diff --stat frontend/` = 0
- [x] `git diff --stat openapi/` ≤ 32 lignes (Décision Q)
- [x] 35 sentinels par nom verts
- [x] 0 JPA stub cross-service
- [x] 9 producteurs Kafka livrés
- [x] 1 consumer Kafka livré (events.banned)
- [x] Pact 4 contracts + E2E 1 happy path verts
- [ ] (à valider preview) `curl /api/users/me` 401 ; `curl /api/events` 200 ; topics Kafka non-vides en preview env

🤖 Generated with [Claude Code](https://claude.com/claude-code) — execution autonome bypass-permissions, exécutée en suivant la spec complétion.
```

---

## Workflow Git imposé à l'exécuteur

- **Branche persistante** : `refactor(backend)--migrate-to-microservices` (NE PAS créer de nouvelle branche).
- **Pas de squash** — chaque sous-étape numérotée a son propre commit (granularité 5-50 fichiers, ≤500 lignes diff sauf bascule de lib).
- **Pas de force push** — additif uniquement.
- **Pas de `--no-verify`** — si pre-commit hook échoue, fixer la cause racine.
- **Pas de `--no-gpg-sign`** — signage Git par défaut respecté.
- **Pas de `--amend`** sur du commit pushé — fixer via nouveau commit.
- **Pas de modification de `main`** ni des autres branches feature (`feature/s7-recurrence`, etc.).
- **Push après chaque sous-étape verte** : `git push origin 'refactor(backend)--migrate-to-microservices'`.
- **Watch CI après chaque push** : `gh pr checks 158 --watch` jusqu'à terminaison.
- **Si CI échoue** : `gh run view <RUN_ID> --log-failed` → fix → nouveau commit additif → push → re-watch. Jamais de skip silencieux.
- **Mise à jour `sprint-context.md` Étape 19** : un patch incrémental après chaque étape, **sans** commit dédié à chaque step (regroupé en commit d'Étape 13.5 final).
- **Mise à jour PR body via `gh pr edit 158 --body-file`** : à la toute fin (Étape 13.10), pas en milieu de parcours.

---

## Frontière DevOps — items NON couverts par cette spec

Les 7 items DevOps suivants sont **explicitement hors scope** de la complétion S8. Ils sont formalisés dans `backend/docs/devops-handoff.md` (Étape 13.6).

| # | Item | Statut spec | Justification report S9+ | Backend a-t-il livré sa moitié ? |
|---|---|---|---|---|
| 1 | Création de 13 SonarCloud projects per-service | spec orig. décision 17/25 | Nécessite SonarCloud admin UI | ✅ YAML CI produit (Étape 12) |
| 2 | Cluster Kafka prod-grade (RF=3, partitions ≥ 3, ISR ≥ 2, durabilité acks=all) | spec orig. § 4.5 + décision 26 | Hors scope cours, single-broker S8 OK | ✅ Helm chart single-broker livré |
| 3 | Schemas-per-service (Flyway physique séparé) | spec orig. décisions 8/9 | Reportée par Décision C avec justification | ❌ déviation actée, pas de livraison backend |
| 4 | NetworkPolicies K8s | (hors spec orig.) | Pure ops K8s | N/A |
| 5 | Domaines / certs prod / Cloudflare tunnel preview | (hors spec orig.) | Pure ops | N/A |
| 6 | Secrets Doppler `DB_PASSWORD`, `OIDC_*`, `S3_*`, `KAFKA_BOOTSTRAP_SERVERS` | (hors spec orig.) | Pure ops | N/A — backend code fail-fast si manquant (SEC-004 corrigé) |
| 7 | Production-grade Kong (DB-mode, OpenTelemetry, rate-limiting policy=redis) | spec orig. décision 6 | Hors scope cours, DB-less S8 OK | ✅ rate-limiting policy=local livré (Étape 10) |

**L'exécuteur autonome ne touche à AUCUN de ces 7 items.** Toute action de complétion qui dépasse « config YAML lue par Helm » doit être déférée DevOps avec note explicite dans `devops-handoff.md`.

---

(continuer à la suite dans le bloc 5 : Annexes mapping finding → étape, plan de bascule des tests legacy, REST clients récap, Helm files à modifier, commandes de référence)
## Annexes

### Annexe A — Mapping finding audit → étape spec (132 findings)

Tableau exhaustif. Référence finding = ID exact du fichier `audit_pr158_microservices_migration.md`.

#### Cat 1 — Conformité spec (22 findings)

| Finding | Sévérité | Étape spec | Sous-étape | Note |
|---|---|---|---|---|
| SPEC-001 | critical | 6 | DEFERRED | Décision C — différé S9+ formellement, doc Étape 13.6 |
| SPEC-002 | critical | 5 | 5.0..5.9 | Cœur Étape 5 (REST clients) |
| SPEC-003 | critical | 10 | 10.1 | Plugin Kong rate-limiting |
| SPEC-004 | critical | 9 | 9.1 + 9.3 + 9.4 | Décision K — 3 deps Quarkus ajoutées + rest-client-reactive (Étape 5) + fault-tolerance (Étape 5) |
| SPEC-005 | high | 4 | 4.1..4.5 | 9 producteurs Kafka livrés |
| SPEC-006 | high | 8 | 8.1..8.5 | Pact + E2E |
| SPEC-007 | high | 12 | 12.1 | CI matrix YAML |
| SPEC-008 | high | 12 | 12.4 | image.api.tag → image.tag |
| SPEC-009 | high | (multi) | Étape 5 (REST) + Décision M | BFF complet via REST clients ; me-aggregator agrège réellement |
| SPEC-010 | high | 13 | 13.6 | Doc devops-handoff.md formalise notification-service scope |
| SPEC-011 | high | 5 | 5.* | FK `@ManyToOne` cross-service supprimées avec les stubs |
| SPEC-012 | medium | 9 | 9.1 | quarkus-logging-json |
| SPEC-013 | medium | 3 + 9 | 3.4 + 9.2 | Lib shared-tracing créée + consommée |
| SPEC-014 | medium | 5 | 5.6 | Endpoint /co-organizers/check exposé |
| SPEC-015 | medium | (no-op) | — | Pas de plugin Kong jwt — décision spec orig. retenue, statu quo OK |
| SPEC-016 | medium | (acté) | — | Tests Kafka in-memory acté ; Testcontainers pas livré (déviation explicite) |
| SPEC-017 | medium | 0 | 0 | Branche persistante — vérification préflight |
| SPEC-018 | medium | (déjà OK) | — | Chart.yaml v0.2.0 — déjà fait, sentinel |
| SPEC-019 | low | 12 | 12.2 | sonar.projectKey override per-module |
| SPEC-020 | low | (acté) | — | docker-compose.dev pas livré (acté DevOps) |
| SPEC-021 | high | 5 | 5.8 | Anti-oracles + cascade SCRUM-136 via REST |
| SPEC-022 | medium | 13 | 13.6 | notification-service scope formalisé |

#### Cat 2 — Bugs runtime (14 findings)

| Finding | Sévérité | Étape spec | Sous-étape | Note |
|---|---|---|---|---|
| BUG-001 | critical | 4 | 4.0 | CDI @Observes(AFTER_SUCCESS) refactor (Décision A) |
| BUG-002 | high | 4 | 4.0 | idem |
| BUG-003 | high | 1 | 1.1 | Guard cancel sur EXPIRED |
| BUG-004 | high | (acté) | — | events.deleted Kafka à différer S9+ ; mention dans devops-handoff.md (action `chore` future) |
| BUG-005 | medium | 1 | (1.X future) | Race attendance — fix optionnel S8 ; sinon S9+ |
| BUG-006 | medium | 1 | (1.X future) | Idempotence favorite — fix optionnel S8 |
| BUG-007 | medium | (acté) | — | À investiguer en S9+ (cas bord co-organizer DECLINED) |
| BUG-008 | high | 5 | 5.0..5.9 | Couvert par REST clients |
| BUG-009 | medium | 1 | 1.6 | @Transactional getPublicProfile |
| BUG-010 | medium | 1 | 1.5 | Guard null updateMyProfile |
| BUG-011 | low | 3 | 3.3 + 5.0 | shared-jaxrs lib + bascule attendance-service |
| BUG-012 | low | 1 | 1.4 | RedirectResource fail-fast |
| BUG-013 | low | (acté) | — | Audit DDL cascades à investiguer S9+ |
| BUG-014 | medium | 1 | 1.7 | TZ normalization documentée |

#### Cat 3 — Couverture tests (18 findings)

| Finding | Sévérité | Étape spec | Sous-étape | Note |
|---|---|---|---|---|
| TEST-001 | BLOCKER | 7 | 7.1..7.13 | 35 sentinels par nom verts ; portage des 1818 tests |
| TEST-002 | CRITICAL | 7 | 7.2 | event-service tests |
| TEST-003 | CRITICAL | 7 | 7.1 | RecurrenceGeneratorTest |
| TEST-004 | CRITICAL | 7 | 7.3 | user-service tests |
| TEST-005 | CRITICAL | 7 | 7.4 | follow-service tests |
| TEST-006 | CRITICAL | 7 | 7.5 | comment-service tests |
| TEST-007 | CRITICAL | 7 | 7.6 | co-organizer-service tests |
| TEST-008 | CRITICAL | 7 | 7.7 | attendance-service tests |
| TEST-009 | CRITICAL | 7 | 7.8 | report-service tests |
| TEST-010 | MAJOR | 7 | 7.9 | favorite-service tests |
| TEST-011 | MAJOR | 7 | 7.10 | calendar-service IcsBuilderTest |
| TEST-012 | MAJOR | 7 | 7.11 | view-service tests |
| TEST-013 | MAJOR | 7 | 7.12 | stats-service tests |
| TEST-014 | MAJOR | 7 | 7.13 | share-service tests |
| TEST-015 | MAJOR | 7 | (étape 4 + 7 mix) | EventLifecyclePublisherTest enrichi (cas erreur Kafka, partition key) |
| TEST-016 | MAJOR | 7 | 7.14 | me-aggregator-service WireMock fan-out |
| TEST-017 | MINOR | 13 | 13.6 | notification-service stub doc clarification |
| TEST-018 | MAJOR | 7 | 7.0 | Stratégie mock/stub formalisée — pas de module shared-test-stubs (décision pratique) |

#### Cat 4 — Refactor / dette (18 findings)

| Finding | Sévérité | Étape spec | Sous-étape | Note |
|---|---|---|---|---|
| REFACTOR-001 | high | 5 | 5.0..5.9 | Stubs JPA → REST clients |
| REFACTOR-002 | high | 3 + 5 | 3.1 + 5.0 | shared-api-error |
| REFACTOR-003 | high | 3 + 5 | 3.2 + 5.0 | shared-domain-enums |
| REFACTOR-004 | medium | 5 | 5.6 | Endpoint REST co-organizer-service /check |
| REFACTOR-005 | medium | 3 + 5 | 3.8 + 5.0 | shared-domain-projections.computeAvailableSpots |
| REFACTOR-006 | medium | 3 + 5 | 3.7 + 5.0 | shared-domain-dtos |
| REFACTOR-007 | medium | (S9+) | — | EventService.getAll JPQL → Criteria API ; à différer (impact tests) |
| REFACTOR-008 | medium | (S9+) | — | EventService split God-object ; à différer |
| REFACTOR-009 | medium | 5 | 5.8 | Anti-oracle ISSUE-92 centralisé |
| REFACTOR-010 | medium | 5 | 5.8 | Anti-oracle ISSUE-93 centralisé |
| REFACTOR-011 | low | 3 + 5 | 3.1 + 5.0 | factory helpers WebApplicationException dans shared-api-error |
| REFACTOR-012 | low | 3 + 5 | 3.6 + 5.0 | shared-platform.ServiceIdentityResource |
| REFACTOR-013 | low | 3 + 5 | 3.8 + 5.0 | shared-domain-projections.resolveUserId |
| REFACTOR-014 | low | 13 | 13.* | Commentaires `<this PR>` substitués (Étape 2.1) + commentaires « replaced at PR 12/13 » nettoyés |
| REFACTOR-015 | low | (acté) | — | findByEventAndUser rename — différer S9+ |
| REFACTOR-016 | medium | 5 | 5.1..5.9 | me-aggregator devient vrai BFF (REST clients vers tous les downstream) |
| REFACTOR-017 | low | 1 | 1.6 (favorite — partiel) | Pattern d'idempotence — partiel S8 ; durcissement S9+ |
| REFACTOR-018 | low | 3 | 3.3 | shared-jaxrs lib créée |

#### Cat 5 — Kafka producers/consumers (9 findings)

| Finding | Sévérité | Étape spec | Sous-étape |
|---|---|---|---|
| KAFKA-001 | BLOCKER | 4 | 4.4 (livraison conjointe avec KAFKA-002) |
| KAFKA-002 | BLOCKER | 4 | 4.4 (idem) |
| KAFKA-003 | HIGH | 4 | 4.1 |
| KAFKA-004 | HIGH | 4 | 4.2 |
| KAFKA-005 | HIGH | 4 | 4.3 |
| KAFKA-006 | MEDIUM | 13 | 13.6 (devops-handoff.md doc) |
| KAFKA-007 | MEDIUM | 3 | 3.5 (shared-kafka-events) |
| KAFKA-008 | MEDIUM | 4 | 4.5 (value.serializer explicite) |
| KAFKA-009 | LOW | 3 | 3.0 (in-memory dans dependencyManagement parent) |

#### Cat 6+7+11 — OpenAPI / Kong / Helm / CI / DevOps (18 findings)

| Finding | Sévérité | Étape spec | Sous-étape |
|---|---|---|---|
| INFRA-001 | high | 1 | (1.X — supprimer doublon openapi /events/{id}/view) |
| INFRA-002 | high | 10 | 10.1 |
| INFRA-003 | medium | (acté) | — Kong jwt non requis spec |
| INFRA-004 | medium | (acté) | — `/duplicate` + `/notifications` paths déjà status « Sprint 7 reporté » dans api-contract.md |
| INFRA-005 | low | (acté) | — Regex Kong /users/[^/]+$ OK chevauchement intentionnel |
| INFRA-006 | medium | 11 | 11.1 |
| INFRA-007 | medium | 12 | 12.4 |
| INFRA-008 | low | (acté) | — notification-service replicas:0 attendu |
| INFRA-009 | low | 11 | 11.2 |
| INFRA-010 | medium | 12 | 12.1 |
| INFRA-011 | medium | 12 | 12.3 |
| INFRA-012 | low | (acté) | — workaround chore(backend) titre PR |
| INFRA-013..018 | low | (RAS) | — déjà OK / propre |

#### Cat 8 — Documentation (24 findings)

| Finding | Sévérité | Étape spec | Sous-étape |
|---|---|---|---|
| DOC-001..006 | high/medium | 2 | 2.6 + 13.1 |
| DOC-007 | medium | 2 | 2.7 |
| DOC-008 | medium | 2 | 2.7 + 13.2 |
| DOC-009 | high | 2 | 2.8 + 13.3 |
| DOC-010 | high | 2 | 2.8 + 13.3 |
| DOC-011 | medium | 2 | 2.9 + 13.4 |
| DOC-012 | low | 2 | 2.9 + 13.4 |
| DOC-013 | critical | 2 | 2.1 |
| DOC-014 | high | 2 | 2.2 |
| DOC-015 | high | 2 | 2.3 |
| DOC-016 | medium | 2 | 2.4 |
| DOC-017 | high | 2 | 2.10 + 13.8 |
| DOC-018 | low | 2 | 2.10 + 13.8 |
| DOC-019 | medium | 2 | 2.11 + 13.9 |
| DOC-020 | critical | 2 | 2.5 |
| DOC-021 | high | 2 | 2.5 |
| DOC-022 | critical | 2 + 13 | 2.12 + 13.10 |
| DOC-023 | medium | 13 | 13.10 |
| DOC-024 | medium | 13 | 13.10 |

#### Cat 9 — Sécurité (4 findings)

| Finding | Sévérité | Étape spec | Sous-étape |
|---|---|---|---|
| SEC-001 | low | 5 | 5.8 (admin bypass getPublicProfile aligné — Décision M) |
| SEC-002 | low | 5 | 5.8 (cascade SCRUM-136 centralisée — Décision L) |
| SEC-003 | low | 5 | 5.8 (admin bypass cancel/restore aligné — Décision M) |
| SEC-004 | low | 1 | 1.3 |

#### Cat 10 — Build hygiene (5 findings)

| Finding | Sévérité | Étape spec | Sous-étape |
|---|---|---|---|
| HYGIENE-001 | medium | 1 | 1.2 |
| HYGIENE-002 | low | 1 | 1.8 |
| HYGIENE-003 | low | 13 | 13.6 (notification-service formalisé) |
| HYGIENE-004 | low | 4 | 4.4 (TODO retiré dans même commit que KAFKA-001) |
| HYGIENE-005 | medium | 12 | 12.3 |

**Total findings traités** : 132/132. Aucun finding « ignoré silencieusement » ; les findings explicitement déférés S9+ sont actés dans `devops-handoff.md`.

### Annexe B — Plan de bascule des tests legacy par service

**Source legacy** : `git show 41074e9:backend/services/legacy-monolith/src/test/java/`

Pour chaque service, structure cible et fichiers legacy à porter :

| Service | Path destination tests | Fichiers legacy à porter (path source via `git show 41074e9:`) | Mocks à créer (via WireMock ou `@InjectMock`) |
|---|---|---|---|
| event-service | `backend/services/event-service/src/test/java/ch/unige/events/event/...` | `service/EventServiceCoverageTest.java`, `resource/EventResourceTest.java`, `resource/AdminEventResourceTest.java`, `resource/EventSearchResourceTest.java`, `util/RecurrenceGeneratorTest.java`, `entity/EventTest.java`, `dto/EventDTOTest.java` | UserServiceClient, AttendanceServiceClient, FavoriteServiceClient, EventViewServiceClient, EventCoOrganizerServiceClient |
| user-service | `backend/services/user-service/src/test/java/ch/unige/events/user/...` | `service/UserServiceCoverageTest.java`, `resource/UserResourceTest.java`, `dto/UserPublicResponseTest.java`, `dto/UpdateProfileRequestTest.java`, `entity/UserTest.java` (+ image upload S3 tests via MockS3 ou Testcontainers MinIO) | FollowServiceClient |
| follow-service | `backend/services/follow-service/src/test/java/ch/unige/events/follow/...` | `service/FollowServiceCoverageTest.java`, `resource/FollowResourceTest.java`, `resource/FollowRequestResourceTest.java`, `dto/FollowDTOTest.java`, `entity/FollowTest.java` | UserServiceClient |
| comment-service | `backend/services/comment-service/src/test/java/ch/unige/events/comment/...` | `service/CommentServiceCoverageTest.java`, `resource/CommentResourceTest.java`, `resource/CommentDirectResourceTest.java`, `entity/CommentTest.java`, `dto/CommentDTOTest.java` | EventServiceClient, UserServiceClient, EventCoOrganizerServiceClient |
| co-organizer-service | `backend/services/co-organizer-service/src/test/java/ch/unige/events/coorganizer/...` | `service/EventCoOrganizerServiceCoverageTest.java`, `resource/EventCoOrganizerResourceTest.java` | EventServiceClient, UserServiceClient, AttendanceServiceClient |
| attendance-service | `backend/services/attendance-service/src/test/java/ch/unige/events/attendance/...` | `service/AttendanceServiceCoverageTest.java`, `resource/AttendanceResourceTest.java`, `dto/AttendanceDTOTest.java` | EventServiceClient, UserServiceClient, EventCoOrganizerServiceClient |
| report-service | `backend/services/report-service/src/test/java/ch/unige/events/report/...` | `service/ReportServiceCoverageTest.java`, `service/ModerationCleanupServiceTest.java`, `service/ModerationCleanupCoverageTest.java`, `resource/AdminReportResourceTest.java` | EventServiceClient, UserServiceClient, EventCoOrganizerServiceClient |
| favorite-service | `backend/services/favorite-service/src/test/java/ch/unige/events/favorite/...` | `service/FavoriteServiceCoverageTest.java`, `resource/FavoriteResourceTest.java` | EventServiceClient, UserServiceClient, AttendanceServiceClient |
| view-service | `backend/services/view-service/src/test/java/ch/unige/events/view/...` | `service/EventViewServiceCoverageTest.java`, `resource/EventViewResourceTest.java` | EventServiceClient, UserServiceClient |
| stats-service | `backend/services/stats-service/src/test/java/ch/unige/events/stats/...` | `service/EventStatsServiceCoverageTest.java`, `resource/EventStatsResourceTest.java` | EventServiceClient, UserServiceClient, AttendanceServiceClient, FavoriteServiceClient, EventViewServiceClient, EventCoOrganizerServiceClient |
| share-service | `backend/services/share-service/src/test/java/ch/unige/events/share/...` | `service/ShareServiceCoverageTest.java`, `resource/RedirectResourceTest.java`, `resource/ShareResourceTest.java` | EventServiceClient |
| calendar-service | `backend/services/calendar-service/src/test/java/ch/unige/events/calendar/...` | `service/CalendarServiceCoverageTest.java`, `util/IcsBuilderTest.java`, `resource/CalendarResourceTest.java`, `resource/UserCalendarTokenResourceTest.java` | UserServiceClient, EventServiceClient, FavoriteServiceClient, AttendanceServiceClient |
| me-aggregator-service | `backend/services/me-aggregator-service/src/test/java/ch/unige/events/meaggregator/...` | (très peu de legacy tests directs ; service nouveau) — tester avec WireMock pour fan-out | EventServiceClient, UserServiceClient |

**Pattern d'adaptation par fichier** :
1. Identifier les imports `ch.unige.events.entity.<X>` → remplacer par les références entity locale du service.
2. Identifier les références `<X>Stub.findByYyy(...)` → remplacer par mock du REST client correspondant.
3. Identifier les références à `<X>Service` direct (cross-service) → remplacer par mock du REST client.
4. Conserver les noms de méthodes test exactement — les sentinels doivent ressortir verts par nom.
5. Si un test dépend d'un comportement supprimé (ex. `EventStub` write côté report → events.banned via Kafka), le re-écrire pour tester le nouveau path Kafka via in-memory connector.

### Annexe C — REST clients à créer (récap consolidé final)

(Voir tableau « Tableau des REST clients » dans la section Architecture cible — ~35 lignes au total. Utilisable comme checklist de l'Étape 5.)

### Annexe D — Helm files à modifier

| Fichier | Action | Étape |
|---|---|---|
| `k8s/chart/templates/kong/configmap-routes.yaml` | Ajouter plugins `rate-limiting` par-route (3 buckets) | 10.1 |
| `k8s/chart/templates/<svc>-service/deployment.yaml` (×13) | Ajouter `livenessProbe` | 11.1 |
| `k8s/chart/templates/ingress/ingress.yaml` | Mettre à jour commentaire (Kong DB-less) | 11.2 |
| `k8s/chart/values.yaml` | Renommer `image.api.tag` → `image.tag` | 12.4 |
| `k8s/chart/values-preview.yaml` | Idem | 12.4 |
| `k8s/chart/templates/<svc>-service/deployment.yaml` (×14) | Référencer `image.tag` au lieu de `image.api.tag` | 12.4 |
| `k8s/chart/templates/kafka/statefulset.yaml` | Idem (annotation release-sha) | 12.4 |
| `.github/workflows/build.yml` | Refonte matrix per-service | 12.1 |
| `.github/workflows/deploy.yml` | `--set image.tag=...` au lieu de `image.api.tag` | 12.4 |

### Annexe E — Commandes de référence pour l'exécuteur autonome

#### Build local

```bash
cd /workspace/backend
./mvnw verify -DskipITs                      # 24+ modules, ~3-5 min
./mvnw -pl services/<svc>-service -am verify  # un seul service + ses deps
./mvnw -pl services/shared-rate-limit -am test  # un seul shared lib
```

#### Couverture jacoco

```bash
ls backend/services/<svc>-service/target/jacoco-report/jacoco.xml   # rapport par module
# Parse via xmllint :
xmllint --xpath 'string(/report/counter[@type="LINE"]/@covered)' \
    backend/services/<svc>-service/target/jacoco-report/jacoco.xml
```

#### Git workflow

```bash
git status                                                         # check
git add <ciblé>                                                    # NEVER `git add -A`
git commit -m "<conv>(<scope>): <subject>"
git push origin 'refactor(backend)--migrate-to-microservices'
gh pr checks 158 --watch                                           # ~10-15 min
gh run view <RUN_ID> --log-failed                                  # debug si fail
```

#### Récupération de fichier legacy

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
git diff --shortstat origin/main HEAD -- frontend/   # = 0 lignes
git diff --shortstat origin/main HEAD -- openapi/    # ≤ 32 lignes (Décision Q)
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

Cette spec est l'unique source de vérité pour la complétion. Toute dérive doit être actée dans le commit message + dans `sprint-context.md` (Étape 19) avec justification. La spec assume un exécuteur autonome **bypass-permissions** capable de :
- lire/écrire/supprimer dans le repo,
- lancer `cd backend && ./mvnw verify`,
- lancer `gh` (pr checks watch, pr edit body),
- commit + push sur la branche persistante,
- attendre la CI et retraiter en cas d'échec.

Si une situation imprévue émerge (ex. test legacy impossible à porter parce que le code source a divergé trop), l'exécuteur :
1. acte la déviation dans le commit message + `sprint-context.md`,
2. continue l'étape (n'aborte pas la complétion entière),
3. à la fin, liste tous les écarts dans le PR body final.

**FIN DE SPEC.**
