/**
 * Pure placement geometry for the @-mention dropdown, extracted from
 * `MentionAutocomplete` so it can be unit-tested and imported without tripping
 * the `react-refresh/only-export-components` rule (a component file may only
 * export components).
 */

/** Hard ceiling for the dropdown (matches the old `max-h-72` = 18rem). */
export const MAX_DROPDOWN_PX = 288
/** Smallest height worth showing — below this we still cap to the available
 *  space but accept a little scroll rather than a sliver. */
const MIN_DROPDOWN_PX = 96
/** Breathing room kept between the dropdown and the viewport edge. */
const VIEWPORT_MARGIN_PX = 8
/** Gap between the textarea and the dropdown (matches the old `mt-1`/`mb-1`). */
const ANCHOR_GAP_PX = 4

/**
 * Fixed-position placement for the dropdown (viewport-relative). The dropdown
 * is portaled to <body> so it escapes the comment card's backdrop-blur
 * stacking context and the page wrapper's overflow-hidden clip. Discriminated
 * on `side` so the anchor coordinate is exactly `top` (below) XOR `bottom`
 * (above) — no optional-coalescing fallbacks needed at the call site.
 */
export type DropdownPlacement =
  | { side: 'below'; maxHeight: number; left: number; width: number; top: number }
  | { side: 'above'; maxHeight: number; left: number; width: number; bottom: number }

/**
 * Picks where to anchor the dropdown (under or over the textarea), how tall it
 * may grow (so it never spills past the viewport), and its fixed viewport
 * coordinates. Falls back to "below, full height, top-left" when geometry
 * can't be measured (e.g. detached ref).
 */
export function computePlacement(textarea: HTMLTextAreaElement | null): DropdownPlacement {
  if (!textarea) return { side: 'below', maxHeight: MAX_DROPDOWN_PX, left: 0, width: 0, top: 0 }
  const rect = textarea.getBoundingClientRect()
  const spaceBelow = window.innerHeight - rect.bottom - VIEWPORT_MARGIN_PX
  const spaceAbove = rect.top - VIEWPORT_MARGIN_PX
  const below = spaceBelow >= MIN_DROPDOWN_PX || spaceBelow >= spaceAbove
  const available = below ? spaceBelow : spaceAbove
  const maxHeight = Math.max(MIN_DROPDOWN_PX, Math.min(MAX_DROPDOWN_PX, available))
  return below
    ? { side: 'below', maxHeight, left: rect.left, width: rect.width, top: rect.bottom + ANCHOR_GAP_PX }
    : { side: 'above', maxHeight, left: rect.left, width: rect.width, bottom: window.innerHeight - rect.top + ANCHOR_GAP_PX }
}
