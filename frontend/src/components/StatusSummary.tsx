import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router'

import { useLineStatus } from '../api/queries'
import { isDisrupted } from '../utils/serviceLevel'
import { ErrorState } from './ErrorState'
import { FreshnessNote } from './FreshnessNote'
import { LineBadge } from './LineBadge'
import { LoadingSkeleton } from './LoadingSkeleton'
import { ServiceLevelBadge } from './ServiceLevelBadge'

const LEVEL_RANK = { CLOSED: 0, SEVERE_DISRUPTION: 1, MINOR_DISRUPTION: 2, UNKNOWN: 3, INFORMATION: 4, GOOD_SERVICE: 5 } as const

/** Network summary on the planner page: which lines have problems right now. */
export function StatusSummary() {
  const status = useLineStatus()

  return (
    <section aria-labelledby="status-summary-heading" className="rounded-xl border border-navy-100 bg-white p-5 shadow-sm">
      <h2 id="status-summary-heading" className="text-lg font-semibold text-navy-900">
        Network status
      </h2>
      {status.isPending ? (
        <LoadingSkeleton label="Loading line status" rows={2} className="mt-3" />
      ) : status.isError ? (
        <div className="mt-3">
          <ErrorState error={status.error} title="Line status unavailable" onRetry={() => void status.refetch()} />
        </div>
      ) : (
        (() => {
          const lines = status.data.data
          const problems = lines.filter((line) => isDisrupted(line.level)).sort((a, b) => LEVEL_RANK[a.level] - LEVEL_RANK[b.level])
          const unknown = lines.filter((line) => line.level === 'UNKNOWN').length
          return (
            <>
              <p className="mt-2 text-sm text-slate-700">
                {problems.length === 0
                  ? `No disruptions reported on ${lines.length - unknown} of ${lines.length} lines.`
                  : `${problems.length} of ${lines.length} lines have disruptions.`}
                {unknown > 0 && ` Status unavailable for ${unknown}.`}
              </p>
              {problems.length > 0 && (
                <ul className="mt-3 space-y-2">
                  {problems.slice(0, 6).map((line) => (
                    <li key={line.id} className="flex flex-wrap items-center justify-between gap-2">
                      <LineBadge lineId={line.id} name={line.name} />
                      <ServiceLevelBadge level={line.level} text={line.summary} />
                    </li>
                  ))}
                </ul>
              )}
              <FreshnessNote retrievedAt={status.data.retrievedAt} stale={status.data.stale} className="mt-3" />
              <Link to="/status" className="mt-3 inline-flex items-center gap-1 text-sm font-semibold text-brand hover:underline">
                All lines and station disruptions
                <ArrowRight aria-hidden="true" className="size-4" />
              </Link>
            </>
          )
        })()
      )}
    </section>
  )
}
