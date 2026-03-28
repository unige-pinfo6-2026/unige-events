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
`docs/openapi/openapi.yaml` est la **source de vérité**. Avant d'implémenter un service dans `src/services/`, vérifier que l'endpoint existe dans ce fichier et noter les noms de champs exacts retournés.

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
- `docs/openapi/openapi.yaml` — contrat API (copie synchronisée depuis le repo backend)
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
| `openapi.yaml` mis à jour côté backend | Copier le fichier dans `docs/openapi/openapi.yaml` |
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