import { useState } from 'react'
import { Send, X } from 'lucide-react'
import { Textarea } from '@/components/utils/FormField'
import { ButtonNeutral, ButtonPrimary } from '@/components/utils/Buttons'

const MAX_LENGTH = 2000

interface Props {
  onSubmit: (content: string) => Promise<{ ok: boolean }>
  submitting: boolean
  /** Optional placeholder; defaults to a generic prompt. */
  placeholder?: string
  /** Optional cancel callback — when present, an extra "Annuler" button is rendered. Used for reply forms. */
  onCancel?: () => void
  /** Auto-focus on mount — useful for inline reply forms. */
  autoFocus?: boolean
  /** Submit button label override. */
  submitLabel?: string
}

/**
 * Textarea + counter + Submit button. Used both for top-level posts and for
 * inline reply forms (rendered conditionally by `CommentItem`).
 *
 * Anonymous-user check lives in the parent (`CommentSection`) — this form is
 * always rendered for authenticated users only.
 */
export default function CommentForm({
  onSubmit,
  submitting,
  placeholder = 'Écrire un commentaire…',
  onCancel,
  autoFocus = false,
  submitLabel = 'Publier',
}: Readonly<Props>) {
  const [content, setContent] = useState('')

  async function handleSubmit() {
    const outcome = await onSubmit(content)
    if (outcome.ok) {
      setContent('')
    }
  }

  const trimmedLength = content.trim().length
  const remaining = MAX_LENGTH - content.length
  const overLimit = content.length > MAX_LENGTH
  const disabled = submitting || trimmedLength === 0 || overLimit

  return (
    <div className="space-y-2">
      <Textarea
        value={content}
        onChange={(e) => setContent(e.target.value)}
        placeholder={placeholder}
        rows={3}
        maxLength={MAX_LENGTH + 1}
        autoFocus={autoFocus}
        disabled={submitting}
        aria-label="Contenu du commentaire"
      />
      <div className="flex items-center justify-between gap-2">
        <span className={`text-xs ${overLimit ? 'text-error' : 'text-foreground/40'}`}>
          {remaining} caractère{Math.abs(remaining) === 1 ? '' : 's'} restant{Math.abs(remaining) === 1 ? '' : 's'}
        </span>
        <div className="flex gap-2">
          {onCancel && (
            <ButtonNeutral onClick={onCancel} size="sm">
              <X className="w-4 h-4" />
              Annuler
            </ButtonNeutral>
          )}
          <ButtonPrimary onClick={() => void handleSubmit()} disabled={disabled} size="sm">
            <Send className="w-4 h-4" />
            {submitLabel}
          </ButtonPrimary>
        </div>
      </div>
    </div>
  )
}
