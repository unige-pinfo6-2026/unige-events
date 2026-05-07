import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import ForbiddenPage from '@/pages/ForbiddenPage'

afterEach(cleanup)

function renderPage() {
  return render(
    <MemoryRouter>
      <ForbiddenPage />
    </MemoryRouter>,
  )
}

describe('ForbiddenPage', () => {
  it('renders the 403 heading', () => {
    renderPage()
    expect(screen.getByText('403')).toBeTruthy()
  })

  it('renders the "Accès refusé" message', () => {
    renderPage()
    expect(screen.getByText('Accès refusé')).toBeTruthy()
  })

  it('renders a link back to home', () => {
    renderPage()
    const link = screen.getByRole('link', { name: /Retour à l'accueil/i })
    expect(link).toBeTruthy()
    expect((link as HTMLAnchorElement).getAttribute('href')).toBe('/')
  })

  it('renders the explanatory message', () => {
    renderPage()
    expect(screen.getByText(/Vous n'avez pas les droits nécessaires/)).toBeTruthy()
  })
})
