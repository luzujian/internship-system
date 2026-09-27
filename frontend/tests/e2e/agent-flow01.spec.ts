/**
 * FLOW-01 侧栏统一闭环受控回归（复用 batch1 受控 harness 模式：mock 后端 + 真实 Vue 组件）。
 * 覆盖：
 *  - 教师：描述→追问（确认禁用）→预览→确认创建（默认暂停，确认前不启用）→确认启用并执行→结果记录；
 *  - 管理员：描述→追问（不可保存）→预览（差异+影响）→确认保存→结果记录；
 *  - 失败/无权限如实提示（403 不推进状态机、不写操作）。
 * 约束：只调手动页同一接口 preview/create/enable/run/logs 与 parse/config；确认前不断言写操作。
 */
import { test, expect, type Page } from '@playwright/test'
import path from 'node:path'

const output = path.resolve('../output/agent-optimization-20260918-build')
const teacherCaps = () => ({
  enabled: true,
  actor: { ownerKey: 'teacher:49', id: 49, name: '受控 mock 教师', role: 'ROLE_TEACHER_COUNSELOR', readOnly: false },
  capabilities: { agent_chat: true, voice_input: true, form_fill: true, auto_task: true, auto_task_notify: true, teacher_internship_summary: true },
  tools: []
})
const adminCaps = () => ({
  enabled: true,
  actor: { ownerKey: 'admin:900', id: 900, name: '受控 mock 管理员', role: 'ROLE_ADMIN', readOnly: false },
  capabilities: { agent_chat: true, voice_input: true, form_fill: true },
  tools: []
})
const teacherDraft = () => ({
  name: '鉴定未提交自动提醒',
  triggerCondition: 'APPRAISAL_MISSING',
  triggerLabel: '实习鉴定未提交',
  triggerDays: 2,
  action: 'STATION_REMINDER',
  actionLabel: '站内提醒',
  frequencyMinutes: 1440,
  frequencyLabel: '每天',
  endAt: null,
  scheduleConfig: JSON.stringify({ mode: 'WINDOW' }),
  scheduleLabel: '截止窗口内检查',
  messageTemplate: '请尽快提交实习鉴定表',
  questions: [],
  needsConfirm: true,
  channelAvailable: true
})
const createdTask = () => ({
  id: 101, teacherId: 49, name: '鉴定未提交自动提醒',
  triggerCondition: 'APPRAISAL_MISSING', triggerDays: 2, action: 'STATION_REMINDER',
  messageTemplate: '请尽快提交实习鉴定表', frequencyMinutes: 1440, status: 'PAUSED', enabled: 0
})

const traffic = new WeakMap<Page, string[]>()
const consoleIssues = new WeakMap<Page, string[]>()

async function mockTeacher(page: Page, mode: { preview?: 'questions' | 'draft' | 'forbidden' } = {}) {
  const requests: string[] = []
  traffic.set(page, requests)
  let previews = 0
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== 'http://127.0.0.1:5192') {
      requests.push(`BLOCKED ${url.origin}${url.pathname}`)
      return route.abort()
    }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    requests.push(`${route.request().method()} ${url.pathname}`)
    if (url.pathname === '/api/agent/capabilities') return route.fulfill({ json: { code: 200, data: teacherCaps() } })
    if (url.pathname === '/api/teacher/auto-tasks/preview') {
      if (mode.preview === 'forbidden') {
        return route.fulfill({ status: 403, json: { code: 403, message: '当前身份未开通自动化任务能力，请联系管理员。' } })
      }
      previews++
      if (mode.preview === 'questions' && previews === 1) {
        return route.fulfill({ json: { code: 200, data: { ...teacherDraft(), questions: ['请确认提醒渠道是站内还是邮件？'] } } })
      }
      return route.fulfill({ json: { code: 200, data: teacherDraft() } })
    }
    if (url.pathname === '/api/teacher/auto-tasks' && route.request().method() === 'POST') {
      return route.fulfill({ json: { code: 200, data: createdTask() } })
    }
    if (url.pathname === '/api/teacher/auto-tasks/101/enable') {
      return route.fulfill({ json: { code: 200, data: { ...createdTask(), status: 'ACTIVE', enabled: 1 } } })
    }
    if (url.pathname === '/api/teacher/auto-tasks/101/run') {
      return route.fulfill({ json: { code: 200, data: { id: 1, taskId: 101, executedAt: '2026-09-18 10:00:00', channel: 'STATION_REMINDER', targetCount: 5, successCount: 5, detail: '受控 mock 执行成功' } } })
    }
    return route.fulfill({ json: { code: 200, data: [] } })
  })
}

