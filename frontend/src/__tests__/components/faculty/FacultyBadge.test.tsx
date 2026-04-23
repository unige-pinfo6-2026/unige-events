
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import FacultyBadge from '@/components/faculty/FacultyBadge'
import { FACULTIES } from '@/types/faculty'
import type { Faculty } from '@/types/faculty'

afterEach(() => cleanup())

const ALL_IDS = Object.keys(FACULTIES) as Faculty[]

describe('FacultyBadge', () => {
  it.each(ALL_IDS)('renders the abbr for %s', (id) => {
    render(<FacultyBadge id={id} />)
    expect(screen.getByText(FACULTIES[id].abbr)).toBeTruthy()
  })

  it.each(ALL_IDS)('has aria-label with the full faculty name for %s', (id) => {
    render(<FacultyBadge id={id} />)
    const badge = screen.getByRole('generic', { name: FACULTIES[id].name })
    expect(badge).toBeTruthy()
    cleanup()
  })

  it.each(ALL_IDS)('applies a backgroundColor inline style for %s', (id) => {
    render(<FacultyBadge id={id} />)
    const badge = screen.getByText(FACULTIES[id].abbr) as HTMLElement
    expect(badge.style.backgroundColor).toBeTruthy()
  })

  it('renders SCIENCES with text-white class', () => {
    render(<FacultyBadge id="SCIENCES" />)
    const badge = screen.getByText('Sciences')
    expect(badge.className).toContain('text-white')
  })

  it('renders MEDICINE with text-white class', () => {
    render(<FacultyBadge id="MEDICINE" />)
    const badge = screen.getByText('Médecine')
    expect(badge.className).toContain('text-white')
  })

  it('renders LETTERS with text-white class', () => {
    render(<FacultyBadge id="LETTERS" />)
    const badge = screen.getByText('Lettres')
    expect(badge.className).toContain('text-white')
  })

  it('renders LAW with text-white class', () => {
    render(<FacultyBadge id="LAW" />)
    const badge = screen.getByText('Droit')
    expect(badge.className).toContain('text-white')
  })

  it('renders THEOLOGY with text-white class', () => {
    render(<FacultyBadge id="THEOLOGY" />)
    const badge = screen.getByText('Théologie')
    expect(badge.className).toContain('text-white')
  })

  it('renders FTI with text-white class', () => {
    render(<FacultyBadge id="FTI" />)
    const badge = screen.getByText('FTI')
    expect(badge.className).toContain('text-white')
  })

  it('renders GSEM with text-white class', () => {
    render(<FacultyBadge id="GSEM" />)
    const badge = screen.getByText('GSEM')
    expect(badge.className).toContain('text-white')
  })

  it('renders PSYCHOLOGY with text-white class', () => {
    render(<FacultyBadge id="PSYCHOLOGY" />)
    const badge = screen.getByText('Psychologie')
    expect(badge.className).toContain('text-white')
  })

  it('renders SOCIAL_SCIENCES with text-white class', () => {
    render(<FacultyBadge id="SOCIAL_SCIENCES" />)
    const badge = screen.getByText('SdS')
    expect(badge.className).toContain('text-white')
  })

  it('renders a span element', () => {
    render(<FacultyBadge id="SCIENCES" />)
    const badge = screen.getByText('Sciences')
    expect(badge.tagName.toLowerCase()).toBe('span')
  })

  it('FACULTIES has an entry for every Faculty key', () => {
    for (const id of ALL_IDS) {
      expect(FACULTIES[id].abbr).toBeTruthy()
      expect(FACULTIES[id].name).toBeTruthy()
      expect(FACULTIES[id].color).toBeTruthy()
    }
  })
})
