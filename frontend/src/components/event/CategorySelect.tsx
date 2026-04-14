import { ChevronDown } from 'lucide-react'
import type { EventCategory } from '@/types/event'
import { EVENT_CATEGORIES } from '@/types/event'
import { Select } from '@/components/utils/FormField'

interface CategorySelectProps {
  id?: string
  value: '' | EventCategory
  onChange: (category: EventCategory) => void
  error?: string
}

export default function CategorySelect({ id, value, onChange, error }: Readonly<CategorySelectProps>) {
  const dotColor = value ? EVENT_CATEGORIES[value].color : 'transparent'

  return (
    <div>
      <div className="relative">
        <span
          className="absolute left-4 top-1/2 -translate-y-1/2 w-2.5 h-2.5 rounded-full shrink-0 transition-colors pointer-events-none"
          style={{ backgroundColor: dotColor }}
          aria-hidden="true"
        />
        <Select
          id={id}
          value={value}
          onChange={(e) => { if (e.target.value) onChange(e.target.value as EventCategory) }}
          error={error}
          className="appearance-none cursor-pointer pl-9 pr-9"
        >
          <option value="">Choisir une catégorie…</option>
          {Object.entries(EVENT_CATEGORIES).map(([catId, cat]) => (
            <option key={catId} value={catId}>{cat.name}</option>
          ))}
        </Select>
        <ChevronDown className="absolute right-3 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/40 pointer-events-none" />
      </div>
      {error && <p className="text-xs text-error mt-1.5">{error}</p>}
    </div>
  )
}
