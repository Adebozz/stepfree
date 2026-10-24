import { Route, Routes } from 'react-router'

import { RequireAuth } from '../components/RequireAuth'
import { AppLayout } from '../layouts/AppLayout'
import { AccountPage } from '../pages/AccountPage'
import { AlertsPage } from '../pages/AlertsPage'
import { DashboardPage } from '../pages/DashboardPage'
import { HomePage } from '../pages/HomePage'
import { JourneyDetailsPage } from '../pages/JourneyDetailsPage'
import { NotFoundPage } from '../pages/NotFoundPage'
import { RegisterPage } from '../pages/RegisterPage'
import { SavedJourneysPage } from '../pages/SavedJourneysPage'
import { SignInPage } from '../pages/SignInPage'
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
        <Route path="sign-in" element={<SignInPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route
          path="dashboard"
          element={
            <RequireAuth>
              <DashboardPage />
            </RequireAuth>
          }
        />
        <Route
          path="alerts"
          element={
            <RequireAuth>
              <AlertsPage />
            </RequireAuth>
          }
        />
        <Route
          path="saved"
          element={
            <RequireAuth>
              <SavedJourneysPage />
            </RequireAuth>
          }
        />
        <Route
          path="account"
          element={
            <RequireAuth>
              <AccountPage />
            </RequireAuth>
          }
        />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  )
}
