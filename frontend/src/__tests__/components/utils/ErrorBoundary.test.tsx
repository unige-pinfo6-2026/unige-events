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

  it('automatically calls window.location.reload when a chunk load error is caught', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const reload = vi.fn()
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload } as Location)

    const sessionStorageMock = {
      getItem: vi.fn().mockReturnValue(null),
      setItem: vi.fn(),
    }
    vi.stubGlobal('sessionStorage', sessionStorageMock)

    function ChunkBomb() {
      throw new TypeError('error loading dynamically imported module: some_chunk.js')
    }

    render(
      <MemoryRouter>
        <ErrorBoundary>
          <ChunkBomb />
        </ErrorBoundary>
      </MemoryRouter>,
    )

    expect(reload).toHaveBeenCalled()

    consoleError.mockRestore()
    vi.restoreAllMocks()
  })

  it('does not reload again when a chunk error recurs inside the 10s guard window', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const reload = vi.fn()
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload } as Location)

    // A reload was recorded ~1s ago → still inside the 10s anti-loop window,
    // so the boundary must NOT reload again (it would otherwise loop forever).
    const sessionStorageMock = {
      getItem: vi.fn().mockReturnValue(String(Date.now() - 1000)),
      setItem: vi.fn(),
    }
    vi.stubGlobal('sessionStorage', sessionStorageMock)

    function ChunkBomb(): never {
      throw new TypeError('Failed to fetch dynamically imported module: app.js')
    }

    render(
      <MemoryRouter>
        <ErrorBoundary>
          <ChunkBomb />
        </ErrorBoundary>
      </MemoryRouter>,
    )

    expect(reload).not.toHaveBeenCalled()
    expect(sessionStorageMock.setItem).not.toHaveBeenCalled()
    expect(consoleError).toHaveBeenCalledWith('Chunk load error loop prevented.')

    consoleError.mockRestore()
    vi.restoreAllMocks()
  })

  it('swallows and logs sessionStorage failures while handling a chunk error', () => {
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})
    const reload = vi.fn()
    vi.spyOn(window, 'location', 'get').mockReturnValue({ ...window.location, reload } as Location)

    // sessionStorage can throw (private mode / disabled storage): the handler
    // must catch it so a chunk error never crashes the boundary itself.
    const sessionStorageMock = {
      getItem: vi.fn(() => { throw new Error('storage disabled') }),
      setItem: vi.fn(),
    }
    vi.stubGlobal('sessionStorage', sessionStorageMock)

    function ChunkBomb(): never {
      throw new TypeError('error loading dynamically imported module: app.js')
    }

    render(
      <MemoryRouter>
        <ErrorBoundary>
          <ChunkBomb />
        </ErrorBoundary>
      </MemoryRouter>,
    )

    expect(reload).not.toHaveBeenCalled()
    expect(consoleError).toHaveBeenCalledWith('Failed to handle chunk load error:', expect.any(Error))

    consoleError.mockRestore()
    vi.restoreAllMocks()
  })
})
