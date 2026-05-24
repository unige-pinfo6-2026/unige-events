import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'

import PendingAttachmentsEditor, {
  type PendingAttachment,
} from '@/components/event/PendingAttachmentsEditor'

function pickFile(file: File | File[]) {
  const input = document.querySelector<HTMLInputElement>('#pending-event-attachments-input')
  if (!input) throw new Error('missing file input')
  const files = Array.isArray(file) ? file : [file]
  fireEvent.change(input, { target: { files } })
}

function renderEditor(initial: PendingAttachment[] = []) {
  const onAdd = vi.fn<(entries: PendingAttachment[]) => void>()
  const onRemove = vi.fn<(id: string) => void>()
  const utils = render(
    <PendingAttachmentsEditor pending={initial} onAdd={onAdd} onRemove={onRemove} />,
  )
  return { onAdd, onRemove, ...utils }
}

function stagedPdf(overrides: Partial<PendingAttachment> = {}): PendingAttachment {
  return {
    id: 'staged-1',
    file: new File(['x'], 'doc.pdf', { type: 'application/pdf' }),
    error: null,
    ...overrides,
  }
}

afterEach(() => cleanup())

describe('PendingAttachmentsEditor', () => {
  it('renders the empty state when nothing is staged', () => {
    renderEditor()
    expect(screen.getByText('Aucun fichier joint pour le moment.')).toBeTruthy()
  })

  it('emits onAdd with one entry per picked file (validated)', () => {
    const { onAdd } = renderEditor()
    pickFile([
      new File(['a'], 'a.pdf', { type: 'application/pdf' }),
      new File(['b'], 'b.png', { type: 'image/png' }),
    ])
    expect(onAdd).toHaveBeenCalledTimes(1)
    const entries = onAdd.mock.calls[0][0]
    expect(entries).toHaveLength(2)
    expect(entries[0].file.name).toBe('a.pdf')
    expect(entries[0].error).toBeNull()
    expect(entries[1].file.name).toBe('b.png')
    expect(entries[1].error).toBeNull()
  })

  it('flags unsupported MIME types with the bad-type error', () => {
    const { onAdd } = renderEditor()
    pickFile(new File(['x'], 'logo.svg', { type: 'image/svg+xml' }))
    const entry = onAdd.mock.calls[0][0][0]
    expect(entry.error).toBe('Format non supporté. Formats acceptés : PDF, DOC, DOCX, XLSX, PNG, JPEG.')
  })

  it('flags oversized files with the size error', () => {
    const { onAdd } = renderEditor()
    pickFile(new File([new Uint8Array(11 * 1024 * 1024)], 'big.pdf', { type: 'application/pdf' }))
    const entry = onAdd.mock.calls[0][0][0]
    expect(entry.error).toBe('Ce fichier dépasse la taille maximale autorisée (10.0 MB).')
  })

  it('flags overflow when adding more than the 5-files cap', () => {
    const fourAccepted = Array.from({ length: 4 }, (_, i) =>
      stagedPdf({ id: `s${i}`, file: new File(['x'], `f${i}.pdf`, { type: 'application/pdf' }) }),
    )
    const { onAdd } = renderEditor(fourAccepted)
    pickFile([
      new File(['a'], 'a.pdf', { type: 'application/pdf' }),
      new File(['b'], 'b.pdf', { type: 'application/pdf' }),
    ])
    const entries = onAdd.mock.calls[0][0]
    expect(entries[0].error).toBeNull()
    expect(entries[1].error).toBe('Limite atteinte : 5 fichiers maximum par événement.')
  })

  it('renders the staged list with "Sera publié" badge for valid entries', () => {
    renderEditor([stagedPdf()])
    expect(screen.getByText('doc.pdf')).toBeTruthy()
    // Wording made explicit so the user understands no extra click is
    // needed — the upload happens automatically with createEvent.
    expect(screen.getByText('Sera publié')).toBeTruthy()
  })

  it('shows the explanatory line "automatiquement publiés"', () => {
    renderEditor()
    expect(
      screen.getByText(/automatiquement publiés à la création de l'événement/),
    ).toBeTruthy()
  })

  it('renders rejected entries with "Refusé" badge + error text', () => {
    renderEditor([stagedPdf({ id: 's-bad', error: 'Format non supporté.' })])
    expect(screen.getByText('Refusé')).toBeTruthy()
    expect(screen.getByText('Format non supporté.')).toBeTruthy()
  })

  it('emits onRemove(id) when the × button is clicked', () => {
    const { onRemove } = renderEditor([stagedPdf({ id: 'pick-me' })])
    fireEvent.click(screen.getByRole('button', { name: 'Retirer doc.pdf' }))
    expect(onRemove).toHaveBeenCalledWith('pick-me')
  })

  it('ignores a change event with no files (null FileList early return)', () => {
    const { onAdd } = renderEditor()
    const input = document.querySelector<HTMLInputElement>('#pending-event-attachments-input')!
    fireEvent.change(input, { target: { files: null } })
    expect(onAdd).not.toHaveBeenCalled()
  })

  it('ignores a change event with an empty file list (length === 0 early return)', () => {
    const { onAdd } = renderEditor()
    const input = document.querySelector<HTMLInputElement>('#pending-event-attachments-input')!
    fireEvent.change(input, { target: { files: [] } })
    expect(onAdd).not.toHaveBeenCalled()
  })
})
