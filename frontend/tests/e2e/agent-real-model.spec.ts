/**
 * 真实模型 + 受控账号的浏览器端到端验收（不 mock 任何接口）。
 * 走真实登录页 → 真实侧栏 → 真实模型回答 → 截图。
 * 账号：E2EAI0918224457 / 123456（受控、测试后删除）。
 */
import { test, expect } from '@playwright/test'
import path from 'node:path'

const shots = path.resolve('./output/agent-real-e2e-20260918/shots')
const ACCOUNT = process.env.E2E_AI_ACCOUNT || 'E2EAI0918224457'
const PASSWORD = process.env.E2E_AI_PASSWORD || '123456'

test('真实模型：登录 → 侧栏提问 → 得到基于真实数据的回答', async ({ page }) => {
  const apiCalls: string[] = []
  page.on('response', r => {
    const u = new URL(r.url())
    if (u.pathname.startsWith('/api/agent/')) apiCalls.push(`${r.request().method()} ${u.pathname} -> ${r.status()}`)
  })
  const consoleErrors: string[] = []
  page.on('pageerror', e => consoleErrors.push(String(e).slice(0, 200)))

  await page.goto('/login', { waitUntil: 'domcontentloaded' })
  await page.fill("input[placeholder='请输入账号']", ACCOUNT)
  await page.fill("input[placeholder='请输入密码']", PASSWORD)
  await page.screenshot({ path: path.join(shots, 'real-01-login.png') })
  await page.getByRole('button', { name: /登\s*录/ }).first().click()
  await page.waitForURL(/\/student\//, { timeout: 45000 })

  // 打开统一 AI 侧栏
  const entry = page.getByRole('button', { name: 'AI 助手' })
  await expect(entry).toBeVisible({ timeout: 30000 })
  await page.screenshot({ path: path.join(shots, 'real-02-entry-visible.png') })
  await entry.click()

  const composer = page.locator('#agent-input')
  await expect(composer).toBeVisible({ timeout: 20000 })
  await composer.fill('我现在还有什么需要完成？')
  await page.screenshot({ path: path.join(shots, 'real-03-draft.png') })

  await composer.press('Enter')

  // 真实模型链路：等待终态为「已完成」且出现真实回答
  const turn = page.locator('article.agent-turn').first()
  await expect(turn).toBeVisible({ timeout: 30000 })
  await expect(turn.locator('.task-status')).toContainText('已完成', { timeout: 150000 })

  const answer = (await turn.locator('.agent-answer').first().innerText()).trim()
  console.log('REAL_ANSWER_START')
  console.log(answer)
  console.log('REAL_ANSWER_END')
  expect(answer.length).toBeGreaterThan(10)
  await page.screenshot({ path: path.join(shots, 'real-04-answer.png'), fullPage: false })

  // 结果卡（真实工具证据）
  const cards = turn.locator('.result-card, .agent-card, [class*="result"]')
  await page.screenshot({ path: path.join(shots, 'real-05-evidence.png') })
  console.log('AGENT_API_CALLS=' + JSON.stringify(apiCalls))
  console.log('CONSOLE_ERRORS=' + JSON.stringify(consoleErrors.slice(0, 5)))
})