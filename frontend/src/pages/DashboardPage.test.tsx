import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { RequireAuth } from '../components/RequireAuth'
import { axeViolations } from '../test/axe'
import { metropolitanAlert, resolvedLiftAlert, savedCommute, signedInUser } from '../test/fixtures'
import { live, mockApi, renderWithProviders } from '../test/render'
import { DashboardPage } from './DashboardPage'

describe('DashboardPage', () => {
  it('shows unread alerts and the live status of each saved journey line', async () => {
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /alerts': () => ({ alerts: [metropolitanAlert, resolvedLiftAlert], unreadCount: 1 }),
      'GET /monitoring/status': () => ({ schedulerEnabled: true, lastCheckedAt: null, lastStatus: null }),
      'GET /saved-journeys': () => [savedCommute],
      'GET /lines/status': () =>
        live([{ id: 'metropolitan', name: 'Metropolitan', mode: 'tube', level: 'MINOR_DISRUPTION', summary: 'Minor Delays', statuses: [] }]),
    })
    const { container } = renderWithProviders(
      <RequireAuth>
        <DashboardPage />
      </RequireAuth>,
    )

    expect(await screen.findByRole('heading', { name: 'Hello, Ada' })).toBeInTheDocument()
    const alerts = screen.getByRole('region', { name: 'Unread alerts' })
    expect(await within(alerts).findByRole('article', { name: 'Metropolitan: Minor Delays' })).toBeInTheDocument()
    // Read alerts aren't repeated on the dashboard.
    expect(within(alerts).queryByRole('article', { name: /Lift out of service/ })).not.toBeInTheDocument()

    const lines = await screen.findByRole('list', { name: 'Lines on Home to work' })
    expect(await within(lines).findByText('Minor Delays')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Plan Home to work' }).getAttribute('href')).toContain('from=1000257')
    expect(screen.getByText(/will be checked against TfL within a few minutes/)).toBeInTheDocument()
    expect(await axeViolations(container)).toEqual([])
  })
})
