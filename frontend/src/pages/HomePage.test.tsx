import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { centralLine, signedInUser } from '../test/fixtures'
import { live, mockApi, problem, renderWithProviders } from '../test/render'
import { HomePage } from './HomePage'

const SEARCH =
  '/?from=1000257&fromId=940GZZLUWYP&fromName=Wembley+Park&to=1000129&toId=HUBKGX&toName=King%27s+Cross'

function mockHome(me: () => unknown) {
  return mockApi({ 'GET /auth/me': me, 'GET /lines/status': () => live([centralLine]) })
}

describe('HomePage welcome', () => {
  it('explains the app and keeps the planner to a first-time visitor', async () => {
    mockHome(() => problem(401, {}))
    renderWithProviders(<HomePage />)

    expect(await screen.findByRole('heading', { name: 'What StepFree London does', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'How it works', level: 2 })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Find journeys' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Create a free account' })).toHaveAttribute('href', '/register')
  })

  it('steps aside for search results', async () => {
    const fetchMock = mockHome(() => problem(401, {}))
    renderWithProviders(<HomePage />, { route: SEARCH })

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    expect(screen.queryByRole('heading', { name: 'What StepFree London does' })).not.toBeInTheDocument()
  })

  it('steps aside for a signed-in user', async () => {
    const fetchMock = mockHome(() => signedInUser)
    renderWithProviders(<HomePage />)

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    await waitFor(() => expect(screen.queryByRole('heading', { name: 'What StepFree London does' })).not.toBeInTheDocument())
  })
})
