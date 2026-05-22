import { AuthProvider } from "./contexts/AuthContext"
import { ThemeProvider } from "./contexts/ThemeContext"
import { ToastProvider } from "./contexts/ToastContext"
import { FavoritesProvider } from "./contexts/FavoritesContext"
import AppRouter from "./router/AppRouter"
import AppErrorBoundary from "./components/AppErrorBoundary"

function App() {
  return (
    <AppErrorBoundary>
      <ThemeProvider>
        <ToastProvider>
          <AuthProvider>
            <FavoritesProvider>
              <AppRouter />
            </FavoritesProvider>
          </AuthProvider>
        </ToastProvider>
      </ThemeProvider>
    </AppErrorBoundary>
  )
}

export default App
