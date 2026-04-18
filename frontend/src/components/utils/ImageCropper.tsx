import { useCallback, useRef, useState } from 'react'
import ReactCrop, { type Crop, type PixelCrop } from 'react-image-crop'
import 'react-image-crop/dist/ReactCrop.css'
import { ButtonPrimary, ButtonSecondary } from '@/components/utils/Buttons'
import { initCrop, cropToBlob } from '@/components/utils/imageCropperUtils'

interface ImageCropperProps {
  src: string
  aspect: number
  circular?: boolean
  onCropComplete: (blob: Blob) => void
  onCancel: () => void
}

export default function ImageCropper({ src, aspect, circular = false, onCropComplete, onCancel }: Readonly<ImageCropperProps>) {
  const imgRef = useRef<HTMLImageElement>(null)
  const [crop, setCrop] = useState<Crop>()
  const [completedCrop, setCompletedCrop] = useState<PixelCrop>()

  const onImageLoad = useCallback((e: React.SyntheticEvent<HTMLImageElement>) => {
    const { naturalWidth, naturalHeight } = e.currentTarget
    setCrop(initCrop(naturalWidth, naturalHeight, aspect))
  }, [aspect])

  const handleConfirm = useCallback(async () => {
    if (!imgRef.current || !completedCrop) return
    const blob = await cropToBlob(imgRef.current, completedCrop, circular)
    onCropComplete(blob)
  }, [completedCrop, circular, onCropComplete])

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/70 backdrop-blur-sm"
      role="dialog"
      aria-modal="true"
      aria-label="Recadrer l'image"
    >
      <div className="bg-background rounded-2xl border border-border shadow-2xl p-6 flex flex-col gap-6 max-w-xl w-full mx-4">
        <h2 className="text-lg font-semibold text-foreground">Recadrer l'image</h2>

        <div className="flex justify-center overflow-hidden rounded-xl">
          <ReactCrop
            crop={crop}
            onChange={(c) => setCrop(c)}
            onComplete={(c) => setCompletedCrop(c)}
            aspect={aspect}
            keepSelection
            circularCrop={circular}
          >
            <img
              ref={imgRef}
              src={src}
              alt="Aperçu recadrage"
              onLoad={onImageLoad}
              className="max-h-[60vh] max-w-full object-contain"
            />
          </ReactCrop>
        </div>

        <div className="flex justify-end gap-3">
          <ButtonSecondary size="sm" onClick={onCancel}>Annuler</ButtonSecondary>
          <ButtonPrimary size="sm" onClick={() => { void handleConfirm() }} disabled={!completedCrop}>
            Recadrer
          </ButtonPrimary>
        </div>
      </div>
    </div>
  )
}
