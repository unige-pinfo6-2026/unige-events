# SPEC — Boneyard : skeleton screens automatiques sur UNIGE Events

> Auteur : Claude  
> Date : 2026-04-12  
> Branche : `rework/events-create-edit` (courante) ou branche dédiée  
> Statut : **À implémenter**

---

## 1. Vue d'ensemble

### Objectif

Intégrer **Boneyard** (`boneyard-js`) pour remplacer les spinners de chargement par des skeleton screens pixel-perfect sur les 3 composants les plus visibles de l'app :

1. `EventCards` — grille de cards sur la landing page (zone la plus visible)
2. `EventDetailPage` — page de détail d'un événement
3. `ProfilePage` — page de profil utilisateur

### Principe de fonctionnement à l'exécution

Les skeleton screens bénéficient aux **utilisateurs déjà connectés** pendant la phase de chargement API. Auth0 et Boneyard opèrent à deux niveaux séparés :

```
Utilisateur connecté
       ↓
PrivateRoute valide isAuthenticated → true
       ↓
Page monte, loading = true → [SKELETON affiché]
       ↓
Hook fire l'appel API en background
       ↓
loading = false → [CONTENU RÉEL remplace le skeleton]
```

Le skeleton ne remplace pas Auth0 — il remplace le spinner qui apparaît entre la validation de la route et la fin du fetch.

### Stratégie auth (CLI Boneyard)

Toutes les pages cibles sont protégées par `PrivateRoute`. Le CLI Boneyard tourne en browser headless et ne peut pas se connecter. Solution : **`fixture` prop** — un JSX statique fourni au CLI pour capturer le layout réel. La `fixture` n'est **jamais rendue en production** (flag CLI-only).

### Stratégie CI/CD

Les fichiers `src/bones/` sont **committés dans le repo** — traités comme des assets versionnés, au même titre que des snapshots de test visuel. Pas de régénération en CI (évite Playwright en CI, +200 MB, instabilité). Régénérer manuellement via `npx boneyard-js build` uniquement quand un layout ciblé change, dans le même commit.

---

## 2. Périmètre des fichiers

### Fichiers modifiés

| Fichier | Modification |
|---|---|
| `package.json` | Ajout dépendance `boneyard-js` |
| `vite.config.ts` | Ajout `boneyardPlugin()` dans `plugins` |
| `src/main.tsx` | Import `'./bones/registry'` |
| `src/components/utils/Skeleton.tsx` | Renommage export `Skeleton` → `SkeletonBlock` |
| `src/components/Navbar.tsx` | Import + usage `SkeletonBlock` |
| `src/components/event/EventCards.tsx` | Fixture locale + wrapping Boneyard |
| `src/pages/event/EventDetailPage.tsx` | Fixture locale + remplacement `LoadingSpinner` |
| `src/pages/profile/ProfilePage.tsx` | Fixture locale + remplacement `LoadingSpinner` |
| `docs/sprint-context.md` | Mise à jour |
| `docs/components.md` | `SkeletonBlock` + note Boneyard |

### Fichiers créés

| Fichier | Description |
|---|---|
| `boneyard.config.json` | Config CLI (breakpoints forcés pour Tailwind v4) |
| `src/bones/event-cards.bones.json` | Généré par CLI, commité |
| `src/bones/event-detail.bones.json` | Généré par CLI, commité |
| `src/bones/profile.bones.json` | Généré par CLI, commité |
| `src/bones/registry.js` | Généré par CLI, commité |

### Ce qui ne change PAS

- Logique des hooks (`useEvents`, `useEvent`, `useAttendance`, etc.)
- Services, types, contexts, router
- Contenu rendu de `EventCard`, `EventDetailPage`, `ProfilePage` (inchangés)
- Tests existants (les fixtures sont CLI-only, non testables)
- `LoadingSpinner` — conservé, toujours utilisé dans `PrivateRoute` et `LoadingPage`

---

## 3. Installation et configuration

### 3.1 — Dépendance npm

```bash
npm install boneyard-js
```

### 3.2 — `boneyard.config.json`

Créer à la racine de `frontend/` :

```json
{
  "breakpoints": [375, 768, 1024, 1280],
  "out": "src/bones"
}
```

> **Pourquoi les breakpoints sont forcés** : Tailwind v4 déclare le thème via `@theme` dans le CSS (`src/index.css`) — il n'existe pas de `tailwind.config.js`. La détection automatique des breakpoints du CLI échoue donc. Ces 4 valeurs couvrent mobile (375), tablet (768), desktop étroit (1024) et large (1280).

### 3.3 — Plugin Vite

