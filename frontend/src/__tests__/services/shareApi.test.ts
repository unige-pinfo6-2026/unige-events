// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
  },
}))

import api from '@/services/api'
import { getShareUrl } from '@/services/shareApi'

const mockApiGet = vi.mocked(api.get)

beforeEach(() => mockApiGet.mockReset())
afterEach(() => vi.clearAllMocks())

describe('shareApi', () => {
  it('returns the shareUrl from the API response', async () => {
    mockApiGet.mockResolvedValue({ data: { shareUrl: 'http://localhost:5173/events/42', shortCode: 'abc123' } } as Awaited<ReturnType<typeof api.get>>)

    const result = await getShareUrl(42)

    expect(mockApiGet).toHaveBeenCalledWith('/events/42/share')
    expect(result).toBe('http://localhost:5173/events/42')
  })
})
