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

const { mockShowToast } = vi.hoisted(() => ({ mockShowToast: vi.fn() }))
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: mockShowToast }),
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
  removeFollower: vi.fn(),
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
import { FOLLOW_LIST_PAGE_SIZE, getFollowers, getFollowing, removeFollower } from '@/services/followApi'
import FollowListPage from '@/pages/profile/FollowListPage'
import type { UserPublicResponse } from '@/types/user'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>
const mockGetUserByUsername = getUserByUsername as ReturnType<typeof vi.fn>
const mockGetFollowers = getFollowers as ReturnType<typeof vi.fn>
const mockGetFollowing = getFollowing as ReturnType<typeof vi.fn>
const mockRemoveFollower = removeFollower as ReturnType<typeof vi.fn>

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
    // The followers fetch fires in a later effect than the heading render —
    // await it so the assertion isn't racy under load.
    await waitFor(() => expect(mockGetFollowers).toHaveBeenCalledWith(OTHER_UUID, 0, FOLLOW_LIST_PAGE_SIZE))
    expect(await screen.findByText('User a')).toBeTruthy()
    expect(screen.getByText('User b')).toBeTruthy()
  })

  it('renders the heading and fetches /following in following mode', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    mockGetFollowing.mockResolvedValue([makeListUser('z')])

    renderAt('/profile/other.user/following')

    await waitFor(() => expect(screen.getByText(/Abonnements de Other User/)).toBeTruthy())
    await waitFor(() => expect(mockGetFollowing).toHaveBeenCalledWith(OTHER_UUID, 0, FOLLOW_LIST_PAGE_SIZE))
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

  it('renders the not-found page when getUserByUsername returns null (404)', async () => {
    mockGetUserByUsername.mockResolvedValue(null)

    renderAt('/profile/private.user/followers')

    expect(await screen.findByText('404')).toBeTruthy()
    expect(mockGetFollowers).not.toHaveBeenCalled()
  })

  it('surfaces an error when getUserByUsername throws non-404', async () => {
    mockGetUserByUsername.mockRejectedValue(new Error('boom'))

    renderAt('/profile/other.user/followers')

    expect(await screen.findByText('500')).toBeTruthy()
  })

  it('cancels getUserByUsername rejection when unmounted before reject', async () => {
    let rejectFetch: ((reason: Error) => void) = () => {}
    mockGetUserByUsername.mockReturnValueOnce(
      new Promise((_, reject) => { rejectFetch = reject }),
    )

    const { unmount } = renderAt('/profile/other.user/followers')
    unmount()

    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    rejectFetch(new Error('boom'))
    await new Promise((r) => setTimeout(r, 0))
    expect(consoleErrorSpy).not.toHaveBeenCalled()
    consoleErrorSpy.mockRestore()
  })

  it('cancels getUserByUsername resolution when unmounted before resolve', async () => {
    let resolveFetch: ((value: unknown) => void) = () => {}
    mockGetUserByUsername.mockReturnValueOnce(
      new Promise((resolve) => { resolveFetch = resolve }),
    )

    const { unmount } = renderAt('/profile/other.user/followers')
    unmount()

    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    resolveFetch(otherProfile)
    await new Promise((r) => setTimeout(r, 0))
    expect(consoleErrorSpy).not.toHaveBeenCalled()
    consoleErrorSpy.mockRestore()
  })

  it('surfaces a list-specific error when getFollowers throws non-404', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    mockGetFollowers.mockRejectedValue(new Error('boom'))

    renderAt('/profile/other.user/followers')

    expect(await screen.findByText('500')).toBeTruthy()
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
  it('renders from useAuth on /me, then resolves the real counts in the background', async () => {
    // The /me list renders the identity immediately from useAuth (uuid known
    // synchronously, no blocking fetch), then fills the follower/following
    // counts via a best-effort public-by-username resolve so the tab labels
    // don't stay at 0/0 on one's own lists.
    mockGetUserByUsername.mockResolvedValue({
      ...otherProfile,
      id: OWN_UUID,
      username: mockUser.username,
      displayName: 'Me',
      followerCount: 5,
      followingCount: 3,
    })
    mockGetFollowers.mockResolvedValue([makeListUser('a')])

    renderAt('/profile/me/followers')

    await waitFor(() => expect(screen.getByText(/Followers de Me/)).toBeTruthy())
    // List uses the uuid from useAuth — it does not wait on the resolve.
    await waitFor(() => expect(mockGetFollowers).toHaveBeenCalledWith(OWN_UUID, 0, FOLLOW_LIST_PAGE_SIZE))
    // Counts are populated in the background from the resolved public profile.
    expect(mockGetUserByUsername).toHaveBeenCalledWith(mockUser.username)
    const followersTab = screen.getByRole('link', { name: /Followers/i })
    await waitFor(() => expect(followersTab.textContent).toMatch(/5/))
    expect(screen.getByRole('link', { name: /Abonnements/i }).textContent).toMatch(/3/)
  })

  it('cancels background count fetch for /me when unmounted before resolve', async () => {
    let resolveFetch: ((value: unknown) => void) = () => {}
    mockGetUserByUsername.mockReturnValueOnce(
      new Promise((resolve) => { resolveFetch = resolve }),
    )
    mockGetFollowers.mockResolvedValue([])

    const { unmount } = renderAt('/profile/me/followers')
    await waitFor(() => expect(screen.getByText(/Followers de Me/)).toBeTruthy())
    unmount()

    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    resolveFetch(otherProfile)
    await new Promise((r) => setTimeout(r, 0))
    expect(consoleErrorSpy).not.toHaveBeenCalled()
    consoleErrorSpy.mockRestore()
  })

  it('cancels background count fetch for /me when unmounted before reject', async () => {
    let rejectFetch: ((reason: Error) => void) = () => {}
    mockGetUserByUsername.mockReturnValueOnce(
      new Promise((_, reject) => { rejectFetch = reject }),
    )
    mockGetFollowers.mockResolvedValue([])

    const { unmount } = renderAt('/profile/me/followers')
    await waitFor(() => expect(screen.getByText(/Followers de Me/)).toBeTruthy())
    unmount()

    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    rejectFetch(new Error('boom'))
    await new Promise((r) => setTimeout(r, 0))
    expect(consoleErrorSpy).not.toHaveBeenCalled()
    consoleErrorSpy.mockRestore()
  })

  it('handles null background count response from getUserByUsername for /me', async () => {
    mockGetUserByUsername.mockResolvedValue(null)
    mockGetFollowers.mockResolvedValue([])

    renderAt('/profile/me/followers')

    await waitFor(() => expect(screen.getByText(/Followers de Me/)).toBeTruthy())
  })

  it('handles background count fetch rejection for /me (best-effort)', async () => {
    mockGetUserByUsername.mockRejectedValue(new Error('boom'))
    mockGetFollowers.mockResolvedValue([])

    renderAt('/profile/me/followers')

    await waitFor(() => expect(screen.getByText(/Followers de Me/)).toBeTruthy())
  })

  it('treats /:ownUsername/followers as the /me route', async () => {
    mockGetUserByUsername.mockResolvedValue({
      ...otherProfile,
      id: OWN_UUID,
      username: mockUser.username,
      displayName: 'Me',
    })
    mockGetFollowers.mockResolvedValue([])

    renderAt(`/profile/${mockUser.username}/followers`)

    await waitFor(() => expect(screen.getByText(/Followers de Me/)).toBeTruthy())
    // Identity (uuid) still comes from useAuth — the list fetch keys on it.
    await waitFor(() => expect(mockGetFollowers).toHaveBeenCalledWith(OWN_UUID, 0, FOLLOW_LIST_PAGE_SIZE))
  })

  it('renders an error placeholder on /me when useAuth has no user', async () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: false })

    renderAt('/profile/me/followers')

    expect(await screen.findByText('500')).toBeTruthy()
    expect(mockGetFollowers).not.toHaveBeenCalled()
  })

  it('falls back to the username as displayName when the /me user has none', async () => {
    // L221[binary-expr #1] — currentUser.displayName ?? currentUser.username.
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, displayName: undefined },
      isLoading: false,
    })
    mockGetUserByUsername.mockResolvedValue({
      ...otherProfile,
      id: OWN_UUID,
      username: 'me.username',
    })
    mockGetFollowers.mockResolvedValue([])

    renderAt('/profile/me/followers')

    expect(await screen.findByText(/Followers de me\.username/)).toBeTruthy()
  })
})

