
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import type { AppState } from '@auth0/auth0-react'
import type { ReactNode } from 'react'
import AuthProvider from '@/components/auth/AuthProvider'

type OnRedirectCallback = (appState?: AppState) => void

// Capture object so the test can read the props Auth0Provider was given.
const captured: { onRedirectCallback?: OnRedirectCallback } = {}

vi.mock('@auth0/auth0-react', () => ({
  Auth0Provider: (props: Readonly<{ children: ReactNode; onRedirectCallback?: OnRedirectCallback }>) => {
    captured.onRedirectCallback = props.onRedirectCallback
    return <>{props.children}</>
  },
}))

const navigate = vi.fn()

vi.mock('react-router-dom', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-router-dom')>()
  return {
    ...actual,
    useNavigate: () => navigate,
  }
})

beforeEach(() => {
  captured.onRedirectCallback = undefined
})

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

function renderProvider() {
  return render(
    <MemoryRouter>
      <AuthProvider>
        <div>protected children</div>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('AuthProvider', () => {
  it('renders its children', () => {
    renderProvider()
    expect(screen.getByText('protected children')).toBeTruthy()
  })

  it('passes an onRedirectCallback to Auth0Provider', () => {
    renderProvider()
    expect(typeof captured.onRedirectCallback).toBe('function')
  })

  it('navigates to a relative returnTo path with replace: true', () => {
    renderProvider()
    captured.onRedirectCallback?.({ returnTo: '/dashboard' })
    expect(navigate).toHaveBeenCalledWith('/dashboard', { replace: true })
  })

  it('navigates to / when returnTo is a protocol-relative (//) URL', () => {
    renderProvider()
    captured.onRedirectCallback?.({ returnTo: '//evil.com' })
    expect(navigate).toHaveBeenCalledWith('/', { replace: true })
  })

  it('navigates to / when returnTo is an absolute external URL', () => {
    renderProvider()
    captured.onRedirectCallback?.({ returnTo: 'https://evil.com' })
    expect(navigate).toHaveBeenCalledWith('/', { replace: true })
  })

  it('navigates to / when no appState is provided', () => {
    renderProvider()
    captured.onRedirectCallback?.(undefined)
    expect(navigate).toHaveBeenCalledWith('/', { replace: true })
  })

  it('navigates to / when appState has no returnTo', () => {
    renderProvider()
    captured.onRedirectCallback?.({})
    expect(navigate).toHaveBeenCalledWith('/', { replace: true })
  })
})
