import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, type RenderOptions } from '@testing-library/react'
import type { ReactElement } from 'react'
import { MemoryRouter } from 'react-router'
import { vi } from 'vitest'

/** Renders with a fresh query client (no retries, no shared cache) and an in-memory router. */
export function renderWithProviders(ui: ReactElement, { route = '/', ...options }: RenderOptions & { route?: string } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </QueryClientProvider>,
    options,
  )
}

/** Stubs fetch: `routes` maps a path prefix (after /api/v1) to a JSON body or a Response factory. */
export function mockFetch(routes: Record<string, unknown | (() => Response)>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost')
    const path = url.pathname.replace('/api/v1', '')
    const match = Object.keys(routes)
      .sort((a, b) => b.length - a.length)
      .find((prefix) => path.startsWith(prefix))
    if (match === undefined) {
      return new Response(JSON.stringify({ title: 'Not mocked' }), { status: 404, headers: { 'Content-Type': 'application/problem+json' } })
    }
    const route = routes[match]
    if (typeof route === 'function') return (route as () => Response)()
    return new Response(JSON.stringify(route), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export function live<T>(data: T, stale = false) {
  return { data, retrievedAt: '2026-10-23T16:24:00Z', stale }
}
