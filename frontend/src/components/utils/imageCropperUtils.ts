import { centerCrop, makeAspectCrop, type Crop, type PixelCrop } from 'react-image-crop'

export function initCrop(width: number, height: number, aspect: number): Crop {
  return centerCrop(
    makeAspectCrop({ unit: '%', width: 90 }, aspect, width, height),
    width,
    height,
  )
}

export async function cropToBlob(
  image: HTMLImageElement,
  pixelCrop: PixelCrop,
  circular: boolean,
): Promise<Blob> {
  const canvas = document.createElement('canvas')
  canvas.width = pixelCrop.width
  canvas.height = pixelCrop.height
  const ctx = canvas.getContext('2d')!

  if (circular) {
    ctx.beginPath()
    ctx.arc(pixelCrop.width / 2, pixelCrop.height / 2, Math.min(pixelCrop.width, pixelCrop.height) / 2, 0, Math.PI * 2)
    ctx.clip()
  }

  ctx.drawImage(
    image,
    pixelCrop.x,
    pixelCrop.y,
    pixelCrop.width,
    pixelCrop.height,
    0,
    0,
    pixelCrop.width,
    pixelCrop.height,
  )

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => blob ? resolve(blob) : reject(new Error('canvas.toBlob returned null')),
      'image/jpeg',
      0.9,
    )
  })
}
