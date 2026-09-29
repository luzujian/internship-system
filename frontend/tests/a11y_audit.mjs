import { chromium } from '@playwright/test'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.dirname(fileURLToPath(import.meta.url))
const OUT = path.resolve(root, '../../output/agent-optimization-20260918-build')
const BASE = 'http://127.0.0.1:5191'
const findings = []
const notes = []

function lum(r, g, b) {
  const f = c => { c /= 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4) }
  return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)
}
function ratio(fg, bg) {
  const l1 = lum(...fg), l2 = lum(...bg)
  const [hi, lo] = l1 > l2 ? [l1, l2] : [l2, l1]
  return (hi + 0.05) / (lo + 0.05)
}
function parseRgb(s) {
  const m = s.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)/)
  return m ? [ +m[1], +m[2], +m[3] ] : null
}

const browser = await chromium.launch({ headless: true })
const page = await browser.newPage({ viewport: { width: 1600, height: 900 }, baseURL: BASE })
page.on('pageerror', e => findings.push({ type: 'pageerror', detail: String(e).slice(0, 200) }))

const traffic = []
await page.routeWebSocket(/.*/, s => s.close())
await page.route('**/*', async route => {
  const u = new URL(route.request().url())
  if (u.origin !== 'http://127.0.0.1:5191') return route.abort()
  if (u.pathname.startsWith('/api/')) {
    let data = []
    if (u.pathname === '/api/agent/capabilities') data = { enabled: true, actor: { ownerKey: 'mock:1', id: '1', name: 'a11y', role: 'ROLE_COMPANY', readOnly: false }, capabilities: { agent_chat: true, form_fill: true, voice_input: true }, tools: [] }
    if (u.pathname === '/api/majors') data = [{ id: 7, name: '软件工程' }]
    return route.fulfill({ json: { code: 200, data } })
  }
  return route.continue()
})
await page.goto('/batch1-fill')
await page.getByRole('button', { name: '发布岗位', exact: true }).click()
const dialog = page.getByRole('dialog', { name: '发布岗位', exact: true })
await dialog.waitFor({ state: 'visible' })
await dialog.getByRole('button', { name: 'AI 助手', exact: true }).click()
await page.locator('#agent-workspace').waitFor({ state: 'visible' })

