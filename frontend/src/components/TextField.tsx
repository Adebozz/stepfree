import { forwardRef, useId, type InputHTMLAttributes } from 'react'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  error?: string
  hint?: string
}

/** A labelled input whose error and hint are announced by screen readers (aria-describedby). */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField({ label, error, hint, id, ...props }, ref) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const hintId = `${inputId}-hint`
  const errorId = `${inputId}-error`
  const describedBy = [hint ? hintId : null, error ? errorId : null].filter(Boolean).join(' ') || undefined
  return (
    <div>
      <label htmlFor={inputId} className="block text-sm font-medium text-navy-900">
        {label}
      </label>
      {hint && (
        <p id={hintId} className="mt-0.5 text-sm text-slate-600">
          {hint}
        </p>
      )}
      <input
        ref={ref}
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        className={`mt-1 block w-full rounded-lg border bg-white px-3 py-2 text-base text-navy-900 ${error ? 'border-red-500' : 'border-navy-200'}`}
        {...props}
      />
      {error && (
        <p id={errorId} className="mt-1 text-sm font-medium text-red-700">
          {error}
        </p>
      )}
    </div>
  )
})