**Fichier :** `frontend/vite.config.ts`

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import { boneyardPlugin } from 'boneyard-js/vite'
import path from 'node:path'

export default defineConfig(() => {
  return {
    plugins: [react(), tailwindcss(), boneyardPlugin()],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
    server: {
      port: 5173,
      host: '0.0.0.0',
      strictPort: true,
      watch: {
        usePolling: true,
      },
      proxy: {
        '/api': {
          target: `http://localhost:8080/`,
          changeOrigin: true
        }
      }
    },
    test: {
      coverage: {
        provider: 'v8',
        reporter: ['text', 'lcov'],
      }
    }
  }
})
```

### 3.4 — Import du registry

**Fichier :** `frontend/src/main.tsx`

```tsx
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import './index.css'
import './bones/registry'
import App from './App.tsx'
import AuthProvider from './components/auth/AuthProvider.tsx'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
```

> **Note TypeScript** : si `tsc -b` signale une erreur sur l'import du `registry.js`, ajouter `"allowJs": true` dans `tsconfig.app.json`. Le plugin Vite gère l'import à la compilation — cette correction n'est généralement pas nécessaire.

---

## 4. Renommage `Skeleton` → `SkeletonBlock`

Le composant `Skeleton` maison entre en conflit de nom avec le `<Skeleton>` exporté par `boneyard-js/react`. Renommage minimal.

### 4.1 — `src/components/utils/Skeleton.tsx`

```tsx
export function SkeletonBlock({ className = '' }: Readonly<{ className?: string }>) {
  return <div className={`rounded-xl bg-foreground/10 animate-pulse ${className}`} />
}
```

### 4.2 — `src/components/Navbar.tsx`

Deux modifications (lignes 7 et 156) :

```tsx
// Ligne 7 — avant
import { Skeleton } from './utils/Skeleton'

// Ligne 7 — après
import { SkeletonBlock } from './utils/Skeleton'
```

```tsx
// Ligne 156 — avant
{isLoading && <Skeleton className="h-9 w-28" />}

// Ligne 156 — après
{isLoading && <SkeletonBlock className="h-9 w-28" />}
```

---

## 5. Composant 1 — `EventCards`

**Fichier :** `src/components/event/EventCards.tsx`

### Changement

Remplacer `if(loading && events.length === 0) return <LoadingSpinner/>` par un `<Skeleton>` Boneyard avec une fixture locale. Le reste du composant (grille, pagination, erreur, état vide) est **inchangé**.

### Fixture

La fixture reproduit la structure DOM exacte de `EventCard` : banner `h-52`, badge catégorie, titre en overlay, content avec 2 meta rows et description. 6 cards dans la même grille `auto-fit`.

### Code complet du fichier

```tsx
import { Skeleton } from 'boneyard-js/react'
import { useEvents } from '@/hooks/useEvents'
import EventCard from './EventCard'
import { InfoMessage } from '../utils/InfoMessage'

function EventCardsFixture() {
  return (
    <div className="flex flex-col gap-8">
      <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,320px))] justify-center gap-5">
        {Array.from({ length: 6 }).map((_, i) => (
          <article key={i} className="relative bg-background border border-border rounded-3xl overflow-hidden">
            {/* Accent line */}
            <div className="absolute top-0 left-0 right-0 h-0.5 bg-foreground/10 z-10" />
            {/* Banner */}
            <div className="relative h-52 bg-foreground/10">
              <span className="absolute top-4 left-4 h-6 w-20 rounded-full bg-foreground/20" />
              <div className="absolute bottom-0 left-0 right-0 p-5 flex flex-col gap-1.5">
                <div className="h-5 w-4/5 rounded bg-foreground/20" />
                <div className="h-5 w-3/5 rounded bg-foreground/20" />
              </div>
            </div>
            {/* Content */}
            <div className="p-5 flex flex-col gap-3">
              <div className="flex flex-col gap-2">
                <div className="flex items-center gap-2.5">
                  <div className="w-4 h-4 rounded bg-foreground/10 shrink-0" />
                  <div className="h-4 w-36 rounded bg-foreground/10" />
                </div>
                <div className="flex items-center gap-2.5">
                  <div className="w-4 h-4 rounded bg-foreground/10 shrink-0" />
                  <div className="h-4 w-28 rounded bg-foreground/10" />
                </div>
              </div>
              <div className="border-t border-border" />
              <div className="flex flex-col gap-1.5">
                <div className="h-3.5 w-full rounded bg-foreground/10" />
                <div className="h-3.5 w-4/5 rounded bg-foreground/10" />
              </div>
            </div>
          </article>
        ))}
      </div>
    </div>
  )
}

