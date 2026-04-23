
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../services/api', () => ({
  default: {
    get: vi.fn(),
  },
}))

import api from '../../services/api'
import { fetchSuggestions, searchEvents } from '../../services/searchApi'
import type { SearchParams } from '../../types/search'

const mockGet = api.get as ReturnType<typeof vi.fn>

afterEach(() => {
  vi.resetAllMocks()
})

const mockEvents = [
  {
    id: 1,
    title: 'Conférence IA',
    location: 'Uni Dufour',
    startDate: '2026-04-10T14:00:00',
    endDate: '2026-04-10T17:00:00',
    category: 'CONFERENCE' as const,
    faculty: null,
    status: 'PUBLISHED' as const,
    creatorId: 'user-1',
    createdAt: '2026-03-01T10:00:00',
  },
]

describe('searchEvents', () => {
  it('calls GET /events/search with params and returns data', async () => {
    mockGet.mockResolvedValue({ data: mockEvents })

    const params: SearchParams = { q: 'IA', category: 'CONFERENCE' }
    const result = await searchEvents(params)

    expect(mockGet).toHaveBeenCalledWith('/events/search', { params })
    expect(result).toEqual(mockEvents)
  })

  it('returns an empty array when no results', async () => {
    mockGet.mockResolvedValue({ data: [] })

    const result = await searchEvents({})
    expect(result).toEqual([])
  })

  it('propagates errors from the API', async () => {
    mockGet.mockRejectedValue(new Error('Network error'))

    await expect(searchEvents({ q: 'test' })).rejects.toThrow('Network error')
  })

  it('passes all filter params to the API', async () => {
    mockGet.mockResolvedValue({ data: [] })

    const params: SearchParams = {
      q: 'sport',
      category: 'SPORTS',
      faculty: 'SCIENCES',
      dateFrom: '2026-04-01',
      dateTo: '2026-04-30',
      page: 0,
      size: 20,
    }
    await searchEvents(params)

    expect(mockGet).toHaveBeenCalledWith('/events/search', { params })
  })

  it('forwards abort signal to Axios when provided', async () => {
    mockGet.mockResolvedValue({ data: [] })

    const controller = new AbortController()
    await searchEvents({}, controller.signal)

    expect(mockGet).toHaveBeenCalledWith('/events/search', {
      params: {},
      signal: controller.signal,
    })
  })

  it('does not include signal in Axios config when not provided', async () => {
    mockGet.mockResolvedValue({ data: [] })

    await searchEvents({ q: 'test' })

    const callConfig = mockGet.mock.calls[0][1] as Record<string, unknown>
    expect(callConfig).not.toHaveProperty('signal')
  })
})

describe('fetchSuggestions', () => {
  it('returns an empty array (stub)', async () => {
    const result = await fetchSuggestions('conf')
    expect(result).toEqual([])
  })

  it('returns an empty array regardless of input', async () => {
    const result = await fetchSuggestions('')
    expect(result).toEqual([])
  })

  it('accepts an optional AbortSignal without throwing', async () => {
    const controller = new AbortController()
    const result = await fetchSuggestions('conf', controller.signal)
    expect(result).toEqual([])
  })
})
