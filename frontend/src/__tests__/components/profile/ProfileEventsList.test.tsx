// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ProfileEventsList from '@/components/profile/ProfileEventsList'
import type { Event } from '@/types/event'

afterEach(() => { cleanup() })

const event: Event = {
  id: 1,
  title: 'Conférence IA',
  description: '',
  location: 'Uni Dufour',
  startDate: '2026-05-14T10:00:00.000Z',
  endDate: '2026-05-14T12:00:00.000Z',
  category: 'CONFERENCE',
  faculty: null,
  status: 'PUBLISHED',
  creatorId: 'a4ab9d0a-3e1c-4b6e-9a8d-0c1e2f3a4b5c',
  capacity: 50,
  attendingCount: 3,
  createdAt: '2026-05-01T10:00:00.000Z',
  allDay: false,
}

function renderList(props: Parameters<typeof ProfileEventsList>[0]) {
  return render(
    <MemoryRouter>
      <ProfileEventsList {...props} />
    </MemoryRouter>,
  )
}

describe('ProfileEventsList', () => {
  it('renders the heading on every state', () => {
    renderList({ events: [], loading: false, error: null })

    expect(screen.getByRole('heading', { name: 'Événements organisés' })).toBeTruthy()
  })

  it('shows loading skeleton when loading is true', () => {
    const { container } = renderList({ events: [], loading: true, error: null })

    expect(container.querySelector('[aria-busy="true"]')).toBeTruthy()
  })

  it('shows the populated event list when events.length > 0', () => {
    renderList({ events: [event], loading: false, error: null })

    expect(screen.getByText('Conférence IA')).toBeTruthy()
    const link = screen.getByRole('link', { name: /Conférence IA/ }) as HTMLAnchorElement
    expect(link.getAttribute('href')).toBe('/events/1')
  })

  it('shows empty state when no events and no error', () => {
    renderList({ events: [], loading: false, error: null })

    expect(screen.getByText('Aucun événement organisé pour le moment.')).toBeTruthy()
  })

  it('renders the error message when error is set', () => {
    renderList({ events: [], loading: false, error: 'Impossible de charger les événements organisés.' })

    expect(screen.getByText('Impossible de charger les événements organisés.')).toBeTruthy()
  })
})
