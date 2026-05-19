import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { AxiosError } from 'axios'

vi.mock('@/services/reportApi', () => ({
  reportComment: vi.fn(),
}))

const showToastMock = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: showToastMock, toasts: [], dismiss: vi.fn() }),
}))

import { reportComment } from '@/services/reportApi'
import { useReportComment } from '@/hooks/useReportComment'

const mockReport = vi.mocked(reportComment)

function axiosErrorWithStatus(status: number): AxiosError {
  // The AxiosError constructor is finicky ; construct one directly.
  const err = new AxiosError('synthetic', 'synthetic')
  // @ts-expect-error — assigning a stripped-down response is sufficient for the hook's read
  err.response = { status }
  return err
}

beforeEach(() => {
  mockReport.mockReset()
  showToastMock.mockReset()
})

afterEach(() => vi.clearAllMocks())

describe('useReportComment', () => {
  it('happy path 201 resolves true and shows success toast', async () => {
    mockReport.mockResolvedValue()
    const { result } = renderHook(() => useReportComment(42))

    let outcome = false
    await act(async () => {
      outcome = await result.current.submit({ reason: 'SPAM' })
    })

    expect(outcome).toBe(true)
    expect(mockReport).toHaveBeenCalledWith(42, { reason: 'SPAM' })
    expect(showToastMock).toHaveBeenCalledWith('success', expect.stringContaining('signalé'))
  })

  it('409 already-reported resolves true and shows specific toast', async () => {
    mockReport.mockRejectedValue(axiosErrorWithStatus(409))
    const { result } = renderHook(() => useReportComment(42))

    let outcome = false
    await act(async () => {
      outcome = await result.current.submit({ reason: 'SPAM' })
    })

    expect(outcome).toBe(true)
    expect(showToastMock).toHaveBeenCalledWith('error', expect.stringContaining('déjà signalé'))
  })

  it('422 cannot-report-own resolves true and shows defensive toast', async () => {
    mockReport.mockRejectedValue(axiosErrorWithStatus(422))
    const { result } = renderHook(() => useReportComment(42))

    let outcome = false
    await act(async () => {
      outcome = await result.current.submit({ reason: 'SPAM' })
    })

    expect(outcome).toBe(true)
    expect(showToastMock).toHaveBeenCalledWith('error', expect.stringContaining('propre commentaire'))
  })

  it('5xx resolves false (modal stays open) and shows generic error toast', async () => {
    mockReport.mockRejectedValue(axiosErrorWithStatus(500))
    const { result } = renderHook(() => useReportComment(42))

    let outcome = true
    await act(async () => {
      outcome = await result.current.submit({ reason: 'SPAM' })
    })

    expect(outcome).toBe(false)
    expect(showToastMock).toHaveBeenCalledWith('error', expect.stringContaining('réessayez'))
  })

  it('toggling stays true until resolved', async () => {
    let resolveReport: (() => void) | null = null
    mockReport.mockImplementationOnce(() => new Promise((res) => { resolveReport = () => res() }))
    const { result } = renderHook(() => useReportComment(42))

    act(() => { void result.current.submit({ reason: 'SPAM' }) })
    expect(result.current.submitting).toBe(true)

    act(() => { resolveReport?.() })
    await waitFor(() => expect(result.current.submitting).toBe(false))
  })

  it('concurrent submit while submitting=true is a no-op', async () => {
    let resolveReport: (() => void) | null = null
    mockReport.mockImplementationOnce(() => new Promise((res) => { resolveReport = () => res() }))
    const { result } = renderHook(() => useReportComment(42))

    act(() => { void result.current.submit({ reason: 'SPAM' }) })
    let secondOutcome = true
    await act(async () => {
      secondOutcome = await result.current.submit({ reason: 'FAKE' })
    })

    expect(secondOutcome).toBe(false)
    expect(mockReport).toHaveBeenCalledTimes(1)

    act(() => { resolveReport?.() })
    await waitFor(() => expect(result.current.submitting).toBe(false))
  })

  it('non-AxiosError (plain Error) falls through to the generic retry toast', async () => {
    // The `err instanceof AxiosError` branch: when the thrown thing isn't an
    // axios error (e.g. a programming bug in a downstream interceptor), the
    // hook must not crash and must show the generic retry toast + keep the
    // modal open.
    mockReport.mockRejectedValue(new Error('something else'))
    const { result } = renderHook(() => useReportComment(42))

    let outcome = true
    await act(async () => {
      outcome = await result.current.submit({ reason: 'SPAM' })
    })

    expect(outcome).toBe(false)
    expect(showToastMock).toHaveBeenCalledWith('error', expect.stringContaining('réessayez'))
  })

  it('AxiosError without response object falls through to the generic toast', async () => {
    // Defensive — an AxiosError without a response field (network reset
    // before headers) should not match either the 409 nor the 422 branch.
    // Constructed with the minimal signature ; `response` is optional in
    // AxiosError so it's `undefined` by default.
    const err = new AxiosError('reset')
    mockReport.mockRejectedValue(err)
    const { result } = renderHook(() => useReportComment(42))

    let outcome = true
    await act(async () => {
      outcome = await result.current.submit({ reason: 'SPAM' })
    })

    expect(outcome).toBe(false)
    expect(showToastMock).toHaveBeenCalledWith('error', expect.stringContaining('réessayez'))
  })
})
