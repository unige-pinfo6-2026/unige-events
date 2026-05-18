// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import ProfilePrivateState from '@/components/profile/ProfilePrivateState'
import type { UserPublicResponse } from '@/types/user'

afterEach(() => { cleanup() })

const privateProfile: UserPublicResponse = {
  id: 'u-1',
  username: 'jane.doe',
  displayName: 'Jane Doe',
  // Restricted projection — bio / faculty / studyLevel / interests / bannerUrl
  // are stripped by the backend (SCRUM-169 Décision E revised).
  faculty: null,
  studyLevel: null,
  bio: null,
  interests: [],
  avatarUrl: null,
  bannerUrl: null,
  profilePublic: false,
  followerCount: 0,
  followingCount: 0,
  followStatus: null,
}

describe('ProfilePrivateState — locked view (with restricted profile)', () => {
  it('renders the displayName and the "Compte privé" lock heading', () => {
    render(<ProfilePrivateState profile={privateProfile} />)

    expect(screen.getByRole('heading', { level: 1, name: 'Jane Doe' })).toBeTruthy()
    expect(screen.getByRole('heading', { level: 2, name: 'Compte privé' })).toBeTruthy()
  })

  it('does NOT render bio, faculté, niveau d\'étude, counters, events or participations', () => {
    const profileWithExtras = {
      ...privateProfile,
      bio: 'Cette bio ne devrait JAMAIS apparaître',
      faculty: 'SCIENCES',
      studyLevel: 'MASTER',
      interests: ['Jazz'],
    }
    render(<ProfilePrivateState profile={profileWithExtras} />)

    expect(screen.queryByText('Cette bio ne devrait JAMAIS apparaître')).toBeNull()
    expect(screen.queryByText('Jazz')).toBeNull()
    expect(screen.queryByLabelText('Compteurs de suivi')).toBeNull()
    expect(screen.queryByRole('heading', { name: 'Événements organisés' })).toBeNull()
    expect(screen.queryByRole('heading', { name: 'Participations publiques' })).toBeNull()
    expect(screen.queryByText('À propos')).toBeNull()
  })

  it('does NOT render any FollowButton', () => {
    render(<ProfilePrivateState profile={privateProfile} />)

    expect(screen.queryByRole('button', { name: /Suivre|Demande envoyée|Abonné|Se désabonner|Annuler la demande/ })).toBeNull()
  })

  it('does NOT render the "Demande de suivi envoyée" PENDING badge even when followStatus is PENDING', () => {
    render(<ProfilePrivateState profile={{ ...privateProfile, followStatus: 'PENDING' }} />)

    expect(screen.queryByText('Demande de suivi envoyée')).toBeNull()
    expect(screen.getByRole('heading', { level: 2, name: 'Compte privé' })).toBeTruthy()
  })

  it('renders the gradient fallback banner when bannerUrl is null', () => {
    const { container } = render(<ProfilePrivateState profile={privateProfile} />)

    expect(container.querySelector('img[src]')).toBeNull()
    expect(container.querySelector('.bg-linear-to-br')).toBeTruthy()
  })

  it('renders the banner image when bannerUrl is set (owner self-view edge case)', () => {
    const { container } = render(
      <ProfilePrivateState profile={{ ...privateProfile, bannerUrl: 'https://example.com/banner.jpg' }} />,
    )

    expect(container.querySelector<HTMLImageElement>('img[src*="banner.jpg"]')).toBeTruthy()
  })

  it('renders avatar initials fallback when avatarUrl is null', () => {
    render(<ProfilePrivateState profile={privateProfile} />)

    expect(screen.getByText('JD')).toBeTruthy()
  })

  it('renders the avatar image when avatarUrl is set', () => {
    const { container } = render(
      <ProfilePrivateState profile={{ ...privateProfile, avatarUrl: 'https://example.com/avatar.jpg' }} />,
    )

    expect(container.querySelector<HTMLImageElement>('img[src*="avatar.jpg"]')).toBeTruthy()
  })
})

describe('ProfilePrivateState — fallback (no profile available, 404 case)', () => {
  it('renders the lock heading without crashing when no profile is provided', () => {
    render(<ProfilePrivateState />)

    expect(screen.getByRole('heading', { level: 2, name: 'Compte privé' })).toBeTruthy()
    expect(screen.queryByRole('heading', { level: 1 })).toBeNull()
  })

  it('renders the gradient fallback banner when no profile is provided', () => {
    const { container } = render(<ProfilePrivateState />)

    expect(container.querySelector('img[src]')).toBeNull()
    expect(container.querySelector('.bg-linear-to-br')).toBeTruthy()
  })

  it('does NOT render the "Demande de suivi envoyée" PENDING badge', () => {
    render(<ProfilePrivateState />)

    expect(screen.queryByText('Demande de suivi envoyée')).toBeNull()
  })
})
