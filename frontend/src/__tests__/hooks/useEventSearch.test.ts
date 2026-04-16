// @vitest-environment jsdom

import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { ReactNode } from 'react'
import { createElement } from 'react'
import { MemoryRouter, useSearchParams } from 'react-router-dom'
import { useSearch } from '@/hooks/useEventSearch'

function useSearchAndParams() {
  const search = useSearch()
  const [searchParams] = useSearchParams()
  return { ...search, searchParams }
}

vi.mock('@/services/searchApi', () => ({
  searchEvents: vi.fn(),
  fetchSuggestions: vi.fn(),
}))

import { fetchSuggestions, searchEvents } from '@/services/searchApi'

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
    faculty: null,
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
      includePast: false,
    })
    expect(result.current.results).toEqual([])
    expect(result.current.suggestions).toEqual([])
    expect(result.current.loading).toBe(true)
    expect(result.current.error).toBeNull()
  })

  it('debounces search', async () => {
    const { result } = renderHook(() => useSearch(), { wrapper })

    mockSearchEvents.mockClear()

    act(() => {
      result.current.setQuery('foo')
    })

    act(() => {
      vi.advanceTimersByTime(399)
    })

    expect(mockSearchEvents).not.toHaveBeenCalled()

    await act(async () => {
      vi.advanceTimersByTime(1)
      await Promise.resolve()
    })

    expect(mockSearchEvents).toHaveBeenCalledTimes(1)
  })

  it('triggers searchEvents after debounce for query changes', async () => {
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
      expect.any(AbortSignal),
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

  it('sets loading to true immediately when query becomes non-empty', () => {
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('test')
    })

    expect(result.current.loading).toBe(true)
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

    expect(mockFetchSuggestions).toHaveBeenCalledWith('conf', expect.any(AbortSignal))
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

  it('clears suggestions when fetchSuggestions rejects with a non-abort error', async () => {
    mockFetchSuggestions.mockResolvedValue(['A', 'B'])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => { result.current.setQuery('conf') })

    await act(async () => { await vi.runAllTimersAsync() })

    expect(result.current.suggestions).toHaveLength(2)

    // Now make fetchSuggestions reject with a generic error
    mockFetchSuggestions.mockRejectedValue(new Error('network'))

    act(() => { result.current.setQuery('confer') })

    await act(async () => { await vi.runAllTimersAsync() })

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

  it('resets filters to default on resetFilters', async () => {
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({
        category: 'CONFERENCE',
        faculty: 'SCIENCES',
        dateFrom: '2026-01-01',
        dateTo: '2026-12-31',
        includePast: true,
      })
    })

    await act(async () => {
      await Promise.resolve()
    })

    act(() => {
      result.current.resetFilters()
    })

    await act(async () => {
      await Promise.resolve()
    })

    expect(result.current.filters).toEqual({ includePast: false })
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
      expect.any(AbortSignal),
    )
  })

  it('setFilters updates filters state', async () => {
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ category: 'SPORTS', faculty: 'SCIENCES', includePast: false })
    })

    await act(async () => { await Promise.resolve() })

    expect(result.current.filters.category).toBe('SPORTS')
    expect(result.current.filters.faculty).toBe('SCIENCES')
  })

  // Fix 1: category change triggers immediate search without waiting for 2000ms debounce
  it('category change triggers immediate search without debounce', async () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    await act(async () => {
      result.current.setFilters({ ...result.current.filters, category: 'ACADEMIC' })
      await Promise.resolve()
    })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ category: 'ACADEMIC' }),
      expect.any(AbortSignal),
    )
  })

  it('filter change fires immediately without waiting for timers', () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ ...result.current.filters, category: 'CONFERENCE' })
    })

    // called synchronously — no timer advance needed
    expect(mockSearchEvents).toHaveBeenCalled()
  })

  it('triggers search when filters change', async () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ category: 'ACADEMIC', includePast: false })
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ category: 'ACADEMIC' }),
      expect.any(AbortSignal),
    )
  })

  it('includes faculty in search params sent to the API', async () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ faculty: 'SCIENCES', category: 'ACADEMIC', includePast: false })
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ faculty: 'SCIENCES' }),
      expect.any(AbortSignal),
    )
  })

  it('ignores CanceledError so it does not set an error state', async () => {
    const canceledError = new Error('canceled')
    canceledError.name = 'CanceledError'
    mockSearchEvents.mockRejectedValue(canceledError)

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('test')
    })

    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(result.current.error).toBeNull()
  })

  it('selectSuggestion does not trigger a duplicate debounce search', async () => {
    mockSearchEvents.mockResolvedValue(mockEvents)

    const { result } = renderHook(() => useSearch(), { wrapper })

    await act(async () => {
      result.current.selectSuggestion('test')
      await Promise.resolve()
    })

    const callsAfterImmediate = mockSearchEvents.mock.calls.length
    expect(callsAfterImmediate).toBeGreaterThanOrEqual(1)

    // The debounce timer should fire but be skipped (skipNextDebounce)
    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(mockSearchEvents).toHaveBeenCalledTimes(callsAfterImmediate)
  })

  it('searchNow does not trigger a duplicate debounce search', async () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setQuery('hello')
    })

    // Call searchNow within the debounce window
    await act(async () => {
      result.current.searchNow()
      await Promise.resolve()
    })

    const callsAfterImmediate = mockSearchEvents.mock.calls.length
    expect(callsAfterImmediate).toBeGreaterThanOrEqual(1)

    // The pending debounce timer should be skipped
    await act(async () => {
      await vi.runAllTimersAsync()
    })

    expect(mockSearchEvents).toHaveBeenCalledTimes(callsAfterImmediate)
  })

  it('initializes state from URL params on mount', () => {
    function wrapperWithEntry({ children }: { children: ReactNode }) {
      return createElement(MemoryRouter, { initialEntries: ['/events/search?q=test&faculty=SCIENCES&dateFrom=2026-01-01'] }, children)
    }
    const { result } = renderHook(() => useSearch(), { wrapper: wrapperWithEntry })
    expect(result.current.query).toBe('test')
    expect(result.current.filters.faculty).toBe('SCIENCES')
    expect(result.current.filters.dateFrom).toBe('2026-01-01')
    expect(result.current.filters.includePast).toBe(false)
  })

  // Fix 4: includePast initialized from URL
  it('initializes includePast=true from URL param', () => {
    function wrapperWithIncludePast({ children }: { children: ReactNode }) {
      return createElement(MemoryRouter, { initialEntries: ['/events/search?includePast=true'] }, children)
    }
    const { result } = renderHook(() => useSearch(), { wrapper: wrapperWithIncludePast })
    expect(result.current.filters.includePast).toBe(true)
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
      result.current.setFilters({ faculty: 'SCIENCES', includePast: false })
    })

    expect(result.current.searchParams.get('faculty')).toBe('SCIENCES')
  })

  // Fix 4: includePast synced to URL
  it('syncs includePast=true to URL when filter changes', async () => {
    const { result } = renderHook(() => useSearchAndParams(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: true })
    })

    await act(async () => { await Promise.resolve() })

    expect(result.current.searchParams.get('includePast')).toBe('true')
  })

  it('does not add includePast to URL when it is false', async () => {
    const { result } = renderHook(() => useSearchAndParams(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: false })
    })

    await act(async () => { await Promise.resolve() })

    expect(result.current.searchParams.get('includePast')).toBeNull()
  })

  it('resetFilters clears filter URL params', () => {
    function wrapperWithEntry({ children }: { children: ReactNode }) {
      return createElement(MemoryRouter, { initialEntries: ['/events/search?q=test&faculty=SCIENCES'] }, children)
    }
    const { result } = renderHook(() => useSearchAndParams(), { wrapper: wrapperWithEntry })

    expect(result.current.searchParams.get('faculty')).toBe('SCIENCES')

    act(() => {
      result.current.resetFilters()
    })

    expect(result.current.searchParams.get('faculty')).toBeNull()
  })

  // Fix 4: when includePast is false, today's date is sent as dateFrom
  it('passes today as dateFrom when includePast is false and no dateFrom set', async () => {
    mockSearchEvents.mockResolvedValue([])
    const today = new Date().toISOString().split('T')[0]

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: false })
    })

    await act(async () => { await Promise.resolve() })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ dateFrom: today }),
      expect.any(AbortSignal),
    )
  })

  it('does not enforce dateFrom when includePast is true', async () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: true })
    })

    await act(async () => { await Promise.resolve() })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.not.objectContaining({ dateFrom: expect.anything() }),
      expect.any(AbortSignal),
    )
  })

  it('uses user dateFrom when it is in the future and includePast is false', async () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: false, dateFrom: '2099-01-01' })
    })

    await act(async () => { await Promise.resolve() })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ dateFrom: '2099-01-01' }),
      expect.any(AbortSignal),
    )
  })

  // Fix 5: client-side sort — upcoming first (ascending), then past (descending)
  it('sorts results: upcoming first ascending, then past most-recent-first', async () => {
    const now = Date.now()
    const futureEvent = {
      ...mockEvents[0],
      id: 1,
      startDate: new Date(now + 86400000).toISOString(),
    }
    const recentPastEvent = {
      ...mockEvents[0],
      id: 2,
      startDate: new Date(now - 86400000).toISOString(),
    }
    const olderPastEvent = {
      ...mockEvents[0],
      id: 3,
      startDate: new Date(now - 2 * 86400000).toISOString(),
    }

    // Backend returns in arbitrary order
    mockSearchEvents.mockResolvedValue([olderPastEvent, futureEvent, recentPastEvent])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: true })
    })

    await act(async () => { await Promise.resolve() })

    expect(result.current.results[0].id).toBe(1)  // upcoming first
    expect(result.current.results[1].id).toBe(2)  // most recent past second
    expect(result.current.results[2].id).toBe(3)  // older past last
  })

  it('sorts multiple upcoming events in ascending order', async () => {
    const now = Date.now()
    const soonEvent = { ...mockEvents[0], id: 2, startDate: new Date(now + 86400000).toISOString() }
    const laterEvent = { ...mockEvents[0], id: 1, startDate: new Date(now + 2 * 86400000).toISOString() }

    mockSearchEvents.mockResolvedValue([laterEvent, soonEvent])

    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: false })
    })

    await act(async () => { await Promise.resolve() })

    expect(result.current.results[0].id).toBe(2)  // sooner first
    expect(result.current.results[1].id).toBe(1)  // later second
  })

  // --- facultyNone filter (mutex avec faculty) ---

  it('reads_facultyNone_from_url_on_mount', () => {
    function wrapperWithFacultyNone({ children }: { children: ReactNode }) {
      return createElement(MemoryRouter, { initialEntries: ['/events/search?facultyNone=true'] }, children)
    }
    const { result } = renderHook(() => useSearch(), { wrapper: wrapperWithFacultyNone })
    expect(result.current.filters.facultyNone).toBe(true)
  })

  it('syncs_facultyNone_to_url_when_set', async () => {
    const { result } = renderHook(() => useSearchAndParams(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: false, facultyNone: true })
    })

    await act(async () => { await Promise.resolve() })

    expect(result.current.searchParams.get('facultyNone')).toBe('true')
  })

  it('does_not_add_facultyNone_to_url_when_false_or_undefined', async () => {
    const { result } = renderHook(() => useSearchAndParams(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: false, facultyNone: undefined })
    })

    await act(async () => { await Promise.resolve() })

    expect(result.current.searchParams.get('facultyNone')).toBeNull()
  })

  it('sends_facultyNone_in_api_params_when_set', async () => {
    mockSearchEvents.mockResolvedValue([])
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: true, facultyNone: true })
    })

    await act(async () => { await Promise.resolve() })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ facultyNone: true, faculty: undefined }),
      expect.any(AbortSignal),
    )
  })

  it('setting_faculty_clears_facultyNone_in_url', async () => {
    function wrapperWithFacultyNone({ children }: { children: ReactNode }) {
      return createElement(MemoryRouter, { initialEntries: ['/events/search?facultyNone=true'] }, children)
    }
    const { result } = renderHook(() => useSearchAndParams(), { wrapper: wrapperWithFacultyNone })

    expect(result.current.searchParams.get('facultyNone')).toBe('true')

    act(() => {
      result.current.setFilters({ includePast: false, faculty: 'SCIENCES', facultyNone: undefined })
    })

    await act(async () => { await Promise.resolve() })

    expect(result.current.searchParams.get('faculty')).toBe('SCIENCES')
    expect(result.current.searchParams.get('facultyNone')).toBeNull()
  })

  it('facultyNone_true_overrides_faculty_in_api_params', async () => {
    mockSearchEvents.mockResolvedValue([])
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => {
      result.current.setFilters({ includePast: true, faculty: 'SCIENCES', facultyNone: true })
    })

    await act(async () => { await Promise.resolve() })

    expect(mockSearchEvents).toHaveBeenCalledWith(
      expect.objectContaining({ facultyNone: true, faculty: undefined }),
      expect.any(AbortSignal),
    )
  })
})
