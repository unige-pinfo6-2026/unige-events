import { useState } from 'react'
import { createPortal } from 'react-dom'
import { Link, NavLink } from 'react-router-dom'
import { useAuth } from '@/hooks/useAuth'
import UserIdentity from '@/components/user/UserIdentity'
import { ButtonPrimary, IconButton } from '@/components/utils/Buttons'
import { ThemeToggle } from '@/components/utils/ThemeToggle'
import { NotificationsDropdown } from '@/components/utils/NotificationsDropdown'
import { Skeleton } from '@/components/utils/Skeleton'
import { Dropdown } from '@/components/utils/Dropdown'
import { ActionLink } from '@/components/utils/Links'
import { Banner } from '@/assets/Banner'
import { Calendar, ChevronDown, Heart, LayoutDashboard, LogOut, Menu, Search, Shield, SquarePlus, Ticket, User, X } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'
import type { User as UserType } from '@/types/user'

// ─── Types & données ──────────────────────────────────────────────────────────

type NavItem = { to: string; icon: LucideIcon; label: string; adminOnly?: boolean; subLinks?: NavItem[] }

const navLinks: NavItem[] = [
  { label: 'Événements', to: '/events', icon: Ticket,
    subLinks: [
      { label: 'Rechercher un événement', to: '/events/search', icon: Search },
      { label: 'Créer un événement', to: '/events/new', icon: SquarePlus },
    ],
  },
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

const visibleUserMenu = (user: UserType) => userMenuItems.filter(i => !i.adminOnly || user.admin)

// ─── Styles partagés ──────────────────────────────────────────────────────────

const dropdownItemClass = 'flex items-center gap-3 px-4 py-3 text-sm text-foreground/70 hover:text-foreground hover:bg-foreground/5 transition-colors'

const sidebarItemClass = (isActive = false) =>
  `flex items-center gap-3 px-3 py-2.5 rounded-xl text-sm font-medium transition-colors ${
    isActive ? 'bg-accent/10 text-accent' : 'text-foreground/60 hover:text-foreground hover:bg-foreground/5'
  }`

const logoutVariants = {
  dropdown: { item: 'flex items-center gap-3 w-full px-4 py-3 text-sm text-error hover:bg-foreground/5 transition-colors cursor-pointer bg-transparent border-0', icon: 'size-4' },
  sidebar:  { item: 'flex items-center gap-3 w-full px-3 py-2.5 rounded-xl text-sm font-medium text-error hover:bg-error/5 transition-colors cursor-pointer bg-transparent border-0', icon: 'size-5' },
} as const

function LogoutButton({ onClick, variant }: Readonly<{ onClick: () => void; variant: keyof typeof logoutVariants }>) {
  const { item, icon } = logoutVariants[variant]
  return (
    <button type="button" onClick={onClick} className={item}>
      <LogOut className={`${icon} shrink-0`} />
      Déconnexion
    </button>
  )
}

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

  if (!link.subLinks) return trigger

  return (
    <Dropdown trigger={trigger}>
      {link.subLinks.map(({ to, icon: Icon, label }) => (
        <Link key={to} to={to} className={dropdownItemClass}>
          <Icon className="size-4 shrink-0" />
          {label}
        </Link>
      ))}
    </Dropdown>
  )
}

