
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import EventCreatePage from '@/pages/event/EventCreatePage'
import { ToastProvider } from '@/contexts/ToastContext'
import ToastsWrapper from '@/components/utils/Toast'

vi.mock('@/services/eventApi', () => ({
  createEvent: vi.fn(),
  updateEvent: vi.fn(),
  uploadEventImage: vi.fn(),
}))

vi.mock('@/services/coOrganizerApi', () => ({
  inviteCoOrganizer: vi.fn(),
}))

vi.mock('@/services/attachmentApi', () => ({
  uploadEventAttachment: vi.fn(),
  deleteEventAttachment: vi.fn(),
}))

vi.mock('@/services/userService', () => ({
  getUserByUsername: vi.fn(),
  // SCRUM-137 autocomplete — never resolves so the dropdown stays idle ;
  // these tests drive the manual submit flow (typed username), not the picker.
  searchUsernames: vi.fn().mockResolvedValue([]),
}))

// SCRUM-137 autocomplete — PendingCoOrganizersEditor now reads useAuth() to
// build the excludeUsernames list. Provide a stable authenticated stub so
// the editor mounts without throwing.
vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({
    user: {
      id: 'caller-id',
      auth0Id: 'auth0|caller',
      email: 'caller@example.com',
      username: 'caller.handle',
      profilePublic: true,
      createdAt: '2026-05-15T10:00:00',
    },
    isAuthenticated: true,
    isAdmin: false,
    isLoading: false,
    error: null,
    login: vi.fn(),
    logout: vi.fn(),
    updateUser: vi.fn(),
  }),
}))

vi.mock('@/components/event/DraftsResumeStrip', () => ({
  default: () => null,
}))

vi.mock('@/components/utils/ImageCropper', () => ({
  default: ({ onCropComplete, onCancel, src }: { onCropComplete: (b: Blob) => void; onCancel: () => void; src: string }) => (
    <div data-testid="image-cropper-mock" data-src={src}>
      <button type="button" onClick={() => onCropComplete(new Blob(['cropped'], { type: 'image/png' }))}>
        Mock Recadrer
      </button>
      <button type="button" onClick={onCancel}>
        Mock Annuler
      </button>
    </div>
  ),
}))

let originalFileReader: typeof FileReader
function mockFileReader(result: string) {
  class MockReader {
    public onload: (() => void) | null = null
    public result: string | null = null
    readAsDataURL() {
      this.result = result
      queueMicrotask(() => this.onload?.())
    }
  }
  globalThis.FileReader = MockReader as unknown as typeof FileReader
}

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

import { createEvent, updateEvent, uploadEventImage } from '@/services/eventApi'
import { inviteCoOrganizer } from '@/services/coOrganizerApi'
import { uploadEventAttachment } from '@/services/attachmentApi'
import { getUserByUsername } from '@/services/userService'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'

const mockCreateEvent = createEvent as ReturnType<typeof vi.fn>
const mockUpdateEvent = updateEvent as ReturnType<typeof vi.fn>
const mockUploadEventImage = uploadEventImage as ReturnType<typeof vi.fn>
const mockInviteCoOrganizer = inviteCoOrganizer as ReturnType<typeof vi.fn>
const mockUploadEventAttachment = uploadEventAttachment as ReturnType<typeof vi.fn>
const mockGetUserByUsername = getUserByUsername as ReturnType<typeof vi.fn>

const createdEvent = {
  id: 42,
  title: 'Forum des associations',
  description: 'Rencontrez les associations du campus.',
  location: 'Uni Dufour',
  startDate: '2026-04-10T08:00:00.000Z',
  endDate: '2026-04-10T10:00:00.000Z',
  category: 'SOCIAL',
  faculty: null,
  creatorId: '8b24e4aa-fdea-4e04-bf56-bdb2ddb7fc11',
  status: 'PUBLISHED',
  capacity: 120,
  createdAt: '2026-03-27T09:00:00.000Z',
}

beforeEach(() => {
  vi.useRealTimers()
  originalFileReader = globalThis.FileReader
})

