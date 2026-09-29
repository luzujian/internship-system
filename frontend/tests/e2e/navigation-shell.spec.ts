import { test, expect, type Page } from '@playwright/test'
import { authenticateAs, mockApis, type TestRole } from './helpers/auth'
import { getRoleApiMocks, ROLE_HOME } from './helpers/roleMocks'

interface ShellSelector {
  aside: string
  hiddenBy: 'vshow' | 'transform'
}

const SHELL_SELECTORS: Record<TestRole, ShellSelector> = {
  student: { aside: 'aside.sidebar', hiddenBy: 'transform' },
  teacher: { aside: 'aside.aside', hiddenBy: 'vshow' },
  company: { aside: 'aside.sidebar', hiddenBy: 'vshow' },
  admin: { aside: 'aside.sidebar', hiddenBy: 'vshow' }
}

const MOBILE_LIKE_PROJECTS = ['mobile', 'tablet']
const DRAWER_MAX_WIDTH = 280

async function openRoleShell(page: Page, role: TestRole, path: string) {
  await authenticateAs(page, role)
  await mockApis(page, getRoleApiMocks(role))
  await page.goto(path)
  await expect(page.locator('.sidebar-toggle-btn')).toBeVisible()
}

async function expectDrawerClosed(page: Page, shell: ShellSelector) {
  const aside = page.locator(shell.aside)
  if (shell.hiddenBy === 'vshow') {
    await expect(aside).toBeHidden()
  } else {
    await expect(async () => {
      const box = await aside.boundingBox()
      expect(box).not.toBeNull()
      expect(box!.x).toBeLessThan(0)
    }).toPass()
  }
}

test.describe('公共入口', () => {
  test('登录页渲染登录表单', async ({ page }) => {
    await mockApis(page)
    await page.goto('/login')
    await expect(page.locator('.title')).toHaveText('欢迎登录')
    await expect(page.locator('input[placeholder="请输入账号"]')).toBeVisible()
    await expect(page.locator('input[type="password"]')).toBeVisible()
  })

  test('企业注册页渲染注册表单', async ({ page }) => {
    await mockApis(page)
    await page.goto('/company-register')
    await expect(page.locator('h2')).toHaveText('企业注册申请')
    await expect(page.getByPlaceholder('请输入个人邮箱（用于找回密码）')).toBeVisible()
    await expect(page.getByText('邮箱验证码', { exact: true })).toHaveCount(0)
  })

  test('企业追回模式使用申请管理凭据且不要求邮箱验证码', async ({ page }) => {
    await mockApis(page)
    await page.goto('/company-register?companyName=测试企业&recall=true')
    await expect(page.getByPlaceholder('请输入申请管理凭据')).toBeVisible()
    await expect(page.getByText('邮箱验证码', { exact: true })).toHaveCount(0)
  })

  test('企业申请撤回需验证管理凭据', async ({ page }) => {
    await mockApis(page)
    await page.route('**/api/company/check-status**', route => route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: { companyName: '测试企业', status: 0, recallStatus: 0 } })
    }))
    await page.goto('/company-check')
    await page.getByPlaceholder('请输入企业全称').fill('测试企业')
    await page.getByRole('button', { name: '查询注册状态' }).click()
    await page.getByRole('button', { name: '追回申请' }).click()
    await expect(page.getByPlaceholder('请输入申请管理凭据')).toBeVisible()
    await expect(page.getByText('邮箱验证码', { exact: true })).toHaveCount(0)
  })

  test('企业审核查询页渲染查询表单', async ({ page }) => {
    await mockApis(page)
    await page.goto('/company-check')
    await expect(page.locator('h2')).toHaveText('企业注册申请')
    await expect(page.getByText('查询注册状态').first()).toBeVisible()
  })
})

