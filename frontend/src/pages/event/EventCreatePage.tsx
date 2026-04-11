import { useNavigate } from 'react-router-dom'
import { useEventForm } from '@/hooks'
import { BANNER_UPLOAD_ERROR_KEY } from '@/constants/sessionStorageKeys'
import EventForm from '@/components/event/EventForm'
import { useToast } from '@/hooks/useToast'
import { SectionWrapper, SectionHeader } from '@/components/utils/Section'
import { BlobsSubtle } from '@/components/utils/Blobs'

export default function EventCreatePage() {
  const navigate = useNavigate()
  const { showToast } = useToast()

  const form = useEventForm({
    mode: 'create',
    onSuccess: (event) => {
      showToast('success', 'Événement créé avec succès.')
      navigate(`/events/${event.id}`)
    },
    onError: (message) => showToast('error', message),
    onBannerError: (message) => sessionStorage.setItem(BANNER_UPLOAD_ERROR_KEY, message),
  })

  return (
    <SectionWrapper padding="sm" size="md" background={<BlobsSubtle />}>
      <SectionHeader
        align="left"
        heading="lg"
        title={<>Créer un <mark>événement</mark></>}
        subtitle="Renseignez les informations de votre événement pour le partager avec la communauté UNIGE."
      />
      <EventForm
        submitLabel="Créer l'événement"
        values={form.values}
        errors={form.errors}
        submitting={form.submitting}
        imagePreview={form.imagePreview}
        selectedImageName={form.selectedImageName}
        onFieldChange={form.setFieldValue}
        onImageChange={form.handleImageChange}
        onSubmit={form.handleSubmit}
        onCancel={() => navigate('/')}
      />
    </SectionWrapper>
  )
}
