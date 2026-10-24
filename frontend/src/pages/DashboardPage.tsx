import { Bell, Bookmark, Route } from 'lucide-react'
import { Link } from 'react-router'

import { useCurrentUser } from '../api/account'
import { useAlerts } from '../api/notifications'
import { useLineStatus } from '../api/queries'
import { useSavedJourneys } from '../api/savedJourneys'
import type { LineStatus, SavedJourney } from '../api/types'
import { AlertItem } from '../components/AlertItem'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { LoadingSkeleton } from '../components/LoadingSkeleton'
import { MonitoringNote } from '../components/MonitoringNote'
import { ServiceLevelBadge } from '../components/ServiceLevelBadge'
import { toUrlParams } from '../features/journey/search'
import { searchFromSaved } from '../features/savedJourney/route'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { lineNameFromId } from '../utils/lines'

const card = 'rounded-xl border border-navy-100 bg-white p-5 shadow-sm'
const RECENT_ALERTS = 3

/** Rendered inside RequireAuth. */
export function DashboardPage() {
  useDocumentTitle('Dashboard')
  const user = useCurrentUser().data
  const alerts = useAlerts()
  const journeys = useSavedJourneys()
  const lineStatus = useLineStatus()
  const lines = new Map((lineStatus.data?.data ?? []).map((line) => [line.id, line]))
  const unread = (alerts.data?.alerts ?? []).filter((alert) => !alert.read)

  return (
    <div className="space-y-6">
      <header className="space-y-1">
        <h1 className="text-2xl font-bold text-navy-900">{user ? `Hello, ${user.fullName.split(' ')[0]}` : 'Dashboard'}</h1>
        <MonitoringNote />
      </header>

      <div className="grid gap-6 lg:grid-cols-2">
        <section aria-labelledby="dash-alerts-heading" className={card}>
          <div className="flex items-baseline justify-between gap-3">
            <h2 id="dash-alerts-heading" className="text-lg font-semibold text-navy-900">
              Unread alerts
            </h2>
            <Link to="/alerts" className="text-sm font-semibold text-brand hover:underline">
              All alerts
            </Link>
          </div>
          <div className="mt-3">
            {alerts.isPending ? (
              <LoadingSkeleton label="Loading alerts" rows={2} />
            ) : alerts.isError ? (
              <ErrorState error={alerts.error} title="Couldn't load your alerts" onRetry={() => void alerts.refetch()} />
            ) : unread.length === 0 ? (
              <EmptyState icon={Bell} title="No unread alerts">
                TfL reports nothing new on your saved routes.
              </EmptyState>
            ) : (
              <ul className="space-y-3">
                {unread.slice(0, RECENT_ALERTS).map((alert) => (
                  <li key={alert.id}>
                    <AlertItem alert={alert} compact headingLevel={3} />
                  </li>
                ))}
                {unread.length > RECENT_ALERTS && (
                  <li className="text-sm text-slate-700">
                    and {unread.length - RECENT_ALERTS} more on the{' '}
                    <Link to="/alerts" className="font-semibold text-brand underline">
                      Alerts page
                    </Link>
                    .
                  </li>
                )}
              </ul>
            )}
          </div>
        </section>

        <section aria-labelledby="dash-journeys-heading" className={card}>
          <div className="flex items-baseline justify-between gap-3">
            <h2 id="dash-journeys-heading" className="text-lg font-semibold text-navy-900">
              Your journeys now
            </h2>
            <Link to="/saved" className="text-sm font-semibold text-brand hover:underline">
              Manage
            </Link>
          </div>
          <div className="mt-3">
            {journeys.isPending ? (
              <LoadingSkeleton label="Loading saved journeys" rows={2} />
            ) : journeys.isError ? (
              <ErrorState error={journeys.error} title="Couldn't load your saved journeys" onRetry={() => void journeys.refetch()} />
            ) : journeys.data.length === 0 ? (
              <EmptyState icon={Bookmark} title="No saved journeys yet">
                <Link to="/" className="font-semibold text-brand underline">
                  Plan a journey
                </Link>{' '}
                and save it to be alerted about disruptions.
              </EmptyState>
            ) : (
              <ul className="divide-y divide-navy-100">
                {journeys.data.map((journey) => (
                  <JourneyRow key={journey.id} journey={journey} lines={lines} statusKnown={lineStatus.isSuccess} />
                ))}
              </ul>
            )}
          </div>
        </section>
      </div>
    </div>
  )
}

function JourneyRow({ journey, lines, statusKnown }: { journey: SavedJourney; lines: Map<string, LineStatus>; statusKnown: boolean }) {
  const search = searchFromSaved(journey)
  return (
    <li className="py-3 first:pt-0 last:pb-0">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <p className="font-medium text-navy-900">{journey.name}</p>
          {!journey.monitoringEnabled && <p className="text-xs text-slate-600">Not being checked for disruptions</p>}
        </div>
        {search && (
          <Link
            to={`/?${toUrlParams(search).toString()}`}
            aria-label={`Plan ${journey.name}`}
            className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:underline"
          >
            <Route aria-hidden="true" className="size-4" />
            Plan
          </Link>
        )}
      </div>
      <ul aria-label={`Lines on ${journey.name}`} className="mt-2 flex flex-wrap gap-2 text-sm">
        {journey.lineIds.map((lineId) => {
          const line = lines.get(lineId)
          return (
            <li key={lineId} className="inline-flex items-center gap-1.5">
              <span className="text-navy-900">{line?.name ?? lineNameFromId(lineId)}</span>
              {line ? (
                <ServiceLevelBadge level={line.level} text={line.summary} />
              ) : (
                <span className="text-xs text-slate-600">{statusKnown ? 'Status unavailable' : 'Loading status…'}</span>
              )}
            </li>
          )
        })}
        {journey.lineIds.length === 0 && <li className="text-slate-600">Walking only</li>}
      </ul>
    </li>
  )
}
