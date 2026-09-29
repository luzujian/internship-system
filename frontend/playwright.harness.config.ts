import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  testMatch: 'agent-harness-safety.spec.ts',
  timeout: 90000,
  workers: 1,
  retries: 0,
  outputDir: '../.codex/harness-test-results',
  use: { baseURL: process.env.E2E_BASE_URL || 'http://127.0.0.1:5173', headless: false, viewport: { width: 1440, height: 1000 } }
})
