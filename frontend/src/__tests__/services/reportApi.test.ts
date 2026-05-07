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
  it('posts the reason enum to the correct endpoint with description omitted/null', async () => {
    mockApiPost.mockResolvedValue({ data: undefined })

    await reportEvent(42, { reason: 'SPAM' })

    expect(mockApiPost).toHaveBeenCalledWith('/events/42/report', { reason: 'SPAM' })
  })

  it('posts reason and description as separate fields when description is provided', async () => {
    mockApiPost.mockResolvedValue({ data: undefined })

    await reportEvent(7, { reason: 'FAKE', description: "Ce n'est pas un vrai événement." })

    expect(mockApiPost).toHaveBeenCalledWith('/events/7/report', {
      reason: 'FAKE',
      description: "Ce n'est pas un vrai événement.",
    })
  })

  it('throws when the API call fails', async () => {
    mockApiPost.mockRejectedValue(new Error('Network error'))

    await expect(reportEvent(1, { reason: 'SPAM' })).rejects.toThrow('Network error')
  })

  it('throws on 409 conflict', async () => {
    const error = Object.assign(new Error('Conflict'), { response: { status: 409 } })
    mockApiPost.mockRejectedValue(error)

    await expect(reportEvent(1, { reason: 'SPAM' })).rejects.toMatchObject({
      response: { status: 409 },
    })
  })
})
