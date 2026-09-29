import { defineConfig } from '@playwright/test'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('.', import.meta.url))
const output = path.resolve(root, '../output/agent-optimization-20260918-build')
// 复用 batch1 受控 harness 模式：真实 Vue 组件（AgentWorkspace/AgentEntry）+ 全 API mock，无真实后端。
const harness = `
import { createApp, h } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import AgentWorkspace from '/src/components/agent/AgentWorkspace.vue'
import AgentEntry from '/src/components/agent/AgentEntry.vue'
import { useAuthStore } from '/src/store/auth/index.ts'
import { useAgentStore } from '/src/agent/store.ts'
const pinia = createPinia()
const isAdmin = location.pathname === '/flow01-admin'
const role = isAdmin ? 'ROLE_ADMIN' : 'ROLE_TEACHER_COUNSELOR'
const router = createRouter({ history: createMemoryHistory(), routes: [
  { path: '/teacher/home', component: { render: () => h('p', '受控教师首页（手动页保留，仅占位）') } },
  { path: '/admin/permissions', component: { render: () => h('p', '受控权限管理页（手动矩阵保留，仅占位）') } },
  { path: '/:pathMatch(.*)*', component: { render: () => h('p', '受控路由空页') } }
] })
await router.push(isAdmin ? '/admin/permissions' : '/teacher/home')
const auth = useAuthStore(pinia)
auth.$patch({ token: 'flow01-mock-token', role, user: { id: isAdmin ? '900' : '49', name: isAdmin ? '受控 mock 管理员' : '受控 mock 教师', username: isAdmin ? 'flow01admin' : 'flow01teacher' }, isAuthenticated: true })
localStorage.setItem('current_role', role)
if (isAdmin) {
  localStorage.setItem('admin_accessToken_ROLE_ADMIN', 'flow01-mock-token')
  localStorage.setItem('admin_token_ROLE_ADMIN', 'flow01-mock-token')
} else {
  localStorage.setItem('teacher_accessToken_ROLE_TEACHER_COUNSELOR', 'flow01-mock-token')
  localStorage.setItem('teacher_token_ROLE_TEACHER_COUNSELOR', 'flow01-mock-token')
}
const app = createApp({ render: () => h('div', { class: 'agent-host' }, [h('header', { style: 'height:64px;background:#264a72;color:white;display:flex;align-items:center;gap:12px;padding:0 16px' }, [h('span', '受控 mock · FLOW-01 无真实后端'), h(AgentEntry)]), h(RouterView), h(AgentWorkspace)]) })
app.use(pinia).use(router).use(ElementPlus)
app.mount('#app')
window.__flow01 = { auth, agent: useAgentStore(pinia), router, unmount: () => app.unmount() }
`

if (process.argv.includes('--serve')) {
  const server = await createServer({
    configFile: false,
    envFile: false,
    root,
    plugins: [vue(), {
      name: 'flow01-controlled-harness',
      resolveId(id) { if (id === '/@flow01-harness') return '\0flow01-harness' },
      load(id) { if (id === '\0flow01-harness') return harness },
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url?.startsWith('/api/')) {
            res.statusCode = 503
            res.end('FLOW01_MOCK_REQUIRED')
            return
          }
          if (req.url === '/flow01-teacher' || req.url === '/flow01-admin') {
            res.setHeader('Content-Type', 'text/html')
            res.end('<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>FLOW-01 受控 mock 回归</title></head><body style="margin:0"><div id="app"></div><script type="module" src="/@flow01-harness"></script></body></html>')
            return
          }
          next()
        })
      }
    }],
    resolve: { alias: { '@': path.join(root, 'src') } },
    define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('/api') },
    server: { host: '127.0.0.1', port: 5192, strictPort: true, hmr: false }
  })
  await server.listen()
  process.on('SIGINT', () => { void server.close(); process.exit(0) })
  process.on('SIGTERM', () => { void server.close(); process.exit(0) })
  setInterval(() => {}, 1 << 30)
}

export default defineConfig({
  testDir: './tests/e2e',
  testMatch: /agent-flow01\.spec\.ts/,
  timeout: 30000,
  expect: { timeout: 4000 },
  workers: 1,
  retries: 0,
  outputDir: path.join(output, 'tests', 'flow01-artifacts'),
  reporter: [['list'], ['json', { outputFile: path.join(output, 'tests', 'flow01.json') }]],
  use: { baseURL: 'http://127.0.0.1:5192', headless: true, serviceWorkers: 'block', viewport: { width: 1600, height: 900 }, actionTimeout: 5000, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: { command: 'node --experimental-strip-types playwright.flow01.config.ts --serve', url: 'http://127.0.0.1:5192/flow01-teacher', reuseExistingServer: false, timeout: 60000 }
})
