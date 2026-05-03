import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import TermsPage from '@/pages/legal/TermsPage'

vi.mock('@/components/utils/Blobs', () => ({
  BlobsSubtle: () => <div data-testid="blobs-subtle" />,
}))

afterEach(() => { cleanup() })

describe('TermsPage', () => {
  function renderPage() {
    return render(
      <MemoryRouter>
        <TermsPage />
      </MemoryRouter>,
    )
  }

  it('renders the page title', () => {
    renderPage()
    expect(screen.getByText("Conditions générales d'utilisation")).toBeTruthy()
  })

  it('renders the academic project disclaimer', () => {
    renderPage()
    expect(screen.getByText(/cadre du cours PINFO/i)).toBeTruthy()
  })

  it('renders the object section', () => {
    renderPage()
    expect(screen.getByText('Objet')).toBeTruthy()
  })

  it('renders the user content section', () => {
    renderPage()
    expect(screen.getByText('Contenu utilisateur')).toBeTruthy()
  })

  it('renders the moderation section', () => {
    renderPage()
    expect(screen.getByText('Modération')).toBeTruthy()
  })

  it('renders the liability limitation section', () => {
    renderPage()
    expect(screen.getByText('Limitation de responsabilité')).toBeTruthy()
  })

  it('renders the applicable law section', () => {
    renderPage()
    expect(screen.getByText('Droit applicable et for juridique')).toBeTruthy()
  })

  it('renders the privacy policy link', () => {
    renderPage()
    const link = screen.getByText('Politique de confidentialité')
    expect(link).toBeTruthy()
    expect(link.getAttribute('href')).toBe('/legal/privacy')
  })

  it('renders the back to home link', () => {
    renderPage()
    const link = screen.getByText("Retour à l'accueil")
    expect(link).toBeTruthy()
    expect(link.getAttribute('href')).toBe('/')
  })

  it('renders the GitHub link', () => {
    renderPage()
    const link = screen.getByText('GitHub')
    expect(link).toBeTruthy()
    expect(link.getAttribute('href')).toBe('https://github.com/unige-pinfo6-2026/unige-events')
    expect(link.getAttribute('target')).toBe('_blank')
    expect(link.getAttribute('rel')).toBe('noopener noreferrer')
  })
})
