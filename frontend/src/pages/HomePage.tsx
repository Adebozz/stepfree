import { useSearchParams } from 'react-router'

import { JourneyResults } from '../components/JourneyResults'
import { JourneySearchForm } from '../components/JourneySearchForm'
import { StatusSummary } from '../components/StatusSummary'
import { fromUrlParams, toUrlParams } from '../features/journey/search'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

export function HomePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const search = fromUrlParams(searchParams)
  useDocumentTitle(search ? `${search.from.name} to ${search.to.name}` : 'Plan a step-free journey')

  return (
    <div className="space-y-8">
      <header className="max-w-3xl">
        <h1 className="text-3xl font-bold tracking-tight text-navy-900 sm:text-4xl">Plan step-free journeys across London</h1>
        <p className="mt-3 text-base text-slate-700 sm:text-lg">
          Live TfL journeys with your accessibility needs, lift outages and disruptions in one place, with clear labels where
          information is missing.
        </p>
      </header>

      <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_20rem]">
        <div className="space-y-8">
          <div className="rounded-xl border border-navy-100 bg-white p-4 shadow-sm sm:p-6">
            {/* Remount when the URL changes (e.g. back button) so the form shows that search. */}
            <JourneySearchForm key={searchParams.toString()} initial={search} onSearch={(next) => setSearchParams(toUrlParams(next))} />
          </div>
          {search && <JourneyResults search={search} />}
        </div>
        <aside>
          <StatusSummary />
        </aside>
      </div>
    </div>
  )
}
