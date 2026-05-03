import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import PrivacyPage from '@/pages/legal/PrivacyPage'

vi.mock('@/components/utils/Blobs', () => ({
  BlobsSubtle: () => <div data-testid="blobs-subtle" />,
}))

afterEach(() => { cleanup() })

describe('PrivacyPage', () => {
  function renderPage() {
    return render(
      <MemoryRouter>
        <PrivacyPage />
      </MemoryRouter>,
    )
  }

  it('renders the page title', () => {
    renderPage()
    expect(screen.getByText(/Politique de/)).toBeTruthy()
    expect(screen.getAllByText(/confidentialité/).length).toBeGreaterThan(0)
  })

  it('renders the academic project disclaimer', () => {
    renderPage()
    expect(screen.getByText(/projet académique/i)).toBeTruthy()
  })

  it('renders the legal framework section', () => {
    renderPage()
    expect(screen.getByText('Cadre légal')).toBeTruthy()
  })

  it('renders the data collection section', () => {
    renderPage()
    expect(screen.getByText('Données collectées')).toBeTruthy()
  })

  it('renders the cookies section', () => {
    renderPage()
    expect(screen.getByText('Cookies')).toBeTruthy()
  })

  it('renders the user rights section', () => {
    renderPage()
    expect(screen.getByText('Vos droits')).toBeTruthy()
  })

  it('renders the security section', () => {
    renderPage()
    expect(screen.getByText('Sécurité')).toBeTruthy()
  })

  it('renders the responsible entity', () => {
    renderPage()
    expect(screen.getByText('Responsable du traitement')).toBeTruthy()
    expect(screen.getByText('Université de Genève')).toBeTruthy()
  })

  it('renders the back to home link', () => {
    renderPage()
    const link = screen.getByText("Retour à l'accueil")
    expect(link).toBeTruthy()
    expect(link.getAttribute('href')).toBe('/')
  })

  it('renders the profile edit link for rights exercise', () => {
    renderPage()
    const link = screen.getByText('Mon profil')
    expect(link).toBeTruthy()
    expect(link.getAttribute('href')).toBe('/profile/me/edit')
  })
})
