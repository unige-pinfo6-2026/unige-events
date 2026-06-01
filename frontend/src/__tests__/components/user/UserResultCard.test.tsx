import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

afterEach(cleanup)
import UserResultCard from '@/components/user/UserResultCard'
import type { UserPublicResponse } from '@/types/user'

function user(overrides: Partial<UserPublicResponse> = {}): UserPublicResponse {
  return {
    id: 'u-1',
    username: 'daniel.dosh',
    displayName: 'Daniel Dosh',
    avatarUrl: null,
    profilePublic: true,
    followerCount: 0,
    followingCount: 0,
    followStatus: null,
    ...overrides,
  }
}

function renderCard(u: UserPublicResponse) {
  return render(
    <MemoryRouter>
      <UserResultCard user={u} />
    </MemoryRouter>,
  )
}

describe('UserResultCard', () => {
  it('renders the displayName and @username linking to the profile', () => {
    const { container } = renderCard(user())
    expect(screen.getByText('Daniel Dosh')).toBeTruthy()
    expect(screen.getByText('@daniel.dosh')).toBeTruthy()
    expect(container.querySelector('a[href="/profile/daniel.dosh"]')).toBeTruthy()
  })

  it('falls back to the @username line only when displayName is null', () => {
    renderCard(user({ displayName: null }))
    expect(screen.getByText('@daniel.dosh')).toBeTruthy()
    expect(screen.queryByText('Daniel Dosh')).toBeNull()
  })
})
