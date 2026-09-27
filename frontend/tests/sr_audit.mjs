/**
 * 读屏器视角审计 v2：使用 locator.ariaSnapshot()（Playwright 1.62 无障碍树快照，
 * 与读屏器消费的 role+name 数据同源）。
 * 真实教师登录 → 真实侧栏 → 核验：
 *  1. 可交互控件 role+可访问名称（无名 = 读屏器只报"按钮"）
 *  2. 地标与标题层级、html lang
 *  3. 真实发一句话后 live 播报进入树
 *  4. 面板线性朗读顺序
 */
import { chromium } from '@playwright/test'
import { writeFileSync } from 'node:fs'
import path from 'node:path'

const OUT = path.resolve('../output/agent-optimization-20260918-build/tests/sr-audit.json')
const BASE = 'http://127.0.0.1:5192'
const INTERACTIVE = new Set(['button', 'link', 'textbox', 'combobox', 'checkbox', 'radio', 'tab', 'switch', 'slider', 'spinbutton', 'searchbox'])

function parseSnap(yaml) {
  // 行形如: - button "发送任务" [disabled]: ↑ / - status: ✓已完成 / - heading "x" [level=2]
  // 名称优先取引号内（可访问名称），无引号则取冒号后尾巴文本
  const nodes = []
  for (const line of String(yaml).split('\n')) {
    const m = line.match(/^(\s*)- ([A-Za-z\/]+)(.*)$/)
    if (!m) continue
    const rest = m[3] || ''
    const attrs = {}
    const am = rest.match(/\[([^\]]*)\]/)
    if (am) for (const kv of am[1].split(' ')) { const eq = kv.indexOf('='); attrs[eq < 0 ? kv : kv.slice(0, eq)] = eq < 0 ? '' : kv.slice(eq + 1) }
    let name = ''
    const qm = rest.match(/"((?:[^"\\]|\\.)*)"/)
    if (qm) name = qm[1].replace(/\\"/g, '"')
    else {
      const tm = rest.match(/:\s*(.+?)\s*$/)
      if (tm) name = tm[1]
    }
    nodes.push({ depth: m[1].length / 2, role: m[2], name: name.slice(0, 80), attrs })
  }
  return nodes
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 900 } })
await page.routeWebSocket(/.*/, s => s.close())
const issues = []
const notes = []
try {
  await page.goto(`${BASE}/login`, { waitUntil: 'domcontentloaded' })
  await page.fill("input[placeholder='请输入账号']", 't001')
  await page.fill("input[placeholder='请输入密码']", '123456')
  await Promise.all([
    page.waitForURL(u => !String(u).includes('/login'), { timeout: 30000 }),
    page.click("button:has-text('登 录')")
  ])
  await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {})
  notes.push('login-landed: ' + page.url())

  const lang = await page.evaluate(() => document.documentElement.lang)
  const title = await page.evaluate(() => document.title)
  notes.push(`html-lang: ${lang || '(missing)'} / title: ${(title || '').slice(0, 40)}`)
  if (!lang) issues.push('html lang 缺失（读屏器选语音引擎依赖它）')

  const fullNodes = parseSnap(await page.locator('body').ariaSnapshot())
  const landmarks = fullNodes.filter(n => ['banner', 'navigation', 'main', 'complementary', 'contentinfo', 'search'].includes(n.role))
  const headings = fullNodes.filter(n => n.role === 'heading')
  notes.push('landmarks: ' + JSON.stringify(landmarks.map(l => `${l.role}:${l.name.slice(0, 16)}`)))
  notes.push('headings: ' + JSON.stringify(headings.map(h => `h${h.attrs.level || '?'}:${h.name.slice(0, 20)}`)))
  if (landmarks.filter(l => l.role === 'main').length !== 1) issues.push('main 地标不是恰好 1 个')
  let last = 0
  for (const h of headings) {
    const lv = parseInt(h.attrs.level || '0', 10)
    if (lv - last > 1) { issues.push(`标题层级跳跃：跳到 h${lv}「${h.name.slice(0, 20)}」`); break }
    last = Math.max(last, lv)
  }

  await page.getByRole('button', { name: 'AI 助手' }).first().click()
  await page.locator('#agent-workspace').waitFor({ state: 'visible', timeout: 15000 })

  for (const tabName of [null, '自动化任务']) {
    if (tabName) {
      const t = page.locator('#agent-workspace').getByRole('tab', { name: tabName })
      if (!(await t.count())) { notes.push(tabName + ': tab 不存在，跳过'); continue }
      await t.click()
    }
    const label = tabName || '对话问答'
    const nodes = parseSnap(await page.locator('#agent-workspace').ariaSnapshot())
    const inter = nodes.filter(n => INTERACTIVE.has(n.role))
    const unnamed = inter.filter(n => !n.name)
    notes.push(`${label}: 交互控件 ${inter.length}，未命名 ${unnamed.length}`)
    for (const u of unnamed) issues.push(`${label}页未命名控件：role=${u.role}`)
    if (!tabName) {
      const order = nodes.filter(n => n.name || INTERACTIVE.has(n.role)).slice(0, 40)
        .map(n => `${n.role}:${n.name.slice(0, 18)}`)
      writeFileSync(OUT + '.tmp', 'x')
      notes.push('reading-order-probed: true')
      globalThis.__order = order
    }
  }

  // 真实对话，检查 live 播报
  await page.locator('#agent-workspace').getByRole('tab', { name: '对话问答' }).click().catch(() => {})
  await page.getByLabel('输入实习相关任务').fill('你好，请用一句话介绍你能做什么')
  await page.getByRole('button', { name: '发送任务', exact: true }).click()
  let liveText = ''
  for (let i = 0; i < 40; i++) {
    await page.waitForTimeout(3000)
    const snap = await page.locator('#agent-workspace').ariaSnapshot()
    const nodes = parseSnap(snap)
    const live = nodes.filter(n => (n.attrs.live || n.role === 'status' || n.role === 'alert') && n.name)
    if (live.length) { liveText = live.map(l => `${l.role}:${l.name.slice(0, 60)}`).join(' | '); break }
  }
  notes.push('live-after-chat: ' + (liveText || '(none within 120s)'))
  if (!liveText) issues.push('真实对话后 120s 内无 live 文本进入无障碍树（读屏器听不到进度/结果播报）')

  const payload = { issues, notes, readingOrder: globalThis.__order || [], at: new Date().toISOString() }
  writeFileSync(OUT, JSON.stringify(payload, null, 2), 'utf-8')
  try { (await import('node:fs')).unlinkSync(OUT + '.tmp') } catch {}
  console.log('ISSUES(' + issues.length + '):')
  for (const i of issues) console.log(' -', i)
  console.log('NOTES:')
  for (const n of notes) console.log(' *', n)
} finally {
  await browser.close()
}
