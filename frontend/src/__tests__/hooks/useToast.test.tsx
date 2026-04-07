// @vitest-environment jsdom

import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { useToast } from '@/hooks/useToast'

function UseToastOutsideProvider() {
  useToast()
  return null
}

describe('useToast', () => {
  it('throws when used outside ToastProvider', () => {
    expect(() => render(<UseToastOutsideProvider />)).toThrow(
      "useToast doit être utilisé à l'intérieur de ToastProvider",
    )
  })
})
