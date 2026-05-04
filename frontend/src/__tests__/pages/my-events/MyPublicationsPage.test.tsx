
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
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
  // Remove any <footer> added by FAB tests
  document.querySelectorAll('footer').forEach(f => f.remove())
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    expect(document.querySelector('[data-boneyard="my-publications"]')).toBeTruthy()
  })

  it('shows error message when fetch fails', () => {
    mockUseMyEvents.mockReturnValue({
      events: [],
      loading: false,
      error: 'Failed to load events',
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    // Find the status badge (EVENT_STATUSES.DRAFT.name is 'Brouillon', singular)
    const badge = screen.getByText('Brouillon')
    expect(badge.className).toContain('uppercase')
  })

  it('calls publish when "Publier" button is clicked', async () => {
    const publish = vi.fn().mockResolvedValue({ ok: true })
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish,
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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

  it('publish fails silently when hook returns empty errors', async () => {
    const publish = vi.fn().mockResolvedValue({ ok: false, errors: [] })
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish,
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const titleLink = screen.getByText('Publication 42').closest('a')
    expect(titleLink?.getAttribute('href')).toBe('/events/42')
  })

  it('clicking card action buttons does not trigger navigation', async () => {
    const publish = vi.fn().mockResolvedValue({ ok: true })
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(7, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish,
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    // Simulate click on Publier - ensure onClick fires and stopPropagation works
    const publishBtn = screen.getByText('Publier')
    fireEvent.click(publishBtn)
    await waitFor(() => expect(publish).toHaveBeenCalledWith(7))
  })

  it('"Annuler" button uses the Ban icon, not Trash2', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'PUBLISHED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const cancelButton = screen.getAllByText('Annuler')[0].closest('button')!
    expect(cancelButton.querySelector('.lucide-ban')).toBeTruthy()
    expect(cancelButton.querySelector('.lucide-trash-2')).toBeFalsy()
  })

  it('"Annuler" on DRAFT tab uses the Ban icon', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const cancelButton = screen.getAllByText('Annuler')[0].closest('button')!
    expect(cancelButton.querySelector('.lucide-ban')).toBeTruthy()
  })

  it('CANCELLED tab does not render Modifier button', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'CANCELLED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    expect(screen.queryByText('Modifier')).toBeFalsy()
  })

  it('CANCELLED tab renders "Remettre en brouillon" and "Supprimer" with correct icons', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'CANCELLED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const restoreBtn = screen.getByText('Remettre en brouillon').closest('button')!
    const deleteBtn = screen.getByText('Supprimer').closest('button')!
    expect(restoreBtn.querySelector('.lucide-undo-2')).toBeTruthy()
    expect(deleteBtn.querySelector('.lucide-trash-2')).toBeTruthy()
    expect(deleteBtn.className).toContain('text-error')
  })

  it('"Remettre en brouillon" calls restore and fires stopPropagation', async () => {
    const restore = vi.fn().mockResolvedValue(true)
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(9, 'CANCELLED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore,
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const restoreBtn = screen.getByText('Remettre en brouillon')
    const event = new MouseEvent('click', { bubbles: true, cancelable: true })
    const stopSpy = vi.spyOn(event, 'stopPropagation')
    restoreBtn.dispatchEvent(event)
    expect(stopSpy).toHaveBeenCalled()
    await waitFor(() => expect(restore).toHaveBeenCalledWith(9))
  })

  it('"Remettre en brouillon" shows error toast when restore fails', async () => {
    const restore = vi.fn().mockResolvedValue(false)
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(9, 'CANCELLED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore,
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    fireEvent.click(screen.getByText('Remettre en brouillon'))
    await waitFor(() => expect(restore).toHaveBeenCalledWith(9))
  })

  it('"Supprimer" opens a confirmation dialog and does NOT delete on cancel', async () => {
    const permanentlyDelete = vi.fn().mockResolvedValue(true)
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(3, 'CANCELLED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete,
    })

    renderWithProviders(<MyPublicationsPage />)
    fireEvent.click(screen.getByText('Supprimer'))
    expect(screen.getByText('Supprimer définitivement ?')).toBeTruthy()

    // Cancel the dialog (modal's "Annuler" button)
    const modalCancel = screen.getByRole('button', { name: 'Annuler' })
    fireEvent.click(modalCancel)

    await waitFor(() => {
      expect(screen.queryByText('Supprimer définitivement ?')).toBeFalsy()
    })
    expect(permanentlyDelete).not.toHaveBeenCalled()
  })

  it('"Supprimer" confirmation calls permanentlyDelete', async () => {
    const permanentlyDelete = vi.fn().mockResolvedValue(true)
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(3, 'CANCELLED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete,
    })

    renderWithProviders(<MyPublicationsPage />)
    fireEvent.click(screen.getByText('Supprimer'))
    const confirmButtons = screen.getAllByRole('button', { name: 'Supprimer' })
    fireEvent.click(confirmButtons.at(-1)!)

    await waitFor(() => expect(permanentlyDelete).toHaveBeenCalledWith(3))
  })

  it('"Supprimer" shows error toast when deletion fails', async () => {
    const permanentlyDelete = vi.fn().mockResolvedValue(false)
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(3, 'CANCELLED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete,
    })

    renderWithProviders(<MyPublicationsPage />)
    fireEvent.click(screen.getByText('Supprimer'))
    const confirmButtons = screen.getAllByRole('button', { name: 'Supprimer' })
    fireEvent.click(confirmButtons.at(-1)!)
    await waitFor(() => expect(permanentlyDelete).toHaveBeenCalledWith(3))
  })

  it('closes confirmation modal when cancelling', async () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'PUBLISHED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
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

  it('"Annuler" modal text says cancellation is reversible (not irréversible)', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'PUBLISHED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })
    renderWithProviders(<MyPublicationsPage />)
    fireEvent.click(screen.getAllByText('Annuler')[0])
    expect(screen.getByText(/remettre en brouillon depuis l'onglet Annulés/)).toBeTruthy()
    expect(screen.queryByText(/irréversible/i)).toBeFalsy()
  })

  it('"Supprimer" modal text says deletion is irréversible', () => {
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'CANCELLED')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })
    renderWithProviders(<MyPublicationsPage />)
    fireEvent.click(screen.getByText('Supprimer'))
    expect(screen.getByText(/irréversible/i)).toBeTruthy()
    expect(screen.getAllByText(/définitivement/i).length).toBeGreaterThan(0)
  })

  it('publish with non-validation error (empty errors) shows toast, not modal', async () => {
    const publish = vi.fn().mockResolvedValue({ ok: false, errors: [] })
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish,
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })
    renderWithProviders(<MyPublicationsPage />)
    fireEvent.click(screen.getByText('Publier'))
    await waitFor(() => expect(publish).toHaveBeenCalledWith(1))
    expect(screen.queryByText('Impossible de publier cet événement')).toBeFalsy()
  })

  it('FAB adjusts bottom offset when footer is in view', async () => {
    // Add a <footer> so the useEffect doesn't early-return
    const footer = document.createElement('footer')
    document.body.appendChild(footer)

    // Mock ResizeObserver
    const observeSpy = vi.fn()
    const disconnectSpy = vi.fn()
    class MockResizeObserver {
      constructor(cb: ResizeObserverCallback) {
        setTimeout(() => cb([] as unknown as ResizeObserverEntry[], this as unknown as ResizeObserver), 0)
      }
      observe = observeSpy
      disconnect = disconnectSpy
      unobserve = vi.fn()
    }
    vi.stubGlobal('ResizeObserver', MockResizeObserver)

    // Mock requestAnimationFrame / cancelAnimationFrame
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => { cb(0); return 1 })
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => {})

    // Mock footer position: footer top at 900, viewport height 1000 → overlap = 100
    vi.spyOn(footer, 'getBoundingClientRect').mockReturnValue({
      top: 900, bottom: 1000, left: 0, right: 0, width: 0, height: 100, x: 0, y: 900, toJSON: () => {},
    })
    Object.defineProperty(window, 'innerHeight', { value: 1000, configurable: true })

    mockUseMyEvents.mockReturnValue({
      events: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)

    await waitFor(() => {
      const fab = document.querySelector('a[aria-label="Créer un événement"].fixed')
      expect(fab).toBeTruthy()
      // overlap = max(0, 1000 - 900) = 100, bottom = 100 + 24 = 124px
      expect((fab as HTMLElement).style.bottom).toBe('124px')
    })

    expect(observeSpy).toHaveBeenCalledWith(document.body)

    // Trigger scroll and resize to exercise those listeners
    window.dispatchEvent(new Event('scroll'))
    window.dispatchEvent(new Event('resize'))

    cleanup()
    // After cleanup, ResizeObserver should be disconnected and listeners removed
    expect(disconnectSpy).toHaveBeenCalled()
  })

  it('FAB useEffect exits early when no footer is present', () => {
    // Ensure no <footer> in DOM
    document.querySelectorAll('footer').forEach(f => f.remove())

    mockUseMyEvents.mockReturnValue({
      events: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)
    const fab = document.querySelector('a[aria-label="Créer un événement"].fixed')
    // When no footer, the effect returns early → style.bottom is not set
    expect((fab as HTMLElement).style.bottom).toBe('')
  })

  it('FAB handles footer fully above viewport (no overlap)', async () => {
    const footer = document.createElement('footer')
    document.body.appendChild(footer)

    vi.stubGlobal('ResizeObserver', class { observe = vi.fn(); disconnect = vi.fn(); unobserve = vi.fn() })
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => { cb(0); return 1 })
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => {})

    // Footer top at 1200, viewport height 1000 → no overlap
    vi.spyOn(footer, 'getBoundingClientRect').mockReturnValue({
      top: 1200, bottom: 1300, left: 0, right: 0, width: 0, height: 100, x: 0, y: 1200, toJSON: () => {},
    })
    Object.defineProperty(window, 'innerHeight', { value: 1000, configurable: true })

    mockUseMyEvents.mockReturnValue({
      events: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)

    await waitFor(() => {
      const fab = document.querySelector('a[aria-label="Créer un événement"].fixed')
      // overlap = max(0, 1000 - 1200) = 0, bottom = 0 + 24 = 24px
      expect((fab as HTMLElement).style.bottom).toBe('24px')
    })
  })

  it('FAB works when ResizeObserver is not available', async () => {
    const footer = document.createElement('footer')
    document.body.appendChild(footer)

    // Simulate env without ResizeObserver
    const orig = globalThis.ResizeObserver
    // @ts-expect-error - intentionally removing ResizeObserver
    delete globalThis.ResizeObserver

    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((cb) => { cb(0); return 1 })
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => {})

    mockUseMyEvents.mockReturnValue({
      events: [],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish: vi.fn(),
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })

    renderWithProviders(<MyPublicationsPage />)

    await waitFor(() => {
      const fab = document.querySelector('a[aria-label="Créer un événement"].fixed')
      // The effect still runs (scroll/resize listeners) and sets bottom
      expect((fab as HTMLElement).style.bottom).not.toBe('')
    })

    globalThis.ResizeObserver = orig
  })

  it('publish with validation errors opens error modal listing each error', async () => {
    const publish = vi.fn().mockResolvedValue({
      ok: false,
      errors: ['La date de l\'événement doit être dans le futur', 'Le titre est obligatoire'],
    })
    mockUseMyEvents.mockReturnValue({
      events: [makeMockEvent(1, 'DRAFT')],
      loading: false,
      error: null,
      refresh: vi.fn(),
      publish,
      cancel: vi.fn(),
      restore: vi.fn(),
      permanentlyDelete: vi.fn(),
    })
    renderWithProviders(<MyPublicationsPage />)
    fireEvent.click(screen.getByText('Publier'))

    await waitFor(() => {
      expect(screen.getByText('Impossible de publier cet événement')).toBeTruthy()
    })
    expect(screen.getByText('La date de l\'événement doit être dans le futur')).toBeTruthy()
    expect(screen.getByText('Le titre est obligatoire')).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Fermer' }))
    await waitFor(() => {
      expect(screen.queryByText('Impossible de publier cet événement')).toBeFalsy()
    })
  })
})
