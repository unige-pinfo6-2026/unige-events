// @vitest-environment jsdom

import { describe, expect, it } from 'vitest'
import { render } from '@testing-library/react'
import { useAuth } from '../../hooks/useAuth'

function UseAuthOutsideProvider() {
  useAuth()
  return null
}

describe('useAuth', () => {
  it('throws when used outside AuthProvider', () => {
    expect(() => render(<UseAuthOutsideProvider />)).toThrow(
      "useAuth doit être utilisé à l'intérieur de AuthProvider",
    )
  })
})
