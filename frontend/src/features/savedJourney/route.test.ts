import { describe, expect, it } from 'vitest'

import type { JourneyLeg } from '../../api/types'
import { savedCommute, stepFreeJourney } from '../../test/fixtures'
import { defaultJourneyName, routeFromJourney, searchFromSaved } from './route'

const wembleyPark = { id: '940GZZLUWYP', icsCode: '1000257', name: 'Wembley Park' }
const kingsCross = { id: 'HUBKGX', icsCode: '1000129', name: "King's Cross & St Pancras International" }
const tubeLeg = stepFreeJourney.legs[1]!

describe('routeFromJourney', () => {
  it('records lines and every stop with its role, keeping hub ids from the search', () => {
    const route = routeFromJourney({ from: wembleyPark, to: kingsCross }, stepFreeJourney)

    expect(route.lineIds).toEqual(['metropolitan'])
    expect(route.stations).toEqual([
      { id: '940GZZLUWYP', role: 'ORIGIN' },
      { id: '940GZZLUFYR', role: 'INTERMEDIATE' },
      { id: '940GZZLUKSX', role: 'DESTINATION' },
      { id: 'HUBKGX', role: 'DESTINATION' },
    ])
  })

  it('marks stops between two lines as interchanges', () => {
    const secondLeg: JourneyLeg = {
      ...tubeLeg,
      lineId: 'victoria',
      from: { id: '940GZZLUKSX', name: "King's Cross", latitude: null, longitude: null },
      to: { id: '940GZZLUBXN', name: 'Brixton', latitude: null, longitude: null },
      intermediateStops: [],
    }
    const journey = { ...stepFreeJourney, lineIds: ['metropolitan', 'victoria'], legs: [...stepFreeJourney.legs, secondLeg] }
    const brixton = { id: '940GZZLUBXN', icsCode: '1000033', name: 'Brixton' }

    const route = routeFromJourney({ from: wembleyPark, to: brixton }, journey)

    expect(route.stations.find((stop) => stop.id === '940GZZLUKSX')?.role).toBe('INTERCHANGE')
    expect(route.stations.at(-1)).toEqual({ id: '940GZZLUBXN', role: 'DESTINATION' })
    expect(route.lineIds).toEqual(['metropolitan', 'victoria'])
  })

  it('drops ids the API would reject instead of failing the save', () => {
    const odd: JourneyLeg = { ...tubeLeg, intermediateStops: [{ id: 'not a stop id!', name: 'Odd', latitude: null, longitude: null }] }
    const journey = { ...stepFreeJourney, lineIds: ['metropolitan', 'Bad Line'], legs: [odd] }

    const route = routeFromJourney({ from: wembleyPark, to: kingsCross }, journey)

    expect(route.lineIds).toEqual(['metropolitan'])
    expect(route.stations.map((stop) => stop.id)).not.toContain('not a stop id!')
  })

  it('still has origin and destination for a walking-only journey', () => {
    const walking = { ...stepFreeJourney, lineIds: [], legs: [stepFreeJourney.legs[0]!] }

    expect(routeFromJourney({ from: wembleyPark, to: kingsCross }, walking).stations).toEqual([
      { id: '940GZZLUWYP', role: 'ORIGIN' },
      { id: 'HUBKGX', role: 'DESTINATION' },
    ])
  })
})

describe('saved journey helpers', () => {
  it('suggests a name within the 80-character limit', () => {
    expect(defaultJourneyName({ from: wembleyPark, to: kingsCross })).toBe("Wembley Park to King's Cross & St Pancras International")
    expect(defaultJourneyName({ from: { ...wembleyPark, name: 'x'.repeat(90) }, to: kingsCross })).toHaveLength(80)
  })

  it('re-plans a saved journey only when TfL gave both ICS codes', () => {
    expect(searchFromSaved(savedCommute)).toMatchObject({ from: { icsCode: '1000257' }, to: { id: 'HUBKGX' }, accessibility: ['STEP_FREE_TO_PLATFORM'] })
    expect(searchFromSaved({ ...savedCommute, destination: { ...savedCommute.destination, icsCode: null } })).toBeNull()
  })
})
