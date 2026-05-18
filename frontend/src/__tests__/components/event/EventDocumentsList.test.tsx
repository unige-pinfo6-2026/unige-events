import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'

vi.mock('@/utils/downloadAttachment', () => ({
  downloadAttachment: vi.fn().mockResolvedValue(undefined),
}))

import EventDocumentsList from '@/components/event/EventDocumentsList'
import { downloadAttachment } from '@/utils/downloadAttachment'
import type { Attachment } from '@/types/attachment'

const mockDownload = downloadAttachment as ReturnType<typeof vi.fn>

function makeAttachment(overrides: Partial<Attachment> = {}): Attachment {
  return {
    id: 1,
    fileName: 'programme.pdf',
    fileUrl: 'https://s3.example.com/events/42/programme.pdf',
    fileSize: 1024 * 1024,
    mimeType: 'application/pdf',
    uploadedById: 'u-1',
    uploadedAt: '2026-05-18T10:00:00Z',
    ...overrides,
  }
}

afterEach(() => {
  cleanup()
  mockDownload.mockClear()
})

describe('EventDocumentsList', () => {
  it('renders nothing when the attachments array is empty', () => {
    const { container } = render(<EventDocumentsList attachments={[]} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders one row per attachment with filename and size', () => {
    render(<EventDocumentsList attachments={[
      makeAttachment({ id: 1, fileName: 'a.pdf', fileSize: 2 * 1024 * 1024 }),
      makeAttachment({ id: 2, fileName: 'b.docx', fileSize: 512 }),
    ]} />)

    expect(screen.getByText('Documents')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Télécharger a.pdf' })).toBeTruthy()
    expect(screen.getByRole('button', { name: 'Télécharger b.docx' })).toBeTruthy()
    expect(screen.getByText('2.0 MB')).toBeTruthy()
    expect(screen.getByText('512 B')).toBeTruthy()
  })

  it('invokes downloadAttachment with the row\'s url + filename when clicked', () => {
    render(<EventDocumentsList attachments={[
      makeAttachment({ fileName: 'rapport.pdf', fileUrl: 'https://s3.example.com/rapport.pdf' }),
    ]} />)

    fireEvent.click(screen.getByRole('button', { name: 'Télécharger rapport.pdf' }))

    expect(mockDownload).toHaveBeenCalledTimes(1)
    expect(mockDownload).toHaveBeenCalledWith('https://s3.example.com/rapport.pdf', 'rapport.pdf')
  })

  it('renders one button per attachment (no <a download> — cross-origin would ignore it)', () => {
    render(<EventDocumentsList attachments={[makeAttachment()]} />)
    expect(screen.queryByRole('link', { name: 'programme.pdf' })).toBeNull()
    expect(screen.getByRole('button', { name: 'Télécharger programme.pdf' })).toBeTruthy()
  })
})
