# ADR-003 — `events.banned` via outbox transactionnel ; les 4 autres topics restent best-effort

| Champ / Field | Valeur / Value |
|---|---|
| Date | 2026-05-10 |
| Status | Accepted |
| Author | Backend / Étape 24.4.1 finalization-pre-merge |
| Review reference | Item A10 (silent-failure-hunter IMP-1) — review consolidée multi-agent PR #158 |
| Related | ADR-001 (`moderation-service` `replicas: 1` strict) |

## Préambule / Preamble

- **FR** — Cet ADR documente le compromis entre durabilité Kafka (pattern
  outbox) et coût d'implémentation pour les 5 publishers
  `AFTER_SUCCESS` du backend UNIGE Events. Décision actée pour la clôture
  de la PR #158 (Étape 24.4.1).
- **EN** — This ADR records the trade-off between Kafka durability
  (outbox pattern) and implementation cost across the 5 `AFTER_SUCCESS`
  publishers in the UNIGE Events backend. Acted at PR #158 closure
  (Étape 24.4.1).

## Context

### FR

Les 5 publishers Kafka du backend sont câblés via le pattern CDI suivant :
le service métier modifie l'état JDBC dans une transaction
`@Transactional`, puis `Event.fire(...)` un événement de domaine. Un
bridge `@Observes(during = AFTER_SUCCESS)` relaie ensuite l'événement
vers Kafka via un `Emitter`.

