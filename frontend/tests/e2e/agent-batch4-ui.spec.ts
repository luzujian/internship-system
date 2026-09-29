/**
 * BATCH4 受控浏览器回归：真实 Vue 侧栏 + mock 后端（不需要模型密钥、不连真实后端）。
 * 覆盖：历史会话面板、编辑态与 Escape 优先级、版本条与同题不重复气泡、
 *       停止入口、窄屏可用性，并保存关键截图。
 */
import { test, expect, type Page } from '@playwright/test'
import path from 'node:path'

const shot = (name: string) => path.resolve('../output/agent-optimization-20260918-build/tests/batch4-shots', `${name}.png`)

const capabilities = () => ({
  enabled: true,
  actor: { ownerKey: 'mock:1', id: '1', name: '受控 mock 学生', role: 'ROLE_STUDENT', readOnly: false },
  capabilities: { agent_chat: true, form_fill: true, voice_input: true, job_apply: true },
  tools: []
})

const pageContext = () => ({ pageKey: 'home', route: '/student/home', title: '首页', view: 'page', object: null, selectedObjects: [], filters: {}, revision: 1 })

const taskV1 = () => ({ id: 'task-1', sessionId: 'mock-session', ownerKey: 'mock:1', message: '我还有什么要完成？', status: 'SUCCEEDED', answer: '第一版回答：有 1 项待办。', errorCode: null, errorMessage: null, editRound: 0, editOf: null, version: 2, createdAt: Date.now() - 60000, updatedAt: Date.now() - 30000, pageContext: pageContext(), steps: [], cards: [] })
const taskV2 = () => ({ id: 'task-2', sessionId: 'mock-session', ownerKey: 'mock:1', message: '我还有什么要完成？', status: 'SUCCEEDED', answer: '第二版回答：有 2 项待办。', errorCode: null, errorMessage: null, editRound: 1, editOf: 'task-1', version: 4, createdAt: Date.now() - 20000, updatedAt: Date.now() - 10000, pageContext: pageContext(), steps: [], cards: [] })

const sessions = () => ([
  { id: 'mock-session', title: '我还有什么要完成', updatedAt: Date.now(), archived: false, taskCount: 2, hasRunningTask: false },
  { id: 'mock-old', title: '上次的实习进度', updatedAt: Date.now() - 7200000, archived: true, taskCount: 1, hasRunningTask: false },
  { id: 'mock-running', title: '正在查询中', updatedAt: Date.now(), archived: false, taskCount: 1, hasRunningTask: true }
])

async function installMocks(page: Page, tasks: () => unknown[]) {
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== 'http://127.0.0.1:5193') return route.abort()
    if (url.pathname.startsWith('/api/') || ['xhr', 'fetch'].includes(route.request().resourceType())) {
      let data: unknown = []
      if (url.pathname === '/api/agent/capabilities') data = capabilities()
      if (url.pathname === '/api/agent/sessions') data = sessions()
      if (url.pathname === '/api/agent/sessions/mock-session/tasks') data = tasks()
      if (/^\/api\/agent\/sessions\/[^/]+\/archive$/.test(url.pathname)) data = { id: 'mock-session', archived: true }
      if (/^\/api\/agent\/sessions\/[^/]+$/.test(url.pathname) && route.request().method() === 'DELETE') data = { id: 'mock-session', deleted: true }
      if (/^\/api\/agent\/sessions\/[^/]+$/.test(url.pathname) && route.request().method() === 'PATCH') data = { id: 'mock-session', title: '新名字' }
      return route.fulfill({ json: { code: 200, data } })
    }
    return route.continue()
  })
  await page.addInitScript(() => { (window as any).fetch = window.fetch })
}

test('历史面板：列出会话、区分归档与执行中、删除说明不级联', async ({ page }) => {
  await installMocks(page, () => [taskV1(), taskV2()])
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手' }).click().catch(async () => { await page.locator('.agent-entry, button').first().click() })
  await page.getByRole('button', { name: '历史' }).click()
  const panel = page.locator('.session-history')
  await expect(panel).toBeVisible()
  await expect(panel.locator('li')).toHaveCount(3)
  await expect(panel).toContainText('已归档')
  await expect(panel).toContainText('执行中')
  await expect(panel).toContainText('不会删除业务记录、审计或已启用的自动化任务')
  await expect(panel.getByRole('button', { name: '重命名' }).first()).toBeVisible()
  await expect(panel.getByRole('button', { name: '归档' }).first()).toBeVisible()
  await expect(panel.getByRole('button', { name: '删除' }).first()).toBeVisible()
  await page.screenshot({ path: shot('01-history-desktop'), fullPage: false })
})

