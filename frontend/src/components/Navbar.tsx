import { useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import { useTheme } from '@/contexts/ThemeContext'
import UserIdentity from '@/components/user/UserIdentity'
import { ButtonPrimary } from '@/components/utils/Buttons'
import { Skeleton } from '@/components/utils/Skeleton'
import { Dropdown } from '@/components/utils/Dropdown'
import { ActionLink } from '@/components/utils/Links'
import { Banner } from '@/assets/Banner'
import { Calendar, ChevronDown, Heart, LayoutDashboard, LogOut, Menu, Moon, Search, Shield, SquarePlus, Sun, Ticket, User, X } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { User as UserType } from '@/types/user'

type NavItem = { to: string; icon: LucideIcon; label: string; adminOnly?: boolean, subLinks?: NavItem[] }

const navLinks: NavItem[] = [
  { label: 'Événements', to: '/events', icon: Ticket },
  { label: 'Calendrier', to: '/calendar', icon: Calendar },
]

const actionButtons: NavItem[] = [
  { label: 'Rechercher un événement', to: '/events/search', icon: Search },
  { label: 'Créer un événement', to: '/events/new', icon: SquarePlus },
]

const userMenuItems: NavItem[] = [
  { label: 'Mon profil', to: '/profile/me', icon: User },
  { label: 'Mes événements', to: '/events/me', icon: LayoutDashboard },
  { label: 'Mes favoris', to: '/events/favorites', icon: Heart },
  { label: 'Administration', to: '/admin', icon: Shield, adminOnly: true },
]

const dropdownItemClass = 'flex items-center gap-3 px-4 py-3 text-sm text-foreground/70 hover:text-foreground hover:bg-foreground/5 transition-colors'

const sidebarItemClass = (isActive = false) =>
  `flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
    isActive ? 'bg-accent/10 text-accent' : 'text-foreground/60 hover:text-foreground hover:bg-foreground/5'
  }`

// ─── Desktop ──────────────────────────────────────────────────────────────────

function DesktopNavItem({ link }: Readonly<{ link: NavItem }>) {
  const Icon = link.icon
  const linkClass = ({ isActive }: { isActive: boolean }) =>
    `flex items-center gap-2 text-base transition-colors ${isActive ? 'text-foreground font-medium' : 'text-foreground/70 hover:text-foreground'}`

  const trigger = (
    <NavLink to={link.to} end={!!link.subLinks} className={linkClass}>
      <Icon className="size-4 shrink-0" />
      {link.label}
    </NavLink>
  )

  if (!link.subLinks) {
    return trigger
  }

  return (
    <Dropdown trigger={trigger}>
      {link.subLinks.map(sub => (
        <Link key={sub.to} to={sub.to} className={dropdownItemClass}>{sub.label}</Link>
      ))}
    </Dropdown>
  )
}

function NavActions() {
  const { theme, toggleTheme } = useTheme()

  return (
    <div className="flex items-center gap-1">
      {actionButtons.map(({ to, icon, label }) => (
        <ActionLink key={to} to={to} icon={icon} label={label} />
      ))}
      <button
        type="button"
        onClick={toggleTheme}
        aria-label={theme === 'dark' ? 'Passer en mode clair' : 'Passer en mode sombre'}
        className="p-2 rounded-lg hover:bg-foreground/5 transition-colors text-foreground cursor-pointer bg-transparent border-0"
      >
        {theme === 'dark' ? <Sun className="size-5" /> : <Moon className="size-5" />}
      </button>
    </div>
  )
}

function UserDropdownMenu({ user, logout }: Readonly<{ user: UserType; logout: () => void }>) {
  return (
    <Dropdown align="right" trigger={<UserIdentity user={user} />}>
      {userMenuItems.filter(item => !item.adminOnly || user.admin).map(({ to, icon: Icon, label }) => (
        <Link key={to} to={to} className={dropdownItemClass}>
          <Icon className="size-4 shrink-0" />
          {label}
        </Link>
      ))}
      <div className="border-t border-border" />
      <button
        type="button"
        onClick={logout}
        className="flex items-center gap-3 w-full px-4 py-3 text-sm text-error hover:bg-foreground/5 transition-colors cursor-pointer bg-transparent border-0"
      >
        <LogOut className="size-4 shrink-0" />
        Déconnexion
      </button>
    </Dropdown>
  )
}

// ─── Mobile sidebar ───────────────────────────────────────────────────────────

function MobileNavItem({ link, onClose }: Readonly<{ link: NavItem; onClose: () => void }>) {
  const [open, setOpen] = useState(false)
  const Icon = link.icon

  if (!link.subLinks) {
    return (
      <NavLink to={link.to} onClick={onClose} className={({ isActive }) => sidebarItemClass(isActive)}>
        <Icon className="size-5 shrink-0" />
        {link.label}
      </NavLink>
    )
  }

  return (
    <div>
      <button
        type="button"
        onClick={() => setOpen(p => !p)}
        className={`${sidebarItemClass()} w-full cursor-pointer bg-transparent border-0`}
      >
        <Icon className="size-5 shrink-0" />
        <span className="flex-1 text-left">{link.label}</span>
        <ChevronDown className={`size-4 shrink-0 transition-transform duration-200 ${open ? 'rotate-180' : ''}`} />
      </button>
      {open && (
        <div className="flex flex-col pl-11 mt-1 gap-0.5">
          {link.subLinks.map(sub => (
            <Link
              key={sub.to}
              to={sub.to}
              onClick={onClose}
              className="px-3 py-2 rounded-lg text-sm text-foreground/60 hover:text-foreground hover:bg-foreground/5 transition-colors"
            >
              {sub.label}
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}

function MobileMenu({ onClose }: Readonly<{ onClose: () => void }>) {
  const { user, login, logout } = useAuth()

  return createPortal((
    <>
      <div
        aria-hidden="true"
        className="fixed inset-0 bg-foreground/40 z-40"
        onClick={onClose}
      />

      <div className="fixed top-0 left-0 h-screen w-72 bg-background border-r border-border z-50 flex flex-col">

        {/* Header */}
        <div className="flex items-center justify-between px-5 py-4 border-b border-border shrink-0">
          <Banner />
          <button
            type="button"
            onClick={onClose}
            aria-label="Fermer le menu"
            className="p-2 rounded-lg hover:bg-foreground/5 transition-colors text-foreground cursor-pointer bg-transparent border-0"
          >
            <X className="size-5" />
          </button>
        </div>

        {/* User profile */}
        {user && (
          <div className="px-5 py-4 border-b border-border shrink-0">
            <UserIdentity user={user} variant="card" />
          </div>
        )}

        {/* Scrollable nav area */}
        <nav className="flex-1 overflow-y-auto px-3 py-4 flex flex-col gap-1 justify-between">
          <div>
            {/* navLinks with icons + accordion */}
            {navLinks.map(link => (
              <MobileNavItem key={link.to} link={link} onClose={onClose} />
            ))}
          </div>

          <div>
            {/* actionButtons with labels */}
            {actionButtons.map(({ to, icon: Icon, label }) => (
              <NavLink key={to} to={to} onClick={onClose} className={({ isActive }) => sidebarItemClass(isActive)}>
                <Icon className="size-5 shrink-0" />
                {label}
              </NavLink>
            ))}
          </div>
        </nav>

        {/* Profile section */}
        <div className="px-3 py-4 border-t border-border flex flex-col gap-1 shrink-0">
          {user ? (
            <>
              {userMenuItems.filter(item => !item.adminOnly || user.admin).map(({ to, icon: Icon, label }) => (
                <Link key={to} to={to} onClick={onClose} className={sidebarItemClass()}>
                  <Icon className="size-5 shrink-0" />
                  {label}
                </Link>
              ))}
              <button
                type="button"
                onClick={() => { onClose(); logout() }}
                className="flex items-center gap-3 w-full px-3 py-2.5 rounded-xl text-sm font-medium text-error hover:bg-error/5 transition-colors cursor-pointer bg-transparent border-0"
              >
                <LogOut className="size-5 shrink-0" />
                Déconnexion
              </button>
            </>
          ) : (
            <div className="px-3">
              <ButtonPrimary size="sm" onClick={login}>Se connecter</ButtonPrimary>
            </div>
          )}
        </div>

      </div>
    </>
  ), document.body)
}

// ─── Root navbar ──────────────────────────────────────────────────────────────

export default function Navbar() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)
  const { user, login, logout, isLoading } = useAuth()
  const { theme, toggleTheme } = useTheme()

  return (
    <nav className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
      <div className="flex items-center justify-between h-navbar">

        {/* Left: Banner + desktop navlinks */}
        <div className="flex items-center gap-10">
          <Banner />
          <div className="hidden lg:flex gap-8">
            {navLinks.map(link => <DesktopNavItem key={link.to} link={link} />)}
          </div>
        </div>

        {/* Right: desktop actions+user / mobile theme+hamburger */}
        <div className="flex items-center gap-1">

          {/* Desktop only */}
          <div className="hidden lg:flex items-center gap-3">
            <NavActions />
            {isLoading && <Skeleton className="h-9 w-28" />}
            {!isLoading && (user
              ? <UserDropdownMenu user={user} logout={logout} />
              : <ButtonPrimary size="sm" onClick={login}>Se connecter</ButtonPrimary>
            )}
          </div>

          {/* Mobile only: theme + hamburger */}
          <div className="flex lg:hidden items-center gap-1">
            <button
              type="button"
              onClick={toggleTheme}
              aria-label={theme === 'dark' ? 'Passer en mode clair' : 'Passer en mode sombre'}
              className="p-2 rounded-lg hover:bg-foreground/5 transition-colors text-foreground cursor-pointer bg-transparent border-0"
            >
              {theme === 'dark' ? <Sun className="size-5" /> : <Moon className="size-5" />}
            </button>
            <button
              type="button"
              onClick={() => setMobileMenuOpen(p => !p)}
              aria-label={mobileMenuOpen ? 'Fermer le menu' : 'Ouvrir le menu'}
              className="p-2 rounded-lg hover:bg-foreground/5 transition-colors text-foreground cursor-pointer bg-transparent border-0"
            >
              <Menu className="size-6" />
            </button>
          </div>

        </div>
      </div>

      {mobileMenuOpen && (
        <MobileMenu onClose={() => setMobileMenuOpen(false)} />
      )}
    </nav>
  )
}
