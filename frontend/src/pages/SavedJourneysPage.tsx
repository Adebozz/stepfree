import { zodResolver } from '@hookform/resolvers/zod'
import { Bookmark, Pencil, Route, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router'
import { z } from 'zod'

import { useLineStatus } from '../api/queries'
import { useDeleteSavedJourney, useSavedJourneys, useUpdateSavedJourney } from '../api/savedJourneys'
import type { SavedJourney } from '../api/types'
import { EmptyState } from '../components/EmptyState'
import { ErrorState } from '../components/ErrorState'
import { FormError } from '../components/FormError'
import { LineBadge } from '../components/LineBadge'
import { LoadingSkeleton } from '../components/LoadingSkeleton'
import { TextField } from '../components/TextField'
import { accessibilityLabel } from '../features/journey/options'
import { toUrlParams } from '../features/journey/search'
import { searchFromSaved } from '../features/savedJourney/route'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { lineNameFromId } from '../utils/lines'

export function SavedJourneysPage() {
  useDocumentTitle('Saved journeys')
  const journeys = useSavedJourneys()
  const lineStatus = useLineStatus()
  const lineNames = new Map((lineStatus.data?.data ?? []).map((line) => [line.id, line.name]))

  return (
    <div className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold text-navy-900">Saved journeys</h1>
        <p className="mt-1 text-slate-700">Re-plan a journey with live times, or tidy up your list.</p>
      </header>
      {journeys.isPending ? (
        <LoadingSkeleton label="Loading saved journeys" rows={3} />
      ) : journeys.isError ? (
        <ErrorState error={journeys.error} title="Couldn't load your saved journeys" onRetry={() => void journeys.refetch()} />
      ) : journeys.data.length === 0 ? (
        <EmptyState icon={Bookmark} title="No saved journeys yet">
          Plan a journey, open its details and choose <span className="font-semibold">Save journey</span>.{' '}
          <Link to="/" className="font-semibold text-brand underline">
            Plan a journey
          </Link>
        </EmptyState>
      ) : (
        <ul className="space-y-4">
          {journeys.data.map((journey) => (
            <li key={journey.id}>
              <SavedJourneyCard journey={journey} lineName={(id) => lineNames.get(id) ?? lineNameFromId(id)} />
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

const renameSchema = z.object({ name: z.string().trim().min(1, 'Enter a name').max(80, 'Use at most 80 characters') })

function SavedJourneyCard({ journey, lineName }: { journey: SavedJourney; lineName: (id: string) => string }) {
  const [renaming, setRenaming] = useState(false)
  const [confirmingDelete, setConfirmingDelete] = useState(false)
  const update = useUpdateSavedJourney()
  const remove = useDeleteSavedJourney()
  const search = searchFromSaved(journey)
  const headingId = `saved-${journey.id}`
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm({ resolver: zodResolver(renameSchema), defaultValues: { name: journey.name } })

  return (
    <article aria-labelledby={headingId} className="rounded-xl border border-navy-100 bg-white p-5 shadow-sm">
      {/* The heading stays while renaming: it names the article (aria-labelledby). */}
      <h2 id={headingId} className="text-lg font-semibold text-navy-900">
        {journey.name}
      </h2>
      {renaming && (
        <form
          noValidate
          aria-label={`Rename ${journey.name}`}
          className="mt-2 space-y-2"
          onSubmit={handleSubmit(({ name }) => update.mutate({ id: journey.id, name }, { onSuccess: () => setRenaming(false) }))}
        >
          <FormError error={update.error} />
          <TextField label="New name" error={errors.name?.message} autoFocus {...register('name')} />
          <div className="flex gap-2">
            <button type="submit" disabled={update.isPending} className="rounded-lg bg-brand px-3 py-1.5 text-sm font-semibold text-white">
              Save name
            </button>
            <button type="button" onClick={() => setRenaming(false)} className="rounded-lg px-3 py-1.5 text-sm font-medium text-navy-900 ring-1 ring-navy-200">
              Cancel
            </button>
          </div>
        </form>
      )}
      <p className="mt-1 text-slate-700">
        {journey.origin.name} to {journey.destination.name}
      </p>
      {journey.lineIds.length > 0 && (
        <ul aria-label="Lines" className="mt-2 flex flex-wrap gap-1.5">
          {journey.lineIds.map((id) => (
            <li key={id}>
              <LineBadge lineId={id} name={lineName(id)} />
            </li>
          ))}
        </ul>
      )}
      <p className="mt-2 text-sm text-slate-600">
        {journey.accessibility.length > 0
          ? `Accessibility: ${journey.accessibility.map(accessibilityLabel).join(', ')}`
          : 'No accessibility needs saved'}
      </p>

      <div className="mt-3 flex items-start gap-2 text-sm">
        <input
          id={`${headingId}-monitor`}
          type="checkbox"
          className="mt-0.5 size-4"
          aria-describedby={`${headingId}-monitor-hint`}
          checked={journey.monitoringEnabled}
          disabled={update.isPending}
          onChange={(event) => update.mutate({ id: journey.id, monitoringEnabled: event.target.checked })}
        />
        <div>
          <label htmlFor={`${headingId}-monitor`} className="text-navy-900">
            Watch this journey for disruptions
          </label>
          <p id={`${headingId}-monitor-hint`} className="text-slate-600">
            We check TfL every few minutes and alert you when a disruption affects this route's lines or stations.
          </p>
        </div>
      </div>

      <div className="mt-4 flex flex-wrap items-center gap-2">
        {search ? (
          <Link
            to={`/?${toUrlParams(search).toString()}`}
            className="inline-flex items-center gap-1.5 rounded-lg bg-brand px-3 py-1.5 text-sm font-semibold text-white hover:bg-brand-dark"
          >
            <Route aria-hidden="true" className="size-4" />
            Plan this journey
          </Link>
        ) : (
          <p className="text-sm text-slate-600">TfL can't plan to or from one of these stations right now.</p>
        )}
        {!renaming && (
          <button
            type="button"
            onClick={() => setRenaming(true)}
            aria-label={`Rename ${journey.name}`}
            className="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-navy-900 ring-1 ring-navy-200 hover:bg-navy-50"
          >
            <Pencil aria-hidden="true" className="size-4" />
            Rename
          </button>
        )}
        {confirmingDelete ? (
          <span className="inline-flex items-center gap-2" role="group" aria-label="Confirm delete">
            <span className="text-sm text-red-800">Delete “{journey.name}”?</span>
            <button
              type="button"
              disabled={remove.isPending}
              onClick={() => remove.mutate(journey.id)}
              className="rounded-lg bg-red-700 px-3 py-1.5 text-sm font-semibold text-white hover:bg-red-800"
            >
              Yes, delete
            </button>
            <button type="button" onClick={() => setConfirmingDelete(false)} className="rounded-lg px-3 py-1.5 text-sm font-medium ring-1 ring-navy-200">
              Keep
            </button>
          </span>
        ) : (
          <button
            type="button"
            onClick={() => setConfirmingDelete(true)}
            aria-label={`Delete ${journey.name}`}
            className="inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-red-800 ring-1 ring-red-200 hover:bg-red-50"
          >
            <Trash2 aria-hidden="true" className="size-4" />
            Delete
          </button>
        )}
      </div>
      {(remove.error || (update.error && !renaming)) && (
        <div className="mt-3">
          <FormError error={remove.error ?? update.error} />
        </div>
      )}
    </article>
  )
}