test('编辑态：提示不撤销已完成操作，Escape 退出编辑而不关闭侧栏', async ({ page }) => {
  await installMocks(page, () => [taskV1(), taskV2()])
  await page.goto('/batch4')
  await page.locator('.agent-entry, button').first().click()
  const firstTurn = page.locator('article.agent-turn').first()
  await firstTurn.getByRole('button', { name: '编辑' }).first().click()
  await expect(firstTurn.locator('.msg-edit')).toBeVisible()
  await expect(firstTurn.locator('.msg-edit-note')).toContainText('修改消息不会撤销已完成的操作')
  await expect(firstTurn.getByRole('button', { name: '保存并重新发送' })).toBeVisible()
  await page.screenshot({ path: shot('02-edit-mode') })
  await page.keyboard.press('Escape')
  await expect(firstTurn.locator('.msg-edit')).toHaveCount(0)
  await expect(page.locator('.agent-workspace')).toBeVisible()
})

test('版本条：同一问题不重复气泡，显示第 2 版回答', async ({ page }) => {
  await installMocks(page, () => [taskV1(), taskV2()])
  await page.goto('/batch4')
  await page.locator('.agent-entry, button').first().click()
  const turns = page.locator('article.agent-turn')
  await expect(turns).toHaveCount(1)
  await expect(turns.locator('.user-question')).toHaveCount(1)
  await expect(turns.first()).toContainText('第二版回答')
  await turns.first().getByRole('button', { name: '查看第1版', exact: true }).click()
  await expect(turns.first()).toContainText('第一版回答')
  await expect(turns.first().locator('.version-pager')).toBeVisible()
  await page.screenshot({ path: shot('03-version-pager') })
})

test('窄屏：侧栏、历史与输入区仍可操作', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await installMocks(page, () => [taskV1(), taskV2()])
  await page.goto('/batch4')
  await page.locator('.agent-entry, button').first().click()
  await expect(page.locator('.agent-workspace')).toBeVisible()
  await page.getByRole('button', { name: '历史' }).click()
  await expect(page.locator('.session-history')).toBeVisible()
  await page.screenshot({ path: shot('04-narrow-history') })
  await page.locator('.session-history').getByRole('button', { name: '关闭' }).click()
  await expect(page.locator('#agent-input')).toBeVisible()
  await expect(page.getByRole('button', { name: '发送任务' })).toBeVisible()
})

test('生成时发送按钮原位切换为停止，输入新草稿不出现第二个按钮', async ({ page }) => {
  await installMocks(page, () => [{ ...taskV1(), status: 'RUNNING', answer: '' }])
  await page.goto('/batch4')
  await page.locator('.agent-entry, button').first().click()
  await expect(page.getByRole('button', { name: '停止查询' })).toBeVisible()
  await expect(page.locator('.composer-field .send-button')).toHaveCount(1)
  await expect(page.getByRole('button', { name: '发送任务', exact: true })).toHaveCount(0)
  const before = await page.locator('.send-button').boundingBox()
  await page.locator('#agent-input').fill('下一条尚未发送的问题')
  await expect(page.locator('.composer-field .send-button')).toHaveCount(1)
  await expect(page.getByRole('button', { name: '停止并发送新问题' })).toHaveCount(0)
  expect(await page.locator('.send-button').boundingBox()).toEqual(before)
  await page.screenshot({ path: shot('05-stop-visible') })
})

test('1366px 桌面无模态遮罩，顶栏和主页面仍可操作', async ({ page }) => {
  await page.setViewportSize({ width: 1366, height: 900 })
  await installMocks(page, () => [taskV1()])
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await expect(page.locator('.agent-backdrop')).toHaveCount(0)
  await expect(page.locator('.agent-workspace')).toHaveAttribute('role', 'complementary')
  const box = await page.locator('.agent-workspace').boundingBox()
  expect(box!.y).toBe(64)
  await page.locator('#business-button').click()
  expect(await page.evaluate(() => (window as any).__businessClicks)).toBe(1)
  await page.screenshot({ path: shot('07-nonmodal-1366') })
})

test('会话草稿隔离，快速切换和关闭重开不丢文字', async ({ page }) => {
  await installMocks(page, () => [taskV1()])
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.locator('#agent-input').fill('会话 A 尚未发送')
  await page.evaluate(() => (window as any).__batch4.agent.switchSession('mock-old'))
  await expect(page.locator('#agent-input')).toHaveValue('')
  await page.locator('#agent-input').fill('会话 B 尚未发送')
  await page.evaluate(() => (window as any).__batch4.agent.switchSession('mock-session'))
  await expect(page.locator('#agent-input')).toHaveValue('会话 A 尚未发送')
  await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await expect(page.locator('#agent-input')).toHaveValue('会话 A 尚未发送')
  await page.evaluate(() => (window as any).__batch4.agent.switchSession('mock-old'))
  await expect(page.locator('#agent-input')).toHaveValue('会话 B 尚未发送')
})

