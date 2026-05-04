import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import Layout from '@/components/Layout'

vi.mock('@/components/Header', () => ({
  default: () => <header>Header</header>,
}))

vi.mock('@/components/Footer', () => ({
  default: () => <footer>Footer</footer>,
}))

vi.mock('@/components/utils/Toast', () => ({
  default: () => <div>Toasts</div>,
}))

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom')
  return {
    ...actual,
    Outlet: () => <main>Page content</main>,
  }
})

const previewMessage =
  "Cette version est une prévisualisation. Les données et fonctionnalités présentées sont susceptibles d'évoluer ou d'être modifiées."

afterEach(() => {
  cleanup()
  vi.unstubAllEnvs()
})

describe('Layout', () => {
  it('shows the preview info banner in preview mode', () => {
    vi.stubEnv('MODE', 'preview')

    render(<Layout />)

    expect(screen.getByText(previewMessage)).toBeTruthy()
    expect(screen.getByText('Header')).toBeTruthy()
    expect(screen.getByText('Page content')).toBeTruthy()
    expect(screen.getByText('Footer')).toBeTruthy()
  })

  it('does not show the preview info banner outside preview mode', () => {
    vi.stubEnv('MODE', 'test')

    render(<Layout />)

    expect(screen.queryByText(previewMessage)).toBeNull()
  })
})
