/**
 * 阶段四验收：学生计划查询 + 投递确认弹窗闭环（只投测、随后撤回清理）。
 * 前置条件由外部编排：岗位 24 已临时 active、student 已授权 job_apply。
 */
import { test, expect } from '@playwright/test'

const STUDENT = {
  username: process.env.E2E_STUDENT_USERNAME || '',
  password: process.env.E2E_STUDENT_PASSWORD || ''
}

async function studentToken(request: any) {
  const res = await request.post('http://localhost:8080/api/auth/login', {
    data: { username: STUDENT.username, password: STUDENT.password }
  })
  const body = await res.json()
  return body.data
}

async function injectStudent(page: any, auth: any) {
  await page.addInitScript((a: any) => {
    localStorage.setItem('current_role', 'ROLE_STUDENT')
    localStorage.setItem('student_token_ROLE_STUDENT', a.accessToken)
    localStorage.setItem('student_accessToken_ROLE_STUDENT', a.accessToken)
    localStorage.setItem('student_refreshToken_ROLE_STUDENT', a.refreshToken)
    localStorage.setItem('student_role_ROLE_STUDENT', 'ROLE_STUDENT')
    localStorage.setItem('student_userId_ROLE_STUDENT', String(a.user.id))
    localStorage.setItem('student_username_ROLE_STUDENT', a.user.username)
    localStorage.setItem('student_name_ROLE_STUDENT', a.user.name)
    localStorage.setItem('student_isReadOnly_ROLE_STUDENT', 'false')
    localStorage.setItem('student_studentId_ROLE_STUDENT', String(a.user.id))
  }, auth)
}

test.describe.serial('阶段四：计划与投递闭环', () => {
  test.slow()
  test.use({ viewport: { width: 1600, height: 900 } })

  test('计划查询有阶段卡 + 投递确认 + 撤回', async ({ page, request }) => {
    test.skip(!STUDENT.username || !STUDENT.password, '需提供管理员预建的隔离测试学生账号')
    const auth = await studentToken(request)
    const token = auth.accessToken
    await injectStudent(page, auth)
    await page.goto('/student/jobs')
    await expect(page.locator('button.agent-entry')).toBeVisible({ timeout: 30000 })
    await page.locator('button.agent-entry').click()
    await expect(page.locator('#agent-workspace')).toBeVisible()

    const send = async (text: string) => {
      await page.locator('#agent-input').fill(text)
      await page.locator('.agent-composer .send-button:not(.stop-button)').click()
    }

    // 1. 计划查询：断言 student_plan 结果卡出现
    await send('我的实习计划现在到哪一步了，还缺什么？')
    await expect(page.locator('.agent-result[data-tool="student_plan"]').first())
      .toBeVisible({ timeout: 120000 })

    // 2. 找岗并点确认投递
    await send('帮我找在招岗位')
    await expect(page.locator('.agent-result[data-tool="jobs_search"]').first())
      .toBeVisible({ timeout: 120000 })
    await page.locator('.agent-result[data-tool="jobs_search"] .apply-button').first()
      .click({ force: true })
    const dialog = page.locator('.apply-dialog')
    await expect(dialog).toBeVisible()
    await expect(dialog).toContainText('企业1')

    // 3. 确认投递：回执含申请记录与计划更新
    await dialog.getByRole('button', { name: '确认投递' }).click()
    await expect(dialog).toContainText('投递成功', { timeout: 60000 })
    await expect(dialog).toContainText('计划已更新', { timeout: 30000 })
    const receiptText = (await dialog.textContent()) || ''
    const matched = receiptText.match(/申请记录\s*#(\d+)/)
    expect(matched, 'receipt contains application id').not.toBeNull()

    // 4. 撤回清理（走同一执行通道）
    const res = await request.post('http://localhost:8080/api/agent/job-applications/withdraw', {
      headers: { Authorization: `Bearer ${token}` },
      data: { applicationId: matched![1], clientRequestId: crypto.randomUUID(), confirmed: true }
    })
    expect(res.ok()).toBeTruthy()
    await dialog.getByRole('button', { name: '关闭' }).click()
  })
})
