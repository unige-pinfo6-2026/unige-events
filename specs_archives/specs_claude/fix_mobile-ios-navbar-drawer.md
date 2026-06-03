# Spécification technique — Fix : responsivité mobile/iOS (navbar + menu latéral)

> Branche : `fix/error-pages` (ajout à la PR #240, déjà ouverte)
> Statut : **validé — implémentation** (décisions design ci-dessous, §6)
> Date : 2026-06-03
> Périmètre : **frontend uniquement** (`Navbar`, `Dropdown`, `Buttons`/`Links`, `index.html`, `index.css`).

## 1. Bugs rapportés

1. Site mal optimisé mobile, surtout iPhone.
2. Boutons du header « buggés » sur iOS : pas toujours faciles à taper, interactions incohérentes.
3. Le menu latéral s'ouvre **du mauvais côté** (gauche) → doit s'ouvrir **à droite**.
4. Ouverture/fermeture du menu **trop brusque** → ajouter une animation de glissement fluide.

## 2. Causes racines (prouvées par lecture du code)

### 2.1 Menu latéral — mauvais côté + pas d'animation
- `MobileMenu` rend le drawer en `fixed top-0 **left-0** h-dvh w-72 … border-r rounded-r-xl` ([Navbar.tsx:251](../../frontend/src/components/Navbar.tsx)) → ancré **à gauche**.
- Le menu est **monté/démonté** conditionnellement : `{mobileMenuOpen && <MobileMenu/>}` ([Navbar.tsx:348](../../frontend/src/components/Navbar.tsx)). Aucune classe `transition`/`translate-x` → apparition/disparition **instantanée**. Une transition CSS de sortie est de toute façon **impossible** tant que l'élément est démonté immédiatement.
- L'overlay utilise `h-screen` ([Navbar.tsx:249](../../frontend/src/components/Navbar.tsx)) (= `100vh`) alors que le drawer utilise `h-dvh`. Sur iOS Safari, `100vh` inclut la zone de la barre d'adresse → l'overlay déborde.

### 2.2 Boutons du header incohérents sur iOS
Le composant partagé `Dropdown` ([Dropdown.tsx](../../frontend/src/components/utils/Dropdown.tsx)) ouvre le panneau via **trois mécanismes cumulés** :
- `group-hover:visible` (hover CSS, l.99-101),
- `group-focus-within:visible` (focus, l.99-101),
- état React `isOpen` basculé par le `onClick` du `<div role="button">` (l.80-86, l.104).

Sur iOS Safari (pas de vrai hover) :
1. **1er tap** → hover simulé (`:hover` collant) **+** focus (`focus-within`) **+** `onClick` → `isOpen=true`. Le panneau s'ouvre via 3 voies.
2. **re-tap** → `onClick` met `isOpen=false`, **mais** le `:hover` simulé et le `focus-within` restent actifs → le panneau **reste visible**. État incohérent (c'est le « interactions inconsistent » rapporté). Il ne se ferme qu'en tapant ailleurs (`handleClickOutside` + blur, l.47-50).

Aggravant : les déclencheurs `NotificationBell`/`InboxBell` sont des `IconButton` (vrais `<button onClick={()=>{}}>`) **imbriqués** dans le `<div role="button">` du `Dropdown` ([NotificationBell.tsx:7](../../frontend/src/components/utils/NotificationBell.tsx), [RequestsInboxDropdown.tsx:13](../../frontend/src/components/utils/RequestsInboxDropdown.tsx)). Bouton dans un `role="button"` = imbrication interactive invalide, gérée de façon incohérente par les navigateurs (surtout iOS).

### 2.3 Cibles tactiles trop petites
`IconButton` = `p-2` + icône `size-5`/`size-6` ([Buttons.tsx:97](../../frontend/src/components/utils/Buttons.tsx)) → ~36–40 px. `ActionLink` idem ([Links.tsx:9](../../frontend/src/components/utils/Links.tsx)). En dessous du **minimum iOS de 44×44 px** → « pas toujours faciles à taper ».

### 2.4 Viewport / safe-area iOS
`index.html` : `<meta name="viewport" content="width=device-width, initial-scale=1.0" />` ([index.html:5](../../frontend/index.html)) — **sans `viewport-fit=cover`**. iOS n'étend pas le contenu sous le notch / home-indicator et `env(safe-area-inset-*)` reste inactif. Aucun `safe-area`/`-webkit-tap-highlight-color` dans [index.css](../../frontend/src/index.css).

## 3. Plan de correction

### 3.1 Drawer à droite + animation (Navbar.tsx + index.css)
- Repositionner le drawer : `right-0`, `border-l`, `rounded-l-xl` (au lieu de left/r).
- **Animer entrée + sortie** sans casser le démontage : pattern « monté pendant la fermeture » (comme les toasts `toast-in`/`toast-out`).
  - `index.css` : ajouter tokens `--animate-drawer-in/out` (translateX 100% ↔ 0) et `--animate-overlay-in/out` (opacité), + `@keyframes`.
  - `Navbar` : états `open` + `closing`. `onClose` déclenche `closing=true` (joue `drawer-out`), puis `onAnimationEnd` → démonte. Entrée = `drawer-in` au montage.
  - Respect `prefers-reduced-motion` via `motion-safe:`/`motion-reduce:` (déjà la convention du repo) → pas d'animation, bascule instantanée.
- Overlay : `h-screen` → `h-dvh` + fade.

### 3.2 Dropdown robuste au tactile (Dropdown.tsx)
- **Gater le hover derrière `@media (hover: hover)`** (utilitaire `[@media(hover:hover)]:group-hover:*` ou variante Tailwind) → les appareils tactiles n'ouvrent **que** par tap (`isOpen`).
- Retirer la dépendance `group-focus-within:visible` pour la visibilité « collante » (garder le focus clavier via `isOpen` au `onKeyDown`, déjà présent) → `isOpen` devient **autoritaire** sur tactile.
- Régler l'imbrication bouton-dans-bouton : `NotificationBell`/`InboxBell` ne doivent plus être des `<button>` (passer en élément non-interactif présentationnel ; le `<div role="button">` du `Dropdown` porte déjà le rôle et le clavier).

### 3.3 Cibles tactiles ≥ 44 px (Buttons.tsx, Links.tsx)
- `IconButton`/`ActionLink` : garantir une zone de tap ≥ 44×44 px (ex. `min-w-11 min-h-11 inline-flex items-center justify-center`), sans grossir l'icône.
- `-webkit-tap-highlight-color: transparent` global (évite le flash gris iOS) + `touch-action: manipulation` sur les contrôles (supprime le délai de 300 ms / double-tap-zoom).

### 3.4 Viewport / safe-area (index.html, index.css)
- `viewport` → `width=device-width, initial-scale=1.0, viewport-fit=cover`.
- Drawer/overlay/navbar : padding `env(safe-area-inset-*)` là où pertinent (haut navbar, bas du drawer).

## 4. Tests (Vitest + jsdom/happy-dom, couverture ~100 % du code modifié)
- **Drawer** : ouvert → classes `right-0`/`border-l` ; clic overlay/`X`/`Échap`/navigation → `closing` puis démontage après `animationend` (simuler `fireEvent.animationEnd`) ; `getFollowers`-style — vérifier `onClose` ; reduced-motion → démontage immédiat.
- **Dropdown** : tap (`click`) ouvre puis **referme** (toggle autoritaire, plus de « collé ») ; click-outside ferme ; clavier Enter/Espace inchangé ; `forceOpen` toujours respecté.
- **IconButton/ActionLink** : présence des classes de cible ≥ 44 px ; rôle/aria inchangés.
- Conserver tous les tests navbar/dropdown existants.

## 5. Critères d'acceptation
| # | Scénario | Attendu |
|---|---|---|
| AC-1 | Ouvrir le menu mobile | Glisse **depuis la droite**, animation fluide ~300 ms |
| AC-2 | Fermer (overlay / X / lien / Échap) | Glisse vers la droite puis disparaît (animation de sortie visible) |
| AC-3 | iPhone : taper cloche/inbox/menu utilisateur | Ouvre au 1er tap, **referme au 2e tap** (comportement cohérent) |
| AC-4 | iPhone : boutons header | Zone tactile ≥ 44 px, pas de flash gris, pas de zoom au double-tap |
| AC-5 | iPhone avec notch | Pas de débordement sous la barre d'adresse / home-indicator |
| AC-6 | Desktop | Hover-open des dropdowns **inchangé** ; aucune régression |
| AC-7 | `prefers-reduced-motion` | Pas d'animation, ouverture/fermeture instantanée |

## 6. Décisions design (validées 2026-06-03)
1. **Approche dropdown tactile** → **Patch du `Dropdown` partagé** : hover gaté `@media(hover:hover)`, tap (`isOpen`) autoritaire, retrait du `group-focus-within:visible` collant, de-button des cloches. Hover-open desktop conservé. (Radix écarté : trop gros refactor.)
2. **Périmètre** → **header + menu + viewport/safe-area uniquement**. Pas d'audit large des pages.
3. **Cibles tactiles** → **garder les tailles actuelles** (pas de passage forcé à 44 px). On corrige le *comportement* tap : `touch-action: manipulation` (supprime le délai/zoom double-tap) + `-webkit-tap-highlight-color: transparent`, sans redimensionner.
4. **Vérif iOS réelle** : layout/côté/animation validés en viewport mobile ; le tactile iOS réel reste à valider sur iPhone par l'équipe (hors capacité de l'environnement).

## 7. Notes
- Aucune modif API/backend. Aucun changement de comportement desktop attendu (hover-open conservé).
- S'ajoute aux 3 commits déjà sur la PR #240 ; commits atomiques séparés (`fix(frontend): …`).
