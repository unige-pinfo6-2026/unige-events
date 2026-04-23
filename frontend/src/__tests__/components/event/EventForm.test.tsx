
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
  allDay: false,
  websiteUrl: '',
  contactEmail: '',
  registrationDeadline: '',
  tags: [],
}

const cropDefaults = {
  cropSource: null as string | null,
  cropAspect: 16 / 9,
  onCropConfirm: vi.fn(),
  onCropCancel: vi.fn(),
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
        cropSource={null}
        cropAspect={16 / 9}
        onCropConfirm={vi.fn()}
        onCropCancel={vi.fn()}
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
        cropSource={null}
        cropAspect={16 / 9}
        onCropConfirm={vi.fn()}
        onCropCancel={vi.fn()}
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

  it('calls onSaveDraft when the draft button is clicked', () => {
    const onSaveDraft = vi.fn().mockResolvedValue(undefined)

    render(
      <EventForm
        mode="create"
        submitLabel="Créer"
        values={baseValues}
        errors={{}}
        submitting={false}
        imagePreview={null}
        selectedImageName={null}
        onFieldChange={vi.fn()}
        onImageChange={vi.fn()}
        cropSource={null}
        cropAspect={16 / 9}
        onCropConfirm={vi.fn()}
        onCropCancel={vi.fn()}
        onSubmit={vi.fn(async () => undefined)}
        onCancel={vi.fn()}
        onSaveDraft={onSaveDraft}
      />,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Sauvegarder en Brouillon' }))

    expect(onSaveDraft).toHaveBeenCalled()
  })

  it('clears the date field when date input is cleared', () => {
    const onFieldChange = vi.fn()

    render(
      <EventForm
        mode="create"
        submitLabel="Créer"
        values={{ ...baseValues, startDate: '2099-04-10T10:00' }}
        errors={{}}
        submitting={false}
        imagePreview={null}
        selectedImageName={null}
        onFieldChange={onFieldChange}
        onImageChange={vi.fn()}
        cropSource={null}
        cropAspect={16 / 9}
        onCropConfirm={vi.fn()}
        onCropCancel={vi.fn()}
        onSubmit={vi.fn(async () => undefined)}
        onCancel={vi.fn()}
      />,
    )

    const toggle = screen.getByRole('switch', { name: /Toute la journée/i }) as HTMLInputElement
    expect(toggle.checked).toBe(false)
    expect(screen.getByTestId('startDate-time-selectors').className).toContain('opacity-100')

    fireEvent.click(toggle)
    expect(onFieldChange).toHaveBeenCalledWith('allDay', true)
  })

  it('hides the time selectors when allDay is true', () => {
    render(
      <EventForm
        mode="create"
        submitLabel='Créer'
        values={{ ...baseValues, allDay: true }}
        errors={{}}
        submitting={false}
        imagePreview={null}
        selectedImageName={null}
        onFieldChange={vi.fn()}
        onImageChange={vi.fn()}
        cropSource={null}
        cropAspect={16 / 9}
        onCropConfirm={vi.fn()}
        onCropCancel={vi.fn()}
        onSubmit={vi.fn(async () => undefined)}
        onCancel={vi.fn()}
      />,
    )

    fireEvent.change(screen.getByLabelText(/Début/i, { selector: 'input' }), { target: { value: '' } })

    expect((screen.getByRole('switch', { name: /Toute la journée/i }) as HTMLInputElement).checked).toBe(true)
    expect(screen.getByTestId('startDate-time-selectors').className).toContain('opacity-0')
    expect(screen.getByTestId('endDate-time-selectors').className).toContain('opacity-0')
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
          cropSource={null}
          cropAspect={16 / 9}
          onCropConfirm={vi.fn()}
          onCropCancel={vi.fn()}
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

  it('does not render ImageCropper when cropSource is null', () => {
    render(
      <EventForm
        mode="create"
        submitLabel="Créer"
        values={baseValues}
        errors={{}}
        submitting={false}
        imagePreview={null}
        selectedImageName={null}
        onFieldChange={vi.fn()}
        onImageChange={vi.fn()}
        cropSource={cropDefaults.cropSource}
        cropAspect={cropDefaults.cropAspect}
        onCropConfirm={vi.fn()}
        onCropCancel={vi.fn()}
        onSubmit={vi.fn(async () => undefined)}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.queryByRole('dialog', { name: /Recadrer/i })).toBeNull()
  })

  it('renders ImageCropper when cropSource is set', () => {
    render(
      <EventForm
        mode="create"
        submitLabel="Créer"
        values={baseValues}
        errors={{}}
        submitting={false}
        imagePreview={null}
        selectedImageName={null}
        onFieldChange={vi.fn()}
        onImageChange={vi.fn()}
        cropSource="data:image/png;base64,abc"
        cropAspect={cropDefaults.cropAspect}
        onCropConfirm={vi.fn()}
        onCropCancel={vi.fn()}
        onSubmit={vi.fn(async () => undefined)}
        onCancel={vi.fn()}
      />,
    )

    expect(screen.getByRole('dialog', { name: /Recadrer/i })).toBeTruthy()
  })

  describe('SCRUM-117 — extra optional fields', () => {
    it('renders websiteUrl, contactEmail, registrationDeadline and tags inputs', () => {
      render(
        <EventForm
          mode="create"
          submitLabel='Créer'
          values={baseValues}
          errors={{}}
          submitting={false}
          imagePreview={null}
          selectedImageName={null}
          onFieldChange={vi.fn()}
          onImageChange={vi.fn()}
          {...cropDefaults}
          onSubmit={vi.fn(async () => undefined)}
          onCancel={vi.fn()}
        />,
      )

      const websiteInput = screen.getByLabelText(/Site web/i) as HTMLInputElement
      expect(websiteInput.type).toBe('url')
      expect(websiteInput.maxLength).toBe(500)

      const emailInput = screen.getByLabelText(/Email de contact/i) as HTMLInputElement
      expect(emailInput.type).toBe('email')
      expect(emailInput.maxLength).toBe(255)

      expect(screen.getByLabelText(/Date limite d'inscription/i, { selector: 'input' })).toBeTruthy()
      expect(screen.getByTestId('registrationDeadline-time-selectors')).toBeTruthy()

      expect(screen.getByText(/0 \/ 20/)).toBeTruthy()
      expect(screen.getByPlaceholderText(/Ajoutez des mots-clés/i)).toBeTruthy()
    })

    it('forwards onFieldChange for websiteUrl, contactEmail, and registrationDeadline', () => {
      const onFieldChange = vi.fn()
      render(
        <EventForm
          mode="create"
          submitLabel='Créer'
          values={baseValues}
          errors={{}}
          submitting={false}
          imagePreview={null}
          selectedImageName={null}
          onFieldChange={onFieldChange}
          onImageChange={vi.fn()}
          {...cropDefaults}
          onSubmit={vi.fn(async () => undefined)}
          onCancel={vi.fn()}
        />,
      )

      fireEvent.change(screen.getByLabelText(/Site web/i), { target: { value: 'https://unige.ch' } })
      fireEvent.change(screen.getByLabelText(/Email de contact/i), { target: { value: 'contact@unige.ch' } })
      fireEvent.change(screen.getByLabelText(/Date limite d'inscription/i, { selector: 'input' }), {
        target: { value: '2099-04-09' },
      })

      expect(onFieldChange).toHaveBeenCalledWith('websiteUrl', 'https://unige.ch')
      expect(onFieldChange).toHaveBeenCalledWith('contactEmail', 'contact@unige.ch')
      expect(onFieldChange).toHaveBeenCalledWith('registrationDeadline', '2099-04-09T00:00')
    })

    it('displays validation error messages for the four extra fields', () => {
      render(
        <EventForm
          mode="create"
          submitLabel='Créer'
          values={baseValues}
          errors={{
            websiteUrl: "L'URL du site web est invalide.",
            contactEmail: "L'email de contact est invalide.",
            registrationDeadline: "La date limite d'inscription doit être antérieure à la date de début.",
            tags: 'Vous ne pouvez pas ajouter plus de 20 mots-clés.',
          }}
          submitting={false}
          imagePreview={null}
          selectedImageName={null}
          onFieldChange={vi.fn()}
          onImageChange={vi.fn()}
          {...cropDefaults}
          onSubmit={vi.fn(async () => undefined)}
          onCancel={vi.fn()}
        />,
      )

      expect(screen.getByText("L'URL du site web est invalide.")).toBeTruthy()
      expect(screen.getByText("L'email de contact est invalide.")).toBeTruthy()
      expect(screen.getByText("La date limite d'inscription doit être antérieure à la date de début.")).toBeTruthy()
      expect(screen.getByText('Vous ne pouvez pas ajouter plus de 20 mots-clés.')).toBeTruthy()
    })

    it('adds a tag through TagInput and forwards it via onFieldChange', () => {
      const onFieldChange = vi.fn()
      render(
        <EventForm
          mode="create"
          submitLabel='Créer'
          values={baseValues}
          errors={{}}
          submitting={false}
          imagePreview={null}
          selectedImageName={null}
          onFieldChange={onFieldChange}
          onImageChange={vi.fn()}
          {...cropDefaults}
          onSubmit={vi.fn(async () => undefined)}
          onCancel={vi.fn()}
        />,
      )

      const tagInput = screen.getByPlaceholderText(/Ajoutez des mots-clés/i) as HTMLInputElement
      fireEvent.change(tagInput, { target: { value: 'forum' } })
      fireEvent.keyDown(tagInput, { key: 'Enter' })

      expect(onFieldChange).toHaveBeenCalledWith('tags', ['forum'])
    })

    it('removes a tag via the TagInput chip button', () => {
      const onFieldChange = vi.fn()
      render(
        <EventForm
          mode="create"
          submitLabel='Créer'
          values={{ ...baseValues, tags: ['forum', 'carrière'] }}
          errors={{}}
          submitting={false}
          imagePreview={null}
          selectedImageName={null}
          onFieldChange={onFieldChange}
          onImageChange={vi.fn()}
          {...cropDefaults}
          onSubmit={vi.fn(async () => undefined)}
          onCancel={vi.fn()}
        />,
      )

      fireEvent.click(screen.getByRole('button', { name: /Remove tag forum/i }))

      expect(onFieldChange).toHaveBeenCalledWith('tags', ['carrière'])
    })

    it('keeps time selectors for registrationDeadline visible even when allDay is true', () => {
      render(
        <EventForm
          mode="create"
          submitLabel='Créer'
          values={{ ...baseValues, allDay: true }}
          errors={{}}
          submitting={false}
          imagePreview={null}
          selectedImageName={null}
          onFieldChange={vi.fn()}
          onImageChange={vi.fn()}
          {...cropDefaults}
          onSubmit={vi.fn(async () => undefined)}
          onCancel={vi.fn()}
        />,
      )

      expect(screen.getByTestId('startDate-time-selectors').className).toContain('opacity-0')
      expect(screen.getByTestId('registrationDeadline-time-selectors').className).toContain('opacity-100')
    })

    it('splits an existing registrationDeadline value into date + time selectors', () => {
      render(
        <EventForm
          mode="create"
          submitLabel='Créer'
          values={{ ...baseValues, registrationDeadline: '2099-04-09T18:30' }}
          errors={{}}
          submitting={false}
          imagePreview={null}
          selectedImageName={null}
          onFieldChange={vi.fn()}
          onImageChange={vi.fn()}
          {...cropDefaults}
          onSubmit={vi.fn(async () => undefined)}
          onCancel={vi.fn()}
        />,
      )

      expect((screen.getByLabelText(/Date limite d'inscription/i, { selector: 'input' }) as HTMLInputElement).value).toBe('2099-04-09')
      expect((screen.getByLabelText(/Heure de la date limite/i) as HTMLSelectElement).value).toBe('18')
      expect((screen.getByLabelText(/Minute de la date limite/i) as HTMLSelectElement).value).toBe('30')
    })
  })
})
