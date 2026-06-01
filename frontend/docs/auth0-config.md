# Auth0 Configuration — UNIGE Events

Réglages Auth0 Dashboard requis pour tous les environnements (dev, staging, prod).

## Application SPA — Settings

**Applications > unige-events-spa > Settings**

| Paramètre | Valeur |
|---|---|
| Application Type | Single Page Application |
| Allowed Callback URLs | `http://localhost:5173/login/callback` (dev), `https://<prod-domain>/login/callback` (prod) |
| Allowed Logout URLs | `http://localhost:5173` (dev), `https://<prod-domain>` (prod) |
| Allowed Web Origins | `http://localhost:5173` (dev), `https://<prod-domain>` (prod) |

### Refresh Token Rotation (onglet "Refresh Token Rotation")

| Paramètre | Valeur | Raison |
|---|---|---|
| Rotation | **Activé** | Chaque usage du refresh token émet un nouveau token ; l'ancien est révoqué. Atténue le vol de refresh token. |
| Reuse Interval | 0 s (recommandé) | |
| Absolute Expiration | **Activé** — 30 jours | Durée de vie maximale absolue d'une session (force re-login après 30 j). |
| Absolute Lifetime | 2 592 000 s (30 jours) | |
| Inactivity Expiration | **Activé** — 15 jours | Révoque le refresh token si l'utilisateur est inactif 15 jours. |
| Inactivity Lifetime | 1 296 000 s (15 jours) | |

> **Pourquoi ces réglages ?** Le frontend utilise `useRefreshTokens={true}` avec `cacheLocation="localstorage"` dans `AuthProvider.tsx`. Le refresh token (en rotation) est persisté pour maintenir la session après rechargement sur **tous** les navigateurs, y compris ceux bloquant les cookies tiers (Safari ITP, Firefox ETP) où le silent-auth par iframe est inopérant. La rotation révoque chaque token après usage unique, et l'Absolute Expiration limite la durée de vie d'une session volée.

## API — Token Expiration

**APIs > unige-events-api > Settings**

| Paramètre | Valeur | Raison |
|---|---|---|
| Token Expiration (Seconds) | **3 600** (1 heure) | Réduit la fenêtre d'exploitation d'un access token volé (was 86 400 s / 24 h — finding 4.27 pentest 2026-04-17). |
| Token Expiration For Browser Flows (Seconds) | **3 600** | Aligner sur le même TTL pour les SPA. |

## Scope `offline_access`

Le scope `offline_access` est requis pour que Auth0 émette un refresh token.  
Il est déclaré dans `AuthProvider.tsx` dans `authorizationParams.scope` :

```
openid profile email offline_access
```

Ce scope doit être autorisé sur l'API `unige-events-api` (**APIs > unige-events-api > Scopes**) ou, selon le tenant, dans les **permissions** de l'application SPA.

## Contexte sécurité

Ces réglages sont liés aux findings du pentest interne du 2026-04-17 :

- **Finding 4.4** — les tokens stockés dans `localStorage` sont lisibles par tout JavaScript de l'origine (risque en cas de XSS). Le cache mémoire seul (`cacheLocation` retiré) a été testé mais casse la persistance de session au rechargement sur les navigateurs bloquant les cookies tiers (le refresh token, stocké en mémoire, est perdu et le fallback iframe est inutilisable). Compromis retenu : conserver `cacheLocation="localstorage"` avec **refresh token rotation** (jetons à usage unique, révoqués après usage) et un TTL access token court (1 h), ce qui réduit fortement l'impact d'un vol de token via XSS tout en garantissant la persistance cross-navigateur.
- **Finding 4.27** — TTL de l'access token à 86 400 s (24 h) donnait 24 h d'accès à un token volé sans mécanisme de révocation côté frontend. Résolu en baissant le TTL à 3 600 s (1 h).

## Vérification

La persistance de session se vérifie en rechargeant la page après login : l'utilisateur doit rester connecté (le refresh token en rotation rafraîchit l'access token via `getAccessTokenSilently()`).

Le TTL de l'access token est vérifiable en décodant le JWT :

```js
const token = /* getAccessTokenSilently() */
const { exp, iat } = JSON.parse(atob(token.split('.')[1]))
console.log(exp - iat) // doit être 3600
```
