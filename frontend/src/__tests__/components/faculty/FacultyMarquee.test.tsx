
import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import FacultyMarquee from '@/components/faculty/FacultyMarquee'
import FacultyCard from '@/components/faculty/FacultyCard'
import { FACULTIES } from '@/types/faculty'
import type { Faculty } from '@/types/faculty'

afterEach(() => cleanup())

const ALL_IDS = Object.keys(FACULTIES) as Faculty[]

describe('FacultyMarquee', () => {
  it('renders an svg logo for every faculty', () => {
    const { container } = render(<FacultyMarquee />)
    const svgs = container.querySelectorAll('svg')
    // Marquee duplicates its children, so at least one svg per faculty appears.
    expect(svgs.length).toBeGreaterThanOrEqual(ALL_IDS.length)
  })
})

describe('FacultyCard', () => {
  it.each(ALL_IDS)('renders an svg logo for %s', (id) => {
    const { container } = render(<FacultyCard faculty={id} />)
    expect(container.querySelector('svg')).toBeTruthy()
  })

  it('renders the logo svg with the fixed default sizing class', () => {
    // FacultyCard takes no className prop — it always renders
    // <Logo className="w-auto h-24" />. Assert that fixed default class.
    const { container } = render(<FacultyCard faculty="SCIENCES" />)
    const svg = container.querySelector('svg')
    expect(svg?.getAttribute('class')).toContain('w-auto h-24')
  })
})
