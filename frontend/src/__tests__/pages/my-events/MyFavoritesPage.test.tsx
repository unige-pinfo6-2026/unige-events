// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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

vi.mock('@/hooks/useFavorite', () => ({
  useFavorite: vi.fn(),
}))

import { getFavorites } from '@/services/favoriteApi'
import { useFavorite } from '@/hooks/useFavorite'

const mockGetFavorites = getFavorites as ReturnType<typeof vi.fn>
const mockUseFavorite = useFavorite as ReturnType<typeof vi.fn>

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

beforeEach(() => {
  // Default mock for useFavorite in all tests
  mockUseFavorite.mockReturnValue({
    favorited: true,
    loading: false,
    toggle: vi.fn().mockResolvedValue(true),
  })
})

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
    expect(document.querySelector('[data-boneyard="event-cards"]')).toBeTruthy()
  })

  it('shows error message when fetch fails', async () => {
    mockGetFavorites.mockRejectedValue(new Error('Network error'))

    renderWithProviders(<MyFavoritesPage />)
    await waitFor(() => {
      expect(screen.getByText('Impossible de charger vos favoris.')).toBeTruthy()
    })
  })

  it('renders with grid layout when favorites exist', async () => {
    mockGetFavorites.mockResolvedValue([makeMockEvent(1), makeMockEvent(2)])

    renderWithProviders(<MyFavoritesPage />)
    await waitFor(() => {
      expect(screen.getByText('Favorite 1')).toBeTruthy()
      expect(screen.getByText('Favorite 2')).toBeTruthy()
    })

    // Verify both events are rendered in the grid
    const gridContainer = screen.getAllByText('Favorite 1')[0]?.closest('[class*="grid"]')
    expect(gridContainer).toBeTruthy()
  })

  it('removes favorite from list when favorite button is clicked', async () => {
    mockGetFavorites.mockResolvedValue([makeMockEvent(1), makeMockEvent(2)])
    mockUseFavorite.mockReturnValue({
      favorited: true,
      loading: false,
      toggle: vi.fn().mockResolvedValue(true),
    })

    renderWithProviders(<MyFavoritesPage />)
    await waitFor(() => {
      expect(screen.getByText('Favorite 1')).toBeTruthy()
      expect(screen.getByText('Favorite 2')).toBeTruthy()
    })

    // Click the favorite button (star icon) with "Retirer des favoris" label to remove the first event
    const removeButtons = screen.getAllByLabelText('Retirer des favoris')
    fireEvent.click(removeButtons[0])

    await waitFor(() => {
      // First event should be removed after favorite button click triggers onRemove
      expect(screen.queryByText('Favorite 1')).toBeFalsy()
      expect(screen.getByText('Favorite 2')).toBeTruthy()
    })
  })
})
