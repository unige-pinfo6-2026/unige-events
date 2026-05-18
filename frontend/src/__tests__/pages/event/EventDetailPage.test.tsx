
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import EventDetailPage from '@/pages/event/EventDetailPage'

vi.mock('@/hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('@/hooks/useEvent', () => ({ useEvent: vi.fn() }))
vi.mock('@/hooks/useOccurrences', () => ({ useOccurrences: vi.fn() }))
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
const mockReportOpen = vi.fn()
const mockReportClose = vi.fn()
const mockReportSubmit = vi.fn()
vi.mock('@/hooks/useReport', () => ({
  useReport: vi.fn(),
}))
vi.mock('@/hooks/useCoOrganizers', () => ({
  useCoOrganizers: vi.fn(() => ({
    coOrganizers: [],
    loading: false,
    error: null,
    invite: vi.fn(),
    remove: vi.fn(),
    refresh: vi.fn(),
  })),
}))
vi.mock('@/hooks/useComments', () => ({
  useComments: vi.fn(() => ({
    comments: [],
    hasMore: false,
    loading: false,
    posting: false,
    error: null,
    post: vi.fn().mockResolvedValue({ ok: true }),
    postReply: vi.fn().mockResolvedValue({ ok: true }),
    remove: vi.fn().mockResolvedValue(undefined),
    loadMore: vi.fn(),
    refresh: vi.fn(),
  })),
}))
vi.mock('@/components/event/ReportModal', () => ({
  default: ({ onClose, onSubmit }: { onClose: () => void; onSubmit: () => Promise<void> }) => (
    <div data-testid="report-modal">
      <button type="button" onClick={onClose}>CloseModal</button>
      <button type="button" onClick={() => onSubmit()}>SubmitModal</button>
    </div>
  ),
}))
const mockShowToast = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: mockShowToast }),
}))

import { useAuth } from '@/hooks/useAuth'
import { useEvent } from '@/hooks/useEvent'
import { useOccurrences } from '@/hooks/useOccurrences'
import type { Event } from '@/types/event'
import { useAttendees } from '@/hooks/useAttendees'
import { useAttendance } from '@/hooks/useAttendance'
import { useReport } from '@/hooks/useReport'
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
const mockUseReport = vi.mocked(useReport)
const mockUseOccurrences = useOccurrences as ReturnType<typeof vi.fn>

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
}

const defaultAttendanceState = {
  currentStatus: null,
  attendingCount: 5,
  loading: false,
  error: null,
  isFull: false,
  toggle: vi.fn(),
}

const defaultReportState = {
  isOpen: false,
  submitting: false,
  open: mockReportOpen,
  close: mockReportClose,
  submit: mockReportSubmit,
}

