// @vitest-environment jsdom

import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { FormEvent } from 'react'
import {
  EVENT_DESCRIPTION_MAX_LENGTH,
  EVENT_TITLE_MAX_LENGTH,
  IMAGE_MAX_SIZE_BYTES,
  IMAGE_MAX_SIZE_MB,
  useEventForm,
} from '../../hooks/useEventForm'

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

afterEach(() => {
  vi.useRealTimers()
  vi.resetAllMocks()
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

  it('handles image validation, preview replacement, and cleanup', () => {
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

    act(() => {
      result.current.handleImageChange({ target: { files: [] } } as never)
    })

    expect(createObjectURL).not.toHaveBeenCalled()

    act(() => {
      result.current.handleImageChange({ target: { files: [new File(['a'], 'banner-a.png', { type: 'image/png' })] } } as never)
    })

    expect(result.current.imagePreview).toBe('blob:first')
    expect(result.current.selectedImageName).toBe('banner-a.png')
    expect(result.current.errors.image).toBeUndefined()

    act(() => {
      result.current.handleImageChange({ target: { files: [new File(['b'], 'banner-b.png', { type: 'image/png' })] } } as never)
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

  it('resets to incoming event values and uses the uploaded event response', async () => {
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
      result.current.handleImageChange({ target: { files: [new File(['img'], 'banner.png', { type: 'image/png' })] } } as never)
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
})
