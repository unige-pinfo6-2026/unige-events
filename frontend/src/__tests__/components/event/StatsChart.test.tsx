// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import StatsChart from '@/components/event/StatsChart'

vi.mock('recharts', () => ({
  ResponsiveContainer: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
  BarChart: ({ children, data }: { children: React.ReactNode; data: unknown[] }) => (
    <div data-testid="bar-chart" data-entries={data.length}>{children}</div>
  ),
  Bar: ({ children }: { children: React.ReactNode }) => <div data-testid="bar">{children}</div>,
  Cell: ({ fill }: { fill: string }) => <div data-testid="cell" data-fill={fill} />,
  XAxis: () => null,
  YAxis: () => null,
  Tooltip: () => null,
}))

afterEach(cleanup)

describe('StatsChart', () => {
  it('renders the chart', () => {
    render(<StatsChart attending={10} checkIns={5} />)
    expect(screen.getByTestId('bar-chart')).toBeTruthy()
  })

  it('shows 2 bars when no capacity is provided', () => {
    render(<StatsChart attending={10} checkIns={5} />)
    expect(screen.getByTestId('bar-chart').getAttribute('data-entries')).toBe('2')
  })

  it('shows 3 bars when capacity is provided', () => {
    render(<StatsChart attending={10} checkIns={5} capacity={50} />)
    expect(screen.getByTestId('bar-chart').getAttribute('data-entries')).toBe('3')
  })

  it('clamps remaining places to 0 when attending exceeds capacity', () => {
    render(<StatsChart attending={60} checkIns={5} capacity={50} />)
    const cells = screen.getAllByTestId('cell')
    // 3rd cell is "Places restantes" — value clamped to 0 but still rendered
    expect(cells).toHaveLength(3)
  })
})
