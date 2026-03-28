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

Le projet utilise des **fichiers CSS par composant** (ex: `LoginPage.css`, `Navbar.css`). Ce n'est pas TailwindCSS. Ne pas introduire de styles inline pour la logique de layout.

---

## Architecture des contextes

```
main.tsx
  └─ BrowserRouter
       └─ Auth0ProviderWithNavigate   ← initialise Auth0, gère onRedirectCallback
            └─ ThemeProvider          ← thème dark/light, persiste en localStorage
                 └─ AuthProvider      ← charge User via GET /api/users/me
                      └─ AppRouter    ← routes React Router
```

Ne pas réorganiser cet ordre de providers — Auth0 doit être dans le BrowserRouter.

---

## Structure des dossiers

```
src/
├── components/     # Composants réutilisables (Layout, Navbar, PrivateRoute, Logo…)
├── contexts/       # AuthContext, ThemeContext
├── hooks/          # useAuth
├── pages/          # Une page = un fichier (LoginPage, HomePage, ProfilePage…)
├── router/         # AppRouter.tsx
├── services/       # api.ts, tokenStore.ts, userService.ts
├── types/          # index.ts — tous les types TypeScript du domaine
└── main.tsx        # Entrée de l'application
```
