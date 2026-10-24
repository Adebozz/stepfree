import { zodResolver } from '@hookform/resolvers/zod'
import { Bookmark, CircleCheck } from 'lucide-react'
import { useForm } from 'react-hook-form'
import { Link, useLocation } from 'react-router'
import { z } from 'zod'

import { useCurrentUser } from '../api/account'
import { useCreateSavedJourney } from '../api/savedJourneys'
import type { JourneyOption } from '../api/types'
import type { JourneySearch } from '../features/journey/search'
import { defaultJourneyName, routeFromJourney } from '../features/savedJourney/route'
import { FormError } from './FormError'
import { TextField } from './TextField'

const schema = z.object({ name: z.string().trim().min(1, 'Give this journey a name').max(80, 'Use at most 80 characters') })

interface SaveJourneyPanelProps {
  search: JourneySearch
  journey: JourneyOption
}

/** Saves the chosen option's route, so later disruption checks can match its exact lines and stations. */
export function SaveJourneyPanel({ search, journey }: SaveJourneyPanelProps) {
  const user = useCurrentUser()
  const location = useLocation()
  const save = useCreateSavedJourney()
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ resolver: zodResolver(schema), defaultValues: { name: defaultJourneyName(search) } })

  if (user.isPending || user.isError) return null

  const container = 'rounded-xl border border-navy-100 bg-white p-5 shadow-sm'
  const heading = (
    <h2 className="flex items-center gap-2 font-semibold text-navy-900">
      <Bookmark aria-hidden="true" className="size-5" />
      Save this journey
    </h2>
  )

  if (!user.data) {
    const next = encodeURIComponent(`${location.pathname}${location.search}`)
    return (
      <section className={container}>
        {heading}
        <p className="mt-2 text-sm text-slate-700">
          <Link to={`/sign-in?next=${next}`} className="font-semibold text-brand underline">
            Sign in
          </Link>{' '}
          or{' '}
          <Link to={`/register?next=${next}`} className="font-semibold text-brand underline">
            create an account
          </Link>{' '}
          to save this journey.
        </p>
      </section>
    )
  }

  if (save.isSuccess) {
    return (
      <section className={container} aria-live="polite">
        <p className="flex items-center gap-2 font-semibold text-green-800">
          <CircleCheck aria-hidden="true" className="size-5" />
          Saved as “{save.data.name}”.
        </p>
        <Link to="/saved" className="mt-2 inline-block text-sm font-semibold text-brand underline">
          View your saved journeys
        </Link>
      </section>
    )
  }

  return (
    <section className={container}>
      {heading}
      <form
        noValidate
        aria-label="Save this journey"
        className="mt-3 space-y-3"
        onSubmit={handleSubmit(({ name }) =>
          save.mutate({
            name,
            originId: search.from.id,
            destinationId: search.to.id,
            accessibility: search.accessibility,
            preference: search.preference,
            route: routeFromJourney(search, journey),
          }),
        )}
      >
        <FormError error={save.error} />
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end">
          <div className="flex-1">
            <TextField label="Name" error={errors.name?.message} {...register('name')} />
          </div>
          <button
            type="submit"
            disabled={save.isPending}
            className="rounded-lg bg-brand px-5 py-2.5 font-semibold text-white shadow-sm hover:bg-brand-dark disabled:opacity-60"
          >
            {save.isPending ? 'Saving…' : 'Save journey'}
          </button>
        </div>
        <p className="text-sm text-slate-600">Saves this route and your accessibility needs, not the departure time.</p>
      </form>
    </section>
  )
}
