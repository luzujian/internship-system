import { test, expect, type Page } from '@playwright/test'
import path from 'node:path'

const output = path.resolve('../output/agent-optimization-20260918-build')
const phase = process.env.BATCH1_PHASE || 'after'
const capabilities = () => ({ enabled: true, actor: { ownerKey: 'mock:1', id: '1', name: '受控 mock 企业', role: 'ROLE_COMPANY', readOnly: false }, capabilities: { agent_chat: true, form_fill: true, voice_input: true }, tools: [] })
const traffic = new WeakMap<Page, string[]>()
const consoleIssues = new WeakMap<Page, string[]>()

test.beforeEach(async ({ page }) => {
  const requests: string[] = []
  traffic.set(page, requests)
  const issues: string[] = []
  consoleIssues.set(page, issues)
  page.on('console', message => { if (['error', 'warning'].includes(message.type())) issues.push(`[console.${message.type()}] ${message.text().slice(0, 300)}`) })
  page.on('pageerror', error => { if (!String(error).includes('@vite/client')) issues.push(`[pageerror] ${String(error).slice(0, 300)}`) })
  await page.routeWebSocket(/\/@vite\//, socket => socket.close())
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== 'http://127.0.0.1:5191') {
      requests.push(`BLOCKED ${url.origin}${url.pathname}`)
      return route.abort()
    }
    if (url.pathname.startsWith('/api/') || ['xhr', 'fetch'].includes(route.request().resourceType())) {
      requests.push(`${route.request().method()} ${url.pathname}`)
      let data: unknown = []
      if (url.pathname === '/api/agent/capabilities') data = capabilities()
      if (url.pathname === '/api/agent/forms/fill-intent') data = { intent: 'form_fill', assignments: [{ prop: 'detailAddress', value: '受控 mock 地址', confidence: 1 }], questions: [], skippedFields: [] }
      if (url.pathname === '/api/agent/tasks') {
        const payload = route.request().postDataJSON()
        data = { id: 'mock-task', sessionId: 'mock-session', ownerKey: 'mock:1', message: payload.message, pageContext: payload.pageContext, status: 'SUCCEEDED', version: 1, answer: '受控 mock 回答', cards: String(payload.message).includes('超长') ? [{ tool: 'jobs_search', title: '超长字段渲染检查岗位卡片标题可以很长很长很长很长', status: 'ok', observedAt: new Date().toISOString(), scope: '受控 mock', items: [{ id: '1', title: 'x'.repeat(120), company: '受控 mock 企业', city: '深圳', description: '长'.repeat(200) }], total: 1, hasMore: false, facts: {}, links: [], warnings: [] }] : [], steps: [] }
      }
      return route.fulfill({ json: { code: 200, data } })
    }
    return route.continue()
  })
  await page.addInitScript(() => {
    const w = window as any
    w.__speech = { sessions: [] }
    class MockRecognition {
      onresult: any
      onerror: any
      onend: any
      starts = 0
      stops = 0
      aborts = 0
      queued: any
      constructor() { w.__speech.sessions.push(this) }
      start() { this.starts++ }
      stop() { this.stops++ }
      abort() { this.aborts++ }
      emit(text: string) {
        this.onresult?.({ resultIndex: 0, results: [Object.assign([{ transcript: text }], { isFinal: true })] })
      }
      queue() { this.queued = { result: this.onresult, end: this.onend, error: this.onerror } }
      late() {
        this.queued.result?.({ resultIndex: 0, results: [Object.assign([{ transcript: '迟到错误文本' }], { isFinal: true })] })
        this.queued.error?.({ error: 'network' })
        this.queued.end?.()
      }
    }
    w.SpeechRecognition = MockRecognition
    w.webkitSpeechRecognition = MockRecognition
  })
  await page.goto('/batch1')
  await expect(page.getByRole('button', { name: 'AI 助手' })).toBeVisible()
})

