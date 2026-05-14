// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import ProfileParticipations from '@/components/profile/ProfileParticipations'

afterEach(() => { cleanup() })

describe('ProfileParticipations', () => {
  it('renders the heading', () => {
    render(<ProfileParticipations />)

    expect(screen.getByRole('heading', { name: 'Participations publiques' })).toBeTruthy()
  })

  it('renders the placeholder copy until the backend endpoint lands', () => {
    render(<ProfileParticipations />)

    expect(screen.getByText('Aucune participation publique à afficher.')).toBeTruthy()
  })
})
