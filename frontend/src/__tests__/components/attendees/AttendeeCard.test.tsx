// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import AttendeeCard from '@/components/attendees/AttendeeCard'
import type { Attendance } from '@/types/attendance'
import type { UserPublicResponse } from '@/types/user'

afterEach(() => { cleanup() })

const attendance: Attendance = {
  id: 1,
  userId: 'user-1',
  eventId: 42,
  status: 'ATTENDING',
  createdAt: '2026-04-08T10:00:00.000Z',
}

const profile: UserPublicResponse = {
  id: 'user-1',
  username: 'alice.martin',
  displayName: 'Alice Martin',
  faculty: 'SCIENCES',
  studyLevel: 'MASTER',
  bio: null,
  interests: [],
  avatarUrl: null,
  bannerUrl: null,
}

function renderCard(...args: Parameters<typeof AttendeeCard>) {
  const [props] = args
  return render(
    <MemoryRouter>
      <AttendeeCard {...props} />
    </MemoryRouter>,
  )
}

describe('AttendeeCard', () => {
  it('renders profile and links to /profile/:username', () => {
    renderCard({ attendance, profile })

    const link = screen.getByRole('link') as HTMLAnchorElement
    expect(link.getAttribute('href')).toBe('/profile/alice.martin')
    expect(screen.getByText('Alice Martin')).toBeTruthy()
    expect(screen.getByText(/Master/)).toBeTruthy()
    expect(screen.getByText(/Sciences/)).toBeTruthy()
  })

  it('renders only the meta values that are present', () => {
    renderCard({
      attendance,
      profile: { ...profile, faculty: null, studyLevel: 'BACHELOR' },
    })

    expect(screen.getByText('Bachelor')).toBeTruthy()
  })

  it('uses fallback name when displayName is null', () => {
    renderCard({
      attendance,
      profile: { ...profile, displayName: null },
    })

    expect(screen.getByText('Utilisateur')).toBeTruthy()
  })

  it('renders anonymous variant without a link when profile is null', () => {
    renderCard({ attendance, profile: null })

    expect(screen.queryByRole('link')).toBeNull()
    expect(screen.getByText('Utilisateur anonyme')).toBeTruthy()
    expect(screen.getByLabelText('Avatar anonyme')).toBeTruthy()
  })

  it('renders a waitlist badge when status is WAITLISTED', () => {
    renderCard({
      attendance: { ...attendance, status: 'WAITLISTED' },
      profile,
    })

    expect(screen.getByText("Liste d'attente")).toBeTruthy()
  })

  it('renders a waitlist badge on anonymous variant too', () => {
    renderCard({
      attendance: { ...attendance, status: 'WAITLISTED' },
      profile: null,
    })

    expect(screen.getByText("Liste d'attente")).toBeTruthy()
  })

  it('does not render meta when both faculty and studyLevel are null', () => {
    renderCard({
      attendance,
      profile: { ...profile, faculty: null, studyLevel: null },
    })

    expect(screen.queryByText(/Master/)).toBeNull()
    expect(screen.queryByText(/Sciences/)).toBeNull()
  })
})
