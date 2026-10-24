import { expect, test } from '@playwright/test'

/**
 * The real stack: Vite → Spring Boot → PostgreSQL, live TfL, Mailpit for email. Run with
 * `npm run test:e2e:live` after `docker compose up -d` and starting the backend with
 * MONITORING_INTERVAL=15s (so an alert appears within the test), or set E2E_TRIGGER_TOKEN to the
 * server's MONITORING_TRIGGER_TOKEN to run a cycle on demand. E2E_BASE_URL targets another server
 * (e.g. the Docker image). Not run in CI: it depends on TfL's live data.
 */
test('register, plan, map, save, get alerted, read the alert, delete the account', async ({ page, isMobile }) => {
  test.skip(isMobile, 'one live run is enough')
  // OSM tile policy: no automated tile traffic.
  await page.route('https://tile.openstreetmap.org/**', (route) => route.fulfill({ status: 204 }))
  // Any console error fails the run, including Content-Security-Policy violations in the production image.
  const consoleErrors: string[] = []
  page.on('console', (message) => {
    // Signed-out /auth/me answers 401 by design (see the checklist's known issues).
    if (message.type() === 'error' && !message.text().includes('401')) consoleErrors.push(message.text())
  })
  page.on('pageerror', (error) => consoleErrors.push(error.message))
  const email = `live-e2e-${Date.now()}@example.com`
  const password = 'correct horse battery'

  await page.goto('/register')
  await page.getByLabel('Full name').fill('Live Tester')
  await page.getByLabel('Email').fill(email)
  await page.getByLabel('Password', { exact: true }).fill(password)
  await page.getByLabel('Confirm password').fill(password)
  await page.getByRole('button', { name: 'Create account' }).click()
  await expect(page.getByRole('link', { name: 'Dashboard' })).toBeVisible()

  await page.goto('/account')
  await page.getByRole('checkbox', { name: 'Email me when an alert is created' }).click()
  await expect(page.getByRole('checkbox', { name: 'Email me when an alert is created' })).toBeChecked()

  await page.goto('/')
  await page.getByRole('combobox', { name: 'From' }).fill('Wembley Park')
  await page.getByRole('option', { name: /Wembley Park/ }).first().click()
  await page.getByRole('combobox', { name: 'To' }).fill("King's Cross")
  await page.getByRole('option', { name: /King's Cross/ }).first().click()
  await page.getByText('Step-free to platform').click()
  await page.getByRole('button', { name: 'Find journeys' }).click()
  await page.getByRole('link', { name: /View journey details/ }).first().click()

  const map = page.getByRole('region', { name: 'Route map' })
  await expect(map.locator('.leaflet-container')).toBeVisible()
  // Some legs are only a few pixels long (a short walk), so count drawn shapes rather than test visibility.
  await expect.poll(() => map.locator('path.leaflet-interactive').count()).toBeGreaterThan(1)
  await page.screenshot({ path: 'test-results/live-journey-map.png', fullPage: true })

  await page.getByLabel('Name').fill('Live commute')
  await page.getByRole('button', { name: 'Save journey' }).click()
  await expect(page.getByText('Saved as “Live commute”.')).toBeVisible()

  // The scheduler runs every 15 s in this setup; the alerts page refreshes every minute, so reload.
  const triggerToken = process.env.E2E_TRIGGER_TOKEN
  if (triggerToken) {
    const run = await page.request.post('/internal/monitoring/run', { headers: { 'X-Monitoring-Token': triggerToken } })
    expect(run.status()).toBe(200)
  }
  await page.goto('/alerts')
  await expect(async () => {
    await page.reload()
    await expect(page.getByRole('article').first()).toBeVisible({ timeout: 2_000 })
  }).toPass({ timeout: 90_000, intervals: [5_000] })
  await expect(page.getByText(/Last checked against TfL at/)).toBeVisible()
  await page.screenshot({ path: 'test-results/live-alerts.png', fullPage: true })

  const unread = await page.getByRole('article').count()
  await page.getByRole('button', { name: 'Mark all as read' }).click()
  await expect(page.getByRole('status')).toHaveText('No unread alerts')

  await page.goto('/dashboard')
  await expect(page.getByRole('list', { name: 'Lines on Live commute' })).toBeVisible()
  await page.screenshot({ path: 'test-results/live-dashboard.png', fullPage: true })

  const inbox = await (await page.request.get('http://localhost:8025/api/v1/messages')).json()
  const mine = inbox.messages.filter((m: { To: { Address: string }[] }) => m.To.some((t) => t.Address === email))
  expect(mine.length).toBe(unread)

  await page.goto('/account')
  await page.getByLabel('Password', { exact: true }).last().fill(password)
  await page.getByRole('checkbox', { name: /I understand/ }).check()
  await page.getByRole('button', { name: 'Delete my account' }).click()
  await expect(page.getByRole('link', { name: 'Sign in' })).toBeVisible()
  expect(consoleErrors).toEqual([])
})
