import type { ChangeEvent } from 'react'
import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useImageCropFlow } from '@/hooks/useImageCropFlow'

let originalFileReader: typeof FileReader

beforeEach(() => {
  originalFileReader = globalThis.FileReader
})

afterEach(() => {
  globalThis.FileReader = originalFileReader
  vi.resetAllMocks()
})

function mockFileReader(result: string) {
  class MockReader {
    public onload: (() => void) | null = null
    public result: string | null = null
    readAsDataURL() {
      this.result = result
      queueMicrotask(() => this.onload?.())
    }
  }
  globalThis.FileReader = MockReader as unknown as typeof FileReader
}

function fileSelectEvent(file: File): ChangeEvent<HTMLInputElement> {
  const input = document.createElement('input')
  Object.defineProperty(input, 'files', { value: [file], configurable: true })
  return { target: input, currentTarget: input } as unknown as ChangeEvent<HTMLInputElement>
}

describe('useImageCropFlow', () => {
  it('initialise avec cropSource null', () => {
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    expect(result.current.cropSource).toBeNull()
    expect(result.current.aspect).toBe(1)
    expect(result.current.circular).toBe(false)
  })

  it('expose le mode circulaire quand fourni', () => {
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1, circular: true }))
    expect(result.current.circular).toBe(true)
  })

  it('ouvre le cropper avec la dataURL après sélection d\'un fichier valide', async () => {
    mockFileReader('data:image/png;base64,abc')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    const file = new File(['x'], 'photo.png', { type: 'image/png' })

    await act(async () => {
      result.current.handleFileSelect(fileSelectEvent(file))
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    expect(result.current.cropSource).toBe('data:image/png;base64,abc')
  })

  it('rejette le fichier si validate retourne un message + appelle onValidationError', () => {
    const onValidationError = vi.fn()
    const validate = vi.fn(() => 'Trop gros.')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1, validate, onValidationError }))
    const file = new File(['x'], 'big.png', { type: 'image/png' })

    act(() => {
      result.current.handleFileSelect(fileSelectEvent(file))
    })

    expect(validate).toHaveBeenCalledWith(file)
    expect(onValidationError).toHaveBeenCalledWith('Trop gros.')
    expect(result.current.cropSource).toBeNull()
  })

  it('ne fait rien si aucun fichier n\'est sélectionné', () => {
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    const event = { target: { files: [] } } as unknown as ChangeEvent<HTMLInputElement>

    act(() => {
      result.current.handleFileSelect(event)
    })

    expect(result.current.cropSource).toBeNull()
  })

  it('confirmCrop retourne un File avec le nom original et le type du blob', async () => {
    mockFileReader('data:image/png;base64,abc')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    const file = new File(['x'], 'photo-original.jpg', { type: 'image/jpeg' })

    await act(async () => {
      result.current.handleFileSelect(fileSelectEvent(file))
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    let resultFile: File | null = null
    act(() => {
      const blob = new Blob(['cropped'], { type: 'image/jpeg' })
      resultFile = result.current.confirmCrop(blob)
    })

    expect(resultFile).toBeInstanceOf(File)
    expect(resultFile!.name).toBe('photo-original.jpg')
    expect(resultFile!.type).toBe('image/jpeg')
    expect(result.current.cropSource).toBeNull()
  })

  it('confirmCrop retourne null si appelé sans fichier en attente', () => {
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    let resultFile: File | null = null
    act(() => {
      resultFile = result.current.confirmCrop(new Blob([]))
    })
    expect(resultFile).toBeNull()
  })

  it('confirmCrop fallback sur image/png si le blob.type est vide', async () => {
    mockFileReader('data:image/png;base64,abc')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    const file = new File(['x'], 'photo.png', { type: 'image/png' })

    await act(async () => {
      result.current.handleFileSelect(fileSelectEvent(file))
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    let resultFile: File | null = null
    act(() => {
      const blob = new Blob(['x'], { type: '' })
      resultFile = result.current.confirmCrop(blob)
    })

    expect(resultFile!.type).toBe('image/png')
  })

  it('cancelCrop ferme le cropper et reset l\'input', async () => {
    mockFileReader('data:image/png;base64,abc')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1 }))
    const file = new File(['x'], 'photo.png', { type: 'image/png' })
    const event = fileSelectEvent(file)

    await act(async () => {
      result.current.handleFileSelect(event)
      await new Promise((r) => queueMicrotask(r as () => void))
    })

    act(() => {
      result.current.cancelCrop()
    })

    expect(result.current.cropSource).toBeNull()
    expect((event.target as HTMLInputElement).value).toBe('')
  })

  it('reset l\'input après échec de validation pour permettre la re-sélection', () => {
    const validate = vi.fn(() => 'Invalide.')
    const { result } = renderHook(() => useImageCropFlow({ aspect: 1, validate }))
    const file = new File(['x'], 'doc.pdf', { type: 'application/pdf' })
    const event = fileSelectEvent(file)

    act(() => {
      result.current.handleFileSelect(event)
    })

    expect((event.target as HTMLInputElement).value).toBe('')
  })
})
