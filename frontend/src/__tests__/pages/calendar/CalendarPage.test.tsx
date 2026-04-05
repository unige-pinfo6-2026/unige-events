// @vitest-environment jsdom

import React from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { CalendarEvent } from '@/hooks/useCalendarEvents'

vi.mock('@/hooks/useCalendarEvents', () => ({
  useCalendarEvents: vi.fn(),
}))

vi.mock('react-big-calendar', () => ({
  Calendar: ({
    events,
    onSelectEvent,
    onView,
    view,
    eventPropGetter,
    tooltipAccessor,
  }: {
    events: CalendarEvent[]
    onSelectEvent: (e: CalendarEvent) => void
    onView: (v: string) => void
    view: string
    eventPropGetter: (e: CalendarEvent) => { style: React.CSSProperties }
    tooltipAccessor: (e: CalendarEvent) => string
  }) => (
    <div data-testid="rbc-calendar" data-view={view}>
      {events.map((e) => {
        const { style } = eventPropGetter(e)
        const tooltip = tooltipAccessor(e)
        return (
          <button
            key={e.resource.id}
            onClick={() => onSelectEvent(e)}
            style={style}
            title={tooltip}
          >
            {e.title}
          </button>
        )
      })}
      <button onClick={() => onView('week')}>Semaine</button>
      <button onClick={() => onView('day')}>Jour</button>
      <button onClick={() => onView('agenda')}>Agenda</button>
    </div>
  ),
  dateFnsLocalizer: vi.fn(() => ({})),
  Views: { MONTH: 'month', WEEK: 'week', DAY: 'day', AGENDA: 'agenda' },
}))

vi.mock('date-fns', () => ({
  format: vi.fn(),
  parse: vi.fn(),
  startOfWeek: vi.fn(),
  getDay: vi.fn(),
}))

vi.mock('date-fns/locale', () => ({ fr: {} }))
vi.mock('react-big-calendar/lib/css/react-big-calendar.css', () => ({}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

import { useCalendarEvents } from '@/hooks/useCalendarEvents'
import CalendarPage from '@/pages/calendar/CalendarPage'

const mockUseCalendarEvents = useCalendarEvents as ReturnType<typeof vi.fn>

const makeCalendarEvent = (id: number, title: string): CalendarEvent => ({
  title,
  start: new Date('2026-04-10T09:00:00'),
  end: new Date('2026-04-10T11:00:00'),
  resource: {
    id,
    title,
    description: 'Description',
    location: 'Salle 1',
    startDate: '2026-04-10T09:00:00',
    endDate: '2026-04-10T11:00:00',
    category: 'CONFERENCE',
    status: 'PUBLISHED',
    creatorId: 'u1',
    createdAt: '2026-03-01T00:00:00',
  },
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <CalendarPage />
    </MemoryRouter>,
  )
}

describe('CalendarPage', () => {
  it('shows a spinner while loading', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: true, error: null })
    renderPage()
    expect(document.querySelector('.spinner')).toBeTruthy()
  })

  it('shows the error message when fetch fails', () => {
    mockUseCalendarEvents.mockReturnValue({
      events: [],
      loading: false,
      error: 'Impossible de charger les événements.',
    })
    renderPage()
    expect(screen.getByText('Impossible de charger les événements.')).toBeTruthy()
  })

  it('does not render the calendar while loading', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: true, error: null })
    renderPage()
    expect(screen.queryByTestId('rbc-calendar')).toBeNull()
  })

  it('renders the calendar with events when loaded', () => {
    mockUseCalendarEvents.mockReturnValue({
      events: [makeCalendarEvent(1, 'Conférence IA'), makeCalendarEvent(2, 'Hackathon')],
      loading: false,
      error: null,
    })
    renderPage()
    expect(screen.getByTestId('rbc-calendar')).toBeTruthy()
    expect(screen.getByText('Conférence IA')).toBeTruthy()
    expect(screen.getByText('Hackathon')).toBeTruthy()
  })

  it('navigates to event detail page when an event is clicked', () => {
    mockUseCalendarEvents.mockReturnValue({
      events: [makeCalendarEvent(42, 'Workshop ML')],
      loading: false,
      error: null,
    })
    renderPage()
    fireEvent.click(screen.getByText('Workshop ML'))
    expect(mockNavigate).toHaveBeenCalledWith('/events/42')
  })

  it('switches to week view when the Semaine button is clicked', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
    renderPage()
    fireEvent.click(screen.getByText('Semaine'))
    expect(screen.getByTestId('rbc-calendar').getAttribute('data-view')).toBe('week')
  })

  it('switches to day view when the Jour button is clicked', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
    renderPage()
    fireEvent.click(screen.getByText('Jour'))
    expect(screen.getByTestId('rbc-calendar').getAttribute('data-view')).toBe('day')
  })

  it('renders an empty calendar when there are no events', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
    renderPage()
    expect(screen.getByTestId('rbc-calendar')).toBeTruthy()
  })

  it('applies category colour to events via eventPropGetter', () => {
    mockUseCalendarEvents.mockReturnValue({
      events: [makeCalendarEvent(1, 'Conférence IA')],
      loading: false,
      error: null,
    })
    renderPage()
    const btn = screen.getByText('Conférence IA')
    expect(btn.style.backgroundColor).toBe('rgb(8, 145, 178)') // CONFERENCE = #0891b2
  })

  it('shows event location as tooltip via tooltipAccessor', () => {
    mockUseCalendarEvents.mockReturnValue({
      events: [makeCalendarEvent(1, 'Workshop')],
      loading: false,
      error: null,
    })
    renderPage()
    expect(screen.getByTitle('Salle 1')).toBeTruthy()
  })
})
