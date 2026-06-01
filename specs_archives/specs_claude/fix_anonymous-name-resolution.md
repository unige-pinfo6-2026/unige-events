# Spécification technique — Fix : noms en hash UUID pour les visiteurs anonymes

> Branche : `fix/anonymous-name-resolution`
> Statut : **analyse + plan** (aucun changement de code à ce stade)
> Date : 2026-05-30
> Périmètre : **frontend + (option) backend event-service**.

---

## 1. Résumé du bug rapporté

En **non-connecté**, sur la page détail d'un événement, les noms s'affichent en hash UUID :
- « Équipe organisatrice » → `@1adb2ea7-0dca-4e48-b3…` (organisateur)
- Commentaires → `b335d1fb` (commentateur)

## 2. Diagnostic — vérifié en live (requêtes anonymes sur prod)

Sondage anonyme (sans header `Authorization`) sur `https://pinfo6.p-info.net/api`, event **725** (PUBLISHED, créateur `1adb2ea7-…`) :

| Appel anonyme | Résultat | Conclusion |
|---|---|---|
| `GET /events/725/comments` | `authorDisplayName:"Viona"`, `"Elie"`, `authorUsername:"viona"/"elie.bxd"` | ✅ commentaires **enrichis** en anon |
| `GET /users/1adb2ea7-…` | `displayName:"Asso des étudiants en Informatique"`, `username:"aei"` | ✅ profil public **résolu** en anon (`@PermitAll`) |
| `GET /events/725` | `creatorId` seul, **aucun** `creatorDisplayName`/`creatorUsername` | l'event DTO ne porte pas le nom créateur |
| `GET /events/725/co-organizers` | **HTTP 401** | ❌ endpoint `@Authenticated` |

### 2.1 Commentateur — déjà corrigé
`CommentItem` lit `comment.authorDisplayName` ([CommentItem.tsx:70-76](../../frontend/src/components/event/CommentItem.tsx)). Le backend renvoie le nom en anon. → **plus de bug** ; le screenshot date d'avant le déploiement du `/users/{id}` `@PermitAll`.

### 2.2 Organisateur (créateur) — déjà corrigé, mais fallback latent à durcir
`EventDetailPage` fait `getUserById(event.creatorId)` dans un `useEffect` **non gated** ([EventDetailPage.tsx:351](../../frontend/src/pages/event/EventDetailPage.tsx)) → `/users/{id}` renvoie 200 + nom en anon → l'organisateur se résout.
**MAIS** ligne 764 : `creatorUsername={organizer?.username ?? event.creatorId}`. Si `getUserById` échoue (5xx, réseau, ou avant résolution), on passe **l'UUID comme username** → `userDisplayLabel(null, <uuid>, <uuid>)` renvoie `'@' + <uuid>` = `@1adb2ea7-…` (exactement le screenshot). Défaut latent : il faut passer `null`, pas l'UUID, pour tomber sur un fallback propre.

### 2.3 Co-organisateurs — toujours cassé en anon
`GET /events/{id}/co-organizers` est `@Authenticated` (les 5 méthodes du resource le sont, [EventCoOrganizerResource.java:42-80](../../backend/services/event-service/src/main/java/ch/unige/events/event/coorganizer/resource/EventCoOrganizerResource.java)). En anon → 401 → `useCoOrganizers` catch → `coOrganizers=[]` → **aucune ligne co-organisateur** rendue. C'est le seul bug réellement non résolu.

> Endpoint public alternatif : `GET /events/{id}/organizer-uuids` (`@PermitAll`, ADR-002) renvoie créateur + co-orgs **ACCEPTED** (UUID seuls, pas de PENDING/DECLINED). Combiné à `getUserById` (anon-OK), il permet de résoudre les noms sans relâcher la sécurité.

---

## 3. Plan de correction

### 3.1 Volet A — Durcir le fallback créateur (cheap, sans regret) — FRONTEND
[`EventDetailPage.tsx:764`](../../frontend/src/pages/event/EventDetailPage.tsx) :
```diff
- creatorUsername={organizer?.username ?? event.creatorId}
+ creatorUsername={organizer?.username ?? null}
```
`OrganizerRow` accepte déjà `username: string | null` ; avec `null`, le profil-link retombe sur `userId` et le label sur `userDisplayLabel(displayName, null, userId)` → `slice(0,8)` (court) au lieu de `@<uuid-complet>`. Et comme `getUserById` résout en anon, le cas normal affiche le vrai nom.

### 3.2 Volet B — Co-organisateurs visibles en anonyme — DÉCISION REQUISE

**Option B1 — Frontend via `organizer-uuids` (recommandé, zéro changement sécurité/openapi)**
`EventOrganizerTeam` résout les co-orgs ACCEPTED via `GET /events/{id}/organizer-uuids` (public) → retire le créateur → `getUserById` par UUID (anon-OK) → rend. L'endpoint `@Authenticated` `/co-organizers` reste réservé à l'éditeur (`CoOrganizersEditor`). Avantages : aucun changement backend, aucun risque de fuite (organizer-uuids est déjà ACCEPTED-only + public), marche identiquement anon/auth. Inconvénient : N appels `getUserById` (N = nb co-orgs, typiquement 0-2).

**Option B2 — Backend : relâcher `GET /co-organizers` en `@PermitAll`**
Passer la méthode GET en `@PermitAll` ET filtrer **ACCEPTED-only** pour les appelants non-organisateurs (anon/autre user), liste complète (tous statuts) réservée à créateur/co-org/admin. Avantages : 1 seul appel, noms déjà enrichis dans le DTO. Inconvénients : logique de filtrage par rôle + **test sentinelle** anti-fuite PENDING/DECLINED + modif sécurité d'un endpoint existant.

**Option B3 — Backend : enrichir l'Event DTO** avec créateur + co-orgs ACCEPTED.
Rejeté : touche le DTO partagé + **openapi.yaml** (invariant 0-ligne figé, cf. internal-endpoints.md).

---

## 4. Tests
- **Frontend** `EventOrganizerTeam.test.tsx` : co-orgs résolus et affichés sans auth (mock organizer-uuids + getUserById) ; fallback créateur ne montre jamais `@<uuid-complet>`.
- **Frontend** `EventDetailPage` : le créateur passe `username=null` quand `organizer` est null.
- **(si B2)** sentinelle backend : appelant anon → PENDING/DECLINED absents de la réponse.
- Couverture ~100 % sur le code modifié (exigence Sonar).

## 5. Critères d'acceptation
| # | Scénario | Attendu |
|---|---|---|
| AC-1 | Anon ouvre un event public avec co-orgs ACCEPTED | Noms réels des co-orgs affichés (plus de 401 silencieux) |
| AC-2 | Anon, créateur a un displayName | Vrai nom (déjà OK) — jamais `@<uuid-complet>` |
| AC-3 | Anon, commentaires | Noms réels (déjà OK) |
| AC-4 | (si B2) Anon ne voit aucun co-org PENDING/DECLINED | Pas de fuite d'invitation |

## 6. Décision tranchée
**Volet B → Option B1 (frontend via `organizer-uuids`).** `EventOrganizerTeam` cesse de dépendre de l'endpoint `@Authenticated` `/co-organizers` ; il résout créateur + co-orgs ACCEPTED via `GET /events/{id}/organizer-uuids` (public) + `getUserById` par UUID (anon-OK). Chemin unique anon/auth. L'endpoint `/co-organizers` reste pour `CoOrganizersEditor` (édition). Aucun changement backend / openapi / sécurité.
