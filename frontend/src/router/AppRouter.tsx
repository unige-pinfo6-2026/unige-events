import Layout from '@/components/Layout'
import PrivateRoute from '@/components/PrivateRoute'
import LandingPage from '@/pages/LandingPage'
import CallbackPage from '@/pages/login/callback/LoginCallbackPage'
import EventCreatePage from '@/pages/event/EventCreatePage'
import EventEditPage from '@/pages/event/EventEditPage'
import EventDetailPage from '@/pages/event/EventDetailPage'
import ProfileEditPage from '@/pages/profile/ProfileEditPage'
import ProfilePage from '@/pages/profile/ProfilePage'
import { Route, Routes } from 'react-router-dom'
import NotFoundPage from '@/pages/NotFoundPage'
import { EventsPage } from '@/pages/event/EventsPage'
import { CalendarPage } from '@/pages/calendar/CalendarPage'

const AppRouter = () => {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<LandingPage />} />

        <Route path="/login">
          <Route index element={<NotFoundPage />} />
          <Route path="callback" element={<CallbackPage />} />
        </Route>

        <Route element={<PrivateRoute />}>
          <Route path="/profile">
            <Route index element={<NotFoundPage />} />
            <Route path="me/edit" element={<ProfileEditPage />} />
            <Route path=":id" element={<ProfilePage />} />
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
  )
}

export default AppRouter
