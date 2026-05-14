// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import AttendeeCard from '@/components/attendees/AttendeeCard'
import type { Attendance } from '@/types/attendance'

afterEach(() => { cleanup() })

const publicRow: Attendance = {
  id: 1,
  userId: 'user-1',
  eventId: 42,
  status: 'ATTENDING',
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: 'Alice Martin',
  avatarUrl: null,
}

// SCRUM-S7 — backend returns userId=null + displayName=null for private rows
// seen by a non-organizer caller. Same shape for orphan rows (deleted users).
const anonymizedRow: Attendance = {
  id: 2,
  userId: null,
  eventId: 42,
  status: 'ATTENDING',
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: null,
  avatarUrl: null,
}

function renderCard(attendance: Attendance) {
  return render(
    <MemoryRouter>
      <AttendeeCard attendance={attendance} />
    </MemoryRouter>,
  )
}

describe('AttendeeCard', () => {
  it('renders the displayName and links to /profile/{userId} when identity is exposed', () => {
    renderCard(publicRow)

    const link = screen.getByRole('link') as HTMLAnchorElement
    expect(link.getAttribute('href')).toBe('/profile/user-1')
    expect(screen.getByText('Alice Martin')).toBeTruthy()
  })

  it('renders anonymous variant without a link when displayName is null', () => {
    renderCard(anonymizedRow)

    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.getByText('Utilisateur anonyme')).toBeTruthy()
    expect(screen.getByText('Profil privé')).toBeTruthy()
    expect(screen.getByLabelText('Avatar anonyme')).toBeTruthy()
  })

  it('renders a waitlist badge on the identity variant', () => {
    renderCard({ ...publicRow, status: 'WAITLISTED' })

    expect(screen.getByText("Liste d'attente")).toBeTruthy()
  })

  it('renders a waitlist badge on the anonymous variant too', () => {
    renderCard({ ...anonymizedRow, status: 'WAITLISTED' })

    expect(screen.getByText("Liste d'attente")).toBeTruthy()
  })

  it('does NOT render a link when displayName is present but userId is null (defensive)', () => {
    // Currently unreachable from the backend contract (userId and displayName
    // are nulled together), but the component must never produce
    // /profile/null. This guards against future contract drift.
    renderCard({ ...publicRow, userId: null })

    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.getByText('Alice Martin')).toBeTruthy()
  })
})
