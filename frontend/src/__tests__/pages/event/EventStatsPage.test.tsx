// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
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

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: vi.fn(() => ({ theme: 'light' })),
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
import { useTheme } from '@/contexts/ThemeContext'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockUseEvent = useEvent as ReturnType<typeof vi.fn>
const mockUseEventStats = useEventStats as ReturnType<typeof vi.fn>
const mockGetEventAttendees = getEventAttendees as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>

const mockUser = { id: 'user-1', email: 'org@test.com', profilePublic: true, createdAt: '' }

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

beforeEach(() => {
  mockUseTheme.mockReturnValue({ theme: 'light' })
})

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
    expect(screen.getByText('404')).toBeTruthy()
  })

  it('shows skeleton while loading', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: true, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: true, error: null })

    renderPage()
    expect(screen.getByText(/statistiques de/i)).toBeTruthy()
  })

  it('shows access denied for non-organizer non-admin', async () => {
    const otherUser = { ...mockUser, id: 'user-other' }
    mockUseAuth.mockReturnValue({ user: otherUser, isAdmin: false })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, coOrganizerOf: false }, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getByText('403')).toBeTruthy()
    )
  })

  it('grants access to an accepted co-organizer (event.coOrganizerOf === true)', async () => {
    const coOrgUser = { ...mockUser, id: 'user-coorg' }
    mockUseAuth.mockReturnValue({ user: coOrgUser, isAdmin: false })
    mockUseEvent.mockReturnValue({
      event: { ...mockEvent, coOrganizerOf: true },
      loading: false,
      error: null,
    })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getAllByText('142').length).toBeGreaterThan(0),
    )
  })

  it('grants access to a site admin (non-creator, non-co-organizer)', async () => {
    const adminUser = { ...mockUser, id: 'user-admin' }
    mockUseAuth.mockReturnValue({ user: adminUser, isAdmin: true })
    mockUseEvent.mockReturnValue({
      event: { ...mockEvent, coOrganizerOf: false },
      loading: false,
      error: null,
    })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getAllByText('142').length).toBeGreaterThan(0),
    )
  })

  it('does NOT grant access while the cached event is for a different id (stale event guard, Copilot review)', async () => {
    // Reproduit le scénario A → B : la page est rendue avec eventId=42
    // mais useEvent renvoie encore l'event A (id=999) du précédent paramètre.
    // Même si le caller est admin / créateur / co-org de A, on ne doit PAS
    // calculer l'autorisation sur l'event A ni fetcher les stats de 42 — sinon
    // 403 noise contre B.
    const staleEvent = { ...mockEvent, id: 999, creatorId: 'user-1' }
    mockUseAuth.mockReturnValue({ user: mockUser, isAdmin: true })
    mockUseEvent.mockReturnValue({ event: staleEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    renderPage('42')

    // useEventStats doit avoir été appelé avec `null` (pas d'event "courant").
    expect(mockUseEventStats).toHaveBeenCalledWith(null)
    // L'accès reste fermé : message d'erreur affiché (event stale → non-organizer).
    await waitFor(() =>
      expect(screen.getByText('403')).toBeTruthy()
    )
  })

  it('passes user.id as second argument to useEvent for coOrganizerOf enrichment', () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isAdmin: false })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })

    renderPage()
    expect(mockUseEvent).toHaveBeenCalledWith(42, 'user-1')
  })

  it('passes null to useEvent when user is null (defensive — PrivateRoute normally prevents this)', () => {
    // Couvre la branche `user?.id ?? null` quand `user` est null.
    mockUseAuth.mockReturnValue({ user: null, isAdmin: false })
    mockUseEvent.mockReturnValue({ event: null, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    renderPage()
    expect(mockUseEvent).toHaveBeenCalledWith(42, null)
  })

  it('shows error when event fails to load', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, error: 'Impossible de charger cet événement.' })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getByText('500')).toBeTruthy()
    )
  })

  it('calls refetchEvent and refetchStats when clicking retry on the error page', async () => {
    const refetchEvent = vi.fn()
    const refetchStats = vi.fn()
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, error: 'boom', refetch: refetchEvent })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: 'boom', refetch: refetchStats })

    renderPage()
    const button = await screen.findByRole('button', { name: 'Réessayer' })
    fireEvent.click(button)
    expect(refetchEvent).toHaveBeenCalled()
    expect(refetchStats).toHaveBeenCalled()
  })

  it('calls only refetchEvent when statsError is false on retry', async () => {
    const refetchEvent = vi.fn()
    const refetchStats = vi.fn()
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, error: 'boom', refetch: refetchEvent })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null, refetch: refetchStats })

    renderPage()
    const button = await screen.findByRole('button', { name: 'Réessayer' })
    fireEvent.click(button)
    expect(refetchEvent).toHaveBeenCalled()
    expect(refetchStats).not.toHaveBeenCalled()
  })

  it('calls only refetchStats when eventError is false on retry', async () => {
    const refetchEvent = vi.fn()
    const refetchStats = vi.fn()
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null, refetch: refetchEvent })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: 'boom', refetch: refetchStats })

    renderPage()
    const button = await screen.findByRole('button', { name: 'Réessayer' })
    fireEvent.click(button)
    expect(refetchEvent).not.toHaveBeenCalled()
    expect(refetchStats).toHaveBeenCalled()
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
      expect(screen.getByText('500')).toBeTruthy()
    )
  })

  it('shows "statistiques indisponibles" when stats is null without error', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    renderPage()
    await waitFor(() =>
      expect(screen.getByText('500')).toBeTruthy()
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

  it('shows attendees with displayName from the AttendanceDTO', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })
    mockGetEventAttendees.mockResolvedValue([
      {
        id: 1, userId: 'u-1', eventId: 42, status: 'ATTENDING', createdAt: '2026-01-01',
        displayName: 'Alice Martin', avatarUrl: null,
      },
    ])

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    await waitFor(() => expect(screen.getByText('Alice Martin')).toBeTruthy())
  })

  it('shows displayName for a private participant — never the UUID', async () => {
    // SCRUM hotfix : sur la page stats, l'organisateur doit voir le vrai nom même
    // pour les profils profilePublic=false. Le backend l'expose via AttendanceDTO
    // (route déjà restreinte au créateur / co-organisateur ACCEPTED), donc le
    // front lit `attendance.displayName` directement, sans fetch /users/{id}.
    const privateParticipantUuid = 'u-private-uuid-90174af2'
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })
    mockGetEventAttendees.mockResolvedValue([
      {
        id: 5, userId: privateParticipantUuid, eventId: 42, status: 'ATTENDING',
        createdAt: '2026-01-01', displayName: 'Charlie Privé', avatarUrl: null,
      },
    ])

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    await waitFor(() => expect(screen.getByText('Charlie Privé')).toBeTruthy())
    expect(screen.queryByText(privateParticipantUuid)).toBeNull()
  })

  it('falls back to "Utilisateur supprimé" on orphan attendance (displayName null)', async () => {
    const orphanUuid = 'u-ghost'
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: mockStats, loading: false, error: null })
    mockGetEventAttendees.mockResolvedValue([
      {
        id: 9, userId: orphanUuid, eventId: 42, status: 'ATTENDING',
        createdAt: '2026-01-01', displayName: null, avatarUrl: null,
      },
    ])

    renderPage()
    await waitFor(() => expect(screen.getByText(/retour à l'événement/i)).toBeTruthy())

    fireEvent.click(screen.getByText(/voir les participants/i))
    await waitFor(() => expect(screen.getByText(/utilisateur supprimé/i)).toBeTruthy())
    // Even on orphan rows, the UUID must never leak to the UI.
    expect(screen.queryByText(orphanUuid)).toBeNull()
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

  // --- Refresh button (review #90 follow-up) ---

  it('renders the refresh button alongside the stats', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({
      stats: mockStats, loading: false, isRefetching: false, error: null, refetch: vi.fn().mockResolvedValue(undefined),
    })

    renderPage()
    await waitFor(() => expect(screen.getByRole('button', { name: /rafraîchir les statistiques/i })).toBeTruthy())
  })

  it('clicking the refresh button calls refetch from useEventStats', async () => {
    const refetch = vi.fn().mockResolvedValue(undefined)
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({
      stats: mockStats, loading: false, isRefetching: false, error: null, refetch,
    })

    renderPage()
    await waitFor(() => expect(screen.getByRole('button', { name: /rafraîchir les statistiques/i })).toBeTruthy())

    fireEvent.click(screen.getByRole('button', { name: /rafraîchir les statistiques/i }))
    expect(refetch).toHaveBeenCalledTimes(1)
  })

  it('refresh button is disabled and shows the spinner state while refetching', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({
      stats: mockStats, loading: false, isRefetching: true, error: null, refetch: vi.fn().mockResolvedValue(undefined),
    })

    renderPage()
    const button = await screen.findByRole('button', { name: /rafraîchir les statistiques/i })
    expect((button as HTMLButtonElement).disabled).toBe(true)
    expect(button.getAttribute('aria-busy')).toBe('true')
    expect(screen.getByText(/rafraîchissement/i)).toBeTruthy()
  })

  it('survives a refetch rejection without throwing', async () => {
    const refetch = vi.fn().mockRejectedValue(new Error('boom'))
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockUseEventStats.mockReturnValue({
      stats: mockStats, loading: false, isRefetching: false, error: null, refetch,
    })

    renderPage()
    const button = await screen.findByRole('button', { name: /rafraîchir les statistiques/i })

    fireEvent.click(button)
    expect(refetch).toHaveBeenCalledTimes(1)
    // The rejection handler swallows the error — no unhandled rejection escapes.
    await new Promise(r => setTimeout(r, 0))
    expect(button).toBeTruthy()
  })

  // --- Residual conditional branches ---

  it('shows the error-colour fill bar at a >=90% fill rate', async () => {
    // attending 48 / capacity 50 → 96% ⇒ pct >= 90 ⇒ bg-error fill.
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, capacity: 50 }, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: { ...mockStats, attendingCount: 48 }, loading: false, error: null })

    renderPage()
    await waitFor(() => expect(screen.getByText('96%')).toBeTruthy())
    expect(document.querySelector('.bg-error')).toBeTruthy()
  })

  it('shows the emerald fill bar at a <70% fill rate', async () => {
    // attending 10 / capacity 50 → 20% ⇒ pct < 70 ⇒ bg-emerald-400 fill.
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, capacity: 50 }, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: { ...mockStats, attendingCount: 10 }, loading: false, error: null })

    renderPage()
    await waitFor(() => expect(screen.getByText('20%')).toBeTruthy())
    expect(document.querySelector('.bg-emerald-400')).toBeTruthy()
  })

  it('shows an invalid id message when the :id route param is undefined (no match)', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: false, error: null })

    render(
      <MemoryRouter>
        <EventStatsPage />
      </MemoryRouter>,
    )

    expect(screen.getByText('404')).toBeTruthy()
  })

  it('renders the skeleton with the dark-theme colour token', () => {
    mockUseTheme.mockReturnValue({ theme: 'dark' })
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: true, error: null })
    mockUseEventStats.mockReturnValue({ stats: null, loading: true, error: null })

    renderPage()
    expect(screen.getByText(/statistiques de/i)).toBeTruthy()
    expect(document.querySelector('[data-boneyard="event-stats"]')).toBeTruthy()
  })
})
