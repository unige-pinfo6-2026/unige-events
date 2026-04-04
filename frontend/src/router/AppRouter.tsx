import Layout from '@/components/Layout'
import PrivateRoute from '@/components/PrivateRoute'
import LandingPage from '@/pages/LandingPage'
import CallbackPage from '@/pages/login/callback/LoginCallbackPage'
import CreateEventPage from '@/pages/event/EventCreatePage'
import EditEventPage from '@/pages/event/EventEditPage'
import EventDetailPage from '@/pages/event/EventDetailPage'
import ProfileEditPage from '@/pages/user/ProfileEditPage'
import ProfilePage from '@/pages/user/ProfilePage'
import { Route, Routes } from 'react-router-dom'
import NotFoundPage from '@/pages/NotFoundPage'

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
            <Route index element={<NotFoundPage />} />
            <Route path="new" element={<CreateEventPage />} />
            <Route path=":id/edit" element={<EditEventPage />} />
            <Route path=":id" element={<EventDetailPage />} />
          </Route>
        </Route>

        <Route path="*" element={<NotFoundPage/>} />
      </Route>
    </Routes>
  )
}

export default AppRouter
