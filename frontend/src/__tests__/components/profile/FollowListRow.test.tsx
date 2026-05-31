// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import FollowListRow from '@/components/profile/FollowListRow'
import type { UserPublicResponse } from '@/types/user'

afterEach(() => { cleanup() })

function baseUser(overrides: Partial<UserPublicResponse> = {}): UserPublicResponse {
  return {
    id: 'a-id',
    username: 'alice',
    displayName: 'Alice',
    profilePublic: true,
    followerCount: 0,
    followingCount: 0,
    followStatus: null,
    ...overrides,
  }
}

function renderRow(user: UserPublicResponse) {
  return render(
    <MemoryRouter>
      <ul>
        <FollowListRow user={user} />
      </ul>
    </MemoryRouter>,
  )
}

describe('FollowListRow', () => {
  it('renders the displayName and links to /profile/{username}', () => {
    renderRow(baseUser())

    expect(screen.getByText('Alice')).toBeTruthy()
    const link = screen.getByRole('link')
    expect(link.getAttribute('href')).toBe('/profile/alice')
  })

  it('falls back to username when displayName is missing', () => {
    renderRow(baseUser({ displayName: null }))

    // Both the heading and the @username line should show "alice".
    expect(screen.getAllByText(/alice/).length).toBeGreaterThan(0)
  })

  it('prefixes the username with @ in the subtitle', () => {
    renderRow(baseUser())

    expect(screen.getByText(/@alice/)).toBeTruthy()
  })

  it('renders the study-level + faculty subtitle when both are present', () => {
    renderRow(baseUser({ studyLevel: 'MASTER', faculty: 'SCIENCES' }))

    expect(screen.getByText(/Master/)).toBeTruthy()
    expect(screen.getByText(/Sciences/)).toBeTruthy()
  })

  it('does not render a FollowButton in the row (intentional — list items have followStatus=null)', () => {
    renderRow(baseUser())

    expect(screen.queryByRole('button', { name: /suivre/i })).toBeNull()
  })

  it('renders no "Retirer" button when onRemove is omitted', () => {
    renderRow(baseUser())
    expect(screen.queryByRole('button', { name: 'Retirer' })).toBeNull()
  })

  it('renders a "Retirer" button calling onRemove with the user id when provided', () => {
    const onRemove = vi.fn()
    render(
      <MemoryRouter>
        <ul>
          <FollowListRow user={baseUser({ id: 'x-id' })} onRemove={onRemove} />
        </ul>
      </MemoryRouter>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Retirer' }))
    expect(onRemove).toHaveBeenCalledWith('x-id')
  })
})
