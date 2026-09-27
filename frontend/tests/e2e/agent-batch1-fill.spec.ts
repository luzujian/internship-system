import { test, expect, type Page, type Route } from '@playwright/test'
import path from 'node:path'

const output = path.resolve('../output/agent-optimization-20260918-build')
const phase = process.env.BATCH1_PHASE || 'fill-after'
const audits = new WeakMap<Page, string[]>()

async function controlled(page: Page, fill: (route: Route) => Promise<void>, caps?: () => unknown, path = '/batch1-fill') {
  const traffic: string[] = []
  audits.set(page, traffic)
  await page.routeWebSocket(/.*/, socket => { traffic.push(`BLOCKED_WS ${socket.url()}`); socket.close() })
  await page.route('**/*', async route => {
    const request = route.request()
    const url = new URL(request.url())
    if (url.origin !== 'http://127.0.0.1:5191') { traffic.push(`BLOCKED_EXTERNAL ${url.href}`); return route.abort() }
    if (url.pathname.startsWith('/api/') || ['xhr', 'fetch'].includes(request.resourceType())) {
      traffic.push(`MOCK ${request.method()} ${url.pathname}`)
      if (url.pathname === '/api/agent/forms/fill-intent') return fill(route)
      let data: unknown = []
      if (url.pathname === '/api/agent/capabilities') data = caps ? caps() : { enabled: true, actor: { ownerKey: 'mock:1', id: '1', name: '受控 mock 企业', role: 'ROLE_COMPANY', readOnly: false }, capabilities: { agent_chat: true, form_fill: true }, tools: [] }
      if (url.pathname === '/api/majors') data = [{ id: 7, name: '软件工程' }, { id: 8, name: '计算机科学与技术' }]
      return route.fulfill({ json: { code: 200, data } })
    }
    return route.continue()
  })
  await page.goto(path)
  await expect.poll(() => traffic.includes('MOCK GET /api/majors')).toBe(true)
}

const reply = (route: Route, values: Record<string, string>) => route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: Object.entries(values).map(([prop, value]) => ({ prop, value, confidence: 1 })), questions: [], skippedFields: [] } } })
const state = (page: Page) => page.evaluate(() => { const agent = (window as any).__batch1.agent; return { instance: agent.currentForm?.instanceId, formId: agent.currentForm?.formId, model: agent.currentForm?.getModel(), events: agent.fillEvents } })
const send = async (page: Page) => {
  await page.getByLabel('输入实习相关任务').fill('受控 mock 定向填写')
  await page.getByRole('button', { name: '发送任务', exact: true }).click()
}
const open = async (page: Page) => {
  await page.getByRole('button', { name: '发布岗位', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '发布岗位', exact: true })
  await expect(dialog).toBeVisible()
  return dialog
}

test.afterEach(async ({ page }, info) => {
  if (!audits.has(page)) return
  await info.attach('network-audit', { body: JSON.stringify({ mode: 'REAL_VUE_BINDING_ALL_BACKEND_MOCK_NO_DB_NO_NOTIFICATION', traffic: audits.get(page) }), contentType: 'application/json' })
  await info.attach('vue-state', { body: JSON.stringify(await state(page)), contentType: 'application/json' })
  await page.screenshot({ path: path.join(output, 'screenshots', `${phase}-${info.title.replace(/[^a-zA-Z0-9-]/g, '_')}.png`), fullPage: true })
  expect(audits.get(page)?.filter(item => item.startsWith('MOCK POST') && !item.endsWith('/agent/forms/fill-intent'))).toEqual([])
})

const permissions = () => ({ enabled: true, actor: { ownerKey: 'mock:1', id: '1', name: '受控 mock 企业', role: 'ROLE_COMPANY', readOnly: false }, capabilities: { agent_chat: true, form_fill: true, voice_input: true }, tools: [] })

test('PERM-01 controlled mock refresh revokes entry while filled values remain', async ({ page }) => {
  let caps = permissions()
  await controlled(page, route => reply(route, { detailAddress: '权限保留测试' }), () => caps)
  const dialog = await open(page)
  await dialog.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  expect((await state(page)).events[0].applied).toBe(true)
  caps = { ...permissions(), enabled: false }
  await page.evaluate(() => (window as any).__batch1.agent.initialize())
  await expect.poll(() => page.evaluate(() => (window as any).__batch1.agent.capabilities?.enabled)).toBe(false)
  await expect(page.getByRole('button', { name: 'AI 助手', exact: true })).toHaveCount(0)
  expect((await state(page)).model.detailAddress).toBe('权限保留测试')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
})

