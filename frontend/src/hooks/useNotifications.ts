import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { getNotifications, markAllRead } from '@/services/notificationApi'
import type { Notification } from '@/types/notification'

const POLL_INTERVAL = 30_000

interface UseNotificationsResult {
  notifications: Notification[]
  unreadCount: number
  loading: boolean
  error: string | null
  markAllAsRead: () => void
}

export function useNotifications(): UseNotificationsResult {
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => { mountedRef.current = false }
  }, [])

  const fetchNotifications = useCallback(async (silent = false) => {
    if (!silent && mountedRef.current) setLoading(true)
    try {
      const data = await getNotifications()
      if (!mountedRef.current) return
      setNotifications(data)
      setError(null)
    } catch (err) {
      if (!mountedRef.current) return
      // Silently ignore 401 — user not authenticated
      if (!axios.isAxiosError(err) || err.response?.status !== 401) {
        setError('Impossible de charger les notifications.')
      }
    } finally {
      if (mountedRef.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    void fetchNotifications()
    const interval = setInterval(() => void fetchNotifications(true), POLL_INTERVAL)
    return () => clearInterval(interval)
  }, [fetchNotifications])

  const markAllAsRead = useCallback(() => {
    // Optimistic update — badge clears immediately
    setNotifications(prev => prev.map(n => ({ ...n, read: true })))
    void markAllRead().catch(() => {
      // Revert on API failure by re-fetching
      void fetchNotifications(true)
    })
  }, [fetchNotifications])

  return {
    notifications,
    unreadCount: notifications.filter(n => !n.read).length,
    loading,
    error,
    markAllAsRead,
  }
}
