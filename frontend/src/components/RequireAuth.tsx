import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'

import { useCurrentUser } from '../api/account'
import { ErrorState } from './ErrorState'
import { LoadingSkeleton } from './LoadingSkeleton'

/**
 * Shows its children only to a signed-in user; otherwise sends them to sign in and back here after.
 * This is a convenience, not security: the API enforces access on every request.
 */
export function RequireAuth({ children }: { children: ReactNode }) {
  const user = useCurrentUser()
  const location = useLocation()

  if (user.isPending) return <LoadingSkeleton label="Checking your account" rows={2} />
  if (user.isError) return <ErrorState error={user.error} onRetry={() => void user.refetch()} />
  if (!user.data) {
    const next = encodeURIComponent(`${location.pathname}${location.search}`)
    return <Navigate to={`/sign-in?next=${next}`} replace />
  }
  return children
}
