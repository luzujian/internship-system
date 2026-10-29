import { test, expect } from '@playwright/test'
import { authenticateAs } from './helpers/auth'

test('管理员清空或修改教师手机号后，重新打开仍显示已保存的值', async ({ page }) => {
  const permissions = ['user:teacher:view', 'user:teacher:edit']
  await authenticateAs(page, 'admin', { adminPermissionCodes: permissions })

  const teacher = {
    id: 101, teacherUserId: 'teacher-101', name: '测试教师', gender: 0,
    departmentId: '1', divisionId: null, teacherType: 'COLLEGE',
    phone: '13800000000', email: 'teacher@example.test', status: 0
  }
  const updates: Array<Record<string, unknown>> = []
  await page.route(url => url.pathname.startsWith('/api/'), async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    let data: unknown = []
    if (path.startsWith('/api/auth/session')) {
      data = { lastActivityAt: Date.now(), timeoutMinutes: 120, serverTime: Date.now() }
    } else if (path === '/api/admin/departments') {
      data = [{ id: 1, name: '测试学院' }]
    } else if (path === '/api/admin/teachers/101') {
      data = { ...teacher }
    } else if (path === '/api/admin/teachers' && request.method() === 'PUT') {
      const body = request.postDataJSON()
      updates.push(body)
      if (body.phone !== undefined && body.phone !== null) teacher.phone = body.phone
      if (body.gender !== undefined && body.gender !== null) teacher.gender = body.gender
      if (body.status !== undefined && body.status !== null) teacher.status = body.status
      data = null
    } else if (path === '/api/admin/teachers') {
      data = { rows: [{ ...teacher }], total: 1 }
    } else if (path.includes('/api/admin/permissions/')) {
      data = permissions.map(permissionCode => ({ permissionCode }))
    } else if (path === '/api/home/ai-config') {
      data = false
    } else if (path === '/api/agent/capabilities') {
      data = { enabled: false, actor: { id: '4', ownerKey: 'mock:admin', role: 'ROLE_ADMIN', readOnly: false }, capabilities: {}, tools: [] }
    }
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ code: 200, data }) })
  })

  await page.goto('/admin/teacher-users')
  const row = page.locator('.el-table__body-wrapper tbody tr').filter({ hasText: '测试教师' }).first()
  await expect(row).toContainText('13800000000')
  const edit = async () => {
    const more = row.getByRole('button', { name: '更多操作' })
    if (await more.isVisible()) await more.click()
    await page.locator('.table-btn.edit:visible').first().click()
    return page.locator('.el-dialog:visible').filter({ hasText: '编辑教师' })
  }

  let dialog = await edit()
  await dialog.getByPlaceholder('请输入手机号（非必填）').fill('')
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(dialog).toBeHidden()
  await expect.poll(() => updates.length).toBe(1)
  expect(updates[0]).toMatchObject({ phone: '', gender: 0 })
  expect(updates[0]).not.toHaveProperty('status')
  expect(teacher.status).toBe(0)
  await expect(row).not.toContainText('13800000000')

  dialog = await edit()
  const phone = dialog.getByPlaceholder('请输入手机号（非必填）')
  await expect(phone).toHaveValue('')
  await phone.fill('13900000000')
  await dialog.getByRole('button', { name: '确定' }).click()
  await expect(dialog).toBeHidden()
  await expect.poll(() => updates.length).toBe(2)
  expect(updates[1]).toMatchObject({ phone: '13900000000', gender: 0 })
  expect(teacher.status).toBe(0)
  await expect(row).toContainText('13900000000')

  dialog = await edit()
  await expect(dialog.getByPlaceholder('请输入手机号（非必填）')).toHaveValue('13900000000')
})
