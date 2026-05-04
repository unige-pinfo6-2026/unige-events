
import { afterEach, describe, expect, it, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'

vi.mock('@/services/favoriteApi', () => ({
  addFavorite: vi.fn(),
  removeFavorite: vi.fn(),
}))

vi.mock('@/contexts/FavoritesContext', () => ({
  useFavoritesContext: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({ isAuthenticated: true })),
}))

import { addFavorite, removeFavorite } from '@/services/favoriteApi'
import { useFavoritesContext } from '@/contexts/FavoritesContext'
import { useAuth } from '@/hooks/useAuth'
import { useFavorite } from '@/hooks/useFavorite'

const mockAddFavorite = vi.mocked(addFavorite)
const mockRemoveFavorite = vi.mocked(removeFavorite)
const mockUseFavoritesContext = vi.mocked(useFavoritesContext)

const markFavorited = vi.fn()
const markUnfavorited = vi.fn()

afterEach(() => vi.resetAllMocks())

function setupContext(ids: number[]) {
  mockUseFavoritesContext.mockReturnValue({
    favoritedIds: new Set(ids),
    isLoaded: true,
    markFavorited,
    markUnfavorited,
  })
}

describe('useFavorite', () => {
  it('returns favorited=true when eventId is in context', () => {
    setupContext([1])
    const { result } = renderHook(() => useFavorite(1))
    expect(result.current.favorited).toBe(true)
  })

  it('returns favorited=false when eventId is not in context', () => {
    setupContext([])
    const { result } = renderHook(() => useFavorite(1))
    expect(result.current.favorited).toBe(false)
  })

  it('uses initialFavorited when context is not loaded', () => {
    mockUseFavoritesContext.mockReturnValue({
      favoritedIds: new Set(),
      isLoaded: false,
      markFavorited,
      markUnfavorited,
    })
    const { result } = renderHook(() => useFavorite(1, true))
    expect(result.current.favorited).toBe(true)
  })

  it('calls addFavorite and markFavorited on toggle when not favorited', async () => {
    setupContext([])
    mockAddFavorite.mockResolvedValue(undefined)

    const { result } = renderHook(() => useFavorite(1))
    let success: boolean
    await act(async () => { success = await result.current.toggle() })

    expect(mockAddFavorite).toHaveBeenCalledWith(1)
    expect(markFavorited).toHaveBeenCalledWith(1)
    expect(success!).toBe(true)
  })

  it('calls removeFavorite and markUnfavorited on toggle when favorited', async () => {
    setupContext([1])
    mockRemoveFavorite.mockResolvedValue(undefined)

    const { result } = renderHook(() => useFavorite(1))
    let success: boolean
    await act(async () => { success = await result.current.toggle() })

    expect(mockRemoveFavorite).toHaveBeenCalledWith(1)
    expect(markUnfavorited).toHaveBeenCalledWith(1)
    expect(success!).toBe(true)
  })

  it('rolls back on API error when adding', async () => {
    setupContext([])
    mockAddFavorite.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useFavorite(1))
    let success: boolean
    await act(async () => { success = await result.current.toggle() })

    expect(markFavorited).toHaveBeenCalledWith(1)
    expect(markUnfavorited).toHaveBeenCalledWith(1)
    expect(success!).toBe(false)
  })

  it('rolls back on API error when removing', async () => {
    setupContext([1])
    mockRemoveFavorite.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useFavorite(1))
    let success: boolean
    await act(async () => { success = await result.current.toggle() })

    expect(markUnfavorited).toHaveBeenCalledWith(1)
    expect(markFavorited).toHaveBeenCalledWith(1)
    expect(success!).toBe(false)
  })

  it('redirects to /login and does not call API when not authenticated', async () => {
    setupContext([])
    vi.mocked(useAuth).mockReturnValueOnce({ isAuthenticated: false } as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useFavorite(1))
    let success: boolean
    await act(async () => { success = await result.current.toggle() })

    expect(mockNavigate).toHaveBeenCalledWith('/login')
    expect(mockAddFavorite).not.toHaveBeenCalled()
    expect(success!).toBe(false)
  })

  it('returns false immediately when already loading', async () => {
    setupContext([])
    mockAddFavorite.mockImplementation(() => new Promise(() => {})) // never resolves

    const { result } = renderHook(() => useFavorite(1))
    act(() => { result.current.toggle() }) // start a toggle, don't await
    let success: boolean
    await act(async () => { success = await result.current.toggle() }) // second toggle while loading

    expect(success!).toBe(false)
    expect(mockAddFavorite).toHaveBeenCalledTimes(1)
  })

  it('invokes onAfterSuccess after a successful add', async () => {
    setupContext([])
    mockAddFavorite.mockResolvedValue(undefined)
    const onAfterSuccess = vi.fn().mockResolvedValue(undefined)

    const { result } = renderHook(() => useFavorite(1, false, { onAfterSuccess }))
    await act(async () => { await result.current.toggle() })

    expect(onAfterSuccess).toHaveBeenCalledTimes(1)
  })

  it('invokes onAfterSuccess after a successful remove', async () => {
    setupContext([1])
    mockRemoveFavorite.mockResolvedValue(undefined)
    const onAfterSuccess = vi.fn().mockResolvedValue(undefined)

    const { result } = renderHook(() => useFavorite(1, false, { onAfterSuccess }))
    await act(async () => { await result.current.toggle() })

    expect(onAfterSuccess).toHaveBeenCalledTimes(1)
  })

  it('does not invoke onAfterSuccess on API error', async () => {
    setupContext([])
    mockAddFavorite.mockRejectedValue(new Error('Network error'))
    const onAfterSuccess = vi.fn()

    const { result } = renderHook(() => useFavorite(1, false, { onAfterSuccess }))
    await act(async () => { await result.current.toggle() })

    expect(onAfterSuccess).not.toHaveBeenCalled()
  })

  it('does not roll back if onAfterSuccess rejects', async () => {
    setupContext([])
    mockAddFavorite.mockResolvedValue(undefined)
    const onAfterSuccess = vi.fn().mockRejectedValue(new Error('refetch failed'))

    const { result } = renderHook(() => useFavorite(1, false, { onAfterSuccess }))
    let success: boolean
    await act(async () => { success = await result.current.toggle() })

    expect(success!).toBe(true)
    // markUnfavorited called only once (the optimistic mark in the catch branch
    // is not executed because the mutation itself succeeded).
    expect(markUnfavorited).not.toHaveBeenCalled()
  })
})
