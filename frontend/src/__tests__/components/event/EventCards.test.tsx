// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import EventCards from '@/components/event/EventCards'
import type { Event } from '@/types/event'

vi.mock('@/hooks/useEvents', () => ({
  useEvents: vi.fn(),
}))

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: vi.fn(() => ({ theme: 'dark', toggleTheme: vi.fn() })),
}))

vi.mock('@/hooks/useFavorite', () => ({
  useFavorite: () => ({ favorited: false, loading: false, toggle: vi.fn() }),
}))

import { useEvents } from '@/hooks/useEvents'
import { useTheme } from '@/contexts/ThemeContext'

const mockUseEvents = useEvents as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const mockEvent: Event = {
  id: 1,
  title: 'Conférence IA',
  location: 'Uni Dufour',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'user-1',
  capacity: 100,
  attendingCount: 0,
  createdAt: '2026-03-01T10:00:00',
}

function renderCards() {
  return render(
    <MemoryRouter>
      <EventCards />
    </MemoryRouter>,
  )
}

describe('EventCards', () => {
  it('shows skeleton when loading with no events', () => {
    mockUseEvents.mockReturnValue({ events: [], loading: true, error: null, hasMore: false, loadMore: vi.fn() })
    renderCards()
    expect(document.querySelector('[data-boneyard="event-cards"]')).toBeTruthy()
  })

  it('shows error message', () => {
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: 'Impossible de charger les événements.', hasMore: false, loadMore: vi.fn() })
    renderCards()
    expect(screen.getByText('Impossible de charger les événements.')).toBeTruthy()
  })

  it('shows empty state message', () => {
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: null, hasMore: false, loadMore: vi.fn() })
    renderCards()
    expect(screen.getByText('Aucun événement publié pour le moment.')).toBeTruthy()
  })

  it('renders event cards when events are present', () => {
    mockUseEvents.mockReturnValue({ events: [mockEvent], loading: false, error: null, hasMore: false, loadMore: vi.fn() })
    renderCards()
    expect(screen.getByText('Conférence IA')).toBeTruthy()
  })

  it('shows load more button when hasMore is true', () => {
    mockUseEvents.mockReturnValue({ events: [mockEvent], loading: false, error: null, hasMore: true, loadMore: vi.fn() })
    renderCards()
    expect(screen.getByRole('button', { name: 'Charger plus' })).toBeTruthy()
  })

  it('calls loadMore when clicking the button', () => {
    const loadMore = vi.fn()
    mockUseEvents.mockReturnValue({ events: [mockEvent], loading: false, error: null, hasMore: true, loadMore })
    renderCards()
    fireEvent.click(screen.getByRole('button', { name: 'Charger plus' }))
    expect(loadMore).toHaveBeenCalledOnce()
  })

  it('shows loading state on load more button while loading additional events', () => {
    mockUseEvents.mockReturnValue({ events: [mockEvent], loading: true, error: null, hasMore: true, loadMore: vi.fn() })
    renderCards()
    const btn = screen.getByRole('button', { name: 'Chargement…' })
    expect(btn).toBeTruthy()
    expect((btn as HTMLButtonElement).disabled).toBe(true)
  })

  it('does not show load more button when there is an error', () => {
    mockUseEvents.mockReturnValue({ events: [mockEvent], loading: false, error: 'Erreur', hasMore: true, loadMore: vi.fn() })
    renderCards()
    expect(screen.queryByRole('button', { name: 'Charger plus' })).toBeNull()
  })

  it('shows skeleton with light theme skeleton color', () => {
    mockUseTheme.mockReturnValue({ theme: 'light', toggleTheme: vi.fn() })
    mockUseEvents.mockReturnValue({ events: [], loading: true, error: null, hasMore: false, loadMore: vi.fn() })
    renderCards()
    expect(document.querySelector('[data-boneyard="event-cards"]')).toBeTruthy()
  })
})
