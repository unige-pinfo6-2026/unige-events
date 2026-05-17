# DevOps Handoff — Sprint 8 microservices migration

> **Mise à jour 2026-05-14 — post-PR #158 + fixes infra.** PR #158 mergée à `ad6d422f`.
> Fixes infra additionnels post-merge livrés par DevOps :
> * `f4b5968e` — **DB-per-service activé** (5 Postgres dédiés) ; **notification-service
>   activé** `replicas: 1` ; strategy `RollingUpdate maxUnavailable:0 maxSurge:1`
>   sur les 5 services.
> * `dd8ca635` — fix outbox sequence name mismatch (moderation) + memory tuning
>   event-service.
> * `60991692` — `memory: 512Mi` sur les 5 services pour éviter OOMKilled.
>
> Conséquences : l'**item « DB-per-schema S9+ »** historiquement listé dans ce handoff
> est **livré** (DB-per-service est l'isolation effective). L'**item notification-service
> SCRUM-99** est résolu (service actif, peut accueillir des consumers Kafka quand SCRUM-99
> sera priorisé).

> Document formel de transition entre l'équipe backend et l'équipe
> DevOps après la complétion de PR #158
> ([branche `refactor(backend)--migrate-to-microservices`](https://github.com/unige-pinfo6-2026/unige-events/pull/158)).
>
> Source de vérité originale : [`../../specs_archives/specs_claude/specs_microservices_migration_completion.md`](../../specs_archives/specs_claude/specs_microservices_migration_completion.md), Décision V.
>
> Source de vérité finale : [`../../specs_archives/specs_claude/specs_pr158_finalization_complete.md`](../../specs_archives/specs_claude/specs_pr158_finalization_complete.md) — l'audit final a clôturé tous les findings backend ; ce handoff a été réduit aux **7 items machine PINFO** explicitement hors scope code.

## TL;DR — état post-finalization-complete

La PR #158 livre **côté code** un état entièrement post-migration et post-finalization, avec la totalité des findings de l'audit final résolus :

* **5 services métiers** Quarkus (event, user, engagement, moderation, notification — tous actifs depuis `f4b5968e`) + **10 shared libs** sous `backend/shared/<lib>` = **15 modules leaf** dans le reactor (post-refactor `fab270e0`, drop `contract-tests` et `e2e`).
* **Migrations Flyway redistribuées par service propriétaire** sur sa Postgres dédiée (`postgres-<svc>`) — fresh deploys bootstrappent le schéma sans intervention manuelle. Plus de schéma `public` partagé.
* **0 stub JPA cross-service** ; **3 REST clients** avec resilience complète couvrant **8 hops cross-service**.
* Cascade SCRUM-136 + anti-oracles ISSUE-92 / ISSUE-93 centralisés ; envelope `ApiErrorResponse` annotée `@Schema` (Étape 4.8).
* Capacity gating sécurisé par `pg_advisory_xact_lock` (Étape 2.1 Décision B).
* `FavoriteService.addFavorite` idempotent sous concurrent double-tap (Étape 2.2).
* `EventService.delete` purge `EventCoOrganizer` (Étape 2.3).
* `MdcKafkaProducer/ConsumerInterceptor` propagent `X-Request-ID` cross-Kafka (Étape 3.1 Décision D — closes KAFKA-002).
* `InternalTokenFilter` + `@Internal` annotation sur les endpoints internes + Kong strip (Étape 3.2 Décision C — closes SEC-002-bis).
* TZ=Europe/Zurich pinné sur les 6 Deployments + EventTzSmokeTest (Étape 4.5 Décision F).
* ADR-001 ModerationCleanupJob replicas:1 strict (Étape 4.6).
* PodDisruptionBudget Kong gated prod replicas≥2 (Étape 4.13).
* Sentinels SCRUM-138/139/144/147 sur 4/4 services métiers (`ModerationDomainSentinelsTest` 8-méthodes ajouté Étape 1.3 Décision H).
* `EnumParamConverterProvider` générique (Étape 5.3) — invalid enum → 400 plutôt que 404.
* Frontend invariant `git diff --shortstat origin/main HEAD -- frontend/` = 0. OpenAPI invariant idem.

**Côté infra**, **7 items machine PINFO** restent à acter par DevOps. Ils sont **explicitement hors scope code** (cf. spec finalization-complete § Frontière DevOps) — le backend a livré sa moitié quand applicable.

---

## 1. Cluster Kafka prod-grade (RF=3, partitions ≥ 3, ISR ≥ 2, durabilité acks=all)

**Statut backend** : ✅ Helm chart single-broker KRaft livré ; producteurs et consommateurs câblés ; tracing `X-Request-ID` propagé via `MdcKafkaProducer/ConsumerInterceptor` (Étape 3.1).

**Action attendue côté DevOps** :
* Provisionner un cluster Kafka **3 brokers** minimum, KRaft (pas de Zookeeper).
* Topics existants : `events.{published,cancelled,expired,banned}`, `comments.created`, `co-organizers.{invited,accepted}`, `users.{followed,follow-requested,follow-accepted}` — tous re-créer en prod avec **partitions ≥ 3** et **replication factor = 3**, **ISR ≥ 2** côté broker config (`min.insync.replicas=2`).
* Producers existants utilisent `acks=all` côté Quarkus (vérifier `application.properties` post-handoff). Si non, ajouter `mp.messaging.outgoing.<channel>.acks=all` sur les 4 services métiers.
* **NB** : `MdcKafkaProducerInterceptor` (post-Étape 3.1) doit fonctionner contre le cluster prod sans config additionnelle — il s'attache via `interceptor.classes` et n'a pas de tunables.

## 2. Certificats TLS + DNS production

**Statut backend** : ✅ Helm chart prêt à recevoir les secrets via `envFrom: secretRef`.

**Action attendue côté DevOps** :
* Provisionner / valider le DNS `pinfo6.p-info.net` + `*.pinfo6.p-info.net`.
* Provisionner les certificats TLS via cert-manager (Let's Encrypt prod, pas staging).
* Configurer l'Ingress / Cloudflare Tunnel pour route HTTPS → Kong upstream.
* Vérifier que les origins CORS Kong (`k8s/chart/templates/kong/configmap-routes.yaml`) couvrent le DNS final.

## 3. Doppler secrets — toutes les variables d'env runtime

**Statut backend** : ✅ Aucun default bidon dans le code (SEC-004 closé). Toute variable absente lève au démarrage Quarkus.

**Action attendue côté DevOps** : provisionner dans Doppler (par environnement) :
* `DB_USER`, `DB_PASSWORD`, `DB_HOST`, `DB_PORT`, `DB_NAME`
* `OIDC_AUTH_SERVER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_AUDIENCE`, `OIDC_ROLE_NAMESPACE`
* `KAFKA_BOOTSTRAP_SERVERS`
* `S3_REGION`, `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_URL`, `S3_BUCKET`
* `EVENT_SERVICE_URL`, `USER_SERVICE_URL`, `ENGAGEMENT_SERVICE_URL` (URLs cluster-internes des REST clients).
* **`INTERNAL_TOKEN`** (post-Étape 3.2 Décision C) — secret partagé par les 4 services métiers pour valider le header `X-Internal-Token` sur les endpoints `@Internal`. **Doit être identique sur les 4 services**, généré aléatoirement (≥ 32 octets) et roulé périodiquement.
* `MODERATION_AUTO_HIDE_THRESHOLD` (default 5).
* `FRONTEND_URL` (URL publique pour les share URLs).

## 4. SonarCloud quality gate — exception sur le « new code » du code migré

**Statut backend** : ✅ Aggregation Option B livrée (Étape 22 + Sonar specs Étape 1.x). Le projet `unige-events-backend` reçoit les scans agrégés des 17 modules.

**Action attendue côté DevOps** : configurer côté SonarCloud une **exception sur la métrique « Coverage on New Code »** pour la PR #158 et toute PR de la branche `refactor(backend)--migrate-to-microservices`. Le code « migré » (services extraits depuis legacy-monolith) est détecté comme « new code » bien qu'il s'agisse d'un déplacement, faussant la métrique. Soit :
* Marquer manuellement la PR #158 comme « not new code » (admin SonarCloud).
* Ou configurer la « New Code Definition » sur une référence antérieure à la migration (option « Specific Date » → date pré-PR #158).
* Ou désactiver temporairement la quality gate stricte sur cette PR.

Tous les autres seuils (Coverage on Overall Code ≥ 80 %, Maintainability A, Security A, etc.) doivent rester actifs.

## 5. NetworkPolicies Kubernetes — complément à SEC-002-bis

**Statut backend** : ✅ `InternalTokenFilter` + `@Internal` annotation + Kong strip livrés (Étape 3.2 Décision C). La sécurité défensive ne dépend plus uniquement du périmètre K8s.

**Action attendue côté DevOps** : ajouter dans `k8s/chart/templates/networkpolicies/` (à créer) des règles qui ferment l'ingress sur le port 8080 des 4 services métiers sauf depuis les pods avec label `app in (event-service, user-service, moderation-service, engagement-service, kong)`. Defense-in-depth — `INTERNAL_TOKEN` reste la barrière principale, NetworkPolicies est la défense périmétrique complémentaire.

## 6. Cleanup GHCR PR-tagged images

**Statut backend** : ✅ CI publie 5 images backend × tag `pr-N` + `latest` à chaque push. Pas de cleanup automatique aujourd'hui — coût stockage GHCR notable à terme.

**Action attendue côté DevOps** : ajouter un job CI scheduled (cron weekly) qui :
* Liste tous les tags `pr-*` sur `ghcr.io/unige-pinfo6-2026/unige-events-{event,user,engagement,moderation,notification}-service`.
* Supprime ceux dont la PR référencée est closed/merged depuis ≥ 7 jours.
* Garde tous les tags `main`, `latest`, semver, et les 5 derniers tags `pr-*` actifs.

Pattern de référence : [GitHub Actions container-cleanup](https://github.com/snok/container-retention-policy) ou équivalent.

## 7bis. SCRUM-99 follow-ups DevOps

Items DevOps additionnels nés de la livraison SCRUM-99 phase 1 (2026-05-17,
notifications infra + duplicate). Tous **hors scope code** de la PR
`feature/scrum-99-notifications-and-duplicate`.

### 7bis.1 helm-smoke CI job

**Décision V** de la spec : ajouter un step CI `helm template chart/`
(avec `--set postgres.shared=true` puis `false`) qui valide que les
templates Helm compilent avant de tenter un `helm upgrade` en preview-
deploy. Coût trivial (~5 lignes), gain élevé (feedback loop avant
deploy). **Non câblé** dans la PR phase 1 — le dev container ne dispose
pas de la CLI helm, et le workflow CI existant n'était pas suffisamment
isolé pour qu'un agent puisse y ajouter un job avec confiance. À câbler
côté DevOps dans `.github/workflows/build.yml`.

### 7bis.2 Rétention des notifications

L'entité `Notification` (table `notifications` dans `postgres-notification`)
n'a aucune politique de rétention en phase 1 — chaque notif persiste
jusqu'à suppression manuelle (jamais déclenchée). Recommandation S10+ :
job CRON quotidien purgeant les rows `read = true` antérieures à 90 jours.
Volume estimé : pour ~500 events/an avec ~30 attendees moyens → ~15 000
notifs/an ; sur 5 ans ≈ 75 000 rows. Pas urgent, pas critique, mais à
acter avant la mise en production grand-publique.

### 7bis.3 Partitioning de `notifications` si > 10M rows

Si le projet venait à scale (e.g. utilisé par d'autres associations
étudiantes au-delà de l'UNIGE), partitionner `notifications` par mois
(`PARTITION BY RANGE (created_at)`) éviterait les seq scans coûteux sur
les requêtes par `user_id`. Pré-requis : seuil ≥ 10M rows. **Non urgent
S9**.

### 7bis.4 Topics Kafka ajoutés (×2)

Le job `kafka-topics-init` du chart Helm (`templates/kafka/topics-init.yaml`)
provisionne maintenant 12 topics (10 historiques + `events.updated` +
`attendances.created`). Idempotence garantie par `--if-not-exists`.
Aucune action DevOps requise — le job re-tourne automatiquement sur
chaque `helm upgrade` (hook `post-install,post-upgrade`).

### 7bis.5 Kong routes ajoutées (×4)

Routes ajoutées dans `templates/kong/configmap-routes.yaml` :
- `event-service.events-duplicate` (`~/api/events/(?:\d+)/duplicate$`)
- `notification-service.notifications-read-all`,
  `notification-service.notification-mark-read`,
  `notification-service.user-notifications` (3 routes — ordre
  most-specific-first pour éviter le prefix shadowing).

Aucune action DevOps requise au-delà du `helm upgrade` normal.

---

## 7. Pact provider verification job harness

**Statut backend** : ✅ 5 pacts JSON consumer-driven dans `contract-tests/target/pacts/` (engagement-event ×2, moderation-event ×1, user-event-bulk ×1, event-engagement-bulk ×1). Aucun job CI ne les vérifie côté provider aujourd'hui.

**Action attendue côté DevOps** : ajouter un job CI Pact provider verification qui :
* Démarre un container du service provider (event-service, user-service, etc.) en mode test profile (`%test.quarkus.oidc.enabled=false`, etc.).
* Lance la verification Pact (`mvn pact:verify` ou équivalent JVM Pact provider plugin) contre le contract local.
* Bloque le merge si une assertion contractuelle échoue.

Provider states (`@State`) sont déjà exprimés dans les pacts ; le harness côté infra doit fournir l'orchestration du DB seed + JWT pré-staged.

---

## Operational invariants (à respecter en prod)

* **moderation-service replicas: 1 strict** (cf. [`adr/ADR-001-moderation-cleanup-replicas-strict.md`](adr/ADR-001-moderation-cleanup-replicas-strict.md)). Ne pas scaler avant qu'un mécanisme de leader-election soit câblé. Sinon le ModerationCleanupJob duplique chaque entrée de l'audit trail Kafka `events.banned`.
* **TZ=Europe/Zurich** sur tous les Deployments (Étape 4.5 Décision F). Ne pas retirer `TZ` ni `JAVA_TOOL_OPTIONS=-Duser.timezone=Europe/Zurich` ; les events « du 12 mai 14h » deviendraient invisibles aux searches en cas d'UTC default.
* **`INTERNAL_TOKEN` identique sur les 4 services** (Étape 3.2 Décision C). Toute désynchronisation casse les hops service-to-service vers les endpoints `@Internal`.
* **Frontend invariant**: `git diff --shortstat origin/main HEAD -- frontend/` doit rester à 0 sur cette branche.
* **OpenAPI invariant**: `git diff --shortstat origin/main HEAD -- openapi/` doit rester à 0 sur cette branche.

---

## Référence audit final

- 35 findings audit final (`audit_pr158_migration_microservices_final.md`) → tous adressés post-Étape 9.4 (cf. spec `specs_pr158_finalization_complete.md` Annexe A).
- 3 findings non-actionnables (MINOR-008/009 process-only commit hygiene rétroactif ; MINOR-012 frontend searchApi.ts hors scope invariant frontend).

Pour le détail technique de chaque résolution, voir le commit log de la branche post-`3cc32ef8` ou le récap `sprint-context.md` § Étape 23.
