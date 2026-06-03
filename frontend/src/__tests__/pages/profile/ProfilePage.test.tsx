// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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

import { useAuth } from '@/hooks/useAuth'
import { getUserById, getUserByUsername } from '@/services/userService'
import { getAll as getAllEvents } from '@/services/eventApi'
import { getUserParticipations } from '@/services/attendanceApi'
import { followUser, unfollowUser } from '@/services/followApi'
import { useTheme } from '@/contexts/ThemeContext'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const origMockReturnValue = mockUseAuth.mockReturnValue;
mockUseAuth.mockReturnValue = (val: Parameters<typeof origMockReturnValue>[0]) => {
  const hasUser = val && typeof val === 'object' && 'user' in val && (val as Record<string, unknown>).user !== null && (val as Record<string, unknown>).user !== undefined;
  return origMockReturnValue.call(mockUseAuth, {
    isAuthenticated: !!hasUser,
    login: vi.fn(),
    logout: vi.fn(),
    ...(val as Record<string, unknown> || {}),
  } as ReturnType<typeof useAuth>);
};
const origMockReturnValueOnce = mockUseAuth.mockReturnValueOnce;
mockUseAuth.mockReturnValueOnce = (val: Parameters<typeof origMockReturnValueOnce>[0]) => {
  const hasUser = val && typeof val === 'object' && 'user' in val && (val as Record<string, unknown>).user !== null && (val as Record<string, unknown>).user !== undefined;
  return origMockReturnValueOnce.call(mockUseAuth, {
    isAuthenticated: !!hasUser,
    login: vi.fn(),
    logout: vi.fn(),
    ...(val as Record<string, unknown> || {}),
  } as ReturnType<typeof useAuth>);
};
const mockGetUserById = getUserById as ReturnType<typeof vi.fn>
const mockGetUserByUsername = getUserByUsername as ReturnType<typeof vi.fn>
const mockGetAllEvents = getAllEvents as ReturnType<typeof vi.fn>
const mockGetUserParticipations = getUserParticipations as ReturnType<typeof vi.fn>
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
  mockGetAllEvents.mockResolvedValue([])
  mockGetUserParticipations.mockResolvedValue([])
  // Default mock so `MeProfileView`'s background refetch on /me resolves
  // quietly in every test. Tests that need a specific payload or a rejection
  // override this with their own mockResolvedValue / mockRejectedValue.
  mockGetUserByUsername.mockResolvedValue({
    id: OWN_UUID, username: 'test.user', displayName: 'Test User',
    faculty: null, studyLevel: null, bio: null, interests: [],
    avatarUrl: null, bannerUrl: null, profilePublic: true,
    followerCount: 0, followingCount: 0, followStatus: null,
  })
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
    if (typeof cls === 'string' && cls.includes('flex-wrap') && cls.includes('items-start') && cls.includes('gap-x-8')) {
      return el
    }
  }
  return null
}

