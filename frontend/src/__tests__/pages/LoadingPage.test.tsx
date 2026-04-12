// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import LoadingPage from '@/pages/LoadingPage'

afterEach(() => { cleanup() })

describe('LoadingPage', () => {
  it('renders the loading spinner', () => {
    const { container } = render(<LoadingPage />)
    expect(container.querySelector('.animate-spin')).toBeTruthy()
  })
})
