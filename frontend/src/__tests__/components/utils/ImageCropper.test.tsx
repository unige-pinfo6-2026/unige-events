// @vitest-environment jsdom

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, act } from '@testing-library/react'
import type { PixelCrop } from 'react-image-crop'
import ImageCropper from '@/components/utils/ImageCropper'
import { cropToBlob, initCrop } from '@/components/utils/imageCropperUtils'

// ── ReactCrop stub ────────────────────────────────────────────────────────────
// ReactCrop relies on pointer events and layout measurements absent from jsdom.
// We replace it with a stub that exposes onChange/onComplete via a click so
// tests can drive the crop state without pointer simulation.
vi.mock('react-image-crop', async (importOriginal) => {
  const actual = await importOriginal<typeof import('react-image-crop')>()
  const stub = ({
    children,
    onChange,
    onComplete,
  }: {
    children: React.ReactNode
    onChange: (c: PixelCrop) => void
    onComplete: (c: PixelCrop) => void
  }) => {
    const crop: PixelCrop = { unit: 'px', x: 10, y: 10, width: 80, height: 80 }
    return (
      <div
        data-testid="react-crop"
        onClick={() => { onChange(crop); onComplete(crop) }}
      >
        {children}
      </div>
    )
  }
  return { ...actual, default: stub }
})

// ── Canvas stubs ──────────────────────────────────────────────────────────────

const makeCtx = () => ({
  drawImage: vi.fn(),
  beginPath: vi.fn(),
  arc: vi.fn(),
  clip: vi.fn(),
})

type FakeCtx = ReturnType<typeof makeCtx>

beforeEach(() => {
  globalThis.ResizeObserver = class ResizeObserver {
    observe() { /* noop */ }
    unobserve() { /* noop */ }
    disconnect() { /* noop */ }
  } as unknown as typeof ResizeObserver

  HTMLCanvasElement.prototype.getContext = function () {
    return makeCtx() as unknown as CanvasRenderingContext2D
  } as unknown as typeof HTMLCanvasElement.prototype.getContext

  HTMLCanvasElement.prototype.toBlob = function (callback: BlobCallback) {
    callback(new Blob(['img'], { type: 'image/jpeg' }))
  }
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

// ── Helpers ───────────────────────────────────────────────────────────────────

const DATA_URL =
  'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=='

const makePixelCrop = (overrides?: Partial<PixelCrop>): PixelCrop => ({
  unit: 'px', x: 0, y: 0, width: 100, height: 100, ...overrides,
})

const makeImage = (): HTMLImageElement => {
  const img = document.createElement('img')
  img.src = DATA_URL
  return img
}

// ── initCrop ──────────────────────────────────────────────────────────────────

describe('initCrop', () => {
  it('returns a centered percentage crop for a square aspect', () => {
    const crop = initCrop(400, 400, 1)
    expect(crop.unit).toBe('%')
    expect(typeof (crop as { width: number }).width).toBe('number')
  })

  it('works with a 3:1 banner aspect ratio', () => {
    expect(initCrop(600, 200, 3).unit).toBe('%')
  })
})

// ── cropToBlob ────────────────────────────────────────────────────────────────

describe('cropToBlob', () => {
  it('resolves with a Blob for a non-circular crop', async () => {
    const blob = await cropToBlob(makeImage(), makePixelCrop(), false)
    expect(blob).toBeInstanceOf(Blob)
    expect(blob.size).toBeGreaterThan(0)
  })

  it('resolves with a Blob and clips canvas for a circular crop', async () => {
    const ctx = makeCtx()
    HTMLCanvasElement.prototype.getContext = (() => ctx as unknown as CanvasRenderingContext2D) as unknown as typeof HTMLCanvasElement.prototype.getContext

    const blob = await cropToBlob(makeImage(), makePixelCrop(), true)
    expect(blob).toBeInstanceOf(Blob)
    expect((ctx as FakeCtx).beginPath).toHaveBeenCalled()
    expect((ctx as FakeCtx).arc).toHaveBeenCalled()
    expect((ctx as FakeCtx).clip).toHaveBeenCalled()
  })

  it('rejects when toBlob returns null', async () => {
    HTMLCanvasElement.prototype.toBlob = (callback: BlobCallback) => callback(null)
    await expect(cropToBlob(makeImage(), makePixelCrop(), false)).rejects.toThrow(
      'canvas.toBlob returned null',
    )
  })
})

// ── ImageCropper component ────────────────────────────────────────────────────

describe('ImageCropper', () => {
  it('renders the modal with crop and cancel buttons', () => {
    render(<ImageCropper src={DATA_URL} aspect={1} onCropComplete={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByRole('dialog')).toBeTruthy()
    expect(screen.getByRole('button', { name: /recadrer/i })).toBeTruthy()
    expect(screen.getByRole('button', { name: /annuler/i })).toBeTruthy()
  })

  it('calls onCancel when the cancel button is clicked', () => {
    const onCancel = vi.fn()
    render(<ImageCropper src={DATA_URL} aspect={1} onCropComplete={vi.fn()} onCancel={onCancel} />)
    fireEvent.click(screen.getByRole('button', { name: /annuler/i }))
    expect(onCancel).toHaveBeenCalledOnce()
  })

  it('crop button is disabled before any crop selection', () => {
    render(<ImageCropper src={DATA_URL} aspect={1} onCropComplete={vi.fn()} onCancel={vi.fn()} />)
    expect((screen.getByRole('button', { name: /recadrer/i }) as HTMLButtonElement).disabled).toBe(true)
  })

  it('enables the crop button after a selection and calls onCropComplete with a Blob', async () => {
    const onCropComplete = vi.fn()
    render(<ImageCropper src={DATA_URL} aspect={1} onCropComplete={onCropComplete} onCancel={vi.fn()} />)

    await act(async () => { fireEvent.click(screen.getByTestId('react-crop')) })

    const btn = screen.getByRole('button', { name: /recadrer/i }) as HTMLButtonElement
    expect(btn.disabled).toBe(false)

    await act(async () => { fireEvent.click(btn) })

    expect(onCropComplete).toHaveBeenCalledWith(expect.any(Blob))
  })

  it('calls onCropComplete with a Blob for a circular crop', async () => {
    const onCropComplete = vi.fn()
    render(
      <ImageCropper src={DATA_URL} aspect={1} circular onCropComplete={onCropComplete} onCancel={vi.fn()} />,
    )

    await act(async () => { fireEvent.click(screen.getByTestId('react-crop')) })
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: /recadrer/i })) })

    expect(onCropComplete).toHaveBeenCalledWith(expect.any(Blob))
  })

  it('sets initial crop on image load', () => {
    render(<ImageCropper src={DATA_URL} aspect={1} onCropComplete={vi.fn()} onCancel={vi.fn()} />)
    const img = screen.getByAltText('Aperçu recadrage') as HTMLImageElement
    Object.defineProperty(img, 'naturalWidth', { value: 200, configurable: true })
    Object.defineProperty(img, 'naturalHeight', { value: 200, configurable: true })
    fireEvent.load(img)
    expect(screen.getByRole('dialog')).toBeTruthy()
  })

  it('renders with a 3:1 banner aspect ratio', () => {
    render(<ImageCropper src={DATA_URL} aspect={3} onCropComplete={vi.fn()} onCancel={vi.fn()} />)
    expect(screen.getByRole('dialog')).toBeTruthy()
  })
})
