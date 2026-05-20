import { lazy, Suspense } from 'react'
import Layout from '@/components/Layout'
import PrivateRoute from '@/components/PrivateRoute'
import AdminRoute from '@/components/AdminRoute'
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
const FollowListPage = lazy(() => import('@/pages/profile/FollowListPage'))
const FavoritesPage = lazy(() => import('@/pages/event/favorites/FavoritesPage'))
const MyEventsPage = lazy(() => import('@/pages/my-events/MyEventsPage'))
const MyFavoritesPage = lazy(() => import('@/pages/my-events/MyFavoritesPage'))
const MyParticipationsPage = lazy(() => import('@/pages/my-events/MyParticipationsPage'))
const MyPublicationsPage = lazy(() => import('@/pages/my-events/MyPublicationsPage'))
const EventStatsPage = lazy(() => import('@/pages/event/EventStatsPage'))
const NotFoundPage = lazy(() => import('@/pages/NotFoundPage'))
const ForbiddenPage = lazy(() => import('@/pages/ForbiddenPage'))
const AdminPage = lazy(() => import('@/pages/admin/AdminPage'))
const FeedPage = lazy(() => import('@/pages/feed/FeedPage'))
const PrivacyPage = lazy(() => import('@/pages/legal/PrivacyPage'))
const TermsPage = lazy(() => import('@/pages/legal/TermsPage'))

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

            <Route path="/feed">
              <Route index element={<FeedPage />} />
            </Route>

            <Route path="/legal">
              <Route index element={<Navigate to="/legal/privacy" replace />} />
              <Route path="privacy" element={<PrivacyPage />} />
              <Route path="terms" element={<TermsPage />} />
            </Route>

            <Route element={<PrivateRoute/>}>
              <Route path="/profile">
                <Route index element={<Navigate to="/profile/me" replace />} />
                <Route path="me/edit" element={<ProfileEditPage />} />
                <Route path=":username" element={<ProfilePage />} />
                <Route path=":username/followers" element={<FollowListPage mode="followers" />} />
                <Route path=":username/following" element={<FollowListPage mode="following" />} />
              </Route>

              <Route path="/events">
                <Route path="new" element={<EventCreatePage />} />
                <Route path=":id/edit" element={<EventEditPage />} />
                <Route path=":id/stats" element={<EventStatsPage />} />
                <Route path="favorites" element={<FavoritesPage />} />
              </Route>

              <Route path="/my-events">
                <Route index element={<MyEventsPage />} />
                <Route path="favorites" element={<MyFavoritesPage />} />
                <Route path="participations" element={<MyParticipationsPage />} />
                <Route path="publications" element={<MyPublicationsPage />} />
              </Route>

              <Route element={<AdminRoute />}>
                <Route path="/admin" element={<AdminPage />} />
              </Route>
            </Route>

          <Route path="/403" element={<ForbiddenPage />} />
          <Route path="*" element={<NotFoundPage/>} />
        </Route>
      </Routes>
    </Suspense>
  )
}

export default AppRouter
