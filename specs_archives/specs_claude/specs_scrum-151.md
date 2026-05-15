# SCRUM-151 — UI événements récurrents dans EventForm + EventCard + EventDetailPage

| Champ | Valeur |
|---|---|
| Ticket Jira | [SCRUM-151](https://pinfo-groupe6.atlassian.net/browse/SCRUM-151) (5 SP) |
| Sprint | S8 (calendrier produit, suite à la réorganisation S9 → S8 — cf. [`frontend/docs/backlog_s5_s10.md` lignes 1011-1015](frontend/docs/backlog_s5_s10.md#L1011-L1015)) |
| Épic | [SCRUM-14](https://pinfo-groupe6.atlassian.net/browse/SCRUM-14) — Édition d'événements |
| Story | [SCRUM-116](https://pinfo-groupe6.atlassian.net/browse/SCRUM-116) (US-27 — *« En tant qu'organisateur, je veux créer des événements récurrents, afin de ne pas dupliquer manuellement chaque occurrence. »*) |
| Story Points | 5 |
| Branche | `feature/scrum-169-profile-username-url` (cf. Décision A — **PAS de nouvelle branche**, SCRUM-151 est livré sur la branche existante de PR #172). |
| Base | `feature/scrum-169-profile-username-url` (tip au moment de la rédaction : `336ae24b Merge remote-tracking branch 'origin/main' into feature/scrum-169-profile-username-url`). Pas de nouvelle PR. |
| Auteur spec | Elie Bussod (rédaction assistée Claude Opus 4.7) |
| Date | 2026-05-15 |
| PR de référence | **PR #172 (existante)** — absorbe SCRUM-151 par-dessus SCRUM-137/146/169 (précédent : PR #170 absorbée par PR #172). Pas de `gh pr create` à exécuter. Le titre de PR #172 sera ajusté via `gh pr edit 172` pour refléter le scope élargi. |
| Mode de travail | **Une seule branche, une seule PR, livrée en pleine autonomie** : commits SCRUM-151 poussés directement sur `feature/scrum-169-profile-username-url`, PR #172 absorbe le tout, un seul surface de review pour le reviewer. Elie merge lui-même. |
| Dépendances amont | [SCRUM-147](https://pinfo-groupe6.atlassian.net/browse/SCRUM-147) (BACK récurrence) — **livré et mergé sur `main`**. Le contrat OpenAPI (`parentEventId`, `recurrenceRule`, `RecurrenceRequest`, `RecurrenceFrequency`, `GET /events/{id}/occurrences`) est figé dans [`openapi/openapi.yaml`](openapi/openapi.yaml). |
| Règle d'or `openapi-first` | **NON applicable** — SCRUM-151 est strictement frontend et consomme un contrat backend déjà figé par SCRUM-147 (`openapi/openapi.yaml` lignes 436-457, 532-540, 900-942, 2806-2866). **Aucune modification de `openapi/openapi.yaml`** dans cette PR. Voir [`frontend/AGENTS.md`](frontend/AGENTS.md). |

> **Pré-requis lecture.** Cette spec consomme intégralement le contrat figé par
> [`specs_archives/specs_claude/specs_scrum-147.md`](specs_archives/specs_claude/specs_scrum-147.md). Les **Décisions backend D9 (cap hard 52), D11 (statut hérité), D17 (PUT parent non propagé), D18 (cancel non cascadé), D24 (GET /events non filtré)** déterminent l'UX SCRUM-151 — toute incohérence apparente avec le frontend doit être résolue côté frontend, jamais côté backend.

---

## 1. Objectifs & non-objectifs

### Objectifs

- **Axe 1 — Types frontend.** Étendre [`frontend/src/types/event.ts`](frontend/src/types/event.ts) avec `Event.parentEventId?: number | null`, `Event.recurrenceRule?: string | null`, `CreateEventRequest.recurrence?: RecurrenceRequest | null`. Introduire l'interface `RecurrenceRequest`, l'union `RecurrenceFrequency`, et la const map typée `RECURRENCE_FREQUENCIES` (miroir de `EVENT_CATEGORIES` / `EVENT_STATUSES`).
- **Axe 2 — Service frontend.** Ajouter `getOccurrences(parentId: number, params?: { page?: number; size?: number }): Promise<Event[]>` dans [`frontend/src/services/eventApi.ts`](frontend/src/services/eventApi.ts), appelant `GET /events/{parentId}/occurrences`.
- **Axe 3 — Hook frontend.** Créer `frontend/src/hooks/useOccurrences.ts` — état `loading / error / data` standard, paramètre `{ enabled: boolean }` pour fetch paresseux (n'appelle pas l'API tant que l'utilisateur n'a pas cliqué « Voir toutes les occurrences »).
- **Axe 4 — `useEventForm.ts`.** Étendre `EventFormValues` avec un bloc `recurrence: { enabled: boolean; frequency: RecurrenceFrequency; endMode: 'date' | 'count'; endDate: string; maxOccurrences: string }`. Le bloc survit au refresh via le mécanisme `sessionStorage` existant. La fonction `validate()` reçoit les contrôles client miroirs (cf. Décision C). La sérialisation `payload.recurrence` se fait **uniquement** dans la branche `create` de `submitForm`.
- **Axe 5 — `EventForm.tsx`.** Remplacer le `ComingSoonBlock` existant lignes 464-472 par une section fonctionnelle (uniquement en `mode === 'create'`) : switch « Unique / Récurrent » en header, body conditionnel avec `<Select>` fréquence (3 options FR) + radio `endDate | maxOccurrences` + Input correspondant. Visuel calqué sur le pattern « Date & heure » (lignes 330-354).
- **Axe 6 — `EventCard.tsx`.** Badge `RefreshCw + "Récurrent"` (lucide-react) en bas-droite du banner, **conditionnel sur `event.parentEventId != null`** (donc sur les occurrences uniquement — cf. Décision F).
- **Axe 7 — `EventDetailPage.tsx`.** Section repliable **inline** sous la description, déclenchée par un bouton « Voir toutes les occurrences (N) ». Fetch paresseux au premier expand via `useOccurrences`. Visible si l'event courant est un parent récurrent (`recurrenceRule != null`) **ou** une occurrence (`parentEventId != null`) — dans ce dernier cas, on liste les occurrences du parent et on marque visuellement l'event courant (cf. Décision G).
- **Axe 8 — Validation client.** Miroir du backend (cf. Décision C) : `frequency` ∈ `WEEKLY|BIWEEKLY|MONTHLY`, exactement un de `endDate` / `maxOccurrences` (mutex côté form — Décision B), `endDate ≥ startDate.toLocalDate()`, `maxOccurrences ∈ [1, 52]`.
- **Axe 9 — Tests.** Couverture V8 ≥ 80 % sur le nouveau code : `useEventForm.test.ts`, `EventForm.test.tsx`, `EventCard.test.tsx`, `EventDetailPage.test.tsx`, `useOccurrences.test.ts`, `eventApi.test.ts`. Couvre la state machine du switch, le mutex radio, la persistance, la validation, le payload sérialisé, le badge conditionnel, le lazy fetch, l'affichage compact.
- **Axe 10 — Documentation frontend.** [`frontend/docs/types.md`](frontend/docs/types.md), [`frontend/docs/components.md`](frontend/docs/components.md), [`frontend/docs/sprint-context.md`](frontend/docs/sprint-context.md). Pas de modification de `openapi/openapi.yaml`. Pas de modification de la table « Skeletons existants » de [`frontend/AGENTS.md`](frontend/AGENTS.md) (cf. Décision I).

### Non-objectifs

- **Pas de modification de `openapi/openapi.yaml`.** Le contrat est figé par SCRUM-147 (mergé sur `main`). Toute incohérence détectée pendant l'implémentation se traite côté frontend, jamais en modifiant le YAML.
- **Pas de modification de l'`Event` côté backend** (entité, DTO, service, resource, migration). Pure consommation frontend.
- **Pas de modification de la section Récurrence en `mode === 'edit'`** (cf. Décision E). Une fois la récurrence créée, le frontend ne propose pas de la modifier — cohérent avec **D17 backend** (`PUT /events/{parentId}` ne propage rien).
- **Pas de nouvelle route `/events/:id/occurrences`.** L'inline collapsible suffit (cf. Décision G).
- **Pas de nouveau skeleton `.bones.json`.** Le skeleton existant `event-detail.bones.json` reste inchangé ; la section inline utilise un `Skeleton` boneyard simple pendant le fetch (cf. Décision I).
- **Pas de rendu humain de la `recurrenceRule` RFC 5545** (`FREQ=WEEKLY;UNTIL=20260601` → « Tous les lundis jusqu'au 1 juin 2026 »). KISS — la fréquence n'est pas affichée sur la page parent ; la liste des occurrences sous le bouton fait office de représentation visuelle de la cadence. À ajouter en S9+ si demande PO.
- **Pas de fonction « éditer toutes les occurrences futures »** (cohérent D17 backend, hors scope S8).
- **Pas de fonction « annuler toutes les occurrences en bloc »** (cohérent D18 backend, hors scope S8).
- **Pas de pagination exposée côté UI.** Le backend cappe à 52 occurrences ; un seul call avec `size=52` couvre toujours.
- **Pas de merge** de la PR. Elie merge lui-même.

---

## 2. Contexte

### 2.1 Le besoin produit (US-27)

> *« En tant qu'organisateur, je veux créer des événements récurrents, afin de ne pas dupliquer manuellement chaque occurrence. »* — US-27 (SCRUM-116, épic SCRUM-14).

Le backend SCRUM-147 a livré la brique de récurrence : `POST /events` accepte désormais un bloc `recurrence: { frequency, endDate?, maxOccurrences? }` qui matérialise 1 parent + jusqu'à 51 occurrences en une transaction atomique. `GET /events/{id}/occurrences` retourne la liste des enfants triée chronologiquement. Mais côté frontend, **aucune surface UI ne permet aujourd'hui à l'organisateur de cocher « récurrent », ni au lecteur de découvrir l'ensemble des sessions d'un cycle de cours**.

SCRUM-151 livre la couche UI manquante : un switch + select + radio dans `EventForm` (côté création), un badge `RefreshCw` sur `EventCard` (côté découverte d'une occurrence), un bouton repliable « Voir toutes les occurrences » sur `EventDetailPage` (côté navigation).

### 2.2 Ce qui manque aujourd'hui

| Pièce manquante | Conséquence |
|---|---|
| Aucun champ `parentEventId` / `recurrenceRule` sur le type [`Event`](frontend/src/types/event.ts:3-39) frontend | Le payload backend est ignoré ; `EventCard.parentEventId` est `undefined` partout |
| Aucun type `RecurrenceRequest` / `RecurrenceFrequency` dans `frontend/src/types/event.ts` | Impossible de typer la sérialisation `payload.recurrence` ni la const map UI |
| Aucun champ `recurrence` sur l'interface `CreateEventRequest` (lignes 67-83) | Le `payload` posté par `useEventForm.submitForm` ignore la récurrence — silently dropped par Axios JSON |
| `useEventForm.EventFormValues` (lignes 20-35) n'a pas de bloc `recurrence` | L'état du switch / select / radio n'a nulle part où vivre ; impossible de persister en sessionStorage |
| `EventForm.tsx:464-472` porte un `ComingSoonBlock` placeholder marqué `sprint="S8"` | UI bloquante, l'utilisateur voit un visuel inactif |
| Aucune fonction `getOccurrences` dans [`eventApi.ts`](frontend/src/services/eventApi.ts) | Le bouton « Voir toutes les occurrences » n'a aucune source de données |
| Aucun hook `useOccurrences` dans `frontend/src/hooks/` | Pas de state machine `loading / error / data` réutilisable pour la section inline |
| `EventCard.tsx` n'a pas de zone badge sous le `FavoriteButton` | Aucune surface pour distinguer une occurrence d'un standalone côté listing |
| `EventDetailPage.tsx` n'évoque la récurrence nulle part (724 lignes, 0 occurrence du mot — vérifié par grep) | Le lecteur d'un parent récurrent n'a aucun moyen de découvrir les sessions suivantes |
| Validation client absente | Tout payload `recurrence` incohérent (sans `endDate` ni `maxOccurrences`, ou avec les deux) part au backend et revient en `400 recurrence_unbounded` — UX dégradée |

### 2.3 Ce qui existe déjà à RÉUTILISER tel quel (ne pas recréer)

| Élément | Fichier / ligne | Rôle dans SCRUM-151 |
|---|---|---|
| Pattern const map typée (`EVENT_CATEGORIES` + `EventCategory = keyof typeof`) | [`frontend/src/types/event.ts:46-55`](frontend/src/types/event.ts#L46-L55) | **Modèle direct** pour `RECURRENCE_FREQUENCIES` + `RecurrenceFrequency = keyof typeof RECURRENCE_FREQUENCIES`. Pattern documenté `frontend/AGENTS.md` lignes 157-195. |
| `EventFormValues` + state `useState<EventFormValues>` + persistance sessionStorage debouncée 300 ms | [`frontend/src/hooks/useEventForm.ts:20-35, 88-92, 245-263, 419-429`](frontend/src/hooks/useEventForm.ts#L20-L35) | **Étendu** d'un champ `recurrence` ; le mécanisme `schedulePersist` couvre automatiquement le nouveau bloc (lit/écrit `EventFormValues` complet) |
| `validate()` (Bean Validation-style côté form, retourne `boolean` + écrit dans `errors`) | [`frontend/src/hooks/useEventForm.ts:518-613`](frontend/src/hooks/useEventForm.ts#L518-L613) | **Étendu** d'un bloc de règles `recurrence` (Décision C) — pas de nouvelle fonction |
| `EventFormErrors` (record `partial<Record<keyof EventFormValues, string>>`) | [`frontend/src/hooks/useEventForm.ts:37-50`](frontend/src/hooks/useEventForm.ts#L37-L50) | **Étendu** d'une clé `recurrence?: string` (un seul message global pour la section, pas un par sous-champ — KISS) |
| `submitForm(kind)` qui sérialise `payload: CreateEventRequest` | [`frontend/src/hooks/useEventForm.ts:615-696`](frontend/src/hooks/useEventForm.ts#L615-L696) | **Étendu** d'une branche conditionnelle `mode === 'create' && values.recurrence.enabled` qui ajoute `payload.recurrence: RecurrenceRequest` |
| Section « Date & heure » `rounded-2xl border border-border/50 bg-foreground/[0.015] px-4 py-4` + switch en header | [`frontend/src/components/event/EventForm.tsx:330-354`](frontend/src/components/event/EventForm.tsx#L330-L354) | **Modèle visuel direct** pour la section « Récurrence ». Switch toggle (input checkbox role=switch + `peer-checked:bg-accent`) déjà stylé — pattern réutilisable |
| `ComingSoonBlock` placeholder + icône `Repeat` lucide importée | [`frontend/src/components/event/EventForm.tsx:8, 69-96, 464-472`](frontend/src/components/event/EventForm.tsx#L464-L472) | Le placeholder lignes 464-472 est **remplacé** par la section fonctionnelle. Le composant `ComingSoonBlock` reste en place pour le slot « Pièces jointes » (S9). L'icône `Repeat` reste importée — cohérent avec l'icône du switch. |
| Pattern `FormField` + `Select` + helper text à droite | [`frontend/src/components/utils/FormField.tsx`](frontend/src/components/utils/FormField.tsx) | Cible — `<Select>` fréquence (3 options) et `<Input type="date" / type="number">` pour endDate et maxOccurrences |
| Pattern radio inline (pas trouvé en l'état — à créer en local dans `EventForm.tsx`, KISS) | — | 2 `<input type="radio" name="recurrence-endMode">` exposés comme labels stylés, miroir du switch allDay |
| Type `Event` + import dans `EventCard.tsx` ; `FavoriteButton` positionné `absolute top-4 right-4 z-10` | [`frontend/src/components/event/EventCard.tsx:41-43`](frontend/src/components/event/EventCard.tsx#L41-L43) | **Zone disponible** pour placer le badge `Récurrent` `absolute bottom-4 right-4 z-10` (sous le FavoriteButton, au-dessus de la zone titre+faculté) |
| Icônes lucide importées globalement | `lucide-react` (déjà dans `package.json`) | Importer `RefreshCw` (EventCard, EventDetailPage), `ChevronDown` / `ChevronUp` (EventDetailPage section repliable). `Repeat` déjà importé dans EventForm. |
| `useEvent` (hook `loading / error / data` pour `getById`) | [`frontend/src/hooks/useEvent.ts`](frontend/src/hooks/useEvent.ts) | **Modèle direct** pour `useOccurrences` (même signature `{ loading, error, data }`, même pattern `useEffect + abort controller` si utilisé) |
| `useAttendees` (autre exemple de fetch list) | [`frontend/src/hooks/useAttendees.ts`](frontend/src/hooks/useAttendees.ts) | **Modèle direct** alternatif — `useOccurrences` peut hériter du même pattern lazy si `enabled === false` court-circuite le `useEffect` |
| Pattern `Skeleton` boneyard pour loading states | `boneyard-js/react` (import existant dans `EventDetailPage.tsx:16`) | **Utilisé** pour le loading de la section inline « occurrences » — pas de `.bones.json` dédié (Décision I) |
| Pattern badge pill (catégorie sur banner) | [`frontend/src/components/event/EventCard.tsx:32-37`](frontend/src/components/event/EventCard.tsx#L32-L37) | **Modèle visuel** pour le badge `Récurrent` — mêmes radius/padding, couleur neutre (pas de category.color, on prend `bg-foreground/10` ou `bg-background/80 backdrop-blur-sm`) |
| `formatEventDateTime` + `formatEventDateTimeCompact` | [`frontend/src/utils/dateTime.ts`](frontend/src/utils/dateTime.ts) | **Réutilisé** pour la liste compacte d'occurrences inline |
| `EVENT_STATUSES` const map | [`frontend/src/types/event.ts:57-65`](frontend/src/types/event.ts#L57-L65) | **Réutilisé** pour afficher le statut FR de chaque occurrence dans la liste (Décision H — un parent CANCELLED peut avoir des occurrences PUBLISHED, statut visible) |
| Pattern `useAuth()` (admin / authenticated) | [`frontend/src/contexts/AuthContext.tsx`](frontend/src/contexts/AuthContext.tsx) (consommé via `useAuth` dans EventDetailPage) | Non requis pour SCRUM-151 — `GET /events/{id}/occurrences` est `@PermitAll` (cf. `openapi.yaml:2821-2823`) |
| `frontend/src/hooks/index.ts` (barrel exports) | [`frontend/src/hooks/index.ts`](frontend/src/hooks/index.ts) | **Étendu** d'une ligne `export { useOccurrences } from './useOccurrences'` |

### 2.4 Pourquoi maintenant

- **Backend SCRUM-147 mergé sur `main`** (preview deployable). Le contrat `RecurrenceRequest` / `RecurrenceFrequency` / `parentEventId` / `recurrenceRule` / `GET /events/{id}/occurrences` est figé et testable end-to-end dès maintenant. Aucune dépendance amont restante.
- **Sprint S8 — sprint courant**, ticket affecté à Daniel sur le board Jira ([`frontend/docs/backlog_s5_s10.md:1322`](frontend/docs/backlog_s5_s10.md#L1322)). Réorganisation S9 → S8 (lignes 1011-1015) place SCRUM-151 dans la fenêtre de livraison alignée sur le backend.
- **Suppression d'un placeholder visible.** `EventForm.tsx:464-472` affiche aujourd'hui un `ComingSoonBlock` marqué `sprint="S8"` que tout utilisateur naviguant sur `/events/new` voit (opacity-30, non-interactif). C'est une dette UX immédiatement réparable.
- **Débloque l'épic SCRUM-14** (Édition d'événements) — la récurrence est la dernière brique majeure de l'épic ; seules les exceptions/skip individuels (RFC 5545 EXDATE) restent à traiter en S9+ (hors scope D7 backend).
- **Pas de réécriture transversale.** Aucun composant existant n'est restructuré ; SCRUM-151 est purement additif sur les 4 fichiers cibles + 2 nouveaux fichiers (`useOccurrences.ts` + tests associés). Empreinte minimale, review rapide.
- **Stackable sur PR #172 sans collision logique.** PR #172 (SCRUM-137/146/169, en review) touche `EventForm.tsx`, `EventDetailPage.tsx`, `eventApi.ts` mais **n'introduit aucune logique de récurrence**. Les diffs SCRUM-151 se posent proprement par-dessus (Décision A).

---

## 3. Décisions techniques tranchées (NE PAS REVISITER pendant l'implémentation)

> **Règle.** Une fois la spec validée par Elie, ces décisions ne se rediscutent pas pendant l'implémentation. Toute déviation doit être documentée dans `frontend/docs/sprint-context.md` à la livraison.

### Décision A — Pas de nouvelle branche ni de nouvelle PR : commits SCRUM-151 sur la branche existante de PR #172

**Décision.** SCRUM-151 est livré **directement sur la branche `feature/scrum-169-profile-username-url`** (la branche de PR #172, ouverte en review). **Pas de nouvelle branche `feature/scrum-151-*`. Pas de `gh pr create`.** PR #172 absorbe le scope SCRUM-151 par-dessus le scope SCRUM-137/146/169 déjà présent. Le titre et la description de PR #172 sont mis à jour via `gh pr edit 172` pour refléter l'ajout (cf. § 5 étape 11).

**Justification.** Choix d'Elie après réflexion : optimiser le coût de review (une PR au lieu de deux stackées). Cinq raisons concrètes :

- **Une seule surface de review.** Le reviewer (Antoine, Viona, Daniel, ou Copilot bot) n'a qu'une PR à charger, comparer à `main`, et approuver. Stacker créerait deux PRs imbriquées dont la 2e dépend visuellement de la 1re — friction inutile.
- **Précédent direct projet.** PR #170 (livraison initiale SCRUM-137 + SCRUM-146) a été **fermée et absorbée par PR #172** quand SCRUM-169 a été décidé sur la même branche. SCRUM-151 reproduit exactement ce pattern : un seul ticket frontend de plus, sur la même branche.
- **Zones touchées disjointes.** SCRUM-151 modifie `ComingSoonBlock` lignes 464-472 d'`EventForm.tsx`, ajoute une section sous la description d'`EventDetailPage.tsx`, ajoute `getOccurrences` à la fin d'`eventApi.ts`. Aucune de ces zones n'est touchée par les commits SCRUM-137/146/169 — pas de conflit textuel, pas de risque de cascade lors d'une éventuelle correction Copilot sur les commits existants.
- **Cohérence des commits.** Chaque commit reste scopé `feat(scrum-151): ...` ou `test(scrum-151): ...` (cf. `AGENTS.md` racine ligne 45). Les commits SCRUM-137/146/169 existants restent intacts. Le scope du commit reste l'identifiant Jira, indépendamment du titre de PR.
- **Pas de coût de stacking.** En stack, il faudrait gérer le rebase quand #172 reçoit de nouveaux commits review (Copilot, retours Elie). En linéaire, chaque push sur `feature/scrum-169-...` est trivialement intégré.

**Coût et mitigations.**

| Inconvénient | Mitigation |
|---|---|
| Le titre actuel de PR #172 (`feat(scrum-169): replace UUID-based profile URLs with usernames`) ne reflète plus le scope élargi | `gh pr edit 172 --title "feat: SCRUM-137/146/169/151 — co-organizers, comments, usernames, recurrence UI"` (cf. § 5 étape 11). Description également mise à jour. |
| CI re-run intégrale à chaque push SCRUM-151 (alors qu'on aurait pu garder une PR stack avec CI séparée par PR) | Acceptable — la matrix CI passe en ~5-7 min sur cette branche, et la review humaine + Copilot reste le bottleneck, pas le CI. |
| Si PR #172 doit être amendée sur SCRUM-137/146/169 pendant SCRUM-151 → commits entrelacés | Acceptable — `git log` reste lisible (chaque commit scope son ticket), et `git diff origin/main..HEAD -- <fichier>` reste révélateur. |
| Le pattern « 1 PR = 1 ticket » de `AGENTS.md` racine ligne 43 n'est plus strictement respecté | Documenté dans la description de PR #172 : *« PR multi-ticket exceptionnelle, absorbe la livraison frontend S7-S8 d'un même groupe »*. Précédent projet (PR #172 a déjà absorbé SCRUM-137/146 + SCRUM-169) — non-régression. |

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) Commits sur `feature/scrum-169-profile-username-url`, PR #172 absorbe SCRUM-151 | Une seule review ; précédent direct projet (PR #170 → #172) ; zones disjointes ; pas de stack à maintenir | Titre PR #172 à ajuster ; pattern « 1 PR / 1 ticket » légèrement étiré | ✅ retenu (décision Elie) |
| (b) Nouvelle branche `feature/scrum-151-recurrence-ui` stackée sur tip #172, nouvelle PR ciblant `main` | Une PR par ticket (strict respect AGENTS.md) ; CI isolée | Deux PRs imbriquées à reviewer ; rebase à maintenir si #172 reçoit des commits review ; UI GitHub stack confuse | ❌ |
| (c) Attendre que #172 merge puis brancher sur `main` | Diff propre dès la PR | Bloque SCRUM-151 sur le calendrier review de #172 (jours / semaine) — sprint S8 file | ❌ |
| (d) Brancher sur `main` directement maintenant (sans stack) | Diff propre immédiat | Conflits textuels triviaux mais bruyants sur 3 fichiers communs avec #172 ; double review pour le reviewer | ❌ |

**Conséquence pratique pour l'agent.** Aucun `git checkout -b ...` à exécuter au démarrage. Aucun `gh pr create` à exécuter à la fin. Le workflow se limite à `git checkout feature/scrum-169-profile-username-url && git pull && <commits SCRUM-151> && git push`.

### Décision B — Mutuelle exclusion radio `endDate` ↔ `maxOccurrences` côté form

**Décision.** Dans la section « Récurrence » de `EventForm`, un radio à 2 options (`endDate` / `maxOccurrences`) contrôle quel champ Input est rendu et soumis. L'état local `values.recurrence.endMode: 'date' | 'count'` porte le choix. Le payload `payload.recurrence` envoyé au backend **ne contient toujours qu'un des deux** (`endDate` ou `maxOccurrences`, jamais les deux).

**Justification.** Le backlog ([`frontend/docs/backlog_s5_s10.md:1330`](frontend/docs/backlog_s5_s10.md#L1330)) impose explicitement : *« Date de fin de récurrence **OU** nombre d'occurrences (radio) »*. Le backend SCRUM-147 (Décision D9) accepte les deux simultanément et s'arrête au plus restrictif, mais la team a choisi la mutex côté form pour deux raisons :

| Aspect | Mutex côté form (Décision retenue) | Les deux acceptés côté form |
|---|---|---|
| Lisibilité UX | Une seule règle de fin visible à la fois — clair pour l'organisateur | Deux contraintes à arbitrer mentalement (« qu'est-ce qui arrive en premier ? ») |
| Cohérence backlog | ✅ Exact wording « radio » | ❌ Devrait dire « OU et/ou ET » |
| Validation client | Une seule règle à vérifier par mode | Combinaison à vérifier (cohérence date/count) |
| Surface de bug | Plus petite | Plus grande |

Le backend reste robuste : si l'organisateur change d'avis, il bascule le radio et l'autre champ est réinitialisé client-side avant le POST — le backend ne reçoit jamais d'état incohérent.

| Option | Verdict |
|---|---|
| (a) Radio mutex côté form | ✅ retenu — backlog explicite |
| (b) Checkbox + 2 champs simultanés | ❌ — incohérent avec le backlog ; UX flottante |
| (c) Tabs « Jusqu'à une date » / « N occurrences » | ❌ — pattern lourd pour 2 options ; le radio est suffisant |

### Décision C — Validation client miroir backend, avant POST

**Décision.** La fonction `validate()` de `useEventForm` reçoit un bloc de règles `recurrence` exécutées **uniquement si `values.recurrence.enabled === true` et `mode === 'create'`**. Les erreurs détectées sont reportées dans `errors.recurrence: string` (clé unique — KISS, un message global pour la section). Règles miroir backend :

1. `values.recurrence.frequency` doit être ∈ `WEEKLY|BIWEEKLY|MONTHLY` — garanti par le typage `RecurrenceFrequency` du Select (non-runtime check, mais le check final s'assure que la valeur n'est pas la string vide d'un éventuel placeholder).
2. Selon `endMode` :
   - `'date'` → `values.recurrence.endDate` non vide ; parseable en `Date` valide ; **≥ à `startDate.toLocalDate()`** (comparaison sur la date locale, pas sur minuit UTC — cf. cas-limite § 5).
   - `'count'` → `values.recurrence.maxOccurrences` non vide ; entier ∈ `[1, 52]` (`Number.isInteger` + bornes).
3. Si `enabled === true` mais aucun des deux n'est rempli → erreur `Définissez une date de fin OU un nombre d'occurrences.` (miroir `400 recurrence_unbounded` côté backend).
4. Si `enabled === true` mais `startDate` invalide ou vide → erreur précédente sur `startDate` suffit ; **on ne re-vérifie pas la cohérence date dans la branche recurrence** si la validation amont a déjà signalé un problème (KISS, single source of truth).

**Justification.** Trois bénéfices :

- **UX** : feedback inline sans attendre l'aller-retour 400 backend.
- **Cohérence** : message FR localisé (l'enveloppe backend `error: recurrence_unbounded` reste anglophone — la couche localisation n'est pas un objectif S8).
- **Surface réduite** : un seul `errors.recurrence: string` au lieu de 3 (`frequency`, `endDate`, `maxOccurrences` séparés). Si l'organisateur fait plusieurs erreurs, il les corrige en cascade.

**Backend reste source de vérité.** Le frontend ne ré-implémente pas la limite hard de 52 — `@Max(52)` côté DTO Bean Validation est l'oracle, et le frontend la **miroir** en bloc soft (Input HTML `max="52"`) + check JS (`maxOccurrences <= 52`). Si le backend décide demain de passer à 104, le frontend devra suivre.

### Décision D — Const map typée `RECURRENCE_FREQUENCIES` + labels FR localisés

**Décision.** Dans `frontend/src/types/event.ts`, ajout :

```ts
export const RECURRENCE_FREQUENCIES = {
  WEEKLY:   { name: 'Chaque semaine' },
  BIWEEKLY: { name: 'Toutes les 2 semaines' },
  MONTHLY:  { name: 'Chaque mois' },
} as const

export type RecurrenceFrequency = keyof typeof RECURRENCE_FREQUENCIES

export interface RecurrenceRequest {
  frequency: RecurrenceFrequency
  endDate?: string | null
  maxOccurrences?: number | null
}
```

Pattern strictement aligné sur `EVENT_CATEGORIES` (`frontend/src/types/event.ts:46-55`) et `EVENT_STATUSES` (lignes 57-65). `frontend/AGENTS.md` lignes 157-195 documente ce pattern.

**Justification.** Single source of truth : ajouter / supprimer une fréquence met à jour le type union automatiquement ; les labels FR sont colocalisés avec les clés ; l'autocomplétion sur `values.recurrence.frequency` est exhaustive. Pas de `Record<string, ...>` (trop large), pas d'enum TS séparé (incohérent avec le pattern projet).

| Option | Verdict |
|---|---|
| (a) `RECURRENCE_FREQUENCIES as const` + `RecurrenceFrequency = keyof typeof` | ✅ retenu — pattern projet |
| (b) `enum RecurrenceFrequency { WEEKLY = 'WEEKLY', ... }` + dict labels séparé | ❌ — incohérent avec EVENT_CATEGORIES |
| (c) Hardcoder les labels FR dans le JSX du Select | ❌ — viole le DRY de `frontend/AGENTS.md` lignes 44-47 |

**Labels canonique FR.** `WEEKLY = "Chaque semaine"`, `BIWEEKLY = "Toutes les 2 semaines"`, `MONTHLY = "Chaque mois"`. Pas de variantes (« Hebdomadaire » / « Bimensuel ») pour rester compréhensible aux non-spécialistes.

### Décision E — Section Récurrence en `mode === 'create'` UNIQUEMENT

**Décision.** Dans `EventForm.tsx`, la section « Récurrence » est entièrement **conditionnée sur `mode === 'create'`**. En `mode === 'edit'`, la section n'apparaît pas du tout (pas de read-only, pas de placeholder « créé en récurrence le X »).

**Justification.** Cohérent avec **D17 backend SCRUM-147** : `PUT /events/{parentId}` ne propage AUCUNE modification aux occurrences. Si on exposait la section Récurrence en edit, on tromperait l'organisateur : il modifierait la fréquence du parent, mais les 51 occurrences existantes resteraient figées.

Variantes considérées :

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) Section masquée totalement en edit | KISS, aucune fausse promesse UX | Pas de visibilité sur le fait que l'event courant fait partie d'une récurrence | ✅ retenu |
| (b) Section read-only en edit (display only) | Visibilité préservée | Coût de design supplémentaire pour 0 valeur d'édition ; banner `parentEventId != null` côté EventDetailPage (Décision G) couvre déjà ce besoin de visibilité | ❌ |
| (c) Section interactive en edit + appel à un endpoint cascade (à créer) | Permet de modifier toute la récurrence | **Hors scope SCRUM-151 + cohérence D17 backend ; nécessite un nouveau ticket** | ❌ |

**Conséquence.** Pas de re-design de `EventEditPage.tsx`. La page `EventCreatePage.tsx` est le seul consommateur de la section interactive — passant `mode="create"` au composant `EventForm`. Tout le reste de l'UX récurrence (badge, occurrences list) reste accessible depuis `EventCard` et `EventDetailPage`, indépendamment du mode du form.

### Décision F — Badge `Récurrent` sur EventCard, bas-droite du banner, conditionnel sur `parentEventId != null`

**Décision.** Sur [`EventCard.tsx`](frontend/src/components/event/EventCard.tsx), ajout d'un pill compact `RefreshCw + "Récurrent"` positionné `absolute bottom-4 right-4 z-10` (sous le `FavoriteButton` qui est en `top-4 right-4`), visible **uniquement** si `event.parentEventId != null`.

**Pattern visuel** : pill background semi-transparent (`bg-background/80 backdrop-blur-sm`) + bordure (`border border-border/40`) + texte foreground (`text-foreground/80`), padding `px-2.5 py-1`, rounded-full, icône `RefreshCw className="w-3.5 h-3.5"`. Pas de couleur catégorie — le badge doit rester neutre pour ne pas concurrencer la pill catégorie en top-left.

**Justification.** Trois choix entrelacés :

| Aspect | Décision | Raison |
|---|---|---|
| Conditionnel sur `parentEventId != null` (occurrences uniquement) | ✅ | Le parent récurrent est l'event « source » (la première session d'un cycle). Le badge marque l'event comme « partie d'un cycle » — c'est-à-dire les enfants. Le parent reste visible comme un event normal (avec sa propre `recurrenceRule` consultable sur la page détail). Cohérent avec l'interprétation produit US-27 : un cycle de 52 cours hebdomadaires = 52 cards marquées « Récurrent », pas 51. |
| Position bas-droite du banner | ✅ | top-left = pill catégorie (toujours présente). top-right = FavoriteButton (toujours présent). bas-droite = libre, et la zone bottom-left est occupée par le titre+faculté overlay. Bas-droite reste visuellement équilibré (un pill discret sous le FavoriteButton). |
| Icône `RefreshCw` lucide | ✅ | Backlog explicite (`frontend/docs/backlog_s5_s10.md:1333`). Cohérent sémantiquement (cycle/réplique). |

| Option position | Verdict |
|---|---|
| (a) Bas-droite du banner (sous FavoriteButton) | ✅ retenu |
| (b) À côté de la pill catégorie (top-left du banner) | ❌ — concurrence visuelle avec la catégorie, surcharge le coin gauche |
| (c) Au-dessus de la zone meta (avec Calendar/MapPin) | ❌ — peu visible, manque de hiérarchie ; le badge devrait être un signal de banner |
| (d) Coin top-right du card (hors banner, sur la card body) | ❌ — pattern non documenté projet ; n'apparaît sur aucune card existante |

| Option condition d'affichage | Verdict |
|---|---|
| (a) `parentEventId != null` (sur occurrences uniquement) | ✅ retenu |
| (b) `parentEventId != null \|\| recurrenceRule != null` (sur parent + occurrences) | ❌ — le parent est noyé dans 52 cards identiques ; ne distingue plus ; viole l'intuition « événement unique » du parent |
| (c) `recurrenceRule != null` (sur parent uniquement) | ❌ — un parent récurrent est un event « source », pas une occurrence ; l'utilisateur recherche le badge sur les sessions à venir |

### Décision G — Section « Voir toutes les occurrences » repliable INLINE sur EventDetailPage

**Décision.** Sur [`EventDetailPage.tsx`](frontend/src/pages/event/EventDetailPage.tsx), ajout d'une section repliable **inline** sous la description (ou sous la card « Description » dans la colonne principale, selon le layout final post-#172). Pattern : un bouton plein-largeur `« Voir toutes les occurrences (N) »` + chevron, ouvert/fermé par un état local `useState(false)`. Au premier expand, déclenche `useOccurrences(parentId, { enabled: true })` — fetch paresseux, single-shot.

**Visibilité.** La section apparaît si :

- `event.recurrenceRule != null` (event courant = parent) → `parentId = event.id` → liste les enfants directement.
- `event.parentEventId != null` (event courant = occurrence) → `parentId = event.parentEventId` → liste les occurrences (incluant l'event courant, marqué visuellement « (vous êtes ici) »).
- Sinon (standalone non-récurrent) → la section n'apparaît pas du tout.

Le compteur `(N)` est connu **après** le premier fetch — affiché en gris (skeleton number) ou `(?)` avant le clic. Au clic, la section se déplie : skeleton `Skeleton` boneyard pendant le `loading`, puis la liste compacte (Décision H).

**Justification.** Trois alternatives évaluées :

| Option | Avantages | Inconvénients | Verdict |
|---|---|---|---|
| (a) Section repliable inline avec fetch paresseux | Pas de nouvelle route ; pas de skeleton bones.json dédié ; UX progressive (l'utilisateur paie le coût réseau quand il en a besoin) ; cohérent avec le pattern `IcsExportButton` lignes 51-52 (similar inline section sidebar) | Si l'utilisateur ouvre/ferme/ouvre, on évite le re-fetch (cache local au composant) — KISS | ✅ retenu |
| (b) Nouvelle route `/events/:id/occurrences` avec page dédiée | Permalink, partageable | Coût : nouvelle route + page + skeleton bones.json + tests routing — pour un cas secondaire d'usage | ❌ |
| (c) Drawer / Modal | Visibilité maximale | Pattern lourd, casse le scroll de la page, pas cohérent avec le reste de EventDetailPage | ❌ |
| (d) Fetch eager (au mount de la page) + section toujours ouverte | Aucun clic supplémentaire | Coût réseau pour 100 % des visites de page détail récurrente, même si 80 % ne déroulent pas la liste | ❌ |

**Position.** Sous la card « Description » dans la colonne principale (ou immédiatement sous le banner si la description est absente). Le `EventDetailFixture` ligne 31-58 documente la structure de la page — la nouvelle section s'insère après la description (h-40) et avant la card AttendeesList compact (h-[90px]). Si #172 ré-arrange le layout, suivre la nouvelle structure de la branche stacked et placer la section juste après la description.

**Cas-limite parent CANCELLED.** Un parent CANCELLED peut avoir des occurrences PUBLISHED (cohérent D18 backend — cancel non cascadé). Dans ce cas, le bouton « Voir toutes les occurrences » reste affiché sur la page détail du parent CANCELLED ; la liste expose les occurrences avec leur status réel via badge (Décision H). Pas de filtre côté frontend.

### Décision H — Affichage compact dans la liste : `{date+heure} • {status badge} • lien /events/{id}`

**Décision.** Chaque ligne de la liste est une ligne compacte (pas une `EventCard` complète). Structure :

```text
[icône RefreshCw petite][formatEventDateTime startDate] · [Status badge][titre cliquable → /events/{id}][indicator "(vous êtes ici)" si event courant]
```

Pas de banner image. Pas de meta location/capacity/attendees. La page détail de chaque occurrence porte ces infos — la liste compacte est un index navigable.

**Justification.**

- 52 EventCards stack verticales = 52 × ~280 px = 14 560 px de scroll. Inutile et coûteux en bande passante (52 images bannière).
- Une ligne compacte tient en ~48 px, total ~2 500 px pour 52 occurrences = scrollable raisonnablement à l'intérieur de la section repliable (max-height: 60vh + overflow-y: auto).
- Le statut visible est **nécessaire** car un parent CANCELLED peut avoir des occurrences PUBLISHED ; sans badge status, l'utilisateur ne distingue plus visuellement.
- Le marqueur « (vous êtes ici) » sur la ligne correspondant à l'event courant est UX-friendly quand on est sur la page d'une occurrence et qu'on déplie la liste : on retrouve immédiatement où on se situe dans le cycle.

| Option | Verdict |
|---|---|
| (a) Ligne compacte (date + status + lien) | ✅ retenu |
| (b) EventCard pleine (avec banner) | ❌ — coût visuel et réseau |
| (c) Liste à puces minimaliste (juste les dates, sans status) | ❌ — perd l'info status critique en cas de parent CANCELLED + occurrences PUBLISHED |
| (d) Tableau (table) | ❌ — pattern projet privilégie les cards et les listes flex, pas les tables HTML |

**Tri.** Le backend retourne déjà la liste triée `startDate ASC, id ASC` ([`openapi.yaml:2810-2811`](openapi/openapi.yaml#L2810-L2811)). Le frontend n'a pas à re-trier. Une option future « masquer les occurrences passées » peut être ajoutée si demande PO (hors scope S8).

### Décision I — Pas de nouveau `.bones.json` ; `Skeleton` boneyard pour le loading inline

**Décision.** La section repliable inline « Voir toutes les occurrences » utilise un `Skeleton` boneyard générique pendant le `loading` du fetch (~200-400 ms typique). **Aucun nouveau fichier `.bones.json`** n'est créé ; la table « Skeletons existants » de [`frontend/AGENTS.md`](frontend/AGENTS.md) lignes 346-361 reste inchangée. Le skeleton existant `event-detail.bones.json` n'est pas modifié non plus (la section inline est sous le fold initial — le skeleton page détail couvre déjà le layout sans elle).

**Justification.** [`frontend/AGENTS.md`](frontend/AGENTS.md) lignes 326-343 fixe la règle : *« Toute page ou composant qui effectue un appel API et affiche un état `loading` doit avoir un skeleton `.bones.json` correspondant. »* La règle s'applique strictement aux **pages** (au mount) et aux **composants visibles immédiatement** (au mount). Notre cas :

- La section est **invisible au mount** (collapsible fermé par défaut).
- L'appel API n'est déclenché **qu'après une interaction utilisateur explicite** (clic « Voir toutes les occurrences »).
- La durée du loading est typiquement < 400 ms (`GET /events/{id}/occurrences` est un SELECT indexé sur `parent_event_id`, max 52 rows, payload < 30 Ko).

Dans ce cas, un `.bones.json` dédié serait du sur-engineering : pas de FOUC visible (la zone collapsible n'existe pas au render initial), pas de delay perçu (l'utilisateur vient de cliquer, attend logiquement < 1 s). Un `Skeleton` boneyard generic suffit (3-5 lignes squelettes, fading) — pattern aligné avec la section commentaires (`CommentSection`) qui n'a pas non plus de `.bones.json` dédié pour le sous-état « loading des commentaires », et utilise un loader inline.

| Option | Verdict |
|---|---|
| (a) Skeleton boneyard inline générique, pas de .bones.json | ✅ retenu |
| (b) Nouveau `occurrences-list.bones.json` + entrée AGENTS.md | ❌ — sur-engineering pour 200 ms de loading post-clic |
| (c) Pas de skeleton du tout, juste un spinner | ❌ — pattern projet privilégie skeleton sur spinner |

**Justification écrite à reporter dans `frontend/docs/sprint-context.md`** pour la traçabilité de la décision (cf. Étape 11 § 5).

### Décision J — Hook dédié `useOccurrences(parentId, { enabled })` avec fetch paresseux

**Décision.** Nouveau fichier `frontend/src/hooks/useOccurrences.ts` exposant :

```ts
interface UseOccurrencesOptions {
  enabled: boolean
}

interface UseOccurrencesResult {
  loading: boolean
  error: string | null
  data: Event[] | null
}

export function useOccurrences(parentId: number | null, options: UseOccurrencesOptions): UseOccurrencesResult
```

Comportement :

- `enabled === false` → `loading: false`, `error: null`, `data: null`. Aucun appel réseau.
- `enabled === true` + `parentId != null` → fetch via `getOccurrences(parentId)`, state machine standard `loading → (data | error)`.
- Si `parentId` change pendant `enabled === true` → re-fetch sur le nouveau parentId.
- Si `enabled` passe de `true` → `false` → cleanup (abort si en cours, conserver `data` pour ré-affichage instantané si l'utilisateur ré-ouvre).
- Cleanup `useEffect` sur unmount (abort controller).

**Justification.** Pattern strictement aligné sur `useEvent` (single fetch + state machine) et `useAttendees` (list fetch). Le paramètre `enabled` est l'innovation : sans lui, le hook fetcherait au mount de `EventDetailPage` même si la section reste fermée. Avec lui, on respecte la promesse « fetch paresseux » de la Décision G.

Pas de TanStack Query / SWR introduit pour ce ticket (pattern projet : les hooks API sont écrits à la main, état local + `useEffect`). Cohérent avec `useEvent`, `useAttendees`, `useFavorite`, `useComments`, etc. (~10 hooks équivalents dans `frontend/src/hooks/`).

| Option | Verdict |
|---|---|
| (a) Hook dédié avec `enabled` | ✅ retenu — paresseux contrôlé |
| (b) Hook qui fetche au mount + le composant ignore tant que la section est fermée | ❌ — fetch coûteux inutile pour 80 % des visites |
| (c) Pas de hook, fetch direct dans `EventDetailPage` avec `useState + useEffect` inline | ❌ — duplique le pattern, casse la convention `frontend/src/hooks/<...>.ts` (cf. `frontend/AGENTS.md` lignes 41-43) |

### Décision K — Service `getOccurrences` dans `eventApi.ts`, sans pagination exposée en S8

**Décision.** Dans [`frontend/src/services/eventApi.ts`](frontend/src/services/eventApi.ts), ajout :

```ts
export interface GetOccurrencesParams {
  page?: number
  size?: number
}

export async function getOccurrences(
  parentId: number,
  params: GetOccurrencesParams = {},
): Promise<Event[]> {
  const response = await api.get<Event[]>('/events/' + parentId + '/occurrences', { params })
  return response.data
}
```

Signature alignée sur `getAll(params)` ligne 16-19. **Pas de pagination exposée côté UI** — la limite backend hard de 52 ([`openapi.yaml:2843-2845`](openapi/openapi.yaml#L2843-L2845)) couvre 100 % des cas. Le hook `useOccurrences` appelle `getOccurrences(parentId)` sans params, le backend default `size=52` est suffisant.

**Justification.** KISS. Si un cas d'usage futur dépasse les 52 occurrences (ex. cycle de cours sur plusieurs années), le backend SCRUM-147 (Décision D9) impose déjà la limite — l'UI ne peut pas la dépasser sans modification backend. Le params optionnel reste pour ne pas casser une éventuelle évolution.

| Option | Verdict |
|---|---|
| (a) `getOccurrences(parentId, params?)` sans pagination UI | ✅ retenu — backend cap = UI cap |
| (b) `getOccurrences(parentId)` strict, pas de params du tout | ❌ — sacrifie l'évolutivité sans bénéfice |
| (c) Pagination exposée dans `useOccurrences` (page state + bouton « page suivante ») | ❌ — sur-engineering pour 52 max rows |

---

## 4. Inventaire des changements

### 4.1 OpenAPI (aucune modification)

**Pas de modification de [`openapi/openapi.yaml`](openapi/openapi.yaml)** — le contrat backend SCRUM-147 est déjà mergé sur `main` :

| Référence existante | Lignes openapi.yaml | Statut |
|---|---|---|
| `Event.parentEventId` | 436-446 | ✅ Présent |
| `Event.recurrenceRule` | 447-457 | ✅ Présent |
| `CreateEventRequest.recurrence` | 532-540 | ✅ Présent |
| Schéma `RecurrenceFrequency` | 900-909 | ✅ Présent |
| Schéma `RecurrenceRequest` | 911-942 | ✅ Présent |
| Path `GET /events/{id}/occurrences` | 2806-2866 | ✅ Présent |

Vérification au début de l'implémentation : `git diff main..HEAD -- openapi/openapi.yaml` doit rester **vide** sur toute la PR SCRUM-151.

### 4.2 Frontend — types

| Fichier | Type | Motif |
|---|---|---|
| [`frontend/src/types/event.ts`](frontend/src/types/event.ts) | Update | (a) `Event.parentEventId?: number \| null` (après `updatedAt`, ligne ~29). (b) `Event.recurrenceRule?: string \| null` (même bloc). (c) Nouvelle interface `RecurrenceRequest { frequency: RecurrenceFrequency; endDate?: string \| null; maxOccurrences?: number \| null }`. (d) `CreateEventRequest.recurrence?: RecurrenceRequest \| null` (après `tags`, ligne ~82). (e) Const map `RECURRENCE_FREQUENCIES` + type `RecurrenceFrequency = keyof typeof RECURRENCE_FREQUENCIES`. |

### 4.3 Frontend — services

| Fichier | Type | Motif |
|---|---|---|
| [`frontend/src/services/eventApi.ts`](frontend/src/services/eventApi.ts) | Update | + `GetOccurrencesParams` interface (page?, size?) + `getOccurrences(parentId, params?)` fonction. Placée après `getById` (cohérent — c'est un fetch lié à un event spécifique). |

### 4.4 Frontend — hooks

| Fichier | Type | Motif |
|---|---|---|
| [`frontend/src/hooks/useEventForm.ts`](frontend/src/hooks/useEventForm.ts) | Update | (a) `EventFormValues.recurrence: { enabled: boolean; frequency: RecurrenceFrequency; endMode: 'date' \| 'count'; endDate: string; maxOccurrences: string }` ajouté à l'interface (lignes 20-35). (b) `DEFAULT_VALUES.recurrence = { enabled: false, frequency: 'WEEKLY', endMode: 'date', endDate: '', maxOccurrences: '' }` (lignes 99-114). (c) `EventFormErrors.recurrence?: string` (lignes 37-50). (d) `validate()` étendu avec le bloc règles Décision C (après le bloc `tags`, avant `setErrors`). (e) `submitForm` étendu : si `mode === 'create' && values.recurrence.enabled`, construire `payload.recurrence: RecurrenceRequest` à partir des champs de `values.recurrence` (filtrage selon `endMode`, conversion `maxOccurrences: string → number`). (f) `toFormValues` (mode edit hydration) doit produire `recurrence: { enabled: false, ... defaults }` parce que la section n'est pas exposée en edit (Décision E). (g) Persistance sessionStorage : le mécanisme `schedulePersist` couvre automatiquement le nouveau champ — vérifier que `readPersistedForm` rétablit correctement les booléens / strings sans cast manquant. |
| [`frontend/src/hooks/useOccurrences.ts`](frontend/src/hooks/useOccurrences.ts) | **Nouveau** | Hook `useOccurrences(parentId: number \| null, { enabled }): { loading, error, data }`. Pattern miroir `useEvent` / `useAttendees`. Fetch paresseux : `enabled === false` court-circuite ; `enabled === true` + `parentId != null` déclenche `getOccurrences`. AbortController pour cleanup unmount. Pas de cache global (KISS). |
| [`frontend/src/hooks/index.ts`](frontend/src/hooks/index.ts) | Update | + `export { useOccurrences } from './useOccurrences'` |

### 4.5 Frontend — composants et pages

| Fichier | Type | Motif |
|---|---|---|
| [`frontend/src/components/event/EventForm.tsx`](frontend/src/components/event/EventForm.tsx) | Update | Lignes 464-472 : remplacer le `ComingSoonBlock` par la section fonctionnelle **conditionnée sur `mode === 'create'`** (cf. Décision E). Structure visuelle : section `rounded-2xl border border-border/50 bg-foreground/[0.015] px-4 py-4` (miroir « Date & heure » lignes 330-354) avec : (a) Header `flex items-center justify-between` : titre `Récurrence` + switch toggle `values.recurrence.enabled` (pattern allDay lignes 333-348). (b) Body conditionnel `values.recurrence.enabled === true` : Select `frequency` (3 options FR via `RECURRENCE_FREQUENCIES`), radio mutex `endMode` (2 options : « Date de fin » / « Nombre d'occurrences »), Input correspondant (`type="date"` ou `type="number"` avec `min="1" max="52"`). (c) Affichage `errors.recurrence` sous la section. Garder l'icône `Repeat` de lucide déjà importée pour le header. L'autre `ComingSoonBlock` (Pièces jointes, sprint S9) reste **inchangé**. |
| [`frontend/src/components/event/EventCard.tsx`](frontend/src/components/event/EventCard.tsx) | Update | + Import `RefreshCw` de `lucide-react`. + Badge pill `absolute bottom-4 right-4 z-10`, conditionnel `event.parentEventId != null`, style `bg-background/80 backdrop-blur-sm border border-border/40 text-foreground/80 px-2.5 py-1 rounded-full text-xs font-medium flex items-center gap-1.5`. Contenu : `<RefreshCw className="w-3.5 h-3.5" /> Récurrent`. Place : à l'intérieur du `<EventBanner>` (lignes 29-52), après le `<div className="absolute top-4 right-4 z-10">` qui porte le FavoriteButton (lignes 41-43). |
| [`frontend/src/pages/event/EventDetailPage.tsx`](frontend/src/pages/event/EventDetailPage.tsx) | Update | + Import `useOccurrences` (`@/hooks`) et `RefreshCw`, `ChevronDown`, `ChevronUp` (`lucide-react`). + `useState<boolean>(false)` pour `occurrencesOpen`. + `parentId = event.parentEventId ?? (event.recurrenceRule != null ? event.id : null)` (logique Décision G). + `useOccurrences(parentId, { enabled: occurrencesOpen && parentId != null })`. + Rendu section repliable sous la card description : bouton plein-largeur `Voir toutes les occurrences` + chevron, expand au clic, contenu = liste compacte (Décision H) avec `Skeleton` boneyard pendant `loading`. Mise en évidence de l'event courant via classe `bg-foreground/5` ou `ring-2 ring-accent/30` + label `(vous êtes ici)`. Si `data` est vide (parent sans enfants OU standalone) → section n'apparaît pas du tout (gardé via `parentId == null` court-circuit). |

### 4.6 Frontend — tests

| Fichier | Type | Motif |
|---|---|---|
| `frontend/src/__tests__/hooks/useEventForm.test.ts` (ou équivalent — vérifier le path exact via `find frontend/src -name '*useEventForm*'` au moment de l'impl) | Update | + 7 cas (cf. § 6) — defaults recurrence, persist sessionStorage, switch toggle, mutex endMode, validate `recurrence_unbounded` côté client, validate bornes, payload sérialisé. |
| `frontend/src/__tests__/components/event/EventForm.test.tsx` | Update | + 4 cas — section visible uniquement en `mode === 'create'`, switch toggle déplie/replie le body, radio mutex masque/montre les inputs, Select fréquence rend les 3 labels FR. |
| `frontend/src/__tests__/components/event/EventCard.test.tsx` | Update | + 2 cas — badge présent si `parentEventId != null`, badge absent si `parentEventId == null` ou `undefined`. |
| `frontend/src/__tests__/pages/event/EventDetailPage.test.tsx` | Update | + 4 cas — section invisible sur standalone, bouton présent + lazy fetch au clic (vérifier qu'`useOccurrences` est `enabled: false` au mount via mock), liste compacte rendue (titre + date + status), event courant marqué `(vous êtes ici)` sur la page d'une occurrence. |
| `frontend/src/__tests__/hooks/useOccurrences.test.ts` (**nouveau**) | **Nouveau** | + 5 cas — `enabled: false` → pas de fetch, `enabled: true` + parentId valide → loading puis data, error → `error: string`, abort sur unmount, re-fetch si parentId change. |
| `frontend/src/__tests__/services/eventApi.test.ts` (ou équivalent) | Update | + 2 cas — `getOccurrences(123)` appelle `GET /events/123/occurrences`, propage params optionnels. |

### 4.7 Documentation frontend

| Fichier | Section | Modif |
|---|---|---|
| [`frontend/docs/types.md`](frontend/docs/types.md) | Section `Event` | + lignes `parentEventId?: number \| null`, `recurrenceRule?: string \| null` ; + section dédiée `RecurrenceRequest`, `RecurrenceFrequency`, `RECURRENCE_FREQUENCIES` (miroir backend) ; + ligne `CreateEventRequest.recurrence?` |
| [`frontend/docs/components.md`](frontend/docs/components.md) | Section services | + `getOccurrences(parentId, params?)` dans la table des fonctions `eventApi` ; section hooks : + `useOccurrences(parentId, { enabled })` ; section composants : note sur la section Récurrence d'`EventForm` (référer Décision B/C/D), note sur le badge Récurrent d'`EventCard` (Décision F), note sur la section repliable d'`EventDetailPage` (Décision G/H) |
| [`frontend/docs/sprint-context.md`](frontend/docs/sprint-context.md) | Section finale | Ajout section datée `2026-05-15 — SCRUM-151 livré (UI récurrence)` avec résumé des axes 1-10 et la liste des fichiers touchés. Justification écrite de la Décision I (pas de nouveau skeleton, raison) pour traçabilité. |
| [`frontend/AGENTS.md`](frontend/AGENTS.md) | Table « Skeletons existants » lignes 346-361 | **Inchangée** — pas de nouveau `.bones.json` (Décision I) |
| [`openapi/openapi.yaml`](openapi/openapi.yaml) | — | **Inchangé** — contrat déjà figé par SCRUM-147 |

### 4.8 Vérifications cross-fichiers

| Vérification | Commande |
|---|---|
| Pas de modif OpenAPI | `git diff main..HEAD -- openapi/openapi.yaml` doit être vide |
| Pas de modif backend | `git diff main..HEAD -- backend/` doit être vide |
| `parentEventId` lu uniquement, jamais écrit | `grep -rn "parentEventId" frontend/src/ \| grep -v __tests__ \| grep -v 'event\.parentEventId\|payload\.parentEventId'` (en lecture/payload only — JAMAIS de mutation locale) |
| `recurrenceRule` lu uniquement, jamais écrit | Idem `recurrenceRule` |
| `RefreshCw` importé uniquement où utilisé | `grep -rn "RefreshCw" frontend/src/` → 2 fichiers attendus : `EventCard.tsx`, `EventDetailPage.tsx` (peut-être + EventForm si décide d'utiliser RefreshCw au lieu de Repeat — ne PAS faire, garder `Repeat` cohérent avec le ComingSoonBlock précédent) |
| `ComingSoonBlock` recurrence supprimé | `grep -n "ComingSoonBlock.*Répéter\|sprint=\"S8\"" frontend/src/components/event/EventForm.tsx` → 0 (sauf si renommé pour les attachments S9) |

---

## 5. Plan d'exécution séquentiel (étapes numérotées, ordre strict)

> **Règle.** Un commit par étape. Format de message : `<type>(scrum-151): <description courte>`. Co-author Claude sur chaque commit. Vérification post-commit : la commande indiquée pour l'étape. Si elle échoue : revert local + fix + nouveau commit (pas d'amend, cf. `AGENTS.md` racine).

### Étape 1 — Types frontend (`frontend/src/types/event.ts`)

- **Commit** : `feat(scrum-151): add recurrence types to Event and CreateEventRequest`
- **Modifs** : section 4.2 — `Event.parentEventId`, `Event.recurrenceRule`, `CreateEventRequest.recurrence`, `RecurrenceRequest` interface, `RecurrenceFrequency`, `RECURRENCE_FREQUENCIES` const map.
- **Vérification** : `cd frontend && npm run lint` — 0 erreur sur le fichier modifié.

### Étape 2 — Service `getOccurrences` (`frontend/src/services/eventApi.ts`)

- **Commit** : `feat(scrum-151): add getOccurrences in eventApi`
- **Modifs** : section 4.3.
- **Vérification** : `cd frontend && npm run lint`. Si test `eventApi.test.ts` existe : `npm run test src/__tests__/services/eventApi.test.ts` (mise à jour anticipée à l'étape 9).

### Étape 3 — Hook `useOccurrences` (`frontend/src/hooks/useOccurrences.ts`)

- **Commit** : `feat(scrum-151): add useOccurrences hook with lazy fetch`
- **Modifs** : nouveau fichier + ajout à `hooks/index.ts` (section 4.4).
- **Vérification** : `cd frontend && npm run lint`. Test ajouté en étape 9.

### Étape 4 — `useEventForm.ts` — état recurrence

- **Commit** : `feat(scrum-151): add recurrence block to EventFormValues and validation`
- **Modifs** : section 4.4 — `EventFormValues.recurrence`, `DEFAULT_VALUES.recurrence`, `EventFormErrors.recurrence`, `validate()` branche récurrence, `submitForm` sérialisation `payload.recurrence`. **Vigilance** : `schedulePersist` capture l'état complet — vérifier que les booléens et chaines vides sont sérialisés/désérialisés sans cast manquant (cf. `readPersistedForm` ligne 245). Si nécessaire, normaliser `parsed.recurrence` dans `readPersistedForm` (cas où une vieille version persistée n'a pas le champ — fallback `DEFAULT_VALUES.recurrence`).
- **Vérification** : `cd frontend && npm run lint && npm run test src/__tests__/hooks/useEventForm.test.ts` (ou path équivalent). Test étendu en étape 9 ; à cette étape, viser au moins que le test existant ne casse pas (champ optionnel ajouté).

### Étape 5 — `EventForm.tsx` — section Récurrence fonctionnelle

- **Commit** : `feat(scrum-151): replace recurrence ComingSoonBlock with functional section`
- **Modifs** : section 4.5 — remplacement des lignes 464-472. Garde `ComingSoonBlock` du composant pour le slot « Pièces jointes » S9. Vérifier que `mode === 'create'` conditionne tout le bloc (Décision E). Pattern visuel « Date & heure » à mimer rigoureusement (rounded-2xl, border, padding, switch dans header).
- **Vérification** : `cd frontend && npm run lint && npm run test src/__tests__/components/event/EventForm.test.tsx`. Smoke test manuel `npm run dev` puis naviguer sur `/events/new` recommandé.

### Étape 6 — `EventCard.tsx` — badge Récurrent

- **Commit** : `feat(scrum-151): add Recurrent badge on EventCard for occurrences`
- **Modifs** : section 4.5 — import `RefreshCw`, badge pill conditionnel `event.parentEventId != null`.
- **Vérification** : `cd frontend && npm run lint && npm run test src/__tests__/components/event/EventCard.test.tsx`. Smoke test manuel sur une liste d'events (page d'accueil ou page profil après création récurrente backend).

### Étape 7 — `EventDetailPage.tsx` — section repliable inline

- **Commit** : `feat(scrum-151): add inline occurrences section to EventDetailPage`
- **Modifs** : section 4.5 — import hook, état local `occurrencesOpen`, calcul `parentId`, rendu section repliable, liste compacte (Décision H), marqueur « (vous êtes ici) ».
- **Vérification** : `cd frontend && npm run lint && npm run test src/__tests__/pages/event/EventDetailPage.test.tsx`. Smoke test manuel sur la page détail d'un parent récurrent ET sur la page détail d'une occurrence.

### Étape 8 — Vérifications cross-fichiers

- **Commit** : (pas nécessairement un commit si rien ne change ; sinon `chore(scrum-151): clean up unused imports / consistency check`)
- **Modifs** : nettoyage si nécessaire — imports non utilisés, console.log de debug, code mort.
- **Vérification** : section 4.8 — chaque commande doit retourner ce qui est attendu :
  - `git diff main..HEAD -- openapi/openapi.yaml` = vide.
  - `git diff main..HEAD -- backend/` = vide.
  - `grep -n "ComingSoonBlock.*Répéter\|ComingSoonBlock.*sprint=\"S8\"" frontend/src/components/event/EventForm.tsx` = 0 résultat.
  - `grep -rn "RefreshCw" frontend/src/` = 2 fichiers (`EventCard.tsx`, `EventDetailPage.tsx`).

### Étape 9 — Tests frontend (couverture complète)

- **Commit** : `test(scrum-151): cover recurrence form, badge, occurrences hook and inline list`
- **Modifs** : section 4.6 — tous les tests étendus + nouveaux.
- **Vérification** : `cd frontend && npm run test` complet → 100 % vert ; vérifier que la couverture sur les fichiers touchés est ≥ 80 % V8 (à valider via output couverture Vitest).

### Étape 10 — Documentation frontend

- **Commit** : `docs(scrum-151): update types, components and sprint-context for recurrence UI`
- **Modifs** : section 4.7 — `types.md`, `components.md`, `sprint-context.md`.
- **Vérification** : `git diff` cohérent. Pas d'oubli (les 4 sites touchés et le hook nouveau sont tous documentés).

### Étape 11 — Vérification finale + push sur PR #172 + ajustement titre/description

- **Pas un commit unique** — étape de vérification globale (cf. § 7).
- **Push** sur la branche existante : `git push origin feature/scrum-169-profile-username-url` (pas de `-u` puisque le tracking remote existe déjà).
- **Mise à jour du titre PR #172** (la PR existante absorbe SCRUM-151) :
  ```bash
  gh pr edit 172 --title "feat: SCRUM-137/146/169/151 — co-organizers, comments, usernames, recurrence UI"
  ```
- **Mise à jour de la description PR #172** : ajouter une section *« SCRUM-151 — UI récurrence »* à la description existante (sections Résumé, Changements, Tests, Test plan, Documentation). Utiliser `gh pr view 172 --json body --jq .body > /tmp/pr172.md` pour récupérer la description actuelle, l'éditer en ajoutant la nouvelle section sous *« Changements »*, puis `gh pr edit 172 --body-file /tmp/pr172.md`.
- **Pas de `gh pr create`** — PR #172 existe déjà (cf. Décision A).
- **Pas de merge** par l'agent. Elie merge lui-même.

---

## 6. Tests

### 6.1 Frontend — `useEventForm.test.ts` (extensions)

| Test | Assertion | Lien décision |
|---|---|---|
| `recurrence_defaults_isCollapsed` | Mount en mode create → `values.recurrence.enabled === false`, body section invisible | Décision E |
| `recurrence_toggleEnable_revealsBody` | `setFieldValue('recurrence', { ...prev, enabled: true })` → body visible avec Select + radio + Input | Décision B |
| `recurrence_radioMutex_switchesField` | endMode='date' → endDate visible, maxOccurrences masqué ; toggle endMode='count' → inverse | Décision B |
| `recurrence_validate_unbounded` | enabled=true, endMode='date', endDate='' → `errors.recurrence` contient le message FR `Définissez une date de fin OU un nombre d'occurrences.` | Décision C |
| `recurrence_validate_dateBeforeStart` | enabled=true, endMode='date', endDate < startDate.toLocalDate() → `errors.recurrence` non-null | Décision C |
| `recurrence_validate_countOutOfRange` | enabled=true, endMode='count', maxOccurrences='53' → `errors.recurrence` non-null ; '0' → idem ; '52' → OK | Décision C |
| `recurrence_payload_built` | enabled=true, frequency='WEEKLY', endMode='date', endDate='2026-12-31' → `payload.recurrence === { frequency: 'WEEKLY', endDate: '2026-12-31' }` (pas de maxOccurrences) | Décision B + K |
| `recurrence_payload_omittedInEditMode` | mode='edit', recurrence.enabled=true (cas théorique) → `payload.recurrence` absent du PUT | Décision E |
| `recurrence_persisted_survivesRefresh` | Activer recurrence + renseigner endDate + déclencher persist (300 ms) + remount → state restauré | Décision A (pattern projet) |
| `recurrence_disabled_omitsPayload` | enabled=false → `payload.recurrence === undefined` (pas null) | Décision K |

### 6.2 Frontend — `EventForm.test.tsx` (extensions)

| Test | Assertion |
|---|---|
| `recurrenceSection_visibleInCreateMode` | Render avec `mode="create"` → la section "Récurrence" est visible (header + switch) |
| `recurrenceSection_hiddenInEditMode` | Render avec `mode="edit"` → la section n'est pas dans le DOM |
| `recurrenceSection_bodyExpandsOnToggle` | Click sur le switch → body (Select fréquence + radio + Input) visible |
| `recurrenceSelect_rendersFrenchLabels` | Body visible → `<option>` text contient "Chaque semaine", "Toutes les 2 semaines", "Chaque mois" |
| `recurrenceRadio_switchesInput` | Body visible → click radio "Nombre d'occurrences" → Input `type="number"` visible, Input `type="date"` masqué |

### 6.3 Frontend — `EventCard.test.tsx` (extensions)

| Test | Assertion |
|---|---|
| `recurrenceBadge_shownIfOccurrence` | `event.parentEventId = 42` → badge `Récurrent` + icône `RefreshCw` dans le DOM |
| `recurrenceBadge_hiddenForStandalone` | `event.parentEventId = null` → badge absent du DOM |
| `recurrenceBadge_hiddenForParent` | `event.recurrenceRule = "FREQ=WEEKLY;UNTIL=20260601"`, `event.parentEventId = null` → badge absent (Décision F : parent reste sans badge) |

### 6.4 Frontend — `EventDetailPage.test.tsx` (extensions)

| Test | Assertion |
|---|---|
| `occurrencesSection_hiddenForStandalone` | event sans recurrenceRule et sans parentEventId → bouton "Voir toutes les occurrences" absent du DOM |
| `occurrencesSection_shownForParent` | event.recurrenceRule = "FREQ=WEEKLY;UNTIL=20260601" → bouton présent, `enabled: false` au mount (vérifier via mock `useOccurrences`) |
| `occurrencesSection_lazyFetchOnExpand` | Click sur le bouton → `useOccurrences` re-called avec `enabled: true`, fetch déclenché |
| `occurrencesSection_renderCompactList` | Mock data 3 occurrences → 3 lignes rendues, chacune avec date formatée + status badge + lien href `/events/<id>` |
| `occurrencesSection_marksCurrentEvent` | Sur la page d'une occurrence, après expand → la ligne correspondant à l'event courant porte la classe distinctive + texte "(vous êtes ici)" |
| `occurrencesSection_handlesCancelledParent` | Parent CANCELLED + occurrences PUBLISHED → liste affichée normalement, status badges visibles | (Décision G + H) |

### 6.5 Frontend — `useOccurrences.test.ts` (nouveau fichier)

| Test | Assertion |
|---|---|
| `notEnabled_returnsNullData` | `useOccurrences(42, { enabled: false })` → `{ loading: false, error: null, data: null }`, aucun appel API |
| `enabled_fetchesAndReturnsData` | `useOccurrences(42, { enabled: true })` → loading=true initial, puis data=[Event, Event] après resolve mock |
| `enabledWithoutParentId_isNoop` | `useOccurrences(null, { enabled: true })` → reste idle, pas de fetch |
| `errorMappedToString` | Mock 500 → `error: string` non-null, `data: null` |
| `unmountAbortsFetch` | Unmount pendant le loading → pas de warning React "setState on unmounted" |
| `refetchOnParentIdChange` | parentId 42 → 43 avec enabled=true → 2 appels API distincts |

### 6.6 Frontend — `eventApi.test.ts` (extensions)

| Test | Assertion |
|---|---|
| `getOccurrences_callsCorrectURL` | `getOccurrences(42)` → mock check `GET /events/42/occurrences` |
| `getOccurrences_propagatesParams` | `getOccurrences(42, { size: 10, page: 1 })` → query string `?size=10&page=1` |
| `getOccurrences_returnsArray` | Mock 200 + `[event1, event2]` → fonction retourne ce array, typé `Event[]` |

### 6.7 Couverture cible et garde-fous

- **Couverture V8 ≥ 80 %** sur les fichiers touchés / créés (cf. `frontend/AGENTS.md` ligne 394). Vérification : output `npm run test -- --coverage` ; lignes du diff couvertes ≥ 80 % par fichier.
- **Aucune assertion de couleur CSS brute** dans les tests (cf. `frontend/AGENTS.md` lignes 17-21 — happy-dom conserve le format hex). Si on assert le badge background, utiliser `bg-background/80` (className) ou le format `#xxxxxx`, jamais `rgb(...)`.
- **Mocks sessionStorage** : spy directement sur l'instance (`vi.spyOn(sessionStorage, 'setItem')`), pas sur le prototype (cf. `frontend/AGENTS.md` ligne 22).

### 6.8 Cas-limites explicites (résumé — couverts par les tableaux ci-dessus)

- Récurrence + `allDay = true` : `endDate.toLocalDate()` se base sur la date du datetime form post-`applyAllDayBounds` — `applyAllDayBounds` est appelé avant la validation `recurrence_end_before_start` dans `validate()` (§ 6.1 `recurrence_validate_dateBeforeStart`).
- Comparaison `recurrence.endDate` (LocalDate) vs `startDate` (LocalDateTime) → on prend `startDate.split('T')[0]` côté frontend (`YYYY-MM-DD`) pour aligner sur la `endDate` `type="date"` du form.
- Parent CANCELLED + occurrences PUBLISHED → § 6.4 `occurrencesSection_handlesCancelledParent`. Badge status visible par occurrence.
- Occurrence orpheline (`parentEventId === null` après cascade `ON DELETE SET NULL`) → traité comme standalone : pas de badge `Récurrent`, pas de section occurrences. § 6.3 `recurrenceBadge_hiddenForStandalone` couvre la première moitié.
- `getOccurrences` sur un parent sans enfants → backend retourne `200 + []`. Frontend : `useOccurrences.data === []` → la section déplie mais affiche un message « Aucune occurrence à venir » (à ajouter au composant ; ce cas est rare en pratique car le parent récurrent a forcément 1+ enfant matérialisé en transaction atomique côté SCRUM-147 D10).
- Création récurrente : `POST /events` renvoie le DTO du **parent uniquement** (cf. SCRUM-147 décision 10). `onSuccess?(event)` redirige vers `/events/{parentId}`. Le user voit la page détail du parent ; en cliquant « Voir toutes les occurrences », il charge les 51 enfants. **Confirmer dans l'implémentation** que `EventCreatePage` redirige bien sur `/events/${createdEvent.id}` après création.
- Anonyme : `GET /events/{id}/occurrences` est `@PermitAll` → le bouton fonctionne sans token. Aucun garde-fou auth nécessaire dans `useOccurrences`.

---

## 7. Critères de done (checklist à exécuter avant push final sur PR #172)

Exécuter **toutes** les commandes ci-dessous **dans l'ordre** et confirmer chaque ligne :

- [ ] `cd frontend && npm run lint` — 0 erreur.
- [ ] `cd frontend && npm run test` — 100 % vert ; couverture ≥ 80 % V8 sur fichiers touchés/créés.
- [ ] `git diff origin/main..HEAD -- openapi/openapi.yaml` montre **uniquement** les changements SCRUM-169 hérités (si déjà présents dans PR #172) — **aucun changement SCRUM-151** sur ce fichier.
- [ ] `git diff origin/main..HEAD -- backend/` montre **uniquement** les changements SCRUM-137/146/169 hérités (le cas échéant) — **aucun changement SCRUM-151** sur le backend.
- [ ] `git log --oneline origin/main..HEAD` montre les commits SCRUM-151 propres en plus des commits SCRUM-137/146/169 existants.
- [ ] `grep -n "ComingSoonBlock.*Répéter\|sprint=\"S8\"" frontend/src/components/event/EventForm.tsx` = 0 (placeholder remplacé).
- [ ] `grep -rn "RefreshCw" frontend/src/` montre 2 fichiers sources : `EventCard.tsx` et `EventDetailPage.tsx`.
- [ ] `grep -rn "RECURRENCE_FREQUENCIES" frontend/src/` montre au moins : `types/event.ts` (déclaration), `components/event/EventForm.tsx` (consommation), tests.
- [ ] `git diff` sur la doc cohérent : `frontend/docs/types.md` (recurrence types), `frontend/docs/components.md` (service + hook + composants), `frontend/docs/sprint-context.md` (section datée 2026-05-15).
- [ ] `git status` propre, branche poussée, pas de `.env`/`devcontainer-lock.json` mégarde.
- [ ] Smoke test manuel sur `npm run dev` :
  - [ ] `/events/new` : section Récurrence visible, switch déplie/replie le body, radio mutex fonctionne, validation client bloque la création sans `endDate` ni `maxOccurrences`.
  - [ ] Création d'un event récurrent → redirection vers `/events/{parentId}` ; bouton « Voir toutes les occurrences (N) » présent ; click → liste affichée.
  - [ ] Navigation sur la page détail d'une occurrence → badge `Récurrent` invisible sur `EventCard` mais bouton « Voir toutes les occurrences » présent (sur l'occurrence, on affiche aussi la liste du parent + l'event courant marqué « vous êtes ici »).
  - [ ] Navigation sur la home / search → la card d'une occurrence porte le badge `Récurrent`.
- [ ] Commits SCRUM-151 poussés sur `feature/scrum-169-profile-username-url`. PR #172 visible avec les nouveaux commits.
- [ ] Titre PR #172 ajusté via `gh pr edit 172 --title "feat: SCRUM-137/146/169/151 — co-organizers, comments, usernames, recurrence UI"`.
- [ ] Description PR #172 enrichie d'une section « SCRUM-151 — UI récurrence » (cf. § 5 étape 11).
- [ ] **Pas de nouvelle PR créée** (`gh pr list --head feature/scrum-151-*` = vide).
- [ ] **Pas de merge** par l'agent. Elie merge lui-même.
- [ ] Boucle review Copilot itérée jusqu'à 0 BLOQUANT / 0 IMPORTANT non-clos sur les nouveaux commits.

---

## 8. Workflow Git (rappel concis)

- **Branche** : `feature/scrum-169-profile-username-url` (existante, branche de PR #172). **Pas de nouvelle branche.** Cf. Décision A.
- **Pré-démarrage** : `git checkout feature/scrum-169-profile-username-url && git pull origin feature/scrum-169-profile-username-url` pour s'aligner sur la tip remote (cas où Copilot a poussé des fixes sur SCRUM-137/146/169 entre-temps).
- **1 commit par étape** du Plan d'exécution (§ 5).
- **Format de message** : `<type>(scrum-151): <description courte>`. Types autorisés : `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`, `ci`, `perf`. Scope obligatoirement `scrum-151` pour `feat`/`refactor`/`perf` (cf. `AGENTS.md` racine). Le scope du commit reste `scrum-151` même si le titre de PR couvre plusieurs tickets — c'est l'ID Jira qui compte.
- **Co-author Claude** sur chaque commit (cf. instructions racine — HEREDOC standard).
- **Push** : `git push origin feature/scrum-169-profile-username-url` (tracking déjà configuré, pas de `-u`).
- **Pas de `gh pr create`** — PR #172 existe déjà et absorbe SCRUM-151.
- **`gh pr edit 172 --title ...`** à l'étape 11 pour refléter le scope élargi.
- **Si conflit avec un commit Copilot poussé entre-temps sur PR #172** : `git pull --rebase origin feature/scrum-169-profile-username-url` puis résoudre. Aucun destructif sans confirmation. En cas de doute → demander à Elie.
- **Pas de merge** de la PR par l'agent. Elie merge lui-même.

---

## 9. Garde-fous

- **Aucune action destructive** : pas de `rm -rf`, `git reset --hard`, `git checkout -- .`, `--no-verify`, force-push sur `main` ou sur `feature/scrum-169-profile-username-url`. Le force-push sur la branche partagée de PR #172 est **strictement interdit** — il écraserait les commits SCRUM-137/146/169 et casserait l'historique review. Si conflit / état incompréhensible → demander à Elie avant d'agir.
- **Pas de nouvelle branche `feature/scrum-151-*`** : SCRUM-151 vit sur la branche de PR #172 (Décision A). Si on se retrouve sur une branche autre, `git checkout feature/scrum-169-profile-username-url` immédiatement avant tout commit.
- **Pas de `gh pr create`** : PR #172 absorbe SCRUM-151 (Décision A). Vérifier `gh pr list --head feature/scrum-151-*` = vide au moment de la livraison.
- **Pas de modification de `openapi/openapi.yaml`** — le contrat backend est figé. Si une incohérence est détectée pendant l'implémentation, **STOP et lever la question à Elie**. Ne pas patcher le YAML en passant.
- **Pas de modification du backend** (entité, service, resource, migration, tests Java). Le périmètre est strictement frontend.
- **Pas de nouvelle route React** (`/events/:id/occurrences`) — la section inline suffit (Décision G).
- **Pas de nouveau `.bones.json`** — `Skeleton` boneyard générique suffit (Décision I). La table « Skeletons existants » de `frontend/AGENTS.md` reste inchangée.
- **Pas de propagation aux occurrences** depuis l'edit du parent (cohérent D17 backend). La section Récurrence reste invisible en `mode === 'edit'` (Décision E).
- **Pas de duplication de logique** entre `EventForm` et un éventuel `RecurrenceForm` séparé — la section Récurrence est inline dans `EventForm` (refactor en composant dédié uniquement si une 2e page la consomme, ce qui n'est pas le cas en S8 ; règle of three projet).
- **Cohérence doc / code** : si la doc dérive du code pendant l'implémentation → fix dans le même commit (règle d'or `frontend/AGENTS.md` ligne 378).
- **Pré-push** : pas de `.env`, `.devcontainer/devcontainer-lock.json`, fichiers temporaires (`*.log`, `node_modules/`) committés.
- **Avant chaque étape qui touche `EventForm.tsx` / `EventDetailPage.tsx` / `eventApi.ts`** : `git diff origin/main -- <fichier>` pour vérifier qu'on hérite bien des changements PR #172 et qu'on ne casse pas son diff.
- **Si un cas non couvert par la spec émerge** : documenter dans `frontend/docs/sprint-context.md` (section finale datée) et continuer ; pas de micro-arbitrage interruptif. Si le cas dépasse les Décisions A-K (changement de contrat, scope élargi), s'arrêter et demander.

---

## 10. Skills à utiliser

| Skill | Quand |
|---|---|
| `superpowers:executing-plans` | Pour itérer méthodiquement étape par étape du § 5 |
| `superpowers:test-driven-development` | Pour les étapes 4-7 (validation `useEventForm`, sections `EventForm` / `EventCard` / `EventDetailPage`) — écrire les tests § 6 avant l'implémentation correspondante |
| `superpowers:systematic-debugging` | Si un test casse de manière inattendue (notamment la persistance sessionStorage du bloc `recurrence`) |
| `superpowers:verification-before-completion` | **Obligatoire** avant chaque claim "done" et avant le push final + `gh pr edit 172` (étape 11) — exécuter toutes les commandes du § 7 |
| `frontend-design` | Pour l'étape 5 (section Récurrence dans `EventForm`) — UX du switch, du radio mutex, des transitions ; et l'étape 7 (section repliable EventDetailPage) — chevron animation, marqueur "(vous êtes ici)", densité visuelle de la liste compacte |
| `code-simplifier` | Après les étapes 4 (`useEventForm`) et 7 (`EventDetailPage`) — refactor lisibilité, dédup |
| `context7` | Si besoin de docs lib externes (`lucide-react` icons disponibles, `boneyard-js/react` Skeleton API, `vitest` `useFakeTimers` pour le debounce sessionStorage) |
| `superpowers:requesting-code-review` + `pr-review-toolkit:review-pr` | Une fois la PR ouverte, lancer la boucle Copilot |
| `superpowers:receiving-code-review` | Pour traiter les retours Copilot avec rigueur (étape boucle post-PR) |
| `superpowers:finishing-a-development-branch` | Pour décider du moment exact du push final sur PR #172 (la PR est déjà ouverte) |
| `github` MCP | `gh pr edit 172`, `gh pr checks 172 --watch`, `gh api repos/unige-pinfo6-2026/unige-events/pulls/172/comments`. **Pas de `gh pr create`** (Décision A). |
| `claude-md-management:revise-claude-md` | Optionnel — uniquement si une leçon de session vaut la peine d'être conservée |

---

## Launch prompt (literal, à copier-coller pour lancer l'implémentation)

```
Implémente SCRUM-151 en autonomie complète selon la spec
`specs_archives/specs_claude/specs_scrum-151.md`.

CONTEXTE BRANCHE / PR (Décision A — lire AVANT d'agir) :
- SCRUM-151 est livré DIRECTEMENT sur la branche existante
  `feature/scrum-169-profile-username-url` (la branche de PR #172).
- PAS de nouvelle branche `feature/scrum-151-*`.
- PAS de `gh pr create`. PR #172 (déjà ouverte, en review) absorbe SCRUM-151
  par-dessus SCRUM-137/146/169 — précédent direct : PR #170 absorbée par
  PR #172.
- À la fin, le titre et la description de PR #172 sont mis à jour via
  `gh pr edit 172` pour refléter le scope élargi.

Étapes :

1. Lis la spec en entier avant de toucher au moindre fichier. Internalise
   les Décisions techniques A → K et le Plan d'exécution séquentiel
   (sections 3 et 5). En particulier, la Décision A (pas de nouvelle PR)
   et la Décision E (section Récurrence en mode create uniquement).
2. Lis `AGENTS.md` (racine + `frontend/AGENTS.md`) pour les conventions de
   commit, scope, doc à toucher. Le backend n'est PAS dans le scope —
   ignore `backend/AGENTS.md` au-delà de la compréhension du contrat
   SCRUM-147 déjà figé dans `openapi/openapi.yaml`.
3. Aligne-toi sur la branche de PR #172 :
   `git checkout feature/scrum-169-profile-username-url`
   `git pull origin feature/scrum-169-profile-username-url`
   Vérifie via `gh pr view 172` que PR #172 est encore OUVERTE. Si elle
   vient de merger sur main, STOP et préviens Elie (le contexte change —
   il faudra alors brancher sur main, ce n'est plus la même Décision A).
4. Avant chaque étape : `git diff origin/main..HEAD -- openapi/openapi.yaml`
   ne doit JAMAIS contenir de modif SCRUM-151. Si tu détectes une
   incohérence contrat / besoin, STOP et lève la question.
5. Exécute chaque étape du Plan dans l'ordre exact (§ 5, étapes 1-10).
   Un commit par étape, format `<type>(scrum-151): <description>` avec
   co-author Claude. Le scope reste `scrum-151` même si la PR couvre
   plusieurs tickets. Vérifie après chaque commit avec la commande
   indiquée (`npm run lint`, `npm run test src/__tests__/...`).
6. Étape 5 (section Récurrence dans EventForm) et étape 7 (section
   repliable EventDetailPage) : applique le skill `frontend-design` —
   UX du switch, du radio mutex, des transitions, marqueur "(vous êtes
   ici)", densité de la liste compacte. Pattern visuel à mimer : section
   « Date & heure » de EventForm lignes 330-354 pour la zone Récurrence.
7. Avant le push final (étape 11) : `superpowers:verification-before-
   completion` non négociable — exécute toutes les commandes de la
   section 7 (Critères de done) et confirme chaque ligne, y compris le
   smoke test manuel sur `npm run dev`. Aucun claim "done" sans cette
   verification.
8. Push final sur PR #172 :
   `git push origin feature/scrum-169-profile-username-url`
   (pas de `-u`, tracking déjà configuré ; PAS de `gh pr create`).
9. Mets à jour le titre et la description de PR #172 :
   `gh pr edit 172 --title "feat: SCRUM-137/146/169/151 — co-organizers, comments, usernames, recurrence UI"`
   Puis enrichis la description en ajoutant une section
   « SCRUM-151 — UI récurrence » sous « Changements » (procédure
   détaillée § 5 étape 11 de la spec).
10. Lance la boucle review Copilot sur PR #172 :
    `gh pr checks 172 --watch`, puis
    `gh api repos/unige-pinfo6-2026/unige-events/pulls/172/comments` pour
    chaque retour. Filtre les retours sur les NOUVEAUX commits SCRUM-151
    (les commits SCRUM-137/146/169 ont leur propre boucle review déjà
    avancée — ne pas y toucher). Applique `superpowers:receiving-code-
    review`, fixe, commit (`fix(scrum-151): apply Copilot review —
    <résumé court>`), push, re-check. Itère jusqu'à 0 BLOQUANT /
    0 IMPORTANT non-clos sur les nouveaux commits.
11. Quand tous les checks sont verts et la review propre sur les nouveaux
    commits : signale-moi avec le lien de PR #172 mis à jour et un résumé
    des commits SCRUM-151 livrés. JE MERGE MOI-MÊME.

Garde-fous (rappel) :
- Aucune action destructive sans confirmation explicite.
- INTERDICTION ABSOLUE de force-push sur la branche partagée
  `feature/scrum-169-profile-username-url` (écraserait SCRUM-137/146/169).
- Aucune modification de `openapi/openapi.yaml` ni du `backend/`.
- Pas de nouvelle branche `feature/scrum-151-*`. Pas de `gh pr create`.
- Pas de nouvelle route `/events/:id/occurrences` (Décision G — inline).
- Pas de nouveau `.bones.json` (Décision I — Skeleton générique).
- Section Récurrence en `mode === 'create'` uniquement (Décision E).
- Badge `Récurrent` sur EventCard uniquement si `parentEventId != null`
  (Décision F — occurrences only, pas le parent).
- Si la doc dérive du code → fix dans le même commit.
- Si un cas non couvert par la spec émerge : documente-le dans
  `frontend/docs/sprint-context.md` (section datée finale) et continue ;
  ne me réveille pas pour des micro-arbitrages.
- Ne touche PAS aux commits SCRUM-137/146/169 existants ; tes commits
  s'ajoutent simplement par-dessus.
```
