import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'

vi.mock('@/services/coOrganizerApi', () => ({
  inviteCoOrganizer: vi.fn(),
  listCoOrganizers: vi.fn(),
  removeCoOrganizer: vi.fn(),
}))

import {
  inviteCoOrganizer,
  listCoOrganizers,
  removeCoOrganizer,
} from '@/services/coOrganizerApi'
import { useCoOrganizers } from '@/hooks/useCoOrganizers'
import type { CoOrganizer } from '@/types/coOrganizer'

const mockInvite = vi.mocked(inviteCoOrganizer)
const mockList = vi.mocked(listCoOrganizers)
const mockRemove = vi.mocked(removeCoOrganizer)

const UUID_ALICE = '00000000-0000-0000-0000-000000000111'
const UUID_BOB = '00000000-0000-0000-0000-000000000222'

const alice: CoOrganizer = {
  id: 1,
  userId: UUID_ALICE,
  displayName: 'Alice',
  avatarUrl: null,
  status: 'PENDING',
  invitedAt: '2026-05-14T10:00:00',
}

const bob: CoOrganizer = {
  id: 2,
  userId: UUID_BOB,
  displayName: 'Bob',
  avatarUrl: null,
  status: 'ACCEPTED',
  invitedAt: '2026-05-14T10:00:00',
}

beforeEach(() => {
  mockInvite.mockReset()
  mockList.mockReset()
  mockRemove.mockReset()
})

afterEach(() => vi.clearAllMocks())

describe('useCoOrganizers', () => {
  it('loads on mount with valid eventId', async () => {
    mockList.mockResolvedValue([alice, bob])

    const { result } = renderHook(() => useCoOrganizers(42))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.coOrganizers).toEqual([alice, bob])
    expect(mockList).toHaveBeenCalledWith(42)
  })

  it('stays idle when eventId is null', async () => {
    const { result } = renderHook(() => useCoOrganizers(null))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.coOrganizers).toEqual([])
    expect(mockList).not.toHaveBeenCalled()
  })

  it('appends an invited co-organizer to the list', async () => {
    mockList.mockResolvedValue([alice])
    mockInvite.mockResolvedValue(bob)

    const { result } = renderHook(() => useCoOrganizers(42))
    await waitFor(() => expect(result.current.loading).toBe(false))

    let outcome: { ok: boolean; error?: string } = { ok: false }
    await act(async () => {
      outcome = await result.current.invite(UUID_BOB)
    })

    expect(outcome.ok).toBe(true)
    expect(result.current.coOrganizers).toEqual([alice, bob])
  })

  it('maps 404 to friendly error and does not append', async () => {
    mockList.mockResolvedValue([alice])
    mockInvite.mockRejectedValue({ response: { status: 404 } })

    const { result } = renderHook(() => useCoOrganizers(42))
    await waitFor(() => expect(result.current.loading).toBe(false))

    let outcome: { ok: boolean; error?: string } = { ok: false }
    await act(async () => {
      outcome = await result.current.invite('bad-uuid')
    })

    expect(outcome.ok).toBe(false)
    expect(outcome.error).toMatch(/introuvable/i)
    expect(result.current.coOrganizers).toEqual([alice])
  })

  it('removes a co-organizer optimistically and confirms server-side', async () => {
    mockList.mockResolvedValue([alice, bob])
    mockRemove.mockResolvedValue(undefined)

    const { result } = renderHook(() => useCoOrganizers(42))
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.remove(UUID_BOB)
    })

    expect(mockRemove).toHaveBeenCalledWith(42, UUID_BOB)
    expect(result.current.coOrganizers).toEqual([alice])
  })

  it('rolls back optimistic remove on error', async () => {
    mockList.mockResolvedValue([alice, bob])
    mockRemove.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useCoOrganizers(42))
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.remove(UUID_BOB)
    })

    expect(result.current.coOrganizers).toEqual([alice, bob])
  })
})