beforeEach(() => {
  mockUseAttendees.mockReturnValue(defaultAttendeesState)
  mockUseAttendance.mockReturnValue(defaultAttendanceState)
  mockUseReport.mockReturnValue(defaultReportState)
  mockRecordEventView.mockResolvedValue(undefined)
  mockUseOccurrences.mockReturnValue({ loading: false, error: null, data: null })
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
    // Post-SCRUM-137 PR : le créateur apparaît à 2 endroits — bloc organizer
    // historique + nouveau panneau "Équipe organisatrice" (EventOrganizerTeam).
    await waitFor(() => expect(screen.getAllByText(/Jean Dupont/).length).toBeGreaterThan(0))
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

  it('shows edit/cancel/stats for accepted co-organizers (SCRUM-137)', () => {
    // Co-organizer = non-creator user with coOrganizerOf=true in the response.
    mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'co-org-user' } })
    mockUseEvent.mockReturnValue({
      event: { ...mockEvent, coOrganizerOf: true },
      loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null,
    })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByRole('link', { name: /Modifier l'événement/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: /Annuler l'événement/ })).toBeTruthy()
    expect(screen.getByRole('link', { name: /Voir les statistiques/ })).toBeTruthy()
  })

  it('hides Supprimer for co-organizers on CANCELLED events (creator-only delete)', () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'co-org-user' } })
    mockUseEvent.mockReturnValue({
      event: { ...mockEvent, status: 'CANCELLED' as const, coOrganizerOf: true },
      loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null,
    })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByRole('button', { name: /Remettre en brouillon/ })).toBeTruthy()
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

  // SCRUM-141 CLS fix: the organizer fetch resolves a frame or two AFTER
  // the page first paints. Without a reserved slot, the resolved row pops
  // in and the entire sidebar reflows ~50px. The placeholder keeps the
  // height stable from first paint → swap is in-place.
  it('renders an organizer-slot placeholder before getUserById resolves (CLS fix)', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    // Never-resolving promise — captures the visible state before the
    // fetch settles, mirroring the real-world first-paint window.
    mockGetUserById.mockImplementation(() => new Promise(() => {}))

    renderPage()

    expect(screen.getByTestId('organizer-placeholder')).toBeTruthy()
    // The resolved row text is not yet in the DOM; only the placeholder is.
    expect(screen.queryByText(/Organisé par/)).toBeNull()
  })

  it('swaps the placeholder for the resolved organizer row in place when getUserById resolves', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null})
    mockGetUserById.mockResolvedValue(mockUser)

    renderPage()

    await waitFor(() => expect(screen.getByText(/Organisé par/)).toBeTruthy())
    expect(screen.queryByTestId('organizer-placeholder')).toBeNull()
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

    // SCRUM-S7 — useAttendees now runs for every authenticated user.
    it('calls useAttendees with enabled=false for an unauthenticated viewer', () => {
      mockUseAuth.mockReturnValue({ user: null })
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
      // Compact summary, no tablist.
      expect(screen.queryByRole('tablist')).toBeNull()
    })

    it('calls useAttendees with enabled=true for an authenticated non-organizer', () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'someone-else' } })
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

      expect(mockUseAttendees).toHaveBeenCalledWith(1, { enabled: true })
      // Full list — tablist is rendered for every authenticated viewer.
      expect(screen.getByRole('tab', { name: /Participants/ })).toBeTruthy()
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

    it('onAfterSuccess refetches event AND attendees for any authenticated user', () => {
      const refetchEvent = vi.fn()
      const refetchAttendees = vi.fn()
      mockUseAttendees.mockReturnValue({ ...defaultAttendeesState, refetch: refetchAttendees })
      mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'someone-else' } })
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
      expect(lastCall).toBeTruthy()
      const options = lastCall?.[4] as { onAfterSuccess?: () => void }
      options.onAfterSuccess?.()

      expect(refetchEvent).toHaveBeenCalledTimes(1)
      // SCRUM-S7: attendees refetch is no longer gated on organizer status — any
      // authenticated viewer who attends/unattends should see the list refresh.
      expect(refetchAttendees).toHaveBeenCalledTimes(1)
    })

    it('passes the lifted attendees hook to AttendeesList for any authenticated viewer', () => {
      const refetch = vi.fn()
      mockUseAttendees.mockReturnValue({ ...defaultAttendeesState, refetch })
      mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'someone-else' } })
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

      // Both live under the same left-column wrapper (which uses display:contents on
      // mobile to flatten its children into the parent grid). Walk up to the column
      // ancestor instead of asserting a direct parent — ordering wrappers may sit in
      // between for the mobile reordering pass.
      const aboutCard = aboutHeading.closest('div.bg-linear-to-br')
      const participantsSection = participantsHeading.closest('section')
      const mainColumnSelector = '.flex.flex-col.gap-5'
      expect(aboutCard?.closest(mainColumnSelector)).toBe(participantsSection?.closest(mainColumnSelector))
      expect(aboutCard?.closest(mainColumnSelector)).not.toBeNull()
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

  // Regression: report button moved from right action column to banner top-right (feature/s6-report-modal).
  // Visual revision: round dark-backdrop pill matching the favoris star button, expanding to icon+label on hover.
  describe('Report button and modal', () => {
    function setupNonOrganizer() {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'other-user' } })
      mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null })
      mockGetUserById.mockResolvedValue(null)
    }

    it('shows "Signaler cet événement" button for logged-in non-organizer', () => {
      setupNonOrganizer()

      renderPage()

      expect(screen.getByRole('button', { name: /Signaler cet événement/ })).toBeTruthy()
    })

    it('hides "Signaler cet événement" button for the organizer', () => {
      // Aligned on main (PR #140): the report button is hidden for the
      // creator/organizer; only authenticated non-organizers see it. The hook
      // still handles 422 cannot_report_own_event defensively in case the
      // organiser status changes mid-flow.
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.queryByRole('button', { name: /Signaler cet événement/ })).toBeNull()
    })

    it('hides "Signaler cet événement" button when user is not logged in', () => {
      mockUseAuth.mockReturnValue({ user: null })
      mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.queryByRole('button', { name: /Signaler cet événement/ })).toBeNull()
    })

    it('clicking "Signaler cet événement" calls reportHook.open', () => {
      setupNonOrganizer()

      renderPage()
      fireEvent.click(screen.getByRole('button', { name: /Signaler cet événement/ }))

      expect(mockReportOpen).toHaveBeenCalledOnce()
    })

    it('renders ReportModal when isOpen is true', () => {
      mockUseReport.mockReturnValue({ ...defaultReportState, isOpen: true })
      setupNonOrganizer()

      renderPage()

      expect(screen.getByTestId('report-modal')).toBeTruthy()
    })

    it('does not render ReportModal when isOpen is false', () => {
      setupNonOrganizer()

      renderPage()

      expect(screen.queryByTestId('report-modal')).toBeNull()
    })

    it('flag button has aria-label and title attributes for a11y and tooltip', () => {
      setupNonOrganizer()

      renderPage()

      const flagBtn = screen.getByRole('button', { name: /Signaler cet événement/ })
      expect(flagBtn.getAttribute('aria-label')).toBe('Signaler cet événement')
      expect(flagBtn.getAttribute('title')).toBe('Signaler cet événement')
    })

    it('flag button lives inside the banner element (not the right action column)', () => {
      setupNonOrganizer()

      renderPage()

      // EventBanner renders with `relative overflow-hidden`; the title <h1> is inside it.
      const titleHeading = screen.getByRole('heading', { name: 'Conférence IA', level: 1 })
      const bannerEl = titleHeading.closest('div.relative.overflow-hidden')
      expect(bannerEl).toBeTruthy()
      const flagBtn = within(bannerEl as HTMLElement).getByRole('button', { name: /Signaler cet événement/ })
      expect(flagBtn).toBeTruthy()
    })

    it('right action column no longer contains a "Signaler" button', () => {
      setupNonOrganizer()

      renderPage()

      // Anchor on "Partager" (right column), walk up to its action card, scope query inside.
      const partagerBtn = screen.getByRole('button', { name: /Partager/ })
      const actionCard = partagerBtn.closest('div.flex.flex-col.gap-4')
      expect(actionCard).toBeTruthy()
      expect(within(actionCard as HTMLElement).queryByRole('button', { name: /Signaler/ })).toBeNull()
    })
  })

  describe('DRAFT redirect (Fix 6)', () => {
    const draftEvent = { ...mockEvent, status: 'DRAFT' as const }

    it('redirects the creator from /events/:id to /events/:id/edit when status is DRAFT', () => {
      mockUseAuth.mockReturnValue({ user: mockUser, isAdmin: false })
      mockUseEvent.mockReturnValue({ event: draftEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(mockNavigate).toHaveBeenCalledWith(`/events/${draftEvent.id}/edit`, { replace: true })
    })

    it('does not redirect the creator when the event is PUBLISHED', () => {
      mockUseAuth.mockReturnValue({ user: mockUser, isAdmin: false })
      mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('does not redirect an admin viewing a DRAFT they created', () => {
      mockUseAuth.mockReturnValue({ user: mockUser, isAdmin: true })
      mockUseEvent.mockReturnValue({ event: draftEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('does not redirect a non-creator non-admin viewing a DRAFT', () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'user-2' }, isAdmin: false })
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

    it('calls recordEventView for anonymous viewers too (post-V11 anon support)', async () => {
      // Post-2026-05-14 (Axe 4 PR) : la vue est enregistrée même pour les
      // utilisateurs anonymes — le backend déduplique par sessionId UUID
      // envoyé en body par statsApi.recordEventView.
      mockUseAuth.mockReturnValue({ user: null })
      mockUseEvent.mockReturnValue({
        event: mockEvent, loading: false, isInitialLoad: false, isRefetching: false, refetch: vi.fn(), error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      await waitFor(() => expect(mockRecordEventView).toHaveBeenCalledWith(1))
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

  // The mobile reorder is implemented purely in CSS — column wrappers switch
  // to display:contents on mobile and each section carries a max-lg:order-N
  // class. DOM order is unchanged on every viewport, so we assert on the
  // markup (classes) rather than visual layout (jsdom doesn't paint).
  describe('responsive layout (Bug 2 — mobile section order)', () => {
    function setupOrganizer() {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, websiteUrl: 'https://example.com', attendingCount: 1 },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)
    }

    it('flattens both column wrappers on mobile via display: contents', () => {
      setupOrganizer()
      const { container } = renderPage()

      const columns = container.querySelectorAll('.max-lg\\:contents')
      // Main + sidebar wrappers
      expect(columns.length).toBe(2)
    })

    it('keeps the desktop two-column grid container untouched', () => {
      setupOrganizer()
      const { container } = renderPage()

      const grid = container.querySelector('.grid.grid-cols-\\[3fr_2fr\\]')
      expect(grid).not.toBeNull()
      expect(grid!.className).toContain('items-start')
      expect(grid!.className).toContain('max-lg:grid-cols-1')
    })

    it('assigns interleaved mobile orders so reading flow goes banner → key info → description → actions → attendees → extras → ICS → stats → organizer actions → stats link', () => {
      setupOrganizer()
      const { container } = renderPage()

      // banner (order 1)
      expect(container.querySelector('.max-lg\\:order-1')).not.toBeNull()
      // infos clés (order 2)
      expect(container.querySelector('.max-lg\\:order-2')).not.toBeNull()
      // description (order 3)
      expect(container.querySelector('.max-lg\\:order-3')).not.toBeNull()
      // attendance + favoris card (order 4)
      expect(container.querySelector('.max-lg\\:order-4')).not.toBeNull()
      // attendees wrapper (order 5)
      expect(container.querySelector('.max-lg\\:order-5')).not.toBeNull()
      // informations complémentaires (order 6)
      expect(container.querySelector('.max-lg\\:order-6')).not.toBeNull()
      // ICS export wrapper (order 7)
      expect(container.querySelector('.max-lg\\:order-7')).not.toBeNull()
      // stats panel wrapper (order 8)
      expect(container.querySelector('.max-lg\\:order-8')).not.toBeNull()
      // organizer actions (order 9)
      expect(container.querySelector('.max-lg\\:order-9')).not.toBeNull()
      // organizer stats link (order 10)
      expect(container.querySelector('.max-lg\\:order-10')).not.toBeNull()
    })

    it('does not apply legacy whole-column orders that would lump the sidebar above the main column', () => {
      setupOrganizer()
      const { container } = renderPage()

      // Pre-fix layout used max-lg:order-1 / max-lg:order-2 on the column
      // wrappers themselves. The fix moves ordering down to individual
      // sections, so the wrappers must not carry order classes anymore.
      const wrappers = container.querySelectorAll('.max-lg\\:contents')
      wrappers.forEach((w) => {
        expect(w.className).not.toMatch(/\bmax-lg:order-1\b(?!\d)/)
        expect(w.className).not.toMatch(/\bmax-lg:order-2\b(?!\d)/)
      })
    })

    it('regression — every section that existed before the reorder still renders', () => {
      setupOrganizer()
      const { container } = renderPage()

      // Banner + title (banner contains the title h1)
      expect(screen.getByRole('heading', { level: 1, name: 'Conférence IA' })).toBeTruthy()
      // Description card heading
      expect(screen.getByRole('heading', { name: 'À propos' })).toBeTruthy()
      // Attendees section heading
      expect(screen.getByRole('heading', { name: 'Participants' })).toBeTruthy()
      // Informations complémentaires heading
      expect(screen.getByRole('heading', { name: 'Informations complémentaires' })).toBeTruthy()
      // Public stats panel
      expect(screen.getByText('Statistiques de participation')).toBeTruthy()
      // Organizer actions still wired (Modifier link present for organizer)
      expect(screen.getByText("Modifier l'événement")).toBeTruthy()
      // Stats link present for organizer
      expect(screen.getByText('Voir les statistiques')).toBeTruthy()
      // Sticky sidebar still applied at lg+ (regression on the desktop layout)
      const stickyCol = container.querySelector('.lg\\:sticky')
      expect(stickyCol).not.toBeNull()
    })
  })

  // Long-unbroken-word overflow guard for the event title (banner) and the
  // description body. jsdom doesn't paint, so we assert on the wrap utility
  // class. break-all is explicitly forbidden by the spec.
  describe('user-content overflow wrapping', () => {
    const LONG = 'a'.repeat(200)

    it('applies wrap-anywhere on the banner title h1', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, title: LONG },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      const h1 = screen.getByRole('heading', { level: 1, name: LONG })
      expect(h1.className).toContain('wrap-anywhere')
      expect(h1.className).not.toContain('break-all')
    })

    it('applies wrap-anywhere on the description paragraph', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, description: LONG },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      const p = screen.getByText(LONG)
      expect(p.className).toContain('wrap-anywhere')
      expect(p.className).not.toContain('break-all')
    })

    it('does not crash with an empty description (paragraph absent)', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, description: undefined },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      // Description card is conditional on event.description being truthy
      expect(screen.queryByRole('heading', { name: 'À propos' })).toBeNull()
    })
  })

  describe('occurrences section (SCRUM-151)', () => {
    // Cast to Partial<Event> to widen the narrow literal types from `mockEvent`
    // (status: 'PUBLISHED' as const) — allows tests to inject status: 'CANCELLED'
    // and the new parentEventId / recurrenceRule fields.
    function setupEvent(overrides: Partial<Event>) {
      mockUseAuth.mockReturnValue({ user: null })
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, ...overrides },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)
    }

    it('does not render the toggle for a standalone event and never invokes useOccurrences', () => {
      setupEvent({})
      renderPage()
      expect(screen.queryByRole('button', { name: /Voir toutes les occurrences/i })).toBeNull()
      // The OccurrencesSection is unmounted for standalones, so its hook never runs.
      expect(mockUseOccurrences).not.toHaveBeenCalled()
    })

    it('renders the toggle on a recurring parent and keeps useOccurrences disabled at mount', () => {
      setupEvent({ recurrenceRule: 'FREQ=WEEKLY;UNTIL=20260601' })
      renderPage()
      expect(screen.getByRole('button', { name: /Voir toutes les occurrences/i })).toBeTruthy()
      // Last call before expand: enabled === false
      expect(mockUseOccurrences).toHaveBeenLastCalledWith(1, { enabled: false })
    })

    it('renders the toggle on an occurrence and points the parentId at its parent', () => {
      setupEvent({ id: 7, parentEventId: 42 })
      renderPage()
      expect(screen.getByRole('button', { name: /Voir toutes les occurrences/i })).toBeTruthy()
      expect(mockUseOccurrences).toHaveBeenLastCalledWith(42, { enabled: false })
    })

    it('flips useOccurrences.enabled true when the user clicks the toggle (lazy fetch)', () => {
      setupEvent({ recurrenceRule: 'FREQ=WEEKLY;UNTIL=20260601' })
      renderPage()
      fireEvent.click(screen.getByRole('button', { name: /Voir toutes les occurrences/i }))
      expect(mockUseOccurrences).toHaveBeenLastCalledWith(1, { enabled: true })
    })

    it('renders a compact row per occurrence with date, status, link and the "vous êtes ici" marker', async () => {
      setupEvent({ id: 7, parentEventId: 42 })

      mockUseOccurrences.mockImplementation((parentId, { enabled }) => {
        if (!enabled || parentId === null) return { loading: false, error: null, data: null }
        return {
          loading: false,
          error: null,
          data: [
            { ...mockEvent, id: 6, title: 'Session 1', status: 'CANCELLED' as const, startDate: '2026-03-01T10:00:00' },
            { ...mockEvent, id: 7, title: 'Session 2 (en cours)', status: 'PUBLISHED' as const, startDate: '2026-03-08T10:00:00' },
          ],
        }
      })

      renderPage()
      fireEvent.click(screen.getByRole('button', { name: /Voir toutes les occurrences/i }))

      await waitFor(() => expect(screen.getByText('Session 1')).toBeTruthy())
      expect(screen.getByText('Session 2 (en cours)')).toBeTruthy()
      expect(screen.getByText('Annulé')).toBeTruthy()
      expect(screen.getByText('Publié')).toBeTruthy()
      expect(screen.getByText(/Vous êtes ici/i)).toBeTruthy()

      // Link out to each occurrence (and not the current page itself).
      expect(screen.getByText('Session 1').closest('a')?.getAttribute('href')).toBe('/events/6')
      expect(screen.getByText('Session 2 (en cours)').closest('a')?.getAttribute('href')).toBe('/events/7')
    })

    it('shows the count next to the toggle once data has loaded', async () => {
      setupEvent({ recurrenceRule: 'FREQ=WEEKLY;UNTIL=20260601' })

      mockUseOccurrences.mockImplementation((_p, { enabled }) => ({
        loading: false,
        error: null,
        data: enabled
          ? [
            { ...mockEvent, id: 11 },
            { ...mockEvent, id: 12 },
            { ...mockEvent, id: 13 },
          ]
          : null,
      }))

      renderPage()
      fireEvent.click(screen.getByRole('button', { name: /Voir toutes les occurrences/i }))
      await waitFor(() => expect(screen.getByRole('button', { name: /Voir toutes les occurrences \(3\)/i })).toBeTruthy())
    })

    it('handles a CANCELLED parent with PUBLISHED occurrences (status badges per row)', async () => {
      setupEvent({ status: 'CANCELLED' as const, recurrenceRule: 'FREQ=WEEKLY' })

      mockUseOccurrences.mockImplementation((_p, { enabled }) => ({
        loading: false,
        error: null,
        data: enabled
          ? [{ ...mockEvent, id: 21, status: 'PUBLISHED' as const, title: 'Future session' }]
          : null,
      }))

      renderPage()
      fireEvent.click(screen.getByRole('button', { name: /Voir toutes les occurrences/i }))

      await waitFor(() => expect(screen.getByText('Future session')).toBeTruthy())
      expect(screen.getByText('Publié')).toBeTruthy()
    })
  })

  describe('Documents section (SCRUM-149)', () => {
    const sampleAttachment = {
      id: 7,
      fileName: 'programme.pdf',
      fileUrl: 'https://s3.example.com/events/1/programme.pdf',
      fileSize: 2 * 1024 * 1024,
      mimeType: 'application/pdf' as const,
      uploadedById: 'u-1',
      uploadedAt: '2026-05-18T10:00:00Z',
    }

    function setupDetailEvent(attachments: Event['attachments']) {
      mockUseEvent.mockReturnValue({
        event: { ...mockEvent, attachments },
        loading: false,
        isInitialLoad: false,
        isRefetching: false,
        refetch: vi.fn(),
        error: null,
      })
      mockGetUserById.mockResolvedValue(null)
    }

    it('does not render the Documents section when attachments is null', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      setupDetailEvent(null)
      renderPage()
      expect(screen.queryByText('Documents')).toBeNull()
    })

    it('does not render the Documents section when attachments is empty', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      setupDetailEvent([])
      renderPage()
      expect(screen.queryByText('Documents')).toBeNull()
    })

    it('renders the Documents section with a download link for each attachment', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      setupDetailEvent([sampleAttachment])
      renderPage()

      expect(screen.getByText('Documents')).toBeTruthy()
      const link = screen.getByRole('link', { name: 'programme.pdf' }) as HTMLAnchorElement
      expect(link.getAttribute('href')).toBe(sampleAttachment.fileUrl)
      expect(link.getAttribute('download')).toBe('programme.pdf')
      expect(screen.getByText('2.0 MB')).toBeTruthy()
    })

    it('renders the Documents section for unauthenticated viewers (no auth gate)', () => {
      // user === null → no auth context, but Documents must still be visible.
      mockUseAuth.mockReturnValue({ user: null })
      setupDetailEvent([sampleAttachment])
      renderPage()
      expect(screen.getByText('Documents')).toBeTruthy()
      expect(screen.getByRole('link', { name: 'programme.pdf' })).toBeTruthy()
    })
  })
})