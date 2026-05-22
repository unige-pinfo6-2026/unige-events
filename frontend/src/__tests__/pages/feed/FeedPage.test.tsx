import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import FeedPage from '@/pages/feed/FeedPage'
import type { FeedGroup, UseFeedResult } from '@/hooks/useFeed'
import type { Event } from '@/types/event'

// ─── Mocks ────────────────────────────────────────────────────────────────────

vi.mock('@/hooks/useFeed', () => ({
  useFeed: vi.fn(),
}))

vi.mock('@/hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: () => ({ theme: 'dark', toggleTheme: vi.fn() }),
}))

vi.mock('@/components/feed/Timeline', () => ({
  default: ({ groups }: { groups: FeedGroup[] }) => (
    <div data-testid="timeline">
      {groups.map(g => (
        <div key={g.date} data-testid={`group-${g.date}`}>
          <span data-testid="date-label">{g.date}</span>
          {g.events.map(e => (
            <div key={e.id} data-testid={`event-${e.id}`}>{e.title}</div>
          ))}
        </div>
      ))}
    </div>
  ),
}))

vi.mock('boneyard-js/react', () => ({
  Skeleton: ({ children }: { children: React.ReactNode }) => (
    <div data-testid="skeleton">{children}</div>
  ),
}))

// ─── Helpers ──────────────────────────────────────────────────────────────────

import { useFeed } from '@/hooks/useFeed'
import { useAuth } from '@/hooks/useAuth'

const mockUseFeed = useFeed as ReturnType<typeof vi.fn>
const mockUseAuth = useAuth as ReturnType<typeof vi.fn>

function defaultFeed(overrides: Partial<UseFeedResult> = {}): UseFeedResult {
  return {
    groups: [],
    loading: false,
    error: null,
    hasMore: false,
    loadMore: vi.fn(),
    ...overrides,
  }
}

function makeEvent(id: number, dateStr = '2026-04-28T10:00:00Z'): Event {
  return {
    id,
    title: `Event ${id}`,
    location: 'Somewhere',
    startDate: dateStr,
    endDate: dateStr,
    category: 'CONFERENCE',
    status: 'PUBLISHED',
    creatorId: 'user-1',
    createdAt: '2026-01-01T00:00:00',
    allDay: false,
    attendingCount: 0,
  }
}

