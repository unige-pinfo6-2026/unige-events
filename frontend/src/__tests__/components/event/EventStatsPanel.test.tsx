import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import EventStatsPanel from '@/components/event/EventStatsPanel'

afterEach(() => { cleanup() })

describe('EventStatsPanel', () => {
  it('renders the section title', () => {
    render(<EventStatsPanel viewCount={0} interestedCount={0} attendingCount={0} />)
    expect(screen.getByText('Statistiques de participation')).toBeTruthy()
  })

  it('renders the three labels (Vues / Inscrits / Intéressés)', () => {
    render(<EventStatsPanel viewCount={1} interestedCount={2} attendingCount={3} />)
    expect(screen.getByText('Vues')).toBeTruthy()
    expect(screen.getByText('Inscrits')).toBeTruthy()
    expect(screen.getByText('Intéressés')).toBeTruthy()
  })

  it('renders numeric counters with fr-CH formatting', () => {
    render(<EventStatsPanel viewCount={1234} interestedCount={56} attendingCount={789} />)
    // fr-CH uses non-breaking thin space (U+202F) as group separator
    expect(screen.getByText(/1.234/)).toBeTruthy()
    expect(screen.getByText('56')).toBeTruthy()
    expect(screen.getByText('789')).toBeTruthy()
  })

  it('shows — when viewCount is null/undefined', () => {
    render(<EventStatsPanel viewCount={null} interestedCount={null} attendingCount={5} />)
    const dashes = screen.getAllByText('—')
    expect(dashes.length).toBe(2)
    expect(screen.getByText('5')).toBeTruthy()
  })

  it('treats undefined the same as null', () => {
    render(<EventStatsPanel viewCount={undefined} interestedCount={undefined} attendingCount={0} />)
    const dashes = screen.getAllByText('—')
    expect(dashes.length).toBe(2)
  })
})