test('发送等待期间下一条输入不会被成功响应清空', async ({ page }) => {
  await installMocks(page, () => [])
  let finish!: () => Promise<void>
  await page.route('**/api/agent/tasks', async route => {
    const payload = route.request().postDataJSON()
    await new Promise<void>(resolve => { finish = async () => {
      await route.fulfill({ json: { code: 200, data: { ...taskV1(), message: payload.message } } }); resolve()
    } })
  })
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  const input = page.locator('#agent-input')
  await input.fill('第一条问题'); await input.press('Enter')
  await expect(page.locator('.pending-bubble')).toContainText('第一条问题')
  await expect(input).toHaveValue('')
  await input.fill('等待时准备的下一条问题')
  await expect.poll(() => Boolean(finish)).toBe(true)
  await finish()
  await expect(page.locator('.pending-bubble')).toHaveCount(0)
  await expect(input).toHaveValue('等待时准备的下一条问题')
})

test('停止保留已输出内容且不调用业务写接口', async ({ page }) => {
  await installMocks(page, () => [{ ...taskV1(), status: 'RUNNING', answer: '已输出的第一段' }])
  const writes: string[] = []
  page.on('request', request => { if (request.method() !== 'GET') writes.push(new URL(request.url()).pathname) })
  await page.route('**/api/agent/tasks/task-1/cancel', route => route.fulfill({ json: { code: 200, data: { ...taskV1(), status: 'CANCELLED', answer: '已输出的第一段', version: 9 } } }))
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.getByRole('button', { name: '停止查询', exact: true }).click()
  await expect(page.locator('[data-task-status="CANCELLED"]')).toContainText('已输出的第一段')
  await expect(page.getByRole('button', { name: '发送任务', exact: true })).toBeVisible()
  await expect(page.getByRole('button', { name: '停止查询', exact: true })).toHaveCount(0)
  await expect(page.locator('.composer-field .send-button')).toHaveCount(1)
  expect(writes).toEqual(['/api/agent/tasks/task-1/cancel'])
})

test('滚动查看历史后聚焦输入不会跳至最新', async ({ page }) => {
  await installMocks(page, () => Array.from({ length: 18 }, (_, i) => ({ ...taskV1(), id: `history-${i}`, message: `第 ${i} 条问题`, answer: '这是一段用于验证历史滚动位置保留的回答。'.repeat(12), createdAt: i + 1 })))
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.locator('.agent-conversation').evaluate(el => { el.scrollTop = 150; el.dispatchEvent(new Event('scroll')) })
  await page.locator('#agent-input').focus()
  await page.waitForTimeout(450)
  expect(await page.locator('.agent-conversation').evaluate(el => el.scrollTop)).toBe(150)
  await expect(page.getByRole('button', { name: '回到最新 ↓' })).toBeVisible()
})

test('填写解析停止后迟到结果不写表单，回执留在时间线', async ({ page }) => {
  await installMocks(page, () => [taskV1()])
  let finish!: () => Promise<void>
  await page.route('**/api/agent/forms/fill-intent', async route => {
    await new Promise<void>(resolve => { finish = async () => {
      await route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: [{ prop: 'count', value: '5', confidence: 1, operation: 'SET', overwriteAllowed: true }], questions: [], skippedFields: [] } } }).catch(() => {}); resolve()
    } })
  })
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.evaluate(() => {
    const h = (window as any).__batch4
    h.model = h.reactive({ count: 1 })
    h.register({ formId: 'job-edit', title: '岗位', fields: [{ prop: 'count', label: '人数', type: 'number', valueType: 'number' }], getModel: () => h.model })
  })
  await page.locator('#agent-input').fill('人数改成五个'); await page.locator('#agent-input').press('Enter')
  await expect.poll(() => Boolean(finish)).toBe(true)
  await page.getByRole('button', { name: '停止查询', exact: true }).click()
  await finish()
  await expect.poll(() => page.evaluate(() => (window as any).__batch4.model.count)).toBe(1)
  await expect(page.locator('.fill-event')).toContainText('已停止')
})

