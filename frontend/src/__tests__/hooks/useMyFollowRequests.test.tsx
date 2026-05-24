// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'

vi.mock('@/services/followApi', () => ({
  getMyFollowRequests: vi.fn(),
  acceptFollowRequest: vi.fn(),
  rejectFollowRequest: vi.fn(),
}))

vi.mock('@/services/userService', () => ({
  getPublicProfile: vi.fn(),
}))

import {
  acceptFollowRequest,
  getMyFollowRequests,
  rejectFollowRequest,
} from '@/services/followApi'
import { getPublicProfile } from '@/services/userService'
import { useMyFollowRequests } from '@/hooks/useMyFollowRequests'

const mockGetRequests = getMyFollowRequests as ReturnType<typeof vi.fn>
const mockAccept = acceptFollowRequest as ReturnType<typeof vi.fn>
const mockReject = rejectFollowRequest as ReturnType<typeof vi.fn>
const mockGetProfile = getPublicProfile as ReturnType<typeof vi.fn>

afterEach(() => {
  vi.resetAllMocks()
})

const aliceRequest = {
  id: 1,
  followerId: 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c',
  followedId: 'me',
  status: 'PENDING' as const,
  createdAt: '2026-05-14T10:00:00Z',
}
const aliceProfile = {
  id: aliceRequest.followerId,
  displayName: 'Alice',
  followerCount: 0,
  followingCount: 0,
  followStatus: null,
}

const bobRequest = {
  id: 2,
  followerId: 'b1b1b1b1-b1b1-4b1b-9b1b-b1b1b1b1b1b1',
  followedId: 'me',
  status: 'PENDING' as const,
  createdAt: '2026-05-14T10:00:00Z',
}
const bobProfile = {
  id: bobRequest.followerId,
  displayName: 'Bob',
  followerCount: 0,
  followingCount: 0,
  followStatus: null,
}

describe('useMyFollowRequests', () => {
  it('fetches requests and resolves the follower profile per row', async () => {
    mockGetRequests.mockResolvedValue([aliceRequest, bobRequest])
    mockGetProfile.mockImplementation((id: string) =>
      Promise.resolve(id === aliceProfile.id ? aliceProfile : bobProfile),
    )

    const { result } = renderHook(() => useMyFollowRequests())

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.rows.map(r => r.follower?.displayName)).toEqual(['Alice', 'Bob'])
    expect(result.current.error).toBeNull()
  })

  it('falls back to follower=null when /users/{id} rejects (doesn\'t fail the batch)', async () => {
    mockGetRequests.mockResolvedValue([aliceRequest, bobRequest])
    mockGetProfile.mockImplementation((id: string) =>
      id === bobProfile.id ? Promise.reject(new Error('boom')) : Promise.resolve(aliceProfile),
    )

    const { result } = renderHook(() => useMyFollowRequests())

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.rows[0].follower?.displayName).toBe('Alice')
    expect(result.current.rows[1].follower).toBeNull()
  })

  it('surfaces an error message when the list fetch fails', async () => {
    mockGetRequests.mockRejectedValue(new Error('network'))

    const { result } = renderHook(() => useMyFollowRequests())

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBe('Impossible de charger les demandes de suivi.')
    expect(result.current.rows).toEqual([])
  })

  it('accept() optimistically removes the row then refreshes', async () => {
    mockGetRequests
      .mockResolvedValueOnce([aliceRequest, bobRequest])
      .mockResolvedValueOnce([bobRequest])
    mockGetProfile.mockImplementation((id: string) =>
      Promise.resolve(id === aliceProfile.id ? aliceProfile : bobProfile),
    )
    mockAccept.mockResolvedValue({ ...aliceRequest, status: 'ACCEPTED' })

    const { result } = renderHook(() => useMyFollowRequests())
    await waitFor(() => expect(result.current.rows.length).toBe(2))

    await act(async () => {
      await result.current.accept(aliceRequest.id)
    })

    expect(mockAccept).toHaveBeenCalledWith(aliceRequest.id)
    await waitFor(() => expect(result.current.rows.length).toBe(1))
    expect(result.current.rows[0].request.id).toBe(bobRequest.id)
  })

  it('reject() optimistically removes the row then refreshes', async () => {
    mockGetRequests
      .mockResolvedValueOnce([aliceRequest])
      .mockResolvedValueOnce([])
    mockGetProfile.mockResolvedValue(aliceProfile)
    mockReject.mockResolvedValue(undefined)

    const { result } = renderHook(() => useMyFollowRequests())
    await waitFor(() => expect(result.current.rows.length).toBe(1))

    await act(async () => {
      await result.current.reject(aliceRequest.id)
    })

    expect(mockReject).toHaveBeenCalledWith(aliceRequest.id)
    await waitFor(() => expect(result.current.rows.length).toBe(0))
  })

  it('accept() rolls back when the API rejects', async () => {
    mockGetRequests.mockResolvedValue([aliceRequest])
    mockGetProfile.mockResolvedValue(aliceProfile)
    mockAccept.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useMyFollowRequests())
    await waitFor(() => expect(result.current.rows.length).toBe(1))

    await expect(
      act(async () => { await result.current.accept(aliceRequest.id) }),
    ).rejects.toThrow('boom')

    expect(result.current.rows.length).toBe(1)
  })

  it('reject() rolls back and rethrows when the API rejects (lines 102-103)', async () => {
    mockGetRequests.mockResolvedValue([aliceRequest])
    mockGetProfile.mockResolvedValue(aliceProfile)
    mockReject.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useMyFollowRequests())
    await waitFor(() => expect(result.current.rows.length).toBe(1))

    await expect(
      act(async () => { await result.current.reject(aliceRequest.id) }),
    ).rejects.toThrow('boom')

    // Optimistic removal was rolled back — the row is back.
    expect(result.current.rows.length).toBe(1)
    expect(result.current.rows[0].request.id).toBe(aliceRequest.id)
  })

  it('refresh() re-fetches the list', async () => {
    mockGetRequests
      .mockResolvedValueOnce([aliceRequest])
      .mockResolvedValueOnce([aliceRequest, bobRequest])
    mockGetProfile.mockImplementation((id: string) =>
      Promise.resolve(id === aliceProfile.id ? aliceProfile : bobProfile),
    )

    const { result } = renderHook(() => useMyFollowRequests())
    await waitFor(() => expect(result.current.rows.length).toBe(1))

    act(() => { result.current.refresh() })
    await waitFor(() => expect(result.current.rows.length).toBe(2))
  })

  it('discards a stale resolve on unmount', async () => {
    let resolveFn: (v: unknown) => void = () => {}
    mockGetRequests.mockImplementation(() => new Promise(resolve => { resolveFn = resolve }))

    const errorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    const { unmount } = renderHook(() => useMyFollowRequests())

    unmount()
    resolveFn([aliceRequest])
    await new Promise(r => setTimeout(r, 20))

    expect(errorSpy).not.toHaveBeenCalled()
    errorSpy.mockRestore()
  })
})
