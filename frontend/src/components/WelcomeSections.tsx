import {
  Accessibility,
  ArrowRight,
  Bell,
  Check,
  CircleCheck,
  CircleHelp,
  MapPin,
  Radio,
  Route,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react'
import { Link } from 'react-router'

type Tone = 'confirmed' | 'reported' | 'unknown'

const tones: Record<Tone, { Icon: LucideIcon; badge: string; node: string }> = {
  confirmed: { Icon: CircleCheck, badge: 'bg-green-50 text-green-800', node: 'bg-green-700' },
  reported: { Icon: TriangleAlert, badge: 'bg-amber-50 text-amber-800', node: 'bg-amber-600' },
  unknown: { Icon: CircleHelp, badge: 'bg-slate-100 text-slate-700', node: 'bg-slate-500' },
}

const exampleStops: { place: string; label: string; detail: string; tone: Tone }[] = [
  { place: 'Start station', label: 'Step-free to platform', detail: 'From TfL station data', tone: 'confirmed' },
  { place: 'Change of line', label: 'Lift reported out of service', detail: 'Flagged before you travel', tone: 'reported' },
  { place: 'Destination', label: 'Access information unavailable', detail: 'Shown as unknown, never assumed', tone: 'unknown' },
]

const promises = ['Free to use', 'Live TfL data', 'Built for keyboard and screen readers']

const heroLink = 'focus-visible:outline-white'

/** The app's logo line, drawn large behind the hero. */
function StepLine() {
  return (
    <svg
      aria-hidden="true"
      viewBox="0 0 1200 520"
      preserveAspectRatio="xMidYMid slice"
      className="absolute inset-0 size-full [mask-image:linear-gradient(to_right,transparent_50%,black_85%)]"
    >
      <path
        d="M-20 500H250V390H500V280H750V170H1000V60H1240"
        fill="none"
        stroke="#2dd4bf"
        strokeOpacity="0.16"
        strokeWidth="26"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  )
}

function RoutePreview() {
  return (
    <figure className="relative hidden rounded-2xl bg-white p-6 text-navy-900 shadow-2xl shadow-black/40 lg:block">
      <figcaption className="flex items-center justify-between gap-3">
        <span className="font-semibold">Every part of your route, labelled</span>
        <span className="shrink-0 rounded-full bg-navy-50 px-2.5 py-1 text-xs font-medium text-slate-600">Example</span>
      </figcaption>
      <ol className="relative mt-5 space-y-5">
        <span aria-hidden="true" className="absolute top-3 bottom-8 left-[0.4375rem] w-0.5 bg-navy-100" />
        {exampleStops.map(({ place, label, detail, tone }) => {
          const { Icon, badge, node } = tones[tone]
          return (
            <li key={place} className="relative flex gap-4">
              <span aria-hidden="true" className={`relative mt-1 size-4 shrink-0 rounded-full ring-4 ring-white ${node}`} />
              <div className="min-w-0">
                <p className="font-semibold">{place}</p>
                <p className={`mt-1.5 inline-flex items-center gap-1.5 rounded-md px-2 py-1 text-sm font-medium ${badge}`}>
                  <Icon aria-hidden="true" className="size-4 shrink-0" />
                  {label}
                </p>
                <p className="mt-1 text-sm text-slate-600">{detail}</p>
              </div>
            </li>
          )
        })}
      </ol>
    </figure>
  )
}

export function WelcomeHero() {
  return (
    <section
      aria-labelledby="welcome-heading"
      className="relative left-1/2 -mt-6 w-screen -translate-x-1/2 overflow-hidden bg-gradient-to-b from-navy-900 to-navy-950 text-white sm:-mt-10"
    >
      <StepLine />
      <div className="relative mx-auto grid max-w-6xl items-center gap-12 px-4 pt-10 pb-32 sm:px-6 sm:pt-16 sm:pb-36 lg:grid-cols-[minmax(0,1fr)_24rem] lg:pt-20 lg:pb-44">
        <div>
          <p className="inline-flex items-center gap-2 rounded-full bg-white/10 px-3 py-1.5 text-sm font-medium text-navy-100">
            <Accessibility aria-hidden="true" className="size-4 text-accent-light" />
            Accessible travel across London
          </p>
          <h1 id="welcome-heading" className="mt-5 text-4xl leading-[1.08] font-bold tracking-tight text-balance sm:text-5xl lg:text-6xl">
            Plan <span className="text-accent-light">step-free</span> journeys across London
          </h1>
          <p className="mt-5 max-w-xl text-lg text-navy-100">
            A broken lift can end a journey if you use a wheelchair or can&apos;t manage stairs. StepFree London puts live TfL routes,
            lift outages and disruptions in one place, and tells you when information is missing.
          </p>
          <div className="mt-7 flex flex-wrap gap-3">
            <a
              href="#plan"
              className={`inline-flex items-center gap-2 rounded-lg bg-accent-light px-5 py-3 font-semibold text-navy-950 hover:bg-white ${heroLink}`}
            >
              Plan a journey
              <ArrowRight aria-hidden="true" className="size-4" />
            </a>
            <a href="#what-it-does" className={`rounded-lg border border-white/30 px-5 py-3 font-semibold text-white hover:bg-white/10 ${heroLink}`}>
              See what it does
            </a>
          </div>
          <ul className="mt-7 flex flex-wrap gap-x-5 gap-y-2 text-sm text-navy-100">
            {promises.map((promise) => (
              <li key={promise} className="inline-flex items-center gap-1.5">
                <Check aria-hidden="true" className="size-4 text-accent-light" />
                {promise}
              </li>
            ))}
          </ul>
        </div>
        <RoutePreview />
      </div>
    </section>
  )
}

const chip = 'rounded-full bg-navy-50 px-3 py-1 text-sm font-medium text-navy-800'

function FeatureCard({
  Icon,
  title,
  body,
  wide,
  children,
}: {
  Icon: LucideIcon
  title: string
  body: string
  wide?: boolean
  children: React.ReactNode
}) {
  return (
    <li
      className={`flex flex-col rounded-2xl border border-navy-100 bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:shadow-lg sm:p-7 ${wide ? 'lg:col-span-2' : ''}`}
    >
      <span aria-hidden="true" className="flex size-11 items-center justify-center rounded-xl bg-navy-900 text-accent-light">
        <Icon className="size-5" />
      </span>
      <h3 className="mt-4 text-lg font-semibold text-navy-900">{title}</h3>
      <p className="mt-1.5 text-slate-700">{body}</p>
      <div className="mt-auto pt-5">{children}</div>
    </li>
  )
}

function Chips({ items }: { items: string[] }) {
  return (
    <ul className="flex flex-wrap gap-2">
      {items.map((item) => (
        <li key={item} className={chip}>
          {item}
        </li>
      ))}
    </ul>
  )
}

const steps = [
  { title: 'Choose your stations', body: 'Enter where you are starting from and where you need to go.' },
  { title: 'Set your access needs', body: 'Tick what matters to you, such as step-free access or avoiding escalators.' },
  { title: 'Compare your options', body: 'Review each route, its accessibility details and any disruption on the line.' },
]

const dataLabels: { tone: Tone; title: string; body: string }[] = [
  { tone: 'confirmed', title: 'Confirmed', body: 'TfL has published this detail.' },
  { tone: 'reported', title: 'Reported problem', body: 'TfL lists an outage or disruption.' },
  { tone: 'unknown', title: 'Unavailable', body: 'TfL has no record, so we say so.' },
]

const eyebrow = 'text-sm font-semibold tracking-wide text-accent uppercase'
const sectionHeading = 'mt-2 text-3xl font-bold tracking-tight text-navy-900 sm:text-4xl'

export function WelcomeSections() {
  return (
    <div className="mt-16 space-y-20 sm:mt-24 sm:space-y-28">
      <section id="what-it-does" aria-labelledby="what-it-does-heading" className="scroll-mt-6">
        <p className={eyebrow}>Before you travel</p>
        <h2 id="what-it-does-heading" className={sectionHeading}>
          What StepFree London does
        </h2>
        <ul className="mt-8 grid gap-5 lg:grid-cols-3">
          <FeatureCard
            wide
            Icon={Route}
            title="Find a route that works for you"
            body="Plan around your access needs, then see the lifts, stairs and walkways on each leg of the journey."
          >
            <Chips items={['Step-free to platform', 'Step-free to train', 'Avoid stairs', 'Avoid escalators', 'Avoid lifts']} />
          </FeatureCard>
          <FeatureCard
            Icon={MapPin}
            title="Check a station first"
            body="Accessibility facts, facilities, lift outages and station disruptions on one page."
          >
            <Chips items={['Lift outages', 'Facilities']} />
          </FeatureCard>
          <FeatureCard Icon={Radio} title="See what is running now" body="Live line status across London, and a map of your route.">
            <Chips items={['Tube', 'Elizabeth line', 'Overground', 'DLR', 'Tram']} />
          </FeatureCard>
          <FeatureCard
            wide
            Icon={Bell}
            title="Get alerts for your regular trips"
            body="Save a journey and StepFree London keeps watching its lines and stations for you."
          >
            <ol className="flex flex-wrap items-center gap-2 text-sm font-medium text-navy-800">
              {['Save a journey', 'We check TfL for changes', 'You get an alert'].map((item, index) => (
                <li key={item} className="inline-flex items-center gap-2">
                  {index > 0 && <ArrowRight aria-hidden="true" className="size-4 text-accent" />}
                  <span className="rounded-full bg-navy-50 px-3 py-1">{item}</span>
                </li>
              ))}
            </ol>
          </FeatureCard>
        </ul>
      </section>

      <section aria-labelledby="how-it-works-heading">
        <p className={eyebrow}>Three stops</p>
        <h2 id="how-it-works-heading" className={sectionHeading}>
          How it works
        </h2>
        <ol className="relative mt-10 grid gap-8 sm:grid-cols-3 sm:gap-6">
          <span aria-hidden="true" className="absolute top-0 bottom-16 left-[1.1875rem] w-0.5 bg-accent/40 sm:top-[1.1875rem] sm:right-1/3 sm:bottom-auto sm:left-0 sm:h-0.5 sm:w-auto" />
          {steps.map((step, index) => (
            <li key={step.title} className="relative flex gap-4 sm:block">
              <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-full bg-accent font-bold text-white ring-8 ring-navy-50">
                {index + 1}
              </span>
              <div className="sm:mt-5 sm:pr-4">
                <h3 className="text-lg font-semibold text-navy-900">{step.title}</h3>
                <p className="mt-1 text-slate-700">{step.body}</p>
              </div>
            </li>
          ))}
        </ol>
      </section>

      <section aria-labelledby="missing-data-heading" className="relative overflow-hidden rounded-3xl bg-gradient-to-br from-navy-900 to-navy-950 text-white">
        <StepLine />
        <div className="relative grid items-center gap-10 p-7 sm:p-12 lg:grid-cols-[minmax(0,1fr)_24rem]">
          <div>
            <h2 id="missing-data-heading" className="text-3xl font-bold tracking-tight sm:text-4xl">
              Clear about what it doesn&apos;t know
            </h2>
            <p className="mt-4 max-w-xl text-lg text-navy-100">
              TfL&apos;s information can be incomplete. If a detail is missing, StepFree London marks it as unavailable and never assumes
              a route is fine.
            </p>
            <div className="mt-7 flex flex-wrap gap-3">
              <Link to="/register" className={`rounded-lg bg-accent-light px-5 py-3 font-semibold text-navy-950 hover:bg-white ${heroLink}`}>
                Create a free account
              </Link>
              <Link to="/status" className={`rounded-lg border border-white/30 px-5 py-3 font-semibold text-white hover:bg-white/10 ${heroLink}`}>
                See live status
              </Link>
            </div>
          </div>
          <ul className="space-y-3">
            {dataLabels.map(({ tone, title, body }) => {
              const { Icon, badge } = tones[tone]
              return (
                <li key={title} className="flex items-center gap-4 rounded-xl bg-white p-4 text-navy-900">
                  <span aria-hidden="true" className={`flex size-10 shrink-0 items-center justify-center rounded-lg ${badge}`}>
                    <Icon className="size-5" />
                  </span>
                  <div>
                    <p className="font-semibold">{title}</p>
                    <p className="text-sm text-slate-600">{body}</p>
                  </div>
                </li>
              )
            })}
          </ul>
        </div>
      </section>
    </div>
  )
}
