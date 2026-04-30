// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import WaitlistBadge from '@/components/attendees/WaitlistBadge'

afterEach(() => { cleanup() })

describe('WaitlistBadge', () => {
  it('renders "Liste d\'attente"', () => {
    render(<WaitlistBadge />)
    expect(screen.getByText("Liste d'attente")).toBeTruthy()
  })
})
