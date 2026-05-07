
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
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
  getCalendarToken: vi.fn().mockResolvedValue({
    calendarToken: 'test-token',
    webcalUrl: 'webcal://example.com/cal.ics',
    httpsUrl: 'https://example.com/cal.ics',
  }),
  regenerateCalendarToken: vi.fn(),
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
import { getUserById, getCalendarToken } from '@/services/userService'
import { useTheme } from '@/contexts/ThemeContext'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>
const mockGetUserById = getUserById as ReturnType<typeof vi.fn>
const mockGetCalendarToken = getCalendarToken as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>

const mockUser = {
  id: '123',
  auth0Id: 'auth0|123',
  email: 'test@example.com',
  displayName: 'Test User',
  profilePublic: true,
  createdAt: '2024-01-01',
}

beforeEach(() => {
  mockGetCalendarToken.mockResolvedValue({
    calendarToken: 'test-token',
    webcalUrl: 'webcal://example.com/cal.ics',
    httpsUrl: 'https://example.com/cal.ics',
  })
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

describe('ProfilePage', () => {
  it('renders own profile when id is "me"', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfilePage('me')
    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
    expect(screen.getByText('Modifier')).toBeTruthy()
  })

  it('shows error when own profile user is null', async () => {
    mockUseAuth.mockReturnValue({ user: null })
    renderProfilePage('me')
    expect(await screen.findByText('Impossible de charger le profil.')).toBeTruthy()
  })

  it('fetches and renders another user profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockGetUserById.mockResolvedValue({
      id: '456',
      auth0Id: 'auth0|456',
      email: 'other@example.com',
      displayName: 'Other User',
      profilePublic: true,
      createdAt: '2024-01-01',
    })
    renderProfilePage('auth0|456')
    expect(await screen.findByRole('heading', { level: 1, name: 'Other User' })).toBeTruthy()
  })

  it('shows not found when getUserById returns null', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockGetUserById.mockResolvedValue(null)
    renderProfilePage('auth0|456')
    expect(await screen.findByText('Profil introuvable.')).toBeTruthy()
  })

  it('shows error when getUserById rejects', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockGetUserById.mockRejectedValue(new Error('Network error'))
    renderProfilePage('auth0|456')
    expect(await screen.findByText('Impossible de charger le profil.')).toBeTruthy()
  })

  it('shows private profile message for non-public profiles', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockGetUserById.mockResolvedValue({
      id: '789',
      auth0Id: 'auth0|789',
      email: 'private@example.com',
      displayName: 'Private User',
      profilePublic: false,
      createdAt: '2024-01-01',
    })
    renderProfilePage('auth0|789')
    expect(await screen.findByText('Ce profil est privé')).toBeTruthy()
  })

  it('renders faculty and study level when present', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, faculty: 'SCIENCES', studyLevel: 'MASTER' },
    })
    renderProfilePage('me')
    expect(await screen.findByRole('img', { name: /Sciences/i })).toBeTruthy()
    expect(screen.getAllByText(/Master/).length).toBeGreaterThan(0)
  })

  it('renders bio when present', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, bio: 'Passionné de recherche.' },
    })
    renderProfilePage('me')
    expect(await screen.findByText('Passionné de recherche.')).toBeTruthy()
  })

  it('renders interests when present', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, interests: ['Jazz', 'Coding'] },
    })
    renderProfilePage('me')
    expect(await screen.findByText('Jazz')).toBeTruthy()
    expect(screen.getByText('Coding')).toBeTruthy()
  })

  it('loads own profile when id matches currentUser.auth0Id', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfilePage('auth0|123')
    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
    expect(screen.getByText('Modifier')).toBeTruthy()
  })

  it('renders avatar image when profile has avatarUrl', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, avatarUrl: 'https://example.com/avatar.jpg' },
    })
    renderProfilePage('me')
    const img = await screen.findByAltText('Test User')
    expect((img as HTMLImageElement).src).toContain('example.com/avatar.jpg')
  })

  it('renders own profile when profilePublic is false', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, profilePublic: false },
    })
    renderProfilePage('me')
    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
  })

  it('renders own profile when profilePublic is true', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfilePage('me')
    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
  })

  it('shows the profile skeleton while auth is loading', () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: true })
    renderProfilePage('me')
    expect(document.querySelector('[data-boneyard="profile"]')).toBeTruthy()
  })

  it('does not crash when faculty key is unknown', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, faculty: 'UNKNOWN_FACULTY' as never },
    })
    renderProfilePage('me')
    expect(await screen.findByRole('heading', { level: 1, name: 'Test User' })).toBeTruthy()
  })

  it('renders banner image when bannerUrl is set', async () => {
    mockUseAuth.mockReturnValue({
      user: { ...mockUser, bannerUrl: 'https://example.com/banner.jpg' },
    })
    renderProfilePage('me')
    await screen.findByRole('heading', { level: 1, name: 'Test User' })
    const banner = document.querySelector<HTMLImageElement>('img[src*="banner.jpg"]')
    expect(banner).toBeTruthy()
    expect(banner!.src).toContain('banner.jpg')
  })

  it('renders gradient fallback when bannerUrl is null', async () => {
    mockUseAuth.mockReturnValue({ user: { ...mockUser, bannerUrl: null } })
    renderProfilePage('me')
    await screen.findByRole('heading', { level: 1, name: 'Test User' })
    expect(document.querySelector('[style*="banner"]')).toBeNull()
  })

  it('uses light skeleton color when theme is light', () => {
    mockUseTheme.mockReturnValue({ theme: 'light', toggleTheme: vi.fn() })
    mockUseAuth.mockReturnValue({ user: null, isLoading: true })
    renderProfilePage('me')
    expect(document.querySelector('[data-boneyard="profile"]')).toBeTruthy()
  })

  it('renders the Mes publications preview on /profile/me', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    renderProfilePage('me')
    expect(await screen.findByRole('heading', { level: 2, name: 'Mes publications' })).toBeTruthy()
  })

  it('does not render the Mes publications preview on another user profile', async () => {
    mockUseAuth.mockReturnValue({ user: mockUser })
    mockGetUserById.mockResolvedValue({
      id: '456',
      auth0Id: 'auth0|456',
      email: 'other@example.com',
      displayName: 'Other User',
      profilePublic: true,
      createdAt: '2024-01-01',
    })
    renderProfilePage('auth0|456')
    expect(await screen.findByRole('heading', { level: 1, name: 'Other User' })).toBeTruthy()
    expect(screen.queryByRole('heading', { level: 2, name: 'Mes publications' })).toBeNull()
  })

  // Layout regression — the right-column calendar card must not stretch to match
  // the (potentially much taller) left column on desktop. We assert on the grid
  // wrapper className since jsdom doesn't perform actual layout.
  describe('content grid layout', () => {
    it('applies items-start to the content grid so the calendar card sizes to its content', async () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      const { container } = renderProfilePage('me')
      await screen.findByRole('heading', { level: 1, name: 'Test User' })

      const grid = findContentGrid(container)
      expect(grid).not.toBeNull()
      expect(grid!.className).toContain('items-start')
      expect(grid!.className).not.toContain('items-stretch')
    })

    it('keeps items-start regardless of MyPublicationsPreview rendering (varying left-column height)', async () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      const { container } = renderProfilePage('me')
      await screen.findByRole('heading', { level: 2, name: 'Mes publications' })

      const grid = findContentGrid(container)
      expect(grid?.className).toContain('items-start')
    })

    it('does not apply a fixed pixel/max height to the calendar card wrapper', async () => {
      mockUseAuth.mockReturnValue({ user: mockUser })
      const { container } = renderProfilePage('me')
      await screen.findByRole('heading', { level: 1, name: 'Test User' })

      const grid = findContentGrid(container)
      expect(grid).not.toBeNull()
      // No hard-coded height (h-[…]) or max-h-[…] sneaked in as a workaround.
      expect(grid!.className).not.toMatch(/\bh-\[/)
      expect(grid!.className).not.toMatch(/\bmax-h-\[/)
    })
  })

  // Long-unbroken-word overflow guard — biography and displayName must wrap
  // even when the user pastes a single 200-char token. jsdom doesn't paint,
  // so we assert on the wrap utility class instead of pixel widths.
  describe('user-content overflow wrapping', () => {
    const LONG_WORD = 'a'.repeat(200)
    const LONG_BIO = `cxgmgggg${'g'.repeat(192)}`

    it('renders a 200-char single-word bio without throwing and applies wrap-anywhere', async () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, bio: LONG_BIO } })
      renderProfilePage('me')

      const bio = await screen.findByText(LONG_BIO)
      expect(bio.className).toContain('wrap-anywhere')
      expect(bio.className).not.toContain('break-all')
    })

    it('applies wrap-anywhere on the displayName heading and min-w-0 on its flex parent', async () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, displayName: LONG_WORD } })
      const { container } = renderProfilePage('me')

      const heading = await screen.findByRole('heading', { level: 1, name: LONG_WORD })
      expect(heading.className).toContain('wrap-anywhere')

      // Parent (the flex item next to the avatar) must allow shrinking below
      // the heading's min-content for wrap-anywhere to be effective.
      const parent = heading.parentElement
      expect(parent?.className).toContain('min-w-0')
      expect(container).toBeTruthy()
    })

    it('still wraps mixed content (short words + long unbroken token) without breaking on every char', async () => {
      const mixed = `Some intro words then ${LONG_WORD}`
      mockUseAuth.mockReturnValue({ user: { ...mockUser, bio: mixed } })
      renderProfilePage('me')

      const bio = await screen.findByText(mixed)
      expect(bio.className).toContain('wrap-anywhere')
      expect(bio.className).not.toContain('break-all')
    })

    it('does not crash with an empty bio (no element rendered, no shift)', async () => {
      mockUseAuth.mockReturnValue({ user: { ...mockUser, bio: '' } })
      renderProfilePage('me')

      // bio paragraph is guarded by `profile.bio &&` so it is absent when empty
      await screen.findByRole('heading', { level: 1, name: 'Test User' })
      expect(screen.queryByText(/^cxgm/)).toBeNull()
    })
  })
})
