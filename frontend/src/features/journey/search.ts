import { z } from 'zod'

import type { AccessibilityPreference, JourneyPreference, WalkingSpeed } from '../../api/types'

/** The minimum we need to know about a chosen station. */
export interface StationRef {
  id: string
  icsCode: string
  name: string
}

/**
 * A complete journey search. It lives in the URL (see toUrlParams/fromUrlParams) so results and
 * details pages can be bookmarked, shared and survive a refresh.
 */
export interface JourneySearch {
  from: StationRef
  to: StationRef
  accessibility: AccessibilityPreference[]
  preference?: JourneyPreference
  walkingSpeed?: WalkingSpeed
  /** London local "YYYY-MM-DDTHH:mm"; absent means leave now. */
  time?: string
  arriveBy: boolean
}

const accessibilityValues = ['STEP_FREE_TO_PLATFORM', 'STEP_FREE_TO_VEHICLE', 'NO_SOLID_STAIRS', 'NO_ESCALATORS', 'NO_ELEVATORS'] as const

const stationSchema = z.object({
  id: z.string().regex(/^[A-Za-z0-9]{3,20}$/),
  icsCode: z.string().regex(/^\d{4,12}$/),
  name: z.string().min(1).max(120),
})

const urlSchema = z.object({
  from: stationSchema,
  to: stationSchema,
  accessibility: z.array(z.enum(accessibilityValues)),
  preference: z.enum(['LEAST_TIME', 'LEAST_INTERCHANGE', 'LEAST_WALKING']).optional(),
  walkingSpeed: z.enum(['SLOW', 'AVERAGE', 'FAST']).optional(),
  time: z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/).optional(),
  arriveBy: z.boolean(),
})

export function toUrlParams(search: JourneySearch): URLSearchParams {
  const params = new URLSearchParams({
    from: search.from.icsCode,
    fromId: search.from.id,
    fromName: search.from.name,
    to: search.to.icsCode,
    toId: search.to.id,
    toName: search.to.name,
  })
  if (search.accessibility.length > 0) params.set('access', search.accessibility.join(','))
  if (search.preference) params.set('pref', search.preference)
  if (search.walkingSpeed) params.set('speed', search.walkingSpeed)
  if (search.time) {
    params.set('time', search.time)
    if (search.arriveBy) params.set('arriveBy', '1')
  }
  return params
}

/** Parses and validates a search from the URL. Anything malformed yields null, not a broken request. */
export function fromUrlParams(params: URLSearchParams): JourneySearch | null {
  const optional = (key: string) => params.get(key) || undefined
  const parsed = urlSchema.safeParse({
    from: { id: params.get('fromId'), icsCode: params.get('from'), name: params.get('fromName') },
    to: { id: params.get('toId'), icsCode: params.get('to'), name: params.get('toName') },
    accessibility: (params.get('access') ?? '').split(',').filter(Boolean),
    preference: optional('pref'),
    walkingSpeed: optional('speed'),
    time: optional('time'),
    arriveBy: params.get('arriveBy') === '1',
  })
  if (!parsed.success || parsed.data.from.icsCode === parsed.data.to.icsCode) {
    return null
  }
  return parsed.data
}

/** Query parameters for GET /api/v1/journeys. */
export function toApiParams(search: JourneySearch): URLSearchParams {
  const params = new URLSearchParams({ from: search.from.icsCode, to: search.to.icsCode })
  if (search.accessibility.length > 0) params.set('accessibility', [...search.accessibility].sort().join(','))
  if (search.preference) params.set('preference', search.preference)
  if (search.walkingSpeed) params.set('walkingSpeed', search.walkingSpeed)
  if (search.time) {
    params.set('time', `${search.time}:00`)
    params.set('arriveBy', String(search.arriveBy))
  }
  return params
}
