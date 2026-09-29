import { test, expect } from '@playwright/test'
import { authenticateAs, mockApis } from './helpers/auth'
import { getRoleApiMocks } from './helpers/roleMocks'

const STUDENT_JOBS = [
  {
    id: 1,
    title: '前端开发实习生',
    industryName: 'IT',
    company: '示例科技有限公司',
    location: '北京 海淀区',
    salary: '3000-5000元/月',
    remainingQuota: 3,
    tags: ['实习'],
    isFavorite: false
  }
]

test.describe('移动端关键交互（手机 375×667）', () => {
  test('企业招聘管理：手机端渲染卡片列表而非表格，横屏保持卡片', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile', '仅手机视口')
    await authenticateAs(page, 'company')
    await mockApis(page, getRoleApiMocks('company'))
    await page.goto('/company/jobs')

    await expect(page.locator('.mobile-card-list')).toBeVisible()
    await expect(page.locator('.mobile-card')).toHaveCount(1)
    await expect(page.locator('.el-table')).toHaveCount(0)

    await page.setViewportSize({ width: 667, height: 375 })
    await expect(page.locator('.mobile-card-list')).toBeVisible()
    await expect(page.locator('.el-table')).toHaveCount(0)
  })

  test('学生实习信息：岗位卡片单列展示，筛选面板可开合', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile', '仅手机视口')
    await authenticateAs(page, 'student')
    await mockApis(page, { ...getRoleApiMocks('student'), '/positions/all': STUDENT_JOBS })
    await page.goto('/student/jobs')

    await expect(page.locator('.job-card')).toHaveCount(1)
    const cardBox = await page.locator('.job-card').first().boundingBox()
    expect(cardBox).not.toBeNull()
    expect(cardBox!.width).toBeGreaterThanOrEqual(300)

    await page.locator('.filter-toggle-button').click()
    await expect(page.locator('.filter-panel')).toBeVisible()
    await page.locator('.filter-toggle-button').click()
    await expect(page.locator('.filter-panel')).toBeHidden()
  })

  test('教师审批：密集表格仅容器内横滑，行操作收敛为更多菜单', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile', '仅手机视口')
    await authenticateAs(page, 'teacher')
    await mockApis(page, getRoleApiMocks('teacher'))
    await page.goto('/teacher/approval')

    await expect(page.locator('.el-table').first()).toBeVisible()

    const wrap = page.locator('.el-table .el-scrollbar__wrap').first()
    await expect(async () => {
      const metrics = await wrap.evaluate((el) => ({
        scrollWidth: el.scrollWidth,
        clientWidth: el.clientWidth
      }))
      expect(metrics.scrollWidth).toBeGreaterThan(metrics.clientWidth)
    }).toPass()

    await wrap.evaluate((el) => {
      el.scrollLeft = 300
    })
    await expect
      .poll(async () => wrap.evaluate((el) => el.scrollLeft), { timeout: 5000 })
      .toBeGreaterThan(0)

    const moreButton = page.locator('.row-actions-more').first()
    await expect(moreButton).toBeVisible()
    await moreButton.click()
    await expect(page.locator('.row-actions-dropdown-menu').first()).toBeVisible()
  })

  test('企业发布岗位：弹窗限宽、表单标签置顶、Escape 可关闭', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile', '仅手机视口')
    await authenticateAs(page, 'company')
    await mockApis(page, getRoleApiMocks('company'))
    await page.goto('/company/jobs')

    await page.locator('.publish-actions').getByRole('button', { name: '发布岗位' }).click()
    const dialog = page.locator('.el-dialog').filter({ hasText: '发布岗位' }).first()
    await expect(dialog).toBeVisible()

    const dialogBox = await dialog.boundingBox()
    expect(dialogBox).not.toBeNull()
    expect(dialogBox!.width).toBeLessThanOrEqual(375 - 24 + 1)

    const firstFormItem = page.locator('.el-dialog .el-form-item').first()
    await expect(firstFormItem).toBeVisible()
    const layout = await firstFormItem.evaluate((el) => {
      const label = el.querySelector('.el-form-item__label')
      const content = el.querySelector('.el-form-item__content')
      if (!label || !content) return null
      return {
        labelTop: label.getBoundingClientRect().top,
        contentTop: content.getBoundingClientRect().top
      }
    })
    expect(layout).not.toBeNull()
    expect(layout!.labelTop).toBeLessThan(layout!.contentTop)

    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
  })

  test('AI 助手：手机端全宽面板、无缩放手柄、可关闭', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile', '仅手机视口')
    await authenticateAs(page, 'student')
    await mockApis(page, {
      ...getRoleApiMocks('student'),
      '/home/ai-config': true,
      '/agent/capabilities': {
        enabled: true,
        actor: { ownerKey: 'mock:student', id: '1', name: '受控 mock 学生', role: 'ROLE_STUDENT', readOnly: false },
        capabilities: { agent_chat: true, form_fill: true, voice_input: true },
        tools: []
      }
    })
    await page.goto('/student/home')

    await page.getByRole('button', { name: 'AI 助手', exact: true }).click()

    const panel = page.locator('#agent-workspace')
    await expect(panel).toBeVisible()
    await expect(async () => {
      const box = await panel.boundingBox()
      expect(box).not.toBeNull()
      expect(box!.width).toBeGreaterThanOrEqual(374)
    }).toPass()

    await expect(page.locator('#agent-workspace .agent-resize')).toHaveCount(0)

    await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
    await expect(panel).toBeHidden()
  })

  test('登录页：输入字号 16px、点击区不低于 44px、登录卡单列满宽', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name !== 'mobile', '仅手机视口')
    await mockApis(page)
    await page.goto('/login')

    const loginForm = page.locator('.login-form')
    await expect(loginForm).toBeVisible()
    const formBox = await loginForm.boundingBox()
    expect(formBox).not.toBeNull()
    expect(formBox!.width).toBeGreaterThanOrEqual(300)
    expect(formBox!.width).toBeLessThanOrEqual(420)

    const input = page.locator('.login-form .el-input__wrapper').first()
    await expect(input).toBeVisible()
    const fontSize = await input.evaluate((el) => parseFloat(getComputedStyle(el).fontSize))
    expect(fontSize).toBeGreaterThanOrEqual(16)
    const inputBox = await input.boundingBox()
    expect(inputBox!.width).toBeGreaterThanOrEqual(280)

    const loginButton = page.getByRole('button', { name: /登\s*录/ })
    await expect(loginButton).toBeVisible()
    const buttonBox = await loginButton.boundingBox()
    expect(buttonBox).not.toBeNull()
    expect(buttonBox!.height).toBeGreaterThanOrEqual(44)
  })
})
