import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import ReportModal from '@/components/event/ReportModal'
import { REPORT_REASONS } from '@/types/admin'
import type { ReportReason } from '@/types/admin'

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderModal(
  overrides: Partial<{
    onClose: () => void
    onSubmit: (reason: ReportReason, description?: string) => Promise<void>
    submitting: boolean
  }> = {},
) {
  const onClose = overrides.onClose ?? vi.fn()
  const onSubmit = overrides.onSubmit ?? vi.fn().mockResolvedValue(undefined)
  const submitting = overrides.submitting ?? false
  render(<ReportModal onClose={onClose} onSubmit={onSubmit} submitting={submitting} />)
  return { onClose, onSubmit }
}

describe('ReportModal — rendering', () => {
  it('renders the modal title', () => {
    renderModal()
    expect(screen.getByText('Signaler cet événement')).toBeTruthy()
  })

  it('renders the human-readable French label for each reason option', () => {
    renderModal()
    for (const { label } of Object.values(REPORT_REASONS)) {
      expect(screen.getByText(label)).toBeTruthy()
    }
  })

  it('uses the enum keys as <option value> so the form submits the backend-valid reason', () => {
    renderModal()
    const select = screen.getByLabelText(/Motif/i) as HTMLSelectElement
    const values = Array.from(select.options).map(o => o.value)
    expect(values).toContain('SPAM')
    expect(values).toContain('INAPPROPRIATE')
    expect(values).toContain('FAKE')
    expect(values).toContain('OTHER')
  })

  it('renders description textarea', () => {
    renderModal()
    expect(screen.getByLabelText(/Détails/i)).toBeTruthy()
  })

  it('submit button is disabled when no reason selected', () => {
    renderModal()
    const submitBtn = screen.getByRole('button', { name: /Envoyer le signalement/i })
    expect((submitBtn as HTMLButtonElement).disabled).toBe(true)
  })

  it('submit button is enabled after reason is selected', () => {
    renderModal()
    const select = screen.getByLabelText(/Motif/i)
    fireEvent.change(select, { target: { value: 'SPAM' } })
    const submitBtn = screen.getByRole('button', { name: /Envoyer le signalement/i })
    expect((submitBtn as HTMLButtonElement).disabled).toBe(false)
  })

  it('shows "Envoi en cours…" label when submitting', () => {
    renderModal({ submitting: true })
    expect(screen.getByText('Envoi en cours…')).toBeTruthy()
  })

  it('disables buttons when submitting', () => {
    renderModal({ submitting: true })
    const buttons = screen.getAllByRole('button')
    for (const btn of buttons) {
      expect((btn as HTMLButtonElement).disabled).toBe(true)
    }
  })
})

describe('ReportModal — interactions', () => {
  it('calls onClose when Annuler is clicked', () => {
    const { onClose } = renderModal()
    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('calls onClose when X button is clicked', () => {
    const { onClose } = renderModal()
    fireEvent.click(screen.getByRole('button', { name: 'Fermer' }))
    expect(onClose).toHaveBeenCalledOnce()
  })

  it('calls onSubmit with the enum key and undefined description when none typed', async () => {
    const { onSubmit } = renderModal()
    fireEvent.change(screen.getByLabelText(/Motif/i), { target: { value: 'SPAM' } })
    fireEvent.click(screen.getByRole('button', { name: /Envoyer le signalement/i }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('SPAM', undefined))
  })

  it('calls onSubmit with reason and description when both filled', async () => {
    const { onSubmit } = renderModal()
    fireEvent.change(screen.getByLabelText(/Motif/i), { target: { value: 'OTHER' } })
    fireEvent.change(screen.getByLabelText(/Détails/i), { target: { value: 'Précisions' } })
    fireEvent.click(screen.getByRole('button', { name: /Envoyer le signalement/i }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('OTHER', 'Précisions'))
  })

  it('passes undefined description when description is blank whitespace', async () => {
    const { onSubmit } = renderModal()
    fireEvent.change(screen.getByLabelText(/Motif/i), { target: { value: 'FAKE' } })
    fireEvent.change(screen.getByLabelText(/Détails/i), { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: /Envoyer le signalement/i }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('FAKE', undefined))
  })

  it('does not call onSubmit when no reason is selected', () => {
    const { onSubmit } = renderModal()
    const form = screen.getByRole('button', { name: /Envoyer le signalement/i }).closest('form')!
    fireEvent.submit(form)
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
