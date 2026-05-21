import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import EventBanner from '@/components/event/EventBanner'
import type { Event } from '@/types/event'

afterEach(() => cleanup())

const makeEvent = (overrides: Partial<Event> = {}): Event => ({
  id: 1,
  title: 'Test Event',
  location: 'Geneva',
  startDate: '2026-06-01T10:00:00',
  endDate: '2026-06-01T12:00:00',
  allDay: false,
  category: 'CONFERENCE',
  faculty: null,
  status: 'PUBLISHED',
  creatorId: 'user-1',
  createdAt: '2026-05-01T10:00:00',
  description: '',
  capacity: undefined,
  attendingCount: 0,
  bannerUrl: undefined,
  ...overrides,
})

describe('EventBanner', () => {
  it('renders a gradient background when bannerUrl is absent', () => {
    const { container } = render(<EventBanner event={makeEvent()} />)
    const div = container.firstChild as HTMLElement
    expect(div.style.background).toContain('linear-gradient')
    expect(document.querySelector('img')).toBeNull()
  })

  it('renders an img when bannerUrl is present and always keeps gradient as background', () => {
    const { container } = render(
      <EventBanner event={makeEvent({ bannerUrl: 'https://example.com/banner.jpg' })} />,
    )
    const img = document.querySelector('img')
    expect(img).toBeTruthy()
    expect(img?.src).toContain('banner.jpg')
    // Gradient is always applied so the banner never appears black while the image loads.
    expect((container.firstChild as HTMLElement).style.background).toContain('linear-gradient')
  })

  it('hides the img after onError and keeps the gradient visible', () => {
    const { container } = render(
      <EventBanner event={makeEvent({ bannerUrl: 'https://example.com/banner.jpg' })} />,
    )
    const img = document.querySelector('img')!
    expect(img).toBeTruthy()

    fireEvent.error(img)

    expect(document.querySelector('img')).toBeNull()
    expect((container.firstChild as HTMLElement).style.background).toContain('linear-gradient')
  })

  it('applies the className prop to the wrapper', () => {
    const { container } = render(<EventBanner event={makeEvent()} className="custom-class" />)
    expect((container.firstChild as HTMLElement).classList.contains('custom-class')).toBe(true)
  })

  it('renders children inside the banner', () => {
    render(
      <EventBanner event={makeEvent()}>
        <span>Banner child</span>
      </EventBanner>,
    )
    expect(screen.getByText('Banner child')).toBeTruthy()
  })
})
