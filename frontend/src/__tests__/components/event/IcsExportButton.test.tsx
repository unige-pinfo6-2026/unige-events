// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, cleanup, fireEvent } from '@testing-library/react'
import IcsExportButton from '@/components/event/IcsExportButton'
import type { Event } from '@/types/event'

vi.mock('@/utils/icsGenerator', () => ({
  generateIcs: vi.fn(() => 'BEGIN:VCALENDAR\r\nEND:VCALENDAR\r\n'),
  buildGoogleCalendarUrl: vi.fn(
    () => 'https://calendar.google.com/calendar/render?action=TEMPLATE&text=Test+Event',
  ),
}))

const mockEvent: Event = {
  id: 42,
  title: 'Test Event',
  location: 'Room 101',
  startDate: '2025-06-15T10:00:00',
  endDate: '2025-06-15T12:00:00',
  category: 'ACADEMIC',
  creatorId: 'auth0|123',
  status: 'PUBLISHED',
  attendingCount: 0,
  createdAt: '2025-01-01T00:00:00',
}

afterEach(() => cleanup())

describe('IcsExportButton', () => {
  it('renders section heading', () => {
    render(<IcsExportButton event={mockEvent} />)
    expect(screen.getByText('Ajouter au calendrier')).toBeTruthy()
  })

  it('renders Apple Calendar, Outlook, and Google Calendar buttons', () => {
    render(<IcsExportButton event={mockEvent} />)
    expect(screen.getByText('Apple Calendar')).toBeTruthy()
    expect(screen.getByText('Outlook')).toBeTruthy()
    expect(screen.getByText('Google Calendar')).toBeTruthy()
  })

  it('does not render generic download button', () => {
    render(<IcsExportButton event={mockEvent} />)
    expect(screen.queryByText('Télécharger .ics')).toBeNull()
  })

  it('Apple Calendar button triggers file download on click', () => {
    const createObjectURL = vi.fn(() => 'blob:test-url')
    const revokeObjectURL = vi.fn()
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = revokeObjectURL

    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {})

    render(<IcsExportButton event={mockEvent} />)
    fireEvent.click(screen.getByText('Apple Calendar'))

    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(clickSpy).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test-url')

    clickSpy.mockRestore()
  })

  it('Outlook button triggers file download on click', () => {
    const createObjectURL = vi.fn(() => 'blob:test-url')
    const revokeObjectURL = vi.fn()
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = revokeObjectURL

    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {})

    render(<IcsExportButton event={mockEvent} />)
    fireEvent.click(screen.getByText('Outlook'))

    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(clickSpy).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test-url')

    clickSpy.mockRestore()
  })

  it('Apple Calendar download uses event id in filename', () => {
    const createObjectURL = vi.fn(() => 'blob:test-url')
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = vi.fn()

    let capturedDownload = ''
    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(function (this: HTMLAnchorElement) {
        capturedDownload = this.download
      })

    render(<IcsExportButton event={mockEvent} />)
    fireEvent.click(screen.getByText('Apple Calendar'))

    expect(capturedDownload).toBe('event-42.ics')

    clickSpy.mockRestore()
  })

  it('Google Calendar link has correct href and opens in new tab', () => {
    render(<IcsExportButton event={mockEvent} />)
    const link = screen.getByText('Google Calendar').closest('a')
    expect(link?.getAttribute('href')).toBe(
      'https://calendar.google.com/calendar/render?action=TEMPLATE&text=Test+Event',
    )
    expect(link?.getAttribute('target')).toBe('_blank')
    expect(link?.getAttribute('rel')).toBe('noopener noreferrer')
  })

  it('renders Apple Calendar description', () => {
    render(<IcsExportButton event={mockEvent} />)
    expect(
      screen.getByText("Double-cliquez le fichier téléchargé pour l'ouvrir dans Apple Calendar"),
    ).toBeTruthy()
  })

  it('renders Outlook description', () => {
    render(<IcsExportButton event={mockEvent} />)
    expect(
      screen.getByText('Téléchargez puis ouvrez le fichier dans Outlook pour l\'importer'),
    ).toBeTruthy()
  })

  it('renders Google Calendar description', () => {
    render(<IcsExportButton event={mockEvent} />)
    expect(
      screen.getByText("Ouvre Google Calendar avec l'événement pré-rempli"),
    ).toBeTruthy()
  })
})