test('语音混合草稿并等待最后转写，审核修改后只发送一次', async ({ page }) => {
  await installMocks(page, () => [])
  const messages: string[] = []
  await page.route('**/api/agent/voice/sessions', route => route.fulfill({ json: { code: 200, data: { url: '/mock-voice' } } }))
  await page.route('**/api/agent/tasks', route => {
    const message = route.request().postDataJSON().message; messages.push(message)
    return route.fulfill({ json: { code: 200, data: { ...taskV1(), message } } })
  })
  await page.addInitScript(() => {
    const w = window as any; w.__voiceStopped = 0
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: { getUserMedia: async () => ({ getTracks: () => [{ stop: () => w.__voiceStopped++ }] }) } })
    w.AudioContext = class {
      destination = {}; audioWorklet = { addModule: async () => {} }
      resume() { return Promise.resolve() } close() { return Promise.resolve() }
      createMediaStreamSource() { return { connect() {}, disconnect() {} } }
    }
    w.AudioWorkletNode = class { port: any = { postMessage: (message: any) => { if (message.type === 'flush') setTimeout(() => this.port.onmessage?.({ data: { type: 'flushed', requestId: message.requestId } }), 0) } }; connect() {} disconnect() {} }
    w.WebSocket = class {
      static OPEN = 1; readyState = 1; bufferedAmount = 0; onmessage: any; onclose: any; onerror: any
      constructor() { w.__voiceSocket = this; setTimeout(() => this.emit({ type: 'ready' }), 0) }
      emit(value: any) { this.onmessage?.({ data: JSON.stringify(value) }) }
      send(value: string) { if (JSON.parse(value).type === 'finish') setTimeout(() => { this.emit({ type: 'final', id: 'last', text: '，其他内容不变。' }); this.emit({ type: 'finished' }) }, 20) }
      close() { this.readyState = 3 }
    }
  })
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.locator('#agent-input').fill('已有文字草稿')
  await page.getByRole('button', { name: '开始语音输入', exact: true }).click()
  await expect(page.getByRole('button', { name: '结束录音', exact: true })).toBeVisible()
  await page.evaluate(() => (window as any).__voiceSocket.emit({ type: 'final', id: 'first', text: '人数改成五个' }))
  await page.getByRole('button', { name: '结束录音', exact: true }).click()
  await expect(page.locator('.asr-review')).toBeVisible()
  await expect(page.locator('#agent-input')).toHaveValue('已有文字草稿\n人数改成五个，其他内容不变。')
  expect(messages).toEqual([])
  await page.locator('#agent-input').fill('已有文字草稿\n人数改成六个，其他内容不变。')
  await page.locator('.asr-review').getByRole('button', { name: '发送', exact: true }).click()
  await expect.poll(() => messages.length).toBe(1)
  expect(messages[0]).toContain('六个')
  expect(await page.evaluate(() => (window as any).__voiceStopped)).toBeGreaterThan(0)
})

test('远程草稿冲突保留当前文字，用户选择后才覆盖', async ({ page }) => {
  await installMocks(page, () => [])
  await page.route('**/api/agent/capabilities', route => route.fulfill({ json: { code: 200, data: { ...capabilities(), persistenceAvailable: true } } }))
  let remote = { draftKey: 'mock-session', text: '', revision: 0 }, conflict = true
  await page.route('**/api/agent/draft**', route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: { code: 200, data: remote } })
    if (conflict) { conflict = false; remote = { ...remote, text: '另一设备的新草稿', revision: 2 }; return route.fulfill({ status: 409, json: { code: 409, errorCode: 'DRAFT_CONFLICT', message: '草稿版本冲突' } }) }
    remote = { ...remote, text: route.request().postDataJSON().text, revision: remote.revision + 1 }
    return route.fulfill({ json: { code: 200, data: remote } })
  })
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.locator('#agent-input').fill('本设备正在编辑的文字')
  await expect(page.getByRole('button', { name: '恢复另一份草稿', exact: true })).toBeVisible()
  await expect(page.locator('#agent-input')).toHaveValue('本设备正在编辑的文字')
  await page.getByRole('button', { name: '恢复另一份草稿', exact: true }).click()
  await expect(page.locator('#agent-input')).toHaveValue('另一设备的新草稿')
})

test('聊天与填写按时间混排且历史回执不会自动重放', async ({ page }) => {
  await installMocks(page, () => [])
  const writes: string[] = []
  page.on('request', request => { if (request.method() === 'POST') writes.push(new URL(request.url()).pathname) })
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.evaluate(task => {
    const agent = (window as any).__batch4.agent
    agent.tasks = [{ ...task, id: 'chat-before', createdAt: 100 }, { ...task, id: 'chat-after', createdAt: 300 }]
    agent.fillEvents = [{ id: 'fill-middle', kind: 'fill', formId: 'closed-form', formTitle: '已关闭的岗位表单', message: '人数改成五个', intent: 'form_fill', assignments: [{ prop: 'count', label: '人数', value: '5', confidence: 1 }], questions: ['还缺地点'], skippedFields: [], applied: true, createdAt: 200, missingFields: ['地点'], businessStatus: 'filled', expectedInstanceId: -1 }]
  }, taskV1())
  const turns = page.locator('article.agent-turn')
  await expect(turns).toHaveCount(3)
  await expect(turns.nth(1)).toContainText('还缺地点')
  await expect(turns.nth(1)).toContainText('尚未保存或提交')
  await expect(turns.nth(1).getByRole('button', { name: '撤销本次填写' })).toHaveCount(0)
  expect(writes).toEqual([])
})

