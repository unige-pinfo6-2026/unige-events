import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

interface StatsChartProps {
  views: number
  attending: number
  interested: number
}

export default function StatsChart({ views, attending, interested }: Readonly<StatsChartProps>) {
  const data = [
    { name: 'Vues totales', value: views,      color: '#3b82f6' },
    { name: 'Intéressés',   value: interested, color: '#a855f7' },
    { name: 'Inscrits',     value: attending,  color: '#22c55e' },
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
            color: 'var(--color-foreground)',
          }}
          itemStyle={{ color: 'var(--color-foreground)' }}
          labelStyle={{ color: 'var(--color-foreground)', opacity: 0.5, marginBottom: 4 }}
          cursor={{ fill: 'currentColor', opacity: 0.05 }}
          formatter={(value) => [
            typeof value === 'number' ? value.toLocaleString('fr-CH') : value,
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
