# Audit PR #158 post-finalisation — 2026-05-09

> **Branche** : `refactor(backend)--migrate-to-microservices`
> **HEAD** : `ec668b91`
> **Build local** : `cd backend && ./mvnw -B -DskipITs verify` → **SUCCESS** sur 17 modules.
> **Méthodologie** : 14 sous-agents en parallèle (Explore + general-purpose), résultats consolidés ici.
> **Auteur** : Claude Code (session audit, 2026-05-09 PM).
> **Statut** : audit read-only, aucune modification de code.

---

## TL;DR

- **52 findings** classés en **12 catégories** (STUB, DUP, REST, TEST, COV, CI, K8S, KAFKA, DOC, SEC, DEP, HYGIENE).
- **6 P0** (bloquants pour le merge ou la production) — tous CI/REST clients/sécurité.
- **18 P1** (à fixer avant le handoff DevOps clean) — couverture, doc drift contradictoire, scope creep.
- **28 P2** (cosmétique / dette douce) — duplicats, commentaires obsolètes, sentinels redondants.
- **Effort fix complet estimé** : ~25-30 commits, ~40-60 heures de travail focalisé.
- **Verdict global** : la PR a **livré la charpente** (consolidation 14→5, REST clients interfaces, scaffold contract-tests + e2e, 4 pacts, CI matrix), mais le **runtime n'est PAS câblé** : aucun consumer n'a `quarkus.rest-client.<svc>.url`, 13 stubs JPA cross-service persistent, et les anti-oracles ne renvoient pas le payload contractuel attendu par les pacts. Le port runtime est mécanique mais nécessaire avant tout merge prod.
- **Bonne nouvelle** : Kafka, Helm/K8s, dépendances POM, hygiène git sont **propres**. La sécurité est OK sur les anti-oracles ISSUE-92/93 + cascade SCRUM-136. Les invariants frontaliers (frontend, openapi) tiennent.

---

## Index des findings

