import axios from 'axios'
import { getToken } from './tokenStore'
import { globalNavigate } from '@/utils/navigation'

declare module 'axios' {
  interface AxiosRequestConfig {
    skipGlobalRedirect?: boolean
  }
  interface InternalAxiosRequestConfig {
    skipGlobalRedirect?: boolean
  }
}

const api = axios.create({
  baseURL: '/api',
})

api.interceptors.request.use((config) => {
  const token = getToken()

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

api.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error) && !error.config?.skipGlobalRedirect) {
      const status = error.response?.status
      if (status === 403) globalNavigate('/403')
      else if (status === 404) globalNavigate('/404')
    }
    return Promise.reject(error)
  },
)

export default api
