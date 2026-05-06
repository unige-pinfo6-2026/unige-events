import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    patch: vi.fn(),
  },
}))

import api from '@/services/api'
import {
  getReports,
  updateReportStatus,
  getFeaturedEvents,
  featureEvent,
  unfeatureEvent,
} from '@/services/adminApi'

const mockGet = vi.mocked(api.get)
const mockPatch = vi.mocked(api.patch)

beforeEach(() => {
  mockGet.mockReset()
  mockPatch.mockReset()
})

afterEach(() => {
  vi.clearAllMocks()
})

describe('getReports', () => {
  it('calls /admin/reports without params when no status given', async () => {
    mockGet.mockResolvedValue({ data: [] })
    await getReports()
    expect(mockGet).toHaveBeenCalledWith('/admin/reports', { params: undefined })
  })

  it('calls /admin/reports with status=PENDING when status is passed', async () => {
    mockGet.mockResolvedValue({ data: [] })
    await getReports('PENDING')
    expect(mockGet).toHaveBeenCalledWith('/admin/reports', { params: { status: 'PENDING' } })
  })

  it('returns response data', async () => {
    const reports = [{ id: 1, reason: 'Spam' }]
    mockGet.mockResolvedValue({ data: reports })
    const result = await getReports()
    expect(result).toEqual(reports)
  })

  it('throws when the API call fails', async () => {
    mockGet.mockRejectedValue(new Error('Network error'))
    await expect(getReports()).rejects.toThrow('Network error')
  })
})

describe('updateReportStatus', () => {
  it('patches the correct endpoint with the status', async () => {
    mockPatch.mockResolvedValue({ data: {} })
    await updateReportStatus(5, 'REVIEWED')
    expect(mockPatch).toHaveBeenCalledWith('/admin/reports/5', { status: 'REVIEWED' })
  })

  it('patches with DISMISSED status', async () => {
    mockPatch.mockResolvedValue({ data: {} })
    await updateReportStatus(12, 'DISMISSED')
    expect(mockPatch).toHaveBeenCalledWith('/admin/reports/12', { status: 'DISMISSED' })
  })

  it('throws when the API call fails', async () => {
    mockPatch.mockRejectedValue(new Error('Forbidden'))
    await expect(updateReportStatus(1, 'REVIEWED')).rejects.toThrow('Forbidden')
  })
})

describe('getFeaturedEvents', () => {
  it('calls /events with featured=true', async () => {
    mockGet.mockResolvedValue({ data: [] })
    await getFeaturedEvents()
    expect(mockGet).toHaveBeenCalledWith('/events', { params: { featured: true } })
  })

  it('returns response data', async () => {
    const events = [{ id: 1, title: 'Concert' }]
    mockGet.mockResolvedValue({ data: events })
    const result = await getFeaturedEvents()
    expect(result).toEqual(events)
  })
})

describe('featureEvent', () => {
  it('patches /admin/events/{id}/feature', async () => {
    mockPatch.mockResolvedValue({})
    await featureEvent(7)
    expect(mockPatch).toHaveBeenCalledWith('/admin/events/7/feature')
  })

  it('throws when the API call fails', async () => {
    mockPatch.mockRejectedValue(new Error('Not found'))
    await expect(featureEvent(7)).rejects.toThrow('Not found')
  })
})

describe('unfeatureEvent', () => {
  it('patches /admin/events/{id}/unfeature', async () => {
    mockPatch.mockResolvedValue({})
    await unfeatureEvent(3)
    expect(mockPatch).toHaveBeenCalledWith('/admin/events/3/unfeature')
  })

  it('throws when the API call fails', async () => {
    mockPatch.mockRejectedValue(new Error('Server error'))
    await expect(unfeatureEvent(3)).rejects.toThrow('Server error')
  })
})
