# Spécification technique — Fix : toast de validation (formulaire événement) + cloche/notifs gated sur l'auth

> Branche : `fix/error-pages` (ajout à la PR #240)
> Statut : **analyse + plan — en attente de validation design**
> Date : 2026-06-03
> Périmètre : **frontend uniquement** (`useEventForm`, `Navbar`).

## Bug 1 — Pas de toast quand la validation du formulaire échoue

### Symptôme
À la création d'un événement, si un champ requis manque, cliquer « Créer l'événement » ne produit **aucun feedback visible** (toast). L'utilisateur reste à recliquer sans comprendre ce qui bloque.

### Cause racine
`submitForm` ([useEventForm.ts:699](../../frontend/src/hooks/useEventForm.ts)) :
```js
if (!validate()) {
  return            // ← pose les erreurs inline (setErrors) puis RETURN en silence
}
```
`validate()` remplit `errors` (affichés sous chaque champ) mais `submitForm` **return sans appeler `onError`** → le `showToast('error', …)` câblé par `EventCreatePage` ([:97](../../frontend/src/pages/event/EventCreatePage.tsx)) et `EventEditPage` ([:151](../../frontend/src/pages/event/EventEditPage.tsx)) n'est jamais déclenché. Si les erreurs inline sont sous le pli, l'utilisateur ne les voit pas.

### Fix
- `validate()` renvoie l'objet d'erreurs (au lieu d'un booléen) pour que `submitForm` puisse construire un message.
- Dans `submitForm`, sur échec de validation : appeler `onError?.(message)` avant le `return`.
- Message listant les champs à corriger via `FIELD_LABELS` (ajouter les clés manquantes : `recurrence`). Ex. : « Veuillez compléter ou corriger : Le titre, Le lieu, La catégorie. »
- (option, cf. Q2) faire défiler/focus vers le premier champ en erreur.

## Bug 2 — Requête notifications + cloche affichée hors authentification

### Symptôme
Déconnecté, l'app appelle quand même `GET /api/users/me/notifications` (→ 401), et la cloche de notifications s'affiche.

### Cause racine
- `useNotifications` ([useNotifications.ts:47-51](../../frontend/src/hooks/useNotifications.ts)) lance `fetchNotifications()` au montage **+ un polling** `setInterval`, **sans condition d'auth** (il avale le 401 mais la requête part quand même).
- `useNotifications` n'est consommé **que** par `NotificationsDropdown`, lui-même rendu **sans gating** dans la navbar : `<NotificationsDropdown />` ([Navbar.tsx](../../frontend/src/components/Navbar.tsx)), contrairement à `{user && <RequestsInboxDropdown />}` juste à côté.

### Fix
Gater le composant derrière `user` : `{user && <NotificationsDropdown />}`. Conséquence : déconnecté → la cloche disparaît **et** `useNotifications` n'est pas monté → aucune requête ni polling. Reconnexion → la cloche réapparaît (le hook se monte). Cohérent avec `RequestsInboxDropdown`.

## Tests
- **Bug 1** (`useEventForm.test`) : sur submit invalide, `onError` est appelé avec un message contenant les champs fautifs ; `createEvent`/`updateEvent` toujours non appelés ; un submit valide n'appelle pas `onError`.
- **Bug 2** (`Navbar.test`) : déconnecté (`user: null`) → pas de cloche (`useNotifications` non monté / `getNotifications` non appelé) ; connecté → cloche présente.
- Couverture ~100 % sur le code modifié.

## Critères d'acceptation
| # | Scénario | Attendu |
|---|---|---|
| AC-1 | « Créer l'événement » avec titre/lieu/catégorie vides | Toast d'erreur indiquant les champs à compléter |
| AC-2 | Submit valide | Pas de toast d'erreur, création normale |
| AC-3 | Édition avec champ invalide | Même toast (onError partagé) |
| AC-4 | Déconnecté | Aucune requête `/users/me/notifications`, pas de cloche |
| AC-5 | Connecté | Cloche présente, notifications chargées |

## Questions ouvertes (à valider avant de coder)
1. **Contenu du toast (Bug 1)** : lister les champs à corriger (« …compléter ou corriger : Le titre, Le lieu ») **vs** message générique (« Veuillez corriger les champs en surbrillance ») ?
2. **Défilement (Bug 1)** : faire aussi défiler/focus automatiquement vers le premier champ en erreur, en plus du toast ?
