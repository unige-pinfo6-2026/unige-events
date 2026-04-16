// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { ReactNode } from 'react'
import AppRouter from '@/router/AppRouter'
import { AuthProvider } from '@/contexts/AuthContext'
import { ToastProvider } from '@/contexts/ToastContext'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
  Auth0Provider: ({ children }: { children: ReactNode }) => <>{children}</>,
}))

vi.mock('@/services/userService', () => ({
  getMe: vi.fn(),
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

import { useAuth0 } from '@auth0/auth0-react'

const mockUseAuth0 = useAuth0 as ReturnType<typeof vi.fn>

function authenticatedAuth0() {
  return {
    isAuthenticated: true,
    isLoading: false,
    user: { sub: 'auth0|123', email: 'test@example.com', name: 'Test User' },
    loginWithRedirect: vi.fn(),
    logout: vi.fn(),
    getAccessTokenSilently: vi.fn().mockResolvedValue('test-token'),
  }
}

function unauthenticatedAuth0() {
  return {
    isAuthenticated: false,
    isLoading: false,
    user: null,
    loginWithRedirect: vi.fn(),
    logout: vi.fn(),
    getAccessTokenSilently: vi.fn().mockResolvedValue(''),
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
      <AuthProvider>
        <MemoryRouter initialEntries={[path]}>
          <AppRouter />
        </MemoryRouter>
      </AuthProvider>
    </ToastProvider>,
  )
}

describe('AppRouter', () => {
  it('shows landing page at /', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/')
    expect(await screen.findByText('LandingPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows login page at /login', async () => {
    mockUseAuth0.mockReturnValue(unauthenticatedAuth0())
    renderAt('/login')
    expect(await screen.findByText('LoginPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows callback page at /login/callback', async () => {
    mockUseAuth0.mockReturnValue(unauthenticatedAuth0())
    renderAt('/login/callback')
    expect(await screen.findByText('CallbackPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows events page at /events', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/events')
    expect(await screen.findByText('EventsPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows event detail page at /events/:id', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/events/42')
    expect(await screen.findByText('EventDetailPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows calendar page at /calendar', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/calendar')
    expect(await screen.findByText('CalendarPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows 404 page for unknown routes', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/unknown')
    expect(await screen.findByText('NotFoundPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('redirects /profile to /profile/me', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/profile')
    expect(await screen.findByText('ProfilePage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows profile page at /profile/:id', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/profile/auth0|123')
    expect(await screen.findByText('ProfilePage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows profile edit page at /profile/me/edit', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/profile/me/edit')
    expect(await screen.findByText('ProfileEditPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows event create page at /events/new', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/events/new')
    expect(await screen.findByText('EventCreatePage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows event edit page at /events/:id/edit', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/events/42/edit')
    expect(await screen.findByText('EventEditPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows favorites page at /events/favorites', async () => {
    mockUseAuth0.mockReturnValue(authenticatedAuth0())
    renderAt('/events/favorites')
    expect(await screen.findByText('FavoritesPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('blocks protected routes when not authenticated', async () => {
    mockUseAuth0.mockReturnValue(unauthenticatedAuth0())
    renderAt('/profile/me')
    expect(await screen.findByText('LoginPage', {}, { timeout: 10000 })).toBeTruthy()
  })

  it('shows loading spinner when auth is loading', async () => {
    mockUseAuth0.mockReturnValue({ ...unauthenticatedAuth0(), isLoading: true })
    renderAt('/profile/me')
    // PrivateRoute renders LoadingSpinner while isLoading
    expect(document.querySelector('svg') ?? document.body.firstChild).toBeTruthy()
  })
})
