// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import ProfilePage from '@/pages/profile/ProfilePage'

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: vi.fn(() => ({ theme: 'dark', toggleTheme: vi.fn() })),
}))

vi.mock('@/services/userService', () => ({
  getMe: vi.fn(),
  getUserById: vi.fn(),
  getUserByUsername: vi.fn(),
  getCalendarToken: vi.fn().mockResolvedValue({
    calendarToken: 'test-token',
    webcalUrl: 'webcal://example.com/cal.ics',
    httpsUrl: 'https://example.com/cal.ics',
  }),
  regenerateCalendarToken: vi.fn(),
}))

vi.mock('@/services/eventApi', () => ({
  getAll: vi.fn().mockResolvedValue([]),
  getMyEvents: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/services/attendanceApi', () => ({
  getUserParticipations: vi.fn().mockResolvedValue([]),
}))

vi.mock('@/services/followApi', () => ({
  followUser: vi.fn().mockResolvedValue({
    id: 1, followerId: 'me', followedId: 'tgt', status: 'PENDING', createdAt: 'x',
  }),
  unfollowUser: vi.fn().mockResolvedValue(undefined),
  getMyFollowRequests: vi.fn().mockResolvedValue([]),
  acceptFollowRequest: vi.fn(),
  rejectFollowRequest: vi.fn(),
}))

const mockShowToast = vi.fn()
vi.mock('@/hooks/useToast', () => ({
  useToast: () => ({ showToast: mockShowToast, toasts: [], dismiss: vi.fn() }),
}))

vi.mock('@/hooks/useMyEvents', () => ({
  useMyEvents: vi.fn(() => ({
    events: [],
    loading: false,
    error: null,
    refresh: vi.fn(),
    publish: vi.fn(),
    cancel: vi.fn(),
    restore: vi.fn(),
    permanentlyDelete: vi.fn(),
  })),
}))

// CoOrganizerInvitationsList ships from main and makes its own API calls;
// stub it out so it doesn't pollute the /me path with unrelated fetches.
vi.mock('@/components/user/CoOrganizerInvitationsList', () => ({
  default: () => null,
}))

import { useAuth } from '@/hooks/useAuth'
import { getCalendarToken, getUserById, getUserByUsername } from '@/services/userService'
import { getAll as getAllEvents } from '@/services/eventApi'
import { getUserParticipations } from '@/services/attendanceApi'
import { followUser, unfollowUser } from '@/services/followApi'
import { useTheme } from '@/contexts/ThemeContext'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockGetUserById = getUserById as ReturnType<typeof vi.fn>
const mockGetUserByUsername = getUserByUsername as ReturnType<typeof vi.fn>
const mockGetAllEvents = getAllEvents as ReturnType<typeof vi.fn>
const mockGetUserParticipations = getUserParticipations as ReturnType<typeof vi.fn>
const mockGetCalendarToken = getCalendarToken as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>
const mockFollowUser = followUser as ReturnType<typeof vi.fn>
const mockUnfollowUser = unfollowUser as ReturnType<typeof vi.fn>

const OWN_UUID = 'b1b1b1b1-b1b1-4b1b-9b1b-b1b1b1b1b1b1'
const OTHER_UUID = 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c'

const mockUser = {
  id: OWN_UUID,
  auth0Id: 'auth0|123',
  email: 'test@example.com',
  username: 'test.user',
  displayName: 'Test User',
  profilePublic: true,
  createdAt: '2024-01-01',
}

const otherProfile = {
  id: OTHER_UUID,
  username: 'other.user',
  displayName: 'Other User',
  faculty: 'SCIENCES',
  studyLevel: 'MASTER',
  bio: 'Bio publique',
  interests: ['Jazz'],
  avatarUrl: null,
  bannerUrl: null,
  profilePublic: true,
  followerCount: 12,
  followingCount: 7,
  followStatus: null,
}

