import { Route, Routes } from 'react-router'

import { AppLayout } from '../layouts/AppLayout'
import { HomePage } from '../pages/HomePage'
import { JourneyDetailsPage } from '../pages/JourneyDetailsPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { StationPage } from '../pages/StationPage'
import { StatusPage } from '../pages/StatusPage'

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<AppLayout />}>
        <Route index element={<HomePage />} />
        <Route path="status" element={<StatusPage />} />
        <Route path="journey/:index" element={<JourneyDetailsPage />} />
        <Route path="stations/:id" element={<StationPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
