import { defineConfig } from '@playwright/test'
import { createServer } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = fileURLToPath(new URL('.', import.meta.url))
const output = path.resolve(root, '../output/agent-optimization-20260918-build')
const phase = process.env.BATCH1_PHASE || 'after'
const harness = `
import { createApp, h } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createMemoryHistory, RouterView, RouterLink } from 'vue-router'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import AgentWorkspace from '/src/components/agent/AgentWorkspace.vue'
import AgentEntry from '/src/components/agent/AgentEntry.vue'
import RecruitmentManagement from '/src/views/company/RecruitmentManagement.vue'
import InternshipRegistration from '/src/views/student/InternshipRegistration.vue'
import TeacherSettings from '/src/views/teacher/Settings.vue'
import { Plus } from '@element-plus/icons-vue'
import { useAuthStore } from '/src/store/auth/index.ts'
import { useAgentStore } from '/src/agent/store.ts'
const pinia = createPinia()
const fillHarness = location.pathname === '/batch1-fill'
const router = createRouter({ history: createMemoryHistory(), routes: [
  { path: '/company/recruitment', component: RecruitmentManagement },
  { path: '/student/registration', component: InternshipRegistration },
  { path: '/teacher/settings', component: TeacherSettings },
  { path: '/:pathMatch(.*)*', component: { render: () => h('p', '受控路由空页面') } }
] })
await router.push('/company/recruitment')
const auth = useAuthStore(pinia)
auth.$patch({ token: 'batch1-mock-token', role: location.pathname === '/batch1-teacher' ? 'ROLE_TEACHER' : 'ROLE_COMPANY', user: { id: '1', name: '受控 mock 企业', username: 'batch1' }, isAuthenticated: true })
const app = createApp({ render: () => h('div', { class: 'agent-host' }, [h('header', { style: 'height:64px;background:#264a72;color:white;display:flex;align-items:center' }, [h('span', '受控 mock · 无真实后端'), h(AgentEntry)]), fillHarness ? h(RouterView) : h(RecruitmentManagement), fillHarness ? h(RouterLink, { to: '/company/other' }, () => '受控切换路由') : null, h(AgentWorkspace)]) })
app.use(pinia).use(router).use(ElementPlus)
app.component('Plus', Plus)
localStorage.setItem('company_accessToken_ROLE_COMPANY', 'batch1-mock-token')
localStorage.setItem('company_token_ROLE_COMPANY', 'batch1-mock-token')
localStorage.setItem('current_role', 'ROLE_COMPANY')
app.mount('#app')
window.__batch1 = { auth, agent: useAgentStore(pinia), router, unmount: () => app.unmount() }
`

if (process.argv.includes('--serve')) {
  const server = await createServer({
    configFile: false,
    envFile: false,
    root,
    plugins: [vue(), {
      name: 'batch1-controlled-harness',
      resolveId(id) { if (id === '/@batch1-harness') return '\0batch1-harness' },
      load(id) { if (id === '\0batch1-harness') return harness },
      configureServer(server) {
        server.middlewares.use((req, res, next) => {
          if (req.url?.startsWith('/api/')) {
            res.statusCode = 503
            res.end('BATCH1_MOCK_REQUIRED')
            return
          }
          if (req.url === '/batch1' || req.url === '/batch1-fill' || req.url === '/batch1-teacher') {
            res.setHeader('Content-Type', 'text/html')
            res.end('<html><head><meta name="viewport" content="width=device-width, initial-scale=1"><title>受控 mock 回归</title></head><body style="margin:0"><div id="app"></div><script type="module" src="/@batch1-harness"></script></body></html>')
            return
          }
          next()
        })
      }
    }],
    resolve: { alias: { '@': path.join(root, 'src') } },
    define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('/api') },
    server: { host: '127.0.0.1', port: 5191, strictPort: true, hmr: false }
  })
  await server.listen()
  process.on('SIGINT', () => { void server.close(); process.exit(0) })
  process.on('SIGTERM', () => { void server.close(); process.exit(0) })
  setInterval(() => {}, 1 << 30)
}

export default defineConfig({
  testDir: './tests/e2e',
  testMatch: /agent-batch1-(ui|fill)\.spec\.ts|agent-mobile-card\.spec\.ts/,
  timeout: 30000,
  expect: { timeout: 4000 },
  workers: 1,
  retries: 0,
  outputDir: path.join(output, 'tests', `batch1-${phase}-artifacts`),
  reporter: [['list'], ['json', { outputFile: path.join(output, 'tests', `batch1-${phase}.json`) }]],
  use: { baseURL: 'http://127.0.0.1:5191', headless: true, serviceWorkers: 'block', viewport: { width: 1600, height: 900 }, actionTimeout: 5000, trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  webServer: { command: 'node --experimental-strip-types playwright.batch1.config.ts --serve', url: 'http://127.0.0.1:5191/batch1', reuseExistingServer: false, timeout: 60000 }
})
