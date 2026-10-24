import { Accessibility, ArrowLeft, Footprints, Repeat, Route } from 'lucide-react'
import { Link, useParams, useSearchParams } from 'react-router'

import { useJourneyPlan } from '../api/queries'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { FreshnessNote } from '../components/FreshnessNote'
import { JourneyDetails } from '../components/JourneyDetails'
import { LineProblems } from '../components/JourneyResults'
import { LoadingSkeleton } from '../components/LoadingSkeleton'
import { RouteChain } from '../components/RouteChain'
import { SaveJourneyPanel } from '../components/SaveJourneyPanel'
import { accessibilityLabel } from '../features/journey/options'
import { fromUrlParams, toApiParams } from '../features/journey/search'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { useJourneyLineStatuses } from '../hooks/useJourneyLineStatuses'
import { obstacleSummaryText } from '../utils/obstacles'
import { formatDuration, formatFare, formatTime } from '../utils/time'

export function JourneyDetailsPage() {
  const { index } = useParams()
  const [searchParams] = useSearchParams()
  const search = fromUrlParams(searchParams)
  const plan = useJourneyPlan(search ? toApiParams(search) : null)
  const journeyIndex = Number(index)
  const selected = plan.data?.data.journeys[journeyIndex]
  const lineStatuses = useJourneyLineStatuses(selected?.lineIds ?? [], plan.data?.data.lineStatuses ?? [])
  useDocumentTitle(search ? `Journey: ${search.from.name} to ${search.to.name}` : 'Journey details')

  const backLink = (
    <Link to={`/?${searchParams.toString()}`} className="inline-flex items-center gap-1 text-sm font-semibold text-brand hover:underline">
      <ArrowLeft aria-hidden="true" className="size-4" />
      Back to results
    </Link>
  )

  if (!search || !Number.isInteger(journeyIndex) || journeyIndex < 0) {
    return (
      <EmptyState icon={Route} title="Journey not found">
        This link doesn't describe a valid journey. <Link to="/" className="font-semibold text-brand underline">Plan a new journey</Link>.
      </EmptyState>
    )
  }

  if (plan.isPending) return <LoadingSkeleton label="Loading journey" rows={4} />
  if (plan.isError) {
    return (
      <div className="space-y-4">
        {backLink}
        <ErrorState error={plan.error} title="Couldn't load this journey" onRetry={() => void plan.refetch()} />
      </div>
    )
  }

  const journey = plan.data.data.journeys[journeyIndex]
  if (!journey) {
    return (
      <div className="space-y-4">
        {backLink}
        <EmptyState icon={Route} title="This journey option is no longer available">
          Live results change as trains run. Go back to see the latest options.
        </EmptyState>
      </div>
    )
  }

  const fare = formatFare(journey.fareTotalPence)

  return (
    <div className="space-y-6">
      {backLink}
      <header className="rounded-xl border border-navy-100 bg-white p-5 shadow-sm">
        <h1 className="text-2xl font-bold text-navy-900">
          {search.from.name} to {search.to.name}
        </h1>
        <p className="mt-1 text-lg font-semibold text-navy-900">
          {formatTime(journey.departureTime)} – {formatTime(journey.arrivalTime)} · {formatDuration(journey.durationMinutes)}
        </p>
        <div className="mt-3">
          <RouteChain legs={journey.legs} />
        </div>
        <ul className="mt-3 flex flex-wrap gap-x-4 gap-y-1 text-sm text-slate-700">
          <li className="inline-flex items-center gap-1.5">
            <Repeat aria-hidden="true" className="size-4 text-slate-500" />
            {journey.interchanges === 0 ? 'No changes' : `${journey.interchanges} change${journey.interchanges === 1 ? '' : 's'}`}
          </li>
          <li className="inline-flex items-center gap-1.5">
            <Footprints aria-hidden="true" className="size-4 text-slate-500" />
            {journey.walkingMinutes} min walking
          </li>
          {fare && <li>{fare} pay as you go</li>}
        </ul>
        <FreshnessNote retrievedAt={plan.data.retrievedAt} stale={plan.data.stale} className="mt-3" />
      </header>

      <section aria-labelledby="accessibility-heading" className="rounded-xl border border-teal-200 bg-teal-50 p-5 text-teal-950">
        <h2 id="accessibility-heading" className="flex items-center gap-2 font-semibold">
          <Accessibility aria-hidden="true" className="size-5" />
          Accessibility
        </h2>
        <p className="mt-2 text-sm">
          {plan.data.data.appliedAccessibility.length > 0
            ? `Planned by TfL with: ${plan.data.data.appliedAccessibility.map(accessibilityLabel).join(', ')}.`
            : 'No accessibility needs were selected for this search.'}
        </p>
        <p className="mt-1 text-sm">{obstacleSummaryText(journey.obstacles)}.</p>
        <p className="mt-1 text-sm">
          TfL's data may not include every barrier, such as a temporary lift failure or a gap between train and platform. Check the
          station pages linked below.
        </p>
      </section>

      <LineProblems lines={lineStatuses} />

      <SaveJourneyPanel search={search} journey={journey} />

      <div className="rounded-xl border border-navy-100 bg-white p-5 shadow-sm">
        <JourneyDetails journey={journey} />
      </div>
    </div>
  )
}
