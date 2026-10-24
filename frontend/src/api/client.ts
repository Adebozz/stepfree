import type { ProblemDetail } from './types'

/**
 * An error from our API. `problem` carries the RFC 9457 body when the server sent one; `status` 0
 * means the request never got a response (offline, server down).
 */
export class ApiError extends Error {
  readonly status: number
  readonly problem: ProblemDetail | null

  constructor(status: number, problem: ProblemDetail | null, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.problem = problem
  }

  get isClientError(): boolean {
    return this.status >= 400 && this.status < 500
  }
}

const BASE = '/api/v1'

export async function apiGet<T>(path: string, params?: URLSearchParams, signal?: AbortSignal): Promise<T> {
  const query = params && params.size > 0 ? `?${params.toString()}` : ''
  let response: Response
  try {
    response = await fetch(`${BASE}${path}${query}`, {
      headers: { Accept: 'application/json' },
      credentials: 'same-origin',
      signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw error
    }
    throw new ApiError(0, null, "We couldn't reach StepFree London. Check your connection and try again.")
  }

  if (!response.ok) {
    const problem = await readProblem(response)
    throw new ApiError(response.status, problem, problem?.detail ?? `Request failed (${response.status})`)
  }
  return (await response.json()) as T
}

async function readProblem(response: Response): Promise<ProblemDetail | null> {
  const contentType = response.headers.get('Content-Type') ?? ''
  if (!contentType.includes('json')) {
    return null
  }
  try {
    return (await response.json()) as ProblemDetail
  } catch {
    return null
  }
}