describe('FollowListPage — remove a follower (own followers list)', () => {
  // Own-followers list → the row exposes a "Retirer" action wired to
  // useFollowList.remove → removeFollower, then a success/error toast.
  const ownProfile = {
    ...otherProfile,
    id: OWN_UUID,
    username: mockUser.username,
    displayName: 'Me',
  }

  it('removes the follower optimistically and shows a success toast', async () => {
    mockGetUserByUsername.mockResolvedValue(ownProfile)
    mockGetFollowers.mockResolvedValue([makeListUser('a')])
    mockRemoveFollower.mockResolvedValue(undefined)

    renderAt(`/profile/${mockUser.username}/followers`)

    fireEvent.click(await screen.findByRole('button', { name: 'Retirer' }))

    await waitFor(() => expect(mockRemoveFollower).toHaveBeenCalledWith('a-id'))
    // Optimistic: the row disappears immediately.
    await waitFor(() => expect(screen.queryByText('User a')).toBeNull())
    expect(mockShowToast).toHaveBeenCalledWith('success', 'Follower retiré.', 3000)
  })

  it('restores the row and shows an error toast when removeFollower fails', async () => {
    mockGetUserByUsername.mockResolvedValue(ownProfile)
    mockGetFollowers.mockResolvedValue([makeListUser('a')])
    mockRemoveFollower.mockRejectedValue(new Error('boom'))

    renderAt(`/profile/${mockUser.username}/followers`)

    fireEvent.click(await screen.findByRole('button', { name: 'Retirer' }))

    await waitFor(() =>
      expect(mockShowToast).toHaveBeenCalledWith('error', 'Impossible de retirer ce follower.', 4000),
    )
    // Rollback: the row comes back after the rejection.
    expect(screen.getByText('User a')).toBeTruthy()
  })

  it('does not offer a "Retirer" action on someone else’s followers list', async () => {
    mockGetUserByUsername.mockResolvedValue(otherProfile)
    mockGetFollowers.mockResolvedValue([makeListUser('a')])

    renderAt('/profile/other.user/followers')

    await waitFor(() => expect(screen.getByText('User a')).toBeTruthy())
    expect(screen.queryByRole('button', { name: 'Retirer' })).toBeNull()
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
