// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ProfileStats from '@/components/profile/ProfileStats'

afterEach(() => { cleanup() })

function renderStats(props: { followerCount: number; followingCount: number; linkUsername?: string }) {
  return render(
    <MemoryRouter>
      <ProfileStats {...props} />
    </MemoryRouter>,
  )
}

describe('ProfileStats', () => {
  it('renders both counters with their values formatted in fr-CH', () => {
    renderStats({ followerCount: 1234, followingCount: 56 })

    // fr-CH thin space (U+202F) between thousands.
    expect(screen.getByText(/1.234/)).toBeTruthy()
    expect(screen.getByText('56')).toBeTruthy()
  })

  it('uses the singular "follower" when followerCount === 1', () => {
    renderStats({ followerCount: 1, followingCount: 5 })

    expect(screen.getByText('follower')).toBeTruthy()
  })

  it('uses the plural "followers" otherwise', () => {
    renderStats({ followerCount: 2, followingCount: 0 })

    expect(screen.getByText('followers')).toBeTruthy()
  })

  it('renders 0 / 0 for new accounts', () => {
    renderStats({ followerCount: 0, followingCount: 0 })

    expect(screen.getAllByText('0').length).toBe(2)
  })

  it('is labelled as a counters group for assistive tech', () => {
    renderStats({ followerCount: 1, followingCount: 1 })

    expect(screen.getByLabelText('Compteurs de suivi')).toBeTruthy()
  })

  it('renders plain tiles (no links) when linkUsername is omitted', () => {
    renderStats({ followerCount: 3, followingCount: 4 })

    expect(screen.queryByRole('link')).toBeNull()
  })

  it('renders linked tiles when linkUsername is provided', () => {
    renderStats({ followerCount: 3, followingCount: 4, linkUsername: 'alice' })

    const links = screen.getAllByRole('link')
    expect(links).toHaveLength(2)
    expect(links[0].getAttribute('href')).toBe('/profile/alice/followers')
    expect(links[1].getAttribute('href')).toBe('/profile/alice/following')
  })

  it('the linked tiles expose accessible labels matching the counts', () => {
    renderStats({ followerCount: 7, followingCount: 12, linkUsername: 'bob' })

    expect(screen.getByLabelText('Voir les followers (7)')).toBeTruthy()
    expect(screen.getByLabelText('Voir les abonnements (12)')).toBeTruthy()
  })
})
