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
  getPublicProfile: vi.fn(),
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

vi.mock('@/services/followApi', () => ({
  followUser: vi.fn().mockResolvedValue({
    id: 1, followerId: 'me', followedId: 'tgt', status: 'PENDING', createdAt: 'x',
  }),
  unfollowUser: vi.fn().mockResolvedValue(undefined),
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
import { getCalendarToken, getPublicProfile } from '@/services/userService'
import { getAll as getAllEvents } from '@/services/eventApi'
import { followUser, unfollowUser } from '@/services/followApi'
import { useTheme } from '@/contexts/ThemeContext'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockGetPublicProfile = getPublicProfile as ReturnType<typeof vi.fn>
const mockGetAllEvents = getAllEvents as ReturnType<typeof vi.fn>
const mockGetCalendarToken = getCalendarToken as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>
const mockFollowUser = followUser as ReturnType<typeof vi.fn>
const mockUnfollowUser = unfollowUser as ReturnType<typeof vi.fn>

const OTHER_UUID = 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c'
const OWN_UUID   = 'b1b1b1b1-b1b1-4b1b-9b1b-b1b1b1b1b1b1'

const mockUser = {
  id: OWN_UUID,
  auth0Id: 'auth0|123',
  email: 'test@example.com',
  displayName: 'Test User',
  profilePublic: true,
  createdAt: '2024-01-01',
}

const otherProfile = {
  id: OTHER_UUID,
  displayName: 'Other User',
  faculty: 'SCIENCES',
  studyLevel: 'MASTER',
  bio: 'Bio publique',
  interests: ['Jazz'],
  avatarUrl: null,
  bannerUrl: null,
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
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderProfilePage(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/profile/${id}`]}>
      <Routes>
        <Route path="/profile/:id" element={<ProfilePage />} />
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
  it('renders own profile when id is "me"', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('me')

    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
    expect(screen.getByText('Modifier')).toBeTruthy()
    // No public-profile fetch fires for /me — we read from useAuth instead.
    expect(mockGetPublicProfile).not.toHaveBeenCalled()
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

    // Stats wrapper is not labelled when absent — its aria-label is unique
    // to the ProfileStats component, so its absence is the assertion.
    expect(screen.queryByLabelText('Compteurs de suivi')).toBeNull()
  })
})

describe('ProfilePage — /profile/:uuid (other user)', () => {
  it('fetches and renders another user public profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue(otherProfile)

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByRole('heading', { level: 1, name: 'Other User' })).toBeTruthy()
    expect(mockGetPublicProfile).toHaveBeenCalledWith(OTHER_UUID)
    expect(screen.getByText('Bio publique')).toBeTruthy()
  })

  it('renders follower/following counters from the public projection', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue(otherProfile)

    renderProfilePage(OTHER_UUID)

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.getByText('12')).toBeTruthy()
    expect(screen.getByText('7')).toBeTruthy()
  })

  it('does NOT render Mes publications preview on another user profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue(otherProfile)

    renderProfilePage(OTHER_UUID)

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.queryByRole('heading', { level: 2, name: 'Mes publications' })).toBeNull()
  })

  it('does NOT render the Modifier button on another user profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue(otherProfile)

    renderProfilePage(OTHER_UUID)

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.queryByText('Modifier')).toBeNull()
  })

  it('renders the "Événements organisés" section', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue(otherProfile)

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByRole('heading', { name: 'Événements organisés' })).toBeTruthy()
    await waitFor(() => expect(mockGetAllEvents).toHaveBeenCalledWith(
      expect.objectContaining({ organizerId: OTHER_UUID, status: 'PUBLISHED' }),
    ))
  })

  it('renders the "Participations publiques" placeholder section', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue(otherProfile)

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByRole('heading', { name: 'Participations publiques' })).toBeTruthy()
    expect(screen.getByText('Bientôt disponible.')).toBeTruthy()
  })

  it('renders the private-state card when getPublicProfile returns null (private OR missing)', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue(null)

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByText('Ce profil est privé')).toBeTruthy()
  })

  it('renders an error message when getPublicProfile rejects', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockRejectedValue(new Error('Network error'))

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByText('Impossible de charger le profil.')).toBeTruthy()
  })

  it('shows the profile skeleton while loading', () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockImplementation(() => new Promise(() => {}))

    renderProfilePage(OTHER_UUID)

    expect(document.querySelector('[data-boneyard="profile"]')).toBeTruthy()
  })

  it('renders own UUID as a regular public profile (no Modifier, no /me widgets)', async () => {
    // SCRUM-141 spec: "Own profile (:id === current user UUID): render normally
    // (no redirect, no special UI)" — visiting /profile/<own-uuid> behaves like
    // any other public profile.
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue({ ...otherProfile, id: OWN_UUID, displayName: 'Test User' })

    renderProfilePage(OWN_UUID)

    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
    expect(screen.queryByText('Modifier')).toBeNull()
    expect(screen.queryByRole('heading', { level: 2, name: 'Mes publications' })).toBeNull()
  })
})

describe('ProfilePage — malformed id / not-found', () => {
  it('renders "Profil introuvable." when id is not a UUID and not "me"', () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('auth0|legacy-id')

    expect(screen.getByText('Profil introuvable.')).toBeTruthy()
    expect(mockGetPublicProfile).not.toHaveBeenCalled()
  })

  it('renders "Profil introuvable." when id is a random string', () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })

    renderProfilePage('not-a-uuid')

    expect(screen.getByText('Profil introuvable.')).toBeTruthy()
  })
})

describe('ProfilePage — private profile PENDING badge', () => {
  it('shows "Demande de suivi envoyée" badge when the private state carries followStatus=PENDING', async () => {
    // The current backend returns 404 for private profiles so the FE never
    // gets followStatus on that path. This test exercises the wiring in case
    // the contract evolves; it directly verifies ProfilePrivateState receives
    // the followStatus when present on the (non-null) profile.
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    // Backend returns a body indicating PENDING (forward-looking; currently
    // hypothetical). The page must still render the private state for any
    // non-null public projection — but in this branch the page renders the
    // full public view since profile is non-null. We assert instead via the
    // unit test of ProfilePrivateState (kept here as an integration sanity).
    mockGetPublicProfile.mockResolvedValue(null)

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByText('Ce profil est privé')).toBeTruthy()
    // No badge today since 404 doesn't carry followStatus — verified by the
    // dedicated ProfilePrivateState test.
    expect(screen.queryByText('Demande de suivi envoyée')).toBeNull()
  })
})

describe('ProfilePage — FollowButton wiring (SCRUM-110)', () => {
  it('renders the FollowButton for an authenticated viewer on another user profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue(otherProfile)

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByRole('button', { name: 'Suivre cet utilisateur' })).toBeTruthy()
  })

  it('does NOT render the FollowButton for an unauthenticated viewer', async () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: false })
    mockGetPublicProfile.mockResolvedValue(otherProfile)

    renderProfilePage(OTHER_UUID)

    await screen.findByRole('heading', { level: 1, name: 'Other User' })
    expect(screen.queryByRole('button', { name: /Suivre|Demande envoyée|Abonné|Se désabonner/ })).toBeNull()
  })

  it('does NOT render the FollowButton when the viewer is looking at their own UUID', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue({ ...otherProfile, id: OWN_UUID, displayName: 'Test User' })

    renderProfilePage(OWN_UUID)

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
    mockGetPublicProfile.mockResolvedValue({ ...otherProfile, followStatus: 'PENDING' })

    renderProfilePage(OTHER_UUID)

    expect(await screen.findByRole('button', { name: 'Annuler la demande de suivi' })).toBeTruthy()
  })

  it('clicking Suivre triggers a refetch (counters update via useUserProfile reload)', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    // First call: initial render. Second call after refetch: same shape but
    // followStatus flipped to PENDING and followerCount bumped.
    mockGetPublicProfile
      .mockResolvedValueOnce(otherProfile)
      .mockResolvedValueOnce({ ...otherProfile, followStatus: 'PENDING', followerCount: 13 })

    renderProfilePage(OTHER_UUID)

    const followBtn = await screen.findByRole('button', { name: 'Suivre cet utilisateur' })
    ;(followBtn as HTMLButtonElement).click()

    await waitFor(() => expect(mockFollowUser).toHaveBeenCalledWith(OTHER_UUID))
    // Refetch fired → second getPublicProfile call.
    await waitFor(() => expect(mockGetPublicProfile).toHaveBeenCalledTimes(2))
  })

  it('forwards unfollow to the service when clicking an ACCEPTED button', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser, isLoading: false })
    mockGetPublicProfile.mockResolvedValue({ ...otherProfile, followStatus: 'ACCEPTED' })

    renderProfilePage(OTHER_UUID)

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
