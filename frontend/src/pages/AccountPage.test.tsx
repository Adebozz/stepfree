import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'

import { signedInUser } from '../test/fixtures'
import { mockApi, problem, renderWithProviders } from '../test/render'
import { AccountPage } from './AccountPage'

describe('AccountPage alert settings', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('shows the defaults and saves a change with both settings', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    let saved: unknown
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /notification-preferences': () => ({ emailEnabled: false, monitoringEnabled: true }),
      'PUT /notification-preferences': ({ body }) => {
        saved = body
        return body
      },
    })
    renderWithProviders(<AccountPage />)

    const email = await screen.findByRole('checkbox', { name: 'Email me when an alert is created' })
    expect(email).not.toBeChecked()
    expect(email).toHaveAccessibleDescription('Sent to ada@example.com. Alerts always appear on your Alerts page.')
    expect(screen.getByRole('checkbox', { name: 'Check my saved journeys for disruptions' })).toBeChecked()

    await userEvent.click(email)

    expect(email).toBeChecked()
    await waitFor(() => expect(saved).toEqual({ emailEnabled: true, monitoringEnabled: true }))
  })

  it('rolls the switch back and explains when saving fails', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /notification-preferences': () => ({ emailEnabled: false, monitoringEnabled: true }),
      'PUT /notification-preferences': () => problem(500, { title: 'Error', detail: 'Something went wrong on our side.' }),
    })
    renderWithProviders(<AccountPage />)

    const monitoring = await screen.findByRole('checkbox', { name: 'Check my saved journeys for disruptions' })
    await userEvent.click(monitoring)

    expect(await screen.findByText('Something went wrong on our side.')).toBeInTheDocument()
    expect(monitoring).toBeChecked()
  })
})
