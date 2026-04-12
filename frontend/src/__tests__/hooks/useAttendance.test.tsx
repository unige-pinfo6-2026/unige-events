// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'
import { useAttendance } from '@/hooks/useAttendance'

vi.mock('@/services/attendanceApi', () => ({
  getMyAttendance: vi.fn(),
  attend: vi.fn(),
  unattend: vi.fn(),
}))

vi.mock('axios', () => ({
  default: { isAxiosError: vi.fn() },
  isAxiosError: vi.fn(),
}))

import { getMyAttendance, attend, unattend } from '@/services/attendanceApi'
import axios from 'axios'

const mockGetMyAttendance = getMyAttendance as ReturnType<typeof vi.fn>
const mockAttend = attend as ReturnType<typeof vi.fn>
const mockUnattend = unattend as ReturnType<typeof vi.fn>
const mockIsAxiosError = axios.isAxiosError as ReturnType<typeof vi.fn>

afterEach(() => {
  vi.resetAllMocks()
})

describe('useAttendance', () => {
  it('starts loading while fetching attendance status', () => {
    mockGetMyAttendance.mockResolvedValue(null)
    const { result } = renderHook(() => useAttendance(1, 10, null))
    expect(result.current.loading).toBe(true)
  })

  it('resolves with fetched attendance status', async () => {
    mockGetMyAttendance.mockResolvedValue('ATTENDING')

    const { result } = renderHook(() => useAttendance(1, 10, null))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.currentStatus).toBe('ATTENDING')
  })

  it('resolves with null status on fetch error', async () => {
    mockGetMyAttendance.mockRejectedValue(new Error('not auth'))

    const { result } = renderHook(() => useAttendance(1, 10, null))

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.currentStatus).toBeNull()
  })

  it('optimistically registers attendance and confirms on success', async () => {
    mockGetMyAttendance.mockResolvedValue(null)
    mockAttend.mockResolvedValue(undefined)

    const { result } = renderHook(() => useAttendance(1, 10, null))

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.toggle('ATTENDING') })

    expect(result.current.currentStatus).toBe('ATTENDING')
    expect(result.current.attendingCount).toBe(11)

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockAttend).toHaveBeenCalledWith(1, 'ATTENDING')
  })

  it('reverts optimistic state and sets error when attend fails with generic error', async () => {
    mockGetMyAttendance.mockResolvedValue(null)
    mockAttend.mockRejectedValue(new Error('generic'))
    mockIsAxiosError.mockReturnValue(false)

    const { result } = renderHook(() => useAttendance(1, 10, null))

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.toggle('ATTENDING') })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.currentStatus).toBeNull()
    expect(result.current.attendingCount).toBe(10)
    expect(result.current.error).toBe('Une erreur est survenue.')
  })

  it('sets isFull when attend fails with 409 conflict', async () => {
    mockGetMyAttendance.mockResolvedValue(null)
    const err = { response: { status: 409 } }
    mockAttend.mockRejectedValue(err)
    mockIsAxiosError.mockReturnValue(true)

    const { result } = renderHook(() => useAttendance(1, 10, null))

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.toggle('ATTENDING') })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.isFull).toBe(true)
    expect(result.current.error).toBeNull()
  })

  it('optimistically cancels attendance and confirms on success', async () => {
    mockGetMyAttendance.mockResolvedValue('ATTENDING')
    mockUnattend.mockResolvedValue(undefined)

    const { result } = renderHook(() => useAttendance(1, 10, null))

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.toggle('ATTENDING') })

    expect(result.current.currentStatus).toBeNull()
    expect(result.current.attendingCount).toBe(9)

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockUnattend).toHaveBeenCalledWith(1)
  })

  it('reverts state and sets error when unattend fails', async () => {
    mockGetMyAttendance.mockResolvedValue('ATTENDING')
    mockUnattend.mockRejectedValue(new Error('network'))
    mockIsAxiosError.mockReturnValue(false)

    const { result } = renderHook(() => useAttendance(1, 10, null))

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => { result.current.toggle('ATTENDING') })

    await waitFor(() => expect(result.current.loading).toBe(false))

    expect(result.current.currentStatus).toBe('ATTENDING')
    expect(result.current.attendingCount).toBe(10)
    expect(result.current.error).toBe('Une erreur est survenue.')
  })

  it('does nothing when toggle is called while loading', () => {
    mockGetMyAttendance.mockResolvedValue(null)

    const { result } = renderHook(() => useAttendance(1, 10, null))

    // loading is true initially
    act(() => { result.current.toggle('ATTENDING') })

    expect(mockAttend).not.toHaveBeenCalled()
  })
})
