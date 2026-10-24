import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'

import type { CreateSavedJourney } from '../api/types'
import { savedCommute, signedInUser, stepFreeJourney } from '../test/fixtures'
import { mockApi, problem, renderWithProviders } from '../test/render'
import { SaveJourneyPanel } from './SaveJourneyPanel'

const search = {
  from: { id: '940GZZLUWYP', icsCode: '1000257', name: 'Wembley Park' },
  to: { id: 'HUBKGX', icsCode: '1000129', name: "King's Cross & St Pancras International" },
  accessibility: ['STEP_FREE_TO_PLATFORM' as const],
  arriveBy: false,
}

describe('SaveJourneyPanel', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('asks a signed-out user to sign in and come back', async () => {
    mockApi({ 'GET /auth/me': () => problem(401, {}) })
    renderWithProviders(<SaveJourneyPanel search={search} journey={stepFreeJourney} />, { route: '/journey/0?from=1000257' })

    const link = await screen.findByRole('link', { name: 'Sign in' })
    expect(link).toHaveAttribute('href', '/sign-in?next=%2Fjourney%2F0%3Ffrom%3D1000257')
  })

  it('saves the chosen route with the search preferences', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    let sent: CreateSavedJourney | undefined
    mockApi({
      'GET /auth/me': () => signedInUser,
      'POST /saved-journeys': ({ body }) => {
        sent = body as CreateSavedJourney
        return new Response(JSON.stringify({ ...savedCommute, name: sent.name }), { status: 201, headers: { 'Content-Type': 'application/json' } })
      },
    })
    const user = userEvent.setup()
    renderWithProviders(<SaveJourneyPanel search={search} journey={stepFreeJourney} />)

    const name = await screen.findByLabelText('Name')
    expect(name).toHaveValue("Wembley Park to King's Cross & St Pancras International")
    await user.clear(name)
    await user.type(name, 'Commute')
    await user.click(screen.getByRole('button', { name: 'Save journey' }))

    expect(await screen.findByText('Saved as “Commute”.')).toBeInTheDocument()
    expect(sent).toMatchObject({
      name: 'Commute',
      originId: '940GZZLUWYP',
      destinationId: 'HUBKGX',
      accessibility: ['STEP_FREE_TO_PLATFORM'],
      route: { lineIds: ['metropolitan'] },
    })
    expect(sent?.route.stations[0]).toEqual({ id: '940GZZLUWYP', role: 'ORIGIN' })
  })

  it('explains a duplicate name', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    mockApi({
      'GET /auth/me': () => signedInUser,
      'POST /saved-journeys': () => problem(409, { detail: 'You already have a saved journey with this name.' }),
    })
    const user = userEvent.setup()
    renderWithProviders(<SaveJourneyPanel search={search} journey={stepFreeJourney} />)

    await user.click(await screen.findByRole('button', { name: 'Save journey' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('You already have a saved journey with this name.')
  })
})