export default function EventCards() {
  const { events, loading, error, hasMore, loadMore } = useEvents()

  if (loading && events.length === 0) return (
    <Skeleton
      name="event-cards"
      loading={true}
      fixture={<EventCardsFixture />}
      animate="pulse"
      color="rgba(0,0,0,0.08)"
      darkColor="rgba(255,255,255,0.06)"
      snapshotConfig={{ excludeTags: ['svg'] }}
    />
  )

  if (error) return <InfoMessage type='error' message={error} />

  if (events.length === 0) return <InfoMessage type='info' message={"Aucun événement publié pour le moment."} />

  return (
    <div className="flex flex-col gap-8">
      <div className="grid grid-cols-[repeat(auto-fit,minmax(280px,320px))] justify-center gap-5">
        {events.map(event => (
          <EventCard key={event.id} event={event} />
        ))}
      </div>

      {(hasMore || loading) && (
        <div className="flex justify-center">
          <button
            type="button"
            onClick={loadMore}
            disabled={loading}
            className={[
              'px-8 py-3 rounded-xl font-semibold text-sm border transition-all',
              loading
                ? 'border-border text-foreground/40 cursor-not-allowed'
                : 'border-transparent bg-linear-to-r from-accent to-pink-600 text-white shadow-lg shadow-accent/20 hover:shadow-accent/30 cursor-pointer',
            ].join(' ')}
          >
            {loading ? 'Chargement…' : 'Charger plus'}
          </button>
        </div>
      )}
    </div>
  )
}
```

> **Note** : l'import `LoadingSpinner` est supprimé de ce fichier — il n'est plus utilisé ici. L'import `@/hooks/useEvents` passe de chemin relatif long à alias `@/` conforme à AGENTS.md.

---

## 6. Composant 2 — `EventDetailPage`

**Fichier :** `src/pages/event/EventDetailPage.tsx`

### Changement

Remplacer **uniquement** la ligne 50 (`if (loading) return <LoadingSpinner/>`). Tout le reste du fichier — logique, gestion d'erreurs, JSX du contenu, modal de confirmation — est **inchangé**.

### Fixture

La fixture reproduit le layout réel : bannière `h-72 rounded-3xl`, main card avec ses 4 meta rows + description, attendance card `rounded-3xl px-7 py-5`, bouton ICS.

### Ajout en haut du fichier

Ajouter l'import Boneyard après les imports existants :

```tsx
import { Skeleton } from 'boneyard-js/react'
```

### Fonction fixture (non-exportée, dans le même fichier, avant `EventDetailPage`)

```tsx
function EventDetailFixture() {
  return (
    <div className="max-w-3xl mx-auto flex flex-col gap-5">
      {/* Banner */}
      <div className="relative h-72 rounded-3xl overflow-hidden bg-foreground/10">
        <span className="absolute top-4 left-4 h-6 w-24 rounded-full bg-foreground/20" />
        <div className="absolute bottom-6 left-6 right-6">
          <div className="h-7 w-3/4 rounded bg-foreground/20" />
        </div>
      </div>

      {/* Main card */}
      <div className="bg-background border border-border rounded-3xl p-7 flex flex-col gap-6">
        {/* Meta rows */}
        <div className="flex flex-col gap-3">
          <div className="flex items-center gap-3">
            <div className="w-4 h-4 rounded bg-foreground/10 shrink-0" />
            <div className="h-4 w-64 rounded bg-foreground/10" />
          </div>
          <div className="flex items-center gap-3">
            <div className="w-4 h-4 rounded bg-foreground/10 shrink-0" />
            <div className="h-4 w-48 rounded bg-foreground/10" />
          </div>
          <div className="flex items-center gap-3">
            <div className="w-4 h-4 rounded bg-foreground/10 shrink-0" />
            <div className="h-4 w-32 rounded bg-foreground/10" />
          </div>
          <div className="flex items-center gap-3">
            <div className="w-9 h-9 rounded-full bg-foreground/10 shrink-0" />
            <div className="h-4 w-40 rounded bg-foreground/10" />
          </div>
        </div>
        {/* Description */}
        <div className="border-t border-border" />
        <div className="flex flex-col gap-2">
          <div className="h-4 w-full rounded bg-foreground/10" />
          <div className="h-4 w-11/12 rounded bg-foreground/10" />
          <div className="h-4 w-4/5 rounded bg-foreground/10" />
          <div className="h-4 w-2/3 rounded bg-foreground/10" />
        </div>
      </div>

      {/* Attendance card */}
      <div className="bg-background border border-border rounded-3xl px-7 py-5">
        <div className="flex gap-3">
          <div className="h-10 w-36 rounded-xl bg-foreground/10" />
          <div className="h-10 w-36 rounded-xl bg-foreground/10" />
        </div>
      </div>

      {/* ICS export button */}
      <div className="h-12 rounded-2xl bg-foreground/10" />
    </div>
  )
}
```

### Remplacement (ligne 50)

```tsx
// Avant
if (loading) return <LoadingSpinner/>

