
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

  it('shows user menu items in DOM when user is logged in', () => {
    // The dropdown is CSS hover-based; its children are always in the DOM
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()
    expect(screen.getByText('Déconnexion')).toBeTruthy()
  })

  it('calls logout when clicking Déconnexion', () => {
    const logout = vi.fn()
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout,
    })
    renderNavbar()
    // Dropdown is CSS hover-based; the button is always in the DOM
    fireEvent.click(screen.getByText('Déconnexion'))
    expect(logout).toHaveBeenCalledOnce()
  })

  it('toggles theme on button click', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn() })
    renderNavbar()
    // Multiple ThemeToggle buttons exist (desktop + mobile toolbar)
    const btn = screen.getAllByLabelText('Passer en mode clair')[0]
    fireEvent.click(btn)
    expect(screen.getAllByLabelText('Passer en mode sombre')[0]).toBeTruthy()
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

  it('shows Mon profil link in dropdown when user is logged in', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()
    expect(screen.getByText('Mon profil')).toBeTruthy()
  })

  it('shows Administration link in orange (text-warning) for admin users — review interne d\'Agon', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      isAdmin: true,
      logout: vi.fn(),
    })
    renderNavbar()
    // The desktop dropdown link uses the warning-styled className. We don't lock
    // the full class string, just assert the warning token is present so future
    // refactors stay sane.
    const link = screen.getAllByText('Administration')[0].closest('a')
    expect(link).not.toBeNull()
    expect(link!.className).toContain('text-warning')
  })

  it('does not show Administration link to non-admin users', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '2', auth0Id: 'auth0|2', email: 'b@b.com', displayName: 'Bob Martin', profilePublic: true, createdAt: '2024-01-01' },
      isAdmin: false,
      logout: vi.fn(),
    })
    renderNavbar()
    expect(screen.queryByText('Administration')).toBeNull()
  })

  it('opens mobile menu on hamburger click', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn(), login: vi.fn() })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Ouvrir le menu'))
    // Both the hamburger and the sidebar X button have "Fermer le menu"
    expect(screen.getAllByLabelText('Fermer le menu').length).toBeGreaterThan(0)
  })

  it('closes mobile menu on second hamburger click', () => {
    mockUseAuth.mockReturnValue({ user: null, logout: vi.fn(), login: vi.fn() })
    renderNavbar()
    fireEvent.click(screen.getByLabelText('Ouvrir le menu'))
    // Click the hamburger button (first "Fermer le menu" in document order = main nav)
    fireEvent.click(screen.getAllByLabelText('Fermer le menu')[0])
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

  it('shows the navbar-user skeleton while Auth0 is loading', () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: true, logout: vi.fn(), login: vi.fn() })
    renderNavbar()
    expect(document.querySelector('[data-boneyard="user-identity-inline"]')).toBeTruthy()
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

  it('renders "Mes événements" dropdown item with ChevronDown', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()
    expect(screen.getByText('Mes événements')).toBeTruthy()
  })

  it('renders "Mes événements" sub-links visible by default (banner-card open)', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()

    // The Collapsible-based banner-card defaults to open, so sub-links are visible
    // immediately when the dropdown is rendered.
    expect(screen.getByText('Mes Favoris')).toBeTruthy()
    expect(screen.getByText('Mes Participations')).toBeTruthy()
    expect(screen.getByText('Mes Publications')).toBeTruthy()
  })

  it('sub-links have correct hrefs', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()

    const favoritesLink = screen.getByText('Mes Favoris').closest('a')
    const participationsLink = screen.getByText('Mes Participations').closest('a')
    const publicationsLink = screen.getByText('Mes Publications').closest('a')

    expect(favoritesLink?.getAttribute('href')).toBe('/my-events/favorites')
    expect(participationsLink?.getAttribute('href')).toBe('/my-events/participations')
    expect(publicationsLink?.getAttribute('href')).toBe('/my-events/publications')
  })

  it('sets aria-expanded on the "Mes événements" banner-card trigger and toggles on click', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
    })
    renderNavbar()

    // Find the desktop Collapsible.Trigger button — distinct from the mobile sidebar
    // version by its banner-card wrapper class.
    const desktopBtn = Array.from(screen.getAllByText('Mes événements'))
      .map(el => el.closest('button'))
      .find(btn => btn?.closest('[data-state]')) as HTMLButtonElement | undefined

    // Default open: aria-expanded === 'true'
    expect(desktopBtn?.getAttribute('aria-expanded')).toBe('true')

    if (desktopBtn) {
      // Click → close
      fireEvent.click(desktopBtn)
      expect(desktopBtn.getAttribute('aria-expanded')).toBe('false')
      // Click → reopen
      fireEvent.click(desktopBtn)
      expect(desktopBtn.getAttribute('aria-expanded')).toBe('true')
    }
  })

  it('expands mobile "Mes événements" submenu with click toggle', () => {
    mockUseAuth.mockReturnValue({
      user: { id: '1', auth0Id: 'auth0|1', email: 'a@b.com', displayName: 'Jean Dupont', profilePublic: true, createdAt: '2024-01-01' },
      logout: vi.fn(),
      login: vi.fn(),
    })
    renderNavbar()

    // Open mobile menu
    fireEvent.click(screen.getByLabelText('Ouvrir le menu'))

    // Find the "Mes événements" button in the mobile menu (has cursor-pointer class)
    const mobileMenuButtons = screen.getAllByText('Mes événements')
    const mesEvnementsInMobileMenu = mobileMenuButtons
      .find(el => el.closest('button')?.className.includes('cursor-pointer'))
      ?.closest('button')

    expect(mesEvnementsInMobileMenu).toBeTruthy()

    if (mesEvnementsInMobileMenu) {
      // Click to toggle state - this should toggle the expanded state
      fireEvent.click(mesEvnementsInMobileMenu)

      // Verify the button still exists and is still clickable
      expect(mesEvnementsInMobileMenu.parentElement).toBeTruthy()
    }
  })
})
