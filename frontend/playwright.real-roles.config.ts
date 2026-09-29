import { defineConfig } from '@playwright/test'
import path from 'node:path'

const output = path.resolve('./output/agent-real-roles-20260918')

/** 真实链路三角色回归：真实前端/后端/模型，不 mock。 */
export default defineConfig({
  testDir: './tests/e2e',
  testMatch: /agent-real-roles\.spec\.ts/,
  timeout: 240000,
  expect: { timeout: 30000 },
  workers: 1,
  retries: 0,
  reporter: [['list'], ['json', { outputFile: path.join(output, 'real-roles.json') }]],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    headless: true,
    serviceWorkers: 'block',
    viewport: { width: 1440, height: 900 },
    actionTimeout: 20000,
    navigationTimeout: 45000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure'
  }
})

