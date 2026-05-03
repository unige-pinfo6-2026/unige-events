import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'

vi.mock('@/services/reportApi', () => ({
  reportEvent: vi.fn(),
}))

const mockShowToast = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: mockShowToast }),
}))

import { reportEvent } from '@/services/reportApi'
import { useReport } from '@/hooks/useReport'
import type { ReportReason } from '@/hooks/useReport'

const mockReportEvent = reportEvent as ReturnType<typeof vi.fn>

afterEach(() => {
  vi.resetAllMocks()
})

describe('useReport — initial state', () => {
  it('starts with isOpen=false and submitting=false', () => {
    const { result } = renderHook(() => useReport(42))
    expect(result.current.isOpen).toBe(false)
    expect(result.current.submitting).toBe(false)
  })
})

describe('useReport — open / close', () => {
  it('open() sets isOpen to true', () => {
    const { result } = renderHook(() => useReport(42))
    act(() => result.current.open())
    expect(result.current.isOpen).toBe(true)
  })

  it('close() sets isOpen to false after open', () => {
    const { result } = renderHook(() => useReport(42))
    act(() => result.current.open())
    act(() => result.current.close())
    expect(result.current.isOpen).toBe(false)
  })
})

describe('useReport — submit success', () => {
  it('calls reportEvent with reason only when no description', async () => {
    mockReportEvent.mockResolvedValue(undefined)
    const { result } = renderHook(() => useReport(42))
    act(() => result.current.open())

    await act(async () => {
      await result.current.submit('Spam' as ReportReason)
    })

    expect(mockReportEvent).toHaveBeenCalledWith(42, { reason: 'Spam' })
  })

  it('combines reason and description when description is provided', async () => {
    mockReportEvent.mockResolvedValue(undefined)
    const { result } = renderHook(() => useReport(7))

    await act(async () => {
      await result.current.submit('Autre' as ReportReason, 'Détails supplémentaires')
    })

    expect(mockReportEvent).toHaveBeenCalledWith(7, {
      reason: 'Autre\n\nDétails supplémentaires',
    })
  })

  it('ignores blank description (whitespace only)', async () => {
    mockReportEvent.mockResolvedValue(undefined)
    const { result } = renderHook(() => useReport(1))

    await act(async () => {
      await result.current.submit('Spam' as ReportReason, '   ')
    })

    expect(mockReportEvent).toHaveBeenCalledWith(1, { reason: 'Spam' })
  })

  it('shows success toast and closes modal on success', async () => {
    mockReportEvent.mockResolvedValue(undefined)
    const { result } = renderHook(() => useReport(42))
    act(() => result.current.open())

    await act(async () => {
      await result.current.submit('Spam' as ReportReason)
    })

    expect(mockShowToast).toHaveBeenCalledWith('success', 'Merci pour votre signalement.')
    expect(result.current.isOpen).toBe(false)
    expect(result.current.submitting).toBe(false)
  })
})

describe('useReport — submit error', () => {
  it('shows 409 toast when already reported', async () => {
    const error = Object.assign(new axios.AxiosError('Conflict'), {
      response: { status: 409 },
    })
    mockReportEvent.mockRejectedValue(error)
    const { result } = renderHook(() => useReport(42))
    act(() => result.current.open())

    await act(async () => {
      await result.current.submit('Spam' as ReportReason)
    })

    expect(mockShowToast).toHaveBeenCalledWith('error', 'Vous avez déjà signalé cet événement.')
    expect(result.current.isOpen).toBe(true)
    expect(result.current.submitting).toBe(false)
  })

  it('shows generic error toast on other errors', async () => {
    mockReportEvent.mockRejectedValue(new Error('Network error'))
    const { result } = renderHook(() => useReport(42))

    await act(async () => {
      await result.current.submit('Contenu inapproprié' as ReportReason)
    })

    expect(mockShowToast).toHaveBeenCalledWith('error', 'Impossible d\'envoyer le signalement.')
    expect(result.current.submitting).toBe(false)
  })

  it('does not close modal on error', async () => {
    mockReportEvent.mockRejectedValue(new Error('fail'))
    const { result } = renderHook(() => useReport(42))
    act(() => result.current.open())

    await act(async () => {
      await result.current.submit('Spam' as ReportReason)
    })

    expect(result.current.isOpen).toBe(true)
  })

  it('sets submitting=true during API call and false after', async () => {
    let resolve: () => void = () => {}
    mockReportEvent.mockReturnValue(new Promise<void>((r) => { resolve = r }))
    const { result } = renderHook(() => useReport(42))

    act(() => { void result.current.submit('Spam' as ReportReason) })
    expect(result.current.submitting).toBe(true)

    await act(async () => { resolve() })
    expect(result.current.submitting).toBe(false)
  })
})
