// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import ProfilePrivateState from '@/components/profile/ProfilePrivateState'

afterEach(() => { cleanup() })

describe('ProfilePrivateState', () => {
  it('renders the privacy copy with the lock icon', () => {
    render(<ProfilePrivateState />)

    expect(screen.getByRole('heading', { name: 'Ce profil est privé' })).toBeTruthy()
    expect(screen.getByText(/Cet utilisateur a choisi de ne pas rendre son profil public/)).toBeTruthy()
  })

  it('shows the "Demande de suivi envoyée" badge when followStatus is PENDING', () => {
    render(<ProfilePrivateState followStatus="PENDING" />)

    expect(screen.getByText('Demande de suivi envoyée')).toBeTruthy()
  })

  it('does NOT show the badge when followStatus is null', () => {
    render(<ProfilePrivateState followStatus={null} />)

    expect(screen.queryByText('Demande de suivi envoyée')).toBeNull()
  })

  it('does NOT show the badge when followStatus is ACCEPTED (the page should not have routed here)', () => {
    render(<ProfilePrivateState followStatus="ACCEPTED" />)

    expect(screen.queryByText('Demande de suivi envoyée')).toBeNull()
  })

  it('does NOT show the badge when followStatus is undefined', () => {
    render(<ProfilePrivateState />)

    expect(screen.queryByText('Demande de suivi envoyée')).toBeNull()
  })
})
