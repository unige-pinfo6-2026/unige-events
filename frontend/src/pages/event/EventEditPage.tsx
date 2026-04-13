import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useEventForm } from '@/hooks'
import EventForm from '@/components/event/EventForm'
import { getById } from '@/services/eventApi'
import type { Event } from '@/types/event'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'
import { InfoMessage } from '@/components/utils/InfoMessage'
import { useToast } from '@/hooks/useToast'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'
import { Skeleton } from 'boneyard-js/react'
import { useTheme } from '@/contexts/ThemeContext'

function EventFormFixture() {
  return (
    <div className="flex flex-col gap-8">
      {/* Band 1: Banner (left 2fr) | Title + Description (right 3fr) */}
      <div className="grid grid-cols-[2fr_3fr] gap-6 max-lg:grid-cols-1">
        <div className="pt-7 max-lg:pt-0">
          <div className="h-52 rounded-2xl" />
        </div>
        <div className="flex flex-col gap-4">
          <div className="h-[92px] rounded-xl" />
          <div className="h-[192px] rounded-xl" />
        </div>
      </div>
      {/* Band 2: Lieu | Début | Fin */}
      <div className="grid grid-cols-[2fr_1fr_1fr] gap-4 max-sm:grid-cols-1">
        <div className="h-[72px]" />
        <div className="h-[72px]" />
        <div className="h-[72px]" />
      </div>
      {/* Band 3: Catégorie | Capacité | CTA */}
      <div className="flex flex-wrap items-end gap-x-6 gap-y-4">
        <div className="w-48 h-[72px]" />
        <div className="w-24 h-[72px]" />
        <div className="ml-auto max-sm:ml-0 max-sm:w-full w-[140px] h-[68px]" />
      </div>
      {/* Band 4: ComingSoon shells */}
      <div className="flex flex-col gap-3">
        <div className="grid grid-cols-2 gap-3 max-sm:grid-cols-1">
          <div className="h-[92px] rounded-2xl" />
          <div className="h-[92px] rounded-2xl" />
        </div>
        <div className="h-[92px] rounded-2xl" />
        <div className="h-[88px] rounded-2xl" />
        <div className="border-t border-border/20" />
        <div className="h-[124px] rounded-2xl" />
      </div>
      {/* Band 5: Co-organisateurs (edit only) */}
      <div className="flex flex-col gap-3 border-t border-border/30 pt-6">
        <div className="h-5" />
        <div className="h-10 max-w-sm rounded-xl" />
        <div className="h-8" />
      </div>
    </div>
  )
}

export default function EventEditPage() {
  const navigate = useNavigate()
  const { showToast } = useToast()
  const { id } = useParams<{ id: string }>()
  const { theme } = useTheme()
  const skeletonColor = theme === 'dark' ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.08)'
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

  if (loading) return (
    <SectionWrapper padding="sm" size="lg" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        heading="lg"
        title={<>Modifier <mark>l'événement</mark></>}
        subtitle="Mettez à jour les informations et republiez pour informer les participants."
      />
      <Skeleton
        name="event-edit"
        loading={true}
        animate="pulse"
        color={skeletonColor}
      ><EventFormFixture /></Skeleton>
    </SectionWrapper>
  )

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
        mode="edit"
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
