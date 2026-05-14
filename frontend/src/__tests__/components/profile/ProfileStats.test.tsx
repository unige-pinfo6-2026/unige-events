// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import ProfileStats from '@/components/profile/ProfileStats'

afterEach(() => { cleanup() })

describe('ProfileStats', () => {
  it('renders both counters with their values formatted in fr-CH', () => {
    render(<ProfileStats followerCount={1234} followingCount={56} />)

    // fr-CH thin space (U+202F) between thousands.
    expect(screen.getByText(/1.234/)).toBeTruthy()
    expect(screen.getByText('56')).toBeTruthy()
  })

  it('uses the singular "follower" when followerCount === 1', () => {
    render(<ProfileStats followerCount={1} followingCount={5} />)

    expect(screen.getByText('follower')).toBeTruthy()
  })

  it('uses the plural "followers" otherwise', () => {
    render(<ProfileStats followerCount={2} followingCount={0} />)

    expect(screen.getByText('followers')).toBeTruthy()
  })

  it('renders 0 / 0 for new accounts', () => {
    render(<ProfileStats followerCount={0} followingCount={0} />)

    expect(screen.getAllByText('0').length).toBe(2)
  })

  it('is labelled as a counters group for assistive tech', () => {
    render(<ProfileStats followerCount={1} followingCount={1} />)

    expect(screen.getByLabelText('Compteurs de suivi')).toBeTruthy()
  })
})
