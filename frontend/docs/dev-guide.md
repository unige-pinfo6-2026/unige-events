# docs/dev-guide.md — Guide de démarrage et workflows

## Prérequis

- Node.js 24+
- npm 10+
- Docker + Docker Compose (pour le dev avec le backend)
- Un compte Auth0 configuré (voir `.env.example`)

---

## Installation

```bash
git clone <repo-url> unige-events-web
cd unige-events-web
npm install
cp .env.example .env   # puis remplir VITE_AUTH0_DOMAIN, VITE_AUTH0_CLIENT_ID, VITE_AUTH0_AUDIENCE
```

---

## Lancement dev local

### Avec Docker Compose (recommandé — backend inclus)

```bash
docker compose up
# Frontend : http://localhost:5173
# API proxiée via Vite sur /api → http://api:8080
```

### Frontend seul (si le backend tourne séparément)

```bash
npm run dev
# http://localhost:5173
# Vite proxie /api → http://api:${API_PORT|8080}
```

---

## Commandes

```bash
npm run dev          # dev local (Vite, proxy /api → api:8080)
npm run build        # build production (tsc -b && vite build)
npm run lint         # ESLint + TypeScript checks
npm run test         # tests unitaires (Vitest)
npm run test:coverage # couverture V8 (lcov + text)
npm run preview      # preview du build prod en local
```

---

## Variables d'environnement

| Variable | Requis | Description |
|---|---|---|
| `VITE_AUTH0_DOMAIN` | Oui | Domaine Auth0 (ex: `my-tenant.auth0.com`) |
| `VITE_AUTH0_CLIENT_ID` | Oui | Client ID Auth0 de la SPA |
| `VITE_AUTH0_AUDIENCE` | Non | Audience API Auth0 (pour les claims) |
| `API_PORT` | Non | Port du backend (défaut `8080`) |
| `APP_PORT` | Non | Port du frontend dev (défaut `5173`) |

Les variables `VITE_*` sont accessibles dans le code via `import.meta.env.VITE_VARNAME`.

---

## Activer les hooks Git

```bash
git config core.hooksPath .github/hooks
```

À faire une seule fois par machine. Les hooks vérifient les conventions de code (snake_case interdit, booléens sans `is`, doc à jour).

---

## Workflow : ajouter une nouvelle page

