
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import UserIdentity from '@/components/user/UserIdentity'
import type { User } from '@/types/user'

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({ user: null, isLoading: false, logout: vi.fn(), login: vi.fn() })),
}))

afterEach(() => { cleanup() })

const user: User = {
  id: '1',
  auth0Id: 'auth0|1',
  email: 'user@example.com',
  displayName: 'Bob Dupont',
  profilePublic: true,
  createdAt: '2024-01-01',
}

function renderUserIdentity(props: Parameters<typeof UserIdentity>[0]) {
  return render(
    <MemoryRouter>
      <UserIdentity {...props} />
    </MemoryRouter>,
  )
}

describe('UserIdentity', () => {
  it('renders the user display name', () => {
    renderUserIdentity({ user })
    expect(screen.getByText('Bob Dupont')).toBeTruthy()
  })

  it('renders the avatar', () => {
    renderUserIdentity({ user })
    expect(screen.getByText('BD')).toBeTruthy()
  })

  it('renders avatar with sm size in inline variant (default)', () => {
    const { container } = renderUserIdentity({ user })
    const avatarDiv = container.querySelector('div[class*="size-"]') as HTMLDivElement
    expect(avatarDiv?.className).toContain('size-8')
  })

  it('renders avatar with lg size in card variant', () => {
    const { container } = renderUserIdentity({ user, variant: 'card' })
    const avatarDiv = container.querySelector('div[class*="size-"]') as HTMLDivElement
    expect(avatarDiv?.className).toContain('size-18')
  })

  it('renders avatar with avatar url', () => {
    const userWithAvatar = { ...user, avatarUrl: 'https://example.com/avatar.jpg' }
    renderUserIdentity({ user: userWithAvatar })
    const img = screen.getByAltText('Bob Dupont') as HTMLImageElement
    expect(img.src).toContain('example.com/avatar.jpg')
  })

  it('renders nothing when user is null and not loading', () => {
    const { container } = renderUserIdentity({ user: null })
    expect(container.firstChild).toBeNull()
  })

  it('renders skeleton when loading', () => {
    const { container } = renderUserIdentity({ user: null, loading: true })
    expect(container.firstChild).toBeTruthy()
  })

  it('renders username in card variant', () => {
    renderUserIdentity({ user: { ...user, username: 'bobdup' }, variant: 'card' })
    expect(screen.getByText('@bobdup')).toBeTruthy()
  })
})
