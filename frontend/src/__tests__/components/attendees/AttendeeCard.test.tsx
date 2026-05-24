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
  username: 'alice.martin',
}

// SCRUM-S7 — backend returns userId=null + displayName=null (+ username=null
// after the SCRUM-169 merge) for private rows seen by a non-organizer caller.
// Same shape for orphan rows (deleted users).
const anonymizedRow: Attendance = {
  id: 2,
  userId: null,
  eventId: 42,
  status: 'ATTENDING',
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: null,
  avatarUrl: null,
  username: null,
}

function renderCard(attendance: Attendance) {
  return render(
    <MemoryRouter>
      <AttendeeCard attendance={attendance} />
    </MemoryRouter>,
  )
}

describe('AttendeeCard', () => {
  it('renders the displayName and links to /profile/{username} when identity is exposed (SCRUM-169)', () => {
    renderCard(publicRow)

    const link = screen.getByRole('link') as HTMLAnchorElement
    expect(link.getAttribute('href')).toBe('/profile/alice.martin')
    expect(screen.getByText('Alice Martin')).toBeTruthy()
  })

  it('falls back to /profile/{userId} when username is null but userId is present (orphan-ish)', () => {
    renderCard({ ...publicRow, username: null })

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

  it('does NOT render a link when displayName is present but both username AND userId are null (defensive)', () => {
    // Currently unreachable from the backend contract — username + userId +
    // displayName are nulled together for anonymized rows. This guards
    // against future contract drift where a displayName lands without any
    // linkable slug (we'd otherwise produce /profile/null).
    renderCard({ ...publicRow, userId: null, username: null })

    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.getByText('Alice Martin')).toBeTruthy()
  })

  it('renders a waitlist badge on the unlinkable-identity variant (no link + WAITLISTED @line 71)', () => {
    // displayName present but no slug → unlinkable identity body; combined with
    // WAITLISTED status to exercise the badge inside the no-link branch.
    renderCard({ ...publicRow, userId: null, username: null, status: 'WAITLISTED' })

    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.getByText('Alice Martin')).toBeTruthy()
    expect(screen.getByText("Liste d'attente")).toBeTruthy()
  })
})
