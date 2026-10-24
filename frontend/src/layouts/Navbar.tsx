import { NavLink } from 'react-router'

const links = [
  { to: '/', label: 'Plan a journey', end: true },
  { to: '/status', label: 'Live status', end: false },
]

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
  return (
    <header className="bg-navy-900 text-white">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
        <NavLink to="/" className="rounded-md text-lg text-white">
          <Logo />{' '}
          <span className="sr-only">home</span>
        </NavLink>
        <nav aria-label="Main">
          <ul className="flex gap-1">
            {links.map((link) => (
              <li key={link.to}>
                <NavLink
                  to={link.to}
                  end={link.end}
                  className={({ isActive }) =>
                    `block rounded-md px-3 py-2 text-sm font-medium ${isActive ? 'bg-navy-700 text-white' : 'text-navy-100 hover:bg-navy-800 hover:text-white'}`
                  }
                >
                  {link.label}
                </NavLink>
              </li>
            ))}
          </ul>
        </nav>
      </div>
    </header>
  )
}
