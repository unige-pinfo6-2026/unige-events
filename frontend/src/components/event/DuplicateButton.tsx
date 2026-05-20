import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Copy } from 'lucide-react'
import { duplicateEvent } from '@/services/eventApi'
import { useToast } from '@/hooks/useToast'

interface DuplicateButtonProps {
  eventId: number
}

export default function DuplicateButton({ eventId }: Readonly<DuplicateButtonProps>) {
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const toast = useToast()

  async function handleDuplicate() {
    setLoading(true)
    try {
      const clone = await duplicateEvent(eventId)
      toast.showToast('success', 'Événement dupliqué. Vous pouvez maintenant le modifier.')
      navigate(`/events/${clone.id}/edit`)
    } catch {
      setLoading(false)
      toast.showToast('error', 'Impossible de dupliquer cet événement.')
    }
  }

  return (
    <button
      type="button"
      onClick={handleDuplicate}
      disabled={loading}
      className="w-full inline-flex items-center justify-center gap-2 px-4 py-2.5 rounded-2xl border border-border text-foreground text-sm font-semibold cursor-pointer bg-transparent hover:border-foreground/30 transition-colors disabled:opacity-50"
    >
      <Copy className="w-4 h-4 shrink-0" />
      {loading ? 'Duplication…' : 'Dupliquer l\'événement'}
    </button>
  )
}
