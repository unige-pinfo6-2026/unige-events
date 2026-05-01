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
  // react-image-crop renvoie les coordonnées dans les pixels *affichés* de l'image,
  // mais drawImage interprète le rectangle source dans les pixels *naturels*.
  // Sans ce ratio, on extrait un fragment minuscule coincé dans le coin haut-gauche.
  const scaleX = image.naturalWidth / image.width
  const scaleY = image.naturalHeight / image.height
  const sx = pixelCrop.x * scaleX
  const sy = pixelCrop.y * scaleY
  const sw = pixelCrop.width * scaleX
  const sh = pixelCrop.height * scaleY

  const canvas = document.createElement('canvas')
  canvas.width = sw
  canvas.height = sh
  const ctx = canvas.getContext('2d')!

  if (circular) {
    ctx.beginPath()
    ctx.arc(sw / 2, sh / 2, Math.min(sw, sh) / 2, 0, Math.PI * 2)
    ctx.clip()
  }

  ctx.drawImage(image, sx, sy, sw, sh, 0, 0, sw, sh)

  return new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (blob) => blob ? resolve(blob) : reject(new Error('canvas.toBlob returned null')),
      'image/jpeg',
      0.9,
    )
  })
}
