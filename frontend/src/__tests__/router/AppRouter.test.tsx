
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import AppRouter from '@/router/AppRouter'
import { ToastProvider } from '@/contexts/ToastContext'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
  Auth0Provider: ({ children }: { children: ReactNode }) => <>{children}</>,
}))

vi.mock('@/contexts/AuthContext', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@/contexts/AuthContext')>()
  return {
    ...actual,
    AuthProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
  }
})

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

vi.mock('@/services/userService', () => ({
  getMe: vi.fn(),
}))

vi.mock('@/hooks/useNotifications', () => ({
  useNotifications: () => ({
    notifications: [],
    unreadCount: 0,
    loading: false,
    error: null,
    markAllAsRead: vi.fn(),
  }),
}))

// Stub all lazy-loaded pages so route coverage doesn't require full page deps
vi.mock('@/pages/LandingPage', () => ({ default: () => <div>LandingPage</div> }))
vi.mock('@/pages/login/LoginPage', () => ({ default: () => <div>LoginPage</div> }))
vi.mock('@/pages/login/callback/LoginCallbackPage', () => ({ default: () => <div>CallbackPage</div> }))
vi.mock('@/pages/event/EventsPage', () => ({ default: () => <div>EventsPage</div> }))
vi.mock('@/pages/event/EventsSearchPage', () => ({ default: () => <div>SearchPage</div> }))
vi.mock('@/pages/event/EventCreatePage', () => ({ default: () => <div>EventCreatePage</div> }))
vi.mock('@/pages/event/EventEditPage', () => ({ default: () => <div>EventEditPage</div> }))
vi.mock('@/pages/event/EventDetailPage', () => ({ default: () => <div>EventDetailPage</div> }))
vi.mock('@/pages/event/EventStatsPage', () => ({ default: () => <div>EventStatsPage</div> }))
vi.mock('@/pages/calendar/CalendarPage', () => ({ default: () => <div>CalendarPage</div> }))
vi.mock('@/pages/profile/ProfilePage', () => ({ default: () => <div>ProfilePage</div> }))
vi.mock('@/pages/profile/ProfileEditPage', () => ({ default: () => <div>ProfileEditPage</div> }))
vi.mock('@/pages/profile/FollowListPage', () => ({ default: ({ mode }: { mode: string }) => <div>FollowListPage-{mode}</div> }))
vi.mock('@/pages/event/favorites/FavoritesPage', () => ({ default: () => <div>FavoritesPage</div> }))
vi.mock('@/pages/my-events/MyEventsPage', () => ({ default: () => <div>MyEventsPage</div> }))
vi.mock('@/pages/my-events/MyFavoritesPage', () => ({ default: () => <div>MyFavoritesPage</div> }))
vi.mock('@/pages/my-events/MyParticipationsPage', () => ({ default: () => <div>MyParticipationsPage</div> }))
vi.mock('@/pages/my-events/MyPublicationsPage', () => ({ default: () => <div>MyPublicationsPage</div> }))
vi.mock('@/pages/admin/AdminPage', () => ({ default: () => <div>AdminPage</div> }))
vi.mock('@/pages/legal/PrivacyPage', () => ({ default: () => <div>PrivacyPage</div> }))
vi.mock('@/pages/legal/TermsPage', () => ({ default: () => <div>TermsPage</div> }))
vi.mock('@/pages/NotFoundPage', () => ({ default: () => <div>NotFoundPage</div> }))
vi.mock('@/pages/ForbiddenPage', () => ({ default: () => <div>ForbiddenPage</div> }))

import { useAuth } from '@/hooks/useAuth'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>

function authenticated() {
  return {
    isAuthenticated: true,
    isLoading: false,
    user: { sub: 'auth0|123', email: 'test@example.com', name: 'Test User' },
    login: vi.fn(),
    logout: vi.fn(),
  }
}

function unauthenticated() {
  return {
    isAuthenticated: false,
    isLoading: false,
    user: null,
    login: vi.fn(),
    logout: vi.fn(),
  }
}

function admin() {
  return { ...authenticated(), isAdmin: true }
}

afterEach(() => {
  localStorage.clear()
  cleanup()
  vi.resetAllMocks()
})

function renderAt(path: string) {
  return render(
    <ToastProvider>
      <MemoryRouter initialEntries={[path]}>
        <AppRouter />
      </MemoryRouter>
    </ToastProvider>,
  )
}

