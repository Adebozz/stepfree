import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { apiGet, apiSend } from './client'
import type { AlertList, MonitoringStatus, NotificationPreferences } from './types'

export const notificationKeys = {
  all: ['notifications'] as const,
  alerts: ['notifications', 'alerts'] as const,
  preferences: ['notifications', 'preferences'] as const,
  monitoring: ['notifications', 'monitoring'] as const,
}

/** Alerts arrive from a background job, so the list refreshes while the tab is open. */
const ALERT_REFRESH_MS = 60_000

export function useAlerts(enabled = true) {
  return useQuery({
    queryKey: notificationKeys.alerts,
    queryFn: ({ signal }) => apiGet<AlertList>('/alerts', new URLSearchParams({ limit: '100' }), signal),
    enabled,
    refetchInterval: ALERT_REFRESH_MS,
  })
}

export function useMarkAlertRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiSend('POST', `/alerts/${encodeURIComponent(id)}/read`),
    onSuccess: (_, id) =>
      queryClient.setQueryData<AlertList>(notificationKeys.alerts, (list) => {
        if (!list) return list
        const wasUnread = list.alerts.some((alert) => alert.id === id && !alert.read)
        return {
          alerts: list.alerts.map((alert) => (alert.id === id ? { ...alert, read: true } : alert)),
          unreadCount: wasUnread ? Math.max(0, list.unreadCount - 1) : list.unreadCount,
        }
      }),
  })
}

export function useMarkAllAlertsRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiSend('POST', '/alerts/read-all'),
    onSuccess: () =>
      queryClient.setQueryData<AlertList>(notificationKeys.alerts, (list) =>
        list && { alerts: list.alerts.map((alert) => ({ ...alert, read: true })), unreadCount: 0 },
      ),
  })
}

export function useNotificationPreferences() {
  return useQuery({
    queryKey: notificationKeys.preferences,
    queryFn: ({ signal }) => apiGet<NotificationPreferences>('/notification-preferences', undefined, signal),
  })
}

/** Optimistic, like the saved-journey monitoring switch: a checkbox must respond when clicked. */
export function useUpdateNotificationPreferences() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (preferences: NotificationPreferences) =>
      apiSend<NotificationPreferences>('PUT', '/notification-preferences', preferences),
    onMutate: async (preferences) => {
      await queryClient.cancelQueries({ queryKey: notificationKeys.preferences })
      const previous = queryClient.getQueryData<NotificationPreferences>(notificationKeys.preferences)
      queryClient.setQueryData(notificationKeys.preferences, preferences)
      return { previous }
    },
    onError: (_error, _preferences, context) => queryClient.setQueryData(notificationKeys.preferences, context?.previous),
    onSuccess: (saved) => queryClient.setQueryData(notificationKeys.preferences, saved),
  })
}

export function useMonitoringStatus(enabled = true) {
  return useQuery({
    queryKey: notificationKeys.monitoring,
    queryFn: ({ signal }) => apiGet<MonitoringStatus>('/monitoring/status', undefined, signal),
    enabled,
    refetchInterval: ALERT_REFRESH_MS,
  })
}
