// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

const invitesMock = {
  invitations: [] as unknown[],
  pendingCount: 0,
  loading: false,
  error: null as string | null,
  accept: vi.fn().mockResolvedValue(undefined),
  decline: vi.fn().mockResolvedValue(undefined),
  refresh: vi.fn(),
}
const followsMock = {
  rows: [] as unknown[],
  loading: false,
  error: null as string | null,
  accept: vi.fn().mockResolvedValue(undefined),
  reject: vi.fn().mockResolvedValue(undefined),
  refresh: vi.fn(),
}
const showToast = vi.fn()

vi.mock('@/hooks/useCoOrganizerInvitations', () => ({ useCoOrganizerInvitations: () => invitesMock }))
vi.mock('@/hooks/useMyFollowRequests', () => ({ useMyFollowRequests: () => followsMock }))
vi.mock('@/hooks/useToast', () => ({ useToast: () => ({ showToast }) }))

import { RequestsInboxDropdown } from '@/components/utils/RequestsInboxDropdown'

const invitation = {
  id: 1,
  status: 'PENDING',
  invitedAt: '2026-05-01T10:00:00Z',
  event: { id: 42, title: 'Soirée Jazz', startDate: '2026-06-01T18:00:00Z' },
}
const followRow = {
  request: { id: 7, followerId: 'u-7', followedId: 'me', status: 'PENDING', createdAt: 'x' },
  follower: { id: 'u-7', username: 'alice', displayName: 'Alice', profilePublic: true, followerCount: 0, followingCount: 0, followStatus: null },
}

function renderDropdown() {
  return render(<MemoryRouter><RequestsInboxDropdown /></MemoryRouter>)
}

beforeEach(() => {
  invitesMock.invitations = []
  invitesMock.pendingCount = 0
  invitesMock.error = null
  followsMock.rows = []
  followsMock.error = null
  followsMock.accept.mockResolvedValue(undefined)
  showToast.mockReset()
})

afterEach(() => { cleanup(); vi.clearAllMocks() })

describe('RequestsInboxDropdown', () => {
  it('shows no badge when there is nothing pending', () => {
    renderDropdown()
    expect(screen.queryByLabelText(/demande.* en attente/)).toBeNull()
  })

  it('shows the combined pending count in the badge', () => {
    invitesMock.invitations = [invitation]
    invitesMock.pendingCount = 1
    followsMock.rows = [followRow, { ...followRow, request: { ...followRow.request, id: 8 } }]
    renderDropdown()
    // 1 invitation + 2 follow requests = 3
    expect(screen.getByLabelText('3 demandes en attente')).toBeTruthy()
  })

  it('delegates an invitation accept to the hook', () => {
    invitesMock.invitations = [invitation]
    invitesMock.pendingCount = 1
    renderDropdown()
    fireEvent.click(screen.getByLabelText("Accepter l'invitation pour Soirée Jazz"))
    expect(invitesMock.accept).toHaveBeenCalledWith(42)
  })

  it('surfaces a toast when accepting a follow request fails', async () => {
    followsMock.rows = [followRow]
    followsMock.accept.mockRejectedValueOnce(new Error('boom'))
    renderDropdown()
    fireEvent.click(screen.getByLabelText('Accepter la demande de Alice'))
    await waitFor(() => expect(showToast).toHaveBeenCalledWith('error', expect.stringContaining('accepter')))
  })
})
