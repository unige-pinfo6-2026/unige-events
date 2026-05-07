import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import FeaturedEventsSection from '@/components/event/FeaturedEventsSection'
import type { Event } from '@/types/event'

vi.mock('@/hooks/useFeaturedEvents', () => ({
  useFeaturedEvents: vi.fn(),
}))

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: vi.fn(() => ({ theme: 'dark', toggleTheme: vi.fn() })),
}))

vi.mock('@/hooks/useFavorite', () => ({
  useFavorite: () => ({ favorited: false, loading: false, toggle: vi.fn() }),
}))

import { useFeaturedEvents } from '@/hooks/useFeaturedEvents'
import { useTheme } from '@/contexts/ThemeContext'

const mockUseFeaturedEvents = useFeaturedEvents as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const baseEvent: Event = {
  id: 1,
  title: 'Conférence IA',
  location: 'Uni Dufour',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  allDay: false,
  category: 'CONFERENCE',
  faculty: null,
  status: 'PUBLISHED',
  creatorId: 'user-1',
  capacity: 100,
  attendingCount: 0,
  featured: false,
  createdAt: '2026-03-01T10:00:00',
}

const makeEvents = (count: number, overrides: Partial<Event> = {}): Event[] =>
  Array.from({ length: count }, (_, i) => ({
    ...baseEvent,
    id: i + 1,
    title: `Event ${i + 1}`,
    ...overrides,
  }))

function renderSection() {
  return render(
    <MemoryRouter>
      <FeaturedEventsSection />
    </MemoryRouter>,
  )
}

describe('FeaturedEventsSection', () => {
  it('renders 6 events when API returns 6', () => {
    mockUseFeaturedEvents.mockReturnValue({ events: makeEvents(6), loading: false, error: null })
    renderSection()
    for (let i = 1; i <= 6; i++) {
      expect(screen.getByText(`Event ${i}`)).toBeTruthy()
    }
  })

  it('renders fewer than 6 events when API returns fewer', () => {
    mockUseFeaturedEvents.mockReturnValue({ events: makeEvents(3), loading: false, error: null })
    renderSection()
    expect(screen.getAllByText(/^Event \d$/)).toHaveLength(3)
  })

  it('caps the rendered list at 6 if API returns more (defensive slice)', () => {
    mockUseFeaturedEvents.mockReturnValue({ events: makeEvents(9), loading: false, error: null })
    renderSection()
    expect(screen.getAllByText(/^Event \d$/)).toHaveLength(6)
  })

  it('renders nothing when there are no events, no loading and no error', () => {
    mockUseFeaturedEvents.mockReturnValue({ events: [], loading: false, error: null })
    const { container } = renderSection()
    expect(container.firstChild).toBeNull()
    expect(screen.queryByText('À la une')).toBeNull()
  })

  it('renders the skeleton during the loading state', () => {
    mockUseFeaturedEvents.mockReturnValue({ events: [], loading: true, error: null })
    renderSection()
    expect(document.querySelector('[data-boneyard="event-cards"]')).toBeTruthy()
    expect(screen.getByText('À la une')).toBeTruthy()
  })

  it('uses the light skeleton color when theme is light', () => {
    mockUseTheme.mockReturnValue({ theme: 'light', toggleTheme: vi.fn() })
    mockUseFeaturedEvents.mockReturnValue({ events: [], loading: true, error: null })
    renderSection()
    expect(document.querySelector('[data-boneyard="event-cards"]')).toBeTruthy()
  })

  it('renders an error message when the hook returns an error', () => {
    mockUseFeaturedEvents.mockReturnValue({
      events: [],
      loading: false,
      error: 'Impossible de charger les événements à la une.',
    })
    renderSection()
    expect(screen.getByText('Impossible de charger les événements à la une.')).toBeTruthy()
    expect(screen.getByText('À la une')).toBeTruthy()
  })

  it('shows the "À la une" badge only on featured events', () => {
    mockUseFeaturedEvents.mockReturnValue({
      events: [
        { ...baseEvent, id: 1, title: 'Featured one', featured: true },
        { ...baseEvent, id: 2, title: 'Featured two', featured: true },
        { ...baseEvent, id: 3, title: 'Plain', featured: false },
        { ...baseEvent, id: 4, title: 'Plain again', featured: undefined },
      ],
      loading: false,
      error: null,
    })
    renderSection()
    const badges = screen.getAllByText(/À la une/i).filter((el) => el.tagName === 'SPAN')
    expect(badges).toHaveLength(2)
    badges.forEach((badge) => expect(badge.textContent).toContain('✨'))
  })

  it('does not render the badge on any card when no event is featured', () => {
    mockUseFeaturedEvents.mockReturnValue({ events: makeEvents(3), loading: false, error: null })
    renderSection()
    const sparkleBadges = screen.queryAllByText(/✨/)
    expect(sparkleBadges).toHaveLength(0)
  })

  it('renders the "À la une" section heading when events are present', () => {
    mockUseFeaturedEvents.mockReturnValue({ events: makeEvents(2), loading: false, error: null })
    renderSection()
    expect(screen.getByRole('heading', { name: 'À la une' })).toBeTruthy()
  })
})
