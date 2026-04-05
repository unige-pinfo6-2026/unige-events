import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { useTheme } from '../contexts/ThemeContext'
import UserIdentity from './user/UserIdentity'
import { ButtonPrimary } from './utils/Buttons'
import { TextLink } from './utils/Links'
import { Banner } from '@/assets/Banner'
import { ChevronDown, Menu, Moon, Sun, X } from 'lucide-react'
import type { User } from '@/types/user'

const navLinks = [
  { href: '/#events', label: 'En ce moment' },
  { href: '/#features', label: 'Fonctionnalités' },
  { href: '/#faq', label: 'FAQ' },
  { href: '/#get-started', label: 'Commencer' },
]

const userMenuSections = [
  [{ label: 'Mon profil', to: '/profile/me' }],
  [
    { label: 'Créer un événement', to: '/events/new' },
    { label: 'Mes événements', to: '/events' },
  ],
  [{ label: 'Calendrier', to: '/calendar' }],
]

function UserDropdown({ user, logout }: Readonly<{ user: User; logout: () => void }>) {
  const [open, setOpen] = useState(false)
  const ref = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    function handleClickOutside(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen(p => !p)}
        aria-expanded={open}
        className="flex items-center gap-2 cursor-pointer bg-transparent border-0 text-foreground"
      >
        <UserIdentity user={user} />
        <ChevronDown className={`w-4 h-4 text-foreground/50 transition-transform duration-200 ${open ? 'rotate-180' : ''}`} />
      </button>

      {open && (
        <div className="absolute right-0 mt-2 w-52 rounded-xl bg-background border border-border shadow-xl overflow-hidden z-50">
          {userMenuSections.map((section, i) => (
            <div key={section[0].to}>
              {i > 0 && <div className="border-t border-border" />}
              {section.map(item => (
                <Link
                  key={item.to}
                  to={item.to}
                  onClick={() => setOpen(false)}
                  className="block px-4 py-3 text-sm text-foreground/70 hover:text-foreground hover:bg-foreground/5 transition-colors"
                >
                  {item.label}
                </Link>
              ))}
            </div>
          ))}
          <div className="border-t border-border" />
          <button
            type="button"
            onClick={() => { setOpen(false); logout() }}
            className="w-full text-left px-4 py-3 text-sm text-error hover:bg-foreground/5 transition-colors cursor-pointer bg-transparent border-0"
          >
            Déconnexion
          </button>
        </div>
      )}
    </div>
  )
}

function DesktopNav({ user, login, logout, theme, toggleTheme }: Readonly<{
  user: User | null
  login: () => void
  logout: () => void
  theme: string
  toggleTheme: () => void
}>) {
  return (
    <>
      <div className="hidden lg:flex items-center gap-8">
        {navLinks.map(link => (
          <TextLink key={link.href} href={link.href}>{link.label}</TextLink>
        ))}
      </div>

      <div className="hidden lg:flex items-center gap-2">
        <button
          type="button"
          onClick={toggleTheme}
          aria-label={theme === 'dark' ? 'Passer en mode clair' : 'Passer en mode sombre'}
          className="p-2 rounded-lg hover:bg-foreground/5 transition-colors text-foreground cursor-pointer bg-transparent border-0"
        >
          {theme === 'dark' ? <Sun className="size-6" /> : <Moon className="size-6" />}
        </button>

        {user
          ? <UserDropdown user={user} logout={logout} />
          : <ButtonPrimary size="sm" onClick={login}>Se connecter</ButtonPrimary>
        }
      </div>
    </>
  )
}

function MobileMenu({ user, login, logout, onClose }: Readonly<{
  user: User | null
  login: () => void
  logout: () => void
  onClose: () => void
}>) {
  return (
    <div className="lg:hidden flex flex-col border-t border-border">
      <div className="flex flex-col px-6 py-4">
        {navLinks.map(link => (
          <a key={link.href} href={link.href} onClick={onClose} className="py-2 text-sm text-foreground/70 hover:text-foreground transition-colors">
            {link.label}
          </a>
        ))}
      </div>

      <div className="flex flex-col p-6 border-t border-border">
        {user ? (
          <>
            <UserIdentity user={user} />

            <div className="border-t border-border my-2" />

            {userMenuSections.map((section, i) => (
              <div key={section[0].to} className="flex flex-col">
                {i > 0 && <div className="border-t border-border my-2" />}
                {section.map(item => (
                  <Link
                    key={item.to}
                    to={item.to}
                    onClick={onClose}
                    className="py-1.5 text-sm text-foreground/70 hover:text-foreground transition-colors"
                  >
                    {item.label}
                  </Link>
                ))}
              </div>
            ))}

            <div className="border-t border-border my-2" />
            <button
              type="button"
              onClick={() => { onClose(); logout() }}
              className="text-left py-1.5 text-sm text-error cursor-pointer bg-transparent border-0"
            >
              Déconnexion
            </button>
          </>
        ) : (
          <div>
            <ButtonPrimary size="sm" onClick={login}>Se connecter</ButtonPrimary>
          </div>
        )}
      </div>
    </div>
  )
}

export default function Navbar() {
  const { user, login, logout } = useAuth()
  const { theme, toggleTheme } = useTheme()
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  return (
    <nav className="max-w-7xl mx-auto">
      <div className="flex justify-between items-center h-navbar px-6">
        <Banner className="size-40" />

        <DesktopNav user={user} login={login} logout={logout} theme={theme} toggleTheme={toggleTheme} />

        <button
          type="button"
          onClick={() => setMobileMenuOpen(p => !p)}
          aria-label={mobileMenuOpen ? 'Fermer le menu' : 'Ouvrir le menu'}
          className="lg:hidden p-2 rounded-lg hover:bg-foreground/5 transition-colors text-foreground cursor-pointer bg-transparent border-0"
        >
          {mobileMenuOpen ? <X className="size-6" /> : <Menu className="size-6" />}
        </button>
      </div>

      {mobileMenuOpen && (
        <MobileMenu user={user} login={login} logout={logout} onClose={() => setMobileMenuOpen(false)} />
      )}
    </nav>
  )
}
