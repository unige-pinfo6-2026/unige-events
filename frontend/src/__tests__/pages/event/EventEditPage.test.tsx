
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import EventEditPage from '@/pages/event/EventEditPage'
import { ToastProvider } from '@/contexts/ToastContext'
import ToastsWrapper from '@/components/utils/Toast'
import { ThemeProvider } from '@/contexts/ThemeContext'

vi.mock('@/services/eventApi', () => ({
  createEvent: vi.fn(),
  getById: vi.fn(),
  updateEvent: vi.fn(),
  uploadEventImage: vi.fn(),
  deleteEvent: vi.fn(),
}))

vi.mock('@/hooks/useAuth', () => ({ useAuth: vi.fn() }))

vi.mock('@/services/attachmentApi', () => ({
  uploadEventAttachment: vi.fn(),
  deleteEventAttachment: vi.fn(),
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

import { deleteEvent, getById, updateEvent, uploadEventImage } from '@/services/eventApi'
import { uploadEventAttachment } from '@/services/attachmentApi'
import { useAuth } from '@/hooks/useAuth'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'

const mockGetById = getById as ReturnType<typeof vi.fn>
const mockUpdateEvent = updateEvent as ReturnType<typeof vi.fn>
const mockUploadEventImage = uploadEventImage as ReturnType<typeof vi.fn>
const mockDeleteEvent = deleteEvent as ReturnType<typeof vi.fn>
const mockUploadEventAttachment = uploadEventAttachment as ReturnType<typeof vi.fn>
const mockUseAuth = useAuth as ReturnType<typeof vi.fn>

const existingEvent = {
  id: 42,
  title: 'Forum des associations',
  description: 'Rencontrez les associations du campus.',
  location: 'Uni Dufour',
  startDate: '2099-04-10T08:00:00.000Z',
  endDate: '2099-04-10T10:00:00.000Z',
  category: 'SOCIAL',
  faculty: null,
  creatorId: '8b24e4aa-fdea-4e04-bf56-bdb2ddb7fc11',
  status: 'PUBLISHED',
  capacity: 120,
  createdAt: '2026-03-27T09:00:00.000Z',
  bannerUrl: 'https://example.com/current-banner.png',
}

const draftEvent = { ...existingEvent, status: 'DRAFT' }

beforeEach(() => {
  vi.useRealTimers()
  originalFileReader = globalThis.FileReader
  // Default to "creator caller". Individual tests can override before render
  // to simulate a co-organizer (different user id).
  mockUseAuth.mockReturnValue({ user: { id: existingEvent.creatorId }, isAdmin: false })
})

afterEach(() => {
  globalThis.FileReader = originalFileReader
  cleanup()
  vi.clearAllTimers()
  vi.restoreAllMocks()
  vi.resetAllMocks()
  sessionStorage.removeItem(BANNER_UPLOAD_ERROR_KEY)
})

function renderPage(path = '/events/42/edit') {
  return render(
    <ToastProvider>
      <ToastsWrapper />
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path='/events/:id/edit' element={<EventEditPage />} />
        </Routes>
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('EditEventPage', () => {
  it('prefills the form with event data', async () => {
    mockGetById.mockResolvedValue(existingEvent)

    renderPage()

    await waitFor(() => {
      expect(screen.getByDisplayValue(existingEvent.title)).toBeTruthy()
      expect(screen.getByDisplayValue(existingEvent.location)).toBeTruthy()
    }, { timeout: 10000 })
  })

  it('updates an event and redirects to its detail page', async () => {
    mockGetById.mockResolvedValue(existingEvent)
    mockUpdateEvent.mockResolvedValue({ ...existingEvent, title: 'Forum 2026', status: 'PUBLISHED' })

    renderPage()
    await waitFor(() => {
      expect(screen.getByDisplayValue(existingEvent.title)).toBeTruthy()
    }, { timeout: 10000 })
    fireEvent.change(screen.getByLabelText(/Titre/i), { target: { value: 'Forum 2026' } })
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

    expect(await screen.findByText('Événement mis à jour avec succès.')).toBeTruthy()
    expect(mockUpdateEvent).toHaveBeenCalledWith(42, expect.objectContaining({
      title: 'Forum 2026',
      location: existingEvent.location,
      bannerUrl: 'https://example.com/current-banner.png',
      status: 'PUBLISHED',
    }))

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/42'), { timeout: 2000 })
  })

  it('shows localized French validation details when an update request fails', async () => {
    mockGetById.mockResolvedValue(existingEvent)
    mockUpdateEvent.mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          message: 'put validation error',
          details: [{ field: 'startDate', message: 'must be in the future' }],
        },
      },
    })

    renderPage()

    await waitFor(() => {
      expect(screen.getByDisplayValue(existingEvent.title)).toBeTruthy()
    }, { timeout: 10000 })
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

    expect(await screen.findByText('La date de début doit être dans le futur.')).toBeTruthy()
  })

  it('uploads a new banner during update', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockGetById.mockResolvedValue(existingEvent)
    mockUpdateEvent.mockResolvedValue(existingEvent)
    mockUploadEventImage.mockResolvedValue({ ...existingEvent, bannerUrl: 'https://example.com/banner.png' })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')

    renderPage()

    await waitFor(() => {
      expect(screen.getByDisplayValue(existingEvent.title)).toBeTruthy()
    }, { timeout: 10000 })
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

    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

    await screen.findByText('Événement mis à jour avec succès.')
    const uploaded = mockUploadEventImage.mock.calls[0]
    expect(uploaded[0]).toBe(42)
    expect(uploaded[1]).toBeInstanceOf(File)
    expect((uploaded[1] as File).name).toBe('banner.png')
  })

  it('shows an invalid id message for malformed routes', async () => {
    renderPage('/events/abc/edit')

    expect(await screen.findByText('Identifiant d\'événement invalide.')).toBeTruthy()
  })

  it('shows a localized load error when the event cannot be loaded', async () => {
    mockGetById.mockRejectedValue(new Error('boom'))

    renderPage()

    expect(await screen.findByText('Impossible de charger cet événement.')).toBeTruthy()
  })

  it('navigates back to the detail page when cancel is clicked', async () => {
    mockGetById.mockResolvedValue(existingEvent)

    renderPage()

    await waitFor(() => {
      expect(screen.getByDisplayValue(existingEvent.title)).toBeTruthy()
    }, { timeout: 10000 })
    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

    expect(mockNavigate).toHaveBeenCalledWith('/events/42')
  })

  it('stores the banner error in sessionStorage when image upload fails after update', async () => {
    mockFileReader('data:image/png;base64,abc')
    mockGetById.mockResolvedValue(existingEvent)
    mockUpdateEvent.mockResolvedValue(existingEvent)
    mockUploadEventImage.mockRejectedValue(new Error('upload failed'))
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem')

    renderPage()

    await waitFor(() => {
      expect(screen.getByDisplayValue(existingEvent.title)).toBeTruthy()
    }, { timeout: 10000 })

    const fileInput = document.querySelector<HTMLInputElement>('#event-banner')
    if (!fileInput) throw new Error('Missing banner input')
    await act(async () => {
      fireEvent.change(fileInput, { target: { files: [new File(['img'], 'banner.png', { type: 'image/png' })] } })
      await new Promise((r) => queueMicrotask(r as () => void))
    })
    fireEvent.click(screen.getByText('Mock Recadrer'))

    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

    await waitFor(() => expect(setItemSpy).toHaveBeenCalledWith(
      BANNER_UPLOAD_ERROR_KEY,
      "L'événement a été créé mais la bannière n'a pas pu être uploadée.",
    ))
    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/42'), { timeout: 2000 })
  })

  it('cleans up pending timers when the page unmounts after success', async () => {
    const clearTimeoutSpy = vi.spyOn(globalThis, 'clearTimeout')
    mockGetById.mockResolvedValue(existingEvent)
    mockUpdateEvent.mockResolvedValue(existingEvent)

    const { unmount } = renderPage()

    await waitFor(() => {
      expect(screen.getByDisplayValue(existingEvent.title)).toBeTruthy()
    }, { timeout: 10000 })
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))
    expect(await screen.findByText('Événement mis à jour avec succès.')).toBeTruthy()

    unmount()

    expect(clearTimeoutSpy.mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  describe('draft mode (resuming a draft)', () => {
    it('renders the draft heading, "Créer l\'événement" main button and "Enregistrer" secondary button', async () => {
      mockGetById.mockResolvedValue(draftEvent)

      renderPage()

      await screen.findByDisplayValue(draftEvent.title, {}, { timeout: 10000 })
      expect(screen.getByRole('button', { name: "Créer l'événement" })).toBeTruthy()
      expect(screen.getByRole('button', { name: 'Enregistrer' })).toBeTruthy()
      expect(screen.queryByRole('button', { name: 'Sauvegarder en Brouillon' })).toBeNull()
    })

    it('publishes the draft when the main submit button is clicked', async () => {
      mockGetById.mockResolvedValue(draftEvent)
      mockUpdateEvent.mockResolvedValue({ ...draftEvent, status: 'PUBLISHED' })

      renderPage()
      await screen.findByDisplayValue(draftEvent.title, {}, { timeout: 10000 })
      fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

      expect(await screen.findByText('Événement créé avec succès.')).toBeTruthy()
      expect(mockUpdateEvent).toHaveBeenCalledWith(42, expect.objectContaining({ status: 'PUBLISHED' }))
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/42'))
    })

    it('redirects to the landing page when re-saving as draft from the resume flow', async () => {
      mockGetById.mockResolvedValue(draftEvent)
      mockUpdateEvent.mockResolvedValue({ ...draftEvent, status: 'DRAFT' })

      renderPage()
      await screen.findByDisplayValue(draftEvent.title, {}, { timeout: 10000 })
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

      expect(await screen.findByText('Brouillon enregistré.')).toBeTruthy()
      expect(mockUpdateEvent).toHaveBeenCalledWith(42, expect.objectContaining({ status: 'DRAFT' }))
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'))
    })

    it('keeps the main "Créer l\'événement" button label unchanged while saving as draft', async () => {
      mockGetById.mockResolvedValue(draftEvent)
      let resolveUpdate: (event: typeof draftEvent) => void = () => {}
      mockUpdateEvent.mockReturnValue(new Promise(r => { resolveUpdate = r }))

      renderPage()
      await screen.findByDisplayValue(draftEvent.title, {}, { timeout: 10000 })
      fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

      // The main button must not flip to "Enregistrement..." while the draft save is
      // in flight — that label is reserved for the publish flow. It IS disabled though,
      // because the three mutations (publish / draft save / delete) are mutually
      // exclusive to prevent concurrent calls on the same event.
      const mainButton = screen.getByRole('button', { name: "Créer l'événement" }) as HTMLButtonElement
      expect(mainButton).toBeTruthy()
      expect(mainButton.disabled).toBe(true)
      expect(mainButton.textContent).not.toContain('Enregistrement')

      resolveUpdate({ ...draftEvent, status: 'DRAFT' })
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'))
    })

    it('does not expose a form-level "Annuler" button in draft mode', async () => {
      mockGetById.mockResolvedValue(draftEvent)

      renderPage()
      await screen.findByDisplayValue(draftEvent.title, {}, { timeout: 10000 })
      // In draft mode the form-level Annuler button is dropped — "Enregistrer" (save draft)
      // covers the "finish later" intent. The delete-confirmation modal still has its own
      // Annuler, but it's only rendered once the delete flow is triggered.
      expect(screen.queryByRole('button', { name: 'Annuler' })).toBeNull()
    })

    it('exposes a "Supprimer le brouillon" button in draft mode', async () => {
      mockGetById.mockResolvedValue(draftEvent)

      renderPage()
      await screen.findByDisplayValue(draftEvent.title, {}, { timeout: 10000 })
      expect(screen.getByRole('button', { name: 'Supprimer le brouillon' })).toBeTruthy()
    })

    it('does not expose a delete button in the standard edit mode (published event)', async () => {
      mockGetById.mockResolvedValue(existingEvent)

      renderPage()
      await screen.findByDisplayValue(existingEvent.title, {}, { timeout: 10000 })
      expect(screen.queryByRole('button', { name: /Supprimer le brouillon/ })).toBeNull()
    })

    it('opens the confirmation modal, calls deleteEvent, and navigates to / on success', async () => {
      mockGetById.mockResolvedValue(draftEvent)
      mockDeleteEvent.mockResolvedValue(undefined)

      renderPage()
      await screen.findByDisplayValue(draftEvent.title, {}, { timeout: 10000 })
      fireEvent.click(screen.getByRole('button', { name: 'Supprimer le brouillon' }))

      // Modal is now shown with a "Confirmer" button.
      fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

      expect(await screen.findByText('Brouillon supprimé.')).toBeTruthy()
      expect(mockDeleteEvent).toHaveBeenCalledWith(42)
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'))
    })

    it('closes the modal without deleting when the user cancels the confirmation', async () => {
      mockGetById.mockResolvedValue(draftEvent)

      renderPage()
      await screen.findByDisplayValue(draftEvent.title, {}, { timeout: 10000 })
      fireEvent.click(screen.getByRole('button', { name: 'Supprimer le brouillon' }))

      // Click the modal's own "Annuler" button. The form-level Annuler is dropped in
      // draft mode, so this is the only Annuler on screen once the modal opens.
      fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

      expect(mockDeleteEvent).not.toHaveBeenCalled()
      expect(mockNavigate).not.toHaveBeenCalled()
      expect(screen.queryByText('Supprimer le brouillon ?')).toBeNull()
    })

    it('shows an error toast and stays on the page when deletion fails', async () => {
      mockGetById.mockResolvedValue(draftEvent)
      mockDeleteEvent.mockRejectedValue(new Error('network boom'))

      renderPage()
      await screen.findByDisplayValue(draftEvent.title, {}, { timeout: 10000 })
      fireEvent.click(screen.getByRole('button', { name: 'Supprimer le brouillon' }))
      fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

      expect(await screen.findByText('Impossible de supprimer ce brouillon.')).toBeTruthy()
      expect(mockNavigate).not.toHaveBeenCalled()
    })

    it('keeps the main "Créer l\'événement" button label unchanged while deleting', async () => {
      mockGetById.mockResolvedValue(draftEvent)
      let resolveDelete: () => void = () => {}
      mockDeleteEvent.mockReturnValue(new Promise<void>(r => { resolveDelete = r }))

      renderPage()
      await screen.findByDisplayValue(draftEvent.title, {}, { timeout: 10000 })
      fireEvent.click(screen.getByRole('button', { name: 'Supprimer le brouillon' }))
      fireEvent.click(screen.getByRole('button', { name: 'Confirmer' }))

      // Same mutex: the main button is disabled while delete is in flight to block
      // a "publish on top of delete" race, but the label must not flip to
      // "Enregistrement..." — that belongs to the publish flow only.
      const mainButton = screen.getByRole('button', { name: "Créer l'événement" }) as HTMLButtonElement
      expect(mainButton.disabled).toBe(true)
      expect(mainButton.textContent).not.toContain('Enregistrement')

      resolveDelete()
      await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/'))
    })
  })

  it('shows invalid id when id param is undefined (no route match)', () => {
    render(
      <ToastProvider>
        <ToastsWrapper />
        <MemoryRouter>
          <EventEditPage />
        </MemoryRouter>
      </ToastProvider>,
    )
    expect(screen.getByText("Identifiant d'événement invalide.")).toBeTruthy()
  })

  it('shows event not found when getById returns null', async () => {
    mockGetById.mockResolvedValue(null)
    renderPage()
    expect(await screen.findByText('Événement introuvable.')).toBeTruthy()
  })

  it('does not update state after unmount (cancelled cleanup)', async () => {
    let resolveGet!: (v: typeof existingEvent) => void
    mockGetById.mockReturnValue(new Promise((r) => { resolveGet = r as typeof resolveGet }))

    const { unmount } = renderPage()
    unmount()

    await new Promise<void>((r) => {
      resolveGet(existingEvent)
      setTimeout(r, 0)
    })
    // No crash = cancelled guard worked
  })

  describe('residual conditional branches', () => {
    it('renders the skeleton with the light-theme colour token', () => {
      // ThemeProvider seeds from localStorage; 'light' drives the non-dark
      // branch of skeletonColor. getById never resolves → stays on the skeleton.
      localStorage.setItem('theme', 'light')
      mockGetById.mockReturnValue(new Promise(() => {}))

      render(
        <ThemeProvider>
          <ToastProvider>
            <ToastsWrapper />
            <MemoryRouter initialEntries={['/events/42/edit']}>
              <Routes>
                <Route path='/events/:id/edit' element={<EventEditPage />} />
              </Routes>
            </MemoryRouter>
          </ToastProvider>
        </ThemeProvider>,
      )

      expect(document.querySelector('[data-boneyard="event-edit"]')).toBeTruthy()
      localStorage.removeItem('theme')
    })

    it('treats a null auth user as a null callerId (still loads the event)', async () => {
      // user === null → callerId resolves through the `?? null` fallback. The
      // page still loads (the backend authorises via the route), it just never
      // flags the viewer as creator.
      mockUseAuth.mockReturnValue({ user: null, isAdmin: false })
      mockGetById.mockResolvedValue(existingEvent)

      renderPage()

      await waitFor(() => expect(screen.getByDisplayValue(existingEvent.title)).toBeTruthy(), { timeout: 10000 })
    })

    it('hides the co-organizers management panel for a non-creator co-organizer', async () => {
      // callerId !== creatorId → isCreator false → coOrganizersSection is undefined.
      mockUseAuth.mockReturnValue({ user: { id: 'a-different-co-organizer' }, isAdmin: false })
      mockGetById.mockResolvedValue(existingEvent)

      renderPage()

      await waitFor(() => expect(screen.getByDisplayValue(existingEvent.title)).toBeTruthy(), { timeout: 10000 })
      // The "Co-organisateurs" management heading is creator-only.
      expect(screen.queryByText(/Co-organisateurs/i)).toBeNull()
    })

    it('does not set an error after unmount when getById rejects (cancelled cleanup)', async () => {
      let rejectGet!: (e: Error) => void
      mockGetById.mockReturnValue(new Promise((_res, rej) => { rejectGet = rej }))

      const { unmount } = renderPage()
      unmount()

      // Rejecting after unmount must hit the `if (!cancelled)` false branch in
      // the catch — no setError, no act() warning, no crash.
      await new Promise<void>((r) => {
        rejectGet(new Error('boom'))
        setTimeout(r, 0)
      })
    })

    it('shows the "Brouillon sans titre" fallback in the delete modal when the draft has no title', async () => {
      mockGetById.mockResolvedValue({ ...draftEvent, title: '' })

      renderPage()

      await waitFor(() => expect(screen.getByRole('button', { name: 'Supprimer le brouillon' })).toBeTruthy(), { timeout: 10000 })
      fireEvent.click(screen.getByRole('button', { name: 'Supprimer le brouillon' }))

      expect(screen.getByText(/Brouillon sans titre/)).toBeTruthy()
    })

    it('merges a freshly uploaded attachment into the event via the editor onChange', async () => {
      mockGetById.mockResolvedValue(existingEvent)
      mockUploadEventAttachment.mockResolvedValue({
        id: 3,
        fileName: 'annexe.pdf',
        fileUrl: 'http://minio:9000/bucket/event-attachments/annexe.pdf',
        downloadUrl: '/api/events/42/attachments/3/download',
        fileSize: 1024,
        mimeType: 'application/pdf',
        uploadedById: 'u-1',
        uploadedAt: '2026-05-18T10:00:00Z',
      })

      renderPage()
      await waitFor(() => expect(screen.getByDisplayValue(existingEvent.title)).toBeTruthy(), { timeout: 10000 })

      const input = document.querySelector<HTMLInputElement>('#event-attachments-input')
      if (!input) throw new Error('missing attachments input')
      fireEvent.change(input, { target: { files: [new File(['x'], 'annexe.pdf', { type: 'application/pdf' })] } })

      fireEvent.click(screen.getByRole('button', { name: /Uploader/ }))

      // onChange([...attachments, uploaded]) → setEvent merges it into the
      // event, so the uploaded file now appears in the "joints" list with its
      // download anchor.
      await waitFor(() => expect(screen.getByRole('link', { name: 'Télécharger annexe.pdf' })).toBeTruthy())
    })
  })
})
