import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import RulesPage from '@/pages/rules/RulesPage'

vi.mock('@/components/utils/Blobs', () => ({
  BlobsSubtle: () => <div data-testid="blobs-subtle" />,
}))

afterEach(() => { cleanup() })

describe('RulesPage', () => {
  function renderPage() {
    return render(
      <MemoryRouter>
        <RulesPage />
      </MemoryRouter>,
    )
  }

  it('renders the page title', () => {
    renderPage()
    expect(screen.getByText(/Règles de la/)).toBeTruthy()
    expect(screen.getAllByText(/communauté/).length).toBeGreaterThan(0)
  })

  it('renders the pledge section', () => {
    renderPage()
    expect(screen.getByText('Notre engagement')).toBeTruthy()
  })

  it('renders the encouraged behaviour section', () => {
    renderPage()
    expect(screen.getByText('Comportements encouragés')).toBeTruthy()
  })

  it('renders the forbidden behaviour section', () => {
    renderPage()
    expect(screen.getByText('Comportements interdits')).toBeTruthy()
  })

  it('renders the event content section', () => {
    renderPage()
    expect(screen.getByText('Contenu des événements')).toBeTruthy()
  })

  it('renders the reporting section', () => {
    renderPage()
    expect(screen.getByText('Signaler un contenu')).toBeTruthy()
  })

  it('renders the moderation and sanctions section', () => {
    renderPage()
    expect(screen.getByText('Modération et sanctions')).toBeTruthy()
  })

  it('renders the enforcement section', () => {
    renderPage()
    expect(screen.getByText('Application')).toBeTruthy()
  })

  it('renders the terms of use link', () => {
    renderPage()
    const link = screen.getByText("Conditions générales d'utilisation")
    expect(link.getAttribute('href')).toBe('/legal/terms')
  })

  it('renders the support centre link', () => {
    renderPage()
    const link = screen.getByText("Centre d'aide")
    expect(link.getAttribute('href')).toBe('/support')
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