| # | ID | Catégorie | Sévérité | Titre | Effort |
|---|---|---|---|---|---|
| 1 | STUB-001 | JPA stubs | P0 | 13 stubs JPA cross-service présents (cible 0) — wiring REST clients pas câblé | XL |
| 2 | REST-001 | REST clients | P0 | Aucun `quarkus.rest-client.<svc>.url` dans aucun consumer — REST clients morts au runtime | S |
| 3 | REST-002 | REST clients | P0 | `GET /users/{id}/attendances` n'existe pas côté provider engagement-service | S |
| 4 | REST-003 | REST clients | P1 | `UserService.getPublicProfile` n'a pas le bypass admin (ISSUE-93 incomplet) | S |
| 5 | REST-004 | REST clients | P1 | Bare `NotFoundException` ne produit pas `{"error":"not_found"}` — pact `EngagementEventIssue92PactTest` cassé | S |
| 6 | CI-001 | CI/Sonar | P0 | `sonar:sonar` plante avec « Maven session does not declare a top level project » sur les 5 cellules services | S |
| 7 | CI-002 | CI/Sonar | P1 | Les 10 cellules `Build Shared Lib` sont du gaspillage post-Option B (clobber + ~25 min CI/run) | M |
| 8 | CI-003 | CI/Sonar | P1 | `continue-on-error: true` masque CI-001 même après création des projets DevOps | XS |
| 9 | CI-004 | CI/Sonar | P1 | Incohérence Option B : commit `39c6f195` ajoute `sonar.projectKey` à 2 shared libs sous Option B (devraient être absents) | XS |
| 10 | CI-005 | CI/Sonar | P2 | Container image `push=true` sur PR — fuite GHCR potentielle sur PR abandonnées | S |
| 11 | CI-006 | CI/Sonar | P2 | `build-contract-and-e2e` utilise `verify` (pas `install`) — incohérent avec ec668b91 | XS |
| 12 | CI-007 | CI/Sonar | P2 | Pact provider verification job absent | M |
| 13 | CI-008 | CI/Sonar | P2 | Spec décrit `pacts/` sans `target/` — mismatch cosmétique avec implémentation | XS |
| 14 | DUP-001 | Duplicats locaux | P1 | `ApiErrorResponse.java` × 4 copies locales malgré shared-api-error | XS |
| 15 | DUP-002 | Duplicats locaux | P1 | `ServiceIdentityResource.java` × 5 copies locales malgré shared-platform | S |
| 16 | DUP-003 | Duplicats locaux | P1 | `Timeframe.java` × 2 copies locales malgré shared-jaxrs | XS |
| 17 | DUP-004 | Duplicats locaux | P1 | 21 enums locaux (EventStatus, EventCategory, …) malgré shared-domain-enums | M |
| 18 | DUP-005 | Duplicats locaux | P1 | DTOs cross-projetés × 7 copies (EventDTO, UserPublicResponse, AttendanceDTO, …) | L |
| 19 | DUP-006 | Duplicats locaux | P2 | Helpers dupliqués (`computeAvailableSpots`, `resolveUserId`) × 5 copies | M |
| 20 | TEST-001 | Tests | P1 | 31/35 sentinels SCRUM sont des `@Test` à corps vide (placeholders) | XL |
| 21 | TEST-002 | Tests | P1 | Pact `AttendanceSummary` (endpoint interne #1) absent — couverture pact 3/4 endpoints | S |
| 22 | TEST-003 | Tests | P2 | `EventLifecycleKafkaBridgeTest` manquant (4/5 bridges testés) | S |
| 23 | TEST-004 | Tests | P2 | `ContractTestsScaffoldTest` + `E2EScaffoldTest` redondants (`assertNotNull(getClass().getName())`) | XS |
| 24 | TEST-005 | Tests | P2 | Pas d'`UserAttendancesInternalResource` dans engagement-service alors que internal-endpoints.md le liste actif | S |
| 25 | COV-001 | Couverture | P1 | Services métiers à 5–17% L (cible 80%) — 31/35 sentinels vides + 1818 tests legacy non portés | XL |
| 26 | COV-002 | Couverture | P2 | `shared-domain-dtos` à 63.2% L (cible 95%) — `EventDTO`/`AttendanceDTO`/`EventCoOrganizerDTO` non testés | S |
| 27 | K8S-001 | Helm/K8s | P1 | `notification-service` deployment.yaml manque `livenessProbe` (asymétrique vs 4 actifs) | XS |
| 28 | KAFKA-001 | Kafka | P2 | `EventLifecycleKafkaBridgeTest` manquant (4/5 bridges testés) — voir aussi TEST-003 | S |
| 29 | DOC-001 | Doc drift | P0 | `devops-handoff.md` TL;DR dit « 35 stubs JPA cross-schéma supprimés » → faux (13 persistent) | XS |
| 30 | DOC-002 | Doc drift | P1 | `architecture.md` topologie K8s mentionne « 13 microservices Quarkus » et matrix `[share, view, …]` | S |
| 31 | DOC-003 | Doc drift | P1 | `architecture.md` flux comment route via `comment-service` + `co-organizer-service` (services dissous) | S |
| 32 | DOC-004 | Doc drift | P1 | `microservices-migration-roadmap.md` décrit la migration comme TODO (« 14 squelettes », « 16 PRs follow-up ») | M |
| 33 | DOC-005 | Doc drift | P1 | `AGENTS.md` racine + `backend/AGENTS.md` mentionnent « 13 microservices » sans consolidation | S |
| 34 | DOC-006 | Doc drift | P1 | `api-contract.md` table topologie + endpoints listent les 13 services dissous | M |
| 35 | DOC-007 | Doc drift | P1 | Multiples docs réfèrent « 13/15 SonarCloud projects » alors qu'Option B = 5 services | S |
| 36 | DOC-008 | Doc drift | P1 | `sprint-context.md` ligne 152 vs ligne 589 : contradiction sur les stubs (`✅ supprimés` vs `❌ encore présents`) | XS |
| 37 | DOC-009 | Doc drift | P2 | `data-model.md` dit « émis par report-service » (service renommé moderation-service en 2.1.2) | XS |
| 38 | DOC-010 | Doc drift | P2 | `internal-endpoints.md` endpoint #4 décrit comme « existant publiquement » — endpoint distinct, à clarifier | XS |
| 39 | DOC-011 | Doc drift | P2 | Commentaires JavaDoc référencent les services dissous dans 6+ classes Java | M |
| 40 | SEC-001 | Sécurité | P0 | Anti-oracle ISSUE-92 retourne 404 sans body — viole pact + spec | S |
| 41 | SEC-002 | Sécurité | P1 | `?check-co-org-of=` est `@PermitAll` et exposé via Kong → oracle de membership co-organizer enumérable anonymement | M |
| 42 | SEC-003 | Sécurité | P2 | Cascade SCRUM-136 inline dans `CommentService` (dette de drift à terme) | S |
| 43 | DEP-001 | Dépendances | P1 | `notification-service` n'a pas `quarkus-jacoco` → coverage = N/A (asymétrique) | XS |
| 44 | DEP-002 | Dépendances | P2 | `engagement-service.UserAttendancesResource` cible un endpoint inexistant côté provider — voir TEST-005 / REST-002 | S |
| 45 | HYGIENE-001 | Git/commits | P1 | 19 commits sans référence Étape/SPEC/SCRUM/BUG | XS |
| 46 | HYGIENE-002 | Git/commits | P1 | 16 commits `(infra)` / `(ci)` mélangés avec scope `(backend)` — scope creep | XS |
| 47 | HYGIENE-003 | Git/commits | P2 | Commit `66acd316` lacks conventional prefix (`Add microservices migration spec`) | XS |
| 48 | HYGIENE-004 | Git/commits | P2 | Doc/code ratio 29.7% — proche du seuil 30% | — |
| 49 | TODO-001 | TODOs | P1 | `AttendanceService:38-39` JavaDoc `"will become a REST call to co-organizer-service"` — code livré sans REST | XS |
| 50 | TODO-002 | TODOs | P2 | `frontend/searchApi.ts` stub `fetchSuggestions()` → `[]` — endpoint backend inexistant | S |
| 51 | OPENAPI-001 | OpenAPI | P2 | `openapi.yaml` lignes 605/758/3515/3849/4012 — 5 TODOs Sprint futurs (legitimate placeholders) | — |
| 52 | BORDER-001 | Invariants | OK | `git diff origin/main HEAD -- frontend/` = 0 ligne ✅ ; `openapi/` = 0 ligne ✅ ; aucun `@Disabled` / `@Ignore` / `--no-verify` / force push | — |

---

## Findings détaillés

### STUB-001 — 13 stubs JPA cross-service présents (P0)

**Catégorie** : JPA stubs | **Sévérité** : P0 | **Effort** : XL (~5-7 commits)

**Description**
La cible spec finalization Décision B et Étape 4.5 est **0 stubs JPA cross-service**. Au HEAD `ec668b91` il en reste **13** : les 3 REST client interfaces existent dans `shared-domain-dtos.shared.client` mais aucun consumer ne les câble runtime. Les call-sites continuent d'utiliser `XStub.findByIdOptional(...)` / `@ManyToOne XStub`.

**Reproduction**
```bash
find /workspace/backend/services -name '*Stub.java' -not -path '*/target/*' | wc -l   # → 13
grep -rln '@ManyToOne.*Stub\|extends.*Stub' /workspace/backend/services/*/src/main/java | wc -l   # → entités impactées
```

**Inventaire** (cf. **Annexe A**)

| # | Path | Service | Table | Refactor |
|---|---|---|---|---|
| 1 | `engagement-service/.../EventStub.java` | engagement | events | EventServiceClient |
| 2 | `engagement-service/.../UserStub.java` | engagement | users | UserServiceClient |
| 3 | `engagement-service/.../EventCoOrganizerStub.java` | engagement | event_co_organizers | EventServiceClient (cascade via `?check-co-org-of=`) |
| 4 | `event-service/.../UserStub.java` | event | users | UserServiceClient |
| 5 | `event-service/.../AttendanceStub.java` | event | attendances | EngagementServiceClient |
| 6 | `event-service/.../EventViewStub.java` | event | event_views | LOCAL ENTITY (post-2.2.2) |
| 7 | `event-service/.../FavoriteStub.java` | event | favorites | **REDUNDANT** — `Favorite.java` existe déjà |
| 8 | `moderation-service/.../EventStub.java` | moderation | events | EventServiceClient |
| 9 | `moderation-service/.../UserStub.java` | moderation | users | UserServiceClient |
| 10 | `moderation-service/.../EventCoOrganizerStub.java` | moderation | event_co_organizers | EventServiceClient (cascade) |
| 11 | `user-service/.../AttendanceStub.java` | user | attendances | EngagementServiceClient |
| 12 | `user-service/.../EventStub.java` | user | events | EventServiceClient |
| 13 | `user-service/.../FavoriteStub.java` | user | favorites | EventServiceClient (via DTO) |

**Entités JPA à refactorer** (3) : `Comment` (engagement), `Event` (event-service, le `creator: UserStub`), `Report` (moderation, 2x `UserStub` + `EventStub`).

**Impact**
- Cible spec violée (0 stubs).
- Tous les anti-oracles + cascades passent par les stubs (lecture directe DB cross-schéma) au lieu des REST clients.
- Coupe la défense en profondeur visée par la migration microservices : le code peut écrire dans des tables hors-domaine (ex. `EventStub` writable pour `event.status = BANNED` côté moderation-service).

**Fix recommandé** (à industrialiser dans la spec finale)
1. Pour chaque consumer : ajouter `quorkus.rest-client.<svc>.url=${<SVC>_SERVICE_URL:http://<svc>-service:8080}` dans `application.properties` (cf. **REST-001**).
2. Pour chaque entité avec `@ManyToOne XStub` : remplacer par `@Column(name="<x>_id") Long/UUID xId` (FK plain, sans navigation JPA).
3. Pour chaque call-site `XStub.findByIdOptional(id)` : remplacer par injection `@RestClient <X>ServiceClient client` + `client.getById(id)`.
4. Pour `FavoriteStub` (event-service) : pure suppression — utiliser `Favorite` qui existe.
5. Mocks de test : `@InjectMock @RestClient <X>ServiceClient` (Étape 4.5/5 du wiring).
6. Final : `git rm` les 13 fichiers Stub + supprimer les beans CDI orphelins.

**Réf spec** : finalization spec § Étape 4.5 (déféré explicitement par sprint-context.md ligne 86-91).

---

### REST-001 — Aucun `quarkus.rest-client.<svc>.url` configuré dans les consumers (P0)

**Catégorie** : REST clients | **Sévérité** : P0 | **Effort** : S

**Description**
Les 3 `@RegisterRestClient` interfaces déclarent `configKey = "event-service"`, `"user-service"`, `"engagement-service"`. Au runtime, Quarkus résout l'URL via la propriété `quarkus.rest-client.<configKey>.url`. **Aucun des 4 services consumers** n'a cette config dans `application.properties`.

**Reproduction**
```bash
grep -rE "rest-client" /workspace/backend/services/*/src/main/resources/application.properties
# → zero hits
```

**Impact** : tout `@Inject @RestClient EventServiceClient ec` au runtime échoue avec `RestClientDefinitionException: invalid URL`. Le wiring de Étape 4.5 est mort dès qu'on l'active.

**Fix recommandé**
Ajouter aux `application.properties` de event/user/engagement/moderation (selon les hops Annexe C) :
```properties
quarkus.rest-client.event-service.url=${EVENT_SERVICE_URL:http://event-service:8080}
quarkus.rest-client.user-service.url=${USER_SERVICE_URL:http://user-service:8080}
quarkus.rest-client.engagement-service.url=${ENGAGEMENT_SERVICE_URL:http://engagement-service:8080}
```

Helm `values.yaml` propage la valeur K8s (`http://<svc>-service.<ns>.svc.cluster.local:8080`).

**Réf spec** : finalization spec § Décision C — convention de URL par variable d'environnement.

---

### REST-002 — Provider endpoint `GET /users/{id}/attendances` introuvable côté engagement-service (P0)

**Catégorie** : REST clients | **Sévérité** : P0 | **Effort** : S

**Description**
`EngagementServiceClient.getUserAttendances(UUID id, String status)` mappe `GET /users/{id}/attendances`. Côté engagement-service, **seul `MyAttendancesResource` existe** avec `GET /users/me/attendances` (self-scoped JWT). Aucun resource ne sert le path-with-id.

**Reproduction**
```bash
grep -rln '"/users/{id}/attendances"\|"/users/{userId}/attendances"' /workspace/backend/services/engagement-service/src/main/java
# → vide
```

**Impact** : runtime 404 systématique. La spec `internal-endpoints.md` #4 ne correspond à aucune implémentation. Le pact `UserEventBulkPactTest` n'est PAS impacté (il vise event-service, pas engagement). Mais le runtime de la calendar ICS feed user-service (qui consomme `engagementClient.getUserAttendances(...)`) sera cassé.

**Fix recommandé**
Créer `engagement-service/.../resource/UserAttendancesInternalResource.java` :
```java
@Path("/users")
@Produces(MediaType.APPLICATION_JSON)
public class UserAttendancesInternalResource {
    @Inject AttendanceService attendanceService;
    @GET @Path("/{id}/attendances") @PermitAll
    public List<AttendanceDTO> get(@PathParam("id") UUID id,
                                    @QueryParam("status") AttendanceStatus status) {
        return attendanceService.getByUser(id, status);
    }
}
```

Ou — alternative — supprimer la méthode de `EngagementServiceClient` et faire passer user-service par le path public `/users/me/attendances` avec OIDC propagation. Trancher dans la spec finale.

---

### REST-003 — `UserService.getPublicProfile` n'a pas le bypass admin (P1)

**Catégorie** : REST clients | **Sévérité** : P1 | **Effort** : S

**Description**
`UserServiceClient.java:21-24` documente : « 404 when target profilePublic=false and caller is **neither self nor admin** ». L'implémentation `UserService.java:73-79` ne teste que `caller == self` — un admin lisant un profil privé reçoit 404 alors qu'il devrait recevoir le payload complet.

**Reproduction**
```java
// user-service/.../service/UserService.java:73-79
if (!user.profilePublic && !auth0Id.equals(user.auth0Id)) {
    throw new NotFoundException();   // ← admin tombe ici aussi
}
```

**Fix recommandé**
```java
if (!user.profilePublic && !auth0Id.equals(user.auth0Id) && !isAdmin) {
    throw new NotFoundException();
}
```

Et propager `isAdmin` depuis `UserResource` (lecture du JWT role `ADMIN`).

---

### REST-004 — Bare `NotFoundException` ne produit pas le body envelope `{"error":"not_found"}` (P1)

**Catégorie** : REST clients | **Sévérité** : P1 | **Effort** : S

**Description**
`event-service.EventService.java:269,272,276`, `user-service.UserService.java:74,78`, `user-service.FollowService.java:150,153` : tous lancent `new NotFoundException()` sans body. Quarkus REST renvoie 404 + body vide.

Le pact `EngagementEventIssue92PactTest:62-65` assert :
```java
.body(new PactDslJsonBody().stringType("error", "not_found"))
```

→ **Le pact verification provider échouera** dès qu'il sera câblé en CI (cf. CI-007).

**Fix recommandé**
Créer dans `shared-api-error` :
```java
@Provider
public class NotFoundExceptionMapper implements ExceptionMapper<NotFoundException> {
    @Override
    public Response toResponse(NotFoundException ex) {
        return Response.status(404)
            .entity(new ApiErrorResponse("not_found", "Resource not found"))
            .type(MediaType.APPLICATION_JSON)
            .build();
    }
}
```

Avec Jandex auto-discovery, le mapper est actif partout où shared-api-error est dans le classpath. Mettre à jour `architecture.md` § Conventions d'erreur.

---

### CI-001 — `sonar:sonar` plante avec « Maven session does not declare a top level project » (P0)

**Catégorie** : CI/Sonar | **Sévérité** : P0 | **Effort** : S

**Description**
Erreur observée par Elie sur les 5 cellules services après le commit `ec668b91` (`verify` → `install`) :
```
Failed to execute goal org.sonarsource.scanner.maven:sonar-maven-plugin:4.0.0.4121:sonar (default-cli)
on project engagement-service: Maven session does not declare a top level project -> [Help 1]
```

**Cause racine**
La step `./mvnw -pl services/<X>-service sonar:sonar -B` lance Maven sur un seul module enfant comme reactor. Le sonar-maven-plugin 4.0.0.4121 (auto-fetch latest) requiert qu'un projet soit marqué `executionRoot=true` dans la `MavenSession`. Avec `-pl module-fils`, **aucun projet n'est top-level** (le parent agrégateur n'est pas dans la session) → `topLevelProject == null` → crash.

Cf. [SonarSource/sonar-scanner-maven](https://github.com/SonarSource/sonar-scanner-maven) issue trackers et [Sonar Community thread](https://community.sonarsource.com/t/failed-to-execute-goal-org-sonarsource-scanner-maven3-7-0-1746-sonar/50362).

**Fix concret** (3 options, par ordre de préférence)

**Option 1 — `-pl .,services/<X>-service`** (préserve le sonar:sonar par module) :
```yaml
- name: SonarQube Scan
  run: ./mvnw -pl .,services/${{ matrix.service }}-service sonar:sonar -B
```
Le `.` ajoute le parent agrégateur comme top-level dans la session. Minimal change.

**Option 2 — Combine en une seule invocation** (recommandé Sonar) :
```yaml
- name: Build & Sonar
  run: ./mvnw -pl .,services/${{ matrix.service }}-service -am verify sonar:sonar -B \
       -Dquarkus.container-image.build=true ...
```
Une seule MavenSession, top-level résolu, scan avec les .class fraîchement compilées. Inconvénient : pas de granularité step (mais fail dans la même cellule donc OK).

**Option 3 — Sonar à la racine** (recommandé après consolidation CI-002) :
```yaml
- name: Sonar (parent reactor)
  run: ./mvnw sonar:sonar -B
```
Scan **toute** la PR en un seul push, le routing par projectKey (présent dans chaque pom enfant) reste honoré. Solution la plus propre quand combinée à CI-002.

**Note** : pinning du plugin (`<sonar-maven-plugin.version>5.0.0.4389</sonar-maven-plugin.version>` dans le parent POM) ne corrige PAS le bug — c'est sémantique session, pas version plugin.

---

### CI-002 — 10 cellules `Build Shared Lib` du gaspillage post-Option B (P1)

**Catégorie** : CI/Sonar | **Sévérité** : P1 | **Effort** : M

**Description**
Sous Option B (Elie 2026-05-09), les 10 shared libs scannent toutes dans le projet `unige-events-backend`. Les 10 cellules CI lancent :
1. 10 checkouts (~5s × 10 = 50s)
2. 10 setup-java (~10s × 10 = 100s)
3. 10 builds isolés `-am` (chacun re-build les ancêtres communs : shared-platform, shared-tracing, etc.)
4. 10 `sonar:sonar` push successifs **sur le même projectKey** → 9/10 scans sont **clobbés** (Sonar ne garde que le dernier scan).

Estimation : **~25 runner-minutes/run gaspillées** + résultat Sonar = scan d'1 seul module (le dernier pushé).

**Détection bonus (CI-004)** : commit `39c6f195` ajoute `sonar.projectKey` à `shared-rate-limit` + `shared-storage` poms. Sous Option B, ces overrides devraient être **supprimés** sinon ces 2 libs scannent dans des projets Sonar inexistants pendant que les 8 autres écrivent dans `unige-events-backend` → split incohérent. Vérifier que le commit Option B (`ac41cb85`) a bien retiré tous les `<sonar.projectKey>` des 10 shared libs (déjà fait selon le commit, mais à confirmer).

**Fix recommandé** : consolidation 10 → 1 cellule
```yaml
build-shared-libs:
  name: Build All Shared Libs
  steps:
    - uses: actions/checkout@v6
    - uses: actions/setup-java@v5 (cache: maven)
    - name: Build & Test
      run: |
        ./mvnw -pl services/shared-rate-limit,services/shared-storage,\
                  services/shared-api-error,services/shared-domain-enums,\
                  services/shared-domain-dtos,services/shared-domain-projections,\
                  services/shared-jaxrs,services/shared-tracing,\
                  services/shared-kafka-events,services/shared-platform \
               -am install -B
    # Pas de sonar:sonar ici — il sera fait à la racine en parent step (CI-001 Option 3)
```

Gain : ~25 min/run + résultat Sonar cohérent (un seul scan avec les 10 libs agrégées dans `unige-events-backend`).

**Variante 3 cellules par catégorie** (shared-runtime, shared-data, shared-test) — over-engineering, pas de gain Sonar (même projectKey), pas de speedup vs 1 cellule (build est déjà transitif via `-am`).

---

### CI-003 — `continue-on-error: true` masque CI-001 même après création des projets (P1)

**Catégorie** : CI/Sonar | **Sévérité** : P1 | **Effort** : XS

**Description**
Quand DevOps créera les 5 projets services Sonar, l'erreur `top level project` (CI-001) **persistera** car elle n'est pas liée au projet manquant. Le `continue-on-error` masque actuellement deux bugs distincts :
1. `Project not found` (résoudra à création des projets)
2. `Maven session does not declare a top level project` (CI-001, ne résoudra **pas** sans fix)

**Fix recommandé**
Appliquer CI-001 d'abord, puis retirer `continue-on-error` cellule par cellule pour vérifier que chaque step est strict.

---

### CI-004 — Incohérence Option B : 2 shared libs gardent leur `sonar.projectKey` (P1)

**Catégorie** : CI/Sonar | **Sévérité** : P1 | **Effort** : XS

**Description**
Le commit `ac41cb85` (Option B) supprime `sonar.projectKey` des 10 shared libs poms — à vérifier ligne par ligne :

```bash
grep -n 'sonar.projectKey' backend/services/shared-*/pom.xml
```

Si une ou plusieurs poms shared libs garde encore son `<sonar.projectKey>`, c'est une incohérence Option B/F. À corriger en supprimant l'override.

**Note** : agent 10 a confirmé (DEP-004) que les 10 shared libs sont propres — finding peut être archivé en P2 informational si revérifié.

---

### CI-005 — `quarkus.container-image.push=true` sur PR (P2)

**Catégorie** : CI/Sonar | **Sévérité** : P2 | **Effort** : S

**Description**
build.yml ligne 100 : `push={{ event_name == 'push' || event_name == 'pull_request' }}` → push systématique sur PR vers GHCR avec tag `pr-<N>`. Voulu pour `deploy-preview` qui consomme `image.tag=${{ github.sha }}`. Coût stockage GHCR non négligeable sur PR draft/abandonnées (5 services × tag pr-N + tag latest).

**Fix recommandé** (optionnel)
Ajouter dans `cleanup.yml` (ou créer un job nettoyage GHCR) :
```yaml
- name: Cleanup PR-tagged GHCR images
  if: github.event.action == 'closed'
  run: |
    for svc in event user engagement moderation notification; do
      gh api -X DELETE /user/packages/container/unige-events-${svc}/versions \
        --jq ".[] | select(.metadata.container.tags[] | contains(\"pr-${{ github.event.pull_request.number }}\")) | .id" \
        | xargs -I{} gh api -X DELETE /user/packages/container/unige-events-${svc}/versions/{}
    done
```

---

### CI-006 — `build-contract-and-e2e` utilise `verify` (pas `install`) (P2)

**Catégorie** : CI/Sonar | **Sévérité** : P2 | **Effort** : XS

**Description**
Le commit `35c0cdbb` ajoute le job avec `./mvnw -pl contract-tests,e2e -am verify`. Le commit `ec668b91` (postérieur) change tous les autres jobs à `install` pour fixer la résolution de deps. Ce job reste à `verify` — incohérence stylistique. Pas de bug fonctionnel ici (rien ne consomme contract-tests downstream).

**Fix recommandé** : aligner à `install` pour cohérence.

---

### CI-007 — Pact provider verification job absent (P2)

**Catégorie** : CI/Sonar | **Sévérité** : P2 | **Effort** : M

**Description**
4 pacts JSON sont générés en consumer dans `backend/contract-tests/target/pacts/`, uploaded en artifact. Aucun job CI ne lance la **provider verification** : `mvn -pl services/event-service verify -Dpact.verifier.tests=*`.

**Impact**
- Les pacts servent de doc, pas de garde-fou. Rien ne casse si le provider drifte (ex. event-service change la shape de EventDTO sans warn).
- REST-004 (NotFoundException sans body) passerait inaperçu jusqu'au runtime preview.

**Fix recommandé**
Ajouter un job `verify-pacts` :
```yaml
verify-pacts:
  name: Pact provider verification
  needs: [build-shared-libs, build-contract-and-e2e]
  steps:
    - uses: actions/checkout@v6
    - uses: actions/download-artifact@v4
      with:
        name: pacts-${{ github.sha }}
        path: backend/contract-tests/pacts/
    - run: |
        cd backend/services/event-service
        ./mvnw verify -Dpact.verifier.dir=../../contract-tests/pacts -Dpact.verifier.tests=*PactVerification*
    # idem pour user-service, engagement-service
```

Activer plus tard quand le provider verification harness est écrit (Sprint S9).

---

### CI-008 — Spec décrit `pacts/` sans `target/` (P2)

**Catégorie** : CI/Sonar | **Sévérité** : P2 | **Effort** : XS

**Description**
La spec finalization mentionne `backend/contract-tests/pacts/` ; l'implémentation génère dans `backend/contract-tests/target/pacts/` (cf. `pom.xml:<pact.rootDir>`). Imprécision spec, pas un bug.

**Fix recommandé** : aligner la spec finale.

---

### DUP-001 — `ApiErrorResponse.java` × 4 copies locales (P1)

**Catégorie** : Duplicats locaux | **Sévérité** : P1 | **Effort** : XS

**Description**
4 services ont leur propre `ApiErrorResponse` malgré l'existence de `shared-api-error/.../ApiErrorResponse.java` :
- `event-service/.../event/dto/ApiErrorResponse.java` (10 lignes, 10 call-sites, ajoute `@Schema`)
- `user-service/.../user/dto/ApiErrorResponse.java` (10 lignes, 6 call-sites)
- `engagement-service/.../engagement/attendance/dto/ApiErrorResponse.java` (10 lignes, 9 call-sites)
- `moderation-service/.../report/dto/ApiErrorResponse.java` (10 lignes, 6 call-sites)

**Diff sémantique** : chaque copie ajoute `@Schema(name="ApiErrorResponse", description="…")` (OpenAPI annotation) ; le shared n'a pas l'annotation. Si on bascule au shared, on perd l'annotation OpenAPI sur la doc générée — à vérifier.

**Fix recommandé**
1. Ajouter `@Schema` au shared `ApiErrorResponse`.
2. Ajouter dep `shared-api-error` aux 4 services (et à `shared-domain-dtos` qui en a besoin pour les fallbacks REST clients).
3. Sed global : `sed -i 's|ch\.unige\.events\.\(event\|user\|engagement\|report\)\.\(.*\)dto\.ApiErrorResponse|ch.unige.events.shared.error.ApiErrorResponse|g'`.
4. `git rm` les 4 copies.

---

### DUP-002 — `ServiceIdentityResource.java` × 5 copies locales (P1)

**Catégorie** : Duplicats locaux | **Sévérité** : P1 | **Effort** : S

**Description**
5 services ont leur propre `ServiceIdentityResource` malgré `shared-platform/.../ServiceIdentityResource.java` (qui paramétrise via `@ConfigProperty("quarkus.application.name")`).

**Diff critique** : event-service ajoute un champ non-standard `"status": "scaffold"` dans la réponse JSON — l'évolution vers le shared casserait le test `ServiceIdentityResourceTest` qui assert `status: scaffold`.

**Fix recommandé**
1. Aligner les tests existants pour ne plus asserter sur `status` (le shared ne le retourne pas).
2. Ajouter `shared-platform` dep aux 5 services.
3. `git rm` les 5 copies locales.
4. Le bean shared est auto-discovered via Jandex.

---

### DUP-003 — `Timeframe.java` × 2 copies locales (P1)

**Catégorie** : Duplicats locaux | **Sévérité** : P1 | **Effort** : XS

**Description**
- `event-service/.../entity/Timeframe.java` (1 call-site)
- `engagement-service/.../attendance/entity/Timeframe.java` (10 call-sites)

Identiques au shared `shared-jaxrs/.../Timeframe.java`. Le shared inclut aussi `TimeframeParamConverter` + `TimeframeParamConverterProvider` (auto-discovered).

**Fix recommandé** : substitution import + suppression locale + ajout dep `shared-jaxrs` si manquante.

---

### DUP-004 — 21 enums locaux malgré shared-domain-enums (P1)

**Catégorie** : Duplicats locaux | **Sévérité** : P1 | **Effort** : M (~230 call-sites)

**Description**
Listing par enum :
- `EventStatus` × 4 copies (event, user, engagement, moderation), 88 call-sites
- `EventCategory` × 2 (event, engagement), ~40 call-sites
- `Faculty` × 2 (event, engagement), ~30 call-sites
- `AttendanceStatus` × 2 (event, engagement), ~25 call-sites
- `CoOrganizerStatus` × 4 (event x2, engagement, moderation), ~15 call-sites
- `RecurrenceFrequency` × 1 (event), ~8 call-sites
- `FollowStatus` × 1 (user), ~12 call-sites
- `ReportStatus` × 1 (moderation), ~5 call-sites
- `ReportReason` × 1 (moderation), ~4 call-sites

**Total** : 21 fichiers, ~230 imports à substituer.

**Fix recommandé**
1. Ajouter dep `shared-domain-enums` aux 4 services métiers.
2. Sed global de migration des imports (cf. spec finalization Étape 4.0.1).
3. Hibernate `@Enumerated(STRING)` reste valide — l'invariant des constantes est garanti par `EnumValuesSentinelTest` côté shared-domain-enums.
4. `git rm` les 21 copies.

---

### DUP-005 — DTOs cross-projetés × 7 copies (P1)

**Catégorie** : Duplicats locaux | **Sévérité** : P1 | **Effort** : L (4h)

**Description**
| DTO | Service | Lignes | Call-sites | Diff |
|---|---|---|---|---|
| EventDTO | event-service | 96 | 96 | factory `from(...)` 2 overloads ; champ `coOrganizerOf` ajouté en 4.4 |
| EventDTO | engagement.attendance.dto | 82 | 9 | factory `from(...)` ; pas de `coOrganizerOf` |
| EventDTO | event-service.coorganizer.dto | ? | ? | (sous-pkg, à vérifier) |
| EventDTO | event-service.me.dto | ? | ? | (sous-pkg, à vérifier) |
| EventDTO | event-service.favorite.dto | ? | ? | (sous-pkg, à vérifier) |
| UserPublicResponse | user-service | 75 | 19 | **3 factories** (from, fromCounts, fromAnonymous) vs shared 1 (`anonymous`) |
| AttendanceDTO | engagement-service | 30 | 17 | factory `from(Attendance, UserStub)` |

**Diff critique**
- event-service local `EventDTO` a `from(...)` avec params dérivés (`attendingCount`, `availableSpots`, `viewCount`...). Le shared a juste le record canonique. Bascule = soit ajouter les méthodes au shared, soit enrichir via wrapper consumer-side.
- user-service `UserPublicResponse` a une logique de factory plus riche que le shared.

**Fix recommandé** (approche progressive)
1. Enrichir `shared-domain-dtos.EventDTO` avec les méthodes statiques `from(...)` (et import des entités JPA via `provided` scope si besoin — sinon helper externe).
2. Bascule chaque service consumer vers shared.
3. Garder le DTO LOCAL du service propriétaire (event-service garde son local `EventDTO` car il a des champs computed) — supprimer les COPIES dans les consumers (engagement.attendance.dto, etc.) qui ne servent qu'à la désérialisation Jackson.

**Décision à prendre dans la spec finale** : single shared `EventDTO` ou dual (provider local + shared canonique).

---

### DUP-006 — Helpers dupliqués (P2)

**Catégorie** : Duplicats locaux | **Sévérité** : P2 | **Effort** : M

**Description**
- `computeAvailableSpots(capacity, attending)` × 5 copies (event ×4 sous-packages + engagement)
- `resolveUserId(auth0Id)` × 3 copies (event, user, engagement)

Le shared `EventCapacity` (shared-domain-projections) et `Auth0IdResolver` existent et exposent ces fonctions. Personne ne les consomme.

**Fix recommandé** : substitution + suppression des helpers locaux.

---

### TEST-001 — 31/35 sentinels SCRUM sont des `@Test` à corps vide (P1)

**Catégorie** : Tests | **Sévérité** : P1 | **Effort** : XL (~31 commits, 202h)

**Description**
La spec finalization Étape 5.6 valide les 35 sentinels par grep. Au HEAD `ec668b91` :
- 4/35 IMPLEMENTED (`RecurrenceGeneratorTest` — assertions réelles portées du legacy).
- 31/35 PLACEHOLDER (`@Test void X() {}` — passent mais ne couvrent rien).
- 0/35 MISSING.

**Détail effort**
- XS×1 (`prePersist_setsCreatedAt` — fixture trivial, 2h)
- S×4 (assertions simples, mock léger, 4×4h = 16h)
- M×12 (mocks REST client + assertions transactionnelles, 12×6h = 72h)
- L×14 (cascade pessimistic lock, DevServices PostgreSQL, anti-oracle ISSUE-93, 14×8h = 112h)
- **Total** : ~202h, ~31 commits.

**Fix recommandé**
Porter par batch en suivant l'ordre de la spec § 5.2-5.4 (depuis `git show 41074e9:.../<TestName>.java`). Pré-requis : Étape 4.5 (REST clients câblés + stubs supprimés) sinon les mocks `@InjectMock @RestClient` ne fonctionnent pas (pas de bean à mocker).

**Réf** : finalization spec § Étape 5.

---

### TEST-002 — Pact `AttendanceSummary` (endpoint interne #1) absent (P1)

**Catégorie** : Tests | **Sévérité** : P1 | **Effort** : S

**Description**
`internal-endpoints.md` #1 = `GET /events/{eventId}/attendance-summary`, exposé par `AttendanceSummaryInternalResource` (engagement-service), consommé par event/moderation via `EngagementServiceClient.getAttendanceSummary(eventId)`.

**Aucun pact** ne le couvre. Les 4 pacts existants visent tous event-service comme provider.

**Fix recommandé**
Créer `EventEngagementAttendancePactTest` (consumer = event-service ou moderation-service, provider = engagement-service) :
```java
@Pact(consumer = "event-service", provider = "engagement-service")
public RequestResponsePact getAttendanceSummary_returnsCounts(PactDslWithProvider builder) {
    return builder
        .given("event 42 has 5 ATTENDING and 2 WAITLISTED")
        .uponReceiving("GET /events/42/attendance-summary")
            .path("/events/42/attendance-summary").method("GET")
        .willRespondWith().status(200)
            .body(new PactDslJsonBody()
                .integerType("attending", 5L)
                .integerType("waitlisted", 2L)
                .integerType("interested", 0L))
        .toPact();
}
```

---

### TEST-003 / KAFKA-001 — `EventLifecycleKafkaBridgeTest` manquant (P2)

**Catégorie** : Tests | **Sévérité** : P2 | **Effort** : S

**Description**
4 bridges sur 5 ont leurs tests :
- `CoOrganizerKafkaBridgeTest` ✅
- `FollowLifecycleKafkaBridgeTest` ✅
- `CommentCreatedKafkaBridgeTest` ✅
- `EventBannedKafkaBridgeTest` ✅
- `EventLifecycleKafkaBridgeTest` ❌ manquant

**Fix** : copier le pattern d'un autre bridge test (Mockito, isolated, `@Observes(AFTER_SUCCESS)`).

---

### TEST-004 — Sentinel scaffolds redondants (P2)

**Catégorie** : Tests | **Sévérité** : P2 | **Effort** : XS

**Description**
- `contract-tests/.../ContractTestsScaffoldTest.java` : seul corps `assertNotNull(getClass().getName())`.
- `e2e/.../E2EScaffoldTest.java` : idem.

Ces tests servaient à valider que surefire boot après le module scaffolding (Étape 6.0). Maintenant que les modules contiennent 4 vrais pacts + 1 vrai E2E, ils sont redondants.

**Fix** : `git rm` les 2 fichiers.

---

### TEST-005 — internal-endpoints.md endpoint #4 sans implémentation (P2)

**Catégorie** : Tests | **Sévérité** : P2 | **Effort** : S

**Description**
`internal-endpoints.md` ligne 23 : « Endpoint #4 GET /users/{id}/attendances?status=ATTENDING — engagement-service. Existant publiquement (déjà routé Kong sur /api/users/me/attendances), réutilisé en interne ». Or `/users/me/attendances` est self-only ; `/users/{id}/attendances` n'existe pas dans le code (cf. **REST-002**).

**Fix** : voir REST-002 (créer le resource OU supprimer la méthode du REST client + reformuler internal-endpoints.md).

---

### COV-001 — Couverture jacoco services métiers à 5–17% L (cible 80%) (P1)

**Catégorie** : Couverture | **Sévérité** : P1 | **Effort** : XL

**Description**

| Module | % L | % B | Cible | Δ L |
|---|---|---|---|---|
| event-service | 5.4% | 0.5% | 80% / 70% | **-74.6** |
| user-service | 6.2% | 1.0% | 80% / 70% | -73.8 |
| engagement-service | 10.8% | 1.0% | 80% / 70% | -69.2 |
| moderation-service | 17.2% | 3.0% | 80% / 70% | -62.8 |

**Diagnostic** :
- 1818 tests legacy (du commit `41074e9`) **non portés**.
- 31/35 sentinels sont vides (pas de gain de couverture).
- Branches quasi-zéro (0.5–3%) — absence totale de tests sur les contrôles de flux.

**Honnêteté jacoco** : **OK**. JaCoCo mesure `src/main/java`, pas les méthodes test. Les 31 sentinels vides ne gonflent pas le compteur. **Les bas scores sont vrais.**

**Top 5 classes 0% à cibler**
- event-service `ServiceIdentityResource.java` (29 lignes) — éliminé par DUP-002 si on bascule au shared.
- engagement-service `CoOrganizerCheck.java` (20 lignes) — DTO non testé.
- moderation-service `ReportReason.java` (8 lignes) — enum dispatch.
- user-service `FollowStatus.java` (6 lignes) — enum.
- shared-domain-dtos `CoOrganizerCheck.java` (20 lignes) — DTO sans test.

**Fix recommandé** : voir TEST-001 (port des 31 sentinels) + import des 1818 tests legacy par batch (cf. spec finalization Étape 5.2-5.5).

---

### COV-002 — `shared-domain-dtos` à 63.2% L (cible 95%) (P2)

**Catégorie** : Couverture | **Sévérité** : P2 | **Effort** : S

**Description**
Les nouveaux records `EventDTO`, `AttendanceDTO`, `EventCoOrganizerDTO` (commit `4ebad58`) ne sont testés que minimalement. `shared-domain-dtos` chute de 100% à 63.2% L.

**Fix** : étoffer `EventDTOTest` + `AttendanceDTOTest` (tests de récord + edge cases sur null booleans).

---

### K8S-001 — `notification-service` deployment.yaml sans livenessProbe (P1)

**Catégorie** : Helm/K8s | **Sévérité** : P1 | **Effort** : XS

**Description**
`k8s/chart/templates/notification-service/deployment.yaml:53-58` ne définit que `readinessProbe`. Asymétrique vs les 4 services actifs qui ont `livenessProbe` + `readinessProbe`.

Même si replicas:0, la cohérence d'exploitation matters le jour où on active le service (Sprint S9 — SCRUM-99).

**Fix** : ajouter le bloc livenessProbe (copy depuis event-service lignes 65-71, path `/api/q/health/live`).

---

### KAFKA-001 — `EventLifecycleKafkaBridgeTest` manquant (P2)

Voir **TEST-003**.

---

### DOC-001 — `devops-handoff.md` TL;DR contredit la réalité sur les stubs (P0)

**Catégorie** : Doc drift | **Sévérité** : P0 (contradiction factuelle) | **Effort** : XS

**Description**
`devops-handoff.md:16` :
```
* REST clients @RegisterRestClient cross-service avec resilience (...) — 35 stubs JPA cross-schéma supprimés.
```

**Réalité** : 13 stubs JPA cross-service persistent (cf. **STUB-001**). Le TL;DR ment.

**Fix recommandé**
Remplacer par :
```
* REST clients @RegisterRestClient cross-service interfaces livrées (3 interfaces couvrant
  8 hops). Wiring consumer + suppression des 13 stubs JPA cross-service restants reste
  à livrer (Étape 4.5 finalization spec, déféré).
```

---

### DOC-002 — `architecture.md` topologie K8s mentionne « 13 microservices » (P1)

**Catégorie** : Doc drift | **Sévérité** : P1 | **Effort** : S

**Description**
`architecture.md` ligne 252, 260-269, 301 : « 13 services Quarkus », table consommateurs avec services dissous, diagramme K8s avec 13 services.

**Fix** : remplacer par 5 services (4 actifs + notification placeholder) + nettoyer la table consommateurs avec event/user/engagement/moderation/notification.

---

### DOC-003 — `architecture.md` flux comment cite services dissous (P1)

**Catégorie** : Doc drift | **Sévérité** : P1 | **Effort** : S

**Description**
`architecture.md` lignes 200-204 : « Kong route → comment-service:8080. CommentResource → CommentService → coOrganizerClient.checkAccess(...) ». Or comment-service est dissous (absorbé par engagement-service en 2.4.1) et co-organizer-service aussi (absorbé par event-service en 2.2.4 ; cascade locale via `?check-co-org-of=`).

**Fix** : reformuler : « Kong route → engagement-service:8080. CommentResource → CommentService → eventServiceClient.getByIdWithCoOrgCheck(eventId, callerUUID) (cascade locale event-service post-2.2.4) ».

---

### DOC-004 — `microservices-migration-roadmap.md` décrit la migration comme TODO (P1)

**Catégorie** : Doc drift | **Sévérité** : P1 | **Effort** : M

**Description**
`microservices-migration-roadmap.md:697-704` TL;DR : « PR #158 livre les **fondations + scaffolds + docs** du Sprint 8. Les **13 extractions de code**... plus la **suppression du monolithe**... plus la **doc finale**... plus la **CI matrix** sont **16 PRs follow-up** ».

**Réalité** : ces 16 PRs ont été **livrées** (commits `b858196` → `ec668b91`). Le doc trace une TODO historique, pas l'état final.

**Fix recommandé**
Ajouter en header (après ligne 1) :
```markdown
> **[ARCHIVÉ — 2026-05-09]** Ce document trace l'historique de la migration
> Sprint 8, désormais **livrée intégralement**. État final : 5 services métiers
> + 10 shared libs + contract-tests + e2e = 17 modules dans le reactor.
> Cf. [`consolidation-plan.md`](consolidation-plan.md) pour le mapping 14→5
> et [`architecture.md`](architecture.md) pour la topologie finale.
```

---

### DOC-005 — `AGENTS.md` racine + `backend/AGENTS.md` mentions « 13 microservices » (P1)

**Catégorie** : Doc drift | **Sévérité** : P1 | **Effort** : S

**Description**
- Racine `AGENTS.md:11-13` : « 13 microservices Quarkus livrés au Sprint 8 » + listing.
- `backend/AGENTS.md:113` : « dev-guide.md — démarrage, workflows, layout 24 modules. » → erroné, c'est 17 modules.
- `backend/AGENTS.md:134` : « activation DevOps des 13 SonarCloud projects (Étape 12...) » → Option B = 5.

**Fix** : aligner sur la topologie 5 + 17 modules + Option B.

---

### DOC-006 — `api-contract.md` table topologie liste les 13 services dissous (P1)

**Catégorie** : Doc drift | **Sévérité** : P1 | **Effort** : M

**Description**
`backend/docs/api-contract.md` lignes 13, 18-47, 76-110 : tout le tableau topologie + endpoints liste les 13 services pré-consolidation. Aucune mention de la consolidation.

**Fix recommandé** : refondre la table sur les 4 services métiers actifs (event/user/engagement/moderation). La colonne « Service amont » se nettoie naturellement.

---

### DOC-007 — Multiples docs réfèrent « 13/15 SonarCloud projects » (Option A obsolète) (P1)

**Catégorie** : Doc drift | **Sévérité** : P1 | **Effort** : S

**Description**
Liste exhaustive de drift Option B :
- `sprint-context.md:15` : « 5 services + 10 libs = 15 projects » → Option A.
- `sprint-context.md:51` : « créer les 15 SonarCloud projects (item 1...) » → Option A.
- `sprint-context.md:560` : « création des 13 SonarCloud projects côté DevOps. »
- `sprint-context.md:581` : « activation effective dépend de la création des 13 SonarCloud projects côté DevOps ».
- `architecture.md:343-345` : matrix `[share, view, …]` + « 13 SonarCloud projects créés ».
- `backend/AGENTS.md:134` : « activation DevOps des 13 SonarCloud projects ».

**Fix** : sed global `13/15 SonarCloud projects` → `5 SonarCloud projects services + parent unige-events-backend (Option B, décision Elie 2026-05-09)`. Sauf dans la spec elle-même (`specs_microservices_migration_finalization.md`) qui est antérieure à Option B.

---

### DOC-008 — `sprint-context.md` ligne 152 vs 589 contradiction stubs (P1)

**Catégorie** : Doc drift | **Sévérité** : P1 | **Effort** : XS

**Description**
- Ligne 152 (récap Étape 4 PM) : `13 stubs JPA cross-service **encore présents** (cible 0) — déféré ❌`.
- Ligne 589 (récap Étape 7 historique) : `0 JPA stub cross-service ✅ — 35 stubs supprimés en complétion (Étape 5)`.

**Contradiction directe** : `find` retourne 13.

**Fix** : ligne 589, remplacer le `✅` par `❌` et préciser : « 35 stubs initiaux du legacy retirés en complétion ; 13 stubs réintroduits par les extractions PR-3..PR-9 et toujours présents post-Étape 4 (suppression effective déférée Étape 4.5) ».

---

### DOC-009 — `data-model.md` mentionne « report-service » au lieu de « moderation-service » (P2)

**Catégorie** : Doc drift | **Sévérité** : P2 | **Effort** : XS

**Description**
`data-model.md:49` : « émis par report-service lors d'un BAN admin ». Le service est renommé `moderation-service` en Étape 2.1.2.

**Fix** : sed `report-service` → `moderation-service`.

---

### DOC-010 — `internal-endpoints.md` endpoint #4 mal documenté (P2)

**Catégorie** : Doc drift | **Sévérité** : P2 | **Effort** : XS

**Description**
Voir REST-002 / TEST-005. La phrase « Existant publiquement (déjà routé Kong sur /api/users/me/attendances), réutilisé en interne par user-service » est trompeuse — `/users/{id}/attendances` est un endpoint **distinct** (path-with-id, pas `/me`), et il n'existe pas du tout côté provider.

**Fix** : reformuler ou supprimer l'entrée selon la décision REST-002.

---

### DOC-011 — Commentaires JavaDoc référencent les services dissous (P2)

**Catégorie** : Doc drift | **Sévérité** : P2 | **Effort** : M

**Description**
Code Java avec mentions historiques (non bloquantes mais cosmétiques) :
- `shared-domain-dtos/.../FollowCounts.java`, `CoOrganizerCheck.java`, `CapacitySummary.java`, `AttendanceSummary.java`, `UserPublicResponse.java` JavaDoc citent follow-service / co-organizer-service / attendance-service / stats-service / report-service / comment-service.
- `shared-domain-dtos/pom.xml:19-23` liste les anciens consommateurs.
- `event-service/.../*.java` : citations dans `ShareResource`, `EventView`, `Favorite`, `UserFavoritesResource`, `EventDTO`, `EventCoOrganizer`, `MyCoOrganizerInvitationsResource`, `EventCoOrganizerService` (≥10 fichiers).
- `engagement-service/.../*.java` : `attendance-service`, `comment-service`, `co-organizer-service` dans entities/services/ServiceIdentityResource.
- `user-service/.../*.java` : `follow-service`, `calendar-service`, `favorite-service`, `attendance-service` dans calendar/ + UserService + FollowService + Follow.
- `moderation-service/.../*.java` : `report-service` dans ServiceIdentityResource, ReportService, ModerationCleanupJob, EventBannedPublisher, Report, EventStub, UserStub, EventCoOrganizerStub.

**Fix** : passe finale sed (low-risk) — `report-service` → `moderation-service`, `attendance-service` → `engagement-service` (renames), pour les autres ajouter une note `[absorbed in <Y>-service per consolidation 2.X.Y]`.

---

### SEC-001 — Anti-oracle ISSUE-92 retourne 404 sans body — viole pact + spec (P0)

Voir **REST-004**. Doublon sécurité-perspective : c'est le même bug.

---

### SEC-002 — `?check-co-org-of=` est un oracle de membership co-organizer enumérable (P1)

**Catégorie** : Sécurité | **Sévérité** : P1 | **Effort** : M

**Description**
`event-service.EventResource.java:120-129` est `@PermitAll`. `EventService.java:267-283` calcule `coOrganizerOf` pour n'importe quelle UUID fournie par n'importe quel caller (même anonyme), sur tout event PUBLISHED.

`internal-endpoints.md:22` claim que ce param est « interne ». Or la route Kong `events-by-id` (`configmap-routes.yaml:157-161`) proxie publiquement le path **avec tous ses query params** — Kong ne filtre pas le query param `check-co-org-of=`.

**Conséquence** : un attaquant anonyme peut enumérer « est `<UUID>` co-organizer/creator de l'event 42 ? » en boucle. Membership oracle. L'anti-oracle ISSUE-92 protège DRAFT/BANNED mais n'empêche pas cet usage abusif sur les events PUBLISHED.

**Fix recommandé** (3 options)
1. **Self-check seulement** : ignorer le param sauf si caller est authentifié ET `checkCoOrgOf == callerUserId`. Le client doit faire son propre check via son propre UUID.
2. **Header service-to-service** : exiger un header `X-Internal-Token: <secret>` (stripped par Kong sur le path public). Quand absent, ignorer le param.
3. **Path interne dédié** : déplacer la cascade vers un path qui n'est PAS routé Kong : `GET /__internal/events/{id}/co-org-check?userId=...`. Le path principal reste sans le param.

Trancher dans la spec finale.

---

### SEC-003 — Cascade SCRUM-136 inline dans `CommentService` (P2 — dette de drift)

**Catégorie** : Sécurité | **Sévérité** : P2 | **Effort** : S

**Description**
`engagement-service/.../comment/service/CommentService.java:178-211` re-implémente la cascade SCRUM-136 localement (lectures sur `EventStub` + `EventCoOrganizerStub`) au lieu d'appeler `eventServiceClient.getByIdWithCoOrgCheck(...)`. Deux read-models pour la même règle = drift risk dès qu'un côté évolue.

**Fix** : voir STUB-001 (suppression des stubs implique le wiring REST client → la cascade s'aligne automatiquement).

---

### DEP-001 — `notification-service` n'a pas `quarkus-jacoco` (P1)

**Catégorie** : Dépendances | **Sévérité** : P1 | **Effort** : XS

**Description**
notification-service est `jar` placeholder ; ne déclare ni `quarkus-jacoco` test scope ni `jacoco-maven-plugin`. Coverage = N/A. Asymétrique vs les 4 actifs.

**Fix** : ajouter `<dependency>quarkus-jacoco</dependency>` + plugin jacoco. Reste à 100% trivialement (1 sentinel test).

---

### DEP-002 — `engagement-service.UserAttendancesResource` cible un endpoint inexistant

Voir **REST-002**.

---

### HYGIENE-001 — 19 commits sans référence Étape/SPEC/SCRUM/BUG (P1)

**Catégorie** : Git/commits | **Sévérité** : P1 | **Effort** : XS (ou 0 si on accepte le tracking actuel)

**Description**
Sur les 121 commits de la PR, 19 omettent toute référence ticket dans titre ou body. Exemples :
- `ci(backend): fix Sonar scan dep resolution — verify → install in build matrix`
- `ci(backend): add sonar.projectKey override to shared-rate-limit + shared-storage POMs`
- `docs(backend): add sonarcloud-setup-guide.md for 15 projects post-consolidation`

**Impact** : traçabilité spec-commit dégradée. Pas bloquant pour merge.

**Fix recommandé** : pour les commits futurs, ajouter `(Étape X.Y)` ou `(<TICKET-ID>)` dans le titre. Pas de rebase suggested (force push interdit par convention).

---

### HYGIENE-002 — 16 commits `(infra)`/`(ci)` mélangés avec scope `(backend)` (P1)

**Catégorie** : Git/commits | **Sévérité** : P1 | **Effort** : XS

**Description**
La PR est titrée `refactor(backend): migrate to microservices...` mais inclut 16 commits avec scope `(infra)` ou `(ci)`. Exemples : `chore(infra): add Kong API gateway and Kafka broker`, `fix(infra): force kafka pod-template-hash bump per release`.

**Impact** : un PR review qui filtre par scope rate les changements infra.

**Fix recommandé** : pour les futures PRs, séparer infra et backend ou clarifier dans la PR title que les deux scopes sont concernés.

---

### HYGIENE-003 — Commit `66acd316` sans préfixe conventional (P2)

**Catégorie** : Git/commits | **Sévérité** : P2 | **Effort** : XS

**Description**
Titre : `Add microservices migration spec (backend)` au lieu de `feat(backend): add microservices migration spec`.

**Impact** : si la CI lint conventional-commit en strict mode, peut bloquer le merge.

**Fix** : amend ou laisse comme l'historique.

---

### HYGIENE-004 — Doc/code ratio 29.7% (proche du seuil 30%) (P2)

**Catégorie** : Git/commits | **Sévérité** : P2 | **Effort** : —

**Description**
- 36 commits docs / 67 commits code / 18 commits CI/chore.
- 36/121 = 29.7% — sous le seuil 30% mais juste.

**Status** : acceptable, mais à surveiller pour les futures PRs.

---

### TODO-001 — `AttendanceService:38-39` JavaDoc « will become a REST call » sur du code livré (P1)

**Catégorie** : TODOs | **Sévérité** : P1 | **Effort** : XS

**Description**
```java
// engagement-service/.../attendance/service/AttendanceService.java:35-40
/**
 * Same contract as the legacy AttendanceService — pessimistic-lock-based
 * capacity gating, idempotent attend, WAITLISTED auto-promotion on
 * remove. The SCRUM-136 cascade is inlined locally (will become a REST
 * call to co-organizer-service in a follow-up cleanup).
 */
```

co-organizer-service est dissous depuis Étape 2.2.4. Le commentaire est obsolète et trompeur.

**Fix** : reformuler : « cascade SCRUM-136 inline (sera remplacée par `eventServiceClient.getByIdWithCoOrgCheck(...)` à l'Étape 4.5) ».

---

### TODO-002 — `frontend/searchApi.ts` stub `fetchSuggestions()` (P2)

**Catégorie** : TODOs | **Sévérité** : P2 | **Effort** : S (post-PR)

**Description**
`frontend/src/services/searchApi.ts:19-20` : `fetchSuggestions()` retourne `[]` par défaut. Endpoint backend `GET /api/events/search/suggestions` inexistant.

**Status** : le frontend invariant (0 ligne diff) est tenu — ce TODO ne touche pas la PR. À tracker côté frontend pour S6+.

---

### OPENAPI-001 — TODOs Sprint futurs dans `openapi.yaml` (P2)

**Catégorie** : OpenAPI | **Sévérité** : P2 | **Effort** : —

**Description**
5 TODOs sprint futurs (S3, S5, S7, S8) dans `openapi.yaml`. Documentés et légitimes. Aucun code généré impacté.

**Status** : laisser tel quel. À éliminer en sprint correspondant.

---

### BORDER-001 — Invariants frontaliers : tous PASS (—)

**Catégorie** : Invariants | **Sévérité** : OK | **Effort** : —

| Invariant | Résultat |
|---|---|
| `git diff --shortstat origin/main HEAD -- frontend/` | 0 ligne ✅ |
| `git diff --shortstat origin/main HEAD -- openapi/` | 0 ligne ✅ |
| Aucun `@Disabled` / `@Ignore` ajouté | ✅ |
| Aucun `--no-verify` / `--no-gpg-sign` / force push | ✅ |
| Working tree clean | ✅ |
| Build local SUCCESS sur 17 modules | ✅ |
| 35 sentinels par nom verts | ✅ (4 implémentés + 31 placeholders) |
| 3 `@RegisterRestClient` interfaces | ✅ |
| 17 modules dans le reactor | ✅ |
| 5 dossiers `<svc>-service/` Helm | ✅ |
| Pas de fichier binaire committé | ✅ |
| Pas de secret accidentellement committé | ✅ |

---

## Synthèse par catégorie

| Catégorie | Findings | P0 | P1 | P2 | Effort agrégé |
|---|---|---|---|---|---|
| **STUB** (JPA stubs) | 1 | 1 | — | — | XL — bloque "0 stubs cross-service" criterion |
| **REST** (REST clients runtime) | 4 | 2 | 2 | — | M — bloque le wiring runtime |
| **CI/Sonar** | 8 | 1 | 4 | 3 | M — bloque le quality gate |
| **DUP** (duplicats locaux) | 6 | — | 5 | 1 | L — cosmétique mais XL en file count |
| **TEST** | 5 | — | 2 | 3 | XL (port sentinels) |
| **COV** (couverture) | 2 | — | 1 | 1 | XL — dépend de TEST |
| **K8S** | 1 | — | 1 | — | XS |
| **KAFKA** | 1 | — | — | 1 | S |
| **DOC** (drift) | 11 | 1 | 7 | 3 | M — sed pass + manual edits |
| **SEC** | 3 | 1 (= REST-004) | 1 | 1 | M |
| **DEP** | 2 | — | 1 | 1 | XS |
| **HYGIENE** | 4 | — | 2 | 2 | XS |
| **TODO** | 3 | — | 1 | 2 | XS |
| **OPENAPI** | 1 | — | — | 1 | — |
| **BORDER** | 1 (OK) | — | — | — | — |

**Total** : 52 findings, **6 P0**, 18 P1, 28 P2.

---

## Recommandations pour la spec finale

La spec finale doit être structurée pour permettre **une session unique de finalisation runtime + handoff DevOps propre**. Recommandations par priorité :

### Priorité 1 — Bloquants P0 (ordre strict)

1. **CI-001 — fix Sonar `top level project`** (XS) : modif CI YAML uniquement, pas de risque code. Premier commit.
2. **REST-001 — `quarkus.rest-client.<svc>.url` dans les 4 consumers** (S) : prerequis runtime pour tout le wiring suivant.
3. **REST-002 — créer `UserAttendancesInternalResource` côté engagement-service** (S) — OU décider de retirer la méthode du `EngagementServiceClient` (trancher dans la spec).
4. **REST-004 / SEC-001 — `NotFoundExceptionMapper` dans shared-api-error** (S) : débloque la pact verification.
5. **STUB-001 — wiring REST clients consumer + suppression des 13 stubs JPA** (XL) : ~5-7 commits dans l'ordre :
   - 5.1 — engagement-service (3 stubs : Event, User, EventCoOrganizer + Comment refactor)
   - 5.2 — moderation-service (3 stubs : Event, User, EventCoOrganizer + Report refactor)
   - 5.3 — user-service (3 stubs : Event, Attendance, Favorite + IcsBuilder/CalendarService refactor)
   - 5.4 — event-service (4 stubs : User, Attendance, EventViewStub→local, FavoriteStub redundant + Event refactor)
   - 5.5 — validation `find ... -name '*Stub.java' \| wc -l` = 0
6. **DOC-001 — corriger `devops-handoff.md` TL;DR** (XS) : passe simultanée à la fin.

### Priorité 2 — Important pour le handoff DevOps (P1)

7. **CI-002 — consolidation 10 → 1 cellule shared-libs** (M)
8. **CI-003 — retirer `continue-on-error`** (XS) après création des projets DevOps + fix CI-001
9. **DUP-001..006 — bascule shared libs** (S+S+XS+M+L+M = ~3 commits)
10. **DOC-002..008 — sed pass + manual edits doc drift** (M)
11. **K8S-001 — livenessProbe notification-service** (XS)
12. **DEP-001 — quarkus-jacoco notification-service** (XS)
13. **TEST-001 — port des 31 sentinels placeholders** (XL — par batch sur plusieurs sessions ou sprints)
14. **TEST-002 — pact AttendanceSummary** (S)
15. **REST-003 — admin bypass ISSUE-93 dans UserService** (S)
16. **SEC-002 — fix `?check-co-org-of=` oracle** (M) — décision architecture (3 options dans le finding)
17. **TODO-001 — fix JavaDoc `AttendanceService` obsolète** (XS)
18. **HYGIENE-001/002 — pour les commits FUTURS** (process — pas de fix rétroactif)

### Priorité 3 — Cosmétique (P2)

19. **CI-004..008** — divers cosmétiques (peuvent être groupés en un seul commit "ci hygiene")
20. **DUP-006 — helpers dupliqués** (M)
21. **TEST-003/KAFKA-001 — `EventLifecycleKafkaBridgeTest`** (S)
22. **TEST-004 — supprimer scaffolds redondants** (XS)
23. **DOC-009..011 — passe sed services dissous** (M)
24. **SEC-003 — cascade inline CommentService** (S — résolu par STUB-001)
25. **HYGIENE-003/004** — informational
26. **TODO-002 — frontend stub** (S, hors scope PR)
27. **COV-002 — étoffer tests shared-domain-dtos** (S)
28. **OPENAPI-001** — laisser tel quel

### Priorité 4 — DevOps S9+ (out of scope pour la PR)

(cf. `devops-handoff.md` items 2-7 — Cluster Kafka prod-grade, Schemas-per-service, NetworkPolicies, Domains/certs, Doppler secrets, Production Kong)

---

## Annexes

### Annexe A — Liste exhaustive des 13 stubs avec call-sites

#### engagement-service (3 stubs)

**1. `engagement/attendance/entity/EventStub.java`** (cross-service, table=`events`)
- 16 lignes, fields: title, description, location, startDate, endDate, category, faculty, bannerUrl, creatorId, status, capacity, allDay, featured, featuredAt, websiteUrl, contactEmail, registrationDeadline, tags, parentEventId, recurrenceRule, createdAt, updatedAt.
- Call-sites :
  - `AttendanceService.java`: `entityManager.find(EventStub.class, eventId, LockModeType.PESSIMISTIC_WRITE)` (×3), `EventStub event = ...`
  - `AttendanceService.java::getMyParticipationEvents`: `EventStub.list("id IN ?1", ids)`
  - `AttendanceService.java::dto.EventDTO`: from(EventStub) factory
  - `CommentService.java::assertEventVisibleAndLoad`: `EventStub.findByIdOptional(eventId)`
  - `Comment.java`: `@ManyToOne EventStub event`
  - `engagement.attendance.dto.EventDTO.java`: `EventDTO.from(EventStub)`

**Refactor** : `engagementClient` ne suffit pas (capacity gating utilise PESSIMISTIC_WRITE → besoin d'event-service du côté event-domain). Solution: REST client `eventServiceClient.getById(id)` pour les lectures (anti-oracle), pessimistic lock reste local SUR `event-service` quand attendance déménage la logique (ou reste sur engagement avec lock advisory). À trancher.

**2. `engagement/attendance/entity/UserStub.java`** (cross-service, table=`users`)
- 12 lignes, fields: id, auth0Id, displayName, avatarUrl. Méthode statique: `findByAuth0Id(auth0Id)`.
- Call-sites :
  - `AttendanceService.java`: `UserStub user = resolveUser(auth0Id)` (× plusieurs)
  - `AttendanceService.java::dto.AttendanceDTO`: factory
  - `CommentService.java::post`: `UserStub.findByAuth0Id(auth0Id)`
  - `CommentService.java::isCreatorOrAcceptedCoOrganizer`: idem
  - `Comment.java`: `@ManyToOne UserStub author`

**Refactor** : `Auth0IdResolver.resolveUserId(jwt)` (déjà dans shared-domain-projections) + `userServiceClient.getById(uuid)`. `Comment.author` devient `@Column UUID authorId` (id only, pas de navigation JPA).

**3. `engagement/attendance/entity/EventCoOrganizerStub.java`** (cross-service, table=`event_co_organizers`)
- ~30 lignes, méthodes: `isAcceptedFor(eventId, userId)`, `findAcceptedUserIdsForEvent(eventId)`.
- Call-sites :
  - `CommentService.java::isCreatorOrAcceptedCoOrganizer`: `EventCoOrganizerStub.isAcceptedFor(...)`
  - `CommentService.java::computeOrganizerUserIds`: `EventCoOrganizerStub.findAcceptedUserIdsForEvent(...)`
  - `AttendanceService.java`: usage similaire (cascade SCRUM-136)

**Refactor** : remplacer par `eventServiceClient.getByIdWithCoOrgCheck(eventId, callerUUID)` qui retourne le payload + `coOrganizerOf: bool`. La méthode `findAcceptedUserIdsForEvent(eventId)` n'a pas d'équivalent REST direct → soit ajouter un endpoint `GET /events/{id}/co-organizers/accepted-user-ids` (conflit avec `internal-endpoints.md` qui dit endpoint disparu), soit passer caller-by-caller.

#### moderation-service (3 stubs)

**4. `report/entity/EventStub.java`** (cross-service, table=`events`, **WRITABLE pour `event.status = BANNED`**)
- 7 lignes, fields: title, status, creatorId.
- Call-sites :
  - `ReportService.java::handle`: `EventStub event = entityManager.find(EventStub.class, eventId)` + `event.status = BANNED`
  - `ModerationCleanupService.java::runCleanup`: idem
  - `Report.java`: `@ManyToOne EventStub event`

**Refactor** : c'est un cas SPÉCIAL. Le ban s'écrit aujourd'hui par mutation directe + Kafka producer `events.banned`. Post-stubs : retirer la mutation et garder uniquement le Kafka producer (event-service consume idempotent et applique le BAN). Ce changement est **plus profond** que les autres stubs.

**5. `report/entity/UserStub.java`** (cross-service, table=`users`)
- ~15 lignes, fields: id, auth0Id, displayName, firstName, lastName, email.
- Call-sites :
  - `ReportService.java::report` : `UserStub.findByAuth0Id(auth0Id)`
  - `ReportService.java::list/handle` : enrichissement reporterDisplayName
  - `Report.java`: `@ManyToOne UserStub reporter`, `@ManyToOne UserStub reviewedBy`
  - `ReportDTO.java`: factory

**Refactor** : id-only (`UUID reporterId`, `UUID reviewedById`) + `userServiceClient.getById(uuid)` pour enrichir.

**6. `report/entity/EventCoOrganizerStub.java`** (cross-service)
- Identique au stub 3.
- Call-sites: `ReportService.java` cascade « can't report own event ».

**Refactor** : `eventServiceClient.getByIdWithCoOrgCheck(...)`.

#### user-service (3 stubs)

**7. `user/calendar/entity/EventStub.java`** (cross-service, READ-ONLY pour ICS feed)
- ~10 lignes, fields: title, description, location, startDate, endDate, status.
- Call-sites :
  - `CalendarService.java::generateIcs`: `EventStub.list("id IN ?1 AND status = ?2", favoriteEventIds, PUBLISHED)`
  - `IcsBuilder.java`: lecture des champs

**Refactor** : `eventServiceClient.findByIds(ids, "PUBLISHED")` (le bulk lookup est déjà spec'd en 4.4). Très propre — pas de pessimistic lock, pas de cascade, juste lecture.

**8. `user/calendar/entity/AttendanceStub.java`** (cross-service)
- Read-only sur `attendances`.
- Call-sites: `CalendarService.java` pour collecter les eventIds where user attendance.

**Refactor** : `engagementServiceClient.getUserAttendances(userId, "ATTENDING")` puis extraire les eventIds.

**9. `user/calendar/entity/FavoriteStub.java`** (cross-service)
- Read-only sur `favorites`.
- Call-sites: `CalendarService.java` pour collecter les eventIds where user favorite.

**Refactor** : pas d'endpoint REST direct (favorites est local event-service post-2.2.3). Soit ajouter un endpoint `GET /users/{id}/favorite-event-ids` côté event-service, soit faire passer le user-service par le path public favorite (déjà routé Kong) avec OIDC propagation. Trancher.

#### event-service (4 stubs)

**10. `event/entity/UserStub.java`** (cross-service, table=`users`)
- 17 lignes. Fields: id, auth0Id, displayName, avatarUrl. `findByAuth0Id`.
- Call-sites: ~10 dans EventService, FavoriteService, EventCoOrganizerService, EventStatsService, EventViewService, MyEventsService, etc.
- `Event.java`: `@ManyToOne UserStub creator`.

**Refactor** : id-only `Event.creatorId` + `userServiceClient.getById(uuid)` pour enrichir DTOs. Beaucoup de call-sites mais mécanique.

**11. `event/entity/AttendanceStub.java`** (cross-service, table=`attendances`)
- Lecture + helper `countGroupedByStatus(eventIds, status, em)`.
- Call-sites : EventSearchService, FeaturedService, EventCoOrganizerService, EventStatsService, EventService, MyEventsService, FavoriteService.

**Refactor** : `engagementServiceClient.getAttendanceSummary(eventId)` pour les counts. Pour les bulk (`countGroupedByStatus(ids, status, em)`), besoin d'un endpoint bulk côté engagement → ajouter `GET /events/attendance-summary?ids=...&status=...` au catalogue interne (pas dans `internal-endpoints.md` actuellement).

**12. `event/entity/EventViewStub.java`** (NON cross-service, table=`event_views` LOCALE)
- C'est une **redondance avec `event/view/entity/EventView.java`**.
- Call-sites: 1 dans EventService.

**Refactor** : remplacer par `EventView` direct.

**13. `event/entity/FavoriteStub.java`** (REDUNDANT — table=`favorites` LOCALE)
- Doublon de `event/favorite/entity/Favorite.java` qui mappe la même `@Table(name="favorites")`. Hibernate accepte 2 entités sur la même table (entityName ≠).
- Call-sites: 3 dans FeaturedService, EventService, CalendarService.

**Refactor** : pure suppression — utiliser `Favorite` directement.

---

### Annexe B — Diff CI matrix avant/après simplification proposée

**Avant** (HEAD ec668b91, 17 jobs CI matrix) :
```yaml
build-shared-libs: matrix.lib × 10 cells   # 10 cellules
build-backend: matrix.service × 5 cells     # 5 cellules
build-contract-and-e2e                       # 1 cellule
build-frontend                               # 1 cellule
# Total: 17 cellules
```

**Après** (proposition CI-001 + CI-002, 8 jobs CI matrix) :
```yaml
build-shared-libs:                          # 1 cellule (10 libs en bulk)
  run: ./mvnw -pl <10-libs> -am install -B
build-backend: matrix.service × 5 cells     # 5 cellules
  # avec sonar:sonar combiné en fin de step:
  # ./mvnw -pl .,services/<X>-service -am verify sonar:sonar -B
build-contract-and-e2e                       # 1 cellule
verify-pacts (post-merge)                   # 1 cellule (optionnelle)
build-frontend                               # 1 cellule
# Total: 8-9 cellules. Gain ~25 min/run.
```

---

### Annexe C — Inventaire des duplicats locaux à supprimer

| Catégorie | Fichiers | LOC totale | Call-sites | Effort suppression |
|---|---|---|---|---|
| ApiErrorResponse | 4 | 40 | 31 | XS (sed + dep) |
| ServiceIdentityResource | 5 | 150 | 5 | S (test event-service à mettre à jour) |
| Timeframe | 2 | 12 | 11 | XS |
| Enums (9 distincts) | 21 | ~210 | ~230 | M (sed + dep) |
| DTOs (3 distincts) | 7 | ~280 | ~141 | L (factories à porter) |
| Helpers | 5 | ~50 | 27 | M |
| **TOTAL** | **44** | **~742** | **~445** | **3-4 commits** |

---

### Annexe D — Tableau complet de couverture jacoco

| Module | % L | % B | Lines | Branches | Cible | Verdict |
|---|---|---|---|---|---|---|
| event-service | 5.4% | 0.5% | 64/1178 | 4/824 | 80% / 70% | ❌ |
| user-service | 6.2% | 1.0% | 39/625 | 5/498 | 80% / 70% | ❌ |
| engagement-service | 10.8% | 1.0% | 59/545 | 4/410 | 80% / 70% | ❌ |
| moderation-service | 17.2% | 3.0% | 38/221 | 5/164 | 80% / 70% | ❌ |
| notification-service | n/a | n/a | n/a | n/a | n/a | placeholder |
| shared-api-error | 100% | 0% | 16/16 | 0/0 | 95% / 90% | ✅ |
| shared-domain-dtos | 63.2% | 0% | 12/19 | 0/0 | 95% / 90% | ❌ |
| shared-domain-enums | 100% | 0% | 46/46 | 0/0 | 95% / 90% | ✅ |
| shared-domain-projections | 100% | 100% | 6/6 | 4/4 | 95% / 90% | ✅✅ |
| shared-jaxrs | 100% | 100% | 12/12 | 6/6 | 95% / 90% | ✅✅ |
| shared-kafka-events | 100% | 0% | 26/26 | 0/0 | 95% / 90% | ✅ |
| shared-platform | 100% | 0% | 2/2 | 0/0 | 95% / 90% | ✅ |
| shared-rate-limit | 100% | 100% | 67/67 | 22/22 | 95% / 90% | ✅✅ |
| shared-storage | 100% | 100% | 101/101 | 36/36 | 95% / 90% | ✅✅ |
| shared-tracing | 100% | 100% | 17/17 | 8/8 | 95% / 90% | ✅✅ |

**Note** : les 31 sentinels placeholders ne gonflent pas le compteur (jacoco mesure src/main/java, pas src/test/java).

---

### Annexe E — Inventaire des 4 + 1 pacts générés

| Pact JSON | Consumer | Provider | Interactions | Sous-étape |
|---|---|---|---|---|
| `engagement-service-event-service.json` | engagement-service | event-service | 4 (ISSUE-92×2 + SCRUM-136×2) | 6.1 + 6.2 |
| `moderation-service-event-service.json` | moderation-service | event-service | 1 | 6.3 |
| `user-service-event-service.json` | user-service | event-service | 1 (calendar bulk) | 6.4 |
| `<MISSING>-engagement-service.json` | event/moderation | engagement-service | 0 | TEST-002 — à créer |
| `e2e/E2EHappyPathTest.java` | (E2E happy path) | — | — | 6.5 |

**Total interactions** : 6 (cible spec : 4 pacts JSON, 6+ interactions au total).

---

### Annexe F — Synthèse des actions par fichier (top 20)

Ordre de priorité d'édition pour la spec finale :

1. `.github/workflows/build.yml` — fix CI-001 + CI-002 + CI-003 + CI-006 + CI-007.
2. `backend/services/{event,user,engagement,moderation}-service/src/main/resources/application.properties` — REST-001.
3. `backend/services/shared-api-error/src/main/java/.../NotFoundExceptionMapper.java` (nouveau) — REST-004.
4. `backend/services/engagement-service/src/main/java/.../resource/UserAttendancesInternalResource.java` (nouveau) — REST-002.
5. `backend/services/user-service/src/main/java/.../service/UserService.java` — REST-003.
6. `backend/services/{engagement,moderation,user,event}-service/.../**/*Stub.java` — STUB-001 (suppression × 13).
7. `backend/services/{engagement,moderation}-service/src/main/java/.../{Comment,Report}.java` — refactor `@ManyToOne` → `@Column id`.
8. `backend/services/event-service/src/main/java/.../entity/Event.java` — refactor `@ManyToOne UserStub creator` → `@Column UUID creatorId`.
9. `backend/services/{event,user,engagement,moderation}-service/.../{**/*ApiErrorResponse,*ServiceIdentityResource,*Timeframe}.java` — DUP-001/002/003 (suppression × 11).
10. `backend/services/{event,user,engagement,moderation}-service/.../entity/{EventStatus,EventCategory,Faculty,AttendanceStatus,CoOrganizerStatus,FollowStatus,RecurrenceFrequency,ReportStatus,ReportReason}.java` — DUP-004 (suppression × 21).
11. `backend/docs/devops-handoff.md` (TL;DR fix) — DOC-001.
12. `backend/docs/architecture.md` (multiples sections) — DOC-002 + DOC-003 + DOC-007 + DOC-011.
13. `backend/docs/api-contract.md` (refonte topologie) — DOC-006.
14. `backend/docs/microservices-migration-roadmap.md` (header archive) — DOC-004.
15. `backend/docs/sprint-context.md` (correction l. 589) — DOC-008.
16. `AGENTS.md` (racine) + `backend/AGENTS.md` — DOC-005.
17. `backend/docs/internal-endpoints.md` (endpoint #4 reformulation) — DOC-010.
18. `backend/services/notification-service/pom.xml` — DEP-001.
19. `k8s/chart/templates/notification-service/deployment.yaml` — K8S-001.
20. `backend/contract-tests/src/test/java/.../EventEngagementAttendancePactTest.java` (nouveau) — TEST-002.

---

### Annexe G — Stratégie d'enchainement des commits pour la spec finale

Estimation : ~20-25 commits structurés en 4 vagues.

**Vague 1 — CI/Sonar fixes (3 commits)**
1. `ci(backend): fix sonar:sonar with -pl .,<X> for top-level project (CI-001)`
2. `ci(backend): consolidate 10 shared-libs cells → 1 (Option B alignment, CI-002)`
3. `ci(backend): drop continue-on-error after CI fixes (CI-003)`

**Vague 2 — REST clients runtime + envelope (5-6 commits)**
4. `feat(backend): add quarkus.rest-client.<svc>.url config to 4 consumers (REST-001)`
5. `feat(backend): add NotFoundExceptionMapper to shared-api-error (REST-004 / SEC-001)`
6. `feat(backend): create UserAttendancesInternalResource in engagement-service (REST-002)`
7. `fix(backend): UserService admin bypass on private profile (REST-003 / ISSUE-93)`
8. `feat(backend): wire EventServiceClient + UserServiceClient + EngagementServiceClient in 4 consumers, delete 13 JPA stubs (STUB-001, Étape 4.5)` — gros commit (à splitter en 4-5 par service)

**Vague 3 — Duplicats + docs (4 commits)**
9. `refactor(backend): adopt shared-api-error + shared-domain-enums across 4 services (DUP-001, DUP-004)`
10. `refactor(backend): adopt shared-platform + shared-jaxrs across 5 services (DUP-002, DUP-003)`
11. `refactor(backend): adopt shared-domain-dtos DTOs in consumer services (DUP-005)`
12. `docs(backend): align multiple docs with post-finalization 5-service reality + Option B (DOC-001..010)`

**Vague 4 — Tests + finitions (5-7 commits)**
13. `test(backend): add EventEngagementAttendancePactTest (TEST-002)`
14. `test(backend): add EventLifecycleKafkaBridgeTest (KAFKA-001 / TEST-003)`
15. `test(backend): port 31 sentinel placeholders to real assertions (TEST-001)` — XL, à splitter par service en sous-commits
16. `chore(backend): livenessProbe on notification-service deployment (K8S-001)`
17. `chore(backend): add quarkus-jacoco to notification-service (DEP-001)`
18. `chore(backend): clean stale JavaDoc references to dissolved services (DOC-011, TODO-001)`
19. `test(backend): drop redundant scaffold tests (TEST-004)`
20. `docs(backend): update sprint-context.md final + PR body (Étape 9 final)`

**Vague 5 — Sécurité (1-2 commits)**
21. `fix(backend): mitigate ?check-co-org-of= co-organizer membership oracle (SEC-002)`

---

## Conclusion

La PR #158 a livré **la charpente complète** d'une migration microservices Sprint 8 :
- ✅ Topologie 5 services + 17 modules (consolidation 14→5 livrée).
- ✅ Helm/K8s (5 deployments, Kong, Kafka 10 topics, livenessProbe sur 4/5).
- ✅ Kafka (9 producteurs + 1 consumer câblés correctement post-merges).
- ✅ Anti-oracles ISSUE-92/93 + cascade SCRUM-136 implémentés.
- ✅ 3 `@RegisterRestClient` interfaces + 4 pacts JSON brokerless.
- ✅ Invariants frontaliers : 0 ligne diff frontend, 0 ligne diff openapi.

Mais **le runtime n'est pas câblé** :
- ❌ 13 stubs JPA cross-service persistent.
- ❌ Aucun consumer n'a `quarkus.rest-client.<svc>.url`.
- ❌ Le pact `EngagementEventIssue92PactTest` cassera au provider verify dès qu'il sera câblé.
- ❌ La couverture services métiers est à 5–17% (cible 80%) — les 31 sentinels placeholders n'aident pas.
- ❌ Sonar plante avec « Maven session does not declare a top level project ».
- ❌ Doc drift : plusieurs docs (devops-handoff TL;DR, architecture.md, AGENTS.md, roadmap.md) annoncent un état qu'on n'a pas atteint.

Une **dernière session** structurée selon les 5 vagues de l'**Annexe G** (estimation ~25 commits / ~40-60h focused) suffirait à clore proprement le sprint.

Le présent audit sert de cahier des charges pour cette spec finale.
