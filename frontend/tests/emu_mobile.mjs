import { _android } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.resolve(root, '../../output/agent-optimization-20260918-build')
const BASE = 'http://10.0.2.2:5192'
const USER = process.env.EMU_USER
const PW = process.env.EMU_PW
if (!USER || !PW) throw new Error('EMU_USER/EMU_PW required')

const [device] = await _android.devices()
console.log('device:', device.serial())
await device.shell('settings put global window_animation_scale 0')
await device.shell('settings put global transition_animation_scale 0')
const context = await device.launchBrowser()
const page = await context.newPage()
const issues = []
page.on('pageerror', e => issues.push('pageerror: ' + String(e).slice(0, 200)))
page.on('console', m => { if (m.type() === 'error') issues.push('console: ' + m.text().slice(0, 200)) })
page.on('response', async r => {
  if (new URL(r.url()).pathname.startsWith('/api/')) {
    let body = ''
    try { body = (await r.text()).slice(0, 160) } catch { body = '<unreadable>' }
    issues.push(`api ${r.status()} ${new URL(r.url()).pathname} :: ${body}`)
  }
})
const checks = {}
try {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  checks.loginPage = await page.getByPlaceholder('请输入账号').isVisible()
  await page.getByPlaceholder('请输入账号').fill(USER)
  await page.getByPlaceholder('请输入密码').fill(PW)
  await page.screenshot({ path: path.join(OUT, 'screenshots', 'emu-01-login.png') })
  await page.getByRole('button', { name: /登\s*录/ }).click()
  await page.waitForTimeout(6000)
  await page.screenshot({ path: path.join(OUT, 'screenshots', 'emu-00-after-click.png') })
  const toasts = await page.locator('.el-message, .el-notification, [role="alert"]').allInnerTexts()
  console.log('TOASTS:' + JSON.stringify(toasts).slice(0, 500))
  try {
    await page.waitForURL(u => !u.toString().includes('/login'), { timeout: 120000 })
  } catch (e) {
    checks.loginBody = (await page.innerText('body')).slice(0, 200)
    throw e
  }
  checks.loggedIn = true
  await page.screenshot({ path: path.join(OUT, 'screenshots', 'emu-02-home.png') })
  const entry = page.getByRole('button', { name: 'AI 助手', exact: true })
  await entry.waitFor({ state: 'visible', timeout: 30000 })
  checks.agentEntry = true
  await entry.click()
  const panel = page.locator('#agent-workspace')
  await panel.waitFor({ state: 'visible', timeout: 30000 })
  checks.agentPanel = true
  const box = await panel.boundingBox()
  checks.panelWidth = box ? Math.round(box.width) : -1
  const composer = page.getByLabel('输入实习相关任务')
  await composer.fill('我现在有什么待办')
  await page.screenshot({ path: path.join(OUT, 'screenshots', 'emu-03-panel-open.png') })
  checks.composer = await composer.isVisible()
  await page.waitForTimeout(1200)
  await page.screenshot({ path: path.join(OUT, 'screenshots', 'emu-03b-keyboard-up.png') })
  await page.getByRole('button', { name: '发送任务', exact: true }).click({ timeout: 15000 })
  await page.waitForTimeout(20000)
  await page.screenshot({ path: path.join(OUT, 'screenshots', 'emu-04-after-send.png') })
  checks.sent = true
  await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
  await panel.waitFor({ state: 'hidden', timeout: 15000 })
  checks.agentClose = true
  await page.screenshot({ path: path.join(OUT, 'screenshots', 'emu-05-closed.png') })
} catch (e) {
  checks.error = String(e).slice(0, 300)
}
console.log(JSON.stringify({ checks, issues }))
const { writeFileSync } = await import('node:fs')
writeFileSync(path.join(OUT, 'tests', 'emu-mobile.json'), JSON.stringify({ checks, issues }, null, 2))
await context.close()
if (checks.error || issues.length) process.exit(1)
