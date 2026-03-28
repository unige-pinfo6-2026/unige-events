// @vitest-environment jsdom

import { afterEach, describe, expect, it } from 'vitest'
import api from '../../services/api'

afterEach(() => {
  localStorage.clear()
})

describe('api client', () => {
  it('uses the Vite proxy base path', () => {
    expect(api.defaults.baseURL).toBe('/api')
  })

  it('includes Authorization header when access_token exists', async () => {
    localStorage.setItem('access_token', 'test-token')

    const response = await api.get('/events', {
      adapter: async (config) => ({
        config,
        data: null,
        headers: {},
        status: 200,
        statusText: 'OK',
      }),
    })

    expect(response.config.headers.Authorization).toBe('Bearer test-token')
  })

  it('does not include Authorization header when access_token is missing', async () => {
    const response = await api.get('/events', {
      adapter: async (config) => ({
        config,
        data: null,
        headers: {},
        status: 200,
        statusText: 'OK',
      }),
    })

    expect(response.config.headers.Authorization).toBeUndefined()
  })
})
