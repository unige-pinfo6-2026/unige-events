
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import EventDetailPage from '@/pages/event/EventDetailPage'

vi.mock('@/hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('@/hooks/useEvent', () => ({ useEvent: vi.fn() }))
vi.mock('@/services/eventApi', () => ({
  deleteEvent: vi.fn(),
  cancelEvent: vi.fn(),
  restoreEvent: vi.fn(),
}))
vi.mock('@/services/userService', () => ({ getUserById: vi.fn() }))
vi.mock('@/services/statsApi', () => ({
  recordEventView: vi.fn().mockResolvedValue(undefined),
}))
vi.mock('@/hooks/useFavorite', () => ({
  useFavorite: vi.fn(() => ({ favorited: false, loading: false, toggle: vi.fn() })),
}))
vi.mock('@/hooks/useAttendees', () => ({
  useAttendees: vi.fn(),
}))
vi.mock('@/hooks/useAttendance', () => ({
  useAttendance: vi.fn(),
}))
const mockShowToast = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: mockShowToast }),
}))

import { useAuth } from '@/hooks/useAuth'
import { useEvent } from '@/hooks/useEvent'
import { useAttendees } from '@/hooks/useAttendees'
import { useAttendance } from '@/hooks/useAttendance'
import { useFavorite } from '@/hooks/useFavorite'
import { cancelEvent, deleteEvent, restoreEvent } from '@/services/eventApi'
import { getUserById } from '@/services/userService'
import { recordEventView } from '@/services/statsApi'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockUseEvent = useEvent as ReturnType<typeof vi.fn>
const mockUseAttendees = useAttendees as ReturnType<typeof vi.fn>
const mockUseAttendance = useAttendance as ReturnType<typeof vi.fn>
const mockUseFavorite = useFavorite as ReturnType<typeof vi.fn>
const mockRecordEventView = recordEventView as ReturnType<typeof vi.fn>
const mockDeleteEvent = deleteEvent as ReturnType<typeof vi.fn>
const mockCancelEvent = cancelEvent as ReturnType<typeof vi.fn>
const mockRestoreEvent = restoreEvent as ReturnType<typeof vi.fn>
const mockGetUserById = getUserById as ReturnType<typeof vi.fn>

const mockUser = {
  id: 'user-1',
  auth0Id: 'auth0|1',
  email: 'test@example.com',
  displayName: 'Jean Dupont',
  profilePublic: true,
  createdAt: '2024-01-01',
}

const mockEvent = {
  id: 1,
  title: 'Conférence IA',
  description: 'Une super conférence.',
  location: 'Uni Dufour',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE' as const,
  faculty: null,
  status: 'PUBLISHED' as const,
  creatorId: 'user-1',
  capacity: 200,
  createdAt: '2026-03-01T10:00:00',
}

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  sessionStorage.removeItem(BANNER_UPLOAD_ERROR_KEY)
})

const defaultAttendeesState = {
  attendees: [],
  isLoading: false,
  error: null,
  hasMore: false,
  loadMore: vi.fn(),
  refetch: vi.fn(),
  isForbidden: false,
}

const defaultAttendanceState = {
  currentStatus: null,
  attendingCount: 5,
  loading: false,
  error: null,
  isFull: false,
  toggle: vi.fn(),
}

beforeEach(() => {
  mockUseAttendees.mockReturnValue(defaultAttendeesState)
  mockUseAttendance.mockReturnValue(defaultAttendanceState)
  mockRecordEventView.mockResolvedValue(undefined)
})

