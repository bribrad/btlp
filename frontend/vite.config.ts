import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'
import { fileURLToPath, URL } from 'node:url'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: true,
    // Unit tests are *.test.*; Playwright owns *.spec.* under src/test/e2e and cannot run
    // under vitest (its test.beforeEach throws outside the Playwright runner).
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