// Après
if (loading) return (
  <Skeleton
    name="event-detail"
    loading={true}
    fixture={<EventDetailFixture />}
    animate="pulse"
    color="rgba(0,0,0,0.08)"
    darkColor="rgba(255,255,255,0.06)"
    snapshotConfig={{ excludeTags: ['svg'] }}
  />
)
```

Vérifier si `LoadingSpinner` est encore utilisé ailleurs dans `EventDetailPage`. Si non, supprimer son import.

---

## 7. Composant 3 — `ProfilePage`

**Fichier :** `src/pages/profile/ProfilePage.tsx`

### Changement

Remplacer **uniquement** la ligne 59 (`if (loading) return <LoadingSpinner />`). Tout le reste — logique, profil privé, JSX du contenu — est **inchangé**.

### Ajout en haut du fichier

```tsx
import { Skeleton } from 'boneyard-js/react'
```

### Fonction fixture (non-exportée, dans le même fichier, avant `ProfilePage`)

La fixture reproduit : bannière `h-52`, bloc avatar 112px + nom/sous-titre, about card avec mail/study level/faculty logo, calendar card — grille `lg:grid-cols-2`.

```tsx
function ProfileFixture() {
  return (
    <div>
      {/* Banner */}
      <div className="relative h-52 overflow-hidden bg-foreground/10" />

      <div className="max-w-5xl mx-auto px-6 lg:px-8 pb-20">
        {/* Header: avatar + name + edit button */}
        <div className="relative -mt-14 flex flex-wrap items-end justify-between gap-4 mb-8">
          <div className="flex items-end gap-5">
            {/* Avatar 112px */}
            <div className="relative shrink-0 w-28 h-28 rounded-full bg-foreground/10 ring-4 ring-background" />
            <div className="pb-2 flex flex-col gap-2">
              <div className="h-9 w-52 rounded bg-foreground/10" />
              <div className="h-4 w-36 rounded bg-foreground/10" />
            </div>
          </div>
        </div>

        {/* Content grid */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* About card */}
          <div className="flex flex-col gap-6">
            <div className="bg-background border border-border rounded-3xl p-6">
              <div className="h-3 w-16 rounded bg-foreground/10 mb-5" />
              <div className="flex flex-col gap-4">
                <div className="flex items-center gap-3">
                  <div className="w-5 h-5 rounded bg-foreground/10 shrink-0" />
                  <div className="h-4 w-52 rounded bg-foreground/10" />
                </div>
                <div className="flex items-center gap-3">
                  <div className="w-5 h-5 rounded bg-foreground/10 shrink-0" />
                  <div className="h-4 w-40 rounded bg-foreground/10" />
                </div>
                <div className="h-12 w-32 rounded bg-foreground/10" />
              </div>
            </div>
          </div>

          {/* Calendar card */}
          <div className="bg-background border border-border rounded-3xl p-6 h-48" />
        </div>
      </div>
    </div>
  )
}
```

### Remplacement (ligne 59)

```tsx
// Avant
if (loading) return <LoadingSpinner />

