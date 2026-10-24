import { CircleCheck, CircleHelp, CircleX, Info, OctagonAlert, TriangleAlert, type LucideIcon } from 'lucide-react'

import type { LineStatus, ServiceLevel } from '../api/types'

export interface LevelStyle {
  label: string
  icon: LucideIcon
  /** Classes for a badge: text and background chosen for AA contrast. */
  badge: string
}

/** Every level has a label and icon as well as a colour, so status never relies on colour alone. */
export const SERVICE_LEVEL_STYLES: Record<ServiceLevel, LevelStyle> = {
  GOOD_SERVICE: { label: 'Good service', icon: CircleCheck, badge: 'bg-green-50 text-green-800 ring-green-600/20' },
  INFORMATION: { label: 'Information', icon: Info, badge: 'bg-blue-50 text-blue-800 ring-blue-600/20' },
  UNKNOWN: { label: 'Status unavailable', icon: CircleHelp, badge: 'bg-slate-100 text-slate-700 ring-slate-500/20' },
  MINOR_DISRUPTION: { label: 'Minor disruption', icon: TriangleAlert, badge: 'bg-amber-50 text-amber-800 ring-amber-600/30' },
  SEVERE_DISRUPTION: { label: 'Severe disruption', icon: OctagonAlert, badge: 'bg-red-50 text-red-800 ring-red-600/20' },
  CLOSED: { label: 'Closed', icon: CircleX, badge: 'bg-red-100 text-red-900 ring-red-700/30' },
}

export function isDisrupted(level: ServiceLevel): boolean {
  return level === 'MINOR_DISRUPTION' || level === 'SEVERE_DISRUPTION' || level === 'CLOSED'
}

export type LineFilter = 'all' | 'disrupted'

/** "With problems" keeps lines whose status is unknown: we can't say they're fine. */
export function filterLines(lines: LineStatus[], filter: LineFilter, mode: string): LineStatus[] {
  return lines.filter(
    (line) => (mode === 'all' || line.mode === mode) && (filter === 'all' || isDisrupted(line.level) || line.level === 'UNKNOWN'),
  )
}