test.afterEach(async ({ page }, info) => {
  await info.attach('controlled-mock-requests', { body: JSON.stringify({ mode: 'ALL_API_CONTROLLED_MOCK_NO_BACKEND', requests: traffic.get(page) }), contentType: 'application/json' })
  await info.attach('console-issues', { body: JSON.stringify(consoleIssues.get(page)), contentType: 'application/json' })
  if (!page.isClosed()) await page.screenshot({ path: path.join(output, 'screenshots', `batch1-${phase}-${info.title.replace(/[^a-zA-Z0-9-]/g, '_')}.png`), fullPage: true })
})

for (const device of ['desktop', 'mobile']) {
  for (const order of ['form-first', 'sidebar-first']) {
    test(`mock UI ${device} ${order}`, async ({ page }) => {
      await page.setViewportSize(device === 'desktop' ? { width: 1600, height: 900 } : { width: 390, height: 844 })
      if (order === 'sidebar-first') {
        await page.getByRole('button', { name: 'AI 助手' }).click()
        if (device === 'mobile') await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
      }
      await page.getByRole('button', { name: '发布岗位', exact: true }).click()
      const dialog = page.getByRole('dialog', { name: '发布岗位', exact: true })
      await expect(dialog).toBeVisible()
      if (order === 'form-first' || device === 'mobile') await dialog.getByRole('button', { name: 'AI 助手' }).click()
      const input = page.getByLabel('输入实习相关任务')
      await input.fill('填写详细地址')
      await input.press('Tab')
      await page.keyboard.press('Shift+Tab')
      await expect(input).toBeFocused()
      await expect(input).toHaveValue('填写详细地址')
      await page.screenshot({ path: path.join(output, 'screenshots', `batch1-${phase}-${device}-${order}-linked.png`), fullPage: true })
      await page.getByRole('button', { name: '发送任务', exact: true }).click()
      await expect(page.locator('.fill-event[data-fill-status="applied"]')).toHaveCount(1)
      await page.getByRole('button', { name: '返回表单', exact: true }).click()
      await expect(page.locator('#agent-workspace')).toBeHidden()
      await expect(dialog).toBeVisible()
      expect(await dialog.evaluate(el => el.contains(document.activeElement))).toBe(true)
      await expect(dialog.locator('input').filter({ visible: true }).first()).toBeVisible()
      await expect.poll(async () => dialog.locator('input').evaluateAll(inputs => inputs.some(input => (input as HTMLInputElement).value === '受控 mock 地址'))).toBe(true)
      await dialog.getByRole('button', { name: 'AI 助手' }).click()
      await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
      await expect(dialog).toBeVisible()
      await dialog.getByRole('button', { name: '取消', exact: true }).click()
      await expect(dialog).toBeHidden()
      expect(traffic.get(page)?.filter(item => item.startsWith('POST'))).toEqual(['POST /api/agent/forms/fill-intent'])
      expect(consoleIssues.get(page)?.filter(item => !item.includes('[el-radio]'))).toEqual([])
    })
  }
}

for (const reason of ['escape', 'close', 'token', 'owner', 'voice-revoked', 'disabled', 'readonly', 'unmount']) {
  test(`mock VOICE cancellation ${reason}`, async ({ page }) => {
    await page.getByRole('button', { name: 'AI 助手' }).click()
    await page.getByRole('button', { name: '开始语音输入' }).click()
    await page.evaluate(() => { const s = (window as any).__speech.sessions[0]; s.emit('取消前内容'); s.queue() })
    if (reason === 'escape') await page.getByLabel('输入实习相关任务').press('Escape')
    else if (reason === 'close') await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
    else await page.evaluate(reason => {
      const { auth, agent, unmount } = (window as any).__batch1
      if (reason === 'token') auth.token = 'mock-new-token'
      if (reason === 'owner') agent.capabilities.actor.ownerKey = 'mock:2'
      if (reason === 'voice-revoked') agent.capabilities.capabilities.voice_input = false
      if (reason === 'disabled') agent.capabilities.enabled = false
      if (reason === 'readonly') agent.capabilities.actor.readOnly = true
      if (reason === 'unmount') unmount()
    }, reason)
    await expect.poll(() => page.evaluate(() => { const s = (window as any).__speech.sessions[0]; return s.stops + s.aborts })).toBe(1)
    await page.evaluate(() => (window as any).__speech.sessions[0].late())
    expect(await page.evaluate(() => (window as any).__speech.sessions[0].starts)).toBe(1)
    expect(traffic.get(page)?.filter(item => item.startsWith('POST'))).toEqual([])
    if (reason !== 'unmount') {
      if (reason === 'escape' || reason === 'close') await page.getByRole('button', { name: 'AI 助手' }).click()
      await expect(page.getByLabel('输入实习相关任务')).toHaveValue('')
      await expect(page.locator('.asr-error')).toHaveCount(0)
    }
  })
}

