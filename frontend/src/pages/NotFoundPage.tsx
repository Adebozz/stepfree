import { MapPin } from 'lucide-react'
import { Link } from 'react-router'

import { EmptyState } from '../components/EmptyState'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

export function NotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-bold text-navy-900">Page not found</h1>
      <EmptyState icon={MapPin} title="We couldn't find that page">
        <Link to="/" className="font-semibold text-brand underline">
          Plan a journey
        </Link>{' '}
        or{' '}
        <Link to="/status" className="font-semibold text-brand underline">
          check live status
        </Link>
        .
      </EmptyState>
    </div>
  )
}
