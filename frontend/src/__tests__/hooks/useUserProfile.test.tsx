// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'

vi.mock('@/services/userService', () => ({
  getPublicProfile: vi.fn(),
}))

import { getPublicProfile } from '@/services/userService'
import { useUserProfile } from '@/hooks/useUserProfile'

const mockGetPublicProfile = getPublicProfile as ReturnType<typeof vi.fn>

afterEach(() => {
  vi.resetAllMocks()
})

const profile = {
  id: 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c',
  displayName: 'Alice',
  faculty: 'SCIENCES',
  studyLevel: 'MASTER',
  bio: 'Bio',
  interests: [],
  avatarUrl: null,
  bannerUrl: null,
  followerCount: 12,
  followingCount: 7,
  followStatus: null,
}

describe('useUserProfile', () => {
  it('does not fetch when id is undefined', async () => {
    const { result } = renderHook(() => useUserProfile(undefined))

    await new Promise(r => setTimeout(r, 10))

    expect(mockGetPublicProfile).not.toHaveBeenCalled()
    expect(result.current.loading).toBe(true)
    expect(result.current.profile).toBeNull()
    expect(result.current.isNotFound).toBe(false)
  })

  it('resolves profile on success', async () => {
    mockGetPublicProfile.mockResolvedValueOnce(profile)

    const { result } = renderHook(() => useUserProfile(profile.id))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.profile).toEqual(profile)
    expect(result.current.isNotFound).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('sets isNotFound when the service returns null (404 — private or missing)', async () => {
    mockGetPublicProfile.mockResolvedValueOnce(null)

    const { result } = renderHook(() => useUserProfile(profile.id))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.profile).toBeNull()
    expect(result.current.isNotFound).toBe(true)
    expect(result.current.error).toBeNull()
  })

  it('sets a French error message on network failure', async () => {
    mockGetPublicProfile.mockRejectedValueOnce(new Error('boom'))

    const { result } = renderHook(() => useUserProfile(profile.id))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger le profil.')
    expect(result.current.profile).toBeNull()
  })

  it('refetches when the id prop changes', async () => {
    mockGetPublicProfile
      .mockResolvedValueOnce(profile)
      .mockResolvedValueOnce({ ...profile, id: 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1', displayName: 'Bob' })

    const { result, rerender } = renderHook(({ id }: { id: string }) => useUserProfile(id), {
      initialProps: { id: profile.id },
    })

    await waitFor(() => expect(result.current.profile?.displayName).toBe('Alice'))

    rerender({ id: 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1' })

    await waitFor(() => expect(result.current.profile?.displayName).toBe('Bob'))
    expect(mockGetPublicProfile).toHaveBeenCalledTimes(2)
  })

  it('refetch() re-fetches the same id without changing the URL param (SCRUM-110 follow flow)', async () => {
    mockGetPublicProfile
      .mockResolvedValueOnce(profile)
      .mockResolvedValueOnce({ ...profile, followStatus: 'PENDING', followerCount: 13 })

    const { result } = renderHook(() => useUserProfile(profile.id))

    await waitFor(() => expect(result.current.profile?.followerCount).toBe(12))

    act(() => { result.current.refetch() })

    await waitFor(() => expect(result.current.profile?.followerCount).toBe(13))
    expect(result.current.profile?.followStatus).toBe('PENDING')
    expect(mockGetPublicProfile).toHaveBeenCalledTimes(2)
  })

  it('refetch() is a no-op while id is undefined (no fetch fires)', async () => {
    const { result } = renderHook(() => useUserProfile(undefined))
    await new Promise(r => setTimeout(r, 10))

    act(() => { result.current.refetch() })
    await new Promise(r => setTimeout(r, 10))

    expect(mockGetPublicProfile).not.toHaveBeenCalled()
  })

  it('discards a stale response when id changes mid-flight', async () => {
    let resolveFirst: (v: unknown) => void = () => {}
    mockGetPublicProfile
      .mockImplementationOnce(() => new Promise(resolve => { resolveFirst = resolve }))
      .mockResolvedValueOnce({ ...profile, id: 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1', displayName: 'Bob' })

    const { result, rerender } = renderHook(({ id }: { id: string }) => useUserProfile(id), {
      initialProps: { id: profile.id },
    })

    rerender({ id: 'b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1' })
    resolveFirst(profile) // stale resolve, must not leak

    await waitFor(() => expect(result.current.profile?.displayName).toBe('Bob'))
  })
})
