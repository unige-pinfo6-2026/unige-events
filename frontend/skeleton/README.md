# Skeleton screens — documentation agent

Ce fichier est la **source de vérité** pour tout agent qui implémente ou modifie un skeleton screen dans ce projet. Le lire en entier avant d'écrire le moindre bone.

---

## Règle fondamentale

**Toute page ou composant qui effectue un appel API et affiche un état `loading` doit avoir un skeleton `.bones.json` correspondant.** C'est non négociable, au même titre que la gestion des états `loading / error / data`. L'agent ne demande pas de confirmation — il génère.

---

## Architecture

```
frontend/
├── skeleton/
│   ├── README.md          ← ce fichier
│   └── generate.mjs       ← générateur (node skeleton/generate.mjs)
└── src/
    └── bones/
        ├── registry.js                 ← enregistre tous les bones au démarrage
        ├── event-cards.bones.json      ← généré par generate.mjs
        ├── search-results.bones.json   ← généré par generate.mjs
        ├── event-calendar.bones.json   ← généré par generate.mjs
        ├── event-detail.bones.json     ← généré par generate.mjs
        ├── event-edit.bones.json       ← généré par generate.mjs
        ├── profile.bones.json          ← manuel (ajusté à la main)
        └── navbar-user.bones.json      ← manuel (inline element fixe)
```

`src/bones/registry.js` est importé dans `src/main.tsx` au démarrage — sans cet import les bones ne sont jamais chargés.

---

## Table des skeletons existants

| `name` | Fichier | Composant consommateur | Méthode |
|---|---|---|---|
| `event-cards` | `event-cards.bones.json` | `EventCards` | `genCards()` dans generate.mjs |
| `search-results` | `search-results.bones.json` | `EventsSearchPage` | `genSearch()` dans generate.mjs |
| `event-calendar` | `event-calendar.bones.json` | `EventCalendar` | `genCalendar()` dans generate.mjs |
| `event-detail` | `event-detail.bones.json` | `EventDetailPage` | `genEventDetail()` dans generate.mjs |
| `event-edit` | `event-edit.bones.json` | `EventEditPage` | `genEventEdit()` dans generate.mjs |
| `profile` | `profile.bones.json` | `ProfilePage` | manuel |
| `navbar-user` | `navbar-user.bones.json` | `Navbar` (`DesktopNav`) | manuel |

Mettre cette table à jour dans `AGENTS.md` à chaque ajout.

---

## Décision : generate.mjs ou JSON manuel ?

```
Le layout utilise une grille CSS (grid, flex wrap, auto-fit) ?
├── OUI → generate.mjs (calcul programmatique des positions)
│         → écrire une fonction buildXxx(containerW) + genXxx()
│         → lancer npm run skeleton après
└── NON → JSON manuel
          → élément inline fixe (ex: navbar-user)
          → layout simple sans variation de colonnes
```

---

## Format d'un bone

```
[x, y, width, height, borderRadius, isContainer?]
```

| Index | Nom | Unité | Description |
|---|---|---|---|
| 0 | `x` | % de container width | Position horizontale |
| 1 | `y` | pixels (scalés) | Position verticale |
| 2 | `width` | % de container width | Largeur |
| 3 | `height` | pixels (scalés) | Hauteur |
| 4 | `borderRadius` | pixels ou `"50%"` | Rayon des coins |
| 5 | `isContainer` | boolean (optionnel) | Si `true` : couleur plus claire (fond distinct) |

### Règle isContainer — hiérarchie visuelle 2 couleurs

```
isContainer = true  (plus clair)  → fond distinct : card, banner, pill, bouton, toolbar
isContainer absent  (plus sombre) → contenu sur fond : texte, icône, séparateur, ligne
```

Exemple pour une event card :
```
Card outer    → true   (surface de la card)
  Banner      → absent (zone image = plus sombre sur card claire)
    Badge     → true   (pill visible sur banner sombre)
    Titre     → true   (texte blanc sur banner sombre)
  Icône meta  → absent (icône sombre sur card claire)
  Texte meta  → absent (texte sombre sur card claire)
```

---

## Format d'un fichier .bones.json

```jsonc
{
  "breakpoints": {
    "320": {
      "name": "mon-skeleton",       // doit correspondre exactement au name= dans le JSX
      "viewportWidth": 320,         // = containerW (pas le viewport)
      "width": 320,                 // = containerW
      "height": 480,                // hauteur intrinsèque du fixture au runtime — CRITIQUE
      "bones": [
        [0, 0, 100, 480, 24, true], // [x%, y_px, w%, h_px, borderRadius, isContainer?]
        [4.17, 20, 60, 20, 4],
        [4.17, 48, 40, 14, 4]
      ]
    },
    "720": { … },
    "1216": { … }
  }
}
```

