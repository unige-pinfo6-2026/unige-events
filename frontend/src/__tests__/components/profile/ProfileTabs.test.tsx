// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { CalendarDays, LayoutGrid, Ticket } from 'lucide-react'
import ProfileTabs, { type ProfileTab } from '@/components/profile/ProfileTabs'

afterEach(() => { cleanup() })

const tabs: ProfileTab[] = [
  { key: 'a', label: 'Onglet A', icon: CalendarDays, content: <div>Contenu A</div> },
  { key: 'b', label: 'Onglet B', icon: Ticket, content: <div>Contenu B</div> },
  { key: 'c', label: 'Onglet C', icon: LayoutGrid, content: <div>Contenu C</div> },
]

describe('ProfileTabs', () => {
  it('renders the first tab active by default and its content', () => {
    render(<ProfileTabs tabs={tabs} />)

    expect(screen.getByRole('tab', { name: 'Onglet A' }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByText('Contenu A')).toBeTruthy()
    expect(screen.queryByText('Contenu B')).toBeNull()
  })

  it('switches the active tab and content on click', () => {
    render(<ProfileTabs tabs={tabs} />)

    fireEvent.click(screen.getByRole('tab', { name: 'Onglet B' }))

    expect(screen.getByRole('tab', { name: 'Onglet B' }).getAttribute('aria-selected')).toBe('true')
    expect(screen.getByText('Contenu B')).toBeTruthy()
    expect(screen.queryByText('Contenu A')).toBeNull()
  })

  it('renders one tab per entry', () => {
    render(<ProfileTabs tabs={tabs} />)

    expect(screen.getAllByRole('tab')).toHaveLength(3)
  })

  it('links the active panel to its tab via aria', () => {
    render(<ProfileTabs tabs={tabs} />)

    const panel = screen.getByRole('tabpanel')
    expect(panel.getAttribute('aria-labelledby')).toBe('profile-tab-a')
  })

  it('renders nothing when given no tabs', () => {
    const { container } = render(<ProfileTabs tabs={[]} />)

    expect(container.firstChild).toBeNull()
  })
})
