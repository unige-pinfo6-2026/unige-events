import { afterEach, describe, it } from 'vitest'
import { cleanup, render } from '@testing-library/react'
import { Blobs, BlobsCta, BlobsHero, BlobsLanding, BlobsSubtle } from '@/components/utils/Blobs'

afterEach(cleanup)

describe('Blobs', () => {
  it('renders Blobs without throwing', () => {
    render(<div className="relative"><Blobs /></div>)
  })

  it('renders BlobsHero without throwing', () => {
    render(<div className="relative"><BlobsHero /></div>)
  })

  it('renders BlobsCta without throwing', () => {
    render(<div className="relative"><BlobsCta /></div>)
  })

  it('renders BlobsSubtle without throwing', () => {
    render(<div className="relative"><BlobsSubtle /></div>)
  })

  it('renders BlobsLanding without throwing', () => {
    render(<div className="relative overflow-hidden"><BlobsLanding /></div>)
  })
})
