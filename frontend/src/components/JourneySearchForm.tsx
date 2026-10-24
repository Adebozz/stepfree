import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowDownUp, Search } from 'lucide-react'
import { Controller, useForm, useWatch } from 'react-hook-form'
import { z } from 'zod'

import type { AccessibilityPreference, JourneyPreference, WalkingSpeed } from '../api/types'
import { JOURNEY_PREFERENCE_OPTIONS, WALKING_SPEED_OPTIONS } from '../features/journey/options'
import type { JourneySearch, StationRef } from '../features/journey/search'
import { londonNowForInput } from '../utils/time'
import { AccessibilityPreferences } from './AccessibilityPreferences'
import { StationSearch } from './StationSearch'

const formSchema = z
  .object({
    from: z.custom<StationRef | null>(),
    to: z.custom<StationRef | null>(),
    accessibility: z.array(z.custom<AccessibilityPreference>()),
    preference: z.custom<JourneyPreference>(),
    walkingSpeed: z.custom<WalkingSpeed | ''>(),
    timeMode: z.enum(['now', 'depart', 'arrive']),
    time: z.string(),
  })
  .superRefine((values, ctx) => {
    if (!values.from) ctx.addIssue({ code: 'custom', path: ['from'], message: 'Choose a starting station' })
    if (!values.to) ctx.addIssue({ code: 'custom', path: ['to'], message: 'Choose a destination station' })
    if (values.from && values.to && values.from.icsCode === values.to.icsCode) {
      ctx.addIssue({ code: 'custom', path: ['to'], message: 'Choose a destination different from the start' })
    }
    if (values.timeMode !== 'now' && !values.time) {
      ctx.addIssue({ code: 'custom', path: ['time'], message: 'Choose a date and time' })
    }
  })

type FormValues = z.infer<typeof formSchema>

interface JourneySearchFormProps {
  initial: JourneySearch | null
  onSearch: (search: JourneySearch) => void
}

function defaults(initial: JourneySearch | null): FormValues {
  return {
    from: initial?.from ?? null,
    to: initial?.to ?? null,
    accessibility: initial?.accessibility ?? [],
    preference: initial?.preference ?? 'LEAST_TIME',
    walkingSpeed: initial?.walkingSpeed ?? '',
    timeMode: initial?.time ? (initial.arriveBy ? 'arrive' : 'depart') : 'now',
    time: initial?.time ?? '',
  }
}

export function JourneySearchForm({ initial, onSearch }: JourneySearchFormProps) {
  const {
    control,
    handleSubmit,
    register,
    getValues,
    setValue,
    formState: { errors, isSubmitted },
  } = useForm<FormValues>({ resolver: zodResolver(formSchema), defaultValues: defaults(initial) })
  const timeMode = useWatch({ control, name: 'timeMode' })

  function swap() {
    const { from, to } = getValues()
    setValue('from', to, { shouldValidate: isSubmitted })
    setValue('to', from, { shouldValidate: isSubmitted })
  }

  function submit(values: FormValues) {
    if (!values.from || !values.to) return
    onSearch({
      from: values.from,
      to: values.to,
      accessibility: values.accessibility,
      preference: values.preference === 'LEAST_TIME' ? undefined : values.preference,
      walkingSpeed: values.walkingSpeed || undefined,
      time: values.timeMode === 'now' ? undefined : values.time,
      arriveBy: values.timeMode === 'arrive',
    })
  }

  return (
    <form onSubmit={handleSubmit(submit)} noValidate aria-label="Plan a journey" className="space-y-5">
      <div className="grid items-start gap-3 sm:grid-cols-[1fr_auto_1fr]">
        <Controller
          control={control}
          name="from"
          render={({ field }) => (
            <StationSearch label="From" value={field.value} onChange={field.onChange} error={errors.from?.message} placeholder="e.g. Wembley Park" />
          )}
        />
        <button
          type="button"
          onClick={swap}
          className="mx-auto inline-flex size-10 items-center justify-center rounded-full border border-navy-200 bg-white text-navy-800 hover:bg-navy-50 sm:mt-6"
        >
          <ArrowDownUp aria-hidden="true" className="size-5 sm:rotate-90" />
          <span className="sr-only">Swap start and destination</span>
        </button>
        <Controller
          control={control}
          name="to"
          render={({ field }) => (
            <StationSearch label="To" value={field.value} onChange={field.onChange} error={errors.to?.message} placeholder="e.g. King's Cross" />
          )}
        />
      </div>

      <Controller
        control={control}
        name="accessibility"
        render={({ field }) => <AccessibilityPreferences value={field.value} onChange={field.onChange} />}
      />

      <details className="group rounded-lg border border-navy-200 bg-white" open={initial?.time !== undefined || undefined}>
        <summary className="cursor-pointer rounded-lg px-3 py-2.5 text-sm font-medium text-navy-900 hover:bg-navy-50">
          Time and route options
        </summary>
        <div className="grid gap-4 border-t border-navy-100 p-3 sm:grid-cols-3">
          <div>
            <label htmlFor="timeMode" className="block text-sm font-medium text-navy-900">
              When
            </label>
            <select id="timeMode" {...register('timeMode')} className="mt-1 block w-full rounded-lg border border-navy-200 bg-white px-3 py-2 text-base">
              <option value="now">Leave now</option>
              <option value="depart">Depart at</option>
              <option value="arrive">Arrive by</option>
            </select>
          </div>
          {timeMode !== 'now' && (
            <div>
              <label htmlFor="time" className="block text-sm font-medium text-navy-900">
                Date and time (London)
              </label>
              <input
                id="time"
                type="datetime-local"
                min={londonNowForInput()}
                {...register('time')}
                aria-invalid={errors.time ? true : undefined}
                aria-describedby={errors.time ? 'time-error' : undefined}
                className="mt-1 block w-full rounded-lg border border-navy-200 bg-white px-3 py-2 text-base"
              />
              {errors.time && (
                <p id="time-error" className="mt-1 text-sm font-medium text-red-700">
                  {errors.time.message}
                </p>
              )}
            </div>
          )}
          <div>
            <label htmlFor="preference" className="block text-sm font-medium text-navy-900">
              Prefer
            </label>
            <select id="preference" {...register('preference')} className="mt-1 block w-full rounded-lg border border-navy-200 bg-white px-3 py-2 text-base">
              {JOURNEY_PREFERENCE_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
          <div>
            <label htmlFor="walkingSpeed" className="block text-sm font-medium text-navy-900">
              Walking speed
            </label>
            <select id="walkingSpeed" {...register('walkingSpeed')} className="mt-1 block w-full rounded-lg border border-navy-200 bg-white px-3 py-2 text-base">
              <option value="">TfL default</option>
              {WALKING_SPEED_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </details>

      <button
        type="submit"
        className="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-brand px-5 py-3 text-base font-semibold text-white shadow-sm hover:bg-brand-dark sm:w-auto"
      >
        <Search aria-hidden="true" className="size-5" />
        Find journeys
      </button>
    </form>
  )
}
