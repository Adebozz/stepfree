import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'

import { ApiError } from '../api/client'
import type { LineStatus } from '../api/types'
import { centralLine } from '../test/fixtures'
import { renderWithProviders } from '../test/render'
import { filterLines } from '../utils/serviceLevel'
import { ErrorState } from './ErrorState'
import { FreshnessNote } from './FreshnessNote'
import { LineStatusCard } from './LineStatusCard'

const unknownLine: LineStatus = { id: 'jubilee', name: 'Jubilee', mode: 'tube', level: 'UNKNOWN', summary: 'Status unavailable', statuses: [] }
const goodLine: LineStatus = { ...unknownLine, id: 'dlr', name: 'DLR', mode: 'dlr', level: 'GOOD_SERVICE', summary: 'Good Service' }

describe('LineStatusCard', () => {
  it('shows TfL wording with an icon and reveals the reason', async () => {
    const user = userEvent.setup()
    renderWithProviders(<LineStatusCard line={centralLine} />)

    expect(screen.getByRole('heading', { name: 'Central' })).toBeInTheDocument()
    expect(screen.getByText('Minor Delays')).toBeInTheDocument()
    await user.click(screen.getByText('Details'))
    expect(screen.getByText('Central Line: Minor delays due to train cancellations.')).toBeVisible()
  })

  it('never presents a missing status as good service', () => {
    renderWithProviders(<LineStatusCard line={unknownLine} />)

    expect(screen.getByText('Status unavailable')).toBeInTheDocument()
    expect(screen.queryByText(/good service/i)).not.toBeInTheDocument()
  })

  it('highlights step-free access problems', () => {
    const line: LineStatus = {
      ...centralLine,
      level: 'SEVERE_DISRUPTION',
      statuses: [{ ...centralLine.statuses[0]!, severityCode: 13, stepFreeAccessAffected: true }],
    }
    renderWithProviders(<LineStatusCard line={line} />)

    expect(screen.getByText('Step-free access affected')).toBeInTheDocument()
  })
})

describe('filterLines', () => {
  it('"with problems" keeps disrupted and unknown lines', () => {
    expect(filterLines([centralLine, unknownLine, goodLine], 'disrupted', 'all').map((l) => l.id)).toEqual(['central', 'jubilee'])
    expect(filterLines([centralLine, unknownLine, goodLine], 'all', 'dlr').map((l) => l.id)).toEqual(['dlr'])
  })
})

describe('FreshnessNote', () => {
  it('shows retrieval time in London time', () => {
    renderWithProviders(<FreshnessNote retrievedAt="2026-10-23T16:24:00Z" stale={false} />)
    expect(screen.getByText(/Retrieved from TfL at/)).toHaveTextContent('Retrieved from TfL at 17:24')
  })

  it('warns prominently when data is stale', () => {
    renderWithProviders(<FreshnessNote retrievedAt="2026-10-23T16:24:00Z" stale />)
    expect(screen.getByRole('status')).toHaveTextContent('Live TfL data is unavailable. Showing information retrieved at 17:24')
  })
})

describe('ErrorState', () => {
  it('shows the problem detail, field errors and reference, and retries', async () => {
    const onRetry = vi.fn()
    const user = userEvent.setup()
    const error = new ApiError(400, {
      title: 'Invalid request',
      detail: 'One or more request values are invalid.',
      correlationId: 'abc-123',
      errors: [{ field: 'time', message: 'must not be in the past' }],
    }, 'One or more request values are invalid.')
    renderWithProviders(<ErrorState error={error} onRetry={onRetry} />)

    expect(screen.getByRole('alert')).toHaveTextContent('Invalid request')
    expect(screen.getByText('must not be in the past')).toBeInTheDocument()
    expect(screen.getByText('Reference: abc-123')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(onRetry).toHaveBeenCalledOnce()
  })

  it('explains connection failures', () => {
    renderWithProviders(<ErrorState error={new ApiError(0, null, "We couldn't reach StepFree London.")} />)
    expect(screen.getByRole('alert')).toHaveTextContent('Connection problem')
  })
})
