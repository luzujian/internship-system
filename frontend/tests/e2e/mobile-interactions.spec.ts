import { test, expect, type Locator, type Page, type TestInfo } from '@playwright/test'
import { authenticateAs, mockApis, type TestRole } from './helpers/auth'
import { getRoleApiMocks, ROLE_HOME, STUDENT_JOBS, COMPANY_INTERNSHIP_STUDENTS } from './helpers/roleMocks'

const MOBILE_PROJECTS = ['mobile', 'webkit-mobile']
const PHONE_PROJECTS = ['mobile', 'tablet', 'webkit-mobile', 'webkit-tablet']

async function openRole(page: Page, role: TestRole, path: string, overrides: Record<string, unknown> = {}) {
  await authenticateAs(page, role)
  await mockApis(page, { ...getRoleApiMocks(role), ...overrides })
  await page.goto(path)
  await expect.poll(() => new URL(page.url()).pathname, { message: `必须实际进入 ${path}` }).toBe(path)
  await expect(page.locator('.sidebar-toggle-btn')).toBeVisible()
}

async function expectInViewport(locator: Locator, page: Page, touchTarget = false) {
  await expect(locator).toBeVisible()
  await expect(async () => {
    const box = await locator.boundingBox()
    expect(box).not.toBeNull()
    const { width, height } = page.viewportSize()!
    expect(box!.x).toBeGreaterThanOrEqual(-1)
    expect(box!.y).toBeGreaterThanOrEqual(-1)
    expect(box!.x + box!.width).toBeLessThanOrEqual(width + 1)
    expect(box!.y + box!.height).toBeLessThanOrEqual(height + 1)
    if (touchTarget) {
      expect(box!.width).toBeGreaterThanOrEqual(44)
      expect(box!.height).toBeGreaterThanOrEqual(44)
    }
    expect(await locator.evaluate(el => {
      const rect = el.getBoundingClientRect()
      const hit = document.elementFromPoint(rect.x + rect.width / 2, rect.y + rect.height / 2)
      return !!hit && (el === hit || el.contains(hit))
    })).toBe(true)
  }).toPass({ timeout: 10000 })
}

async function screenshot(page: Page, testInfo: TestInfo, name: string) {
  const path = testInfo.outputPath(`${name}.png`)
  await page.screenshot({ path, fullPage: false, animations: 'disabled' })
  await testInfo.attach(name, { path, contentType: 'image/png' })
}

