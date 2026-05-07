import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom'
import AdminRoute from '@/components/AdminRoute'

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

vi.mock('@/components/utils/LoadingSpinner', () => ({
  LoadingSpinner: () => <div data-testid="spinner" />,
}))

import { useAuth } from '@/hooks/useAuth'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>

function renderInRouter(initialPath = '/admin') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <Routes>
        <Route path="/login" element={<h1>Login</h1>} />
        <Route path="/403" element={<h1>Forbidden</h1>} />
        <Route element={<AdminRoute />}>
          <Route path="/admin" element={<h1>Admin Dashboard</h1>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('AdminRoute', () => {
  it('shows spinner while loading', () => {
    mockUseAuth.mockReturnValue({ user: null, isAuthenticated: false, isAdmin: false, isLoading: true })
    renderInRouter()
    expect(screen.getByTestId('spinner')).toBeTruthy()
    expect(screen.queryByText('Admin Dashboard')).toBeNull()
  })

  it('redirects to /login when not authenticated', async () => {
    mockUseAuth.mockReturnValue({ user: null, isAuthenticated: false, isAdmin: false, isLoading: false })
    renderInRouter()
    expect(await screen.findByRole('heading', { name: 'Login' })).toBeTruthy()
    expect(screen.queryByText('Admin Dashboard')).toBeNull()
  })

  it('passes returnTo state with current path when redirecting to /login', async () => {
    mockUseAuth.mockReturnValue({ user: null, isAuthenticated: false, isAdmin: false, isLoading: false })

    const { unmount } = render(
      <MemoryRouter initialEntries={['/admin']}>
        <Routes>
          <Route path="/login" element={<LocationState />} />
          <Route element={<AdminRoute />}>
            <Route path="/admin" element={<h1>Admin</h1>} />
          </Route>
        </Routes>
      </MemoryRouter>,
    )

    expect((await screen.findByTestId('returnTo')).textContent).toBe('/admin')
    unmount()
  })

  it('redirects to /403 when authenticated but Auth0 claim does not include ADMIN', async () => {
    mockUseAuth.mockReturnValue({
      user: { id: 'u1' },
      isAuthenticated: true,
      isAdmin: false,
      isLoading: false,
    })
    renderInRouter()
    expect(await screen.findByRole('heading', { name: 'Forbidden' })).toBeTruthy()
    expect(screen.queryByText('Admin Dashboard')).toBeNull()
  })

  it('renders children when authenticated and Auth0 claim includes ADMIN', async () => {
    mockUseAuth.mockReturnValue({
      user: { id: 'u1' },
      isAuthenticated: true,
      isAdmin: true,
      isLoading: false,
    })
    renderInRouter()
    expect(await screen.findByRole('heading', { name: 'Admin Dashboard' })).toBeTruthy()
  })
})

function LocationState() {
  const { state } = useLocation()
  return <span data-testid="returnTo">{(state as { returnTo?: string } | null)?.returnTo}</span>
}
