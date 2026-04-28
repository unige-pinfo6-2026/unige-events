
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import LoginCallbackPage from '@/pages/login/callback/LoginCallbackPage'

vi.mock('@auth0/auth0-react', () => ({
  useAuth0: vi.fn(),
}))

import { useAuth0 } from '@auth0/auth0-react'

const mockUseAuth0 = useAuth0 as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('CallbackPage', () => {
  it('shows loading spinner when no error', () => {
    mockUseAuth0.mockReturnValue({ error: undefined, isLoading: true, isAuthenticated: false })
    render(<LoginCallbackPage />)
    expect(screen.getByText('Connexion en cours...')).toBeTruthy()
  })

  it('shows error message when auth fails', () => {
    mockUseAuth0.mockReturnValue({ error: new Error('Auth failed') })
    render(<LoginCallbackPage />)
    expect(screen.getByText(/Erreur d'authentification/)).toBeTruthy()
    expect(screen.getByText(/Auth failed/)).toBeTruthy()
  })

  it('shows nothing after auth resolves while waiting for onRedirectCallback', () => {
    mockUseAuth0.mockReturnValue({ isAuthenticated: true, isLoading: false, error: undefined })
    render(<LoginCallbackPage />)
    expect(screen.queryByText('Connexion en cours...')).toBeNull()
    expect(screen.queryByText(/Erreur/)).toBeNull()
  })
})
