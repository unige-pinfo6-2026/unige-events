import { useCallback, useEffect, useState } from 'react'
import { getReports, updateReportStatus } from '@/services/adminApi'
import { useToast } from '@/hooks/useToast'
import type { Report } from '@/types/admin'

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
  const { showToast } = useToast()

  const fetch = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (activeTab === 'PENDING') {
        const data = await getReports('PENDING')
        setReports(data)
      } else {
        // Le back interprète `?status=` omis comme PENDING, donc l'onglet "Traités"
        // doit explicitement demander REVIEWED et DISMISSED puis fusionner.
        const [reviewed, dismissed] = await Promise.all([
          getReports('REVIEWED'),
          getReports('DISMISSED'),
        ])
        const merged = [...reviewed, ...dismissed].sort((a, b) => b.createdAt.localeCompare(a.createdAt))
        setReports(merged)
      }
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
      // Côté back, valider un report bannit l'event et clôt en cascade tous les
      // autres signalements PENDING sur ce même event (SCRUM-97). Le toast
      // reflète ces deux effets pour ne pas surprendre l'admin.
      showToast('success', 'Événement banni, signalements clôturés.')
      return true
    } catch {
      showToast('error', 'Impossible de bannir l\'événement.')
      return false
    }
  }, [showToast])

  const dismissReport = useCallback(async (id: number) => {
    try {
      await updateReportStatus(id, 'DISMISSED')
      setReports(prev => prev.filter(r => r.id !== id))
      showToast('success', 'Signalement ignoré.')
      return true
    } catch {
      showToast('error', 'Impossible de traiter le signalement.')
      return false
    }
  }, [showToast])

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
