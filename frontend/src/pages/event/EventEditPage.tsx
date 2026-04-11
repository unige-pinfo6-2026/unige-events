import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useEventForm } from '@/hooks'
import EventForm from '@/components/event/EventForm'
import { getById } from '@/services/eventApi'
import type { Event } from '@/types/event'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'
import { LoadingSpinner } from '@/components/utils/LoadingSpinner'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { useToast } from '@/hooks/useToast'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'

export default function EventEditPage() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { id } = useParams<{ id: string }>()
  const parsedId = id === undefined ? Number.NaN : Number(id)
  const eventId = Number.isInteger(parsedId) && parsedId > 0 ? parsedId : null

  const [event, setEvent] = useState<Event | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (eventId === null) { setLoading(false); return }

    let cancelled = false

    async function loadEvent() {
      setLoading(true)
      setError(null)
      try {
        const response = await getById(eventId!)
        if (!cancelled) setEvent(response)
      } catch {
        if (!cancelled) setError('Impossible de charger cet événement.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void loadEvent()

    return () => {
      cancelled = true
    }
  }, [eventId])

  const form = useEventForm({
    mode: 'edit',
    initialEvent: event,
    onSuccess: (updatedEvent) => {
      setEvent(updatedEvent)
      showToast('success', 'Événement mis à jour avec succès.')
      navigate(`/events/${updatedEvent.id}`)
    },
    onError: (message) => showToast('error', message),
    onBannerError: (message) => sessionStorage.setItem(BANNER_UPLOAD_ERROR_KEY, message),
  })

  if (eventId === null) return <InfoMessage type="error" message="Identifiant d'événement invalide." />
  if (loading) return <LoadingSpinner />
  if (error) return <InfoMessage type="error" message={error} />
  if (!event) return <InfoMessage type="error" message="Événement introuvable." />

  return (
    <SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        heading="lg"
        title={<>Modifier <mark>l'événement</mark></>}
        subtitle="Mettez à jour les informations et republiez pour informer les participants."
      />
      <EventForm
        submitLabel="Enregistrer"
        values={form.values}
        errors={form.errors}
        submitting={form.submitting}
        imagePreview={form.imagePreview}
        selectedImageName={form.selectedImageName}
        onFieldChange={form.setFieldValue}
        onImageChange={form.handleImageChange}
        onSubmit={form.handleSubmit}
        onCancel={() => navigate(`/events/${event.id}`)}
      />
    </SectionWrapper>
  )
}
