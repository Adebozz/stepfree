import { Bell, Bookmark, Radio, type LucideIcon } from 'lucide-react'

const benefits: { Icon: LucideIcon; title: string; body: string }[] = [
  {
    Icon: Bookmark,
    title: 'Save your regular journeys',
    body: 'Keep your routes and access needs, then plan them again in one click.',
  },
  {
    Icon: Radio,
    title: 'See their status at a glance',
    body: 'Your dashboard shows live line status for every journey you save.',
  },
  {
    Icon: Bell,
    title: 'Get alerts when something changes',
    body: 'We check TfL for problems on your saved lines and stations, and alert you in your account.',
  },
]

export function AccountBenefits() {
  return (
    <section aria-labelledby="account-benefits-heading" className="rounded-2xl bg-gradient-to-br from-navy-900 to-navy-950 p-6 text-white sm:p-8">
      <h2 id="account-benefits-heading" className="text-xl font-bold">
        What you get with an account
      </h2>
      <ul className="mt-5 space-y-5">
        {benefits.map(({ Icon, title, body }) => (
          <li key={title} className="flex gap-4">
            <span aria-hidden="true" className="flex size-10 shrink-0 items-center justify-center rounded-lg bg-white/10 text-accent-light">
              <Icon className="size-5" />
            </span>
            <div>
              <h3 className="font-semibold">{title}</h3>
              <p className="mt-0.5 text-navy-100">{body}</p>
            </div>
          </li>
        ))}
      </ul>
    </section>
  )
}
