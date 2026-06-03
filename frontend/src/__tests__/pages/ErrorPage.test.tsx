import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ErrorPage from '@/pages/ErrorPage'

vi.mock('@/components/utils/Blobs', () => ({
  Blobs: () => <div data-testid="blobs" />,
}))

afterEach(() => { cleanup() })

describe('ErrorPage', () => {
  it('renders the default 500 heading', () => {
    render(
      <MemoryRouter>
        <ErrorPage />
      </MemoryRouter>,
    )
    expect(screen.getByText('500')).toBeTruthy()
  })

  it('renders custom status code', () => {
    render(
      <MemoryRouter>
        <ErrorPage statusCode={503} />
      </MemoryRouter>,
    )
    expect(screen.getByText('503')).toBeTruthy()
  })

  it('renders the apologies and text', () => {
    render(
      <MemoryRouter>
        <ErrorPage />
      </MemoryRouter>,
    )
    expect(screen.getByText("Ce n'est pas vous, c'est nous.")).toBeTruthy()
    expect(screen.getByText(/Une erreur inattendue est survenue de notre côté/i)).toBeTruthy()
  })


  it('renders retry button and triggers callback', () => {
    const onRetry = vi.fn()
    render(
      <MemoryRouter>
        <ErrorPage onRetry={onRetry} />
      </MemoryRouter>,
    )
    const retryBtn = screen.getByRole('button', { name: /Réessayer/i })
    expect(retryBtn).toBeTruthy()
    fireEvent.click(retryBtn)
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('renders the back to home link', () => {
    render(
      <MemoryRouter>
        <ErrorPage />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: /Retour à l'accueil/i })).toBeTruthy()
  })
})