describe('ProfilePage — /profile/me (owner)', () => {
  it('renders the owner profile when slug is "me" — no fetch by id', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    // `MeProfileView` does a background `getUserByUsername(currentUser.username)`
    // to surface real follower/following counts ; stub it so the effect resolves
    // cleanly (resolved value irrelevant for this assertion).
    mockGetUserByUsername.mockResolvedValue({
      ...otherProfile, id: OWN_UUID, username: 'test.user', followerCount: 0, followingCount: 0,
    })

    renderProfilePage('me')

    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
    expect(screen.getByText('Modifier')).toBeTruthy()
    expect(mockGetUserById).not.toHaveBeenCalled()
  })

  it('renders ProfileStats with the followers/following counts fetched in background', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({
      ...otherProfile,
      id: OWN_UUID,
      username: 'test.user',
      followerCount: 42,
      followingCount: 17,
    })

    renderProfilePage('me')

    // Tiles always render — initially 0/0, then update once the fetch resolves.
    expect(await screen.findByRole('link', { name: /Voir les abonnés \(42\)/i })).toBeTruthy()
    expect(screen.getByRole('link', { name: /Voir les abonnements \(17\)/i })).toBeTruthy()
    // Links point at /profile/{currentUser.username}/{followers|following}.
    expect(screen.getByRole('link', { name: /Voir les abonnés/i }).getAttribute('href'))
      .toBe('/profile/test.user/followers')
    expect(screen.getByRole('link', { name: /Voir les abonnements/i }).getAttribute('href'))
      .toBe('/profile/test.user/following')
  })

  it('cancels the background counts fetch when the page unmounts before it resolves', async () => {
    // Covers the `if (cancelled) return` guard in MeProfileView's useEffect.
    // Without it, a late resolve would call `setCounts` on an unmounted
    // component and React would warn — we want a clean no-op.
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    let resolveFetch: ((value: unknown) => void) = () => {}
    mockGetUserByUsername.mockReturnValueOnce(
      new Promise((resolve) => { resolveFetch = resolve }),
    )

    const { unmount } = renderProfilePage('me')
    await screen.findByRole('heading', { level: 1, name: 'Test User' })
    // Unmount BEFORE the promise resolves → cancelled flips to true.
    unmount()

    // Watch console.error : if the guard fails, React logs
    // "can't perform a React state update on an unmounted component".
    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    resolveFetch({ followerCount: 99, followingCount: 99 })
    await new Promise((r) => setTimeout(r, 0))
    expect(consoleErrorSpy).not.toHaveBeenCalled()
    consoleErrorSpy.mockRestore()
  })

  it('keeps ProfileStats at 0/0 when the background fetch fails (best-effort)', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockRejectedValue(new Error('boom'))

    renderProfilePage('me')

    // Page still renders, tiles are visible with 0 counts ; no toast.
    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
    expect(screen.getByRole('link', { name: /Voir les abonnés \(0\)/i })).toBeTruthy()
    expect(screen.getByRole('link', { name: /Voir les abonnements \(0\)/i })).toBeTruthy()
  })

  it('cancels background count fetch when unmounted before reject', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    let rejectFetch: ((reason: Error) => void) = () => {}
    mockGetUserByUsername.mockReturnValueOnce(
      new Promise((_, reject) => { rejectFetch = reject }),
    )

    const { unmount } = renderProfilePage('me')
    await screen.findByRole('heading', { level: 1, name: 'Test User' })
    unmount()

    const consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    rejectFetch(new Error('boom'))
    await new Promise((r) => setTimeout(r, 0))
    expect(consoleErrorSpy).not.toHaveBeenCalled()
    consoleErrorSpy.mockRestore()
  })

  it('handles null public data response from getUserByUsername for me', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(null)

    renderProfilePage('me')

    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
    expect(screen.getByRole('link', { name: /Voir les abonnés \(0\)/i })).toBeTruthy()
  })

  it('treats slug = current user username as owner route', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('test.user')

    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
    expect(screen.getByText('Modifier')).toBeTruthy()
  })

  it('redirects to login when own profile user is null and auth is loaded', async () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: false })

    renderProfilePage('me')

    expect(await screen.findByTestId('post-redirect-route')).toBeTruthy()
  })

  it('exposes the "Mes publications" tab on /me and renders its content when activated', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('me')

    const tab = await screen.findByRole('tab', { name: 'Mes publications' })
    ;(tab as HTMLButtonElement).click()
    expect(await screen.findByText('Voir toutes mes publications')).toBeTruthy()
  })

  it('renders the faculty name when present', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, faculty: 'SCIENCES', studyLevel: 'MASTER' },
      isLoading: false,
    })

    renderProfilePage('me')

    // Le redesign affiche le nom de la faculté (icône GraduationCap) — plus de
    // logo ni de niveau d'étude dans la colonne gauche.
    expect(await screen.findByText('Faculté des Sciences')).toBeTruthy()
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

  it('renders ProfileStats on /me — owner sees the same affordance as visitors', async () => {
    // Owner used to be denied the followers/following tiles because /users/me
    // doesn't carry counts ; we now refetch via getUserByUsername to populate
    // them. Tiles are always present.
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    renderProfilePage('me')
    await screen.findByRole('heading', { level: 1, name: 'Test User' })

    expect(screen.getByLabelText('Compteurs de suivi')).toBeTruthy()
  })
})

