import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Timeline from '@/components/feed/Timeline'
import type { FeedGroup } from '@/hooks/useFeed'
import type { Event } from '@/types/event'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('@/components/feed/EventFeedCard', () => ({
  default: ({ event }: { event: Event }) => (
    <div data-testid={`event-card-${event.id}`}>{event.title}</div>
  ),
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

function localDateKey(offsetDays: number): string {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

function makeEvent(id: number): Event {
  return {
    id,
    title: `Event ${id}`,
    location: 'Somewhere',
    startDate: '2026-04-28T10:00:00Z',
    endDate: '2026-04-28T12:00:00Z',
    category: 'CONFERENCE',
    status: 'PUBLISHED',
    creatorId: 'user-1',
    createdAt: '2026-01-01T00:00:00',
    allDay: false,
    attendingCount: 0,
  }
}

function renderTimeline(groups: FeedGroup[]) {
  return render(
    <MemoryRouter>
      <Timeline groups={groups} />
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('Timeline', () => {
  it('renders the vertical line', () => {
    const groups: FeedGroup[] = [{ date: '2026-04-28', events: [makeEvent(1)] }]
    renderTimeline(groups)
    expect(screen.getByTestId('timeline-line')).toBeDefined()
  })

  it('renders one date marker per group', () => {
    const groups: FeedGroup[] = [
      { date: '2026-04-28', events: [makeEvent(1)] },
      { date: '2026-04-29', events: [makeEvent(2)] },
    ]
    renderTimeline(groups)
    expect(screen.getAllByTestId('date-marker')).toHaveLength(2)
  })

  it('renders all EventFeedCard components', () => {
    const groups: FeedGroup[] = [
      { date: '2026-04-28', events: [makeEvent(1), makeEvent(2)] },
      { date: '2026-04-29', events: [makeEvent(3)] },
    ]
    renderTimeline(groups)
    expect(screen.getByTestId('event-card-1')).toBeDefined()
    expect(screen.getByTestId('event-card-2')).toBeDefined()
    expect(screen.getByTestId('event-card-3')).toBeDefined()
  })

  it('shows "Aujourd\'hui" label for today\'s date key', () => {
    const groups: FeedGroup[] = [{ date: localDateKey(0), events: [makeEvent(1)] }]
    renderTimeline(groups)
    expect(screen.getByText("Aujourd'hui")).toBeDefined()
  })

  it('shows "Demain" label for tomorrow\'s date key', () => {
    const groups: FeedGroup[] = [{ date: localDateKey(1), events: [makeEvent(1)] }]
    renderTimeline(groups)
    expect(screen.getByText('Demain')).toBeDefined()
  })

  it('renders a formatted date label for future dates', () => {
    // Use a fixed future date — label should not be "Aujourd'hui" or "Demain"
    const groups: FeedGroup[] = [{ date: '2030-12-25', events: [makeEvent(1)] }]
    renderTimeline(groups)
    const markers = screen.getAllByTestId('date-marker')
    expect(markers[0].textContent).not.toContain("Aujourd'hui")
    expect(markers[0].textContent).not.toContain('Demain')
    // Formatted date should mention the month or day
    expect(markers[0].textContent?.length).toBeGreaterThan(0)
  })

  it('renders empty without errors when groups is empty', () => {
    renderTimeline([])
    expect(screen.queryByTestId('date-marker')).toBeNull()
    expect(screen.getByTestId('timeline-line')).toBeDefined()
  })
})
