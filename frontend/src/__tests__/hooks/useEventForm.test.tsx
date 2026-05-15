
import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { FormEvent } from 'react'
import {
  DRAFT_FORM_KEY,
  DRAFT_FORM_PERSIST_DEBOUNCE_MS,
  EDIT_FORM_KEY_PREFIX,
  EVENT_DESCRIPTION_MAX_LENGTH,
  EVENT_TITLE_MAX_LENGTH,
  IMAGE_MAX_SIZE_BYTES,
  IMAGE_MAX_SIZE_MB,
  useEventForm,
} from '@/hooks/useEventForm'
import {
  EVENT_CONTACT_EMAIL_MAX_LENGTH,
  EVENT_TAG_MAX_LENGTH,
  EVENT_TAGS_MAX_ITEMS,
  EVENT_WEBSITE_URL_MAX_LENGTH,
} from '@/types/event'

vi.mock('@/services/eventApi', () => ({
  createEvent: vi.fn(),
  updateEvent: vi.fn(),
  uploadEventImage: vi.fn(),
}))

import { createEvent, updateEvent, uploadEventImage } from '@/services/eventApi'

const mockCreateEvent = createEvent as ReturnType<typeof vi.fn>
const mockUpdateEvent = updateEvent as ReturnType<typeof vi.fn>
const mockUploadEventImage = uploadEventImage as ReturnType<typeof vi.fn>

const baseEvent = {
  id: 42,
  title: 'Forum des associations',
  description: 'Rencontrez les associations du campus.',
  location: 'Uni Dufour',
  startDate: '2099-04-10T08:00:00.000Z',
  endDate: '2099-04-10T10:00:00.000Z',
  allDay: false,
  category: 'SOCIAL' as const,
  faculty: null,
  creatorId: '8b24e4aa-fdea-4e04-bf56-bdb2ddb7fc11',
  status: 'DRAFT' as const,
  capacity: 120,
  attendingCount: 0,
  createdAt: '2026-03-27T09:00:00.000Z',
  bannerUrl: 'https://example.com/current-banner.png',
}

function submitEvent() {
  return {
    preventDefault: vi.fn(),
  } as unknown as FormEvent<HTMLFormElement>
}

let originalFileReader: typeof FileReader
let originalCreateObjectURL: typeof URL.createObjectURL
let originalRevokeObjectURL: typeof URL.revokeObjectURL

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

beforeEach(() => {
  originalFileReader = globalThis.FileReader
  originalCreateObjectURL = globalThis.URL.createObjectURL
  originalRevokeObjectURL = globalThis.URL.revokeObjectURL
})

afterEach(() => {
  globalThis.FileReader = originalFileReader
  globalThis.URL.createObjectURL = originalCreateObjectURL
  globalThis.URL.revokeObjectURL = originalRevokeObjectURL
  vi.useRealTimers()
  vi.resetAllMocks()
  sessionStorage.clear()
})

