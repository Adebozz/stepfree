import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { renderHook, waitFor } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'

import type { LineStatus } from '../api/types'
import { live, mockFetch } from '../test/render'
import { useJourneyLineStatuses } from './useJourneyLineStatuses'

const minorOnly: LineStatus = {
  id: 'metropolitan', name: 'Metropolitan', mode: 'tube', level: 'MINOR_DISRUPTION', summary: 'Minor Delays',
  statuses: [{ severityCode: 9, severityDescription: 'Minor Delays', level: 'MINOR_DISRUPTION', reason: 'Signal failure', validFrom: null, validTo: null, stepFreeAccessAffected: false }],
}
const fullStatus: LineStatus = { ...minorOnly, level: 'SEVERE_DISRUPTION', summary: 'Part Suspended' }
const bus18: LineStatus = { ...minorOnly, id: '18', name: '18', mode: 'bus' }

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>
}

describe('useJourneyLineStatuses', () => {
  // Regression: on 23 Oct 2026 the journey feed reported Metropolitan "Minor Delays" while the status feed
  // reported "Part Suspended" as well, so the page showed two different answers.
  it('prefers the complete status feed over the partial journey feed', async () => {
    mockFetch({ '/lines/status': live([fullStatus]) })

    const { result } = renderHook(() => useJourneyLineStatuses(['metropolitan', '18'], [minorOnly, bus18]), { wrapper })

    await waitFor(() => expect(result.current[0]?.summary).toBe('Part Suspended'))
    expect(result.current.map((line) => line.id)).toEqual(['metropolitan', '18'])
  })

  it('falls back to the journey feed when the status feed fails', async () => {
    mockFetch({ '/lines/status': () => new Response('{}', { status: 503, headers: { 'Content-Type': 'application/problem+json' } }) })

    const { result } = renderHook(() => useJourneyLineStatuses(['metropolitan'], [minorOnly]), { wrapper })

    expect(result.current).toEqual([minorOnly])
    await new Promise((resolve) => setTimeout(resolve, 50))
    expect(result.current).toEqual([minorOnly])
  })
})
