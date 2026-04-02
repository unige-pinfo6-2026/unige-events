// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import HomePage from '../../pages/dashboard/DashboardPage'

vi.mock('../../hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('../../hooks/useEvents', () => ({ useEvents: vi.fn() }))

import { useAuth } from '../../hooks/useAuth'
import { useEvents } from '../../hooks/useEvents'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockUseEvents = useEvents as ReturnType<typeof vi.fn>

const mockUser = {
  id: 'user-1',
  auth0Id: 'auth0|1',
  email: 'test@example.com',
  displayName: 'Jean Dupont',
  avatarUrl: 'https://example.com/avatar.png',
  profilePublic: true,
  createdAt: '2024-01-01',
}

const makeEvents = (count: number) =>
  Array.from({ length: count }, (_, index) => ({
    id: index + 1,
    title: 'Event ' + (index + 1),
    location: 'Location',
    startDate: '2026-04-10T14:00:00',
    endDate: '2026-04-10T17:00:00',
    category: 'CONFERENCE' as const,
    status: 'PUBLISHED' as const,
    creatorId: 'user-1',
    createdAt: '2026-03-01T10:00:00',
  }))

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <HomePage />
    </MemoryRouter>,
  )
}

describe('HomePage', () => {
  it('shows the welcome message and quick links', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: null, hasMore: false, loadMore: vi.fn() })

    renderPage()

    expect(screen.getByText(/Bienvenue, Jean/)).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Créer un événement' }).getAttribute('href')).toBe('/events/new')
    expect(screen.getByRole('link', { name: 'Mon profil' }).getAttribute('href')).toBe('/profile/me')
  })

  it('shows a loading spinner when loading with no events', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvents.mockReturnValue({ events: [], loading: true, error: null, hasMore: false, loadMore: vi.fn() })

    renderPage()

    expect(document.querySelector('.spinner')).toBeTruthy()
  })

  it('shows the localized error message when loading fails', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: 'Impossible de charger les événements.', hasMore: false, loadMore: vi.fn() })

    renderPage()

    expect(screen.getByText('Impossible de charger les événements.')).toBeTruthy()
  })

  it('shows the empty state when there are no published events', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: null, hasMore: false, loadMore: vi.fn() })

    renderPage()

    expect(screen.getByText('Aucun événement publié pour le moment.')).toBeTruthy()
  })

  it('renders the event cards when events are available', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvents.mockReturnValue({ events: makeEvents(3), loading: false, error: null, hasMore: false, loadMore: vi.fn() })

    renderPage()

    expect(screen.getByText('Event 1')).toBeTruthy()
    expect(screen.getByText('Event 2')).toBeTruthy()
    expect(screen.getByText('Event 3')).toBeTruthy()
  })

  it('shows and triggers the load-more action when more events are available', () => {
    const loadMore = vi.fn()
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvents.mockReturnValue({ events: makeEvents(12), loading: false, error: null, hasMore: true, loadMore })

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Charger plus' }))
    expect(loadMore).toHaveBeenCalledOnce()
  })

  it('shows the loading-more button state and disables it while fetching the next page', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvents.mockReturnValue({ events: makeEvents(12), loading: true, error: null, hasMore: true, loadMore: vi.fn() })

    renderPage()

    const button = screen.getByRole('button', { name: 'Chargement…' }) as HTMLButtonElement
    expect(button.disabled).toBe(true)
  })

  it('hides the load-more button when there are no more pages', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvents.mockReturnValue({ events: makeEvents(3), loading: false, error: null, hasMore: false, loadMore: vi.fn() })

    renderPage()

    expect(screen.queryByRole('button', { name: 'Charger plus' })).toBeNull()
  })
})
