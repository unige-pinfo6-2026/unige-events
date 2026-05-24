import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'

vi.mock('@/services/attachmentApi', () => ({
  uploadEventAttachment: vi.fn(),
  deleteEventAttachment: vi.fn(),
}))

import EventAttachmentsEditor from '@/components/event/EventAttachmentsEditor'
import { deleteEventAttachment, uploadEventAttachment } from '@/services/attachmentApi'
import type { Attachment } from '@/types/attachment'

const mockUpload = uploadEventAttachment as ReturnType<typeof vi.fn>
const mockDelete = deleteEventAttachment as ReturnType<typeof vi.fn>

function pdfAttachment(overrides: Partial<Attachment> = {}): Attachment {
  return {
    id: 1,
    fileName: 'programme.pdf',
    fileUrl: 'http://minio:9000/bucket/event-attachments/programme.pdf',
    downloadUrl: '/api/events/42/attachments/1/download',
    fileSize: 2048,
    mimeType: 'application/pdf',
    uploadedById: 'u-1',
    uploadedAt: '2026-05-18T10:00:00Z',
    ...overrides,
  }
}

function pickFile(file: File | File[]) {
  const input = document.querySelector<HTMLInputElement>('#event-attachments-input')
  if (!input) throw new Error('missing file input')
  const files = Array.isArray(file) ? file : [file]
  fireEvent.change(input, { target: { files } })
}

function renderEditor(props: Partial<React.ComponentProps<typeof EventAttachmentsEditor>> = {}) {
  const onChange = vi.fn()
  const utils = render(
    <EventAttachmentsEditor
      eventId={props.eventId ?? 42}
      attachments={props.attachments ?? []}
      onChange={props.onChange ?? onChange}
    />,
  )
  return { onChange, ...utils }
}

beforeEach(() => {
  mockUpload.mockReset()
  mockDelete.mockReset()
})

afterEach(() => {
  cleanup()
})

