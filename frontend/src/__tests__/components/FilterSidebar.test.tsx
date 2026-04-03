// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import FilterSidebar from '../../components/FilterSidebar'
import type { SearchFilters } from '../../hooks/useSearch'

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

const defaultFilters: SearchFilters = {}

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
    expect(screen.getByText('Sciences')).toBeTruthy()
    expect(screen.getByText('Médecine')).toBeTruthy()
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
    renderSidebar({ category: 'SPORTS' })
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
    renderSidebar({ category: 'ACADEMIC' }, setFilters)
    const radios = screen.getAllByRole('radio') as HTMLInputElement[]
    const academicRadio = radios.find((r) => r.closest('label')?.textContent?.includes('Académique'))!
    fireEvent.click(academicRadio)
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ category: undefined }))
  })

  it('calls setFilters with the selected faculty', () => {
    const setFilters = vi.fn()
    renderSidebar(defaultFilters, setFilters)
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: 'SCIENCES' } })
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ faculty: 'SCIENCES' }))
  })

  it('clears faculty when empty option is selected', () => {
    const setFilters = vi.fn()
    renderSidebar({ faculty: 'SCIENCES' }, setFilters)
    const select = screen.getByRole('combobox')
    fireEvent.change(select, { target: { value: '' } })
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ faculty: undefined }))
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
    renderSidebar({ dateFrom: '2026-05-01' }, setFilters)
    fireEvent.change(screen.getByLabelText('De'), { target: { value: '' } })
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ dateFrom: undefined }))
  })

  it('shows the current dateFrom value', () => {
    renderSidebar({ dateFrom: '2026-05-01' })
    const input = screen.getByLabelText('De') as HTMLInputElement
    expect(input.value).toBe('2026-05-01')
  })

  it('shows the current faculty value', () => {
    renderSidebar({ faculty: 'DROIT' })
    const select = screen.getByRole('combobox') as HTMLSelectElement
    expect(select.value).toBe('DROIT')
  })
})
