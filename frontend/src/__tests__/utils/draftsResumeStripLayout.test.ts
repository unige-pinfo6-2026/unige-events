import { describe, expect, it } from 'vitest'
import { computeStripLayout } from '@/utils/draftsResumeStripLayout'

describe('computeStripLayout', () => {
  it('returns empty layout when there are no drafts', () => {
    expect(computeStripLayout(2000, 0)).toEqual({ displayCount: 0, showViewAll: false })
  })

  it('stays optimistic before the first measurement (width = 0)', () => {
    expect(computeStripLayout(0, 4)).toEqual({ displayCount: 4, showViewAll: false })
  })

  it('displays every draft when the container is very large', () => {
    expect(computeStripLayout(2000, 2)).toEqual({ displayCount: 2, showViewAll: false })
  })

  it('hides the "Voir tout" button when all drafts fit naturally', () => {
    const layout = computeStripLayout(1700, 4)
    expect(layout.showViewAll).toBe(false)
    expect(layout.displayCount).toBe(4)
  })

  it('shows the "Voir tout" button when there are more drafts than slots', () => {
    const layout = computeStripLayout(1700, 8)
    expect(layout.showViewAll).toBe(true)
    expect(layout.displayCount).toBeGreaterThanOrEqual(1)
    expect(layout.displayCount).toBeLessThan(8)
  })

  it('collapses to a single card on a narrow container with many drafts', () => {
    const layout = computeStripLayout(400, 3)
    expect(layout.displayCount).toBe(1)
    expect(layout.showViewAll).toBe(true)
  })

  it('hides the "Voir tout" button when there is a single draft even on a narrow container', () => {
    const layout = computeStripLayout(200, 1)
    expect(layout.displayCount).toBe(1)
    expect(layout.showViewAll).toBe(false)
  })

  it('ignores the left label on mobile (< sm breakpoint)', () => {
    // At width = 500 (< 640 sm breakpoint) no label is reserved, so more room
    // remains for cards than the desktop layout at the same width would give.
    const mobileLayout = computeStripLayout(500, 1)
    expect(mobileLayout.displayCount).toBe(1)
    expect(mobileLayout.showViewAll).toBe(false)
  })

  it('never returns a negative displayCount', () => {
    const layout = computeStripLayout(100, 5)
    expect(layout.displayCount).toBeGreaterThanOrEqual(1)
    expect(layout.showViewAll).toBe(true)
  })

  it('still shows one card when the inner width goes non-positive after padding', () => {
    // availableWidth = 20 is positive (so computeStripLayout does not early-return)
    // but after the 32px horizontal padding innerWidth = -12 ≤ 0, driving
    // slotsFor into its `usableWidth <= 0 → return 0` branch @line 18. The
    // Math.max(1, …) floor then keeps a single card on screen.
    const layout = computeStripLayout(20, 3)
    expect(layout.displayCount).toBe(1)
    expect(layout.showViewAll).toBe(true)
  })

  it('keeps the "Voir tout" button from stealing a card slot on a medium container', () => {
    // Regression: on a container ~1024 px (SectionWrapper size="lg"), the old algorithm
    // reserved the button width before computing slots and collapsed from 2 cards to 1.
    // With the new rule the button appends to the rail without costing a slot.
    const layout = computeStripLayout(1024, 3)
    expect(layout.displayCount).toBe(2)
    expect(layout.showViewAll).toBe(true)
  })
})
