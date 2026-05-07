import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/adminApi', () => ({
  getReports: vi.fn(),
  updateReportStatus: vi.fn(),
}))

import { getReports, updateReportStatus } from '@/services/adminApi'
import { useAdminReports } from '@/hooks/useAdminReports'
import type { Report } from '@/types/admin'

const mockGetReports = getReports as ReturnType<typeof vi.fn>
const mockUpdateReportStatus = updateReportStatus as ReturnType<typeof vi.fn>

const makeReport = (id: number, status: Report['status'] = 'PENDING', createdAt = '2026-05-01T10:00:00'): Report => ({
  id,
  eventId: 100 + id,
  eventTitle: `Event ${id}`,
  reporterId: `reporter-${id}`,
  reporterDisplayName: 'Alice',
  reason: 'SPAM',
  description: null,
  createdAt,
  status,
  moderationNote: null,
  reviewedAt: null,
  reviewedBy: null,
})

afterEach(() => vi.resetAllMocks())

describe('useAdminReports — initial load', () => {
  it('starts loading and fetches PENDING reports on mount', async () => {
    mockGetReports.mockResolvedValue([makeReport(1)])
    const { result } = renderHook(() => useAdminReports())

    expect(result.current.loading).toBe(true)
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetReports).toHaveBeenCalledWith('PENDING')
    expect(result.current.reports).toHaveLength(1)
    expect(result.current.error).toBeNull()
  })

  it('sets error message when fetch fails', async () => {
    mockGetReports.mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les signalements.')
    expect(result.current.reports).toEqual([])
  })

  it('starts with activeTab=PENDING', async () => {
    mockGetReports.mockResolvedValue([])
    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.activeTab).toBe('PENDING')
  })
})

describe('useAdminReports — tab switching', () => {
  it('fetches REVIEWED and DISMISSED in parallel when switching to PROCESSED tab', async () => {
    mockGetReports.mockResolvedValue([])
    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))
    mockGetReports.mockClear()

    act(() => result.current.setActiveTab('PROCESSED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetReports).toHaveBeenCalledWith('REVIEWED')
    expect(mockGetReports).toHaveBeenCalledWith('DISMISSED')
    expect(mockGetReports).toHaveBeenCalledTimes(2)
    expect(result.current.activeTab).toBe('PROCESSED')
  })

  it('merges and sorts REVIEWED+DISMISSED results by createdAt desc on PROCESSED tab', async () => {
    const reviewed = makeReport(1, 'REVIEWED', '2026-05-03T10:00:00')
    const dismissed = makeReport(2, 'DISMISSED', '2026-05-05T10:00:00')
    const olderDismissed = makeReport(3, 'DISMISSED', '2026-05-01T10:00:00')

    mockGetReports.mockImplementation((status: string) => {
      if (status === 'REVIEWED') return Promise.resolve([reviewed])
      if (status === 'DISMISSED') return Promise.resolve([dismissed, olderDismissed])
      return Promise.resolve([])
    })

    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.setActiveTab('PROCESSED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.reports.map(r => r.id)).toEqual([2, 1, 3])
  })

  it('fetches with PENDING status when switching back to PENDING tab', async () => {
    mockGetReports.mockResolvedValue([])
    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.setActiveTab('PROCESSED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => result.current.setActiveTab('PENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(mockGetReports).toHaveBeenLastCalledWith('PENDING')
  })

  it('reports an error if one of the processed fetches fails', async () => {
    mockGetReports.mockResolvedValueOnce([])
    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))

    mockGetReports.mockRejectedValue(new Error('Network down'))
    act(() => result.current.setActiveTab('PROCESSED'))
    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.error).toBe('Impossible de charger les signalements.')
    expect(result.current.reports).toEqual([])
  })
})

describe('useAdminReports — reviewReport', () => {
  it('calls updateReportStatus with REVIEWED and removes report from list', async () => {
    mockGetReports.mockResolvedValue([makeReport(1), makeReport(2)])
    mockUpdateReportStatus.mockResolvedValue({})

    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.reports).toHaveLength(2)

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.reviewReport(1) })

    expect(mockUpdateReportStatus).toHaveBeenCalledWith(1, 'REVIEWED')
    expect(result.current.reports).toHaveLength(1)
    expect(result.current.reports[0].id).toBe(2)
    expect(ok).toBe(true)
  })

  it('returns false and keeps list intact when reviewReport fails', async () => {
    mockGetReports.mockResolvedValue([makeReport(1)])
    mockUpdateReportStatus.mockRejectedValue(new Error('Forbidden'))

    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.reviewReport(1) })

    expect(ok).toBe(false)
    expect(result.current.reports).toHaveLength(1)
  })
})

describe('useAdminReports — dismissReport', () => {
  it('calls updateReportStatus with DISMISSED and removes report from list', async () => {
    mockGetReports.mockResolvedValue([makeReport(1), makeReport(2)])
    mockUpdateReportStatus.mockResolvedValue({})

    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.dismissReport(2) })

    expect(mockUpdateReportStatus).toHaveBeenCalledWith(2, 'DISMISSED')
    expect(result.current.reports).toHaveLength(1)
    expect(result.current.reports[0].id).toBe(1)
    expect(ok).toBe(true)
  })

  it('returns false and keeps list intact when dismissReport fails', async () => {
    mockGetReports.mockResolvedValue([makeReport(1)])
    mockUpdateReportStatus.mockRejectedValue(new Error('Server error'))

    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))

    let ok: boolean | undefined
    await act(async () => { ok = await result.current.dismissReport(1) })

    expect(ok).toBe(false)
    expect(result.current.reports).toHaveLength(1)
  })
})

describe('useAdminReports — refresh', () => {
  it('re-fetches reports when refresh() is called', async () => {
    mockGetReports.mockResolvedValue([makeReport(1)])
    const { result } = renderHook(() => useAdminReports())
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockGetReports).toHaveBeenCalledTimes(1)

    act(() => result.current.refresh())
    await waitFor(() => expect(mockGetReports).toHaveBeenCalledTimes(2))
  })
})
