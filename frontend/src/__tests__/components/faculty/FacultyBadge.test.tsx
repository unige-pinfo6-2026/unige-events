// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import FacultyBadge from '@/components/faculty/FacultyBadge'
import { Faculty, FACULTY_LABELS } from '@/types/event'

afterEach(() => cleanup())

describe('FacultyBadge', () => {
  it.each(Object.values(Faculty))('renders the French label for %s', (faculty) => {
    render(<FacultyBadge faculty={faculty} />)
    expect(screen.getByText(FACULTY_LABELS[faculty])).toBeTruthy()
  })

  it.each(Object.values(Faculty))('has aria-label "Faculté : <label>" for %s', (faculty) => {
    render(<FacultyBadge faculty={faculty} />)
    const badge = screen.getByRole('generic', { name: `Faculté : ${FACULTY_LABELS[faculty]}` })
    expect(badge).toBeTruthy()
    cleanup()
  })

  it('renders SCIENCES with blue classes', () => {
    render(<FacultyBadge faculty={Faculty.SCIENCES} />)
    const badge = screen.getByText('Sciences')
    expect(badge.className).toContain('bg-[#007E64]')
    expect(badge.className).toContain('text-white')
  })

  it('renders LETTRES with purple classes', () => {
    render(<FacultyBadge faculty={Faculty.LETTRES} />)
    const badge = screen.getByText('Lettres')
    expect(badge.className).toContain('bg-[#0067C5]')
    expect(badge.className).toContain('text-white')
  })

  it('renders DROIT with red classes', () => {
    render(<FacultyBadge faculty={Faculty.DROIT} />)
    const badge = screen.getByText('Droit')
    expect(badge.className).toContain('bg-[#F42941]')
    expect(badge.className).toContain('text-white')
  })

  it('renders MEDECINE with green classes', () => {
    render(<FacultyBadge faculty={Faculty.MEDECINE} />)
    const badge = screen.getByText('Médecine')
    expect(badge.className).toContain('bg-[#96004B]')
    expect(badge.className).toContain('text-white')
  })

  it('renders SES with yellow classes', () => {
    render(<FacultyBadge faculty={Faculty.SES} />)
    const badge = screen.getByText('SES')
    expect(badge.className).toContain('bg-[#F1AB00]')
    expect(badge.className).toContain('text-gray-900')
  })

  it('renders PSYCHOLOGIE with pink classes', () => {
    render(<FacultyBadge faculty={Faculty.PSYCHOLOGIE} />)
    const badge = screen.getByText('Psychologie')
    expect(badge.className).toContain('bg-[#C69200]')
    expect(badge.className).toContain('text-gray-900')
  })

  it('renders THEOLOGIE with orange classes', () => {
    render(<FacultyBadge faculty={Faculty.THEOLOGIE} />)
    const badge = screen.getByText('Théologie')
    expect(badge.className).toContain('bg-[#4B0B71]')
    expect(badge.className).toContain('text-white')
  })

  it('renders FTI with cyan classes', () => {
    render(<FacultyBadge faculty={Faculty.FTI} />)
    const badge = screen.getByText('FTI')
    expect(badge.className).toContain('bg-[#FF5C00]')
    expect(badge.className).toContain('text-white')
  })

  it('renders GSI with indigo classes', () => {
    render(<FacultyBadge faculty={Faculty.GSI} />)
    const badge = screen.getByText('GSI')
    expect(badge.className).toContain('bg-[#003580]')
    expect(badge.className).toContain('text-white')
  })

  it('renders a span element', () => {
    render(<FacultyBadge faculty={Faculty.SCIENCES} />)
    const badge = screen.getByText('Sciences')
    expect(badge.tagName.toLowerCase()).toBe('span')
  })

  it('FACULTY_LABELS has an entry for every Faculty value', () => {
    for (const f of Object.values(Faculty)) {
      expect(FACULTY_LABELS[f]).toBeTruthy()
    }
  })
})
