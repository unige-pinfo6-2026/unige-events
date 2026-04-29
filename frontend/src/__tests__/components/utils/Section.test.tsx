
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'

afterEach(() => { cleanup() })

describe('SectionWrapper', () => {
  it('renders children', () => {
    render(<SectionWrapper><p>Contenu</p></SectionWrapper>)
    expect(screen.getByText('Contenu')).toBeTruthy()
  })

  it('sets the id on the section element when provided', () => {
    const { container } = render(<SectionWrapper id="hero"><p>x</p></SectionWrapper>)
    expect(container.querySelector('#hero')).toBeTruthy()
  })

  it('renders background element when provided', () => {
    render(
      <SectionWrapper background={<div data-testid="bg" />}>
        <p>x</p>
      </SectionWrapper>,
    )
    expect(screen.getByTestId('bg')).toBeTruthy()
  })

  it('renders footer element when provided', () => {
    render(
      <SectionWrapper footer={<footer data-testid="ft">footer</footer>}>
        <p>x</p>
      </SectionWrapper>,
    )
    expect(screen.getByTestId('ft')).toBeTruthy()
  })

  it('renders without background or footer', () => {
    const { container } = render(<SectionWrapper><p>x</p></SectionWrapper>)
    expect(container.querySelector('section')).toBeTruthy()
  })

  it('applies padding hero variant', () => {
    const { container } = render(<SectionWrapper padding="hero"><p>x</p></SectionWrapper>)
    expect(container.querySelector('section')?.className).toContain('min-h-hero')
  })

  it('applies padding sm variant', () => {
    const { container } = render(<SectionWrapper padding="sm"><p>x</p></SectionWrapper>)
    expect(container.querySelector('section')?.className).toContain('py-12')
  })

  it('applies size lg variant', () => {
    const { container } = render(<SectionWrapper size="lg"><p>x</p></SectionWrapper>)
    expect(container.querySelector('div')?.className).toContain('max-w-5xl')
  })

  it('applies size md variant', () => {
    const { container } = render(<SectionWrapper size="md"><p>x</p></SectionWrapper>)
    expect(container.querySelector('div')?.className).toContain('max-w-3xl')
  })
})

describe('SectionHeader', () => {
  it('renders the title', () => {
    render(<SectionHeader title="Mon titre" />)
    expect(screen.getByRole('heading', { name: 'Mon titre' })).toBeTruthy()
  })

  it('renders subtitle when provided', () => {
    render(<SectionHeader title="Titre" subtitle="Sous-titre" />)
    expect(screen.getByText('Sous-titre')).toBeTruthy()
  })

  it('does not render subtitle when absent', () => {
    render(<SectionHeader title="Titre" />)
    expect(screen.queryByRole('paragraph')).toBeNull()
  })

  it('applies xl heading variant', () => {
    const { container } = render(<SectionHeader title="Grand" heading="xl" />)
    expect(container.querySelector('h2')?.className).toContain('text-5xl')
  })

  it('applies lg heading variant', () => {
    const { container } = render(<SectionHeader title="Grand" heading="lg" />)
    expect(container.querySelector('h2')?.className).toContain('text-4xl')
  })

  it('applies left align', () => {
    const { container } = render(<SectionHeader title="Titre" align="left" />)
    expect(container.querySelector('div')?.className).toContain('text-left')
  })

  it('applies right align', () => {
    const { container } = render(<SectionHeader title="Titre" align="right" />)
    expect(container.querySelector('div')?.className).toContain('text-right')
  })
})
