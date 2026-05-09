# DevOps Handoff — Sprint 8 microservices migration

> Document formel de transition entre l'équipe backend et l'équipe
> DevOps après la complétion de PR #158
> ([branche `refactor(backend)--migrate-to-microservices`](https://github.com/unige-pinfo6-2026/unige-events/pull/158)).
>
> Source de vérité : [`../../specs_archives/specs_claude/specs_microservices_migration_completion.md`](../../specs_archives/specs_claude/specs_microservices_migration_completion.md), Décision V.

## TL;DR

La PR #158 livre **côté code** :

* 13 microservices Quarkus extraits + 10 shared libs.
* Kong DB-less + table de routes complète + plugin `rate-limiting` `policy: local` sur 3 routes.
* Kafka KRaft single-broker + 10 topics provisionnés + **9 producteurs câblés + 1 consommateur** (`event-service ← events.banned`).
* REST clients `@RegisterRestClient` cross-service avec resilience (`@Retry` + `@Timeout` + `@CircuitBreaker` + `@Fallback`) — 35 stubs JPA cross-schéma supprimés.
* Anti-oracles ISSUE-92 / ISSUE-93 + cascade SCRUM-136 centralisés derrière les services propriétaires + REST clients.
* Observabilité : `quarkus-logging-json` + `micrometer-registry-prometheus` + `shared-tracing` (`X-Request-ID` MDC + propagation REST + Kafka).
* Helm : `livenessProbe` ajoutée aux 13 deployments.
* CI : `.github/workflows/build.yml` refondu en `strategy.matrix.service: [...]` + `sonar.projectKey` override par module + suppression du glob `<sonar.coverage.exclusions>services/*-service/**/*</sonar.coverage.exclusions>`.
* Tests : 1818 tests legacy portés ; 35 sentinels SCRUM-138/139/144/147 verts ; 4 pacts + 1 E2E happy path.

**Côté infra**, sept items restent à faire — formalisés ci-dessous.
Ils sont **explicitement hors scope S8** (cf. spec de complétion
Décision V). Le backend a livré **sa moitié** quand applicable.

## 1. Création de 5 SonarCloud projects per-service + 10 shared libs

**Statut backend** : ✅ YAML CI matrix livré (Étape 7 de la spec de finalization) ; sonar.projectKey
per-module livré (Étape 12.2 de la completion-spec).

**Action attendue côté DevOps** :

* Créer manuellement, via la UI SonarCloud, **5 projets services** sous l'organisation `unige-pinfo6-2026` :
  - `unige-events-event-service`
  - `unige-events-user-service`
  - `unige-events-engagement-service`
  - `unige-events-moderation-service`
  - `unige-events-notification-service`
* Plus **10 projets shared libs** : `unige-events-shared-rate-limit`, `unige-events-shared-storage`,
  `unige-events-shared-api-error`, `unige-events-shared-domain-enums`, `unige-events-shared-domain-dtos`,
  `unige-events-shared-domain-projections`, `unige-events-shared-jaxrs`, `unige-events-shared-tracing`,
  `unige-events-shared-kafka-events`, `unige-events-shared-platform`.
* Ajouter au repo GitHub (Settings → Secrets) : `SONAR_TOKEN` (un seul partagé suffit avec SonarCloud).

**Note de transition post-consolidation 14→5** : les anciens SonarCloud projects (`unige-events-{share,view,favorite,calendar,follow,comment,co-organizer,attendance,report,stats,me-aggregator}-service`)
deviennent **orphelins** post-consolidation (Décision A de la spec finalization). DevOps peut les
archiver ou les laisser ; aucun blocker. Aucune CI n'écrit plus dedans.

**Sans cette action**, le workflow CI matrix échoue côté Sonar à la première exécution avec
« project not found » — c'est un blocker DevOps **attendu** documenté ; pas un fail backend.

**Justification du report** : nécessite SonarCloud admin UI (hors scope code).

## 2. Cluster Kafka prod-grade (RF=3, partitions ≥ 3, ISR ≥ 2, durabilité acks=all)

**Statut backend** : ✅ Helm chart single-broker KRaft livré ; 9 producteurs + 1 consommateur câblés.

**Action attendue côté DevOps** :

* Migrer le StatefulSet Kafka (`k8s/chart/templates/kafka/`) vers un cluster ≥ 3 brokers en prod.
* Ajuster les topics : `--replication-factor 3 --partitions 3 --min-insync-replicas 2`.
* Côté code Quarkus, ajouter (par service producteur) `mp.messaging.outgoing.<chan>.acks=all` une fois le cluster en place.

**Justification du report** : hors scope cours pinfo6 (single-broker S8 OK pour démo).

## 3. Schemas-per-service (Flyway physique séparé)

**Statut backend** : ❌ déviation explicite — pas de livraison backend.

**Décision actée** : Décision C de la spec de complétion défère cette étape à S9+. Le bénéfice fonctionnel est **nul** dès que les REST clients (Décision B) suppriment tous les accès JPA cross-service. La défense en profondeur (« même si un dev oublie et utilise un stub, la DB rejette le write ») coûte XL en effort vs un bénéfice essentiellement disciplinaire.

**Action attendue côté DevOps (S9+)** :

* Créer 13 schémas SQL séparés sous le même rôle DB ou par rôle distinct (`<svc>_svc.<table>`).
* `ALTER TABLE ... SET SCHEMA` pour chaque table.
* `GRANT SELECT, INSERT, UPDATE, DELETE` par rôle si RBAC strict.
* Bumper `currentSchema=<svc>_svc` dans la JDBC URL de chaque service.
* Adapter les baselines Flyway de chaque service (`baseline-on-migrate=true` + `baseline-version=17`).

**Justification du report** : XL effort + bénéfice marginal en S8 — ré-évaluer en S9+.

## 4. NetworkPolicies K8s pour isoler le trafic service-to-service

**Statut backend** : N/A — pure ops K8s.

**Action attendue côté DevOps** :

* Définir des `NetworkPolicy` par service qui restreignent les connexions ingress aux seuls services voisins listés dans la table « REST clients » de [`architecture.md`](architecture.md).
* Restriction au minimum : Kong → tous les services ; service-to-service uniquement entre couples consumer-provider documentés.

**Justification du report** : aucune dépendance code, K8s policy pure.

## 5. Domaines / certs prod / Cloudflare tunnel preview

**Statut backend** : N/A.

**Action attendue côté DevOps** :

* Configurer le DNS prod (`unige-events.ch`).
* Provisionner les certs TLS via cert-manager.
* Cloudflared tunnel pour preview env (mode quick OK, déjà setup).

**Justification du report** : pure ops.

## 6. Secrets Doppler (DB_PASSWORD, OIDC_*, S3_*, KAFKA_BOOTSTRAP_SERVERS, FRONTEND_URL, TZ)

**Statut backend** : ✅ defaults bidons retirés en complétion (SEC-004) — fail-fast au boot si une var manque.

**Action attendue côté DevOps** :

* Doppler config push pour `unige-events-pr-N` et `unige-events-prod` :
  - `DB_USER`, `DB_PASSWORD`, `DB_HOST`, `DB_PORT`, `DB_NAME`
  - `OIDC_AUTH_SERVER_URL`, `OIDC_CLIENT_ID`, `OIDC_CLIENT_SECRET`, `OIDC_AUDIENCE`, `OIDC_ROLE_NAMESPACE`
  - `S3_ENDPOINT`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_BUCKET`
  - `KAFKA_BOOTSTRAP_SERVERS`
  - `FRONTEND_URL` (consommé par share-service `RedirectResource`)
  - `TZ=Europe/Zurich` (cf. JavaDoc `EventRequestBase` BUG-014)

**Justification du report** : pure ops, secrets ne vivent pas dans le repo.

## 7. Production-grade Kong (DB-mode, OpenTelemetry, plugin rate-limiting policy=redis cluster-wide)

**Statut backend** : ✅ Kong DB-less + plugin `rate-limiting` `policy: local` livré (Étape 10 de la spec de complétion).

**Action attendue côté DevOps (S9+)** :

* Provisionner Postgres dédié pour Kong (DB-mode permet le hot reload de routes sans rebuild ConfigMap).
* Ajouter le plugin `opentelemetry` (export OTLP vers Tempo / Jaeger / Honeycomb).
* Migrer le plugin `rate-limiting` de `policy: local` vers `policy: redis` (avec un Redis Helm chart) pour un compteur cluster-wide. Sans cela, un attaquant peut tripler son budget en routant sur une autre instance Kong.

**Justification du report** : hors scope cours, DB-less S8 OK.

## Smoke tests recommandés post-deploy preview

Une fois la PR mergée et le preview env déployé :

```bash
# 1. Auth
curl -i https://<preview>/api/users/me                 # → 401
curl -i -H "Authorization: Bearer <jwt>" \
        https://<preview>/api/users/me                 # → 200

# 2. Cross-service smoke
curl -i https://<preview>/api/events                   # → 200 list
curl -i https://<preview>/api/events/1/comments        # → 200 list

# 3. Rate limit
for i in {1..15}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
       -X POST -H "Authorization: Bearer <jwt>" \
       -d '{"title":"...","startDate":"..."}' \
       https://<preview>/api/events
done                                                   # → 11 fois 201, 4 fois 429

# 4. Kafka topics non-vides après quelques actions
kubectl exec -it kafka-0 -- /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic events.published --from-beginning --max-messages 1

# 5. Métriques Prometheus exposées par chaque service
kubectl exec -it event-service-<pod> -- curl -s http://localhost:8080/api/q/metrics | head -20
```

## Liens

* Spec originale : [`specs_archives/specs_claude/specs_microservices_migration.md`](../../specs_archives/specs_claude/specs_microservices_migration.md)
* Audit post-PR-158 : [`specs_archives/audit_pr158_microservices_migration.md`](../../specs_archives/audit_pr158_microservices_migration.md)
* Spec de complétion : [`specs_archives/specs_claude/specs_microservices_migration_completion.md`](../../specs_archives/specs_claude/specs_microservices_migration_completion.md)
* Sprint context : [`sprint-context.md`](sprint-context.md)
* Architecture : [`architecture.md`](architecture.md)
* Internal endpoints : [`internal-endpoints.md`](internal-endpoints.md)
