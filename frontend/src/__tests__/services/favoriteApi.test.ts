// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from '@/services/api'
import { addFavorite, getFavorites, removeFavorite } from '@/services/favoriteApi'
import type { Event } from '@/types/event'

const mockApiGet = vi.mocked(api.get)
const mockApiPost = vi.mocked(api.post)
const mockApiDelete = vi.mocked(api.delete)

const sampleEvent: Event = {
  id: 1,
  title: 'Conférence IA',
  location: 'Uni Dufour',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  allDay: false,
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'user-1',
  attendingCount: 0,
  createdAt: '2026-03-01T10:00:00',
}

beforeEach(() => {
  mockApiGet.mockReset()
  mockApiPost.mockReset()
  mockApiDelete.mockReset()
})

afterEach(() => vi.clearAllMocks())

describe('favoriteApi', () => {
  it('gets the list of favorites', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleEvent] } as Awaited<ReturnType<typeof api.get>>)

    const result = await getFavorites()

    expect(mockApiGet).toHaveBeenCalledWith('/users/me/favorites')
    expect(result).toEqual([sampleEvent])
  })

  it('adds a favorite', async () => {
    mockApiPost.mockResolvedValue({} as Awaited<ReturnType<typeof api.post>>)

    await addFavorite(1)

    expect(mockApiPost).toHaveBeenCalledWith('/events/1/favorite')
  })

  it('removes a favorite', async () => {
    mockApiDelete.mockResolvedValue({} as Awaited<ReturnType<typeof api.delete>>)

    await removeFavorite(1)

    expect(mockApiDelete).toHaveBeenCalledWith('/events/1/favorite')
  })
})
