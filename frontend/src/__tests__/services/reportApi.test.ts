// Regression: frontend was sending French labels ("Spam") instead of enum constants
// ("SPAM"), causing a 400 — see PR for feature/s6-report-modal.
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    post: vi.fn(),
  },
}))

import api from '@/services/api'
import { reportComment, reportEvent } from '@/services/reportApi'

const mockApiPost = vi.mocked(api.post)

beforeEach(() => {
  mockApiPost.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('reportApi', () => {
  it('posts to the correct endpoint with reason only', async () => {
    mockApiPost.mockResolvedValue({ data: undefined })

    await reportEvent(42, { reason: 'SPAM' })

    expect(mockApiPost).toHaveBeenCalledWith('/events/42/report', { reason: 'SPAM' })
  })

  it('posts reason and description as separate fields', async () => {
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

  it('reportComment posts to /comments/{id}/report with reason only', async () => {
    mockApiPost.mockResolvedValue({ data: undefined })

    await reportComment(31, { reason: 'SPAM' })

    expect(mockApiPost).toHaveBeenCalledWith('/comments/31/report', { reason: 'SPAM' })
  })

  it('reportComment posts reason and description', async () => {
    mockApiPost.mockResolvedValue({ data: undefined })

    await reportComment(31, { reason: 'INAPPROPRIATE', description: 'Insultes répétées.' })

    expect(mockApiPost).toHaveBeenCalledWith('/comments/31/report', {
      reason: 'INAPPROPRIATE',
      description: 'Insultes répétées.',
    })
  })

  it('reportComment propagates 409 already-reported', async () => {
    const error = Object.assign(new Error('Conflict'), { response: { status: 409 } })
    mockApiPost.mockRejectedValue(error)

    await expect(reportComment(31, { reason: 'SPAM' })).rejects.toMatchObject({
      response: { status: 409 },
    })
  })

  it('reportComment propagates 422 cannot-report-own-comment', async () => {
    const error = Object.assign(new Error('Unprocessable'), { response: { status: 422 } })
    mockApiPost.mockRejectedValue(error)

    await expect(reportComment(31, { reason: 'SPAM' })).rejects.toMatchObject({
      response: { status: 422 },
    })
  })
})
