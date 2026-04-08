// @vitest-environment jsdom

import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import axios from 'axios'

vi.mock('@/services/attendanceApi', () => ({
  attend: vi.fn(),
  unattend: vi.fn(),
}))

import { attend, unattend } from '@/services/attendanceApi'
import { useAttendance } from '@/hooks/useAttendance'

const mockAttend = attend as ReturnType<typeof vi.fn>
const mockUnattend = unattend as ReturnType<typeof vi.fn>

const sampleAttendance = {
  id: 1,
  userId: 'user-abc',
  eventId: 42,
  status: 'ATTENDING' as const,
  createdAt: '2026-04-08T10:00:00.000Z',
}

afterEach(() => vi.resetAllMocks())

describe('useAttendance', () => {
  it('initializes with provided values', () => {
    const { result } = renderHook(() => useAttendance(42, 5, 3, 'ATTENDING'))
    expect(result.current.currentStatus).toBe('ATTENDING')
    expect(result.current.attendingCount).toBe(5)
    expect(result.current.interestedCount).toBe(3)
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
    expect(result.current.isFull).toBe(false)
  })

  it('initializes with null status when not attended', () => {
    const { result } = renderHook(() => useAttendance(42, 0, 0, null))
    expect(result.current.currentStatus).toBeNull()
  })

  describe('toggle ON (set status)', () => {
    it('optimistically sets ATTENDING and increments attendingCount', async () => {
      mockAttend.mockResolvedValue(sampleAttendance)
      const { result } = renderHook(() => useAttendance(42, 5, 3, null))

      act(() => result.current.toggle('ATTENDING'))

      expect(result.current.currentStatus).toBe('ATTENDING')
      expect(result.current.attendingCount).toBe(6)
      expect(result.current.interestedCount).toBe(3)
      expect(result.current.loading).toBe(true)

      await waitFor(() => expect(result.current.loading).toBe(false))
      expect(mockAttend).toHaveBeenCalledWith(42, 'ATTENDING')
    })

    it('optimistically sets INTERESTED and increments interestedCount', async () => {
      mockAttend.mockResolvedValue({ ...sampleAttendance, status: 'INTERESTED' })
      const { result } = renderHook(() => useAttendance(42, 5, 3, null))

      act(() => result.current.toggle('INTERESTED'))

      expect(result.current.currentStatus).toBe('INTERESTED')
      expect(result.current.interestedCount).toBe(4)
      expect(result.current.attendingCount).toBe(5)

      await waitFor(() => expect(result.current.loading).toBe(false))
      expect(mockAttend).toHaveBeenCalledWith(42, 'INTERESTED')
    })

    it('switches from INTERESTED to ATTENDING adjusting both counts', async () => {
      mockAttend.mockResolvedValue(sampleAttendance)
      const { result } = renderHook(() => useAttendance(42, 5, 3, 'INTERESTED'))

      act(() => result.current.toggle('ATTENDING'))

      expect(result.current.currentStatus).toBe('ATTENDING')
      expect(result.current.attendingCount).toBe(6)
      expect(result.current.interestedCount).toBe(2)

      await waitFor(() => expect(result.current.loading).toBe(false))
    })
  })

  describe('toggle OFF (unset status)', () => {
    it('optimistically clears ATTENDING and decrements attendingCount', async () => {
      mockUnattend.mockResolvedValue(undefined)
      const { result } = renderHook(() => useAttendance(42, 5, 3, 'ATTENDING'))

      act(() => result.current.toggle('ATTENDING'))

      expect(result.current.currentStatus).toBeNull()
      expect(result.current.attendingCount).toBe(4)
      expect(result.current.interestedCount).toBe(3)
      expect(result.current.loading).toBe(true)

      await waitFor(() => expect(result.current.loading).toBe(false))
      expect(mockUnattend).toHaveBeenCalledWith(42)
    })

    it('optimistically clears INTERESTED and decrements interestedCount', async () => {
      mockUnattend.mockResolvedValue(undefined)
      const { result } = renderHook(() => useAttendance(42, 5, 3, 'INTERESTED'))

      act(() => result.current.toggle('INTERESTED'))

      expect(result.current.currentStatus).toBeNull()
      expect(result.current.interestedCount).toBe(2)
      expect(result.current.attendingCount).toBe(5)

      await waitFor(() => expect(result.current.loading).toBe(false))
    })

    it('does not decrement below zero', async () => {
      mockUnattend.mockResolvedValue(undefined)
      const { result } = renderHook(() => useAttendance(42, 0, 0, 'ATTENDING'))

      act(() => result.current.toggle('ATTENDING'))

      expect(result.current.attendingCount).toBe(0)

      await waitFor(() => expect(result.current.loading).toBe(false))
    })
  })

  describe('optimistic rollback on error', () => {
    it('rolls back state when attend API fails', async () => {
      mockAttend.mockRejectedValue(new Error('Network error'))
      const { result } = renderHook(() => useAttendance(42, 5, 3, null))

      act(() => result.current.toggle('ATTENDING'))

      await waitFor(() => expect(result.current.loading).toBe(false))

      expect(result.current.currentStatus).toBeNull()
      expect(result.current.attendingCount).toBe(5)
      expect(result.current.interestedCount).toBe(3)
      expect(result.current.error).toBe('Une erreur est survenue.')
    })

    it('rolls back state when unattend API fails', async () => {
      mockUnattend.mockRejectedValue(new Error('Network error'))
      const { result } = renderHook(() => useAttendance(42, 5, 3, 'ATTENDING'))

      act(() => result.current.toggle('ATTENDING'))

      await waitFor(() => expect(result.current.loading).toBe(false))

      expect(result.current.currentStatus).toBe('ATTENDING')
      expect(result.current.attendingCount).toBe(5)
      expect(result.current.error).toBe('Une erreur est survenue.')
    })
  })

  describe('409 → isFull flag', () => {
    it('sets isFull on 409 and rolls back state', async () => {
      const axiosError = new axios.AxiosError('Conflict', 'ERR_BAD_RESPONSE')
      Object.defineProperty(axiosError, 'response', { value: { status: 409 }, writable: false })
      mockAttend.mockRejectedValue(axiosError)

      const { result } = renderHook(() => useAttendance(42, 5, 3, null))

      act(() => result.current.toggle('ATTENDING'))

      await waitFor(() => expect(result.current.loading).toBe(false))

      expect(result.current.isFull).toBe(true)
      expect(result.current.error).toBeNull()
      expect(result.current.currentStatus).toBeNull()
      expect(result.current.attendingCount).toBe(5)
    })

    it('does not set isFull on non-409 errors', async () => {
      const axiosError = new axios.AxiosError('Server Error', 'ERR_BAD_RESPONSE')
      Object.defineProperty(axiosError, 'response', { value: { status: 500 }, writable: false })
      mockAttend.mockRejectedValue(axiosError)

      const { result } = renderHook(() => useAttendance(42, 5, 3, null))

      act(() => result.current.toggle('ATTENDING'))

      await waitFor(() => expect(result.current.loading).toBe(false))

      expect(result.current.isFull).toBe(false)
      expect(result.current.error).toBe('Une erreur est survenue.')
    })
  })

  it('ignores toggle call while loading is true', async () => {
    let resolve: (v: unknown) => void = () => {}
    mockAttend.mockReturnValue(new Promise((r) => { resolve = r }))

    const { result } = renderHook(() => useAttendance(42, 5, 3, null))

    act(() => result.current.toggle('ATTENDING'))
    expect(result.current.loading).toBe(true)

    act(() => result.current.toggle('INTERESTED'))
    expect(result.current.currentStatus).toBe('ATTENDING')

    await act(async () => { resolve(sampleAttendance) })
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(mockAttend).toHaveBeenCalledTimes(1)
  })

  it('clears isFull when toggling again after 409', async () => {
    const axiosError = new axios.AxiosError('Conflict', 'ERR_BAD_RESPONSE')
    Object.defineProperty(axiosError, 'response', { value: { status: 409 }, writable: false })
    mockAttend.mockRejectedValueOnce(axiosError).mockResolvedValue(sampleAttendance)

    const { result } = renderHook(() => useAttendance(42, 5, 3, null))

    act(() => result.current.toggle('ATTENDING'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.isFull).toBe(true)

    act(() => result.current.toggle('INTERESTED'))
    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.isFull).toBe(false)
  })
})
