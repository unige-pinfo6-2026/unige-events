
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { TextLink, IconLink, ContactLink } from '@/components/utils/Links'
import { Mail } from 'lucide-react'

afterEach(() => { cleanup() })

describe('TextLink', () => {
  it('renders a link with the correct href', () => {
    render(<TextLink href="/about">À propos</TextLink>)
    const link = screen.getByRole('link', { name: 'À propos' })
    expect(link.getAttribute('href')).toBe('/about')
  })

  it('renders children text', () => {
    render(<TextLink href="/">Accueil</TextLink>)
    expect(screen.getByText('Accueil')).toBeTruthy()
  })

  it('renders the decoration span when decorate is true', () => {
    const { container } = render(<TextLink href="/" decorate>Lien</TextLink>)
    expect(container.querySelector('span')).toBeTruthy()
  })

  it('does not render the decoration span when decorate is false', () => {
    const { container } = render(<TextLink href="/">Lien</TextLink>)
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
