import { defineConfig } from '@playwright/test'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('.', import.meta.url))
const output = path.resolve(root, '../output/agent-optimization-20260918-build')

/**
 * BATCH4 受控 harness：真实 Vue 组件 + mock 后端，不需要模型密钥，也不连真实后端。
 * 用于验证统一侧栏的编辑/版本/历史/停止等交互在真实浏览器中的表现（含窄屏）。
 */
const harness = `
import { createApp, h, reactive } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import AgentWorkspace from '/src/components/agent/AgentWorkspace.vue'
import AgentEntry from '/src/components/agent/AgentEntry.vue'
import { useAgentStore } from '/src/agent/store.ts'
import { register, unregister, beginFormOperation } from '/src/agent/formContext.ts'
import { useAuthStore } from '/src/store/auth/index.ts'
const pinia = createPinia()
// AgentWorkspace 依赖 useRoute/useRouter：受控 harness 必须提供路由，否则应用无法挂载。
const router = createRouter({ history: createMemoryHistory(), routes: [
  { path: '/student/home', component: { render: () => h('p', '受控首页') } },
  { path: '/company/jobs', component: { render: () => h('p', '受控岗位列表') } },
  { path: '/:pathMatch(.*)*', component: { render: () => h('p', '受控路由空页面') } }
] })
await router.push('/student/home')
const app = createApp({ render: () => h('div', { class: 'agent-host' }, [h('header', { style: 'height:64px;background:#264a72;color:white;display:flex;align-items:center' }, [h('span', '受控 mock · 无真实后端'), h(AgentEntry)]), h('main', [h('button', { id: 'business-button', onClick: () => { window.__businessClicks = (window.__businessClicks || 0) + 1 } }, '原页面操作')]), h(AgentWorkspace)]) })
// 侧栏靠 auth.token 的 watcher 触发 capabilities 加载；受控 harness 必须直接 patch 认证状态。
const auth = useAuthStore(pinia)
auth.$patch({ token: 'batch4-mock-token', role: 'ROLE_STUDENT', user: { id: '1', name: '受控 mock 学生', username: 'batch4' }, isAuthenticated: true })
app.use(pinia).use(router).use(ElementPlus)
localStorage.setItem('student_accessToken_ROLE_STUDENT', 'batch4-mock-token')
localStorage.setItem('student_token_ROLE_STUDENT', 'batch4-mock-token')
localStorage.setItem('current_role', 'ROLE_STUDENT')
sessionStorage.setItem('deepintern.agent.session:mock:1', 'mock-session')
app.mount('#app')
window.__batch4 = { agent: useAgentStore(pinia), register, unregister, reactive, beginFormOperation, router }
`

if (process.argv.includes('--serve')) {
  const server = await createServer({
    configFile: false,
    envFile: false,
    root,
    plugins: [vue(), {
      name: 'batch4-controlled-harness',
      resolveId(id) { if (id === '/@batch4-harness') return '\0batch4-harness' },
      load(id) { if (id === '\0batch4-harness') return harness },
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url?.startsWith('/api/')) {
            res.statusCode = 503
            res.end('BATCH4_MOCK_REQUIRED')
            return
          }
          if (req.url === '/batch4') {
            res.setHeader('Content-Type', 'text/html')
            res.end('<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>BATCH4 受控回归</title></head><body style="margin:0"><div id="app"></div><script type="module" src="/@batch4-harness"></script></body></html>')
            return
          }
          next()
        })
      }
    }],
    resolve: { alias: { '@': path.join(root, 'src') } },
    define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('/api') },
    server: { host: '127.0.0.1', port: 5193, strictPort: true, hmr: false }
  })
  await server.listen()
  process.on('SIGINT', () => { void server.close(); process.exit(0) })
  process.on('SIGTERM', () => { void server.close(); process.exit(0) })
  setInterval(() => {}, 1 << 30)
}

export default defineConfig({
  testDir: './tests/e2e',
  testMatch: /agent-batch4-(ui|ime)\.spec\.ts/,
  timeout: 40000,
  expect: { timeout: 5000 },
  workers: 1,
  retries: 0,
  outputDir: path.join(output, 'tests', 'batch4-artifacts'),
  reporter: [['list'], ['json', { outputFile: path.join(output, 'tests', 'batch4.json') }]],
  use: { baseURL: 'http://127.0.0.1:5193', headless: true, serviceWorkers: 'block', viewport: { width: 1440, height: 900 }, actionTimeout: 6000, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: { command: 'node --experimental-strip-types playwright.batch4.config.ts --serve', url: 'http://127.0.0.1:5193/batch4', reuseExistingServer: false, timeout: 90000 }
})

