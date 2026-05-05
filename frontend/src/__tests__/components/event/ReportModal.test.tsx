import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import ReportModal from '@/components/event/ReportModal'
import { REPORT_REASONS } from '@/hooks/useReport'
import type { ReportReason } from '@/hooks/useReport'

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

  it('renders all reason options', () => {
    renderModal()
    for (const r of REPORT_REASONS) {
      expect(screen.getByText(r)).toBeTruthy()
    }
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
    fireEvent.change(select, { target: { value: 'Spam' } })
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

  it('calls onSubmit with reason only when description is empty', async () => {
    const { onSubmit } = renderModal()
    fireEvent.change(screen.getByLabelText(/Motif/i), { target: { value: 'Spam' } })
    fireEvent.click(screen.getByRole('button', { name: /Envoyer le signalement/i }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('Spam', undefined))
  })

  it('calls onSubmit with reason and description when both filled', async () => {
    const { onSubmit } = renderModal()
    fireEvent.change(screen.getByLabelText(/Motif/i), { target: { value: 'Autre' } })
    fireEvent.change(screen.getByLabelText(/Détails/i), { target: { value: 'Précisions' } })
    fireEvent.click(screen.getByRole('button', { name: /Envoyer le signalement/i }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('Autre', 'Précisions'))
  })

  it('passes undefined description when description is blank whitespace', async () => {
    const { onSubmit } = renderModal()
    fireEvent.change(screen.getByLabelText(/Motif/i), { target: { value: 'Faux événement' } })
    fireEvent.change(screen.getByLabelText(/Détails/i), { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: /Envoyer le signalement/i }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('Faux événement', undefined))
  })

  it('does not call onSubmit when no reason is selected', () => {
    const { onSubmit } = renderModal()
    const form = screen.getByRole('button', { name: /Envoyer le signalement/i }).closest('form')!
    fireEvent.submit(form)
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
