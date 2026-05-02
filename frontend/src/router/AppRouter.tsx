import { lazy, Suspense } from 'react'
import Layout from '@/components/Layout'
import PrivateRoute from '@/components/PrivateRoute'
import { Navigate, Route, Routes } from 'react-router-dom'
import LoadingPage from '@/pages/LoadingPage'
import SearchPage from '@/pages/event/EventsSearchPage'

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
const FavoritesPage = lazy(() => import('@/pages/event/favorites/FavoritesPage'))
const MyEventsPage = lazy(() => import('@/pages/my-events/MyEventsPage'))
const MyFavoritesPage = lazy(() => import('@/pages/my-events/MyFavoritesPage'))
const MyParticipationsPage = lazy(() => import('@/pages/my-events/MyParticipationsPage'))
const MyPublicationsPage = lazy(() => import('@/pages/my-events/MyPublicationsPage'))
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

            <Route path="/events">
              <Route index element={<EventsPage />} />
              <Route path="search" element={<SearchPage />} />
              <Route path=":id" element={<EventDetailPage />} />
            </Route>

            <Route path="/calendar">
              <Route index element={<CalendarPage />} />
            </Route>

            <Route element={<PrivateRoute/>}>
              <Route path="/profile">
                <Route index element={<Navigate to="/profile/me" replace />} />
                <Route path="me/edit" element={<ProfileEditPage />} />
                <Route path=":username" element={<ProfilePage />} />
              </Route>

              <Route path="/events">
                <Route path="new" element={<EventCreatePage />} />
                <Route path=":id/edit" element={<EventEditPage />} />
                <Route path="favorites" element={<FavoritesPage />} />
              </Route>

              <Route path="/my-events">
                <Route index element={<MyEventsPage />} />
                <Route path="favorites" element={<MyFavoritesPage />} />
                <Route path="participations" element={<MyParticipationsPage />} />
                <Route path="publications" element={<MyPublicationsPage />} />
              </Route>
            </Route>

          <Route path="*" element={<NotFoundPage/>} />
        </Route>
      </Routes>
    </Suspense>
  )
}

export default AppRouter