test('教师预览、创建、后台启用、立即运行是分别确认的步骤', async ({ page }) => {
  await installMocks(page, () => [])
  await page.route('**/api/agent/capabilities', route => route.fulfill({ json: { code: 200, data: { ...capabilities(), actor: { ...capabilities().actor, role: 'ROLE_TEACHER_COUNSELOR' }, capabilities: { agent_chat: true, auto_task: true } } } }))
  const operations: { path: string; body: any }[] = []
  await page.route('**/api/teacher/auto-tasks**', route => {
    const path = new URL(route.request().url()).pathname
    operations.push({ path, body: route.request().postDataJSON() })
    const data = path.endsWith('/enable/preview') ? { id: 'enable-preview', parameters: { taskId: '12', enabled: true } } : path.endsWith('/preview') ? { actionId: 'approved-preview', name: '跟进未交心得', triggerCondition: 'REFLECTION_MISSING', action: 'REMIND_REFLECTION', questions: [], scopeLabel: '本人负责班级', targetCount: 5 }
      : path.endsWith('/run') ? { status: 'SUCCEEDED', receipt: { targetCount: 5, successCount: 4, failedCount: 0 } }
      : { id: 12, name: '跟进未交心得' }
    return route.fulfill({ json: { code: 200, data } })
  })
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.getByRole('tab', { name: '自动化任务', exact: true }).click()
  await page.locator('#agent-input').fill('每周提醒没交心得的学生'); await page.locator('#agent-input').press('Enter')
  await page.getByRole('button', { name: '确认创建（默认暂停）', exact: true }).click()
  expect(operations[1].body.actionId).toBe('approved-preview')
  expect(operations[1].body.confirmed).toBe(true)
  await page.getByRole('button', { name: '确认启用后台跟进', exact: true }).click()
  await expect(page.getByRole('button', { name: '立即执行一次', exact: true })).toBeVisible()
  expect(operations.some(item => item.path.endsWith('/run'))).toBe(false)
  await page.getByRole('button', { name: '立即执行一次', exact: true }).click()
  await expect.poll(() => operations.filter(item => item.path.endsWith('/run')).length).toBe(1)
  expect(operations.find(item => item.path.endsWith('/enable'))?.body.clientRequestId).toBeTruthy()
  expect(operations.find(item => item.path.endsWith('/enable'))?.body.actionId).toBe('enable-preview')
  expect(operations.find(item => item.path.endsWith('/run'))?.body.clientRequestId).toBeTruthy()
})

test('填写后原表单异步校验更新回执，等待时仍可准备下一条输入', async ({ page }) => {
  await installMocks(page, () => [])
  await page.route('**/api/agent/forms/fill-intent', route => route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: [{ prop: 'count', value: '5', confidence: 1, operation: 'SET', overwriteAllowed: true }], questions: [], skippedFields: [] } } }))
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.evaluate(() => {
    const h = (window as any).__batch4
    h.model = h.reactive({ count: 1 })
    h.register({ formId: 'job-edit', title: '岗位', fields: [{ prop: 'count', label: '人数', type: 'number', valueType: 'number' }], getModel: () => h.model,
      getFormEl: () => ({ validate: (callback: any) => new Promise(resolve => { h.finishValidation = () => { callback(false, { count: [{ message: '人数超过当前审批额度' }] }); resolve(false) } }) }) })
  })
  await page.locator('#agent-input').fill('人数改成五个'); await page.locator('#agent-input').press('Enter')
  await expect.poll(() => page.evaluate(() => (window as any).__batch4.model.count)).toBe(5)
  await expect(page.locator('.fill-event')).toContainText('已填写 1 项')
  await expect(page.locator('#agent-input')).toBeEnabled()
  await page.locator('#agent-input').fill('下一条要求')
  await page.evaluate(() => (window as any).__batch4.finishValidation())
  await expect(page.locator('.fill-event')).toContainText('人数超过当前审批额度')
  await expect(page.locator('#agent-input')).toHaveValue('下一条要求')
})

