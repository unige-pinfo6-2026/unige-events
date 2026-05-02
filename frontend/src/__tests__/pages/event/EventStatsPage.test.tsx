// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'

vi.mock('@/hooks', () => ({
  useAuth: vi.fn(),
  useEvent: vi.fn(),
}))

vi.mock('@/hooks/useEventStats', () => ({
  useEventStats: vi.fn(),
}))

vi.mock('@/services/statsApi', () => ({
  getEventAttendees: vi.fn(),
}))

vi.mock('@/services/userService', () => ({
  getUserById: vi.fn(),
}))

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: () => ({ theme: 'light' }),
}))

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  BarChart: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Bar: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  Cell: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
}))

import { useAuth, useEvent } from '@/hooks'
import { useEventStats } from '@/hooks/useEventStats'
import { getEventAttendees } from '@/services/statsApi'
import { getUserById } from '@/services/userService'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockUseEvent = useEvent as ReturnType<typeof vi.fn>
const mockUseEventStats = useEventStats as ReturnType<typeof vi.fn>
const mockGetEventAttendees = getEventAttendees as ReturnType<typeof vi.fn>
const mockGetUserById = getUserById as ReturnType<typeof vi.fn>

const mockUser = { id: 'user-1', email: 'org@test.com', profilePublic: true, admin: false, createdAt: '' }

const mockEvent = {
  id: 42,
  title: 'Test Event',
  location: 'Geneva',
  startDate: '2026-06-01T10:00:00',
  endDate: '2026-06-01T12:00:00',
  category: 'CONFERENCE' as const,
  status: 'PUBLISHED' as const,
  creatorId: 'user-1',
  attendingCount: 10,
  allDay: false,
  createdAt: '2026-01-01',
  capacity: 50,
}

const mockStats = { viewCount: 142, attendingCount: 38, interestedCount: 21 }

function renderPage(eventId = '42') {
  return render(
    <MemoryRouter initialEntries={[`/events/${eventId}/stats`]}>
      <Routes>
        <Route path="/events/:id/stats" element={<EventStatsPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

import EventStatsPage from '@/pages/event/EventStatsPage'

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('EventStatsPage', () => {
  it('shows error for invalid event id', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    renderPage('abc')
    expect(screen.getByText(/identifiant/i)).toBeTruthy()
  })

  it('shows skeleton while loading', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: true, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: true, error: null })

    renderPage()
    expect(screen.getByText(/statistiques de/i)).toBeTruthy()
  })

  it('shows access denied for non-organizer', async () => {
    const otherUser = { ...mockUser, id: 'user-other' }
    mockUseAuth.mockReturnValue({ user: otherUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getByText(/accès réservé/i)).toBeTruthy()
    )
  })

  it('shows error when event fails to load', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, error: 'Impossible de charger cet événement.' })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getByText(/impossible de charger/i)).toBeTruthy()
    )
  })

  it('shows "événement introuvable" when event is null without error', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getByText(/introuvable/i)).toBeTruthy()
    )
  })

  it('shows error when stats fail to load', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: 'Impossible de charger les statistiques.' })

    renderPage()
    await waitFor(() =>
      expect(screen.getByText(/impossible de charger les statistiques/i)).toBeTruthy()
    )
  })

  it('shows "statistiques indisponibles" when stats is null without error', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getByText(/statistiques indisponibles/i)).toBeTruthy()
    )
  })

  it('renders KPI values for the organizer', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })

    renderPage()
    await waitFor(() => {
      expect(screen.getAllByText('142').length).toBeGreaterThan(0)
      expect(screen.getAllByText('38').length).toBeGreaterThan(0)
      expect(screen.getAllByText('21').length).toBeGreaterThan(0)
    })
  })

  it('renders capacity fill bar when event has capacity', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getByText(/taux de remplissage/i)).toBeTruthy()
    )
  })

  it('does not render capacity bar when event has no capacity', async () => {
    const eventNoCapacity = { ...mockEvent, capacity: undefined }
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: eventNoCapacity, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())
    expect(screen.queryByText(/taux de remplissage/i)).toBeNull()
  })

  it('renders the attendees toggle button', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getByText(/voir les participants/i)).toBeTruthy()
    )
  })

  it('loads and shows attendees when toggle is clicked', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })
    mockGetEventAttendees.mockResolvedValue([
      { id: 1, userId: 'u-1', eventId: 42, status: 'ATTENDING', createdAt: '2026-01-01' },
    ])
    mockGetUserById.mockResolvedValue({
      id: 'u-1', email: 'alice@test.com', displayName: 'Alice Martin',
      profilePublic: true, admin: false, createdAt: '',
    })

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    await waitFor(() => expect(screen.getByText('Alice Martin')).toBeTruthy())
  })

  it('falls back to email when attendee user has no displayName', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })
    mockGetEventAttendees.mockResolvedValue([
      { id: 2, userId: 'u-2', eventId: 42, status: 'ATTENDING', createdAt: '2026-01-01' },
    ])
    mockGetUserById.mockResolvedValue({
      id: 'u-2', email: 'bob@test.com',
      profilePublic: true, admin: false, createdAt: '',
    })

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    await waitFor(() => expect(screen.getByText('bob@test.com')).toBeTruthy())
  })

  it('falls back to userId when user fetch fails', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })
    mockGetEventAttendees.mockResolvedValue([
      { id: 3, userId: 'u-unknown', eventId: 42, status: 'ATTENDING', createdAt: '2026-01-01' },
    ])
    mockGetUserById.mockRejectedValue(new Error('Not found'))

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    await waitFor(() => expect(screen.getByText('u-unknown')).toBeTruthy())
  })

  it('shows loading state while fetching attendees', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })

    let resolveAttendees: (v: unknown[]) => void
    mockGetEventAttendees.mockImplementation(
      () => new Promise(r => { resolveAttendees = r }),
    )

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    expect(screen.getByText(/chargement/i)).toBeTruthy()

    await waitFor(() => { resolveAttendees([]) })
  })

  it('shows error when attendees fetch fails', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })
    mockGetEventAttendees.mockRejectedValue(new Error('Forbidden'))

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    await waitFor(() =>
      expect(screen.getByText(/impossible de charger les participants/i)).toBeTruthy()
    )
  })

  it('shows empty state when no attendees', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })
    mockGetEventAttendees.mockResolvedValue([])

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    await waitFor(() =>
      expect(screen.getByText(/aucun participant/i)).toBeTruthy()
    )
  })

  it('collapses attendees section when toggle is clicked again', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })
    mockGetEventAttendees.mockResolvedValue([])

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    await waitFor(() => expect(mockGetEventAttendees).toHaveBeenCalledTimes(1))

    fireEvent.click(screen.getByText(/voir les participants/i))
    expect(screen.queryByText(/aucun participant/i)).toBeNull()
  })

  it('does not re-fetch attendees on second open', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })
    mockGetEventAttendees.mockResolvedValue([])

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    await waitFor(() => expect(mockGetEventAttendees).toHaveBeenCalledTimes(1))

    fireEvent.click(screen.getByText(/voir les participants/i)) // close
    fireEvent.click(screen.getByText(/voir les participants/i)) // reopen
    expect(mockGetEventAttendees).toHaveBeenCalledTimes(1) // no second fetch
  })
})
