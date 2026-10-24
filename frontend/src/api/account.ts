import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query'

import { apiGet, apiSend, ApiError } from './client'
import type { User } from './types'

export const accountKeys = {
  me: ['account', 'me'] as const,
}

/** Everything cached for the signed-in user. Dropped on sign-out so the next user never sees it. */
const USER_SCOPED_KEYS = [['saved-journeys'], ['notifications']]

/** The signed-in user, or null when signed out. A 401 is an answer here, not an error. */
export function useCurrentUser() {
  return useQuery({
    queryKey: accountKeys.me,
    queryFn: async ({ signal }) => {
      try {
        return await apiGet<User>('/auth/me', undefined, signal)
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
    staleTime: 5 * 60_000,
  })
}

function signedOut(queryClient: QueryClient) {
  USER_SCOPED_KEYS.forEach((queryKey) => queryClient.removeQueries({ queryKey }))
  queryClient.setQueryData(accountKeys.me, null)
}

function signedIn(queryClient: QueryClient, user: User) {
  // A different user may have been signed in on this tab before.
  USER_SCOPED_KEYS.forEach((queryKey) => queryClient.removeQueries({ queryKey }))
  queryClient.setQueryData(accountKeys.me, user)
}

export function useSignIn() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (credentials: { email: string; password: string }) => apiSend<User>('POST', '/auth/login', credentials),
    onSuccess: (user) => signedIn(queryClient, user),
  })
}

export function useRegister() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (details: { fullName: string; email: string; password: string }) =>
      apiSend<User>('POST', '/auth/register', details),
    onSuccess: (user) => signedIn(queryClient, user),
  })
}

export function useSignOut() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => apiSend('POST', '/auth/logout'),
    // Clear local state even if the request failed: the user asked to be signed out of this device.
    onSettled: () => signedOut(queryClient),
  })
}

export function useUpdateProfile() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (changes: { fullName?: string; email?: string; currentPassword?: string }) =>
      apiSend<User>('PATCH', '/auth/me', changes),
    onSuccess: (user) => queryClient.setQueryData(accountKeys.me, user),
  })
}

export function useChangePassword() {
  return useMutation({
    mutationFn: (passwords: { currentPassword: string; newPassword: string }) => apiSend('POST', '/auth/me/password', passwords),
  })
}

export function useDeleteAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (password: string) => apiSend('DELETE', '/auth/me', { password }),
    onSuccess: () => signedOut(queryClient),
  })
}

/**
 * Only same-site paths are allowed as a post-sign-in destination. Without this, a link like
 * /sign-in?next=https://evil.example would send users to another site after they sign in.
 */
export function safeNextPath(next: string | null): string {
  if (!next || !next.startsWith('/') || next.startsWith('//') || next.startsWith('/\\')) return '/'
  return next
}
