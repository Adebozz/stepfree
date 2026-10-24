import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

import { stepFreeJourney } from '../test/fixtures'
import { JourneyMapSection } from './JourneyMapSection'

// Leaflet needs a real layout engine; jsdom has none. The map itself is checked in the browser run.
vi.mock('./JourneyMap', () => ({ default: () => <div data-testid="leaflet-map" /> }))

describe('JourneyMapSection', () => {
  it('shows the map and says which parts TfL gave no shape for', async () => {
    render(<JourneyMapSection legs={stepFreeJourney.legs} />)

    expect(await screen.findByTestId('leaflet-map')).toBeInTheDocument()
    expect(screen.getByText(/1 part of the journey had no shape from TfL and is not drawn; we don't guess\./)).toBeInTheDocument()
  })

  it('shows no map rather than an invented one when TfL gave no geometry', () => {
    render(<JourneyMapSection legs={stepFreeJourney.legs.map((leg) => ({ ...leg, path: [] }))} />)

    expect(screen.queryByTestId('leaflet-map')).not.toBeInTheDocument()
    expect(screen.getByText(/TfL didn't provide a route shape for this journey/)).toBeInTheDocument()
  })
})
