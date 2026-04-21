
import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import LandingPage from '@/pages/LandingPage'

vi.mock('@/hooks/useEvents', () => ({
  useEvents: vi.fn(),
}))

vi.mock('@/components/faculty/FacultyMarquee', () => ({
  default: () => <div>FacultyMarquee</div>,
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(() => ({ user: null, login: vi.fn() })),
}))

import { useEvents } from '@/hooks/useEvents'

const mockUseEvents = useEvents as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <LandingPage />
    </MemoryRouter>,
  )
}

describe('LandingPage', () => {
  it('renders without crashing', () => {
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: null, hasMore: false, loadMore: vi.fn() })
    const { container } = renderPage()
    expect(container).toBeTruthy()
  })

  it('scrolls to hash section with navbar offset', () => {
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: null, hasMore: false, loadMore: vi.fn() })
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
    // top = getBoundingClientRect().top (300) + scrollY (100) = 400 (no navbar offset)
    expect(mockScrollTo).toHaveBeenCalledWith({ top: 400, behavior: 'smooth' })

    vi.useRealTimers()
  })

  it('does not scroll when no hash', () => {
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: null, hasMore: false, loadMore: vi.fn() })
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

  it('toggles FAQ answer on click', () => {
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: null, hasMore: false, loadMore: vi.fn() })
    renderPage()

    const allButtons = screen.getAllByRole('button')
    const faqBtn = allButtons.find(btn => {
      const span = btn.querySelector('span')
      return span && span.textContent && span.textContent.length > 10
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
