import type { AccessibilityPreference } from '../api/types'
import { ACCESSIBILITY_OPTIONS } from '../features/journey/options'

interface AccessibilityPreferencesProps {
  value: AccessibilityPreference[]
  onChange: (value: AccessibilityPreference[]) => void
}

export function AccessibilityPreferences({ value, onChange }: AccessibilityPreferencesProps) {
  function toggle(option: AccessibilityPreference, checked: boolean) {
    onChange(checked ? [...value, option] : value.filter((v) => v !== option))
  }

  return (
    <fieldset>
      <legend className="text-sm font-medium text-navy-900">Accessibility needs</legend>
      <p className="mt-0.5 text-sm text-slate-600">TfL's journey planner applies these. Results depend on TfL's station data.</p>
      <div className="mt-2 grid gap-2 sm:grid-cols-2">
        {ACCESSIBILITY_OPTIONS.map((option) => {
          const id = `access-${option.value}`
          const checked = value.includes(option.value)
          return (
            <label
              key={option.value}
              htmlFor={id}
              className={`flex cursor-pointer gap-3 rounded-lg border p-3 transition-colors ${
                checked ? 'border-brand bg-blue-50' : 'border-navy-200 bg-white hover:border-navy-700/40'
              }`}
            >
              <input
                id={id}
                type="checkbox"
                checked={checked}
                onChange={(event) => toggle(option.value, event.target.checked)}
                aria-describedby={`${id}-desc`}
                className="mt-0.5 size-4 shrink-0 accent-brand"
              />
              <span>
                <span className="block text-sm font-medium text-navy-900">{option.label}</span>
                <span id={`${id}-desc`} className="block text-xs text-slate-600">
                  {option.description}
                </span>
              </span>
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}
