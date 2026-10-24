import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { wembleyCentralAccessibility } from '../test/fixtures'
import { renderWithProviders } from '../test/render'
import { StationInformation } from './StationInformation'

describe('StationInformation', () => {
  it('shows missing TfL data as "Information unavailable", never as "No"', () => {
    renderWithProviders(<StationInformation station={wembleyCentralAccessibility} />)

    const facts = screen.getByLabelText('Accessibility at Wembley Central')
    expect(within(facts).getAllByText('Information unavailable')).toHaveLength(2)
    expect(within(facts).queryByText('No')).not.toBeInTheDocument()
    expect(screen.getByText(/TfL doesn't publish accessibility details for this station/)).toBeInTheDocument()
  })

  it('does not read missing lift data as "all lifts working"', () => {
    renderWithProviders(<StationInformation station={wembleyCentralAccessibility} />)

    expect(screen.getByText('Lift status is unavailable right now. This does not mean all lifts are working.')).toBeInTheDocument()
    expect(screen.queryByText(/no lift outages/i)).not.toBeInTheDocument()
  })

  it('shows accessibility-related disruptions', () => {
    renderWithProviders(<StationInformation station={wembleyCentralAccessibility} />)

    expect(screen.getByText(/No Step Free Access - Step free access is not available/)).toBeInTheDocument()
    expect(screen.getByText('(affects accessibility)')).toBeInTheDocument()
  })

  it('confirms when TfL explicitly reports no lift outages', () => {
    renderWithProviders(<StationInformation station={{ ...wembleyCentralAccessibility, liftDataAvailable: true, liftOutages: [] }} />)

    expect(screen.getByText('TfL reports no lift outages at this station.')).toBeInTheDocument()
  })

  it('marks TfL notes that were cut off at 255 characters', () => {
    const cut = `${'x'.repeat(240)} continue to Ki`
    const section = wembleyCentralAccessibility.stations[0]!
    renderWithProviders(
      <StationInformation station={{ ...wembleyCentralAccessibility, stations: [{ ...section, additionalInformation: cut }] }} />,
    )

    expect(screen.getByText("(TfL's text ends here)")).toBeInTheDocument()
  })

  it('says "no other disruptions" when lift outages are already listed', () => {
    renderWithProviders(
      <StationInformation
        station={{
          ...wembleyCentralAccessibility,
          liftDataAvailable: true,
          liftOutages: [{ stationId: '940GZZLUWYC', liftIds: ['lift-1'], message: 'Lift out of order' }],
          disruptionDataAvailable: true,
          disruptions: [],
        }}
      />,
    )

    expect(screen.getByText('TfL reports no other disruptions at this station.')).toBeInTheDocument()
  })
})
