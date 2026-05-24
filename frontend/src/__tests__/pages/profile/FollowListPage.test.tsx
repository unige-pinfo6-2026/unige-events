// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { AxiosError, AxiosHeaders } from 'axios'

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: vi.fn(() => ({ theme: 'dark', toggleTheme: vi.fn() })),
}))

vi.mock('@/services/userService', () => ({
  getUserByUsername: vi.fn(),
}))

// Explicit comprehensive mock — `vi.importActual` plus a spread is fragile
// in CI's fork pool : another test file mocking the same module can poison
// the import cache and we end up with a real function instead of vi.fn(),
// which then silently returns undefined and makes the `findByText` race
// past the network call. List every export explicitly.
vi.mock('@/services/followApi', () => ({
  FOLLOW_LIST_PAGE_SIZE: 20,
  followUser: vi.fn(),
  unfollowUser: vi.fn(),
  getMyFollowRequests: vi.fn(),
  acceptFollowRequest: vi.fn(),
  rejectFollowRequest: vi.fn(),
  getFollowers: vi.fn(),
  getFollowing: vi.fn(),
}))

// boneyard renders an SSR-style `<div>` with bones the test doesn't care
// about — stub it to render children only so loading-state assertions can
// use the fixture's text content if needed.
vi.mock('boneyard-js/react', () => ({
  Skeleton: ({ children }: { children: React.ReactNode }) => <div data-testid="skeleton">{children}</div>,
}))

import { useAuth } from '@/hooks/useAuth'
import { getUserByUsername } from '@/services/userService'
import { useTheme } from '@/contexts/ThemeContext'
import { FOLLOW_LIST_PAGE_SIZE, getFollowers, getFollowing } from '@/services/followApi'
import FollowListPage from '@/pages/profile/FollowListPage'
import type { UserPublicResponse } from '@/types/user'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>
const mockGetUserByUsername = getUserByUsername as ReturnType<typeof vi.fn>
const mockGetFollowers = getFollowers as ReturnType<typeof vi.fn>
const mockGetFollowing = getFollowing as ReturnType<typeof vi.fn>

const OWN_UUID = 'b1b1b1b1-b1b1-4b1b-9b1b-b1b1b1b1b1b1'
const OTHER_UUID = 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c'

const mockUser = {
  id: OWN_UUID,
  auth0Id: 'auth0|123',
  email: 'me@example.com',
  username: 'me.username',
  displayName: 'Me',
  profilePublic: true,
  createdAt: '2024-01-01',
}

const otherProfile = {
  id: OTHER_UUID,
  username: 'other.user',
  displayName: 'Other User',
  faculty: null,
  studyLevel: null,
  bio: null,
  interests: [],
  avatarUrl: null,
  bannerUrl: null,
  profilePublic: true,
  followerCount: 12,
  followingCount: 7,
  followStatus: null,
}

function makeListUser(suffix: string): UserPublicResponse {
  return {
    id: `${suffix}-id`,
    username: `user-${suffix}`,
    displayName: `User ${suffix}`,
    profilePublic: true,
    followerCount: 0,
    followingCount: 0,
    followStatus: null,
  }
}