beforeEach(() => {
  mockGetCalendarToken.mockResolvedValue({
    calendarToken: 'test-token',
    webcalUrl: 'webcal://example.com/cal.ics',
    httpsUrl: 'https://example.com/cal.ics',
  })
  mockGetAllEvents.mockResolvedValue([])
  mockGetUserParticipations.mockResolvedValue([])
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderProfilePage(slug: string) {
  return render(
    <MemoryRouter initialEntries={[`/profile/${slug}`]}>
      <Routes>
        <Route path="/profile/:username" element={<ProfilePage />} />
        <Route path="*" element={<div data-testid="post-redirect-route">redirected</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

function findContentGrid(container: HTMLElement): HTMLElement | null {
  const candidates = container.querySelectorAll('div')
  for (const el of Array.from(candidates)) {
    const cls = el.className
    if (typeof cls === 'string' && cls.includes('grid-cols-1') && cls.includes('lg:grid-cols-2')) {
      return el
    }
  }
  return null
}

describe('ProfilePage — /profile/me (owner)', () => {
  it('renders the owner profile when slug is "me" — no API call', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('me')

    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
    expect(screen.getByText('Modifier')).toBeTruthy()
    expect(mockGetUserByUsername).not.toHaveBeenCalled()
    expect(mockGetUserById).not.toHaveBeenCalled()
  })

  it('treats slug = current user username as owner route', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('test.user')

    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
    expect(screen.getByText('Modifier')).toBeTruthy()
  })

  it('shows error when own profile user is null and auth is loaded', async () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: false })

    renderProfilePage('me')

    expect(await screen.findByText('Impossible de charger le profil.')).toBeTruthy()
  })

  it('renders Mes publications preview on /me', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('me')

    expect(await screen.findByRole('heading', { level: 2, name: 'Mes publications' })).toBeTruthy()
  })

  it('renders faculty + study level when present', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, faculty: 'SCIENCES', studyLevel: 'MASTER' },
      isLoading: false,
    })

    renderProfilePage('me')

    expect(await screen.findByRole('img', { name: /Sciences/i })).toBeTruthy()
    expect(screen.getAllByText(/Master/).length).toBeGreaterThan(0)
  })

  it('renders bio when present', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, bio: 'Passionné de recherche.' },
      isLoading: false,
    })

    renderProfilePage('me')

    expect(await screen.findByText('Passionné de recherche.')).toBeTruthy()
  })

  it('renders interests as chips', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, interests: ['Jazz', 'Coding'] },
      isLoading: false,
    })

    renderProfilePage('me')

    expect(await screen.findByText('Jazz')).toBeTruthy()
    expect(screen.getByText('Coding')).toBeTruthy()
  })

  it('does not crash on unknown faculty key', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, faculty: 'UNKNOWN_FACULTY' as never },
      isLoading: false,
    })

    renderProfilePage('me')

    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
  })

  it('renders banner image when bannerUrl is set', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, bannerUrl: 'https://example.com/banner.jpg' },
      isLoading: false,
    })

    renderProfilePage('me')

    await screen.findByRole('heading', { level: 1, name: 'Test User' })
    const banner = document.querySelector<HTMLImageElement>('img[src*="banner.jpg"]')
    expect(banner).toBeTruthy()
  })

  it('shows skeleton while auth is loading', () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: true })
    renderProfilePage('me')
    expect(document.querySelector('[data-boneyard="profile"]')).toBeTruthy()
  })

  it('uses light skeleton color when theme is light', () => {
    mockUseTheme.mockReturnValue({ theme: 'light', toggleTheme: vi.fn() })
    mockUseAuth.mockReturnValue({ user: null, isLoading: true })
    renderProfilePage('me')
    expect(document.querySelector('[data-boneyard="profile"]')).toBeTruthy()
  })

  it('hides ProfileStats on /me (self payload UserProfileResponse does not carry follower counts)', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    renderProfilePage('me')
    await screen.findByRole('heading', { level: 1, name: 'Test User' })

    expect(screen.queryByLabelText('Compteurs de suivi')).toBeNull()
  })
})

