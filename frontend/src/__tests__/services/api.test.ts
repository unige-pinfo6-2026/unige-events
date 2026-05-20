import { afterEach, describe, expect, it, vi } from 'vitest'
import api from '@/services/api'

const mockGlobalNavigate = vi.fn()
vi.mock('@/utils/navigation', () => ({
  globalNavigate: (...args: unknown[]) => mockGlobalNavigate(...args),
  setGlobalNavigate: vi.fn(),
}))

afterEach(() => {
  localStorage.clear()
  mockGlobalNavigate.mockReset()
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

describe('api response interceptor', () => {
  function makeErrorAdapter(status: number) {
    return async (config: unknown) => {
      const err = Object.assign(new Error('Request failed'), {
        isAxiosError: true,
        response: { status, data: null, headers: {}, config },
        config,
      })
      return Promise.reject(err)
    }
  }

  it('calls globalNavigate("/403") on a 403 response', async () => {
    await expect(
      api.get('/protected', { adapter: makeErrorAdapter(403) }),
    ).rejects.toBeDefined()
    expect(mockGlobalNavigate).toHaveBeenCalledWith('/403')
  })

  it('calls globalNavigate("/404") on a 404 response', async () => {
    await expect(
      api.get('/missing', { adapter: makeErrorAdapter(404) }),
    ).rejects.toBeDefined()
    expect(mockGlobalNavigate).toHaveBeenCalledWith('/404')
  })

  it('does not navigate on a 500 response', async () => {
    await expect(
      api.get('/broken', { adapter: makeErrorAdapter(500) }),
    ).rejects.toBeDefined()
    expect(mockGlobalNavigate).not.toHaveBeenCalled()
  })

  it('skips navigation when skipGlobalRedirect is set', async () => {
    await expect(
      api.get('/skip', { adapter: makeErrorAdapter(403), skipGlobalRedirect: true }),
    ).rejects.toBeDefined()
    expect(mockGlobalNavigate).not.toHaveBeenCalled()
  })
})
