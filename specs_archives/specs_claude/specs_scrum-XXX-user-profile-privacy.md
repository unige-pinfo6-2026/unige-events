# Specs ISSUE-93 — Fermer l'oracle d'existence + réduire la fuite anonyme sur `GET /api/users/{id}` (Pentest 4.1 + 4.1b)

> **Branche :** `feature/ISSUE-93-user-profile-privacy`
> **Base :** `origin/main`
> **Sprint :** S6 — Hotfix sécurité post-pentest
> **Findings couverts :** 4.1 (user-existence oracle via `403` vs `404` sur `GET /users/{id}`, Medium) + 4.1b (public profiles exposent un payload complet anonymement, Medium, GDPR-relevant)
> **Rapport :** `unige-events-pentest-report.md` — audit du 2026-04-17 par Elie Bussod
> **Règle d'or :** Modifier `openapi/openapi.yaml` EN PREMIER, puis Resource → Service → DTO → Tests. Doc dans le **même commit** que le code.


---

## Contexte

### La faille — deux volets sur le même endpoint

**Volet 1 — Oracle d'existence (finding 4.1).** `UserService.getPublicProfile(UUID id)` distingue aujourd'hui trois cas :

| Cas | Comportement actuel |
|---|---|
| UUID inexistant | `404 not_found` |
| UUID existe, `profilePublic=false` | `403 forbidden "This profile is private"` |
| UUID existe, `profilePublic=true` | `200` + profil complet |

La différence entre `404` (inexistant) et `403` (privé) permet à un attaquant de confirmer qu'un UUID correspond à un utilisateur réel même quand il ne peut pas lire le profil. UUIDv4 n'est pas énumérable seul, **mais `creatorId` est leaké en clair dans `GET /api/events`** — chaque organisateur d'event est une cible probable. Le finding 4.1 documente l'exploitation trivialement reproduisible :

```bash
curl https://pinfo6.duckdns.org/api/users/00000000-0000-0000-0000-000000000000
# → 404  {"error":"not_found"}

curl https://pinfo6.duckdns.org/api/users/25a9a661-5225-4333-b70f-3e348dbecc66
# → 403  {"error":"forbidden","message":"This profile is private"}
# ⇒ ce second UUID "existe", le premier non. Différence = oracle.
```

**Volet 2 — Harvest anonyme des profils opt-in (finding 4.1b).** Pour un user avec `profilePublic=true`, `GET /users/{uuid}` retourne **le payload complet** à n'importe quel appelant anonyme : `id`, `displayName`, `faculty`, `studyLevel`, `bio`, `interests`, `avatarUrl`, `bannerUrl`. Combiné à l'énumération via `creatorId`, un attaquant peut harvester en quelques lignes de shell tous les profils opt-in de la plateforme. C'est un risque **GDPR-relevant pour une université** : `faculty + bio + studyLevel` = donnée identifiante.

```bash
curl https://pinfo6.duckdns.org/api/events | jq -r '.[].creatorId' | sort -u > /tmp/uuids
for u in $(cat /tmp/uuids); do
  curl -s https://pinfo6.duckdns.org/api/users/$u | jq -c '{id, displayName, faculty, studyLevel, bio}'
done
```

### La correction structurelle

**Volet 1 — 403 devient 404** : un profil privé demandé par un appelant qui n'est pas le propriétaire renvoie `404 not_found`, envelope **strictement identique** à celle d'un UUID inexistant. L'oracle se ferme : l'attaquant ne peut plus distinguer « existe mais caché » de « n'existe pas ».

**Volet 2 — Stripping pour les anonymes (Option A)** : pour un profil public consulté sans JWT, `UserPublicResponse` ne renvoie plus que `id`, `displayName`, `avatarUrl`. Les champs `faculty`, `studyLevel`, `bio`, `interests`, `bannerUrl` sont remplacés par `null`. Les appelants authentifiés continuent de recevoir le payload complet — comportement inchangé pour l'UX connectée.

**Règle fonctionnelle complète :**

| Appelant | Profil cible | Comportement |
|---|---|---|
| (anyone) | UUID inexistant | `404 not_found` |
| anon | `profilePublic=false` | `404 not_found` (identique à inexistant — anti-oracle) |
| auth autre user | `profilePublic=false` | `404 not_found` (idem) |
| auth **propriétaire** (`auth0Id` matche) | `profilePublic=false` | `200` + profil complet (self-case) |
| anon | `profilePublic=true` | `200` + profil **réduit** (`id`, `displayName`, `avatarUrl` seulement) |
| auth quel qu'il soit | `profilePublic=true` | `200` + profil **complet** (non-régression) |

### Pourquoi 404 et pas 403 pour le privé

Identique au raisonnement de [`specs_issue-92-hide-draft-events.md`](specs_archives/specs_claude/specs_issue-92-hide-draft-events.md) : `403 "forbidden"` révèle « existe mais vous n'avez pas le droit ». `404 not_found` avec envelope identique à « n'existe pas » ferme l'oracle. Le mapper standard produit `{"error":"not_found","message":"HTTP 404 Not Found"}` dans les deux cas — les tests vérifieront l'identité stricte du body.

### Pourquoi Option A (stripping) et pas Option B (fermer l'endpoint aux anonymes)

Option B = `@PermitAll` → `@Authenticated` → `401` pour anon. Rejetée par le PO car elle casse l'UX « lien partagé d'un profil public » (un visiteur sans compte qui clique sur un profil partagé atterrit sur un écran de login hostile). Option A préserve l'UX publique minimale (avatar + nom) tout en supprimant la donnée sensible.

### Oracle résiduel assumé

Même avec la correction, un attaquant peut distinguer « profil public existe » (200 avec `id`+`displayName`+`avatarUrl`) de « UUID inexistant » (404). **Hors scope** — le but est de fermer la distinction « existe mais caché » vs « n'existe pas », pas « existe et public » vs « n'existe pas ».

### Pourquoi pas d'admin bypass

L'issue ne mentionne pas le cas admin. Contrairement à `GET /events/{id}` (ISSUE-92) où l'admin avait un bypass justifié par la modération, `GET /users/{id}` n'a aucun cas produit documenté qui exigerait qu'un admin lise un profil privé (la modération de profil se ferait via un endpoint dédié `/admin/users/{id}` à construire séparément). **Y ajouter un privilège serait du scope creep non discuté avec le PO.** À ré-ouvrir en issue séparée si besoin modération.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Signature du service : 2 paramètres, pas d'overload

**Décision.** `UserService.getPublicProfile` passe de `getPublicProfile(UUID id)` à `getPublicProfile(UUID id, String auth0Id)`. Pas d'overload, pas de flag `isAdmin` (non demandé).

**Justification.** Aligné sur le pattern déjà adopté par `EventService.getById(Long id, String auth0Id, boolean isAdmin)` (ISSUE-92). Le paramètre `auth0Id` est **nullable** — `null` signifie « appelant anonyme ». Le coût est la mise à jour de **5 call-sites** (1 prod + 3 internal tests + 1 mock override) — triviaux.

