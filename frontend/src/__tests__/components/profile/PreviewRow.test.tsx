// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import PreviewRow from '@/components/profile/PreviewRow'
import type { Event, EventStatus } from '@/types/event'
import { EVENT_STATUS_VARIANTS } from '@/utils/eventStatusStyles'

function makeEvent(overrides: Partial<Event> = {}): Event {
  return {
    id: 42,
    title: 'Conférence IA',
    location: 'Uni Dufour',
    startDate: '2026-04-10T14:00:00',
    endDate: '2026-04-10T17:00:00',
    allDay: false,
    category: 'CONFERENCE',
    faculty: null,
    status: 'PUBLISHED',
    creatorId: 'user-1',
    capacity: 200,
    attendingCount: 12,
    createdAt: '2026-03-01T10:00:00',
    ...overrides,
  }
}

function renderRow(event: Event) {
  return render(
    <MemoryRouter>
      <PreviewRow event={event} />
    </MemoryRouter>,
  )
}

afterEach(() => cleanup())

describe('PreviewRow', () => {
  it('renders the event title', () => {
    renderRow(makeEvent({ title: 'Mon événement' }))
    expect(screen.getByText('Mon événement')).toBeTruthy()
  })

  it('renders the formatted date and the attendingCount', () => {
    renderRow(makeEvent({ attendingCount: 7 }))
    expect(screen.getByText(/avr/i)).toBeTruthy()
    expect(screen.getByText('7')).toBeTruthy()
  })

  it('links the whole row to /events/{id}', () => {
    renderRow(makeEvent({ id: 99 }))
    const link = screen.getByRole('link', { name: 'Conférence IA' })
    expect(link.getAttribute('href')).toBe('/events/99')
  })

  it('renders the status badge label from EVENT_STATUSES', () => {
    renderRow(makeEvent({ status: 'DRAFT' }))
    expect(screen.getByText('Brouillon')).toBeTruthy()
  })

  it.each<EventStatus>(['PUBLISHED', 'DRAFT', 'CANCELLED'])(
    'applies the right variant classes for status %s',
    (status) => {
      renderRow(makeEvent({ status }))
      const badge = screen.getByText(/Publié|Brouillon|Annulé/)
      const expectedClass = EVENT_STATUS_VARIANTS[status].split(' ')[0]
      expect(badge.className).toContain(expectedClass)
    },
  )

  it('uses a banner background image when bannerUrl is set', () => {
    const { container } = renderRow(makeEvent({ bannerUrl: 'https://example.com/b.jpg' }))
    const thumb = container.querySelector('[aria-hidden="true"]') as HTMLDivElement
    expect(thumb.style.backgroundImage).toContain('example.com/b.jpg')
  })

  it('falls back to a category gradient when bannerUrl is missing', () => {
    const { container } = renderRow(makeEvent({ bannerUrl: undefined }))
    const thumb = container.querySelector('[aria-hidden="true"]') as HTMLDivElement
    const bg = `${thumb.style.background} ${thumb.style.backgroundImage}`
    expect(bg).toContain('linear-gradient')
    expect(bg).not.toContain('url(')
  })
})
