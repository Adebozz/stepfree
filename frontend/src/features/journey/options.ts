import type { AccessibilityPreference, JourneyPreference, WalkingSpeed } from '../../api/types'

export interface AccessibilityOption {
  value: AccessibilityPreference
  label: string
  description: string
}

/**
 * Exactly the preferences TfL's journey planner supports. Descriptions explain what each one does
 * and, importantly, what it doesn't guarantee.
 */
export const ACCESSIBILITY_OPTIONS: AccessibilityOption[] = [
  {
    value: 'STEP_FREE_TO_PLATFORM',
    label: 'Step-free to platform',
    description: 'Avoids stairs and escalators between street and platform. There may still be a step or gap onto the train.',
  },
  {
    value: 'STEP_FREE_TO_VEHICLE',
    label: 'Step-free to train',
    description: 'Also requires level or ramped boarding between platform and train, where TfL records it.',
  },
  {
    value: 'NO_SOLID_STAIRS',
    label: 'Avoid stairs',
    description: 'Routes around fixed staircases.',
  },
  {
    value: 'NO_ESCALATORS',
    label: 'Avoid escalators',
    description: 'Routes around escalators.',
  },
  {
    value: 'NO_ELEVATORS',
    label: 'Avoid lifts',
    description: 'Routes around lifts, for people who cannot use them.',
  },
]

export const JOURNEY_PREFERENCE_OPTIONS: { value: JourneyPreference; label: string }[] = [
  { value: 'LEAST_TIME', label: 'Fastest' },
  { value: 'LEAST_INTERCHANGE', label: 'Fewest changes' },
  { value: 'LEAST_WALKING', label: 'Least walking' },
]

export const WALKING_SPEED_OPTIONS: { value: WalkingSpeed; label: string }[] = [
  { value: 'SLOW', label: 'Slow' },
  { value: 'AVERAGE', label: 'Average' },
  { value: 'FAST', label: 'Fast' },
]

export function accessibilityLabel(value: AccessibilityPreference): string {
  return ACCESSIBILITY_OPTIONS.find((option) => option.value === value)?.label ?? value
}
