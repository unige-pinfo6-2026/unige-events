// @vitest-environment jsdom

import { renderHook, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useUserParticipations } from '@/hooks/useUserParticipations'
import type { Event } from '@/types/event'

vi.mock('@/services/attendanceApi', () => ({
  getUserParticipations: vi.fn(),
}))

import { getUserParticipations } from '@/services/attendanceApi'

const mockGet = getUserParticipations as ReturnType<typeof vi.fn>

const sampleEvent: Event = {
  id: 1,
  title: 'Atelier',
  location: 'Uni Mail',
  startDate: '2026-06-01T18:00:00',
  endDate: '2026-06-01T20:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'u-2',
  createdAt: '2026-03-01T10:00:00',
  allDay: false,
  attendingCount: 3,
}

beforeEach(() => { mockGet.mockReset() })
afterEach(() => { vi.clearAllMocks() })

describe('useUserParticipations', () => {
  it('stays loading and does not fetch when userId is undefined', () => {
    const { result } = renderHook(() => useUserParticipations(undefined))

    expect(result.current.loading).toBe(true)
    expect(result.current.events).toEqual([])
    expect(result.current.error).toBeNull()
    expect(mockGet).not.toHaveBeenCalled()
  })

  it('loads the participations for a userId', async () => {
    mockGet.mockResolvedValue([sampleEvent])

    const { result } = renderHook(() => useUserParticipations('u-1'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGet).toHaveBeenCalledWith('u-1')
    expect(result.current.events).toEqual([sampleEvent])
    expect(result.current.error).toBeNull()
  })

  it('sets an error message when the fetch rejects', async () => {
    mockGet.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useUserParticipations('u-1'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les participations.')
    expect(result.current.events).toEqual([])
  })

  it('refetches when the userId changes', async () => {
    mockGet.mockResolvedValue([])
    const { result, rerender } = renderHook(
      ({ id }: { id: string | undefined }) => useUserParticipations(id),
      { initialProps: { id: 'u-1' as string | undefined } },
    )
    await waitFor(() => expect(result.current.loading).toBe(false))

    mockGet.mockResolvedValue([sampleEvent])
    rerender({ id: 'u-2' })
    await waitFor(() => expect(result.current.events).toEqual([sampleEvent]))
    expect(mockGet).toHaveBeenCalledWith('u-2')
  })

  it('ignores a resolved fetch after unmount (cancelled guard)', async () => {
    let resolve!: (v: Event[]) => void
    mockGet.mockReturnValue(new Promise<Event[]>(r => { resolve = r }))

    const { unmount } = renderHook(() => useUserParticipations('u-1'))
    unmount()
    resolve([sampleEvent])
    // The cancelled guard must swallow the post-unmount resolution (no setState
    // on an unmounted component). Flush the microtask queue so the .then runs.
    await Promise.resolve()
  })
})
