import Layout from '@/components/Layout'
import PrivateRoute from '@/components/PrivateRoute'
import LandingPage from '@/pages/LandingPage'
import LoginPage from '@/pages/login/LoginPage'
import CallbackPage from '@/pages/login/callback/CallbackPage'
import DashboardPage from '@/pages/dashboard/DashboardPage'
import CreateEventPage from '@/pages/dashboard/event/CreateEventPage'
import EditEventPage from '@/pages/dashboard/event/EditEventPage'
import EventDetailPage from '@/pages/dashboard/event/EventDetailPage'
import ProfileEditPage from '@/pages/dashboard/user/ProfileEditPage'
import ProfilePage from '@/pages/dashboard/user/ProfilePage'
import { Route, Routes } from 'react-router-dom'
import NotFoundPage from '@/pages/NotFoundPage'

const AppRouter = () => {
  return (
    <Routes>
      {/* Layout global */}
      <Route element={<Layout />}>
        {/* Public */}
        <Route path="/" element={<LandingPage />} />

        <Route path="/login">
          <Route index element={<LoginPage />} />
          <Route path="callback" element={<CallbackPage />} />
        </Route>

        {/* Private */}
        <Route element={<PrivateRoute />}>
          <Route path="dashboard">
            <Route index element={<DashboardPage />} />

            {/* Profile */}
            <Route path="profile">
              <Route path="me/edit" element={<ProfileEditPage />} />
              <Route path=":id" element={<ProfilePage />} />
            </Route>

            {/* Events */}
            <Route path="events">
              <Route path="new" element={<CreateEventPage />} />
              <Route path=":id/edit" element={<EditEventPage />} />
              <Route path=":id" element={<EventDetailPage />} />
            </Route>
          </Route>
        </Route>

        {/* Fallback */}
        <Route path="*" element={<NotFoundPage/>} />
      </Route>
    </Routes>
  )
}

export default AppRouter