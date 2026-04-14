// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { LoadingSpinner } from '@/components/utils/LoadingSpinner'

afterEach(() => { cleanup() })

describe('LoadingSpinner', () => {
  it('renders the spin element', () => {
    const { container } = render(<LoadingSpinner />)
    expect(container.querySelector('.animate-spin')).toBeTruthy()
  })

  it('renders children when provided', () => {
    render(<LoadingSpinner><span>Chargement...</span></LoadingSpinner>)
    expect(screen.getByText('Chargement...')).toBeTruthy()
  })

  it('renders without children', () => {
    const { container } = render(<LoadingSpinner />)
    expect(container.firstChild).toBeTruthy()
  })
})
