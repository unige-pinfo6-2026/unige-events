// @vitest-environment jsdom

import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { FormEvent } from 'react'
import {
  EVENT_DESCRIPTION_MAX_LENGTH,
  EVENT_TITLE_MAX_LENGTH,
  useEventForm,
} from '../../hooks/useEventForm'
import { EventCategory, EventStatus } from '../../types'

vi.mock('../../services/eventApi', () => ({
  createEvent: vi.fn(),
  updateEvent: vi.fn(),
  uploadEventImage: vi.fn(),
}))

import { createEvent, updateEvent, uploadEventImage } from '../../services/eventApi'

const mockCreateEvent = createEvent as ReturnType<typeof vi.fn>
const mockUpdateEvent = updateEvent as ReturnType<typeof vi.fn>
const mockUploadEventImage = uploadEventImage as ReturnType<typeof vi.fn>

const baseEvent = {
  id: 42,
  title: 'Forum des associations',
  description: 'Rencontrez les associations du campus.',
  location: 'Uni Dufour',
  startDate: '2026-04-10T08:00:00.000Z',
  endDate: '2026-04-10T10:00:00.000Z',
  category: 'SOCIAL' as const,
  creatorId: '8b24e4aa-fdea-4e04-bf56-bdb2ddb7fc11',
  status: 'DRAFT' as const,
  capacity: 120,
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
      result.current.setFieldValue('status', EventStatus.PUBLISHED)
    })

    expect(result.current.errors.title).toBe('Le titre est requis.')
  })

  it('validates invalid dates and capacity before submit', async () => {
    const { result } = renderHook(() => useEventForm({ mode: 'create' }))

    act(() => {
      result.current.setFieldValue('title', 'Forum')
      result.current.setFieldValue('location', 'Uni Dufour')
      result.current.setFieldValue('category', EventCategory.SOCIAL)
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
      result.current.setFieldValue('category', EventCategory.SOCIAL)
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
      result.current.setFieldValue('category', EventCategory.SOCIAL)
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
      result.current.setFieldValue('category', EventCategory.SOCIAL)
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
      capacity: undefined,
      status: 'DRAFT',
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
      result.current.setFieldValue('category', EventCategory.SOCIAL)
      result.current.setFieldValue('status', EventStatus.PUBLISHED)
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
      result.current.setFieldValue('category', EventCategory.SOCIAL)
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
      result.current.setFieldValue('category', EventCategory.SOCIAL)
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
      result.current.setFieldValue('category', EventCategory.SOCIAL)
    })

    await act(async () => {
      await result.current.handleSubmit(submitEvent())
    })

    expect(onError).toHaveBeenCalledWith('La mise à jour de l\'événement a échoué. Veuillez réessayer.')
    expect(mockUpdateEvent).not.toHaveBeenCalled()
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
