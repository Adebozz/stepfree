import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it } from 'vitest'

import { signedInUser } from '../test/fixtures'
import { mockApi, problem, renderWithProviders } from '../test/render'
import { SignInPage } from './SignInPage'

function renderAt(route: string) {
  return renderWithProviders(
    <Routes>
      <Route path="/sign-in" element={<SignInPage />} />
      <Route path="/saved" element={<p>Saved journeys page</p>} />
      <Route path="/" element={<p>Home page</p>} />
    </Routes>,
    { route },
  )
}

describe('SignInPage', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
  })

  it('signs in and returns to the page that asked for it', async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    mockApi({
      'GET /auth/me': () => problem(401, {}),
      'POST /auth/login': ({ body }) => (body as { email: string }).email === 'ada@example.com' ? signedInUser : problem(401, {}),
    })
    const user = userEvent.setup()
    renderAt('/sign-in?next=%2Fsaved')

    await user.type(await screen.findByLabelText('Email'), 'ada@example.com')
    await user.type(screen.getByLabelText('Password'), 'correct horse battery')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Saved journeys page')).toBeInTheDocument()
  })

  it("shows the server's message when sign-in fails", async () => {
    document.cookie = 'XSRF-TOKEN=t; path=/'
    mockApi({
      'GET /auth/me': () => problem(401, {}),
      'POST /auth/login': () => problem(401, { detail: 'The email or password is incorrect.' }),
    })
    const user = userEvent.setup()
    renderAt('/sign-in')

    await user.type(await screen.findByLabelText('Email'), 'ada@example.com')
    await user.type(screen.getByLabelText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('The email or password is incorrect.')
  })

  it('ignores an off-site return address', async () => {
    mockApi({ 'GET /auth/me': () => signedInUser })
    renderAt('/sign-in?next=https%3A%2F%2Fevil.example')

    expect(await screen.findByText('Home page')).toBeInTheDocument()
  })

  it('validates before calling the server', async () => {
    const fetchMock = mockApi({ 'GET /auth/me': () => problem(401, {}) })
    const user = userEvent.setup()
    renderAt('/sign-in')

    await user.click(await screen.findByRole('button', { name: 'Sign in' }))

    expect(await screen.findByText('Enter your email address')).toBeInTheDocument()
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true')
    expect(fetchMock.mock.calls.every(([url]) => !String(url).includes('/auth/login'))).toBe(true)
  })
})
