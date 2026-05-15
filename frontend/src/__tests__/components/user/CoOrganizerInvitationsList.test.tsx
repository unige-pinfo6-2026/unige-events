import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, fireEvent } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

vi.mock('@/hooks/useCoOrganizerInvitations', () => ({
  useCoOrganizerInvitations: vi.fn(),
}))

import { useCoOrganizerInvitations } from '@/hooks/useCoOrganizerInvitations'
import CoOrganizerInvitationsList from '@/components/user/CoOrganizerInvitationsList'
import type { CoOrganizerInvitation } from '@/types/coOrganizer'

const mockHook = vi.mocked(useCoOrganizerInvitations)

function makeInvitation(eventId: number, title = 'Mon event'): CoOrganizerInvitation {
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

function setup(overrides: Partial<ReturnType<typeof useCoOrganizerInvitations>> = {}) {
  const accept = vi.fn().mockResolvedValue(undefined)
  const decline = vi.fn().mockResolvedValue(undefined)
  mockHook.mockReturnValue({
    invitations: [],
    pendingCount: 0,
    loading: false,
    error: null,
    accept,
    decline,
    refresh: vi.fn(),
    ...overrides,
  })
  return { accept, decline }
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

beforeEach(() => mockHook.mockReset())

describe('CoOrganizerInvitationsList', () => {
  it('renders empty state', () => {
    setup({ invitations: [] })
    render(<MemoryRouter><CoOrganizerInvitationsList /></MemoryRouter>)
    expect(screen.getByText(/aucune invitation/i)).toBeTruthy()
  })

  it('renders the section header', () => {
    setup()
    render(<MemoryRouter><CoOrganizerInvitationsList /></MemoryRouter>)
    expect(screen.getByRole('region', { name: /invitations.*co-organiser/i })).toBeTruthy()
  })

  it('renders one card per invitation with event title', () => {
    setup({ invitations: [makeInvitation(1, 'Event 1'), makeInvitation(2, 'Event 2')] })
    render(<MemoryRouter><CoOrganizerInvitationsList /></MemoryRouter>)
    expect(screen.getByText('Event 1')).toBeTruthy()
    expect(screen.getByText('Event 2')).toBeTruthy()
  })

  it('calls accept when "Accepter" is clicked', () => {
    const { accept } = setup({ invitations: [makeInvitation(42)] })
    render(<MemoryRouter><CoOrganizerInvitationsList /></MemoryRouter>)
    fireEvent.click(screen.getByRole('button', { name: /accepter/i }))
    expect(accept).toHaveBeenCalledWith(42)
  })

  it('calls decline when "Décliner" is clicked', () => {
    const { decline } = setup({ invitations: [makeInvitation(42)] })
    render(<MemoryRouter><CoOrganizerInvitationsList /></MemoryRouter>)
    fireEvent.click(screen.getByRole('button', { name: /décliner/i }))
    expect(decline).toHaveBeenCalledWith(42)
  })

  it('renders error message when error is set', () => {
    setup({ error: 'Boom' })
    render(<MemoryRouter><CoOrganizerInvitationsList /></MemoryRouter>)
    expect(screen.getByText('Boom')).toBeTruthy()
  })
})
