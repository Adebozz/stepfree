import { useSearchParams } from 'react-router'

import { useCurrentUser } from '../api/account'
import { JourneyResults } from '../components/JourneyResults'
import { JourneySearchForm } from '../components/JourneySearchForm'
import { StatusSummary } from '../components/StatusSummary'
import { WelcomeHero, WelcomeSections } from '../components/WelcomeSections'
import { fromUrlParams, toUrlParams } from '../features/journey/search'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

export function HomePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const search = fromUrlParams(searchParams)
  useDocumentTitle(search ? `${search.from.name} to ${search.to.name}` : 'Plan a step-free journey')
  const user = useCurrentUser()
  const showWelcome = !search && !user.data

  return (
    <div>
      {showWelcome ? (
        <WelcomeHero />
      ) : (
        <header className="mb-8 max-w-3xl">
          <h1 className="text-3xl font-bold tracking-tight text-navy-900 sm:text-4xl">Plan step-free journeys across London</h1>
          <p className="mt-3 text-base text-slate-700 sm:text-lg">
            Live TfL journeys with your accessibility needs, lift outages and disruptions in one place, with clear labels where
            information is missing.
          </p>
        </header>
      )}

      <div
        id="plan"
        tabIndex={-1}
        className={`grid scroll-mt-6 gap-6 outline-none lg:grid-cols-[minmax(0,1fr)_20rem] ${showWelcome ? 'relative z-10 -mt-24 lg:-mt-32' : ''}`}
      >
        <div className="space-y-8">
          <div className={`border border-navy-100 bg-white p-4 sm:p-6 ${showWelcome ? 'rounded-2xl shadow-xl shadow-navy-900/10' : 'rounded-xl shadow-sm'}`}>
            {showWelcome && <h2 className="mb-5 text-xl font-bold text-navy-900">Where do you need to go?</h2>}
            {/* Remount when the URL changes (e.g. back button) so the form shows that search. */}
            <JourneySearchForm key={searchParams.toString()} initial={search} onSearch={(next) => setSearchParams(toUrlParams(next))} />
          </div>
          {search && <JourneyResults search={search} />}
        </div>
        <aside>
          <StatusSummary />
        </aside>
      </div>

      {showWelcome && <WelcomeSections />}
    </div>
  )
}