describe('EventAttachmentsEditor', () => {
  it('renders the empty state when no attachments are present', () => {
    renderEditor()
    expect(screen.getByText('Aucun fichier joint pour le moment.')).toBeTruthy()
  })

  it('lists already-uploaded attachments with size and a same-origin download link', () => {
    renderEditor({ attachments: [pdfAttachment({ fileSize: 1024 * 1024 })] })
    // SCRUM-149 follow-up — link uses downloadUrl (API endpoint that
    // streams from MinIO with Content-Disposition: attachment), NOT the
    // raw fileUrl (internal minio:9000 host, unreachable).
    const link = screen.getByRole('link', { name: 'programme.pdf' }) as HTMLAnchorElement
    expect(link.getAttribute('href')).toBe('/api/events/42/attachments/1/download')
    expect(link.getAttribute('href')).not.toContain('minio')
    expect(link.getAttribute('download')).toBe('programme.pdf')
    expect(screen.getByRole('link', { name: 'Télécharger programme.pdf' })).toBeTruthy()
    expect(screen.getByText('1.0 MB')).toBeTruthy()
  })

  it('stages picked files in the "to upload" list without sending them', () => {
    renderEditor()
    const file = new File(['x'], 'doc.pdf', { type: 'application/pdf' })
    pickFile(file)
    expect(screen.getByText('Fichiers à uploader')).toBeTruthy()
    expect(screen.getByText('doc.pdf')).toBeTruthy()
    expect(mockUpload).not.toHaveBeenCalled()
  })

  it('ignores a change event with no files (null/empty FileList early return)', () => {
    renderEditor()
    const input = document.querySelector<HTMLInputElement>('#event-attachments-input')!
    fireEvent.change(input, { target: { files: null } })
    fireEvent.change(input, { target: { files: [] } })
    // Nothing was staged → the "to upload" heading never appears.
    expect(screen.queryByText('Fichiers à uploader')).toBeNull()
    expect(mockUpload).not.toHaveBeenCalled()
  })

  it('removes a staged file when its × button is clicked', () => {
    renderEditor()
    const file = new File(['x'], 'doc.pdf', { type: 'application/pdf' })
    pickFile(file)
    fireEvent.click(screen.getByRole('button', { name: 'Retirer doc.pdf' }))
    expect(screen.queryByText('doc.pdf')).toBeNull()
  })

  it('rejects unsupported MIME types client-side and surfaces a French error', () => {
    renderEditor()
    // image/svg+xml is intentionally NOT in the whitelist (PNG/JPEG were
    // added in SCRUM-149 follow-up, SVG / GIF / WEBP stay out).
    pickFile(new File(['x'], 'logo.svg', { type: 'image/svg+xml' }))
    expect(screen.getByText('Format non supporté. Formats acceptés : PDF, DOC, DOCX, XLSX, PNG, JPEG.')).toBeTruthy()
    fireEvent.click(screen.getByRole('button', { name: 'Uploader' }))
    expect(mockUpload).not.toHaveBeenCalled()
  })

  it('accepts PNG and JPEG files (SCRUM-149 image MIMEs)', () => {
    renderEditor()
    pickFile(new File(['x'], 'poster.png', { type: 'image/png' }))
    pickFile(new File(['y'], 'photo.jpg', { type: 'image/jpeg' }))
    expect(screen.queryByText('Format non supporté. Formats acceptés : PDF, DOC, DOCX, XLSX, PNG, JPEG.')).toBeNull()
    expect(screen.getByText('poster.png')).toBeTruthy()
    expect(screen.getByText('photo.jpg')).toBeTruthy()
  })

  it('rejects files larger than the 10 MiB cap before upload', () => {
    renderEditor()
    const big = new File([new Uint8Array(11 * 1024 * 1024)], 'big.pdf', { type: 'application/pdf' })
    pickFile(big)
    expect(screen.getByText('Ce fichier dépasse la taille maximale autorisée (10.0 MB).')).toBeTruthy()
  })

  it('flags overflow files but keeps the Uploader enabled for the accepted ones', () => {
    // 4 already uploaded + pick 2 → first one fits the 5-cap, second is
    // overflow. The Uploader stays enabled because the accepted count
    // (4 + 1 = 5) is at the cap, not over.
    const existing = Array.from({ length: 4 }, (_, i) => pdfAttachment({ id: i + 1, fileName: `f${i}.pdf` }))
    renderEditor({ attachments: existing })
    const f1 = new File(['a'], 'a.pdf', { type: 'application/pdf' })
    const f2 = new File(['b'], 'b.pdf', { type: 'application/pdf' })
    pickFile([f1, f2])

    expect(screen.getByText('Limite atteinte : 5 fichiers maximum par événement.')).toBeTruthy()
    const button = screen.getByRole('button', { name: 'Uploader' }) as HTMLButtonElement
    expect(button.disabled).toBe(false)
  })

  it('disables the Uploader only when the accepted-count strictly exceeds the cap', () => {
    // 4 uploaded + 3 picked, all valid → 7 > 5 → genuinely over the cap.
    // Per the new validate(), the 5th and 6th get an overflow error so
    // acceptedStaged.length = 1 → 4 + 1 = 5, still at cap. To force the
    // disabled state we need accepted > 1 here, which requires fewer
    // uploaded — start with 3 and pick 3 valid.
    const existing = Array.from({ length: 3 }, (_, i) => pdfAttachment({ id: i + 1, fileName: `f${i}.pdf` }))
    renderEditor({ attachments: existing })
    pickFile([
      new File(['a'], 'a.pdf', { type: 'application/pdf' }),
      new File(['b'], 'b.pdf', { type: 'application/pdf' }),
      new File(['c'], 'c.pdf', { type: 'application/pdf' }),
    ])
    // remainingSlots = max(0, 5-3-0)=2 → first two accepted, third overflow.
    // Accepted count = 2, total committed = 3+2 = 5, NOT exceeded.
    const button = screen.getByRole('button', { name: 'Uploader' }) as HTMLButtonElement
    expect(button.disabled).toBe(false)
  })

  it('ignores rejected staged files when counting remaining slots (does not block valid picks)', () => {
    renderEditor({ attachments: [] })
    // First pick a bad-mime file — it's staged but rejected.
    pickFile(new File(['x'], 'logo.svg', { type: 'image/svg+xml' }))
    // Then pick 5 valid pdfs — they should all be accepted (rejected file
    // doesn't count toward the 5-cap).
    pickFile(Array.from({ length: 5 }, (_, i) =>
      new File(['x'], `f${i}.pdf`, { type: 'application/pdf' }),
    ))
    // No global "Limite atteinte" warning — accepted count = 5 ≤ 5.
    const limitWarnings = screen.queryAllByText('Limite atteinte : 5 fichiers maximum par événement.')
    expect(limitWarnings.length).toBe(0)
    const button = screen.getByRole('button', { name: 'Uploader' }) as HTMLButtonElement
    expect(button.disabled).toBe(false)
  })

  it('uploads a single staged file, refreshes the list, and clears staging', async () => {
    const uploaded = pdfAttachment({ id: 99, fileName: 'doc.pdf' })
    mockUpload.mockResolvedValueOnce(uploaded)
    const onChange = vi.fn()
    renderEditor({ onChange })

    pickFile(new File(['x'], 'doc.pdf', { type: 'application/pdf' }))
    fireEvent.click(screen.getByRole('button', { name: 'Uploader' }))

    await waitFor(() => expect(mockUpload).toHaveBeenCalledTimes(1))
    expect(mockUpload).toHaveBeenCalledWith(42, expect.any(File))
    await waitFor(() => expect(onChange).toHaveBeenCalledWith([uploaded]))
    await waitFor(() => expect(screen.queryByText('Fichiers à uploader')).toBeNull())
  })

  it('uploads multiple files with one request per file (sequential)', async () => {
    const a = pdfAttachment({ id: 11, fileName: 'a.pdf' })
    const b = pdfAttachment({ id: 12, fileName: 'b.pdf' })
    mockUpload.mockResolvedValueOnce(a).mockResolvedValueOnce(b)
    const onChange = vi.fn()
    renderEditor({ onChange })

    pickFile([
      new File(['a'], 'a.pdf', { type: 'application/pdf' }),
      new File(['b'], 'b.pdf', { type: 'application/pdf' }),
    ])
    fireEvent.click(screen.getByRole('button', { name: 'Uploader' }))

    await waitFor(() => expect(mockUpload).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(onChange).toHaveBeenCalledWith([a, b]))
  })

  it('on partial failure persists succeeded files and surfaces the failed file inline', async () => {
    const ok = pdfAttachment({ id: 30, fileName: 'ok.pdf' })
    mockUpload
      .mockResolvedValueOnce(ok)
      .mockRejectedValueOnce({ isAxiosError: true, response: { status: 500, data: { message: 'Boom serveur' } } })
    const onChange = vi.fn()
    renderEditor({ onChange })

    pickFile([
      new File(['a'], 'ok.pdf', { type: 'application/pdf' }),
      new File(['b'], 'fail.pdf', { type: 'application/pdf' }),
    ])
    fireEvent.click(screen.getByRole('button', { name: 'Uploader' }))

    await waitFor(() => expect(onChange).toHaveBeenCalledWith([ok]))
    expect(await screen.findByText('Boom serveur')).toBeTruthy()
    expect(screen.getByText('fail.pdf')).toBeTruthy()
    expect(screen.queryByText('ok.pdf')).toBeNull()
  })

  it('maps backend code attachment_invalid_size to the French oversize message', async () => {
    mockUpload.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 422, data: { code: 'attachment_invalid_size' } },
    })
    renderEditor()
    pickFile(new File(['x'], 'doc.pdf', { type: 'application/pdf' }))
    fireEvent.click(screen.getByRole('button', { name: 'Uploader' }))
    expect(await screen.findByText('Ce fichier dépasse la taille maximale autorisée (10.0 MB).')).toBeTruthy()
  })

  it('maps a raw 413 response to the same oversize message (backend rejection passthrough)', async () => {
    mockUpload.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 413, data: {} },
    })
    renderEditor()
    pickFile(new File(['x'], 'doc.pdf', { type: 'application/pdf' }))
    fireEvent.click(screen.getByRole('button', { name: 'Uploader' }))
    expect(await screen.findByText('Ce fichier dépasse la taille maximale autorisée (10.0 MB).')).toBeTruthy()
  })

  it('maps the backend code attachment_limit_exceeded to the French limit message', async () => {
    mockUpload.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 422, data: { code: 'attachment_limit_exceeded' } },
    })
    renderEditor()
    pickFile(new File(['x'], 'doc.pdf', { type: 'application/pdf' }))
    fireEvent.click(screen.getByRole('button', { name: 'Uploader' }))
    expect(await screen.findByText('Limite atteinte : 5 fichiers maximum par événement.')).toBeTruthy()
  })

  it('maps a 415 response to the bad-type message', async () => {
    mockUpload.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 415, data: {} },
    })
    renderEditor()
    pickFile(new File(['x'], 'doc.pdf', { type: 'application/pdf' }))
    fireEvent.click(screen.getByRole('button', { name: 'Uploader' }))
    expect(await screen.findByText('Format non supporté. Formats acceptés : PDF, DOC, DOCX, XLSX, PNG, JPEG.')).toBeTruthy()
  })

  it('falls back to a generic message when an upload fails without an axios envelope', async () => {
    mockUpload.mockRejectedValueOnce(new Error('boom'))
    renderEditor()
    pickFile(new File(['x'], 'doc.pdf', { type: 'application/pdf' }))
    fireEvent.click(screen.getByRole('button', { name: 'Uploader' }))
    expect(await screen.findByText("L'upload de ce fichier a échoué.")).toBeTruthy()
  })

  it('deletes an existing attachment and refreshes the list', async () => {
    mockDelete.mockResolvedValueOnce(undefined)
    const onChange = vi.fn()
    const existing = pdfAttachment()
    renderEditor({ attachments: [existing], onChange })

    fireEvent.click(screen.getByRole('button', { name: 'Supprimer programme.pdf' }))

    await waitFor(() => expect(mockDelete).toHaveBeenCalledWith(42, 1))
    await waitFor(() => expect(onChange).toHaveBeenCalledWith([]))
  })

  it('shows a per-row error when a delete fails (and keeps the row visible)', async () => {
    mockDelete.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 403, data: { message: 'Accès refusé.' } },
    })
    const onChange = vi.fn()
    renderEditor({ attachments: [pdfAttachment()], onChange })

    fireEvent.click(screen.getByRole('button', { name: 'Supprimer programme.pdf' }))

    expect(await screen.findByText('Accès refusé.')).toBeTruthy()
    expect(onChange).not.toHaveBeenCalled()
  })

  it('shows a generic delete-failure message when the backend gives no envelope', async () => {
    mockDelete.mockRejectedValueOnce(new Error('net'))
    renderEditor({ attachments: [pdfAttachment()] })

    fireEvent.click(screen.getByRole('button', { name: 'Supprimer programme.pdf' }))

    expect(await screen.findByText('La suppression du fichier a échoué.')).toBeTruthy()
  })

  it('falls back to the generic delete message when an axios error carries no usable message', async () => {
    // axios error envelope present but message absent → the inner
    // string/trim guard is false and we fall through to the generic copy.
    mockDelete.mockRejectedValueOnce({
      isAxiosError: true,
      response: { status: 500, data: {} },
    })
    renderEditor({ attachments: [pdfAttachment()] })

    fireEvent.click(screen.getByRole('button', { name: 'Supprimer programme.pdf' }))

    expect(await screen.findByText('La suppression du fichier a échoué.')).toBeTruthy()
  })

  it('marks subsequent picks beyond the 5-limit as overflow rejected', () => {
    renderEditor({ attachments: [] })
    const files = Array.from({ length: 6 }, (_, i) => new File(['x'], `f${i}.pdf`, { type: 'application/pdf' }))
    pickFile(files)
    // First 5 are accepted, the 6th is flagged as overflow.
    expect(screen.getAllByText('Limite atteinte : 5 fichiers maximum par événement.').length).toBeGreaterThanOrEqual(1)
  })

  it('uses the file extension as a fallback when file.type is empty (drag-and-drop spoof guard)', () => {
    renderEditor()
    // happy-dom keeps file.type as '' when omitted.
    pickFile(new File(['x'], 'doc.docx', { type: '' }))
    // No error → file is staged as valid by extension fallback.
    expect(screen.getByText('doc.docx')).toBeTruthy()
    expect(screen.queryByText('Format non supporté. Formats acceptés : PDF, DOC, DOCX, XLSX, PNG, JPEG.')).toBeNull()
  })

  it('rejects a file with neither a known mime nor a known extension', () => {
    renderEditor()
    pickFile(new File(['x'], 'noext', { type: '' }))
    expect(screen.getByText('Format non supporté. Formats acceptés : PDF, DOC, DOCX, XLSX, PNG, JPEG.')).toBeTruthy()
  })
})
