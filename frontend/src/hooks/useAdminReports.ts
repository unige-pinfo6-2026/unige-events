import { useCallback, useEffect, useState } from 'react'
import { getReports, updateReportStatus } from '@/services/adminApi'
import type { Report, ReportStatus } from '@/types/admin'

type TabType = 'PENDING' | 'PROCESSED'

interface UseAdminReportsResult {
  reports: Report[]
  loading: boolean
  error: string | null
  activeTab: TabType
  setActiveTab: (tab: TabType) => void
  reviewReport: (id: number) => Promise<boolean>
  dismissReport: (id: number) => Promise<boolean>
  refresh: () => void
}

export function useAdminReports(): UseAdminReportsResult {
  const [reports, setReports] = useState<Report[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [activeTab, setActiveTab] = useState<TabType>('PENDING')

  const fetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const status: ReportStatus | undefined = activeTab === 'PENDING' ? 'PENDING' : undefined
      const data = await getReports(status)
      setReports(data)
    } catch {
      setError('Impossible de charger les signalements.')
    } finally {
      setLoading(false)
    }
  }, [activeTab])

  useEffect(() => {
    void fetch()
  }, [fetch])

  const reviewReport = useCallback(async (id: number) => {
    try {
      await updateReportStatus(id, 'REVIEWED')
      setReports(prev => prev.filter(r => r.id !== id))
      return true
    } catch {
      return false
    }
  }, [])

  const dismissReport = useCallback(async (id: number) => {
    try {
      await updateReportStatus(id, 'DISMISSED')
      setReports(prev => prev.filter(r => r.id !== id))
      return true
    } catch {
      return false
    }
  }, [])

  return {
    reports,
    loading,
    error,
    activeTab,
    setActiveTab,
    reviewReport,
    dismissReport,
    refresh: fetch,
  }
}