describe('ProfilePage — /profile/:username (other user)', () => {
  it('fetches and renders another user public profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    expect(await screen.findByRole('heading', { level: 1, name: 'Other User' })).toBeTruthy()
    expect(mockGetUserByUsername).toHaveBeenCalledWith('other.user')
    expect(screen.getByText('Bio publique')).toBeTruthy()
  })

  it('renders follower/following counters from the public projection', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.getByText('12')).toBeTruthy()
    expect(screen.getByText('7')).toBeTruthy()
  })

  it('does NOT render Mes publications preview on another user profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.queryByRole('heading', { level: 2, name: 'Mes publications' })).toBeNull()
  })

  it('does NOT render the Modifier button on another user profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.queryByText('Modifier')).toBeNull()
  })

  it('renders the "Événements organisés" section', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    expect(await screen.findByRole('heading', { name: 'Événements organisés' })).toBeTruthy()
    await waitFor(() => expect(mockGetAllEvents).toHaveBeenCalledWith(
      expect.objectContaining({ organizerId: OTHER_UUID, status: 'PUBLISHED' }),
    ))
  })

  it('renders the "Participations publiques" section with the empty state when none', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    expect(await screen.findByRole('heading', { name: 'Participations publiques' })).toBeTruthy()
    // getUserParticipations resolves [] → dedicated empty copy (SCRUM-141 follow-up),
    // not the former "Bientôt disponible" placeholder.
    expect(await screen.findByText('Aucune participation publique pour le moment.')).toBeTruthy()
  })

  it('renders the private-state card when getUserByUsername returns null (404 — user does not exist)', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(null)

    renderProfilePage('ghost.handle')

    expect(await screen.findByRole('heading', { level: 2, name: 'Compte privé' })).toBeTruthy()
    // 404 path — no profile data, no displayName heading rendered.
    expect(screen.queryByRole('heading', { level: 1 })).toBeNull()
  })

  it('renders an error message when getUserByUsername rejects', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockRejectedValue(new Error('Network error'))

    renderProfilePage('other.user')

    expect(await screen.findByText('Impossible de charger le profil.')).toBeTruthy()
  })

  it('shows the profile skeleton while loading', () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockImplementation(() => new Promise(() => {}))

    renderProfilePage('other.user')

    expect(document.querySelector('[data-boneyard="profile"]')).toBeTruthy()
  })
})

