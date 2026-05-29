import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import SupportPage from '@/pages/support/SupportPage'

vi.mock('@/components/utils/Blobs', () => ({
  BlobsSubtle: () => <div data-testid="blobs-subtle" />,
}))

afterEach(() => { cleanup() })

describe('SupportPage', () => {
  function renderPage() {
    return render(
      <MemoryRouter>
        <SupportPage />
      </MemoryRouter>,
    )
  }

  it('renders the page title', () => {
    renderPage()
    expect(screen.getByText(/Centre/)).toBeTruthy()
    expect(screen.getAllByText(/d'aide/).length).toBeGreaterThan(0)
  })

  it('renders the getting started section', () => {
    renderPage()
    expect(screen.getByText('Premiers pas')).toBeTruthy()
  })

  it('renders the discover events section', () => {
    renderPage()
    expect(screen.getByText('Découvrir des événements')).toBeTruthy()
  })

  it('renders the create and manage section', () => {
    renderPage()
    expect(screen.getByText('Créer et gérer un événement')).toBeTruthy()
  })

  it('renders the co-organisation section', () => {
    renderPage()
    expect(screen.getByText('Co-organisation')).toBeTruthy()
  })

  it('renders the frequently asked questions section', () => {
    renderPage()
    expect(screen.getByText('Questions fréquentes')).toBeTruthy()
  })

  it('renders the profile edit link', () => {
    renderPage()
    const link = screen.getByText('Modifier mon profil')
    expect(link.getAttribute('href')).toBe('/profile/me/edit')
  })

  it('renders the privacy policy link', () => {
    renderPage()
    const link = screen.getByText('Politique de confidentialité')
    expect(link.getAttribute('href')).toBe('/legal/privacy')
  })

  it('renders the community rules link', () => {
    renderPage()
    const link = screen.getByText('Règles de la communauté')
    expect(link.getAttribute('href')).toBe('/rules')
  })

  it('renders the contact email', () => {
    renderPage()
    const link = screen.getByText('contact@events.unige.ch')
    expect(link.getAttribute('href')).toBe('mailto:contact@events.unige.ch')
  })

  it('renders the back to home link', () => {
    renderPage()
    const link = screen.getByText("Retour à l'accueil")
    expect(link.getAttribute('href')).toBe('/')
  })

  it('scrolls to top when clicking the back to home link', () => {
    const mockScrollTo = vi.fn()
    globalThis.scrollTo = mockScrollTo

    renderPage()
    fireEvent.click(screen.getByText("Retour à l'accueil"))

    expect(mockScrollTo).toHaveBeenCalledWith({ top: 0 })
  })
})