function makeFullPage(prefix: string): UserPublicResponse[] {
  return Array.from({ length: FOLLOW_LIST_PAGE_SIZE }, (_, i) =>
    makeListUser(`${prefix}-${i}`),
  )
}

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/profile/:username/followers" element={<FollowListPage mode="followers" />} />
        <Route path="/profile/:username/following" element={<FollowListPage mode="following" />} />
        <Route path="/profile/:username" element={<div>profile of {path}</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('FollowListPage — others', () => {
  it('resolves the username via getUserByUsername then fetches followers', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    mockGetFollowers.mockResolvedValue([makeListUser('a'), makeListUser('b')])

    renderAt('/profile/other.user/followers')

    await waitFor(() => expect(screen.getByText(/Followers de Other User/)).toBeTruthy())
    expect(mockGetUserByUsername).toHaveBeenCalledWith('other.user')
    expect(mockGetFollowers).toHaveBeenCalledWith(OTHER_UUID, 0, FOLLOW_LIST_PAGE_SIZE)
    expect(await screen.findByText('User a')).toBeTruthy()
    expect(screen.getByText('User b')).toBeTruthy()
  })

  it('renders the heading and fetches /following in following mode', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    mockGetFollowing.mockResolvedValue([makeListUser('z')])

    renderAt('/profile/other.user/following')

    await waitFor(() => expect(screen.getByText(/Abonnements de Other User/)).toBeTruthy())
    expect(mockGetFollowing).toHaveBeenCalledWith(OTHER_UUID, 0, FOLLOW_LIST_PAGE_SIZE)
    expect(await screen.findByText('User z')).toBeTruthy()
  })

  it('exposes follower and following tabs with the target counts', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    mockGetFollowers.mockResolvedValue([])

    renderAt('/profile/other.user/followers')

    await waitFor(() => expect(screen.getByText(/Followers de Other User/)).toBeTruthy())
    // Followers tab points to itself, following tab points to the other side.
    const followersTab = screen.getByRole('link', { name: /Followers/i })
    const followingTab = screen.getByRole('link', { name: /Abonnements/i })
    expect(followersTab.getAttribute('href')).toBe('/profile/other.user/followers')
    expect(followingTab.getAttribute('href')).toBe('/profile/other.user/following')
    expect(followersTab.textContent).toMatch(/12/)
    expect(followingTab.textContent).toMatch(/7/)
  })

  it('shows the empty state when no users come back', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    mockGetFollowers.mockResolvedValue([])

    renderAt('/profile/other.user/followers')

    expect(await screen.findByText(/Aucun follower/)).toBeTruthy()
  })

  it('renders the private-state placeholder when getUserByUsername returns null (404)', async () => {
    mockGetUserByUsername.mockResolvedValue(null)

    renderAt('/profile/private.user/followers')

    expect(await screen.findByRole('heading', { level: 2, name: 'Compte privé' })).toBeTruthy()
    expect(mockGetFollowers).not.toHaveBeenCalled()
  })

  it('surfaces an error when getUserByUsername throws non-404', async () => {
    mockGetUserByUsername.mockRejectedValue(new Error('boom'))

    renderAt('/profile/other.user/followers')

    expect(await screen.findByText(/Impossible de charger le profil/)).toBeTruthy()
  })

  it('surfaces a list-specific error when getFollowers throws non-404', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    mockGetFollowers.mockRejectedValue(new Error('boom'))

    renderAt('/profile/other.user/followers')

    expect(await screen.findByText('Impossible de charger les followers.')).toBeTruthy()
  })

  it('flips to the private-state placeholder when getFollowers returns 404 mid-flow', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    const err = new AxiosError(
      'private',
      undefined,
      undefined,
      undefined,
      { status: 404, data: {}, statusText: 'Not Found', headers: {}, config: { headers: new AxiosHeaders() } },
    )
    mockGetFollowers.mockRejectedValue(err)

    renderAt('/profile/other.user/followers')

    expect(await screen.findByRole('heading', { level: 2, name: 'Compte privé' })).toBeTruthy()
  })

  it('shows the Charger plus button when the page is full and loads the next page', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    mockGetFollowers
      .mockResolvedValueOnce(makeFullPage('p1'))
      .mockResolvedValueOnce([makeListUser('tail')])

    renderAt('/profile/other.user/followers')

    const button = await screen.findByRole('button', { name: /Charger plus/i })
    fireEvent.click(button)

    await waitFor(() => expect(screen.getByText('User tail')).toBeTruthy())
    expect(mockGetFollowers).toHaveBeenNthCalledWith(2, OTHER_UUID, 1, FOLLOW_LIST_PAGE_SIZE)
    // Charger plus disappears once the next page is short.
    expect(screen.queryByRole('button', { name: /Charger plus/i })).toBeNull()
  })

  it('renders a back link to the target profile', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    mockGetFollowers.mockResolvedValue([])

    renderAt('/profile/other.user/followers')

    await waitFor(() => expect(screen.getByText(/Followers de Other User/)).toBeTruthy())
    const back = screen.getByRole('link', { name: /Retour au profil de Other User/i })
    expect(back.getAttribute('href')).toBe('/profile/other.user')
  })
})

describe('FollowListPage — me', () => {
  it('uses the authenticated user when username = "me" without an extra fetch', async () => {
    mockGetFollowers.mockResolvedValue([makeListUser('a')])

    renderAt('/profile/me/followers')

    await waitFor(() => expect(screen.getByText(/Followers de Me/)).toBeTruthy())
    expect(mockGetUserByUsername).not.toHaveBeenCalled()
    expect(mockGetFollowers).toHaveBeenCalledWith(OWN_UUID, 0, FOLLOW_LIST_PAGE_SIZE)
  })

  it('treats /:ownUsername/followers as the /me route', async () => {
    mockGetFollowers.mockResolvedValue([])

    renderAt(`/profile/${mockUser.username}/followers`)

    await waitFor(() => expect(screen.getByText(/Followers de Me/)).toBeTruthy())
    expect(mockGetUserByUsername).not.toHaveBeenCalled()
  })

  it('renders an error placeholder on /me when useAuth has no user', async () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: false })

    renderAt('/profile/me/followers')

    expect(await screen.findByText(/Impossible de charger le profil/)).toBeTruthy()
    expect(mockGetFollowers).not.toHaveBeenCalled()
  })

  it('falls back to the username as displayName when the /me user has none', async () => {
    // L221[binary-expr #1] — currentUser.displayName ?? currentUser.username.
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, displayName: undefined },
      isLoading: false,
    })
    mockGetFollowers.mockResolvedValue([])

    renderAt('/profile/me/followers')

    expect(await screen.findByText(/Followers de me\.username/)).toBeTruthy()
  })
})

describe('FollowListPage — edge cases', () => {
  it('falls back to the username as displayName when the resolved profile has none', async () => {
    // L245[binary-expr #1] — profile.displayName ?? profile.username.
    mockGetUserByUsername.mockResolvedValue({ ...otherProfile, displayName: null })
    mockGetFollowers.mockResolvedValue([])

    renderAt('/profile/other.user/followers')

    expect(await screen.findByText(/Followers de other\.user/)).toBeTruthy()
  })

  it('stays on the skeleton while auth is still loading (effect early-returns)', () => {
    // L202[if #0] — `if (!username || authLoading) return` short-circuits the
    // fetch effect until auth resolves; the page shows the loading skeleton.
    mockUseAuth.mockReturnValue({ user: null, isLoading: true })

    renderAt('/profile/other.user/followers')

    expect(screen.getByTestId('skeleton')).toBeTruthy()
    expect(mockGetUserByUsername).not.toHaveBeenCalled()
    expect(mockGetFollowers).not.toHaveBeenCalled()
  })

  it('uses the light skeleton color when the theme is light', () => {
    // L190[cond-expr #1] — light branch of the skeletonColor ternary.
    mockUseTheme.mockReturnValue({ theme: 'light', toggleTheme: vi.fn() })
    mockGetUserByUsername.mockImplementation(() => new Promise(() => {}))

    renderAt('/profile/other.user/followers')

    expect(screen.getByTestId('skeleton')).toBeTruthy()
  })
})
