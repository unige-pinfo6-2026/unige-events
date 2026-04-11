// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import EventCreatePage from '@/pages/event/EventCreatePage'
import { ToastProvider } from '@/contexts/ToastContext'
import ToastsWrapper from '@/components/utils/Toast'

vi.mock('@/services/eventApi', () => ({
  createEvent: vi.fn(),
  updateEvent: vi.fn(),
  uploadEventImage: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

import { createEvent, updateEvent, uploadEventImage } from '@/services/eventApi'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'

const mockCreateEvent = createEvent as ReturnType<typeof vi.fn>
const mockUpdateEvent = updateEvent as ReturnType<typeof vi.fn>
const mockUploadEventImage = uploadEventImage as ReturnType<typeof vi.fn>

const createdEvent = {
  id: 42,
  title: 'Forum des associations',
  description: 'Rencontrez les associations du campus.',
  location: 'Uni Dufour',
  startDate: '2026-04-10T08:00:00.000Z',
  endDate: '2026-04-10T10:00:00.000Z',
  category: 'SOCIAL',
  creatorId: '8b24e4aa-fdea-4e04-bf56-bdb2ddb7fc11',
  status: 'DRAFT',
  capacity: 120,
  createdAt: '2026-03-27T09:00:00.000Z',
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

function renderPage() {
  return render(
    <ToastProvider>
      <ToastsWrapper />
      <MemoryRouter>
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
  fireEvent.click(screen.getByRole('button', { name: 'Social' }))
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
    fireEvent.click(screen.getByRole('button', { name: 'Social' }))

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
    fireEvent.click(screen.getByRole('button', { name: 'Social' }))

    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    expect(await screen.findByText('La date de fin doit être postérieure à la date de début.')).toBeTruthy()
  })

  it('creates an event, uploads the banner, and redirects to the detail page', async () => {
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
    fireEvent.change(fileInput, { target: { files: [file] } })

    fireEvent.click(screen.getByRole('button', { name: "Créer l'événement" }))

    expect(await screen.findByText('Événement créé avec succès.')).toBeTruthy()
    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({
      title: createdEvent.title,
      location: createdEvent.location,
      category: createdEvent.category,
      capacity: 120,
      status: 'DRAFT',
    }))
    expect(mockUploadEventImage).toHaveBeenCalledWith(42, file)

    await waitFor(() => expect(mockNavigate).toHaveBeenCalledWith('/events/42'), { timeout: 2000 })
  })

  it('sends the published status during creation without a second update call', async () => {
    mockCreateEvent.mockResolvedValue({ ...createdEvent, status: 'PUBLISHED' })

    renderPage()

    fillRequiredFields()
    fireEvent.change(screen.getByLabelText(/Statut/i), { target: { value: 'PUBLISHED' } })

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

  it('navigates back home when cancel is clicked', () => {
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

    expect(mockNavigate).toHaveBeenCalledWith('/')
  })

  it('stores the banner error in sessionStorage when image upload fails after creation', async () => {
    mockCreateEvent.mockResolvedValue(createdEvent)
    mockUploadEventImage.mockRejectedValue(new Error('upload failed'))
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')
    const setItemSpy = vi.spyOn(Storage.prototype, 'setItem')

    renderPage()

    fillRequiredFields()

    const fileInput = document.querySelector<HTMLInputElement>('#event-banner')
    if (!fileInput) throw new Error('Missing banner input')
    fireEvent.change(fileInput, { target: { files: [new File(['img'], 'banner.png', { type: 'image/png' })] } })

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
})
