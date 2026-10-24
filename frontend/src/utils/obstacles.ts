import type { JourneyOption } from '../api/types'

const plural = (count: number, word: string) => `${count} ${word}${count === 1 ? '' : 's'}`

/**
 * Lists the obstacle types TfL reported. Worded as "reported" because TfL's data may be incomplete;
 * we never claim a journey is free of an obstacle.
 */
export function obstacleSummaryText(obstacles: JourneyOption['obstacles']): string {
  const parts: string[] = []
  if (obstacles.lifts > 0) parts.push(plural(obstacles.lifts, 'lift'))
  if (obstacles.stairs > 0) parts.push(obstacles.stairs === 1 ? '1 set of stairs' : `${obstacles.stairs} sets of stairs`)
  if (obstacles.escalators > 0) parts.push(plural(obstacles.escalators, 'escalator'))
  if (obstacles.walkways > 0) parts.push(plural(obstacles.walkways, 'walkway'))
  return parts.length === 0 ? 'No lifts, stairs or escalators reported by TfL' : `TfL reports ${parts.join(', ')}`
}
