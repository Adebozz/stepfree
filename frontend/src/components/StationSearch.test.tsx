import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'

import type { StationSummary } from '../api/types'
import type { StationRef } from '../features/journey/search'
import { live, mockFetch, renderWithProviders } from '../test/render'
import { StationSearch } from './StationSearch'

const wembley: StationSummary[] = [
  { id: 'HUBWMB', icsCode: '1000256', name: 'Wembley Central', modes: ['national-rail', 'overground', 'tube'], zone: '4', latitude: 51.55, longitude: -0.29, hub: true },
  { id: '940GZZLUWYP', icsCode: '1000257', name: 'Wembley Park', modes: ['tube'], zone: '4', latitude: 51.56, longitude: -0.27, hub: false },
  { id: '910GNOICS', icsCode: null, name: 'No Ics Halt', modes: ['national-rail'], zone: null, latitude: null, longitude: null, hub: false },
]

function Harness({ onChange }: { onChange: (station: StationRef | null) => void }) {
  const [value, setValue] = useState<StationRef | null>(null)
  return (
    <StationSearch
      label="From"
      value={value}
      onChange={(station) => {
        setValue(station)
        onChange(station)
      }}
    />
  )
}

describe('StationSearch', () => {
  it('shows matching stations and selects one with the keyboard', async () => {
    const fetchMock = mockFetch({ '/stations': live(wembley) })
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<Harness onChange={onChange} />)

    const input = screen.getByRole('combobox', { name: 'From' })
    await user.type(input, 'Wembley')

    const options = await screen.findAllByRole('option')
    expect(options).toHaveLength(3)
    expect(options[0]).toHaveTextContent('Interchange · National Rail, Overground, Underground · Zone 4')
    expect(screen.getByText(/3 stations found/)).toBeInTheDocument()
    expect(String(fetchMock.mock.calls.at(-1)?.[0])).toContain('/api/v1/stations?query=Wembley')

    await user.keyboard('{ArrowDown}{ArrowDown}')
    expect(input).toHaveAttribute('aria-activedescendant', options[1]!.id)
    await user.keyboard('{Enter}')

    expect(onChange).toHaveBeenLastCalledWith({ id: '940GZZLUWYP', icsCode: '1000257', name: 'Wembley Park' })
    expect(input).toHaveValue('Wembley Park')
    expect(input).toHaveAttribute('aria-expanded', 'false')
  })

  it('does not search for fewer than two characters', async () => {
    const fetchMock = mockFetch({ '/stations': live(wembley) })
    const user = userEvent.setup()
    renderWithProviders(<Harness onChange={vi.fn()} />)

    await user.type(screen.getByRole('combobox', { name: 'From' }), 'W')
    await new Promise((resolve) => setTimeout(resolve, 350))

    expect(fetchMock).not.toHaveBeenCalled()
    expect(screen.queryByRole('option')).not.toBeInTheDocument()
  })

  it('marks stations that cannot be used for journey planning and ignores clicks on them', async () => {
    mockFetch({ '/stations': live(wembley) })
    const onChange = vi.fn()
    const user = userEvent.setup()
    renderWithProviders(<Harness onChange={onChange} />)

    await user.type(screen.getByRole('combobox', { name: 'From' }), 'Halt')
    const option = await screen.findByRole('option', { name: /No Ics Halt/ })
    expect(option).toHaveAttribute('aria-disabled', 'true')
    expect(option).toHaveTextContent('Journey planning unavailable')

    await user.click(option)
    expect(onChange).not.toHaveBeenCalledWith(expect.objectContaining({ id: '910GNOICS' }))
  })

  it('says so when nothing matches', async () => {
    mockFetch({ '/stations': live([]) })
    const user = userEvent.setup()
    renderWithProviders(<Harness onChange={vi.fn()} />)

    await user.type(screen.getByRole('combobox', { name: 'From' }), 'Zzqq')

    expect(await screen.findByText('No stations match that name.')).toBeInTheDocument()
  })

  it('reports search failures instead of showing an empty list', async () => {
    mockFetch({
      '/stations': () => new Response(JSON.stringify({ detail: 'TfL down' }), { status: 503, headers: { 'Content-Type': 'application/problem+json' } }),
    })
    const user = userEvent.setup()
    renderWithProviders(<Harness onChange={vi.fn()} />)

    await user.type(screen.getByRole('combobox', { name: 'From' }), 'Wembley')

    await waitFor(() => expect(screen.getByText('Station search is unavailable right now.')).toBeInTheDocument())
  })
})
