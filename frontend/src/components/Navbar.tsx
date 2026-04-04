import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { useTheme } from '../contexts/ThemeContext'
import UserAvatar from './user/UserAvatar'
import { ButtonPrimary } from './utils/Buttons'
import { ChevronDown, Menu, Moon, Sun, X } from 'lucide-react'
import { Banner } from '@/assets/Banner'
import { TextLink } from './utils/Links'

export default function Navbar() {
  const { user, login, logout } = useAuth()
  const { theme, toggleTheme } = useTheme()
  
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const [dropdownOpen, setDropdownOpen] = useState(false)

  return (
    <nav className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div className="flex justify-between items-center h-navbar">
        {/* Logo */}
        <Banner className="size-40"/>

        {/* Desktop Nav */}
        <div className="hidden md:flex items-center gap-8">
          <TextLink href={"/#events"}>En ce moment</TextLink>
          <TextLink href={"/#features"}>Fonctionnalités</TextLink>
          <TextLink href={"/#faq"}>FAQ</TextLink>
          <TextLink href={"/#get-started"}>Commencer</TextLink>
        </div>

        {/* Right side */}
        <div className="hidden md:flex items-center gap-3">
          {/* Theme toggle */}
          <button
            onClick={toggleTheme}
            aria-label={theme === 'dark' ? 'Passer en mode clair' : 'Passer en mode sombre'}
            className="p-2 rounded-lg hover:bg-white/5 transition-colors text-foreground cursor-pointer bg-transparent border-0"
          >
            {theme === 'dark' ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
          </button>

          {user ? (
            <div className="relative">
              <button
                onClick={() => setDropdownOpen(p => !p)}
                className="flex items-center gap-2 cursor-pointer bg-transparent border-0 text-foreground"
                aria-expanded={dropdownOpen}
              >
                <UserAvatar
                  user={user}
                  size={32}
                />
                <span className="text-sm text-foreground/80">{user.displayName}</span>
                <ChevronDown className="w-4 h-4 text-foreground/50" />
              </button>

              {dropdownOpen && (
                <div className="absolute right-0 mt-2 w-48 rounded-xl bg-card border border-border shadow-xl overflow-hidden z-50">
                  <Link
                    to="/profile/me"
                    className="block px-4 py-3 text-sm text-foreground/70 hover:text-foreground hover:bg-white/5 transition-colors"
                    onClick={() => setDropdownOpen(false)}
                  >
                    Mon Profil
                  </Link>
                  <button
                    onClick={() => { setDropdownOpen(false); logout() }}
                    className="w-full text-left px-4 py-3 text-sm text-red-400 hover:text-red-300 hover:bg-white/5 transition-colors cursor-pointer bg-transparent border-0"
                  >
                    Déconnexion
                  </button>
                </div>
              )}
            </div>
          ) : (
            <ButtonPrimary size="sm" onClick={login}>Se connecter</ButtonPrimary>
          )}
        </div>

        {/* Mobile toggle */}
        <button
          onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
          className="md:hidden p-2 rounded-lg hover:bg-white/5 transition-colors text-foreground cursor-pointer bg-transparent border-0"
        >
          {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
      </div>

      {/* Mobile menu */}
      {mobileMenuOpen && (
        <div className="md:hidden py-4 space-y-3 border-t border-white/10 mt-2">
          <div className="pt-3 space-y-2">
            {user ? (
              <>
                <Link to="/profile/me" className="block py-2 text-sm text-foreground/70 hover:text-foreground transition-colors">
                  Mon Profil
                </Link>
                <button onClick={logout} className="block py-2 text-sm text-red-400 cursor-pointer bg-transparent border-0">
                  Déconnexion
                </button>
              </>
            ) : (
              <ButtonPrimary size="sm" onClick={login}>
                Se connecter
              </ButtonPrimary>
            )}
          </div>
        </div>
      )}
    </nav>
  )
}