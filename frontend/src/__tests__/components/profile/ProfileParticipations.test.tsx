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

    // The copy intentionally signals "not implemented yet" rather than
    // "no participations" — accounts that DO have public participations
    // would otherwise be misrepresented as having none.
    expect(screen.getByText('Bientôt disponible.')).toBeTruthy()
    expect(screen.getByText(/dès qu'elle sera prise en charge par le backend/)).toBeTruthy()
  })
})
