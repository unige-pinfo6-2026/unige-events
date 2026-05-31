import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'

vi.mock('@/services/userService', () => ({
  searchUsernames: vi.fn(),
}))

import { searchUsernames } from '@/services/userService'
import { useUserSearch } from '@/hooks/useUserSearch'
import type { UserPublicResponse } from '@/types/user'

const mockSearch = vi.mocked(searchUsernames)

function user(username: string): UserPublicResponse {
  return {
    id: `${username}-id`,
    username,
    displayName: username,
    avatarUrl: null,
    profilePublic: true,
    followerCount: 0,
    followingCount: 0,
    followStatus: null,
  }
}

beforeEach(() => {
  mockSearch.mockReset()
  mockSearch.mockResolvedValue([])
})

afterEach(() => {
  vi.useRealTimers()
})

describe('useUserSearch', () => {
  it('does not search when disabled (endpoint is @Authenticated)', async () => {
    const { result } = renderHook(() => useUserSearch(false))
    act(() => result.current.setQuery('daniel'))
    await new Promise(r => setTimeout(r, 400))
    expect(mockSearch).not.toHaveBeenCalled()
    expect(result.current.searched).toBe(false)
  })

  it('does not search below the 2-char minimum', async () => {
    const { result } = renderHook(() => useUserSearch(true))
    act(() => result.current.setQuery('d'))
    await new Promise(r => setTimeout(r, 400))
    expect(mockSearch).not.toHaveBeenCalled()
  })

  it('searches (debounced) once enabled and ≥ 2 chars, exposing results', async () => {
    mockSearch.mockResolvedValue([user('daniel.dosh')])
    const { result } = renderHook(() => useUserSearch(true))
    act(() => result.current.setQuery('dan'))
    await waitFor(() => expect(mockSearch).toHaveBeenCalledWith('dan', 20), { timeout: 1000 })
    await waitFor(() => expect(result.current.results).toHaveLength(1))
    expect(result.current.searched).toBe(true)
  })

  it('surfaces a generic error on non-401 failures', async () => {
    mockSearch.mockRejectedValue(new Error('boom'))
    const { result } = renderHook(() => useUserSearch(true))
    act(() => result.current.setQuery('dan'))
    await waitFor(() => expect(result.current.error).toBe('Impossible de charger les utilisateurs.'), { timeout: 1000 })
    expect(result.current.results).toEqual([])
  })
})
