// @vitest-environment node

import { describe, expect, it } from 'vitest'
import { AUTH_SESSION_EXPIRED_CODES, isAuthSessionExpiredError } from '@/utils/authErrors'

describe('isAuthSessionExpiredError', () => {
  it.each(AUTH_SESSION_EXPIRED_CODES.map(c => [c]))(
    'returns true for the Auth0 SDK %s error',
    (code) => {
      expect(isAuthSessionExpiredError({ error: code, error_description: 'whatever' })).toBe(true)
    },
  )

  it('matches when the SDK throws an Error-like object with the `error` field tacked on', () => {
    // auth0-spa-js extends Error and adds the `error` field on the prototype.
    class OAuthError extends Error {
      readonly error: string
      constructor(error: string, message?: string) {
        super(message)
        this.error = error
      }
    }
    const e = new OAuthError('login_required', 'No session')
    expect(isAuthSessionExpiredError(e)).toBe(true)
  })

  it('returns false for HTTP-shaped errors from axios (e.g. backend 500)', () => {
    expect(isAuthSessionExpiredError({ response: { status: 500 } })).toBe(false)
  })

  it('returns false for HTTP 401 from the backend (the AuthContext handles it separately)', () => {
    expect(isAuthSessionExpiredError({ response: { status: 401 } })).toBe(false)
  })

  it('returns false for a network error without an `error` field', () => {
    expect(isAuthSessionExpiredError(new Error('Network Error'))).toBe(false)
  })

  it('returns false for null / undefined / primitives', () => {
    expect(isAuthSessionExpiredError(null)).toBe(false)
    expect(isAuthSessionExpiredError(undefined)).toBe(false)
    expect(isAuthSessionExpiredError('login_required')).toBe(false)
    expect(isAuthSessionExpiredError(42)).toBe(false)
    expect(isAuthSessionExpiredError(true)).toBe(false)
  })

  it('returns false when `error` is present but not a string', () => {
    expect(isAuthSessionExpiredError({ error: 42 })).toBe(false)
    expect(isAuthSessionExpiredError({ error: { code: 'login_required' } })).toBe(false)
  })

  it('returns false for an unknown OAuth error code', () => {
    expect(isAuthSessionExpiredError({ error: 'server_error' })).toBe(false)
    expect(isAuthSessionExpiredError({ error: 'access_denied' })).toBe(false)
  })
})
