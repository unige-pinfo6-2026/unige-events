// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { RequestsInbox } from '@/components/utils/RequestsInbox'
import type { CoOrganizerInvitation } from '@/types/coOrganizer'
import type { FollowRequestRow } from '@/hooks/useMyFollowRequests'

afterEach(() => { cleanup() })

const invitation = {
  id: 1,
  status: 'PENDING',
  invitedAt: '2026-05-01T10:00:00Z',
  event: { id: 42, title: 'Soirée Jazz', startDate: '2026-06-01T18:00:00Z' },
} as unknown as CoOrganizerInvitation

const followRow: FollowRequestRow = {
  request: { id: 7, followerId: 'u-7', followedId: 'me', status: 'PENDING', createdAt: '2026-05-02T10:00:00Z' },
  follower: {
    id: 'u-7', username: 'alice', displayName: 'Alice',
    profilePublic: true, followerCount: 0, followingCount: 0, followStatus: null,
  },
}

function noop() { return Promise.resolve() }

function renderInbox(props: Partial<React.ComponentProps<typeof RequestsInbox>> = {}) {
  return render(
    <MemoryRouter>
      <RequestsInbox
        invitations={[invitation]}
        followRequests={[followRow]}
        loading={false}
        error={null}
        onAcceptInvitation={noop}
        onDeclineInvitation={noop}
        onAcceptFollow={noop}
        onRejectFollow={noop}
        {...props}
      />
    </MemoryRouter>,
  )
}

describe('RequestsInbox', () => {
  it('shows a loading state', () => {
    const { container } = renderInbox({ loading: true })
    expect(container.querySelector('[aria-busy="true"]')).toBeTruthy()
  })

  it('shows the error message', () => {
    renderInbox({ error: 'Boom' })
    expect(screen.getByText('Boom')).toBeTruthy()
  })

  it('shows the empty state when there is nothing pending', () => {
    renderInbox({ invitations: [], followRequests: [] })
    expect(screen.getByText('Aucune demande en attente')).toBeTruthy()
  })

  it('renders both sections with their rows', () => {
    renderInbox()
    expect(screen.getByText('Invitations à co-organiser')).toBeTruthy()
    expect(screen.getByText('Soirée Jazz')).toBeTruthy()
    expect(screen.getByText('Demandes de suivi')).toBeTruthy()
    expect(screen.getByText('Alice')).toBeTruthy()
  })

  it('calls onAcceptInvitation with the event id', () => {
    const onAcceptInvitation = vi.fn().mockResolvedValue(undefined)
    renderInbox({ onAcceptInvitation })
    fireEvent.click(screen.getByLabelText("Accepter l'invitation pour Soirée Jazz"))
    expect(onAcceptInvitation).toHaveBeenCalledWith(42)
  })

  it('calls onRejectFollow with the follow request id', () => {
    const onRejectFollow = vi.fn().mockResolvedValue(undefined)
    renderInbox({ onRejectFollow })
    fireEvent.click(screen.getByLabelText('Refuser la demande de Alice'))
    expect(onRejectFollow).toHaveBeenCalledWith(7)
  })

  it('falls back to a neutral name when the follower profile failed to resolve', () => {
    renderInbox({ followRequests: [{ ...followRow, follower: null }] })
    expect(screen.getByText('Utilisateur')).toBeTruthy()
  })
})
