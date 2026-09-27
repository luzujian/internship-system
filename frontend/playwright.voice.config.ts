import { defineConfig } from '@playwright/test'

/**
 * 真实音频验收专用配置。
 * 必须使用 headed Chrome + MediaStreamTrackWebSpeech，才能把真实音频轨道嵌入
 * 现有 Web Speech API 识别链路；不并入默认的 desktop/tablet/mobile 项目。
 */
export default defineConfig({
  testDir: './tests/voice-e2e',
  timeout: 10 * 60 * 1000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list', { printSteps: true }], ['html', { outputFolder: 'playwright-report/voice-e2e', open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:5173',
    headless: false,
    channel: 'chrome',
    viewport: { width: 1600, height: 900 },
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'off',
    launchOptions: {
      args: [
        '--enable-blink-features=MediaStreamTrackWebSpeech',
        '--autoplay-policy=no-user-gesture-required',
        '--disable-gpu-shader-disk-cache',
        '--disable-gpu-program-cache'
      ]
    }
  },
  webServer: {
    command: 'npm run dev -- --host 127.0.0.1',
    url: 'http://127.0.0.1:5173',
    reuseExistingServer: true,
    timeout: 60_000
  }
})
