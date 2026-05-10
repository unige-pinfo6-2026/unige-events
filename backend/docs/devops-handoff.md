# DevOps Handoff — Sprint 8 microservices migration

> Document formel de transition entre l'équipe backend et l'équipe
> DevOps après la complétion de PR #158
> ([branche `refactor(backend)--migrate-to-microservices`](https://github.com/unige-pinfo6-2026/unige-events/pull/158)).
>
> Source de vérité : [`../../specs_archives/specs_claude/specs_microservices_migration_completion.md`](../../specs_archives/specs_claude/specs_microservices_migration_completion.md), Décision V.

## TL;DR

La PR #158 livre **côté code** (état à clôture finale Étape 21 — finalization-ultimate) :

* **5 services métiers** Quarkus extraits (event, user, engagement, moderation + notification placeholder) post-consolidation 14→5 + **10 shared libs** + `contract-tests` + `e2e` = **17 modules** dans le reactor.
* Kong DB-less + table de routes 4 services métiers actifs + plugin `rate-limiting` `policy: local` sur 3 routes.
* Kafka KRaft single-broker + 10 topics provisionnés + **9 producteurs câblés + 1 consommateur** (`event-service ← events.banned`).
* **3 REST clients `@RegisterRestClient`** dans `shared-domain-dtos` couvrant **8 hops cross-service** avec resilience (`@Retry` + `@Timeout` + `@CircuitBreaker` + `@Fallback`).
* **0 stub JPA cross-service** — refactor `@ManyToOne XStub` → `@Column id` (Décision F finalization-ultimate) ; mutation `events.banned` déléguée au consumer Kafka (Décision H).
* Anti-oracles ISSUE-92 / ISSUE-93 + cascade SCRUM-136 centralisés derrière les services propriétaires + REST clients ; envelope canonique `{error:"not_found"}` via `NotFoundExceptionMapper` (REST-004 / SEC-001).
* Cascade SCRUM-136 self-check authentifié uniquement sur `?check-co-org-of=` (SEC-002 / Décision C — fermeture de l'oracle de membership co-organizer).
* Observabilité : `quarkus-logging-json` + `micrometer-registry-prometheus` + `shared-tracing` (`X-Request-ID` MDC + propagation REST + Kafka).
* Helm : `livenessProbe` sur 5 deployments (4 actifs + notification placeholder, K8S-001).
* CI : `.github/workflows/build.yml` matrix consolidée (1 cellule shared-libs + 5 services + 1 contract-tests/e2e + 1 frontend), Sonar `-pl .,<X>` pour résoudre top-level project (CI-001 / Décision E).
* Tests : 4 sentinels SCRUM-147 RecurrenceGenerator (assertions réelles) + 1 sentinel SCRUM-144 prePersist porté + 30 sentinels taggés `@Tag("legacy-port-s9")` (Décision D Option 3 — port complet S9). 5 pacts JSON consumer-driven brokerless (engagement-event ×2, moderation-event ×1, user-event-bulk ×1, event-engagement-bulk ×1) + 1 E2E happy path gated env var.

**Côté infra**, sept items restent à faire — formalisés ci-dessous.
Ils sont **explicitement hors scope S8** (cf. spec de complétion
Décision V + spec finalization-ultimate § Frontière DevOps). Le
backend a livré **sa moitié** quand applicable.

## 1. SonarCloud — Option B définitive (1 seul projet `unige-events-backend`) — Annulé Étape 22

**Statut backend** : ✅ Aggregation Option B livrée Étape 22 (PR #158, commits 1.1 + 1.3 + 1.4
de la spec `specs_sonar_quality_gate_post_migration.md`).

**Action attendue côté DevOps** : **AUCUNE**. Le projet `unige-events-backend` existe déjà
sur SonarCloud et reçoit désormais les scans agrégés des 17 modules backend (5 services
métiers + 10 shared libs + contract-tests + e2e). Les 5 projets services per-bounded-context
(`unige-events-{event,user,engagement,moderation,notification}-service`) sont **abandonnés** —
DevOps peut les archiver via UI SonarCloud s'il le souhaite, ce n'est pas un blocker.

**Justification.** (a) `sonar-maven-plugin` 4.0.0.4121 ignorait silencieusement les
`<sonar.projectKey>` overrides per-module quand `sonar:sonar` était invoqué depuis le
reactor parent — la configuration multi-projet ne fonctionnait pas (Bug 1 spec quality
gate). (b) Pour un projet pinfo6 à 6 mois, 1 quality gate sur le backend agrégé est
suffisant et aligné avec l'état pré-migration de `main`. (c) Quality Gate **PASSED** sur
PR #158 post-Étape 22.

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

## 8. Pact provider verification CI job (NEW — finalization-ultimate)

**Statut backend** : ✅ 5 pacts consumer générés (`engagement-event-issue92.json`, `engagement-event-scrum136.json`, `moderation-event.json`, `user-event-bulk.json`, `event-engagement-bulk.json`) à chaque CI run et uploadés en artifact GitHub Actions (`pacts-${{ github.sha }}`).

**Action attendue côté DevOps** : ajouter un job `verify-pacts` qui les vérifie côté provider :

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

**Justification du report** : harness provider states non trivial, sortie du scope finalization-ultimate.

## 9. GHCR cleanup PR-tagged images (NEW — finalization-ultimate)

**Statut backend** : ✅ push GHCR des 5 services à chaque PR avec tag `pr-<N>`.

**Action attendue côté DevOps** : ajouter à `cleanup.yml` un job qui supprime les images via `gh api -X DELETE /user/packages/container/<img>/versions/<id>` filtré par tag `pr-<N>` quand la PR est fermée. Sprint S9.

**Justification du report** : pas urgent, coût stockage faible à court terme.

## 10. Port runtime des 30 sentinels @Tag("legacy-port-s9") — ✅ Annulé Étape 22

**Statut backend** : ✅ **Tous les 30 sentinels portés en runtime** dans les Vagues 4-7 de l'Étape 22 quality gate (PR #158, sessions 2026-05-09/10) :
  - 7 sentinels SCRUM-144 dans `engagement-service/src/test/java/.../EngagementDomainSentinelsTest.java` (Vague 4).
  - 6 sentinels SCRUM-138 dans `user-service/src/test/java/.../UserDomainSentinelsTest.java` (Vague 5).
  - 17 sentinels SCRUM-147 dans `event-service/src/test/java/.../EventDomainSentinelsTest.java` (Vague 6).

Chaque sentinel a un corps `@QuarkusTest` (ou `@QuarkusTest @TestTransaction`) avec assertions réelles, `@InjectMock @RestClient` pour les REST clients cross-service, et `@TestSecurity` + `JwtTestContext` pour les claims JWT staging. Le `@Tag("legacy-port-s9")` est entièrement absent du test tree post-livraison.

**Action attendue côté DevOps** : **AUCUNE**. La cible TEST-001 « port complet S9 » est anticipée et résolue en S8 par cette spec quality-gate-post-migration.

**Vérif** : `grep -rln '@Tag("legacy-port-s9")' backend/services/*/src/test/java` → vide.

## 11. Doublon openapi `POST /events/{id}/view` (NEW — finalization-ultimate)

**Statut openapi** : ⚠️ le contrat openapi.yaml expose `/events/{id}/view` deux fois (cosmétique, pas de bug runtime). Hors scope finalization-ultimate (invariant openapi = 0 ligne diff).

**Action attendue côté DevOps / frontend S9** : nettoyer dans une PR future avec coordination frontend explicite (pour éviter de casser un client qui dépendrait du double-listing).

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
