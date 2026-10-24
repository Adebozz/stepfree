import { Accessibility, TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'

interface DisruptionAlertProps {
  title: string
  children: ReactNode
  /** Accessibility-related alerts get a distinct icon so they stand out in a list. */
  accessibility?: boolean
  meta?: ReactNode
}

export function DisruptionAlert({ title, children, accessibility = false, meta }: DisruptionAlertProps) {
  const Icon = accessibility ? Accessibility : TriangleAlert
  return (
    <div className="rounded-xl border border-amber-200 bg-amber-50 p-4 text-amber-950">
      <div className="flex gap-3">
        <Icon aria-hidden="true" className="mt-0.5 size-5 shrink-0 text-amber-700" />
        <div className="min-w-0 flex-1">
          <p className="font-semibold">
            {title}
            {accessibility && (
              <>
                {' '}
                <span className="sr-only">(affects accessibility)</span>
              </>
            )}
          </p>
          <div className="mt-1 text-sm leading-relaxed break-words">{children}</div>
          {meta && <div className="mt-2 text-xs text-amber-900">{meta}</div>}
        </div>
      </div>
    </div>
  )
}
