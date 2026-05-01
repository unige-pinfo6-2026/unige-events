import { useCallback, useRef, useState, type ChangeEvent } from 'react'

export interface UseImageCropFlowOptions {
  aspect: number
  circular?: boolean
  validate?: (file: File) => string | null
  onValidationError?: (message: string) => void
}

export interface UseImageCropFlowResult {
  cropSource: string | null
  handleFileSelect: (event: ChangeEvent<HTMLInputElement>) => void
  aspect: number
  circular: boolean
  confirmCrop: (blob: Blob) => File | null
  cancelCrop: () => void
}

export function useImageCropFlow({
  aspect,
  circular = false,
  validate,
  onValidationError,
}: UseImageCropFlowOptions): UseImageCropFlowResult {
  const [cropSource, setCropSource] = useState<string | null>(null)
  const pendingFileNameRef = useRef<string | null>(null)
  const inputRef = useRef<HTMLInputElement | null>(null)

  const cleanup = useCallback(() => {
    setCropSource(null)
    pendingFileNameRef.current = null
    if (inputRef.current) inputRef.current.value = ''
    inputRef.current = null
  }, [])

  const handleFileSelect = useCallback((event: ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0]
    if (!file) return

    if (validate) {
      const error = validate(file)
      if (error !== null) {
        onValidationError?.(error)
        event.target.value = ''
        return
      }
    }

    pendingFileNameRef.current = file.name
    inputRef.current = event.target

    const reader = new FileReader()
    reader.onload = () => {
      const result = reader.result
      if (typeof result === 'string') setCropSource(result)
    }
    reader.readAsDataURL(file)
  }, [validate, onValidationError])

  const confirmCrop = useCallback((blob: Blob): File | null => {
    const name = pendingFileNameRef.current
    if (!name) {
      cleanup()
      return null
    }
    const file = new File([blob], name, { type: blob.type || 'image/png' })
    cleanup()
    return file
  }, [cleanup])

  const cancelCrop = useCallback(() => {
    cleanup()
  }, [cleanup])

  return { cropSource, handleFileSelect, aspect, circular, confirmCrop, cancelCrop }
}