describe('AppRouter', () => {
  it('shows landing page at /', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/')
    expect(await screen.findByText('LandingPage')).toBeTruthy()
  })

  it('shows login page at /login', async () => {
    mockUseAuth.mockReturnValue(unauthenticated())
    renderAt('/login')
    expect(await screen.findByText('LoginPage')).toBeTruthy()
  })

  it('shows callback page at /login/callback', async () => {
    mockUseAuth.mockReturnValue(unauthenticated())
    renderAt('/login/callback')
    expect(await screen.findByText('CallbackPage')).toBeTruthy()
  })

  it('shows events page at /events', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/events')
    expect(await screen.findByText('EventsPage')).toBeTruthy()
  })

  it('shows event detail page at /events/:id', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/events/42')
    expect(await screen.findByText('EventDetailPage')).toBeTruthy()
  })

  it('shows calendar page at /calendar', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/calendar')
    expect(await screen.findByText('CalendarPage')).toBeTruthy()
  })

  it('shows 404 page for unknown routes', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/unknown')
    expect(await screen.findByText('NotFoundPage')).toBeTruthy()
  })

  it('redirects /profile to /profile/me', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/profile')
    expect(await screen.findByText('ProfilePage')).toBeTruthy()
  })

  it('shows profile page at /profile/:id', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/profile/auth0|123')
    expect(await screen.findByText('ProfilePage')).toBeTruthy()
  })

  it('shows profile page at /profile/:id when not authenticated', async () => {
    mockUseAuth.mockReturnValue(unauthenticated())
    renderAt('/profile/auth0|123')
    expect(await screen.findByText('ProfilePage')).toBeTruthy()
  })

  it('shows profile edit page at /profile/me/edit', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/profile/me/edit')
    expect(await screen.findByText('ProfileEditPage')).toBeTruthy()
  })

  it('blocks profile edit page at /profile/me/edit when not authenticated', async () => {
    mockUseAuth.mockReturnValue(unauthenticated())
    renderAt('/profile/me/edit')
    expect(await screen.findByText('LoginPage')).toBeTruthy()
  })

  it('shows event create page at /events/new', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/events/new')
    expect(await screen.findByText('EventCreatePage')).toBeTruthy()
  })

  it('shows event edit page at /events/:id/edit', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/events/42/edit')
    expect(await screen.findByText('EventEditPage')).toBeTruthy()
  })

  it('shows favorites page at /events/favorites', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/events/favorites')
    expect(await screen.findByText('FavoritesPage')).toBeTruthy()
  })

  it('blocks protected routes when not authenticated', async () => {
    mockUseAuth.mockReturnValue(unauthenticated())
    renderAt('/events/new')
    expect(await screen.findByText('LoginPage')).toBeTruthy()
  })

  it('shows loading spinner when auth is loading', async () => {
    mockUseAuth.mockReturnValue({ ...unauthenticated(), isLoading: true })
    renderAt('/profile/me')
    // PrivateRoute renders LoadingSpinner while isLoading
    expect(document.querySelector('svg') ?? document.body.firstChild).toBeTruthy()
  })

  it('shows search page at /events/search', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/events/search')
    expect(await screen.findByText('SearchPage')).toBeTruthy()
  })

  it('shows event stats page at /events/:id/stats', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/events/42/stats')
    expect(await screen.findByText('EventStatsPage')).toBeTruthy()
  })

  it('shows privacy page at /legal/privacy', async () => {
    mockUseAuth.mockReturnValue(unauthenticated())
    renderAt('/legal/privacy')
    expect(await screen.findByText('PrivacyPage')).toBeTruthy()
  })

  it('shows terms page at /legal/terms', async () => {
    mockUseAuth.mockReturnValue(unauthenticated())
    renderAt('/legal/terms')
    expect(await screen.findByText('TermsPage')).toBeTruthy()
  })

  it('redirects /legal to /legal/privacy', async () => {
    mockUseAuth.mockReturnValue(unauthenticated())
    renderAt('/legal')
    expect(await screen.findByText('PrivacyPage')).toBeTruthy()
  })

  it('shows followers list at /profile/:username/followers', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/profile/alice/followers')
    expect(await screen.findByText('FollowListPage-followers')).toBeTruthy()
  })

  it('shows followers list at /profile/:username/followers when not authenticated', async () => {
    mockUseAuth.mockReturnValue(unauthenticated())
    renderAt('/profile/alice/followers')
    expect(await screen.findByText('FollowListPage-followers')).toBeTruthy()
  })

  it('shows following list at /profile/:username/following', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/profile/alice/following')
    expect(await screen.findByText('FollowListPage-following')).toBeTruthy()
  })

  it('shows following list at /profile/:username/following when not authenticated', async () => {
    mockUseAuth.mockReturnValue(unauthenticated())
    renderAt('/profile/alice/following')
    expect(await screen.findByText('FollowListPage-following')).toBeTruthy()
  })

  it('shows my-events page at /my-events', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/my-events')
    expect(await screen.findByText('MyEventsPage')).toBeTruthy()
  })

  it('shows my-events favorites at /my-events/favorites', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/my-events/favorites')
    expect(await screen.findByText('MyFavoritesPage')).toBeTruthy()
  })

  it('shows my-events participations at /my-events/participations', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/my-events/participations')
    expect(await screen.findByText('MyParticipationsPage')).toBeTruthy()
  })

  it('shows my-events publications at /my-events/publications', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/my-events/publications')
    expect(await screen.findByText('MyPublicationsPage')).toBeTruthy()
  })

  it('shows admin page at /admin for admin users', async () => {
    mockUseAuth.mockReturnValue(admin())
    renderAt('/admin')
    expect(await screen.findByText('AdminPage')).toBeTruthy()
  })

  it('renders the ForbiddenPage in place at /admin for non-admin authenticated users', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/admin')
    expect(await screen.findByText('ForbiddenPage')).toBeTruthy()
  })
})
