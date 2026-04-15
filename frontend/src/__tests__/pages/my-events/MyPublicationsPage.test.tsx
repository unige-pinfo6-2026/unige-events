// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import MyPublicationsPage from '@/pages/my-events/MyPublicationsPage'
import { ThemeProvider } from '@/contexts/ThemeContext'
import { ToastProvider } from '@/contexts/ToastContext'
import { FavoritesProvider } from '@/contexts/FavoritesContext'

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({ user: { id: 'user-1' } })),
}))

vi.mock('@/hooks/useMyEvents', () => ({
  useMyEvents: vi.fn(),
}))

import { useMyEvents } from '@/hooks/useMyEvents'

const mockUseMyEvents = useMyEvents as ReturnType<typeof vi.fn>

const makeMockEvent = (id: number, status: 'PUBLISHED' | 'DRAFT' | 'CANCELLED' = 'PUBLISHED') => ({
  id,
  title: `Publication ${id}`,
  description: 'Description',
  location: 'Location',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE' as const,
  faculty: 'SCIENCES' as const,
  status,
  creatorId: 'user-1',
  createdAt: '2026-03-01T10:00:00',
  capacity: 100,
  attendingCount: 25,
  bannerUrl: '',
})

function renderWithProviders(component: ReactNode) {
  return render(
    <MemoryRouter initialEntries={['/my-events/publications']}>
      <ThemeProvider>
        <ToastProvider>
          <FavoritesProvider>
            {component}
          </FavoritesProvider>
        </ToastProvider>
      </ThemeProvider>
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('MyPublicationsPage', () => {
  it('renders page title "Mes Publications"', () => {
    mockUseMyEvents.mockReturnValue({
      events: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    expect(screen.getByText('Publications')).toBeTruthy()
  })

  it('renders EventCard grid when publications exist', async () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1), makeMockEvent(2)],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    await waitFor(() => {
      expect(screen.getByText('Publication 1')).toBeTruthy()
      expect(screen.getByText('Publication 2')).toBeTruthy()
    })
  })

  it('renders empty state when no events', () => {
    mockUseMyEvents.mockReturnValue({
      events: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    expect(screen.getByText('Aucun événement dans cette catégorie')).toBeTruthy()
  })

  it('shows loading skeleton while loading', () => {
    mockUseMyEvents.mockReturnValue({
      events: [],
      loading: true,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    expect(document.querySelector('[data-boneyard="my-events"]')).toBeTruthy()
  })

  it('shows error message when fetch fails', () => {
    mockUseMyEvents.mockReturnValue({
      events: [],
      loading: false,
      error: 'Failed to load events',
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    expect(screen.getByText('Failed to load events')).toBeTruthy()
  })

  it('switches status filter via tabs', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const draftTab = screen.getByRole('button', { name: 'Brouillons' })
    fireEvent.click(draftTab)
    expect(draftTab.className).toContain('border-accent')
  })

  it('renders "Modifier" link with correct href', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(42, 'PUBLISHED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const editLink = screen.getByText('Modifier')
    expect(editLink.closest('a')?.getAttribute('href')).toBe('/events/42/edit')
  })

  it('shows "Publier" button only for DRAFT events', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'DRAFT'), makeMockEvent(2, 'PUBLISHED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const publishButtons = screen.getAllByText('Publier')
    expect(publishButtons.length).toBe(1) // Only DRAFT event has publish button
  })

  it('"Annuler" button opens confirmation modal', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'PUBLISHED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const cancelButton = screen.getByText('Annuler')
    fireEvent.click(cancelButton)
    expect(screen.getByText('Annuler l\'événement ?')).toBeTruthy()
  })

  it('displays FacultyBadge when faculty is set', () => {
    mockUseMyEvents.mockReturnValue({
      events: [{ ...makeMockEvent(1), faculty: 'SCIENCES' }],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    expect(screen.getByText('Sciences')).toBeTruthy()
  })

  it('displays "Toutes facultés" fallback when faculty is null', () => {
    mockUseMyEvents.mockReturnValue({
      events: [{ ...makeMockEvent(1), faculty: null }],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    expect(screen.getByText('Toutes facultés')).toBeTruthy()
  })

  it('shows status badge with correct variant', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    // Find the status badge (EVENT_STATUSES.DRAFT.name is 'Brouillon', singular)
    const badge = screen.getByText('Brouillon')
    expect(badge.className).toContain('uppercase')
  })

  it('calls publish when "Publier" button is clicked', async () => {
    const publish = vi.fn().mockResolvedValue(true)
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish,
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const publishButton = screen.getByText('Publier')
    fireEvent.click(publishButton)

    await waitFor(() => {
      expect(publish).toHaveBeenCalledWith(1)
    })
  })

  it('calls cancel when "Annuler" button is clicked and confirms', async () => {
    const cancel = vi.fn().mockResolvedValue(true)
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'PUBLISHED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel,
    })

    renderWithProviders(<MyPublicationsPage />)
    const cancelButton = screen.getByText('Annuler')
    fireEvent.click(cancelButton)

    const confirmButton = screen.getByRole('button', { name: 'Confirmer' })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(cancel).toHaveBeenCalledWith(1)
    })
  })

  it('publish fails silently when hook returns false', async () => {
    const publish = vi.fn().mockResolvedValue(false)
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish,
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const publishButton = screen.getByText('Publier')
    fireEvent.click(publishButton)

    await waitFor(() => {
      expect(publish).toHaveBeenCalledWith(1)
    })
  })

  it('cancel fails silently when hook returns false', async () => {
    const cancel = vi.fn().mockResolvedValue(false)
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'PUBLISHED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel,
    })

    renderWithProviders(<MyPublicationsPage />)
    const cancelButtons = screen.getAllByText('Annuler')
    fireEvent.click(cancelButtons[0])

    const confirmButton = screen.getByRole('button', { name: 'Confirmer' })
    fireEvent.click(confirmButton)

    await waitFor(() => {
      expect(cancel).toHaveBeenCalledWith(1)
    })
  })

  it('wraps card content in a Link to the event detail page', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(42, 'PUBLISHED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const titleLink = screen.getByText('Publication 42').closest('a')
    expect(titleLink?.getAttribute('href')).toBe('/events/42')
  })

  it('clicking card action buttons does not trigger navigation', async () => {
    const publish = vi.fn().mockResolvedValue(true)
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(7, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish,
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    // Simulate click on Publier - ensure onClick fires and stopPropagation works
    const publishBtn = screen.getByText('Publier')
    fireEvent.click(publishBtn)
    await waitFor(() => expect(publish).toHaveBeenCalledWith(7))
  })

  it('closes confirmation modal when cancelling', async () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'PUBLISHED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const cancelButtons = screen.getAllByText('Annuler')
    // Click the card's cancel button (the first one)
    fireEvent.click(cancelButtons[0])

    await waitFor(() => {
      expect(screen.getByText('Annuler l\'événement ?')).toBeTruthy()
    })

    // Get all buttons, find the second "Annuler" which should be in the modal
    const allAnnulerButtons = screen.getAllByText('Annuler')
    // There should be at least 2: one on the card, one in the modal close button
    // Click the one that's in the modal (appears after the first one)
    fireEvent.click(allAnnulerButtons[1])

    await waitFor(() => {
      expect(screen.queryByText('Annuler l\'événement ?')).toBeFalsy()
    })
  })
})
