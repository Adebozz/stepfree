import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { metropolitanAlert, signedInUser } from '../test/fixtures'
import { mockApi, problem, renderWithProviders } from '../test/render'
import { Navbar } from './Navbar'

describe('Navbar', () => {
  it('announces the unread alert count as part of the link name', async () => {
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /alerts': () => ({ alerts: [metropolitanAlert], unreadCount: 1 }),
    })
    renderWithProviders(<Navbar />)

    expect(await screen.findByRole('link', { name: 'Alerts, 1 unread' })).toHaveAttribute('href', '/alerts')
    expect(screen.getByRole('link', { name: 'Dashboard' })).toBeInTheDocument()
  })

  it('shows only public links and sign in when signed out, without asking for alerts', async () => {
    const fetchMock = mockApi({ 'GET /auth/me': () => problem(401, {}) })
    renderWithProviders(<Navbar />)

    expect(await screen.findByRole('link', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: /Alerts/ })).not.toBeInTheDocument()
    expect(fetchMock.mock.calls.map(([url]) => String(url))).not.toContainEqual(expect.stringContaining('/alerts'))
  })
})
