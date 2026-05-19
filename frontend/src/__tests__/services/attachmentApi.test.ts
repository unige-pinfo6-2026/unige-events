import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from '@/services/api'
import { deleteEventAttachment, uploadEventAttachment } from '@/services/attachmentApi'

const mockPost = vi.mocked(api.post)
const mockDelete = vi.mocked(api.delete)

const sampleAttachment = {
  id: 7,
  fileName: 'programme.pdf',
  fileUrl: 'http://minio:9000/bucket/event-attachments/programme.pdf',
  downloadUrl: '/api/events/42/attachments/7/download',
  fileSize: 1234,
  mimeType: 'application/pdf' as const,
  uploadedById: 'user-uuid',
  uploadedAt: '2026-05-18T10:00:00Z',
}

afterEach(() => {
  vi.clearAllMocks()
})

describe('uploadEventAttachment', () => {
  it('posts a multipart form with the file field and returns the attachment DTO', async () => {
    mockPost.mockResolvedValue({ data: sampleAttachment } as Awaited<ReturnType<typeof api.post>>)
    const file = new File(['hello'], 'programme.pdf', { type: 'application/pdf' })

    const result = await uploadEventAttachment(42, file)

    expect(mockPost).toHaveBeenCalledTimes(1)
    const [url, body, config] = mockPost.mock.calls[0]
    expect(url).toBe('/events/42/attachments')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
    expect(config).toBeUndefined()
    expect(result).toEqual(sampleAttachment)
  })

  it('propagates upload errors as-is so the caller can surface them per row', async () => {
    const err = Object.assign(new Error('boom'), { isAxiosError: true })
    mockPost.mockRejectedValue(err)
    const file = new File(['x'], 'huge.pdf', { type: 'application/pdf' })

    await expect(uploadEventAttachment(1, file)).rejects.toBe(err)
  })
})

describe('deleteEventAttachment', () => {
  it('calls DELETE /events/{eventId}/attachments/{attachmentId}', async () => {
    mockDelete.mockResolvedValue({} as Awaited<ReturnType<typeof api.delete>>)

    await deleteEventAttachment(42, 7)

    expect(mockDelete).toHaveBeenCalledWith('/events/42/attachments/7')
  })

  it('propagates delete errors so the caller can keep the row visible', async () => {
    const err = new Error('network')
    mockDelete.mockRejectedValue(err)

    await expect(deleteEventAttachment(42, 7)).rejects.toBe(err)
  })
})