// Après
if (loading) return (
  <Skeleton
    name="profile"
    loading={true}
    fixture={<ProfileFixture />}
    animate="pulse"
    color="rgba(0,0,0,0.08)"
    darkColor="rgba(255,255,255,0.06)"
    snapshotConfig={{ excludeTags: ['svg'] }}
  />
)
```

Vérifier si `LoadingSpinner` est encore utilisé ailleurs dans `ProfilePage`. Si non, supprimer son import.

---

## 8. Génération des bones et commit

### 8.1 — Pré-requis

Le dev server doit tourner. Le backend n'est **pas** nécessaire — Boneyard utilise les `fixture` props statiques, pas les vraies données API.

```bash
cd frontend
npm run dev   # port 5173
```

### 8.2 — Génération

Dans un autre terminal :

```bash
cd frontend
npx boneyard-js build
```

Le CLI détecte automatiquement le dev server sur le port 5173. Il visite les routes contenant des `<Skeleton>`, rend les fixtures, capture les positions DOM via `getBoundingClientRect()` et écrit les bones.

### 8.3 — Fichiers générés

```
frontend/src/bones/
├── event-cards.bones.json
├── event-detail.bones.json
├── profile.bones.json
└── registry.js
```

### 8.4 — Vérification du build

```bash
npm run build   # doit passer — les bones sont bundlées via l'import registry dans main.tsx
```

### 8.5 — Commit

Committer le dossier `src/bones/` complet. Vérifier que `.gitignore` ne l'exclut pas (il ne devrait pas — `src/` n'est pas ignoré).

> **Règle de régénération** : si le layout d'un composant wrappé change structurellement (ex: ajout d'une section dans la main card d'`EventDetailPage`), relancer `npx boneyard-js build` et committer les nouveaux `.bones.json` dans le même commit que le changement de layout. Les builds sont incrémentaux — seuls les composants modifiés sont recapturés.

### 8.6 — Mode dark (note)

Boneyard utilise `darkColor` conjointement à `prefers-color-scheme: dark`. Notre projet utilise `[data-theme="dark"]` (attribut custom). Les bones s'afficheront donc toujours avec `color` (mode clair) quelle que soit le thème choisi. C'est un détail cosmétique acceptable pour une V1 — les rectangles skeleton restent discrets dans les deux modes.

---

## 9. Documentation

### `docs/sprint-context.md`

Ajouter dans la section sprint la plus récente :

```markdown
- [x] Skeleton screens Boneyard — EventCards, EventDetailPage, ProfilePage
```

### `docs/components.md`

Section composants utils :
- Renommer `Skeleton` → `SkeletonBlock` dans la description et les exemples d'usage
- Ajouter une note :

> `SkeletonBlock` : rectangle pulse simple (Tailwind) — usage : placeholders inline (ex: Navbar)  
> Pour les skeleton screens de page complète, utiliser `<Skeleton>` de `boneyard-js/react` (cf. EventCards, EventDetailPage, ProfilePage)

---

## 10. Résumé des fichiers touchés

| Fichier | Action |
|---|---|
| `package.json` | `+boneyard-js` |
| `boneyard.config.json` | **Nouveau** — breakpoints + out |
| `vite.config.ts` | `+boneyardPlugin()` |
| `src/main.tsx` | `+import './bones/registry'` |
| `src/components/utils/Skeleton.tsx` | `Skeleton` → `SkeletonBlock` |
| `src/components/Navbar.tsx` | Import + usage `SkeletonBlock` |
| `src/components/event/EventCards.tsx` | Fixture + wrapping (suppression `LoadingSpinner`) |
| `src/pages/event/EventDetailPage.tsx` | Fixture + remplacement `LoadingSpinner` |
| `src/pages/profile/ProfilePage.tsx` | Fixture + remplacement `LoadingSpinner` |
| `src/bones/*.bones.json` + `registry.js` | **Généré localement + commité** |
| `docs/sprint-context.md` | Mise à jour |
| `docs/components.md` | `SkeletonBlock` + note Boneyard |

---

## 11. Critères de validation

### Fonctionnels

- [ ] `npm run build` passe sans erreur avec les bones committées
- [ ] `npm run lint` passe — pas de `any`, imports `@/` corrects, pas d'import non utilisé
- [ ] Sur la landing page (utilisateur connecté), la grille affiche 6 rectangles skeleton pendant le fetch initial, puis les vraies cards apparaissent sans layout shift
- [ ] Sur `/events/:id`, skeleton bannière + card + attendance apparaît le temps du fetch, puis le contenu réel
- [ ] Sur `/profile/:id`, skeleton bannière + avatar + cards apparaît le temps du fetch, puis le contenu réel
- [ ] `LoadingSpinner` n'apparaît plus sur ces 3 composants — mais reste fonctionnel dans `PrivateRoute` et `LoadingPage`
- [ ] La Navbar continue d'afficher `SkeletonBlock` (pulse) pendant `isLoading` Auth0
- [ ] Le "Charger plus" dans `EventCards` fonctionne toujours (pagination indépendante du skeleton)
- [ ] Les états d'erreur et d'état vide (`InfoMessage`) fonctionnent toujours

### Qualité SonarCloud

- Duplication ≤ 3% — les fixtures sont du JSX structurellement répétitif mais non-dupliqué (3 composants différents, non-exportés)
- Reliability Rating A
- Maintainability Rating A
- Security Rating A

### Non-régressions

- [ ] `npm run test` passe — les tests existants ne testent pas les états `loading` des pages, pas d'impact attendu
- [ ] L'app fonctionne identiquement en prod (Docker + Nginx) — les bones sont bundlées dans `dist/`
