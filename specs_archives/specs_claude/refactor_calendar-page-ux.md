# Spécification technique — Refonte UX de la page `/calendar`

> Branche : `refactor/calendar-page-ux`
> Statut : **analyse + plan** (aucun changement de code à ce stade)
> Date : 2026-05-27
> Périmètre : **frontend uniquement** — aucun changement backend, aucun changement de contrat API.

---

## 1. Résumé

Deux changements UX sur `/calendar` (`CalendarPage.tsx`) :

1. **Déplacer l'abonnement calendrier** (`CalendarSubscribeButton` — Apple/Outlook webcal, Google Calendar, téléchargement `.ics` + régénération token) depuis la page profil (`/profile/me`, sidebar droite) vers la page `/calendar`. C'est l'endroit naturel : un utilisateur qui vient s'abonner pense au calendrier, pas à son profil.
2. **Refondre la légende des catégories** affichée en haut à droite de `/calendar`. Aujourd'hui : une grappe de petites chips noires (pastille de couleur + label) qui « flottent » à côté du titre — petite, peu lisible, sans valeur ajoutée. Cible : transformer en **filtres cliquables** (un clic masque/affiche les events de cette catégorie sur le calendrier) avec un traitement visuel plus généreux et propre.

---

## 2. État actuel (diagnostic)

### 2.1 Abonnement calendrier — `CalendarSubscribeButton`

- **Composant** : [`frontend/src/components/calendar/CalendarSubscribeButton.tsx`](../../frontend/src/components/calendar/CalendarSubscribeButton.tsx) — 140 lignes, autoportant.
- **Rendu actuel** : uniquement dans `ProfilePage.tsx` (lignes 21 + 214) **et seulement** sur la route `/profile/me` (gate `isMeRoute`).
- **API** :
  - `getCalendarToken()` → `GET /users/me/calendar-token` ([userService.ts:141-144](../../frontend/src/services/userService.ts))
  - `regenerateCalendarToken()` → `POST /users/me/calendar-token/regenerate` ([userService.ts:146-149](../../frontend/src/services/userService.ts))
  - Backend [`UserCalendarTokenResource.java`](../../backend/services/user-service/src/main/java/ch/unige/events/user/calendar/resource/UserCalendarTokenResource.java) — les deux endpoints sont `@Authenticated`.
- **Type partagé** : [`frontend/src/types/calendarToken.ts`](../../frontend/src/types/calendarToken.ts) — `{ calendarToken, webcalUrl, httpsUrl }`.
- **Tests existants** : 14 cas dans [`__tests__/components/calendar/CalendarSubscribeButton.test.tsx`](../../frontend/src/__tests__/components/calendar/CalendarSubscribeButton.test.tsx) — **agnostiques de la page parente**, à conserver tels quels.
- **Mocks dans tests de ProfilePage** : `getCalendarToken`/`regenerateCalendarToken` mockés défensivement ([ProfilePage.test.tsx:16-26](../../frontend/src/__tests__/pages/profile/ProfilePage.test.tsx)) — à retirer après déplacement.

### 2.2 Légende des catégories — état actuel

[`CalendarPage.tsx:16-27`](../../frontend/src/pages/calendar/CalendarPage.tsx) :

```tsx
<div className="flex flex-wrap gap-2 lg:max-w-xs">
  {Object.entries(EVENT_CATEGORIES).map(([key, cat]) => (
    <div
      key={key}
      className="flex items-center gap-2 bg-background/60 backdrop-blur-sm border border-border rounded-full px-3 py-1.5"
    >
      <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ backgroundColor: cat.color }} />
      <span className="text-sm text-foreground/70 font-medium">{cat.name}</span>
    </div>
  ))}
</div>
```

Source des couleurs : [`frontend/src/types/event.ts:55-62`](../../frontend/src/types/event.ts) — `EVENT_CATEGORIES` (6 entrées : ACADEMIC bleu, SPORTS vert, CULTURAL violet, SOCIAL orange, CONFERENCE cyan, OTHER gris).

**Problèmes UX** :
- Pastilles 10 px (`w-2.5`), texte `text-sm/70` → faiblement visible, surtout sur fond sombre.
- `lg:max-w-xs` (20 rem) contraint la grille → wrap moche selon la largeur.
- Aucune valeur fonctionnelle : c'est purement décoratif alors que la couleur des events sur le calendrier suggère naturellement un filtre par catégorie.
- Mal placé : tassé en haut à droite à côté d'un titre `Calendrier du campus` de 64 px de haut, créant un déséquilibre.

