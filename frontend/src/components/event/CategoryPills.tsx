import type { EventCategory } from '@/types/event'
import { EVENT_CATEGORIES } from '@/types/event'

interface CategoryPillsProps {
  value: '' | EventCategory
  onChange: (category: EventCategory) => void
  error?: string
}

const pillVariants = {
  active: 'bg-accent text-white shadow-sm shadow-accent/30 border-transparent',
  inactive: 'border border-border text-foreground/60 hover:border-accent/50 hover:text-foreground/80 bg-transparent',
} as const

export default function CategoryPills({ value, onChange, error }: Readonly<CategoryPillsProps>) {
  return (
    <div>
      <div className="flex flex-wrap gap-2">
        {Object.entries(EVENT_CATEGORIES).map(([id, category]) => {
          const variant: keyof typeof pillVariants = value === id ? 'active' : 'inactive'
          return (
            <button
              key={id}
              type="button"
              onClick={() => onChange(id as EventCategory)}
              className={`px-3 py-1.5 rounded-lg text-sm font-medium transition-all cursor-pointer ${pillVariants[variant]}`}
            >
              {category.name}
            </button>
          )
        })}
      </div>
      {error && <p className="text-xs text-error mt-1.5">{error}</p>}
    </div>
  )
}
