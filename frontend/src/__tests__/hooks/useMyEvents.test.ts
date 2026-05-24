
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act } from 'react'
import { renderHook, waitFor } from '@testing-library/react'
import { useMyEvents } from '@/hooks/useMyEvents'

vi.mock('@/services/eventApi', () => ({
  getMyEvents: vi.fn(),
  publishEvent: vi.fn(),
  deleteEvent: vi.fn(),
  cancelEvent: vi.fn(),
  restoreEvent: vi.fn(),
}))

import { getMyEvents, publishEvent, deleteEvent, cancelEvent, restoreEvent } from '@/services/eventApi'

const mockGetMyEvents = getMyEvents as ReturnType<typeof vi.fn>
const mockPublishEvent = publishEvent as ReturnType<typeof vi.fn>
const mockDeleteEvent = deleteEvent as ReturnType<typeof vi.fn>
const mockCancelEvent = cancelEvent as ReturnType<typeof vi.fn>
const mockRestoreEvent = restoreEvent as ReturnType<typeof vi.fn>

const makeMockEvent = (id: number, startDate: string) => ({
  id,
  title: `Event ${id}`,
  location: 'Location',
  startDate,
  endDate: '2026-04-10T17:00:00',
  allDay: false,
  category: 'CONFERENCE' as const,
  faculty: null,
  status: 'PUBLISHED' as const,
  creatorId: 'org-1',
  createdAt: '2026-03-01T10:00:00',
  description: '',
  capacity: 100,
  attendingCount: 0,
  bannerUrl: '',
})

afterEach(() => vi.resetAllMocks())