function renderPage(eventId = '1') {
  return render(
    <MemoryRouter initialEntries={['/events/' + eventId]}>
      <Routes>
        <Route path='/events/:id' element={<EventDetailPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('EventDetailPage', () => {
  it('shows a skeleton while loading', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: true, isInitialLoad: true, isRefetching: false, refetch: vi.fn(), error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(document.querySelector('[data-boneyard="event-detail"]')).toBeTruthy()
  })

  it('does NOT show the full-page skeleton during a refetch (event already loaded)', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({
      event: mockEvent,
      loading: true,
      isInitialLoad: false,
      isRefetching: true,
      refetch: vi.fn(),
      error: null,
    })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(document.querySelector('[data-boneyard="event-detail"]')).toBeNull()
    expect(screen.getByRole('heading', { name: 'Conférence IA' })).toBeTruthy()
  })

  it('renders the public stats panel for any user (review #90)', () => {
    // Non-organizer (user.id !== creatorId) sees the public stats block too.
    mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'someone-else' } })
    mockUseEvent.mockReturnValue({
      event: { ...mockEvent, attendingCount: 12, viewCount: 1234, interestedCount: 5 },
      loading: false,
      isInitialLoad: false,
      isRefetching: false,
      refetch: vi.fn(),
      error: null,
    })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByText('Statistiques de participation')).toBeTruthy()
    expect(screen.getByText('Vues')).toBeTruthy()
    expect(screen.getByText('Inscrits')).toBeTruthy()
    expect(screen.getByText('Intéressés')).toBeTruthy()
    // fr-CH thin space (U+202F) between thousands
    expect(screen.getByText(/1.234/)).toBeTruthy()
    expect(screen.getByText('12')).toBeTruthy()
    expect(screen.getByText('5')).toBeTruthy()
  })

  it('public stats panel shows — when viewCount and interestedCount are missing', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({
      event: { ...mockEvent, attendingCount: 7 },
      loading: false,
      isInitialLoad: false,
      isRefetching: false,
      refetch: vi.fn(),
      error: null,
    })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByText('Statistiques de participation')).toBeTruthy()
    expect(screen.getAllByText('—').length).toBe(2)
    expect(screen.getByText('7')).toBeTruthy()
  })

  it('CapacityIndicator surfaces aria-busy while isRefetching', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({
      event: { ...mockEvent, capacity: 10, availableSpots: 5 },
      loading: true,
      isInitialLoad: false,
      isRefetching: true,
      refetch: vi.fn(),
      error: null,
    })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    const badge = screen.getByText('5 places disponibles')
    const container = badge.closest('[aria-busy="true"]')
    expect(container).toBeTruthy()
  })

  it('shows a localized invalid id message', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: true, isInitialLoad: true, isRefetching: false, refetch: vi.fn(), error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage('abc')

    expect(screen.getByText("Identifiant d'événement invalide.")).toBeTruthy()
  })

  it('shows a localized load error', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: 'Impossible de charger cet événement.' })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByText('Impossible de charger cet événement.')).toBeTruthy()
  })

  it('shows event not found when there is no event and no error', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByText('Événement introuvable.')).toBeTruthy()
  })

  it('renders event details and organizer information', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(mockUser)

    renderPage()

    expect(screen.getByRole('heading', { name: 'Conférence IA' })).toBeTruthy()
    expect(screen.getByText(/Une super conférence/)).toBeTruthy()
    expect(screen.getByText('Conférence')).toBeTruthy()
    expect(screen.getByText('Uni Dufour')).toBeTruthy()
    expect(screen.getByText('200 places au total')).toBeTruthy()
    await waitFor(() => expect(screen.getByText(/Jean Dupont/)).toBeTruthy())
  })

  it('shows organizer-only actions with the final edit route on PUBLISHED', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    const editLink = screen.getByRole('link', { name: /Modifier l'événement/ })
    expect(editLink.getAttribute('href')).toBe('/events/1/edit')
    expect(editLink.querySelector('.lucide-pencil')).toBeTruthy()
    const cancelBtn = screen.getByRole('button', { name: /Annuler l'événement/ })
    expect(cancelBtn.querySelector('.lucide-ban')).toBeTruthy()
    expect(screen.queryByRole('button', { name: /Supprimer l'événement/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Remettre en brouillon/ })).toBeNull()
  })

  it('hides organizer actions for another user', () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'other-user' } })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.queryByRole('link', { name: /Modifier l'événement/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Annuler l'événement/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Supprimer l'événement/ })).toBeNull()
  })

  it('CANCELLED event shows Remettre en brouillon (Undo2) and Supprimer (Trash2) and hides Modifier/Annuler', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.queryByRole('link', { name: /Modifier l'événement/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Annuler l'événement/ })).toBeNull()
    const restoreBtn = screen.getByRole('button', { name: /Remettre en brouillon/ })
    expect(restoreBtn.querySelector('.lucide-undo-2')).toBeTruthy()
    const deleteBtn = screen.getByRole('button', { name: /Supprimer l'événement/ })
    expect(deleteBtn.querySelector('.lucide-trash-2')).toBeTruthy()
    expect(deleteBtn.className).toContain('text-error')
  })

  it('opens and closes the delete confirmation modal on CANCELLED events', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /Supprimer l'événement/ }))
    expect(screen.getByRole('heading', { name: "Supprimer l'événement ?" })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))
    expect(screen.queryByRole('heading', { name: "Supprimer l'événement ?" })).toBeNull()
  })

  it('"Annuler l\'événement" opens confirmation modal and does not call API without confirm', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Annuler l'événement/ }))

    expect(screen.getByRole('heading', { name: "Annuler l'événement ?" })).toBeTruthy()
    expect(screen.getByText(/remettre en brouillon depuis l'onglet Annulés/)).toBeTruthy()
    expect(mockCancelEvent).not.toHaveBeenCalled()
  })

  it('"Annuler l\'événement" cancel button on confirmation closes modal without calling API', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Annuler l'événement/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

    expect(screen.queryByRole('heading', { name: "Annuler l'événement ?" })).toBeNull()
    expect(mockCancelEvent).not.toHaveBeenCalled()
  })

  it('"Annuler l\'événement" confirm calls cancelEvent and navigates to my-events cancelled tab', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)
    mockCancelEvent.mockResolvedValue({})

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Annuler l'événement/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

    await waitFor(() => expect(mockCancelEvent).toHaveBeenCalledWith(1))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/my-events/publications?status=cancelled'))
  })

  it('"Annuler l\'événement" shows error toast on API failure', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)
    mockCancelEvent.mockRejectedValue(new Error('fail'))

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Annuler l'événement/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

    await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith('error', expect.stringContaining('annuler')))
  })

  it('"Remettre en brouillon" calls restoreEvent and navigates to drafts', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)
    mockRestoreEvent.mockResolvedValue({})

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Remettre en brouillon/ }))

    await waitFor(() => expect(mockRestoreEvent).toHaveBeenCalledWith(1))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/my-events/publications?status=draft'))
  })

  it('"Remettre en brouillon" shows error toast on failure', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)
    mockRestoreEvent.mockRejectedValue(new Error('fail'))

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Remettre en brouillon/ }))

    await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith('error', expect.stringContaining('restaurer')))
  })

  it('shows a banner warning toast when bannerUploadError is present in sessionStorage', async () => {
    sessionStorage.setItem(BANNER_UPLOAD_ERROR_KEY, "L'événement a été créé mais la bannière n'a pas pu être uploadée.")

    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(await screen.findByText("L'événement a été créé mais la bannière n'a pas pu être uploadée.")).toBeTruthy()
    expect(sessionStorage.getItem(BANNER_UPLOAD_ERROR_KEY)).toBeNull()
  })

  it('calls deleteEvent and navigates to my-events cancelled on confirm', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)
    mockDeleteEvent.mockResolvedValue(undefined)

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /Supprimer l'événement/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

    await waitFor(() => expect(mockDeleteEvent).toHaveBeenCalledWith(1))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/my-events/publications?status=cancelled'))
  })

  it('hides confirm modal and re-enables button when deleteEvent fails', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)
    mockDeleteEvent.mockRejectedValue(new Error('network error'))

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /Supprimer l'événement/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

    await waitFor(() => expect(screen.queryByRole('heading', { name: "Supprimer l'événement ?" })).toBeNull())
    expect(screen.getByRole('button', { name: /Supprimer l'événement/ })).toBeTruthy()
  })

  it('sets organizer to null when getUserById rejects', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockRejectedValue(new Error('not found'))

    renderPage()

    await waitFor(() => expect(mockGetUserById).toHaveBeenCalled())
    expect(screen.queryByText(/Organisé par/)).toBeNull()
  })

  it('renders event with no capacity (InfoRow without color branch)', () => {
    const eventNoCapacity = { ...mockEvent, capacity: undefined }
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: eventNoCapacity, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByRole('heading', { name: 'Conférence IA' })).toBeTruthy()
  })

  it('copies the event URL and shows a success toast when sharing', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /Partager/ }))
    await waitFor(() => expect(writeText).toHaveBeenCalled())
    await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith('success', 'Lien copié !', 3000))
  })

  it('shows a fallback error toast when clipboard writeText rejects', async () => {
    const writeText = vi.fn().mockRejectedValue(new Error('denied'))
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /Partager/ }))
    await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith('error', expect.stringContaining('Copiez ce lien'), 6000))
  })

  it('shows a fallback error toast when clipboard API is unavailable', () => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true })

    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /Partager/ }))
    expect(mockShowToast).toHaveBeenCalledWith('error', expect.stringContaining('Copiez ce lien'), 6000)
  })

  describe('CapacityIndicator', () => {
    it('shows green badge when spots are available', () => {
      const event = { ...mockEvent, capacity: 20, availableSpots: 10 }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText('10 places disponibles')).toBeTruthy()
    })

    it('shows orange badge when ≤10% spots remain', () => {
      const event = { ...mockEvent, capacity: 20, availableSpots: 1 }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText('Presque complet')).toBeTruthy()
    })

    it('shows red badge when event is full', () => {
      const event = { ...mockEvent, capacity: 20, availableSpots: 0 }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText('Complet')).toBeTruthy()
    })

    it('shows waitlist count when waitlistedCount > 0', () => {
      const event = { ...mockEvent, capacity: 20, availableSpots: 0, waitlistedCount: 4 }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText('4 en liste d\'attente')).toBeTruthy()
    })

    it('does not show waitlist pill when waitlistedCount is 0', () => {
      const event = { ...mockEvent, capacity: 20, availableSpots: 5, waitlistedCount: 0 }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.queryByText(/en liste d'attente/)).toBeNull()
    })

    it('does not render capacity indicator when availableSpots is null', () => {
      const event = { ...mockEvent, capacity: 20, availableSpots: null }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.queryByText('Places disponibles')).toBeNull()
    })
  })

  describe('AttendeesList integration', () => {
    it('renders the Participants heading for any event', () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'other' } })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, attendingCount: 4 },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByRole('heading', { name: 'Participants' })).toBeTruthy()
    })

    it('calls useAttendees with enabled=false for a non-organizer', () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'other' } })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, attendingCount: 4 },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(mockUseAttendees).toHaveBeenCalledWith(1, { enabled: false })
    })

    it('calls useAttendees with enabled=true for the organizer', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, attendingCount: 0 },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(mockUseAttendees).toHaveBeenCalledWith(1, { enabled: true })
      expect(screen.getByRole('tab', { name: /Participants/ })).toBeTruthy()
    })

    it('onAfterSuccess refetches event AND attendees for the organizer', () => {
      const refetchEvent = vi.fn()
      const refetchAttendees = vi.fn()
      mockUseAttendees.mockReturnValue({ ...defaultAttendeesState, refetch: refetchAttendees })
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, attendingCount: 0 },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: refetchEvent,
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      // Capture the onAfterSuccess option passed into useAttendance and invoke it.
      const lastCall = mockUseAttendance.mock.calls.at(-1)
      expect(lastCall).toBeTruthy()
      const options = lastCall?.[4] as { onAfterSuccess?: () => void }
      options.onAfterSuccess?.()

      expect(refetchEvent).toHaveBeenCalledTimes(1)
      expect(refetchAttendees).toHaveBeenCalledTimes(1)
    })

    it('onAfterSuccess refetches event but NOT attendees for non-organizer', () => {
      const refetchEvent = vi.fn()
      const refetchAttendees = vi.fn()
      mockUseAttendees.mockReturnValue({ ...defaultAttendeesState, refetch: refetchAttendees })
      mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'other-user' } })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, attendingCount: 0 },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: refetchEvent,
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      const lastCall = mockUseAttendance.mock.calls.at(-1)
      const options = lastCall?.[4] as { onAfterSuccess?: () => void }
      options.onAfterSuccess?.()

      expect(refetchEvent).toHaveBeenCalledTimes(1)
      expect(refetchAttendees).not.toHaveBeenCalled()
    })

    it('passes the lifted attendees hook to AttendeesList for the organizer', () => {
      const refetch = vi.fn()
      mockUseAttendees.mockReturnValue({ ...defaultAttendeesState, refetch })
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, attendingCount: 0 },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      // Tabs render → confirms OrganizerView received the lifted hook.
      expect(screen.getByRole('tab', { name: /Participants/ })).toBeTruthy()
    })

    it('renders Participants section in the same column as "À propos", after it', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, attendingCount: 2 },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      const aboutHeading = screen.getByRole('heading', { name: 'À propos' })
      const participantsHeading = screen.getByRole('heading', { name: 'Participants' })

      // Participants comes AFTER "À propos" in document order
      const position = aboutHeading.compareDocumentPosition(participantsHeading)
      expect(position & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

      // Both live under the same left-column container (same parent flex column)
      const aboutCard = aboutHeading.closest('div.bg-linear-to-br')
      const participantsSection = participantsHeading.closest('section')
      expect(aboutCard?.parentElement).toBe(participantsSection?.parentElement)
    })
  })

  describe('SCRUM-117 — extra optional fields display', () => {
    it('does not render the extra-info card when none of the four fields are present', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.queryByText('Informations complémentaires')).toBeNull()
    })

    it('renders websiteUrl as an external link opening in a new tab', () => {
      const withWebsite = { ...mockEvent, websiteUrl: 'https://unige.ch/event' }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: withWebsite, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText('Informations complémentaires')).toBeTruthy()
      const link = screen.getByRole('link', { name: 'https://unige.ch/event' }) as HTMLAnchorElement
      expect(link.getAttribute('href')).toBe('https://unige.ch/event')
      expect(link.getAttribute('target')).toBe('_blank')
      expect(link.getAttribute('rel')).toBe('noopener noreferrer')
    })

    it('does not render an anchor when websiteUrl uses a non-http(s) scheme (XSS guard)', () => {
      const unsafe = { ...mockEvent, websiteUrl: 'javascript:alert(1)' }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: unsafe, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText('javascript:alert(1)')).toBeTruthy()
      expect(screen.queryByRole('link', { name: 'javascript:alert(1)' })).toBeNull()
    })

    it('does not render an anchor when websiteUrl is malformed', () => {
      const malformed = { ...mockEvent, websiteUrl: 'not a url' }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: malformed, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText('not a url')).toBeTruthy()
      expect(screen.queryByRole('link', { name: 'not a url' })).toBeNull()
    })

    it('renders contactEmail as a mailto link', () => {
      const withEmail = { ...mockEvent, contactEmail: 'contact@unige.ch' }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: withEmail, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      const link = screen.getByRole('link', { name: 'contact@unige.ch' }) as HTMLAnchorElement
      expect(link.getAttribute('href')).toBe('mailto:contact@unige.ch')
    })

    it('renders a formatted registrationDeadline when present', () => {
      const withDeadline = { ...mockEvent, registrationDeadline: '2026-04-09T18:00:00.000Z' }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: withDeadline, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText(/Inscriptions jusqu'au/)).toBeTruthy()
    })

    it('renders tags as clickable chips linking to /events/search?q=<tag>', () => {
      const withTags = { ...mockEvent, tags: ['forum', 'carrière emploi'] }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: withTags, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      const forumLink = screen.getByRole('link', { name: 'forum' }) as HTMLAnchorElement
      expect(forumLink.getAttribute('href')).toBe('/events/search?q=forum')

      const encodedLink = screen.getByRole('link', { name: 'carrière emploi' }) as HTMLAnchorElement
      expect(encodedLink.getAttribute('href')).toBe('/events/search?q=carri%C3%A8re%20emploi')
    })

    it('does not render the tags row when tags is an empty array', () => {
      const withEmptyTags = { ...mockEvent, websiteUrl: 'https://unige.ch', tags: [] }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: withEmptyTags, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText('Informations complémentaires')).toBeTruthy()
      expect(screen.queryByRole('link', { name: 'forum' })).toBeNull()
    })

    it('renders all four fields together when populated', () => {
      const allFields = {
        ...mockEvent,
        websiteUrl: 'https://unige.ch/event',
        contactEmail: 'contact@unige.ch',
        registrationDeadline: '2026-04-09T18:00:00.000Z',
        tags: ['forum'],
      }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: allFields, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByRole('link', { name: 'https://unige.ch/event' })).toBeTruthy()
      expect(screen.getByRole('link', { name: 'contact@unige.ch' })).toBeTruthy()
      expect(screen.getByText(/Inscriptions jusqu'au/)).toBeTruthy()
      expect(screen.getByRole('link', { name: 'forum' })).toBeTruthy()
    })
  })

  describe('DRAFT redirect (Fix 6)', () => {
    const draftEvent = { ...mockEvent, status: 'DRAFT' as const }

    it('redirects the creator from /events/:id to /events/:id/edit when status is DRAFT', () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, admin: false } })
      mockUseEvent.mockReturnValue({ event: draftEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(mockNavigate).toHaveBeenCalledWith(`/events/${draftEvent.id}/edit`, { replace: true })
    })

    it('does not redirect the creator when the event is PUBLISHED', () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, admin: false } })
      mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('does not redirect an admin viewing a DRAFT they created', () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, admin: true } })
      mockUseEvent.mockReturnValue({ event: draftEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('does not redirect a non-creator non-admin viewing a DRAFT', () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'user-2', admin: false } })
      mockUseEvent.mockReturnValue({ event: draftEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(mockNavigate).not.toHaveBeenCalled()
    })
  })

  // --- View tracking (Copilot review) ---

  describe('view tracking', () => {
    it('calls recordEventView on mount when authenticated and eventId is valid', async () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      await waitFor(() => expect(mockRecordEventView).toHaveBeenCalledWith(1))
      expect(mockRecordEventView).toHaveBeenCalledTimes(1)
    })

    it('does not call recordEventView for anonymous (no user) viewers', async () => {
      mockUseAuth.mockReturnValue({ user: null })
      mockUseEvent.mockReturnValue({
        event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      // wait long enough for the effect to have run if it were going to
      await new Promise(r => setTimeout(r, 0))
      expect(mockRecordEventView).not.toHaveBeenCalled()
    })

    it('does not call recordEventView when eventId is invalid', async () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: null, loading: false, isInitialLoad: true, isRefetching: false, refetch: vi.fn(), error: null })

      renderPage('abc') // non-numeric id → eventId resolves to null

      await new Promise(r => setTimeout(r, 0))
      expect(mockRecordEventView).not.toHaveBeenCalled()
    })

    it('swallows recordEventView errors without breaking the page', async () => {
      mockRecordEventView.mockRejectedValueOnce(new Error('network'))
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      await waitFor(() => expect(mockRecordEventView).toHaveBeenCalled())
      // Page still renders normally
      expect(screen.getByRole('heading', { name: 'Conférence IA' })).toBeTruthy()
    })
  })

  // --- Favorite refetch wiring (review #90 follow-up) ---

  describe('favorite onAfterSuccess', () => {
    it('passes an onAfterSuccess callback to useFavorite that refetches the event', async () => {
      const refetchEvent = vi.fn().mockResolvedValue(undefined)
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: refetchEvent, error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      // Find the call made by FavoriteTextButton (eventId, initialFavorited, options)
      const favoriteCallWithOptions = mockUseFavorite.mock.calls.find(
        ([, , opts]) => opts && typeof opts.onAfterSuccess === 'function',
      )
      expect(favoriteCallWithOptions).toBeTruthy()

      const onAfterSuccess = favoriteCallWithOptions![2].onAfterSuccess as () => Promise<void>
      await onAfterSuccess()
      expect(refetchEvent).toHaveBeenCalledTimes(1)
    })
  })
})