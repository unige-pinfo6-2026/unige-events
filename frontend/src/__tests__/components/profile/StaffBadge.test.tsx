// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import StaffBadge from '@/components/profile/StaffBadge'
import { isStaff, STAFF_ROLE } from '@/types/user'

afterEach(() => { cleanup() })

describe('StaffBadge — pill', () => {
  it('renders the "Staff" label with the blue (link-token) chip styling', () => {
    render(<StaffBadge />)

    // Visible label + accessible name align so screen readers and the
    // visual chip stay in sync.
    const badge = screen.getByLabelText('Membre du staff')
    expect(badge.textContent).toContain('Staff')
    // Token-driven blue (see frontend/AGENTS.md — `--color-link`, le seul
    // token bleu du thème). Asserting the token classes catches accidental
    // reverts to the previous amber styling.
    expect(badge.className).toContain('text-link')
    expect(badge.className).toContain('border-link/40')
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