describe('useMyEvents', () => {
  it('fetches events on mount with the status param', async () => {
    mockGetMyEvents.mockResolvedValue([
      makeMockEvent(1, '2026-04-10T14:00:00'),
      makeMockEvent(2, '2026-04-11T14:00:00'),
    ])

    const { result } = renderHook(() => useMyEvents('PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events).toHaveLength(2)
    expect(result.current.error).toBeNull()
    expect(mockGetMyEvents).toHaveBeenCalledWith({
      status: 'PUBLISHED',
      size: 100,
    })
  })

  it('does not sort events on the client — trusts server-side createdAt DESC ordering', async () => {
    const serverOrder = [
      makeMockEvent(3, '2026-04-09T14:00:00'),
      makeMockEvent(1, '2026-04-11T14:00:00'),
      makeMockEvent(2, '2026-04-10T14:00:00'),
    ]
    mockGetMyEvents.mockResolvedValue(serverOrder)

    const { result } = renderHook(() => useMyEvents('PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.events.map(e => e.id)).toEqual([3, 1, 2])
  })

  it('never passes organizerId in the request', async () => {
    mockGetMyEvents.mockResolvedValue([])

    renderHook(() => useMyEvents('DRAFT'))
    await waitFor(() => expect(mockGetMyEvents).toHaveBeenCalled())

    const callArg = mockGetMyEvents.mock.calls[0][0] ?? {}
    expect(callArg).not.toHaveProperty('organizerId')
  })

  it('re-fetches when the status changes', async () => {
    mockGetMyEvents.mockResolvedValue([])

    const { rerender } = renderHook(
      ({ status }: { status: 'DRAFT' | 'PUBLISHED' }) => useMyEvents(status),
      { initialProps: { status: 'DRAFT' } },
    )
    await waitFor(() => expect(mockGetMyEvents).toHaveBeenCalledTimes(1))
    expect(mockGetMyEvents).toHaveBeenLastCalledWith({ status: 'DRAFT', size: 100 })

    rerender({ status: 'PUBLISHED' })
    await waitFor(() => expect(mockGetMyEvents).toHaveBeenCalledTimes(2))
    expect(mockGetMyEvents).toHaveBeenLastCalledWith({ status: 'PUBLISHED', size: 100 })
  })

  it('sets error message when fetch fails', async () => {
    mockGetMyEvents.mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useMyEvents('PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger vos événements.')
    expect(result.current.events).toEqual([])
  })

  it('publish() calls publishEvent and removes event from list', async () => {
    const event = makeMockEvent(42, '2026-04-10T14:00:00')
    mockGetMyEvents.mockResolvedValue([event])
    mockPublishEvent.mockResolvedValue({})

    const { result } = renderHook(() => useMyEvents('DRAFT'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(1)

    let publishResult: { ok: boolean } | undefined
    await act(async () => {
      publishResult = await result.current.publish(42)
    })

    expect(mockPublishEvent).toHaveBeenCalledWith(42)
    expect(result.current.events).toHaveLength(0)
    expect(publishResult).toEqual({ ok: true })
  })

  it('cancel() calls cancelEvent and removes event from list', async () => {
    const event = makeMockEvent(42, '2026-04-10T14:00:00')
    mockGetMyEvents.mockResolvedValue([event])
    mockCancelEvent.mockResolvedValue({})

    const { result } = renderHook(() => useMyEvents('PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(1)

    let cancelResult: boolean | undefined
    await act(async () => {
      cancelResult = await result.current.cancel(42)
    })

    expect(mockCancelEvent).toHaveBeenCalledWith(42)
    expect(result.current.events).toHaveLength(0)
    expect(cancelResult).toBe(true)
  })

  it('restore() calls restoreEvent and removes event from list', async () => {
    const event = makeMockEvent(42, '2026-04-10T14:00:00')
    mockGetMyEvents.mockResolvedValue([event])
    mockRestoreEvent.mockResolvedValue({})

    const { result } = renderHook(() => useMyEvents('CANCELLED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.restore(42) })

    expect(mockRestoreEvent).toHaveBeenCalledWith(42)
    expect(result.current.events).toHaveLength(0)
    expect(ok).toBe(true)
  })

  it('restore() returns false when restoreEvent fails', async () => {
    mockGetMyEvents.mockResolvedValue([makeMockEvent(42, '2026-04-10T14:00:00')])
    mockRestoreEvent.mockRejectedValue(new Error('API'))

    const { result } = renderHook(() => useMyEvents('CANCELLED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.restore(42) })

    expect(ok).toBe(false)
    expect(result.current.events).toHaveLength(1)
  })

  it('permanentlyDelete() calls deleteEvent and removes event from list', async () => {
    mockGetMyEvents.mockResolvedValue([makeMockEvent(42, '2026-04-10T14:00:00')])
    mockDeleteEvent.mockResolvedValue(undefined)

    const { result } = renderHook(() => useMyEvents('CANCELLED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.permanentlyDelete(42) })

    expect(mockDeleteEvent).toHaveBeenCalledWith(42)
    expect(result.current.events).toHaveLength(0)
    expect(ok).toBe(true)
  })

  it('permanentlyDelete() returns false when deleteEvent fails', async () => {
    mockGetMyEvents.mockResolvedValue([makeMockEvent(42, '2026-04-10T14:00:00')])
    mockDeleteEvent.mockRejectedValue(new Error('API'))

    const { result } = renderHook(() => useMyEvents('CANCELLED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.permanentlyDelete(42) })

    expect(ok).toBe(false)
    expect(result.current.events).toHaveLength(1)
  })

  it('refresh() re-fetches events', async () => {
    mockGetMyEvents.mockResolvedValue([makeMockEvent(1, '2026-04-10T14:00:00')])

    const { result } = renderHook(() => useMyEvents('PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetMyEvents).toHaveBeenCalledTimes(1)

    act(() => {
      result.current.refresh()
    })

    await waitFor(() => expect(mockGetMyEvents).toHaveBeenCalledTimes(2))
  })

  it('publish() returns ok:false with empty errors for non-axios failure', async () => {
    const event = makeMockEvent(42, '2026-04-10T14:00:00')
    mockGetMyEvents.mockResolvedValue([event])
    mockPublishEvent.mockRejectedValue(new Error('API error'))

    const { result } = renderHook(() => useMyEvents('DRAFT'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(1)

    let publishResult: { ok: boolean; errors?: string[] } | undefined
    await act(async () => {
      publishResult = await result.current.publish(42)
    })

    expect(mockPublishEvent).toHaveBeenCalledWith(42)
    expect(publishResult).toEqual({ ok: false, errors: [] })
    expect(result.current.events).toHaveLength(1)
  })

  it('publish() extracts validation errors from axios 422 response', async () => {
    const event = makeMockEvent(42, '2026-04-10T14:00:00')
    mockGetMyEvents.mockResolvedValue([event])
    const axiosError = Object.assign(new Error('Request failed'), {
      isAxiosError: true,
      response: { status: 422, data: { error: 'validation_failed', errors: ['Err A', 'Err B'] } },
    })
    mockPublishEvent.mockRejectedValue(axiosError)

    const { result } = renderHook(() => useMyEvents('DRAFT'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    let publishResult: { ok: boolean; errors?: string[] } | undefined
    await act(async () => {
      publishResult = await result.current.publish(42)
    })

    expect(publishResult).toEqual({ ok: false, errors: ['Err A', 'Err B'] })
    expect(result.current.events).toHaveLength(1)
  })

  it('publish() returns empty errors when axios response.data.errors is not an array', async () => {
    const event = makeMockEvent(42, '2026-04-10T14:00:00')
    mockGetMyEvents.mockResolvedValue([event])
    // axios error WITH a response body, but `errors` is a string (not Array) —
    // exercises the false branch of `Array.isArray(data.errors)` (line 15).
    const axiosError = Object.assign(new Error('Request failed'), {
      isAxiosError: true,
      response: { status: 422, data: { error: 'validation_failed', errors: 'oops not a list' } },
    })
    mockPublishEvent.mockRejectedValue(axiosError)

    const { result } = renderHook(() => useMyEvents('DRAFT'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    let publishResult: { ok: boolean; errors?: string[] } | undefined
    await act(async () => {
      publishResult = await result.current.publish(42)
    })

    expect(publishResult).toEqual({ ok: false, errors: [] })
    expect(result.current.events).toHaveLength(1)
  })

  it('cancel() returns false when cancelEvent fails', async () => {
    const event = makeMockEvent(42, '2026-04-10T14:00:00')
    mockGetMyEvents.mockResolvedValue([event])
    mockCancelEvent.mockRejectedValue(new Error('API error'))

    const { result } = renderHook(() => useMyEvents('PUBLISHED'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.events).toHaveLength(1)

    let cancelResult: boolean | undefined
    await act(async () => {
      cancelResult = await result.current.cancel(42)
    })

    expect(mockCancelEvent).toHaveBeenCalledWith(42)
    expect(cancelResult).toBe(false)
    expect(result.current.events).toHaveLength(1)
  })
})
