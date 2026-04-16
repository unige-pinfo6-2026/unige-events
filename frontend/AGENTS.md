# AGENTS.md — unige-events-web

## Rôle
Frontend SPA de UNIGE Events. React 19 · TypeScript strict · Vite · servi par Nginx en production.

## Commandes
```bash
npm run dev      # dev local (Vite, proxy /api → api:8080)
npm run build    # build production
npm run lint     # ESLint + TypeScript checks
npm run test     # tests unitaires (couverture V8)
npm run preview  # preview du build prod en local
```

## Architecture MVC
```
View       → src/pages/ et src/components/   (composants React)
Controller → src/services/                   (Axios, logique appels API)
Model      → hooks/ et contexts/             (état applicatif)
```

## Conventions critiques

### Nommage — camelCase partout
- L'API backend retourne du **camelCase** : `user.displayName`, `event.startDate`, `event.creatorId`
- **Ne jamais utiliser de snake_case** pour les champs des types TypeScript ou les réponses API
- Les champs booléens backend n'ont **pas** de préfixe `is` : le champ s'appelle `active`, `featured`, `admin`, `read`, `profilePublic` (pas `isActive`, `isFeatured`, etc.)
- Les types dans `src/types/` doivent refléter exactement les noms de champs retournés par l'API — se référer à `docs/openapi/openapi.yaml` comme source de vérité

