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
  username: 'alice',
}

const waitlistedRow: Attendance = {
  id: 2,
  userId: 'user-2',
  eventId: 42,
  status: 'WAITLISTED',
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: 'Bob',
  avatarUrl: null,
  username: 'bob',
}

const anonymizedRow: Attendance = {
  id: 3,
  userId: null,
  eventId: 42,
  status: 'ATTENDING',
  createdAt: '2026-04-08T10:00:00.000Z',
  displayName: null,
  avatarUrl: null,
  username: null,
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

    // ── "Voir tout" expander (INITIAL_VISIBLE = 5) ────────────────────────────

    function makeAttendingRows(n: number): Attendance[] {
      return Array.from({ length: n }, (_, i) => ({
        id: i + 1,
        userId: `user-${i + 1}`,
        eventId: 42,
        status: 'ATTENDING' as const,
        createdAt: '2026-04-08T10:00:00.000Z',
        displayName: `Participant ${i + 1}`,
        avatarUrl: null,
        username: `user${i + 1}`,
      }))
    }

    it('shows at most 5 cards when the active tab has more than 5 attendees', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 8,
        attendeesHook: makeHook({ attendees: makeAttendingRows(8) }),
      })

      // Only the first 5 names visible ; 6/7/8 hidden behind the expander.
      expect(screen.getByText('Participant 1')).toBeTruthy()
      expect(screen.getByText('Participant 5')).toBeTruthy()
      expect(screen.queryByText('Participant 6')).toBeNull()
      expect(screen.queryByText('Participant 8')).toBeNull()
      expect(screen.getByRole('button', { name: /Voir tout \(3 de plus\)/ })).toBeTruthy()
    })

    it('does NOT show the expander when the list has exactly 5 or fewer items', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 5,
        attendeesHook: makeHook({ attendees: makeAttendingRows(5) }),
      })

      expect(screen.queryByRole('button', { name: /Voir tout/ })).toBeNull()
      // All 5 rendered.
      for (let i = 1; i <= 5; i++) {
        expect(screen.getByText(`Participant ${i}`)).toBeTruthy()
      }
    })

    it('reveals the rest of the list when "Voir tout" is clicked, and shows a "Réduire" button', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 8,
        attendeesHook: makeHook({ attendees: makeAttendingRows(8) }),
      })

      fireEvent.click(screen.getByRole('button', { name: /Voir tout/ }))

      // All 8 rows visible.
      for (let i = 1; i <= 8; i++) {
        expect(screen.getByText(`Participant ${i}`)).toBeTruthy()
      }
      // « Voir tout » a été remplacé par « Réduire ».
      expect(screen.queryByRole('button', { name: /Voir tout/ })).toBeNull()
      expect(screen.getByRole('button', { name: /Réduire/ })).toBeTruthy()
    })

    it('collapses back to 5 cards when "Réduire" is clicked', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 8,
        attendeesHook: makeHook({ attendees: makeAttendingRows(8) }),
      })

      fireEvent.click(screen.getByRole('button', { name: /Voir tout/ }))
      fireEvent.click(screen.getByRole('button', { name: /Réduire/ }))

      expect(screen.getByText('Participant 5')).toBeTruthy()
      expect(screen.queryByText('Participant 6')).toBeNull()
      expect(screen.getByRole('button', { name: /Voir tout/ })).toBeTruthy()
    })

    it('keeps a separate collapsed/expanded state per tab', () => {
      // 8 ATTENDING + 8 WAITLISTED. Expanding ATTENDING must not auto-expand WAITLISTED.
      const attending = makeAttendingRows(8)
      const waitlisted = makeAttendingRows(8).map((a) => ({
        ...a,
        id: a.id + 100,
        status: 'WAITLISTED' as const,
        displayName: `Waitlist ${a.id}`,
      }))
      renderList({
        isAuthenticated: true,
        attendingCount: 8,
        attendeesHook: makeHook({ attendees: [...attending, ...waitlisted] }),
      })

      // Expand ATTENDING.
      fireEvent.click(screen.getByRole('button', { name: /Voir tout/ }))
      expect(screen.queryByText('Participant 6')).toBeTruthy()

      // Switch to WAITLISTED — should be COLLAPSED (« Voir tout » re-appears).
      fireEvent.click(screen.getByRole('tab', { name: /Liste d'attente/ }))
      expect(screen.getByRole('button', { name: /Voir tout/ })).toBeTruthy()
      expect(screen.getByText('Waitlist 5')).toBeTruthy()
      expect(screen.queryByText('Waitlist 6')).toBeNull()
    })

    it('hides "Charger plus" while collapsed even when hasMore is true', () => {
      renderList({
        isAuthenticated: true,
        attendingCount: 8,
        attendeesHook: makeHook({ attendees: makeAttendingRows(8), hasMore: true }),
      })

      // Collapsed → only « Voir tout » should be visible, not « Charger plus ».
      expect(screen.getByRole('button', { name: /Voir tout/ })).toBeTruthy()
      expect(screen.queryByRole('button', { name: /Charger plus/ })).toBeNull()

      // After expanding, « Charger plus » re-appears next to « Réduire ».
      fireEvent.click(screen.getByRole('button', { name: /Voir tout/ }))
      expect(screen.getByRole('button', { name: /Charger plus/ })).toBeTruthy()
      expect(screen.getByRole('button', { name: /Réduire/ })).toBeTruthy()
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
