import type { ChangeEvent } from 'react'
import type { SearchFilters } from '@/types/search'
import { EVENT_CATEGORIES, type EventCategory } from '@/types/event'
import { FACULTIES, type Faculty } from '@/types/faculty'
import TagInput from '@/components/utils/TagInput'

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

export default function EventSearchSidebar({ filters, setFilters, resetFilters }: FilterSidebarProps) {
  function handleCategoryChange(e: ChangeEvent<HTMLInputElement>) {
    setFilters({...filters, category: e.target.value as EventCategory || undefined})
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
          {Object.entries(EVENT_CATEGORIES).map(([id, category]) => (
            <label
              key={id}
              className="flex items-center gap-2.5 cursor-pointer text-sm text-foreground/70 hover:text-foreground transition-colors"
            >
              <input
                type="radio"
                name="category"
                value={id}
                checked={filters.category === id}
                onChange={handleCategoryChange}
                onClick={() => { if (filters.category === id) setFilters({ ...filters, category: undefined }) }}
                className="accent-accent"
              />
              {category.name}
            </label>
          ))}
        </div>
      </div>

      <div className="border-t border-border/50" />

      {/* Faculty — chips (mutex entre le chip « Toutes facultés » et les facultés nommées) */}
      <div>
        <SectionLabel>Faculté</SectionLabel>
        <div className="flex flex-wrap gap-2">
          <button
            key="NONE"
            type="button"
            onClick={() => setFilters({
              ...filters,
              facultyNone: filters.facultyNone ? undefined : true,
              faculty: filters.facultyNone ? filters.faculty : undefined,
            })}
            className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors cursor-pointer ${
              filters.facultyNone
                ? 'bg-accent text-white border-accent'
                : 'border-border text-foreground/60 hover:border-accent/40 hover:text-foreground'
            }`}
          >
            Toutes facultés
          </button>
          {Object.entries(FACULTIES).map(([id, faculty]) => (
            <button
              key={id}
              type="button"
              onClick={() => setFilters({
                ...filters,
                faculty: filters.faculty === id ? undefined : id as Faculty,
                facultyNone: undefined,
              })}
              className={`px-3 py-1.5 rounded-full text-xs font-semibold border transition-colors cursor-pointer ${
                filters.faculty === id && !filters.facultyNone
                  ? 'bg-accent text-white border-accent'
                  : 'border-border text-foreground/60 hover:border-accent/40 hover:text-foreground'
              }`}
            >
              {faculty.abbr}
            </button>
          ))}
        </div>
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

      {/* Tags (SCRUM-132) */}
      <div>
        <SectionLabel>Mots-clés</SectionLabel>
        <TagInput
          value={filters.tags ?? []}
          onChange={(tags) =>
            setFilters({ ...filters, tags: tags.length > 0 ? tags : undefined })
          }
          placeholder="Ex. football, art…"
          maxTags={10}
        />
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