---

## Règle des breakpoints

Les clés de breakpoints sont des **container widths**, pas des viewport widths.

**Pourquoi :** boneyard mesure la largeur du container `<Skeleton>` via ResizeObserver. Les `x%` et `w%` sont relatifs à cette largeur — pas au viewport. Si on indexe par viewport, les percentages sont faux dès qu'il y a une sidebar ou un max-width.

**Combien de breakpoints :**
- Une BP par **transition de layout** (changement de nombre de colonnes, sidebar qui apparaît/disparaît, toolbar qui passe de stacked à row, etc.)
- Ajouter des BPs intermédiaires si deux transitions sont éloignées (évite le stretching visible entre BPs)
- **Minimum** : 1 BP pour un élément sans variation responsive (ex: `navbar-user`)

**Comment boneyard choisit le bon breakpoint au runtime :**
```js
// Plus grand breakpoint dont la clé est ≤ container width mesuré
const match = [...bps].reverse().find(bp => width >= bp) ?? bps[0]
```
→ La première BP doit couvrir le container le plus petit possible (souvent mobile).

---

## Règle du `height`

`height` dans le breakpoint = hauteur intrinsèque **exacte** du fixture au runtime.

Au runtime : `scaleY = runtime_container_height / bones.height`

Si `bones.height` ≠ hauteur réelle du fixture → tous les `y` et `h` sont étirés ou compressés → skeleton déformé.

**Comment calculer `height` :**
- Layout en grille : `rows * cardHeight + (rows - 1) * gap`
- Hauteur fixe CSS (`h-[680px]`) : utiliser cette valeur directement
- Layout libre : sommer toutes les hauteurs d'éléments + gaps

---

## Workflow : créer un nouveau skeleton (JSON manuel)

### 1. Identifier la structure visuelle

Lire le composant cible. Relever :
- Toutes les hauteurs fixes (Tailwind `h-[Xpx]`, `h-X`, padding, gap)
- La grille CSS (colonnes, breakpoints Tailwind où le layout change)
- Les éléments visuellement distincts (cards, banners, pills, icônes, textes)

### 2. Choisir les container widths cibles

Lister toutes les transitions de layout. Choisir une BP par transition + intermédiaires si besoin. Indexer sur la **largeur du container** (pas du viewport).

### 3. Écrire les bones

```js
// Helpers — toujours calculer depuis containerW
const pctX = px => Math.round(px * 10000 / containerW) / 100
const pctW = px => Math.round(px * 10000 / containerW) / 100

// Exemple : card surface + ligne de texte
bones.push([0, 0, 100, 320, 24, true])              // card (container)
bones.push([pctX(20), 20, pctW(180), 18, 4])         // titre (leaf)
bones.push([pctX(20), 46, pctW(120), 14, 4])         // sous-titre (leaf)
```

### 4. Écrire le fichier .bones.json

Le placer dans `frontend/src/bones/<nom>.bones.json`.

### 5. Enregistrer dans registry.js

```js
// frontend/src/bones/registry.js
import _mon_skeleton from './mon-skeleton.bones.json'

registerBones({
  // ... existants
  "mon-skeleton": _mon_skeleton,
})
```

### 6. Écrire le fixture dans le composant

```tsx
// Composant local, non-exporté, dans le même fichier que le composant cible.
// Doit reproduire EXACTEMENT la structure CSS du layout réel.
// Le contenu interne peut être vide — seules les dimensions comptent.
function MonComposantFixture() {
  return (
    <div className="grid grid-cols-[3fr_2fr] gap-6 items-start max-lg:grid-cols-1">
      <div className="flex flex-col gap-5">
        <div className="h-72 lg:h-80 rounded-3xl" />
        <div className="h-40 rounded-3xl" />
      </div>
      <div className="flex flex-col gap-4">
        <div className="h-72 rounded-3xl" />
      </div>
    </div>
  )
}
```

**Le fixture est obligatoire.** Sans children avec des dimensions, le container `<Skeleton>` a une hauteur de 0px et les bones sont clippés par `overflow:hidden` → skeleton invisible.

### 7. Intégrer `<Skeleton>` dans le composant

```tsx
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'

export default function MonComposant() {
  const { data, loading, error } = useMonHook()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.08)'

  if (loading) return (
    <Skeleton name="mon-skeleton" loading animate="pulse" color={skeletonColor}>
      <MonComposantFixture />
    </Skeleton>
  )
  if (error) return <InfoMessage type="error" message={error} />
  return <div>{/* contenu réel */}</div>
}
```

Props `<Skeleton>` obligatoires :
- `name` → clé dans le registry (doit correspondre exactement)
- `loading={true}` → active le mode skeleton
- `animate="pulse"` → animation pulse
- `color` → toujours conditionnel au thème (dark/light)

