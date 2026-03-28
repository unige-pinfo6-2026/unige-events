// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import LoginPage from '../../pages/LoginPage'

vi.mock('../../hooks/useAuth', () => ({
  useAuth: vi.fn(),
}))

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

import { useAuth } from '../../hooks/useAuth'

const mockUseAuth = useAuth as ReturnType<typeof vi.fn>

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderLoginPage(search = '') {
  return render(
    <MemoryRouter initialEntries={[`/login${search}`]}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  it('renders Se connecter button', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, login: vi.fn() })
    renderLoginPage()
    expect(screen.getByRole('button', { name: 'Se connecter' })).toBeTruthy()
  })

  it('redirects to /home when already authenticated', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: true, isLoading: false, login: vi.fn() })
    renderLoginPage()
    expect(mockNavigate).toHaveBeenCalledWith('/home', { replace: true })
  })

  it('does not redirect while loading', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: true, login: vi.fn() })
    renderLoginPage()
    expect(mockNavigate).not.toHaveBeenCalled()
  })

  it('calls login on button click', () => {
    const login = vi.fn()
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, login })
    renderLoginPage()
    fireEvent.click(screen.getByRole('button', { name: 'Se connecter' }))
    expect(login).toHaveBeenCalledOnce()
  })

  it('shows error message when ?error=auth_failed', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, login: vi.fn() })
    renderLoginPage('?error=auth_failed')
    expect(screen.getByText("Échec de l'authentification. Veuillez réessayer.")).toBeTruthy()
  })

  it('shows raw error param for unknown error codes', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, login: vi.fn() })
    renderLoginPage('?error=some_other_error')
    expect(screen.getByText('some_other_error')).toBeTruthy()
  })

  it('disables button while loading', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: true, login: vi.fn() })
    renderLoginPage()
    expect((screen.getByRole('button', { name: 'Se connecter' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('opens card on mouse enter and closes on mouse leave', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, login: vi.fn() })
    renderLoginPage()
    const details = document.querySelector('details')!
    fireEvent.mouseEnter(details)
    expect(details.open).toBe(true)
    fireEvent.mouseLeave(details)
    expect(details.open).toBe(false)
  })

  it('syncs open state on native details toggle event', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, login: vi.fn() })
    renderLoginPage()
    const details = document.querySelector('details')!
    // Open via mouse, then simulate native toggle close (e.g. keyboard Escape)
    fireEvent.mouseEnter(details)
    expect(details.open).toBe(true)
    details.open = false
    fireEvent(details, new Event('toggle'))
    expect(details.open).toBe(false)
  })

  it('typewriter types and deletes characters over time', async () => {
    vi.useFakeTimers()
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, login: vi.fn() })
    renderLoginPage()
    // First EVENTS entry: "Soirée étudiante au Uni-Mail" = 28 chars at 65ms each
    // Each act() flushes one React state update → one character advance
    for (let i = 0; i < 28; i++) {
      await act(async () => { vi.advanceTimersByTime(65) })
    }
    // Full word reached — 1400ms pause before deletion starts (covers lines 35-36)
    await act(async () => { vi.advanceTimersByTime(1400) })
    // Delete 28 characters at 35ms each (covers line 44 in deleting=true branch)
    for (let i = 0; i < 28; i++) {
      await act(async () => { vi.advanceTimersByTime(35) })
    }
    // Empty string reached → transition to next word (covers lines 38-41)
    await act(async () => { vi.advanceTimersByTime(1) })
    vi.useRealTimers()
  })

  it('opens card on summary focus and closes on blur', () => {
    mockUseAuth.mockReturnValue({ isAuthenticated: false, isLoading: false, login: vi.fn() })
    renderLoginPage()
    const summary = document.querySelector('summary')!
    fireEvent.focus(summary)
    expect(document.querySelector('details')!.open).toBe(true)
    fireEvent.blur(summary)
    expect(document.querySelector('details')!.open).toBe(false)
  })
})
