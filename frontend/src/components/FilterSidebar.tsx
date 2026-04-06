import type { ChangeEvent } from 'react'
import type { SearchFilters } from '@/hooks/useSearch'
import { EventCategory, Faculty } from '@/types'

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

function SectionLabel({ children }: { children: React.ReactNode }) {
  return (
    <p className="text-xs font-bold uppercase tracking-widest text-foreground/40 mb-3">
      {children}
    </p>
  )
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

  return (
    <aside className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl border border-border p-6 flex flex-col gap-5">
      {/* Category */}
      <div>
        <SectionLabel>Catégorie</SectionLabel>
        <div className="flex flex-col gap-2">
          {Object.values(EventCategory).map((cat) => (
            <label
              key={cat}
              className="flex items-center gap-2.5 cursor-pointer text-sm text-foreground/70 hover:text-foreground transition-colors"
            >
              <input
                type="radio"
                name="category"
                value={cat}
                checked={filters.category === cat}
                onChange={() => setFilters({ ...filters, category: cat })}
                onClick={() => {
                  if (filters.category === cat) setFilters({ ...filters, category: undefined })
                }}
                className="accent-accent"
              />
              {CATEGORY_LABELS[cat]}
            </label>
          ))}
        </div>
      </div>

      <div className="border-t border-border/50" />

      {/* Faculty — TODO: SCRUM-77 — filtre activé quand le champ faculty sera ajouté à l'entité Event */}
      <div>
        <SectionLabel>Faculté</SectionLabel>
        <select
          value={filters.faculty ?? ''}
          onChange={handleFacultyChange}
          disabled
          aria-disabled="true"
          className="w-full px-3 py-2 rounded-xl border border-border bg-background/60 text-sm opacity-50 cursor-not-allowed"
        >
          <option value="">Toutes les facultés</option>
          {Object.values(Faculty).map((fac) => (
            <option key={fac} value={fac}>
              {FACULTY_LABELS[fac]}
            </option>
          ))}
        </select>
        <p className="mt-1.5 text-xs text-foreground/30">Bientôt disponible</p>
      </div>

      <div className="border-t border-border/50" />

      {/* Date range */}
      <div>
        <SectionLabel>Date</SectionLabel>
        <div className="flex flex-col gap-3">
          <div>
            <label htmlFor="filter-date-from" className="block text-xs text-foreground/50 mb-1.5">
              De
            </label>
            <input
              id="filter-date-from"
              type="date"
              value={filters.dateFrom ?? ''}
              onChange={handleDateFromChange}
              className="w-full px-3 py-2 rounded-xl border border-border bg-background/60 text-foreground text-sm focus:border-accent/60 focus:outline-none transition-colors"
            />
          </div>
          <div>
            <label htmlFor="filter-date-to" className="block text-xs text-foreground/50 mb-1.5">
              Au
            </label>
            <input
              id="filter-date-to"
              type="date"
              value={filters.dateTo ?? ''}
              onChange={handleDateToChange}
              className="w-full px-3 py-2 rounded-xl border border-border bg-background/60 text-foreground text-sm focus:border-accent/60 focus:outline-none transition-colors"
            />
          </div>
        </div>
      </div>

      <div className="border-t border-border/50" />

      {/* Include past events */}
      <label className="flex items-center gap-2.5 cursor-pointer text-sm text-foreground/70 hover:text-foreground transition-colors">
        <input
          type="checkbox"
          checked={filters.includePast}
          onChange={() => setFilters({ ...filters, includePast: !filters.includePast })}
          className="accent-accent"
        />
        Afficher les événements passés
      </label>

      {/* Reset */}
      <button
        type="button"
        onClick={resetFilters}
        className="w-full py-2.5 rounded-xl border border-border hover:border-accent/40 text-foreground/60 hover:text-foreground text-sm font-semibold transition-colors cursor-pointer bg-transparent"
      >
        Réinitialiser les filtres
      </button>
    </aside>
  )
}

export default FilterSidebar
