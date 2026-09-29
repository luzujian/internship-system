import { test, expect } from '@playwright/test'

async function loginTeacher(request: any) {
  const response = await request.post('http://127.0.0.1:8080/api/auth/login', {
    data: { username: 't001', password: '123456' }
  })
  const body = await response.json()
  if (body.code !== 200) throw new Error(`teacher login failed: ${JSON.stringify(body).slice(0, 200)}`)
  return {
    accessToken: body.data.token,
    refreshToken: body.data.refreshToken,
    user: body.data.userInfo,
    permissions: body.data.permissions
  }
}

async function injectTeacher(page: any, auth: any) {
  await page.addInitScript((data: any) => {
    const role = data.user.role
    localStorage.setItem('current_role', role)
    localStorage.setItem(`teacher_token_${role}`, data.accessToken)
    localStorage.setItem(`teacher_accessToken_${role}`, data.accessToken)
    localStorage.setItem(`teacher_refreshToken_${role}`, data.refreshToken)
    localStorage.setItem(`teacher_role_${role}`, role)
    localStorage.setItem(`teacher_userId_${role}`, String(data.user.id))
    localStorage.setItem(`teacher_username_${role}`, data.user.username)
    localStorage.setItem(`teacher_name_${role}`, data.user.name)
    localStorage.setItem(`teacher_isReadOnly_${role}`, 'false')
    localStorage.setItem('teacherType', data.user.teacherType || '')
    localStorage.setItem('teacherPermissions', JSON.stringify(data.permissions || []))
  }, auth)
}

test.describe.serial('教师企业关系表单智能填表', () => {
  test.slow()
  test.use({ viewport: { width: 1600, height: 900 } })

  test('企业走访与校内对接人表单可关联并填充文字字段', async ({ page, request }) => {
    const auth = await loginTeacher(request)
    await injectTeacher(page, auth)
    await page.goto('/teacher/company-list')
    await expect(page).not.toHaveURL(/\/login/)
    await expect(page.getByRole('button', { name: '登记走访' })).toBeVisible({ timeout: 30_000 })

    await page.locator('button.agent-entry').click()
    await expect(page.locator('#agent-workspace')).toBeVisible()

    await page.getByRole('button', { name: '登记走访' }).click()
    const visitDialog = page.locator('.el-dialog:visible').filter({ hasText: '登记企业走访' })
    await expect(visitDialog).toBeVisible()
    await expect(page.locator('.agent-form-link', { hasText: '登记企业走访' })).toBeVisible()

    const remark = visitDialog.locator('textarea').first()
    await page.locator('#agent-input').fill('备注写阶段六企业走访验证，其他内容不要动')
    await page.locator('.agent-composer .send-button:not(.stop-button)').click()
    await expect(page.locator('.fill-event[data-fill-status="applied"]').first()).toBeVisible({ timeout: 120_000 })
    await expect(remark).toHaveValue(/阶段六企业走访验证/)
    await page.getByRole('button', { name: '撤销最近一批 AI 填写' }).click()
    await expect(remark).toHaveValue('')
    await visitDialog.getByRole('button', { name: '取消' }).click()
    await expect(visitDialog).toBeHidden()

    await page.getByRole('tab', { name: '校内对接人' }).click()
    const liaisonPanel = page.getByRole('tabpanel', { name: '校内对接人' })
    await expect(liaisonPanel).toBeVisible()
    const companySelect = liaisonPanel.locator('.el-select').first()
    await companySelect.click()
    await page.locator('.el-select-dropdown__item:visible').first().click()
    const addLiaison = page.getByRole('button', { name: '添加对接人' })
    await expect(addLiaison).toBeEnabled()
    await addLiaison.click()
    const liaisonDialog = page.locator('.el-dialog:visible').filter({ hasText: '添加校内对接人' })
    await expect(liaisonDialog).toBeVisible()
    await expect(page.locator('.agent-form-link', { hasText: '添加校内对接人' })).toBeVisible()
    await liaisonDialog.getByRole('button', { name: '取消' }).click()
  })
})
