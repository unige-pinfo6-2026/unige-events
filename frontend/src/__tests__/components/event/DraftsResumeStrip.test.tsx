// @vitest-environment jsdom

import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import type { Event } from '@/types/event'

const mockNavigate = vi.fn()

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  }
})

vi.mock('@/hooks/useAuth', () => ({
  useAuth: () => ({ user: { id: 'uuid-test' } }),
}))

vi.mock('@/hooks/useMyDrafts', () => ({
  useMyDrafts: vi.fn(),
}))

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: () => ({ theme: 'dark', toggleTheme: () => {} }),
}))

vi.mock('boneyard-js/react', () => ({
  Skeleton: ({ children, name }: { children: React.ReactNode; name: string }) => (
    <div data-testid="skeleton" data-name={name}>
      {children}
    </div>
  ),
}))

import { useMyDrafts } from '@/hooks/useMyDrafts'
import DraftsResumeStrip from '@/components/event/DraftsResumeStrip'

const mockUseMyDrafts = vi.mocked(useMyDrafts)

function makeDraft(overrides: Partial<Event> = {}): Event {
  return {
    id: 1,
    title: 'Forum des associations',
    description: 'desc',
    location: 'Uni',
    startDate: '2099-05-01T10:00:00.000Z',
    endDate: '2099-05-01T11:00:00.000Z',
    category: 'ACADEMIC',
    creatorId: 'uuid-test',
    status: 'DRAFT',
    attendingCount: 0,
    allDay: false,
    createdAt: '2026-04-01T00:00:00.000Z',
    updatedAt: '2026-04-10T00:00:00.000Z',
    ...overrides,
  } as Event
}

let mockBoundingRectWidth = 2000

function stubBoundingRect(width: number) {
  mockBoundingRectWidth = width
}

beforeAll(() => {
  class MockResizeObserver {
    callback: ResizeObserverCallback
    constructor(cb: ResizeObserverCallback) {
      this.callback = cb
    }
    observe(target: Element) {
      this.callback(
        [{ contentRect: { width: mockBoundingRectWidth } } as unknown as ResizeObserverEntry],
        this as unknown as ResizeObserver,
      )
      void target
    }
    unobserve() {}
    disconnect() {}
  }
  ;(globalThis as unknown as { ResizeObserver: typeof ResizeObserver }).ResizeObserver =
    MockResizeObserver as unknown as typeof ResizeObserver

  if (typeof window !== 'undefined' && typeof window.matchMedia !== 'function') {
    ;(window as unknown as { matchMedia: (q: string) => MediaQueryList }).matchMedia = (query: string) =>
      ({
        matches: false,
        media: query,
        onchange: null,
        addListener: () => {},
        removeListener: () => {},
        addEventListener: () => {},
        removeEventListener: () => {},
        dispatchEvent: () => false,
      }) as unknown as MediaQueryList
  }

  Element.prototype.getBoundingClientRect = function (): DOMRect {
    return {
      width: mockBoundingRectWidth,
      height: 56,
      top: 0,
      left: 0,
      right: mockBoundingRectWidth,
      bottom: 56,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    }
  }
})

beforeEach(() => {
  mockNavigate.mockReset()
  mockUseMyDrafts.mockReset()
  stubBoundingRect(2000)
})

afterEach(() => cleanup())

function getTrigger() {
  return screen.getByRole('button', { name: /Mes brouillons/ })
}

function openPanel() {
  fireEvent.click(getTrigger())
}

