// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import EventForm from '../../components/events/EventForm'
import { EventStatus } from '../../types'
import type { EventFormValues } from '../../hooks/useEventForm'

const baseValues: EventFormValues = {
  title: 'Forum des associations',
  description: 'Rencontrez les associations du campus.',
  location: 'Uni Dufour',
  startDate: '2026-04-10T10:00',
  endDate: '2026-04-10T12:00',
  category: 'SOCIAL',
  capacity: '120',
  status: EventStatus.DRAFT,
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('EventForm', () => {
  it('renders capacity and image errors with the loading submit state', () => {
    render(
      <EventForm
        title='Créer un événement'
        submitLabel='Créer'
        values={baseValues}
        errors={{ capacity: 'Capacité invalide', image: 'Image invalide' }}
        submitting
        imagePreview={null}
        selectedImageName={null}
        onFieldChange={vi.fn()}
        onImageChange={vi.fn()}
        onSubmit={vi.fn(async () => undefined)}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.getByText('Capacité invalide')).toBeTruthy()
    expect(screen.getByText('Image invalide')).toBeTruthy()
    expect(screen.getByText('Ajoutez une image de couverture')).toBeTruthy()
    expect(screen.getByText('PNG, JPG ou WEBP')).toBeTruthy()
    expect((screen.getByRole('button', { name: 'Enregistrement...' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('renders preview metadata and forwards cancel and field changes', () => {
    const onFieldChange = vi.fn()
    const onCancel = vi.fn()
    const onImageChange = vi.fn()

    render(
      <EventForm
        title="Modifier l'événement"
        submitLabel='Enregistrer'
        values={baseValues}
        errors={{}}
        submitting={false}
        imagePreview='https://example.com/banner.png'
        selectedImageName='banner.png'
        onFieldChange={onFieldChange}
        onImageChange={onImageChange}
        onSubmit={vi.fn(async () => undefined)}
        onCancel={onCancel}
      />,
    )

    fireEvent.change(screen.getByLabelText(/Capacité/i), { target: { value: '200' } })
    fireEvent.change(screen.getByLabelText(/Statut/i), { target: { value: 'PUBLISHED' } })
    fireEvent.change(document.querySelector('#event-banner') as HTMLInputElement, {
      target: { files: [new File(['img'], 'banner.png', { type: 'image/png' })] },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

    expect(screen.getByAltText('Aperçu de la bannière').getAttribute('src')).toBe('https://example.com/banner.png')
    expect(screen.getByText('banner.png')).toBeTruthy()
    expect(onFieldChange).toHaveBeenCalledWith('capacity', '200')
    expect(onFieldChange).toHaveBeenCalledWith('status', 'PUBLISHED')
    expect(onImageChange).toHaveBeenCalled()
    expect(onCancel).toHaveBeenCalled()
  })
})