async function mockAdmin(page: Page) {
  const requests: string[] = []
  traffic.set(page, requests)
  let parses = 0
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== 'http://127.0.0.1:5192') {
      requests.push(`BLOCKED ${url.origin}${url.pathname}`)
      return route.abort()
    }
    if (!url.pathname.startsWith('/api/')) return route.continue()
    requests.push(`${route.request().method()} ${url.pathname}`)
    if (url.pathname === '/api/agent/capabilities') return route.fulfill({ json: { code: 200, data: adminCaps() } })
    if (url.pathname === '/api/admin/agent-capabilities/parse') {
      parses++
      if (parses === 1) {
        return route.fulfill({
          json: {
            code: 200,
            data: {
              proposedRoleDefaults: { student: ['agent_chat'], teacher: ['agent_chat'], company: ['agent_chat'], admin: ['agent_chat'] },
              proposedAutoTaskPolicy: { version: 1, roleDefaults: { teacher: { actions: ['SEND_REMINDER'], channels: ['EMAIL'], scopes: ['OWN_CLASSES'], maxTargetsPerRun: 200 } }, userOverrides: {} },
              changes: [], policyChanges: [], questions: ['请确认是否仅保留邮件渠道？'],
              impact: { tasks: [], tasksAtRisk: 0, userOverrideKeys: [], usersWithOverrides: 0 }
            }
          }
        })
      }
      return route.fulfill({
        json: {
          code: 200,
          data: {
            proposedRoleDefaults: { student: ['agent_chat'], teacher: ['agent_chat', 'auto_task'], company: ['agent_chat'], admin: ['agent_chat'] },
            proposedAutoTaskPolicy: { version: 1, roleDefaults: { teacher: { actions: ['SEND_REMINDER'], channels: ['EMAIL'], scopes: ['OWN_CLASSES'], maxTargetsPerRun: 200 } }, userOverrides: {} },
            changes: [{ role: 'teacher', capability: 'auto_task', from: false, to: true }],
            policyChanges: [{ role: 'teacher', field: 'channels', from: ['STATION_REMINDER', 'EMAIL'], to: ['EMAIL'] }],
            questions: [],
            impact: { tasks: [{ id: 7, name: '受控任务', teacherName: '受控教师', reason: '当前策略不允许该提醒渠道' }], tasksAtRisk: 1, userOverrideKeys: ['ROLE_TEACHER_COUNSELOR:49'], usersWithOverrides: 1 }
          }
        }
      })
    }
    if (url.pathname === '/api/admin/agent-capabilities/config' && route.request().method() === 'PUT') {
      return route.fulfill({ json: { code: 200, data: { saved: true, pausedTasks: [{ id: 7 }] } } })
    }
    return route.fulfill({ json: { code: 200, data: [] } })
  })
}

function watchConsole(page: Page) {
  const issues: string[] = []
  consoleIssues.set(page, issues)
  page.on('console', message => { if (['error', 'warning'].includes(message.type())) issues.push(`[console.${message.type()}] ${message.text().slice(0, 300)}`) })
  page.on('pageerror', error => { if (!String(error).includes('@vite/client')) issues.push(`[pageerror] ${String(error).slice(0, 300)}`) })
}

