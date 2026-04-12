// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import SearchPage from '@/pages/event/EventsSearchPage'
import type { UseSearchResult } from '@/hooks/useEventSearch'
import type { Event } from '@/types/event'

vi.mock('@/hooks/useEventSearch', () => ({
  useSearch: vi.fn(),
}))

vi.mock('@/components/utils/Blobs', () => ({
  BlobsSubtle: () => <div data-testid="blobs" />,
}))

vi.mock('@/contexts/ThemeContext', () => ({
  useTheme: vi.fn(() => ({ theme: 'dark', toggleTheme: vi.fn() })),
}))

import { useSearch } from '@/hooks/useEventSearch'
import { useTheme } from '@/contexts/ThemeContext'

const mockUseSearch = useSearch as ReturnType<typeof vi.fn>
const mockUseTheme = useTheme as ReturnType<typeof vi.fn>

const mockEvent: Event = {
  id: 1,
  title: 'Conférence IA',
  location: 'Uni Dufour',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'user-1',
  capacity: 100,
  attendingCount: 0,
  createdAt: '2026-03-01T10:00:00',
}

const baseSearch: UseSearchResult = {
  query: '',
  setQuery: vi.fn(),
  filters: { includePast: false },
  setFilters: vi.fn(),
  results: [],
  suggestions: [],
  loading: false,
  error: null,
  resetFilters: vi.fn(),
  selectSuggestion: vi.fn(),
  searchNow: vi.fn(),
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <SearchPage />
    </MemoryRouter>,
  )
}

describe('EventsSearchPage', () => {
  it('renders the search input', () => {
    mockUseSearch.mockReturnValue(baseSearch)
    renderPage()
    expect(screen.getByRole('textbox', { name: 'Rechercher' })).toBeTruthy()
  })

  it('shows skeleton while loading', () => {
    mockUseSearch.mockReturnValue({ ...baseSearch, loading: true })
    renderPage()
    expect(document.querySelector('[data-boneyard="search-results"]')).toBeTruthy()
  })

  it('shows error message when fetch fails', () => {
    mockUseSearch.mockReturnValue({
      ...baseSearch,
      loading: false,
      error: 'Erreur réseau.',
    })
    renderPage()
    expect(screen.getByText('Erreur réseau.')).toBeTruthy()
  })

  it('shows empty state when no results', () => {
    mockUseSearch.mockReturnValue({ ...baseSearch, results: [], loading: false })
    renderPage()
    expect(screen.getByText(/Aucun résultat/)).toBeTruthy()
  })

  it('renders event cards when results exist', () => {
    mockUseSearch.mockReturnValue({ ...baseSearch, results: [mockEvent] })
    renderPage()
    expect(screen.getByText('Conférence IA')).toBeTruthy()
  })

  it('shows result count for single result', () => {
    mockUseSearch.mockReturnValue({ ...baseSearch, results: [mockEvent] })
    renderPage()
    expect(screen.getByText('1 événement trouvé')).toBeTruthy()
  })

  it('shows result count for multiple results', () => {
    const events = [mockEvent, { ...mockEvent, id: 2, title: 'Workshop' }]
    mockUseSearch.mockReturnValue({ ...baseSearch, results: events })
    renderPage()
    expect(screen.getByText('2 événements trouvés')).toBeTruthy()
  })

  it('shows suggestions dropdown when suggestions are present', () => {
    mockUseSearch.mockReturnValue({ ...baseSearch, suggestions: ['SuggestionUnique', 'AutreSuggestion'] })
    renderPage()
    expect(screen.getByRole('listbox')).toBeTruthy()
    expect(screen.getByText('SuggestionUnique')).toBeTruthy()
    expect(screen.getByText('AutreSuggestion')).toBeTruthy()
  })

  it('calls selectSuggestion when a suggestion is clicked', () => {
    const selectSuggestion = vi.fn()
    mockUseSearch.mockReturnValue({ ...baseSearch, suggestions: ['UniqueSuggestionTerm'], selectSuggestion })
    renderPage()
    fireEvent.click(screen.getByRole('option', { name: 'UniqueSuggestionTerm' }))
    expect(selectSuggestion).toHaveBeenCalledWith('UniqueSuggestionTerm')
  })

  it('hides suggestions after clicking a suggestion', () => {
    const selectSuggestion = vi.fn()
    mockUseSearch.mockReturnValue({ ...baseSearch, suggestions: ['UniqueSuggestionTerm'], selectSuggestion })
    renderPage()
    fireEvent.click(screen.getByRole('option', { name: 'UniqueSuggestionTerm' }))
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('calls searchNow when the search button is clicked', () => {
    const searchNow = vi.fn()
    mockUseSearch.mockReturnValue({ ...baseSearch, searchNow })
    renderPage()
    fireEvent.click(screen.getByRole('button', { name: 'Lancer la recherche' }))
    expect(searchNow).toHaveBeenCalledOnce()
  })

  it('hides suggestions on Escape key', () => {
    mockUseSearch.mockReturnValue({ ...baseSearch, suggestions: ['EscapeSuggestion'] })
    renderPage()
    expect(screen.getByRole('listbox')).toBeTruthy()
    fireEvent.keyDown(screen.getByRole('textbox', { name: 'Rechercher' }), { key: 'Escape' })
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('hides suggestions when clicking outside', () => {
    mockUseSearch.mockReturnValue({ ...baseSearch, suggestions: ['OutsideSuggestion'] })
    renderPage()
    expect(screen.getByRole('listbox')).toBeTruthy()
    fireEvent.mouseDown(document.body)
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('shows 0 result count when there are no results', () => {
    mockUseSearch.mockReturnValue({ ...baseSearch, results: [] })
    renderPage()
    expect(screen.getByText('0 événement trouvé')).toBeTruthy()
  })

  it('does not show error when loading', () => {
    mockUseSearch.mockReturnValue({ ...baseSearch, loading: true, error: 'Erreur' })
    renderPage()
    expect(screen.queryByText('Erreur')).toBeNull()
  })

  it('shows skeleton with light theme color', () => {
    mockUseTheme.mockReturnValue({ theme: 'light', toggleTheme: vi.fn() })
    mockUseSearch.mockReturnValue({ ...baseSearch, loading: true })
    renderPage()
    expect(document.querySelector('[data-boneyard="search-results"]')).toBeTruthy()
  })
})
