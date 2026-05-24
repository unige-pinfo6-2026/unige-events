
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import LandingPage from '@/pages/LandingPage'

vi.mock('@/hooks/useFeaturedEvents', () => ({
  useFeaturedEvents: vi.fn(),
}))

vi.mock('@/components/faculty/FacultyMarquee', () => ({
  default: () => <div>FacultyMarquee</div>,
}))

vi.mock('@/components/event/FeaturedEventsSection', () => ({
  default: () => <div data-testid="featured-events-section">FeaturedEventsSection</div>,
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({ user: null, login: vi.fn() })),
}))

import { useFeaturedEvents } from '@/hooks/useFeaturedEvents'

const mockUseFeaturedEvents = useFeaturedEvents as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderPage() {
  mockUseFeaturedEvents.mockReturnValue({ events: [], loading: false, error: null })
  return render(
    <MemoryRouter>
      <LandingPage />
    </MemoryRouter>,
  )
}

describe('LandingPage', () => {
  it('renders without crashing', () => {
    const { container } = renderPage()
    expect(container).toBeTruthy()
  })

  it('renders FeaturedEventsSection', () => {
    renderPage()
    expect(screen.getByTestId('featured-events-section')).toBeTruthy()
  })

  it('does not render the legacy "Événements à venir" heading', () => {
    renderPage()
    expect(screen.queryByText('Événements à venir')).toBeNull()
  })

  it('scrolls to hash section with navbar offset', () => {
    mockUseFeaturedEvents.mockReturnValue({ events: [], loading: false, error: null })
    vi.useFakeTimers()
    const mockScrollTo = vi.fn()
    globalThis.scrollTo = mockScrollTo
    const mockEl = { getBoundingClientRect: () => ({ top: 300 }) } as unknown as HTMLElement
    vi.spyOn(document, 'getElementById').mockReturnValue(mockEl)
    Object.defineProperty(globalThis, 'scrollY', { value: 100, configurable: true })

    render(
      <MemoryRouter initialEntries={['/#features']}>
        <LandingPage />
      </MemoryRouter>,
    )

    act(() => { vi.runAllTimers() })

    expect(document.getElementById).toHaveBeenCalledWith('features')
    expect(mockScrollTo).toHaveBeenCalledWith({ top: 400, behavior: 'smooth' })

    vi.useRealTimers()
  })

  it('does not scroll when no hash', () => {
    mockUseFeaturedEvents.mockReturnValue({ events: [], loading: false, error: null })
    vi.useFakeTimers()
    const mockScrollTo = vi.fn()
    globalThis.scrollTo = mockScrollTo

    render(
      <MemoryRouter initialEntries={['/']}>
        <LandingPage />
      </MemoryRouter>,
    )

    act(() => { vi.runAllTimers() })

    expect(mockScrollTo).not.toHaveBeenCalled()

    vi.useRealTimers()
  })

  it('retries scroll when element is absent on first tryScroll (lines 156-158)', () => {
    mockUseFeaturedEvents.mockReturnValue({ events: [], loading: false, error: null })
    vi.useFakeTimers()
    const mockScrollTo = vi.fn()
    globalThis.scrollTo = mockScrollTo
    Object.defineProperty(globalThis, 'scrollY', { value: 0, configurable: true })

    // First call returns null (element not yet rendered); second returns the real element
    const mockEl = { getBoundingClientRect: () => ({ top: 200 }) } as unknown as HTMLElement
    let callCount = 0
    vi.spyOn(document, 'getElementById').mockImplementation(() => {
      callCount++
      return callCount >= 2 ? mockEl : null
    })

    render(
      <MemoryRouter initialEntries={['/#features']}>
        <LandingPage />
      </MemoryRouter>,
    )

    // Initial 50 ms delay fires tryScroll — element absent → schedules retry
    act(() => { vi.advanceTimersByTime(50) })
    expect(mockScrollTo).not.toHaveBeenCalled()

    // Retry after 100 ms — element now found → scrolls
    act(() => { vi.advanceTimersByTime(100) })
    expect(mockScrollTo).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })

  it('stops retrying once maxAttempts is reached and never scrolls (line 157 else)', () => {
    // L157[if #1] — when the element is never found, the retry loop bails out
    // after maxAttempts (10) instead of scheduling forever.
    mockUseFeaturedEvents.mockReturnValue({ events: [], loading: false, error: null })
    vi.useFakeTimers()
    const mockScrollTo = vi.fn()
    globalThis.scrollTo = mockScrollTo
    // Element is never present.
    vi.spyOn(document, 'getElementById').mockReturnValue(null)

    render(
      <MemoryRouter initialEntries={['/#never-there']}>
        <LandingPage />
      </MemoryRouter>,
    )

    // Initial 50 ms tick + 10 retries × 100 ms — well past the cap.
    act(() => { vi.advanceTimersByTime(50 + 100 * 12) })

    expect(mockScrollTo).not.toHaveBeenCalled()
    // getElementById is called once per attempt and capped at maxAttempts (10):
    // the initial tryScroll + 9 scheduled retries = 10 lookups, then it stops.
    expect((document.getElementById as ReturnType<typeof vi.fn>).mock.calls.length).toBe(10)
    // No further timers are pending — the loop has terminated.
    expect(vi.getTimerCount()).toBe(0)
    vi.useRealTimers()
  })

  it('toggles FAQ answer on click', () => {
    renderPage()

    const allButtons = screen.getAllByRole('button')
    const faqBtn = allButtons.find(btn => {
      const span = btn.querySelector('span')
      return (span?.textContent?.length ?? 0) > 10
    })

    expect(faqBtn).toBeTruthy()
    fireEvent.click(faqBtn!)

    const answers = document.querySelectorAll('[class*="px-6"][class*="pb-6"]')
    expect(answers.length).toBeGreaterThan(0)

    fireEvent.click(faqBtn!)
    const answersAfter = document.querySelectorAll('[class*="px-6"][class*="pb-6"]')
    expect(answersAfter.length).toBe(0)
  })
})
