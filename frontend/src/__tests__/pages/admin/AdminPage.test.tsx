import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { Report } from '@/types/admin'
import type { Event } from '@/types/event'

vi.mock('@/hooks/useAdminReports', () => ({ useAdminReports: vi.fn() }))
vi.mock('@/hooks/useAdminFeatured', () => ({ useAdminFeatured: vi.fn() }))
vi.mock('@/contexts/ThemeContext', () => ({ useTheme: vi.fn(() => ({ theme: 'dark' })) }))
vi.mock('boneyard-js/react', () => ({
  Skeleton: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

import { useAdminReports } from '@/hooks/useAdminReports'
import { useAdminFeatured } from '@/hooks/useAdminFeatured'
import { useTheme } from '@/contexts/ThemeContext'
import AdminPage from '@/pages/admin/AdminPage'

const mockUseAdminReports = useAdminReports as ReturnType<typeof vi.fn>
const mockUseAdminFeatured = useAdminFeatured as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>

const makeReport = (id: number, status: Report['status'] = 'PENDING'): Report => ({
  id,
  targetType: 'EVENT',
  eventId: 10 + id,
  commentId: null,
  eventTitle: `Event ${id}`,
  commentContent: null,
  reporterId: `reporter-${id}`,
  reporterDisplayName: 'Alice',
  reason: 'SPAM',
  description: null,
  createdAt: '2026-05-01T10:00:00',
  status,
  moderationNote: null,
  reviewedAt: null,
  reviewedBy: null,
})

const makeCommentReport = (id: number, status: Report['status'] = 'PENDING'): Report => ({
  ...makeReport(id, status),
  targetType: 'COMMENT',
  eventId: null,
  commentId: 100 + id,
  eventTitle: null,
  commentContent: `reported body ${id}`,
})

const makeEvent = (id: number): Event => ({
  id,
  title: `Featured Event ${id}`,
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
})

const defaultReports = {
  reports: [],
  loading: false,
  error: null,
  activeTab: 'PENDING' as const,
  setActiveTab: vi.fn(),
  reviewReport: vi.fn(),
  dismissReport: vi.fn(),
  refresh: vi.fn(),
}

const defaultFeatured = {
  featuredEvents: [],
  loading: false,
  error: null,
  searchQuery: '',
  setSearchQuery: vi.fn(),
  searchResults: [],
  searchLoading: false,
  featureEvent: vi.fn(),
  unfeatureEvent: vi.fn(),
  refresh: vi.fn(),
}

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminPage />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  mockUseTheme.mockReturnValue({ theme: 'dark' })
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('AdminPage — layout', () => {
  it('renders the Administration heading', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Administration')).toBeTruthy()
  })

  it('renders both section headings', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Modération des signalements')).toBeTruthy()
    expect(screen.getByText('Événements mis en avant')).toBeTruthy()
  })
})

describe('AdminPage — reports section', () => {
  it('renders En attente and Traités tabs', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByRole('button', { name: /En attente/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: /Traités/ })).toBeTruthy()
  })

  it('shows empty state when no reports', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Aucun signalement en attente.')).toBeTruthy()
  })

  it('renders report rows when reports exist', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1), makeReport(2)],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Event 1')).toBeTruthy()
    expect(screen.getByText('Event 2')).toBeTruthy()
  })

  it('renders Bannir and Ignorer buttons for PENDING reports', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1, 'PENDING')],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByRole('button', { name: /Bannir l'événement/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: /Ignorer/ })).toBeTruthy()
  })

  it('calls reviewReport when "Bannir l\'événement" is clicked', () => {
    const reviewReport = vi.fn().mockResolvedValue(true)
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1, 'PENDING')],
      reviewReport,
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Bannir l'événement/ }))
    expect(reviewReport).toHaveBeenCalledWith(1)
  })

  it('uses the destructive (error) color on the Bannir button to signal irreversibility', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1, 'PENDING')],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    const banBtn = screen.getByRole('button', { name: /Bannir l'événement/ })
    // We don't lock down a full class string — just assert the destructive token
    // is present so the action visually reads as irreversible.
    expect(banBtn.className).toContain('text-error')
    expect(banBtn.className).toContain('bg-error')
  })

  it('calls dismissReport when Ignorer is clicked', () => {
    const dismissReport = vi.fn().mockResolvedValue(true)
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1, 'PENDING')],
      dismissReport,
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Ignorer/ }))
    expect(dismissReport).toHaveBeenCalledWith(1)
  })

  it('renders "Banni" and "Ignoré" badges for processed reports', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1, 'REVIEWED'), makeReport(2, 'DISMISSED')],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Banni')).toBeTruthy()
    expect(screen.getByText('Ignoré')).toBeTruthy()
  })

  it('calls setActiveTab when tab button is clicked', () => {
    const setActiveTab = vi.fn()
    mockUseAdminReports.mockReturnValue({ ...defaultReports, setActiveTab })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Traités/ }))
    expect(setActiveTab).toHaveBeenCalledWith('PROCESSED')
  })

  it('calls setActiveTab(PENDING) when the En attente tab is clicked', () => {
    // L144 — the PENDING tab button onClick (only Traités was clicked before).
    const setActiveTab = vi.fn()
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      activeTab: 'PROCESSED' as const,
      setActiveTab,
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /En attente/ }))
    expect(setActiveTab).toHaveBeenCalledWith('PENDING')
  })

  it('highlights the Traités tab and shows the processed empty copy when activeTab is PROCESSED', () => {
    // L146[cond-expr #1] (PENDING tab inactive), L162[cond-expr #0] (PROCESSED
    // tab active), L173[cond-expr #1] (processed empty copy).
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [],
      activeTab: 'PROCESSED' as const,
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    const pendingTab = screen.getByRole('button', { name: /En attente/ })
    const processedTab = screen.getByRole('button', { name: /Traités/ })
    expect(pendingTab.className).not.toContain('bg-accent')
    expect(processedTab.className).toContain('bg-accent')
    expect(screen.getByText('Aucun signalement traité.')).toBeTruthy()
  })

  it('shows error message when reports fetch fails', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      error: 'Impossible de charger les signalements.',
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Impossible de charger les signalements.')).toBeTruthy()
  })

  it('shows report count badge on En attente tab when reports exist', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1), makeReport(2), makeReport(3)],
      activeTab: 'PENDING' as const,
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('3')).toBeTruthy()
  })

  it('renders the human-readable French label for the reason enum', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1)],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Spam')).toBeTruthy()
    expect(screen.queryByText('SPAM')).toBeNull()
  })

  it('renders the report description as a secondary line under the reason', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [{ ...makeReport(1), description: 'Looks like an obvious scam' }],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Looks like an obvious scam')).toBeTruthy()
  })

  it('falls back to placeholder labels when event and reporter are deleted', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [{
        ...makeReport(1),
        eventId: null,
        eventTitle: null,
        reporterId: null,
        reporterDisplayName: null,
      }],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Événement supprimé')).toBeTruthy()
    expect(screen.getByText('Compte supprimé')).toBeTruthy()
    // No anchor tag when event is gone
    expect(screen.queryByRole('link', { name: 'Événement supprimé' })).toBeNull()
  })

  it('renders a comment report with its body and a "Supprimer le commentaire" action (bug ③)', () => {
    mockUseAdminReports.mockReturnValue({ ...defaultReports, reports: [makeCommentReport(1)] })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText(/reported body 1/)).toBeTruthy()
    expect(screen.getByRole('button', { name: /Supprimer le commentaire/ })).toBeTruthy()
    // A comment report must NOT offer the event-ban action.
    expect(screen.queryByRole('button', { name: /Bannir l'événement/ })).toBeNull()
  })

  it('falls back to "Commentaire supprimé" when a comment report has no body', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [{ ...makeCommentReport(2), commentContent: null }],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Commentaire supprimé')).toBeTruthy()
  })

  it('shows the "Supprimé" badge for a processed (REVIEWED) comment report', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeCommentReport(3, 'REVIEWED')],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Supprimé')).toBeTruthy()
  })
})

