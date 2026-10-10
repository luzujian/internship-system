import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import { createCompanyActivityFeed, readCompanyActivity, activityStatusText, activityTimeText } from '../../utils/companyActivityFeed.mjs'

const item = (id, viewed = false) => ({ id, status: 'pending', viewed })
const page = (items, extra = {}) => ({ items, total: items.length, unreadCount: 0, hasMore: false, ...extra })
const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }

test('角标使用全量未读数，最近十条已读也不会隐藏更早的未读', async () => {
  const feed = createCompanyActivityFeed(async () => page(Array.from({ length: 10 }, (_, i) => item(i, true)), { total: 18, unreadCount: 8, hasMore: true }))
  await feed.refresh()
  assert.equal(feed.state.unreadCount, 8)
  assert.equal(feed.state.hasMore, true)
})

test('加载更多保留已读记录，去重并沿用服务端分页位置', async () => {
  const calls = []
  const feed = createCompanyActivityFeed(async params => {
    calls.push(params)
    return calls.length === 1 ? page([item(3, true), item(2)], { hasMore: true, total: 4 })
      : page([item(2), item(1)], { total: 4 })
  })
  await feed.refresh(); await feed.loadMore()
  assert.equal(calls[1].offset, 2)
  assert.deepEqual(feed.state.items.map(row => row.id), [3, 2, 1])
  assert.equal(feed.state.items[0].viewed, true)
})

test('切换未读筛选后，迟到的旧响应不能覆盖新结果', async () => {
  const old = deferred()
  const feed = createCompanyActivityFeed(params => params.unreadOnly ? Promise.resolve(page([item(7)], { unreadCount: 1 })) : old.promise)
  const pending = feed.refresh()
  await feed.setUnreadOnly(true)
  old.resolve(page([item(1, true)])); await pending
  assert.deepEqual(feed.state.items.map(row => row.id), [7])
  assert.equal(feed.state.unreadOnly, true)
})

test('加载失败保留已有记录，重试继续获取失败的分页', async () => {
  const offsets = []
  const feed = createCompanyActivityFeed(async ({ offset }) => {
    offsets.push(offset)
    if (offsets.length === 2) throw new Error('network')
    return page([item(offset + 1)], { total: 2, hasMore: offset === 0 })
  })
  await feed.refresh(); await feed.loadMore()
  assert.equal(feed.state.items.length, 1)
  assert.match(feed.state.error, /加载失败/)
  await feed.retry()
  assert.deepEqual(offsets, [0, 1, 1])
  assert.equal(feed.state.items.length, 2)
  assert.equal(feed.state.error, '')
})

test('组件卸载后不再应用未完成的响应', async () => {
  const pending = deferred()
  const feed = createCompanyActivityFeed(() => pending.promise)
  const loading = feed.refresh(); feed.dispose()
  pending.resolve(page([item(1)])); await loading
  assert.deepEqual(feed.state.items, [])
})

test('已读请求失败保留未读，成功后仅更新当前条目且重复读取不再写入', async () => {
  const rows = [item(1), item(2)]
  await assert.rejects(readCompanyActivity(rows[0], async () => { throw new Error('network') }))
  assert.equal(rows[0].viewed, false)
  let writes = 0
  const save = async () => { writes++ }
  await readCompanyActivity(rows[0], save); await readCompanyActivity(rows[0], save)
  assert.equal(rows.length, 2)
  assert.equal(rows[0].viewed, true)
  assert.equal(rows[1].viewed, false)
  assert.equal(writes, 1)
})

test('申请状态与确认表状态不混淆，缺失日期不会伪装成刚刚', () => {
  assert.equal(activityStatusText('pending'), '待审核')
  assert.equal(activityStatusText('approved'), '已同意面试')
  assert.equal(activityStatusText('defaulted'), '已失效')
  assert.equal(activityTimeText(null), '时间未知')
  assert.equal(activityTimeText('invalid'), '时间未知')
})

const home = fs.readFileSync(new URL('./CompanyHome.vue', import.meta.url), 'utf8')
test('通知公告继续写入服务端已读记录', () => {
  const handler = home.slice(home.indexOf('const handleViewAnnouncement'), home.indexOf('const parseAttachments'))
  assert.match(handler, /await markAnnouncementRead\(Number\(notification\.id\)\)/)
})
test('实际首页点击携带动态编号，在详情打开后才确认已读', () => {
  const source = home.slice(home.indexOf('const handleViewRecentActivity'), home.indexOf('const handleNotificationRefresh'))
  const routes = []
  const handler = new Function('router', source + '; return handleViewRecentActivity')({ push: route => routes.push(route) })
  handler(item(41))
  assert.deepEqual(routes, [{ path: '/company/application-view', query: { activityId: '41' } }])
})

test('实际待办通知刷新动态和统计，不把待处理绝对数从申请总数扣除', () => {
  const source = home.slice(home.indexOf('const handleCompanyTodoUpdate'), home.indexOf('const refreshOnFocus'))
    .replace('data: CompanyTodoUpdateData', 'data')
  const statsData = { value: [{ value: 1 }, { value: 10 }, { value: 0 }] }
  let activityRefreshes = 0, statsRefreshes = 0
  const handler = new Function('statsData', 'fetchStats', 'fetchRecentActivities', 'console', source + '; return handleCompanyTodoUpdate')(
    statsData, () => statsRefreshes++, () => activityRefreshes++, { log() {} })
  handler({ pendingApplications: 3, pendingConfirmations: 2 })
  handler({ pendingApplications: 3, pendingConfirmations: 2 })
  assert.equal(statsData.value[1].value, 10)
  assert.equal(statsData.value[2].value, 2)
  assert.equal(activityRefreshes, 2)
  assert.equal(statsRefreshes, 2)
})
