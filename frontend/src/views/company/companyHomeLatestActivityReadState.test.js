import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'

// 企业端首页“最新动态”：点击后条目必须保留，并切换为已读视觉状态；未点击仍为未读。
const home = fs.readFileSync(new URL('./CompanyHome.vue', import.meta.url), 'utf8')

test('最新动态不再过滤掉已读条目', () => {
  assert.ok(!/\.filter\(app => !app\.viewed\)/.test(home), '仍在前端过滤已读动态')
  assert.ok(!/recentActivities\.value\.filter\(a => a\.type !== activity\.type\)/.test(home), '点击后仍在移除动态条目')
})

test('点击只标记当前动态为已读，并保留在列表中', () => {
  const handler = home.slice(home.indexOf('const handleViewRecentActivity'), home.indexOf('const getStatusText'))
  assert.match(handler, /if \(!activity\.viewed\) \{/)
  assert.match(handler, /\/company\/applications\/\$\{activity\.id\}\/view/)
  assert.match(handler, /activity\.viewed = true/)
})

test('未读数量用于角标，已读/未读有独立视觉状态', () => {
  assert.match(home, /const unreadActivityCount = computed/)
  assert.match(home, /:value="unreadActivityCount" :hidden="unreadActivityCount === 0"/)
  assert.match(home, /:class="\{ 'is-unread': !activity\.viewed, 'is-read': activity\.viewed \}"/)
  assert.match(home, /\{\{ activity\.viewed \? '已读' : '未读' \}\}/)
  assert.ok(!/box-shadow: inset 3px 0 0 #f56c6c/.test(home), '未读动态不应再有红色竖杠')
  assert.match(home, /\.activity-item\.is-read \.activity-title \{/)
})

test('通知公告点击后写入服务端已读记录', () => {
  assert.match(home, /import \{ markAnnouncementRead \} from '@\/api\/announcement'/)
  const handler = home.slice(home.indexOf('const handleViewAnnouncement'), home.indexOf('const parseAttachments'))
  assert.match(handler, /await markAnnouncementRead\(Number\(notification\.id\)\)/)
  assert.match(handler, /targetNotification\.isRead = true/)
})
