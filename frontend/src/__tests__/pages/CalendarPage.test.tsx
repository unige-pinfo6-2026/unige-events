// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import { CalendarPage } from '@/pages/calendar/CalendarPage'

afterEach(() => cleanup())

describe('CalendarPage', () => {
  it('renders without crashing', () => {
    const { container } = render(<CalendarPage />)
    expect(container).toBeTruthy()
  })
})
