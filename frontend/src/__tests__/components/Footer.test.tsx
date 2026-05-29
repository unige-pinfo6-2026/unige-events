import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import Footer from '@/components/Footer'

afterEach(() => { cleanup() })

function renderFooter() {
  return render(
    <MemoryRouter>
      <Footer />
    </MemoryRouter>,
  )
}

describe('Footer', () => {
  it('renders the privacy and terms links pointing to /legal/*', () => {
    renderFooter()

    const privacy = screen.getByText('Politique de confidentialité')
    const terms = screen.getByText('Conditions générales')

    expect(privacy.getAttribute('href')).toBe('/legal/privacy')
    expect(terms.getAttribute('href')).toBe('/legal/terms')
  })

  it('scrolls to top when clicking the privacy link', () => {
    const mockScrollTo = vi.fn()
    globalThis.scrollTo = mockScrollTo

    renderFooter()
    fireEvent.click(screen.getByText('Politique de confidentialité'))

    expect(mockScrollTo).toHaveBeenCalledWith({ top: 0 })
  })

  it('scrolls to top when clicking the terms link', () => {
    const mockScrollTo = vi.fn()
    globalThis.scrollTo = mockScrollTo

    renderFooter()
    fireEvent.click(screen.getByText('Conditions générales'))

    expect(mockScrollTo).toHaveBeenCalledWith({ top: 0 })
  })

  it('renders the resource links pointing to /support and /rules', () => {
    renderFooter()

    const support = screen.getByText("Centre d'aide")
    const rules = screen.getByText('Règles communauté')

    expect(support.getAttribute('href')).toBe('/support')
    expect(rules.getAttribute('href')).toBe('/rules')
  })

  it('renders the current year in the copyright', () => {
    renderFooter()
    const year = new Date().getFullYear()
    expect(screen.getByText(new RegExp(`© ${year} UNIGE Events`))).toBeTruthy()
  })
})
