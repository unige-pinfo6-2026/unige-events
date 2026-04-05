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

```tsx
// ✅ Correct — const array partagée entre desktop et mobile
const navLinks = [
  { href: '/#events', label: 'En ce moment' },
  { href: '/#faq', label: 'FAQ' },
]
// utilisée dans le rendu desktop ET mobile

// ❌ Interdit — même liste dupliquée dans deux blocs JSX
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

## Contrat API
`openapi/openapi.yaml` est la **source de vérité** (monorepo — fichier unique partagé entre frontend et backend). Avant d'implémenter un service dans `src/services/`, vérifier que l'endpoint existe dans ce fichier et noter les noms de champs exacts retournés.

## Design & UI — "faut que ça claque"

### Référence visuelle
`src/pages/LandingPage.tsx` est la **référence de style** du projet. Toute nouvelle page ou section doit s'aligner sur ce niveau de qualité visuelle : typographie grande et grasse, fonds avec blobs animés, cards glassmorphism, gradients sur les accents.

### Composants utilitaires à utiliser en priorité

| Composant | Import | Usage |
|---|---|---|
| `ButtonPrimary`, `ButtonSecondary` | `@/components/utils/Buttons` | Tous les boutons d'action |
| `BlobsHero`, `BlobsSubtle`, `BlobsCta`, `Blobs` | `@/components/utils/Blobs` | Fonds décoratifs de sections |
| `SectionWrapper` | inline dans la page (pattern à reproduire) | Wrapper standard de section |
| `SectionHeader` | inline dans la page (pattern à reproduire) | Titre + sous-titre de section |
| `Marquee` | `@/components/utils/Marquee` | Défilement horizontal |
| `FormField` | `@/components/utils/FormField` | Champs de formulaire |
| `Toast` | `@/components/utils/Toast` | Notifications |

### Icônes — lucide-react uniquement
Toujours utiliser `lucide-react` pour les icônes. Ne jamais utiliser d'autres librairies d'icônes ni des SVG inline ad hoc.

### Pattern de section
Chaque grande section de page suit ce pattern :
```tsx
<SectionWrapper id="mon-id" className="py-20 lg:py-32 bg-foreground/2" background={<BlobsSubtle />}>
  <SectionHeader title="Titre" subtitle="Sous-titre optionnel" />
  {/* contenu */}
</SectionWrapper>
```

### Typographie
- Titres héros : `text-5xl lg:text-7xl font-bold tracking-tight leading-[0.95]`
- Titres de sections : `text-5xl lg:text-7xl font-bold tracking-tight`
- Corps : `text-xl text-foreground/60 font-light leading-relaxed`
- Gradient sur mot-clé accent : `<span className="text-accent-gradient">mot</span>`

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