afterEach(() => {
  globalThis.FileReader = originalFileReader
  cleanup()
  vi.clearAllTimers()
  vi.restoreAllMocks()
  vi.resetAllMocks()
  sessionStorage.removeItem(BANNER_UPLOAD_ERROR_KEY)
  sessionStorage.removeItem('unige:event-create-draft')
})

const templateEvent = {
  id: 99,
  title: 'Conférence IA',
  description: 'Présentation des dernières avancées en IA.',
  location: 'Uni Bastions',
  startDate: '2025-01-15T09:00:00.000Z',
  endDate: '2025-01-15T11:00:00.000Z',
  category: 'CONFERENCE' as const,
  faculty: null,
  creatorId: 'abc',
  status: 'EXPIRED' as const,
  allDay: false,
  capacity: 80,
  attendingCount: 75,
  websiteUrl: 'https://conf.example.com',
  contactEmail: 'conf@unige.ch',
  tags: ['IA'],
  createdAt: '2025-01-01T00:00:00.000Z',
}

function renderPage(locationState?: Record<string, unknown>) {
  const initialEntries = locationState
    ? [{ pathname: '/events/new', state: locationState }]
    : ['/events/new']
  return render(
    <ToastProvider>
      <ToastsWrapper />
      <MemoryRouter initialEntries={initialEntries}>
        <EventCreatePage />
      </MemoryRouter>
    </ToastProvider>,
  )
}

function fillRequiredFields() {
  fireEvent.change(screen.getByLabelText(/Titre/i), { target: { value: createdEvent.title } })
  fireEvent.change(screen.getByLabelText(/Lieu/i), { target: { value: createdEvent.location } })
  fireEvent.change(screen.getByLabelText(/Début/i, { selector: 'input' }), { target: { value: '2099-04-10' } })
  fireEvent.change(screen.getByLabelText('Heure de début'), { target: { value: '10' } })
  fireEvent.change(screen.getByLabelText('Minute de début'), { target: { value: '00' } })
  fireEvent.change(screen.getByLabelText(/Fin/i, { selector: 'input' }), { target: { value: '2099-04-10' } })
  fireEvent.change(screen.getByLabelText('Heure de fin'), { target: { value: '12' } })
  fireEvent.change(screen.getByLabelText('Minute de fin'), { target: { value: '00' } })
  fireEvent.change(screen.getByLabelText(/Catégorie/i), { target: { value: 'SOCIAL' } })
}

