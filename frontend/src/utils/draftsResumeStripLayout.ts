export const STRIP_LAYOUT = {
  cardWidth: 288,
  cardGap: 12,
  labelWidth: 220,
  labelGap: 16,
  viewAllButtonWidth: 110,
  viewAllButtonGap: 16,
  containerHorizontalPadding: 32,
  labelBreakpoint: 640,
} as const

export interface StripLayout {
  displayCount: number
  showViewAll: boolean
}

function slotsFor(usableWidth: number): number {
  if (usableWidth <= 0) return 0
  const slotSize = STRIP_LAYOUT.cardWidth + STRIP_LAYOUT.cardGap
  return Math.max(0, Math.floor((usableWidth + STRIP_LAYOUT.cardGap) / slotSize))
}

export function computeStripLayout(availableWidth: number, totalDrafts: number): StripLayout {
  if (totalDrafts <= 0) return { displayCount: 0, showViewAll: false }

  // Before the first measurement, stay optimistic: render everything and let the
  // ResizeObserver refine once the real width is known.
  if (availableWidth <= 0) return { displayCount: totalDrafts, showViewAll: false }

  const showsLabel = availableWidth >= STRIP_LAYOUT.labelBreakpoint
  const labelReserved = showsLabel ? STRIP_LAYOUT.labelWidth + STRIP_LAYOUT.labelGap : 0
  const innerWidth = availableWidth - STRIP_LAYOUT.containerHorizontalPadding - labelReserved

  const slotsWithoutButton = slotsFor(innerWidth)
  if (totalDrafts <= slotsWithoutButton) {
    return { displayCount: totalDrafts, showViewAll: false }
  }

  const innerWidthWithButton = innerWidth - STRIP_LAYOUT.viewAllButtonWidth - STRIP_LAYOUT.viewAllButtonGap
  const slotsWithButton = Math.max(1, slotsFor(innerWidthWithButton))
  const displayCount = Math.min(slotsWithButton, totalDrafts)
  return { displayCount, showViewAll: displayCount < totalDrafts }
}
