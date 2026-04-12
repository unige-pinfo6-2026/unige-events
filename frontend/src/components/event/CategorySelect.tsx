import { ChevronDown } from 'lucide-react'
import type { EventCategory } from '@/types/event'
import { EVENT_CATEGORIES } from '@/types/event'

interface CategorySelectProps {
  id?: string
  value: '' | EventCategory
  onChange: (category: EventCategory) => void
  error?: string
}

const selectVariants = {
  default: 'border-border bg-background text-foreground focus:border-accent/60',
  error:   'border-error bg-background text-foreground focus:border-error',
} as const

export default function CategorySelect({ id, value, onChange, error }: Readonly<CategorySelectProps>) {
  const dotColor = value ? EVENT_CATEGORIES[value].color : 'transparent'
  const variant: keyof typeof selectVariants = error ? 'error' : 'default'

  return (
    <div>
      <div className="relative flex items-center gap-2">
        <span
          className="w-2.5 h-2.5 rounded-full shrink-0 transition-colors"
          style={{ backgroundColor: dotColor }}
          aria-hidden="true"
        />
        <div className="relative flex-1">
          <select
            id={id}
            value={value}
            onChange={(e) => { if (e.target.value) onChange(e.target.value as EventCategory) }}
            className={`w-full appearance-none rounded-xl border px-3 py-2 text-sm transition-colors outline-none cursor-pointer pr-8 ${selectVariants[variant]}`}
          >
            <option value="">Choisir une catégorie…</option>
            {Object.entries(EVENT_CATEGORIES).map(([catId, cat]) => (
              <option key={catId} value={catId}>{cat.name}</option>
            ))}
          </select>
          <ChevronDown className="absolute right-2 top-1/2 -translate-y-1/2 w-4 h-4 text-foreground/40 pointer-events-none" />
        </div>
      </div>
      {error && <p className="text-xs text-error mt-1.5">{error}</p>}
    </div>
  )
}
