// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import EventCard from '../../components/events/EventCard'
import type { Event } from '../../types'

const mockEvent: Event = {
  id: 1,
  title: 'Conférence IA',
  location: 'Uni Dufour',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'user-1',
  capacity: 200,
  createdAt: '2026-03-01T10:00:00',
}

afterEach(() => cleanup())

function renderCard(event: Event) {
  return render(
    <MemoryRouter>
      <EventCard event={event} />
    </MemoryRouter>,
  )
}

describe('EventCard', () => {
  it('renders event title', () => {
    renderCard(mockEvent)
    expect(screen.getByText('Conférence IA')).toBeTruthy()
  })

  it('renders event location', () => {
    renderCard(mockEvent)
    expect(screen.getByText(/Uni Dufour/)).toBeTruthy()
  })

  it('renders category badge', () => {
    renderCard(mockEvent)
    expect(screen.getByText('Conférence')).toBeTruthy()
  })

  it('renders capacity', () => {
    renderCard(mockEvent)
    expect(screen.getByText(/200 places/)).toBeTruthy()
  })

  it('links to event detail page', () => {
    renderCard(mockEvent)
    const link = screen.getByRole('link')
    expect(link.getAttribute('href')).toBe('/events/1')
  })

  it('does not render capacity when not provided', () => {
    renderCard({ ...mockEvent, capacity: undefined })
    expect(screen.queryByText(/places/)).toBeNull()
  })

  it('applies hover styles on mouse enter and reverts on mouse leave', () => {
    renderCard(mockEvent)
    const article = document.querySelector('article')!
    fireEvent.mouseEnter(article)
    expect(article.style.transform).toBe('translateY(-2px)')
    expect(article.style.boxShadow).toBe('0 8px 24px rgba(0,0,0,0.12)')
    fireEvent.mouseLeave(article)
    expect(article.style.transform).toBe('translateY(0)')
    expect(article.style.boxShadow).toBe('none')
  })

  it('renders all event categories without error', () => {
    const categories: Event['category'][] = ['ACADEMIC', 'SPORTS', 'CULTURAL', 'SOCIAL', 'CONFERENCE', 'OTHER']
    for (const category of categories) {
      const { unmount } = renderCard({ ...mockEvent, category })
      expect(screen.getAllByRole('article').length).toBeGreaterThan(0)
      unmount()
      cleanup()
    }
  })
})
