import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

vi.mock('@/hooks/usePublicOrganizers', () => ({
  usePublicOrganizers: vi.fn(),
}))

import { usePublicOrganizers, type PublicOrganizer } from '@/hooks/usePublicOrganizers'
import EventOrganizerTeam from '@/components/event/EventOrganizerTeam'

const mockUsePublicOrganizers = vi.mocked(usePublicOrganizers)

const CREATOR_UUID = '00000000-0000-0000-0000-000000000001'

function setupHook(overrides: Partial<ReturnType<typeof usePublicOrganizers>> = {}) {
  mockUsePublicOrganizers.mockReturnValue({
    coOrganizers: [],
    loading: false,
    ...overrides,
  })
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

beforeEach(() => mockUsePublicOrganizers.mockReset())

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

  it('renders co-organizers resolved by usePublicOrganizers with the right badge', () => {
    const alice: PublicOrganizer = {
      userId: '00000000-0000-0000-0000-000000000002',
      displayName: 'Alice',
      avatarUrl: null,
      username: 'alice',
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

  it('passes eventId and creatorId to the public-organizers hook (creator filtered out there)', () => {
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
    expect(mockUsePublicOrganizers).toHaveBeenCalledWith(42, CREATOR_UUID)
  })

  it('renders the loading skeleton placeholder while co-organizers load', () => {
    setupHook({ loading: true })
    renderWithRouter(
      <EventOrganizerTeam
        eventId={42}
        creatorId={CREATOR_UUID}
        creatorUsername="charlie"
        creatorDisplayName="Charlie"
        creatorAvatarUrl={null}
      />,
    )
    // Creator is always shown; the skeleton occupies an extra <li> in the
    // co-organizer slot while loading is true.
    expect(screen.getByText('Charlie')).toBeTruthy()
    expect(document.querySelectorAll('li').length).toBeGreaterThan(1)
  })

  it('renders an avatar <img> for the creator when an avatarUrl is provided', () => {
    setupHook()
    renderWithRouter(
      <EventOrganizerTeam
        eventId={42}
        creatorId={CREATOR_UUID}
        creatorUsername="charlie"
        creatorDisplayName="Charlie"
        creatorAvatarUrl="https://example.com/charlie.png"
      />,
    )
    const img = document.querySelector('img') as HTMLImageElement | null
    expect(img).toBeTruthy()
    expect(img?.getAttribute('src')).toBe('https://example.com/charlie.png')
  })

  it('falls back to the short UUID prefix — never @<full-uuid> — when creator name and username are null', () => {
    // The EventDetailPage hardening passes username=null (not the raw UUID)
    // while the creator fetch is pending. userDisplayLabel order:
    // displayName > @username > UUID prefix → here the 8-char prefix.
    setupHook()
    renderWithRouter(
      <EventOrganizerTeam
        eventId={42}
        creatorId={CREATOR_UUID}
        creatorUsername={null}
        creatorDisplayName={null}
        creatorAvatarUrl={null}
      />,
    )
    expect(screen.getByText(CREATOR_UUID.slice(0, 8))).toBeTruthy()
    // The full UUID with an @ prefix must never appear.
    expect(screen.queryByText('@' + CREATOR_UUID)).toBeNull()
  })
})
