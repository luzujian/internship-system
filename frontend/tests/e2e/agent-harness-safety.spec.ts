import { test, expect } from '@playwright/test'

// Controlled model faults, real Vue UI and authenticated backend. No business writes.
test.beforeEach(async ({ page }) => {
  test.skip(!process.env.E2E_PW_COMPANY, 'Set E2E_PW_COMPANY for the local test account')
  await page.goto('/login')
  await page.locator('.el-form input').nth(0).fill(process.env.E2E_COMPANY || '企业1')
  await page.locator('.el-form input').nth(1).fill(process.env.E2E_PW_COMPANY!)
  await page.locator('.button-container button').first().click()
  await page.waitForURL('**/company/**')
  await page.goto('/company/dashboard')
  await page.locator('.agent-entry:visible').last().click()
})

for (const interruption of ['cancel', 'away-back', 'close'] as const) {
  test(`late model result is ignored after ${interruption}`, async ({ page }) => {
    let release!: () => void
    const delayed = new Promise<void>(resolve => { release = resolve })
    let requested = false, fills = 0
    page.on('request', r => { if (r.url().includes('/forms/fill-intent')) fills++ })
    await page.route('**/api/agent/ui/intent', async route => {
      const target = route.request().postDataJSON().targets.find((t: any) => t.kind === 'page' && t.label.includes('招聘管理'))
      requested = true
      await delayed
      await route.fulfill({ json: { code: 200, data: { intent: 'open', targetId: target.id, fill: true, form: true } } }).catch(() => {})
    })
    await page.locator('#agent-input').fill('打开发布岗位表单，岗位名称填写测试工程师')
    await page.locator('#agent-input').press('Enter')
    await expect.poll(() => requested).toBe(true)
    await expect(page.getByRole('button', { name: '新会话', exact: true })).toBeDisabled()
    if (interruption === 'cancel') await page.getByRole('button', { name: '停止查询', exact: true }).click()
    if (interruption === 'close') await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
    if (interruption === 'away-back') {
      await page.locator('[data-agent-route="/company/jobs"]:visible').first().click()
      await page.waitForURL('**/company/jobs')
      await page.locator('[data-agent-route="/company"]:visible').first().click()
      await page.waitForURL('**/company/dashboard')
    }
    release()
    await page.waitForTimeout(1000)
    await expect(page).toHaveURL(/\/company\/dashboard$/)
    await expect(page.locator('.el-dialog:visible')).toHaveCount(0)
    expect(fills).toBe(0)
  })
}

test('repeated page selection stops without claiming form completion', async ({ page }) => {
  let calls = 0, fills = 0
  page.on('request', r => { if (r.url().includes('/forms/fill-intent')) fills++ })
  await page.route('**/api/agent/ui/intent', async route => {
    calls++
    const target = route.request().postDataJSON().targets.find((t: any) => t.kind === 'page' && t.label.includes('招聘管理'))
    await route.fulfill({ json: { code: 200, data: { intent: 'open', targetId: target.id, fill: true, form: true } } })
  })
  await page.locator('#agent-input').fill('打开发布岗位并填写测试工程师')
  await page.locator('#agent-input').press('Enter')
  await expect(page.locator('.agent-error')).toContainText('尚未定位目标表单')
  expect(calls).toBe(2)
  expect(fills).toBe(0)
  await expect(page.locator('.agent-workspace')).not.toContainText('已填写')
})

test('typing the next draft cannot replace the message being classified', async ({ page }) => {
  let release!: () => void, requested = false, sent = ''
  const delayed = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/agent/ui/intent', async route => {
    requested = true
    await delayed
    await route.fulfill({ json: { code: 200, data: { intent: 'chat' } } })
  })
  await page.route('**/api/agent/tasks', async route => {
    sent = route.request().postDataJSON().message
    await route.fulfill({ status: 503, json: { code: 503, message: '受控查询失败' } })
  })
  await page.locator('#agent-input').fill('查询本企业岗位申请数量')
  await page.locator('#agent-input').press('Enter')
  await expect.poll(() => requested).toBe(true)
  await page.locator('#agent-input').fill('下一条尚未发送的草稿')
  release()
  await expect.poll(() => sent).toBe('查询本企业岗位申请数量')
  await expect(page.locator('#agent-input')).toHaveValue('下一条尚未发送的草稿')
})

test('cancel during field parsing leaves the form unchanged', async ({ page }) => {
  await page.locator('[data-agent-route="/company/jobs"]:visible').first().click()
  await page.getByRole('button', { name: '发布岗位', exact: true }).click()
  const inputs = page.locator('.el-dialog:visible input')
  const before = await inputs.evaluateAll(nodes => nodes.map(node => (node as HTMLInputElement).value))
  let release!: () => void, requested = false
  const delayed = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/agent/ui/intent', route => route.fulfill({ json: { code: 200, data: { intent: 'open', targetId: 'current-form', form: true, fill: true } } }))
  await page.route('**/api/agent/forms/fill-intent', async route => {
    requested = true
    await delayed
    await route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: [{ prop: 'plannedRecruit', value: 9, confidence: 1 }], questions: [], skippedFields: [] } } }).catch(() => {})
  })
  await page.locator('#agent-input').fill('计划招聘人数填写9人')
  await page.locator('#agent-input').press('Enter')
  await expect.poll(() => requested).toBe(true)
  await page.getByRole('button', { name: '停止查询', exact: true }).click()
  release()
  await page.waitForTimeout(500)
  expect(await inputs.evaluateAll(nodes => nodes.map(node => (node as HTMLInputElement).value))).toEqual(before)
})

test('manual form edits during planning are preserved', async ({ page }) => {
  await page.locator('[data-agent-route="/company/jobs"]:visible').first().click()
  await page.getByRole('button', { name: '发布岗位', exact: true }).click()
  let release!: () => void, requested = false, fills = 0
  const delayed = new Promise<void>(resolve => { release = resolve })
  page.on('request', r => { if (r.url().includes('/forms/fill-intent')) fills++ })
  await page.route('**/api/agent/ui/intent', async route => {
    requested = true
    await delayed
    await route.fulfill({ json: { code: 200, data: { intent: 'open', targetId: 'current-form', form: true, fill: true } } })
  })
  await page.locator('#agent-input').fill('计划招聘人数填写9人')
  await page.locator('#agent-input').press('Enter')
  await expect.poll(() => requested).toBe(true)
  const count = page.locator('.el-dialog:visible .el-form-item').filter({ hasText: '计划招聘人数' }).locator('input')
  await count.fill('7')
  await count.blur()
  release()
  await expect(page.locator('.agent-error')).toContainText('表单内容已变化')
  await expect(count).toHaveValue('7')
  expect(fills).toBe(0)
})
