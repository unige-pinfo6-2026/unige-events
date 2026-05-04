# Specs Frontend Polish S7 — 6 fix UX repérés au fil du sprint

> **Branche :** `feature/s7-frontend-polish-fixes` (à créer depuis `origin/main` avec `--no-track` OBLIGATOIRE)
> **Base :** `origin/main` à jour (HEAD `32d1999` au moment de la spec — backlogs Flyway-aligned + suppression du ticket fantôme SCRUM-164 inclus)
> **Sprint :** S7 (24 avril – 8 mai 2026)
> **Ticket Jira :** **AUCUN** — c'est une PR de polish UX repérée au fil du sprint, pas un ticket SCRUM. Le scope CI sera `chore` (les types `feat`/`refactor`/`perf` imposent un scope `scrum-XXX`, pas `chore`).
> **Story Points :** N/A (lot de 6 fix indépendants ; ~3-5 SP cumulés)
> **Frontend lié :** N/A (PR frontend autonome, aucun changement backend)
> **Règle d'or `openapi-first` :** **NON APPLICABLE** — aucun nouvel endpoint, aucune modification de schéma API. Tout vit côté UI.

---

## Contexte

### Le besoin produit

Six frottements UX ont été remontés sur le fil du sprint 7 par l'utilisation réelle de l'app :

1. **Navbar profil** : un dropdown nested (« Mes événements » → favoris/participations/publications) donne l'impression d'un menu dans un menu, peu lisible.
2. **Skeleton « Mes brouillons »** : le placeholder de chargement de la bannière brouillons sur `/events/new` est cassé — un seul breakpoint manuel à 320px → blob isolé sur desktop.
3. **Bouton flottant `Créer un événement`** sur `/my-events/publications` : `position: fixed bottom-6 right-6` recouvre le footer quand on scroll en bas.
4. **Skeleton `event-edit`** : ne reflète pas le layout actuel d'`EventForm` (« très étrange »). En passant, **le skeleton `event-detail` est aussi dépassé** depuis l'ajout des champs SCRUM-117 (`websiteUrl`, `contactEmail`, `registrationDeadline`, `tags`) et SCRUM-136 (cascade co-organisateurs).
5. **Liens externes sur `/events/:id`** : `websiteUrl` et `contactEmail` rendus en `text-foreground hover:underline` — visuellement indistincts du texte normal, on ne voit pas qu'ils sont cliquables.
6. **Brouillon accessible via `/events/:id`** : le créateur peut afficher la page détail d'un event `DRAFT` (le backend l'autorise via la cascade SCRUM-136), mais l'UX attendue est de rediriger vers `/edit` puisque la page détail d'un brouillon n'a pas de sens fonctionnel pour un créateur (rien à signaler, pas de bouton « participer », pas d'inscrits…).

### Pourquoi grouper en une PR

Aucun de ces fix n'est assez gros pour mériter une PR individuelle, mais ensemble ils représentent un effort cohérent de **polish UX du frontend en fin de S7**. Tous touchent des fichiers indépendants (zéro chevauchement), donc la review est facile à découper item par item. Une PR `chore` unique évite la fragmentation du backlog Git pour des fix triviaux.

### Pas de ticket Jira

