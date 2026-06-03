import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ErrorBoundary from '@/components/utils/ErrorBoundary'

vi.mock('@/components/utils/Blobs', () => ({
  Blobs: () => <div data-testid="blobs" />,
}))

vi.mock('@/components/utils/Buttons', () => ({
  ButtonPrimary: ({ children, onClick }: { children: React.ReactNode; onClick?: () => void }) => (
    <button onClick={onClick}>{children}</button>
  ),
  ButtonSecondary: ({ children }: { children: React.ReactNode }) => <button>{children}</button>,
  ButtonsWrapper: ({ children }: { children: React.ReactNode }) => <div>{children}</div>,
}))

afterEach(cleanup)

function Bomb({ shouldThrow }: { shouldThrow: boolean }) {
  if (shouldThrow) throw new Error('test crash')
  return <div>OK</div>
}

describe('ErrorBoundary', () => {
  it('renders children when there is no error', () => {
    render(
      <MemoryRouter>
        <ErrorBoundary>
          <Bomb shouldThrow={false} />
        </ErrorBoundary>
      </MemoryRouter>,
    )
    expect(screen.getByText('OK')).toBeTruthy()
  })

  it('renders the fallback UI when a child throws', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(
      <MemoryRouter>
        <ErrorBoundary>
          <Bomb shouldThrow={true} />
        </ErrorBoundary>
      </MemoryRouter>,
    )
    expect(screen.getByText(/Une erreur inattendue est survenue/)).toBeTruthy()
    consoleError.mockRestore()
  })

  it('shows the reload and home buttons in the fallback', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(
      <MemoryRouter>
        <ErrorBoundary>
          <Bomb shouldThrow={true} />
        </ErrorBoundary>
      </MemoryRouter>,
    )
    expect(screen.getByText('Réessayer')).toBeTruthy()
    expect(screen.getByText("Retour à l'accueil")).toBeTruthy()
    consoleError.mockRestore()
  })

  it('logs the error via console.error', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    render(
      <MemoryRouter>
        <ErrorBoundary>
          <Bomb shouldThrow={true} />
        </ErrorBoundary>
      </MemoryRouter>,
    )
    expect(consoleError).toHaveBeenCalled()
    consoleError.mockRestore()
  })

  it('calls window.location.reload when the reload button is clicked', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const reload = vi.fn()
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload } as Location)

    render(
      <MemoryRouter>
        <ErrorBoundary>
          <Bomb shouldThrow={true} />
        </ErrorBoundary>
      </MemoryRouter>,
    )
    fireEvent.click(screen.getByText('Réessayer'))
    expect(reload).toHaveBeenCalled()

    consoleError.mockRestore()
    vi.restoreAllMocks()
  })
})
