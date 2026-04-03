// @vitest-environment jsdom

import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { createElement } from 'react'
import { MemoryRouter, useSearchParams } from 'react-router-dom'
import { useSearch } from '../../hooks/useSearch'

function useSearchAndParams() {
  const search = useSearch()
  const [searchParams] = useSearchParams()
  return { ...search, searchParams }
}

vi.mock('../../services/searchApi', () => ({
  searchEvents: vi.fn(),
  fetchSuggestions: vi.fn(),
}))

import { fetchSuggestions, searchEvents } from '../../services/searchApi'

const mockSearchEvents = searchEvents as ReturnType<typeof vi.fn>
const mockFetchSuggestions = fetchSuggestions as ReturnType<typeof vi.fn>

const mockEvents = [
  {
    id: 1,
    title: 'Conférence IA',
    location: 'Uni Dufour',
    startDate: '2026-04-10T14:00:00',
    endDate: '2026-04-10T17:00:00',
    category: 'CONFERENCE' as const,
    status: 'PUBLISHED' as const,
    creatorId: 'user-1',
    createdAt: '2026-03-01T10:00:00',
  },
]

function wrapper({ children }: { children: ReactNode }) {
  return createElement(MemoryRouter, null, children)
}

beforeEach(() => {
  vi.useFakeTimers()
  mockSearchEvents.mockResolvedValue([])
  mockFetchSuggestions.mockResolvedValue([])
})

afterEach(() => {
  vi.useRealTimers()
  vi.resetAllMocks()
})

describe('useSearch', () => {
  it('initializes with empty query and default filters', () => {
    const { result } = renderHook(() => useSearch(), { wrapper })
    expect(result.current.query).toBe('')
    expect(result.current.filters).toEqual({
      category: undefined,
      faculty: undefined,
      dateFrom: undefined,
      dateTo: undefined,
    })
    expect(result.current.results).toEqual([])
    expect(result.current.suggestions).toEqual([])
    expect(result.current.loading).toBe(false)
    expect(result.current.error).toBeNull()
  })

  it('does not call searchEvents before 2000ms', async () => {
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('foo')
    })

    act(() => {
      vi.advanceTimersByTime(1999)
    })

    expect(mockSearchEvents).not.toHaveBeenCalled()
  })

  it('triggers searchEvents after 2000ms debounce', async () => {
    mockSearchEvents.mockResolvedValue(mockEvents)

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('conférence')
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'conférence' }),
    )
  })

  it('updates results after a successful search', async () => {
    mockSearchEvents.mockResolvedValue(mockEvents)

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('IA')
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(result.current.results).toEqual(mockEvents)
  })

  it('sets error when searchEvents rejects', async () => {
    mockSearchEvents.mockRejectedValue(new Error('Network error'))

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('error')
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(result.current.error).toBe('Impossible de charger les résultats.')
  })

  it('sets loading to false after search completes', async () => {
    mockSearchEvents.mockResolvedValue(mockEvents)

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('test')
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(result.current.loading).toBe(false)
    expect(result.current.results).toEqual(mockEvents)
  })

  it('does not call fetchSuggestions before 300ms', () => {
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('conf')
    })

    act(() => {
      vi.advanceTimersByTime(299)
    })

    expect(mockFetchSuggestions).not.toHaveBeenCalled()
  })

  it('calls fetchSuggestions after 300ms when query is non-empty', async () => {
    mockFetchSuggestions.mockResolvedValue(['Conférence IA', 'Conférence ML'])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('conf')
    })

    await act(async () => {
      vi.advanceTimersByTime(300)
      await Promise.resolve()
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(mockFetchSuggestions).toHaveBeenCalledWith('conf')
  })

  it('populates suggestions from fetchSuggestions', async () => {
    mockFetchSuggestions.mockResolvedValue(['Conférence IA', 'Conférence ML'])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('conf')
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(result.current.suggestions).toContain('Conférence IA')
  })

  it('clears suggestions when query becomes empty', () => {
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('conf')
    })

    act(() => {
      result.current.setQuery('')
    })

    expect(result.current.suggestions).toEqual([])
  })

  it('limits suggestions to 5 items', async () => {
    mockFetchSuggestions.mockResolvedValue(['A', 'B', 'C', 'D', 'E', 'F', 'G'])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('a')
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(result.current.suggestions.length).toBeLessThanOrEqual(5)
  })

  it('resets filters to default on resetFilters', () => {
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({
        category: 'CONFERENCE',
        faculty: 'SCIENCES',
        dateFrom: '2026-01-01',
        dateTo: '2026-12-31',
      })
    })

    act(() => {
      result.current.resetFilters()
    })

    expect(result.current.filters).toEqual({})
  })

  it('selectSuggestion sets query and clears suggestions', () => {
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.selectSuggestion('Conférence IA')
    })

    expect(result.current.query).toBe('Conférence IA')
    expect(result.current.suggestions).toEqual([])
  })

  it('selectSuggestion immediately triggers search', async () => {
    mockSearchEvents.mockResolvedValue(mockEvents)

    const { result } = renderHook(() => useSearch(), { wrapper })

    await act(async () => {
      result.current.selectSuggestion('test')
      await Promise.resolve()
    })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ q: 'test' }),
    )
  })

  it('setFilters updates filters state', () => {
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ category: 'SPORTS', faculty: 'SCIENCES' })
    })

    expect(result.current.filters.category).toBe('SPORTS')
    expect(result.current.filters.faculty).toBe('SCIENCES')
  })

  it('triggers search when filters change', async () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ category: 'ACADEMIC' })
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ category: 'ACADEMIC' }),
    )
  })

  it('initializes state from URL params on mount', () => {
    function wrapperWithEntry({ children }: { children: ReactNode }) {
      return createElement(MemoryRouter, { initialEntries: ['/search?q=test&faculty=SES&dateFrom=2026-01-01'] }, children)
    }
    const { result } = renderHook(() => useSearch(), { wrapper: wrapperWithEntry })
    expect(result.current.query).toBe('test')
    expect(result.current.filters.faculty).toBe('SES')
    expect(result.current.filters.dateFrom).toBe('2026-01-01')
  })

  it('updates URL when query changes', () => {
    const { result } = renderHook(() => useSearchAndParams(), { wrapper })

    act(() => {
      result.current.setQuery('react')
    })

    expect(result.current.searchParams.get('q')).toBe('react')
  })

  it('updates URL when filters change', () => {
    const { result } = renderHook(() => useSearchAndParams(), { wrapper })

    act(() => {
      result.current.setFilters({ faculty: 'SES' })
    })

    expect(result.current.searchParams.get('faculty')).toBe('SES')
  })

  it('resetFilters clears filter URL params', () => {
    function wrapperWithEntry({ children }: { children: ReactNode }) {
      return createElement(MemoryRouter, { initialEntries: ['/search?q=test&faculty=SES'] }, children)
    }
    const { result } = renderHook(() => useSearchAndParams(), { wrapper: wrapperWithEntry })

    expect(result.current.searchParams.get('faculty')).toBe('SES')

    act(() => {
      result.current.resetFilters()
    })

    expect(result.current.searchParams.get('faculty')).toBeNull()
  })
})
