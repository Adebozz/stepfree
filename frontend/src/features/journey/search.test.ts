import { describe, expect, it } from 'vitest'

import { fromUrlParams, toApiParams, toUrlParams, type JourneySearch } from './search'

const search: JourneySearch = {
  from: { id: '940GZZLUWYP', icsCode: '1000257', name: 'Wembley Park' },
  to: { id: 'HUBKGX', icsCode: '1000129', name: "King's Cross & St Pancras International" },
  accessibility: ['STEP_FREE_TO_PLATFORM', 'NO_ESCALATORS'],
  preference: 'LEAST_WALKING',
  walkingSpeed: 'SLOW',
  time: '2026-10-24T08:30',
  arriveBy: true,
}

describe('journey search URL state', () => {
  it('round-trips through the URL', () => {
    expect(fromUrlParams(new URLSearchParams(toUrlParams(search).toString()))).toEqual(search)
  })

  it('omits optional values that were not chosen', () => {
    const minimal: JourneySearch = { ...search, accessibility: [], preference: undefined, walkingSpeed: undefined, time: undefined, arriveBy: false }
    const params = toUrlParams(minimal)
    expect([...params.keys()]).toEqual(['from', 'fromId', 'fromName', 'to', 'toId', 'toName'])
    expect(fromUrlParams(params)).toEqual(minimal)
  })

  it('rejects tampered or incomplete URLs instead of sending a bad request', () => {
    const tampered = toUrlParams(search)
    tampered.set('from', 'HUBWMB')
    expect(fromUrlParams(tampered)).toBeNull()

    const badPreference = toUrlParams(search)
    badPreference.set('access', 'STEP_FREE_TO_PLATFORM,FLYING')
    expect(fromUrlParams(badPreference)).toBeNull()

    expect(fromUrlParams(new URLSearchParams('from=1000257'))).toBeNull()
  })

  it('rejects the same station as origin and destination', () => {
    expect(fromUrlParams(toUrlParams({ ...search, to: search.from }))).toBeNull()
  })
})

describe('toApiParams', () => {
  it('builds the backend query with a stable preference order and seconds on the time', () => {
    const params = toApiParams(search)
    expect(params.get('from')).toBe('1000257')
    expect(params.get('to')).toBe('1000129')
    expect(params.get('accessibility')).toBe('NO_ESCALATORS,STEP_FREE_TO_PLATFORM')
    expect(params.get('preference')).toBe('LEAST_WALKING')
    expect(params.get('walkingSpeed')).toBe('SLOW')
    expect(params.get('time')).toBe('2026-10-24T08:30:00')
    expect(params.get('arriveBy')).toBe('true')
  })

  it('leaves out time entirely for "leave now"', () => {
    const params = toApiParams({ ...search, time: undefined, arriveBy: false })
    expect(params.has('time')).toBe(false)
    expect(params.has('arriveBy')).toBe(false)
  })
})
