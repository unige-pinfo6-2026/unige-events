import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { Report } from '@/types/admin'
import type { Event } from '@/types/event'

vi.mock('@/hooks/useAdminReports', () => ({ useAdminReports: vi.fn() }))
vi.mock('@/hooks/useAdminFeatured', () => ({ useAdminFeatured: vi.fn() }))
vi.mock('@/contexts/ThemeContext', () => ({ useTheme: () => ({ theme: 'dark' }) }))
vi.mock('boneyard-js/react', () => ({
  Skeleton: ({ children }: { children: React.ReactNode }) => <>{children}</>,
}))

import { useAdminReports } from '@/hooks/useAdminReports'
import { useAdminFeatured } from '@/hooks/useAdminFeatured'
import AdminPage from '@/pages/admin/AdminPage'

const mockUseAdminReports = useAdminReports as ReturnType<typeof vi.fn>
const mockUseAdminFeatured = useAdminFeatured as ReturnType<typeof vi.fn>

const makeReport = (id: number, status: Report['status'] = 'PENDING'): Report => ({
  id,
  eventId: 10 + id,
  eventTitle: `Event ${id}`,
  reason: 'Spam',
  reportedByDisplayName: 'Alice',
  createdAt: '2026-05-01T10:00:00',
  status,
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
  capacity: null,
  attendingCount: 0,
  bannerUrl: null,
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

  it('renders Valider and Ignorer buttons for PENDING reports', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1, 'PENDING')],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByRole('button', { name: /Valider/ })).toBeTruthy()
    expect(screen.getByRole('button', { name: /Ignorer/ })).toBeTruthy()
  })

  it('calls reviewReport when Valider is clicked', () => {
    const reviewReport = vi.fn().mockResolvedValue(true)
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1, 'PENDING')],
      reviewReport,
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: /Valider/ }))
    expect(reviewReport).toHaveBeenCalledWith(1)
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

  it('renders status badge for processed reports', () => {
    mockUseAdminReports.mockReturnValue({
      ...defaultReports,
      reports: [makeReport(1, 'REVIEWED'), makeReport(2, 'DISMISSED')],
    })
    mockUseAdminFeatured.mockReturnValue(defaultFeatured)
    renderPage()
    expect(screen.getByText('Validé')).toBeTruthy()
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
    const placeholders = document.querySelectorAll('.h-\\[68px\\]')
    expect(placeholders.length).toBe(5)
  })

  it('renders featured skeleton fixture when featured events are loading', () => {
    mockUseAdminReports.mockReturnValue(defaultReports)
    mockUseAdminFeatured.mockReturnValue({ ...defaultFeatured, loading: true })
    renderPage()
    // Fixture contains 3 card placeholder divs
    const placeholders = document.querySelectorAll('.h-\\[72px\\]')
    expect(placeholders.length).toBe(3)
  })
})