describe('DraftsResumeStrip', () => {
  function stub(drafts: Event[], overrides: Partial<ReturnType<typeof useMyDrafts>> = {}) {
    mockUseMyDrafts.mockReturnValue({
      drafts,
      loading: false,
      error: null,
      ...overrides,
    })
  }

  it('renders the collapsed-header skeleton while loading', () => {
    stub([], { loading: true })
    render(<DraftsResumeStrip />)
    expect(screen.getByTestId('skeleton').getAttribute('data-name')).toBe('drafts-resume-strip')
  })

  it('renders the banner with "Aucun brouillon" when the user has zero drafts', () => {
    stub([])
    render(<DraftsResumeStrip />)
    expect(screen.getByRole('button', { name: /Mes brouillons/ })).toBeTruthy()
    openPanel()
    expect(screen.getByText('Aucun brouillon')).toBeTruthy()
  })

  it('returns null when there is an error', () => {
    stub([], { error: 'boom' })
    const { container } = render(<DraftsResumeStrip />)
    expect(container.firstChild).toBeNull()
  })

  it('renders the header trigger even with zero drafts, collapsed by default', () => {
    stub([])
    render(<DraftsResumeStrip />)
    const trigger = getTrigger()
    expect(trigger).toBeTruthy()
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
  })

  it('renders the "Mes brouillons" trigger collapsed by default', () => {
    stub([makeDraft({ id: 1, title: 'Alpha' })])
    render(<DraftsResumeStrip />)
    const trigger = getTrigger()
    expect(trigger).toBeTruthy()
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    // The draft card must not be present in the DOM while collapsed.
    expect(screen.queryByRole('button', { name: /Alpha/ })).toBeNull()
  })

  it('expands the panel on trigger click and exposes the cards', () => {
    stub([makeDraft({ id: 1, title: 'Alpha' }), makeDraft({ id: 2, title: 'Beta' })])
    render(<DraftsResumeStrip />)
    openPanel()
    expect(getTrigger().getAttribute('aria-expanded')).toBe('true')
    expect(screen.getByText('Alpha')).toBeTruthy()
    expect(screen.getByText('Beta')).toBeTruthy()
  })

  it('collapses the panel on a second trigger click', () => {
    stub([makeDraft({ id: 1, title: 'Alpha' })])
    render(<DraftsResumeStrip />)
    openPanel()
    expect(getTrigger().getAttribute('aria-expanded')).toBe('true')
    fireEvent.click(getTrigger())
    expect(getTrigger().getAttribute('aria-expanded')).toBe('false')
    expect(screen.queryByRole('button', { name: /Alpha/ })).toBeNull()
  })

  it('exposes the trigger as a native <button> so the browser handles Enter/Space natively', () => {
    stub([makeDraft({ id: 1, title: 'Alpha' })])
    render(<DraftsResumeStrip />)
    const trigger = getTrigger()
    // jsdom does not synthesize click-on-Enter for <button>, so we verify the element
    // type + aria contract rather than the keyboard side-effect itself.
    expect(trigger.tagName).toBe('BUTTON')
    expect(trigger.getAttribute('aria-expanded')).toBe('false')
    expect(trigger.getAttribute('aria-controls')).toBe('drafts-resume-panel')
  })

  it('navigates to the edit page when a card is clicked after opening the panel', () => {
    stub([makeDraft({ id: 42, title: 'Clickable' })])
    render(<DraftsResumeStrip />)
    openPanel()
    fireEvent.click(screen.getByRole('button', { name: /Clickable/ }))
    expect(mockNavigate).toHaveBeenCalledWith('/events/42/edit')
  })

  it('exposes a verbose aria-label on each card', () => {
    stub([makeDraft({ id: 1, title: 'Forum' })])
    render(<DraftsResumeStrip />)
    openPanel()
    const btn = screen.getByRole('button', { name: /Forum/ })
    expect(btn.getAttribute('aria-label')).toMatch(/Reprendre le brouillon/)
  })

  it('moves focus with ArrowRight across cards inside the open panel', () => {
    stub([makeDraft({ id: 1, title: 'First' }), makeDraft({ id: 2, title: 'Second' })])
    render(<DraftsResumeStrip />)
    openPanel()
    const first = screen.getByRole('button', { name: /First/ })
    const second = screen.getByRole('button', { name: /Second/ })
    first.focus()
    fireEvent.keyDown(first.parentElement!, { key: 'ArrowRight' })
    expect(document.activeElement).toBe(second)
  })

  it('shows "Brouillon sans titre" for a whitespace-only title', () => {
    stub([makeDraft({ id: 1, title: '   ' })])
    render(<DraftsResumeStrip />)
    openPanel()
    expect(screen.getByText('Brouillon sans titre')).toBeTruthy()
  })

  it('does not render an "Expirée" badge even when startDate is in the past', () => {
    stub([makeDraft({ id: 1, title: 'Old draft', startDate: '2020-01-01T00:00:00.000Z' })])
    render(<DraftsResumeStrip />)
    openPanel()
    expect(screen.queryByText(/Expirée/)).toBeNull()
  })

  it('renders the location with an icon when the draft has a location', () => {
    stub([makeDraft({ id: 1, title: 'With location', location: 'Uni Mail' })])
    render(<DraftsResumeStrip />)
    openPanel()
    expect(screen.getByText('Uni Mail')).toBeTruthy()
  })

  it('falls back to a compact start date when the draft has no location', () => {
    stub([
      makeDraft({
        id: 1,
        title: 'No location',
        location: '   ',
        startDate: '2099-05-01T10:00:00.000Z',
      }),
    ])
    render(<DraftsResumeStrip />)
    openPanel()
    expect(screen.getByText(/mai/i)).toBeTruthy()
  })

  it('falls back to the category name when neither location nor a valid start date is present', () => {
    stub([
      makeDraft({
        id: 1,
        title: 'Only category',
        location: '',
        startDate: '',
        category: 'ACADEMIC',
      }),
    ])
    render(<DraftsResumeStrip />)
    openPanel()
    expect(screen.getByText('Académique')).toBeTruthy()
  })

  it('renders a "Brouillon" tag on each card in addition to the header trigger', () => {
    stub([makeDraft({ id: 1, title: 'Tagged' })])
    render(<DraftsResumeStrip />)
    openPanel()
    // Header trigger "Mes brouillons" + per-card "Brouillon" tag = 2 distinct matches.
    const cardTag = screen.getByText('Brouillon')
    expect(cardTag).toBeTruthy()
  })

  it('does not crash when updatedAt is missing (sort still works on createdAt)', () => {
    stub([makeDraft({ id: 1, title: 'NoUpdatedAt', updatedAt: undefined })])
    render(<DraftsResumeStrip />)
    openPanel()
    expect(screen.getByText('NoUpdatedAt')).toBeTruthy()
  })

  it('exposes a descriptive aria-label on the expanded panel region', () => {
    stub([makeDraft()])
    render(<DraftsResumeStrip />)
    openPanel()
    expect(screen.getByLabelText('Liste de mes brouillons')).toBeTruthy()
  })

  it('does not render the "Voir tout" button when all drafts fit', () => {
    stubBoundingRect(2000)
    stub([makeDraft({ id: 1, title: 'Only a few' })])
    render(<DraftsResumeStrip />)
    openPanel()
    expect(screen.queryByRole('button', { name: /Voir tous mes brouillons/ })).toBeNull()
  })

  it('falls back to window resize listener when ResizeObserver is unavailable', () => {
    const originalRO = (globalThis as unknown as { ResizeObserver?: typeof ResizeObserver }).ResizeObserver
    delete (globalThis as unknown as { ResizeObserver?: typeof ResizeObserver }).ResizeObserver
    const addSpy = vi.spyOn(window, 'addEventListener')
    const removeSpy = vi.spyOn(window, 'removeEventListener')

    stub([makeDraft({ id: 1, title: 'Fallback' })])
    const { unmount } = render(<DraftsResumeStrip />)
    openPanel()

    expect(addSpy).toHaveBeenCalledWith('resize', expect.any(Function))

    unmount()
    expect(removeSpy).toHaveBeenCalledWith('resize', expect.any(Function))

    addSpy.mockRestore()
    removeSpy.mockRestore()
    ;(globalThis as unknown as { ResizeObserver?: typeof ResizeObserver }).ResizeObserver = originalRO
  })

  it('never renders the "Voir tout" button (route /my-events not wired yet — SCRUM-93)', () => {
    // Even when the rail overflows (6 drafts in a 500px container), the Voir tout
    // button must not appear — its target route doesn't exist and would 404. The
    // rail's overflow-x-auto lets the user reach the extra cards by scrolling.
    stubBoundingRect(500)
    stub(
      Array.from({ length: 6 }, (_, i) => makeDraft({ id: i + 1, title: `Draft ${i + 1}` })),
    )
    render(<DraftsResumeStrip />)
    openPanel()
    expect(screen.queryByRole('button', { name: /Voir tous mes brouillons/ })).toBeNull()
  })
})
