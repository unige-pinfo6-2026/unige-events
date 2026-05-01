// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import MyPublicationsPreview from '@/components/profile/MyPublicationsPreview'
import type { Event, EventStatus } from '@/types/event'

vi.mock('@/hooks/useMyEvents', () => ({
  useMyEvents: vi.fn(),
}))

import { useMyEvents } from '@/hooks/useMyEvents'

const mockUseMyEvents = useMyEvents as ReturnType<typeof vi.fn>

function makeEvent(id: number, status: EventStatus = 'PUBLISHED'): Event {
  return {
    id,
    title: `Publication ${id}`,
    location: 'Uni',
    startDate: '2026-04-10T14:00:00',
    endDate: '2026-04-10T17:00:00',
    allDay: false,
    category: 'CONFERENCE',
    faculty: null,
    status,
    creatorId: 'user-1',
    capacity: 100,
    attendingCount: 5,
    createdAt: '2026-03-01T10:00:00',
  }
}

function mockHook(overrides: Partial<ReturnType<typeof useMyEvents>> = {}) {
  mockUseMyEvents.mockReturnValue({
    events: [],
    loading: false,
    error: null,
    refresh: vi.fn(),
    publish: vi.fn(),
    cancel: vi.fn(),
    restore: vi.fn(),
    permanentlyDelete: vi.fn(),
    ...overrides,
  })
}

function renderPreview() {
  return render(
    <MemoryRouter>
      <MyPublicationsPreview />
    </MemoryRouter>,
  )
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('MyPublicationsPreview', () => {
  it('calls useMyEvents with PUBLISHED on mount (default tab)', () => {
    mockHook()
    renderPreview()
    expect(mockUseMyEvents).toHaveBeenCalledWith('PUBLISHED')
  })

  it('switches the hook call to DRAFT when Brouillons is clicked', () => {
    mockHook()
    renderPreview()
    fireEvent.click(screen.getByRole('tab', { name: /Brouillons/ }))
    expect(mockUseMyEvents).toHaveBeenLastCalledWith('DRAFT')
  })

  it('switches the hook call to CANCELLED when Annulés is clicked', () => {
    mockHook()
    renderPreview()
    fireEvent.click(screen.getByRole('tab', { name: /Annulés/ }))
    expect(mockUseMyEvents).toHaveBeenLastCalledWith('CANCELLED')
  })

  it('renders at most 3 rows even when 5 events are returned', () => {
    mockHook({ events: [1, 2, 3, 4, 5].map((id) => makeEvent(id)) })
    renderPreview()
    expect(screen.getByText('Publication 1')).toBeTruthy()
    expect(screen.getByText('Publication 2')).toBeTruthy()
    expect(screen.getByText('Publication 3')).toBeTruthy()
    expect(screen.queryByText('Publication 4')).toBeFalsy()
    expect(screen.queryByText('Publication 5')).toBeFalsy()
  })

  it('shows the create CTA in the empty state for the Publiés tab only', () => {
    mockHook({ events: [] })
    renderPreview()
    expect(screen.getByText('Aucune publication.')).toBeTruthy()
    const cta = screen.getByRole('link', { name: /Créer un événement/ })
    expect(cta.getAttribute('href')).toBe('/events/new')
  })

  it('does not show the create CTA on the empty Brouillons tab', () => {
    mockHook({ events: [] })
    renderPreview()
    fireEvent.click(screen.getByRole('tab', { name: /Brouillons/ }))
    expect(screen.getByText('Aucun brouillon.')).toBeTruthy()
    expect(screen.queryByRole('link', { name: /Créer un événement/ })).toBeFalsy()
  })

  it('does not show the create CTA on the empty Annulés tab', () => {
    mockHook({ events: [] })
    renderPreview()
    fireEvent.click(screen.getByRole('tab', { name: /Annulés/ }))
    expect(screen.getByText('Aucun événement annulé.')).toBeTruthy()
    expect(screen.queryByRole('link', { name: /Créer un événement/ })).toBeFalsy()
  })

  it('renders the loading state with skeleton placeholders', () => {
    mockHook({ loading: true })
    const { container } = renderPreview()
    expect(container.querySelector('[aria-busy="true"]')).toBeTruthy()
    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0)
  })

  it('renders the error state with a retry button that calls refresh', () => {
    const refresh = vi.fn()
    mockHook({ error: 'Boom', refresh })
    renderPreview()
    expect(screen.getByText('Impossible de charger vos publications.')).toBeTruthy()
    const retry = screen.getByRole('button', { name: /Réessayer/ })
    fireEvent.click(retry)
    expect(refresh).toHaveBeenCalledTimes(1)
  })

  it('points the "Voir toutes mes publications" link to ?status=published on Publiés', () => {
    mockHook()
    renderPreview()
    const link = screen.getByRole('link', { name: /Voir toutes mes publications/ })
    expect(link.getAttribute('href')).toBe('/my-events/publications?status=published')
  })

  it('updates the link href to ?status=draft when on Brouillons', () => {
    mockHook()
    renderPreview()
    fireEvent.click(screen.getByRole('tab', { name: /Brouillons/ }))
    const link = screen.getByRole('link', { name: /Voir toutes mes publications/ })
    expect(link.getAttribute('href')).toBe('/my-events/publications?status=draft')
  })

  it('updates the link href to ?status=cancelled when on Annulés', () => {
    mockHook()
    renderPreview()
    fireEvent.click(screen.getByRole('tab', { name: /Annulés/ }))
    const link = screen.getByRole('link', { name: /Voir toutes mes publications/ })
    expect(link.getAttribute('href')).toBe('/my-events/publications?status=cancelled')
  })

  it('shows the count next to the active tab and not the inactive ones', () => {
    mockHook({ events: [makeEvent(1), makeEvent(2), makeEvent(3), makeEvent(4)] })
    renderPreview()
    const publishedTab = screen.getByRole('tab', { name: /Publiés/ })
    const draftTab = screen.getByRole('tab', { name: /Brouillons/ })
    const cancelledTab = screen.getByRole('tab', { name: /Annulés/ })
    expect(publishedTab.textContent).toContain('(4)')
    expect(draftTab.textContent).not.toMatch(/\(\d+\)/)
    expect(cancelledTab.textContent).not.toMatch(/\(\d+\)/)
  })

  it('marks the active tab with aria-selected=true', () => {
    mockHook()
    renderPreview()
    const publishedTab = screen.getByRole('tab', { name: /Publiés/ })
    expect(publishedTab.getAttribute('aria-selected')).toBe('true')
    fireEvent.click(screen.getByRole('tab', { name: /Brouillons/ }))
    expect(publishedTab.getAttribute('aria-selected')).toBe('false')
    expect(screen.getByRole('tab', { name: /Brouillons/ }).getAttribute('aria-selected')).toBe('true')
  })
})
