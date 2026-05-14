import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/services/api', () => ({
  default: {
    get: vi.fn(),
    post: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

import api from '@/services/api'
import {
  acceptInvitation,
  declineInvitation,
  getMyInvitations,
  inviteCoOrganizer,
  listCoOrganizers,
  removeCoOrganizer,
} from '@/services/coOrganizerApi'
import type { CoOrganizer, CoOrganizerInvitation } from '@/types/coOrganizer'

const mockApiGet = vi.mocked(api.get)
const mockApiPost = vi.mocked(api.post)
const mockApiPatch = vi.mocked(api.patch)
const mockApiDelete = vi.mocked(api.delete)

const USER_UUID = '00000000-0000-0000-0000-000000000111'

const sampleCoOrg: CoOrganizer = {
  id: 1,
  userId: USER_UUID,
  displayName: 'Alice',
  avatarUrl: null,
  status: 'PENDING',
  invitedAt: '2026-05-14T10:00:00',
}

beforeEach(() => {
  mockApiGet.mockReset()
  mockApiPost.mockReset()
  mockApiPatch.mockReset()
  mockApiDelete.mockReset()
})

afterEach(() => vi.clearAllMocks())

describe('coOrganizerApi', () => {
  it('invites a co-organizer by UUID', async () => {
    mockApiPost.mockResolvedValue({ data: sampleCoOrg } as Awaited<ReturnType<typeof api.post>>)

    const result = await inviteCoOrganizer(42, USER_UUID)

    expect(mockApiPost).toHaveBeenCalledWith('/events/42/co-organizers', { userId: USER_UUID })
    expect(result).toEqual(sampleCoOrg)
  })

  it('lists co-organizers of an event', async () => {
    mockApiGet.mockResolvedValue({ data: [sampleCoOrg] } as Awaited<ReturnType<typeof api.get>>)

    const result = await listCoOrganizers(42)

    expect(mockApiGet).toHaveBeenCalledWith('/events/42/co-organizers')
    expect(result).toEqual([sampleCoOrg])
  })

  it('removes a co-organizer', async () => {
    mockApiDelete.mockResolvedValue({} as Awaited<ReturnType<typeof api.delete>>)

    await removeCoOrganizer(42, USER_UUID)

    expect(mockApiDelete).toHaveBeenCalledWith(`/events/42/co-organizers/${USER_UUID}`)
  })

  it('accepts an invitation', async () => {
    mockApiPatch.mockResolvedValue({
      data: { ...sampleCoOrg, status: 'ACCEPTED' },
    } as Awaited<ReturnType<typeof api.patch>>)

    const result = await acceptInvitation(42)

    expect(mockApiPatch).toHaveBeenCalledWith('/events/42/co-organizers/me/accept')
    expect(result.status).toBe('ACCEPTED')
  })

  it('declines an invitation', async () => {
    mockApiPatch.mockResolvedValue({} as Awaited<ReturnType<typeof api.patch>>)

    await declineInvitation(42)

    expect(mockApiPatch).toHaveBeenCalledWith('/events/42/co-organizers/me/decline')
  })

  it('fetches my invitations with default params', async () => {
    const sampleInvitation: CoOrganizerInvitation = {
      id: 1,
      event: {
        id: 42,
        title: 'X',
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
    mockApiGet.mockResolvedValue({ data: [sampleInvitation] } as Awaited<ReturnType<typeof api.get>>)

    const result = await getMyInvitations({ status: 'PENDING' })

    expect(mockApiGet).toHaveBeenCalledWith('/users/me/co-organizer-invitations', {
      params: { status: 'PENDING' },
    })
    expect(result).toHaveLength(1)
  })
})
