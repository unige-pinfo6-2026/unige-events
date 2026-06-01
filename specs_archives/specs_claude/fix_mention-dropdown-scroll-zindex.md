# Spécification technique — Fix : dropdown @-mention masqué par le footer + non scrollable

> Branche : `fix/mention-dropdown-scroll-zindex`
> Statut : **analyse + plan**
> Date : 2026-05-30
> Périmètre : **frontend uniquement** (`MentionAutocomplete`).

## 1. Bug

Dans le composer de commentaires (`CommentForm`), taper `@xxx` ouvre le dropdown de suggestions d'utilisateurs. Deux problèmes :
1. **Le footer chevauche / passe devant** le dropdown.
2. **La liste n'est pas scrollable** (en pratique : la partie débordante est sous le footer, donc inatteignable).

## 2. Cause racine

`MentionAutocomplete` rend le dropdown en `absolute z-20`, **enfant** de la carte `CommentSection` qui porte `backdrop-blur-xl` ([CommentSection.tsx:72](../../frontend/src/components/event/CommentSection.tsx)). `backdrop-filter` **crée un stacking context** (z-auto) → le `z-20` est piégé dedans. Le footer a son contenu en `z-10` dans le contexte racine ([Footer.tsx:30](../../frontend/src/components/Footer.tsx)) → `10 > z-auto` côté racine → **le footer peint par-dessus** les descendants de la carte.

Second piège : la page est enveloppée d'un `SectionWrapper` en `relative overflow-hidden` ([Section.tsx:61](../../frontend/src/components/utils/Section.tsx)) → un dropdown `absolute` qui déborde vers le bas est **clippé**.

Le scroll lui-même fonctionne déjà (`overflow-y-auto` + `maxHeight` calculé par `computePlacement`) — il était juste masqué/clippé.

## 3. Fix — portal `fixed` vers `document.body`

Rendre le `<ul>` du dropdown via `createPortal(…, document.body)` en **`position: fixed`** avec coordonnées calculées depuis le `getBoundingClientRect()` du textarea (déjà mesuré par `computePlacement`). `z-40` (au-dessus du footer `z-10`, sous les modals `z-50`).

Avantages : échappe **à la fois** le stacking context (`backdrop-blur`) et le clipping (`overflow-hidden`). Garde `overflow-y-auto` + `maxHeight` → scroll OK. Le recompute sur `scroll`/`resize` (déjà câblé) suit le textarea.

`computePlacement` est étendu pour renvoyer aussi `top` / `left` / `width` (placement `below` → `top = rect.bottom + margin` ; `above` → on ancre par `bottom`). Le click-outside (`containerRef.contains`) continue de marcher (le nœud porté reste référencé par `containerRef`).

## 4. Tests
- Adapter les 2 tests de placement existants (`top-full`/`bottom-full` → assertions sur `position: fixed` + un `data-side` below/above + coords).
- Conserver tous les tests comportementaux (search, clavier, sélection, debounce, click-outside) inchangés.
- Ajouter : le dropdown est porté dans `document.body` (hors de la carte) ; `z-40` ; maxHeight + overflow-y-auto conservés.
- Couverture ~100 % sur le code modifié.

## 5. Critères d'acceptation
| # | Scénario | Attendu |
|---|---|---|
| AC-1 | `@xx` avec page courte (footer visible) | Dropdown **devant** le footer, entièrement visible |
| AC-2 | > 8 résultats / peu d'espace | Liste scrollable (maxHeight + overflow) |
| AC-3 | Textarea proche du bas du viewport | Dropdown flip au-dessus |
| AC-4 | Scroll de la page pendant que le dropdown est ouvert | Le dropdown suit le textarea (recompute) |
