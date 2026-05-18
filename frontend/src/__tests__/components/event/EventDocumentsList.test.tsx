import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import EventDocumentsList from '@/components/event/EventDocumentsList'
import type { Attachment } from '@/types/attachment'

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

afterEach(() => cleanup())

describe('EventDocumentsList', () => {
  it('renders nothing when the attachments array is empty', () => {
    const { container } = render(<EventDocumentsList attachments={[]} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders one row per attachment with filename, size, and a download link', () => {
    render(<EventDocumentsList attachments={[
      makeAttachment({ id: 1, fileName: 'a.pdf', fileSize: 2 * 1024 * 1024 }),
      makeAttachment({ id: 2, fileName: 'b.docx', fileSize: 512, fileUrl: 'https://s3.example.com/b.docx' }),
    ]} />)

    expect(screen.getByText('Documents')).toBeTruthy()
    const linkA = screen.getByRole('link', { name: 'a.pdf' }) as HTMLAnchorElement
    expect(linkA.href).toBe('https://s3.example.com/events/42/programme.pdf'.replace('programme.pdf', 'programme.pdf'))
    // The href is whatever fileUrl carries — verify both attachments produce the right one.
    expect(linkA.getAttribute('href')).toBe('https://s3.example.com/events/42/programme.pdf')
    expect(linkA.getAttribute('download')).toBe('a.pdf')
    expect(screen.getByText('2.0 MB')).toBeTruthy()

    const linkB = screen.getByRole('link', { name: 'b.docx' }) as HTMLAnchorElement
    expect(linkB.getAttribute('href')).toBe('https://s3.example.com/b.docx')
    expect(linkB.getAttribute('download')).toBe('b.docx')
    expect(screen.getByText('512 B')).toBeTruthy()
  })

  it('opens links in a new tab with rel=noopener', () => {
    render(<EventDocumentsList attachments={[makeAttachment()]} />)
    const link = screen.getByRole('link', { name: 'programme.pdf' })
    expect(link.getAttribute('target')).toBe('_blank')
    expect(link.getAttribute('rel')).toBe('noopener noreferrer')
  })
})
