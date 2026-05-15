// @vitest-environment jsdom

import { describe, expect, it, vi, afterEach } from 'vitest'
import { getEventStats, getEventAttendees, recordEventView } from '@/services/statsApi'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
  },
}))

import api from '@/services/api'
const mockGet = api.get as ReturnType<typeof vi.fn>
const mockPost = api.post as ReturnType<typeof vi.fn>

afterEach(() => vi.resetAllMocks())

describe('getEventStats', () => {
  it('calls GET /events/:id/stats and returns data', async () => {
    const stats = { viewCount: 100, attendingCount: 20, interestedCount: 5 }
    mockGet.mockResolvedValue({ data: stats })

    const result = await getEventStats(42)

    expect(mockGet).toHaveBeenCalledWith('/events/42/stats')
    expect(result).toEqual(stats)
  })

  it('propagates errors from the API', async () => {
    mockGet.mockRejectedValue(new Error('Network error'))
    await expect(getEventStats(1)).rejects.toThrow('Network error')
  })
})

describe('recordEventView', () => {
  it('calls POST /events/:id/view with a sessionId body', async () => {
    mockPost.mockResolvedValue({})
    await recordEventView(42)
    expect(mockPost).toHaveBeenCalledWith(
      '/events/42/view',
      expect.objectContaining({ sessionId: expect.any(String) }),
    )
  })

  it('reuses the same sessionId across calls (persistent via localStorage)', async () => {
    mockPost.mockResolvedValue({})
    await recordEventView(1)
    await recordEventView(2)
    const firstCall = mockPost.mock.calls[0]
    const secondCall = mockPost.mock.calls[1]
    expect(firstCall[1].sessionId).toBe(secondCall[1].sessionId)
  })

  it('propagates errors from the API', async () => {
    mockPost.mockRejectedValue(new Error('Network error'))
    await expect(recordEventView(1)).rejects.toThrow('Network error')
  })
})

describe('getEventAttendees', () => {
  it('paginates with size=100 and returns the full list', async () => {
    const attendees = [{ id: 1, userId: 'u-1', eventId: 42, status: 'ATTENDING', createdAt: '2026-01-01' }]
    mockGet.mockResolvedValue({ data: attendees })

    const result = await getEventAttendees(42)

    expect(mockGet).toHaveBeenCalledWith('/events/42/attendees', { params: { page: 0, size: 100 } })
    expect(mockGet).toHaveBeenCalledTimes(1) // first batch is short → stop
    expect(result).toEqual(attendees)
  })

  it('keeps fetching while a batch is full and stops on a short one', async () => {
    const fullPage = Array.from({ length: 100 }, (_, i) => ({
      id: i, userId: `u-${i}`, eventId: 42, status: 'ATTENDING', createdAt: '2026-01-01',
    }))
    const shortPage = Array.from({ length: 7 }, (_, i) => ({
      id: 1000 + i, userId: `u-tail-${i}`, eventId: 42, status: 'WAITLISTED', createdAt: '2026-01-01',
    }))
    mockGet
      .mockResolvedValueOnce({ data: fullPage })
      .mockResolvedValueOnce({ data: fullPage })
      .mockResolvedValueOnce({ data: shortPage })

    const result = await getEventAttendees(42)

    expect(mockGet).toHaveBeenNthCalledWith(1, '/events/42/attendees', { params: { page: 0, size: 100 } })
    expect(mockGet).toHaveBeenNthCalledWith(2, '/events/42/attendees', { params: { page: 1, size: 100 } })
    expect(mockGet).toHaveBeenNthCalledWith(3, '/events/42/attendees', { params: { page: 2, size: 100 } })
    expect(result).toHaveLength(207)
  })

  it('stops on an empty page (exact multiple of PAGE_SIZE in DB)', async () => {
    const fullPage = Array.from({ length: 100 }, (_, i) => ({
      id: i, userId: `u-${i}`, eventId: 42, status: 'ATTENDING', createdAt: '2026-01-01',
    }))
    mockGet
      .mockResolvedValueOnce({ data: fullPage })
      .mockResolvedValueOnce({ data: [] })

    const result = await getEventAttendees(42)

    expect(mockGet).toHaveBeenCalledTimes(2)
    expect(result).toHaveLength(100)
  })

  it('propagates errors from the API', async () => {
    mockGet.mockRejectedValue(new Error('Forbidden'))
    await expect(getEventAttendees(1)).rejects.toThrow('Forbidden')
  })
})
