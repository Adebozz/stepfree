import { Accessibility, Route } from 'lucide-react'

import { ApiError } from '../api/client'
import { useJourneyPlan } from '../api/queries'
import type { LineStatus } from '../api/types'
import { accessibilityLabel } from '../features/journey/options'
import { useJourneyLineStatuses } from '../hooks/useJourneyLineStatuses'
import { toApiParams, toUrlParams, type JourneySearch } from '../features/journey/search'
import { isDisrupted } from '../utils/serviceLevel'
import { DisruptionAlert } from './DisruptionAlert'
import { EmptyState } from './EmptyState'
import { ErrorState } from './ErrorState'
import { FreshnessNote } from './FreshnessNote'
import { JourneyCard } from './JourneyCard'
import { LoadingSkeleton } from './LoadingSkeleton'

export function LineProblems({ lines }: { lines: LineStatus[] }) {
  const problems = lines.filter((line) => isDisrupted(line.level))
  if (problems.length === 0) return null
  return (
    <div className="space-y-2">
      {problems.map((line) => (
        <DisruptionAlert key={line.id} title={`${line.name}: ${line.summary}`} accessibility={line.statuses.some((s) => s.stepFreeAccessAffected)}>
          {line.statuses.find((s) => s.reason)?.reason ?? 'TfL reports disruption on this line.'}
        </DisruptionAlert>
      ))}
    </div>
  )
}

export function JourneyResults({ search }: { search: JourneySearch }) {
  const plan = useJourneyPlan(toApiParams(search))
  const detailsQuery = toUrlParams(search).toString()
  const journeys = plan.data?.data.journeys ?? []
  const lineStatuses = useJourneyLineStatuses(
    journeys.flatMap((journey) => journey.lineIds),
    plan.data?.data.lineStatuses ?? [],
  )

  return (
    <section aria-labelledby="results-heading" className="space-y-4">
      <h2 id="results-heading" className="text-xl font-semibold text-navy-900">
        {search.from.name} to {search.to.name}
      </h2>

      {search.accessibility.length > 0 && (
        <p className="flex flex-wrap items-center gap-2 text-sm text-navy-900">
          <Accessibility aria-hidden="true" className="size-4 text-accent" />
          Planned with:
          {search.accessibility.map((value) => (
            <span key={value} className="rounded-full bg-teal-50 px-2 py-0.5 text-xs font-medium text-teal-900 ring-1 ring-teal-600/30">
              {accessibilityLabel(value)}
            </span>
          ))}
        </p>
      )}

      {plan.isPending ? (
        <LoadingSkeleton label="Finding journeys" rows={3} />
      ) : plan.isError ? (
        plan.error instanceof ApiError && plan.error.status === 404 ? (
          <EmptyState icon={Route} title="No journey found">
            {plan.error.message}
          </EmptyState>
        ) : (
          <ErrorState error={plan.error} title="Couldn't plan this journey" onRetry={() => void plan.refetch()} />
        )
      ) : (
        <>
          <p role="status" className="sr-only">
            {plan.data.data.journeys.length} journey options found.
          </p>
          <FreshnessNote retrievedAt={plan.data.retrievedAt} stale={plan.data.stale} />
          <LineProblems lines={lineStatuses} />
          {plan.data.data.stopMessages.map((message) => (
            <DisruptionAlert key={message} title="Notice from TfL">
              {message}
            </DisruptionAlert>
          ))}
          {plan.data.data.journeys.length === 0 ? (
            <EmptyState icon={Route} title="No journey options returned">
              TfL didn't return any journeys. Try a different time or fewer options.
            </EmptyState>
          ) : (
            <ol className="space-y-3">
              {plan.data.data.journeys.map((journey, index) => (
                <li key={index}>
                  <JourneyCard journey={journey} index={index} detailsHref={`/journey/${index}?${detailsQuery}`} />
                </li>
              ))}
            </ol>
          )}
        </>
      )}
    </section>
  )
}
