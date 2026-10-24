import { expect, test } from '@playwright/test'

import { MockApi } from './mockApi'

test('sign in, read an alert, and change alert settings', async ({ page }) => {
  const api = new MockApi()
  await api.install(page)

  await page.goto('/alerts')
  await expect(page).toHaveURL(/\/sign-in\?next=%2Falerts/)
  await page.getByLabel('Email').fill('ada@example.com')
  await page.getByLabel('Password').fill('wrong password here')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page.getByText('The email or password is incorrect.')).toBeVisible()

  await page.getByLabel('Password').fill('correct horse battery')
  await page.getByRole('button', { name: 'Sign in' }).click()
  await expect(page).toHaveURL(/\/alerts$/)
  await expect(page.getByRole('link', { name: 'Alerts, 1 unread' })).toBeVisible()

  await page.getByRole('button', { name: 'Mark "Metropolitan: Minor Delays" as read' }).click()
  await expect(page.getByRole('status')).toHaveText('No unread alerts')
  await expect(page.getByRole('link', { name: 'Alerts', exact: true })).toBeVisible()

  await page.getByRole('link', { name: 'your account' }).click()
  const email = page.getByRole('checkbox', { name: 'Email me when an alert is created' })
  // click + assertion, not check(): the optimistic update lands a microtask after the click.
  await email.click()
  await expect(email).toBeChecked()
  await expect.poll(() => api.preferences.emailEnabled).toBe(true)
})

test('plan, see the route map, and save the journey', async ({ page }) => {
  const api = new MockApi({ signedIn: true })
  await api.install(page)

  await page.goto('/')
  await page.getByRole('combobox', { name: 'From' }).fill('Wembley')
  await page.getByRole('option', { name: /Wembley Park/ }).click()
  await page.getByRole('combobox', { name: 'To' }).fill('Kings')
  await page.getByRole('option', { name: /King's Cross/ }).click()
  await page.getByRole('button', { name: 'Find journeys' }).click()

  await page.getByRole('link', { name: /View journey details/ }).first().click()
  const map = page.getByRole('region', { name: 'Route map' })
  await expect(map.locator('.leaflet-container')).toBeVisible()
  // One TfL path drawn (the walking leg had no shape) and three stop markers.
  await expect(map.locator('path.leaflet-interactive')).toHaveCount(4)
  await expect(map.getByText(/had no shape from TfL and is not drawn/)).toBeVisible()
  await expect(map.getByRole('link', { name: 'OpenStreetMap' })).toBeVisible()

  await page.getByLabel('Name').fill('Match day')
  await page.getByRole('button', { name: 'Save journey' }).click()
  await expect(page.getByText('Saved as “Match day”.')).toBeVisible()
  expect(api.journeys.map((j) => j.name)).toContain('Match day')
})
