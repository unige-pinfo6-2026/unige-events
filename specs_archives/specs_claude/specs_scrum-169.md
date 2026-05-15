# SCRUM-169 — Identifier les profils utilisateur par `username` plutôt que par UUID

| Champ | Valeur |
|---|---|
| Ticket Jira | [SCRUM-169](https://pinfo-groupe6.atlassian.net/browse/SCRUM-169) (8 SP) |
| Sprint | S7 (calendrier produit) — préfixe Jira `[FULLSTACK][S7]` |
| Épic | [SCRUM-13](https://pinfo-groupe6.atlassian.net/browse/SCRUM-13) — Profils utilisateurs et social |
| Story | — (ticket transversal, pas de US-N rattachée) |
| Story Points | 8 |
| Branche | `feature/scrum-169-profile-username-url` (cf. Décision A — base = tip de `feature/scrum-137-146-doc-and-views`, PR #170 stacked) |
| Base | `feature/scrum-137-146-doc-and-views` (tip `d9b15f3d fix(scrum-146): unify comment delete confirmation with site-wide modal pattern`). Cible PR : `main`. |
| Auteur spec | Elie Bussod (rédaction assistée Claude Opus 4.7) |
| Date | 2026-05-14 |
| PR de référence | `feat(scrum-169): replace UUID-based profile URLs with usernames` |
| Mode de travail | **Une seule branche, une seule PR, livrée en pleine autonomie** jusqu'à ouverture PR + boucle CI/review Copilot. Elie merge lui-même. |
| Règle d'or `openapi-first` | **APPLICABLE — 1 nouveau champ (`username`) sur 2 schémas (`User`, `UserPublicResponse`) + 3 nouveaux paths (`PATCH /users/me/username`, `GET /users/by-username/{username}`, `HEAD /users/by-username/{username}`) + 1 nouveau schéma (`UpdateUsernameRequest`).** Modifier [`openapi/openapi.yaml`](openapi/openapi.yaml) AVANT toute ligne de code Java. Voir [`backend/AGENTS.md`](backend/AGENTS.md). |

> **Pré-requis lecture (chemins post-migration microservices).** Le backlog [`backend/docs/backlog_s5_s10.md` ligne 882](backend/docs/backlog_s5_s10.md#L882) référence des chemins monolithiques pré-PR #158 (`backend/src/main/java/ch/unige/events/entity/User.java`, `SchemaFixup.java`). Les chemins actuels sont **tous** sous `backend/services/user-service/src/main/java/ch/unige/events/user/`. `SchemaFixup.java` **n'existe pas** dans le repo (mention obsolète du backlog — `find backend/services -name 'SchemaFixup*' = ∅`) et n'a donc rien à mettre à jour.

---

## 1. Objectifs & non-objectifs

### Objectifs

- **Axe 1 — Schéma backend.** Ajouter le champ `username: String` à l'entité [`User.java`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java) (`@NotBlank`, `@Pattern(regexp = "^[a-z0-9._-]{3,30}$")`, `@Column(nullable=false, unique=true)`), avec finder statique `findByUsername(String)`.
- **Axe 2 — Migration Flyway V3.** Un seul fichier `V3__add_user_username.sql` qui ajoute la colonne, back-fill via génération auto (slug `displayName` → fallback `firstName.lastName` → fallback `user`), résout les collisions par suffixe numérique incrémental, puis bascule en `NOT NULL UNIQUE`. PL/pgSQL pur (atomique avec la migration).
- **Axe 3 — Endpoints.** Trois nouveaux endpoints :
  - `PATCH /api/users/me/username` (body `UpdateUsernameRequest { username }`) — change le username de l'appelant.
  - `GET /api/users/by-username/{username}` — lookup case-insensitive ; respecte la règle anti-oracle `profilePublic` existante sur `GET /users/{id}`.
  - `HEAD /api/users/by-username/{username}` — check d'unicité léger pour le debounce frontend (200 = pris, 404 = libre).
- **Axe 4 — OpenAPI.** Champ `username` ajouté sur `User` **et** `UserPublicResponse` (cf. Décision E — `username` exposé même aux anonymes). Codes d'erreur applicatifs documentés : `USERNAME_TAKEN` (409), `USERNAME_INVALID` (400), `USERNAME_RESERVED` (400).
- **Axe 5 — Types et services frontend.** `User.username` passe de `string?` à `string` (non-optionnel) dans `frontend/src/types/user.ts`. `userService.ts` reçoit `getUserByUsername`, `updateUsername`, `checkUsernameAvailable`.
- **Axe 6 — Routing frontend.** Route `/profile/:username` (au lieu de `/profile/:id`). `/profile/me` reste un alias résolu côté `ProfilePage`. Redirect permanent UUID → username dans `ProfilePage` (regex UUID v4 → lookup `getUserById` → `<Navigate replace />`).
- **Axe 7 — Pages frontend.** `ProfilePage` (`useParams<{ username }>` + `isOwnProfile = username === 'me' || username === currentUser?.username`). `ProfileEditPage` (champ "Nom d'utilisateur" en haut du form, validation client miroir backend, check d'unicité debouncé, update séparé du `updateProfile` global).
- **Axe 8 — Liens internes profil.** Les 4 sites en `/profile/<uuid>` migrent en `/profile/<username>` :
  - [`UserIdentity.tsx:42`](frontend/src/components/user/UserIdentity.tsx#L42) — trivial (l'objet `User` consommé inclut `username`).
  - [`EventDetailPage.tsx:521`](frontend/src/pages/event/EventDetailPage.tsx#L521) — trivial (`organizer` résolu via `getUserById` inclut `username`).
  - [`EventOrganizerTeam.tsx:94`](frontend/src/components/event/EventOrganizerTeam.tsx#L94) — nécessite enrichissement de `EventCoOrganizerDTO` avec `username` (cf. Décision K).
  - [`AttendeeCard.tsx:74`](frontend/src/components/attendees/AttendeeCard.tsx#L74) — nécessite enrichissement de `AttendanceDTO` avec `username` (cf. Décision K).
- **Axe 9 — Util `displayName.ts`.** Le fallback chain de [`frontend/src/utils/displayName.ts`](frontend/src/utils/displayName.ts) est révisé : `trimmedDisplayName > @username > "Utilisateur"` (le UUID prefix disparaît). Le commentaire « Follow-up post-PR-170 » est supprimé. Le `// TODO: SPRINT 5 : Username` de [`UserIdentity.tsx:64`](frontend/src/components/user/UserIdentity.tsx#L64) disparaît.
- **Axe 10 — Documentation.** `data-model.md` (section User : champ, pattern, stratégie de gen, blocklist), `api-contract.md` (3 nouveaux endpoints + rate-limit), `components.md` / `types.md` / `architecture.md` (frontend), `sprint-context.md` (les deux, section finale datée 2026-05-14).
- **Tests.** Backend ≥ 80 % L sur nouveau code. Frontend ≥ 80 % L sur les fichiers touchés. Tous les cas-limites du § 6 couverts.

### Non-objectifs

- **Pas de `GET /users/search`.** L'endpoint n'existe pas et reste hors scope (follow-up S9+ — cf. [`specs_scrum-137-146-views-docs.md` Décision A](specs_archives/specs_claude/specs_scrum-137-146-views-docs.md)).
- **Pas d'historique des anciens usernames** (redirect 301 sur l'ancien après change). Recommandation PO : non au S7 ; à ajouter ultérieurement si besoin.
- **Pas de préfixe `@` dans l'URL.** `/profile/jean.dupont`, pas `/profile/@jean.dupont` (recommandation PO).
- **Pas de modification de `EventDTO`** (event-service). Le frontend résout l'organizer via `GET /users/{id}` séparément — pas d'embedding `creator` à enrichir.
- **Pas de touche à `SchemaFixup.java`** (n'existe pas — mention obsolète du backlog).
- **Pas de notifications Kafka** sur changement de username (aucun consommateur n'en a besoin ; out of scope produit).
- **Pas de rate-limit anti-harvest** sur `HEAD /by-username/{username}` au-delà du `@PerUserRateLimit` standard appliqué uniquement aux authentifiés (cf. Décision D — l'endpoint est `@PermitAll` par design pour le debounce anon ; ré-évaluer en S9+ si harvest constaté).
- **Pas de merge** de la PR. Elie merge lui-même.

---

## 2. Contexte

### 2.1 Le besoin produit

L'URL d'un profil utilisateur est aujourd'hui `/profile/<uuid>`, par exemple `/profile/19f3ab78-0fbf-4cfb-896e-5c0346fabed5`. C'est illisible, impossible à mémoriser ou à partager oralement, et expose un identifiant interne (UUID DB) à du contenu public-facing. Le comportement cible : chaque utilisateur dispose d'un identifiant public `username` unique (ex. `jean.dupont`), garanti unique, modifiable depuis l'édition de profil ; l'UUID reste la clé primaire DB et l'identifiant interne (cross-service, JPA, REST clients) ; le username est une projection lisible.

Cette dette n'est plus seulement esthétique :

- PR #170 vient de livrer un util [`displayName.ts`](frontend/src/utils/displayName.ts) dont la docstring acte explicitement le follow-up : *« replace UUID short with @username once the username system is implemented (tracked as separate ticket, post-PR-170) »*. SCRUM-169 est ce ticket.
- [`UserIdentity.tsx:64`](frontend/src/components/user/UserIdentity.tsx#L64) porte un `// TODO: SPRINT 5 : Username` qui traîne depuis le sprint 5 — le composant affiche `@username` en variant `card`, mais retombe sur le placeholder littéral `"username"` faute d'un champ peuplé.
- Le type frontend [`User`](frontend/src/types/user.ts) déclare déjà `username?: string` (depuis le sprint 5) mais l'API ne le peuple jamais — c'est un champ fantôme.

### 2.2 Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| Aucun champ `username` sur l'entité [`User.java`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java) | Impossible de stocker un username, de l'unicifier, de le valider |
| Aucun finder `findByUsername(String)` | `GET /users/by-username/{username}` impossible |
| Aucun endpoint `PATCH /users/me/username` | Le frontend ne peut pas exposer un champ "Nom d'utilisateur" dans `ProfileEditPage` |
| Aucun endpoint `GET /users/by-username/{username}` | La route `/profile/:username` ne peut pas résoudre l'utilisateur cible |
| Aucun endpoint `HEAD /users/by-username/{username}/exists` | Le check d'unicité debouncé côté `ProfileEditPage` doit retomber sur un `GET` lourd |
| Aucune migration `username` | La colonne n'existe pas en DB ; Hibernate `validate` casserait au startup si on ajoutait le champ en Java seul |
| Aucun champ `username` dans le schéma OpenAPI `User` / `UserPublicResponse` | Le contrat consommé par tout le frontend n'expose pas l'identifiant public |
| Route React `/profile/:id` accepte un UUID uniquement | Liens en `/profile/jean.dupont` impossibles |
| Logique `isOwnProfile` boguée dans [`ProfilePage.tsx:82`](frontend/src/pages/profile/ProfilePage.tsx#L82) (`id === currentUser.auth0Id` au lieu de `id === currentUser.id`) | Incohérence pré-existante détectée lors de la rédaction — sera redressée par le passage à `username` (le param sera typé `username`, plus `id`) |
| Fallback de [`displayName.ts`](frontend/src/utils/displayName.ts) utilise un UUID prefix faute de username | UX dégradée pour les comptes Auth0 sans claim `name` |
| `// TODO: SPRINT 5 : Username` dans `UserIdentity.tsx:64` | Placeholder visible (`@username` littéral) |
| 4 sites `/profile/<uuid>` (`UserIdentity`, `EventDetailPage` organizer, `EventOrganizerTeam`, `AttendeeCard`) | Tous les liens internes vers profils restent illisibles |

### 2.3 Ce qui existe déjà à RÉUTILISER tel quel (ne pas recréer)

| Élément | Fichier / ligne | Rôle dans SCRUM-169 |
|---|---|---|
| Entité `User` (PanacheEntityBase, `id: UUID`, `auth0Id`, `email`, `displayName`, `firstName`, `lastName`, `profilePublic`, `@Version version`) | [`User.java`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java) | Cible — on ajoute `username` + finder, on touche à rien d'autre |
| Finders statiques `findByAuth0Id`, `findByEmail`, `findByCalendarToken` (Optional-based, sur `Panache.find(...).firstResultOptional()`) | [`User.java:76-86`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java#L76-L86) | Modèle direct pour `findByUsername(String)` (case-insensitive — cf. Décision E) |
| Pattern `@Transactional` sur service + `getOrCreateUser(auth0Id, JsonWebToken)` + `updateMyProfile(auth0Id, UpdateProfileRequest)` + helpers `flushEntityManager`/`optimisticLockConflict`/`isUniqueAuth0Conflict` | [`UserService.java`](backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java) | Pattern à dupliquer pour `updateUsername` (gestion `@Version` + `OptimisticLockException` + unique-constraint conflict → 409 `USERNAME_TAKEN`) |
| Pattern Resource `@Path("/users")` + `@Authenticated` + `@PerUserRateLimit` + `Response.ok(...)` | [`UserResource.java`](backend/services/user-service/src/main/java/ch/unige/events/user/resource/UserResource.java) | Modèle direct pour `PATCH /users/me/username` + les 2 lookups `/by-username/...` |
| Anti-oracle 404 sur `profilePublic = false` (admin bypass préservé) | [`UserResource.java:69-86`](backend/services/user-service/src/main/java/ch/unige/events/user/resource/UserResource.java#L69-L86) + [`UserService.java:80-109`](backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java#L80-L109) | À appliquer **identiquement** dans `getByUsername` et `existsByUsername` — même envelope 404 (anti-oracle ISSUE-93) |
| DTOs `UserProfileResponse`, `UserPublicResponse`, `UpdateProfileRequest`, `PublicProfileView` | [`backend/services/user-service/src/main/java/ch/unige/events/user/dto/`](backend/services/user-service/src/main/java/ch/unige/events/user/dto/) | À étendre (ajout du champ `username`), pas à dupliquer. `PublicProfileView` est un record container (`User + counters + followStatus`) — `username` est porté par le `User` interne donc rien à changer ici, mais `UserPublicResponse.from(...)` et `fromAnonymous(...)` doivent projeter `username`. |
| Pattern `record UpdateUsernameRequest(...)` dans le même dossier `dto/` | [`UpdateProfileRequest.java`](backend/services/user-service/src/main/java/ch/unige/events/user/dto/UpdateProfileRequest.java) | Modèle direct (record + Bean Validation) |
| `ApiErrorResponse` envelope + `optimisticLockConflict` wrapper | [`UserService.java:230-243`](backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java#L230-L243) | Pattern d'erreur applicative à dupliquer pour `USERNAME_TAKEN` / `USERNAME_INVALID` / `USERNAME_RESERVED` |
| Tests `UserResourceTest`, `UserServiceTest`, `UserDomainSentinelsTest`, `TestFixtures`, `JwtTestHelper`, `JwtTestContext` | [`backend/services/user-service/src/test/java/.../user/`](backend/services/user-service/src/test/java/ch/unige/events/user/) | Suites existantes — ajouter cas username (pas créer de nouvelles classes ; `UserResourceTest` reçoit `PATCH /me/username` + lookups, `UserServiceTest` reçoit gen-slug + collision + blocklist) |
| `application.properties` `quarkus.flyway.migrate-at-start=true`, `validate-on-migrate=false`, `out-of-order=true` | [`backend/services/user-service/src/main/resources/application.properties:19-27`](backend/services/user-service/src/main/resources/application.properties#L19-L27) | Permet d'insérer un V3 entre V1 et V2 si besoin (mais ici V3 vient après V2 naturellement) |
| Pattern entité PanacheEntityBase + `@Version` optimistic locking | [`User.java:69-71`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java#L69-L71) | `updateUsername` doit incrémenter `version` comme `updateMyProfile` |
| **Frontend** `FormField` + `Input` (avec prop `error`) | [`frontend/src/components/utils/FormField.tsx`](frontend/src/components/utils/FormField.tsx) | Champ "Nom d'utilisateur" dans `ProfileEditPage` (variant inline + helper text colored) |
| Pattern toast `useToast` | [`frontend/src/hooks/useToast.ts`](frontend/src/hooks/useToast.ts) | Feedback succès/erreur sur update username |
| Pattern `useAuth.updateUser(freshUser)` post-mutation | [`frontend/src/pages/profile/ProfileEditPage.tsx:155`](frontend/src/pages/profile/ProfileEditPage.tsx#L155) | À répliquer après un `PATCH /me/username` réussi (sinon le username affiché reste l'ancien) |
| `Attendance` type frontend enrichi de `displayName` + `avatarUrl` | [`frontend/src/types/attendance.ts`](frontend/src/types/attendance.ts) | Cible d'enrichissement (cf. Décision K) — ajouter `username: string` non-null après back-fill |
| `CoOrganizer` type frontend enrichi de `displayName` + `avatarUrl` | [`frontend/src/types/coOrganizer.ts`](frontend/src/types/coOrganizer.ts) | Cible d'enrichissement (cf. Décision K) — idem |
| Mapper d'enrichissement attendance | [`AttendanceDTOMapper.java:32-33`](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/dto/AttendanceDTOMapper.java#L32-L33) | Ajoute `username` à côté de `displayName`/`avatarUrl` (mêmes lignes, même `user` REST DTO côté user-service) |
| Linter OpenAPI optionnel | [`@redocly/cli`](https://redocly.com/docs/cli/) (npx, pas de dep installée) | Vérification de cohérence du YAML après modifs |

### 2.4 Pourquoi maintenant

- **Déblocage du follow-up PR #170.** L'util `displayName.ts` livré dans PR #170 (cf. [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) section 2026-05-14) acte explicitement *« remplacer le UUID prefix par un @username une fois le système username livré (post-PR #170) »*. PR #170 ouverte au moment de la rédaction → SCRUM-169 démarre en stacked (cf. Décision A).
- **Nettoyage du `// TODO: SPRINT 5 : Username`** dans `UserIdentity.tsx:64` — résiduel du sprint 5 ; le champ frontend `User.username` existe en option depuis cette époque mais n'a jamais été peuplé.
- **Redressement de l'incohérence `isOwnProfile`** dans [`ProfilePage.tsx:82`](frontend/src/pages/profile/ProfilePage.tsx#L82) — actuellement `id === currentUser.auth0Id`, alors que le param de route est censé être l'UUID DB. Le passage à `:username` clôt naturellement cette divergence (la comparaison devient `username === currentUser.username`).
- **Aucune dépendance amont** (post-#170). Compatible avec les tickets S7 en cours.
- **Anticipe SCRUM-141** (page profil public S8 si remappée S7) — un username public-facing est un pré-requis ergonomique avant l'élargissement social.

---

## 3. Décisions techniques tranchées (NE PAS REVISITER pendant l'implémentation)

> **Règle.** Une fois la spec validée par Elie, ces décisions ne se rediscutent pas pendant l'implémentation. Toute déviation doit être documentée dans `sprint-context.md` à la livraison.

### Décision A — Branche `feature/scrum-169-profile-username-url`, base = tip de PR #170 (stacked)

**Décision.** La branche s'appelle `feature/scrum-169-profile-username-url` et est créée à partir du tip de `feature/scrum-137-146-doc-and-views` (PR #170 ouverte). La PR cible `main`. Si #170 merge avant SCRUM-169, rebase sur `main` (trivial : pas de modification de l'`EventDTO` ni des fichiers du périmètre principal de #170 hormis `displayName.ts`).

**Justification.** Trois raisons concrètes :
- `frontend/src/utils/displayName.ts` (livré par #170) est modifié par SCRUM-169 (axe 9). En partant de `main`, le fichier n'existerait pas → conflit triple à la PR-#170-merge.
- `ProfilePage.tsx` reçoit dans #170 le composant `CoOrganizerInvitationsList`. SCRUM-169 modifie `useParams`, `isOwnProfile`, et le redirect — partir de la tip de #170 permet d'éviter un conflit textuel.
- `EventDetailPage.tsx`, `Navbar.tsx`, `UserIdentity.tsx` sont tous touchés par #170 ; les diffs SCRUM-169 se posent proprement par-dessus.

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) `feature/scrum-169-...`, base = tip #170 (stacked, target `main`) | Pas de conflits avec #170 ; livraison parallèle | Si #170 ne merge pas → rebase à faire ; review GitHub affiche un diff contre `main` qui inclut #170 jusqu'à son merge | ✅ retenu |
| (b) Attendre #170 merge, puis brancher sur `main` | Diff propre dès la PR | Bloque SCRUM-169 sur le calendrier de merge de #170 (jours / semaine) | ❌ |
| (c) Brancher sur `main` directement maintenant | Diff propre immédiat | Conflits inévitables sur ≥ 4 fichiers, dont `displayName.ts` qui n'existe que sur #170 | ❌ |

**Convention de nom.** `feature/scrum-XXX-description` (préfixe `scrum-` minuscule + ticket Jira) — conforme à [`AGENTS.md` racine](AGENTS.md). On n'utilise **pas** l'alias `feature/s7-profile-username-url` du backlog (les conventions de la team privilégient l'identifiant Jira sur l'alias sprint).

### Décision B — URL `/profile/:username` sans préfixe `@`, alias `/profile/me` résolu côté composant

**Décision.** Route React Router `/profile/:username`. `/profile/me` reste un alias **uniquement résolu côté composant** (`useParams<{ username }>()` → si `username === 'me'`, utiliser `currentUser` directement). Le username `me` est dans la blocklist (cf. Décision F) — impossible qu'un user humain le réserve.

**Justification.** Préfixe `@` :

| Option | Verdict |
|---|---|
| (a) `/profile/jean.dupont` (sans `@`) | ✅ retenu — recommandation PO ; cohérent avec GitHub, Linear, Slack-mention-style sans le sigil |
| (b) `/profile/@jean.dupont` | ❌ ajoute un char hors-pattern à gérer en routing (encodage `%40`), pas de bénéfice fonctionnel |

**Convention `/me`.** L'alias `me` survit au passage `:id → :username` parce qu'il est dans la blocklist et donc inaccessible aux usernames humains.

### Décision C — Endpoint dédié `PATCH /users/me/username` (pas une extension de `PUT /users/me`)

**Décision.** Le username est mis à jour via un endpoint dédié `PATCH /api/users/me/username` avec body `UpdateUsernameRequest { username }`. **Pas** d'extension de `PUT /users/me` (qui consomme `UpdateProfileRequest`).

**Justification.** Quatre raisons :

| Aspect | Endpoint dédié `PATCH /me/username` | Extension `PUT /users/me` |
|---|---|---|
| Granularité d'erreur | `409 USERNAME_TAKEN` / `400 USERNAME_INVALID` / `400 USERNAME_RESERVED` — code applicatif clair | Mélange avec d'autres erreurs profil (`displayName` trop long, etc.) |
| Live-check / debounce | Endpoint indépendant, ne soumet pas le reste du form | Forcerait à re-poster tout le profil |
| Rate-limit séparé | `@PerUserRateLimit(name="users.updateUsername", max=5)` — anti-spam dédié | Diluerait le quota existant `users.updateMe` |
| Cohérence REST | `PATCH` sur ressource sous-jacente (`/me/username`) — RFC 5789 | `PUT /users/me` est déjà un update partiel ad hoc — l'extension serait incohérente |

| Option | Verdict |
|---|---|
| (a) `PATCH /users/me/username` (endpoint dédié) | ✅ retenu |
| (b) Extension de `UpdateProfileRequest.username` dans `PUT /users/me` | ❌ |
| (c) `POST /users/me/username` (création-style) | ❌ — le username existe déjà après back-fill, c'est un update, pas une création |

### Décision D — `HEAD /by-username/{username}` pour le check d'unicité (200 = pris, 404 = libre)

**Décision.** Endpoint dédié `HEAD /api/users/by-username/{username}` qui retourne `200 OK` (corps vide) si le username est pris, `404 Not Found` si libre. Sécurité : `@PermitAll` (debounce côté form d'édition peut tourner avant authent ; le harvest est mitigé par la simple existence d'un autre `GET /users/{id}` énumérable et par `@PerUserRateLimit` standard quand l'appel est authentifié).

**Justification.**

| Aspect | `HEAD /by-username/{u}` | Réutiliser `GET /by-username/{u}` |
|---|---|---|
| Coût réseau | Headers seuls — quelques centaines d'octets | Body `UserPublicResponse` complet — ~1 Ko |
| Coût DB | `SELECT 1 FROM users WHERE username = ? LIMIT 1` (existence only) | `SELECT * FROM users WHERE username = ?` + count `Follow` (anti-oracle) |
| Sémantique | RFC 7231 §4.3.2 : HEAD = "métadonnées seulement" — adapté pour "ressource existe-t-elle ?" | OK mais sémantiquement bruyant |
| Anti-oracle | Réponse identique pour profil privé existant et profil inexistant : `200` (pris) — pas d'oracle d'existence (cf. Décision G ci-dessous) | Idem |

| Option | Verdict |
|---|---|
| (a) `HEAD /by-username/{u}` dédié, `200`/`404`, `@PermitAll` | ✅ retenu |
| (b) Réutiliser `GET /by-username/{u}`, ignorer le body | ❌ — coût DB et réseau injustifié pour un debounce 400 ms |
| (c) `GET /by-username/{u}/exists` retournant `{ exists: bool }` | ❌ — sigil REST faible et incohérent avec le reste de l'API |

**Sémantique inversion.** `200 = pris`, `404 = libre` — intuitivement contre-intuitif (`404` est "OK" pour l'utilisateur qui veut prendre ce nom). Documenté explicitement dans l'OpenAPI **et** dans le JavaDoc de `UserResource.existsByUsername`. La couche frontend `checkUsernameAvailable` traduit `404 → true (disponible)`, `200 → false (pris)`.

### Décision E — `username` exposé même aux anonymes (pas de stripping)

**Décision.** Dans `UserPublicResponse.from(...)` **et** `UserPublicResponse.fromAnonymous(...)`, le champ `username` est **toujours présent et peuplé**. Contrairement à `email`/`bio`/`interests` qui sont nullifiés en anonyme (finding pentest 2026-04-17 — finding 4.1b), le username est par définition l'identifiant **public-facing** du profil.

**Justification.** Le username est le canal de partage humain du profil (URL, "@jean.dupont"). Le nullifier en anonyme casserait :
- L'affichage `@jean.dupont` sur `EventDetailPage` quand un anonyme consulte un événement.
- La construction des URLs `/profile/<username>` côté frontend SSR-friendly future.
- La résolution `GET /by-username/{u}` anon (un anon doit pouvoir naviguer vers `/profile/jean.dupont`).

| Option | Verdict |
|---|---|
| (a) `username` exposé aux anonymes | ✅ retenu |
| (b) `username` strippé en anonyme (cohérent avec `email`) | ❌ — casse l'usage premier du username |

**Anti-harvest.** Le `username` est de toute façon exposé via le partage de l'URL elle-même. Sa visibilité dans la payload publique n'ajoute pas de surface de harvest au-delà de ce que `GET /users/{id}` expose déjà avec `id`/`displayName`/`avatarUrl`.

### Décision F — Stockage lowercase + lookup case-insensitive, pattern `^[a-z0-9._-]{3,30}$`, blocklist explicite

**Décision.** Stockage en base : **lowercase** strict (colonne `username VARCHAR(30) NOT NULL UNIQUE`, contrainte `CHECK (username ~ '^[a-z0-9._-]{3,30}$')`). Côté service : `username.toLowerCase().trim()` avant chaque comparaison ou écriture. Lookups (`findByUsername`, endpoints `/by-username/{u}`) normalisent le param entrant (`u.toLowerCase()`) avant le SELECT — donc `GET /by-username/Jean.Dupont` matche `jean.dupont` en DB.

Pattern Bean Validation `@Pattern(regexp = "^[a-z0-9._-]{3,30}$")` + `@NotBlank` sur :
- `User.username` (entité, cohérence persistance).
- `UpdateUsernameRequest.username` (validation du body PATCH).

Blocklist (rejet en update **et** auto-évitement en back-fill) — constante Java `UserService.RESERVED_USERNAMES = Set.of("me", "admin", "api", "login", "logout", "signup", "register", "settings")` + miroir frontend `frontend/src/types/user.ts` (export `RESERVED_USERNAMES`).

**Justification.**

| Aspect | Décision | Raison |
|---|---|---|
| Casse stockage | Lowercase strict | Évite les "deux Jean Dupont" différents par casse ; `LIKE` insensible inutile ; unique index simple |
| Pattern | `^[a-z0-9._-]{3,30}$` | Cohérent avec GitHub/Twitter/Linear ; pas d'Unicode (ASCII fold appliqué au back-fill — cf. Décision G) ; min 3 (sub-3 chars trop confusable), max 30 (limite raisonnable, ` VARCHAR(30)`) |
| Blocklist | 8 entrées au minimum | `me` indispensable (collision route alias) ; `admin`/`api`/`login`/`logout`/`signup`/`register`/`settings` anticipent routes futures et squat évident |

| Option | Verdict |
|---|---|
| (a) Lowercase stockage + case-insensitive lookup | ✅ retenu |
| (b) Casse préservée à l'écriture, lookup case-insensitive | ❌ — complexifie l'index unique (UPPER/LOWER functional) |
| (c) Case-sensitive end-to-end | ❌ — UX : `Jean.Dupont` et `jean.dupont` indistinguables visuellement |

### Décision G — Génération auto en SQL pur (PL/pgSQL DO block) dans la migration V3, suffixe numérique incrémental

**Décision.** La stratégie de back-fill est implémentée **entièrement en PL/pgSQL** dans la migration V3 (atomique, pas de race avec démarrage app multi-replica, pas de code Java orphelin "one-shot").

Algorithme :
1. Pour chaque row `users` sans username, calculer un slug candidat :
   - Base = `lower(unaccent(coalesce(nullif(trim(display_name), ''), trim(first_name)||'.'||trim(last_name), 'user')))`.
   - Normalisation : remplacer les espaces/multi-spaces par `.`, retirer tout char hors `[a-z0-9._-]`, collapser les `.` consécutifs, trim leading/trailing `._-`.
   - Tronquer à 30 chars (limite colonne).
   - Si le résultat est vide ou < 3 chars → `'user'`.
2. Vérifier la collision : si `EXISTS(SELECT 1 FROM users WHERE username = candidat AND id <> current_id)`, recommencer avec suffixe numérique incrémental `candidat`, `candidat2`, `candidat3`, ...
3. Vérifier la blocklist : si le candidat (sans suffixe) est dans `('me','admin','api','login','logout','signup','register','settings')`, démarrer directement au suffixe `2` (ex. `admin2`).
4. `UPDATE users SET username = <résultat>` pour chaque row.

**Dépendance.** `unaccent` est une extension PostgreSQL standard ; vérifier avant si elle est chargée (`SELECT 1 FROM pg_extension WHERE extname='unaccent'`). Si non, `CREATE EXTENSION IF NOT EXISTS unaccent;` en tête de migration. (Alternativement, `translate()` ASCII brute pour le sous-ensemble courant. Préférer `unaccent` — couvre `François` → `Francois`, `Müller` → `Muller`, `Đorđe` → `Dorde`, etc.)

**Justification SQL pur vs code Java post-migration.**

| Aspect | SQL dans la migration V3 | Code Java post-migration (one-shot CDI startup) |
|---|---|---|
| Atomicité | Une seule transaction Flyway — succès ou rollback total | Démarrage en deux temps : migration up sans username, puis code Java qui back-fill |
| Race multi-replica | Aucune — Flyway pose `SELECT pg_try_advisory_lock` | Race possible si 2 replicas démarrent en parallèle (mitigeable par `LOCK TABLE users IN EXCLUSIVE MODE` mais complexe) |
| Test DevServices `%test.drop-and-create` | OK — la migration n'est pas exécutée en test (cf. `quarkus.flyway.enabled=false` en test) mais le `drop-and-create` régénère le schéma directement depuis l'entité (qui aura `nullable=false`) ; pas de problème de back-fill puisque pas de données héritées | Idem |
| Maintenance | Une fois mergé, jamais retouché | Code mort à supprimer après prod stable |

| Option | Verdict |
|---|---|
| (a) PL/pgSQL DO block dans V3, suffixe numérique | ✅ retenu |
| (b) Code Java one-shot (`@Startup` @ApplicationScoped + `if (count missing > 0) backfill()`) | ❌ — complexité, race multi-replica, code mort |
| (c) Suffixe random (4 chars hex) | ❌ — instable au re-back-fill, illisible, justification PO contraire |

### Décision H — Migration V3 atomique, un seul fichier, pas de split V3/V4

**Décision.** **Un seul** fichier `backend/services/user-service/src/main/resources/db/migration/V3__add_user_username.sql` qui enchaîne :

```text
1. CREATE EXTENSION IF NOT EXISTS unaccent;     -- idempotent
2. ALTER TABLE users ADD COLUMN username VARCHAR(30) NULL;
3. DO $$ BEGIN ... END $$;                       -- back-fill (PL/pgSQL, cf. Décision G)
4. ALTER TABLE users ALTER COLUMN username SET NOT NULL;
5. ALTER TABLE users ADD CONSTRAINT uq_users_username UNIQUE (username);
6. ALTER TABLE users ADD CONSTRAINT ck_users_username CHECK (username ~ '^[a-z0-9._-]{3,30}$');
```

**Justification.** Splitter en V3 (column nullable + back-fill) puis V4 (set not null + constraints) ajouterait un état intermédiaire transitoire **inutile** : la migration s'exécute sur preview/prod sans rupture trafic (back-fill < 1 s pour < 1000 users — vérifié par profil de chargement). Le split V3+V4 augmenterait le risque d'oublier l'un des deux fichiers en backport. **Une seule migration immutable**.

**Risque preview-deploy documenté.** La leçon Flyway-immutabilité de [`specs_scrum-139.md` note 2026-05-08](specs_archives/specs_claude/specs_scrum-139.md) s'applique : si V3 est poussé sur la PR et qu'un `helm upgrade` preview applique le migrant, **toute modification ultérieure de V3 fera échouer Flyway avec "checksum mismatch"**. Si une correction de la migration est nécessaire post-push, créer un `V4__fix_username_<correctif>.sql` additif (jamais modifier V3 en place). L'agent d'implémentation doit vérifier `ls backend/services/user-service/src/main/resources/db/migration` au début de chaque étape pour s'assurer qu'aucune autre PR n'a mergé un V3 entre-temps — si conflit, bumper à V4 et adapter toutes les références de cette spec.

| Option | Verdict |
|---|---|
| (a) Une seule migration V3 atomique | ✅ retenu |
| (b) Split V3 (column + backfill) + V4 (set not null + constraints) | ❌ — état transitoire inutile |
| (c) Migration en deux passes manuelles (V3 + script de back-fill DBA) | ❌ — hors-process, perd la reproductibilité Flyway |

### Décision I — Redirect transitoire UUID → username dans `ProfilePage`, **permanent**

**Décision.** Dans `ProfilePage.tsx`, après `useParams<{ username }>()`, si la valeur du param matche la regex `^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$` (UUID v4 standard) :
1. Appeler `getUserById(uuid)`.
2. Si trouvé → `<Navigate to={`/profile/${user.username}`} replace />`.
3. Si `null`/404 → `InfoMessage type="error"` "Profil introuvable" (pas de redirect en boucle).

Le redirect est **permanent** (pas de date d'expiration au S8 ou S9) : peu de coût (3 lignes + 1 call API), robuste aux liens externes en cache, aux bookmarks, et aux liens encore présents dans des emails / messages produits avant SCRUM-169.

**Justification.**

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) Permanent | Robuste, gratuit | Code reste indéfiniment | ✅ retenu (recommandation PO) |
| (b) À supprimer 1-2 sprints après merge | Code mort éliminé | Casse les liens en cache après le drop ; impossible de savoir quand "tous" les liens sont migrés | ❌ |
| (c) Pas de redirect, 404 sur tous les vieux UUIDs | Plus simple | UX dégradée immédiate, perd les liens partagés | ❌ |

### Décision J — OpenAPI : `username` requis sur `User` et `UserPublicResponse`, 3 nouveaux paths, 1 nouveau schéma

**Décision.** Modifications de [`openapi/openapi.yaml`](openapi/openapi.yaml) :

1. **Schéma `User`** (ligne ~59) : ajout du champ `username: string` avec `pattern: "^[a-z0-9._-]{3,30}$"`, `minLength: 3`, `maxLength: 30`, **non-nullable**, ajouté à `required` (le champ existe toujours après back-fill).
2. **Schéma `UserPublicResponse`** (ligne ~130) : même ajout, même contraintes, présent même pour l'appelant anonyme (cf. Décision E) — `required: [id, username, followerCount, followingCount]`.
3. **Schéma `UpdateUsernameRequest` (nouveau)** : `{ username: string (pattern + min + max) }` ; `required: [username]`.
4. **Path `PATCH /users/me/username`** : `tags: [users]`, `security: BearerAuth`, body `UpdateUsernameRequest`, responses :
   - `200` → `User` complet (frontend remplace `useAuth.user` avec ce body).
   - `400` → `ApiErrorResponse` (codes `username_invalid` ou `username_reserved`).
   - `401` → token absent/invalide.
   - `404` → user non provisionné (appel `GET /me` d'abord).
   - `409` → `ApiErrorResponse` `username_taken` (race entre debounce check et submit).
   - `429` → `RateLimited`.
5. **Path `GET /users/by-username/{username}`** : `security: [{} ; BearerAuth: []]` (cohérent avec `GET /users/{id}`), parameters `username: string` (pattern, min, max), responses :
   - `200` → `UserPublicResponse` (anti-oracle 404 si profil privé non-owner — cf. `GET /users/{id}` finding 4.1).
   - `404` → not found (envelope identique à UUID inexistant).
6. **Path `HEAD /users/by-username/{username}`** : `security: []` (anonyme OK, cf. Décision D), responses :
   - `200` → "username pris" (pas de body).
   - `404` → "username libre".
   - description explicite sur l'inversion sémantique.

**Codes d'erreur applicatifs documentés dans la description des responses** : `username_taken` (409), `username_invalid` (400, pattern KO), `username_reserved` (400, blocklist hit).

**Justification.** Voir Décisions C/D/E/F. Le champ est `required` sur les **deux** schémas parce que le back-fill garantit qu'il existe pour 100 % des rows ; déclarer `nullable: true` créerait une surface optionnelle inutile dans les types TS générés (côté frontend, on veut `username: string` strict).

### Décision K — Enrichissement `AttendanceDTO` (engagement-service) et `EventCoOrganizerDTO` (event-service) avec `username`

**Décision.** Les DTOs enrichis qui exposent déjà `displayName` et `avatarUrl` pour éviter le N+1 reçoivent **en parallèle** le champ `username`. Concrètement :

- **engagement-service** : [`AttendanceDTOMapper.java:32-33`](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/dto/AttendanceDTOMapper.java#L32-L33) ajoute `user.username()` aux deux lignes existantes. Le record `AttendanceDTO` gagne un champ `String username`. Le REST client vers user-service (qui peuple le record `user`) est mis à jour pour fetcher `username` (probablement aucune modif si le DTO miroir côté engagement-service réutilise déjà le `UserPublicResponse` complet — à vérifier au moment de l'impl).
- **event-service** : `EventCoOrganizerDTO` (sous `event.coorganizer.dto`) gagne un champ `String username`. Mapper équivalent mis à jour.
- **OpenAPI** : schémas `AttendanceDTO` et `EventCoOrganizerDTO` reçoivent le champ `username: string` (required, same constraints).

**Justification.** Sans cet enrichissement, [`AttendeeCard.tsx:74`](frontend/src/components/attendees/AttendeeCard.tsx#L74) et [`EventOrganizerTeam.tsx:94`](frontend/src/components/event/EventOrganizerTeam.tsx#L94) ne pourraient pas construire `/profile/<username>` sans introduire un N+1 (un appel `getUserById` par row affichée). Le pattern d'enrichissement est **déjà en place** pour `displayName`/`avatarUrl` ; l'ajout de `username` est strictement additif et coût-zéro (champ inclus dans le DTO renvoyé par le REST client interne).

**Non-objectif explicite.** `EventDTO` (event-service) n'est **pas** enrichi (cf. § 1 et garde-fou général) — son `creatorId: UUID` suffit, et `EventDetailPage` résout déjà l'organizer via un appel séparé à `GET /users/{id}` qui retournera `username`.

| Option | Verdict |
|---|---|
| (a) Enrichir `AttendanceDTO` + `EventCoOrganizerDTO` avec `username` | ✅ retenu — coût-zéro, pas de N+1 |
| (b) Garder les DTOs inchangés, faire des `getUserById` N+1 côté frontend dans AttendeeCard/OrganizerTeam | ❌ — UX, charge réseau |
| (c) Ne pas migrer les 2 sites concernés (laisser `/profile/<uuid>` pour AttendeeCard + OrganizerTeam) | ❌ — incohérence avec le but du ticket |

---

## 4. Inventaire des changements

### 4.1 OpenAPI (1 fichier — règle d'or `openapi-first`)

| Fichier | Changement | Motif |
|---|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | (a) Champ `username` ajouté à `User` (required, pattern, min/max). (b) Champ `username` ajouté à `UserPublicResponse` (required, exposé même aux anonymes — cf. Décision E). (c) Nouveau schéma `UpdateUsernameRequest`. (d) Nouveau path `PATCH /users/me/username` (200/400/401/404/409/429). (e) Nouveau path `GET /users/by-username/{username}` (200/404, security `[{} ; BearerAuth: []]`). (f) Nouveau path `HEAD /users/by-username/{username}` (200/404, security `[]`). (g) Champ `username` ajouté à `AttendanceDTO`. (h) Champ `username` ajouté à `EventCoOrganizerDTO`. | Décisions C/D/E/F/J/K — contrat public figé pour le frontend |

### 4.2 Backend — user-service

| Fichier | Type | Motif |
|---|---|---|
| [`User.java`](backend/services/user-service/src/main/java/ch/unige/events/user/entity/User.java) | Update | `@Column(nullable = false, unique = true) @NotBlank @Pattern(...) public String username` + finder `findByUsername(String)` case-insensitive |
| [`UserService.java`](backend/services/user-service/src/main/java/ch/unige/events/user/service/UserService.java) | Update | Nouveau `updateUsername(auth0Id, requestedUsername)` (normalise + valide blocklist + gère 409 unique-constraint) ; nouveau `getByUsername(username, callerAuth0Id, isAdmin)` (case-insensitive, anti-oracle 404 sur privé non-owner — pattern strictement aligné sur `getPublicProfile`) ; nouveau `existsByUsername(username)` (light) ; constante `RESERVED_USERNAMES`. |
| [`UserResource.java`](backend/services/user-service/src/main/java/ch/unige/events/user/resource/UserResource.java) | Update | Trois nouveaux endpoints (`PATCH /me/username`, `GET /by-username/{u}`, `HEAD /by-username/{u}`). Le PATCH porte `@PerUserRateLimit(name="users.updateUsername", max=5)`. Le GET porte `@PermitAll` (anonyme = payload stripped sauf `username` — cf. Décision E). Le HEAD porte `@PermitAll` sans rate-limit anonyme spécifique. |
| [`UpdateUsernameRequest.java`](backend/services/user-service/src/main/java/ch/unige/events/user/dto/UpdateUsernameRequest.java) | **Nouveau** | `public record UpdateUsernameRequest(@NotBlank @Pattern(...) String username) {}` |
| [`UserPublicResponse.java`](backend/services/user-service/src/main/java/ch/unige/events/user/dto/UserPublicResponse.java) | Update | Champ `username` ajouté (présent dans `from(...)` **et** `fromAnonymous(...)`) ; tests `UserPublicResponseTest` étendus |
| [`UserProfileResponse.java`](backend/services/user-service/src/main/java/ch/unige/events/user/dto/UserProfileResponse.java) | Update | Champ `username` ajouté ; tests `UserProfileResponseTest` étendus |
| [`PublicProfileView.java`](backend/services/user-service/src/main/java/ch/unige/events/user/dto/PublicProfileView.java) | (vérifier) | Si record `User + counters + status` : pas de changement (le `User` interne porte déjà `username`). Si record qui projette les champs un par un : ajouter `String username`. |
| `V3__add_user_username.sql` (nouveau) | **Nouveau** | Migration Flyway atomique (cf. Décision H). Inclut `CREATE EXTENSION IF NOT EXISTS unaccent`. |
| [`UserResourceTest.java`](backend/services/user-service/src/test/java/ch/unige/events/user/resource/UserResourceTest.java) | Update | + 6 cas (cf. § 6) — PATCH happy/409/400-invalid/400-reserved/401, GET by-username, HEAD by-username |
| [`UserServiceTest.java`](backend/services/user-service/src/test/java/ch/unige/events/user/service/UserServiceTest.java) | Update | + 7 cas (cf. § 6) — `updateUsername` cases, `getByUsername` anti-oracle, blocklist |
| [`UserPublicResponseTest.java`](backend/services/user-service/src/test/java/ch/unige/events/user/dto/UserPublicResponseTest.java) | Update | Assert `username` peuplé dans `from` **et** `fromAnonymous` |
| [`UserProfileResponseTest.java`](backend/services/user-service/src/test/java/ch/unige/events/user/dto/UserProfileResponseTest.java) | Update | Assert `username` peuplé |
| Test migration | **Nouveau** (light) | `UsernameMigrationBackfillTest.java` — `@QuarkusTest` + `@TestProfile` qui ré-active Flyway en `%test`, persist 5 users avec `displayName` variés (incl. accents et empty), exécute V3, assert tous ont un username unique respectant le pattern + valide les fallbacks/suffixes |

### 4.3 Backend — engagement-service (Décision K)

| Fichier | Type | Motif |
|---|---|---|
| [`AttendanceDTOMapper.java`](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/dto/AttendanceDTOMapper.java) | Update | + `user.username()` à la projection (lignes 32-33 + record `AttendanceDTO`) |
| `AttendanceDTO.java` (engagement-service) | Update | + champ `String username` (non-null après back-fill) |
| REST client miroir `UserPublicResponse` côté engagement-service | (vérifier) | Si record local : ajouter `username` ; si import `shared-domain-dtos` : pas de changement nécessaire au-delà de la lib. À confirmer au moment de l'impl. |
| Test associé `AttendanceDTOMapperTest` (ou équivalent) | Update | Assert `username` peuplé |

### 4.4 Backend — event-service (Décision K)

| Fichier | Type | Motif |
|---|---|---|
| Mapper / DTO `EventCoOrganizerDTO` (sous `event.coorganizer.dto`) | Update | + champ `String username` ; mapper équivalent met à jour la projection |
| Test associé | Update | Assert `username` peuplé |

### 4.5 Frontend — types et services

| Fichier | Type | Motif |
|---|---|---|
| [`frontend/src/types/user.ts`](frontend/src/types/user.ts) | Update | `username: string` (non-optionnel) + export `RESERVED_USERNAMES` (constante miroir backend, lecture seule) |
| [`frontend/src/types/attendance.ts`](frontend/src/types/attendance.ts) | Update | + `username: string` |
| [`frontend/src/types/coOrganizer.ts`](frontend/src/types/coOrganizer.ts) | Update | + `username: string` |
| [`frontend/src/services/userService.ts`](frontend/src/services/userService.ts) | Update | + `getUserByUsername(username)` → `GET /users/by-username/{u}` (retourne `User | null` ; 404 → null) ; + `updateUsername(username)` → `PATCH /users/me/username` (retourne `User`) ; + `checkUsernameAvailable(username)` → `HEAD /users/by-username/{u}` (200 → false, 404 → true) ; **conservation** de `getUserById` pour le redirect transitoire |

### 4.6 Frontend — routing et pages

| Fichier | Type | Motif |
|---|---|---|
| [`frontend/src/router/AppRouter.tsx`](frontend/src/router/AppRouter.tsx) | Update | `<Route path=":username" element={<ProfilePage />} />` au lieu de `:id` |
| [`frontend/src/pages/profile/ProfilePage.tsx`](frontend/src/pages/profile/ProfilePage.tsx) | Update | `useParams<{ username }>()`, `isOwnProfile = username === 'me' \|\| username === currentUser?.username`, lookup via `getUserByUsername` (sauf cas `me` ou UUID-redirect), redirect permanent UUID v4 → username (cf. Décision I) |
| [`frontend/src/pages/profile/ProfileEditPage.tsx`](frontend/src/pages/profile/ProfileEditPage.tsx) | Update | Nouveau champ `FormField` "Nom d'utilisateur" en haut du form, état local `usernameValue` + `usernameStatus: 'idle'\|'checking'\|'available'\|'taken'\|'invalid'\|'reserved'`, validation client (pattern + blocklist), debounced `checkUsernameAvailable` (400 ms — cf. § 5 étape 12), update séparé du `updateProfile` via `updateUsername` (skip si non modifié), gestion `409 USERNAME_TAKEN` sans blocage du reste du form, mise à jour `useAuth.updateUser(freshUser)` après succès |

### 4.7 Frontend — composants et util

| Fichier | Type | Motif |
|---|---|---|
| [`frontend/src/components/user/UserIdentity.tsx`](frontend/src/components/user/UserIdentity.tsx) | Update | Ligne 42 : `profileUrl = currentUser?.id === user?.id ? '/profile/me' : `/profile/${user?.username}`` ; ligne 64 : retirer `// TODO: SPRINT 5 : Username` et le placeholder littéral `"username"` (remplacer par `user.username`) |
| [`frontend/src/pages/event/EventDetailPage.tsx`](frontend/src/pages/event/EventDetailPage.tsx) | Update | Ligne 521 : `to={`/profile/${organizer.username}`}` (l'`organizer` est résolu via `getUserById` → `User` qui porte maintenant `username`) |
| [`frontend/src/components/event/EventOrganizerTeam.tsx`](frontend/src/components/event/EventOrganizerTeam.tsx) | Update | Ligne 94 : `to={`/profile/${username}`}` ; props enrichies (nouveau `username: string` au lieu de `userId: string` dans `OrganizerRow`, mais le `userId` reste utile au `initials`/`fallback label`) |
| [`frontend/src/components/attendees/AttendeeCard.tsx`](frontend/src/components/attendees/AttendeeCard.tsx) | Update | Ligne 74 : `to={`/profile/${profile.username}`}` ; le type `profile` est `Attendance` qui porte maintenant `username` (cf. § 4.5) |
| [`frontend/src/utils/displayName.ts`](frontend/src/utils/displayName.ts) | Update | Fallback chain révisé : `trimmedDisplayName > '@' + username > 'Utilisateur'` (le UUID prefix disparaît). Signature change : `userDisplayLabel(displayName, username?)` au lieu de `userDisplayLabel(displayName, userId?)`. Tous les call-sites (`CommentItem`, `EventOrganizerTeam.OrganizerRow`) reçoivent `username` au lieu de `userId`. Suppression du commentaire "Follow-up: replace UUID short with @username once the username system is implemented (tracked as separate ticket, post-PR-170)". |
| `frontend/src/__tests__/utils/displayName.test.ts` (si existe ; sinon créer) | Update / Nouveau | Couvrir le nouveau fallback chain |
| `frontend/src/hooks/useDebounce.ts` (**nouveau**) | **Nouveau** | Hook minimaliste `useDebounce<T>(value: T, delay: number): T` (rule of three : utilisé une fois pour le check unicité, mais isolation pour testabilité). Aucun `useDebounce` n'existe actuellement dans `frontend/src/hooks/` (vérifié : `ls frontend/src/hooks \| grep -i debounce = ∅`). |

### 4.8 Frontend — tests à mettre à jour ou créer

| Fichier | Type | Motif |
|---|---|---|
| `frontend/src/__tests__/services/userService.test.ts` | Update | + 3 nouveaux describe blocks pour `getUserByUsername`, `updateUsername`, `checkUsernameAvailable` (cf. § 6) |
| `frontend/src/__tests__/pages/profile/ProfilePage.test.tsx` | Update | Tests existant `'/profile/:id'` → `'/profile/:username'` ; + tests redirect UUID, alias `me`, 404 |
| `frontend/src/__tests__/pages/profile/ProfileEditPage.test.tsx` | Update | + tests champ username pré-rempli, validation pattern, debounced unicité (mock timers vitest), gestion 409, gestion succès, skip si non modifié, mise à jour `useAuth.updateUser` |
| `frontend/src/__tests__/router/AppRouter.test.tsx` | Update | `'/profile/auth0|123'` → `'/profile/jean.dupont'` ; + test redirect UUID v4 |
| `frontend/src/__tests__/components/user/UserIdentity.test.tsx` | Update | Lien `/profile/<username>` (plus `<id>`) |
| `frontend/src/__tests__/pages/event/EventDetailPage.test.tsx` | Update | Lien organizer `/profile/<username>` |
| `frontend/src/__tests__/components/event/EventOrganizerTeam.test.tsx` | Update | Lien team member `/profile/<username>` |
| `frontend/src/__tests__/components/attendees/AttendeeCard.test.tsx` | Update | Lien attendee `/profile/<username>` ; ligne 47 : `expect(link.getAttribute('href')).toBe('/profile/<username>')` |
| `frontend/src/__tests__/utils/displayName.test.ts` | Update / Nouveau | Couvrir fallback chain `@username` |
| `frontend/src/__tests__/hooks/useDebounce.test.ts` (nouveau) | **Nouveau** | 3 cas : valeur initiale retournée immédiatement, debounce respecté, cleanup sur unmount |

### 4.9 Documentation backend

| Fichier | Section | Modif |
|---|---|---|
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Section `User` | + ligne `username` (pattern, contrainte, generation strategy, blocklist) |
| [`backend/docs/api-contract.md`](backend/docs/api-contract.md) | Tableau endpoints user-service | + 3 lignes `PATCH /users/me/username` (rate-limit `users.updateUsername` max 5), `GET /users/by-username/{u}`, `HEAD /users/by-username/{u}` |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Section finale | Ajout section datée `2026-05-14 (suite 2) — SCRUM-169 livré (usernames)` avec résumé des axes 1-10 et la liste des fichiers touchés |

### 4.10 Documentation frontend

| Fichier | Section | Modif |
|---|---|---|
| [`frontend/docs/architecture.md`](frontend/docs/architecture.md) | Table de routage | `/profile/:id` → `/profile/:username` (avec mention du redirect transitoire UUID → username) |
| [`frontend/docs/components.md`](frontend/docs/components.md) | Section services (userService) | + 3 nouvelles signatures ; section pages : mise à jour `ProfilePage` (param `username`) et `ProfileEditPage` (champ username) |
| [`frontend/docs/types.md`](frontend/docs/types.md) | User | `username: string` (non-optional après back-fill). + `RESERVED_USERNAMES`. Attendance et CoOrganizer reçoivent `username`. |
| [`frontend/docs/sprint-context.md`](frontend/docs/sprint-context.md) | Section finale | Idem backend |

---

## 5. Plan d'exécution séquentiel (étapes numérotées, ordre strict)

> **Règle.** Un commit par étape. Format de message : `<type>(scrum-169): <description courte>`. Co-author Claude sur chaque commit. Vérification post-commit : la commande indiquée pour l'étape. Si elle échoue : revert local + fix + nouveau commit (pas d'amend, cf. AGENTS).

### Étape 1 — OpenAPI d'abord (règle d'or projet)

- **Commit** : `feat(scrum-169): update openapi with username field and 3 new endpoints`
- **Modifs** : section 4.1.
- **Vérification** : YAML valide (`npx --yes @redocly/cli@latest lint openapi/openapi.yaml` — accepter les warnings existants, viser 0 erreur sur le diff).
- **Garde-fou** : ne pas casser les schémas existants (`User`, `UserPublicResponse`) ; vérifier visuellement que les `required` ajoutés sont cohérents.

### Étape 2 — Migration Flyway V3

- **Commit** : `feat(scrum-169): add V3 migration for user.username column with backfill`
- **Modifs** : `V3__add_user_username.sql` (section 4.2).
- **Vérification** :
  - `ls backend/services/user-service/src/main/resources/db/migration` — confirmer que V3 n'est pas conflictuelle.
  - Backend `mvnw -pl services/user-service -am verify -DskipITs` doit passer (le `%test` profil drop-and-create n'exécute pas Flyway donc la migration ne casse rien ; entité pas encore modifiée à cette étape — la migration est silencieusement non-validée jusqu'à étape 3).
- **Garde-fou** : **immutabilité** une fois pushée ; toute correction passe par V4.

### Étape 3 — Entité `User.java`

- **Commit** : `feat(scrum-169): add username field and findByUsername finder to User entity`
- **Modifs** : `User.java` (section 4.2).
- **Vérification** : `mvnw -pl services/user-service -am verify -DskipITs` → en `%test` (drop-and-create), Hibernate doit générer le schéma avec la colonne. En `%dev`/`%prod` (validate), Flyway V3 applique la migration avant le validate Hibernate → cohérent.

### Étape 4 — DTOs (UserPublicResponse, UserProfileResponse, UpdateUsernameRequest)

- **Commit** : `feat(scrum-169): expose username in user DTOs and add UpdateUsernameRequest`
- **Modifs** : `UserPublicResponse.java`, `UserProfileResponse.java`, `UpdateUsernameRequest.java` (nouveau) + tests DTOs associés.
- **Vérification** : `mvnw -pl services/user-service -am verify` (avec ITs).

### Étape 5 — UserService (updateUsername + getByUsername + existsByUsername)

- **Commit** : `feat(scrum-169): add updateUsername, getByUsername, existsByUsername in UserService`
- **Modifs** : `UserService.java` + constante `RESERVED_USERNAMES`.
- **Vérification** : `mvnw -pl services/user-service -am verify` (avec ITs).
- **Garde-fou** : `updateUsername` doit normaliser `requestedUsername.trim().toLowerCase()` avant la comparaison et le persist ; `getByUsername` doit appliquer la règle anti-oracle 404 strictement comme `getPublicProfile`.

### Étape 6 — UserResource (3 endpoints)

- **Commit** : `feat(scrum-169): add PATCH /me/username, GET/HEAD /by-username/{username} endpoints`
- **Modifs** : `UserResource.java`.
- **Vérification** : `mvnw -pl services/user-service -am verify` ; smoke test manuel avec `quarkus:dev` + `curl` recommandé mais pas bloquant.
- **Garde-fou** : `@PerUserRateLimit(name="users.updateUsername", max=5)` sur le PATCH ; `@PermitAll` sur le GET et le HEAD.

### Étape 7 — Tests backend user-service (UserResourceTest + UserServiceTest + migration)

- **Commit** : `test(scrum-169): cover username endpoints, generation, blocklist and migration backfill`
- **Modifs** : `UserResourceTest`, `UserServiceTest`, `UsernameMigrationBackfillTest` (nouveau).
- **Vérification** : `mvnw -pl services/user-service -am verify` ; jacoco report ≥ 80 % sur nouveau code.

### Étape 8 — Enrichissement DTOs cross-service (engagement-service + event-service)

- **Commit** : `feat(scrum-169): expose username in AttendanceDTO and EventCoOrganizerDTO`
- **Modifs** : section 4.3 + 4.4.
- **Vérification** : `mvnw verify` (reactor complet) → les 15 modules doivent compiler.
- **Garde-fou** : ne pas modifier `EventDTO` lui-même ; vérifier qu'aucun `*Stub.java` n'apparaît (`find backend/services -name '*Stub.java' = ∅`).

### Étape 9 — Frontend types (User + Attendance + CoOrganizer + RESERVED_USERNAMES)

- **Commit** : `feat(scrum-169): make User.username required and add username to Attendance/CoOrganizer types`
- **Modifs** : `frontend/src/types/user.ts`, `attendance.ts`, `coOrganizer.ts`.
- **Vérification** : `cd frontend && npm run lint` — attendre des erreurs `username is missing` sur les fixtures de test : à fixer aux étapes 12-15.
- **Note** : à cette étape, plusieurs tests cassent. C'est attendu — les étapes suivantes les corrigent. Le commit peut être OK si l'erreur est strictement liée à `username` manquant dans les mocks (qui seront mis à jour avec le code consommateur).

### Étape 10 — Frontend service (userService + useDebounce)

- **Commit** : `feat(scrum-169): add username APIs in userService and create useDebounce hook`
- **Modifs** : `userService.ts` (3 nouvelles fonctions), `hooks/useDebounce.ts` (nouveau), + tests.
- **Vérification** : `npm run test src/__tests__/services/userService.test.ts src/__tests__/hooks/useDebounce.test.ts`.

### Étape 11 — Frontend routing

- **Commit** : `refactor(scrum-169): switch profile route from :id to :username`
- **Modifs** : `AppRouter.tsx`.
- **Vérification** : `npm run test src/__tests__/router/AppRouter.test.tsx` + ajustement du test.

### Étape 12 — Frontend ProfilePage

- **Commit** : `refactor(scrum-169): resolve profile by username with transient UUID redirect`
- **Modifs** : `ProfilePage.tsx` (cf. section 4.6 — `useParams<{ username }>`, `isOwnProfile`, redirect permanent UUID → username, lookup `getUserByUsername`).
- **Vérification** : `npm run test src/__tests__/pages/profile/ProfilePage.test.tsx`.

### Étape 13 — Frontend ProfileEditPage (champ username + debounced check)

- **Commit** : `feat(scrum-169): add username field with debounced uniqueness check in ProfileEditPage`
- **Modifs** : `ProfileEditPage.tsx`.
- **Détails UX (à appliquer avec skill `frontend-design`)** :
  - Champ `FormField` "Nom d'utilisateur" **en haut** du form (avant "Nom" / displayName).
  - Helper-text par défaut : *« 3 à 30 caractères, lettres minuscules, chiffres, `.`, `_`, `-` »*.
  - Validation côté client en miroir : `/^[a-z0-9._-]{3,30}$/.test(value)` + check blocklist `RESERVED_USERNAMES.includes(value)`.
  - Debounce 400 ms via `useDebounce`. Pendant `usernameStatus === 'checking'` : icône `Loader2` animée + texte gris *« vérification… »*. État `available` : icône `Check` verte + *« disponible »*. État `taken` : icône `X` rouge + *« déjà pris »*. État `invalid` : *« format invalide »*. État `reserved` : *« nom réservé »*.
  - Skip de l'appel API si la valeur n'a pas changé par rapport à `user.username` initial.
  - Submit principal du form (`handleSubmit`) appelle `updateUsername(value)` séparément si le champ a changé, **avant** `updateProfile` (pour pouvoir afficher l'erreur 409 sans bloquer le reste). Si 409 → focus sur le champ, status `taken`, abort submit. Si succès → continue avec `updateProfile`.
  - Post-succès : `useAuth.updateUser(freshUser)` pour refléter le nouveau username dans tout le UI sans refresh.
- **Vérification** : `npm run test src/__tests__/pages/profile/ProfileEditPage.test.tsx`.

### Étape 14 — Frontend liens internes (4 sites)

- **Commit** : `refactor(scrum-169): build profile links from username instead of UUID`
- **Modifs** : `UserIdentity.tsx`, `EventDetailPage.tsx:521`, `EventOrganizerTeam.tsx:94`, `AttendeeCard.tsx:74`. Drop du `// TODO: SPRINT 5 : Username` sur `UserIdentity.tsx:64`.
- **Vérification** :
  - `grep -rn "/profile/\${.*\.id}" frontend/src/ | grep -v __tests__ | grep -v node_modules` = 0 résultat (modulo le redirect transitoire dans `ProfilePage.tsx`).
  - `grep -rn "TODO: SPRINT.*[Uu]sername" frontend/src/` = 0.
  - `npm run lint`.

### Étape 15 — Frontend `displayName.ts` (fallback chain révisé)

- **Commit** : `refactor(scrum-169): replace UUID prefix with @username in displayName fallback`
- **Modifs** : `frontend/src/utils/displayName.ts` (signature `userDisplayLabel(displayName, username?)`) + tous les call-sites (`CommentItem`, `EventOrganizerTeam.OrganizerRow`, etc.) — `grep -rn "userDisplayLabel" frontend/src/`.
- **Vérification** : `npm run test src/__tests__/utils/displayName.test.ts` + `npm run lint`.

### Étape 16 — Frontend tests qui restaient à mettre à jour

- **Commit** : `test(scrum-169): align profile/attendee/organizer link tests with new username routes`
- **Modifs** : tous les tests listés à la section 4.8 qui n'ont pas été touchés par les étapes 10-15.
- **Vérification** : `npm run test` complet → vert.

### Étape 17 — Documentation backend + frontend + sprint-context

- **Commit** : `docs(scrum-169): update data-model, api-contract, components, types, architecture and sprint-context`
- **Modifs** : sections 4.9 + 4.10.
- **Vérification** : `git diff` cohérent ; pas d'oubli (table routing frontend mise à jour, ligne `username` ajoutée dans `data-model.md User`, etc.).

### Étape 18 — Verification finale + push + PR

- **Pas un commit unique** — c'est une étape de vérification globale (cf. § 7 et § 8).
- **Push** : `git push -u origin feature/scrum-169-profile-username-url`.
- **PR** : `gh pr create --base main --title "feat(scrum-169): replace UUID-based profile URLs with usernames"` avec body issu de `.github/pull_request_template.md` rempli.

---

## 6. Tests

### 6.1 Backend — `UserResourceTest` (extensions, sans nouveau fichier)

| Test (nom suggéré) | Assertion | Lien décision / cas-limite |
|---|---|---|
| `patchUsername_happyPath_returns200WithUpdatedUser` | `PATCH /me/username` avec username valide → 200 + body User contenant le nouveau username | Décision C |
| `patchUsername_conflict_returns409` | Deux users, le 2e tente le username du 1er → 409 envelope `username_taken` | Décision C + cas-limite "race debounce/submit" |
| `patchUsername_invalidPattern_returns400` | `"Jean Dupont"` (espaces, majuscules) → 400 envelope `username_invalid` | Décision F |
| `patchUsername_reservedWord_returns400` | `"admin"` → 400 envelope `username_reserved` | Décision F |
| `patchUsername_unauthenticated_returns401` | Pas de token → 401 | Standard |
| `patchUsername_rateLimitExceeded_returns429` | 6 appels rapides → 429 sur le 6e | `@PerUserRateLimit max=5` |
| `getByUsername_publicProfile_returnsPayload` | Profil public ciblé par username → 200 + UserPublicResponse complet | Décision F |
| `getByUsername_caseInsensitive_findsLowercased` | `GET /by-username/Jean.Dupont` → 200 sur user `jean.dupont` | Décision F |
| `getByUsername_privateProfile_anonymous_returns404` | Profil privé, caller anonyme → 404 anti-oracle | ISSUE-93 / Décision F |
| `getByUsername_privateProfile_otherUser_returns404` | Profil privé, caller authentifié non-owner non-admin → 404 | ISSUE-93 |
| `getByUsername_privateProfile_admin_returnsPayload` | Profil privé, caller admin → 200 | ISSUE-93 admin bypass |
| `getByUsername_anonymous_includesUsername` | Profil public, caller anonyme → payload stripped MAIS `username` présent | Décision E |
| `getByUsername_notFound_returns404` | Username inexistant → 404 | Standard |
| `headByUsername_taken_returns200` | Username pris → 200 (corps vide) | Décision D |
| `headByUsername_available_returns404` | Username libre → 404 | Décision D |
| `headByUsername_caseInsensitive` | `HEAD /by-username/Jean.Dupont` matche `jean.dupont` → 200 | Décision F |

### 6.2 Backend — `UserServiceTest` (extensions, sans nouveau fichier)

| Test | Assertion | Lien |
|---|---|---|
| `updateUsername_happy_persistsAndReturns` | Username valide non pris → row update, `version++`, retour user | Décision C |
| `updateUsername_blocklist_throws400` | `"login"` → BadRequestException `username_reserved` | Décision F |
| `updateUsername_invalidPattern_throws400` | `"ab"` (< 3 chars) → BadRequestException `username_invalid` | Décision F |
| `updateUsername_conflict_throws409` | Username déjà pris par un autre user → WebApplicationException 409 `username_taken` | Décision C |
| `updateUsername_sameValue_isIdempotent` | Set le même username que l'actuel → no-op, pas d'incrément `version` | Optimisation UX (skip si non modifié, mais le service ne peut pas savoir — c'est l'appelant frontend qui skip ; le service accepte ré-affecter la même valeur sans erreur, juste pas de change DB perçu si on compare avant/après) |
| `getByUsername_normalizesLowercase` | Passer `"Jean.Dupont"` → SELECT sur `jean.dupont` | Décision F |
| `getByUsername_followStatusComputed` | Caller authentifié → `followStatus` peuplé selon la row `Follow` | Pattern existant SCRUM-138 |
| `getByUsername_privateProfile_nonOwner_throwsNotFound` | Idem `getPublicProfile` ; anti-oracle ISSUE-93 | ISSUE-93 |
| `getByUsername_privateProfile_admin_bypasses` | Idem `getPublicProfile` admin bypass | REST-003 |

### 6.3 Backend — `UsernameMigrationBackfillTest` (nouveau fichier)

Approche : `@QuarkusTest` avec `@TestProfile` qui ré-active Flyway en `%test` (`quarkus.flyway.enabled=true` + ré-attache la datasource preview Flyway-on). Pré-charge la table `users` (via `EntityManager.createNativeQuery("INSERT ... ")` ou Panache) **avant** Flyway, puis force `Flyway.migrate()` manuellement.

| Test | Assertion |
|---|---|
| `backfill_displayName_simple` | User `displayName="Jean Dupont"` → username `jean.dupont` |
| `backfill_displayName_withAccents` | User `displayName="François Müller"` → username `francois.muller` (ASCII fold via `unaccent`) |
| `backfill_displayName_empty_firstLastFallback` | User `displayName=""`, `firstName="Marie"`, `lastName="Curie"` → username `marie.curie` |
| `backfill_allEmpty_userFallback` | User avec tous les champs nuls/vides → username `user` (ou `user2`/`user3` si déjà pris) |
| `backfill_collision_numericSuffix` | 5 users `displayName="Jean Dupont"` consécutifs → `jean.dupont`, `jean.dupont2`, `jean.dupont3`, `jean.dupont4`, `jean.dupont5` |
| `backfill_blocklist_avoided` | User `displayName="Admin"` → username `admin2` (pas `admin`) |
| `backfill_resultMatchesPattern` | Tous les usernames générés respectent `^[a-z0-9._-]{3,30}$` |
| `backfill_resultIsUnique` | `SELECT COUNT(DISTINCT username) FROM users == COUNT(*)` |

### 6.4 Frontend — `userService.test.ts`

| Test | Assertion |
|---|---|
| `getUserByUsername_callsCorrectURL` | `getUserByUsername("jean.dupont")` → `GET /users/by-username/jean.dupont` |
| `getUserByUsername_returnsUserOn200` | Mock 200 → retourne le `User` désérialisé |
| `getUserByUsername_returnsNullOn404` | Mock 404 → retourne `null` (pas d'exception) |
| `updateUsername_callsPATCH` | `updateUsername("new.name")` → `PATCH /users/me/username` body `{username:"new.name"}` |
| `updateUsername_returnsUpdatedUser` | Mock 200 + body User → retourne le User |
| `updateUsername_throwsOn409` | Mock 409 → throw avec message exploitable par UI |
| `checkUsernameAvailable_returnsTrueOn404` | Mock 404 → `true` (disponible) — **inversion sémantique documentée** |
| `checkUsernameAvailable_returnsFalseOn200` | Mock 200 → `false` (pris) |

### 6.5 Frontend — `ProfilePage.test.tsx`

| Test | Assertion |
|---|---|
| `lookupByUsername` | Navigation `/profile/jean.dupont` → appelle `getUserByUsername("jean.dupont")` |
| `aliasMe` | Navigation `/profile/me` → utilise `currentUser` sans appel API |
| `redirectUuidV4ToUsername` | Navigation `/profile/19f3ab78-0fbf-4cfb-896e-5c0346fabed5` → call `getUserById`, `<Navigate to="/profile/jean.dupont" replace />` (cf. Décision I) |
| `redirectUuidNotFound` | UUID introuvable → InfoMessage "Profil introuvable" (pas de boucle) |
| `usernameNotFound` | `getUserByUsername` → null → "Profil introuvable" |
| `privateProfile_otherUser_showsLocked` | Lookup OK mais `profilePublic=false` et `username !== currentUser.username` → écran cadenas |

### 6.6 Frontend — `ProfileEditPage.test.tsx`

| Test | Assertion |
|---|---|
| `usernameFieldPrefilled` | Mount avec `user.username = "jean.dupont"` → champ valeur `"jean.dupont"` |
| `clientValidation_invalidPattern_showsError` | Taper `"Jean"` (majuscule) → status `invalid` |
| `clientValidation_reserved_showsError` | Taper `"admin"` → status `reserved` |
| `debouncedCheck_available` | Taper `"new.name"`, attendre 400 ms (`vi.useFakeTimers`) → `checkUsernameAvailable` appelé une seule fois, status `available` |
| `debouncedCheck_taken` | Idem mais mock retourne `false` → status `taken` |
| `submitSuccess_callsUpdateUsername` | Modifier le username, submit → `updateUsername` appelé avec la nouvelle valeur |
| `submitSuccess_skipsUpdateIfUnchanged` | Submit sans toucher au champ → `updateUsername` **non** appelé |
| `submit409_handlesWithoutBlocking` | `updateUsername` rejette 409 → toast erreur, focus sur le champ, le reste du form (`updateProfile`) **n'est pas** appelé |
| `submitSuccess_refreshesAuthUser` | Après succès → `useAuth.updateUser(freshUser)` appelé avec le user à jour |

### 6.7 Frontend — `AppRouter.test.tsx`

| Test | Assertion |
|---|---|
| `routeProfileUsername` | `/profile/jean.dupont` → render ProfilePage |
| `routeProfileMe` | `/profile/me` → render ProfilePage (cas alias) |
| `routeProfileMeEdit` | `/profile/me/edit` → render ProfileEditPage |
| `routeProfileRoot` | `/profile` → redirect vers `/profile/me` |

### 6.8 Frontend — `useDebounce.test.ts`

| Test | Assertion |
|---|---|
| `returnsInitialValueImmediately` | `useDebounce("a", 300)` à `t=0` → `"a"` |
| `debouncesValueAfterDelay` | Changement à `t=10`, lecture à `t=200` → ancienne valeur ; lecture à `t=350` → nouvelle valeur |
| `cleansUpOnUnmount` | Unmount avant le timer → pas de `setState` après unmount (vi.useFakeTimers, vérifier pas de warning React) |

### 6.9 Frontend — `displayName.test.ts`

| Test | Assertion |
|---|---|
| `prefersTrimmedDisplayName` | `userDisplayLabel(" Jean ", "jean.dupont")` → `"Jean"` |
| `fallsBackToAtUsername` | `userDisplayLabel(null, "jean.dupont")` → `"@jean.dupont"` |
| `fallsBackToLiteralWhenBoth` | `userDisplayLabel(null, undefined)` → `"Utilisateur"` |

### 6.10 Cas-limites explicites (déjà couverts par les tableaux ci-dessus)

- Username pris au moment du PATCH (race debounce/submit) — § 6.6 `submit409_handlesWithoutBlocking` + § 6.1 `patchUsername_conflict_returns409`.
- Username modifié pendant navigation → 404 → "Profil introuvable" — § 6.5 `usernameNotFound`.
- Migration sur user avec tous les champs vides → `user` + suffixe — § 6.3 `backfill_allEmpty_userFallback`.
- Username avec accents → ASCII fold → `francois.muller` — § 6.3 `backfill_displayName_withAccents`.
- Blocklist en update + en back-fill — § 6.1 `patchUsername_reservedWord_returns400` + § 6.3 `backfill_blocklist_avoided`.
- Pattern non respecté → 400 — § 6.1 `patchUsername_invalidPattern_returns400`.
- Case-sensitivity → § 6.1 `getByUsername_caseInsensitive_findsLowercased` + `headByUsername_caseInsensitive`.
- Anonyme → `username` inclus dans payload stripped — § 6.1 `getByUsername_anonymous_includesUsername`.

---

## 7. Critères de done (checklist à exécuter avant `gh pr create`)

Exécuter **toutes** les commandes ci-dessous **dans l'ordre** et confirmer chaque ligne :

- [ ] `cd backend && ./mvnw verify` — reactor complet 15 modules, SUCCESS.
- [ ] `cd backend && ./mvnw -pl services/user-service -am verify` — focus user-service avec ITs, SUCCESS.
- [ ] `cd frontend && npm run lint` — 0 erreur.
- [ ] `cd frontend && npm run test` — couverture maintenue, 100 % tests verts.
- [ ] `npx --yes @redocly/cli@latest lint openapi/openapi.yaml` — 0 erreur sur le diff.
- [ ] `grep -rn "/profile/\${.*\.id}" frontend/src/ | grep -v __tests__ | grep -v node_modules` = 0 résultat (modulo redirect transitoire `ProfilePage.tsx`).
- [ ] `grep -rn "TODO: SPRINT.*[Uu]sername" frontend/src/` = 0.
- [ ] `find backend/services -name '*Stub.java'` = 0 (invariant projet).
- [ ] `ls backend/services/user-service/src/main/resources/db/migration` montre V1, V2, V3 ; aucun V3 conflictuel d'une autre PR.
- [ ] `git diff origin/main HEAD -- openapi/openapi.yaml` non-vide (cf. § 4.1) et cohérent.
- [ ] `git diff` sur la doc cohérent : `data-model.md` (User), `api-contract.md` (3 endpoints), `components.md` + `types.md` + `architecture.md` (route + service + types), `sprint-context.md` (les deux, section datée).
- [ ] `git status` propre, branche poussée, pas de `.env`/`devcontainer-lock.json` mégarde.
- [ ] PR ouverte avec titre `feat(scrum-169): replace UUID-based profile URLs with usernames`, body template GitHub rempli, base = `main`, head = `feature/scrum-169-profile-username-url`.
- [ ] **Pas de merge** par l'agent. Elie merge lui-même.
- [ ] Boucle review Copilot itérée jusqu'à 0 BLOQUANT / 0 IMPORTANT non-clos.

---

## 8. Workflow Git (rappel concis)

- **Branche** : `feature/scrum-169-profile-username-url`, base = tip `feature/scrum-137-146-doc-and-views` (Décision A).
- **1 commit par étape** du Plan d'exécution (§ 5).
- **Format de message** : `<type>(scrum-169): <description courte>`. Types autorisés : `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `perf`. Scope obligatoire `scrum-169` pour `feat`/`refactor`/`perf` (cf. `AGENTS.md` racine).
- **Co-author Claude** sur chaque commit (cf. instructions racine — HEREDOC standard).
- **Push** : `git push -u origin feature/scrum-169-profile-username-url`.
- **PR** : `gh pr create --base main --title "..." --body "$(cat <<'EOF' ... EOF)"`.
- **Si PR #170 merge avant SCRUM-169** : `git fetch origin main && git rebase origin/main`. Documenter le rebase dans `sprint-context.md` (note 2026-05-14 suite). Aucun destructif sans confirmation.
- **Pas de merge** de la PR par l'agent. Elie merge lui-même.

---

## 9. Garde-fous

- **Aucune action destructive** : pas de `rm -rf`, `git reset --hard`, `git checkout -- .`, `--no-verify`, force-push sur `main` ou sur la branche cible #170. Si conflit / état incompréhensible → demander à Elie avant d'agir.
- **Immutabilité Flyway** : V3 ne se modifie plus une fois pushée. Toute correction passe par un V4 additif. La leçon préventive de [`specs_scrum-139.md` note Flyway-immutabilité](specs_archives/specs_claude/specs_scrum-139.md) s'applique mot pour mot.
- **Pas de `GET /users/search`** (out-of-scope, follow-up S9+).
- **Pas de modification de `EventDTO`** (event-service) — uniquement `AttendanceDTO` (engagement-service) et `EventCoOrganizerDTO` (event-service) — cf. Décision K.
- **Pas de stub JPA cross-service** : `find backend/services -name '*Stub.java' = ∅`. Les calls cross-service passent par REST clients existants.
- **Pas de touche à `SchemaFixup.java`** (n'existe pas).
- **Cohérence doc / code** : si la doc dérive du code pendant l'implémentation → fix dans le même commit (règle d'or projet).
- **Pré-push** : pas de `.env`, `.devcontainer/devcontainer-lock.json`, fichiers temporaires (`*.log`, `target/`, `node_modules/`) committés.
- **Avant chaque étape** : `ls backend/services/user-service/src/main/resources/db/migration` pour confirmer que la V3 reste libre.
- **Si un cas non couvert par la spec émerge** : documenter dans `sprint-context.md` (section finale datée) et continuer ; pas de micro-arbitrage interruptif. Si le cas dépasse les Décisions A-K (changement de contrat, scope élargi), s'arrêter et demander.

---

## 10. Skills à utiliser

| Skill | Quand |
|---|---|
| `superpowers:executing-plans` | Pour itérer méthodiquement étape par étape du § 5 |
| `superpowers:test-driven-development` | Pour chaque endpoint backend (étapes 5-7) et pour le champ username de ProfileEditPage (étape 13) |
| `superpowers:systematic-debugging` | Si un test casse de manière inattendue, notamment la migration PL/pgSQL (étape 2 + 7) |
| `superpowers:verification-before-completion` | **Obligatoire** avant chaque claim "done" et avant `gh pr create` (étape 18) — exécuter toutes les commandes du § 7 |
| `frontend-design` | Pour le champ "Nom d'utilisateur" de ProfileEditPage (étape 13) — feedback inline ✅/❌/⏳, accessibilité, micro-interactions debounce 400 ms |
| `code-simplifier` | Après les étapes 5 (`UserService`) et 13 (`ProfileEditPage`) — refactor lisibilité, dédup |
| `context7` | Si besoin de docs lib externes (`java.text.Normalizer` semantics, `react-router-dom` v6 `<Navigate replace>`, `vitest` `useFakeTimers` pour le debounce) |
| `superpowers:requesting-code-review` + `pr-review-toolkit:review-pr` | Une fois la PR ouverte, lancer la boucle Copilot |
| `superpowers:receiving-code-review` | Pour traiter les retours Copilot avec rigueur (étape boucle post-PR) |
| `superpowers:finishing-a-development-branch` | Pour décider du moment exact de push + ouverture PR |
| `github` MCP | `gh pr create`, `gh pr checks <PR#> --watch`, `gh api .../pulls/<PR#>/comments` |
| `claude-md-management:revise-claude-md` | Optionnel — uniquement si une leçon de session vaut la peine d'être conservée |

---

## Launch prompt (literal, à copier-coller pour lancer l'implémentation)

````markdown
Implémente SCRUM-169 en autonomie complète selon la spec
`specs_archives/specs_claude/specs_scrum-169.md`.

Étapes :

1. Lis la spec en entier avant de toucher au moindre fichier. Internalise
   les Décisions techniques A → K et le Plan d'exécution séquentiel
   (sections 3 et 5).
2. Lis `AGENTS.md` (racine + `backend/AGENTS.md` + `frontend/AGENTS.md`)
   pour les conventions de commit, scope, doc à toucher.
3. Crée la branche `feature/scrum-169-profile-username-url` à partir de la
   tip de `feature/scrum-137-146-doc-and-views` (Décision A). Vérifie via
   `gh pr view 170` que PR #170 est encore OUVERTE ; si elle vient de
   merger, `git fetch origin main && git rebase origin/main` (jamais de
   destructifs sans confirmation).
4. Avant chaque étape touchant la migration : `ls
   backend/services/user-service/src/main/resources/db/migration` pour
   confirmer qu'aucune autre PR n'a pris ton numéro V3 — sinon bumper en
   V4 et adapter toutes les références.
5. Exécute chaque étape du Plan dans l'ordre exact (§ 5, étapes 1-17).
   Un commit par étape, format `<type>(scrum-169): <description>` avec
   co-author Claude. Vérifie après chaque commit avec la commande
   indiquée (`mvnw -pl services/user-service -am verify` ou `npm run lint
   && npm run test`).
6. Étape 13 (champ username ProfileEditPage) : applique le skill
   `frontend-design` — feedback inline ✅/❌/⏳, accessibilité,
   micro-interactions debounce 400 ms (cf. spec § 5 étape 13 détails UX).
7. Avant `gh pr create` (étape 18) : `superpowers:verification-before-
   completion` non négociable — exécute toutes les commandes de la section
   7 (Critères de done) et confirme chaque ligne. Aucun claim "done"
   sans cette verification.
8. Ouvre la PR via `gh pr create --base main --title "feat(scrum-169):
   replace UUID-based profile URLs with usernames"` + body issu du
   template `.github/pull_request_template.md` (sections Résumé,
   Changements, Tests, Test plan, Documentation obligatoires). **Pas de
   merge.**
9. Lance la boucle review Copilot : `gh pr checks <PR#> --watch`, puis
   `gh api repos/unige-pinfo6-2026/unige-events/pulls/<PR#>/comments` pour
   chaque retour. Applique `superpowers:receiving-code-review`, fixe,
   commit (`fix(scrum-169): apply Copilot review — <résumé court>`),
   push, re-check. Itère jusqu'à 0 BLOQUANT / 0 IMPORTANT non-clos.
10. Quand tous les checks sont verts et la review propre : signale-moi
    avec le lien de la PR et un résumé des commits livrés. **Je merge
    moi-même.**

Garde-fous (rappel) :
- Aucune action destructive sans confirmation explicite.
- Aucune modification d'une migration committée (V3 immutable après push).
- Aucun stub JPA cross-service.
- Pas de touche à `EventDTO` event-service ni à `SchemaFixup.java` (n'existe pas).
- Si la doc dérive du code → fix dans le même commit.
- Si un cas non couvert par la spec émerge : documente-le dans
  `sprint-context.md` (section datée finale) et continue ; ne me réveille
  pas pour des micro-arbitrages.
````
