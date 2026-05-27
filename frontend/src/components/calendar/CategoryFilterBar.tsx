import { EVENT_CATEGORIES, type EventCategory } from '@/types/event'

interface CategoryFilterBarProps {
  /** Set of categories currently masked. Empty set = all categories visible. */
  disabled: ReadonlySet<EventCategory>
  /** Toggle a category in/out of the disabled set. */
  onToggle: (category: EventCategory) => void
}

// 8-digit hex (#RRGGBBAA) — alpha appended to the category's hex color.
// 26 ≈ 15 % (active background), 66 ≈ 40 % (active border).
const ACTIVE_BG_ALPHA = '26'
const ACTIVE_BORDER_ALPHA = '66'

const chipVariants = {
  active:   'border text-foreground hover:brightness-110',
  inactive: 'bg-foreground/5 border-border text-foreground/40 hover:text-foreground/60',
} as const

/**
 * Cliquable category legend that doubles as a filter. Each chip toggles the
 * visibility of its category on the parent component's event list. Active
 * chips are tinted with the category's color (15 % bg / 40 % border) ;
 * inactive chips fade out to indicate that category is currently hidden.
 *
 * <p>Stateless — the disabled-set lives in the parent (CalendarPage) so the
 * filter logic stays close to the event source. Reusable on other listing
 * pages (search, feed) that hold a similar set.
 */
export default function CategoryFilterBar({
  disabled,
  onToggle,
}: Readonly<CategoryFilterBarProps>) {
  return (
    <div
      role="group"
      aria-label="Filtrer les événements par catégorie"
      className="flex flex-wrap gap-2"
    >
      {(Object.entries(EVENT_CATEGORIES) as Array<
        [EventCategory, (typeof EVENT_CATEGORIES)[EventCategory]]
      >).map(([key, cat]) => {
        const isActive = !disabled.has(key)
        const variantKey = isActive ? 'active' : 'inactive'
        const chipStyle = isActive
          ? {
              backgroundColor: cat.color + ACTIVE_BG_ALPHA,
              borderColor: cat.color + ACTIVE_BORDER_ALPHA,
            }
          : undefined
        const dotStyle = isActive
          ? { backgroundColor: cat.color }
          : { borderColor: cat.color }
        return (
          <button
            key={key}
            type="button"
            aria-pressed={isActive}
            aria-label={`${isActive ? 'Masquer' : 'Afficher'} la catégorie ${cat.name}`}
            onClick={() => onToggle(key)}
            style={chipStyle}
            className={`flex items-center gap-2 rounded-full px-3 py-2 text-sm font-semibold transition-colors cursor-pointer focus:outline-none focus-visible:ring-2 focus-visible:ring-accent ${chipVariants[variantKey]}`}
          >
            <span
              className={`w-3 h-3 rounded-full shrink-0 ${isActive ? '' : 'border-2 bg-transparent'}`}
              style={dotStyle}
            />
            {cat.name}
          </button>
        )
      })}
    </div>
  )
}