describe('CreateEventPage', () => {
  it('shows required field validation errors', async () => {
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    expect(await screen.findByText('Le titre est requis.')).toBeTruthy()
    expect(screen.getByText('Le lieu est requis.')).toBeTruthy()
    expect(screen.getByText('La date de début est requise.')).toBeTruthy()
    expect(screen.getByText('La date de fin est requise.')).toBeTruthy()
    expect(screen.getByText('La catégorie est requise.')).toBeTruthy()
  })

  it('blocks submission when the start date is in the past', async () => {
    renderPage()

    fireEvent.change(screen.getByLabelText(/Titre/i), { target: { value: 'Forum' } })
    fireEvent.change(screen.getByLabelText(/Lieu/i), { target: { value: 'Uni Dufour' } })
    fireEvent.change(screen.getByLabelText(/Début/i, { selector: 'input' }), { target: { value: '2000-04-10' } })
    fireEvent.change(screen.getByLabelText('Heure de début'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Minute de début'), { target: { value: '00' } })
    fireEvent.change(screen.getByLabelText(/Fin/i, { selector: 'input' }), { target: { value: '2099-04-10' } })
    fireEvent.change(screen.getByLabelText('Heure de fin'), { target: { value: '12' } })
    fireEvent.change(screen.getByLabelText('Minute de fin'), { target: { value: '00' } })
    fireEvent.change(screen.getByLabelText(/Catégorie/i), { target: { value: 'SOCIAL' } })

    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    expect(await screen.findByText('La date de début doit être postérieure à la date et à l\'heure actuelles.')).toBeTruthy()
    expect(mockCreateEvent).not.toHaveBeenCalled()
  })

  it('validates endDate after startDate', async () => {
    renderPage()

    fireEvent.change(screen.getByLabelText(/Titre/i), { target: { value: 'Forum' } })
    fireEvent.change(screen.getByLabelText(/Lieu/i), { target: { value: 'Uni Dufour' } })
    fireEvent.change(screen.getByLabelText(/Début/i, { selector: 'input' }), { target: { value: '2099-04-10' } })
    fireEvent.change(screen.getByLabelText('Heure de début'), { target: { value: '10' } })
    fireEvent.change(screen.getByLabelText('Minute de début'), { target: { value: '00' } })
    fireEvent.change(screen.getByLabelText(/Fin/i, { selector: 'input' }), { target: { value: '2099-04-10' } })
    fireEvent.change(screen.getByLabelText('Heure de fin'), { target: { value: '09' } })
    fireEvent.change(screen.getByLabelText('Minute de fin'), { target: { value: '00' } })
    fireEvent.change(screen.getByLabelText(/Catégorie/i), { target: { value: 'SOCIAL' } })

    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    expect(await screen.findByText('La date de fin doit être postérieure à la date de début.')).toBeTruthy()
  })

  it('creates an event, uploads the banner, and redirects to the detail page', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockCreateEvent.mockResolvedValue(createdEvent)
    mockUploadEventImage.mockResolvedValue({ ...createdEvent, bannerUrl: 'https://example.com/banner.png' })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')

    renderPage()

    fillRequiredFields()
    fireEvent.change(screen.getByLabelText(/Description/i), { target: { value: createdEvent.description } })
    fireEvent.change(screen.getByLabelText(/Capacité/i), { target: { value: '120' } })

    const fileInput = document.querySelector<HTMLInputElement>('#event-banner')
    if (!fileInput) {
      throw new Error('Missing banner input')
    }
    const file = new File(['img'], 'banner.png', { type: 'image/png' })
    await act(async () => {
      fireEvent.change(fileInput, { target: { files: [file] } })
      await new Promise((r) => queueMicrotask(r as () => void))
    })
    fireEvent.click(screen.getByText('Mock Recadrer'))

    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    expect(await screen.findByText('Événement créé avec succès.')).toBeTruthy()
    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({
      title: createdEvent.title,
      location: createdEvent.location,
      category: createdEvent.category,
      capacity: 120,
      status: 'PUBLISHED',
    }))
    const uploaded = mockUploadEventImage.mock.calls[0]
    expect(uploaded[0]).toBe(42)
    expect(uploaded[1]).toBeInstanceOf(File)
    expect((uploaded[1] as File).name).toBe('banner.png')

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/42'), { timeout: 2000 })
  })

  it('sends the published status during creation without a second update call', async () => {
    mockCreateEvent.mockResolvedValue({ ...createdEvent, status: 'PUBLISHED' })

    renderPage()

    fillRequiredFields()

    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    await screen.findByText('Événement créé avec succès.')
    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({ status: 'PUBLISHED' }))
    expect(mockUpdateEvent).not.toHaveBeenCalled()
  })

  it('shows localized French validation details when the backend returns a technical error', async () => {
    mockCreateEvent.mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          message: 'post validation error',
          details: [{ field: 'title', message: 'must not be blank' }],
        },
      },
    })

    renderPage()

    fillRequiredFields()
    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    expect(await screen.findByText('Le titre est requis.')).toBeTruthy()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('shows an error toast when creation fails', async () => {
    mockCreateEvent.mockRejectedValue(new Error('boom'))

    renderPage()

    fillRequiredFields()
    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    expect(await screen.findByText('La création de l\'événement a échoué. Veuillez réessayer.')).toBeTruthy()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('redirects to the landing page after saving as draft', async () => {
    mockCreateEvent.mockResolvedValue({ ...createdEvent, status: 'DRAFT' })

    renderPage()

    fillRequiredFields()

    fireEvent.click(screen.getByRole('button', { name: 'Sauvegarder en Brouillon' }))

    expect(await screen.findByText('Brouillon enregistré.')).toBeTruthy()
    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({ status: 'DRAFT' }))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'))
    expect(mockNavigate).not.toHaveBeenCalledWith('/events/42')
  })

  it('navigates back home when cancel is clicked', () => {
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

    expect(mockNavigate).toHaveBeenCalledWith('/')
  })

  it('clears the persisted create-form draft from sessionStorage when cancel is clicked', () => {
    sessionStorage.setItem('unige:event-create-draft', JSON.stringify({ title: 'À effacer' }))
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

    expect(sessionStorage.getItem('unige:event-create-draft')).toBeNull()
    expect(mockNavigate).toHaveBeenCalledWith('/')
  })

  it('stores the banner error in sessionStorage when image upload fails after creation', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockCreateEvent.mockResolvedValue(createdEvent)
    mockUploadEventImage.mockRejectedValue(new Error('upload failed'))
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')
    const setItemSpy = vi.spyOn(sessionStorage, 'setItem')

    renderPage()

    fillRequiredFields()

    const fileInput = document.querySelector<HTMLInputElement>('#event-banner')
    if (!fileInput) throw new Error('Missing banner input')
    await act(async () => {
      fireEvent.change(fileInput, { target: { files: [new File(['img'], 'banner.png', { type: 'image/png' })] } })
      await new Promise((r) => queueMicrotask(r as () => void))
    })
    fireEvent.click(screen.getByText('Mock Recadrer'))

    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    await waitFor(() => expect(setItemSpy).toHaveBeenCalledWith(
      BANNER_UPLOAD_ERROR_KEY,
      "L'événement a été créé mais la bannière n'a pas pu être uploadée.",
    ))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/42'), { timeout: 2000 })
  })

  it('cleans up pending timers when the page unmounts after success', async () => {
    const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout')
    mockCreateEvent.mockResolvedValue(createdEvent)

    const { unmount } = renderPage()

    fillRequiredFields()
    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    expect(await screen.findByText('Événement créé avec succès.')).toBeTruthy()

    unmount()

    expect(clearTimeoutSpy.mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  describe('pending co-organizers (SCRUM-137 from create page)', () => {
    it('stages a co-organizer by username and dispatches the invite after the event is created', async () => {
      mockCreateEvent.mockResolvedValue(createdEvent)
      mockGetUserByUsername.mockResolvedValue({
        id: 'aa11bb22-cc33-dd44-ee55-ff6677889900',
        username: 'alice.martin',
        displayName: 'Alice',
        avatarUrl: null,
        email: 'alice@example.com',
        auth0Id: 'auth0|alice',
        profilePublic: true,
        createdAt: '2026-05-14T10:00:00',
      })
      mockInviteCoOrganizer.mockResolvedValue({
        id: 1,
        userId: 'aa11bb22-cc33-dd44-ee55-ff6677889900',
        displayName: 'Alice',
        avatarUrl: null,
        username: 'alice.martin',
        status: 'PENDING',
        invitedAt: '2026-05-14T10:00:00',
      })

      renderPage()

      fillRequiredFields()

      fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'alice.martin' } })
      fireEvent.click(screen.getByRole('button', { name: /^ajouter$/i }))

      await waitFor(() => expect(screen.getByText('@alice.martin')).toBeTruthy())

      fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

      await waitFor(() => expect(mockCreateEvent).toHaveBeenCalled())
      await waitFor(() =>
        expect(mockInviteCoOrganizer).toHaveBeenCalledWith(42, 'aa11bb22-cc33-dd44-ee55-ff6677889900'),
      )
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/42'))
    })

    it('dispatches pending invitations even when the event is saved as a draft', async () => {
      // Copilot review: a DRAFT submit used to early-return before the invite
      // dispatch, silently dropping the staged list. The fix moves the
      // dispatch before the draft branch.
      mockCreateEvent.mockResolvedValue({ ...createdEvent, status: 'DRAFT' })
      mockGetUserByUsername.mockResolvedValue({
        id: 'aa11bb22-cc33-dd44-ee55-ff6677889900',
        username: 'alice.martin',
        displayName: 'Alice',
        avatarUrl: null,
        email: 'alice@example.com',
        auth0Id: 'auth0|alice',
        profilePublic: true,
        createdAt: '2026-05-14T10:00:00',
      })
      mockInviteCoOrganizer.mockResolvedValue({
        id: 1,
        userId: 'aa11bb22-cc33-dd44-ee55-ff6677889900',
        displayName: 'Alice',
        avatarUrl: null,
        username: 'alice.martin',
        status: 'PENDING',
        invitedAt: '2026-05-14T10:00:00',
      })

      renderPage()

      fillRequiredFields()
      fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'alice.martin' } })
      fireEvent.click(screen.getByRole('button', { name: /^ajouter$/i }))
      await waitFor(() => expect(screen.getByText('@alice.martin')).toBeTruthy())

      fireEvent.click(screen.getByRole('button', { name: 'Sauvegarder en Brouillon' }))

      await waitFor(() =>
        expect(mockInviteCoOrganizer).toHaveBeenCalledWith(42, 'aa11bb22-cc33-dd44-ee55-ff6677889900'),
      )
      expect(await screen.findByText('Brouillon enregistré.')).toBeTruthy()
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'))
    })

    it('still navigates and toasts the failure when one invitation fails', async () => {
      mockCreateEvent.mockResolvedValue(createdEvent)
      mockGetUserByUsername.mockResolvedValue({
        id: 'aa11bb22-cc33-dd44-ee55-ff6677889900',
        username: 'alice.martin',
        displayName: 'Alice',
        avatarUrl: null,
        email: 'alice@example.com',
        auth0Id: 'auth0|alice',
        profilePublic: true,
        createdAt: '2026-05-14T10:00:00',
      })
      mockInviteCoOrganizer.mockRejectedValue(new Error('409 already invited'))

      renderPage()

      fillRequiredFields()
      fireEvent.change(screen.getByLabelText(/username/i), { target: { value: 'alice.martin' } })
      fireEvent.click(screen.getByRole('button', { name: /^ajouter$/i }))
      await waitFor(() => expect(screen.getByText('@alice.martin')).toBeTruthy())

      fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

      await waitFor(() =>
        expect(screen.getByText(/Impossible d'inviter Alice/i)).toBeTruthy(),
      )
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/42'))
    })
  })

  describe('template pre-fill (location.state.template)', () => {
    it('shows the info banner with the template title', () => {
      renderPage({ template: templateEvent })

      expect(screen.getByText(/Pré-rempli depuis l'événement/)).toBeTruthy()
      expect(screen.getByText(/"Conférence IA"/)).toBeTruthy()
      expect(screen.getByRole('button', { name: /Effacer le template/i })).toBeTruthy()
    })

    it('pre-fills form fields from the template but leaves dates empty', () => {
      renderPage({ template: templateEvent })

      expect((screen.getByLabelText(/Titre/i) as HTMLInputElement).value).toBe('Conférence IA')
      expect((screen.getByLabelText(/Lieu/i) as HTMLInputElement).value).toBe('Uni Bastions')
      // Dates must be empty
      expect((screen.getByLabelText(/Début/i, { selector: 'input' }) as HTMLInputElement).value).toBe('')
      expect((screen.getByLabelText(/Fin/i, { selector: 'input' }) as HTMLInputElement).value).toBe('')
    })

    it('"Effacer le template" hides the banner and resets the form', async () => {
      renderPage({ template: templateEvent })

      expect(screen.getByText(/Pré-rempli depuis l'événement/)).toBeTruthy()
      expect((screen.getByLabelText(/Titre/i) as HTMLInputElement).value).toBe('Conférence IA')

      fireEvent.click(screen.getByRole('button', { name: /Effacer le template/i }))

      expect(screen.queryByText(/Pré-rempli depuis l'événement/)).toBeFalsy()
      expect((screen.getByLabelText(/Titre/i) as HTMLInputElement).value).toBe('')
    })

    it('"Effacer le template" calls navigate with replace to clear the router state', () => {
      renderPage({ template: templateEvent })

      fireEvent.click(screen.getByRole('button', { name: /Effacer le template/i }))

      expect(mockNavigate).toHaveBeenCalledWith('/events/new', { replace: true })
    })

    it('does not show the banner when no template is in location state', () => {
      renderPage()

      expect(screen.queryByText(/Pré-rempli depuis l'événement/)).toBeFalsy()
      expect(screen.queryByRole('button', { name: /Effacer le template/i })).toBeFalsy()
    })
  })

  describe('pending attachments (SCRUM-149 from create page)', () => {
    function pickAttachments(files: File[]) {
      const input = document.querySelector<HTMLInputElement>('#pending-event-attachments-input')
      if (!input) throw new Error('missing pending attachments input')
      fireEvent.change(input, { target: { files } })
    }

    it('stages picked files locally without calling the upload API', () => {
      renderPage()
      pickAttachments([
        new File(['x'], 'doc.pdf', { type: 'application/pdf' }),
        new File(['y'], 'photo.png', { type: 'image/png' }),
      ])
      expect(screen.getByText('doc.pdf')).toBeTruthy()
      expect(screen.getByText('photo.png')).toBeTruthy()
      expect(mockUploadEventAttachment).not.toHaveBeenCalled()
    })

    it('uploads staged files after createEvent succeeds and navigates to detail', async () => {
      mockCreateEvent.mockResolvedValue(createdEvent)
      mockUploadEventAttachment.mockResolvedValue({
        id: 1, fileName: 'doc.pdf', fileUrl: 'https://s3/doc.pdf',
        fileSize: 1, mimeType: 'application/pdf',
        uploadedById: 'u', uploadedAt: '2026-05-18T10:00:00Z',
      })

      renderPage()
      fillRequiredFields()
      pickAttachments([new File(['x'], 'doc.pdf', { type: 'application/pdf' })])

      fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

      await waitFor(() => expect(mockCreateEvent).toHaveBeenCalledTimes(1))
      await waitFor(() => expect(mockUploadEventAttachment).toHaveBeenCalledTimes(1))
      const call = mockUploadEventAttachment.mock.calls[0]
      expect(call[0]).toBe(42)
      expect((call[1] as File).name).toBe('doc.pdf')
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/42'))
    })

    it('skips client-rejected files when flushing after create', async () => {
      mockCreateEvent.mockResolvedValue(createdEvent)

      renderPage()
      fillRequiredFields()
      pickAttachments([
        new File(['x'], 'logo.svg', { type: 'image/svg+xml' }), // bad mime
        new File(['y'], 'ok.pdf', { type: 'application/pdf' }),
      ])
      mockUploadEventAttachment.mockResolvedValue({
        id: 7, fileName: 'ok.pdf', fileUrl: 'https://s3/ok.pdf',
        fileSize: 1, mimeType: 'application/pdf',
        uploadedById: 'u', uploadedAt: '2026-05-18T10:00:00Z',
      })

      fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

      await waitFor(() => expect(mockUploadEventAttachment).toHaveBeenCalledTimes(1))
      expect((mockUploadEventAttachment.mock.calls[0][1] as File).name).toBe('ok.pdf')
    })

    it('surfaces a per-file toast when an attachment upload fails (but still navigates)', async () => {
      mockCreateEvent.mockResolvedValue(createdEvent)
      mockUploadEventAttachment.mockRejectedValue(new Error('boom'))

      renderPage()
      fillRequiredFields()
      pickAttachments([new File(['x'], 'broken.pdf', { type: 'application/pdf' })])

      await act(async () => {
        fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))
      })

      expect(await screen.findByText('Impossible d\'uploader « broken.pdf ».')).toBeTruthy()
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/42'))
    })

    it('removes a staged file when the × is clicked', () => {
      renderPage()
      pickAttachments([new File(['x'], 'doc.pdf', { type: 'application/pdf' })])
      expect(screen.getByText('doc.pdf')).toBeTruthy()
      fireEvent.click(screen.getByRole('button', { name: 'Retirer doc.pdf' }))
      expect(screen.queryByText('doc.pdf')).toBeNull()
    })
  })
})
