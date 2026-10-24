import { afterEach, describe, expect, it } from 'vitest'

import { mockApi, noContent, problem } from '../test/render'
import { safeNextPath } from './account'
import { apiSend, ApiError } from './client'

function clearCsrfCookie() {
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
}

describe('apiSend', () => {
  afterEach(clearCsrfCookie)

  it('fetches a CSRF token when it has none and echoes it in the header', async () => {
    const fetchMock = mockApi({
      'GET /auth/csrf': () => {
        document.cookie = 'XSRF-TOKEN=token-123; path=/'
        return noContent()
      },
      'POST /auth/logout': ({ headers }) => (headers.get('X-XSRF-TOKEN') === 'token-123' ? noContent() : problem(403, {})),
    })

    await expect(apiSend('POST', '/auth/logout')).resolves.toBeUndefined()
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('reuses an existing token without asking again', async () => {
    document.cookie = 'XSRF-TOKEN=already-here; path=/'
    const fetchMock = mockApi({
      'PATCH /saved-journeys/abc': ({ headers, body }) => ({ token: headers.get('X-XSRF-TOKEN'), body }),
    })

    const result = await apiSend<{ token: string; body: unknown }>('PATCH', '/saved-journeys/abc', { name: 'New' })

    expect(result).toEqual({ token: 'already-here', body: { name: 'New' } })
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it("turns a problem response into an ApiError with the server's message", async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    mockApi({
      'POST /auth/login': () => problem(401, { type: '/problems/invalid-credentials', detail: 'The email or password is incorrect.' }),
    })

    const error = await apiSend('POST', '/auth/login', {}).catch((e: unknown) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect((error as ApiError).status).toBe(401)
    expect((error as ApiError).message).toBe('The email or password is incorrect.')
  })
})

describe('safeNextPath', () => {
  it('allows same-site paths and rejects anything that could leave the site', () => {
    expect(safeNextPath('/saved')).toBe('/saved')
    expect(safeNextPath('/journey/0?from=1000257')).toBe('/journey/0?from=1000257')
    expect(safeNextPath(null)).toBe('/')
    expect(safeNextPath('https://evil.example')).toBe('/')
    expect(safeNextPath('//evil.example')).toBe('/')
    expect(safeNextPath('/\\evil.example')).toBe('/')
  })
})