describe('AdminPage — featured section', () => {
  it('shows empty state when no featured events', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Aucun événement mis en avant.')).toBeTruthy()
  })

  it('renders featured event cards', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({
      ...defaultFeatured,
      featuredEvents: [makeEvent(1), makeEvent(2)],
    })
    renderPage()
    expect(screen.getByText('Featured Event 1')).toBeTruthy()
    expect(screen.getByText('Featured Event 2')).toBeTruthy()
  })

  it('renders the banner thumbnail when the featured event has a bannerUrl', () => {
    // L229[cond-expr #0] — the <img> branch of the FeaturedEventCard banner.
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({
      ...defaultFeatured,
      featuredEvents: [{ ...makeEvent(1), bannerUrl: 'https://example.com/b.jpg' }],
    })
    renderPage()
    const img = document.querySelector<HTMLImageElement>('img[src*="b.jpg"]')
    expect(img).toBeTruthy()
  })

  it('calls unfeatureEvent when Retirer is clicked', () => {
    const unfeatureEvent = vi.fn().mockResolvedValue(true)
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({
      ...defaultFeatured,
      featuredEvents: [makeEvent(5)],
      unfeatureEvent,
    })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Retirer/ }))
    expect(unfeatureEvent).toHaveBeenCalledWith(5)
  })

  it('renders search results when query is set', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({
      ...defaultFeatured,
      searchQuery: 'concert',
      searchResults: [makeEvent(10)],
    })
    renderPage()
    expect(screen.getByText('Featured Event 10')).toBeTruthy()
    expect(screen.getByRole('button', { name: /Mettre en avant/ })).toBeTruthy()
  })

  it('calls featureEvent when Mettre en avant is clicked', () => {
    const featureEvent = vi.fn().mockResolvedValue(true)
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({
      ...defaultFeatured,
      searchQuery: 'concert',
      searchResults: [makeEvent(10)],
      featureEvent,
    })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Mettre en avant/ }))
    expect(featureEvent).toHaveBeenCalledWith(10)
  })

  it('shows "Aucun résultat." when search has no results and query is set', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({
      ...defaultFeatured,
      searchQuery: 'xyz',
      searchResults: [],
      searchLoading: false,
    })
    renderPage()
    expect(screen.getByText('Aucun résultat.')).toBeTruthy()
  })

  it('shows "Recherche en cours…" when searchLoading is true', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({
      ...defaultFeatured,
      searchQuery: 'event',
      searchLoading: true,
    })
    renderPage()
    expect(screen.getByText('Recherche en cours…')).toBeTruthy()
  })

  it('shows error message when featured fetch fails', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({
      ...defaultFeatured,
      error: 'Impossible de charger les événements mis en avant.',
    })
    renderPage()
    expect(screen.getByText('Impossible de charger les événements mis en avant.')).toBeTruthy()
  })

  it('calls setSearchQuery when search input changes', () => {
    const setSearchQuery = vi.fn()
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({ ...defaultFeatured, setSearchQuery })
    renderPage()
    const input = screen.getByPlaceholderText("Titre de l'événement…")
    fireEvent.change(input, { target: { value: 'music' } })
    expect(setSearchQuery).toHaveBeenCalledWith('music')
  })
})

