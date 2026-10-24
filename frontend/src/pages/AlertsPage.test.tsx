import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'

import { RequireAuth } from '../components/RequireAuth'
import { axeViolations } from '../test/axe'
import { metropolitanAlert, resolvedLiftAlert, signedInUser } from '../test/fixtures'
import { mockApi, noContent, renderWithProviders } from '../test/render'
import { AlertsPage } from './AlertsPage'

const status = { schedulerEnabled: true, lastCheckedAt: '2026-10-24T14:21:26Z', lastStatus: 'SUCCEEDED' }

function renderAlerts() {
  return renderWithProviders(
    <RequireAuth>
      <AlertsPage />
    </RequireAuth>,
  )
}

describe('AlertsPage', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('lists alerts with their type, journey and TfL text, and when journeys were last checked', async () => {
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /alerts': () => ({ alerts: [metropolitanAlert, resolvedLiftAlert], unreadCount: 1 }),
      'GET /monitoring/status': () => status,
    })
    const { container } = renderAlerts()

    const alert = await screen.findByRole('article', { name: 'Metropolitan: Minor Delays' })
    expect(within(alert).getByText('New disruption')).toBeInTheDocument()
    expect(within(alert).getByText('Unread')).toBeInTheDocument()
    expect(within(alert).getByText('Saved journey: Home to work')).toBeInTheDocument()
    expect(within(alert).getByText(/Minor delays between Harrow-on-the-Hill/)).toBeInTheDocument()
    expect(within(screen.getByRole('article', { name: 'No longer reported: Lift out of service' })).getByText('No longer reported'))
      .toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('1 unread alert')
    expect(await screen.findByText(/Last checked against TfL at/)).toBeInTheDocument()
    expect(await axeViolations(container)).toEqual([])
  })

  it('marks one alert as read and updates the count', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    const marked: string[] = []
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /alerts': () => ({ alerts: [metropolitanAlert], unreadCount: 1 }),
      'GET /monitoring/status': () => status,
      [`POST /alerts/${metropolitanAlert.id}/read`]: () => {
        marked.push(metropolitanAlert.id)
        return noContent()
      },
    })
    renderAlerts()

    await userEvent.click(await screen.findByRole('button', { name: 'Mark "Metropolitan: Minor Delays" as read' }))

    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('No unread alerts'))
    expect(marked).toEqual([metropolitanAlert.id])
    expect(screen.queryByText('Unread')).not.toBeInTheDocument()
  })

  it('filters to unread and marks all as read', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /alerts': () => ({ alerts: [metropolitanAlert, resolvedLiftAlert], unreadCount: 1 }),
      'GET /monitoring/status': () => status,
      'POST /alerts/read-all': () => noContent(),
    })
    renderAlerts()

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Show unread only' }))
    expect(screen.queryByRole('article', { name: 'No longer reported: Lift out of service' })).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Mark all as read' }))
    expect(await screen.findByText("You've read every alert.")).toBeInTheDocument()
  })

  it('explains the empty state and warns when TfL could not be read', async () => {
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /alerts': () => ({ alerts: [], unreadCount: 0 }),
      'GET /monitoring/status': () => ({ ...status, lastStatus: 'PARTIAL' }),
    })
    renderAlerts()

    expect(await screen.findByText('No alerts yet')).toBeInTheDocument()
    expect(await screen.findByText(/Some TfL feeds were unavailable, so some disruptions may be missing\./)).toBeInTheDocument()
  })
})
