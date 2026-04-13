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
 * @prop trigger  — ReactNode affiché en permanence (le déclencheur visible)
 * @prop children — contenu du panel (liens, boutons…)
 * @prop align    — alignement du panel : 'left' (défaut) ou 'right'
 */
export function Dropdown({
  trigger,
  children,
  align = 'left',
}: Readonly<{
  trigger: React.ReactNode
  children: React.ReactNode
  align?: keyof typeof aligns
}>) {
  return (
    <div className="group relative">
      <div className="flex items-center gap-1">
        {trigger}
        <ChevronDown className="size-4 text-foreground/50 transition-transform duration-200 group-hover:rotate-180" />
      </div>

      {/* Invisible bridge prevents the gap between trigger and panel from closing the hover */}
      <div className="absolute top-full left-0 h-2 w-full" />
      <div className={`invisible group-hover:visible opacity-0 group-hover:opacity-100 translate-y-1 group-hover:translate-y-0 transition-all duration-150 absolute top-[calc(100%+0.5rem)] ${aligns[align]}`}>
        <div className={"w-52 rounded-xl bg-background border border-border shadow-xl overflow-hidden z-50"}>
          {children}
        </div>
      </div>
    </div>
  )
}