### 2.3 Contraintes de routage / auth

- Route `/calendar` : **publique** ([`AppRouter.tsx:49-51`](../../frontend/src/router/AppRouter.tsx)), pas dans `<PrivateRoute>`.
- L'abonnement requiert un token → caller authentifié. Le composant doit donc être **gated** côté nouvelle page : visible uniquement si `useAuth().user !== null`, sinon CTA « Connectez-vous pour vous abonner » OU section cachée (à trancher § 7 Q1).

### 2.4 Tests existants à toucher

- `__tests__/pages/profile/ProfilePage.test.tsx` — retirer les mocks calendar-token + le test « items-start sur la grille about/calendar sur /me » (la grille n'existera plus côté `/me`).
- `__tests__/pages/calendar/CalendarPage.test.tsx` — 17 cas existants (calendrier seul, legend non testée). À étendre pour le nouveau comportement (filtres + subscription).
- `__tests__/components/calendar/CalendarSubscribeButton.test.tsx` — **inchangé** (le composant est testé en isolation).

---

## 3. Plan de correction

### 3.1 Volet A — Déplacer l'abonnement calendrier

**Fichiers à modifier** :

1. `frontend/src/pages/profile/ProfilePage.tsx`
   - Retirer l'import `CalendarSubscribeButton`.
   - Retirer le rendu `{isMeRoute && <CalendarSubscribeButton/>}` (lignes 212-218).
   - Si la grille `about/calendar` (`items-start`) n'est plus utile sans la 2ᵉ colonne, simplifier le layout.

2. `frontend/src/pages/calendar/CalendarPage.tsx`
   - Importer `useAuth` + `CalendarSubscribeButton`.
   - Ajouter une section conditionnelle au-dessous du calendrier (ou au-dessus, cf. § 7 Q2) :
     ```tsx
     {user && (
       <SectionWrapper padding="bottom" size="lg">
         <CalendarSubscribeButton />
       </SectionWrapper>
     )}
     ```
   - Si non authentifié : choix par défaut **(a) ne rien afficher** (le bouton « Se connecter » de la Navbar suffit) — alternatives en § 7 Q1.

3. `frontend/src/__tests__/pages/profile/ProfilePage.test.tsx`
   - Retirer les mocks `getCalendarToken`/`regenerateCalendarToken` (lignes 16-26).
   - Retirer le test « items-start … sur /me ».

4. `frontend/src/__tests__/pages/calendar/CalendarPage.test.tsx`
   - Ajouter mock `useAuth` + mocks `getCalendarToken`/`regenerateCalendarToken`.
   - Tests :
     - T-Sub-1 : authentifié → bloc `CalendarSubscribeButton` rendu.
     - T-Sub-2 : non authentifié → bloc absent.

### 3.2 Volet B — Refondre la légende en filtres cliquables

**Approche recommandée** (§ 7 Q3 pour valider) : transformer la légende en **toggles de filtre**.

- État local dans `CalendarPage` : `Set<EventCategory>` des catégories **désactivées** (vide par défaut → toutes affichées).
- Cliquer un chip → toggle la catégorie dans le Set.
- Filtrage : les events passés à `EventCalendar` sont filtrés sur `!disabled.has(event.category)`.
- Visuel d'un chip :
  - Actif (catégorie visible) : fond `cat.color` à 15 % alpha, bordure `cat.color` à 40 %, label en `text-foreground`, pastille pleine en `cat.color`.
  - Inactif (catégorie masquée) : fond `bg-foreground/5`, bordure `border` standard, label en `text-foreground/40`, pastille creuse (`border + bg-transparent`).
  - Hover : bordure foreground/30.
  - Taille : padding `px-3 py-2`, pastille 12 px, `text-sm font-semibold`, `rounded-full`. Plus généreux que l'actuel.
  - `cursor-pointer`, `aria-pressed`, `role="button"` pour a11y.
- Layout : **bandeau horizontal au-dessus du calendrier** (sous le titre, pleine largeur), `flex flex-wrap gap-2 justify-start`. Plus de `max-w-xs` étranglé.
- Extraction : nouveau composant local non exporté `CategoryFilterBar` dans `CalendarPage.tsx` (pattern AGENTS.md § « DRY » : un composant local quand la structure n'est utilisée qu'ici), OU un composant partagé `components/calendar/CategoryFilterBar.tsx` si on prévoit de le réutiliser (page recherche par ex.).

**Pattern variants** (AGENTS.md § 86-103) :

```tsx
const chipVariants = {
  active:   'border text-foreground hover:border-foreground/40',
  inactive: 'bg-foreground/5 border-border text-foreground/40 hover:text-foreground/60',
} as const
```

La couleur de fond/border active est appliquée via `style` car la valeur est data-driven (couleur de la catégorie) — cf. AGENTS.md qui tolère explicitement les `style` pour valeurs dérivées des données (cf. usage actuel sur la pastille).

**Tests** (étendre `CalendarPage.test.tsx`) :
- T-Legend-1 : toutes les chips rendues avec leur nom (régression).
- T-Legend-2 : click sur une chip masque la catégorie → events filtrés (vérifier `useCalendarEvents` ou le mock de `EventCalendar` reçoit la liste filtrée).
- T-Legend-3 : re-click ré-affiche.
- T-Legend-4 : `aria-pressed` reflète l'état actif/inactif.

### 3.3 Couleurs hardcodées dans `EVENT_CATEGORIES`

Hors scope mais à noter : les couleurs sont hex brutes (`#2563eb` etc.) au lieu de tokens. Conforme à l'usage existant (déjà présent sur les events, le banner, etc.), donc on ne touche pas dans cette PR.

---

## 4. Critères d'acceptance

| # | Scénario | Résultat attendu |
|---|---|---|
| AC-1 | Utilisateur authentifié visite `/calendar` | Voit le calendrier + filtres + bloc abonnement |
| AC-2 | Utilisateur non authentifié visite `/calendar` | Voit le calendrier + filtres ; **pas** de bloc abonnement |
| AC-3 | Utilisateur authentifié visite `/profile/me` | Le bloc abonnement **n'est plus là** ; le profil reste fonctionnel |
| AC-4 | Click sur le chip d'une catégorie | Les events de cette catégorie sont masqués sur le calendrier ; le chip change d'apparence (inactif) |
| AC-5 | Re-click sur un chip inactif | Les events réapparaissent ; chip ré-active |
| AC-6 | Plusieurs catégories masquées en même temps | Toutes filtrées simultanément, état indépendant par chip |
| AC-7 | Reload de la page | État des filtres réinitialisé (pas de persist par défaut, cf. § 7 Q4) |
| AC-8 | A11y | Chips clavier-navigables, `aria-pressed` correct, focus visible |

---

## 5. Tests

### 5.1 Frontend

- `ProfilePage.test.tsx` : retirer 2 mocks + 1 test (items-start grille).
- `CalendarPage.test.tsx` : +6 tests (3 subscription gating + 3 filters behavior + 1 a11y).
- `CalendarSubscribeButton.test.tsx` : **inchangé**.
- Si extraction en composant partagé `CategoryFilterBar.tsx` → nouveau fichier de tests dédié.

### 5.2 Couverture cible

Conformément à AGENTS.md (Sonar ≥ 80 % nouveau code, plus haut possible) :
- Branches du nouveau filtrage (chip actif/inactif, événement filtré ou non) entièrement couvertes.
- Gating `user` (true/false) couvert.

### 5.3 Backend

Aucun changement, aucun test backend à ajouter/modifier.

---

## 6. Documentation à mettre à jour

D'après `frontend/AGENTS.md` § « Maintenance de la documentation » :

- ✅ `docs/components.md` : si on extrait `CategoryFilterBar` en composant partagé, l'ajouter à la section composants.
- ✅ `docs/sprint-context.md` : entrée en fin de tâche.
- ❌ `docs/architecture.md` : pas de nouvelle route.
- ❌ `docs/types.md` : pas de nouveau type.
- ❌ `openapi.yaml` : non touché.
- ❌ Skeleton : pas de nouveau layout async (le subscription a déjà son loading state interne, le calendrier garde `event-calendar.bones.json`).

---

## 7. Décisions tranchées

| # | Question | Décision |
|---|---|---|
| Q1 | Non authentifié → bloc abonnement | **Caché complètement.** Pas de placeholder, pas de CTA. La Navbar suffit. |
| Q2 | Placement du bloc abonnement | **Section dédiée au-dessous du calendrier.** `SectionWrapper` séparé, pleine largeur, après `EventCalendar`. |
| Q3 | Approche légende | **Filtres cliquables.** Chaque chip toggle la catégorie ; visuel teinté quand actif, grisé quand inactif. |
| Q4 | Persistance des filtres | **Aucune.** État éphémère côté React, reset au reload. |
| Q5 | Composant local ou partagé | **Partagé** : `components/calendar/CategoryFilterBar.tsx` (réutilisable sur `EventsSearchPage` plus tard). |
