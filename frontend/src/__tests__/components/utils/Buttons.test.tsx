// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { ButtonPrimary, ButtonSecondary, ButtonsWrapper } from '@/components/utils/Buttons'

afterEach(() => { cleanup() })

describe('ButtonsWrapper', () => {
  it('renders children', () => {
    render(
      <ButtonsWrapper>
        <span>Child</span>
      </ButtonsWrapper>,
    )
    expect(screen.getByText('Child')).toBeTruthy()
  })
})

describe('ButtonPrimary', () => {
  it('renders button text', () => {
    render(<ButtonPrimary>Créer</ButtonPrimary>)
    expect(screen.getByRole('button', { name: 'Créer' })).toBeTruthy()
  })

  it('calls onClick when clicked', () => {
    const onClick = vi.fn()
    render(<ButtonPrimary onClick={onClick}>Cliquer</ButtonPrimary>)
    fireEvent.click(screen.getByRole('button'))
    expect(onClick).toHaveBeenCalledOnce()
  })

  it('is disabled when disabled prop is true', () => {
    render(<ButtonPrimary disabled>Désactivé</ButtonPrimary>)
    expect((screen.getByRole('button') as HTMLButtonElement).disabled).toBe(true)
  })

  it('renders with type submit', () => {
    render(<ButtonPrimary type="submit">Envoyer</ButtonPrimary>)
    expect((screen.getByRole('button') as HTMLButtonElement).type).toBe('submit')
  })

  it('renders with type reset', () => {
    render(<ButtonPrimary type="reset">Réinitialiser</ButtonPrimary>)
    expect((screen.getByRole('button') as HTMLButtonElement).type).toBe('reset')
  })

  it('defaults to type button', () => {
    render(<ButtonPrimary>Default</ButtonPrimary>)
    expect((screen.getByRole('button') as HTMLButtonElement).type).toBe('button')
  })

  it('renders with sm size class', () => {
    const { container } = render(<ButtonPrimary size="sm">Petit</ButtonPrimary>)
    expect(container.querySelector('button')?.className).toContain('px-4')
  })

  it('renders with lg size class', () => {
    const { container } = render(<ButtonPrimary size="lg">Grand</ButtonPrimary>)
    expect(container.querySelector('button')?.className).toContain('px-8')
  })
})

describe('ButtonSecondary', () => {
  it('renders button text', () => {
    render(<ButtonSecondary>Annuler</ButtonSecondary>)
    expect(screen.getByRole('button', { name: 'Annuler' })).toBeTruthy()
  })

  it('calls onClick when clicked', () => {
    const onClick = vi.fn()
    render(<ButtonSecondary onClick={onClick}>Cliquer</ButtonSecondary>)
    fireEvent.click(screen.getByRole('button'))
    expect(onClick).toHaveBeenCalledOnce()
  })

  it('is disabled when disabled prop is true', () => {
    render(<ButtonSecondary disabled>Désactivé</ButtonSecondary>)
    expect((screen.getByRole('button') as HTMLButtonElement).disabled).toBe(true)
  })

  it('renders with type submit', () => {
    render(<ButtonSecondary type="submit">Envoyer</ButtonSecondary>)
    expect((screen.getByRole('button') as HTMLButtonElement).type).toBe('submit')
  })

  it('renders with sm size', () => {
    const { container } = render(<ButtonSecondary size="sm">Petit</ButtonSecondary>)
    expect(container.querySelector('button')?.className).toContain('px-4')
  })

  it('renders with lg size', () => {
    const { container } = render(<ButtonSecondary size="lg">Grand</ButtonSecondary>)
    expect(container.querySelector('button')?.className).toContain('px-8')
  })
})
