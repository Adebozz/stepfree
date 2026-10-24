import { Component, type ErrorInfo, type ReactNode } from 'react'

interface ErrorBoundaryState {
  error: Error | null
}

/**
 * Catches rendering errors so one broken component shows a recovery message instead of a blank
 * page. Error boundaries must be class components; React has no hook equivalent.
 */
export class ErrorBoundary extends Component<{ children: ReactNode }, ErrorBoundaryState> {
  override state: ErrorBoundaryState = { error: null }

  static getDerivedStateFromError(error: Error): ErrorBoundaryState {
    return { error }
  }

  override componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled rendering error', error, info.componentStack)
  }

  override render() {
    if (this.state.error) {
      return (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-6 text-red-900">
          <p className="font-semibold">This page couldn't be displayed.</p>
          <p className="mt-1 text-sm">Something unexpected went wrong. Reloading usually fixes it.</p>
          <button
            type="button"
            onClick={() => window.location.reload()}
            className="mt-3 rounded-lg bg-white px-3 py-1.5 text-sm font-medium ring-1 ring-red-300 hover:bg-red-100"
          >
            Reload page
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
