import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    post: vi.fn(),
  },
}))

import api from '@/services/api'
import { reportEvent } from '@/services/reportApi'

const mockApiPost = vi.mocked(api.post)

beforeEach(() => {
  mockApiPost.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('reportApi', () => {
  it('posts to the correct endpoint with reason', async () => {
    mockApiPost.mockResolvedValue({ data: undefined })

    await reportEvent(42, { reason: 'Spam' })

    expect(mockApiPost).toHaveBeenCalledWith('/events/42/report', { reason: 'Spam' })
  })

  it('posts reason with description when both provided', async () => {
    mockApiPost.mockResolvedValue({ data: undefined })

    await reportEvent(7, { reason: 'Faux événement\n\nCe n\'est pas un vrai événement.' })

    expect(mockApiPost).toHaveBeenCalledWith('/events/7/report', {
      reason: 'Faux événement\n\nCe n\'est pas un vrai événement.',
    })
  })

  it('throws when the API call fails', async () => {
    mockApiPost.mockRejectedValue(new Error('Network error'))

    await expect(reportEvent(1, { reason: 'Spam' })).rejects.toThrow('Network error')
  })

  it('throws on 409 conflict', async () => {
    const error = Object.assign(new Error('Conflict'), { response: { status: 409 } })
    mockApiPost.mockRejectedValue(error)

    await expect(reportEvent(1, { reason: 'Spam' })).rejects.toMatchObject({
      response: { status: 409 },
    })
  })
})
