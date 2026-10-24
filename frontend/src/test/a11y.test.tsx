import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import { JourneyDetails } from '../components/JourneyDetails'
import { JourneySearchForm } from '../components/JourneySearchForm'
import { StationInformation } from '../components/StationInformation'
import { AccountPage } from '../pages/AccountPage'
import { RegisterPage } from '../pages/RegisterPage'
import { SavedJourneysPage } from '../pages/SavedJourneysPage'
import { SignInPage } from '../pages/SignInPage'
import { StatusPage } from '../pages/StatusPage'
import { axeViolations } from './axe'
import { centralLine, savedCommute, signedInUser, stepFreeJourney, wembleyCentralAccessibility } from './fixtures'
import { live, mockApi, renderWithProviders } from './render'

/** Automated WCAG checks (axe-core) on every page's main content. Contrast is checked in the browser audit. */
describe('accessibility (axe)', () => {
  it('journey search form', async () => {
    mockApi({})
    const { container } = renderWithProviders(<JourneySearchForm initial={null} onSearch={() => {}} />)
    expect(await axeViolations(container)).toEqual([])
  })

  it('journey details', async () => {
    const { container } = renderWithProviders(<JourneyDetails journey={stepFreeJourney} />)
    expect(await axeViolations(container)).toEqual([])
  })

  it('station information', async () => {
    const { container } = renderWithProviders(<StationInformation station={wembleyCentralAccessibility} />)
    expect(await axeViolations(container)).toEqual([])
  })

  it('live status page', async () => {
    mockApi({ 'GET /lines/status': () => live([centralLine]), 'GET /disruptions': () => live({ stationDisruptions: [], liftOutages: [], liftDataAvailable: true }) })
    const { container } = renderWithProviders(<StatusPage />)
    await screen.findByText('Central')
    expect(await axeViolations(container)).toEqual([])
  })

  it('sign in and register', async () => {
    mockApi({})
    const signIn = renderWithProviders(<SignInPage />)
    expect(await axeViolations(signIn.container)).toEqual([])
    signIn.unmount()
    const register = renderWithProviders(<RegisterPage />)
    expect(await axeViolations(register.container)).toEqual([])
  })

  it('account page', async () => {
    mockApi({
      'GET /auth/me': () => signedInUser,
      'GET /notification-preferences': () => ({ emailEnabled: false, monitoringEnabled: true }),
    })
    const { container } = renderWithProviders(<AccountPage />)
    await screen.findByRole('checkbox', { name: 'Email me when an alert is created' })
    expect(await axeViolations(container)).toEqual([])
  })

  it('saved journeys page', async () => {
    mockApi({ 'GET /saved-journeys': () => [savedCommute], 'GET /lines/status': () => live([]) })
    const { container } = renderWithProviders(<SavedJourneysPage />)
    await screen.findByRole('article', { name: 'Home to work' })
    expect(await axeViolations(container)).toEqual([])
  })
})
