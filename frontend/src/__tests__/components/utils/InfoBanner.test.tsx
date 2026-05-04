import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import InfoBanner from '@/components/utils/InfoBanner'

afterEach(() => {
  cleanup()
})

describe('InfoBanner', () => {
  it('renders a warning banner with the provided message', () => {
    const { container } = render(<InfoBanner type="warning" message="Version de prévisualisation." />)

    expect(screen.getByText('Version de prévisualisation.')).toBeTruthy()
    expect(container.firstElementChild?.className).toContain('bg-yellow-300')
  })
})