test('PERM-01 controlled mock voice revoked mid-session stops recognition', async ({ page }) => {
  await page.addInitScript(() => {
    const w = window as any
    w.__speech = { sessions: [] }
    class MockRecognition {
      onresult: any; onerror: any; onend: any; starts = 0; stops = 0; aborts = 0; queued: any
      constructor() { w.__speech.sessions.push(this) }
      start() { this.starts++ }
      stop() { this.stops++ }
      abort() { this.aborts++ }
      emit(text: string) { this.onresult?.({ resultIndex: 0, results: [Object.assign([{ transcript: text }], { isFinal: true })] }) }
      queue() { this.queued = { result: this.onresult, end: this.onend, error: this.onerror } }
      late() {
        this.queued.result?.({ resultIndex: 0, results: [Object.assign([{ transcript: '迟到语音' }], { isFinal: true })] })
        this.queued.error?.({ error: 'network' })
        this.queued.end?.()
      }
    }
    w.SpeechRecognition = MockRecognition
    w.webkitSpeechRecognition = MockRecognition
  })
  let caps = permissions()
  await controlled(page, route => reply(route, { detailAddress: '语音撤权' }), () => caps)
  const dialog = await open(page)
  await dialog.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.getByRole('button', { name: '开始语音输入' }).click()
  await page.evaluate(() => { const s = (window as any).__speech.sessions[0]; s.emit('撤销中的语音'); s.queue() })
  caps = { ...permissions(), enabled: false }
  await page.evaluate(() => (window as any).__batch1.agent.initialize())
  await expect.poll(() => page.evaluate(() => { const s = (window as any).__speech.sessions[0]; return s.stops + s.aborts })).toBe(1)
  await page.evaluate(() => (window as any).__speech.sessions[0].late())
  expect(await page.evaluate(() => (window as any).__speech.sessions[0].starts)).toBe(1)
  expect((await state(page)).model.detailAddress).toBe('')
  expect((await state(page)).events.length).toBe(0)
  await page.getByRole('button', { name: '返回表单', exact: true }).click()
  await expect(dialog.getByPlaceholder('请输入详细地址，如街道、门牌号等')).toHaveValue('')
})

test('PERM-01 controlled mock fill-intent 403 surfaces error and blocks write', async ({ page }) => {
  let status = 200
  await controlled(page, async route => {
    if (status === 403) return route.fulfill({ status: 403, json: { code: 403, message: '智能填表能力未授权' } })
    return reply(route, { detailAddress: '首次写入' })
  })
  const dialog = await open(page)
  await dialog.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  expect((await state(page)).model.detailAddress).toBe('首次写入')
  status = 403
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(2)
  const event = (await state(page)).events[1]
  expect(event.applied).toBe(false)
  expect(event.error).toContain('未授权')
  expect((await state(page)).model.detailAddress).toBe('首次写入')
  await page.getByRole('button', { name: '返回表单', exact: true }).click()
  await expect(dialog.getByPlaceholder('请输入详细地址，如街道、门牌号等')).toHaveValue('首次写入')
})

for (const action of ['reopen', 'route', 'manual']) {
  test(`FILL-01 controlled backend mock delayed ${action}`, async ({ page }) => {
    let pending: Route | undefined
    await controlled(page, async route => { pending = route })
    let dialog = await open(page)
    const before = await state(page)
    await dialog.getByRole('button', { name: 'AI 助手' }).click()
    await send(page)
    await expect.poll(() => Boolean(pending)).toBe(true)
    await page.getByRole('button', { name: '返回表单', exact: true }).click()
    if (action === 'manual') await dialog.getByPlaceholder('请输入详细地址，如街道、门牌号等').fill('手改地址')
    else {
      await dialog.getByRole('button', { name: '取消', exact: true }).click()
      if (action === 'route') {
        await page.getByRole('link', { name: '受控切换路由' }).click()
        await expect(page.getByText('受控路由空页面', { exact: true })).toBeVisible()
        expect((await state(page)).formId).toBeUndefined()
      } else {
        dialog = await open(page)
        const after = await state(page)
        expect(after.formId).toBe(before.formId)
        expect(after.instance).not.toBe(before.instance)
        expect(after.model).toEqual(before.model)
      }
    }
    await reply(pending!, { detailAddress: 'A迟到地址', salaryMin: '5' })
    await expect.poll(async () => (await state(page)).events.length).toBe(1)
    const after = await state(page)
    if (action === 'route') expect(after.events[0].applied).toBe(false)
    else if (action === 'reopen') {
      expect(after.model).toEqual(before.model)
      expect(after.events[0].applied).toBe(false)
      await expect(dialog.getByPlaceholder('请输入详细地址，如街道、门牌号等')).toHaveValue('')
    } else {
      expect(after.model.detailAddress).toBe('手改地址')
      expect(after.model.salaryMin).toBe(5)
      expect(after.events[0].skippedFields[0].prop).toBe('detailAddress')
    }
  })
}

