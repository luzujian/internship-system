/**
 * 模拟器触屏焦点测试（真机级 v2）：Pixel6-API34 真实 Chrome，adb input tap。
 * tap 带重试（单次 tap 偶发丢失，重 tap 即人手重试行为）。
 * 核验：tap 获焦 / 键盘弹起可发 / tap 发送端到端 / tap 关闭＋焦点归还 / 遮罩关闭。
 */
import { _android } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { writeFileSync } from 'node:fs'

const root = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.resolve(root, '../../output/agent-optimization-20260918-build')
const BASE = 'http://10.0.2.2:5192'
const USER = process.env.EMU_USER
const PW = process.env.EMU_PW
if (!USER || !PW) throw new Error('EMU_USER/EMU_PW required')

const [device] = await _android.devices()
console.log('device:', device.serial())
const context = await device.launchBrowser()
const page = await context.newPage()
const issues = []
const checks = { tapRetries: {} }
page.on('pageerror', e => issues.push('pageerror: ' + String(e).slice(0, 200)))
page.on('console', m => { if (m.type() === 'error') issues.push('console: ' + m.text().slice(0, 200)) })

let DPR = 1
async function rawTap(x, y) {
  await device.shell(`input tap ${Math.round(x * DPR)} ${Math.round(y * DPR)}`)
}
async function isTopmost(locator) {
  const h = await locator.elementHandle().catch(() => null)
  if (!h) return false
  return h.evaluate(el => {
    const r = el.getBoundingClientRect()
    const t = document.elementFromPoint(r.x + r.width / 2, r.y + r.height / 2)
    return !!t && (t === el || el.contains(t))
  }).catch(() => false)
}
async function tap(locator, expectFn, label) {
  // 若被 Chrome 气泡/弹窗盖住，先按返回键关弹窗再点；tap 后轮询期望状态
  let tries = 0
  for (let i = 0; i < 5; i++) {
    if (!(await isTopmost(locator))) {
      await device.shell('input keyevent 4')
      await page.waitForTimeout(1200)
      checks['popupDismissed_' + label] = true
      continue
    }
    tries++
    const box = await locator.boundingBox()
    if (!box) { await page.waitForTimeout(1500); continue }
    await rawTap(box.x + box.width / 2, box.y + box.height / 2)
    await page.waitForTimeout(1500)
    try {
      if (await expectFn()) { checks.tapRetries[label] = tries; return true }
    } catch { /* 状态未就绪继续 */ }
  }
  checks.tapRetries[label] = tries
  return false
}
const focused = () => page.evaluate(() => {
  const a = document.activeElement
  return a ? `${a.tagName}.${(a.className || '').toString().slice(0, 30)}` : '(none)'
})
const shot = name => page.screenshot({ path: path.join(OUT, 'screenshots', name) })

try {
  DPR = await page.evaluate(() => window.devicePixelRatio || 1).catch(() => 1)
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded', timeout: 60000 })
  DPR = await page.evaluate(() => window.devicePixelRatio || 1)
  console.log('DPR:', DPR)
  await page.getByPlaceholder('请输入账号').fill(USER)
  await page.getByPlaceholder('请输入密码').fill(PW)
  const okLogin = await tap(page.getByRole('button', { name: /登\s*录/ }),
    () => page.evaluate(() => !location.href.includes('/login')), 'login')
  if (!okLogin) throw new Error('login tap failed')
  checks.login = true

  const panel = page.locator('#agent-workspace')
  const okOpen = await tap(page.getByRole('button', { name: 'AI 助手', exact: true }),
    () => panel.isVisible(), 'openPanel')
  if (!okOpen) throw new Error('open panel tap failed')
  const metrics = await page.evaluate(() => ({ w: document.querySelector('#agent-workspace')?.getBoundingClientRect().width || 0, innerW: window.innerWidth }))
  checks.overlayFullWidth = metrics.w >= metrics.innerW - 2
  console.log('panelMetrics:', JSON.stringify(metrics))

  // 1) tap 输入框获焦
  const composer = page.getByLabel('输入实习相关任务')
  const okFocus = await tap(composer,
    () => page.evaluate(() => (document.activeElement?.getAttribute('aria-label') || '') === '输入实习相关任务'), 'focusComposer')
  checks.focusAfterTap = await focused()
  checks.composerFocused = okFocus
  // 键盘状态（visualViewport 收缩 = 软键盘弹起）
  const kb = await page.evaluate(() => ({
    innerH: window.innerHeight,
    vvH: Math.round(window.visualViewport?.height || 0),
    shrunk: window.visualViewport ? window.visualViewport.height < window.innerHeight - 100 : null
  }))
  checks.keyboard = kb
  console.log('keyboard:', JSON.stringify(kb))
  await shot('emu-touch-01-focus.png')

  // 2) 键盘弹起状态点发送（真实提交）
  await composer.fill('触屏测试：你好')
  await page.waitForTimeout(1000)
  const sendBtn = page.getByRole('button', { name: '发送任务', exact: true })
  const sendGeom = await page.evaluate(() => {
    const el = [...document.querySelectorAll('button')].find(b => (b.textContent || '').includes('发送任务'))
    if (!el) return null
    const r = el.getBoundingClientRect()
    return { x: Math.round(r.x), y: Math.round(r.y), w: Math.round(r.width), h: Math.round(r.height), innerH: window.innerHeight, vvH: Math.round(window.visualViewport?.height || 0) }
  })
  checks.sendGeometry = sendGeom
  console.log('sendGeom:', JSON.stringify(sendGeom))
  const okSend = await tap(sendBtn, () => panel.locator('.agent-turn').first().isVisible(), 'sendTask')
  checks.sentViaTouch = okSend
  await shot('emu-touch-02-sent.png')

  // 3) tap 关闭＋焦点归还
  const entryAgain = page.getByRole('button', { name: 'AI 助手', exact: true })
  const okClose = await tap(panel.getByRole('button', { name: '关闭 AI 助手' }),
    () => panel.isHidden(), 'closePanel')
  checks.closedByTap = okClose
  checks.focusAfterClose = await focused()
  await shot('emu-touch-03-closed.png')

  // 4) 重开后 tap 遮罩关闭
  const okReopen = await tap(entryAgain, () => panel.isVisible(), 'reopenPanel')
  checks.reopened = okReopen
  const backdrop = page.locator('.agent-backdrop')
  if (await backdrop.count()) {
    const okBd = await tap(backdrop.first(), () => panel.isHidden(), 'backdropDismiss')
    checks.backdropDismiss = okBd
  } else {
    checks.backdropDismiss = 'no-backdrop'
  }
  checks.focusAfterBackdrop = await focused()
  await shot('emu-touch-04-backdrop.png')

  checks.pageErrors = issues.filter(s => !s.includes('@vite/client'))
} finally {
  writeFileSync(path.join(OUT, 'tests', 'emu-touch.json'), JSON.stringify({ checks, issues }, null, 2), 'utf-8')
  console.log(JSON.stringify(checks, null, 1))
  await context.close()
}
