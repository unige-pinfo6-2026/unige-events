// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

import AttendeesList from '@/components/attendees/AttendeesList'
import type { UseAttendeesResult } from '@/hooks/useAttendees'
import type { Attendance } from '@/types/attendance'

afterEach(() => {
  cleanup()
})

const publicRow: Attendance = {
  id: 1,
  userId: 'user-1',
  eventId: 42,
  status: 'ATTENDING',
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: 'Alice',
  avatarUrl: null,
}

const waitlistedRow: Attendance = {
  id: 2,
  userId: 'user-2',
  eventId: 42,
  status: 'WAITLISTED',
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: 'Bob',
  avatarUrl: null,
}

const anonymizedRow: Attendance = {
  id: 3,
  userId: null,
  eventId: 42,
  status: 'ATTENDING',
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: null,
  avatarUrl: null,
}

function makeHook(overrides: Partial<UseAttendeesResult> = {}): UseAttendeesResult {
  return {
    attendees: [],
    isLoading: false,
    error: null,
    hasMore: false,
    loadMore: vi.fn(),
    refetch: vi.fn(),
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
  describe('unauthenticated compact summary', () => {
    it('renders the count', () => {
      renderList({ isAuthenticated: false, attendingCount: 12, attendeesHook: makeHook() })

      expect(screen.getByText('12 participants')).toBeTruthy()
    })

    it('uses singular form for one participant', () => {
      renderList({ isAuthenticated: false, attendingCount: 1, attendeesHook: makeHook() })

      expect(screen.getByText('1 participant')).toBeTruthy()
    })

    it('shows empty placeholder text when attendingCount is 0', () => {
      renderList({ isAuthenticated: false, attendingCount: 0, attendeesHook: makeHook() })

      expect(screen.getByText('Aucun participant pour le moment.')).toBeTruthy()
    })

    it('does NOT render any AttendeeCard for unauthenticated viewers', () => {
      // Even if the hook somehow has rows (e.g. stale), the compact view ignores them.
      renderList({
        isAuthenticated: false,
        attendingCount: 3,
        attendeesHook: makeHook({ attendees: [publicRow] }),
      })

      expect(screen.queryByText('Alice')).toBeNull()
      expect(screen.queryByRole('tablist')).toBeNull()
    })

    it('renders avatars and counter as a single inline row (compact)', () => {
      renderList({ isAuthenticated: false, attendingCount: 3, attendeesHook: makeHook() })

      const counter = screen.getByText('3 participants')
      const row = counter.parentElement
      expect(row).not.toBeNull()
      const avatars = row?.querySelector('[aria-hidden="true"]')
      expect(avatars).toBeTruthy()
    })
  })

  describe('authenticated view', () => {
    it('renders both tabs with counts and switches between them', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 1,
        attendeesHook: makeHook({ attendees: [publicRow, waitlistedRow] }),
      })

      const attendingTab = screen.getByRole('tab', { name: /Participants/ })
      const waitTab = screen.getByRole('tab', { name: /Liste d'attente/ })
      expect(attendingTab.textContent).toContain('(1)')
      expect(waitTab.textContent).toContain('(1)')

      // Default tab is ATTENDING — Alice visible, Bob not.
      expect(screen.getByText('Alice')).toBeTruthy()
      expect(screen.queryByText('Bob')).toBeNull()

      fireEvent.click(waitTab)

      expect(screen.queryByText('Alice')).toBeNull()
      expect(screen.getByText('Bob')).toBeTruthy()
    })

    it('renders anonymous rows (private profiles seen by non-organizers) inline with real ones', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 2,
        attendeesHook: makeHook({ attendees: [publicRow, anonymizedRow] }),
      })

      expect(screen.getByText('Alice')).toBeTruthy()
      expect(screen.getByText('Utilisateur anonyme')).toBeTruthy()
    })

    it('shows empty state for the active tab', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 1,
        attendeesHook: makeHook({ attendees: [publicRow] }),
      })

      fireEvent.click(screen.getByRole('tab', { name: /Liste d'attente/ }))

      expect(screen.getByText("Personne en liste d'attente.")).toBeTruthy()
    })

    it('shows empty state for the participating tab when no attendees', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 0,
        attendeesHook: makeHook(),
      })

      expect(screen.getByText('Aucun participant pour le moment.')).toBeTruthy()
    })

    it('renders a loading skeleton on initial load', () => {
      const { container } = renderList({
        isAuthenticated: true,
        attendingCount: 0,
        attendeesHook: makeHook({ isLoading: true, hasMore: true }),
      })

      expect(container.querySelector('[aria-busy="true"]')).toBeTruthy()
    })

    it('renders error state with retry button that calls refetch (not loadMore)', () => {
      const loadMore = vi.fn()
      const refetch = vi.fn()
      renderList({
        isAuthenticated: true,
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
        isAuthenticated: true,
        attendingCount: 1,
        attendeesHook: makeHook({ attendees: [publicRow], hasMore: true, loadMore }),
      })

      const btn = screen.getByRole('button', { name: /Charger plus/ })
      fireEvent.click(btn)
      expect(loadMore).toHaveBeenCalled()
    })

    it('hides "Charger plus" when hasMore is false', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 1,
        attendeesHook: makeHook({ attendees: [publicRow] }),
      })

      expect(screen.queryByRole('button', { name: /Charger plus/ })).toBeNull()
    })

    it('disables "Charger plus" while loading another page', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 1,
        attendeesHook: makeHook({ attendees: [publicRow], isLoading: true, hasMore: true }),
      })

      const btn = screen.getByRole('button', { name: /Chargement/ }) as HTMLButtonElement
      expect(btn.disabled).toBe(true)
    })
  })
})
