import test from 'node:test'
import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'

import {
  getActivityAction,
  getStatusTone,
  groupActivitiesByDate,
  mergeActivities,
  normalizeActivity
} from './recentActivity.js'

const readHomeVue = () => readFileSync(new URL('../views/student/Home.vue', import.meta.url), 'utf8')

const now = new Date('2026-07-18T12:00:00+08:00')

test('最近动态按上海时区分组并在组内倒序排列', () => {
  const groups = groupActivitiesByDate([
    { id: 1, occurredAt: '2026-07-17T23:30:00+08:00', title: '昨天' },
    { id: 2, occurredAt: '2026-07-18T09:00:00+08:00', title: '早上' },
    { id: 3, occurredAt: '2026-07-18T11:00:00+08:00', title: '上午' },
    { id: 4, occurredAt: '2025-12-31T18:00:00+08:00', title: '跨年' }
  ], now)

  assert.deepEqual(groups.map((group) => group.label), ['今天', '昨天', '2025年12月31日'])
  assert.deepEqual(groups[0].items.map((item) => item.id), [3, 2])
})

test('同时间动态以 id 倒序稳定排序且合并时去重', () => {
  const merged = mergeActivities(
    [{ id: 1, occurredAt: '2026-07-18T10:00:00+08:00', title: '旧标题' }],
    [
      { id: 1, occurredAt: '2026-07-18T10:00:00+08:00', title: '新标题' },
      { id: 2, occurredAt: '2026-07-18T10:00:00+08:00', title: '另一条' }
    ]
  )

  assert.deepEqual(merged.map((item) => item.id), [2, 1])
  assert.equal(merged[1].title, '新标题')
})

test('可识别动作生成带目标 ID 和附加参数的路由', () => {
  assert.deepEqual(getActivityAction({
    key: 'VIEW_APPLICATION',
    targetId: 25,
    params: { source: 'timeline', unsafe: { value: true } }
  }), {
    label: '查看申请',
    to: {
      path: '/student/applications',
      query: { source: 'timeline', applicationId: '25' }
    }
  })

  assert.deepEqual(getActivityAction({
    key: 'BROWSE_JOBS',
    params: { source: 'timeline' }
  }), {
    label: '浏览岗位',
    to: {
      path: '/student/jobs',
      query: { source: 'timeline' }
    }
  })
})

test('未知动作安全降级为无跳转', () => {
  const activity = normalizeActivity({
    id: 9,
    eventType: 'future_event',
    category: 'future_category',
    status: 'future_status',
    action: { key: 'FUTURE_ACTION', targetId: 1 }
  })

  assert.equal(activity.category, 'unknown')
  assert.equal(activity.action, null)
  assert.equal(activity.statusTone, 'info')
})

test('实习生命周期类别使用 internship 契约且兼容 lifecycle', () => {
  assert.equal(normalizeActivity({ category: 'internship' }).category, 'internship')
  assert.equal(normalizeActivity({ category: 'lifecycle' }).category, 'lifecycle')
})

test('状态语义映射不仅依赖颜色', () => {
  assert.equal(getStatusTone('pending'), 'pending')
  assert.equal(getStatusTone('success'), 'success')
  assert.equal(getStatusTone('rejected'), 'failed')
  assert.equal(getStatusTone('cancelled'), 'withdrawn')
  assert.equal(getStatusTone('withdrawn'), 'withdrawn')
  assert.equal(getStatusTone('something-new'), 'info')
})

test('学生首页 activityIcons 映射覆盖核心类别并使用 InfoFilled 兜底', () => {
  const home = readHomeVue()
  // 断言以 Home.vue 实际源码写法为准
  assert.match(home, /recruitment:\s*Briefcase/)
  assert.match(home, /confirmation:\s*CircleCheck/)
  assert.match(home, /application:\s*Document/)
  assert.match(home, /reflection:\s*EditPen/)
  assert.match(home, /internship:\s*Calendar/)
  assert.match(home, /lifecycle:\s*Calendar/)
  assert.match(home, /unknown:\s*InfoFilled/)
})

test('学生首页最近动态状态颜色 CSS 类完整覆盖五种语义', () => {
  const home = readHomeVue()
  assert.match(home, /\.activity-status\.status-pending\s*\{/)
  assert.match(home, /\.activity-status\.status-success\s*\{/)
  assert.match(home, /\.activity-status\.status-failed\s*\{/)
  assert.match(home, /\.activity-status\.status-withdrawn\s*\{/)
  assert.match(home, /\.activity-status\.status-info\s*\{/)
})
