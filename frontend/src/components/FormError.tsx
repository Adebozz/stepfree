import { TriangleAlert } from 'lucide-react'

import { ApiError } from '../api/client'

/** A failed form submission, in the server's words. role="alert" so it's announced when it appears. */
export function FormError({ error }: { error: unknown }) {
  if (!error) return null
  const message = error instanceof ApiError ? error.message : 'Something went wrong. Please try again.'
  const fieldErrors = error instanceof ApiError ? (error.problem?.errors ?? []) : []
  return (
    <div role="alert" className="flex gap-2 rounded-lg border border-red-200 bg-red-50 p-3 text-sm text-red-900">
      <TriangleAlert aria-hidden="true" className="mt-0.5 size-4 shrink-0 text-red-700" />
      <div>
        <p>{message}</p>
        {fieldErrors.length > 0 && (
          <ul className="mt-1 list-disc pl-5">
            {fieldErrors.map((fieldError) => (
              <li key={`${fieldError.field}-${fieldError.message}`}>
                {fieldError.field}: {fieldError.message}
              </li>
            ))}
          </ul>
        )}
      </div>
    </div>
  )
}
