
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import NotFoundPage from '@/pages/NotFoundPage'

vi.mock('@/components/utils/Blobs', () => ({
  Blobs: () => <div data-testid="blobs" />,
}))

afterEach(() => { cleanup() })

describe('NotFoundPage', () => {
  it('renders the 404 heading', () => {
    render(
      <MemoryRouter>
        <NotFoundPage />
      </MemoryRouter>,
    )
    expect(screen.getByText('404')).toBeTruthy()
  })

  it('renders the page introuvable message', () => {
    render(
      <MemoryRouter>
        <NotFoundPage />
      </MemoryRouter>,
    )
    expect(screen.getByText('Page introuvable')).toBeTruthy()
  })

  it('renders the back to home link', () => {
    render(
      <MemoryRouter>
        <NotFoundPage />
      </MemoryRouter>,
    )
    expect(screen.getByRole('link', { name: /Retour à l'accueil/i })).toBeTruthy()
  })
})