test('FILL-02 controlled backend mock multiple cascade sequential undo validation', async ({ page }) => {
  let values = { requiredMajors: '软件工程、计算机科学与技术', province: '广东省', city: '深圳市', district: '南山区', providesAccommodation: '是', salaryMin: '3', detailAddress: '第一地址' } as Record<string, string>
  await controlled(page, route => reply(route, values))
  const dialog = await open(page)
  await dialog.getByRole('button', { name: 'AI 助手' }).click()
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  const first = (await state(page)).model
  expect(first.requiredMajors).toEqual(['软件工程', '计算机科学与技术'])
  expect(first.providesAccommodation).toBe(1)
  expect(first.addressCode).toEqual(['440000', '440300', '440305'])
  values = { detailAddress: '第二地址' }
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(2)
  expect((await state(page)).model).toEqual({ ...first, detailAddress: '第二地址' })
  await page.getByRole('button', { name: '返回表单', exact: true }).click()
  await expect(dialog.getByText('软件工程', { exact: true })).toBeVisible()
  await expect(dialog.getByText('计算机科学与技术', { exact: true })).toBeVisible()
  await expect(dialog.getByRole('textbox', { name: '工作地点', exact: true })).toHaveValue('广东省 / 深圳市 / 南山区')
  await expect(dialog.getByRole('radio', { name: '是', exact: true })).toBeChecked()
  await dialog.getByPlaceholder('请输入详细地址，如街道、门牌号等').fill('手改保留')
  await dialog.getByRole('button', { name: 'AI 助手' }).click()
  await page.getByRole('button', { name: /撤销/ }).last().click()
  expect((await state(page)).model.detailAddress).toBe('手改保留')
  await page.getByRole('button', { name: '返回表单', exact: true }).click()
  await dialog.getByRole('button', { name: '发布', exact: true }).click()
  await expect(page.getByText('请输入岗位名称', { exact: true })).toBeVisible()
})

test('FILL-02 controlled backend mock invalid options and undeclared fields', async ({ page }) => {
  await controlled(page, route => reply(route, { id: '999', status: 'inactive', interviewTime: 'not-a-date', requiredMajors: '不存在专业', providesAccommodation: '7', salaryMin: 'Infinity' }))
  const dialog = await open(page)
  const before = (await state(page)).model
  await dialog.getByRole('button', { name: 'AI 助手' }).click()
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  const after = await state(page)
  expect(after.model).toEqual(before)
  expect(after.events[0].applied).toBe(false)
  // P7 收尾：company:position.interviewTime 已声明为 date/string 可填字段，因此非法日期不再静默忽略，
  // 而是被拒绝并计入 skipped（与 requiredMajors/数字非法同口径）。
  expect(after.events[0].skippedFields.map((item: any) => item.prop)).toEqual(['interviewTime', 'requiredMajors', 'providesAccommodation', 'salaryMin'])
})

test('FILL-02 controlled backend mock preserves manually bound date string', async ({ page }) => {
  await controlled(page, route => reply(route, { detailAddress: '日期保持测试' }))
  const dialog = await open(page)
  const date = dialog.getByRole('combobox', { name: '面试时间', exact: true })
  await date.fill('2026-09-25 14:30')
  await date.press('Enter')
  await date.press('Tab')
  await expect.poll(async () => (await state(page)).model.interviewTime).toBe('2026-09-25 14:30:00')
  await dialog.getByRole('button', { name: 'AI 助手' }).click()
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  expect((await state(page)).model.interviewTime).toBe('2026-09-25 14:30:00')
  await page.getByRole('button', { name: '返回表单', exact: true }).click()
  await expect(date).toHaveValue('2026-09-25 14:30')
})

