import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'

import EventDocumentsList from '@/components/event/EventDocumentsList'
import type { Attachment } from '@/types/attachment'

function makeAttachment(overrides: Partial<Attachment> = {}): Attachment {
  return {
    id: 1,
    fileName: 'programme.pdf',
    fileUrl: 'http://minio:9000/bucket/event-attachments/abc.pdf',
    downloadUrl: '/api/events/42/attachments/1/download',
    fileSize: 1024 * 1024,
    mimeType: 'application/pdf',
    uploadedById: 'u-1',
    uploadedAt: '2026-05-18T10:00:00Z',
    ...overrides,
  }
}

afterEach(() => cleanup())

describe('EventDocumentsList', () => {
  it('renders nothing when the attachments array is empty', () => {
    const { container } = render(<EventDocumentsList attachments={[]} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders one row per attachment with filename and size', () => {
    render(<EventDocumentsList attachments={[
      makeAttachment({ id: 1, fileName: 'a.pdf', fileSize: 2 * 1024 * 1024, downloadUrl: '/api/events/42/attachments/1/download' }),
      makeAttachment({ id: 2, fileName: 'b.docx', fileSize: 512, downloadUrl: '/api/events/42/attachments/2/download' }),
    ]} />)

    expect(screen.getByText('Documents')).toBeTruthy()
    expect(screen.getByText('a.pdf')).toBeTruthy()
    expect(screen.getByText('b.docx')).toBeTruthy()
    expect(screen.getByText('2.0 MB')).toBeTruthy()
    expect(screen.getByText('512 B')).toBeTruthy()
  })

  it('points the link at the same-origin downloadUrl (NOT the internal MinIO fileUrl)', () => {
    render(<EventDocumentsList attachments={[
      makeAttachment({
        fileName: 'rapport.pdf',
        fileUrl: 'http://minio:9000/bucket/event-attachments/abc.pdf',
        downloadUrl: '/api/events/42/attachments/1/download',
      }),
    ]} />)

    const link = screen.getByRole('link', { name: 'Télécharger rapport.pdf' }) as HTMLAnchorElement
    // SCRUM-149 follow-up — downloadUrl is the public API endpoint that
    // streams the file with Content-Disposition: attachment ; fileUrl points
    // at minio:9000 which is unreachable from the browser.
    expect(link.getAttribute('href')).toBe('/api/events/42/attachments/1/download')
    expect(link.getAttribute('href')).not.toContain('minio')
    expect(link.getAttribute('download')).toBe('rapport.pdf')
  })

  it('uses the original filename as the download attribute (browser-side hint)', () => {
    render(<EventDocumentsList attachments={[makeAttachment({ fileName: 'résumé.pdf' })]} />)
    const link = screen.getByRole('link', { name: 'Télécharger résumé.pdf' }) as HTMLAnchorElement
    expect(link.getAttribute('download')).toBe('résumé.pdf')
  })
})
