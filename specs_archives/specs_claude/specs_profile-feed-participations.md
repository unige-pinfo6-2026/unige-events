# SPEC — Corrections profil / feed + participations publiques

| Champ | Valeur |
|---|---|
| Périmètre | 4 correctifs UX/fonctionnels post-S9 : layout profil, skeleton du feed, filtre « Mes abonnements », participations publiques |
| Branche proposée | `feature/profile-feed-participations-fixes` (cible PR : `main`) |
| Base | `origin/main` — tip à la rédaction : `65d633f9 docs(security): add UNIGE Events penetration test report` |
| Sprint | S9/S10 (correctifs de finition après PR #195 SCRUM-116 + PR #196 SCRUM-168) |
| Tickets liés | SCRUM-116 (feed, PR #195 mergé 2026-05-20), SCRUM-168 (`followedOnly`, PR #196 mergé 2026-05-21), SCRUM-141 (profil public), SCRUM-110 (follow), SCRUM-137 (co-organisateurs) |
| Auteur spec | Elie Bussod (rédaction assistée Claude Opus 4.7) |
| Date | 2026-05-22 |
| Règle d'or `openapi-first` | **APPLICABLE au seul Problème 5** (nouveau path public `GET /users/{id}/participations`). Problèmes 1, 2/3, 4 = **frontend pur, aucun changement de contrat**. |

> **IMPÉRATIF de livrable** : cette spec est **spec-only**. L'implémentation ultérieure produira un **seul** flux de travail ; aucune autre spec ne doit être créée. Tous les chemins ci-dessous sont **vérifiés** sur la structure microservices réelle (post-PR #158) — ne pas réutiliser les chemins monolithe `backend/src/main/...` des vieilles specs (obsolètes).

---

## 1. Vue d'ensemble

Quatre problèmes indépendants, regroupés en une seule livraison de finition :

| # | Problème | Nature | Backend ? |
|---|---|---|---|
| **P1** | Le cadre « Demandes de suivi reçues » doit passer sous « Invitations à co-organiser » | Frontend pur (réordonnancement layout) | Non |
| **P2/3** | Le skeleton de `/feed` n'affiche presque rien (cartes invisibles) | Frontend pur (bug de rendu boneyard + fidélité fixture) | Non |
| **P4** | Le bouton « Mes abonnements » du feed est inaccessible (hardcodé `disabled`) | Frontend pur (câblage — **backend déjà livré** SCRUM-168) | Non (déjà fait) |
| **P5** | « Participations publiques » sur le profil n'est pas implémenté | **Full-stack** (nouvel endpoint public + frontend) | **Oui** |

Ordre de difficulté croissant : P1 < P4 ≈ P2/3 < P5. P5 porte l'essentiel de l'effort (contrat + backend + frontend + tests + docs).

---

## 2. Contexte

### 2.1 Pourquoi maintenant

La page feed (SCRUM-116) a été mergée **le 2026-05-20** (PR #195) avec : un skeleton `feed-timeline` cassé, un bouton « Mes abonnements » désactivé en dur, et un hook `useFeed` qui ignore `followedOnly`. Le filtre backend `followedOnly` (SCRUM-168) a été mergé **le lendemain, 2026-05-21** (PR #196) — mais le frontend n'a jamais été recâblé pour le consommer. P4 ferme cette dette. P2/3 corrige un bug de skeleton introduit par la même PR #195. P1 et P5 sont des finitions de la page profil (SCRUM-141).

### 2.2 Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| `FollowRequestsPanel` est dans la colonne gauche du profil, pas sous les invitations | Layout non conforme à l'attendu produit (P1) |
| `feed-timeline.bones.json` marque ses cartes/dots en `container` (`true`) | boneyard 1.8.1 les **filtre au rendu** → skeleton quasi vide (P2/3) |
| `EventsParams` n'expose pas `followedOnly` ; `useFeed` l'ignore ; le bouton est `disabled` | Filtre « Mes abonnements » inaccessible alors que le backend le supporte (P4) |
| Aucun endpoint `GET /users/{id}/participations` (seul `/users/me/participations` self-only existe) | Section « Participations publiques » réduite à un placeholder « Bientôt disponible » (P5) |

### 2.3 Ce qui existe déjà à RÉUTILISER

| Élément | Fichier | Rôle |
|---|---|---|
| `GET /events?followedOnly=true` implémenté | [EventResource.java:93-127](backend/services/event-service/src/main/java/ch/unige/events/event/resource/EventResource.java#L93) | Backend P4 **déjà livré** (SCRUM-168). Auth requise → 401 si anonyme, résout les `followedIds` via `UserServiceClient`. |
| `getMyParticipationEvents(auth0Id, status, timeframe)` | [AttendanceService.java:319-350](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java#L319) | Patron exact à dériver pour P5 (variante par `{id}` cible) |
| `EventServiceClient.findByIds(ids, status)` | [EventServiceClient.java:81-90](backend/shared/domain-dtos/src/main/java/ch/unige/events/shared/client/EventServiceClient.java#L81) | Enrichissement P5 : le 2e param **filtre le statut** → passer `"PUBLISHED"` |
| `UserServiceClient.getAttendeeProjections(ids)` → `List<AttendeeProjection>` (porte `profilePublic`) | [UserServiceClient.java:76-85](backend/shared/domain-dtos/src/main/java/ch/unige/events/shared/client/UserServiceClient.java#L76) | Mécanisme de gating privacy P5 (résout `profilePublic` de la cible) |
| `AttendanceService.withCounts / matchesTimeframe / findByUser / fetchAttendeeProjections` | [AttendanceService.java](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java) | Helpers réutilisables tels quels pour P5 |
| `UserAttendancesInternalResource` (`/users/{id}/attendances`, `@Internal @PermitAll`) | [UserAttendancesInternalResource.java](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/resource/UserAttendancesInternalResource.java) | Prouve que lister les attendances d'un user arbitraire est déjà en place |
| `ProfileEventsList` (états loading/error/empty/data + `PreviewRow`) | [ProfileEventsList.tsx](frontend/src/components/profile/ProfileEventsList.tsx) | **Patron direct** du frontend P5 (le composant frère, rendu juste à côté) |
| `useOrganizerEvents(userId)` | [ProfilePage.tsx:105](frontend/src/pages/profile/ProfilePage.tsx#L105) | Patron de hook pour `useUserParticipations` (P5) |
| `getMyParticipations()` | [attendanceApi.ts:27-38](frontend/src/services/attendanceApi.ts#L27) | Patron de l'appel API P5 |
| Générateurs sans flag `container` (alpha-compounding) | [generate.mjs](frontend/skeleton/generate.mjs) (sections event-detail / event-edit) | Référence de la règle « ne jamais flaguer un bone visible » (P2/3) |

### 2.4 Piège central confirmé (P2/3)

`boneyard-js@1.8.1` (version installée) **supprime au rendu tout bone dont le 6ᵉ élément vaut `true`** :

```js
// frontend/node_modules/boneyard-js/dist/react.js:143
activeBones.bones.filter(raw => !normalizeBone(raw).c).map(...)
// normalizeBone (types.js) : t[5] → .c
```

[feed-timeline.bones.json](frontend/src/bones/feed-timeline.bones.json) flague `true` sur **les dots et toutes les cartes** → seuls les labels de date survivent. La doc [skeleton/README.md](frontend/skeleton/README.md) (§ « Règle isContainer ») est **obsolète** : elle décrit encore `isContainer=true` comme une teinte plus claire, alors que la lib le filtre. **La règle correcte est : ne jamais poser le flag `container` sur un bone qu'on veut voir.** Trois fichiers manuels portent encore le flag : `feed-timeline` (27), `follow-list` (16), `notification-panel` (8) ; les générés en ont 0.

---

## 3. Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### P1 — Placement du panneau « Demandes de suivi »

**D1.** Déplacer `<FollowRequestsPanel />` de la **colonne gauche** vers la **colonne droite** de `PublicProfileView` (branche `isMeRoute`), positionné **après** `<CoOrganizerInvitationsList />`. Ordre cible de la colonne droite : `CalendarSubscribeButton` → `CoOrganizerInvitationsList` → `FollowRequestsPanel`. La colonne gauche conserve : bio → « À propos » → `MyPublicationsPreview`.

> Aujourd'hui dans [ProfilePage.tsx:199-209](frontend/src/pages/profile/ProfilePage.tsx#L199) : gauche = `MyPublicationsPreview` + `FollowRequestsPanel` ; droite = `CalendarSubscribeButton` + `CoOrganizerInvitationsList`.

**Justification.** Les deux composants sont autonomes (chacun fait ses propres appels via un hook interne — `useCoOrganizerInvitations`, état local dans `FollowRequestsPanel`). Aucun couplage à casser. Les tests existants ([ProfilePage.test.tsx:490-519](frontend/src/__tests__/pages/profile/ProfilePage.test.tsx#L490)) vérifient la **présence** de l'en-tête « Demandes de suivi reçues » sur `/me` (et son absence ailleurs), **pas l'ordre DOM ni la colonne** → le déplacement ne les casse pas. `CoOrganizerInvitationsList` est mocké dans ce fichier de test (l.61-63), donc neutre.

### P2/3 — Skeleton du feed

**D2 (cause racine).** Réécrire [feed-timeline.bones.json](frontend/src/bones/feed-timeline.bones.json) **sans aucun flag `container`** (jamais de `true` en 6ᵉ position). La hiérarchie de teinte ne repose plus sur le flag (filtré par la lib) mais sur l'alpha-compounding des bones superposés, comme dans les générateurs `event-detail`/`event-edit`.

**D3 (manuel vs generate.mjs).** **Rester en JSON manuel** (ne PAS ajouter à `generate.mjs`). Justification via l'arbre de décision du [README](frontend/skeleton/README.md#L56) : la timeline est un layout **flex vertical** (pas une grille auto-fit à colonnes variables) → le cas « JSON manuel » s'applique. Mettre à jour directement le `.bones.json`.

**D4 (fidélité du fixture + hauteur).** Corriger `FeedFixture` dans [FeedPage.tsx:15-32](frontend/src/pages/feed/FeedPage.tsx#L15) pour refléter **exactement** le layout réel de [Timeline.tsx](frontend/src/components/feed/Timeline.tsx) + [EventFeedCard.tsx](frontend/src/components/feed/EventFeedCard.tsx) :

| Élément réel | Contrainte fixture |
|---|---|
| Groupe = `flex flex-col gap-4 pb-8 last:pb-0` (pas `gap-8`) | Reproduire `gap-4` + `pb-8` (sauf dernier) |
| `DateMarker` = conteneur `w-7 h-7 md:w-10 md:h-10` avec un **petit dot 12px** centré (pas un cercle plein) | Le bone du dot doit représenter le **petit dot 12px**, pas le conteneur entier |
| `EventFeedCard` = `flex flex-col md:flex-row` : mobile bannière `h-32`(128) **empilée** + bloc infos (`px-4 py-3`, titre 2 lignes + meta + footer) ; desktop `md:w-48 h-28`(112) | Mobile : carte ≈ **bannière 128 + infos** (> `h-40`) ; desktop : `h-28`=112 |
| Trait vertical continu `absolute left-3.5 md:left-5` | Optionnel dans le fixture (cosmétique) ; **non** flaggué container dans les bones |

`bones.height` de chaque breakpoint **doit égaler la hauteur intrinsèque réelle** produite par le fixture à ce container-width (sinon `scaleY` déforme tout — cf. README § « Règle du `height` »). Recalculer après correction du fixture. Garder des container-widths cohérents avec les transitions de layout `flex-col → md:flex-row` (md = 768 viewport).

**D5 (périmètre — follow-list & notification-panel).** **Inclure** la correction de [follow-list.bones.json](frontend/src/bones/follow-list.bones.json) et [notification-panel.bones.json](frontend/src/bones/notification-panel.bones.json) dans cette même livraison (retrait des flags `container`, vérification du rendu vs leur fixture/composant). 

**Justification.** Cause **identique** (même bug boneyard 1.8.1), coût marginal faible, et les laisser cassés serait incohérent avec le fait de documenter la règle dans D6. Tableau d'options :

| Option | Verdict |
|---|---|
| (a) Corriger seulement feed-timeline | ❌ laisse 2 skeletons cassés alors qu'on touche déjà la doc qui les régit |
| (b) **Corriger les 3 fichiers manuels à flag** (feed-timeline, follow-list, notification-panel) | ✅ retenu — même cause, même PR, cohérent avec D6 |
| (c) Auditer + régénérer tous les skeletons | ❌ hors scope (les générés n'ont pas le flag, déjà sains) |

**D6 (correction de la doc).** Mettre à jour **dans la même livraison** : (1) [skeleton/README.md](frontend/skeleton/README.md) § « Règle isContainer » → indiquer que **boneyard ≥ 1.7.7 filtre les bones `container=true` au rendu (React)**, donc **ne jamais poser ce flag sur un bone visible** ; la hiérarchie de teinte se fait par alpha-compounding. (2) [frontend/AGENTS.md](frontend/AGENTS.md#L344) et [docs/components.md](frontend/docs/components.md#L549) → noter la règle anti-flag et confirmer `feed-timeline` en JSON manuel. La table « Skeletons existants » liste déjà `feed-timeline`/`follow-list`/`notification-panel` (rien à ajouter, juste corriger la règle).

### P4 — Filtre « Mes abonnements »

**D7 (câblage).** Propager `followedOnly` de bout en bout :
- [eventApi.ts:5-14](frontend/src/services/eventApi.ts#L5) : ajouter `followedOnly?: boolean` à `EventsParams` (transmis tel quel par `getAll` via `{ params }`).
- [useFeed.ts:66-98](frontend/src/hooks/useFeed.ts#L66) : cesser d'ignorer l'option (`_followedOnly` → `followedOnly`), l'inclure dans l'appel `getAll(...)` **et** dans les dépendances de `fetchPage`/effet de reset, de sorte qu'un changement de `followedOnly` **réinitialise** `allEvents` + `page` + relance `fetchPage(0)`.
- [FeedPage.tsx:75-97](frontend/src/pages/feed/FeedPage.tsx#L75) : remplacer les deux boutons statiques par un toggle piloté par un état local `followedOnly` ; passer cet état à `useFeed({ followedOnly })`. Réinitialiser proprement le sentinel d'infinite-scroll au switch.

**D8 (utilisateur non authentifié).** `/feed` est **public** (hors `PrivateRoute` — [AppRouter.tsx:53](frontend/src/router/AppRouter.tsx#L53)), mais `followedOnly` exige un token (→ 401). Décision : **n'afficher le toggle Tous/Mes abonnements que pour un utilisateur authentifié** (`useAuth`). Un anonyme voit le feed « Tous » sans toggle.

| Option | Verdict |
|---|---|
| (a) Toggle visible mais désactivé + tooltip « connexion requise » pour l'anonyme | ❌ contrôle mort, bruit visuel ; c'est précisément l'état cassé qu'on retire |
| (b) **Toggle rendu uniquement si authentifié** | ✅ retenu — propre, pas de contrôle inerte ; le filtre n'a aucun sens sans compte |
| (c) Rediriger l'anonyme vers login au clic | ❌ surprise UX sur une page publique |

**D9 (état vide « Mes abonnements »).** Quand `followedOnly=true` renvoie `[]`, ne pas distinguer « ne suit personne » de « abonnements sans événement à venir » (le backend renvoie `[]` dans les deux cas). Afficher un **état vide dédié** : *« Aucun événement à venir de vos abonnements. Suivez des organisateurs pour voir leurs événements ici. »* (distinct du vide générique « Aucun événement à venir pour le moment. »).

**D10 (commentaire SCRUM).** Corriger le commentaire périmé de `useFeed` qui cite « SCRUM-138 / non supporté » → référencer **SCRUM-168** (filtre livré). Aucun autre changement sémantique.

### P5 — Participations publiques

**D11 (endpoint).** Créer **`GET /users/{id}/participations`** (public, routé Kong), **owned par `engagement-service`** (`x-owner-service: engagement-service`), renvoyant un `List<Event>` enrichi — **miroir** de [`/users/me/participations`](openapi/openapi.yaml#L2590) mais pour un `{id}` cible (UUID).

**D12 (auth).** Endpoint **`@Authenticated`**. 

| Option | Verdict |
|---|---|
| (a) `@PermitAll` (anonyme autorisé) | ❌ incohérent avec `/events/{id}/attendees` (`@Authenticated`) — le graphe « qui participe à quoi » est partout gated par auth dans le repo |
| (b) **`@Authenticated`** | ✅ retenu — la page profil est déjà sous `PrivateRoute` ([AppRouter.tsx:63-67](frontend/src/router/AppRouter.tsx#L63)) → l'appelant porte **toujours** un token ; aucun cas anonyme à gérer côté front ; aligné sur le précédent attendees |

**D13 (gating `profilePublic`).** Règle de visibilité, alignée sur la **convention la plus récente** (SCRUM-169 / commit `de867a15`, qui a **abandonné le 404 anti-oracle** de `GET /users/{id}` au profit d'un `200` à projection restreinte) :

| Appelant vs cible | `profilePublic` cible | Réponse |
|---|---|---|
| caller = target (self) | `true` **ou** `false` | `200` + liste de ses participations (PUBLISHED + ATTENDING) |
| caller ≠ target | `true` | `200` + liste |
| caller ≠ target | `false` | **`200 []`** (liste vide — pas de leak, pas d'oracle) |
| UUID inexistant | n/a | `200 []` (pas de 404 — cohérent avec « pas de leak ») |

| Option pour le cas « cible privée, non-self » | Verdict |
|---|---|
| (a) `404 not_found` anti-oracle (façon SCRUM-138 décision 10) | ❌ convention abandonnée par SCRUM-169/de867a15 ; ré-introduirait une incohérence |
| (b) **`200 []`** | ✅ retenu — aligné SCRUM-169 « 200 restreint, pas de 404 » ; le front masque déjà la section sur profil privé (`ProfilePrivateState`) → c'est de la défense en profondeur |
| (c) `403 forbidden` | ❌ oracle d'existence + nouveau cas d'erreur front inutile |

**Mécanisme** : `engagement-service` résout `profilePublic` de la cible via `UserServiceClient.getAttendeeProjections(List.of(targetId))` (porte `profilePublic` par row). **Fail-closed** : si la projection est absente (user-service down) **et** caller ≠ target → renvoyer `[]` (ne jamais leak en cas de dégradation). Self court-circuite ce check.

**D14 (statut exposé).** **`ATTENDING` uniquement.** Ne jamais exposer publiquement les inscriptions `WAITLISTED` (on ne diffuse pas qu'un tiers est sur liste d'attente). Filtre appliqué côté service, non paramétrable sur cet endpoint public.

**D15 (statut d'événement).** **`PUBLISHED` uniquement**, via `eventClient.findByIds(eventIds, "PUBLISHED")`. Jamais de `DRAFT`/`CANCELLED` d'un tiers (cohérent ISSUE-92).

**D16 (pagination).** **Aucune pagination** — miroir strict de `/users/me/participations` (renvoi de la liste complète). Le volume (événements auxquels un user participe) est naturellement borné ; cohérent avec les autres listes `me/*` non paginées.

**D17 (identifiant).** Le frontend appelle avec **l'UUID** `profile.id` (`UserPublicResponse.id`). Pas de variante username (l'endpoint est sous `/users/{id}` UUID, comme `/users/{id}/follow|followers|following`).

**D18 (migration).** **Aucune migration Flyway.** P5 est un endpoint de **lecture** sur la table `attendances` existante (engagement = V1..V4, pas de V5). Pas de nouvelle entité, pas de nouveau DTO (réutiliser `EventDTO`/`Event`).

**D19 (paramètre `timeframe`).** Accepter un `timeframe` optionnel (`upcoming`/`past`, insensible à la casse) **par parité** avec `/me/participations`, en réutilisant `Timeframe`/`matchesTimeframe`. Omis → toutes périodes. (Le frontend P5 n'a pas besoin de le passer pour le MVP, mais le contrat reste cohérent.)

**D20 (frontend P5).** Miroir exact du frère `ProfileEventsList` :
- Nouveau hook `useUserParticipations(userId)` (calque de `useOrganizerEvents`) → `{ events, loading, error }`.
- `ProfileParticipations` reçoit `events/loading/error` **en props** (rendu loading/error/empty/data avec `PreviewRow`) — il faut donc lui passer ces props depuis `PublicProfileView` (appel du hook avec `profile.id`).
- **Pas de nouveau `.bones.json`** : `ProfileEventsList` utilise un skeleton inline `animate-pulse` (`LoadingRows`) — `ProfileParticipations` fait pareil. (Cohérent, et évite un skeleton boneyard supplémentaire.)
- État vide : *« Aucune participation publique pour le moment. »* (calque de « Aucun événement organisé… »).

---

## 4. Analyse de l'existant

### 4.1 À MODIFIER

| Fichier | Modification | Problème |
|---|---|---|
| [frontend/src/pages/profile/ProfilePage.tsx](frontend/src/pages/profile/ProfilePage.tsx) | Déplacer `FollowRequestsPanel` (gauche→droite, sous invitations) ; appeler `useUserParticipations(profile.id)` et passer les props à `ProfileParticipations` | P1, P5 |
| [frontend/src/bones/feed-timeline.bones.json](frontend/src/bones/feed-timeline.bones.json) | Réécriture sans flag `container` ; recalcul `height` aligné au fixture corrigé | P2/3 |
| [frontend/src/bones/follow-list.bones.json](frontend/src/bones/follow-list.bones.json), [notification-panel.bones.json](frontend/src/bones/notification-panel.bones.json) | Retrait des flags `container` ; vérif rendu vs composant | P2/3 (D5) |
| [frontend/src/pages/feed/FeedPage.tsx](frontend/src/pages/feed/FeedPage.tsx) | Corriger `FeedFixture` (fidélité) ; toggle fonctionnel piloté par état + `useAuth` | P2/3, P4 |
| [frontend/src/hooks/useFeed.ts](frontend/src/hooks/useFeed.ts) | Consommer `followedOnly` (appel + deps + reset) ; corriger commentaire SCRUM-168 | P4 |
| [frontend/src/services/eventApi.ts](frontend/src/services/eventApi.ts) | `followedOnly?: boolean` dans `EventsParams` | P4 |
| [frontend/src/services/attendanceApi.ts](frontend/src/services/attendanceApi.ts) | Ajouter `getUserParticipations(userId, timeframe?)` → `GET /users/{id}/participations` | P5 |
| [frontend/src/components/profile/ProfileParticipations.tsx](frontend/src/components/profile/ProfileParticipations.tsx) | Remplacer le placeholder par le rendu réel (props `events/loading/error`, `PreviewRow`, états) | P5 |
| [openapi/openapi.yaml](openapi/openapi.yaml) | Nouveau path `GET /users/{id}/participations` (à côté de `/users/me/participations`) | P5 |
| [backend/.../engagement/attendance/service/AttendanceService.java](backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/service/AttendanceService.java) | Nouvelle méthode `getUserParticipationEvents(UUID targetId, Timeframe)` (gating + ATTENDING + PUBLISHED) | P5 |
| [frontend/skeleton/README.md](frontend/skeleton/README.md), [frontend/AGENTS.md](frontend/AGENTS.md), [frontend/docs/components.md](frontend/docs/components.md) | Corriger la règle `isContainer` (filtré au rendu ≥1.7.7) | P2/3 (D6) |
| [backend/docs/api-contract.md](backend/docs/api-contract.md), [backend/docs/data-model.md](backend/docs/data-model.md) | Documenter `GET /users/{id}/participations` + règle de gating | P5 |

### 4.2 À CRÉER

| Fichier | Rôle | Problème |
|---|---|---|
| `backend/services/engagement-service/src/main/java/ch/unige/events/engagement/attendance/resource/UserParticipationsResource.java` | Resource publique `@Path("/users")` + `@GET @Path("/{id}/participations") @Authenticated` | P5 |
| `backend/services/engagement-service/src/test/java/.../attendance/resource/UserParticipationsResourceTest.java` | Tests `@QuarkusTest` (200 self, 200 public-target, 200 [] private-target, 401 anon, ATTENDING-only, PUBLISHED-only) | P5 |
| `backend/services/engagement-service/src/test/java/.../attendance/service/` (compléter le test service existant) | Tests unitaires `getUserParticipationEvents` (gating, filtres, fail-closed) | P5 |
| `frontend/src/hooks/useUserParticipations.ts` | Hook calque de `useOrganizerEvents` | P5 |
| `frontend/src/__tests__/...` | Tests : `useFeed` (followedOnly reset), `FeedPage` (toggle auth/anonyme), `ProfileParticipations` (états), MAJ test « Bientôt disponible » | P4, P5 |

### 4.3 HORS SCOPE (explicite)

- ❌ Aucune migration Flyway (D18).
- ❌ Aucun nouveau schéma OpenAPI (réutiliser `Event`). Seul un **path** est ajouté.
- ❌ Aucune modification du backend `followedOnly` (déjà livré SCRUM-168).
- ❌ Pas de pagination sur `/users/{id}/participations` (D16).
- ❌ Pas de variante `WAITLISTED` exposée publiquement (D14).
- ❌ Pas de régénération des skeletons générés (`event-cards`, etc. — sans flag, déjà sains).
- ❌ Pas de second fichier de spec ni de découpage par problème.
- ❌ Pas de notification déclenchée par P5.
- ❌ Pas de modification de `/users/me/participations` (self) existant.

---

## 5. Étapes d'implémentation

> Marqueurs : **[FE]** frontend-only · **[FS]** full-stack. Respecter pour P5 l'ordre `openapi → backend (service → resource → tests) → frontend (api → hook → composant → tests) → docs`.

### 5.A — P1 (placement) **[FE]**
1. Dans `PublicProfileView`, retirer `<FollowRequestsPanel />` de la colonne gauche.
2. L'insérer dans la colonne droite (`isMeRoute`) **après** `<CoOrganizerInvitationsList />`.
3. Lancer `frontend` tests profil — vérifier que les assertions de présence (l.490-519) passent toujours.

### 5.B — P2/3 (skeleton feed + doc) **[FE]**
1. Corriger `FeedFixture` (D4) pour matcher `Timeline` + `EventFeedCard` (gap-4/pb-8, dot 12px, carte mobile bannière+infos / desktop h-28).
2. Mesurer la hauteur intrinsèque réelle du fixture par container-width retenu ; réécrire `feed-timeline.bones.json` **sans flag** avec `height` exact (D2/D3).
3. Retirer les flags de `follow-list.bones.json` + `notification-panel.bones.json` ; vérifier leur rendu (D5).
4. Corriger la règle `isContainer` dans README/AGENTS/components.md (D6).
5. Vérifier visuellement les 3 skeletons (loading) en mode dark + light.

### 5.C — P4 (Mes abonnements) **[FE]**
1. `EventsParams += followedOnly?: boolean` (eventApi.ts).
2. `useFeed` : consommer `followedOnly` (appel `getAll`, deps, reset page 0 au changement) ; commentaire SCRUM-168 (D10).
3. `FeedPage` : état `followedOnly` + toggle fonctionnel, **rendu conditionné à `useAuth`** (D8) ; état vide dédié (D9).
4. Tests : `useFeed` (toggle → refetch page 0), `FeedPage` (toggle visible si auth, absent si anonyme).

### 5.D — P5 (participations) **[FS]**
1. **OpenAPI d'abord** : ajouter `GET /users/{id}/participations` (`x-owner-service: engagement-service`, `security: BearerAuth`, param path `id: uuid` + query `timeframe` optionnel, `200` → `array` de `Event`, `401`). Camel-case, pas de préfixe `is`.
2. **Backend service** : `AttendanceService.getUserParticipationEvents(UUID targetId, Timeframe)` — résoudre `profilePublic` (getAttendeeProjections, fail-closed), appliquer gating D13, filtrer `ATTENDING`, enrichir via `findByIds(ids, "PUBLISHED")`, `withCounts` + `matchesTimeframe`. Réutiliser les helpers existants.
3. **Backend resource** : `UserParticipationsResource` (`@Authenticated`), récupère caller via `callerIdentity`, délègue au service.
4. **Backend tests** : service (gating self/public/private, ATTENDING-only, PUBLISHED-only, fail-closed) + resource (`@QuarkusTest` : 200/401/[] private).
5. **Frontend api** : `getUserParticipations(userId, timeframe?)` (attendanceApi.ts).
6. **Frontend hook** : `useUserParticipations(userId)`.
7. **Frontend composant** : réécrire `ProfileParticipations` (props + états + `PreviewRow`) ; le câbler dans `PublicProfileView` (appel hook + props).
8. **Frontend tests** : `ProfileParticipations` (loading/error/empty/data) ; **mettre à jour** le test asserttant « Bientôt disponible. » ([ProfilePage.test.tsx:327](frontend/src/__tests__/pages/profile/ProfilePage.test.tsx#L327)).
9. **Docs** : `api-contract.md` (+ ligne endpoint), `data-model.md` (règle de gating participations). `internal-endpoints.md` non concerné (endpoint **public**).

---

## 6. Ordre de réalisation recommandé & dépendances

- **Indépendants, parallélisables** : P1, P2/3, P4 sont 3 lots frontend disjoints (fichiers différents, sauf `FeedPage.tsx` partagé par P2/3+P4 → les faire dans la même passe).
- **Quick wins d'abord** : P1 (≈ trivial) → P2/3 + P4 (même fichier `FeedPage.tsx`) → P5 (le plus lourd, openapi-first).
- **P5 est le seul à toucher backend + contrat** → le traiter en dernier, en respectant strictement l'ordre des couches.
- Aucune dépendance inter-problèmes bloquante : P4 dépend du backend SCRUM-168 **déjà mergé**.

---

## 7. Checklist finale de validation

- [ ] **P1** : sur `/profile/me`, « Demandes de suivi reçues » apparaît dans la colonne droite **sous** « Invitations à co-organiser ». Tests profil verts.
- [ ] **P2/3** : skeleton `/feed` montre des cartes (plus seulement des labels). Aucun bone `container=true` dans les 3 fichiers manuels. `bones.height == ` hauteur réelle du fixture. README/AGENTS/components.md corrigés sur `isContainer`.
- [ ] **P4** : toggle Tous/Mes abonnements fonctionnel pour un user authentifié (filtre effectif, reset page 0 au switch) ; absent pour l'anonyme ; état vide dédié. Tests `useFeed`/`FeedPage` verts.
- [ ] **P5** : `GET /users/{id}/participations` dans openapi ; backend (service+resource+tests) ; `ProfileParticipations` affiche les participations publiées (ATTENDING) ; gating self/public/private respecté ; test « Bientôt disponible » mis à jour.
- [ ] `cd frontend && npm run build && npm run test && npm run lint` — verts.
- [ ] `cd backend && ./mvnw verify` — vert (15 modules).
- [ ] `npm run skeleton` **non requis** (feed-timeline manuel, generate.mjs non touché) — vérifier que le JSON manuel est bien chargé par `registry.js`.
- [ ] `git diff openapi/` ne contient **que** l'ajout du path participations (P1-P4 = 0 ligne contrat).
- [ ] Docs backend (`api-contract.md`, `data-model.md`) à jour.