function localDateKey(offsetDays: number): string {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

function renderPage() {
  return render(
    <MemoryRouter>
      <FeedPage />
    </MemoryRouter>,
  )
}

// ─── Setup ────────────────────────────────────────────────────────────────────

beforeEach(() => {
  // Default: an authenticated visitor (so the Tous / Mes abonnements toggle renders).
  mockUseAuth.mockReturnValue({ user: { id: 'u1' }, isLoading: false })
  // IntersectionObserver is used with `new` → must be a class/constructor
  class MockIntersectionObserver {
    observe = vi.fn()
    unobserve = vi.fn()
    disconnect = vi.fn()
    constructor(_cb: IntersectionObserverCallback) {}
  }
  vi.stubGlobal('IntersectionObserver', MockIntersectionObserver)
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  vi.unstubAllGlobals()
})

// ─── Tests ────────────────────────────────────────────────────────────────────

describe('FeedPage', () => {
  it('renders the page title', () => {
    mockUseFeed.mockReturnValue(defaultFeed())
    renderPage()
    expect(screen.getByText(/fil/i)).toBeDefined()
  })

  it('renders toggle buttons "Tous" and "Mes abonnements"', () => {
    mockUseFeed.mockReturnValue(defaultFeed())
    renderPage()
    expect(screen.getByText('Tous')).toBeDefined()
    expect(screen.getByText('Mes abonnements')).toBeDefined()
  })

  it('"Mes abonnements" toggle is enabled for an authenticated user and switches on click', () => {
    mockUseFeed.mockReturnValue(defaultFeed())
    renderPage()
    const btn = screen.getByText('Mes abonnements').closest('button')!
    expect(btn.disabled).toBe(false)
    expect(btn.getAttribute('aria-pressed')).toBe('false')
    fireEvent.click(btn)
    expect(btn.getAttribute('aria-pressed')).toBe('true')
  })

  it('hides the Tous / Mes abonnements toggle for an anonymous visitor', () => {
    mockUseAuth.mockReturnValue({ user: null, isLoading: false })
    mockUseFeed.mockReturnValue(defaultFeed())
    renderPage()
    expect(screen.queryByText('Mes abonnements')).toBeNull()
    expect(screen.queryByRole('group', { name: 'Filtrer le fil' })).toBeNull()
  })

  it('shows the abonnements-specific empty state after switching the toggle', () => {
    mockUseFeed.mockReturnValue(defaultFeed({ groups: [] }))
    renderPage()
    fireEvent.click(screen.getByText('Mes abonnements').closest('button')!)
    expect(screen.getByText(/de vos abonnements/i)).toBeDefined()
  })

  it('shows skeleton during initial loading (no groups yet)', () => {
    mockUseFeed.mockReturnValue(defaultFeed({ loading: true, groups: [] }))
    renderPage()
    expect(screen.getByTestId('skeleton')).toBeDefined()
  })

  it('shows error message on error', () => {
    mockUseFeed.mockReturnValue(defaultFeed({ error: 'Erreur réseau', groups: [] }))
    renderPage()
    expect(screen.getByText('Erreur réseau')).toBeDefined()
  })

  it('shows empty state when no events', () => {
    mockUseFeed.mockReturnValue(defaultFeed({ groups: [] }))
    renderPage()
    expect(screen.getByText(/aucun événement/i)).toBeDefined()
  })

  it('renders Timeline with groups when events are loaded', () => {
    const today = localDateKey(0)
    const tomorrow = localDateKey(1)
    const groups: FeedGroup[] = [
      { date: today, events: [makeEvent(1)] },
      { date: tomorrow, events: [makeEvent(2)] },
    ]
    mockUseFeed.mockReturnValue(defaultFeed({ groups }))
    renderPage()
    expect(screen.getByTestId('timeline')).toBeDefined()
    expect(screen.getByTestId(`group-${today}`)).toBeDefined()
    expect(screen.getByTestId(`group-${tomorrow}`)).toBeDefined()
  })

  it('shows the sentinel element for infinite scroll', () => {
    const groups: FeedGroup[] = [
      { date: '2026-04-28', events: [makeEvent(1)] },
    ]
    mockUseFeed.mockReturnValue(defaultFeed({ groups, hasMore: true }))
    renderPage()
    expect(screen.getByTestId('scroll-sentinel')).toBeDefined()
  })

  it('shows loading spinner when loading more pages (groups already present)', () => {
    const groups: FeedGroup[] = [
      { date: '2026-04-28', events: [makeEvent(1)] },
    ]
    mockUseFeed.mockReturnValue(defaultFeed({ groups, loading: true, hasMore: true }))
    renderPage()
    // Skeleton is NOT shown (groups > 0); spinner IS shown
    expect(screen.queryByTestId('skeleton')).toBeNull()
    expect(screen.getByLabelText('Chargement en cours')).toBeDefined()
  })

  it('triggers loadMore via IntersectionObserver when sentinel is visible', () => {
    const loadMore = vi.fn()
    const groups: FeedGroup[] = [
      { date: '2026-04-28', events: [makeEvent(1)] },
    ]
    mockUseFeed.mockReturnValue(defaultFeed({ groups, hasMore: true, loadMore }))

    let capturedCallback: IntersectionObserverCallback = () => {}
    class CapturingObserver {
      observe = vi.fn()
      disconnect = vi.fn()
      constructor(cb: IntersectionObserverCallback) { capturedCallback = cb }
    }
    vi.stubGlobal('IntersectionObserver', CapturingObserver)

    renderPage()

    // Simulate intersection
    capturedCallback([{ isIntersecting: true } as IntersectionObserverEntry], {} as IntersectionObserver)
    expect(loadMore).toHaveBeenCalledOnce()
  })
})
