import { BarChart2, CheckCircle2, Eye, Star } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface EventStatsPanelProps {
  viewCount: number | null | undefined
  interestedCount: number | null | undefined
  attendingCount: number
}

const tileGradients = {
  blue:   'from-blue-500/20 to-blue-500/5',
  green:  'from-emerald-500/20 to-emerald-500/5',
  purple: 'from-purple-500/20 to-purple-500/5',
} as const

const tileIconColors = {
  blue:   'text-blue-400',
  green:  'text-emerald-400',
  purple: 'text-purple-400',
} as const

type TileColor = keyof typeof tileGradients

interface TileProps {
  icon: LucideIcon
  label: string
  value: number | null | undefined
  color: TileColor
}

function StatTile({ icon: Icon, label, value, color }: Readonly<TileProps>) {
  const display = typeof value === 'number' ? value.toLocaleString('fr-CH') : '—'
  return (
    <div className="rounded-2xl border border-border bg-foreground/[0.02] p-3 flex flex-col items-center gap-1.5">
      <div className={`w-8 h-8 rounded-lg bg-linear-to-br ${tileGradients[color]} flex items-center justify-center shrink-0`}>
        <Icon className={`w-4 h-4 ${tileIconColors[color]}`} />
      </div>
      <span className="text-lg font-bold text-foreground leading-none">{display}</span>
      <span className="text-[10px] text-foreground/40 leading-none">{label}</span>
    </div>
  )
}

export default function EventStatsPanel({
  viewCount,
  interestedCount,
  attendingCount,
}: Readonly<EventStatsPanelProps>) {
  return (
    <div className="bg-linear-to-br from-background/80 to-background/40 backdrop-blur-xl rounded-3xl p-5 border border-border">
      <h3 className="flex items-center gap-2 text-xs font-bold uppercase tracking-widest text-foreground/40 mb-3">
        <BarChart2 className="w-4 h-4 shrink-0" />
        Statistiques de participation
      </h3>
      <div className="grid grid-cols-3 gap-2">
        <StatTile icon={Eye}          label="Vues"       value={viewCount}       color="blue"   />
        <StatTile icon={CheckCircle2} label="Inscrits"   value={attendingCount}  color="green"  />
        <StatTile icon={Star}         label="Intéressés" value={interestedCount} color="purple" />
      </div>
    </div>
  )
}
