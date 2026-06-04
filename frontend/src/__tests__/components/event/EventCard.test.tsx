
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import EventCard from '@/components/event/EventCard'
import { type Event } from '@/types/event'
import { type Faculty } from '@/types/faculty'

vi.mock('@/hooks/useFavorite', () => ({
  useFavorite: () => ({ favorited: false, loading: false, toggle: vi.fn() }),
}))

const mockEvent: Event = {
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
  capacity: 200,
  attendingCount: 0,
  createdAt: '2026-03-01T10:00:00',
}

afterEach(() => cleanup())

function renderCard(event: Event) {
  return render(
    <MemoryRouter>
      <EventCard event={event} />
    </MemoryRouter>,
  )
}

describe('EventCard', () => {
  it('renders event title', () => {
    renderCard(mockEvent)
    expect(screen.getByText('Conférence IA')).toBeTruthy()
  })

  it('renders event location', () => {
    renderCard(mockEvent)
    expect(screen.getByText(/Uni Dufour/)).toBeTruthy()
  })

  it('renders category badge', () => {
    renderCard(mockEvent)
    expect(screen.getByText('Conférence')).toBeTruthy()
  })

  it('renders capacity', () => {
    renderCard(mockEvent)
    expect(screen.getByText(/200 places/)).toBeTruthy()
  })

  it('links to event detail page', () => {
    renderCard(mockEvent)
    const link = screen.getByRole('link')
    expect(link.getAttribute('href')).toBe('/events/1')
  })

  it('does not render capacity when not provided', () => {
    renderCard({ ...mockEvent, capacity: undefined })
    expect(screen.queryByText(/places/)).toBeNull()
  })

  it('renders the singular "place" label when capacity is exactly 1', () => {
    renderCard({ ...mockEvent, capacity: 1 })
    expect(screen.getByText(/1 place$/)).toBeTruthy()
    expect(screen.queryByText(/places/)).toBeNull()
  })

  it('renders all event categories without error', () => {
    const categories: Event['category'][] = ['ACADEMIC', 'SPORTS', 'CULTURAL', 'SOCIAL', 'CONFERENCE', 'OTHER']
    for (const category of categories) {
      const { unmount } = renderCard({ ...mockEvent, category })
      expect(screen.getAllByRole('article').length).toBeGreaterThan(0)
      unmount()
      cleanup()
    }
  })

  it('renders FacultyBadge with the faculty label when faculty is set', () => {
    renderCard({ ...mockEvent, faculty: 'SCIENCES' })
    expect(screen.getByText('Sciences')).toBeTruthy()
  })

  it('renders a neutral "Toutes facultés" badge when faculty is null', () => {
    renderCard({ ...mockEvent, faculty: null })
    expect(screen.getByText('Toutes facultés')).toBeTruthy()
  })

  it('renders a neutral "Toutes facultés" badge when faculty is undefined', () => {
    renderCard({ ...mockEvent, faculty: undefined })
    expect(screen.getByText('Toutes facultés')).toBeTruthy()
  })

  it('shows time when allDay is false', () => {
    renderCard({ ...mockEvent, allDay: false })
    expect(screen.getByText(/\d{2}:\d{2}/)).toBeTruthy()
  })

  it('hides time when allDay is true', () => {
    renderCard({ ...mockEvent, allDay: true })
    expect(screen.queryByText(/\d{2}:\d{2}/)).toBeNull()
  })

  it('renders the correct faculty label for each Faculty value', () => {
    const labels: Partial<Record<Faculty, string>> = {
      'LETTERS': 'Lettres',
      'LAW': 'Droit',
      'MEDICINE': 'Médecine',
    }

    for (const [faculty, label] of Object.entries(labels) as [Faculty, string][]) {
      const { unmount } = renderCard({ ...mockEvent, faculty })
      expect(screen.getByText(label)).toBeTruthy()
      unmount()
      cleanup()
    }
  })

  it('renders banner image when bannerUrl is set', () => {
    renderCard({ ...mockEvent, bannerUrl: 'https://example.com/banner.jpg' })
    const banner = document.querySelector<HTMLImageElement>('img[src*="banner.jpg"]')
    expect(banner).toBeTruthy()
  })

  it('renders gradient fallback when bannerUrl is absent', () => {
    renderCard({ ...mockEvent, bannerUrl: undefined })
    const banner = document.querySelector<HTMLElement>('[style*="linear-gradient"]')
    expect(banner).toBeTruthy()
  })

  it('renders description when present', () => {
    renderCard({ ...mockEvent, description: 'Une conférence passionnante.' })
    expect(screen.getByText('Une conférence passionnante.')).toBeTruthy()
  })

  describe('user-content overflow wrapping', () => {
    const LONG = 'a'.repeat(200)

    it('applies wrap-anywhere on the title so a long unbroken word breaks instead of overflowing', () => {
      renderCard({ ...mockEvent, title: LONG })
      const heading = screen.getByRole('heading', { level: 3 })
      expect(heading.textContent).toBe(LONG)
      expect(heading.className).toContain('wrap-anywhere')
      expect(heading.className).not.toContain('break-all')
    })

    it('applies wrap-anywhere on the description', () => {
      renderCard({ ...mockEvent, description: LONG })
      const p = screen.getByText(LONG)
      expect(p.className).toContain('wrap-anywhere')
      expect(p.className).not.toContain('break-all')
    })
  })

  describe('recurrence badge (SCRUM-151)', () => {
    it('renders the Récurrent pill when parentEventId is set', () => {
      renderCard({ ...mockEvent, parentEventId: 42 })
      expect(screen.getByText('Récurrent')).toBeTruthy()
    })

    it('does not render the badge for a standalone event', () => {
      renderCard(mockEvent)
      expect(screen.queryByText('Récurrent')).toBeNull()
    })

    it('does not render the badge for the parent (recurrenceRule set, parentEventId null)', () => {
      renderCard({
        ...mockEvent,
        parentEventId: null,
        recurrenceRule: 'FREQ=WEEKLY;UNTIL=20260601',
      })
      expect(screen.queryByText('Récurrent')).toBeNull()
    })
  })

  describe('cancelled badge', () => {
    it('renders an "Annulé" badge when the event is CANCELLED', () => {
      renderCard({ ...mockEvent, status: 'CANCELLED' })
      expect(screen.getByText('Annulé')).toBeTruthy()
    })

    it('does not render the "Annulé" badge for a non-cancelled event', () => {
      renderCard(mockEvent)
      expect(screen.queryByText('Annulé')).toBeNull()
    })
  })
})
