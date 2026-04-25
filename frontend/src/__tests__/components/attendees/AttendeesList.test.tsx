// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'

vi.mock('@/hooks/useAttendees', () => ({
  useAttendees: vi.fn(),
}))

import AttendeesList from '@/components/attendees/AttendeesList'
import { useAttendees } from '@/hooks/useAttendees'

const mockUseAttendees = useAttendees as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const a1 = {
  attendance: {
    id: 1,
    userId: 'user-1',
    eventId: 42,
    status: 'ATTENDING' as const,
    createdAt: '2026-04-08T10:00:00.000Z',
  },
  profile: {
    id: 'user-1',
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
  },
  profile: {
    id: 'user-2',
    displayName: 'Bob',
    faculty: null,
    studyLevel: null,
  },
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
    it('renders summary with the count and does not call the hook', () => {
      renderList({ eventId: 42, isOrganizer: false, attendingCount: 12 })

      expect(screen.getByText('12 personnes participent')).toBeTruthy()
      expect(mockUseAttendees).not.toHaveBeenCalled()
    })

    it('uses singular form for one participant', () => {
      renderList({ eventId: 42, isOrganizer: false, attendingCount: 1 })

      expect(screen.getByText('1 personne participe')).toBeTruthy()
    })

    it('shows empty placeholder text when attendingCount is 0', () => {
      renderList({ eventId: 42, isOrganizer: false, attendingCount: 0 })

      expect(screen.getByText('Aucun participant pour le moment.')).toBeTruthy()
    })

    it('renders avatars and counter as a single inline row (compact)', () => {
      renderList({ eventId: 42, isOrganizer: false, attendingCount: 3 })

      const counter = screen.getByText('3 personnes participent')
      const row = counter.parentElement
      expect(row).not.toBeNull()
      // Same container holds both the avatar group and the counter span
      const avatars = row?.querySelector('[aria-hidden="true"]')
      expect(avatars).toBeTruthy()
      expect(row?.contains(counter)).toBe(true)
      expect(row?.contains(avatars as Node)).toBe(true)
      expect(mockUseAttendees).not.toHaveBeenCalled()
    })
  })

  describe('organizer view', () => {
    it('renders both tabs with counts and switches between them', () => {
      mockUseAttendees.mockReturnValue({
        attendees: [a1, a2],
        isLoading: false,
        error: null,
        hasMore: false,
        loadMore: vi.fn(),
        isForbidden: false,
      })

      renderList({ eventId: 42, isOrganizer: true, attendingCount: 1 })

      const attendingTab = screen.getByRole('tab', { name: /Participent/ })
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
      mockUseAttendees.mockReturnValue({
        attendees: [a1],
        isLoading: false,
        error: null,
        hasMore: false,
        loadMore: vi.fn(),
        isForbidden: false,
      })

      renderList({ eventId: 42, isOrganizer: true, attendingCount: 1 })

      fireEvent.click(screen.getByRole('tab', { name: /Liste d'attente/ }))

      expect(screen.getByText("Personne en liste d'attente.")).toBeTruthy()
    })

    it('shows empty state for the participating tab when no attendees', () => {
      mockUseAttendees.mockReturnValue({
        attendees: [],
        isLoading: false,
        error: null,
        hasMore: false,
        loadMore: vi.fn(),
        isForbidden: false,
      })

      renderList({ eventId: 42, isOrganizer: true, attendingCount: 0 })

      expect(screen.getByText('Aucun participant pour le moment.')).toBeTruthy()
    })

    it('renders a loading skeleton on initial load', () => {
      mockUseAttendees.mockReturnValue({
        attendees: [],
        isLoading: true,
        error: null,
        hasMore: true,
        loadMore: vi.fn(),
        isForbidden: false,
      })

      const { container } = renderList({ eventId: 42, isOrganizer: true, attendingCount: 0 })

      expect(container.querySelector('[aria-busy="true"]')).toBeTruthy()
    })

    it('renders error state with retry button that calls refetch (not loadMore)', () => {
      const loadMore = vi.fn()
      const refetch = vi.fn()
      mockUseAttendees.mockReturnValue({
        attendees: [],
        isLoading: false,
        error: new Error('boom'),
        hasMore: true,
        loadMore,
        refetch,
        isForbidden: false,
      })

      renderList({ eventId: 42, isOrganizer: true, attendingCount: 0 })

      expect(screen.getByText(/Impossible de charger la liste/)).toBeTruthy()
      fireEvent.click(screen.getByRole('button', { name: /Réessayer/ }))
      expect(refetch).toHaveBeenCalledTimes(1)
      expect(loadMore).not.toHaveBeenCalled()
    })

    it('shows "Charger plus" only when hasMore is true and calls loadMore on click', () => {
      const loadMore = vi.fn()
      mockUseAttendees.mockReturnValue({
        attendees: [a1],
        isLoading: false,
        error: null,
        hasMore: true,
        loadMore,
        isForbidden: false,
      })

      renderList({ eventId: 42, isOrganizer: true, attendingCount: 1 })

      const btn = screen.getByRole('button', { name: /Charger plus/ })
      fireEvent.click(btn)
      expect(loadMore).toHaveBeenCalled()
    })

    it('hides "Charger plus" when hasMore is false', () => {
      mockUseAttendees.mockReturnValue({
        attendees: [a1],
        isLoading: false,
        error: null,
        hasMore: false,
        loadMore: vi.fn(),
        isForbidden: false,
      })

      renderList({ eventId: 42, isOrganizer: true, attendingCount: 1 })

      expect(screen.queryByRole('button', { name: /Charger plus/ })).toBeNull()
    })

    it('disables "Charger plus" while loading another page', () => {
      mockUseAttendees.mockReturnValue({
        attendees: [a1],
        isLoading: true,
        error: null,
        hasMore: true,
        loadMore: vi.fn(),
        isForbidden: false,
      })

      renderList({ eventId: 42, isOrganizer: true, attendingCount: 1 })

      const btn = screen.getByRole('button', { name: /Chargement/ }) as HTMLButtonElement
      expect(btn.disabled).toBe(true)
    })

    it('falls back to summary when isForbidden is true', () => {
      mockUseAttendees.mockReturnValue({
        attendees: [],
        isLoading: false,
        error: null,
        hasMore: false,
        loadMore: vi.fn(),
        isForbidden: true,
      })

      renderList({ eventId: 42, isOrganizer: true, attendingCount: 7 })

      expect(screen.queryByRole('tab')).toBeNull()
      expect(screen.getByText('7 personnes participent')).toBeTruthy()
    })
  })
})
