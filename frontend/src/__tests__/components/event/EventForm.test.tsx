// @vitest-environment jsdom

import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import EventForm from '@/components/event/EventForm'
import type { EventFormValues } from '@/hooks/useEventForm'
import { useState } from 'react'

const baseValues: EventFormValues = {
  title: 'Forum des associations',
  description: 'Rencontrez les associations du campus.',
  location: 'Uni Dufour',
  startDate: '2026-04-10T10:00',
  endDate: '2026-04-10T12:00',
  category: 'SOCIAL',
  faculty: null,
  capacity: '120',
  status: 'DRAFT',
}

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

describe('EventForm', () => {
  it('renders capacity and image errors with the loading submit state', () => {
    render(
      <EventForm
        mode="create"
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
    expect(screen.getByText(/PNG, JPG ou WEBP/)).toBeTruthy()
    expect(screen.getByText(/\/ 120/)).toBeTruthy()
    expect(screen.getByText(/\/ 2000/)).toBeTruthy()
    expect((screen.getByLabelText(/Titre/i) as HTMLInputElement).maxLength).toBe(120)
    expect((screen.getByLabelText(/Description/i) as HTMLTextAreaElement).maxLength).toBe(2000)
    expect((screen.getByLabelText(/Début/i, { selector: 'input' }) as HTMLInputElement).type).toBe('date')
    expect((screen.getByLabelText(/Fin/i, { selector: 'input' }) as HTMLInputElement).type).toBe('date')
    expect(screen.getAllByText('00').length).toBeGreaterThan(0)
    expect(screen.getAllByText('23').length).toBeGreaterThan(0)
    expect(screen.getAllByText('59').length).toBeGreaterThan(0)
    expect((screen.getByRole('button', { name: 'Enregistrement...' }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('renders preview metadata and forwards cancel and field changes', () => {
    const onFieldChange = vi.fn()
    const onCancel = vi.fn()
    const onImageChange = vi.fn()

    render(
      <EventForm
        mode="edit"
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
    fireEvent.change(document.querySelector('#event-banner') as HTMLInputElement, {
      target: { files: [new File(['img'], 'banner.png', { type: 'image/png' })] },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Annuler' }))

    expect(screen.getByAltText('Aperçu de la bannière').getAttribute('src')).toBe('https://example.com/banner.png')
    expect(screen.getByText('banner.png')).toBeTruthy()
    expect(onFieldChange).toHaveBeenCalledWith('capacity', '200')
    expect(onImageChange).toHaveBeenCalled()
    expect(onCancel).toHaveBeenCalled()
  })

  it('combines date and time parts into datetime values', () => {
    const emittedValues: string[] = []

    function StatefulForm() {
      const [values, setValues] = useState<EventFormValues>({ ...baseValues, startDate: '', endDate: '' })

      return (
        <EventForm
          mode="create"
          submitLabel='Créer'
          values={values}
          errors={{}}
          submitting={false}
          imagePreview={null}
          selectedImageName={null}
          onFieldChange={(field, value) => {
            if (field === 'startDate') {
              emittedValues.push(value as string)
            }
            setValues((current) => ({ ...current, [field]: value }))
          }}
          onImageChange={vi.fn()}
          onSubmit={vi.fn(async () => undefined)}
          onCancel={vi.fn()}
        />
      )
    }

    render(<StatefulForm />)

    fireEvent.change(screen.getByLabelText(/Début/i, { selector: 'input' }), { target: { value: '2099-04-10' } })
    fireEvent.change(screen.getByLabelText('Heure de début'), { target: { value: '20' } })
    fireEvent.change(screen.getByLabelText('Minute de début'), { target: { value: '45' } })

    expect(emittedValues).toEqual([
      '2099-04-10T00:00',
      '2099-04-10T20:00',
      '2099-04-10T20:45',
    ])
  })
})
