// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Navbar from '@/components/Navbar'
import { ThemeProvider } from '@/contexts/ThemeContext'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

import { useAuth } from '@/hooks/useAuth'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  mockNavigate.mockReset()
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

  it('shows Se connecter button when no user', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn(), login: vi.fn() })
    renderNavbar()
    expect(screen.getByRole('button', { name: 'Se connecter' })).toBeTruthy()
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
    expect(screen.getByText('Mon profil')).toBeTruthy()
    fireEvent.click(screen.getByText('Mon profil'))
    expect(screen.queryByText('Déconnexion')).toBeNull()
  })

  it('closes dropdown when clicking outside', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Menu utilisateur'))
    expect(screen.getByText('Déconnexion')).toBeTruthy()
    fireEvent.mouseDown(document.body)
    expect(screen.queryByText('Déconnexion')).toBeNull()
  })

  it('opens mobile menu on hamburger click', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn(), login: vi.fn() })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Ouvrir le menu'))
    expect(screen.getByLabelText('Fermer le menu')).toBeTruthy()
  })

  it('closes mobile menu on second hamburger click', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn(), login: vi.fn() })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Ouvrir le menu'))
    fireEvent.click(screen.getByLabelText('Fermer le menu'))
    expect(screen.getByLabelText('Ouvrir le menu')).toBeTruthy()
  })

  it('shows Se connecter in mobile menu when unauthenticated', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn(), login: vi.fn() })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Ouvrir le menu'))
    const buttons = screen.getAllByRole('button', { name: 'Se connecter' })
    expect(buttons.length).toBeGreaterThan(0)
  })

  it('shows user info and logout in mobile menu when authenticated', () => {
    const logout = vi.fn()
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout,
      login: vi.fn(),
    })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Ouvrir le menu'))
    const deconnexionBtns = screen.getAllByText('Déconnexion')
    expect(deconnexionBtns.length).toBeGreaterThan(0)
  })

  it('scrolls to section when nav link clicked on home page', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn(), login: vi.fn() })
    const mockScrollIntoView = vi.fn()
    vi.spyOn(document, 'getElementById').mockReturnValue({ scrollIntoView: mockScrollIntoView } as unknown as HTMLElement)

    render(
      <MemoryRouter initialEntries={['/']}>
        <ThemeProvider>
          <Navbar />
        </ThemeProvider>
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByText('En ce moment'))
    expect(document.getElementById).toHaveBeenCalledWith('events')
    expect(mockScrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth' })
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('navigates to home with hash when nav link clicked from another route', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn(), login: vi.fn() })

    render(
      <MemoryRouter initialEntries={['/events/123']}>
        <ThemeProvider>
          <Navbar />
        </ThemeProvider>
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByText('En ce moment'))
    expect(mockNavigate).toHaveBeenCalledWith('/#events')
  })

  it('calls logout from mobile menu', () => {
    const logout = vi.fn()
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout,
      login: vi.fn(),
    })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Ouvrir le menu'))
    const deconnexionBtns = screen.getAllByText('Déconnexion')
    fireEvent.click(deconnexionBtns.at(-1)!)
    expect(logout).toHaveBeenCalled()
  })
})
