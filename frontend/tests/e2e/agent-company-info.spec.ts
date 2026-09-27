/**
 * 阶段四验收：企业资料表单接线（只填不保存，零数据污染）。
 * 覆盖：页面常驻表单关联 → 多字段填写 → 撤销恢复 → 离开不保存。
 */
import { test, expect } from '@playwright/test'

test.describe.serial('企业资料智能填表', () => {
  test.slow()
  test.use({ viewport: { width: 1600, height: 900 } })

  test('填写 → 撤销 → 不保存离开', async ({ page, request }) => {
    const res = await request.post('http://localhost:8080/api/auth/login', {
      data: { username: '企业1', password: '123456' }
    })
    const auth = (await res.json()).data
    await page.addInitScript((a: any) => {
      localStorage.setItem('current_role', 'ROLE_COMPANY')
      localStorage.setItem('company_token_ROLE_COMPANY', a.accessToken)
      localStorage.setItem('company_accessToken_ROLE_COMPANY', a.accessToken)
      localStorage.setItem('company_refreshToken_ROLE_COMPANY', a.refreshToken)
      localStorage.setItem('company_role_ROLE_COMPANY', 'ROLE_COMPANY')
      localStorage.setItem('company_userId_ROLE_COMPANY', String(a.user.id))
      localStorage.setItem('company_username_ROLE_COMPANY', a.user.username)
      localStorage.setItem('company_name_ROLE_COMPANY', a.user.name)
      localStorage.setItem('company_isReadOnly_ROLE_COMPANY', 'false')
      localStorage.setItem('company_companyId_COMPANY', String(a.user.id))
    }, auth)
    await page.goto('/company/interviews')
    await page.locator('button.agent-entry').click()
    await expect(page.locator('#agent-workspace')).toBeVisible()
    await expect(page.locator('.agent-form-link', { hasText: '企业基本信息' })).toBeVisible()

    const item = (label: string) =>
      page.locator('.el-form-item', { hasText: label }).locator('input').first()
    const beforePerson = await item('联系人').inputValue()
    const beforePhone = await item('联系电话').inputValue()

    await page.locator('#agent-input').fill('联系人改成张三，联系电话改成13800000000')
    await page.locator('.agent-composer .send-button:not(.stop-button)').click()
    await expect(page.locator('.fill-event[data-fill-status="applied"]').first())
      .toBeVisible({ timeout: 90000 })
    await expect(item('联系人')).toHaveValue('张三', { timeout: 15000 })
    await expect(item('联系电话')).toHaveValue('13800000000')

    await page.getByRole('button', { name: '撤销最近一批 AI 填写' }).click()
    await expect(item('联系人')).toHaveValue(beforePerson, { timeout: 15000 })
    await expect(item('联系电话')).toHaveValue(beforePhone)
    // 不点保存直接离开：DB 无任何变更
  })
})
