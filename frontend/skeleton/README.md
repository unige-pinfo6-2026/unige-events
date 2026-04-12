# Skeleton generator

Générateur maison pour les skeleton screens affichés pendant le chargement des pages. Écrit des fichiers `.bones.json` dans [`../src/bones/`](../src/bones/) qui sont consommés au runtime par `<Skeleton>` du package `boneyard-js`.

## Pourquoi un générateur custom (et pas la CLI boneyard) ?

À l'origine, boneyard propose une CLI (`npx boneyard-js build`) et un plugin Vite qui capturent automatiquement les positions des éléments en visitant les pages via Playwright. On a abandonné cette approche pour plusieurs raisons :

- **Routes protégées inaccessibles** : Playwright tourne sans authentification, donc impossible de visiter `/profile/me`, `/events/1`, `/calendar`, `/events/search` — tout ce qui passe par `PrivateRoute`. Le registry final ne contenait que `event-cards` (landing publique).
- **Plugin Vite destructeur** : activé, il re-capture à chaque démarrage du dev server et à chaque HMR, écrasant systématiquement le travail en cours.
- **Layouts complexes mal capturés** : les grids `auto-fit minmax(280, 320)` cap à 320px mais boneyard exprime tout en pourcentages de container width, qui eux scalent linéairement. Résultat : cards qui s'étirent ou rétrécissent entre les breakpoints.
- **Pas de contrôle fin** sur la hiérarchie visuelle (zones claires/sombres, distribution manuelle d'events, lignes de grille, etc.).

Les skeletons actuels sont donc **définitifs** : ils ne sont plus régénérés automatiquement. Si tu touches au layout d'une page couverte (event card, détail événement, profil, calendrier, recherche), tu dois éditer le bloc correspondant dans [`generate.mjs`](./generate.mjs) et relancer `npm run skeleton`.

## Utilisation

```bash
# Depuis frontend/
npm run skeleton

# Ou directement
node skeleton/generate.mjs
```

Le script écrit tous les `.bones.json` d'un coup (event-cards, search-results, event-calendar). Les autres skeletons (event-detail, profile) n'ont pas de fonction de génération — ils ont été tunés manuellement par la CLI au début puis ajustés dans le JSON ; on les considère stables.

Aucun serveur, aucun browser, aucun build — ça tourne en ~100 ms.

## Comprendre le format `.bones.json`

Un fichier `.bones.json` contient plusieurs breakpoints, chacun avec un tableau de "bones" (rectangles dessinés à l'écran).

```jsonc
{
  "breakpoints": {
    "320": {
      "name": "event-cards",
      "viewportWidth": 320,
      "width": 320,
      "height": 2428,
      "bones": [
        [3.35, 0, 93.29, 388, 24, true],
        [3.35, 0, 93.29, 208, 24],
        ...
      ]
    },
    "720": { ... },
    ...
  }
}
```

### Format d'un bone

Chaque bone est un tableau `[x, y, width, height, borderRadius, isContainer?]` :

| Index | Nom | Type | Unité | Description |
|---|---|---|---|---|
| 0 | `x` | number | **% de container width** | Position horizontale |
| 1 | `y` | number | **pixels** (scalés) | Position verticale |
| 2 | `width` | number | **% de container width** | Largeur du bone |
| 3 | `height` | number | **pixels** (scalés) | Hauteur du bone |
| 4 | `borderRadius` | number | pixels (ou `"50%"`) | Rayon des coins |
| 5 | `isContainer` | boolean (optionnel) | — | Si `true`, le bone est dessiné avec une **couleur plus claire** (`adjustColor(color, 0.45)` en light mode, `0.03` en dark mode) |

> ⚠️ **x/w = pourcentage de la container width, pas du viewport.** Au runtime, boneyard mesure la largeur réelle du conteneur `<Skeleton>` via ResizeObserver et applique les percentages dessus.

### Scaling vertical (scaleY)

Les `y` et `h` sont en pixels mais **scalés** au runtime :

```
scaleY = runtime_container_height / bones.height
```

Le `height` déclaré dans le breakpoint doit donc matcher la hauteur intrinsèque du fixture au runtime (la somme des cards + gaps, ou la hauteur fixe du parent style `h-[680px]` pour le calendrier). Sinon, tous les `y` et `h` sont stretched ou compressés.

### Résolution du breakpoint (responsive)

Au runtime, boneyard choisit le plus grand breakpoint dont la clé est `≤` à la largeur mesurée :

```js
// From boneyard-js/dist/shared.js
const bps = Object.keys(bones.breakpoints).map(Number).sort((a, b) => a - b)
const match = [...bps].reverse().find(bp => width >= bp) ?? bps[0]
```

où `width = containerWidth` (si mesurée) ou `window.innerWidth` (avant le mount).

**Important** : on indexe les breakpoints sur la **container width attendue**, pas le viewport. C'est la seule façon d'avoir des percentages cohérents. Exemple pour la search page (sidebar `lg:w-60 = 240px` qui réduit le container à lg+), on utilise les clés `[327, 480, 700, 950]` et pas `[375, 768, 1024, 1280]`.

## La hiérarchie visuelle 2-couleurs (flag `isContainer`)

Le `<Skeleton>` affiche tous les bones avec **une seule couleur de base**, passée via la prop `color` (ici `rgba(255,255,255,0.15)` en dark mode, `rgba(0,0,0,0.08)` en light mode).

Mais il y a une exception : les bones avec `isContainer: true` sont rendus avec `adjustColor(color, 0.45)` en light mode — ce qui les rend **significativement plus clairs** que les autres bones. En dark mode, l'écart est plus subtil (0.03).

On exploite ça pour créer une **hiérarchie visuelle à deux niveaux** :

- **Container bones** (plus clairs) → surface du support : card, bouton, pill, toolbar, zone de "texte blanc" sur image
- **Leaf bones** (plus sombres) → contenu : icônes, lignes de texte sur fond clair, séparateurs

**Exemple pour une event card** :

```
Card outer         = container (lighter)  → card surface
  Banner           = leaf      (darker)   → image zone
    Badge          = container (lighter)  → pill visible on dark banner
    Title line     = container (lighter)  → white text on dark banner
  Meta icon        = leaf      (darker)   → dark icon on light card
  Meta text        = leaf      (darker)   → dark text on light card
  Description      = leaf      (darker)   → dark text on light card
```

Cette inversion dynamique (leaf darker sur container lighter, ou container lighter sur container lighter inversé) donne au skeleton l'aspect "banner sombre avec texte clair, puis contenu sombre sur card claire" — exactement comme la vraie card.

**Règle du pouce** : si l'élément réel a un **fond distinct** (card surface, banner image, pill, bouton), c'est un container. Si c'est du **contenu sur un fond** (texte, icône, ligne), c'est une leaf.

## Architecture du générateur

Le fichier [`generate.mjs`](./generate.mjs) est organisé en sections :

1. **Helpers** : `round`, `writeBones`, `makeCardsPayload`
2. **Card template** : `buildCard()` — produit les bones d'une seule card à la position `(cardX_pct, y0)` avec des dimensions relatives à `containerW`. Réutilisé par event-cards ET search-results.
3. **Auto-fit grid math** : `autoFitLayout()` — calcule le nombre de cols, la largeur du track, les x de chaque card selon le container width. Réplique le comportement de `grid-cols-[repeat(auto-fit,minmax(280px,320px))]`.
4. **Fixed cols math** : `fixedColsLayout()` — variant pour `grid-cols-1 md:grid-cols-2 xl:grid-cols-3`.
5. **`genCards()`, `genSearch()`, `genCalendar()`** — un par skeleton. Itère sur les container widths cibles et écrit le `.bones.json`.

### Ajouter un nouveau skeleton

1. **Identifier la structure du composant réel**. Note les dimensions (hauteurs fixes, paddings, gaps), la grid CSS, les breakpoints Tailwind qui changent le layout, et les éléments visuels à représenter.

2. **Choisir les container widths cibles**. Combien et où ? Règle : une bp à chaque **transition de layout** (changement de nombre de colonnes, sidebar qui apparaît…) plus éventuellement des bps intermédiaires pour éviter le stretching entre deux bps éloignés. Exemple pour un grid auto-fit qui passe de 1 → 4 cols : 8 bps (cf. `EVENT_CARDS_CONTAINERS`).

3. **Écrire la fonction `build<Skeleton>()`** qui reçoit un `containerW` et retourne un tableau de bones :
   ```js
   function buildMySkeleton(containerW) {
     const pctX = px => round(px * 100 / containerW)
     const pctW = px => round(px * 100 / containerW)
     const bones = []
     // Outer surface
     bones.push([0, 0, 100, HEIGHT, 24, true])
     // Header
     bones.push([pctX(20), 20, pctW(200), 32, 6, true])
     // ...
     return bones
   }
   ```

4. **Décider de la `height`** : doit matcher la hauteur intrinsèque du fixture au runtime. Si le fixture est rendu à l'intérieur d'un parent avec hauteur fixe (`h-[680px]`), utilise cette valeur. Sinon, somme les dimensions de tous les éléments.

5. **Écrire `gen<Skeleton>()`** qui itère sur les container widths et appelle `writeBones()` :
   ```js
   function genMySkeleton() {
     const out = { breakpoints: {} }
     for (const cw of [320, 720, 1216]) {
       out.breakpoints[String(cw)] = {
         name: 'my-skeleton',
         viewportWidth: cw,
         width: cw,
         height: HEIGHT,
         bones: buildMySkeleton(cw),
       }
     }
     writeBones('my-skeleton.bones.json', out)
   }
   ```

6. **Appeler `genMySkeleton()` en bas du fichier**.

7. **Côté composant React**, wrapper le contenu dans `<Skeleton>` avec le bon `name` :
   ```tsx
   import { Skeleton } from 'boneyard-js/react'
   import { useTheme } from '@/contexts/ThemeContext'

   function MyFixture() {
     // Un composant JSX qui a la MÊME structure CSS que le contenu final,
     // rendu pendant le chargement avec visibility:hidden pour fournir
     // les dimensions intrinsèques au container <Skeleton>.
     return <div>...</div>
   }

   export default function MyPage() {
     const { data, loading } = useMyHook()
     const { theme } = useTheme()
     const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.15)' : 'rgba(0,0,0,0.08)'

     if (loading) return (
       <Skeleton
         name="my-skeleton"
         loading={true}
         animate="pulse"
         color={skeletonColor}
       ><MyFixture /></Skeleton>
     )

     return <div>{/* contenu réel */}</div>
   }
   ```

8. **Ajouter l'import dans [`../src/bones/registry.js`](../src/bones/registry.js)** :
   ```js
   import _my_skeleton from './my-skeleton.bones.json'

   registerBones({
     ...
     "my-skeleton": _my_skeleton,
   })
   ```

9. **Lancer `npm run skeleton`** → le JSON est généré, HMR recharge le registry, skeleton visible immédiatement.

## Le fixture rendu en `children`

Le `<Skeleton>` reçoit deux choses :

- **Les bones** (via `name` → registry lookup) qui sont dessinés par-dessus en absolute
- **Les children** (ton fixture component) rendus avec `visibility: hidden` pour **établir les dimensions intrinsèques** du container

C'est important : **sans children avec des dimensions, le container a une hauteur de 0px** et les bones sont clipped par `overflow:hidden`. On a eu le bug au début.

Le fixture doit donc refléter la **structure CSS réelle** du composant chargé (mêmes classes, mêmes heights fixes, mêmes breakpoints Tailwind). Le contenu interne des éléments peut être vide — seules les dimensions comptent.

**Ne pas confondre** avec la prop `fixture` de boneyard : celle-ci était utilisée UNIQUEMENT par la CLI au moment de la capture, on l'a retirée partout.

## Pièges connus

### 1. Percentages vs container width

Les `x%` et `w%` sont relatifs à la **container width mesurée au runtime**, pas au viewport. Si tu calcules tes percentages en divisant par `vw` au lieu de `containerW`, ça compense parfois par hasard (car container ≈ vw - 48) mais pas quand il y a une sidebar ou un max-width qui réduit le container.

**Règle** : toujours calculer `px * 100 / containerW`, et nommer tes bps par container width.

### 2. scaleY qui compresse verticalement

Si le fixture produit une hauteur différente de `bones.height`, tout est stretched/compressed. Résultat : cards qui paraissent landscape alors que tu les as dessinées portrait.

**Vérification** : calcule manuellement la hauteur intrinsèque du fixture (somme des heights + gaps) et assure-toi que `bones.height` matche. Pour les cards en grid : `rows * cardHeight + (rows - 1) * gap`.

### 3. Grid `auto-fit` cap

`grid-cols-[repeat(auto-fit,minmax(280px,320px))]` cap les cards à 320 max et centre l'excédent via `justify-center`. Les percentages boneyard ne capent pas — ils scalent linéairement. Résultat : au runtime, si le container est plus large que prévu, la card bone dépasse 320 alors que la vraie card reste 320.

**Solution** : ajouter plusieurs breakpoints qui suivent les transitions du auto-fit (cf. `autoFitLayout()` + `EVENT_CARDS_CONTAINERS`).

### 4. Border-radius uniforme

Un bone a un seul `borderRadius` pour les 4 coins. Si tu veux un rectangle rounded-top-only (style banner en haut d'une card), tu ne peux pas. Compromis : soit les 4 coins arrondis (pinch visible en bas du banner), soit 0 (angles droits visibles au-dessus du card container rounded).

Dans la pratique, le pinch avec radius 24 sur banner + card container 24 est à peine visible en dark mode et passe très bien.

### 5. Fixture avec CSS qui ne scale pas linéairement

Si le fixture utilise des `max-w-[320px]` ou des `minmax()` qui cap, les percentages bones ne correspondront pas aux dimensions réelles au runtime (idem que le piège du auto-fit). Soit tu ajoutes des bps, soit tu simplifies le fixture avec un grid Tailwind fixe (`grid-cols-1 md:grid-cols-2 xl:grid-cols-3`).

## Fichiers concernés

- [`generate.mjs`](./generate.mjs) — le générateur (ce fichier)
- [`../src/bones/*.bones.json`](../src/bones/) — les 5 skeletons générés (4 générés + event-detail/profile ajustés manuellement)
- [`../src/bones/registry.js`](../src/bones/registry.js) — import + `registerBones()`
- [`../src/main.tsx`](../src/main.tsx) — import du registry au startup (obligatoire)
- Composants qui utilisent `<Skeleton>` : `EventCards`, `EventCalendar`, `EventDetailPage`, `ProfilePage`, `EventsSearchPage`
