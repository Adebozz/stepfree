import { useEffect } from 'react'

/** Sets a descriptive page title so screen-reader users and tab lists know where they are. */
export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · StepFree London` : 'StepFree London'
  }, [title])
}