test('mock VOICE explicit stop submits once and discards old callbacks', async ({ page }) => {
  await page.getByRole('button', { name: 'AI 助手' }).click()
  await page.getByRole('button', { name: '开始语音输入' }).click()
  await page.evaluate(() => { const s = (window as any).__speech.sessions[0]; s.emit('受控语音查询'); s.queue() })
  await page.getByRole('button', { name: '停止录音并提交' }).click()
  await expect(page.locator('.user-question')).toHaveText('受控语音查询')
  await page.getByRole('button', { name: '开始语音输入' }).click()
  await page.evaluate(() => (window as any).__speech.sessions[0].late())
  await page.getByRole('button', { name: '停止录音并提交' }).click()
  expect(traffic.get(page)?.filter(item => item.startsWith('POST'))).toEqual(['POST /api/agent/tasks'])
})

test.describe('P2 allow-create 岗位名称真实浏览器回归', () => {
  const openDialog = async (page) => {
    await page.goto('/batch1-fill')
    await page.getByRole('button', { name: '发布岗位', exact: true }).click()
    const dlg = page.getByRole('dialog', { name: '发布岗位', exact: true })
    await expect(dlg).toBeVisible()
    return dlg
  }
  const nameBox = (dlg) => dlg.getByRole('combobox', { name: '* 岗位名称' })
  const nameLabel = (dlg) => dlg.locator('.el-form-item', { hasText: '岗位名称' }).locator('.el-select__placeholder')
  const vueModel = (page) => page.evaluate(() => (window as any).__batch1.agent.currentForm?.getModel?.() ?? null)

  test('P2 只打字不选创建项：点其他字段后发布，非静默且 payload 带名', async ({ page }) => {
    const dlg = await openDialog(page)
    const posted = []
    await page.route('**/api/admin/positions', async route => {
      if (route.request().method() === 'POST') { try { posted.push(route.request().postDataJSON()) } catch {} }
      return route.fulfill({ json: { code: 200, data: [] } })
    })
    await nameBox(dlg).click()
    await nameBox(dlg).fill('P2定制岗0918')
    await dlg.getByRole('combobox', { name: '* 岗位类别' }).click()
    await expect(nameLabel(dlg)).toHaveText('P2定制岗0918')
    await dlg.locator('.el-form-item', { hasText: '招聘专业' }).locator('.el-select').click()
    await page.locator('.el-select-dropdown__item', { hasText: '不限制专业' }).click()
    await dlg.getByRole('button', { name: '发布', exact: true }).click()
    await expect(page.locator('.el-message').filter({ hasText: '岗位发布成功' })).toBeVisible()
    await expect(dlg).toBeHidden()
    expect(posted.length).toBe(1)
    expect(posted[0]?.positionName).toBe('P2定制岗0918')
    expect(await vueModel(page)).toBeNull()
  })

  test('P2 空名称提交：明确校验且零提交流量', async ({ page }) => {
    const dlg = await openDialog(page)
    await dlg.getByRole('button', { name: '发布', exact: true }).click()
    await expect(page.locator('.el-message').filter({ hasText: '请输入岗位名称' })).toBeVisible()
    await expect(dlg).toBeVisible()
    expect(traffic.get(page)?.filter(item => item === 'POST /api/admin/positions')).toEqual([])
  })

  test('P2 AI 回填定制名称：下拉框有回显且 Vue model 一致', async ({ page }) => {
    const dlg = await openDialog(page)
    await page.route('**/api/agent/forms/fill-intent', async route => {
      return route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: [{ prop: 'positionName', value: 'P2智能岗0918', confidence: 1 }], questions: [], skippedFields: [] } } })
    })
    await dlg.getByRole('button', { name: 'AI 助手', exact: true }).click()
    await page.getByLabel('输入实习相关任务').fill('岗位名称填P2智能岗0918')
    await page.getByRole('button', { name: '发送任务', exact: true }).click()
    await expect(page.locator('.fill-event[data-fill-status="applied"]').first()).toBeVisible({ timeout: 15000 })
    await expect(nameLabel(dlg)).toHaveText('P2智能岗0918')
    expect((await vueModel(page))?.positionName).toBe('P2智能岗0918')
  })
  test('P2 回车确认创建项：原生行为不受损且可发布', async ({ page }) => {
    const dlg = await openDialog(page)
    const posted = []
    await page.route('**/api/admin/positions', async route => {
      if (route.request().method() === 'POST') { try { posted.push(route.request().postDataJSON()) } catch {} }
      return route.fulfill({ json: { code: 200, data: [] } })
    })
    await nameBox(dlg).click()
    await nameBox(dlg).fill('P2回车岗0918')
    await nameBox(dlg).press('Enter')
    await dlg.locator('.el-form-item', { hasText: '招聘专业' }).locator('.el-select').click()
    await page.locator('.el-select-dropdown__item', { hasText: '不限制专业' }).click()
    await dlg.getByRole('button', { name: '发布', exact: true }).click()
    await expect(page.locator('.el-message').filter({ hasText: '岗位发布成功' })).toBeVisible()
    expect(posted.length).toBe(1)
    expect(posted[0]?.positionName).toBe('P2回车岗0918')
  })
})
test.describe('P4 模型故障隔离', () => {
  test('P4 模型通道全挂时 AI 保持安静、手工发布照常用', async ({ page }) => {
    await page.route('**/api/agent/**', async route => {
      return route.fulfill({ status: 500, json: { code: 500, message: 'P4受控模型故障' } })
    })
    await page.goto('/batch1-fill')
    await page.getByRole('button', { name: '发布岗位', exact: true }).click()
    const dlg = page.getByRole('dialog', { name: '发布岗位', exact: true })
    await expect(dlg).toBeVisible()
    await expect(page.locator('button.agent-entry')).toHaveCount(0)
    const posted = []
    await page.route('**/api/admin/positions', async route => {
      if (route.request().method() === 'POST') { try { posted.push(route.request().postDataJSON()) } catch {} }
      return route.fulfill({ json: { code: 200, data: [] } })
    })
    await dlg.getByRole('combobox', { name: '* 岗位名称' }).click()
    await dlg.getByRole('combobox', { name: '* 岗位名称' }).fill('P4手工岗0918')
    await dlg.getByRole('combobox', { name: '* 岗位类别' }).click()
    await expect(dlg.locator('.el-form-item', { hasText: '岗位名称' }).locator('.el-select__placeholder')).toHaveText('P4手工岗0918')
    await dlg.locator('.el-form-item', { hasText: '招聘专业' }).locator('.el-select').click()
    await page.locator('.el-select-dropdown__item', { hasText: '不限制专业' }).click()
    await dlg.getByRole('button', { name: '发布', exact: true }).click()
    await expect(page.locator('.el-message').filter({ hasText: '岗位发布成功' })).toBeVisible()
    await expect(dlg).toBeHidden()
    expect(posted.length).toBe(1)
    expect(posted[0]?.positionName).toBe('P4手工岗0918')
  })
})
