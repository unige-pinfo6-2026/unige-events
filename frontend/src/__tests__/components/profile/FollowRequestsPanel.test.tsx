// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

vi.mock('@/services/followApi', () => ({
  getMyFollowRequests: vi.fn(),
  acceptFollowRequest: vi.fn(),
  rejectFollowRequest: vi.fn(),
}))

vi.mock('@/services/userService', () => ({
  getPublicProfile: vi.fn(),
}))

const mockShowToast = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: mockShowToast, toasts: [], dismiss: vi.fn() }),
}))

import {
  acceptFollowRequest,
  getMyFollowRequests,
  rejectFollowRequest,
} from '@/services/followApi'
import { getPublicProfile } from '@/services/userService'
import FollowRequestsPanel from '@/components/profile/FollowRequestsPanel'

const mockGetRequests = getMyFollowRequests as ReturnType<typeof vi.fn>
const mockAccept = acceptFollowRequest as ReturnType<typeof vi.fn>
const mockReject = rejectFollowRequest as ReturnType<typeof vi.fn>
const mockGetProfile = getPublicProfile as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const aliceRequest = {
  id: 1,
  followerId: 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c',
  followedId: 'me',
  status: 'PENDING' as const,
  createdAt: '2026-05-14T10:00:00Z',
}
const aliceProfile = {
  id: aliceRequest.followerId,
  displayName: 'Alice',
  followerCount: 0,
  followingCount: 0,
  followStatus: null,
}

function renderPanel() {
  return render(
    <MemoryRouter>
      <FollowRequestsPanel />
    </MemoryRouter>,
  )
}

describe('FollowRequestsPanel', () => {
  it('renders the heading on every state', async () => {
    mockGetRequests.mockResolvedValue([])
    renderPanel()
    expect(await screen.findByRole('heading', { name: 'Demandes de suivi reçues' })).toBeTruthy()
  })

  it('shows the loading skeleton during the initial fetch', () => {
    mockGetRequests.mockImplementation(() => new Promise(() => {}))
    const { container } = renderPanel()
    expect(container.querySelector('[aria-busy="true"]')).toBeTruthy()
  })

  it('renders the empty state when there are no requests', async () => {
    mockGetRequests.mockResolvedValue([])
    renderPanel()

    expect(await screen.findByText('Aucune demande de suivi en attente.')).toBeTruthy()
  })

  it('renders a row per request with avatar, displayName, and Accepter/Refuser buttons', async () => {
    mockGetRequests.mockResolvedValue([aliceRequest])
    mockGetProfile.mockResolvedValue(aliceProfile)
    renderPanel()

    expect(await screen.findByText('Alice')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Accepter la demande de Alice' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Refuser la demande de Alice' })).toBeTruthy()
    // displayName link points to the requester's profile.
    const link = screen.getAllByRole('link').find(el => el.getAttribute('href') === `/profile/${aliceRequest.followerId}`)
    expect(link).toBeTruthy()
  })

  it('falls back to "Utilisateur" when the follower profile fails to resolve', async () => {
    mockGetRequests.mockResolvedValue([aliceRequest])
    mockGetProfile.mockRejectedValue(new Error('boom'))
    renderPanel()

    expect(await screen.findByText('Utilisateur')).toBeTruthy()
  })

  it('accepting removes the row optimistically + calls the service', async () => {
    mockGetRequests
      .mockResolvedValueOnce([aliceRequest])
      .mockResolvedValueOnce([])
    mockGetProfile.mockResolvedValue(aliceProfile)
    mockAccept.mockResolvedValue({ ...aliceRequest, status: 'ACCEPTED' })

    renderPanel()
    const acceptBtn = await screen.findByRole('button', { name: 'Accepter la demande de Alice' })
    fireEvent.click(acceptBtn)

    await waitFor(() => expect(mockAccept).toHaveBeenCalledWith(aliceRequest.id))
    await waitFor(() => expect(screen.queryByText('Alice')).toBeNull())
    expect(screen.getByText('Aucune demande de suivi en attente.')).toBeTruthy()
  })

  it('rejecting removes the row optimistically + calls the service', async () => {
    mockGetRequests
      .mockResolvedValueOnce([aliceRequest])
      .mockResolvedValueOnce([])
    mockGetProfile.mockResolvedValue(aliceProfile)
    mockReject.mockResolvedValue(undefined)

    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: 'Refuser la demande de Alice' }))

    await waitFor(() => expect(mockReject).toHaveBeenCalledWith(aliceRequest.id))
    await waitFor(() => expect(screen.queryByText('Alice')).toBeNull())
  })

  it('toasts an error and keeps the row on accept failure', async () => {
    mockGetRequests.mockResolvedValue([aliceRequest])
    mockGetProfile.mockResolvedValue(aliceProfile)
    mockAccept.mockRejectedValue(new Error('boom'))

    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: 'Accepter la demande de Alice' }))

    await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith(
      'error', "Impossible d'accepter cette demande.",
    ))
    expect(screen.getByText('Alice')).toBeTruthy()
  })

  it('toasts an error and keeps the row on reject failure', async () => {
    mockGetRequests.mockResolvedValue([aliceRequest])
    mockGetProfile.mockResolvedValue(aliceProfile)
    mockReject.mockRejectedValue(new Error('boom'))

    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: 'Refuser la demande de Alice' }))

    await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith(
      'error', 'Impossible de refuser cette demande.',
    ))
    expect(screen.getByText('Alice')).toBeTruthy()
  })

  it('renders the error message when the initial fetch fails', async () => {
    mockGetRequests.mockRejectedValue(new Error('network'))

    renderPanel()

    expect(await screen.findByText('Impossible de charger les demandes de suivi.')).toBeTruthy()
  })

  it('renders the count badge when at least one request is present', async () => {
    mockGetRequests.mockResolvedValue([aliceRequest])
    mockGetProfile.mockResolvedValue(aliceProfile)
    renderPanel()

    await screen.findByText('Alice')
    expect(screen.getByText('1')).toBeTruthy()
  })
})