describe('useEventForm', () => {
  it('keeps validation errors when changing non-validatable fields', async () => {
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(result.current.errors.title).toBe('Le titre est requis.')

    act(() => {
      result.current.setFieldValue('description', 'Texte libre')
      result.current.setFieldValue('status', 'PUBLISHED')
    })

    expect(result.current.errors.title).toBe('Le titre est requis.')
  })

  it('validates invalid dates and capacity before submit', async () => {
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('category', 'SOCIAL')
      result.current.setFieldValue('startDate', 'invalid-date')
      result.current.setFieldValue('endDate', 'invalid-date')
      result.current.setFieldValue('capacity', '0')
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(result.current.errors.startDate).toBe('La date de début est invalide.')
    expect(result.current.errors.endDate).toBe('La date de fin est invalide.')
    expect(result.current.errors.capacity).toBe('La capacité doit être un entier strictement positif.')
    expect(mockCreateEvent).not.toHaveBeenCalled()
  })

  it('blocks submission when the start date is in the past', async () => {
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('category', 'SOCIAL')
      result.current.setFieldValue('startDate', '2000-01-01T10:00')
      result.current.setFieldValue('endDate', '2099-01-01T12:00')
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(result.current.errors.startDate).toBe('La date de début doit être postérieure à la date et à l\'heure actuelles.')
    expect(mockCreateEvent).not.toHaveBeenCalled()
  })

  it('validates title and description max length before submit', async () => {
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    act(() => {
      result.current.setFieldValue('title', 'x'.repeat(EVENT_TITLE_MAX_LENGTH + 1))
      result.current.setFieldValue('description', 'y'.repeat(EVENT_DESCRIPTION_MAX_LENGTH + 1))
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('category', 'SOCIAL')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(result.current.errors.title).toBe(`Le titre ne doit pas dépasser ${EVENT_TITLE_MAX_LENGTH} caractères.`)
    expect(result.current.errors.description).toBe(`La description ne doit pas dépasser ${EVENT_DESCRIPTION_MAX_LENGTH} caractères.`)
    expect(mockCreateEvent).not.toHaveBeenCalled()
  })

  it('stores the cropped image after confirmCrop and rejects invalid files', async () => {
    mockFileReader('data:image/png;base64,abc')
    const createObjectURL = vi.fn()
      .mockReturnValueOnce('blob:first')
      .mockReturnValueOnce('blob:second')
    const revokeObjectURL = vi.fn()

    globalThis.URL.createObjectURL = createObjectURL
    globalThis.URL.revokeObjectURL = revokeObjectURL

    const { result, unmount } = renderHook(() => useEventForm({ mode: 'create' }))

    act(() => {
      result.current.handleImageChange({ target: { files: [new File(['x'], 'doc.txt', { type: 'text/plain' })] } } as never)
    })
    expect(result.current.errors.image).toBe('Le fichier doit être une image.')
    expect(result.current.cropSource).toBeNull()

    act(() => {
      result.current.handleImageChange({ target: { files: [] } } as never)
    })
    expect(createObjectURL).not.toHaveBeenCalled()

    await act(async () => {
      result.current.handleImageChange({ target: { files: [new File(['a'], 'banner-a.png', { type: 'image/png' })] } } as never)
      await new Promise((r) => queueMicrotask(r as () => void))
    })
    expect(result.current.cropSource).toBe('data:image/png;base64,abc')
    expect(result.current.imagePreview).toBeNull()

    act(() => {
      result.current.confirmCrop(new Blob(['cropped'], { type: 'image/png' }))
    })
    expect(result.current.imagePreview).toBe('blob:first')
    expect(result.current.selectedImageName).toBe('banner-a.png')
    expect(result.current.cropSource).toBeNull()
    expect(result.current.errors.image).toBeUndefined()

    await act(async () => {
      result.current.handleImageChange({ target: { files: [new File(['b'], 'banner-b.png', { type: 'image/png' })] } } as never)
      await new Promise((r) => queueMicrotask(r as () => void))
    })
    act(() => {
      result.current.confirmCrop(new Blob(['cropped2'], { type: 'image/png' }))
    })
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:first')
    expect(result.current.imagePreview).toBe('blob:second')

    unmount()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:second')
  })

  it('rejects a file that exceeds the maximum allowed size', () => {
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    const oversizedFile = new File([new ArrayBuffer(IMAGE_MAX_SIZE_BYTES + 1)], 'big.png', { type: 'image/png' })
    act(() => {
      result.current.handleImageChange({ target: { files: [oversizedFile] } } as never)
    })

    expect(result.current.errors.image).toBe(
      `Le fichier dépasse la taille maximale autorisée (${IMAGE_MAX_SIZE_MB} Mo).`,
    )
    expect(result.current.imagePreview).toBeNull()
    expect(result.current.cropSource).toBeNull()
  })

  it('cancelCrop closes the cropper without touching imageFile/imagePreview', async () => {
    mockFileReader('data:image/png;base64,abc')
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    await act(async () => {
      result.current.handleImageChange({ target: { files: [new File(['x'], 'p.png', { type: 'image/png' })] } } as never)
      await new Promise((r) => queueMicrotask(r as () => void))
    })
    expect(result.current.cropSource).not.toBeNull()

    act(() => {
      result.current.cancelCrop()
    })
    expect(result.current.cropSource).toBeNull()
    expect(result.current.imagePreview).toBeNull()
    expect(result.current.selectedImageName).toBeNull()
  })

  it('submits creation payload with trimmed and optional fields normalized', async () => {
    mockCreateEvent.mockResolvedValue(baseEvent)
    const onSuccess = vi.fn()

    const { result } = renderHook(() => useEventForm({ mode: 'create', onSuccess }))

    act(() => {
      result.current.setFieldValue('title', '  Forum des associations  ')
      result.current.setFieldValue('description', '   ')
      result.current.setFieldValue('location', '  Uni Dufour  ')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
      result.current.setFieldValue('capacity', '')
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(mockCreateEvent).toHaveBeenCalledWith({
      title: 'Forum des associations',
      description: undefined,
      location: 'Uni Dufour',
      startDate: '2099-04-10T10:00:00.000Z',
      endDate: '2099-04-10T12:00:00.000Z',
      category: 'SOCIAL',
      faculty: null,
      capacity: undefined,
      status: 'PUBLISHED',
      allDay: false,
      websiteUrl: null,
      contactEmail: null,
      registrationDeadline: null,
      tags: null,
    })
    expect(onSuccess).toHaveBeenCalledWith(baseEvent)
  })

  it('sends the selected published status during creation', async () => {
    mockCreateEvent.mockResolvedValue({ ...baseEvent, status: 'PUBLISHED' })

    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
      result.current.setFieldValue('status', 'PUBLISHED')
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({ status: 'PUBLISHED' }))
    expect(mockUpdateEvent).not.toHaveBeenCalled()
  })

  it('reports a localized validation error message on creation failure', async () => {
    mockCreateEvent.mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          message: 'post validation error',
          details: [
            { field: 'title', message: 'must not be blank' },
            { field: 'startDate', message: 'must be in the future' },
          ],
        },
      },
    })

    const onError = vi.fn()
    const { result } = renderHook(() => useEventForm({ mode: 'create', onError }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(onError).toHaveBeenCalledWith('Le titre est requis. La date de début doit être dans le futur.')
  })

  it('replaces a raw technical backend message with a friendly French fallback', async () => {
    mockCreateEvent.mockRejectedValue({
      isAxiosError: true,
      response: { data: { message: 'post validation error' } },
    })

    const onError = vi.fn()
    const { result } = renderHook(() => useEventForm({ mode: 'create', onError }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(onError).toHaveBeenCalledWith('La validation de l\'événement a échoué. Vérifiez les informations saisies.')
  })

  it('returns the generic edit error when edit mode has no initial event', async () => {
    const onError = vi.fn()
    const { result } = renderHook(() => useEventForm({ mode: 'edit', onError }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(onError).toHaveBeenCalledWith('La mise à jour de l\'événement a échoué. Veuillez réessayer.')
    expect(mockUpdateEvent).not.toHaveBeenCalled()
  })

  it('localizes endDate "after" validation detail', async () => {
    mockCreateEvent.mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          details: [{ field: 'endDate', message: 'must be after start date' }],
        },
      },
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useEventForm({ mode: 'create', onError }))
    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
    })
    await act(async () => { await result.current.handleSubmit(submitEvent()) })
    expect(onError).toHaveBeenCalledWith('La date de fin doit être postérieure à la date de début.')
  })

  it('localizes "must be greater than 0" validation detail', async () => {
    mockCreateEvent.mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          details: [{ field: 'capacity', message: 'must be greater than 0' }],
        },
      },
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useEventForm({ mode: 'create', onError }))
    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
    })
    await act(async () => { await result.current.handleSubmit(submitEvent()) })
    expect(onError).toHaveBeenCalledWith(expect.stringContaining('supérieure à 0'))
  })

  it('localizes "must be greater than or equal to 1" validation detail', async () => {
    mockCreateEvent.mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          details: [{ field: 'capacity', message: 'must be greater than or equal to 1' }],
        },
      },
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useEventForm({ mode: 'create', onError }))
    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
    })
    await act(async () => { await result.current.handleSubmit(submitEvent()) })
    expect(onError).toHaveBeenCalledWith(expect.stringContaining('supérieure ou égale à 1'))
  })

  it('passes through French validation detail messages as-is', async () => {
    mockCreateEvent.mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          details: [{ field: 'title', message: 'Le titre est invalide.' }],
        },
      },
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useEventForm({ mode: 'create', onError }))
    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
    })
    await act(async () => { await result.current.handleSubmit(submitEvent()) })
    expect(onError).toHaveBeenCalledWith('Le titre est invalide.')
  })

  it('uses fieldLabel prefix for unknown detail messages', async () => {
    mockCreateEvent.mockRejectedValue({
      isAxiosError: true,
      response: {
        data: {
          details: [{ field: 'title', message: 'some unknown constraint' }],
        },
      },
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useEventForm({ mode: 'create', onError }))
    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
    })
    await act(async () => { await result.current.handleSubmit(submitEvent()) })
    expect(onError).toHaveBeenCalledWith(expect.stringContaining('Le titre'))
    expect(onError).toHaveBeenCalledWith(expect.stringContaining('some unknown constraint'))
  })

  it('passes through French top-level API error messages', async () => {
    mockCreateEvent.mockRejectedValue({
      isAxiosError: true,
      response: { data: { message: 'La création a échoué pour des raisons externes.' } },
    })
    const onError = vi.fn()
    const { result } = renderHook(() => useEventForm({ mode: 'create', onError }))
    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
    })
    await act(async () => { await result.current.handleSubmit(submitEvent()) })
    expect(onError).toHaveBeenCalledWith('La création a échoué pour des raisons externes.')
  })

  it('forces start to T00:00 and end to T23:59 when allDay is true', async () => {
    mockCreateEvent.mockResolvedValue(baseEvent)
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('startDate', '2099-04-10T14:30')
      result.current.setFieldValue('endDate', '2099-04-10T16:00')
      result.current.setFieldValue('category', 'SOCIAL')
      result.current.setFieldValue('allDay', true)
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    const call = mockCreateEvent.mock.calls[0][0]
    expect(call.allDay).toBe(true)
    expect(call.startDate).toBe(new Date('2099-04-10T00:00').toISOString())
    expect(call.endDate).toBe(new Date('2099-04-10T23:59').toISOString())
  })

  it('passes allDay false through and keeps the original times', async () => {
    mockCreateEvent.mockResolvedValue(baseEvent)
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('startDate', '2099-04-10T14:30')
      result.current.setFieldValue('endDate', '2099-04-10T16:00')
      result.current.setFieldValue('category', 'SOCIAL')
      result.current.setFieldValue('allDay', false)
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    const call = mockCreateEvent.mock.calls[0][0]
    expect(call.allDay).toBe(false)
    expect(call.startDate).toBe(new Date('2099-04-10T14:30').toISOString())
  })

  it('skips end-after-start time check when allDay normalizes the bounds', async () => {
    mockCreateEvent.mockResolvedValue(baseEvent)
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('startDate', '2099-04-10T20:00')
      result.current.setFieldValue('endDate', '2099-04-10T08:00')
      result.current.setFieldValue('category', 'SOCIAL')
      result.current.setFieldValue('allDay', true)
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(result.current.errors.endDate).toBeUndefined()
    expect(mockCreateEvent).toHaveBeenCalled()
  })

  it('loads allDay from the initial event when editing', async () => {
    const allDayEvent = { ...baseEvent, allDay: true }
    const { result, rerender } = renderHook(
      ({ initialEvent }) => useEventForm({ mode: 'edit', initialEvent }),
      { initialProps: { initialEvent: null as typeof baseEvent | null } },
    )

    rerender({ initialEvent: allDayEvent })

    await waitFor(() => expect(result.current.values.allDay).toBe(true))
  })

  it('saves with DRAFT status when triggerDraftSave is called regardless of the current status field', async () => {
    mockCreateEvent.mockResolvedValue({ ...baseEvent, status: 'DRAFT' })
    const onSuccess = vi.fn()
    const { result } = renderHook(() => useEventForm({ mode: 'create', onSuccess }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
      result.current.setFieldValue('status', 'PUBLISHED')
    })

    await act(async () => {
      await result.current.triggerDraftSave()
    })

    expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({ status: 'DRAFT' }))
    expect(onSuccess).toHaveBeenCalled()
  })

  it('toggles draftSaving (not submitting) while triggerDraftSave is in flight', async () => {
    let resolveCreate: (event: typeof baseEvent) => void = () => {}
    mockCreateEvent.mockReturnValue(new Promise(r => { resolveCreate = r }))
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('startDate', '2099-04-10T10:00')
      result.current.setFieldValue('endDate', '2099-04-10T12:00')
      result.current.setFieldValue('category', 'SOCIAL')
    })

    let pending: Promise<void> = Promise.resolve()
    act(() => { pending = result.current.triggerDraftSave() })

    expect(result.current.draftSaving).toBe(true)
    expect(result.current.submitting).toBe(false)

    await act(async () => {
      resolveCreate({ ...baseEvent, status: 'DRAFT' })
      await pending
    })

    expect(result.current.draftSaving).toBe(false)
    expect(result.current.submitting).toBe(false)
  })

  it('resets to incoming event values and uses the uploaded event response', async () => {
    mockFileReader('data:image/png;base64,abc')
    const uploadedEvent = { ...baseEvent, bannerUrl: 'https://example.com/banner.png', status: 'PUBLISHED' as const }
    mockUpdateEvent.mockResolvedValue(baseEvent)
    mockUploadEventImage.mockResolvedValue(uploadedEvent)
    globalThis.URL.createObjectURL = vi.fn(() => 'blob:preview-url')
    globalThis.URL.revokeObjectURL = vi.fn()
    const onSuccess = vi.fn()

    const { result, rerender } = renderHook(
      ({ initialEvent }) => useEventForm({ mode: 'edit', initialEvent, onSuccess }),
      { initialProps: { initialEvent: null as typeof baseEvent | null } },
    )

    rerender({ initialEvent: baseEvent })

    await waitFor(() => expect(result.current.values.title).toBe(baseEvent.title))
    expect(result.current.values.description).toBe(baseEvent.description)
    expect(result.current.values.capacity).toBe('120')

    act(() => {
      result.current.setFieldValue('title', 'Forum 2026')
    })

    await act(async () => {
      result.current.handleImageChange({ target: { files: [new File(['img'], 'banner.png', { type: 'image/png' })] } } as never)
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    act(() => {
      result.current.confirmCrop(new Blob(['cropped'], { type: 'image/png' }))
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(mockUpdateEvent).toHaveBeenCalledWith(42, expect.objectContaining({
      title: 'Forum 2026',
      location: baseEvent.location,
      bannerUrl: 'https://example.com/current-banner.png',
      status: 'DRAFT',
    }))
    expect(mockUploadEventImage).toHaveBeenCalledWith(42, expect.any(File))
    expect(onSuccess).toHaveBeenCalledWith(uploadedEvent)
  })

  describe('sessionStorage persistence (create mode only)', () => {
    it('starts with DEFAULT_VALUES when sessionStorage is empty on mount', () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))
      expect(result.current.values.title).toBe('')
      expect(result.current.values.location).toBe('')
      expect(result.current.values.category).toBe('')
    })

    it('hydrates from sessionStorage when a valid JSON is persisted', () => {
      sessionStorage.setItem(DRAFT_FORM_KEY, JSON.stringify({
        title: 'Forum persisté',
        location: 'Uni Dufour',
        startDate: '2099-04-10T10:00',
        endDate: '2099-04-10T12:00',
        category: 'SOCIAL',
        capacity: '50',
        status: 'PUBLISHED',
        description: 'Hydraté depuis sessionStorage',
      }))
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))
      expect(result.current.values.title).toBe('Forum persisté')
      expect(result.current.values.location).toBe('Uni Dufour')
      expect(result.current.values.category).toBe('SOCIAL')
      expect(result.current.values.description).toBe('Hydraté depuis sessionStorage')
    })

    it('debounces writes to sessionStorage in create mode (no write before the timer fires)', () => {
      vi.useFakeTimers()
      try {
        const { result } = renderHook(() => useEventForm({ mode: 'create' }))
        act(() => {
          result.current.setFieldValue('title', 'Nouveau titre')
        })
        // Before the debounce timer fires, nothing is persisted yet.
        expect(sessionStorage.getItem(DRAFT_FORM_KEY)).toBeNull()
        act(() => { vi.advanceTimersByTime(DRAFT_FORM_PERSIST_DEBOUNCE_MS + 10) })
        const raw = sessionStorage.getItem(DRAFT_FORM_KEY)
        expect(raw).not.toBeNull()
        const parsed = JSON.parse(raw!) as { title?: string }
        expect(parsed.title).toBe('Nouveau titre')
      } finally {
        vi.useRealTimers()
      }
    })

    it('collapses rapid setFieldValue calls into a single debounced write', () => {
      vi.useFakeTimers()
      try {
        const { result } = renderHook(() => useEventForm({ mode: 'create' }))
        act(() => {
          result.current.setFieldValue('title', 'A')
          result.current.setFieldValue('title', 'AB')
          result.current.setFieldValue('title', 'ABC')
        })
        expect(sessionStorage.getItem(DRAFT_FORM_KEY)).toBeNull()
        act(() => { vi.advanceTimersByTime(DRAFT_FORM_PERSIST_DEBOUNCE_MS + 10) })
        const parsed = JSON.parse(sessionStorage.getItem(DRAFT_FORM_KEY)!) as { title?: string }
        expect(parsed.title).toBe('ABC')
      } finally {
        vi.useRealTimers()
      }
    })

    it('flushes pending debounced write on unmount so refresh keeps the last keystrokes', () => {
      vi.useFakeTimers()
      try {
        const { result, unmount } = renderHook(() => useEventForm({ mode: 'create' }))
        act(() => {
          result.current.setFieldValue('title', 'Almost saved')
        })
        // Do NOT advance timers — simulate an immediate refresh (unmount) while the
        // debounce timer is still armed.
        expect(sessionStorage.getItem(DRAFT_FORM_KEY)).toBeNull()
        unmount()
        const parsed = JSON.parse(sessionStorage.getItem(DRAFT_FORM_KEY)!) as { title?: string }
        expect(parsed.title).toBe('Almost saved')
      } finally {
        vi.useRealTimers()
      }
    })

    // it('clears sessionStorage after a successful create submission', async () => {
    //   vi.useFakeTimers()
    //   try {
    //     sessionStorage.setItem(DRAFT_FORM_KEY, JSON.stringify({ title: 'Sera publié' }))
    //     mockCreateEvent.mockResolvedValue({ ...baseEvent, status: 'PUBLISHED' })
    //     const { result } = renderHook(() => useEventForm({ mode: 'create' }))
    //     act(() => {
    //       result.current.setFieldValue('title', 'Forum')
    //       result.current.setFieldValue('location', 'Uni Dufour')
    //       result.current.setFieldValue('startDate', '2099-04-10T10:00')
    //       result.current.setFieldValue('endDate', '2099-04-10T12:00')
    //       result.current.setFieldValue('category', 'SOCIAL')
    //     })
    //     act(() => { vi.advanceTimersByTime(DRAFT_FORM_PERSIST_DEBOUNCE_MS + 10) })
    //     await act(async () => {
    //       await result.current.handleSubmit(submitEvent())
    //     })

    //     expect(sessionStorage.getItem(DRAFT_FORM_KEY)).toBeNull()
    //   } finally {
    //     vi.useRealTimers()
    //   }
    // })

    it('does not read from sessionStorage in edit mode', () => {
      sessionStorage.setItem(DRAFT_FORM_KEY, JSON.stringify({ title: 'Leak' }))
      const { result } = renderHook(() => useEventForm({ mode: 'edit', initialEvent: baseEvent }))
      expect(result.current.values.title).toBe(baseEvent.title)
      expect(result.current.values.title).not.toBe('Leak')
    })

    it('exposes clearPersistedDraft that wipes the key on demand', () => {
      sessionStorage.setItem(DRAFT_FORM_KEY, JSON.stringify({ title: 'À effacer' }))
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))
      act(() => { result.current.clearPersistedDraft() })
      expect(sessionStorage.getItem(DRAFT_FORM_KEY)).toBeNull()
    })
  })

  describe('sessionStorage persistence (edit mode, per-event key)', () => {
    const editKey = `${EDIT_FORM_KEY_PREFIX}${baseEvent.id}`

    it('hydrates from the per-event key when a persisted form exists for this event', async () => {
      sessionStorage.setItem(editKey, JSON.stringify({
        title: 'Unsaved title',
        location: 'Unsaved location',
        startDate: '2099-04-10T10:00',
        endDate: '2099-04-10T12:00',
        category: 'SOCIAL',
        capacity: '30',
        status: 'PUBLISHED',
        description: 'Draft recovered from sessionStorage',
      }))
      const { result } = renderHook(() => useEventForm({ mode: 'edit', initialEvent: baseEvent }))
      await waitFor(() => expect(result.current.values.title).toBe('Unsaved title'))
      expect(result.current.values.location).toBe('Unsaved location')
      expect(result.current.values.description).toBe('Draft recovered from sessionStorage')
    })

    it('falls back to initialEvent when the per-event key is absent', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'edit', initialEvent: baseEvent }))
      await waitFor(() => expect(result.current.values.title).toBe(baseEvent.title))
      expect(result.current.values.location).toBe(baseEvent.location)
    })

    it('does not hydrate from the create key when in edit mode', async () => {
      sessionStorage.setItem(DRAFT_FORM_KEY, JSON.stringify({ title: 'Create leak' }))
      const { result } = renderHook(() => useEventForm({ mode: 'edit', initialEvent: baseEvent }))
      await waitFor(() => expect(result.current.values.title).toBe(baseEvent.title))
    })

    it('debounces writes to the per-event key after setFieldValue', () => {
      vi.useFakeTimers()
      try {
        const { result } = renderHook(() => useEventForm({ mode: 'edit', initialEvent: baseEvent }))
        act(() => { result.current.setFieldValue('title', 'Edited title') })
        expect(sessionStorage.getItem(editKey)).toBeNull()
        act(() => { vi.advanceTimersByTime(DRAFT_FORM_PERSIST_DEBOUNCE_MS + 10) })
        const parsed = JSON.parse(sessionStorage.getItem(editKey)!) as { title?: string }
        expect(parsed.title).toBe('Edited title')
        // Must NOT touch the create key from edit mode.
        expect(sessionStorage.getItem(DRAFT_FORM_KEY)).toBeNull()
      } finally {
        vi.useRealTimers()
      }
    })

    it('clears the per-event key after a successful edit submission', async () => {
      sessionStorage.setItem(editKey, JSON.stringify({
        title: 'Will be cleared',
        location: baseEvent.location,
        startDate: '2099-04-10T08:00',
        endDate: '2099-04-10T10:00',
        category: baseEvent.category,
        capacity: '120',
        status: baseEvent.status,
      }))
      mockUpdateEvent.mockResolvedValue({ ...baseEvent, title: 'Updated' })
      const { result } = renderHook(() => useEventForm({ mode: 'edit', initialEvent: baseEvent }))
      // Hydration from sessionStorage runs in the useEffect, wait for it to land.
      await waitFor(() => expect(result.current.values.title).toBe('Will be cleared'))
      await act(async () => {
        await result.current.handleSubmit(submitEvent())
      })
      expect(sessionStorage.getItem(editKey)).toBeNull()
    })

    it('isolates per-event keys — two different events do not collide', () => {
      const otherEvent = { ...baseEvent, id: 99 }
      sessionStorage.setItem(`${EDIT_FORM_KEY_PREFIX}99`, JSON.stringify({ title: 'Other event' }))
      const { result } = renderHook(() => useEventForm({ mode: 'edit', initialEvent: baseEvent }))
      // Hydration uses the key for baseEvent.id (42), not the one for event 99.
      expect(result.current.values.title).toBe(baseEvent.title)
      expect(result.current.values.title).not.toBe('Other event')
      void otherEvent
    })
  })

  describe('SCRUM-117 — extra optional fields (websiteUrl, contactEmail, registrationDeadline, tags)', () => {
    function fillMandatory(setFieldValue: ReturnType<typeof useEventForm>['setFieldValue']) {
      setFieldValue('title', 'Forum')
      setFieldValue('location', 'Uni Dufour')
      setFieldValue('startDate', '2099-04-10T10:00')
      setFieldValue('endDate', '2099-04-10T12:00')
      setFieldValue('category', 'SOCIAL')
    }

    it('defaults the four extra fields to empty values', () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))
      expect(result.current.values.websiteUrl).toBe('')
      expect(result.current.values.contactEmail).toBe('')
      expect(result.current.values.registrationDeadline).toBe('')
      expect(result.current.values.tags).toEqual([])
    })

    it('hydrates the four extra fields from an existing event in edit mode', async () => {
      const eventWithExtras = {
        ...baseEvent,
        websiteUrl: 'https://unige.ch/event',
        contactEmail: 'contact@unige.ch',
        registrationDeadline: '2099-04-09T18:00:00.000Z',
        tags: ['forum', 'associations'],
      }
      const { result, rerender } = renderHook(
        ({ initialEvent }) => useEventForm({ mode: 'edit', initialEvent }),
        { initialProps: { initialEvent: null as typeof eventWithExtras | null } },
      )

      rerender({ initialEvent: eventWithExtras })

      await waitFor(() => expect(result.current.values.websiteUrl).toBe('https://unige.ch/event'))
      expect(result.current.values.contactEmail).toBe('contact@unige.ch')
      expect(result.current.values.registrationDeadline).toMatch(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/)
      expect(result.current.values.tags).toEqual(['forum', 'associations'])
    })

    it('rejects a websiteUrl that is not http(s)', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('websiteUrl', 'ftp://unige.ch/download')
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.websiteUrl).toBe("L'URL du site web est invalide.")
      expect(mockCreateEvent).not.toHaveBeenCalled()
    })

    it('rejects a websiteUrl that cannot be parsed as a URL', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('websiteUrl', 'not a url')
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.websiteUrl).toBe("L'URL du site web est invalide.")
    })

    it('rejects a websiteUrl exceeding the max length', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      const longUrl = 'https://unige.ch/' + 'a'.repeat(EVENT_WEBSITE_URL_MAX_LENGTH)
      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('websiteUrl', longUrl)
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.websiteUrl).toBe(
        `L'URL ne doit pas dépasser ${EVENT_WEBSITE_URL_MAX_LENGTH} caractères.`,
      )
    })

    it('accepts a valid https websiteUrl and forwards it in the payload', async () => {
      mockCreateEvent.mockResolvedValue(baseEvent)
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('websiteUrl', '  https://unige.ch/event  ')
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.websiteUrl).toBeUndefined()
      expect(mockCreateEvent).toHaveBeenCalledWith(
        expect.objectContaining({ websiteUrl: 'https://unige.ch/event' }),
      )
    })

    it('rejects an invalid contactEmail', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('contactEmail', 'not-an-email')
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.contactEmail).toBe("L'email de contact est invalide.")
      expect(mockCreateEvent).not.toHaveBeenCalled()
    })

    it('rejects a contactEmail exceeding the max length', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      const longEmail = 'a'.repeat(EVENT_CONTACT_EMAIL_MAX_LENGTH) + '@unige.ch'
      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('contactEmail', longEmail)
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.contactEmail).toBe(
        `L'email ne doit pas dépasser ${EVENT_CONTACT_EMAIL_MAX_LENGTH} caractères.`,
      )
    })

    it('accepts a valid contactEmail and forwards it in the payload', async () => {
      mockCreateEvent.mockResolvedValue(baseEvent)
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('contactEmail', '  contact@unige.ch  ')
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.contactEmail).toBeUndefined()
      expect(mockCreateEvent).toHaveBeenCalledWith(
        expect.objectContaining({ contactEmail: 'contact@unige.ch' }),
      )
    })

    it('rejects a registrationDeadline that is not before startDate', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('registrationDeadline', '2099-04-10T11:00')
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.registrationDeadline).toBe(
        "La date limite d'inscription doit être antérieure à la date de début.",
      )
      expect(mockCreateEvent).not.toHaveBeenCalled()
    })

    it('rejects a registrationDeadline equal to startDate', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('registrationDeadline', '2099-04-10T10:00')
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.registrationDeadline).toBe(
        "La date limite d'inscription doit être antérieure à la date de début.",
      )
    })

    it('rejects an invalid registrationDeadline value', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('registrationDeadline', 'invalid-date')
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.registrationDeadline).toBe(
        "La date limite d'inscription est invalide.",
      )
    })

    it('accepts a registrationDeadline strictly before startDate and forwards an ISO value', async () => {
      mockCreateEvent.mockResolvedValue(baseEvent)
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('registrationDeadline', '2099-04-09T18:00')
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.registrationDeadline).toBeUndefined()
      const call = mockCreateEvent.mock.calls[0][0]
      expect(call.registrationDeadline).toBe(new Date('2099-04-09T18:00').toISOString())
    })

    it('rejects tag count over the max', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      const tooMany = Array.from({ length: EVENT_TAGS_MAX_ITEMS + 1 }, (_, i) => `tag${i}`)
      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('tags', tooMany)
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.tags).toBe(
        `Vous ne pouvez pas ajouter plus de ${EVENT_TAGS_MAX_ITEMS} mots-clés.`,
      )
      expect(mockCreateEvent).not.toHaveBeenCalled()
    })

    it('rejects a tag longer than EVENT_TAG_MAX_LENGTH', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('tags', ['ok', 'x'.repeat(EVENT_TAG_MAX_LENGTH + 1)])
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.tags).toBe(
        `Chaque mot-clé doit faire au plus ${EVENT_TAG_MAX_LENGTH} caractères.`,
      )
    })

    it('sends tags as an array when non-empty and null when empty', async () => {
      mockCreateEvent.mockResolvedValue(baseEvent)
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('tags', ['forum', 'carrière'])
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(mockCreateEvent).toHaveBeenCalledWith(
        expect.objectContaining({ tags: ['forum', 'carrière'] }),
      )
    })

    it('sends all four extra fields together when populated', async () => {
      mockCreateEvent.mockResolvedValue(baseEvent)
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('websiteUrl', 'https://unige.ch/event')
        result.current.setFieldValue('contactEmail', 'contact@unige.ch')
        result.current.setFieldValue('registrationDeadline', '2099-04-09T18:00')
        result.current.setFieldValue('tags', ['forum'])
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(mockCreateEvent).toHaveBeenCalledWith(
        expect.objectContaining({
          websiteUrl: 'https://unige.ch/event',
          contactEmail: 'contact@unige.ch',
          registrationDeadline: new Date('2099-04-09T18:00').toISOString(),
          tags: ['forum'],
        }),
      )
    })

    it('clears errors for extra fields when setFieldValue is called', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillMandatory(result.current.setFieldValue)
        result.current.setFieldValue('websiteUrl', 'not-a-url')
      })
      await act(async () => { await result.current.handleSubmit(submitEvent()) })
      expect(result.current.errors.websiteUrl).toBeDefined()

      act(() => {
        result.current.setFieldValue('websiteUrl', 'https://unige.ch')
      })
      expect(result.current.errors.websiteUrl).toBeUndefined()
    })
  })

  describe('template mode (templateEvent)', () => {
    const templateEvent = {
      id: 99,
      title: 'Conférence IA',
      description: 'Présentation des dernières avancées en IA.',
      location: 'Uni Bastions',
      startDate: '2025-01-15T09:00:00.000Z',
      endDate: '2025-01-15T11:00:00.000Z',
      allDay: false,
      category: 'CONFERENCE' as const,
      faculty: null,
      creatorId: 'abc',
      status: 'EXPIRED' as const,
      capacity: 80,
      attendingCount: 75,
      websiteUrl: 'https://conf.example.com',
      contactEmail: 'conf@unige.ch',
      tags: ['IA', 'machine-learning'],
      createdAt: '2025-01-01T00:00:00.000Z',
    }

    it('initializes with template fields and leaves dates empty', () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create', templateEvent }))

      expect(result.current.values.title).toBe('Conférence IA')
      expect(result.current.values.description).toBe('Présentation des dernières avancées en IA.')
      expect(result.current.values.location).toBe('Uni Bastions')
      expect(result.current.values.category).toBe('CONFERENCE')
      expect(result.current.values.websiteUrl).toBe('https://conf.example.com')
      expect(result.current.values.contactEmail).toBe('conf@unige.ch')
      expect(result.current.values.tags).toEqual(['IA', 'machine-learning'])
      expect(result.current.values.capacity).toBe('80')
      // Dates must be empty — user must supply new ones
      expect(result.current.values.startDate).toBe('')
      expect(result.current.values.endDate).toBe('')
      expect(result.current.values.registrationDeadline).toBe('')
      // Status resets to the default for new events
      expect(result.current.values.status).toBe('PUBLISHED')
    })

    it('preserves allDay from the template event', () => {
      const allDayTemplate = { ...templateEvent, allDay: true }
      const { result } = renderHook(() => useEventForm({ mode: 'create', templateEvent: allDayTemplate }))

      expect(result.current.values.allDay).toBe(true)
    })

    it('takes priority over a persisted sessionStorage draft', () => {
      sessionStorage.setItem(DRAFT_FORM_KEY, JSON.stringify({
        title: 'Brouillon persisté',
        location: 'Autre lieu',
        category: 'SOCIAL',
      }))

      const { result } = renderHook(() => useEventForm({ mode: 'create', templateEvent }))

      expect(result.current.values.title).toBe('Conférence IA')
      expect(result.current.values.location).toBe('Uni Bastions')
    })

    it('resetForm clears all values to defaults and clears errors', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create', templateEvent }))

      expect(result.current.values.title).toBe('Conférence IA')

      // Trigger a validation error first
      await act(async () => { await result.current.handleSubmit(submitEvent()) })
      expect(result.current.errors.startDate).toBeDefined()

      act(() => { result.current.resetForm() })

      expect(result.current.values.title).toBe('')
      expect(result.current.values.location).toBe('')
      expect(result.current.values.category).toBe('')
      expect(result.current.values.tags).toEqual([])
      expect(result.current.errors).toEqual({})
    })

    it('resetForm clears the sessionStorage draft key', () => {
      sessionStorage.setItem(DRAFT_FORM_KEY, JSON.stringify({ title: 'Quelque chose' }))

      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => { result.current.resetForm() })

      expect(sessionStorage.getItem(DRAFT_FORM_KEY)).toBeNull()
    })
  })

  describe('recurrence (SCRUM-151)', () => {
    function fillRequired(hook: ReturnType<typeof useEventForm>) {
      hook.setFieldValue('title', 'Cours hebdo')
      hook.setFieldValue('location', 'Uni Mail')
      hook.setFieldValue('startDate', '2099-09-21T10:00')
      hook.setFieldValue('endDate', '2099-09-21T12:00')
      hook.setFieldValue('category', 'ACADEMIC')
    }

    it('defaults the recurrence block with enabled=false and WEEKLY frequency', () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))
      expect(result.current.values.recurrence).toEqual({
        enabled: false,
        frequency: 'WEEKLY',
        endMode: 'date',
        endDate: '',
        maxOccurrences: '',
      })
    })

    it('persists the recurrence block to sessionStorage via setFieldValue', () => {
      vi.useFakeTimers()
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        result.current.setFieldValue('recurrence', {
          ...result.current.values.recurrence,
          enabled: true,
          frequency: 'BIWEEKLY',
          endMode: 'count',
          maxOccurrences: '10',
        })
      })

      act(() => { vi.advanceTimersByTime(DRAFT_FORM_PERSIST_DEBOUNCE_MS + 10) })

      const persisted = JSON.parse(sessionStorage.getItem(DRAFT_FORM_KEY) ?? '{}')
      expect(persisted.recurrence).toMatchObject({
        enabled: true,
        frequency: 'BIWEEKLY',
        endMode: 'count',
        maxOccurrences: '10',
      })
    })

    it('hydrates a sessionStorage payload missing the recurrence sub-object back to defaults', () => {
      sessionStorage.setItem(DRAFT_FORM_KEY, JSON.stringify({ title: 'Legacy draft' }))

      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      expect(result.current.values.title).toBe('Legacy draft')
      expect(result.current.values.recurrence).toEqual({
        enabled: false,
        frequency: 'WEEKLY',
        endMode: 'date',
        endDate: '',
        maxOccurrences: '',
      })
    })

    it('reports a single recurrence error when enabled with neither endDate nor maxOccurrences set', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillRequired(result.current)
        result.current.setFieldValue('recurrence', {
          ...result.current.values.recurrence,
          enabled: true,
          endMode: 'date',
          endDate: '',
        })
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.recurrence).toBe("Définissez une date de fin OU un nombre d'occurrences.")
      expect(mockCreateEvent).not.toHaveBeenCalled()
    })

    it('rejects an endDate earlier than the start date', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillRequired(result.current)
        result.current.setFieldValue('recurrence', {
          ...result.current.values.recurrence,
          enabled: true,
          endMode: 'date',
          endDate: '2099-09-20',
        })
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.recurrence).toBe('La date de fin de récurrence doit être postérieure à la date de début.')
    })

    it('rejects a maxOccurrences value outside [1, 52]', async () => {
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillRequired(result.current)
        result.current.setFieldValue('recurrence', {
          ...result.current.values.recurrence,
          enabled: true,
          endMode: 'count',
          maxOccurrences: '53',
        })
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(result.current.errors.recurrence).toMatch(/entier entre 1 et 52/)
      expect(mockCreateEvent).not.toHaveBeenCalled()
    })

    it('serialises an endDate-bound recurrence into the create payload', async () => {
      mockCreateEvent.mockResolvedValue(baseEvent)
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillRequired(result.current)
        result.current.setFieldValue('recurrence', {
          ...result.current.values.recurrence,
          enabled: true,
          frequency: 'WEEKLY',
          endMode: 'date',
          endDate: '2099-12-15',
        })
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(mockCreateEvent).toHaveBeenCalledWith(expect.objectContaining({
        recurrence: { frequency: 'WEEKLY', endDate: '2099-12-15' },
      }))
    })

    it('serialises a count-bound recurrence into a numeric maxOccurrences', async () => {
      mockCreateEvent.mockResolvedValue(baseEvent)
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => {
        fillRequired(result.current)
        result.current.setFieldValue('recurrence', {
          ...result.current.values.recurrence,
          enabled: true,
          frequency: 'MONTHLY',
          endMode: 'count',
          maxOccurrences: '6',
        })
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      const [, payload] = mockCreateEvent.mock.calls[0] ? [null, mockCreateEvent.mock.calls[0][0]] : [null, null]
      expect(payload).toMatchObject({ recurrence: { frequency: 'MONTHLY', maxOccurrences: 6 } })
      expect(payload?.recurrence?.endDate).toBeUndefined()
    })

    it('omits payload.recurrence when the switch is off', async () => {
      mockCreateEvent.mockResolvedValue(baseEvent)
      const { result } = renderHook(() => useEventForm({ mode: 'create' }))

      act(() => { fillRequired(result.current) })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      const payload = mockCreateEvent.mock.calls[0][0]
      expect(payload.recurrence).toBeUndefined()
    })

    it('never carries a recurrence payload in edit mode even with enabled state', async () => {
      mockUpdateEvent.mockResolvedValue(baseEvent)
      const { result } = renderHook(() => useEventForm({ mode: 'edit', initialEvent: baseEvent }))

      // The UI never exposes the section in edit mode, but if the state somehow
      // arrived enabled (e.g. from a hand-crafted sessionStorage payload), the
      // PUT must still omit `recurrence` — confirms Décision E end-to-end.
      act(() => {
        result.current.setFieldValue('recurrence', {
          ...result.current.values.recurrence,
          enabled: true,
          endMode: 'count',
          maxOccurrences: '5',
        })
      })

      await act(async () => { await result.current.handleSubmit(submitEvent()) })

      expect(mockUpdateEvent).toHaveBeenCalledTimes(1)
      const [, updatePayload] = mockUpdateEvent.mock.calls[0]
      expect((updatePayload as { recurrence?: unknown }).recurrence).toBeUndefined()
    })
  })
})
