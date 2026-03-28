// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Navbar from '../../components/Navbar'
import { ThemeProvider } from '../../contexts/ThemeContext'

vi.mock('../../hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '../../hooks/useAuth'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderNavbar() {
  return render(
    <MemoryRouter>
      <ThemeProvider>
        <Navbar />
      </ThemeProvider>
    </MemoryRouter>,
  )
}

describe('Navbar', () => {
  it('shows user initials when no photo', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()
    expect(screen.getByText('JD')).toBeTruthy()
  })

  it('shows empty avatar when no user', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn() })
    renderNavbar()
    expect(screen.getByLabelText('Menu utilisateur')).toBeTruthy()
  })

  it('opens dropdown on avatar click', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Menu utilisateur'))
    expect(screen.getByText('Déconnexion')).toBeTruthy()
  })

  it('calls logout when clicking Déconnexion', () => {
    const logout = vi.fn()
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout,
    })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Menu utilisateur'))
    fireEvent.click(screen.getByText('Déconnexion'))
    expect(logout).toHaveBeenCalledOnce()
  })

  it('toggles theme on button click', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn() })
    renderNavbar()
    const btn = screen.getByLabelText('Passer en mode clair')
    fireEvent.click(btn)
    expect(screen.getByLabelText('Passer en mode sombre')).toBeTruthy()
  })

  it('shows avatar image when user has avatarUrl', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', avatarUrl: 'https://example.com/photo.jpg', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()
    const img = screen.getByAltText('Jean Dupont')
    expect(img).toBeTruthy()
  })

  it('closes dropdown when Mon Profil is clicked', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Menu utilisateur'))
    expect(screen.getByText('Mon Profil')).toBeTruthy()
    fireEvent.click(screen.getByText('Mon Profil'))
    expect(screen.queryByText('Déconnexion')).toBeNull()
  })
})