Omission délibérée de `isAdmin` : l'issue ne le demande pas. Si un besoin modération apparaît, on l'ajoutera via une issue dédiée. Le pattern reste extensible (ajout d'un 3e param plus tard sans casser la sémantique actuelle).

### 2. Lieu de la vérification privacy (403 → 404) : dans le Service

**Décision.** La règle d'autorisation vit dans `UserService.getPublicProfile`. La Resource se limite à résoudre `auth0Id` depuis `SecurityIdentity` et à le transmettre.

**Justification.** La règle dépend d'un état lu en DB (`user.profilePublic`, `user.auth0Id`). Symétrique à la décision 2 de specs_issue-92 : la logique métier reste dans le Service ; la Resource lit `identity` et délègue. `backend/AGENTS.md` interdit la logique métier dans la Resource hors lecture d'identity.

### 3. Lieu du stripping anonyme : dans la Resource

**Décision.** Le choix entre `UserPublicResponse.from(user)` (full) et `UserPublicResponse.fromAnonymous(user)` (réduit) se fait **dans la Resource**, basé sur `identity.isAnonymous()`. Le Service retourne toujours l'entité `User` complète.

**Justification.** Le stripping est un **choix de représentation** (DTO), pas une règle métier. Coupler le Service à « qui est l'appelant » pour décider quels champs projeter violerait la séparation des couches. Précédent proche : les tests passent aujourd'hui `User` au Resource qui projette via `UserPublicResponse.from(user)` — on étend le Resource avec un ternaire factory, sans toucher à la signature du Service pour la partie projection.

### 4. Self-case : propriétaire voit son propre profil privé

**Décision.** Si `auth0Id != null` et `auth0Id.equals(user.auth0Id)`, le Service retourne le profil sans exiger `profilePublic=true`. Pas de 404 sur son propre profil.

**Justification.** Un utilisateur qui ouvre `/users/{monId}` plutôt que `/users/me` (liens partagés, previews) ne doit pas se retrouver 404 sur son propre profil. Symétrie directe avec le traitement du créateur dans `EventService.getById` (ISSUE-92 décision 5). Le frontend (`ProfilePage.tsx:80`) gère déjà le cas `isOwnProfile` en court-circuitant vers `currentUser` — cette décision est une ceinture + bretelles pour les cas où le cache côté client est vide.

### 5. Admin : pas de privilège spécial

**Décision.** Le rôle `ADMIN` ne contourne pas `profilePublic=false`. Un admin non propriétaire reçoit `404` sur un profil privé, comme n'importe quel autre user authentifié.

**Justification.** Issue silencieuse sur ce cas → hors scope. Ajouter `isAdmin` maintenant serait du scope creep inutile. La modération de profils n'est pas un besoin produit documenté. Ré-ouvrir en issue séparée si le besoin apparaît.

### 6. DTO : nouvelle factory `fromAnonymous(User)` plutôt qu'un paramètre booléen

**Décision.** Ajouter `public static UserPublicResponse fromAnonymous(User user)` en factory statique distincte. Ne **pas** modifier `from(User)` existante. Ne **pas** ajouter de paramètre `boolean anonymous` à `from`.

**Justification.** Factory séparée = intention explicite au call-site (`UserPublicResponse.fromAnonymous(user)` se lit : « je projette pour un anonyme »). Un ternaire `from(user, isAnonymous)` obligerait chaque lecteur à se rappeler quel booléen signifie quoi. Les champs laissés à `null` (`faculty`, `studyLevel`, `bio`, `interests`, `bannerUrl`) sont déjà `nullable: true` dans le schéma OpenAPI — **sauf `interests`** (array sans `nullable: true` — voir décision 8).

### 7. Envelope 404 : réutilisée telle quelle

**Décision.** `throw new NotFoundException()` sans message. Le mapper standard produit `{"error":"not_found","message":"HTTP 404 Not Found"}` (vérifié sur ISSUE-92 : quand `NotFoundException` est lancée sans message, JAX-RS positionne le message par défaut `"HTTP 404 Not Found"`, que le mapper conserve).

**Justification.** Cette envelope est **identique** pour UUID inexistant et profil privé non autorisé — c'est la garantie anti-oracle. Les tests vérifieront `body("message", equalTo("HTTP 404 Not Found"))` sur les deux cas pour prouver l'identité stricte. Pas de nouveau code d'erreur (`private_profile`, `not_visible`, etc.) — ils ré-ouvriraient l'oracle.

### 8. OpenAPI — schéma unique, champs nullables

**Décision.** Garder **un seul schéma** `UserPublicResponse`. Marquer `interests` comme `nullable: true` (les autres champs le sont déjà). Documenter dans la `description` de l'opération que les champs sensibles sont `null` pour les appelants anonymes.

**Justification.** Option (a) du prompt. Éviter la prolifération de schémas (`UserPublicResponseAnonymous` vs. `UserPublicResponseFull`) qui augmenterait la surface de doc et de SDK générés. Les champs sont déjà optionnels en pratique (un user public peut légitimement avoir `bio=null`), donc la sémantique schéma n'est pas dénaturée.

### 9. Sécurité OpenAPI — auth optionnelle

**Décision.** Passer `security: [{}, {BearerAuth: []}]` sur l'opération `getPublicProfile`. Aligné sur la modification apportée par ISSUE-92 sur `GET /events/{id}`.

**Justification.** L'endpoint reste `@PermitAll` côté code (anon est légitime pour un profil public réduit), mais le comportement dépend du token présent ou non. `security: []` seul (ce qui existe aujourd'hui) masque le fait qu'un token change la forme du payload. La liste `[{}, {BearerAuth: []}]` est la convention OpenAPI 3 pour « pas d'auth OU bearer » = auth optionnelle.

### 10. Pas de log, pas de metric sur le rejet 404

**Décision.** Aucune trace applicative lorsqu'un 404 est renvoyé pour un profil privé.

**Justification.** Aligné sur ISSUE-92 décision 9 et `specs_scrum-133.md` décision 9. Logger chaque refus génère du bruit (un scanner qui énumère les UUIDs créerait un flood) sans valeur actionnable. Si audit sécurité requis, ajouter un compteur Micrometer dans une issue ultérieure.

### 11. `getPublicProfile` non-`@Transactional` — ne pas corriger

**Décision.** La méthode actuelle n'est pas `@Transactional` (contrairement à `getOrCreateUser`, `updateMyProfile`, etc.). Ne **pas** ajouter `@Transactional` dans cette PR.

**Justification.** C'est une opération de lecture pure, Hibernate gère la lecture sans transaction explicite (même pattern que `User.findByIdOptional` dans d'autres services). Ajouter `@Transactional` serait un refactor hors scope. Si jamais un lazy-fetch sur `user.interests` pose problème en test/prod, à traiter en issue séparée.

### 12. Frontend non modifié — impact UX documenté mais pas corrigé

**Décision.** Aucune modification frontend dans cette PR. Documenter l'impact UX dans la section « Ce qui n'est PAS dans le scope ».

**Justification.** Le frontend consommateur ([`frontend/src/pages/profile/ProfilePage.tsx`](frontend/src/pages/profile/ProfilePage.tsx)) affiche `bio`, `faculty`, `studyLevel`, `interests` avec des guards `&&` ou équivalents (lignes 191, 201-205, 211-216). Un payload réduit se dégradera gracieusement : un visiteur anonyme verra uniquement avatar + nom, sans crash ni écran cassé. **L'UX est légèrement réduite** (pas de bio, pas de faculté affichée pour les anons) mais c'est l'objectif conscient de l'Option A. Si un « Connectez-vous pour voir plus » discret est souhaité, à ouvrir en issue frontend séparée.

