import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

import { apiGet, apiSend } from './client'
import type { CreateSavedJourney, SavedJourney } from './types'

export const savedJourneyKeys = {
  all: ['saved-journeys'] as const,
}

export function useSavedJourneys(enabled = true) {
  return useQuery({
    queryKey: savedJourneyKeys.all,
    queryFn: ({ signal }) => apiGet<SavedJourney[]>('/saved-journeys', undefined, signal),
    enabled,
  })
}

export function useCreateSavedJourney() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (journey: CreateSavedJourney) => apiSend<SavedJourney>('POST', '/saved-journeys', journey),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: savedJourneyKeys.all }),
  })
}

type SavedJourneyChanges = { id: string; name?: string; monitoringEnabled?: boolean }

/**
 * Optimistic: the list changes immediately (so a checkbox responds when clicked), rolls back if the
 * server refuses, and is replaced by the server's version when it answers.
 */
export function useUpdateSavedJourney() {
  const queryClient = useQueryClient()
  const replace = (id: string, change: (journey: SavedJourney) => SavedJourney) =>
    queryClient.setQueryData<SavedJourney[]>(savedJourneyKeys.all, (list) =>
      list?.map((journey) => (journey.id === id ? change(journey) : journey)),
    )
  return useMutation({
    mutationFn: ({ id, ...changes }: SavedJourneyChanges) =>
      apiSend<SavedJourney>('PATCH', `/saved-journeys/${encodeURIComponent(id)}`, changes),
    onMutate: async ({ id, ...changes }) => {
      // Stop an in-flight refetch from overwriting the optimistic value with an older one.
      await queryClient.cancelQueries({ queryKey: savedJourneyKeys.all })
      const previous = queryClient.getQueryData<SavedJourney[]>(savedJourneyKeys.all)
      replace(id, (journey) => ({ ...journey, ...changes }))
      return { previous }
    },
    onError: (_error, _changes, context) => queryClient.setQueryData(savedJourneyKeys.all, context?.previous),
    onSuccess: (updated) => replace(updated.id, () => updated),
  })
}

export function useDeleteSavedJourney() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => apiSend('DELETE', `/saved-journeys/${encodeURIComponent(id)}`),
    onSuccess: (_, id) =>
      queryClient.setQueryData<SavedJourney[]>(savedJourneyKeys.all, (list) => list?.filter((journey) => journey.id !== id)),
  })
}
