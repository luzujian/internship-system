import { chromium } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.resolve(root, '../../output/agent-optimization-20260918-build')
const BASE = 'http://127.0.0.1:5192'
const USER = process.env.SMOKE_USER
const PW = process.env.SMOKE_PW
if (!USER || !PW) throw new Error('SMOKE_USER/SMOKE_PW required')

const BROWSERS = [
  { name: 'chromium-bundled', opts: { headless: true } },
  { name: 'edge', opts: { headless: true, channel: 'msedge' } },
  { name: 'chrome', opts: { headless: true, channel: 'chrome' } },
]
const results = []
for (const b of BROWSERS) {
  const r = { browser: b.name, pageerrors: [], failedRequests: [], checks: {} }
  let browser = null
  try {
    browser = await chromium.launch(b.opts)
    const page = await browser.newPage({ viewport: { width: 1600, height: 900 } })
    page.on('pageerror', e => r.pageerrors.push(String(e).slice(0, 200)))
    page.on('requestfailed', req => r.failedRequests.push(req.url().slice(0, 120)))
    await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 30000 })
    r.checks.loginPage = await page.getByPlaceholder('请输入账号').isVisible()
    await page.getByPlaceholder('请输入账号').fill(USER)
    await page.getByPlaceholder('请输入密码').fill(PW)
    await page.getByRole('button', { name: /登\s*录/ }).click()
    await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 30000 })
    r.checks.loggedIn = true
    const entry = page.getByRole('button', { name: 'AI 助手', exact: true })
    await expect_visible(entry)
    r.checks.agentEntry = true
    await entry.click()
    const panel = page.locator('#agent-workspace')
    await panel.waitFor({ state: 'visible', timeout: 15000 })
    r.checks.agentPanel = true
    const composer = page.getByLabel('输入实习相关任务')
    r.checks.composer = await composer.isVisible()
    await page.screenshot({ path: path.join(OUT, 'screenshots', `xbrowser-${b.name}-panel.png`), fullPage: false })
    await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
    await panel.waitFor({ state: 'hidden', timeout: 10000 })
    r.checks.agentClose = true
    r.pass = r.pageerrors.length === 0
  } catch (e) {
    r.error = String(e).slice(0, 300)
    r.pass = false
  } finally {
    await browser?.close()
  }
  results.push(r)
  console.log(JSON.stringify(r))
}
const { writeFileSync } = await import('node:fs')
writeFileSync(path.join(OUT, 'tests', 'xbrowser-smoke.json'), JSON.stringify({ results }, null, 2))
if (results.some(r => !r.pass)) process.exit(1)

async function expect_visible(loc) {
  const t0 = Date.now()
  while (Date.now() - t0 < 15000) {
    if (await loc.isVisible()) return
    await new Promise(r => setTimeout(r, 300))
  }
  throw new Error('element not visible: AI entry')
}
