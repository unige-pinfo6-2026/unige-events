// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import CategorySelect from '@/components/event/CategorySelect'
import { EVENT_CATEGORIES } from '@/types/event'
import type { EventCategory } from '@/types/event'

afterEach(() => {
  cleanup()
})

describe('CategorySelect', () => {
  it('renders a select with a default empty option', () => {
    render(<CategorySelect value="" onChange={vi.fn()} />)
    const select = screen.getByRole('combobox')
    expect(select).toBeTruthy()
    expect(screen.getByText('Choisir une catégorie…')).toBeTruthy()
  })

  it('renders an option for each category', () => {
    render(<CategorySelect value="" onChange={vi.fn()} />)
    for (const { name } of Object.values(EVENT_CATEGORIES)) {
      expect(screen.getByRole('option', { name })).toBeTruthy()
    }
  })

  it('calls onChange with the selected category', () => {
    const onChange = vi.fn()
    render(<CategorySelect value="" onChange={onChange} />)
    fireEvent.change(screen.getByRole('combobox'), { target: { value: 'SOCIAL' } })
    expect(onChange).toHaveBeenCalledWith('SOCIAL')
  })

  it('does not call onChange when the empty placeholder is selected', () => {
    const onChange = vi.fn()
    render(<CategorySelect value={'CONFERENCE' as EventCategory} onChange={onChange} />)
    fireEvent.change(screen.getByRole('combobox'), { target: { value: '' } })
    expect(onChange).not.toHaveBeenCalled()
  })

  it('applies error border class when error prop is provided', () => {
    render(<CategorySelect value="" onChange={vi.fn()} error="La catégorie est requise." />)
    const select = screen.getByRole('combobox')
    expect(select.className).toContain('border-error')
  })

  it('shows the error message text when error prop is provided', () => {
    render(<CategorySelect value="" onChange={vi.fn()} error="La catégorie est requise." />)
    expect(screen.getByText('La catégorie est requise.')).toBeTruthy()
  })

  it('applies default border class when no error', () => {
    render(<CategorySelect value="" onChange={vi.fn()} />)
    const select = screen.getByRole('combobox')
    expect(select.className).toContain('border-border')
  })

  it('shows a color dot when a category is selected', () => {
    render(<CategorySelect value={'CONFERENCE' as EventCategory} onChange={vi.fn()} />)
    const dot = document.querySelector('[aria-hidden="true"]') as HTMLElement
    expect(dot.style.backgroundColor).toBe('rgb(8, 145, 178)')
  })

  it('shows a transparent dot when no category is selected', () => {
    render(<CategorySelect value="" onChange={vi.fn()} />)
    const dot = document.querySelector('[aria-hidden="true"]') as HTMLElement
    expect(dot.style.backgroundColor).toBe('transparent')
  })
})
