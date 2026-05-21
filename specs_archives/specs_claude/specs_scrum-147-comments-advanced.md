# SCRUM-147 — Frontend commentaires avancés (likes, threads, autocomplete mentions, signalement)

| Champ | Valeur |
|---|---|
| Ticket Jira | SCRUM-147 (à vérifier en chat — le ticket pasté par Daniel ne portait pas explicitement la clé ; à confirmer avec le PO avant l'implémentation) |
| Sprint | S9 (calendrier produit) |
| Épic | [SCRUM-13](https://pinfo-groupe6.atlassian.net/browse/SCRUM-13) — Profils utilisateurs et social (les commentaires sont la primitive d'engagement social sur les events) |
| Story Points | 8 (proposition — à valider) |
| Branche | **`feature/s7-comment-mentions`** (cf. Décision A — partagée avec SCRUM-145, **PAS** `feature/s8-comments-advanced` suggérée par le Jira drift, **PAS** `feature/scrum-145-comment-mentions` du prompt original — la branche réelle de l'agent porte le préfixe sprint historique `s7`) |
| Base | tip de la branche `feature/s7-comment-mentions` après livraison SCRUM-145 (`b5f9f7b6` au moment de la rédaction) ; la PR cible `main` |
| Auteur spec | Daniel (rédaction assistée Claude Opus 4.7) |
| Date | 2026-05-19 |
| PR de référence | **PR #193 existante** — SCRUM-147 absorbé dans la PR ouverte pour SCRUM-145 (cf. Décision A). Aucun nouveau `gh pr create`. Le titre / le body de PR #193 seront mis à jour à la livraison via `gh pr edit 193 --title ... --body ...` pour refléter le scope élargi. |
| Mode de travail | **Une seule PR mixte backend (SCRUM-145) + frontend (SCRUM-147), livrée en pleine autonomie** dans une session d'implémentation séparée. La présente spec est l'artefact de planification ; aucun code de production n'est livré dans la session courante. |
| Règle d'or `openapi-first` | **NON applicable** — toute la surface backend dont SCRUM-147 a besoin est **déjà figée** dans [`openapi/openapi.yaml`](openapi/openapi.yaml) (`POST /comments/{commentId}/like` SCRUM-144 ligne 4750+, `DELETE /comments/{commentId}/like`, `POST /comments/{commentId}/report` ligne 4720+, `GET /users/search` SCRUM-137 ligne 1902+, `POST /events/{eventId}/comments` avec `parentCommentId` SCRUM-139). **Aucune modification d'`openapi.yaml`** dans cette PR. |

> **Pré-requis lecture obligatoires avant implémentation :**
> - [`frontend/AGENTS.md`](frontend/AGENTS.md) — conventions camelCase, MVC, hooks bespoke (PAS TanStack Query), `@/` alias, design tokens, skeleton screens, glassmorphism cards.
> - [`AGENTS.md`](AGENTS.md) racine — workflow git, scope `scrum-147` obligatoire sur `feat`/`refactor`/`perf`.
> - [`specs_archives/specs_claude/specs_scrum-128-145-comment-mentions.md`](specs_archives/specs_claude/specs_scrum-128-145-comment-mentions.md) — **spec anchor**. SCRUM-147 se branche sur la pipeline backend décrite ici. Le format de mention (`@<username>`, regex `[a-z0-9._-]{3,30}`) doit matcher mot-pour-mot.
> - [`specs_archives/specs_claude/specs_scrum-169.md`](specs_archives/specs_claude/specs_scrum-169.md) — système username (livré). Source de vérité pour `UserPublicResponse.username`.

---

> ## ⚠️ Pré-ambule — coupling PR backend + frontend
>
> Cette spec est rédigée sous la décision explicite de Daniel : **SCRUM-147
> est livré sur le même branch et la même PR que SCRUM-145**, malgré le
> coût opérationnel (deploy coupling, reviewers mixtes, taille de diff).
> Le risque a été soulevé en chat, refusé, accepté en deuxième passe par
> Daniel. La section § 9 (Risques) le re-documente sans le re-débattre.

---

## 0. Reconciliation avec le ticket Jira d'origine

Le ticket Jira (que Daniel a paste dans le prompt, sans clé visible) contient plusieurs références qui ont dérivé par rapport à la réalité du repo. Cette spec corrige :

| # | Texte Jira | État dans le repo / dans la spec SCRUM-145 | Action |
|---|---|---|---|
| 1 | `@displayName` (×4 occurrences dans le ticket) | **Faux** : la pipeline backend SCRUM-145 parse exclusivement `@<username>` (regex `[a-z0-9._-]{3,30}`, charset `[a-z0-9._-]`, cf. spec SCRUM-145 § 0 reconciliation point 1 et Décision E). Mentionner par displayName = identifiant non-unique + accentué + casing imprévisible. | **Réalité du repo retenue.** Cette spec utilise `@<username>` partout, y compris dans le préfixe de reply et la sélection autocomplete. |
| 2 | Branche suggérée `feature/s8-comments-advanced` | La branche réelle est **`feature/s7-comment-mentions`** (préfixe `s7` historique, sur laquelle SCRUM-145 a été livrée — cf. § Mode de travail). | **Réalité retenue.** PR #193. |
| 3 | "Le report propose une textarea ou un select de motif" (variante du ticket) | Le backend `POST /comments/{commentId}/report` (SCRUM-144 livré) **exige** un body `CreateReportRequest` avec **`reason` non-null + Bean Validation** (cf. openapi.yaml ligne 4720 — réponse 400 sur `reason` manquant). | **Conflit avec le locked-in #G du prompt** ("no reason field in MVP — simple yes/no"). Voir Décision G ci-dessous pour la résolution. |
| 4 | "Le système enforce depth-1 côté frontend (cache la reply button)" | Déjà en place : `CommentItem.tsx:144-153` masque "Répondre" via `canReply = !isReply && currentUserId !== null`. Backend `CommentService.post` rejette `replies_too_deep` 422 si bypass. | Pas d'action — déjà livré par SCRUM-139 / SCRUM-146. Sentinel test à ajouter pour pinner le comportement. |
| 5 | Composants de reply, modal report, autocomplete "à créer de zéro" | **Plusieurs surfaces existent déjà** : `ReportModal.tsx` (pour events), `UsernameAutocomplete.tsx` (input-replacement SCRUM-137), `ConfirmDialog.tsx`, `useToast`. Le ticket Jira ne les référence pas. | **Réutiliser tel quel** (Décision J). Voir § 4 pour le détail des reuse. |

**Conclusion.** Le ticket Jira est partiellement déphasé par rapport à la réalité livrée (SCRUM-144 / SCRUM-146 / SCRUM-137 / SCRUM-169). La spec respecte ce qui est en production. Les 12 décisions verrouillées du prompt restent valides modulo le point 3 ci-dessus (le report nécessite obligatoirement un `reason` — Décision G résout la divergence).

---

## 1. Objectifs & non-objectifs

### Objectifs

- **Axe 1 — Like / unlike d'un commentaire avec optimistic update.** Bouton cœur dans le footer de chaque `CommentItem` (top-level ET reply). Toggle :
  - **Authentifié + non-liké → liké** : optimistic flip de `likedByMe = true` + incrément `likeCount`. `POST /comments/{id}/like` (idempotent 200/201). Rollback sur 4xx/5xx (sauf 409 idempotent qui est traité comme succès).
  - **Authentifié + liké → unliké** : optimistic flip + décrément. `DELETE /comments/{id}/like`. Rollback symétrique.
  - **Non-authentifié** : bouton visible mais désactivé, tooltip "Connectez-vous pour aimer" (matche le pattern `FavoriteButton` existant).
  - **Anti-double-click** : le bouton est `disabled` pendant la mutation in-flight. Pas de race avec un 2e clic.
- **Axe 2 — Réponses inline avec préfixe `@<parentAuthorUsername> `.** Le bouton "Répondre" + le `CommentForm` inline existent déjà (`CommentItem.tsx:144-187`). **Ce qui manque** :
  - Le `CommentForm` reply est aujourd'hui ouvert vide (`value=''`). SCRUM-147 le pré-remplit avec `@<parentAuthorUsername> ` (avec espace final) au moment du `setShowReplyForm(true)`.
  - Le placeholder change de `"Répondre à {authorLabel}…"` (déjà en place) → confirmer + ajuster si nécessaire.
  - **Pas de wrapper nesting** au-delà de la profondeur 1 : la garde-fou est déjà en place côté UI (`canReply = !isReply && currentUserId !== null`) ET côté backend (422 `replies_too_deep`). Sentinel test à pinner.
- **Axe 3 — Autocomplete mention `@<prefix>` à l'intérieur du `CommentForm`.** Nouveau composant `MentionAutocomplete` (différent de `UsernameAutocomplete` existant, cf. Décision E). Trigger : l'utilisateur tape `@` suivi de ≥ 2 chars dans le textarea. Dropdown sous le textarea, debounce 300 ms, ↑/↓ navigation, Enter pour insérer, Esc pour fermer. Chaque ligne = avatar + displayName (gras) + `@username` (gris). À la sélection, le préfixe tapé est remplacé par `@<username> ` (espace final, pour que l'utilisateur continue à taper). Plusieurs `@` dans un même commentaire fonctionnent indépendamment.
- **Axe 4 — Signalement de commentaire** avec **réutilisation du `ReportModal` existant** (cf. Décision G — divergence avec le locked-in #G du prompt). Bouton "Signaler" du `CommentItem.tsx:164-174` aujourd'hui ouvre un toast placeholder ; SCRUM-147 le re-câble pour ouvrir `ReportModal` (avec adaptation du titre / texte / endpoint pour les commentaires) et appeler `POST /comments/{id}/report` à la confirmation.
- **Axe 5 — Hooks bespoke** : `useCommentLike` (optimistic + rollback), `useReportComment` (wrap `reportApi.reportComment`), pas de hook `useUserSearch` dédié (réutilisation directe de `searchUsernames` qui existe déjà). Pattern miroir des hooks existants (`useEvent`, `useAttendees`, `useDebounce`). **Pas de TanStack Query.**
- **Axe 6 — Tests** (vitest + happy-dom) : couverture ≥ 80 % sur le nouveau code, sentinel par axe (cf. § 6).
- **Axe 7 — Documentation** :
  - [`frontend/docs/components.md`](frontend/docs/components.md) : entrée pour `MentionAutocomplete`, mise à jour de `CommentItem` (likes + report wiring).
  - [`frontend/docs/types.md`](frontend/docs/types.md) : pas de nouveau type (les types `Comment`, `UserPublicResponse`, `ReportReason` existent déjà).
  - [`frontend/docs/sprint-context.md`](frontend/docs/sprint-context.md) : section datée `2026-05-XX — SCRUM-147 livré (frontend commentaires avancés)`.
  - **Pas de modification d'`openapi.yaml`.**

### Non-objectifs

- **Rendu cliquable des mentions dans le contenu des commentaires déjà postés** — transformer la sous-chaîne `@alice.dosh` dans `CommentItem.content` en `<Link to="/profile/alice.dosh">@alice.dosh</Link>`. Ticket frontend séparé S9+. Le parseur miroir TypeScript de la regex SCRUM-145 sera à écrire à ce moment-là.
- **Édition d'un commentaire posté** (modification du texte). Hors scope produit.
- **Suppression UX** — déjà livré par SCRUM-146 (`CommentItem.tsx:154-163` + `ConfirmDialog`). Aucune action SCRUM-147.
- **Préférences de notification par event** (mute mentions / mute new-comment). Produit S10+, hors scope.
- **Mute / block users.**
- **Quote-reply** (citer le texte du parent dans la reply). Future work.
- **Pagination des likes** (qui a liké un commentaire). Hors scope MVP.
- **`useUserSearch` hook dédié** — la fonction `searchUsernames` du `userService.ts` est suffisante ; `MentionAutocomplete` la consomme directement avec son propre debounce + cancellation. Pas de duplication avec le pattern existant d'`UsernameAutocomplete`.
- **TanStack Query / SWR / autre store global** — convention projet : hooks bespoke `useEffect + useState`.
- **Réinventer le `ReportModal`** — celui-ci existe et est réutilisé.
- **Merge de la PR** par l'agent.

---

## 2. Contexte

### 2.1 Le besoin produit

SCRUM-146 (livré) a livré la première itération de la section commentaires : liste paginée, post top-level, post reply (depth-1), suppression. Quatre features manquent pour amener la conversation au standard moderne :

1. **Like** — signal d'engagement bas coût, déjà alimenté côté backend par SCRUM-144 (table `comment_likes`, endpoints `POST/DELETE /comments/{id}/like`, projection `likedByMe` dans `CommentDTO`). Aujourd'hui le `likeCount` est affiché nulle part dans `CommentItem` et le `likedByMe` n'est pas consommé. Dette explicite.
2. **Préfixe reply** — l'inline reply form ouvre vide. Sur Slack / Discord / Twitter, cliquer "Reply" pré-remplit avec `@<author> ` ; c'est le geste qui informe le destinataire qu'il est interpelé. Sans ce préfixe, l'engagement social rate la connection.
3. **Autocomplete mention** — sans aide, l'utilisateur tape `@alice.do` et fait des fautes de frappe. Le backend SCRUM-145 ne déclenchera pas la notif si le handle ne matche pas exactement. L'autocomplete supprime cette friction et garantit que les mentions résolvent.
4. **Signalement de commentaire** — `POST /comments/{id}/report` est livré (SCRUM-144) mais le bouton frontal montre un toast placeholder *« Le signalement de commentaire arrive bientôt »*. Dette explicite documentée dans `CommentItem.tsx:82-91`.

### 2.2 Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| Aucun bouton like dans `CommentItem` (le footer n'a que Répondre / Supprimer / Signaler-placeholder) | `likedByMe` et `likeCount` du DTO ne sont consommés nulle part — feature complètement invisible |
| Aucun service `likeComment` / `unlikeComment` dans [`commentApi.ts`](frontend/src/services/commentApi.ts) | Les endpoints existent côté backend mais pas de wrapper frontend pour les appeler |
| Aucun hook `useCommentLike` pour orchestrer l'optimistic + rollback | Le composant devrait gérer l'orchestration localement → couplage fort, pas testable isolément |
| Le `CommentForm` ouvert en reply est vide (`value=''` initial) | L'utilisateur doit re-taper `@<author>` pour pinguer le parent — friction inutile, mention souvent oubliée |
| Aucun composant `MentionAutocomplete` adapté à un trigger inline dans un textarea | `UsernameAutocomplete` (SCRUM-137) est un input-replacement, pas un trigger inline — shape incompatible (voir Décision E) |
| Le bouton "Signaler" du `CommentItem` montre un toast placeholder | Feature explicitement marquée TODO dans le code |
| Aucun service `reportComment` dans [`reportApi.ts`](frontend/src/services/reportApi.ts) | Seul `reportEvent` existe |

### 2.3 Ce qui existe déjà à RÉUTILISER tel quel (ne pas recréer)

| Élément | Fichier | Rôle dans SCRUM-147 |
|---|---|---|
| `CommentItem.tsx` — affichage commentaire + footer Reply / Delete / Signaler | [`frontend/src/components/event/CommentItem.tsx`](frontend/src/components/event/CommentItem.tsx) | Cible — ajout du bouton like, re-câblage de Signaler, pré-remplissage du reply form |
| `CommentForm.tsx` — textarea + counter + submit / cancel buttons | [`frontend/src/components/event/CommentForm.tsx`](frontend/src/components/event/CommentForm.tsx) | Cible — ajout d'un slot autocomplete + initialValue prop |
| `CommentSection.tsx` — orchestrateur page + tri + pagination | [`frontend/src/components/event/CommentSection.tsx`](frontend/src/components/event/CommentSection.tsx) | Pas touché — le composant délègue aux items |
| `commentApi.ts` — getEventComments / postComment / deleteComment | [`frontend/src/services/commentApi.ts`](frontend/src/services/commentApi.ts) | Cible — ajout `likeComment` et `unlikeComment` |
| `reportApi.ts` — `reportEvent` uniquement | [`frontend/src/services/reportApi.ts`](frontend/src/services/reportApi.ts) | Cible — ajout `reportComment` (même shape `CreateReportRequest`) |
| `searchUsernames(q, limit)` — wrapper `GET /users/search` | [`frontend/src/services/userService.ts:93`](frontend/src/services/userService.ts#L93) | Consommé par `MentionAutocomplete` — déjà existant, **PAS** de re-création |
| `useDebounce<T>(value, delay)` — hook minimaliste, déjà testé | [`frontend/src/hooks/useDebounce.ts`](frontend/src/hooks/useDebounce.ts) | Consommé par `MentionAutocomplete` (debounce 300 ms) |
| `ReportModal.tsx` — modal avec Select `reason` + Textarea `description` | [`frontend/src/components/event/ReportModal.tsx`](frontend/src/components/event/ReportModal.tsx) | **Réutilisé** par SCRUM-147 (cf. Décision G). Adaptation : props `target: 'event' \| 'comment'` + texte du titre / placeholder |
| `ConfirmDialog.tsx` — confirmation simple (oui/non) | [`frontend/src/components/utils/ConfirmDialog.tsx`](frontend/src/components/utils/ConfirmDialog.tsx) | Pas réutilisé pour le report (le report a une intention "spécifier le motif" pas seulement "confirmer"). Reste utilisé pour le delete existant. |
| `useToast` — toast helper | [`frontend/src/hooks/useToast.ts`](frontend/src/hooks/useToast.ts) | Feedback succès / erreur sur like + report |
| `UserAvatar` — round avatar avec fallback initiales | [`frontend/src/components/user/UserAvatar.tsx`](frontend/src/components/user/UserAvatar.tsx) | Lignes du dropdown autocomplete |
| `userDisplayLabel(displayName, username, userId)` | [`frontend/src/utils/displayName.ts`](frontend/src/utils/displayName.ts) | Label affiché dans les lignes du dropdown |
| Types `Comment`, `UserPublicResponse`, `ReportReason`, `REPORT_REASONS` | [`frontend/src/types/comment.ts`](frontend/src/types/comment.ts), [`frontend/src/types/user.ts`](frontend/src/types/user.ts), [`frontend/src/types/report.ts`](frontend/src/types/report.ts) | Aucune modification |
| `useAuth` — `currentUser`, `isAuthenticated` | [`frontend/src/contexts/AuthContext.tsx`](frontend/src/contexts/AuthContext.tsx) | Gating des actions par état d'auth (Décision I) |
| Pattern `FavoriteButton` (like-button-like) | [`frontend/src/components/event/FavoriteButton.tsx`](frontend/src/components/event/FavoriteButton.tsx) | **Modèle visuel + comportemental** pour le like button : icône cœur, optimistic toggle, disabled pendant in-flight, tooltip si non-auth |

### 2.4 Pourquoi maintenant

- **SCRUM-144 a livré le backend likes + report-comment** mais le frontend est resté en placeholder explicite (`TODO: SCRUM-144` dans `CommentItem.tsx:83`). Dette technique ouverte.
- **SCRUM-145 livre le backend des mention-notifications** (PR #193 en cours, sera mergée avec SCRUM-147). Sans l'autocomplete `@`, l'utilisateur ne sait pas qu'il peut mentionner — la feature reste invisible / sous-utilisée.
- **SCRUM-169 a livré usernames** (mergé). Le champ `username` est exposé partout, l'autocomplete a un identifiant non-ambigu à utiliser.
- **Le coupling backend+frontend a été accepté par Daniel** malgré le risque (cf. § 9) — autant livrer maintenant les deux ensemble plutôt que de fragmenter la PR de revue.
- **Aucune dépendance amont restante** — toutes les briques (likes endpoints, report endpoint, search endpoint, types, modal, primitives UI) sont livrées.

---

## 3. Décisions techniques tranchées (NE PAS REVISITER pendant l'implémentation)

> **Règle.** Une fois la spec validée par Daniel, ces décisions ne se rediscutent pas pendant l'implémentation. Toute déviation doit être documentée dans `sprint-context.md` à la livraison.

### Décision A — Branche `feature/s7-comment-mentions`, PR #193 partagée avec SCRUM-145

**Décision.** SCRUM-147 est livré sur la **même branche** (`feature/s7-comment-mentions`, déjà existante, déjà à PR #193) que SCRUM-145. Les commits portent le scope `feat(scrum-147)`. À la fin de la session, `git push origin feature/s7-comment-mentions` met à jour la PR existante ; **aucun `gh pr create`** n'est invoqué. Le titre + le body de PR #193 sont mis à jour à la fin via `gh pr edit 193` pour refléter le scope élargi (backend SCRUM-145 + frontend SCRUM-147).

**Justification.** Décision explicite de Daniel après pushback (cf. § Pré-ambule). La cohérence end-to-end (backend câblé + frontend qui le consomme) est validée en preview dans la même fenêtre. Inconvénients documentés § 9.

| Option | Verdict |
|---|---|
| (a) Même branche, même PR (SCRUM-147 absorbé dans PR #193) | ✅ retenu (décision Daniel) |
| (b) Branche dédiée `feature/scrum-147-...`, PR séparée stacked sur PR #193 | ❌ — Daniel ne veut pas de stacked PR ici |
| (c) Attendre que SCRUM-145 merge, puis brancher depuis main | ❌ — bloque SCRUM-147 sur le calendrier de merge |

### Décision B — Hooks bespoke `useEffect + useState`, **PAS** TanStack Query

**Décision.** Tous les hooks de SCRUM-147 (`useCommentLike`, `useReportComment`, autocomplete state machine inline dans `MentionAutocomplete`) suivent le pattern projet existant : `useEffect + useState`, fetch via Axios, état local `{ loading, error, data }`. **Aucune dépendance ajoutée** (pas de `@tanstack/react-query`, pas de SWR).

**Justification.** Cohérent avec [`frontend/AGENTS.md`](frontend/AGENTS.md) + les ~15 hooks bespoke existants (`useEvent`, `useAttendees`, `useComments`, `useOccurrences`, etc.). Ajouter un store global pour 2 mutations + 1 query coûterait plus que ça ne rapporte ; introduire la dépendance maintenant est hors-scope.

| Option | Verdict |
|---|---|
| (a) Hooks bespoke `useEffect + useState` | ✅ retenu (convention projet) |
| (b) TanStack Query / SWR | ❌ |
| (c) Context + reducer | ❌ — overkill pour 2 mutations |

### Décision C — Optimistic update + rollback granulaire sur le like

**Décision.** `useCommentLike(initialComment)` expose `{ liked, likeCount, toggling, toggle }` et gère :

1. À l'appel `toggle()` : flip optimiste local de `liked` + ajustement de `likeCount` (+1 ou -1).
2. Lance `POST /comments/{id}/like` (si like) ou `DELETE /comments/{id}/like` (si unlike). Marque `toggling = true`.
3. **Succès 200 / 201** : confirme l'état optimiste. `toggling = false`.
4. **Succès 200 idempotent (déjà liké)** : confirme l'état optimiste. `toggling = false`.
5. **Erreur 4xx / 5xx / réseau** : rollback de `liked` et `likeCount` à la valeur pré-toggle. `toggling = false`. Toast d'erreur *« Impossible d'enregistrer le like. »* (variant `error`).
6. **Bouton `disabled` pendant `toggling`** : pas de double-click possible (sentinel test).

**Justification.** L'UX standard pour les like buttons (Twitter / Discord) est l'optimistic update — le clic se sent instantané même sur réseau lent. Le rollback gracieux préserve la cohérence en cas d'échec sans laisser l'UI désaligné de la DB.

| Option | Verdict |
|---|---|
| (a) Optimistic + rollback sur erreur | ✅ retenu |
| (b) Pessimistic (attendre la réponse avant de flipper) | ❌ — UX laggy |
| (c) Optimistic sans rollback (fire-and-forget) | ❌ — laisse l'UI désaligné de la DB |

**Edge case 409.** Le backend retourne 200 (idempotent) si le caller a déjà liké et POST encore. Le frontend traite 200/201 identiquement comme "succès" — pas de rollback nécessaire. Sentinel test : `like_doubleClick_resolves200_doesNotDoubleIncrement`.

### Décision D — Autocomplete debounce 300 ms + minimum 2 chars

**Décision.** Le `MentionAutocomplete` ne déclenche la requête `/users/search?q=` qu'après :

- **300 ms** de stabilité sur le préfixe en cours (debounce identique à `UsernameAutocomplete` SCRUM-137).
- **≥ 2 chars** après le `@`. Un seul char produit trop de résultats peu utiles + spam user-service (cap 60 req/min côté backend).

**Justification.** Cohérence avec le pattern existant ; cap d'appels backend ; UX pas brutale (l'utilisateur tape 1 char et ne voit pas immédiatement un dropdown vide).

### Décision E — `MentionAutocomplete` est un **nouveau composant** (pas d'extension d'`UsernameAutocomplete`)

**Décision.** Crée un composant frontal **distinct** : `frontend/src/components/event/MentionAutocomplete.tsx`. Il gère :

- Lecture de la position du caret dans un `<textarea>` parent (via `ref` + `selectionStart`).
- Détection du token `@<prefix>` actif (regex sur `value.slice(0, caretPos)` pour trouver le dernier `@` non précédé d'un char de mot, et le préfixe à droite de ce `@`).
- Si préfixe valide (≥ 2 chars, charset `[a-z0-9._-]`), affiche un dropdown sous le textarea.
- Sur sélection (click / Enter), remplace la sous-chaîne `@<prefix>` par `@<username> ` (espace final), repositionne le caret juste après l'espace.
- Sur Esc, ferme le dropdown sans modifier le contenu.
- Si plusieurs `@<prefix>` actifs dans le texte, **seul le token autour du caret** trigger le dropdown.

**Justification — pourquoi pas étendre `UsernameAutocomplete` ?**

| Aspect | `UsernameAutocomplete` (SCRUM-137, existant) | `MentionAutocomplete` (SCRUM-147, nouveau) |
|---|---|---|
| Shape | Input-replacement (le composant **est** l'input, contrôle sa valeur entière) | Trigger inline dans un textarea (le composant **observe** la valeur du textarea parent et propose un dropdown) |
| Cible | Champ unique "Nom d'utilisateur" dans `CoOrganizersEditor` | Sous-chaîne dans `CommentForm.textarea` |
| Position du caret | Toujours en fin de valeur (input ne supporte pas multi-token) | Au milieu de la valeur, peut-être plusieurs `@<prefix>` |
| Remplacement | Toute la valeur du champ devient le username choisi | Substring replace de `@<prefix>` → `@<username> ` |

Forcer un seul composant à supporter les deux shapes ferait exploser sa complexité. **Décision : duplication contrôlée du pattern debounce + cancellation + ARIA**, mais composants séparés.

| Option | Verdict |
|---|---|
| (a) Nouveau composant `MentionAutocomplete` | ✅ retenu |
| (b) Étendre `UsernameAutocomplete` avec un mode `inline` | ❌ — sur-couplage |
| (c) Refactor majeur en `BaseUsernameSearch` + 2 surfaces | ❌ — over-engineering, rule-of-three pas encore (2 surfaces seulement) |

### Décision F — Dropdown autocomplete positionné **sous le textarea** (pas flottant près du caret)

**Décision.** Le dropdown s'affiche en position absolue `top-full left-0 right-0` sous le textarea, **PAS** en popup flottant suivant la position du caret.

**Justification.**

| Option | Pour | Contre | Verdict |
|---|---|---|---|
| (a) Sous le textarea | Simple à implémenter ; alignement clair ; pattern identique à `UsernameAutocomplete` | Visuellement déconnecté du caret en bas de textarea long | ✅ retenu (KISS) |
| (b) Popup au caret (mesure `getBoundingClientRect` + offset character-by-character) | UX premium, "feels native" | Complexité significative (font metrics, line wrapping, scrolling) ; bugs typographiques inévitables ; pas de bénéfice utilisateur mesurable sur un textarea 3-lignes max | ❌ |

### Décision G — Réutilisation du `ReportModal` existant (avec adaptation `target`), **PAS** une nouvelle modal "simple yes/no"

**Décision.** **Diverge du locked-in #G du prompt** ("no reason field in MVP — simple yes/no"). Le backend `POST /comments/{commentId}/report` (SCRUM-144 livré) **exige** un body `CreateReportRequest` avec un `reason` non-null + Bean Validation. Forcer un default reason côté frontend pour éviter l'UX du champ serait :

- Trompeur — toutes les rows `reports.reason` deviendraient identiques, polluant le dashboard admin.
- Fragile — si le backend ajoute des reasons enum, le default deviendrait invalide silencieusement.
- Incohérent — le report d'**event** demande déjà le motif + la description (`ReportModal.tsx` existant).

**Solution retenue.** Réutiliser le `ReportModal` existant en lui ajoutant deux props :

```ts
interface ReportModalProps {
  target: 'event' | 'comment';   // NEW — change le titre + l'endpoint via callback
  onClose: () => void;
  onSubmit: (reason: ReportReason, description?: string) => Promise<void>;
  submitting: boolean;
}
```

Le `CommentItem` passe `target='comment'` et un `onSubmit` qui appelle `reportApi.reportComment(commentId, { reason, description })`. Le `ReportModal` affiche dynamiquement le titre `"Signaler ce commentaire"` au lieu de `"Signaler cet événement"`.

**Codes d'erreur applicatifs côté UI** :
- `409 already_reported` (UK partiel sur `(reporterId, commentId)`) → toast `"Vous avez déjà signalé ce commentaire."` + close modal.
- `422 cannot_report_own_comment` → ne devrait pas se produire (le bouton est masqué pour le comment author — Décision I), mais filet : toast `"Vous ne pouvez pas signaler votre propre commentaire."` + close.
- 4xx/5xx générique → toast `"Erreur lors du signalement, réessayez."` + le modal reste ouvert pour retry.
- 201 succès → toast `"Commentaire signalé. Merci."` + close modal.

| Option | Verdict |
|---|---|
| (a) Réutiliser `ReportModal` avec `target` prop | ✅ retenu — cohérent backend + reviewer admin a le motif |
| (b) Nouvelle modal "simple yes/no" + default reason hardcoded | ❌ — pollue le dashboard admin, fragile |
| (c) Bypass complet (POST avec body invalide, attendre que ça soit accepté plus tard) | ❌ — backend rejette 400, feature cassée |

**Re-litigation flag.** Cette décision **diverge** du locked-in #G du prompt. Si Daniel refuse cette divergence après lecture, deux fallbacks possibles : (b) ci-dessus (à ses risques), ou ajouter un endpoint `POST /comments/{id}/report-simple` côté backend (changement de scope, bouleverse la PR).

### Décision H — Mention insert format : `@<username> ` (avec espace final, **lowercased**)

**Décision.** Quand l'utilisateur sélectionne une suggestion dans le dropdown autocomplete :

1. La sous-chaîne `@<typedPrefix>` (depuis le dernier `@` jusqu'au caret) est remplacée par `@<username> ` :
   - `@` littéral
   - `<username>` exactement comme stocké côté backend (déjà lowercased par SCRUM-169 — la regex est `^[a-z0-9._-]{3,30}$`)
   - **un espace final** pour positionner le caret prêt à taper la suite
2. Le caret est repositionné juste après l'espace.
3. Idem pour le préfixe de reply : `setShowReplyForm(true)` → le `CommentForm` ouvre avec `initialValue = '@<parentAuthorUsername> '`.

**Cas-limites** :
- L'utilisateur tape `@al`, voit le dropdown, **n'ouvre pas** le dropdown (ne presse pas Enter), continue à taper `@alice.dosh `. Le backend parse `@alice.dosh` correctement (SCRUM-145 regex). Le frontend laisse passer.
- L'utilisateur tape `@`, n'envoie pas de chars, le dropdown ne s'ouvre pas (< 2 chars min — Décision D).
- L'utilisateur sélectionne, puis efface le `@<username> ` complètement avec backspace : aucun caractère résiduel.

| Option | Verdict |
|---|---|
| (a) `@<username> ` avec espace final + lowercased | ✅ retenu |
| (b) `@<username>` sans espace | ❌ — friction (utilisateur doit ajouter l'espace) |
| (c) `@<displayName>` | ❌ — déphasé avec SCRUM-145 (cf. § 0 point 1) |

### Décision I — Authentification gates par action

**Décision.** Tableau de gating, miroir du pattern `FavoriteButton` / `RSVPButton` :

| Action | Non-authentifié | Authentifié non-author | Authentifié author |
|---|---|---|---|
| **Like** | Bouton visible mais `disabled`, tooltip *« Connectez-vous pour aimer »* | ✅ Cliquable | ✅ Cliquable (peut liker son propre commentaire) |
| **Reply** | Bouton **masqué** (`canReply = !isReply && currentUserId !== null` existant) | ✅ Cliquable | ✅ Cliquable |
| **Report** | Bouton **masqué** | ✅ Cliquable | **Masqué** (filet anti `422 cannot_report_own_comment`) |
| **Autocomplete `@`** | N/A — le `CommentForm` n'est rendu que pour authentifié (`CommentSection.tsx` gère le gate parent) | Actif | Actif |
| **Delete** (SCRUM-146 existant) | Masqué | Masqué (sauf si admin ou organisateur) | ✅ Cliquable |

**Justification.** Cohérent avec le reste de l'app (RSVP, favorite, etc.) — un visiteur non-authentifié voit l'engagement social mais ne peut pas y participer (incitation à se connecter).

### Décision J — Réutilisation systématique des primitives UI existantes — **PAS** de réinvention

**Décision.** L'implémenteur **n'a pas le droit** de créer :

- Une nouvelle modal (utiliser `ReportModal` adapté + `ConfirmDialog` pour les autres usages).
- Un nouveau toast (utiliser `useToast`).
- Un nouveau dropdown (le pattern `<ul role="listbox">` d'`UsernameAutocomplete` peut être copié dans `MentionAutocomplete` ; pas de framework de composant générique).
- Un nouveau hook `useDebounce` (utiliser celui existant).
- Un nouveau composant Avatar (utiliser `UserAvatar`).

**Justification.** Convention frontend AGENTS.md : "Toujours créer des composants réutilisables pour les éléments répétés". Le corollaire : ne **PAS** dupliquer un composant existant. Le seul nouveau composant ici est `MentionAutocomplete` parce que sa shape (trigger inline dans textarea) est strictement différente de toutes les autres surfaces (cf. Décision E).

### Décision K — Stratégie de fallback si la shape de `/users/search` dérive de l'attendu

**Décision.** Au démarrage de l'implémentation, l'étape 1 (cf. § 5) **vérifie** :

1. `searchUsernames('al', 8)` retourne `UserPublicResponse[]` avec `id`, `username`, `displayName`, `avatarUrl` peuplés.
2. La projection `fromAnonymous` (SCRUM-169 Décision E) est bien celle utilisée — `username` toujours présent, `bio` / `interests` / etc. à `null`.

Si l'une des conditions échoue (le backend a régressé), l'agent **stoppe** et signale à Daniel avant d'écrire le composant. Si tout OK, on continue.

**Justification.** Coût zéro de vérification, économise un rework si le contrat a dérivé. Cohérent avec le pattern `git diff origin/main HEAD -- openapi/openapi.yaml` du SCRUM-145.

---

## 4. Inventaire des changements

### 4.1 OpenAPI (aucun changement)

| Fichier | Changement |
|---|---|
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | **AUCUN.** Toute la surface backend est déjà figée par SCRUM-139 / SCRUM-144 / SCRUM-137 / SCRUM-169. |

### 4.2 Frontend — services

| Fichier | Type | Motif |
|---|---|---|
| [`commentApi.ts`](frontend/src/services/commentApi.ts) | Update | + `likeComment(commentId: number): Promise<{ liked: boolean; likeCount: number }>` (POST `/comments/{id}/like`). + `unlikeComment(commentId: number): Promise<void>` (DELETE `/comments/{id}/like` → 204). |
| [`reportApi.ts`](frontend/src/services/reportApi.ts) | Update | + `reportComment(commentId: number, body: CreateReportRequest): Promise<void>` (POST `/comments/{id}/report`). Réutilise le type `CreateReportRequest` existant. |

### 4.3 Frontend — hooks

| Fichier | Type | Motif |
|---|---|---|
| `frontend/src/hooks/useCommentLike.ts` | **Nouveau** | Hook `useCommentLike(initialLiked: boolean, initialCount: number, commentId: number)` retournant `{ liked, likeCount, toggling, toggle }`. Optimistic + rollback (cf. Décision C). |
| `frontend/src/hooks/useReportComment.ts` | **Nouveau** | Hook `useReportComment(commentId)` retournant `{ submitting, submit(reason, description?) }`. Wrap `reportApi.reportComment` + gestion 409 / 422 / 4xx via toasts. |

### 4.4 Frontend — composants

| Fichier | Type | Motif |
|---|---|---|
| `frontend/src/components/event/MentionAutocomplete.tsx` | **Nouveau** | Composant trigger inline pour le textarea du `CommentForm`. Cf. Décisions D/E/F/H. Props : `value: string`, `onChange(newValue)`, `textareaRef: RefObject<HTMLTextAreaElement>`, `disabled?: boolean`. |
| [`CommentForm.tsx`](frontend/src/components/event/CommentForm.tsx) | Update | + Intégration du `MentionAutocomplete` via une ref sur le textarea. + Prop optionnelle `initialValue?: string` (utilisée pour le préfixe `@<author> ` des replies). |
| [`CommentItem.tsx`](frontend/src/components/event/CommentItem.tsx) | Update | + Bouton like dans le footer (icône `Heart` lucide, count à droite, optimistic toggle via `useCommentLike`). + Pré-remplissage `@<parentAuthorUsername> ` du `CommentForm` reply quand `showReplyForm` est ouvert. + Remplace le `handleReport` placeholder par l'ouverture du `ReportModal` (target='comment'). + Masquage du bouton report pour le comment author (Décision I). |
| [`ReportModal.tsx`](frontend/src/components/event/ReportModal.tsx) | Update | + Prop `target: 'event' \| 'comment'`. Adapte le titre du modal (`"Signaler cet événement"` vs `"Signaler ce commentaire"`). Aucune autre modification — la shape du form (reason + description) reste identique aux 2 cibles. |

### 4.5 Frontend — types (aucune modification)

| Fichier | État |
|---|---|
| [`types/comment.ts`](frontend/src/types/comment.ts) | **Inchangé** — `likedByMe`, `likeCount`, `parentCommentId`, `authorUsername` y figurent déjà. |
| [`types/user.ts`](frontend/src/types/user.ts) | **Inchangé** — `UserPublicResponse.username` y figure depuis SCRUM-169. |
| [`types/report.ts`](frontend/src/types/report.ts) | **Inchangé** — `ReportReason`, `REPORT_REASONS`, `CreateReportRequest` y figurent depuis SCRUM-144. |

### 4.6 Frontend — tests à ajouter

| Fichier | Type | Motif |
|---|---|---|
| `__tests__/services/commentApi.test.ts` | Update | + 4 cases (`likeComment` happy / idempotent 200, `unlikeComment` happy / 404 silent). |
| `__tests__/services/reportApi.test.ts` (créer si absent) | Nouveau / Update | + 3 cases (`reportComment` happy 201, 409 already-reported, 422 cannot-report-own). |
| `__tests__/hooks/useCommentLike.test.ts` | **Nouveau** | 8 cases (cf. § 6). |
| `__tests__/hooks/useReportComment.test.ts` | **Nouveau** | 4 cases (cf. § 6). |
| `__tests__/components/event/MentionAutocomplete.test.tsx` | **Nouveau** | 12 cases (cf. § 6). |
| `__tests__/components/event/CommentForm.test.tsx` | Update | + 3 cases : initialValue prop, autocomplete trigger integration, plusieurs `@` indépendants. |
| `__tests__/components/event/CommentItem.test.tsx` | Update | + ~8 cases : like button visible / disabled / optimistic / rollback, reply prefix correct, report modal ouverture / 409 / 422 / success, hide report pour l'author. |
| `__tests__/components/event/ReportModal.test.tsx` (créer si absent) | Update | + 2 cases : `target='comment'` change le titre, `target='event'` reste l'ancien. |

### 4.7 Frontend — documentation

| Fichier | Section | Modif |
|---|---|---|
| [`frontend/docs/components.md`](frontend/docs/components.md) | Section composants partagés / event | + entrée `MentionAutocomplete`. + Mise à jour de `CommentItem` (likes + report wiring). + Update de `CommentForm` (autocomplete + initialValue). |
| [`frontend/docs/sprint-context.md`](frontend/docs/sprint-context.md) | Section finale | Ajout section datée `2026-05-XX — SCRUM-147 livré (frontend commentaires avancés : likes, threads, autocomplete mentions, signalement)`. Lister les fichiers touchés, les décisions clés A → K, et la confirmation du coupling PR avec SCRUM-145. |

---

## 5. Plan d'exécution séquentiel (étapes numérotées, ordre strict)

> **Règle.** Un commit par étape. Format de message : `feat(scrum-147): <description>` ou `test(scrum-147):` / `refactor(scrum-147):` / `docs(scrum-147):` selon le type. Co-author Claude sur chaque commit. Vérification post-commit : la commande indiquée pour l'étape. **Push à la fin de chaque étape** (ou en bloc à la fin — la PR existe déjà donc le push met juste à jour la PR #193).

### Étape 0 — Vérification de la shape du backend search (Décision K)

- **Pas un commit** — étape de pré-flight.
- **Modifs** : aucune.
- **Vérification** : lancer le frontend en `npm run dev`, ouvrir une console, exécuter `await fetch('/api/users/search?q=al&limit=8', { headers: { Authorization: 'Bearer <token>' }}).then(r => r.json())`. Vérifier que chaque entrée a `id`, `username`, `displayName`, `avatarUrl` peuplés. Si la shape diffère → **stopper** et signaler à Daniel.
- **Garde-fou** : ne PAS continuer si la shape a régressé.

### Étape 1 — `commentApi.ts` + `reportApi.ts` extensions

- **Commit** : `feat(scrum-147): add likeComment, unlikeComment, reportComment to API clients`
- **Modifs** : sections 4.2.
- **Vérification** : `npm run lint` + ajout des tests de service (`__tests__/services/commentApi.test.ts` + `reportApi.test.ts`) → `npm run test src/__tests__/services/commentApi.test.ts src/__tests__/services/reportApi.test.ts`.

### Étape 2 — `useCommentLike` hook + tests

- **Commit** : `feat(scrum-147): add useCommentLike hook with optimistic update and rollback`
- **Modifs** : `frontend/src/hooks/useCommentLike.ts` + `__tests__/hooks/useCommentLike.test.ts`.
- **Vérification** : `npm run test src/__tests__/hooks/useCommentLike.test.ts`.

### Étape 3 — Bouton like dans `CommentItem` + tests

- **Commit** : `feat(scrum-147): show like button on every comment with optimistic toggle`
- **Modifs** : `CommentItem.tsx` (footer), `__tests__/components/event/CommentItem.test.tsx` (extension).
- **Vérification** : `npm run test src/__tests__/components/event/CommentItem.test.tsx`.
- **Détails UX** : icône `Heart` lucide (filled = liked, outline = not liked) ; count à droite ; couleur `text-error` quand liked, `text-foreground/50 hover:text-error` sinon ; tooltip `"Connectez-vous pour aimer"` quand non-auth.

### Étape 4 — `MentionAutocomplete` (composant + tests)

- **Commit** : `feat(scrum-147): add MentionAutocomplete component for inline @prefix trigger in textareas`
- **Modifs** : `frontend/src/components/event/MentionAutocomplete.tsx` + tests.
- **Vérification** : `npm run test src/__tests__/components/event/MentionAutocomplete.test.tsx`.
- **Détails UX (à appliquer avec skill `frontend-design`)** :
  - Position : `absolute top-full left-0 right-0 mt-1` (sous le textarea).
  - Background : `bg-background border border-border rounded-2xl shadow-xl`, max-height `max-h-80 overflow-y-auto`.
  - Ligne : avatar (size-8) + displayName (gras text-sm) + `@username` (text-xs text-foreground/60).
  - Active state : `bg-foreground/10` ; hover : `bg-foreground/5`.
  - Aucun bruit "Aucun utilisateur trouvé" — si 0 résultat, le dropdown se ferme (Décision F simplifiée par rapport à `UsernameAutocomplete`).

### Étape 5 — Wire `MentionAutocomplete` dans `CommentForm` + `initialValue` prop

- **Commit** : `feat(scrum-147): wire MentionAutocomplete into CommentForm and add initialValue prop`
- **Modifs** : `CommentForm.tsx` + tests.
- **Vérification** : `npm run test src/__tests__/components/event/CommentForm.test.tsx`.

### Étape 6 — Préfixe `@<parentAuthorUsername> ` sur le reply form dans `CommentItem`

- **Commit** : `feat(scrum-147): prefill reply form with @parentAuthorUsername`
- **Modifs** : `CommentItem.tsx` (calcul du préfixe + pass à `CommentForm.initialValue`).
- **Vérification** : `npm run test src/__tests__/components/event/CommentItem.test.tsx`.

### Étape 7 — `useReportComment` hook + tests

- **Commit** : `feat(scrum-147): add useReportComment hook with 409/422 handling`
- **Modifs** : `frontend/src/hooks/useReportComment.ts` + tests.
- **Vérification** : `npm run test src/__tests__/hooks/useReportComment.test.ts`.

### Étape 8 — `ReportModal` accepte `target` + re-câblage du bouton "Signaler" dans `CommentItem`

- **Commit** : `feat(scrum-147): wire ReportModal target='comment' from CommentItem`
- **Modifs** : `ReportModal.tsx` (prop `target`), `CommentItem.tsx` (remplacement du `handleReport` placeholder).
- **Vérification** : `npm run test src/__tests__/components/event/ReportModal.test.tsx src/__tests__/components/event/CommentItem.test.tsx`.

### Étape 9 — Documentation

- **Commit** : `docs(scrum-147): document MentionAutocomplete, CommentItem updates, sprint context`
- **Modifs** : `frontend/docs/components.md`, `frontend/docs/sprint-context.md`.
- **Vérification** : `git diff` cohérent.

### Étape 10 — Verification finale + push

- **Pas un commit** — vérification globale (cf. § 7).
- **Push** : `git push origin feature/s7-comment-mentions` → met à jour PR #193 (pas de `gh pr create`).
- **`gh pr edit 193`** : adapter le titre pour refléter le scope SCRUM-145 + SCRUM-147, et ajouter une section dans le body décrivant les axes frontend.

### Étape 11 — Boucle review Copilot + CI

- Itérer `gh pr checks 193 --watch` + `gh api repos/<org>/unige-events/pulls/193/comments` jusqu'à 0 BLOQUANT / 0 IMPORTANT.

---

## 6. Tests

### 6.1 Frontend — `useCommentLike.test.ts`

| Test | Setup | Assertion |
|---|---|---|
| `initialState_liked_exposesLikedTrue` | `useCommentLike(true, 5, 7)` | `liked=true`, `likeCount=5`, `toggling=false` |
| `toggle_unlikedToLiked_optimisticUpdate_then200` | mock POST 200 | flip immédiat à `liked=true, count+1`, puis stable après resolution |
| `toggle_unlikedToLiked_idempotent200_doesNotDoubleIncrement` | mock POST 200 (déjà liké backend) | `liked=true, likeCount=initial+1` puis confirmation sans double-incrément |
| `toggle_likedToUnliked_optimistic_then204` | mock DELETE 204 | flip immédiat à `liked=false, count-1`, stable |
| `toggle_4xx_rollback` | mock POST 500 | flip immédiat puis rollback à l'état pré-clic + toast erreur |
| `toggle_network_rollback` | mock axios reject | idem |
| `toggle_doubleClick_secondClickNoop` | premier clic in-flight, second clic | second clic est ignoré (bouton disabled), une seule requête HTTP |
| `toggle_unauthenticated_doesNothing` | mock `useAuth` returning unauthenticated | aucun appel HTTP, état inchangé |

### 6.2 Frontend — `useReportComment.test.ts`

| Test | Assertion |
|---|---|
| `submit_201_resolvesSuccessAndShowsToast` | mock POST 201 → resolve + toast success |
| `submit_409_resolvesAndShowsAlreadyReportedToast` | mock POST 409 → resolve (modal can close) + toast "Vous avez déjà signalé…" |
| `submit_422_resolvesAndShowsCantReportOwnToast` | mock POST 422 → resolve + toast adapté |
| `submit_5xx_rejectsAndShowsErrorToast` | mock POST 500 → reject, modal reste ouvert |

### 6.3 Frontend — `MentionAutocomplete.test.tsx`

| Test | Assertion |
|---|---|
| `noAt_noDropdown` | tape `"hello world"` → dropdown jamais ouvert |
| `atWithoutPrefix_noDropdown` | tape `"hello @"` → dropdown fermé (0 chars après `@`) |
| `at1Char_noDropdown` | tape `"@a"` → dropdown fermé (< 2 chars min — Décision D) |
| `at2Chars_triggersDebounced` | tape `"@al"`, attendre 300 ms via `vi.useFakeTimers` → `searchUsernames` appelé 1 fois |
| `at2Chars_typesQuickly_debounceCollapses` | tape `@a`, `@al`, `@ali`, `@alic` en 50 ms each → un seul `searchUsernames` à la fin |
| `selection_replacesPrefix_addsTrailingSpace_repositionsCaret` | sélectionner `alice.dosh` après `@al` → la valeur devient `"@alice.dosh "` et le caret est juste après l'espace |
| `arrowDown_then_enter_inserts` | ↓ ↓ Enter → insertion de la 3e suggestion |
| `escape_closesDropdown_keepsText` | tape `@al`, dropdown ouvre, Esc → dropdown ferme, le texte reste `@al` |
| `clickOutside_closesDropdown` | mousedown hors composant → dropdown ferme |
| `multipleAt_onlyTokenAroundCaret_triggers` | tape `"@alice.dosh hi @bob"` avec caret après `bob` → dropdown sur préfixe `bob`, pas `alice.dosh` |
| `selection_replacesOnlyCurrentToken_notOtherText` | tape `"hello @al world"`, caret après `al` → sélection ne touche pas à `world` |
| `noResults_dropdownClosed` | mock `searchUsernames` returning `[]` → dropdown fermé (pas de "Aucun résultat" noise) |

### 6.4 Frontend — `CommentForm.test.tsx` (extensions)

| Test | Assertion |
|---|---|
| `initialValue_prefillsTextarea` | render avec `initialValue="@alice.dosh "` → textarea contient cette valeur |
| `mentionAutocomplete_triggersOnAt` | tape `"@al"` dans le textarea → composant `MentionAutocomplete` rendu |
| `submit_keepsAutocompleteValueAsTextOnly` | tape `"@alice.dosh hi"`, submit → `onSubmit('@alice.dosh hi')` (pas de DOM enrichi parasitant) |

### 6.5 Frontend — `CommentItem.test.tsx` (extensions)

| Test | Assertion |
|---|---|
| `likeButton_visibleForAllUsers_evenAnonymous` | render avec `currentUserId=null` → bouton like présent mais `disabled` + tooltip |
| `likeButton_clickToggles_optimistic` | render auth, click → flip immédiat de l'icône (filled) |
| `likeButton_rollbackOn500` | mock 500 → flip puis rollback |
| `likeButton_disabledDuringInFlight` | click → bouton disabled pendant la mutation |
| `replyButton_hiddenForAnonymous` | `currentUserId=null` → bouton "Répondre" absent |
| `replyButton_hiddenOnReply` | `isReply=true` → bouton "Répondre" absent (depth-1) |
| `replyForm_prefillsParentUsername` | click "Répondre" sur comment de `@alice.dosh` → textarea contient `"@alice.dosh "` |
| `replyForm_emptyParentUsername_noPrefix` | parent sans `authorUsername` → textarea ouvert vide |
| `reportButton_hiddenForAnonymous` | `currentUserId=null` → bouton "Signaler" absent |
| `reportButton_hiddenForAuthor` | comment author = currentUser → bouton "Signaler" absent (Décision I) |
| `reportButton_opensReportModalWithTargetComment` | click → `ReportModal` rendu avec titre `"Signaler ce commentaire"` |
| `reportModal_submitSuccess_closesAndToasts` | submit valide → modal close + toast success |
| `reportModal_409_closesAndToastsAlreadyReported` | mock 409 → modal close + toast spécifique |

### 6.6 Frontend — `ReportModal.test.tsx` (extensions)

| Test | Assertion |
|---|---|
| `target_event_showsEventTitle` | render avec `target='event'` → titre `"Signaler cet événement"` |
| `target_comment_showsCommentTitle` | render avec `target='comment'` → titre `"Signaler ce commentaire"` |

### 6.7 Cas-limites explicites (récap)

- Like : optimistic + rollback ; double-click ; non-auth tooltip — § 6.5 + § 6.1.
- Reply : préfixe `@<username> ` ; depth-1 enforcement ; non-auth — § 6.5.
- Autocomplete : trigger ≥ 2 chars ; debounce 300 ms ; ↑/↓/Enter/Esc ; click-outside ; multi-`@` ; 0 résultats — § 6.3.
- Report : modal `target='comment'` ; 409 ; 422 ; auteur masqué ; non-auth masqué — § 6.5 + § 6.2.

---

## 7. Critères de done

- [ ] `cd frontend && npm run lint` — 0 erreur.
- [ ] `cd frontend && npm run test -- --run` — tous les tests verts, total bumped d'au moins ~40 cases.
- [ ] Coverage V8 ≥ 80 % L sur le nouveau code (hooks + composants).
- [ ] `grep -rn "// TODO: SCRUM-144" frontend/src/` = 0 (le placeholder report est levé).
- [ ] **Aucune modification de** `openapi/openapi.yaml` : `git diff origin/main HEAD -- openapi/openapi.yaml` ne contient que les modifs SCRUM-149 (déjà mergées via #192 ou empilées). Diff strictement SCRUM-147 sur openapi : 0 ligne.
- [ ] Smoke check qualitatif en preview (PR #193) :
  - [ ] Liker un commentaire incrémente le compteur immédiatement, persiste après reload.
  - [ ] Répondre à un commentaire ouvre le form avec `@<author> ` pré-rempli.
  - [ ] Taper `@al` dans un comment ouvre le dropdown avec `alice.*` ; clic insère `@alice.dosh `.
  - [ ] Signaler un commentaire ouvre `ReportModal` ; choisir un motif + soumettre → toast success.
  - [ ] Signaler 2× le même commentaire → toast "déjà signalé".
- [ ] Documentation cohérente : `components.md` + `sprint-context.md`.
- [ ] PR #193 titre / body adaptés via `gh pr edit 193` pour refléter le scope élargi.
- [ ] **Pas de merge** par l'agent. Daniel merge lui-même.
- [ ] Boucle review Copilot itérée jusqu'à 0 BLOQUANT / 0 IMPORTANT.

---

## 8. Workflow Git

- **Branche** : `feature/s7-comment-mentions` (existante, partagée avec SCRUM-145).
- **1 commit par étape** du Plan d'exécution.
- **Format de message** : `<type>(scrum-147): <description courte>`. Types autorisés : `feat`, `fix`, `docs`, `refactor`, `test`, `chore`, `ci`. Scope obligatoire `scrum-147`.
- **Co-author Claude** sur chaque commit (HEREDOC standard).
- **Push** : `git push origin feature/s7-comment-mentions` à la fin de chaque étape OU en bloc final. La PR #193 existe déjà ; chaque push met à jour la PR.
- **Adapter PR #193** à la fin : `gh pr edit 193 --title "..." --body "..."` pour refléter le scope élargi backend + frontend.
- **Pas de `gh pr create`.**
- **Pas de merge** par l'agent.

---

## 9. Risques et concerns

### 🔴 Risque critique — Deploy coupling backend + frontend

**Description.** Bundler SCRUM-145 (backend Kafka consumers) et SCRUM-147 (frontend UI) dans la même PR #193 signifie :

- **Si le frontend a un bug post-merge** (ex. `MentionAutocomplete` casse sur Safari ancien) et qu'il faut un hotfix, le revert/rollback touche aussi le backend, alors que les consumers Kafka backend tournent peut-être déjà et émettent des notifications.
- **Inversement**, si un bug backend se révèle en preview (ex. mention parser regex casse sur un cas exotique), un revert annule aussi le frontend qui est entre-temps validé.

**Acceptation.** Daniel a explicitement refusé un split, malgré le pushback initial du Claude écrivant cette spec. La présente section re-documente le risque ; **elle ne re-débatte pas la décision.**

**Mitigations applicables** :
- Bien tester en preview avant le merge — la PR #193 dispose d'un deploy automatique vers Cloudflare Tunnel.
- Coordonner avec l'équipe backend + frontend pour valider la PR ensemble (reviewers mixtes — autre risque ci-dessous).
- Garder les commits granulaires (1 commit par étape) pour pouvoir cherry-pick un revert ciblé si nécessaire.
- Documenter dans le commit final de la PR la matrice "commit X → feature Y" pour faciliter un éventuel rollback partiel.

### 🟡 Mixed-stack reviewers

**Description.** PR #193 a déjà ~11 commits backend (SCRUM-145) + 1 commit frontend (PSN-style `@username` sur ProfilePage, `b5f9f7b6`). SCRUM-147 ajoutera ~10 commits frontend supplémentaires. La revue exigera **au moins** :

- Un reviewer back (validation pipeline Kafka, REST internal, gestion d'erreurs).
- Un reviewer front (validation hooks, composants, accessibilité, optimistic UI).

**Mitigation.** Demander explicitement aux deux profils de reviewer dans la description de PR (mention Slack si l'org en a un, sinon `gh pr review-request`).

### 🟡 Taille de PR

**Estimation post-SCRUM-147** :

| Tranche | Commits | Files touchés | LoC ~ |
|---|---|---|---|
| SCRUM-149 (attachments, déjà sur cette branche) | ~11 | ~30 | ~2500 |
| SCRUM-145 (backend mentions) | ~10 | ~20 | ~1500 |
| SCRUM-147 (frontend commentaires) | ~10 | ~15-20 | ~1500 |
| **TOTAL PR #193** | **~31** | **~65** | **~5500** |

C'est **bien au-dessus** du seuil "raisonnable" pour une PR (~1000 LoC max idéalement). Reviewers vont avoir mal à charger ça en une session. **Mitigation** : ouvrir un commentaire en haut de la PR avec une map "quelle catégorie de commit pour quel reviewer" pour orienter la lecture.

### 🟠 Optimistic update edge cases

- **Network flap** : POST en cours, réseau tombe, retry Axios ne re-déclenche pas (pas de retry policy frontale). Le rollback se produit après le timeout Axios (10s par défaut). Documenter dans la doc utilisateur : *« Le compteur peut sauter brièvement si la connection est instable. »*
- **Backend 5xx partial** : Notification créée backend mais réponse perdue → l'optimistic flippe à `liked=true`, le rollback s'enclenche, mais en DB le like existe. Au prochain reload, le caller voit `likedByMe=true` à nouveau. Acceptable (cohérence éventuelle). Documenter.

### 🟠 Rate-limit user-service search

- Le backend `/users/search` est rate-limité à **60 req/min par utilisateur authentifié** (cf. spec SCRUM-137). Avec debounce 300 ms + min 2 chars, un utilisateur normal n'atteindra jamais ce cap (max ~3 req/sec en frappant à 200 wpm). Mais un utilisateur "malveillant" qui spam pourrait. Le backend renvoie 429 → le composant l'affiche silencieusement et ferme le dropdown (l'utilisateur peut taper la suite à la main). Sentinel test à pinner.

### 🟠 Cursor placement edge cases sur `@<username> ` insertion

- L'utilisateur tape `"hello @al world"`, place le caret entre `@al` et `world` (= position 10). Sélectionne `alice.dosh` dans le dropdown. Le composant remplace `@al` (positions 6-9) par `@alice.dosh `, repositionne le caret à la position 6 + 13 = 19. Le `world` reste intact à la fin. Sentinel test : `selection_replacesOnlyCurrentToken_notOtherText` (§ 6.3).
- **Cas piège** : si le caret est *avant* le `@` (l'utilisateur scrolle vers le début), aucun token actif n'est détecté → dropdown fermé. Vérifié dans le composant.

### 🟠 Conflit avec autres specs en cours

- **PR #192** (SCRUM-149 attachments) doit merger en premier ou ses commits sont rebased trivialement. Aucun fichier conflictuel sur les fichiers SCRUM-147 (les attachments touchent `EventCreatePage`, `EventDetailPage`, `EventEditPage` et `EventForm` — aucun de ces fichiers n'est touché par SCRUM-147).
- **`ReportModal.tsx`** n'est touché par aucune autre PR ouverte (vérifié au moment de la rédaction).

---

## 10. Future work / known follow-ups

| Ticket suggéré | Description |
|---|---|
| `[FRONT][S10+]` Mention rendering cliquable | Parser le contenu d'un `CommentItem.content` côté client (regex miroir SCRUM-145) et wrapper chaque `@<handle>` dans `<Link to="/profile/<handle>">`. Compose-bien avec SCRUM-145 (les notifs sont déjà envoyées, ne dépend que de l'UX). |
| `[FRONT][S10+]` Mention autocomplete sur ProfileEditPage bio textarea | La bio profil supporte aussi les mentions à terme. Réutilisation directe du `MentionAutocomplete` SCRUM-147. |
| `[FRONT][S10+]` Édition d'un commentaire | UX rare sur les plateformes modernes, à valider produit. |
| `[FRONT][S10+]` Quote-reply (citer le parent dans la reply) | Au-delà du `@<author> ` préfixe, citer le texte du parent (style Reddit / Substack). |
| `[FRONT][S10+]` Préférences de notification par event | Mute mentions / new-comment via un widget sur EventDetailPage. |
| `[BACK/FRONT][S10+]` Pagination des likers | "Qui a aimé ce commentaire" — modal avec liste paginée. |
| `[FRONT][S10+]` Tooltip "Liker ce commentaire" sur l'icône cœur en hover | Améliore l'accessibilité au-delà de l'`aria-label`. |
| `[BACK][S10+]` Endpoint `POST /comments/{id}/report-simple` | Si Daniel ré-ouvre la Décision G et veut une modal yes/no, créer un endpoint backend qui n'exige pas de `reason`. Hors-scope SCRUM-147. |

---

## 11. Garde-fous

- **Aucune action destructive** : pas de `rm -rf`, `git reset --hard`, `--no-verify`, force-push sur PR #193.
- **Aucune modification du contrat OpenAPI public** : toute la surface est figée. Si l'envie d'ajouter un endpoint surface (ex. `POST /comments/{id}/report-simple` de la Décision G fallback), stopper et discuter avec Daniel avant.
- **Aucun nouveau composant qui dupliquerait une primitive existante** (Décision J).
- **Aucune dépendance npm ajoutée** sans validation explicite (pas de TanStack Query, pas de Floating UI, pas de `@radix-ui` même si tentant pour la modal).
- **`MentionAutocomplete` doit utiliser `searchUsernames` existant** — pas de re-implémentation d'un wrapper fetch.
- **Le `ReportModal` reste générique** — pas de fork "ReportCommentModal" / "ReportEventModal" séparés (Décision J).
- **Cohérence doc / code** : si la doc dérive du code pendant l'implémentation → fix dans le même commit (règle d'or projet).
- **Si Step 0 de vérification (§ 5) montre que le backend search a régressé** → s'arrêter et signaler à Daniel avant d'écrire le composant `MentionAutocomplete`.

---

## 12. Recommended Jira restructure (à exécuter par Daniel)

> **Constat.** Le ticket Jira que Daniel a paste dans le prompt contient des références à `@displayName`, une branche `feature/s8-comments-advanced`, et suggère de créer des primitives qui existent déjà (`ReportModal`, `UsernameAutocomplete`-like). À mettre à jour avant l'implémentation.

### Trouver la clé du ticket

Daniel doit identifier la clé Jira du ticket pasté (probablement `SCRUM-147` mais à vérifier — la spec utilise cette hypothèse partout). Cf. `mcp__claude_ai_Atlassian_Rovo__searchJiraIssuesUsingJql` avec `text ~ "commentaires"` AND `project = SCRUM` AND `status = TO DO`.

### Édits suggérés sur le ticket

1. **Body** :
   - Remplacer toutes les occurrences de `@displayName` → `@username` (×4 au moins).
   - Remplacer `feature/s8-comments-advanced` → `feature/s7-comment-mentions` (avec note "branche partagée avec SCRUM-145").
   - Retirer les mentions "créer ReportModal", "créer UsernameAutocomplete" — ces composants existent et sont réutilisés.
2. **Story Points** : 8 (proposition, à valider).
3. **Sprint** : S9.
4. **Dépendances** : ajouter "depends on SCRUM-145" (notifications backend) + "blocks SCRUM-XXX-front-mention-rendering" (futur ticket S10+).
5. **Commentaire** : *« Spec détaillée : [`specs_archives/specs_claude/specs_scrum-147-comments-advanced.md`](specs_archives/specs_claude/specs_scrum-147-comments-advanced.md). Implémentation : même PR que SCRUM-145 (PR #193, branche feature/s7-comment-mentions). »*

---

## 13. Open questions

Quelques points non-bloquants mais à clarifier en chat **avant** de lancer l'implémentation :

1. **Clé Jira définitive** — confirmer que c'est bien `SCRUM-147` (ou autre). Toutes les références dans cette spec utilisent SCRUM-147 comme placeholder.
2. **Divergence Décision G** — Daniel a verrouillé "no reason field" dans le prompt, mais le backend exige un `reason`. La spec propose de réutiliser `ReportModal` avec ses 2 champs (motif + description). À valider explicitement.
3. **Faut-il un endpoint `POST /comments/{id}/report-simple` côté backend ?** Si oui, ça réintroduit du scope backend dans la PR (qui est déjà mixte) et rejoint la décision déjà longue.
4. **Update du body de PR #193** — l'auto-update va se faire à la fin. Faut-il aussi changer le titre ? Proposition : `feat(scrum-145+147): backend mention notifications + frontend comments advanced (likes, threads, autocomplete, report)`.

---

## 14. Skills à utiliser (lors de l'implémentation, dans une session séparée)

| Skill | Quand |
|---|---|
| `superpowers:executing-plans` | Itérer méthodiquement étape par étape du § 5 |
| `superpowers:test-driven-development` | Pour chaque hook (étapes 2 + 7) et pour `MentionAutocomplete` (étape 4) |
| `frontend-design` | Étape 3 (bouton like — heart icon micro-interaction) + étape 4 (dropdown autocomplete) + étape 8 (modal title swap). Feedback inline, accessibilité, animations |
| `superpowers:systematic-debugging` | Si un test optimistic-rollback casse de manière inattendue |
| `superpowers:verification-before-completion` | **Obligatoire** avant d'envoyer pour review — exécuter toutes les commandes du § 7 |
| `superpowers:requesting-code-review` + `pr-review-toolkit:review-pr` | Lancer la boucle Copilot review sur PR #193 |
| `superpowers:receiving-code-review` | Traiter les retours Copilot |
| `code-simplifier` | Après chaque hook (étapes 2 + 7) — vérifier qu'aucune complexité prématurée n'est introduite |
| `github` MCP | `gh pr edit 193`, `gh pr checks 193 --watch`, `gh api .../pulls/193/comments` |

---

## Launch prompt (literal, à copier-coller pour lancer l'implémentation dans une session séparée)

````markdown
Implémente SCRUM-147 (frontend commentaires avancés : likes, threads,
autocomplete mentions, signalement) en autonomie complète selon la spec
`specs_archives/specs_claude/specs_scrum-147-comments-advanced.md`.

Étapes :

1. Lis la spec en entier avant de toucher au moindre fichier. Internalise les
   Décisions techniques A → K et le Plan d'exécution séquentiel (§ 3 et § 5).
2. Lis `frontend/AGENTS.md` (et `AGENTS.md` racine) pour les conventions de
   commit, scope, hooks bespoke (PAS TanStack), composants utilitaires
   préférés, design tokens.
3. **Pas de nouvelle branche**. Continue sur la branche actuelle
   `feature/s7-comment-mentions` (partagée avec SCRUM-145 — PR #193 ouverte).
4. **Étape 0 obligatoire** (§ 5) : vérifie que `GET /users/search?q=al&limit=8`
   renvoie bien la shape attendue (UserPublicResponse avec id + username +
   displayName + avatarUrl peuplés). Si shape régressée, stoppe et signale-moi.
5. Exécute chaque étape du Plan dans l'ordre exact (§ 5, étapes 1 → 11). Un
   commit par étape, format `<type>(scrum-147): <description>` avec co-author
   Claude. Vérifie après chaque commit avec la commande indiquée
   (`npm run test src/__tests__/<file>` + `npm run lint`).
6. Étape 4 (MentionAutocomplete) : applique le skill `frontend-design` —
   accessibilité ARIA combobox / listbox / option, navigation clavier complète,
   focus management correct au mount/unmount du dropdown.
7. Étape 8 (ReportModal target='comment') : attention à NE PAS forker en 2
   modals — adapter le composant existant avec une prop, c'est tout
   (Décision J).
8. Avant la fin (§ 7 critères de done) : `superpowers:verification-before-
   completion` — exécute toutes les commandes du § 7 et confirme chaque
   ligne. Aucun claim "done" sans cette verification.
9. À la fin : `git push origin feature/s7-comment-mentions` met à jour la
   PR #193 existante (pas de gh pr create). Puis `gh pr edit 193 --title
   "feat(scrum-145+147): backend mention notifications + frontend comments
   advanced" --body "..."` pour refléter le scope élargi.
10. Lance la boucle review Copilot : `gh pr checks 193 --watch`, puis
    `gh api repos/unige-pinfo6-2026/unige-events/pulls/193/comments` pour
    chaque retour. Itère jusqu'à 0 BLOQUANT / 0 IMPORTANT non-clos.
11. Quand tous les checks sont verts et la review propre : signale-moi avec
    le résumé des commits livrés. **Je merge moi-même.**

Garde-fous (rappel) :
- Aucune action destructive sans confirmation explicite.
- Aucune modification de `openapi/openapi.yaml` (la surface backend est
  figée).
- Aucune nouvelle dépendance npm sans validation explicite.
- Réutiliser systématiquement les primitives existantes (ReportModal,
  ConfirmDialog, useToast, useDebounce, UserAvatar) — Décision J.
- Si la doc dérive du code → fix dans le même commit.
- Si un cas non couvert par la spec émerge : documente-le dans
  `sprint-context.md` et continue ; ne me réveille pas pour des
  micro-arbitrages.
- Si la Décision G (réutilisation ReportModal avec target='comment')
  pose problème en revue Copilot, ne pivote PAS sans me consulter —
  c'est la divergence avec le locked-in #G du prompt, déjà acceptée
  dans la spec.
````
