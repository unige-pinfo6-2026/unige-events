import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'

vi.mock('@/services/commentApi', () => ({
  likeComment: vi.fn(),
  unlikeComment: vi.fn(),
}))

const showToastMock = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: showToastMock, toasts: [], dismiss: vi.fn() }),
}))

import { likeComment, unlikeComment } from '@/services/commentApi'
import { useCommentLike } from '@/hooks/useCommentLike'

const mockLike = vi.mocked(likeComment)
const mockUnlike = vi.mocked(unlikeComment)

beforeEach(() => {
  mockLike.mockReset()
  mockUnlike.mockReset()
  showToastMock.mockReset()
})

afterEach(() => vi.clearAllMocks())

describe('useCommentLike', () => {
  it('initial state surfaces the constructor args', () => {
    const { result } = renderHook(() => useCommentLike(7, true, 12))
    expect(result.current.liked).toBe(true)
    expect(result.current.likeCount).toBe(12)
    expect(result.current.toggling).toBe(false)
  })

  it('toggle unliked → liked applies optimistic flip then resolves on 2xx', async () => {
    mockLike.mockResolvedValue({ liked: true, likeCount: 6 })
    const { result } = renderHook(() => useCommentLike(7, false, 5))

    act(() => { void result.current.toggle() })

    // Optimistic — already flipped before the await resolves.
    expect(result.current.liked).toBe(true)
    expect(result.current.likeCount).toBe(6)

    await waitFor(() => expect(result.current.toggling).toBe(false))
    expect(result.current.liked).toBe(true)
    expect(result.current.likeCount).toBe(6)
    expect(mockLike).toHaveBeenCalledWith(7)
  })

  it('toggle liked → unliked applies optimistic flip then resolves on 2xx', async () => {
    mockUnlike.mockResolvedValue()
    const { result } = renderHook(() => useCommentLike(7, true, 5))

    act(() => { void result.current.toggle() })

    expect(result.current.liked).toBe(false)
    expect(result.current.likeCount).toBe(4)

    await waitFor(() => expect(result.current.toggling).toBe(false))
    expect(mockUnlike).toHaveBeenCalledWith(7)
  })

  it('toggle rolls back on like 5xx and shows error toast', async () => {
    mockLike.mockRejectedValue(new Error('boom'))
    const { result } = renderHook(() => useCommentLike(7, false, 5))

    await act(async () => { await result.current.toggle() })

    expect(result.current.liked).toBe(false)
    expect(result.current.likeCount).toBe(5)
    expect(showToastMock).toHaveBeenCalledWith('error', expect.stringContaining('like'))
  })

  it('toggle rolls back on unlike network failure', async () => {
    mockUnlike.mockRejectedValue(new Error('network'))
    const { result } = renderHook(() => useCommentLike(7, true, 5))

    await act(async () => { await result.current.toggle() })

    expect(result.current.liked).toBe(true)
    expect(result.current.likeCount).toBe(5)
    expect(showToastMock).toHaveBeenCalled()
  })

  it('double-click during in-flight is a no-op (single HTTP call)', async () => {
    let resolveFirst: (() => void) | null = null
    mockLike.mockImplementationOnce(() => new Promise((res) => {
      resolveFirst = () => res({ liked: true, likeCount: 6 })
    }))
    const { result } = renderHook(() => useCommentLike(7, false, 5))

    act(() => { void result.current.toggle() })
    // Second click while toggling=true should be ignored.
    act(() => { void result.current.toggle() })

    expect(mockLike).toHaveBeenCalledTimes(1)

    act(() => { resolveFirst?.() })
    await waitFor(() => expect(result.current.toggling).toBe(false))
  })

  it('toggle stays toggling=true until the mutation resolves', async () => {
    let resolve: ((v: { liked: boolean; likeCount: number }) => void) | null = null
    mockLike.mockImplementationOnce(() => new Promise((res) => { resolve = res }))
    const { result } = renderHook(() => useCommentLike(7, false, 5))

    act(() => { void result.current.toggle() })
    expect(result.current.toggling).toBe(true)

    act(() => { resolve?.({ liked: true, likeCount: 6 }) })
    await waitFor(() => expect(result.current.toggling).toBe(false))
  })

  it('alternating like/unlike cycles are independent', async () => {
    mockLike.mockResolvedValue({ liked: true, likeCount: 6 })
    mockUnlike.mockResolvedValue()
    const { result } = renderHook(() => useCommentLike(7, false, 5))

    await act(async () => { await result.current.toggle() })
    expect(result.current.liked).toBe(true)

    await act(async () => { await result.current.toggle() })
    expect(result.current.liked).toBe(false)
    expect(result.current.likeCount).toBe(5)
  })
})