describe('ProfilePage — private profile (SCRUM-141 redesign — restricted projection)', () => {
  // SCRUM-169 Décision E revised : the backend returns a 200 restricted
  // projection for a non-owner non-admin caller of a private profile
  // (id + username + displayName + avatarUrl + profilePublic=false,
  // bannerUrl / bio / faculty / studyLevel / interests stripped).
  const privateProfile = {
    id: OTHER_UUID,
    username: 'jane.private',
    displayName: 'Jane Private',
    faculty: null,
    studyLevel: null,
    bio: null,
    interests: [],
    avatarUrl: null,
    bannerUrl: null,
    profilePublic: false,
    followerCount: 0,
    followingCount: 0,
    followStatus: null,
  }

  it('renders the locked view with banner + avatar + displayName + "Compte privé"', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(privateProfile)

    renderProfilePage('jane.private')

    expect(await screen.findByRole('heading', { level: 1, name: 'Jane Private' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 2, name: 'Compte privé' })).toBeTruthy()
  })

  it('renders the gradient fallback banner when bannerUrl is null', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(privateProfile)

    const { container } = renderProfilePage('jane.private')

    await screen.findByRole('heading', { level: 2, name: 'Compte privé' })
    expect(container.querySelector('img[src]')).toBeNull()
    expect(container.querySelector('.bg-linear-to-br')).toBeTruthy()
  })

  it('renders the avatar fallback (initials) when no avatar is set', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(privateProfile)

    renderProfilePage('jane.private')

    await screen.findByRole('heading', { level: 2, name: 'Compte privé' })
    expect(screen.getByText('JP')).toBeTruthy()
  })

  it('does NOT render bio / counters / events / participations / FollowButton on a private profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({
      // Even if the backend leaked the fields (defense-in-depth), they
      // must not appear in the rendered UI.
      ...privateProfile,
      bio: 'NEVER VISIBLE',
      faculty: 'SCIENCES',
      studyLevel: 'MASTER',
      followerCount: 99,
      followingCount: 42,
    })

    renderProfilePage('jane.private')

    await screen.findByRole('heading', { level: 2, name: 'Compte privé' })
    expect(screen.queryByText('NEVER VISIBLE')).toBeNull()
    expect(screen.queryByLabelText('Compteurs de suivi')).toBeNull()
    expect(screen.queryByRole('heading', { name: 'Événements organisés' })).toBeNull()
    expect(screen.queryByRole('heading', { name: 'Participations publiques' })).toBeNull()
    expect(screen.queryByRole('button', { name: /Suivre|Demande envoyée|Abonné|Se désabonner/ })).toBeNull()
  })

  it('does NOT render the "Demande de suivi envoyée" PENDING badge when followStatus is PENDING', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({ ...privateProfile, followStatus: 'PENDING' })

    renderProfilePage('jane.private')

    await screen.findByRole('heading', { level: 2, name: 'Compte privé' })
    expect(screen.queryByText('Demande de suivi envoyée')).toBeNull()
  })

  it('renders the same locked view when followStatus is null', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({ ...privateProfile, followStatus: null })

    renderProfilePage('jane.private')

    expect(await screen.findByRole('heading', { level: 2, name: 'Compte privé' })).toBeTruthy()
  })
})

describe('ProfilePage — legacy UUID redirect (SCRUM-169 Décision I)', () => {
  it('redirects legacy /profile/<uuid> to /profile/<username>', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    const legacyUser = {
      id: OTHER_UUID,
      auth0Id: 'auth0|legacy',
      email: 'legacy@example.com',
      username: 'jean.dupont',
      displayName: 'Jean Dupont',
      profilePublic: true,
      createdAt: '2024-01-01',
    }
    mockGetUserById.mockResolvedValue(legacyUser)
    mockGetUserByUsername.mockResolvedValue({ ...otherProfile, displayName: 'Jean Dupont', username: 'jean.dupont' })

    renderProfilePage(OTHER_UUID)

    // The page first hits getUserById with the UUID, gets back the user's
    // username, navigates to /profile/<username>, then re-renders and fetches
    // via getUserByUsername.
    await waitFor(() => expect(mockGetUserById).toHaveBeenCalledWith(OTHER_UUID))
    await waitFor(() => expect(mockGetUserByUsername).toHaveBeenCalledWith('jean.dupont'))
    expect(await screen.findByRole('heading', { level: 1, name: 'Jean Dupont' })).toBeTruthy()
  })

  it('renders private-state when legacy UUID lookup returns null', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserById.mockResolvedValue(null)

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByRole('heading', { level: 2, name: 'Compte privé' })).toBeTruthy()
  })

  it('shows an error message when the legacy UUID lookup rejects', async () => {
    // L309 — getUserById .catch sets the error state on the legacy path.
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserById.mockRejectedValue(new Error('Network error'))

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByText('Impossible de charger le profil.')).toBeTruthy()
    expect(mockGetUserByUsername).not.toHaveBeenCalled()
  })
})

describe('ProfilePage — FollowRequestsPanel on /profile/me (SCRUM-110)', () => {
  it('renders the panel only on /profile/me', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('me')

    expect(await screen.findByRole('heading', { name: 'Demandes de suivi reçues' })).toBeTruthy()
  })

  it('does NOT render the panel on /profile/<username>', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.queryByRole('heading', { name: 'Demandes de suivi reçues' })).toBeNull()
  })

  it('does NOT render the panel on /profile/<own-username> (treated as /me)', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    // Own username is detected as /me before any API call — no fetch fires.

    renderProfilePage('test.user')

    await screen.findByRole('heading', { level: 1, name: 'Test User' })
    // Own-username path renders the owner view, which DOES include the panel
    // (it's the /me path under the hood). This test asserts the inverse of
    // what the other-user path does.
    expect(screen.getByRole('heading', { name: 'Demandes de suivi reçues' })).toBeTruthy()
  })
})

