import { RefreshCw, WifiOff, TriangleAlert } from 'lucide-react'

import { ApiError } from '../api/client'

interface ErrorStateProps {
  error: unknown
  title?: string
  onRetry?: () => void
}

/** Explains what went wrong in plain language, offers a retry, and shows a reference id for support. */
export function ErrorState({ error, title, onRetry }: ErrorStateProps) {
  const apiError = error instanceof ApiError ? error : null
  const offline = apiError?.status === 0
  const Icon = offline ? WifiOff : TriangleAlert
  const heading = title ?? apiError?.problem?.title ?? (offline ? 'Connection problem' : 'Something went wrong')
  const detail = apiError?.message ?? 'An unexpected error occurred. Please try again.'
  const fieldErrors = apiError?.problem?.errors ?? []

  return (
    <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-red-900">
      <div className="flex gap-3">
        <Icon aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-red-700" />
        <div className="min-w-0 flex-1">
          <p className="font-semibold">{heading}</p>
          <p className="mt-1 text-sm">{detail}</p>
          {fieldErrors.length > 0 && (
            <ul className="mt-2 list-disc pl-5 text-sm">
              {fieldErrors.map((fieldError) => (
                <li key={`${fieldError.field}-${fieldError.message}`}>{fieldError.message}</li>
              ))}
            </ul>
          )}
          {onRetry && (
            <button
              type="button"
              onClick={onRetry}
              className="mt-3 inline-flex items-center gap-2 rounded-lg bg-white px-3 py-1.5 text-sm font-medium text-red-900 ring-1 ring-red-300 hover:bg-red-100"
            >
              <RefreshCw aria-hidden="true" className="size-4" />
              Try again
            </button>
          )}
          {apiError?.problem?.correlationId && (
            <p className="mt-2 text-xs text-red-800">Reference: {apiError.problem.correlationId}</p>
          )}
        </div>
      </div>
    </div>
  )
}