test.beforeEach(async ({ page }) => {
  watchConsole(page)
  await page.routeWebSocket(/\/@vite\//, socket => socket.close())
})

test.afterEach(async ({ page }, info) => {
  await info.attach('controlled-mock-requests', { body: JSON.stringify({ mode: 'ALL_API_CONTROLLED_MOCK_NO_BACKEND', requests: traffic.get(page) }), contentType: 'application/json' })
  await info.attach('console-issues', { body: JSON.stringify(consoleIssues.get(page)), contentType: 'application/json' })
  if (!page.isClosed()) await page.screenshot({ path: path.join(output, 'screenshots', `flow01-${info.title.replace(/[^a-zA-Z0-9-]+/g, '_')}.png`), fullPage: true })
})

async function openSidebar(page: Page) {
  await page.getByRole('button', { name: 'AI 助手' }).click()
  await expect(page.locator('#agent-workspace')).toBeVisible()
}

test('teacher sidebar closed loop with follow-up gate', async ({ page }) => {
  await mockTeacher(page, { preview: 'questions' })
  await page.goto('/flow01-teacher')
  await openSidebar(page)
  await page.getByRole('tab', { name: '自动化任务' }).click()
  const input = page.getByLabel('输入实习相关任务')
  // 追问轮：预览仅返回 questions，确认按钮禁用，且确认前无创建/启用流量
  await input.fill('提醒没交的同学')
  await page.getByRole('button', { name: '发送任务', exact: true }).click()
  const flow = page.locator('.auto-flow')
  await expect(flow).toHaveAttribute('data-flow-phase', 'need_input')
  await expect(flow).toContainText('请确认提醒渠道是站内还是邮件？')
  await expect(flow.getByRole('button', { name: '确认创建（默认暂停）' })).toBeDisabled()
  expect(traffic.get(page)?.filter(item => item.startsWith('POST'))).toEqual(['POST /api/teacher/auto-tasks/preview'])
  // 补充后重新预览：无追问才可确认
  await input.fill('鉴定截止前两天站内提醒未提交的人，提交后不再提醒')
  await page.getByRole('button', { name: '发送任务', exact: true }).click()
  await expect(flow).toHaveAttribute('data-flow-phase', 'need_confirm')
  await expect(flow).toContainText('鉴定未提交自动提醒')
  await expect(flow.getByRole('button', { name: '确认创建（默认暂停）' })).toBeEnabled()
  // 确认前仍无创建/启用流量
  expect(traffic.get(page)?.filter(item => item === 'POST /api/teacher/auto-tasks')).toEqual([])
  // 确认创建：默认暂停，仍未启用/执行
  await flow.getByRole('button', { name: '确认创建（默认暂停）' }).click()
  await expect(flow).toHaveAttribute('data-flow-phase', 'created')
  await expect(flow).toContainText('默认暂停')
  expect(traffic.get(page)).toContain('POST /api/teacher/auto-tasks')
  expect(traffic.get(page)?.filter(item => item.includes('/enable') || item.includes('/run'))).toEqual([])
  await page.screenshot({ path: path.join(output, 'screenshots', 'flow01-teacher-created.png'), fullPage: true })
  // 确认启用并执行：真实执行 + 结果记录
  await flow.getByRole('button', { name: '确认启用并执行一次' }).click()
  await expect(flow).toHaveAttribute('data-flow-phase', 'done')
  await expect(flow).toContainText('命中 5 人')
  await expect(flow).toContainText('发送 5 人')
  expect(traffic.get(page)).toContain('POST /api/teacher/auto-tasks/101/enable')
  expect(traffic.get(page)).toContain('POST /api/teacher/auto-tasks/101/run')
  expect(consoleIssues.get(page)?.filter(item => !item.includes('[el-radio]'))).toEqual([])
})

test('admin sidebar closed loop with impact preview', async ({ page }) => {
  await mockAdmin(page)
  await page.goto('/flow01-admin')
  await openSidebar(page)
  await page.getByRole('tab', { name: '权限变更' }).click()
  const input = page.getByLabel('输入实习相关任务')
  await input.fill('改一下权限')
  await page.getByRole('button', { name: '发送任务', exact: true }).click()
  const flow = page.locator('.caps-flow')
  await expect(flow).toHaveAttribute('data-flow-phase', 'need_input')
  await expect(flow).toContainText('请确认是否仅保留邮件渠道？')
  await expect(flow.getByRole('button', { name: '确认并保存' })).toBeDisabled()
  expect(traffic.get(page)?.filter(item => item.startsWith('POST') || item.startsWith('PUT'))).toEqual(['POST /api/admin/agent-capabilities/parse'])
  await input.fill('自动化只开放给教师，并且先只允许邮件提醒')
  await page.getByRole('button', { name: '发送任务', exact: true }).click()
  await expect(flow).toHaveAttribute('data-flow-phase', 'need_confirm')
  await expect(flow).toContainText('将停止 1 个任务')
  await expect(flow).toContainText('受控任务')
  expect(traffic.get(page)?.filter(item => item.startsWith('PUT'))).toEqual([])
  await flow.getByRole('button', { name: '确认并保存' }).click()
  await expect(flow).toHaveAttribute('data-flow-phase', 'done')
  await expect(flow).toContainText('暂停任务 1 个')
  expect(traffic.get(page)).toContain('PUT /api/admin/agent-capabilities/config')
  expect(consoleIssues.get(page)?.filter(item => !item.includes('[el-radio]'))).toEqual([])
})

test('teacher 403 truthfully blocks without writes', async ({ page }) => {
  await mockTeacher(page, { preview: 'forbidden' })
  await page.goto('/flow01-teacher')
  await openSidebar(page)
  await page.getByRole('tab', { name: '自动化任务' }).click()
  await page.getByLabel('输入实习相关任务').fill('提醒未提交心得的同学')
  await page.getByRole('button', { name: '发送任务', exact: true }).click()
  const flow = page.locator('.auto-flow')
  await expect(flow).toHaveAttribute('data-flow-phase', 'error')
  await expect(flow).toContainText('未开通自动化任务能力')
  expect(traffic.get(page)?.filter(item => item.startsWith('POST'))).toEqual(['POST /api/teacher/auto-tasks/preview'])
  expect(traffic.get(page)?.filter(item => item.includes('/enable') || item.includes('/run') || item === 'POST /api/teacher/auto-tasks')).toEqual([])
})