### 8. Mettre à jour la documentation

- `AGENTS.md` → table "Skeletons existants"
- `docs/components.md` → section "Skeleton screens"

---

## Workflow : ajouter un skeleton via generate.mjs

À utiliser pour toute page avec une grille CSS dynamique.

### 1. Écrire la fonction `buildXxx(containerW)`

```js
function buildMonSkeleton(containerW) {
  const pctX = px => round(px * 100 / containerW)
  const pctW = px => round(px * 100 / containerW)
  const bones = []

  // Surface (container)
  bones.push([0, 0, 100, HEIGHT, 24, true])
  // Titre
  bones.push([pctX(20), 20, pctW(200), 22, 6, true])
  // ...
  return bones
}
```

### 2. Écrire la fonction `genXxx()`

```js
function genMonSkeleton() {
  const out = { breakpoints: {} }
  for (const cw of [320, 720, 1216]) {
    out.breakpoints[String(cw)] = {
      name: 'mon-skeleton',
      viewportWidth: cw,
      width: cw,
      height: HEIGHT,
      bones: buildMonSkeleton(cw),
    }
  }
  writeBones('mon-skeleton.bones.json', out)
}
```

### 3. Appeler `genMonSkeleton()` en bas du fichier

### 4. Lancer le générateur

```bash
# depuis frontend/
npm run skeleton
```

Le JSON est écrit dans `src/bones/`, HMR recharge le registry.

### 5. Suivre les étapes 5 à 8 du workflow manuel (registry, fixture, intégration, doc)

---

## Workflow : mettre à jour un skeleton existant

Si le layout d'un composant change (nouveau composant, refactoring de grid, hauteurs modifiées) :

1. **Skeleton dans generate.mjs** → modifier `buildXxx()` / `genXxx()` + `npm run skeleton`
2. **Skeleton manuel** → éditer directement le `.bones.json`
3. **Fixture** → mettre à jour le composant fixture pour qu'il reflète le nouveau layout
4. Vérifier que `bones.height` correspond toujours à la hauteur réelle du fixture

---

## Pièges — NE PAS faire

### ❌ Indexer les breakpoints par viewport width

```jsonc
// FAUX — viewport width
{ "breakpoints": { "375": {…}, "768": {…}, "1280": {…} } }

// CORRECT — container width (mesurée par ResizeObserver)
{ "breakpoints": { "320": {…}, "720": {…}, "960": {…} } }
```

### ❌ Calculer les % depuis le viewport

```js
// FAUX
const pctX = px => px * 100 / window.innerWidth

// CORRECT
const pctX = px => px * 100 / containerW
```

### ❌ height incorrect dans le breakpoint

Si le fixture produit `h = 640px` au runtime mais `bones.height = 480`, scaleY = 0.75 → tous les éléments sont 25% plus petits qu'attendu.

### ❌ Fixture sans dimensions

```tsx
// FAUX — le container sera height: 0, bones clippés
function Fixture() { return <div /> }

// CORRECT — reproduit les dimensions du layout réel
function Fixture() {
  return (
    <div className="h-72 rounded-3xl" /> // même classes que le vrai composant
  )
}
```

### ❌ borderRadius "50%" sur des rectangles

`"50%"` est réservé aux cercles (avatars). Pour des éléments rectangulaires arrondis, utiliser une valeur en pixels.

### ❌ grid auto-fit sans BPs aux transitions de colonnes

`grid-cols-[repeat(auto-fit,minmax(280px,320px))]` change de nombre de colonnes à ~580px, ~880px, ~1180px. Sans BP à chaque transition, les bones s'étirent linéairement alors que les vraies cards restent cappées à 320px.

### ❌ Oublier le registry

Un fichier `.bones.json` non importé dans `registry.js` → `<Skeleton>` ne trouve pas le name → erreur silencieuse, skeleton vide.

### ❌ Utiliser LoadingSpinner à la place d'un skeleton

`LoadingSpinner` est réservé à `PrivateRoute` et `LoadingPage`. Tout autre composant/page avec `loading` doit utiliser `<Skeleton>`.

---

## Checklist avant de commiter

- [ ] Fichier `.bones.json` créé dans `src/bones/`
- [ ] `registry.js` mis à jour avec l'import et la clé
- [ ] Fixture local non-exporté dans le composant cible
- [ ] `bones.height` = hauteur intrinsèque du fixture (calculé ou vérifié)
- [ ] `skeletonColor` conditionnel au thème (dark/light)
- [ ] `LoadingSpinner` retiré du composant si présent
- [ ] Table skeletons mise à jour dans `AGENTS.md`
- [ ] Section skeleton mise à jour dans `docs/components.md`
- [ ] Si generate.mjs modifié : `npm run skeleton` relancé, JSON commité
