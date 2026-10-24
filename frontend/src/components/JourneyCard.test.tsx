import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { renderWithProviders } from '../test/render'
import { disruptedJourney, stepFreeJourney } from '../test/fixtures'
import { JourneyCard } from './JourneyCard'

describe('JourneyCard', () => {
  it('shows London times, duration, route and journey facts', () => {
    renderWithProviders(<JourneyCard journey={stepFreeJourney} index={0} detailsHref="/journey/0?x=1" />)

    // 16:24 UTC is 17:24 in London (BST).
    expect(screen.getByRole('heading', { name: 'Option 1: 17:24 – 17:58' })).toBeInTheDocument()
    expect(screen.getByText('34 min')).toBeInTheDocument()
    expect(screen.getByText('Walk 5 min')).toBeInTheDocument()
    expect(screen.getByText('Metropolitan')).toBeInTheDocument()
    expect(screen.getByText('No changes')).toBeInTheDocument()
    expect(screen.getByText('12 min walking')).toBeInTheDocument()
    expect(screen.getByText('£3.60 pay as you go')).toBeInTheDocument()
    expect(screen.getByText('TfL reports 3 lifts, 1 walkway')).toBeInTheDocument()
    expect(screen.queryByText('Disruption reported on this route')).not.toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'View journey details for option 1' })).toHaveAttribute('href', '/journey/0?x=1')
  })

  it('flags disruption and never claims a route is obstacle-free', () => {
    renderWithProviders(<JourneyCard journey={disruptedJourney} index={1} detailsHref="/journey/1" />)

    expect(screen.getByText('Disruption reported on this route')).toBeInTheDocument()
    expect(screen.getByText('1 change')).toBeInTheDocument()
    expect(screen.getByText('No lifts, stairs or escalators reported by TfL')).toBeInTheDocument()
    expect(screen.queryByText(/pay as you go/)).not.toBeInTheDocument()
  })
})
