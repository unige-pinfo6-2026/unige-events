// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { FavoritesProvider, useFavoritesContext } from '@/contexts/FavoritesContext'

vi.mock('@/hooks', () => ({
  useAuth: vi.fn(),
}))

vi.mock('@/services/favoriteApi', () => ({
  getFavorites: vi.fn(),
}))

import { useAuth } from '@/hooks'
import { getFavorites } from '@/services/favoriteApi'
import type { Event } from '@/types/event'

const mockUseAuth = vi.mocked(useAuth)
const mockGetFavorites = vi.mocked(getFavorites)

const sampleEvent: Event = {
  id: 1,
  title: 'Conférence IA',
  location: 'Uni Dufour',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'user-1',
  createdAt: '2026-03-01T10:00:00',
}

function wrapper({ children }: { children: ReactNode }) {
  return <FavoritesProvider>{children}</FavoritesProvider>
}

afterEach(() => vi.resetAllMocks())

describe('FavoritesContext', () => {
  it('throws when used outside provider', () => {
    expect(() => renderHook(() => useFavoritesContext())).toThrow(
      'useFavoritesContext must be used within FavoritesProvider',
    )
  })

  it('starts with empty set when not authenticated', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false } as ReturnType<typeof useAuth>)
    mockGetFavorites.mockResolvedValue([])

    const { result } = renderHook(() => useFavoritesContext(), { wrapper })

    expect(result.current.favoritedIds.size).toBe(0)
    expect(result.current.isLoaded).toBe(false)
  })

  it('loads favorited ids on mount when authenticated', async () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
    mockGetFavorites.mockResolvedValue([sampleEvent])

    const { result } = renderHook(() => useFavoritesContext(), { wrapper })

    await waitFor(() => expect(result.current.isLoaded).toBe(true))
    expect(result.current.favoritedIds.has(1)).toBe(true)
  })

  it('sets isLoaded to true even when getFavorites fails', async () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
    mockGetFavorites.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useFavoritesContext(), { wrapper })

    await waitFor(() => expect(result.current.isLoaded).toBe(true))
    expect(result.current.favoritedIds.size).toBe(0)
  })

  it('markFavorited adds an id to the set', async () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
    mockGetFavorites.mockResolvedValue([])

    const { result } = renderHook(() => useFavoritesContext(), { wrapper })
    await waitFor(() => expect(result.current.isLoaded).toBe(true))

    act(() => result.current.markFavorited(42))

    expect(result.current.favoritedIds.has(42)).toBe(true)
  })

  it('markUnfavorited removes an id from the set', async () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
    mockGetFavorites.mockResolvedValue([sampleEvent])

    const { result } = renderHook(() => useFavoritesContext(), { wrapper })
    await waitFor(() => expect(result.current.isLoaded).toBe(true))

    act(() => result.current.markUnfavorited(1))

    expect(result.current.favoritedIds.has(1)).toBe(false)
  })

  it('clears favorites when user logs out', async () => {
    const mockAuth = { isAuthenticated: true }
    mockUseAuth.mockReturnValue(mockAuth as ReturnType<typeof useAuth>)
    mockGetFavorites.mockResolvedValue([sampleEvent])

    const { result, rerender } = renderHook(() => useFavoritesContext(), { wrapper })
    await waitFor(() => expect(result.current.isLoaded).toBe(true))
    expect(result.current.favoritedIds.has(1)).toBe(true)

    mockUseAuth.mockReturnValue({ isAuthenticated: false } as ReturnType<typeof useAuth>)
    rerender()

    expect(result.current.favoritedIds.size).toBe(0)
    expect(result.current.isLoaded).toBe(false)
  })
})