test('FILL-02 real el-date-picker Date object binding via AI fill', async ({ page }) => {
  await controlled(page, route => reply(route, { status: '已找到实习', startDate: '2026-09-20', endDate: '2026-09-21' }))
  await page.evaluate(() => (window as any).__batch1.router.push('/student/registration'))
  await expect(page.getByRole('heading', { name: '实习去向登记' })).toBeVisible()
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  const model = (await state(page)).model
  expect(model.status).toBe('found')
  expect(model.startDate).toBeInstanceOf(Date)
  expect(model.startDate.toISOString()).toBe(new Date('2026-09-20').toISOString())
  expect(model.endDate).toBeInstanceOf(Date)
  await page.getByRole('button', { name: '返回表单', exact: true }).click()
  await expect(page.getByRole('combobox', { name: '开始时间' })).toHaveValue('2026-09-20')
  await expect(page.getByRole('combobox', { name: '结束时间' })).toHaveValue('2026-09-21')
})

test('FILL-02 teacher calendar AI date string fill and native input display', async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem('teacherType', 'DEPARTMENT'))
  await controlled(page, route => reply(route, { startDate: '2026-10-01', endDate: '2026-10-31', reportCycle: '14', appraisalDeadline: '2026-11-10' }))
  await page.evaluate(() => (window as any).__batch1.router.push('/teacher/settings'))
  await expect(page.getByRole('heading', { name: '重要设置' })).toBeVisible()
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  const model = (await state(page)).model
  expect(model.startDate).toBe('2026-10-01')
  expect(model.reportCycle).toBe(14)
  await page.getByRole('button', { name: '返回表单', exact: true }).click()
  const dates = page.locator('input[type="date"]')
  await expect(dates.nth(0)).toHaveValue('2026-10-01')
  await expect(dates.nth(1)).toHaveValue('2026-10-31')
  await expect(dates.nth(2)).toHaveValue('2026-11-10')
})

