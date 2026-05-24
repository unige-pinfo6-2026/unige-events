// Dedicated file for the module-load-time ROLES_CLAIM const (AuthContext.tsx
// line 25): `VITE_AUTH0_ROLES_CLAIM ?? 'https://unige-events/roles'`.
//
// The project `.env` defines VITE_AUTH0_ROLES_CLAIM, so during the normal test
// suite the left operand is always truthy and the `?? default` fallback (right
// operand) is never taken. To exercise the fallback we must make the env var
// undefined BEFORE the (hoisted) module loads — done here with `vi.stubEnv` in
// a `beforeAll`, in a file that NEVER statically imports AuthContext so the
// only instance v8 instruments is the stubbed one.

import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { useContext } from 'react'

vi.mock('@auth0/auth0-react', () => ({ useAuth0: vi.fn() }))
vi.mock('@/services/userService', () => ({ getMe: vi.fn() }))
vi.mock('@/services/tokenStore', () => ({ setToken: vi.fn() }))

const mockUser = {
  id: 'user-1',
  auth0Id: 'auth0|1',
  email: 'test@example.com',
  username: 'jean.dupont',
  displayName: 'Jean Dupont',
  profilePublic: true,
  createdAt: '2024-01-01',
}

const DEFAULT_NS = 'https://unige-events/roles'

// Unset the env var before the first import of the module under test so the
// `?? DEFAULT_NS` fallback branch runs at module-load time.
beforeAll(() => {
  vi.stubEnv('VITE_AUTH0_ROLES_CLAIM', undefined as unknown as string)
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('AuthContext — ROLES_CLAIM default fallback (VITE_AUTH0_ROLES_CLAIM unset)', () => {
  it('falls back to the default claim namespace and reads ADMIN from it', async () => {
    const { useAuth0 } = await import('@auth0/auth0-react')
    const { getMe } = await import('@/services/userService')
    const { ToastProvider } = await import('@/contexts/ToastContext')
    const { AuthProvider, AuthContext } = await import('@/contexts/AuthContext')

    vi.mocked(useAuth0).mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      // Role under the DEFAULT namespace — only resolves to ADMIN if the
      // fallback was used for ROLES_CLAIM.
      user: { sub: 'auth0|admin', [DEFAULT_NS]: ['ADMIN'] },
      loginWithRedirect: vi.fn(),
      logout: vi.fn(),
      getAccessTokenSilently: vi.fn().mockResolvedValue('token'),
    } as unknown as ReturnType<typeof useAuth0>)
    vi.mocked(getMe).mockResolvedValue(mockUser)

    function Consumer() {
      const ctx = useContext(AuthContext)
      return <span data-testid="isAdmin">{String(ctx?.isAdmin)}</span>
    }

    render(
      <MemoryRouter>
        <ToastProvider>
          <AuthProvider>
            <Consumer />
          </AuthProvider>
        </ToastProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByTestId('isAdmin').textContent).toBe('true'))
  })

  it('is not ADMIN when no role lives under the default namespace', async () => {
    const { useAuth0 } = await import('@auth0/auth0-react')
    const { getMe } = await import('@/services/userService')
    const { ToastProvider } = await import('@/contexts/ToastContext')
    const { AuthProvider, AuthContext } = await import('@/contexts/AuthContext')

    vi.mocked(useAuth0).mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { sub: 'auth0|user' },
      loginWithRedirect: vi.fn(),
      logout: vi.fn(),
      getAccessTokenSilently: vi.fn().mockResolvedValue('token'),
    } as unknown as ReturnType<typeof useAuth0>)
    vi.mocked(getMe).mockResolvedValue(mockUser)

    function Consumer() {
      const ctx = useContext(AuthContext)
      return (
        <>
          <span data-testid="user">{ctx?.user?.displayName ?? 'null'}</span>
          <span data-testid="isAdmin">{String(ctx?.isAdmin)}</span>
        </>
      )
    }

    render(
      <MemoryRouter>
        <ToastProvider>
          <AuthProvider>
            <Consumer />
          </AuthProvider>
        </ToastProvider>
      </MemoryRouter>,
    )

    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('Jean Dupont'))
    expect(screen.getByTestId('isAdmin').textContent).toBe('false')
  })
})
