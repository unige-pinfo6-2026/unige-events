// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import EventsPage from '@/pages/event/EventsPage'

afterEach(() => cleanup())

describe('EventsPage', () => {
  it('renders without crashing', () => {
    const { container } = render(<EventsPage />)
    expect(container).toBeTruthy()
  })
})