test('PERM-01 reopening entry refreshes revoked capabilities without manual initialize', async ({ page }) => {
  let caps = permissions()
  await controlled(page, route => reply(route, { detailAddress: '重开刷新' }), () => caps)
  const dialog = await open(page)
  await dialog.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await expect(page.getByRole('button', { name: '关闭 AI 助手', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
  caps = { ...permissions(), enabled: false }
  await dialog.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await expect.poll(() => page.evaluate(() => (window as any).__batch1.agent.capabilities?.enabled)).toBe(false)
  await expect(page.getByRole('button', { name: 'AI 助手', exact: true })).toHaveCount(0)
})

test('INTERVIEW form-first fill return and cancel keeps real Vue model', async ({ page }) => {
  await controlled(page, route => reply(route, { interviewLocation: '受控 mock 面试地点', remark: '受控 mock 备注' }))
  await page.getByRole('button', { name: '预设面试信息', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '填写面试信息', exact: true })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  expect((await state(page)).events[0].applied).toBe(true)
  expect((await state(page)).formId).toBe('company:interview-template')
  expect((await state(page)).model.interviewLocation).toBe('受控 mock 面试地点')
  expect((await state(page)).model.remark).toBe('受控 mock 备注')
  await page.getByRole('button', { name: '返回表单', exact: true }).click()
  await expect(dialog.getByPlaceholder('请输入面试地点')).toHaveValue('受控 mock 面试地点')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
})

test('INTERVIEW sidebar-first switches linked form to interview template', async ({ page }) => {
  await controlled(page, route => reply(route, { interviewLocation: '先侧栏面试地点' }))
  await page.getByRole('button', { name: 'AI 助手' }).first().click()
  await expect(page.getByRole('button', { name: '关闭 AI 助手', exact: true })).toBeVisible()
  await page.getByRole('button', { name: '预设面试信息', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '填写面试信息', exact: true })
  await expect(dialog).toBeVisible()
  expect((await state(page)).formId).toBe('company:interview-template')
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  expect((await state(page)).events[0].applied).toBe(true)
  expect((await state(page)).model.interviewLocation).toBe('先侧栏面试地点')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
})

test('INTERVIEW datetime fill keeps value-format string on real picker', async ({ page }) => {
  await controlled(page, route => reply(route, { interviewTime: '2026-10-02 14:30:00' }))
  await page.getByRole('button', { name: '预设面试信息', exact: true }).click()
  const dialog = page.getByRole('dialog', { name: '填写面试信息', exact: true })
  await expect(dialog).toBeVisible()
  await dialog.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  expect((await state(page)).events[0].applied).toBe(true)
  expect((await state(page)).model.interviewTime).toBe('2026-10-02 14:30:00')
  await page.getByRole('button', { name: '返回表单', exact: true }).click()
  await expect(dialog.getByPlaceholder('请选择面试时间')).toHaveValue('2026-10-02 14:30')
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
})

test('FILL-02 number radio keeps numeric value on real position form', async ({ page }) => {
  await controlled(page, route => reply(route, { providesAccommodation: '是' }))
  const dialog = await open(page)
  await dialog.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await send(page)
  await expect.poll(async () => (await state(page)).events.length).toBe(1)
  expect((await state(page)).events[0].applied).toBe(true)
  expect((await state(page)).model.providesAccommodation).toBe(1)
  await dialog.getByRole('button', { name: '取消', exact: true }).click()
  await expect(dialog).toBeHidden()
})

test('FILL-02 controlled backend mock empty number preserves real Vue salary binding', async ({ page }, info) => {
  const traffic: string[] = []
  let value = '3'
  await page.routeWebSocket(/.*/, socket => { traffic.push(`BLOCKED_WS ${socket.url()}`); socket.close() })
  await page.route('**/*', async route => {
    const request = route.request()
    const url = new URL(request.url())
    if (url.origin !== 'http://127.0.0.1:5191') {
      traffic.push(`BLOCKED_EXTERNAL ${url.origin}${url.pathname}`)
      return route.abort()
    }
    if (url.pathname.startsWith('/api/') || ['xhr', 'fetch'].includes(request.resourceType())) {
      traffic.push(`MOCK ${request.method()} ${url.pathname}`)
      let data: unknown = []
      if (url.pathname === '/api/agent/capabilities') data = { enabled: true, actor: { ownerKey: 'mock:1', id: '1', name: '受控 mock 企业', role: 'ROLE_COMPANY', readOnly: false }, capabilities: { agent_chat: true, form_fill: true }, tools: [] }
      if (url.pathname === '/api/agent/forms/fill-intent') data = { intent: 'form_fill', assignments: [{ prop: 'salaryMin', value, confidence: 1 }], questions: [], skippedFields: [] }
      return route.fulfill({ json: { code: 200, data } })
    }
    return route.continue()
  })
  try {
    await page.goto('/batch1-fill')
    await page.getByRole('button', { name: '发布岗位', exact: true }).click()
    const dialog = page.getByRole('dialog', { name: '发布岗位', exact: true })
    await dialog.getByRole('button', { name: 'AI 助手' }).click()
    const send = async () => {
      await page.getByLabel('输入实习相关任务').fill('填写最低薪资')
      await page.getByRole('button', { name: '发送任务', exact: true }).click()
    }
    const model = () => page.evaluate(() => (window as any).__batch1.agent.currentForm.getModel().salaryMin)
    await send()
    await expect.poll(model).toBe(3)
    value = ''
    await send()
    await expect.poll(() => page.evaluate(() => (window as any).__batch1.agent.fillEvents.length)).toBe(2)
    await page.getByRole('button', { name: '返回表单', exact: true }).click()
    expect.soft(await model()).toBe(3)
    await expect.soft(dialog.getByPlaceholder('最低', { exact: true })).toHaveValue('3')
    expect(traffic.filter(item => item.startsWith('MOCK POST'))).toEqual(['MOCK POST /api/agent/forms/fill-intent', 'MOCK POST /api/agent/forms/fill-intent'])
  } finally {
    await info.attach('network-audit', { body: JSON.stringify({ mode: 'REAL_VUE_BINDING_ALL_BACKEND_MOCK_NO_DB_NO_NOTIFICATION', traffic }), contentType: 'application/json' })
    await info.attach('vue-state', { body: JSON.stringify(await page.evaluate(() => { const agent = (window as any).__batch1?.agent; return { model: agent?.currentForm?.getModel(), events: agent?.fillEvents } })), contentType: 'application/json' })
    await page.screenshot({ path: path.join(output, 'screenshots', `${phase}-empty-number.png`), fullPage: true })
  }
})
