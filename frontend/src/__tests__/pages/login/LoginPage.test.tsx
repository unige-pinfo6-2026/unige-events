
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
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
    const { container } = render(<LoginPage />)
    expect(container.querySelector('.animate-spin')).toBeTruthy()
  })

  it('shows the redirect message', () => {
    const login = vi.fn()
    mockUseAuth.mockReturnValue({ login })
    render(<LoginPage />)
    expect(screen.getByText('Redirection vers la page de connexion...')).toBeTruthy()
  })

  it('calls login on mount', () => {
    const login = vi.fn()
    mockUseAuth.mockReturnValue({ login })
    render(<LoginPage />)
    expect(login).toHaveBeenCalledOnce()
  })
})
