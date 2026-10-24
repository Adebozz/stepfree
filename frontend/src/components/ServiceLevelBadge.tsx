import type { ServiceLevel } from '../api/types'
import { SERVICE_LEVEL_STYLES } from '../utils/serviceLevel'

interface ServiceLevelBadgeProps {
  level: ServiceLevel
  /** TfL's wording (e.g. "Part Suspended"); falls back to our label. */
  text?: string
}

export function ServiceLevelBadge({ level, text }: ServiceLevelBadgeProps) {
  const style = SERVICE_LEVEL_STYLES[level]
  const Icon = style.icon
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${style.badge}`}>
      <Icon aria-hidden="true" className="size-3.5" />
      {text ?? style.label}
    </span>
  )
}
