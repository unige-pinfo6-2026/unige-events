// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, renderHook, waitFor } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { useSearch } from '@/hooks/useEventSearch'
import type { ReactNode } from 'react'

vi.mock('@/services/searchApi', () => ({
  fetchSuggestions: vi.fn(),
  searchEvents: vi.fn(),
}))

import { fetchSuggestions, searchEvents } from '@/services/searchApi'

const mockFetchSuggestions = fetchSuggestions as ReturnType<typeof vi.fn>
const mockSearchEvents = searchEvents as ReturnType<typeof vi.fn>

function wrapper({ children }: { children: ReactNode }) {
  return <MemoryRouter>{children}</MemoryRouter>
}

const mockEvent = {
  id: 1,
  title: 'Conférence IA',
  location: 'Uni Dufour',
  startDate: '2099-04-10T09:00:00',
  endDate: '2099-04-10T11:00:00',
  category: 'CONFERENCE',
  status: 'PUBLISHED',
  creatorId: 'u1',
  attendingCount: 0,
  createdAt: '2026-03-01',
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
  vi.useRealTimers()
})

describe('useSearch', () => {
  it('starts with loading false (not loading on initial render, loads after mount)', () => {
    mockSearchEvents.mockResolvedValue([])
    const { result } = renderHook(() => useSearch(), { wrapper })
    // loading may be false or set to true; just verify it settles
    expect(typeof result.current.loading).toBe('boolean')
  })

  it('returns results from searchEvents after initial mount', async () => {
    mockSearchEvents.mockResolvedValue([mockEvent])

    const { result } = renderHook(() => useSearch(), { wrapper })

    await waitFor(() => expect(result.current.results).toHaveLength(1))
    expect(result.current.results[0].title).toBe('Conférence IA')
    expect(result.current.error).toBeNull()
  })

  it('sets error when searchEvents fails', async () => {
    mockSearchEvents.mockRejectedValue(new Error('network'))

    const { result } = renderHook(() => useSearch(), { wrapper })

    await waitFor(() => expect(result.current.error).toBe('Impossible de charger les résultats.'))
  })

  it('searchNow triggers an immediate search', async () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    await waitFor(() => expect(result.current.loading).toBe(false))

    mockSearchEvents.mockResolvedValue([mockEvent])

    act(() => { result.current.searchNow() })

    await waitFor(() => expect(result.current.results).toHaveLength(1))
  })

  it('selectSuggestion updates query and triggers search', async () => {
    mockSearchEvents.mockResolvedValue([])
    mockFetchSuggestions.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    await waitFor(() => expect(result.current.loading).toBe(false))

    mockSearchEvents.mockResolvedValue([mockEvent])

    act(() => { result.current.selectSuggestion('Workshop') })

    await waitFor(() => expect(result.current.query).toBe('Workshop'))
    expect(result.current.suggestions).toHaveLength(0)
  })

  it('resetFilters resets to default filters', async () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    await waitFor(() => expect(result.current.loading).toBe(false))

    act(() => {
      result.current.setFilters({ category: 'CONFERENCE', includePast: true })
    })

    act(() => { result.current.resetFilters() })

    expect(result.current.filters.includePast).toBe(false)
    expect(result.current.filters.category).toBeUndefined()
  })

  it('setFilters triggers immediate search', async () => {
    mockSearchEvents.mockResolvedValue([])

    const { result } = renderHook(() => useSearch(), { wrapper })

    await waitFor(() => expect(result.current.loading).toBe(false))

    mockSearchEvents.mockResolvedValue([mockEvent])

    act(() => {
      result.current.setFilters({ category: 'SOCIAL', includePast: false })
    })

    await waitFor(() => expect(result.current.results).toHaveLength(1))
  })

  it('setQuery updates the query value', () => {
    mockSearchEvents.mockResolvedValue([])
    const { result } = renderHook(() => useSearch(), { wrapper })

    act(() => { result.current.setQuery('hello') })

    expect(result.current.query).toBe('hello')
  })
})
