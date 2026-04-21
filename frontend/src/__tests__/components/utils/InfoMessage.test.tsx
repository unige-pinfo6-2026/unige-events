
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { AlertCircle } from 'lucide-react'

afterEach(() => { cleanup() })

describe('InfoMessage', () => {
  it('renders the message text', () => {
    render(<InfoMessage type="success" message="Opération réussie." />)
    expect(screen.getByText('Opération réussie.')).toBeTruthy()
  })

  it('renders with type success', () => {
    const { container } = render(<InfoMessage type="success" message="Succès" />)
    expect(container.firstChild).toBeTruthy()
    expect(screen.getByText('Succès')).toBeTruthy()
  })

  it('renders with type info', () => {
    render(<InfoMessage type="info" message="Information" />)
    expect(screen.getByText('Information')).toBeTruthy()
  })

  it('renders with type error', () => {
    render(<InfoMessage type="error" message="Une erreur est survenue." />)
    expect(screen.getByText('Une erreur est survenue.')).toBeTruthy()
  })

  it('renders an icon when the icon prop is provided', () => {
    const { container } = render(
      <InfoMessage type="info" message="Avec icône" icon={AlertCircle} />,
    )
    expect(container.querySelector('svg')).toBeTruthy()
  })

  it('does not render an icon when the icon prop is absent', () => {
    const { container } = render(<InfoMessage type="info" message="Sans icône" />)
    expect(container.querySelector('svg')).toBeNull()
  })
})
