// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    delete: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    patch: vi.fn(),
  },
}))

import api from '@/services/api'
import { createEvent, deleteEvent, getAll, getById, publishEvent, updateEvent, uploadEventImage } from '@/services/eventApi'

const mockApiGet = vi.mocked(api.get)
const mockApiDelete = vi.mocked(api.delete)
const mockApiPost = vi.mocked(api.post)
const mockApiPut = vi.mocked(api.put)
const mockApiPatch = vi.mocked(api.patch)

const sampleEvent = {
  id: 42,
  title: 'Forum des associations',
  description: 'Rencontrez les associations du campus.',
  location: 'Uni Dufour',
  startDate: '2026-04-10T08:00:00.000Z',
  endDate: '2026-04-10T10:00:00.000Z',
  category: 'SOCIAL',
  faculty: null,
  creatorId: '8b24e4aa-fdea-4e04-bf56-bdb2ddb7fc11',
  status: 'DRAFT',
  capacity: 120,
  createdAt: '2026-03-27T09:00:00.000Z',
}

beforeEach(() => {
  mockApiGet.mockReset()
  mockApiDelete.mockReset()
  mockApiPost.mockReset()
  mockApiPut.mockReset()
  mockApiPatch.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('eventApi', () => {
  it('gets a paginated event list with query params', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleEvent] } as Awaited<ReturnType<typeof api.get>>)

    const response = await getAll({ page: 0, size: 12, status: 'PUBLISHED' })

    expect(mockApiGet).toHaveBeenCalledWith('/events', { params: { page: 0, size: 12, status: 'PUBLISHED' } })
    expect(response).toEqual([sampleEvent])
  })

  it('gets an event by id', async () => {
    mockApiGet.mockResolvedValue({ data: sampleEvent } as Awaited<ReturnType<typeof api.get>>)

    const response = await getById(42)

    expect(mockApiGet).toHaveBeenCalledWith('/events/42')
    expect(response).toEqual(sampleEvent)
  })

  it('creates an event', async () => {
    mockApiPost.mockResolvedValue({ data: sampleEvent } as Awaited<ReturnType<typeof api.post>>)

    const payload = {
      title: 'Forum des associations',
      description: 'Rencontrez les associations du campus.',
      location: 'Uni Dufour',
      startDate: '2026-04-10T08:00:00.000Z',
      endDate: '2026-04-10T10:00:00.000Z',
      category: 'SOCIAL' as const,
      capacity: 120,
      status: 'DRAFT' as const,
    }

    const response = await createEvent(payload)

    expect(mockApiPost).toHaveBeenCalledWith('/events', payload)
    expect(response).toEqual(sampleEvent)
  })

  it('updates an event with a full put payload', async () => {
    mockApiPut.mockResolvedValue({ data: sampleEvent } as Awaited<ReturnType<typeof api.put>>)

    const payload = {
      title: 'Forum des associations',
      description: 'Rencontrez les associations du campus.',
      location: 'Uni Dufour',
      startDate: '2026-04-10T08:00:00.000Z',
      endDate: '2026-04-10T10:00:00.000Z',
      category: 'SOCIAL' as const,
      capacity: 120,
      status: 'PUBLISHED' as const,
    }

    const response = await updateEvent(42, payload)

    expect(mockApiPut).toHaveBeenCalledWith('/events/42', payload)
    expect(response).toEqual(sampleEvent)
  })

  it('uploads an event image as multipart form data and returns the updated event', async () => {
    mockApiPost.mockResolvedValue({ data: sampleEvent } as Awaited<ReturnType<typeof api.post>>)
    const file = new File(['img'], 'banner.png', { type: 'image/png' })

    const response = await uploadEventImage(42, file)

    expect(mockApiPost).toHaveBeenCalledTimes(1)
    const [url, body, config] = mockApiPost.mock.calls[0]
    expect(url).toBe('/events/42/image')
    expect(body).toBeInstanceOf(FormData)
    expect((body as FormData).get('file')).toBe(file)
    expect(config).toBeUndefined()
    expect(response).toEqual(sampleEvent)
  })

  it('deletes an event', async () => {
    mockApiDelete.mockResolvedValue({} as Awaited<ReturnType<typeof api.delete>>)

    await deleteEvent(42)

    expect(mockApiDelete).toHaveBeenCalledWith('/events/42')
  })

  it('publishes an event (DRAFT → PUBLISHED)', async () => {
    const publishedEvent = { ...sampleEvent, status: 'PUBLISHED' as const }
    mockApiPatch.mockResolvedValue({ data: publishedEvent } as Awaited<ReturnType<typeof api.patch>>)

    const response = await publishEvent(42)

    expect(mockApiPatch).toHaveBeenCalledWith('/events/42/publish')
    expect(response).toEqual(publishedEvent)
  })
})