Le défaut connu (review item A10, silent-failure-hunter IMP-1) :
si Kafka est indisponible au moment du `Emitter.send(...)`, l'événement
est perdu **silencieusement**. Aucun rollback possible — la transaction
JDBC est déjà commitée. Le seul signal opérateur est un log local
(jusqu'à présent en `WARN`, désormais en `ERROR` avec `errorId` dédié).

Les 5 topics ont une criticité différenciée :

| Topic | Criticité | Conséquence d'une perte |
|---|---|---|
| `events.banned` | **Sécurité** | Un Event modéré reste publiquement visible — bug de sécurité fonctionnelle. |
| `events.{published,cancelled,expired}` | UX | Notifications retardées ; DB reste source de vérité pour le statut. |
| `co-organizers.{invited,accepted}` | UX | Co-organisateur non notifié ; visible via UI au prochain refresh. |
| `comments.created` | UX | Notification de commentaire perdue ; le commentaire reste visible côté lecteur. |
| `users.{followed,follow-requested,follow-accepted}` | UX | Compteur de followers / état de demande désynchronisé jusqu'à la prochaine action. |

### EN

The 5 backend Kafka publishers all share the same wiring: the domain
service mutates JDBC state inside a `@Transactional` boundary then
`Event.fire(...)` a domain event. A bridge observing
`AFTER_SUCCESS` relays the event to Kafka via an `Emitter`.

The known defect (review item A10, silent-failure-hunter IMP-1):
if Kafka is unavailable when `Emitter.send(...)` runs, the event is
**silently lost**. No rollback is possible — the JDBC transaction has
already committed. The only operator signal is a local log (previously
`WARN`, now `ERROR` with a dedicated `errorId`).

Topic criticality differs:

| Topic | Criticality | Loss impact |
|---|---|---|
| `events.banned` | **Security** | A moderated Event remains publicly visible — functional security defect. |
| `events.{published,cancelled,expired}` | UX | Delayed notifications ; DB remains source of truth for status. |
| `co-organizers.{invited,accepted}` | UX | Co-organizer not notified ; visible at next UI refresh. |
| `comments.created` | UX | Comment notification lost ; comment itself remains visible to readers. |
| `users.{followed,follow-requested,follow-accepted}` | UX | Follower counter / request state out-of-sync until next action. |

## Decision

### FR

- **`events.banned`** : pattern **outbox transactionnel** via la table
  `event_banned_outbox` (Flyway V18) + `EventBannedOutboxPoller`
  `@Scheduled(every = "10s")`. Le `EventBannedKafkaBridge` ne publie
  plus directement dans Kafka — il persiste une row outbox dans la
  même transaction que le flip `Report.status = REVIEWED`. Le poller
  draine les rows non publiées et marque `published_at` à la première
  ack Kafka. At-least-once delivery garantie tant que la DB tient.
- **Les 4 autres topics** : **best-effort**. `Emitter.send(...)`
  post-commit avec `Log.errorf` portant un `errorId` dédié
  (`[KAFKA_PUBLISH_FAIL_<channel>]`). Pas d'outbox. La DB reste source
  de vérité ; un opérateur peut investiguer via le log et rejouer
  manuellement si nécessaire.

### EN

- **`events.banned`**: **transactional outbox** pattern via the
  `event_banned_outbox` table (Flyway V18) + `EventBannedOutboxPoller`
  `@Scheduled(every = "10s")`. `EventBannedKafkaBridge` no longer
  publishes to Kafka directly — it persists an outbox row in the same
  transaction as the `Report.status = REVIEWED` flip. The poller drains
  unpublished rows and stamps `published_at` on the first Kafka ack.
  At-least-once delivery is guaranteed as long as the DB is alive.
- **The 4 other topics**: **best-effort**. `Emitter.send(...)`
  post-commit with `Log.errorf` carrying a dedicated `errorId`
  (`[KAFKA_PUBLISH_FAIL_<channel>]`). No outbox. The DB remains source
  of truth ; an operator can investigate via the log and replay
  manually if needed.

## Why this is necessary / Pourquoi c'est nécessaire

### FR

- **Outbox sur `events.banned`** : prévient un défaut de sécurité (un
  Event modéré qui reste publiquement visible alors que `event-service`
  n'a jamais reçu le verdict de ban). Coût d'implémentation modéré
  (1 table, 1 poller, ~150 LoC + 3 tests sentinels) ; bénéfice direct
  sur la criticité fonctionnelle la plus élevée du périmètre.
- **Best-effort sur les 4 autres** : implémenter un outbox sur les 5
  topics multiplierait par 5 la dette de schéma (5 tables + 5 pollers
  + 5 jeux de tests + 5 mécanismes de monitoring) sans bénéfice métier
  proportionnel. Pour des transitions UX (notifications retardées,
  listes désynchronisées), la DB reste la source de vérité ; un retry
  manuel admin est acceptable en cas d'incident Kafka long.

### EN

- **Outbox for `events.banned`**: prevents a security defect (a
  moderated Event remaining publicly visible because `event-service`
  never received the ban verdict). Implementation cost is moderate
  (1 table, 1 poller, ~150 LoC + 3 sentinel tests) ; direct benefit on
  the highest-criticality flow in scope.
- **Best-effort for the 4 others**: an outbox across all 5 topics would
  multiply schema debt 5× (5 tables + 5 pollers + 5 test suites +
  5 monitoring hooks) without a proportional business benefit. For UX
  transitions (delayed notifications, out-of-sync lists), the DB is the
  source of truth and a manual admin replay is acceptable during a
  prolonged Kafka outage.

## Alternatives considered / Alternatives considérées

| Alternative | Rejet / Why not |
|---|---|
| Outbox sur les 5 topics / Outbox on all 5 topics | Coût implémentation × 5 sans bénéfice proportionnel (cf. ci-dessus) / 5× cost without proportional benefit (see above). |
| Pas d'outbox du tout, juste log error / No outbox at all, just log error | Insuffisant pour `events.banned` (sécurité fonctionnelle) / insufficient for `events.banned` (functional security). |
| Debezium CDC sur les tables sources / Debezium CDC on source tables | Out of scope S8 — dépendance infra Doppler ; deferred S9+ / out of scope for S8 — Doppler infra dependency, deferred to S9+. |
| Outbox SmallRye Reactive Messaging built-in | Pas encore stable en Quarkus 3.35 ; à réévaluer en 4.x / not yet stable in Quarkus 3.35, reassess in 4.x. |
| Kafka transactions (idempotent producer + transactional read-process-write) | Cluster Kafka local dev/test pas configuré pour exactly-once ; deferred S9+ / local dev/test cluster not configured for exactly-once, deferred to S9+. |

## Consequences / Conséquences

### FR

- **`moderation-service` reste `replicas: 1` strict** (cf. ADR-001).
  `EventBannedOutboxPoller` n'a **pas** de leader election ; deux pods
  draineraient la même row simultanément et publieraient deux fois sur
  Kafka. Si le scaling out devient nécessaire, ajouter Shedlock
  (DB-backed lock) ou un Kubernetes Lease — deferred S9+.
- **Latence** : `events.banned` peut être retardé jusqu'à 10 s
  (période du poller). Acceptable pour une action de modération : le
  ban est immédiatement visible côté `moderation-service` (UI admin) ;
  la propagation aux consumers de `events.banned` (event-service +
  futur notification-service SCRUM-99) attend le prochain tick.
- **Accumulation à monitorer** : la table `event_banned_outbox` peut
  accumuler des rows si Kafka est down longtemps. Surveiller via une
  requête manuelle ou un panel Grafana :
  `SELECT COUNT(*) FROM event_banned_outbox WHERE published_at IS NULL AND attempts > 5`.
  Toute valeur > 0 = incident à investiguer.
- **Best-effort sur les 4 autres topics** : un opérateur n'a pas de
  re-jeu automatique. Le `errorId` dans le log
  (`[KAFKA_PUBLISH_FAIL_<channel>]`) doit être agrégé en alerte
  (SonarCloud + Loki/Grafana). Volume attendu : 0 sauf incident.

### EN

- **`moderation-service` stays `replicas: 1` strict** (see ADR-001).
  `EventBannedOutboxPoller` has **no** leader election ; two pods would
  drain the same row concurrently and double-publish to Kafka. If
  scale-out becomes necessary, add Shedlock (DB-backed lock) or a
  Kubernetes Lease — deferred to S9+.
- **Latency**: `events.banned` may be delayed by up to 10 s (poller
  period). Acceptable for a moderation action: the ban is immediately
  visible inside `moderation-service` (admin UI) ; propagation to
  consumers of `events.banned` (event-service + future
  notification-service SCRUM-99) waits for the next tick.
- **Backlog to monitor**: `event_banned_outbox` may accumulate rows
  during a prolonged Kafka outage. Watch via a manual query or a
  Grafana panel:
  `SELECT COUNT(*) FROM event_banned_outbox WHERE published_at IS NULL AND attempts > 5`.
  Any value > 0 means an incident to investigate.
- **Best-effort on the 4 other topics**: no automatic replay for
  operators. The log `errorId` (`[KAFKA_PUBLISH_FAIL_<channel>]`) must
  be aggregated into an alert (SonarCloud + Loki/Grafana). Expected
  volume: 0 outside of incidents.

## When to revisit / Quand reconsidérer

- Si la latence `events.banned` (10 s poll) devient un blocker UX —
  passer à un poll plus court ou à un push direct via SmallRye outbox.
- Si Kafka est upgradé à un cluster prod-grade avec exactly-once
  semantics (idempotent producer + transactional read-process-write).
- Si Debezium CDC ou l'outbox SmallRye Reactive Messaging built-in
  mûrit en Quarkus 4.x.
- Si un nouveau topic dépasse le seuil de criticité UX (ex. paiement,
  certification académique) — réévaluer si l'outbox doit être étendu.
