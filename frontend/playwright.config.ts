import { defineConfig, devices } from '@playwright/test'

/**
 * Browser tests. `e2e/*.spec.ts` run against the Vite dev server with the API mocked in the browser
 * (e2e/mockApi.ts), so they need no backend or TfL and run in CI. `e2e/live/` drives the real
 * stack and only runs with E2E_LIVE=1 (backend on :8080, see docs/testing.md).
 */
const live = process.env.E2E_LIVE === '1'
/** Point the live suite at another server, e.g. the Docker image on :8080. Vite is then not started. */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:5173'

export default defineConfig({
  testDir: live ? './e2e/live' : './e2e',
  testIgnore: live ? [] : ['**/live/**'],
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [['github'], ['html', { open: 'never' }]] : 'list',
  timeout: live ? 120_000 : 30_000,
  use: {
    baseURL,
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: 'npm run dev -- --strictPort',
        url: 'http://localhost:5173',
        reuseExistingServer: !process.env.CI,
      },
})
