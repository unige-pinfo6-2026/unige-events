
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import LoginPage from '@/pages/login/LoginPage'

vi.mock('@/hooks', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '@/hooks'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('LoginPage', () => {
  it('renders the loading spinner', () => {
    const login = vi.fn()
    mockUseAuth.mockReturnValue({ login })
    const { container } = render(<MemoryRouter><LoginPage /></MemoryRouter>)
    expect(container.querySelector('.animate-spin')).toBeTruthy()
  })

  it('shows the redirect message', () => {
    const login = vi.fn()
    mockUseAuth.mockReturnValue({ login })
    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    expect(screen.getByText('Redirection vers la page de connexion...')).toBeTruthy()
  })

  it('calls login with returnTo from location state when present', () => {
    const login = vi.fn()
    mockUseAuth.mockReturnValue({ login })
    render(
      <MemoryRouter initialEntries={[{ pathname: '/login', state: { returnTo: '/events/new' } }]}>
        <LoginPage />
      </MemoryRouter>,
    )
    expect(login).toHaveBeenCalledOnce()
    expect(login).toHaveBeenCalledWith('/events/new')
  })

  it('calls login with undefined when location state has no returnTo', () => {
    const login = vi.fn()
    mockUseAuth.mockReturnValue({ login })
    render(<MemoryRouter><LoginPage /></MemoryRouter>)
    expect(login).toHaveBeenCalledOnce()
    expect(login).toHaveBeenCalledWith(undefined)
  })
})
