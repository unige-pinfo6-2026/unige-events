// @vitest-environment jsdom

import React from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import StatsChart from '@/components/event/StatsChart'

type TooltipProps = { formatter?: (value: unknown) => unknown }
let capturedFormatter: TooltipProps['formatter'] | undefined

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  BarChart: ({ data, children }: { data: { name: string }[]; children: React.ReactNode }) => (
    <div>
      {data.map((d) => <span key={d.name}>{d.name}</span>)}
      {children}
    </div>
  ),
  Bar: () => null,
  Cell: () => null,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: ({ formatter }: TooltipProps) => {
    capturedFormatter = formatter
    return null
  },
}))

afterEach(cleanup)

describe('StatsChart', () => {
  it('renders all three axis labels', () => {
    render(<StatsChart views={100} attending={30} interested={60} />)
    expect(screen.getByText('Vues totales')).toBeTruthy()
    expect(screen.getByText('Intéressés')).toBeTruthy()
    expect(screen.getByText('Inscrits')).toBeTruthy()
  })

  it('renders without crashing when all values are zero', () => {
    render(<StatsChart views={0} attending={0} interested={0} />)
    expect(screen.getByText('Vues totales')).toBeTruthy()
  })

  it('tooltip formatter formats numbers with fr-CH locale', () => {
    render(<StatsChart views={1234} attending={30} interested={60} />)
    expect(capturedFormatter).toBeDefined()
    expect(capturedFormatter!(1234)).toEqual([(1234).toLocaleString('fr-CH')])
  })

  it('tooltip formatter passes non-number values through unchanged', () => {
    render(<StatsChart views={0} attending={0} interested={0} />)
    expect(capturedFormatter!('some string')).toEqual(['some string'])
  })
})
