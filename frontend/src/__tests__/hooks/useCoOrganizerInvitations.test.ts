import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, renderHook, waitFor } from '@testing-library/react'

vi.mock('@/services/coOrganizerApi', () => ({
  acceptInvitation: vi.fn(),
  declineInvitation: vi.fn(),
  getMyInvitations: vi.fn(),
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({ isAuthenticated: true })),
}))

import {
  acceptInvitation,
  declineInvitation,
  getMyInvitations,
} from '@/services/coOrganizerApi'
import { useAuth } from '@/hooks/useAuth'
import { useCoOrganizerInvitations } from '@/hooks/useCoOrganizerInvitations'
import type { CoOrganizerInvitation } from '@/types/coOrganizer'

const mockGet = vi.mocked(getMyInvitations)
const mockAccept = vi.mocked(acceptInvitation)
const mockDecline = vi.mocked(declineInvitation)
const mockUseAuth = vi.mocked(useAuth)

function invitation(eventId: number, title = 'Event'): CoOrganizerInvitation {
  return {
    id: eventId,
    event: {
      id: eventId,
      title,
      location: 'L',
      startDate: '2026-05-20T10:00:00',
      endDate: '2026-05-20T12:00:00',
      allDay: false,
      category: 'CONFERENCE',
      status: 'PUBLISHED',
      creatorId: 'creator-uuid',
      attendingCount: 0,
      createdAt: '2026-05-01T10:00:00',
    },
    status: 'PENDING',
    invitedAt: '2026-05-14T10:00:00',
  }
}

beforeEach(() => {
  mockGet.mockReset()
  mockAccept.mockReset()
  mockDecline.mockReset()
  mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
})

afterEach(() => vi.clearAllMocks())

describe('useCoOrganizerInvitations', () => {
  it('loads pending invitations when authenticated', async () => {
    mockGet.mockResolvedValue([invitation(1), invitation(2)])

    const { result } = renderHook(() => useCoOrganizerInvitations())

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.invitations).toHaveLength(2)
    expect(result.current.pendingCount).toBe(2)
    expect(mockGet).toHaveBeenCalledWith({ status: 'PENDING' })
  })

  it('skips loading when not authenticated', async () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false } as ReturnType<typeof useAuth>)

    const { result } = renderHook(() => useCoOrganizerInvitations())

    await waitFor(() => expect(result.current.loading).toBe(false))
    expect(result.current.invitations).toEqual([])
    expect(mockGet).not.toHaveBeenCalled()
  })

  it('removes an invitation on accept', async () => {
    mockGet.mockResolvedValue([invitation(1), invitation(2)])
    mockAccept.mockResolvedValue({
      id: 1,
      userId: 'me',
      displayName: 'Me',
      avatarUrl: null,
      username: 'me.user',
      status: 'ACCEPTED',
      invitedAt: '2026-05-14T10:00:00',
    })

    const { result } = renderHook(() => useCoOrganizerInvitations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.accept(1)
    })

    expect(mockAccept).toHaveBeenCalledWith(1)
    expect(result.current.invitations).toHaveLength(1)
    expect(result.current.invitations[0].event.id).toBe(2)
  })

  it('rolls back accept on error', async () => {
    mockGet.mockResolvedValue([invitation(1)])
    mockAccept.mockRejectedValue(new Error('boom'))

    const { result } = renderHook(() => useCoOrganizerInvitations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.accept(1)
    })

    // After rollback + refresh, the row should be back.
    await waitFor(() => expect(result.current.invitations).toHaveLength(1))
  })

  it('removes an invitation on decline', async () => {
    mockGet.mockResolvedValue([invitation(1)])
    mockDecline.mockResolvedValue(undefined)

    const { result } = renderHook(() => useCoOrganizerInvitations())
    await waitFor(() => expect(result.current.loading).toBe(false))

    await act(async () => {
      await result.current.decline(1)
    })

    expect(mockDecline).toHaveBeenCalledWith(1)
    expect(result.current.invitations).toHaveLength(0)
  })
})
