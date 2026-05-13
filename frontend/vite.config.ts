import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'
import path from 'node:path'
import os from 'node:os'

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
          target: `http://kong:8000/`,
          changeOrigin: true
        }
      }
    },
    test: {
      environment: 'happy-dom',
      environmentOptions: {
        happyDOM: {
          settings: {
            // Prevent happy-dom from fetching external URLs
            navigation: {
              disableMainFrameNavigation: false,
              disableChildFrameNavigation: true,
              disableChildPageNavigation: false,
              disableFallbackToSetURL: false,
              crossOriginPolicy: 'anyOrigin',
              beforeContentCallback: null,
            },
          },
        },
      },
      setupFiles: ['src/__tests__/setup.ts'],
      testTimeout: 10000,
      maxForks: Math.max(2, os.cpus().length - 1),
      coverage: {
        provider: 'v8',
        reporter: ['text', 'lcov'],
      }
    }
  }
})