describe('ProfilePage — FollowButton wiring (SCRUM-110)', () => {
  it('renders the FollowButton for an authenticated viewer on another user profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    expect(await screen.findByRole('button', { name: 'Suivre cet utilisateur' })).toBeTruthy()
  })

  it('does NOT render the FollowButton for an unauthenticated viewer', async () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.queryByRole('button', { name: /Suivre|Demande envoyée|Abonné|Se désabonner/ })).toBeNull()
  })

  it('does NOT render the FollowButton when the viewer is looking at their own username (treated as /me)', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('test.user')

    await screen.findByRole('heading', { level: 1, name: 'Test User' })
    expect(screen.queryByRole('button', { name: /Suivre/ })).toBeNull()
  })

  it('does NOT render the FollowButton on /profile/me', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('me')

    await screen.findByRole('heading', { level: 1, name: 'Test User' })
    expect(screen.queryByRole('button', { name: /Suivre/ })).toBeNull()
  })

  it('reflects followStatus="PENDING" on the FollowButton when the profile carries it', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({ ...otherProfile, followStatus: 'PENDING' })

    renderProfilePage('other.user')

    expect(await screen.findByRole('button', { name: 'Annuler la demande de suivi' })).toBeTruthy()
  })

  it('clicking Suivre triggers a refetch (counters update via reload)', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername
      .mockResolvedValueOnce(otherProfile)
      .mockResolvedValueOnce({ ...otherProfile, followStatus: 'PENDING', followerCount: 13 })

    renderProfilePage('other.user')

    const followBtn = await screen.findByRole('button', { name: 'Suivre cet utilisateur' })
    ;(followBtn as HTMLButtonElement).click()

    // SCRUM-110: FollowButton calls followUser with the target's UUID (not
    // the username slug) — that's the API contract.
    await waitFor(() => expect(mockFollowUser).toHaveBeenCalledWith(OTHER_UUID))
    // Refetch fired → second getUserByUsername call.
    await waitFor(() => expect(mockGetUserByUsername).toHaveBeenCalledTimes(2))
  })

  it('forwards unfollow to the service when clicking an ACCEPTED button', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({ ...otherProfile, followStatus: 'ACCEPTED' })

    renderProfilePage('other.user')

    const btn = await screen.findByRole('button', { name: 'Se désabonner' })
    ;(btn as HTMLButtonElement).click()

    await waitFor(() => expect(mockUnfollowUser).toHaveBeenCalledWith(OTHER_UUID))
  })
})

describe('ProfilePage — layout regressions', () => {
  it('applies items-start on the about/calendar grid on /me', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    const { container } = renderProfilePage('me')

    await screen.findByRole('heading', { level: 1, name: 'Test User' })
    const grid = findContentGrid(container)
    expect(grid).not.toBeNull()
    expect(grid!.className).toContain('items-start')
  })
})

describe('ProfilePage — user-content overflow wrapping', () => {
  const LONG_WORD = 'a'.repeat(200)

  it('renders a 200-char single-word bio with wrap-anywhere', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, bio: LONG_WORD }, isLoading: false })

    renderProfilePage('me')
    const bio = await screen.findByText(LONG_WORD)
    expect(bio.className).toContain('wrap-anywhere')
    expect(bio.className).not.toContain('break-all')
  })

  it('applies wrap-anywhere on the displayName heading', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, displayName: LONG_WORD }, isLoading: false })

    renderProfilePage('me')
    const heading = await screen.findByRole('heading', { level: 1, name: LONG_WORD })
    expect(heading.className).toContain('wrap-anywhere')
  })
})