Ces frottements ont été notés au fur et à mesure des sessions sans création de ticket SCRUM dédié. Le commit + PR utiliseront le scope `chore(frontend)` (autorisé pour les types non-`feat`/`refactor`/`perf` cf. [`backend/AGENTS.md`](backend/AGENTS.md#L113-L116) — la même règle s'applique côté frontend via le workflow [`.github/workflows/pr-title-check.yml`](.github/workflows/pr-title-check.yml)).

### Pourquoi maintenant

- Sprint 7 fin de course → fenêtre idéale pour le polish avant la review du sprint.
- Ces fix débloquent la perception qualité côté utilisateurs internes qui testent l'app.
- Aucun fix ne dépend d'un backend non encore livré ; tout est frontend pur.

---

## Décisions techniques (tranchées — NE PAS revisiter pendant l'implémentation)

### 1. Branche `feature/s7-frontend-polish-fixes` — pas `chore/...`

**Décision.** Bien que le scope de PR soit `chore`, la branche reste préfixée `feature/` pour cohérence avec les autres branches du repo (`feature/s6-co-organizers`, `feature/s6-report-moderation`, `feature/s7-expiration-job`…).

```bash
git fetch origin
git checkout -b feature/s7-frontend-polish-fixes origin/main --no-track
```

⚠️ **`--no-track` est OBLIGATOIRE** (incident historique repris par toutes les specs). Sans ce flag, la branche traque `origin/main` et `git push` envoie les commits sur main.

Premier push :

```bash
git push -u origin feature/s7-frontend-polish-fixes
```

### 2. Titre de PR : `chore(frontend): polish navbar, skeletons, sticky FAB, link styling and draft redirect`

**Décision.** Titre couvre les 6 axes en mots-clés, sans être trop long :
- `navbar` (Fix 1)
- `skeletons` (Fix 2 + Fix 4)
- `sticky FAB` (Fix 3 — FAB = Floating Action Button)
- `link styling` (Fix 5)
- `draft redirect` (Fix 6)

Type `chore` car c'est un lot de polish qui ne livre pas une nouvelle feature et n'est pas un refactor. Pas de scope `scrum-XXX` puisqu'aucun ticket Jira correspondant.

### 3. Fix 1 — Pattern `Collapsible.Root` style « banner-card » à l'INTÉRIEUR du dropdown profil

**Décision.** L'item `Mes événements` du dropdown profil est transformé en un sous-bloc `Collapsible.Root` (Radix UI), visuellement stylé comme une carte (`rounded-2xl border border-border/40 bg-foreground/[0.02]`), avec :
- Header cliquable + chevron pivotant (animation 200ms)
- Contenu inline animé (les 3 sous-liens favoris / participations / publications)
- **Par défaut OUVERT** (`defaultOpen={true}`) — l'utilisateur vient d'ouvrir son profil, autant lui montrer ses raccourcis sans clic supplémentaire.
- L'utilisateur peut le fermer s'il veut alléger visuellement (état non persisté entre ouvertures du dropdown profil).

**Justification — pourquoi pas un autre layout ?**

| Option | Verdict |
|---|---|
| (a) Aplatir : afficher les 3 sous-liens directement à plat dans le dropdown profil (supprimer `Mes événements` parent) | ❌ Perte de structure visuelle ; les 3 raccourcis se mélangent à `Mon profil`/`Administration`/`Déconnexion` sans groupement clair |
| (b) Sortir `Mes événements` dans un dropdown séparé de la navbar | ❌ Trop d'espace pris en navbar pour un raccourci personnel ; et la navbar a déjà un dropdown principal `Événements` |
| (c) Carte standalone à l'intérieur du dropdown profil avec `Collapsible.Root` Radix | ✅ retenu — préserve la structure (3 raccourcis groupés sous un titre), pattern visuel déjà connu dans le projet (`DraftsResumeStrip`), pas de dropdown-in-dropdown |

**Conserver le mobile (`MobileNavItem` lignes 174-215 de [`Navbar.tsx`](frontend/src/components/Navbar.tsx))** tel quel — la sidebar mobile gère déjà le nesting correctement avec un layout en colonne, c'est la version desktop floating qui pose problème. Toucher au mobile = risque de régression sans bénéfice.

### 4. Fix 2 — `drafts-resume-strip.bones.json` : 3 breakpoints manuels (pas de migration `generate.mjs`)

**Décision.** Le skeleton reste en JSON manuel mais étendu à 3 container-width breakpoints : `320`, `720`, `1216` (alignés sur les autres skeletons du projet). Le layout est un header h-14 statique (icône gauche / texte / chevron droit) — pas de grille variable, donc `generate.mjs` serait du sur-poids.

**Justification.** Cf. arbre de décision [`frontend/skeleton/README.md`](frontend/skeleton/README.md#L55-L65) : *« Le layout utilise une grille CSS ? OUI → `generate.mjs`. NON → JSON manuel »*. Ici le layout est strictement positionné en `flex` avec dimensions fixes (icônes 16×16px, hauteur 56px) → manuel.

**Bones cibles à chaque breakpoint :**

```json
{
  "name": "drafts-resume-strip",
  "viewportWidth": 320,  // = container width
  "width": 320,
  "height": 56,
  "bones": [
    [0, 0, 100, 56, 16, true],     // carte conteneur (h-14 rounded-2xl)
    [pct(16), 20, pct(16), 16, 4, true],  // icône Library gauche (size-4 = 16px à 16px du bord)
    [pct(40), 21, pct(140), 14, 4],       // texte "Mes brouillons" (~140px de large)
    [pct(W-32), 20, pct(16), 16, 4, true] // chevron droit (size-4 à 16px du bord)
  ]
}
```

Où `pct(px) = round(px * 10000 / containerW) / 100` et `W` = container width.

Pour `320` : icône à 5%, texte à 12.5% (largeur 43.75%), chevron à 90%.
Pour `720` : icône à 2.22%, texte à 5.55% (largeur 19.44%), chevron à 95.56%.
Pour `1216` : icône à 1.32%, texte à 3.29% (largeur 11.51%), chevron à 97.37%.

**`isContainer` :** la carte extérieure et les icônes (16×16) sont des containers (couleur claire), le texte est un leaf (couleur sombre). Cohérent avec la règle [`README.md` ligne 84-99](frontend/skeleton/README.md#L84-L99).

**Vérification que la fixture matche** : la fixture `DraftsHeaderFixture` actuelle ([`DraftsResumeStrip.tsx:15-25`](frontend/src/components/event/DraftsResumeStrip.tsx#L15-L25)) doit rester telle quelle — elle a déjà la bonne structure HTML (h-14, rounded-2xl, flex justify-between, icône+texte gauche, icône droite). `bones.height = 56` matche bien `h-14 = 56px`.

### 5. Fix 3 — `position: sticky bottom-6 self-end` sur le bouton FAB de `MyPublicationsPage`

**Décision.** Remplacer le `position: fixed bottom-6 right-6 z-40` par un `position: sticky bottom-6 self-end z-40` placé en fin de `<SectionWrapper>`. Le bouton :
- Reste visible en bas-droite tant qu'on scrolle dans le contenu de la page (sticky behavior).
- Quand on atteint sa position naturelle (fin du contenu de la page), il s'arrête là — il ne déborde pas dans le footer.

**Code cible** ([`MyPublicationsPage.tsx:375-382`](frontend/src/pages/my-events/MyPublicationsPage.tsx#L375-L382)) :

```tsx
// AVANT
<Link
  to="/events/new"
  aria-label="Créer un événement"
  className="fixed bottom-6 right-6 z-40 inline-flex items-center gap-2 px-5 py-3 rounded-full bg-linear-to-r from-accent to-pink-600 text-white font-semibold shadow-xl shadow-accent/30 no-underline hover:from-accent/90 hover:to-pink-600/90 transition-colors"
>
  <Plus className="size-5" />
  Créer un événement
</Link>

// APRÈS
<Link
  to="/events/new"
  aria-label="Créer un événement"
  className="sticky bottom-6 self-end z-40 inline-flex items-center gap-2 px-5 py-3 rounded-full bg-linear-to-r from-accent to-pink-600 text-white font-semibold shadow-xl shadow-accent/30 no-underline hover:from-accent/90 hover:to-pink-600/90 transition-colors"
>
  <Plus className="size-5" />
  Créer un événement
</Link>
```

**Pré-requis CSS pour que `sticky` marche** : le parent ne doit pas avoir `overflow: hidden` sur l'axe vertical, et le bouton doit être un enfant direct (ou suffisamment proche) d'un container suffisamment grand pour que le sticky ait de la marge. Vérifier que `<SectionWrapper>` n'a pas `overflow-hidden` qui casserait le sticky — si oui, ajouter une classe `overflow-y-visible` ou repositionner le bouton.

**Justification — pourquoi pas IntersectionObserver.**

| Option | Verdict |
|---|---|
| (a) `position: sticky bottom-6 self-end` | ✅ retenu — 1 ligne Tailwind, pas de JS, comportement natif et accessible |
| (b) IntersectionObserver sur le `<Footer>` pour basculer `fixed` ↔ caché | ❌ Sur-ingénierie pour un cas simple ; ajoute du JS, des refs, du cleanup `useEffect` |
| (c) `position: absolute bottom-0` dans wrapper relative | ❌ Le bouton n'est plus visible pendant le scroll de la page (sauf à la toute fin) |

> **Note technique.** `sticky` ne fonctionne que si le parent direct n'a PAS `display: flex` avec un comportement contraire, et ne PAS avoir de `transform` qui crée un nouveau contexte de containing block. Si la sticky ne fonctionne pas après le changement, vérifier le DOM parent dans les devtools. Fallback : option (b) IntersectionObserver.

### 6. Fix 4 — Skeleton `event-edit` ET `event-detail` régénérés via `generate.mjs`

**Décision.** Les deux skeletons sont déjà générés par `frontend/skeleton/generate.mjs` (fonctions `genEventEdit()` et `genEventDetail()`). Ces fonctions sont à mettre à jour pour matcher les layouts actuels :

#### 6.a — `genEventEdit()` (lignes ~503-823 de generate.mjs)

À auditer/corriger contre l'état actuel d'[`EventForm.tsx`](frontend/src/components/event/EventForm.tsx) :

**Layout réel actuel d'`EventForm` (5 bandes séparées par `gap-8` = 32px) :**
- **Bande 1** : `grid-cols-[2fr_3fr] gap-6 max-lg:grid-cols-1`. Gauche = bannière (h-52 min, max-h-72, rounded-2xl), droite = Titre (FormField + counter) + Description (FormField textarea + counter).
- **Bande 2a** : Lieu (FormField avec icône MapPin gauche).
- **Bande 2b** : `<section>` "Date & heure" groupée (`flex flex-col gap-3 rounded-2xl border bg-foreground/[0.015] px-4 py-4`) — header `Date & heure` + toggle `Toute la journée`, puis `grid-cols-2 gap-4 max-sm:grid-cols-1` avec 2 datetime fields (Début / Fin).
- **Bande 3** : `flex flex-wrap items-end gap-x-6 gap-y-4` avec Catégorie (`w-48`), Faculté (`w-56`), Capacité (`w-24`).
- **Bande 4** : Champs additionnels — `grid-cols-2 max-sm:grid-cols-1` avec websiteUrl + contactEmail (icônes), puis registrationDeadline (datetime field), puis tags (TagInput), puis 2 ComingSoonBlock (Récurrence si mode=create, Pièces jointes toujours).
- **Bande 5** : Co-organisateurs (mode=edit only) — `border-t pt-6` avec header + mock champ recherche + 2 mock chips.
- **CTA** : `flex flex-wrap items-center gap-3 ml-auto` avec boutons (delete optionnel, cancel optionnel, draft optionnel, submit).

**Différences à corriger dans `genEventEdit()` :**
- La **Faculté** (`w-56`) a été ajoutée à la bande 3 entre Catégorie et Capacité — vérifier qu'elle est dans le bones JSON.
- La **bande 2b "Date & heure"** est désormais une section groupée avec son propre rounded-2xl et un toggle `allDay` — la version actuelle de `genEventEdit` la modélise probablement comme 3 fields séparés (Lieu/Début/Fin sur la même bande), à revoir.
- Les **datetime fields** ont maintenant un select horaires + select minutes inline avec une transition `max-w-0 opacity-0` quand `allDay` — pour le skeleton, représenter la version la plus visible (allDay=false, time selectors visibles).
- La fixture `EventFormFixture` ([`EventEditPage.tsx:16-60`](frontend/src/pages/event/EventEditPage.tsx#L16-L60)) doit être réécrite pour matcher exactement le nouveau layout.

#### 6.b — `genEventDetail()` (lignes ~457-501 de generate.mjs)

À auditer/corriger contre l'état actuel d'[`EventDetailPage.tsx`](frontend/src/pages/event/EventDetailPage.tsx) :

**Colonne main (3fr) :**
1. Banner (h-72 lg:h-80, rounded-3xl)
2. Card description (h-40 approx) — conditionnelle (présente si `event.description`)
3. AttendeesList — composant avec son propre layout (h variable)
4. **Card "Informations complémentaires" (SCRUM-117)** — conditionnelle (présente si au moins un champ parmi `websiteUrl`/`contactEmail`/`registrationDeadline`/`tags`). 4 InfoRow avec icône + texte. **PAS dans la fixture actuelle**, à ajouter.

**Colonne sidebar (2fr, sticky lg:top-6) :**
1. Card infos clés (date, location, capacity, organizer, capacityIndicator) — h ≈ 280-320px
2. Card AttendanceButtons (favoris/partager grid + boutons participation) — h ≈ 180px
3. IcsExportButton — h ≈ 60-80px
4. **Actions organisateur** — conditionnelle (visible seulement si `isOrganizer`). 2 boutons (Modifier + Annuler) ou 2 boutons (Restore + Supprimer si CANCELLED).
5. **Stats organisateur (ComingSoonBlock)** — conditionnelle (visible seulement si `isOrganizer`).

**Différences à corriger :**
- La **carte "Informations complémentaires"** SCRUM-117 doit être ajoutée à la main column.
- Les **actions organisateur** et **stats** sont conditionnelles — pour le skeleton qui s'affiche à `isInitialLoad` (avant qu'on connaisse le user/event), faire un choix : représenter la variante la plus large (avec actions org + stats) ou la variante public (sans). Recommandation : **représenter la variante public** par défaut (skeleton affiché aux anonymes ET aux organisateurs ; plus court visuellement que de stretcher la sidebar).
- La fixture `EventDetailFixture` ([`EventDetailPage.tsx:25-48`](frontend/src/pages/event/EventDetailPage.tsx#L25-L48)) doit être réécrite pour matcher.

**Workflow obligatoire :**

```bash
# Modifier skeleton/generate.mjs (genEventEdit + genEventDetail)
# Modifier EventEditPage.tsx (EventFormFixture) + EventDetailPage.tsx (EventDetailFixture)
cd frontend
npm run skeleton  # régénère les .bones.json
git diff src/bones/event-edit.bones.json src/bones/event-detail.bones.json
# Vérifier visuellement en dev mode (npm run dev), recharger les pages avec network throttling
```

### 7. Fix 5 — Nouvelle CSS variable `--color-link` + classe `text-link`

**Décision.** Le projet n'a pas de variable dédiée pour les liens (cf. [`frontend/src/index.css`](frontend/src/index.css) qui définit `--color-primary`, `--color-accent`, `--color-foreground`, `--color-error`, `--color-warning`, `--color-success`, mais pas `link`). On en ajoute une.

**Tokens cibles :**

```css
/* Light mode */
:root {
  /* …existants… */
  --color-link: rgb(2, 132, 199);  /* sky-600 — bleu lisible sur fond clair */
}

/* Dark mode */
.dark {
  /* …existants… */
  --color-link: rgb(56, 189, 248); /* sky-400 — bleu plus clair sur fond sombre */
}
```

Tailwind v4 expose automatiquement `text-link`, `bg-link`, `border-link` à partir d'une variable `--color-link` placée dans `@theme`.

**Application dans EventDetailPage** ([`EventDetailPage.tsx:412-420`](frontend/src/pages/event/EventDetailPage.tsx#L412-L420) et lignes 430-435) :

```tsx
// AVANT (websiteUrl)
<a
  href={safeHref}
  target="_blank"
  rel="noopener noreferrer"
  className="text-foreground hover:underline break-all"
>
  {event.websiteUrl}
</a>

// APRÈS
<a
  href={safeHref}
  target="_blank"
  rel="noopener noreferrer"
  className="text-link hover:underline break-all"
>
  {event.websiteUrl}
</a>
```

Idem pour le mailto contactEmail à la ligne 430-435 (cohérence).

**Justification — pourquoi pas `text-blue-400` ou `text-accent` ?**

| Option | Verdict |
|---|---|
| (a) `text-blue-400` (couleur Tailwind brute) | ❌ Interdit par [`AGENTS.md`](frontend/AGENTS.md#L309) : *« ne jamais utiliser `red-400`, `red-500` ou autre valeur brute »* |
| (b) `text-accent` (rose/pink #d80669) | ❌ L'utilisateur a explicitement demandé du **bleu** |
| (c) `text-link` (nouveau token CSS variable) | ✅ retenu — convention du projet, scalable, dark/light aware |

**Tags `<Link>` du même bloc "Informations complémentaires"** ([`EventDetailPage.tsx:451-460`](frontend/src/pages/event/EventDetailPage.tsx#L451-L460)) : ce sont des chips, pas des liens textuels. **Ne PAS** appliquer `text-link` — leur style actuel (`bg-foreground/5 border border-border/30 text-foreground/70`) est volontaire et cohérent avec les autres chips de l'app. Garder.

**Lien organisateur dans la sidebar** ([`EventDetailPage.tsx:499-510`](frontend/src/pages/event/EventDetailPage.tsx#L499-L510)) : `<Link to={profile/...}>` qui contient un avatar + nom. Pas un lien textuel pur — c'est une zone cliquable carte-style. **Ne PAS** appliquer `text-link` non plus.

### 8. Fix 6 — Redirect `/events/:id` → `/events/:id/edit` si DRAFT et user = créateur

**Décision.** Dans `EventDetailPage`, après le chargement de l'event ET du user, si `event.status === 'DRAFT'` ET que l'utilisateur courant est créateur (`user?.id === event.creatorId`), `useNavigate` vers `/events/:id/edit` avec `{ replace: true }` (pour ne pas casser le bouton back du navigateur).

**Co-organisateur ACCEPTED — exclu du scope de cette PR :**

```bash
$ grep -rln "useCoOrganizers\|getCoOrganizers\|coOrganizers" frontend/src/
# (zéro résultat)
```

Le frontend n'a aucun hook ni service pour récupérer les co-organisateurs d'un event — la PR SCRUM-137 (frontend co-org) n'est pas encore livrée. **La spec exclut donc le co-organisateur ACCEPTED du redirect**. Quand SCRUM-137 sera livré, un follow-up trivial étendra la condition à `user.id === event.creatorId || coOrganizers.some(c => c.userId === user.id && c.status === 'ACCEPTED')`.

**Admin — explicitement EXCLU du redirect :**

L'admin doit pouvoir **voir** un DRAFT pour modérer (la cascade SCRUM-94 backend lui en laisse le droit via le rôle Auth0). Forcer un redirect vers `/edit` casserait son cas d'usage. Détection via `user.admin` (champ Auth0 claim côté front, type [`User`](frontend/src/types/user.ts) du frontend).

**Code cible — emplacement** ([`EventDetailPage.tsx:223-291`](frontend/src/pages/event/EventDetailPage.tsx#L223-L291)) :

Ajouter un `useEffect` après le useEvent et avant le rendu :

```tsx
useEffect(() => {
  if (!event || !user) return
  if (event.status !== 'DRAFT') return
  if (user.admin) return                          // admin: stays on detail
  if (user.id !== event.creatorId) return         // not the creator: stays on detail
  // Future: also check coOrganizers ACCEPTED via SCRUM-137 hook when available
  navigate(`/events/${event.id}/edit`, { replace: true })
}, [event, user, navigate])
```

**Edge cases :**
- **Pendant `isInitialLoad`** : `event === null`, le useEffect ne fait rien.
- **Pendant `isRefetching`** : `event` est l'ancien (déjà vu), pas de redirect surprise (statut déjà connu).
- **User déconnecté** : impossible — le backend renvoie 404 sur les DRAFT pour anon (cf. [`backend/docs/data-model.md` règle de visibilité par statut](backend/docs/data-model.md#L76-L86)).
- **Event PUBLISHED ou CANCELLED** : pas de redirect, comportement normal.

**Justification — pourquoi pas une garde dans le router (`<Route loader={...}>`) ?**

Le router actuel du projet (`react-router-dom` v6/v7 selon le package.json — à vérifier) utilise la pattern composants + `useEffect` plutôt que loaders, et ajouter un loader juste pour ce cas casserait le pattern. Le `useEffect` dans la page est moins « élégant » mais cohérent avec le reste du codebase.

### 9. Tests — minimum manuel + automatisé sur Fix 6

**Décision.** Vérifications manuelles documentées dans le test plan de la PR pour les 6 fix. Tests automatisés ajoutés uniquement là où ils sont triviaux et à haute valeur :

| Fix | Test automatisé | Justification |
|---|---|---|
| 1 (navbar) | ❌ Non | Visuel pur, hard à test sans snapshots — manuel suffit |
| 2 (skeleton drafts) | ❌ Non | Idem visuel ; le `name` du skeleton est testé implicitement par la résolution registry au runtime |
| 3 (FAB sticky) | ❌ Non | Comportement de scroll = manuel, pas reproductible en jsdom/happy-dom |
| 4 (skeletons event-edit/event-detail) | ❌ Non | Idem |
| 5 (lien bleu) | ❌ Non | Couleur visuelle — pas testable sans snapshot, manuel suffit |
| 6 (redirect DRAFT) | ✅ Oui | Logic conditionnelle, isolable, à haute valeur (régression facile à introduire) |

**Test automatisé Fix 6** : un test `EventDetailPage.test.tsx` qui :
1. Mock `useEvent` retournant un event DRAFT avec `creatorId = 'user-A'`.
2. Mock `useAuth` retournant `user = { id: 'user-A', admin: false, ... }`.
3. Render `<EventDetailPage>` avec `MemoryRouter initialEntries={['/events/42']}`.
4. Assert que `useNavigate` a été appelé avec `'/events/42/edit'` et `{ replace: true }`.

Plus 3 tests négatifs : (a) admin DRAFT créateur → pas de redirect, (b) non-créateur DRAFT → pas de redirect (ni admin), (c) PUBLISHED créateur → pas de redirect.

### 10. Doc à mettre à jour

**Décision.** 3 fichiers de doc à toucher dans le même commit :

- **`frontend/docs/components.md`** — sections `EventDetailPage`, `EventEditPage`, `MyPublicationsPage`, `Navbar` (mention du nouveau pattern banner-card pour `Mes événements`), `DraftsResumeStrip` (skeleton mis à jour).
- **`frontend/docs/sprint-context.md`** — entrée polish S7 dans la section Sprint 7.
- **`frontend/AGENTS.md`** — section "Design tokens CSS" (ligne 296-309) → ajouter la ligne `--color-link` au tableau.

Pas de modif d'`openapi/openapi.yaml` (pas de changement API). Pas de modif de `frontend/docs/types.md` (pas de nouveaux types). Pas de modif de `frontend/docs/architecture.md` (pas de nouvelle route).

---

## Analyse de l'existant

### Ce qui existe (à réutiliser)

| Élément | Fichier / ligne | Rôle |
|---|---|---|
| Pattern `Collapsible.Root` + `Collapsible.Trigger` + `Collapsible.Content` (Radix UI) | [`DraftsResumeStrip.tsx:82-138`](frontend/src/components/event/DraftsResumeStrip.tsx#L82-L138) | Modèle direct pour Fix 1 |
| Animation chevron pivotant `group-data-[state=open]:rotate-180` | [`DraftsResumeStrip.tsx:96-99`](frontend/src/components/event/DraftsResumeStrip.tsx#L96-L99) | Pattern de chevron animé |
| Pattern `nav config en const arrays` (`navLinks`, `myEventsSubLinks`, `userMenuItems`) | [`Navbar.tsx:20-45`](frontend/src/components/Navbar.tsx#L20-L45) | Réutiliser tel quel pour Fix 1 |
| Composant existant `UserDropdownItem` (gère le nested expand actuellement) | [`Navbar.tsx:64-103`](frontend/src/components/Navbar.tsx#L64-L103) | À retravailler — extraire la logique du sub-banner ou créer un nouveau composant `UserDropdownBanner` |
| Pattern `<Skeleton name="..." loading animate="pulse" color={skeletonColor}>` | [`EventDetailPage.tsx:281-286`](frontend/src/pages/event/EventDetailPage.tsx#L281-L286) | Modèle d'intégration skeleton |
| Helper `pct(px) = round(px * 10000 / containerW) / 100` | [`skeleton/generate.mjs:39-40`](frontend/skeleton/generate.mjs#L39-L40) | Pour Fix 2 (manuel) et Fix 4 (generate.mjs) |
| Fonction `genEventEdit()` | [`skeleton/generate.mjs:811-823`](frontend/skeleton/generate.mjs#L811-L823) | À MAJ pour Fix 4 — bandes/positions correctes |
| Fonction `genEventDetail()` | [`skeleton/generate.mjs:489-501`](frontend/skeleton/generate.mjs#L489-L501) | À MAJ pour Fix 4 — ajout SCRUM-117 + actions organisateur |
| Pattern CSS variable token + `@theme` Tailwind v4 | [`frontend/src/index.css`](frontend/src/index.css) | Pour Fix 5 — ajouter `--color-link` |
| `useEvent`, `useAuth`, `useNavigate` | [`hooks/useEvent.ts`](frontend/src/hooks/useEvent.ts), [`hooks/useAuth.ts`](frontend/src/hooks/useAuth.ts), `react-router-dom` | Pour Fix 6 |
| Type `User` avec `admin: boolean` | [`frontend/src/types/user.ts`](frontend/src/types/user.ts) | Pour Fix 6 — exclusion admin |
| Pattern `position: sticky` | non encore utilisé dans le repo, mais Tailwind expose `sticky` natif | Pour Fix 3 |

### Ce qui est à modifier

| Fichier | Modification |
|---|---|
| [`frontend/src/components/Navbar.tsx`](frontend/src/components/Navbar.tsx) | Fix 1 — Refacto `UserDropdownItem` : si l'item est `Mes événements` (a `subLinks`), rendre un nouveau composant local `UserDropdownBanner` avec `Collapsible.Root` Radix + style banner-card. Sinon comportement actuel pour les autres items. |
| [`frontend/src/bones/drafts-resume-strip.bones.json`](frontend/src/bones/drafts-resume-strip.bones.json) | Fix 2 — Étendre à 3 breakpoints (320, 720, 1216). |
| [`frontend/src/pages/my-events/MyPublicationsPage.tsx`](frontend/src/pages/my-events/MyPublicationsPage.tsx) | Fix 3 — Remplacer `fixed bottom-6 right-6` par `sticky bottom-6 self-end` sur le bouton FAB. |
| [`frontend/skeleton/generate.mjs`](frontend/skeleton/generate.mjs) | Fix 4 — Refacto `genEventEdit()` (bandes mises à jour) ET `genEventDetail()` (ajout carte SCRUM-117 + actions organisateur). |
| [`frontend/src/pages/event/EventEditPage.tsx`](frontend/src/pages/event/EventEditPage.tsx) | Fix 4 — Réécrire `EventFormFixture` (lignes 16-60) pour matcher le nouveau layout. |
| [`frontend/src/pages/event/EventDetailPage.tsx`](frontend/src/pages/event/EventDetailPage.tsx) | Fix 4 + Fix 5 + Fix 6 : (a) réécrire `EventDetailFixture` (lignes 25-48) avec carte SCRUM-117 et actions org ; (b) appliquer `text-link` aux 2 `<a>` websiteUrl/contactEmail (lignes 412-420 et 430-435) ; (c) ajouter `useEffect` redirect DRAFT (après ligne 268). |
| [`frontend/src/index.css`](frontend/src/index.css) | Fix 5 — Ajouter `--color-link` pour light et dark mode. |
| [`frontend/src/bones/event-edit.bones.json`](frontend/src/bones/event-edit.bones.json) | Fix 4 — Régénéré par `npm run skeleton` après modification de `genEventEdit()`. |
| [`frontend/src/bones/event-detail.bones.json`](frontend/src/bones/event-detail.bones.json) | Fix 4 — Idem via `genEventDetail()`. |
| [`frontend/AGENTS.md`](frontend/AGENTS.md) | Doc — Ajouter la ligne `--color-link` au tableau "Design tokens CSS" (ligne ~296). |
| [`frontend/docs/components.md`](frontend/docs/components.md) | Doc — Mention du pattern banner-card pour Mes événements (Navbar), skeleton drafts-resume-strip mis à jour, fix sticky FAB MyPublications, redirect DRAFT EventDetail. |
| [`frontend/docs/sprint-context.md`](frontend/docs/sprint-context.md) | Doc — Entrée "Polish UX S7" en fin de section Sprint 7. |

### Ce qui est à créer

| Fichier | Rôle |
|---|---|
| `frontend/src/__tests__/EventDetailPage.test.tsx` (s'il n'existe pas déjà — sinon ajouter au fichier existant) | Test redirect DRAFT pour Fix 6 (4 tests : créateur DRAFT redirige, créateur PUBLISHED ne redirige pas, admin DRAFT ne redirige pas, non-créateur DRAFT ne redirige pas). |

### Ce qui n'est PAS dans le scope

- ❌ Pas de modif backend (aucun changement d'API, aucun nouveau endpoint).
- ❌ Pas de modif d'`openapi/openapi.yaml`.
- ❌ Pas d'ajout de `useCoOrganizers` ni de wiring frontend co-org (SCRUM-137 séparé). Le redirect Fix 6 est limité au créateur ; co-org ACCEPTED arrivera en follow-up.
- ❌ Pas de modif de la sidebar mobile (`MobileNavItem`) — fonctionne bien telle quelle.
- ❌ Pas de migration `drafts-resume-strip.bones.json` vers `generate.mjs` — manuel reste pertinent (cf. décision 4).
- ❌ Pas d'ajout d'IntersectionObserver pour le FAB (cf. décision 5).
- ❌ Pas de refacto de la classe-utilitaire `text-foreground` (juste ajout de `text-link`).
- ❌ Pas d'override de couleur sur les chips tags (cf. décision 7).
- ❌ Pas de loader/router-guard pour Fix 6 (`useEffect` en page reste cohérent avec le pattern projet).

---

## Étape 0 — Préparation et lecture obligatoire

Avant toute ligne de code :

1. Lire **EN ENTIER** [`frontend/AGENTS.md`](frontend/AGENTS.md) — conventions du projet (camelCase, pas de `is`, design tokens, structure pages/composants, imports `@/`, gestion erreurs, skeleton workflow non-négociable).
2. Lire **EN ENTIER** [`frontend/skeleton/README.md`](frontend/skeleton/README.md) — workflow skeleton, format des bones, container-width breakpoints, pièges, checklist.
3. Lire les sections pertinentes de :
   - [`frontend/docs/components.md`](frontend/docs/components.md) — pages affectées (`EventDetailPage`, `EventEditPage`, `MyPublicationsPage`, `Navbar`, `DraftsResumeStrip`).
   - [`frontend/docs/architecture.md`](frontend/docs/architecture.md) — table de routage.
4. Inspecter les fichiers à modifier listés dans la table « Ce qui est à modifier » ci-dessus pour comprendre l'état courant.

**Aucune ligne de Java/TS ne doit être écrite avant cette étape.**

---

## Étape 1 — Fix 1 : Navbar `Mes événements` en banner-card

### 1.1 — Refacto de `UserDropdownItem`

**Fichier :** [`frontend/src/components/Navbar.tsx`](frontend/src/components/Navbar.tsx)

**Changement :** ajouter un nouveau composant local `UserDropdownBanner` qui rend un `Collapsible.Root` Radix avec style banner-card. Modifier `UserDropdownItem` pour déléguer à `UserDropdownBanner` quand l'item a `subLinks`.

```tsx
import * as Collapsible from '@radix-ui/react-collapsible'

// Nouveau composant — version "banner-card" pour les items avec subLinks dans le dropdown profil.
function UserDropdownBanner({ item }: Readonly<{ item: NavItem }>) {
  const Icon = item.icon
  const subLinks = item.subLinks!  // garanti par le call-site

  return (
    <Collapsible.Root
      defaultOpen
      className="mx-2 my-1 rounded-2xl border border-border/40 bg-foreground/[0.02] overflow-hidden"
    >
      <Collapsible.Trigger
        className="group flex w-full items-center justify-between gap-3 px-3 py-2.5 text-sm text-foreground/70 hover:text-foreground hover:bg-foreground/5 transition-colors cursor-pointer"
      >
        <span className="flex items-center gap-3">
          <Icon className="size-4 shrink-0" />
          {item.label}
        </span>
        <ChevronDown
          className="size-4 text-foreground/50 transition-transform duration-200 group-data-[state=open]:rotate-180 motion-reduce:transition-none"
          aria-hidden
        />
      </Collapsible.Trigger>
      <Collapsible.Content
        className="overflow-hidden border-t border-border/40 motion-safe:data-[state=open]:animate-collapsible-open motion-safe:data-[state=closed]:animate-collapsible-close"
      >
        {subLinks.map(({ to, icon: SubIcon, label }) => (
          <Link
            key={to}
            to={to}
            className="flex items-center gap-3 pl-9 pr-3 py-2 text-sm text-foreground/60 hover:text-foreground hover:bg-foreground/5 transition-colors"
          >
            <SubIcon className="size-4 shrink-0" />
            {label}
          </Link>
        ))}
      </Collapsible.Content>
    </Collapsible.Root>
  )
}

// Modifier UserDropdownItem pour déléguer au banner si subLinks
function UserDropdownItem({ item }: Readonly<{ item: NavItem }>) {
  const Icon = item.icon
  if (item.subLinks) {
    return <UserDropdownBanner item={item} />
  }
  return (
    <Link to={item.to} className={dropdownItemClass}>
      <Icon className="size-4 shrink-0" />
      {item.label}
    </Link>
  )
}
```

**Animations Tailwind à ajouter** (dans `frontend/src/index.css` ou via `@keyframes`) — si elles n'existent pas déjà :

```css
@theme {
  --animate-collapsible-open: collapsible-open 200ms ease-out;
  --animate-collapsible-close: collapsible-close 200ms ease-out;
}

@keyframes collapsible-open {
  from { height: 0; }
  to { height: var(--radix-collapsible-content-height); }
}
@keyframes collapsible-close {
  from { height: var(--radix-collapsible-content-height); }
  to { height: 0; }
}
```

> **Note importante.** Vérifier que `@radix-ui/react-collapsible` est déjà dans `package.json` (`DraftsResumeStrip` l'utilise déjà → c'est le cas). Sinon `npm i @radix-ui/react-collapsible`.

### 1.2 — Vérifier que `MobileNavItem` n'est pas affecté

`MobileNavItem` ([`Navbar.tsx:174-215`](frontend/src/components/Navbar.tsx#L174-L215)) lit la même `NavItem` config et gère son propre expand/collapse pour le sidebar mobile. **Ne pas toucher.** Le DRY est respecté : `myEventsSubLinks` et `userMenuItems` restent les const arrays partagées entre desktop et mobile.

---

## Étape 2 — Fix 2 : Skeleton `drafts-resume-strip.bones.json` étendu

**Fichier :** [`frontend/src/bones/drafts-resume-strip.bones.json`](frontend/src/bones/drafts-resume-strip.bones.json)

**Remplacement complet :**

```json
{
  "breakpoints": {
    "320": {
      "name": "drafts-resume-strip",
      "viewportWidth": 320,
      "width": 320,
      "height": 56,
      "bones": [
        [0, 0, 100, 56, 16, true],
        [5, 20, 5, 16, 4, true],
        [12.5, 21, 43.75, 14, 4],
        [90, 20, 5, 16, 4, true]
      ]
    },
    "720": {
      "name": "drafts-resume-strip",
      "viewportWidth": 720,
      "width": 720,
      "height": 56,
      "bones": [
        [0, 0, 100, 56, 16, true],
        [2.22, 20, 2.22, 16, 4, true],
        [5.55, 21, 19.44, 14, 4],
        [95.56, 20, 2.22, 16, 4, true]
      ]
    },
    "1216": {
      "name": "drafts-resume-strip",
      "viewportWidth": 1216,
      "width": 1216,
      "height": 56,
      "bones": [
        [0, 0, 100, 56, 16, true],
        [1.32, 20, 1.32, 16, 4, true],
        [3.29, 21, 11.51, 14, 4],
        [97.37, 20, 1.32, 16, 4, true]
      ]
    }
  }
}
```

**Pas de `_hash`** ajouté manuellement — il est posé par `writeBones()` dans `generate.mjs`. Pour un fichier manuel, **ne pas mettre `_hash`** (sinon Boneyard refuse) ou utiliser une valeur stub. Vérifier en lisant le format de la précédente version JSON manuelle.

> **Note technique.** Si Boneyard refuse les bones sans `_hash`, calculer un hash SHA-1 sur le contenu (cf. `writeBones` dans `generate.mjs`) ou simplement laisser le champ absent — Boneyard tolère les fichiers manuels sans hash dans la majorité des cas.

**Vérification :** la fixture `DraftsHeaderFixture` ([`DraftsResumeStrip.tsx:15-25`](frontend/src/components/event/DraftsResumeStrip.tsx#L15-L25)) reste inchangée — sa structure HTML matche déjà.

**Test runtime :** ouvrir `/events/new` dans le navigateur (`npm run dev`), throttle 3G dans devtools, recharger, vérifier que le skeleton « Mes brouillons » s'affiche correctement à mobile (~320px), tablet (~720px), desktop (~1216px). Avec ResizeObserver, boneyard prend automatiquement le bon BP.

---

## Étape 3 — Fix 3 : Bouton FAB `MyPublicationsPage` en sticky

**Fichier :** [`frontend/src/pages/my-events/MyPublicationsPage.tsx`](frontend/src/pages/my-events/MyPublicationsPage.tsx)

**Changement** ([lignes 375-382](frontend/src/pages/my-events/MyPublicationsPage.tsx#L375-L382)) :

```tsx
// AVANT
<Link
  to="/events/new"
  aria-label="Créer un événement"
  className="fixed bottom-6 right-6 z-40 inline-flex items-center gap-2 px-5 py-3 rounded-full bg-linear-to-r from-accent to-pink-600 text-white font-semibold shadow-xl shadow-accent/30 no-underline hover:from-accent/90 hover:to-pink-600/90 transition-colors"
>
  <Plus className="size-5" />
  Créer un événement
</Link>

// APRÈS
<Link
  to="/events/new"
  aria-label="Créer un événement"
  className="sticky bottom-6 self-end z-40 inline-flex items-center gap-2 px-5 py-3 rounded-full bg-linear-to-r from-accent to-pink-600 text-white font-semibold shadow-xl shadow-accent/30 no-underline hover:from-accent/90 hover:to-pink-600/90 transition-colors"
>
  <Plus className="size-5" />
  Créer un événement
</Link>
```

**Diff = 1 mot** (`fixed bottom-6 right-6` → `sticky bottom-6 self-end`). Tester en local : scroller la page, vérifier que le bouton stick au bas du viewport, puis en arrivant en fin de contenu (juste avant le footer) il s'arrête. **Si le sticky ne fonctionne pas** (parce que `<SectionWrapper>` a `overflow-y: hidden` ou un `transform`), inspecter le DOM, retirer le offending property, ou fallback IntersectionObserver (cf. décision 5 option (b)).

> **Note layout.** `<SectionWrapper>` rend probablement un `<section>` avec `flex flex-col` ou `block` — le `self-end` sur le bouton lui dit de s'aligner à droite si parent est flex. Vérifier la structure de `<SectionWrapper>` dans [`frontend/src/components/utils/Section.tsx`](frontend/src/components/utils/Section.tsx) (probablement à inspecter avant de coder).

---

## Étape 4 — Fix 4 : Skeletons `event-edit` + `event-detail`

### 4.1 — Auditer le layout actuel d'`EventForm`

Lire intégralement [`frontend/src/components/event/EventForm.tsx`](frontend/src/components/event/EventForm.tsx). Cartographier les 5 bandes :

- **Bande 1** : `<div className="grid grid-cols-[2fr_3fr] gap-6 max-lg:grid-cols-1">`
  - Gauche : zone bannière avec image preview (h-52 min, max-h-72) ou placeholder upload
  - Droite : Titre (FormField + counter) + Description (FormField textarea + counter)
- **Bande 2a** : Lieu (FormField avec icône MapPin)
- **Bande 2b** : `<section className="flex flex-col gap-3 rounded-2xl border border-border/50 bg-foreground/[0.015] px-4 py-4">` — header "Date & heure" + toggle allDay, puis grid 2 fields (Début/Fin avec heures-minutes)
- **Bande 3** : `<div className="flex flex-wrap items-end gap-x-6 gap-y-4">` avec Catégorie (w-48), Faculté (w-56), Capacité (w-24)
- **Bande 4** : Champs additionnels — websiteUrl + contactEmail (grid 2 cols), registrationDeadline, tags, **séparateur**, ComingSoonBlock Récurrence (mode=create only), ComingSoonBlock Pièces jointes
- **Bande 5** : Co-organisateurs (mode=edit only) — header + champ recherche mock + 2 chips mock
- **CTA bar** : delete (optionnel) + cancel (optionnel) + draft (optionnel) + submit, alignés à droite

### 4.2 — Réécrire `genEventEdit()` dans `generate.mjs`

**Fichier :** [`frontend/skeleton/generate.mjs`](frontend/skeleton/generate.mjs#L811)

Approche : laisser la structure des 3 states A/B/C (320/592/960) mais s'assurer que :
- Bande 2 modélise la `<section>` "Date & heure" groupée avec son fond rounded-2xl + le toggle allDay (un container + 2 datetime fields à l'intérieur).
- Bande 3 inclut les 3 champs (Catégorie + Faculté + Capacité), pas seulement 2.
- Les datetime fields ont un select horaires + minutes inline (3 sub-éléments par field au lieu d'1).
- Bande 4 inclut tous les sous-éléments : websiteUrl, contactEmail, registrationDeadline (avec heures), tags (avec counter), séparateur, ComingSoon Récurrence (create only — à gérer en flag), ComingSoon Pièces jointes.
- Bande 5 (co-org, edit only — à gérer en flag) : header + champ recherche + 2 chips.

**Tester** : après modification, lancer `npm run skeleton` depuis `frontend/`, ouvrir le diff de `event-edit.bones.json`, lancer `npm run dev`, throttle network, recharger `/events/:id/edit`, vérifier visuellement que le skeleton matche le rendu réel à 3 viewports (mobile, tablet, desktop).

### 4.3 — Réécrire `EventFormFixture` dans `EventEditPage.tsx`

**Fichier :** [`frontend/src/pages/event/EventEditPage.tsx`](frontend/src/pages/event/EventEditPage.tsx#L16-L60)

Réécrire la fixture pour matcher EXACTEMENT les classes CSS de `<EventForm>` rendu en mode edit, avec dimensions vides (les bones rempliront les blocs) :

```tsx
function EventFormFixture() {
  return (
    <div className="flex flex-col gap-8">
      {/* Bande 1: Banner | Title + Description */}
      <div className="grid grid-cols-[2fr_3fr] gap-6 max-lg:grid-cols-1">
        <div className="pt-7 max-lg:pt-0">
          <div className="h-52 rounded-2xl" />
        </div>
        <div className="flex flex-col gap-4">
          <div className="h-[92px] rounded-xl" />
          <div className="h-[192px] rounded-xl" />
        </div>
      </div>
      {/* Bande 2a: Lieu */}
      <div className="h-[72px]" />
      {/* Bande 2b: Date & heure groupée */}
      <div className="rounded-2xl border border-border/50 bg-foreground/[0.015] px-4 py-4 flex flex-col gap-3">
        <div className="flex items-center justify-between">
          <div className="h-4 w-32" />
          <div className="h-5.5 w-24" />
        </div>
        <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1">
          <div className="h-[72px]" />
          <div className="h-[72px]" />
        </div>
      </div>
      {/* Bande 3: Catégorie | Faculté | Capacité */}
      <div className="flex flex-wrap items-end gap-x-6 gap-y-4">
        <div className="w-48 h-[72px]" />
        <div className="w-56 h-[72px]" />
        <div className="w-24 h-[72px]" />
      </div>
      {/* Bande 4: Champs additionnels */}
      <div className="flex flex-col gap-4">
        <div className="grid grid-cols-2 gap-4 max-sm:grid-cols-1">
          <div className="h-[72px] rounded-xl" />
          <div className="h-[72px] rounded-xl" />
        </div>
        <div className="h-[72px]" />
        <div className="h-[88px] rounded-xl" />
        <div className="border-t border-border/20" />
        <div className="h-[92px] rounded-2xl" />
      </div>
      {/* Bande 5: Co-organisateurs (edit only) */}
      <div className="flex flex-col gap-3 border-t border-border/30 pt-6">
        <div className="h-5" />
        <div className="h-10 max-w-sm rounded-xl" />
        <div className="h-8" />
      </div>
      {/* CTA bar */}
      <div className="flex flex-wrap items-center gap-3 ml-auto max-sm:ml-0 max-sm:w-full">
        <div className="w-24 h-11 rounded-xl" />
        <div className="w-24 h-11 rounded-xl" />
        <div className="w-32 h-11 rounded-xl" />
      </div>
    </div>
  )
}
```

> **Calcul de `bones.height`** : ce fixture en flex-col avec `gap-8 = 32px` entre 6 enfants → la hauteur totale dépend des viewports. À 960 (desktop) : Bande 1 = 300, gap, Bande 2a = 72, gap, Bande 2b ≈ 156, gap, Bande 3 = 72, gap, Bande 4 ≈ 412, gap, Bande 5 ≈ 88, gap, CTA = 44 → ≈ 1144 + 5*32 = 1304. Ajuster en fonction des dimensions réelles via les helpers déjà présents dans `genEventEdit()`. **Vérifier au runtime** que `bones.height` matche la hauteur réelle du fixture (cf. [`README.md` règle du height](frontend/skeleton/README.md#L147-L160)).

### 4.4 — Réécrire `genEventDetail()` dans `generate.mjs`

**Fichier :** [`frontend/skeleton/generate.mjs`](frontend/skeleton/generate.mjs#L457-L501)

Modifications dans `pushMainCol()` ou équivalent :
- Ajouter une carte "Informations complémentaires" (h ≈ 220-260px, rounded-3xl) après le bloc AttendeesList.

Dans `pushSidebarCol()` :
- Garder card infos clés, card AttendanceButtons, IcsExportButton.
- **Décider** : ajouter ou non le bloc "actions organisateur" (h ≈ 90-110px) ? Recommandation : ne pas l'ajouter au skeleton de base — c'est conditionnel à `isOrganizer`, et le skeleton est rendu avant qu'on connaisse cette info. Variante simple = variante public.
- Idem pour le bloc Stats organisateur (ComingSoonBlock).

### 4.5 — Réécrire `EventDetailFixture` dans `EventDetailPage.tsx`

**Fichier :** [`frontend/src/pages/event/EventDetailPage.tsx`](frontend/src/pages/event/EventDetailPage.tsx#L25-L48)

```tsx
function EventDetailFixture() {
  return (
    <div className="grid grid-cols-[3fr_2fr] gap-6 items-start max-lg:grid-cols-1">
      {/* Main column (order-2 on mobile) */}
      <div className="flex flex-col gap-5 max-lg:order-2">
        <div className="h-72 lg:h-80 rounded-3xl" />
        <div className="h-40 rounded-3xl" />
        <div className="flex flex-col gap-3">
          <div className="h-20 rounded-2xl" />
          <div className="h-20 rounded-2xl" />
          <div className="h-20 rounded-2xl" />
          <div className="h-20 rounded-2xl" />
        </div>
        {/* Card "Informations complémentaires" SCRUM-117 */}
        <div className="h-44 rounded-3xl" />
      </div>
      {/* Sidebar column (order-1 on mobile) */}
      <div className="flex flex-col gap-4 max-lg:order-1">
        <div className="h-72 rounded-3xl" />
        <div className="h-44 rounded-3xl" />
        <div className="h-[104px] rounded-2xl" />
      </div>
    </div>
  )
}
```

### 4.6 — Régénérer les bones et commit

```bash
cd frontend
npm run skeleton  # régénère event-edit.bones.json + event-detail.bones.json
git diff src/bones/event-edit.bones.json src/bones/event-detail.bones.json
```

Visualiser dans le navigateur (3 viewports : 320, 720, 1216) :
- `/events/new` → skeleton drafts strip (Fix 2)
- `/events/:id/edit` → skeleton event-edit (Fix 4a)
- `/events/:id` (event PUBLISHED) → skeleton event-detail (Fix 4b)

---

## Étape 5 — Fix 5 : Lien websiteUrl/contactEmail en bleu

### 5.1 — Ajouter `--color-link` dans `index.css`

**Fichier :** [`frontend/src/index.css`](frontend/src/index.css)

Ajouter dans le bloc `@theme` (ou son équivalent dans Tailwind v4) :

```css
@theme {
  /* …existants… */
  --color-link: rgb(2, 132, 199);  /* sky-600 — light mode default */
}

.dark {
  /* …existants… */
  --color-link: rgb(56, 189, 248); /* sky-400 — dark mode override */
}
```

**Vérifier** que `text-link` est bien généré par Tailwind après ajout (cf. doc Tailwind v4 sur les CSS variables auto-mapped).

### 5.2 — Appliquer `text-link` aux deux liens

**Fichier :** [`frontend/src/pages/event/EventDetailPage.tsx`](frontend/src/pages/event/EventDetailPage.tsx#L412-L437)

```tsx
// Ligne 412-420 (websiteUrl)
<a
  href={safeHref}
  target="_blank"
  rel="noopener noreferrer"
  className="text-link hover:underline break-all"
>
  {event.websiteUrl}
</a>

// Ligne 430-435 (contactEmail)
<a
  href={`mailto:${event.contactEmail}`}
  className="text-link hover:underline break-all"
>
  {event.contactEmail}
</a>
```

Le fallback non-cliquable (cas où `safeExternalHref` retourne `null`) reste en `text-foreground/70` — pas de lien donc pas de bleu :

```tsx
<span className="text-foreground/70 break-all">{event.websiteUrl}</span>
```

---

## Étape 6 — Fix 6 : Redirect DRAFT → /edit

**Fichier :** [`frontend/src/pages/event/EventDetailPage.tsx`](frontend/src/pages/event/EventDetailPage.tsx)

Ajouter un `useEffect` après le useEffect existant qui charge l'organizer (ligne ~260) :

```tsx
useEffect(() => {
  if (!event || !user) return
  if (event.status !== 'DRAFT') return
  if (user.admin) return                         // admin: stays to moderate
  if (user.id !== event.creatorId) return        // not the creator: stays
  // Future SCRUM-137 frontend: also include co-organizer ACCEPTED check here
  navigate(`/events/${event.id}/edit`, { replace: true })
}, [event, user, navigate])
```

**Test manuel** :
1. Login en tant qu'utilisateur A.
2. Créer un brouillon (status DRAFT) — note l'id, ex. 101.
3. Aller sur `/events/101` directement (URL barre) → doit rediriger vers `/events/101/edit` instantanément.
4. Login en tant qu'utilisateur B (non-créateur, non-admin) → visite de `/events/101` → 404 backend → page erreur (pas le rendu détail). Comportement inchangé.
5. Login en tant qu'admin → visite de `/events/101` → reste sur la page détail (pas de redirect).
6. Aller sur un event PUBLISHED en tant que créateur → reste sur la page détail (pas de redirect).

---

## Étape 7 — Tests

### 7.1 — Test automatisé Fix 6

**Fichier :** `frontend/src/__tests__/EventDetailPage.test.tsx` (créer si absent ; sinon ajouter au fichier existant)

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import EventDetailPage from '@/pages/event/EventDetailPage'

// Mock useNavigate
const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

// Mock useEvent and useAuth
vi.mock('@/hooks', () => ({
  useEvent: vi.fn(),
  useAuth: vi.fn(),
  useFavorite: () => ({ favorited: false, loading: false, toggle: vi.fn() }),
}))

// ... import des mocks

describe('EventDetailPage — DRAFT redirect (Fix 6)', () => {
  beforeEach(() => {
    mockNavigate.mockClear()
  })

  it('redirige le créateur vers /edit si event DRAFT', () => {
    // setup mocks: useEvent → DRAFT event with creatorId = 'user-A'
    // useAuth → user { id: 'user-A', admin: false }
    render(<MemoryRouter initialEntries={['/events/42']}><EventDetailPage /></MemoryRouter>)
    expect(mockNavigate).toHaveBeenCalledWith('/events/42/edit', { replace: true })
  })

  it('ne redirige PAS si event PUBLISHED', () => {
    // useEvent → PUBLISHED event with creatorId = 'user-A'
    // useAuth → user { id: 'user-A', admin: false }
    render(<MemoryRouter initialEntries={['/events/42']}><EventDetailPage /></MemoryRouter>)
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('ne redirige PAS un admin sur DRAFT', () => {
    // useEvent → DRAFT event with creatorId = 'user-A'
    // useAuth → user { id: 'admin-X', admin: true }
    render(<MemoryRouter initialEntries={['/events/42']}><EventDetailPage /></MemoryRouter>)
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('ne redirige PAS un non-créateur non-admin sur DRAFT (cas hypothétique — backend renvoie 404 normalement)', () => {
    // useEvent → DRAFT event with creatorId = 'user-A'
    // useAuth → user { id: 'user-B', admin: false }
    render(<MemoryRouter initialEntries={['/events/42']}><EventDetailPage /></MemoryRouter>)
    expect(mockNavigate).not.toHaveBeenCalled()
  })
})
```

> **Note** : le 4e test est défensif — en pratique le backend renvoie 404 pour ce cas, donc `useEvent` ne renverra pas l'event. Mais on teste que la garde frontend est correcte si jamais l'API change.

### 7.2 — Vérifications manuelles (test plan PR)

| # | Fix | Action | Attendu |
|---|---|---|---|
| 1 | Fix 1 | Login → ouvrir le dropdown profil | Voir une carte « Mes événements » (style banner) ouverte par défaut, avec 3 sous-liens favoris/participations/publications. Cliquer le chevron → toggle ouvert/fermé avec animation 200ms. Cliquer un sous-lien → navigation correcte + dropdown se ferme. |
| 2 | Fix 2 | Aller sur `/events/new` avec network throttling 3G | Skeleton « Mes brouillons » s'affiche correctement à mobile/tablet/desktop (largeur, hauteur, position icône+texte+chevron correctes). |
| 3 | Fix 3 | Sur `/my-events/publications` avec plusieurs events, scroller la page | Le bouton « Créer un événement » reste visible en bas-droite pendant le scroll, mais s'arrête au-dessus du footer (ne le recouvre pas). |
| 4a | Fix 4 (event-edit) | Aller sur `/events/:id/edit` avec throttling | Skeleton matche le layout réel (5 bandes + CTA) à 3 viewports. |
| 4b | Fix 4 (event-detail) | Aller sur `/events/:id` PUBLISHED avec throttling | Skeleton matche le layout réel (main + sidebar + carte SCRUM-117) à 3 viewports. |
| 5 | Fix 5 | Sur `/events/:id` d'un event avec websiteUrl + contactEmail | Les deux liens apparaissent en bleu (sky-400 dark / sky-600 light). Hover → underline. |
| 6 | Fix 6 | Login → créer brouillon → URL `/events/<id>` | Redirect immédiat vers `/events/<id>/edit`. Bouton back du navigateur → retour à la page précédente (replace=true). |
| 6b | Fix 6 | Login admin → URL `/events/<draft-id>` | Reste sur la page détail (pas de redirect). |
| 6c | Fix 6 | Login créateur → URL `/events/<published-id>` | Reste sur la page détail. |

---

## Étape 8 — Documentation

### 8.1 — `frontend/AGENTS.md`

Ajouter au tableau "Design tokens CSS" (autour de la ligne 296-309) :

```markdown
| `--color-link` | `text/bg/border-link` | Couleur des liens textuels externes (websiteUrl, mailto, etc.). Sky-600 en light, sky-400 en dark. |
```

### 8.2 — `frontend/docs/components.md`

Mettre à jour les sections impactées :

- **`Navbar`** — mentionner que dans le dropdown profil, l'item « Mes événements » est rendu comme un sous-bloc collapsible style banner-card (`@radix-ui/react-collapsible`), par défaut ouvert. Référence à `DraftsResumeStrip` comme pattern source.
- **`EventDetailPage`** — mentionner que les liens `websiteUrl` et `contactEmail` utilisent désormais `text-link` (CSS variable `--color-link`). Mentionner le redirect DRAFT → /edit pour le créateur (pas pour admin).
- **`EventEditPage`** — mentionner la mise à jour du skeleton pour matcher le layout actuel d'`EventForm` (5 bandes + CTA).
- **`MyPublicationsPage`** — mentionner que le bouton FAB utilise `position: sticky` au lieu de `fixed` pour ne pas recouvrir le footer.
- **`DraftsResumeStrip`** — mentionner que le skeleton est désormais étendu à 3 breakpoints (320, 720, 1216).
- **Section "Skeleton screens"** (ligne 351-373) — mettre à jour la table si pertinent (les bones JSON régénérés ne changent pas la liste).

### 8.3 — `frontend/docs/sprint-context.md`

Ajouter une entrée en fin de section Sprint 7 :

```markdown
- [x] **Polish UX S7** — lot de 6 fix frontend hors-ticket :
  1. Navbar dropdown profil : `Mes événements` transformé en sous-bloc collapsible
     style banner-card (`@radix-ui/react-collapsible`), par défaut ouvert. Sidebar
     mobile inchangée.
  2. Skeleton `drafts-resume-strip.bones.json` étendu de 1 à 3 breakpoints
     (320 / 720 / 1216) — fix de scaling sur desktop.
  3. Bouton FAB `Créer un événement` sur `MyPublicationsPage` passé de
     `position: fixed` à `position: sticky bottom-6 self-end` — ne recouvre plus
     le footer.
  4. Skeletons `event-edit` et `event-detail` régénérés via `generate.mjs` pour
     matcher les layouts actuels (incl. SCRUM-117 / SCRUM-136).
  5. Liens `websiteUrl` et `contactEmail` sur `EventDetailPage` rendus en bleu via
     une nouvelle CSS variable `--color-link` (sky-600 light / sky-400 dark) +
     classe `text-link`.
  6. Redirect automatique `/events/:id` → `/events/:id/edit` sur DRAFT pour le
     créateur (admin exclu, co-organisateur ACCEPTED suit en follow-up SCRUM-137
     frontend).

  PR `chore(frontend): polish navbar, skeletons, sticky FAB, link styling and draft redirect`.
  Pas de ticket Jira — frottements UX repérés au fil du sprint.
```

---

## Edge cases à traiter explicitement

| Cas | Comportement attendu | Couvert par |
|---|---|---|
| Fix 1 — utilisateur sur mobile | Sidebar mobile inchangée (MobileNavItem fonctionne déjà) | Étape 1.2 |
| Fix 1 — admin avec `Administration` ajouté au menu | Le banner-card affiche les 3 sous-liens favoris/participations/publications. `Administration` reste un item plat séparé. Pas de banner-card pour Administration (pas de subLinks). | Logique `if (item.subLinks)` dans `UserDropdownItem` |
| Fix 2 — viewport très étroit (<320px) | Boneyard prend le BP `320` (le plus petit), bones rendus avec scaleY si fixture height ≠ 56 | Le fixture est h-14 = 56px → scaleY = 1, OK |
| Fix 3 — page très courte (peu de cards) | Le bouton sticky reste visible en bas du contenu (court) — pas de scroll, comportement statique | OK |
| Fix 3 — `<SectionWrapper>` a `overflow: hidden` | Sticky ne fonctionne pas — fallback IntersectionObserver | À vérifier au runtime ; si présent, retirer `overflow-hidden` ou switcher en option (b) |
| Fix 4 — event minimal (sans description, sans SCRUM-117) | Le skeleton montre la variante "complète" (worst-case) ; le rendu réel a moins de blocs → pas dramatique pendant le chargement | Acceptable — skeleton est temporaire |
| Fix 4 — bones.height décalé de la fixture réelle | Skeleton étiré ou compressé → visuellement faux | Vérifier `bones.height` au runtime via inspector ; ajuster `genXxx()` si besoin |
| Fix 5 — `safeExternalHref` retourne `null` (URL invalide ou non-http) | Affiche le `<span>` non-cliquable en `text-foreground/70`, PAS en `text-link` | Ligne 421 conditionnelle `safeHref ?` |
| Fix 5 — light mode | `--color-link = sky-600` (rgb(2, 132, 199)) → bleu lisible sur fond clair | Test manuel light mode |
| Fix 5 — dark mode | `--color-link = sky-400` (rgb(56, 189, 248)) → bleu plus clair sur fond sombre | Test manuel dark mode |
| Fix 6 — pendant `isInitialLoad` | `event === null` → useEffect early-return, pas de redirect | Ligne `if (!event || !user) return` |
| Fix 6 — user déconnecté visite DRAFT | Backend renvoie 404 → `event === null` → useEffect ne fait rien → page d'erreur s'affiche | Comportement inchangé |
| Fix 6 — admin créateur d'un DRAFT | `user.admin = true` → useEffect early-return, pas de redirect | Sentinel test 7.1 #3 |
| Fix 6 — co-organisateur ACCEPTED visite DRAFT (futur) | Pas de redirect dans cette PR. Follow-up SCRUM-137 frontend ajoutera la condition. | Mention explicite dans la spec et la doc |

---

## Conventions du projet à respecter

- Lire **EN ENTIER** [`frontend/AGENTS.md`](frontend/AGENTS.md) et [`frontend/skeleton/README.md`](frontend/skeleton/README.md) AVANT toute ligne de code.
- **camelCase partout** dans le code TypeScript ; pas de snake_case ni de préfixe `is`.
- **Imports `@/`** — toujours utiliser l'alias, jamais de chemins relatifs `../`.
- **Tokens CSS** — utiliser `text-foreground`, `text-link`, `border-border`, etc. ; jamais `text-blue-500` ou autre couleur Tailwind brute.
- **DRY** — pas de duplication ; les const arrays comme `myEventsSubLinks` partagés entre desktop et mobile.
- **TypeScript strict** — pas de `any`. Tout typé.
- **Composants utilitaires** prioritaires : `<SectionWrapper>`, `<SectionHeader>`, `<ButtonPrimary>`, etc. (cf. `AGENTS.md` ligne 222-230).
- **Lucide-react** uniquement pour les icônes.
- **Skeleton workflow** : si `loading`, alors skeleton obligatoire. Pas de `LoadingSpinner` hors `PrivateRoute`/`LoadingPage`.
- **Tests Vitest + happy-dom** — voir AGENTS.md pour les pièges (clipboard, sessionStorage…).
- **Doc dans le même commit** que le code.

---

## Interdits stricts

- ❌ Ne PAS toucher à la sidebar mobile (`MobileNavItem`) — fonctionne déjà.
- ❌ Ne PAS migrer `drafts-resume-strip.bones.json` vers `generate.mjs` — manuel reste pertinent (cf. décision 4).
- ❌ Ne PAS utiliser IntersectionObserver pour Fix 3 sans avoir tenté `position: sticky` d'abord (cf. décision 5).
- ❌ Ne PAS appliquer `text-link` aux chips tags ni au lien organisateur sidebar (cf. décision 7).
- ❌ Ne PAS rediriger l'admin sur DRAFT (cf. décision 8).
- ❌ Ne PAS rediriger sur PUBLISHED ou CANCELLED.
- ❌ Ne PAS introduire un `loader` dans le router pour Fix 6 — le `useEffect` est cohérent avec le pattern projet.
- ❌ Ne PAS introduire un `useCoOrganizers` hook dans cette PR — out of scope (SCRUM-137 séparé).
- ❌ Ne PAS toucher à `MobileNavItem` ni à la version sidebar du menu utilisateur.
- ❌ Ne PAS changer la liste des sous-liens favoris/participations/publications.
- ❌ Ne PAS toucher au backend ni à `openapi/openapi.yaml`.
- ❌ Ne PAS modifier `useEvent` ou `useAuth` (utilisés tels quels).
- ❌ Ne PAS introduire de TODO commenté.
- ❌ Ne PAS utiliser `text-blue-400` ou autre couleur Tailwind brute (interdit par AGENTS.md).
- ❌ Ne PAS utiliser `position: absolute` dans Fix 3 (cf. option (c) écartée).
- ❌ Ne PAS casser un test existant — `npm run test` doit rester vert.

---

## Résumé des fichiers touchés

| Fichier | Action |
|---|---|
| [`frontend/src/components/Navbar.tsx`](frontend/src/components/Navbar.tsx) | Modifier — Fix 1 : nouveau composant local `UserDropdownBanner` + délégation depuis `UserDropdownItem` |
| [`frontend/src/index.css`](frontend/src/index.css) | Modifier — Fix 5 : ajout `--color-link` light/dark + (potentiellement) keyframes `collapsible-open/close` pour Fix 1 |
| [`frontend/src/bones/drafts-resume-strip.bones.json`](frontend/src/bones/drafts-resume-strip.bones.json) | Modifier — Fix 2 : étendu de 1 à 3 breakpoints |
| [`frontend/src/pages/my-events/MyPublicationsPage.tsx`](frontend/src/pages/my-events/MyPublicationsPage.tsx) | Modifier — Fix 3 : `fixed` → `sticky` |
| [`frontend/skeleton/generate.mjs`](frontend/skeleton/generate.mjs) | Modifier — Fix 4 : `genEventEdit()` + `genEventDetail()` à jour |
| [`frontend/src/bones/event-edit.bones.json`](frontend/src/bones/event-edit.bones.json) | Modifier — Fix 4 : régénéré par `npm run skeleton` |
| [`frontend/src/bones/event-detail.bones.json`](frontend/src/bones/event-detail.bones.json) | Modifier — Fix 4 : régénéré par `npm run skeleton` |
| [`frontend/src/pages/event/EventEditPage.tsx`](frontend/src/pages/event/EventEditPage.tsx) | Modifier — Fix 4 : `EventFormFixture` réécrite |
| [`frontend/src/pages/event/EventDetailPage.tsx`](frontend/src/pages/event/EventDetailPage.tsx) | Modifier — Fix 4 + 5 + 6 : `EventDetailFixture` réécrite, `text-link` sur 2 anchors, useEffect redirect DRAFT |
| `frontend/src/__tests__/EventDetailPage.test.tsx` | **Créer** (ou compléter si existant) — 4 tests redirect Fix 6 |
| [`frontend/AGENTS.md`](frontend/AGENTS.md) | Modifier — Doc : ligne `--color-link` dans le tableau Design tokens |
| [`frontend/docs/components.md`](frontend/docs/components.md) | Modifier — Doc : sections `Navbar`, `EventDetailPage`, `EventEditPage`, `MyPublicationsPage`, `DraftsResumeStrip` |
| [`frontend/docs/sprint-context.md`](frontend/docs/sprint-context.md) | Modifier — Doc : entrée "Polish UX S7" en fin de Sprint 7 |

**Total** : 1 fichier créé (test) + 12 fichiers modifiés (dont 3 docs + 1 AGENTS.md). 0 fichier backend, 0 modif d'`openapi.yaml`.

---

## Branche et PR

### Branche

```bash
git fetch origin
git checkout -b feature/s7-frontend-polish-fixes origin/main --no-track
git push -u origin feature/s7-frontend-polish-fixes
```

### PR

- **Base :** `main`.
- **Titre :** `chore(frontend): polish navbar, skeletons, sticky FAB, link styling and draft redirect`
  - Type `chore` car aucun ticket Jira ; scope `frontend` (autorisé pour types non-`feat`/`refactor`/`perf`).
- **Description :** **À FOURNIR PAR L'AGENT EN FIN D'IMPLÉMENTATION** (cf. section « Livrable final attendu »).

### Commits atomiques suggérés

Pour faciliter la review, regrouper par fix :

- `chore(frontend): banner-card pattern for "Mes événements" in profile dropdown` (Fix 1)
- `chore(frontend): extend drafts-resume-strip skeleton to 3 breakpoints` (Fix 2)
- `chore(frontend): switch MyPublications FAB to position sticky` (Fix 3)
- `chore(frontend): regenerate event-edit and event-detail skeletons` (Fix 4 — incl. fixtures + bones)
- `chore(frontend): introduce --color-link token and apply to event detail anchors` (Fix 5)
- `chore(frontend): redirect creator to /edit on DRAFT events` (Fix 6 — incl. tests)
- `docs(frontend): document polish S7 fixes in components.md, sprint-context, AGENTS.md` (Doc)

Combinables en moins de commits si le diff total est petit. Le titre de PR final reste celui ci-dessus, indépendamment du nombre de commits.

---

## Checklist Sonar / qualité

- [ ] `npm run lint` vert (ESLint + TypeScript strict).
- [ ] `npm run test` vert (Vitest + happy-dom).
- [ ] Couverture ≥ 80% sur les lignes nouvelles (cible : 100% sur `EventDetailPage` au moins pour le useEffect redirect).
- [ ] Pas de `any` ajouté.
- [ ] Pas de `text-blue-*` ou autre couleur Tailwind brute introduite.
- [ ] Pas de `LoadingSpinner` ajouté hors `PrivateRoute`/`LoadingPage`.
- [ ] Tous les imports utilisent l'alias `@/`.
- [ ] Skeleton bones height matche la fixture height au runtime (à vérifier visuellement à 3 viewports).

---

## Checklist finale

### Avant push

- [ ] `npm run lint` vert.
- [ ] `npm run test` vert.
- [ ] `npm run build` vert (vérifier que la prod build ne casse pas).
- [ ] Vérifications manuelles à 3 viewports (mobile 320, tablet 720, desktop 1216) sur les 6 fix.
- [ ] Light mode + dark mode testés pour Fix 5 (couleur du lien).
- [ ] `git diff --stat backend/` vide.
- [ ] `git diff --stat openapi/` vide.
- [ ] Pas de TODO commenté ajouté.
- [ ] Pas de console.log ou debug dans le code.
- [ ] `frontend/skeleton/generate.mjs` modifié → `npm run skeleton` lancé → JSON commités.

### Avant PR

- [ ] Branche `feature/s7-frontend-polish-fixes` créée avec `--no-track` depuis `origin/main`.
- [ ] `git branch -vv` confirme tracking sur `origin/feature/s7-frontend-polish-fixes` (PAS `origin/main`).
- [ ] Commits atomiques nommés selon la convention (préfixe `chore(frontend):` ou `docs(frontend):`).
- [ ] Description de PR remplie selon le template `.github/pull_request_template.md`.
- [ ] Base de la PR : `main`.
- [ ] La check CI `Lint PR title` est verte (`chore(frontend): ...` est valide — type `chore` accepte un scope libre).

### Avant merge

- [ ] CI verte (lint + tests + build).
- [ ] Review approuvée.
- [ ] SonarCloud quality gate vert.

---

## Livrable final attendu — titre et description de PR

**À la fin de l'implémentation, l'agent DOIT retourner dans la réponse :**

1. **Le titre exact de la PR** — `chore(frontend): polish navbar, skeletons, sticky FAB, link styling and draft redirect`.

2. **La description complète de PR**, prête à coller dans GitHub, qui suit strictement le template [`.github/pull_request_template.md`](.github/pull_request_template.md). Sections obligatoires :
   - `## Résumé` (1-3 phrases : 6 fix UX repérés au fil du sprint, pas de ticket Jira).
   - `## Why / Motivation` (frottements remontés par utilisation interne, fenêtre fin S7).
   - `## Changements` avec sous-sections `### Frontend` (les 6 fix listés avec liens `[file](path#L...)`) et `### Documentation`.
   - `## Tests` (résumé tests automatisés Fix 6, manuel pour les 5 autres).
   - `## Test plan` (checklist concrète avec les 7 vérifications du tableau étape 7.2).
   - `## Documentation` (checkbox + liste des fichiers de doc modifiés).
   - `## Dépendances / ordre de merge` (aucune dépendance amont ; mention que SCRUM-137 frontend étendra Fix 6 au co-organisateur ACCEPTED).
   - `## Décisions techniques tranchées` (au minimum : type `chore` car pas de ticket Jira, banner-card par défaut ouvert, sticky vs IntersectionObserver, redirect créateur seul (pas co-org pour cette PR), nouvelle CSS variable `--color-link`).
   - `## Notes pour le reviewer` (souligner qu'on regroupe 6 fix indépendants en 1 PR pour réduire le bruit Git, suggérer d'auditer commit-par-commit pour faciliter la review).

L'utilisateur ouvrira la PR lui-même — **NE PAS appeler `gh pr create`**.

---

## Prompt de lancement d'implémentation

````
Tu vas implémenter 6 fix UX frontend repérés au fil du sprint 7 sur le projet 
unige-events-web (React 19 / TS strict / Vite). Pas de ticket Jira — c'est une 
PR `chore` de polish.

## ÉTAPE 0 — Création de la branche (avec --no-track OBLIGATOIRE)

Avant TOUT code :

    git fetch origin
    git checkout -b feature/s7-frontend-polish-fixes origin/main --no-track

Premier push (dès qu'un commit existe) :

    git push -u origin feature/s7-frontend-polish-fixes

Le flag `--no-track` est CRITIQUE — sans lui git push envoie sur main.

## Source unique de vérité

`specs_archives/specs_claude/specs_frontend-fixes-s7.md` — à lire INTÉGRALEMENT 
avant d'écrire une ligne de code. Toutes les décisions (banner-card par défaut 
ouvert, sticky vs IntersectionObserver, --color-link CSS variable, redirect 
créateur seul, etc.) y sont tranchées.

## À lire avant de commencer

1. `frontend/AGENTS.md` EN ENTIER — conventions critiques (camelCase, design 
   tokens, structure pages/composants, imports @/, gestion erreurs, skeleton 
   non-négociable, pattern variants typés).
2. `frontend/skeleton/README.md` EN ENTIER — workflow skeleton, format des 
   bones, container-width breakpoints, pièges, checklist.
3. `frontend/docs/components.md` — pages affectées (EventDetailPage, 
   EventEditPage, MyPublicationsPage, Navbar, DraftsResumeStrip).
4. `frontend/docs/architecture.md` — table de routage.
5. Code source à inspecter avant de coder :
   - `frontend/src/components/Navbar.tsx` lignes 35-103 (UserDropdownItem actuel)
   - `frontend/src/components/event/DraftsResumeStrip.tsx` lignes 82-138 
     (pattern Collapsible.Root à copier)
   - `frontend/src/components/event/EventForm.tsx` (layout 5 bandes)
   - `frontend/src/pages/event/EventDetailPage.tsx` (fixture, anchors 
     websiteUrl/contactEmail, lieu du useEffect redirect)
   - `frontend/src/pages/event/EventEditPage.tsx` lignes 16-60 (fixture)
   - `frontend/src/pages/my-events/MyPublicationsPage.tsx` lignes 375-382 (FAB)
   - `frontend/skeleton/generate.mjs` (genEventEdit ligne 811, genEventDetail 
     ligne 489)
   - `frontend/src/bones/drafts-resume-strip.bones.json` (état actuel 1 BP)
   - `frontend/src/bones/event-edit.bones.json` et `event-detail.bones.json`
   - `frontend/src/index.css` (CSS variables theme)
   - `frontend/src/types/user.ts` (champ admin pour Fix 6)
   - `frontend/src/hooks/` (useEvent, useAuth)

## Ordre d'implémentation strict

1. **Branche créée + premier commit conventionnel** (ex. config initiale ou 
   premier fix).
2. **Fix 1 — Navbar banner-card** : nouveau composant local UserDropdownBanner 
   dans Navbar.tsx avec Collapsible.Root Radix + style banner-card. Vérifier 
   Mobile inchangé. Ajouter keyframes collapsible si manquantes dans index.css.
3. **Fix 2 — Skeleton drafts-resume-strip** : étendre le JSON manuel à 3 BPs 
   (320, 720, 1216). Pas de changement à la fixture.
4. **Fix 3 — Sticky FAB** : 1 mot dans MyPublicationsPage.tsx (fixed → sticky 
   self-end). Vérifier que SectionWrapper n'a pas overflow-hidden bloquant.
5. **Fix 4 — Skeletons event-edit + event-detail** :
   a. Auditer EventForm.tsx (5 bandes) et EventDetailPage.tsx (main + sidebar).
   b. Réécrire EventFormFixture dans EventEditPage.tsx pour matcher le layout.
   c. Réécrire EventDetailFixture dans EventDetailPage.tsx (ajout SCRUM-117).
   d. Mettre à jour genEventEdit() et genEventDetail() dans generate.mjs.
   e. `npm run skeleton` pour régénérer les bones JSON.
   f. Vérifier visuellement à 3 viewports.
6. **Fix 5 — Lien bleu** :
   a. Ajouter --color-link dans index.css (light + dark).
   b. Appliquer text-link aux 2 anchors websiteUrl + contactEmail dans 
      EventDetailPage.
   c. Ajouter ligne au tableau Design tokens dans AGENTS.md.
7. **Fix 6 — Redirect DRAFT** :
   a. Vérifier qu'aucun useCoOrganizers hook n'existe (grep dans frontend/src/).
   b. Ajouter useEffect dans EventDetailPage avec garde admin + creator only.
   c. Écrire 4 tests dans __tests__/EventDetailPage.test.tsx.
8. **`npm run lint`** + **`npm run test`** + **`npm run build`** — DOIT être vert.
9. **Documentation** :
   - frontend/AGENTS.md : ligne --color-link.
   - frontend/docs/components.md : sections Navbar, EventDetailPage, 
     EventEditPage, MyPublicationsPage, DraftsResumeStrip.
   - frontend/docs/sprint-context.md : entrée "Polish UX S7".
10. **Vérifications visuelles** — ouvrir l'app en `npm run dev`, throttle 3G, 
    tester les 7 cas du test plan (étape 7.2 de la spec) à 3 viewports + light 
    + dark.
11. **Commits atomiques** — un par fix au minimum, plus un commit doc final.
12. **Push** : `git push` (branche déjà tracking grâce au -u initial).

## Livrable FINAL attendu (à fournir à l'utilisateur dans la réponse)

**OBLIGATOIRE — sans ces deux blocs, la tâche n'est PAS terminée :**

1. **Titre EXACT de la PR** :

   `chore(frontend): polish navbar, skeletons, sticky FAB, link styling and draft redirect`

2. **Description COMPLÈTE de la PR**, prête à coller dans le textarea GitHub, 
   qui suit strictement le template `.github/pull_request_template.md`. 
   Sections à remplir :
   - `## Résumé` (1-3 phrases ; mentionner explicitement "pas de ticket Jira").
   - `## Why / Motivation` (frottements UX repérés au fil du sprint 7 ; fin de 
     sprint = fenêtre idéale pour polish).
   - `## Changements` avec sous-sections `### Frontend` (les 6 fix avec liens 
     [file](path#L...) cliquables) et `### Documentation`.
   - `## Tests` (4 tests automatisés sur Fix 6 ; manuel pour les 5 autres).
   - `## Test plan` (checklist concrète : ouvrir profil dropdown, throttle 3G 
     sur /events/new et /events/:id et /events/:id/edit, scroller MyPublications, 
     vérifier light + dark mode pour le lien bleu, tester redirect DRAFT en tant 
     que créateur / admin / non-créateur).
   - `## Documentation` (checkbox + fichiers).
   - `## Dépendances / ordre de merge` (aucune amont ; SCRUM-137 frontend 
     étendra Fix 6 au co-org ACCEPTED en follow-up).
   - `## Décisions techniques tranchées` (au minimum : type `chore` car pas de 
     ticket Jira, banner-card par défaut ouvert pour réduire les clics, sticky 
     plutôt que IntersectionObserver pour Fix 3, redirect créateur seul pour 
     Fix 6 (admin exclu, co-org follow-up), nouvelle CSS variable --color-link 
     plutôt que valeur Tailwind brute).
   - `## Notes pour le reviewer` (6 fix indépendants regroupés en 1 PR pour 
     réduire le bruit Git ; suggérer d'auditer commit-par-commit ; mentionner 
     que sticky FAB est testé visuellement uniquement, pas en jsdom/happy-dom).

L'utilisateur ouvrira la PR lui-même — **NE PAS appeler `gh pr create`**.

## Interdits stricts

- PAS de modif backend ni d'`openapi/openapi.yaml`.
- PAS de modif de la sidebar mobile (`MobileNavItem`).
- PAS de migration `drafts-resume-strip.bones.json` vers `generate.mjs`.
- PAS d'IntersectionObserver pour Fix 3 (essayer sticky d'abord).
- PAS d'application de `text-link` aux chips tags ni au lien organisateur sidebar.
- PAS de redirect admin sur DRAFT.
- PAS de redirect sur PUBLISHED ou CANCELLED.
- PAS de `useCoOrganizers` hook créé dans cette PR.
- PAS de `loader` router pour Fix 6 (useEffect en page).
- PAS de TODO commenté.
- PAS de `text-blue-*` ou couleur Tailwind brute.
- PAS de `console.log` ou debug.
- PAS de cassure de tests existants — `npm run test` doit rester vert.
- PAS de snake_case côté TS.

## Conventions à respecter

- camelCase partout en TS, JSON, CSS variables.
- Pas de préfixe `is` sur les booléens (n/a — aucun ajouté).
- Imports `@/` uniquement.
- TypeScript strict, pas de `any`.
- Tokens CSS pour les couleurs (jamais `text-blue-400`).
- Lucide-react pour les icônes.
- Composants utilitaires (`SectionWrapper`, etc.) prioritaires.
- Skeleton workflow : si `loading`, alors skeleton obligatoire.
- Tests Vitest + happy-dom (cf. AGENTS.md pour les pièges).
- Doc dans le même commit que le code correspondant.
- Commits atomiques par fix : `chore(frontend): ...`.

## Critères de done

- [ ] `npm run lint` + `npm run test` + `npm run build` verts.
- [ ] Les 4 tests Fix 6 verts nommément (créateur DRAFT redirige, créateur 
  PUBLISHED ne redirige pas, admin DRAFT ne redirige pas, non-créateur DRAFT 
  ne redirige pas).
- [ ] Les 7 vérifications manuelles du test plan passées (3 viewports + light 
  + dark mode).
- [ ] `git diff --stat backend/` vide.
- [ ] `git diff --stat openapi/` vide.
- [ ] Skeletons régénérés par `npm run skeleton` après modification de 
  generate.mjs ; `bones.height` matche la fixture height au runtime.
- [ ] frontend/AGENTS.md, components.md, sprint-context.md mis à jour dans le 
  même commit doc.
- [ ] Commits atomiques bien nommés.
- [ ] `git branch -vv` confirme tracking `origin/feature/s7-frontend-polish-fixes` 
  (PAS origin/main).
- [ ] La check CI `Lint PR title` est verte (`chore(frontend): ...` accepté).
- [ ] **Titre + description complète de PR fournis dans la réponse finale**, 
  prêts à coller dans GitHub.
````
