// Regression: dropdown values must be the backend enum constants
// (SPAM | INAPPROPRIATE | FAKE | OTHER) — not the French labels — and the typed
// description must reach the submit handler unchanged. See PR for feature/s6-report-modal.
import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import ReportModal from '@/components/event/ReportModal'
import { REPORT_REASONS } from '@/types/report'
import type { ReportReason } from '@/types/report'

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

  it('renders one option per enum constant with French label visible', () => {
    renderModal()
    for (const [key, label] of Object.entries(REPORT_REASONS)) {
      const option = screen.getByRole('option', { name: label }) as HTMLOptionElement
      expect(option.value).toBe(key)
    }
  })

  it('renders description textarea', () => {
    renderModal()
    expect(screen.getByLabelText('Description')).toBeTruthy()
  })

  it('submit button is disabled when no reason selected', () => {
    renderModal()
    const submitBtn = screen.getByRole('button', { name: 'Signaler' })
    expect((submitBtn as HTMLButtonElement).disabled).toBe(true)
  })

  it('submit button is enabled after reason is selected', () => {
    renderModal()
    const select = screen.getByLabelText(/Motif/i)
    fireEvent.change(select, { target: { value: 'SPAM' } })
    const submitBtn = screen.getByRole('button', { name: 'Signaler' })
    expect((submitBtn as HTMLButtonElement).disabled).toBe(false)
  })

  it('shows "Envoi..." label when submitting', () => {
    renderModal({ submitting: true })
    expect(screen.getByText('Envoi...')).toBeTruthy()
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
    fireEvent.change(screen.getByLabelText(/Motif/i), { target: { value: 'SPAM' } })
    fireEvent.click(screen.getByRole('button', { name: 'Signaler' }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('SPAM', undefined))
  })

  // Plumbing-bug guard: drives the textarea via fireEvent and asserts that the
  // value the user actually typed reaches the submit handler intact.
  it('passes the typed description string from textarea state to onSubmit', async () => {
    const { onSubmit } = renderModal()
    fireEvent.change(screen.getByLabelText(/Motif/i), { target: { value: 'OTHER' } })

    const typedDescription = 'gvjhgj — un commentaire arbitraire saisi par l’utilisateur'
    fireEvent.change(screen.getByLabelText('Description'), {
      target: { value: typedDescription },
    })

    fireEvent.click(screen.getByRole('button', { name: 'Signaler' }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('OTHER', typedDescription))
  })

  it('passes undefined description when description is blank whitespace', async () => {
    const { onSubmit } = renderModal()
    fireEvent.change(screen.getByLabelText(/Motif/i), { target: { value: 'FAKE' } })
    fireEvent.change(screen.getByLabelText('Description'), { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Signaler' }))
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith('FAKE', undefined))
  })

  it('does not call onSubmit when no reason is selected', () => {
    const { onSubmit } = renderModal()
    const form = screen.getByRole('button', { name: 'Signaler' }).closest('form')!
    fireEvent.submit(form)
    expect(onSubmit).not.toHaveBeenCalled()
  })
})
