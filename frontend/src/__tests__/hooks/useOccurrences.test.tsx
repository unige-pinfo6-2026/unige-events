import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useOccurrences } from '@/hooks/useOccurrences'

vi.mock('@/services/eventApi', () => ({
  getOccurrences: vi.fn(),
}))

import { getOccurrences } from '@/services/eventApi'

const mockGetOccurrences = getOccurrences as ReturnType<typeof vi.fn>

const baseOccurrence = {
  id: 101,
  title: 'Cours hebdo',
  location: 'Uni Mail',
  startDate: '2026-09-21T09:00:00.000Z',
  endDate: '2026-09-21T11:00:00.000Z',
  category: 'ACADEMIC',
  status: 'PUBLISHED',
  creatorId: 'u1',
  createdAt: '2026-05-15T08:00:00.000Z',
  parentEventId: 42,
}

afterEach(() => {
  vi.resetAllMocks()
})

describe('useOccurrences (SCRUM-151)', () => {
  it('stays idle and never fetches when enabled is false', () => {
    const { result } = renderHook(() => useOccurrences(42, { enabled: false }))
    expect(result.current).toEqual({ loading: false, error: null, data: null })
    expect(mockGetOccurrences).not.toHaveBeenCalled()
  })

  it('fetches and exposes data when enabled flips true', async () => {
    mockGetOccurrences.mockResolvedValue([baseOccurrence, { ...baseOccurrence, id: 102 }])

    const { result } = renderHook(() => useOccurrences(42, { enabled: true }))

    expect(result.current.loading).toBe(true)

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetOccurrences).toHaveBeenCalledWith(42)
    expect(result.current.data).toHaveLength(2)
    expect(result.current.error).toBeNull()
  })

  it('does not fetch when parentId is null even with enabled true', () => {
    renderHook(() => useOccurrences(null, { enabled: true }))
    expect(mockGetOccurrences).not.toHaveBeenCalled()
  })

  it('exposes a human-readable error string when the request fails', async () => {
    mockGetOccurrences.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useOccurrences(42, { enabled: true }))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les occurrences.')
    expect(result.current.data).toBeNull()
  })

  it('refetches when parentId changes while enabled', async () => {
    const second = { ...baseOccurrence, id: 201, parentEventId: 99 }
    mockGetOccurrences
      .mockResolvedValueOnce([baseOccurrence])
      .mockResolvedValueOnce([second])

    const { result, rerender } = renderHook(
      ({ id }: { id: number }) => useOccurrences(id, { enabled: true }),
      { initialProps: { id: 42 } },
    )

    await waitFor(() => expect(result.current.data?.[0]?.id).toBe(101))

    rerender({ id: 99 })

    await waitFor(() => expect(result.current.data?.[0]?.id).toBe(201))
    expect(mockGetOccurrences).toHaveBeenNthCalledWith(1, 42)
    expect(mockGetOccurrences).toHaveBeenNthCalledWith(2, 99)
  })

  it('drops the result of an in-flight request after unmount (no setState warning)', async () => {
    let resolveFetch: (value: typeof baseOccurrence[]) => void = () => {}
    mockGetOccurrences.mockImplementation(() => new Promise((res) => { resolveFetch = res }))

    const { unmount } = renderHook(() => useOccurrences(42, { enabled: true }))
    unmount()
    resolveFetch([baseOccurrence])

    // Give the resolution microtask a tick to run — if the hook still wrote to
    // state, vitest's console assertions would surface the React warning.
    await new Promise((res) => setTimeout(res, 0))
    expect(mockGetOccurrences).toHaveBeenCalledTimes(1)
  })
})
