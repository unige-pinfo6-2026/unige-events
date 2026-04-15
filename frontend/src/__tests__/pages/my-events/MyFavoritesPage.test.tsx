// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import MyFavoritesPage from '@/pages/my-events/MyFavoritesPage'
import { ThemeProvider } from '@/contexts/ThemeContext'
import { FavoritesProvider } from '@/contexts/FavoritesContext'

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({ isAuthenticated: false })),
}))

vi.mock('@/services/favoriteApi', () => ({
  getFavorites: vi.fn(),
}))

import { getFavorites } from '@/services/favoriteApi'

const mockGetFavorites = getFavorites as ReturnType<typeof vi.fn>

const makeMockEvent = (id: number) => ({
  id,
  title: `Favorite ${id}`,
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
    <MemoryRouter initialEntries={['/my-events/favorites']}>
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

describe('MyFavoritesPage', () => {
  it('renders page title "Mes Favoris"', () => {
    mockGetFavorites.mockResolvedValue([])

    renderWithProviders(<MyFavoritesPage />)
    expect(screen.getByText('Favoris')).toBeTruthy()
  })

  it('renders EventCard grid when favorites exist', async () => {
    mockGetFavorites.mockResolvedValue([makeMockEvent(1), makeMockEvent(2)])

    renderWithProviders(<MyFavoritesPage />)
    await waitFor(() => {
      expect(screen.getByText('Favorite 1')).toBeTruthy()
      expect(screen.getByText('Favorite 2')).toBeTruthy()
    })
  })

  it('renders empty state when no favorites', async () => {
    mockGetFavorites.mockResolvedValue([])

    renderWithProviders(<MyFavoritesPage />)
    await waitFor(() => {
      expect(screen.getByText('Aucun favori pour le moment')).toBeTruthy()
    })
  })

  it('shows loading skeleton while loading', () => {
    mockGetFavorites.mockImplementation(() => new Promise(() => {})) // Never resolves

    renderWithProviders(<MyFavoritesPage />)
    expect(document.querySelector('[data-boneyard="my-events"]')).toBeTruthy()
  })

  it('shows error message when fetch fails', async () => {
    mockGetFavorites.mockRejectedValue(new Error('Network error'))

    renderWithProviders(<MyFavoritesPage />)
    await waitFor(() => {
      expect(screen.getByText('Impossible de charger vos favoris.')).toBeTruthy()
    })
  })
})
