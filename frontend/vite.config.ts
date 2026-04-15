import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'

// https://vite.dev/config/
export default defineConfig(() => {
  return {
    plugins: [react(), tailwindcss()],
    resolve: {
      alias: {
        "@": path.resolve(__dirname, "./src"),
      },
    },
    server: {
      port: 5173,
      host: '0.0.0.0',
      strictPort: true,
      watch: {
        usePolling: true,
      },
      // Dev-only proxy: replaced by nginx in production (docker compose)
      proxy: {
        '/api': {
          target: `http://localhost:8080/`,
          changeOrigin: true
        }
      }
    },
    test: {
      setupFiles: ['src/__tests__/setup.ts'],
      testTimeout: 30000,
      maxForks: 4,
      coverage: {
        provider: 'v8',
        reporter: ['text', 'lcov'],
      }
    }
  }
})