// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import FavoriteButton from '@/components/event/FavoriteButton'

vi.mock('@/hooks/useFavorite', () => ({
  useFavorite: vi.fn(),
}))

import { useFavorite } from '@/hooks/useFavorite'

const mockUseFavorite = vi.mocked(useFavorite)

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('FavoriteButton', () => {
  it('renders an unfilled star when not favorited', () => {
    mockUseFavorite.mockReturnValue({ favorited: false, loading: false, toggle: vi.fn() })
    render(<FavoriteButton eventId={1} />)
    const btn = screen.getByRole('button')
    expect(btn.getAttribute('aria-label')).toBe('Ajouter aux favoris')
  })

  it('renders a filled star when favorited', () => {
    mockUseFavorite.mockReturnValue({ favorited: true, loading: false, toggle: vi.fn() })
    render(<FavoriteButton eventId={1} />)
    const btn = screen.getByRole('button')
    expect(btn.getAttribute('aria-label')).toBe('Retirer des favoris')
  })

  it('is disabled while loading', () => {
    mockUseFavorite.mockReturnValue({ favorited: false, loading: true, toggle: vi.fn() })
    render(<FavoriteButton eventId={1} />)
    expect((screen.getByRole('button') as HTMLButtonElement).disabled).toBe(true)
  })

  it('calls toggle on click', async () => {
    const toggle = vi.fn().mockResolvedValue(true)
    mockUseFavorite.mockReturnValue({ favorited: false, loading: false, toggle })
    render(<FavoriteButton eventId={1} />)
    fireEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(toggle).toHaveBeenCalledTimes(1))
  })

  it('calls onRemove when unfavoriting succeeds', async () => {
    const onRemove = vi.fn()
    const toggle = vi.fn().mockResolvedValue(true)
    mockUseFavorite.mockReturnValue({ favorited: true, loading: false, toggle })
    render(<FavoriteButton eventId={1} onRemove={onRemove} />)
    fireEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(onRemove).toHaveBeenCalledTimes(1))
  })

  it('does not call onRemove when toggle fails', async () => {
    const onRemove = vi.fn()
    const toggle = vi.fn().mockResolvedValue(false)
    mockUseFavorite.mockReturnValue({ favorited: true, loading: false, toggle })
    render(<FavoriteButton eventId={1} onRemove={onRemove} />)
    fireEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(toggle).toHaveBeenCalled())
    expect(onRemove).not.toHaveBeenCalled()
  })

  it('does not call onRemove when adding a favorite', async () => {
    const onRemove = vi.fn()
    const toggle = vi.fn().mockResolvedValue(true)
    mockUseFavorite.mockReturnValue({ favorited: false, loading: false, toggle })
    render(<FavoriteButton eventId={1} onRemove={onRemove} />)
    fireEvent.click(screen.getByRole('button'))
    await waitFor(() => expect(toggle).toHaveBeenCalled())
    expect(onRemove).not.toHaveBeenCalled()
  })
})
