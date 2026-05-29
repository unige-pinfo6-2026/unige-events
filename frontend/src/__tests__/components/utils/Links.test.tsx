
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { ActionLink, TextLink, IconLink, ContactLink } from '@/components/utils/Links'
import { Mail } from 'lucide-react'

afterEach(() => { cleanup() })

describe('ActionLink', () => {
  it('renders a router link with the correct href and accessible label', () => {
    render(
      <MemoryRouter>
        <ActionLink to="/profile/me" icon={Mail} label="Mon profil" />
      </MemoryRouter>,
    )
    const link = screen.getByRole('link', { name: 'Mon profil' })
    expect(link.getAttribute('href')).toBe('/profile/me')
  })
})

describe('TextLink', () => {
  function renderRouted(ui: React.ReactNode) {
    return render(<MemoryRouter>{ui}</MemoryRouter>)
  }

  it('renders an internal route as a router link with the correct href', () => {
    renderRouted(<TextLink href="/support">Centre d'aide</TextLink>)
    const link = screen.getByRole('link', { name: "Centre d'aide" })
    expect(link.getAttribute('href')).toBe('/support')
  })

  it('renders a hash anchor as a plain anchor', () => {
    render(<TextLink href="#events">Évènements</TextLink>)
    const link = screen.getByRole('link', { name: 'Évènements' })
    expect(link.getAttribute('href')).toBe('#events')
  })

  it('scrolls to top when an internal link is clicked', () => {
    const mockScrollTo = vi.fn()
    globalThis.scrollTo = mockScrollTo

    renderRouted(<TextLink href="/rules">Règles</TextLink>)
    fireEvent.click(screen.getByRole('link', { name: 'Règles' }))

    expect(mockScrollTo).toHaveBeenCalledWith({ top: 0 })
  })

  it('renders children text', () => {
    renderRouted(<TextLink href="/">Accueil</TextLink>)
    expect(screen.getByText('Accueil')).toBeTruthy()
  })

  it('renders the decoration span when decorate is true', () => {
    const { container } = renderRouted(<TextLink href="/" decorate>Lien</TextLink>)
    expect(container.querySelector('span')).toBeTruthy()
  })

  it('does not render the decoration span when decorate is false', () => {
    const { container } = renderRouted(<TextLink href="/">Lien</TextLink>)
    expect(container.querySelector('span')).toBeNull()
  })
})

describe('IconLink', () => {
  const FakeIcon = ({ className }: { className?: string }) => (
    <svg data-testid="icon" className={className} />
  )

  it('renders a link with the correct href', () => {
    render(<IconLink href="https://github.com" icon={FakeIcon} />)
    const link = screen.getByRole('link')
    expect(link.getAttribute('href')).toBe('https://github.com')
  })

  it('renders the icon', () => {
    render(<IconLink href="/" icon={FakeIcon} />)
    expect(screen.getByTestId('icon')).toBeTruthy()
  })
})

describe('ContactLink', () => {
  it('renders a link with the correct href', () => {
    render(<ContactLink href="mailto:info@example.com" icon={Mail}>Nous contacter</ContactLink>)
    const link = screen.getByRole('link', { name: /Nous contacter/i })
    expect(link.getAttribute('href')).toBe('mailto:info@example.com')
  })

  it('renders children text', () => {
    render(<ContactLink href="mailto:x@x.com" icon={Mail}>Email</ContactLink>)
    expect(screen.getByText('Email')).toBeTruthy()
  })

  it('renders the icon', () => {
    const { container } = render(
      <ContactLink href="mailto:x@x.com" icon={Mail}>x</ContactLink>,
    )
    expect(container.querySelector('svg')).toBeTruthy()
  })
})
