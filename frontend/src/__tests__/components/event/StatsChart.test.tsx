// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import StatsChart from '@/components/event/StatsChart'

afterEach(cleanup)

describe('StatsChart', () => {
  it('renders all three funnel steps', () => {
    render(<StatsChart views={100} attending={30} interested={60} />)
    expect(screen.getByText('Vues totales')).toBeTruthy()
    expect(screen.getByText('Intéressés')).toBeTruthy()
    expect(screen.getByText('Inscrits')).toBeTruthy()
  })

  it('displays the correct values', () => {
    render(<StatsChart views={100} attending={30} interested={60} />)
    expect(screen.getByText('100')).toBeTruthy()
    expect(screen.getByText('60')).toBeTruthy()
    expect(screen.getByText('30')).toBeTruthy()
  })

  it('shows conversion from views to interested', () => {
    render(<StatsChart views={100} attending={30} interested={60} />)
    expect(screen.getByText(/60%.*visiteurs/)).toBeTruthy()
  })

  it('shows conversion from interested to attending', () => {
    render(<StatsChart views={100} attending={30} interested={60} />)
    expect(screen.getByText(/50%.*intéressés/)).toBeTruthy()
  })

  it('hides conversion when views is 0', () => {
    render(<StatsChart views={0} attending={0} interested={0} />)
    expect(screen.queryByText(/visiteurs/)).toBeNull()
    expect(screen.queryByText(/des intéressés/)).toBeNull()
  })

  it('hides interested-to-attending conversion when interested is 0', () => {
    render(<StatsChart views={10} attending={0} interested={0} />)
    expect(screen.queryByText(/des intéressés/)).toBeNull()
  })
})