const pairs = await page.evaluate(() => {
  const out = []
  const push = (label, el, large) => {
    if (!el) { out.push({ label, missing: true }); return }
    const cs = getComputedStyle(el)
    out.push({ label, fg: cs.color, bg: cs.backgroundColor, fontSize: cs.fontSize, fontWeight: cs.fontWeight, large: !!large })
  }
  const q = s => document.querySelector('#agent-workspace ' + s)
  const effBg = el => {
    let n = el
    while (n && n !== document.documentElement) {
      const bg = getComputedStyle(n).backgroundColor
      const m = bg.match(/rgba?\((\d+),\s*(\d+),\s*(\d+)(?:,\s*([\d.]+))?/)
      if (m && (m[4] === undefined || parseFloat(m[4]) >= 0.99)) return [ +m[1], +m[2], +m[3] ]
      n = n.parentElement
    }
    return [255, 255, 255]
  }
  window.__effBg = effBg
  push('panel-title', q('.agent-header h2'), true)
  push('panel-subtitle', q('.agent-header p'), false)
  push('toolbar-user', q('.agent-toolbar span'), false)
  push('context-title', q('.agent-context strong'), false)
  push('welcome-h3', q('.agent-welcome h3'), true)
  push('welcome-p', q('.agent-welcome p'), false)
  push('suggestion-btn', q('.agent-suggestions button span:nth-child(2)'), false)
  push('boundary-note', q('.welcome-boundary'), false)
  push('composer-hint', q('.composer-hint span'), false)
  push('close-btn', q('.agent-close'), false)
  push('form-link-title', q('.agent-form-link strong'), false)
  push('form-link-icon', q('.agent-form-link .form-link-icon'), true)
  push('form-link-sub', q('.agent-form-link div span'), false)
  return out
})
const contrastResults = []
const effBgs = await page.evaluate(() => {
  const out = {}
  const names = ['panel-title', 'panel-subtitle', 'toolbar-user', 'context-title', 'welcome-h3', 'welcome-p', 'suggestion-btn', 'boundary-note', 'composer-hint',   'close-btn', 'form-link-title', 'form-link-icon', 'form-link-sub']
  const sels = ['.agent-header h2', '.agent-header p', '.agent-toolbar span', '.agent-context strong', '.agent-welcome h3', '.agent-welcome p', '.agent-suggestions button span:nth-child(2)', '.welcome-boundary', '.composer-hint span', '.agent-close', '.agent-form-link strong', '.agent-form-link .form-link-icon', '.agent-form-link div span']
  sels.forEach((s, i) => {
    const el = document.querySelector('#agent-workspace ' + s)
    out[names[i]] = el ? window.__effBg(el) : null
  })
  return out
})
for (const p of pairs) {
  if (p.missing) { contrastResults.push({ ...p, pass: false, reason: 'missing' }); continue }
  const fg = parseRgb(p.fg)
  const bg = effBgs[p.label] || parseRgb(p.bg)
  if (!fg || !bg) { contrastResults.push({ ...p, pass: null, reason: 'unparsed-color' }); continue }
  const r = ratio(fg, bg)
  const need = p.large ? 3.0 : 4.5
  contrastResults.push({ label: p.label, fg: p.fg, bg: p.bg, ratio: Math.round(r * 100) / 100, need, pass: r >= need })
}

const tabOrder = []
await page.locator('#agent-workspace').evaluate(el => el.setAttribute('tabindex', '-1'))
await page.keyboard.press('Tab')
for (let i = 0; i < 40; i++) {
  const info = await page.evaluate(() => {
    const el = document.activeElement
    if (!el) return null
    return { tag: el.tagName, label: el.getAttribute('aria-label') || (el.innerText || el.value || '').slice(0, 24), visible: !!(el.offsetWidth || el.offsetHeight) }
  })
  tabOrder.push(info)
  if (info && info.label.includes('关闭 AI 助手') && i > 5) break
  await page.keyboard.press('Tab')
}
const focusVisible = await page.evaluate(() => {
  const el = document.activeElement
  const o = getComputedStyle(el)
  return { outline: o.outlineWidth + ' ' + o.outlineStyle, boxShadow: (o.boxShadow || '').slice(0, 40) }
})

const semantics = await page.evaluate(() => {
  const ws = document.querySelector('#agent-workspace')
  const btns = [...ws.querySelectorAll('button')].map(b => ({
    name: (b.getAttribute('aria-label') || b.innerText || '').slice(0, 20),
    labelled: !!(b.getAttribute('aria-label') || (b.innerText || '').trim())
  }))
  return {
    role: ws.getAttribute('role'),
    labelledby: ws.getAttribute('aria-labelledby'),
    modal: ws.getAttribute('aria-modal'),
    h1count: document.querySelectorAll('h1').length,
    h2inh2: [...ws.querySelectorAll('h2,h3')].map(h => h.tagName + ':' + (h.innerText || '').slice(0, 16)),
    imgAlts: [...document.querySelectorAll('#agent-workspace img')].map(i => i.getAttribute('alt')),
    liveRegions: [...ws.querySelectorAll('[role="status"],[role="alert"]')].length,
    unlabeledButtons: btns.filter(b => !b.labelled),
    composerLabelled: !!document.querySelector('label[for="agent-input"]'),
    htmlLang: document.documentElement.getAttribute('lang')
  }
})
await page.keyboard.press('Escape')
const closedByEsc = await page.locator('#agent-workspace').isHidden().catch(() => false)
await page.getByRole('dialog').getByRole('button', { name: '取消', exact: true }).click()
await page.setViewportSize({ width: 390, height: 844 })
await page.getByRole('button', { name: 'AI 助手', exact: true }).first().click()
await page.locator('#agent-workspace').waitFor({ state: 'visible' })
await page.getByLabel('输入实习相关任务').click()
let trapOk = true
for (let i = 0; i < 30; i++) {
  await page.keyboard.press('Tab')
  const inside = await page.evaluate(() => {
    const ws = document.querySelector('#agent-workspace')
    return !!ws?.contains(document.activeElement)
  })
  if (!inside) { trapOk = false; break }
}
notes.push('narrow-overlay-tab-trap: ' + (trapOk ? 'PASS' : 'FAIL'))
await page.screenshot({ path: path.join(OUT, 'screenshots', 'a11y-panel.png'), fullPage: false })

console.log(JSON.stringify({ contrastResults, tabOrder: tabOrder.slice(0, 25), focusVisible, semantics, closedByEsc }, null, 1))
const { writeFileSync } = await import('node:fs')
writeFileSync(path.join(OUT, 'tests', 'a11y-audit.json'), JSON.stringify({ contrastResults, tabOrder, focusVisible, semantics, closedByEsc, findings, notes }, null, 1))
await browser.close()
