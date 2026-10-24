import { keepPreviousData, QueryClient, useQuery } from '@tanstack/react-query'

import { apiGet, ApiError } from './client'
import type {
  DisruptionsOverview,
  JourneyPlan,
  LineStatus,
  LiveData,
  StationAccessibility,
  StationSummary,
} from './types'

export function createQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // Client errors (bad input, not found) won't succeed on retry; outages might.
        retry: (failureCount, error) => !(error instanceof ApiError && error.isClientError) && failureCount < 1,
        refetchOnWindowFocus: false,
      },
    },
  })
}

export const queryKeys = {
  stationSearch: (query: string) => ['stations', 'search', query] as const,
  stationAccessibility: (id: string) => ['stations', id, 'accessibility'] as const,
  lineStatus: ['lines', 'status'] as const,
  disruptions: ['disruptions'] as const,
  journeys: (params: string) => ['journeys', params] as const,
}

export const MIN_STATION_QUERY = 2

export function useStationSearch(query: string) {
  const trimmed = query.trim()
  return useQuery({
    queryKey: queryKeys.stationSearch(trimmed.toLowerCase()),
    queryFn: ({ signal }) =>
      apiGet<LiveData<StationSummary[]>>('/stations', new URLSearchParams({ query: trimmed }), signal),
    enabled: trimmed.length >= MIN_STATION_QUERY,
    staleTime: 5 * 60_000,
    placeholderData: keepPreviousData,
  })
}

export function useLineStatus() {
  return useQuery({
    queryKey: queryKeys.lineStatus,
    queryFn: ({ signal }) => apiGet<LiveData<LineStatus[]>>('/lines/status', undefined, signal),
    staleTime: 30_000,
    refetchInterval: 60_000,
  })
}

export function useDisruptions() {
  return useQuery({
    queryKey: queryKeys.disruptions,
    queryFn: ({ signal }) => apiGet<LiveData<DisruptionsOverview>>('/disruptions', undefined, signal),
    staleTime: 30_000,
    refetchInterval: 120_000,
  })
}

/** `params` is null until the user has chosen both stations. */
export function useJourneyPlan(params: URLSearchParams | null) {
  const key = params?.toString() ?? ''
  return useQuery({
    queryKey: queryKeys.journeys(key),
    queryFn: ({ signal }) => apiGet<LiveData<JourneyPlan>>('/journeys', params ?? undefined, signal),
    enabled: params !== null,
    staleTime: 60_000,
  })
}

export function useStationAccessibility(id: string | undefined) {
  return useQuery({
    queryKey: queryKeys.stationAccessibility(id ?? ''),
    queryFn: ({ signal }) => apiGet<LiveData<StationAccessibility>>(`/stations/${encodeURIComponent(id ?? '')}/accessibility`, undefined, signal),
    enabled: Boolean(id),
    staleTime: 60_000,
  })
}
