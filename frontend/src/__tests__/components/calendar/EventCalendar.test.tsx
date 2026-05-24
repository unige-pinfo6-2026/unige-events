import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import EventCalendar from '@/components/calendar/EventCalendar'
import { ThemeProvider } from '@/contexts/ThemeContext'
import type { CalendarEvent } from '@/hooks/useCalendarEvents'
import type { Event } from '@/types/event'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('@/hooks/useCalendarEvents', () => ({
  useCalendarEvents: vi.fn(),
}))

import { useCalendarEvents } from '@/hooks/useCalendarEvents'

const mockUseCalendarEvents = vi.mocked(useCalendarEvents)

// Anchor every event inside the current month so react-big-calendar paints the
// event bars (and therefore calls eventPropGetter — the line-160 target).
const now = new Date()
function dayThisMonth(day: number, hour: number): Date {
  return new Date(now.getFullYear(), now.getMonth(), day, hour, 0, 0)
}

function makeEvent(overrides: Partial<Event>): Event {
  return {
    id: 1,
    title: 'Conférence',
    location: 'Uni Dufour',
    startDate: dayThisMonth(15, 10).toISOString(),
    endDate: dayThisMonth(15, 12).toISOString(),
    category: 'ACADEMIC',
    creatorId: 'auth0|1',
    status: 'PUBLISHED',
    allDay: false,
    attendingCount: 0,
    createdAt: '2026-01-01T00:00:00',
    ...overrides,
  }
}

function makeCalendarEvent(event: Event): CalendarEvent {
  return {
    title: event.title,
    start: new Date(event.startDate),
    end: new Date(event.endDate),
    resource: event,
  }
}

function renderCalendar() {
  return render(
    <MemoryRouter>
      <ThemeProvider>
        <EventCalendar />
      </ThemeProvider>
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
  mockNavigate.mockReset()
})

describe('EventCalendar', () => {
  beforeEach(() => {
    // Two events: one with a real category (left side of `category ?? 'OTHER'`)
    // and one whose category is nulled by the backend contract (right-side
    // fallback @line 160).
    const withCategory = makeEvent({ id: 1, title: 'Conférence' })
    const withoutCategory = makeEvent({
      id: 2,
      title: 'Sans catégorie',
      startDate: dayThisMonth(16, 9).toISOString(),
      endDate: dayThisMonth(16, 11).toISOString(),
      // Defensive backend rows can arrive without a category — exercises `?? 'OTHER'`.
      category: undefined as unknown as Event['category'],
    })
    mockUseCalendarEvents.mockReturnValue({
      events: [makeCalendarEvent(withCategory), makeCalendarEvent(withoutCategory)],
      loading: false,
      error: null,
    })
  })

  it('renders the calendar toolbar with all four view buttons', () => {
    renderCalendar()
    // VIEW_LABELS lookup (line-143 left side) for the hardcoded view list.
    expect(screen.getByRole('button', { name: 'Mois' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Semaine' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Jour' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Agenda' })).toBeTruthy()
  })

  it('paints event bars and styles them via eventPropGetter (category + fallback)', () => {
    renderCalendar()
    const academic = screen.getByText('Conférence')
    const uncategorized = screen.getByText('Sans catégorie')
    // ACADEMIC → #2563eb ; missing category → OTHER → #6b7280 (happy-dom uses hex).
    expect(academic.closest('.rbc-event')?.getAttribute('style')).toContain('#2563eb')
    expect(uncategorized.closest('.rbc-event')?.getAttribute('style')).toContain('#6b7280')
  })

  it('navigates to the event detail page when an event is clicked', () => {
    renderCalendar()
    fireEvent.click(screen.getByText('Conférence'))
    expect(mockNavigate).toHaveBeenCalledWith('/events/1')
  })

  it('switches view when a toolbar view button is clicked', () => {
    renderCalendar()
    fireEvent.click(screen.getByRole('button', { name: 'Semaine' }))
    // The week view exposes a time-grid the month view does not.
    expect(document.querySelector('.rbc-time-view')).toBeTruthy()
  })

  it('moves the visible range with the Précédent / Suivant / Aujourd\'hui controls', () => {
    renderCalendar()
    fireEvent.click(screen.getByRole('button', { name: 'Précédent' }))
    fireEvent.click(screen.getByRole('button', { name: 'Suivant' }))
    fireEvent.click(screen.getByRole('button', { name: "Aujourd'hui" }))
    // The hook is re-queried as the current date changes month boundaries.
    expect(mockUseCalendarEvents).toHaveBeenCalled()
  })

  it('renders the skeleton fixture while loading', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: true, error: null })
    renderCalendar()
    expect(document.querySelector('[data-boneyard="event-calendar"]')).toBeTruthy()
    expect(screen.queryByRole('button', { name: 'Mois' })).toBeNull()
  })

  it('renders an error message when the hook reports an error', () => {
    mockUseCalendarEvents.mockReturnValue({
      events: [],
      loading: false,
      error: 'Impossible de charger les événements.',
    })
    renderCalendar()
    expect(screen.getByText('Impossible de charger les événements.')).toBeTruthy()
  })

  it('renders the empty agenda message when switching to Agenda with no matching rows', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
    renderCalendar()
    fireEvent.click(screen.getByRole('button', { name: 'Agenda' }))
    const agenda = document.querySelector('.rbc-agenda-view')
    expect(agenda).toBeTruthy()
    expect(within(agenda as HTMLElement).getByText('Aucun événement sur cette période.')).toBeTruthy()
  })
})