test('首次填写等待建会话时也可停止，迟到会话不会继续解析表单', async ({ page }) => {
  await installMocks(page, () => [])
  let completeSession!: () => Promise<void>, parseCalls = 0
  await page.route('**/api/agent/sessions', async route => {
    if (route.request().method() !== 'POST') return route.fulfill({ json: { code: 200, data: { items: [], hasMore: false } } })
    await new Promise<void>(resolve => { completeSession = async () => {
      await route.fulfill({ json: { code: 200, data: { id: '00000000-0000-4000-8000-000000000099' } } }); resolve()
    } })
  })
  await page.route('**/api/agent/forms/fill-intent', route => { parseCalls++; return route.fulfill({ json: { code: 200, data: {} } }) })
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.evaluate(() => {
    const h = (window as any).__batch4
    h.agent.newSession(); h.agent.capabilities.persistenceAvailable = true
    h.model = h.reactive({ count: 1 })
    h.register({ formId: 'job-edit', title: '岗位', fields: [{ prop: 'count', label: '人数', type: 'number', valueType: 'number' }], getModel: () => h.model })
  })
  await page.locator('#agent-input').fill('人数改成五个'); await page.locator('#agent-input').press('Enter')
  await expect.poll(() => Boolean(completeSession)).toBe(true)
  await page.getByRole('button', { name: '停止查询', exact: true }).click()
  await completeSession()
  await expect(page.locator('.fill-event')).toContainText('已停止')
  expect(parseCalls).toBe(0)
  expect(await page.evaluate(() => (window as any).__batch4.model.count)).toBe(1)
})

test('填写途中收回能力会停止解析，迟到结果不能写入', async ({ page }) => {
  await installMocks(page, () => [])
  let finish!: () => Promise<void>
  await page.route('**/api/agent/forms/fill-intent', async route => {
    await new Promise<void>(resolve => { finish = async () => {
      await route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: [{ prop: 'count', value: 5, confidence: 1, operation: 'SET', overwriteAllowed: true }] } } }).catch(() => {}); resolve()
    } })
  })
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.evaluate(() => {
    const h = (window as any).__batch4
    h.model = h.reactive({ count: 1 })
    h.register({ formId: 'job-edit', title: '岗位', fields: [{ prop: 'count', label: '人数', type: 'number', valueType: 'number' }], getModel: () => h.model })
  })
  await page.locator('#agent-input').fill('人数改成五个'); await page.locator('#agent-input').press('Enter')
  await expect.poll(() => Boolean(finish)).toBe(true)
  await page.evaluate(() => { (window as any).__batch4.agent.capabilities.capabilities.form_fill = false })
  await finish()
  await expect(page.locator('.fill-event')).toContainText('已停止')
  expect(await page.evaluate(() => (window as any).__batch4.model.count)).toBe(1)
})

test('教师执行未知时保留原请求核验，部分成功不会显示为全部完成', async ({ page }) => {
  await installMocks(page, () => [])
  const runRequests: any[] = [], checks: string[] = []
  await page.route('**/api/teacher/auto-tasks/12/run', route => {
    runRequests.push(route.request().postDataJSON())
    return route.fulfill({ json: { code: 200, data: { status: 'UNKNOWN', receipt: { targetCount: 5, successCount: 2, unknownCount: 3 } } } })
  })
  await page.route('**/api/teacher/auto-tasks/12/runs/*', route => {
    checks.push(new URL(route.request().url()).pathname.split('/').pop()!)
    return route.fulfill({ json: { code: 200, data: { status: 'PARTIAL', receipt: { targetCount: 5, successCount: 2, failedCount: 3, unknownCount: 0 } } } })
  })
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.evaluate(() => { (window as any).__batch4.agent.autoFlow = { phase: 'enabled', message: '按约定跟进', questions: [], draft: null, created: { id: 12, name: '跟进任务', enabled: true }, runLog: null, error: '', working: false } })
  await page.getByRole('button', { name: '立即执行一次', exact: true }).click()
  await expect(page.getByRole('button', { name: '核验本次执行' })).toBeVisible()
  await expect(page.getByRole('button', { name: '立即执行一次', exact: true })).toHaveCount(0)
  await page.getByRole('button', { name: '核验本次执行' }).click()
  await expect(page.locator('.action-receipt')).toContainText('执行部分完成')
  expect(runRequests).toHaveLength(1)
  expect(checks).toEqual([runRequests[0].clientRequestId])
})

async function openExistingJobForm(page: Page) {
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.evaluate(() => {
    const h = (window as any).__batch4
    h.model = h.reactive({ count: 1, salary: 3000, title: '已有岗位名称', notes: '已有备注', description: '' })
    h.register({ formId: 'job-edit', title: '岗位', fields: [
      { prop: 'count', label: '人数', type: 'number', valueType: 'number' },
      { prop: 'salary', label: '薪资', type: 'number', valueType: 'number' },
      { prop: 'title', label: '岗位名称', type: 'input' },
      { prop: 'notes', label: '备注', type: 'textarea' },
      { prop: 'description', label: '描述', type: 'textarea' }
    ], getModel: () => h.model })
  })
}

