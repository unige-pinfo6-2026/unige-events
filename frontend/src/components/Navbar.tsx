import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { useTheme } from '../contexts/ThemeContext'
import Avatar from './Avatar'
import Logo from './Logo'
import './Navbar.css'

function Navbar() {
  const { user, logout } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const [dropdownOpen, setDropdownOpen] = useState(false)
  const dropdownRef = useRef<HTMLDivElement>(null)

  function handleDropdownToggle() {
    setDropdownOpen((prev) => !prev)
  }

  function handleLogout() {
    setDropdownOpen(false)
    logout()
  }

  return (
    <nav className="navbar">
      <div className="navbar-brand">
        <Link to="/home" className="navbar-title">
          <Logo size={28} className="navbar-logo" />
          UNIGE Events
        </Link>
      </div>

      <div className="navbar-links">
        <Link to="/home" className="navbar-link">
          Accueil
        </Link>
      </div>

      <div className="navbar-user" ref={dropdownRef}>
        <Link
          to="/search"
          className="navbar-theme-toggle"
          aria-label="Rechercher"
        >
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
            <circle cx="11" cy="11" r="8"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>
          </svg>
        </Link>

        <button
          className="navbar-theme-toggle"
          onClick={toggleTheme}
          aria-label={theme === 'dark' ? 'Passer en mode clair' : 'Passer en mode sombre'}
        >
          {theme === 'dark' ? (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="12" cy="12" r="5"/><line x1="12" y1="1" x2="12" y2="3"/><line x1="12" y1="21" x2="12" y2="23"/><line x1="4.22" y1="4.22" x2="5.64" y2="5.64"/><line x1="18.36" y1="18.36" x2="19.78" y2="19.78"/><line x1="1" y1="12" x2="3" y2="12"/><line x1="21" y1="12" x2="23" y2="12"/><line x1="4.22" y1="19.78" x2="5.64" y2="18.36"/><line x1="18.36" y1="5.64" x2="19.78" y2="4.22"/>
            </svg>
          ) : (
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
            </svg>
          )}
        </button>

        <button
          className="navbar-avatar"
          onClick={handleDropdownToggle}
          aria-label="Menu utilisateur"
          aria-expanded={dropdownOpen}
        >
          <Avatar
            avatarUrl={user?.avatarUrl}
            displayName={user?.displayName}
            size={32}
            style={!user?.avatarUrl ? { background: 'rgba(255,255,255,0.2)', border: '2px solid rgba(255,255,255,0.28)', fontSize: '0.8rem' } : undefined}
          />
          <span className="navbar-username">{user?.displayName ?? ''}</span>
          <span className="dropdown-caret">▾</span>
        </button>

        {dropdownOpen && (
          <div className="navbar-dropdown">
            <Link
              to="/profile/me"
              className="dropdown-item"
              onClick={() => setDropdownOpen(false)}
            >
              Mon Profil
            </Link>
            <button className="dropdown-item dropdown-item--danger" onClick={handleLogout}>
              Déconnexion
            </button>
          </div>
        )}
      </div>
    </nav>
  )
}

export default Navbar
