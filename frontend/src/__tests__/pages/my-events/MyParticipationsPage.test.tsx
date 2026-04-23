
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
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

const makeMockEvent = (id: number) => ({
  id,
  title: `Event ${id}`,
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

describe('MyParticipationsPage', () => {
  it('renders page title "Mes Participations"', () => {
    mockUseMyParticipations.mockReturnValue({
      events: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
    })

    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Participations')).toBeTruthy()
  })

  it('renders "coming soon" message when no events (stub)', () => {
    mockUseMyParticipations.mockReturnValue({
      events: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
    })

    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Vos participations ne sont pas encore disponibles')).toBeTruthy()
    expect(screen.getByText('Cette fonctionnalité sera bientôt disponible.')).toBeTruthy()
  })

  it('shows loading skeleton while loading', () => {
    mockUseMyParticipations.mockReturnValue({
      events: [],
      loading: true,
      error: null,
      refresh: vi.fn(),
    })

    renderWithProviders(<MyParticipationsPage />)
    expect(document.querySelector('[data-boneyard="event-cards"]')).toBeTruthy()
  })

  it('shows error message when fetch fails', () => {
    mockUseMyParticipations.mockReturnValue({
      events: [],
      loading: false,
      error: 'Failed to load participations',
      refresh: vi.fn(),
    })

    renderWithProviders(<MyParticipationsPage />)
    expect(screen.getByText('Failed to load participations')).toBeTruthy()
  })

  it('renders EventCards when events exist', async () => {
    mockUseMyParticipations.mockReturnValue({
      events: [makeMockEvent(1), makeMockEvent(2)],
      loading: false,
      error: null,
      refresh: vi.fn(),
    })

    renderWithProviders(<MyParticipationsPage />)
    await waitFor(() => {
      expect(screen.getByText('Event 1')).toBeTruthy()
      expect(screen.getByText('Event 2')).toBeTruthy()
    })
  })

  it('shows "Inscrit" badge on event cards', async () => {
    mockUseMyParticipations.mockReturnValue({
      events: [makeMockEvent(1)],
      loading: false,
      error: null,
      refresh: vi.fn(),
    })

    renderWithProviders(<MyParticipationsPage />)
    await waitFor(() => {
      expect(screen.getByText('Inscrit')).toBeTruthy()
    })
  })
})
