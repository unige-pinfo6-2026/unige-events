
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
  loading: false,
  error: null as string | null,
  refresh: vi.fn(),
}

describe('MyParticipationsPage', () => {
  it('renders gradient title "Mes Participations"', () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Participations')).toBeTruthy()
    // <mark> wrapping is what styles the gradient — sanity check the element exists
    expect(document.querySelector('mark')?.textContent).toBe('Participations')
  })

  it('renders both tabs with labels and counts', () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      attending: [makeMockEvent(1), makeMockEvent(2)],
      waitlisted: [makeMockEvent(3)],
    })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByRole('button', { name: "J'y participe (2)" })).toBeTruthy()
    expect(screen.getByRole('button', { name: "Liste d'attente (1)" })).toBeTruthy()
  })

  it("defaults to the J'y participe tab", () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      attending: [makeMockEvent(1, 'Confirmed')],
      waitlisted: [makeMockEvent(2, 'Waiting')],
    })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Confirmed')).toBeTruthy()
    expect(screen.queryByText('Waiting')).toBeNull()
  })

  it('switches to the waitlist tab on click and renders only that list', () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      attending: [makeMockEvent(1, 'Confirmed')],
      waitlisted: [makeMockEvent(2, 'Waiting')],
    })
    renderWithProviders(<MyParticipationsPage />)

    fireEvent.click(screen.getByRole('button', { name: /Liste d'attente/ }))

    expect(screen.getByText('Waiting')).toBeTruthy()
    expect(screen.queryByText('Confirmed')).toBeNull()
  })

  it('shows the loading skeleton', () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook, loading: true })
    renderWithProviders(<MyParticipationsPage />)
    expect(document.querySelector('[data-boneyard="event-cards"]')).toBeTruthy()
  })

  it('shows error message when the hook returns an error', () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      error: 'Impossible de charger vos participations.',
    })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Impossible de charger vos participations.')).toBeTruthy()
  })

  it('shows the attending-empty copy on the J\'y participe tab', () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Vous ne participez à aucun événement pour le moment.')).toBeTruthy()
  })

  it("shows the waitlist-empty copy on the Liste d'attente tab", () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook })
    renderWithProviders(<MyParticipationsPage />)
    fireEvent.click(screen.getByRole('button', { name: /Liste d'attente/ }))
    expect(screen.getByText("Vous n'êtes sur aucune liste d'attente.")).toBeTruthy()
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

  it('does not render organizer-only actions (Modifier / Annuler)', () => {
    mockUseMyParticipations.mockReturnValue({
      ...baseHook,
      attending: [makeMockEvent(1)],
    })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.queryByRole('button', { name: /Modifier/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^Annuler$/ })).toBeNull()
  })

  it('does not render the obsolete placeholder copy', () => {
    mockUseMyParticipations.mockReturnValue({ ...baseHook })
    renderWithProviders(<MyParticipationsPage />)
    expect(screen.queryByText('Vos participations ne sont pas encore disponibles')).toBeNull()
    expect(screen.queryByText('Cette fonctionnalité sera bientôt disponible.')).toBeNull()
  })
})
