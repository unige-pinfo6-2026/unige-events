// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import EventEditPage from '@/pages/event/EventEditPage'
import { ToastProvider } from '@/contexts/ToastContext'
import ToastsWrapper from '@/components/utils/Toast'

vi.mock('@/services/eventApi', () => ({
  createEvent: vi.fn(),
  getById: vi.fn(),
  updateEvent: vi.fn(),
  uploadEventImage: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

import { getById, updateEvent, uploadEventImage } from '@/services/eventApi'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'

const mockGetById = getById as ReturnType<typeof vi.fn>
const mockUpdateEvent = updateEvent as ReturnType<typeof vi.fn>
const mockUploadEventImage = uploadEventImage as ReturnType<typeof vi.fn>

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
  status: 'DRAFT',
  capacity: 120,
  createdAt: '2026-03-27T09:00:00.000Z',
  bannerUrl: 'https://example.com/current-banner.png',
}

beforeEach(() => {
  vi.useRealTimers()
})

afterEach(() => {
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

    expect(await screen.findByDisplayValue(existingEvent.title)).toBeTruthy()
    expect(screen.getByDisplayValue(existingEvent.location)).toBeTruthy()
  })

  it('updates an event and redirects to its detail page', async () => {
    mockGetById.mockResolvedValue(existingEvent)
    mockUpdateEvent.mockResolvedValue({ ...existingEvent, title: 'Forum 2026', status: 'PUBLISHED' })

    renderPage()
    await screen.findByDisplayValue(existingEvent.title)
    fireEvent.change(screen.getByLabelText(/Titre/i), { target: { value: 'Forum 2026' } })
    fireEvent.change(screen.getByLabelText(/Statut/i), { target: { value: 'PUBLISHED' } })
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

    await screen.findByDisplayValue(existingEvent.title)
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

    expect(await screen.findByText('La date de début doit être dans le futur.')).toBeTruthy()
  })

  it('uploads a new banner during update', async () => {
    mockGetById.mockResolvedValue(existingEvent)
    mockUpdateEvent.mockResolvedValue(existingEvent)
    mockUploadEventImage.mockResolvedValue({ ...existingEvent, bannerUrl: 'https://example.com/banner.png' })
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')

    renderPage()

    await screen.findByDisplayValue(existingEvent.title)
    const fileInput = document.querySelector<HTMLInputElement>('#event-banner')
    if (!fileInput) {
      throw new Error('Missing banner input')
    }
    const file = new File(['img'], 'banner.png', { type: 'image/png' })
    fireEvent.change(fileInput, { target: { files: [file] } })

    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))

    await screen.findByText('Événement mis à jour avec succès.')
    expect(mockUploadEventImage).toHaveBeenCalledWith(42, file)
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

    await screen.findByDisplayValue(existingEvent.title)
    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

    expect(mockNavigate).toHaveBeenCalledWith('/events/42')
  })

  it('stores the banner error in sessionStorage when image upload fails after update', async () => {
    mockGetById.mockResolvedValue(existingEvent)
    mockUpdateEvent.mockResolvedValue(existingEvent)
    mockUploadEventImage.mockRejectedValue(new Error('upload failed'))
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem')

    renderPage()

    await screen.findByDisplayValue(existingEvent.title)

    const fileInput = document.querySelector<HTMLInputElement>('#event-banner')
    if (!fileInput) throw new Error('Missing banner input')
    fireEvent.change(fileInput, { target: { files: [new File(['img'], 'banner.png', { type: 'image/png' })] } })

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

    await screen.findByDisplayValue(existingEvent.title)
    fireEvent.click(screen.getByRole('button', { name: 'Enregistrer' }))
    expect(await screen.findByText('Événement mis à jour avec succès.')).toBeTruthy()

    unmount()

    expect(clearTimeoutSpy.mock.calls.length).toBeGreaterThanOrEqual(2)
  })
})
