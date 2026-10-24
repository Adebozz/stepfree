interface LoadingSkeletonProps {
  /** Announced to screen readers; the shimmer itself is hidden from them. */
  label: string
  rows?: number
  className?: string
}

export function LoadingSkeleton({ label, rows = 3, className = '' }: LoadingSkeletonProps) {
  return (
    <div role="status" aria-live="polite" className={className}>
      <span className="sr-only">{label}</span>
      <div aria-hidden="true" className="space-y-3">
        {Array.from({ length: rows }, (_, index) => (
          <div key={index} className="animate-pulse rounded-xl border border-navy-100 bg-white p-4">
            <div className="h-4 w-1/3 rounded bg-navy-100" />
            <div className="mt-3 h-3 w-2/3 rounded bg-navy-100" />
            <div className="mt-2 h-3 w-1/2 rounded bg-navy-100" />
          </div>
        ))}
      </div>
    </div>
  )
}
