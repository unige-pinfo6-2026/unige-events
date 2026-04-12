// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import UserIdentity from '@/components/user/UserIdentity'
import type { User } from '@/types/user'

afterEach(() => { cleanup() })

const user: User = {
  id: '1',
  auth0Id: 'auth0|1',
  email: 'user@example.com',
  displayName: 'Bob Dupont',
  profilePublic: true,
  createdAt: '2024-01-01',
}

describe('UserIdentity', () => {
  it('renders the user display name', () => {
    render(<UserIdentity user={user} />)
    expect(screen.getByText('Bob Dupont')).toBeTruthy()
  })

  it('renders the avatar', () => {
    render(<UserIdentity user={user} />)
    expect(screen.getByText('BD')).toBeTruthy()
  })

  it('renders avatar with custom size', () => {
    const { container } = render(<UserIdentity user={user} avatarSize={48} />)
    // Find the inner div (avatar container) which has inline style
    const avatarDiv = Array.from(container.querySelectorAll('div')).find(
      (el) => el.style.width !== '',
    ) as HTMLDivElement
    expect(avatarDiv?.style.width).toBe('48px')
  })

  it('renders avatar with avatar url', () => {
    const userWithAvatar = { ...user, avatarUrl: 'https://example.com/avatar.jpg' }
    render(<UserIdentity user={userWithAvatar} />)
    const img = screen.getByAltText('Bob Dupont') as HTMLImageElement
    expect(img.src).toContain('example.com/avatar.jpg')
  })
})
