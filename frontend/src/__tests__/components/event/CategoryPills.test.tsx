// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import CategoryPills from '@/components/event/CategoryPills'
import { EVENT_CATEGORIES } from '@/types/event'
import type { EventCategory } from '@/types/event'

afterEach(() => {
  cleanup()
})

describe('CategoryPills', () => {
  it('renders a pill button for each category', () => {
    render(<CategoryPills value="" onChange={vi.fn()} />)
    for (const { name } of Object.values(EVENT_CATEGORIES)) {
      expect(screen.getByRole('button', { name })).toBeTruthy()
    }
  })

  it('calls onChange with the category id when a pill is clicked', () => {
    const onChange = vi.fn()
    render(<CategoryPills value="" onChange={onChange} />)
    fireEvent.click(screen.getByRole('button', { name: 'Conférence' }))
    expect(onChange).toHaveBeenCalledWith('CONFERENCE')
  })

  it('applies active styling to the selected category pill', () => {
    render(<CategoryPills value={'CONFERENCE' as EventCategory} onChange={vi.fn()} />)
    const activeBtn = screen.getByRole('button', { name: 'Conférence' })
    expect(activeBtn.className).toContain('bg-accent')
  })

  it('applies inactive styling to non-selected pills', () => {
    render(<CategoryPills value={'CONFERENCE' as EventCategory} onChange={vi.fn()} />)
    const inactiveBtn = screen.getByRole('button', { name: 'Social' })
    expect(inactiveBtn.className).toContain('border-border')
  })

  it('shows error message when error prop is provided', () => {
    render(<CategoryPills value="" onChange={vi.fn()} error="La catégorie est requise." />)
    expect(screen.getByText('La catégorie est requise.')).toBeTruthy()
  })

  it('does not show error message when error prop is absent', () => {
    render(<CategoryPills value="" onChange={vi.fn()} />)
    expect(screen.queryByText('La catégorie est requise.')).toBeNull()
  })
})
