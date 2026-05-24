# Spec — Couverture frontend MAXIMALE (et utile, sans exclusion)

| Champ | Valeur |
|---|---|
| Périmètre | `frontend/` — React 19, TypeScript strict, Vite, Vitest 4 + happy-dom + @testing-library/react, couverture **V8** → SonarCloud `unige-pinfo6-2026_unige-events-frontend` |
| Branche de travail | `feature/frontend-coverage-max` |
| Base | `main` |
| Sprint | Hors-sprint (dette technique / qualité) |
| Objectif chiffré | Maximiser la couverture **overall** (lcov) **utile, sans exclusion** — cible ≈ **99 % lignes / ≈ 97-98 % branches / blend ≈ 98,5-99 %** (baseline 98,5 % L / 92,0 % Br) |
| Tickets liés | — (suite de la mission backend, PR #200 / `specs_backend-coverage-max.md`) |
| Auteur spec | Claude (analyse pilotée par le PO) |
| Date | 2026-05-24 |
| Règle d'or | **Pas une seule nouvelle exclusion de couverture. Pas un seul test inutile.** Tout ce qui n'est ni atteignable ni utile est documenté comme **plafond** (§6), jamais forcé, jamais exclu. 0 fichier `src/` applicatif modifié — sauf bug applicatif **signalé** et **accordé** par le PO. |

---

## §1. Vue d'ensemble

### 1.1 Baseline mesurée en local (`cd frontend && npm run test:coverage` — 159 fichiers, 2009 tests verts)

| Métrique | Couvert / total | % | Reste NC |
|---|---|---|---|
| **Lines** | 3960 / 4020 | **98,50 %** | 60 |
| **Branches** | 2661 / 2892 | **92,01 %** | **231** |
| Functions | 1178 / 1214 | 97,03 % | 36 |
| Statements | 4388 / 4531 | 96,84 % | 143 |
| **Blend type-Sonar** `(LH+BRH)/(LF+BRF)` | 6621 / 6912 | **≈ 95,84 %** | — |

**Cadrage** : les lignes sont quasi pleines ; **le gisement réel = les branches (231 NC)**. SonarCloud calcule un blend lignes+conditions, donc fermer des branches maximise le gate. La heat-map « new-code » du PO (67 lignes NC) est une **carte de priorisation**, pas la cible : on pilote sur le **lcov overall**.

> Correctif de lecture sur la heat-map : « `assets/` 8 NC, surtout `Banner.tsx` » = en réalité les **8-9 logos SVG `assets/faculty/*Logo.tsx`** (chacun 1/2 ligne). `Banner.tsx` est **déjà 100 % couvert** en overall — rien à y faire.

### 1.2 Décompte par dossier (lcov overall) + cibles

| Dossier | Lignes (cov/total) | Branches (cov/total) | Gisement dominant |
|---|---|---|---|
| `hooks/useEventForm.ts` | 284/296 | **278/317** | helpers purs + `validate()` + `submitForm()` (39 Br) |
| `components/event/` | ~97-99 % | ~85-95 % | MentionAutocomplete (17 Br), EventForm (4 Br), CommentItem (4 Br), CommentSection (3 Br), CoOrganizersEditor (6 Br), EventAttachmentsEditor (8 Br), DraftsResumeStrip (5 Br) |
| `components/user/` | 100 % L | ~85-95 % | UsernameAutocomplete (11 Br — famille MentionAutocomplete), UserAvatar (2 Br), FollowButton/UserBanner (1 Br) |
| `components/auth/AuthProvider.tsx` | **0/3** | **0/2** | **jamais testé** |
| `components/faculty/` + `assets/faculty/*` | logos 1/2 | — | 9 logos SVG couvrables transitivement par FacultyMarquee/FacultyCard |
| `hooks/` (autres) | 100 % L | n-1/n | chemins d'erreur (mock service rejeté) |
| `pages/` | ~98,5 % L | gros gisement Br | EventDetailPage (20 Br), ProfileEditPage (8 Br), EventEditPage/AdminPage/FollowListPage (6 Br), EventStatsPage (5 Br), MyPublicationsPage/EventsSearchPage (4 Br) |
| `services/sessionId.ts` | 10/11 | 4/4 | catch localStorage (1 L) |
| `utils/` | 100 % L | 1-2 Br | formatRelativeTime (2 Br), icsGenerator / draftsResumeStripLayout (1 Br) |
| `constants/` `contexts/` `router/` `types/` `App.tsx` | ~100 % | ~100 % | rien / résiduel défensif |

### 1.3 Décompte A / B / C

| Classe | Définition | ~Nb items | Action spec |
|---|---|---|---|
| **A** | Rendu + assertion/interaction triviale, providers triviaux | ~25-35 branches/lignes | écrire |
| **B** | Mécanisme requis (mock service, contexte/router wrappé, fake/real timers, état de formulaire, erreur réseau simulée, fichier/clipboard/storage mocké) | ~150-180 branches/lignes | écrire |
| **C — plafond accepté** | Inutile ou inatteignable : garde anti-course, ref détachée défensive, branche morte par typage, contenu statique | ~25-40 branches | **documenter (§6), jamais forcer, jamais exclure** |

---

## §2. Contexte — mécanismes déterminants (vérifiés sur le source réel)

1. **Couverture V8 + lcov source de vérité.** `npm run test:coverage` → `coverage/lcov.info` + table texte. Parser : `SF:` (fichier), `DA:<ligne>,<hits>` (`hits=0` → ligne NC), `BRDA:<ligne>,<block>,<branche>,<taken>` (`taken=0`/`-` → branche NC), `LF/LH`, `BRF/BRH`. **Re-dériver les numéros de ligne d'un lcov frais** — la projection V8 sur le JSX bouge (les n° de cette spec sont indicatifs, pas contractuels).

2. **Boucle interne LOCALE rapide** (atout vs backend qui n'avait que la CI) : `npx vitest run src/__tests__/<sous-dossier> --coverage` pour itérer, puis **re-mesurer le coverage GLOBAL** (`npm run test:coverage`) avant de figer.

3. **Pièges happy-dom (non négociables, cf. `frontend/AGENTS.md`)** :
   - `navigator.clipboard` getter-only → `Object.defineProperty(navigator, 'clipboard', { value: …, configurable: true })`, jamais `Object.assign`.
   - couleurs CSS conservées en **hexadécimal** (`#0891b2`) — asserter en hex, pas `rgb()`.
   - `localStorage`/`sessionStorage` n'héritent **pas** de `Storage.prototype` → `vi.spyOn(localStorage, 'getItem')` sur l'**instance**, jamais le prototype.

4. **Setup global déjà en place** (`src/__tests__/setup.ts`, ne pas re-mocker) : bones enregistrés, HTTP réel bloqué (interceptor happy-dom), `matchMedia` / `ResizeObserver` / `getBoundingClientRect` mockés.

5. **Patterns de test du projet (réutiliser, DRY)** :
   - Mock service : `vi.mock('@/services/…')` hoisté avant l'import du composant, puis `vi.mocked(...)`. Modèles : `__tests__/components/PrivateRoute.test.tsx`, `__tests__/contexts/FavoritesContext.test.tsx`.
   - **Pas de `renderWithProviders` central** → wrapper **inline** par fichier (`MemoryRouter`/`ThemeProvider`/`FavoritesProvider`/`AuthContext.Provider` selon besoin). Modèles : `__tests__/router/AppRouter.test.tsx`, `__tests__/components/Navbar.test.tsx`, `__tests__/pages/my-events/MyParticipationsPage.test.tsx`.
   - Interactions : **`fireEvent`** (PAS `@testing-library/user-event`, absent des deps).
   - Hooks : `renderHook(fn, { wrapper, initialProps })` + `act`. Modèles : `__tests__/contexts/FavoritesContext.test.tsx`, `__tests__/hooks/useDebounce.test.ts`.
   - **Timers** : **fake** pour hook pur (`vi.useFakeTimers()` + `act(() => vi.advanceTimersByTime(ms))`, cf. `useDebounce.test.ts`) ; **réels + `waitFor(..., { timeout })`** pour les composants à debounce (cf. `MentionAutocomplete.test.tsx`).

6. **Flaky historique** : `MentionAutocomplete > ArrowDown` (debounce en timers réels). Être **déterministe** : `findBy*`/`waitFor`, pas de course, résoudre les promesses de search explicitement. Si flaky avéré en CI → re-run le job.

7. **Couverture transitive** : rendre un composant conteneur couvre ses enfants. Ex. `render(<FacultyMarquee/>)` → `Marquee` rend les enfants → `FacultyCard` × N facultés → **les 9 logos SVG** couverts en un test.

8. **Boucle CI** (après la boucle locale) : `gh run list/view` en background-poll. Tout vert **SAUF `Deploy / Deploy to Preview`**. Gate bloquant : `Build / Build Frontend` + **`[unige-events-frontend] SonarCloud Code Analysis`** (new-code ≥ 80 %, dup ≤ 3 %, ratings A). Un `BLOCKED` résiduel = `REVIEW_REQUIRED` (humain), pas un échec CI.

---

## §3. Décisions tranchées (NE PAS revisiter)

- **D1 — Type, branche, PR.** Nouvelle branche `feature/frontend-coverage-max` depuis `main` ; nouvelle PR vers `main` ; titre **`test(frontend): maximise coverage without exclusions`** (type `test` → scope libre, validé par `.github/workflows/pr-title-check.yml`).
- **D2 — ZÉRO exclusion.** Interdit d'élargir `sonar.exclusions` dans `sonar-project.properties`, d'ajouter `/* c8 ignore */` ou `/* istanbul ignore */`, ou tout `@vitest`/coverage-skip. Les items C sont **documentés en §6**, pas exclus.
- **D3 — Pas de test « pour le chiffre ».** Le PO veut « maximum atteignable **ET utile** ». Une cible inutile (assert d'un SVG statique par-path, forçage d'une garde morte) ou inatteignable (course) = **plafond**, pas un test bidon.
- **D4 — DRY.** Factoriser les helpers de rendu/mock/wrap-providers réutilisés dans un fichier (petit helper local). Pas de copier-coller massif (gate dup ≤ 3 % sur le nouveau code).
- **D5 — Priorisation par gisement.** Ordre : `useEventForm` → `event/` (MentionAutocomplete, EventForm, CommentItem, CommentForm + éditeurs) → `user/` (UsernameAutocomplete) → `AuthProvider` → `useCoOrganizerInvitations` + hooks résiduels → `pages/**` (branches) → `faculty/` + `sessionId` + `utils/` résiduels.

---

## §4. Analyse par fichier (ligne-précise — classes A/B/C)

Marqueurs : **[+]** = compléter un fichier de test existant · **[C]** = créer un fichier de test · **[~]** = plafond (ne rien écrire).

### 4.1 `hooks/useEventForm.ts` — 12 L + 39 Br (LE gros morceau)

Tester via `renderHook(() => useEventForm({ mode, … }))` + `act`, `vi.mock('@/services/eventApi')`. Beaucoup de helpers purs déjà appelés indirectement → piloter par `setFieldValue` + `triggerPublish`/`triggerDraftSave` + `handleSubmit`.

| Cible | Classe | Approche |
|---|---|---|
| `toTemplateValues` (≈217-233) — mode create + `templateEvent` | **B** | [+/C] `useEventForm({ mode:'create', templateEvent: makeEvent() })` → asserter `values.title/description/category/faculty/capacity/tags` repris, `startDate/endDate/registrationDeadline` vides |
| `readPersistedForm` catch (≈291-294) — JSON corrompu | **B** | seed `sessionStorage.setItem(DRAFT_FORM_KEY,'{bad')` puis monter en create → `console.warn` + `removeItem` + retour defaults ; spy `console.warn` |
| `getValidationDetails` flatMap `[]` (≈343-345) — détail non-objet / sans message | **B** | mock `createEvent` rejette avec `axios` error `response.data.details=[{}, {message:'  '}]` → submit → asserter pas de crash + message générique |
| Messages récurrence (≈659/663/676…) — endMode date/count, vide/invalide/antérieure | **B** | create + `setFieldValue('recurrence',{enabled:true,endMode:'date',endDate:''})` → `triggerPublish` → `errors.recurrence` = « Définissez une date… » ; idem endDate invalide, antérieure à start, count vide/hors borne |
| Garde double-submit (≈691-693) `if (submitting\|\|draftSaving)` | **B** | `createEvent` renvoie une promesse contrôlée (non résolue) ; 1er `triggerPublish()` (met `submitting`), 2e appel immédiat → early-return, puis résoudre |
| Branches `validate()` : titre>max, description>max, capacité non-entière/≤0, websiteUrl>max / invalide, email>max / invalide, deadline invalide / ≥ start, tags>max / tag>maxLen | **B** | piloter chaque champ via `setFieldValue` puis `triggerPublish` ; asserter le message d'erreur correspondant |
| `localizeValidationDetail` / `getApiErrorMessage` (must not be blank/null, future date, after, >0, ≥1, message FR, message technique) | **B** | mock `createEvent`/`updateEvent` rejette avec `details` variés + `message` FR/technique → asserter le `onError(message)` |
| `isValidEmail` whitespaces exotiques (0x1680, 0x2000-200a, 0x2028/2029, 0x202f, 0x205f, 0x3000, 0xFEFF) | **B** | `setFieldValue('contactEmail', 'a@b.c ')` etc. → `errors.contactEmail` invalide (tedious mais atteignable) |
| `toFormValues` nullish-coalescing (`description ?? ''`, `capacity?.toString() ?? ''`, `faculty ?? null`, `allDay ?? false`, `websiteUrl/contactEmail/tags ?? …`, `registrationDeadline ? …`) | **A/B** | edit mode avec un event **complet** vs un event aux champs **absents** → couvre les 2 côtés de chaque `??` |
| `applyAllDayBounds` `if (!datePart) return dateTime` (≈184-186) | **C** | [~] `datePart` vide ⇔ valeur commençant par `T` ; un input `type="date"` ne produit jamais ça → **garde défensive morte** (§6) |

### 4.2 `components/event/MentionAutocomplete.tsx` — ~7 L + 17 Br

Real timers, `vi.mock('@/services/userService')` (`searchUsernames`). Réutiliser le `Harness` du test existant. **Déterminisme** (flaky historique).

| Cible | Classe | Approche |
|---|---|---|
| `.catch()` 112-113 — `searchUsernames` rejette → `setResults([])` | **B** | [+] `mockSearch.mockRejectedValue(new Error())`, taper `@al`, `waitFor` → liste « Aucun utilisateur. » / vide, pas de crash |
| rAF 202-203 — `t.focus()` + `setSelectionRange` post-commit | **B** | [+] sélectionner une suggestion (Enter/clic) puis `waitFor` que la valeur du textarea contient `@<username> ` (flush rAF) |
| Garde « ne pas rouvrir après sélection » 64-66 | **B** | [+] après commit (`lastInsertedRef` posé), re-déclencher `detectActiveMention` sur une valeur commençant encore par le handle inséré, caret ≥ insertedEnd → dropdown reste fermée |
| Escape pendant chargement, `results.length===0`, ArrowUp clamp à 0, ArrowDown clamp à max, `displayName` présent/absent, `aria-selected` | **A/B** | [+] cas clavier + résultats avec/sans `displayName` |
| Gardes anti-course `if (requestIdRef.current !== myReqId) return` ×3 (then/catch/finally) | **C** | [~] requête périmée par une plus récente — **non-déterministe** (§6) |
| `if (!el) return` (effets), `if (!ta …)`, `if (t)` (rAF), refs détachées | **C** | [~] gardes défensives sur ref nulle — happy-dom garde la ref montée (§6) |

### 4.3 `components/event/EventForm.tsx` — ~6 L + 4 Br

Composant présentationnel piloté par props. `render(<EventForm {...props}/>)` + `fireEvent`. Helper local `makeFormProps(overrides)` (DRY).

| Cible | Classe | Approche |
|---|---|---|
| `RecurrenceSection` (≈101-198) — mode create + `recurrence.enabled` | **B** | [+/C] `mode:'create'`, `values.recurrence.enabled:true` → `fireEvent.change` fréquence, toggle radios endMode date↔count, saisir endDate / maxOccurrences → asserter `onFieldChange('recurrence', …)` |
| Faculty select `(value as Faculty) \|\| null` (≈494) | **B** | [+] `fireEvent.change(facultySelect, { target:{ value:'' }})` → `onFieldChange('faculty', null)` ; + une faculté réelle → branche truthy |
| `joinDateTime` → `''` (≈229-230) — heure renseignée, minute vide | **B** | [+] poser une date puis changer l'heure en laissant la minute `''` → `onFieldChange(field, '')` |
| Handlers/slots optionnels : `onDelete`/`onCancel`/`onSaveDraft` présents vs absents ; `imagePreview`/`selectedImageName`/`attachmentsSection`/`coOrganizersSection`/`errors.image` présents vs absents ; `busy` désactive | **A/B** | [+] 2 rendus (avec / sans) → asserter présence/absence des boutons & blocs, `disabled` |
| `followsAllDay && values.allDay` — masquage sélecteurs d'heure | **A** | [+] `values.allDay:true` → `data-testid="startDate-time-selectors"` masqué (`aria-hidden`) |

### 4.4 `components/event/CommentItem.tsx` — ~3 L + 4 Br

`vi.mock('@/hooks/useReportComment')` + `vi.mock('@/hooks/useCommentLike')`, wrap `MemoryRouter`.

| Cible | Classe | Approche |
|---|---|---|
| `handleReportSubmit` 104-105 | **B** | [+] `currentUserId` set + `!isAuthor` (canReport) → clic « Signaler » → ReportModal → submit ; `submitReport` mock renvoie `true` (modal se ferme) puis `false` (reste) |
| `profileSlug` null (avatar `<div>` + nom `<span>` fallback) | **A** | [+] comment avec `authorUsername=null` et `authorId=null` → pas de `<Link>` |
| Badge `authorIsOrganizer`, replies (`comment.replies`), `canLike`/`title` connecté vs anonyme | **A** | [+] variations de props |

### 4.5 `components/event/CommentForm.tsx` — 2 L + 1 Br

| Cible | Classe | Approche |
|---|---|---|
| `handleAutocompleteChange` 72/76 (`setContent` + `void newCaretPos`) | **B** | [+] `vi.mock('@/services/userService')`, render CommentForm, taper `@al`, résoudre la search, commit (Enter/clic) → `content` mis à jour (couvre les 2 lignes) |
| `onCancel` présent (bouton « Annuler ») | **A** | [+] rendu avec/sans `onCancel` |

### 4.6 `hooks/useCoOrganizerInvitations.ts` — 3 L

`vi.mock('@/hooks/useAuth')` (`isAuthenticated:true`) + `vi.mock('@/services/coOrganizerApi')`, `renderHook`.

| Cible | Classe | Approche |
|---|---|---|
| `refresh` catch 46 — `getMyInvitations` rejette → `setError` | **B** | [+/C] mock rejette → `waitFor(() => result.current.error === 'Impossible de charger vos invitations.')` |
| `decline` catch 77-78 — `declineInvitation` rejette → restore + refresh | **B** | [+/C] charger 1 invitation, `declineInvitation` rejette → `act(decline)` → invitation restaurée, refresh rappelé |

### 4.7 `components/auth/AuthProvider.tsx` — 0 % (jamais testé)

| Cible | Classe | Approche |
|---|---|---|
| Rendu + `onRedirectCallback` (branches `isRelative`) | **B** | [C] `vi.mock('@auth0/auth0-react')` : `Auth0Provider` capture ses props (`let captured`); `vi.mock('react-router-dom', …)` ou wrap `MemoryRouter` + spy `useNavigate`. `render(<AuthProvider>…)`, puis invoquer `captured.onRedirectCallback({returnTo:'/dash'})` → `navigate('/dash',{replace:true})` ; `{returnTo:'//evil'}` → `navigate('/',…)` ; `undefined` → `'/'` |

### 4.8 `components/faculty/` + `assets/faculty/*Logo.tsx` — 9 logos SVG

| Cible | Classe | Approche |
|---|---|---|
| FacultyMarquee + FacultyCard + 9 logos SVG (transitif) | **A** | [C] un seul `render(<FacultyMarquee/>)` couvre `Marquee` → `FacultyCard` × N → tous les logos ; asserter ≥ 1 `<svg>` par faculté. **Utile** (bandeau facultés). Pas d'assertion par-`<path>` (statique) |
| `FacultyBadge` branches `id == null` / `!id && "bg-overlay"` | **C** | [~] le type `id: Faculty` interdit `null` → **état impossible** ; le test existant couvre les branches réelles (§6) |

### 4.9 `services/sessionId.ts` — 1 L (ligne 28)

| Cible | Classe | Approche |
|---|---|---|
| catch 25-28 — localStorage throw → `generateUuidV4()` fallback | **B** | [+] `vi.spyOn(localStorage,'getItem').mockImplementation(() => { throw new Error('denied') })` (**instance, pas prototype**), `getOrCreateSessionId()` → match `UUID_RE` |

### 4.10 `pages/**` — gisement de branches (peu de lignes)

Wrap inline (`MemoryRouter`/contexts) + mock des services/hooks consommés. Cibler les **états** non couverts.

| Cible | Classe | Approche |
|---|---|---|
| `EventDetailPage` (≈20 Br) | **B** | états : event introuvable/erreur, anonyme vs connecté, auteur/admin (boutons edit/delete), commentaires vides, like/report, occurrences |
| `ProfileEditPage` (8 Br), `EventEditPage` (6 Br), `AdminPage` (6 Br), `FollowListPage` (6 Br), `EventStatsPage` (5 Br), `MyPublicationsPage` (4 Br), `EventsSearchPage` (4 Br) | **B** | états d'erreur (service rejeté), états vides, rendus conditionnels (rôle, pagination, drafts, filtres) ; compléter les tests existants |
| `ProfilePage` (1 L + 3 Br), `FeedPage` (1 L + 2 Br), `EventCreatePage` (1 L + 1 Br), `MyFavoritesPage`/`MyParticipationsPage`/`LandingPage` (1 Br) | **A/B** | brancher l'état conditionnel restant |
| Gardes défensives résiduelles (fallback inatteignable) | **C** | [~] documenter si rencontrées (§6) |

### 4.11 `components/**` + `hooks/**` + `utils/**` résiduels

| Cible | Classe | Approche |
|---|---|---|
| `UsernameAutocomplete` (11 Br) | **B/C** | même famille que MentionAutocomplete : B pour catch/clavier/résultats, **C** pour les gardes anti-course |
| `EventAttachmentsEditor` (8 Br), `CoOrganizersEditor` (6 Br), `DraftsResumeStrip` (5 Br), `Navbar` (4 Br), `TagInput` (4 Br), `CommentSection` (3 Br) | **A/B** | états d'upload/erreur, limites (max tags), navigation, rendus conditionnels |
| `AttendeeCard`/`UserAvatar`/`UserBanner`/`FollowButton`/`CalendarSubscribeButton`/`EventCalendar`/`ImageCropper` (1-3 Br) | **A/B** | brancher la variation manquante (avatar absent, erreur calendrier, etc.) |
| Hooks à `n-1/n` Br (`useAttendance`, `useFeed`, `useCoOrganizers`, `useMyFollowRequests`, `useAdminFeatured`, `useUserProfile`…) | **B** | mock service **rejeté** → couvrir le chemin d'erreur |
| `formatRelativeTime` (2 Br), `icsGenerator` (1 Br), `draftsResumeStripLayout` (1 Br) | **A** | entrées limites (dates extrêmes, liste vide) |

---

## §5. Étapes d'implémentation (ORDONNÉES)

0. **Setup** : vérifier `git log` / branche, créer `feature/frontend-coverage-max` depuis `origin/main`. `cd frontend && npm run test:coverage` pour fixer la baseline locale.
1. **`useEventForm.ts`** (gisement #1) — helpers purs + `validate()` + `submitForm()` (§4.1). Commit `test(frontend): cover useEventForm validation & persistence paths`.
2. **`event/` composants** — MentionAutocomplete, EventForm, CommentItem, CommentForm (§4.2-4.5). Commit `test(frontend): cover event form & comment components`.
3. **`user/UsernameAutocomplete`** + composants user résiduels (§4.11).
4. **`AuthProvider`** (§4.7) + **`useCoOrganizerInvitations`** + hooks résiduels chemins d'erreur (§4.6, §4.11). Commit `test(frontend): cover AuthProvider & hook error paths`.
5. **`pages/**`** — branches d'états (§4.10). Commit par lot de pages.
6. **`faculty/`** + **`sessionId`** + **`utils/`** résiduels (§4.8-4.9, §4.11). Commit `test(frontend): cover faculty marquee, sessionId fallback & utils`.
7. **Stabilisation** : `npm run test:coverage` global + `npm run lint` verts ; re-parser le lcov pour les résiduels ; classer en C ce qui reste (§6). Pousser, ouvrir la PR, **boucler la CI** jusqu'au vert (sauf Deploy Preview), remplir §8.

> Itération : `npx vitest run src/__tests__/<dossier> --coverage` pendant le travail, **re-mesure globale** avant chaque commit.

---

## §6. Plafond accepté (C) — exhaustif

### Tier 1 — inatteignable / inutile (C pur, jamais forcé, jamais exclu)

| Item | Raison structurelle |
|---|---|
| `MentionAutocomplete` / `UsernameAutocomplete` : `if (requestIdRef.current !== myReqId) return` ×3 (then/catch/finally) | Branche « requête périmée » : ne se prend que si une 2e recherche termine avant la 1re. **Non-déterministe** ; un test forçant la course serait artificiel et flaky. |
| `if (!el) return` / `if (!ta …)` / `if (t)` dans effets, cleanup, rAF | Gardes de **ref détachée** : happy-dom garde la ref montée pendant le test ; la branche nulle ne survient qu'en démontage concurrent réel. Défensif. |
| `useEventForm.applyAllDayBounds` → `if (!datePart) return dateTime` | `datePart` vide ⇔ valeur commençant par `T` ; un input `type="date"` ne produit jamais ça. **Branche morte**. |
| `FacultyBadge` : `id == null` / `!id && "bg-overlay"` | Le type `id: Faculty` **interdit** `null`. Forcer via `id={null as any}` testerait un état que le compilateur garantit impossible. |
| Contenu `<path>` des 9 logos SVG | Statique, aucune logique. Le **rendu** du composant est couvert (transitif via FacultyMarquee) ; asserter chaque `<path>` n'apporte rien. |
| `default:`/fallbacks d'erreur inatteignables éventuellement repérés en implémentation | À documenter ici au cas par cas avec leur raison. |

### Tier 2 — atteignable mais à faible valeur (à trancher en implémentation)

| Item | Pourquoi marginal |
|---|---|
| Whitespaces Unicode exotiques de `isValidEmail` (0x1680, 0x2028…) | Atteignable (B) mais purement défensif ; à couvrir si le coût reste faible (un test paramétré), sinon C documenté. |
| Variantes décoratives pures (classes conditionnelles sans effet observable) | Couvrir seulement si l'assertion reste **utile** (état visible/aria), pas pour le seul hit de branche. |

### Estimation du plafond

- **Cibles A+B fermées** : ~85-90 % du gap branches + quasi-100 % des lignes → **≈ 99 % L / ≈ 97-98 % Br / blend ≈ 98,5-99 %** (baseline 95,8 %).
- **100 % impossible** sans dénaturer le code (tests artificiels interdits par D3) : restent les C Tier-1 ci-dessus.

### Bug applicatif

**Aucun bug détecté à l'analyse.** Si l'implémentation en révèle un (ex. comportement faux derrière une branche jamais exécutée) → **le SIGNALER au PO** et attendre l'accord avant tout fix (modèle backend : NPE `ReportService.listByStatus`, §8 de `specs_backend-coverage-max.md`). Ne pas corriger du code applicatif en douce.

---

## §7. Checklist finale de validation

- [x] **0 nouvelle exclusion** : `sonar-project.properties` inchangé ; aucun `c8 ignore` / `istanbul ignore` / skip de couverture (D2).
- [x] **0 fichier `src/` applicatif modifié** (`git diff --stat origin/main` = uniquement `*.test.tsx` / `*.test.ts` + cette spec).
- [x] Titre PR `test(frontend): maximise coverage without exclusions` ; branche `feature/frontend-coverage-max` → `main` (D1).
- [x] `npm run lint` (`eslint .`) vert + `tsc -b --noEmit` vert (TS strict, **pas de `any`**).
- [x] `npm run test:coverage` vert + lcov overall en hausse (L 98,5 → **99,97 %**, Br 92,0 → **97,13 %**).
- [ ] Duplication ≤ 3 % sur le nouveau code (helpers locaux factorisés, D4) — _confirmé par le gate SonarCloud en CI._
- [ ] CI verte **sauf `Deploy / Deploy to Preview`** ; **gate SonarCloud `unige-events-frontend` franchi** (new-code ≥ 80 %, dup ≤ 3 %, ratings A) — _en cours de vérification._
- [x] Plafond (C) **documenté** en §6 + §8.2, aucun test bidon (D3).
- [x] §8 remplie avec le résultat chiffré.

---

## §8. Résultat d'implémentation

Mesuré en local (`npm run test:coverage`, lcov overall) — **2185 tests verts / 163 fichiers** (avant : 2009 / 159 ; **+176 tests, +4 fichiers**). `eslint .` ✅, `tsc -b --noEmit` ✅, **0 fichier `src/` applicatif modifié**, **0 nouvelle exclusion**.

| Métrique | Avant | Après |
|---|---|---|
| Lines | 98,50 % (3960/4020) | **99,97 % (4029/4030)** |
| Branches | 92,01 % (2661/2892) | **97,13 % (2816/2899)** |
| Statements | 96,84 % (4388/4531) | **98,78 % (4486/4541)** |
| Functions | 97,03 % (1178/1214) | **99,42 % (1213/1220)** |
| Blend type-Sonar (L+Br)/(L+Br) | ≈ 95,84 % | **≈ 98,79 %** |

> Les dénominateurs montent légèrement (4020→4030 L, 2892→2899 Br) parce que des fichiers jusque-là **jamais chargés** par un test (`AuthProvider`, `FacultyMarquee`/`FacultyCard` + les SVG facultés) entrent désormais dans le rapport V8 — instrumentés **et** couverts.

### §8.1 Bug applicatif
**Aucun bug applicatif détecté.** Chaque cible non couverte testée s'est comportée correctement ; aucun code applicatif n'a été modifié. (Note de sécurité validée au passage : la garde `isRelative` de `AuthProvider.onRedirectCallback` rejette correctement les `returnTo` protocol-relatifs `//` et absolus → pas d'open-redirect.)

### §8.2 Plafond final atteint — **≈ 98,79 % blend** (99,97 % L / 97,13 % Br)
Résiduel = **1 ligne + 83 branches**, 100 % classées **C** (jamais forcées, jamais exclues), regroupées par raison structurelle :

| Catégorie C | Exemples (fichier @ ligne) | Raison |
|---|---|---|
| **Garde anti-course** `if (requestIdRef.current !== id) return` / `if (!isCurrent()) return` | MentionAutocomplete @106/108/116 · UsernameAutocomplete @127/131/136/141 · useFeed @105 · useAttendees @73 · useOccurrences @50 | Branche « requête périmée » — non-déterministe, un test forcerait une course flaky. |
| **Garde montage tardif** `if (cancelled) return` / `if (!mountedRef.current) return` | useAttendance @160/186 · useUserProfile @67 · useUserParticipations @45 · ProfileEditPage @138/142 · FollowListPage @232/252 | Ne se déclenche qu'après démontage/redondance — inatteignable sans simuler un cycle de vie artificiel. |
| **Garde bouton désactivé** `if (pending) return` derrière `disabled={…}` | EventEditPage @157 · FollowButton @73 · FollowRequestsPanel @128/140 · EventAttachmentsEditor @81/109 · ImageCropper @26 · CommentItem @84/161 | happy-dom/jsdom ne déclenche pas `onClick` sur un bouton désactivé → le re-entry guard est inatteignable par l'UI. |
| **Garde de ref détachée** `if (!el/!t) return` | MentionAutocomplete @76/80/201 · CommentForm @56 · PendingAttachmentsEditor @61 · TagInput @20/29 | La ref reste montée pendant le test ; la branche nulle = démontage concurrent réel. |
| **Branche interdite par le type / état impossible** | useEventForm @185 (`!datePart` ⇔ valeur en `T`) · @718 (`category \|\| "OTHER"`, validé requis) · EventDetailPage @75/419/431/445/460 · EventStatsPage @82 · EventSearchSidebar @23 · UserAvatar @23 / UserBanner @22 · CoOrganizersEditor `Array.isArray` else · ProfilePage @181/345/360 | Le typage TS (ou un invariant amont) rend la branche structurellement morte. |
| **Repli inatteignable / défensif** | EventAttachmentsEditor @166 (`limitExceeded`, validate rejette d'abord) · EventCalendar @100/143 (react-big-calendar normalise `views`) · icsGenerator @39 (ligne vide, helper privé) · useAdminFeatured @75 · useEventStats @69 · Navbar @78 · AuthProvider @22 (fallback env `audience`) | Repli/fallback qu'aucun chemin réel n'emprunte (helper privé, dépendance externe, config env). |

**100 % est inatteignable sans tests artificiels (interdit par D3).** Le seul résiduel de *ligne* (useEventForm:185) est une garde défensive morte ; tout le reste est branche. Objectif « max utile sans exclusion » **atteint**.
