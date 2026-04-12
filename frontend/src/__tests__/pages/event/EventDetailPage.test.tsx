// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import EventDetailPage from '@/pages/event/EventDetailPage'

vi.mock('@/hooks/useAuth', () => ({ useAuth: vi.fn() }))
vi.mock('@/hooks/useEvent', () => ({ useEvent: vi.fn() }))
vi.mock('@/services/eventApi', () => ({ deleteEvent: vi.fn() }))
vi.mock('@/services/userService', () => ({ getUserById: vi.fn() }))

import { useAuth } from '@/hooks/useAuth'
import { useEvent } from '@/hooks/useEvent'
import { deleteEvent } from '@/services/eventApi'
import { getUserById } from '@/services/userService'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockUseEvent = useEvent as ReturnType<typeof vi.fn>
const mockDeleteEvent = deleteEvent as ReturnType<typeof vi.fn>
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

  it('shows organizer-only actions with the final edit route', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.getByRole('link', { name: "Modifier l'événement" }).getAttribute('href')).toBe('/events/1/edit')
    expect(screen.getByRole('button', { name: "Supprimer l'événement" })).toBeTruthy()
  })

  it('hides organizer actions for another user', () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, id: 'other-user' } })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    expect(screen.queryByRole('link', { name: "Modifier l'événement" })).toBeNull()
    expect(screen.queryByRole('button', { name: "Supprimer l'événement" })).toBeNull()
  })

  it('opens and closes the delete confirmation modal', () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: "Supprimer l'événement" }))
    expect(screen.getByRole('heading', { name: "Supprimer l'événement ?" })).toBeTruthy()

    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))
    expect(screen.queryByRole('heading', { name: "Supprimer l'événement ?" })).toBeNull()
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

  it('calls deleteEvent and navigates home on confirm', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)
    mockDeleteEvent.mockResolvedValue(undefined)

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: "Supprimer l'événement" }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

    await waitFor(() => expect(mockDeleteEvent).toHaveBeenCalledWith(1))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'))
  })

  it('hides confirm modal and re-enables button when deleteEvent fails', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockResolvedValue(null)
    mockDeleteEvent.mockRejectedValue(new Error('network error'))

    renderPage()

    fireEvent.click(screen.getByRole('button', { name: "Supprimer l'événement" }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

    await waitFor(() => expect(screen.queryByRole('heading', { name: "Supprimer l'événement ?" })).toBeNull())
    expect(screen.getByRole('button', { name: "Supprimer l'événement" })).toBeTruthy()
  })

  it('sets organizer to null when getUserById rejects', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockUseEvent.mockReturnValue({ event: mockEvent, loading: false, error: null })
    mockGetUserById.mockRejectedValue(new Error('not found'))

    renderPage()

    await waitFor(() => expect(mockGetUserById).toHaveBeenCalled())
    expect(screen.queryByText(/Organisé par/)).toBeNull()
  })
})
