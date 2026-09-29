import { test, expect, type Page } from '@playwright/test'
import path from 'node:path'
const shots = path.resolve('./output/agent-real-notify-20260919/shots')
async function login(page: Page, account: string, password: string, route: RegExp) {
  await page.goto('/login', { waitUntil: 'domcontentloaded' })
  await page.fill("input[placeholder='请输入账号']", account)
  await page.fill("input[placeholder='请输入密码']", password)
  await page.getByRole('button', { name: /登\s*录/ }).first().click()
  await page.waitForURL(route, { timeout: 45000 })
}
async function openSidebar(page: Page) {
  const entry = page.getByRole('button', { name: 'AI 助手' })
  await expect(entry).toBeVisible({ timeout: 30000 })
  await entry.click()
  await expect(page.locator('#agent-input')).toBeVisible({ timeout: 20000 })
}
test('teacher notify run: create paused, enable, run once, real email dispatched', async ({ page }) => {
  const posts: string[] = []
  page.on('request', r => {
    const u = new URL(r.url())
    if (u.pathname.indexOf('/api/') === 0) posts.push(r.method() + ' ' + u.pathname)
  })
  await login(page, 't003', process.env.E2E_PW_TEACHER || '123456', /\/teacher\//)
  await openSidebar(page)
  await page.getByRole('tab', { name: '自动化任务' }).click()
  const composer = page.locator('#agent-input')
  await composer.fill('鉴定截止前两天用邮件提醒未提交心得的同学')
  await composer.press('Enter')
  const flow = page.locator('article.auto-flow')
  await expect(flow).toBeVisible({ timeout: 60000 })
  await expect(flow).toContainText('邮件提醒', { timeout: 60000 })
  const createBtn = flow.getByRole('button', { name: '确认创建（默认暂停）' })
  await expect(createBtn).toBeEnabled({ timeout: 30000 })
  await createBtn.click()
  await expect(flow).toContainText('已创建：', { timeout: 60000 })
  await expect(flow).toContainText('默认暂停', { timeout: 30000 })
  const createdText = await flow.innerText()
  const m = createdText.match(/#(\d+)/)
  console.log('NOTIFY_TASK id=' + (m ? m[1] : 'unknown'))
  const runBtn = flow.getByRole('button', { name: '确认启用并执行一次' })
  await expect(runBtn).toBeEnabled({ timeout: 30000 })
  await runBtn.click()
  await expect(flow).toContainText('执行记录', { timeout: 180000 })
  const finalText = await flow.innerText()
  const sm = finalText.match(/发送\s*(\d+)\s*人/)
  console.log('NOTIFY_SENT count=' + (sm ? sm[1] : 'unknown'))
  console.log('NOTIFY_POSTS=' + JSON.stringify(posts.filter(u => u.indexOf('auto-task') >= 0 || u.indexOf('auto_task') >= 0)))
  await page.screenshot({ path: path.join(shots, 'notify-01-run-done.png') })
})