function DesktopNav() {
  const { user, login, logout, isLoading } = useAuth()

  return (
    <div className="hidden lg:flex items-center gap-3">
      <div className="flex items-center gap-1">
        {actionButtons.map(({ to, icon, label }) => (
          <ActionLink key={to} to={to} icon={icon} label={label} />
        ))}
        <ThemeToggle />
        <NotificationsDropdown />
      </div>
      {isLoading && <Skeleton className="h-9 w-28" />}
      {!isLoading && (user
        ? (
          <Dropdown align="right" trigger={<UserIdentity user={user} />}>
            {visibleUserMenu(user).map(({ to, icon: Icon, label }) => (
              <Link key={to} to={to} className={dropdownItemClass}>
                <Icon className="size-4 shrink-0" />
                {label}
              </Link>
            ))}
            <div className="border-t border-border" />
            <LogoutButton onClick={logout} variant="dropdown" />
          </Dropdown>
        )
        : <ButtonPrimary size="sm" onClick={login}>Se connecter</ButtonPrimary>
      )}
    </div>
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
        <div className="flex flex-col pl-6 mt-1 gap-0.5">
          {link.subLinks.map(({ to, icon: Icon, label }) => (
            <Link
              key={to}
              to={to}
              onClick={onClose}
              className="flex items-center gap-3 px-3 py-2 rounded-lg text-sm text-foreground/60 hover:text-foreground hover:bg-foreground/5 transition-colors"
            >
              <Icon className="size-4 shrink-0" />
              {label}
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}

function MobileMenu({ onClose }: Readonly<{ onClose: () => void }>) {
  const { user, login, logout } = useAuth()

  return createPortal(
    <div className="relative lg:hidden">
      <div aria-hidden="true" className="fixed inset-0 backdrop-blur-lg z-40" onClick={onClose} />

      <div className="fixed top-0 left-0 h-screen w-72 bg-background border-r border-border z-50 flex flex-col">
        {/* Header */}
        <div className="relative border-b border-border">
          <div className="flex items-center justify-between px-4 shrink-0 h-navbar">
            <Banner />
            <IconButton label="Fermer le menu" onClick={onClose}>
              <X className="size-5" />
            </IconButton>
          </div>
        </div>

        {/* User identity */}
        {user && (
          <div className="p-4 border-b border-border shrink-0">
            <UserIdentity user={user} variant="card" />
          </div>
        )}

        {/* Nav */}
        <nav className="flex flex-col flex-1 overflow-y-auto p-4">
          {navLinks.map(link => (
            <MobileNavItem key={link.to} link={link} onClose={onClose} />
          ))}
        </nav>

        {/* User menu */}
        <div className="flex flex-col gap-0.5 p-4 border-t border-border shrink-0">
          {user ? (
            <>
              {visibleUserMenu(user).map(({ to, icon: Icon, label }) => (
                <Link key={to} to={to} onClick={onClose} className={sidebarItemClass()}>
                  <Icon className="size-5 shrink-0" />
                  {label}
                </Link>
              ))}
              <LogoutButton onClick={() => { onClose(); logout() }} variant="sidebar" />
            </>
          ) : (
            <ButtonPrimary size="sm" onClick={login}>Se connecter</ButtonPrimary>
          )}
        </div>
      </div>
    </div>,
    document.body,
  )
}

// ─── Root navbar ──────────────────────────────────────────────────────────────

export default function Navbar() {
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  return (
    <nav className="relative border-b border-border">
      <div className="max-w-7xl mx-auto flex items-center justify-between h-navbar px-4 sm:px-6 lg:px-8">
        {/* Left */}
        <div className="flex items-center gap-10">
          <Banner />
          <div className="hidden lg:flex gap-8">
            {navLinks.map(link => <DesktopNavItem key={link.to} link={link} />)}
          </div>
        </div>

        {/* Right */}
        <div className="flex items-center gap-1">
          <DesktopNav />

          {/* Mobile */}
          <div className="flex lg:hidden items-center gap-1">
            {actionButtons.map(({ to, icon, label }) => (
              <ActionLink key={to} to={to} icon={icon} label={label} />
            ))}
            <ThemeToggle />
        <NotificationsDropdown />
            <IconButton
              label={mobileMenuOpen ? 'Fermer le menu' : 'Ouvrir le menu'}
              onClick={() => setMobileMenuOpen(p => !p)}
            >
              <Menu className="size-6" />
            </IconButton>
          </div>
        </div>

      </div>

      {mobileMenuOpen && <MobileMenu onClose={() => setMobileMenuOpen(false)} />}
    </nav>
  )
}