test('局部修改只改人数，薪资不变和其他内容不要动优先于模型候选', async ({ page }) => {
  await installMocks(page, () => [])
  await page.route('**/api/agent/forms/fill-intent', route => route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: [
    { prop: 'count', value: '5', confidence: 1, operation: 'SET', overwriteAllowed: true },
    { prop: 'salary', value: '9000', confidence: 1, operation: 'SET', overwriteAllowed: true },
    { prop: 'notes', value: '模型擅自改写', confidence: 1, operation: 'SET', overwriteAllowed: true }
  ], questions: [], skippedFields: [] } } }))
  await openExistingJobForm(page)
  await page.locator('#agent-input').fill('人数改成五个，薪资不变，其他内容不要动'); await page.locator('#agent-input').press('Enter')
  await expect(page.locator('.fill-event')).toContainText('已填写 1 项')
  expect(await page.evaluate(() => ({ ...(window as any).__batch4.model }))).toEqual({ count: 5, salary: 3000, title: '已有岗位名称', notes: '已有备注', description: '' })
})

test('明确清空备注仅清空该字段，撤销本次填写恢复原备注', async ({ page }) => {
  await installMocks(page, () => [])
  await page.route('**/api/agent/forms/fill-intent', route => route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: [
    { prop: 'notes', value: '', confidence: 1, operation: 'CLEAR', overwriteAllowed: true }
  ], questions: [], skippedFields: [] } } }))
  await openExistingJobForm(page)
  await page.locator('#agent-input').fill('清空备注，其他内容不要动'); await page.locator('#agent-input').press('Enter')
  await expect(page.locator('.fill-event')).toContainText('已填写 1 项')
  expect(await page.evaluate(() => (window as any).__batch4.model.notes)).toBe('')
  await page.locator('.fill-event summary').click()
  await expect(page.locator('.fill-row')).toContainText('已清空')
  await page.locator('.fill-event').getByRole('button', { name: '撤销本次填写', exact: true }).click()
  await expect.poll(() => page.evaluate(() => (window as any).__batch4.model.notes)).toBe('已有备注')
  expect(await page.evaluate(() => (window as any).__batch4.model.salary)).toBe(3000)
})

test('缺少覆盖授权或原话未指定的模型候选不能覆盖已有值，空字段可补充', async ({ page }) => {
  await installMocks(page, () => [])
  await page.route('**/api/agent/forms/fill-intent', route => route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: [
    { prop: 'count', value: '5', confidence: 1, operation: 'SET' },
    { prop: 'title', value: '模型擅自改名', confidence: 1, operation: 'SET', overwriteAllowed: true },
    { prop: 'description', value: '协助整理实习资料', confidence: 1, operation: 'SET', overwriteAllowed: false }
  ], questions: [], skippedFields: [] } } }))
  await openExistingJobForm(page)
  await page.locator('#agent-input').fill('人数改成五个，补充描述：协助整理实习资料'); await page.locator('#agent-input').press('Enter')
  await expect(page.locator('.fill-event')).toContainText('已填写 1 项')
  expect(await page.evaluate(() => ({ ...(window as any).__batch4.model }))).toEqual({ count: 1, salary: 3000, title: '已有岗位名称', notes: '已有备注', description: '协助整理实习资料' })
  await page.route('**/api/agent/forms/fill-intent', route => route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: [
    { prop: 'count', value: '5', confidence: 1, operation: 'SET' }
  ], questions: [], skippedFields: [] } } }))
  await page.locator('#agent-input').fill('人数改成五个'); await page.locator('#agent-input').press('Enter')
  await expect(page.locator('.fill-event')).toHaveCount(2)
  await expect(page.locator('.fill-event').last()).toContainText('本次未填写字段')
  await expect(page.locator('.fill-event').last().locator('.fill-skipped')).toBeVisible()
  expect(await page.evaluate(() => (window as any).__batch4.model.count)).toBe(1)
})

