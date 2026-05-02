
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
  return {
    currentStatus: null,
    attendingCount: 5,
    loading: false,
    error: null,
    isFull: false,
    toggle: vi.fn(),
    ...overrides,
  }
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

    it('shows "Rejoindre la liste d\'attente" button', () => {
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

    it('shows "En liste d\'attente" button', () => {
      render(<AttendanceButtons {...defaultProps} />)

      expect(screen.getByRole('button', { name: /en liste d'attente/i })).not.toBeNull()
    })

    it('clicking "En liste d\'attente" calls toggle to leave waitlist', () => {
      const toggle = vi.fn()
      mockUseAttendance.mockReturnValue(makeHookResult({ isFull: true, currentStatus: 'WAITLISTED', toggle }))

      render(<AttendanceButtons {...defaultProps} />)
      fireEvent.click(screen.getByRole('button', { name: /en liste d'attente/i }))

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
