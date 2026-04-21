// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useMyDrafts } from '@/hooks/useMyDrafts'
import type { Event } from '@/types/event'

vi.mock('@/services/eventApi', () => ({
  getMyDrafts: vi.fn(),
}))

import { getMyDrafts } from '@/services/eventApi'

const mockGetMyDrafts = getMyDrafts as ReturnType<typeof vi.fn>

function makeDraft(overrides: Partial<Event>): Event {
  return {
    id: 1,
    title: 'Draft',
    location: 'Uni',
    startDate: '2026-05-01T10:00:00.000Z',
    endDate: '2026-05-01T11:00:00.000Z',
    category: 'ACADEMIC',
    creatorId: 'uuid',
    status: 'DRAFT',
    attendingCount: 0,
    allDay: false,
    createdAt: '2026-04-01T00:00:00.000Z',
    ...overrides,
  } as Event
}

beforeEach(() => {
  mockGetMyDrafts.mockReset()
  vi.spyOn(console, 'warn').mockImplementation(() => {})
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('useMyDrafts', () => {
  it('stays in loading without fetching when organizerId is undefined', () => {
    const { result } = renderHook(() => useMyDrafts(undefined))
    expect(result.current.loading).toBe(true)
    expect(result.current.drafts).toEqual([])
    expect(result.current.error).toBeNull()
    expect(mockGetMyDrafts).not.toHaveBeenCalled()
  })

  it('returns fetched drafts on success', async () => {
    const data = [makeDraft({ id: 1, updatedAt: '2026-04-10T10:00:00.000Z' })]
    mockGetMyDrafts.mockResolvedValue(data)
    const { result } = renderHook(() => useMyDrafts('uuid-1'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.drafts).toHaveLength(1)
    expect(result.current.error).toBeNull()
    expect(mockGetMyDrafts).toHaveBeenCalledWith(10)
  })

  it('returns the full fetched pool without truncation', async () => {
    mockGetMyDrafts.mockResolvedValue(
      Array.from({ length: 8 }, (_, i) =>
        makeDraft({ id: i + 1, updatedAt: `2026-04-${String(i + 1).padStart(2, '0')}T10:00:00.000Z` }),
      ),
    )
    const { result } = renderHook(() => useMyDrafts('uuid-1'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.drafts).toHaveLength(8)
  })

  it('sorts drafts by updatedAt DESC', async () => {
    mockGetMyDrafts.mockResolvedValue([
      makeDraft({ id: 1, updatedAt: '2026-04-01T10:00:00.000Z' }),
      makeDraft({ id: 2, updatedAt: '2026-04-10T10:00:00.000Z' }),
      makeDraft({ id: 3, updatedAt: '2026-04-05T10:00:00.000Z' }),
    ])
    const { result } = renderHook(() => useMyDrafts('uuid-1'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.drafts.map(d => d.id)).toEqual([2, 3, 1])
  })

  it('falls back to createdAt when updatedAt is missing', async () => {
    mockGetMyDrafts.mockResolvedValue([
      makeDraft({ id: 1, createdAt: '2026-04-01T10:00:00.000Z' }),
      makeDraft({ id: 2, createdAt: '2026-04-10T10:00:00.000Z' }),
    ])
    const { result } = renderHook(() => useMyDrafts('uuid-1'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.drafts.map(d => d.id)).toEqual([2, 1])
  })

  it('captures the error when fetching fails', async () => {
    mockGetMyDrafts.mockRejectedValue(new Error('boom'))
    const { result } = renderHook(() => useMyDrafts('uuid-1'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBe('Erreur de chargement des brouillons')
    expect(result.current.drafts).toEqual([])
  })

  it('does not set state after unmount', async () => {
    let resolve: (v: Event[]) => void = () => {}
    mockGetMyDrafts.mockReturnValue(
      new Promise<Event[]>(r => {
        resolve = r
      }),
    )
    const { result, unmount } = renderHook(() => useMyDrafts('uuid-1'))
    unmount()
    resolve([makeDraft({ id: 99 })])
    await new Promise(r => setTimeout(r, 0))
    expect(result.current.drafts).toEqual([])
  })
})
