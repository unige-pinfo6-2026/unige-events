// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import FavoritesPage from '@/pages/event/favorites/FavoritesPage'
import type { Event } from '@/types/event'

vi.mock('@/services/favoriteApi', () => ({
  getFavorites: vi.fn(),
}))

const mockToggle = vi.fn()
vi.mock('@/hooks/useFavorite', () => ({
  useFavorite: () => ({ favorited: true, loading: false, toggle: mockToggle }),
}))

import { getFavorites } from '@/services/favoriteApi'

const mockGetFavorites = vi.mocked(getFavorites)

const sampleEvent: Event = {
  id: 1,
  title: 'Conférence IA',
  location: 'Uni Dufour',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'user-1',
  attendingCount: 0,
  createdAt: '2026-03-01T10:00:00',
}

function renderPage() {
  return render(
    <MemoryRouter>
      <FavoritesPage />
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('FavoritesPage', () => {
  it('shows a loading spinner initially', () => {
    mockGetFavorites.mockImplementation(() => new Promise(() => {}))
    renderPage()
    expect(document.querySelector('[class*="animate"]') ?? screen.getByRole('status', { hidden: true })).toBeTruthy()
  })

  it('shows empty state when there are no favorites', async () => {
    mockGetFavorites.mockResolvedValue([])
    renderPage()
    await waitFor(() => expect(screen.getByText(/aucun favori/i)).toBeTruthy())
  })

  it('renders event cards for each favorite', async () => {
    mockGetFavorites.mockResolvedValue([sampleEvent])
    renderPage()
    await waitFor(() => expect(screen.getByText('Conférence IA')).toBeTruthy())
  })

  it('shows an error message when loading fails', async () => {
    mockGetFavorites.mockRejectedValue(new Error('Network error'))
    renderPage()
    await waitFor(() => expect(screen.getByText(/Impossible de charger vos favoris/)).toBeTruthy())
  })

  it('removes an event from the list when the favorite star is clicked', async () => {
    mockToggle.mockResolvedValue(true)
    mockGetFavorites.mockResolvedValue([sampleEvent])
    renderPage()
    await waitFor(() => expect(screen.getByText('Conférence IA')).toBeTruthy())

    fireEvent.click(screen.getByRole('button', { name: /retirer des favoris/i }))

    await waitFor(() => expect(screen.queryByText('Conférence IA')).toBeNull())
  })

  it('shows the page title', async () => {
    mockGetFavorites.mockResolvedValue([])
    renderPage()
    await waitFor(() => expect(screen.getByRole('heading', { name: /Mes Favoris/i })).toBeTruthy())
  })
})
