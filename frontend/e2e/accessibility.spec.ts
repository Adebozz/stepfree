import AxeBuilder from '@axe-core/playwright'
import { expect, type Locator, type Page, test } from '@playwright/test'

import { MockApi } from './mockApi'

const JOURNEY_QUERY = 'from=1000257&fromId=940GZZLUWYP&fromName=Wembley+Park&to=1000129&toId=HUBKGX&toName=King%27s+Cross&access=STEP_FREE_TO_PLATFORM'

const pages = [
  { name: 'home', path: '/', ready: 'Plan step-free journeys across London', signedIn: false },
  { name: 'journey results', path: `/?${JOURNEY_QUERY}`, ready: 'View journey details', signedIn: false },
  { name: 'journey details with map', path: `/journey/0?${JOURNEY_QUERY}`, ready: 'Route map', signedIn: false },
  { name: 'live status', path: '/status', ready: 'Central', signedIn: false },
  { name: 'station', path: '/stations/HUBWMB', ready: 'Lift outages', signedIn: false },
  { name: 'sign in', path: '/sign-in', ready: 'Sign in', signedIn: false },
  { name: 'register', path: '/register', ready: 'Create an account', signedIn: false },
  { name: 'dashboard', path: '/dashboard', ready: 'Unread alerts', signedIn: true },
  { name: 'alerts', path: '/alerts', ready: 'Metropolitan: Minor Delays', signedIn: true },
  { name: 'saved journeys', path: '/saved', ready: 'Home to work', signedIn: true },
  { name: 'account', path: '/account', ready: 'Email me when an alert is created', signedIn: true },
  { name: 'not found', path: '/nowhere', ready: 'Page not found', signedIn: false },
]

for (const { name, path, ready, signedIn } of pages) {
  test(`${name} has no WCAG 2.1 A/AA violations, including colour contrast`, async ({ page }) => {
    await new MockApi({ signedIn }).install(page)
    // Cards fade in over 180 ms; axe would otherwise sample text mid-fade and report false contrast failures.
    await page.emulateMedia({ reducedMotion: 'reduce' })
    await page.goto(path)
    await expect(page.getByText(ready).first()).toBeVisible()

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      // Leaflet's tiles and controls are third-party; the route map has a text alternative (the itinerary).
      .exclude('.leaflet-container')
      .analyze()
    expect(results.violations.map((v) => `${v.id}: ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`)).toEqual([])
  })
}

test('skip link moves focus to the main content', async ({ page, isMobile }) => {
  test.skip(isMobile, 'keyboard test')
  await new MockApi().install(page)
  await page.goto('/')
  await page.keyboard.press('Tab')
  const skip = page.getByRole('link', { name: 'Skip to main content' })
  await expect(skip).toBeFocused()
  await expect(skip).toBeVisible()
  await page.keyboard.press('Enter')
  await expect(page.locator('main')).toBeFocused()
})

test('a journey can be planned with the keyboard alone', async ({ page, isMobile }) => {
  test.skip(isMobile, 'keyboard test')
  await new MockApi().install(page)
  await page.goto('/')

  await page.getByRole('combobox', { name: 'From' }).focus()
  await page.keyboard.type('Wembley')
  await expect(page.getByRole('option', { name: /Wembley Park/ })).toBeVisible()
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')
  await tabUntilFocused(page, page.getByRole('combobox', { name: 'To' }))
  await page.keyboard.type('Kings')
  await expect(page.getByRole('option', { name: /King's Cross/ })).toBeVisible()
  await page.keyboard.press('ArrowDown')
  await page.keyboard.press('Enter')

  await tabUntilFocused(page, page.getByRole('button', { name: 'Find journeys' }))
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/from=1000257/)
  await expect(page.getByRole('link', { name: /View journey details/ }).first()).toBeVisible()
})

/** Presses Tab until `target` has focus (bounded), proving it is reachable in tab order. */
async function tabUntilFocused(page: Page, target: Locator) {
  for (let i = 0; i < 30 && !(await target.evaluate((el) => el === document.activeElement)); i++) {
    await page.keyboard.press('Tab')
  }
  await expect(target).toBeFocused()
}
