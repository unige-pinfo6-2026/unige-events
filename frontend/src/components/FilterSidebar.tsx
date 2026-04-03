import type { ChangeEvent } from 'react'
import type { SearchFilters } from '../hooks/useSearch'
import { EventCategory, Faculty } from '../types'

const CATEGORY_LABELS: Record<string, string> = {
  ACADEMIC: 'Académique',
  SPORTS: 'Sports',
  CULTURAL: 'Culturel',
  SOCIAL: 'Social',
  CONFERENCE: 'Conférence',
  OTHER: 'Autre',
}

const FACULTY_LABELS: Record<string, string> = {
  SCIENCES: 'Sciences',
  LETTRES: 'Lettres',
  DROIT: 'Droit',
  MEDECINE: 'Médecine',
  SES: 'SES',
  PSYCHOLOGIE: 'Psychologie',
  THEOLOGIE: 'Théologie',
  FTI: 'FTI',
  GSI: 'GSI',
}

interface FilterSidebarProps {
  filters: SearchFilters
  setFilters: (f: SearchFilters) => void
  resetFilters: () => void
}

function FilterSidebar({ filters, setFilters, resetFilters }: FilterSidebarProps) {
  function handleFacultyChange(e: ChangeEvent<HTMLSelectElement>) {
    setFilters({
      ...filters,
      faculty: (e.target.value as (typeof Faculty)[keyof typeof Faculty]) || undefined,
    })
  }

  function handleDateFromChange(e: ChangeEvent<HTMLInputElement>) {
    setFilters({ ...filters, dateFrom: e.target.value || undefined })
  }

  function handleDateToChange(e: ChangeEvent<HTMLInputElement>) {
    setFilters({ ...filters, dateTo: e.target.value || undefined })
  }

  const sectionLabelStyle: React.CSSProperties = {
    fontWeight: 700,
    fontSize: '0.8rem',
    textTransform: 'uppercase',
    letterSpacing: '0.06em',
    color: 'var(--text-muted)',
    marginBottom: '0.75rem',
  }

  const inputStyle: React.CSSProperties = {
    width: '100%',
    padding: '0.4rem 0.5rem',
    borderRadius: 8,
    border: '1px solid var(--input-border)',
    background: 'var(--input-bg)',
    color: 'var(--text-primary)',
    fontSize: '0.875rem',
    boxSizing: 'border-box',
  }

  return (
    <aside
      style={{
        background: 'var(--card-bg)',
        border: '1px solid var(--card-border)',
        borderRadius: 16,
        padding: '1.5rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '1.25rem',
        transition: 'background 0.3s, border-color 0.3s',
      }}
    >
      {/* Category */}
      <div>
        <div style={sectionLabelStyle}>Catégorie</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          {Object.values(EventCategory).map((cat) => (
            <label
              key={cat}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '0.5rem',
                cursor: 'pointer',
                fontSize: '0.875rem',
                color: 'var(--text-primary)',
              }}
            >
              <input
                type='radio'
                name='category'
                value={cat}
                checked={filters.category === cat}
                onChange={() => setFilters({ ...filters, category: cat })}
                onClick={() => {
                  if (filters.category === cat) setFilters({ ...filters, category: undefined })
                }}
              />
              {CATEGORY_LABELS[cat]}
            </label>
          ))}
        </div>
      </div>

      {/* Faculty */}
      <div>
        <div style={sectionLabelStyle}>Faculté</div>
        <select
          value={filters.faculty ?? ''}
          onChange={handleFacultyChange}
          style={{ ...inputStyle, padding: '0.5rem' }}
        >
          <option value=''>Toutes les facultés</option>
          {Object.values(Faculty).map((fac) => (
            <option key={fac} value={fac}>
              {FACULTY_LABELS[fac]}
            </option>
          ))}
        </select>
      </div>

      {/* Date range */}
      <div>
        <div style={sectionLabelStyle}>Date</div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
          <div>
            <label
              htmlFor='filter-date-from'
              style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', display: 'block', marginBottom: '0.25rem' }}
            >
              De
            </label>
            <input
              id='filter-date-from'
              type='date'
              value={filters.dateFrom ?? ''}
              onChange={handleDateFromChange}
              style={inputStyle}
            />
          </div>
          <div>
            <label
              htmlFor='filter-date-to'
              style={{ fontSize: '0.8125rem', color: 'var(--text-secondary)', display: 'block', marginBottom: '0.25rem' }}
            >
              Au
            </label>
            <input
              id='filter-date-to'
              type='date'
              value={filters.dateTo ?? ''}
              onChange={handleDateToChange}
              style={inputStyle}
            />
          </div>
        </div>
      </div>

      {/* Reset */}
      <button
        type='button'
        onClick={resetFilters}
        style={{
          padding: '0.5rem',
          borderRadius: 8,
          border: '1px solid var(--input-border)',
          background: 'var(--btn-secondary-bg)',
          color: 'var(--text-secondary)',
          fontSize: '0.875rem',
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        Réinitialiser les filtres
      </button>
    </aside>
  )
}

export default FilterSidebar