test('实际成功回执才显示保存说明及当前角色的业务核对入口', async ({ page }) => {
  await installMocks(page, () => [])
  await page.route('**/api/agent/capabilities', route => route.fulfill({ json: { code: 200, data: { ...capabilities(), actor: { ...capabilities().actor, role: 'ROLE_COMPANY' } } } }))
  await page.route('**/api/agent/forms/fill-intent', route => route.fulfill({ json: { code: 200, data: { intent: 'form_fill', assignments: [
    { prop: 'notes', value: '面试时带上简历', confidence: 1, operation: 'SET', overwriteAllowed: true }
  ], questions: [], skippedFields: [] } } }))
  let saveSucceeded = false
  await page.route('**/api/company/controlled-save', route => route.fulfill({ status: saveSucceeded ? 200 : 500, json: { code: saveSucceeded ? 200 : 500 } }))
  await openExistingJobForm(page)
  await page.locator('#agent-input').fill('备注改为面试时带上简历'); await page.locator('#agent-input').press('Enter')
  await expect(page.locator('.fill-event')).toContainText('已填写 1 项')
  const label = '面试模板已保存到当前浏览器，尚未发布岗位'
  const save = () => page.evaluate(async label => {
    const h = (window as any).__batch4, pending = h.beginFormOperation('job-edit')
    const response = await fetch('/api/company/controlled-save', { method: 'POST' })
    const result = await response.json()
    if (response.ok && result.code === 200) pending.complete({ status: 'saved', label, url: '/company/jobs' })
  }, label)
  await save()
  await expect(page.locator('.fill-business-receipt')).toHaveCount(0)
  await expect(page.getByText(label, { exact: true })).toHaveCount(0)
  saveSucceeded = true
  await save()
  await expect(page.locator('.fill-business-receipt')).toContainText(label)
  const link = page.getByRole('button', { name: '查看业务页面 ↗', exact: true })
  await expect(link).toBeVisible()
  for (const url of ['https://evil.example', '/student/home', '/company/unregistered']) {
    await page.evaluate(url => { (window as any).__batch4.agent.fillEvents[0].receipt.url = url }, url)
    await expect(link).toHaveCount(0)
  }
  await page.evaluate(() => { (window as any).__batch4.agent.fillEvents[0].receipt.url = '/company/jobs' })
  await link.click()
  await expect.poll(() => page.evaluate(() => (window as any).__batch4.router.currentRoute.value.path)).toBe('/company/jobs')
})

test('真实模型回包中的19项未填写原因默认折叠，历史保留中文字段名', async ({ page }) => {
  await installMocks(page, () => [])
  const fields = [
    ['salaryUnit', '薪资单位'], ['salaryMin', '最低薪资'], ['salaryMax', '最高薪资'], ['positionName', '岗位名称'], ['department', '岗位类型'],
    ['positionType', '岗位类别'], ['province', '省份'], ['city', '城市'], ['district', '区县'], ['detailAddress', '详细地址'], ['requiredMajors', '招聘专业'],
    ['description', '岗位描述'], ['requirements', '任职要求'], ['internshipEnvironment', '实习环境'], ['providesAccommodation', '是否提供住宿'],
    ['interviewMethod', '面试方式'], ['interviewLocation', '面试地点'], ['interviewTime', '面试时间'], ['interviewRemark', '面试备注']
  ]
  // Same assignment/skip payload observed in the real company model acceptance.
  await page.route('**/api/agent/forms/fill-intent', route => route.fulfill({ json: { code: 200, data: { intent: 'form_fill',
    assignments: [{ prop: 'plannedRecruit', label: '计划招聘人数', value: '5', confidence: 0.95, operation: 'SET', overwriteAllowed: true }],
    skippedFields: fields.map(([prop]) => ({ prop, reason: prop.startsWith('salary') ? '用户明确表示薪资不变' : '用户要求其他内容不要动' })), questions: []
  } } }))
  await page.goto('/batch4')
  await page.getByRole('button', { name: 'AI 助手', exact: true }).click()
  await page.evaluate(fields => {
    const h = (window as any).__batch4
    h.model = h.reactive({ plannedRecruit: 3, ...Object.fromEntries(fields.map(([prop]) => [prop, ''])) })
    h.register({ formId: 'actual-job-response', title: '发布岗位', fields: [{ prop: 'plannedRecruit', label: '计划招聘人数', type: 'number', valueType: 'number' }, ...fields.map(([prop, label]) => ({ prop, label, type: 'input' }))], getModel: () => h.model,
      getValidationSummary: () => ({ missingFields: ['工作地点'], validationErrors: ['岗位名称尚未填写'] }) })
  }, fields)
  await page.locator('#agent-input').fill('人数改成五个，薪资不变，其他内容不要动'); await page.locator('#agent-input').press('Enter')
  const receipt = page.locator('.fill-event'), details = receipt.locator('details.fill-skipped')
  await expect(receipt).toContainText('已填写 1 项')
  await expect(details.locator('summary')).toHaveText('查看未填写原因（19项）')
  await expect(details).not.toHaveAttribute('open')
  await expect(details.locator('p').first()).toBeHidden()
  await expect(receipt).toContainText('还缺工作地点')
  await expect(receipt.locator('.agent-task-error')).toContainText('岗位名称尚未填写')
  await details.locator('summary').click()
  await expect(details).toContainText('最低薪资：用户明确表示薪资不变')
  expect(await details.innerText()).not.toContain('salaryMin')
  await page.evaluate(() => (window as any).__batch4.unregister('actual-job-response'))
  await expect(details).toContainText('最低薪资：用户明确表示薪资不变')
  await page.evaluate(() => { delete (window as any).__batch4.agent.fillEvents[0].skippedFields[1].label })
  await expect(details).toContainText('该字段：用户明确表示薪资不变')
  expect(await details.innerText()).not.toContain('salaryMin')
})
