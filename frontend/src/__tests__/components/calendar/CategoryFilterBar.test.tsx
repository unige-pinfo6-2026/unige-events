// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import CategoryFilterBar from '@/components/calendar/CategoryFilterBar'
import { EVENT_CATEGORIES, type EventCategory } from '@/types/event'

afterEach(() => {
  cleanup()
})

const ALL_CATEGORIES = Object.keys(EVENT_CATEGORIES) as EventCategory[]

describe('CategoryFilterBar', () => {
  it('renders one chip per EVENT_CATEGORIES entry', () => {
    render(<CategoryFilterBar disabled={new Set()} onToggle={vi.fn()} />)
    for (const key of ALL_CATEGORIES) {
      expect(screen.getByText(EVENT_CATEGORIES[key].name)).toBeTruthy()
    }
  })

  it('marks every chip aria-pressed=true when the disabled set is empty', () => {
    render(<CategoryFilterBar disabled={new Set()} onToggle={vi.fn()} />)
    const chips = screen.getAllByRole('button', { pressed: true })
    expect(chips.length).toBe(ALL_CATEGORIES.length)
  })

  it('marks a chip aria-pressed=false when its category is in the disabled set', () => {
    render(<CategoryFilterBar disabled={new Set<EventCategory>(['SPORTS'])} onToggle={vi.fn()} />)
    expect(screen.getByRole('button', { name: /Afficher la catégorie Sports/i })).toBeTruthy()
    // Other chips remain pressed.
    expect(screen.getByRole('button', { name: /Masquer la catégorie Académique/i })).toBeTruthy()
  })

  it('calls onToggle with the category key when a chip is clicked', () => {
    const onToggle = vi.fn()
    render(<CategoryFilterBar disabled={new Set()} onToggle={onToggle} />)
    fireEvent.click(screen.getByRole('button', { name: /catégorie Académique/i }))
    expect(onToggle).toHaveBeenCalledWith('ACADEMIC')
    fireEvent.click(screen.getByRole('button', { name: /catégorie Autre/i }))
    expect(onToggle).toHaveBeenCalledWith('OTHER')
  })

  it('exposes a group landmark with a French aria-label', () => {
    render(<CategoryFilterBar disabled={new Set()} onToggle={vi.fn()} />)
    expect(screen.getByRole('group', { name: /Filtrer les événements par catégorie/i })).toBeTruthy()
  })

  it('applies the active background tint (~15 % alpha of the category color) to active chips', () => {
    render(<CategoryFilterBar disabled={new Set()} onToggle={vi.fn()} />)
    const academique = screen.getByRole('button', { name: /Masquer la catégorie Académique/i })
    // EVENT_CATEGORIES.ACADEMIC.color = '#2563eb' (37, 99, 235) ; we append
    // alpha '26' (≈0.15) in CSS — jsdom normalises that to rgba(...).
    expect(academique.style.backgroundColor).toBe('rgba(37, 99, 235, 0.15)')
  })

  it('inactive chips do not carry an inline backgroundColor', () => {
    render(<CategoryFilterBar disabled={new Set<EventCategory>(['SPORTS'])} onToggle={vi.fn()} />)
    const sports = screen.getByRole('button', { name: /Afficher la catégorie Sports/i })
    expect(sports.style.backgroundColor).toBe('')
  })
})