describe('ProfilePage — Staff badge (driven by profile.roles)', () => {
  // Surfaces the backend-mirrored Auth0 role on the rendered header. The
  // badge is owned by the profile (not the viewer) — visible to anyone
  // looking at an admin's profile, hidden on non-admin profiles even when
  // the viewer themself is admin.

  it('renders the badge on another user profile when roles includes ADMIN', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({ ...otherProfile, roles: ['ADMIN'] })

    renderProfilePage('other.user')

    expect(await screen.findByLabelText('Membre du staff')).toBeTruthy()
  })

  it('does NOT render the badge when roles is empty or missing on the target', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({ ...otherProfile, roles: [] })

    renderProfilePage('other.user')

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.queryByLabelText('Membre du staff')).toBeNull()
  })

  it('renders the badge on /profile/me when the owner has ADMIN role', async () => {
    // MeProfileView projects `user.roles` from useAuth into the UserPublicResponse
    // it hands to ProfileHeader — covers the /me code path specifically.
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, roles: ['ADMIN'] },
      isLoading: false,
    })

    renderProfilePage('me')

    expect(await screen.findByLabelText('Membre du staff')).toBeTruthy()
  })

  it('does NOT render the badge on /profile/me when the owner has no admin role', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, roles: ['STUDENT'] },
      isLoading: false,
    })

    renderProfilePage('me')

    await screen.findByRole('heading', { level: 1, name: 'Test User' })
    expect(screen.queryByLabelText('Membre du staff')).toBeNull()
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
    expect(screen.queryByRole('tab', { name: 'Mes publications' })).toBeNull()
  })

  it('does NOT render the Modifier button on another user profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.queryByText('Modifier')).toBeNull()
  })

  it('renders the "Événements organisés" tab (active by default)', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    expect(await screen.findByRole('tab', { name: 'Événements organisés' })).toBeTruthy()
    await waitFor(() => expect(mockGetAllEvents).toHaveBeenCalledWith(
      expect.objectContaining({ organizerId: OTHER_UUID, status: 'PUBLISHED' }),
    ))
  })

  it('renders the "Participations publiques" tab with the empty state when activated', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    // Onglet inactif par défaut (style Instagram) — on l'active pour révéler
    // le contenu (état vide dédié, SCRUM-141 follow-up).
    const tab = await screen.findByRole('tab', { name: 'Participations publiques' })
    ;(tab as HTMLButtonElement).click()
    expect(await screen.findByText('Aucune participation publique pour le moment.')).toBeTruthy()
  })

  it('renders the private-state card when getUserByUsername returns null (404 — user does not exist)', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(null)

    renderProfilePage('ghost.handle')

    expect(await screen.findByText('Page introuvable')).toBeTruthy()
  })

  it('renders an error message when getUserByUsername rejects', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockRejectedValue(new Error('Network error'))

    renderProfilePage('other.user')

    expect(await screen.findByText("Ce n'est pas vous, c'est nous.")).toBeTruthy()
  })

  it('calls setReloadKey when retry is clicked on error page', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername
      .mockRejectedValueOnce(new Error('Network error'))
      .mockResolvedValueOnce(otherProfile)

    renderProfilePage('other.user')
    const button = await screen.findByRole('button', { name: 'Réessayer' })
    fireEvent.click(button)

    await waitFor(() => expect(mockGetUserByUsername).toHaveBeenCalledTimes(2))
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

  it('does NOT render bio / counters / events / participations on a private profile', async () => {
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
  })

  it('renders a FollowButton on a private profile for an authenticated non-owner (followStatus null)', async () => {
    // Authenticated viewer looking at someone else's private profile — they
    // must be able to send a follow request even though the content is locked.
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({ ...privateProfile, followStatus: null })

    renderProfilePage('jane.private')

    await screen.findByRole('heading', { level: 2, name: 'Compte privé' })
    expect(screen.getByRole('button', { name: 'Suivre cet utilisateur' })).toBeTruthy()
  })

  it('renders FollowButton as "Demande envoyée" when followStatus is PENDING on a private profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({ ...privateProfile, followStatus: 'PENDING' })

    renderProfilePage('jane.private')

    await screen.findByRole('heading', { level: 2, name: 'Compte privé' })
    expect(screen.getByRole('button', { name: 'Annuler la demande de suivi' })).toBeTruthy()
  })

  it('renders the same locked view when followStatus is null', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({ ...privateProfile, followStatus: null })

    renderProfilePage('jane.private')

    expect(await screen.findByRole('heading', { level: 2, name: 'Compte privé' })).toBeTruthy()
  })

  it('shows the full public profile when followStatus is ACCEPTED (already following a private account)', async () => {
    // Regression test : when the target switched from public → private AFTER
    // the viewer was accepted as a follower, the viewer must still see the full
    // profile (not the locked card). Without the fix the page rendered
    // ProfilePrivateState + "Suivre" and a click produced a 409.
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue({
      ...privateProfile,
      bio: 'Bio secrète',
      followerCount: 5,
      followingCount: 3,
      followStatus: 'ACCEPTED',
    })

    renderProfilePage('jane.private')

    // Full profile rendered — heading level 1 (displayName), NO lock heading.
    expect(await screen.findByRole('heading', { level: 1, name: 'Jane Private' })).toBeTruthy()
    expect(screen.queryByRole('heading', { level: 2, name: 'Compte privé' })).toBeNull()
    // Content is visible.
    expect(screen.getByText('Bio secrète')).toBeTruthy()
    // FollowButton is in "Se désabonner" state (ACCEPTED).
    expect(screen.getByRole('button', { name: 'Se désabonner' })).toBeTruthy()
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

    expect(await screen.findByText('Page introuvable')).toBeTruthy()
  })

  it('shows an error message when the legacy UUID lookup rejects', async () => {
    // L309 — getUserById .catch sets the error state on the legacy path.
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserById.mockRejectedValue(new Error('Network error'))

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByText("Ce n'est pas vous, c'est nous.")).toBeTruthy()
    expect(mockGetUserByUsername).not.toHaveBeenCalled()
  })
})

describe('ProfilePage — FollowButton wiring (SCRUM-110)', () => {
  it('renders the FollowButton for an authenticated viewer on another user profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    expect(await screen.findByRole('button', { name: 'Suivre cet utilisateur' })).toBeTruthy()
  })

  it('renders the FollowButton for an unauthenticated viewer as "Suivre"', async () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: false })
    mockGetUserByUsername.mockResolvedValue(otherProfile)

    renderProfilePage('other.user')

    expect(await screen.findByRole('button', { name: 'Suivre cet utilisateur' })).toBeTruthy()
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
  it('applies items-start on the about/invitations grid on /me', async () => {
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
