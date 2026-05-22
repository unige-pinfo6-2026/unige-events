# Spec — Issue #96 · CSP & security headers (nginx)

- **Issue**: [#96](https://github.com/unige-pinfo6-2026/unige-events/issues/96) — `[SECURITY][Medium] Add CSP and missing security headers (nginx)`
- **Pentest finding**: 4.3 (interne, 2026-04-17), sévérité **Medium**
- **Périmètre**: frontend SPA servie par nginx (`frontend/nginx.conf`)
- **Branche**: `claude/lucid-pascal-ozg7v`
- **Rollout retenu**: `Content-Security-Policy-Report-Only` d'abord (monitoring 3–5 j), passage en mode enforce dans un PR de suivi.

---

## 1. Contexte

`GET /` sur la production ne renvoie qu'un seul header de sécurité :

```
strict-transport-security: max-age=31536000; includeSubDomains
```

Ce HSTS est injecté par le **contrôleur ingress-nginx** de MicroK8s (addon `ingress`,
cf. `docs/devops/k8s.md`) — il n'est **pas** présent dans le dépôt. Le reste des
headers de sécurité est absent, ce qui rend le site **clickjackable** (PoC :
`<iframe src="https://pinfo6.duckdns.org">` rend la SPA) et laisse toute XSS
(notamment la faille HIGH #01, SVG XSS) s'exécuter sans restriction résiduelle.

### Topologie des réponses HTTP

```
navigateur ─▶ ingress-nginx (HSTS) ─▶ Service web ─▶ pod nginx (frontend/nginx.conf) ─▶ dist/ SPA
```

ingress-nginx relaie les headers de réponse amont tels quels : les `add_header`
posés dans le pod nginx remontent donc bien sur `GET /`. C'est l'emplacement déjà
utilisé pour les findings pentest 4.6 (`server_tokens off`) et 4.13/4.19
(`client_max_body_size`) — on reste cohérent.

---

## 2. Objectif

Ajouter au niveau `server {}` de `frontend/nginx.conf` les headers manquants :

1. `Content-Security-Policy` (déployé en **Report-Only** dans ce PR)
2. `X-Frame-Options: DENY` + CSP `frame-ancestors 'none'` (anti-clickjacking)
3. `X-Content-Type-Options: nosniff`
4. `Referrer-Policy: strict-origin-when-cross-origin`
5. `Permissions-Policy: geolocation=(), camera=(), microphone=(), payment=()`

HSTS reste géré par l'ingress (pas de double émission).

---

## 3. CSP — directives et justification

La SPA n'est **pas** mono-origine : trois dépendances externes confirmées dans le
code dictent les directives ci-dessous.

| Directive | Valeur | Pourquoi |
|---|---|---|
| `default-src` | `'self'` | base restrictive |
| `script-src` | `'self'` | build Vite = bundles JS externes hashés, pas de script inline |
| `style-src` | `'self' 'unsafe-inline'` | styles inline React + `Blobs` animés (`src/components/utils/Blobs.tsx`) posent des `style=` attributs → `'unsafe-inline'` requis (décision validée) |
| `img-src` | `'self' data: blob: https://lh3.googleusercontent.com https://*.googleusercontent.com` | `'self'`/`/s3` proxy + `data:`/`blob:` (preview crop `EventForm`) + **avatars Google** via Auth0 social login (`src/components/user/UserAvatar.tsx`) |
| `font-src` | `'self' data:` | police Inter bundlée |
| `connect-src` | `'self' https://dev-p8ufbvhr6g61j78w.us.auth0.com` | `/api` même origine + endpoint token/JWKS Auth0 (`getAccessTokenSilently`) |
| `frame-src` | `https://dev-p8ufbvhr6g61j78w.us.auth0.com https://www.google.com` | iframe **silent-auth** Auth0 (`cacheLocation="localstorage"`, sans refresh tokens → renouvellement par iframe) + **Google Maps embed** du Footer (`src/components/Footer.tsx`) |
| `frame-ancestors` | `'none'` | anti-clickjacking (qui peut nous *encadrer*) |
| `base-uri` | `'self'` | bloque l'injection de `<base>` |
| `form-action` | `'self'` | restreint les cibles de `<form>` |
| `object-src` | `'none'` | bloque plugins/embeds legacy |

> ⚠️ Distinction clé : `frame-ancestors` (qui peut nous encadrer — fix clickjacking)
> ≠ `frame-src` (ce que **nous** avons le droit d'encadrer — Auth0 + Maps). Les deux
> sont nécessaires et ne se contredisent pas.

### Valeur CSP (une ligne)

```
default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://lh3.googleusercontent.com https://*.googleusercontent.com; font-src 'self' data:; connect-src 'self' https://dev-p8ufbvhr6g61j78w.us.auth0.com; frame-src https://dev-p8ufbvhr6g61j78w.us.auth0.com https://www.google.com; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'
```

---

## 4. Modifications

### `frontend/nginx.conf`

Au niveau `server {}`, après `client_max_body_size`, ajouter le bloc de headers
(commentaire référençant le finding 4.3, style identique aux fixes 4.6 / 4.13).
Tous les `add_header` portent le flag **`always`** pour couvrir aussi les réponses
`error_page 404 → /index.html`. Le bloc `location /` ne définit aucun `add_header`,
donc les headers `server`-level sont hérités sans être écrasés.

CSP émise en `Content-Security-Policy-Report-Only` → les violations sont
rapportées (console navigateur) mais **rien n'est bloqué** : aucun risque de
casser login/avatars/maps pendant la phase d'observation.

---

## 5. Rollout

1. **Ce PR** : `Content-Security-Policy-Report-Only` + les 4 autres headers en
   mode enforce (sans risque fonctionnel).
2. **Monitoring 3–5 jours** : relever les violations Report-Only (login Auth0,
   avatars Google, uploads MinIO/S3, embed Maps, calendar).
3. **PR de suivi** : renommer le header en `Content-Security-Policy` (enforce)
   une fois la policy validée sans violation.

---

## 6. Plan de vérification

Impossible de joindre la prod depuis l'environnement ; vérification locale via
l'image nginx :

- [ ] `curl -I` → les 5 headers présents + version nginx masquée (`Server: nginx`).
- [ ] `nginx -t` OK (config valide après envsubst du template).
- [ ] SPA fonctionnelle : login (+ refresh silencieux Auth0), navigation, upload
      bannière/avatar, calendar.
- [ ] Avatars Google chargés.
- [ ] `<iframe>` d'embedding bloqué par le navigateur (`X-Frame-Options` /
      `frame-ancestors`).
- [ ] En mode enforce ultérieur : script injecté non exécuté (CSP appliquée).

---

## 7. Critères d'acceptation (issue #96)

| Critère | Couvert par |
|---|---|
| 6 headers présents dans `curl -I` | §2 + §4 (HSTS via ingress, 5 ajoutés ici) |
| SPA fonctionnelle (login, nav, upload, calendar) | CSP calibrée §3 + Report-Only §5 |
| Avatars Google chargés | `img-src …googleusercontent.com` |
| Embedding iframe bloqué | `X-Frame-Options: DENY` + `frame-ancestors 'none'` |
| Scripts injectés non exécutés | `script-src 'self'` (effectif au passage enforce) |
| Version nginx masquée | `server_tokens off` (déjà en place) |

---

## 8. Risques & questions ouvertes

- **Host réel des uploads MinIO/S3** : `avatarUrl`/`bannerUrl` sont construits
  depuis `app.s3.url` = `S3_ENDPOINT` (`http://minio:9000` dans le Helm —
  `FileStorageService.java:139`), qui est une adresse **interne**. Le chemin
  navigateur réel (même-origine `/s3` via l'ingress `unige-events-s3`, vs host
  absolu) doit être confirmé par le monitoring Report-Only avant l'enforce ; au
  besoin, élargir `img-src`.
- **Domaine Auth0** : identique (`dev-p8ufbvhr6g61j78w.us.auth0.com`) sur dev /
  preview / prod dans les `.env.*` actuels. À re-vérifier si un tenant prod
  distinct est introduit.
- **`script-src 'self'`** : suffisant pour un build Vite/React 19 standard
  (pas de `wasm-unsafe-eval` attendu) — à confirmer si une dépendance WASM est
  ajoutée.
