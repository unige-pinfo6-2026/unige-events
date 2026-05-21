import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import EventFeedCard from '@/components/feed/EventFeedCard'
import type { Event } from '@/types/event'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('@/components/event/FavoriteButton', () => ({
  default: ({ eventId }: { eventId: number }) => (
    <button type="button" data-testid="favorite-button" aria-label={`Favoris ${eventId}`} />
  ),
}))

vi.mock('@/components/event/EventBanner', () => ({
  default: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="event-banner">{children}</div>
  ),
}))

vi.mock('@/components/faculty/FacultyBadge', () => ({
  default: ({ id }: { id: string }) => (
    <span data-testid="faculty-badge">{id}</span>
  ),
}))

// ─── Factory ──────────────────────────────────────────────────────────────────

function makeEvent(overrides: Partial<Event> = {}): Event {
  return {
    id: 42,
    title: 'Conférence IA',
    location: 'Uni Dufour, Salle 1',
    startDate: '2026-04-28T14:00:00Z',
    endDate: '2026-04-28T17:00:00Z',
    category: 'CONFERENCE',
    faculty: 'SCIENCES',
    status: 'PUBLISHED',
    creatorId: 'user-1',
    createdAt: '2026-03-01T00:00:00',
    allDay: false,
    attendingCount: 10,
    ...overrides,
  }
}

function renderCard(event: Event) {
  return render(
    <MemoryRouter>
      <EventFeedCard event={event} />
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('EventFeedCard', () => {
  it('renders the event title', () => {
    renderCard(makeEvent())
    expect(screen.getByText('Conférence IA')).toBeDefined()
  })

  it('renders the event location', () => {
    renderCard(makeEvent())
    expect(screen.getByText('Uni Dufour, Salle 1')).toBeDefined()
  })

  it('renders the start time', () => {
    renderCard(makeEvent({ startDate: '2026-04-28T14:00:00Z', allDay: false }))
    // Time is localised; just verify some time-like text is present
    const article = screen.getByRole('article')
    expect(article.textContent).toMatch(/\d{1,2}[h:]\d{2}|Toute la journée/)
  })

  it('renders "Toute la journée" for all-day events', () => {
    renderCard(makeEvent({ allDay: true }))
    expect(screen.getByText('Toute la journée')).toBeDefined()
  })

  it('renders FavoriteButton', () => {
    renderCard(makeEvent())
    expect(screen.getByTestId('favorite-button')).toBeDefined()
  })

  it('renders FacultyBadge when faculty is set', () => {
    renderCard(makeEvent({ faculty: 'SCIENCES' }))
    expect(screen.getByTestId('faculty-badge')).toBeDefined()
  })

  it('does not render FacultyBadge when faculty is absent', () => {
    renderCard(makeEvent({ faculty: null }))
    expect(screen.queryByTestId('faculty-badge')).toBeNull()
  })

  it('links to /events/:id', () => {
    renderCard(makeEvent({ id: 42 }))
    const link = screen.getByRole('link')
    expect(link.getAttribute('href')).toBe('/events/42')
  })

  it('shows "Complet" when availableSpots is 0 and capacity is set', () => {
    renderCard(makeEvent({ capacity: 50, availableSpots: 0 }))
    expect(screen.getByText('Complet')).toBeDefined()
  })

  it('shows remaining spots when availableSpots <= 10', () => {
    renderCard(makeEvent({ capacity: 50, availableSpots: 3 }))
    expect(screen.getByText('3 places restantes')).toBeDefined()
  })

  it('shows total capacity when availableSpots is above 10', () => {
    renderCard(makeEvent({ capacity: 100, availableSpots: 80 }))
    expect(screen.getByText('100 places')).toBeDefined()
  })

  it('shows no capacity indicator when capacity is not set', () => {
    renderCard(makeEvent({ capacity: undefined, availableSpots: undefined }))
    expect(screen.queryByText(/place/i)).toBeNull()
  })

  it('shows singular "place restante" for 1 spot left', () => {
    renderCard(makeEvent({ capacity: 10, availableSpots: 1 }))
    expect(screen.getByText('1 place restante')).toBeDefined()
  })
})
