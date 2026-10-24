import { describe, expect, it } from 'vitest'

import { stepFreeJourney } from '../../test/fixtures'
import { bounds, legsWithoutGeometry, mapLegs, mapStops } from './geometry'

describe('map geometry', () => {
  it("draws only legs TfL gave a shape for, never a straight line for the rest", () => {
    const legs = mapLegs(stepFreeJourney.legs)

    expect(legs).toHaveLength(1)
    expect(legs[0]).toMatchObject({ lineId: 'metropolitan', walking: false, path: [[51.56, -0.28], [51.53, -0.12]] })
    // The walking transfer has from/to coordinates but no path: it is counted as missing, not drawn.
    expect(legsWithoutGeometry(stepFreeJourney.legs)).toBe(1)
  })

  it('drops points that cannot be in or near London', () => {
    const leg = { ...stepFreeJourney.legs[1]!, path: [[51.5, -0.1], [0, 0], [51.52, -0.13], [Number.NaN, -0.1]] as [number, number][] }
    expect(mapLegs([leg])[0]!.path).toEqual([[51.5, -0.1], [51.52, -0.13]])
    expect(mapLegs([{ ...leg, path: [[51.5, -0.1], [0, 0]] }])).toEqual([])
  })

  it('marks the start, changes and end where TfL gave coordinates', () => {
    expect(mapStops(stepFreeJourney.legs)).toEqual([
      { key: 'from-0', name: 'Wembley Park', position: [51.56, -0.28], kind: 'start' },
      { key: 'from-1', name: 'Wembley Park', position: [51.56, -0.28], kind: 'change' },
      { key: 'end', name: "King's Cross St. Pancras", position: [51.53, -0.12], kind: 'end' },
    ])
  })

  it('fits the bounds to every drawn point', () => {
    const legs = mapLegs(stepFreeJourney.legs)
    expect(bounds(legs, mapStops(stepFreeJourney.legs))).toEqual([[51.53, -0.28], [51.56, -0.12]])
    expect(bounds([], [])).toBeNull()
  })
})
