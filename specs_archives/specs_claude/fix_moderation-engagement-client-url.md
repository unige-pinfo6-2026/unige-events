# Spécification technique — Fix : `POST /events/{id}/report` renvoie 500 (config REST-client manquante)

> Branche : `fix/moderation-engagement-client-url`
> Statut : **analyse + fix** (root cause prouvée par les logs prod)
> Date : 2026-05-28
> Périmètre : **backend uniquement** — moderation-service. Incident **prod** (signalement d'événement cassé pour tous).

---

## 1. Résumé du bug

Signaler un événement (« Signaler cet événement » → motif Spam) échoue avec le toast générique « Impossible d'envoyer le signalement. ». Diagnostic réseau : `POST /api/events/{id}/report` → **HTTP 500** sur tous les events testés (2, 5, 201).

---

## 2. Root cause (prouvée par les logs prod)

Logs moderation-service (error id `bc2c4af3-…` repris de l'envelope Quarkus côté browser) :

```
ERROR QuarkusErrorHandler — HTTP Request to /api/events/2/report failed:
java.lang.RuntimeException: Error injecting
  ch.unige.events.shared.client.EngagementServiceClient
  ch.unige.events.report.service.ReportService.engagementClient
Caused by: java.lang.IllegalArgumentException: Unable to determine the proper
  baseUrl/baseUri. … add 'quarkus.rest-client.engagement-service.url' …
```

- `ReportService` injecte **trois** clients REST en champ ([ReportService.java:62-64](../../backend/services/moderation-service/src/main/java/ch/unige/events/report/service/ReportService.java)) : `eventClient`, `userClient`, **`engagementClient`**.
- `EngagementServiceClient` est `@RegisterRestClient(configKey = "engagement-service")` → exige `quarkus.rest-client.engagement-service.url`.
- **`moderation-service/application.properties` ne définissait que `event-service.url` et `user-service.url`** — pas `engagement-service.url`.
- L'injection d'un `@RestClient` en champ est résolue **à la création du bean** : URL absente → le bean `ReportService` ne s'instancie pas → **500 sur TOUT appel à `ReportService`**, y compris `create()` (signalement d'événement) qui n'utilise jamais `engagementClient`.

### Pourquoi `engagementClient` est là
Ajouté en SCRUM-144 pour `createForComment()` (signalement de **commentaire** → `GET /comments/{id}/_internal-visibility`). Le champ a été ajouté sans la ligne de config correspondante.

### Pourquoi les tests n'ont rien vu
Les `@QuarkusTest` de moderation utilisent `@InjectMock @RestClient EngagementServiceClient` → le vrai client n'est jamais construit, le baseUri manquant est masqué. Classique « vert en test, rouge en prod ». De plus, **moderation-service est le seul service sans `*ClientWiringTest`** (event/user/engagement en ont un) — donc aucun garde-fou sur le wiring de ses clients.

---

## 3. Fix

### 3.1 Config (le vrai fix — débloque la prod)
Ajout dans [`moderation-service/application.properties`](../../backend/services/moderation-service/src/main/resources/application.properties), bloc copié verbatim de user-service :

```properties
quarkus.rest-client.engagement-service.url=${ENGAGEMENT_SERVICE_URL:http://engagement-service:8080/api}
%dev.quarkus.rest-client.engagement-service.url=http://localhost:8083/api
quarkus.rest-client.engagement-service.connect-timeout=2000
quarkus.rest-client.engagement-service.read-timeout=5000
```

Le défaut in-cluster `http://engagement-service:8080/api` résout sans variable d'env (même mécanique que `event-service.url`) → pas de changement Doppler/helm requis.

### 3.2 Garde-fou régression — `EngagementServiceClientWiringTest`
Nouveau `@QuarkusTest` qui injecte le **vrai** `@RestClient EngagementServiceClient` et appelle `getAttendanceSummary(42L)`. Particularité : il **n'override PAS l'URL** (elle vient de `application.properties` — c'est ce qu'on garde), seulement les timeouts. Si la ligne de config par défaut disparaît → l'injection échoue → test ROUGE. Mirroir du pattern `*ClientFallbackWiringTest` des autres services.

---

## 4. Tests / validation

| Check | Attendu |
|---|---|
| `mvn test-compile -pl services/moderation-service -am` | vert |
| `EngagementServiceClientWiringTest` (CI, DevServices) | vert : bean injectable + fallback `AttendanceSummary.of(0,0)` |
| Tests moderation existants (ReportService, CommentReport…) | inchangés, verts |
| Manuel prod après deploy | `POST /events/{id}/report` → 201 (ou 409/422 selon le cas), plus de 500 |

---

## 5. Critères d'acceptation

| # | Scénario | Résultat |
|---|---|---|
| AC-1 | Utilisateur signale un événement (motif + description) | 201 Created, plus de 500 |
| AC-2 | Re-signalement du même event par le même user | 409 « déjà signalé » |
| AC-3 | Signalement de son propre event | 422 |
| AC-4 | Signalement de commentaire (SCRUM-144) | fonctionne (le client engagement résout) |
| AC-5 | Régression config : retirer `engagement-service.url` | `EngagementServiceClientWiringTest` rouge |

---

## 6. Hors périmètre (à traiter séparément)

- **Bannières `minio:9000` (`ERR_NAME_NOT_RESOLVED`)** : certains events (ex. 201) ont un `bannerUrl` pointant l'hôte interne `minio:9000` au lieu du `/s3/` public — même famille que `207e7c18 fix(s3): serve public S3 URLs`. Données héritées ayant échappé à la migration. À corriger dans une PR dédiée (backfill `bannerUrl` ou réécriture à la lecture).
- **Frontend `useReport`** : mappe tout statut ≠ 409/422 vers le message générique → un 500/404/400 sont indistinguables côté UX. Amélioration possible (afficher un message distinct sur 5xx), non bloquant.
