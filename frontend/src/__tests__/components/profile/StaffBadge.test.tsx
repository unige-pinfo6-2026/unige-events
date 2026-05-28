// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import StaffBadge from '@/components/profile/StaffBadge'
import { isStaff, STAFF_ROLE } from '@/types/user'

afterEach(() => { cleanup() })

describe('StaffBadge — pill', () => {
  it('renders the "Staff" label with the warning-color chip styling', () => {
    render(<StaffBadge />)

    // Visible label + accessible name align so screen readers and the
    // visual chip stay in sync.
    const badge = screen.getByLabelText('Membre du staff')
    expect(badge.textContent).toContain('Staff')
    // Token-driven amber (see frontend/AGENTS.md — same as the Brouillon
    // chip on DraftResumeCard). Asserting the token classes catches
    // accidental "red is more visible" tweaks that would break the
    // shared design language.
    expect(badge.className).toContain('text-warning')
    expect(badge.className).toContain('border-warning/40')
  })
})

describe('isStaff — role guard', () => {
  it(`returns true when roles include ${STAFF_ROLE}`, () => {
    expect(isStaff([STAFF_ROLE])).toBe(true)
    expect(isStaff(['MODERATOR', STAFF_ROLE])).toBe(true)
  })

  it('returns false for an empty array, undefined, or null', () => {
    expect(isStaff([])).toBe(false)
    expect(isStaff(undefined)).toBe(false)
    expect(isStaff(null)).toBe(false)
  })

  it('returns false for unrelated roles (no false-positive on STUDENT)', () => {
    expect(isStaff(['STUDENT'])).toBe(false)
    expect(isStaff(['USER', 'STUDENT'])).toBe(false)
  })
})
