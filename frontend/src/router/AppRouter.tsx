import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from '../components/Layout'
import PrivateRoute from '../components/PrivateRoute'
import CallbackPage from '../pages/CallbackPage'
import CreateEventPage from '../pages/dashboard/events/CreateEventPage'
import EditEventPage from '../pages/dashboard/events/EditEventPage'
import EventDetailPage from '../pages/dashboard/events/EventDetailPage'
import DashboardPage from '../pages/dashboard/DashboardPage'
import LandingPage from '../pages/LandingPage'
import LoginPage from '../pages/LoginPage'
import ProfileEditPage from '../pages/dashboard/user/ProfileEditPage'
import ProfilePage from '../pages/dashboard/user/ProfilePage'

function AppRouter() {
  return (
    <Routes>
      <Route path='/' element={
        <Layout>
          <LandingPage />
        </Layout>
      } />
      <Route path='/login' element={<LoginPage />} />
      <Route path='/callback' element={<CallbackPage />} />
      <Route
        path='/dashboard'
        element={
          <PrivateRoute>
            <Layout>
              <DashboardPage />
            </Layout>
          </PrivateRoute>
        }
      />
      {/* /profile/me/edit must be before /profile/:id to avoid `:id` matching "me" */}
      <Route
        path='/profile/me/edit'
        element={
          <PrivateRoute>
            <Layout>
              <ProfileEditPage />
            </Layout>
          </PrivateRoute>
        }
      />
      <Route
        path='/profile/:id'
        element={
          <PrivateRoute>
            <Layout>
              <ProfilePage />
            </Layout>
          </PrivateRoute>
        }
      />
      {/* Event form entry points */}
      <Route
        path='/events/new'
        element={
          <PrivateRoute>
            <Layout>
              <CreateEventPage />
            </Layout>
          </PrivateRoute>
        }
      />
      <Route
        path='/events/:id/edit'
        element={
          <PrivateRoute>
            <Layout>
              <EditEventPage />
            </Layout>
          </PrivateRoute>
        }
      />
      <Route
        path='/events/:id'
        element={
          <PrivateRoute>
            <Layout>
              <EventDetailPage />
            </Layout>
          </PrivateRoute>
        }
      />
      <Route path='*' element={<Navigate to='/' replace />} />
    </Routes>
  )
}

export default AppRouter
