
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', () => ({
  useNavigate: () => mockNavigate,
}))

vi.mock('@/hooks/useAttendance', () => ({
  useAttendance: vi.fn(),
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

import { useAttendance } from '@/hooks/useAttendance'
import { useAuth } from '@/hooks/useAuth'
import AttendanceButtons from '@/components/event/AttendanceButtons'

const mockUseAttendance = vi.mocked(useAttendance)
const mockUseAuth = vi.mocked(useAuth)

afterEach(() => {
  cleanup()
  mockNavigate.mockClear()
  mockUseAttendance.mockClear()
})

function makeHookResult(overrides: Partial<ReturnType<typeof useAttendance>> = {}): ReturnType<typeof useAttendance> {
  const base = {
    currentStatus: null as ReturnType<typeof useAttendance>['currentStatus'],
    attendingCount: 5,
    loading: false,
    mutating: false,
    error: null,
    isFull: false,
    displayStatus: null as ReturnType<typeof useAttendance>['displayStatus'],
    displayIsFull: false,
    toggle: vi.fn(),
  }
  const merged = { ...base, ...overrides }
  // Mirror display* to current* unless caller overrode them explicitly — keeps
  // existing tests legible (they only set currentStatus / isFull).
  if (!('displayStatus' in overrides)) merged.displayStatus = merged.currentStatus
  if (!('displayIsFull' in overrides)) merged.displayIsFull = merged.isFull
  return merged
}

const defaultProps = {
  eventId: 42,
  initialAttendingCount: 5,
  initialStatus: null,
} as const

describe('AttendanceButtons', () => {
  describe('when event is not full', () => {
    beforeEach(() => {
      mockUseAttendance.mockReturnValue(makeHookResult())
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
    })

    it('renders the attending button enabled', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button', { name: /je participe/i })
      expect(button).not.toBeNull()
      expect((button as HTMLButtonElement).disabled).toBe(false)
    })

    it('button has no aria-disabled', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button', { name: /je participe/i })
      expect(button.hasAttribute('aria-disabled')).toBe(false)
    })
  })

  describe('when event is full and user is not registered', () => {
    beforeEach(() => {
      mockUseAttendance.mockReturnValue(makeHookResult({ isFull: true, currentStatus: null }))
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
    })

    it("shows \"Rejoindre la liste d'attente\" button", () => {
      render(<AttendanceButtons {...defaultProps} />)

      expect(screen.getByRole('button', { name: /rejoindre la liste d'attente/i })).not.toBeNull()
    })

    it('join waitlist button is enabled and clickable', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button', { name: /rejoindre la liste d'attente/i })
      expect((button as HTMLButtonElement).disabled).toBe(false)
    })

    it('clicking join waitlist calls toggle', () => {
      const toggle = vi.fn()
      mockUseAttendance.mockReturnValue(makeHookResult({ isFull: true, currentStatus: null, toggle }))

      render(<AttendanceButtons {...defaultProps} />)
      fireEvent.click(screen.getByRole('button', { name: /rejoindre la liste d'attente/i }))

      expect(toggle).toHaveBeenCalledWith('ATTENDING')
    })
  })

  describe('when user is waitlisted', () => {
    beforeEach(() => {
      mockUseAttendance.mockReturnValue(makeHookResult({ isFull: true, currentStatus: 'WAITLISTED' }))
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
    })

    it("shows \"Quitter la liste d'attente\" button", () => {
      render(<AttendanceButtons {...defaultProps} />)

      expect(screen.getByRole('button', { name: /quitter la liste d'attente/i })).not.toBeNull()
    })

    it("clicking \"Quitter la liste d'attente\" calls toggle to leave waitlist", () => {
      const toggle = vi.fn()
      mockUseAttendance.mockReturnValue(makeHookResult({ isFull: true, currentStatus: 'WAITLISTED', toggle }))

      render(<AttendanceButtons {...defaultProps} />)
      fireEvent.click(screen.getByRole('button', { name: /quitter la liste d'attente/i }))

      expect(toggle).toHaveBeenCalledWith('ATTENDING')
    })
  })

  describe('when event is full but user is already attending', () => {
    it('attending button shows "Annuler ma participation" active state', () => {
      mockUseAttendance.mockReturnValue(
        makeHookResult({ isFull: true, currentStatus: 'ATTENDING' }),
      )
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} />)

      expect(screen.getByRole('button', { name: /annuler ma participation/i })).not.toBeNull()
    })

    it('attending button is enabled (user can toggle off)', () => {
      mockUseAttendance.mockReturnValue(
        makeHookResult({ isFull: true, currentStatus: 'ATTENDING' }),
      )
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button', { name: /annuler ma participation/i })
      expect((button as HTMLButtonElement).disabled).toBe(false)
    })

    it('clicking the active attending button calls toggle to unattend', () => {
      const toggle = vi.fn()
      mockUseAttendance.mockReturnValue(
        makeHookResult({ isFull: false, currentStatus: 'ATTENDING', toggle }),
      )
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} />)
      fireEvent.click(screen.getByRole('button', { name: /annuler ma participation/i }))

      expect(toggle).toHaveBeenCalledWith('ATTENDING')
    })
  })

  describe('disabled / mutation affordance', () => {
    it('button is disabled while loading is true', () => {
      mockUseAttendance.mockReturnValue(makeHookResult({ loading: true }))
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} />)
      const button = screen.getByRole('button', { name: /je participe/i })
      expect((button as HTMLButtonElement).disabled).toBe(true)
    })

    it('forwards onAfterSuccess into useAttendance options', () => {
      const onAfterSuccess = vi.fn()
      mockUseAttendance.mockReturnValue(makeHookResult())
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} onAfterSuccess={onAfterSuccess} />)

      expect(mockUseAttendance).toHaveBeenCalledWith(42, 5, null, undefined, { onAfterSuccess })
    })

    it('shows aria-busy="true", a spinner and wait cursor while mutating', () => {
      mockUseAttendance.mockReturnValue(
        makeHookResult({ mutating: true, loading: true }),
      )
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button')
      expect(button.getAttribute('aria-busy')).toBe('true')
      expect((button as HTMLButtonElement).style.cursor).toBe('wait')
      expect(button.querySelector('.animate-spin')).toBeTruthy()
    })

    it('hides aria-busy and the spinner when not mutating', () => {
      mockUseAttendance.mockReturnValue(makeHookResult())
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button')
      expect(button.getAttribute('aria-busy')).toBeNull()
      expect(button.querySelector('.animate-spin')).toBeNull()
    })
  })

  describe('frozen label during mutation', () => {
    it('displays the snapshotted label while mutating, ignoring underlying status changes', () => {
      // Full event, user not registered → label should be the waitlist-join one.
      mockUseAttendance.mockReturnValue(
        makeHookResult({
          mutating: true,
          loading: true,
          currentStatus: 'ATTENDING', // optimistic — would normally show "Annuler ma participation"
          isFull: true,
          displayStatus: null,        // snapshot at click time → "Rejoindre la liste d'attente"
          displayIsFull: true,
        }),
      )
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} />)

      // The label rendered must come from display*, NOT current*.
      expect(screen.getByRole('button', { name: /rejoindre la liste d'attente/i })).toBeTruthy()
      expect(screen.queryByRole('button', { name: /annuler ma participation/i })).toBeNull()
    })

    it('settles on the new label only after mutating clears (label changes at most once)', () => {
      // First render: mutating=true, frozen on "Je participe".
      mockUseAttendance.mockReturnValue(
        makeHookResult({
          mutating: true,
          loading: true,
          currentStatus: 'ATTENDING',
          isFull: false,
          displayStatus: null,
          displayIsFull: false,
        }),
      )
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      const { rerender } = render(<AttendanceButtons {...defaultProps} />)
      const labels: string[] = []
      labels.push(screen.getByRole('button').textContent ?? '')

      // Second render: mutation cleared, display* now match live state.
      mockUseAttendance.mockReturnValue(
        makeHookResult({
          mutating: false,
          loading: false,
          currentStatus: 'ATTENDING',
          isFull: false,
          displayStatus: 'ATTENDING',
          displayIsFull: false,
        }),
      )
      rerender(<AttendanceButtons {...defaultProps} />)
      labels.push(screen.getByRole('button').textContent ?? '')

      // Exactly one label transition: "Je participe" → "Annuler ma participation".
      // No third intermediate value.
      expect(labels[0]).toContain('Je participe')
      expect(labels[1]).toContain('Annuler ma participation')
    })
  })

  describe('counts display', () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
    })

    it('shows attending count correctly', () => {
      mockUseAttendance.mockReturnValue(makeHookResult({ attendingCount: 12 }))

      render(<AttendanceButtons {...defaultProps} />)

      screen.getByText(/12 personnes participent/)
    })

    it('uses singular form for 1 attendee', () => {
      mockUseAttendance.mockReturnValue(makeHookResult({ attendingCount: 1 }))

      render(<AttendanceButtons {...defaultProps} />)

      screen.getByText(/1 personne participe/)
    })
  })

  describe('error display', () => {
    it('shows error message when error is set', () => {
      mockUseAttendance.mockReturnValue(makeHookResult({ error: 'Une erreur est survenue.' }))
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} />)

      expect(screen.getByText('Une erreur est survenue.')).toBeTruthy()
    })
  })

  describe('unauthenticated user', () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({ isAuthenticated: false } as ReturnType<typeof useAuth>)
      mockUseAttendance.mockReturnValue(makeHookResult())
    })

    it('clicking "Je participe" navigates to /login without calling toggle', () => {
      render(<AttendanceButtons {...defaultProps} />)
      const toggle = mockUseAttendance.mock.results[0].value.toggle

      fireEvent.click(screen.getByRole('button', { name: /je participe/i }))

      expect(mockNavigate).toHaveBeenCalledWith('/login')
      expect(toggle).not.toHaveBeenCalled()
    })
  })

  describe('authenticated user', () => {
    beforeEach(() => {
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
      mockUseAttendance.mockReturnValue(makeHookResult())
    })

    it('clicking "Je participe" calls toggle, no redirect', () => {
      render(<AttendanceButtons {...defaultProps} />)
      const toggle = mockUseAttendance.mock.results[0].value.toggle

      fireEvent.click(screen.getByRole('button', { name: /je participe/i }))

      expect(toggle).toHaveBeenCalledWith('ATTENDING')
      expect(mockNavigate).not.toHaveBeenCalled()
    })
  })
})
