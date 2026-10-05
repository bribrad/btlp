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
    coverage: {
      provider: 'v8',
      // text for the CI log, html to browse locally, lcov for any external reporter, and
      // json-summary so scripts/ci/coverage-summary.sh reads totals instead of scraping text.
      reporter: ['text', 'html', 'lcov', 'json-summary'],
      reportsDirectory: './coverage',
      // Report every source file, not just the ones a test happened to import — otherwise
      // an untested module reads as 0 files rather than 0%.
      all: true,
      include: ['src/**/*.{ts,tsx}'],
      exclude: [
        'src/test/**',
        'src/types/**', // type-only declarations, no executable lines
        'src/main.tsx', // bootstrap; exercised by the e2e suite, not jsdom
        'src/**/*.d.ts',
      ],
      // No thresholds yet: see the note in backend/pom.xml. Add `thresholds` here once the
      // first reports land so the floor reflects reality instead of a guess.
    },
  },
})
