// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import FilterSidebar from '@/components/search/FilterSidebar'
import type { SearchFilters } from '@/types/search'

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const defaultFilters: SearchFilters = { includePast: false }

function renderSidebar(
  filters: SearchFilters = defaultFilters,
  setFilters = vi.fn(),
  resetFilters = vi.fn(),
) {
  return render(
    <FilterSidebar filters={filters} setFilters={setFilters} resetFilters={resetFilters} />,
  )
}

describe('FilterSidebar', () => {
  it('renders the includePast checkbox', () => {
    renderSidebar()
    expect(screen.getByText('Afficher les événements passés')).toBeTruthy()
  })

  it('includePast checkbox is unchecked by default', () => {
    renderSidebar()
    const checkbox = screen.getByRole('checkbox') as HTMLInputElement
    expect(checkbox.checked).toBe(false)
  })

  it('includePast checkbox is checked when filter is true', () => {
    renderSidebar({ includePast: true })
    const checkbox = screen.getByRole('checkbox') as HTMLInputElement
    expect(checkbox.checked).toBe(true)
  })

  it('calls setFilters with toggled includePast when checkbox changes', () => {
    const setFilters = vi.fn()
    renderSidebar({ includePast: false }, setFilters)
    fireEvent.click(screen.getByRole('checkbox'))
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ includePast: true }))
  })

  it('renders all category checkboxes', () => {
    renderSidebar()
    expect(screen.getByText('Académique')).toBeTruthy()
    expect(screen.getByText('Sports')).toBeTruthy()
    expect(screen.getByText('Culturel')).toBeTruthy()
    expect(screen.getByText('Social')).toBeTruthy()
    expect(screen.getByText('Conférence')).toBeTruthy()
    expect(screen.getByText('Autre')).toBeTruthy()
  })

  it('renders the faculty select with all options', () => {
    renderSidebar()
    expect(screen.getByText('Toutes les facultés')).toBeTruthy()
    expect(screen.getByText('Faculté des Sciences')).toBeTruthy()
    expect(screen.getByText('Faculté de Médecine')).toBeTruthy()
  })

  it('renders date from and date to inputs', () => {
    renderSidebar()
    expect(screen.getByLabelText('De')).toBeTruthy()
    expect(screen.getByLabelText('Au')).toBeTruthy()
  })

  it('renders reset button', () => {
    renderSidebar()
    expect(screen.getByRole('button', { name: 'Réinitialiser les filtres' })).toBeTruthy()
  })

  it('calls resetFilters when reset button is clicked', () => {
    const resetFilters = vi.fn()
    renderSidebar(defaultFilters, vi.fn(), resetFilters)
    fireEvent.click(screen.getByRole('button', { name: 'Réinitialiser les filtres' }))
    expect(resetFilters).toHaveBeenCalledOnce()
  })

  it('checks the radio matching the current category filter', () => {
    renderSidebar({ includePast: false, category: 'SPORTS' })
    const radios = screen.getAllByRole('radio') as HTMLInputElement[]
    const sportsRadio = radios.find((r) => r.closest('label')?.textContent?.includes('Sports'))
    expect(sportsRadio?.checked).toBe(true)
  })

  it('leaves all radios unchecked when no category is selected', () => {
    renderSidebar()
    const radios = screen.getAllByRole('radio') as HTMLInputElement[]
    expect(radios.every((r) => !r.checked)).toBe(true)
  })

  it('calls setFilters with the selected category when a radio is clicked', () => {
    const setFilters = vi.fn()
    renderSidebar(defaultFilters, setFilters)
    const radios = screen.getAllByRole('radio')
    fireEvent.click(radios[0]) // ACADEMIC
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ category: 'ACADEMIC' }))
  })

  it('deselects a category when its radio is clicked again', () => {
    const setFilters = vi.fn()
    renderSidebar({ includePast: false, category: 'ACADEMIC' }, setFilters)
    const radios = screen.getAllByRole('radio') as HTMLInputElement[]
    const academicRadio = radios.find((r) => r.closest('label')?.textContent?.includes('Académique'))!
    fireEvent.click(academicRadio)
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ category: undefined }))
  })

  it('faculty select is disabled (SCRUM-77 not yet implemented)', () => {
    renderSidebar()
    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.disabled).toBe(true)
  })

  it('shows "Bientôt disponible" label below the faculty select', () => {
    renderSidebar()
    expect(screen.getByText('Bientôt disponible')).toBeTruthy()
  })

  it('calls setFilters with dateFrom when date input changes', () => {
    const setFilters = vi.fn()
    renderSidebar(defaultFilters, setFilters)
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '2026-05-01' } })
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ dateFrom: '2026-05-01' }))
  })

  it('calls setFilters with dateTo when date input changes', () => {
    const setFilters = vi.fn()
    renderSidebar(defaultFilters, setFilters)
    fireEvent.change(screen.getByLabelText('Au'), { target: { value: '2026-05-31' } })
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ dateTo: '2026-05-31' }))
  })

  it('clears dateFrom when input is cleared', () => {
    const setFilters = vi.fn()
    renderSidebar({ includePast: false, dateFrom: '2026-05-01' }, setFilters)
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '' } })
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ dateFrom: undefined }))
  })

  it('shows the current dateFrom value', () => {
    renderSidebar({ includePast: false, dateFrom: '2026-05-01' })
    const input = screen.getByLabelText('De') as HTMLInputElement
    expect(input.value).toBe('2026-05-01')
  })

  it('shows the current faculty value', () => {
    renderSidebar({ includePast: false, faculty: 'LAW' })
    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.value).toBe('LAW')
  })
})
