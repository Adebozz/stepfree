import { Bell } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router'

import { useAlerts, useMarkAllAlertsRead } from '../api/notifications'
import { AlertItem } from '../components/AlertItem'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { FormError } from '../components/FormError'
import { LoadingSkeleton } from '../components/LoadingSkeleton'
import { MonitoringNote } from '../components/MonitoringNote'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

export function AlertsPage() {
  useDocumentTitle('Alerts')
  const alerts = useAlerts()
  const markAll = useMarkAllAlertsRead()
  const [unreadOnly, setUnreadOnly] = useState(false)
  const shown = (alerts.data?.alerts ?? []).filter((alert) => !unreadOnly || !alert.read)
  const unread = alerts.data?.unreadCount ?? 0

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold text-navy-900">Alerts</h1>
        <p className="text-slate-700">
          Disruptions TfL reports on the lines and stations of your saved journeys. Change what you're told about in{' '}
          <Link to="/account" className="font-semibold text-brand underline">
            your account
          </Link>
          .
        </p>
        <MonitoringNote />
      </header>

      {alerts.isPending ? (
        <LoadingSkeleton label="Loading alerts" rows={3} />
      ) : alerts.isError ? (
        <ErrorState error={alerts.error} title="Couldn't load your alerts" onRetry={() => void alerts.refetch()} />
      ) : alerts.data.alerts.length === 0 ? (
        <EmptyState icon={Bell} title="No alerts yet">
          When TfL reports a disruption on one of your{' '}
          <Link to="/saved" className="font-semibold text-brand underline">
            saved journeys
          </Link>
          , it appears here.
        </EmptyState>
      ) : (
        <>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <p role="status" className="text-sm font-medium text-navy-900">
              {unread === 0 ? 'No unread alerts' : `${unread} unread alert${unread === 1 ? '' : 's'}`}
            </p>
            <div className="flex flex-wrap items-center gap-3">
              <label className="flex items-center gap-2 text-sm text-navy-900">
                <input type="checkbox" className="size-4" checked={unreadOnly} onChange={(event) => setUnreadOnly(event.target.checked)} />
                Show unread only
              </label>
              {unread > 0 && (
                <button
                  type="button"
                  onClick={() => markAll.mutate()}
                  disabled={markAll.isPending}
                  className="rounded-lg border border-navy-200 bg-white px-3 py-1.5 text-sm font-medium text-navy-900 hover:bg-navy-50 disabled:opacity-60"
                >
                  Mark all as read
                </button>
              )}
            </div>
          </div>
          <FormError error={markAll.error} />
          {shown.length === 0 ? (
            <p className="text-sm text-slate-700">You've read every alert.</p>
          ) : (
            <ul className="space-y-3">
              {shown.map((alert) => (
                <li key={alert.id}>
                  <AlertItem alert={alert} />
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </div>
  )
}
