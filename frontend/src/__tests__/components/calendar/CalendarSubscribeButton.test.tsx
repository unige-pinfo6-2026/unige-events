// @vitest-environment jsdom
import { describe, it, expect, vi, afterEach, beforeEach } from 'vitest'
import { render, screen, cleanup, fireEvent, waitFor, act } from '@testing-library/react'
import CalendarSubscribeButton from '@/components/calendar/CalendarSubscribeButton'
import * as userService from '@/services/userService'
import type { CalendarTokenResponse } from '@/types/calendarToken'

vi.mock('@/services/userService', () => ({
  getCalendarToken: vi.fn(),
  regenerateCalendarToken: vi.fn(),
}))

const mockToken: CalendarTokenResponse = {
  calendarToken: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  webcalUrl: 'webcal://example.com/api/calendar/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.ics',
  httpsUrl: 'https://example.com/api/calendar/aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee.ics',
}

const mockToken2: CalendarTokenResponse = {
  calendarToken: '11111111-2222-3333-4444-555555555555',
  webcalUrl: 'webcal://example.com/api/calendar/11111111-2222-3333-4444-555555555555.ics',
  httpsUrl: 'https://example.com/api/calendar/11111111-2222-3333-4444-555555555555.ics',
}

afterEach(() => cleanup())

beforeEach(() => {
  vi.mocked(userService.getCalendarToken).mockResolvedValue(mockToken)
  vi.mocked(userService.regenerateCalendarToken).mockResolvedValue(mockToken2)
})

describe('CalendarSubscribeButton', () => {
  it('shows loading state on mount', () => {
    vi.mocked(userService.getCalendarToken).mockReturnValue(new Promise(() => {}))
    render(<CalendarSubscribeButton />)
    expect(screen.getByText('Chargement…')).toBeTruthy()
  })

  it('renders subscription links after token fetch', async () => {
    render(<CalendarSubscribeButton />)

    await waitFor(() => {
      expect(screen.getByText("S'abonner (Apple / Outlook)")).toBeTruthy()
      expect(screen.getByText("S'abonner (Google Calendar)")).toBeTruthy()
      expect(screen.getByText('Télécharger le flux .ics')).toBeTruthy()
    })
  })

  it('Apple/Outlook link uses webcalUrl', async () => {
    render(<CalendarSubscribeButton />)

    await waitFor(() => {
      const link = screen.getByText("S'abonner (Apple / Outlook)").closest('a')
      expect(link?.getAttribute('href')).toBe(mockToken.webcalUrl)
    })
  })

  it('Google Calendar link uses wrapped httpsUrl and opens in new tab', async () => {
    render(<CalendarSubscribeButton />)

    await waitFor(() => {
      const link = screen.getByText("S'abonner (Google Calendar)").closest('a')
      const expected = `https://calendar.google.com/calendar/r?cid=${encodeURIComponent(mockToken.httpsUrl)}`
      expect(link?.getAttribute('href')).toBe(expected)
      expect(link?.getAttribute('target')).toBe('_blank')
      expect(link?.getAttribute('rel')).toBe('noopener noreferrer')
    })
  })

  it('direct .ics link uses httpsUrl without download attribute', async () => {
    render(<CalendarSubscribeButton />)

    await waitFor(() => {
      const link = screen.getByText('Télécharger le flux .ics').closest('a')
      expect(link?.getAttribute('href')).toBe(mockToken.httpsUrl)
      expect(link?.hasAttribute('download')).toBe(false)
    })
  })

  it('shows error state on fetch failure', async () => {
    vi.mocked(userService.getCalendarToken).mockRejectedValue(new Error('Network error'))

    render(<CalendarSubscribeButton />)

    await waitFor(() => {
      expect(screen.getByText('Impossible de charger le lien de calendrier.')).toBeTruthy()
    })

    expect(screen.queryByText("S'abonner (Apple / Outlook)")).toBeNull()
  })

  it('regenerate button calls regenerateCalendarToken and updates URLs', async () => {
    render(<CalendarSubscribeButton />)

    await waitFor(() => {
      expect(screen.getByText("S'abonner (Apple / Outlook)")).toBeTruthy()
    })

    fireEvent.click(screen.getByText('Révoquer et régénérer le lien'))

    await waitFor(() => {
      const link = screen.getByText("S'abonner (Apple / Outlook)").closest('a')
      expect(link?.getAttribute('href')).toBe(mockToken2.webcalUrl)
    })

    expect(userService.regenerateCalendarToken).toHaveBeenCalledOnce()
  })

  it('shows confirmation message after regeneration', async () => {
    render(<CalendarSubscribeButton />)

    await waitFor(() => {
      expect(screen.getByText('Révoquer et régénérer le lien')).toBeTruthy()
    })

    fireEvent.click(screen.getByText('Révoquer et régénérer le lien'))

    await waitFor(() => {
      expect(
        screen.getByText(
          'Lien régénéré — pensez à mettre à jour votre abonnement dans votre application calendrier',
        ),
      ).toBeTruthy()
    })
  })

  it('does not render URLs while loading', () => {
    vi.mocked(userService.getCalendarToken).mockReturnValue(new Promise(() => {}))
    render(<CalendarSubscribeButton />)

    expect(screen.queryByText("S'abonner (Apple / Outlook)")).toBeNull()
    expect(screen.queryByText("S'abonner (Google Calendar)")).toBeNull()
    expect(screen.queryByText('Télécharger le flux .ics')).toBeNull()
  })

  it('clicking Copier le lien calls clipboard.writeText with httpsUrl', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })

    render(<CalendarSubscribeButton />)

    await waitFor(() => {
      expect(screen.getByText('Copier le lien')).toBeTruthy()
    })

    fireEvent.click(screen.getByText('Copier le lien'))

    await waitFor(() => {
      expect(writeText).toHaveBeenCalledWith(mockToken.httpsUrl)
    })
  })

  it('shows Lien copié ! confirmation after copy and resets after 2 seconds', async () => {
    vi.useFakeTimers()
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.assign(navigator, { clipboard: { writeText } })

    render(<CalendarSubscribeButton />)

    await act(async () => {
      await Promise.resolve()
    })

    expect(screen.getByText('Copier le lien')).toBeTruthy()

    fireEvent.click(screen.getByText('Copier le lien'))

    await act(async () => {
      await Promise.resolve()
    })

    expect(screen.getByText('Lien copié !')).toBeTruthy()

    await act(async () => {
      vi.advanceTimersByTime(2000)
    })

    expect(screen.queryByText('Lien copié !')).toBeNull()
    expect(screen.getByText('Copier le lien')).toBeTruthy()

    vi.useRealTimers()
  })
})
