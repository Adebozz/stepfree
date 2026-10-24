import { NavLink, useNavigate } from 'react-router'

import { useCurrentUser, useSignOut } from '../api/account'

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `block rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-navy-700 text-white' : 'text-navy-100 hover:bg-navy-800 hover:text-white'}`

export function Logo() {
  return (
    <span className="inline-flex items-center gap-2 font-semibold tracking-tight">
      <svg aria-hidden="true" viewBox="0 0 32 32" className="size-8">
        <rect width="32" height="32" rx="8" fill="#0b1f3a" />
        <path d="M7 22h5v-5h5v-5h8" fill="none" stroke="#2dd4bf" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      <span>
        StepFree <span className="text-accent-light">London</span>
      </span>
    </span>
  )
}

export function Navbar() {
  const user = useCurrentUser()
  const signOut = useSignOut()
  const navigate = useNavigate()
  const signedIn = Boolean(user.data)
  const links = [
    { to: '/', label: 'Plan a journey', end: true },
    { to: '/status', label: 'Live status', end: false },
    ...(signedIn
      ? [
          { to: '/saved', label: 'Saved journeys', end: false },
          { to: '/account', label: 'Account', end: false },
        ]
      : []),
  ]

  return (
    <header className="bg-navy-900 text-white">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <NavLink to="/" className="rounded-md text-lg text-white">
          <Logo />{' '}
          <span className="sr-only">home</span>
        </NavLink>
        <nav aria-label="Main">
          <ul className="flex flex-wrap gap-1">
            {links.map((link) => (
              <li key={link.to}>
                <NavLink to={link.to} end={link.end} className={navLinkClass}>
                  {link.label}
                </NavLink>
              </li>
            ))}
            {/* Nothing until we know, so the bar doesn't flash "Sign in" for a signed-in user. */}
            {user.isSuccess &&
              (signedIn ? (
                <li>
                  <button
                    type="button"
                    disabled={signOut.isPending}
                    onClick={() => signOut.mutate(undefined, { onSettled: () => void navigate('/') })}
                    className="block rounded-md px-3 py-2 text-sm font-medium text-navy-100 hover:bg-navy-800 hover:text-white"
                  >
                    Sign out
                  </button>
                </li>
              ) : (
                <li>
                  <NavLink to="/sign-in" className={navLinkClass}>
                    Sign in
                  </NavLink>
                </li>
              ))}
          </ul>
        </nav>
      </div>
    </header>
  )
}
