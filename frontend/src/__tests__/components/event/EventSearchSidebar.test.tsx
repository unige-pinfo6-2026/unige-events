// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import EventSearchSidebar from '@/components/event/EventSearchSidebar'
import { FACULTIES } from '@/types/faculty'
import type { Faculty } from '@/types/faculty'
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
    <EventSearchSidebar filters={filters} setFilters={setFilters} resetFilters={resetFilters} />,
  )
}

const ALL_IDS = Object.keys(FACULTIES) as Faculty[]

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

  it('renders a chip for every Faculty value', () => {
    renderSidebar()
    for (const id of ALL_IDS) {
      expect(screen.getByRole('button', { name: FACULTIES[id].abbr })).toBeTruthy()
    }
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

  it('selects a faculty chip and calls setFilters with that faculty', () => {
    const setFilters = vi.fn()
    renderSidebar(defaultFilters, setFilters)
    fireEvent.click(screen.getByRole('button', { name: 'Sciences' }))
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ faculty: 'SCIENCES' }))
  })

  it('deselects faculty chip when clicking the active chip', () => {
    const setFilters = vi.fn()
    renderSidebar({ includePast: false, faculty: 'SCIENCES' }, setFilters)
    fireEvent.click(screen.getByRole('button', { name: 'Sciences' }))
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ faculty: undefined }))
  })

  it('no faculty chip appears active when no faculty filter is set', () => {
    renderSidebar()
    const chips = ALL_IDS.map((id) => screen.getByRole('button', { name: FACULTIES[id].abbr }))
    for (const chip of chips) {
      expect(chip.className).not.toContain('bg-accent')
    }
  })

  it('the active faculty chip has bg-accent class', () => {
    renderSidebar({ includePast: false, faculty: 'LAW' })
    const chip = screen.getByRole('button', { name: 'Droit' })
    expect(chip.className).toContain('bg-accent')
  })

  it('renders a "Toutes facultés" chip alongside the faculty chips', () => {
    renderSidebar()
    expect(screen.getByRole('button', { name: 'Toutes facultés' })).toBeTruthy()
  })

  it('clicking_toutesFacultes_chip_sets_facultyNone_true', () => {
    const setFilters = vi.fn()
    renderSidebar(defaultFilters, setFilters)
    fireEvent.click(screen.getByRole('button', { name: 'Toutes facultés' }))
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ facultyNone: true }))
  })

  it('clicking_toutesFacultes_chip_clears_faculty', () => {
    const setFilters = vi.fn()
    renderSidebar({ includePast: false, faculty: 'SCIENCES' }, setFilters)
    fireEvent.click(screen.getByRole('button', { name: 'Toutes facultés' }))
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({
      facultyNone: true,
      faculty: undefined,
    }))
  })

  it('clicking_faculty_chip_clears_facultyNone', () => {
    const setFilters = vi.fn()
    renderSidebar({ includePast: false, facultyNone: true }, setFilters)
    fireEvent.click(screen.getByRole('button', { name: 'Sciences' }))
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({
      faculty: 'SCIENCES',
      facultyNone: undefined,
    }))
  })

  it('toutes_facultes_chip_is_active_when_facultyNone_is_true', () => {
    renderSidebar({ includePast: false, facultyNone: true })
    const chip = screen.getByRole('button', { name: 'Toutes facultés' })
    expect(chip.className).toContain('bg-accent')
  })

  it('deselect_toutes_facultes_chip_clears_facultyNone', () => {
    const setFilters = vi.fn()
    renderSidebar({ includePast: false, facultyNone: true }, setFilters)
    fireEvent.click(screen.getByRole('button', { name: 'Toutes facultés' }))
    expect(setFilters).toHaveBeenCalledWith(expect.objectContaining({ facultyNone: undefined }))
  })

  it('no faculty chip appears active when facultyNone is true', () => {
    renderSidebar({ includePast: false, facultyNone: true, faculty: 'SCIENCES' })
    const sciencesChip = screen.getByRole('button', { name: 'Sciences' })
    expect(sciencesChip.className).not.toContain('bg-accent')
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
})
