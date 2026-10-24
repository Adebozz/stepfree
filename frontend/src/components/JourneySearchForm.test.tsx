import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import type { JourneySearch } from '../features/journey/search'
import { renderWithProviders } from '../test/render'
import { JourneySearchForm } from './JourneySearchForm'

const wembleyPark = { id: '940GZZLUWYP', icsCode: '1000257', name: 'Wembley Park' }
const kingsCross = { id: 'HUBKGX', icsCode: '1000129', name: "King's Cross" }

function prefilled(overrides: Partial<JourneySearch> = {}): JourneySearch {
  return { from: wembleyPark, to: kingsCross, accessibility: [], arriveBy: false, ...overrides }
}

describe('JourneySearchForm', () => {
  it('requires both stations before searching', async () => {
    const onSearch = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<JourneySearchForm initial={null} onSearch={onSearch} />)

    await user.click(screen.getByRole('button', { name: 'Find journeys' }))

    expect(await screen.findByText('Choose a starting station')).toBeInTheDocument()
    expect(screen.getByText('Choose a destination station')).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: 'From' })).toHaveAttribute('aria-invalid', 'true')
    expect(onSearch).not.toHaveBeenCalled()
  })

  it('rejects the same station as origin and destination', async () => {
    const onSearch = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<JourneySearchForm initial={prefilled({ to: wembleyPark })} onSearch={onSearch} />)

    await user.click(screen.getByRole('button', { name: 'Find journeys' }))

    expect(await screen.findByText('Choose a destination different from the start')).toBeInTheDocument()
    expect(onSearch).not.toHaveBeenCalled()
  })

  it('submits chosen accessibility needs and options', async () => {
    const onSearch = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<JourneySearchForm initial={prefilled()} onSearch={onSearch} />)

    await user.click(screen.getByRole('checkbox', { name: /Step-free to platform/ }))
    await user.click(screen.getByRole('checkbox', { name: /Avoid escalators/ }))
    await user.click(screen.getByText('Time and route options'))
    await user.selectOptions(screen.getByLabelText('Prefer'), 'LEAST_WALKING')
    await user.selectOptions(screen.getByLabelText('Walking speed'), 'SLOW')
    await user.click(screen.getByRole('button', { name: 'Find journeys' }))

    expect(onSearch).toHaveBeenCalledWith({
      from: wembleyPark,
      to: kingsCross,
      accessibility: ['STEP_FREE_TO_PLATFORM', 'NO_ESCALATORS'],
      preference: 'LEAST_WALKING',
      walkingSpeed: 'SLOW',
      time: undefined,
      arriveBy: false,
    })
  })

  it('requires a time when departing or arriving at a set time', async () => {
    const onSearch = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<JourneySearchForm initial={prefilled()} onSearch={onSearch} />)

    await user.click(screen.getByText('Time and route options'))
    await user.selectOptions(screen.getByLabelText('When'), 'arrive')
    await user.click(screen.getByRole('button', { name: 'Find journeys' }))

    expect(await screen.findByText('Choose a date and time')).toBeInTheDocument()
    expect(onSearch).not.toHaveBeenCalled()
  })

  it('swaps origin and destination', async () => {
    const onSearch = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<JourneySearchForm initial={prefilled()} onSearch={onSearch} />)

    await user.click(screen.getByRole('button', { name: 'Swap start and destination' }))

    expect(screen.getByRole('combobox', { name: 'From' })).toHaveValue("King's Cross")
    expect(screen.getByRole('combobox', { name: 'To' })).toHaveValue('Wembley Park')
    await user.click(screen.getByRole('button', { name: 'Find journeys' }))
    expect(onSearch).toHaveBeenCalledWith(expect.objectContaining({ from: kingsCross, to: wembleyPark }))
  })
})