describe('AdminPage — loading skeletons', () => {
  it('renders reports skeleton fixture when reports are loading', () => {
    mockUseAdminReports.mockReturnValue({ ...defaultReports, loading: true })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    // Skeleton renders its children (the fixture divs) via the mock
    // Fixture contains 2 tab placeholder divs and 5 row placeholder divs
    const placeholders = document.querySelectorAll(String.raw`.h-\[68px\]`)
    expect(placeholders.length).toBe(5)
  })

  it('renders featured skeleton fixture when featured events are loading', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({ ...defaultFeatured, loading: true })
    renderPage()
    // Fixture contains 3 card placeholder divs
    const placeholders = document.querySelectorAll(String.raw`.h-\[72px\]`)
    expect(placeholders.length).toBe(3)
  })
})

describe('AdminPage — light theme skeleton color', () => {
  it('renders both sections under the light theme (light skeletonColor branch)', () => {
    // L127[cond-expr #1] + L276[cond-expr #1] — the light branch of the
    // per-section skeletonColor ternary, exercised while both are loading.
    mockUseTheme.mockReturnValue({ theme: 'light' })
    mockUseAdminReports.mockReturnValue({ ...defaultReports, loading: true })
    mockUseAdminFeatured.mockReturnValue({ ...defaultFeatured, loading: true })
    renderPage()
    expect(document.querySelectorAll(String.raw`.h-\[68px\]`).length).toBe(5)
    expect(document.querySelectorAll(String.raw`.h-\[72px\]`).length).toBe(3)
  })
})
