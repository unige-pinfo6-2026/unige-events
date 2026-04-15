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

import { useAuth0 } from '@auth0/auth0-react'

const mockUseAuth0 = useAuth0 as ReturnType<typeof vi.fn>

afterEach(() => {
  localStorage.clear()
  cleanup()
  vi.resetAllMocks()
})

describe('AppRouter', () => {
  it('shows landing page at /', async () => {
    mockUseAuth0.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { sub: 'auth0|123', email: 'test@example.com', name: 'Test User' },
      loginWithRedirect: vi.fn(),
      logout: vi.fn(),
      getAccessTokenSilently: vi.fn().mockResolvedValue('test-token'),
    })

    render(
      <ToastProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={['/']}>
            <AppRouter />
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    )

    // AppRouter lazy-imports every page — under heavy parallel test load the dynamic
    // import can outlast the default 1000ms waitFor window, leaving the Suspense fallback
    // (LoadingPage spinner) visible when findByText resolves. Give it enough breathing room.
    expect(await screen.findByText(/Événements à venir/i, undefined, { timeout: 15000 })).toBeTruthy()
  })

  it('shows 404 page for unknown routes', async () => {
    mockUseAuth0.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { sub: 'auth0|123', email: 'test@example.com', name: 'Test User' },
      loginWithRedirect: vi.fn(),
      logout: vi.fn(),
      getAccessTokenSilently: vi.fn().mockResolvedValue('test-token'),
    })

    render(
      <ToastProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={['/unknown']}>
            <AppRouter />
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    )

    expect(await screen.findByText('Page introuvable', undefined, { timeout: 15000 })).toBeTruthy()
  })

  it('blocks protected routes when not authenticated', async () => {
    mockUseAuth0.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      loginWithRedirect: vi.fn(),
      logout: vi.fn(),
      getAccessTokenSilently: vi.fn(),
    })

    render(
      <ToastProvider>
        <AuthProvider>
          <MemoryRouter initialEntries={['/profile/me']}>
            <AppRouter />
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    )

    expect(await screen.findByText(/Redirection vers la page de connexion/, undefined, { timeout: 15000 })).toBeTruthy()
  })
})
