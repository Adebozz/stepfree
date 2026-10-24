import type { LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'

interface EmptyStateProps {
  icon: LucideIcon
  title: string
  children?: ReactNode
}

export function EmptyState({ icon: Icon, title, children }: EmptyStateProps) {
  return (
    <div className="rounded-xl border border-dashed border-navy-200 bg-white px-6 py-10 text-center">
      <Icon aria-hidden="true" className="mx-auto size-8 text-slate-400" />
      <p className="mt-3 font-semibold text-navy-900">{title}</p>
      {children && <div className="mx-auto mt-1 max-w-md text-sm text-slate-600">{children}</div>}
    </div>
  )
}
