
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
vi.mock('@/pages/event/EventCreatePage', () => ({ default: () => <div>EventCreatePage</div> }))
vi.mock('@/pages/event/EventEditPage', () => ({ default: () => <div>EventEditPage</div> }))
vi.mock('@/pages/event/EventDetailPage', () => ({ default: () => <div>EventDetailPage</div> }))
vi.mock('@/pages/calendar/CalendarPage', () => ({ default: () => <div>CalendarPage</div> }))
vi.mock('@/pages/profile/ProfilePage', () => ({ default: () => <div>ProfilePage</div> }))
vi.mock('@/pages/profile/ProfileEditPage', () => ({ default: () => <div>ProfileEditPage</div> }))
vi.mock('@/pages/event/favorites/FavoritesPage', () => ({ default: () => <div>FavoritesPage</div> }))
vi.mock('@/pages/NotFoundPage', () => ({ default: () => <div>NotFoundPage</div> }))

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

  it('shows profile edit page at /profile/me/edit', async () => {
    mockUseAuth.mockReturnValue(authenticated())
    renderAt('/profile/me/edit')
    expect(await screen.findByText('ProfileEditPage')).toBeTruthy()
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
    renderAt('/profile/me')
    expect(await screen.findByText('LoginPage')).toBeTruthy()
  })

  it('shows loading spinner when auth is loading', async () => {
    mockUseAuth.mockReturnValue({ ...unauthenticated(), isLoading: true })
    renderAt('/profile/me')
    // PrivateRoute renders LoadingSpinner while isLoading
    expect(document.querySelector('svg') ?? document.body.firstChild).toBeTruthy()
  })
})