test.describe('四角色导航壳层（手机/平板抽屉）', () => {
  for (const role of ['student', 'teacher', 'company', 'admin'] as TestRole[]) {
    test(`${role}：默认关闭，点汉堡展开遮罩抽屉，点遮罩关闭`, async ({ page }, testInfo) => {
      test.skip(!MOBILE_LIKE_PROJECTS.includes(testInfo.project.name), '仅移动/平板视口')
      const shell = SHELL_SELECTORS[role]

      await openRoleShell(page, role, ROLE_HOME[role])
      await expectDrawerClosed(page, shell)
      await expect(page.locator('.sidebar-overlay')).toHaveCount(0)

      await page.locator('.sidebar-toggle-btn').click()
      const aside = page.locator(shell.aside)
      await expect(aside).toBeVisible()
      await expect(page.locator('.sidebar-overlay')).toBeVisible()

      await expect(async () => {
        const openBox = await aside.boundingBox()
        expect(openBox).not.toBeNull()
        expect(openBox!.x).toBeGreaterThanOrEqual(0)
        expect(Math.abs(openBox!.width - DRAWER_MAX_WIDTH)).toBeLessThanOrEqual(2)
      }).toPass()

      await page.locator('.sidebar-overlay').click({ position: { x: 340, y: 120 } })
      await expect(page.locator('.sidebar-overlay')).toHaveCount(0)
      await expectDrawerClosed(page, shell)
    })

    test(`${role}：Escape 关闭抽屉`, async ({ page }, testInfo) => {
      test.skip(!MOBILE_LIKE_PROJECTS.includes(testInfo.project.name), '仅移动/平板视口')
      const shell = SHELL_SELECTORS[role]

      await openRoleShell(page, role, ROLE_HOME[role])
      await page.locator('.sidebar-toggle-btn').click()
      await expect(page.locator('.sidebar-overlay')).toBeVisible()

      await page.keyboard.press('Escape')
      await expect(page.locator('.sidebar-overlay')).toHaveCount(0)
      await expectDrawerClosed(page, shell)
    })

    test(`${role}：桌面端侧栏默认展开`, async ({ page }, testInfo) => {
      test.skip(testInfo.project.name !== 'desktop', '仅桌面视口')
      const shell = SHELL_SELECTORS[role]

      await openRoleShell(page, role, ROLE_HOME[role])
      const aside = page.locator(shell.aside)
      await expect(aside).toBeVisible()
      const box = await aside.boundingBox()
      expect(box).not.toBeNull()
      expect(box!.x).toBeGreaterThanOrEqual(0)
      await expect(page.locator('.sidebar-overlay')).toHaveCount(0)

      await page.locator('.sidebar-toggle-btn').click()
      await expectDrawerClosed(page, shell)

      await page.locator('.sidebar-toggle-btn').click()
      await expect(aside).toBeVisible()
      await expect(async () => {
        const box = await aside.boundingBox()
        expect(box).not.toBeNull()
        expect(box!.x).toBeGreaterThanOrEqual(0)
      }).toPass()
    })
  }

  test('teacher：点击菜单项导航后抽屉自动关闭', async ({ page }, testInfo) => {
    test.skip(!MOBILE_LIKE_PROJECTS.includes(testInfo.project.name), '仅移动/平板视口')
    const shell = SHELL_SELECTORS.teacher

    await openRoleShell(page, 'teacher', ROLE_HOME.teacher)
    await page.locator('.sidebar-toggle-btn').click()
    const aside = page.locator(shell.aside)
    await expect(aside).toBeVisible()

    await aside.getByText('审批管理').click()
    await page.waitForURL('**/teacher/approval')
    await expectDrawerClosed(page, shell)
    await expect(page.locator('.sidebar-overlay')).toHaveCount(0)
  })

  test('student：点击菜单项导航后抽屉自动关闭', async ({ page }, testInfo) => {
    test.skip(!MOBILE_LIKE_PROJECTS.includes(testInfo.project.name), '仅移动/平板视口')
    const shell = SHELL_SELECTORS.student

    await openRoleShell(page, 'student', ROLE_HOME.student)
    await page.locator('.sidebar-toggle-btn').click()
    const aside = page.locator(shell.aside)
    await expect(aside).toBeVisible()
    await expect(aside.locator('.el-menu-item')).toHaveCount(8)

    await aside.locator('.el-menu-item').nth(1).click()
    await page.waitForURL('**/student/jobs')
    await expectDrawerClosed(page, shell)
    await expect(page.locator('.sidebar-overlay')).toHaveCount(0)
  })

  test('admin：权限加载后菜单可见', async ({ page }, testInfo) => {
    test.skip(!MOBILE_LIKE_PROJECTS.includes(testInfo.project.name), '仅移动/平板视口')

    await openRoleShell(page, 'admin', ROLE_HOME.admin)
    await page.locator('.sidebar-toggle-btn').click()
    const aside = page.locator(SHELL_SELECTORS.admin.aside)
    await expect(aside).toBeVisible()
    await expect(aside.locator('.el-menu').first()).toBeVisible()
  })
})
