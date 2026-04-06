// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { ThemeProvider, useTheme } from '@/contexts/ThemeContext'

afterEach(() => {
  localStorage.clear()
  cleanup()
})

function ThemeDisplay() {
  const { theme, toggleTheme } = useTheme()
  return (
    <div>
      <span data-testid="theme">{theme}</span>
      <button onClick={toggleTheme}>Toggle</button>
    </div>
  )
}

describe('ThemeContext', () => {
  it('defaults to dark theme', () => {
    render(<ThemeProvider><ThemeDisplay /></ThemeProvider>)
    expect(screen.getByTestId('theme').textContent).toBe('dark')
  })

  it('toggles to light then back to dark', () => {
    render(<ThemeProvider><ThemeDisplay /></ThemeProvider>)
    fireEvent.click(screen.getByText('Toggle'))
    expect(screen.getByTestId('theme').textContent).toBe('light')
    fireEvent.click(screen.getByText('Toggle'))
    expect(screen.getByTestId('theme').textContent).toBe('dark')
  })

  it('persists theme to localStorage', () => {
    render(<ThemeProvider><ThemeDisplay /></ThemeProvider>)
    fireEvent.click(screen.getByText('Toggle'))
    expect(localStorage.getItem('theme')).toBe('light')
  })

  it('reads initial theme from localStorage', () => {
    localStorage.setItem('theme', 'light')
    render(<ThemeProvider><ThemeDisplay /></ThemeProvider>)
    expect(screen.getByTestId('theme').textContent).toBe('light')
  })
})
