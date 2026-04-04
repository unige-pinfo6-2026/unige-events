import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from '../components/Layout'
import PrivateRoute from '../components/PrivateRoute'
import CalendarPage from '../pages/CalendarPage'
import CallbackPage from '../pages/CallbackPage'
import CreateEventPage from '../pages/CreateEventPage'
import EditEventPage from '../pages/EditEventPage'
import EventDetailPage from '../pages/EventDetailPage'
import HomePage from '../pages/HomePage'
import LoginPage from '../pages/LoginPage'
import ProfileEditPage from '../pages/ProfileEditPage'
import ProfilePage from '../pages/ProfilePage'
import SearchPage from '../pages/SearchPage'

function AppRouter() {
  return (
    <Routes>
      <Route path='/' element={<Navigate to='/home' replace />} />
      <Route path='/login' element={<LoginPage />} />
      <Route path='/callback' element={<CallbackPage />} />
      <Route
        path='/home'
        element={
          <PrivateRoute>
            <Layout>
              <HomePage />
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
      <Route
        path='/calendar'
        element={
          <PrivateRoute>
            <Layout>
              <CalendarPage />
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
      <Route
        path='/search'
        element={
          <PrivateRoute>
            <Layout>
              <SearchPage />
            </Layout>
          </PrivateRoute>
        }
      />
      <Route path='*' element={<Navigate to='/home' replace />} />
    </Routes>
  )
}

export default AppRouter
