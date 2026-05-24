
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import MyParticipationsPage from '@/pages/my-events/MyParticipationsPage'
import { ThemeProvider } from '@/contexts/ThemeContext'
import { FavoritesProvider } from '@/contexts/FavoritesContext'

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({ isAuthenticated: false })),
}))

vi.mock('@/services/favoriteApi', () => ({
  getFavorites: vi.fn(() => Promise.resolve([])),
}))

vi.mock('@/hooks/useMyParticipations', () => ({
  useMyParticipations: vi.fn(),
}))

import { useMyParticipations } from '@/hooks/useMyParticipations'

const mockUseMyParticipations = useMyParticipations as ReturnType<typeof vi.fn>

const makeMockEvent = (id: number, title?: string) => ({
  id,
  title: title ?? `Event ${id}`,
  description: 'Description',
  location: 'Location',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE' as const,
  faculty: null,
  status: 'PUBLISHED' as const,
  creatorId: 'user-1',
  createdAt: '2026-03-01T10:00:00',
  capacity: 100,
  attendingCount: 50,
  bannerUrl: '',
  allDay: false,
})

function renderWithProviders(component: ReactNode) {
  return render(
    <MemoryRouter initialEntries={['/my-events/participations']}>
      <ThemeProvider>
        <FavoritesProvider>
          {component}
        </FavoritesProvider>
      </ThemeProvider>
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const baseHook = {
  attending: [] as ReturnType<typeof makeMockEvent>[],
  waitlisted: [] as ReturnType<typeof makeMockEvent>[],
  pastAttending: [] as ReturnType<typeof makeMockEvent>[],
  loading: false,
  error: null as string | null,
  refresh: vi.fn(),
}

describe('MyParticipationsPage', () => {
  it('renders gradient title "Mes Participations"', () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Participations')).toBeTruthy()
    expect(document.querySelector('mark')?.textContent).toBe('Participations')
  })

  it('renders the three tabs in order with their counts', () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      attending: [makeMockEvent(1), makeMockEvent(2)],
      waitlisted: [makeMockEvent(3)],
      pastAttending: [makeMockEvent(4), makeMockEvent(5), makeMockEvent(6), makeMockEvent(7)],
    })
    renderWithProviders(<MyParticipationsPage />)

    const tabs = screen.getAllByRole('button')
    // Filter to the participation tabs (ignore other buttons that may exist)
    const labels = tabs.map(t => t.textContent ?? '')
    const idxAttending  = labels.findIndex(l => l.startsWith("J'y participe"))
    const idxWaitlisted = labels.findIndex(l => l.startsWith("Liste d'attente"))
    const idxPast       = labels.findIndex(l => l.startsWith('Anciennes participations'))

    expect(idxAttending).toBeGreaterThanOrEqual(0)
    expect(idxWaitlisted).toBeGreaterThan(idxAttending)
    expect(idxPast).toBeGreaterThan(idxWaitlisted)

    expect(screen.getByRole('button', { name: "J'y participe (2)" })).toBeTruthy()
    expect(screen.getByRole('button', { name: "Liste d'attente (1)" })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Anciennes participations (4)' })).toBeTruthy()
  })

  it("defaults to the J'y participe tab", () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      attending: [makeMockEvent(1, 'Confirmed')],
      waitlisted: [makeMockEvent(2, 'Waiting')],
      pastAttending: [makeMockEvent(3, 'OldOne')],
    })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Confirmed')).toBeTruthy()
    expect(screen.queryByText('Waiting')).toBeNull()
    expect(screen.queryByText('OldOne')).toBeNull()
  })

  it("switches to the Liste d'attente tab and renders only that list", () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      attending: [makeMockEvent(1, 'Confirmed')],
      waitlisted: [makeMockEvent(2, 'Waiting')],
      pastAttending: [makeMockEvent(3, 'OldOne')],
    })
    renderWithProviders(<MyParticipationsPage />)

    fireEvent.click(screen.getByRole('button', { name: /Liste d'attente/ }))

    expect(screen.getByText('Waiting')).toBeTruthy()
    expect(screen.queryByText('Confirmed')).toBeNull()
    expect(screen.queryByText('OldOne')).toBeNull()
  })

  it('switches to the Anciennes participations tab and renders only that list', () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      attending: [makeMockEvent(1, 'Confirmed')],
      waitlisted: [makeMockEvent(2, 'Waiting')],
      pastAttending: [makeMockEvent(3, 'OldOne')],
    })
    renderWithProviders(<MyParticipationsPage />)

    fireEvent.click(screen.getByRole('button', { name: /Anciennes participations/ }))

    expect(screen.getByText('OldOne')).toBeTruthy()
    expect(screen.queryByText('Confirmed')).toBeNull()
    expect(screen.queryByText('Waiting')).toBeNull()
  })

  it('shows the loading skeleton', () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook, loading: true })
    renderWithProviders(<MyParticipationsPage />)
    expect(document.querySelector('[data-boneyard="event-cards"]')).toBeTruthy()
  })

  it('renders the loading skeleton under the light theme (light skeletonColor branch)', () => {
    // L83[cond-expr #1] — the light branch of the skeletonColor ternary.
    localStorage.setItem('theme', 'light')
    mockUseMyParticipations.mockReturnValue({ ...baseHook, loading: true })
    renderWithProviders(<MyParticipationsPage />)
    expect(document.querySelector('[data-boneyard="event-cards"]')).toBeTruthy()
    localStorage.removeItem('theme')
  })

  it('shows error message when the hook returns an error', () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      error: 'Impossible de charger vos participations.',
    })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Impossible de charger vos participations.')).toBeTruthy()
  })

  it("shows the empty copy on the J'y participe tab", () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Vous ne participez à aucun événement pour le moment.')).toBeTruthy()
  })

  it("shows the empty copy on the Liste d'attente tab", () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook })
    renderWithProviders(<MyParticipationsPage />)
    fireEvent.click(screen.getByRole('button', { name: /Liste d'attente/ }))
    expect(screen.getByText("Vous n'êtes sur aucune liste d'attente.")).toBeTruthy()
  })

  it('shows the empty copy on the Anciennes participations tab', () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook })
    renderWithProviders(<MyParticipationsPage />)
    fireEvent.click(screen.getByRole('button', { name: /Anciennes participations/ }))
    expect(screen.getByText("Vous n'avez pas encore participé à un événement passé.")).toBeTruthy()
  })

  it('renders EventCards on the active tab', async () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      attending: [makeMockEvent(1), makeMockEvent(2)],
    })
    renderWithProviders(<MyParticipationsPage />)
    await waitFor(() => {
      expect(screen.getByText('Event 1')).toBeTruthy()
      expect(screen.getByText('Event 2')).toBeTruthy()
    })
  })

  it('does not render organizer-only actions on any tab', () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      attending: [makeMockEvent(1)],
      waitlisted: [makeMockEvent(2)],
      pastAttending: [makeMockEvent(3)],
    })
    renderWithProviders(<MyParticipationsPage />)

    // Default (attending)
    expect(screen.queryByRole('button', { name: /^Modifier$/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Annuler$/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Publier$/ })).toBeNull()

    // Waitlist
    fireEvent.click(screen.getByRole('button', { name: /Liste d'attente/ }))
    expect(screen.queryByRole('button', { name: /^Modifier$/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Annuler$/ })).toBeNull()

    // Past attending — task explicitly states past events are read-only
    fireEvent.click(screen.getByRole('button', { name: /Anciennes participations/ }))
    expect(screen.queryByRole('button', { name: /^Modifier$/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Annuler$/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Publier$/ })).toBeNull()
  })

  it('does not render the obsolete placeholder copy', () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.queryByText('Vos participations ne sont pas encore disponibles')).toBeNull()
    expect(screen.queryByText('Cette fonctionnalité sera bientôt disponible.')).toBeNull()
  })
})
