import { defineConfig, loadEnv } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig(({ mode }) => {
  // If you want to access to .env in src/ folder
  // please use `import.meta.env.VITE_VARNAME` instead
  const env = loadEnv(mode, process.cwd(), '')

  return {
    plugins: [react()],
    server: {
      host: '0.0.0.0',
      port: 5173,
      strictPort: true,
      // Dev-only proxy: replaced by nginx in production (docker compose)
      proxy: {
        '/api': {
          target: `http://api:${env.API_PORT ?? "8080"}`,
          changeOrigin: true
        }
      }
    },
    test: {
      coverage: {
        provider: 'v8',
        reporter: ['text', 'lcov'],
      }
    }
  }
})