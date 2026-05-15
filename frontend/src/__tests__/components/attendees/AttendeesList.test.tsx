// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import AttendeesList from '@/components/attendees/AttendeesList'
import type { UseAttendeesResult } from '@/hooks/useAttendees'

afterEach(() => {
  cleanup()
})

const a1 = {
  attendance: {
    id: 1,
    userId: 'user-1',
    eventId: 42,
    status: 'ATTENDING' as const,
    createdAt: '2026-04-08T10:00:00.000Z',
    displayName: 'Alice',
    avatarUrl: null,
    username: 'alice',
  },
  profile: {
    id: 'user-1',
    username: 'alice',
    displayName: 'Alice',
    faculty: null,
    studyLevel: null,
  },
}

const a2 = {
  attendance: {
    id: 2,
    userId: 'user-2',
    eventId: 42,
    status: 'WAITLISTED' as const,
    createdAt: '2026-04-08T10:00:00.000Z',
    displayName: 'Bob',
    avatarUrl: null,
    username: 'bob',
  },
  profile: {
    id: 'user-2',
    username: 'bob',
    displayName: 'Bob',
    faculty: null,
    studyLevel: null,
  },
}

function makeHook(overrides: Partial<UseAttendeesResult> = {}): UseAttendeesResult {
  return {
    attendees: [],
    isLoading: false,
    error: null,
    hasMore: false,
    loadMore: vi.fn(),
    refetch: vi.fn(),
    isForbidden: false,
    ...overrides,
  }
}

function renderList(props: Parameters<typeof AttendeesList>[0]) {
  return render(
    <MemoryRouter>
      <AttendeesList {...props} />
    </MemoryRouter>,
  )
}

describe('AttendeesList', () => {
  describe('non-organizer summary', () => {
    it('renders summary with the count', () => {
      renderList({ isOrganizer: false, attendingCount: 12, attendeesHook: makeHook() })

      expect(screen.getByText('12 participants')).toBeTruthy()
    })

    it('uses singular form for one participant', () => {
      renderList({ isOrganizer: false, attendingCount: 1, attendeesHook: makeHook() })

      expect(screen.getByText('1 participant')).toBeTruthy()
    })

    it('shows empty placeholder text when attendingCount is 0', () => {
      renderList({ isOrganizer: false, attendingCount: 0, attendeesHook: makeHook() })

      expect(screen.getByText('Aucun participant pour le moment.')).toBeTruthy()
    })

    it('renders avatars and counter as a single inline row (compact)', () => {
      renderList({ isOrganizer: false, attendingCount: 3, attendeesHook: makeHook() })

      const counter = screen.getByText('3 participants')
      const row = counter.parentElement
      expect(row).not.toBeNull()
      const avatars = row?.querySelector('[aria-hidden="true"]')
      expect(avatars).toBeTruthy()
      expect(row?.contains(counter)).toBe(true)
      expect(row?.contains(avatars as Node)).toBe(true)
    })
  })

  describe('organizer view', () => {
    it('renders both tabs with counts and switches between them', () => {
      renderList({
        isOrganizer: true,
        attendingCount: 1,
        attendeesHook: makeHook({ attendees: [a1, a2] }),
      })

      const attendingTab = screen.getByRole('tab', { name: /Participants/ })
      const waitTab = screen.getByRole('tab', { name: /Liste d'attente/ })
      expect(attendingTab.textContent).toContain('(1)')
      expect(waitTab.textContent).toContain('(1)')

      // Default tab is ATTENDING, so Alice is shown, Bob (waitlisted) is not
      expect(screen.getByText('Alice')).toBeTruthy()
      expect(screen.queryByText('Bob')).toBeNull()

      fireEvent.click(waitTab)

      expect(screen.queryByText('Alice')).toBeNull()
      expect(screen.getByText('Bob')).toBeTruthy()
    })

    it('shows empty state for the active tab', () => {
      renderList({
        isOrganizer: true,
        attendingCount: 1,
        attendeesHook: makeHook({ attendees: [a1] }),
      })

      fireEvent.click(screen.getByRole('tab', { name: /Liste d'attente/ }))

      expect(screen.getByText("Personne en liste d'attente.")).toBeTruthy()
    })

    it('shows empty state for the participating tab when no attendees', () => {
      renderList({
        isOrganizer: true,
        attendingCount: 0,
        attendeesHook: makeHook(),
      })

      expect(screen.getByText('Aucun participant pour le moment.')).toBeTruthy()
    })

    it('renders a loading skeleton on initial load', () => {
      const { container } = renderList({
        isOrganizer: true,
        attendingCount: 0,
        attendeesHook: makeHook({ isLoading: true, hasMore: true }),
      })

      expect(container.querySelector('[aria-busy="true"]')).toBeTruthy()
    })

    it('renders error state with retry button that calls refetch (not loadMore)', () => {
      const loadMore = vi.fn()
      const refetch = vi.fn()
      renderList({
        isOrganizer: true,
        attendingCount: 0,
        attendeesHook: makeHook({
          error: new Error('boom'),
          hasMore: true,
          loadMore,
          refetch,
        }),
      })

      expect(screen.getByText(/Impossible de charger la liste/)).toBeTruthy()
      fireEvent.click(screen.getByRole('button', { name: /Réessayer/ }))
      expect(refetch).toHaveBeenCalledTimes(1)
      expect(loadMore).not.toHaveBeenCalled()
    })

    it('shows "Charger plus" only when hasMore is true and calls loadMore on click', () => {
      const loadMore = vi.fn()
      renderList({
        isOrganizer: true,
        attendingCount: 1,
        attendeesHook: makeHook({ attendees: [a1], hasMore: true, loadMore }),
      })

      const btn = screen.getByRole('button', { name: /Charger plus/ })
      fireEvent.click(btn)
      expect(loadMore).toHaveBeenCalled()
    })

    it('hides "Charger plus" when hasMore is false', () => {
      renderList({
        isOrganizer: true,
        attendingCount: 1,
        attendeesHook: makeHook({ attendees: [a1] }),
      })

      expect(screen.queryByRole('button', { name: /Charger plus/ })).toBeNull()
    })

    it('disables "Charger plus" while loading another page', () => {
      renderList({
        isOrganizer: true,
        attendingCount: 1,
        attendeesHook: makeHook({ attendees: [a1], isLoading: true, hasMore: true }),
      })

      const btn = screen.getByRole('button', { name: /Chargement/ }) as HTMLButtonElement
      expect(btn.disabled).toBe(true)
    })

    it('falls back to summary when isForbidden is true', () => {
      renderList({
        isOrganizer: true,
        attendingCount: 7,
        attendeesHook: makeHook({ isForbidden: true }),
      })

      expect(screen.queryByRole('tab')).toBeNull()
      expect(screen.getByText('7 participants')).toBeTruthy()
    })
  })
})
