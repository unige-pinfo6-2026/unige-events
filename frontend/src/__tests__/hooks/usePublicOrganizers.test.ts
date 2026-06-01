// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'

vi.mock('@/services/eventApi', () => ({
  getOrganizerUuids: vi.fn(),
}))
vi.mock('@/services/userService', () => ({
  getUserById: vi.fn(),
}))

import { usePublicOrganizers } from '@/hooks/usePublicOrganizers'
import { getOrganizerUuids } from '@/services/eventApi'
import { getUserById } from '@/services/userService'

const mockGetOrganizerUuids = vi.mocked(getOrganizerUuids)
const mockGetUserById = vi.mocked(getUserById)

const CREATOR = '00000000-0000-0000-0000-000000000001'
const CO_A = '00000000-0000-0000-0000-00000000000a'
const CO_B = '00000000-0000-0000-0000-00000000000b'

function user(id: string, displayName: string | null, username: string | null) {
  return {
    id,
    auth0Id: 'auth0|' + id,
    email: id + '@test',
    username: username ?? '',
    displayName: displayName ?? undefined,
    avatarUrl: null,
    profilePublic: true,
    createdAt: '2026-01-01',
  } as unknown as Awaited<ReturnType<typeof getUserById>>
}

afterEach(() => {
  vi.clearAllMocks()
})

describe('usePublicOrganizers', () => {
  it('stays idle and returns no co-organizers when eventId is null', () => {
    const { result } = renderHook(() => usePublicOrganizers(null, CREATOR))
    expect(result.current.coOrganizers).toEqual([])
    expect(mockGetOrganizerUuids).not.toHaveBeenCalled()
  })

  it('filters out the creator and resolves each co-organizer name', async () => {
    mockGetOrganizerUuids.mockResolvedValue([CREATOR, CO_A, CO_B])
    mockGetUserById.mockImplementation(async (id: string) =>
      id === CO_A ? user(CO_A, 'Alice', 'alice') : user(CO_B, 'Bob', 'bob'),
    )

    const { result } = renderHook(() => usePublicOrganizers(42, CREATOR))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.coOrganizers).toHaveLength(2)
    expect(result.current.coOrganizers.map((c) => c.displayName).sort()).toEqual(['Alice', 'Bob'])
    // The creator is never looked up nor included.
    expect(mockGetUserById).not.toHaveBeenCalledWith(CREATOR)
  })

  it('keeps a co-organizer row with just the UUID when its user lookup fails', async () => {
    mockGetOrganizerUuids.mockResolvedValue([CREATOR, CO_A])
    mockGetUserById.mockRejectedValue(new Error('404'))

    const { result } = renderHook(() => usePublicOrganizers(42, CREATOR))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.coOrganizers).toEqual([
      { userId: CO_A, displayName: null, username: null, avatarUrl: null },
    ])
  })

  it('returns an empty list (no throw) when organizer-uuids fails', async () => {
    mockGetOrganizerUuids.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => usePublicOrganizers(42, CREATOR))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.coOrganizers).toEqual([])
  })

  it('returns an empty list when the event has no co-organizers (creator only)', async () => {
    mockGetOrganizerUuids.mockResolvedValue([CREATOR])

    const { result } = renderHook(() => usePublicOrganizers(42, CREATOR))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.coOrganizers).toEqual([])
    expect(mockGetUserById).not.toHaveBeenCalled()
  })
})
