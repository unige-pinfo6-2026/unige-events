// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, render, screen, waitFor } from '@testing-library/react'
import { AuthProvider, AuthContext } from '@/contexts/AuthContext'
import { ToastProvider } from '@/contexts/ToastContext'
import { useContext } from 'react'

vi.mock('@auth0/auth0-react', () => ({ useAuth0: vi.fn() }))
vi.mock('@/services/userService', () => ({ getMe: vi.fn() }))
vi.mock('@/services/tokenStore', () => ({ setToken: vi.fn() }))

import { useAuth0 } from '@auth0/auth0-react'
import { getMe } from '@/services/userService'
import { setToken } from '@/services/tokenStore'

const mockUseAuth0 = useAuth0 as ReturnType<typeof vi.fn>
const mockGetMe = getMe as ReturnType<typeof vi.fn>
const mockSetToken = setToken as ReturnType<typeof vi.fn>

const mockUser = {
  id: 'user-1',
  auth0Id: 'auth0|1',
  email: 'test@example.com',
  displayName: 'Jean Dupont',
  profilePublic: true,
  createdAt: '2024-01-01',
}

function TestConsumer() {
  const ctx = useContext(AuthContext)
  return (
    <div>
      <span data-testid="user">{ctx?.user?.displayName ?? 'null'}</span>
      <span data-testid="loading">{String(ctx?.isLoading)}</span>
      <button onClick={() => ctx?.login()}>login</button>
      <button onClick={() => ctx?.logout()}>logout</button>
      <button onClick={() => ctx?.updateUser({ ...mockUser, displayName: 'Updated' })}>update</button>
    </div>
  )
}

function renderProvider() {
  return render(
    <ToastProvider>
      <AuthProvider>
        <TestConsumer />
      </AuthProvider>
    </ToastProvider>,
  )
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('AuthContext', () => {
  it('calls loginWithRedirect when login is invoked', () => {
    const loginWithRedirect = vi.fn()
    mockUseAuth0.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      loginWithRedirect,
      logout: vi.fn(),
      getAccessTokenSilently: vi.fn(),
    })
    renderProvider()
    act(() => {
      screen.getByText('login').click()
    })
    expect(loginWithRedirect).toHaveBeenCalledOnce()
  })

  it('calls setToken(null) and auth0Logout when logout is invoked', () => {
    const auth0Logout = vi.fn()
    mockUseAuth0.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      loginWithRedirect: vi.fn(),
      logout: auth0Logout,
      getAccessTokenSilently: vi.fn(),
    })
    renderProvider()
    act(() => {
      screen.getByText('logout').click()
    })
    expect(mockSetToken).toHaveBeenCalledWith(null)
    expect(auth0Logout).toHaveBeenCalledOnce()
  })

  it('updates user when updateUser is called', async () => {
    mockUseAuth0.mockReturnValue({
      isAuthenticated: false,
      isLoading: false,
      user: null,
      loginWithRedirect: vi.fn(),
      logout: vi.fn(),
      getAccessTokenSilently: vi.fn(),
    })
    renderProvider()
    act(() => {
      screen.getByText('update').click()
    })
    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('Updated'))
  })

  it('fetches token and user on successful auth', async () => {
    mockUseAuth0.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { sub: 'auth0|1' },
      loginWithRedirect: vi.fn(),
      logout: vi.fn(),
      getAccessTokenSilently: vi.fn().mockResolvedValue('token-abc'),
    })
    mockGetMe.mockResolvedValue(mockUser)
    renderProvider()
    await waitFor(() => expect(screen.getByTestId('user').textContent).toBe('Jean Dupont'))
    expect(mockSetToken).toHaveBeenCalledWith('token-abc')
  })

  it('calls auth0Logout on 401 error from backend', async () => {
    const auth0Logout = vi.fn()
    mockUseAuth0.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { sub: 'auth0|1' },
      loginWithRedirect: vi.fn(),
      logout: auth0Logout,
      getAccessTokenSilently: vi.fn().mockResolvedValue('bad-token'),
    })
    mockGetMe.mockRejectedValue({ response: { status: 401 } })
    renderProvider()
    await waitFor(() => expect(auth0Logout).toHaveBeenCalledOnce())
    expect(mockSetToken).toHaveBeenCalledWith(null)
  })

  it('leaves user null on non-401 errors', async () => {
    mockUseAuth0.mockReturnValue({
      isAuthenticated: true,
      isLoading: false,
      user: { sub: 'auth0|1' },
      loginWithRedirect: vi.fn(),
      logout: vi.fn(),
      getAccessTokenSilently: vi.fn().mockResolvedValue('token'),
    })
    mockGetMe.mockRejectedValue({ response: { status: 500 } })
    renderProvider()
    await waitFor(() => expect(screen.getByTestId('loading').textContent).toBe('false'))
    expect(screen.getByTestId('user').textContent).toBe('null')
  })
})
