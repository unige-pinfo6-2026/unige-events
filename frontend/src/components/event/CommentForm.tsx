import { useEffect, useRef, useState } from 'react'
import { Send, X } from 'lucide-react'
import { Textarea } from '@/components/utils/FormField'
import { ButtonNeutral, ButtonPrimary } from '@/components/utils/Buttons'
import MentionAutocomplete from '@/components/event/MentionAutocomplete'

const MAX_LENGTH = 500

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
  /** SCRUM-147 — initial value of the textarea. Used by replies to pre-fill
   *  `@<parentAuthorUsername> `. Only consumed on mount ; subsequent prop
   *  changes are ignored so the user's typing isn't clobbered. */
  initialValue?: string
}

/**
 * Textarea + counter + Submit button. Used both for top-level posts and for
 * inline reply forms (rendered conditionally by `CommentItem`).
 *
 * Anonymous-user check lives in the parent (`CommentSection`) — this form is
 * always rendered for authenticated users only.
 *
 * <p>SCRUM-147 — the textarea now hosts an inline {@link MentionAutocomplete}
 * that triggers on {@code @<prefix>} (≥ 2 chars, 300ms debounce). The
 * autocomplete is a side-panel listening to the textarea ref ; the form keeps
 * full control of the content state.
 */
export default function CommentForm({
  onSubmit,
  submitting,
  placeholder = 'Écrire un commentaire…',
  onCancel,
  autoFocus = false,
  submitLabel = 'Publier',
  initialValue,
}: Readonly<Props>) {
  const [content, setContent] = useState(initialValue ?? '')
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)

  // Apply autoFocus + place the caret at the END of the initial value when
  // mounting (replies open with @<author> pre-filled — the user should be
  // able to start typing immediately).
  useEffect(() => {
    if (!autoFocus) return
    const t = textareaRef.current
    if (!t) return
    t.focus()
    const len = t.value.length
    t.setSelectionRange(len, len)
    // mount-only
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  async function handleSubmit() {
    const outcome = await onSubmit(content)
    if (outcome.ok) {
      setContent('')
    }
  }

  function handleAutocompleteChange(newValue: string, newCaretPos: number) {
    setContent(newValue)
    // The autocomplete already calls setSelectionRange via rAF ; nothing
    // more to do here. Keep newCaretPos in the signature for future
    // extensibility (e.g. emitting analytics).
    void newCaretPos
  }

  const trimmedLength = content.trim().length
  const remaining = MAX_LENGTH - content.length
  const overLimit = content.length > MAX_LENGTH
  const disabled = submitting || trimmedLength === 0 || overLimit

  return (
    <div className="space-y-2">
      <div className="relative">
        <Textarea
          ref={textareaRef}
          value={content}
          onChange={(e) => setContent(e.target.value)}
          placeholder={placeholder}
          rows={3}
          maxLength={MAX_LENGTH + 1}
          disabled={submitting}
          aria-label="Contenu du commentaire"
        />
        <MentionAutocomplete
          value={content}
          onChange={handleAutocompleteChange}
          textareaRef={textareaRef}
          disabled={submitting}
        />
      </div>
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
