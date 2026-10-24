import { NavLink, useNavigate } from 'react-router'

import { useCurrentUser, useSignOut } from '../api/account'
import { useAlerts } from '../api/notifications'

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
  const unread = useAlerts(signedIn).data?.unreadCount ?? 0
  const links: { to: string; label: string; end: boolean; badge?: number }[] = [
    { to: '/', label: 'Plan a journey', end: true },
    { to: '/status', label: 'Live status', end: false },
    ...(signedIn
      ? [
          { to: '/dashboard', label: 'Dashboard', end: false },
          { to: '/saved', label: 'Saved journeys', end: false },
          { to: '/alerts', label: 'Alerts', end: false, badge: unread },
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
                <NavLink
                  to={link.to}
                  end={link.end}
                  className={navLinkClass}
                  // One label, not visible text + an sr-only span: browsers join those with a stray space ("Alerts , 1 unread").
                  aria-label={link.badge ? `${link.label}, ${link.badge} unread` : undefined}
                >
                  {link.label}
                  {link.badge ? (
                    <span aria-hidden="true" className="ml-1.5 rounded-full bg-amber-400 px-1.5 py-0.5 text-xs font-bold text-navy-900">
                      {link.badge}
                    </span>
                  ) : null}
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
