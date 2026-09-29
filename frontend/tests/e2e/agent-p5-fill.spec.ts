/**
 * 四端核心表单填表验证（零污染：只填不保存，撤销后关闭）。
 * 前置编排：岗位24 active、teacher 有 form_fill 能力（外部脚本）。
 */
import { test, expect } from '@playwright/test'

async function realLogin(request: any, username: string, password: string) {
  const res = await request.post('http://localhost:8080/api/auth/login', {
    data: { username, password }
  })
  const body = await res.json()
  if (body.code !== 200) throw new Error('login failed for ' + username)
  return body.data
}

async function inject(page: any, prefix: string, role: string, auth: any, extra: Record<string, string> = {}) {
  await page.addInitScript(({ prefix, role, auth, extra }: any) => {
    localStorage.setItem('current_role', role)
    localStorage.setItem(`${prefix}token_${role}`, auth.accessToken)
    localStorage.setItem(`${prefix}accessToken_${role}`, auth.accessToken)
    localStorage.setItem(`${prefix}refreshToken_${role}`, auth.refreshToken)
    localStorage.setItem(`${prefix}role_${role}`, role)
    localStorage.setItem(`${prefix}userId_${role}`, String(auth.user.id))
    localStorage.setItem(`${prefix}username_${role}`, auth.user.username)
    localStorage.setItem(`${prefix}name_${role}`, auth.user.name)
    localStorage.setItem(`${prefix}isReadOnly_${role}`, 'false')
    for (const [k, v] of Object.entries(extra)) localStorage.setItem(k, v as string)
  }, { prefix, role, auth, extra })
}

async function openWorkspace(page: any) {
  // 入口按钮会随页面数据加载发生位移（常规点击在命中测试与稳定性上都 flaky），用 JS 直调点击
  await page.locator('button.agent-entry').evaluate((el: HTMLElement) => el.click())
  await expect(page.locator('#agent-workspace')).toBeVisible()
  await expect(page.locator('.agent-form-link')).toBeVisible({ timeout: 15000 })
}

async function send(page: any, text: string) {
  await page.locator('#agent-input').fill(text)
  await page.locator('.agent-composer .send-button:not(.stop-button)').click()
  await expect(page.locator('.fill-event[data-fill-status="applied"]').first())
    .toBeVisible({ timeout: 90000 })
}

test.describe.serial('四端表单填表', () => {
  test.slow()
  test.use({ viewport: { width: 1600, height: 900 } })

  test('学生求职申请', async ({ page, request }) => {
    const username = process.env.E2E_STUDENT_USERNAME
    const password = process.env.E2E_STUDENT_PASSWORD
    test.skip(!username || !password, '需提供管理员预建的隔离测试学生账号')
    const auth = await realLogin(request, username!, password!)
    await inject(page, 'student_', 'ROLE_STUDENT', auth, { student_studentId_ROLE_STUDENT: String(auth.user.id) })
    await page.goto('/student/jobs')
    await page.locator('.apply-button').first().click()
    const dlg = page.locator('.el-dialog').filter({ has: page.locator('textarea') }).last()
    await expect(dlg).toBeVisible()
    await openWorkspace(page)
    const box = dlg.locator('textarea').first()
    await send(page, '自我介绍写三年Java开发经验，熟悉SpringBoot')
    await expect(box).toHaveValue(/三年Java开发经验/, { timeout: 15000 })
    await page.getByRole('button', { name: '撤销最近一批 AI 填写' }).click()
    await expect(box).toHaveValue('', { timeout: 15000 })
  })

  test('教师公告发布', async ({ page, request }) => {
    const res = await request.post('http://localhost:8080/api/auth/login', {
      data: { username: 't4verify01', password: 'Verify123' }
    })
    const body = await res.json()
    if (body.code !== 200) throw new Error('teacher login failed')
    const auth = {
      accessToken: body.data.token, refreshToken: body.data.refreshToken,
      user: body.data.userInfo, permissions: body.data.permissions
    }
    await inject(page, 'teacher_', 'ROLE_TEACHER_COUNSELOR', auth,
      { teacherType: 'COUNSELOR', teacherPermissions: JSON.stringify(auth.permissions) })
    await page.goto('/teacher/announcements')
    await page.locator('button.action-btn.primary').first().click()
    const dlg = page.locator('.el-dialog').last()
    await expect(dlg).toBeVisible()
    await openWorkspace(page)
    const title = dlg.locator('input[type="text"]').first()
    await send(page, '公告标题写实习动员会通知，内容写本周五下午三点开会')
    await expect(title).not.toHaveValue('', { timeout: 15000 })
    const filled = await title.inputValue()
    expect(filled).toContain('实习动员会')
    await page.getByRole('button', { name: '撤销最近一批 AI 填写' }).click()
    await expect(title).toHaveValue('', { timeout: 15000 })
  })

  test('企业面试模板', async ({ page, request }) => {
    const auth = await realLogin(request, '企业1', '123456')
    await inject(page, 'company_', 'ROLE_COMPANY', auth, { company_companyId_COMPANY: String(auth.user.id) })
    await page.goto('/company/jobs')
    await page.getByRole('button', { name: /预设/ }).click()
    const dlg = page.locator('.el-dialog').last()
    await expect(dlg).toBeVisible()
    await openWorkspace(page)
    const location = dlg.locator('input[type="text"]').nth(1)
    const remark = dlg.locator('textarea').first()
    const beforeLocation = await location.inputValue()
    const beforeRemark = await remark.inputValue()
    await send(page, '面试地点改成公司三楼会议室，备注带好简历')
    await expect(location).toHaveValue(/三楼会议室/, { timeout: 15000 })
    await expect(remark).toHaveValue(/带好简历/, { timeout: 15000 })
    await page.getByRole('button', { name: '撤销最近一批 AI 填写' }).click()
    await expect(location).toHaveValue(beforeLocation, { timeout: 15000 })
    await expect(remark).toHaveValue(beforeRemark)
  })

  test('管理员公告发布', async ({ page, request }) => {
    const auth = await realLogin(request, 'admin2', '123456')
    await inject(page, 'admin_', 'ROLE_ADMIN', auth)
    await page.goto('/admin/announcements')
    await page.getByRole('button', { name: /新增|发布/ }).first().click()
    const dlg = page.locator('.el-dialog').last()
    await expect(dlg).toBeVisible()
    await openWorkspace(page)
    const title = dlg.locator('input[type="text"]').first()
    await send(page, '公告标题写系统维护通知，内容写今晚十点停机半小时')
    await expect(title).not.toHaveValue('', { timeout: 15000 })
    expect(await title.inputValue()).toContain('系统维护')
    await page.getByRole('button', { name: '撤销最近一批 AI 填写' }).click()
    await expect(title).toHaveValue('', { timeout: 15000 })
  })
})
