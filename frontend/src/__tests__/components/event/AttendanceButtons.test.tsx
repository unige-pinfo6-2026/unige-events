// @vitest-environment jsdom

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

    it('tooltip div is always in the DOM and hidden via class', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const tooltip = document.getElementById(`attending-full-tooltip-${defaultProps.eventId}`)
      expect(tooltip).not.toBeNull()
      expect(tooltip?.className).toContain('hidden')
    })

    it('attending button has no aria-describedby on wrapper', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button', { name: /je participe/i })
      const wrapper = button.parentElement
      expect(wrapper?.hasAttribute('aria-describedby')).toBe(false)
    })
  })

  describe('when event is full and user is not attending', () => {
    beforeEach(() => {
      mockUseAttendance.mockReturnValue(makeHookResult({ isFull: true, currentStatus: null }))
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)
    })

    it('tooltip element is in the DOM', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const tooltip = document.getElementById(`attending-full-tooltip-${defaultProps.eventId}`)
      expect(tooltip).not.toBeNull()
    })

    it('tooltip element contains the correct text', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const tooltip = document.getElementById(`attending-full-tooltip-${defaultProps.eventId}`)
      expect(tooltip?.textContent?.trim()).toBe('Événement complet')
    })

    it('wrapper div has aria-describedby pointing to tooltip id', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button', { name: /je participe/i })
      const wrapper = button.parentElement
      expect(wrapper?.getAttribute('aria-describedby')).toBe(`attending-full-tooltip-${defaultProps.eventId}`)
    })

    it('attending button is not truly disabled but has aria-disabled', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button', { name: /je participe/i })
      expect((button as HTMLButtonElement).disabled).toBe(false)
      expect(button.getAttribute('aria-disabled')).toBe('true')
    })

    it('attending button is focusable when isFull is true', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button', { name: /je participe/i })
      expect((button as HTMLButtonElement).disabled).toBe(false)
    })

    it('tooltip uses opacity classes (not hidden) when full', () => {
      render(<AttendanceButtons {...defaultProps} />)

      const tooltip = document.getElementById(`attending-full-tooltip-${defaultProps.eventId}`)
      expect(tooltip?.className).not.toContain('hidden')
      expect(tooltip?.className).toContain('opacity-0')
    })
  })

  describe('when event is full but user is already attending', () => {
    it('attending button is not disabled (user can toggle off)', () => {
      mockUseAttendance.mockReturnValue(
        makeHookResult({ isFull: true, currentStatus: 'ATTENDING' }),
      )
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button', { name: /je participe/i })
      expect((button as HTMLButtonElement).disabled).toBe(false)
    })

    it('attending button wrapper has no aria-describedby', () => {
      mockUseAttendance.mockReturnValue(
        makeHookResult({ isFull: true, currentStatus: 'ATTENDING' }),
      )
      mockUseAuth.mockReturnValue({ isAuthenticated: true } as ReturnType<typeof useAuth>)

      render(<AttendanceButtons {...defaultProps} />)

      const button = screen.getByRole('button', { name: /je participe/i })
      const wrapper = button.parentElement
      expect(wrapper?.hasAttribute('aria-describedby')).toBe(false)
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
