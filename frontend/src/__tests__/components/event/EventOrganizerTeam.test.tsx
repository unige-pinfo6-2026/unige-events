import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

vi.mock('@/hooks/useCoOrganizers', () => ({
  useCoOrganizers: vi.fn(),
}))

import { useCoOrganizers } from '@/hooks/useCoOrganizers'
import EventOrganizerTeam from '@/components/event/EventOrganizerTeam'
import type { CoOrganizer } from '@/types/coOrganizer'

const mockUseCoOrganizers = vi.mocked(useCoOrganizers)

const CREATOR_UUID = '00000000-0000-0000-0000-000000000001'

function setupHook(overrides: Partial<ReturnType<typeof useCoOrganizers>> = {}) {
  mockUseCoOrganizers.mockReturnValue({
    coOrganizers: [],
    loading: false,
    error: null,
    invite: vi.fn(),
    remove: vi.fn(),
    refresh: vi.fn(),
    ...overrides,
  })
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

beforeEach(() => mockUseCoOrganizers.mockReset())

function renderWithRouter(ui: React.ReactNode) {
  return render(<MemoryRouter>{ui}</MemoryRouter>)
}

describe('EventOrganizerTeam', () => {
  it('always renders the primary creator', () => {
    setupHook()
    renderWithRouter(
      <EventOrganizerTeam
        eventId={42}
        creatorId={CREATOR_UUID}
        creatorUsername="charlie"
        creatorDisplayName="Charlie"
        creatorAvatarUrl={null}
      />,
    )
    expect(screen.getByText('Charlie')).toBeTruthy()
    expect(screen.getByText('Organisateur')).toBeTruthy()
  })

  it('renders ACCEPTED co-organizers with the right badge', () => {
    const alice: CoOrganizer = {
      id: 1,
      userId: '00000000-0000-0000-0000-000000000002',
      displayName: 'Alice',
      avatarUrl: null,
      username: 'alice',
      status: 'ACCEPTED',
      invitedAt: '2026-05-14T10:00:00',
    }
    setupHook({ coOrganizers: [alice] })
    renderWithRouter(
      <EventOrganizerTeam
        eventId={42}
        creatorId={CREATOR_UUID}
        creatorUsername="charlie"
        creatorDisplayName="Charlie"
        creatorAvatarUrl={null}
      />,
    )
    expect(screen.getByText('Alice')).toBeTruthy()
    expect(screen.getByText('Co-organisateur')).toBeTruthy()
  })

  it('filters out PENDING and DECLINED co-organizers (display ACCEPTED only)', () => {
    const pending: CoOrganizer = {
      id: 1,
      userId: '00000000-0000-0000-0000-000000000010',
      displayName: 'Pending',
      avatarUrl: null,
      username: 'pending',
      status: 'PENDING',
      invitedAt: '2026-05-14T10:00:00',
    }
    const declined: CoOrganizer = {
      id: 2,
      userId: '00000000-0000-0000-0000-000000000011',
      displayName: 'Declined',
      avatarUrl: null,
      username: 'declined',
      status: 'DECLINED',
      invitedAt: '2026-05-14T10:00:00',
    }
    setupHook({ coOrganizers: [pending, declined] })
    renderWithRouter(
      <EventOrganizerTeam
        eventId={42}
        creatorId={CREATOR_UUID}
        creatorUsername="charlie"
        creatorDisplayName="Charlie"
        creatorAvatarUrl={null}
      />,
    )
    expect(screen.queryByText('Pending')).toBeNull()
    expect(screen.queryByText('Declined')).toBeNull()
  })

  it('falls back to the UUID prefix when displayName and username are null', () => {
    // SCRUM-169 — userDisplayLabel order : displayName > @username > UUID prefix.
    // Here both are absent → falls to the first 8 chars of CREATOR_UUID.
    // CREATOR_UUID starts with "00000000-".
    setupHook()
    renderWithRouter(
      <EventOrganizerTeam
        eventId={42}
        creatorId={CREATOR_UUID}
        creatorUsername={null as unknown as string}
        creatorDisplayName={null}
        creatorAvatarUrl={null}
      />,
    )
    expect(screen.getByText(CREATOR_UUID.slice(0, 8))).toBeTruthy()
  })
})
