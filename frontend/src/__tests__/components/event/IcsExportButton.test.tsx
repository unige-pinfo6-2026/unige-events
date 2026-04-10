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
  it('renders download button and Google Calendar link', () => {
    render(<IcsExportButton event={mockEvent} />)
    expect(screen.getByText('Télécharger .ics')).toBeTruthy()
    expect(screen.getByText('Google Calendar')).toBeTruthy()
  })

  it('renders section heading', () => {
    render(<IcsExportButton event={mockEvent} />)
    expect(screen.getByText('Ajouter au calendrier')).toBeTruthy()
  })

  it('renders Apple Calendar / Outlook button', () => {
    render(<IcsExportButton event={mockEvent} />)
    expect(screen.getByText('Apple Calendar / Outlook')).toBeTruthy()
  })

  it('triggers file download on button click', () => {
    const createObjectURL = vi.fn(() => 'blob:test-url')
    const revokeObjectURL = vi.fn()
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = revokeObjectURL

    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {})

    render(<IcsExportButton event={mockEvent} />)
    fireEvent.click(screen.getByText('Télécharger .ics'))

    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(clickSpy).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test-url')

    clickSpy.mockRestore()
  })

  it('Apple Calendar / Outlook button triggers download', () => {
    const createObjectURL = vi.fn(() => 'blob:test-url')
    const revokeObjectURL = vi.fn()
    URL.createObjectURL = createObjectURL
    URL.revokeObjectURL = revokeObjectURL

    const clickSpy = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => {})

    render(<IcsExportButton event={mockEvent} />)
    fireEvent.click(screen.getByText('Apple Calendar / Outlook'))

    expect(createObjectURL).toHaveBeenCalledOnce()
    expect(clickSpy).toHaveBeenCalledOnce()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test-url')

    clickSpy.mockRestore()
  })

  it('download anchor uses event id in filename', () => {
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
    fireEvent.click(screen.getByText('Télécharger .ics'))

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
})
