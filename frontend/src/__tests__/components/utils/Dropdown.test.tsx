import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes, Link } from 'react-router-dom'
import { Dropdown } from '@/components/utils/Dropdown'

afterEach(() => {
  cleanup()
})

describe('Dropdown', () => {
  it('renders trigger and children correctly', () => {
    render(
      <MemoryRouter>
        <Dropdown trigger={<button>Open me</button>}>
          <div>Dropdown Content</div>
        </Dropdown>
      </MemoryRouter>
    )

    expect(screen.getByText('Open me')).toBeTruthy()
    expect(screen.getByText('Dropdown Content')).toBeTruthy()
  })

  it('toggles visibility classes on trigger click', async () => {
    render(
      <MemoryRouter>
        <Dropdown trigger={<button>Open me</button>}>
          <div>Dropdown Content</div>
        </Dropdown>
      </MemoryRouter>
    )

    // Initially, the panel element does not have the forceOpen style override.
    const panelWrapper = screen.getByText('Dropdown Content').parentElement?.parentElement
    expect(panelWrapper?.className).not.toContain('!visible')

    // Click trigger to open
    const trigger = screen.getByText('Open me')
    fireEvent.click(trigger)
    expect(panelWrapper?.className).toContain('!visible')

    // Click trigger again to close
    fireEvent.click(trigger)
    expect(panelWrapper?.className).not.toContain('!visible')
  })

  it('closes dropdown on click outside', () => {
    render(
      <MemoryRouter>
        <div>
          <button data-testid="outside">Outside</button>
          <Dropdown trigger={<button>Open me</button>}>
            <div>Dropdown Content</div>
          </Dropdown>
        </div>
      </MemoryRouter>
    )

    const panelWrapper = screen.getByText('Dropdown Content').parentElement?.parentElement
    const trigger = screen.getByText('Open me')

    // Click trigger to open
    fireEvent.click(trigger)
    expect(panelWrapper?.className).toContain('!visible')

    // Click outside
    fireEvent.click(screen.getByTestId('outside'))
    expect(panelWrapper?.className).not.toContain('!visible')
  })

  it('closes dropdown on Escape key', () => {
    render(
      <MemoryRouter>
        <Dropdown trigger={<button>Open me</button>}>
          <div>Dropdown Content</div>
        </Dropdown>
      </MemoryRouter>
    )

    const panelWrapper = screen.getByText('Dropdown Content').parentElement?.parentElement
    const trigger = screen.getByText('Open me')

    // Click trigger to open
    fireEvent.click(trigger)
    expect(panelWrapper?.className).toContain('!visible')

    // Press Escape
    fireEvent.keyDown(document, { key: 'Escape' })
    expect(panelWrapper?.className).not.toContain('!visible')
  })

  it('closes dropdown when navigation happens (pathname changes)', () => {
    function TestComponent() {
      return (
        <Dropdown trigger={<button>Open me</button>}>
          <Link to="/other">Go to other</Link>
        </Dropdown>
      )
    }

    render(
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<TestComponent />} />
          <Route path="/other" element={<div>Other Page</div>} />
        </Routes>
      </MemoryRouter>
    )

    const trigger = screen.getByText('Open me')

    // Click trigger to open
    fireEvent.click(trigger)

    // Click the navigation link
    const link = screen.getByText('Go to other')
    fireEvent.click(link)

    expect(screen.getByText('Other Page')).toBeTruthy()
  })

  it('forces open status when forceOpen is true', () => {
    render(
      <MemoryRouter>
        <Dropdown trigger={<button>Open me</button>} forceOpen={true}>
          <div>Dropdown Content</div>
        </Dropdown>
      </MemoryRouter>
    )

    const panelWrapper = screen.getByText('Dropdown Content').parentElement?.parentElement
    expect(panelWrapper?.className).toContain('!visible')
  })

  it('toggles via the keyboard (Enter / Space) on the trigger for accessibility', () => {
    render(
      <MemoryRouter>
        <Dropdown trigger={<button>Open me</button>}>
          <div>Dropdown Content</div>
        </Dropdown>
      </MemoryRouter>
    )

    const panelWrapper = screen.getByText('Dropdown Content').parentElement?.parentElement
    // The keyboard-operable trigger is the role="button" wrapper around the slot.
    const triggerWrapper = screen.getByText('Open me').parentElement as HTMLElement

    // Enter opens.
    fireEvent.keyDown(triggerWrapper, { key: 'Enter' })
    expect(panelWrapper?.className).toContain('!visible')

    // Space toggles back closed (and preventDefault stops the page from scrolling).
    fireEvent.keyDown(triggerWrapper, { key: ' ' })
    expect(panelWrapper?.className).not.toContain('!visible')

    // Any other key is a no-op.
    fireEvent.keyDown(triggerWrapper, { key: 'a' })
    expect(panelWrapper?.className).not.toContain('!visible')
  })

  it('uses triggerLabel as the accessible name of the trigger control', () => {
    render(
      <MemoryRouter>
        <Dropdown trigger={<span>bell</span>} triggerLabel="Notifications">
          <div>Dropdown Content</div>
        </Dropdown>
      </MemoryRouter>
    )
    // The role="button" wrapper carries the label so wrapped presentational
    // triggers (e.g. the notification bell) still expose an accessible name.
    expect(screen.getByRole('button', { name: 'Notifications' })).toBeTruthy()
  })

  it('exposes aria-haspopup and reflects the open state via aria-expanded', () => {
    render(
      <MemoryRouter>
        <Dropdown trigger={<span>menu</span>} triggerLabel="Menu">
          <div>Dropdown Content</div>
        </Dropdown>
      </MemoryRouter>
    )
    const trigger = screen.getByRole('button', { name: 'Menu' })
    expect(trigger.getAttribute('aria-haspopup')).toBe('true')
    expect(trigger.getAttribute('aria-expanded')).toBe('false')

    fireEvent.click(trigger)
    expect(trigger.getAttribute('aria-expanded')).toBe('true')
  })
})
