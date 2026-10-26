import { defineConfig } from '@playwright/test'

const port = Number(process.env.E2E_PORT || 5173)
const baseURL = process.env.E2E_BASE_URL || `http://127.0.0.1:${port}`

/**
 * Playwright E2E 配置
 *
 * - Chrome project：desktop / tablet / mobile；WebKit 引擎模拟：webkit-mobile / webkit-tablet
 * - webServer 在测试启动前自动 `npm run dev -- --host 127.0.0.1`，端口默认 5173，可用 E2E_PORT / E2E_BASE_URL 覆盖
 * - 网络说明：统一走 IPv4 回环（127.0.0.1）。vite 默认 host 'localhost'
 *   会绑定到 IPv6 ::1，部分受限环境下 IPv6 回环连接被拦截会导致
 *   webServer 轮询超时与页面加载失败
 * - 浏览器统一使用 channel: 'chrome'（本机完整版 Chrome），
 *   避免 Playwright 内置 headless-shell 被本地安全策略拦截的问题
 * - launchOptions.args 禁用 GPU 着色器磁盘缓存，避免 Chrome 向
 *   NVIDIA DXCache / GPU 缓存目录写入文件（受限环境会拦截该写入
 *   导致浏览器启动失败）
 * - 认证策略：不使用 VITE_SKIP_AUTH_CHECK（该配置与 authGuard 的
 *   restoreAuthState 流程相互矛盾）。测试通过 helpers/auth.ts 的
 *   authenticateAs() 用 page.addInitScript 注入 localStorage 伪 JWT，
 *   让 authGuard 走正常的 restoreAuthState 流程成功恢复认证状态
 * - 真实后端调用一律被 helpers/auth.ts 的 mockApis() 拦截返回空数据，
 *   测试完全不依赖后端服务
 *
 * 参考：https://playwright.dev/docs/test-configuration
 */
export default defineConfig({
  testDir: './tests/e2e',
  // 默认只收集会在导航前拦截全部 /api/ 请求的纯前端用例。
  // 真实账号、业务写入、邮件和模型测试必须使用各自专用配置。
  testMatch: [
    '**/navigation-shell.spec.ts',
    '**/mobile-interactions.spec.ts',
    '**/responsive-overflow.spec.ts',
    '**/student-recent-activity.spec.ts',
    '**/zz-smoke.spec.ts'
  ],
  timeout: 60000,
  expect: {
    timeout: 10000
  },
  // 串行 + 单 worker：避免多个 project 同时启动 dev server 造成端口冲突，
  // 也避免 Home.vue 中 WebSocket / 计时器在并行 context 下产生 flaky 行为
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],

  use: {
    baseURL,
    serviceWorkers: 'block',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    actionTimeout: 10000,
    navigationTimeout: 15000,
    launchOptions: {
      args: [
        '--disable-gpu-shader-disk-cache',
        '--disable-gpu-program-cache'
      ]
    }
  },

  webServer: {
    command: `npm run dev -- --host 127.0.0.1 --port ${port} --strictPort`,
    url: baseURL,
    reuseExistingServer: process.env.E2E_REUSE_SERVER !== 'false',
    timeout: 60000
  },

  projects: [
    {
      name: 'desktop',
      use: {
        browserName: 'chromium',
        channel: 'chrome',
        viewport: { width: 1280, height: 720 }
      }
    },
    {
      name: 'tablet',
      use: {
        browserName: 'chromium',
        channel: 'chrome',
        viewport: { width: 768, height: 1024 }
      }
    },
    {
      name: 'mobile',
      use: {
        browserName: 'chromium',
        channel: 'chrome',
        viewport: { width: 375, height: 667 },
        isMobile: true,
        hasTouch: true
      }
    },
    {
      name: 'webkit-mobile',
      use: { browserName: 'webkit', viewport: { width: 375, height: 667 }, isMobile: true, hasTouch: true }
    },
    {
      name: 'webkit-tablet',
      use: { browserName: 'webkit', viewport: { width: 768, height: 1024 }, isMobile: true, hasTouch: true }
    }
  ]
})
