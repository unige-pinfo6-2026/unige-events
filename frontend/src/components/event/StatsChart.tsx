import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

interface StatsChartProps {
  attending: number
  interested: number
  capacity?: number
}

const BAR_COLORS = {
  attending:  '#22c55e',
  interested: '#a855f7',
  remaining:  '#6b7280',
} as const

export default function StatsChart({ attending, interested, capacity }: Readonly<StatsChartProps>) {
  const remaining = capacity != null ? Math.max(0, capacity - attending) : null

  const data = [
    { name: 'Inscrits',    value: attending,  color: BAR_COLORS.attending  },
    { name: 'Intéressés',  value: interested, color: BAR_COLORS.interested },
    ...(remaining !== null
      ? [{ name: 'Places restantes', value: remaining, color: BAR_COLORS.remaining }]
      : []),
  ]

  return (
    <ResponsiveContainer width="100%" height={200}>
      <BarChart data={data} margin={{ top: 8, right: 16, left: -16, bottom: 4 }}>
        <XAxis
          dataKey="name"
          tick={{ fontSize: 12, fill: 'currentColor', opacity: 0.5 }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis
          tick={{ fontSize: 11, fill: 'currentColor', opacity: 0.4 }}
          axisLine={false}
          tickLine={false}
          allowDecimals={false}
        />
        <Tooltip
          contentStyle={{
            backgroundColor: 'var(--color-background)',
            border: '1px solid var(--color-border)',
            borderRadius: '12px',
            fontSize: '13px',
          }}
          cursor={{ fill: 'currentColor', opacity: 0.05 }}
          formatter={(value: number, _key: string, props: { payload?: { name: string } }) => [
            value.toLocaleString('fr-CH'),
            props.payload?.name ?? _key,
          ]}
        />
        <Bar dataKey="value" radius={[6, 6, 0, 0]} maxBarSize={80}>
          {data.map((entry, i) => (
            <Cell key={i} fill={entry.color} />
          ))}
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  )
}
