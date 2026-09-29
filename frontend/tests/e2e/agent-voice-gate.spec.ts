/**
 * 语音能力禁用 live 验证（阶段三验收）。
 * 前置条件由外部编排：DB 已下发 company 去 voice_input 的临时配置。
 * 断言：AI 入口仍在（chat 开）、工作区可开、麦克风按钮消失。
 */
import { test, expect } from '@playwright/test'

test('语音关闭时麦克风入口隐藏', async ({ page, request }) => {
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
  await page.goto('/company/jobs')
  await expect(page.locator('button.agent-entry')).toBeVisible()
  await page.locator('button.agent-entry').click()
  await expect(page.locator('#agent-workspace')).toBeVisible()
  await expect(page.locator('.mic-button')).toHaveCount(0)
})
