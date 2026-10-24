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

type ApiHandler = (request: { body: unknown; headers: Headers }) => Response | Promise<Response> | unknown

/**
 * Stubs fetch by exact "METHOD /path" (after /api/v1, query ignored). A handler returns a Response
 * or a JSON body (200). Unmatched requests get a 404 problem, so a missing mock fails loudly.
 */
export function mockApi(handlers: Record<string, ApiHandler>) {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost')
    const key = `${init?.method ?? 'GET'} ${url.pathname.replace('/api/v1', '')}`
    const handler = handlers[key]
    if (!handler) {
      return new Response(JSON.stringify({ title: `Not mocked: ${key}` }), { status: 404, headers: { 'Content-Type': 'application/problem+json' } })
    }
    const result = await handler({ body: init?.body ? JSON.parse(String(init.body)) : undefined, headers: new Headers(init?.headers) })
    if (result instanceof Response) return result
    return new Response(JSON.stringify(result), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

export function problem(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify({ status, ...body }), { status, headers: { 'Content-Type': 'application/problem+json' } })
}

export function noContent() {
  return new Response(null, { status: 204 })
}
