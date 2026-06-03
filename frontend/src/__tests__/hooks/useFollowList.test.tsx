// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { AxiosError, AxiosHeaders } from 'axios'

vi.mock('@/services/followApi', async () => {
  const actual = await vi.importActual<typeof import('@/services/followApi')>('@/services/followApi')
  return {
    ...actual,
    getFollowers: vi.fn(),
    getFollowing: vi.fn(),
    removeFollower: vi.fn(),
  }
})

import { FOLLOW_LIST_PAGE_SIZE, getFollowers, getFollowing, removeFollower } from '@/services/followApi'
import { useFollowList } from '@/hooks/useFollowList'
import type { UserPublicResponse } from '@/types/user'

const mockGetFollowers = getFollowers as ReturnType<typeof vi.fn>
const mockGetFollowing = getFollowing as ReturnType<typeof vi.fn>
const mockRemoveFollower = removeFollower as ReturnType<typeof vi.fn>

afterEach(() => {
  vi.resetAllMocks()
})

function makeUser(suffix: string): UserPublicResponse {
  return {
    id: `${suffix}-id`,
    username: `user-${suffix}`,
    displayName: `User ${suffix}`,
    profilePublic: true,
    followerCount: 0,
    followingCount: 0,
    followStatus: null,
  }
}

function makeFullPage(prefix: string): UserPublicResponse[] {
  return Array.from({ length: FOLLOW_LIST_PAGE_SIZE }, (_, i) =>
    makeUser(`${prefix}-${i}`),
  )
}

