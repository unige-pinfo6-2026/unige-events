import { lazy, Suspense } from 'react'
import Layout from '@/components/Layout'
import PrivateRoute from '@/components/PrivateRoute'
import { Navigate, Route, Routes } from 'react-router-dom'
import LoadingPage from '@/pages/LoadingPage'

const LandingPage = lazy(() => import('@/pages/LandingPage'))
const LoginPage = lazy(() => import('@/pages/login/LoginPage'))
const CallbackPage = lazy(() => import('@/pages/login/callback/LoginCallbackPage'))
const EventsPage = lazy(() => import('@/pages/event/EventsPage'))
const EventCreatePage = lazy(() => import('@/pages/event/EventCreatePage'))
const EventEditPage = lazy(() => import('@/pages/event/EventEditPage'))
const EventDetailPage = lazy(() => import('@/pages/event/EventDetailPage'))
const CalendarPage = lazy(() => import('@/pages/calendar/CalendarPage'))
const ProfilePage = lazy(() => import('@/pages/profile/ProfilePage'))
const ProfileEditPage = lazy(() => import('@/pages/profile/ProfileEditPage'))
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'))

const AppRouter = () => {
  return (
    <Suspense fallback={<LoadingPage />}>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<LandingPage />} />
            <Route path="/login">
              <Route index element={<LoginPage />} />
              <Route path="callback" element={<CallbackPage />} />
            </Route>

            <Route element={<PrivateRoute/>}>
              <Route path="/profile">
                <Route index element={<Navigate to="/profile/me" replace />} />
                <Route path=":id" element={<ProfilePage />} />
                <Route path="me/edit" element={<ProfileEditPage />} />
              </Route>

              <Route path="/events">
                <Route index element={<EventsPage />} />
                <Route path="new" element={<EventCreatePage />} />
                <Route path=":id/edit" element={<EventEditPage />} />
                <Route path=":id" element={<EventDetailPage />} />
              </Route>

              <Route path="/calendar">
                <Route index element={<CalendarPage />} />
              </Route>
            </Route>

          <Route path="*" element={<NotFoundPage/>} />
        </Route>
      </Routes>
    </Suspense>
  )
}

export default AppRouter
