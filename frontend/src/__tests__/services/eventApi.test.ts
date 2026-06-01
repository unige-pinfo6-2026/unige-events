
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
import { cancelEvent, createEvent, deleteEvent, duplicateEvent, getAll, getById, getFeatured, getMyDrafts, getMyEvents, getOccurrences, getOrganizerUuids, publishEvent, restoreEvent, updateEvent, uploadEventImage } from '@/services/eventApi'

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
  it('getOrganizerUuids hits the public organizer-uuids endpoint and returns the UUID list', async () => {
    const uuids = ['11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222']
    mockApiGet.mockResolvedValue({ data: uuids } as Awaited<ReturnType<typeof api.get>>)

    const result = await getOrganizerUuids(42)

    expect(mockApiGet).toHaveBeenCalledWith('/events/42/organizer-uuids')
    expect(result).toEqual(uuids)
  })

  it('gets a paginated event list with query params', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleEvent] } as Awaited<ReturnType<typeof api.get>>)

    const response = await getAll({ page: 0, size: 12, status: 'PUBLISHED' })

    expect(mockApiGet).toHaveBeenCalledWith('/events', { params: { page: 0, size: 12, status: 'PUBLISHED' } })
    expect(response).toEqual([sampleEvent])
  })

  it('gets an event by id', async () => {
    mockApiGet.mockResolvedValue({ data: sampleEvent } as Awaited<ReturnType<typeof api.get>>)

    const response = await getById(42)

    expect(mockApiGet).toHaveBeenCalledWith('/events/42', { params: undefined })
    expect(response).toEqual(sampleEvent)
  })

  it('forwards check-co-org-of when given the caller uuid (SCRUM-137)', async () => {
    mockApiGet.mockResolvedValue({ data: sampleEvent } as Awaited<ReturnType<typeof api.get>>)

    await getById(42, 'aa11bb22-cc33-dd44-ee55-ff6677889900')

    expect(mockApiGet).toHaveBeenCalledWith('/events/42', {
      params: { 'check-co-org-of': 'aa11bb22-cc33-dd44-ee55-ff6677889900' },
    })
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

  it('getMyDrafts calls /users/me/events with status=DRAFT and size=5 by default', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleEvent] } as Awaited<ReturnType<typeof api.get>>)

    const response = await getMyDrafts()

    expect(mockApiGet).toHaveBeenCalledWith('/users/me/events', {
      params: { status: 'DRAFT', size: 5 },
    })
    expect(response).toEqual([sampleEvent])
  })

  it('getMyDrafts honors a custom limit', async () => {
    mockApiGet.mockResolvedValue({ data: [] } as unknown as Awaited<ReturnType<typeof api.get>>)

    await getMyDrafts(3)

    expect(mockApiGet).toHaveBeenCalledWith('/users/me/events', {
      params: { status: 'DRAFT', size: 3 },
    })
  })

  it('getMyEvents calls /users/me/events with status param', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleEvent] } as Awaited<ReturnType<typeof api.get>>)

    const response = await getMyEvents({ status: 'DRAFT' })

    expect(mockApiGet).toHaveBeenCalledWith('/users/me/events', { params: { status: 'DRAFT' } })
    expect(response).toEqual([sampleEvent])
  })

  it('getMyEvents calls /users/me/events with empty params by default', async () => {
    mockApiGet.mockResolvedValue({ data: [] } as Awaited<ReturnType<typeof api.get>>)

    await getMyEvents()

    expect(mockApiGet).toHaveBeenCalledWith('/users/me/events', { params: {} })
  })

  it('getMyEvents returns the response data', async () => {
    const events = [sampleEvent, { ...sampleEvent, id: 43, title: 'Other' }]
    mockApiGet.mockResolvedValue({ data: events } as Awaited<ReturnType<typeof api.get>>)

    const response = await getMyEvents({ page: 0, size: 50 })

    expect(mockApiGet).toHaveBeenCalledWith('/users/me/events', { params: { page: 0, size: 50 } })
    expect(response).toEqual(events)
  })

  it('getFeatured calls /events/featured with limit param', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleEvent] } as Awaited<ReturnType<typeof api.get>>)

    const response = await getFeatured(6)

    expect(mockApiGet).toHaveBeenCalledWith('/events/featured', { params: { limit: 6 } })
    expect(response).toEqual([sampleEvent])
  })

  it('getOccurrences calls /events/{parentId}/occurrences with no params by default (SCRUM-151)', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleEvent] } as Awaited<ReturnType<typeof api.get>>)

    const response = await getOccurrences(42)

    expect(mockApiGet).toHaveBeenCalledWith('/events/42/occurrences', { params: {} })
    expect(response).toEqual([sampleEvent])
  })

  it('getOccurrences propagates pagination params to axios (SCRUM-151)', async () => {
    mockApiGet.mockResolvedValue({ data: [] } as unknown as Awaited<ReturnType<typeof api.get>>)

    await getOccurrences(42, { page: 1, size: 10 })

    expect(mockApiGet).toHaveBeenCalledWith('/events/42/occurrences', { params: { page: 1, size: 10 } })
  })

  it('duplicateEvent posts to /events/{id}/duplicate and returns the clone', async () => {
    const clone = { ...sampleEvent, id: 99, title: 'Copie de Forum des associations', status: 'DRAFT' }
    mockApiPost.mockResolvedValue({ data: clone } as Awaited<ReturnType<typeof api.post>>)

    const result = await duplicateEvent(42)

    expect(mockApiPost).toHaveBeenCalledWith('/events/42/duplicate')
    expect(result).toEqual(clone)
  })

  it('cancelEvent patches /events/{id}/cancel and returns the updated event', async () => {
    const cancelled = { ...sampleEvent, status: 'CANCELLED' }
    mockApiPatch.mockResolvedValue({ data: cancelled } as Awaited<ReturnType<typeof api.patch>>)

    const result = await cancelEvent(42)

    expect(mockApiPatch).toHaveBeenCalledWith('/events/42/cancel')
    expect(result).toEqual(cancelled)
  })

  it('restoreEvent patches /events/{id}/restore and returns the updated event', async () => {
    const restored = { ...sampleEvent, status: 'DRAFT' }
    mockApiPatch.mockResolvedValue({ data: restored } as Awaited<ReturnType<typeof api.patch>>)

    const result = await restoreEvent(42)

    expect(mockApiPatch).toHaveBeenCalledWith('/events/42/restore')
    expect(result).toEqual(restored)
  })

  it('publishEvent patches /events/{id}/publish and returns the updated event', async () => {
    const published = { ...sampleEvent, status: 'PUBLISHED' }
    mockApiPatch.mockResolvedValue({ data: published } as Awaited<ReturnType<typeof api.patch>>)

    const result = await publishEvent(42)

    expect(mockApiPatch).toHaveBeenCalledWith('/events/42/publish')
    expect(result).toEqual(published)
  })
})
