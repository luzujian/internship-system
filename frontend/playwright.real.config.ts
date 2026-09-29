import { defineConfig } from '@playwright/test'
import path from 'node:path'

const output = path.resolve('./output/agent-real-e2e-20260918')

/**
 * 真实链路验收：真实前端(5173) + 真实后端(8080) + 真实模型（DB 默认模型行的凭据）。
 * 不 mock 任何接口；账号为受控 E2E 账号，测试后删除。
 */
export default defineConfig({
  testDir: './tests/e2e',
  testMatch: /agent-real-(model|lifecycle)\.spec\.ts/,
  timeout: 180000,
  expect: { timeout: 30000 },
  workers: 1,
  retries: 0,
  reporter: [['list'], ['json', { outputFile: path.join(output, 'real-e2e.json') }]],
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
