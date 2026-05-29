import { useEffect, useRef } from 'react'
import { useLocation } from 'react-router-dom'
import { ChevronDown } from 'lucide-react'

const aligns = {
  left: 'left-0',
  right: 'right-0',
}

/**
 * Hover dropdown — pure CSS, aucun state.
 *
 * Variantes via const map typée (`aligns`) — suivre ce pattern pour toute nouvelle variante.
 * Le `ChevronDown` est inclus automatiquement et pivote au hover : ne pas l'ajouter dans `trigger`.
 *
 * @prop trigger    — ReactNode affiché en permanence (le déclencheur visible)
 * @prop children   — contenu du panel (liens, boutons…)
 * @prop align      — alignement du panel : 'left' (défaut) ou 'right'
 * @prop forceOpen  — quand `true`, force la visibilité du panel indépendamment du
 *                   hover/focus (utile pour empêcher la fermeture pendant une
 *                   mutation qui rétrécit le contenu et fait sortir la souris —
 *                   cf. `RequestsInboxDropdown`).
 */
export function Dropdown({
  trigger,
  children,
  align = 'left',
  showChevron = true,
  forceOpen = false,
}: Readonly<{
  trigger: React.ReactNode
  children: React.ReactNode
  align?: keyof typeof aligns
  showChevron?: boolean
  forceOpen?: boolean
}>) {
  const ref = useRef<HTMLDivElement>(null)
  const { pathname } = useLocation()

  useEffect(() => {
    const el = ref.current?.querySelector<HTMLElement>(':focus')
    el?.blur()
  }, [pathname])

  return (
    <div ref={ref} className="group relative">
      <div className="flex items-center gap-1">
        {trigger}
        {showChevron && <ChevronDown className="size-4 text-foreground/50 transition-transform duration-200 group-hover:rotate-180" />}
      </div>

      {/* Invisible bridge prevents the gap between trigger and panel from closing the hover */}
      <div className="absolute top-full left-0 h-2 w-full" />
      <div
        className={[
          'transition-all duration-150 absolute top-[calc(100%+0.5rem)] z-50',
          aligns[align],
          // Base : invisible ; s'ouvre au hover et au focus-within.
          'invisible group-hover:visible group-focus-within:visible',
          'opacity-0 group-hover:opacity-100 group-focus-within:opacity-100',
          'translate-y-1 group-hover:translate-y-0 group-focus-within:translate-y-0',
          // forceOpen : écrase les classes de visibilité avec !important pour garder
          // le panel visible même quand la souris sort (ex : après suppression d'une
          // ligne qui rétrécit le panel et déplace le curseur hors de la zone hover).
          forceOpen ? '!visible !opacity-100 !translate-y-0' : '',
        ].join(' ')}
      >
        <div className="w-max rounded-xl bg-background border border-border shadow-xl overflow-hidden z-50">
          {children}
        </div>
      </div>
    </div>
  )
}