describe('useFollowList', () => {
  it('stays in loading state without firing a request when targetId is undefined', async () => {
    const { result } = renderHook(() => useFollowList(undefined, 'followers'))

    expect(result.current.loading).toBe(true)
    expect(mockGetFollowers).not.toHaveBeenCalled()
    expect(mockGetFollowing).not.toHaveBeenCalled()
  })

  it('fetches /followers on initial mount in followers mode', async () => {
    mockGetFollowers.mockResolvedValue([makeUser('a')])

    const { result } = renderHook(() => useFollowList('target-id', 'followers'))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetFollowers).toHaveBeenCalledWith('target-id', 0, FOLLOW_LIST_PAGE_SIZE)
    expect(result.current.users.map(u => u.username)).toEqual(['user-a'])
    expect(mockGetFollowing).not.toHaveBeenCalled()
  })

  it('fetches /following in following mode', async () => {
    mockGetFollowing.mockResolvedValue([makeUser('b')])

    const { result } = renderHook(() => useFollowList('target-id', 'following'))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetFollowing).toHaveBeenCalledWith('target-id', 0, FOLLOW_LIST_PAGE_SIZE)
    expect(result.current.users.map(u => u.username)).toEqual(['user-b'])
    expect(mockGetFollowers).not.toHaveBeenCalled()
  })

  it('hasMore is true when the page is full, false otherwise', async () => {
    mockGetFollowers.mockResolvedValue(makeFullPage('p'))

    const { result } = renderHook(() => useFollowList('target', 'followers'))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.hasMore).toBe(true)
  })

  it('hasMore is false when the first page is short', async () => {
    mockGetFollowers.mockResolvedValue([makeUser('a'), makeUser('b')])

    const { result } = renderHook(() => useFollowList('target', 'followers'))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.hasMore).toBe(false)
  })

  it('loadMore appends the next page and updates hasMore', async () => {
    const firstPage = makeFullPage('p1')
    const secondPage = [makeUser('tail-1'), makeUser('tail-2')]
    mockGetFollowers
      .mockResolvedValueOnce(firstPage)
      .mockResolvedValueOnce(secondPage)

    const { result } = renderHook(() => useFollowList('target', 'followers'))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.users).toHaveLength(FOLLOW_LIST_PAGE_SIZE)
    expect(result.current.hasMore).toBe(true)

    act(() => { result.current.loadMore() })

    await waitFor(() => expect(result.current.loadingMore).toBe(false))
    expect(mockGetFollowers).toHaveBeenNthCalledWith(2, 'target', 1, FOLLOW_LIST_PAGE_SIZE)
    expect(result.current.users).toHaveLength(FOLLOW_LIST_PAGE_SIZE + 2)
    expect(result.current.users.at(-1)?.username).toBe('user-tail-2')
    expect(result.current.hasMore).toBe(false)
  })

  it('refetches and resets state when targetId changes', async () => {
    mockGetFollowers
      .mockResolvedValueOnce([makeUser('a')])
      .mockResolvedValueOnce([makeUser('b')])

    const { result, rerender } = renderHook(
      ({ id }) => useFollowList(id, 'followers'),
      { initialProps: { id: 't1' } },
    )

    await waitFor(() => expect(result.current.users.map(u => u.id)).toEqual(['a-id']))

    rerender({ id: 't2' })

    await waitFor(() => expect(result.current.users.map(u => u.id)).toEqual(['b-id']))
    expect(mockGetFollowers).toHaveBeenNthCalledWith(2, 't2', 0, FOLLOW_LIST_PAGE_SIZE)
  })

  it('switches endpoint when mode flips', async () => {
    mockGetFollowers.mockResolvedValue([makeUser('a')])
    mockGetFollowing.mockResolvedValue([makeUser('b')])

    const { result, rerender } = renderHook(
      ({ mode }) => useFollowList('target', mode),
      { initialProps: { mode: 'followers' as 'followers' | 'following' } },
    )

    await waitFor(() => expect(result.current.users.map(u => u.id)).toEqual(['a-id']))

    rerender({ mode: 'following' })

    await waitFor(() => expect(result.current.users.map(u => u.id)).toEqual(['b-id']))
    expect(mockGetFollowing).toHaveBeenCalledTimes(1)
  })

  it('surfaces isNotFound on 404 without an error message', async () => {
    const err = new AxiosError(
      'not found',
      undefined,
      undefined,
      undefined,
      { status: 404, data: {}, statusText: 'Not Found', headers: {}, config: { headers: new AxiosHeaders() } },
    )
    mockGetFollowers.mockRejectedValue(err)

    const { result } = renderHook(() => useFollowList('private', 'followers'))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.isNotFound).toBe(true)
    expect(result.current.error).toBeNull()
    expect(result.current.users).toEqual([])
  })

  it('surfaces a mode-specific error message on non-404 failures', async () => {
    mockGetFollowers.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useFollowList('target', 'followers'))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.isNotFound).toBe(false)
    expect(result.current.error).toBe('Impossible de charger les followers.')
  })

  it('uses the abonnements label on following error', async () => {
    mockGetFollowing.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useFollowList('target', 'following'))

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.error).toBe('Impossible de charger les abonnements.')
  })

  it('discards a stale resolve from a prior targetId', async () => {
    let resolveFirst: (v: UserPublicResponse[]) => void = () => {}
    mockGetFollowers
      .mockImplementationOnce(() => new Promise<UserPublicResponse[]>(r => { resolveFirst = r }))
      .mockResolvedValueOnce([makeUser('b')])

    const { result, rerender } = renderHook(
      ({ id }) => useFollowList(id, 'followers'),
      { initialProps: { id: 't1' } },
    )

    rerender({ id: 't2' })

    await waitFor(() => expect(result.current.users.map(u => u.id)).toEqual(['b-id']))

    // Late resolve of the t1 fetch must NOT clobber the t2 result.
    resolveFirst([makeUser('stale')])
    await new Promise(r => setTimeout(r, 20))

    expect(result.current.users.map(u => u.id)).toEqual(['b-id'])
  })

  it('discards a stale rejection from a prior targetId (catch requestId guard)', async () => {
    let rejectFirst: (e: Error) => void = () => {}
    mockGetFollowers
      .mockImplementationOnce(() => new Promise<UserPublicResponse[]>((_, r) => { rejectFirst = r }))
      .mockResolvedValueOnce([makeUser('b')])

    const { result, rerender } = renderHook(
      ({ id }) => useFollowList(id, 'followers'),
      { initialProps: { id: 't1' } },
    )

    rerender({ id: 't2' })

    await waitFor(() => expect(result.current.users.map(u => u.id)).toEqual(['b-id']))

    // Late rejection of the t1 fetch hits the catch `requestIdRef !== myRequestId`
    // guard (line 109) — it must NOT set error/isNotFound for the live t2 list.
    rejectFirst(new Error('stale failure'))
    await new Promise(r => setTimeout(r, 20))

    expect(result.current.users.map(u => u.id)).toEqual(['b-id'])
    expect(result.current.error).toBeNull()
    expect(result.current.isNotFound).toBe(false)
  })

  it('remove() optimistically drops the row and calls removeFollower', async () => {
    mockGetFollowers.mockResolvedValue([makeUser('a'), makeUser('b')])
    mockRemoveFollower.mockResolvedValue(undefined)
    const { result } = renderHook(() => useFollowList('target', 'followers'))
    await waitFor(() => expect(result.current.users.length).toBe(2))

    await act(async () => { await result.current.remove('a-id') })

    expect(mockRemoveFollower).toHaveBeenCalledWith('a-id')
    expect(result.current.users.map(u => u.id)).toEqual(['b-id'])
  })

  it('remove() restores the row and rethrows when the API rejects', async () => {
    mockGetFollowers.mockResolvedValue([makeUser('a')])
    mockRemoveFollower.mockRejectedValue(new Error('boom'))
    const { result } = renderHook(() => useFollowList('target', 'followers'))
    await waitFor(() => expect(result.current.users.length).toBe(1))

    await expect(
      act(async () => { await result.current.remove('a-id') }),
    ).rejects.toThrow('boom')

    // Optimistic removal rolled back — the row is back.
    expect(result.current.users.map(u => u.id)).toEqual(['a-id'])
  })

  it('refresh resets page, reloadKey, loading, and error', async () => {
    mockGetFollowers.mockResolvedValue([makeUser('a')])
    const { result } = renderHook(() => useFollowList('target', 'followers'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => {
      result.current.refresh()
    })

    expect(result.current.loading).toBe(true)
  })
})