Autre consommateur : [`frontend/src/pages/event/EventDetailPage.tsx:200`](frontend/src/pages/event/EventDetailPage.tsx) (bloc organisateur sur la page d'event). Même traitement graceful à vérifier mais pas à modifier.

---

## Analyse de l'existant

### Ce qui existe déjà (à réutiliser)

| Élément | Fichier / ligne | Rôle |
|---|---|---|
| `@PermitAll` sur `GET /users/{id}` | [`UserResource.java:63`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L63) | Endpoint déjà anon-accessible — à conserver |
| Injection `SecurityIdentity identity` | [`UserResource.java:49`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L49) | Déjà câblée — pas de nouveau `@Inject` |
| Pattern `identity.isAnonymous()` / `identity.getPrincipal().getName()` | utilisé dans `UserResource` ailleurs (lignes 104, 207, 221…) | Pattern exact à reprendre dans `getProfile` |
| `UserPublicResponse.from(User)` | [`UserPublicResponse.java:18-29`](backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java#L18-L29) | Factory complète — à ne PAS modifier |
| Ordre des champs du record | [`UserPublicResponse.java:8-17`](backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java#L8-L17) | `id, displayName, faculty, studyLevel, bio, interests, avatarUrl, bannerUrl` (8 champs, dans cet ordre exact) |
| `NotFoundExceptionMapper` | [`NotFoundExceptionMapper.java`](backend/src/main/java/ch/unige/events/exception/mapper/NotFoundExceptionMapper.java) | Produit l'envelope `{"error":"not_found","message":"..."}` |
| `UserServiceMock.getPublicProfile` | [`UserServiceMock.java:77-86`](backend/src/test/java/ch/unige/events/service/UserServiceMock.java#L77-L86) | Mock override — signature à aligner |
| Helper de test `persistUser(auth0Id, email, profilePublic)` | [`UserServiceCoverageTest.java:393-402`](backend/src/test/java/ch/unige/events/service/UserServiceCoverageTest.java#L393-L402) | Seeding DB avec flag `profilePublic` — à réutiliser |
| Helper de test `seedUser(auth0Id, email)` | [`UserServiceMock.java:46-55`](backend/src/test/java/ch/unige/events/service/UserServiceMock.java#L46-L55) | Seeding in-memory pour tests Resource |
| Pattern `@TestSecurity(user = "auth0|alice")` | multiple tests de `UserResourceTest` et `EventResourceTest` | À réutiliser pour simuler Bob vs. Alice |

### Ce qui est à modifier

| Fichier | Modification |
|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) (ligne ~859) | Path `/users/{id}` GET : `description` + `security: [{}, {BearerAuth: []}]`, **retirer** la réponse `403`, enrichir la description du `404`, marquer `UserPublicResponse.interests` comme `nullable: true` |
| [`backend/src/main/java/ch/unige/events/resource/UserResource.java`](backend/src/main/java/ch/unige/events/resource/UserResource.java) (lignes 61-67) | `getProfile` : extraire `auth0Id` + `isAnonymous`, passer au Service, choisir la factory selon `isAnonymous` |
| [`backend/src/main/java/ch/unige/events/service/UserService.java`](backend/src/main/java/ch/unige/events/service/UserService.java) (lignes 78-85) | `getPublicProfile` : nouvelle signature `(UUID id, String auth0Id)`, self-case, 404 au lieu de 403 |
| [`backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java`](backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java) | Ajouter factory `fromAnonymous(User)` — ne pas modifier `from(User)` |
| [`backend/src/test/java/ch/unige/events/service/UserServiceMock.java`](backend/src/test/java/ch/unige/events/service/UserServiceMock.java) (lignes 77-86) | Aligner override `getPublicProfile` sur nouvelle signature + appliquer la règle in-memory |
| [`backend/src/test/java/ch/unige/events/resource/UserResourceTest.java`](backend/src/test/java/ch/unige/events/resource/UserResourceTest.java) (lignes 43-74) | Renommer + refactorer les 3 tests existants (`getProfilePublicSuccess`, `getProfilePrivateForbidden`, `getProfileNotFound`) puis ajouter 4 nouveaux tests pour la matrice complète |
| [`backend/src/test/java/ch/unige/events/service/UserServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/UserServiceCoverageTest.java) (lignes 159-185) | Adapter les 3 tests existants à la nouvelle signature (ajouter `null` en 2e param) puis ajouter ~5 nouveaux tests pour couvrir self-case, 404 sur privé, non-owner, non-provisionned caller |
| [`backend/docs/api-contract.md`](backend/docs/api-contract.md) | Section `GET /users/{id}` : retirer mention `403`, documenter règle 404 + stripping anonyme |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Note sur `User.profilePublic` et ses deux niveaux (visibilité + stripping anonyme) |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Entrée Sprint 6 ISSUE-93 — hotfix pentest 4.1 + 4.1b |

### Ce qui n'est PAS dans le scope

- ❌ Pas de migration Flyway.
- ❌ Pas de changement `@PermitAll` → `@Authenticated` sur `GET /users/{id}` (Option B rejetée par le PO).
- ❌ Pas de 403 sur profil privé — 404 uniquement.
- ❌ Pas d'ajout de privilège admin (scope creep non discuté).
- ❌ Pas de nouveau schéma OpenAPI `UserPublicResponseAnonymous`.
- ❌ Pas de modification de `UserPublicResponse.from(User)` — ajout de `fromAnonymous` uniquement.
- ❌ Pas d'ajout de `@Transactional` sur `getPublicProfile` (voir décision 11).
- ❌ Pas d'application de la même règle à `GET /users/me` (reste `@Authenticated`, full profile, inchangé).
- ❌ Pas de modification frontend. Le consumer principal ([`ProfilePage.tsx`](frontend/src/pages/profile/ProfilePage.tsx)) affiche `bio`/`faculty`/`studyLevel`/`interests` avec des guards `&&` — dégradation graceful pour les anonymes. Si un « Connectez-vous pour voir plus » discret est souhaité, issue frontend séparée. Vérifier avec `git diff --stat frontend/` vide avant de commit.
- ❌ Pas de nouvelle exception custom ni de nouveau code d'erreur dans `ApiErrorResponse` — réutiliser `NotFoundException` + envelope standard.
- ❌ Pas de log applicatif sur le rejet 404.
- ❌ Pas d'élargissement à d'autres endpoints (`/me`, `/me/events`, `/me/attendances`, etc.). Cette PR ne corrige QUE `GET /users/{id}`.

---

## Étape 0 — `openapi/openapi.yaml` (OBLIGATOIRE EN PREMIER)

**Fichier :** [`/workspace/openapi/openapi.yaml`](openapi/openapi.yaml) — path `/users/{id}` GET (lignes ~859-891) et schéma `UserPublicResponse` (lignes ~94-124).

### 0.1 — Path `/users/{id}` GET

Remplacer (lignes 859-891) :

```yaml
  /users/{id}:
    get:
      summary: Profil public d'un utilisateur
      description: Retourne le profil public si profilePublic = true. Retourne 403 si privé.
      operationId: getPublicProfile
      tags: [users]
      security: []   # @PermitAll — pas de token requis
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: Profil public de l'utilisateur
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserPublicResponse'
        '403':
          description: Profil privé (profilePublic = false)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Utilisateur introuvable
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

par :

```yaml
  /users/{id}:
    get:
      summary: Profil public d'un utilisateur
      description: |
        Retourne le profil public d'un utilisateur.

        **Règle d'autorisation** (hotfix pentest 2026-04-17, findings 4.1 + 4.1b) :
        - Profil avec `profilePublic=true` : accessible en lecture. Un appelant **anonyme**
          reçoit un payload **réduit** (seuls `id`, `displayName`, `avatarUrl` sont renseignés ;
          les autres champs sont `null`). Un appelant **authentifié** reçoit le payload complet.
        - Profil avec `profilePublic=false` : visible uniquement par son propriétaire
          (`auth0Id` du JWT matche `user.auth0Id`). Tout autre appelant — anonyme, user
          différent — reçoit `404 not_found`, envelope identique à celle d'un UUID inexistant
          (ferme l'oracle d'existence exploité via `creatorId` leaké par `GET /events`).
      operationId: getPublicProfile
      tags: [users]
      security:
        - {}
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '200':
          description: |
            Profil public de l'utilisateur. Payload complet pour les appelants authentifiés ;
            payload réduit (`id`, `displayName`, `avatarUrl` uniquement, autres champs `null`)
            pour les appelants anonymes.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/UserPublicResponse'
        '404':
          description: |
            Utilisateur introuvable, OU profil privé demandé par un appelant qui n'en est pas
            le propriétaire. Le corps est identique dans les deux cas (pas d'oracle d'existence).
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

**Points à respecter :**
- **Retirer la réponse `403`** — elle disparaît avec le fix.
- `security: [{}, {BearerAuth: []}]` remplace `security: []` — convention OpenAPI 3 pour auth optionnelle (pattern ISSUE-92).
- Pas de nouveau paramètre, pas de nouveau schéma.

### 0.2 — Schéma `UserPublicResponse`

Dans le schéma (lignes ~94-124), marquer `interests` comme `nullable: true` (les autres champs le sont déjà) :

```yaml
        interests:
          type: array
          items:
            type: string
          nullable: true
```

Ajouter en fin de schéma une note décrivant le stripping :

```yaml
      description: |
        Profil public d'un utilisateur.

        **Stripping anonyme** : pour un appelant sans JWT, seuls `id`, `displayName` et
        `avatarUrl` sont renseignés ; `faculty`, `studyLevel`, `bio`, `interests`, `bannerUrl`
        sont systématiquement `null`. Les appelants authentifiés reçoivent tous les champs
        renseignés en base.
```

---

## Étape 1 — `UserResource.java`

**Fichier :** [`backend/src/main/java/ch/unige/events/resource/UserResource.java`](backend/src/main/java/ch/unige/events/resource/UserResource.java) — méthode `getProfile`, lignes 61-67.

### 1.1 — Nouvelle signature

Remplacer :

```java
    /**
     * GET /api/users/{id}
     * Public si profilePublic=true, sinon 403
     */
    @GET
    @Path("/{id}")
    @PermitAll
    public Response getProfile(@PathParam("id") UUID id) {
        User user = userService.getPublicProfile(id);
        return Response.ok(UserPublicResponse.from(user)).build();
    }
```

par :

```java
    /**
     * GET /api/users/{id}
     * Profil public d'un utilisateur.
     * - profilePublic=true + anon  → 200 payload réduit (id, displayName, avatarUrl)
     * - profilePublic=true + auth  → 200 payload complet
     * - profilePublic=false + self → 200 payload complet
     * - sinon                      → 404 (envelope identique à UUID inexistant, anti-oracle)
     * Hotfix pentest 2026-04-17 findings 4.1 + 4.1b.
     */
    @GET
    @Path("/{id}")
    @PermitAll
    public Response getProfile(@PathParam("id") UUID id) {
        boolean anonymous = identity.isAnonymous();
        String auth0Id = anonymous ? null : identity.getPrincipal().getName();
        User user = userService.getPublicProfile(id, auth0Id);
        UserPublicResponse body = anonymous
                ? UserPublicResponse.fromAnonymous(user)
                : UserPublicResponse.from(user);
        return Response.ok(body).build();
    }
```

**Points à respecter :**
- `@PermitAll` est **conservé** — l'anon est légitime pour un profil public réduit.
- `identity` est déjà injecté ([ligne 49](backend/src/main/java/ch/unige/events/resource/UserResource.java#L49)) — pas de nouveau `@Inject`.
- `anonymous` capturé en variable locale pour éviter deux appels à `identity.isAnonymous()` (micro-opti + cohérence : le ternaire d'en bas utilise la même valeur).
- Ne pas remplacer `@Inject SecurityIdentity` par autre chose (le champ est partagé avec les autres méthodes de la classe).

### 1.2 — Pas de nouvel import

`SecurityIdentity`, `PermitAll`, `UserPublicResponse` déjà importés.

---

## Étape 2 — `UserService.java`

**Fichier :** [`backend/src/main/java/ch/unige/events/service/UserService.java`](backend/src/main/java/ch/unige/events/service/UserService.java) — méthode `getPublicProfile`, lignes 78-85.

### 2.1 — Nouvelle signature et règle d'autorisation

Remplacer :

```java
    public User getPublicProfile(UUID id) {
        User user = (User) User.findByIdOptional(id).orElseThrow(NotFoundException::new);
        if (!user.profilePublic) {
            throw new ForbiddenException("This profile is private");
        }

        return user;
    }
```

par :

```java
    public User getPublicProfile(UUID id, String auth0Id) {
        User user = (User) User.findByIdOptional(id).orElseThrow(NotFoundException::new);

        // Hotfix pentest 4.1 : hide private profiles with 404 (not 403) to close the
        // existence oracle — identical envelope as "user does not exist".
        // Self-case: an authenticated user can always read their own profile.
        boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
        if (!user.profilePublic && !isOwner) {
            throw new NotFoundException();
        }

        return user;
    }
```

**Points à respecter :**
- **Ordre des checks** : lookup → self-case → privacy → 404 sinon.
- **`NotFoundException` sans message** — le mapper produit l'envelope identique à un UUID inexistant (`{"error":"not_found","message":"HTTP 404 Not Found"}`), ce qui ferme l'oracle.
- **Supprimer l'import `ForbiddenException`** s'il n'est plus utilisé ailleurs dans la classe. Vérifier avec un grep rapide : `grep "ForbiddenException" UserService.java` — si d'autres méthodes (`updateMyProfile`) l'utilisent, garder l'import.
- Ne pas ajouter `@Transactional` (cf. décision 11).
- Ne pas appeler `User.findByAuth0Id` — la comparaison par chaîne `auth0Id.equals(user.auth0Id)` suffit et évite une 2e requête DB (symétrique à la décision 6 de specs_issue-92).

### 2.2 — Pas de nouvel import

`NotFoundException` déjà importé. `User` déjà importé.

---

## Étape 3 — `UserPublicResponse.java`

**Fichier :** [`backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java`](backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java).

### 3.1 — Ajouter la factory `fromAnonymous`

Le record a 8 champs dans cet ordre : `id, displayName, faculty, studyLevel, bio, interests, avatarUrl, bannerUrl`. **Vérifier cet ordre avant de coder** (il peut avoir évolué entre la rédaction de la spec et l'implémentation).

**Ajouter** après la factory `from` existante :

```java
    /**
     * Factory pour les appelants anonymes.
     * Ne projette que id, displayName et avatarUrl — les autres champs sont null.
     * Hotfix pentest 2026-04-17 finding 4.1b (GDPR : limiter le harvest anonyme des
     * profils opt-in).
     */
    public static UserPublicResponse fromAnonymous(User user) {
        return new UserPublicResponse(
                user.id,
                user.displayName,
                null,   // faculty
                null,   // studyLevel
                null,   // bio
                null,   // interests
                user.avatarUrl,
                null    // bannerUrl
        );
    }
```

**Points à respecter :**
- Ne pas modifier `from(User)` existante.
- Respecter strictement l'ordre du constructeur du record (les commentaires en fin de ligne aident la relecture mais ne remplacent pas la vérification).
- Jackson sérialise `null` comme `null` en JSON — conforme au schéma OpenAPI `nullable: true` mis à jour.

### 3.2 — Pas de nouvel import

`User` et `UUID` déjà importés.

---

## Étape 4 — `UserServiceMock.java` (tests unitaires)

**Fichier :** [`backend/src/test/java/ch/unige/events/service/UserServiceMock.java`](backend/src/test/java/ch/unige/events/service/UserServiceMock.java) — méthode `getPublicProfile`, lignes 77-86.

### 4.1 — Aligner l'override sur la nouvelle signature

Remplacer :

```java
    @Override
    public User getPublicProfile(UUID id) {
        User user = usersById.get(id);
        if (user == null) {
            throw new NotFoundException();
        }
        if (!user.profilePublic) {
            throw new ForbiddenException("This profile is private");
        }
        return user;
    }
```

par :

```java
    @Override
    public User getPublicProfile(UUID id, String auth0Id) {
        User user = usersById.get(id);
        if (user == null) {
            throw new NotFoundException();
        }
        // Même règle qu'en prod — hotfix pentest 4.1.
        boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
        if (!user.profilePublic && !isOwner) {
            throw new NotFoundException();
        }
        return user;
    }
```

**Points à respecter :**
- La règle est **dupliquée** ici (pas un appel à `UserService.getPublicProfile`) — c'est le pattern d'indépendance des mocks du projet.
- Si `ForbiddenException` n'est plus utilisée dans la classe après ce changement, retirer son import.

---

## Étape 5 — Mise à jour des call-sites internes

**5 call-sites à mettre à jour**, dont 1 production (déjà traité à l'étape 1) et 4 dans les tests.

### 5.1 — `UserServiceCoverageTest.java` — call-sites existants (3)

| Ligne | Test actuel | Modification |
|---|---|---|
| 163 | `getPublicProfileThrowsNotFoundWhenMissing` : `userService.getPublicProfile(UUID.randomUUID())` | Ajouter `, null` en 2e param |
| 173 | `getPublicProfileThrowsForbiddenWhenPrivate` : `userService.getPublicProfile(user.id)` | **Renommer** en `getPublicProfileThrowsNotFoundWhenPrivateAndAnon` ET changer l'assertion de `ForbiddenException.class` à `NotFoundException.class` ET ajouter `, null` en 2e param |
| 182 | `getPublicProfileReturnsUserWhenPublic` : `userService.getPublicProfile(user.id)` | Ajouter `, null` en 2e param (le profil est public, anon OK) |

### 5.2 — `UserServiceMock.java` — déjà traité à l'étape 4

### 5.3 — `UserResource.java` — déjà traité à l'étape 1

---

## Étape 6 — Tests `UserResourceTest.java`

**Fichier :** [`backend/src/test/java/ch/unige/events/resource/UserResourceTest.java`](backend/src/test/java/ch/unige/events/resource/UserResourceTest.java) — lignes 43-74.

### 6.1 — Refactorer les 3 tests existants

Remplacer le bloc des 3 tests existants (`getProfilePublicSuccess`, `getProfilePrivateForbidden`, `getProfileNotFound` aux lignes 43-74) par :

```java
    // --- GET /users/{id} ---

    @Test
    void getProfile_publicProfile_anon_returns200_strippedPayload() {
        var user = userServiceMock.seedUser("auth0|public-profile", "public@example.com");
        user.profilePublic = true;
        user.faculty = "Sciences";
        user.studyLevel = "Master";
        user.bio = "Étudiant";
        user.interests = java.util.List.of("tech");
        user.bannerUrl = "https://cdn/banner.png";
        user.avatarUrl = "https://cdn/avatar.png";
        user.displayName = "Alice";

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("id", equalTo(user.id.toString()))
            .body("displayName", equalTo("Alice"))
            .body("avatarUrl", equalTo("https://cdn/avatar.png"))
            // Champs sensibles absents/null pour un anon (hotfix pentest 4.1b)
            .body("faculty", nullValue())
            .body("studyLevel", nullValue())
            .body("bio", nullValue())
            .body("interests", nullValue())
            .body("bannerUrl", nullValue());
    }

    @Test
    void getProfile_privateProfile_anon_returns404_sameEnvelopeAsUnknown() {
        // Anti-oracle : body strictement identique au cas UUID inexistant (hotfix pentest 4.1)
        var user = userServiceMock.seedUser("auth0|private-profile", "private@example.com");
        user.profilePublic = false;

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"))
            .body("message", equalTo("HTTP 404 Not Found"));
    }

    @Test
    void getProfile_unknownUuid_anon_returns404() {
        given()
            .when().get("/users/" + UUID.randomUUID())
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"))
            .body("message", equalTo("HTTP 404 Not Found"));
    }
```

### 6.2 — Ajouter les nouveaux tests

Juste après le bloc ci-dessus :

```java
    @Test
    @TestSecurity(user = "auth0|bob")
    void getProfile_privateProfile_otherUser_returns404() {
        var user = userServiceMock.seedUser("auth0|alice-private", "alice@example.com");
        user.profilePublic = false;

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(404)
            .body("error", equalTo("not_found"));
    }

    @Test
    @TestSecurity(user = "auth0|alice-self", attributes = {
        @SecurityAttribute(key = "email", value = "alice-self@example.com")
    })
    void getProfile_privateProfile_owner_returns200_fullPayload() {
        var user = userServiceMock.seedUser("auth0|alice-self", "alice-self@example.com");
        user.profilePublic = false;
        user.faculty = "Sciences";
        user.bio = "Secret bio";

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .body("id", equalTo(user.id.toString()))
            .body("faculty", equalTo("Sciences"))
            .body("bio", equalTo("Secret bio"));
    }

    @Test
    @TestSecurity(user = "auth0|bob", attributes = {
        @SecurityAttribute(key = "email", value = "bob@example.com")
    })
    void getProfile_publicProfile_authenticated_returns200_fullPayload() {
        // Non-régression : un user authentifié reçoit TOUJOURS le payload complet
        // sur un profil public, indépendamment de son identité.
        var user = userServiceMock.seedUser("auth0|alice-pub", "alice-pub@example.com");
        user.profilePublic = true;
        user.faculty = "Sciences";
        user.bio = "Public bio";

        given()
            .when().get("/users/" + user.id)
            .then()
            .statusCode(200)
            .body("id", equalTo(user.id.toString()))
            .body("faculty", equalTo("Sciences"))
            .body("bio", equalTo("Public bio"));
    }
```

### 6.3 — Matrice récapitulative

| Test | Setup | `@TestSecurity` | Attendu |
|---|---|---|---|
| `_publicProfile_anon_returns200_strippedPayload` | profilePublic=true + tous les champs remplis | (aucune — anon) | 200 + seuls `id`, `displayName`, `avatarUrl` non-null |
| `_privateProfile_anon_returns404_sameEnvelopeAsUnknown` | profilePublic=false | (aucune — anon) | 404 + `message: "HTTP 404 Not Found"` |
| `_unknownUuid_anon_returns404` | UUID random inexistant | (aucune — anon) | 404 + **même message** que le test précédent |
| `_privateProfile_otherUser_returns404` | profilePublic=false seedé par Alice | `auth0|bob` | 404 |
| `_privateProfile_owner_returns200_fullPayload` | profilePublic=false seedé par Alice | `auth0|alice-self` (matche l'auth0Id du user seedé) | 200 + champs sensibles visibles |
| `_publicProfile_authenticated_returns200_fullPayload` | profilePublic=true seedé par Alice | `auth0|bob` (non propriétaire) | 200 + payload complet (non-régression — auth ≠ anon) |

**Imports à vérifier :** `import org.hamcrest.Matchers.nullValue;` (pour l'assertion `nullValue()` sur les champs strippés).

---

## Étape 7 — Tests `UserServiceCoverageTest.java` (intégration DevServices)

**Fichier :** [`backend/src/test/java/ch/unige/events/service/UserServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/UserServiceCoverageTest.java) — section autour des lignes 157-185.

### 7.1 — Adapter les 3 tests existants (cf. étape 5.1)

### 7.2 — Ajouter les nouveaux tests d'intégration

À la suite du test `getPublicProfileReturnsUserWhenPublic` (vers la ligne 185), ajouter :

```java
    @Test
    @TestTransaction
    void getPublicProfileReturnsUserWhenPrivateAndOwner() {
        // Self-case : le propriétaire peut toujours lire son propre profil privé
        deleteAllUsers();
        User user = persistUser("auth0|self", "self@example.com", false);

        User result = userService.getPublicProfile(user.id, "auth0|self");

        assertEquals(user.id, result.id);
    }

    @Test
    @TestTransaction
    void getPublicProfileThrowsNotFoundWhenPrivateAndOtherUser() {
        deleteAllUsers();
        User alice = persistUser("auth0|alice-priv", "alice-priv@example.com", false);
        persistUser("auth0|bob-priv", "bob-priv@example.com", true);

        assertThrows(NotFoundException.class,
            () -> userService.getPublicProfile(alice.id, "auth0|bob-priv"));
    }

    @Test
    @TestTransaction
    void getPublicProfileThrowsNotFoundWhenPrivateAndCallerNotProvisioned() {
        // Edge case : l'appelant a un auth0Id mais son User n'est pas provisionné.
        // Il ne peut être propriétaire d'aucun profil, donc la règle privacy s'applique.
        deleteAllUsers();
        User alice = persistUser("auth0|alice-ghost", "alice-ghost@example.com", false);

        assertThrows(NotFoundException.class,
            () -> userService.getPublicProfile(alice.id, "auth0|ghost-not-in-db"));
    }

    @Test
    @TestTransaction
    void getPublicProfileReturnsUserWhenPublicAndAnon() {
        // Non-régression : profil public + anon → OK
        deleteAllUsers();
        User user = persistUser("auth0|public-anon", "public-anon@example.com", true);

        User result = userService.getPublicProfile(user.id, null);

        assertEquals(user.id, result.id);
    }

    @Test
    @TestTransaction
    void getPublicProfileReturnsUserWhenPublicAndAuth() {
        // Non-régression : profil public + authentifié autre → OK
        deleteAllUsers();
        User user = persistUser("auth0|alice-pub", "alice-pub@example.com", true);
        persistUser("auth0|bob-pub", "bob-pub@example.com", true);

        User result = userService.getPublicProfile(user.id, "auth0|bob-pub");

        assertEquals(user.id, result.id);
    }
```

### 7.3 — Cible de couverture

**Viser 100 % sur les lignes nouvelles de `UserService.getPublicProfile` :**

- `auth0Id != null && auth0Id.equals(user.auth0Id)` — vrai (tests `owner_returns200`, `self`), faux-par-null (tests `anon`), faux-par-mismatch (tests `otherUser`, `ghost`).
- `!user.profilePublic && !isOwner` — vrai (tests `private_anon/otherUser/ghost`), faux-par-public (test `public_*`), faux-par-owner (test `owner_returns200`).

---

## Étape 8 — Documentation

### 8.1 — [`backend/docs/api-contract.md`](backend/docs/api-contract.md)

Remplacer la section détaillée `GET /users/{id}` par :

```markdown
### `GET /users/{id}`

Retourne le profil public d'un utilisateur.

**Règle d'autorisation** (hotfix pentest 2026-04-17, findings 4.1 + 4.1b) :
- `profilePublic=true` : accessible en lecture. **Anon** → payload **réduit** (`id`,
  `displayName`, `avatarUrl` ; autres champs `null`). **Authentifié** → payload **complet**.
- `profilePublic=false` : visible uniquement par son propriétaire (`auth0Id` du JWT
  matche `user.auth0Id`). Sinon → `404 not_found`, envelope identique à celle d'un UUID
  inexistant (ferme l'oracle d'existence exploité via `creatorId` leaké par `GET /events`).

**Paramètre :** `id` — UUID de l'utilisateur.

**Réponses :**
- `200 OK` — `UserPublicResponse` (payload complet ou réduit selon l'authentification)
- `404 Not Found` — utilisateur introuvable, OU profil privé demandé par un appelant non autorisé
```

Dans la table « Endpoints implémentés » (ligne 14), mettre à jour la ligne `GET /users/{id}` :

```markdown
| `GET` | `/users/{id}` | `@PermitAll` | Profil public d'un utilisateur — **payload réduit pour anon**, 404 si privé ou non autorisé (pas d'oracle d'existence) | 200, 404 |
```

### 8.2 — [`backend/docs/data-model.md`](backend/docs/data-model.md)

Sous l'entité `User` (après la table des champs), ajouter une sous-section :

```markdown
#### Règle de visibilité du profil (hotfix pentest 2026-04-17)

Le champ `profilePublic` contrôle deux dimensions simultanément sur `GET /api/users/{id}` :

| `profilePublic` | Appelant | Réponse |
|---|---|---|
| `true` | anon | `200` — payload **réduit** (`id`, `displayName`, `avatarUrl` ; autres `null`) |
| `true` | authentifié | `200` — payload **complet** |
| `false` | anon ou autre user | `404 not_found` (envelope identique à un UUID inexistant) |
| `false` | propriétaire (`auth0Id` matche) | `200` — payload complet (self-case) |

La règle d'autorisation vit dans `UserService.getPublicProfile(UUID, String auth0Id)` ;
le stripping anonyme est appliqué dans `UserResource` via `UserPublicResponse.fromAnonymous`.
Voir `specs_archives/specs_claude/specs_ISSUE-93-user-profile-privacy.md`.
```

### 8.3 — [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md)

En haut du fichier (avant la première section de sprint), ajouter :

```markdown
## Sprint 6 — Hotfix sécurité post-pentest (ISSUE-93) — 2026-04-24

Correction des findings **4.1** (user-existence oracle via 403 vs 404) et **4.1b**
(harvest anonyme des profils opt-in) du rapport de pentest du 2026-04-17.

Fix :
- `UserService.getPublicProfile(UUID id, String auth0Id)` — signature étendue. Si
  `profilePublic=false` et que l'appelant n'est pas le propriétaire (self-case sur
  `auth0Id`), throw `NotFoundException` (→ `404 not_found`, envelope identique à un
  UUID inexistant). Ferme l'oracle exploité via `creatorId` leaké par `GET /events`.
- `UserResource.getProfile` reste `@PermitAll` mais lit `identity.isAnonymous()` pour
  choisir entre `UserPublicResponse.from(user)` (full, authentifié) et
  `UserPublicResponse.fromAnonymous(user)` (réduit : `id` + `displayName` + `avatarUrl`).
- Nouvelle factory `UserPublicResponse.fromAnonymous(User)` — ne projette que 3 champs
  sur 8. Les 5 autres sont `null` et conformes au schéma (tous `nullable: true`).
- 5 call-sites internes migrés (1 prod + 3 coverage tests + 1 mock override).

**Pas de changement DB.** Pas d'impact frontend — le consumer `ProfilePage.tsx`
affiche `bio`/`faculty`/`studyLevel`/`interests` avec des guards `&&` et dégrade
gracieusement. L'UX anonyme devient simplement « avatar + nom » sans crash.
```

---

## Edge cases à traiter explicitement

| Cas | Appelant | Profil | Comportement attendu | Implémenté par |
|---|---|---|---|---|
| Anon + profil public | `identity.isAnonymous()` | `profilePublic=true` | `200` + payload réduit | `fromAnonymous` dans Resource |
| Anon + profil privé | `identity.isAnonymous()` | `profilePublic=false` | `404 not_found` | Service : `auth0Id=null` → `isOwner=false` → 404 |
| Auth + son propre profil privé | `auth0|alice` | Alice.profilePublic=false | `200` + payload complet | Service : `isOwner=true` → pas de 404 |
| Auth + son propre profil public | `auth0|alice` | Alice.profilePublic=true | `200` + payload complet | Resource : `!anonymous` → `from` (full) |
| Auth + autre user privé | `auth0|bob` | Alice.profilePublic=false | `404 not_found` | Service : `isOwner=false`, pas public → 404 |
| Auth + autre user public | `auth0|bob` | Alice.profilePublic=true | `200` + payload complet | Resource : `!anonymous` → `from` (full) |
| UUID inexistant (anon ou auth) | n'importe qui | — | `404 not_found` | `findByIdOptional` vide → throw (non-régression) |
| Auth avec `auth0Id` non provisionné en DB | `auth0|ghost` | privé (d'Alice) | `404 not_found` | `isOwner=false` (aucun user.auth0Id ne matche « ghost »), pas public → 404 |
| User cible avec `auth0Id == null` (défense en profondeur) | n'importe qui | — | Le propriétaire potentiel ne peut jamais matcher (le test `auth0Id.equals(null)` est géré par la garde `auth0Id != null` côté caller) | Service : `isOwner = auth0Id != null && auth0Id.equals(user.auth0Id)` |
| UUID malformé dans le path | n'importe qui | — | `400` par JAX-RS parsing (non-régression) | Framework |

---

## Critères d'acceptation (repris de l'issue GitHub)

- [ ] `GET /users/{uuid_inexistant}` → `404`
- [ ] `GET /users/{uuid_prive}` anon → `404` (**body strictement identique** au cas précédent — pas d'oracle)
- [ ] `GET /users/{uuid_prive}` avec token d'un autre user → `404`
- [ ] `GET /users/{uuid_prive}` avec token du propriétaire → `200` (profil complet — self-case confirmé et implémenté)
- [ ] `GET /users/{uuid_public}` anon → `200` avec **uniquement** `id`, `displayName`, `avatarUrl` ; `faculty`, `studyLevel`, `bio`, `interests`, `bannerUrl` = `null`
- [ ] `GET /users/{uuid_public}` authentifié → `200` avec le profil public complet (non-régression)
- [ ] Response schema OpenAPI cohérent avec la réalité (schéma `UserPublicResponse` avec `interests.nullable = true`, réponse `403` retirée de l'opération)
- [ ] Coverage ≥ 80 % sur le nouveau code

---

## Conventions du projet à respecter

- **Règle d'or :** `openapi.yaml` modifié en premier (cf. [`backend/AGENTS.md`](backend/AGENTS.md)).
- **camelCase** partout, pas de snake_case.
- **Pas de préfixe `is`** sur les booléens d'entités ; `isAnonymous` / `isOwner` sont des variables locales OK.
- **Pas de migration Flyway** — aucun changement de schéma.
- **Pas de logique métier dans la Resource** au-delà de la lecture de `identity` et du choix de factory (projection DTO = pas logique métier).
- **SonarCloud** : ≥ 80 % couverture sur le nouveau code, ≤ 3 % duplication, ratings A.
- **Doc mise à jour dans le même commit** que le code correspondant.
- **Commits atomiques** : `feat(ISSUE-93): …`, `test(ISSUE-93): …`, `docs(ISSUE-93): …`.

---

## Interdits stricts

- ❌ Pas de migration Flyway ni changement de schéma DB.
- ❌ Pas de `@PermitAll` → `@Authenticated` (Option B rejetée par le PO — casse l'UX lien partagé).
- ❌ Pas de 403 sur profil privé — **404 uniquement** (l'issue l'exige).
- ❌ Pas de message custom dans `NotFoundException` pour profil privé (casserait l'invariant anti-oracle).
- ❌ Pas de modification frontend. Le consumer `ProfilePage` dégrade gracieusement.
- ❌ Pas d'ajout de champ à `UserPublicResponse` (factory uniquement).
- ❌ Pas de modification de `UserPublicResponse.from(User)` — seulement ajout de `fromAnonymous`.
- ❌ Pas de nouveau schéma OpenAPI (`UserPublicResponseAnonymous`) — un seul schéma avec champs nullable.
- ❌ Pas de nouveau code d'erreur dans `ApiErrorResponse` — réutiliser `not_found`.
- ❌ Pas d'ajout de privilège admin sur cet endpoint (scope creep non demandé).
- ❌ Pas de `@Transactional` ajouté à `getPublicProfile` (voir décision 11).
- ❌ Pas d'application de la même règle à `GET /users/me` (reste `@Authenticated`, full profile, inchangé).
- ❌ Pas de refactor opportuniste d'`UserService` au-delà de `getPublicProfile` + 5 call-sites.
- ❌ Pas de log applicatif ou metric sur le rejet 404.
- ❌ Pas d'élargissement à d'autres endpoints users (`/me/*`).

---

## Résumé des fichiers touchés

| Fichier | Action |
|---|---|
| `/workspace/openapi/openapi.yaml` | Modifier — description + `security` + suppression réponse `403` + `interests.nullable` sur `UserPublicResponse` |
| `backend/src/main/java/ch/unige/events/resource/UserResource.java` | Modifier — `getProfile` extrait `isAnonymous` + `auth0Id`, choisit factory |
| `backend/src/main/java/ch/unige/events/service/UserService.java` | Modifier — nouvelle signature `(UUID, String)` + self-case + 404 au lieu de 403 |
| `backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java` | Modifier — ajout factory `fromAnonymous` (ne pas modifier `from`) |
| `backend/src/test/java/ch/unige/events/service/UserServiceMock.java` | Modifier — aligner override + dupliquer règle in-memory |
| `backend/src/test/java/ch/unige/events/resource/UserResourceTest.java` | Modifier — remplacer les 3 tests existants par 6 tests couvrant la matrice complète |
| `backend/src/test/java/ch/unige/events/service/UserServiceCoverageTest.java` | Modifier — adapter 3 tests existants (signature + 404 au lieu de 403) + 5 nouveaux tests intégration |
| `backend/docs/api-contract.md` | Modifier — ligne table + section détail pour `GET /users/{id}` |
| `backend/docs/data-model.md` | Modifier — note règle de visibilité du profil sous l'entité User |
| `backend/docs/sprint-context.md` | Modifier — entrée Sprint 6 ISSUE-93 |

**Total :** 10 fichiers modifiés, 0 créé.

---

## Branche et PR

- **Branche :** `feature/ISSUE-93-user-profile-privacy`, basée sur `origin/main`.
  ```bash
  git fetch origin
  git checkout -b feature/ISSUE-93-user-profile-privacy origin/main --no-track
  ```
  ⚠️ **`--no-track` est OBLIGATOIRE** : sans ce flag, la branche traque `origin/main` et `git push` envoie les commits directement sur main (incident documenté sur ISSUE-92, cf. commit de revert `9c2e6d4` sur main).

- **PR :** ciblant `main`, titre : `ISSUE-93 — Fix user profile privacy: close existence oracle + strip anonymous payload (pentest 4.1 + 4.1b)`.
- **Description PR** (modèle) :

  ```markdown
  Closes #<num>

  Hotfix des findings **4.1** (Medium) + **4.1b** (Medium, GDPR-relevant) du pentest
  interne du 2026-04-17.

  ## Avant
  ```bash
  # Oracle d'existence
  curl /api/users/{inexistant}       → 404 not_found
  curl /api/users/{uuid_prive}       → 403 forbidden "This profile is private"
  # ↑ la différence confirme l'existence d'un user

  # Harvest anonyme
  curl /api/users/{uuid_public}      → 200 {id, displayName, faculty, studyLevel, bio, interests, avatarUrl, bannerUrl}
  ```

  ## Après
  ```bash
  curl /api/users/{inexistant}       → 404 not_found
  curl /api/users/{uuid_prive}       → 404 not_found (envelope identique — pas d'oracle)
  curl /api/users/{uuid_public}      → 200 {id, displayName, avatarUrl, faculty:null, studyLevel:null, bio:null, interests:null, bannerUrl:null}
  curl -H "Bearer $TOKEN" /api/users/{uuid_public}  → 200 payload complet (inchangé)
  ```

  ## Matrice de décision
  | Appelant | Profil | Réponse |
  |---|---|---|
  | anon | inexistant | 404 |
  | anon | privé | 404 (= inexistant) |
  | auth autre user | privé | 404 |
  | auth propriétaire | privé | 200 full |
  | anon | public | 200 réduit |
  | auth | public | 200 full |

  ## Fichiers touchés
  - openapi/openapi.yaml (security optionnelle, description, 403 retiré, interests nullable)
  - backend/src/main/java/.../UserResource.java
  - backend/src/main/java/.../UserService.java
  - backend/src/main/java/.../UserPublicResponse.java (ajout fromAnonymous)
  - Tests : UserResourceTest, UserServiceCoverageTest, UserServiceMock
  - Doc : api-contract.md, data-model.md, sprint-context.md

  ## Référence
  Rapport pentest : unige-events-pentest-report.md — findings 4.1 + 4.1b.
  ```

- **Commits atomiques suggérés :**
  - `feat(ISSUE-93): close existence oracle on GET /users/{id} (404 instead of 403)`
  - `feat(ISSUE-93): strip anonymous payload on public profiles to id+displayName+avatarUrl`
  - `test(ISSUE-93): cover anon/other-user/owner × public/private matrix on GET /users/{id}`
  - `docs(ISSUE-93): document authorization rule and stripped anonymous payload`

  Combiner les deux `feat` en un seul commit si le diff est petit et cohérent — à juger au moment de l'implémentation.

---

## Checklist Sonar / qualité

- [ ] Coverage ≥ 80 % sur les lignes nouvelles (JaCoCo).
- [ ] Duplication < 3 % sur le code nouveau.
- [ ] Security Rating : A.
- [ ] Reliability Rating : A.
- [ ] Maintainability Rating : A.
- [ ] Security Review Rating : A.

---

## Checklist finale

### Avant push

- [ ] `./mvnw verify` vert localement.
- [ ] Rapport JaCoCo `backend/target/jacoco-report/` — lignes nouvelles ≥ 80 %.
- [ ] Les **3 tests de sécurité critiques** verts nommément (run ciblé) :
  - `getProfile_privateProfile_anon_returns404_sameEnvelopeAsUnknown` (anti-oracle + assertion `message: "HTTP 404 Not Found"`)
  - `getProfile_privateProfile_otherUser_returns404`
  - `getProfile_publicProfile_anon_returns200_strippedPayload` (avec assertion `faculty/studyLevel/bio/interests/bannerUrl = nullValue()`)
- [ ] Test de non-régression vert : `getProfile_publicProfile_authenticated_returns200_fullPayload`.
- [ ] Test de self-case vert : `getProfile_privateProfile_owner_returns200_fullPayload`.
- [ ] `openapi.yaml` modifié EN PREMIER et cohérent avec le code. `403` supprimé de l'opération.
- [ ] `git diff --stat frontend/` vide (pas de changement frontend).
- [ ] Aucun log applicatif ajouté (grep `logger` sur le diff).

### Avant PR

- [ ] Branche `feature/ISSUE-93-user-profile-privacy` créée avec `--no-track` depuis `origin/main`.
- [ ] `git branch -vv` confirme que la branche track `origin/feature/ISSUE-93-user-profile-privacy` après le premier push (pas `origin/main`).
- [ ] Commits atomiques nommés selon la convention.
- [ ] Description de PR reprenant motivation + avant/après + matrice + `Closes #<num>`.
- [ ] Base du PR : `main`.

### Avant merge

- [ ] CI verte.
- [ ] Review approuvée.
- [ ] SonarCloud quality gate vert.

---

## Prompt de lancement d'implémentation

````
Tu vas implémenter ISSUE-93 (hotfix pentest 4.1 + 4.1b) sur `GET /api/users/{id}` : fermer l'oracle d'existence (403 → 404 identique au cas UUID inexistant) et réduire le payload envoyé aux appelants anonymes (Option A : seuls `id`, `displayName`, `avatarUrl` exposés).

## ÉTAPE 0 — Création de la branche (avec --no-track OBLIGATOIRE)

Avant TOUT code :

    git fetch origin
    git checkout -b feature/ISSUE-93-user-profile-privacy origin/main --no-track

Le flag `--no-track` est CRITIQUE. Sans lui, la branche traque `origin/main` et `git push` envoie les commits sur main (incident documenté sur ISSUE-92, cf. commit de revert 9c2e6d4 sur main). Le `-u` viendra au premier push pour set-up le bon upstream.

Remplacer `ISSUE-93` par le numéro réel de l'issue (probablement `ISSUE-93` d'après les échanges). Si inconnu, demander avant de commencer.

## Source unique de vérité

`specs_archives/specs_claude/specs_ISSUE-93-user-profile-privacy.md` — à lire INTÉGRALEMENT avant d'écrire une ligne de code. Toutes les décisions (signature service à 2 params, self-case, 404 vs 403, stripping dans Resource pas Service, factory `fromAnonymous` séparée, schéma OpenAPI unique avec `interests` nullable, pas d'admin bypass, pas de log) y sont tranchées. Tu n'as RIEN à inventer.

## À lire avant de commencer

1. `unige-events-pentest-report.md` à la racine — findings 4.1 (oracle) et 4.1b (harvest anonyme). Comprendre pourquoi le fix doit préserver l'UX « lien partagé profil public » tout en fermant l'oracle.
2. `backend/AGENTS.md` — conventions (camelCase, openapi-first, pas de Flyway, logique métier dans le Service, seuil Sonar 80 %).
3. `backend/docs/` en entier (README, architecture, data-model, api-contract, sprint-context).
4. `specs_archives/specs_claude/specs_issue-92-hide-draft-events.md` — spec jumelle (même pattern auth optionnelle + envelope 404 anti-oracle) ; s'en inspirer pour les détails d'implémentation et de test pattern.
5. Code backend sur `main` :
   - `backend/src/main/java/ch/unige/events/resource/UserResource.java` — méthode `getProfile` ligne 61, `SecurityIdentity identity` injecté ligne 49, patterns `identity.getPrincipal().getName()` et `identity.isAnonymous()` utilisés ailleurs dans la classe.
   - `backend/src/main/java/ch/unige/events/service/UserService.java` — méthode `getPublicProfile` ligne 78.
   - `backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java` — record à 8 champs : `id, displayName, faculty, studyLevel, bio, interests, avatarUrl, bannerUrl` (vérifier l'ordre avant de coder `fromAnonymous`).
   - `backend/src/main/java/ch/unige/events/exception/mapper/NotFoundExceptionMapper.java` — envelope `{"error":"not_found","message":"HTTP 404 Not Found"}` quand `NotFoundException` est throw sans message (JAX-RS positionne le message par défaut).
   - `backend/src/test/java/ch/unige/events/service/UserServiceMock.java` — override `getPublicProfile` ligne 77.
   - `backend/src/test/java/ch/unige/events/resource/UserResourceTest.java` — tests existants lignes 43-74, patterns `@TestSecurity`.
   - `backend/src/test/java/ch/unige/events/service/UserServiceCoverageTest.java` — tests existants lignes 159-185, helper `persistUser(auth0Id, email, profilePublic)` ligne 393.

## Ordre d'implémentation strict

1. **`openapi/openapi.yaml`** EN PREMIER (règle d'or `backend/AGENTS.md`). Path `/users/{id}` GET (~ligne 859) :
   - Mettre à jour la `description` pour documenter la règle (profil privé → 404 anti-oracle + stripping pour anon).
   - Passer `security: []` à `security: [{}, {BearerAuth: []}]`.
   - **Retirer la réponse `403`** (elle disparaît avec le fix).
   - Enrichir la description du `404` (« introuvable OU profil privé non autorisé »).
   Schéma `UserPublicResponse` (~ligne 94) :
   - Marquer `interests` comme `nullable: true`.
   - Ajouter une description expliquant le stripping anonyme.

2. **`backend/src/main/java/ch/unige/events/resource/UserResource.java`** — dans `getProfile(id)` (lignes 61-67), résoudre :
   ```java
   boolean anonymous = identity.isAnonymous();
   String auth0Id = anonymous ? null : identity.getPrincipal().getName();
   User user = userService.getPublicProfile(id, auth0Id);
   UserPublicResponse body = anonymous
           ? UserPublicResponse.fromAnonymous(user)
           : UserPublicResponse.from(user);
   return Response.ok(body).build();
   ```
   Conserver `@PermitAll`. Pas de nouvel import (`SecurityIdentity` déjà injecté à la ligne 49).

3. **`backend/src/main/java/ch/unige/events/service/UserService.java`** — nouvelle signature `getPublicProfile(UUID id, String auth0Id)`. Corps :
   ```java
   User user = (User) User.findByIdOptional(id).orElseThrow(NotFoundException::new);
   boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
   if (!user.profilePublic && !isOwner) {
       throw new NotFoundException();
   }
   return user;
   ```
   Retirer l'import `ForbiddenException` si plus utilisé dans la classe (vérifier avec grep — `updateMyProfile` l'utilise, donc probablement garder).
   Ne **pas** ajouter `@Transactional`. Ne **pas** passer de message custom à `NotFoundException`.

4. **`backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java`** — ajouter factory :
   ```java
   public static UserPublicResponse fromAnonymous(User user) {
       return new UserPublicResponse(
               user.id, user.displayName,
               null, null, null, null,   // faculty, studyLevel, bio, interests
               user.avatarUrl,
               null                       // bannerUrl
       );
   }
   ```
   Respecter l'ordre EXACT des champs du record (vérifier avant de coder). Ne **pas** modifier `from(User)`.

5. **`backend/src/test/java/ch/unige/events/service/UserServiceMock.java`** — aligner override `getPublicProfile(UUID id, String auth0Id)` avec la même règle in-memory (comparaison `auth0Id.equals(user.auth0Id)` en ligne).

6. **Mise à jour des 3 call-sites internes dans `UserServiceCoverageTest.java`** :
   - Ligne 163 : `userService.getPublicProfile(UUID.randomUUID(), null)` (unknown — anon)
   - Ligne 173 : **renommer** `getPublicProfileThrowsForbiddenWhenPrivate` en `getPublicProfileThrowsNotFoundWhenPrivateAndAnon`, changer l'assertion de `ForbiddenException.class` à `NotFoundException.class`, `userService.getPublicProfile(user.id, null)`.
   - Ligne 182 : `userService.getPublicProfile(user.id, null)` (public + anon).

7. **`backend/src/test/java/ch/unige/events/resource/UserResourceTest.java`** — remplacer les 3 tests existants (`getProfilePublicSuccess`, `getProfilePrivateForbidden`, `getProfileNotFound`) par 6 nouveaux tests couvrant la matrice complète :
   - `getProfile_publicProfile_anon_returns200_strippedPayload` (avec assertion `nullValue()` sur faculty/studyLevel/bio/interests/bannerUrl)
   - `getProfile_privateProfile_anon_returns404_sameEnvelopeAsUnknown` (avec `body("message", equalTo("HTTP 404 Not Found"))`)
   - `getProfile_unknownUuid_anon_returns404` (avec **même message** pour prouver l'anti-oracle)
   - `getProfile_privateProfile_otherUser_returns404` (`@TestSecurity(user = "auth0|bob")`)
   - `getProfile_privateProfile_owner_returns200_fullPayload` (`@TestSecurity(user = "auth0|alice-self")` matching l'auth0Id du seed)
   - `getProfile_publicProfile_authenticated_returns200_fullPayload` (non-régression)
   Imports : ajouter `org.hamcrest.Matchers.nullValue`.

8. **`backend/src/test/java/ch/unige/events/service/UserServiceCoverageTest.java`** — après les 3 tests adaptés à l'étape 6, ajouter 5 tests intégration :
   - `getPublicProfileReturnsUserWhenPrivateAndOwner` (self-case)
   - `getPublicProfileThrowsNotFoundWhenPrivateAndOtherUser`
   - `getPublicProfileThrowsNotFoundWhenPrivateAndCallerNotProvisioned` (edge case ghost)
   - `getPublicProfileReturnsUserWhenPublicAndAnon` (non-régression)
   - `getPublicProfileReturnsUserWhenPublicAndAuth` (non-régression)
   Utiliser le helper `persistUser(auth0Id, email, profilePublic)` ligne 393.

9. **`./mvnw verify`** — DOIT être vert avec couverture ≥ 80 % sur le nouveau code. Corriger avant de passer à la doc.

10. **Documentation (même commit que le code correspondant)** :
    - `backend/docs/api-contract.md` — ligne table + section détail `GET /users/{id}` (règle autorisation + stripping).
    - `backend/docs/data-model.md` — note règle de visibilité sous l'entité User.
    - `backend/docs/sprint-context.md` — entrée Sprint 6 ISSUE-93.

## Interdits stricts

- PAS de migration Flyway ni changement DB.
- PAS de `@PermitAll` → `@Authenticated` (Option B rejetée).
- PAS de 403 sur profil privé — 404 uniquement, envelope identique au 404 standard.
- PAS de message custom dans `NotFoundException` (casse l'invariant anti-oracle).
- PAS de modification frontend (consumer dégrade gracieusement).
- PAS d'ajout de champ à `UserPublicResponse`, pas de nouveau schéma OpenAPI, pas de nouveau code d'erreur dans `ApiErrorResponse`.
- PAS de modification de `UserPublicResponse.from(User)` — seulement ajout de `fromAnonymous`.
- PAS d'ajout de privilège admin (scope creep non demandé).
- PAS d'application à `/me` — reste `@Authenticated`, full profile, inchangé.
- PAS d'ajout de `@Transactional` à `getPublicProfile`.
- PAS de refactor opportuniste d'`UserService` hors de `getPublicProfile` + call-sites.
- PAS de log applicatif ni de metric sur le rejet.
- PAS d'élargissement à d'autres endpoints users.
- PAS de snake_case, pas de `any`, pas de TODO commenté.

## Conventions à respecter

- camelCase partout.
- `openapi.yaml` modifié en PREMIER.
- Couverture JaCoCo ≥ 80 % sur les lignes nouvelles ; duplication < 3 % ; Sonar ratings A.
- Doc mise à jour dans le même commit que le code.
- Commits atomiques nommés `feat(ISSUE-93): …`, `test(ISSUE-93): …`, `docs(ISSUE-93): …` (possiblement 2 `feat` si on sépare les volets 4.1 et 4.1b — à juger).

## Critères de done

- [ ] `./mvnw verify` vert localement et en CI.
- [ ] JaCoCo ≥ 80 % sur les lignes nouvelles.
- [ ] Les **3 tests de sécurité critiques** verts nommément :
  - `getProfile_privateProfile_anon_returns404_sameEnvelopeAsUnknown` (avec `body("message", equalTo("HTTP 404 Not Found"))` — prouve l'absence d'oracle)
  - `getProfile_privateProfile_otherUser_returns404`
  - `getProfile_publicProfile_anon_returns200_strippedPayload` (avec `.body("faculty", nullValue()).body("bio", nullValue()).body("studyLevel", nullValue()).body("interests", nullValue()).body("bannerUrl", nullValue())`)
- [ ] Test de non-régression vert : `getProfile_publicProfile_authenticated_returns200_fullPayload`.
- [ ] Test self-case vert : `getProfile_privateProfile_owner_returns200_fullPayload`.
- [ ] Au moins un test 404 asserte l'envelope identique au cas UUID-inexistant (`getProfile_unknownUuid_anon_returns404` avec le même `message`).
- [ ] `git diff --stat frontend/` vide.
- [ ] SonarCloud Quality Gate vert sur la PR.
- [ ] `openapi.yaml` modifié EN PREMIER et cohérent avec le code (réponse 403 retirée, security optionnelle, `interests.nullable`).
- [ ] `backend/docs/api-contract.md`, `backend/docs/data-model.md`, `backend/docs/sprint-context.md` mis à jour dans le même PR.
- [ ] PR ouverte avec base `main`, titre `ISSUE-93 — Fix user profile privacy: close existence oracle + strip anonymous payload (pentest 4.1 + 4.1b)`, description contenant `Closes #<num>` pour linker automatiquement l'issue GitHub, matrice avant/après et exemples curl.
- [ ] Commits atomiques bien nommés (`feat(ISSUE-93)`, `test(ISSUE-93)`, `docs(ISSUE-93)`).
- [ ] `git branch -vv` confirme que la branche track `origin/feature/ISSUE-93-user-profile-privacy` (PAS `origin/main`) après le premier push.
````
