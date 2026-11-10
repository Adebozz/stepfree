import { ArrowRight } from 'lucide-react'
import { Link } from 'react-router'

const steps = [
  { title: 'Plan a journey', body: 'Search your route and set your access needs.' },
  { title: 'Press Save', body: 'Choose the route that works and give it a name.' },
  { title: 'Let us watch it', body: 'Its live status appears here, and we alert you if it is affected.' },
]

/** Shown on a new account's dashboard in place of empty alert and journey lists. */
export function FirstJourneyGuide() {
  return (
    <section aria-labelledby="first-journey-heading" className="rounded-3xl bg-gradient-to-br from-navy-900 to-navy-950 p-6 text-white sm:p-10">
      <h2 id="first-journey-heading" className="text-2xl font-bold tracking-tight sm:text-3xl">
        Save your first journey to get started
      </h2>
      <p className="mt-2 max-w-2xl text-lg text-navy-100">
        Once a journey is saved, this page shows how its lines are running and any alerts that affect it.
      </p>
      <ol className="mt-8 grid gap-6 sm:grid-cols-3">
        {steps.map((step, index) => (
          <li key={step.title} className="flex gap-3 sm:block">
            <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-full bg-accent-light font-bold text-navy-950">
              {index + 1}
            </span>
            <div className="sm:mt-3">
              <h3 className="font-semibold">{step.title}</h3>
              <p className="mt-0.5 text-navy-100">{step.body}</p>
            </div>
          </li>
        ))}
      </ol>
      <Link
        to="/"
        className="mt-8 inline-flex items-center gap-2 rounded-lg bg-accent-light px-5 py-3 font-semibold text-navy-950 hover:bg-white focus-visible:outline-white"
      >
        Plan a journey
        <ArrowRight aria-hidden="true" className="size-4" />
      </Link>
    </section>
  )
}
