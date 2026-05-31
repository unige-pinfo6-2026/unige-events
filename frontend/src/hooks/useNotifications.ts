import { useCallback, useEffect, useRef, useState } from 'react'
import axios from 'axios'
import { getNotifications, markAllRead, markNotificationRead } from '@/services/notificationApi'
import { INBOX_POLL_INTERVAL_MS } from '@/constants/polling'
import type { Notification } from '@/types/notification'

interface UseNotificationsResult {
  notifications: Notification[]
  unreadCount: number
  loading: boolean
  error: string | null
  markAllAsRead: () => void
  markOneAsRead: (id: number) => void
}

export function useNotifications(): UseNotificationsResult {
  const [notifications, setNotifications] = useState<Notification[]>([])
  const [unreadCount, setUnreadCount] = useState(0)
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
      const { notifications: data, unreadCount: count } = await getNotifications()
      if (!mountedRef.current) return
      setNotifications(data)
      setUnreadCount(count)
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
    const interval = setInterval(() => void fetchNotifications(true), INBOX_POLL_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [fetchNotifications])

  const markAllAsRead = useCallback(() => {
    // Optimistic update — badge clears immediately
    const nowIso = new Date().toISOString()
    setNotifications(prev => prev.map(n => ({ ...n, read: true, readAt: n.readAt ?? nowIso })))
    setUnreadCount(0)
    void markAllRead().catch(() => {
      // Revert on API failure by re-fetching authoritative state
      void fetchNotifications(true)
    })
  }, [fetchNotifications])

  const markOneAsRead = useCallback((id: number) => {
    // Optimistic update — flip the row, decrement the badge if it was unread.
    // Read `wasUnread` from the current state (closure) rather than from the
    // setNotifications updater, because the updater runs asynchronously and
    // closure-mutated locals would still be `false` when setUnreadCount fires.
    const target = notifications.find(n => n.id === id)
    const wasUnread = target !== undefined && !target.read
    const nowIso = new Date().toISOString()
    setNotifications(prev => prev.map(n =>
      n.id === id ? { ...n, read: true, readAt: n.readAt ?? nowIso } : n,
    ))
    if (wasUnread) setUnreadCount(c => Math.max(0, c - 1))
    void markNotificationRead(id).catch(() => {
      void fetchNotifications(true)
    })
  }, [notifications, fetchNotifications])

  return {
    notifications,
    unreadCount,
    loading,
    error,
    markAllAsRead,
    markOneAsRead,
  }
}
