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

import { useSearch } from '@/hooks/useEventSearch'

const mockUseSearch = useSearch as ReturnType<typeof vi.fn>

const mockEvent: Event = {
  id: 1,
  title: 'Conférence IA',
  location: 'Uni Dufour',
  startDate: '2026-04-10T14:00:00',
  endDate: '2026-04-10T17:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'user-1',
  attendingCount: 0,
  createdAt: '2026-03-01T10:00:00',
}

function makeDefaultHook(overrides: Partial<UseSearchResult> = {}): UseSearchResult {
  return {
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
    ...overrides,
  }
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

describe('SearchPage', () => {

  it('renders the search input', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook())
    renderPage()
    expect(screen.getByRole('textbox', { name: 'Rechercher' })).toBeTruthy()
  })

  it('renders the search button', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook())
    renderPage()
    expect(screen.getByRole('button', { name: 'Lancer la recherche' })).toBeTruthy()
  })

  it('always shows the result counter', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook())
    renderPage()

    expect(
      screen.getByText((text) =>
        text.includes('0') && text.includes('événement'),
      ),
    ).toBeTruthy()
  })

  it('shows singular result count', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ results: [mockEvent] }))
    renderPage()

    expect(screen.getByText((t) => t.includes('1') && t.includes('événement'))).toBeTruthy()
  })

  it('shows plural result count', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ results: [mockEvent, { ...mockEvent, id: 2 }] }),
    )
    renderPage()

    expect(screen.getByText((t) => t.includes('2') && t.includes('événements'))).toBeTruthy()
  })

  it('shows a spinner when loading', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ loading: true }))
    renderPage()

    expect(document.querySelector('.animate-spin')).toBeTruthy()
  })

  it('shows error message', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ error: 'Impossible de charger les résultats.' }),
    )
    renderPage()

    expect(screen.getByText('Impossible de charger les résultats.')).toBeTruthy()
  })

  it('shows empty state', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ results: [] }))
    renderPage()

    expect(
      screen.getByText('Aucun résultat. Essayez de modifier vos filtres ou votre recherche'),
    ).toBeTruthy()
  })

  it('renders event cards', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ results: [mockEvent] }))
    renderPage()

    expect(screen.getByText('Conférence IA')).toBeTruthy()
  })

  it('calls searchNow when clicking button', () => {
    const searchNow = vi.fn()
    mockUseSearch.mockReturnValue(makeDefaultHook({ searchNow }))
    renderPage()

    fireEvent.click(screen.getByRole('button', { name: 'Lancer la recherche' }))
    expect(searchNow).toHaveBeenCalledOnce()
  })

  it('calls setQuery on input change', () => {
    const setQuery = vi.fn()
    mockUseSearch.mockReturnValue(makeDefaultHook({ setQuery }))
    renderPage()

    fireEvent.change(screen.getByRole('textbox', { name: 'Rechercher' }), {
      target: { value: 'conférence' },
    })

    expect(setQuery).toHaveBeenCalledWith('conférence')
  })

  it('shows suggestions', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ suggestions: ['Conférence IA'] }),
    )
    renderPage()

    expect(screen.getByText('Conférence IA')).toBeTruthy()
  })

  it('calls selectSuggestion', () => {
    const selectSuggestion = vi.fn()
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ suggestions: ['Conférence IA'], selectSuggestion }),
    )
    renderPage()

    fireEvent.click(screen.getByText('Conférence IA'))
    expect(selectSuggestion).toHaveBeenCalledWith('Conférence IA')
  })

})