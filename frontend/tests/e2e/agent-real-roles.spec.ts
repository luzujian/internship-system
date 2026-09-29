/**
 * 真实链路多角色回归（真实项目账号）：教师(院级/系级/辅导员) + 企业 + 管理员。
 * 真实前端 + 真实后端 + 真实模型，不 mock 任何接口。
 * 凭据一律从环境变量读取，绝不写入仓库文件。
 */
import { test, expect, type Page } from '@playwright/test'
import path from 'node:path'

const shots = path.resolve('./output/agent-real-roles-20260918/shots')

interface Case { key: string; account: string; password: string; route: RegExp; question: string }
const CASES: Case[] = [
  { key: 'teacher-college', account: 't001', password: process.env.E2E_PW_TEACHER || '', route: /\/teacher\//, question: '我们学院实习情况怎么样？' },
  { key: 'teacher-department', account: 't002', password: process.env.E2E_PW_TEACHER || '', route: /\/teacher\//, question: '我们系学生的实习情况怎么样？' },
  { key: 'teacher-counselor', account: 't003', password: process.env.E2E_PW_TEACHER || '', route: /\/teacher\//, question: '我负责的班级实习情况怎么样？' },
  { key: 'company', account: '企业1', password: process.env.E2E_PW_COMPANY || '', route: /\/company\//, question: '我们公司收到了哪些申请？' },
  { key: 'admin', account: 'admin', password: process.env.E2E_PW_ADMIN || '', route: /\/admin\//, question: '系统里现在有多少待审核的企业？' }
]

async function login(page: Page, account: string, password: string, route: RegExp) {
  await page.goto('/login', { waitUntil: 'domcontentloaded' })
  await page.fill("input[placeholder='请输入账号']", account)
  await page.fill("input[placeholder='请输入密码']", password)
  await page.getByRole('button', { name: /登\s*录/ }).first().click()
  await page.waitForURL(route, { timeout: 45000 })
}

for (const c of CASES) {
  test(`真实链路 · ${c.key}（${c.account}）`, async ({ page }) => {
    const agentCalls: string[] = []
    page.on('response', r => {
      const u = new URL(r.url())
      if (u.pathname.startsWith('/api/agent/') && !u.pathname.includes('/events')) {
        agentCalls.push(`${r.request().method()} ${u.pathname} ${r.status()}`)
      }
    })
    const errors: string[] = []
    page.on('pageerror', e => errors.push(String(e).slice(0, 160)))

    await login(page, c.account, c.password, c.route)
    await page.screenshot({ path: path.join(shots, `real-${c.key}-01-home.png`) })

    const entry = page.getByRole('button', { name: 'AI 助手' })
    await expect(entry).toBeVisible({ timeout: 30000 })
    await entry.click()

    const composer = page.locator('#agent-input')
    await expect(composer).toBeVisible({ timeout: 20000 })
    await composer.fill(c.question)
    await composer.press('Enter')

    const turn = page.locator('article.agent-turn').first()
    await expect(turn).toBeVisible({ timeout: 30000 })
    await expect(turn.locator('.task-status')).toContainText('已完成', { timeout: 180000 })

    const answer = (await turn.locator('.agent-answer').first().innerText()).trim()
    console.log(`REAL_${c.key.toUpperCase()}_ANSWER_START`)
    console.log(answer)
    console.log(`REAL_${c.key.toUpperCase()}_ANSWER_END`)
    expect(answer.length).toBeGreaterThan(8)
    await page.screenshot({ path: path.join(shots, `real-${c.key}-02-answer.png`) })
    console.log(`REAL_${c.key.toUpperCase()}_CALLS=` + JSON.stringify(agentCalls))
    console.log(`REAL_${c.key.toUpperCase()}_ERRORS=` + JSON.stringify(errors.slice(0, 3)))
  })
}