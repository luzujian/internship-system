import { test, expect, type Page } from '@playwright/test'
import { authenticateAs, mockApis, type TestRole } from './helpers/auth'
import { getRoleApiMocks, PUBLIC_ROUTES, ROLE_ROUTES } from './helpers/roleMocks'

type SmokeRole = TestRole | 'public'

const OVERFLOW_TOLERANCE_PX = 10

const KEY_SPOT_ROUTES: Array<{ role: SmokeRole; path: string }> = [
  { role: 'public', path: '/login' },
  { role: 'public', path: '/company-register' },
  { role: 'public', path: '/forgot-password' },
  { role: 'student', path: '/student/jobs' },
  { role: 'student', path: '/student/applications' },
  { role: 'student', path: '/student/internship-registration' },
  { role: 'teacher', path: '/teacher/approval' },
  { role: 'teacher', path: '/teacher/student-tracking' },
  { role: 'company', path: '/company/jobs' },
  { role: 'company', path: '/company/application-view' },
  { role: 'company', path: '/company/internship-students' },
  { role: 'admin', path: '/admin/student-users' },
  { role: 'admin', path: '/admin/recruitment' }
]

async function settlePage(page: Page) {
  await page.evaluate(
    () =>
      new Promise<void>((resolve) => {
        requestAnimationFrame(() => requestAnimationFrame(() => resolve()))
      })
  )
  await page.waitForTimeout(300)
}

async function openRoute(page: Page, role: SmokeRole, path: string) {
  if (role === 'public') {
    await mockApis(page)
    await page.goto(path)
    await expect(page.locator('.title, h2').first()).toBeVisible()
  } else {
    await authenticateAs(page, role)
    await mockApis(page, getRoleApiMocks(role))
    await page.goto(path)
    await expect(page.locator('.sidebar-toggle-btn')).toBeVisible()
    await expect(page.locator('[data-navigation-content]')).not.toHaveText('')
  }
  await expect.poll(() => new URL(page.url()).pathname, { message: `必须进入目标路由 ${path}，禁止重定向后误报通过` }).toBe(path)
  await settlePage(page)
}

function collectOverflowOffenders(tolerance: number): string[] {
  const vw = document.documentElement.clientWidth
  const offenders: string[] = []
  for (const el of Array.from(document.body.querySelectorAll<HTMLElement>('*'))) {
    if (offenders.length >= 8) break
    const position = getComputedStyle(el).position
    if (position === 'fixed' || position === 'sticky') continue
    const rect = el.getBoundingClientRect()
    if (rect.width === 0 && rect.height === 0) continue
    if (rect.right > vw + tolerance) {
      const cls =
        typeof el.className === 'string' && el.className.trim()
          ? `.${el.className.trim().split(/\s+/).slice(0, 3).join('.')}`
          : ''
      offenders.push(`<${el.tagName.toLowerCase()}${cls}> right=${Math.round(rect.right)}px（视口 ${vw}px）`)
    }
  }
  return offenders
}

async function expectNoFullPageHorizontalOverflow(page: Page, label: string) {
  const metrics = await page.evaluate(() => ({
    scrollW: document.documentElement.scrollWidth,
    clientW: document.documentElement.clientWidth,
    bodyScrollW: document.body.scrollWidth,
    bodyClientW: document.body.clientWidth
  }))

  const rootOverflow = metrics.scrollW - metrics.clientW
  const bodyOverflow = metrics.bodyScrollW - metrics.bodyClientW
  let diagnostics = ''
  if (rootOverflow > OVERFLOW_TOLERANCE_PX || bodyOverflow > OVERFLOW_TOLERANCE_PX) {
    const offenders = await page.evaluate(collectOverflowOffenders, OVERFLOW_TOLERANCE_PX)
    if (offenders.length > 0) {
      diagnostics = `\n溢出可疑元素（fixed/sticky 已排除）：\n${offenders.join('\n')}`
    }
  }
  expect(
    rootOverflow,
    `${label}：html 横向溢出 ${rootOverflow}px（scrollWidth=${metrics.scrollW}，clientWidth=${metrics.clientW}，容差 ${OVERFLOW_TOLERANCE_PX}px）${diagnostics}`
  ).toBeLessThanOrEqual(OVERFLOW_TOLERANCE_PX)

  expect(
    bodyOverflow,
    `${label}：body 横向溢出 ${bodyOverflow}px（scrollWidth=${metrics.bodyScrollW}，clientWidth=${metrics.bodyClientW}，容差 ${OVERFLOW_TOLERANCE_PX}px）${diagnostics}`
  ).toBeLessThanOrEqual(OVERFLOW_TOLERANCE_PX)
}

test.describe('全路由整页横向溢出 smoke（375 / 768，含真实路由断言）', () => {
  for (const path of PUBLIC_ROUTES) {
    test(`公开页 ${path}`, async ({ page }, testInfo) => {
      test.skip(!['mobile', 'webkit-mobile', 'tablet', 'webkit-tablet'].includes(testInfo.project.name), '仅手机视口')
      await openRoute(page, 'public', path)
      await expectNoFullPageHorizontalOverflow(page, `公开页 ${path}`)
    })
  }

  for (const role of Object.keys(ROLE_ROUTES) as TestRole[]) {
    for (const path of ROLE_ROUTES[role]) {
      test(`${role} ${path}`, async ({ page }, testInfo) => {
        test.skip(!['mobile', 'webkit-mobile', 'tablet', 'webkit-tablet'].includes(testInfo.project.name), '全路由 375/768')
        await openRoute(page, role, path)
        await expectNoFullPageHorizontalOverflow(page, `${role} ${path}`)
      })
    }
  }
})

test.describe('关键页面横屏 667×375 抽查', () => {
  for (const { role, path } of KEY_SPOT_ROUTES) {
    test(`${role} ${path}`, async ({ page }, testInfo) => {
      test.skip(!['mobile', 'webkit-mobile'].includes(testInfo.project.name), '仅手机视口')
      await openRoute(page, role, path)
      await expectNoFullPageHorizontalOverflow(page, `竖屏 ${role} ${path}`)

      await page.setViewportSize({ width: 667, height: 375 })
      await settlePage(page)
      await expectNoFullPageHorizontalOverflow(page, `横屏 ${role} ${path}`)
    })
  }
})

test.describe('关键页面平板横屏 1024×768 抽查', () => {
  for (const { role, path } of KEY_SPOT_ROUTES) {
    test(`${role} ${path}`, async ({ page }, testInfo) => {
      test.skip(!['tablet', 'webkit-tablet'].includes(testInfo.project.name), '仅平板视口')
      await page.setViewportSize({ width: 1024, height: 768 })
      await openRoute(page, role, path)
      await expectNoFullPageHorizontalOverflow(page, `平板横屏 ${role} ${path}`)
    })
  }
})
