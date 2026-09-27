/**
 * 企业端智能填表 Live 演示（阶段三验收）。
 *
 * 约束：只填不发布，不产生任何业务数据；用真实后端 + 真实模型。
 * 覆盖：关联表单识别 → 多字段首填 → “人数改成五个，薪资不变”连续修改 →
 * 撤销回滚 → 关闭无残留；附带语音入口可见性断言（默认开启）。
 */
import { test, expect } from '@playwright/test'

const COMPANY = { username: '企业1', password: '123456' }

async function loginAsCompany(request: any) {
  const res = await request.post('http://localhost:8080/api/auth/login', {
    data: { username: COMPANY.username, password: COMPANY.password }
  })
  const body = await res.json()
  if (body.code !== 200) throw new Error('company login failed: ' + JSON.stringify(body).slice(0, 200))
  return body.data as { accessToken: string; refreshToken: string; user: any }
}

async function injectCompanyAuth(page: any, data: { accessToken: string; refreshToken: string; user: any }) {
  await page.addInitScript((auth: any) => {
    localStorage.setItem('current_role', 'ROLE_COMPANY')
    localStorage.setItem('company_token_ROLE_COMPANY', auth.accessToken)
    localStorage.setItem('company_accessToken_ROLE_COMPANY', auth.accessToken)
    localStorage.setItem('company_refreshToken_ROLE_COMPANY', auth.refreshToken)
    localStorage.setItem('company_role_ROLE_COMPANY', 'ROLE_COMPANY')
    localStorage.setItem('company_userId_ROLE_COMPANY', String(auth.user.id))
    localStorage.setItem('company_username_ROLE_COMPANY', auth.user.username)
    localStorage.setItem('company_name_ROLE_COMPANY', auth.user.name)
    localStorage.setItem('company_isReadOnly_ROLE_COMPANY', 'false')
    localStorage.setItem('company_companyId_COMPANY', String(auth.user.id))
  }, data)
}

async function positionCount(request: any, token: string) {
  const res = await request.get('http://localhost:8080/api/admin/positions/company/1', {
    headers: { Authorization: `Bearer ${token}` }
  })
  const body = await res.json()
  return Array.isArray(body.data) ? body.data.length : -1
}

test.describe.serial('企业岗位发布智能填表', () => {
  test.slow()
  // 宽视口使 AI 侧栏停靠为侧边栏（而非浮层），与岗位弹窗互不遮挡
  test.use({ viewport: { width: 1600, height: 900 } })

  test('多字段首填 → 连续修改 → 撤销 → 关闭无残留', async ({ page, request }) => {
    const auth = await loginAsCompany(request)
    const before = await positionCount(request, auth.accessToken)
    await injectCompanyAuth(page, auth)
    await page.goto('/company/jobs')
    await expect(page).not.toHaveURL(/\/login/)

    // 先开 AI 侧栏（模态弹窗会盖住 header 入口，侧栏本身浮于弹窗之上）
    await page.locator('button.agent-entry').click()
    await expect(page.locator('#agent-workspace')).toBeVisible()

    // 打开发布弹窗并读取真实岗位类别
    await page.getByRole('button', { name: '发布岗位' }).click()
    const dlg = page.locator('.el-dialog').filter({ hasText: '计划招聘人数' })
    await expect(dlg).toBeVisible()
    await expect(page.locator('.agent-form-link', { hasText: '发布岗位' })).toBeVisible()
    // 默认开启语音：麦克风入口可见
    await expect(page.locator('.mic-button')).toBeVisible()
    await dlg.locator('.el-form-item', { hasText: '岗位类别' }).locator('.el-select').click()
    const deptLabel = ((await page.locator('.el-select-dropdown__item:visible').first().textContent()) || '').trim()
    expect(deptLabel.length).toBeGreaterThan(0)
    await page.keyboard.press('Escape')

    const fieldInput = (label: string) =>
      dlg.locator('.el-form-item', { hasText: label }).locator('input').first()
    const send = async (text: string) => {
      await page.locator('#agent-input').fill(text)
      await page.locator('.agent-composer .send-button:not(.stop-button)').click()
      await expect(page.locator('.fill-event[data-fill-status="applied"]').first())
        .toBeVisible({ timeout: 90000 })
    }

    // 首填：多字段一次下发
    await send(`岗位类别选${deptLabel}，薪资20到30K每月，计划招3人，岗位描述写负责后端开发工作`)
    await expect(fieldInput('计划招聘人数')).toHaveValue('3', { timeout: 15000 })
    await expect(dlg.locator('.el-form-item', { hasText: '薪资范围' }).getByPlaceholder('最低'))
      .toHaveValue('20')
    await expect(dlg.locator('.el-form-item', { hasText: '薪资范围' }).getByPlaceholder('最高'))
      .toHaveValue('30')
    await expect(dlg.locator('.el-form-item', { hasText: '岗位描述' }).locator('textarea'))
      .toHaveValue(/负责后端开发工作/)

    // 连续修改：只改人数，薪资与其他不动
    await send('人数改成五个，薪资不变，其他内容不要动')
    await expect(fieldInput('计划招聘人数')).toHaveValue('5', { timeout: 15000 })
    await expect(dlg.locator('.el-form-item', { hasText: '薪资范围' }).getByPlaceholder('最低'))
      .toHaveValue('20')
    await expect(dlg.locator('.el-form-item', { hasText: '岗位描述' }).locator('textarea'))
      .toHaveValue(/负责后端开发工作/)

    // 撤销最近一批：人数回到 3
    await page.getByRole('button', { name: '撤销最近一批 AI 填写' }).click()
    await expect(fieldInput('计划招聘人数')).toHaveValue('3', { timeout: 15000 })

    // 取消关闭：不产生业务数据
    await dlg.getByRole('button', { name: '取消' }).click()
    await expect(dlg).toBeHidden()
    expect(await positionCount(request, auth.accessToken)).toBe(before)
  })
})