test.describe('移动端关键交互', () => {
  for (const role of ['student', 'teacher', 'company', 'admin'] as TestRole[]) {
    test(`${role}：小屏和横屏顶栏导航、AI、账号均可触控`, async ({ page }, testInfo) => {
      test.skip(!MOBILE_PROJECTS.includes(testInfo.project.name), '关键手机尺寸矩阵')
      await openRole(page, role, ROLE_HOME[role])
      for (const viewport of [{ width: 320, height: 640 }, { width: 360, height: 740 }, { width: 375, height: 667 }, { width: 390, height: 844 }, { width: 430, height: 932 }, { width: 667, height: 375 }, { width: 768, height: 1024 }]) {
        await page.setViewportSize(viewport)
        await expectInViewport(page.locator('.sidebar-toggle-btn'), page, true)
        await expectInViewport(page.getByRole('button', { name: '问问 AI', exact: true }), page, true)
        const account = page.getByRole('button', { name: /账号菜单/ })
        await expectInViewport(account, page, true)
        await account.click()
        await expect(page.getByRole('menuitem', { name: '退出登录', exact: true })).toBeVisible()
        await page.keyboard.press('Escape')
      }
      await screenshot(page, testInfo, `${role}-header-768`)
    })
  }

  test('企业招聘：卡片直接操作，横屏保留卡片', async ({ page }, testInfo) => {
    test.skip(!PHONE_PROJECTS.includes(testInfo.project.name), '手机和平板边界')
    await openRole(page, 'company', '/company/jobs')
    await expect(page.locator('.mobile-card-list')).toBeVisible()
    await expect(page.locator('.mobile-card')).toHaveCount(1)
    await expect(page.locator('.el-table')).toHaveCount(0)
    await page.locator('.mobile-card').getByRole('button', { name: '编辑', exact: true }).click()
    const dialog = page.getByRole('dialog').filter({ hasText: '编辑岗位' })
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: '取消', exact: true }).click()
    await page.setViewportSize({ width: 667, height: 375 })
    await expect(page.locator('.mobile-card-list')).toBeVisible()
    await expect(page.locator('.el-table')).toHaveCount(0)
  })

  test('学生岗位：默认收起筛选，卡片详情可达，对比状态跨断点保留', async ({ page }, testInfo) => {
    test.skip(!PHONE_PROJECTS.includes(testInfo.project.name), '手机和平板边界')
    await openRole(page, 'student', '/student/jobs')
    const card = page.locator('.mobile-card').first()
    await expect(card).toContainText(STUDENT_JOBS[0].title)
    await expect(page.locator('.filter-panel')).toBeHidden()
    await page.locator('.filter-toggle-button').click()
    await expect(page.locator('.filter-panel')).toBeVisible()
    await page.locator('.filter-toggle-button').click()
    await expect(page.locator('.filter-panel')).toBeHidden()
    await card.getByRole('button', { name: '查看详情', exact: true }).click()
    await expect(page.locator('.el-dialog:visible')).toBeVisible()
    await page.keyboard.press('Escape')
    await card.getByRole('button', { name: 'AI 比较', exact: true }).click()
    await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
    await expect(card.getByRole('button', { name: '已加入 AI 比较', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await page.setViewportSize({ width: 769, height: 900 })
    await expect(page.locator('.data-table')).toBeVisible()
    await expect(page.locator('.mobile-card-list')).toHaveCount(0)
    await expect(page.locator('.data-table').getByRole('button', { name: '已加入 AI 比较', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await page.setViewportSize({ width: 768, height: 1024 })
    await expect(page.locator('.mobile-card').getByRole('button', { name: '已加入 AI 比较', exact: true })).toHaveAttribute('aria-pressed', 'true')
    await screenshot(page, testInfo, 'student-jobs-cards')
  })

  test('教师审核：卡片显示身份状态并可打开详情', async ({ page }, testInfo) => {
    test.skip(!PHONE_PROJECTS.includes(testInfo.project.name), '手机和平板边界')
    await openRole(page, 'teacher', '/teacher/approval')
    const card = page.locator('.mobile-card').first()
    await expect(card).toContainText('张三')
    await expect(card).toContainText('2021001')
    await expect(card).toContainText('待审核')
    await card.getByRole('button', { name: '查看详情', exact: true }).click()
    const modal = page.locator('.modal-overlay:visible')
    await expect(modal).toBeVisible()
    await modal.getByRole('button', { name: '关闭', exact: true }).scrollIntoViewIfNeeded()
    await expectInViewport(modal.getByRole('button', { name: '关闭', exact: true }), page)
    await modal.getByRole('button', { name: '关闭', exact: true }).click()
    await expect(modal).toBeHidden()
  })

  test('教师学生跟踪：手机选择跨 768/769 切换保留', async ({ page }, testInfo) => {
    test.skip(!PHONE_PROJECTS.includes(testInfo.project.name), '手机和平板边界')
    await openRole(page, 'teacher', '/teacher/student-tracking')
    await expect(page.locator('.mobile-card')).toHaveCount(1)
    await page.locator('.mobile-card .el-checkbox').first().click()
    await expect(page.locator('.mobile-selection-toolbar')).toContainText('已选择 1 人')
    await page.setViewportSize({ width: 769, height: 900 })
    await expect(page.locator('.el-table')).toBeVisible()
    await expect(page.locator('.el-table__body .el-checkbox input')).toBeChecked()
    await page.setViewportSize({ width: 768, height: 1024 })
    await expect(page.getByRole('checkbox', { name: '选择学生 张三', exact: true })).toBeChecked()
    await expect(page.locator('.mobile-selection-toolbar')).toContainText('已选择 1 人')
  })

  test('企业申请：手机卡片显示申请状态并打开档案', async ({ page }, testInfo) => {
    test.skip(!PHONE_PROJECTS.includes(testInfo.project.name), '手机和平板边界')
    await openRole(page, 'company', '/company/application-view')
    const card = page.locator('.mobile-card').first()
    await expect(card).toContainText('张三')
    await expect(card).toContainText('前端开发工程师')
    await expect(card).toContainText('待审核')
    await card.getByRole('button', { name: /查看.*档案|查看详情/ }).click()
    await expect(page.locator('.el-dialog:visible')).toBeVisible()
    await page.keyboard.press('Escape')
  })

  test('企业实习学生：长字段、联系方式和分页可操作', async ({ page }, testInfo) => {
    test.skip(!PHONE_PROJECTS.includes(testInfo.project.name), '手机和平板边界')
    const student = { ...COMPANY_INTERNSHIP_STUDENTS[0], email: 'very.long.student.email.for.mobile.layout@example.com', positionName: '医疗信息系统前端开发与医学影像数据可视化实践实习岗位'.repeat(3) }
    await openRole(page, 'company', '/company/internship-students', { '/company/internship-students': { total: 1, rows: [student] } })
    const card = page.locator('.mobile-card').first()
    await expect(card).toContainText(student.positionName)
    await expect(card.getByRole('link', { name: student.phone })).toHaveAttribute('href', `tel:${student.phone}`)
    await expect(card.getByRole('link', { name: student.email })).toHaveAttribute('href', `mailto:${student.email}`)
    await expect(card).toBeVisible()
    await expect(page.locator('.el-pagination')).toContainText('共 1 条')
    expect(await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(1)
    await screenshot(page, testInfo, 'company-internship-long-content')
  })

  test('企业实习学生：请求失败可重试，空状态清晰', async ({ page }, testInfo) => {
    test.skip(!MOBILE_PROJECTS.includes(testInfo.project.name), '关键反馈场景')
    await openRole(page, 'company', '/company/internship-students', {
      '/company/internship-students': (_url: string, count: number) => count === 1
        ? { code: 500, message: '验收模拟：名单暂时不可用', data: null }
        : { code: 200, data: { total: 0, rows: [] } }
    })
    await expect(page.locator('.load-error')).toContainText('名单暂时不可用')
    await page.getByRole('button', { name: '刷新名单', exact: true }).click()
    await expect(page.locator('.load-error')).toHaveCount(0)
    await expect(page.locator('.el-empty')).toContainText('当前没有正在本企业实习的学生')
  })

  test('企业发布岗位：长弹窗标签置顶，底部提交和关闭操作可达', async ({ page }, testInfo) => {
    test.skip(!PHONE_PROJECTS.includes(testInfo.project.name), '手机和平板边界')
    await openRole(page, 'company', '/company/jobs')
    await page.locator('.publish-actions').getByRole('button', { name: '发布岗位' }).click()
    const dialog = page.getByRole('dialog', { name: '发布岗位', exact: true })
    await expect(dialog).toBeVisible()
    const box = await dialog.locator('.el-dialog').boundingBox()
    expect(box!.width).toBeLessThanOrEqual(page.viewportSize()!.width - 16)
    const layout = await dialog.locator('.el-form-item').first().evaluate(el => ({
      labelBottom: el.querySelector('.el-form-item__label')!.getBoundingClientRect().bottom,
      contentTop: el.querySelector('.el-form-item__content')!.getBoundingClientRect().top
    }))
    expect(layout.labelBottom).toBeLessThanOrEqual(layout.contentTop + 1)
    const publish = dialog.getByRole('button', { name: '发布', exact: true })
    await publish.scrollIntoViewIfNeeded()
    await expectInViewport(publish, page, true)
    await expectInViewport(dialog.getByRole('button', { name: '取消', exact: true }), page, true)
    await expectInViewport(dialog.locator('.el-dialog__headerbtn'), page, true)
    await screenshot(page, testInfo, 'publish-dialog-mobile')
    await page.keyboard.press('Escape')
    await expect(dialog).toBeHidden()
  })

  test('管理员密集表格：只在容器横滑，更多操作可触控', async ({ page }, testInfo) => {
    test.skip(!MOBILE_PROJECTS.includes(testInfo.project.name), '关键手机密集表格场景')
    const permissionCodes = ['user:student:view', 'user:student:edit', 'user:student:delete', 'user:student:reset']
    await authenticateAs(page, 'admin', { adminPermissionCodes: permissionCodes })
    await mockApis(page, { ...getRoleApiMocks('admin'),
      '/admin/permissions/roles/1/permissions': permissionCodes.map(permissionCode => ({ permissionCode })),
      '/admin/students': { total: 1, rows: [{ id: 301, studentId: '2023001001', name: '张三', username: '2023001001', grade: 2023, majorId: 1, className: '医学信息工程 2023 级 1 班', status: 1, phone: '13800138000' }] }
    })
    await page.goto('/admin/student-users')
    await expect(page).toHaveURL(/\/admin\/student-users$/)
    const table = page.locator('.table-card .el-table').first()
    const wrap = table.locator('.el-scrollbar__wrap').first()
    await expect(table).toContainText('张三')
    await expect.poll(() => wrap.evaluate(el => el.scrollWidth - el.clientWidth)).toBeGreaterThan(0)
    await expect.poll(() => table.evaluate(el => getComputedStyle(el, '::after').content)).toContain('滑动')
    await wrap.evaluate(el => { el.scrollLeft = 300 })
    await expect.poll(() => wrap.evaluate(el => el.scrollLeft)).toBeGreaterThan(0)
    const more = table.getByRole('button', { name: '更多操作', exact: true }).first()
    await more.scrollIntoViewIfNeeded()
    await expectInViewport(more, page, true)
    await more.click()
    const menu = page.locator('.row-actions-dropdown-menu:visible')
    await expect(menu).toBeVisible()
    await expect(menu.locator('.el-button')).toHaveCount(4)
    for (const button of await menu.locator('.el-button').all()) await expectInViewport(button, page, true)
    await page.keyboard.press('Escape')
  })

  test('日期范围：面板在手机内上下排列、可选择并关闭', async ({ page }, testInfo) => {
    test.skip(!MOBILE_PROJECTS.includes(testInfo.project.name), '关键手机日期场景')
    await openRole(page, 'admin', '/admin/application-withdrawal-records')
    const filterToggle = page.locator('.mobile-filter-toggle:visible')
    if (await filterToggle.count()) await filterToggle.click()
    await page.getByPlaceholder('开始日期', { exact: true }).click()
    const picker = page.locator('.el-date-range-picker:visible')
    await expect(picker).toBeVisible()
    const box = await picker.boundingBox()
    expect(box!.x).toBeGreaterThanOrEqual(0)
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width + 1)
    await expect(async () => {
      const visibleBox = await picker.boundingBox()
      expect(visibleBox!.y).toBeGreaterThanOrEqual(0)
      expect(visibleBox!.y + visibleBox!.height).toBeLessThanOrEqual(page.viewportSize()!.height + 1)
    }).toPass({ timeout: 10000 })
    await expect(async () => {
      const panels = picker.locator('.el-date-range-picker__content')
      const left = await panels.nth(0).boundingBox()
      const right = await panels.nth(1).boundingBox()
      expect(right!.y).toBeGreaterThanOrEqual(left!.y + left!.height - 1)
    }).toPass({ timeout: 10000 })
    await picker.locator('.el-date-range-picker__content').first().locator('.el-date-table td.available:not(.prev-month):not(.next-month)').nth(2).click()
    await picker.locator('.el-date-range-picker__content').last().locator('.el-date-table td.available:not(.prev-month):not(.next-month)').nth(5).click()
    await expect(picker).toBeHidden()
    await expect(page.getByPlaceholder('开始日期', { exact: true })).toHaveValue(/\d{4}-\d{2}-\d{2}/)
    await page.getByPlaceholder('开始日期', { exact: true }).click()
    await expect(picker).toBeVisible()
    await expect(async () => {
      const reopened = await picker.boundingBox()
      expect(reopened!.y).toBeGreaterThanOrEqual(0)
      expect(reopened!.y + reopened!.height).toBeLessThanOrEqual(page.viewportSize()!.height + 1)
    }).toPass({ timeout: 10000 })
    await screenshot(page, testInfo, 'date-range-mobile')
    await page.keyboard.press('Escape')
    await expect(picker).toBeHidden()
    await page.setViewportSize({ width: 667, height: 375 })
    await page.getByPlaceholder('开始日期', { exact: true }).click()
    await expectInViewport(picker, page)
    const scrollBody = picker.locator('.el-picker-panel__body-wrapper')
    await scrollBody.evaluate(el => { el.scrollTop = el.scrollHeight })
    await expect.poll(() => scrollBody.evaluate(el => el.scrollTop)).toBeGreaterThan(0)
    await page.keyboard.press('Escape')
    await expect(picker).toBeHidden()
  })

  test('AI：768 全宽和 769 桌面宽度，缩小可视高度后输入与发送可达', async ({ page }, testInfo) => {
    test.skip(!MOBILE_PROJECTS.includes(testInfo.project.name), '关键 AI 断点场景')
    await openRole(page, 'student', '/student/home')
    await page.getByRole('button', { name: '问问 AI', exact: true }).click()
    const panel = page.locator('#agent-workspace')
    await expect(panel).toBeVisible()
    for (const width of [375, 768, 769, 390]) {
      await page.setViewportSize({ width, height: width === 390 ? 460 : 844 })
      await expect(async () => {
        const box = await panel.boundingBox()
        expect(box).not.toBeNull()
        if (width <= 768) expect(Math.abs(box!.width - width)).toBeLessThanOrEqual(1)
        else expect(box!.width).toBeLessThan(width)
      }).toPass({ timeout: 10000 })
      const composer = page.locator('#agent-input')
      await composer.fill('请帮我分析适合的实习岗位。'.repeat(15))
      await composer.focus()
      await expectInViewport(composer, page)
      await expectInViewport(panel.getByRole('button', { name: '发送任务', exact: true }), page, width <= 768)
      if (width <= 768) await expect(panel.locator('.agent-resize')).toHaveCount(0)
    }
    await screenshot(page, testInfo, 'ai-reduced-viewport')
    await page.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
    await expect(panel).toBeHidden()
    await expect(page).toHaveURL(/\/student\/home$/)
    await expectInViewport(page.locator('.sidebar-toggle-btn'), page, true)
  })

  test('岗位详情打开 AI：输入与关闭可操作，返回后详情保持', async ({ page }, testInfo) => {
    test.skip(!MOBILE_PROJECTS.includes(testInfo.project.name), 'Chrome / WebKit 手机叠层回归')
    await openRole(page, 'student', '/student/jobs')
    await page.locator('.mobile-card').first().getByRole('button', { name: '查看详情', exact: true }).click()
    const detail = page.getByRole('dialog', { name: '职位详情', exact: true })
    await expect(detail).toBeVisible()
    await expect(detail).toContainText(STUDENT_JOBS[0].title)
    await page.getByRole('button', { name: '问问 AI', exact: true }).click()
    const panel = page.locator('#agent-workspace')
    await expect(panel).toBeVisible()
    const composer = panel.locator('#agent-input')
    await composer.fill('请分析当前岗位的实习要求。')
    await expectInViewport(composer, page)
    await expectInViewport(panel.getByRole('button', { name: '关闭 AI 助手', exact: true }), page, true)
    expect(await detail.evaluate(el => !!el.closest('[inert]'))).toBe(true)
    await panel.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
    await expect(panel).toBeHidden()
    await expect(detail).toBeVisible()
    await expect(detail).toContainText(STUDENT_JOBS[0].title)
    expect(await detail.evaluate(el => !!el.closest('[inert]'))).toBe(false)
    await detail.locator('.el-dialog__headerbtn').click()
    await expect(detail).toBeHidden()
    await expect(page).toHaveURL(/\/student\/jobs$/)
  })

  for (const scenario of [{ width: 320, visibleHeight: 300 }, { width: 667, visibleHeight: 220 }]) {
    test(`仅 visualViewport 缩小：${scenario.width}px / 可见高 ${scenario.visibleHeight}px 输入与关闭可达`, async ({ page }, testInfo) => {
      test.skip(!MOBILE_PROJECTS.includes(testInfo.project.name), 'Chrome / WebKit 可视视口键盘模拟')
      await page.setViewportSize({ width: scenario.width, height: 600 })
      await openRole(page, 'student', '/student/home')
      await page.getByRole('button', { name: '问问 AI', exact: true }).click()
      const panel = page.locator('#agent-workspace')
      const composer = panel.locator('#agent-input')
      await composer.fill('软键盘展开后仍可核对并发送这条实习相关问题。'.repeat(6))
      await composer.focus()
      await page.evaluate(visibleHeight => {
        const vv = window.visualViewport!
        Object.defineProperty(vv, 'height', { configurable: true, value: visibleHeight })
        Object.defineProperty(vv, 'offsetTop', { configurable: true, value: 0 })
        vv.dispatchEvent(new Event('resize'))
      }, scenario.visibleHeight)
      expect(await page.evaluate(() => window.innerHeight)).toBe(600)
      await expect(panel).toHaveClass(/is-compact/)
      await expect.poll(() => page.locator('#app').evaluate(el => Math.round(el.getBoundingClientRect().top))).toBe(0)
      for (const control of [composer, panel.getByRole('button', { name: '发送任务', exact: true }), panel.getByRole('button', { name: '关闭 AI 助手', exact: true })]) {
        await expectInViewport(control, page, control !== composer)
        await expect(async () => {
          const bounds = await control.boundingBox()
          expect(bounds!.y).toBeGreaterThanOrEqual(0)
          expect(bounds!.y + bounds!.height).toBeLessThanOrEqual(scenario.visibleHeight + 1)
        }).toPass({ timeout: 10000 })
      }
      await screenshot(page, testInfo, `visual-viewport-${scenario.width}-${scenario.visibleHeight}`)
      await panel.getByRole('button', { name: '关闭 AI 助手', exact: true }).click()
      await expect(panel).toBeHidden()
      await expect(page).toHaveURL(/\/student\/home$/)
      await page.evaluate(() => {
        delete (window.visualViewport as any).height
        delete (window.visualViewport as any).offsetTop
        window.visualViewport!.dispatchEvent(new Event('resize'))
      })
      await expect.poll(() => page.locator('#app').evaluate(el => Math.round(el.getBoundingClientRect().top))).toBe(0)
    })
  }

  test('教师自动化模板确认：对话框位于 AI 上层，保留操作保存原草稿', async ({ page }, testInfo) => {
    test.skip(!MOBILE_PROJECTS.includes(testInfo.project.name), 'Chrome / WebKit 手机二级弹层回归')
    await openRole(page, 'teacher', '/teacher/home', {
      '/agent/capabilities': {
        enabled: true,
        actor: { ownerKey: 'mock:teacher', id: '3', name: '测试教师', role: 'ROLE_TEACHER_COLLEGE', readOnly: false },
        capabilities: { agent_chat: true, form_fill: true, voice_input: true, auto_task: true }, tools: []
      },
      '/teacher/auto-tasks/capability': { canManage: true, canExecute: true, featureEnabled: true },
      '/teacher/auto-tasks/v2/tasks': [],
      '/teacher/auto-tasks/v2/drafts': [],
      '/teacher/auto-tasks/v2/catalog': { canManage: true, canExecute: true, templates: [] }
    })
    await page.getByRole('button', { name: '问问 AI', exact: true }).click()
    const panel = page.locator('#agent-workspace')
    const draft = '请保留这份尚未发送的教师实习提醒草稿。'
    await panel.locator('#agent-input').fill(draft)
    await panel.getByRole('button', { name: '自动化任务', exact: true }).click()
    await expect(panel.getByRole('region', { name: '自动化任务管理', exact: true })).toBeVisible()
    await panel.getByRole('button', { name: '新建自动化任务', exact: true }).click()
    const confirm = page.locator('.el-message-box').filter({ hasText: '用此模板替换当前未发送内容？' })
    await expect(confirm).toBeVisible()
    const keep = confirm.getByRole('button', { name: '保留', exact: true })
    await expectInViewport(keep, page, true)
    const confirmationZ = await confirm.evaluate(el => Number(getComputedStyle(el.closest('.el-overlay') || el).zIndex))
    const panelZ = await panel.evaluate(el => Number(getComputedStyle(el).zIndex))
    expect(confirmationZ).toBeGreaterThan(panelZ)
    await screenshot(page, testInfo, 'teacher-template-confirmation')
    await keep.click()
    await expect(confirm).toBeHidden()
    await panel.getByRole('button', { name: '返回对话', exact: true }).click()
    await expect(panel.locator('#agent-input')).toHaveValue(draft)
    await expectInViewport(panel.locator('#agent-input'), page)
  })

  test('登录：输入 16px、按钮 44px、卡片适应窄屏', async ({ page }, testInfo) => {
    test.skip(!MOBILE_PROJECTS.includes(testInfo.project.name), '关键手机登录场景')
    await mockApis(page)
    await page.goto('/login')
    const form = page.locator('.login-form')
    await expect(form).toBeVisible()
    const input = form.locator('input').first()
    expect(await input.evaluate(el => parseFloat(getComputedStyle(el).fontSize))).toBeGreaterThanOrEqual(16)
    await expectInViewport(page.getByRole('button', { name: /登\s*录/ }), page, true)
    const box = await form.boundingBox()
    expect(box!.width).toBeGreaterThanOrEqual(280)
    expect(box!.x + box!.width).toBeLessThanOrEqual(page.viewportSize()!.width)
  })
})
