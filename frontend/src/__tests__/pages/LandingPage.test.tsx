// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
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

  it('scrolls to hash section on mount', async () => {
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: null, hasMore: false, loadMore: vi.fn() })
    const mockScrollIntoView = vi.fn()
    vi.spyOn(document, 'getElementById').mockReturnValue({ scrollIntoView: mockScrollIntoView } as unknown as HTMLElement)

    render(
      <MemoryRouter initialEntries={['/#events']}>
        <LandingPage />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(document.getElementById).toHaveBeenCalledWith('events')
      expect(mockScrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth' })
    })
  })

  it('does not scroll when no hash', async () => {
    mockUseEvents.mockReturnValue({ events: [], loading: false, error: null, hasMore: false, loadMore: vi.fn() })
    const mockScrollIntoView = vi.fn()
    vi.spyOn(document, 'getElementById').mockReturnValue({ scrollIntoView: mockScrollIntoView } as unknown as HTMLElement)

    render(
      <MemoryRouter initialEntries={['/']}>
        <LandingPage />
      </MemoryRouter>,
    )

    await waitFor(() => {
      expect(mockScrollIntoView).not.toHaveBeenCalled()
    })
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
