import { CheckCircle2, Eye, Star } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface StatsChartProps {
  views: number
  attending: number
  interested: number
}

interface FunnelStep {
  icon: LucideIcon
  label: string
  value: number
  color: string
}

export default function StatsChart({ views, attending, interested }: Readonly<StatsChartProps>) {
  const ref = Math.max(views, 1)

  const steps: FunnelStep[] = [
    { icon: Eye,          label: 'Vues totales', value: views,      color: '#3b82f6' },
    { icon: Star,         label: 'Intéressés',   value: interested, color: '#a855f7' },
    { icon: CheckCircle2, label: 'Inscrits',      value: attending,  color: '#22c55e' },
  ]

  const conversions = [
    views > 0      ? Math.round((interested / views)      * 100) : null,
    interested > 0 ? Math.round((attending  / interested) * 100) : null,
  ]

  return (
    <div className="flex flex-col gap-3">
      {steps.map((step, i) => (
        <div key={step.label}>
          {i > 0 && conversions[i - 1] !== null && (
            <div className="flex items-center gap-2 mb-3 pl-1">
              <div className="w-px h-4 bg-foreground/10 ml-[7px]" />
              <span className="text-[11px] text-foreground/30">
                {conversions[i - 1]}% {i === 1 ? 'des visiteurs' : 'des intéressés'}
              </span>
            </div>
          )}
          <div className="flex items-center gap-3">
            <step.icon className="w-4 h-4 shrink-0" style={{ color: step.color }} />
            <div className="flex-1 flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs text-foreground/50">{step.label}</span>
                <span className="text-sm font-bold text-foreground tabular-nums">
                  {step.value.toLocaleString('fr-CH')}
                </span>
              </div>
              <div className="h-2 rounded-full bg-foreground/5 overflow-hidden">
                <div
                  className="h-full rounded-full transition-all duration-700"
                  style={{
                    width: `${Math.min(100, Math.round((step.value / ref) * 100))}%`,
                    backgroundColor: step.color,
                  }}
                />
              </div>
            </div>
          </div>
        </div>
      ))}
    </div>
  )
}
