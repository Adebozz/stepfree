import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'

import { RequireAuth } from '../components/RequireAuth'
import { savedCommute, signedInUser } from '../test/fixtures'
import { live, mockApi, noContent, problem, renderWithProviders } from '../test/render'
import { SavedJourneysPage } from './SavedJourneysPage'

function renderSaved() {
  return renderWithProviders(
    <Routes>
      <Route
        path="/saved"
        element={
          <RequireAuth>
            <SavedJourneysPage />
          </RequireAuth>
        }
      />
      <Route path="/sign-in" element={<p>Sign-in page</p>} />
    </Routes>,
    { route: '/saved' },
  )
}

describe('SavedJourneysPage', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('sends signed-out users to sign in', async () => {
    mockApi({ 'GET /auth/me': () => problem(401, {}) })
    renderSaved()

    expect(await screen.findByText('Sign-in page')).toBeInTheDocument()
  })

  it('lists journeys with a link that re-plans them', async () => {
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /saved-journeys': () => [savedCommute],
      'GET /lines/status': () => live([]),
    })
    renderSaved()

    const card = await screen.findByRole('article', { name: 'Home to work' })
    expect(within(card).getByText("Wembley Park to King's Cross & St Pancras International")).toBeInTheDocument()
    expect(within(card).getByText('Metropolitan')).toBeInTheDocument()
    expect(within(card).getByRole('checkbox', { name: 'Watch this journey for disruptions' })).toHaveAccessibleDescription(
      'Disruption alerts are coming in a later update; your choice is saved now.',
    )
    const plan = within(card).getByRole('link', { name: 'Plan this journey' })
    expect(plan.getAttribute('href')).toContain('from=1000257')
    expect(plan.getAttribute('href')).toContain('access=STEP_FREE_TO_PLATFORM')
  })

  it('asks for confirmation before deleting', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    const fetchMock = mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /saved-journeys': () => [savedCommute],
      'GET /lines/status': () => live([]),
      [`DELETE /saved-journeys/${savedCommute.id}`]: () => noContent(),
    })
    const user = userEvent.setup()
    renderSaved()

    await user.click(await screen.findByRole('button', { name: 'Delete Home to work' }))
    expect(fetchMock.mock.calls.some(([, init]) => init?.method === 'DELETE')).toBe(false)
    await user.click(screen.getByRole('button', { name: 'Yes, delete' }))

    expect(await screen.findByText('No saved journeys yet')).toBeInTheDocument()
  })

  it('shows an empty state with a way forward', async () => {
    mockApi({ 'GET /auth/me': () => signedInUser, 'GET /saved-journeys': () => [], 'GET /lines/status': () => live([]) })
    renderSaved()

    expect(await screen.findByText('No saved journeys yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Plan a journey' })).toHaveAttribute('href', '/')
  })

  it('renames a journey and keeps the card named while editing', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /saved-journeys': () => [savedCommute],
      'GET /lines/status': () => live([]),
      [`PATCH /saved-journeys/${savedCommute.id}`]: ({ body }) => ({ ...savedCommute, ...(body as object) }),
    })
    const user = userEvent.setup()
    renderSaved()

    await user.click(await screen.findByRole('button', { name: 'Rename Home to work' }))
    const card = screen.getByRole('article', { name: 'Home to work' })
    const field = within(card).getByLabelText('New name')
    await user.clear(field)
    await user.type(field, 'Office')
    await user.click(within(card).getByRole('button', { name: 'Save name' }))

    expect(await screen.findByRole('article', { name: 'Office' })).toBeInTheDocument()
  })

  it('toggles monitoring at once and rolls back if the server refuses', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    let respond: (response: Response) => void = () => {}
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /saved-journeys': () => [savedCommute],
      'GET /lines/status': () => live([]),
      [`PATCH /saved-journeys/${savedCommute.id}`]: () => new Promise<Response>((resolve) => (respond = resolve)),
    })
    const user = userEvent.setup()
    renderSaved()

    const checkbox = await screen.findByRole('checkbox', { name: 'Watch this journey for disruptions' })
    await user.click(checkbox)
    expect(checkbox).not.toBeChecked()

    respond(problem(500, { detail: 'Something went wrong on our side.' }))
    await waitFor(() => expect(checkbox).toBeChecked())
    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong on our side.')
  })
})
