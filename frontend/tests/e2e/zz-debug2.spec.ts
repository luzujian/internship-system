import { test, expect } from '@playwright/test'

test('debug jobs fill link', async ({ page, request }) => {
  const username = process.env.E2E_STUDENT_USERNAME
  const password = process.env.E2E_STUDENT_PASSWORD
  test.skip(!username || !password, '需提供管理员预建的隔离测试学生账号')
  page.on('pageerror', e => console.log('PAGE-ERR=', String(e).slice(0, 300)))
  const login = await (await request.post('http://localhost:8080/api/auth/login', {
    data: { username, password }
  })).json()
  const auth = login.data
  const caps = await (await fetch('http://localhost:8080/api/agent/capabilities',
    { headers: { Authorization: 'Bearer ' + auth.accessToken } }).then(r => r.json()).catch(e => ({ err: String(e) })))
  console.log('CAPS=', JSON.stringify(caps?.data?.capabilities))
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
  await page.goto('/student/jobs', { waitUntil: 'networkidle', timeout: 60000 })
  console.log('APPLY-COUNT=', await page.locator('.apply-button').count())
  await page.locator('.apply-button').first().click()
  await page.waitForTimeout(1500)
  const dialogs = await page.locator('.el-dialog').count()
  console.log('DIALOGS=', dialogs)
  page.on('response', r => {
    if (r.url().includes('/agent/capabilities')) console.log('CAPS-RESP=', r.status())
  })
  console.log('ENTRY-COUNT=', await page.locator('button.agent-entry').count())
  console.log('ENTRY-HTML=', (await page.locator('header').first().textContent() || '').slice(0, 200))
  await page.locator('button.agent-entry').click({ timeout: 15000 })
  await page.waitForTimeout(2000)
  console.log('FORM-LINK-COUNT=', await page.locator('.agent-form-link').count())
  console.log('WORKSPACE-TEXT=', ((await page.locator('#agent-workspace').textContent()) || '').slice(0, 300))
})
