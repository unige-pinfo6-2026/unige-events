// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { ReactNode } from 'react'
import PrivateRoute from '@/components/PrivateRoute'
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

describe('PrivateRoute', () => {
  it('redirects to /login when not authenticated', async () => {
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
          <MemoryRouter initialEntries={['/']}>
            <Routes>
              <Route path="/login" element={<h1>Login</h1>} />
              <Route element={<PrivateRoute />}>
                <Route path="/" element={<h1>Protected Content</h1>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    )

    expect(await screen.findByRole('heading', { name: 'Login' })).toBeTruthy()
    expect(screen.queryByText('Protected Content')).toBeNull()
  })

  it('renders protected content when authenticated', async () => {
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
          <MemoryRouter initialEntries={['/home']}>
            <Routes>
              <Route path="/login" element={<h1>Login</h1>} />
              <Route element={<PrivateRoute />}>
                <Route path="/home" element={<h1>Protected Content</h1>} />
              </Route>
            </Routes>
          </MemoryRouter>
        </AuthProvider>
      </ToastProvider>,
    )

    expect(
      await screen.findByRole('heading', { name: 'Protected Content' }),
    ).toBeTruthy()
    expect(screen.queryByText('Login')).toBeNull()
  })
})
