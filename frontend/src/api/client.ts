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

type Method = 'POST' | 'PUT' | 'PATCH' | 'DELETE'

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((cookie) => cookie.startsWith(`${name}=`))
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null
}

/**
 * Returns the CSRF token, asking the server for one if we have none. The server clears the cookie
 * when it rotates the token (sign-in, sign-out), so this also picks up the new one automatically.
 */
async function csrfToken(): Promise<string | null> {
  const existing = readCookie(CSRF_COOKIE)
  if (existing) return existing
  await fetch(`${BASE}/auth/csrf`, { credentials: 'same-origin' })
  return readCookie(CSRF_COOKIE)
}

/**
 * Sends a state-changing request with the CSRF header. The session cookie is HttpOnly, so scripts
 * (including injected ones) can't read it; the browser attaches it. Resolves to undefined on 204.
 */
export async function apiSend<T = void>(method: Method, path: string, body?: unknown): Promise<T> {
  let response: Response
  try {
    const token = await csrfToken()
    response = await fetch(`${BASE}${path}`, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
        ...(token ? { [CSRF_HEADER]: token } : {}),
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
    })
  } catch {
    throw new ApiError(0, null, "We couldn't reach StepFree London. Check your connection and try again.")
  }

  if (!response.ok) {
    const problem = await readProblem(response)
    throw new ApiError(response.status, problem, problem?.detail ?? `Request failed (${response.status})`)
  }
  return (response.status === 204 ? undefined : await response.json()) as T
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
