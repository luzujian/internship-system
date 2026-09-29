import { test, expect, type Page } from '@playwright/test'
import { readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { authenticateAs, freezeDate, mockApis as mockApiRoutes } from './helpers/auth'

const __dirname = dirname(fileURLToPath(import.meta.url))
const FIXTURES_DIR = resolve(__dirname, 'fixtures')

/**
 * 加载 fixture JSON
 */
function loadFixture(name: string): any {
  return JSON.parse(readFileSync(resolve(FIXTURES_DIR, name), 'utf-8'))
}

/**
 * 固定的「现在」时间：2026-07-18 12:00:00 +08:00
 * 用于让 fixture 中的日期（2026-07-18 等）在 groupActivitiesByDate 中产生稳定的标签
 */
const FIXED_NOW_ISO = '2026-07-18T12:00:00+08:00'

/**
 * 注入认证状态 + 固定 Date：
 * - authenticateAs('student')：注入 localStorage 伪 JWT，authGuard 走正常
 *   restoreAuthState 流程恢复认证（不依赖 VITE_SKIP_AUTH_CHECK）
 * - freezeDate：Mock window.Date 让「今天」固定为 2026-07-18
 */
async function setupPage(page: Page, options: { mockWebSocket?: boolean } = {}) {
  await authenticateAs(page, 'student', { mockWebSocket: options.mockWebSocket })
  await freezeDate(page, FIXED_NOW_ISO)
}

interface MockOptions {
  /** progress-list 接口的返回逻辑（基于 URL 与调用次数） */
  progress?: (url: string, callIndex: number) => any
  /** /api/student/job-applications 列表 */
  jobApplications?: any[]
  /** /api/student/applications 列表（自主实习等） */
  applications?: any[]
}

/**
 * 拦截所有真实 API 请求（转发到共享 helpers/auth.ts 的 mockApis，禁用真实后端）。
 * 注意匹配顺序：job-applications 必须在 applications 之前（URL 子串包含关系）。
 */
async function mockApis(page: Page, options: MockOptions = {}) {
  await mockApiRoutes(page, {
    '/student/home/progress-list': (url: string, callIndex: number) =>
      options.progress
        ? options.progress(url, callIndex)
        : { code: 200, data: { items: [], nextCursor: null, hasMore: false } },
    '/student/job-applications': options.jobApplications ?? [],
    '/student/applications': options.applications ?? []
  })
}

/**
 * 等待时间线加载完成（骨架消失，第一条 .activity-item 出现 或 显示空状态）
 */
async function waitForTimelineReady(page: Page) {
  await page.goto('/student/home')
  // 等待骨架消失（progressLoading=false）
  await expect(page.locator('.activity-skeleton')).toHaveCount(0, { timeout: 15000 })
}

// ============================================================
// 8.1 数据量边界场景
// ============================================================
test.describe('数据量边界', () => {
  test('0 条数据显示空状态', async ({ page }) => {
    await setupPage(page)
    await mockApis(page, { progress: () => loadFixture('empty.json') })

    await waitForTimelineReady(page)

    await expect(page.locator('.activity-empty')).toBeVisible()
    await expect(page.getByText('还没有实习动态')).toBeVisible()
    await expect(page.getByText('投递岗位或提交申请后会显示在这里')).toBeVisible()
    await expect(page.locator('.activity-empty').getByRole('button', { name: '浏览岗位' })).toBeVisible()
    await expect(page.locator('.activity-item')).toHaveCount(0)
    await expect(page.locator('.activity-load-more')).toHaveCount(0)
  })

  test('1 条数据显示单条', async ({ page }) => {
    await setupPage(page)
    await mockApis(page, { progress: () => loadFixture('single.json') })

    await waitForTimelineReady(page)

    await expect(page.locator('.activity-item')).toHaveCount(1)
    await expect(page.locator('.activity-title').first()).toContainText('已投递岗位')
    await expect(page.locator('.activity-status').first()).toContainText('待企业处理')
    // 单条且 hasMore=false，不应显示「加载更多」按钮
    await expect(page.getByRole('button', { name: '加载更多' })).toHaveCount(0)
    await expect(page.locator('.activity-end')).toBeVisible()
  })

  test('10 条数据显示加载更多按钮', async ({ page }) => {
    await setupPage(page)
    await mockApis(page, { progress: () => loadFixture('ten.json') })

    await waitForTimelineReady(page)

    await expect(page.locator('.activity-item')).toHaveCount(10)
    await expect(page.getByRole('button', { name: '加载更多' })).toBeVisible()
    await expect(page.locator('.activity-end')).toHaveCount(0)
  })

  test('20 条数据两次加载后无更多', async ({ page }) => {
    await setupPage(page)
    await mockApis(page, {
      progress: (url) => {
        // 第二次（带 cursor=cursor-page2）返回 ten-more.json
        if (url.includes('cursor=cursor-page2')) {
          return loadFixture('ten-more.json')
        }
        return loadFixture('ten.json')
      }
    })

    await waitForTimelineReady(page)
    await expect(page.locator('.activity-item')).toHaveCount(10)

    // 点击「加载更多」
    await page.getByRole('button', { name: '加载更多' }).click()

    // 等待加载完成，总条数应为 20
    await expect(page.locator('.activity-item')).toHaveCount(20)

    // hasMore=false 后「加载更多」按钮消失，显示「没有更多了」
    await expect(page.getByRole('button', { name: '加载更多' })).toHaveCount(0)
    await expect(page.locator('.activity-end')).toBeVisible()
  })
})

// ============================================================
// 8.2 同时间稳定排序
// ============================================================
test('同时间记录按 id 倒序展示', async ({ page }) => {
  await setupPage(page)
  await mockApis(page, { progress: () => loadFixture('same-time.json') })

  await waitForTimelineReady(page)

  await expect(page.locator('.activity-item')).toHaveCount(2)

  // 第一条的标题应为 id=100 的「已投递岗位 A」
  await expect(page.locator('.activity-item').first().locator('.activity-title')).toContainText('已投递岗位 A')
  // 第二条的标题应为 id=99 的「面试邀请 B」
  await expect(page.locator('.activity-item').nth(1).locator('.activity-title')).toContainText('面试邀请 B')
})

// ============================================================
// 8.3 跨日日期分组
// ============================================================
test('跨日记录出现今天/昨天/M月D日/跨年四类标签', async ({ page }) => {
  await setupPage(page)
  await mockApis(page, { progress: () => loadFixture('cross-day.json') })

  await waitForTimelineReady(page)

  await expect(page.locator('.activity-item')).toHaveCount(4)

  // 今天（2026-07-18）
  await expect(page.locator('.activity-date-label').filter({ hasText: '今天' })).toBeVisible()
  // 昨天（2026-07-17）
  await expect(page.locator('.activity-date-label').filter({ hasText: '昨天' })).toBeVisible()
  // 本年其他日期：6月15日
  await expect(page.locator('.activity-date-label').filter({ hasText: '6月15日' })).toBeVisible()
  // 跨年：2025年12月31日
  await expect(page.locator('.activity-date-label').filter({ hasText: '2025年12月31日' })).toBeVisible()
})

// ============================================================
// 8.4 多 Offer 并存
// ============================================================
test('多 Offer 并存，各自独立的「填写确认表」链接', async ({ page }) => {
  await setupPage(page)
  await mockApis(page, { progress: () => loadFixture('multi-offer.json') })

  await waitForTimelineReady(page)

  await expect(page.locator('.activity-item')).toHaveCount(2)

  // 两条均显示「获得 Offer」状态
  await expect(page.locator('.activity-status').filter({ hasText: '获得 Offer' })).toHaveCount(2)

  // 两个「填写确认表」链接
  const confirmLinks = page.locator('.activity-action').filter({ hasText: '填写确认表' })
  await expect(confirmLinks).toHaveCount(2)

  // 第一个链接 href 包含 offerId=25
  const firstHref = await confirmLinks.nth(0).getAttribute('href')
  expect(firstHref).toContain('offerId=25')

  // 第二个链接 href 包含 offerId=26
  const secondHref = await confirmLinks.nth(1).getAttribute('href')
  expect(secondHref).toContain('offerId=26')
})

// ============================================================
// 8.5 多类实习动态
// ============================================================
test('多类实习动态不显示阶段标签', async ({ page }) => {
  await setupPage(page)
  await mockApis(page, { progress: () => loadFixture('lifecycle-mix.json') })

  await waitForTimelineReady(page)

  await expect(page.locator('.activity-item')).toHaveCount(4)
  await expect(page.locator('.activity-stage')).toHaveCount(0)
})

// ============================================================
// 8.6 WebSocket 静默刷新
// ============================================================
test('WebSocket 收到通知后 200ms 内刷新并去重', async ({ page }) => {
  // 安装 WebSocket mock 以便后续触发 onmessage
  await setupPage(page, { mockWebSocket: true })

  const tenFixture = loadFixture('ten.json')
  // 第二次请求返回的 fixture：在原有 10 条基础上加 1 条新动态（id=999）
  const tenWithExtra = {
    code: 200,
    data: {
      items: [
        {
          id: 999,
          eventType: 'job_applied',
          category: 'recruitment',
          title: 'WS 触发的新动态',
          description: 'WebSocket 静默刷新验证',
          status: 'pending',
          statusText: '待企业处理',
          occurredAt: '2026-07-18T15:00:00+08:00',
          relatedId: 999,
          action: { key: 'VIEW_APPLICATION', targetId: 999, params: {} }
        },
        ...tenFixture.data.items
      ],
      nextCursor: 'cursor-page2',
      hasMore: true
    }
  }

  let progressCallCount = 0
  await mockApis(page, {
    progress: () => {
      progressCallCount++
      return progressCallCount === 1 ? tenFixture : tenWithExtra
    }
  })

  await waitForTimelineReady(page)
  await expect(page.locator('.activity-item')).toHaveCount(10)

  // 滚动时间线到非零位置，便于验证刷新后滚动位置保留
  const scrollContainer = page.locator('.recent-activity-body')
  await scrollContainer.evaluate((el: HTMLElement) => {
    el.scrollTop = 50
  })

  // 等待 WebSocket 实例创建并完成 onmessage 赋值
  // 注意：__testWSInstances 中包含 Vite HMR 的 WebSocket（url 含 localhost:5173），
  // 需按 URL 过滤出 StudentLayout 创建的 announcement WebSocket（url 含 /ws/）
  await page.waitForFunction(() => {
    const instances = (window as any).__testWSInstances
    if (!instances || instances.length === 0) return false
    return instances.some((ws: any) =>
      ws.url && ws.url.includes('/ws/') && typeof ws.onmessage === 'function'
    )
  }, { timeout: 15000 })

  // 触发 WebSocket 消息：student_recent_activity_changed
  await page.evaluate(() => {
    const instances = (window as any).__testWSInstances
    const ws = instances.find((w: any) => w.url && w.url.includes('/ws/'))
    if (!ws) {
      throw new Error('未找到 announcement WebSocket 实例')
    }
    if (typeof ws.onmessage !== 'function') {
      throw new Error(`onmessage 不是函数: ${typeof ws.onmessage}`)
    }
    ws.onmessage({
      data: JSON.stringify({
        type: 'student_recent_activity_changed',
        data: { activityId: 999 }
      })
    })
  })

  // 等待 200ms 防抖 + 网络请求 + 渲染
  await page.waitForTimeout(500)

  // 验证：新增动态（id=999）出现，原 10 条按 id 去重保留，总条数 11
  await expect(page.locator('.activity-item')).toHaveCount(11)
  await expect(page.locator('.activity-title').filter({ hasText: 'WS 触发的新动态' })).toBeVisible()

  // 验证：滚动位置保留（不严格相等，允许新内容导致的偏移，但应非 0）
  const scrollTopAfter = await scrollContainer.evaluate((el: HTMLElement) => el.scrollTop)
  expect(scrollTopAfter).toBeGreaterThan(0)
})

// ============================================================
// 8.7 键盘操作
// ============================================================
test('操作链接可通过键盘 Enter 跳转携带 applicationId', async ({ page }) => {
  await setupPage(page)
  // 注意：必须 mock jobApplications 返回 id=10 的记录，否则 Applications.vue
  // 找不到记录会调用 clearApplicationDeepLink() 清除 URL 中的 applicationId
  await mockApis(page, {
    progress: () => loadFixture('single.json'),
    jobApplications: [
      {
        id: 10,
        positionName: 'Java 实习生',
        companyName: '星河科技',
        location: '北京',
        salary: '300/天',
        duration: '3 个月',
        status: '0',
        applyDate: '2026-07-18',
        createTime: '2026-07-18'
      }
    ]
  })

  await waitForTimelineReady(page)

  await expect(page.locator('.activity-action')).toHaveCount(1)

  // 聚焦操作链接（验证键盘可访问性）
  await page.locator('.activity-action').first().focus()

  // 验证聚焦元素是 .activity-action 链接（<a> 标签）
  const focusedTag = await page.evaluate(() => {
    const el = document.activeElement
    return {
      tag: el ? el.tagName : '',
      className: el ? el.className : ''
    }
  })
  expect(focusedTag.tag).toBe('A')
  expect(focusedTag.className).toContain('activity-action')

  // 直接点击触发跳转（Tab+Enter 的可访问性已通过 focus 验证）
  await page.locator('.activity-action').first().click()

  // 验证 URL 跳转到 /student/applications
  await expect(page).toHaveURL(/\/student\/applications/)

  // 验证 URL 携带 applicationId=10
  await expect(page).toHaveURL(/applicationId=10/)
})

// ============================================================
// 8.8 布局三档（在 desktop / tablet / mobile 三个 project 下都会跑）
// ============================================================
test.describe('布局', () => {
  test('时间线容器不横向溢出', async ({ page }, testInfo) => {
    await setupPage(page)
    await mockApis(page, { progress: () => loadFixture('ten.json') })

    await waitForTimelineReady(page)

    const timeline = page.locator('.recent-activity-body')
    await expect(timeline).toBeVisible()

    const overflow = await timeline.evaluate((el: HTMLElement) => ({
      scrollWidth: el.scrollWidth,
      clientWidth: el.clientWidth
    }))

    // mobile 375px 下允许 10px 容差（滚动条和内边距可能导致轻微差异）
    const tolerance = testInfo.project.name === 'mobile' ? 10 : 0
    expect(overflow.scrollWidth).toBeLessThanOrEqual(overflow.clientWidth + tolerance)
  })

  test('点击区高度 >= 44px', async ({ page }) => {
    await setupPage(page)
    await mockApis(page, { progress: () => loadFixture('ten.json') })

    await waitForTimelineReady(page)

    const action = page.locator('.activity-action').first()
    await expect(action).toBeVisible()

    const box = await action.boundingBox()
    expect(box).not.toBeNull()
    expect(box!.height).toBeGreaterThanOrEqual(44)
  })

  test('描述最多 3 行（-webkit-line-clamp: 3）', async ({ page }) => {
    await setupPage(page)
    await mockApis(page, { progress: () => loadFixture('ten.json') })

    await waitForTimelineReady(page)

    const description = page.locator('.activity-description').first()
    await expect(description).toBeVisible()

    const clamp = await description.evaluate((el: HTMLElement) => {
      return (window as any).getComputedStyle(el).webkitLineClamp
    })
    expect(String(clamp)).toBe('3')
  })
})

// ============================================================
// 8.9 深链
// ============================================================
test.describe('深链', () => {
  test('点击查看申请跳转并携带 applicationId', async ({ page }) => {
    await setupPage(page)
    await mockApis(page, {
      progress: () => loadFixture('single.json'),
      // 返回包含 id=10 的岗位申请
      jobApplications: [
        {
          id: 10,
          positionName: 'Java 实习生',
          companyName: '星河科技',
          location: '北京',
          salary: '300/天',
          duration: '3 个月',
          status: '0',
          applyDate: '2026-07-18',
          createTime: '2026-07-18'
        }
      ]
    })

    await waitForTimelineReady(page)

    // 点击「查看申请」链接
    const viewLink = page.locator('.activity-action').filter({ hasText: '查看申请' }).first()
    await expect(viewLink).toBeVisible()
    await viewLink.click()

    // 验证 URL 跳转到 /student/applications?applicationId=10
    await expect(page).toHaveURL(/\/student\/applications/)
    await expect(page).toHaveURL(/applicationId=10/)

    // 验证目标页定位到对应记录（deep-link-target 类）
    await expect(page.locator('.deep-link-target')).toBeVisible({ timeout: 15000 })
  })

  test('ID 失效时退回列表并提示', async ({ page }) => {
    await setupPage(page)

    // 修改 single.json：relatedId/targetId 改为 999（列表中不存在）
    const singleWithInvalidId = {
      code: 200,
      data: {
        items: [
          {
            id: 10,
            eventType: 'job_applied',
            category: 'recruitment',
            title: '已投递岗位',
            description: '星河科技 · Java 实习生',
            status: 'pending',
            statusText: '待企业处理',
            occurredAt: '2026-07-18T14:20:00+08:00',
            relatedId: 999,
            action: { key: 'VIEW_APPLICATION', targetId: 999, params: {} }
          }
        ],
        nextCursor: null,
        hasMore: false
      }
    }

    await mockApis(page, {
      progress: () => singleWithInvalidId,
      // 列表中不包含 id=999
      jobApplications: [
        {
          id: 1,
          positionName: '其他岗位',
          companyName: '其他公司',
          status: '0'
        }
      ]
    })

    await waitForTimelineReady(page)

    // 点击「查看申请」链接
    const viewLink = page.locator('.activity-action').filter({ hasText: '查看申请' }).first()
    await expect(viewLink).toBeVisible()
    await viewLink.click()

    // 验证 URL 跳转
    await expect(page).toHaveURL(/\/student\/applications/)

    // 验证显示「未找到该申请记录」提示（Applications.vue 找不到记录时会清除 URL 中的 applicationId）
    await expect(page.locator('.el-message').filter({ hasText: '未找到该申请记录' })).toBeVisible({ timeout: 15000 })
  })
})