1. Vérifier l'endpoint dans `docs/openapi/openapi.yaml` — noter les noms de champs exacts
2. Ajouter le type dans `src/types/index.ts` si nécessaire
3. Créer le service dans `src/services/` (utiliser l'instance `api` de `services/api.ts`)
4. Créer le composant page dans `src/pages/NomPage.tsx`
5. Ajouter la route dans `src/router/AppRouter.tsx`
6. Mettre à jour `docs/components.md` (page) + `docs/architecture.md` (table de routage)

---

## Workflow : ajouter un appel API

1. Vérifier l'endpoint dans `docs/openapi/openapi.yaml`
2. Noter les noms de champs exacts (camelCase, booléens sans `is`)
3. Ajouter/mettre à jour le type dans `src/types/index.ts`
4. Ajouter la fonction dans `src/services/` — **utiliser `api` depuis `services/api.ts`**
5. Mettre à jour `docs/types.md` + `docs/components.md` (section services)

```typescript
// Exemple : ajouter getEvents dans eventService.ts
import api from './api'
import type { Event, PagedEvents } from '../types'

export async function getEvents(params?: { category?: string; upcoming?: boolean }): Promise<PagedEvents> {
  const response = await api.get<PagedEvents>('/events', { params })
  return response.data
}
```

---

## DRY — Don't Repeat Yourself

Ne jamais dupliquer des données ou de la logique. Si la même liste ou structure apparaît à deux endroits, l'extraire :

- **Données statiques** (liens, options, labels) → const array typée déclarée hors du composant, réutilisée partout
- **Structure JSX répétée** → composant extrait dès la 2e occurrence
- **Logique répétée** → hook custom ou fonction utilitaire dans `src/services/` ou `src/hooks/`

```tsx
// ✅ Correct
const navLinks = [{ href: '/#events', label: 'En ce moment' }, …]
// rendu desktop : navLinks.map(…)
// rendu mobile  : navLinks.map(…)

// ❌ Interdit
// <a href="/#events">En ce moment</a>  ← dans le bloc desktop
// <a href="/#events">En ce moment</a>  ← répété dans le bloc mobile
```

---

## Pattern variants — const maps typées

Pour tout composant avec des variantes visuelles, ne jamais utiliser de ternaires inline. Déclarer des const maps typées :

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
const cls = type === 'success' ? '…' : '…'
```

Références : `src/components/utils/Blobs.tsx` (colors, sizes, positions), `src/components/utils/Toast.tsx` (variants).

---

## Workflow : créer un composant réutilisable

1. Identifier si le composant est déjà dans `src/components/` ou listé dans `docs/components.md` section "à extraire"
2. Créer le fichier dans `src/components/NomComposant.tsx`
3. Typer toutes les props (pas de `any`)
4. Utiliser les CSS existants ou créer un `.css` dédié (pas de styles inline pour les composants)
5. Mettre à jour `docs/components.md`

---

## Gestion des états dans les composants

Toujours gérer les trois états pour tout appel API :

```typescript
// Pattern obligatoire
const [data, setData] = useState<MyType | null>(null)
const [loading, setLoading] = useState(true)
const [error, setError] = useState<string | null>(null)

useEffect(() => {
  myService.getData()
    .then(setData)
    .catch(() => setError('Erreur de chargement'))
    .finally(() => setLoading(false))
}, [])

if (loading) return <div className="spinner" />
if (error) return <div className="error-message">{error}</div>
if (!data) return null
return <ActualComponent data={data} />
```

Ne jamais afficher une page avec des données `undefined`, `null`, ou des `?` à la place de valeurs.

---

## Conventions de style

Le projet utilise **TailwindCSS v4** avec un thème personnalisé défini dans `src/index.css` via `@theme`. Ne pas introduire de styles inline pour la logique de layout.

### Design tokens disponibles (à utiliser en priorité sur les valeurs Tailwind brutes)

| Token CSS | Classe Tailwind | Usage |
|---|---|---|
| `--color-primary` | `text-primary`, `bg-primary`, `border-primary` | Couleur principale de la marque |
| `--color-accent` | `text-accent`, `bg-accent`, `border-accent` | Accentuation, liens actifs, focus rings |
| `--color-background` | `bg-background` | Fond de page et de cards |
| `--color-foreground` | `text-foreground` | Texte principal (avec opacité : `/60`, `/40`, etc.) |
| `--color-border` | `border-border` | Bordures standard |
| `--color-error` | `text-error`, `border-error`, `bg-error` | Messages d'erreur, champs invalides, feedback négatif |
| `--color-overlay` | — | Fond semi-transparent pour modales |
| `--font-primary` | `font-primary` | Police principale Inter |
| `--height-navbar` | `h-navbar` | Hauteur fixe de la navbar |

**Règle** : utiliser `text-error` / `border-error` pour toutes les erreurs de validation, messages d'échec, et états invalides — **jamais `red-400` ou autre valeur Tailwind brute**.

Le mode sombre (`[data-theme="dark"]`) surcharge automatiquement `--color-background`, `--color-foreground`, `--color-overlay` et `--color-border`.

---

## Afficher un toast

Utiliser `useToast` depuis n'importe quel composant ou page :

```typescript
import { useToast } from '@/contexts/ToastContext'

const { showToast } = useToast()

showToast('success', 'Profil mis à jour.')
showToast('error', 'Une erreur est survenue.')
showToast('success', 'Message court.', 3000) // durée optionnelle en ms (défaut 5000)
```

Les toasts sont gérés au niveau de l'application (`ToastProvider` > `Layout`) et **persistent lors d'une navigation**. Il est donc correct d'appeler `showToast` puis `navigate()` immédiatement — le toast s'affichera bien sur la page de destination.

```typescript
// ✅ Correct — le toast survit à la navigation
showToast('success', 'Événement créé.')
navigate(`/events/${event.id}`)

// ❌ Inutile — pas besoin de délai pour "laisser le temps" au toast
setTimeout(() => navigate(`/events/${event.id}`), 1000)
```

Plusieurs toasts peuvent être empilés simultanément. Chaque toast se ferme automatiquement après sa durée, ou immédiatement au clic.

---

## Architecture des contextes

```
main.tsx
  └─ BrowserRouter
       └─ Auth0ProviderWithNavigate   ← initialise Auth0, gère onRedirectCallback
            └─ ThemeProvider          ← thème dark/light, persiste en localStorage
                 └─ ToastProvider     ← file de toasts globale, showToast()
                      └─ AuthProvider ← charge User via GET /api/users/me
                           └─ AppRouter ← routes React Router
```

Ne pas réorganiser cet ordre de providers — Auth0 doit être dans le BrowserRouter, et `ToastProvider` doit envelopper `AuthProvider` (qui utilise `showToast` pour les erreurs d'auth).

---

## Structure des dossiers

```
src/
├── components/     # Composants réutilisables (Layout, Navbar, PrivateRoute, Logo…)
├── contexts/       # AuthContext, ThemeContext, ToastContext
├── hooks/          # useAuth
├── pages/          # Une page = un fichier (LoginPage, HomePage, ProfilePage…)
├── router/         # AppRouter.tsx
├── services/       # api.ts, tokenStore.ts, userService.ts
├── types/          # index.ts — tous les types TypeScript du domaine
└── main.tsx        # Entrée de l'application
```