### Composants
- **Toujours créer des composants réutilisables** pour les éléments répétés (avatar utilisateur, card d'événement, badge de catégorie, etc.)
- Les composants partagés vont dans `src/components/`
- Séparer la logique métier du rendu : extraire dans un hook (`src/hooks/`) ou un service (`src/services/`) si un composant fait des appels API ou contient de la logique complexe

### DRY — Don't Repeat Yourself
- **Ne jamais dupliquer des données ou de la logique** : si la même liste de liens, de labels ou de classes apparaît à deux endroits, extraire dans une const array ou un composant.
- Les listes de données statiques (liens de nav, sections de menu, options de filtre…) se déclarent comme **const arrays typées en dehors des composants** et sont réutilisées partout où elles sont nécessaires (desktop, mobile, tests…).
- Un composant extrait se justifie dès que la même structure JSX apparaît deux fois. En dessous de deux occurrences, l'inline est préférable.
- Le composant local (défini dans le même fichier, non exporté) est la bonne réponse quand la structure se répète uniquement dans ce fichier. Ne pas créer un fichier dédié pour chaque micro-composant.

```tsx
// ✅ Correct — const array partagée entre desktop et mobile
const navLinks = [
  { href: '/#events', label: 'En ce moment' },
  { href: '/#faq', label: 'FAQ' },
]
// utilisée dans le rendu desktop ET mobile

// ❌ Interdit — même liste dupliquée dans deux blocs JSX
```

```tsx
// ✅ Correct — structure JSX répétée → composant local dans le même fichier
function AboutRow({ icon: Icon, children }: { icon: LucideIcon; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-3 text-sm text-foreground/60">
      <Icon className="w-5 h-5 shrink-0 text-foreground/30" />
      <span className="truncate">{children}</span>
    </div>
  )
}
// utilisé N fois dans le même composant parent
<AboutRow icon={Mail}>{profile.email}</AboutRow>
<AboutRow icon={GraduationCap}>{profile.studyLevel}</AboutRow>

// ❌ Interdit — même div/classe copiée-collée à chaque occurrence
<div className="flex items-center gap-3 text-sm text-foreground/60">
  <Mail className="w-5 h-5 shrink-0 text-foreground/30" />
  <span className="truncate">{profile.email}</span>
</div>
<div className="flex items-center gap-3 text-sm text-foreground/60">
  <GraduationCap className="w-5 h-5 shrink-0 text-foreground/30" />
  <span className="truncate">{profile.studyLevel}</span>
</div>
```

### Pattern variants — const maps typées

Pour tout composant qui accepte des variantes visuelles (couleur, taille, position…), **ne jamais utiliser de ternaires ou de switch inline**. Déclarer des const maps typées en dehors du composant, puis indexer avec la prop :

```tsx
// ✅ Correct
const variants = {
  success: 'border-emerald-500/40 text-emerald-400',
  error:   'border-error/40 text-error',
}

function MyComponent({ type }: { type: keyof typeof variants }) {
  return <div className={variants[type]}>…</div>
}

// ❌ Interdit
const cls = type === 'success' ? 'border-emerald-500/40 …' : 'border-error/40 …'
```

Ce pattern est en place dans `src/components/utils/Blobs.tsx` (colors, sizes, positions) et `src/components/utils/Toast.tsx` (variants). L'appliquer à tout nouveau composant avec des variantes.

### Structure des fichiers — pages et composants

#### Pages — miroir des routes
Chaque page doit être placée dans un sous-dossier de `src/pages/` qui reflète exactement sa route. Le fichier principal s'appelle `<Nom>Page.tsx`.

```
Route /profile          → src/pages/profile/ProfilePage.tsx
Route /profile/edit     → src/pages/profile/ProfileEditPage.tsx
Route /event/:id        → src/pages/event/EventDetailPage.tsx
Route /search           → src/pages/search/SearchPage.tsx
```

Les pages globales sans route dédiée (`LandingPage`, `NotFoundPage`, `LoadingPage`) restent directement dans `src/pages/`.

#### Composants — organisation par domaine
Les composants sont rangés par domaine fonctionnel, **pas** nécessairement calqués sur les routes :

| Dossier | Contenu |
|---|---|
| `components/event/` | Tout ce qui concerne l'affichage d'un événement (`EventCard`, `EventForm`…) |
| `components/calendar/` | Composants liés au calendrier (`EventCalendar`…) |
| `components/user/` | Avatar, identité utilisateur (`UserAvatar`, `UserIdentity`…) |
| `components/faculty/` | Composants liés aux facultés |
| `components/auth/` | Composants liés à l'authentification |
| `components/utils/` | Composants génériques réutilisables partout (`Buttons`, `Toast`, `Blobs`…) |

Utiliser le **singulier** pour les noms de dossiers (`event/`, `calendar/`, `user/`) — pas `events/` ni `calendars/`.

Un composant qui n'appartient clairement qu'à un seul domaine va dans le dossier de ce domaine. Un composant transversal va dans `utils/`. Les composants de layout global (`Navbar`, `Layout`, `Footer`) restent à la racine de `src/components/`.

### Routing et auth
- Toutes les routes protégées passent par `PrivateRoute` (vérifie `isAuthenticated` via `AuthContext`)
- Le token JWT est stocké en localStorage sous la clé `access_token` — ne pas changer cette clé

### Appels API
- Toujours utiliser l'instance Axios dans `src/services/api.ts` — **ne jamais appeler `/api` avec `fetch` ou un autre `axios.create()`**
- L'intercepteur Axios ajoute automatiquement le header `Authorization: Bearer <token>`
- En dev, Vite proxie `/api` → `http://api:8080`. En prod, Nginx proxie vers `api:8080`.
- Chaque appel API doit gérer les états : **loading**, **error**, **data** — jamais d'affichage avec des données `undefined` ou `null`

### Gestion des erreurs
- Si `GET /api/users/me` échoue (401, réseau), rediriger vers `/login` — ne jamais afficher une page avec des champs `?` ou vides
- Après un `PUT /api/users/me` réussi, mettre à jour l'état local avec la réponse du serveur — ne pas attendre un refresh manuel
- Utiliser les Error Boundaries React pour les erreurs inattendues

### TypeScript
- TypeScript strict : **pas de `any`**
- Toujours typer les props, les réponses API, et les états
- Ne jamais redéfinir les types d'entités hors de `src/types/`

### Pattern — données statiques typées (`as const` + `keyof typeof`)

Pour toute liste de données statiques avec des métadonnées associées (facultés, catégories, rôles…), utiliser un objet `as const` et dériver le type union depuis ses clés :

```ts
// ✅ Correct — src/types/faculty.ts
export const FACULTIES = {
  SCIENCES: { name: 'Faculté des Sciences', abbr: 'Sciences', logo: Sciences },
  MEDECINE: { name: 'Faculté de Médecine', abbr: 'Médecine', logo: Medicine },
  // ...
} as const

export type Faculty = keyof typeof FACULTIES
// → 'SCIENCES' | 'MEDECINE' | ...

// ❌ Interdit — Record avec string trop large, ou enum séparé
export type Faculty = string
export const FACULTIES: Record<string, { name: string }> = { ... }
```

**Avantages :**
- Single source of truth : ajouter/supprimer une entrée met à jour le type automatiquement
- Autocomplétion et vérification exhaustive à la compilation
- Les métadonnées (libellé, icône, abréviation…) sont colocalisées avec les clés

**Caveat `Object.entries()`** : `Object.entries()` widens les clés en `string`. Caster explicitement quand on assigne à un type dérivé :

```tsx
// Object.entries retourne [string, ...]
Object.entries(FACULTIES).map(([id, faculty]) => (
  <button
    onClick={() => setFilters({ ...filters, faculty: id as Faculty })}
  >
    {faculty.abbr}
  </button>
))
```

Ce cast est sûr car les clés proviennent directement de l'objet `FACULTIES`.

### Imports — toujours utiliser l'alias `@`
`@` est un alias vers `src/` (configuré dans `tsconfig.app.json`). **Toujours utiliser `@/` pour les imports internes** — jamais de chemins relatifs avec `../`.

```ts
// ✅ Correct
import { api } from '@/services/api'
import type { Event } from '@/types/event'

// ❌ Interdit
import { api } from '../../services/api'
import type { Event } from '../types/event'
```

## Contrat API
`openapi/openapi.yaml` est la **source de vérité** (monorepo — fichier unique partagé entre frontend et backend). Avant d'implémenter un service dans `src/services/`, vérifier que l'endpoint existe dans ce fichier et noter les noms de champs exacts retournés.

## Design & UI — "faut que ça claque"

### Référence visuelle
`src/pages/LandingPage.tsx` est la **référence de style** du projet. Toute nouvelle page ou section doit s'aligner sur ce niveau de qualité visuelle : typographie grande et grasse, fonds avec blobs animés, cards glassmorphism, gradients sur les accents.

Pour les **pages derrière une route privée** (authentification requise), prendre `src/pages/calendar/CalendarPage.tsx` comme inspiration.

### Composants utilitaires à utiliser en priorité

| Composant | Import | Usage |
|---|---|---|
| `ButtonPrimary`, `ButtonSecondary` | `@/components/utils/Buttons` | Tous les boutons d'action |
| `BlobsHero`, `BlobsSubtle`, `BlobsCta`, `Blobs` | `@/components/utils/Blobs` | Fonds décoratifs de sections |
| `SectionWrapper` | `@/components/utils/Section` | Wrapper standard de section (props : `padding`, `size`, `background`, `footer`) |
| `SectionHeader` | `@/components/utils/Section` | Titre + sous-titre de section (prop : `heading`, `align`) |
| `Marquee` | `@/components/utils/Marquee` | Défilement horizontal |
| `FormField` | `@/components/utils/FormField` | Champs de formulaire |
| `Toast` | `@/components/utils/Toast` | Notifications |

### Icônes — lucide-react uniquement
Toujours utiliser `lucide-react` pour les icônes. Ne jamais utiliser d'autres librairies d'icônes ni des SVG inline ad hoc.

### Pattern de section
Chaque grande section de page utilise `SectionWrapper` + `SectionHeader` depuis `@/components/utils/Section` :

```tsx
<SectionWrapper id="mon-id" background={<BlobsSubtle />}>
  <SectionHeader title="Titre" subtitle="Sous-titre optionnel" />
  {/* contenu */}
</SectionWrapper>
```

**Variants `SectionWrapper`** — toujours utiliser les props typées, jamais de `className` libre :

| Prop | Valeurs | Défaut | Effet |
|---|---|---|---|
| `padding` | `hero` · `md` · `sm` · `bottom` | `md` | Espacement vertical de la section |
| `size` | `xl` · `lg` · `md` | `xl` | Largeur max du contenu (`max-w-7xl` / `5xl` / `3xl`) |
| `tint` | `boolean` | `false` | Ajoute `bg-foreground/2` (fond légèrement teinté) |

**Variant `SectionHeader`** :

| Prop | Valeurs | Défaut |
|---|---|---|
| `align` | `center` · `left` | `center` |

### Typographie
- Titres de sections : `<SectionHeader></SectionHeader>`
- Corps : `text-xl text-foreground/60 font-light leading-relaxed`
- Gradient sur mot-clé accent : `<mark>mot</mark>`

### Cards glassmorphism
Pattern standard pour les cards :
```tsx
<div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-8 border border-border hover:border-foreground/50 transition-colors">
```
- `rounded-3xl` pour les cards larges, `rounded-2xl` pour les éléments intermédiaires
- Toujours inclure `transition-colors` et un état hover (`hover:border-foreground/50` ou `hover:border-accent/50`)

### Icônes dans des badges colorés
Pour afficher une icône dans un badge coloré (comme dans la section Features) :
```tsx
<div className={`w-16 h-16 rounded-2xl bg-linear-to-br ${gradient} flex items-center justify-center shadow-lg`}>
  <MonIcone className="w-8 h-8 text-foreground" />
</div>
```

### Gradients décoratifs
- Élément décoratif coin de card : `absolute top-0 right-0 w-32 h-32 bg-linear-to-br from-white/5 to-transparent rounded-bl-full`
- Utiliser `bg-linear-to-br` (Tailwind v4) — pas `bg-gradient-to-br`

### Responsive
- Mobile-first : base = mobile, `lg:` = desktop
- Espacements de sections : `py-20 lg:py-32`
- Grilles : `grid md:grid-cols-2 lg:grid-cols-3 gap-6`

### Espacement des pages
Les pages n'ont **aucun padding ou margin par défaut** — le layout ne fournit rien. Chaque page est responsable de ses propres marges internes. Toujours gérer explicitement l'espacement vertical en haut et en bas du contenu (ex: `py-12 lg:py-16` pour un header hero, `pb-20` en bas de page).

## Design tokens CSS

Le thème est défini dans `src/index.css` via `@theme` (TailwindCSS v4). **Toujours utiliser les tokens plutôt que des couleurs Tailwind brutes.**

| Token | Classe Tailwind | Usage |
|---|---|---|
| `--color-primary` | `text/bg/border-primary` | Couleur de marque principale |
| `--color-accent` | `text/bg/border-accent` | Accentuation, focus, liens actifs |
| `--color-background` | `bg-background` | Fond de page et cards |
| `--color-foreground` | `text-foreground` (+ `/60`, `/40`…) | Texte (avec opacités) |
| `--color-border` | `border-border` | Bordures standard |
| `--color-error` | `text/bg/border-error` | Erreurs de validation, messages d'échec, champs invalides |
| `--color-warning` | `text/bg/border-warning` | États d'avertissement non-bloquants (ex. badge "Brouillon" sur `EventCard`). Amber `rgb(245,158,11)`. |
| `--color-overlay` | — | Fond semi-transparent pour modales |
| `--font-primary` | `font-primary` | Police Inter |
| `--height-navbar` | `h-navbar` | Hauteur de la navbar |

Règle : ne jamais utiliser `red-400`, `red-500` ou autre valeur brute — utiliser `text-error` / `border-error` / `bg-error`.

## Ce qu'il ne faut jamais faire
- Appeler `/api` avec `fetch` ou un `axios` instancié localement
- Créer des types dupliqués en dehors de `src/types/`
- Utiliser `any` en TypeScript
- Afficher une page avec des données `undefined`, `null`, ou des `?` à la place de valeurs
- Ne pas mettre à jour l'état local après une mutation réussie (forcer un refresh est une mauvaise pratique)

## Documentation du projet
- `docs/README.md` — index
- `docs/architecture.md` — architecture frontend et rôle dans le système global
- `docs/components.md` — pages, composants réutilisables, et services existants
- `docs/types.md` — types TypeScript et correspondance exacte avec les champs API
- `openapi/openapi.yaml` — contrat API (fichier unique du monorepo — ne pas dupliquer)
- `docs/dev-guide.md` — guide de démarrage et workflows
- `docs/sprint-context.md` — état d'avancement

## Skeleton screens — règle non négociable

**Toute page ou composant qui effectue un appel API et affiche un état `loading` doit avoir un skeleton `.bones.json` correspondant.** Les agents ne demandent pas de confirmation — c'est une obligation systématique au même titre que la gestion des états `loading`, `error` et `data`.

> **Lecture obligatoire avant toute implémentation frontend impliquant un chargement :**
> [`frontend/skeleton/README.md`](skeleton/README.md) — workflows complets, format des bones, pièges, checklist.

### Quand générer un skeleton

| Situation | Action requise |
|---|---|
| Nouvelle page avec appel API | Générer le skeleton avant de clore la tâche |
| Nouveau composant avec appel API (hook `loading`) | Générer le skeleton |
| Refactoring du layout d'une page déjà couverte | Mettre à jour `skeleton/generate.mjs` et relancer `npm run skeleton` |
| Skeleton existant qui ne correspond plus au layout réel | Corriger immédiatement |

### Skeletons existants (à tenir à jour)

| Nom | Fichier bones | Composant consommateur | Méthode |
|---|---|---|---|
| `event-cards` | `event-cards.bones.json` | `EventCards` | generate.mjs |
| `event-detail` | `event-detail.bones.json` | `EventDetailPage` | generate.mjs |
| `event-edit` | `event-edit.bones.json` | `EventEditPage` | generate.mjs |
| `profile` | `profile.bones.json` | `ProfilePage` | manuel |
| `search-results` | `search-results.bones.json` | `EventsSearchPage` | generate.mjs |
| `event-calendar` | `event-calendar.bones.json` | `EventCalendar` | generate.mjs |
| `navbar-user` | `navbar-user.bones.json` | `Navbar` (`DesktopNav`) | manuel |
| `my-events` | `my-events.bones.json` | Pages `/my-events/*` | manuel |
| `drafts-resume-strip` | `drafts-resume-strip.bones.json` | `DraftsResumeStrip` (header collapsed, conditionnel) | manuel |

---

## Maintenance de la documentation
**En tant qu'agent, tu dois mettre à jour la documentation dans les cas suivants :**

| Fichier modifié | Documentation à mettre à jour |
|---|---|
| Nouveau composant réutilisable | `docs/components.md` (section composants partagés) |
| Nouvelle page | `docs/components.md` + `docs/architecture.md` (table de routage) |
| Nouveau service dans `src/services/` | `docs/components.md` (section services) |
| Ajout ou modification dans `src/types/` | `docs/types.md` |
| Nouvelle route dans le router | `docs/architecture.md` (table de routage) |
| `openapi.yaml` mis à jour | Le fichier unique est `openapi/openapi.yaml` — monorepo, pas de copie à synchroniser |
| Fin de sprint / tâche terminée | `docs/sprint-context.md` |
| Nouveau skeleton | `AGENTS.md` (table "Skeletons existants") + `docs/components.md` |

**Règle d'or : si tu touches au code, tu touches à la doc correspondante dans le même commit.**

## Workflow Git
- Branche : `feature/SCRUM-XX-description`
- 1 PR par tâche, review obligatoire avant merge sur main
- Qualité : couverture V8, lint + TypeScript checks en CI

# Requis analyse Sonar :
- Minimum 80% de coverage sur le nouveau code
- Maximum 3% de duplication sur le nouveau code
- Security Rating : A
- Security Review Rating : A
- Reliability Rating : A
- Maintainability Rating : A