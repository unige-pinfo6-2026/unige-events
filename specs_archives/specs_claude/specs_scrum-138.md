# Specs SCRUM-138 — Entité `Follow` + endpoints follow / unfollow / demandes / listes

> **Branche :** `feature/s6-follow` (nom historique du backlog ; conservé pour la traçabilité — cf. décision 1)
> **Base :** `origin/main` (tip à la date de rédaction : `cb10e29 style(scrum-97): right-align processed-report status badge in actions column`)
> **Sprint :** S6 (calendrier produit) — ticket marqué `[BACK][S8]` dans le titre, statut Jira « À faire »
> **Ticket Jira :** [SCRUM-138](https://pinfo-groupe6.atlassian.net/browse/SCRUM-138) (5 SP)
> **Story Points :** 5
> **Épic :** SCRUM-13 (Profils utilisateurs et social) · **Stories :** [SCRUM-109](https://pinfo-groupe6.atlassian.net/browse/SCRUM-109) (US-20) + [SCRUM-110](https://pinfo-groupe6.atlassian.net/browse/SCRUM-110) (US-21)
> **Frontend lié (consommateurs aval) :** SCRUM-141 (page profil public), SCRUM-142 (FollowButton + panneau demandes), SCRUM-143 (modale listes followers/following) — Sprint S7. Le contrat OpenAPI livré ici est figé pour ces tickets.
> **Notifications :** SCRUM-140 (`NEW_FOLLOWER`, `FOLLOW_REQUEST`, `FOLLOW_ACCEPTED`) — Sprint S7. **Hors scope** SCRUM-138 (cf. décision 19).
> **Filtre `followedOnly` sur `GET /events` :** SCRUM-168 — Sprint S9. Hors scope SCRUM-138, **mais** le finder statique `Follow.findAcceptedFollowedIds(UUID)` est livré ici (cf. décision 20).
> **Dépendances amont :** Aucune.
> **Règle d'or `openapi-first` :** **APPLICABLE — 7 nouveaux endpoints + 3 schémas neufs (`FollowStatus`, `FollowDTO`, `FollowRequestDTO`) + enrichissement de `UserPublicResponse`.** Modifier [`openapi/openapi.yaml`](openapi/openapi.yaml) AVANT toute ligne de code Java. Voir [`backend/AGENTS.md`](backend/AGENTS.md#L77-L80).

---

## Contexte

### Le besoin produit (US-20 + US-21)

> *« En tant qu'utilisateur, je veux consulter le profil public d'un autre utilisateur, afin de découvrir ses événements et son activité. »* — US-20 (SCRUM-109)
> *« En tant qu'utilisateur, je veux suivre d'autres utilisateurs (ou envoyer une demande de suivi sur un profil privé), afin de rester informé de leur activité. »* — US-21 (SCRUM-110)

L'épic SCRUM-13 vise à transformer la plateforme d'un catalogue d'événements en un mini-réseau social autour des organisateurs : un utilisateur qui aime ce que poste « Comité Sciences PInfo » doit pouvoir s'abonner pour suivre ses futurs événements. SCRUM-138 livre **le socle backend de la relation de suivi** : entité, endpoints, enrichissement du profil public avec les compteurs et l'état de la relation depuis le point de vue de l'appelant.

### Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| Aucune entité représentant la relation `(follower, followed)` | Impossible de stocker un suivi |
| Aucun endpoint `POST /api/users/{id}/follow` | Le frontend (SCRUM-142) ne peut pas afficher de bouton « Suivre » fonctionnel |
| `UserPublicResponse` n'expose ni `followerCount`, ni `followingCount`, ni `followStatus` | La page profil (SCRUM-141) ne peut afficher ni compteurs, ni état du bouton « Suivre / Demande envoyée / Abonné » |
| Aucun mécanisme de demande PENDING → ACCEPTED pour profils privés | US-21 (« envoyer une demande sur profil privé ») impossible à livrer |
| Aucune liste `/api/users/{id}/followers` ni `/following` | SCRUM-143 (modales listes) bloqué |
| Aucun finder `findAcceptedFollowedIds(UUID)` | SCRUM-168 (filtre `followedOnly` sur le feed, S9) bloqué |
| Aucun schéma OpenAPI `Follow*` | Le contrat consommé par SCRUM-141/142/143 (S7) n'est pas figé |

### Ce qui existe déjà à RÉUTILISER tel quel (ne pas recréer)

| Élément | Fichier / ligne | Rôle dans SCRUM-138 |
|---|---|---|
| Entité `User` (PanacheEntityBase) avec `id: UUID`, `auth0Id`, `profilePublic: boolean` | [`User.java`](backend/src/main/java/ch/unige/events/entity/User.java) | Source de vérité — Follow référence `User.id` (UUID) |
| `User.findByAuth0Id(String)` | [`User.java:53-55`](backend/src/main/java/ch/unige/events/entity/User.java#L53-L55) | Résolution `auth0Id → User` côté `FollowService` |
| Pattern PanacheEntity « table de jointure UUID/Long » : `Favorite` + `V4__create_favorites.sql` | [`Favorite.java`](backend/src/main/java/ch/unige/events/entity/Favorite.java) + [`V4__create_favorites.sql`](backend/src/main/resources/db/migration/V4__create_favorites.sql) | **Modèle direct** pour `Follow` (PK `Long` via `PanacheEntity`, deux UUIDs bruts, contrainte unique sur le couple, `@PrePersist createdAt`, finders statiques par champ) |
| `FavoriteService.resolveUserId(auth0Id)` | [`FavoriteService.java:75-80`](backend/src/main/java/ch/unige/events/service/FavoriteService.java#L75-L80) | Pattern de résolution `auth0Id → UUID` à dupliquer dans `FollowService` |
| Pattern pagination `@DefaultValue("0") @Min(0) page`, `@DefaultValue("20") @Positive @Max(100) size` | [`UserResource.java:283-291`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L283-L291) + [`UserResource.java:343-351`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L343-L351) | À dupliquer pour `GET /api/users/{id}/followers`, `/following` |
| Règle anti-oracle 404 sur profil privé | [`UserService.java:78-90`](backend/src/main/java/ch/unige/events/service/UserService.java#L78-L90) | À aligner pour le listing followers/following d'un profil privé non-owner (cf. décision 10) |
| Pattern factory `from(User)` + `fromAnonymous(User)` | [`UserPublicResponse.java`](backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java) | À étendre — nouvelle factory enrichie + politique anonyme à trancher (décision 13) |
| Helpers d'erreur `badRequest`/`conflict`/`unprocessable` (WebApplicationException + ApiErrorResponse) | [`EventCoOrganizerService.java:158-180`](backend/src/main/java/ch/unige/events/service/EventCoOrganizerService.java#L158-L180) | Pattern à dupliquer dans `FollowService` |
| Pattern `@Authenticated` + `identity.getPrincipal().getName()` | [`UserResource.java:120-124`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L120-L124) | Pattern d'auth standard |
| `@PerUserRateLimit(name=…, max=…)` | [`PerUserRateLimit.java`](backend/src/main/java/ch/unige/events/config/PerUserRateLimit.java) | Annotation à apposer sur `POST /api/users/{id}/follow` (cf. décision 22) |
| `ApiErrorResponse` record | [`ApiErrorResponse.java`](backend/src/main/java/ch/unige/events/dto/ApiErrorResponse.java) | Envelope d'erreur standard |
| Pattern test `@Mock @ApplicationScoped extends Service` + `volatile boolean force*` + `reset()` | [`FavoriteServiceMock.java`](backend/src/test/java/ch/unige/events/service/FavoriteServiceMock.java) | **Modèle direct** pour `FollowServiceMock` |
| Pattern `@QuarkusTest` + `@TestSecurity(user="auth0\|alice")` + RestAssured | [`FavoriteResourceTest.java`](backend/src/test/java/ch/unige/events/resource/FavoriteResourceTest.java) | Pour `FollowResourceTest` |
| Mappers d'exception standards | [`backend/src/main/java/ch/unige/events/exception/mapper/`](backend/src/main/java/ch/unige/events/exception/mapper/) | `NotFoundExceptionMapper`, `ConflictExceptionMapper`, etc. — à réutiliser |

### Pourquoi maintenant

- Sprint S6 — sprint courant, ticket assigné à Elie sur le board Jira, statut « À faire » prêt à passer en cours.
- **Aucune dépendance amont** : pas de migration ouverte, pas de refactor en vol qui toucherait `User`, `UserPublicResponse` ou `UserResource`.
- Débloque immédiatement **trois tickets frontend S7** (SCRUM-141 page profil, SCRUM-142 bouton, SCRUM-143 listes) qui ne peuvent pas commencer sans le contrat `Follow*` figé.
- Anticipe le ticket S9 SCRUM-168 (filtre `followedOnly`) en livrant le finder dédié `Follow.findAcceptedFollowedIds(UUID)` dès maintenant — l'éventuelle dette technique (introduire le finder en S9 « at the last minute ») est ainsi évitée.
- La règle anti-oracle 404 (ISSUE-93, mergée) est **déjà en place** sur `GET /users/{id}` — la cohérence à appliquer aux listes `/followers`/`/following` est triviale, pas un refactor.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Branche `feature/s6-follow` — pas `feature/SCRUM-138-follow`

**Décision.** La branche s'appelle `feature/s6-follow`, conformément au nom suggéré dans [`backlog_s5_s10.md` ligne 1103](backend/docs/backlog_s5_s10.md#L1103). Le ticket porte le préfixe `[BACK][S8]` mais le backlog le rattache au sprint S6 (artefact historique : reporté de S6 à un sprint ultérieur).

**Justification.** Cohérence intra-projet avec [`specs_scrum-94.md` décision 1](specs_archives/specs_claude/specs_scrum-94.md) (`feature/s6-report-moderation`) et [`specs_scrum-136.md` décision 1](specs_archives/specs_claude/specs_scrum-136.md) (`feature/s6-co-organizers`). Toutes les branches « historiquement S6 » du backlog suivent le même préfixe. La règle racine `AGENTS.md` autorise le format `feature/SCRUM-XX-description` mais les specs récentes du repo retiennent l'alias backlog quand il existe — pour la traçabilité review/merge.

### 2. UUID brut pour `followerId` / `followedId` — pas `@ManyToOne User`

**Décision.** L'entité `Follow` stocke `followerId: UUID` et `followedId: UUID` directement, **sans relation `@ManyToOne User`** ni `@JoinColumn`. Le backlog est explicite ([ligne 1080-1081](backend/docs/backlog_s5_s10.md#L1080-L1081)) : « `followerId` (UUID, @Column nullable=false) ` ».

**Justification.** Trois options pesées :

| Option | Conséquence | Verdict |
|---|---|---|
| (a) `followerId: UUID` + `followedId: UUID` (UUIDs bruts) | Cohérent avec `Favorite.userId`, `Attendance.userId`, `EventCoOrganizer.userId`, `EventView.userId` (le pattern existant dans tout le code-base). Pas de cascade FK : un compte supprimé laissera des rows orphelines à nettoyer côté job de cleanup. | ✅ retenu |
| (b) `@ManyToOne(LAZY) User follower` + `@ManyToOne(LAZY) User followed` | Cohérent avec `Report.reporter`, `Event.creator` mais incohérent avec les 4 tables de jointure ci-dessus. Force des fetch lazy avec risque de N+1 dans les listings. | ❌ |
| (c) Mixte : un côté UUID, l'autre `@ManyToOne` | Asymétrie sémantiquement injustifiable. | ❌ |

L'option (a) suit le pattern **table de jointure** déjà adopté à 4 endroits du projet. La FK est portée côté DB par la migration (`fk_follows_follower`, `fk_follows_followed`) — sans cascade, pour préserver la trace en cas de soft-delete futur d'un compte (pattern défensif assumé, identique à `Report.reporter` nullable).

### 3. PK `Follow.id` → `Long` via `PanacheEntity`

**Décision.** `public class Follow extends PanacheEntity` — PK `Long` séquentielle gérée par `follows_seq` (sequence Hibernate par défaut, increment 50).

**Justification.** Cohérent avec `Favorite`, `Attendance`, `Report`, `EventCoOrganizer`, `EventView` (tous PanacheEntity Long). Une PK UUID ne servirait à rien (la clef logique est le couple `(followerId, followedId)`). Le `Long` est sérialisé en JSON sous forme entière — utilisé par les endpoints `PATCH /api/follow-requests/{followId}/accept|reject` qui prennent ce `id` en paramètre.

### 4. Numérotation Flyway → `V14__create_follows.sql`

**Décision.** Nouveau fichier `backend/src/main/resources/db/migration/V14__create_follows.sql`. Le dernier migrant existant en `main` au moment de la rédaction est `V13__allow_event_status_banned.sql`.

**Justification.** Une migration committée est immutable ([`backend/AGENTS.md` lignes 54-57](backend/AGENTS.md#L54-L57)). **Avant** de coder le SQL : exécuter `ls backend/src/main/resources/db/migration | sort` une dernière fois pour s'assurer qu'aucune autre PR (ex. SCRUM-139 Comments) n'a mergé un V14 entre-temps. Si V14 est pris, basculer en V15 et adapter toutes les références `V14` de cette spec.

### 5. `FollowStatus` — 2 valeurs (`PENDING`, `ACCEPTED`) — **pas** de `REJECTED` stocké

**Décision.** L'enum a deux valeurs strictes : `PENDING`, `ACCEPTED`. Un refus de demande (`PATCH /api/follow-requests/{id}/reject`) **supprime physiquement la row** (pattern `EventCoOrganizer.DECLINE` documenté dans [`data-model.md`](backend/docs/data-model.md#L192-L201)). Une re-tentative de follow après reject repart à zéro (PENDING ou ACCEPTED selon `profilePublic` à ce moment-là).

**Justification.** Trois options pesées :

| Option | Verdict |
|---|---|
| (a) 3 valeurs `PENDING / ACCEPTED / REJECTED` | Garderait une trace mais cumule les rows REJECTED qui pourraient bloquer une re-tentative future (contrainte unique). Aucun consommateur frontend planifié n'a besoin de l'état REJECTED. |
| (b) **2 valeurs `PENDING / ACCEPTED`, reject = DELETE** | Aligné avec `EventCoOrganizer.decline`. Re-follow naturellement possible. Pas de pollution de la table. ✅ retenu |
| (c) Soft-delete `deletedAt` | Sur-ingénierie pour un MVP — pas de besoin métier d'historique. |

### 6. Self-follow → `422 cannot_follow_self`

**Décision.** Si `follower.id == followed.id`, la création est rejetée avec **`422 Unprocessable Entity`** + envelope `{ "error": "cannot_follow_self", "message": "..." }`. Pas de check applicatif ; juste check côté service avant le `persist`.

**Justification.** Pattern strictement aligné sur SCRUM-94 décision 10 (`cannot_report_own_event`) et SCRUM-136 (`cannot_invite_self`). RFC 4918 : *422 = la requête est syntaxiquement valide mais sémantiquement incorrecte*. Le body est bien formé (`followedId` est un UUID valide), mais se suivre soi-même n'a pas de sens métier — `422` est le code dédié à cette classe d'erreur. `400` serait moins précis (*bad request* impliquant un défaut de syntaxe), `409` serait inadapté (pas de conflit de ressource).

### 7. Doublon → `409 already_following` (check préalable + filet de sécurité unique constraint)

**Décision.** Le service détecte le doublon via un `Follow.find("followerId = ?1 AND followedId = ?2", ...)` préalable au `persist` ; **puis** la contrainte unique `uq_follow_follower_followed` (V14) protège du race condition concurrent. Envelope `409 already_following`.

**Justification.** Strictement aligné sur SCRUM-94 décision 15 (`already_reported`) et le fix de race documenté pour `EventCoOrganizer`. Le check applicatif produit l'envelope custom lisible par le frontend (SCRUM-142 affichera un toast spécifique). Le filet unique-constraint protège de deux POST simultanés du même couple — Hibernate jette une `PersistenceException` mappée en 409 par le mapper standard ; au pire, l'envelope est moins jolie sur ce cas extrême-rare (acceptable).

### 8. Cancel d'une demande PENDING par le follower → `DELETE /api/users/{id}/follow` est accepté et idempotent

**Décision.** `DELETE /api/users/{id}/follow` supprime la row `Follow` (followerId = caller, followedId = `{id}`), peu importe son `status` (`PENDING` **ou** `ACCEPTED`). Idempotent : si la row n'existe pas, retourne `204 No Content` sans erreur (pas de 404).

**Justification.** Cohérent avec deux patterns existants :
- `Favorite.removeFavorite` ([`FavoriteService.java:46-54`](backend/src/main/java/ch/unige/events/service/FavoriteService.java#L46-L54)) lève `404 Favorite not found` si la row est absente — **mais** SCRUM-138 préfère le pattern `EventCoOrganizer.DELETE` ([`api-contract.md` ligne 35](backend/docs/api-contract.md#L35)) qui est strictement idempotent (204 même si rien à supprimer). Justification : le bouton frontend (SCRUM-142) « Annuler la demande » ne doit pas exposer un 404 visible quand le user a refresh entre temps.
- Couvre les deux cas d'usage UI : **(a)** annuler une demande PENDING (« je me suis trompé »), **(b)** se désabonner après un follow ACCEPTED. Une seule sémantique côté API.

### 9. Bascule `profilePublic` privé → public : auto-accept des PENDING ? **Hors scope**

**Décision.** Quand `User.profilePublic` passe de `false` à `true` via `PUT /api/users/me`, les rows `Follow` PENDING dont `followedId = me` **ne sont PAS auto-acceptées**. Elles restent PENDING jusqu'à action explicite via `PATCH /api/follow-requests/{id}/accept`.

**Justification.** Comportement le plus défensif côté privacy : un user qui ouvre temporairement son profil ne « libère » pas implicitement toutes les demandes accumulées en privé. Si un besoin produit émerge (« mass-accept au passage public »), il sera implémenté dans un follow-up dédié. **Aucune modification de `UserService.updateMyProfile`** dans cette PR.

### 10. Visibilité des listes `/followers` / `/following` — `@Authenticated`, anti-oracle 404 sur profil privé non-owner

**Décision.** Les endpoints `GET /api/users/{id}/followers` et `GET /api/users/{id}/following` sont `@Authenticated` (token requis). Sur un profil cible :

| Caller | `profilePublic` cible | Réponse |
|---|---|---|
| Anon (pas de token) | n'importe | `401 Unauthorized` |
| Auth, target = caller | `false` ou `true` | `200 OK` + liste paginée |
| Auth, target ≠ caller, `profilePublic = true` | `true` | `200 OK` + liste paginée |
| Auth, target ≠ caller, `profilePublic = false` | `false` | `404 not_found` (anti-oracle, envelope identique à UUID inexistant) |
| Auth, UUID inexistant | n/a | `404 not_found` |

**Justification.** Quatre options pesées :

| Option | Verdict |
|---|---|
| (a) `@PermitAll` (anon ok pour profils publics) | Permet le harvest anonyme massif des relations sociales (« qui suit qui ») via `creatorId` énuméré sur `GET /events`. Aggrave le finding 4.1b (GDPR) que ISSUE-93 vient de mitiger. |
| (b) **`@Authenticated` + 404 anti-oracle pour profil privé non-owner** | Cohérent avec ISSUE-93 décision 1 (privacy par défaut côté listes sociales). Ne casse pas l'UX : SCRUM-141/142 sont déjà sous `PrivateRoute` côté frontend. ✅ retenu |
| (c) `@Authenticated` + `403 forbidden` sur profil privé non-owner | Ré-ouvrirait l'oracle d'existence (le 403 confirme que l'UUID existe). |
| (d) `@Authenticated` + payload réduit (juste un compte) | Sur-ingénierie ; rajoute un nouveau cas de figure pour l'UI sans valeur ajoutée. |

L'option (b) garde la cohérence stricte avec les invariants posés par ISSUE-93 sur `GET /users/{id}` : *« un profil privé est invisible aux non-owners, point »*. La règle est appliquée dans `FollowService.getFollowers`/`getFollowing` (cf. décision 14).

### 11. Shape des listes followers/following → `List<UserPublicResponse>` (lookup bulk, pas de wrapper)

**Décision.** `GET /api/users/{id}/followers` et `GET /api/users/{id}/following` retournent un **`List<UserPublicResponse>`** plat (pas de wrapper `{ items, total }`).

- Pour chaque page de `Follow` rows (filtrées sur `status = ACCEPTED`), résoudre les `User` correspondants via **un seul** `User.list("id in ?1", ids)` (bulk fetch — pattern déjà appliqué dans `AttendanceService.getAttendees`, cf. [`data-model.md` ligne 150](backend/docs/data-model.md#L150)).
- `from(User)` (factory complète) — l'endpoint étant `@Authenticated`, les compteurs ne sont **pas** projetés ici (c'est `GET /users/{id}` qui les expose pour le profil cible) ; les items de la liste exposent juste l'identité publique de chaque user (id, displayName, faculty, studyLevel, bio, interests, avatarUrl, bannerUrl).

**Justification.** Trois options pesées :

| Option | Verdict |
|---|---|
| (a) `List<FollowDTO>` brut (id, followerId, followedId, status, createdAt) | Le frontend devrait faire N+1 lookups `GET /users/{uuid}` pour afficher avatar+nom — surcharge réseau évitable. |
| (b) **`List<UserPublicResponse>` enrichi** | 1 requête DB de résolution bulk + page ≤ 100 → coût acceptable. Frontend peut afficher avatar+nom sans round-trip supplémentaire. ✅ retenu |
| (c) Wrapper `{ items, total, page, size }` | `List<EventDTO>` brut est déjà le format de toutes les autres listes paginées du projet (`/users/me/favorites`, `/admin/reports`, etc.). Garder la convention. |

**Important.** Les items de cette liste sont projetés via `from(User)` **sans** les nouveaux compteurs (cf. décision 13) — ces compteurs ne font sens que sur le profil cible (`GET /users/{id}`), pas sur chaque follower/followed itemisé. La factory enrichie `from(User, long, long, FollowStatus)` (cf. décision 13) reste réservée à `getProfile`.

### 12. Stripping anonyme N/A pour les listes (endpoint `@Authenticated`)

**Décision.** Comme les listes `/followers` et `/following` sont `@Authenticated` (cf. décision 10), aucun appel anonyme n'arrive à ces endpoints. Pas besoin d'invoquer `UserPublicResponse.fromAnonymous` côté `FollowResource`. La règle de stripping anonyme (ISSUE-93 finding 4.1b) ne s'applique qu'à `GET /users/{id}`.

**Justification.** Cohérence et simplicité — un endpoint `@Authenticated` n'a jamais à se demander si l'appelant est anonyme.

### 13. Enrichissement `UserPublicResponse` avec `followerCount`, `followingCount`, `followStatus`

**Décision.** Le record est étendu :

```java
public record UserPublicResponse(
    UUID id,
    String displayName,
    String faculty,
    String studyLevel,
    String bio,
    List<String> interests,
    String avatarUrl,
    String bannerUrl,
    long followerCount,                  // NEW
    long followingCount,                 // NEW
    FollowStatus followStatus            // NEW (nullable)
) { ... }
```

Trois factories distinctes :

```java
// Pré-existant — préservé pour tests legacy & contextes sans calcul de follow.
// Compteurs = 0, followStatus = null.
public static UserPublicResponse from(User user) { ... }

// Pré-existant — préservé pour les anonymes sur GET /users/{id}.
// Compteurs = 0, followStatus = null.
public static UserPublicResponse fromAnonymous(User user) { ... }

// NOUVELLE — utilisée par UserService.getPublicProfile pour l'appelant authentifié.
public static UserPublicResponse from(
    User user,
    long followerCount,
    long followingCount,
    FollowStatus followStatus  // null si self ou no-relation
) { ... }
```

**Politique de projection des nouveaux champs :**

| Cas appelant | `followerCount` | `followingCount` | `followStatus` |
|---|---|---|---|
| Anon | `0` | `0` | `null` |
| Auth, target = self | compteurs réels | compteurs réels | `null` |
| Auth, target ≠ self, follow inexistant | compteurs réels | compteurs réels | `null` |
| Auth, target ≠ self, follow PENDING | compteurs réels | compteurs réels | `PENDING` |
| Auth, target ≠ self, follow ACCEPTED | compteurs réels | compteurs réels | `ACCEPTED` |

**Justification — pourquoi compteurs `0` pour les anonymes** : un anonyme n'a aucun moyen d'agir socialement (le bouton « Suivre » n'apparaîtra pas côté SCRUM-142 pour un anon). Exposer les compteurs créerait une nouvelle surface de harvest (énumérer la popularité de chaque organisateur public). Cohérent avec la philosophie ISSUE-93 finding 4.1b : *« l'anonyme voit le strict minimum (id, displayName, avatarUrl) »*. La factory `fromAnonymous` zero-init explicitement.

**Justification — pourquoi `followStatus = null` pour self** : un user n'est pas censé pouvoir « se suivre lui-même » (cf. décision 6 → 422). Exposer `followStatus` sur son propre profil n'a pas de sens. La factory enrichie reçoit `null` en 4e argument quand le caller charge son propre profil.

**Justification — pourquoi `long` pour les compteurs et pas `Long` nullable** : zéro = absence de followers, sémantiquement valide. `0L` est non ambigü et évite des `?? 0` côté TS.

### 14. Calcul des compteurs et de `followStatus` dans `UserService.getPublicProfile`

**Décision.** La résolution des trois nouveaux champs est faite dans `UserService.getPublicProfile`, qui injecte `FollowService` (DI) et appelle :
- `FollowService.countFollowers(targetUserId)` → `long`
- `FollowService.countFollowing(targetUserId)` → `long`
- `FollowService.getStatusBetween(callerUserId, targetUserId)` → `FollowStatus | null`

Le Service retourne **un objet typé** (record interne ou tuple `Map.entry` n'est pas accepté — cf. décision 14bis ci-dessous).

### 14bis. Forme du retour de `UserService.getPublicProfile`

**Décision.** La signature passe de `User getPublicProfile(UUID id, String auth0Id)` à :

```java
public PublicProfileView getPublicProfile(UUID id, String auth0Id) {
    // ...
}

/** Vue agrégée retournée par UserService — sert de pont entre Service et Resource
 *  sans exposer FollowService dans la Resource. */
public record PublicProfileView(
    User user,
    long followerCount,
    long followingCount,
    FollowStatus followStatus  // nullable
) {}
```

Le Resource consomme `view.user`, `view.followerCount`, `view.followingCount`, `view.followStatus` et compose le DTO :

```java
UserPublicResponse body = anonymous
    ? UserPublicResponse.fromAnonymous(view.user())
    : UserPublicResponse.from(
        view.user(),
        view.followerCount(),
        view.followingCount(),
        view.followStatus()
      );
```

**Justification — pourquoi un record interne et pas une signature multi-retour bricolée :**

| Option | Verdict |
|---|---|
| (a) Trois services injectés dans la Resource (UserService + FollowService + comptage manuel) | Pollue la Resource avec de la logique métier. Viole `backend/AGENTS.md` *« la Resource ne touche pas aux entités directement, la logique métier est dans le Service »*. |
| (b) Modifier `User` pour stocker les compteurs (cache DB) | Sur-ingénierie pour un MVP. Force des updates transactionnels à chaque follow/unfollow. |
| (c) **Record `PublicProfileView` retourné par le Service** | Une seule responsabilité par couche. Le Service compose les 3 lectures, le Resource projette. ✅ retenu |
| (d) Service retourne `User` enrichi via `@Transient` setters | Hack — `User` est une entité, pas un DTO. |

**Justification — anonymes** : `getPublicProfile(id, null)` retourne `PublicProfileView(user, 0, 0, null)` (court-circuit avant l'appel à `FollowService`) pour économiser les requêtes DB sur les anonymes (cf. décision 13). La règle anti-oracle 404 reste appliquée en amont.

### 15. Réponse `POST /api/users/{id}/follow` → `201 Created` + `FollowDTO`

**Décision.** L'endpoint retourne **`201 Created`** + body `FollowDTO` qui projette `id`, `followerId`, `followedId`, `status` (`PENDING` ou `ACCEPTED` selon `profilePublic` cible), `createdAt`. Le frontend (SCRUM-142) lit `body.status` pour basculer le bouton entre « Demande envoyée » (PENDING) et « Abonné » (ACCEPTED).

**Justification.** Trois options pesées :

| Option | Verdict |
|---|---|
| (a) **`201 Created` + `FollowDTO`** | Aligné REST standard pour création + permet au frontend de connaître le status sans refetch. ✅ retenu |
| (b) `200 OK` + `FollowDTO` | `200` est plutôt pour mutation/idempotence sans création. Contradiction avec `201` retenu pour `POST /events/{id}/co-organizers` ([`api-contract.md` ligne 33](backend/docs/api-contract.md#L33)). |
| (c) `204 No Content` | Force le frontend à refetch `GET /users/{id}` pour connaître le status — round-trip évitable. |

### 16. Réponse `DELETE /api/users/{id}/follow` → `204 No Content` (idempotent)

**Décision.** `204 No Content` toujours, peu importe que la row existait ou non. Pas de body. Pas de 404 si le caller ne suivait pas la cible.

**Justification.** Cf. décision 8 + cohérence stricte avec `DELETE /events/{id}/co-organizers/{userId}` ([`api-contract.md` ligne 35](backend/docs/api-contract.md#L35)). L'UX (SCRUM-142) ne doit jamais voir un toast d'erreur sur une action « Se désabonner ».

### 17. PATCH accept/reject sur un follow non-PENDING → `409 invalid_transition`

**Décision.** Si la row ciblée par `PATCH /api/follow-requests/{id}/accept` ou `.../reject` a déjà `status = ACCEPTED` (ou est en cours de processus terminé), le service rejette avec **`409 Conflict`** + envelope `{ "error": "invalid_transition", "message": "..." }`.

```java
if (follow.status != FollowStatus.PENDING) {
    throw conflict("invalid_transition",
        "Follow is already in status " + follow.status + " — only PENDING follows can be transitioned.");
}
```

**Justification.** Pattern strictement aligné sur SCRUM-94 décision 20 (`PATCH /admin/reports/{id}` sur un report non-PENDING). Une transition double-cliquée par l'utilisateur ne doit pas être absorbée silencieusement — le 409 est le signal explicite.

> **Trade-off explicite.** Une refresh tardive du panneau « Demandes reçues » (SCRUM-142) pourrait afficher une demande comme PENDING alors qu'un autre onglet vient de l'accepter — un clic produirait alors un 409 surprenant. Acceptable côté UX (le frontend affichera un toast d'erreur générique). Si le frottement devient gênant, ouvrir un follow-up pour basculer en idempotent.

### 18. Authorization `PATCH accept/reject` → seul le `followed` peut accepter / refuser, sinon `403`

**Décision.** Si le caller authentifié n'est pas le `followed` de la row ciblée (`row.followedId != callerUserId`), le service rejette avec **`403 Forbidden`** + envelope `{ "error": "forbidden", "message": "Only the target of the follow request can accept or reject it." }`.

**Justification.** Trois options pesées :

| Option | Verdict |
|---|---|
| (a) `403 Forbidden` (l'autorisation manque) | Code HTTP standard pour ce cas. Mini-oracle accepté : un attaquant peut savoir que le `followId` existe. Mais cet ID n'est jamais leaké côté API (visible uniquement dans la réponse de `GET /api/users/me/follow-requests` du `followed`) — surface d'attaque négligeable. ✅ retenu |
| (b) `404 Not Found` anti-oracle | Sur-précaution. Le `followId` est un `Long` séquentiel non énumérable dans une URL publique. La règle anti-oracle ISSUE-92/93 cible les UUIDs leakés via `creatorId` — ce n'est pas le cas ici. |
| (c) `400 Bad Request` | Inapproprié — le body est valide, c'est le caller qui n'est pas autorisé. |

### 19. Notifications hors scope (déléguées à SCRUM-140, S7)

**Décision.** Aucune notification émise par `FollowService` (création, accept, reject). Pas d'entité `Notification` touchée, pas de Quarkus event, pas de hook async.

**Justification.** SCRUM-140 (S7) prévoit explicitement le câblage des trois types `NEW_FOLLOWER`, `FOLLOW_REQUEST`, `FOLLOW_ACCEPTED` une fois l'infra Notification livrée par SCRUM-99. Toute infrastructure de notif ajoutée dans cette PR introduirait des dépendances (table, Service, scheduler éventuel) qui sortent largement du scope SCRUM-138. Une fois SCRUM-99 + SCRUM-140 mergés, un follow-up trivial branchera `FollowService.follow()` / `acceptRequest()` sur le pipeline notif.

### 20. Finder statique `Follow.findAcceptedFollowedIds(UUID): List<UUID>` livré ici (anticipation SCRUM-168)

**Décision.** La classe `Follow` expose un finder statique :

```java
public static List<UUID> findAcceptedFollowedIds(UUID followerId) {
    return Follow.<Follow>find("followerId = ?1 AND status = ?2",
                FollowStatus.ACCEPTED, followerId)
            .project(...)            // pseudo, voir étape 3 pour la signature exacte
            .stream()
            .map(f -> f.followedId)
            .toList();
}
```

(L'implémentation peut utiliser une projection JPQL `select f.followedId from Follow f where ...` pour éviter de matérialiser les entités.)

Ce finder est consommé par SCRUM-168 (S9, filtre `followedOnly` sur `GET /api/events`). Il est **livré ici**, avec un test sentinel dédié (cf. étape 9), pour figer le contrat avant SCRUM-168.

**Justification.** Backlog SCRUM-168 ([ligne 1352](backend/docs/backlog_s5_s10.md#L1352)) cite explicitement : *« Récupérer la liste des `followedId` via `Follow.find("followerId = ?1 AND status = ?2", userId, FollowStatus.ACCEPTED)` → extraire les UUIDs. »*. L'introduire ici via un finder dédié et testé évite à SCRUM-168 de re-réfléchir à la requête JPQL plus tard.

### 21. Pas de bulk endpoints, pas de search

**Décision.** Pas de `POST /api/follow-requests/bulk-accept`, pas de `GET /api/users/{id}/followers/search?q=…`. Les 7 endpoints listés dans le backlog suffisent.

**Justification.** SCRUM-138 livre le primitif. Toute UX bulk ou recherche émergerait d'un besoin produit non documenté — à reporter en follow-up.

### 22. Rate limiting → `@PerUserRateLimit(name="follows.follow", max=30)` sur POST follow

**Décision.** Apposer l'annotation `@PerUserRateLimit(name = "follows.follow", max = 30)` sur la méthode `FollowResource.follow(@PathParam UUID id)`. Pas de rate limit explicite sur `unfollow`, accept, reject (les abus n'ont pas de valeur attaquant).

**Justification.** 30 follows/min/utilisateur = ~ une action toutes les 2 secondes. Couvre un usage légitime intensif (parcours d'une liste de membres d'un club) sans laisser tourner un script de mass-follow (vecteur de spam d'événements). Pattern aligné sur `users.updateMe` (10/min, [`UserResource.java:223`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L223)) — `follow` est une action moins sensible mais à fréquence légitimement plus élevée.

### 23. Pas de `@Transactional` sur les lectures pures du `FollowService`

**Décision.** Les méthodes `countFollowers`, `countFollowing`, `getStatusBetween`, `getFollowers`, `getFollowing`, `getPendingRequests` sont **non-transactionnelles** (pure read). `@Transactional` reste apposé sur `follow`, `unfollow`, `acceptRequest`, `rejectRequest` (les mutations).

**Justification.** Aligné sur le pattern `UserService.getPublicProfile` (non-transactionnel, cf. ISSUE-93 décision 11 / [`UserService.java:78`](backend/src/main/java/ch/unige/events/service/UserService.java#L78)). Hibernate gère les lectures sans transaction explicite. Ajouter `@Transactional` partout serait un overhead inutile.

### 24. `FollowDTO` — projection minimale

**Décision.** Le DTO :

```java
public record FollowDTO(
    Long id,
    UUID followerId,
    UUID followedId,
    FollowStatus status,
    LocalDateTime createdAt
) {
    public static FollowDTO from(Follow f) {
        return new FollowDTO(f.id, f.followerId, f.followedId, f.status, f.createdAt);
    }
}
```

**Justification.** Tout ce qu'un consommateur (frontend SCRUM-142 panneau « Demandes reçues ») doit afficher pour rendre la modale + les boutons accept/reject. Pas de `User` enrichi (cf. décision 11 — le frontend résoudra `GET /users/{id}` à la demande pour le profil du `followerId`).

### 25. Réponse `GET /api/users/me/follow-requests` → `List<FollowDTO>` brut

**Décision.** L'endpoint retourne un `List<FollowDTO>` plat (rows PENDING uniquement, tri `createdAt DESC, id DESC`). Pagination identique aux autres `me/*` (page, size).

**Justification.** Pas d'enrichissement `User` car le frontend (SCRUM-142) chargera les profils des `followerId` un par un via `GET /api/users/{id}` (déjà `@PermitAll`, déjà cohérent avec le pattern « drill-down on demand »). Évite un N+1 côté serveur.

---

## Analyse de l'existant

### Ce qui existe déjà à RÉUTILISER tel quel

| Élément | Fichier / ligne | Rôle |
|---|---|---|
| `User.id`, `User.auth0Id`, `User.profilePublic` | [`User.java`](backend/src/main/java/ch/unige/events/entity/User.java) | Source de vérité pour résolutions et règle d'auto-accept |
| `User.findByAuth0Id(String): Optional<User>` | [`User.java:53-55`](backend/src/main/java/ch/unige/events/entity/User.java#L53-L55) | Résolution dans `FollowService` |
| `Favorite` + V4 (modèle direct table de jointure UUID/Long) | [`Favorite.java`](backend/src/main/java/ch/unige/events/entity/Favorite.java) + [`V4__create_favorites.sql`](backend/src/main/resources/db/migration/V4__create_favorites.sql) | Pattern à dupliquer |
| `FavoriteService.resolveUserId(auth0Id)` | [`FavoriteService.java:75-80`](backend/src/main/java/ch/unige/events/service/FavoriteService.java#L75-L80) | À dupliquer dans `FollowService` |
| `UserResource` injection `SecurityIdentity identity` | [`UserResource.java:55`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L55) | À reprendre dans `FollowResource` |
| `UserService.getPublicProfile(UUID, String)` (règle anti-oracle 404) | [`UserService.java:78-90`](backend/src/main/java/ch/unige/events/service/UserService.java#L78-L90) | À étendre — nouvelle signature retournant `PublicProfileView` |
| Pattern `from(User)` + `fromAnonymous(User)` factories | [`UserPublicResponse.java`](backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java) | À étendre avec `from(User, long, long, FollowStatus)` |
| Pattern pagination `@DefaultValue("0") @Min(0) page` + `@DefaultValue("20") @Positive @Max(100) size` | [`UserResource.java:286-288`](backend/src/main/java/ch/unige/events/resource/UserResource.java#L286-L288) | Standard projet, à reprendre |
| Helpers `badRequest/conflict/unprocessable` (WebApplicationException + ApiErrorResponse) | [`EventCoOrganizerService.java:158-180`](backend/src/main/java/ch/unige/events/service/EventCoOrganizerService.java#L158-L180) | À dupliquer dans `FollowService` (ne pas extraire en util partagé pour cette PR — Boy Scout après mergé) |
| `@PerUserRateLimit` annotation | [`PerUserRateLimit.java:33`](backend/src/main/java/ch/unige/events/config/PerUserRateLimit.java#L33) | À apposer sur POST follow |
| `ApiErrorResponse` record | [`ApiErrorResponse.java`](backend/src/main/java/ch/unige/events/dto/ApiErrorResponse.java) | Envelope d'erreur standard |
| Mappers d'exception standards | [`exception/mapper/`](backend/src/main/java/ch/unige/events/exception/mapper/) | Réutilisés tels quels |
| `FavoriteServiceMock` + `FavoriteResourceTest` (patterns tests) | [`FavoriteServiceMock.java`](backend/src/test/java/ch/unige/events/service/FavoriteServiceMock.java) + [`FavoriteResourceTest.java`](backend/src/test/java/ch/unige/events/resource/FavoriteResourceTest.java) | Modèles directs |
| `AdminReportResourceTest` (pattern `@TestSecurity`) | [`AdminReportResourceTest.java:44`](backend/src/test/java/ch/unige/events/resource/AdminReportResourceTest.java#L44) | Pour les tests `403` accept par non-cible |

### Ce qui est à modifier

| Fichier | Modification |
|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | **Ajouter** schémas `FollowStatus`, `FollowDTO`, `FollowRequestDTO` ; **enrichir** `UserPublicResponse` (3 nouveaux champs) ; **ajouter** 7 paths (`POST/DELETE /users/{id}/follow`, `GET /users/{id}/followers`, `GET /users/{id}/following`, `GET /users/me/follow-requests`, `PATCH /follow-requests/{followId}/accept`, `PATCH /follow-requests/{followId}/reject`). |
| [`backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java`](backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java) | Étendre le record avec `followerCount: long`, `followingCount: long`, `followStatus: FollowStatus` ; ajouter factory `from(User, long, long, FollowStatus)` ; mettre à jour `from(User)` et `fromAnonymous(User)` pour zéro-init les nouveaux champs (compat tests legacy). |
| [`backend/src/main/java/ch/unige/events/service/UserService.java`](backend/src/main/java/ch/unige/events/service/UserService.java) (lignes 78-90) | Modifier la signature `getPublicProfile(UUID id, String auth0Id)` : retourner désormais `PublicProfileView` (record dans le service ou dans `dto/user/`) ; injecter `FollowService` ; calculer `followerCount`, `followingCount`, `followStatus` ; preserver la règle anti-oracle 404. |
| [`backend/src/main/java/ch/unige/events/resource/UserResource.java`](backend/src/main/java/ch/unige/events/resource/UserResource.java) (lignes 73-84) | Adapter `getProfile(@PathParam UUID id)` : consommer `PublicProfileView`, projeter via la factory enrichie pour les authentifiés / `fromAnonymous` pour les anonymes. |
| [`backend/src/test/java/ch/unige/events/service/UserServiceMock.java`](backend/src/test/java/ch/unige/events/service/UserServiceMock.java) | Adapter le mock à la nouvelle signature retournant `PublicProfileView`. |
| [`backend/src/test/java/ch/unige/events/service/UserServiceCoverageTest.java`](backend/src/test/java/ch/unige/events/service/UserServiceCoverageTest.java) | Adapter les 3+ tests existants à la nouvelle signature ; ajouter ~4 nouveaux tests pour `followerCount`, `followingCount`, `followStatus` (combinaisons self / no-rel / PENDING / ACCEPTED). |
| [`backend/src/test/java/ch/unige/events/resource/UserResourceTest.java`](backend/src/test/java/ch/unige/events/resource/UserResourceTest.java) | Adapter les tests existants à la nouvelle forme du body `UserPublicResponse` ; ajouter ~3 tests pour vérifier la projection des nouveaux champs. |
| [`backend/docs/data-model.md`](backend/docs/data-model.md) | Section `Follow` neuve (entité + table + finders) ; ajouter `FollowStatus` au tableau « Énumérations » ; étendre la section `UserPublicResponse` (3 nouveaux champs + politique de projection). |
| [`backend/docs/api-contract.md`](backend/docs/api-contract.md) | Ajouter les 7 endpoints dans la table « Endpoints implémentés » + section « Follow (SCRUM-138) » avec règle d'auto-accept et règle d'autorisation accept/reject. |
| [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) | Section S6 — entrée SCRUM-138 (résumé + lien Jira). |

### Ce qui est à créer

| Fichier | Rôle |
|---|---|
| `backend/src/main/resources/db/migration/V14__create_follows.sql` | Migration Flyway (CREATE SEQUENCE, CREATE TABLE, CHECK status, contrainte unique, FK, index) |
| `backend/src/main/java/ch/unige/events/entity/FollowStatus.java` | Enum `PENDING`, `ACCEPTED` |
| `backend/src/main/java/ch/unige/events/entity/Follow.java` | PanacheEntity avec finders statiques (cf. décisions 2-3-20) |
| `backend/src/main/java/ch/unige/events/dto/follow/FollowDTO.java` | Projection sortante des rows Follow (cf. décision 24) |
| `backend/src/main/java/ch/unige/events/dto/user/PublicProfileView.java` | Vue agrégée retournée par `UserService.getPublicProfile` (cf. décision 14bis) |
| `backend/src/main/java/ch/unige/events/service/FollowService.java` | Métier — 7 méthodes publiques + helpers `badRequest/conflict/unprocessable` (cf. décisions 6-7-15-17-18-23) |
| `backend/src/main/java/ch/unige/events/resource/FollowResource.java` | 7 endpoints REST (cf. étape 7) |
| `backend/src/test/java/ch/unige/events/entity/FollowTest.java` | Tests unitaires de l'entité (champs assignables, finders) |
| `backend/src/test/java/ch/unige/events/dto/follow/FollowDTOTest.java` | Test factory `from(Follow)` |
| `backend/src/test/java/ch/unige/events/service/FollowServiceMock.java` | Mock service pour tests Resource (pattern `FavoriteServiceMock`) |
| `backend/src/test/java/ch/unige/events/resource/FollowResourceTest.java` | Tests `@QuarkusTest` Resource (≥ 18 tests) |
| `backend/src/test/java/ch/unige/events/service/FollowServiceCoverageTest.java` | Tests intégration DevServices PostgreSQL (≥ 25 tests, dont sentinels SCRUM-168) |

### Ce qui n'est PAS dans le scope

- ❌ **Aucune modification frontend.** `git diff --stat frontend/` doit être strictement vide. Les tickets frontend (SCRUM-141/142/143) sont S7.
- ❌ **Pas de notification.** Pas d'entité `Notification` touchée, pas de Quarkus event, pas de hook (cf. décision 19, déléguée à SCRUM-140 S7).
- ❌ **Pas de cascade `profilePublic` privé → public.** Les rows PENDING restent PENDING (cf. décision 9).
- ❌ **Pas de modification de `UserService.getOrCreateUser`, `updateMyProfile`, `uploadImage`, `uploadBanner`, `deleteBanner`** — uniquement `getPublicProfile`.
- ❌ **Pas de modification du frontend `ProfilePage.tsx`** ni d'ajout de bouton « Suivre » — SCRUM-142.
- ❌ **Pas de modification des migrations V1..V13** (immutables).
- ❌ **Pas de bulk endpoint** ni de search (cf. décision 21).
- ❌ **Pas de propagation au filtre `GET /events?followedOnly=true`** (SCRUM-168 S9). Le finder `Follow.findAcceptedFollowedIds(UUID)` est livré ici, mais **pas câblé** sur EventResource/EventService.
- ❌ **Pas de bypass admin.** Le rôle `ADMIN` n'a aucun privilège spécial sur les follows (un admin doit lui aussi suivre/se désabonner explicitement). Pas de `@RolesAllowed("ADMIN")` sur les endpoints SCRUM-138.
- ❌ **Pas de soft-delete sur Follow.** Reject = DELETE physique (cf. décision 5). Unfollow = DELETE physique (cf. décision 16).
- ❌ **Pas d'envelope custom pour `404 not_found` sur les listes anti-oracle** — réutiliser `NotFoundException()` standard (envelope `{"error":"not_found","message":"HTTP 404 Not Found"}`, identique à un UUID inexistant).
- ❌ **Pas d'historique** des accept/reject (pas de table d'audit).
- ❌ **Pas de chiffrement** ni de hash sur les champs `followerId`/`followedId` — UUIDs en clair, conformes au reste du data model.
- ❌ **Pas d'index dédié sur `status`** dans V14 — le filtre principal est `(followedId, status)` ou `(followerId, status)`, déjà couvert par les index posés sur les colonnes de jointure (cf. étape 1).
- ❌ **Pas de cache applicatif** sur les compteurs `followerCount`/`followingCount` (cf. décision 14 — calcul à la volée).
- ❌ **Pas de log INFO** sur les actions follow/unfollow (privacy ; pas de besoin audit pour cette PR).
- ❌ **Pas de TODO commenté** dans le code livré.

---

## Étape 0 — `openapi/openapi.yaml` (EN PREMIER, règle d'or)

**Aucune ligne de Java ne doit être écrite avant cette étape.** [`backend/AGENTS.md`](backend/AGENTS.md#L77-L80) : *« Avant d'implémenter un endpoint : 1. L'ajouter dans `openapi/openapi.yaml` ; 2. Ensuite seulement coder Resource → Service → Entity → Test »*.

### 0.1 — Ajouter le schéma `FollowStatus` (section `components.schemas`, à côté de `CoOrganizerStatus` / `ReportStatus` ligne ~818)

```yaml
    FollowStatus:
      type: string
      description: |
        Statut d'une relation de suivi entre deux utilisateurs.
        - `PENDING` : demande de suivi envoyée à un utilisateur dont `profilePublic = false`,
          en attente de validation par la cible (la `followed`).
        - `ACCEPTED` : suivi confirmé. Les deux utilisateurs ont une relation active ; le
          `follower` voit les événements créés par le `followed` dans son feed
          (cf. SCRUM-168 — filtre `followedOnly` Sprint 9).

        Un refus de demande (`PATCH /follow-requests/{id}/reject`) **supprime physiquement**
        la row — la valeur `REJECTED` n'existe pas en base. Cette décision permet une
        re-tentative ultérieure de suivi sans 409 (la contrainte unique étant strictement
        basée sur la présence d'une row, pas sur son statut).
      enum: [PENDING, ACCEPTED]
```

### 0.2 — Ajouter le schéma `FollowDTO` (à proximité de `CoOrganizer`, ligne ~759)

```yaml
    FollowDTO:
      type: object
      description: |
        Représentation d'une row Follow renvoyée par les endpoints follow.
        - `POST /users/{id}/follow` (201) → `FollowDTO` reflétant le statut résolu
          (PENDING si profil cible privé, ACCEPTED si public).
        - `PATCH /follow-requests/{followId}/accept` (200) → `FollowDTO` avec status ACCEPTED.
        - `GET /users/me/follow-requests` (200) → `List<FollowDTO>` filtré sur status PENDING.

        Le `followerId` n'est PAS enrichi en `UserPublicResponse` — le frontend résoudra
        `GET /users/{id}` à la demande pour afficher avatar + displayName.
      properties:
        id:
          type: integer
          format: int64
          description: PK séquentielle (Long, sequence `follows_seq`).
        followerId:
          type: string
          format: uuid
          description: UUID du `User` qui suit ou demande à suivre.
        followedId:
          type: string
          format: uuid
          description: UUID du `User` ciblé par le suivi.
        status:
          $ref: '#/components/schemas/FollowStatus'
        createdAt:
          type: string
          format: date-time
      required: [id, followerId, followedId, status, createdAt]
```

### 0.3 — Enrichir le schéma `UserPublicResponse` (ligne ~130)

**Remplacer** le schéma actuel par la version enrichie ci-dessous (3 nouveaux champs `followerCount`, `followingCount`, `followStatus`) :

```yaml
    UserPublicResponse:
      type: object
      description: |
        Profil public d'un utilisateur.

        **Stripping anonyme** (hotfix pentest 2026-04-17, finding 4.1b) : pour un
        appelant sans JWT, seuls `id`, `displayName` et `avatarUrl` sont renseignés ;
        `faculty`, `studyLevel`, `bio`, `interests`, `bannerUrl` sont systématiquement
        `null`. Les compteurs `followerCount` et `followingCount` sont à `0` et
        `followStatus` est `null` pour les anonymes (cf. SCRUM-138).

        **Compteurs et état de suivi** (SCRUM-138) : pour un appelant authentifié,
        `followerCount` et `followingCount` reflètent les rows `Follow` ACCEPTED
        impliquant l'utilisateur cible. `followStatus` est non-null **uniquement** s'il
        existe une row `Follow` pour le couple `(callerId, targetId)` — il vaut alors
        `PENDING` (demande envoyée par le caller, profil cible privé) ou `ACCEPTED`
        (suivi actif). Sur son propre profil (caller = target), `followStatus` est
        toujours `null`.
      properties:
        id:
          type: string
          format: uuid
        displayName:
          type: string
          nullable: true
        faculty:
          $ref: '#/components/schemas/Faculty'
          nullable: true
        studyLevel:
          $ref: '#/components/schemas/StudyLevel'
          nullable: true
        bio:
          type: string
          nullable: true
        interests:
          type: array
          items:
            type: string
          nullable: true
        avatarUrl:
          type: string
          format: uri
          nullable: true
        bannerUrl:
          type: string
          format: uri
          nullable: true
        followerCount:
          type: integer
          format: int64
          minimum: 0
          description: |
            Nombre de rows `Follow` ACCEPTED dont `followedId = id`. Toujours présent
            (jamais null) ; vaut `0` pour un appelant anonyme ou un user sans followers.
        followingCount:
          type: integer
          format: int64
          minimum: 0
          description: |
            Nombre de rows `Follow` ACCEPTED dont `followerId = id`. Toujours présent
            (jamais null) ; vaut `0` pour un appelant anonyme ou un user qui ne suit personne.
        followStatus:
          $ref: '#/components/schemas/FollowStatus'
          nullable: true
          description: |
            État de la relation **du point de vue de l'appelant authentifié** vers
            l'utilisateur cible. `null` si l'appelant est anonyme, sur son propre profil,
            ou s'il n'existe aucune row `Follow` pour le couple `(callerId, targetId)`.
            `PENDING` ou `ACCEPTED` sinon.
      required: [id, followerCount, followingCount]
```

> **Notes critiques :**
> - `followerCount`, `followingCount` sont marqués `required` et `minimum: 0` — ils ne sont JAMAIS `null` (cf. décision 13).
> - `followStatus` est `nullable: true` — null sur self, anon, no-relation.
> - Tous les autres champs gardent leur sémantique antérieure (ISSUE-93). Ne pas changer leur ordre dans le record Java sans précaution (compatible-binary-projet OFF — Hibernate ne dépend pas de l'ordre, mais Jackson sérialisera dans l'ordre de déclaration → tester côté frontend).

### 0.4 — Ajouter le path `POST /users/{id}/follow` (à proximité de `/users/{id}` GET, ligne ~1113)

```yaml
  /users/{id}/follow:
    post:
      summary: Suivre un utilisateur ou envoyer une demande de suivi (SCRUM-138)
      description: |
        Crée une row `Follow` entre l'appelant authentifié (`follower`) et l'utilisateur
        cible (`followed = {id}`).

        **Règle d'auto-accept :**
        - Si la cible a `profilePublic = true` → status direct `ACCEPTED`.
        - Si la cible a `profilePublic = false` → status `PENDING` ; la cible peut
          ensuite accepter (`PATCH /follow-requests/{id}/accept`) ou refuser
          (`PATCH /follow-requests/{id}/reject`).

        Codes d'erreur :
        - `401 unauthorized` : token absent ou invalide.
        - `404 not_found` : UUID cible inexistant **ou** profil utilisateur appelant
          non provisionné (cas extrême — appeler `GET /users/me` d'abord).
        - `409 already_following` : une row existe déjà pour le couple
          `(followerId, followedId)`, peu importe son statut.
        - `422 cannot_follow_self` : tentative de suivre son propre UUID.
        - `429 rate_limited` : limite `@PerUserRateLimit(name="follows.follow", max=30)` dépassée.
      operationId: follow
      tags: [users, follows]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '201':
          description: Follow créé (status PENDING ou ACCEPTED selon `profilePublic` cible)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FollowDTO'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Utilisateur cible introuvable ou profil appelant non provisionné
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '409':
          description: Le caller suit déjà cette cible (`error=already_following`)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '422':
          description: Tentative de self-follow (`error=cannot_follow_self`)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '429':
          $ref: '#/components/responses/RateLimited'
```

### 0.5 — Ajouter le path `DELETE /users/{id}/follow`

```yaml
    delete:
      summary: Se désabonner / annuler une demande de suivi (SCRUM-138)
      description: |
        Supprime la row `Follow` (followerId = caller, followedId = `{id}`), peu importe
        son statut (`PENDING` ou `ACCEPTED`).

        **Idempotent** : retourne `204 No Content` même si aucune row n'existait. Pas de
        404 sur l'absence de relation — l'UI ne doit jamais voir un toast d'erreur sur
        une action « Se désabonner / annuler la demande ».
      operationId: unfollow
      tags: [users, follows]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
      responses:
        '204':
          description: Suppression effective ou no-op idempotent
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.6 — Ajouter `GET /users/{id}/followers` et `GET /users/{id}/following`

```yaml
  /users/{id}/followers:
    get:
      summary: Liste paginée des utilisateurs qui suivent la cible (SCRUM-138)
      description: |
        Retourne la liste des `User` dont une row `Follow` ACCEPTED a `followedId = {id}`.
        Tri `Follow.createdAt DESC`, tie-breaker `Follow.id DESC`. Page taille max 100.

        **Règle d'autorisation :**
        - Profil cible `profilePublic = true` → 200 + liste paginée (caller authentifié).
        - Profil cible `profilePublic = false`, caller = owner → 200.
        - Profil cible `profilePublic = false`, caller ≠ owner → `404 not_found`
          (envelope identique à un UUID inexistant — anti-oracle, aligné ISSUE-93).
        - Caller anonyme → `401 unauthorized`.

        Items projetés via `UserPublicResponse.from(User)` — compteurs `followerCount` /
        `followingCount` à `0` et `followStatus` à `null` (les compteurs ne font sens que
        sur le profil cible, pas sur les items de la liste).
      operationId: getFollowers
      tags: [users, follows]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
        - name: page
          in: query
          schema:
            type: integer
            default: 0
            minimum: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
            minimum: 1
            maximum: 100
      responses:
        '200':
          description: Liste paginée des followers (tableau vide si aucun)
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/UserPublicResponse'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: |
            Utilisateur cible introuvable, OU profil cible privé demandé par un
            non-owner (envelope identique — anti-oracle).
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

  /users/{id}/following:
    get:
      summary: Liste paginée des utilisateurs suivis par la cible (SCRUM-138)
      description: |
        Retourne la liste des `User` ciblés par les rows `Follow` ACCEPTED dont
        `followerId = {id}`. Mêmes règles que `/followers` (auth, anti-oracle, tri,
        pagination, projection).
      operationId: getFollowing
      tags: [users, follows]
      security:
        - BearerAuth: []
      parameters:
        - name: id
          in: path
          required: true
          schema:
            type: string
            format: uuid
        - name: page
          in: query
          schema:
            type: integer
            default: 0
            minimum: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
            minimum: 1
            maximum: 100
      responses:
        '200':
          description: Liste paginée des utilisateurs suivis (tableau vide si aucun)
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/UserPublicResponse'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: |
            Utilisateur cible introuvable, OU profil cible privé demandé par un
            non-owner (envelope identique — anti-oracle).
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.7 — Ajouter `GET /users/me/follow-requests`

```yaml
  /users/me/follow-requests:
    get:
      summary: Demandes de suivi PENDING reçues par l'utilisateur connecté (SCRUM-138)
      description: |
        Retourne la liste des rows `Follow` PENDING dont `followedId = me`. Tri
        `createdAt DESC, id DESC`.

        Le frontend (SCRUM-142) consomme cet endpoint pour afficher le panneau
        « Demandes reçues » avec boutons Accepter / Refuser (cf. `PATCH /follow-requests/{id}/accept|reject`).
        Pour chaque entrée, le frontend résoudra `GET /users/{followerId}` à la demande
        pour afficher avatar + displayName du demandeur.
      operationId: getMyFollowRequests
      tags: [users, follows]
      security:
        - BearerAuth: []
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 0
            minimum: 0
        - name: size
          in: query
          schema:
            type: integer
            default: 20
            minimum: 1
            maximum: 100
      responses:
        '200':
          description: Demandes PENDING reçues (tableau vide si aucune)
          content:
            application/json:
              schema:
                type: array
                items:
                  $ref: '#/components/schemas/FollowDTO'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: Profil utilisateur appelant non provisionné (appeler `GET /users/me` d'abord)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.8 — Ajouter `PATCH /follow-requests/{followId}/accept` et `.../reject`

```yaml
  /follow-requests/{followId}/accept:
    patch:
      summary: Accepter une demande de suivi (SCRUM-138)
      description: |
        Bascule la row `Follow` (`followId`) de `PENDING` à `ACCEPTED`. Réservé au
        `followed` (la cible de la demande). Sinon `403 forbidden`.

        Codes d'erreur :
        - `401 unauthorized` : token absent ou invalide.
        - `403 forbidden` : caller ≠ `followed`.
        - `404 not_found` : `followId` inexistant.
        - `409 invalid_transition` : la row n'est pas en statut PENDING (déjà ACCEPTED).
      operationId: acceptFollowRequest
      tags: [follows]
      security:
        - BearerAuth: []
      parameters:
        - name: followId
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '200':
          description: Demande acceptée — `FollowDTO` avec status ACCEPTED
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/FollowDTO'
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '403':
          description: |
            Le caller n'est pas la cible de la demande (`error=forbidden`).
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: `followId` inexistant
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '409':
          description: |
            La row n'est pas PENDING (`error=invalid_transition`) — déjà ACCEPTED.
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'

  /follow-requests/{followId}/reject:
    patch:
      summary: Refuser une demande de suivi (SCRUM-138)
      description: |
        Refuse la demande PENDING — **supprime physiquement la row**. Le `follower`
        peut ré-envoyer une demande ultérieurement sans 409.

        Réservé au `followed` (la cible). Sinon `403 forbidden`.

        Codes d'erreur :
        - `401 unauthorized` : token absent ou invalide.
        - `403 forbidden` : caller ≠ `followed`.
        - `404 not_found` : `followId` inexistant.
        - `409 invalid_transition` : la row est déjà ACCEPTED (utiliser DELETE pour se désabonner côté caller, mais le `followed` n'a pas de bouton « refuser » sur un follow déjà actif — front bloque cette UX).
      operationId: rejectFollowRequest
      tags: [follows]
      security:
        - BearerAuth: []
      parameters:
        - name: followId
          in: path
          required: true
          schema:
            type: integer
            format: int64
      responses:
        '204':
          description: Demande refusée et supprimée
        '401':
          description: Token absent ou invalide
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '403':
          description: Caller ≠ `followed`
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '404':
          description: `followId` inexistant
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
        '409':
          description: Row déjà ACCEPTED (`error=invalid_transition`)
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/ApiErrorResponse'
```

### 0.9 — Vérifier la validité YAML

Après les ajouts ci-dessus :

```bash
cd /workspace
python3 -c "import yaml; yaml.safe_load(open('openapi/openapi.yaml'))" && echo "YAML OK"
# Optionnel : si swagger-cli est installé localement
# npx --yes @apidevtools/swagger-cli validate openapi/openapi.yaml
```

---

## Étape 1 — Migration Flyway `V14__create_follows.sql`

**Fichier :** `backend/src/main/resources/db/migration/V14__create_follows.sql` (nouveau)

> **Vérification préalable obligatoire** : exécuter `ls backend/src/main/resources/db/migration | sort` pour s'assurer que `V14` n'a pas été pris par une autre PR mergée. Si oui, basculer en `V15` et adapter toutes les références dans cette spec et les commits.

```sql
-- SCRUM-138 — Création de la table follows : relation directionnelle entre deux utilisateurs.
-- Table de jointure UUID/UUID avec statut PENDING/ACCEPTED, contrainte unique sur le couple,
-- et FK vers users(id) côté DB (sans cascade — pattern défensif assumé, cf. décision 2).

CREATE SEQUENCE IF NOT EXISTS follows_seq START WITH 1 INCREMENT BY 50;

CREATE TABLE IF NOT EXISTS follows (
    id           BIGINT       NOT NULL DEFAULT nextval('follows_seq'),
    follower_id  UUID         NOT NULL,
    followed_id  UUID         NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    CONSTRAINT pk_follows PRIMARY KEY (id),
    CONSTRAINT uq_follow_follower_followed UNIQUE (follower_id, followed_id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(id),
    CONSTRAINT fk_follows_followed FOREIGN KEY (followed_id) REFERENCES users(id),
    CONSTRAINT follows_status_check CHECK (status IN ('PENDING', 'ACCEPTED'))
);

-- Index pour les requêtes inverses fréquentes (listing followers d'un user, calcul de followStatus).
CREATE INDEX IF NOT EXISTS idx_follow_followed ON follows(followed_id);
CREATE INDEX IF NOT EXISTS idx_follow_follower ON follows(follower_id);
```

**Points à respecter :**

- `BIGINT` + `nextval('follows_seq')` → cohérent avec les autres tables `Long`-keyed (`favorites`, `reports`).
- `UUID` direct (pas `BYTEA`) — conforme à la colonne `users.id` (cf. [`V1__create_users.sql:2`](backend/src/main/resources/db/migration/V1__create_users.sql#L2)).
- `status VARCHAR(16)` — laisse une marge si une 3e valeur émergeait dans un futur ticket. La CHECK constraint pose la garde stricte sur l'enum.
- `created_at TIMESTAMP NOT NULL` — pas de défaut DB, c'est `@PrePersist` côté Java qui pose la valeur (pattern aligné sur `Favorite` / `Attendance`).
- **Pas de `ON DELETE CASCADE` sur les FK** — cf. décision 2. Si un user est supprimé, ses rows `Follow` deviennent orphelines (à nettoyer via job ultérieur, hors scope).
- **Pas d'index sur `(follower_id, followed_id, status)`** — le filtre composite passe par la combinaison `idx_follow_follower` + le check `status` filtré au plan PostgreSQL ; le coût d'un index multi-colonnes additionnel ne se justifie pas pour un MVP.

---

## Étape 2 — Enum `FollowStatus`

**Fichier :** `backend/src/main/java/ch/unige/events/entity/FollowStatus.java` (nouveau)

```java
package ch.unige.events.entity;

public enum FollowStatus {
    PENDING,
    ACCEPTED
}
```

**Points à respecter :**

- Pas de javadoc verbeuse — l'enum est documenté côté `data-model.md` et OpenAPI.
- Pas de méthodes (`isAccepted()` etc.) — les comparaisons se font par `==`.
- Sérialisation Jackson par défaut → `"PENDING"` ou `"ACCEPTED"` en JSON. Côté Hibernate : `@Enumerated(EnumType.STRING)` sur l'entité (cf. étape 3).

---

## Étape 3 — Entité `Follow`

**Fichier :** `backend/src/main/java/ch/unige/events/entity/Follow.java` (nouveau)

```java
package ch.unige.events.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(
    name = "follows",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_follow_follower_followed",
        columnNames = {"follower_id", "followed_id"}
    ),
    indexes = {
        @Index(name = "idx_follow_followed", columnList = "followed_id"),
        @Index(name = "idx_follow_follower", columnList = "follower_id")
    }
)
public class Follow extends PanacheEntity {

    @Column(name = "follower_id", nullable = false)
    public UUID followerId;

    @Column(name = "followed_id", nullable = false)
    public UUID followedId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    public FollowStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    // ── Finders statiques (Active Record Panache) ──────────────────────────────

    public static Optional<Follow> findByFollowerAndFollowed(UUID followerId, UUID followedId) {
        return find("followerId = ?1 and followedId = ?2", followerId, followedId).firstResultOptional();
    }

    /** Followers d'un user : page paginée, tri createdAt DESC, status ACCEPTED uniquement. */
    public static List<Follow> findFollowersOf(UUID followedId, int page, int size) {
        return find("followedId = ?1 and status = ?2 order by createdAt desc, id desc",
                followedId, FollowStatus.ACCEPTED)
                .page(page, size)
                .list();
    }

    /** Following d'un user : rows ACCEPTED dont followerId = userId. */
    public static List<Follow> findFollowingOf(UUID followerId, int page, int size) {
        return find("followerId = ?1 and status = ?2 order by createdAt desc, id desc",
                followerId, FollowStatus.ACCEPTED)
                .page(page, size)
                .list();
    }

    /** Demandes PENDING reçues par un user (inbox). */
    public static List<Follow> findPendingRequestsFor(UUID followedId, int page, int size) {
        return find("followedId = ?1 and status = ?2 order by createdAt desc, id desc",
                followedId, FollowStatus.PENDING)
                .page(page, size)
                .list();
    }

    /**
     * SCRUM-168 (S9, anticipé ici par SCRUM-138 décision 20) — extrait les `followedId`
     * d'une projection JPQL directe pour alimenter le filtre `followedOnly` de
     * `GET /api/events`. Évite de matérialiser les entités Follow.
     */
    public static List<UUID> findAcceptedFollowedIds(UUID followerId) {
        return getEntityManager()
                .createQuery(
                    "select f.followedId from Follow f " +
                    "where f.followerId = :follower and f.status = :status",
                    UUID.class)
                .setParameter("follower", followerId)
                .setParameter("status", FollowStatus.ACCEPTED)
                .getResultList();
    }

    public static long countFollowersOf(UUID followedId) {
        return count("followedId = ?1 and status = ?2", followedId, FollowStatus.ACCEPTED);
    }

    public static long countFollowingOf(UUID followerId) {
        return count("followerId = ?1 and status = ?2", followerId, FollowStatus.ACCEPTED);
    }
}
```

**Points à respecter :**

- **Champs publics** (Panache idiom) — pas de getters/setters.
- **`@PrePersist` avec garde `if (createdAt == null)`** — permet aux tests de seed une valeur custom sans qu'elle soit écrasée (pattern utilisé dans `Report`).
- **`Enumerated(STRING)` + `length = 16`** — symétrique avec la colonne SQL.
- **Finders statiques pré-câblés pour les 4 cas d'usage du Service** — un finder par requête, pas de méthode utilitaire qui prend un `Map<String, Object>`.
- **`findAcceptedFollowedIds`** : projection JPQL directe (pas `find(...).list()` qui matérialise les entités). Le retour est `List<UUID>` (pas `Set` — Panache préfère `List`, le caller dédupe si besoin).
- **Pas de `findById` custom** — `Follow.<Follow>findByIdOptional(id)` standard suffit pour PATCH accept/reject.
- **Pas de méthodes mutantes** sur l'entité (`accept()`, `reject()`) — la mutation passe par `FollowService` (séparation des couches).

---

## Étape 4 — DTOs

### 4.1 — `FollowDTO`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/follow/FollowDTO.java` (nouveau)

```java
package ch.unige.events.dto.follow;

import ch.unige.events.entity.Follow;
import ch.unige.events.entity.FollowStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record FollowDTO(
    Long id,
    UUID followerId,
    UUID followedId,
    FollowStatus status,
    LocalDateTime createdAt
) {
    public static FollowDTO from(Follow f) {
        return new FollowDTO(f.id, f.followerId, f.followedId, f.status, f.createdAt);
    }
}
```

### 4.2 — `PublicProfileView` (record agrégé pour `UserService.getPublicProfile`)

**Fichier :** `backend/src/main/java/ch/unige/events/dto/user/PublicProfileView.java` (nouveau)

```java
package ch.unige.events.dto.user;

import ch.unige.events.entity.FollowStatus;
import ch.unige.events.entity.User;

/**
 * Vue agrégée retournée par {@link ch.unige.events.service.UserService#getPublicProfile(java.util.UUID, String)}.
 * Sert de pont entre Service et Resource sans exposer FollowService dans la Resource.
 *
 * <p>Les compteurs et le {@link FollowStatus} sont calculés par le Service ; la Resource
 * compose ensuite l'envelope DTO via les factories de {@link UserPublicResponse}.
 *
 * <p>Pour un appelant anonyme, le Service court-circuite avant d'invoquer FollowService :
 * compteurs à 0, {@code followStatus} à {@code null}.
 */
public record PublicProfileView(
    User user,
    long followerCount,
    long followingCount,
    FollowStatus followStatus
) {
    /** Helper pour les anonymes — compteurs à 0, followStatus null. */
    public static PublicProfileView anonymous(User user) {
        return new PublicProfileView(user, 0L, 0L, null);
    }
}
```

---

## Étape 5 — Enrichissement `UserPublicResponse`

**Fichier :** `backend/src/main/java/ch/unige/events/dto/user/UserPublicResponse.java` (modification)

Remplacer **intégralement** le contenu actuel par :

```java
package ch.unige.events.dto.user;

import ch.unige.events.entity.FollowStatus;
import ch.unige.events.entity.User;

import java.util.List;
import java.util.UUID;

public record UserPublicResponse(
    UUID id,
    String displayName,
    String faculty,
    String studyLevel,
    String bio,
    List<String> interests,
    String avatarUrl,
    String bannerUrl,
    long followerCount,
    long followingCount,
    FollowStatus followStatus
) {

    /**
     * Factory legacy (compteurs = 0, followStatus = null).
     * Utilisée par les contextes où le calcul de follow n'est pas pertinent : items
     * de listes followers / following (cf. SCRUM-138 décision 11), tests.
     */
    public static UserPublicResponse from(User user) {
        return new UserPublicResponse(
                user.id,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.bannerUrl,
                0L,
                0L,
                null
        );
    }

    /**
     * Factory enrichie utilisée par {@link ch.unige.events.service.UserService#getPublicProfile}
     * pour les appelants authentifiés. Compteurs et followStatus calculés par le Service.
     */
    public static UserPublicResponse from(
            User user,
            long followerCount,
            long followingCount,
            FollowStatus followStatus) {
        return new UserPublicResponse(
                user.id,
                user.displayName,
                user.faculty,
                user.studyLevel,
                user.bio,
                user.interests,
                user.avatarUrl,
                user.bannerUrl,
                followerCount,
                followingCount,
                followStatus
        );
    }

    /**
     * Factory pour les appelants anonymes — projet uniquement id, displayName et avatarUrl.
     * Compteurs à 0, followStatus null. Hotfix pentest 2026-04-17 finding 4.1b
     * (limit anonymous harvest of opt-in public profiles).
     */
    public static UserPublicResponse fromAnonymous(User user) {
        return new UserPublicResponse(
                user.id,
                user.displayName,
                null,
                null,
                null,
                null,
                user.avatarUrl,
                null,
                0L,
                0L,
                null
        );
    }
}
```

**Points à respecter :**

- L'**ordre** des champs du record est strict : `id, displayName, faculty, studyLevel, bio, interests, avatarUrl, bannerUrl, followerCount, followingCount, followStatus`. Tout test qui construit un `UserPublicResponse` directement via constructeur (s'il en existe en `coverage`) doit être adapté.
- Les 3 factories existent **toutes** — pas de suppression de l'ancienne `from(User)` qui reste utilisée pour les items de liste.
- `null`-init pour `followStatus` est explicite (4e argument de la factory enrichie).

---

## Étape 6 — `FollowService`

**Fichier :** `backend/src/main/java/ch/unige/events/service/FollowService.java` (nouveau)

### 6.1 — Squelette de classe + injections + helpers

```java
package ch.unige.events.service;

import ch.unige.events.dto.ApiErrorResponse;
import ch.unige.events.entity.Follow;
import ch.unige.events.entity.FollowStatus;
import ch.unige.events.entity.User;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class FollowService {

    @Inject EntityManager entityManager;

    // ── Lectures (pas @Transactional, cf. décision 23) ─────────────────────────

    public long countFollowers(UUID userId) {
        return Follow.countFollowersOf(userId);
    }

    public long countFollowing(UUID userId) {
        return Follow.countFollowingOf(userId);
    }

    /**
     * État du follow (callerId → targetId), ou null si aucune row.
     * Conformément à la décision 13, retourne null pour caller == target (self).
     */
    public FollowStatus getStatusBetween(UUID callerId, UUID targetId) {
        if (callerId == null || callerId.equals(targetId)) {
            return null;
        }
        return Follow.findByFollowerAndFollowed(callerId, targetId)
                .map(f -> f.status)
                .orElse(null);
    }

    public List<Follow> getFollowers(UUID userId, int page, int size) {
        return Follow.findFollowersOf(userId, page, size);
    }

    public List<Follow> getFollowing(UUID userId, int page, int size) {
        return Follow.findFollowingOf(userId, page, size);
    }

    public List<Follow> getPendingRequests(String auth0Id, int page, int size) {
        UUID me = resolveUserId(auth0Id);
        return Follow.findPendingRequestsFor(me, page, size);
    }

    // ── Mutations (toutes @Transactional) ──────────────────────────────────────

    @Transactional
    public Follow follow(String followerAuth0Id, UUID followedId) {
        UUID followerId = resolveUserId(followerAuth0Id);
        if (followerId.equals(followedId)) {
            throw unprocessable("cannot_follow_self", "You cannot follow yourself.");
        }

        User followed = (User) User.findByIdOptional(followedId)
                .orElseThrow(() -> new NotFoundException("Target user not found"));

        // Doublon : check applicatif + filet de sécurité unique constraint.
        if (Follow.findByFollowerAndFollowed(followerId, followedId).isPresent()) {
            throw conflict("already_following", "You are already following this user.");
        }

        Follow row = new Follow();
        row.followerId = followerId;
        row.followedId = followedId;
        row.status = followed.profilePublic ? FollowStatus.ACCEPTED : FollowStatus.PENDING;
        row.persist();
        entityManager.flush();   // garantit que la unique-constraint déclenche en cas de race
        return row;
    }

    @Transactional
    public void unfollow(String followerAuth0Id, UUID followedId) {
        UUID followerId = resolveUserId(followerAuth0Id);
        Follow.findByFollowerAndFollowed(followerId, followedId)
                .ifPresent(Follow::delete);
        // Pas d'exception si rien à supprimer — DELETE idempotent (cf. décision 16).
    }

    @Transactional
    public Follow acceptRequest(String targetAuth0Id, Long followId) {
        UUID targetUserId = resolveUserId(targetAuth0Id);
        Follow row = Follow.<Follow>findByIdOptional(followId)
                .orElseThrow(() -> new NotFoundException("Follow request not found"));
        if (!row.followedId.equals(targetUserId)) {
            throw forbidden("forbidden", "Only the target of the follow request can accept it.");
        }
        if (row.status != FollowStatus.PENDING) {
            throw conflict("invalid_transition",
                    "Follow is already in status " + row.status + " — only PENDING follows can be accepted.");
        }
        row.status = FollowStatus.ACCEPTED;
        return row;
    }

    @Transactional
    public void rejectRequest(String targetAuth0Id, Long followId) {
        UUID targetUserId = resolveUserId(targetAuth0Id);
        Follow row = Follow.<Follow>findByIdOptional(followId)
                .orElseThrow(() -> new NotFoundException("Follow request not found"));
        if (!row.followedId.equals(targetUserId)) {
            throw forbidden("forbidden", "Only the target of the follow request can reject it.");
        }
        if (row.status != FollowStatus.PENDING) {
            throw conflict("invalid_transition",
                    "Follow is already in status " + row.status + " — only PENDING follows can be rejected.");
        }
        row.delete();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private UUID resolveUserId(String auth0Id) {
        return User.<User>find("auth0Id", auth0Id)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("User profile not found"))
                .id;
    }

    protected static WebApplicationException conflict(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.CONFLICT)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    protected static WebApplicationException unprocessable(String error, String message) {
        return new WebApplicationException(
                Response.status(422)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }

    protected static WebApplicationException forbidden(String error, String message) {
        return new WebApplicationException(
                Response.status(Response.Status.FORBIDDEN)
                        .entity(new ApiErrorResponse(error, message))
                        .type(MediaType.APPLICATION_JSON_TYPE)
                        .build());
    }
}
```

**Points à respecter :**

- **Lectures non-transactionnelles** (cf. décision 23). `entityManager.flush()` n'est appelé QUE dans `follow()` pour forcer la unique-constraint à se déclencher synchroniquement (race-safety du 409 already_following).
- **`resolveUserId`** dupliqué avec `FavoriteService.resolveUserId` — pas de Boy Scout vers une méthode statique partagée dans cette PR (à extraire dans une issue dédiée si l'on duplique 3 fois). Le code est trivial et la duplication acceptable.
- **`acceptRequest` retourne `Follow`** (pas `void`) — la Resource projette via `FollowDTO.from`. `rejectRequest` retourne `void` (la Resource renvoie 204).
- **Pas de log INFO** sur les actions follow/unfollow (privacy ; cf. scope « pas dans le scope »).
- **`forbidden` helper** — pour le 403 accept/reject par non-cible (décision 18). Pas de réutilisation d'un mapper standard pour pouvoir envoyer `error=forbidden` custom.
- **`flush()` après `persist()`** dans `follow()` — pattern aligné sur `UserService.flushEntityManager()` ([`UserService.java:155-157`](backend/src/main/java/ch/unige/events/service/UserService.java#L155-L157)). Sans ce flush, deux POST simultanés n'enclenchent pas la unique-constraint au moment du return → le 409 « late » serait écrasé par un 200 OK trompeur.

### 6.2 — Câblage dans `UserService.getPublicProfile`

**Fichier :** [`backend/src/main/java/ch/unige/events/service/UserService.java`](backend/src/main/java/ch/unige/events/service/UserService.java) (modification)

Modifications :

```java
// Nouveau import :
import ch.unige.events.dto.user.PublicProfileView;
import ch.unige.events.entity.FollowStatus;

// Nouvelle injection :
@Inject FollowService followService;

// Modification de la méthode existante :
public PublicProfileView getPublicProfile(UUID id, String auth0Id) {
    User user = (User) User.findByIdOptional(id).orElseThrow(NotFoundException::new);

    // Hotfix pentest 4.1 (ISSUE-93) : 404 anti-oracle — préservé tel quel.
    boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
    if (!user.profilePublic && !isOwner) {
        throw new NotFoundException();
    }

    // SCRUM-138 : compteurs + état de suivi.
    if (auth0Id == null) {
        // Anonymes : court-circuit, pas d'appel FollowService.
        return PublicProfileView.anonymous(user);
    }

    long followerCount = followService.countFollowers(user.id);
    long followingCount = followService.countFollowing(user.id);

    FollowStatus followStatus = null;
    if (!isOwner) {
        UUID callerId = User.findByAuth0Id(auth0Id).map(u -> u.id).orElse(null);
        if (callerId != null) {
            followStatus = followService.getStatusBetween(callerId, user.id);
        }
    }
    return new PublicProfileView(user, followerCount, followingCount, followStatus);
}
```

**Points à respecter :**

- Conserver la règle anti-oracle 404 telle quelle (ne pas toucher à la condition `!user.profilePublic && !isOwner`).
- Le court-circuit `auth0Id == null` économise 2 requêtes DB sur les anonymes.
- Le `if (!isOwner)` évite un appel `getStatusBetween(self, self)` qui retournerait `null` de toute façon — micro-opti + intention explicite (« on ne calcule pas un follow status sur son propre profil »).
- **Le caller non provisionné** (auth0Id sans User correspondant — cas extrême après changement d'auth0 mid-session) tombe dans le `if (callerId != null)` → followStatus reste `null`. Pas de NotFoundException sur le caller (le profil cible reste lisible).

### 6.3 — Adaptation de `UserResource.getProfile`

**Fichier :** [`backend/src/main/java/ch/unige/events/resource/UserResource.java`](backend/src/main/java/ch/unige/events/resource/UserResource.java) (lignes 73-84, modification)

```java
// Nouvel import :
import ch.unige.events.dto.user.PublicProfileView;

// Modification :
@GET
@Path("/{id}")
@PermitAll
public Response getProfile(@PathParam("id") UUID id) {
    boolean anonymous = identity.isAnonymous();
    String auth0Id = anonymous ? null : identity.getPrincipal().getName();
    PublicProfileView view = userService.getPublicProfile(id, auth0Id);
    UserPublicResponse body = anonymous
            ? UserPublicResponse.fromAnonymous(view.user())
            : UserPublicResponse.from(
                    view.user(),
                    view.followerCount(),
                    view.followingCount(),
                    view.followStatus()
              );
    return Response.ok(body).build();
}
```

**Points à respecter :**

- La règle anti-oracle 404 reste portée par le Service — la Resource n'a aucune logique d'autorisation (cf. ISSUE-93 décision 2).
- L'ancienne signature `UserPublicResponse.from(User)` n'est plus appelée ici — elle reste exportée pour les items de liste (cf. étape 7).

---

## Étape 7 — `FollowResource`

**Fichier :** `backend/src/main/java/ch/unige/events/resource/FollowResource.java` (nouveau)

```java
package ch.unige.events.resource;

import ch.unige.events.config.PerUserRateLimit;
import ch.unige.events.dto.follow.FollowDTO;
import ch.unige.events.dto.user.UserPublicResponse;
import ch.unige.events.entity.Follow;
import ch.unige.events.entity.User;
import ch.unige.events.service.FollowService;
import ch.unige.events.service.UserService;

import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;

import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Path("/")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class FollowResource {

    @Inject SecurityIdentity identity;
    @Inject FollowService followService;
    @Inject UserService userService;   // utilisé pour la garde 404 anti-oracle sur listes (cf. ci-dessous)

    // ── POST /users/{id}/follow ────────────────────────────────────────────────

    @POST
    @Path("/users/{id}/follow")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    @PerUserRateLimit(name = "follows.follow", max = 30)
    public Response follow(@PathParam("id") UUID followedId) {
        String auth0Id = identity.getPrincipal().getName();
        Follow row = followService.follow(auth0Id, followedId);
        return Response.status(Response.Status.CREATED)
                .entity(FollowDTO.from(row))
                .build();
    }

    @DELETE
    @Path("/users/{id}/follow")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response unfollow(@PathParam("id") UUID followedId) {
        String auth0Id = identity.getPrincipal().getName();
        followService.unfollow(auth0Id, followedId);
        return Response.noContent().build();
    }

    // ── GET /users/{id}/followers ──────────────────────────────────────────────

    @GET
    @Path("/users/{id}/followers")
    @Authenticated
    public List<UserPublicResponse> getFollowers(
            @PathParam("id") UUID userId,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        // Garde 404 anti-oracle alignée ISSUE-93 : passe par UserService.getPublicProfile.
        // La méthode jette NotFoundException si profil inexistant ou privé non-owner.
        String auth0Id = identity.getPrincipal().getName();
        userService.getPublicProfile(userId, auth0Id);   // throws si non visible

        List<Follow> rows = followService.getFollowers(userId, page, size);
        return resolveUsers(rows.stream().map(f -> f.followerId).toList());
    }

    @GET
    @Path("/users/{id}/following")
    @Authenticated
    public List<UserPublicResponse> getFollowing(
            @PathParam("id") UUID userId,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        userService.getPublicProfile(userId, auth0Id);   // throws si non visible

        List<Follow> rows = followService.getFollowing(userId, page, size);
        return resolveUsers(rows.stream().map(f -> f.followedId).toList());
    }

    // ── GET /users/me/follow-requests ──────────────────────────────────────────

    @GET
    @Path("/users/me/follow-requests")
    @Authenticated
    public List<FollowDTO> getMyFollowRequests(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Positive @Max(100) int size) {
        String auth0Id = identity.getPrincipal().getName();
        return followService.getPendingRequests(auth0Id, page, size).stream()
                .map(FollowDTO::from)
                .toList();
    }

    // ── PATCH /follow-requests/{followId}/accept | /reject ─────────────────────

    @PATCH
    @Path("/follow-requests/{followId}/accept")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public FollowDTO acceptFollowRequest(@PathParam("followId") Long followId) {
        String auth0Id = identity.getPrincipal().getName();
        Follow row = followService.acceptRequest(auth0Id, followId);
        return FollowDTO.from(row);
    }

    @PATCH
    @Path("/follow-requests/{followId}/reject")
    @Authenticated
    @Consumes(MediaType.WILDCARD)
    public Response rejectFollowRequest(@PathParam("followId") Long followId) {
        String auth0Id = identity.getPrincipal().getName();
        followService.rejectRequest(auth0Id, followId);
        return Response.noContent().build();
    }

    // ── Helpers internes ──────────────────────────────────────────────────────

    /**
     * Résout en bulk les `User` correspondant aux UUIDs reçus (1 seule requête DB),
     * puis projette via `UserPublicResponse.from(User)` en préservant l'ordre d'arrivée
     * (ordre de la liste `Follow` paginée). Pattern aligné sur
     * `AttendanceService.getAttendees` — cf. data-model.md L150.
     */
    private List<UserPublicResponse> resolveUsers(List<UUID> ids) {
        if (ids.isEmpty()) return List.of();
        List<User> users = User.list("id in ?1", ids);
        Map<UUID, User> byId = new HashMap<>(users.size());
        users.forEach(u -> byId.put(u.id, u));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(UserPublicResponse::from)   // ← factory legacy (compteurs 0, status null)
                .toList();
    }
}
```

**Points à respecter :**

- **`@Path("/")`** + paths absolus par méthode — permet à une seule Resource de servir les paths racine `/users/{id}/...` ET `/follow-requests/{id}/...`. Pattern aligné avec `EventResource` qui mélange `/events/...` et `/events/{id}/...` sur la même classe.
- **`@Consumes(MediaType.WILDCARD)`** sur les méthodes sans body (POST/PATCH/DELETE) — évite le 415 Unsupported Media Type sur les requêtes sans header `Content-Type` (pattern aligné avec `UserResource.deleteBanner` ligne 276).
- **Pas de `@Valid`** car aucun body côté requête.
- **`@PerUserRateLimit`** uniquement sur `follow` (cf. décision 22).
- **Garde 404 anti-oracle pour les listes** : la Resource appelle d'abord `userService.getPublicProfile(userId, auth0Id)` qui jette `NotFoundException` si le profil cible est inaccessible. **Ne pas réimplémenter la garde dans `FollowService`** — la règle vit déjà dans `UserService`. Conséquence : 2 requêtes DB en plus pour chaque liste (1 lookup user, 1 anti-oracle check) — acceptable, optimisation possible ultérieurement.
- **`resolveUsers` filtre les `null`** — protection défensive contre les UUIDs orphelins (user supprimé entre la lecture du Follow et le bulk fetch). Aucune row n'a normalement de UUID orphelin, mais la décision 2 (pas de cascade FK) le rend possible théoriquement.
- **Ordre préservé** dans `resolveUsers` — la liste sortante respecte l'ordre `createdAt DESC` de la requête initiale, pas l'ordre alphabétique sur UUID que renverrait `User.list`.

---

## Étape 8 — Adaptation `UserService` + tests existants

### 8.1 — Adapter `UserServiceMock` à la nouvelle signature

**Fichier :** [`backend/src/test/java/ch/unige/events/service/UserServiceMock.java`](backend/src/test/java/ch/unige/events/service/UserServiceMock.java) (modification)

La méthode override `getPublicProfile(UUID, String)` retourne désormais `PublicProfileView` au lieu de `User` :

```java
@Override
public PublicProfileView getPublicProfile(UUID id, String auth0Id) {
    User user = usersById.get(id);
    if (user == null) {
        throw new NotFoundException();
    }
    boolean isOwner = auth0Id != null && auth0Id.equals(user.auth0Id);
    if (!user.profilePublic && !isOwner) {
        throw new NotFoundException();
    }
    // Pour le mock : compteurs et followStatus pré-seedés via setters statiques (cf. ci-dessous)
    return new PublicProfileView(
            user,
            mockFollowerCount,
            mockFollowingCount,
            mockFollowStatus);
}

public static volatile long mockFollowerCount = 0L;
public static volatile long mockFollowingCount = 0L;
public static volatile FollowStatus mockFollowStatus = null;

// reset() doit aussi remettre ces champs à zéro :
public void reset() {
    super.reset();
    mockFollowerCount = 0L;
    mockFollowingCount = 0L;
    mockFollowStatus = null;
}
```

### 8.2 — Adapter `UserServiceCoverageTest`

Tous les tests qui appelaient `userService.getPublicProfile(uuid, null)` ou `(uuid, auth0)` doivent désormais consommer `view.user()` au lieu de la valeur de retour directe :

```java
// AVANT
User user = userService.getPublicProfile(uuid, null);
assertEquals("Alice", user.displayName);

// APRÈS
PublicProfileView view = userService.getPublicProfile(uuid, null);
assertEquals("Alice", view.user().displayName);
assertEquals(0L, view.followerCount());                  // anonyme → 0
assertNull(view.followStatus());                         // anonyme → null
```

Ajouter **4 nouveaux tests** :

| Test | Sentinel ? | Description |
|---|---|---|
| `getPublicProfile_authNonOwner_includesFollowerCount` | ✅ | Seed 2 followers ACCEPTED + 1 PENDING (PENDING ne compte pas) → `view.followerCount() == 2`. |
| `getPublicProfile_self_followStatusIsNull` | ✅ | `auth0Id == user.auth0Id` → `view.followStatus() == null` même si rows existent. |
| `getPublicProfile_authNonOwnerNoRelation_followStatusIsNull` | ✅ | Caller authentifié, aucune row Follow → `view.followStatus() == null`. |
| `getPublicProfile_authNonOwnerWithPending_followStatusIsPending` | ✅ | Caller a une row PENDING vers target → `view.followStatus() == FollowStatus.PENDING`. |

### 8.3 — Adapter `UserResourceTest`

Les tests qui vérifient le body de `GET /users/{id}` doivent désormais inclure les 3 nouveaux champs :

```java
given().header(...).when().get("/users/{id}", id)
    .then().statusCode(200)
    .body("followerCount", equalTo(0))
    .body("followingCount", equalTo(0))
    .body("followStatus", nullValue());
```

Pas de nouveaux tests Resource dédiés — la couverture est portée par `UserServiceCoverageTest`. Vérifier que les tests existants restent verts.

---

## Étape 9 — Tests

**Cible globale :** JaCoCo ≥ 80 % sur les lignes nouvelles, idéalement **100 %** sur `FollowService` et `FollowResource`. Duplication < 3 %, ratings A.

### 9.1 — `FollowTest` (entité, unitaire)

**Fichier :** `backend/src/test/java/ch/unige/events/entity/FollowTest.java` (nouveau)

```java
@QuarkusTest
class FollowTest {
    @Test void fieldsAreAssignable() {
        Follow f = new Follow();
        f.followerId = UUID.randomUUID();
        f.followedId = UUID.randomUUID();
        f.status = FollowStatus.PENDING;
        f.createdAt = LocalDateTime.now();
        assertEquals(FollowStatus.PENDING, f.status);
    }

    @Test void prePersist_setsCreatedAt_whenNull() {
        Follow f = new Follow();
        f.prePersist();
        assertNotNull(f.createdAt);
    }

    @Test void prePersist_preservesExistingCreatedAt() {
        Follow f = new Follow();
        LocalDateTime fixed = LocalDateTime.of(2026, 1, 1, 12, 0);
        f.createdAt = fixed;
        f.prePersist();
        assertEquals(fixed, f.createdAt);
    }
}
```

### 9.2 — `FollowDTOTest`

**Fichier :** `backend/src/test/java/ch/unige/events/dto/follow/FollowDTOTest.java` (nouveau)

```java
class FollowDTOTest {
    @Test void from_projectsAllFields() {
        Follow f = new Follow();
        f.id = 42L;
        f.followerId = UUID.randomUUID();
        f.followedId = UUID.randomUUID();
        f.status = FollowStatus.ACCEPTED;
        f.createdAt = LocalDateTime.now();

        FollowDTO dto = FollowDTO.from(f);
        assertEquals(42L, dto.id());
        assertEquals(f.followerId, dto.followerId());
        assertEquals(f.followedId, dto.followedId());
        assertEquals(FollowStatus.ACCEPTED, dto.status());
        assertEquals(f.createdAt, dto.createdAt());
    }
}
```

### 9.3 — `FollowServiceMock`

**Fichier :** `backend/src/test/java/ch/unige/events/service/FollowServiceMock.java` (nouveau)

Pattern strictement aligné sur [`FavoriteServiceMock`](backend/src/test/java/ch/unige/events/service/FavoriteServiceMock.java) :

```java
@Mock
@ApplicationScoped
public class FollowServiceMock extends FollowService {

    private final Map<Long, Follow> rowsById = new ConcurrentHashMap<>();
    private final AtomicLong idSequence = new AtomicLong(1);

    public static volatile boolean forceConflictOnFollow = false;
    public static volatile boolean forceUnprocessableOnFollow = false;
    public static volatile boolean forceForbiddenOnAccept = false;
    public static volatile boolean forceInvalidTransitionOnAccept = false;
    public static volatile boolean forceNotFoundOnTarget = false;
    public static volatile FollowStatus nextCreateStatus = FollowStatus.ACCEPTED;

    public void reset() {
        rowsById.clear();
        idSequence.set(1);
        forceConflictOnFollow = false;
        forceUnprocessableOnFollow = false;
        forceForbiddenOnAccept = false;
        forceInvalidTransitionOnAccept = false;
        forceNotFoundOnTarget = false;
        nextCreateStatus = FollowStatus.ACCEPTED;
    }

    public Follow seedFollow(UUID followerId, UUID followedId, FollowStatus status) {
        Follow f = new Follow();
        f.id = idSequence.getAndIncrement();
        f.followerId = followerId;
        f.followedId = followedId;
        f.status = status;
        f.createdAt = LocalDateTime.now();
        rowsById.put(f.id, f);
        return f;
    }

    @Override
    public Follow follow(String followerAuth0Id, UUID followedId) {
        if (forceUnprocessableOnFollow)
            throw FollowService.unprocessable("cannot_follow_self", "...");
        if (forceConflictOnFollow)
            throw FollowService.conflict("already_following", "...");
        if (forceNotFoundOnTarget)
            throw new NotFoundException("Target user not found");
        Follow f = seedFollow(UUID.randomUUID(), followedId, nextCreateStatus);
        return f;
    }

    @Override
    public void unfollow(String followerAuth0Id, UUID followedId) {
        // No-op idempotent
    }

    @Override
    public Follow acceptRequest(String targetAuth0Id, Long followId) {
        if (forceForbiddenOnAccept)
            throw FollowService.forbidden("forbidden", "...");
        Follow f = rowsById.get(followId);
        if (f == null) throw new NotFoundException();
        if (forceInvalidTransitionOnAccept)
            throw FollowService.conflict("invalid_transition", "...");
        f.status = FollowStatus.ACCEPTED;
        return f;
    }

    @Override
    public void rejectRequest(String targetAuth0Id, Long followId) {
        Follow f = rowsById.get(followId);
        if (f == null) throw new NotFoundException();
        rowsById.remove(followId);
    }

    @Override public long countFollowers(UUID userId) { return rowsById.values().stream()
            .filter(f -> f.followedId.equals(userId) && f.status == FollowStatus.ACCEPTED).count(); }
    @Override public long countFollowing(UUID userId) { return rowsById.values().stream()
            .filter(f -> f.followerId.equals(userId) && f.status == FollowStatus.ACCEPTED).count(); }
    @Override public FollowStatus getStatusBetween(UUID callerId, UUID targetId) {
        if (callerId == null || callerId.equals(targetId)) return null;
        return rowsById.values().stream()
                .filter(f -> f.followerId.equals(callerId) && f.followedId.equals(targetId))
                .map(f -> f.status).findFirst().orElse(null);
    }
    @Override public List<Follow> getFollowers(UUID userId, int page, int size) { /* filter+page */ }
    @Override public List<Follow> getFollowing(UUID userId, int page, int size) { /* filter+page */ }
    @Override public List<Follow> getPendingRequests(String auth0Id, int page, int size) { /* filter PENDING */ }
}
```

(Implémentation complète à l'identique du pattern `FavoriteServiceMock`, avec ces overrides.)

### 9.4 — `FollowResourceTest` (`@QuarkusTest`)

**Fichier :** `backend/src/test/java/ch/unige/events/resource/FollowResourceTest.java` (nouveau)

Pattern aligné sur `FavoriteResourceTest`. **Au minimum 18 tests** couvrant :

| # | Test | Sentinel ? | Endpoint |
|---|---|---|---|
| 1 | `follow_publicProfile_returnsAccepted_201` | ✅ | POST /users/{id}/follow |
| 2 | `follow_privateProfile_returnsPending_201` | ✅ | POST /users/{id}/follow |
| 3 | `follow_alreadyFollowing_returns409_alreadyFollowing` | ✅ | POST /users/{id}/follow |
| 4 | `follow_selfFollow_returns422_cannotFollowSelf` | ✅ | POST /users/{id}/follow |
| 5 | `follow_unknownTarget_returns404` | | POST /users/{id}/follow |
| 6 | `follow_unauthenticated_returns401` | | POST /users/{id}/follow |
| 7 | `unfollow_existingRow_returns204` | | DELETE /users/{id}/follow |
| 8 | `unfollow_noRow_returns204_idempotent` | ✅ | DELETE /users/{id}/follow |
| 9 | `unfollow_unauthenticated_returns401` | | DELETE /users/{id}/follow |
| 10 | `getFollowers_publicProfile_returns200WithList` | | GET /users/{id}/followers |
| 11 | `getFollowers_privateProfileNonOwner_returns404_antiOracle` | ✅ | GET /users/{id}/followers |
| 12 | `getFollowers_privateProfileOwner_returns200` | | GET /users/{id}/followers |
| 13 | `getFollowers_anon_returns401` | | GET /users/{id}/followers |
| 14 | `getFollowing_publicProfile_returns200WithList` | | GET /users/{id}/following |
| 15 | `getMyFollowRequests_returns200WithPendingRows` | | GET /users/me/follow-requests |
| 16 | `acceptFollowRequest_byNonTarget_returns403` | ✅ | PATCH /follow-requests/{id}/accept |
| 17 | `acceptFollowRequest_alreadyAccepted_returns409_invalidTransition` | ✅ | PATCH /follow-requests/{id}/accept |
| 18 | `acceptFollowRequest_unknownId_returns404` | | PATCH /follow-requests/{id}/accept |
| 19 | `rejectFollowRequest_pending_returns204` | ✅ | PATCH /follow-requests/{id}/reject |
| 20 | `rejectFollowRequest_byNonTarget_returns403` | | PATCH /follow-requests/{id}/reject |

Exemples de tests sentinels :

```java
@Test
@TestSecurity(user = "auth0|alice")
void follow_publicProfile_returnsAccepted_201() {
    UUID targetId = UUID.randomUUID();
    FollowServiceMock.nextCreateStatus = FollowStatus.ACCEPTED;

    given().when().post("/users/{id}/follow", targetId)
        .then()
        .statusCode(201)
        .body("status", is("ACCEPTED"))
        .body("followedId", is(targetId.toString()));
}

@Test
@TestSecurity(user = "auth0|alice")
void follow_alreadyFollowing_returns409_alreadyFollowing() {
    FollowServiceMock.forceConflictOnFollow = true;
    given().when().post("/users/{id}/follow", UUID.randomUUID())
        .then()
        .statusCode(409)
        .body("error", is("already_following"));
}

@Test
@TestSecurity(user = "auth0|alice")
void follow_selfFollow_returns422_cannotFollowSelf() {
    FollowServiceMock.forceUnprocessableOnFollow = true;
    given().when().post("/users/{id}/follow", UUID.randomUUID())
        .then()
        .statusCode(422)
        .body("error", is("cannot_follow_self"));
}

@Test
@TestSecurity(user = "auth0|bob")
void getFollowers_privateProfileNonOwner_returns404_antiOracle() {
    UUID privateId = UUID.randomUUID();
    UserServiceMock.seedPrivateUser(privateId, "auth0|alice");
    given().when().get("/users/{id}/followers", privateId)
        .then()
        .statusCode(404)
        .body("error", is("not_found"));
}
```

### 9.5 — `FollowServiceCoverageTest` (intégration DevServices PostgreSQL)

**Fichier :** `backend/src/test/java/ch/unige/events/service/FollowServiceCoverageTest.java` (nouveau)

`@QuarkusTest` avec vraie DB éphémère (DevServices). Tests couvrant le cycle de vie complet, la cascade auto-accept, les transitions PENDING → ACCEPTED, le 409 race-safe via la unique constraint.

**Au minimum 25 tests** dont les sentinels suivants nommément :

| # | Test | Couverture |
|---|---|---|
| 1 | `follow_publicProfile_persistsAcceptedRow` | Cascade auto-accept |
| 2 | `follow_privateProfile_persistsPendingRow` | Cascade PENDING |
| 3 | `follow_alreadyFollowing_throwsConflict` | Doublon applicatif |
| 4 | `follow_selfFollow_throwsUnprocessable` | 422 |
| 5 | `follow_targetNotFound_throwsNotFound` | 404 sur followedId |
| 6 | `follow_callerNotProvisioned_throwsNotFound` | 404 sur auth0Id |
| 7 | `follow_concurrentDuplicate_uniqueConstraintHits` | Race-safety (lance deux follows en parallèle, un doit perdre) |
| 8 | `unfollow_existingRow_deletesIt` | Suppression effective |
| 9 | `unfollow_noRow_isIdempotent` | Pas d'exception sur no-op |
| 10 | `acceptRequest_byTarget_setsAccepted` | Transition |
| 11 | `acceptRequest_byNonTarget_throwsForbidden` ✅ | 403 |
| 12 | `acceptRequest_alreadyAccepted_throwsInvalidTransition` ✅ | 409 |
| 13 | `acceptRequest_unknownId_throwsNotFound` | 404 |
| 14 | `rejectRequest_pending_deletesRow` ✅ | DELETE |
| 15 | `rejectRequest_byNonTarget_throwsForbidden` | 403 |
| 16 | `rejectRequest_alreadyAccepted_throwsInvalidTransition` | 409 |
| 17 | `rejectRequest_followerCanReFollowAfterReject` | Re-follow possible (cf. décision 5) |
| 18 | `countFollowers_returnsAcceptedOnly` | Compteurs PENDING ne comptent pas |
| 19 | `countFollowing_returnsAcceptedOnly` | Idem |
| 20 | `getStatusBetween_self_returnsNull` | Self-case |
| 21 | `getStatusBetween_pendingRow_returnsPending` | |
| 22 | `getStatusBetween_acceptedRow_returnsAccepted` | |
| 23 | `getStatusBetween_noRow_returnsNull` | |
| 24 | `findAcceptedFollowedIds_returnsOnlyAcceptedUuids` ✅ | **Sentinel SCRUM-168** : seed 2 ACCEPTED + 1 PENDING + 1 ACCEPTED inverse, vérifier que seul les 2 ACCEPTED sortants sortent. |
| 25 | `findAcceptedFollowedIds_emptyForUserWithNoFollows` | Liste vide non-null |
| 26 | `getFollowers_paginated_respectsPageAndSize` | Pagination DB |
| 27 | `getFollowers_excludesPendingRows` | Filtre status |
| 28 | `getPendingRequests_returnsOnlyPendingForTarget` | Filtre PENDING + cible |

Exemple test sentinel SCRUM-168 :

```java
@Test
void findAcceptedFollowedIds_returnsOnlyAcceptedUuids() {
    User alice = persistUser("auth0|alice", "alice@u.ch", true);
    User bob = persistUser("auth0|bob", "bob@u.ch", true);
    User carol = persistUser("auth0|carol", "carol@u.ch", false);
    User dave = persistUser("auth0|dave", "dave@u.ch", true);

    seedFollow(alice.id, bob.id, FollowStatus.ACCEPTED);
    seedFollow(alice.id, carol.id, FollowStatus.PENDING);   // PENDING — exclu
    seedFollow(alice.id, dave.id, FollowStatus.ACCEPTED);
    seedFollow(bob.id, alice.id, FollowStatus.ACCEPTED);    // inverse — exclu

    List<UUID> ids = Follow.findAcceptedFollowedIds(alice.id);
    assertThat(ids, containsInAnyOrder(bob.id, dave.id));
    assertEquals(2, ids.size());
}
```

### 9.6 — Mise à jour `UserServiceCoverageTest` et `UserResourceTest`

Cf. étape 8 — adapter à la nouvelle signature `PublicProfileView` + ajouter 4 nouveaux tests sur les compteurs et `followStatus`.

---

## Étape 10 — Documentation

**Règle d'or [`backend/AGENTS.md`](backend/AGENTS.md#L114) :** *« Si tu touches au code, tu touches à la doc correspondante dans le même commit. »*

### 10.1 — `backend/docs/data-model.md`

#### Nouvelle section `Follow`

À insérer **après** la section `EventCoOrganizer` (autour de la ligne 232) :

```markdown
### Follow

Table : `follows` (créée par la migration `V14__create_follows.sql` en SCRUM-138).

| Champ Java | Nom JSON | Type Java | Colonne DB | Contraintes |
|---|---|---|---|---|
| `id` | `id` | `Long` | `id` | PK, hérité de `PanacheEntity`, sequence `follows_seq` |
| `followerId` | `followerId` | `UUID` | `follower_id` | not null — FK `fk_follows_follower` → `users(id)` |
| `followedId` | `followedId` | `UUID` | `followed_id` | not null — FK `fk_follows_followed` → `users(id)` |
| `status` | `status` | `FollowStatus` | `status` | `@Enumerated(STRING)`, not null, `length=16`, CHECK constraint |
| `createdAt` | `createdAt` | `LocalDateTime` | `created_at` | `@Column(updatable=false)`, initialisé via `@PrePersist` |

Contrainte unique : `uq_follow_follower_followed` sur `(follower_id, followed_id)` —
empêche le double suivi et sert de filet de sécurité au check applicatif `409 already_following`.

CHECK constraints :
- `follows_status_check` (V14) : `status IN ('PENDING', 'ACCEPTED')`.

Index : `idx_follow_followed` (followed_id), `idx_follow_follower` (follower_id) —
support des compteurs `countFollowers` / `countFollowing` et des listings paginés.

**Pas de cascade FK** — un user supprimé laisse des rows orphelines (pattern défensif
identique à `Report.reporter`, à nettoyer par job ultérieur si nécessaire).

#### Sémantique du `REJECT`

`PATCH /follow-requests/{id}/reject` **supprime physiquement** la row au lieu de la marquer
`REJECTED`. La valeur `REJECTED` n'existe pas dans l'enum. Cette décision permet au
follower de re-tenter une demande après refus, sans 409 (la contrainte unique étant
strictement basée sur la présence d'une row, pas sur son statut). Pattern aligné sur
`EventCoOrganizer.DECLINE`.

#### Helpers statiques

- `Follow.findByFollowerAndFollowed(UUID, UUID)` — résolution unitaire pour follow/unfollow/cancel.
- `Follow.findFollowersOf(UUID, int, int)` — listing paginé ACCEPTED, tri `createdAt DESC, id DESC`.
- `Follow.findFollowingOf(UUID, int, int)` — idem côté following.
- `Follow.findPendingRequestsFor(UUID, int, int)` — inbox des demandes PENDING.
- `Follow.findAcceptedFollowedIds(UUID)` — projection JPQL directe (List<UUID>).
  **Anticipation SCRUM-168** : consommé par le filtre `followedOnly` du feed S9.
- `Follow.countFollowersOf(UUID)`, `Follow.countFollowingOf(UUID)` — compteurs ACCEPTED uniquement.

#### Consommation par `UserService.getPublicProfile`

`UserService.getPublicProfile(UUID id, String auth0Id)` retourne désormais un `PublicProfileView`
(record `(User user, long followerCount, long followingCount, FollowStatus followStatus)`) :

- Pour un appelant **anonyme** : `PublicProfileView.anonymous(user)` — court-circuit (compteurs 0,
  followStatus null), pas d'appel `FollowService` (économie 2 requêtes DB).
- Pour un appelant **authentifié** : compteurs réels via `FollowService.countFollowers/Following`,
  `followStatus` calculé via `FollowService.getStatusBetween(callerId, targetId)`.
- Sur son **propre profil** (auth0Id matche `user.auth0Id`) : `followStatus` reste `null` (un
  user ne peut pas se suivre — cf. SCRUM-138 décision 6).

La règle anti-oracle 404 ISSUE-93 reste inchangée : un profil privé non-owner jette `NotFoundException`
avant tout calcul de follow.

#### Consommation par `FollowResource`

7 endpoints (cf. `api-contract.md`). La règle anti-oracle 404 sur les listings followers/following
est portée par un appel préalable à `userService.getPublicProfile(...)` qui jette si non visible.
```

#### Mise à jour de la section `UserPublicResponse` (record)

Remplacer (autour ligne 351) :

```markdown
### UserPublicResponse (record)
Profil public — retourné via `GET /users/{id}` si `profilePublic = true`.

`id, displayName, faculty, studyLevel, bio, interests, avatarUrl, bannerUrl,
followerCount (long), followingCount (long), followStatus (FollowStatus | null)`

Trois factories :

- `UserPublicResponse.from(User u)` — legacy, compteurs à 0, followStatus null. Utilisée
  par les items de listes followers/following (cf. SCRUM-138 décision 11).
- `UserPublicResponse.from(User u, long followerCount, long followingCount, FollowStatus followStatus)`
  — factory enrichie utilisée par `UserResource.getProfile` pour les appelants authentifiés.
- `UserPublicResponse.fromAnonymous(User u)` — factory anonyme (ISSUE-93 finding 4.1b) ; tous les
  champs sensibles `null`, compteurs à 0, followStatus null.
```

#### Tableau Énumérations

Ajouter une ligne :

| `FollowStatus` | `PENDING`, `ACCEPTED` | Sprint 6 (SCRUM-138) | ✅ Implémenté — REJECT n'est pas un statut, traduit en DELETE physique de la row |

### 10.2 — `backend/docs/api-contract.md`

#### Tableau « Endpoints implémentés »

Ajouter 7 lignes (ordre alphabétique de path) :

| Méthode | Path | Auth | Description | Codes HTTP |
|---|---|---|---|---|
| `POST` | `/users/{id}/follow` | `@Authenticated` + `@PerUserRateLimit(max=30)` | Suivre un user (auto-accept si `profilePublic=true`, sinon PENDING) | 201, 401, 404, 409, 422, 429 |
| `DELETE` | `/users/{id}/follow` | `@Authenticated` | Se désabonner / annuler une demande (idempotent) | 204, 401 |
| `GET` | `/users/{id}/followers` | `@Authenticated` | Liste paginée des followers (404 anti-oracle si privé non-owner) | 200, 401, 404 |
| `GET` | `/users/{id}/following` | `@Authenticated` | Liste paginée des suivis | 200, 401, 404 |
| `GET` | `/users/me/follow-requests` | `@Authenticated` | Demandes PENDING reçues | 200, 401, 404 |
| `PATCH` | `/follow-requests/{followId}/accept` | `@Authenticated` | Accepter (target uniquement) | 200, 401, 403, 404, 409 |
| `PATCH` | `/follow-requests/{followId}/reject` | `@Authenticated` | Refuser et supprimer la row | 204, 401, 403, 404, 409 |

#### Section « Follow (SCRUM-138) »

À insérer entre « Co-organisateurs » et « Exception mappers » :

```markdown
### Follow (SCRUM-138)

Sept endpoints exposent la relation de suivi entre utilisateurs. Toutes les opérations sont
sous `@Authenticated`. Pas de privilège `ADMIN` (un admin doit suivre/se désabonner explicitement).

#### `POST /users/{id}/follow`

Crée une row Follow. Auto-accept si `profilePublic=true` côté cible, sinon PENDING.

**Réponses :**
- `201 Created` — `FollowDTO` (status reflétant la cascade auto-accept)
- `401 Unauthorized`
- `404 Not Found` — UUID cible inexistant ou profil caller non provisionné
- `409 Conflict` — `error=already_following`
- `422 Unprocessable Entity` — `error=cannot_follow_self`
- `429 Too Many Requests` — `@PerUserRateLimit(name="follows.follow", max=30)` dépassé

#### `DELETE /users/{id}/follow`

Idempotent — supprime la row peu importe son statut, ne lève pas 404 sur l'absence.

**Réponses :**
- `204 No Content`
- `401 Unauthorized`

#### `GET /users/{id}/followers` & `GET /users/{id}/following`

Listes paginées (`page`, `size` ; max 100). Items projetés via `UserPublicResponse.from(User)`
(compteurs et followStatus à 0/null sur les items — ces champs ne font sens que sur le profil
cible).

**Règle d'autorisation** (alignée ISSUE-93) :
- Profil cible `profilePublic=true` → 200 + liste paginée.
- Profil cible `profilePublic=false`, caller ≠ owner → `404 not_found` (envelope identique
  à un UUID inexistant — anti-oracle).
- Profil cible `profilePublic=false`, caller = owner → 200.

#### `GET /users/me/follow-requests`

Demandes PENDING reçues par l'utilisateur courant. `List<FollowDTO>` brut (le frontend
résoudra `GET /users/{followerId}` à la demande pour enrichir le rendu).

#### `PATCH /follow-requests/{followId}/accept`

Bascule PENDING → ACCEPTED. Réservé au `followed`.

**Réponses :**
- `200 OK` — `FollowDTO` mis à jour
- `401 Unauthorized`
- `403 Forbidden` — caller ≠ `followed`
- `404 Not Found` — `followId` inexistant
- `409 Conflict` — `error=invalid_transition` (déjà ACCEPTED)

#### `PATCH /follow-requests/{followId}/reject`

Refuse la demande PENDING — **supprime physiquement la row** (cf. data-model.md). Le
follower peut re-tenter ultérieurement sans 409. Réservé au `followed`.

**Réponses :**
- `204 No Content`
- `401 Unauthorized`
- `403 Forbidden`
- `404 Not Found`
- `409 Conflict` — `error=invalid_transition`
```

### 10.3 — `backend/docs/sprint-context.md`

Ajouter dans la section Sprint S6 (ou créer la sous-section si absente) :

```markdown
- **SCRUM-138** — Entité `Follow` + 7 endpoints follow / unfollow / demandes / listes
  ([SCRUM-138 Jira](https://pinfo-groupe6.atlassian.net/browse/SCRUM-138), 5 SP, Elie).
  - Migration V14 (`follows` table, FK vers `users`, contrainte unique sur le couple).
  - Enrichissement `UserPublicResponse` avec `followerCount`, `followingCount`, `followStatus`.
  - Finder `Follow.findAcceptedFollowedIds(UUID)` livré, anticipation SCRUM-168 (S9).
  - Notifications déléguées à SCRUM-140 (S7), hors scope.
```

---

## Ordre d'implémentation strict

Suivre **scrupuleusement** cet ordre. Toute déviation casse la règle openapi-first ou Hibernate `validate`.

1. **Branchement** (cf. workflow ci-dessous) — `git fetch origin && git checkout -b feature/s6-follow origin/main --no-track`.
2. **Étape 0 — `openapi/openapi.yaml`** : ajouter `FollowStatus`, `FollowDTO`, enrichir `UserPublicResponse`, ajouter les 7 paths. Vérifier la validité YAML (`python3 -c "import yaml; yaml.safe_load(open('openapi/openapi.yaml'))"`).
3. **Étape 1 — Migration V14** : créer `V14__create_follows.sql`. **Vérifier** d'abord avec `ls backend/src/main/resources/db/migration | sort` qu'aucune autre PR n'a pris V14 — basculer en V15 si oui.
4. **Étape 2 — Enum `FollowStatus`**.
5. **Étape 3 — Entité `Follow`** (avec tous les finders statiques, dont `findAcceptedFollowedIds` pour SCRUM-168).
6. **Étape 4 — DTOs** : `FollowDTO`, `PublicProfileView`.
7. **Étape 5 — `UserPublicResponse`** enrichi (3 nouveaux champs + nouvelle factory).
8. **Étape 6 — `FollowService`** + adaptation `UserService.getPublicProfile` (signature retournant `PublicProfileView`).
9. **Étape 6.3 — `UserResource.getProfile`** adapté.
10. **Étape 7 — `FollowResource`** (les 7 endpoints).
11. **Étape 8 — Adaptation `UserServiceMock`, `UserServiceCoverageTest`, `UserResourceTest`** (compat avec la nouvelle signature).
12. **Étape 9 — Tests neufs** : `FollowTest`, `FollowDTOTest`, `FollowServiceMock`, `FollowResourceTest`, `FollowServiceCoverageTest`.
13. **`./mvnw verify`** depuis `backend/` — DOIT être vert avant doc. Itérer jusqu'à JaCoCo ≥ 80 % sur les lignes nouvelles.
14. **Étape 10 — Documentation** dans le **même commit** que le code correspondant (ou commit `docs(scrum-138):` dédié si moins de 3 changements de doc).
15. **Vérifications finales avant push** :
    - `git diff --stat frontend/` strictement vide.
    - `git diff --stat openapi/` non-vide.
    - `git diff --stat backend/src/main/resources/db/migration/` contient `V14__create_follows.sql` (ou V15 selon).
    - Pas de nouvelle dépendance Maven (`pom.xml` inchangé hormis cas exceptionnel).
    - `./mvnw verify` vert. JaCoCo ≥ 80 % sur le diff. Sentinels listés en étape 9 tous verts nommément.
16. **Push, PR, review, CI** (cf. section workflow ci-dessous).

## Commits atomiques suggérés

(À regrouper si chaque sous-commit reste sous ~600 lignes de diff total — sinon les éclater.)

1. `feat(scrum-138): document Follow contract in openapi.yaml`
2. `feat(scrum-138): add Follow entity, FollowStatus enum and Flyway V14 migration`
3. `feat(scrum-138): add FollowService with follow/unfollow/accept/reject business rules`
4. `feat(scrum-138): expose 7 follow endpoints in FollowResource`
5. `feat(scrum-138): enrich UserPublicResponse with follower counts and follow status`
6. `test(scrum-138): cover entity, service, resource and SCRUM-168 finder`
7. `docs(scrum-138): document Follow data model, endpoints and sprint context`

Combinables si le diff total reste maîtrisé. Si tu groupes 2-3 commits, conserve un titre `feat(scrum-138): ...` cohérent.

## Push final

```bash
git push -u origin feature/s6-follow
```

---

## Workflow Git, PR, review Copilot, CI

Cette section est **obligatoire** pour l'agent qui implémentera la spec. Elle décrit la séquence shell exacte de bout en bout.

### 1. Branchement (au tout début de l'implémentation)

```bash
git fetch origin
git checkout -b feature/s6-follow origin/main --no-track
```

> **`--no-track` est OBLIGATOIRE.** Sans ce flag, la branche traque `origin/main` et un `git push` plus tard enverra les commits sur `main`. Incident historique évité par toutes les specs récentes du projet (cf. [`specs_scrum-94.md` décision 2](specs_archives/specs_claude/specs_scrum-94.md), [`specs_scrum-136.md` décision 1](specs_archives/specs_claude/specs_scrum-136.md)).

### 2. Implémentation atomique

Suivre l'ordre 1 → 16 ci-dessus. **Commit + push autorisés sur cette branche au fil des étapes.** Recommandé : un push après chaque commit atomique pour ne pas accumuler les écarts locaux.

```bash
# Exemple type pour le commit 1 (openapi-first) :
git add openapi/openapi.yaml
git commit -m "feat(scrum-138): document Follow contract in openapi.yaml"
git push -u origin feature/s6-follow      # première fois — pose le tracking
# Puis : git push (suffisant pour les commits suivants)
```

### 3. Vérification finale locale (avant d'ouvrir la PR)

Depuis `backend/` :

```bash
cd backend
./mvnw verify
```

**Doit être vert.** Inspection rapide :

```bash
# JaCoCo report (HTML) :
open target/site/jacoco/index.html       # ou xdg-open / start selon OS
# Couverture des fichiers SCRUM-138 doit être ≥ 80 %.

# Vérification des tests sentinels listés en étape 9 :
./mvnw test -Dtest='FollowResourceTest,FollowServiceCoverageTest,UserServiceCoverageTest'

# Vérification des invariants de diff :
cd ..
git diff --stat frontend/                 # DOIT être vide
git diff --stat openapi/                  # DOIT être non-vide
git diff --stat backend/src/main/resources/db/migration/   # DOIT contenir V14
git diff --stat backend/pom.xml           # devrait être vide (pas de nouvelle dépendance)
```

### 4. Ouverture de la PR

**Titre EXACT** (validé par `.github/workflows/pr-title-check.yml`) :

```
feat(scrum-138): add follow entity, endpoints and public profile counters
```

**Description** : suit strictement `.github/pull_request_template.md` (cf. section « Livrable FINAL attendu » plus bas — la version prête-à-coller est fournie).

```bash
gh pr create \
  --base main \
  --head feature/s6-follow \
  --title "feat(scrum-138): add follow entity, endpoints and public profile counters" \
  --body-file /tmp/pr-description-scrum-138.md
```

(Le fichier `/tmp/pr-description-scrum-138.md` contient la description prête fournie en fin de spec.)

### 5. Demander la review à Copilot

```bash
# Récupérer le numéro de PR :
PR_NUM=$(gh pr view --json number -q .number)

# Ajouter Copilot comme reviewer :
gh pr edit $PR_NUM --add-reviewer copilot-pull-request-reviewer
```

> **Si la commande échoue avec `unknown reviewer`**, le compte exact est `copilot-pull-request-reviewer[bot]` ou il faut passer par l'UI GitHub (sidebar « Reviewers » → cocher Copilot). Documenter l'écart si tu changes la méthode.

### 6. Analyse de la review Copilot (commentaire par commentaire)

Une fois la review postée par Copilot (peut prendre 1-5 minutes) :

```bash
# Lister tous les commentaires inline :
gh api "repos/:owner/:repo/pulls/${PR_NUM}/comments" --paginate \
    | jq '.[] | {id, path, line, body: (.body[0:200])}'

# Lister les "review comments" globaux (au niveau de la PR) :
gh api "repos/:owner/:repo/pulls/${PR_NUM}/reviews" --paginate \
    | jq '.[] | select(.user.login | test("copilot"; "i")) | {id, state, body: (.body[0:500])}'
```

**Pour chaque commentaire**, juger :

| Catégorie | Action |
|---|---|
| Pertinent (utile, factuel, aligné conventions) | Appliquer le fix dans un nouveau commit dédié `fix(scrum-138): <résumé>` + `git push`, puis répondre au commentaire avec un lien vers le commit |
| Incorrect / hors-scope / contraire à une décision tranchée de la spec | Répondre poliment au commentaire en justifiant pourquoi la remarque n'est pas appliquée. **Ne jamais ignorer silencieusement** — chaque commentaire mérite une réponse |
| Suggestion mineure de style sans impact | Appliquer **ou** répondre selon ton jugement (préférer appliquer si le fix coûte < 1 minute) |

Pour répondre :

```bash
# Reply à un commentaire inline (id = commentId Copilot) :
gh api -X POST "repos/:owner/:repo/pulls/${PR_NUM}/comments/${COMMENT_ID}/replies" \
    -F body='Merci pour la remarque — appliqué dans <commit-sha>.'

# Ou commenter au niveau PR :
gh pr comment $PR_NUM --body "Réponse globale à la review : ..."
```

### 7. Surveillance CI

```bash
# Lister tous les checks de la PR :
gh pr checks $PR_NUM

# Surveillance live :
gh pr checks $PR_NUM --watch          # bloque jusqu'à conclusion de tous les checks
```

**Checks attendus :**
- `Lint PR title` (`.github/workflows/pr-title-check.yml`)
- `build / build-test` (Maven verify côté backend)
- `frontend / build-test` (devrait être no-op vu qu'aucun fichier `frontend/` n'est touché — vérifier que le diff frontend est bien vide)
- `SonarCloud Quality Gate` (couverture ≥ 80 %, duplication < 3 %, ratings A)

**Si une check échoue** :

```bash
# Identifier le run en échec :
gh run list --branch feature/s6-follow --limit 5

# Lire les logs failed :
gh run view <RUN_ID> --log-failed

# Corriger la cause **racine** (pas de --no-verify, pas de skip, pas de @Disabled).
# Commit le fix + push :
git add <files>
git commit -m "fix(scrum-138): <résumé du fix CI>"
git push

# Relancer la surveillance :
gh pr checks $PR_NUM --watch
```

Itérer jusqu'à ce que **toutes** les checks soient vertes ET que le Quality Gate Sonar soit vert.

### 8. Ne PAS merger

**L'utilisateur mergera lui-même** la PR après validation finale. Ne pas appeler `gh pr merge`.

---

## Critères de done

À la fin de l'implémentation, **tous** ces points doivent être vrais :

- [ ] `feature/s6-follow` créée depuis `origin/main` avec `--no-track`.
- [ ] `openapi/openapi.yaml` modifié EN PREMIER (commit 1) — schémas `FollowStatus`, `FollowDTO`, enrichissement `UserPublicResponse`, 7 paths.
- [ ] `V14__create_follows.sql` (ou V15 si conflit) présent et correctement nommé.
- [ ] Entité `Follow` avec finders statiques dont **`findAcceptedFollowedIds`**.
- [ ] Enum `FollowStatus` avec 2 valeurs `PENDING`, `ACCEPTED`.
- [ ] `FollowService` avec helpers `badRequest/conflict/unprocessable/forbidden` et règles métier 409 / 422 / 403 / 404 conformes à la spec.
- [ ] `FollowResource` avec 7 endpoints, `@Authenticated`, `@PerUserRateLimit` sur POST follow uniquement.
- [ ] `UserService.getPublicProfile(...)` retourne désormais `PublicProfileView`.
- [ ] `UserResource.getProfile(...)` adapté à la nouvelle signature, court-circuit anonyme préservé.
- [ ] `UserPublicResponse` enrichi avec 3 nouveaux champs + 3 factories (`from(User)`, `from(User, long, long, FollowStatus)`, `fromAnonymous(User)`).
- [ ] `UserServiceMock` adapté à la nouvelle signature.
- [ ] `UserServiceCoverageTest` et `UserResourceTest` adaptés ; 4 nouveaux tests sur compteurs/followStatus.
- [ ] `./mvnw verify` vert localement et en CI.
- [ ] **Tests sentinels verts nommément** :
  - `follow_publicProfile_returnsAccepted_201`
  - `follow_privateProfile_returnsPending_201`
  - `follow_alreadyFollowing_returns409_alreadyFollowing`
  - `follow_selfFollow_returns422_cannotFollowSelf`
  - `unfollow_noRow_returns204_idempotent`
  - `getFollowers_privateProfileNonOwner_returns404_antiOracle`
  - `acceptFollowRequest_byNonTarget_returns403`
  - `acceptFollowRequest_alreadyAccepted_returns409_invalidTransition`
  - `rejectFollowRequest_pending_returns204`
  - `findAcceptedFollowedIds_returnsOnlyAcceptedUuids` (sentinel SCRUM-168)
  - `getPublicProfile_self_followStatusIsNull`
  - `getPublicProfile_authNonOwnerWithPending_followStatusIsPending`
- [ ] JaCoCo ≥ 80 % sur les lignes nouvelles ; idéalement 100 % sur `FollowService` et `FollowResource`.
- [ ] Duplication < 3 %, ratings A (Security, Reliability, Maintainability).
- [ ] `git diff --stat frontend/` strictement vide.
- [ ] `git diff --stat openapi/` non-vide.
- [ ] `git diff --stat backend/src/main/resources/db/migration/` contient V14 (ou V15).
- [ ] `backend/docs/data-model.md`, `backend/docs/api-contract.md`, `backend/docs/sprint-context.md` mis à jour dans le même commit que le code (ou commit `docs(scrum-138):` dédié).
- [ ] Commits atomiques bien nommés selon le format `feat(scrum-138):` / `test(scrum-138):` / `docs(scrum-138):` / `fix(scrum-138):`.
- [ ] `git branch -vv` confirme que la branche track `origin/feature/s6-follow`.
- [ ] La check CI `Lint PR title` est verte.
- [ ] **Toutes** les autres checks CI (build backend, build frontend no-op, Sonar) sont vertes.
- [ ] SonarCloud Quality Gate vert.
- [ ] Review Copilot demandée et **chaque commentaire traité** (appliqué OU répondu).
- [ ] Titre EXACT de PR : `feat(scrum-138): add follow entity, endpoints and public profile counters`
- [ ] Description PR conforme au template, prête à coller, contenant les sections : Résumé / Why / Changements / Tests / Test plan / Documentation / Dépendances / Décisions techniques tranchées / Notes pour le reviewer.
- [ ] **PR PAS mergée par l'agent** — l'utilisateur merge lui-même après validation.

---

## Livrable FINAL attendu (à fournir à l'utilisateur dans la réponse finale)

**OBLIGATOIRE — sans ces deux blocs, la tâche n'est PAS terminée.**

### 1. Titre EXACT de la PR

```
feat(scrum-138): add follow entity, endpoints and public profile counters
```

(Validé par `.github/workflows/pr-title-check.yml`. Format `feat(scrum-138)` minuscules. Description impérative anglaise concise sous 70 caractères.)

### 2. Description COMPLÈTE de la PR

À copier-coller dans le textarea GitHub. Suit strictement `.github/pull_request_template.md`.

```markdown
## Résumé

**SCRUM-138** — Ajoute l'entité `Follow`, 7 endpoints REST (follow / unfollow / accept / reject / listes / inbox demandes) et enrichit `UserPublicResponse` avec `followerCount`, `followingCount`, `followStatus`. Socle backend du graphe social qui débloque SCRUM-141/142/143 (front S7) et SCRUM-168 (filtre `followedOnly` du feed S9).

## Why / Motivation

US-20 (consulter le profil public d'un utilisateur) et US-21 (suivre / demande de suivi sur profil privé) — épic SCRUM-13. Aujourd'hui, le frontend ne peut afficher ni compteurs followers/following ni bouton « Suivre / Demande envoyée / Abonné ». Cette PR livre **uniquement le backend** ; les notifications de follow restent SCRUM-140 (S7) et le filtre de feed `followedOnly` reste SCRUM-168 (S9). Le finder statique `Follow.findAcceptedFollowedIds(UUID)` est livré ici pour anticiper SCRUM-168.

## Changements

### Backend

- Nouvelle entité `Follow` (PanacheEntity, table de jointure `(followerId UUID, followedId UUID, status FollowStatus, createdAt)`) ([`Follow.java`](backend/src/main/java/ch/unige/events/entity/Follow.java)) avec finders statiques dont `findAcceptedFollowedIds` (anticipation SCRUM-168).
- Nouvel enum `FollowStatus` (`PENDING`, `ACCEPTED` — pas de `REJECTED` stocké, reject = DELETE row) ([`FollowStatus.java`](backend/src/main/java/ch/unige/events/entity/FollowStatus.java)).
- Nouveau `FollowService` (`@ApplicationScoped`) avec règles métier : auto-accept si profil cible public, PENDING sinon, 409 `already_following`, 422 `cannot_follow_self`, 403 sur accept/reject par non-target, 409 `invalid_transition` sur transition non-PENDING, DELETE idempotent ([`FollowService.java`](backend/src/main/java/ch/unige/events/service/FollowService.java)).
- Nouveau `FollowResource` exposant les 7 endpoints sous `@Authenticated` (avec `@PerUserRateLimit(name="follows.follow", max=30)` sur POST follow) ([`FollowResource.java`](backend/src/main/java/ch/unige/events/resource/FollowResource.java)) :
  - `POST /api/users/{id}/follow` — 201 + FollowDTO
  - `DELETE /api/users/{id}/follow` — 204 idempotent
  - `GET /api/users/{id}/followers` — 200 + List<UserPublicResponse> (404 anti-oracle si profil privé non-owner)
  - `GET /api/users/{id}/following` — idem
  - `GET /api/users/me/follow-requests` — 200 + List<FollowDTO> PENDING
  - `PATCH /api/follow-requests/{followId}/accept` — 200 + FollowDTO
  - `PATCH /api/follow-requests/{followId}/reject` — 204 (supprime physiquement la row)
- Enrichissement `UserPublicResponse` (3 nouveaux champs : `followerCount: long`, `followingCount: long`, `followStatus: FollowStatus | null`). 3 factories distinctes : `from(User)` legacy / `from(User, long, long, FollowStatus)` enrichie / `fromAnonymous(User)` (compteurs 0, status null).
- `UserService.getPublicProfile(UUID, String)` retourne désormais `PublicProfileView` (record agrégé). Court-circuit anonyme (pas d'appel `FollowService`). Règle anti-oracle 404 ISSUE-93 préservée.
- `FollowDTO` (record `id, followerId, followedId, status, createdAt`) ([`FollowDTO.java`](backend/src/main/java/ch/unige/events/dto/follow/FollowDTO.java)).
- `PublicProfileView` (record `user, followerCount, followingCount, followStatus`) ([`PublicProfileView.java`](backend/src/main/java/ch/unige/events/dto/user/PublicProfileView.java)).

### Infrastructure

- Migration Flyway [`V14__create_follows.sql`](backend/src/main/resources/db/migration/V14__create_follows.sql) : `CREATE SEQUENCE follows_seq`, `CREATE TABLE follows`, contrainte unique `uq_follow_follower_followed`, FK vers `users(id)` (sans cascade), CHECK constraint `follows_status_check`, index sur `follower_id` et `followed_id`.

### Documentation

- [`backend/docs/data-model.md`](backend/docs/data-model.md) — section `Follow` (champs, finders, sémantique du REJECT, consommation par UserService) + `FollowStatus` au tableau Énumérations + mise à jour de `UserPublicResponse` (3 nouveaux champs).
- [`backend/docs/api-contract.md`](backend/docs/api-contract.md) — 7 lignes ajoutées dans le tableau « Endpoints implémentés » + section dédiée « Follow (SCRUM-138) ».
- [`backend/docs/sprint-context.md`](backend/docs/sprint-context.md) — entrée SCRUM-138 dans le sprint S6.
- [`openapi/openapi.yaml`](openapi/openapi.yaml) — schémas `FollowStatus`, `FollowDTO`, enrichissement `UserPublicResponse`, 7 paths.

## Tests

- `FollowTest` — entité (3 tests : assignabilité, prePersist).
- `FollowDTOTest` — factory `from(Follow)`.
- `FollowServiceMock` — pattern aligné `FavoriteServiceMock`, support des 4 flags `force*` pour les tests Resource.
- `FollowResourceTest` — 18+ tests `@QuarkusTest` couvrant 201/204/401/403/404/409/422.
- `FollowServiceCoverageTest` — 25+ tests intégration DevServices PostgreSQL incluant la sentinel `findAcceptedFollowedIds_returnsOnlyAcceptedUuids` pour SCRUM-168 et un test de race-safety sur la unique constraint.
- `UserServiceCoverageTest` — 4 nouveaux tests sur `followerCount`, `followingCount`, `followStatus` (self / no-relation / PENDING / ACCEPTED).
- `UserResourceTest` — 3 tests d'assertion sur les nouveaux champs du body.

JaCoCo ≥ 80 % sur les lignes nouvelles ; ~100 % sur `FollowService` et `FollowResource`.

## Test plan

- [ ] `cd backend && ./mvnw verify` — vert.
- [ ] JaCoCo : couverture ≥ 80 % sur les fichiers SCRUM-138.
- [ ] `git diff --stat frontend/` vide.
- [ ] Smoke manuel (DevServices) :
  - [ ] `POST /api/users/{publicUuid}/follow` avec un user public → 201, status `ACCEPTED`.
  - [ ] `POST /api/users/{privateUuid}/follow` avec un user privé → 201, status `PENDING`.
  - [ ] Re-POST → 409 `already_following`.
  - [ ] `POST /api/users/{selfUuid}/follow` → 422 `cannot_follow_self`.
  - [ ] `GET /api/users/{publicUuid}` (authentifié, autre user) → `followerCount`, `followingCount`, `followStatus` cohérents.
  - [ ] `GET /api/users/{privateUuid}/followers` (auth, non-owner) → 404 `not_found`.
  - [ ] `PATCH /api/follow-requests/{id}/accept` par un non-target → 403.
- [ ] `gh pr checks` — toutes vertes.
- [ ] SonarCloud Quality Gate vert.

## Documentation

- [x] Documentation mise à jour : `backend/docs/data-model.md`, `backend/docs/api-contract.md`, `backend/docs/sprint-context.md`, `openapi/openapi.yaml`.

## Dépendances / ordre de merge

Aucune dépendance amont. **Cette PR débloque** : SCRUM-141 (page profil S7), SCRUM-142 (FollowButton + panneau demandes S7), SCRUM-143 (modale listes S7), SCRUM-168 (filtre `followedOnly` S9 — consomme `Follow.findAcceptedFollowedIds`). SCRUM-140 (notifications follow S7) viendra brancher des side-effects sur `FollowService.follow()` / `acceptRequest()` une fois l'infra Notification (SCRUM-99) livrée.

## Décisions techniques tranchées

- **UUID brut** sur `Follow.followerId` / `followedId` (pas `@ManyToOne User`) — cohérent avec `Favorite`, `Attendance`, `EventCoOrganizer`.
- **REJECT = DELETE physique de la row** (pas de statut `REJECTED` stocké) — pattern `EventCoOrganizer.DECLINE`. Permet une re-tentative ultérieure sans 409.
- **DELETE follow idempotent** (204 même si rien à supprimer) — UX cohérente côté SCRUM-142.
- **Self-follow → 422 `cannot_follow_self`** (pattern `cannot_report_own_event` SCRUM-94).
- **Doublon → 409 `already_following`** (check applicatif + filet de sécurité unique constraint, pattern SCRUM-94).
- **Listes followers/following sous `@Authenticated`** + **404 anti-oracle si profil privé non-owner** — protection harvest GDPR (alignement ISSUE-93).
- **Compteurs calculés à la volée** (pas de cache DB) — MVP simple, à optimiser uniquement si profilage le demande.
- **`UserService.getPublicProfile` retourne `PublicProfileView`** (record agrégé) — sépare le calcul (Service) de la projection (Resource), sans exposer `FollowService` à la Resource.
- **Notifications hors scope** — SCRUM-140 (S7) une fois SCRUM-99 livré.
- **Bascule `profilePublic` privé → public n'auto-accepte PAS les PENDING existants** — privacy par défaut, follow-up séparé si besoin produit.
- **`Follow.findAcceptedFollowedIds(UUID)` livré ici** — anticipation SCRUM-168, sentinel de test dédié.

## Notes pour le reviewer

- L'enrichissement de `UserPublicResponse` change la **forme du body** retourné par `GET /api/users/{id}`. Trois nouveaux champs apparaîtront dans toutes les réponses — vérifier l'absence d'effet de bord côté frontend (qui ne devrait rien casser car les champs sont simplement ajoutés). `git diff --stat frontend/` vide confirme qu'aucun consommateur frontend n'est touché dans cette PR.
- La signature de `UserService.getPublicProfile` change (retour `User` → `PublicProfileView`). Adapté côté Mock + tests existants — vérifier que tous les tests passent.
- La règle anti-oracle 404 d'ISSUE-93 est préservée à l'identique. Les nouveaux endpoints `/followers` et `/following` la **respectent** en passant par `userService.getPublicProfile(...)` en garde — zone à regarder en review (zoom sur `FollowResource.getFollowers/getFollowing`).
- Pas de cascade FK `ON DELETE` sur `follows.follower_id` / `followed_id` — pattern défensif assumé. Un job futur de soft-delete d'un compte devra nettoyer ces rows orphelines.
- `@PerUserRateLimit(name="follows.follow", max=30)` sur POST follow — éviter le mass-follow scripté. Pas de rate limit sur unfollow / accept / reject (faible valeur attaquant).
```

---

## Interdits stricts

- ❌ PAS de modification frontend (`git diff --stat frontend/` doit être strictement vide).
- ❌ PAS de modification des migrations V1..V13 (immutables).
- ❌ PAS de notification émise (Quarkus event, Notification entity, etc.).
- ❌ PAS de bypass `@RolesAllowed("ADMIN")` sur les endpoints follow.
- ❌ PAS d'auto-accept au passage de `profilePublic` false → true.
- ❌ PAS de cascade `ON DELETE` sur les FK de `follows`.
- ❌ PAS de soft-delete `Follow.deletedAt` — DELETE physique sur unfollow et reject.
- ❌ PAS de stocker un statut `REJECTED` (reject = DELETE row).
- ❌ PAS de bulk endpoint, pas de search, pas de mass-accept.
- ❌ PAS de log INFO sur les follows/unfollows (privacy).
- ❌ PAS de TODO commenté dans le code livré.
- ❌ PAS d'extraction préventive de `resolveUserId` en util statique partagée — duplication acceptable dans cette PR.
- ❌ PAS de `--no-verify`, pas de `@Disabled`, pas de skip de check CI sous prétexte de fix « ultérieur ».
- ❌ PAS de force-push sur `feature/s6-follow` pendant la review (utiliser des commits additifs).
- ❌ PAS de merge de la PR par l'agent — l'utilisateur s'en charge.

## Conventions à respecter

- camelCase partout en Java, JSON, OpenAPI. Hibernate convertit en snake_case côté DB.
- Pas de préfixe `is` sur les booléens (n/a — aucun nouveau booléen ajouté).
- Constructor injection ou `@Inject` direct (pattern existant) sur `FollowResource` — homogène avec les autres Resources du projet.
- `@Transactional` sur toutes les **mutations** Service ; lectures non-transactionnelles (cf. décision 23).
- `@Authenticated` sur les 7 endpoints Follow (par méthode ; pas d'annotation classe-niveau car le path racine `/` est partagé).
- `@PerUserRateLimit(name="follows.follow", max=30)` uniquement sur POST follow.
- `@PathParam UUID id` pour les paths `/users/{id}/...`, `@PathParam Long followId` pour `/follow-requests/{followId}/...`.
- Pagination identique au reste du projet : `@DefaultValue("0") @Min(0) page`, `@DefaultValue("20") @Positive @Max(100) size`.
- Codes d'erreur custom dans le champ `error` de l'envelope `ApiErrorResponse` : `cannot_follow_self`, `already_following`, `invalid_transition`, `forbidden`. Codes 4xx/5xx standards pour les autres.
- Couverture JaCoCo ≥ 80 % sur les lignes nouvelles, duplication < 3 %, ratings A.
- Doc mise à jour dans le **même commit** que le code correspondant (ou commit `docs(scrum-138):` dédié).
- Commits atomiques `feat(scrum-138): …`, `test(scrum-138): …`, `docs(scrum-138): …`, `fix(scrum-138): …`.
- Titre PR EXACT : `feat(scrum-138): add follow entity, endpoints and public profile counters`.

---

## Prompt de lancement d'implémentation

```
Tu vas implémenter la feature SCRUM-138 du projet UNIGE Events. La spec d'implémentation complète et figée vit dans `specs_archives/specs_claude/specs_scrum-138.md` — c'est la **source unique de vérité**. Toute déviation par rapport à cette spec doit être justifiée auprès de l'utilisateur AVANT exécution.

## Contexte projet à relire AVANT d'écrire la moindre ligne

1. `AGENTS.md`, `backend/AGENTS.md`, `backend/CLAUDE.md` — règles d'or projet (openapi-first, Flyway immutable, camelCase, pas de préfixe `is`, conventions PR).
2. `backend/docs/data-model.md`, `backend/docs/api-contract.md`, `backend/docs/sprint-context.md`, `backend/docs/architecture.md`, `openapi/openapi.yaml`.
3. `specs_archives/specs_claude/specs_scrum-138.md` — la spec, intégralement.

## Ce que tu vas faire

Implémenter SCRUM-138 selon la spec. Étapes dans cet ordre strict (cf. spec section « Ordre d'implémentation strict ») :

1. Branchement : `git fetch origin && git checkout -b feature/s6-follow origin/main --no-track` (`--no-track` OBLIGATOIRE).
2. **Étape 0** — Modifier `openapi/openapi.yaml` EN PREMIER (schémas `FollowStatus`, `FollowDTO`, enrichissement `UserPublicResponse`, 7 paths). Vérifier la validité YAML.
3. **Étape 1** — Vérifier que `V14` est libre (`ls backend/src/main/resources/db/migration | sort`), créer `V14__create_follows.sql` (basculer en V15 si conflit et adapter toutes les références).
4. **Étape 2** — Enum `FollowStatus` (`PENDING`, `ACCEPTED`).
5. **Étape 3** — Entité `Follow` (PanacheEntity, finders statiques dont `findAcceptedFollowedIds` pour SCRUM-168).
6. **Étape 4** — DTOs `FollowDTO` et `PublicProfileView`.
7. **Étape 5** — Enrichir `UserPublicResponse` (3 nouveaux champs + 3 factories).
8. **Étape 6** — `FollowService` + adaptation `UserService.getPublicProfile` (retour `PublicProfileView`) + adaptation `UserResource.getProfile`.
9. **Étape 7** — `FollowResource` (7 endpoints, `@Authenticated`, `@PerUserRateLimit` sur POST follow uniquement).
10. **Étape 8** — Adapter `UserServiceMock`, `UserServiceCoverageTest`, `UserResourceTest` à la nouvelle signature ; ajouter 4 nouveaux tests sur compteurs/followStatus.
11. **Étape 9** — Tests neufs : `FollowTest`, `FollowDTOTest`, `FollowServiceMock`, `FollowResourceTest`, `FollowServiceCoverageTest`. Sentinels listés en spec étape 9 doivent tous être verts nommément.
12. **Étape 10** — Mettre à jour `backend/docs/data-model.md`, `backend/docs/api-contract.md`, `backend/docs/sprint-context.md` dans le **même commit** que le code (ou commit `docs(scrum-138):` dédié).

À chaque étape, commit + push autorisés (et recommandés). Format des commits : `feat(scrum-138): …`, `test(scrum-138): …`, `docs(scrum-138): …`, `fix(scrum-138): …`.

## Vérification finale locale (avant ouverture PR)

```bash
cd backend && ./mvnw verify
```

Doit être vert. Inspecter JaCoCo (`target/site/jacoco/index.html`) — couverture ≥ 80 % sur le diff. Vérifier les invariants :
- `git diff --stat frontend/` strictement VIDE.
- `git diff --stat openapi/` non-vide.
- `git diff --stat backend/src/main/resources/db/migration/` contient V14 (ou V15).

## Workflow PR / Copilot / CI

1. Ouvrir la PR avec **titre EXACT** : `feat(scrum-138): add follow entity, endpoints and public profile counters` (validé par `.github/workflows/pr-title-check.yml`).
2. Description PR : copier-coller le bloc fourni dans la spec section « Livrable FINAL attendu » — respecte strictement `.github/pull_request_template.md`.
3. Demander la review à Copilot : `gh pr edit <PR_NUM> --add-reviewer copilot-pull-request-reviewer`.
4. Pour CHAQUE commentaire de Copilot :
   - Récupérer via `gh api repos/:owner/:repo/pulls/<PR_NUM>/comments --paginate`.
   - Juger pertinence (alignement avec les conventions projet et les décisions tranchées de la spec).
   - Si pertinent → corriger dans un commit `fix(scrum-138): …` + push + répondre au commentaire avec un lien vers le commit.
   - Si non-pertinent → répondre poliment en justifiant pourquoi la remarque n'est pas appliquée.
   - **Ne jamais ignorer silencieusement un commentaire.**
5. Surveiller la CI : `gh pr checks <PR_NUM> --watch`. Si une check échoue, lire les logs (`gh run view <RUN_ID> --log-failed`), corriger la cause **racine** (PAS de `--no-verify`, PAS de skip), commit + push, surveiller à nouveau jusqu'à ce que **toutes** les checks soient vertes ET que le Quality Gate Sonar soit vert.
6. **Ne PAS merger** la PR — l'utilisateur s'en charge après validation finale.

## Conventions à respecter (rappel critique)

- camelCase partout (Java, JSON, OpenAPI) ; pas de préfixe `is` sur les booléens.
- openapi-first : modifier `openapi/openapi.yaml` AVANT le code.
- Flyway immutable : ne jamais modifier V1..V13.
- `@Transactional` sur les mutations Service ; pas sur les lectures.
- `@Authenticated` sur les 7 endpoints Follow ; `@PerUserRateLimit(name="follows.follow", max=30)` sur POST follow uniquement.
- JaCoCo ≥ 80 % sur le diff, duplication < 3 %, ratings A.
- Doc mise à jour dans le même commit que le code correspondant.

## Interdits

- ❌ Aucune modification frontend (`git diff --stat frontend/` strictement vide).
- ❌ Aucune notification émise (Quarkus event, Notification entity, etc.) — déléguées à SCRUM-140 (S7).
- ❌ Pas de bypass `@RolesAllowed("ADMIN")` sur les endpoints Follow.
- ❌ Pas d'auto-accept des PENDING au passage `profilePublic` false → true.
- ❌ Pas de modification des migrations V1..V13.
- ❌ Pas de cascade `ON DELETE` sur les FK de `follows`.
- ❌ Pas de soft-delete `Follow.deletedAt` — DELETE physique sur unfollow et reject.
- ❌ Pas de stocker un statut `REJECTED` (reject = DELETE row).
- ❌ Pas de bulk endpoint, pas de search.
- ❌ Pas de log INFO sur les actions follow.
- ❌ Pas de TODO commenté dans le code livré.
- ❌ Pas de `--no-verify`, pas de `@Disabled`, pas de skip de check CI.
- ❌ Pas de force-push sur la branche pendant la review.
- ❌ Pas de merge de la PR par toi — l'utilisateur s'en charge.

## Critères de done

- [ ] `feature/s6-follow` créée depuis `origin/main` avec `--no-track`.
- [ ] `openapi/openapi.yaml` modifié EN PREMIER (commit 1).
- [ ] `V14__create_follows.sql` (ou V15) présent et correctement nommé.
- [ ] Entité `Follow` avec finders dont `findAcceptedFollowedIds`.
- [ ] Enum `FollowStatus` avec `PENDING`, `ACCEPTED` uniquement.
- [ ] `FollowService` avec règles métier 409 / 422 / 403 / 404 conformes à la spec.
- [ ] `FollowResource` avec 7 endpoints, `@Authenticated`, `@PerUserRateLimit` sur POST follow uniquement.
- [ ] `UserService.getPublicProfile` retourne `PublicProfileView`.
- [ ] `UserResource.getProfile` adapté ; `UserPublicResponse` enrichi avec 3 nouveaux champs + 3 factories.
- [ ] `UserServiceMock`, `UserServiceCoverageTest`, `UserResourceTest` adaptés ; 4 nouveaux tests sur compteurs/followStatus.
- [ ] `./mvnw verify` vert localement et en CI.
- [ ] Sentinels verts nommément :
  - `follow_publicProfile_returnsAccepted_201`
  - `follow_privateProfile_returnsPending_201`
  - `follow_alreadyFollowing_returns409_alreadyFollowing`
  - `follow_selfFollow_returns422_cannotFollowSelf`
  - `unfollow_noRow_returns204_idempotent`
  - `getFollowers_privateProfileNonOwner_returns404_antiOracle`
  - `acceptFollowRequest_byNonTarget_returns403`
  - `acceptFollowRequest_alreadyAccepted_returns409_invalidTransition`
  - `rejectFollowRequest_pending_returns204`
  - `findAcceptedFollowedIds_returnsOnlyAcceptedUuids` (sentinel SCRUM-168)
  - `getPublicProfile_self_followStatusIsNull`
  - `getPublicProfile_authNonOwnerWithPending_followStatusIsPending`
- [ ] JaCoCo ≥ 80 % sur les lignes nouvelles ; idéalement 100 % sur `FollowService` et `FollowResource`.
- [ ] Duplication < 3 %, ratings A (Security, Reliability, Maintainability).
- [ ] `git diff --stat frontend/` strictement vide.
- [ ] `git diff --stat openapi/` non-vide.
- [ ] `git diff --stat backend/src/main/resources/db/migration/` contient V14 (ou V15).
- [ ] `backend/docs/data-model.md`, `backend/docs/api-contract.md`, `backend/docs/sprint-context.md` mis à jour.
- [ ] Commits atomiques bien nommés.
- [ ] `git branch -vv` confirme tracking sur `origin/feature/s6-follow`.
- [ ] La check CI `Lint PR title` est verte.
- [ ] Toutes les autres checks CI (build backend, build frontend no-op, Sonar) sont vertes.
- [ ] SonarCloud Quality Gate vert.
- [ ] Review Copilot demandée et **chaque commentaire traité** (appliqué OU répondu).
- [ ] Titre EXACT de PR : `feat(scrum-138): add follow entity, endpoints and public profile counters`.
- [ ] Description PR conforme au template, prête à coller, contenant les sections : Résumé / Why / Changements / Tests / Test plan / Documentation / Dépendances / Décisions techniques tranchées / Notes pour le reviewer.
- [ ] **PR PAS mergée par toi** — l'utilisateur merge lui-même après validation.

Procède maintenant. Reporte ton avancement à chaque étape complétée.
```
