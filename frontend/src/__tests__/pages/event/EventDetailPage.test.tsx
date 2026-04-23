
import { afterEach, describe, expect, it, vi } from 'vitest'
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
vi.mock('@/hooks/useFavorite', () => ({
  useFavorite: () => ({ favorited: false, loading: false, toggle: vi.fn() }),
}))
const mockShowToast = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: mockShowToast }),
}))

import { useAuth } from '@/hooks/useAuth'
import { useEvent } from '@/hooks/useEvent'
import { cancelEvent, deleteEvent, restoreEvent } from '@/services/eventApi'
import { getUserById } from '@/services/userService'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockUseEvent = useEvent as ReturnType<typeof vi.fn>
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
    mockUseEvent.mockReturnValue({ event: null, loading: true, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(document.querySelector('[data-boneyard="event-detail"]')).toBeTruthy()
  })

  it('shows a localized invalid id message', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: true, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage('abc')

    expect(screen.getByText("Identifiant d'événement invalide.")).toBeTruthy()
  })

  it('shows a localized load error', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, error: 'Impossible de charger cet événement.' })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByText('Impossible de charger cet événement.')).toBeTruthy()
  })

  it('shows event not found when there is no event and no error', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: null, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByText('Événement introuvable.')).toBeTruthy()
  })

  it('renders event details and organizer information', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
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
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
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
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.queryByRole('link', { name: /Modifier l'événement/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Annuler l'événement/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /Supprimer l'événement/ })).toBeNull()
  })

  it('CANCELLED event shows Remettre en brouillon (Undo2) and Supprimer (Trash2) and hides Modifier/Annuler', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, error: null })
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
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /Supprimer l'événement/ }))
    expect(screen.getByRole('heading', { name: "Supprimer l'événement ?" })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))
    expect(screen.queryByRole('heading', { name: "Supprimer l'événement ?" })).toBeNull()
  })

  it('"Annuler l\'événement" opens confirmation modal and does not call API without confirm', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Annuler l'événement/ }))

    expect(screen.getByRole('heading', { name: "Annuler l'événement ?" })).toBeTruthy()
    expect(screen.getByText(/remettre en brouillon depuis l'onglet Annulés/)).toBeTruthy()
    expect(mockCancelEvent).not.toHaveBeenCalled()
  })

  it('"Annuler l\'événement" cancel button on confirmation closes modal without calling API', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Annuler l'événement/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

    expect(screen.queryByRole('heading', { name: "Annuler l'événement ?" })).toBeNull()
    expect(mockCancelEvent).not.toHaveBeenCalled()
  })

  it('"Annuler l\'événement" confirm calls cancelEvent and navigates to my-events cancelled tab', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
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
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)
    mockCancelEvent.mockRejectedValue(new Error('fail'))

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Annuler l'événement/ }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

    await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith('error', expect.stringContaining('annuler')))
  })

  it('"Remettre en brouillon" calls restoreEvent and navigates to drafts', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)
    mockRestoreEvent.mockResolvedValue({})

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Remettre en brouillon/ }))

    await waitFor(() => expect(mockRestoreEvent).toHaveBeenCalledWith(1))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/my-events/publications?status=draft'))
  })

  it('"Remettre en brouillon" shows error toast on failure', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)
    mockRestoreEvent.mockRejectedValue(new Error('fail'))

    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Remettre en brouillon/ }))

    await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith('error', expect.stringContaining('restaurer')))
  })

  it('shows a banner warning toast when bannerUploadError is present in sessionStorage', async () => {
    sessionStorage.setItem(BANNER_UPLOAD_ERROR_KEY, "L'événement a été créé mais la bannière n'a pas pu être uploadée.")

    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(await screen.findByText("L'événement a été créé mais la bannière n'a pas pu être uploadée.")).toBeTruthy()
    expect(sessionStorage.getItem(BANNER_UPLOAD_ERROR_KEY)).toBeNull()
  })

  it('calls deleteEvent and navigates to my-events cancelled on confirm', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, error: null })
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
    mockUseEvent.mockReturnValue({ event: { ...mockEvent, status: 'CANCELLED' as const }, loading: false, error: null })
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
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockRejectedValue(new Error('not found'))

    renderPage()

    await waitFor(() => expect(mockGetUserById).toHaveBeenCalled())
    expect(screen.queryByText(/Organisé par/)).toBeNull()
  })

  it('renders event with no capacity (InfoRow without color branch)', () => {
    const eventNoCapacity = { ...mockEvent, capacity: undefined }
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: eventNoCapacity, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByRole('heading', { name: 'Conférence IA' })).toBeTruthy()
  })

  it('copies the event URL and shows a success toast when sharing', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
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
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /Partager/ }))
    await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith('error', expect.stringContaining('Copiez ce lien'), 6000))
  })

  it('shows a fallback error toast when clipboard API is unavailable', () => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true })

    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: /Partager/ }))
    expect(mockShowToast).toHaveBeenCalledWith('error', expect.stringContaining('Copiez ce lien'), 6000)
  })

  describe('SCRUM-117 — extra optional fields display', () => {
    it('does not render the extra-info card when none of the four fields are present', () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.queryByText('Informations complémentaires')).toBeNull()
    })

    it('renders websiteUrl as an external link opening in a new tab', () => {
      const withWebsite = { ...mockEvent, websiteUrl: 'https://unige.ch/event' }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: withWebsite, loading: false, error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText('Informations complémentaires')).toBeTruthy()
      const link = screen.getByRole('link', { name: 'https://unige.ch/event' }) as HTMLAnchorElement
      expect(link.getAttribute('href')).toBe('https://unige.ch/event')
      expect(link.getAttribute('target')).toBe('_blank')
      expect(link.getAttribute('rel')).toBe('noopener noreferrer')
    })

    it('renders contactEmail as a mailto link', () => {
      const withEmail = { ...mockEvent, contactEmail: 'contact@unige.ch' }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: withEmail, loading: false, error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      const link = screen.getByRole('link', { name: 'contact@unige.ch' }) as HTMLAnchorElement
      expect(link.getAttribute('href')).toBe('mailto:contact@unige.ch')
    })

    it('renders a formatted registrationDeadline when present', () => {
      const withDeadline = { ...mockEvent, registrationDeadline: '2026-04-09T18:00:00.000Z' }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: withDeadline, loading: false, error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByText(/Inscriptions jusqu'au/)).toBeTruthy()
    })

    it('renders tags as clickable chips linking to /events/search?q=<tag>', () => {
      const withTags = { ...mockEvent, tags: ['forum', 'carrière emploi'] }
      mockUseAuth.mockReturnValue({ user: mockUser })
      mockUseEvent.mockReturnValue({ event: withTags, loading: false, error: null })
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
      mockUseEvent.mockReturnValue({ event: withEmptyTags, loading: false, error: null })
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
      mockUseEvent.mockReturnValue({ event: allFields, loading: false, error: null })
      mockGetUserById.mockResolvedValue(null)

      renderPage()

      expect(screen.getByRole('link', { name: 'https://unige.ch/event' })).toBeTruthy()
      expect(screen.getByRole('link', { name: 'contact@unige.ch' })).toBeTruthy()
      expect(screen.getByText(/Inscriptions jusqu'au/)).toBeTruthy()
      expect(screen.getByRole('link', { name: 'forum' })).toBeTruthy()
    })
  })
})
