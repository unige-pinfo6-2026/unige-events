// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import SearchPage from '../../pages/SearchPage'
import type { UseSearchResult } from '../../hooks/useSearch'
import type { Event } from '../../types'

vi.mock('../../hooks/useSearch', () => ({
  useSearch: vi.fn(),
}))

import { useSearch } from '../../hooks/useSearch'

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
  createdAt: '2026-03-01T10:00:00',
}

function makeDefaultHook(overrides: Partial<UseSearchResult> = {}): UseSearchResult {
  return {
    query: '',
    setQuery: vi.fn(),
    filters: {},
    setFilters: vi.fn(),
    results: [],
    suggestions: [],
    loading: false,
    error: null,
    resetFilters: vi.fn(),
    selectSuggestion: vi.fn(),
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

  it('renders the filter sidebar', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook())
    renderPage()
    expect(screen.getByRole('button', { name: 'Réinitialiser les filtres' })).toBeTruthy()
  })

  it('always shows the result counter', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook())
    renderPage()
    expect(screen.getByText('0 événements trouvés')).toBeTruthy()
  })

  it('shows singular result count for one result', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ results: [mockEvent] }))
    renderPage()
    expect(screen.getByText('1 événement trouvé')).toBeTruthy()
  })

  it('shows plural result count for multiple results', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ results: [mockEvent, { ...mockEvent, id: 2 }] }),
    )
    renderPage()
    expect(screen.getByText('2 événements trouvés')).toBeTruthy()
  })

  it('shows a spinner when loading', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ loading: true }))
    renderPage()
    expect(document.querySelector('.spinner')).toBeTruthy()
  })

  it('shows the error message when there is an error', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ error: 'Impossible de charger les résultats.' }),
    )
    renderPage()
    expect(screen.getByText('Impossible de charger les résultats.')).toBeTruthy()
  })

  it('shows the empty state when there are no results and no loading or error', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ results: [] }))
    renderPage()
    expect(
      screen.getByText('Aucun résultat — essayez de modifier vos filtres ou votre recherche'),
    ).toBeTruthy()
  })

  it('renders event cards when results are available', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ results: [mockEvent] }))
    renderPage()
    expect(screen.getByText('Conférence IA')).toBeTruthy()
  })

  it('does not show the empty state when loading', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ loading: true, results: [] }))
    renderPage()
    expect(
      screen.queryByText('Aucun résultat — essayez de modifier vos filtres ou votre recherche'),
    ).toBeNull()
  })

  it('does not show the empty state when there is an error', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ error: 'Erreur', results: [] }),
    )
    renderPage()
    expect(
      screen.queryByText('Aucun résultat — essayez de modifier vos filtres ou votre recherche'),
    ).toBeNull()
  })

  it('does not show the error message when loading', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ loading: true, error: 'Erreur', results: [] }),
    )
    renderPage()
    expect(screen.queryByText('Erreur')).toBeNull()
  })

  it('calls setQuery when the input changes', () => {
    const setQuery = vi.fn()
    mockUseSearch.mockReturnValue(makeDefaultHook({ setQuery }))
    renderPage()
    fireEvent.change(screen.getByRole('textbox', { name: 'Rechercher' }), {
      target: { value: 'conférence' },
    })
    expect(setQuery).toHaveBeenCalledWith('conférence')
  })

  it('shows the autocomplete dropdown when suggestions are available', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ suggestions: ['Conférence IA', 'Conférence ML'] }),
    )
    renderPage()
    expect(screen.getByText('Conférence IA')).toBeTruthy()
    expect(screen.getByText('Conférence ML')).toBeTruthy()
  })

  it('hides the dropdown when there are no suggestions', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ suggestions: [] }))
    renderPage()
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('calls selectSuggestion when a suggestion is clicked', () => {
    const selectSuggestion = vi.fn()
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ suggestions: ['Conférence IA'], selectSuggestion }),
    )
    renderPage()
    fireEvent.click(screen.getByText('Conférence IA'))
    expect(selectSuggestion).toHaveBeenCalledWith('Conférence IA')
  })

  it('hides the dropdown on Escape key', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ suggestions: ['Conférence IA'] }),
    )
    renderPage()
    const input = screen.getByRole('textbox', { name: 'Rechercher' })
    fireEvent.keyDown(input, { key: 'Escape' })
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('shows the input value from the hook query state', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ query: 'test query' }))
    renderPage()
    const input = screen.getByRole('textbox', { name: 'Rechercher' }) as HTMLInputElement
    expect(input.value).toBe('test query')
  })

  it('shows at most 5 suggestions', () => {
    const manySuggestions = ['A', 'B', 'C', 'D', 'E', 'F', 'G']
    mockUseSearch.mockReturnValue(makeDefaultHook({ suggestions: manySuggestions }))
    renderPage()
    const listbox = screen.getByRole('listbox')
    const options = listbox.querySelectorAll('[role="option"]')
    expect(options.length).toBeLessThanOrEqual(5)
  })

  it('shows suggestions on input focus when suggestions exist', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ suggestions: ['Conférence IA'] }),
    )
    renderPage()
    const input = screen.getByRole('textbox', { name: 'Rechercher' })
    // The dropdown is initially visible because suggestions exist; focus again keeps it visible
    fireEvent.focus(input)
    expect(screen.getByRole('listbox')).toBeTruthy()
  })

  it('does not show dropdown on focus when there are no suggestions', () => {
    mockUseSearch.mockReturnValue(makeDefaultHook({ suggestions: [] }))
    renderPage()
    const input = screen.getByRole('textbox', { name: 'Rechercher' })
    fireEvent.focus(input)
    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('hides the dropdown when clicking outside the input and suggestions', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ suggestions: ['Conférence IA'] }),
    )
    renderPage()

    expect(screen.getByRole('listbox')).toBeTruthy()

    // Click an element outside the input and the suggestions dropdown
    fireEvent.mouseDown(document.body)

    expect(screen.queryByRole('listbox')).toBeNull()
  })

  it('does not hide the dropdown when clicking inside the input', () => {
    mockUseSearch.mockReturnValue(
      makeDefaultHook({ suggestions: ['Conférence IA'] }),
    )
    renderPage()

    const input = screen.getByRole('textbox', { name: 'Rechercher' })
    fireEvent.mouseDown(input)

    expect(screen.getByRole('listbox')).toBeTruthy()
  })
})
