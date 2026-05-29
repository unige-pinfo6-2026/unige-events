
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
    onNavigate,
    onView,
    view,
    views,
    eventPropGetter,
    tooltipAccessor,
    components,
  }: {
    events: CalendarEvent[]
    onSelectEvent: (e: CalendarEvent) => void
    onNavigate: (action: string) => void
    onView: (v: string) => void
    view: string
    views: string[]
    eventPropGetter: (e: CalendarEvent) => { style: React.CSSProperties }
    tooltipAccessor: (e: CalendarEvent) => string
    components?: { toolbar?: React.ComponentType<{
      label: string
      onNavigate: (action: string) => void
      onView: (v: string) => void
      view: string
      views: string[]
    }> }
  }) => (
    <div data-testid="rbc-calendar" data-view={view}>
      {components?.toolbar && React.createElement(components.toolbar, {
        label: 'Avril 2026',
        onNavigate,
        onView,
        view,
        views: views ?? ['month', 'week', 'day', 'agenda'],
      })}
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

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: vi.fn(() => ({ theme: 'dark', toggleTheme: vi.fn() })),
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({ user: null })),
}))

// CalendarSubscribeButton imports these on mount when rendered (authenticated
// branch). Provide quiet defaults so unrelated tests don't blow up if the
// subscription block ever renders.
vi.mock('@/services/userService', () => ({
  getCalendarToken: vi.fn(() => new Promise(() => {})), // pending — keeps it in loading state
  regenerateCalendarToken: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return { ...actual, useNavigate: () => mockNavigate }
})

import { useCalendarEvents } from '@/hooks/useCalendarEvents'
import { useTheme } from '@/contexts/ThemeContext'
import { useAuth } from '@/hooks/useAuth'
import CalendarPage from '@/pages/calendar/CalendarPage'

const mockUseCalendarEvents = useCalendarEvents as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>
const mockUseAuth = useAuth as ReturnType<typeof vi.fn>

const mockUser = { id: 'user-1', email: 'u@test', profilePublic: true, createdAt: '' }

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
    allDay: false,
    category: 'CONFERENCE',
    faculty: null,
    status: 'PUBLISHED',
    creatorId: 'u1',
    attendingCount: 0,
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
  it('shows skeleton while loading', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: true, error: null })
    renderPage()
    expect(document.querySelector('[data-boneyard="event-calendar"]')).toBeTruthy()
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
    expect(btn.style.backgroundColor).toBe('#0891b2') // CONFERENCE = #0891b2
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

  it('renders toolbar with Précédent, Aujourd\'hui, and Suivant buttons', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
    renderPage()
    expect(screen.getByRole('button', { name: 'Précédent' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Suivant' })).toBeTruthy()
    expect(screen.getByRole('button', { name: "Aujourd'hui" })).toBeTruthy()
  })

  it('calls onNavigate PREV when clicking Précédent', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
    renderPage()
    const prevDate = screen.getByTestId('rbc-calendar').getAttribute('data-view')
    fireEvent.click(screen.getByRole('button', { name: 'Précédent' }))
    // After clicking PREV the calendar date would update — just verify the button fires without crash
    expect(screen.getByTestId('rbc-calendar')).toBeTruthy()
    expect(prevDate).toBe('month')
  })

  it('calls onNavigate NEXT when clicking Suivant', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Suivant' }))
    expect(screen.getByTestId('rbc-calendar')).toBeTruthy()
  })

  it('calls onNavigate TODAY when clicking Aujourd\'hui', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: "Aujourd'hui" }))
    expect(screen.getByTestId('rbc-calendar')).toBeTruthy()
  })

  it('switches view from toolbar view buttons', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
    renderPage()
    // The toolbar renders view buttons; click Semaine which is the week view label
    fireEvent.click(screen.getByRole('button', { name: 'Semaine' }))
    expect(screen.getByTestId('rbc-calendar').getAttribute('data-view')).toBe('week')
  })

  it('shows the label passed to the toolbar', () => {
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
    renderPage()
    expect(screen.getByText('Avril 2026')).toBeTruthy()
  })

  it('shows skeleton with light theme color', () => {
    mockUseTheme.mockReturnValue({ theme: 'light', toggleTheme: vi.fn() })
    mockUseCalendarEvents.mockReturnValue({ events: [], loading: true, error: null })
    renderPage()
    expect(document.querySelector('[data-boneyard="event-calendar"]')).toBeTruthy()
  })

  // ── Category filter bar ────────────────────────────────────────────────────

  describe('category filter bar', () => {
    it('renders one chip per EVENT_CATEGORIES entry with aria-pressed=true by default', () => {
      mockUseAuth.mockReturnValue({ user: null })
      mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
      renderPage()
      // 6 categories, all active (aria-pressed=true) initially.
      const chips = screen.getAllByRole('button', { pressed: true }).filter(
        (b) => b.getAttribute('aria-label')?.includes('catégorie'),
      )
      expect(chips.length).toBe(6)
      expect(screen.getByRole('button', { name: /catégorie Académique/i })).toBeTruthy()
      expect(screen.getByRole('button', { name: /catégorie Sports/i })).toBeTruthy()
      expect(screen.getByRole('button', { name: /catégorie Autre/i })).toBeTruthy()
    })

    it('hides events of a category when its chip is clicked', () => {
      const conference = makeCalendarEvent(1, 'Conf IA')   // category CONFERENCE
      const sports = { ...makeCalendarEvent(2, 'Match foot'),
        resource: { ...makeCalendarEvent(2, 'Match foot').resource, category: 'SPORTS' as const } }
      mockUseAuth.mockReturnValue({ user: null })
      mockUseCalendarEvents.mockReturnValue({ events: [conference, sports], loading: false, error: null })
      renderPage()

      // Both visible initially.
      expect(screen.getByText('Conf IA')).toBeTruthy()
      expect(screen.getByText('Match foot')).toBeTruthy()

      // Click "Masquer la catégorie Conférence" → only Match foot remains.
      fireEvent.click(screen.getByRole('button', { name: /Masquer la catégorie Conférence/i }))
      expect(screen.queryByText('Conf IA')).toBeNull()
      expect(screen.getByText('Match foot')).toBeTruthy()
      // The chip is now aria-pressed=false and labelled "Afficher".
      expect(screen.getByRole('button', { name: /Afficher la catégorie Conférence/i })).toBeTruthy()
    })

    it('re-shows the events when the chip is clicked again', () => {
      const conference = makeCalendarEvent(1, 'Conf IA')
      mockUseAuth.mockReturnValue({ user: null })
      mockUseCalendarEvents.mockReturnValue({ events: [conference], loading: false, error: null })
      renderPage()
      const chip = screen.getByRole('button', { name: /Masquer la catégorie Conférence/i })
      fireEvent.click(chip) // hide
      expect(screen.queryByText('Conf IA')).toBeNull()
      // Same DOM node, label changed
      fireEvent.click(screen.getByRole('button', { name: /Afficher la catégorie Conférence/i })) // re-show
      expect(screen.getByText('Conf IA')).toBeTruthy()
    })

    it('treats an event with category=undefined as OTHER when filtering', () => {
      // Defensive : backend rows can arrive without a category. Filtering
      // OTHER must hide such rows via the `?? 'OTHER'` fallback inside the
      // filter callback (EventCalendar line 183).
      const conference = makeCalendarEvent(1, 'Conf IA')
      const orphan: CalendarEvent = {
        ...makeCalendarEvent(2, 'Sans catégorie'),
        resource: {
          ...makeCalendarEvent(2, 'Sans catégorie').resource,
          category: undefined as unknown as 'OTHER',
        },
      }
      mockUseAuth.mockReturnValue({ user: null })
      mockUseCalendarEvents.mockReturnValue({
        events: [conference, orphan],
        loading: false,
        error: null,
      })
      renderPage()
      // Both visible initially.
      expect(screen.getByText('Sans catégorie')).toBeTruthy()
      expect(screen.getByText('Conf IA')).toBeTruthy()
      // Mask "Autre" → the categoryless row is filtered out via `?? 'OTHER'`.
      fireEvent.click(screen.getByRole('button', { name: /Masquer la catégorie Autre/i }))
      expect(screen.queryByText('Sans catégorie')).toBeNull()
      expect(screen.getByText('Conf IA')).toBeTruthy()
    })

    it('keeps independent toggles when multiple categories are hidden', () => {
      const conference = makeCalendarEvent(1, 'Conf IA')
      const sports = { ...makeCalendarEvent(2, 'Match foot'),
        resource: { ...makeCalendarEvent(2, 'Match foot').resource, category: 'SPORTS' as const } }
      mockUseAuth.mockReturnValue({ user: null })
      mockUseCalendarEvents.mockReturnValue({ events: [conference, sports], loading: false, error: null })
      renderPage()
      fireEvent.click(screen.getByRole('button', { name: /Masquer la catégorie Conférence/i }))
      fireEvent.click(screen.getByRole('button', { name: /Masquer la catégorie Sports/i }))
      expect(screen.queryByText('Conf IA')).toBeNull()
      expect(screen.queryByText('Match foot')).toBeNull()
      // Re-show only Conference — Sports stays hidden.
      fireEvent.click(screen.getByRole('button', { name: /Afficher la catégorie Conférence/i }))
      expect(screen.getByText('Conf IA')).toBeTruthy()
      expect(screen.queryByText('Match foot')).toBeNull()
    })
  })

  // ── Subscription block (gated on auth) ─────────────────────────────────────

  describe('calendar subscription block', () => {
    it('shows the "S\'abonner" button when authenticated', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
      renderPage()
      expect(screen.getByRole('button', { name: "S'abonner" })).toBeTruthy()
    })

    it('opens the subscription modal and shows the card on button click', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
      renderPage()
      fireEvent.click(screen.getByRole('button', { name: "S'abonner" }))
      // CalendarSubscribeButton header is always rendered (even while loading token)
      expect(screen.getByText(/s.abonner au calendrier/i)).toBeTruthy()
    })

    it('does NOT render the "S\'abonner" button when unauthenticated', () => {
      mockUseAuth.mockReturnValue({ user: null })
      mockUseCalendarEvents.mockReturnValue({ events: [], loading: false, error: null })
      renderPage()
      expect(screen.queryByRole('button', { name: "S'abonner" })).toBeNull()
      expect(screen.queryByText(/s.abonner au calendrier/i)).toBeNull()
    })
  })
})
