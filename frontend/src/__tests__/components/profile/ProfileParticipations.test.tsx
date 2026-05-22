// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ProfileParticipations from '@/components/profile/ProfileParticipations'
import type { Event } from '@/types/event'

afterEach(() => { cleanup() })

function makeEvent(id: number): Event {
  return {
    id,
    title: `Participation ${id}`,
    location: 'Uni Mail',
    startDate: '2026-06-01T18:00:00',
    endDate: '2026-06-01T20:00:00',
    category: 'CONFERENCE',
    status: 'PUBLISHED',
    creatorId: 'user-1',
    createdAt: '2026-01-01T00:00:00',
    allDay: false,
    attendingCount: 3,
  }
}

function renderWith(props: Partial<React.ComponentProps<typeof ProfileParticipations>> = {}) {
  return render(
    <MemoryRouter>
      <ProfileParticipations events={[]} loading={false} error={null} {...props} />
    </MemoryRouter>,
  )
}

describe('ProfileParticipations', () => {
  it('renders the heading', () => {
    renderWith()
    expect(screen.getByRole('heading', { name: 'Participations publiques' })).toBeTruthy()
  })

  it('shows the loading skeleton rows while loading', () => {
    const { container } = renderWith({ loading: true })
    expect(container.querySelector('[aria-busy="true"]')).toBeTruthy()
  })

  it('shows the dedicated empty state when there are no participations', () => {
    renderWith({ events: [], loading: false })
    expect(screen.getByText('Aucune participation publique pour le moment.')).toBeTruthy()
  })

  it('renders a PreviewRow per event when participations are present', () => {
    renderWith({ events: [makeEvent(1), makeEvent(2)], loading: false })
    expect(screen.getByText('Participation 1')).toBeTruthy()
    expect(screen.getByText('Participation 2')).toBeTruthy()
    expect(screen.queryByText('Aucune participation publique pour le moment.')).toBeNull()
  })

  it('shows the error message on error', () => {
    renderWith({ error: 'Impossible de charger les participations.' })
    expect(screen.getByText('Impossible de charger les participations.')).toBeTruthy()
  })
})
